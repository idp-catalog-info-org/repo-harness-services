/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.EXPRESSION_PATTERN;
import static io.harness.idp.common.Constants.MISSING_DATA;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_SPEC_OWNER;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_CONDITIONAL_INPUT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionMode;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class GenericExpressionParser implements DataPointParser {
  private static final String EXPR_START = "<+";
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    DataPointEntity dataPoint = dataFetchDTO.getDataPoint();
    String outcomeExpression = dataPoint.getOutcomeExpression();
    String sourceIdentifier = dataPoint.getDataSourceIdentifier();
    Map<String, Object> dataPointResponse = new HashMap<>();
    if (outcomeExpression == null) {
      List<InputValue> inputValues = dataFetchDTO.getInputValues();
      if (inputValues.size() != 1) {
        dataPointResponse.put(ERROR_MESSAGE_KEY, INVALID_CONDITIONAL_INPUT);
        dataPointResponse.put(DATA_POINT_VALUE_KEY, null);
        return Map.of(dataFetchDTO.getRuleIdentifier(), dataPointResponse);
      }
      outcomeExpression = inputValues.get(0).getValue();
      outcomeExpression = outcomeExpression.replace("\"", "");
    }
    String apiVersion = (String) CommonUtils.findObjectByName(data, "apiVersion");
    if (dataPoint.getIdentifier().equals(CATALOG_SPEC_OWNER) && apiVersion != null
        && apiVersion.equals("harness.io/v1")) {
      outcomeExpression = "catalog.owner!=null && catalog.owner.toLowerCase()!=\"unknown\"";
    }
    Matcher matcher = EXPRESSION_PATTERN.matcher(outcomeExpression);
    if (matcher.find()) {
      outcomeExpression = outcomeExpression.replace(EXPR_START, EXPR_START + sourceIdentifier + DOT_SEPARATOR);
    }
    Map<String, Map<String, Object>> expressionData = new HashMap<>();
    expressionData.put(sourceIdentifier, (Map<String, Object>) data.get(DSL_RESPONSE));
    IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(expressionData);

    Object value = null;
    dataPointResponse.put(ERROR_MESSAGE_KEY, "");
    try {
      value = evaluator.evaluateExpression(outcomeExpression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
      if (value == null) {
        log.warn(
            "Could not find the required data by evaluating expression for data point {}", dataPoint.getIdentifier());
        dataPointResponse.put(ERROR_MESSAGE_KEY, MISSING_DATA);
      }
    } catch (Exception e) {
      log.warn("Datapoint expression evaluation failed for data point {}", dataPoint.getIdentifier(), e);
      dataPointResponse.put(ERROR_MESSAGE_KEY, "Datapoint extraction expression evaluation failed");
    }
    dataPointResponse.put(DATA_POINT_VALUE_KEY, value);
    return Map.of(dataFetchDTO.getRuleIdentifier(), dataPointResponse);
  }
}
