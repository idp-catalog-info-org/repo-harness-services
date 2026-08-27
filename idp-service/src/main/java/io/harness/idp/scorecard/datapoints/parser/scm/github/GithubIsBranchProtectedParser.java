/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.scm.github;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.DEFAULT_BRANCH_KEY_ESCAPED;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERRORS;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.GITHUB_REPOSITORY_ACCESS_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_BRANCH_NAME_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.common.GithubCommonUtils;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class GithubIsBranchProtectedParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataFetchDTO dataFetchDTO) {
    Map<String, Object> dataPointData = new HashMap<>();

    List<InputValue> inputValues = dataFetchDTO.getInputValues();
    if (inputValues.size() != 1) {
      dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, INVALID_BRANCH_NAME_ERROR));
    }
    String inputValue = inputValues.get(0).getValue();
    data = (Map<String, Object>) data.get(dataFetchDTO.getRuleIdentifier());

    if (isEmpty(data) || !isEmpty((String) data.get(ERROR_MESSAGE_KEY))) {
      String errorMessage = (String) data.get(ERROR_MESSAGE_KEY);
      dataPointData.putAll(constructDataPointInfo(
          dataFetchDTO, false, !isEmpty(errorMessage) ? errorMessage : INVALID_BRANCH_NAME_ERROR));
      return dataPointData;
    }

    Map<String, Object> dslResponse = (Map<String, Object>) data.get(DSL_RESPONSE);
    List<Map<String, Object>> errors = null;
    if (dslResponse != null) {
      errors = (List<Map<String, Object>>) dslResponse.get(ERRORS);
    }

    if (isEmpty(data) || !isEmpty(errors)) {
      String errorMessage = null;

      if (!isEmpty(errors)) {
        errorMessage = (String) errors.get(0).get(MESSAGE_KEY);
      }

      if (!isEmpty(errorMessage) && errorMessage.contains("Could not resolve to a Repository with the name")) {
        String[] repoParts = errorMessage.split("'");
        String repoName = "";
        if (repoParts.length > 1) {
          repoName = repoParts[1];
        }
        String customErrorMessage =
            !isEmpty(repoName) ? repoName + " " + GITHUB_REPOSITORY_ACCESS_ERROR : GITHUB_REPOSITORY_ACCESS_ERROR;
        dataPointData.putAll(constructDataPointInfo(dataFetchDTO, null, customErrorMessage));
        return dataPointData;
      }

      dataPointData.putAll(constructDataPointInfo(
          dataFetchDTO, null, !isEmpty(errorMessage) ? errorMessage : INVALID_BRANCH_NAME_ERROR));
      return dataPointData;
    }

    Map<String, Object> ref;
    if (CommonUtils.findObjectByName(data, "defaultBranchRef") == null
        && CommonUtils.findObjectByName(data, "ref") == null) {
      String errorMessage = GithubCommonUtils.fetchErrorMessageFromGraphQLResponse(data);
      dataPointData.putAll(constructDataPointInfo(
          dataFetchDTO, false, !isEmpty(errorMessage) ? errorMessage : INVALID_BRANCH_NAME_ERROR));
      return dataPointData;
    }

    if (inputValue.equals(DEFAULT_BRANCH_KEY_ESCAPED)) {
      ref = (Map<String, Object>) CommonUtils.findObjectByName(data, "defaultBranchRef");
    } else {
      ref = (Map<String, Object>) CommonUtils.findObjectByName(data, "ref");
    }
    Map<String, Object> branchProtectionRule = (Map<String, Object>) ref.get("branchProtectionRule");

    boolean value = false;
    String errorMessage = null;
    if (branchProtectionRule != null) {
      value = !(boolean) branchProtectionRule.get("allowsDeletions")
          && !(boolean) branchProtectionRule.get("allowsForcePushes");
    }
    dataPointData.putAll(constructDataPointInfo(dataFetchDTO, value, errorMessage));
    return dataPointData;
  }
}
