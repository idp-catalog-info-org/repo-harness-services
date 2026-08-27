/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.iteratorhandler;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doNothing;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.helpers.VerificationHelper;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.metrics.IdpIteratorMetricRecorder;
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
public class CatalogEntitiesVerificationHandlerTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks private CatalogEntitiesVerificationHandler handler;
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock MongoTemplate mongoTemplate;
  @Mock VerificationHelper verificationHelper;
  @Mock IdpIteratorMetricRecorder idpIteratorMetricRecorder;
  private static final String TEST_ACCOUNT = "test-account";
  private static final String TEST_ACCOUNT2 = "test-account-2";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandle() {
    when(mongoTemplate.find(any(), eq(NamespaceEntity.class)))
        .thenReturn(List.of(NamespaceEntity.builder().accountIdentifier(TEST_ACCOUNT).build()));
    doNothing().when(verificationHelper).verifyHarnessAndIDPEntities(TEST_ACCOUNT);
    handler.handle(IteratorEntity.builder().build());
    verify(verificationHelper).verifyHarnessAndIDPEntities(TEST_ACCOUNT);
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("CatalogEntitiesVerificationHandler", TEST_ACCOUNT);
    verify(idpIteratorMetricRecorder, times(0)).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleWithMultipleAccounts() {
    when(mongoTemplate.find(any(), eq(NamespaceEntity.class)))
        .thenReturn(List.of(NamespaceEntity.builder().accountIdentifier(TEST_ACCOUNT).build(),
            NamespaceEntity.builder().accountIdentifier(TEST_ACCOUNT2).build()));
    doNothing().when(verificationHelper).verifyHarnessAndIDPEntities(any());
    handler.handle(IteratorEntity.builder().build());
    verify(verificationHelper, times(1)).verifyHarnessAndIDPEntities(TEST_ACCOUNT);
    verify(verificationHelper, times(1)).verifyHarnessAndIDPEntities(TEST_ACCOUNT2);
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("CatalogEntitiesVerificationHandler", TEST_ACCOUNT);
    verify(idpIteratorMetricRecorder, times(1)).recordSuccess("CatalogEntitiesVerificationHandler", TEST_ACCOUNT2);
    verify(idpIteratorMetricRecorder, times(0)).recordFailure(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testHandleFailure() {
    when(mongoTemplate.find(any(), eq(NamespaceEntity.class)))
        .thenReturn(List.of(NamespaceEntity.builder().accountIdentifier(TEST_ACCOUNT).build()));
    doThrow(new RuntimeException("Verification failed"))
        .when(verificationHelper)
        .verifyHarnessAndIDPEntities(TEST_ACCOUNT);
    try {
      handler.handle(IteratorEntity.builder().build());
    } catch (RuntimeException e) {
      // Expected
    }
    verify(idpIteratorMetricRecorder, times(1)).recordFailure("CatalogEntitiesVerificationHandler", TEST_ACCOUNT);
    verify(idpIteratorMetricRecorder, times(0)).recordSuccess(any(), any());
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    handler.registerIterators(IteratorConfig.builder().build());
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(CatalogEntitiesVerificationHandler.class), any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
