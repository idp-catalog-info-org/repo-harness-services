/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotation.RecasterAlias;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.eraro.Level;
import io.harness.execution.NodeExecution;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ForMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PostExecutionRollbackInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagService;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

public class ExecutionSummaryUpdateUtilsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String STAGE1 = "stage1";
  private static final String STAGE = "STAGE";
  private static final String STAGE_VALUE = "stageValue";
  private static final String STRATEGY1 = "strategy1";
  private static final String STRATEGY = "STRATEGY";
  private static final String STEP_VALUE = "stepValue";
  private static final String TESTING = "testing";

  PlanNode pipelinePlanNode;
  PlanNode stagePlanNode;
  PlanNode stagesPlanNode;
  IdentityPlanNode stageIdentityPlanNode;

  PlanNode strategyPlanNode;
  PlanNode stepPlanNode;
  PlanNode stepStrategyPlanNode;
  @InjectMocks ExecutionSummaryUpdateUtils executionSummaryUpdateUtils;
  @Mock NodeExecutionInfoService nodeExecutionInfoService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    when(pmsFeatureFlagService.isEnabled(any(), eq(FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)))
        .thenReturn(false);
    pipelinePlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("pipeline")
            .stepType(StepType.newBuilder().setType("PIPELINE").setStepCategory(StepCategory.PIPELINE).build())
            .identifier("pipeline")
            .skipExpressionChain(false)
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                RecastOrchestrationUtils.toMap(TestStepParameters.builder().param("pipelineValue").build()))))
            .group("PIPELINE")
            .build();
    stagePlanNode = PlanNode.builder()
                        .uuid(generateUuid())
                        .name(STAGE1)
                        .stepType(StepType.newBuilder().setType(STAGE).setStepCategory(StepCategory.STAGE).build())
                        .identifier(STAGE1)
                        .skipExpressionChain(false)
                        .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                            RecastOrchestrationUtils.toMap(TestStepParameters.builder().param(STAGE_VALUE).build()))))
                        .group(STAGE)
                        .build();
    stagesPlanNode = PlanNode.builder()
                         .uuid(generateUuid())
                         .name("stages")
                         .stepType(StepType.newBuilder().setType("STAGES").setStepCategory(StepCategory.STAGES).build())
                         .identifier("stages")
                         .skipExpressionChain(false)
                         .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                             RecastOrchestrationUtils.toMap(TestStepParameters.builder().param(STAGE_VALUE).build()))))
                         .group("STAGES")
                         .build();
    strategyPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name(STRATEGY1)
            .stepType(StepType.newBuilder().setType(STRATEGY).setStepCategory(StepCategory.STRATEGY).build())
            .identifier(STRATEGY1)
            .skipExpressionChain(false)
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                RecastOrchestrationUtils.toMap(TestStepParameters.builder().param(STAGE_VALUE).build()))))
            .group(STRATEGY)
            .build();
    stepPlanNode = PlanNode.builder()
                       .uuid(generateUuid())
                       .name("step1")
                       .stepType(StepType.newBuilder().setType("STEP").setStepCategory(StepCategory.STEP).build())
                       .identifier("step")
                       .skipExpressionChain(false)
                       .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                           RecastOrchestrationUtils.toMap(TestStepParameters.builder().param(STAGE_VALUE).build()))))
                       .group("STEP")
                       .build();
    stepStrategyPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name(STRATEGY1)
            .stepType(StepType.newBuilder().setType(STRATEGY).setStepCategory(StepCategory.STRATEGY).build())
            .identifier(STRATEGY1)
            .skipExpressionChain(false)
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                RecastOrchestrationUtils.toMap(TestStepParameters.builder().param(STEP_VALUE).build()))))
            .group(STRATEGY)
            .build();
    stageIdentityPlanNode =
        IdentityPlanNode.builder()
            .uuid(generateUuid())
            .name("stage1")
            .stepType(StepType.newBuilder().setType(STAGE).setStepCategory(StepCategory.STAGE).build())
            .identifier("stage1")
            .group(STAGE)
            .build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testStageUpdateCriteriaForBarrierStep() {
    PlanNode stepPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("step")
            .stepType(
                StepType.newBuilder().setType(StepSpecTypeConstants.BARRIER).setStepCategory(StepCategory.STEP).build())
            .identifier("step")
            .skipExpressionChain(false)
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toJson(
                RecastOrchestrationUtils.toMap(TestStepParameters.builder().param(STEP_VALUE).build()))))
            .group("STEP")
            .build();
    Ambiance stepAmbiance = Ambiance.newBuilder()
                                .setPlanExecutionId(generateUuid())
                                .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stepPlanNode))
                                .build();
    NodeExecution stepNodeExecution = NodeExecution.builder()
                                          .nodeId(stepPlanNode.getUuid())
                                          .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                          .status(Status.EXPIRED)
                                          .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                          .ambiance(stepAmbiance)
                                          .build();
    doReturn(stepAmbiance).when(nodeExecutionService).getAmbiance(stepNodeExecution);
    Update update = new Update();
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, stepNodeExecution, null);
    assertThat(update.getUpdateObject().keySet().size()).isEqualTo(0);

    // step inside strategy then use runtimeId
    PlanNode stagePlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("stage")
            .stepType(StepType.newBuilder().setType("STAGE").setStepCategory(StepCategory.STAGE).build())
            .build();
    StrategyMetadata strategyMetadata = StrategyMetadata.newBuilder()
                                            .setForMetadata(ForMetadata.newBuilder().setValue("hostName").build())
                                            .setCurrentIteration(1)
                                            .setTotalIterations(1)
                                            .build();
    String stageRuntimeId = "stageRuntimeId";
    io.harness.pms.contracts.ambiance.Level level1 =
        PmsLevelUtils.buildLevelFromNode(stageRuntimeId, stagePlanNode, strategyMetadata, false);
    io.harness.pms.contracts.ambiance.Level level2 = PmsLevelUtils.buildLevelFromNode(generateUuid(), stepPlanNode);
    stepAmbiance =
        Ambiance.newBuilder().setPlanExecutionId(generateUuid()).addLevels(0, level1).addLevels(1, level2).build();
    stepNodeExecution = NodeExecution.builder()
                            .nodeId(stepPlanNode.getUuid())
                            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                            .status(Status.EXPIRED)
                            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                            .ambiance(stepAmbiance)
                            .build();
    doReturn(stepAmbiance).when(nodeExecutionService).getAmbiance(stepNodeExecution);
    update = new Update();
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, stepNodeExecution, null);
    assertThat(update.getUpdateObject().keySet().size()).isEqualTo(1);
    Set<String> stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();
    assertThat(stringSet.size()).isEqualTo(1);
    assertThat(stringSet).containsOnly(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageRuntimeId + ".barrierFound");

    // step having stage and pipeline in the ambiance levels
    stepAmbiance = Ambiance.newBuilder()
                       .setPlanExecutionId(generateUuid())
                       .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
                       .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagePlanNode))
                       .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stepPlanNode))
                       .build();
    stepNodeExecution = NodeExecution.builder()
                            .status(Status.EXPIRED)
                            .ambiance(stepAmbiance)
                            .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                            .build();
    doReturn(stepAmbiance).when(nodeExecutionService).getAmbiance(stepNodeExecution);
    update = new Update();
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, stepNodeExecution, null);
    assertThat(update.getUpdateObject().keySet().size()).isEqualTo(1);
    stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();
    assertThat(stringSet.size()).isEqualTo(1);
    assertThat(stringSet).containsOnly(
        PlanExecutionSummaryKeys.layoutNodeMap + "." + stagePlanNode.getUuid() + ".barrierFound");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testStageUpdateCriteria() {
    Ambiance stageAmbiance = Ambiance.newBuilder()
                                 .setPlanExecutionId(generateUuid())
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagePlanNode))
                                 .build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .status(Status.FAILED)
            .nodeId(stagePlanNode.getUuid())
            .endTs(System.currentTimeMillis())
            .stepType(StepType.newBuilder().setType("test").setStepCategory(StepCategory.STEP).build())
            .ambiance(stageAmbiance)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage(TESTING)
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(GENERAL_ERROR.name())
                                                 .setMessage(TESTING)
                                                 .build())
                             .build())
            .build();
    doReturn(stageAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    Update update = new Update();
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, null);
    String prefixLayoutNodeMap = PlanExecutionSummaryKeys.layoutNodeMap + "." + stagePlanNode.getUuid();
    Set<String> stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();
    assertThat(stringSet).containsOnly(prefixLayoutNodeMap + ".status", prefixLayoutNodeMap + ".createdAt",
        prefixLayoutNodeMap + ".startTs", prefixLayoutNodeMap + ".nodeRunInfo", prefixLayoutNodeMap + ".endTs",
        prefixLayoutNodeMap + ".failureInfo", prefixLayoutNodeMap + ".failureInfoDTO",
        prefixLayoutNodeMap + ".nodeExecutionId", prefixLayoutNodeMap + ".executionInputConfigured",
        prefixLayoutNodeMap + ".name", prefixLayoutNodeMap + ".nodeIdentifier",
        prefixLayoutNodeMap + ".isRollbackStageNode");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testStageUpdateCriteriaWithStepStrategy() {
    Ambiance stageAmbiance = Ambiance.newBuilder()
                                 .setPlanExecutionId(generateUuid())
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesPlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagePlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), strategyPlanNode))
                                 .build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .status(Status.FAILED)
            .nodeId(strategyPlanNode.getUuid())
            .endTs(System.currentTimeMillis())
            .stepType(StepType.newBuilder().setType(STRATEGY).setStepCategory(StepCategory.STRATEGY).build())
            .ambiance(stageAmbiance)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage(TESTING)
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(GENERAL_ERROR.name())
                                                 .setMessage(TESTING)
                                                 .build())
                             .build())
            .build();
    Update update = new Update();
    doReturn(stageAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, null);
    assertThat(update.getUpdateObject().isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testParallelNodeStatusUpdate() {
    // Test that parallel (FORK) nodes get their status updated in the layout graph
    PlanNode parallelPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("parallel")
            .stepType(StepType.newBuilder().setType("FORK").setStepCategory(StepCategory.FORK).build())
            .identifier("parallel")
            .skipExpressionChain(false)
            .group("STAGE")
            .build();

    Ambiance parallelAmbiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesPlanNode))
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), parallelPlanNode))
            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .status(Status.RUNNING)
            .nodeId(parallelPlanNode.getUuid())
            .startTs(System.currentTimeMillis())
            .stepType(StepType.newBuilder().setType("FORK").setStepCategory(StepCategory.FORK).build())
            .ambiance(parallelAmbiance)
            .build();
    doReturn(parallelAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    Update update = new Update();
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, null);
    String prefixLayoutNodeMap = PlanExecutionSummaryKeys.layoutNodeMap + "." + parallelPlanNode.getUuid();
    Set<String> stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();
    // Verify that the parallel node status and other fields are updated (V1 uses isStageOrParallelStageNode)
    assertThat(stringSet).contains(prefixLayoutNodeMap + ".status");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testStageUpdateStageInsideStrategy() {
    Ambiance stageAmbiance = Ambiance.newBuilder()
                                 .setPlanExecutionId(generateUuid())
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesPlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), strategyPlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagePlanNode))
                                 .build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(AmbianceUtils.obtainCurrentRuntimeId(stageAmbiance))
            .status(Status.FAILED)
            .nodeId(strategyPlanNode.getUuid())
            .endTs(System.currentTimeMillis())
            .stepType(StepType.newBuilder().setType(STRATEGY).setStepCategory(StepCategory.STRATEGY).build())
            .ambiance(stageAmbiance)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage(TESTING)
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(GENERAL_ERROR.name())
                                                 .setMessage(TESTING)
                                                 .build())
                             .build())
            .build();
    doReturn(stageAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    Update update = new Update();
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, null);
    String prefixLayoutNodeMap = PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getUuid();
    Set<String> stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();
    assertThat(stringSet).containsOnly(prefixLayoutNodeMap + ".status", prefixLayoutNodeMap + ".createdAt",
        prefixLayoutNodeMap + ".startTs", prefixLayoutNodeMap + ".nodeRunInfo", prefixLayoutNodeMap + ".endTs",
        prefixLayoutNodeMap + ".failureInfo", prefixLayoutNodeMap + ".failureInfoDTO",
        prefixLayoutNodeMap + ".nodeExecutionId", prefixLayoutNodeMap + ".executionInputConfigured",
        prefixLayoutNodeMap + ".nodeIdentifier", prefixLayoutNodeMap + ".name",
        prefixLayoutNodeMap + ".strategyMetadata", prefixLayoutNodeMap + ".isRollbackStageNode");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testChildrenCountUpdateForWrapperNodes() {
    // Test that childrenCount is added to update when > 0 for wrapper nodes (NG_FORK)
    PlanNode parallelPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("parallel")
            .stepType(StepType.newBuilder().setType("NG_FORK").setStepCategory(StepCategory.FORK).build())
            .identifier("parallel")
            .skipExpressionChain(false)
            .group("STAGE")
            .build();

    Ambiance parallelAmbiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesPlanNode))
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), parallelPlanNode))
            .build();

    // NodeExecution with childrenCount > 0
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .status(Status.RUNNING)
            .nodeId(parallelPlanNode.getUuid())
            .startTs(System.currentTimeMillis())
            .stepType(StepType.newBuilder().setType("NG_FORK").setStepCategory(StepCategory.FORK).build())
            .ambiance(parallelAmbiance)
            .childrenCount(3L)
            .build();
    doReturn(parallelAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);

    Update update = new Update();
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, null);

    String prefixLayoutNodeMap = PlanExecutionSummaryKeys.layoutNodeMap + "." + parallelPlanNode.getUuid();
    Set<String> stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();

    // Verify that childrenCount is included in the update
    assertThat(stringSet).contains(prefixLayoutNodeMap + ".childrenCount");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testChildrenCountNotAddedWhenZero() {
    // Test that childrenCount is NOT added to update when == 0
    PlanNode parallelPlanNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("parallel")
            .stepType(StepType.newBuilder().setType("NG_FORK").setStepCategory(StepCategory.FORK).build())
            .identifier("parallel")
            .skipExpressionChain(false)
            .group("STAGE")
            .build();

    Ambiance parallelAmbiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesPlanNode))
            .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), parallelPlanNode))
            .build();

    // NodeExecution with childrenCount = 0 (default)
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .status(Status.RUNNING)
            .nodeId(parallelPlanNode.getUuid())
            .startTs(System.currentTimeMillis())
            .stepType(StepType.newBuilder().setType("NG_FORK").setStepCategory(StepCategory.FORK).build())
            .ambiance(parallelAmbiance)
            .build();
    doReturn(parallelAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);

    Update update = new Update();
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, null);

    String prefixLayoutNodeMap = PlanExecutionSummaryKeys.layoutNodeMap + "." + parallelPlanNode.getUuid();
    Set<String> stringSet = ((Document) update.getUpdateObject().get("$set")).keySet();

    // Verify that childrenCount is NOT included in the update when it's 0
    assertThat(stringSet).doesNotContain(prefixLayoutNodeMap + ".childrenCount");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testUpdateNextIdOfStageBeforePipelineRollback() {
    String rollbackNodeId = generateUuid();
    String previousStageNodeId = generateUuid();
    Update update = new Update();
    executionSummaryUpdateUtils.updateNextIdOfStageBeforePipelineRollback(update, rollbackNodeId, previousStageNodeId);
    Document setObjects = (Document) update.getUpdateObject().get("$set");
    String expectedKey = PlanExecutionSummaryKeys.layoutNodeMap + "." + previousStageNodeId + ".edgeLayoutList.nextIds";
    assertThat(setObjects).containsKey(expectedKey);
    assertThat(setObjects.get(expectedKey)).isEqualTo(java.util.Collections.singletonList(rollbackNodeId));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testPostExecutionRollbackIdentityStageUpdatesLayoutStatus() {
    String stageSetupId = generateUuid();
    IdentityPlanNode identityStage =
        IdentityPlanNode.builder()
            .uuid(stageSetupId)
            .name("custom")
            .stepType(StepType.newBuilder().setType(STAGE).setStepCategory(StepCategory.STAGE).build())
            .identifier("custom")
            .group(STAGE)
            .build();

    Ambiance stageAmbiance = Ambiance.newBuilder()
                                 .setPlanExecutionId(generateUuid())
                                 .putSetupAbstractions("accountId", ACCOUNT_ID)
                                 .setMetadata(ExecutionMetadata.newBuilder()
                                                  .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                                  .setEnableDAG(true)
                                                  .build())
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesPlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), identityStage))
                                 .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .nodeId(stageSetupId)
            .status(Status.SKIPPED)
            .endTs(1000L)
            .stepType(StepType.newBuilder().setType(STAGE).setStepCategory(StepCategory.STAGE).build())
            .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
            .ambiance(stageAmbiance)
            .build();
    doReturn(stageAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))
        .thenReturn(true);

    Update update = new Update();
    List<PostExecutionRollbackInfo> rollbackInfos = List.of(PostExecutionRollbackInfo.newBuilder()
                                                                .setPostExecutionRollbackStageId(stageSetupId)
                                                                .setOriginalStageExecutionId(generateUuid())
                                                                .build());
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, rollbackInfos);

    String statusKey = PlanExecutionSummaryKeys.layoutNodeMap + "." + stageSetupId + ".status";
    Document setObjects = (Document) update.getUpdateObject().get("$set");
    assertThat(setObjects).containsKey(statusKey);
    assertThat(setObjects.get(statusKey)).isEqualTo(ExecutionStatus.SKIPPED);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testPostExecutionRollbackUpstreamIdentityStageDoesNotUpdateLayout() {
    String rollbackTargetId = generateUuid();
    String upstreamStageId = generateUuid();
    IdentityPlanNode upstreamStage =
        IdentityPlanNode.builder()
            .uuid(upstreamStageId)
            .name("s2")
            .stepType(StepType.newBuilder().setType(STAGE).setStepCategory(StepCategory.STAGE).build())
            .identifier("s2")
            .group(STAGE)
            .build();

    Ambiance stageAmbiance = Ambiance.newBuilder()
                                 .setPlanExecutionId(generateUuid())
                                 .putSetupAbstractions("accountId", ACCOUNT_ID)
                                 .setMetadata(ExecutionMetadata.newBuilder()
                                                  .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                                  .setEnableDAG(true)
                                                  .build())
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesPlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), upstreamStage))
                                 .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .nodeId(upstreamStageId)
            .status(Status.SKIPPED)
            .stepType(StepType.newBuilder().setType(STAGE).setStepCategory(StepCategory.STAGE).build())
            .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
            .ambiance(stageAmbiance)
            .build();
    doReturn(stageAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))
        .thenReturn(true);

    Update update = new Update();
    List<PostExecutionRollbackInfo> rollbackInfos = List.of(PostExecutionRollbackInfo.newBuilder()
                                                                .setPostExecutionRollbackStageId(rollbackTargetId)
                                                                .setOriginalStageExecutionId(generateUuid())
                                                                .build());
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, rollbackInfos);

    Document setObjects = (Document) update.getUpdateObject().get("$set");
    assertThat(setObjects).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testPostExecutionRollbackWithEnableDagButFfOffUsesSequentialIdentityPath() {
    // enableDAG=true but FF off → sequential update path; identity stages do not get layout status writes.
    String stageSetupId = generateUuid();
    IdentityPlanNode identityStage =
        IdentityPlanNode.builder()
            .uuid(stageSetupId)
            .name("custom")
            .stepType(StepType.newBuilder().setType(STAGE).setStepCategory(StepCategory.STAGE).build())
            .identifier("custom")
            .group(STAGE)
            .build();

    Ambiance stageAmbiance = Ambiance.newBuilder()
                                 .setPlanExecutionId(generateUuid())
                                 .putSetupAbstractions("accountId", ACCOUNT_ID)
                                 .setMetadata(ExecutionMetadata.newBuilder()
                                                  .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
                                                  .setEnableDAG(true)
                                                  .build())
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), pipelinePlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesPlanNode))
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), identityStage))
                                 .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .nodeId(stageSetupId)
            .status(Status.SKIPPED)
            .endTs(1000L)
            .stepType(StepType.newBuilder().setType(STAGE).setStepCategory(StepCategory.STAGE).build())
            .nodeType(NodeType.IDENTITY_PLAN_NODE.name())
            .ambiance(stageAmbiance)
            .build();
    doReturn(stageAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))
        .thenReturn(false);

    Update update = new Update();
    List<PostExecutionRollbackInfo> rollbackInfos = List.of(PostExecutionRollbackInfo.newBuilder()
                                                                .setPostExecutionRollbackStageId(stageSetupId)
                                                                .setOriginalStageExecutionId(generateUuid())
                                                                .build());
    executionSummaryUpdateUtils.addStageUpdateCriteria(update, nodeExecution, rollbackInfos);

    Document setObjects = (Document) update.getUpdateObject().get("$set");
    // Sequential identity path skips status (writeStatusForIdentity=false).
    String statusKey = PlanExecutionSummaryKeys.layoutNodeMap + "." + stageSetupId + ".status";
    assertThat(setObjects).doesNotContainKey(statusKey);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testUpdateDependencyGraphForPipelineRollback() {
    String rollbackNodeId = generateUuid();
    String triggeringStageNodeId = generateUuid();
    Update update = new Update();
    executionSummaryUpdateUtils.updateDependencyGraphForPipelineRollback(update, rollbackNodeId, triggeringStageNodeId);
    Document setObjects = (Document) update.getUpdateObject().get("$set");
    String expectedKey = PlanExecutionSummaryKeys.dependencyGraph + "." + rollbackNodeId;
    assertThat(setObjects).containsKey(expectedKey);
    assertThat(setObjects.get(expectedKey)).isEqualTo(java.util.Collections.singletonList(triggeringStageNodeId));
  }

  @Data
  @Builder
  @RecasterAlias("io.harness.pms.expressions.ExecutionSummaryUpdateUtilsTest$TestStepParameters")
  public static class TestStepParameters implements StepParameters {
    String param;
  }
}
