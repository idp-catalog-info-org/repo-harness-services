/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.planqueue;

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
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

/**
 * JOOQ-backed store for {@code plan_creation_queue}. Uses the shared
 * {@code PipelineServiceDSLContext} — no separate connection pool.
 *
 * <p>Uses jOOQ's dynamic DSL (table/field name strings) rather than generated types, matching
 * {@code StepConcurrencyQueueServiceImpl}. To swap in generated types later, add
 * {@code plan_creation_queue} to the jOOQ codegen {@code includes} list (see
 * {@code 959-psql-database-models/README.md}).
 *
 * <p>All methods are best-effort on the hot orchestration path: exceptions are logged and
 * swallowed. A lost queue row is reconciled by the backfill / rebuild and the self-healing drain;
 * the caller must never be blocked by a Postgres blip.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanCreationDbQueueServiceImpl implements PlanCreationDbQueueService {
  public static final String METRIC_QUEUE_OPERATIONS = "pipeline_plan_concurrency_queue_operations_total";
  public static final String METRIC_QUEUE_FETCH_BATCH_SIZE = "pipeline_plan_concurrency_queue_fetch_batch_size";

  private static final String OPERATION_INSERT = "insert";
  private static final String OPERATION_DELETE = "delete";
  private static final String OPERATION_FETCH_BATCH = "fetch_batch";
  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_ERROR = "error";

  private static final Table<Record> TABLE = DSL.table("plan_creation_queue");
  private static final Field<String> PLAN_EXECUTION_ID = DSL.field("plan_execution_id", SQLDataType.VARCHAR);
  private static final Field<String> ACCOUNT_ID = DSL.field("account_id", SQLDataType.VARCHAR);
  private static final Field<String> ORG_ID = DSL.field("org_id", SQLDataType.VARCHAR);
  private static final Field<String> PROJECT_ID = DSL.field("project_id", SQLDataType.VARCHAR);
  private static final Field<String> PARENT_UNIQUE_ID = DSL.field("parent_unique_id", SQLDataType.VARCHAR);
  private static final Field<String> PRIORITY_TYPE = DSL.field("priority_type", SQLDataType.VARCHAR);
  private static final Field<OffsetDateTime> CREATED_AT = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE);

  private final DSLContext dsl;
  private final boolean queueStoreEnabled;
  private final MetricService metricService;
  // Rate-limit fail-noise logs so a sustained Postgres outage doesn't flood logs (one WARN per
  // minute per JVM per operation). Non-blocking: on any Postgres failure we return safe defaults
  // (empty batch / false) and let the poller keep running.
  private static final long LOG_INTERVAL_MS = 60_000L;
  private final AtomicLong lastFetchFailLogEpochMs = new AtomicLong(0);
  private final AtomicLong lastInsertFailLogEpochMs = new AtomicLong(0);
  private final AtomicLong lastDeleteFailLogEpochMs = new AtomicLong(0);

  @Inject
  public PlanCreationDbQueueServiceImpl(@Named("PipelineServiceDSLContext") DSLContext dsl,
      @Named("useDbQueueForPlanCreation") boolean queueStoreEnabled, MetricService metricService) {
    this.dsl = dsl;
    this.queueStoreEnabled = queueStoreEnabled;
    this.metricService = metricService;
  }

  // Rate-limit helper: log the full stack once per LOG_INTERVAL_MS per op, otherwise emit a single
  // concise WARN line (no stack) so a sustained outage doesn't spam logs.
  private void logRateLimited(AtomicLong lastLogEpochMs, String tag, Exception ex) {
    long now = System.currentTimeMillis();
    long last = lastLogEpochMs.get();
    if (now - last >= LOG_INTERVAL_MS && lastLogEpochMs.compareAndSet(last, now)) {
      log.error("[PLAN_CREATION_QUEUE] {} failed", tag, ex);
    } else {
      log.warn("[PLAN_CREATION_QUEUE] {} failed: {}: {}", tag, ex.getClass().getSimpleName(), ex.getMessage());
    }
  }

  @Override
  public void insert(PlanCreationDbQueueEntry entry) {
    if (!queueStoreEnabled) {
      return;
    }
    if (entry == null || entry.getPlanExecutionId() == null) {
      log.warn("[PLAN_CREATION_QUEUE] refusing to insert null entry / null planExecutionId");
      return;
    }
    try {
      OffsetDateTime createdAt =
          OffsetDateTime.ofInstant(entry.getCreatedAt() != null ? entry.getCreatedAt() : Instant.now(), ZoneOffset.UTC);
      dsl.insertInto(TABLE)
          .set(PLAN_EXECUTION_ID, entry.getPlanExecutionId())
          .set(ACCOUNT_ID, entry.getAccountId())
          .set(ORG_ID, entry.getOrgId())
          .set(PROJECT_ID, entry.getProjectId())
          .set(PARENT_UNIQUE_ID, entry.getParentUniqueId())
          .set(PRIORITY_TYPE, entry.getPriorityType())
          .set(CREATED_AT, createdAt)
          .onConflict(PLAN_EXECUTION_ID)
          .doNothing()
          .execute();
      emitOperationMetric(OPERATION_INSERT, OUTCOME_SUCCESS);
    } catch (Exception ex) {
      logRateLimited(lastInsertFailLogEpochMs, "insert planExecutionId=" + entry.getPlanExecutionId(), ex);
      emitOperationMetric(OPERATION_INSERT, OUTCOME_ERROR);
    }
  }

  @Override
  public boolean deleteByPlanExecutionId(String planExecutionId) {
    if (!queueStoreEnabled || planExecutionId == null) {
      return false;
    }
    try {
      int deleted = dsl.deleteFrom(TABLE).where(PLAN_EXECUTION_ID.eq(planExecutionId)).execute();
      emitOperationMetric(OPERATION_DELETE, OUTCOME_SUCCESS);
      return deleted > 0;
    } catch (Exception ex) {
      logRateLimited(lastDeleteFailLogEpochMs, "delete planExecutionId=" + planExecutionId, ex);
      emitOperationMetric(OPERATION_DELETE, OUTCOME_ERROR);
      return false;
    }
  }

  @Override
  public List<PlanCreationDbQueueEntry> fetchBatch(int limit) {
    if (!queueStoreEnabled) {
      return List.of();
    }
    try {
      List<PlanCreationDbQueueEntry> rows = new ArrayList<>(limit);
      dsl.select(PLAN_EXECUTION_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, PARENT_UNIQUE_ID, PRIORITY_TYPE, CREATED_AT)
          .from(TABLE)
          .orderBy(CREATED_AT.asc())
          .limit(limit)
          .fetch()
          .forEach(record
              -> rows.add(PlanCreationDbQueueEntry.builder()
                              .planExecutionId(record.get(PLAN_EXECUTION_ID))
                              .accountId(record.get(ACCOUNT_ID))
                              .orgId(record.get(ORG_ID))
                              .projectId(record.get(PROJECT_ID))
                              .parentUniqueId(record.get(PARENT_UNIQUE_ID))
                              .priorityType(record.get(PRIORITY_TYPE))
                              .createdAt(record.get(CREATED_AT) != null ? record.get(CREATED_AT).toInstant() : null)
                              .build()));
      emitOperationMetric(OPERATION_FETCH_BATCH, OUTCOME_SUCCESS);
      emitFetchBatchSizeMetric(rows.size());
      return rows;
    } catch (Exception ex) {
      logRateLimited(lastFetchFailLogEpochMs, "fetchBatch limit=" + limit, ex);
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
          "[PLAN_CREATION_QUEUE] operation metric emission failed operation={} outcome={}", operation, outcome, ex);
    }
  }

  private void emitFetchBatchSizeMetric(int batchSize) {
    try {
      metricService.recordMetric(METRIC_QUEUE_FETCH_BATCH_SIZE, batchSize);
    } catch (Exception ex) {
      log.debug("[PLAN_CREATION_QUEUE] fetch batch size metric emission failed", ex);
    }
  }
}
