/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;
import static io.harness.idp.scorecard.datasources.providers.scm.ScmBaseProvider.SOURCE_LOCATION_ANNOTATION;
import static io.harness.rule.OwnerRule.AGNIVA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
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
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.scorecard.common.beans.HttpConfig;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.parser.scm.harnesscode.HarnessCodeFileExistsParser;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.entity.HttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactory;
import io.harness.idp.scorecard.datasourcelocations.locations.scm.harnesscode.HarnessCodeFileExistsDsl;
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
public class HarnessProviderTest extends CategoryTest {
  private static final String base = "base";
  private static final String ACCOUNT_ID = "123";
  private static final String IDP_SERVICE_ENTITY_ID = "03bc314a-437b-4d15-b75b-b819179e7859";
  private static final String IDP_SERVICE_ENTITY_NAME = "idp-service";
  private static final String PROJECT_ID = "project1";
  private static final String ORG_ID = "org1";
  private static final String RULE_IDENTIFIER = "rule1";
  private static final String EXTRACT_STRING_FROM_A_FILE_DATAPOINT = "extractStringFromAFile";
  AutoCloseable openMocks;
  @Mock DataPointService dataPointService;
  @Mock DataSourceLocationFactory dataSourceLocationFactory;
  @Mock DataSourceLocationRepository dataSourceLocationRepository;
  @Mock DataPointParserFactory dataPointParserFactory;
  @Mock HarnessCodeRepoConfig harnessCodeRepoConfig;
  @Mock HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  @Mock DataSourceRepository dataSourceRepository;
  @Mock ConfigReader configReader;
  @Mock HarnessCodeFileExistsDsl harnessCodeFileExistsDsl;
  @Mock HarnessCodeFileExistsParser harnessCodeFileExistsParser;
  HarnessProvider harnessProvider;
  @Mock AccountClient accountClient;
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    harnessProvider = new HarnessProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, harnessCodeRepoConfig, harnessCodeConnectorUtils, base, dataSourceRepository,
        accountClient, false);
  }
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testFetchData() {
    MockedStatic<CGRestUtils> mockRestUtils = Mockito.mockStatic(CGRestUtils.class);
    mockRestUtils.when(() -> CGRestUtils.getResponse(any())).thenReturn(true);
    List<Map<String, Object>> harnessIntegrationConfigs = new ArrayList<>();
    Map<String, Object> harnessIntegrationConfig = Map.of("host", "harness0.harness.io");
    harnessIntegrationConfigs.add(harnessIntegrationConfig);
    when(configReader.getConfigValues(ACCOUNT_ID, null, "")).thenReturn("harness0.harness.io");
    Map<String, List<io.harness.idp.scorecard.scores.beans.DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put("dsl", List.of(getDataFetchDTO()));
    when(dataPointService.getDslDataPointsInfo(any(), any(), anyList())).thenReturn(dataToFetchByDsl);

    when(dataSourceLocationFactory.getDataSourceLocation(any())).thenReturn(harnessCodeFileExistsDsl);
    when(dataSourceLocationRepository.findByIdentifier(any()))
        .thenReturn(HttpDataSourceLocationEntity.builder().build());
    when(dataSourceRepository.findByAccountIdentifierInAndIdentifier(any(), any()))
        .thenReturn(Optional.of(getDataSourceEntity()));
    when(harnessCodeFileExistsDsl.fetchData(
             any(), any(), any(), anyList(), anyMap(), anyMap(), anyMap(), any(), anyBoolean(), anySet()))
        .thenReturn(Map.of(HARNESS_IDENTIFIER, "dslResponse"));
    when(dataPointParserFactory.getParser(any(), any())).thenReturn(harnessCodeFileExistsParser);
    when(harnessCodeFileExistsParser.parseDataPoint(anyMap(), any())).thenReturn(Map.of(RULE_IDENTIFIER, 2));
    Map<String, Map<String, Object>> data =
        harnessProvider.fetchData(ACCOUNT_ID, getBackstageCatalogEntity(), List.of(getDataFetchDTO()), null);
    Map<String, Object> dsl = data.get(HARNESS_IDENTIFIER);
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
                "url:https://harness0.harness.io/ng/account/l7B_kbSEQD2wjrM7PShm5w/module/code/orgs/PROD/projects/"
                    + "Harness_Commons/repos/harness-core")))
        .build();
  }

  private DataFetchDTO getDataFetchDTO() {
    DataPointEntity dataPointEntity = DataPointEntity.builder()
                                          .dataSourceIdentifier(HARNESS_IDENTIFIER)
                                          .identifier(EXTRACT_STRING_FROM_A_FILE_DATAPOINT)
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
        .name(HARNESS_IDENTIFIER)
        .identifier(HARNESS_IDENTIFIER)
        .description(HARNESS_IDENTIFIER)
        .httpConfig(httpConfig)
        .build();
  }
}
