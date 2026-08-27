/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers.scm;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.common.Constants.MISSING_DATA;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.rule.OwnerRule.SARABJYOT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.common.YamlUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultCatalogDSLParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;
import io.harness.idp.scorecard.datasourcelocations.entity.CatalogDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DirectHttpDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HQLDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.CatalogDataSourceLocation;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactoryV2;
import io.harness.idp.scorecard.datasourcelocations.locations.HQLDataSourceLocation;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasourcelocations.service.IdpAnalyticsService;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class GithubDataSourceProviderV2Test extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account-123";
  private static final String CONFIGS = "github-configs";
  private static final String RULE_IDENTIFIER = "test-rule-1";
  private static final String RULE_IDENTIFIER_2 = "test-rule-2";
  private static final String LEGACY_RULE = "legacy-rule";
  private static final String DATA_POINT_IDENTIFIER = "github.test.datapoint";
  private static final String CATALOG_DSL_IDENTIFIER = "github-catalog-dsl";
  private static final String HQL_DSL_IDENTIFIER = "github-hql-dsl";
  private static final String LEGACY_DSL_IDENTIFIER = "github-legacy-dsl";
  private static final String MISSING_DSL_IDENTIFIER = "missing-dsl";
  private static final String ENTITY_NAME = "test-service";
  private static final String ENTITY_KIND = "component";
  private static final String PARENT_UNIQUE_ID = "zEaak-FLS425IEO7OLzMUg";
  private static final String UNIQUE_ID = "abcd-FLS425IEO7OLzMUg";
  private static final String PROVIDER = "Github";
  private static final String DESCRIPTION_JEXL = "catalog.metadata.integration_properties.Github.description";
  private static final String DESCRIPTION_VALUE = "Harness IDP monorepo";

  AutoCloseable openMocks;
  @Mock IdpAnalyticsService idpAnalyticsService;
  @Mock DataPointService dataPointService;
  @Mock DataSourceLocationRepository dataSourceLocationRepository;
  @Mock DataSourceRepository dataSourceRepository;
  @Mock GithubProvider githubProvider;
  DataSourceLocationFactoryV2 dataSourceLocationFactory;
  DataPointParserFactory dataPointParserFactory;
  DataPointParser catalogDataPointParser;
  DataPointParser hqlDataPointParser;
  GithubDataSourceProviderV2 githubDataSourceProviderV2;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    catalogDataPointParser = new DefaultCatalogDSLParser();
    hqlDataPointParser = new DefaultHQLParser();
    // Catalog datapoints use DefaultCatalogDSLParser; HQL datapoints (github.hql.*) use DefaultHQLParser.
    dataPointParserFactory = (identifier, dataSourceLocationType)
        -> identifier != null && identifier.startsWith("github.hql.") ? hqlDataPointParser : catalogDataPointParser;
    dataSourceLocationFactory = new DataSourceLocationFactoryV2(
        new HQLDataSourceLocation(idpAnalyticsService), new CatalogDataSourceLocation());

    githubDataSourceProviderV2 = new GithubDataSourceProviderV2(dataPointService, dataSourceLocationFactory,
        dataSourceLocationRepository, dataPointParserFactory, dataSourceRepository, githubProvider);
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetIdentifier() {
    assertEquals(GITHUB_IDENTIFIER, githubDataSourceProviderV2.getIdentifier());
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_EmptyDataToFetch() {
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, Map.of("description", DESCRIPTION_VALUE));

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, Collections.emptyList(), CONFIGS);

    assertNotNull(result);
    assertTrue(result.isEmpty());
    verify(githubProvider, never()).fetchData(eq(ACCOUNT_ID), eq(entity), anyList(), eq(CONFIGS));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_V2Catalog_SuccessfulFetch() {
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, Map.of("description", DESCRIPTION_VALUE));
    DataFetchDTO dataFetchDTO = createDataFetchDTO(RULE_IDENTIFIER);

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(CATALOG_DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(CATALOG_DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity(DESCRIPTION_JEXL));

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), CONFIGS);

    assertNotNull(result);
    assertTrue(result.containsKey(GITHUB_IDENTIFIER));
    Map<String, Object> dataPointInfo = (Map<String, Object>) result.get(GITHUB_IDENTIFIER).get(RULE_IDENTIFIER);
    assertEquals(DESCRIPTION_VALUE, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
    verify(githubProvider, never()).fetchData(eq(ACCOUNT_ID), eq(entity), anyList(), eq(CONFIGS));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_V2Catalog_MultipleDataPoints() {
    Map<String, Object> github = new HashMap<>();
    github.put("description", DESCRIPTION_VALUE);
    github.put("stars", 42);
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, github);

    DataFetchDTO dataFetchDTO1 = createDataFetchDTO(RULE_IDENTIFIER);
    DataFetchDTO dataFetchDTO2 = createDataFetchDTO(RULE_IDENTIFIER_2);

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(CATALOG_DSL_IDENTIFIER, List.of(dataFetchDTO1, dataFetchDTO2));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(CATALOG_DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity(DESCRIPTION_JEXL));

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, List.of(dataFetchDTO1, dataFetchDTO2), CONFIGS);

    assertNotNull(result);
    Map<String, Object> providerData = result.get(GITHUB_IDENTIFIER);
    assertEquals(2, providerData.size());
    assertTrue(providerData.containsKey(RULE_IDENTIFIER));
    assertTrue(providerData.containsKey(RULE_IDENTIFIER_2));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_V2Catalog_UnresolvedExpressionYieldsMissingData() {
    CatalogEntity entity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    DataFetchDTO dataFetchDTO = createDataFetchDTO(RULE_IDENTIFIER);

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(CATALOG_DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(CATALOG_DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity(DESCRIPTION_JEXL));

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), CONFIGS);

    assertNotNull(result);
    Map<String, Object> dataPointInfo = (Map<String, Object>) result.get(GITHUB_IDENTIFIER).get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertTrue(((String) dataPointInfo.get(ERROR_MESSAGE_KEY)).contains(MISSING_DATA));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_V2Catalog_EntityNotCatalogEntity() {
    DataFetchDTO dataFetchDTO = createDataFetchDTO(RULE_IDENTIFIER);

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(CATALOG_DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(CATALOG_DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity(DESCRIPTION_JEXL));

    Map<String, Map<String, Object>> result = githubDataSourceProviderV2.fetchData(
        ACCOUNT_ID, new Object(), Collections.singletonList(dataFetchDTO), CONFIGS);

    assertNotNull(result);
    Map<String, Object> dataPointInfo = (Map<String, Object>) result.get(GITHUB_IDENTIFIER).get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals("Entity must be of type CatalogEntity", dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_V2Hql_SuccessfulFetch() {
    CatalogEntity entity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    DataFetchDTO dataFetchDTO = DataFetchDTO.builder()
                                    .ruleIdentifier(RULE_IDENTIFIER)
                                    .dataPoint(DataPointEntity.builder()
                                                   .identifier("github.hql.datapoint")
                                                   .type(DataPointEntity.Type.NUMBER)
                                                   .dataSourceIdentifier(GITHUB_IDENTIFIER)
                                                   .build())
                                    .build();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(HQL_DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(HQL_DSL_IDENTIFIER)).thenReturn(createHqlLocationEntity());
    when(idpAnalyticsService.execute(anyString(), eq(ACCOUNT_ID)))
        .thenReturn(Collections.singletonList(Map.of("value", 7)));

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), CONFIGS);

    assertNotNull(result);
    Map<String, Object> dataPointInfo = (Map<String, Object>) result.get(GITHUB_IDENTIFIER).get(RULE_IDENTIFIER);
    assertEquals(7, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
    verify(githubProvider, never()).fetchData(eq(ACCOUNT_ID), eq(entity), anyList(), eq(CONFIGS));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_V2Hql_ExceptionInLocationFetch() {
    CatalogEntity entity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    DataFetchDTO dataFetchDTO = DataFetchDTO.builder()
                                    .ruleIdentifier(RULE_IDENTIFIER)
                                    .dataPoint(DataPointEntity.builder()
                                                   .identifier("github.hql.datapoint")
                                                   .dataSourceIdentifier(GITHUB_IDENTIFIER)
                                                   .build())
                                    .build();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(HQL_DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(HQL_DSL_IDENTIFIER)).thenReturn(createHqlLocationEntity());
    when(idpAnalyticsService.execute(anyString(), eq(ACCOUNT_ID)))
        .thenThrow(new RuntimeException("Query execution failed"));

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), CONFIGS);

    assertNotNull(result);
    assertTrue(result.containsKey(GITHUB_IDENTIFIER));
    Map<String, Object> dataPointInfo = (Map<String, Object>) result.get(GITHUB_IDENTIFIER).get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNotNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_LocationNotFound_SkipsDsl() {
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, Map.of("description", DESCRIPTION_VALUE));
    DataFetchDTO dataFetchDTO = createDataFetchDTO(RULE_IDENTIFIER);

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(MISSING_DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(MISSING_DSL_IDENTIFIER)).thenReturn(null);

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), CONFIGS);

    assertNotNull(result);
    assertTrue(result.isEmpty());
    verify(githubProvider, never()).fetchData(eq(ACCOUNT_ID), eq(entity), anyList(), eq(CONFIGS));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_LegacyOnly_DelegatesToGithubProvider() {
    CatalogEntity entity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    DataFetchDTO legacyFetchDTO = createDataFetchDTO(LEGACY_RULE);

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(LEGACY_DSL_IDENTIFIER, Collections.singletonList(legacyFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(LEGACY_DSL_IDENTIFIER)).thenReturn(createLegacyLocationEntity());
    when(githubProvider.fetchData(eq(ACCOUNT_ID), eq(entity), anyList(), eq(CONFIGS)))
        .thenReturn(Map.of(GITHUB_IDENTIFIER, Map.of(LEGACY_RULE, 42)));

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, Collections.singletonList(legacyFetchDTO), CONFIGS);

    assertNotNull(result);
    assertEquals(42, result.get(GITHUB_IDENTIFIER).get(LEGACY_RULE));

    ArgumentCaptor<List<DataFetchDTO>> legacyCaptor = ArgumentCaptor.forClass(List.class);
    verify(githubProvider).fetchData(eq(ACCOUNT_ID), eq(entity), legacyCaptor.capture(), eq(CONFIGS));
    assertEquals(1, legacyCaptor.getValue().size());
    assertEquals(LEGACY_RULE, legacyCaptor.getValue().get(0).getRuleIdentifier());
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_MixedLegacyAndV2_MergesResults() {
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, Map.of("description", DESCRIPTION_VALUE));
    DataFetchDTO legacyFetchDTO = createDataFetchDTO(LEGACY_RULE);
    DataFetchDTO v2FetchDTO = createDataFetchDTO(RULE_IDENTIFIER);

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(LEGACY_DSL_IDENTIFIER, Collections.singletonList(legacyFetchDTO));
    dataToFetchByDsl.put(CATALOG_DSL_IDENTIFIER, Collections.singletonList(v2FetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(LEGACY_DSL_IDENTIFIER)).thenReturn(createLegacyLocationEntity());
    when(dataSourceLocationRepository.findByIdentifier(CATALOG_DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity(DESCRIPTION_JEXL));
    when(githubProvider.fetchData(eq(ACCOUNT_ID), eq(entity), anyList(), eq(CONFIGS)))
        .thenReturn(Map.of(GITHUB_IDENTIFIER, new HashMap<>(Map.of(LEGACY_RULE, "legacy-value"))));

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, List.of(legacyFetchDTO, v2FetchDTO), CONFIGS);

    Map<String, Object> providerData = result.get(GITHUB_IDENTIFIER);
    assertEquals(2, providerData.size());
    assertEquals("legacy-value", providerData.get(LEGACY_RULE));
    Map<String, Object> v2DataPointInfo = (Map<String, Object>) providerData.get(RULE_IDENTIFIER);
    assertEquals(DESCRIPTION_VALUE, v2DataPointInfo.get(DATA_POINT_VALUE_KEY));

    ArgumentCaptor<List<DataFetchDTO>> legacyCaptor = ArgumentCaptor.forClass(List.class);
    verify(githubProvider).fetchData(eq(ACCOUNT_ID), eq(entity), legacyCaptor.capture(), eq(CONFIGS));
    assertEquals(1, legacyCaptor.getValue().size());
    assertEquals(LEGACY_RULE, legacyCaptor.getValue().get(0).getRuleIdentifier());
    verify(dataSourceLocationRepository, times(1)).findByIdentifier(CATALOG_DSL_IDENTIFIER);
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_MissingDslSkippedAmongValidOnes() {
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, Map.of("description", DESCRIPTION_VALUE));
    DataFetchDTO missingFetchDTO = createDataFetchDTO("missing-rule");
    DataFetchDTO v2FetchDTO = createDataFetchDTO(RULE_IDENTIFIER);

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(MISSING_DSL_IDENTIFIER, Collections.singletonList(missingFetchDTO));
    dataToFetchByDsl.put(CATALOG_DSL_IDENTIFIER, Collections.singletonList(v2FetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(MISSING_DSL_IDENTIFIER)).thenReturn(null);
    when(dataSourceLocationRepository.findByIdentifier(CATALOG_DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity(DESCRIPTION_JEXL));

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, List.of(missingFetchDTO, v2FetchDTO), CONFIGS);

    assertEquals(1, result.get(GITHUB_IDENTIFIER).size());
    assertFalse(result.get(GITHUB_IDENTIFIER).containsKey("missing-rule"));
    Map<String, Object> dataPointInfo = (Map<String, Object>) result.get(GITHUB_IDENTIFIER).get(RULE_IDENTIFIER);
    assertEquals(DESCRIPTION_VALUE, dataPointInfo.get(DATA_POINT_VALUE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_LegacyProviderMissingIdentifierKey_DoesNotMerge() {
    CatalogEntity entity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    DataFetchDTO legacyFetchDTO = createDataFetchDTO(LEGACY_RULE);

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(LEGACY_DSL_IDENTIFIER, Collections.singletonList(legacyFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(GITHUB_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(LEGACY_DSL_IDENTIFIER)).thenReturn(createLegacyLocationEntity());
    when(githubProvider.fetchData(eq(ACCOUNT_ID), eq(entity), anyList(), eq(CONFIGS)))
        .thenReturn(Map.of("other-provider", Map.of(LEGACY_RULE, 1)));

    Map<String, Map<String, Object>> result =
        githubDataSourceProviderV2.fetchData(ACCOUNT_ID, entity, Collections.singletonList(legacyFetchDTO), CONFIGS);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private DataFetchDTO createDataFetchDTO(String ruleIdentifier) {
    DataPointEntity dataPointEntity =
        DataPointEntity.builder().identifier(DATA_POINT_IDENTIFIER).dataSourceIdentifier(GITHUB_IDENTIFIER).build();
    return DataFetchDTO.builder().ruleIdentifier(ruleIdentifier).dataPoint(dataPointEntity).build();
  }

  private DataSourceLocationEntity createCatalogLocationEntity(String jexl) {
    CatalogDataSourceLocationEntity entity = new CatalogDataSourceLocationEntity();
    entity.setIdentifier(CATALOG_DSL_IDENTIFIER);
    entity.setType(DataSourceLocationType.CATALOG);
    entity.setJexl(jexl);
    return entity;
  }

  private DataSourceLocationEntity createHqlLocationEntity() {
    HQLDataSourceLocationEntity entity = new HQLDataSourceLocationEntity();
    entity.setIdentifier(HQL_DSL_IDENTIFIER);
    entity.setHqlTemplate("SELECT * FROM test");
    return entity;
  }

  private DataSourceLocationEntity createLegacyLocationEntity() {
    DirectHttpDataSourceLocationEntity entity = new DirectHttpDataSourceLocationEntity();
    entity.setIdentifier(LEGACY_DSL_IDENTIFIER);
    return entity;
  }

  /**
   * Builds a catalog entity whose {@code Github} integration properties are stored under
   * {@code decorator._processed_data.metadata.integration_properties.Github}, mirroring how the integration
   * manager enriches entities.
   */
  private CatalogEntity createEnrichedCatalogEntity(String provider, Map<String, Object> providerProperties) {
    InlineCatalogEntity entity = (InlineCatalogEntity) createCatalogEntity(ENTITY_NAME, ENTITY_KIND);

    Map<String, Object> integrationProperties = new HashMap<>();
    integrationProperties.put(provider, providerProperties);

    Map<String, Object> processedMetadata = new HashMap<>();
    processedMetadata.put("integration_properties", integrationProperties);

    Map<String, Object> processedData = new HashMap<>();
    processedData.put("metadata", processedMetadata);

    Map<String, Object> decorator = new HashMap<>();
    decorator.put(PROCESSED_DATA, processedData);

    entity.setDecorator(decorator);
    return entity;
  }

  private CatalogEntity createCatalogEntity(String name, String kind) {
    Map<String, Object> yamlContent = new HashMap<>();
    yamlContent.put("apiVersion", "v1");
    yamlContent.put("kind", kind);
    yamlContent.put("identifier", name);
    yamlContent.put("metadata", new HashMap<>(Map.of("name", name)));

    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier(ACCOUNT_ID);
    entity.setApiVersion("v1");
    entity.setKind(kind);
    entity.setName(name);
    entity.setIdentifier(name);
    entity.setParentUniqueId(PARENT_UNIQUE_ID);
    entity.setUniqueId(UNIQUE_ID);
    entity.setMetadata(new HashMap<>(Map.of("name", name)));
    entity.setYaml(YamlUtils.writeObjectAsYaml(yamlContent));
    return entity;
  }
}
