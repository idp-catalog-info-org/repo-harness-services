/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.iteratorhandler;

import static io.harness.rule.OwnerRule.NISARG;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.iterator.PersistenceIteratorFactory;
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
import org.springframework.data.mongodb.core.MongoTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstagePermissionsSyncHandlerTest extends CategoryTest {
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock MongoTemplate mongoTemplate;
  @Mock BackstagePermissionsService backstagePermissionsService;
  @Mock NamespaceService namespaceService;
  @InjectMocks BackstagePermissionsSyncHandler backstagePermissionsSyncHandler;

  static final String TEST_ACCOUNT_1 = "account1";
  static final String TEST_ACCOUNT_2 = "account2";
  AutoCloseable openMocks;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHandleWithMultipleAccounts() {
    List<String> accountIds = Arrays.asList(TEST_ACCOUNT_1, TEST_ACCOUNT_2);
    when(namespaceService.getAccountIds()).thenReturn(accountIds);
    doNothing().when(backstagePermissionsService).findAndSyncPermissions(any());

    IteratorEntity entity = IteratorEntity.builder().name("BackstagePermissionsSyncHandler").build();
    backstagePermissionsSyncHandler.handle(entity);

    verify(namespaceService, times(1)).getAccountIds();
    verify(backstagePermissionsService, times(1)).findAndSyncPermissions(TEST_ACCOUNT_1);
    verify(backstagePermissionsService, times(1)).findAndSyncPermissions(TEST_ACCOUNT_2);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHandleWithException() {
    List<String> accountIds = Arrays.asList(TEST_ACCOUNT_1, TEST_ACCOUNT_2);
    when(namespaceService.getAccountIds()).thenReturn(accountIds);
    doThrow(new RuntimeException("Error syncing permissions"))
        .when(backstagePermissionsService)
        .findAndSyncPermissions(TEST_ACCOUNT_1);
    doNothing().when(backstagePermissionsService).findAndSyncPermissions(TEST_ACCOUNT_2);

    IteratorEntity entity = IteratorEntity.builder().name("BackstagePermissionsSyncHandler").build();
    backstagePermissionsSyncHandler.handle(entity);

    verify(namespaceService, times(1)).getAccountIds();
    verify(backstagePermissionsService, times(1)).findAndSyncPermissions(TEST_ACCOUNT_1);
    verify(backstagePermissionsService, times(1)).findAndSyncPermissions(TEST_ACCOUNT_2);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testHandleWithEmptyAccountList() {
    List<String> accountIds = Arrays.asList();
    when(namespaceService.getAccountIds()).thenReturn(accountIds);

    IteratorEntity entity = IteratorEntity.builder().name("BackstagePermissionsSyncHandler").build();
    backstagePermissionsSyncHandler.handle(entity);

    verify(namespaceService, times(1)).getAccountIds();
    verify(backstagePermissionsService, times(0)).findAndSyncPermissions(any());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
