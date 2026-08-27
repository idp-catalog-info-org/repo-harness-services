/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.engine.pms.execution.strategy.plan;

import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.concurrency.PlanConcurrencyGate;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionConcurrencyMode;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.tasks.ResponseData;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.OldNotifyCallback;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Data
@Builder
@Slf4j
public class PlanExecutionResumeCallback implements OldNotifyCallback {
  private static final String PLAN_EXECUTION_START_CALLBACK_PREFIX = "PLAN_EXECUTION_START_CALLBACK%s";
  private static final int DRAIN_BATCH_SIZE = 100;

  public static final String METRIC_DEQUEUE_TOTAL = "pipeline_plan_concurrency_dequeue_total";
  public static final String METRIC_DEQUEUE_WALK_LENGTH = "pipeline_plan_concurrency_dequeue_walk_length";
  public static final String METRIC_DEQUEUE_SKIPS_TOTAL = "pipeline_plan_concurrency_dequeue_skips_total";

  private static final String OUTCOME_CLAIMED = "claimed";
  private static final String OUTCOME_WALK_EXHAUSTED = "walk_exhausted";
  private static final String SKIP_REASON_ALREADY_STARTED = "already_started_in_project";
  private static final String SKIP_REASON_CAP_REACHED = "cap_reached";

  String accountIdIdentifier;
  String projectIdentifier;
  String orgIdentifier;
  String pipelineIdentifier;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private PlanService planService;
  @Inject private PlanExecutionStrategy planExecutionStrategy;

  @Inject PersistentLocker persistentLocker;
  @Inject PipelineSettingsService pipelineSettingsService;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;
  @Inject PlanConcurrencyGate planConcurrencyGate;
  @Inject MetricService metricService;

  @Override
  public void notify(Map<String, ResponseData> response) {
    notifyError(response);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    String lockName = String.format(PLAN_EXECUTION_START_CALLBACK_PREFIX, accountIdIdentifier);
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLockOptional(lockName, Duration.ofSeconds(30), Duration.ofSeconds(30))) {
      if (lock == null) {
        log.error("[PLAN_EXECUTION_RESUME_CALLBACK]: Could not acquire lock for account: [{}], org: [{}], project: "
                + "[{}], pipeline: [{}]",
            accountIdIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
        return;
      }
      // PER_PROJECT mode: walk the FIFO queue with skip-ahead so a project at its cap does not block
      // executions of other projects that still have headroom. Otherwise keep the existing
      // account-wide strict-FIFO single-pick behaviour (no regression for partitions / account-only).
      if (isPerProjectMode()) {
        drainWithSkipAhead();
        return;
      }
      PlanExecution planExecution = planExecutionService.findNextExecutionToRunInAccount(accountIdIdentifier);
      if (planExecution != null) {
        if (!canContinueToNextExecution()) {
          return;
        }
        startExecution(planExecution);
      }
    }
  }

  private boolean isPerProjectMode() {
    try {
      return pmsFeatureFlagService.isEnabled(
                 accountIdIdentifier, FeatureName.PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES.name())
          && pipelineSettingsService.getConcurrencyMode(accountIdIdentifier)
          == PlanExecutionConcurrencyMode.PER_PROJECT;
    } catch (Exception ex) {
      log.warn("[PLAN_EXECUTION_RESUME_CALLBACK]: failed to resolve concurrency mode for account {}; "
              + "falling back to account-wide drain",
          accountIdIdentifier, ex);
      return false;
    }
  }

  // FIFO walk with skip-ahead: start the oldest queued execution whose project (and the account)
  // has headroom, skipping projects that are at their cap. A single completion can release
  // executions for multiple different projects while the account still has room (cross-project
  // fairness). We deliberately start at most ONE execution per project scope per cycle: the counter
  // increment on the RUNNING transition is post-commit/best-effort, so starting several for the same
  // project in one tight loop could read a lagging counter and over-shoot its cap. The next
  // completion drains that project's next queued execution — throughput is preserved, over-start is
  // not possible.
  private void drainWithSkipAhead() {
    List<PlanExecution> candidates =
        planExecutionService.findNextExecutionsToRunInAccount(accountIdIdentifier, DRAIN_BATCH_SIZE);
    Set<String> startedProjectScopes = new HashSet<>();
    int inspected = 0;
    int startedCount = 0;
    for (PlanExecution candidate : candidates) {
      inspected++;
      Map<String, String> setupAbstractions = candidate.getSetupAbstractions();
      String parentUniqueId =
          setupAbstractions == null ? null : setupAbstractions.get(SetupAbstractionKeys.parentUniqueId);
      // Dedup by the stable project id (parentUniqueId) so a project is throttled to one start per
      // cycle; executions with no resolvable parentUniqueId fall back to the plan id (never deduped).
      String scope = parentUniqueId != null ? accountIdIdentifier + "/" + parentUniqueId : candidate.getUuid();
      if (startedProjectScopes.contains(scope)) {
        // Already started one for this project this cycle; leave the rest for the next completion.
        emitSkipMetric(SKIP_REASON_ALREADY_STARTED);
        continue;
      }
      if (planConcurrencyGate.hasHeadroomFor(accountIdIdentifier, parentUniqueId)) {
        startExecution(candidate);
        startedProjectScopes.add(scope);
        startedCount++;
      } else {
        // Skip this candidate (its project / account is at cap) and try the next one.
        emitSkipMetric(SKIP_REASON_CAP_REACHED);
      }
    }
    emitWalkLengthMetric(inspected);
    emitDequeueOutcomeMetric(startedCount > 0 ? OUTCOME_CLAIMED : OUTCOME_WALK_EXHAUSTED);
  }

  private void emitDequeueOutcomeMetric(String outcome) {
    try (PmsMetricContextGuard guard = new PmsMetricContextGuard(ImmutableMap.of("outcome", outcome))) {
      metricService.incCounter(METRIC_DEQUEUE_TOTAL);
    } catch (Exception ex) {
      log.debug("[PLAN_CONCURRENCY] dequeue outcome metric emission failed outcome={}", outcome, ex);
    }
  }

  private void emitSkipMetric(String skipReason) {
    try (PmsMetricContextGuard guard = new PmsMetricContextGuard(ImmutableMap.of("skip_reason", skipReason))) {
      metricService.incCounter(METRIC_DEQUEUE_SKIPS_TOTAL);
    } catch (Exception ex) {
      log.debug("[PLAN_CONCURRENCY] dequeue skip metric emission failed skip_reason={}", skipReason, ex);
    }
  }

  private void emitWalkLengthMetric(int walkLength) {
    try {
      metricService.recordMetric(METRIC_DEQUEUE_WALK_LENGTH, walkLength);
    } catch (Exception ex) {
      log.debug("[PLAN_CONCURRENCY] dequeue walk length metric emission failed", ex);
    }
  }

  private void startExecution(PlanExecution planExecution) {
    planExecutionService.updateStatus(planExecution.getUuid(), Status.RUNNING);
    planExecutionStrategy.startPlanExecution(
        planService.fetchPlan(planExecution.getPlanId()), planExecution.getAmbiance());
  }

  private boolean canContinueToNextExecution() {
    long maxConcurrency = pipelineSettingsService.getMaxConcurrency(accountIdIdentifier);
    long currentExecutionCount = pipelineSettingsService.getCurrentExecutionCount(accountIdIdentifier);
    return currentExecutionCount < maxConcurrency;
  }
}
