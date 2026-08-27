/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service.locations.scm.gitlab;

import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.GITLAB_IDENTIFIER;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_FILE_EXISTS;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.FILE_PATH;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.API_BASE_URL;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.AUTHORIZATION_HEADER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_NAME;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPOSITORY_OWNER;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.REPO_SCM;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DirectDslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.gitlab.GitlabFileExistsDsl;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitlabFileExistsDslTest {
  AutoCloseable openMocks;
  @InjectMocks GitlabFileExistsDsl gitlabFileExistsDsl;
  @Mock DslClientFactory dslClientFactory;
  @Mock DirectDslClient directDslClient;
  private static final String ACCOUNT_ID = "123";
  private static final String RULE_IDENTIFIER = "rule1";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData() {
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
    Response response = Response.ok().entity("{\"data\": \"Success Response\"}").build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = gitlabFileExistsDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO("README.md")), replaceableHeaders(), possibleReplaceableRequestBody(),
        possibleReplaceableUrlBody(), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(DSL_RESPONSE));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForSubFolder() {
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
    Response response = Response.ok().entity("{\"data\": \"Success Response\"}").build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = gitlabFileExistsDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO("idp/README.md")), replaceableHeaders(), possibleReplaceableRequestBody(),
        possibleReplaceableUrlBody(), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(DSL_RESPONSE));
  }

  private DataSourceLocationEntity getDataSourceLocationEntity() {
    String requestBody =
        "{\"query\":\"{\\n  project(fullPath: \\\"{REPOSITORY_OWNER}/{REPOSITORY_NAME}\\\") {\\n    repository {\\n    "
        + "  tree(ref: \\\"HEAD\\\", path: \\\"{FILE_PATH_REPLACER}\\\") {\\n        blobs {\\n          nodes {\\n    "
        + "        name\\n            path\\n          }\\n        }\\n      }\\n    }\\n  }\\n}\",\"variables\":{}}";
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    return HttpDataSourceLocationEntity.builder()
        .apiRequestDetails(ApiRequestDetails.builder().url("url").requestBody(requestBody).headers(headers).build())
        .build();
  }

  private DataFetchDTO getDataFetchDTO(String path) {
    DataPointEntity dataPointEntity =
        DataPointEntity.builder().dataSourceIdentifier(GITLAB_IDENTIFIER).identifier(IS_FILE_EXISTS).build();
    List<InputValue> inputValues = new ArrayList<>();
    InputValue inputValue = new InputValue();
    inputValue.setKey(FILE_PATH);
    inputValue.setValue(path);
    inputValues.add(inputValue);
    return DataFetchDTO.builder()
        .ruleIdentifier(RULE_IDENTIFIER)
        .dataPoint(dataPointEntity)
        .inputValues(inputValues)
        .build();
  }

  private Map<String, String> possibleReplaceableRequestBody() {
    Map<String, String> pairs = new HashMap<>();
    pairs.put(REPO_SCM, "gitlab.com");
    pairs.put(REPOSITORY_OWNER, "harness");
    pairs.put(REPOSITORY_NAME, "harness-core");
    return pairs;
  }

  private Map<String, String> possibleReplaceableUrlBody() {
    Map<String, String> pairs = new HashMap<>();
    pairs.put(API_BASE_URL, "api.gitlab.com");
    return pairs;
  }

  private Map<String, String> replaceableHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION_HEADER, "token");
    return headers;
  }

  private DataSourceConfig getDataSourceConfig() {
    HttpConfig httpConfig = new HttpConfig();
    httpConfig.setTarget("https://{API_BASE_URL}");
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION_HEADER, "{BEARER_AUTH}");
    httpConfig.setHeaders(headers);
    return httpConfig;
  }
}
