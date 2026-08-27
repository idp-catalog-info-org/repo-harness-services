/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.eventlistener.evaluation;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EventListenerStepNGException;
import io.harness.expression.common.ExpressionMode;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class EventListenerStepInstanceCriteriaEvaluator {
  public static boolean evaluateJexlCriteria(
      String expression, EventListenerStepInstanceExpressionEvaluator eventListenerStepInstanceExpressionEvaluator) {
    if (StringUtils.isBlank(expression)) {
      throw new EventListenerStepNGException("Expression cannot be blank in criteria", true);
    }
    try {
      Object result = eventListenerStepInstanceExpressionEvaluator.evaluateExpression(
          expression, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      if (result instanceof Boolean) {
        return (boolean) result;
      } else {
        throw new EventListenerStepNGException(
            String.format("Non boolean result while evaluating criteria for expressions %s", expression), true);
      }
    } catch (Exception e) {
      throw new EventListenerStepNGException(
          String.format("Error while evaluating criteria for expression: %s", expression), true, e);
    }
  }
}
