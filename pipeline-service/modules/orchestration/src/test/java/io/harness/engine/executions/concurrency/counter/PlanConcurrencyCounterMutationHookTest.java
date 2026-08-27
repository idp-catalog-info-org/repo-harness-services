/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PlanConcurrencyCounterMutationHookTest extends CategoryTest {
  @Mock private PlanConcurrencyCounterService counterService;
  @Mock private MetricService metricService;

  private PlanConcurrencyCounterMutationHook hook;

  private static final String ACCOUNT_ID = "acc123";
  private static final String PARENT_UNIQUE_ID = "proj-uuid-456";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    hook = new PlanConcurrencyCounterMutationHook(counterService, true, metricService);
  }

  private Map<String, String> setupAbstractions() {
    return Map.of(SetupAbstractionKeys.accountId, ACCOUNT_ID, SetupAbstractionKeys.parentUniqueId, PARENT_UNIQUE_ID);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_MutationDisabled_DoesNothing() {
    hook = new PlanConcurrencyCounterMutationHook(counterService, false, metricService);

    hook.onStatusChange(setupAbstractions(), Status.QUEUED, Status.RUNNING);

    verify(counterService, never()).incrementAccount(eq(ACCOUNT_ID), anyLong());
    verify(counterService, never()).incrementProject(eq(ACCOUNT_ID), eq(PARENT_UNIQUE_ID), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_QueuedToRunning_Increments() {
    hook.onStatusChange(setupAbstractions(), Status.QUEUED, Status.RUNNING);

    verify(counterService).incrementAccount(ACCOUNT_ID, 1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, 1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_RunningToSuccess_Decrements() {
    hook.onStatusChange(setupAbstractions(), Status.RUNNING, Status.SUCCEEDED);

    verify(counterService).incrementAccount(ACCOUNT_ID, -1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, -1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_RunningToFailed_Decrements() {
    hook.onStatusChange(setupAbstractions(), Status.RUNNING, Status.FAILED);

    verify(counterService).incrementAccount(ACCOUNT_ID, -1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, -1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_RunningToAborted_Decrements() {
    hook.onStatusChange(setupAbstractions(), Status.RUNNING, Status.ABORTED);

    verify(counterService).incrementAccount(ACCOUNT_ID, -1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, -1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_RunningToExpired_Decrements() {
    hook.onStatusChange(setupAbstractions(), Status.RUNNING, Status.EXPIRED);

    verify(counterService).incrementAccount(ACCOUNT_ID, -1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, -1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_QueuedToFailed_NoChange() {
    hook.onStatusChange(setupAbstractions(), Status.QUEUED, Status.FAILED);

    verify(counterService, never()).incrementAccount(eq(ACCOUNT_ID), anyLong());
    verify(counterService, never()).incrementProject(eq(ACCOUNT_ID), eq(PARENT_UNIQUE_ID), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_RunningToRunning_NoChange() {
    hook.onStatusChange(setupAbstractions(), Status.RUNNING, Status.RUNNING);

    verify(counterService, never()).incrementAccount(eq(ACCOUNT_ID), anyLong());
    verify(counterService, never()).incrementProject(eq(ACCOUNT_ID), eq(PARENT_UNIQUE_ID), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_SuccessToSuccess_NoChange() {
    hook.onStatusChange(setupAbstractions(), Status.SUCCEEDED, Status.SUCCEEDED);

    verify(counterService, never()).incrementAccount(eq(ACCOUNT_ID), anyLong());
    verify(counterService, never()).incrementProject(eq(ACCOUNT_ID), eq(PARENT_UNIQUE_ID), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_NullParentUniqueId_OnlyUpdatesAccount() {
    Map<String, String> setupAbstractionsNoProject = Map.of(SetupAbstractionKeys.accountId, ACCOUNT_ID);
    hook.onStatusChange(setupAbstractionsNoProject, Status.QUEUED, Status.RUNNING);

    verify(counterService).incrementAccount(ACCOUNT_ID, 1L);
    // Should not call incrementProject
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_TaskWaitingToRunning_NoChange() {
    // Both TASK_WAITING and RUNNING are in activeStatuses(), so delta is 0
    hook.onStatusChange(setupAbstractions(), Status.TASK_WAITING, Status.RUNNING);

    verify(counterService, never()).incrementAccount(eq(ACCOUNT_ID), anyLong());
    verify(counterService, never()).incrementProject(eq(ACCOUNT_ID), eq(PARENT_UNIQUE_ID), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_AsyncWaitingToSuccess_Decrements() {
    // ASYNC_WAITING is active, SUCCEEDED is not, so delta is -1
    hook.onStatusChange(setupAbstractions(), Status.ASYNC_WAITING, Status.SUCCEEDED);

    verify(counterService).incrementAccount(ACCOUNT_ID, -1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, -1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_RunningToApprovalWaiting_Decrements() {
    // APPROVAL_WAITING does not occupy a slot (excluded from both account and project counters),
    // so leaving RUNNING for APPROVAL_WAITING releases the slot.
    hook.onStatusChange(setupAbstractions(), Status.RUNNING, Status.APPROVAL_WAITING);

    verify(counterService).incrementAccount(ACCOUNT_ID, -1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, -1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_ApprovalWaitingToRunning_Increments() {
    // Re-entering the slot set from APPROVAL_WAITING re-acquires the slot on both legs.
    hook.onStatusChange(setupAbstractions(), Status.APPROVAL_WAITING, Status.RUNNING);

    verify(counterService).incrementAccount(ACCOUNT_ID, 1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, 1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_QueuedToApprovalWaiting_NoChange() {
    // Neither QUEUED nor APPROVAL_WAITING occupies a slot, so there is no delta.
    hook.onStatusChange(setupAbstractions(), Status.QUEUED, Status.APPROVAL_WAITING);

    verify(counterService, never()).incrementAccount(eq(ACCOUNT_ID), anyLong());
    verify(counterService, never()).incrementProject(eq(ACCOUNT_ID), eq(PARENT_UNIQUE_ID), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testOnStatusChange_ApprovalWaitingToSuccess_NoChange() {
    // APPROVAL_WAITING never held a slot, so completing from it does not decrement.
    hook.onStatusChange(setupAbstractions(), Status.APPROVAL_WAITING, Status.SUCCEEDED);

    verify(counterService, never()).incrementAccount(eq(ACCOUNT_ID), anyLong());
    verify(counterService, never()).incrementProject(eq(ACCOUNT_ID), eq(PARENT_UNIQUE_ID), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunWithAdmissionIncrementSuppressed_SkipsTheReservedPlusOne() {
    // A reserve already applied the +1; the admission flip must NOT increment again.
    PlanConcurrencyCounterMutationHook.runWithAdmissionIncrementSuppressed(() -> {
      hook.onStatusChange(setupAbstractions(), Status.QUEUED_PLAN_CREATION, Status.STARTING_PLAN_CREATION);
      return null;
    });

    verify(counterService, never()).incrementAccount(eq(ACCOUNT_ID), anyLong());
    verify(counterService, never()).incrementProject(eq(ACCOUNT_ID), eq(PARENT_UNIQUE_ID), anyLong());
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunWithAdmissionIncrementSuppressed_IsConsumedExactlyOnce() {
    // Flag is one-shot: the FIRST increment inside the block is suppressed, a SECOND is not.
    PlanConcurrencyCounterMutationHook.runWithAdmissionIncrementSuppressed(() -> {
      hook.onStatusChange(setupAbstractions(), Status.QUEUED_PLAN_CREATION, Status.STARTING_PLAN_CREATION);
      // Second increment in the same thread scope is no longer suppressed.
      hook.onStatusChange(setupAbstractions(), Status.APPROVAL_WAITING, Status.RUNNING);
      return null;
    });

    verify(counterService).incrementAccount(ACCOUNT_ID, 1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, 1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunWithAdmissionIncrementSuppressed_NeverSuppressesDecrement() {
    // Only the +1 is owned by the reserve; a -1 must always land to release the slot.
    PlanConcurrencyCounterMutationHook.runWithAdmissionIncrementSuppressed(() -> {
      hook.onStatusChange(setupAbstractions(), Status.RUNNING, Status.SUCCEEDED);
      return null;
    });

    verify(counterService).incrementAccount(ACCOUNT_ID, -1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, -1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunWithAdmissionIncrementSuppressed_FlagClearedAfterBlock() {
    // Outside the suppression block a normal increment is applied (flag did not leak).
    PlanConcurrencyCounterMutationHook.runWithAdmissionIncrementSuppressed(() -> null);

    hook.onStatusChange(setupAbstractions(), Status.QUEUED, Status.RUNNING);

    verify(counterService).incrementAccount(ACCOUNT_ID, 1L);
    verify(counterService).incrementProject(ACCOUNT_ID, PARENT_UNIQUE_ID, 1L);
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRunWithAdmissionIncrementSuppressed_ReturnsActionResult() {
    String result = PlanConcurrencyCounterMutationHook.runWithAdmissionIncrementSuppressed(() -> "flipped");

    assertThat(result).isEqualTo("flipped");
  }
}
