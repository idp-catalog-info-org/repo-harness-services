/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.engine.pms.execution.strategy.plan;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.concurrency.PlanConcurrencyGate;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionConcurrencyMode;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.metrics.service.api.MetricService;
import io.harness.plan.Plan;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PlanExecutionResumeCallbackTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test_account";
  private static final String ORG_ID = "test_org";
  private static final String PROJECT_ID = "test_project";
  private static final String PIPELINE_ID = "test_pipeline";
  private static final String LOCK_NAME = "PLAN_EXECUTION_START_CALLBACK%s";

  @Mock private PlanExecutionService planExecutionService;
  @Mock private PlanService planService;
  @Mock private PlanExecutionStrategy planExecutionStrategy;
  @Mock private PersistentLocker persistentLocker;
  @Mock private PipelineSettingsService pipelineSettingsService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private PlanConcurrencyGate planConcurrencyGate;
  @Mock private MetricService metricService;
  @Mock private AcquiredLock<?> acquiredLock;
  @Mock private Plan plan;
  @Mock private Ambiance ambiance;

  @InjectMocks private PlanExecutionResumeCallback callback;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    callback = PlanExecutionResumeCallback.builder()
                   .accountIdIdentifier(ACCOUNT_ID)
                   .orgIdentifier(ORG_ID)
                   .projectIdentifier(PROJECT_ID)
                   .pipelineIdentifier(PIPELINE_ID)
                   .build();
    callback.setPlanExecutionService(planExecutionService);
    callback.setPlanService(planService);
    callback.setPlanExecutionStrategy(planExecutionStrategy);
    callback.setPersistentLocker(persistentLocker);
    callback.setPipelineSettingsService(pipelineSettingsService);
    callback.setPmsFeatureFlagService(pmsFeatureFlagService);
    callback.setPlanConcurrencyGate(planConcurrencyGate);
    callback.setMetricService(metricService);

    // Default: lock acquisition succeeds
    when(persistentLocker.waitToAcquireLockOptional(
             String.format(LOCK_NAME, ACCOUNT_ID), Duration.ofSeconds(30), Duration.ofSeconds(30)))
        .thenReturn(acquiredLock);
  }

  /**
   * Scenario: 6 candidates spanning 3 projects in FIFO order.
   * - Project A (parentUniqueId = "proj_a"): 2 executions, AT CAP
   * - Project B (parentUniqueId = "proj_b"): 2 executions, HAS HEADROOM
   * - Project C (null parentUniqueId): 2 executions, HAS HEADROOM
   *
   * Expected drain behavior:
   * - Skip both Project A candidates (at cap)
   * - Start FIRST Project B candidate (proj_b_exec1)
   * - Skip SECOND Project B candidate (dedup: already started one for proj_b this cycle)
   * - Start FIRST Project C candidate (proj_c_exec1, null parentUniqueId uses unique scope per exec)
   * - Start SECOND Project C candidate (proj_c_exec2, different scope since parentUniqueId is null)
   *
   * Cross-project fairness verified: Project A being at cap does NOT block Project B or C.
   * Per-project dedup verified: Only one execution per project (with non-null parentUniqueId) per cycle.
   * Null parentUniqueId handling: Each execution gets unique scope, so no dedup (legacy behavior preserved).
   */
  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDrainWithSkipAhead_CrossProjectFairnessAndDedup() {
    // Enable per-project mode
    enablePerProjectMode();

    // Create 6 candidates: Project A (at cap), Project B (headroom), Project C (null parentUniqueId, headroom)
    PlanExecution projA_exec1 = createPlanExecution("proj_a_exec1", "plan_a1", "proj_a");
    PlanExecution projA_exec2 = createPlanExecution("proj_a_exec2", "plan_a2", "proj_a");
    PlanExecution projB_exec1 = createPlanExecution("proj_b_exec1", "plan_b1", "proj_b");
    PlanExecution projB_exec2 = createPlanExecution("proj_b_exec2", "plan_b2", "proj_b");
    PlanExecution projC_exec1 = createPlanExecution("proj_c_exec1", "plan_c1", null); // null parentUniqueId
    PlanExecution projC_exec2 = createPlanExecution("proj_c_exec2", "plan_c2", null); // null parentUniqueId

    List<PlanExecution> candidates =
        Arrays.asList(projA_exec1, projA_exec2, projB_exec1, projB_exec2, projC_exec1, projC_exec2);

    when(planExecutionService.findNextExecutionsToRunInAccount(ACCOUNT_ID, 100)).thenReturn(candidates);

    // Project A: NO HEADROOM (at cap)
    when(planConcurrencyGate.hasHeadroomFor(ACCOUNT_ID, "proj_a")).thenReturn(false);

    // Project B: HAS HEADROOM
    when(planConcurrencyGate.hasHeadroomFor(ACCOUNT_ID, "proj_b")).thenReturn(true);

    // Project C: HAS HEADROOM (null parentUniqueId)
    when(planConcurrencyGate.hasHeadroomFor(ACCOUNT_ID, null)).thenReturn(true);

    // Mock Plan for all executions
    when(planService.fetchPlan(anyString())).thenReturn(plan);

    // Execute drain
    callback.notifyError(Collections.emptyMap());

    // Verify gate checks:
    // - Dedup check happens BEFORE hasHeadroomFor, BUT only applies to successful starts
    // - If hasHeadroomFor returns false (no start), the scope is NOT added, so next candidate still checks
    verify(planConcurrencyGate, times(2))
        .hasHeadroomFor(ACCOUNT_ID, "proj_a"); // both proj_a checked (both fail, neither added to dedup set)
    verify(planConcurrencyGate, times(1))
        .hasHeadroomFor(ACCOUNT_ID, "proj_b"); // first proj_b checked (succeeds, added to set), second deduped
    verify(planConcurrencyGate, times(2))
        .hasHeadroomFor(ACCOUNT_ID, null); // both proj_c checked (each has unique scope, no dedup)

    // Verify status updates: should start 3 executions
    ArgumentCaptor<String> executionIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(planExecutionService, times(3)).updateStatus(executionIdCaptor.capture(), eq(Status.RUNNING));

    List<String> startedExecutionIds = executionIdCaptor.getAllValues();

    // Assert: Project A candidates NOT started (at cap, skipped)
    assert !startedExecutionIds.contains("proj_a_exec1");
    assert !startedExecutionIds.contains("proj_a_exec2");

    // Assert: Only FIRST Project B candidate started (second deduped)
    assert startedExecutionIds.contains("proj_b_exec1");
    assert !startedExecutionIds.contains("proj_b_exec2");

    // Assert: BOTH Project C candidates started (null parentUniqueId = unique scope per exec, no dedup)
    assert startedExecutionIds.contains("proj_c_exec1");
    assert startedExecutionIds.contains("proj_c_exec2");

    // Verify planExecutionStrategy.startPlanExecution called 3 times
    verify(planExecutionStrategy, times(3)).startPlanExecution(eq(plan), eq(ambiance));
  }

  /**
   * Scenario: All candidates from same project, project at cap.
   * Expected: NO executions started (all skipped).
   */
  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDrainWithSkipAhead_AllCandidatesAtCap() {
    enablePerProjectMode();

    PlanExecution exec1 = createPlanExecution("exec1", "plan1", "proj_a");
    PlanExecution exec2 = createPlanExecution("exec2", "plan2", "proj_a");
    PlanExecution exec3 = createPlanExecution("exec3", "plan3", "proj_a");

    when(planExecutionService.findNextExecutionsToRunInAccount(ACCOUNT_ID, 100))
        .thenReturn(Arrays.asList(exec1, exec2, exec3));

    // Project A: NO HEADROOM
    when(planConcurrencyGate.hasHeadroomFor(ACCOUNT_ID, "proj_a")).thenReturn(false);

    callback.notifyError(Collections.emptyMap());

    // Verify: NO executions started
    verify(planExecutionService, never()).updateStatus(anyString(), eq(Status.RUNNING));
    verify(planExecutionStrategy, never()).startPlanExecution(any(Plan.class), any(Ambiance.class));
  }

  /**
   * Scenario: All candidates from same project, project has headroom.
   * Expected: Only FIRST candidate started (rest deduped by project scope).
   */
  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDrainWithSkipAhead_SameProjectWithHeadroom_OnlyFirstStarted() {
    enablePerProjectMode();

    PlanExecution exec1 = createPlanExecution("exec1", "plan1", "proj_a");
    PlanExecution exec2 = createPlanExecution("exec2", "plan2", "proj_a");
    PlanExecution exec3 = createPlanExecution("exec3", "plan3", "proj_a");

    when(planExecutionService.findNextExecutionsToRunInAccount(ACCOUNT_ID, 100))
        .thenReturn(Arrays.asList(exec1, exec2, exec3));

    // Project A: HAS HEADROOM
    when(planConcurrencyGate.hasHeadroomFor(ACCOUNT_ID, "proj_a")).thenReturn(true);

    when(planService.fetchPlan(anyString())).thenReturn(plan);

    callback.notifyError(Collections.emptyMap());

    // Verify: Only FIRST execution started
    verify(planExecutionService, times(1)).updateStatus("exec1", Status.RUNNING);
    verify(planExecutionService, never()).updateStatus("exec2", Status.RUNNING);
    verify(planExecutionService, never()).updateStatus("exec3", Status.RUNNING);

    // Gate check should only be called once (first candidate), rest skipped due to dedup
    verify(planConcurrencyGate, times(1)).hasHeadroomFor(ACCOUNT_ID, "proj_a");
  }

  /**
   * Scenario: Multiple candidates with null parentUniqueId (legacy behavior).
   * Expected: All candidates started if hasHeadroomFor(accountId, null) returns true.
   * Each execution gets unique scope (uuid), so no dedup.
   */
  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDrainWithSkipAhead_NullParentUniqueId_AllStarted() {
    enablePerProjectMode();

    PlanExecution exec1 = createPlanExecution("exec1", "plan1", null);
    PlanExecution exec2 = createPlanExecution("exec2", "plan2", null);
    PlanExecution exec3 = createPlanExecution("exec3", "plan3", null);

    when(planExecutionService.findNextExecutionsToRunInAccount(ACCOUNT_ID, 100))
        .thenReturn(Arrays.asList(exec1, exec2, exec3));

    // All have headroom (null parentUniqueId)
    when(planConcurrencyGate.hasHeadroomFor(ACCOUNT_ID, null)).thenReturn(true);

    when(planService.fetchPlan(anyString())).thenReturn(plan);

    callback.notifyError(Collections.emptyMap());

    // Verify: ALL 3 executions started (each has unique scope = uuid, no dedup)
    verify(planExecutionService, times(1)).updateStatus("exec1", Status.RUNNING);
    verify(planExecutionService, times(1)).updateStatus("exec2", Status.RUNNING);
    verify(planExecutionService, times(1)).updateStatus("exec3", Status.RUNNING);

    // Gate check called 3 times (once per candidate)
    verify(planConcurrencyGate, times(3)).hasHeadroomFor(ACCOUNT_ID, null);
  }

  /**
   * Scenario: Empty candidate list.
   * Expected: No crashes, no starts, no gate checks.
   */
  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDrainWithSkipAhead_EmptyCandidateList() {
    enablePerProjectMode();

    when(planExecutionService.findNextExecutionsToRunInAccount(ACCOUNT_ID, 100)).thenReturn(Collections.emptyList());

    callback.notifyError(Collections.emptyMap());

    // Verify: No executions started, no gate checks
    verify(planExecutionService, never()).updateStatus(anyString(), eq(Status.RUNNING));
    verify(planConcurrencyGate, never()).hasHeadroomFor(anyString(), anyString());
  }

  /**
   * Scenario: Lock acquisition fails.
   * Expected: Drain not attempted, early return.
   */
  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDrainWithSkipAhead_LockAcquisitionFails() {
    enablePerProjectMode();

    // Lock acquisition fails
    when(persistentLocker.waitToAcquireLockOptional(
             String.format(LOCK_NAME, ACCOUNT_ID), Duration.ofSeconds(30), Duration.ofSeconds(30)))
        .thenReturn(null);

    callback.notifyError(Collections.emptyMap());

    // Verify: No drain attempted
    verify(planExecutionService, never()).findNextExecutionsToRunInAccount(anyString(), anyInt());
    verify(planConcurrencyGate, never()).hasHeadroomFor(anyString(), anyString());
  }

  /**
   * Scenario: Per-project mode disabled (FF off or mode != PER_PROJECT).
   * Expected: Falls back to legacy account-wide drain (single-pick).
   */
  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDrainWithSkipAhead_PerProjectModeDisabled_FallbackToLegacy() {
    // Disable per-project mode
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES.name()))
        .thenReturn(false);

    PlanExecution nextExec = createPlanExecution("exec1", "plan1", "proj_a");
    when(planExecutionService.findNextExecutionToRunInAccount(ACCOUNT_ID)).thenReturn(nextExec);

    // Account has headroom
    when(pipelineSettingsService.getMaxConcurrency(ACCOUNT_ID)).thenReturn(100L);
    when(pipelineSettingsService.getCurrentExecutionCount(ACCOUNT_ID)).thenReturn(50L);

    when(planService.fetchPlan("plan1")).thenReturn(plan);

    callback.notifyError(Collections.emptyMap());

    // Verify: Legacy single-pick drain used
    verify(planExecutionService, times(1)).findNextExecutionToRunInAccount(ACCOUNT_ID);
    verify(planExecutionService, never()).findNextExecutionsToRunInAccount(anyString(), anyInt());

    // Verify: Execution started
    verify(planExecutionService, times(1)).updateStatus("exec1", Status.RUNNING);
  }

  // ======================= Helper Methods =======================

  private void enablePerProjectMode() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES.name()))
        .thenReturn(true);
    when(pipelineSettingsService.getConcurrencyMode(ACCOUNT_ID)).thenReturn(PlanExecutionConcurrencyMode.PER_PROJECT);
  }

  private PlanExecution createPlanExecution(String uuid, String planId, String parentUniqueId) {
    Map<String, String> setupAbstractions = new HashMap<>();
    if (parentUniqueId != null) {
      setupAbstractions.put(SetupAbstractionKeys.parentUniqueId, parentUniqueId);
    }

    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(uuid)
                                      .planId(planId)
                                      .setupAbstractions(setupAbstractions)
                                      .ambiance(ambiance)
                                      .build();
    return planExecution;
  }
}
