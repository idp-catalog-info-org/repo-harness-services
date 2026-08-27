/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.utils.DslCommons;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.spec.server.idp.v1.model.ScmConfig;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BitbucketContentDsl extends ScmContentDsl implements DslDataProvider {
  private static final String FILE_CONTENT_URL =
      "/2.0/repositories/{REPOSITORY_OWNER}/{REPOSITORY_NAME}/src/{REPOSITORY_BRANCH}/"
      + "{REPOSITORY_SUB_FOLDER}{FILE_PATH_REPLACER}?pagelen=100";

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, Object config) {
    Map<String, Object> dataPointData = new HashMap<>();
    if (!(config instanceof ScmConfig)) {
      return dataPointData;
    }
    ScmConfig scmConfig = (ScmConfig) config;

    String url = "https://api." + scmConfig.getRepoScm();
    return fetchData(scmConfig, accountIdentifier, url);
  }

  @Override
  public ApiRequestDetails getApiRequestDetails(String baseUrl, Map<String, String> authHeaders, int index) {
    return ApiRequestDetails.builder().method("GET").headers(authHeaders).url(baseUrl + FILE_CONTENT_URL).build();
  }

  @Override
  public String getFileName(Response response, ScmConfig scmConfig) {
    if (response.getStatus() == 200) {
      Map<String, Object> data = GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class);
      List<Map<String, Object>> values = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "values");
      return iterateAndFetchMatchingFile(values, scmConfig, "path");
    }
    return null;
  }

  @Override
  public Map<String, Object> processResponse(Response response) {
    return DslCommons.processResponse(response);
  }
}
