/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.queue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.events.base.PmsMetricContextGuard;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

/**
 * JOOQ-backed store for the tier-2 dequeue queue used by the counter-based step-level concurrency
 * gate. Uses {@link DSLContext} against the shared {@code PipelineServiceDSLContext} — no separate
 * connection pool.
 *
 * <p>Uses jOOQ's dynamic DSL (table/field name strings) rather than generated types. To swap in
 * generated types later, add {@code step_concurrency_queue} to the jOOQ codegen {@code includes}
 * list (see {@code 959-psql-database-models/README.md}) and replace {@link #TABLE} / {@link
 * #NODE_EXECUTION_ID} etc. with the generated constants. Query shape stays the same.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class StepConcurrencyQueueServiceImpl implements StepConcurrencyQueueService {
  public static final String METRIC_QUEUE_OPERATIONS = "pipeline_step_concurrency_queue_operations_total";
  public static final String METRIC_QUEUE_FETCH_BATCH_SIZE = "pipeline_step_concurrency_queue_fetch_batch_size";

  private static final String OPERATION_INSERT = "insert";
  private static final String OPERATION_DELETE = "delete";
  private static final String OPERATION_FETCH_BATCH = "fetch_batch";
  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_ERROR = "error";

  private static final Table<Record> TABLE = DSL.table("step_concurrency_queue");
  private static final Field<String> NODE_EXECUTION_ID = DSL.field("node_execution_id", SQLDataType.VARCHAR);
  private static final Field<String> PLAN_EXECUTION_ID = DSL.field("plan_execution_id", SQLDataType.VARCHAR);
  private static final Field<String> ACCOUNT_ID = DSL.field("account_id", SQLDataType.VARCHAR);
  private static final Field<OffsetDateTime> CREATED_AT = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE);

  private final DSLContext dsl;
  private final boolean queueStoreEnabled;
  private final MetricService metricService;

  @Inject
  public StepConcurrencyQueueServiceImpl(@Named("PipelineServiceDSLContext") DSLContext dsl,
      @Named("stepConcurrencyQueueStoreEnabled") boolean queueStoreEnabled, MetricService metricService) {
    this.dsl = dsl;
    this.queueStoreEnabled = queueStoreEnabled;
    this.metricService = metricService;
  }

  /**
   * Idempotent upsert. Best-effort on failure: exceptions are logged and swallowed. The caller
   * (PR 5, queue-in path on the hot orchestration thread) must not be blocked by a Postgres blip
   * — a lost queue row will be reconciled by the daily rebuild + Mongo status flip. See PR 4
   * review discussion for the observability follow-up (metric emission on this catch block).
   */
  @Override
  public void insert(StepConcurrencyQueueEntry entry) {
    if (!queueStoreEnabled) {
      return;
    }
    if (entry == null || entry.getNodeExecutionId() == null) {
      log.warn("[STEP_CONCURRENCY_QUEUE] refusing to insert null entry / null nodeExecutionId");
      return;
    }
    try {
      OffsetDateTime createdAt =
          OffsetDateTime.ofInstant(entry.getCreatedAt() != null ? entry.getCreatedAt() : Instant.now(), ZoneOffset.UTC);
      dsl.insertInto(TABLE)
          .set(NODE_EXECUTION_ID, entry.getNodeExecutionId())
          .set(PLAN_EXECUTION_ID, entry.getPlanExecutionId())
          .set(ACCOUNT_ID, entry.getAccountId())
          .set(CREATED_AT, createdAt)
          .onConflict(NODE_EXECUTION_ID)
          .doNothing()
          .execute();
      emitOperationMetric(OPERATION_INSERT, OUTCOME_SUCCESS);
    } catch (Exception ex) {
      log.error("[STEP_CONCURRENCY_QUEUE] insert failed nodeExecutionId={}", entry.getNodeExecutionId(), ex);
      emitOperationMetric(OPERATION_INSERT, OUTCOME_ERROR);
    }
  }

  /**
   * Returns true iff a row was actually deleted (this pod owns the claim).
   *
   * <p><b>Note on false semantics:</b> {@code false} covers <em>both</em> "another pod already
   * claimed" <em>and</em> "Postgres blipped and we can't tell". The tier-2 dequeue's Mongo
   * {@code findAndModify} predicate miss backstop self-heals either case (the row is either gone
   * from Mongo or will be reclaimed on the next walk). Rethrowing would blow up the orchestration
   * thread on a Postgres blip during tier-1 inline delete or the compensating write path — worse
   * than the observability gap the swallow creates. Metric on the catch block will land with the
   * gate wiring in PR 5 to break the "blip vs race" ambiguity for ops.
   */
  @Override
  public boolean deleteByNodeExecutionId(String nodeExecutionId) {
    if (!queueStoreEnabled || nodeExecutionId == null) {
      return false;
    }
    try {
      int deleted = dsl.deleteFrom(TABLE).where(NODE_EXECUTION_ID.eq(nodeExecutionId)).execute();
      emitOperationMetric(OPERATION_DELETE, OUTCOME_SUCCESS);
      return deleted > 0;
    } catch (Exception ex) {
      log.error("[STEP_CONCURRENCY_QUEUE] delete failed nodeExecutionId={}", nodeExecutionId, ex);
      emitOperationMetric(OPERATION_DELETE, OUTCOME_ERROR);
      return false;
    }
  }

  /**
   * Fetches up to {@code limit} candidates in FIFO order. On Postgres failure returns an empty
   * list — ops observability comes from the error-counter metric (see the TODO in the catch
   * block), not from throwing. Callers (PR 5's tier-2 walker) must not confuse a genuinely empty
   * queue with a swallowed error; the metric-driven alert on the counter is the source of truth.
   */
  @Override
  public List<StepConcurrencyQueueEntry> fetchBatch(int limit) {
    if (!queueStoreEnabled) {
      return List.of();
    }
    try {
      List<StepConcurrencyQueueEntry> rows = new ArrayList<>(limit);
      dsl.select(NODE_EXECUTION_ID, PLAN_EXECUTION_ID, ACCOUNT_ID, CREATED_AT)
          .from(TABLE)
          .orderBy(CREATED_AT.asc())
          .limit(limit)
          .fetch()
          .forEach(record
              -> rows.add(StepConcurrencyQueueEntry.builder()
                              .nodeExecutionId(record.get(NODE_EXECUTION_ID))
                              .planExecutionId(record.get(PLAN_EXECUTION_ID))
                              .accountId(record.get(ACCOUNT_ID))
                              .createdAt(record.get(CREATED_AT) != null ? record.get(CREATED_AT).toInstant() : null)
                              .build()));
      emitOperationMetric(OPERATION_FETCH_BATCH, OUTCOME_SUCCESS);
      emitFetchBatchSizeMetric(rows.size());
      return rows;
    } catch (Exception ex) {
      log.error("[STEP_CONCURRENCY_QUEUE] fetchBatch failed limit={}", limit, ex);
      emitOperationMetric(OPERATION_FETCH_BATCH, OUTCOME_ERROR);
      return List.of();
    }
  }

  private void emitOperationMetric(String operation, String outcome) {
    try {
      try (PmsMetricContextGuard guard =
               new PmsMetricContextGuard(ImmutableMap.of("operation", operation, "outcome", outcome))) {
        metricService.incCounter(METRIC_QUEUE_OPERATIONS);
      }
    } catch (Exception ex) {
      log.debug(
          "[STEP_CONCURRENCY_QUEUE] operation metric emission failed operation={} outcome={}", operation, outcome, ex);
    }
  }

  private void emitFetchBatchSizeMetric(int batchSize) {
    try {
      metricService.recordMetric(METRIC_QUEUE_FETCH_BATCH_SIZE, batchSize);
    } catch (Exception ex) {
      log.debug("[STEP_CONCURRENCY_QUEUE] fetch batch size metric emission failed", ex);
    }
  }
}
