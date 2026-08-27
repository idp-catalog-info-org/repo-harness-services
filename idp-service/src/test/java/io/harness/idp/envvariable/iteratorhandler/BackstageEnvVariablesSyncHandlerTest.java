/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.iteratorhandler;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.rule.Owner;

import java.util.Arrays;
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

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageEnvVariablesSyncHandlerTest extends CategoryTest {
  private static final String TEST_ACCOUNT1 = "acc1";
  private static final String TEST_ACCOUNT2 = "acc2";
  AutoCloseable openMocks;
  @InjectMocks private BackstageEnvVariablesSyncHandler handler;
  @Mock private BackstageEnvVariableService backstageEnvVariableService;
  @Mock private NamespaceService namespaceService;
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandle() {
    List<String> accountIds = Arrays.asList(TEST_ACCOUNT1, TEST_ACCOUNT2);
    when(namespaceService.getAccountIds()).thenReturn(accountIds);
    handler.handle(IteratorEntity.builder().build());
    verify(backstageEnvVariableService).findAndSync(TEST_ACCOUNT1);
    verify(backstageEnvVariableService).findAndSync(TEST_ACCOUNT2);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testHandleThrowsException() {
    List<String> accountIds = Arrays.asList(TEST_ACCOUNT1, TEST_ACCOUNT2);
    when(namespaceService.getAccountIds()).thenReturn(accountIds);
    doThrow(new InvalidRequestException("Failed to replace secret. Code: 403"))
        .when(backstageEnvVariableService)
        .findAndSync(TEST_ACCOUNT1);
    handler.handle(IteratorEntity.builder().build());
    verify(backstageEnvVariableService).findAndSync(TEST_ACCOUNT2);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    handler.registerIterators(IteratorConfig.builder().build());
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(BackstageEnvVariablesSyncHandler.class), any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
