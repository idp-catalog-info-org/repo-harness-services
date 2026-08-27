/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import static io.harness.idp.common.Constants.ERRORS;
import static io.harness.idp.common.Constants.MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.GsonUtils;
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
public class GitlabContentDsl extends ScmContentDsl implements DslDataProvider {
  private static final String LIST_FILES_REQUEST_BODY =
      "{\"query\":\"{ project(fullPath: \\\"{REPOSITORY_OWNER}/{REPOSITORY_NAME}\\\") { repository { tree(ref: "
      + "\\\"{REPOSITORY_BRANCH}\\\", path: \\\"{REPOSITORY_SUB_FOLDER}{FILE_PATH_REPLACER}\\\") { blobs { nodes { "
      + "name path } } } } } }\"}";
  private static final String FILE_CONTENTS_REQUEST_BODY =
      "{\"query\":\"{ project(fullPath: \\\"{REPOSITORY_OWNER}/{REPOSITORY_NAME}\\\") { repository { blobs(ref: "
      + "\\\"{REPOSITORY_BRANCH}\\\", paths: \\\"{REPOSITORY_SUB_FOLDER}{FILE_PATH_REPLACER}\\\") { nodes { "
      + "rawTextBlob } } } } }\"}";
  private static final List<String> REQUEST_BODIES = List.of(LIST_FILES_REQUEST_BODY, FILE_CONTENTS_REQUEST_BODY);

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, Object config) {
    Map<String, Object> dataPointData = new HashMap<>();
    if (!(config instanceof ScmConfig)) {
      return dataPointData;
    }
    ScmConfig scmConfig = (ScmConfig) config;

    String url = "https://" + scmConfig.getRepoScm() + "/api/graphql";
    return fetchData(scmConfig, accountIdentifier, url);
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

  @Override
  public String getFileName(Response response, ScmConfig scmConfig) {
    if (response.getStatus() == 200) {
      Map<String, Object> data = GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class);
      List<Map<String, Object>> nodes = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "nodes");
      return iterateAndFetchMatchingFile(nodes, scmConfig, "name");
    }
    return null;
  }

  @Override
  public Map<String, Object> processResponse(Response response) {
    if (response.getStatus() == 200) {
      return GsonUtils.convertJsonStringToObject((String) response.getEntity(), Map.class);
    } else if (response.getStatus() == 500) {
      throw new InvalidRequestException(((ResponseMessage) response.getEntity()).getMessage());
    } else {
      List<Map<String, Object>> errors =
          (List<Map<String, Object>>) GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class)
              .get(ERRORS);
      throw new BadRequestException((String) errors.get(0).get(MESSAGE_KEY));
    }
  }
}
