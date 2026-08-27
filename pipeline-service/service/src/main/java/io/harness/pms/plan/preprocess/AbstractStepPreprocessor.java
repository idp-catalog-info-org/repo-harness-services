/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.preprocess;

import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractStepPreprocessor implements PlanCreationYamlPreprocessor {
  protected static final String K8S_INFRA_TYPE = "KubernetesDirect";

  @Override
  public JsonNode preprocessPipelineYaml(JsonNode pipelineJsonNode, String accountId, String orgId, String projectId,
      String executionUuid, String pipelineId, ExecutionMode executionMode) {
    if (pipelineJsonNode == null) {
      return pipelineJsonNode;
    }

    boolean modified = expandStepsInPipeline(pipelineJsonNode, accountId, orgId, projectId);
    if (modified) {
      log.info("{} steps expanded in pipeline '{}' for account '{}'", getStepTypeName(), pipelineId, accountId);
      String updatedJson = JsonPipelineUtils.getJsonString(pipelineJsonNode);
      return JsonPipelineUtils.readTree(updatedJson);
    }
    return pipelineJsonNode;
  }

  protected abstract boolean isTargetStep(JsonNode stepNode);

  protected abstract String getGroupType();

  protected abstract String getStepTypeName();

  protected abstract JsonNode expandStep(
      JsonNode stepNode, String accountId, String orgId, String projectId, boolean isK8sInfra);

  private boolean expandStepsInPipeline(JsonNode pipelineNode, String accountId, String orgId, String projectId) {
    boolean modified = false;
    JsonNode stagesNode = pipelineNode.path("pipeline").path("stages");
    if (stagesNode.isMissingNode() || !stagesNode.isArray()) {
      return false;
    }

    for (JsonNode stageWrapper : stagesNode) {
      JsonNode parallelNode = stageWrapper.path("parallel");
      if (!parallelNode.isMissingNode() && parallelNode.isArray()) {
        for (JsonNode parallelStageWrapper : parallelNode) {
          boolean expanded = expandStepsInStage(parallelStageWrapper, accountId, orgId, projectId);
          modified = modified || expanded;
        }
        continue;
      }
      boolean expanded = expandStepsInStage(stageWrapper, accountId, orgId, projectId);
      modified = modified || expanded;
    }
    return modified;
  }

  private boolean expandStepsInStage(JsonNode stageWrapper, String accountId, String orgId, String projectId) {
    if (stageWrapper == null) {
      return false;
    }
    JsonNode stepsNode = stageWrapper.path("stage").path("spec").path("execution").path("steps");
    if (stepsNode.isMissingNode() || !stepsNode.isArray()) {
      return false;
    }

    String infraType = stageWrapper.path("stage").path("spec").path("infrastructure").path("type").asText(null);
    boolean isK8sInfra = K8S_INFRA_TYPE.equals(infraType);

    boolean modified = expandStepsInArray((ArrayNode) stepsNode, accountId, orgId, projectId, isK8sInfra, false);

    JsonNode rollbackStepsNode = stageWrapper.path("stage").path("spec").path("execution").path("rollbackSteps");
    if (!rollbackStepsNode.isMissingNode() && rollbackStepsNode.isArray()) {
      boolean rollbackModified =
          expandStepsInArray((ArrayNode) rollbackStepsNode, accountId, orgId, projectId, isK8sInfra, false);
      modified = modified || rollbackModified;
    }

    return modified;
  }

  protected boolean expandStepsInArray(ArrayNode stepsArray, String accountId, String orgId, String projectId,
      boolean isK8sInfra, boolean insideContainerizedStepGroup) {
    boolean modified = false;

    for (int i = 0; i < stepsArray.size(); i++) {
      JsonNode wrapper = stepsArray.get(i);

      JsonNode stepNode = wrapper.path("step");
      if (!stepNode.isMissingNode() && isTargetStep(stepNode)) {
        JsonNode expandedStepGroup = expandStep(stepNode, accountId, orgId, projectId, isK8sInfra);
        if (expandedStepGroup != null) {
          JsonNode whenNode = stepNode.path("when");
          JsonNode failureStrategiesNode = stepNode.path("failureStrategies");

          if (insideContainerizedStepGroup) {
            ObjectNode groupStep = YamlUtils.getMapper().createObjectNode();
            groupStep.put("type", getGroupType());
            groupStep.put("identifier", stepNode.path("identifier").asText());
            groupStep.put("name", stepNode.path("name").asText());

            if (!whenNode.isMissingNode()) {
              groupStep.set("when", whenNode.deepCopy());
            }

            JsonNode childSteps = expandedStepGroup.path("steps");
            if (!failureStrategiesNode.isMissingNode() && childSteps.isArray()) {
              for (JsonNode childWrapper : childSteps) {
                JsonNode childStep = childWrapper.path("step");
                if (!childStep.isMissingNode() && childStep.isObject()) {
                  ((ObjectNode) childStep).set("failureStrategies", failureStrategiesNode.deepCopy());
                }
              }
            }

            ObjectNode specNode = YamlUtils.getMapper().createObjectNode();
            specNode.set("steps", childSteps);
            groupStep.set("spec", specNode);

            ObjectNode groupWrapper = YamlUtils.getMapper().createObjectNode();
            groupWrapper.set("step", groupStep);
            YamlUtils.injectUuid(groupWrapper);
            stepsArray.set(i, groupWrapper);
            modified = true;
            log.info("Expanded {} step '{}' to {} at index {} (containerized step group)", getStepTypeName(),
                stepNode.path("identifier").asText(), getGroupType(), i);
          } else {
            if (!whenNode.isMissingNode() && expandedStepGroup.isObject()) {
              ((ObjectNode) expandedStepGroup).set("when", whenNode.deepCopy());
            }

            if (!failureStrategiesNode.isMissingNode()) {
              JsonNode childSteps = expandedStepGroup.path("steps");
              if (childSteps.isArray()) {
                for (JsonNode childWrapper : childSteps) {
                  JsonNode childStep = childWrapper.path("step");
                  if (!childStep.isMissingNode() && childStep.isObject()) {
                    ((ObjectNode) childStep).set("failureStrategies", failureStrategiesNode.deepCopy());
                  }
                }
              }
            }

            ObjectNode replacement = YamlUtils.getMapper().createObjectNode();
            replacement.set("stepGroup", expandedStepGroup);
            YamlUtils.injectUuid(replacement);
            stepsArray.set(i, replacement);
            modified = true;
            log.info("Expanded {} step '{}' to StepGroup at index {}", getStepTypeName(),
                stepNode.path("identifier").asText(), i);
          }
        }
        continue;
      }

      JsonNode stepGroupNode = wrapper.path("stepGroup");
      if (!stepGroupNode.isMissingNode()) {
        String sgInfraType = stepGroupNode.path("stepGroupInfra").path("type").asText(null);
        boolean sgIsK8s = (sgInfraType != null) ? K8S_INFRA_TYPE.equals(sgInfraType) : isK8sInfra;
        boolean sgIsContainerized = sgInfraType != null;

        JsonNode nestedSteps = stepGroupNode.path("steps");
        if (!nestedSteps.isMissingNode() && nestedSteps.isArray()) {
          boolean expanded =
              expandStepsInArray((ArrayNode) nestedSteps, accountId, orgId, projectId, sgIsK8s, sgIsContainerized);
          modified = modified || expanded;
        }
        continue;
      }

      JsonNode parallelNode = wrapper.path("parallel");
      if (!parallelNode.isMissingNode() && parallelNode.isArray()) {
        boolean expanded = expandStepsInArray(
            (ArrayNode) parallelNode, accountId, orgId, projectId, isK8sInfra, insideContainerizedStepGroup);
        modified = modified || expanded;
      }
    }

    return modified;
  }

  protected JsonNode buildStepGroupNode(String identifier, String name, List<ExecutionWrapperConfig> v0Steps) {
    return buildStepGroupNode(identifier, name, v0Steps, null, null);
  }

  protected JsonNode buildStepGroupNode(String identifier, String name, List<ExecutionWrapperConfig> v0Steps,
      JsonNode whenNode, JsonNode failureStrategiesNode) {
    ObjectNode stepGroupNode = YamlUtils.getMapper().createObjectNode();
    stepGroupNode.put("identifier", identifier);
    stepGroupNode.put("name", name);

    if (whenNode != null && !whenNode.isMissingNode()) {
      stepGroupNode.set("when", whenNode.deepCopy());
    }

    ArrayNode stepsArray = YamlUtils.getMapper().createArrayNode();
    for (ExecutionWrapperConfig wrapper : v0Steps) {
      ObjectNode wrapperNode = YamlUtils.getMapper().createObjectNode();
      if (wrapper.getStep() != null) {
        ObjectNode stepContent = (ObjectNode) wrapper.getStep().deepCopy();
        if (failureStrategiesNode != null && !failureStrategiesNode.isMissingNode()) {
          stepContent.set("failureStrategies", failureStrategiesNode.deepCopy());
        }
        wrapperNode.set("step", stepContent);
      } else if (wrapper.getParallel() != null) {
        wrapperNode.set("parallel", wrapper.getParallel().deepCopy());
      } else if (wrapper.getStepGroup() != null) {
        wrapperNode.set("stepGroup", wrapper.getStepGroup().deepCopy());
      } else {
        log.warn("Step expansion produced an unhandled ExecutionWrapperConfig type for step group '{}'", identifier);
        continue;
      }
      stepsArray.add(wrapperNode);
    }
    stepGroupNode.set("steps", stepsArray);

    YamlUtils.injectUuid(stepGroupNode);
    return stepGroupNode;
  }
}
