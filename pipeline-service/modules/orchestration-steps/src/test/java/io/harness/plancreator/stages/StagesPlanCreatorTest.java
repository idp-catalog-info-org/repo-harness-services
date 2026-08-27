/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanExecutionContext;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StagesStep;
import io.harness.steps.StagesStepParameters;
import io.harness.steps.StagesStepWithChildrenSupport;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class StagesPlanCreatorTest extends CategoryTest {
  YamlField stagesYamlField;
  StagesConfig stagesConfig;
  StagesConfig stagesConfigWithInject;
  PlanCreationContext context;
  PlanCreationContext contextWithFlexible;
  YamlField stagesFieldWithInject;

  @Mock KryoSerializer kryoSerializer;
  @Mock PmsFeatureFlagService featureFlagService;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.openMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("complex_pipeline.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    stagesConfig = YamlUtils.read(stagesYamlField.getNode().toString(), StagesConfig.class);

    context = PlanCreationContext.builder()
                  .currentField(stagesYamlField)
                  .dependency(Dependency.newBuilder().build())
                  .globalContext("metadata",
                      PlanCreationContextValue.newBuilder()
                          .setExecutionContext(PlanExecutionContext.newBuilder()
                                                   .putFeatureFlagToValueMap("PIE_FLEXIBLE_TEMPLATES", false)
                                                   .build())
                          .build())
                  .build();

    final URL testFile2 = classLoader.getResource("complex-pipeline-with-inject-stage.yaml");
    assertThat(testFile2).isNotNull();
    String pipelineYaml2 = Resources.toString(testFile2, Charsets.UTF_8);
    String pipelineYamlWithUuid2 = YamlUtils.injectUuid(pipelineYaml2);
    YamlField pipelineYamlField2 = YamlUtils.readTree(pipelineYamlWithUuid2);
    stagesFieldWithInject = pipelineYamlField2.getNode().getField("stages");
    stagesConfigWithInject = YamlUtils.read(stagesFieldWithInject.getNode().toString(), StagesConfig.class);

    contextWithFlexible = PlanCreationContext.builder()
                              .currentField(stagesFieldWithInject)
                              .globalContext("metadata",
                                  PlanCreationContextValue.newBuilder()
                                      .setExecutionContext(PlanExecutionContext.newBuilder()
                                                               .putFeatureFlagToValueMap("PIE_FLEXIBLE_TEMPLATES", true)
                                                               .build())
                                      .build())
                              .dependency(Dependency.newBuilder().setParentInfo(HarnessStruct.newBuilder()).build())
                              .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode() {
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    String approvalStageUuid = Objects.requireNonNull(stages.get(0).getField("stage")).getNode().getUuid();
    List<String> childrenNodeIds = Collections.singletonList(approvalStageUuid);

    // Mock feature flag to be disabled for traditional execution
    when(featureFlagService.isEnabled(anyString(), anyString())).thenReturn(false);

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    PlanNode planForParentNode = stagesPlanCreator.createPlanForParentNode(context, stagesConfig, childrenNodeIds);
    assertThat(planForParentNode).isNotNull();

    assertThat(planForParentNode.getUuid()).isEqualTo(stagesYamlField.getNode().getUuid());
    assertThat(planForParentNode.getIdentifier()).isEqualTo("stages");
    assertThat(planForParentNode.getStepType()).isEqualTo(StagesStep.STEP_TYPE);
    assertThat(planForParentNode.getGroup()).isEqualTo("STAGES");
    assertThat(planForParentNode.getName()).isEqualTo("stages");

    // With feature flag disabled, step parameters should still be StagesStepParameters for consistency
    assertThat(planForParentNode.getStepParameters() instanceof NGSectionStepParameters).isTrue();
    NGSectionStepParameters stagesParams = (NGSectionStepParameters) planForParentNode.getStepParameters();
    assertThat(stagesParams.getChildNodeId()).isEqualTo(approvalStageUuid);
    assertThat(stagesParams.getLogMessage()).isEqualTo("Stages");

    assertThat(planForParentNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planForParentNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILD");

    // With feature flag disabled, dependency graph should be null
    assertThat(planForParentNode.getDependencyGraph()).isNull();

    assertThat(planForParentNode.isSkipExpressionChain()).isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  @Ignore("CI-6025: TI team to follow up")
  public void testCreatePlanForChildrenNodes() {
    doReturn(new byte[2]).when(kryoSerializer).asBytes(any());
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    YamlField approvalStage = stages.get(0).getField("stage");
    assertThat(approvalStage).isNotNull();
    String approvalStageUuid = approvalStage.getNode().getUuid();
    YamlField parallelDeploymentStages = stages.get(1).getField("parallel");
    assertThat(parallelDeploymentStages).isNotNull();
    String parallelStagesUuid = parallelDeploymentStages.getNode().getUuid();

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(kryoSerializer, featureFlagService);
    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        stagesPlanCreator.createPlanForChildrenNodes(context, stagesConfig);
    assertThat(planForChildrenNodes).isNotEmpty();
    assertThat(planForChildrenNodes).hasSize(4);
    assertThat(planForChildrenNodes.containsKey(approvalStageUuid)).isTrue();
    assertThat(planForChildrenNodes.containsKey(parallelStagesUuid)).isTrue();
    assertThat(planForChildrenNodes.containsKey(approvalStageUuid + "_rollbackStage")).isTrue();
    assertThat(planForChildrenNodes.containsKey(parallelStagesUuid + "_rollbackStage")).isTrue();

    PlanCreationResponse approvalStageResponse = planForChildrenNodes.get(approvalStageUuid);
    assertThat(approvalStageResponse.getDependencies().getDependenciesMap()).hasSize(1);
    assertThat(approvalStageResponse.getDependencies().getDependenciesMap().containsKey(approvalStageUuid)).isTrue();
    assertThat(approvalStageResponse.getDependencies().getDependenciesMap().get(approvalStageUuid))
        .isEqualTo("pipeline/stages/[0]/stage");

    PlanCreationResponse parallelStagesResponse = planForChildrenNodes.get(parallelStagesUuid);
    assertThat(parallelStagesResponse.getDependencies().getDependenciesMap()).hasSize(1);
    assertThat(parallelStagesResponse.getDependencies().getDependenciesMap().containsKey(parallelStagesUuid)).isTrue();
    assertThat(parallelStagesResponse.getDependencies().getDependenciesMap().get(parallelStagesUuid))
        .isEqualTo("pipeline/stages/[1]/parallel");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodesForFlexibleTemplates() {
    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(kryoSerializer, featureFlagService);

    String firstStageUuid = stagesFieldWithInject.getNode().asArray().get(0).getField("stage").getUuid();
    String secondStageUuid = stagesFieldWithInject.getNode().asArray().get(1).getField("insert").getUuid();

    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        stagesPlanCreator.createPlanForChildrenNodes(contextWithFlexible, stagesConfigWithInject);
    assertThat(planForChildrenNodes).isNotEmpty();
    assertThat(planForChildrenNodes).hasSize(3);

    assertThat(planForChildrenNodes.containsKey(firstStageUuid)).isTrue();
    PlanCreationResponse stagesResponse = planForChildrenNodes.get(firstStageUuid);
    assertThat(stagesResponse.getDependencies()).isNotNull();
    assertThat(stagesResponse.getDependencies().getDependenciesMap().containsKey(firstStageUuid)).isTrue();
    assertThat(stagesResponse.getDependencies().getDependenciesMap().get(firstStageUuid)).isEqualTo("stages/[0]/stage");

    assertThat(planForChildrenNodes.containsKey(secondStageUuid)).isTrue();
    stagesResponse = planForChildrenNodes.get(secondStageUuid);
    assertThat(stagesResponse.getDependencies()).isNotNull();
    assertThat(stagesResponse.getDependencies().getDependenciesMap().containsKey(secondStageUuid)).isTrue();
    assertThat(stagesResponse.getDependencies().getDependenciesMap().get(secondStageUuid))
        .isEqualTo("stages/[1]/insert");
    assertThat(stagesResponse.getDependencies()
                   .getDependencyMetadataMap()
                   .get(secondStageUuid)
                   .getNodeMetadata()
                   .getDataMap()
                   .containsKey("injectType"))
        .isTrue();
    assertThat(stagesResponse.getDependencies()
                   .getDependencyMetadataMap()
                   .get(secondStageUuid)
                   .getNodeMetadata()
                   .getDataMap()
                   .get("injectType")
                   .getStringValue())
        .isEqualTo("STAGE");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfo() {
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    YamlField approvalStage = stages.get(0).getField("stage");
    assertThat(approvalStage).isNotNull();
    String approvalStageUuid = approvalStage.getNode().getUuid();
    YamlField parallelDeploymentStages = stages.get(1).getField("parallel");
    assertThat(parallelDeploymentStages).isNotNull();
    String parallelStagesUuid = parallelDeploymentStages.getNode().getUuid();

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    GraphLayoutResponse layoutNodeInfo = stagesPlanCreator.getLayoutNodeInfo(context, stagesConfig);
    assertThat(layoutNodeInfo).isNotNull();
    assertThat(layoutNodeInfo.getStartingNodeId()).isEqualTo(approvalStageUuid);
    assertThat(layoutNodeInfo.getLayoutNodes()).hasSize(1);
    assertThat(layoutNodeInfo.getLayoutNodes().containsKey(approvalStageUuid)).isTrue();

    GraphLayoutNode stageLayoutNode = layoutNodeInfo.getLayoutNodes().get(approvalStageUuid);
    assertThat(stageLayoutNode.getNodeUUID()).isEqualTo(approvalStageUuid);
    assertThat(stageLayoutNode.getNodeType()).isEqualTo("Approval");
    assertThat(stageLayoutNode.getName()).isEqualTo("a1-1");
    assertThat(stageLayoutNode.getNodeGroup()).isEqualTo("STAGE");
    assertThat(stageLayoutNode.getNodeIdentifier()).isEqualTo("a11");

    EdgeLayoutList edgeLayoutList = stageLayoutNode.getEdgeLayoutList();
    assertThat(edgeLayoutList).isNotNull();
    assertThat(edgeLayoutList.getNextIdsList()).hasSize(1);
    assertThat(edgeLayoutList.getNextIds(0)).isEqualTo(parallelStagesUuid);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNodeWithDependencyExecution() {
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    String approvalStageUuid = Objects.requireNonNull(stages.get(0).getField("stage")).getNode().getUuid();
    List<String> childrenNodeIds = Collections.singletonList(approvalStageUuid);

    // Create context with both FF enabled and enableDAG=true (both required for DAG mode)
    PlanCreationContext contextWithDag =
        PlanCreationContext.builder()
            .currentField(stagesYamlField)
            .dependency(Dependency.newBuilder().build())
            .globalContext("metadata",
                PlanCreationContextValue.newBuilder()
                    .setExecutionContext(PlanExecutionContext.newBuilder()
                                             .putFeatureFlagToValueMap(
                                                 FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION.toString(), true)
                                             .setEnableDAG(true)
                                             .build())
                    .build())
            .build();

    // Mock feature flag to be enabled for dependency-based execution
    when(featureFlagService.isEnabled(any(), eq(FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(true);

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    PlanNode planForParentNode =
        stagesPlanCreator.createPlanForParentNode(contextWithDag, stagesConfig, childrenNodeIds);
    assertThat(planForParentNode).isNotNull();

    assertThat(planForParentNode.getUuid()).isEqualTo(stagesYamlField.getNode().getUuid());
    assertThat(planForParentNode.getIdentifier()).isEqualTo("stages");
    assertThat(planForParentNode.getStepType()).isEqualTo(StagesStepWithChildrenSupport.STEP_TYPE);
    assertThat(planForParentNode.getGroup()).isEqualTo("STAGES");
    assertThat(planForParentNode.getName()).isEqualTo("stages");

    // With dependency-based execution, step parameters should be StagesStepParameters
    assertThat(planForParentNode.getStepParameters() instanceof StagesStepParameters).isTrue();
    StagesStepParameters stagesParams = (StagesStepParameters) planForParentNode.getStepParameters();
    assertThat(stagesParams.getLogMessage()).isEqualTo("Stages");
    assertThat(stagesParams.getName()).isEqualTo("stages");
    assertThat(stagesParams.getId()).isEqualTo(stagesYamlField.getNode().getUuid());
    assertThat(stagesParams.getChildrenIds()).isNotNull();

    assertThat(planForParentNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planForParentNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILDREN");

    assertThat(planForParentNode.isSkipExpressionChain()).isFalse();
    assertThat(planForParentNode.getDependencyGraph()).isNotNull();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfoWhenInsideWrapper() {
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    YamlField approvalStage = stages.get(0).getField("stage");
    assertThat(approvalStage).isNotNull();
    String approvalStageUuid = approvalStage.getNode().getUuid();
    YamlField parallelDeploymentStages = stages.get(1).getField("parallel");
    assertThat(parallelDeploymentStages).isNotNull();
    String parallelStagesUuid = parallelDeploymentStages.getNode().getUuid();

    PlanCreationContext creationContext =
        PlanCreationContext.builder()
            .currentField(stagesYamlField)
            .dependency(Dependency.newBuilder()
                            .setParentInfo(HarnessStruct.newBuilder()
                                               .putData(PlanCreatorConstants.CHILD_OF_INJECT,
                                                   HarnessValue.newBuilder().setBoolValue(true).build())
                                               .build())
                            .build())
            .globalContext("metadata",
                PlanCreationContextValue.newBuilder()
                    .setExecutionContext(PlanExecutionContext.newBuilder()
                                             .putFeatureFlagToValueMap("PIE_FLEXIBLE_TEMPLATES", true)
                                             .build())
                    .build())
            .build();

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    GraphLayoutResponse layoutNodeInfo = stagesPlanCreator.getLayoutNodeInfo(creationContext, stagesConfig);

    assertThat(layoutNodeInfo).isNotNull();
    // Since stages node is inside a wrapper So it will not set a starting nodeId
    assertThat(layoutNodeInfo.getStartingNodeId()).isNull();

    creationContext = PlanCreationContext.builder()
                          .currentField(stagesYamlField)
                          .dependency(Dependency.newBuilder()
                                          .setParentInfo(HarnessStruct.newBuilder()
                                                             .putData(PlanCreatorConstants.CHILD_OF_DYNAMIC_STAGE,
                                                                 HarnessValue.newBuilder().setBoolValue(true).build())
                                                             .build())
                                          .build())
                          .build();
    layoutNodeInfo = stagesPlanCreator.getLayoutNodeInfo(creationContext, stagesConfig);

    assertThat(layoutNodeInfo.getLayoutNodes()).hasSize(1);
    assertThat(layoutNodeInfo.getLayoutNodes().containsKey(approvalStageUuid)).isTrue();

    GraphLayoutNode stageLayoutNode = layoutNodeInfo.getLayoutNodes().get(approvalStageUuid);
    assertThat(stageLayoutNode.getNodeUUID()).isEqualTo(approvalStageUuid);
    assertThat(stageLayoutNode.getNodeType()).isEqualTo("Approval");
    assertThat(stageLayoutNode.getName()).isEqualTo("a1-1");
    assertThat(stageLayoutNode.getNodeGroup()).isEqualTo("STAGE");
    assertThat(stageLayoutNode.getNodeIdentifier()).isEqualTo("a11");

    EdgeLayoutList edgeLayoutList = stageLayoutNode.getEdgeLayoutList();
    assertThat(edgeLayoutList).isNotNull();
    assertThat(edgeLayoutList.getNextIdsList()).hasSize(1);
    assertThat(edgeLayoutList.getNextIds(0)).isEqualTo(parallelStagesUuid);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfo_sequentialPipeline_returnsDagFieldsAsDefaults() {
    // Test that sequential pipelines (no DAG) return proper default values for DAG fields
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    YamlField approvalStage = stages.get(0).getField("stage");
    assertThat(approvalStage).isNotNull();
    String approvalStageUuid = approvalStage.getNode().getUuid();

    // Mock feature flag to be disabled for traditional execution
    when(featureFlagService.isEnabled(anyString(), anyString())).thenReturn(false);

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    GraphLayoutResponse layoutNodeInfo = stagesPlanCreator.getLayoutNodeInfo(context, stagesConfig);

    assertThat(layoutNodeInfo).isNotNull();
    // Sequential pipeline should have startingNodeId set
    assertThat(layoutNodeInfo.getStartingNodeId()).isEqualTo(approvalStageUuid);
    // startingNodeIds should contain the first stage
    assertThat(layoutNodeInfo.getStartingNodeIds()).containsExactly(approvalStageUuid);
    // isDagEnabled should be false for sequential pipelines
    assertThat(layoutNodeInfo.isDagEnabled()).isFalse();
    // dependencyGraph should be null for sequential pipelines
    assertThat(layoutNodeInfo.getDependencyGraph()).isNull();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfo_withDagFeatureDisabled_returnsFalseForIsDagEnabled() {
    // Even if the YAML has depends_on fields, if feature flag is disabled, isDagEnabled should be false

    // Mock feature flag to be disabled
    when(featureFlagService.isEnabled(anyString(), eq(FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)))
        .thenReturn(false);
    lenient().when(featureFlagService.isEnabled(anyString(), anyString())).thenReturn(false);

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    GraphLayoutResponse layoutNodeInfo = stagesPlanCreator.getLayoutNodeInfo(context, stagesConfig);

    assertThat(layoutNodeInfo).isNotNull();
    // isDagEnabled should be false when feature flag is disabled
    assertThat(layoutNodeInfo.isDagEnabled()).isFalse();
    // dependencyGraph should be null when feature flag is disabled
    assertThat(layoutNodeInfo.getDependencyGraph()).isNull();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfo_insideWrapper_doesNotSetStartingNodeIdOrDagFields() {
    // When stages is inside a wrapper (inject), it should not set starting node ID
    List<YamlNode> stages = stagesYamlField.getNode().asArray();

    PlanCreationContext creationContext =
        PlanCreationContext.builder()
            .currentField(stagesYamlField)
            .dependency(Dependency.newBuilder()
                            .setParentInfo(HarnessStruct.newBuilder()
                                               .putData(PlanCreatorConstants.CHILD_OF_INJECT,
                                                   HarnessValue.newBuilder().setBoolValue(true).build())
                                               .build())
                            .build())
            .globalContext("metadata",
                PlanCreationContextValue.newBuilder()
                    .setExecutionContext(PlanExecutionContext.newBuilder()
                                             .putFeatureFlagToValueMap("PIE_FLEXIBLE_TEMPLATES", true)
                                             .build())
                    .build())
            .build();

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    GraphLayoutResponse layoutNodeInfo = stagesPlanCreator.getLayoutNodeInfo(creationContext, stagesConfig);

    assertThat(layoutNodeInfo).isNotNull();
    // When inside wrapper, startingNodeId should be null
    assertThat(layoutNodeInfo.getStartingNodeId()).isNull();
    // startingNodeIds should be empty when inside wrapper
    assertThat(layoutNodeInfo.getStartingNodeIds()).isEmpty();
    // isDagEnabled should be false when inside wrapper
    assertThat(layoutNodeInfo.isDagEnabled()).isFalse();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode_withFFEnabledButEnableDAGFalse_usesSequentialMode() {
    // FF is enabled but enableDAG is false in PlanExecutionContext -> should use sequential mode
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    String approvalStageUuid = Objects.requireNonNull(stages.get(0).getField("stage")).getNode().getUuid();
    List<String> childrenNodeIds = Collections.singletonList(approvalStageUuid);

    // Create context with FF enabled but enableDAG=false
    PlanCreationContext contextWithFFButNoEnableDAG =
        PlanCreationContext.builder()
            .currentField(stagesYamlField)
            .dependency(Dependency.newBuilder().build())
            .globalContext("metadata",
                PlanCreationContextValue.newBuilder()
                    .setExecutionContext(PlanExecutionContext.newBuilder()
                                             .putFeatureFlagToValueMap(
                                                 FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION.toString(), true)
                                             .setEnableDAG(false) // enableDAG is false
                                             .build())
                    .build())
            .build();

    // Mock feature flag to return true (FF is enabled)
    when(featureFlagService.isEnabled(any(), eq(FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(true);

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    PlanNode planForParentNode =
        stagesPlanCreator.createPlanForParentNode(contextWithFFButNoEnableDAG, stagesConfig, childrenNodeIds);

    assertThat(planForParentNode).isNotNull();
    // Should use sequential mode (StagesStep) since enableDAG is false
    assertThat(planForParentNode.getStepType()).isEqualTo(StagesStep.STEP_TYPE);
    // Dependency graph should be null in sequential mode
    assertThat(planForParentNode.getDependencyGraph()).isNull();
    // Facilitator should be CHILD (sequential) not CHILDREN (DAG)
    assertThat(planForParentNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILD");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode_withBothFFAndEnableDAGTrue_usesDagMode() {
    // Both FF enabled AND enableDAG=true -> should use DAG mode
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    String approvalStageUuid = Objects.requireNonNull(stages.get(0).getField("stage")).getNode().getUuid();
    List<String> childrenNodeIds = Collections.singletonList(approvalStageUuid);

    // Create context with both FF enabled and enableDAG=true
    PlanCreationContext contextWithBothEnabled =
        PlanCreationContext.builder()
            .currentField(stagesYamlField)
            .dependency(Dependency.newBuilder().build())
            .globalContext("metadata",
                PlanCreationContextValue.newBuilder()
                    .setExecutionContext(PlanExecutionContext.newBuilder()
                                             .putFeatureFlagToValueMap(
                                                 FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION.toString(), true)
                                             .setEnableDAG(true) // enableDAG is true
                                             .build())
                    .build())
            .build();

    // Mock feature flag to return true
    when(featureFlagService.isEnabled(any(), eq(FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(true);

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    PlanNode planForParentNode =
        stagesPlanCreator.createPlanForParentNode(contextWithBothEnabled, stagesConfig, childrenNodeIds);

    assertThat(planForParentNode).isNotNull();
    // Should use DAG mode (StagesStepWithChildrenSupport)
    assertThat(planForParentNode.getStepType()).isEqualTo(StagesStepWithChildrenSupport.STEP_TYPE);
    // Dependency graph should be present in DAG mode
    assertThat(planForParentNode.getDependencyGraph()).isNotNull();
    // Facilitator should be CHILDREN (DAG) not CHILD (sequential)
    assertThat(planForParentNode.getFacilitatorObtainments().get(0).getType().getType()).isEqualTo("CHILDREN");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfo_withBothFFAndEnableDAGTrue_returnsDagEnabled() {
    // Both FF enabled AND enableDAG=true -> isDagEnabled should be true
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    YamlField approvalStage = stages.get(0).getField("stage");
    assertThat(approvalStage).isNotNull();
    String approvalStageUuid = approvalStage.getNode().getUuid();

    // Create context with both FF enabled and enableDAG=true
    PlanCreationContext contextWithBothEnabled =
        PlanCreationContext.builder()
            .currentField(stagesYamlField)
            .dependency(Dependency.newBuilder().build())
            .globalContext("metadata",
                PlanCreationContextValue.newBuilder()
                    .setExecutionContext(PlanExecutionContext.newBuilder()
                                             .putFeatureFlagToValueMap(
                                                 FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION.toString(), true)
                                             .setEnableDAG(true)
                                             .build())
                    .build())
            .build();

    // Mock feature flag to return true
    when(featureFlagService.isEnabled(any(), eq(FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(true);
    lenient().when(featureFlagService.isEnabled(anyString(), anyString())).thenReturn(false);

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    GraphLayoutResponse layoutNodeInfo = stagesPlanCreator.getLayoutNodeInfo(contextWithBothEnabled, stagesConfig);

    assertThat(layoutNodeInfo).isNotNull();
    assertThat(layoutNodeInfo.getStartingNodeId()).isEqualTo(approvalStageUuid);
    // isDagEnabled should be true when both conditions are met
    assertThat(layoutNodeInfo.isDagEnabled()).isTrue();
    // dependencyGraph should be present
    assertThat(layoutNodeInfo.getDependencyGraph()).isNotNull();
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetLayoutNodeInfo_withFFEnabledButEnableDAGFalse_returnsDagDisabled() {
    // FF enabled but enableDAG=false -> isDagEnabled should be false
    List<YamlNode> stages = stagesYamlField.getNode().asArray();
    YamlField approvalStage = stages.get(0).getField("stage");
    assertThat(approvalStage).isNotNull();
    String approvalStageUuid = approvalStage.getNode().getUuid();

    // Create context with FF enabled but enableDAG=false
    PlanCreationContext contextWithFFButNoEnableDAG =
        PlanCreationContext.builder()
            .currentField(stagesYamlField)
            .dependency(Dependency.newBuilder().build())
            .globalContext("metadata",
                PlanCreationContextValue.newBuilder()
                    .setExecutionContext(PlanExecutionContext.newBuilder()
                                             .putFeatureFlagToValueMap(
                                                 FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION.toString(), true)
                                             .setEnableDAG(false)
                                             .build())
                    .build())
            .build();

    // Mock feature flag to return true
    when(featureFlagService.isEnabled(any(), eq(FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION))).thenReturn(true);
    lenient().when(featureFlagService.isEnabled(anyString(), anyString())).thenReturn(false);

    StagesPlanCreator stagesPlanCreator = new StagesPlanCreator(null, featureFlagService);
    GraphLayoutResponse layoutNodeInfo = stagesPlanCreator.getLayoutNodeInfo(contextWithFFButNoEnableDAG, stagesConfig);

    assertThat(layoutNodeInfo).isNotNull();
    assertThat(layoutNodeInfo.getStartingNodeId()).isEqualTo(approvalStageUuid);
    // isDagEnabled should be false since enableDAG is false
    assertThat(layoutNodeInfo.isDagEnabled()).isFalse();
    // dependencyGraph should be null in sequential mode
    assertThat(layoutNodeInfo.getDependencyGraph()).isNull();
  }
}
