/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service.locations.scm.github;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_CONNECTOR_CONFIGURATION;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_DATA_SOURCE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PULL_REQUEST_MEAN_TIME_TO_MERGE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.SOURCE_LOCATION_ANNOTATION_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.BRANCH_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertEquals;
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
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubMeanTimeToMergePRDsl;
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
public class GithubMeanTimeToMergePRDslTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks GithubMeanTimeToMergePRDsl githubMeanTimeToMergePRDsl;
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
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
    Response response = Response.ok().entity("{\"data\": \"Success Response\"}").build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = githubMeanTimeToMergePRDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO("develop")), replaceableHeaders(), possibleReplaceableRequestBody(true),
        possibleReplaceableUrlBody(true), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(DSL_RESPONSE));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForDefaultBranch() {
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
    Response response = Response.status(500).entity(ResponseMessage.builder().message("Network Error").build()).build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = githubMeanTimeToMergePRDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO("refs/")), replaceableHeaders(), possibleReplaceableRequestBody(true),
        possibleReplaceableUrlBody(true), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForInvalidRepoName() {
    Map<String, Object> data = githubMeanTimeToMergePRDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO("main")), replaceableHeaders(), possibleReplaceableRequestBody(false), new HashMap<>(),
        getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertEquals(SOURCE_LOCATION_ANNOTATION_ERROR, ruleData.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForInvalidDataSource() {
    Map<String, Object> data = githubMeanTimeToMergePRDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO("main")), replaceableHeaders(), possibleReplaceableRequestBody(true),
        possibleReplaceableUrlBody(false), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertEquals(INVALID_DATA_SOURCE, ruleData.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForInvalidHeader() {
    Map<String, Object> data = githubMeanTimeToMergePRDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO("main")), new HashMap<>(), possibleReplaceableRequestBody(true),
        possibleReplaceableUrlBody(true), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertEquals(INVALID_CONNECTOR_CONFIGURATION, ruleData.get(ERROR_MESSAGE_KEY));
  }

  private DataSourceLocationEntity getDataSourceLocationEntity() {
    String requestBody =
        "{\"query\":\"query {\\n    repository(owner: \\\"{REPOSITORY_OWNER}\\\", name: \\\"{REPOSITORY_NAME}\\\") "
        + "{\\n    pullRequests(states: MERGED, last: 100{REPOSITORY_BRANCH_NAME_REPLACER}) {\\n      edges {\\n       "
        + " node {\\n          number\\n          createdAt\\n          mergedAt\\n        }\\n      }\\n    }\\n  "
        + "}\\n}\",\"variables\":{}}";
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    return HttpDataSourceLocationEntity.builder()
        .apiRequestDetails(ApiRequestDetails.builder().url("url").requestBody(requestBody).headers(headers).build())
        .build();
  }

  private DataFetchDTO getDataFetchDTO(String branch) {
    DataPointEntity dataPointEntity = DataPointEntity.builder()
                                          .dataSourceIdentifier(GITHUB_IDENTIFIER)
                                          .identifier(PULL_REQUEST_MEAN_TIME_TO_MERGE)
                                          .build();
    List<InputValue> inputValues = new ArrayList<>();
    InputValue inputValue = new InputValue();
    inputValue.setKey(BRANCH_NAME);
    inputValue.setValue(branch);
    inputValues.add(inputValue);
    return DataFetchDTO.builder()
        .ruleIdentifier(RULE_IDENTIFIER)
        .dataPoint(dataPointEntity)
        .inputValues(inputValues)
        .build();
  }

  private Map<String, String> possibleReplaceableRequestBody(boolean isValid) {
    Map<String, String> pairs = new HashMap<>();
    pairs.put(REPO_SCM, "github.com");
    pairs.put(REPOSITORY_OWNER, "harness");
    if (isValid) {
      pairs.put(REPOSITORY_NAME, "harness-core");
    }
    return pairs;
  }

  private Map<String, String> possibleReplaceableUrlBody(boolean isValid) {
    Map<String, String> pairs = new HashMap<>();
    pairs.put(REPO_SCM, "github.com");
    pairs.put(API_BASE_URL, isValid ? "api.github.com" : "bitbucket");
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
