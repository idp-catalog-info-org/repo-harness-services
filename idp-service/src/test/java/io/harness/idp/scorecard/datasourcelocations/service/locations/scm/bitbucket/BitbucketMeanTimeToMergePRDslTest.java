/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service.locations.scm.bitbucket;

import static io.harness.idp.common.Constants.BITBUCKET_IDENTIFIER;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PULL_REQUEST_MEAN_TIME_TO_MERGE;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.BRANCH_NAME;
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
import io.harness.idp.scorecard.datasourcelocations.locations.scm.bitbucket.BitbucketMeanTimeToMergePRDsl;
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

@OwnedBy(HarnessTeam.IDP)
public class BitbucketMeanTimeToMergePRDslTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks BitbucketMeanTimeToMergePRDsl bitbucketMeanTimeToMergePRDsl;
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
  public void testFetchDataForDefaultBranch() {
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
    Response response = Response.ok().entity("{\"data\": \"Success Response\"}").build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = bitbucketMeanTimeToMergePRDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO("refs/")), replaceableHeaders(), possibleReplaceableRequestBody(true), new HashMap<>(),
        getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(DSL_RESPONSE));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData() {
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
    Response response = Response.status(500).entity(ResponseMessage.builder().message("Network Error").build()).build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = bitbucketMeanTimeToMergePRDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO("develop")), replaceableHeaders(), possibleReplaceableRequestBody(false),
        new HashMap<>(), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForInvalidToken() {
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
    Response response = Response.status(401).entity("{\"error\":{\"message\":\"Invalid token\"}}").build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = bitbucketMeanTimeToMergePRDsl.fetchData(ACCOUNT_ID, null, getDataSourceLocationEntity(),
        List.of(getDataFetchDTO("develop")), replaceableHeaders(), possibleReplaceableRequestBody(true),
        new HashMap<>(), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(ERROR_MESSAGE_KEY));
  }

  private DataSourceLocationEntity getDataSourceLocationEntity() {
    String url = "/2.0/repositories/{REPOSITORY_OWNER}/{REPOSITORY_NAME}/pullrequests?q=state=\"MERGED\"";
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    return HttpDataSourceLocationEntity.builder()
        .apiRequestDetails(ApiRequestDetails.builder().url(url).headers(headers).build())
        .build();
  }

  private DataFetchDTO getDataFetchDTO(String branch) {
    DataPointEntity dataPointEntity = DataPointEntity.builder()
                                          .dataSourceIdentifier(BITBUCKET_IDENTIFIER)
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
    pairs.put(REPO_SCM, "bitbucket.org");
    pairs.put(REPOSITORY_OWNER, "harness");
    if (isValid) {
      pairs.put(REPOSITORY_NAME, "harness-core");
    }
    return pairs;
  }

  private Map<String, String> replaceableHeaders() {
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION_HEADER, "token");
    return headers;
  }

  private DataSourceConfig getDataSourceConfig() {
    HttpConfig httpConfig = new HttpConfig();
    httpConfig.setTarget("https://api.bitbucket.org");
    Map<String, String> headers = new HashMap<>();
    headers.put(AUTHORIZATION_HEADER, "{BASIC_AUTH}");
    httpConfig.setHeaders(headers);
    return httpConfig;
  }
}
