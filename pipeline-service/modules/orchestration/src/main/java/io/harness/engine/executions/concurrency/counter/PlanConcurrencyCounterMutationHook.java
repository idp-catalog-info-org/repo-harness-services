/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Post-commit hook that keeps the plan-level per-project and per-account Redis counters in sync with
 * pipeline-execution status transitions. Plan-level analogue of
 * {@code StepConcurrencyCounterMutationHook}.
 *
 * <p>An execution "occupies a slot" while in {@link StatusUtils#activeStatuses()}. Each transition
 * is classified as an entry (+1), exit (-1) or no-op (0) and applied to both counters — see
 * {@link #occupiesSlot}. {@code APPROVAL_WAITING} is excluded: an approval can wait on a human
 * indefinitely and must not hold a slot that starves other executions.
 *
 * <p>Best-effort: runs after the Mongo status commit, never throws back to the caller, and drift is
 * reconciled by the daily rebuild. Inert unless the caller opted in (per-project mode FF) and the
 * {@code planConcurrencyCounterMutationEnabled} kill switch is on.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanConcurrencyCounterMutationHook {
  public static final String METRIC_COUNTER_MUTATIONS = "pipeline_plan_concurrency_counter_mutations_total";

  private static final String SCOPE_ACCOUNT = "account";
  private static final String SCOPE_PROJECT = "project";
  private static final String OPERATION_INCREMENT = "increment";
  private static final String OPERATION_DECREMENT = "decrement";
  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_ERROR = "error";

  // One-shot suppression of the admission +1 that an atomic reserve already applied. The drain path
  // reserves the slot before flipping to STARTING_PLAN_CREATION; without this the hook would count
  // it again on that transition. The matching -1 still fires later, keeping it balanced.
  private static final ThreadLocal<Boolean> SUPPRESS_NEXT_INCREMENT = ThreadLocal.withInitial(() -> Boolean.FALSE);

  private final PlanConcurrencyCounterService counterService;
  private final boolean mutationEnabled;
  private final MetricService metricService;

  @Inject
  public PlanConcurrencyCounterMutationHook(PlanConcurrencyCounterService counterService,
      @Named("planConcurrencyCounterMutationEnabled") boolean mutationEnabled, MetricService metricService) {
    this.counterService = counterService;
    this.mutationEnabled = mutationEnabled;
    this.metricService = metricService;
  }

  public boolean isEnabled() {
    return mutationEnabled;
  }

  /**
   * Run {@code action} with the next increment suppressed on this thread (a reserve already applied
   * that {@code +1}). Wrap only the admission {@code updateStatus} that flips a reserved execution
   * to its active status. Consumed at most once; decrements are never suppressed.
   */
  public static <T> T runWithAdmissionIncrementSuppressed(Supplier<T> action) {
    boolean previous = SUPPRESS_NEXT_INCREMENT.get();
    SUPPRESS_NEXT_INCREMENT.set(Boolean.TRUE);
    try {
      return action.get();
    } finally {
      SUPPRESS_NEXT_INCREMENT.set(previous);
    }
  }

  /**
   * Apply the counter delta for a single pipeline-execution status transition. {@code oldStatus}
   * may be null (execution created directly into {@code newStatus}).
   */
  public void onStatusChange(Map<String, String> setupAbstractions, Status oldStatus, Status newStatus) {
    if (!mutationEnabled) {
      return;
    }
    try {
      int delta = classifyTransition(oldStatus, newStatus);
      if (delta == 0) {
        return;
      }
      // If a reserve already applied the +1, consume the one-shot flag and skip this increment
      // (never a decrement — a -1 must always land to release the slot).
      if (delta > 0 && consumeSuppressNextIncrement()) {
        log.debug("[PLAN_CONCURRENCY] admission +1 suppressed for transition {} -> {} (owned by reserve)", oldStatus,
            newStatus);
        return;
      }
      String accountId = value(setupAbstractions, SetupAbstractionKeys.accountId);
      // parentUniqueId = the project's stable DB uniqueId; survives org moves, so the per-project
      // counter follows the project.
      String parentUniqueId = value(setupAbstractions, SetupAbstractionKeys.parentUniqueId);
      if (accountId == null) {
        return;
      }
      String operation = delta > 0 ? OPERATION_INCREMENT : OPERATION_DECREMENT;
      boolean accountSucceeded = applyOne(() -> counterService.incrementAccount(accountId, delta));
      emitMutationMetric(SCOPE_ACCOUNT, operation, accountId, accountSucceeded ? OUTCOME_SUCCESS : OUTCOME_ERROR);
      // Only mutate the project counter when we have a stable project id. If parentUniqueId is
      // absent, skip the project leg rather than key by an empty scope — the account counter still
      // enforces the global ceiling and the daily rebuild backfills the project counter later.
      if (parentUniqueId != null && !parentUniqueId.isEmpty()) {
        boolean projectSucceeded = applyOne(() -> counterService.incrementProject(accountId, parentUniqueId, delta));
        emitMutationMetric(SCOPE_PROJECT, operation, accountId, projectSucceeded ? OUTCOME_SUCCESS : OUTCOME_ERROR);
      }
    } catch (Exception ex) {
      // Never let counter accounting break the status-transition path; rebuild reconciles drift.
      log.warn("[PLAN_CONCURRENCY] counter mutation failed for transition {} -> {}", oldStatus, newStatus, ex);
    }
  }

  // Reads and resets the flag in one call so it is honoured for exactly one increment.
  private static boolean consumeSuppressNextIncrement() {
    if (Boolean.TRUE.equals(SUPPRESS_NEXT_INCREMENT.get())) {
      SUPPRESS_NEXT_INCREMENT.set(Boolean.FALSE);
      return true;
    }
    return false;
  }

  /** +1 entering the slot set, -1 leaving it, 0 no-op. */
  private int classifyTransition(Status oldStatus, Status newStatus) {
    boolean wasIn = occupiesSlot(oldStatus);
    boolean isIn = occupiesSlot(newStatus);
    if (!wasIn && isIn) {
      return 1;
    }
    if (wasIn && !isIn) {
      return -1;
    }
    return 0;
  }

  // APPROVAL_WAITING is excluded: an approval can wait on a human indefinitely and must not hold a
  // slot that starves other executions.
  private boolean occupiesSlot(Status status) {
    return status != null && status != Status.APPROVAL_WAITING && StatusUtils.activeStatuses().contains(status);
  }

  private boolean applyOne(Runnable r) {
    try {
      r.run();
      return true;
    } catch (Exception ex) {
      log.warn("[PLAN_CONCURRENCY] counter leg failed", ex);
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
      log.debug("[PLAN_CONCURRENCY] counter mutation metric emission failed", ex);
    }
  }

  private static String value(Map<String, String> map, String key) {
    return map == null ? null : map.get(key);
  }
}
