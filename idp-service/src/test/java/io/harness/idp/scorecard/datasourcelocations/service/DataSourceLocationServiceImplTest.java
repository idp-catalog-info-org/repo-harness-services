/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.rule.OwnerRule.AGNIVA;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.datasourcelocations.entity.CatalogDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HQLDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.repositories.DataSourceLocationRepository;
import io.harness.idp.scorecard.datasources.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataSourceLocationDetails;

import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DataSourceLocationServiceImplTest extends CategoryTest {
  private static final String DATA_SOURCE_IDENTIFIER = "traceable";
  private static final String LOCATION_IDENTIFIER = "hql_traceable_risk_score";
  private static final String HQL_TEMPLATE = "find view idp:traceable_api_matches";
  private static final String JEXL = "catalog.spec.owner != null";

  @Mock DataSourceLocationRepository dataSourceLocationRepository;
  @Mock DataSourceRepository dataSourceRepository;
  @InjectMocks DataSourceLocationServiceImpl dataSourceLocationServiceImpl;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testCreateGlobalHqlDataSourceLocation() {
    DataSourceLocationDetails details = createHqlDetails();
    mockValidDataSource();
    when(dataSourceLocationRepository.findByIdentifier(LOCATION_IDENTIFIER)).thenReturn(null);

    dataSourceLocationServiceImpl.createGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, details);

    ArgumentCaptor<DataSourceLocationEntity> captor = ArgumentCaptor.forClass(DataSourceLocationEntity.class);
    verify(dataSourceLocationRepository).save(captor.capture());
    HQLDataSourceLocationEntity saved = (HQLDataSourceLocationEntity) captor.getValue();
    assertEquals(GLOBAL_ACCOUNT_ID, saved.getAccountIdentifier());
    assertEquals(DATA_SOURCE_IDENTIFIER, saved.getDataSourceIdentifier());
    assertEquals(LOCATION_IDENTIFIER, saved.getIdentifier());
    assertEquals(HQL_TEMPLATE, saved.getHqlTemplate());
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testCreateGlobalCatalogDataSourceLocation() {
    DataSourceLocationDetails details = createCatalogDetails();
    mockValidDataSource();
    when(dataSourceLocationRepository.findByIdentifier(LOCATION_IDENTIFIER)).thenReturn(null);

    dataSourceLocationServiceImpl.createGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, details);

    ArgumentCaptor<DataSourceLocationEntity> captor = ArgumentCaptor.forClass(DataSourceLocationEntity.class);
    verify(dataSourceLocationRepository).save(captor.capture());
    CatalogDataSourceLocationEntity saved = (CatalogDataSourceLocationEntity) captor.getValue();
    assertEquals(JEXL, saved.getJexl());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testCreateGlobalDataSourceLocationRejectsDuplicate() {
    DataSourceLocationDetails details = createHqlDetails();
    mockValidDataSource();
    HQLDataSourceLocationEntity existing = new HQLDataSourceLocationEntity();
    existing.setAccountIdentifier(GLOBAL_ACCOUNT_ID);
    existing.setDataSourceIdentifier(DATA_SOURCE_IDENTIFIER);
    when(dataSourceLocationRepository.findByIdentifier(LOCATION_IDENTIFIER)).thenReturn(existing);

    dataSourceLocationServiceImpl.createGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, details);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testUpdateGlobalHqlDataSourceLocation() {
    DataSourceLocationDetails details = createHqlDetails().hqlTemplate("updated template");
    mockValidDataSource();
    HQLDataSourceLocationEntity existing = new HQLDataSourceLocationEntity();
    existing.setId("existing-id");
    existing.setAccountIdentifier(GLOBAL_ACCOUNT_ID);
    existing.setIdentifier(LOCATION_IDENTIFIER);
    existing.setDataSourceIdentifier(DATA_SOURCE_IDENTIFIER);
    existing.setHqlTemplate(HQL_TEMPLATE);
    when(dataSourceLocationRepository.findByIdentifier(LOCATION_IDENTIFIER)).thenReturn(existing);

    dataSourceLocationServiceImpl.updateGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, LOCATION_IDENTIFIER, details);

    assertEquals("existing-id", existing.getId());
    assertEquals("updated template", existing.getHqlTemplate());
    verify(dataSourceLocationRepository).save(existing);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testUpdateGlobalDataSourceLocationRejectsIdentifierChange() {
    DataSourceLocationDetails details = createHqlDetails().identifier("different-identifier");

    dataSourceLocationServiceImpl.updateGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, LOCATION_IDENTIFIER, details);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testUpdateGlobalDataSourceLocationRejectsTypeChange() {
    DataSourceLocationDetails details = createCatalogDetails();
    mockValidDataSource();
    HQLDataSourceLocationEntity existing = new HQLDataSourceLocationEntity();
    existing.setAccountIdentifier(GLOBAL_ACCOUNT_ID);
    existing.setIdentifier(LOCATION_IDENTIFIER);
    existing.setDataSourceIdentifier(DATA_SOURCE_IDENTIFIER);
    when(dataSourceLocationRepository.findByIdentifier(LOCATION_IDENTIFIER)).thenReturn(existing);

    dataSourceLocationServiceImpl.updateGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, LOCATION_IDENTIFIER, details);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testCreateGlobalDataSourceLocationRejectsMissingHqlTemplate() {
    DataSourceLocationDetails details = createHqlDetails().hqlTemplate(" ");
    mockValidDataSource();

    dataSourceLocationServiceImpl.createGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, details);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testCreateGlobalDataSourceLocationRejectsJexlForHql() {
    DataSourceLocationDetails details = createHqlDetails().jexl(JEXL);
    mockValidDataSource();

    dataSourceLocationServiceImpl.createGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, details);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testCreateGlobalDataSourceLocationRejectsMissingJexlForCatalog() {
    DataSourceLocationDetails details = createCatalogDetails().jexl(" ");
    mockValidDataSource();

    dataSourceLocationServiceImpl.createGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, details);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testCreateGlobalDataSourceLocationRejectsHqlTemplateForCatalog() {
    DataSourceLocationDetails details = createCatalogDetails().hqlTemplate(HQL_TEMPLATE);
    mockValidDataSource();

    dataSourceLocationServiceImpl.createGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, details);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testUpdateGlobalDataSourceLocationNotFound() {
    DataSourceLocationDetails details = createHqlDetails();
    mockValidDataSource();
    when(dataSourceLocationRepository.findByIdentifier(LOCATION_IDENTIFIER)).thenReturn(null);

    dataSourceLocationServiceImpl.updateGlobalDataSourceLocation(DATA_SOURCE_IDENTIFIER, LOCATION_IDENTIFIER, details);
  }

  private void mockValidDataSource() {
    when(dataSourceRepository.findByAccountIdentifierInAndIdentifier(
             Collections.singleton(GLOBAL_ACCOUNT_ID), DATA_SOURCE_IDENTIFIER))
        .thenReturn(Optional.of(mock(DataSourceEntity.class)));
  }

  private DataSourceLocationDetails createHqlDetails() {
    return new DataSourceLocationDetails()
        .identifier(LOCATION_IDENTIFIER)
        .type(DataSourceLocationDetails.TypeEnum.HQL)
        .hqlTemplate(HQL_TEMPLATE);
  }

  private DataSourceLocationDetails createCatalogDetails() {
    return new DataSourceLocationDetails()
        .identifier(LOCATION_IDENTIFIER)
        .type(DataSourceLocationDetails.TypeEnum.CATALOG)
        .jexl(JEXL);
  }
}
