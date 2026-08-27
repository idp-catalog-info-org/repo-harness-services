/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.instrumentaion;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.FailureType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants;
import io.harness.pms.pipeline.CommonStepInfo;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class PipelineInstrumentationUtils {
  public String getIdentityFromAmbiance(Ambiance ambiance) {
    if (!ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email").isEmpty()) {
      return ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email");
    }
    return ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getIdentifier();
  }

  public Collection<FailureType> getFailureTypesFromPipelineExecutionSummary(
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    if (pipelineExecutionSummaryEntity.getFailureInfo() == null) {
      return Collections.emptyList();
    }
    return pipelineExecutionSummaryEntity.getFailureInfo().getFailureTypeList();
  }

  public Set<String> getErrorMessagesFromPipelineExecutionSummary(
      PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    if (pipelineExecutionSummaryEntity.getFailureInfo() == null) {
      return Collections.emptySet();
    }
    Set<String> errorMessages = new HashSet<>();
    if (!StringUtils.isEmpty(pipelineExecutionSummaryEntity.getFailureInfo().getMessage())) {
      errorMessages.add(pipelineExecutionSummaryEntity.getFailureInfo().getMessage());
    }
    errorMessages.addAll(pipelineExecutionSummaryEntity.getFailureInfo()
                             .getResponseMessages()
                             .stream()
                             .map(ResponseMessage::getMessage)
                             .collect(Collectors.toList()));
    return errorMessages;
  }

  public String extractExceptionMessage(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    if (pipelineExecutionSummaryEntity.getFailureInfo() == null) {
      return "";
    }
    return pipelineExecutionSummaryEntity.getFailureInfo()
        .getResponseMessages()
        .stream()
        .filter(o -> o.getCode() != ErrorCode.HINT && o.getCode() != ErrorCode.EXPLANATION)
        .map(ResponseMessage::getMessage)
        .collect(Collectors.toList())
        .toString();
  }

  public static List<String> getStageTypes(PipelineEntity pipelineEntity) {
    if (pipelineEntity == null || pipelineEntity.getYaml() == null) {
      return Collections.emptyList();
    }

    String yaml = pipelineEntity.getYaml();
    try {
      JsonNode jsonNode = new ObjectMapper(new YAMLFactory()).readTree(yaml);
      JsonNode stagesJsonNode =
          jsonNode.get(PipelineInstrumentationConstants.PIPELINE).get(PipelineInstrumentationConstants.STAGES);
      List<String> stageTypesList = new ArrayList<>();
      if (stagesJsonNode.isArray()) {
        for (JsonNode stageNode : stagesJsonNode) {
          if (stageNode.get(PipelineInstrumentationConstants.PARALLEL) != null) {
            getStageTypeFromParallelNode(stageNode, stageTypesList);
          } else if (stageNode.get(PipelineInstrumentationConstants.STAGE) != null) {
            getStageTypeFromStageNode(stageNode, stageTypesList);
          } else if (stageNode.get(PipelineInstrumentationConstants.INSERT) != null) {
            getStageTypeFromInsertNode(stageNode, stageTypesList);
          }
        }
      }
      return stageTypesList;
    } catch (Exception ex) {
      log.error(String.format("Unable to parse stage types from Pipeline yaml: %s", yaml), ex);
      return Collections.emptyList();
    }
  }

  private static void getStageTypeFromInsertNode(JsonNode insertStageNode, List<String> stageTypesList) {
    JsonNode insertNode = insertStageNode.get(PipelineInstrumentationConstants.INSERT);
    if (insertNode.get(YAMLFieldNameConstants.STAGES) != null) {
      JsonNode stagesNode = insertNode.get(YAMLFieldNameConstants.STAGES);
      if (stagesNode == null || !stagesNode.isArray()) {
        return;
      }
      for (JsonNode stageNode : stagesNode) {
        if (stageNode.get(PipelineInstrumentationConstants.STAGE) != null) {
          getStageTypeFromStageNode(stageNode, stageTypesList);
        } else if (stageNode.get(PipelineInstrumentationConstants.INSERT) != null) {
          getStageTypeFromInsertNode(stageNode, stageTypesList);
        } else if (stageNode.get(PipelineInstrumentationConstants.PARALLEL) != null) {
          getStageTypeFromParallelNode(stageNode, stageTypesList);
        }
      }
    }
  }

  private static void getStageTypeFromStageNode(JsonNode stageNode, List<String> stageTypesList) {
    JsonNode stageTypeNode = stageNode.get(PipelineInstrumentationConstants.STAGE);
    stageTypesList.add(getStageType(stageTypeNode));
  }

  private static void getStageTypeFromParallelNode(JsonNode stageNode, List<String> stageTypesList) {
    JsonNode parallelStagesNode = stageNode.get(PipelineInstrumentationConstants.PARALLEL);
    for (JsonNode stageNodeInsideParallel : parallelStagesNode) {
      if (stageNodeInsideParallel.get(YAMLFieldNameConstants.INSERT) != null) {
        getStageTypeFromInsertNode(stageNodeInsideParallel, stageTypesList);
      } else if (stageNodeInsideParallel.get(PipelineInstrumentationConstants.STAGE) != null) {
        getStageTypeFromStageNode(stageNodeInsideParallel, stageTypesList);
      }
    }
  }

  /**
   * populates the data required from the FullYamlField.
   *
   * @param fullYamlField the YamlField for the processed yaml
   * @param version Pipeline version
   */
  public static Map<String, Object> populateInstrumentationYamlFieldData(YamlField fullYamlField, String version) {
    Map<String, Object> data = new HashMap<>();
    if (version.equals(HarnessYamlVersion.V1)) {
      return data;
    }
    AtomicBoolean hasStepGroup = new AtomicBoolean(false), hasCommonSteps = new AtomicBoolean(false),
                  hasBarrier = new AtomicBoolean(false), hasLoopingStrategy = new AtomicBoolean(false),
                  hasFailureStrategy = new AtomicBoolean(false), hasStepsInsert = new AtomicBoolean(false),
                  hasStagesInsert = new AtomicBoolean(false);
    List<String> stages = new ArrayList<>();
    List<String> loopingStrategyTypes = new ArrayList<>();
    List<String> loopingStrategyLevels = new ArrayList<>();
    List<String> failureStrategyLevels = new ArrayList<>();
    if (fullYamlField != null && fullYamlField.getNode() != null && fullYamlField.getNode().getCurrJsonNode() != null) {
      try {
        JsonNode pipelineJsonNode =
            fullYamlField.getNode().getCurrJsonNode().get(PipelineInstrumentationConstants.PIPELINE);
        if (pipelineJsonNode != null) {
          JsonNode flowControlJsonNode = pipelineJsonNode.get(PipelineInstrumentationConstants.FLOW_CONTROL);
          if (flowControlJsonNode != null) {
            hasBarrier.set(flowControlJsonNode.get(PipelineInstrumentationConstants.BARRIERS) != null);
          }
          JsonNode stagesJsonNode = pipelineJsonNode.get(PipelineInstrumentationConstants.STAGES);
          if (stagesJsonNode != null && stagesJsonNode.isArray()) {
            for (JsonNode stage : stagesJsonNode) {
              if (stage.get(PipelineInstrumentationConstants.INSERT) != null) {
                handleInsertStages(stage, hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels,
                    hasFailureStrategy, failureStrategyLevels, stages, hasStepGroup, hasCommonSteps, hasStepsInsert,
                    hasStagesInsert);
              } else if (null != stage.get(PipelineInstrumentationConstants.STAGE)) {
                handleStageNode(stage, hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels,
                    hasFailureStrategy, failureStrategyLevels, stages, hasStepGroup, hasCommonSteps, hasStepsInsert,
                    hasStagesInsert);
              } else if (null != stage.get(PipelineInstrumentationConstants.PARALLEL)) {
                handleParallelNode(stage, hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels,
                    hasFailureStrategy, failureStrategyLevels, stages, hasStepGroup, hasCommonSteps, hasStepsInsert,
                    hasStagesInsert);
              }
            }
          }
        }
      } catch (Exception e) {
        log.error("Error in populating the instrumentation data for sending the event for telemetry, error: {}",
            e.getMessage(), e);
      }
    }
    data.put(PipelineInstrumentationConstants.HAS_STEP_GROUP, hasStepGroup.get());
    data.put(PipelineInstrumentationConstants.HAS_COMMON_STEPS, hasCommonSteps.get());
    data.put(PipelineInstrumentationConstants.HAS_BARRIER, hasBarrier.get());
    data.put(PipelineInstrumentationConstants.HAS_LOOPING_STRATEGY, hasLoopingStrategy.get());
    data.put(PipelineInstrumentationConstants.LOOPING_STRATEGY_TYPES, loopingStrategyTypes);
    data.put(PipelineInstrumentationConstants.LOOPING_STRATEGY_LEVELS, loopingStrategyLevels);
    data.put(PipelineInstrumentationConstants.HAS_FAILURE_STRATEGY, hasFailureStrategy.get());
    data.put(PipelineInstrumentationConstants.FAILURE_STRATEGY_LEVELS, failureStrategyLevels);
    data.put(PipelineInstrumentationConstants.STAGES_PROPERTY, stages);
    data.put(PipelineInstrumentationConstants.HAS_STEPS_INSERT, hasStepsInsert.get());
    data.put(PipelineInstrumentationConstants.HAS_STAGES_INSERT, hasStagesInsert.get());
    return data;
  }

  private static void handleParallelNode(JsonNode stage, AtomicBoolean hasLoopingStrategy,
      List<String> loopingStrategyTypes, List<String> loopingStrategyLevels, AtomicBoolean hasFailureStrategy,
      List<String> failureStrategyLevels, List<String> stages, AtomicBoolean hasStepGroup, AtomicBoolean hasCommonSteps,
      AtomicBoolean hasStepsInsert, AtomicBoolean hasStagesInsert) {
    JsonNode parallelStagesNode = stage.get(PipelineInstrumentationConstants.PARALLEL);
    if (null != parallelStagesNode && parallelStagesNode.isArray()) {
      for (JsonNode stageNodeInsideParallel : parallelStagesNode) {
        if (null != stageNodeInsideParallel.get(PipelineInstrumentationConstants.STAGE)) {
          handleStageNode(stageNodeInsideParallel, hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels,
              hasFailureStrategy, failureStrategyLevels, stages, hasStepGroup, hasCommonSteps, hasStepsInsert,
              hasStagesInsert);
        } else if (null != stageNodeInsideParallel.get(PipelineInstrumentationConstants.INSERT)) {
          handleInsertStages(stageNodeInsideParallel, hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels,
              hasFailureStrategy, failureStrategyLevels, stages, hasStepGroup, hasCommonSteps, hasStepsInsert,
              hasStagesInsert);
        }
      }
    }
  }

  private static void handleInsertStages(JsonNode stage, AtomicBoolean hasLoopingStrategy,
      List<String> loopingStrategyTypes, List<String> loopingStrategyLevels, AtomicBoolean hasFailureStrategy,
      List<String> failureStrategyLevels, List<String> stages, AtomicBoolean hasStepGroup, AtomicBoolean hasCommonSteps,
      AtomicBoolean hasStepsInsert, AtomicBoolean hasStagesInsert) {
    JsonNode insertNode = stage.get(PipelineInstrumentationConstants.INSERT);
    hasStagesInsert.set(true);
    if (insertNode.get(YAMLFieldNameConstants.STAGES) != null) {
      JsonNode stagesNode = insertNode.get(YAMLFieldNameConstants.STAGES);
      for (JsonNode stageNode : stagesNode) {
        if (null != stageNode.get(PipelineInstrumentationConstants.STAGE)) {
          handleStageNode(stageNode, hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels,
              hasFailureStrategy, failureStrategyLevels, stages, hasStepGroup, hasCommonSteps, hasStepsInsert,
              hasStagesInsert);
        } else if (null != stageNode.get(YAMLFieldNameConstants.INSERT)) {
          handleInsertStages(stageNode, hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels,
              hasFailureStrategy, failureStrategyLevels, stages, hasStepGroup, hasCommonSteps, hasStepsInsert,
              hasStagesInsert);
        } else if (null != stageNode.get(YAMLFieldNameConstants.PARALLEL)) {
          handleParallelNode(stageNode, hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels,
              hasFailureStrategy, failureStrategyLevels, stages, hasStepGroup, hasCommonSteps, hasStepsInsert,
              hasStagesInsert);
        }
      }
    }
  }

  private static void handleStageNode(JsonNode stageJsonNode, AtomicBoolean hasLoopingStrategy,
      List<String> loopingStrategyTypes, List<String> loopingStrategyLevels, AtomicBoolean hasFailureStrategy,
      List<String> failureStrategyLevels, List<String> stages, AtomicBoolean hasStepGroup, AtomicBoolean hasCommonSteps,
      AtomicBoolean hasStepsInsert, AtomicBoolean hasStagesInsert) {
    JsonNode stageNode = stageJsonNode.get(YAMLFieldNameConstants.STAGE);
    // Looping Strategy
    if (hasLoopingStrategy(stageNode)) {
      hasLoopingStrategy.set(true);
      loopingStrategyTypes.add(getLoopingStrategyType(stageNode));
      loopingStrategyLevels.add(PipelineInstrumentationConstants.STAGE);
    }

    // Failure strategy
    if (hasFailureStrategy(stageNode)) {
      hasFailureStrategy.set(true);
      failureStrategyLevels.add(PipelineInstrumentationConstants.STAGE);
    }

    // Stage Type
    stages.add(getStageType(stageNode));

    JsonNode specJsonNode = stageNode.get(PipelineInstrumentationConstants.SPEC);
    if (specJsonNode != null) {
      JsonNode executionJsonNode = specJsonNode.get(PipelineInstrumentationConstants.EXECUTION);
      if (executionJsonNode != null) {
        JsonNode stepsJsonNode = executionJsonNode.get(PipelineInstrumentationConstants.STEPS);
        if (stepsJsonNode != null && stepsJsonNode.isArray()) {
          for (JsonNode stepNode : stepsJsonNode) {
            boolean isStepGroup = stepNode.get(PipelineInstrumentationConstants.STEP_GROUP) != null;
            boolean isStepsInsert = stepsJsonNode.get(PipelineInstrumentationConstants.INSERT) != null;

            // Insert
            hasStepsInsert.set(hasStepsInsert.get() || isStepsInsert);

            // in case it is a step group, it will have its own steps.
            if (isStepGroup) {
              handleStepGroupSteps(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
                  failureStrategyLevels, hasCommonSteps, stepNode, hasStepsInsert, hasStepGroup);
            } else if (isStepsInsert) {
              handleStepsInsert(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
                  failureStrategyLevels, hasCommonSteps, stepNode, hasStepsInsert, hasStepGroup);
            } else if (stepNode.get(PipelineInstrumentationConstants.PARALLEL) != null) {
              handleParallelSteps(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
                  failureStrategyLevels, hasCommonSteps, stepNode, hasStepsInsert, hasStepGroup);
            } else if (stepNode.get(PipelineInstrumentationConstants.STEP) != null) {
              handleSingleStep(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
                  failureStrategyLevels, hasCommonSteps, stepNode, hasStepsInsert, hasStepGroup);
            }
          }
        }
      }
    }
  }

  private static void handleSingleStep(AtomicBoolean hasLoopingStrategy, List<String> loopingStrategyTypes,
      List<String> loopingStrategyLevels, AtomicBoolean hasFailureStrategy, List<String> failureStrategyLevels,
      AtomicBoolean hasCommonSteps, JsonNode stepNode, AtomicBoolean hasStepsInsert, AtomicBoolean hasStepGroup) {
    hasCommonSteps.set(hasCommonSteps.get() || isCommonStep(stepNode));
    JsonNode stepJsonNode = stepNode.get(PipelineInstrumentationConstants.STEP);
    // Looping Strategy
    if (hasLoopingStrategy(stepJsonNode)) {
      hasLoopingStrategy.set(true);
      loopingStrategyTypes.add(getLoopingStrategyType(stepJsonNode));
      loopingStrategyLevels.add(PipelineInstrumentationConstants.STEP);
    }
    // Failure strategy
    if (hasFailureStrategy(stepJsonNode)) {
      hasFailureStrategy.set(true);
      failureStrategyLevels.add(PipelineInstrumentationConstants.STEP);
    }
  }

  private static void handleStepsInsert(AtomicBoolean hasLoopingStrategy, List<String> loopingStrategyTypes,
      List<String> loopingStrategyLevels, AtomicBoolean hasFailureStrategy, List<String> failureStrategyLevels,
      AtomicBoolean hasCommonSteps, JsonNode stepNode, AtomicBoolean hasStepsInsert, AtomicBoolean hasStepGroup) {
    JsonNode insertJsonNode = stepNode.get(PipelineInstrumentationConstants.INSERT);
    hasStepsInsert.set(true);
    JsonNode insertStepsJsonNode = insertJsonNode.get(PipelineInstrumentationConstants.STEPS);
    if (insertStepsJsonNode != null && insertStepsJsonNode.isArray()) {
      for (JsonNode insertStepJsonNode : insertStepsJsonNode) {
        if (insertStepJsonNode.get(PipelineInstrumentationConstants.STEP) != null) {
          handleSingleStep(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
              failureStrategyLevels, hasCommonSteps, insertStepJsonNode, hasStepsInsert, hasStepGroup);
        } else if (insertStepJsonNode.get(PipelineInstrumentationConstants.STEP_GROUP) != null) {
          handleStepGroupSteps(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
              failureStrategyLevels, hasCommonSteps, insertStepJsonNode, hasStepsInsert, hasStepGroup);
        } else if (insertStepJsonNode.get(PipelineInstrumentationConstants.PARALLEL) != null) {
          handleParallelSteps(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
              failureStrategyLevels, hasCommonSteps, insertStepJsonNode, hasStepsInsert, hasStepGroup);
        }
      }
    }
  }

  private static void handleStepGroupSteps(AtomicBoolean hasLoopingStrategy, List<String> loopingStrategyTypes,
      List<String> loopingStrategyLevels, AtomicBoolean hasFailureStrategy, List<String> failureStrategyLevels,
      AtomicBoolean hasCommonSteps, JsonNode stepNode, AtomicBoolean hasStepsInsert, AtomicBoolean hasStepGroup) {
    JsonNode stepGroupJsonNode = stepNode.get(PipelineInstrumentationConstants.STEP_GROUP);
    hasStepGroup.set(true);

    // Looping strategy
    if (hasLoopingStrategy(stepGroupJsonNode)) {
      hasLoopingStrategy.set(true);
      loopingStrategyTypes.add(getLoopingStrategyType(stepGroupJsonNode));
      loopingStrategyLevels.add(PipelineInstrumentationConstants.STEP_GROUP);
    }
    // Failure strategy
    if (hasFailureStrategy(stepGroupJsonNode)) {
      hasFailureStrategy.set(true);
      failureStrategyLevels.add(PipelineInstrumentationConstants.STEP_GROUP);
    }

    JsonNode stepGroupStepsJsonNode = stepGroupJsonNode.get(PipelineInstrumentationConstants.STEPS);
    if (stepGroupStepsJsonNode != null && stepGroupStepsJsonNode.isArray()) {
      for (JsonNode stepGroupStepJsonNode : stepGroupStepsJsonNode) {
        if (stepGroupStepJsonNode.get(PipelineInstrumentationConstants.STEP) != null) {
          handleSingleStep(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
              failureStrategyLevels, hasCommonSteps, stepGroupStepJsonNode, hasStepsInsert, hasStepGroup);
        } else if (stepGroupStepJsonNode.get(PipelineInstrumentationConstants.INSERT) != null) {
          handleStepsInsert(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
              failureStrategyLevels, hasCommonSteps, stepGroupStepJsonNode, hasStepsInsert, hasStepGroup);
        } else if (stepGroupStepJsonNode.get(PipelineInstrumentationConstants.PARALLEL) != null) {
          handleParallelSteps(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
              failureStrategyLevels, hasCommonSteps, stepGroupStepJsonNode, hasStepsInsert, hasStepGroup);
        } else if (stepGroupStepJsonNode.get(PipelineInstrumentationConstants.STEP_GROUP) != null) {
          handleStepGroupSteps(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
              failureStrategyLevels, hasCommonSteps, stepGroupStepJsonNode, hasStepsInsert, hasStepGroup);
        }
      }
    }
  }

  private static void handleParallelSteps(AtomicBoolean hasLoopingStrategy, List<String> loopingStrategyTypes,
      List<String> loopingStrategyLevels, AtomicBoolean hasFailureStrategy, List<String> failureStrategyLevels,
      AtomicBoolean hasCommonSteps, JsonNode parallelStepJsonNode, AtomicBoolean hasStepsInsert,
      AtomicBoolean hasStepGroup) {
    JsonNode parallelStepsNode = parallelStepJsonNode.get(PipelineInstrumentationConstants.PARALLEL);
    if (parallelStepsNode.isArray()) {
      for (JsonNode stepNodeInsideParallel : parallelStepsNode) {
        if (stepNodeInsideParallel.get(PipelineInstrumentationConstants.STEP) != null) {
          handleSingleStep(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
              failureStrategyLevels, hasCommonSteps, stepNodeInsideParallel, hasStepsInsert, hasStepGroup);
        } else if (stepNodeInsideParallel.get(PipelineInstrumentationConstants.STEP_GROUP) != null) {
          handleStepGroupSteps(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
              failureStrategyLevels, hasCommonSteps, stepNodeInsideParallel, hasStepsInsert, hasStepGroup);
        } else if (stepNodeInsideParallel.get(PipelineInstrumentationConstants.INSERT) != null) {
          handleStepsInsert(hasLoopingStrategy, loopingStrategyTypes, loopingStrategyLevels, hasFailureStrategy,
              failureStrategyLevels, hasCommonSteps, stepNodeInsideParallel, hasStepsInsert, hasStepGroup);
        }
      }
    }
  }

  /**
   * Gets the type of the stage.
   *
   * @param node the JsonNode of the stage node.
   * @return a String of the type of the node.
   */
  private String getStageType(JsonNode node) {
    JsonNode stageTypeNode = node.get(PipelineInstrumentationConstants.TYPE);
    if (stageTypeNode != null) {
      return stageTypeNode.textValue();
    }
    // If the type is not found in the stage, it must be template.
    JsonNode templateNode = node.get(PipelineInstrumentationConstants.TEMPLATE);
    if (templateNode != null) {
      JsonNode templateInputs = templateNode.get(PipelineInstrumentationConstants.TEMPLATE_INPUTS);
      if (templateInputs != null && templateInputs.get(PipelineInstrumentationConstants.TYPE) != null) {
        return templateInputs.get(PipelineInstrumentationConstants.TYPE).textValue();
      }
    }
    // default to empty string.
    return Strings.EMPTY;
  }

  /**
   * Calculates if the pipeline has a common step.
   *
   * @param stepNode the json node of a step.
   * @return if stepNode step is a common step.
   */
  private static boolean isCommonStep(JsonNode stepNode) {
    JsonNode stepJsonNode = stepNode.get(PipelineInstrumentationConstants.STEP);
    if (stepJsonNode != null) {
      // Common Steps
      String stepType = stepJsonNode.get(PipelineInstrumentationConstants.TYPE).textValue();
      return CommonStepInfo.COMMON_STEP_TYPES.contains(stepType);
      // check if this pipeline already has common steps in previous stages.
    }
    return false;
  }

  /**
   * Checks if the node has any strategy associated with it.
   *
   * @param node the JsonNode of the node.
   * @return a boolean if the strategy node exists.
   */
  private static boolean hasLoopingStrategy(JsonNode node) {
    JsonNode jsonNode = node.get(PipelineInstrumentationConstants.STRATEGY);
    if (jsonNode != null) {
      return true;
    }
    return false;
  }

  /**
   * Get the looping strategy type of the given node.
   *
   * @param node the JsonNode of the node.
   * @return a string of the looping strategy type.
   */
  private static String getLoopingStrategyType(JsonNode node) {
    JsonNode jsonNode = node.get(PipelineInstrumentationConstants.STRATEGY);
    if (jsonNode != null) {
      JsonNode repeatNode = jsonNode.get(PipelineInstrumentationConstants.REPEAT);
      JsonNode matrixNode = jsonNode.get(PipelineInstrumentationConstants.MATRIX);
      JsonNode parallelismNode = jsonNode.get(PipelineInstrumentationConstants.PARALLELISM);
      if (repeatNode != null) {
        return PipelineInstrumentationConstants.REPEAT;
      }
      if (matrixNode != null) {
        return PipelineInstrumentationConstants.MATRIX;
      }
      if (parallelismNode != null) {
        return PipelineInstrumentationConstants.PARALLELISM;
      }
    }
    log.warn("Could not find the appropriate Looping strategy type for Node: {}", node.asText());
    return Strings.EMPTY;
  }

  /**
   * Checks if the node has any failure strategy associated with it.
   *
   * @param node the JsonNode of the node.
   * @return a boolean if the strategy node exists.
   */
  private static boolean hasFailureStrategy(JsonNode node) {
    JsonNode jsonNode = node.get(PipelineInstrumentationConstants.FAILURE_STRATEGIES);
    if (jsonNode != null) {
      return true;
    }
    return false;
  }
}
