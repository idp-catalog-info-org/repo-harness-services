/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.v1;

import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.pms.plan.creation.PlanCreatorConstants.YAML_VERSION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.pipelinerollback.PipelineRollbackStageHelper;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.children.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StagesStep;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.utils.execution.ExecutionModeUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
public class StagesPlanCreatorV1 extends ChildrenPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    List<YamlField> stages = getStageYamlFields(config);
    int numElementsInStages = stages.size();
    String prbStageYamlPath;
    if (config.getNode().isArray()) {
      prbStageYamlPath = "pipeline/stages/[" + numElementsInStages + "]";
    } else {
      prbStageYamlPath = "pipeline/jobs/pipelineRollback";
    }
    if (EmptyPredicate.isEmpty(stages)) {
      return responseMap;
    }
    String prbStageUuid = "";
    JsonNode prbStageJsonNode = null;
    boolean isStepsInsideGroup = PlanCreatorUtilsV1.isStepsInsideGroup(ctx.getDependency());
    boolean isInsideDynamicStage = PlanCreatorUtilsV1.isStageInsideDynamicStage(ctx);
    if (!ExecutionModeUtils.isRollbackMode(ctx.getExecutionMode()) && !isStepsInsideGroup && !isInsideDynamicStage) {
      prbStageJsonNode = PipelineRollbackStageHelper.buildPipelineRollbackStageJsonNodeV1();
      prbStageUuid = prbStageJsonNode.get(YamlNode.UUID_FIELD_NAME).asText();
    }
    int i;
    YamlField curr;
    for (i = 0; i < stages.size() - 1; i++) {
      curr = getStageField(stages.get(i));
      String version = getYamlVersionFromStageField(stages.get(i));
      String nextId = getStageField(stages.get(i + 1)).getUuid();
      HarnessStruct.Builder parentInfo = getParentInfo(prbStageUuid, version);
      addStageFqnToParentInfo(parentInfo, stages.get(i), version);
      Dependency dependency =
          Dependency.newBuilder()
              .setParentInfo(parentInfo)
              .setNodeMetadata(
                  HarnessStruct.newBuilder()
                      .putAllData(ctx.getDependency().getNodeMetadata().getDataMap())
                      .putData(PlanCreatorConstants.NEXT_ID, HarnessValue.newBuilder().setStringValue(nextId).build())
                      .putData("parent", HarnessValue.newBuilder().setStringValue("stages").build())
                      .build())
              .build();
      responseMap.put(curr.getUuid(),
          PlanCreationResponse.builder()
              .dependencies(Dependencies.newBuilder()
                                .putDependencies(curr.getUuid(), curr.getYamlPath())
                                .putDependencyMetadata(curr.getUuid(), dependency)
                                .build())
              .build());
    }

    curr = getStageField(stages.get(i));
    String version = getYamlVersionFromStageField(stages.get(i));
    if (curr.getNode().getField(YAMLFieldNameConstants.STAGE) != null) {
      curr = curr.getNode().getField(YAMLFieldNameConstants.STAGE);
      version = HarnessYamlVersion.V0;
    }

    HarnessStruct.Builder parentInfo = getParentInfo(prbStageUuid, version);
    addStageFqnToParentInfo(parentInfo, stages.get(i), version);
    Dependency dependency =
        Dependency.newBuilder()
            .setParentInfo(parentInfo)
            .setNodeMetadata(HarnessStruct.newBuilder()
                                 .putData(PlanCreatorConstants.NEXT_ID,
                                     HarnessValue.newBuilder().setStringValue(prbStageUuid).build())
                                 .putData("parent", HarnessValue.newBuilder().setStringValue("stages").build())
                                 .putAllData(ctx.getDependency().getNodeMetadata().getDataMap())
                                 .build())
            .build();
    responseMap.put(curr.getUuid(),
        PlanCreationResponse.builder()
            .dependencies(Dependencies.newBuilder()
                              .putDependencies(curr.getUuid(), curr.getYamlPath())
                              .putDependencyMetadata(curr.getUuid(), dependency)
                              .build())
            .build());
    if (!ExecutionModeUtils.isRollbackMode(ctx.getExecutionMode()) && !isStepsInsideGroup && !isInsideDynamicStage) {
      PipelineRollbackStageHelper.addPipelineRollbackStageDependencyV1(
          prbStageUuid, prbStageJsonNode, responseMap, ctx.getCurrentField(), prbStageYamlPath);
    }
    return responseMap;
  }

  private void addStageFqnToParentInfo(HarnessStruct.Builder parentInfo, YamlField stageField, String version) {
    if (HarnessYamlVersion.isV1(version)) {
      String stageId = stageField.getNode().getId();
      if (stageId != null) {
        parentInfo.putData(PlanCreatorConstants.STAGE_FQN,
            HarnessValue.newBuilder().setStringValue(PlanCreatorConstants.STAGE_PREFIX_FQN + stageId).build());
      } else {
        parentInfo.putData(PlanCreatorConstants.STAGE_FQN,
            HarnessValue.newBuilder().setStringValue(YAMLFieldNameConstants.STAGES).build());
      }
    }
  }

  private HarnessStruct.Builder getParentInfo(String prbStageUuid, String version) {
    HarnessStruct.Builder parentInfo = HarnessStruct.newBuilder();
    parentInfo.putData(YAML_VERSION, HarnessValue.newBuilder().setStringValue(version).build());
    parentInfo.putData(PlanCreatorConstants.PIPELINE_ROLLBACK_STAGE_UUID,
        HarnessValue.newBuilder().setStringValue(prbStageUuid).build());
    return parentInfo;
  }

  private YamlField getStageField(YamlField currField) {
    if (currField.getNode().getField(YAMLFieldNameConstants.STAGE) != null) {
      return currField.getNode().getField(YAMLFieldNameConstants.STAGE);
    }
    return currField;
  }

  private String getYamlVersionFromStageField(YamlField currField) {
    if (currField.getNode().getField(YAMLFieldNameConstants.STAGE) != null
        || YAMLFieldNameConstants.STAGE.equals(currField.getNode().getFieldName())) {
      return HarnessYamlVersion.V0;
    }
    return HarnessYamlVersion.V1;
  }
  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext ctx, YamlField config) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    List<YamlField> stagesYamlField =
        getStageYamlFields(config).stream().map(this::getStageField).collect(Collectors.toList());
    List<EdgeLayoutList> edgeLayoutLists = new ArrayList<>();
    for (YamlField stageYamlField : stagesYamlField) {
      EdgeLayoutList.Builder stageEdgesBuilder = EdgeLayoutList.newBuilder();
      stageEdgesBuilder.addNextIds(stageYamlField.getNode().getUuid());
      edgeLayoutLists.add(stageEdgesBuilder.build());
    }
    for (int i = 0; i < edgeLayoutLists.size(); i++) {
      YamlField stageYamlField = stagesYamlField.get(i);
      if (stageYamlField.getNode().getField("parallel") != null) {
        continue;
      }
      stageYamlFieldMap.put(stageYamlField.getNode().getUuid(),
          GraphLayoutNode.newBuilder()
              .setNodeUUID(stageYamlField.getNode().getUuid())
              .setNodeType("unified")
              .setName(emptyIfNull(stageYamlField.getNode().getName()))
              .setNodeGroup(StepOutcomeGroup.STAGE.name())
              .setNodeIdentifier(emptyIfNull(stageYamlField.getNode().getIdentifier()))
              .setEdgeLayoutList(
                  i + 1 < edgeLayoutLists.size() ? edgeLayoutLists.get(i + 1) : EdgeLayoutList.newBuilder().build())
              .build());
    }
    if (shouldSetStartingNodeId(ctx)) {
      String startingNodeId = stagesYamlField.get(0).getNode().getUuid();
      // For V1 pipelines, DAG support is not yet implemented
      // isDagEnabled=false, dependencyGraph=null, startingNodeIds=[first stage]
      return GraphLayoutResponse.builder()
          .layoutNodes(stageYamlFieldMap)
          .startingNodeId(startingNodeId)
          .startingNodeIds(Collections.singletonList(startingNodeId))
          .isDagEnabled(false)
          .dependencyGraph(null)
          .build();
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).isDagEnabled(false).build();
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    String facilitatorType = OrchestrationFacilitatorType.CHILD;
    StepType stepType = StagesStep.STEP_TYPE;
    StepParameters stepParameters =
        NGSectionStepParameters.builder().childNodeId(childrenNodeIds.get(0)).logMessage("Stages").build();
    String identifier = YAMLFieldNameConstants.STAGES;
    String name = YAMLFieldNameConstants.STAGES;
    if (!config.getNode().isArray()) {
      identifier = name = YAMLFieldNameConstants.JOBS;
    }
    return PlanNode.builder()
        .uuid(ctx.getCurrentField().getNode().getUuid())
        .identifier(identifier)
        .stepType(stepType)
        .group(StepOutcomeGroup.STAGES.name())
        .name(name)
        .stepParameters(stepParameters)
        .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                   .setType(FacilitatorType.newBuilder().setType(facilitatorType).build())
                                   .build())
        .skipExpressionChain(false)
        .build();
  }

  @Override
  public YamlField getFieldObject(YamlField field) {
    return field;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Map.of("stages", Collections.singleton(PlanCreatorUtils.ANY_TYPE), YAMLFieldNameConstants.JOBS,
        Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  private List<YamlField> getStageYamlFields(YamlField yamlField) {
    if (yamlField.getNode().isArray()) {
      List<YamlNode> yamlNodes = Optional.of(yamlField.getNode().asArray()).orElse(Collections.emptyList());
      return yamlNodes.stream().map(YamlField::new).collect(Collectors.toList());
    } else {
      List<YamlField> stages = new ArrayList<>();
      for (Iterator<Map.Entry<String, JsonNode>> it = yamlField.getNode().getCurrJsonNode().fields(); it.hasNext();) {
        Map.Entry<String, JsonNode> field = it.next();
        if (!(field.getValue() instanceof TextNode)) {
          stages.add(new YamlField(new YamlNode(field.getKey(), field.getValue(), yamlField.getNode())));
        }
      }
      return stages;
    }
  }

  private boolean shouldSetStartingNodeId(PlanCreationContext ctx) {
    Optional<Object> value = PlanCreatorUtilsV1.getDeserializedObjectFromDependency(
        ctx.getDependency(), kryoSerializer, PlanCreatorConstants.SET_STARTING_NODE_ID, false);
    return value.isPresent() && (boolean) value.get();
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }
}
