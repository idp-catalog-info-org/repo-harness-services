/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser.NO_DATA_FOR_DATA_POINT_ERROR;
import static io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser.NO_DATA_FOUND_ERROR;

import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.utils.ParserUtils;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DefaultCatalogDSLParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    Map<String, Object> dataPointData = new HashMap<>();
    // Step 1: Extract rule-specific data from the input map.
    // Input has structure: {ruleIdentifier: {dsl_response: {value: ...} or error_messages: ...}}
    Map<String, Object> ruleData = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());

    // Step 2: Check for errors in the fetched data. A missing/empty rule entry, a non-empty
    // error message, or an absent dsl_response all mean we have no usable result to parse. Each
    // case gets its own descriptive message so downstream never receives a null error string.
    if (isEmpty(ruleData) || !isEmpty((String) ruleData.get(ERROR_MESSAGE_KEY))
        || Objects.isNull(ruleData.get(DSL_RESPONSE))) {
      String errorMessage;
      if (isEmpty(ruleData)) {
        errorMessage = NO_DATA_FOUND_ERROR;
      } else if (!isEmpty((String) ruleData.get(ERROR_MESSAGE_KEY))) {
        errorMessage = (String) ruleData.get(ERROR_MESSAGE_KEY);
      } else {
        // dsl_response was null with no explicit error: treat as missing data.
        errorMessage = NO_DATA_FOR_DATA_POINT_ERROR;
      }
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, errorMessage));
      return dataPointData;
    }

    // Step 3: Extract the dsl_response payload. This parser always expects result to be
    // wrapped in a map under DSL_RESPONSE; anything else is treated as a missing response.
    dataPointData.putAll(validateAndConstructDataPointInfo(dataFetchDTO, ruleData.get(DSL_RESPONSE)));
    return dataPointData;
  }
}
