/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.pms.contracts.execution.ExecutionMode.SYNC;
import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.pms.contracts.plan.ExecutionMode.POST_EXECUTION_ROLLBACK;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.plancreator.common.dependencyUtils.DependencyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.DependencyEntry;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.GraphLayoutInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.PrincipalType;
import io.harness.pms.contracts.plan.StringArray;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.util.CloseableIterator;

public class RollbackModeExecutionHelperTest extends CategoryTest {
  @Spy RollbackModeExecutionHelper rollbackModeExecutionHelper;
  @Mock NodeExecutionService nodeExecutionService;

  @Mock PlanService planService;
  @Mock PrincipalInfoHelper principalInfoHelper;
  @Mock RollbackModeYamlTransformer rollbackModeYamlTransformer;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock NodeExecutionInfoService nodeExecutionInfoService;
  @Mock PlanExecutionService planExecutionService;

  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String pipeline = "pipelineId";
  PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
      PlanExecutionMetadataWithContext.builder().runAllStages(true).build();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    rollbackModeExecutionHelper = new RollbackModeExecutionHelper(nodeExecutionService, planExecutionService,
        planService, principalInfoHelper, rollbackModeYamlTransformer, pmsFeatureFlagHelper, nodeExecutionInfoService);
  }

  private void mockDagPostExecutionRollbackGateEnabled(String previousExecutionId) {
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)).thenReturn(true);
    when(planExecutionService.getWithFieldsIncludedOptional(eq(previousExecutionId), any()))
        .thenReturn(Optional.of(
            PlanExecution.builder().metadata(ExecutionMetadata.newBuilder().setEnableDAG(true).build()).build()));
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformExecutionMetadata() {
    ExecutionPrincipalInfo newPrincipalInfo =
        ExecutionPrincipalInfo.newBuilder().setPrincipalType(PrincipalType.USER).build();
    doReturn(newPrincipalInfo).when(principalInfoHelper).getPrincipalInfoFromSecurityContext();

    ExecutionMetadata oldExecutionMetadata =
        ExecutionMetadata.newBuilder()
            .setExecutionUuid("oldId")
            .setTriggerInfo(ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.WEBHOOK).build())
            .setRunSequence(5131)
            .setPrincipalInfo(ExecutionPrincipalInfo.newBuilder().setPrincipalType(PrincipalType.SERVICE).build())
            .setExecutionMode(ExecutionMode.NORMAL)
            .setPipelineIdentifier(pipeline)
            .build();
    String newId = "newId";
    ExecutionTriggerInfo newTriggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("ds").build()).build();
    ExecutionMetadata newMetadata =
        rollbackModeExecutionHelper.transformExecutionMetadata(oldExecutionMetadata, newId, newTriggerInfo,
            POST_EXECUTION_ROLLBACK, PipelineStageInfo.newBuilder().setHasParentPipeline(true).build(), null);
    assertThat(newMetadata.getExecutionUuid()).isEqualTo(newId);
    assertThat(newMetadata.getTriggerInfo()).isEqualTo(newTriggerInfo);
    assertThat(newMetadata.getPrincipalInfo()).isEqualTo(newPrincipalInfo);
    assertThat(newMetadata.getExecutionMode()).isEqualTo(POST_EXECUTION_ROLLBACK);
    assertThat(newMetadata.getPipelineStageInfo().getHasParentPipeline()).isTrue();
    assertThat(newMetadata.getOriginalPlanExecutionIdForRollbackMode()).isEqualTo("oldId");
    assertThat(rollbackModeExecutionHelper
                   .transformExecutionMetadata(
                       oldExecutionMetadata, newId, newTriggerInfo, POST_EXECUTION_ROLLBACK, null, null)
                   .getPipelineStageInfo()
                   .getHasParentPipeline())
        .isFalse();

    List<String> stageNodeExecutionIds = Collections.singletonList("stageNodeExecutionId");

    doReturn(Collections.singletonList(
                 NodeExecution.builder()
                     .ambiance(Ambiance.newBuilder()
                                   .addLevels(Level.newBuilder()
                                                  .setSetupId("setupId")
                                                  .setRuntimeId("runtime123")
                                                  .setStrategyMetadata(StrategyMetadata.newBuilder().build())
                                                  .build())
                                   .build())
                     .nodeId("planNodeUuid")
                     .build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(
            new HashSet<>(stageNodeExecutionIds), NodeProjectionUtils.fieldsForRollbackTransformer);

    newMetadata = rollbackModeExecutionHelper.transformExecutionMetadata(oldExecutionMetadata, newId, newTriggerInfo,
        POST_EXECUTION_ROLLBACK, PipelineStageInfo.newBuilder().setHasParentPipeline(true).build(),
        stageNodeExecutionIds);

    assertThat(newMetadata.getOriginalPlanExecutionIdForRollbackMode())
        .isEqualTo(oldExecutionMetadata.getExecutionUuid());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformPlanExecutionMetadata() {
    String original = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n";
    String transformed = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: \"s2\"\n"
        + "  - stage:\n"
        + "      identifier: \"s1\"\n";
    doReturn(transformed)
        .when(rollbackModeYamlTransformer)
        .transformProcessedYaml(
            account, original, POST_EXECUTION_ROLLBACK, "oldPlanId", null, HarnessYamlVersion.V0, false);
    PlanExecutionMetadata oldPlanExecutionMetadata = PlanExecutionMetadata.builder()
                                                         .uuid("randomId")
                                                         .accountIdentifier(account)
                                                         .planExecutionId("oldPlanId")
                                                         .yaml(original)
                                                         .processedYaml(original)
                                                         .build();
    String newId = "newId";
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext1 =
        planExecutionMetadataWithContext.withPreviousExecutionId("oldPlanId");
    PlanExecutionMetadata newMetadata = rollbackModeExecutionHelper.transformPlanExecutionMetadata(
        oldPlanExecutionMetadata, newId, POST_EXECUTION_ROLLBACK, null, null, planExecutionMetadataWithContext1);
    assertThat(newMetadata.getUuid()).isNull();
    assertThat(newMetadata.getPlanExecutionId()).isEqualTo(newId);
    assertThat(planExecutionMetadataWithContext1.getStagesExecutionMetadata()).isNull();
    assertThat(planExecutionMetadataWithContext1.getProcessedYaml()).isEqualTo(transformed);
    assertThat(planExecutionMetadataWithContext.getPostExecutionRollbackInfos().size()).isEqualTo(0);

    List<String> stageNodeExecutionIds = Collections.singletonList("stageNodeExecutionId");
    String stageFqn = "pipeline.stages.stage1";

    doReturn(Collections.singletonList(
                 NodeExecution.builder()
                     .stageFqn(stageFqn)
                     .ambiance(Ambiance.newBuilder()
                                   .addLevels(Level.newBuilder()
                                                  .setSetupId("setupId")
                                                  .setRuntimeId("runtime123")
                                                  .setStrategyMetadata(StrategyMetadata.newBuilder().build())
                                                  .build())
                                   .build())
                     .nodeId("planNodeUuid")
                     .mode(SYNC)
                     .build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(
            new HashSet<>(stageNodeExecutionIds), NodeProjectionUtils.fieldsForRollbackTransformer);
    PlanExecutionMetadataWithContext newPlanExecutionMetadataWithContext =
        planExecutionMetadataWithContext.withPreviousExecutionId("oldPlanId");
    newMetadata = rollbackModeExecutionHelper.transformPlanExecutionMetadata(oldPlanExecutionMetadata, newId,
        POST_EXECUTION_ROLLBACK, stageNodeExecutionIds, null, newPlanExecutionMetadataWithContext);
    assertThat(newPlanExecutionMetadataWithContext.getStagesExecutionMetadata().getStageIdentifiers().size())
        .isEqualTo(1);
    assertThat(newPlanExecutionMetadataWithContext.getStagesExecutionMetadata().getStageIdentifiers().get(0))
        .isEqualTo(stageFqn);

    assertThat(newPlanExecutionMetadataWithContext.getPostExecutionRollbackInfos().size()).isEqualTo(1);
    assertThat(
        newPlanExecutionMetadataWithContext.getPostExecutionRollbackInfos().get(0).getPostExecutionRollbackStageId())
        .isEqualTo("setupId");
    assertThat(
        newPlanExecutionMetadataWithContext.getPostExecutionRollbackInfos().get(0).getRollbackStageStrategyMetadata())
        .isEqualTo(StrategyMetadata.newBuilder().build());
    assertThat(newPlanExecutionMetadataWithContext.getPostExecutionRollbackInfos().get(0).getOriginalStageExecutionId())
        .isEqualTo("runtime123");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testTransformPlanForRollbackMode() {
    StepType stepType = StepType.newBuilder().setStepCategory(StepCategory.STEP).build();
    String prevExecId = "prevExecId";

    String planNodeIdentifier = "planNode";
    String nodeExecutionIdentifier = "nodeExecution";
    PlanNode stageNode = PlanNode.builder()
                             .uuid("s1")
                             .stageFqn("pipeline.stages.s1")
                             .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                             .skipGraphType(SkipType.NOOP)
                             .build();

    PlanNode toBeReplaced = PlanNode.builder()
                                .uuid("uuid1")
                                .identifier(planNodeIdentifier)
                                .name(planNodeIdentifier)
                                .stageFqn("pipeline.stages.s1")
                                .stepType(stepType)
                                .advisorObtainmentsForExecutionMode(Collections.singletonMap(POST_EXECUTION_ROLLBACK,
                                    Collections.singletonList(AdviserObtainment.newBuilder().build())))
                                .skipGraphType(SkipType.NOOP)
                                .build();

    NodeExecution nodeExecutionForUuid1 = NodeExecution.builder()
                                              .nodeId(toBeReplaced.getUuid())
                                              .stepType(stepType)
                                              .name(nodeExecutionIdentifier)
                                              .identifier(nodeExecutionIdentifier)
                                              .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                              .uuid("nodeExecForUuid1")
                                              .build();
    NodeExecution nodeExecutionForStage =
        NodeExecution.builder()
            .nodeId("s1")
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
            .name(nodeExecutionIdentifier)
            .identifier(nodeExecutionIdentifier)
            .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
            .uuid("nodeExecForUuid1")
            .build();

    List<NodeExecution> nodeExecutionList = Collections.singletonList(nodeExecutionForUuid1);

    PlanNode tobePreserved = PlanNode.builder()
                                 .uuid("uuid2")
                                 .stageFqn("pipeline.stages.s1")
                                 .stepType(stepType)
                                 .skipGraphType(SkipType.NOOP)
                                 .build();

    Plan createdPlan =
        Plan.builder().planNode(toBeReplaced).planNode(tobePreserved).planNode(stageNode).valid(true).build();

    Stream<NodeExecution> iterator = createCloseableIterator(nodeExecutionList.iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecId, Collections.singletonList("pipeline.stages.s1"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);

    when(planService.fetchNode("planId1", toBeReplaced.getUuid())).thenReturn(toBeReplaced);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(false);
    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecId,
        Collections.singletonList("uuid2"), POST_EXECUTION_ROLLBACK, Collections.singletonList("pipeline.stages.s1"),
        null);
    List<Node> nodes = transformedPlan.getPlanNodes();
    assertThat(nodes).hasSize(3);
    assertThat(nodes).contains(
        stageNode.withPreserveInRollbackMode(true), tobePreserved.withPreserveInRollbackMode(true));
    List<Node> identityNodes =
        nodes.stream().filter(node -> node.getNodeType() == NodeType.IDENTITY_PLAN_NODE).collect(Collectors.toList());
    assertThat(identityNodes).hasSize(1);
    IdentityPlanNode identityNode = (IdentityPlanNode) identityNodes.get(0);
    assertThat(identityNode.getUuid()).isEqualTo("uuid1");
    assertThat(identityNode.getSkipGraphType()).isEqualTo(SkipType.SKIP_NODE);
    assertThat(identityNode.getAdviserObtainments()).hasSize(1);
    assertThat(identityNode.getUseAdviserObtainments()).isTrue();
    assertThat(identityNode.getName()).isEqualTo(nodeExecutionIdentifier);
    assertThat(identityNode.getIdentifier()).isEqualTo(nodeExecutionIdentifier);

    // With FF enabled
    nodeExecutionList = Lists.newArrayList(nodeExecutionForUuid1, nodeExecutionForStage);
    iterator = createCloseableIterator(nodeExecutionList.iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecId, Collections.singletonList("pipeline.stages.s1"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", toBeReplaced.getUuid())).thenReturn(toBeReplaced);
    when(planService.fetchNode("planId1", stageNode.getUuid())).thenReturn(stageNode);

    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecId,
        Collections.singletonList("uuid2"), POST_EXECUTION_ROLLBACK, Collections.singletonList("pipeline.stages.s1"),
        account);
    nodes = transformedPlan.getPlanNodes();
    assertThat(nodes).hasSize(3);
    assertThat(nodes).contains(tobePreserved.withPreserveInRollbackMode(true));
    identityNodes =
        nodes.stream().filter(node -> node.getNodeType() == NodeType.IDENTITY_PLAN_NODE).collect(Collectors.toList());
    assertThat(identityNodes).hasSize(2);
    identityNode = (IdentityPlanNode) identityNodes.get(0);
    assertThat(identityNode.getUuid()).isEqualTo("uuid1");
    assertThat(identityNode.getSkipGraphType()).isEqualTo(SkipType.SKIP_NODE);
    assertThat(identityNode.getAdviserObtainments()).hasSize(1);
    assertThat(identityNode.getUseAdviserObtainments()).isTrue();
    assertThat(identityNode.getName()).isEqualTo(nodeExecutionIdentifier);
    assertThat(identityNode.getIdentifier()).isEqualTo(nodeExecutionIdentifier);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAddAdvisorsToIdentityNodes() {
    Map<String, Node> planNodeIDToUpdatedPlanNodes = new HashMap<>();
    planNodeIDToUpdatedPlanNodes.put("uuid1", IdentityPlanNode.builder().build());
    StepType stepType = StepType.newBuilder().setStepCategory(StepCategory.STEP).build();
    PlanNode tobePreserved = PlanNode.builder().uuid("uuid2").stepType(stepType).skipGraphType(SkipType.NOOP).build();
    PlanNode toBeReplaced =
        PlanNode.builder()
            .uuid("uuid1")
            .stageFqn("pipeline.stages.s1")
            .stepType(stepType)
            .advisorObtainmentsForExecutionMode(
                Map.of(POST_EXECUTION_ROLLBACK, Collections.singletonList(AdviserObtainment.newBuilder().build()),
                    PIPELINE_ROLLBACK, Collections.singletonList(AdviserObtainment.newBuilder().build())))
            .skipGraphType(SkipType.NOOP)
            .build();
    PlanNode stageNode = PlanNode.builder()
                             .uuid("s1")
                             .stageFqn("pipeline.stages.s1")
                             .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                             .skipGraphType(SkipType.NOOP)
                             .build();
    Plan createdPlan =
        Plan.builder().planNode(toBeReplaced).planNode(tobePreserved).planNode(stageNode).valid(true).build();

    rollbackModeExecutionHelper.addAdvisorsToIdentityNodes(createdPlan, planNodeIDToUpdatedPlanNodes,
        POST_EXECUTION_ROLLBACK, Collections.singletonList("pipeline.stages.s1"));

    IdentityPlanNode updatedNode = (IdentityPlanNode) planNodeIDToUpdatedPlanNodes.get("uuid1");
    assertThat(updatedNode.getUseAdviserObtainments()).isTrue();
    assertThat(updatedNode.getAdviserObtainments()).hasSize(1);

    planNodeIDToUpdatedPlanNodes.put("uuid1", IdentityPlanNode.builder().build());
    rollbackModeExecutionHelper.addAdvisorsToIdentityNodes(
        createdPlan, planNodeIDToUpdatedPlanNodes, POST_EXECUTION_ROLLBACK, null);
    updatedNode = (IdentityPlanNode) planNodeIDToUpdatedPlanNodes.get("uuid1");
    assertThat(updatedNode.getUseAdviserObtainments()).isFalse();
    assertThat(updatedNode.getAdviserObtainments()).isNull();

    planNodeIDToUpdatedPlanNodes.put("uuid1", IdentityPlanNode.builder().build());
    rollbackModeExecutionHelper.addAdvisorsToIdentityNodes(
        createdPlan, planNodeIDToUpdatedPlanNodes, PIPELINE_ROLLBACK, Collections.emptyList());
    updatedNode = (IdentityPlanNode) planNodeIDToUpdatedPlanNodes.get("uuid1");
    assertThat(updatedNode.getUseAdviserObtainments()).isTrue();
    assertThat(updatedNode.getAdviserObtainments()).hasSize(1);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCheckIfPostExecutionRollbackAllowed() {
    List<String> stageNodeExecutionIds = List.of("stageExecutionId1");
    doReturn(List.of(NodeExecution.builder().status(Status.SUCCEEDED).build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(new HashSet<>(stageNodeExecutionIds), NodeProjectionUtils.withStatus);
    assertThatCode(() -> rollbackModeExecutionHelper.checkIfPostExecutionRollbackAllowed(stageNodeExecutionIds))
        .doesNotThrowAnyException();
    doReturn(List.of(NodeExecution.builder().status(Status.ABORTED).build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(new HashSet<>(stageNodeExecutionIds), NodeProjectionUtils.withStatus);
    assertThatThrownBy(() -> rollbackModeExecutionHelper.checkIfPostExecutionRollbackAllowed(stageNodeExecutionIds))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not start the Post Execution Rollback because the stages [stageExecutionId1] are not "
            + "SUCCEEDED but [ABORTED]");
    doReturn(List.of(NodeExecution.builder().status(Status.FAILED).build()))
        .when(nodeExecutionService)
        .getAllWithFieldIncluded(new HashSet<>(stageNodeExecutionIds), NodeProjectionUtils.withStatus);
    assertThatThrownBy(() -> rollbackModeExecutionHelper.checkIfPostExecutionRollbackAllowed(stageNodeExecutionIds))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not start the Post Execution Rollback because the stages [stageExecutionId1] are not "
            + "SUCCEEDED but [FAILED]");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testTransformPlanForPostExecutionRollbackMode() {
    StepType stepType = StepType.newBuilder().setStepCategory(StepCategory.STEP).build();
    String prevExecutionId = "prevExecutionId";

    PlanNode stageNode1 = PlanNode.builder()
                              .uuid("stageNodeUuid1")
                              .stageFqn("pipeline.stages.s1")
                              .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                              .skipGraphType(SkipType.NOOP)
                              .build();

    String nodeExecutionIdentifierForStageNode1 = "nodeExecutionIdentifierForStageNode1";
    NodeExecution nodeExecutionForStageNode1 =
        NodeExecution.builder()
            .nodeId(stageNode1.getUuid())
            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
            .name(nodeExecutionIdentifierForStageNode1)
            .identifier(nodeExecutionIdentifierForStageNode1)
            .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
            .uuid("nodeExecutionForStageNodeUuid1")
            .build();

    PlanNode stage1StepsNode = PlanNode.builder()
                                   .uuid("stage1StepsNodeUuid")
                                   .stageFqn("pipeline.stages.s1")
                                   .stepType(stepType)
                                   .advisorObtainmentsForExecutionMode(Collections.singletonMap(POST_EXECUTION_ROLLBACK,
                                       Collections.singletonList(AdviserObtainment.newBuilder().build())))
                                   .skipGraphType(SkipType.NOOP)
                                   .build();

    String stage1StepsNodeExecutionIdentifier = "stage1StepsNodeExecutionIdentifier";
    NodeExecution stage1StepsNodeExecution = NodeExecution.builder()
                                                 .nodeId(stage1StepsNode.getUuid())
                                                 .stepType(stepType)
                                                 .name(stage1StepsNodeExecutionIdentifier)
                                                 .identifier(stage1StepsNodeExecutionIdentifier)
                                                 .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                                 .uuid("stage1StepsNodeExecutionUuid")
                                                 .build();

    PlanNode stage1StepNode = PlanNode.builder()
                                  .uuid("stage1StepNodeUuid")
                                  .stageFqn("pipeline.stages.s1")
                                  .stepType(stepType)
                                  .skipGraphType(SkipType.NOOP)
                                  .build();

    String stage1StepNodeExecutionIdentifier = "stage1StepNodeExecutionIdentifier";
    NodeExecution stage1StepNodeExecution = NodeExecution.builder()
                                                .nodeId(stage1StepNode.getUuid())
                                                .stepType(stepType)
                                                .name(stage1StepNodeExecutionIdentifier)
                                                .identifier(stage1StepNodeExecutionIdentifier)
                                                .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                                .uuid("stage1StepNodeExecutionUuid")
                                                .build();

    PlanNode stageNode2 = PlanNode.builder()
                              .uuid("stageNodeUuid2")
                              .stageFqn("pipeline.stages.s2")
                              .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                              .skipGraphType(SkipType.NOOP)
                              .build();

    PlanNode stage2RollbackStepsNode = PlanNode.builder()
                                           .uuid("stage2RollbackStepsNodeUuid")
                                           .stageFqn("pipeline.stages.s2")
                                           .stepType(stepType)
                                           .skipGraphType(SkipType.NOOP)
                                           .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stageNode1)
                           .planNode(stage1StepsNode)
                           .planNode(stage1StepNode)
                           .planNode(stageNode2)
                           .planNode(stage2RollbackStepsNode)
                           .valid(true)
                           .build();

    List<NodeExecution> nodeExecutionList =
        List.of(nodeExecutionForStageNode1, stage1StepsNodeExecution, stage1StepNodeExecution);
    Stream<NodeExecution> iterator = createCloseableIterator(nodeExecutionList.iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecutionId, List.of("pipeline.stages.s1", "pipeline.stages.s2"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);

    when(planService.fetchNode("planId1", stage1StepsNode.getUuid())).thenReturn(stage1StepsNode);
    when(planService.fetchNode("planId1", stageNode1.getUuid())).thenReturn(stageNode1);
    when(planService.fetchNode("planId1", stage1StepNode.getUuid())).thenReturn(stage1StepNode);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("stage1StepsNodeUuid", "stageNodeUuid1", "stage2RollbackStepsNodeUuid"), POST_EXECUTION_ROLLBACK,
        Collections.singletonList("pipeline.stages.s1"), account);
    List<Node> nodes = transformedPlan.getPlanNodes();
    assertThat(nodes).hasSize(3);
    assertThat(nodes).contains(
        stageNode1.withPreserveInRollbackMode(true), stage1StepsNode.withPreserveInRollbackMode(true));
    List<Node> identityNodes =
        nodes.stream().filter(node -> node.getNodeType() == NodeType.IDENTITY_PLAN_NODE).collect(Collectors.toList());
    assertThat(identityNodes).hasSize(1);
    IdentityPlanNode identityNode = (IdentityPlanNode) identityNodes.get(0);
    assertThat(identityNode.getUuid()).isEqualTo("stage1StepNodeUuid");
    assertThat(identityNode.getSkipGraphType()).isEqualTo(SkipType.SKIP_NODE);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformPlanForPostExecutionRollbackMode_DagPipeline_AdjustsChildrenIds() {
    String prevExecutionId = "prevExecutionId";
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    StepType stagesStepType = StepType.newBuilder()
                                  .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                  .setStepCategory(StepCategory.STAGES)
                                  .build();

    PlanNode stageNode1 = PlanNode.builder()
                              .uuid("stageNodeUuid1")
                              .stageFqn("pipeline.stages.s1")
                              .stepType(stageStepType)
                              .skipGraphType(SkipType.NOOP)
                              .build();
    PlanNode stageNode2 = PlanNode.builder()
                              .uuid("stageNodeUuid2")
                              .stageFqn("pipeline.stages.s2")
                              .stepType(stageStepType)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid1")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid2").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid2")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .build();

    PlanNode stagesNode = PlanNode.builder()
                              .uuid("stagesNodeUuid")
                              .stageFqn("pipeline.stages")
                              .stepType(stagesStepType)
                              .stepParameters(new PmsStepParameters(
                                  Map.of("childrenIds", List.of("stageNodeUuid2"), "logMessage", "dag stages")))
                              .dependencyGraph(dependencyGraph)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    GraphLayoutInfo graphLayoutInfo = GraphLayoutInfo.newBuilder()
                                          .setIsDagEnabled(true)
                                          .setStartingNodeId("stageNodeUuid2")
                                          .addStartingNodeIds("stageNodeUuid2")
                                          .setDependencyGraph(dependencyGraph)
                                          .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stagesNode)
                           .planNode(stageNode1)
                           .planNode(stageNode2)
                           .graphLayoutInfo(graphLayoutInfo)
                           .valid(true)
                           .build();

    NodeExecution stage1Execution = NodeExecution.builder()
                                        .nodeId(stageNode1.getUuid())
                                        .stepType(stageStepType)
                                        .identifier("s1")
                                        .name("s1")
                                        .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                        .uuid("stage1ExecutionUuid")
                                        .build();
    NodeExecution stage2Execution = NodeExecution.builder()
                                        .nodeId(stageNode2.getUuid())
                                        .stepType(stageStepType)
                                        .identifier("s2")
                                        .name("s2")
                                        .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                        .uuid("stage2ExecutionUuid")
                                        .build();

    Stream<NodeExecution> iterator =
        createCloseableIterator(List.of(stage1Execution, stage2Execution).iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecutionId, List.of("pipeline.stages.s1", "pipeline.stages.s2"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", stageNode1.getUuid())).thenReturn(stageNode1);
    when(planService.fetchNode("planId1", stageNode2.getUuid())).thenReturn(stageNode2);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    mockDagPostExecutionRollbackGateEnabled(prevExecutionId);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("combinedRollbackNodeUuid"), POST_EXECUTION_ROLLBACK, List.of("pipeline.stages.s2"), account);

    PlanNode transformedStagesNode = transformedPlan.getPlanNodes()
                                         .stream()
                                         .filter(PlanNode.class ::isInstance)
                                         .map(PlanNode.class ::cast)
                                         .filter(node -> node.getUuid().equals("stagesNodeUuid"))
                                         .findFirst()
                                         .orElseThrow();
    PmsStepParameters stagesStepParameters = (PmsStepParameters) transformedStagesNode.getStepParameters();
    assertThat((List<String>) stagesStepParameters.get("childrenIds")).containsExactly("stageNodeUuid2");
    assertThat(transformedPlan.getGraphLayoutInfo().getStartingNodeIdsList()).containsExactly("stageNodeUuid2");
    assertThat(transformedPlan.getGraphLayoutInfo().getStartingNodeId()).isEqualTo("stageNodeUuid2");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void
  testTransformPlanForPostExecutionRollbackMode_DagPipeline_AdjustsChildrenIdsWithRollbackStepPreserveIds() {
    String prevExecutionId = "prevExecutionId";
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    StepType stagesStepType = StepType.newBuilder()
                                  .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                  .setStepCategory(StepCategory.STAGES)
                                  .build();
    StepType stepType = StepType.newBuilder().setStepCategory(StepCategory.STEP).build();

    PlanNode stageNode1 = PlanNode.builder()
                              .uuid("stageNodeUuid1")
                              .stageFqn("pipeline.stages.s1")
                              .stepType(stageStepType)
                              .skipGraphType(SkipType.NOOP)
                              .build();
    PlanNode stageNode2 = PlanNode.builder()
                              .uuid("stageNodeUuid2")
                              .stageFqn("pipeline.stages.s2")
                              .stepType(stageStepType)
                              .skipGraphType(SkipType.NOOP)
                              .build();
    PlanNode deployStepNode = PlanNode.builder()
                                  .uuid("deployStepNodeUuid")
                                  .stageFqn("pipeline.stages.s1")
                                  .stepType(stepType)
                                  .advisorObtainmentsForExecutionMode(Collections.singletonMap(POST_EXECUTION_ROLLBACK,
                                      Collections.singletonList(AdviserObtainment.newBuilder().build())))
                                  .skipGraphType(SkipType.NOOP)
                                  .build();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid1")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid2").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid2")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .build();

    PlanNode stagesNode = PlanNode.builder()
                              .uuid("stagesNodeUuid")
                              .stageFqn("pipeline.stages")
                              .stepType(stagesStepType)
                              .stepParameters(new PmsStepParameters(
                                  Map.of("childrenIds", List.of("stageNodeUuid2"), "logMessage", "dag stages")))
                              .dependencyGraph(dependencyGraph)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    GraphLayoutInfo graphLayoutInfo = GraphLayoutInfo.newBuilder()
                                          .setIsDagEnabled(true)
                                          .setStartingNodeId("stageNodeUuid2")
                                          .addStartingNodeIds("stageNodeUuid2")
                                          .setDependencyGraph(dependencyGraph)
                                          .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stagesNode)
                           .planNode(stageNode1)
                           .planNode(stageNode2)
                           .planNode(deployStepNode)
                           .graphLayoutInfo(graphLayoutInfo)
                           .valid(true)
                           .build();

    NodeExecution stage1Execution = NodeExecution.builder()
                                        .nodeId(stageNode1.getUuid())
                                        .stepType(stageStepType)
                                        .identifier("s1")
                                        .name("s1")
                                        .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                        .uuid("stage1ExecutionUuid")
                                        .build();
    NodeExecution stage2Execution = NodeExecution.builder()
                                        .nodeId(stageNode2.getUuid())
                                        .stepType(stageStepType)
                                        .identifier("s2")
                                        .name("s2")
                                        .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                        .uuid("stage2ExecutionUuid")
                                        .build();
    NodeExecution deployStepExecution = NodeExecution.builder()
                                            .nodeId(deployStepNode.getUuid())
                                            .stepType(stepType)
                                            .identifier("rolloutDeployment")
                                            .name("rolloutDeployment")
                                            .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                            .uuid("deployStepExecutionUuid")
                                            .build();

    Stream<NodeExecution> iterator =
        createCloseableIterator(List.of(stage1Execution, stage2Execution, deployStepExecution).iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecutionId, List.of("pipeline.stages.s1", "pipeline.stages.s2"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", stageNode1.getUuid())).thenReturn(stageNode1);
    when(planService.fetchNode("planId1", stageNode2.getUuid())).thenReturn(stageNode2);
    when(planService.fetchNode("planId1", deployStepNode.getUuid())).thenReturn(deployStepNode);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    mockDagPostExecutionRollbackGateEnabled(prevExecutionId);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("combinedRollbackNodeUuid"), POST_EXECUTION_ROLLBACK, List.of("pipeline.stages.s1"), account);

    PlanNode transformedStagesNode = transformedPlan.getPlanNodes()
                                         .stream()
                                         .filter(PlanNode.class ::isInstance)
                                         .map(PlanNode.class ::cast)
                                         .filter(node -> node.getUuid().equals("stagesNodeUuid"))
                                         .findFirst()
                                         .orElseThrow();
    PmsStepParameters stagesStepParameters = (PmsStepParameters) transformedStagesNode.getStepParameters();
    assertThat((List<String>) stagesStepParameters.get("childrenIds")).containsExactly("stageNodeUuid1");

    IdentityPlanNode deployStepIdentity = transformedPlan.getPlanNodes()
                                              .stream()
                                              .filter(node -> node.getNodeType() == NodeType.IDENTITY_PLAN_NODE)
                                              .map(IdentityPlanNode.class ::cast)
                                              .filter(node -> node.getUuid().equals("deployStepNodeUuid"))
                                              .findFirst()
                                              .orElseThrow();
    assertThat(deployStepIdentity.getUseAdviserObtainments()).isTrue();
    assertThat(deployStepIdentity.getAdviserObtainments()).hasSize(1);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformPlanForPostExecutionRollbackMode_DagPipeline_SkipsAdjustWhenFeatureFlagDisabled() {
    String prevExecutionId = "prevExecutionId";
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    StepType stagesStepType = StepType.newBuilder()
                                  .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                  .setStepCategory(StepCategory.STAGES)
                                  .build();

    PlanNode stageNode1 = PlanNode.builder()
                              .uuid("stageNodeUuid1")
                              .stageFqn("pipeline.stages.s1")
                              .stepType(stageStepType)
                              .skipGraphType(SkipType.NOOP)
                              .build();
    PlanNode stageNode2 = PlanNode.builder()
                              .uuid("stageNodeUuid2")
                              .stageFqn("pipeline.stages.s2")
                              .stepType(stageStepType)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid1")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid2").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid2")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .build();

    PlanNode stagesNode = PlanNode.builder()
                              .uuid("stagesNodeUuid")
                              .stageFqn("pipeline.stages")
                              .stepType(stagesStepType)
                              .stepParameters(new PmsStepParameters(
                                  Map.of("childrenIds", List.of("stageNodeUuid2"), "logMessage", "dag stages")))
                              .dependencyGraph(dependencyGraph)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    GraphLayoutInfo graphLayoutInfo = GraphLayoutInfo.newBuilder()
                                          .setIsDagEnabled(true)
                                          .setStartingNodeId("stageNodeUuid2")
                                          .addStartingNodeIds("stageNodeUuid2")
                                          .setDependencyGraph(dependencyGraph)
                                          .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stagesNode)
                           .planNode(stageNode1)
                           .planNode(stageNode2)
                           .graphLayoutInfo(graphLayoutInfo)
                           .valid(true)
                           .build();

    NodeExecution stage1Execution = NodeExecution.builder()
                                        .nodeId(stageNode1.getUuid())
                                        .stepType(stageStepType)
                                        .identifier("s1")
                                        .name("s1")
                                        .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                        .uuid("stage1ExecutionUuid")
                                        .build();
    NodeExecution stage2Execution = NodeExecution.builder()
                                        .nodeId(stageNode2.getUuid())
                                        .stepType(stageStepType)
                                        .identifier("s2")
                                        .name("s2")
                                        .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                        .uuid("stage2ExecutionUuid")
                                        .build();

    Stream<NodeExecution> iterator =
        createCloseableIterator(List.of(stage1Execution, stage2Execution).iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecutionId, List.of("pipeline.stages.s1", "pipeline.stages.s2"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", stageNode1.getUuid())).thenReturn(stageNode1);
    when(planService.fetchNode("planId1", stageNode2.getUuid())).thenReturn(stageNode2);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)).thenReturn(false);
    when(planExecutionService.getWithFieldsIncludedOptional(eq(prevExecutionId), any()))
        .thenReturn(Optional.of(
            PlanExecution.builder().metadata(ExecutionMetadata.newBuilder().setEnableDAG(true).build()).build()));

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("combinedRollbackNodeUuid"), POST_EXECUTION_ROLLBACK, List.of("pipeline.stages.s2"), account);

    PlanNode transformedStagesNode = transformedPlan.getPlanNodes()
                                         .stream()
                                         .filter(PlanNode.class ::isInstance)
                                         .map(PlanNode.class ::cast)
                                         .filter(node -> node.getUuid().equals("stagesNodeUuid"))
                                         .findFirst()
                                         .orElseThrow();
    PmsStepParameters stagesStepParameters = (PmsStepParameters) transformedStagesNode.getStepParameters();
    assertThat((List<String>) stagesStepParameters.get("childrenIds")).containsExactly("stageNodeUuid2");
    assertThat(transformedPlan.getGraphLayoutInfo().getStartingNodeIdsList()).containsExactly("stageNodeUuid2");
    assertThat(transformedStagesNode.getDependencyGraph().getEntriesCount()).isEqualTo(2);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformPlanForPostExecutionRollbackMode_ComplexDagFanOutMerge_InstanceRollbackOnParallelBranch() {
    // Fan-out / merge DAG: S1 || S2 -> S3 -> S4. Instance rollback targets S2 only.
    // Reversed DAG root would be S4, but execution must start at S2 (the rollback target).
    String prevExecutionId = "prevExecutionId";
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    StepType stagesStepType = StepType.newBuilder()
                                  .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                  .setStepCategory(StepCategory.STAGES)
                                  .build();

    PlanNode stageNode1 = buildDagStageNode("stageNodeUuid1", "pipeline.stages.s1", stageStepType);
    PlanNode stageNode2 = buildDagStageNode("stageNodeUuid2", "pipeline.stages.s2", stageStepType);
    PlanNode stageNode3 = buildDagStageNode("stageNodeUuid3", "pipeline.stages.s3", stageStepType);
    PlanNode stageNode4 = buildDagStageNode("stageNodeUuid4", "pipeline.stages.s4", stageStepType);

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid4")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid3")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid4").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid1")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid3").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid2")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid3").build())
                            .build())
            .build();

    PlanNode stagesNode = PlanNode.builder()
                              .uuid("stagesNodeUuid")
                              .stageFqn("pipeline.stages")
                              .stepType(stagesStepType)
                              .stepParameters(new PmsStepParameters(
                                  Map.of("childrenIds", List.of("stageNodeUuid4"), "logMessage", "dag stages")))
                              .dependencyGraph(dependencyGraph)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    GraphLayoutInfo graphLayoutInfo = GraphLayoutInfo.newBuilder()
                                          .setIsDagEnabled(true)
                                          .setStartingNodeId("stageNodeUuid4")
                                          .addStartingNodeIds("stageNodeUuid4")
                                          .setDependencyGraph(dependencyGraph)
                                          .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stagesNode)
                           .planNode(stageNode1)
                           .planNode(stageNode2)
                           .planNode(stageNode3)
                           .planNode(stageNode4)
                           .graphLayoutInfo(graphLayoutInfo)
                           .valid(true)
                           .build();

    List<NodeExecution> priorStageExecutions = List.of(buildStageExecution("stage1ExecutionUuid", stageNode1, "s1"),
        buildStageExecution("stage2ExecutionUuid", stageNode2, "s2"),
        buildStageExecution("stage3ExecutionUuid", stageNode3, "s3"),
        buildStageExecution("stage4ExecutionUuid", stageNode4, "s4"));

    Stream<NodeExecution> iterator = createCloseableIterator(priorStageExecutions.iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecutionId,
            List.of("pipeline.stages.s1", "pipeline.stages.s2", "pipeline.stages.s3", "pipeline.stages.s4"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", stageNode1.getUuid())).thenReturn(stageNode1);
    when(planService.fetchNode("planId1", stageNode2.getUuid())).thenReturn(stageNode2);
    when(planService.fetchNode("planId1", stageNode3.getUuid())).thenReturn(stageNode3);
    when(planService.fetchNode("planId1", stageNode4.getUuid())).thenReturn(stageNode4);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    mockDagPostExecutionRollbackGateEnabled(prevExecutionId);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("combinedRollbackNodeUuid"), POST_EXECUTION_ROLLBACK, List.of("pipeline.stages.s2"), account);

    PlanNode transformedStagesNode = transformedPlan.getPlanNodes()
                                         .stream()
                                         .filter(PlanNode.class ::isInstance)
                                         .map(PlanNode.class ::cast)
                                         .filter(node -> node.getUuid().equals("stagesNodeUuid"))
                                         .findFirst()
                                         .orElseThrow();
    PmsStepParameters stagesStepParameters = (PmsStepParameters) transformedStagesNode.getStepParameters();
    assertThat((List<String>) stagesStepParameters.get("childrenIds")).containsExactly("stageNodeUuid2");

    Map<String, List<String>> executionGraph =
        DependencyUtils.convertDependencyGraphToMap(transformedStagesNode.getDependencyGraph());
    assertThat(executionGraph.keySet()).containsExactly("stageNodeUuid2");

    Map<String, Node> nodesById =
        transformedPlan.getPlanNodes().stream().collect(Collectors.toMap(Node::getUuid, node -> node));
    assertThat(nodesById.get("stageNodeUuid1")).isInstanceOf(IdentityPlanNode.class);
    assertThat(nodesById.get("stageNodeUuid2")).isInstanceOf(IdentityPlanNode.class);
    assertThat(nodesById.get("stageNodeUuid3")).isInstanceOf(IdentityPlanNode.class);
    assertThat(nodesById.get("stageNodeUuid4")).isInstanceOf(IdentityPlanNode.class);

    List<IdentityPlanNode> identityStageNodes =
        transformedPlan.getPlanNodes()
            .stream()
            .filter(node -> node.getNodeType() == NodeType.IDENTITY_PLAN_NODE)
            .map(IdentityPlanNode.class ::cast)
            .filter(node -> node.getStepType().getStepCategory() == StepCategory.STAGE)
            .collect(Collectors.toList());
    assertThat(identityStageNodes).hasSize(4);
    identityStageNodes.forEach(identityNode -> assertThat(identityNode.getUseAdviserObtainments()).isFalse());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformPlanForPostExecutionRollbackMode_ComplexDagFanOutMerge_RollbackOnForwardLeaf() {
    // Fan-out / merge DAG: S1 || S2 -> S3 -> S4. Post-prod rollback targets S4 (forward leaf).
    String prevExecutionId = "prevExecutionId";
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    StepType stagesStepType = StepType.newBuilder()
                                  .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                  .setStepCategory(StepCategory.STAGES)
                                  .build();

    PlanNode stageNode1 = buildDagStageNode("stageNodeUuid1", "pipeline.stages.s1", stageStepType);
    PlanNode stageNode2 = buildDagStageNode("stageNodeUuid2", "pipeline.stages.s2", stageStepType);
    PlanNode stageNode3 = buildDagStageNode("stageNodeUuid3", "pipeline.stages.s3", stageStepType);
    PlanNode stageNode4 = buildDagStageNode("stageNodeUuid4", "pipeline.stages.s4", stageStepType);

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid4")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid3")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid4").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid1")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid3").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid2")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid3").build())
                            .build())
            .build();

    PlanNode stagesNode = PlanNode.builder()
                              .uuid("stagesNodeUuid")
                              .stageFqn("pipeline.stages")
                              .stepType(stagesStepType)
                              .stepParameters(new PmsStepParameters(
                                  Map.of("childrenIds", List.of("stageNodeUuid4"), "logMessage", "dag stages")))
                              .dependencyGraph(dependencyGraph)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    GraphLayoutInfo graphLayoutInfo = GraphLayoutInfo.newBuilder()
                                          .setIsDagEnabled(true)
                                          .setStartingNodeId("stageNodeUuid4")
                                          .addStartingNodeIds("stageNodeUuid4")
                                          .setDependencyGraph(dependencyGraph)
                                          .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stagesNode)
                           .planNode(stageNode1)
                           .planNode(stageNode2)
                           .planNode(stageNode3)
                           .planNode(stageNode4)
                           .graphLayoutInfo(graphLayoutInfo)
                           .valid(true)
                           .build();

    List<NodeExecution> priorStageExecutions = List.of(buildStageExecution("stage1ExecutionUuid", stageNode1, "s1"),
        buildStageExecution("stage2ExecutionUuid", stageNode2, "s2"),
        buildStageExecution("stage3ExecutionUuid", stageNode3, "s3"),
        buildStageExecution("stage4ExecutionUuid", stageNode4, "s4"));

    Stream<NodeExecution> iterator = createCloseableIterator(priorStageExecutions.iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecutionId,
            List.of("pipeline.stages.s1", "pipeline.stages.s2", "pipeline.stages.s3", "pipeline.stages.s4"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", stageNode1.getUuid())).thenReturn(stageNode1);
    when(planService.fetchNode("planId1", stageNode2.getUuid())).thenReturn(stageNode2);
    when(planService.fetchNode("planId1", stageNode3.getUuid())).thenReturn(stageNode3);
    when(planService.fetchNode("planId1", stageNode4.getUuid())).thenReturn(stageNode4);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    mockDagPostExecutionRollbackGateEnabled(prevExecutionId);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("combinedRollbackNodeUuid"), POST_EXECUTION_ROLLBACK, List.of("pipeline.stages.s4"), account);

    PlanNode transformedStagesNode = transformedPlan.getPlanNodes()
                                         .stream()
                                         .filter(PlanNode.class ::isInstance)
                                         .map(PlanNode.class ::cast)
                                         .filter(node -> node.getUuid().equals("stagesNodeUuid"))
                                         .findFirst()
                                         .orElseThrow();
    PmsStepParameters stagesStepParameters = (PmsStepParameters) transformedStagesNode.getStepParameters();
    assertThat((List<String>) stagesStepParameters.get("childrenIds")).containsExactly("stageNodeUuid4");

    Map<String, List<String>> executionGraph =
        DependencyUtils.convertDependencyGraphToMap(transformedStagesNode.getDependencyGraph());
    assertThat(executionGraph.keySet()).containsExactly("stageNodeUuid4");
    Map<String, List<String>> layoutGraph =
        DependencyUtils.convertDependencyGraphToMap(transformedPlan.getGraphLayoutInfo().getDependencyGraph());
    assertThat(layoutGraph.keySet()).containsExactly("stageNodeUuid4");
    assertThat(DependencyUtils.findRootNodesInDependencyGraphMap(layoutGraph)).containsExactly("stageNodeUuid4");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformPlanForPostExecutionRollbackMode_MultiStageDag_ReversedGraphStartsFromOriginalLeaf() {
    // Original forward graph: S1 -> S2. After YAML reversal the plan graph is S1 depends_on S2 (S2 is root).
    // Multi-stage post-prod rollback preserves both stages; execution must start at S2 (original leaf).
    String prevExecutionId = "prevExecutionId";
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    StepType stagesStepType = StepType.newBuilder()
                                  .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                  .setStepCategory(StepCategory.STAGES)
                                  .build();

    PlanNode stageNode1 = buildDagStageNode("stageNodeUuid1", "pipeline.stages.s1", stageStepType);
    PlanNode stageNode2 = buildDagStageNode("stageNodeUuid2", "pipeline.stages.s2", stageStepType);

    DependencyGraphProto reversedDependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid1")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid2").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid2")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .build();

    PlanNode stagesNode = PlanNode.builder()
                              .uuid("stagesNodeUuid")
                              .stageFqn("pipeline.stages")
                              .stepType(stagesStepType)
                              .stepParameters(new PmsStepParameters(
                                  Map.of("childrenIds", List.of("stageNodeUuid2"), "logMessage", "dag stages")))
                              .dependencyGraph(reversedDependencyGraph)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    GraphLayoutInfo graphLayoutInfo = GraphLayoutInfo.newBuilder()
                                          .setIsDagEnabled(true)
                                          .setStartingNodeId("stageNodeUuid2")
                                          .addStartingNodeIds("stageNodeUuid2")
                                          .setDependencyGraph(reversedDependencyGraph)
                                          .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stagesNode)
                           .planNode(stageNode1)
                           .planNode(stageNode2)
                           .graphLayoutInfo(graphLayoutInfo)
                           .valid(true)
                           .build();

    Stream<NodeExecution> iterator =
        createCloseableIterator(List.of(buildStageExecution("stage1ExecutionUuid", stageNode1, "s1"),
                                        buildStageExecution("stage2ExecutionUuid", stageNode2, "s2"))
                                    .iterator())
            .stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecutionId, List.of("pipeline.stages.s1", "pipeline.stages.s2"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", stageNode1.getUuid())).thenReturn(stageNode1);
    when(planService.fetchNode("planId1", stageNode2.getUuid())).thenReturn(stageNode2);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    mockDagPostExecutionRollbackGateEnabled(prevExecutionId);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("combinedRollbackNodeUuid1", "combinedRollbackNodeUuid2"), POST_EXECUTION_ROLLBACK,
        List.of("pipeline.stages.s1", "pipeline.stages.s2"), account);

    PlanNode transformedStagesNode = transformedPlan.getPlanNodes()
                                         .stream()
                                         .filter(PlanNode.class ::isInstance)
                                         .map(PlanNode.class ::cast)
                                         .filter(node -> node.getUuid().equals("stagesNodeUuid"))
                                         .findFirst()
                                         .orElseThrow();
    PmsStepParameters stagesStepParameters = (PmsStepParameters) transformedStagesNode.getStepParameters();
    assertThat((List<String>) stagesStepParameters.get("childrenIds")).containsExactly("stageNodeUuid2");
    assertThat(transformedPlan.getGraphLayoutInfo().getStartingNodeIdsList()).containsExactly("stageNodeUuid2");
    assertThat(transformedPlan.getGraphLayoutInfo().getStartingNodeId()).isEqualTo("stageNodeUuid2");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformPlanForPostExecutionRollbackMode_DagWithStrategyStage_PrunesUpstreamChain() {
    // Reversed DAG: S4 (root) -> strategy wrapper -> deploy stage. Instance rollback on deploy stage only.
    String prevExecutionId = "prevExecutionId";
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    StepType strategyStepType = StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build();
    StepType stagesStepType = StepType.newBuilder()
                                  .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                  .setStepCategory(StepCategory.STAGES)
                                  .build();

    PlanNode stageNode4 = buildDagStageNode("stageNodeUuid4", "pipeline.stages.s4", stageStepType);
    PlanNode strategyNode = PlanNode.builder()
                                .uuid("strategyNodeUuid")
                                .stageFqn("pipeline.stages.strategy")
                                .stepType(strategyStepType)
                                .skipGraphType(SkipType.NOOP)
                                .build();
    PlanNode deployStageNode = buildDagStageNode("deployStageUuid", "pipeline.stages.strategy.deploy", stageStepType);

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid4")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("strategyNodeUuid")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid4").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("deployStageUuid")
                            .setDependencies(StringArray.newBuilder().addValues("strategyNodeUuid").build())
                            .build())
            .build();

    PlanNode stagesNode = PlanNode.builder()
                              .uuid("stagesNodeUuid")
                              .stageFqn("pipeline.stages")
                              .stepType(stagesStepType)
                              .stepParameters(new PmsStepParameters(
                                  Map.of("childrenIds", List.of("stageNodeUuid4"), "logMessage", "dag stages")))
                              .dependencyGraph(dependencyGraph)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    GraphLayoutInfo graphLayoutInfo = GraphLayoutInfo.newBuilder()
                                          .setIsDagEnabled(true)
                                          .setStartingNodeId("stageNodeUuid4")
                                          .addStartingNodeIds("stageNodeUuid4")
                                          .setDependencyGraph(dependencyGraph)
                                          .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stagesNode)
                           .planNode(stageNode4)
                           .planNode(strategyNode)
                           .planNode(deployStageNode)
                           .graphLayoutInfo(graphLayoutInfo)
                           .valid(true)
                           .build();

    List<NodeExecution> priorStageExecutions = List.of(buildStageExecution("stage4ExecutionUuid", stageNode4, "s4"),
        buildStageExecution("strategyExecutionUuid", strategyNode, "strategy"),
        buildStageExecution("deployExecutionUuid", deployStageNode, "deploy"));

    Stream<NodeExecution> iterator = createCloseableIterator(priorStageExecutions.iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(eq(prevExecutionId),
            eq(List.of("pipeline.stages.s4", "pipeline.stages.strategy.deploy")),
            eq(NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation));
    when(planService.fetchNode("planId1", stageNode4.getUuid())).thenReturn(stageNode4);
    when(planService.fetchNode("planId1", strategyNode.getUuid())).thenReturn(strategyNode);
    when(planService.fetchNode("planId1", deployStageNode.getUuid())).thenReturn(deployStageNode);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    mockDagPostExecutionRollbackGateEnabled(prevExecutionId);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("executionRollbackNodeUuid"), POST_EXECUTION_ROLLBACK, List.of("pipeline.stages.strategy.deploy"),
        account);

    PlanNode transformedStagesNode = transformedPlan.getPlanNodes()
                                         .stream()
                                         .filter(PlanNode.class ::isInstance)
                                         .map(PlanNode.class ::cast)
                                         .filter(node -> node.getUuid().equals("stagesNodeUuid"))
                                         .findFirst()
                                         .orElseThrow();
    PmsStepParameters stagesStepParameters = (PmsStepParameters) transformedStagesNode.getStepParameters();
    assertThat((List<String>) stagesStepParameters.get("childrenIds")).containsExactly("deployStageUuid");

    Map<String, List<String>> executionGraph =
        DependencyUtils.convertDependencyGraphToMap(transformedStagesNode.getDependencyGraph());
    assertThat(executionGraph.keySet()).containsExactly("deployStageUuid");
    assertThat(transformedPlan.getGraphLayoutInfo().getStartingNodeIdsList()).containsExactly("strategyNodeUuid");
    assertThat(transformedPlan.getGraphLayoutInfo().getStartingNodeId()).isEqualTo("strategyNodeUuid");
    assertThat(transformedPlan.getGraphLayoutInfo().getDependencyGraph().getEntriesList()).hasSize(1);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testIsDagPostExecutionRollbackActive_RequiresAllGateConditions() {
    Map<String, List<String>> dependencyGraph = Map.of("a", List.of(), "b", List.of("a"));
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)).thenReturn(true);

    assertThat(RollbackModeExecutionHelper.isDagPostExecutionRollbackActive(
                   account, true, true, dependencyGraph, pmsFeatureFlagHelper))
        .isTrue();

    assertThat(RollbackModeExecutionHelper.isDagPostExecutionRollbackActive(
                   account, false, true, dependencyGraph, pmsFeatureFlagHelper))
        .isFalse();

    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)).thenReturn(false);
    assertThat(RollbackModeExecutionHelper.isDagPostExecutionRollbackActive(
                   account, true, true, dependencyGraph, pmsFeatureFlagHelper))
        .isFalse();

    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)).thenReturn(true);
    assertThat(RollbackModeExecutionHelper.isDagPostExecutionRollbackActive(
                   account, true, false, dependencyGraph, pmsFeatureFlagHelper))
        .isFalse();
    assertThat(
        RollbackModeExecutionHelper.isDagPostExecutionRollbackActive(account, true, true, null, pmsFeatureFlagHelper))
        .isFalse();
    assertThat(RollbackModeExecutionHelper.isDagPostExecutionRollbackActive(
                   account, true, true, Collections.emptyMap(), pmsFeatureFlagHelper))
        .isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformPlanForPostExecutionRollbackMode_NonDagPlan_SkipsDagAdjustWhenPlanNotDagEnabled() {
    // Sequential post-prod rollback: plan has no isDagEnabled flag even if metadata.enableDAG is true.
    String prevExecutionId = "prevExecutionId";
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    StepType stagesStepType = StepType.newBuilder()
                                  .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                  .setStepCategory(StepCategory.STAGES)
                                  .build();

    PlanNode stageNode1 = buildDagStageNode("stageNodeUuid1", "pipeline.stages.s1", stageStepType);
    PlanNode stageNode2 = buildDagStageNode("stageNodeUuid2", "pipeline.stages.s2", stageStepType);

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid1")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuid2").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuid2")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .build();

    PlanNode stagesNode = PlanNode.builder()
                              .uuid("stagesNodeUuid")
                              .stageFqn("pipeline.stages")
                              .stepType(stagesStepType)
                              .stepParameters(new PmsStepParameters(
                                  Map.of("childrenIds", List.of("stageNodeUuid2"), "logMessage", "stages")))
                              .dependencyGraph(dependencyGraph)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    GraphLayoutInfo graphLayoutInfo = GraphLayoutInfo.newBuilder()
                                          .setIsDagEnabled(false)
                                          .setStartingNodeId("stageNodeUuid2")
                                          .addStartingNodeIds("stageNodeUuid2")
                                          .setDependencyGraph(dependencyGraph)
                                          .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stagesNode)
                           .planNode(stageNode1)
                           .planNode(stageNode2)
                           .graphLayoutInfo(graphLayoutInfo)
                           .valid(true)
                           .build();

    Stream<NodeExecution> iterator =
        createCloseableIterator(List.of(buildStageExecution("stage1ExecutionUuid", stageNode1, "s1"),
                                        buildStageExecution("stage2ExecutionUuid", stageNode2, "s2"))
                                    .iterator())
            .stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecutionId, List.of("pipeline.stages.s1", "pipeline.stages.s2"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", stageNode1.getUuid())).thenReturn(stageNode1);
    when(planService.fetchNode("planId1", stageNode2.getUuid())).thenReturn(stageNode2);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    mockDagPostExecutionRollbackGateEnabled(prevExecutionId);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("combinedRollbackNodeUuid"), POST_EXECUTION_ROLLBACK, List.of("pipeline.stages.s1"), account);

    PlanNode transformedStagesNode = transformedPlan.getPlanNodes()
                                         .stream()
                                         .filter(PlanNode.class ::isInstance)
                                         .map(PlanNode.class ::cast)
                                         .filter(node -> node.getUuid().equals("stagesNodeUuid"))
                                         .findFirst()
                                         .orElseThrow();
    PmsStepParameters stagesStepParameters = (PmsStepParameters) transformedStagesNode.getStepParameters();
    // Non-DAG path: childrenIds / graph unchanged from created plan (no subgraph prune).
    assertThat((List<String>) stagesStepParameters.get("childrenIds")).containsExactly("stageNodeUuid2");
    assertThat(transformedStagesNode.getDependencyGraph().getEntriesCount()).isEqualTo(2);
    assertThat(transformedPlan.getGraphLayoutInfo().getStartingNodeIdsList()).containsExactly("stageNodeUuid2");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testShouldPreserveNode_ForkNodeWithV1StagesFqnIsPreserved() {
    // A FORK node with stageFqn="stages" (V1 pipeline format) should be treated as an ancestor of stage
    // and preserved as a PlanNode (not converted to an IdentityPlanNode) during plan transformation.
    StepType forkStepType = StepType.newBuilder().setStepCategory(StepCategory.FORK).build();
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    String prevExecId = "prevExecId";

    // Create a FORK node with V1-style stageFqn "stages" (not "pipeline.stages")
    PlanNode forkNode = PlanNode.builder()
                            .uuid("forkNodeUuid")
                            .stageFqn("stages")
                            .stepType(forkStepType)
                            .skipGraphType(SkipType.NOOP)
                            .build();

    PlanNode stageNode = PlanNode.builder()
                             .uuid("stageNodeUuid")
                             .stageFqn("stages.s1")
                             .stepType(stageStepType)
                             .skipGraphType(SkipType.NOOP)
                             .build();

    Plan createdPlan = Plan.builder().planNode(forkNode).planNode(stageNode).valid(true).build();

    NodeExecution stageNodeExecution = NodeExecution.builder()
                                           .nodeId(stageNode.getUuid())
                                           .stepType(stageStepType)
                                           .name("s1")
                                           .identifier("s1")
                                           .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                           .uuid("stageNodeExecUuid")
                                           .build();

    Stream<NodeExecution> iterator =
        createCloseableIterator(Collections.singletonList(stageNodeExecution).iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(
            prevExecId, List.of("stages.s1"), NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", stageNode.getUuid())).thenReturn(stageNode);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(
        createdPlan, prevExecId, List.of("stageNodeUuid"), PIPELINE_ROLLBACK, null, account);

    List<Node> nodes = transformedPlan.getPlanNodes();

    // The FORK node with stageFqn="stages" must be preserved as a PlanNode, not replaced by an IdentityPlanNode
    Optional<Node> preservedForkNode = nodes.stream().filter(n -> n.getUuid().equals("forkNodeUuid")).findFirst();
    assertThat(preservedForkNode).isPresent();
    assertThat(preservedForkNode.get()).isInstanceOf(PlanNode.class);
    assertThat(((PlanNode) preservedForkNode.get()).isPreserveInRollbackMode()).isTrue();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testIsAncestorOfStage_DynamicStageV1IsTreatedAsAncestor() {
    // A node with step type DYNAMIC_STAGE_V1 and StepCategory.STAGE should be treated as an ancestor of stage
    // and preserved as a PlanNode during plan transformation, just like DYNAMIC_STAGE nodes are.
    StepType dynamicStageV1StepType = StepType.newBuilder()
                                          .setType(OrchestrationStepTypes.DYNAMIC_STAGE_V1)
                                          .setStepCategory(StepCategory.STAGE)
                                          .build();
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    String prevExecId = "prevExecId";

    PlanNode dynamicStageV1Node = PlanNode.builder()
                                      .uuid("dynamicStageV1Uuid")
                                      .stageFqn("pipeline.stages.dynamicV1")
                                      .stepType(dynamicStageV1StepType)
                                      .skipGraphType(SkipType.NOOP)
                                      .build();

    PlanNode regularStageNode = PlanNode.builder()
                                    .uuid("regularStageUuid")
                                    .stageFqn("pipeline.stages.s1")
                                    .stepType(stageStepType)
                                    .skipGraphType(SkipType.NOOP)
                                    .build();

    Plan createdPlan = Plan.builder().planNode(dynamicStageV1Node).planNode(regularStageNode).valid(true).build();

    NodeExecution regularStageExecution = NodeExecution.builder()
                                              .nodeId(regularStageNode.getUuid())
                                              .stepType(stageStepType)
                                              .name("s1")
                                              .identifier("s1")
                                              .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
                                              .uuid("regularStageExecUuid")
                                              .build();

    Stream<NodeExecution> iterator =
        createCloseableIterator(Collections.singletonList(regularStageExecution).iterator()).stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecId, List.of("pipeline.stages.dynamicV1", "pipeline.stages.s1"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", regularStageNode.getUuid())).thenReturn(regularStageNode);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(
        createdPlan, prevExecId, List.of("regularStageUuid"), PIPELINE_ROLLBACK, null, account);

    List<Node> nodes = transformedPlan.getPlanNodes();

    // The DYNAMIC_STAGE_V1 node must be preserved as a PlanNode (ancestor of stage) rather than IdentityPlanNode
    Optional<Node> preservedDynamicV1Node =
        nodes.stream().filter(n -> n.getUuid().equals("dynamicStageV1Uuid")).findFirst();
    assertThat(preservedDynamicV1Node).isPresent();
    assertThat(preservedDynamicV1Node.get()).isInstanceOf(PlanNode.class);
    assertThat(((PlanNode) preservedDynamicV1Node.get()).isPreserveInRollbackMode()).isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void
  testTransformPlanForPostExecutionRollbackMode_DagStageWithStepLevelStrategy_ExcludesStepStrategyFromChildren() {
    String prevExecutionId = "prevExecutionId";
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    // Matches production StrategyStep.STEP_TYPE — no SubCategory is set on real strategy nodes.
    StepType stepLevelStrategyStepType = StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build();
    StepType stagesStepType = StepType.newBuilder()
                                  .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                  .setStepCategory(StepCategory.STAGES)
                                  .build();

    PlanNode stageNode1 = buildDagStageNode("stageNodeUuid1", "pipeline.stages.s1", stageStepType);
    // Step-level strategy sitting inside rollbackSteps of s1 — same stageFqn as s1.
    PlanNode stepLevelStrategyNode = PlanNode.builder()
                                         .uuid("stepStrategyUuid")
                                         .stageFqn("pipeline.stages.s1")
                                         .stepType(stepLevelStrategyStepType)
                                         .skipGraphType(SkipType.NOOP)
                                         .build();

    DependencyGraphProto dependencyGraph = DependencyGraphProto.newBuilder()
                                               .addEntries(DependencyEntry.newBuilder()
                                                               .setNodeId("stageNodeUuid1")
                                                               .setDependencies(StringArray.newBuilder().build())
                                                               .build())
                                               .build();

    PlanNode stagesNode = PlanNode.builder()
                              .uuid("stagesNodeUuid")
                              .stageFqn("pipeline.stages")
                              .stepType(stagesStepType)
                              .stepParameters(new PmsStepParameters(
                                  Map.of("childrenIds", List.of("stageNodeUuid1"), "logMessage", "dag stages")))
                              .dependencyGraph(dependencyGraph)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    GraphLayoutInfo graphLayoutInfo = GraphLayoutInfo.newBuilder()
                                          .setIsDagEnabled(true)
                                          .setStartingNodeId("stageNodeUuid1")
                                          .addStartingNodeIds("stageNodeUuid1")
                                          .setDependencyGraph(dependencyGraph)
                                          .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stagesNode)
                           .planNode(stageNode1)
                           .planNode(stepLevelStrategyNode)
                           .graphLayoutInfo(graphLayoutInfo)
                           .valid(true)
                           .build();

    Stream<NodeExecution> iterator =
        createCloseableIterator(List.of(buildStageExecution("stage1ExecutionUuid", stageNode1, "s1")).iterator())
            .stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(
            prevExecutionId, List.of("pipeline.stages.s1"), NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", stageNode1.getUuid())).thenReturn(stageNode1);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    mockDagPostExecutionRollbackGateEnabled(prevExecutionId);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("combinedRollbackNodeUuid"), POST_EXECUTION_ROLLBACK, List.of("pipeline.stages.s1"), account);

    PlanNode transformedStagesNode = transformedPlan.getPlanNodes()
                                         .stream()
                                         .filter(PlanNode.class ::isInstance)
                                         .map(PlanNode.class ::cast)
                                         .filter(node -> node.getUuid().equals("stagesNodeUuid"))
                                         .findFirst()
                                         .orElseThrow();
    PmsStepParameters stagesStepParameters = (PmsStepParameters) transformedStagesNode.getStepParameters();
    // childrenIds must contain only the actual stage uuid — never the step-level strategy uuid.
    assertThat((List<String>) stagesStepParameters.get("childrenIds")).containsExactly("stageNodeUuid1");
    assertThat((List<String>) stagesStepParameters.get("childrenIds")).doesNotContain("stepStrategyUuid");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testTransformPlanForPostExecutionRollbackMode_DagFanOutBeforeDeploy_FocusesLayoutOnRollbackTarget() {
    // Forward: custom_1 -> (s2 || s3), s2 -> deploy. Post-prod rollback on deploy shows deploy only.
    String prevExecutionId = "prevExecutionId";
    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    StepType stagesStepType = StepType.newBuilder()
                                  .setType(OrchestrationStepTypes.STAGES_STEP_WITH_DEPENDENCY)
                                  .setStepCategory(StepCategory.STAGES)
                                  .build();

    PlanNode custom1Node = buildDagStageNode("stageNodeUuidCustom1", "pipeline.stages.custom_1", stageStepType);
    PlanNode s2Node = buildDagStageNode("stageNodeUuidS2", "pipeline.stages.s2", stageStepType);
    PlanNode s3Node = buildDagStageNode("stageNodeUuidS3", "pipeline.stages.s3", stageStepType);
    PlanNode deployNode = buildDagStageNode("stageNodeUuidDeploy", "pipeline.stages.deploy", stageStepType);

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuidDeploy")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuidS2")
                            .setDependencies(StringArray.newBuilder().addValues("stageNodeUuidDeploy").build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId("stageNodeUuidS3")
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(
                DependencyEntry.newBuilder()
                    .setNodeId("stageNodeUuidCustom1")
                    .setDependencies(
                        StringArray.newBuilder().addValues("stageNodeUuidS2").addValues("stageNodeUuidS3").build())
                    .build())
            .build();

    PlanNode stagesNode = PlanNode.builder()
                              .uuid("stagesNodeUuid")
                              .stageFqn("pipeline.stages")
                              .stepType(stagesStepType)
                              .stepParameters(new PmsStepParameters(
                                  Map.of("childrenIds", List.of("stageNodeUuidDeploy"), "logMessage", "dag stages")))
                              .dependencyGraph(dependencyGraph)
                              .skipGraphType(SkipType.NOOP)
                              .build();

    GraphLayoutInfo graphLayoutInfo = GraphLayoutInfo.newBuilder()
                                          .setIsDagEnabled(true)
                                          .setStartingNodeId("stageNodeUuidDeploy")
                                          .addStartingNodeIds("stageNodeUuidDeploy")
                                          .setDependencyGraph(dependencyGraph)
                                          .build();

    Plan createdPlan = Plan.builder()
                           .planNode(stagesNode)
                           .planNode(custom1Node)
                           .planNode(s2Node)
                           .planNode(s3Node)
                           .planNode(deployNode)
                           .graphLayoutInfo(graphLayoutInfo)
                           .valid(true)
                           .build();

    Stream<NodeExecution> iterator =
        createCloseableIterator(List.of(buildStageExecution("custom1ExecutionUuid", custom1Node, "custom_1"),
                                        buildStageExecution("s2ExecutionUuid", s2Node, "s2"),
                                        buildStageExecution("s3ExecutionUuid", s3Node, "s3"),
                                        buildStageExecution("deployExecutionUuid", deployNode, "deploy"))
                                    .iterator())
            .stream();
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchNodeExecutionsForGivenStageFQNs(prevExecutionId,
            List.of("pipeline.stages.custom_1", "pipeline.stages.s2", "pipeline.stages.s3", "pipeline.stages.deploy"),
            NodeProjectionUtils.fieldsForRollbackIdentityNodeCreation);
    when(planService.fetchNode("planId1", custom1Node.getUuid())).thenReturn(custom1Node);
    when(planService.fetchNode("planId1", s2Node.getUuid())).thenReturn(s2Node);
    when(planService.fetchNode("planId1", s3Node.getUuid())).thenReturn(s3Node);
    when(planService.fetchNode("planId1", deployNode.getUuid())).thenReturn(deployNode);
    when(pmsFeatureFlagHelper.isEnabled(account, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK))
        .thenReturn(true);
    mockDagPostExecutionRollbackGateEnabled(prevExecutionId);

    Plan transformedPlan = rollbackModeExecutionHelper.transformPlanForRollbackMode(createdPlan, prevExecutionId,
        List.of("combinedRollbackNodeUuid"), POST_EXECUTION_ROLLBACK, List.of("pipeline.stages.deploy"), account);

    PlanNode transformedStagesNode = transformedPlan.getPlanNodes()
                                         .stream()
                                         .filter(PlanNode.class ::isInstance)
                                         .map(PlanNode.class ::cast)
                                         .filter(node -> node.getUuid().equals("stagesNodeUuid"))
                                         .findFirst()
                                         .orElseThrow();
    Map<String, List<String>> prunedGraph =
        DependencyUtils.convertDependencyGraphToMap(transformedStagesNode.getDependencyGraph());
    assertThat(prunedGraph.keySet()).containsExactly("stageNodeUuidDeploy");
    assertThat((List<String>) ((PmsStepParameters) transformedStagesNode.getStepParameters()).get("childrenIds"))
        .containsExactly("stageNodeUuidDeploy");
    assertThat(transformedPlan.getGraphLayoutInfo().getDependencyGraph().getEntriesList()).hasSize(1);
    assertThat(transformedPlan.getGraphLayoutInfo().getStartingNodeIdsList()).containsExactly("stageNodeUuidDeploy");
    assertThat(transformedPlan.getGraphLayoutInfo().getStartingNodeId()).isEqualTo("stageNodeUuidDeploy");
  }

  private static PlanNode buildDagStageNode(String uuid, String stageFqn, StepType stageStepType) {
    return PlanNode.builder()
        .uuid(uuid)
        .stageFqn(stageFqn)
        .stepType(stageStepType)
        .skipGraphType(SkipType.NOOP)
        .build();
  }

  private static NodeExecution buildStageExecution(String executionUuid, PlanNode stageNode, String identifier) {
    return NodeExecution.builder()
        .nodeId(stageNode.getUuid())
        .stepType(stageNode.getStepType())
        .identifier(identifier)
        .name(identifier)
        .ambiance(Ambiance.newBuilder().setPlanId("planId1").build())
        .uuid(executionUuid)
        .build();
  }

  public static <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }
}