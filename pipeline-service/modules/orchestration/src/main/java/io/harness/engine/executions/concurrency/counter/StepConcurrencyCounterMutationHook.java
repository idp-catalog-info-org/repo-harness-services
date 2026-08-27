/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.ExecutionModeUtils;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

/**
 * Post-commit hook that mutates the Redis account + cluster counters based on a leaf node's
 * status transition. Runs synchronously in the caller's thread after the Mongo status commit;
 * exceptions are caught and swallowed inline — orchestration progress is never blocked on a
 * counter write. Drift accumulates and is reconciled by the daily rebuild.
 *
 * <p>See {@link StatusUtils#ACTIVE_STATUSES_OCCUPYING_STEP_CONCURRENCY_SLOT} for the definition
 * of "occupying a slot". A transition into that set is +1, out of the set is −1, both-in or
 * both-out is a no-op.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class StepConcurrencyCounterMutationHook {
  public static final String METRIC_COUNTER_MUTATIONS = "pipeline_step_concurrency_counter_mutations_total";

  private static final String SCOPE_ACCOUNT = "account";
  private static final String SCOPE_CLUSTER = "cluster";
  private static final String OPERATION_INCREMENT = "increment";
  private static final String OPERATION_DECREMENT = "decrement";
  private static final String OPERATION_BULK_ENTRY = "bulk_entry";
  private static final String OPERATION_BULK_EXIT = "bulk_exit";
  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_ERROR = "error";

  private final StepConcurrencyCounterService counterService;
  private final boolean mutationEnabled;
  private final MetricService metricService;

  @Inject
  public StepConcurrencyCounterMutationHook(StepConcurrencyCounterService counterService,
      @Named("stepConcurrencyCounterMutationEnabled") boolean mutationEnabled, MetricService metricService) {
    this.counterService = counterService;
    this.mutationEnabled = mutationEnabled;
    this.metricService = metricService;
  }

  /**
   * Callers on hot paths (e.g. NodeExecutionServiceImpl.updateStatusWithOps) should consult this
   * before performing any hook-supporting work (such as a pre-update projection read) so the
   * kill switch also gates that setup cost, not just the Redis write.
   */
  public boolean isEnabled() {
    return mutationEnabled;
  }

  /**
   * Fire counter deltas for a single-row status transition. No-op if:
   * <ul>
   *   <li>Mutation is globally disabled ({@code stepConcurrencyCounterMutationEnabled=false}).</li>
   *   <li>The mode isn't a leaf mode (pipelines, stages, strategy nodes never occupy a slot).</li>
   *   <li>The old→new pair produces a zero delta (both statuses inside the slot set, or both outside).</li>
   * </ul>
   *
   * <p>{@code oldStatus} may be null (e.g., row didn't exist before or projection failed). We
   * conservatively treat null as "outside the slot set".
   */
  public void onStatusChange(String accountId, ExecutionMode mode, Status oldStatus, Status newStatus) {
    int delta = classifyTransition(oldStatus, newStatus);
    boolean leaf = isLeafMode(mode);
    log.debug("[STEP_CONCURRENCY_HOOK] onStatusChange account={} mode={} leaf={} old={} new={} delta={} enabled={}",
        accountId, mode, leaf, oldStatus, newStatus, delta, mutationEnabled);
    if (!mutationEnabled) {
      return;
    }
    if (!leaf) {
      return;
    }
    if (accountId == null || accountId.isEmpty()) {
      log.warn("[STEP_CONCURRENCY] StepConcurrencyCounterMutationHook received null/empty accountId; skipping counter "
          + "mutation");
      return;
    }
    if (delta == 0) {
      return;
    }
    applyDelta(accountId, delta, delta > 0 ? OPERATION_INCREMENT : OPERATION_DECREMENT);
  }

  /**
   * Bulk decrement path. Used by {@code errorOutActiveNodes} and similar bulk cleanups where N
   * leaf rows leave the slot set atomically. Caller is responsible for scoping N to leaf rows
   * only (via {@code mode IN leafModes()} on the bulk query).
   */
  public void onBulkExit(String accountId, long count) {
    if (!mutationEnabled || count <= 0) {
      return;
    }
    if (accountId == null || accountId.isEmpty()) {
      log.warn("[STEP_CONCURRENCY] onBulkExit received null/empty accountId; skipping counter mutation");
      return;
    }
    applyDelta(accountId, -Math.toIntExact(Math.min(count, Integer.MAX_VALUE)), OPERATION_BULK_EXIT);
  }

  /**
   * Bulk increment path. Used by the split {@code *Discontinuing} handlers when rows in
   * {@code QUEUED_STEP_LIMIT_REACHED} bulk-transition into the slot set.
   */
  public void onBulkEntry(String accountId, long count) {
    if (!mutationEnabled || count <= 0) {
      return;
    }
    if (accountId == null || accountId.isEmpty()) {
      log.warn("[STEP_CONCURRENCY] onBulkEntry received null/empty accountId; skipping counter mutation");
      return;
    }
    applyDelta(accountId, Math.toIntExact(Math.min(count, Integer.MAX_VALUE)), OPERATION_BULK_ENTRY);
  }

  private void applyDelta(String accountId, int delta, String operation) {
    // Each leg is applied independently and best-effort. No application-layer retry: the counter
    // service (StepConcurrencyCounterServiceImpl) does a bounded async wait on the Redisson
    // future, and Redisson runs its own retry loop in the background. Adding another retry here
    // would only stack Thread.sleep on the hot orchestration path. On failure we log and move on;
    // drift accumulates and the daily rebuild reconciles.
    boolean accountSucceeded =
        applyOne(SCOPE_ACCOUNT, delta, accountId, () -> counterService.incrementAccount(accountId, delta));
    emitMutationMetric(SCOPE_ACCOUNT, operation, accountId, accountSucceeded ? OUTCOME_SUCCESS : OUTCOME_ERROR);

    boolean clusterSucceeded = applyOne(SCOPE_CLUSTER, delta, accountId, () -> counterService.incrementCluster(delta));
    emitMutationMetric(SCOPE_CLUSTER, operation, accountId, clusterSucceeded ? OUTCOME_SUCCESS : OUTCOME_ERROR);
  }

  private boolean applyOne(String scope, int delta, String accountId, Runnable op) {
    try {
      op.run();
      return true;
    } catch (Exception ex) {
      log.error("[STEP_CONCURRENCY] {} counter mutation abandoned; drift will accumulate. account={} delta={}", scope,
          accountId, delta, ex);
      return false;
    }
  }

  private void emitMutationMetric(String scope, String operation, String accountId, String outcome) {
    try {
      ImmutableMap.Builder<String, String> labels = ImmutableMap.<String, String>builder()
                                                        .put("scope", scope)
                                                        .put("operation", operation)
                                                        .put("outcome", outcome);
      if (SCOPE_ACCOUNT.equals(scope) && accountId != null) {
        labels.put("accountId", accountId);
      }
      try (PmsMetricContextGuard guard = new PmsMetricContextGuard(labels.build())) {
        metricService.incCounter(METRIC_COUNTER_MUTATIONS);
      }
    } catch (Exception ex) {
      log.debug("[STEP_CONCURRENCY] counter mutation metric emission failed", ex);
    }
  }

  private static int classifyTransition(Status oldStatus, Status newStatus) {
    boolean oldInSlot = isInSlotSet(oldStatus);
    boolean newInSlot = isInSlotSet(newStatus);
    if (oldInSlot == newInSlot) {
      return 0;
    }
    return newInSlot ? 1 : -1;
  }

  private static boolean isInSlotSet(Status status) {
    return status != null && StatusUtils.ACTIVE_STATUSES_OCCUPYING_STEP_CONCURRENCY_SLOT.contains(status);
  }

  private static boolean isLeafMode(ExecutionMode mode) {
    return mode != null && ExecutionModeUtils.isLeafMode(mode);
  }
}
