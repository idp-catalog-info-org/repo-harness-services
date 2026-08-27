/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class to inject collection stages for dynamic test splitting.
 * Follows the same pattern as InputSetMergeHelperV1.mergeBuildIntelligenceYamlToPipelineYaml()
 */
@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class DynamicTestSplittingYamlMerger {
  /**
   * Scans the pipeline for CI stages with enable_dynamic_test_split: true
   * and injects a collection stage before each such stage.
   *
   * This is called from ExecutionHelper before plan creation starts.
   *
   * @param pipelineNode the pipeline JsonNode
   * @return modified pipeline JsonNode with collection stages injected
   */
  public JsonNode injectCollectionStages(JsonNode pipelineNode) {
    try {
      // Validate pipeline structure
      if (!YamlUtils.isYamlFieldPresent(pipelineNode, YAMLFieldNameConstants.PIPELINE)) {
        log.debug("Pipeline field not present, skipping dynamic test splitting injection");
        return pipelineNode;
      }

      ObjectNode pipelineObj = (ObjectNode) pipelineNode;
      ObjectNode pipelineContent = (ObjectNode) pipelineObj.get(YAMLFieldNameConstants.PIPELINE);

      if (!YamlUtils.isYamlFieldPresent(pipelineContent, YAMLFieldNameConstants.STAGES)) {
        log.debug("Stages field not present, skipping dynamic test splitting injection");
        return pipelineNode;
      }

      JsonNode stagesNode = pipelineContent.get(YAMLFieldNameConstants.STAGES);
      if (!stagesNode.isArray()) {
        log.warn("Stages is not an array, skipping dynamic test splitting injection");
        return pipelineNode;
      }

      ArrayNode stagesArray = (ArrayNode) stagesNode;
      ArrayNode newStagesArray = YamlUtils.getMapper().createArrayNode();
      boolean modified = false;

      // Iterate through all stages
      for (JsonNode stageWrapper : stagesArray) {
        JsonNode stageNode = extractStageNode(stageWrapper);

        if (stageNode != null && shouldInjectCollectionStage(stageNode)) {
          String stageId = stageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText();
          log.info("Injecting collection stage for CI stage: {}", stageId);

          // Create and add collection stage
          JsonNode collectionStageWrapper = createCollectionStage(stageNode);
          newStagesArray.add(collectionStageWrapper);

          // Modify execution stage to use collection stage output
          JsonNode modifiedExecutionStage = modifyExecutionStageParallelism(stageWrapper, stageNode);
          newStagesArray.add(modifiedExecutionStage);

          modified = true;
        } else {
          // Keep stage as-is
          newStagesArray.add(stageWrapper);
        }
      }

      if (modified) {
        pipelineContent.set(YAMLFieldNameConstants.STAGES, newStagesArray);
        log.info("Successfully injected collection stages for dynamic test splitting");
      }

      return pipelineNode;
    } catch (Exception e) {
      log.error("Failed to inject collection stages for dynamic test splitting, returning original YAML", e);
      // Return original pipeline to avoid breaking execution
      return pipelineNode;
    }
  }

  private JsonNode extractStageNode(JsonNode stageWrapper) {
    if (stageWrapper.has(YAMLFieldNameConstants.STAGE)) {
      return stageWrapper.get(YAMLFieldNameConstants.STAGE);
    }
    // Parallel stages not supported yet
    return null;
  }

  private boolean shouldInjectCollectionStage(JsonNode stageNode) {
    // Check if CI stage
    if (!stageNode.has(YAMLFieldNameConstants.TYPE)
        || !"CI".equals(stageNode.get(YAMLFieldNameConstants.TYPE).asText())) {
      return false;
    }

    // Check if spec exists
    if (!stageNode.has(YAMLFieldNameConstants.SPEC)) {
      return false;
    }

    JsonNode specNode = stageNode.get(YAMLFieldNameConstants.SPEC);

    // Check enable_dynamic_test_split flag
    if (!specNode.has("enable_dynamic_test_split")) {
      return false;
    }

    JsonNode enableFlag = specNode.get("enable_dynamic_test_split");
    return enableFlag.isBoolean() && enableFlag.asBoolean();
  }

  private JsonNode createCollectionStage(JsonNode executionStageNode) {
    ObjectNode collectionStageNode = executionStageNode.deepCopy();

    // Modify identifier
    String originalIdentifier = executionStageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText();
    String collectionIdentifier = originalIdentifier + "_collect";
    ((ObjectNode) collectionStageNode).put(YAMLFieldNameConstants.IDENTIFIER, collectionIdentifier);

    // Modify name
    String originalName = executionStageNode.has(YAMLFieldNameConstants.NAME)
        ? executionStageNode.get(YAMLFieldNameConstants.NAME).asText()
        : originalIdentifier;
    ((ObjectNode) collectionStageNode).put(YAMLFieldNameConstants.NAME, originalName + " - Collect Tests");

    // Get spec node
    ObjectNode specNode = (ObjectNode) collectionStageNode.get(YAMLFieldNameConstants.SPEC);

    // Remove strategy if present (collection runs once)
    if (specNode.has("strategy")) {
      specNode.remove("strategy");
    }

    // Inject HARNESS_TI_COLLECT_ONLY into Run/RunTests steps
    if (specNode.has(YAMLFieldNameConstants.EXECUTION)) {
      JsonNode executionNode = specNode.get(YAMLFieldNameConstants.EXECUTION);
      if (executionNode.has(YAMLFieldNameConstants.STEPS)) {
        injectCollectEnvIntoSteps((ArrayNode) executionNode.get(YAMLFieldNameConstants.STEPS));
      }
    }

    // Add calculation step
    addParallelismCalculationStep(specNode);

    // Wrap in stage wrapper
    ObjectNode stageWrapper = YamlUtils.getMapper().createObjectNode();
    stageWrapper.set(YAMLFieldNameConstants.STAGE, collectionStageNode);

    return stageWrapper;
  }

  private void injectCollectEnvIntoSteps(ArrayNode steps) {
    boolean outputVarAdded = false;

    for (JsonNode stepWrapper : steps) {
      if (stepWrapper.has(YAMLFieldNameConstants.STEP)) {
        JsonNode stepNode = stepWrapper.get(YAMLFieldNameConstants.STEP);
        if (stepNode.has(YAMLFieldNameConstants.TYPE)) {
          String stepType = stepNode.get(YAMLFieldNameConstants.TYPE).asText();
          if ("Run".equals(stepType) || "RunTests".equals(stepType)) {
            ObjectNode stepSpec = (ObjectNode) stepNode.get(YAMLFieldNameConstants.SPEC);
            if (stepSpec != null) {
              // Inject env var
              ObjectNode envVars;
              if (stepSpec.has("envVariables")) {
                envVars = (ObjectNode) stepSpec.get("envVariables");
              } else {
                envVars = YamlUtils.getMapper().createObjectNode();
                stepSpec.set("envVariables", envVars);
              }
              envVars.put("HARNESS_TI_COLLECT_ONLY", "true");

              // Add output variable to FIRST Run/RunTests step only
              if (!outputVarAdded) {
                if (!stepSpec.has("outputVariables")) {
                  ArrayNode outputVars = YamlUtils.getMapper().createArrayNode();
                  ObjectNode outputVar = YamlUtils.getMapper().createObjectNode();
                  outputVar.put("name", "HARNESS_TI_TESTS_SELECTED");
                  outputVars.add(outputVar);
                  stepSpec.set("outputVariables", outputVars);
                }
                outputVarAdded = true;
              }
            }
          }
        }
      }
    }
  }

  private void addParallelismCalculationStep(ObjectNode specNode) {
    if (!specNode.has(YAMLFieldNameConstants.EXECUTION)) {
      return;
    }

    ObjectNode executionNode = (ObjectNode) specNode.get(YAMLFieldNameConstants.EXECUTION);
    if (!executionNode.has(YAMLFieldNameConstants.STEPS)) {
      return;
    }

    ArrayNode steps = (ArrayNode) executionNode.get(YAMLFieldNameConstants.STEPS);

    // Create calculation step
    ObjectNode calcStepWrapper = YamlUtils.getMapper().createObjectNode();
    ObjectNode calcStep = YamlUtils.getMapper().createObjectNode();
    calcStep.put(YAMLFieldNameConstants.IDENTIFIER, "calculate_parallelism");
    calcStep.put(YAMLFieldNameConstants.TYPE, "Run");
    calcStep.put(YAMLFieldNameConstants.NAME, "Calculate Parallelism");

    ObjectNode calcStepSpec = YamlUtils.getMapper().createObjectNode();
    calcStepSpec.put("shell", "Bash");
    calcStepSpec.put("command",
        ". ./.harness_ti_env\n"
            + "p=${HARNESS_TI_MAX_PARALLELISM:-10}\n"
            + "t=${HARNESS_TI_TESTS_SELECTED:-0}\n"
            + "[ \"$t\" -lt \"$p\" ] && [ \"$t\" -gt 0 ] && p=$t\n"
            + "export HARNESS_TI_EFFECTIVE_PARALLELISM=$p\n"
            + "echo \"Effective parallelism: $HARNESS_TI_EFFECTIVE_PARALLELISM\"");

    ArrayNode outputVars = YamlUtils.getMapper().createArrayNode();
    ObjectNode outputVar = YamlUtils.getMapper().createObjectNode();
    outputVar.put("name", "HARNESS_TI_EFFECTIVE_PARALLELISM");
    outputVars.add(outputVar);
    calcStepSpec.set("outputVariables", outputVars);

    calcStep.set(YAMLFieldNameConstants.SPEC, calcStepSpec);
    calcStepWrapper.set(YAMLFieldNameConstants.STEP, calcStep);

    steps.add(calcStepWrapper);
  }

  private JsonNode modifyExecutionStageParallelism(JsonNode stageWrapperNode, JsonNode stageNode) {
    ObjectNode modifiedWrapper = stageWrapperNode.deepCopy();
    ObjectNode modifiedStage = (ObjectNode) modifiedWrapper.get(YAMLFieldNameConstants.STAGE);
    ObjectNode specNode = (ObjectNode) modifiedStage.get(YAMLFieldNameConstants.SPEC);

    // Get identifiers
    String executionIdentifier = stageNode.get(YAMLFieldNameConstants.IDENTIFIER).asText();
    String collectionIdentifier = executionIdentifier + "_collect";

    // Set or update strategy.parallelism
    ObjectNode strategyNode;
    if (specNode.has("strategy")) {
      strategyNode = (ObjectNode) specNode.get("strategy");
    } else {
      strategyNode = YamlUtils.getMapper().createObjectNode();
      specNode.set("strategy", strategyNode);
    }

    // Set parallelism expression
    String parallelismExpression = String.format("<+pipeline.stages.%s.spec.execution.steps.calculate_parallelism."
            + "output.outputVariables.HARNESS_TI_EFFECTIVE_PARALLELISM>",
        collectionIdentifier);
    strategyNode.put("parallelism", parallelismExpression);

    return modifiedWrapper;
  }
}
