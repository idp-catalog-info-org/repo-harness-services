/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.gitstatus;

import static io.harness.rule.OwnerRule.ANKUR_PATEL;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.MOHD_FAIZ;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.gitops.gitstatus.GitOpsGitStatusHelper;
import io.harness.cdng.gitops.outcomes.GitOpsPRStatusInfo;
import io.harness.cdng.gitops.outcomes.GitOpsStatusCheckOutput;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.execution.intfc.GitBuildStatusUtility;
import io.harness.ci.execution.execution.intfc.GitStatusNotificationParams;
import io.harness.common.NGExpressionUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.NGAccess;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.SendGitStatusConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.PmsFeatureFlagService;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class GitStatusUpdateNotifierTest extends CategoryTest {
  @Mock private PlanExecutionService planExecutionService;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private GitBuildStatusUtility gitBuildStatusUtility;

  @InjectMocks private GitStatusUpdateNotifierImpl gitStatusUpdateNotifier;
  @Mock private ServiceHttpClientConfig harnessCodeClientConfig;

  @Mock private NodeUpdateInfo nodeUpdateInfo;
  @Mock private NodeExecution nodeExecution;
  @Mock private PlanExecution planExecution;
  @Mock private PullRequestHook prHook;
  @Mock PmsFeatureFlagService featureFlagService;
  @Mock private PlanService planService;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PMSExecutionService pmsExecutionService;
  @Mock private GitOpsGitStatusHelper gitOpsGitStatusHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  private static final String ACCOUNT_ID = "accountId";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String NODE_ID = "nodeId";
  private static final String PLAN_ID = "planId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private AutoCloseable closeable;

  @Before
  public void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
  }
  @After
  public void releaseMocks() throws Exception {
    closeable.close();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void nodeStatusUpdateDoesNotSendStatusWhenPlanExecutionIsNull() {
    when(featureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    when(nodeUpdateInfo.getNodeExecution()).thenReturn(nodeExecution);
    when(nodeExecution.getPlanExecutionId()).thenReturn("planExecutionId");
    when(planExecutionService.get("planExecutionId")).thenReturn(null);

    gitStatusUpdateNotifier.onNodeStatusUpdate(
        nodeUpdateInfo.getNodeExecution(), nodeExecutionService.getAmbiance(nodeUpdateInfo.getNodeExecution()));

    verify(gitBuildStatusUtility, never()).sendStatusToGit(any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void nodeStatusUpdateDoesNotSendStatusWhenTriggerPayloadIsNull() {
    when(featureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    when(nodeUpdateInfo.getNodeExecution()).thenReturn(nodeExecution);
    when(nodeExecution.getPlanExecutionId()).thenReturn("planExecutionId");
    when(planExecutionService.get("planExecutionId")).thenReturn(planExecution);
    when(planExecution.getTriggerPayload()).thenReturn(null);

    gitStatusUpdateNotifier.onNodeStatusUpdate(
        nodeUpdateInfo.getNodeExecution(), nodeExecutionService.getAmbiance(nodeUpdateInfo.getNodeExecution()));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void nodeStatusUpdateSendsStatusWhenConditionsAreMet() {
    when(featureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);

    // NodeExecution and PlanExecution
    when(nodeUpdateInfo.getNodeExecution()).thenReturn(nodeExecution);
    when(nodeExecution.getPlanExecutionId()).thenReturn("planExecutionId");
    when(planExecutionService.get("planExecutionId")).thenReturn(planExecution);
    when(nodeExecution.getCurrentLevel())
        .thenReturn(Level.newBuilder()
                        .setStepType(StepType.newBuilder().setType("stage").setStepCategoryValue(1).build())
                        .build());

    // Pull Request Hook and related details
    PullRequestHook prHook = mock(PullRequestHook.class);
    io.harness.product.ci.scm.proto.PullRequest pr = mock(io.harness.product.ci.scm.proto.PullRequest.class);
    io.harness.product.ci.scm.proto.Repository repo = mock(io.harness.product.ci.scm.proto.Repository.class);

    when(pr.getSha()).thenReturn("sha123");
    when(pr.getTitle()).thenReturn("Test PR");
    when(pr.getTarget()).thenReturn("http://target");
    when(pr.getNumber()).thenReturn(123L);

    when(repo.getName()).thenReturn("test-repo");
    when(prHook.getPr()).thenReturn(pr);
    when(prHook.getRepo()).thenReturn(repo);

    TriggerPayload triggerPayload = TriggerPayload.newBuilder()
                                        .setParsedPayload(ParsedPayload.newBuilder().setPr(prHook).build())
                                        .setConnectorRef("connector-ref")
                                        .build();
    when(planExecution.getTriggerPayload()).thenReturn(triggerPayload);

    // NodeExecution fields
    when(nodeExecution.getAccountId()).thenReturn("acc");
    when(nodeExecution.getNodeId()).thenReturn("nid");
    when(nodeExecution.getPipelineIdentifier()).thenReturn("pid");
    when(nodeExecution.getIdentifier()).thenReturn("stageId");
    when(nodeExecution.getStatus()).thenReturn(io.harness.pms.contracts.execution.Status.SUCCEEDED);
    when(nodeExecution.getStageExecutionId()).thenReturn("stageExecId");
    when(nodeExecution.getStartTs()).thenReturn(123456789L);

    Ambiance ambiance = Ambiance.newBuilder().build();
    when(planExecution.getAmbiance()).thenReturn(ambiance);
    when(planExecution.getPlanId()).thenReturn("planId");
    when(planExecutionMetadataService.findByPlanExecutionId(any(), any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder()
                                    .triggerPayload(TriggerPayload.newBuilder()
                                                        .setParsedPayload(ParsedPayload.newBuilder()
                                                                              .setPr(PullRequestHook.newBuilder()
                                                                                         .setPr(PullRequest.newBuilder()
                                                                                                    .setTitle("PR")
                                                                                                    .setTarget("PR")
                                                                                                    .setNumber(1200)
                                                                                                    .build())
                                                                                         .build())
                                                                              .build())
                                                        .build())
                                    .build()));

    // Git status and connector mocks
    when(gitBuildStatusUtility.shouldSendStatus(any())).thenReturn(true);
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(ConnectorDetails.builder().build());
    when(planService.fetchNode(nodeExecution.getPlanId(), nodeExecution.getNodeId()))
        .thenReturn(
            PlanNode.builder().sendGitStatus(SendGitStatusConfig.newBuilder().setEnabled(true).build()).build());
    try (MockedStatic<AmbianceUtils> mockedStatic = mockStatic(AmbianceUtils.class)) {
      mockedStatic.when(() -> AmbianceUtils.getNgAccess(any())).thenReturn(mock(NGAccess.class));
      gitStatusUpdateNotifier.onNodeStatusUpdate(
          nodeUpdateInfo.getNodeExecution(), nodeExecutionService.getAmbiance(nodeUpdateInfo.getNodeExecution()));
      verify(gitBuildStatusUtility, times(1)).sendStatusToGit(any());
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void nodeStatusUpdateSendsStatusFoBuildContext() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    PlanExecution planExecution = mock(PlanExecution.class);
    Ambiance ambiance = Ambiance.newBuilder().build();
    TriggerPayload triggerPayload = mock(TriggerPayload.class);
    ParsedPayload parsedPayload = mock(ParsedPayload.class);
    PullRequest pr = PullRequest.newBuilder().setSha("sha").setTitle("title").setTarget("url").setNumber(100).build();
    Repository repo = Repository.newBuilder().setName("repo").build();
    PullRequestHook prHook = PullRequestHook.newBuilder().setPr(pr).setRepo(repo).build();
    PlanExecutionMetadata planExecutionMetadata = mock(PlanExecutionMetadata.class);

    // Mock in correct order to avoid NPE
    when(planExecution.getTriggerPayload()).thenReturn(triggerPayload);
    when(planExecutionMetadata.getTriggerPayload()).thenReturn(triggerPayload);
    when(triggerPayload.getParsedPayload()).thenReturn(parsedPayload);
    when(parsedPayload.getPr()).thenReturn(prHook);

    when(nodeExecution.getAccountId()).thenReturn("account-id");
    when(nodeExecution.getPipelineIdentifier()).thenReturn("pipeline-id");
    when(nodeExecution.getNodeId()).thenReturn("node-id");
    when(nodeExecution.getStageExecutionId()).thenReturn("stage-exec-id");
    when(nodeExecution.getPlanExecutionId()).thenReturn("plan-exec-id");
    when(nodeExecution.getStatus()).thenReturn(Status.SUCCEEDED);
    when(nodeExecution.getStartTs()).thenReturn(123456789L);

    when(planExecution.getAmbiance()).thenReturn(ambiance);

    NGAccess ngAccess = mock(NGAccess.class);
    ConnectorDetails connectorDetails = mock(ConnectorDetails.class);
    try (MockedStatic<AmbianceUtils> mockedStatic = mockStatic(AmbianceUtils.class)) {
      mockedStatic.when(() -> AmbianceUtils.getNgAccess(ambiance)).thenReturn(ngAccess);

      when(connectorUtils.getConnectorDetails(ngAccess, "git-connector-ref")).thenReturn(connectorDetails);

      GitStatusNotificationParams params =
          gitStatusUpdateNotifier.buildContext(nodeExecution, planExecutionMetadata, "stage-id");

      assertThat("account-id").isEqualTo(params.getAccountId());
      assertThat("pipeline-id").isEqualTo(params.getPipelineIdentifier());
      assertThat("repo").isEqualTo(params.getRepoName());
      assertThat("sha").isEqualTo(params.getSha());
      assertThat("title").isEqualTo(params.getTitle());
      assertThat("url").isEqualTo(params.getTargetUrl());
      assertThat("100").isEqualTo(params.getPrNumber());
      assertThat("stage-exec-id").isEqualTo(params.getStageExecutionId());
      assertThat("plan-exec-id").isEqualTo(params.getPlanExecutionId());
      assertThat(123456789L).isEqualTo(params.getStartTs());
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetStageIdentifier_WhenSendGitStatusNameIsEmpty() {
    NodeExecution nodeExecution = NodeExecution.builder().identifier("testStage").build();
    SendGitStatusConfig sendGitStatus = SendGitStatusConfig.newBuilder().build();
    PlanNode planNode = PlanNode.builder().sendGitStatus(sendGitStatus).build();
    String result = gitStatusUpdateNotifier.getStageIdentifier(planNode, nodeExecution);
    assertThat(result).isEqualTo("testStage");
    verify(pmsEngineExpressionService, never()).resolve(any(), any(), any());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetStageIdentifier_WhenNameIsNotExpression() {
    NodeExecution nodeExecution =
        NodeExecution.builder().identifier("testStage").ambiance(Ambiance.newBuilder().build()).build();
    SendGitStatusConfig sendGitStatus = SendGitStatusConfig.newBuilder().setName("static-name").build();
    PlanNode planNode = PlanNode.builder().sendGitStatus(sendGitStatus).build();

    try (MockedStatic<NGExpressionUtils> ngUtils = mockStatic(NGExpressionUtils.class)) {
      ngUtils
          .when(() -> NGExpressionUtils.containsPattern(NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, "static-name"))
          .thenReturn(false);

      String result = gitStatusUpdateNotifier.getStageIdentifier(planNode, nodeExecution);
      assertThat(result).isEqualTo("static-name");
      verify(pmsEngineExpressionService, never()).resolve(any(), any(), any());
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetStageIdentifier_WhenSendGitStatusNameIsResolved() {
    NodeExecution nodeExecution =
        NodeExecution.builder().identifier("testStage").ambiance(Ambiance.newBuilder().build()).build();
    SendGitStatusConfig sendGitStatus =
        SendGitStatusConfig.newBuilder().setName("<+pipeline.stages.stage1.name>").build();
    PlanNode planNode = PlanNode.builder().sendGitStatus(sendGitStatus).build();
    try (MockedStatic<NGExpressionUtils> ngUtils = mockStatic(NGExpressionUtils.class)) {
      ngUtils
          .when(()
                    -> NGExpressionUtils.containsPattern(
                        NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, sendGitStatus.getName()))
          .thenReturn(true);
      when(pmsEngineExpressionService.resolve(
               any(), eq(sendGitStatus.getName()), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)))
          .thenReturn("resolvedStageName");
      String result = gitStatusUpdateNotifier.getStageIdentifier(planNode, nodeExecution);
      assertThat(result).isEqualTo("resolvedStageName");
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetStageIdentifier_WhenExpressionResolutionFails() {
    NodeExecution nodeExecution = NodeExecution.builder().identifier("testStage").build();
    SendGitStatusConfig sendGitStatus =
        SendGitStatusConfig.newBuilder().setName("<+pipeline.stages.stage1.name>").build();
    PlanNode planNode = PlanNode.builder().sendGitStatus(sendGitStatus).build();
    try (MockedStatic<NGExpressionUtils> ngUtils = mockStatic(NGExpressionUtils.class)) {
      ngUtils
          .when(()
                    -> NGExpressionUtils.containsPattern(
                        NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, sendGitStatus.getName()))
          .thenReturn(true);
      when(pmsEngineExpressionService.resolve(
               any(), eq(sendGitStatus.getName()), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)))
          .thenReturn(null);
      String result = gitStatusUpdateNotifier.getStageIdentifier(planNode, nodeExecution);
      assertThat(result).isEqualTo("testStage");
    }
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetStageIdentifier_WhenNameHasMultipleExpressions_ResolvesAll() {
    NodeExecution nodeExecution =
        NodeExecution.builder().identifier("testStage").ambiance(Ambiance.newBuilder().build()).build();
    String nameWithMultipleExpressions = "<+org.identifier>/<+project.identifier>/<+pipeline.name>/<+stage.name>";
    SendGitStatusConfig sendGitStatus = SendGitStatusConfig.newBuilder().setName(nameWithMultipleExpressions).build();
    PlanNode planNode = PlanNode.builder().sendGitStatus(sendGitStatus).build();
    try (MockedStatic<NGExpressionUtils> ngUtils = mockStatic(NGExpressionUtils.class)) {
      ngUtils
          .when(()
                    -> NGExpressionUtils.containsPattern(
                        NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, nameWithMultipleExpressions))
          .thenReturn(true);
      when(pmsEngineExpressionService.resolve(
               any(), eq(nameWithMultipleExpressions), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)))
          .thenReturn("myOrg/myProject/myPipeline/myStage");
      String result = gitStatusUpdateNotifier.getStageIdentifier(planNode, nodeExecution);
      assertThat(result).isEqualTo("myOrg/myProject/myPipeline/myStage");
    }
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetStageIdentifier_WhenNameIsVariableWithNestedExpressions_ResolvesFully() {
    NodeExecution nodeExecution =
        NodeExecution.builder().identifier("testStage").ambiance(Ambiance.newBuilder().build()).build();
    String variableExpression = "<+pipeline.variables.customname>";
    SendGitStatusConfig sendGitStatus = SendGitStatusConfig.newBuilder().setName(variableExpression).build();
    PlanNode planNode = PlanNode.builder().sendGitStatus(sendGitStatus).build();
    try (MockedStatic<NGExpressionUtils> ngUtils = mockStatic(NGExpressionUtils.class)) {
      ngUtils
          .when(()
                    -> NGExpressionUtils.containsPattern(
                        NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, variableExpression))
          .thenReturn(true);
      // Variable value could be "<+org.name>/<+project.name>/..."; resolve processes the string via processString
      when(pmsEngineExpressionService.resolve(
               any(), eq(variableExpression), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)))
          .thenReturn("resolvedOrg/resolvedProject/resolvedPipeline/resolvedStage");
      String result = gitStatusUpdateNotifier.getStageIdentifier(planNode, nodeExecution);
      assertThat(result).isEqualTo("resolvedOrg/resolvedProject/resolvedPipeline/resolvedStage");
    }
  }

  @Test
  @Owner(developers = OwnerRule.SHIVAM)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdate_WhenFeatureFlagDisabled_ShouldNotProcess() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);

    gitStatusUpdateNotifier.onNodeStatusUpdate(nodeExecution, nodeExecutionService.getAmbiance(nodeExecution));
    verify(gitBuildStatusUtility, never()).sendStatusToGit(any());
  }

  @Test
  @Owner(developers = OwnerRule.SHIVAM)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdate_WhenValidInput_ShouldProcess() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(Ambiance.newBuilder().build());
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanExecutionId()).thenReturn(PLAN_EXECUTION_ID);
    when(nodeExecution.getNodeId()).thenReturn(NODE_ID);
    when(nodeExecution.getPlanId()).thenReturn(PLAN_ID);
    when(nodeExecution.getCurrentLevel())
        .thenReturn(Level.newBuilder()
                        .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("STAGE").build())
                        .build());
    when(planExecutionService.get(any()))
        .thenReturn(PlanExecution.builder().ambiance(Ambiance.newBuilder().build()).planId("plan").build());

    when(planExecutionMetadataService.findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder()
                                    .triggerPayload(TriggerPayload.newBuilder()
                                                        .setParsedPayload(ParsedPayload.newBuilder()
                                                                              .setPr(PullRequestHook.newBuilder()
                                                                                         .setPr(PullRequest.newBuilder()
                                                                                                    .setTitle("PR")
                                                                                                    .setTarget("PR")
                                                                                                    .setNumber(1200)
                                                                                                    .build())
                                                                                         .build())
                                                                              .build())
                                                        .build())
                                    .build()));

    PlanNode planNode = mock(PlanNode.class);
    when(planService.fetchNode(PLAN_ID, NODE_ID))
        .thenReturn(PlanNode.builder()
                        .sendGitStatus(SendGitStatusConfig.newBuilder().setEnabled(true).setName("custom").build())
                        .build());
    when(gitBuildStatusUtility.shouldSendStatus(any())).thenReturn(true);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(true);

    gitStatusUpdateNotifier.onNodeStatusUpdate(nodeExecution, nodeExecutionService.getAmbiance(nodeExecution));

    verify(gitBuildStatusUtility, times(1)).sendStatusToGit(any());
  }

  @Test
  @Owner(developers = OwnerRule.SHIVAM)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdate_WhenExceptionThrown_ShouldNotPropagate() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanExecutionId()).thenReturn(PLAN_EXECUTION_ID);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(true);
    when(planExecutionMetadataService.findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID))
        .thenThrow(new RuntimeException("Test exception"));

    gitStatusUpdateNotifier.onNodeStatusUpdate(nodeExecution, nodeExecutionService.getAmbiance(nodeExecution));

    verify(gitBuildStatusUtility, never()).sendStatusToGit(any());
  }

  @Test
  @Owner(developers = OwnerRule.SHIVAM)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdate_SendGitStatusOnlyForPullRequest() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(Ambiance.newBuilder().build());
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanExecutionId()).thenReturn(PLAN_EXECUTION_ID);
    when(nodeExecution.getNodeId()).thenReturn(NODE_ID);
    when(nodeExecution.getPlanId()).thenReturn(PLAN_ID);
    when(nodeExecution.getCurrentLevel())
        .thenReturn(Level.newBuilder()
                        .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("STAGE").build())
                        .build());
    when(planExecutionService.get(any()))
        .thenReturn(PlanExecution.builder().ambiance(Ambiance.newBuilder().build()).planId("plan").build());

    when(planExecutionMetadataService.findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID))
        .thenReturn(Optional.of(
            PlanExecutionMetadata.builder()
                .triggerPayload(
                    TriggerPayload.newBuilder()
                        .setParsedPayload(ParsedPayload.newBuilder().setPush(PushHook.newBuilder().build()).build())
                        .build())
                .build()));

    PlanNode planNode = mock(PlanNode.class);
    when(planService.fetchNode(PLAN_ID, NODE_ID))
        .thenReturn(PlanNode.builder()
                        .sendGitStatus(SendGitStatusConfig.newBuilder().setEnabled(true).setName("custom").build())
                        .build());
    when(gitBuildStatusUtility.shouldSendStatus(any())).thenReturn(true);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(true);

    gitStatusUpdateNotifier.onNodeStatusUpdate(nodeExecution, nodeExecutionService.getAmbiance(nodeExecution));

    verify(gitBuildStatusUtility, never()).sendStatusToGit(any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testOnNodeStatusUpdate_SendGitStatusOnlyForPullRequestWhenBuildAmbianceFromContext() {
    NodeExecution nodeExecution = mock(NodeExecution.class);

    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanExecutionId()).thenReturn(PLAN_EXECUTION_ID);
    when(nodeExecution.getNodeId()).thenReturn(NODE_ID);
    when(nodeExecution.getPlanId()).thenReturn(PLAN_ID);
    when(nodeExecution.getCurrentLevel())
        .thenReturn(Level.newBuilder()
                        .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("STAGE").build())
                        .build());
    when(planExecutionService.get(any()))
        .thenReturn(PlanExecution.builder().ambiance(Ambiance.newBuilder().build()).planId("plan").build());
    when(planExecutionService.getExecutionMetadataFromPlanExecution(any()))
        .thenReturn(ExecutionMetadata.newBuilder().build());

    when(planExecutionMetadataService.findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID))
        .thenReturn(Optional.of(
            PlanExecutionMetadata.builder()
                .triggerPayload(TriggerPayload.newBuilder()
                                    .setParsedPayload(
                                        ParsedPayload.newBuilder().setPr(PullRequestHook.newBuilder().build()).build())
                                    .build())
                .build()));

    PlanNode planNode = mock(PlanNode.class);
    String variableExpression = "<+project.name>";
    when(planService.fetchNode(PLAN_ID, NODE_ID))
        .thenReturn(
            PlanNode.builder()
                .sendGitStatus(SendGitStatusConfig.newBuilder().setEnabled(true).setName(variableExpression).build())
                .build());
    when(gitBuildStatusUtility.shouldSendStatus(any())).thenReturn(true);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(Ambiance.newBuilder().build());
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(true);
    when(
        pmsEngineExpressionService.resolve(any(), eq(variableExpression), eq(ExpressionMode.RETURN_NULL_IF_UNRESOLVED)))
        .thenReturn("resolvedOrg/resolvedProject/resolvedPipeline/resolvedStage");
    gitStatusUpdateNotifier.onNodeStatusUpdate(nodeExecution, Ambiance.newBuilder().build());

    verify(gitBuildStatusUtility, times(1)).sendStatusToGit(any());
    verify(nodeExecutionService, times(2)).getAmbiance(nodeExecution); // here it is called 2 times
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void nodeStatusUpdateSendsStatusFoBuildContextForCodeRepo() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    PlanExecution planExecution = mock(PlanExecution.class);
    Ambiance ambiance = Ambiance.newBuilder().build();
    TriggerPayload triggerPayload = mock(TriggerPayload.class);
    ParsedPayload parsedPayload = mock(ParsedPayload.class);
    PullRequest pr = PullRequest.newBuilder().setSha("sha").setTitle("title").setTarget("url").setNumber(100).build();
    Repository repo = Repository.newBuilder().setName("repo").build();
    PullRequestHook prHook = PullRequestHook.newBuilder().setPr(pr).setRepo(repo).build();
    PlanExecutionMetadata planExecutionMetadata = mock(PlanExecutionMetadata.class);

    // Mock in correct order to avoid NPE
    when(planExecution.getTriggerPayload()).thenReturn(triggerPayload);
    when(planExecutionMetadata.getTriggerPayload()).thenReturn(triggerPayload);
    when(triggerPayload.getParsedPayload()).thenReturn(parsedPayload);
    when(parsedPayload.getPr()).thenReturn(prHook);

    when(nodeExecution.getAccountId()).thenReturn("account-id");
    when(nodeExecution.getPipelineIdentifier()).thenReturn("pipeline-id");
    when(nodeExecution.getNodeId()).thenReturn("node-id");
    when(nodeExecution.getStageExecutionId()).thenReturn("stage-exec-id");
    when(nodeExecution.getPlanExecutionId()).thenReturn("plan-exec-id");
    when(nodeExecution.getStatus()).thenReturn(Status.SUCCEEDED);
    when(nodeExecution.getStartTs()).thenReturn(123456789L);

    when(planExecution.getAmbiance()).thenReturn(ambiance);

    NGAccess ngAccess = mock(NGAccess.class);
    ConnectorDetails connectorDetails = mock(ConnectorDetails.class);
    try (MockedStatic<AmbianceUtils> mockedStatic = mockStatic(AmbianceUtils.class)) {
      mockedStatic.when(() -> AmbianceUtils.getNgAccess(ambiance)).thenReturn(ngAccess);

      when(connectorUtils.getConnectorDetails(ngAccess, "", true)).thenReturn(connectorDetails);

      GitStatusNotificationParams params =
          gitStatusUpdateNotifier.buildContext(nodeExecution, planExecutionMetadata, "stage-id");

      assertThat("account-id").isEqualTo(params.getAccountId());
      assertThat("pipeline-id").isEqualTo(params.getPipelineIdentifier());
      assertThat("repo").isEqualTo(params.getRepoName());
      assertThat("sha").isEqualTo(params.getSha());
      assertThat("title").isEqualTo(params.getTitle());
      assertThat("url").isEqualTo(params.getTargetUrl());
      assertThat("100").isEqualTo(params.getPrNumber());
      assertThat("stage-exec-id").isEqualTo(params.getStageExecutionId());
      assertThat("plan-exec-id").isEqualTo(params.getPlanExecutionId());
      assertThat(123456789L).isEqualTo(params.getStartTs());
    }
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testGetPipelineName_ReturnsDisplayNameFromSummary() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanExecutionId()).thenReturn(PLAN_EXECUTION_ID);
    when(nodeExecution.getPipelineIdentifier()).thenReturn("pex_hup_PSW_scheduling_practice_apps");
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, PLAN_EXECUTION_ID, false))
        .thenReturn(PipelineExecutionSummaryEntity.builder().name("scheduling-practice-apps").build());

    String result = gitStatusUpdateNotifier.getPipelineName(nodeExecution);

    assertThat(result).isEqualTo("scheduling-practice-apps");
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testGetPipelineName_FallsBackToIdentifierWhenSummaryMissing() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanExecutionId()).thenReturn(PLAN_EXECUTION_ID);
    when(nodeExecution.getPipelineIdentifier()).thenReturn("pipeline_slug");
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, PLAN_EXECUTION_ID, false)).thenReturn(null);

    String result = gitStatusUpdateNotifier.getPipelineName(nodeExecution);

    assertThat(result).isEqualTo("pipeline_slug");
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testGetPipelineName_FallsBackToIdentifierOnException() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanExecutionId()).thenReturn(PLAN_EXECUTION_ID);
    when(nodeExecution.getPipelineIdentifier()).thenReturn("pipeline_slug");
    when(pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, PLAN_EXECUTION_ID, false))
        .thenThrow(new RuntimeException("summary not found"));

    String result = gitStatusUpdateNotifier.getPipelineName(nodeExecution);

    assertThat(result).isEqualTo("pipeline_slug");
  }

  @Test
  @Owner(developers = ANKUR_PATEL)
  @Category(UnitTests.class)
  public void testBuildContext_SetsPipelineNameOnParams() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    Ambiance ambiance = Ambiance.newBuilder().build();
    TriggerPayload triggerPayload = mock(TriggerPayload.class);
    ParsedPayload parsedPayload = mock(ParsedPayload.class);
    PullRequest pr = PullRequest.newBuilder().setSha("sha").setTitle("title").setTarget("url").setNumber(100).build();
    Repository repo = Repository.newBuilder().setName("repo").build();
    PullRequestHook prHook = PullRequestHook.newBuilder().setPr(pr).setRepo(repo).build();
    PlanExecutionMetadata planExecutionMetadata = mock(PlanExecutionMetadata.class);

    when(planExecutionMetadata.getTriggerPayload()).thenReturn(triggerPayload);
    when(triggerPayload.getParsedPayload()).thenReturn(parsedPayload);
    when(parsedPayload.getPr()).thenReturn(prHook);

    when(nodeExecution.getAccountId()).thenReturn("account-id");
    when(nodeExecution.getPipelineIdentifier()).thenReturn("pipeline-id");
    when(nodeExecution.getNodeId()).thenReturn("node-id");
    when(nodeExecution.getStageExecutionId()).thenReturn("stage-exec-id");
    when(nodeExecution.getPlanExecutionId()).thenReturn("plan-exec-id");
    when(nodeExecution.getStatus()).thenReturn(Status.SUCCEEDED);
    when(nodeExecution.getStartTs()).thenReturn(123456789L);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);

    NGAccess ngAccess = mock(NGAccess.class);
    ConnectorDetails connectorDetails = mock(ConnectorDetails.class);
    try (MockedStatic<AmbianceUtils> mockedStatic = mockStatic(AmbianceUtils.class)) {
      mockedStatic.when(() -> AmbianceUtils.getNgAccess(ambiance)).thenReturn(ngAccess);
      when(connectorUtils.getConnectorDetails(any(), any(), eq(true), any())).thenReturn(connectorDetails);

      GitStatusNotificationParams params = gitStatusUpdateNotifier.buildContext(
          nodeExecution, planExecutionMetadata, "stage-id", "Scheduling Practice Apps");

      assertThat(params.getPipelineIdentifier()).isEqualTo("pipeline-id");
      assertThat(params.getPipelineName()).isEqualTo("Scheduling Practice Apps");
      assertThat(params.getStageIdentifier()).isEqualTo("stage-id");
    }
  }

  private GitOpsPRStatusInfo prStatusInfo(int prNumber) {
    return GitOpsPRStatusInfo.builder()
        .sha("sha" + prNumber)
        .owner("owner")
        .repo("repo")
        .prNumber(prNumber)
        .connectorIdentifier("connector")
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .scopedRepoIdentifier("scopedRepo")
        .build();
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testProcessGitOps_WhenFinalStatusAndContextFoundAndEnabled_SendsFinalStatus() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanId()).thenReturn(PLAN_ID);
    when(nodeExecution.getNodeId()).thenReturn(NODE_ID);
    when(nodeExecution.getStatus()).thenReturn(Status.SUCCEEDED);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
        .thenReturn(false);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);

    GitOpsPRStatusInfo prInfo = prStatusInfo(1);
    GitOpsStatusCheckOutput context =
        GitOpsStatusCheckOutput.builder().prStatusInfos(Collections.singletonList(prInfo)).build();
    when(executionSweepingOutputService.resolveOptional(eq(ambiance), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(context).build());
    when(planService.fetchNode(PLAN_ID, NODE_ID))
        .thenReturn(
            PlanNode.builder().sendGitStatus(SendGitStatusConfig.newBuilder().setEnabled(true).build()).build());
    when(gitBuildStatusUtility.getBuildDetailsUrl(ambiance)).thenReturn("http://execution-url");

    try (MockedStatic<StatusUtils> statusUtils = mockStatic(StatusUtils.class);
         MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class)) {
      statusUtils.when(() -> StatusUtils.isFinalStatus(Status.SUCCEEDED)).thenReturn(true);
      ambianceUtils.when(() -> AmbianceUtils.getStageIdentifierFromAmbiance(ambiance)).thenReturn("stageId");
      ambianceUtils.when(() -> AmbianceUtils.getPipelineIdentifier(ambiance)).thenReturn("pipelineId");

      gitStatusUpdateNotifier.onGitOpsNodeStatusUpdate(nodeExecution, nodeExecutionService.getAmbiance(nodeExecution));

      verify(gitOpsGitStatusHelper, times(1))
          .sendFinalStatus(
              eq(ambiance), eq(prInfo), eq(Status.SUCCEEDED), eq("pipelineId/stageId"), eq("http://execution-url"));
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testProcessGitOps_WhenStatusNotFinal_DoesNotSendFinalStatus() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getStatus()).thenReturn(Status.RUNNING);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(Ambiance.newBuilder().build());
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
        .thenReturn(false);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);

    try (MockedStatic<StatusUtils> statusUtils = mockStatic(StatusUtils.class)) {
      statusUtils.when(() -> StatusUtils.isFinalStatus(Status.RUNNING)).thenReturn(false);

      gitStatusUpdateNotifier.onGitOpsNodeStatusUpdate(nodeExecution, nodeExecutionService.getAmbiance(nodeExecution));

      verify(gitOpsGitStatusHelper, never()).sendFinalStatus(any(), any(), any(), anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testProcessGitOps_WhenSweepingOutputNotFound_DoesNotSendFinalStatus() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanId()).thenReturn(PLAN_ID);
    when(nodeExecution.getNodeId()).thenReturn(NODE_ID);
    when(nodeExecution.getStatus()).thenReturn(Status.SUCCEEDED);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
        .thenReturn(false);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);
    when(planService.fetchNode(PLAN_ID, NODE_ID))
        .thenReturn(
            PlanNode.builder().sendGitStatus(SendGitStatusConfig.newBuilder().setEnabled(true).build()).build());
    when(executionSweepingOutputService.resolveOptional(eq(ambiance), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    try (MockedStatic<StatusUtils> statusUtils = mockStatic(StatusUtils.class)) {
      statusUtils.when(() -> StatusUtils.isFinalStatus(Status.SUCCEEDED)).thenReturn(true);

      gitStatusUpdateNotifier.onGitOpsNodeStatusUpdate(nodeExecution, nodeExecutionService.getAmbiance(nodeExecution));

      verify(gitOpsGitStatusHelper, never()).sendFinalStatus(any(), any(), any(), anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testProcessGitOps_WhenSendGitStatusDisabled_DoesNotSendFinalStatus() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanId()).thenReturn(PLAN_ID);
    when(nodeExecution.getNodeId()).thenReturn(NODE_ID);
    when(nodeExecution.getStatus()).thenReturn(Status.SUCCEEDED);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
        .thenReturn(false);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);

    when(planService.fetchNode(PLAN_ID, NODE_ID))
        .thenReturn(
            PlanNode.builder().sendGitStatus(SendGitStatusConfig.newBuilder().setEnabled(false).build()).build());

    try (MockedStatic<StatusUtils> statusUtils = mockStatic(StatusUtils.class)) {
      statusUtils.when(() -> StatusUtils.isFinalStatus(Status.SUCCEEDED)).thenReturn(true);

      gitStatusUpdateNotifier.onGitOpsNodeStatusUpdate(nodeExecution, nodeExecutionService.getAmbiance(nodeExecution));

      verify(executionSweepingOutputService, never()).resolveOptional(any(), any());
      verify(gitOpsGitStatusHelper, never()).sendFinalStatus(any(), any(), any(), anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testProcessGitOps_WhenSendGitStatusNull_DoesNotSendFinalStatus() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanId()).thenReturn(PLAN_ID);
    when(nodeExecution.getNodeId()).thenReturn(NODE_ID);
    when(nodeExecution.getStatus()).thenReturn(Status.SUCCEEDED);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
        .thenReturn(false);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);

    when(planService.fetchNode(PLAN_ID, NODE_ID)).thenReturn(PlanNode.builder().build());

    try (MockedStatic<StatusUtils> statusUtils = mockStatic(StatusUtils.class)) {
      statusUtils.when(() -> StatusUtils.isFinalStatus(Status.SUCCEEDED)).thenReturn(true);

      gitStatusUpdateNotifier.onGitOpsNodeStatusUpdate(nodeExecution, nodeExecutionService.getAmbiance(nodeExecution));

      verify(executionSweepingOutputService, never()).resolveOptional(any(), any());
      verify(gitOpsGitStatusHelper, never()).sendFinalStatus(any(), any(), any(), anyString(), anyString());
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testResolveStatusCheckName_WhenSendGitStatusNull_ReturnsPipelineSlashStage() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    PlanNode planNode = PlanNode.builder().build();

    try (MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class)) {
      ambianceUtils.when(() -> AmbianceUtils.getStageIdentifierFromAmbiance(ambiance)).thenReturn("stageId");
      ambianceUtils.when(() -> AmbianceUtils.getPipelineIdentifier(ambiance)).thenReturn("pipelineId");

      String result = gitStatusUpdateNotifier.resolveStatusCheckName(planNode, ambiance);

      assertThat(result).isEqualTo("pipelineId/stageId");
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testResolveStatusCheckName_WhenNameIsNotExpression_ReturnsName() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    PlanNode planNode =
        PlanNode.builder().sendGitStatus(SendGitStatusConfig.newBuilder().setName("static-name").build()).build();

    try (MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class);
         MockedStatic<NGExpressionUtils> ngUtils = mockStatic(NGExpressionUtils.class)) {
      ambianceUtils.when(() -> AmbianceUtils.getStageIdentifierFromAmbiance(ambiance)).thenReturn("stageId");
      ngUtils
          .when(() -> NGExpressionUtils.containsPattern(NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, "static-name"))
          .thenReturn(false);

      String result = gitStatusUpdateNotifier.resolveStatusCheckName(planNode, ambiance);

      assertThat(result).isEqualTo("static-name");
      verify(pmsEngineExpressionService, never()).resolve(any(), any(), any());
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testResolveStatusCheckName_WhenNameIsExpression_ReturnsResolvedValue() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    String expression = "<+pipeline.name>";
    PlanNode planNode =
        PlanNode.builder().sendGitStatus(SendGitStatusConfig.newBuilder().setName(expression).build()).build();

    try (MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class);
         MockedStatic<NGExpressionUtils> ngUtils = mockStatic(NGExpressionUtils.class)) {
      ambianceUtils.when(() -> AmbianceUtils.getStageIdentifierFromAmbiance(ambiance)).thenReturn("stageId");
      ngUtils.when(() -> NGExpressionUtils.containsPattern(NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, expression))
          .thenReturn(true);
      when(pmsEngineExpressionService.resolve(ambiance, expression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
          .thenReturn("resolvedName");

      String result = gitStatusUpdateNotifier.resolveStatusCheckName(planNode, ambiance);

      assertThat(result).isEqualTo("resolvedName");
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testResolveStatusCheckName_WhenExpressionUnresolved_ReturnsPipelineSlashStage() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    String expression = "<+pipeline.name>";
    PlanNode planNode =
        PlanNode.builder().sendGitStatus(SendGitStatusConfig.newBuilder().setName(expression).build()).build();

    try (MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class);
         MockedStatic<NGExpressionUtils> ngUtils = mockStatic(NGExpressionUtils.class)) {
      ambianceUtils.when(() -> AmbianceUtils.getStageIdentifierFromAmbiance(ambiance)).thenReturn("stageId");
      ambianceUtils.when(() -> AmbianceUtils.getPipelineIdentifier(ambiance)).thenReturn("pipelineId");
      ngUtils.when(() -> NGExpressionUtils.containsPattern(NGExpressionUtils.GENERIC_EXPRESSIONS_PATTERN, expression))
          .thenReturn(true);
      when(pmsEngineExpressionService.resolve(ambiance, expression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
          .thenReturn(null);

      String result = gitStatusUpdateNotifier.resolveStatusCheckName(planNode, ambiance);

      assertThat(result).isEqualTo("pipelineId/stageId");
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testProcessGitOps_WhenOnePrFails_LoopContinuesAndDoesNotPropagate() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    Ambiance ambiance = Ambiance.newBuilder().build();
    when(nodeExecution.getAccountId()).thenReturn(ACCOUNT_ID);
    when(nodeExecution.getPlanId()).thenReturn(PLAN_ID);
    when(nodeExecution.getNodeId()).thenReturn(NODE_ID);
    when(nodeExecution.getStatus()).thenReturn(Status.SUCCEEDED);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
        .thenReturn(false);
    when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);

    GitOpsPRStatusInfo prInfo1 = prStatusInfo(1);
    GitOpsPRStatusInfo prInfo2 = prStatusInfo(2);
    GitOpsStatusCheckOutput context =
        GitOpsStatusCheckOutput.builder().prStatusInfos(Arrays.asList(prInfo1, prInfo2)).build();
    when(executionSweepingOutputService.resolveOptional(eq(ambiance), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(context).build());
    when(planService.fetchNode(PLAN_ID, NODE_ID))
        .thenReturn(
            PlanNode.builder().sendGitStatus(SendGitStatusConfig.newBuilder().setEnabled(true).build()).build());
    when(gitBuildStatusUtility.getBuildDetailsUrl(ambiance)).thenReturn("http://execution-url");
    doThrow(new RuntimeException("boom"))
        .when(gitOpsGitStatusHelper)
        .sendFinalStatus(eq(ambiance), eq(prInfo1), eq(Status.SUCCEEDED), anyString(), anyString());

    try (MockedStatic<StatusUtils> statusUtils = mockStatic(StatusUtils.class);
         MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class)) {
      statusUtils.when(() -> StatusUtils.isFinalStatus(Status.SUCCEEDED)).thenReturn(true);
      ambianceUtils.when(() -> AmbianceUtils.getStageIdentifierFromAmbiance(ambiance)).thenReturn("stageId");
      ambianceUtils.when(() -> AmbianceUtils.getPipelineIdentifier(ambiance)).thenReturn("pipelineId");

      gitStatusUpdateNotifier.onGitOpsNodeStatusUpdate(nodeExecution, nodeExecutionService.getAmbiance(nodeExecution));

      verify(gitOpsGitStatusHelper, times(1))
          .sendFinalStatus(
              eq(ambiance), eq(prInfo2), eq(Status.SUCCEEDED), eq("pipelineId/stageId"), eq("http://execution-url"));
    }
  }
}
