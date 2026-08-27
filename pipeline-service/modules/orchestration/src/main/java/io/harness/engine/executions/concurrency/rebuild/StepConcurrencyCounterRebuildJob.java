/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.rebuild;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterService;
import io.harness.engine.executions.node.helper.NodeExecutionReadHelper;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.service.api.MetricService;
import io.harness.monitoring.ExecutionCountWithAccountResult;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Leader-elected daily drift recompute for the step-concurrency Redis counters. Reads the true
 * per-account leaf-step count occupying a slot from Mongo and overwrites the Redis counters via
 * {@code setAccountCount}/{@code setClusterCount} — an absolute overwrite, not a delta, since this
 * is the reconciliation path that corrects whatever drift the mutation hook accumulated.
 *
 * <p>Also invoked directly (bypassing the leader lock) by the admin recompute endpoint.
 */
@OwnedBy(PIPELINE)
@Slf4j
public class StepConcurrencyCounterRebuildJob implements Runnable {
  private static final String LOCK_NAME = "StepConcurrencyCounterRebuildJob";
  private static final Duration LOCK_TTL = Duration.ofMinutes(30);
  private static final Duration LOCK_WAIT_TIME = Duration.ofSeconds(5);

  public static final String METRIC_COUNTER_DRIFT = "pipeline_step_concurrency_counter_drift";
  public static final String METRIC_REBUILD_RUN_TOTAL = "pipeline_step_concurrency_rebuild_run_total";
  public static final String METRIC_REBUILD_ACCOUNTS_RECOMPUTED =
      "pipeline_step_concurrency_rebuild_accounts_recomputed";
  public static final String METRIC_REBUILD_STALE_ACCOUNTS_ZEROED =
      "pipeline_step_concurrency_rebuild_stale_accounts_zeroed";

  private static final String SCOPE_ACCOUNT = "account";
  private static final String SCOPE_CLUSTER = "cluster";
  private static final String OUTCOME_SUCCESS = "success";
  private static final String OUTCOME_LEADER_LOCK_HELD_BY_OTHER = "leader_lock_held_by_other";
  private static final String OUTCOME_ERROR = "error";

  @Inject private PersistentLocker persistentLocker;
  @Inject private NodeExecutionReadHelper nodeExecutionReadHelper;
  @Inject private StepConcurrencyCounterService counterService;
  @Inject private MetricService metricService;

  @Override
  public void run() {
    try (AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(LOCK_NAME, LOCK_TTL, LOCK_WAIT_TIME)) {
      if (lock == null) {
        log.info("[STEP_CONCURRENCY_REBUILD] another pod holds the lock; skipping this run");
        emitRebuildRunMetric(OUTCOME_LEADER_LOCK_HELD_BY_OTHER);
        return;
      }
      rebuild();
      emitRebuildRunMetric(OUTCOME_SUCCESS);
    } catch (Exception ex) {
      log.error("[STEP_CONCURRENCY_REBUILD] rebuild failed", ex);
      emitRebuildRunMetric(OUTCOME_ERROR);
    }
  }

  @VisibleForTesting
  public void rebuild() {
    Long clusterPreviousCount = safeGetClusterCount();
    Map<String, Long> staleAccountIds = new LinkedHashMap<>(counterService.getAllAccountCounts());
    List<ExecutionCountWithAccountResult> accountCounts = nodeExecutionReadHelper.aggregateLeafStepCountByAccount(
        StatusUtils.ACTIVE_STATUSES_OCCUPYING_STEP_CONCURRENCY_SLOT);
    long clusterTotal = 0;
    for (ExecutionCountWithAccountResult accountCount : accountCounts) {
      long count = accountCount.getCount() == null ? 0 : accountCount.getCount();
      counterService.setAccountCount(accountCount.getAccountId(), count);
      Long previousRemoved = staleAccountIds.remove(accountCount.getAccountId());
      long previous = previousRemoved == null ? 0L : previousRemoved;
      emitDrift(SCOPE_ACCOUNT, accountCount.getAccountId(), count - previous);
      clusterTotal += count;
    }
    // Accounts left in staleAccountIds have a non-zero Redis counter but no active leaf steps in
    // Mongo anymore — the mutation hook missed a decrement at some point. Zero them so drift doesn't
    // keep gating an account indefinitely.
    staleAccountIds.forEach((accountId, previous) -> {
      counterService.setAccountCount(accountId, 0L);
      emitDrift(SCOPE_ACCOUNT, accountId, -previous);
    });
    counterService.setClusterCount(clusterTotal);
    if (clusterPreviousCount != null) {
      emitDrift(SCOPE_CLUSTER, null, clusterTotal - clusterPreviousCount);
    }
    emitGauge(METRIC_REBUILD_ACCOUNTS_RECOMPUTED, accountCounts.size());
    emitGauge(METRIC_REBUILD_STALE_ACCOUNTS_ZEROED, staleAccountIds.size());
    log.info(
        "[STEP_CONCURRENCY_REBUILD] recomputed counters for {} accounts; zeroed {} stale accounts; cluster total={}",
        accountCounts.size(), staleAccountIds.size(), clusterTotal);
  }

  private Long safeGetClusterCount() {
    try {
      return counterService.getClusterCount();
    } catch (Exception ex) {
      log.warn(
          "[STEP_CONCURRENCY_REBUILD] failed to read cluster count before recompute; cluster drift metric skipped", ex);
      return null;
    }
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
      log.debug("[STEP_CONCURRENCY_REBUILD] drift metric emission failed scope={} account={}", scope, accountId, ex);
    }
  }

  private void emitGauge(String metricName, long value) {
    try {
      metricService.recordMetric(metricName, value);
    } catch (Exception ex) {
      log.debug("[STEP_CONCURRENCY_REBUILD] gauge metric emission failed metric={}", metricName, ex);
    }
  }

  private void emitRebuildRunMetric(String outcome) {
    try {
      try (PmsMetricContextGuard guard = new PmsMetricContextGuard(ImmutableMap.of("outcome", outcome))) {
        metricService.incCounter(METRIC_REBUILD_RUN_TOTAL);
      }
    } catch (Exception ex) {
      log.debug("[STEP_CONCURRENCY_REBUILD] rebuild run metric emission failed outcome={}", outcome, ex);
    }
  }
}
