/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.ci.commonconstants.BuildEnvironmentConstants.PLUGIN_OVERRIDE_IMAGE;
import static io.harness.ci.commonconstants.CIExecutionConstants.NULL_STR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves expression-valued environment variables on run / background steps during K8s init. For now it targets the
 * single {@code PLUGIN_OVERRIDE_IMAGE} env var (which carries a {@code serverlessImageConfig} functor expression), but
 * it is intentionally structured so it can be extended to resolve multiple env vars.
 */
@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class EnvironmentVariablesResolver {
  private static final int MAX_DEPTH = 4;

  /**
   * A run/background step's env object that carries a targeted env var expression, paired with the raw expression
   * captured before {@code resolveGitAppFunctor} can clobber it.
   */
  @Value
  public static class EnvironmentVariableRef {
    ObjectNode envNode;
    String rawExpression;
  }

  /**
   * Phase 1 - run BEFORE {@code resolveGitAppFunctor}. Captures the raw {@code PLUGIN_OVERRIDE_IMAGE} expression on
   * every run/background step without rendering (serviceOutput is not available yet).
   */
  public List<EnvironmentVariableRef> getEnvVarsToResolve(ExecutionElementConfig executionElementConfig) {
    List<EnvironmentVariableRef> refs = new ArrayList<>();
    try {
      if (executionElementConfig != null) {
        collectWrapperList(executionElementConfig.getSteps(), refs, 0);
      }
    } catch (Exception e) {
      // Never break init: fall back to the existing downstream defensive rendering.
      log.debug("Exception stashing env var to resolve", e);
    }
    return refs;
  }

  /**
   * Phase 2 - run AFTER {@code serviceOutput} is available. Renders each stashed expression and writes the resolved
   * value back onto its env node (repairing a JEXL value that {@code resolveGitAppFunctor} may have set to {@code
   * null}). Falls back to the raw expression when still unresolved, so a literal {@code "null"} is never written.
   *
   * @param renderer renders an expression against the ambiance; must use RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED
   */
  public void resolveEnvVars(List<EnvironmentVariableRef> refs, UnaryOperator<String> renderer) {
    if (isEmpty(refs) || renderer == null) {
      return;
    }
    for (EnvironmentVariableRef ref : refs) {
      try {
        String rendered = renderer.apply(ref.getRawExpression());
        String finalValue = isResolved(rendered) ? rendered : ref.getRawExpression();
        ref.getEnvNode().set(PLUGIN_OVERRIDE_IMAGE, TextNode.valueOf(finalValue));
      } catch (Exception e) {
        log.debug("Exception resolving stashed env var", e);
      }
    }
  }

  private void collectWrapperList(List<ExecutionWrapperConfig> wrappers, List<EnvironmentVariableRef> refs, int depth) {
    if (isEmpty(wrappers) || depth > MAX_DEPTH) {
      return;
    }
    for (ExecutionWrapperConfig wrapper : wrappers) {
      try {
        collectWrapper(wrapper, refs, depth);
      } catch (Exception e) {
        log.debug("Exception collecting env var to resolve for wrapper", e);
      }
    }
  }

  private void collectWrapper(ExecutionWrapperConfig wrapper, List<EnvironmentVariableRef> refs, int depth) {
    if (wrapper == null || depth > MAX_DEPTH) {
      return;
    }
    if (wrapper.getStep() != null && !wrapper.getStep().isNull()) {
      collectStepNode(wrapper.getStep(), refs);
    } else if (wrapper.getParallel() != null && !wrapper.getParallel().isNull()) {
      collectParallel(wrapper.getParallel(), refs, depth);
    } else if (wrapper.getStepGroup() != null && !wrapper.getStepGroup().isNull()) {
      collectStepGroup(wrapper.getStepGroup(), refs, depth);
    }
  }

  private void collectJsonNodeAsWrapper(JsonNode wrapperNode, List<EnvironmentVariableRef> refs, int depth) {
    if (wrapperNode == null || !wrapperNode.isObject() || depth > MAX_DEPTH) {
      return;
    }
    JsonNode stepNode = wrapperNode.get("step");
    if (stepNode != null && !stepNode.isNull()) {
      collectStepNode(stepNode, refs);
      return;
    }
    JsonNode parallelNode = wrapperNode.get("parallel");
    if (parallelNode != null && !parallelNode.isNull()) {
      collectParallel(parallelNode, refs, depth);
      return;
    }
    JsonNode stepGroupNode = wrapperNode.get("stepGroup");
    if (stepGroupNode != null && !stepGroupNode.isNull()) {
      collectStepGroup(stepGroupNode, refs, depth);
    }
  }

  private void collectParallel(JsonNode parallelNode, List<EnvironmentVariableRef> refs, int depth) {
    if (parallelNode == null || parallelNode.isNull()) {
      return;
    }
    // V1 wire shape: parallel is a bare JSON array of wrappers.
    if (parallelNode.isArray()) {
      for (JsonNode sectionNode : parallelNode) {
        collectJsonNodeAsWrapper(sectionNode, refs, depth + 1);
      }
    }
  }

  private void collectStepGroup(JsonNode stepGroupNode, List<EnvironmentVariableRef> refs, int depth) {
    if (stepGroupNode == null || stepGroupNode.isNull() || !stepGroupNode.isObject()) {
      return;
    }
    JsonNode stepsNode = stepGroupNode.get("steps");
    if (stepsNode != null && stepsNode.isArray()) {
      for (JsonNode stepNode : stepsNode) {
        collectJsonNodeAsWrapper(stepNode, refs, depth + 1);
      }
    }
  }

  private void collectStepNode(JsonNode stepNode, List<EnvironmentVariableRef> refs) {
    if (stepNode == null || !stepNode.isObject()) {
      return;
    }
    JsonNode runNode = stepNode.get("run");
    if (runNode != null && runNode.isObject()) {
      collectEnvVarRefToResolve(runNode.get("env"), refs);
      return;
    }
    JsonNode backgroundNode = stepNode.get("background");
    if (backgroundNode != null && backgroundNode.isObject()) {
      collectEnvVarRefToResolve(backgroundNode.get("env"), refs);
    }
  }

  private void collectEnvVarRefToResolve(JsonNode envNode, List<EnvironmentVariableRef> refs) {
    if (!(envNode instanceof ObjectNode)) {
      return;
    }
    ObjectNode envObject = (ObjectNode) envNode;
    JsonNode value = envObject.get(PLUGIN_OVERRIDE_IMAGE);
    if (value == null || !value.isTextual()) {
      return;
    }
    String raw = value.asText();
    if (hasExpression(raw)) {
      refs.add(new EnvironmentVariableRef(envObject, raw));
    }
  }

  private boolean hasExpression(String value) {
    if (isEmpty(value)) {
      return false;
    }
    return NGExpressionUtils.isRuntimeOrExpressionFieldV0AndV1(value.trim());
  }

  private boolean isResolved(String rendered) {
    return isNotEmpty(rendered) && !NULL_STR.equals(rendered) && !hasExpression(rendered);
  }
}
