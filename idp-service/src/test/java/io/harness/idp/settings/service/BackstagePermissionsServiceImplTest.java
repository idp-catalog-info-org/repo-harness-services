/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.settings.service;

import static io.harness.rule.OwnerRule.AGNIVA;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.settings.beans.entity.BackstagePermissionsEntity;
import io.harness.idp.settings.mappers.BackstagePermissionsMapper;
import io.harness.idp.settings.repositories.BackstagePermissionsRepository;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstagePermissionsServiceImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "accountId";
  static final String TEST_NAMESPACE = "namespace";
  static final List<String> TEST_USERGROUP = List.of("IDP-ADMIN");
  static final List<String> TEST_PERMISSIONS =
      List.of("user_read", "user_update", "user_delete", "owner_read", "owner_update", "owner_delete", "all_create");
  AutoCloseable openMocks;
  @Mock BackstagePermissionsRepository backstagePermissionsRepository;
  @Mock NamespaceService namespaceService;
  @InjectMocks BackstagePermissionsServiceImpl backstagePermissionsServiceImpl;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private BackstageEnvVariableService backstageEnvVariableService;
  @Mock private OutboxService outboxService;
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifier() {
    BackstagePermissionsEntity backstagePermissionsEntity = BackstagePermissionsEntity.builder().build();
    when(backstagePermissionsRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(backstagePermissionsEntity));
    Optional<BackstagePermissions> backstagePermissionsOpt =
        backstagePermissionsServiceImpl.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    assertTrue(backstagePermissionsOpt.isPresent());
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testUpdatePermissions() {
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));

    BackstagePermissionsEntity oldbackstagePermissionsEntity = BackstagePermissionsEntity.builder().build();
    when(backstagePermissionsRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(oldbackstagePermissionsEntity));
    mockAccountNamespaceMapping();
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    backstagePermissions.setUserGroups(TEST_USERGROUP);
    BackstagePermissionsEntity backstagePermissionsEntity =
        BackstagePermissionsMapper.fromDTO(backstagePermissions, TEST_ACCOUNT_IDENTIFIER);
    when(backstagePermissionsRepository.update(any())).thenReturn(backstagePermissionsEntity);

    assertEquals(backstagePermissions,
        backstagePermissionsServiceImpl.updatePermissions(backstagePermissions, TEST_ACCOUNT_IDENTIFIER));

    Map<String, String> data = new HashMap<>();
    data.put("PERMISSIONS", String.join(",", TEST_PERMISSIONS));
    data.put("USERGROUP", String.join(",", TEST_USERGROUP));
    verify(backstageEnvVariableService).createOrUpdate(any(), eq(TEST_ACCOUNT_IDENTIFIER));
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testCreatePermissions() {
    mockAccountNamespaceMapping();
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    backstagePermissions.setUserGroups(TEST_USERGROUP);
    BackstagePermissionsEntity backstagePermissionsEntity =
        BackstagePermissionsMapper.fromDTO(backstagePermissions, TEST_ACCOUNT_IDENTIFIER);
    when(backstagePermissionsRepository.save(any())).thenReturn(backstagePermissionsEntity);
    when(outboxService.save(any())).thenReturn(null);
    assertEquals(backstagePermissions,
        backstagePermissionsServiceImpl.createPermissions(backstagePermissions, TEST_ACCOUNT_IDENTIFIER));

    Map<String, String> data = new HashMap<>();
    data.put("PERMISSIONS", String.join(",", TEST_PERMISSIONS));
    data.put("USERGROUP", String.join(",", TEST_USERGROUP));
    verify(backstageEnvVariableService).createOrUpdate(any(), eq(TEST_ACCOUNT_IDENTIFIER));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllBackstagePermissions() {
    when(backstagePermissionsRepository.findAll()).thenReturn(List.of(BackstagePermissionsEntity.builder().build()));
    Iterable<BackstagePermissionsEntity> backstagePermissionsEntityList =
        backstagePermissionsServiceImpl.getAllBackstagePermissions();
    assertNotNull(backstagePermissionsEntityList);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSavePermissions() {
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    backstagePermissions.setUserGroups(TEST_USERGROUP);
    BackstagePermissionsEntity backstagePermissionsEntity =
        BackstagePermissionsMapper.fromDTO(backstagePermissions, TEST_ACCOUNT_IDENTIFIER);

    when(backstagePermissionsRepository.save(any())).thenReturn(backstagePermissionsEntity);
    mockAccountNamespaceMapping();
    Map<String, String> data = new HashMap<>();
    data.put("PERMISSIONS", String.join(",", TEST_PERMISSIONS));
    data.put("USERGROUP", String.join(",", TEST_USERGROUP));

    backstagePermissionsServiceImpl.savePermission(backstagePermissionsEntity);
    verify(backstageEnvVariableService).createOrUpdate(any(), eq(TEST_ACCOUNT_IDENTIFIER));
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testSavePermissionswithoutUserGroupswithoutConfigPermissions() {
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    List<String> empty = List.of();
    backstagePermissions.setPermissions(empty);
    backstagePermissions.setUserGroups(empty);
    BackstagePermissionsEntity backstagePermissionsEntity =
        BackstagePermissionsMapper.fromDTO(backstagePermissions, TEST_ACCOUNT_IDENTIFIER);

    when(backstagePermissionsRepository.save(any())).thenReturn(backstagePermissionsEntity);
    mockAccountNamespaceMapping();
    Map<String, String> data = new HashMap<>();
    data.put("PERMISSIONS", " ");
    data.put("USERGROUP", " ");

    backstagePermissionsServiceImpl.savePermission(backstagePermissionsEntity);
    verify(backstageEnvVariableService).createOrUpdate(any(), eq(TEST_ACCOUNT_IDENTIFIER));
  }

  @Test
  @Category(UnitTests.class)
  @Owner(developers = AGNIVA)
  public void testFindAsyncPermissions() {
    BackstagePermissions backstagePermissions = new BackstagePermissions();
    backstagePermissions.setPermissions(TEST_PERMISSIONS);
    backstagePermissions.setUserGroups(TEST_USERGROUP);
    BackstagePermissionsEntity expectedEntity =
        BackstagePermissionsMapper.fromDTO(backstagePermissions, TEST_ACCOUNT_IDENTIFIER);
    when(backstagePermissionsRepository.save(any())).thenReturn(expectedEntity);
    mockAccountNamespaceMapping();
    backstagePermissionsServiceImpl.findAndSyncPermissions(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, expectedEntity.getAccountIdentifier());
    assertEquals(TEST_PERMISSIONS, expectedEntity.getPermissions());
    assertEquals(TEST_USERGROUP, expectedEntity.getUserGroups());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private void mockAccountNamespaceMapping() {
    NamespaceInfo namespaceInfo = new NamespaceInfo();
    namespaceInfo.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    namespaceInfo.setNamespace(TEST_NAMESPACE);
    when(namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(namespaceInfo);
  }
}
