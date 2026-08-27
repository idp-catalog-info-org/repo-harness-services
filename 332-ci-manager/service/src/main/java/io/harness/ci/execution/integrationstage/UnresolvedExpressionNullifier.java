/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class to nullify unresolved expressions in step environment variables.
 * This is needed to prevent unresolved runtime inputs from being passed to containers.
 */
@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class UnresolvedExpressionNullifier {
  // Root keys that should be considered for nullification
  private static final Set<String> NULLIFIABLE_ROOT_KEYS =
      new HashSet<>(Arrays.asList("artifact", "artifact_type", "artifacts", "createTicketOptions", "env",
          "exportedVariables", "infra", "input", "inputs", "json", "manifest", "manifests", "rollback", "runtime",
          "selectDeleteResources", "selectRolloutResources", "service", "skipSteadyStateCheck", "strategy", "taskType",
          "test", "ticketType", "trafficShift", "updateMultiple", "updateTicketOption", "cel"));

  /**
   * Process InitializeStepInfo to nullify unresolved expressions in executionElementConfig and
   * moduleImplicitStepsConfig.
   */
  public void processInitializeStepInfo(
      ExecutionElementConfig executionElementConfig, List<ExecutionWrapperConfig> moduleImplicitStepsConfig) {
    try {
      // Process executionElementConfig steps and rollbackSteps
      if (executionElementConfig != null) {
        if (executionElementConfig.getSteps() != null) {
          processExecutionWrapperList(executionElementConfig.getSteps());
        }
        if (executionElementConfig.getRollbackSteps() != null) {
          processExecutionWrapperList(executionElementConfig.getRollbackSteps());
        }
      }

      // Process moduleImplicitStepsConfig
      if (moduleImplicitStepsConfig != null) {
        processExecutionWrapperList(moduleImplicitStepsConfig);
      }
    } catch (Exception e) {
      // Silently ignore exceptions to not break existing functionality
      log.debug("Exception occurred while processing unresolved expressions", e);
    }
  }

  /**
   * Process a list of ExecutionWrapperConfig to find and nullify unresolved expressions.
   */
  private void processExecutionWrapperList(List<ExecutionWrapperConfig> wrappers) {
    processExecutionWrapperList(wrappers, 0);
  }

  /**
   * Process a list of ExecutionWrapperConfig to find and nullify unresolved expressions.
   * @param wrappers list of execution wrappers
   * @param depth current recursion depth
   */
  private void processExecutionWrapperList(List<ExecutionWrapperConfig> wrappers, int depth) {
    if (isEmpty(wrappers)) {
      return;
    }

    for (ExecutionWrapperConfig wrapper : wrappers) {
      try {
        processExecutionWrapper(wrapper, depth);
      } catch (Exception e) {
        // Silently ignore per-wrapper exceptions
        log.debug("Exception processing execution wrapper", e);
      }
    }
  }

  /**
   * Process a single ExecutionWrapperConfig.
   * Handles step, parallel, and step group cases.
   * @param wrapper execution wrapper to process
   * @param depth current recursion depth
   */
  private void processExecutionWrapper(ExecutionWrapperConfig wrapper, int depth) {
    if (wrapper == null) {
      return;
    }

    // Stop recursion at depth 3
    if (depth > 3) {
      return;
    }

    try {
      // Process step field if present
      if (wrapper.getStep() != null && !wrapper.getStep().isNull()) {
        processStepNode(wrapper.getStep());
      }
      // Process parallel steps if present
      else if (wrapper.getParallel() != null && !wrapper.getParallel().isNull()) {
        processParallelSteps(wrapper, depth);
      }
      // Process step group if present
      else if (wrapper.getStepGroup() != null && !wrapper.getStepGroup().isNull()) {
        processStepGroup(wrapper, depth);
      }
    } catch (Exception e) {
      // Silently ignore exceptions
      log.debug("Exception processing step wrapper", e);
    }
  }

  /**
   * Process a step node to nullify unresolved expressions.
   */
  private void processStepNode(JsonNode stepNode) {
    if (stepNode == null || !stepNode.isObject()) {
      return;
    }

    // Navigate to the "run" field in the JSON
    JsonNode runNode = stepNode.get("run");
    if (runNode != null && runNode.isObject()) {
      processStepTypeNode(runNode);
      return;
    }

    // Navigate to the "background" field in the JSON
    JsonNode backgroundNode = stepNode.get("background");
    if (backgroundNode != null && backgroundNode.isObject()) {
      processStepTypeNode(backgroundNode);
    }
  }

  /**
   * Process a step type node (run or background) to nullify unresolved expressions in env and with fields.
   */
  private void processStepTypeNode(JsonNode typeNode) {
    // Process env field if present
    JsonNode envNode = typeNode.get("env");
    if (envNode != null && envNode.isObject()) {
      processMapNodeDirectly(envNode);
    }

    // Process with field if present
    JsonNode withNode = typeNode.get("with");
    if (withNode != null && withNode.isObject()) {
      processMapNodeDirectly(withNode);
    }
  }

  /**
   * Process parallel steps recursively.
   * Works directly with JsonNode to ensure mutations are preserved.
   */
  private void processParallelSteps(ExecutionWrapperConfig wrapper, int depth) {
    try {
      JsonNode parallelNode = wrapper.getParallel();
      if (parallelNode == null || parallelNode.isNull()) {
        return;
      }

      // V1 wire shape: parallel is a bare JSON array of wrappers.
      if (parallelNode.isArray()) {
        for (JsonNode sectionNode : parallelNode) {
          processJsonNodeAsWrapper(sectionNode, depth + 1);
        }
        return;
      }

      // Legacy wire shape: parallel is an object with a "sections" array.
      if (!parallelNode.isObject()) {
        return;
      }

      // Get sections array
      JsonNode sectionsNode = parallelNode.get("sections");
      if (sectionsNode == null || !sectionsNode.isArray()) {
        return;
      }

      // Process each section
      for (JsonNode sectionNode : sectionsNode) {
        processJsonNodeAsWrapper(sectionNode, depth + 1);
      }
    } catch (Exception e) {
      log.debug("Exception processing parallel steps", e);
    }
  }

  /**
   * Process step group recursively.
   * Works directly with JsonNode to ensure mutations are preserved.
   */
  private void processStepGroup(ExecutionWrapperConfig wrapper, int depth) {
    try {
      JsonNode stepGroupNode = wrapper.getStepGroup();
      if (stepGroupNode == null || !stepGroupNode.isObject()) {
        return;
      }

      // Get steps array
      JsonNode stepsNode = stepGroupNode.get("steps");
      if (stepsNode == null || !stepsNode.isArray()) {
        return;
      }

      // Process each step
      for (JsonNode stepNode : stepsNode) {
        processJsonNodeAsWrapper(stepNode, depth + 1);
      }
    } catch (Exception e) {
      log.debug("Exception processing step group", e);
    }
  }

  /**
   * Process a JsonNode that represents an ExecutionWrapperConfig.
   * This method works directly with JsonNode to preserve mutations.
   */
  private void processJsonNodeAsWrapper(JsonNode wrapperNode, int depth) {
    if (wrapperNode == null || !wrapperNode.isObject()) {
      return;
    }

    // Stop recursion at depth 3
    if (depth > 3) {
      return;
    }

    try {
      // Check if this is a step
      JsonNode stepNode = wrapperNode.get("step");
      if (stepNode != null && !stepNode.isNull()) {
        processStepNode(stepNode);
        return;
      }

      // Check if this is a parallel
      JsonNode parallelNode = wrapperNode.get("parallel");
      if (parallelNode != null && !parallelNode.isNull()) {
        // V1 wire shape: parallel is a bare JSON array of wrappers.
        if (parallelNode.isArray()) {
          for (JsonNode sectionNode : parallelNode) {
            processJsonNodeAsWrapper(sectionNode, depth + 1);
          }
          return;
        }

        // Legacy wire shape: parallel is an object with a "sections" array.
        JsonNode sectionsNode = parallelNode.get("sections");
        if (sectionsNode != null && sectionsNode.isArray()) {
          for (JsonNode sectionNode : sectionsNode) {
            processJsonNodeAsWrapper(sectionNode, depth + 1);
          }
        }
        return;
      }

      // Check if this is a step group
      JsonNode stepGroupNode = wrapperNode.get("stepGroup");
      if (stepGroupNode != null && !stepGroupNode.isNull()) {
        JsonNode stepsNode = stepGroupNode.get("steps");
        if (stepsNode != null && stepsNode.isArray()) {
          for (JsonNode stepInGroupNode : stepsNode) {
            processJsonNodeAsWrapper(stepInGroupNode, depth + 1);
          }
        }
        return;
      }
    } catch (Exception e) {
      log.debug("Exception processing wrapper node", e);
    }
  }

  /**
   * Process a map node directly to nullify unresolved expressions.
   * Works with JsonNode without deserialization.
   * Returns true if any modification was made.
   */
  private boolean processMapNodeDirectly(JsonNode mapNode) {
    if (!(mapNode instanceof ObjectNode)) {
      return false;
    }

    ObjectNode objectNode = (ObjectNode) mapNode;
    boolean modified = false;
    var fields = objectNode.fields();

    while (fields.hasNext()) {
      try {
        var entry = fields.next();
        String key = entry.getKey();
        JsonNode value = entry.getValue();

        if (value != null && value.isTextual()) {
          String textValue = value.asText();
          if (shouldNullifyExpression(textValue)) {
            // Directly modify the ObjectNode
            objectNode.set(key, NullNode.getInstance());
            modified = true;
          }
        }
      } catch (Exception e) {
        // Silently ignore per-entry exceptions
        log.debug("Exception processing map node entry", e);
      }
    }

    return modified;
  }

  /**
   * Check if an expression should be nullified.
   * Returns true if the expression starts with ${{ or <+ and contains one of the nullifiable root keys.
   */
  private boolean shouldNullifyExpression(String value) {
    if (isEmpty(value)) {
      return false;
    }

    String trimmed = value.trim();

    // Check if starts with ${{ or <+
    boolean startsWithExpression = trimmed.startsWith("${{") || trimmed.startsWith("<+");
    if (!startsWithExpression) {
      return false;
    }

    // Extract the root key after the expression prefix
    String rootKey = extractRootKey(trimmed);
    if (rootKey == null) {
      return false;
    }

    // Check if root key is in the nullifiable set
    return NULLIFIABLE_ROOT_KEYS.contains(rootKey);
  }

  /**
   * Extract the root key from an expression.
   * For ${{input.something}} returns "input"
   * For <+artifact.tag> returns "artifact"
   */
  private String extractRootKey(String expression) {
    try {
      String content;
      if (expression.startsWith("${{")) {
        // Extract content between ${{ and }}
        int endIdx = expression.indexOf("}}");
        if (endIdx == -1) {
          return null;
        }
        content = expression.substring(3, endIdx).trim();
      } else if (expression.startsWith("<+")) {
        // Extract content after <+
        int endIdx = expression.indexOf('>');
        if (endIdx == -1) {
          // If no closing >, take everything after <+
          content = expression.substring(2).trim();
        } else {
          content = expression.substring(2, endIdx).trim();
        }
      } else {
        return null;
      }

      // Extract first part before dot or any special character
      if (content.isEmpty()) {
        return null;
      }

      // Find first dot
      int dotIdx = content.indexOf('.');
      if (dotIdx > 0) {
        return content.substring(0, dotIdx);
      }

      // No dot, return the whole content
      return content;
    } catch (Exception e) {
      log.debug("Exception extracting root key from: {}", expression, e);
      return null;
    }
  }
}
