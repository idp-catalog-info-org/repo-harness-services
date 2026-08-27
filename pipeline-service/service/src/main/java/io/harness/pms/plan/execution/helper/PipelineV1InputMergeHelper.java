/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidYamlException;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
public class PipelineV1InputMergeHelper {
  private static final String VALUE = "value";
  public static final String INPUTS = "inputs";
  private static final String OVERLAY = "overlay";

  public JsonNode mergeUserInputsToPipelineEntityInputs(JsonNode pipelineJsonNode, JsonNode inputsJsonNode) {
    if (!JsonNodeUtils.isNull(inputsJsonNode)) {
      try {
        JsonNode inputInputsJsonNode = inputsJsonNode.get(INPUTS);
        if (!JsonNodeUtils.isNull(pipelineJsonNode.get(YAMLFieldNameConstants.PIPELINE))
            && !JsonNodeUtils.isNull(pipelineJsonNode.get(YAMLFieldNameConstants.PIPELINE).get(INPUTS))) {
          ObjectNode pipelineEntityInputs =
              (ObjectNode) pipelineJsonNode.get(YAMLFieldNameConstants.PIPELINE).get(INPUTS);

          if (pipelineEntityInputs != null) {
            pipelineEntityInputs.fieldNames().forEachRemaining(fieldName -> {
              if (inputInputsJsonNode.has(fieldName)) {
                JsonNode pipelineField = pipelineEntityInputs.get(fieldName);
                ((ObjectNode) pipelineField).put(VALUE, inputInputsJsonNode.get(fieldName));
              }
            });
          }
        }
      } catch (Exception ex) {
        throw new InvalidYamlException(
            "Could not merge inputs to pipeline yaml, Please check inputs format provided", ex);
      }
    }
    return pipelineJsonNode;
  }

  /**
   * Merges flat runtime input values from {@code inputs.overlay.stages[]} into each stage's typed
   * {@code inputs.<name>.value} field. Mirrors {@link #mergeUserInputsToPipelineEntityInputs} for stage-level inputs.
   *
   * <p>Execute API overlay format:
   * <pre>
   * inputs:
   *   overlay:
   *     stages:
   *       - id: stage
   *         inputs:
   *           SPECS_PATH: testValueForPath
   * </pre>
   */
  public JsonNode mergeUserInputsToStageEntityInputs(JsonNode pipelineJsonNode, JsonNode inputsJsonNode) {
    if (JsonNodeUtils.isNull(inputsJsonNode) || JsonNodeUtils.isNull(pipelineJsonNode)) {
      return pipelineJsonNode;
    }
    try {
      JsonNode inputsNode = inputsJsonNode.get(INPUTS);
      if (JsonNodeUtils.isNull(inputsNode)) {
        return pipelineJsonNode;
      }
      JsonNode overlayStages = getOverlayStages(inputsNode);
      if (overlayStages == null || !overlayStages.isArray() || overlayStages.isEmpty()) {
        return pipelineJsonNode;
      }

      JsonNode pipelineNode = resolvePipelineNode(pipelineJsonNode);
      if (JsonNodeUtils.isNull(pipelineNode) || !pipelineNode.has(YAMLFieldNameConstants.STAGES)) {
        return pipelineJsonNode;
      }

      Map<String, JsonNode> overlayStagesById = indexOverlayStagesById((ArrayNode) overlayStages);
      if (overlayStagesById.isEmpty()) {
        return pipelineJsonNode;
      }

      mergeStageInputsInStagesArray((ArrayNode) pipelineNode.get(YAMLFieldNameConstants.STAGES), overlayStagesById);
    } catch (Exception ex) {
      throw new InvalidYamlException(
          "Could not merge stage inputs to pipeline yaml, Please check inputs format provided", ex);
    }
    return pipelineJsonNode;
  }

  /**
   * Applies all V1 runtime-input merges for pipeline execute flows. Must only be invoked for V1 pipelines;
   * V0 pipelines use {@link io.harness.pms.merger.helpers.MergeHelper#mergeInputSetFormatYamlToOriginYaml} instead.
   *
   * <p>Order: overlay FQN merge, pipeline-level typed inputs merge, then stage-level typed inputs merge.
   */
  public JsonNode mergeV1UserProvidedInputs(
      JsonNode pipelineJsonNode, JsonNode inputsJsonNode, boolean processAdditionalBaseKeys, String pipelineYaml) {
    if (JsonNodeUtils.isNull(inputsJsonNode) || JsonNodeUtils.isNull(pipelineJsonNode)) {
      return pipelineJsonNode;
    }
    JsonNode pipelineInputsJsonNode = inputsJsonNode.get(INPUTS);
    if (!JsonNodeUtils.isNull(pipelineInputsJsonNode)) {
      pipelineJsonNode =
          mergePipelineOverlayInputs(processAdditionalBaseKeys, pipelineYaml, pipelineJsonNode, pipelineInputsJsonNode);
    }
    pipelineJsonNode = mergeUserInputsToPipelineEntityInputs(pipelineJsonNode, inputsJsonNode);
    return mergeUserInputsToStageEntityInputs(pipelineJsonNode, inputsJsonNode);
  }

  public JsonNode mergePipelineOverlayInputs(boolean processAdditionalBaseKeys, String pipelineYaml,
      JsonNode pipelineEntityJsonNode, JsonNode pipelineInputsJsonNode) {
    if (pipelineInputsJsonNode == null || pipelineInputsJsonNode.isNull()) {
      return pipelineEntityJsonNode;
    }

    ObjectNode pipelineInputsRootNode = buildPipelineInputsRootNode(pipelineInputsJsonNode, pipelineEntityJsonNode);
    if (pipelineInputsRootNode != null) {
      pipelineEntityJsonNode = MergeHelper.mergePipelineCloneRefOverlay(pipelineEntityJsonNode, pipelineInputsJsonNode);
      pipelineEntityJsonNode = MergeHelper.mergeRuntimeInputValuesIntoOriginalYamlInternal(pipelineEntityJsonNode,
          pipelineInputsRootNode, true, false, true, processAdditionalBaseKeys, HarnessYamlVersion.V1);
    }
    return pipelineEntityJsonNode;
  }

  /**
   * Builds the root node for the overlay FQN merge.
   *
   * Two overlay content shapes are handled:
   * 1. Template pipeline — overlay content already starts with "pipeline:" and mirrors the full
   *    entity FQN path. e.g. { pipeline: { template: { with: { overlay: { ... } } } } }
   *    Used directly as the merge root.
   * 2. Direct pipeline — overlay content is the pipeline body without "pipeline:" wrapper.
   *    e.g. { stages: [...] }
   *    Wrapped under "pipeline:" so FQN paths match the entity.
   *
   * @return the root node to merge, or null if no overlay content is present.
   */
  private ObjectNode buildPipelineInputsRootNode(JsonNode pipelineInputsJsonNode, JsonNode pipelineEntityJsonNode) {
    if (!pipelineInputsJsonNode.has(OVERLAY) || pipelineInputsJsonNode.get(OVERLAY).isNull()) {
      return null;
    }
    JsonNode overlayContent = pipelineInputsJsonNode.get(OVERLAY);
    if (!overlayContent.isObject()) {
      return null;
    }

    // If overlay content already starts with "pipeline:", it mirrors the full entity FQN path
    if (overlayContent.has(YAMLFieldNameConstants.PIPELINE)) {
      return overlayContent.deepCopy();
    }

    // Direct pipeline: overlay content is the body (e.g. { stages: [...] }) — wrap under "pipeline:"
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode rootNode = mapper.createObjectNode();
    rootNode.set(YAMLFieldNameConstants.PIPELINE, overlayContent);
    return rootNode;
  }

  private JsonNode getOverlayStages(JsonNode inputsNode) {
    if (!inputsNode.has(OVERLAY) || inputsNode.get(OVERLAY).isNull()) {
      return null;
    }
    JsonNode overlayContent = inputsNode.get(OVERLAY);
    if (overlayContent.has(YAMLFieldNameConstants.PIPELINE)
        && overlayContent.get(YAMLFieldNameConstants.PIPELINE).has(YAMLFieldNameConstants.STAGES)) {
      return overlayContent.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);
    }
    if (overlayContent.has(YAMLFieldNameConstants.STAGES)) {
      return overlayContent.get(YAMLFieldNameConstants.STAGES);
    }
    return null;
  }

  private Map<String, JsonNode> indexOverlayStagesById(ArrayNode overlayStages) {
    Map<String, JsonNode> overlayStagesById = new HashMap<>();
    for (JsonNode overlayStage : overlayStages) {
      String stageId = getStageId(overlayStage);
      if (EmptyPredicate.isNotEmpty(stageId)) {
        overlayStagesById.put(stageId, overlayStage);
      }
    }
    return overlayStagesById;
  }

  private void mergeStageInputsInStagesArray(ArrayNode pipelineStages, Map<String, JsonNode> overlayStagesById) {
    for (JsonNode pipelineStage : pipelineStages) {
      if (pipelineStage == null || pipelineStage.isNull()) {
        continue;
      }
      // V1 parallel stages: parallel.stages[]
      if (pipelineStage.has(YAMLFieldNameConstants.PARALLEL)) {
        JsonNode parallelNode = pipelineStage.get(YAMLFieldNameConstants.PARALLEL);
        if (parallelNode != null && parallelNode.has(YAMLFieldNameConstants.STAGES)) {
          mergeStageInputsInStagesArray((ArrayNode) parallelNode.get(YAMLFieldNameConstants.STAGES), overlayStagesById);
        }
        continue;
      }
      // V1 stage groups: group.stages[]
      if (pipelineStage.has(YAMLFieldNameConstants.GROUP)) {
        JsonNode groupNode = pipelineStage.get(YAMLFieldNameConstants.GROUP);
        if (groupNode != null && groupNode.has(YAMLFieldNameConstants.STAGES)) {
          mergeStageInputsInStagesArray((ArrayNode) groupNode.get(YAMLFieldNameConstants.STAGES), overlayStagesById);
        }
        continue;
      }
      // INSERT is not supported in V1 pipelines (see PipelineYamlUtils#getStagesNodeFromInjectNode).
      String stageId = getStageId(pipelineStage);
      if (EmptyPredicate.isEmpty(stageId) || !overlayStagesById.containsKey(stageId)) {
        continue;
      }
      mergeInputsOnStage((ObjectNode) pipelineStage, overlayStagesById.get(stageId));
    }
  }

  private void mergeInputsOnStage(ObjectNode pipelineStage, JsonNode overlayStage) {
    if (!overlayStage.has(INPUTS) || !pipelineStage.has(INPUTS)) {
      return;
    }
    JsonNode overlayInputs = overlayStage.get(INPUTS);
    if (!overlayInputs.isObject()) {
      return;
    }
    ObjectNode pipelineInputs = (ObjectNode) pipelineStage.get(INPUTS);
    Iterator<Map.Entry<String, JsonNode>> fields = overlayInputs.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode pipelineInputField = pipelineInputs.get(entry.getKey());
      if (pipelineInputField != null && pipelineInputField.isObject()) {
        mergeRuntimeInputValue((ObjectNode) pipelineInputField, entry.getValue());
      }
    }
  }

  private void mergeRuntimeInputValue(ObjectNode typedInputNode, JsonNode runtimeInputValue) {
    if (runtimeInputValue == null || runtimeInputValue.isNull()) {
      return;
    }
    if (runtimeInputValue.isObject() && runtimeInputValue.has(VALUE)) {
      typedInputNode.set(VALUE, runtimeInputValue.get(VALUE));
      return;
    }
    if (!runtimeInputValue.isObject()) {
      typedInputNode.set(VALUE, runtimeInputValue);
    }
  }

  private String getStageId(JsonNode stageNode) {
    if (stageNode.has(YAMLFieldNameConstants.ID)) {
      return stageNode.get(YAMLFieldNameConstants.ID).asText();
    }
    if (stageNode.has(YAMLFieldNameConstants.IDENTIFIER)) {
      return stageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText();
    }
    return null;
  }

  private JsonNode resolvePipelineNode(JsonNode pipelineJsonNode) {
    if (pipelineJsonNode.has(YAMLFieldNameConstants.PIPELINE)) {
      return pipelineJsonNode.get(YAMLFieldNameConstants.PIPELINE);
    }
    if (pipelineJsonNode.has(YAMLFieldNameConstants.STAGES)) {
      return pipelineJsonNode;
    }
    return null;
  }
}
