/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_FILE_NAME_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.common.GithubCommonUtils;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.spec.server.idp.v1.model.ScmConfig;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GithubContentDsl extends ScmContentDsl implements DslDataProvider {
  private static final String LIST_FILES_REQUEST_BODY =
      "{\"query\":\"{ repository(owner: \\\"{REPOSITORY_OWNER}\\\", name: \\\"{REPOSITORY_NAME}\\\") { "
      + "object(expression : \\\"{REPOSITORY_BRANCH}:{REPOSITORY_SUB_FOLDER}{FILE_PATH_REPLACER}\\\") { ... on Tree { "
      + "id entries { name path type } } } } }\"}";
  private static final String FILE_CONTENTS_REQUEST_BODY =
      "{\"query\": \"{ repository(owner: \\\"{REPOSITORY_OWNER}\\\", name: \\\"{REPOSITORY_NAME}\\\") { "
      + "object(expression: \\\"{REPOSITORY_BRANCH}:{REPOSITORY_SUB_FOLDER}{FILE_PATH_REPLACER}\\\") { ... on Blob { "
      + "text } } } }\"}";
  private static final List<String> REQUEST_BODIES = List.of(LIST_FILES_REQUEST_BODY, FILE_CONTENTS_REQUEST_BODY);
  @Override
  public Map<String, Object> getDslData(String accountIdentifier, Object config) {
    Map<String, Object> dataPointData = new HashMap<>();
    if (!(config instanceof ScmConfig)) {
      return dataPointData;
    }
    ScmConfig scmConfig = (ScmConfig) config;

    String url = GithubCommonUtils.constructGraphQLUrl(scmConfig.getRepoScm());
    return fetchData(scmConfig, accountIdentifier, url);
  }

  @Override
  public String getFileName(Response response, ScmConfig scmConfig) {
    if (response.getStatus() == 200) {
      Map<String, Object> data = GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class);
      if (CommonUtils.findObjectByName(data, "object") != null) {
        List<Map<String, Object>> entries = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "entries");
        return iterateAndFetchMatchingFile(entries, scmConfig, "name");
      } else {
        String errorMessage = GithubCommonUtils.fetchErrorMessageFromGraphQLResponse(data);
        if (!isEmpty(errorMessage)) {
          throw new BadRequestException(errorMessage);
        }
        throw new BadRequestException(INVALID_FILE_NAME_ERROR);
      }
    }
    return null;
  }

  @Override
  public ApiRequestDetails getApiRequestDetails(String baseUrl, Map<String, String> authHeaders, int index) {
    return ApiRequestDetails.builder()
        .method("POST")
        .requestBody(REQUEST_BODIES.get(index))
        .headers(authHeaders)
        .url(baseUrl)
        .build();
  }
}
