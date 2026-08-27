/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.step;

import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.engine.execution.PipelineStageResponseData;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.interrupts.service.InterruptService;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.pipelinestage.PipelineStageStepParameters;
import io.harness.pms.pipelinestage.outcome.PipelineStageOutcome;
import io.harness.pms.pipelinestage.output.PipelineStageSweepingOutput;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.helper.PipelineExecutor;
import io.harness.pms.plan.execution.helper.PipelineStageHelper;
import io.harness.pms.plan.execution.helper.PipelineStageStep;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.steps.pipelinestage.ChildPipelineExecutionDetails;
import io.harness.tasks.ResponseData;
import io.harness.utils.ScopeResolutionHelper;

import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PipelineStageStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock PMSExecutionService pmsExecutionService;
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock PipelineStageHelper pipelineStageHelper;
  @Mock AccessControlClient client;
  @Mock PipelineExecutor pipelineExecutor;
  @Mock ExecutionSweepingOutputService sweepingOutputService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock InterruptService interruptService;

  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock ScopeResolutionHelper scopeResolutionHelper;

  @Mock SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @InjectMocks PipelineStageStep pipelineStageStep;

  String planExecutionId = "planExecutionId";
  String accountId = "accountId";
  String projectId = "projectId";
  String orgId = "orgId";
  String parentUniqueId = "parentUniqueId";
  ScopeInfo scopeInfo = ScopeInfo.builder()
                            .accountIdentifier(accountId)
                            .orgIdentifier(orgId)
                            .projectIdentifier(projectId)
                            .uniqueId(parentUniqueId)
                            .build();
  Map<String, String> setup = Map.of(
      "accountId", accountId, "orgIdentifier", orgId, "projectIdentifier", projectId, "parentUniqueId", parentUniqueId);

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAbort() {
    String firstCallBackId = "callBack1";
    String secondCallBackId = "callBack2";
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build();
    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(
                IssuedBy.newBuilder()
                    .setManualIssuer(
                        ManualIssuer.newBuilder().setEmailId("email").setIdentifier("id").setUserId("user1").build())
                    .build())
            .build();
    when(interruptService.fetchPlanLevelInterrupt(
             "planExecutionId", EnumSet.of(InterruptType.ABORT_ALL, InterruptType.ABORT)))
        .thenReturn(List.of(Interrupt.builder()
                                .type(InterruptType.ABORT)
                                .planExecutionId("planExecutionId")
                                .interruptConfig(interruptConfig)
                                .build()));

    pipelineStageStep.handleAbort(ambiance, PipelineStageStepParameters.builder().build(),
        AsyncExecutableResponse.newBuilder().addCallbackIds(firstCallBackId).addCallbackIds(secondCallBackId).build(),
        false);
    verify(pmsExecutionService, times(1))
        .registerInterrupt(PlanExecutionInterruptType.ABORTALL, firstCallBackId, null, interruptConfig);
    Principal principal = SecurityContextBuilder.getPrincipal();
    assertThat(principal).isNotNull();
    assertEquals(principal, new ServicePrincipal("PipelineService"));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testPipelineStageInfo() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(setup)
                            .setMetadata(ExecutionMetadata.newBuilder().setRunSequence(40).build())
                            .build();

    doReturn(Optional.of(PlanExecutionMetadata.builder().triggerJsonPayload("trigger").build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
    PipelineStageStepParameters stepParameters =
        PipelineStageStepParameters.builder().stageNodeId("stageNodeId").build();
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .executionTriggerInfo(ExecutionTriggerInfo.newBuilder().build())
                 .build())
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(ambiance.getSetupAbstractions().get("accountId"), planExecutionId);

    doReturn(PipelineExecutionSummaryEntity.builder().name("pipelineName").build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(accountId, ambiance.getPlanExecutionId(),
            Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.name));

    PipelineStageInfo info = pipelineStageStep.prepareParentStageInfo(ambiance, stepParameters);
    assertThat(info.getHasParentPipeline()).isEqualTo(true);
    assertThat(info.getStageNodeId()).isEqualTo("stageNodeId");
    assertThat(info.getExecutionId()).isEqualTo(planExecutionId);
    assertThat(info.getProjectId()).isEqualTo(projectId);
    assertThat(info.getOrgId()).isEqualTo(orgId);
    assertThat(info.getRunSequence()).isEqualTo(40);
    assertThat(info.getPipelineName()).isEqualTo("pipelineName");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testStepParameters() {
    assertThat(pipelineStageStep.getStepParametersClass()).isEqualTo(PipelineStageStepParameters.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateResource() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(setup)
                            .setMetadata(ExecutionMetadata.newBuilder().setRunSequence(40).build())
                            .build();

    PipelineStageStepParameters stepParameters =
        PipelineStageStepParameters.builder().stageNodeId("stageNodeId").build();
    pipelineStageStep.validateResources(ambiance, stepParameters);
    verify(pipelineStageHelper, times(1)).validateResource(client, ambiance, stepParameters);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(setup)
                            .setMetadata(ExecutionMetadata.newBuilder().setRunSequence(40).build())
                            .build();

    doReturn(Optional.of(PlanExecutionMetadata.builder().triggerJsonPayload("").build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .executionTriggerInfo(ExecutionTriggerInfo.newBuilder().build())
                 .build())
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(ambiance.getSetupAbstractions().get("accountId"), planExecutionId);
    PipelineStageStepParameters stepParameters =
        PipelineStageStepParameters.builder().stageNodeId("stageNodeId").org(orgId).project(projectId).build();
    doReturn(PipelineExecutionSummaryEntity.builder().name("pipelineName").build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(accountId, ambiance.getPlanExecutionId(),
            Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.name));
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(any(), any(), any());
    PipelineStageInfo info = pipelineStageStep.prepareParentStageInfo(ambiance, stepParameters);
    doReturn(PlanExecutionResponseDto.builder().planExecution(PlanExecution.builder().uuid("uuid").build()).build())
        .when(pipelineExecutor)
        .runPipelineAsChildPipelineWithJsonNode(ambiance.getSetupAbstractions().get("accountId"),
            stepParameters.getOrg(), stepParameters.getProject(), stepParameters.getPipeline(),
            ambiance.getMetadata().getModuleType(), stepParameters.getPipelineInputsJsonNode(), false, false,
            stepParameters.getInputSetReferences(), info, ambiance.getMetadata().getIsDebug(), null, false, scopeInfo);

    doReturn(null)
        .when(sweepingOutputService)
        .consume(ambiance, PipelineStageSweepingOutput.OUTPUT_NAME,
            PipelineStageSweepingOutput.builder().childExecutionId("uuid").build(), StepCategory.STAGE.name());
    doNothing()
        .when(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(ambiance,
            ChildPipelineExecutionDetails.builder()
                .planExecutionId("uuid")
                .projectId(stepParameters.getProject())
                .orgId(stepParameters.getOrg())
                .build(),
            "childPipelineExecutionDetails");
    AsyncExecutableResponse asyncExecutableResponse =
        pipelineStageStep.executeAsyncAfterRbac(ambiance, stepParameters, null);

    // to verify if principal is set
    assertThat(SecurityContextBuilder.getPrincipal()).isNotNull();
    assertThat(asyncExecutableResponse.getCallbackIdsList().size()).isEqualTo(1);
    assertThat(asyncExecutableResponse.getCallbackIds(0)).isEqualTo("uuid");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(setup)
                            .setMetadata(ExecutionMetadata.newBuilder().setRunSequence(40).build())
                            .build();

    String planExecutionId = "planExecutionId";
    PipelineStageStepParameters stepParameters =
        PipelineStageStepParameters.builder()
            .stageNodeId("stageNodeId")
            .outputs(ParameterField.<Map<String, ParameterField<String>>>builder().build())
            .build();

    doReturn(OptionalSweepingOutput.builder().build())
        .when(sweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(PipelineStageSweepingOutput.OUTPUT_NAME));
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put(planExecutionId, PipelineStageResponseData.builder().status(Status.SUCCEEDED).build());
    StepResponse stepResponse = pipelineStageStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);

    PipelineStageSweepingOutput output =
        PipelineStageSweepingOutput.builder().childExecutionId(planExecutionId).build();
    doReturn(OptionalSweepingOutput.builder().found(true).output(output).build())
        .when(sweepingOutputService)
        .resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(PipelineStageSweepingOutput.OUTPUT_NAME));

    doReturn(PipelineStageOutcome.builder().build()).when(pipelineStageHelper).resolveOutputVariables(any(), any());
    doReturn(Optional.of(NodeExecution.builder().ambiance(ambiance).build()))
        .when(nodeExecutionService)
        .getPipelineNodeExecutionWithProjections(
            output.getChildExecutionId(), NodeProjectionUtils.WithAmbianceAndFailureInfo);

    stepResponse = pipelineStageStep.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testFailure() {
    String firstCallBackId = "callBack1";
    String secondCallBackId = "callBack2";
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build();
    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(
                IssuedBy.newBuilder()
                    .setManualIssuer(
                        ManualIssuer.newBuilder().setEmailId("email").setIdentifier("id").setUserId("user1").build())
                    .build())
            .build();
    when(interruptService.fetchPlanLevelInterrupt(
             "planExecutionId", EnumSet.of(InterruptType.USER_MARKED_FAIL_ALL, InterruptType.MARK_FAILED)))
        .thenReturn(List.of(Interrupt.builder()
                                .type(InterruptType.USER_MARKED_FAIL_ALL)
                                .planExecutionId("planExecutionId")
                                .interruptConfig(interruptConfig)
                                .build()));

    pipelineStageStep.handleAbort(ambiance, PipelineStageStepParameters.builder().build(),
        AsyncExecutableResponse.newBuilder().addCallbackIds(firstCallBackId).addCallbackIds(secondCallBackId).build(),
        true);
    verify(pmsExecutionService, times(1))
        .registerInterrupt(PlanExecutionInterruptType.UserMarkedFailure, firstCallBackId, null, interruptConfig);
    Principal principal = SecurityContextBuilder.getPrincipal();
    assertThat(principal).isNotNull();
    assertEquals(principal, new ServicePrincipal("PipelineService"));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testExpire() {
    String firstCallBackId = "callBack1";
    String secondCallBackId = "callBack2";
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build();
    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(
                IssuedBy.newBuilder()
                    .setManualIssuer(
                        ManualIssuer.newBuilder().setEmailId("email").setIdentifier("id").setUserId("user1").build())
                    .build())
            .build();
    when(interruptService.fetchPlanLevelInterrupt(
             "planExecutionId", EnumSet.of(InterruptType.EXPIRE_ALL, InterruptType.MARK_EXPIRED)))
        .thenReturn(List.of(Interrupt.builder()
                                .type(InterruptType.EXPIRE_ALL)
                                .planExecutionId("planExecutionId")
                                .interruptConfig(interruptConfig)
                                .build()));

    pipelineStageStep.handleExpire(ambiance, PipelineStageStepParameters.builder().build(),
        AsyncExecutableResponse.newBuilder().addCallbackIds(firstCallBackId).addCallbackIds(secondCallBackId).build());
    verify(pmsExecutionService, times(1))
        .registerInterrupt(PlanExecutionInterruptType.EXPIREALL, firstCallBackId, null, interruptConfig);
    Principal principal = SecurityContextBuilder.getPrincipal();
    assertThat(principal).isNotNull();
    assertEquals(principal, new ServicePrincipal("PipelineService"));
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testIsParentPipelineUsingOriginalDefinition_WhenYamlsMatch() {
    String accountId = "accountId";
    String currentExecutionId = "currentExecId";
    String originalExecutionId = "originalExecId";
    String originalYaml = "pipeline:\n  name: test";

    when(planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
             eq(accountId), eq(currentExecutionId), any()))
        .thenReturn(PlanExecutionMetadata.builder().yaml(originalYaml).build());

    when(planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
             eq(accountId), eq(originalExecutionId), any()))
        .thenReturn(PlanExecutionMetadata.builder().yaml(originalYaml).build());

    boolean result =
        pipelineStageStep.isParentPipelineUsingOriginalDefinition(accountId, currentExecutionId, originalExecutionId);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testIsParentPipelineUsingOriginalDefinition_WhenYamlsDontMatch() {
    String accountId = "accountId";
    String currentExecutionId = "currentExecId";
    String originalExecutionId = "originalExecId";
    String currentYaml = "pipeline:\n  name: test";
    String originalYaml = "pipeline:\n  name: different";

    when(planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
             eq(accountId), eq(currentExecutionId), any()))
        .thenReturn(PlanExecutionMetadata.builder().yaml(currentYaml).build());

    when(planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
             eq(accountId), eq(originalExecutionId), any()))
        .thenReturn(PlanExecutionMetadata.builder().yaml(originalYaml).build());

    boolean result =
        pipelineStageStep.isParentPipelineUsingOriginalDefinition(accountId, currentExecutionId, originalExecutionId);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testFindOriginalChildExecutionId_WhenFound() {
    String originalParentExecId = "originalParentExecId";
    String originalChildExecId = "originalChildExecId";
    String stageFqn = "stage1";
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("currentExecId").build();

    NodeExecution nodeExecution = NodeExecution.builder().stageFqn(stageFqn).build();
    when(nodeExecutionService.get((String) any())).thenReturn(nodeExecution);

    NodeExecution originalNodeExecution = NodeExecution.builder().build();
    when(nodeExecutionService.fetchNodeExecutionsForGivenStageFQNs(eq(originalParentExecId), any(), any()))
        .thenReturn(java.util.stream.Stream.of(originalNodeExecution));

    when(nodeExecutionService.getAmbiance(originalNodeExecution)).thenReturn(Ambiance.newBuilder().build());

    PipelineStageSweepingOutput output =
        PipelineStageSweepingOutput.builder().childExecutionId(originalChildExecId).build();
    when(sweepingOutputService.resolve(any(), any())).thenReturn(output);

    String result = pipelineStageStep.findOriginalChildExecutionId(originalParentExecId, ambiance);

    assertThat(result).isEqualTo(originalChildExecId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testRegisterDummyAbortInterruptForParent_WhenChildInterruptExists() {
    String childExecutionId = "childExecId";
    String planExecutionId = "planExecId";
    String runtimeId = "runtimeId";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .addLevels(Level.newBuilder().setRuntimeId(runtimeId).build())
                            .build();
    // Mock child abort interrupt
    InterruptConfig interruptConfig = InterruptConfig.newBuilder().build();
    Interrupt childInterrupt = Interrupt.builder()
                                   .planExecutionId(childExecutionId)
                                   .interruptConfig(interruptConfig)
                                   .type(InterruptType.ABORT)
                                   .createdAt(System.currentTimeMillis())
                                   .state(State.PROCESSED_SUCCESSFULLY)
                                   .build();
    when(interruptService.fetchAbortAllPlanLevelInterrupt(childExecutionId)).thenReturn(Arrays.asList(childInterrupt));
    // Mock no parent interrupt
    when(interruptService.fetchAbortAllPlanLevelInterrupt(planExecutionId)).thenReturn(Collections.emptyList());
    pipelineStageStep.registerDummyAbortInterruptForParent(ambiance, childExecutionId);
    // Verify interrupt was saved for parent
    verify(interruptService)
        .save(argThat(interrupt
            -> interrupt.getPlanExecutionId().equals(planExecutionId)
                && interrupt.getNodeExecutionId().equals(runtimeId) && interrupt.getType() == InterruptType.ABORT
                && interrupt.getInterruptConfig() == interruptConfig
                && interrupt.getState() == State.PROCESSED_SUCCESSFULLY));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testRegisterDummyAbortInterruptForParent_WhenParentInterruptExists() {
    String childExecutionId = "childExecId";
    String planExecutionId = "planExecId";
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build();
    // Mock child abort interrupt
    Interrupt childInterrupt = Interrupt.builder()
                                   .planExecutionId(childExecutionId)
                                   .type(InterruptType.ABORT)
                                   .state(State.PROCESSED_SUCCESSFULLY)
                                   .build();
    when(interruptService.fetchAbortAllPlanLevelInterrupt(childExecutionId)).thenReturn(Arrays.asList(childInterrupt));
    // Mock existing parent interrupt
    Interrupt parentInterrupt = Interrupt.builder()
                                    .planExecutionId(planExecutionId)
                                    .type(InterruptType.ABORT_ALL)
                                    .state(State.PROCESSED_SUCCESSFULLY)
                                    .build();
    when(interruptService.fetchAbortAllPlanLevelInterrupt(planExecutionId)).thenReturn(Arrays.asList(parentInterrupt));
    pipelineStageStep.registerDummyAbortInterruptForParent(ambiance, childExecutionId);
    // Verify no new interrupt was saved
    verify(interruptService, times(0)).save(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testRegisterDummyAbortInterruptForParent_WhenNoChildInterrupt() {
    String childExecutionId = "childExecId";
    String planExecutionId = "planExecId";
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build();
    // Mock no child interrupt
    when(interruptService.fetchAbortAllPlanLevelInterrupt(childExecutionId))
        .thenReturn(java.util.Collections.emptyList());
    pipelineStageStep.registerDummyAbortInterruptForParent(ambiance, childExecutionId);
    // Verify no new interrupt was saved
    verify(interruptService, times(0)).save(any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testChildPipelineRerunWithOriginalYaml() {
    // Setup
    String accountId = "accountId";
    String planExecutionId = "planExecutionId";
    String originalParentExecId = "originalParentExecId";
    String originalChildExecId = "originalChildExecId";
    String originalYaml = "pipeline:\n  name: test";

    Map<String, String> setup = new HashMap<>();
    setup.put("accountId", accountId);
    setup.put("projectIdentifier", "projectId");
    setup.put("orgIdentifier", "orgId");

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(setup)
                            .setMetadata(ExecutionMetadata.newBuilder().setRunSequence(40).build())
                            .build();

    when(
        planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(eq(accountId), eq(planExecutionId), any()))
        .thenReturn(PlanExecutionMetadata.builder().yaml(originalYaml).build());

    when(planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
             eq(accountId), eq(originalParentExecId), any()))
        .thenReturn(PlanExecutionMetadata.builder().yaml(originalYaml).build());

    boolean result =
        pipelineStageStep.isParentPipelineUsingOriginalDefinition(accountId, planExecutionId, originalParentExecId);

    assertThat(result).isTrue();

    NodeExecution nodeExecution = NodeExecution.builder().stageFqn("stage1").build();
    when(nodeExecutionService.get((String) any())).thenReturn(nodeExecution);

    NodeExecution originalNodeExecution = NodeExecution.builder().build();
    when(nodeExecutionService.fetchNodeExecutionsForGivenStageFQNs(eq(originalParentExecId), any(), any()))
        .thenReturn(java.util.stream.Stream.of(originalNodeExecution));

    when(nodeExecutionService.getAmbiance(originalNodeExecution)).thenReturn(Ambiance.newBuilder().build());

    PipelineStageSweepingOutput output =
        PipelineStageSweepingOutput.builder().childExecutionId(originalChildExecId).build();
    when(sweepingOutputService.resolve(any(), any())).thenReturn(output);

    String childExecId = pipelineStageStep.findOriginalChildExecutionId(originalParentExecId, ambiance);

    assertThat(childExecId).isEqualTo(originalChildExecId);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testExecuteWithChildBranchOverridesDuringRunAndRestoresAfter() {
    // Parent context branch = main
    GitEntityInfo parent = GitEntityInfo.builder().branch("main").connectorRef("conn").repoName("repo").build();
    GitAwareContextHelper.updateGitEntityContext(parent);

    // Setup
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(setup)
                            .setMetadata(ExecutionMetadata.newBuilder().setRunSequence(40).build())
                            .build();

    doReturn(Optional.of(PlanExecutionMetadata.builder().triggerJsonPayload("").build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .executionTriggerInfo(ExecutionTriggerInfo.newBuilder().build())
                 .build())
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(ambiance.getSetupAbstractions().get("accountId"), planExecutionId);
    PipelineStageStepParameters stepParameters = PipelineStageStepParameters.builder()
                                                     .stageNodeId("stageNodeId")
                                                     .pipeline("child")
                                                     .org(orgId)
                                                     .project(projectId)
                                                     .gitBranch("devtest")
                                                     .build();
    doReturn(PipelineExecutionSummaryEntity.builder().name("pipelineName").build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(accountId, ambiance.getPlanExecutionId(),
            Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.name));
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(any(), any(), any());
    PipelineStageInfo info = pipelineStageStep.prepareParentStageInfo(ambiance, stepParameters);
    when(pipelineExecutor.runPipelineAsChildPipelineWithJsonNode(ambiance.getSetupAbstractions().get("accountId"),
             stepParameters.getOrg(), stepParameters.getProject(), stepParameters.getPipeline(),
             ambiance.getMetadata().getModuleType(), stepParameters.getPipelineInputsJsonNode(), false, false,
             stepParameters.getInputSetReferences(), info, ambiance.getMetadata().getIsDebug(), null, false, scopeInfo))
        .thenAnswer(inv -> {
          String transientBranch = GitAwareContextHelper.getGitRequestParamsInfo().getTransientBranch();
          String activeBranch = GitAwareContextHelper.getGitRequestParamsInfo().getBranch();
          assertThat(activeBranch).isEqualTo("main");
          assertThat(transientBranch).isEqualTo("devtest");
          return PlanExecutionResponseDto.builder()
              .planExecution(io.harness.execution.PlanExecution.builder().uuid("id").build())
              .build();
        });

    doReturn(null)
        .when(sweepingOutputService)
        .consume(ambiance, PipelineStageSweepingOutput.OUTPUT_NAME,
            PipelineStageSweepingOutput.builder().childExecutionId("uuid").build(), StepCategory.STAGE.name());
    doNothing()
        .when(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(ambiance,
            ChildPipelineExecutionDetails.builder()
                .planExecutionId("uuid")
                .projectId(stepParameters.getProject())
                .orgId(stepParameters.getOrg())
                .build(),
            "childPipelineExecutionDetails");

    pipelineStageStep.executeAsyncAfterRbac(ambiance, stepParameters, null);

    // After execution, branch restored to parent
    assertThat(GitAwareContextHelper.getGitRequestParamsInfo().getBranch()).isEqualTo("main");
    assertThat(GitAwareContextHelper.getGitRequestParamsInfo().getTransientBranch()).isEqualTo(null);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testExecuteWithoutBranchDoesNotOverride() {
    // Parent context branch = main
    GitEntityInfo parent = GitEntityInfo.builder().branch("main").connectorRef("conn").repoName("repo").build();
    GitAwareContextHelper.updateGitEntityContext(parent);

    // Setup
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putAllSetupAbstractions(setup)
                            .setMetadata(ExecutionMetadata.newBuilder().setRunSequence(40).build())
                            .build();

    doReturn(Optional.of(PlanExecutionMetadata.builder().triggerJsonPayload("").build()))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(AmbianceUtils.getAccountId(ambiance), ambiance.getPlanExecutionId());
    doReturn(PipelineExecutionSummaryEntity.builder()
                 .executionTriggerInfo(ExecutionTriggerInfo.newBuilder().build())
                 .build())
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(ambiance.getSetupAbstractions().get("accountId"), planExecutionId);
    PipelineStageStepParameters stepParameters = PipelineStageStepParameters.builder()
                                                     .stageNodeId("stageNodeId")
                                                     .pipeline("child")
                                                     .org(orgId)
                                                     .project(projectId)
                                                     .gitBranch(null)
                                                     .build();
    doReturn(PipelineExecutionSummaryEntity.builder().name("pipelineName").build())
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(accountId, ambiance.getPlanExecutionId(),
            Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.name));
    doReturn(scopeInfo).when(scopeResolutionHelper).getScopeInfo(any(), any(), any());
    PipelineStageInfo info = pipelineStageStep.prepareParentStageInfo(ambiance, stepParameters);
    when(pipelineExecutor.runPipelineAsChildPipelineWithJsonNode(ambiance.getSetupAbstractions().get("accountId"),
             stepParameters.getOrg(), stepParameters.getProject(), stepParameters.getPipeline(),
             ambiance.getMetadata().getModuleType(), stepParameters.getPipelineInputsJsonNode(), false, false,
             stepParameters.getInputSetReferences(), info, ambiance.getMetadata().getIsDebug(), null, false, scopeInfo))
        .thenAnswer(inv -> {
          String transientBranch = GitAwareContextHelper.getGitRequestParamsInfo().getTransientBranch();
          String activeBranch = GitAwareContextHelper.getGitRequestParamsInfo().getBranch();
          assertThat(activeBranch).isEqualTo("main");
          assertThat(transientBranch).isEqualTo(null);
          return PlanExecutionResponseDto.builder()
              .planExecution(io.harness.execution.PlanExecution.builder().uuid("id").build())
              .build();
        });

    doReturn(null)
        .when(sweepingOutputService)
        .consume(ambiance, PipelineStageSweepingOutput.OUTPUT_NAME,
            PipelineStageSweepingOutput.builder().childExecutionId("uuid").build(), StepCategory.STAGE.name());
    doNothing()
        .when(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(ambiance,
            ChildPipelineExecutionDetails.builder()
                .planExecutionId("uuid")
                .projectId(stepParameters.getProject())
                .orgId(stepParameters.getOrg())
                .build(),
            "childPipelineExecutionDetails");

    pipelineStageStep.executeAsyncAfterRbac(ambiance, stepParameters, null);
    assertThat(GitAwareContextHelper.getGitRequestParamsInfo().getBranch()).isEqualTo("main");
    assertThat(GitAwareContextHelper.getGitRequestParamsInfo().getTransientBranch()).isEqualTo(null);
  }
}
