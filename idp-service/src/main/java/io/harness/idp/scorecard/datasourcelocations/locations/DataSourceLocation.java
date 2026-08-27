/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import static io.harness.idp.common.CommonUtils.removeLeadingSlash;
import static io.harness.idp.common.CommonUtils.removeTrailingSlash;
import static io.harness.idp.common.Constants.CATALOG_IDENTIFIER;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.EXPRESSION_PATTERN;
import static io.harness.idp.common.Constants.MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_EXPRESSION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.expression.common.ExpressionMode;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.common.GsonUtils;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.spec.server.idp.v1.model.InputValue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public abstract class DataSourceLocation {
  @Inject DslClientFactory dslClientFactory;
  private static final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public abstract Map<String, Object> fetchData(String accountIdentifier, Object entity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataFetchDTO> dataPointAndInputValues,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs, DataSourceConfig dataSourceConfig, boolean throughDelegate,
      Set<String> delegateSelectors);

  protected ApiRequestDetails fetchApiRequestDetails(DataSourceLocationEntity dataSourceLocationEntity) {
    return ((HttpDataSourceLocationEntity) dataSourceLocationEntity).getApiRequestDetails();
  }

  protected void matchAndReplaceHeaders(Map<String, String> headers, Map<String, String> replaceableHeaders) {
    headers.forEach((k, v) -> {
      if (replaceableHeaders.containsKey(k)) {
        headers.put(k, replaceableHeaders.get(k));
      }
    });
  }

  protected String constructUrl(String baseUrl, String url, Map<String, String> replaceableUrls,
      DataPointEntity dataPoint, List<InputValue> inputValues) {
    String replacedUrl = replaceUrlPlaceholdersIfAny(
        String.format("%s/%s", removeTrailingSlash(baseUrl), removeLeadingSlash(url)), replaceableUrls);
    return replaceInputValuePlaceholdersIfAnyInRequestUrl(replacedUrl, dataPoint, inputValues);
  }

  protected String replaceUrlPlaceholdersIfAny(String url, Map<String, String> replaceableUrls) {
    for (Map.Entry<String, String> entry : replaceableUrls.entrySet()) {
      if (entry.getValue() != null) {
        url = url.replace(entry.getKey(), entry.getValue());
      }
    }
    return url;
  }

  protected abstract String replaceInputValuePlaceholdersIfAnyInRequestUrl(
      String url, DataPointEntity dataPoint, List<InputValue> inputValues);

  protected abstract boolean validate(DataFetchDTO dataFetchDTO, Map<String, Object> data,
      Map<String, String> replaceableHeaders, Map<String, String> possibleReplaceableRequestBodyPairs,
      Map<String, String> possibleReplaceableUrlPairs);

  protected abstract String constructRequestBody(ApiRequestDetails apiRequestDetails,
      Map<String, String> possibleReplaceableRequestBodyPairs, List<DataFetchDTO> dataPointsAndInputValues,
      Object entity);

  protected String replaceRequestBodyPlaceholdersIfAny(
      Map<String, String> possibleReplaceableRequestBodyPairs, String requestBody) {
    if (requestBody != null) {
      for (Map.Entry<String, String> entry : possibleReplaceableRequestBodyPairs.entrySet()) {
        requestBody = requestBody.replace(entry.getKey(), entry.getValue());
      }
    }
    return requestBody;
  }

  protected abstract String getHost(Map<String, String> data);

  protected Response getResponse(ApiRequestDetails apiRequestDetails, DslClient dslClient, String accountIdentifier,
      Set<String> delegateSelectors, Object entity) {
    return dslClient.call(accountIdentifier, apiRequestDetails, delegateSelectors, entity);
  }

  protected Map<String, Object> processResponse(Response response) {
    Map<String, Object> ruleData = new HashMap<>();
    if (response.getStatus() == 200) {
      ruleData.put(DSL_RESPONSE, GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class));
    } else if (response.getStatus() == 500) {
      ruleData.put(ERROR_MESSAGE_KEY, ((ResponseMessage) response.getEntity()).getMessage());
    } else {
      ruleData.put(ERROR_MESSAGE_KEY,
          GsonUtils.convertJsonStringToObject(response.getEntity().toString(), Map.class).get(MESSAGE_KEY));
    }
    return ruleData;
  }

  protected boolean expressionResolved(DataFetchDTO dataFetchDTO, Object entity, Map<String, Object> data) {
    for (InputValue inputValue : dataFetchDTO.getInputValues()) {
      String value = inputValue.getValue();
      Matcher matcher = EXPRESSION_PATTERN.matcher(value);
      while (matcher.find()) {
        String expression = matcher.group(1);
        String fullExpression = CATALOG_IDENTIFIER + DOT_SEPARATOR + expression;
        Object resolvedValue = null;
        Map<String, Object> catalog;
        if (entity instanceof CatalogEntity) {
          catalog = ((CatalogEntity) entity).getDecoratedEntityMap();
        } else {
          catalog = mapper.convertValue(entity, new TypeReference<>() {});
        }
        IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(Map.of(CATALOG_IDENTIFIER, catalog));
        try {
          resolvedValue = evaluator.evaluateExpression(fullExpression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
          if (resolvedValue != null) {
            value = value.replace(matcher.group(0), (String) resolvedValue);
            continue;
          }
        } catch (Exception e) {
          log.error("Expression evaluation failed for expression {}, input {}", expression, inputValue.getKey(), e);
        }
        if (resolvedValue == null) {
          log.info("Could not find the required data by evaluating expression for - {}, input {}", expression,
              inputValue.getKey());
        }
        data.put(
            dataFetchDTO.getRuleIdentifier(), Map.of(ERROR_MESSAGE_KEY, String.format(INVALID_EXPRESSION, expression)));
        return false;
      }
      inputValue.setValue(value);
    }
    return true;
  }
}
