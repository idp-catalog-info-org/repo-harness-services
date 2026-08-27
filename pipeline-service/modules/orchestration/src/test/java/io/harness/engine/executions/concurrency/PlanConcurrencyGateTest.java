/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.concurrency.PlanConcurrencyGate.ThrottleDecision;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterService;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PlanConcurrencyGateTest extends CategoryTest {
  @Mock private PlanConcurrencyCounterService counterService;
  @Mock private PipelineSettingsService pipelineSettingsService;
  @Mock private MetricService metricService;

  private PlanConcurrencyGate gate;

  private static final String ACCOUNT_ID = "acc123";
  private static final String PARENT_UNIQUE_ID = "proj-uuid-456";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueue_DisabledMode_AlwaysReturnsAllow() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "disabled", true);

    ThrottleDecision result = gate.shouldQueue(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(result.isQueue()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueue_EnforceMode_ProjectAtCap() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5L);

    ThrottleDecision result = gate.shouldQueue(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(result.isQueue()).isTrue();
    assertThat(result.getReason()).isEqualTo(PlanConcurrencyGate.REASON_PROJECT);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueue_EnforceMode_ProjectHasHeadroom_AccountAtCap() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(3L);
    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(100L);
    when(counterService.getAccountCount(ACCOUNT_ID)).thenReturn(100L);

    ThrottleDecision result = gate.shouldQueue(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(result.isQueue()).isTrue();
    assertThat(result.getReason()).isEqualTo(PlanConcurrencyGate.REASON_ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueue_EnforceMode_BothHaveHeadroom() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(3L);
    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(100L);
    when(counterService.getAccountCount(ACCOUNT_ID)).thenReturn(80L);

    ThrottleDecision result = gate.shouldQueue(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(result.isQueue()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueue_ShadowMode_LogsButReturnsAllow() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "shadow", true);

    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(10L);

    ThrottleDecision result = gate.shouldQueue(ACCOUNT_ID, PARENT_UNIQUE_ID);

    // Shadow mode always returns allow (doesn't actually queue)
    assertThat(result.isQueue()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueue_NullParentUniqueId_FallsBackToAccountOnly() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(100L);
    when(counterService.getAccountCount(ACCOUNT_ID)).thenReturn(100L);

    ThrottleDecision result = gate.shouldQueue(ACCOUNT_ID, null);

    assertThat(result.isQueue()).isTrue();
    assertThat(result.getReason()).isEqualTo(PlanConcurrencyGate.REASON_ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldQueue_FailsOpen_OnException() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    when(pipelineSettingsService.getEffectiveProjectConcurrency(anyString(), anyString()))
        .thenThrow(new RuntimeException("Redis down"));

    ThrottleDecision result = gate.shouldQueue(ACCOUNT_ID, PARENT_UNIQUE_ID);

    // Should fail open (allow execution)
    assertThat(result.isQueue()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHasHeadroomFor_DisabledMode_AlwaysReturnsTrue() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "disabled", true);

    boolean result = gate.hasHeadroomFor(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHasHeadroomFor_EnforceMode_HasHeadroom() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(3L);
    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(100L);
    when(counterService.getAccountCount(ACCOUNT_ID)).thenReturn(80L);

    boolean result = gate.hasHeadroomFor(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHasHeadroomFor_EnforceMode_ProjectAtCap() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5L);

    boolean result = gate.hasHeadroomFor(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHasHeadroomFor_EnforceMode_AccountAtCap() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(3L);
    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(100L);
    when(counterService.getAccountCount(ACCOUNT_ID)).thenReturn(100L);

    boolean result = gate.hasHeadroomFor(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHasHeadroomFor_ShadowMode_AlwaysReturnsTrue() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "shadow", true);

    // Even when at cap, shadow mode should return true (no enforcement)
    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5L);

    boolean result = gate.hasHeadroomFor(ACCOUNT_ID, PARENT_UNIQUE_ID);

    // Shadow mode: always allows (logs but doesn't enforce)
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHasHeadroomFor_EnforceMode_FailsClosed_OnException() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    when(pipelineSettingsService.getEffectiveProjectConcurrency(anyString(), anyString()))
        .thenThrow(new RuntimeException("Redis down"));

    boolean result = gate.hasHeadroomFor(ACCOUNT_ID, PARENT_UNIQUE_ID);

    // Enforce mode: should fail closed (return false to prevent breaching cap)
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testHasHeadroomFor_ShadowMode_FailsOpen_OnException() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "shadow", true);

    when(pipelineSettingsService.getEffectiveProjectConcurrency(anyString(), anyString()))
        .thenThrow(new RuntimeException("Redis down"));

    boolean result = gate.hasHeadroomFor(ACCOUNT_ID, PARENT_UNIQUE_ID);

    // Shadow mode: should fail open (return true, no enforcement)
    assertThat(result).isTrue();
  }

  // ---- evaluateHeadroom (reason-returning variant, used by the Postgres drainer's per-walk cache) ----

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testEvaluateHeadroom_EnforceMode_ProjectAtCap_ReturnsProjectFull() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);
    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5L);

    assertThat(gate.evaluateHeadroom(ACCOUNT_ID, PARENT_UNIQUE_ID))
        .isEqualTo(PlanConcurrencyGate.HeadroomDecision.PROJECT_FULL);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testEvaluateHeadroom_EnforceMode_ProjectHasRoom_AccountAtCap_ReturnsAccountFull() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);
    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(3L);
    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(100L);
    when(counterService.getAccountCount(ACCOUNT_ID)).thenReturn(100L);

    assertThat(gate.evaluateHeadroom(ACCOUNT_ID, PARENT_UNIQUE_ID))
        .isEqualTo(PlanConcurrencyGate.HeadroomDecision.ACCOUNT_FULL);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testEvaluateHeadroom_EnforceMode_BothHaveRoom_ReturnsHasHeadroom() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);
    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(3L);
    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(100L);
    when(counterService.getAccountCount(ACCOUNT_ID)).thenReturn(80L);

    assertThat(gate.evaluateHeadroom(ACCOUNT_ID, PARENT_UNIQUE_ID))
        .isEqualTo(PlanConcurrencyGate.HeadroomDecision.HAS_HEADROOM);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testEvaluateHeadroom_EnforceMode_FailsClosed_ReturnsIndeterminate() {
    // Fail-closed on a Redis blip: requeue but do NOT cache the scope (INDETERMINATE, not *_FULL).
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);
    when(pipelineSettingsService.getEffectiveProjectConcurrency(anyString(), anyString()))
        .thenThrow(new RuntimeException("Redis down"));

    assertThat(gate.evaluateHeadroom(ACCOUNT_ID, PARENT_UNIQUE_ID))
        .isEqualTo(PlanConcurrencyGate.HeadroomDecision.INDETERMINATE);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testEvaluateHeadroom_DisabledMode_AlwaysHasHeadroom() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "disabled", true);

    assertThat(gate.evaluateHeadroom(ACCOUNT_ID, PARENT_UNIQUE_ID))
        .isEqualTo(PlanConcurrencyGate.HeadroomDecision.HAS_HEADROOM);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testEvaluateHeadroom_ShadowMode_AlwaysHasHeadroom() {
    // Shadow computes the real decision for logging but must not enforce -> always HAS_HEADROOM.
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "shadow", true);
    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5L);

    assertThat(gate.evaluateHeadroom(ACCOUNT_ID, PARENT_UNIQUE_ID))
        .isEqualTo(PlanConcurrencyGate.HeadroomDecision.HAS_HEADROOM);
  }

  // ---- tryReserveSlot (atomic admission, PIPE-35674) ----

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_DisabledMode_NotReserved_NoRedisCall() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "disabled", true);

    PlanConcurrencyGate.ReserveOutcome outcome = gate.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(outcome).isEqualTo(PlanConcurrencyGate.ReserveOutcome.NOT_RESERVED);
    verify(counterService, never()).tryReserveSlot(anyString(), anyString(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_ShadowMode_NotReserved_NoActualReserve() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "shadow", true);
    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(counterService.getProjectCount(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5L);

    PlanConcurrencyGate.ReserveOutcome outcome = gate.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID);

    // Shadow never actually reserves — the hook still applies the +1 on the status flip.
    assertThat(outcome).isEqualTo(PlanConcurrencyGate.ReserveOutcome.NOT_RESERVED);
    verify(counterService, never()).tryReserveSlot(anyString(), anyString(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_EnforceMode_Reserved() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);
    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(100L);
    when(counterService.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID, 5L, 100L)).thenReturn(true);

    PlanConcurrencyGate.ReserveOutcome outcome = gate.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(outcome).isEqualTo(PlanConcurrencyGate.ReserveOutcome.RESERVED);
    verify(counterService).tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID, 5L, 100L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_EnforceMode_Denied() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);
    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(5);
    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(100L);
    when(counterService.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID, 5L, 100L)).thenReturn(false);

    PlanConcurrencyGate.ReserveOutcome outcome = gate.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(outcome).isEqualTo(PlanConcurrencyGate.ReserveOutcome.DENIED);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_EnforceMode_NoProjectCap_PassesMinusOne() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);
    // Non-positive project cap => "no project cap" (-1); unlimited account total => -1.
    when(pipelineSettingsService.getEffectiveProjectConcurrency(ACCOUNT_ID, PARENT_UNIQUE_ID)).thenReturn(0);
    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(Long.MAX_VALUE);
    when(counterService.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID, -1L, -1L)).thenReturn(true);

    PlanConcurrencyGate.ReserveOutcome outcome = gate.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(outcome).isEqualTo(PlanConcurrencyGate.ReserveOutcome.RESERVED);
    verify(counterService).tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID, -1L, -1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_EnforceMode_MutationDisabled_DegradesToNotReserved() {
    // ENFORCE + mutation off would leak reserves (no -1 ever fires); the gate must not reserve.
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", false);

    PlanConcurrencyGate.ReserveOutcome outcome = gate.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID);

    assertThat(outcome).isEqualTo(PlanConcurrencyGate.ReserveOutcome.NOT_RESERVED);
    verify(counterService, never()).tryReserveSlot(anyString(), anyString(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTryReserveSlot_EnforceMode_FailsClosed_OnException() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);
    when(pipelineSettingsService.getEffectiveProjectConcurrency(anyString(), anyString()))
        .thenThrow(new RuntimeException("Redis down"));

    PlanConcurrencyGate.ReserveOutcome outcome = gate.tryReserveSlot(ACCOUNT_ID, PARENT_UNIQUE_ID);

    // Fail-closed: a blip requeues rather than admitting past the cap.
    assertThat(outcome).isEqualTo(PlanConcurrencyGate.ReserveOutcome.DENIED);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testReleaseReservedSlot_DecrementsBothLegs() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    gate.releaseReservedSlot(ACCOUNT_ID, PARENT_UNIQUE_ID);

    verify(counterService).incrementAccount(ACCOUNT_ID, -1);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, -1);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testReleaseReservedSlot_AccountOnlyWhenNoParentUniqueId() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);

    gate.releaseReservedSlot(ACCOUNT_ID, null);

    verify(counterService).incrementAccount(ACCOUNT_ID, -1);
    verify(counterService, never()).incrementProject(anyString(), anyString(), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testReleaseReservedSlot_ProjectLegFailure_StillReleasesAccountLeg() {
    gate = new PlanConcurrencyGate(counterService, pipelineSettingsService, metricService, "enforce", true);
    // Project leg throws (e.g. Redis timeout); the account leg must still be released so no phantom
    // occupant is stranded on either counter.
    when(counterService.incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, -1))
        .thenThrow(new RuntimeException("Redis down"));

    gate.releaseReservedSlot(ACCOUNT_ID, PARENT_UNIQUE_ID);

    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, -1);
    verify(counterService).incrementAccount(ACCOUNT_ID, -1);
  }
}
