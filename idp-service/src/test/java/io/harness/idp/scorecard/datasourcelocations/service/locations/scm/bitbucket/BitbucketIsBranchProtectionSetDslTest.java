/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service.locations.scm.bitbucket;

import static io.harness.idp.common.Constants.BITBUCKET_IDENTIFIER;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.IS_BRANCH_PROTECTED;
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
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.idp.scorecard.common.beans.DataSourceConfig;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DirectDslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.bitbucket.BitbucketIsBranchProtectionSetDsl;
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
public class BitbucketIsBranchProtectionSetDslTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks BitbucketIsBranchProtectionSetDsl bitbucketIsBranchProtectionSetDsl;
  @Mock DslClientFactory dslClientFactory;
  @Mock DirectDslClient directDslClient;
  @Mock ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;
  private static final String ACCOUNT_ID = "123";
  private static final String RULE_IDENTIFIER = "rule1";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    when(dslClientFactory.getClient(any(), any(), anyBoolean(), any())).thenReturn(directDslClient);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData() {
    Response response = Response.ok().entity("{\"data\": \"Success Response\"}").build();
    when(directDslClient.call(any(), any(), anySet(), any())).thenReturn(response);
    Map<String, Object> data = bitbucketIsBranchProtectionSetDsl.fetchData(ACCOUNT_ID, null,
        getDataSourceLocationEntity(), List.of(getDataFetchDTO()), replaceableHeaders(),
        possibleReplaceableRequestBody(), new HashMap<>(), getDataSourceConfig(), false, Set.of());
    assertNotNull(data);
    Map<String, Object> ruleData = (Map<String, Object>) data.get(RULE_IDENTIFIER);
    assertNotNull(ruleData.get(DSL_RESPONSE));
  }

  private DataSourceLocationEntity getDataSourceLocationEntity() {
    String url =
        "/2.0/repositories/{REPOSITORY_OWNER}/{REPOSITORY_NAME}/branch-restrictions?pattern={BRANCH_NAME_REPLACER}";
    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    return HttpDataSourceLocationEntity.builder()
        .apiRequestDetails(ApiRequestDetails.builder().url(url).headers(headers).build())
        .build();
  }

  private DataFetchDTO getDataFetchDTO() {
    DataPointEntity dataPointEntity =
        DataPointEntity.builder().dataSourceIdentifier(BITBUCKET_IDENTIFIER).identifier(IS_BRANCH_PROTECTED).build();
    List<InputValue> inputValues = new ArrayList<>();
    InputValue inputValue = new InputValue();
    inputValue.setKey(BRANCH_NAME);
    inputValue.setValue("develop");
    inputValues.add(inputValue);
    return DataFetchDTO.builder()
        .ruleIdentifier(RULE_IDENTIFIER)
        .dataPoint(dataPointEntity)
        .inputValues(inputValues)
        .build();
  }

  private Map<String, String> possibleReplaceableRequestBody() {
    Map<String, String> pairs = new HashMap<>();
    pairs.put(REPO_SCM, "bitbucket.org");
    pairs.put(REPOSITORY_OWNER, "harness");
    pairs.put(REPOSITORY_NAME, "harness-core");
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
