/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.scm.bitbucket;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_BRANCH_NAME_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_CONDITIONAL_INPUT;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_FILE_PATH_ERROR;
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
public class BitbucketFileExistsParser implements DataPointParser {
  static final String INCORRECT_FILE_PATH_ERROR = "No such file or directory:.*";
  static final String INCORRECT_BRANCH_ERROR = "Commit not found";

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
      if (errorMessage.matches(INCORRECT_FILE_PATH_ERROR)) {
        dataPointData.putAll(constructDataPointInfo(dataFetchDTO, false, INVALID_FILE_PATH_ERROR));
      } else if (errorMessage.matches(INCORRECT_BRANCH_ERROR)) {
        dataPointData.putAll(constructDataPointInfo(dataFetchDTO, false, INVALID_BRANCH_NAME_ERROR));
      } else {
        dataPointData.putAll(constructDataPointInfo(dataFetchDTO, false, errorMessage));
      }
      return dataPointData;
    }

    List<Map<String, Object>> entries = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "values");
    boolean isPresent = false;
    inputValue = inputValue.replace("\"", "");
    int lastSlash = inputValue.lastIndexOf("/");
    String inputFile = (lastSlash != -1) ? inputValue.substring(lastSlash + 1) : inputValue;
    for (Map<String, Object> entry : entries) {
      String fileName = (String) entry.get("path");
      int lastSlashForFile = fileName.lastIndexOf("/");
      fileName = (lastSlashForFile != -1) ? fileName.substring(lastSlashForFile + 1) : fileName;
      if (fileName.equals(inputFile)) {
        isPresent = true;
        break;
      }
    }
    dataPointData.putAll(constructDataPointInfo(dataFetchDTO, isPresent, null));
    return dataPointData;
  }
}
