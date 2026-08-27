/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputs.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to detect if pipeline-level clone.ref should be exposed as a runtime input.
 *
 * This is a special case for V1 YAML where:
 * 1. Pipeline has clone defined at pipeline level
 * 2. Clone is enabled (not explicitly disabled)
 * 3. Clone.ref is NOT defined
 *
 * Unlike regular runtime inputs,<+input> marker is needed.
 * If ref is missing and clone is enabled, it automatically becomes a runtime input.
 */
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class CloneRefRuntimeInputHelper {
  private static final String CLONE = "clone";
  private static final String REF = "ref";
  private static final String ENABLED = "enabled";
  private static final String DISABLED = "disabled";
  private static final String RUNTIME_INPUT_MARKER = "<+input>";

  // Checks if pipeline-level clone.ref should be exposed as a runtime input.
  public boolean shouldPipelineCloneRefRuntimeInput(String pipelineYaml) {
    try {
      JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineYaml);
      if (pipelineJsonNode == null || !pipelineJsonNode.has(YAMLFieldNameConstants.PIPELINE)) {
        return false;
      }

      JsonNode pipelineNode = pipelineJsonNode.get(YAMLFieldNameConstants.PIPELINE);
      if (pipelineNode == null || !pipelineNode.has(CLONE)) {
        return false;
      }

      JsonNode cloneNode = pipelineNode.get(CLONE);
      return shouldCloneRefRuntimeInput(cloneNode);
    } catch (Exception e) {
      log.warn("Error checking pipeline clone ref runtime input status", e);
      return false;
    }
  }

  public String injectCloneRefAsRuntimeInput(String pipelineYaml) {
    try {
      if (!shouldPipelineCloneRefRuntimeInput(pipelineYaml)) {
        return pipelineYaml;
      }
      JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineYaml);
      JsonNode pipelineNode = pipelineJsonNode.get(YAMLFieldNameConstants.PIPELINE);
      JsonNode cloneNode = pipelineNode.get(CLONE);

      if (cloneNode.isBoolean() || cloneNode.isTextual()) {
        // Clone is just "true" or similar - convert to object with enabled and ref
        boolean enabled = cloneNode.isBoolean() ? cloneNode.asBoolean() : Boolean.parseBoolean(cloneNode.asText());
        ObjectNode cloneNodeToBeUpdated = YamlUtils.getObjectNode();
        YamlUtils.updateNode(cloneNodeToBeUpdated, ENABLED, enabled);
        YamlUtils.updateNode(cloneNodeToBeUpdated, REF, RUNTIME_INPUT_MARKER);
        ObjectNode obj = (ObjectNode) pipelineNode;
        YamlUtils.setEntityInObjectNode(obj, CLONE, cloneNodeToBeUpdated);
      } else if (cloneNode.isObject()) {
        // Clone is already an object - just add ref
        YamlUtils.updateNode(cloneNode, REF, RUNTIME_INPUT_MARKER);
      }
      return YamlUtils.writeYamlString(pipelineJsonNode);
    } catch (Exception e) {
      log.warn("Error injecting clone.ref runtime input marker", e);
      return pipelineYaml;
    }
  }

  // Checks if clone.ref should be a runtime input based on the clone node.
  private boolean shouldCloneRefRuntimeInput(JsonNode cloneNode) {
    if (cloneNode == null || cloneNode.isNull()) {
      return false;
    }

    // If clone is just a boolean false or "false" string, clone is disabled
    if (cloneNode.isBoolean() && !cloneNode.asBoolean()) {
      return false;
    }
    if (cloneNode.isTextual() && cloneNode.asText().equalsIgnoreCase("false")) {
      return false;
    }

    // If clone is an object, check enabled/disabled status and ref presence
    if (cloneNode.isObject()) {
      if (cloneNode.has(DISABLED)) {
        JsonNode disabledNode = cloneNode.get(DISABLED);
        if (disabledNode != null && YamlUtils.isNodeEvaluatesToTrue(disabledNode)) {
          return false;
        }
      }

      if (cloneNode.has(ENABLED)) {
        JsonNode enabledNode = cloneNode.get(ENABLED);
        if (enabledNode != null && !YamlUtils.isNodeEvaluatesToTrue(enabledNode)) {
          return false;
        }
      }

      if (!cloneNode.has(REF) || cloneNode.get(REF) == null || cloneNode.get(REF).isNull()) {
        // ref is missing - this should be a runtime input
        return true;
      }

      // ref is present - no runtime input needed
      return false;
    }

    // If clone is just "true" or similar (enabled but no details),
    // ref is missing and should be a runtime input
    return true;
  }
}
