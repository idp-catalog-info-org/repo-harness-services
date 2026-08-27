/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.rebuild;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterKey;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconciles the per-project / per-account plan concurrency Redis counters against Mongo (the
 * source of truth). Recomputes the count of slot-occupying executions ({@link
 * StatusUtils#activeStatuses()} minus {@code APPROVAL_WAITING}, which can wait on a human
 * indefinitely and must not hold a slot) grouped by account and by project, overwrites the
 * counters, and zeroes counters whose scope no longer has any active executions. The same
 * absolute-overwrite pass is also the backfill when the feature is first enabled.
 *
 * <p>Leader-elected so only one pod runs it. {@link #rebuild()} is also the method the admin
 * recompute endpoint invokes directly (bypassing the lock).
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanConcurrencyCounterRebuildJob implements Runnable {
  private static final String LOCK_NAME = "PlanConcurrencyCounterRebuildJob";
  private static final Set<String> PROJECTION_FIELDS = Set.of(PlanExecutionKeys.setupAbstractions);

  public static final String METRIC_COUNTER_DRIFT = "pipeline_plan_concurrency_counter_drift";
  public static final String METRIC_REBUILD_RUN_TOTAL = "pipeline_plan_concurrency_rebuild_run_total";
  public static final String METRIC_REBUILD_ACCOUNTS_RECOMPUTED =
      "pipeline_plan_concurrency_rebuild_accounts_recomputed";
  public static final String METRIC_REBUILD_PROJECTS_RECOMPUTED =
      "pipeline_plan_concurrency_rebuild_projects_recomputed";
  public static final String METRIC_REBUILD_STALE_ACCOUNTS_ZEROED =
      "pipeline_plan_concurrency_rebuild_stale_accounts_zeroed";
  public static final String METRIC_REBUILD_STALE_PROJECTS_ZEROED =
      "pipeline_plan_concurrency_rebuild_stale_projects_zeroed";

  private static final String SCOPE_ACCOUNT = "account";
  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_LEADER_LOCK_HELD_BY_OTHER = "leader_lock_held_by_other";
  private static final String OUTCOME_ERROR = "error";

  // Slot-occupying statuses for the plan-concurrency counters. This is StatusUtils#activeStatuses()
  // MINUS APPROVAL_WAITING: an approval can wait on a human indefinitely and must not hold a
  // concurrency slot at either the account or project level. Mirrors
  // PlanConcurrencyCounterMutationHook#occupiesSlot so the rebuild and the live hook agree.
  private static final EnumSet<Status> SLOT_OCCUPYING_STATUSES = buildSlotOccupyingStatuses();

  private static EnumSet<Status> buildSlotOccupyingStatuses() {
    EnumSet<Status> statuses = EnumSet.copyOf(StatusUtils.activeStatuses());
    statuses.remove(Status.APPROVAL_WAITING);
    return statuses;
  }

  @Inject private PlanExecutionService planExecutionService;
  @Inject private PlanConcurrencyCounterService counterService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private MetricService metricService;

  @Override
  public void run() {
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME, Duration.ofMinutes(30))) {
      if (lock == null) {
        log.info("[PLAN_CONCURRENCY_REBUILD] another pod holds the lock; skipping");
        emitRebuildRunMetric(OUTCOME_LEADER_LOCK_HELD_BY_OTHER);
        return;
      }
      rebuild();
      emitRebuildRunMetric(OUTCOME_SUCCESS);
    } catch (Exception ex) {
      log.error("[PLAN_CONCURRENCY_REBUILD] rebuild failed", ex);
      emitRebuildRunMetric(OUTCOME_ERROR);
    }
  }

  @VisibleForTesting
  public void rebuild() {
    Map<String, Long> accountCounts = new HashMap<>();
    Map<String, Long> projectCounts = new HashMap<>();
    try (Stream<PlanExecution> stream = planExecutionService.fetchPlanExecutionsByStatusFromAnalytics(
             SLOT_OCCUPYING_STATUSES, PROJECTION_FIELDS)) {
      for (PlanExecution planExecution : (Iterable<PlanExecution>) stream::iterator) {
        Map<String, String> setupAbstractions = planExecution.getSetupAbstractions();
        if (setupAbstractions == null) {
          continue;
        }
        String accountId = setupAbstractions.get(SetupAbstractionKeys.accountId);
        if (accountId == null) {
          continue;
        }
        accountCounts.merge(accountId, 1L, Long::sum);
        // Project counter is keyed by the stable parentUniqueId so it matches the live gate/hook and
        // survives org moves. Skip the project leg for executions with no resolvable parentUniqueId.
        String parentUniqueId = setupAbstractions.get(SetupAbstractionKeys.parentUniqueId);
        if (parentUniqueId != null && !parentUniqueId.isEmpty()) {
          projectCounts.merge(PlanConcurrencyCounterKey.projectScope(accountId, parentUniqueId), 1L, Long::sum);
        }
      }
    }

    // Number of scopes recomputed from Mongo (before we fold in stale-key zeroing below).
    int accountsRecomputed = accountCounts.size();
    int projectsRecomputed = projectCounts.size();

    // Previous Redis counts are the drift baseline (mongoCount - redisCountBeforeRecompute).
    Map<String, Long> previousAccountCounts = counterService.getAllAccountCounts();
    Map<String, Long> previousProjectCounts = counterService.getAllProjectCounts();

    // Zero out counters whose scope no longer has any active executions (so a stale key can't keep
    // throttling). Then overwrite the live counts.
    long staleAccountsZeroed =
        previousAccountCounts.entrySet()
            .stream()
            .filter(e -> !accountCounts.containsKey(e.getKey()) && e.getValue() != null && e.getValue() != 0L)
            .count();
    long staleProjectsZeroed =
        previousProjectCounts.entrySet()
            .stream()
            .filter(e -> !projectCounts.containsKey(e.getKey()) && e.getValue() != null && e.getValue() != 0L)
            .count();
    previousAccountCounts.keySet().forEach(accountId -> accountCounts.putIfAbsent(accountId, 0L));
    previousProjectCounts.keySet().forEach(scope -> projectCounts.putIfAbsent(scope, 0L));
    counterService.setAccountCounts(accountCounts);
    counterService.setProjectCounts(projectCounts);

    accountCounts.forEach(
        (accountId, count) -> emitDrift(SCOPE_ACCOUNT, accountId, count - previous(previousAccountCounts, accountId)));
    emitGauge(METRIC_REBUILD_ACCOUNTS_RECOMPUTED, accountsRecomputed);
    emitGauge(METRIC_REBUILD_PROJECTS_RECOMPUTED, projectsRecomputed);
    emitGauge(METRIC_REBUILD_STALE_ACCOUNTS_ZEROED, staleAccountsZeroed);
    emitGauge(METRIC_REBUILD_STALE_PROJECTS_ZEROED, staleProjectsZeroed);
    log.info("[PLAN_CONCURRENCY_REBUILD] rebuilt {} account counters and {} project counters", accountCounts.size(),
        projectCounts.size());
  }

  private static long previous(Map<String, Long> previousCounts, String key) {
    Long value = previousCounts.get(key);
    return value == null ? 0L : value;
  }

  private void emitDrift(String scope, String accountId, long drift) {
    try {
      ImmutableMap.Builder<String, String> labels = ImmutableMap.<String, String>builder().put("scope", scope);
      if (SCOPE_ACCOUNT.equals(scope) && accountId != null) {
        labels.put("accountId", accountId);
      }
      try (PmsMetricContextGuard guard = new PmsMetricContextGuard(labels.build())) {
        metricService.recordMetric(METRIC_COUNTER_DRIFT, drift);
      }
    } catch (Exception ex) {
      log.debug("[PLAN_CONCURRENCY_REBUILD] drift metric emission failed scope={} account={}", scope, accountId, ex);
    }
  }

  private void emitGauge(String metricName, long value) {
    try {
      metricService.recordMetric(metricName, value);
    } catch (Exception ex) {
      log.debug("[PLAN_CONCURRENCY_REBUILD] gauge metric emission failed metric={}", metricName, ex);
    }
  }

  private void emitRebuildRunMetric(String outcome) {
    try {
      try (PmsMetricContextGuard guard = new PmsMetricContextGuard(ImmutableMap.of("outcome", outcome))) {
        metricService.incCounter(METRIC_REBUILD_RUN_TOTAL);
      }
    } catch (Exception ex) {
      log.debug("[PLAN_CONCURRENCY_REBUILD] rebuild run metric emission failed outcome={}", outcome, ex);
    }
  }
}
