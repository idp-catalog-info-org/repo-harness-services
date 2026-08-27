/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.pipelinerollback;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.harness.CategoryTest;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.engine.execution.PipelineStageResponseData;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.helper.PipelineExecutor;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;
import io.harness.waiter.StringNotifyResponseData;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineRollbackStageStepTest extends CategoryTest {
  @InjectMocks PipelineRollbackStageStep step;
  @Mock PipelineExecutor pipelineExecutor;
  @Mock PmsExecutionSummaryService executionSummaryService;
  @Mock InterruptService interruptService;
  @Mock PMSExecutionService executionService;

  String accountId = "acc";
  String orgId = "org";
  String projectId = "proj";
  String parentUniqueId = "parentUniqueId";
  String currentPlanExecutionId = "curr";
  String rollbackPlanExecutionId = "rollbackPlanExecutionId";

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac() {
    PlanExecution planExecution = PlanExecution.builder().uuid("rbUuid").build();
    doReturn(planExecution)
        .when(pipelineExecutor)
        .startPipelineRollback(accountId, orgId, projectId, currentPlanExecutionId,
            PipelineStageInfo.newBuilder().setHasParentPipeline(false).setStageNodeId("setupId").build(),
            ScopeInfo.builder()
                .accountIdentifier(accountId)
                .orgIdentifier(orgId)
                .projectIdentifier(projectId)
                .uniqueId(parentUniqueId)
                .scopeType(ScopeLevel.PROJECT)
                .build());
    doReturn(null).when(executionSummaryService).update(any(), any());
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .putSetupAbstractions("parentUniqueId", parentUniqueId)
                            .setPlanExecutionId(currentPlanExecutionId)
                            .addLevels(Level.newBuilder().setSetupId("setupId"))
                            .build();
    AsyncExecutableResponse asyncExecutableResponse = step.executeAsyncAfterRbac(ambiance, null, null);
    assertThat(asyncExecutableResponse.getCallbackIdsCount()).isEqualTo(1);
    assertThat(asyncExecutableResponse.getCallbackIds(0)).isEqualTo("rbUuid");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac_ShouldFailWhenCurrentExecutionIsPipelineRollback() {
    // Create an ambiance with ExecutionMode.PIPELINE_ROLLBACK to simulate a pipeline rollback execution
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", accountId)
            .putSetupAbstractions("orgIdentifier", orgId)
            .putSetupAbstractions("projectIdentifier", projectId)
            .putSetupAbstractions("parentUniqueId", parentUniqueId)
            .setPlanExecutionId(currentPlanExecutionId)
            .addLevels(Level.newBuilder().setSetupId("setupId"))
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.PIPELINE_ROLLBACK).build())
            .build();

    // Verify that InvalidRequestException is thrown when trying to start a pipeline rollback
    // from an execution that is already a pipeline rollback
    assertThatThrownBy(() -> step.executeAsyncAfterRbac(ambiance, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to start Pipeline Rollback, as the current execution is also a pipeline rollback");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac_ShouldFailWhenCurrentExecutionIsPostRollback() {
    // Create an ambiance with ExecutionMode.PIPELINE_ROLLBACK to simulate a pipeline rollback execution
    Ambiance ambiance =
        Ambiance.newBuilder()
            .putSetupAbstractions("accountId", accountId)
            .putSetupAbstractions("orgIdentifier", orgId)
            .putSetupAbstractions("projectIdentifier", projectId)
            .putSetupAbstractions("parentUniqueId", parentUniqueId)
            .setPlanExecutionId(currentPlanExecutionId)
            .addLevels(Level.newBuilder().setSetupId("setupId"))
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK).build())
            .build();

    // Verify that InvalidRequestException is thrown when trying to start a pipeline rollback
    // from an execution that is already a pipeline rollback
    assertThatThrownBy(() -> step.executeAsyncAfterRbac(ambiance, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to start Pipeline Rollback, as the current execution is also a pipeline rollback");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleAbort_UserMarked() {
    // Setup
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .setPlanExecutionId(currentPlanExecutionId)
                            .build();
    InterruptConfig interruptConfig = InterruptConfig.getDefaultInstance();

    AsyncExecutableResponse executableResponse =
        AsyncExecutableResponse.newBuilder().addCallbackIds(rollbackPlanExecutionId).build();

    Interrupt interrupt = Interrupt.builder()
                              .type(InterruptType.USER_MARKED_FAIL_ALL)
                              .interruptConfig(interruptConfig)
                              .planExecutionId(currentPlanExecutionId)
                              .build();
    List<Interrupt> interrupts = List.of(interrupt);

    // Mock behavior
    doReturn(interrupts)
        .when(interruptService)
        .fetchPlanLevelInterrupt(
            currentPlanExecutionId, EnumSet.of(InterruptType.USER_MARKED_FAIL_ALL, InterruptType.MARK_FAILED));

    // Execute
    step.handleAbort(ambiance, null, executableResponse, true);

    // Verify
    verify(executionService)
        .registerInterrupt(
            PlanExecutionInterruptType.UserMarkedFailure, rollbackPlanExecutionId, null, interruptConfig);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleAbort_NotUserMarked() {
    // Setup
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .setPlanExecutionId(currentPlanExecutionId)
                            .build();

    InterruptConfig interruptConfig = InterruptConfig.getDefaultInstance();

    AsyncExecutableResponse executableResponse =
        AsyncExecutableResponse.newBuilder().addCallbackIds("callbackId").build();

    Interrupt interrupt = Interrupt.builder()
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(interruptConfig)
                              .planExecutionId(currentPlanExecutionId)
                              .build();
    List<Interrupt> interrupts = List.of(interrupt);

    // Mock behavior
    doReturn(interrupts)
        .when(interruptService)
        .fetchPlanLevelInterrupt(currentPlanExecutionId, EnumSet.of(InterruptType.ABORT_ALL, InterruptType.ABORT));

    // Execute
    step.handleAbort(ambiance, null, executableResponse, false);

    // Verify
    verify(executionService)
        .registerInterrupt(PlanExecutionInterruptType.ABORTALL, "callbackId", null, interruptConfig);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleAbort_NoInterrupts() {
    // Setup
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .setPlanExecutionId(currentPlanExecutionId)
                            .build();

    AsyncExecutableResponse executableResponse =
        AsyncExecutableResponse.newBuilder().addCallbackIds("callbackId").build();

    // Mock behavior - return empty list of interrupts
    doReturn(List.of())
        .when(interruptService)
        .fetchPlanLevelInterrupt(currentPlanExecutionId, EnumSet.of(InterruptType.ABORT_ALL, InterruptType.ABORT));

    // Execute
    step.handleAbort(ambiance, null, executableResponse, false);

    // Verify - no interaction with executionService
    verifyNoInteractions(executionService);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_ExpiredStatusPropagates() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(currentPlanExecutionId).build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put(rollbackPlanExecutionId, PipelineStageResponseData.builder().status(Status.EXPIRED).build());

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, null, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.EXPIRED);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_FailedStatusMarkedSucceeded() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(currentPlanExecutionId).build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put(rollbackPlanExecutionId, PipelineStageResponseData.builder().status(Status.FAILED).build());

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, null, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_SucceededStatus() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(currentPlanExecutionId).build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put(rollbackPlanExecutionId, PipelineStageResponseData.builder().status(Status.SUCCEEDED).build());

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, null, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_EmptyResponseDataMap() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(currentPlanExecutionId).build();

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, null, new HashMap<>());
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse_UnexpectedType() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(currentPlanExecutionId).build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put(rollbackPlanExecutionId, StringNotifyResponseData.builder().data("SUCCESS").build());

    StepResponse stepResponse = step.handleAsyncResponse(ambiance, null, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleAbort_NullExecutableResponse() {
    // Setup
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgId)
                            .putSetupAbstractions("projectIdentifier", projectId)
                            .setPlanExecutionId(currentPlanExecutionId)
                            .build();

    Interrupt interrupt = Interrupt.builder()
                              .type(InterruptType.USER_MARKED_FAIL_ALL)
                              .planExecutionId(currentPlanExecutionId)
                              .interruptConfig(InterruptConfig.getDefaultInstance())
                              .build();
    List<Interrupt> interrupts = List.of(interrupt);

    // Mock behavior
    doReturn(interrupts)
        .when(interruptService)
        .fetchPlanLevelInterrupt(currentPlanExecutionId, EnumSet.of(InterruptType.ABORT_ALL, InterruptType.ABORT));

    // Execute
    step.handleAbort(ambiance, null, null, false);

    // Verify - no interaction with executionService
    verifyNoInteractions(executionService);
  }
}