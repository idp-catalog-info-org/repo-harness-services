/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations.scm;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MESSAGE_KEY;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.ACCOUNT_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.BODY;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.ORG_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PROJECT_IDENTIFIER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_BRANCH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_SUB_FOLDER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;
import static io.harness.idp.scorecard.datasources.providers.DataSourceProviderV1.HOST;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.JacksonUtils;
import io.harness.idp.proxy.services.IdpAuthInterceptor;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceLocationInfo;
import io.harness.spec.server.idp.v1.model.InputValue;
import io.harness.spec.server.idp.v1.model.ScmConfig;
import io.harness.spec.server.idp.v1.model.ScmRequest;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

@OwnedBy(HarnessTeam.IDP)
public class ScmProxyThroughDsl extends ScmBaseDslNoLoop {
  @Inject IdpAuthInterceptor idpAuthInterceptor;
  @Inject @Named("base") private String base;
  private ScmConfig scmConfig;

  @Override
  protected void matchAndReplaceHeaders(Map<String, String> headers, Map<String, String> replaceableHeaders) {
    scmConfig = new ScmConfig();
    scmConfig.setToken(replaceableHeaders.get(AUTHORIZATION_HEADER));
    replaceableHeaders.putAll(idpAuthInterceptor.getAuthHeaders());
    headers.forEach((k, v) -> {
      if (replaceableHeaders.containsKey(k)) {
        headers.put(k, replaceableHeaders.get(k));
      }
    });
  }

  @Override
  protected String constructUrl(String baseUrl, String url, Map<String, String> replaceableUrls,
      DataPointEntity dataPoint, List<InputValue> inputValues) {
    replaceableUrls.put(HOST, base);
    return replaceUrlPlaceholdersIfAny(url, replaceableUrls);
  }

  @Override
  protected String constructRequestBody(ApiRequestDetails apiRequestDetails,
      Map<String, String> possibleReplaceableRequestBodyPairs, List<DataFetchDTO> dataFetchDTOS, Object entity) {
    List<DataPointInputValues> dataPoints = new ArrayList<>();
    for (DataFetchDTO dataFetchDTO : dataFetchDTOS) {
      DataPointInputValues dataPointInputValues = new DataPointInputValues();
      dataPointInputValues.setDataSourceIdentifier(dataFetchDTO.getDataPoint().getDataSourceIdentifier());
      dataPointInputValues.setDataPointIdentifier(dataFetchDTO.getDataPoint().getIdentifier());
      dataPointInputValues.setInputValues(dataFetchDTO.getInputValues());
      dataPoints.add(dataPointInputValues);
    }
    DataSourceLocationInfo dataSourceLocationInfo = new DataSourceLocationInfo();
    dataSourceLocationInfo.setDataPoints(dataPoints);
    scmConfig.setDataSourceLocation(dataSourceLocationInfo);

    scmConfig.setRepoScm(possibleReplaceableRequestBodyPairs.get(REPO_SCM));
    scmConfig.setRepoOwner(possibleReplaceableRequestBodyPairs.get(REPOSITORY_OWNER));
    scmConfig.setRepoName(possibleReplaceableRequestBodyPairs.get(REPOSITORY_NAME));
    scmConfig.setRepoBranch(possibleReplaceableRequestBodyPairs.get(REPOSITORY_BRANCH));

    scmConfig.setAccountIdentifier(possibleReplaceableRequestBodyPairs.get(ACCOUNT_IDENTIFIER));
    scmConfig.setOrgIdentifier(possibleReplaceableRequestBodyPairs.get(ORG_IDENTIFIER));
    scmConfig.setProjectIdentifier(possibleReplaceableRequestBodyPairs.get(PROJECT_IDENTIFIER));

    if (possibleReplaceableRequestBodyPairs.containsKey(REPOSITORY_SUB_FOLDER)) {
      scmConfig.setRepoSubFolder(possibleReplaceableRequestBodyPairs.get(REPOSITORY_SUB_FOLDER));
    }

    ScmRequest request = new ScmRequest();
    request.setRequest(scmConfig);
    return apiRequestDetails.getRequestBody().replace(BODY, JacksonUtils.write(request));
  }

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestBody(
      String requestBody, DataPointEntity dataPoint, List<InputValue> inputValues, Object entity) {
    return requestBody;
  }

  @Override
  protected String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues) {
    return url;
  }

  @Override
  protected String getHost(Map<String, String> data) {
    return null;
  }

  @Override
  protected Map<String, Object> processResponse(Response response) {
    Map<String, Object> ruleData = new HashMap<>();
    if (response.getStatus() == 200) {
      ruleData.put(DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class));
    } else {
      if (response.getEntity() instanceof String) {
        ruleData.put(ERROR_MESSAGE_KEY,
            GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class).get(MESSAGE_KEY));
      } else {
        ruleData.put(ERROR_MESSAGE_KEY, ((ResponseMessage) response.getEntity()).getMessage());
      }
    }
    return ruleData;
  }
}
