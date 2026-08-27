/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency;

import static io.harness.pms.contracts.execution.Status.STARTING_QUEUED_STEP;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterGate;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterMutationHook;
import io.harness.engine.executions.concurrency.queue.StepConcurrencyQueueEntry;
import io.harness.engine.executions.concurrency.queue.StepConcurrencyQueueService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class StepConcurrencyHelper {
  public static final int TIER2_BATCH_SIZE = 100;

  public static final String METRIC_DEQUEUE_TOTAL = "pipeline_step_concurrency_dequeue_total";
  public static final String METRIC_DEQUEUE_WALK_LENGTH = "pipeline_step_concurrency_dequeue_walk_length";
  public static final String METRIC_DEQUEUE_SKIPS = "pipeline_step_concurrency_dequeue_skips_total";

  private static final String TIER_SAME_PLAN = "same_plan";
  private static final String TIER_POSTGRES_FIFO = "postgres_fifo";
  private static final String OUTCOME_CLAIMED = "claimed";
  private static final String OUTCOME_WALK_EXHAUSTED = "walk_exhausted";
  private static final String OUTCOME_CAP_REACHED = "cap_reached";
  private static final String SKIP_REASON_ACCOUNT_CAP = "account_cap";
  private static final String SKIP_REASON_ALREADY_DELETED = "already_deleted";
  private static final String SKIP_REASON_MONGO_PREDICATE_MISS = "mongo_predicate_miss";

  @Inject NodeExecutionService nodeExecutionService;
  @Inject PipelineSettingsService pipelineSettingsService;
  @Inject StepConcurrencyQueueService queueService;
  @Inject StepConcurrencyCounterGate counterGate;
  @Inject StepConcurrencyCounterMutationHook counterMutationHook;
  @Inject MetricService metricService;

  public boolean shouldQueue(ExecutionMode mode, Ambiance ambiance) {
    if (!ExecutionModeUtils.isLeafMode(mode)) {
      return false;
    }
    return shouldQueue(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
  }

  public boolean shouldQueue(Ambiance ambiance) {
    return shouldQueue(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
  }

  /**
   * Checks if the given planExecution is running at max concurrency. Counts active statuses including
   * STARTING_QUEUED_STEP so that nodes that have claimed a slot but haven't fully started are counted.
   */
  private boolean shouldQueue(String accountId, String planExecutionId) {
    long runningLeafCount = nodeExecutionService.getCountOfLeafStepsWithGivenStatuses(
        planExecutionId, StatusUtils.ACTIVE_STATUSES_WITH_QUEUED_STEP_CONCURRENCY_STATUS);
    return isRunningCountGreaterThanLimit(accountId, runningLeafCount);
  }

  public boolean shouldStartQueuedStep(ExecutionMode mode, Ambiance ambiance) {
    if (!ExecutionModeUtils.isLeafMode(mode)) {
      return false;
    }
    return getAvailableSlotsForQueuedSteps(ambiance) > 0;
  }

  /**
   * Returns the number of queued steps that can be started without exceeding the concurrency limit.
   * Uses ACTIVE_STATUSES_WITH_QUEUED_STEP_CONCURRENCY_STATUS to include STARTING_QUEUED_STEP in the count,
   * preventing the race where multiple completing steps each dequeue a new step simultaneously.
   */
  public int getAvailableSlotsForQueuedSteps(Ambiance ambiance) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    long runningLeafCount = nodeExecutionService.getCountOfLeafStepsWithGivenStatuses(
        planExecutionId, StatusUtils.ACTIVE_STATUSES_WITH_QUEUED_STEP_CONCURRENCY_STATUS);
    int maxConcurrency = pipelineSettingsService.getMaxStepConcurrency(accountId);
    if (maxConcurrency <= 0) {
      return 0;
    }
    return (int) Math.max(0, maxConcurrency - runningLeafCount);
  }

  private boolean isRunningCountGreaterThanLimit(String accountId, long runningLeafCount) {
    int maxConcurrency = pipelineSettingsService.getMaxStepConcurrency(accountId);
    return maxConcurrency <= 0 || runningLeafCount > maxConcurrency;
  }

  /**
   * Two-tier dequeue.
   *
   * <p>Tier 1: same-plan Mongo {@code findAndModify}. Common case — a step completing on plan P
   * wakes P's own next queued row. No Postgres round-trip. On success, the Postgres queue row (if
   * any — the counter-gate FF may not have been on when the row was queued) is best-effort
   * deleted inline to keep the queue store in sync.
   *
   * <p>Tier 2 (fallback): cross-plan Postgres FIFO walk. Only consulted when the counter-gate
   * queue store is populated (i.e. some other plan queued a row we're eligible to drain). Fetch
   * top-{@value TIER2_BATCH_SIZE}, walk in-app checking each candidate's counters, atomic
   * {@code DELETE ... RETURNING} to claim, then Mongo {@code findAndModify} to flip status.
   * Self-heals on Mongo predicate miss (Postgres row orphaned by a pod crash mid queue-in).
   *
   * <p>Fires the counter mutation hook on successful claim — the QSLR &rarr;
   * STARTING_QUEUED_STEP transition happens via a query-based update, not
   * {@code updateStatusWithOps}, so the hook must be invoked directly.
   */
  public Ambiance findQueuedNode(String completingPlanExecutionId) {
    Ambiance tier1 = tier1SamePlanClaim(completingPlanExecutionId);
    if (tier1 != null) {
      return tier1;
    }
    return tier2ClusterWideClaim();
  }

  private Ambiance tier1SamePlanClaim(String planExecutionId) {
    if (planExecutionId == null || planExecutionId.isEmpty()) {
      return null;
    }
    try {
      Criteria criteria = where(NodeExecutionKeys.planExecutionId)
                              .is(planExecutionId)
                              .and(NodeExecutionKeys.mode)
                              .in(ExecutionModeUtils.leafModes())
                              .and(NodeExecutionKeys.status)
                              .is(Status.QUEUED_STEP_LIMIT_REACHED);
      Query query = new Query(criteria).with(Sort.by(NodeExecutionKeys.createdAt));
      query.fields()
          .include(NodeExecutionKeys.ambiance)
          .include(NodeExecutionKeys.executionContext)
          .include(NodeExecutionKeys.mode);
      Update update = new Update();
      update.set(NodeExecutionKeys.status, STARTING_QUEUED_STEP);
      NodeExecution claimed = nodeExecutionService.updateUsingQuery(query, update);
      if (claimed == null) {
        emitDequeueOutcome(TIER_SAME_PLAN, OUTCOME_WALK_EXHAUSTED);
        return null;
      }
      fireDequeueClaimHook(claimed);
      deletePostgresRowBestEffort(claimed.getUuid());
      emitDequeueOutcome(TIER_SAME_PLAN, OUTCOME_CLAIMED);
      return nodeExecutionService.getAmbiance(claimed);
    } catch (Exception ex) {
      log.warn("[STEP_CONCURRENCY] tier-1 claim failed for planExecutionId={}", planExecutionId, ex);
      return null;
    }
  }

  private Ambiance tier2ClusterWideClaim() {
    List<StepConcurrencyQueueEntry> batch;
    try {
      batch = queueService.fetchBatch(TIER2_BATCH_SIZE);
    } catch (Exception ex) {
      log.warn("[STEP_CONCURRENCY] tier-2 fetchBatch failed", ex);
      return null;
    }
    if (batch.isEmpty()) {
      emitWalkLength(0);
      return null;
    }
    Set<String> accountsWithoutHeadroom = new HashSet<>();
    int walkLength = 0;
    // Tracks whether every candidate skipped so far was skipped for capacity reasons only — used
    // to distinguish a capacity-bound exhaustion (outcome=cap_reached) from an exhaustion caused
    // by other transient reasons (outcome=walk_exhausted) once the walk finishes empty-handed.
    boolean sawNonCapSkip = false;
    for (StepConcurrencyQueueEntry candidate : batch) {
      walkLength++;
      try {
        String accountId = candidate.getAccountId();
        if (accountsWithoutHeadroom.contains(accountId)) {
          emitDequeueSkip(SKIP_REASON_ACCOUNT_CAP);
          continue;
        }
        // Consult the counter gate for headroom before consuming this candidate. Skip if at cap.
        if (!counterGate.hasHeadroomFor(accountId)) {
          accountsWithoutHeadroom.add(accountId);
          emitDequeueSkip(SKIP_REASON_ACCOUNT_CAP);
          continue;
        }
        // Atomic claim: DELETE RETURNING gives us race-free ownership. false = another pod won.
        if (!queueService.deleteByNodeExecutionId(candidate.getNodeExecutionId())) {
          sawNonCapSkip = true;
          emitDequeueSkip(SKIP_REASON_ALREADY_DELETED);
          continue;
        }
        Ambiance ambiance = claimQueuedNodeInMongo(candidate.getNodeExecutionId());
        if (ambiance == null) {
          log.info(
              "[STEP_CONCURRENCY] tier-2 self-heal — Postgres row {} had no matching QSLR Mongo row; continuing walk",
              candidate.getNodeExecutionId());
          sawNonCapSkip = true;
          emitDequeueSkip(SKIP_REASON_MONGO_PREDICATE_MISS);
          continue;
        }
        emitWalkLength(walkLength);
        emitDequeueOutcome(TIER_POSTGRES_FIFO, OUTCOME_CLAIMED);
        return ambiance;
      } catch (Exception ex) {
        // A single candidate flaking (Postgres/Mongo blip) shouldn't strand the rest of the batch
        // un-drained until another pod's fetch picks it up.
        sawNonCapSkip = true;
        log.warn("[STEP_CONCURRENCY] tier-2 candidate {} failed; continuing walk", candidate.getNodeExecutionId(), ex);
      }
    }
    log.debug("[STEP_CONCURRENCY] tier-2 walk exhausted {} candidates without a claim", batch.size());
    emitWalkLength(walkLength);
    emitDequeueOutcome(TIER_POSTGRES_FIFO, sawNonCapSkip ? OUTCOME_WALK_EXHAUSTED : OUTCOME_CAP_REACHED);
    return null;
  }

  private Ambiance claimQueuedNodeInMongo(String nodeExecutionId) {
    Query query = new Query(where(NodeExecutionKeys.uuid)
                                .is(nodeExecutionId)
                                .and(NodeExecutionKeys.status)
                                .is(Status.QUEUED_STEP_LIMIT_REACHED));
    query.fields()
        .include(NodeExecutionKeys.ambiance)
        .include(NodeExecutionKeys.executionContext)
        .include(NodeExecutionKeys.mode);
    Update update = new Update();
    update.set(NodeExecutionKeys.status, STARTING_QUEUED_STEP);
    NodeExecution claimed = nodeExecutionService.updateUsingQuery(query, update);
    if (claimed == null) {
      return null;
    }
    fireDequeueClaimHook(claimed);
    return nodeExecutionService.getAmbiance(claimed);
  }

  private void fireDequeueClaimHook(NodeExecution claimed) {
    // QSLR (out of slot set) -> STARTING_QUEUED_STEP (in slot set) = +1. The transition bypasses
    // updateStatusWithOps, so we call the hook directly. Only leaf modes reach QSLR — if the
    // projection didn't populate mode we fall back to TASK so the hook still fires.
    ExecutionMode claimedMode = claimed.getMode();
    if (claimedMode == null || !ExecutionModeUtils.isLeafMode(claimedMode)) {
      claimedMode = ExecutionMode.TASK;
    }
    String accountId = claimed.getAmbiance() == null ? null : AmbianceUtils.getAccountId(claimed.getAmbiance());
    counterMutationHook.onStatusChange(accountId, claimedMode, Status.QUEUED_STEP_LIMIT_REACHED, STARTING_QUEUED_STEP);
  }

  private void deletePostgresRowBestEffort(String nodeExecutionId) {
    try {
      queueService.deleteByNodeExecutionId(nodeExecutionId);
    } catch (Exception ex) {
      log.warn("[STEP_CONCURRENCY] tier-1 Postgres delete failed for nodeExecutionId={} (self-heal on next fetch)",
          nodeExecutionId, ex);
    }
  }

  private void emitDequeueOutcome(String tier, String outcome) {
    try {
      ImmutableMap<String, String> labels = ImmutableMap.of("tier", tier, "outcome", outcome);
      try (PmsMetricContextGuard guard = new PmsMetricContextGuard(labels)) {
        metricService.incCounter(METRIC_DEQUEUE_TOTAL);
      }
    } catch (Exception ex) {
      log.debug("[STEP_CONCURRENCY] dequeue outcome metric emission failed", ex);
    }
  }

  private void emitDequeueSkip(String skipReason) {
    try {
      ImmutableMap<String, String> labels = ImmutableMap.of("skip_reason", skipReason);
      try (PmsMetricContextGuard guard = new PmsMetricContextGuard(labels)) {
        metricService.incCounter(METRIC_DEQUEUE_SKIPS);
      }
    } catch (Exception ex) {
      log.debug("[STEP_CONCURRENCY] dequeue skip metric emission failed", ex);
    }
  }

  private void emitWalkLength(int walkLength) {
    try (PmsMetricContextGuard guard = new PmsMetricContextGuard(ImmutableMap.of())) {
      metricService.recordMetric(METRIC_DEQUEUE_WALK_LENGTH, walkLength);
    } catch (Exception ex) {
      log.debug("[STEP_CONCURRENCY] dequeue walk length metric emission failed", ex);
    }
  }
}
