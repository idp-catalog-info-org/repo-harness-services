/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.DATADOG_IDENTIFIER;
import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.MISSING_DATA;
import static io.harness.idp.common.Constants.PROCESSED_DATA;
import static io.harness.rule.OwnerRule.SARABJYOT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;
import io.harness.idp.scorecard.datasourcelocations.entity.CatalogDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DatadogProviderTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account-123";
  private static final String RULE_IDENTIFIER = "test-rule-1";
  private static final String DATA_POINT_IDENTIFIER = "datadog.test.datapoint";
  private static final String DSL_IDENTIFIER = "test-catalog-dsl";
  private static final String ENTITY_NAME = "test-service";
  private static final String ENTITY_KIND = "component";
  private static final String PARENT_UNIQUE_ID = "zEaak-FLS425IEO7OLzMUg";
  private static final String UNIQUE_ID = "abcd-FLS425IEO7OLzMUg";
  private static final String PROVIDER = "Datadog";
  private static final String DESCRIPTION_JEXL = "catalog.metadata.integration_properties.Datadog.description";
  private static final String DESCRIPTION_VALUE = "Payments service dashboards";

  AutoCloseable openMocks;
  @Mock IdpAnalyticsService idpAnalyticsService;
  @Mock DataPointService dataPointService;
  @Mock DataSourceLocationRepository dataSourceLocationRepository;
  @Mock DataSourceRepository dataSourceRepository;
  DataSourceLocationFactoryV2 dataSourceLocationFactory;
  DataPointParserFactory dataPointParserFactory;
  DataPointParser dataPointParser;
  DatadogProvider datadogProvider;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    dataPointParser = new DefaultCatalogDSLParser();
    // Datadog resolves its data points through the Catalog DSL, so the parser factory always hands back the
    // catalog parser and the location factory routes CATALOG-typed DSLs to the real CatalogDataSourceLocation.
    dataPointParserFactory = (identifier, dataSourceLocationType) -> dataPointParser;
    dataSourceLocationFactory = new DataSourceLocationFactoryV2(
        new HQLDataSourceLocation(idpAnalyticsService), new CatalogDataSourceLocation());

    datadogProvider = new DatadogProvider(dataPointService, dataSourceLocationFactory, dataSourceLocationRepository,
        dataPointParserFactory, dataSourceRepository);
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_SuccessfulFetch() {
    // Arrange - entity is enriched with Datadog integration properties so the JEXL expression resolves
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, Map.of("description", DESCRIPTION_VALUE));
    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(DATADOG_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity(DESCRIPTION_JEXL));

    // Act
    Map<String, Map<String, Object>> result =
        datadogProvider.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), null);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(DATADOG_IDENTIFIER));
    Map<String, Object> providerData = result.get(DATADOG_IDENTIFIER);
    assertNotNull(providerData);
    assertTrue(providerData.containsKey(RULE_IDENTIFIER));
    Map<String, Object> dataPointInfo = (Map<String, Object>) providerData.get(RULE_IDENTIFIER);
    assertEquals(DESCRIPTION_VALUE, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));

    verify(dataPointService).getDslDataPointsInfo(eq(ACCOUNT_ID), eq(DATADOG_IDENTIFIER), anyList());
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_ComplexObjectValue() {
    // Arrange - the Catalog DSL returns the JEXL-resolved value raw, so a nested map flows through unchanged
    Map<String, Object> monitorsSummary = new HashMap<>();
    monitorsSummary.put("red", List.of("High error rate"));
    monitorsSummary.put("green", List.of("Service health check"));
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, Map.of("monitors_summary", monitorsSummary));
    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(DATADOG_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity("catalog.metadata.integration_properties.Datadog.monitors_summary"));

    // Act
    Map<String, Map<String, Object>> result =
        datadogProvider.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), null);

    // Assert
    assertNotNull(result);
    Map<String, Object> dataPointInfo = (Map<String, Object>) result.get(DATADOG_IDENTIFIER).get(RULE_IDENTIFIER);
    assertEquals(monitorsSummary, dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_MultipleDataPoints() {
    // Arrange - two rules under the same catalog DSL resolving two different Datadog properties
    Map<String, Object> datadog = new HashMap<>();
    datadog.put("description", DESCRIPTION_VALUE);
    datadog.put("monitorCount", 12);
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, datadog);

    DataFetchDTO dataFetchDTO1 = createDataFetchDTO();
    DataFetchDTO dataFetchDTO2 = DataFetchDTO.builder()
                                     .ruleIdentifier("test-rule-2")
                                     .dataPoint(DataPointEntity.builder().identifier(DATA_POINT_IDENTIFIER).build())
                                     .build();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, List.of(dataFetchDTO1, dataFetchDTO2));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(DATADOG_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity(DESCRIPTION_JEXL));

    // Act
    Map<String, Map<String, Object>> result =
        datadogProvider.fetchData(ACCOUNT_ID, entity, List.of(dataFetchDTO1, dataFetchDTO2), null);

    // Assert - both rules are resolved and merged under the provider identifier
    assertNotNull(result);
    assertTrue(result.containsKey(DATADOG_IDENTIFIER));
    Map<String, Object> providerData = result.get(DATADOG_IDENTIFIER);
    assertEquals(2, providerData.size());
    assertTrue(providerData.containsKey(RULE_IDENTIFIER));
    assertTrue(providerData.containsKey("test-rule-2"));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_UnresolvedExpressionYieldsMissingData() {
    // Arrange - the entity was never enriched, so the Datadog segment is undefined and JEXL resolves to nothing
    CatalogEntity entity = createCatalogEntity(ENTITY_NAME, ENTITY_KIND);
    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(DATADOG_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity(DESCRIPTION_JEXL));

    // Act
    Map<String, Map<String, Object>> result =
        datadogProvider.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), null);

    // Assert - the rule is present but its value is null with a MISSING_DATA error message
    assertNotNull(result);
    assertTrue(result.containsKey(DATADOG_IDENTIFIER));
    Map<String, Object> dataPointInfo = (Map<String, Object>) result.get(DATADOG_IDENTIFIER).get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertTrue(((String) dataPointInfo.get(ERROR_MESSAGE_KEY)).contains(MISSING_DATA));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_EntityNotCatalogEntity() {
    // Arrange - a non-catalog entity makes the location surface an error message rather than a value
    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(DATADOG_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER))
        .thenReturn(createCatalogLocationEntity(DESCRIPTION_JEXL));

    // Act
    Map<String, Map<String, Object>> result =
        datadogProvider.fetchData(ACCOUNT_ID, new Object(), Collections.singletonList(dataFetchDTO), null);

    // Assert - the error propagates to the data point as a null value with a non-null error message
    assertNotNull(result);
    assertTrue(result.containsKey(DATADOG_IDENTIFIER));
    Map<String, Object> dataPointInfo = (Map<String, Object>) result.get(DATADOG_IDENTIFIER).get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertEquals("Entity must be of type CatalogEntity", dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_LocationNotFound() {
    // Arrange - no DataSourceLocation exists for the DSL, so the provider skips it entirely
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, Map.of("description", DESCRIPTION_VALUE));
    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(DATADOG_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER)).thenReturn(null);

    // Act
    Map<String, Map<String, Object>> result =
        datadogProvider.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), null);

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testFetchData_EmptyDataToFetch() {
    // Arrange
    CatalogEntity entity = createEnrichedCatalogEntity(PROVIDER, Map.of("description", DESCRIPTION_VALUE));

    // Act
    Map<String, Map<String, Object>> result =
        datadogProvider.fetchData(ACCOUNT_ID, entity, Collections.emptyList(), null);

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetIdentifier() {
    // Act
    String identifier = datadogProvider.getIdentifier();

    // Assert
    assertEquals(DATADOG_IDENTIFIER, identifier);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  // Helper methods

  private DataFetchDTO createDataFetchDTO() {
    DataPointEntity dataPointEntity =
        DataPointEntity.builder().identifier(DATA_POINT_IDENTIFIER).dataSourceIdentifier(DATADOG_IDENTIFIER).build();
    return DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER).dataPoint(dataPointEntity).build();
  }

  private DataSourceLocationEntity createCatalogLocationEntity(String jexl) {
    CatalogDataSourceLocationEntity entity = new CatalogDataSourceLocationEntity();
    entity.setIdentifier(DSL_IDENTIFIER);
    entity.setType(DataSourceLocationType.CATALOG);
    entity.setJexl(jexl);
    return entity;
  }

  /**
   * Builds a catalog entity whose {@code provider} integration properties are stored under
   * {@code decorator._processed_data.metadata.integration_properties.<provider>}, mirroring how the integration
   * manager enriches entities. These become resolvable via
   * {@code catalog.metadata.integration_properties.<provider>.<key>} through {@code getDecoratedEntityMap()}.
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
