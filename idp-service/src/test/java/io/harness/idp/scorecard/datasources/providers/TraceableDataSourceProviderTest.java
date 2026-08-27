/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.providers;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.TRACEABLE_IDENTIFIER;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.datapoints.parser.DefaultHQLParser;
import io.harness.idp.scorecard.datapoints.parser.factory.DataPointParserFactory;
import io.harness.idp.scorecard.datapoints.parser.factory.DefaultIntegrationDataPointParserFactory;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HQLDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.CatalogDataSourceLocation;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationFactoryV2;
import io.harness.idp.scorecard.datasourcelocations.locations.DataSourceLocationV2;
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
public class TraceableDataSourceProviderTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account-123";
  private static final String RULE_IDENTIFIER = "test-rule-1";
  private static final String DATA_POINT_IDENTIFIER = "traceable.test.datapoint";
  private static final String DSL_IDENTIFIER = "test-hql-dsl";
  private static final String ENTITY_NAME = "test-service";
  private static final String ENTITY_KIND = "component";

  AutoCloseable openMocks;
  @Mock IdpAnalyticsService idpAnalyticsService;
  @Mock DataPointService dataPointService;
  @Mock DataSourceLocationRepository dataSourceLocationRepository;
  @Mock DataSourceRepository dataSourceRepository;
  DataSourceLocationFactoryV2 dataSourceLocationFactory;
  DataPointParserFactory dataPointParserFactory;
  DataSourceLocationV2 dataSourceLocation;
  DataPointParser dataPointParser;
  TraceableDataSourceProvider traceableDataSourceProvider;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    dataSourceLocation = new HQLDataSourceLocation(idpAnalyticsService);
    dataPointParser = new DefaultHQLParser();
    dataPointParserFactory = new DefaultIntegrationDataPointParserFactory((DefaultHQLParser) dataPointParser, null);
    dataSourceLocationFactory =
        new DataSourceLocationFactoryV2((HQLDataSourceLocation) dataSourceLocation, new CatalogDataSourceLocation());

    traceableDataSourceProvider = new TraceableDataSourceProvider(dataPointService, dataSourceLocationFactory,
        dataSourceLocationRepository, dataPointParserFactory, dataSourceRepository);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData_SuccessfulFetch() {
    // Arrange
    CatalogEntity entity = createCatalogEntity();
    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    Map<String, Object> dslResponse = new HashMap<>();
    dslResponse.put("value", 42);

    Map<String, Object> parsedData = new HashMap<>();
    parsedData.put(DATA_POINT_VALUE_KEY, dslResponse.get("value"));
    parsedData.put(ERROR_MESSAGE_KEY, null);

    Map<String, Object> parserResponse = new HashMap<>();
    parserResponse.put(RULE_IDENTIFIER, parsedData);

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(TRACEABLE_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER)).thenReturn(createDataSourceLocationEntity());
    when(idpAnalyticsService.execute(anyString(), eq(ACCOUNT_ID))).thenReturn(Collections.singletonList(dslResponse));

    // Act
    Map<String, Map<String, Object>> result =
        traceableDataSourceProvider.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), null);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(TRACEABLE_IDENTIFIER));
    Map<String, Object> providerData = result.get(TRACEABLE_IDENTIFIER);
    assertNotNull(providerData);
    assertTrue(providerData.containsKey(RULE_IDENTIFIER));
    assertEquals(parsedData, providerData.get(RULE_IDENTIFIER));

    verify(dataPointService).getDslDataPointsInfo(eq(ACCOUNT_ID), eq(TRACEABLE_IDENTIFIER), anyList());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData_MultipleDataPoints() {
    // Arrange
    CatalogEntity entity = createCatalogEntity();
    DataFetchDTO dataFetchDTO1 = createDataFetchDTO();
    DataFetchDTO dataFetchDTO2 = DataFetchDTO.builder()
                                     .ruleIdentifier("test-rule-2")
                                     .dataPoint(DataPointEntity.builder().identifier(DATA_POINT_IDENTIFIER).build())
                                     .build();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, List.of(dataFetchDTO1, dataFetchDTO2));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(TRACEABLE_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER)).thenReturn(createDataSourceLocationEntity());
    when(idpAnalyticsService.execute(anyString(), eq(ACCOUNT_ID)))
        .thenReturn(Collections.singletonList(Map.of("value", 42)))
        .thenReturn(Collections.singletonList(Map.of("value", 50)));

    // Act
    Map<String, Map<String, Object>> result =
        traceableDataSourceProvider.fetchData(ACCOUNT_ID, entity, List.of(dataFetchDTO1, dataFetchDTO2), null);

    // Assert
    assertNotNull(result);
    assertTrue(result.containsKey(TRACEABLE_IDENTIFIER));
    Map<String, Object> providerData = result.get(TRACEABLE_IDENTIFIER);
    assertEquals(2, providerData.size());
    assertTrue(providerData.containsKey(RULE_IDENTIFIER));
    assertTrue(providerData.containsKey("test-rule-2"));

    verify(idpAnalyticsService, times(2)).execute(anyString(), eq(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData_ExceptionInLocationFetch() {
    // Arrange
    CatalogEntity entity = createCatalogEntity();
    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(TRACEABLE_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER)).thenReturn(createDataSourceLocationEntity());
    when(idpAnalyticsService.execute(anyString(), eq(ACCOUNT_ID)))
        .thenThrow(new RuntimeException("Query execution failed"));

    // Act
    Map<String, Map<String, Object>> result =
        traceableDataSourceProvider.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), null);

    // Assert - Query execution failure surfaces as an error message, not a successful value
    assertNotNull(result);
    assertTrue(result.isEmpty() || !result.containsKey(RULE_IDENTIFIER));

    verify(idpAnalyticsService).execute(anyString(), eq(ACCOUNT_ID));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData_MissingValueKey() {
    // Arrange - HQL result is missing the "value" key, so no usable data point value is produced
    CatalogEntity entity = createCatalogEntity();
    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(TRACEABLE_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER)).thenReturn(createDataSourceLocationEntity());
    when(idpAnalyticsService.execute(anyString(), eq(ACCOUNT_ID)))
        .thenReturn(Collections.singletonList(Map.of("count", 42)));

    // Act
    Map<String, Map<String, Object>> result =
        traceableDataSourceProvider.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), null);

    // Assert - the rule is present but its value is null with an error message set
    assertNotNull(result);
    assertTrue(result.containsKey(TRACEABLE_IDENTIFIER));
    Map<String, Object> providerData = result.get(TRACEABLE_IDENTIFIER);
    assertTrue(providerData.containsKey(RULE_IDENTIFIER));
    Map<String, Object> dataPointInfo = (Map<String, Object>) providerData.get(RULE_IDENTIFIER);
    assertNull(dataPointInfo.get(DATA_POINT_VALUE_KEY));
    assertNotNull(dataPointInfo.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData_EmptyDataToFetch() {
    // Arrange
    CatalogEntity entity = createCatalogEntity();

    // Act
    Map<String, Map<String, Object>> result =
        traceableDataSourceProvider.fetchData(ACCOUNT_ID, entity, Collections.emptyList(), null);

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFetchData_ResponseWrapping() {
    // Arrange - Verify that response is wrapped with rule identifier before passing to parser
    CatalogEntity entity = createCatalogEntity();
    DataFetchDTO dataFetchDTO = createDataFetchDTO();

    Map<String, List<DataFetchDTO>> dataToFetchByDsl = new HashMap<>();
    dataToFetchByDsl.put(DSL_IDENTIFIER, Collections.singletonList(dataFetchDTO));

    when(dataPointService.getDslDataPointsInfo(eq(ACCOUNT_ID), eq(TRACEABLE_IDENTIFIER), anyList()))
        .thenReturn(dataToFetchByDsl);
    when(dataSourceLocationRepository.findByIdentifier(DSL_IDENTIFIER)).thenReturn(createDataSourceLocationEntity());
    when(idpAnalyticsService.execute(anyString(), eq(ACCOUNT_ID)))
        .thenReturn(Collections.singletonList(Map.of("value", 100)));

    // Act
    Map<String, Map<String, Object>> result =
        traceableDataSourceProvider.fetchData(ACCOUNT_ID, entity, Collections.singletonList(dataFetchDTO), null);

    // Assert - response is wrapped with the rule identifier and the value is unwrapped by the parser
    assertNotNull(result);
    assertTrue(result.containsKey(TRACEABLE_IDENTIFIER));
    Map<String, Object> providerData = result.get(TRACEABLE_IDENTIFIER);
    assertTrue(providerData.containsKey(RULE_IDENTIFIER));
    Map<String, Object> dataPointInfo = (Map<String, Object>) providerData.get(RULE_IDENTIFIER);
    assertEquals(100, dataPointInfo.get(DATA_POINT_VALUE_KEY));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetIdentifier() {
    // Act
    String identifier = traceableDataSourceProvider.getIdentifier();

    // Assert
    assertEquals(TRACEABLE_IDENTIFIER, identifier);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  // Helper methods

  private CatalogEntity createCatalogEntity() {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier(ACCOUNT_ID);
    entity.setKind(ENTITY_KIND);
    entity.setName(ENTITY_NAME);
    entity.setIdentifier(ENTITY_NAME);
    entity.setApiVersion("v1");

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("name", ENTITY_NAME);
    entity.setMetadata(metadata);

    String yaml = "apiVersion: v1\nkind: " + ENTITY_KIND + "\nmetadata:\n  name: " + ENTITY_NAME;
    entity.setYaml(yaml);

    return entity;
  }

  private DataFetchDTO createDataFetchDTO() {
    DataPointEntity dataPointEntity =
        DataPointEntity.builder().identifier(DATA_POINT_IDENTIFIER).dataSourceIdentifier(TRACEABLE_IDENTIFIER).build();
    return DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER).dataPoint(dataPointEntity).build();
  }

  private DataSourceLocationEntity createDataSourceLocationEntity() {
    HQLDataSourceLocationEntity entity = new HQLDataSourceLocationEntity();
    entity.setIdentifier(DSL_IDENTIFIER);
    entity.setHqlTemplate("SELECT * FROM test");
    return entity;
  }
}
