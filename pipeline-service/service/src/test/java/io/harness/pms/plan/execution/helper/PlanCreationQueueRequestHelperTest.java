/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.execution.Status.QUEUED_PLAN_CREATION;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.ANKUR_PATEL;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;
import static io.harness.rule.OwnerRule.THRISHANK;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.settings.response.PlanExecutionSettingResponse;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.concurrency.PlanConcurrencyGate;
import io.harness.engine.executions.plan.service.PlanCreationQueueRequestService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanCreationQueueRequest;
import io.harness.execution.PlanCreationRequest;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.execution.PriorityConcurrentExecutionsMetadata;
import io.harness.execution.PriorityProjects;
import io.harness.execution.PriorityType;
import io.harness.expression.RuntimeInputValuesValidatorV1;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.governance.GovernanceMetadata;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.metrics.service.api.MetricService;
import io.harness.observer.Subject;
import io.harness.opa.gitx.OpaGitxStatus;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.plan.Plan;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.response.PipelineMetadataService;
import io.harness.pms.plan.creation.lookup.intfc.NodeTypeLookupService;
import io.harness.pms.plan.execution.PlanExecutionUtils;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.RollbackModeExecutionHelper;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.springdata.TransactionHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
@PrepareForTest({PlanExecutionUtils.class, UUIDGenerator.class})
public class PlanCreationQueueRequestHelperTest extends CategoryTest {
  @Spy @InjectMocks PlanCreationQueueRequestHelper planCreationQueueRequestHelper;
  @Mock OrchestrationService orchestrationService;

  @Mock PipelineMetadataService pipelineMetadataService;
  @Mock PlanExecutionService planExecutionService;
  @Mock PipelineSettingsService pipelineSettingsService;
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock PrincipalInfoHelper principalInfoHelper;
  @Mock PlanCreatorMergeService planCreatorMergeService;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock PipelineGovernanceService pipelineGovernanceService;
  @Mock NodeTypeLookupService nodeTypeLookupService;
  @Mock MetricService metricService;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock PlanCreationQueueRequestService planCreationQueueRequestService;

  @Mock RollbackModeExecutionHelper rollbackModeExecutionHelper;
  @Mock PlanService planService;
  @Mock RetryExecutionHelper retryExecutionHelper;
  @Mock RuntimeInputValuesValidatorV1 runtimeInputValuesValidatorV1;
  @Mock Subject<OrchestrationStartObserver> orchestrationStartSubject;
  @Mock HsqsClientService hsqsClientService;
  @Mock TransactionHelper transactionHelper;
  @Mock QueueServiceClientConfig queueServiceClientConfig;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock io.harness.pms.pipeline.service.BranchSequenceService branchSequenceService;
  @Mock PipelineIdentityService pipelineIdentityService;
  @Mock PlanConcurrencyGate planConcurrencyGate;

  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String uniqueId = "uniqueId";
  String pipelineId = "pipelineId";
  String pipelineYaml = "pipeline:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        description: <+input>\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        description: <+input>\n"
      + "  allowStageExecutions: true\n";
  String pipelineYamlWithExpressions = "pipeline:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        description: desc\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        description: <+pipeline.stages.s1.description>\n"
      + "  allowStageExecutions: true\n";
  String mergedPipelineYaml = "pipeline:\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: s1\n"
      + "        description: desc\n"
      + "    - stage:\n"
      + "        identifier: s2\n"
      + "        description: desc\n"
      + "  allowStageExecutions: true\n";

  String originalExecutionId = "originalExecutionId";
  String generatedExecutionId = "newExecId";

  PipelineEntity pipelineEntity;
  PipelineEntity pipelineEntityWithExpressions;
  TriggeredBy triggeredBy;
  ExecutionTriggerInfo executionTriggerInfo;
  ExecutionPrincipalInfo executionPrincipalInfo;
  MockedStatic<UUIDGenerator> aStatic;
  PlanExecutionMetadata prevExecutionMetadata;
  PlanExecutionMetadata prevExecutionMetadataWithoutToken;
  MockedStatic<PlanExecutionUtils> bStatic;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    pipelineEntity = PipelineEntity.builder()
                         .accountId(accountId)
                         .orgIdentifier(orgId)
                         .projectIdentifier(projectId)
                         .identifier(pipelineId)
                         .yaml(pipelineYaml)
                         .runSequence(394)
                         .build();
    pipelineEntityWithExpressions = PipelineEntity.builder()
                                        .accountId(accountId)
                                        .orgIdentifier(orgId)
                                        .projectIdentifier(projectId)
                                        .identifier(pipelineId)
                                        .yaml(pipelineYamlWithExpressions)
                                        .runSequence(394)
                                        .build();
    triggeredBy = TriggeredBy.newBuilder().setUuid("userUuid").setIdentifier("username").build();
    executionTriggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).setTriggerType(MANUAL).setIsRerun(false).build();
    executionPrincipalInfo = ExecutionPrincipalInfo.newBuilder().build();
    doNothing().when(metricService).recordMetric(anyString(), anyDouble());
    aStatic = Mockito.mockStatic(UUIDGenerator.class);
    aStatic.when(UUIDGenerator::generateUuid).thenReturn(generatedExecutionId);
    prevExecutionMetadata = PlanExecutionMetadata.builder().expressionFunctorToken(1234L).build();
    prevExecutionMetadataWithoutToken = PlanExecutionMetadata.builder().build();
    buildExecutionArgsMocks();
    bStatic = Mockito.mockStatic(PlanExecutionUtils.class);
    doNothing()
        .when(runtimeInputValuesValidatorV1)
        .validate(any(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
  }

  @After
  public void afterMethod() {
    aStatic.close();
    bStatic.close();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSavePlanExecutionAndQueuePlanExecutionRequest() {
    on(planCreationQueueRequestHelper).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = "testPlanExecId";
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .pipelineYamlWithTemplateRef("pipelineYamlWithTemplateRef")
            .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setPipelineIdentifier(pipelineId)
                                              .setExecutionUuid(planExecutionId)
                                              .setRunSequence(394)
                                              .build();
    PlanCreationRequest planCreationRequest = PlanCreationRequest.builder()
                                                  .accountId(accountId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .executionMetadata(executionMetadata)
                                                  .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                  .build();
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .setupAbstractions(setupAbstractions)
                                      .status(QUEUED_PLAN_CREATION)
                                      .metadata(executionMetadata)
                                      .priorityType(PriorityType.NORMAL)
                                      .build();
    doReturn(395).when(pipelineMetadataService).incrementRunSequence(any(), any(), any(), any(), any(), anyBoolean());
    doReturn(false).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(accountId);
    assertThatThrownBy(
        () -> planCreationQueueRequestHelper.savePlanExecutionAndQueuePlanExecutionRequest(planCreationRequest))
        .isInstanceOf(LimitExceededException.class)
        .hasMessage("You have exceeded the number of queued executions allowed on the account. Please upgrade your "
            + "plan or contact harness support.");

    doReturn(true).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(accountId);
    doReturn(planExecution).when(transactionHelper).performTransaction(any());
    doReturn(EnqueueResponse.builder().itemId("itemId").build()).when(hsqsClientService).enqueue(any());
    PlanExecution planExecution1 =
        planCreationQueueRequestHelper.savePlanExecutionAndQueuePlanExecutionRequest(planCreationRequest);
    assertThat(planExecution1).isEqualTo(planExecution);
    verify(orchestrationStartSubject, times(1)).fireInform(any(), any());
    verify(hsqsClientService, times(1)).enqueue(any());

    doThrow(new InvalidRequestException("Error Message")).when(hsqsClientService).enqueue(any());
    assertThatThrownBy(
        () -> planCreationQueueRequestHelper.savePlanExecutionAndQueuePlanExecutionRequest(planCreationRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error Message");
    verify(orchestrationStartSubject, times(2)).fireInform(any(), any());

    doThrow(new InvalidRequestException("Error Message")).when(orchestrationStartSubject).fireInform(any(), any());
    assertThatThrownBy(
        () -> planCreationQueueRequestHelper.savePlanExecutionAndQueuePlanExecutionRequest(planCreationRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Error Message");
    verify(planExecutionService, times(1)).updateStatus(eq(planExecutionId), eq(Status.ERRORED), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExecutePlanCreationRequest() throws IOException {
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setHarnessVersion(HarnessYamlVersion.V0)
                                              .setProcessedYamlVersion(HarnessYamlVersion.V0)
                                              .setRunSequence(394)
                                              .build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build();
    String startingNodeId = "startingNodeId";
    PlanCreationBlobResponse planCreationBlobResponse =
        PlanCreationBlobResponse.newBuilder().setStartingNodeId(startingNodeId).build();
    ScopeInfo mockScopeInfo = ScopeInfo.builder()
                                  .accountIdentifier(accountId)
                                  .uniqueId(uniqueId)
                                  .orgIdentifier(orgId)
                                  .projectIdentifier(projectId)
                                  .build();
    doReturn(Optional.of(mockScopeInfo)).when(scopeResolutionHelper).getScopeInfoOptional(accountId, orgId, projectId);
    doReturn(planCreationBlobResponse)
        .when(planCreatorMergeService)
        .createPipelinePlanVersion(accountId, orgId, projectId, HarnessYamlVersion.V0, executionMetadata,
            planExecutionMetadataWithContext, mockScopeInfo, false);

    PlanExecution planExecution = PlanExecution.builder().build();
    Plan plan = Plan.builder().startingNodeId(startingNodeId).accountIdentifier(accountId).build();
    bStatic.when(() -> PlanExecutionUtils.extractPlan(planCreationBlobResponse, accountId)).thenReturn(plan);
    Map<String, String> abstractions = new HashMap<>();
    abstractions.put(SetupAbstractionKeys.accountId, accountId);
    abstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    abstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);
    abstractions.put(SetupAbstractionKeys.parentUniqueId, uniqueId);
    abstractions.put("status", ExecutionStatus.SUCCESS.name());
    doReturn(planExecution)
        .when(orchestrationService)
        .startExecution(plan, abstractions, executionMetadata, planExecutionMetadataWithContext);
    PlanExecution createdPlanExecution = planCreationQueueRequestHelper.executePlanCreationRequest(
        PlanCreationRequest.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .executionMetadata(executionMetadata)
            .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
            .scopeInfo(mockScopeInfo)
            .isParentIdQueryingEnabled(false)
            .isDebug(false)
            .runSequenceIncrementNeeded(false)
            .build());
    assertThat(createdPlanExecution).isEqualTo(planExecution);
    verify(planCreatorMergeService, times(1))
        .createPipelinePlanVersion(accountId, orgId, projectId, HarnessYamlVersion.V0, executionMetadata,
            planExecutionMetadataWithContext, mockScopeInfo, false);
    verify(orchestrationService, times(1))
        .startExecution(plan, abstractions, executionMetadata, planExecutionMetadataWithContext);
    verify(rollbackModeExecutionHelper, never())
        .transformPlanForRollbackMode(any(), anyString(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testExecutePlanCreationRequestInPostExecutionRollbackMode() throws IOException {
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setHarnessVersion(HarnessYamlVersion.V0)
                                              .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                              .setProcessedYamlVersion(HarnessYamlVersion.V0)
                                              .setRunSequence(394)
                                              .build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .previousExecutionId("prevId")
            .build();

    String startingNodeId = "startingNodeId";
    PlanCreationBlobResponse planCreationBlobResponse = PlanCreationBlobResponse.newBuilder()
                                                            .setStartingNodeId(startingNodeId)
                                                            .addPreservedNodesInRollbackMode("n1")
                                                            .build();
    ScopeInfo mockScopeInfo = ScopeInfo.builder()
                                  .accountIdentifier(accountId)
                                  .uniqueId(uniqueId)
                                  .orgIdentifier(orgId)
                                  .projectIdentifier(projectId)
                                  .build();
    doReturn(Optional.of(mockScopeInfo)).when(scopeResolutionHelper).getScopeInfoOptional(accountId, orgId, projectId);
    doReturn(planCreationBlobResponse)
        .when(planCreatorMergeService)
        .createPipelinePlanVersion(accountId, orgId, projectId, HarnessYamlVersion.V0, executionMetadata,
            planExecutionMetadataWithContext, mockScopeInfo, false);

    PlanExecution planExecution = PlanExecution.builder().build();
    Plan plan = Plan.builder()
                    .startingNodeId(startingNodeId)
                    .preservedNodesInRollbackMode(Collections.singletonList("n1"))
                    .accountIdentifier(accountId)
                    .build();
    bStatic.when(() -> PlanExecutionUtils.extractPlan(planCreationBlobResponse, accountId)).thenReturn(plan);
    Map<String, String> abstractions = new HashMap<>();
    abstractions.put(SetupAbstractionKeys.accountId, accountId);
    abstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    abstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);
    abstractions.put(SetupAbstractionKeys.parentUniqueId, uniqueId);
    abstractions.put("status", ExecutionStatus.SUCCESS.name());
    doReturn(plan)
        .when(rollbackModeExecutionHelper)
        .transformPlanForRollbackMode(plan, "prevId", Collections.singletonList("n1"),
            ExecutionMode.POST_EXECUTION_ROLLBACK, Collections.emptyList(), accountId);
    doReturn(planExecution)
        .when(orchestrationService)
        .startExecution(plan, abstractions, executionMetadata, planExecutionMetadataWithContext);

    String planId = "planId";
    doReturn(PlanExecution.builder().planId(planId).build())
        .when(planExecutionService)
        .getWithFieldsIncluded("prevId", Set.of(PlanExecutionKeys.planId));
    doReturn(plan).when(planService).fetchPlan(planId);
    doReturn(plan.getPlanNodes()).when(planService).fetchNodes(planId);

    PlanExecution createdPlanExecution = planCreationQueueRequestHelper.executePlanCreationRequest(
        PlanCreationRequest.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .executionMetadata(executionMetadata)
            .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
            .scopeInfo(mockScopeInfo)
            .isParentIdQueryingEnabled(false)
            .isDebug(false)
            .runSequenceIncrementNeeded(false)
            .build());
    assertThat(createdPlanExecution).isEqualTo(planExecution);
    verify(orchestrationService, times(1))
        .startExecution(plan, abstractions, executionMetadata, planExecutionMetadataWithContext);
    verify(rollbackModeExecutionHelper, times(1))
        .transformPlanForRollbackMode(plan, "prevId", Collections.singletonList("n1"),
            ExecutionMode.POST_EXECUTION_ROLLBACK, Collections.emptyList(), accountId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessMessage_Success() {
    String planExecutionId = "testPlanExecId";
    PlanCreationQueuePayload payload = PlanCreationQueuePayload.builder()
                                           .accountId(accountId)
                                           .planExecutionId(planExecutionId)
                                           .priorityType(PriorityType.NORMAL)
                                           .build();
    DequeueResponse message = DequeueResponse.builder().payload(RecastOrchestrationUtils.toJson(payload)).build();
    PlanCreationQueueRequest queueRequest = PlanCreationQueueRequest.builder()
                                                .accountId(accountId)
                                                .orgId(orgId)
                                                .projectId(projectId)
                                                .pipelineYamlWithTemplateRef("pipeline:\n  name: test")
                                                .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build();
    PlanExecutionMetadata metadata = PlanExecutionMetadata.builder().build();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(QUEUED_PLAN_CREATION).build();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.status)))
        .thenReturn(planExecution);
    when(pipelineSettingsService.shouldQueuePlanExecution(accountId))
        .thenReturn(PlanExecutionSettingResponse.builder().shouldQueue(false).build());
    PlanExecution updatedPlanExecution =
        PlanExecution.builder()
            .uuid(planExecutionId)
            .status(Status.STARTING_PLAN_CREATION)
            .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build())
            .ambiance(Ambiance.newBuilder().setMetadata(executionMetadata).build())
            .build();
    when(planExecutionService.updateStatus(planExecutionId, Status.STARTING_PLAN_CREATION))
        .thenReturn(updatedPlanExecution);
    when(planCreationQueueRequestService.get(planExecutionId)).thenReturn(queueRequest);
    when(planExecutionMetadataService.findByPlanExecutionId(accountId, planExecutionId))
        .thenReturn(Optional.of(metadata));
    when(pmsPipelineService.getPipeline(
             accountId, orgId, projectId, pipelineId, false, false, false, false, null, false))
        .thenReturn(Optional.of(PipelineEntity.builder().build()));
    doReturn("expandedJson")
        .when(pipelineGovernanceService)
        .fetchExpandedPipelineJSONFromYaml(any(), any(), any(), any());
    doNothing().when(planCreationQueueRequestService).updateTTL(planExecutionId);
    doReturn(planExecution).when(planCreationQueueRequestHelper).executePlanCreationRequest(any());
    doReturn(new PmsGitSyncBranchContextGuard(GitSyncBranchContext.builder().build(), true))
        .when(pmsGitSyncHelper)
        .createGitSyncBranchContextGuard(any(), anyBoolean());
    HsqsProcessMessageResponse response = planCreationQueueRequestHelper.processMessage(message);
    ArgumentCaptor<PlanCreationRequest> planCreationRequestArgumentCaptor =
        ArgumentCaptor.forClass(PlanCreationRequest.class);
    assertThat(response.getSuccess()).isTrue();
    assertThat(response.getAccountId()).isEqualTo(accountId);
    verify(planExecutionService, times(1)).updateStatus(eq(planExecutionId), any());
    verify(planCreationQueueRequestHelper, times(1))
        .executePlanCreationRequest(planCreationRequestArgumentCaptor.capture());
    verify(planCreationQueueRequestService).updateTTL(planExecutionId);
    assertThat(planCreationRequestArgumentCaptor.getValue().getAccountId()).isEqualTo(accountId);
    assertThat(planCreationRequestArgumentCaptor.getValue().getOrgIdentifier()).isEqualTo(orgId);
    assertThat(planCreationRequestArgumentCaptor.getValue().getProjectIdentifier()).isEqualTo(projectId);
    assertThat(planCreationRequestArgumentCaptor.getValue().getExecutionMetadata()).isEqualTo(executionMetadata);
    assertThat(
        planCreationRequestArgumentCaptor.getValue().getPlanExecutionMetadataWithContext().getPlanExecutionMetadata())
        .isEqualTo(metadata);
    assertThat(
        planCreationRequestArgumentCaptor.getValue().getPlanExecutionMetadataWithContext().getExpandedPipelineJson())
        .isEqualTo("expandedJson");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessMessage_ExecutionAlreadyAborted() {
    String planExecutionId = "testPlanExecId";
    PlanCreationQueuePayload payload = PlanCreationQueuePayload.builder()
                                           .accountId(accountId)
                                           .planExecutionId(planExecutionId)
                                           .priorityType(PriorityType.NORMAL)
                                           .build();
    DequeueResponse message = DequeueResponse.builder().payload(RecastOrchestrationUtils.toJson(payload)).build();
    PlanExecution abortedExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.ABORTED).build();
    when(planExecutionService.get(planExecutionId)).thenReturn(abortedExecution);
    HsqsProcessMessageResponse response = planCreationQueueRequestHelper.processMessage(message);
    assertThat(response.getSuccess()).isTrue();
    verify(planExecutionService, never()).updateStatus(any(), any());
    verify(planCreationQueueRequestHelper, never()).executePlanCreationRequest(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessMessage_MaxConcurrencyReached() {
    String planExecutionId = "testPlanExecId";
    PlanCreationQueuePayload payload = PlanCreationQueuePayload.builder()
                                           .accountId(accountId)
                                           .planExecutionId(planExecutionId)
                                           .priorityType(PriorityType.NORMAL)
                                           .build();
    DequeueResponse message = DequeueResponse.builder().payload(RecastOrchestrationUtils.toJson(payload)).build();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(QUEUED_PLAN_CREATION).build();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.status)))
        .thenReturn(planExecution);
    when(pipelineSettingsService.shouldQueuePlanExecution(accountId))
        .thenReturn(PlanExecutionSettingResponse.builder().shouldQueue(true).build());

    HsqsProcessMessageResponse response = planCreationQueueRequestHelper.processMessage(message);

    assertThat(response.getSuccess()).isFalse();
    verify(planExecutionService, never()).updateStatus(eq(planExecutionId), eq(Status.STARTING_PLAN_CREATION));
    verify(planCreationQueueRequestService, never()).get(planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessMessage_UpdateStatusFails() {
    String planExecutionId = "testPlanExecId";
    PlanCreationQueuePayload payload = PlanCreationQueuePayload.builder()
                                           .accountId(accountId)
                                           .planExecutionId(planExecutionId)
                                           .priorityType(PriorityType.NORMAL)
                                           .build();
    DequeueResponse message = DequeueResponse.builder().payload(RecastOrchestrationUtils.toJson(payload)).build();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(QUEUED_PLAN_CREATION).build();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.status)))
        .thenReturn(planExecution);
    when(pipelineSettingsService.shouldQueuePlanExecution(accountId))
        .thenReturn(PlanExecutionSettingResponse.builder().shouldQueue(false).build());
    when(planExecutionService.updateStatus(planExecutionId, Status.STARTING_PLAN_CREATION)).thenReturn(null);
    HsqsProcessMessageResponse response = planCreationQueueRequestHelper.processMessage(message);
    assertThat(response.getSuccess()).isTrue();
    verify(planCreationQueueRequestService, never()).get(any());
    verify(planCreationQueueRequestHelper, never()).executePlanCreationRequest(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testProcessMessage_PlanCreationFails() {
    String planExecutionId = "testPlanExecId";
    PlanCreationQueuePayload payload = PlanCreationQueuePayload.builder()
                                           .accountId(accountId)
                                           .planExecutionId(planExecutionId)
                                           .priorityType(PriorityType.NORMAL)
                                           .build();
    DequeueResponse message = DequeueResponse.builder().payload(RecastOrchestrationUtils.toJson(payload)).build();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(QUEUED_PLAN_CREATION).build();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.status)))
        .thenReturn(planExecution);
    when(pipelineSettingsService.shouldQueuePlanExecution(accountId))
        .thenReturn(PlanExecutionSettingResponse.builder().shouldQueue(false).build());
    when(planExecutionService.updateStatus(planExecutionId, Status.STARTING_PLAN_CREATION)).thenReturn(planExecution);
    when(planCreationQueueRequestService.get(planExecutionId)).thenThrow(new RuntimeException("Test exception"));
    HsqsProcessMessageResponse response = planCreationQueueRequestHelper.processMessage(message);
    assertThat(response.getSuccess()).isTrue(); // Returns true to acknowledge message
    verify(planExecutionService).updateStatus(eq(planExecutionId), eq(Status.ERRORED), any());
    verify(planCreationQueueRequestHelper, never()).executePlanCreationRequest(any());
  }

  // ---- Per-project ENFORCE atomic admission (PIPE-35674) ----

  private void enablePerProjectMode() {
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES))
        .thenReturn(true);
    when(pipelineSettingsService.getConcurrencyMode(accountId))
        .thenReturn(io.harness.execution.PlanExecutionConcurrencyMode.PER_PROJECT);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAdmitOrRequeue_PerProjectEnforce_Reserved_AdmitsWithReserve() {
    String planExecutionId = "testPlanExecId";
    enablePerProjectMode();
    when(planConcurrencyGate.tryReserveSlot(accountId, uniqueId))
        .thenReturn(PlanConcurrencyGate.ReserveOutcome.RESERVED);

    PlanCreationQueueRequestHelper.AdmissionDecision decision =
        planCreationQueueRequestHelper.admitOrRequeue(planExecutionId, accountId, uniqueId, PriorityType.NORMAL);

    assertThat(decision.isRequeue()).isFalse();
    assertThat(decision.isSlotReserved()).isTrue();
    verify(planConcurrencyGate).tryReserveSlot(accountId, uniqueId);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAdmitOrRequeue_PerProjectEnforce_Denied_Requeues() {
    String planExecutionId = "testPlanExecId";
    enablePerProjectMode();
    when(planConcurrencyGate.tryReserveSlot(accountId, uniqueId)).thenReturn(PlanConcurrencyGate.ReserveOutcome.DENIED);
    // On DENIED the helper classifies which cap blocked so the drainer can cache the full scope.
    when(planConcurrencyGate.evaluateHeadroom(accountId, uniqueId))
        .thenReturn(PlanConcurrencyGate.HeadroomDecision.PROJECT_FULL);

    PlanCreationQueueRequestHelper.AdmissionDecision decision =
        planCreationQueueRequestHelper.admitOrRequeue(planExecutionId, accountId, uniqueId, PriorityType.NORMAL);

    assertThat(decision.isRequeue()).isTrue();
    assertThat(decision.isSlotReserved()).isFalse();
    assertThat(decision.getRequeueReason()).isEqualTo(PlanCreationQueueRequestHelper.RequeueReason.PROJECT_FULL);
    assertThat(decision.getResolvedParentUniqueId()).isEqualTo(uniqueId);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAdmitOrRequeue_PerProjectEnforce_DeniedByAccountCap_ReportsAccountFull() {
    String planExecutionId = "testPlanExecId";
    enablePerProjectMode();
    when(planConcurrencyGate.tryReserveSlot(accountId, uniqueId)).thenReturn(PlanConcurrencyGate.ReserveOutcome.DENIED);
    when(planConcurrencyGate.evaluateHeadroom(accountId, uniqueId))
        .thenReturn(PlanConcurrencyGate.HeadroomDecision.ACCOUNT_FULL);

    PlanCreationQueueRequestHelper.AdmissionDecision decision =
        planCreationQueueRequestHelper.admitOrRequeue(planExecutionId, accountId, uniqueId, PriorityType.NORMAL);

    assertThat(decision.isRequeue()).isTrue();
    assertThat(decision.getRequeueReason()).isEqualTo(PlanCreationQueueRequestHelper.RequeueReason.ACCOUNT_FULL);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAdmitOrRequeue_PerProjectEnforce_DeniedButIndeterminate_ReportsOther() {
    // Fail-closed Redis blip on the classify read -> INDETERMINATE -> OTHER so the drainer does NOT
    // cache the scope (it may be transient).
    String planExecutionId = "testPlanExecId";
    enablePerProjectMode();
    when(planConcurrencyGate.tryReserveSlot(accountId, uniqueId)).thenReturn(PlanConcurrencyGate.ReserveOutcome.DENIED);
    when(planConcurrencyGate.evaluateHeadroom(accountId, uniqueId))
        .thenReturn(PlanConcurrencyGate.HeadroomDecision.INDETERMINATE);

    PlanCreationQueueRequestHelper.AdmissionDecision decision =
        planCreationQueueRequestHelper.admitOrRequeue(planExecutionId, accountId, uniqueId, PriorityType.NORMAL);

    assertThat(decision.isRequeue()).isTrue();
    assertThat(decision.getRequeueReason()).isEqualTo(PlanCreationQueueRequestHelper.RequeueReason.OTHER);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAdmitOrRequeue_PerProjectShadow_NotReserved_AdmitsWithoutReserve() {
    String planExecutionId = "testPlanExecId";
    enablePerProjectMode();
    when(planConcurrencyGate.tryReserveSlot(accountId, uniqueId))
        .thenReturn(PlanConcurrencyGate.ReserveOutcome.NOT_RESERVED);

    PlanCreationQueueRequestHelper.AdmissionDecision decision =
        planCreationQueueRequestHelper.admitOrRequeue(planExecutionId, accountId, uniqueId, PriorityType.NORMAL);

    assertThat(decision.isRequeue()).isFalse();
    // No reserve owned the +1 — the mutation hook must apply it on the flip, exactly as today.
    assertThat(decision.isSlotReserved()).isFalse();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAdmitOrRequeue_PerProjectEnforce_ResolvesParentUniqueIdFromSetupAbstractions() {
    String planExecutionId = "testPlanExecId";
    enablePerProjectMode();
    // Transport (hsqs) carried no scope — parentUniqueId must be resolved from the execution.
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .setupAbstractions(Map.of(SetupAbstractionKeys.parentUniqueId, uniqueId))
                                      .build();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.setupAbstractions)))
        .thenReturn(planExecution);
    when(planConcurrencyGate.tryReserveSlot(accountId, uniqueId))
        .thenReturn(PlanConcurrencyGate.ReserveOutcome.RESERVED);

    PlanCreationQueueRequestHelper.AdmissionDecision decision =
        planCreationQueueRequestHelper.admitOrRequeue(planExecutionId, accountId, null, PriorityType.NORMAL);

    assertThat(decision.isSlotReserved()).isTrue();
    verify(planConcurrencyGate).tryReserveSlot(accountId, uniqueId);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAdmitOrRequeue_LegacyPath_UntouchedWhenPerProjectDisabled() {
    String planExecutionId = "testPlanExecId";
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES))
        .thenReturn(false);
    when(pipelineSettingsService.shouldQueuePlanExecution(accountId))
        .thenReturn(PlanExecutionSettingResponse.builder().shouldQueue(false).build());

    PlanCreationQueueRequestHelper.AdmissionDecision decision =
        planCreationQueueRequestHelper.admitOrRequeue(planExecutionId, accountId, uniqueId, PriorityType.NORMAL);

    assertThat(decision.isRequeue()).isFalse();
    assertThat(decision.isSlotReserved()).isFalse();
    // Legacy path must never consult the per-project gate.
    verify(planConcurrencyGate, never()).tryReserveSlot(anyString(), anyString());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProcessQueuedPlanCreation_PerProjectEnforce_FlipLostAfterReserve_ReleasesSlot() {
    String planExecutionId = "testPlanExecId";
    enablePerProjectMode();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(QUEUED_PLAN_CREATION).build();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.status)))
        .thenReturn(planExecution);
    when(planConcurrencyGate.tryReserveSlot(accountId, uniqueId))
        .thenReturn(PlanConcurrencyGate.ReserveOutcome.RESERVED);
    // CAS lost: another consumer already advanced this execution, so the flip returns null.
    when(planExecutionService.updateStatus(planExecutionId, Status.STARTING_PLAN_CREATION)).thenReturn(null);

    PlanCreationQueueRequestHelper.ProcessResult result = planCreationQueueRequestHelper.processQueuedPlanCreation(
        planExecutionId, accountId, uniqueId, PriorityType.NORMAL);

    assertThat(result).isEqualTo(PlanCreationQueueRequestHelper.ProcessResult.DROP);
    // The reserved slot must be compensated so the counter does not leak a phantom occupant.
    verify(planConcurrencyGate).releaseReservedSlot(accountId, uniqueId);
    verify(planCreationQueueRequestService, never()).get(planExecutionId);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProcessQueuedPlanCreation_PerProjectEnforce_FlipThrowsAfterReserve_ReleasesSlot() {
    String planExecutionId = "testPlanExecId";
    enablePerProjectMode();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(QUEUED_PLAN_CREATION).build();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.status)))
        .thenReturn(planExecution);
    when(planConcurrencyGate.tryReserveSlot(accountId, uniqueId))
        .thenReturn(PlanConcurrencyGate.ReserveOutcome.RESERVED);
    // Mongo blip: the flip throws instead of losing the CAS. The execution never leaves
    // QUEUED_PLAN_CREATION, so the mutation hook applies no compensating -1 and the reserve would leak.
    when(planExecutionService.updateStatus(planExecutionId, Status.STARTING_PLAN_CREATION))
        .thenThrow(new RuntimeException("mongo blip"));

    PlanCreationQueueRequestHelper.ProcessResult result = planCreationQueueRequestHelper.processQueuedPlanCreation(
        planExecutionId, accountId, uniqueId, PriorityType.NORMAL);

    assertThat(result).isEqualTo(PlanCreationQueueRequestHelper.ProcessResult.DROP);
    // The reserved slot must be released even though the failure never reached an active status.
    verify(planConcurrencyGate).releaseReservedSlot(accountId, uniqueId);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreatePlanExecution_WithProjectLevelPriority() {
    String planExecutionId = "testPlanExecId";
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().planExecutionMetadata(planExecutionMetadata).build();
    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).setExecutionUuid(planExecutionId).build();

    PlanCreationRequest planCreationRequest = PlanCreationRequest.builder()
                                                  .accountId(accountId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .executionMetadata(executionMetadata)
                                                  .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                  .build();

    // Mock orchestrationStartSubject
    doNothing().when(orchestrationStartSubject).fireInform(any(), any());

    // Mock planExecutionMetadataService
    when(planExecutionMetadataService.findByPlanExecutionId(accountId, planExecutionId))
        .thenReturn(Optional.of(planExecutionMetadata));

    // Mock planCreationQueueRequestService
    PlanCreationQueueRequest queueRequest =
        PlanCreationQueueRequest.builder().accountId(accountId).orgId(orgId).projectId(projectId).build();
    when(planCreationQueueRequestService.get(planExecutionId)).thenReturn(queueRequest);

    // Mock feature flag enabled
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY))
        .thenReturn(true);

    // Test case 1: High priority project list with high priority type
    PriorityProjects priorityProject = PriorityProjects.builder().fqn(String.join("/", orgId, projectId)).build();
    PriorityConcurrentExecutionsMetadata priorityMetadata =
        PriorityConcurrentExecutionsMetadata.builder()
            .priorityType(YAMLFieldNameConstants.HIGH_PRIORITY)
            .priorityProjectsList(Collections.singletonList(priorityProject))
            .build();
    when(pipelineSettingsService.getPriorityExecutionPreferences(accountId)).thenReturn(priorityMetadata);

    PlanExecution expectedPlanExecution = PlanExecution.builder()
                                              .uuid(planExecutionId)
                                              .status(Status.QUEUED_PLAN_CREATION)
                                              .priorityType(PriorityType.HIGH)
                                              .build();

    when(transactionHelper.performTransaction(any())).thenReturn(expectedPlanExecution);

    PlanExecution result = planCreationQueueRequestHelper.createPlanExecution(
        planCreationRequest, setupAbstractions, planExecutionMetadataWithContext, executionMetadata, null);
    assertThat(result.getPriorityType()).isEqualTo(PriorityType.HIGH);

    // Test case 2: High priority project list with low priority type
    priorityMetadata = PriorityConcurrentExecutionsMetadata.builder()
                           .priorityType(YAMLFieldNameConstants.LOW_PRIORITY)
                           .priorityProjectsList(Collections.singletonList(priorityProject))
                           .build();
    when(pipelineSettingsService.getPriorityExecutionPreferences(accountId)).thenReturn(priorityMetadata);

    expectedPlanExecution = PlanExecution.builder()
                                .uuid(planExecutionId)
                                .status(Status.QUEUED_PLAN_CREATION)
                                .priorityType(PriorityType.LOW)
                                .build();

    when(transactionHelper.performTransaction(any())).thenReturn(expectedPlanExecution);

    result = planCreationQueueRequestHelper.createPlanExecution(
        planCreationRequest, setupAbstractions, planExecutionMetadataWithContext, executionMetadata, null);
    assertThat(result.getPriorityType()).isEqualTo(PriorityType.LOW);

    // Test case 3: Non-priority project with high priority type
    PriorityProjects differentProject = PriorityProjects.builder().fqn("different/project").build();
    priorityMetadata = PriorityConcurrentExecutionsMetadata.builder()
                           .priorityType(YAMLFieldNameConstants.HIGH_PRIORITY)
                           .priorityProjectsList(Collections.singletonList(differentProject))
                           .build();
    when(pipelineSettingsService.getPriorityExecutionPreferences(accountId)).thenReturn(priorityMetadata);

    expectedPlanExecution = PlanExecution.builder()
                                .uuid(planExecutionId)
                                .status(Status.QUEUED_PLAN_CREATION)
                                .priorityType(PriorityType.LOW)
                                .build();

    when(transactionHelper.performTransaction(any())).thenReturn(expectedPlanExecution);

    result = planCreationQueueRequestHelper.createPlanExecution(
        planCreationRequest, setupAbstractions, planExecutionMetadataWithContext, executionMetadata, null);
    assertThat(result.getPriorityType()).isEqualTo(PriorityType.LOW);

    // Test case 4: Empty priority project list
    priorityMetadata = PriorityConcurrentExecutionsMetadata.builder()
                           .priorityType(YAMLFieldNameConstants.HIGH_PRIORITY)
                           .priorityProjectsList(Collections.emptyList())
                           .build();
    when(pipelineSettingsService.getPriorityExecutionPreferences(accountId)).thenReturn(priorityMetadata);

    expectedPlanExecution = PlanExecution.builder()
                                .uuid(planExecutionId)
                                .status(Status.QUEUED_PLAN_CREATION)
                                .priorityType(PriorityType.NORMAL)
                                .build();

    when(transactionHelper.performTransaction(any())).thenReturn(expectedPlanExecution);

    result = planCreationQueueRequestHelper.createPlanExecution(
        planCreationRequest, setupAbstractions, planExecutionMetadataWithContext, executionMetadata, null);
    assertThat(result.getPriorityType()).isEqualTo(PriorityType.NORMAL);

    // Test case 5: Invalid priority type
    priorityMetadata = PriorityConcurrentExecutionsMetadata.builder()
                           .priorityType("INVALID_TYPE")
                           .priorityProjectsList(Collections.singletonList(priorityProject))
                           .build();
    when(pipelineSettingsService.getPriorityExecutionPreferences(accountId)).thenReturn(priorityMetadata);

    expectedPlanExecution = PlanExecution.builder()
                                .uuid(planExecutionId)
                                .status(Status.QUEUED_PLAN_CREATION)
                                .priorityType(PriorityType.NORMAL)
                                .build();

    when(transactionHelper.performTransaction(any())).thenReturn(expectedPlanExecution);

    result = planCreationQueueRequestHelper.createPlanExecution(
        planCreationRequest, setupAbstractions, planExecutionMetadataWithContext, executionMetadata, null);
    assertThat(result.getPriorityType()).isEqualTo(PriorityType.NORMAL);

    // Test case 6: Feature flag disabled
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY))
        .thenReturn(false);

    expectedPlanExecution = PlanExecution.builder()
                                .uuid(planExecutionId)
                                .status(Status.QUEUED_PLAN_CREATION)
                                .priorityType(PriorityType.NORMAL)
                                .build();

    when(transactionHelper.performTransaction(any())).thenReturn(expectedPlanExecution);

    result = planCreationQueueRequestHelper.createPlanExecution(
        planCreationRequest, setupAbstractions, planExecutionMetadataWithContext, executionMetadata, null);
    assertThat(result.getPriorityType()).isEqualTo(PriorityType.NORMAL);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetParentUniqueIdFromScopeInfo() {
    String uniqueId = "test-unique-id";
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    ScopeInfo scopeInfo = ScopeInfo.builder().uniqueId(uniqueId).build();

    // Execute test
    String result = planCreationQueueRequestHelper.getParentUniqueId(setupAbstractions, scopeInfo);

    // Verify results
    assertThat(result).isEqualTo(uniqueId);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetParentUniqueIdFromScopeResolutionHelper() {
    String uniqueId = "test-unique-id";
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    ScopeInfo resolvedScopeInfo = ScopeInfo.builder().uniqueId(uniqueId).build();
    when(scopeResolutionHelper.getScopeInfoOptional(accountId, orgId, projectId))
        .thenReturn(Optional.of(resolvedScopeInfo));

    // Execute test with null scopeInfo
    String result = planCreationQueueRequestHelper.getParentUniqueId(setupAbstractions, null);

    // Verify results
    assertThat(result).isEqualTo(uniqueId);
    verify(scopeResolutionHelper).getScopeInfoOptional(accountId, orgId, projectId);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetParentUniqueIdWhenBothSourcesEmpty() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    when(scopeResolutionHelper.getScopeInfoOptional(accountId, orgId, projectId)).thenReturn(Optional.empty());

    // Execute test with null scopeInfo
    String result = planCreationQueueRequestHelper.getParentUniqueId(setupAbstractions, null);

    // Verify results
    assertThat(result).isNull();
    verify(scopeResolutionHelper).getScopeInfoOptional(accountId, orgId, projectId);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSetupAbstractionsWhenScopeInfoIsNull() {
    when(scopeResolutionHelper.getScopeInfoOptional(accountId, orgId, projectId))
        .thenReturn(Optional.of(ScopeInfo.builder()
                                    .accountIdentifier(accountId)
                                    .orgIdentifier(orgId)
                                    .projectIdentifier(projectId)
                                    .uniqueId(uniqueId)
                                    .build()));
    Map<String, String> setupAbstractions =
        planCreationQueueRequestHelper.setupAbstractions(accountId, orgId, projectId, null);
    assertEquals(setupAbstractions.get("parentUniqueId"), uniqueId);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSetupAbstractionsWhenScopeInfoIsNotNull() {
    planCreationQueueRequestHelper.setupAbstractions(accountId, orgId, projectId,
        ScopeInfo.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .uniqueId(uniqueId)
            .build());
    verify(scopeResolutionHelper, times(0)).getScopeInfoOptional(accountId, orgId, projectId);
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testSetupAbstractionsAlwaysPopulatesOrgIdentifierEvenWhenScopeResolutionFails() {
    when(scopeResolutionHelper.getScopeInfoOptional(accountId, orgId, projectId)).thenReturn(Optional.empty());
    Map<String, String> setupAbstractions =
        planCreationQueueRequestHelper.setupAbstractions(accountId, orgId, projectId, null);
    assertEquals(orgId, setupAbstractions.get(SetupAbstractionKeys.orgIdentifier));
    assertEquals(accountId, setupAbstractions.get(SetupAbstractionKeys.accountId));
    assertEquals(projectId, setupAbstractions.get(SetupAbstractionKeys.projectIdentifier));
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetExpandedJson_WhenPipelineEntityIsEmpty_ReturnsNull() throws Exception {
    // Setup
    PlanCreationQueueRequest queueRequest = PlanCreationQueueRequest.builder()
                                                .accountId(accountId)
                                                .orgId(orgId)
                                                .projectId(projectId)
                                                .pipelineYamlWithTemplateRef("pipeline-yaml")
                                                .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build();

    // Mock: pipelineEntity is empty
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.empty());

    // Execute using reflection to access private method
    java.lang.reflect.Method method = PlanCreationQueueRequestHelper.class.getDeclaredMethod(
        "getExpandedJson", PlanCreationQueueRequest.class, ExecutionMetadata.class);
    method.setAccessible(true);
    String result = (String) method.invoke(planCreationQueueRequestHelper, queueRequest, executionMetadata);

    // Verify
    assertThat(result).isNull();
    verify(pmsPipelineService, times(1))
        .getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean());
    verify(pipelineGovernanceService, never()).fetchExpandedPipelineJSONFromYaml(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetExpandedJson_WhenPipelineYamlWithTemplateRefIsNull_ReturnsNull() throws Exception {
    // Setup
    PlanCreationQueueRequest queueRequest = PlanCreationQueueRequest.builder()
                                                .accountId(accountId)
                                                .orgId(orgId)
                                                .projectId(projectId)
                                                .pipelineYamlWithTemplateRef(null)
                                                .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build();

    // Mock: pipelineEntity exists but pipelineYamlWithTemplateRef is null
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    // Execute using reflection to access private method
    java.lang.reflect.Method method = PlanCreationQueueRequestHelper.class.getDeclaredMethod(
        "getExpandedJson", PlanCreationQueueRequest.class, ExecutionMetadata.class);
    method.setAccessible(true);
    String result = (String) method.invoke(planCreationQueueRequestHelper, queueRequest, executionMetadata);

    // Verify
    assertThat(result).isNull();
    verify(pmsPipelineService, times(1))
        .getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean());
    verify(pipelineGovernanceService, never()).fetchExpandedPipelineJSONFromYaml(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetExpandedJson_WhenPipelineYamlWithTemplateRefIsEmpty_ReturnsNull() throws Exception {
    // Setup
    PlanCreationQueueRequest queueRequest = PlanCreationQueueRequest.builder()
                                                .accountId(accountId)
                                                .orgId(orgId)
                                                .projectId(projectId)
                                                .pipelineYamlWithTemplateRef("")
                                                .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build();

    // Mock: pipelineEntity exists but pipelineYamlWithTemplateRef is empty
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));

    // Execute using reflection to access private method
    java.lang.reflect.Method method = PlanCreationQueueRequestHelper.class.getDeclaredMethod(
        "getExpandedJson", PlanCreationQueueRequest.class, ExecutionMetadata.class);
    method.setAccessible(true);
    String result = (String) method.invoke(planCreationQueueRequestHelper, queueRequest, executionMetadata);

    // Verify
    assertThat(result).isNull();
    verify(pmsPipelineService, times(1))
        .getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean());
    verify(pipelineGovernanceService, never()).fetchExpandedPipelineJSONFromYaml(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetExpandedJson_WhenBothPresent_ReturnsExpandedJson() throws Exception {
    // Setup
    String pipelineYamlWithTemplateRef = "pipeline:\n  name: test";
    String expandedJson = "{\"pipeline\":{\"name\":\"test\"}}";
    PlanCreationQueueRequest queueRequest = PlanCreationQueueRequest.builder()
                                                .accountId(accountId)
                                                .orgId(orgId)
                                                .projectId(projectId)
                                                .pipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef)
                                                .isParentIdQueryingEnabled(false)
                                                .branch("main")
                                                .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build();

    // Mock: pipelineEntity exists and pipelineYamlWithTemplateRef is present
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));
    when(pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
             eq(pipelineEntity), eq(pipelineYamlWithTemplateRef), eq("main"), anyString()))
        .thenReturn(expandedJson);

    // Execute using reflection to access private method
    java.lang.reflect.Method method = PlanCreationQueueRequestHelper.class.getDeclaredMethod(
        "getExpandedJson", PlanCreationQueueRequest.class, ExecutionMetadata.class);
    method.setAccessible(true);
    String result = (String) method.invoke(planCreationQueueRequestHelper, queueRequest, executionMetadata);

    // Verify
    assertThat(result).isEqualTo(expandedJson);
    verify(pmsPipelineService, times(1))
        .getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean());
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            eq(pipelineEntity), eq(pipelineYamlWithTemplateRef), eq("main"), anyString());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetExpandedJson_WithParentIdQueryingEnabled_ReturnsExpandedJson() throws Exception {
    // Setup
    String pipelineYamlWithTemplateRef = "pipeline:\n  name: test";
    String expandedJson = "{\"pipeline\":{\"name\":\"test\"}}";
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountId)
                              .orgIdentifier(orgId)
                              .projectIdentifier(projectId)
                              .uniqueId(uniqueId)
                              .build();
    PlanCreationQueueRequest queueRequest = PlanCreationQueueRequest.builder()
                                                .accountId(accountId)
                                                .orgId(orgId)
                                                .projectId(projectId)
                                                .pipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef)
                                                .isParentIdQueryingEnabled(true)
                                                .scopeInfo(scopeInfo)
                                                .branch("main")
                                                .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build();

    // Mock: pipelineEntity exists and pipelineYamlWithTemplateRef is present
    when(pmsPipelineService.getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
             anyBoolean(), anyBoolean(), any(), anyBoolean()))
        .thenReturn(Optional.of(pipelineEntity));
    when(pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
             eq(pipelineEntity), eq(scopeInfo), eq(pipelineYamlWithTemplateRef), eq("main"), anyString()))
        .thenReturn(expandedJson);

    // Execute using reflection to access private method
    java.lang.reflect.Method method = PlanCreationQueueRequestHelper.class.getDeclaredMethod(
        "getExpandedJson", PlanCreationQueueRequest.class, ExecutionMetadata.class);
    method.setAccessible(true);
    String result = (String) method.invoke(planCreationQueueRequestHelper, queueRequest, executionMetadata);

    // Verify
    assertThat(result).isEqualTo(expandedJson);
    verify(pmsPipelineService, times(1))
        .getPipeline(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(),
            anyBoolean(), any(), anyBoolean());
    verify(pipelineGovernanceService, times(1))
        .fetchExpandedPipelineJSONFromYaml(
            eq(pipelineEntity), eq(scopeInfo), eq(pipelineYamlWithTemplateRef), eq("main"), anyString());
  }

  private void buildExecutionArgsMocks() throws IOException {
    doReturn(executionPrincipalInfo).when(principalInfoHelper).getPrincipalInfoFromSecurityContext();
    doReturn(394).when(pipelineMetadataService).incrementRunSequence(any(), any(), any(), any(), any(), anyBoolean());
    doReturn(Optional.of(prevExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(accountId, originalExecutionId);
    when(pmsExecutionSummaryService.fetchRootRetryExecutionId(accountId, originalExecutionId))
        .thenReturn(originalExecutionId);
    String processedYamlForRetry = YamlUtils.injectUuid(mergedPipelineYaml);
    when(retryExecutionHelper.retryProcessedYaml(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
        .thenReturn(processedYamlForRetry);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testBranchSequenceNotCalledWhenFeatureFlagDisabled() {
    // When CI_ENABLE_BRANCH_SEQUENCE_ID is disabled, branchSequenceService should not be called
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(eq(accountId), eq(FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID));

    // Verify that the branchSequenceService methods are never called
    verify(branchSequenceService, never())
        .incrementBranchSequenceFromTriggerPayload(any(), any(), any(), any(), any(), any());
    verify(branchSequenceService, never())
        .incrementBranchSequenceFromProcessedYaml(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testBranchSequenceCalledWhenFeatureFlagEnabled() {
    // When CI_ENABLE_BRANCH_SEQUENCE_ID is enabled, the feature flag check should pass
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(eq(accountId), eq(FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID));

    // Verifying the feature flag helper is properly configured
    assertThat(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID)).isTrue();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testIncrementBranchSequenceWithTriggerPayload() throws IOException {
    on(planCreationQueueRequestHelper).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = "testPlanExecId";
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    // Create trigger payload with push event
    io.harness.pms.contracts.triggers.TriggerPayload triggerPayload =
        io.harness.pms.contracts.triggers.TriggerPayload.newBuilder().build();

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).triggerPayload(triggerPayload).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .pipelineYamlWithTemplateRef("pipelineYamlWithTemplateRef")
            .processedYaml(pipelineYaml)
            .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setPipelineIdentifier(pipelineId)
                                              .setExecutionUuid(planExecutionId)
                                              .setRunSequence(394)
                                              .build();
    PlanCreationRequest planCreationRequest = PlanCreationRequest.builder()
                                                  .accountId(accountId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .executionMetadata(executionMetadata)
                                                  .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                  .build();
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .setupAbstractions(setupAbstractions)
                                      .status(QUEUED_PLAN_CREATION)
                                      .metadata(executionMetadata)
                                      .priorityType(PriorityType.NORMAL)
                                      .build();

    // Enable the feature flag
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(eq(accountId), eq(FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID));
    doReturn(395).when(pipelineMetadataService).incrementRunSequence(any(), any(), any(), any(), any(), anyBoolean());
    doReturn(true).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(accountId);
    doReturn(planExecution).when(transactionHelper).performTransaction(any());
    doReturn(EnqueueResponse.builder().itemId("itemId").build()).when(hsqsClientService).enqueue(any());

    // Mock branchSequenceService - no result from trigger payload
    doReturn(null)
        .when(branchSequenceService)
        .incrementBranchSequenceFromTriggerPayload(any(), any(), any(), any(), any(), any());

    PlanExecution result =
        planCreationQueueRequestHelper.savePlanExecutionAndQueuePlanExecutionRequest(planCreationRequest);
    assertThat(result).isEqualTo(planExecution);
    verify(pmsFeatureFlagHelper, times(1)).isEnabled(eq(accountId), eq(FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testBranchSequenceExceptionDoesNotFailExecution() throws IOException {
    on(planCreationQueueRequestHelper).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = "testPlanExecId";
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .pipelineYamlWithTemplateRef("pipelineYamlWithTemplateRef")
            .processedYaml(pipelineYaml)
            .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setPipelineIdentifier(pipelineId)
                                              .setExecutionUuid(planExecutionId)
                                              .setRunSequence(394)
                                              .build();
    PlanCreationRequest planCreationRequest = PlanCreationRequest.builder()
                                                  .accountId(accountId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .executionMetadata(executionMetadata)
                                                  .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                  .build();
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .setupAbstractions(setupAbstractions)
                                      .status(QUEUED_PLAN_CREATION)
                                      .metadata(executionMetadata)
                                      .priorityType(PriorityType.NORMAL)
                                      .build();

    // Enable the feature flag
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(eq(accountId), eq(FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID));
    doReturn(395).when(pipelineMetadataService).incrementRunSequence(any(), any(), any(), any(), any(), anyBoolean());
    doReturn(true).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(accountId);
    doReturn(planExecution).when(transactionHelper).performTransaction(any());
    doReturn(EnqueueResponse.builder().itemId("itemId").build()).when(hsqsClientService).enqueue(any());

    // Make branchSequenceService throw an exception - this should NOT fail the execution
    doThrow(new RuntimeException("Branch sequence error"))
        .when(branchSequenceService)
        .incrementBranchSequenceFromTriggerPayload(any(), any(), any(), any(), any(), any());

    // Execution should still succeed even though branch sequence failed
    PlanExecution result =
        planCreationQueueRequestHelper.savePlanExecutionAndQueuePlanExecutionRequest(planCreationRequest);
    assertThat(result).isEqualTo(planExecution);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testBranchSequenceWithNullPlanExecutionMetadataWithContext() throws IOException {
    on(planCreationQueueRequestHelper).set("orchestrationStartSubject", orchestrationStartSubject);
    String planExecutionId = "testPlanExecId";
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build();
    // Create PlanExecutionMetadataWithContext with null processedYaml and no trigger payload
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .planExecutionMetadata(planExecutionMetadata)
            .pipelineYamlWithTemplateRef("pipelineYamlWithTemplateRef")
            .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setPipelineIdentifier(pipelineId)
                                              .setExecutionUuid(planExecutionId)
                                              .setRunSequence(394)
                                              .build();
    PlanCreationRequest planCreationRequest = PlanCreationRequest.builder()
                                                  .accountId(accountId)
                                                  .orgIdentifier(orgId)
                                                  .projectIdentifier(projectId)
                                                  .executionMetadata(executionMetadata)
                                                  .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
                                                  .build();
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(planExecutionId)
                                      .setupAbstractions(setupAbstractions)
                                      .status(QUEUED_PLAN_CREATION)
                                      .metadata(executionMetadata)
                                      .priorityType(PriorityType.NORMAL)
                                      .build();

    // Enable the feature flag
    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(eq(accountId), eq(FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID));
    doReturn(395).when(pipelineMetadataService).incrementRunSequence(any(), any(), any(), any(), any(), anyBoolean());
    doReturn(true).when(pipelineSettingsService).isQueuedExecutionsWithinLimit(accountId);
    doReturn(planExecution).when(transactionHelper).performTransaction(any());
    doReturn(EnqueueResponse.builder().itemId("itemId").build()).when(hsqsClientService).enqueue(any());

    PlanExecution result =
        planCreationQueueRequestHelper.savePlanExecutionAndQueuePlanExecutionRequest(planCreationRequest);
    assertThat(result).isEqualTo(planExecution);

    // Verify branchSequenceService methods were called but returned null (no branch sequence to set)
    verify(branchSequenceService, never())
        .incrementBranchSequenceFromTriggerPayload(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProcessQueuedPlanCreation_ReturnsProcessed() {
    String planExecutionId = "testPlanExecId";
    PlanCreationQueueRequest queueRequest = PlanCreationQueueRequest.builder()
                                                .accountId(accountId)
                                                .orgId(orgId)
                                                .projectId(projectId)
                                                .pipelineYamlWithTemplateRef("pipeline:\n  name: test")
                                                .build();
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build();
    PlanExecutionMetadata metadata = PlanExecutionMetadata.builder().build();
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(QUEUED_PLAN_CREATION).build();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.status)))
        .thenReturn(planExecution);
    when(pipelineSettingsService.shouldQueuePlanExecution(accountId))
        .thenReturn(PlanExecutionSettingResponse.builder().shouldQueue(false).build());
    PlanExecution updatedPlanExecution =
        PlanExecution.builder()
            .uuid(planExecutionId)
            .status(Status.STARTING_PLAN_CREATION)
            .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineId).build())
            .ambiance(Ambiance.newBuilder().setMetadata(executionMetadata).build())
            .build();
    when(planExecutionService.updateStatus(planExecutionId, Status.STARTING_PLAN_CREATION))
        .thenReturn(updatedPlanExecution);
    when(planCreationQueueRequestService.get(planExecutionId)).thenReturn(queueRequest);
    when(planExecutionMetadataService.findByPlanExecutionId(accountId, planExecutionId))
        .thenReturn(Optional.of(metadata));
    when(pmsPipelineService.getPipeline(
             accountId, orgId, projectId, pipelineId, false, false, false, false, null, false))
        .thenReturn(Optional.of(PipelineEntity.builder().build()));
    doReturn("expandedJson")
        .when(pipelineGovernanceService)
        .fetchExpandedPipelineJSONFromYaml(any(), any(), any(), any());
    doNothing().when(planCreationQueueRequestService).updateTTL(planExecutionId);
    doReturn(planExecution).when(planCreationQueueRequestHelper).executePlanCreationRequest(any());
    doReturn(new PmsGitSyncBranchContextGuard(GitSyncBranchContext.builder().build(), true))
        .when(pmsGitSyncHelper)
        .createGitSyncBranchContextGuard(any(), anyBoolean());
    PlanCreationQueueRequestHelper.ProcessResult result =
        planCreationQueueRequestHelper.processQueuedPlanCreation(planExecutionId, accountId, null, PriorityType.NORMAL);
    assertThat(result).isEqualTo(PlanCreationQueueRequestHelper.ProcessResult.PROCESSED);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProcessQueuedPlanCreation_ReturnsDropWhenAlreadyAborted() {
    String planExecutionId = "testPlanExecId";
    PlanExecution abortedExecution = PlanExecution.builder().uuid(planExecutionId).status(Status.ABORTED).build();
    when(planExecutionService.get(planExecutionId)).thenReturn(abortedExecution);
    PlanCreationQueueRequestHelper.ProcessResult result =
        planCreationQueueRequestHelper.processQueuedPlanCreation(planExecutionId, accountId, null, PriorityType.NORMAL);
    assertThat(result).isEqualTo(PlanCreationQueueRequestHelper.ProcessResult.DROP);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProcessQueuedPlanCreation_ReturnsRequeueWhenMaxConcurrencyReached() {
    String planExecutionId = "testPlanExecId";
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(QUEUED_PLAN_CREATION).build();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.status)))
        .thenReturn(planExecution);
    when(pipelineSettingsService.shouldQueuePlanExecution(accountId))
        .thenReturn(PlanExecutionSettingResponse.builder().shouldQueue(true).build());
    PlanCreationQueueRequestHelper.ProcessResult result =
        planCreationQueueRequestHelper.processQueuedPlanCreation(planExecutionId, accountId, null, PriorityType.NORMAL);
    assertThat(result).isEqualTo(PlanCreationQueueRequestHelper.ProcessResult.REQUEUE);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProcessQueuedPlanCreation_ReturnsDropWhenUpdateStatusFails() {
    String planExecutionId = "testPlanExecId";
    PlanExecution planExecution = PlanExecution.builder().uuid(planExecutionId).status(QUEUED_PLAN_CREATION).build();
    when(planExecutionService.getWithFieldsIncluded(planExecutionId, Set.of(PlanExecutionKeys.status)))
        .thenReturn(planExecution);
    when(pipelineSettingsService.shouldQueuePlanExecution(accountId))
        .thenReturn(PlanExecutionSettingResponse.builder().shouldQueue(false).build());
    when(planExecutionService.updateStatus(planExecutionId, Status.STARTING_PLAN_CREATION)).thenReturn(null);
    PlanCreationQueueRequestHelper.ProcessResult result =
        planCreationQueueRequestHelper.processQueuedPlanCreation(planExecutionId, accountId, null, PriorityType.NORMAL);
    assertThat(result).isEqualTo(PlanCreationQueueRequestHelper.ProcessResult.DROP);
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testMarkPlanExecutionFailed_PreservesOpaGovernanceMetadata() {
    String planExecutionId = "opa-blocked-exec";
    GovernanceMetadata gm = GovernanceMetadata.newBuilder().setId("eval-1").setDeny(true).setStatus("error").build();
    OpaOnSaveStatusDTO opaStatus = OpaOnSaveStatusDTO.builder()
                                       .status(OpaGitxStatus.ERROR)
                                       .repoURL("https://github.com/org/repo")
                                       .filePath(".harness/services/svc.yaml")
                                       .evaluatedAtCommitId("commit1")
                                       .lastValidCommitId("abc123")
                                       .evaluatedAt(1700000000000L)
                                       .message("bad services are not allowed")
                                       .governanceMetadata(gm)
                                       .build();
    String cleanMessage =
        "Execution blocked by governance policies for service [good_service]. bad services are not allowed";
    PolicyEvaluationFailureException policyFailure = new PolicyEvaluationFailureException(cleanMessage, opaStatus);

    planCreationQueueRequestHelper.markPlanExecutionFailed(policyFailure, planExecutionId);

    ArgumentCaptor<Consumer> opsCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(planExecutionService).updateStatus(eq(planExecutionId), eq(Status.ERRORED), opsCaptor.capture());

    Update planExecutionUpdate = new Update();
    opsCaptor.getValue().accept(planExecutionUpdate);
    Document setDoc = (Document) planExecutionUpdate.getUpdateObject().get("$set");
    FailureInfo failureInfo = (FailureInfo) setDoc.get("failureInfo");
    assertThat(failureInfo.getErrorMessage()).isEqualTo(cleanMessage);
    assertThat(failureInfo.getFailureData(0).getCode()).isEqualTo(String.valueOf(ErrorCode.POLICY_EVALUATION_FAILURE));
    assertThat(failureInfo.getFailureData(0).getFailureTypeInfos(0).getFailureType())
        .isEqualTo(FailureType.POLICY_EVALUATION_FAILURE);
    assertThat(setDoc.get("governanceMetadata")).isEqualTo(gm);

    ArgumentCaptor<Update> summaryUpdateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(pmsExecutionSummaryService).update(eq(planExecutionId), summaryUpdateCaptor.capture());
    Document summarySet = (Document) summaryUpdateCaptor.getValue().getUpdateObject().get("$set");
    assertThat(summarySet.get("governanceMetadata")).isEqualTo(gm);
    OpaOnSaveStatusDTO persistedStatus = (OpaOnSaveStatusDTO) summarySet.get("opaOnSaveStatus");
    assertThat(persistedStatus).isSameAs(opaStatus);
    assertThat(persistedStatus.getGovernanceMetadata()).isEqualTo(gm);
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testMarkPlanExecutionFailed_GenericExceptionKeepsPlanCreationError() {
    String planExecutionId = "generic-fail-exec";
    Exception generic = new InvalidRequestException("YAML is invalid");

    planCreationQueueRequestHelper.markPlanExecutionFailed(generic, planExecutionId);

    ArgumentCaptor<Consumer> opsCaptor = ArgumentCaptor.forClass(Consumer.class);
    verify(planExecutionService).updateStatus(eq(planExecutionId), eq(Status.ERRORED), opsCaptor.capture());
    Update planExecutionUpdate = new Update();
    opsCaptor.getValue().accept(planExecutionUpdate);
    Document setDoc = (Document) planExecutionUpdate.getUpdateObject().get("$set");
    FailureInfo failureInfo = (FailureInfo) setDoc.get("failureInfo");
    assertThat(failureInfo.getErrorMessage()).isEqualTo("YAML is invalid");
    assertThat(failureInfo.getFailureData(0).getCode()).isEqualTo(String.valueOf(ErrorCode.PLAN_CREATION_ERROR));
    assertThat(failureInfo.getFailureData(0).getFailureTypeInfos(0).getFailureType())
        .isEqualTo(FailureType.UNKNOWN_FAILURE);
    assertThat(setDoc.containsKey("governanceMetadata")).isFalse();
    verify(pmsExecutionSummaryService, never()).update(eq(planExecutionId), any(Update.class));
  }
}
