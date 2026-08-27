/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service.locations.jira;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.JIRA_IDENTIFIER;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.JQL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.JIRA_ISSUES_COUNT;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.PROJECT_COMPONENT_REPLACER;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DirectDslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.jira.JiraIssuesCountDsl;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class JiraIssuesCountDslTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks JiraIssuesCountDsl jiraIssuesCountDsl;
  @Mock DslClientFactory dslClientFactory;
  @Mock DirectDslClient directDslClient;
  private static final String ACCOUNT_ID = "123";
  private static final String RULE_IDENTIFIER = "rule1";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData() {
    Response response = Response.ok().entity("{\"data\": \"Success Response\"}").build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = jiraIssuesCountDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO(true)), replaceableHeaders(), possibleReplaceableRequestBody(), new HashMap<>(),
        getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(DSL_RESPONSE));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForInvalidExpression() {
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
    Response response = Response.status(500).entity(ResponseMessage.builder().message("Network Error").build()).build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = jiraIssuesCountDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO(false)), replaceableHeaders(), possibleReplaceableRequestBody(), new HashMap<>(),
        getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForInvalidToken() {
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
    Response response = Response.status(401).entity("{\"errorMessages\":\"Invalid token\"}").build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = jiraIssuesCountDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO(true)), replaceableHeaders(), possibleReplaceableRequestBody(), new HashMap<>(),
        getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(ERROR_MESSAGE_KEY));
  }

  private DataSourceLocationEntity getDataSourceLocationEntity() {
    String requestBody = "{\"jql\" : \"project = {PROJECT_COMPONENT_REPLACER} AND {JQL_EXPRESSION}\",\n    \"fields\": "
        + "[\"created\", \"resolutiondate\"],\n    \"maxResults\": 100}";
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    return HttpDataSourceLocationEntity.builder()
        .apiRequestDetails(ApiRequestDetails.builder().url("url").requestBody(requestBody).headers(headers).build())
        .build();
  }

  private DataFetchDTO getDataFetchDTO(boolean isValid) {
    DataPointEntity dataPointEntity =
        DataPointEntity.builder().dataSourceIdentifier(JIRA_IDENTIFIER).identifier(JIRA_ISSUES_COUNT).build();
    List<InputValue> inputValues = new ArrayList<>();
    InputValue inputValue = new InputValue();
    inputValue.setKey(JQL);
    inputValue.setValue(isValid ? "valid JQL" : "invalid JQL");
    inputValues.add(inputValue);
    return DataFetchDTO.builder()
        .ruleIdentifier(RULE_IDENTIFIER)
        .dataPoint(dataPointEntity)
        .inputValues(inputValues)
        .build();
  }

  private Map<String, String> possibleReplaceableRequestBody() {
    Map<String, String> pairs = new HashMap<>();
    pairs.put(PROJECT_COMPONENT_REPLACER, "IDP");
    pairs.put(API_BASE_URL, "https://harness.atlassian.net");
    return pairs;
  }

  private Map<String, String> replaceableHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION_HEADER, "token");
    return headers;
  }

  private DataSourceConfig getDataSourceConfig() {
    HttpConfig httpConfig = new HttpConfig();
    httpConfig.setTarget("{API_BASE_URL}");
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION_HEADER, "{BEARER_AUTH}");
    httpConfig.setHeaders(headers);
    return httpConfig;
  }
}
