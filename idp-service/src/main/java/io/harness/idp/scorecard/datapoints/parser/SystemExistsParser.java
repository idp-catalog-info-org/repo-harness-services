/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class SystemExistsParser extends GenericExpressionParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    Map<String, Object> dataPointResponse = new HashMap<>();

    if (data.get(ERROR_MESSAGE_KEY) != null) {
      dataPointResponse.put(DATA_POINT_VALUE_KEY, false);
    } else {
      dataPointResponse.put(DATA_POINT_VALUE_KEY, true);
    }
    return Map.of(dataFetchDTO.getRuleIdentifier(), dataPointResponse);
  }
}
