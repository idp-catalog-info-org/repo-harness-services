/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plancreator.V1;

import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.RENDERING_NODE_ID;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.RENDERING_NODE_NAME;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.service.v1.ManifestStepConstants.PLUGIN_ADD_ON_FILE_PATHS;
import static io.harness.ng.core.service.v1.ManifestStepConstants.PLUGIN_FETCH_FILE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.states.V1.cd.RenderingStep;
import io.harness.ci.states.V1.cd.RenderingStepParameters;
import io.harness.ci.states.V1.cd.RenderingStepParameters.RenderingStepParametersBuilder;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.v1.RunInfoUtilsV1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CI)
public class RenderingPlanCreator {
  @Inject private KryoSerializer kryoSerializer;

  public String addRenderingNode(LinkedHashMap<String, PlanCreationResponse> responseMap, YamlField curr, String nextId,
      JsonNode stepEnvNode, boolean isStepInsideRollback,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    RenderingStepParameters stepParameters = getRenderingStepParameters(curr, stepEnvNode, envVars);
    PlanNode renderingStepNode = getRenderingStepNode(curr, nextId, stepParameters, isStepInsideRollback);
    responseMap.put(renderingStepNode.getUuid(), PlanCreationResponse.builder().planNode(renderingStepNode).build());
    return renderingStepNode.getUuid();
  }

  private PlanNode getRenderingStepNode(
      YamlField curr, String nextId, RenderingStepParameters stepParameters, boolean isStepInsideRollback) {
    return PlanNode.builder()
        .uuid(curr.getUuid())
        .stepType(RenderingStep.STEP_TYPE)
        .name(isNotEmpty(stepParameters.getName()) ? stepParameters.getName() : RENDERING_NODE_NAME)
        .identifier(isNotEmpty(stepParameters.getId()) ? stepParameters.getId() : RENDERING_NODE_ID)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC_CHAIN).build())
                .build())
        .adviserObtainment(
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                .setParameters(ByteString.copyFrom(
                    kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(nextId).build())))
                .build())
        .whenCondition(RunInfoUtilsV1.getStepWhenCondition(null, isStepInsideRollback))
        .skipExpressionChain(true)
        .build();
  }

  private RenderingStepParameters getRenderingStepParameters(
      YamlField curr, JsonNode stepEnvNode, ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    YamlNode idNode = curr.getNode().getField(YamlNode.ID_FIELD_NAME).getNode();
    if (!(idNode.getCurrJsonNode() instanceof TextNode id)) {
      throw new InvalidYamlException("Rendering step node id is not configured");
    }

    YamlNode nameNode = curr.getNode().getField(YamlNode.NAME_FIELD_NAME).getNode();
    if (!(nameNode.getCurrJsonNode() instanceof TextNode name)) {
      throw new InvalidYamlException("Rendering step node name is not configured");
    }

    RenderingStepParametersBuilder stepParametersBuilder = RenderingStepParameters.builder()
                                                               .stepId(idNode.getCurrJsonNode().asText())
                                                               .id(id.asText())
                                                               .name(name.asText())
                                                               .envVars(envVars);

    if (stepEnvNode != null) {
      if (stepEnvNode.get(PLUGIN_FETCH_FILE) != null) {
        stepParametersBuilder.fetch(Boolean.TRUE.equals(stepEnvNode.get(PLUGIN_FETCH_FILE).asBoolean()));
      }
      if (stepEnvNode.get(PLUGIN_ADD_ON_FILE_PATHS) != null) {
        String pluginAddOnFilePaths = stepEnvNode.get(PLUGIN_ADD_ON_FILE_PATHS).asText();
        List<String> pluginAddOnFilePathsList =
            Arrays.stream(pluginAddOnFilePaths.split(",")).map(String::trim).toList();

        stepParametersBuilder.addOnFiles(pluginAddOnFilePathsList);
      }
    }

    return stepParametersBuilder.build();
  }
}
