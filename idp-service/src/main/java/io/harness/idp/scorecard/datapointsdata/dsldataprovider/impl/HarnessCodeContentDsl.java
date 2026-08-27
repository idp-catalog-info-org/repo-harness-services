/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import static io.harness.idp.common.Constants.MESSAGE_KEY;
import static io.harness.idp.common.Constants.TEXT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.spec.server.idp.v1.model.ScmConfig;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class HarnessCodeContentDsl extends ScmContentDsl implements DslDataProvider {
  @Inject @Named("harnessCodeRepoConfig") HarnessCodeRepoConfig harnessCodeRepoConfig;
  private static final String LIST_FILES_REQUEST_URL = "/api/v1/repos/{REPOSITORY_NAME}/content/"
      + "{REPOSITORY_SUB_FOLDER}{FILE_PATH_REPLACER}?accountIdentifier={ACCOUNT_IDENTIFIER}&orgIdentifier={ORG_"
      + "IDENTIFIER}&projectIdentifier={PROJECT_IDENTIFIER}&git_ref={REPOSITORY_BRANCH}";
  private static final String FILE_CONTENTS_REQUEST_URL = "/api/v1/repos/{REPOSITORY_NAME}/raw/"
      + "{REPOSITORY_SUB_FOLDER}{FILE_PATH_REPLACER}?accountIdentifier={ACCOUNT_IDENTIFIER}&orgIdentifier={ORG_"
      + "IDENTIFIER}&projectIdentifier={PROJECT_IDENTIFIER}&git_ref={REPOSITORY_BRANCH}";
  private static final List<String> REQUEST_URLS = List.of(LIST_FILES_REQUEST_URL, FILE_CONTENTS_REQUEST_URL);

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, Object config) {
    Map<String, Object> dataPointData = new HashMap<>();
    if (!(config instanceof ScmConfig scmConfig)) {
      return dataPointData;
    }
    String url = harnessCodeRepoConfig.getInternalApiUrl();
    return fetchData(scmConfig, accountIdentifier, url);
  }

  @Override
  public ApiRequestDetails getApiRequestDetails(String baseUrl, Map<String, String> authHeaders, int index) {
    return ApiRequestDetails.builder()
        .method("GET")
        .headers(authHeaders)
        .url(baseUrl + REQUEST_URLS.get(index))
        .build();
  }

  @Override
  public String getFileName(Response response, ScmConfig scmConfig) {
    if (response.getStatus() == 200) {
      Map<String, Object> data = GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class);
      List<Map<String, Object>> entries = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "entries");
      return iterateAndFetchMatchingFile(entries, scmConfig, "name");
    }
    return null;
  }

  @Override
  public Map<String, Object> processResponse(Response response) {
    if (response.getStatus() == 200) {
      return Map.of(TEXT, response.getEntity());
    } else {
      if (response.getEntity() instanceof String) {
        throw new BadRequestException(
            (String) GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class).get(MESSAGE_KEY));
      } else {
        throw new InvalidRequestException(((ResponseMessage) response.getEntity()).getMessage());
      }
    }
  }
}
