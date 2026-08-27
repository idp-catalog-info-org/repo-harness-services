/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.common.Constants.GITHUB_INSTALLATION_ID;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PULL_REQUEST_MEAN_TIME_TO_MERGE;
import static io.harness.idp.scorecard.datasources.providers.scm.GithubProvider.GITHUB_EXPRESSION_KEY;
import static io.harness.idp.scorecard.datasources.providers.scm.ScmBaseProvider.SOURCE_LOCATION_ANNOTATION;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cistatus.service.GithubService;
import io.harness.connector.ConnectorResourceClient;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.service.git.GithubIntegrationOpsImpl;
import io.harness.idp.proxy.envvariable.ProxyEnvVariableServiceWrapper;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.parser.scm.github.GithubWorkflowsCountParser;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.github.GithubWorkflowsCountDsl;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.entity.HttpDataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class GithubProviderTest extends CategoryTest {
  AutoCloseable openMocks;
  GithubProvider githubProvider;
  @Mock DataPointService dataPointService;
  @Mock DataSourceLocationFactory dataSourceLocationFactory;
  @Mock DataSourceLocationRepository dataSourceLocationRepository;
  @Mock DataPointParserFactory dataPointParserFactory;
  @Mock ConfigReader configReader;
  @Mock DataSourceRepository dataSourceRepository;
  @Mock GithubService githubService;
  @Mock ProxyEnvVariableServiceWrapper proxyEnvVariableServiceWrapper;
  @Mock GithubWorkflowsCountDsl githubWorkflowsCountDsl;
  @Mock GithubWorkflowsCountParser githubWorkflowsCountParser;
  @Mock ConnectorResourceClient connectorResourceClient;
  @Mock GithubIntegrationOpsImpl githubIntegrationOps;
  @Mock BackstageEnvVariableService backstageEnvVariableService;
  @Mock AccountClient accountClient;

  private static final String ACCOUNT_ID = "123";
  private static final String PROJECT_ID = "project1";
  private static final String ORG_ID = "org1";
  private static final String IDP_SERVICE_ENTITY_ID = "03bc314a-437b-4d15-b75b-b819179e7859";
  private static final String IDP_SERVICE_ENTITY_NAME = "idp-service";
  private static final String RULE_IDENTIFIER = "rule1";

  private static final String TARGET_URL_EXPRESSION_KEY = "appConfig.integrations.github.0.apiBaseUrl";
  private static final String TOKEN_EXPRESSION_KEY = "appConfig.integrations.github.0.token";
  private static final String GITHUB_APP_ID_EXPRESSION = "appConfig.integrations.github.0.apps.0.appId";
  private static final String GITHUB_APP_PRIVATE_KEY_EXPRESSION = "appConfig.integrations.github.0.apps.0.privateKey";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    githubProvider = new GithubProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, configReader, dataSourceRepository, githubService, proxyEnvVariableServiceWrapper,
        connectorResourceClient, githubIntegrationOps, backstageEnvVariableService, accountClient, false);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData() {
    MockedStatic<CGRestUtils> mockRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);
    List<Map<String, Object>> gitIntegrationConfigs = new ArrayList<>();
    Map<String, Object> gitIntegrationConfig = Map.of("host", "github.com");
    gitIntegrationConfigs.add(gitIntegrationConfig);
    when(configReader.getConfigValues(ACCOUNT_ID, null, GITHUB_EXPRESSION_KEY)).thenReturn(gitIntegrationConfigs);
    when(configReader.getConfigValues(ACCOUNT_ID, null, TARGET_URL_EXPRESSION_KEY))
        .thenReturn("https://api.github.com");
    when(configReader.getConfigValues(ACCOUNT_ID, null, TOKEN_EXPRESSION_KEY)).thenReturn("1234");

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put("dsl", List.of(getDataFetchDTO()));
    when(dataPointService.getDslDataPointsInfo(any(), any(), anyList())).thenReturn(dataToFetchByDsl);

    when(dataSourceLocationFactory.getDataSourceLocation(any())).thenReturn(githubWorkflowsCountDsl);
    when(dataSourceLocationRepository.findByIdentifier(any()))
        .thenReturn(HttpDataSourceLocationEntity.builder().build());
    when(dataSourceRepository.findByAccountIdentifierInAndIdentifier(any(), any()))
        .thenReturn(Optional.of(getDataSourceEntity()));
    when(githubWorkflowsCountDsl.fetchData(
             any(), any(), any(), anyList(), anyMap(), anyMap(), anyMap(), any(), anyBoolean(), anySet()))
        .thenReturn(Map.of(GITHUB_IDENTIFIER, "dslResponse"));
    when(dataPointParserFactory.getParser(any(), any())).thenReturn(githubWorkflowsCountParser);
    when(githubWorkflowsCountParser.parseDataPoint(anyMap(), any())).thenReturn(Map.of(RULE_IDENTIFIER, 2));
    BackstageCatalogEntity entity = getBackstageCatalogEntity();
    Map<String, Map<String, Object>> data =
        githubProvider.fetchData(ACCOUNT_ID, entity, List.of(getDataFetchDTO()), null);
    Map<String, Object> dsl = data.get(GITHUB_IDENTIFIER);
    assertNotNull(dsl);
    assertEquals(2, dsl.get(RULE_IDENTIFIER));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchDataForGithubApp() {
    MockedStatic<CGRestUtils> mockRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);
    List<Map<String, Object>> gitIntegrationConfigs = new ArrayList<>();
    Map<String, Object> gitIntegrationConfig = Map.of("host", "github.com");
    gitIntegrationConfigs.add(gitIntegrationConfig);
    when(configReader.getConfigValues(ACCOUNT_ID, null, GITHUB_EXPRESSION_KEY)).thenReturn(gitIntegrationConfigs);
    when(configReader.getConfigValues(ACCOUNT_ID, null, TARGET_URL_EXPRESSION_KEY))
        .thenReturn("https://api.github.com");
    when(configReader.getConfigValues(ACCOUNT_ID, null, TOKEN_EXPRESSION_KEY)).thenReturn(null);
    when(configReader.getConfigValues(ACCOUNT_ID, null, GITHUB_APP_ID_EXPRESSION)).thenReturn("appId");
    when(configReader.getDecryptedValue(ACCOUNT_ID, GITHUB_INSTALLATION_ID)).thenReturn("installationId");
    when(configReader.getConfigValues(ACCOUNT_ID, null, GITHUB_APP_PRIVATE_KEY_EXPRESSION)).thenReturn("privateKey");
    when(githubService.getToken(any())).thenReturn("token");

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put("dsl", List.of(getDataFetchDTO()));
    when(dataPointService.getDslDataPointsInfo(any(), any(), anyList())).thenReturn(dataToFetchByDsl);

    when(dataSourceLocationFactory.getDataSourceLocation(any())).thenReturn(githubWorkflowsCountDsl);
    when(dataSourceLocationRepository.findByIdentifier(any()))
        .thenReturn(HttpDataSourceLocationEntity.builder().build());
    when(dataSourceRepository.findByAccountIdentifierInAndIdentifier(any(), any()))
        .thenReturn(Optional.of(getDataSourceEntity()));
    when(githubWorkflowsCountDsl.fetchData(
             any(), any(), any(), anyList(), anyMap(), anyMap(), anyMap(), any(), anyBoolean(), anySet()))
        .thenReturn(Map.of(GITHUB_IDENTIFIER, "dslResponse"));
    when(dataPointParserFactory.getParser(any(), any())).thenReturn(githubWorkflowsCountParser);
    when(githubWorkflowsCountParser.parseDataPoint(anyMap(), any())).thenReturn(Map.of(RULE_IDENTIFIER, 2));
    BackstageCatalogEntity entity = getBackstageCatalogEntity();
    Map<String, Map<String, Object>> data =
        githubProvider.fetchData(ACCOUNT_ID, entity, List.of(getDataFetchDTO()), null);
    Map<String, Object> dsl = data.get(GITHUB_IDENTIFIER);
    assertNotNull(dsl);
    assertEquals(2, dsl.get(RULE_IDENTIFIER));
  }

  private BackstageCatalogEntity getBackstageCatalogEntity() {
    return BackstageCatalogComponentEntity.builder()
        .entityUid(IDP_SERVICE_ENTITY_ID)
        .kind(BackstageCatalogEntityTypes.COMPONENT.kind)
        .metadata(Map.of(MetadataFieldConstants.NAME, IDP_SERVICE_ENTITY_NAME))
        .spec(BackstageCatalogComponentEntity.Spec.builder()
                  .domain(ORG_ID)
                  .system(Collections.singletonList(PROJECT_ID))
                  .type("service")
                  .owner("team-a")
                  .build())
        .metadata(Map.of(MetadataFieldConstants.NAME, IDP_SERVICE_ENTITY_NAME, MetadataFieldConstants.ANNOTATIONS,
            Map.of(SOURCE_LOCATION_ANNOTATION, "url:https://github.com/harness/harness-core/tree/develop")))
        .build();
  }

  private DataFetchDTO getDataFetchDTO() {
    DataPointEntity dataPointEntity = DataPointEntity.builder()
                                          .dataSourceIdentifier(GITHUB_IDENTIFIER)
                                          .identifier(PULL_REQUEST_MEAN_TIME_TO_MERGE)
                                          .build();
    return DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER).dataPoint(dataPointEntity).build();
  }

  private DataSourceEntity getDataSourceEntity() {
    HttpConfig httpConfig = new HttpConfig();
    httpConfig.setTarget("{API_BASE_URL}");
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "{BEARER_AUTH}");
    httpConfig.setHeaders(headers);
    return HttpDataSourceEntity.builder()
        .name(GITHUB_IDENTIFIER)
        .identifier(GITHUB_IDENTIFIER)
        .description(GITHUB_IDENTIFIER)
        .httpConfig(httpConfig)
        .build();
  }
}
