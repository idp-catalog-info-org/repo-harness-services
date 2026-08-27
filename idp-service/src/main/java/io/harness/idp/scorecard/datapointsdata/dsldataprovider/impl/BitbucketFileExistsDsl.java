/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_BRANCH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.base.DslDataProvider;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.utils.DslCommons;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.spec.server.idp.v1.model.ScmConfig;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BitbucketFileExistsDsl extends ScmDslCommon implements DslDataProvider {
  DslClientFactory dslClientFactory;
  private static final String LIST_FILES_URL =
      "/2.0/repositories/{REPOSITORY_OWNER}/{REPOSITORY_NAME}/src/{REPOSITORY_BRANCH}/"
      + "{REPOSITORY_SUB_FOLDER}{FILE_PATH_REPLACER}?pagelen=100";

  @Override
  public Map<String, Object> getDslData(String accountIdentifier, Object config) {
    Map<String, Object> dataPointData = new HashMap<>();
    if (!(config instanceof ScmConfig scmConfig)) {
      return dataPointData;
    }

    DslClient client = dslClientFactory.getClient(accountIdentifier, scmConfig.getRepoScm(),
        scmConfig.isThroughDelegate() != null ? scmConfig.isThroughDelegate() : false);
    Map<String, String> headers = getAuthHeaders(scmConfig.getToken());
    String baseUrl = "https://api." + scmConfig.getRepoScm();
    ApiRequestDetails apiRequestDetails =
        ApiRequestDetails.builder().method("GET").headers(headers).url(baseUrl + LIST_FILES_URL).build();
    apiRequestDetails.setUrl(DslCommons.replacePlaceholdersFromInputValues(
        apiRequestDetails.getUrl(), scmConfig.getDataSourceLocation().getDataPoints().get(0).getInputValues(), null));
    apiRequestDetails.setUrl(
        DslCommons.replacePlaceholdersFromSourceLocationAnnotation(apiRequestDetails.getUrl(), scmConfig));

    if (apiRequestDetails.getUrl().contains(REPOSITORY_BRANCH)) {
      apiRequestDetails.setUrl(
          apiRequestDetails.getUrl().replace(REPOSITORY_BRANCH, getBranch(accountIdentifier, scmConfig)));
    }

    log.info("Request for File exist API: method {}, url {}, requestBody {}", apiRequestDetails.getMethod(),
        apiRequestDetails.getUrl(), apiRequestDetails.getRequestBody());
    Response response = client.call(accountIdentifier, apiRequestDetails,
        new HashSet<>(
            scmConfig.getDelegateSelectors() != null ? scmConfig.getDelegateSelectors() : Collections.emptySet()),
        null);
    log.info("Status code from File exist API: {}", response.getStatus());
    log.debug("Response from File exist API: {}", response.getEntity().toString());
    return processResponse(response);
  }

  private Map<String, String> getAuthHeaders(String token) {
    return Map.of(AUTHORIZATION_HEADER, token);
  }

  private Map<String, Object> processResponse(Response response) {
    Map<String, Object> ruleData = new HashMap<>();
    if (response.getStatus() == 200) {
      ruleData.put(DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class));
    } else {
      DslCommons.throwExceptionForBitbucket(response);
    }
    return ruleData;
  }
}
