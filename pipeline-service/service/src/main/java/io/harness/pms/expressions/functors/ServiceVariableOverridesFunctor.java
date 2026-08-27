/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;
import static io.harness.expression.common.ExpressionConstants.EXPR_END;
import static io.harness.expression.common.ExpressionConstants.EXPR_END_CEL;
import static io.harness.expression.common.ExpressionConstants.EXPR_START;
import static io.harness.expression.common.ExpressionConstants.EXPR_START_CEL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.expression.LateBindingMap;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.ExpressionModeMapper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_PIPELINE})
public class ServiceVariableOverridesFunctor extends LateBindingMap {
  private final Ambiance ambiance;
  private final PmsEngineExpressionService pmsEngineExpressionService;
  private static final String EXECUTION = "EXECUTION";
  // Expression token to climb from a step group to its enclosing (parent) step group in v1.
  private static final String GET_PARENT_STEP_GROUP = "getParentStepGroup";

  public ServiceVariableOverridesFunctor(Ambiance ambiance, PmsEngineExpressionService pmsEngineExpressionService) {
    this.ambiance = ambiance;
    this.pmsEngineExpressionService = pmsEngineExpressionService;
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String variableName)) {
      return null;
    }

    if (HarnessYamlVersion.isV1(ambiance.getMetadata().getHarnessVersion())) {
      return resolveV1(variableName);
    }
    return resolveV0(variableName);
  }

  private Object resolveV0(String variableName) {
    List<Level> levels = ambiance.getLevelsList();
    List<Level> subLevels;
    List<String> fqnList = new ArrayList<>();

    Set<String> groups = levels.stream().map(Level::getGroup).collect(Collectors.toSet());
    // step group overrides are rendered within execution context only
    if (!groups.contains(EXECUTION)) {
      // functor will return null to return original expression with mode RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED
      return null;
    }

    // base fqn is serviceVariables.variable_name
    fqnList.add(
        String.format("%s%s.%s%s", EXPR_START, YAMLFieldNameConstants.SERVICE_VARIABLES, variableName, EXPR_END));

    int currentIndex = 0;
    for (Level level : levels) {
      if ("STEP_GROUP".equals(level.getStepType().getType())) {
        // create a sub list starting at pipeline and ending at the step group
        subLevels = levels.subList(0, currentIndex + 1);
        String fqn = AmbianceUtils.getFQNUsingLevels(subLevels);

        // append variables.variable_name
        fqn = String.format("%s.%s.%s", fqn, YAMLFieldNameConstants.VARIABLES, variableName);
        // Create expression for the engine
        fqn = String.format("%s%s%s", EXPR_START, fqn, EXPR_END);

        fqnList.add(fqn);
      }
      currentIndex++;
    }

    String finalValue = null;

    for (int i = fqnList.size() - 1; i >= 0; i--) {
      String fqnRendered = pmsEngineExpressionService.renderExpression(ambiance, fqnList.get(i),
          ExpressionModeMapper.fromExpressionModeProto(ExpressionMode.RETURN_NULL_IF_UNRESOLVED));

      // nearest non-null value needs to be picked up
      if (fqnRendered != null && !Objects.equals(fqnRendered, "null")) {
        finalValue = fqnRendered;
        break;
      }
    }

    return finalValue;
  }

  /**
   * Resolves a service variable override for v1 YAMLs.
   *
   * <p>In v1 step groups carry group {@link AmbianceUtils#STEP_GROUP_V1} ("GROUP") and a step group's variables are
   * addressed through the {@code group} alias, which resolves the nearest enclosing step group (see
   * {@code step-group-v1-with-input.yaml} which uses {@code <+group.variables.var1>}). Outer step groups are reached by
   * climbing through {@code getParentStepGroup}. The base value comes from the stage scoped {@code serviceVariables}
   * sweeping output. The innermost enclosing step group that defines the variable wins, otherwise the next outer step
   * group is consulted and finally the service variable.
   */
  private Object resolveV1(String variableName) {
    List<Level> levels = ambiance.getLevelsList();

    Set<String> groups = levels.stream().map(Level::getGroup).collect(Collectors.toSet());
    // overrides only make sense inside a stage where serviceVariables sweeping output exists
    if (!groups.contains(AmbianceUtils.STAGE)) {
      // functor will return null to return original expression with mode RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED
      return null;
    }

    long stepGroupCount =
        levels.stream().filter(level -> AmbianceUtils.STEP_GROUP_V1.equals(level.getStepType().getType())).count();

    // ordered list of fully qualified names from innermost step group to outermost, then the stage scoped service
    // variable as the last resort. The group alias resolves the nearest step group, getParentStepGroup climbs outward.
    List<String> fqnList = new ArrayList<>();
    StringBuilder groupExpr = new StringBuilder(YAMLFieldNameConstants.GROUP);
    for (int i = 0; i < stepGroupCount; i++) {
      fqnList.add(String.format("%s.%s.%s", groupExpr, YAMLFieldNameConstants.VARIABLES, variableName));
      groupExpr.append('.').append(GET_PARENT_STEP_GROUP);
    }
    fqnList.add(String.format("%s.%s", YAMLFieldNameConstants.SERVICE_VARIABLES, variableName));

    // innermost step group wins, service variable is the last resort
    for (String fqn : fqnList) {
      String rendered = renderFirstNonNull(fqn);
      if (rendered != null) {
        return rendered;
      }
    }

    return null;
  }

  /**
   * Renders the given fully qualified name trying both supported v1 delimiter forms ({@code <+...>} then
   * {@code ${{...}}}), returning the first non-null resolution or {@code null} if neither resolves.
   */
  private String renderFirstNonNull(String fqn) {
    List<String> expressionForms = List.of(
        String.format("%s%s%s", EXPR_START, fqn, EXPR_END), String.format("%s%s%s", EXPR_START_CEL, fqn, EXPR_END_CEL));
    for (String expression : expressionForms) {
      String rendered = pmsEngineExpressionService.renderExpression(
          ambiance, expression, ExpressionModeMapper.fromExpressionModeProto(ExpressionMode.RETURN_NULL_IF_UNRESOLVED));
      if (rendered != null && !Objects.equals(rendered, "null")) {
        return rendered;
      }
    }
    return null;
  }
}
