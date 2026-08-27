/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.iteratorhandler;

import static io.harness.rule.OwnerRule.ANKUR;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.helpers.IDPToHarnessHelper;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.rule.Owner;

import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPToHarnessEntitiesHandlerTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks private IDPToHarnessEntitiesHandler handler;
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock MongoTemplate mongoTemplate;
  @Mock IDPToHarnessHelper idpToHarnessHelper;
  @Mock io.harness.idp.metrics.IdpIteratorMetricRecorder idpIteratorMetricRecorder;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandle() {
    NamespaceEntity ns = NamespaceEntity.builder().accountIdentifier("acc1").build();
    when(mongoTemplate.find(any(), eq(NamespaceEntity.class))).thenReturn(List.of(ns));
    doNothing()
        .when(idpToHarnessHelper)
        .migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(any(NamespaceEntity.class));
    handler.handle(IteratorEntity.builder().build());
    verify(idpToHarnessHelper).migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(ns);
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("IDPToHarnessEntitiesHandler", "acc1");
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleRecordsFailureOnException() {
    NamespaceEntity ns = NamespaceEntity.builder().accountIdentifier("acc1").build();
    when(mongoTemplate.find(any(), eq(NamespaceEntity.class))).thenReturn(List.of(ns));
    doThrow(new RuntimeException("migration failed"))
        .when(idpToHarnessHelper)
        .migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(any(NamespaceEntity.class));
    handler.handle(IteratorEntity.builder().build());
    verify(idpIteratorMetricRecorder, times(1)).recordFailure("IDPToHarnessEntitiesHandler", "acc1");
    verify(idpIteratorMetricRecorder, times(0)).recordSuccess(any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleThrowsException() {
    willAnswer(invocation -> { throw new Exception("Exception Throw"); })
        .given(idpToHarnessHelper)
        .migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(NamespaceEntity.builder().build());
    handler.handle(IteratorEntity.builder().build());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    handler.registerIterators(IteratorConfig.builder().build());
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(IDPToHarnessEntitiesHandler.class), any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
