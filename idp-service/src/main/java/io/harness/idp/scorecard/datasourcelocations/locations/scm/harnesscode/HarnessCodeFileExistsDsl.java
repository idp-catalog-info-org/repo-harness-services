/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.scm.harnesscode;

import static io.harness.idp.common.CommonUtils.removeLeadingSlash;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.BRANCH_NAME;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.FILE_PATH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_BRANCH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.ScmBaseDslNoLoop;
import io.harness.spec.server.idp.v1.model.InputValue;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@OwnedBy(HarnessTeam.IDP)
public class HarnessCodeFileExistsDsl extends ScmBaseDslNoLoop {
  private static final String FILE_PATH_REPLACER = "{FILE_PATH_REPLACER}";
  @Inject private @Named("harnessCodeRepoConfig") HarnessCodeRepoConfig harnessCodeRepoConfig;

  @Override
  protected String constructUrl(String baseUrl, String url, Map<String, String> replaceableUrls,
      DataPointEntity dataPoint, List<InputValue> inputValues) {
    Optional<InputValue> inputValueOpt =
        inputValues.stream().filter(inputValue -> inputValue.getKey().equals(BRANCH_NAME)).findFirst();
    if (inputValueOpt.isPresent()) {
      String inputValue = inputValueOpt.get().getValue();
      inputValue = inputValue.replace("\"", "");
      if (!inputValue.isEmpty()) {
        url = url.replace(REPOSITORY_BRANCH, inputValue);
      }
    }
    return super.constructUrl(harnessCodeRepoConfig.getInternalApiUrl(), url, replaceableUrls, dataPoint, inputValues);
  }

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    Optional<InputValue> inputValueOpt =
        inputValues.stream().filter(inputValue -> inputValue.getKey().equals(FILE_PATH)).findFirst();
    if (inputValueOpt.isPresent()) {
      String inputValue = inputValueOpt.get().getValue();
      inputValue = inputValue.replace("\"", "");
      if (!inputValue.isEmpty()) {
        int lastSlash = inputValue.lastIndexOf("/");
        if (lastSlash != -1) {
          String path = removeLeadingSlash(inputValue.substring(0, lastSlash));
          url = url.replace(FILE_PATH_REPLACER, path);
        } else {
          url = url.replace(FILE_PATH_REPLACER, "");
        }
      }
    }
    return url;
  }

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestBody(
      String requestBody, DataPointEntity dataPoint, List<InputValue> inputValues, Object entity) {
    return requestBody;
  }
}
