/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.dynamic;

import static io.harness.steps.StepSpecTypeConstants.DYNAMIC_STAGE_TYPE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.children.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.when.utils.RunInfoUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class DynamicStagePlanCreator implements PartialPlanCreator<DynamicStageNode> {
  @Inject private KryoSerializer kryoSerializer;
  @Override
  public Class<DynamicStageNode> getFieldClass() {
    return DynamicStageNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.DYNAMIC_STAGE));
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, DynamicStageNode stageNode) {
    DynamicStageConfig config = stageNode.getDynamicStageConfig();
    if (config == null) {
      throw new InvalidRequestException("Dynamic Stage Yaml does not contain spec");
    }

    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    String planNodeId = StrategyUtils.getSwappedPlanNodeId(ctx, stageNode.getUuid());
    List<AdviserObtainment> adviserObtainmentFromMetaData =
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false, ctx);

    PlanNodeBuilder builder =
        PlanNode.builder()
            .uuid(planNodeId)
            .name(stageNode.getName())
            .identifier(stageNode.getIdentifier())
            .group(StepCategory.STAGE.name())
            .stepType(DYNAMIC_STAGE_TYPE)
            .whenCondition(RunInfoUtils.getRunConditionForStage(stageNode.getWhen(), ctx.getExecutionMode()))
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .adviserObtainments(adviserObtainmentFromMetaData)
            .advisorObtainmentsForExecutionMode(Map.of(ExecutionMode.PIPELINE_ROLLBACK, adviserObtainmentFromMetaData,
                ExecutionMode.POST_EXECUTION_ROLLBACK, adviserObtainmentFromMetaData));

    DynamicStageStepParameters stepParameters = getStepParameter(config);
    YamlField childYamlNode = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STAGES);
    GraphLayoutResponse graphLayoutResponse = GraphLayoutResponse.builder().build();
    Map<String, Dependency> metadataMap = new HashMap<>();

    if (null != childYamlNode) {
      List<YamlField> stageYamlFields =
          PlanCreatorUtils.getStageYamlFields(childYamlNode.getNode(), false, true, false);
      for (YamlField stageYamlField : stageYamlFields) {
        dependenciesNodeMap.put(stageYamlField.getNode().getUuid(), stageYamlField);
        metadataMap.put(stageYamlField.getNode().getUuid(),
            Dependency.newBuilder()
                .setNodeMetadata(HarnessStruct.newBuilder()
                                     .putData(YAMLFieldNameConstants.IS_ANCESTOR_OF_STAGE,
                                         HarnessValue.newBuilder().setBoolValue(true).build())
                                     .build())
                .build());
      }
      graphLayoutResponse = getLayoutNodeInfo(ctx, stageYamlFields);
      if (EmptyPredicate.isNotEmpty(stageYamlFields)) {
        stepParameters.setChildNodeId(stageYamlFields.get(0).getNode().getUuid());
      }
    }

    builder.stepParameters(stepParameters);
    return PlanCreationResponse.builder()
        .planNode(builder.build())
        .graphLayoutResponse(graphLayoutResponse)
        .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                          .toBuilder()
                          .putAllDependencyMetadata(metadataMap)
                          .build())
        .build();
  }

  private DynamicStageStepParameters getStepParameter(DynamicStageConfig config) {
    return DynamicStageStepParameters.builder()
        .source(config.getSource())
        .sourceConfig(config.getSourceConfig())
        .build();
  }

  private GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, List<YamlField> stagesYamlField) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    List<EdgeLayoutList> edgeLayoutLists = new ArrayList<>();
    for (YamlField stageYamlField : stagesYamlField) {
      EdgeLayoutList.Builder stageEdgesBuilder = EdgeLayoutList.newBuilder();
      stageEdgesBuilder.addNextIds(stageYamlField.getNode().getUuid());
      edgeLayoutLists.add(stageEdgesBuilder.build());
    }
    for (int i = 0; i < edgeLayoutLists.size(); i++) {
      YamlField stageYamlField = stagesYamlField.get(i);
      if (stageYamlField.getName().equals(YAMLFieldNameConstants.PARALLEL)) {
        continue;
      }
      stageYamlFieldMap.put(stageYamlField.getNode().getUuid(),
          GraphLayoutNode.newBuilder()
              .setNodeUUID(stageYamlField.getNode().getUuid())
              .setNodeType(stageYamlField.getNode().getType())
              .setName(stageYamlField.getNode().getName())
              .setNodeGroup(StepOutcomeGroup.STAGE.name())
              .setNodeIdentifier(stageYamlField.getNode().getIdentifier())
              .setEdgeLayoutList(
                  i + 1 < edgeLayoutLists.size() ? edgeLayoutLists.get(i + 1) : EdgeLayoutList.newBuilder().build())
              .setIsManualExecution(PlanCreatorUtils.isManualExecution(context, stageYamlField))
              .build());
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }
}