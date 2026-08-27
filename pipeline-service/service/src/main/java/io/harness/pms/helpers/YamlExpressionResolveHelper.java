/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.common.NGExpressionUtils;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.execution.NodeExecution;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.ngtriggers.expressions.NGTriggerExpressionEvaluatorProvider;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
// TODO: Merge with having same expression support instead of null return for unresolved expressions in
// engineExpressionEvaluator
public class YamlExpressionResolveHelper {
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;

  @Inject private NGTriggerExpressionEvaluatorProvider ngTriggerExpressionEvaluatorProvider;

  public String resolveExpressionsInYaml(
      String yamlString, String planExecutionId, ResolveInputYamlType resolveInputYamlType, String harnessVersion) {
    Optional<NodeExecution> nodeExecution = nodeExecutionService.getPipelineNodeExecutionWithProjections(
        planExecutionId, NodeProjectionUtils.withAmbianceAndStatus);

    if (nodeExecution.isPresent()) {
      return resolveExpressionsInYaml(
          yamlString, resolveInputYamlType, nodeExecutionService.getAmbiance(nodeExecution.get()), harnessVersion);
    }

    return yamlString;
  }

  public String resolveExpressionsInYaml(
      String yamlString, ResolveInputYamlType resolveInputYamlType, Ambiance ambiance, String harnessVersion) {
    EngineExpressionEvaluator engineExpressionEvaluator;
    if (resolveInputYamlType.equals(ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS)) {
      engineExpressionEvaluator = ngTriggerExpressionEvaluatorProvider.get(ambiance);
    } else {
      engineExpressionEvaluator = pmsEngineExpressionService.prepareExpressionEvaluator(ambiance);
    }
    try {
      YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlString));
      YamlField pipelineYamlField = yamlField;
      if (!HarnessYamlVersion.isV1(harnessVersion)) {
        pipelineYamlField = yamlField.getNode().getField("pipeline");
      }

      if (pipelineYamlField == null) {
        throw new InvalidRequestException(
            "YAML does not have pipeline object. No Input set was provided part of pipeline execution");
      }

      String accountId = ambiance != null ? AmbianceUtils.getAccountId(ambiance) : null;
      resolveExpressions(pipelineYamlField, engineExpressionEvaluator, accountId);
      JsonNode resolvedYamlNode = yamlField.getNode().getCurrJsonNode();
      YamlUtils.removeUuid(resolvedYamlNode);
      return YamlPipelineUtils.writeYamlString(resolvedYamlNode);

    } catch (IOException ex) {
      log.error(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
    }
  }

  public String resolveExpressionsInYaml(
      String yamlString, EngineExpressionEvaluator engineExpressionEvaluator, String accountId) {
    try {
      YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlString));
      resolveExpressions(yamlField, engineExpressionEvaluator, accountId);
      JsonNode resolvedYamlNode = yamlField.getNode().getCurrJsonNode();
      YamlUtils.removeUuid(resolvedYamlNode);
      return YamlPipelineUtils.writeYamlString(resolvedYamlNode);
    } catch (IOException ex) {
      log.error(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
    }
  }

  private void resolveExpressions(
      YamlField field, EngineExpressionEvaluator engineExpressionEvaluator, String accountId) {
    if (field.getNode().isObject()) {
      resolveExpressionsInObject(field.getNode(), engineExpressionEvaluator, accountId);
    } else if (field.getNode().isArray()) {
      resolveExpressionsInArray(field.getNode(), engineExpressionEvaluator, accountId);
    }
  }

  private void resolveExpressionsInObject(
      YamlNode parentNode, EngineExpressionEvaluator engineExpressionEvaluator, String accountId) {
    for (YamlField childYamlField : parentNode.fields()) {
      if (childYamlField.getNode().getCurrJsonNode().isValueNode()) {
        resolveExpressionInValueNode(parentNode, childYamlField.getName(),
            childYamlField.getNode().getCurrJsonNode().asText(), engineExpressionEvaluator, accountId);
      } else if (YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(parentNode.getCurrJsonNode())) {
        continue;
      } else {
        resolveExpressions(childYamlField, engineExpressionEvaluator, accountId);
      }
    }
  }

  private void resolveExpressionsInArray(
      YamlNode arrayNode, EngineExpressionEvaluator engineExpressionEvaluator, String accountId) {
    int childIndex = 0;
    for (YamlNode arrayElement : arrayNode.asArray()) {
      if (arrayElement.isObject()) {
        resolveExpressionsInObject(arrayElement, engineExpressionEvaluator, accountId);
      } else if (arrayElement.isArray()) {
        resolveExpressionsInArray(arrayElement, engineExpressionEvaluator, accountId);
      } else if (arrayElement.getCurrJsonNode().isValueNode()) {
        resolveExpressionForArrayElement(
            arrayNode, childIndex, arrayElement.getCurrJsonNode().asText(), engineExpressionEvaluator);
      }
      childIndex = childIndex + 1;
    }
  }

  public void resolveExpressionForArrayElement(
      YamlNode parentNode, int childIndex, String childValue, EngineExpressionEvaluator engineExpressionEvaluator) {
    ArrayNode object = (ArrayNode) parentNode.getCurrJsonNode();
    if (EngineExpressionEvaluator.hasExpressions(childValue)) {
      String resolvedExpression = engineExpressionEvaluator.renderExpression(
          childValue, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      // Update node value only if expression was successfully resolved
      if (isExpressionResolved(resolvedExpression) && !resolvedExpression.equals(childValue)) {
        object.set(childIndex, resolvedExpression);
      }
    }
  }

  private void resolveExpressionInValueNode(YamlNode parentNode, String childName, String childValue,
      EngineExpressionEvaluator engineExpressionEvaluator, String accountId) {
    ObjectNode objectNode = (ObjectNode) parentNode.getCurrJsonNode();
    if (NGExpressionUtils.matchesExecutionInputPattern(childValue)) {
      return;
    }
    if (EngineExpressionEvaluator.hasExpressions(childValue)) {
      String resolvedExpression = engineExpressionEvaluator.renderExpression(
          childValue, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      // Update node value only if expression was successfully resolved
      if (isExpressionResolved(resolvedExpression) && !resolvedExpression.equals(childValue)) {
        if (accountId != null
            && pmsFeatureFlagService.isEnabled(
                accountId, FeatureName.CDS_PIPELINE_YAML_EXPRESSION_JSON_ARRAY_PARSING)) {
          Optional<JsonNode> arrayNodeOpt = tryParseAsJsonArray(resolvedExpression);
          if (arrayNodeOpt.isPresent()) {
            objectNode.set(childName, arrayNodeOpt.get());
          } else {
            objectNode.put(childName, resolvedExpression);
          }
        } else {
          objectNode.put(childName, resolvedExpression);
        }
      }
    }
  }

  private boolean isExpressionResolved(String resolvedValue) {
    return resolvedValue != null && !resolvedValue.equals("null");
  }

  /**
   * Attempts to parse a string as a JSON array
   * @param expression The string potentially containing a JSON array
   * @return Optional containing parsed JsonNode if successful, empty otherwise
   */
  private Optional<JsonNode> tryParseAsJsonArray(String expression) {
    if (expression.startsWith("[") && expression.endsWith("]") && expression.contains("\"")) {
      try {
        JsonNode arrayNode = YamlUtils.readTree(expression).getNode().getCurrJsonNode();
        if (arrayNode.isArray()) {
          return Optional.of(arrayNode);
        }
      } catch (Exception e) {
        log.debug("Failed to parse expression as JSON array: {}", expression, e);
      }
    }
    return Optional.empty();
  }
}
