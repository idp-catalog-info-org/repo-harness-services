/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.stages.dynamic.v1;

import static io.harness.steps.StepSpecTypeConstants.DYNAMIC_STAGE_V1_TYPE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.stages.v1.AbstractStagePlanCreator;
import io.harness.plancreator.stages.v1.StageParameterUtilsV1;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1;
import io.harness.plancreator.steps.common.v1.StageElementParametersV1.StageElementParametersV1Builder;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class DynamicStagePlanCreatorV1 extends AbstractStagePlanCreator<DynamicStageNodeV1> {
  @Override
  public DynamicStageNodeV1 getFieldObject(YamlField field) {
    try {
      return YamlUtils.read(field.getNode().toString(), DynamicStageNodeV1.class);
    } catch (IOException e) {
      throw new InvalidYamlException(
          "Unable to parse dynamic stage yaml. Please ensure that it is in correct format", e);
    }
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, DynamicStageNodeV1 stageNode) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();

    Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.DYNAMIC_STAGE_V1),
        "Dynamic stage YAML field ('dynamic') is missing from the current node");

    YamlField embeddedStagesField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STAGES);
    if (embeddedStagesField != null) {
      dependenciesNodeMap.put(embeddedStagesField.getNode().getUuid(), embeddedStagesField);
    }

    Dependency strategyDependency = getDependencyForStrategy(dependenciesNodeMap, stageNode, ctx);

    Dependencies.Builder depsBuilder = DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                                           .toBuilder()
                                           .putDependencyMetadata(stageNode.getUuid(), strategyDependency);

    if (embeddedStagesField != null) {
      Dependency embeddedStagesDep = Dependency.newBuilder()
                                         .setParentInfo(HarnessStruct.newBuilder()
                                                            .putData(PlanCreatorConstants.CHILD_OF_DYNAMIC_STAGE,
                                                                HarnessValue.newBuilder().setBoolValue(true).build())
                                                            .build())
                                         .build();
      depsBuilder = depsBuilder.putDependencyMetadata(embeddedStagesField.getNode().getUuid(), embeddedStagesDep);
    }

    planCreationResponseMap.put(
        stageNode.getUuid(), PlanCreationResponse.builder().dependencies(depsBuilder.build()).build());

    return planCreationResponseMap;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(YAMLFieldNameConstants.DYNAMIC_STAGE_V1));
  }

  @SuppressWarnings("RepetitiveNameCheck")
  @Override
  public StageElementParametersV1 getStageParameters(
      PlanCreationContext ctx, DynamicStageNodeV1 stageNode, List<String> childrenNodeIds) {
    StageElementParametersV1Builder stageParameters = StageParameterUtilsV1.getCommonStageParametersBuilder(stageNode);

    DynamicStageConfigV1 config = stageNode.getDynamicStageConfig();
    if (config == null) {
      throw new InvalidYamlException("Dynamic stage is missing its configuration ('dynamic' key)");
    }

    DynamicStageStepParametersV1.DynamicStageStepParametersV1Builder specBuilder =
        DynamicStageStepParametersV1.builder().source(config.getSource()).sourceConfig(config.getSourceConfig());

    YamlField stagesField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STAGES);
    if (stagesField != null) {
      specBuilder.childNodeId(stagesField.getNode().getUuid());
    }

    stageParameters.spec(specBuilder.build());
    stageParameters.type(YAMLFieldNameConstants.DYNAMIC_STAGE_V1);
    return stageParameters.build();
  }

  @Override
  public StepType getStepType() {
    return DYNAMIC_STAGE_V1_TYPE;
  }
}
