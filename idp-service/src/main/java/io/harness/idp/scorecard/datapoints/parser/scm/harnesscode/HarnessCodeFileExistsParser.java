/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.scm.harnesscode;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_CONDITIONAL_INPUT;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_FILE_NAME_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.FILE_PATH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@OwnedBy(HarnessTeam.IDP)
public class HarnessCodeFileExistsParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    List<InputValue> inputValues = dataFetchDTO.getInputValues();
    Map<String, Object> dataPointData = new HashMap<>();

    String inputValue;
    Optional<InputValue> inputValueOptForFilePath =
        inputValues.stream().filter(input -> input.getKey().equals(FILE_PATH)).findFirst();
    if (inputValueOptForFilePath.isPresent() && inputValues.size() == 2) {
      inputValue = inputValueOptForFilePath.get().getValue();
    } else {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, INVALID_CONDITIONAL_INPUT));
      return dataPointData;
    }

    data = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());

    if (isEmpty(data) || !isEmpty((String) data.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(
          constructDataPointInfo(dataFetchDTO, false, !isEmpty(errorMessage) ? errorMessage : INVALID_FILE_NAME_ERROR));
      return dataPointData;
    }

    List<Map<String, Object>> entries = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "entries");
    boolean isPresent = false;
    inputValue = inputValue.replace("\"", "");
    int lastSlash = inputValue.lastIndexOf("/");
    String inputFile = (lastSlash != -1) ? inputValue.substring(lastSlash + 1) : inputValue;
    for (Map<String, Object> entry : entries) {
      String fileName = (String) entry.get("name");
      if (fileName.equals(inputFile)) {
        isPresent = true;
        break;
      }
    }
    dataPointData.putAll(constructDataPointInfo(dataFetchDTO, isPresent, null));
    return dataPointData;
  }
}
