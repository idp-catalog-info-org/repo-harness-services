/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.service;

import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.SARABJYOT;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.integrationmanager.TypesIntegrationConfig.EnumIntegrationType;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasources.cache.EnabledIntegrationsInMemoryCache;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.entity.HttpDataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.DataSource;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointsMap;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DataSourceServiceImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock DataSourceRepository dataSourceRepository;

  @Mock DataPointService dataPointService;
  @Mock EnabledIntegrationsInMemoryCache enabledIntegrationsInMemoryCache;

  @InjectMocks DataSourceServiceImpl dataSourceServiceImpl;

  private static final String TEST_DATA_SOURCE_IDENTIFIER = " test-datasource-identifier";
  private static final String TEST_DATA_SOURCE_NAME = "test-datasource-name";
  private static final String TEST_DATA_SOURCE_DESCRIPTION = "test-datasource-description";
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-accountIdentifier";

  private static final String TEST_DATA_POINT_TYPE = "test-datapoint-type";
  private static final String TEST_DATA_POINT_NAME = "test-data-point-name";
  private static final String TEST_DATA_POINT_DESCRIPTION = "test-data-point=description";
  private static final Boolean TEST_DATA_POINT_IS_CONDITIONAL_VALUE = false;
  private static final String TEST_CONDITIONAL_INPUT_DESCRIPTION = "test-datapoint-input-description";
  private static final String TEST_DATAPOINT_IDENTIFIER = "test-datapoint-identifier";
  private static final String TEST_DATAPOINT_DETAILED_DESCRIPTION = "test-datapoint-detailed-description";

  private static final String TEST_DATA_SOURCE_LOCATION_IDENTIFIER = "test-data-source-location-identifier";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }
  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllDataSourcesDetailsForAnAccount() {
    DataSourceEntity dataSourceEntity = HttpDataSourceEntity.builder()
                                            .name(TEST_DATA_SOURCE_NAME)
                                            .description(TEST_DATA_SOURCE_DESCRIPTION)
                                            .identifier(TEST_DATA_SOURCE_IDENTIFIER)
                                            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                            .build();
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(Collections.singletonList(dataSourceEntity));
    List<DataSource> dataSourceList =
        dataSourceServiceImpl.getAllDataSourcesDetailsForAnAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(dataSourceEntity.getName(), dataSourceList.get(0).getName());
    assertEquals(dataSourceEntity.getDescription(), dataSourceList.get(0).getDescription());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllDataPointsDetailsForDataSource() {
    DataPoint dataPoint = new DataPoint();
    dataPoint.setName(TEST_DATA_POINT_NAME);
    dataPoint.setDataPointIdentifier(TEST_DATAPOINT_IDENTIFIER);
    dataPoint.setType(TEST_DATA_POINT_TYPE);
    dataPoint.setDescription(TEST_DATA_POINT_DESCRIPTION);
    dataPoint.setDetailedDescription(TEST_DATAPOINT_DETAILED_DESCRIPTION);

    when(dataPointService.getAllDataPointsDetailsForAccountAndDataSource(
             TEST_ACCOUNT_IDENTIFIER, TEST_DATA_SOURCE_IDENTIFIER))
        .thenReturn(Collections.singletonList(dataPoint));

    List<DataPoint> returnedDataPoints = dataSourceServiceImpl.getAllDataPointsDetailsForDataSource(
        TEST_ACCOUNT_IDENTIFIER, TEST_DATA_SOURCE_IDENTIFIER);
    assertEquals(returnedDataPoints.get(0).getDataPointIdentifier(), dataPoint.getDataPointIdentifier());
    assertEquals(returnedDataPoints.get(0).getName(), dataPoint.getName());
    assertEquals(returnedDataPoints.get(0).getDescription(), dataPoint.getDescription());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetDataPointsForDataSources() {
    DataPointEntity dataPointEntity = DataPointEntity.builder()
                                          .dataSourceIdentifier(TEST_DATA_SOURCE_IDENTIFIER)
                                          .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                          .conditionalInputValueDescription(TEST_CONDITIONAL_INPUT_DESCRIPTION)
                                          .detailedDescription(TEST_DATAPOINT_DETAILED_DESCRIPTION)
                                          .isConditional(TEST_DATA_POINT_IS_CONDITIONAL_VALUE)
                                          .identifier(TEST_DATAPOINT_IDENTIFIER)
                                          .dataSourceLocationIdentifier(TEST_DATA_SOURCE_LOCATION_IDENTIFIER)
                                          .type(DataPointEntity.Type.NUMBER)
                                          .build();
    DataSourceEntity dataSourceEntity = HttpDataSourceEntity.builder()
                                            .name(TEST_DATA_SOURCE_NAME)
                                            .description(TEST_DATA_SOURCE_DESCRIPTION)
                                            .accountIdentifier(TEST_DATA_SOURCE_IDENTIFIER)
                                            .identifier(TEST_DATA_SOURCE_IDENTIFIER)
                                            .build();

    when(dataPointService.getAllDataPointsForAccount(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(dataPointEntity));
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(Collections.singletonList(dataSourceEntity));

    List<DataSourceDataPointsMap> returnedData =
        dataSourceServiceImpl.getDataPointsForDataSources(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(returnedData.get(0).getDataSource().getName(), dataSourceEntity.getName());

    assertEquals(returnedData.get(0).getDataSource().getIdentifier(), dataSourceEntity.getIdentifier());

    assertEquals(returnedData.get(0).getDataPoints().get(0).getDataPointIdentifier(), dataPointEntity.getIdentifier());
    assertEquals(returnedData.get(0).getDataPoints().get(0).getName(), dataPointEntity.getName());
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetAllDataSourcesFiltersDisabledAndMissingNewIntegrations() {
    DataSourceEntity datadog = dataSource("datadog");
    DataSourceEntity sonar = dataSource("sonarqube");
    DataSourceEntity gcp = dataSource("gcp");
    DataSourceEntity github = dataSource("github");
    DataSourceEntity custom = dataSource("custom");
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(List.of(datadog, sonar, gcp, github, custom));
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(Set.of(EnumIntegrationType.DataDog)));

    List<DataSource> dataSources = dataSourceServiceImpl.getAllDataSourcesDetailsForAnAccount(TEST_ACCOUNT_IDENTIFIER);

    assertThat(dataSources).extracting(DataSource::getIdentifier).containsExactly("datadog", "github", "custom");
    verify(enabledIntegrationsInMemoryCache).getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetAllDataSourcesAlwaysPassesLegacyDataSources() {
    DataSourceEntity github = dataSource("github");
    DataSourceEntity pagerDuty = dataSource("pagerduty");
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(List.of(github, pagerDuty));
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(Collections.emptySet()));

    List<DataSource> dataSources = dataSourceServiceImpl.getAllDataSourcesDetailsForAnAccount(TEST_ACCOUNT_IDENTIFIER);

    assertThat(dataSources).extracting(DataSource::getIdentifier).containsExactly("github", "pagerduty");
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetAllDataSourcesFailsOpenWhenIntegrationCacheMisses() {
    DataSourceEntity datadog = dataSource("datadog");
    DataSourceEntity sonar = dataSource("sonarqube");
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(List.of(datadog, sonar));
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.empty());

    List<DataSource> dataSources = dataSourceServiceImpl.getAllDataSourcesDetailsForAnAccount(TEST_ACCOUNT_IDENTIFIER);

    assertThat(dataSources).extracting(DataSource::getIdentifier).containsExactly("datadog", "sonarqube");
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetDataPointsForDataSourcesFailsOpenWhenIntegrationCacheMisses() {
    DataSourceEntity datadog = dataSource("datadog");
    DataSourceEntity custom = dataSource("custom");
    when(dataPointService.getAllDataPointsForAccount(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Collections.emptyList());
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(List.of(datadog, custom));
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.empty());

    List<DataSourceDataPointsMap> dataSources =
        dataSourceServiceImpl.getDataPointsForDataSources(TEST_ACCOUNT_IDENTIFIER);

    assertThat(dataSources)
        .extracting(dataSourceDataPointsMap -> dataSourceDataPointsMap.getDataSource().getIdentifier())
        .containsExactly("datadog", "custom");
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetDataPointsForNewIntegrationFailsOpenWhenIntegrationCacheMisses() {
    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(TEST_DATAPOINT_IDENTIFIER);
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.empty());
    when(dataPointService.getAllDataPointsDetailsForAccountAndDataSource(TEST_ACCOUNT_IDENTIFIER, "datadog"))
        .thenReturn(List.of(dataPoint));

    List<DataPoint> dataPoints =
        dataSourceServiceImpl.getAllDataPointsDetailsForDataSource(TEST_ACCOUNT_IDENTIFIER, "datadog");

    assertThat(dataPoints).containsExactly(dataPoint);
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetDataPointsForDataSourcesFiltersDisabledNewIntegration() {
    DataSourceEntity datadog = dataSource("datadog");
    DataSourceEntity custom = dataSource("custom");
    when(dataPointService.getAllDataPointsForAccount(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Collections.emptyList());
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(List.of(datadog, custom));
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(Collections.emptySet()));

    List<DataSourceDataPointsMap> dataSources =
        dataSourceServiceImpl.getDataPointsForDataSources(TEST_ACCOUNT_IDENTIFIER);

    assertThat(dataSources)
        .extracting(dataSourceDataPointsMap -> dataSourceDataPointsMap.getDataSource().getIdentifier())
        .containsExactly("custom");
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetDataPointsForDisabledNewIntegrationReturnsEmpty() {
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(Collections.emptySet()));

    List<DataPoint> dataPoints =
        dataSourceServiceImpl.getAllDataPointsDetailsForDataSource(TEST_ACCOUNT_IDENTIFIER, "datadog");

    assertThat(dataPoints).isEmpty();
    verify(dataPointService, never())
        .getAllDataPointsDetailsForAccountAndDataSource(TEST_ACCOUNT_IDENTIFIER, "datadog");
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetDataPointsForEnabledNewIntegration() {
    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(TEST_DATAPOINT_IDENTIFIER);
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(Set.of(EnumIntegrationType.DataDog)));
    when(dataPointService.getAllDataPointsDetailsForAccountAndDataSource(TEST_ACCOUNT_IDENTIFIER, "datadog"))
        .thenReturn(List.of(dataPoint));

    List<DataPoint> dataPoints =
        dataSourceServiceImpl.getAllDataPointsDetailsForDataSource(TEST_ACCOUNT_IDENTIFIER, "datadog");

    assertThat(dataPoints).containsExactly(dataPoint);
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetDataPointsForLegacyDataSourceDoesNotCheckIntegrations() {
    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(TEST_DATAPOINT_IDENTIFIER);
    when(dataPointService.getAllDataPointsDetailsForAccountAndDataSource(TEST_ACCOUNT_IDENTIFIER, "github"))
        .thenReturn(List.of(dataPoint));

    List<DataPoint> dataPoints =
        dataSourceServiceImpl.getAllDataPointsDetailsForDataSource(TEST_ACCOUNT_IDENTIFIER, "github");

    assertThat(dataPoints).containsExactly(dataPoint);
    verify(enabledIntegrationsInMemoryCache, never()).getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testTraceableIsFilteredWhenIntegrationIsDisabled() {
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(List.of(dataSource("traceable"), dataSource("custom")));
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(Collections.emptySet()));

    List<DataSource> dataSources = dataSourceServiceImpl.getAllDataSourcesDetailsForAnAccount(TEST_ACCOUNT_IDENTIFIER);

    assertThat(dataSources).extracting(DataSource::getIdentifier).containsExactly("custom");
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testTraceableIsIncludedWhenIntegrationIsEnabled() {
    when(dataPointService.getAllDataPointsForAccount(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Collections.emptyList());
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(List.of(dataSource("traceable")));
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(Set.of(EnumIntegrationType.HarnessTraceable)));

    List<DataSourceDataPointsMap> dataSources =
        dataSourceServiceImpl.getDataPointsForDataSources(TEST_ACCOUNT_IDENTIFIER);

    assertThat(dataSources)
        .extracting(dataSourceDataPointsMap -> dataSourceDataPointsMap.getDataSource().getIdentifier())
        .containsExactly("traceable");
  }

  @Test
  @Owner(developers = SARABJYOT)
  @Category(UnitTests.class)
  public void testGetDataPointsForTraceableReturnsEmptyWhenIntegrationIsDisabled() {
    when(enabledIntegrationsInMemoryCache.getEnabledIntegrationTypes(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(Collections.emptySet()));

    List<DataPoint> dataPoints =
        dataSourceServiceImpl.getAllDataPointsDetailsForDataSource(TEST_ACCOUNT_IDENTIFIER, "traceable");

    assertThat(dataPoints).isEmpty();
    verify(dataPointService, never())
        .getAllDataPointsDetailsForAccountAndDataSource(TEST_ACCOUNT_IDENTIFIER, "traceable");
  }

  private DataSourceEntity dataSource(String identifier) {
    return HttpDataSourceEntity.builder()
        .name(identifier)
        .description(identifier)
        .identifier(identifier)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .build();
  }
}
