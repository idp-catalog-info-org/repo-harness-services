/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.service;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.k8s.client.K8sClient;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.repositories.NamespaceRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;
import io.harness.spec.server.idp.v1.model.NamespaceMetadata;
import io.harness.spec.server.idp.v1.model.NamespaceRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class NamespaceServiceImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccountId";
  static final String TEST_NAMESPACE = "testNamespace";
  static final String DEPLOYMENT_TYPE_SMP = "SMP";
  static final String DEPLOYMENT_TYPE_SAAS = "SAAS";
  static final String DEPLOYMENT_NAMESPACE = "deploymentNamespace";

  @Mock NamespaceRepository namespaceRepository;

  @Mock K8sClient k8sClient;

  NamespaceServiceImpl namespaceService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    namespaceService =
        new NamespaceServiceImpl(namespaceRepository, k8sClient, DEPLOYMENT_TYPE_SAAS, DEPLOYMENT_NAMESPACE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetNamespaceForAccountIdentifier() {
    NamespaceEntity entity =
        NamespaceEntity.builder().id(TEST_NAMESPACE).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).build();
    when(namespaceRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.of(entity));

    NamespaceInfo result = namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    assertNotNull(result);
    assertEquals(TEST_NAMESPACE, result.getNamespace());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, result.getAccountIdentifier());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetNamespaceForAccountIdentifierNotFound() {
    when(namespaceRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());

    namespaceService.getNamespaceForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAccountIdForNamespace() {
    NamespaceEntity entity =
        NamespaceEntity.builder().id(TEST_NAMESPACE).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).build();
    when(namespaceRepository.findById(TEST_NAMESPACE)).thenReturn(Optional.of(entity));

    NamespaceInfo result = namespaceService.getAccountIdForNamespace(TEST_NAMESPACE);

    assertNotNull(result);
    assertEquals(TEST_NAMESPACE, result.getNamespace());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, result.getAccountIdentifier());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAccountIdForNamespaceNotFound() {
    when(namespaceRepository.findById(TEST_NAMESPACE)).thenReturn(Optional.empty());

    namespaceService.getAccountIdForNamespace(TEST_NAMESPACE);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateWithMetadataChange() {
    NamespaceEntity existingEntity =
        NamespaceEntity.builder()
            .id(TEST_NAMESPACE)
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .metadata(NamespaceEntity.Metadata.builder().postgresIdpV2MigrationCompleted(false).build())
            .build();
    when(namespaceRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.of(existingEntity));
    when(namespaceRepository.save(any(NamespaceEntity.class))).thenReturn(existingEntity);

    NamespaceRequest request = new NamespaceRequest();
    NamespaceMetadata metadata = new NamespaceMetadata();
    metadata.setPostgresIdpV2MigrationCompleted(true);
    request.setMetadata(metadata);

    NamespaceInfo result = namespaceService.update(TEST_ACCOUNT_IDENTIFIER, request);

    assertNotNull(result);
    verify(namespaceRepository).save(any(NamespaceEntity.class));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateWithNoMetadataChange() {
    NamespaceEntity existingEntity =
        NamespaceEntity.builder()
            .id(TEST_NAMESPACE)
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .metadata(NamespaceEntity.Metadata.builder().postgresIdpV2MigrationCompleted(true).build())
            .build();
    when(namespaceRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.of(existingEntity));

    NamespaceRequest request = new NamespaceRequest();
    NamespaceMetadata metadata = new NamespaceMetadata();
    metadata.setPostgresIdpV2MigrationCompleted(true);
    request.setMetadata(metadata);

    NamespaceInfo result = namespaceService.update(TEST_ACCOUNT_IDENTIFIER, request);

    assertNotNull(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateWithNullMetadata() {
    NamespaceEntity existingEntity =
        NamespaceEntity.builder().id(TEST_NAMESPACE).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).metadata(null).build();
    when(namespaceRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.of(existingEntity));
    when(namespaceRepository.save(any(NamespaceEntity.class))).thenReturn(existingEntity);

    NamespaceRequest request = new NamespaceRequest();
    NamespaceMetadata metadata = new NamespaceMetadata();
    metadata.setPostgresIdpV2MigrationCompleted(true);
    request.setMetadata(metadata);

    NamespaceInfo result = namespaceService.update(TEST_ACCOUNT_IDENTIFIER, request);

    assertNotNull(result);
    verify(namespaceRepository).save(any(NamespaceEntity.class));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateNotFound() {
    when(namespaceRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.empty());

    NamespaceRequest request = new NamespaceRequest();
    request.setMetadata(new NamespaceMetadata());

    namespaceService.update(TEST_ACCOUNT_IDENTIFIER, request);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAccountIds() {
    List<NamespaceEntity> entities = new ArrayList<>();
    entities.add(NamespaceEntity.builder().accountIdentifier("account1").build());
    entities.add(NamespaceEntity.builder().accountIdentifier("account2").build());
    when(namespaceRepository.findAllByIsDeleted(false)).thenReturn(entities);

    List<String> result = namespaceService.getAccountIds();

    assertNotNull(result);
    assertEquals(2, result.size());
    assertTrue(result.contains("account1"));
    assertTrue(result.contains("account2"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAccountIdpStatusTrue() {
    NamespaceEntity entity =
        NamespaceEntity.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).isDeleted(false).build();
    when(namespaceRepository.findByAccountIdentifierAndIsDeleted(TEST_ACCOUNT_IDENTIFIER, false))
        .thenReturn(Optional.of(entity));

    Boolean result = namespaceService.getAccountIdpStatus(TEST_ACCOUNT_IDENTIFIER);

    assertTrue(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetAccountIdpStatusFalse() {
    when(namespaceRepository.findByAccountIdentifierAndIsDeleted(TEST_ACCOUNT_IDENTIFIER, false))
        .thenReturn(Optional.empty());

    Boolean result = namespaceService.getAccountIdpStatus(TEST_ACCOUNT_IDENTIFIER);

    assertFalse(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateDevSpaceEnvDefaultMappingEntryNew() {
    when(namespaceRepository.findByAccountIdentifierAndId(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE)).thenReturn(null);
    NamespaceEntity savedEntity =
        NamespaceEntity.builder().id(TEST_NAMESPACE).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).build();
    when(namespaceRepository.save(any(NamespaceEntity.class))).thenReturn(savedEntity);

    NamespaceEntity result =
        namespaceService.createDevSpaceEnvDefaultMappingEntry(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE);

    assertNotNull(result);
    assertEquals(TEST_NAMESPACE, result.getId());
    verify(namespaceRepository).save(any(NamespaceEntity.class));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testCreateDevSpaceEnvDefaultMappingEntryExisting() {
    NamespaceEntity existingEntity =
        NamespaceEntity.builder().id(TEST_NAMESPACE).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).build();
    when(namespaceRepository.findByAccountIdentifierAndId(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE))
        .thenReturn(existingEntity);

    NamespaceEntity result =
        namespaceService.createDevSpaceEnvDefaultMappingEntry(TEST_ACCOUNT_IDENTIFIER, TEST_NAMESPACE);

    assertNotNull(result);
    assertEquals(existingEntity, result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetActiveAccounts() {
    List<NamespaceEntity> entities = new ArrayList<>();
    entities.add(NamespaceEntity.builder().accountIdentifier("account1").isDeleted(false).build());
    entities.add(NamespaceEntity.builder().accountIdentifier("account2").isDeleted(false).build());
    when(namespaceRepository.findAllByIsDeleted(false)).thenReturn(entities);

    List<NamespaceEntity> result = namespaceService.getActiveAccounts();

    assertNotNull(result);
    assertEquals(2, result.size());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetEntityForAccountIdentifier() {
    NamespaceEntity entity =
        NamespaceEntity.builder().id(TEST_NAMESPACE).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).build();
    when(namespaceRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(Optional.of(entity));

    Optional<NamespaceEntity> result = namespaceService.getEntityForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);

    assertTrue(result.isPresent());
    assertEquals(TEST_NAMESPACE, result.get().getId());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSave() {
    NamespaceEntity entity =
        NamespaceEntity.builder().id(TEST_NAMESPACE).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).build();
    when(namespaceRepository.save(entity)).thenReturn(entity);

    namespaceService.save(entity);

    verify(namespaceRepository).save(entity);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateIdpV2MigrationInfoAndSaveWithNullMetadata() {
    NamespaceEntity entity =
        NamespaceEntity.builder().id(TEST_NAMESPACE).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).metadata(null).build();
    when(namespaceRepository.save(any(NamespaceEntity.class))).thenReturn(entity);

    namespaceService.updateIdpV2MigrationInfoAndSave(entity, true);

    verify(namespaceRepository).save(any(NamespaceEntity.class));
    assertNotNull(entity.getMetadata());
    assertTrue(entity.getMetadata().isIdpV2FFState());
    assertNotNull(entity.getMetadata().getIdpV2MigrationInfo());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateIdpV2MigrationInfoAndSaveWithExistingMetadata() {
    NamespaceEntity.Metadata existingMetadata =
        NamespaceEntity.Metadata.builder().catalogCustomPropertiesEnabled(true).build();
    NamespaceEntity entity = NamespaceEntity.builder()
                                 .id(TEST_NAMESPACE)
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .metadata(existingMetadata)
                                 .build();
    when(namespaceRepository.save(any(NamespaceEntity.class))).thenReturn(entity);

    namespaceService.updateIdpV2MigrationInfoAndSave(entity, true);

    verify(namespaceRepository).save(any(NamespaceEntity.class));
    assertTrue(entity.getMetadata().isIdpV2FFState());
    assertNotNull(entity.getMetadata().getIdpV2MigrationInfo());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateIdpV2MigrationInfoAndSaveWithFalseFFState() {
    NamespaceEntity entity =
        NamespaceEntity.builder().id(TEST_NAMESPACE).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).metadata(null).build();
    when(namespaceRepository.save(any(NamespaceEntity.class))).thenReturn(entity);

    namespaceService.updateIdpV2MigrationInfoAndSave(entity, false);

    verify(namespaceRepository).save(any(NamespaceEntity.class));
    assertNotNull(entity.getMetadata());
    assertFalse(entity.getMetadata().isIdpV2FFState());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateIdpV2MigrationInfoAndSaveWithExistingMigrationInfo() {
    NamespaceEntity.Metadata.IdpV2MigrationInfo existingMigrationInfo =
        NamespaceEntity.Metadata.IdpV2MigrationInfo.builder()
            .migrateDefaultToAccountNamespaceInBackstageCompleted(true)
            .build();
    NamespaceEntity.Metadata existingMetadata =
        NamespaceEntity.Metadata.builder().idpV2MigrationInfo(existingMigrationInfo).build();
    NamespaceEntity entity = NamespaceEntity.builder()
                                 .id(TEST_NAMESPACE)
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .metadata(existingMetadata)
                                 .build();
    when(namespaceRepository.save(any(NamespaceEntity.class))).thenReturn(entity);

    namespaceService.updateIdpV2MigrationInfoAndSave(entity, true);

    verify(namespaceRepository).save(any(NamespaceEntity.class));
    assertTrue(entity.getMetadata().isIdpV2FFState());
    assertEquals(existingMigrationInfo, entity.getMetadata().getIdpV2MigrationInfo());
  }
}
