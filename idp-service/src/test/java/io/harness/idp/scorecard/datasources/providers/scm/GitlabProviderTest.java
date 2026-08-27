/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.idp.common.Constants.GITLAB_IDENTIFIER;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.PULL_REQUEST_MEAN_TIME_TO_MERGE;
import static io.harness.idp.scorecard.datasources.providers.scm.GitlabProvider.GITLAB_EXPRESSION_KEY;
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
import io.harness.connector.ConnectorResourceClient;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.integrations.service.git.GitlabIntegrationOpsImpl;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.parser.scm.gitlab.GitlabMeanTimeToMergeParser;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.gitlab.GitlabMeanTimeToMergePRDsl;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.entity.HttpDataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.datasources.utils.ConfigReader;
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
public class GitlabProviderTest extends CategoryTest {
  AutoCloseable openMocks;
  GitlabProvider gitlabProvider;
  @Mock DataPointService dataPointService;
  @Mock DataSourceLocationFactory dataSourceLocationFactory;
  @Mock DataSourceLocationRepository dataSourceLocationRepository;
  @Mock DataPointParserFactory dataPointParserFactory;
  @Mock ConfigReader configReader;
  @Mock DataSourceRepository dataSourceRepository;
  @Mock GitlabMeanTimeToMergePRDsl gitlabMeanTimeToMergePRDsl;
  @Mock GitlabMeanTimeToMergeParser gitlabMeanTimeToMergeParser;
  @Mock ConnectorResourceClient connectorResourceClient;
  @Mock GitlabIntegrationOpsImpl gitlabIntegrationOps;
  @Mock BackstageEnvVariableService backstageEnvVariableService;
  @Mock AccountClient accountClient;

  private static final String ACCOUNT_ID = "123";
  private static final String PROJECT_ID = "project1";
  private static final String ORG_ID = "org1";
  private static final String IDP_SERVICE_ENTITY_ID = "03bc314a-437b-4d15-b75b-b819179e7859";
  private static final String IDP_SERVICE_ENTITY_NAME = "idp-service";
  public static final String RULE_IDENTIFIER = "rule1";

  private static final String HOST_EXPRESSION_KEY = "appConfig.integrations.gitlab.0.apiBaseUrl";
  private static final String TOKEN_EXPRESSION_KEY = "appConfig.integrations.gitlab.0.token";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    gitlabProvider = new GitlabProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, configReader, dataSourceRepository, connectorResourceClient, gitlabIntegrationOps,
        backstageEnvVariableService, accountClient, false);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData() {
    MockedStatic<CGRestUtils> mockRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(false);
    List<Map<String, Object>> gitIntegrationConfigs = new ArrayList<>();
    Map<String, Object> gitIntegrationConfig = Map.of("host", "gitlab.com");
    gitIntegrationConfigs.add(gitIntegrationConfig);
    when(configReader.getConfigValues(ACCOUNT_ID, null, GITLAB_EXPRESSION_KEY)).thenReturn(gitIntegrationConfigs);

    when(configReader.getConfigValues(ACCOUNT_ID, null, HOST_EXPRESSION_KEY)).thenReturn("gitlab.com");
    when(configReader.getConfigValues(ACCOUNT_ID, null, TOKEN_EXPRESSION_KEY)).thenReturn("token");

    Map<String, List<io.harness.idp.scorecard.scores.beans.DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put("dsl", List.of(getDataFetchDTO()));
    when(dataPointService.getDslDataPointsInfo(any(), any(), anyList())).thenReturn(dataToFetchByDsl);

    when(dataSourceLocationFactory.getDataSourceLocation(any())).thenReturn(gitlabMeanTimeToMergePRDsl);
    when(dataSourceLocationRepository.findByIdentifier(any()))
        .thenReturn(HttpDataSourceLocationEntity.builder().build());
    when(dataSourceRepository.findByAccountIdentifierInAndIdentifier(any(), any()))
        .thenReturn(Optional.of(getDataSourceEntity()));
    when(gitlabMeanTimeToMergePRDsl.fetchData(
             any(), any(), any(), anyList(), anyMap(), anyMap(), anyMap(), any(), anyBoolean(), anySet()))
        .thenReturn(Map.of(GITLAB_IDENTIFIER, "dslResponse"));
    when(dataPointParserFactory.getParser(any(), any())).thenReturn(gitlabMeanTimeToMergeParser);
    when(gitlabMeanTimeToMergeParser.parseDataPoint(anyMap(), any())).thenReturn(Map.of(RULE_IDENTIFIER, 2));
    BackstageCatalogEntity entity = getBackstageCatalogEntity();
    Map<String, Map<String, Object>> data =
        gitlabProvider.fetchData(ACCOUNT_ID, entity, List.of(getDataFetchDTO()), null);
    Map<String, Object> dsl = data.get(GITLAB_IDENTIFIER);
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
            Map.of(SOURCE_LOCATION_ANNOTATION,
                "url:https://gitlab.com/harness/harness-core/sub-group/repo/-/tree/develop/idp?ref_type=heads")))
        .build();
  }

  private io.harness.idp.scorecard.scores.beans.DataFetchDTO getDataFetchDTO() {
    DataPointEntity dataPointEntity = DataPointEntity.builder()
                                          .dataSourceIdentifier(GITLAB_IDENTIFIER)
                                          .identifier(PULL_REQUEST_MEAN_TIME_TO_MERGE)
                                          .build();
    return io.harness.idp.scorecard.scores.beans.DataFetchDTO.builder()
        .ruleIdentifier(RULE_IDENTIFIER)
        .dataPoint(dataPointEntity)
        .build();
  }

  private DataSourceEntity getDataSourceEntity() {
    HttpConfig httpConfig = new HttpConfig();
    httpConfig.setTarget("https://{API_BASE_URL}");
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "{BEARER_AUTH}");
    httpConfig.setHeaders(headers);
    return HttpDataSourceEntity.builder()
        .name(GITLAB_IDENTIFIER)
        .identifier(GITLAB_IDENTIFIER)
        .description(GITLAB_IDENTIFIER)
        .httpConfig(httpConfig)
        .build();
  }
}
