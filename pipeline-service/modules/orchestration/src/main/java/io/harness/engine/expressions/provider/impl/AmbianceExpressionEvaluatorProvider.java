/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.provider.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.expressions.evaluator.AmbianceExpressionEvaluator;
import io.harness.engine.expressions.functors.type.NodeExecutionEntityType;
import io.harness.engine.expressions.provider.ExpressionEvaluatorProvider;
import io.harness.expression.AutoCloseableExpressionTracker;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.VariableResolverTracker;
import io.harness.pms.contracts.ambiance.Ambiance;

import java.util.Map;
import java.util.Set;

@OwnedBy(CDC)
public class AmbianceExpressionEvaluatorProvider implements ExpressionEvaluatorProvider {
  @Override
  public EngineExpressionEvaluator get(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, Object> contextMap) {
    return new AmbianceExpressionEvaluator(
        variableResolverTracker, ambiance, entityTypes, refObjectSpecific, contextMap);
  }

  @Override
  public EngineExpressionEvaluator get(Ambiance ambiance, Set<NodeExecutionEntityType> entityTypes,
      boolean refObjectSpecific, Map<String, Object> contextMap, AutoCloseableExpressionTracker expressionTracker) {
    return new AmbianceExpressionEvaluator(expressionTracker, ambiance, entityTypes, refObjectSpecific, contextMap);
  }

  @Override
  public EngineExpressionEvaluator get(VariableResolverTracker variableResolverTracker, Ambiance ambiance,
      Set<NodeExecutionEntityType> entityTypes, boolean refObjectSpecific, Map<String, Object> contextMap,
      boolean isCel) {
    return new AmbianceExpressionEvaluator(
        variableResolverTracker, ambiance, entityTypes, refObjectSpecific, contextMap, isCel);
  }

  @Override
  public EngineExpressionEvaluator get(Ambiance ambiance, Set<NodeExecutionEntityType> entityTypes,
      boolean refObjectSpecific, Map<String, Object> contextMap, AutoCloseableExpressionTracker expressionTracker,
      boolean isCel) {
    return new AmbianceExpressionEvaluator(
        expressionTracker, ambiance, entityTypes, refObjectSpecific, contextMap, isCel);
  }
}
