/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.preprocess;

import static io.harness.annotations.dev.HarnessTeam.AI;

import io.harness.agent.convert.V1ToV0StepGroupConverter;
import io.harness.agent.expansion.AgentTemplateExpansionService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(AI)
public class AiEvalStepPreprocessor extends AbstractStepPreprocessor {
  private static final String AI_EVAL_STEP_TYPE = "AiEval";
  private static final String AI_EVAL_GROUP_TYPE = "AiEvalGroup";
  private static final String TEMPLATE_ID = "aiEvalStep";

  private final AgentTemplateExpansionService agentTemplateExpansionService;
  private final V1ToV0StepGroupConverter v1ToV0StepGroupConverter;

  public AiEvalStepPreprocessor(
      AgentTemplateExpansionService agentTemplateExpansionService, V1ToV0StepGroupConverter v1ToV0StepGroupConverter) {
    this.agentTemplateExpansionService = agentTemplateExpansionService;
    this.v1ToV0StepGroupConverter = v1ToV0StepGroupConverter;
  }

  @Override
  protected boolean isTargetStep(JsonNode stepNode) {
    JsonNode typeNode = stepNode.path("type");
    return !typeNode.isMissingNode() && AI_EVAL_STEP_TYPE.equals(typeNode.asText());
  }

  @Override
  protected String getGroupType() {
    return AI_EVAL_GROUP_TYPE;
  }

  @Override
  protected String getStepTypeName() {
    return "AiEval";
  }

  @Override
  protected JsonNode expandStep(
      JsonNode stepNode, String accountId, String orgId, String projectId, boolean isK8sInfra) {
    String identifier = stepNode.path("identifier").asText();
    String name = stepNode.path("name").asText();
    JsonNode specNode = stepNode.path("spec");

    Map<String, JsonNode> templateInputs = buildTemplateInputs(specNode);

    try {
      JsonNode expandedV1Template =
          agentTemplateExpansionService.expandAgentStep(accountId, orgId, projectId, TEMPLATE_ID, templateInputs);

      List<ExecutionWrapperConfig> v0Steps =
          v1ToV0StepGroupConverter.convertToV0Steps(expandedV1Template, null, isK8sInfra);

      if (v0Steps.isEmpty()) {
        log.warn("AiEval template expanded to empty step list for step '{}'", identifier);
        return null;
      }

      return buildStepGroupNode(identifier, name, v0Steps);
    } catch (Exception ex) {
      throw new InvalidRequestException(
          String.format("Failed to expand AiEval step '%s': %s", identifier, ex.getMessage()), ex);
    }
  }

  private Map<String, JsonNode> buildTemplateInputs(JsonNode specNode) {
    Map<String, JsonNode> inputs = new LinkedHashMap<>();
    mapStringInput(inputs, specNode, "evalId", "eval_id");
    mapStringInput(inputs, specNode, "suiteId", "suite_id");
    mapStringInput(inputs, specNode, "suitePath", "suite_path");
    mapStringInput(inputs, specNode, "apiKey", "api_key");
    mapStringInput(inputs, specNode, "apiEndpoint", "api_endpoint");
    mapStringInput(inputs, specNode, "concurrency", "concurrency");
    mapStringInput(inputs, specNode, "repoFlags", "repo_flags");
    mapStringInput(inputs, specNode, "targetId", "target_id");
    mapStringInput(inputs, specNode, "datasetId", "dataset_id");
    mapStringInput(inputs, specNode, "llmConnectorRef", "llm_connector_ref");
    mapStringInput(inputs, specNode, "model", "model");
    if (!specNode.path("runId").isMissingNode() || !specNode.path("suiteRunId").isMissingNode()) {
      log.warn("AiEval step spec contains removed fields runId/suiteRunId — they will be ignored");
    }
    return inputs;
  }

  private void mapStringInput(Map<String, JsonNode> inputs, JsonNode specNode, String fieldName, String templateKey) {
    JsonNode value = specNode.path(fieldName);
    if (!value.isMissingNode() && !value.isNull()) {
      if (value.isTextual()) {
        inputs.put(templateKey, value);
      } else {
        inputs.put(templateKey, new TextNode(value.asText()));
      }
    }
  }
}
