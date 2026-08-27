/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.service;

import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.rule.OwnerRule.AGNIVA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.repositories.DataPointsRepository;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.DataPointDetails;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DataPointServiceImplTest extends CategoryTest {
  private static final String accountIdentifier = "account123";
  private static final String dataSourceIdentifier = "dataSourceXYZ";
  private static final String dataPointIdentifier = "testDataPoint";
  private static final String datapointentityidentifier = "identifier123";
  private static final String datasourcelocationidA = "locationA";
  private static final String datasourcelocationidB = "locationB";
  private static final List<String> identifiers = Arrays.asList(datapointentityidentifier, "identifier456");

  @Mock DataPointsRepository dataPointsRepository;
  @Mock DataSourceRepository dataSourceRepository;
  @Mock DataSourceLocationRepository dataSourceLocationRepository;
  @InjectMocks DataPointServiceImpl dataPointServiceImpl;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetAllDataPointsDetailsForAccountAndDataSource() {
    DataPointEntity dataPointEntity1 = createDataPointEntity(accountIdentifier, datapointentityidentifier,
        "Sample Name", DataPointEntity.Type.STRING, "Sample Description", dataSourceIdentifier, datasourcelocationidA);

    when(dataPointsRepository.findAllByAccountIdentifierInAndDataSourceIdentifier(
             addGlobalAccountIdentifierAlong(accountIdentifier), dataSourceIdentifier))
        .thenReturn(Collections.singletonList(dataPointEntity1));

    List<DataPoint> actualDataPoints =
        dataPointServiceImpl.getAllDataPointsDetailsForAccountAndDataSource(accountIdentifier, dataSourceIdentifier);

    assertEquals(datapointentityidentifier, actualDataPoints.get(0).getDataPointIdentifier());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDslDataPointsInfo() {
    DataPointEntity dataPointEntity1 = createDataPointEntity(accountIdentifier, datapointentityidentifier,
        "Sample Name", DataPointEntity.Type.STRING, "Sample Description", dataSourceIdentifier, datasourcelocationidA);

    DataPointEntity dataPointEntity2 = createDataPointEntity(accountIdentifier, "identifier456", "Another Name",
        DataPointEntity.Type.NUMBER, "Another Description", dataSourceIdentifier, datasourcelocationidB);

    when(dataPointsRepository.findByAccountIdentifierInAndDataSourceIdentifierAndIdentifierIn(
             addGlobalAccountIdentifierAlong(accountIdentifier), dataSourceIdentifier, identifiers))
        .thenReturn(Arrays.asList(dataPointEntity1, dataPointEntity2));

    Map<String, List<DataPointEntity>> actualDataPointsInfo =
        dataPointServiceImpl.getDslDataPointsInfo(accountIdentifier, identifiers, dataSourceIdentifier);

    assertEquals(2, actualDataPointsInfo.size());
    assertTrue(actualDataPointsInfo.containsKey(datasourcelocationidA));
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDslDataPointsInfoWithDataFetchDTO() {
    DataPointEntity dataPointEntity1 = createDataPointEntity(accountIdentifier, datapointentityidentifier,
        "Sample Name", DataPointEntity.Type.STRING, "Sample Description", dataSourceIdentifier, datasourcelocationidA);

    DataPointEntity dataPointEntity2 = createDataPointEntity(accountIdentifier, "identifier456", "Another Name",
        DataPointEntity.Type.NUMBER, "Another Description", dataSourceIdentifier, datapointentityidentifier);

    DataFetchDTO dataFetchDTO1 = createDataFetchDTO("rule1", dataPointEntity1, Collections.emptyList());
    DataFetchDTO dataFetchDTO2 = createDataFetchDTO("rule2", dataPointEntity2, Collections.emptyList());

    when(dataPointsRepository.findByAccountIdentifierInAndDataSourceIdentifierAndIdentifierIn(any(), any(), any()))
        .thenReturn(Arrays.asList(dataPointEntity1, dataPointEntity2));

    Map<String, List<DataFetchDTO>> actualDslDataPointsInfo = dataPointServiceImpl.getDslDataPointsInfo(
        accountIdentifier, dataSourceIdentifier, Arrays.asList(dataFetchDTO1, dataFetchDTO2));

    assertEquals(2, actualDslDataPointsInfo.size());
    assertTrue(actualDslDataPointsInfo.containsKey(datasourcelocationidA));
    assertTrue(actualDslDataPointsInfo.containsKey(datapointentityidentifier));
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDataPointSuccess() {
    DataPointEntity dataPointEntity1 = createDataPointEntity(accountIdentifier, datapointentityidentifier,
        "Sample Name", DataPointEntity.Type.STRING, "Sample Description", dataSourceIdentifier, datasourcelocationidA);

    when(dataPointsRepository.findByAccountIdentifierInAndDataSourceIdentifierAndIdentifier(
             anySet(), anyString(), anyString()))
        .thenReturn(Optional.of(dataPointEntity1));

    DataPointEntity result =
        dataPointServiceImpl.getDataPoint(accountIdentifier, dataSourceIdentifier, dataPointIdentifier);
    assertEquals(dataPointEntity1, result);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testgetAllDataPointsForAccount() {
    DataPointEntity dataPointEntity1 = createDataPointEntity(accountIdentifier, datapointentityidentifier,
        "Sample Name", DataPointEntity.Type.STRING, "Sample Description", dataSourceIdentifier, datasourcelocationidA);

    when(dataPointsRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(accountIdentifier)))
        .thenReturn(Collections.singletonList(dataPointEntity1));

    List<DataPointEntity> actualDataPoints = dataPointServiceImpl.getAllDataPointsForAccount(accountIdentifier);
    assertEquals(1, actualDataPoints.size());
    assertEquals(dataPointEntity1, actualDataPoints.get(0));
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDataPointsMap() {
    DataPointEntity dataPointEntity1 = createDataPointEntity(accountIdentifier, datapointentityidentifier,
        "Sample Name", DataPointEntity.Type.STRING, "Sample Description", dataSourceIdentifier, datasourcelocationidA);

    when(dataPointsRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(accountIdentifier)))
        .thenReturn(Collections.singletonList(dataPointEntity1));

    Map<String, DataPoint> actualDataPointsMap = dataPointServiceImpl.getDataPointsMap(accountIdentifier);
    assertEquals(1, actualDataPointsMap.size());
    assertTrue(actualDataPointsMap.containsKey(dataSourceIdentifier + "." + datapointentityidentifier));
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDataPointNotFound() {
    when(dataPointsRepository.findByAccountIdentifierInAndDataSourceIdentifierAndIdentifier(
             anySet(), anyString(), anyString()))
        .thenThrow(InvalidRequestException.class);
    try {
      dataPointServiceImpl.getDataPoint(accountIdentifier, dataSourceIdentifier, dataPointIdentifier);
    } catch (InvalidRequestException e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testgetDataPointwithEmptyDataPoint() {
    when(dataPointsRepository.findByAccountIdentifierInAndDataSourceIdentifierAndIdentifier(
             anySet(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    try {
      dataPointServiceImpl.getDataPoint(accountIdentifier, dataSourceIdentifier, dataPointIdentifier);
    } catch (InvalidRequestException e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testCreateGlobalDataPoint() {
    DataPointDetails details = createDataPointDetails();
    mockValidReferences();
    when(dataPointsRepository.findByAccountIdentifierInAndDataSourceIdentifierAndIdentifier(
             Collections.singleton(GLOBAL_ACCOUNT_ID), dataSourceIdentifier, dataPointIdentifier))
        .thenReturn(Optional.empty());

    dataPointServiceImpl.createGlobalDataPoint(dataSourceIdentifier, details);

    ArgumentCaptor<DataPointEntity> captor = ArgumentCaptor.forClass(DataPointEntity.class);
    verify(dataPointsRepository).save(captor.capture());
    assertEquals(GLOBAL_ACCOUNT_ID, captor.getValue().getAccountIdentifier());
    assertEquals(dataSourceIdentifier, captor.getValue().getDataSourceIdentifier());
    assertEquals(dataPointIdentifier, captor.getValue().getIdentifier());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testUpdateGlobalDataPointPreservesIdentity() {
    DataPointDetails details = createDataPointDetails();
    mockValidReferences();
    DataPointEntity existing = createDataPointEntity(GLOBAL_ACCOUNT_ID, dataPointIdentifier, "old name",
        DataPointEntity.Type.STRING, "old description", dataSourceIdentifier, datasourcelocationidA);
    existing.setId("existing-id");
    when(dataPointsRepository.findByAccountIdentifierInAndDataSourceIdentifierAndIdentifier(
             Collections.singleton(GLOBAL_ACCOUNT_ID), dataSourceIdentifier, dataPointIdentifier))
        .thenReturn(Optional.of(existing));

    dataPointServiceImpl.updateGlobalDataPoint(dataSourceIdentifier, dataPointIdentifier, details);

    assertEquals("existing-id", existing.getId());
    assertEquals("updated name", existing.getName());
    verify(dataPointsRepository).save(existing);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testUpdateGlobalDataPointRejectsIdentifierChange() {
    DataPointDetails details = createDataPointDetails();
    details.setIdentifier("different-identifier");

    dataPointServiceImpl.updateGlobalDataPoint(dataSourceIdentifier, dataPointIdentifier, details);
  }

  private void mockValidReferences() {
    when(dataSourceRepository.findByAccountIdentifierInAndIdentifier(
             Collections.singleton(GLOBAL_ACCOUNT_ID), dataSourceIdentifier))
        .thenReturn(Optional.of(mock(DataSourceEntity.class)));
    DataSourceLocationEntity location = mock(DataSourceLocationEntity.class);
    when(location.getDataSourceIdentifier()).thenReturn(dataSourceIdentifier);
    when(dataSourceLocationRepository.findByIdentifier(datasourcelocationidA)).thenReturn(location);
  }

  private DataPointDetails createDataPointDetails() {
    return new DataPointDetails()
        .identifier(dataPointIdentifier)
        .name("updated name")
        .type(DataPointDetails.TypeEnum.STRING)
        .description("updated description")
        .detailedDescription("updated detailed description")
        .dataSourceLocationIdentifier(datasourcelocationidA);
  }

  private DataPointEntity createDataPointEntity(String accountId, String identifier, String name,
      DataPointEntity.Type type, String description, String dataSourceId, String dataSourceLocationId) {
    return DataPointEntity.builder()
        .accountIdentifier(accountId)
        .identifier(identifier)
        .name(name)
        .type(type)
        .description(description)
        .dataSourceIdentifier(dataSourceId)
        .dataSourceLocationIdentifier(dataSourceLocationId)
        .build();
  }
  private DataFetchDTO createDataFetchDTO(
      String ruleIdentifier, DataPointEntity dataPointEntity, List<InputValue> inputValues) {
    return DataFetchDTO.builder()
        .ruleIdentifier(ruleIdentifier)
        .dataPoint(dataPointEntity)
        .inputValues(inputValues)
        .build();
  }
}
