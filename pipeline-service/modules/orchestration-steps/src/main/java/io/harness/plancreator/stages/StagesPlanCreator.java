/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.beans.FeatureName.PIPE_THROW_ERROR_WHEN_NO_VALID_STAGE_IN_PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.common.dependencyUtils.DependencyUtils;
import io.harness.plancreator.inject.InjectTypes;
import io.harness.plancreator.inject.InjectUtils;
import io.harness.plancreator.pipelinerollback.PipelineRollbackStageHelper;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.children.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StagesStep;
import io.harness.steps.StagesStepParameters;
import io.harness.steps.StagesStepWithChildrenSupport;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.execution.ExecutionModeUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@NoArgsConstructor(onConstructor = @__({ @Inject }))
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class StagesPlanCreator extends ChildrenPlanCreator<StagesConfig> {
  @Inject KryoSerializer kryoSerializer;
  @Inject PmsFeatureFlagService featureFlagService;

  private static final String LOG_MESSAGE_STEP_PARAMETER_FOR_STAGES = "Stages";

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, StagesConfig config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> stageYamlFields = getStageYamlFields(ctx);
    if (ctx.getFeatureFlagValue(PIPE_THROW_ERROR_WHEN_NO_VALID_STAGE_IN_PIPELINE.name())
        && EmptyPredicate.isEmpty(stageYamlFields)) {
      throw new InvalidRequestException(
          "No valid stage has been provided. Please add at lease one stage in pipeline and try again.");
    }
    boolean isParallelChildOfInsertStages = InjectUtils.isNodeAncestorOfStage(ctx);
    for (YamlField stageYamlField : stageYamlFields) {
      Map<String, YamlField> stageYamlFieldMap = new HashMap<>();
      stageYamlFieldMap.put(stageYamlField.getNode().getUuid(), stageYamlField);
      responseMap.put(stageYamlField.getNode().getUuid(),
          PlanCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProtoWithMetadataMap(stageYamlFieldMap,
                  InjectUtils.getDependencyMetadataMapWithInjectType(
                      stageYamlField, ctx, InjectTypes.STAGE.toString(), isParallelChildOfInsertStages)))
              .build());
    }
    boolean isStagesInsideWrapper =
        InjectUtils.isStagesInsideInject(ctx) || PlanCreatorUtilsV1.isStageInsideDynamicStage(ctx);
    if (!ExecutionModeUtils.isRollbackMode(ctx.getExecutionMode()) && !isStagesInsideWrapper) {
      PipelineRollbackStageHelper.addPipelineRollbackStageDependency(responseMap, ctx.getCurrentField());
    }
    return responseMap;
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext ctx, StagesConfig config) {
    boolean setStartingNodeId = true;
    if (InjectUtils.isStagesInsideInject(ctx) || PlanCreatorUtilsV1.isStageInsideDynamicStage(ctx)) {
      setStartingNodeId = false;
    }
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    List<YamlField> stagesYamlField = getStageYamlFields(ctx);
    List<EdgeLayoutList> edgeLayoutLists = new ArrayList<>();
    for (YamlField stageYamlField : stagesYamlField) {
      EdgeLayoutList.Builder stageEdgesBuilder = EdgeLayoutList.newBuilder();
      stageEdgesBuilder.addNextIds(stageYamlField.getNode().getUuid());
      edgeLayoutLists.add(stageEdgesBuilder.build());
    }
    for (int i = 0; i < edgeLayoutLists.size(); i++) {
      YamlField stageYamlField = stagesYamlField.get(i);
      if (stageYamlField.getName().equals("parallel")) {
        continue;
      }
      stageYamlFieldMap.put(stageYamlField.getNode().getUuid(),
          GraphLayoutNode.newBuilder()
              .setNodeUUID(stageYamlField.getNode().getUuid())
              .setNodeType(InjectUtils.getStageType(stageYamlField, ctx))
              .setName(stageYamlField.getNode().getName())
              .setNodeGroup(StepOutcomeGroup.STAGE.name())
              .setNodeIdentifier(stageYamlField.getNode().getIdentifier())
              .setEdgeLayoutList(
                  i + 1 < edgeLayoutLists.size() ? edgeLayoutLists.get(i + 1) : EdgeLayoutList.newBuilder().build())
              .setIsManualExecution(PlanCreatorUtils.isManualExecution(ctx, stageYamlField))
              .build());
    }

    String startingNodeId = InjectUtils.getStartingNodeIdForStagesLayoutNodeMap(setStartingNodeId, stagesYamlField);
    DagMetadata dagMetadata = computeDagMetadata(ctx, stagesYamlField, setStartingNodeId);

    return GraphLayoutResponse.builder()
        .layoutNodes(stageYamlFieldMap)
        .startingNodeId(startingNodeId)
        .startingNodeIds(dagMetadata.startingNodeIds())
        .isDagEnabled(dagMetadata.isDagEnabled())
        .dependencyGraph(dagMetadata.dependencyGraph())
        .build();
  }

  private record DagMetadata(List<String> startingNodeIds, boolean isDagEnabled, DependencyGraphProto dependencyGraph) {
    static DagMetadata empty() {
      return new DagMetadata(new ArrayList<>(), false, null);
    }

    static DagMetadata forSequential(String startingNodeId) {
      return new DagMetadata(Collections.singletonList(startingNodeId), false, null);
    }

    static DagMetadata forDag(List<String> startingNodeIds, DependencyGraphProto dependencyGraph) {
      return new DagMetadata(startingNodeIds, true, dependencyGraph);
    }
  }

  private DagMetadata computeDagMetadata(
      PlanCreationContext ctx, List<YamlField> stagesYamlField, boolean setStartingNodeId) {
    if (!setStartingNodeId || EmptyPredicate.isEmpty(stagesYamlField)) {
      return DagMetadata.empty();
    }

    String firstStageNodeId = stagesYamlField.get(0).getNode().getUuid();

    if (!featureFlagService.isEnabled(ctx.getAccountIdentifier(), FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)
        || !isPipelineDAGEnabled(ctx)) {
      return DagMetadata.forSequential(firstStageNodeId);
    }

    YamlField stagesField = ctx.getCurrentField();
    Map<String, String> stageIdToNodeUuid = PlanCreatorUtils.buildStageIdentifierToNodeUuidMap(stagesField);
    DependencyGraphProto dependencyGraph =
        DependencyUtils.createDependencyGraph(stagesField, stageIdToNodeUuid, YAMLFieldNameConstants.STAGE);
    List<String> rootNodeIds = DependencyUtils.findRootNodesInDependencyGraph(dependencyGraph);
    return DagMetadata.forDag(rootNodeIds, dependencyGraph);
  }

  private boolean isPipelineDAGEnabled(PlanCreationContext ctx) {
    PlanCreationContextValue metadata = ctx.getGlobalContext().get("metadata");
    if (metadata == null || !metadata.hasExecutionContext()) {
      return false;
    }
    return metadata.getExecutionContext().getEnableDAG();
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, StagesConfig config, List<String> childrenNodeIds) {
    StepParameters stepParameters = NGSectionStepParameters.builder()
                                        .childNodeId(childrenNodeIds.get(0))
                                        .logMessage(LOG_MESSAGE_STEP_PARAMETER_FOR_STAGES)
                                        .build();
    FacilitatorObtainment facilitatorObtainments =
        FacilitatorObtainment.newBuilder()
            .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
            .build();
    StepType stepType = StagesStep.STEP_TYPE;
    DependencyGraphProto dependencyGraphProto = null;
    // Check both: Feature Flag AND Pipeline's enableDAG setting
    if (featureFlagService.isEnabled(ctx.getAccountIdentifier(), FeatureName.PIPE_ENABLE_DEPENDENCY_BASED_EXECUTION)
        && isPipelineDAGEnabled(ctx)) {
      YamlField stagesField = ctx.getCurrentField();
      // Build stage identifier to node UUID mapping
      Map<String, String> stageIdToNodeUuid = PlanCreatorUtils.buildStageIdentifierToNodeUuidMap(stagesField);
      dependencyGraphProto =
          DependencyUtils.createDependencyGraph(ctx.getCurrentField(), stageIdToNodeUuid, YAMLFieldNameConstants.STAGE);
      stepParameters = createStagesStepParameters(ctx, dependencyGraphProto);
      stepType = StagesStepWithChildrenSupport.STEP_TYPE;
      facilitatorObtainments =
          FacilitatorObtainment.newBuilder()
              .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
              .build();
    }
    PlanNode planNode = PlanNode.builder()
                            .uuid(ctx.getCurrentField().getNode().getUuid())
                            .identifier(YAMLFieldNameConstants.STAGES)
                            .stepType(stepType)
                            .group(StepOutcomeGroup.STAGES.name())
                            .name(YAMLFieldNameConstants.STAGES)
                            .stepParameters(stepParameters)
                            .facilitatorObtainment(facilitatorObtainments)
                            .skipExpressionChain(false)
                            .build();
    if (dependencyGraphProto != null) {
      planNode.setDependencyGraph(dependencyGraphProto);
    }
    return planNode;
  }

  /**
   * Create StagesStepParameters with proper children IDs for dependency-based execution
   * This replaces the old approach
   */
  private StagesStepParameters createStagesStepParameters(
      PlanCreationContext ctx, DependencyGraphProto dependencyGraphProto) {
    // Calculate which children should start immediately (root nodes for DAG execution)
    List<String> rootNodeIds = DependencyUtils.findRootNodesInDependencyGraph(dependencyGraphProto);
    if (rootNodeIds.isEmpty()) {
      log.error("No initial nodes found with zero dependencies for executionId: {}", ctx.getExecutionUuid());
      throw new InvalidRequestException(
          String.format("No initial nodes found with zero dependencies for executionId: %s", ctx.getExecutionUuid()));
    }
    return StagesStepParameters.builder()
        .childrenIds(rootNodeIds)
        .logMessage(LOG_MESSAGE_STEP_PARAMETER_FOR_STAGES)
        .name(YAMLFieldNameConstants.STAGES)
        .id(ctx.getCurrentField().getNode().getUuid())
        .build();
  }

  @Override
  public Class<StagesConfig> getFieldClass() {
    return StagesConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap("stages", Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0);
  }

  private List<YamlField> getStageYamlFields(PlanCreationContext planCreationContext) {
    List<YamlNode> yamlNodes =
        Optional.of(planCreationContext.getCurrentField().getNode().asArray()).orElse(Collections.emptyList());
    List<YamlField> stageFields = new LinkedList<>();

    yamlNodes.forEach(yamlNode -> {
      YamlField stageField = yamlNode.getField(YAMLFieldNameConstants.STAGE);
      YamlField parallelStageField = yamlNode.getField(YAMLFieldNameConstants.PARALLEL);
      YamlField injectStageField = yamlNode.getField(YAMLFieldNameConstants.INSERT);
      if (stageField != null) {
        stageFields.add(stageField);
      } else if (parallelStageField != null) {
        stageFields.add(parallelStageField);
      } else if (injectStageField != null) {
        stageFields.add(injectStageField);
      }
    });
    return stageFields;
  }
}
