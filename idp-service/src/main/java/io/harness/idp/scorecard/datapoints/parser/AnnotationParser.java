/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionMode;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class AnnotationParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    Map<String, Object> dataPointResponse = new HashMap<>();
    Map<String, Map<String, Object>> expressionData = new HashMap<>();
    DataPointEntity dataPointEntity = dataFetchDTO.getDataPoint();
    expressionData.put(dataPointEntity.getDataSourceIdentifier(), (Map<String, Object>) data.get(DSL_RESPONSE));
    IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(expressionData);

    Object value = null;
    dataPointResponse.put(ERROR_MESSAGE_KEY, "");
    try {
      value = evaluator.evaluateExpression(
          "catalog.metadata.annotations" + DOT_SEPARATOR + dataFetchDTO.getInputValues().get(0).getValue() + "!=null",
          ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    } catch (Exception e) {
      log.warn("Datapoint expression evaluation failed for data point {}", dataPointEntity.getIdentifier(), e);
      dataPointResponse.put(ERROR_MESSAGE_KEY, "Datapoint extraction expression evaluation failed");
    }
    dataPointResponse.put(DATA_POINT_VALUE_KEY, value);
    return Map.of(dataFetchDTO.getRuleIdentifier(), dataPointResponse);
  }
}
