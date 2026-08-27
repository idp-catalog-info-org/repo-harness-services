/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.constants.OrchestrationConstants;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.plan.DependencyEntry;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.StringArray;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.tools.reflect.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionMapTest extends CategoryTest {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock NodeExecutionsCache nodeExecutionsCache;
  @Mock NodeExecutionInfoService nodeExecutionInfoService;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock PlanExecutionService planExecutionService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFetchExecutionUrl() {
    // Ambiance of stage level without matrix

    String stageSetupId = UUIDGenerator.generateUuid();
    String stageRuntimeId = UUIDGenerator.generateUuid();

    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(getNormalStageLevels(stageRuntimeId, stageSetupId)).build();

    NodeExecutionsCache nodeExecutionsCache = NodeExecutionsCache.builder().build();
    String nodeExecutionId = UUIDGenerator.generateUuid();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, ambiance);
    NodeExecutionMap nodeExecutionMap =
        NodeExecutionMap.builder()
            .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build())
            .nodeExecutionsCache(nodeExecutionsCache)
            .ambiance(ambiance)
            .build();
    Optional<Object> executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s'>", stageSetupId));

    // Stage inside parallel block
    ambiance = Ambiance.newBuilder().addAllLevels(getStageLevelInsideParallel(stageRuntimeId, stageSetupId)).build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, ambiance);
    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build())
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .ambiance(ambiance)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s'>", stageSetupId));

    String stepRuntimeId = UUIDGenerator.generateUuid();
    Ambiance stepAmbiance =
        Ambiance.newBuilder().addAllLevels(getNormalStepLevels(stepRuntimeId, stageRuntimeId, stageSetupId)).build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, stepAmbiance);
    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(stepAmbiance).build())
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .ambiance(stepAmbiance)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s&step=%s'>", stageSetupId, stepRuntimeId));

    // step inside parallel block
    stepAmbiance = Ambiance.newBuilder()
                       .addAllLevels(getStepLevelInsideParallel(stepRuntimeId, stageRuntimeId, stageSetupId))
                       .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, stepAmbiance);

    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(stepAmbiance).build())
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .ambiance(stepAmbiance)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s&step=%s'>", stageSetupId, stepRuntimeId));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFetchParentStepGroup() {
    String nearestStepGroupUuid = UUIDGenerator.generateUuid();
    String parentStepGroupUuid = UUIDGenerator.generateUuid();
    String planExecutionId = UUIDGenerator.generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getNestedStepGroupLevels(parentStepGroupUuid, nearestStepGroupUuid))
                            .setPlanExecutionId(planExecutionId)
                            .build();
    NodeExecution parentNodeExecution = NodeExecution.builder()
                                            .uuid(parentStepGroupUuid)
                                            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                                            .build();
    nodeExecutionsCache.getAmbianceMap().put(nearestStepGroupUuid, ambiance);
    Reflect.on(nodeExecutionsCache).set("nodeExecutionService", nodeExecutionService);
    when(nodeExecutionsCache.fetch(anyString())).thenReturn(parentNodeExecution);
    List<NodeExecution> childrens = new LinkedList<>();
    when(nodeExecutionsCache.fetchChildren(anyString())).thenReturn(childrens);
    NodeExecutionMap nodeExecutionMap =
        NodeExecutionMap.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(nearestStepGroupUuid)
                               .ambiance(ambiance)
                               .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP_GROUP).build())
                               .group("STEP_GROUP")
                               .build())
            .nodeExecutionsCache(nodeExecutionsCache)
            .ambiance(ambiance)
            .build();
    Optional<Object> parentStepGroup = nodeExecutionMap.fetchParentStepGroup("getParentStepGroup");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testFetchExecutionUrlForStrategy() {
    // Ambiance of stage level without matrix

    String stageSetupId = UUIDGenerator.generateUuid();
    String stageRuntimeId = UUIDGenerator.generateUuid();

    NodeExecutionsCache nodeExecutionsCache = NodeExecutionsCache.builder().build();
    String nodeExecutionId = UUIDGenerator.generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .addAllLevels(getStageLevelInsideStrategy(stageRuntimeId, stageSetupId))
            .setStageExecutionId(stageRuntimeId)
            .setMetadata(
                ExecutionMetadata.newBuilder()
                    .putFeatureFlagToValueMap(FeatureName.PIPE_DISABLE_ESCAPE_AMPERSAND_IN_STAGE_EXEC_URL.name(), false)
                    .build())
            .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, ambiance);

    NodeExecutionMap nodeExecutionMap =
        NodeExecutionMap.builder()
            .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build())
            .ambiance(ambiance)
            .nodeExecutionsCache(nodeExecutionsCache)
            .build();
    Optional<Object> executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(
            String.format("<+<+pipeline.executionUrl>+'?stage=%s&stageExecId=%s'>", stageSetupId, stageRuntimeId));

    // Stage having strategy inside parallel block
    ambiance = Ambiance.newBuilder()
                   .addAllLevels(getStageLevelInsideStrategyInParallel(stageRuntimeId, stageSetupId))
                   .setStageExecutionId(stageRuntimeId)
                   .setMetadata(ExecutionMetadata.newBuilder()
                                    .putFeatureFlagToValueMap(
                                        FeatureName.PIPE_DISABLE_ESCAPE_AMPERSAND_IN_STAGE_EXEC_URL.name(), false)
                                    .build())
                   .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, ambiance);

    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build())
                           .ambiance(ambiance)
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(
            String.format("<+<+pipeline.executionUrl>+'?stage=%s&stageExecId=%s'>", stageSetupId, stageRuntimeId));

    // Only step is inside strategy
    String stepRuntimeId = UUIDGenerator.generateUuid();
    Ambiance stepAmbiance = Ambiance.newBuilder()
                                .addAllLevels(getStepLevelInsideStrategy(stepRuntimeId, stageRuntimeId, stageSetupId))
                                .setStageExecutionId(stageRuntimeId)
                                .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, stepAmbiance);

    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(stepAmbiance).build())
                           .ambiance(stepAmbiance)
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s&step=%s'>", stageSetupId, stepRuntimeId));

    // step having strategy inside parallel block and not stage
    stepAmbiance = Ambiance.newBuilder()
                       .addAllLevels(getStepLevelInsideStrategyInParallel(stepRuntimeId, stageRuntimeId, stageSetupId))
                       .setStageExecutionId(stageRuntimeId)
                       .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, stepAmbiance);

    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(stepAmbiance).build())
                           .ambiance(stepAmbiance)
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s&step=%s'>", stageSetupId, stepRuntimeId));

    // step having stage strategy
    stepAmbiance = Ambiance.newBuilder()
                       .addAllLevels(getStepLevelInsideStageStrategy(stepRuntimeId, stageRuntimeId, stageSetupId))
                       .setStageExecutionId(stageRuntimeId)
                       .setMetadata(ExecutionMetadata.newBuilder()
                                        .putFeatureFlagToValueMap(
                                            FeatureName.PIPE_DISABLE_ESCAPE_AMPERSAND_IN_STAGE_EXEC_URL.name(), false)
                                        .build())
                       .build();
    nodeExecutionsCache.getAmbianceMap().put(nodeExecutionId, stepAmbiance);

    nodeExecutionMap = NodeExecutionMap.builder()
                           .nodeExecution(NodeExecution.builder().uuid(nodeExecutionId).ambiance(stepAmbiance).build())
                           .ambiance(stepAmbiance)
                           .nodeExecutionsCache(nodeExecutionsCache)
                           .build();
    executionUrl = nodeExecutionMap.fetchExecutionUrl(OrchestrationConstants.EXECUTION_URL);
    assertThat(executionUrl).isPresent();
    assertThat((String) executionUrl.get())
        .isEqualTo(String.format("<+<+pipeline.executionUrl>+'?stage=%s&stageExecId=%s&step=%s'>", stageSetupId,
            stageRuntimeId, stepRuntimeId));
  }

  private List<Level> getNormalStageLevels(String stageRuntimeId, String stageSetupId) {
    List<Level> levelList = new LinkedList<>();
    levelList.add(
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).setType("pipeline").build())
            .build());
    levelList.add(Level.newBuilder()
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).setType("stages").build())
                      .build());
    levelList.add(Level.newBuilder()
                      .setRuntimeId(stageRuntimeId)
                      .setSetupId(stageSetupId)
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
                      .build());
    return levelList;
  }

  private List<Level> getNestedStepGroupLevels(String parentStepGroupUuid, String nearestStepGroupUuid) {
    List<Level> levelList = new LinkedList<>();
    levelList.add(
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).setType("pipeline").build())
            .build());
    levelList.add(Level.newBuilder()
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).setType("stages").build())
                      .build());
    levelList.add(
        Level.newBuilder()
            .setRuntimeId(parentStepGroupUuid)
            .setSetupId(parentStepGroupUuid)
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP_GROUP).setType("STEP_GROUP").build())
            .build());
    levelList.add(Level.newBuilder()
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
                      .build());
    levelList.add(
        Level.newBuilder()
            .setRuntimeId(nearestStepGroupUuid)
            .setSetupId(nearestStepGroupUuid)
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP_GROUP).setType("STEP_GROUP").build())
            .build());
    levelList.add(Level.newBuilder()
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("step").build())
                      .build());
    return levelList;
  }

  private List<Level> getNormalStepLevels(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> levels = getNormalStageLevels(stageRuntimeId, stageSetupId);
    levels.add(Level.newBuilder()
                   .setRuntimeId(stepRuntimeId)
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
                   .build());
    return levels;
  }

  private List<Level> getStageLevelInsideParallel(String stageRuntimeId, String stageSetupId) {
    List<Level> stageLevels = getNormalStageLevels(stageRuntimeId, stageSetupId);
    // Add parallel block to it
    stageLevels.add(2,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.FORK).setType("section").build())
            .build());
    return stageLevels;
  }

  private List<Level> getStepLevelInsideParallel(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> stepLevels = getNormalStepLevels(stepRuntimeId, stageRuntimeId, stageSetupId);
    // Add parallel block to it
    stepLevels.add(3,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.FORK).setType("section").build())
            .build());
    return stepLevels;
  }

  private List<Level> getStageLevelInsideStrategy(String stageRuntimeId, String stageSetupId) {
    List<Level> stageLevels = getNormalStageLevels(stageRuntimeId, stageSetupId);
    // Add strategy block to it
    stageLevels.add(2,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .build());
    stageLevels.set(3,
        Level.newBuilder()
            .setRuntimeId(stageRuntimeId)
            .setSetupId(stageSetupId)
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stageLevels;
  }

  private List<Level> getStepLevelInsideStrategy(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> stepLevels = getNormalStepLevels(stepRuntimeId, stageRuntimeId, stageSetupId);
    // Add strategy block to it
    stepLevels.add(3,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .build());

    stepLevels.add(4,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stepLevels;
  }

  private List<Level> getStageLevelInsideStrategyInParallel(String stageRuntimeId, String stageSetupId) {
    List<Level> stageLevels = getStageLevelInsideParallel(stageRuntimeId, stageSetupId);
    // Add strategy block to it
    stageLevels.add(3,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .build());
    stageLevels.set(4,
        Level.newBuilder()
            .setRuntimeId(stageRuntimeId)
            .setSetupId(stageSetupId)
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stageLevels;
  }

  private List<Level> getStepLevelInsideStrategyInParallel(
      String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> stepLevels = getStepLevelInsideParallel(stepRuntimeId, stageRuntimeId, stageSetupId);
    // Add strategy block to it
    stepLevels.add(4,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    stepLevels.add(5,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stepLevels;
  }

  private List<Level> getStepLevelInsideStageStrategy(
      String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> stepLevels = getNormalStepLevels(stepRuntimeId, stageRuntimeId, stageSetupId);

    stepLevels.add(2,
        Level.newBuilder()
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).setType("strategy").build())
            .build());
    stepLevels.set(3,
        Level.newBuilder()
            .setRuntimeId(stageRuntimeId)
            .setSetupId(stageSetupId)
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
            .setStrategyMetadata(StrategyMetadata.newBuilder().build())
            .build());
    return stepLevels;
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testfetchNodeExecutionField() {
    String executionId = "nodeExecutionId";
    StepType stepType = StepType.newBuilder().setType("type").setStepCategory(StepCategory.STAGE).build();
    NodeExecution nodeExecution = NodeExecution.builder().stepType(stepType).uuid("test-uuid").build();
    NodeExecutionMap nodeExecutionMap = NodeExecutionMap.builder().nodeExecution(nodeExecution).build();
    Optional<Object> actual = nodeExecutionMap.fetchNodeExecutionField(executionId);

    assertThat(actual).isEqualTo(Optional.of("test-uuid"));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testFetchCurrentStatus_WithCachedStatus() {
    String nodeExecutionId = UUIDGenerator.generateUuid();
    String planExecutionId = UUIDGenerator.generateUuid();

    // Build ambiance with feature flag enabled
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .putFeatureFlagToValueMap(FeatureName.PIPE_CACHE_CURRENT_STATUS.name(), true)
                             .build())
            .build();

    NodeExecution nodeExecution = NodeExecution.builder().uuid(nodeExecutionId).ambiance(ambiance).build();

    when(nodeExecutionInfoService.getCurrentStatus(nodeExecutionId)).thenReturn(Optional.of(Status.FAILED));

    NodeExecutionMap nodeExecutionMap = NodeExecutionMap.builder()
                                            .nodeExecution(nodeExecution)
                                            .nodeExecutionInfoService(nodeExecutionInfoService)
                                            .ambiance(ambiance)
                                            .build();

    // Test through public API
    Object result = nodeExecutionMap.get(OrchestrationConstants.CURRENT_STATUS);

    assertThat(result).isNotNull();
    assertThat(result).isEqualTo("FAILED");
    verify(nodeExecutionInfoService).getCurrentStatus(nodeExecutionId);
  }

  /**
   * Wires up a DAG scenario for stage3 (nodeId = nodeSetupId) with declared dependencies dep1 & dep2.
   * Returns a NodeExecutionMap bound to the current node so assertions can drive it through get().
   */
  private NodeExecutionMap buildDagStageNodeExecutionMap(String nodeSetupId, String parentSetupId,
      String parentRuntimeId, List<String> depSetupIds, List<NodeExecution> siblings) {
    DependencyGraphProto.Builder dg = DependencyGraphProto.newBuilder();
    if (depSetupIds != null) {
      dg.addEntries(DependencyEntry.newBuilder()
                        .setNodeId(nodeSetupId)
                        .setDependencies(StringArray.newBuilder().addAllValues(depSetupIds).build())
                        .build());
    }
    PlanNode parentPlanNode = PlanNode.builder().uuid(parentSetupId).dependencyGraph(dg.build()).build();
    NodeExecution parentExec = NodeExecution.builder().uuid(parentRuntimeId).nodeId(parentSetupId).build();
    NodeExecution currentExec = NodeExecution.builder()
                                    .uuid(UUIDGenerator.generateUuid())
                                    .nodeId(nodeSetupId)
                                    .parentId(parentRuntimeId)
                                    .build();

    when(nodeExecutionsCache.fetch(parentRuntimeId)).thenReturn(parentExec);
    when(nodeExecutionsCache.fetchNode(parentSetupId)).thenReturn(parentPlanNode);
    when(nodeExecutionsCache.fetchChildren(parentRuntimeId)).thenReturn(siblings);

    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(UUIDGenerator.generateUuid()).build();
    return NodeExecutionMap.builder()
        .nodeExecution(currentExec)
        .nodeExecutionsCache(nodeExecutionsCache)
        .ambiance(ambiance)
        .build();
  }

  private NodeExecution sibling(String nodeSetupId, Status status, boolean oldRetry) {
    return NodeExecution.builder()
        .uuid(UUIDGenerator.generateUuid())
        .nodeId(nodeSetupId)
        .status(status)
        .oldRetry(oldRetry)
        .build();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testDagAllDependantsSucceeded_WhenAllDepsSucceeded_ReturnsTrue() {
    String nodeSetupId = "stage3";
    String parentSetupId = "stagesContainer";
    String parentRuntimeId = UUIDGenerator.generateUuid();
    NodeExecutionMap map =
        buildDagStageNodeExecutionMap(nodeSetupId, parentSetupId, parentRuntimeId, Arrays.asList("dep1", "dep2"),
            Arrays.asList(sibling("dep1", Status.SUCCEEDED, false), sibling("dep2", Status.IGNORE_FAILED, false),
                // unrelated sibling — must be ignored
                sibling("stage4", Status.FAILED, false)));

    assertThat(map.get(OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED)).isEqualTo(true);
    assertThat(map.get(OrchestrationConstants.ANY_DEPENDANT_FAILED)).isEqualTo(false);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testDagAllDependantsSucceeded_WhenAnyDepFailed_ReturnsFalse() {
    String nodeSetupId = "stage3";
    String parentSetupId = "stagesContainer";
    String parentRuntimeId = UUIDGenerator.generateUuid();
    NodeExecutionMap map =
        buildDagStageNodeExecutionMap(nodeSetupId, parentSetupId, parentRuntimeId, Arrays.asList("dep1", "dep2"),
            Arrays.asList(sibling("dep1", Status.SUCCEEDED, false), sibling("dep2", Status.FAILED, false)));

    assertThat(map.get(OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED)).isEqualTo(false);
    assertThat(map.get(OrchestrationConstants.ANY_DEPENDANT_FAILED)).isEqualTo(true);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testDagAllDependantsSucceeded_WhenDepSkipped_ReturnsFalse() {
    // SKIPPED dependency means an upstream failure propagated through — must not count as success.
    String nodeSetupId = "stage3";
    String parentSetupId = "stagesContainer";
    String parentRuntimeId = UUIDGenerator.generateUuid();
    NodeExecutionMap map = buildDagStageNodeExecutionMap(nodeSetupId, parentSetupId, parentRuntimeId,
        Arrays.asList("dep1"), Collections.singletonList(sibling("dep1", Status.SKIPPED, false)));

    assertThat(map.get(OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED)).isEqualTo(false);
    assertThat(map.get(OrchestrationConstants.ANY_DEPENDANT_FAILED)).isEqualTo(false);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testDagAllDependantsSucceeded_FiltersOutRetriedExecutions() {
    // A retried dep: old attempt failed, current attempt succeeded. The resolver must honour the live status.
    String nodeSetupId = "stage3";
    String parentSetupId = "stagesContainer";
    String parentRuntimeId = UUIDGenerator.generateUuid();
    NodeExecutionMap map =
        buildDagStageNodeExecutionMap(nodeSetupId, parentSetupId, parentRuntimeId, Arrays.asList("dep1"),
            Arrays.asList(sibling("dep1", Status.FAILED, true), sibling("dep1", Status.SUCCEEDED, false)));

    assertThat(map.get(OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED)).isEqualTo(true);
    assertThat(map.get(OrchestrationConstants.ANY_DEPENDANT_FAILED)).isEqualTo(false);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testDagAllDependantsSucceeded_WhenDepNotYetExecuted_ReturnsFalse() {
    // Dep declared but no NodeExecution present yet → treat as not-satisfied.
    String nodeSetupId = "stage3";
    String parentSetupId = "stagesContainer";
    String parentRuntimeId = UUIDGenerator.generateUuid();
    NodeExecutionMap map = buildDagStageNodeExecutionMap(
        nodeSetupId, parentSetupId, parentRuntimeId, Arrays.asList("dep1"), Collections.emptyList());

    assertThat(map.get(OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED)).isEqualTo(false);
    assertThat(map.get(OrchestrationConstants.ANY_DEPENDANT_FAILED)).isEqualTo(false);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testDagAllDependantsSucceeded_ParentHasNoDependencyGraph_ReturnsVacuousDefaults() {
    // Sequential pipeline (no DAG) — parent carries no dependency graph. Resolver must return vacuous
    // defaults so a user who writes <+OnAllDependantsSuccess> in a sequential stage still gets a sane value.
    String nodeSetupId = "stage3";
    String parentSetupId = "stagesContainer";
    String parentRuntimeId = UUIDGenerator.generateUuid();
    NodeExecutionMap map =
        buildDagStageNodeExecutionMap(nodeSetupId, parentSetupId, parentRuntimeId, null, Collections.emptyList());

    assertThat(map.get(OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED)).isEqualTo(true);
    assertThat(map.get(OrchestrationConstants.ANY_DEPENDANT_FAILED)).isEqualTo(false);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testDagAllDependantsSucceeded_MatrixDependency_AllIterationsSucceeded_ReturnsTrue() {
    // stage3 depends on a matrix-looped stage dep1. The strategy wrapper is the NodeExecution that
    // lives at the stages level; the individual iterations are its children, NOT siblings of stage3.
    // So the dependency graph points at the wrapper's planNodeUuid, and its aggregated rolled-up
    // status (SUCCEEDED once all iterations succeeded) is what the resolver should observe.
    String nodeSetupId = "stage3";
    String parentSetupId = "stagesContainer";
    String parentRuntimeId = UUIDGenerator.generateUuid();

    NodeExecutionMap map = buildDagStageNodeExecutionMap(nodeSetupId, parentSetupId, parentRuntimeId,
        Arrays.asList("dep1Strategy"), Collections.singletonList(sibling("dep1Strategy", Status.SUCCEEDED, false)));

    assertThat(map.get(OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED)).isEqualTo(true);
    assertThat(map.get(OrchestrationConstants.ANY_DEPENDANT_FAILED)).isEqualTo(false);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testDagAllDependantsSucceeded_MatrixDependency_PartialFailure_ReturnsFalse() {
    // Matrix dep with at least one iteration failed → strategy wrapper rolls up to FAILED. Guarantees
    // the resolver doesn't accidentally peek inside the wrapper and miss the failure of any iteration.
    String nodeSetupId = "stage3";
    String parentSetupId = "stagesContainer";
    String parentRuntimeId = UUIDGenerator.generateUuid();

    NodeExecutionMap map = buildDagStageNodeExecutionMap(nodeSetupId, parentSetupId, parentRuntimeId,
        Arrays.asList("dep1Strategy"), Collections.singletonList(sibling("dep1Strategy", Status.FAILED, false)));

    assertThat(map.get(OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED)).isEqualTo(false);
    assertThat(map.get(OrchestrationConstants.ANY_DEPENDANT_FAILED)).isEqualTo(true);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testDagAllDependantsSucceeded_MatrixDependency_RetriedStrategyWrapper_ReturnsTrue() {
    // Matrix dep was retried: the old strategy wrapper is FAILED with oldRetry=true; the new wrapper
    // is SUCCEEDED. Resolver must ignore the old attempt and honour the live one.
    String nodeSetupId = "stage3";
    String parentSetupId = "stagesContainer";
    String parentRuntimeId = UUIDGenerator.generateUuid();

    NodeExecutionMap map = buildDagStageNodeExecutionMap(nodeSetupId, parentSetupId, parentRuntimeId,
        Arrays.asList("dep1Strategy"),
        Arrays.asList(sibling("dep1Strategy", Status.FAILED, true), sibling("dep1Strategy", Status.SUCCEEDED, false)));

    assertThat(map.get(OrchestrationConstants.ALL_DEPENDANTS_SUCCEEDED)).isEqualTo(true);
    assertThat(map.get(OrchestrationConstants.ANY_DEPENDANT_FAILED)).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testFetchInputsReadsFromPlanExecutionWhenReadSwitchEnabled() {
    // V1 pipeline.inputs with PIPE_EXECUTION_SWITCH_FIELD_SOURCE enabled: the read switches to
    // PlanExecution.processedYaml. This exercises planExecutionService, which is now wired through the functor
    // chain; previously it was null here and this branch NPE'd, causing pipeline.inputs to resolve to null.
    String accountId = "acc";
    String planExecutionId = UUIDGenerator.generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putSetupAbstractions("accountId", accountId)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setHarnessVersion(HarnessYamlVersion.V1)
                             .putFeatureFlagToValueMap(FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name(), true)
                             .build())
            .build();

    when(planExecutionMetadataService.findByPlanExecutionId(accountId, planExecutionId))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));
    String processedYamlFromPlanExecution =
        "pipeline:\n  inputs:\n    var1:\n      type: string\n      value: fromPlanExecution\n";
    when(planExecutionService.getWithFieldsIncludedOptional(anyString(), any()))
        .thenReturn(Optional.of(PlanExecution.builder().processedYaml(processedYamlFromPlanExecution).build()));

    NodeExecutionMap nodeExecutionMap = NodeExecutionMap.builder()
                                            .ambiance(ambiance)
                                            .planExecutionMetadataService(planExecutionMetadataService)
                                            .planExecutionService(planExecutionService)
                                            .build();

    Optional<Object> result = Reflect.on(nodeExecutionMap).call("fetchInputs", "inputs").get();
    assertThat(result).isPresent();
    assertThat((Map<String, Object>) result.get()).containsEntry("var1", "fromPlanExecution");
    verify(planExecutionService).getWithFieldsIncludedOptional(anyString(), any());
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testFetchInputsReadsFromMetadataWhenReadSwitchDisabled() {
    // No regression with the read switch OFF: inputs are read from PlanExecutionMetadata.processedYaml and
    // planExecutionService is never touched.
    String accountId = "acc";
    String planExecutionId = UUIDGenerator.generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putSetupAbstractions("accountId", accountId)
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .build();

    String processedYamlFromMetadata =
        "pipeline:\n  inputs:\n    var1:\n      type: string\n      value: fromMetadata\n";
    when(planExecutionMetadataService.findByPlanExecutionId(accountId, planExecutionId))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().processedYaml(processedYamlFromMetadata).build()));

    NodeExecutionMap nodeExecutionMap = NodeExecutionMap.builder()
                                            .ambiance(ambiance)
                                            .planExecutionMetadataService(planExecutionMetadataService)
                                            .planExecutionService(planExecutionService)
                                            .build();

    Optional<Object> result = Reflect.on(nodeExecutionMap).call("fetchInputs", "inputs").get();
    assertThat(result).isPresent();
    assertThat((Map<String, Object>) result.get()).containsEntry("var1", "fromMetadata");
    verify(planExecutionService, never()).getWithFieldsIncludedOptional(anyString(), any());
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testFetchInputsReturnsEmptyForNonV1() {
    // V0 must be untouched: fetchInputs short-circuits for non-V1 versions and reads neither metadata nor plan
    // execution.
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(UUIDGenerator.generateUuid())
                            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion("0").build())
                            .build();
    NodeExecutionMap nodeExecutionMap = NodeExecutionMap.builder()
                                            .ambiance(ambiance)
                                            .planExecutionMetadataService(planExecutionMetadataService)
                                            .planExecutionService(planExecutionService)
                                            .build();

    Optional<Object> result = Reflect.on(nodeExecutionMap).call("fetchInputs", "inputs").get();
    assertThat(result).isEmpty();
    verify(planExecutionMetadataService, never()).findByPlanExecutionId(anyString(), anyString());
    verify(planExecutionService, never()).getWithFieldsIncludedOptional(anyString(), any());
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testFetchInputsResolvesBooleanWithoutNullingStringInputs() {
    String processedYaml = "pipeline:\n"
        + "  inputs:\n"
        + "    input1:\n"
        + "      type: string\n"
        + "      default: defaultValue1\n"
        + "    input4:\n"
        + "      type: boolean\n"
        + "      default: true\n";

    Map<String, Object> resolved = resolveInputsFromMetadata(processedYaml);

    assertThat(resolved).containsEntry("input1", "defaultValue1");
    assertThat(resolved).containsEntry("input4", true);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void testFetchInputsResolvesHeterogeneousInputTypes() {
    // A fully mixed inputs block (string, boolean, integer, object, array) must resolve every entry
    // independently to its native value. Each entry is handled on its own type; no entry may break another.
    String processedYaml = "pipeline:\n"
        + "  inputs:\n"
        + "    strVar:\n"
        + "      type: string\n"
        + "      value: hello\n"
        + "    boolVar:\n"
        + "      type: boolean\n"
        + "      default: true\n"
        + "    intVar:\n"
        + "      type: integer\n"
        + "      default: 42\n"
        + "    objVar:\n"
        + "      type: object\n"
        + "      default:\n"
        + "        k: v\n"
        + "    arrVar:\n"
        + "      type: array\n"
        + "      default:\n"
        + "        - a\n"
        + "        - b\n";

    Map<String, Object> resolved = resolveInputsFromMetadata(processedYaml);

    assertThat(resolved).containsEntry("strVar", "hello");
    assertThat(resolved).containsEntry("boolVar", true);
    assertThat(resolved).containsEntry("intVar", 42);
    assertThat(resolved.get("objVar")).isInstanceOf(Map.class);
    assertThat((Map<String, Object>) resolved.get("objVar")).containsEntry("k", "v");
    assertThat(resolved.get("arrVar")).isInstanceOf(List.class);
    assertThat((List<Object>) resolved.get("arrVar")).containsExactly("a", "b");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testFetchInputsWrapsSecretTypeInputUnchanged() {
    // No regression to the secret path: a secret-typed input is still wrapped as a secret expression, and it
    // coexists with a boolean input in the same block without either affecting the other.
    String processedYaml = "pipeline:\n"
        + "  inputs:\n"
        + "    secretVar:\n"
        + "      type: secret\n"
        + "      value: my_secret\n"
        + "    boolVar:\n"
        + "      type: boolean\n"
        + "      default: false\n";

    Map<String, Object> resolved = resolveInputsFromMetadata(processedYaml);

    assertThat(resolved).containsEntry("secretVar", "<+secrets.getValue('my_secret')>");
    assertThat(resolved).containsEntry("boolVar", false);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> resolveInputsFromMetadata(String processedYaml) {
    String accountId = "acc";
    String planExecutionId = UUIDGenerator.generateUuid();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .putSetupAbstractions("accountId", accountId)
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .build();
    when(planExecutionMetadataService.findByPlanExecutionId(accountId, planExecutionId))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().processedYaml(processedYaml).build()));
    NodeExecutionMap nodeExecutionMap = NodeExecutionMap.builder()
                                            .ambiance(ambiance)
                                            .planExecutionMetadataService(planExecutionMetadataService)
                                            .planExecutionService(planExecutionService)
                                            .build();
    Optional<Object> result = Reflect.on(nodeExecutionMap).call("fetchInputs", "inputs").get();
    assertThat(result).isPresent();
    return (Map<String, Object>) result.get();
  }
}
