/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MISSING_DATA;

import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.HashMap;
import java.util.Map;

public class DefaultHQLParser implements DataPointParser {
  static final String NO_DATA_FOUND_ERROR = "No data found for rule";
  static final String NO_DSL_RESPONSE_ERROR = "No dsl_response found in HQL result";
  static final String MISSING_VALUE_ERROR = "HQL result is missing the '" + DATA_POINT_VALUE_KEY + "' key";
  static final String NO_MATCHING_ROWS = "NO_MATCHING_ROWS";
  static final String NO_DATA_FOR_DATA_POINT_ERROR = MISSING_DATA + ": No data found for data point";

  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    Map<String, Object> dataPointData = new HashMap<>();

    // Step 1: Extract rule-specific data from the input map.
    // Input has structure: {ruleIdentifier: {dsl_response: {value: ...} or error_messages: ...}}
    Map<String, Object> ruleData = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());

    // Step 2: Check for errors in the fetched data. A missing/empty rule entry or a non-empty
    // error message means we have no usable result to parse.
    if (isEmpty(ruleData) || !isEmpty((String) ruleData.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = isEmpty(ruleData) ? NO_DATA_FOUND_ERROR : (String) ruleData.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, errorMessage));
      return dataPointData;
    }

    // Step 3: Extract the dsl_response payload. This parser always expects the HQL result to be
    // wrapped in a map under DSL_RESPONSE; anything else is treated as a missing response.
    Object dslResponseRaw = ruleData.get(DSL_RESPONSE);
    if (!(dslResponseRaw instanceof Map)) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, NO_DSL_RESPONSE_ERROR));
      return dataPointData;
    }
    Map<String, Object> dslResponse = (Map<String, Object>) dslResponseRaw;

    // Step 4: The dsl_response must always carry the actual result under the "value" key.
    // If it is absent, the result is malformed and cannot be parsed into a data point.
    if (!dslResponse.containsKey(DATA_POINT_VALUE_KEY)) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, MISSING_VALUE_ERROR));
      return dataPointData;
    }

    // Step 5: Treat the HQL no-result sentinel as missing data before validating its type.
    Object value = dslResponse.get(DATA_POINT_VALUE_KEY);
    if (NO_MATCHING_ROWS.equals(value)) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, NO_DATA_FOR_DATA_POINT_ERROR));
      return dataPointData;
    }

    dataPointData.putAll(validateAndConstructDataPointInfo(dataFetchDTO, value));
    return dataPointData;
  }
}
