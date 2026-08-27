/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;

@OwnedBy(HarnessTeam.IDP)
public abstract class DataSourceLocationNoLoop extends DataSourceLocation {
  public Map<String, Object> fetchData(String accountIdentifier, Object entity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataFetchDTO> dataPointAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs, DataSourceConfig dataSourceConfig, boolean throughDelegate,
      Set<String> delegateSelectors) {
    ApiRequestDetails apiRequestDetails = fetchApiRequestDetails(dataSourceLocationEntity);
    String urlUnmodified = apiRequestDetails.getUrl();
    String requestBodyUnmodified = apiRequestDetails.getRequestBody();
    Map<String, String> replaceableHeadersUnmodified = new HashMap<>(replaceableHeaders);
    matchAndReplaceHeaders(apiRequestDetails.getHeaders(), replaceableHeaders);
    HttpConfig httpConfig = (HttpConfig) dataSourceConfig;
    httpConfig.getHeaders().forEach((k, v) -> {
      if (!apiRequestDetails.getHeaders().containsKey(k)) {
        apiRequestDetails.getHeaders().put(k, v);
      }
    });
    Map<String, Object> data = new HashMap<>();
    if (!validate(dataPointAndInputValues.get(0), data, replaceableHeaders, possibleReplaceableRequestBodyPairs,
            possibleReplaceableUrlPairs)
        || !expressionResolved(dataPointAndInputValues.get(0), entity, data)) {
      return data;
    }

    apiRequestDetails.setUrl(
        constructUrl(httpConfig.getTarget(), apiRequestDetails.getUrl(), possibleReplaceableUrlPairs,
            dataPointAndInputValues.get(0).getDataPoint(), dataPointAndInputValues.get(0).getInputValues()));

    String requestBody =
        constructRequestBody(apiRequestDetails, possibleReplaceableRequestBodyPairs, dataPointAndInputValues, entity);
    apiRequestDetails.setRequestBody(requestBody);
    DslClient dslClient = dslClientFactory.getClient(
        accountIdentifier, getHost(possibleReplaceableRequestBodyPairs), throughDelegate, apiRequestDetails.getUrl());
    try {
      Response response = getResponse(apiRequestDetails, dslClient, accountIdentifier, delegateSelectors, entity);
      Map<String, Object> ruleData = processResponse(response);
      data.put(dataPointAndInputValues.get(0).getRuleIdentifier(), ruleData);
    } finally {
      apiRequestDetails.setUrl(urlUnmodified);
      apiRequestDetails.setRequestBody(requestBodyUnmodified);
      ((HttpDataSourceLocationEntity) dataSourceLocationEntity).setApiRequestDetails(apiRequestDetails);
      replaceableHeaders.putAll(replaceableHeadersUnmodified);
    }
    return data;
  }

  protected String constructRequestBody(ApiRequestDetails apiRequestDetails,
      Map<String, String> possibleReplaceableRequestBodyPairs, List<DataFetchDTO> dataPointsAndInputValues,
      Object entity) {
    String requestBody = apiRequestDetails.getRequestBody();
    requestBody = replaceInputValuePlaceholdersIfAnyInRequestBody(requestBody,
        dataPointsAndInputValues.get(0).getDataPoint(), dataPointsAndInputValues.get(0).getInputValues(), entity);
    return replaceRequestBodyPlaceholdersIfAny(possibleReplaceableRequestBodyPairs, requestBody);
  }

  protected abstract String replaceInputValuePlaceholdersIfAnyInRequestBody(
      String requestBody, DataPointEntity dataPoint, List<InputValue> inputValues, Object entity);
}
