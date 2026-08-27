/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import static io.harness.rule.OwnerRule.MANAS_ASATI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;

public class StepConcurrencyCounterMutationHookTest extends CategoryTest {
  private StepConcurrencyCounterService counterService;
  private MetricService metricService;
  private StepConcurrencyCounterMutationHook hook;

  @Before
  public void setUp() {
    counterService = mock(StepConcurrencyCounterService.class);
    metricService = mock(MetricService.class);
    hook = new StepConcurrencyCounterMutationHook(counterService, true, metricService);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void nonLeafModeIsNoOp() {
    hook.onStatusChange("acc", ExecutionMode.CHILD, Status.QUEUED, Status.RUNNING);
    verify(counterService, never()).incrementAccount(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong());
    verify(counterService, never()).incrementCluster(ArgumentMatchers.anyLong());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void mutationDisabledIsNoOp() {
    StepConcurrencyCounterMutationHook disabled =
        new StepConcurrencyCounterMutationHook(counterService, false, metricService);
    disabled.onStatusChange("acc", ExecutionMode.TASK, Status.QUEUED, Status.RUNNING);
    verify(counterService, never()).incrementAccount(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void entryIntoSlotSetIncrementsByOne() {
    // QUEUED (out) -> RUNNING (in slot set) = +1
    when(counterService.incrementAccount("acc", 1)).thenReturn(1L);
    when(counterService.incrementCluster(1)).thenReturn(1L);
    hook.onStatusChange("acc", ExecutionMode.TASK, Status.QUEUED, Status.RUNNING);
    verify(counterService).incrementAccount("acc", 1);
    verify(counterService).incrementCluster(1);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void exitFromSlotSetDecrementsByOne() {
    // RUNNING (in) -> SUCCEEDED (terminal, out of slot set) = -1
    when(counterService.incrementAccount("acc", -1)).thenReturn(0L);
    when(counterService.incrementCluster(-1)).thenReturn(0L);
    hook.onStatusChange("acc", ExecutionMode.TASK, Status.RUNNING, Status.SUCCEEDED);
    verify(counterService).incrementAccount("acc", -1);
    verify(counterService).incrementCluster(-1);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void bothInSlotSetIsNoOp() {
    // RUNNING -> INTERVENTION_WAITING (both in slot set) = 0 delta
    hook.onStatusChange("acc", ExecutionMode.TASK, Status.RUNNING, Status.INTERVENTION_WAITING);
    verify(counterService, never()).incrementAccount(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void approvalWaitingIsNotInSlotSet() {
    // RUNNING (in) -> APPROVAL_WAITING (out) = -1; an approval waiting on a human must not hold a slot.
    when(counterService.incrementAccount("acc", -1)).thenReturn(0L);
    when(counterService.incrementCluster(-1)).thenReturn(0L);
    hook.onStatusChange("acc", ExecutionMode.TASK, Status.RUNNING, Status.APPROVAL_WAITING);
    verify(counterService).incrementAccount("acc", -1);
    verify(counterService).incrementCluster(-1);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void approvalWaitingToApprovalWaitingIsNoOp() {
    // Both outside the slot set = no-op.
    hook.onStatusChange("acc", ExecutionMode.TASK, Status.APPROVAL_WAITING, Status.APPROVAL_WAITING);
    verify(counterService, never()).incrementAccount(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void queuedStepLimitReachedIsNotInSlotSet() {
    // RUNNING (in) -> QUEUED_STEP_LIMIT_REACHED (out) is unusual but must be -1
    when(counterService.incrementAccount("acc", -1)).thenReturn(0L);
    when(counterService.incrementCluster(-1)).thenReturn(0L);
    hook.onStatusChange("acc", ExecutionMode.TASK, Status.RUNNING, Status.QUEUED_STEP_LIMIT_REACHED);
    verify(counterService).incrementAccount("acc", -1);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void nullOldStatusTreatedAsOutside() {
    // null (out) -> RUNNING (in) = +1
    when(counterService.incrementAccount("acc", 1)).thenReturn(1L);
    when(counterService.incrementCluster(1)).thenReturn(1L);
    hook.onStatusChange("acc", ExecutionMode.TASK, null, Status.RUNNING);
    verify(counterService).incrementAccount("acc", 1);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void nullAccountIdIsNoOp() {
    hook.onStatusChange(null, ExecutionMode.TASK, Status.QUEUED, Status.RUNNING);
    verify(counterService, never()).incrementAccount(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void counterFailureIsSwallowedAndDoesNotThrow() {
    when(counterService.incrementAccount(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong()))
        .thenThrow(new RuntimeException("redis down"));
    when(counterService.incrementCluster(ArgumentMatchers.anyLong())).thenThrow(new RuntimeException("redis down"));
    // Should not throw — orchestration progress must not be blocked by counter failure.
    hook.onStatusChange("acc", ExecutionMode.TASK, Status.QUEUED, Status.RUNNING);
    // No application-layer retry: each leg is called exactly once. Redisson runs its own retry
    // loop in the background; the service layer bounds the wait.
    verify(counterService, org.mockito.Mockito.times(1)).incrementAccount("acc", 1);
    verify(counterService, org.mockito.Mockito.times(1)).incrementCluster(1);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void partialFailureCallsEachLegExactlyOnce() {
    // Account write succeeds; cluster write throws. Each leg is best-effort and independent.
    when(counterService.incrementAccount("acc", 1)).thenReturn(1L);
    when(counterService.incrementCluster(1L)).thenThrow(new RuntimeException("redis down"));
    hook.onStatusChange("acc", ExecutionMode.TASK, Status.QUEUED, Status.RUNNING);
    verify(counterService, org.mockito.Mockito.times(1)).incrementAccount("acc", 1);
    verify(counterService, org.mockito.Mockito.times(1)).incrementCluster(1L);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void onBulkExitAppliesNegativeDelta() {
    when(counterService.incrementAccount("acc", -5)).thenReturn(0L);
    when(counterService.incrementCluster(-5)).thenReturn(0L);
    hook.onBulkExit("acc", 5);
    verify(counterService).incrementAccount("acc", -5);
    verify(counterService).incrementCluster(-5);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void onBulkEntryAppliesPositiveDelta() {
    when(counterService.incrementAccount("acc", 3)).thenReturn(3L);
    when(counterService.incrementCluster(3)).thenReturn(3L);
    hook.onBulkEntry("acc", 3);
    verify(counterService).incrementAccount("acc", 3);
    verify(counterService).incrementCluster(3);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void bulkOpsWithZeroOrNegativeAreNoOps() {
    hook.onBulkExit("acc", 0);
    hook.onBulkEntry("acc", 0);
    hook.onBulkExit("acc", -5);
    hook.onBulkEntry("acc", -5);
    verify(counterService, never()).incrementAccount(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong());
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void successfulMutationEmitsMutationsMetricForBothLegs() {
    when(counterService.incrementAccount("acc", 1)).thenReturn(1L);
    when(counterService.incrementCluster(1)).thenReturn(1L);
    hook.onStatusChange("acc", ExecutionMode.TASK, Status.QUEUED, Status.RUNNING);
    // One emit for the account leg, one for the cluster leg; both outcome=success.
    verify(metricService, org.mockito.Mockito.times(2))
        .incCounter(StepConcurrencyCounterMutationHook.METRIC_COUNTER_MUTATIONS);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void counterFailureEmitsMutationMetricWithErrorOutcomeForBothLegs() {
    when(counterService.incrementAccount(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong()))
        .thenThrow(new RuntimeException("redis down"));
    when(counterService.incrementCluster(ArgumentMatchers.anyLong())).thenThrow(new RuntimeException("redis down"));
    hook.onStatusChange("acc", ExecutionMode.TASK, Status.QUEUED, Status.RUNNING);
    // Same metric as the success path; outcome label (carried via MDC, not asserted here)
    // distinguishes success from error.
    verify(metricService, org.mockito.Mockito.times(2))
        .incCounter(StepConcurrencyCounterMutationHook.METRIC_COUNTER_MUTATIONS);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void onBulkExitEmitsMutationsMetric() {
    when(counterService.incrementAccount("acc", -5)).thenReturn(0L);
    when(counterService.incrementCluster(-5)).thenReturn(0L);
    hook.onBulkExit("acc", 5);
    verify(metricService, org.mockito.Mockito.times(2))
        .incCounter(StepConcurrencyCounterMutationHook.METRIC_COUNTER_MUTATIONS);
  }
}
