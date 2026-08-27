/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eraro.ErrorCode.INVALID_REQUEST;
import static io.harness.eraro.Level.ERROR;
import static io.harness.pms.yaml.YAMLFieldNameConstants.HARNESS_HIDE_WHEN_SKIPPED;
import static io.harness.pms.yaml.YAMLFieldNameConstants.TRUE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.config.OrchestrationRestrictionConfiguration;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executioncheck.PreFacilitationExecutionCheck;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.expressions.constants.OrchestrationConstants;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.runtime.JexlRuntimeException;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.plan.Node;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.run.ExpressionBlock;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlException;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(PIPELINE)
public class RunPreFacilitationChecker extends AbstractPreFacilitationChecker {
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanService planService;
  @Inject PmsEngineExpressionService pmsEngineExpressionService;
  @Inject OrchestrationRestrictionConfiguration orchestrationRestrictionConfiguration;
  private static final String MAX_LEVELS_LIMIT_REACHED_ERROR =
      "The pipeline has reached the maximum nesting allowed for an execution. Please simplify the pipeline "
      + "configuration so that it does not breach the allowed limit of nesting";
  private static final StepType RUN_V1_STEP_TYPE =
      StepType.newBuilder().setType(CIStepInfoType.RUN.getDisplayName()).setStepCategory(StepCategory.STEP).build();
  @Override
  protected PreFacilitationExecutionCheck performCheck(Ambiance ambiance, Node node) {
    if (ambiance.getLevelsCount() > orchestrationRestrictionConfiguration.getMaxNestedLevelsCount()) {
      log.error(MAX_LEVELS_LIMIT_REACHED_ERROR);
      StepResponseProto response =
          StepResponseProto.newBuilder()
              .setStatus(Status.FAILED)
              .setFailureInfo(FailureInfo.newBuilder()
                                  .setErrorMessage(MAX_LEVELS_LIMIT_REACHED_ERROR)
                                  .addFailureData(FailureData.newBuilder()
                                                      .setLevel(ERROR.name())
                                                      .setCode(INVALID_REQUEST.name())
                                                      .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                                      .setMessage(MAX_LEVELS_LIMIT_REACHED_ERROR)
                                                      .build())
                                  .build())
              .build();
      orchestrationEngine.processStepResponse(ambiance, response);
      return PreFacilitationExecutionCheck.builder().proceed(false).reason(MAX_LEVELS_LIMIT_REACHED_ERROR).build();
    }
    log.info("Checking If Node should be Run with When Condition.");
    String nodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String whenCondition = node.getWhenCondition();
    if (EmptyPredicate.isNotEmpty(whenCondition)) {
      try {
        Object evaluatedExpression;
        List<ExpressionBlock> expressionBlocks = new ArrayList<>();
        boolean isV1 = HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion());
        if (isV1) {
          evaluatedExpression = evaluateExpressionForV1(ambiance, whenCondition, expressionBlocks);
        } else {
          evaluatedExpression = evaluateExpressionForV0(ambiance, whenCondition, expressionBlocks);
        }
        if (evaluatedExpression == null) {
          // TODO - @Utkarsh Choubey @Sahil We are adding this logs because whenever we pass any wrong expression in
          // when condition, and when that expression fails to resolve,we were getting NPE, At the moment we are adding
          // logs , so that we can check , we are not updating evaluated Expression coming as NULL to treat as false or
          // true, as this may cause behavioural change, therefore we will take this up later going forward.
          log.warn(String.format("evaluated expression is null for this condition - [%s]", whenCondition));
        }
        boolean whenConditionValue = convertToBoolean(evaluatedExpression, whenCondition);
        NodeRunInfo nodeRunInfo = NodeRunInfo.newBuilder()
                                      .setEvaluatedCondition(whenConditionValue)
                                      .setWhenCondition(whenCondition)
                                      .addAllExpressions(expressionBlocks)
                                      // The below field is required for UI to show if the node is a manual execution
                                      .setIsManualExecution(node.isManualExecution())
                                      .build();
        Map<String, Object> updates = new HashMap<>();
        updates.put(NodeExecutionKeys.nodeRunInfo, nodeRunInfo);
        if (!whenConditionValue) {
          log.info(String.format("Skipping node: %s", nodeExecutionId));
          // For V1 Run steps with HARNESS_HIDE=true, update skipGraphType to SKIP_TREE to remove from graph
          if (isV1 && shouldHideFromGraph(node)) {
            updates.put(NodeExecutionKeys.skipGraphType, SkipType.SKIP_TREE);
          }
          StepResponseProto response =
              StepResponseProto.newBuilder()
                  .setStatus(Status.SKIPPED)
                  .setNodeRunInfo(
                      NodeRunInfo.newBuilder().setWhenCondition(whenCondition).setEvaluatedCondition(false).build())
                  .build();
          orchestrationEngine.processStepResponse(ambiance, response);
          return PreFacilitationExecutionCheck.builder()
              .proceed(false)
              .reason("When Condition Evaluated to false")
              .updates(updates)
              .build();
        }
        return PreFacilitationExecutionCheck.builder()
            .proceed(true)
            .reason("When Condition Evaluated to true")
            .updates(updates)
            .build();
      } catch (Exception ex) {
        return handleExpressionEvaluationError(ex, whenCondition, ambiance);
      }
    }
    return PreFacilitationExecutionCheck.builder().proceed(true).reason("No when Condition Configured").build();
  }

  private void getUpdateOps(Map<String, Object> updates, Update ops) {
    for (Map.Entry<String, Object> entry : updates.entrySet()) {
      ops.set(entry.getKey(), entry.getValue());
    }
  }

  @VisibleForTesting
  List<ExpressionBlock> getAllExpressions(EngineExpressionEvaluator engineExpressionEvaluator) {
    Map<String, Map<Object, Integer>> usedExpressionsMap =
        engineExpressionEvaluator.getVariableResolverTracker().getUsage();
    List<ExpressionBlock> resultExpressionsList = new LinkedList<>();
    for (Map.Entry<String, Map<Object, Integer>> stringMapEntry : usedExpressionsMap.entrySet()) {
      String expression = stringMapEntry.getKey();
      // Removing internal expressions.
      if (checkIfDefaultOrchestrationConstantInExpression(expression) || expression.contains("toString")) {
        continue;
      }
      Set<Object> expressionValueSet = stringMapEntry.getValue().keySet();
      for (Object value : expressionValueSet) {
        String expressionValue = String.valueOf(value);
        ExpressionBlock expressionBlock = ExpressionBlock.newBuilder()
                                              .setExpression(expression)
                                              .setExpressionValue(expressionValue)
                                              .setCount(stringMapEntry.getValue().get(value))
                                              .build();
        resultExpressionsList.add(expressionBlock);
      }
    }
    return resultExpressionsList;
  }

  private boolean checkIfDefaultOrchestrationConstantInExpression(String expression) {
    List<String> defaultExpressions = Arrays.asList(OrchestrationConstants.CURRENT_STATUS,
        OrchestrationConstants.PIPELINE_FAILURE, OrchestrationConstants.PIPELINE_SUCCESS,
        OrchestrationConstants.STAGE_SUCCESS, OrchestrationConstants.STAGE_FAILURE, OrchestrationConstants.LIVE_STATUS,
        OrchestrationConstants.ALWAYS, OrchestrationConstants.ROLLBACK_MODE_EXECUTION,
        OrchestrationConstants.ALL_DEPENDANTS_SUCCESS, OrchestrationConstants.ANY_DEPENDANT_FAILURE);
    for (String defaultExpression : defaultExpressions) {
      if (expression.contains(defaultExpression)) {
        return true;
      }
    }
    return false;
  }

  private Object evaluateExpressionForV1(
      Ambiance ambiance, String whenCondition, List<ExpressionBlock> expressionBlocks) {
    Object evaluatedExpression = null;
    EngineExpressionEvaluator engineExpressionEvaluator =
        pmsEngineExpressionService.prepareExpressionEvaluator(ambiance, false);
    try {
      evaluatedExpression = engineExpressionEvaluator.evaluateExpression(whenCondition);
    } catch (Exception ex) {
      log.error(String.format("Error while resolving expression %s with jexl", whenCondition), ex);
    }
    expressionBlocks.addAll(getAllExpressions(engineExpressionEvaluator));

    // Resolving in cel mode.
    try {
      engineExpressionEvaluator = pmsEngineExpressionService.prepareExpressionEvaluator(ambiance, true);
      if (evaluatedExpression != null) {
        evaluatedExpression = engineExpressionEvaluator.evaluateExpression(evaluatedExpression.toString());
      } else {
        evaluatedExpression = engineExpressionEvaluator.evaluateExpression(whenCondition);
      }
    } catch (Exception ex) {
      log.error(String.format("Error while resolving expression %s with cel", whenCondition), ex);
    }
    expressionBlocks.addAll(getAllExpressions(engineExpressionEvaluator));
    return evaluatedExpression;
  }

  private Object evaluateExpressionForV0(
      Ambiance ambiance, String whenCondition, List<ExpressionBlock> expressionBlocks) {
    EngineExpressionEvaluator engineExpressionEvaluator =
        pmsEngineExpressionService.prepareExpressionEvaluator(ambiance);
    Object evaluatedExpression = engineExpressionEvaluator.evaluateExpression(whenCondition);
    expressionBlocks.addAll(getAllExpressions(engineExpressionEvaluator));
    return evaluatedExpression;
  }

  private PreFacilitationExecutionCheck handleExpressionEvaluationError(
      Exception ex, String conditionExpression, Ambiance ambiance) {
    Exception cascadedException = ex;
    if (ex instanceof JexlException) {
      cascadedException = new JexlRuntimeException(conditionExpression, ex);
    }
    if (ex instanceof NullPointerException) {
      cascadedException = new InvalidRequestException(
          "Error in evaluating when condition. Please check if the expression for when condition is correct");
    }
    orchestrationEngine.handleError(ambiance, cascadedException);
    return PreFacilitationExecutionCheck.builder()
        .proceed(false)
        .reason("Error in evaluating configured when condition on step")
        .build();
  }

  /**
   * Converts the evaluated expression to a boolean value.
   * Handles cases where the expression evaluates to a String, Boolean, or null.
   */
  @VisibleForTesting
  boolean convertToBoolean(Object evaluatedExpression, String whenCondition) {
    if (evaluatedExpression instanceof Boolean) {
      return (Boolean) evaluatedExpression;
    }

    if (evaluatedExpression instanceof String stringValue) {
      log.debug(String.format("Evaluated expression is a String: '%s' for condition [%s]", stringValue, whenCondition));

      if (stringValue.equalsIgnoreCase("true") || stringValue.equalsIgnoreCase("false")) {
        return Boolean.parseBoolean(stringValue);
      }
      log.warn("When condition evaluated expression is not a boolean: '{}'", stringValue);
      return !stringValue.isEmpty();
    }
    log.warn("When condition evaluated expression is not a boolean or a string: '{}'", evaluatedExpression);
    return (Boolean) evaluatedExpression;
  }

  /**
   * Checks if the node should be hidden from the graph when skipped.
   * This check is only applicable for V1 Run steps.
   * Returns true if the step type is V1 "Run" and env variable HARNESS_HIDE_WHEN_SKIPPED is "true"
   */
  @VisibleForTesting
  @SuppressWarnings("unchecked")
  boolean shouldHideFromGraph(Node node) {
    try {
      if (node.getStepType() == null || !RUN_V1_STEP_TYPE.equals(node.getStepType())
          || node.getStepParameters() == null) {
        return false;
      }

      // Deserialize to typed StepElementParametersV1
      StepElementParametersV1 stepParams =
          RecastOrchestrationUtils.fromMap(node.getStepParameters(), StepElementParametersV1.class);
      if (stepParams == null || !(stepParams.getSpec() instanceof RunStepInfoV1 runStepInfoV1)) {
        return false;
      }

      // Get env field: ParameterField<Map<String, JsonNode>>
      ParameterField<Map<String, JsonNode>> env = runStepInfoV1.getEnv();
      if (env == null || env.fetchFinalValue() == null) {
        return false;
      }

      // Get HARNESS_HIDE_WHEN_SKIPPED from env map
      Map<String, JsonNode> envMap = (Map<String, JsonNode>) env.fetchFinalValue();
      JsonNode harnessHideField = envMap.get(HARNESS_HIDE_WHEN_SKIPPED);
      if (harnessHideField == null) {
        return false;
      }

      // Get the JsonNode value and extract text
      return TRUE.equalsIgnoreCase(harnessHideField.asText());
    } catch (Exception ex) {
      log.debug("Error checking HARNESS_HIDE_WHEN_SKIPPED env variable, defaulting to not hiding from graph", ex);
    }
    return false;
  }
}
