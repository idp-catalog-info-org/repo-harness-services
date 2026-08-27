/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.iteratorhandler;

import static io.harness.rule.OwnerRule.NISARG;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.license.usage.service.IDPModuleLicenseUsage;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersSyncHandlerTest extends CategoryTest {
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock MongoTemplate mongoTemplate;
  @Mock IDPModuleLicenseUsage idpModuleLicenseUsage;

  ActiveDevelopersSyncHandler handler;
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    handler = new ActiveDevelopersSyncHandler(persistenceIteratorFactory, mongoTemplate, idpModuleLicenseUsage);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHandle_Success() {
    IteratorEntity entity = IteratorEntity.builder().name("ActiveDevelopersSyncHandler").build();

    doNothing().when(idpModuleLicenseUsage).populateActiveDevelopersDataForSync();

    handler.handle(entity);

    verify(idpModuleLicenseUsage, times(1)).populateActiveDevelopersDataForSync();
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHandle_Exception() {
    IteratorEntity entity = IteratorEntity.builder().name("ActiveDevelopersSyncHandler").build();

    doThrow(new RuntimeException("Test exception")).when(idpModuleLicenseUsage).populateActiveDevelopersDataForSync();

    handler.handle(entity);

    verify(idpModuleLicenseUsage, times(1)).populateActiveDevelopersDataForSync();
  }
}
