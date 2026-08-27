/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.namespace.beans.entity;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.User;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class NamespaceEntityTest extends CategoryTest {
  static final String TEST_ID = "testId";
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccountId";
  static final Long TEST_CREATED_AT = 1000L;
  static final Long TEST_LAST_MODIFIED_AT = 2000L;
  static final Long TEST_NEXT_ITERATION = 3000L;
  static final Long TEST_DELETED_AT = 4000L;

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testNamespaceEntityBuilder() {
    NamespaceEntity.Metadata metadata = NamespaceEntity.Metadata.builder()
                                            .scaffolderTasksSyncFrom(1000L)
                                            .catalogCustomPropertiesEnabled(true)
                                            .migrateCatalogEntitiesFromBackstageToHarnessCompleted(true)
                                            .userGroupSyncCompleted(true)
                                            .postgresIdpV2MigrationCompleted(false)
                                            .idpV2FFState(true)
                                            .build();

    NamespaceEntity entity = NamespaceEntity.builder()
                                 .id(TEST_ID)
                                 .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                 .createdAt(TEST_CREATED_AT)
                                 .lastModifiedAt(TEST_LAST_MODIFIED_AT)
                                 .nextIteration(TEST_NEXT_ITERATION)
                                 .isDeleted(false)
                                 .deletedAt(TEST_DELETED_AT)
                                 .metadata(metadata)
                                 .build();

    assertNotNull(entity);
    assertEquals(TEST_ID, entity.getId());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertEquals(TEST_CREATED_AT, entity.getCreatedAt());
    assertEquals(TEST_LAST_MODIFIED_AT, entity.getLastModifiedAt());
    assertEquals(TEST_NEXT_ITERATION, entity.getNextIteration());
    assertFalse(entity.isDeleted());
    assertEquals(TEST_DELETED_AT.longValue(), entity.getDeletedAt());
    assertNotNull(entity.getMetadata());
    assertTrue(entity.getMetadata().isCatalogCustomPropertiesEnabled());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testNamespaceEntitySettersAndGetters() {
    NamespaceEntity entity = NamespaceEntity.builder().build();

    entity.setId(TEST_ID);
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setCreatedAt(TEST_CREATED_AT);
    entity.setLastModifiedAt(TEST_LAST_MODIFIED_AT);
    entity.setNextIteration(TEST_NEXT_ITERATION);
    entity.setDeleted(true);
    entity.setDeletedAt(TEST_DELETED_AT);

    assertEquals(TEST_ID, entity.getId());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertEquals(TEST_CREATED_AT, entity.getCreatedAt());
    assertEquals(TEST_LAST_MODIFIED_AT, entity.getLastModifiedAt());
    assertEquals(TEST_NEXT_ITERATION, entity.getNextIteration());
    assertTrue(entity.isDeleted());
    assertEquals(TEST_DELETED_AT.longValue(), entity.getDeletedAt());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateNextIteration() {
    NamespaceEntity entity = NamespaceEntity.builder().build();

    entity.updateNextIteration("nextIteration", 5000L);

    assertEquals(Long.valueOf(5000L), entity.getNextIteration());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testObtainNextIteration() {
    NamespaceEntity entity = NamespaceEntity.builder().nextIteration(TEST_NEXT_ITERATION).build();

    Long result = entity.obtainNextIteration("nextIteration");

    assertEquals(TEST_NEXT_ITERATION, result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetUuid() {
    NamespaceEntity entity = NamespaceEntity.builder().id(TEST_ID).build();

    String uuid = entity.getUuid();

    assertEquals(TEST_ID, uuid);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMetadataBuilder() {
    NamespaceEntity.Metadata metadata = NamespaceEntity.Metadata.builder()
                                            .scaffolderTasksSyncFrom(1000L)
                                            .catalogCustomPropertiesEnabled(true)
                                            .migrateCatalogEntitiesFromBackstageToHarnessCompleted(true)
                                            .userGroupSyncCompleted(true)
                                            .postgresIdpV2MigrationCompleted(false)
                                            .idpV2FFState(true)
                                            .build();

    assertNotNull(metadata);
    assertEquals(1000L, metadata.getScaffolderTasksSyncFrom());
    assertTrue(metadata.isCatalogCustomPropertiesEnabled());
    assertTrue(metadata.isMigrateCatalogEntitiesFromBackstageToHarnessCompleted());
    assertTrue(metadata.isUserGroupSyncCompleted());
    assertFalse(metadata.isPostgresIdpV2MigrationCompleted());
    assertTrue(metadata.isIdpV2FFState());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMetadataSettersAndGetters() {
    NamespaceEntity.Metadata metadata = new NamespaceEntity.Metadata();

    metadata.setScaffolderTasksSyncFrom(2000L);
    metadata.setCatalogCustomPropertiesEnabled(false);
    metadata.setMigrateCatalogEntitiesFromBackstageToHarnessCompleted(false);
    metadata.setUserGroupSyncCompleted(false);
    metadata.setPostgresIdpV2MigrationCompleted(true);
    metadata.setIdpV2FFState(false);

    assertEquals(2000L, metadata.getScaffolderTasksSyncFrom());
    assertFalse(metadata.isCatalogCustomPropertiesEnabled());
    assertFalse(metadata.isMigrateCatalogEntitiesFromBackstageToHarnessCompleted());
    assertFalse(metadata.isUserGroupSyncCompleted());
    assertTrue(metadata.isPostgresIdpV2MigrationCompleted());
    assertFalse(metadata.isIdpV2FFState());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpV2MigrationInfoBuilder() {
    NamespaceEntity.Metadata.IdpV2MigrationInfo migrationInfo =
        NamespaceEntity.Metadata.IdpV2MigrationInfo.builder()
            .migrateDefaultToAccountNamespaceInBackstageCompleted(true)
            .migrateDefaultToAccountNamespaceInBackstageFrom(1000L)
            .migrateDefaultToAccountNamespaceInDependentsCompleted(true)
            .migrateDefaultToAccountNamespaceInDependentsFrom(2000L)
            .migrateWorkflowFormContextDataCompleted(true)
            .migrateWorkflowFormContextDataFrom(3000L)
            .populateQueryableEntityRefInCatalogCompleted(true)
            .populateQueryableEntityRefInCatalogFrom(4000L)
            .build();

    assertNotNull(migrationInfo);
    assertTrue(migrationInfo.isMigrateDefaultToAccountNamespaceInBackstageCompleted());
    assertEquals(1000L, migrationInfo.getMigrateDefaultToAccountNamespaceInBackstageFrom());
    assertTrue(migrationInfo.isMigrateDefaultToAccountNamespaceInDependentsCompleted());
    assertEquals(2000L, migrationInfo.getMigrateDefaultToAccountNamespaceInDependentsFrom());
    assertTrue(migrationInfo.isMigrateWorkflowFormContextDataCompleted());
    assertEquals(3000L, migrationInfo.getMigrateWorkflowFormContextDataFrom());
    assertTrue(migrationInfo.isPopulateQueryableEntityRefInCatalogCompleted());
    assertEquals(4000L, migrationInfo.getPopulateQueryableEntityRefInCatalogFrom());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpV2MigrationInfoSettersAndGetters() {
    NamespaceEntity.Metadata.IdpV2MigrationInfo migrationInfo = new NamespaceEntity.Metadata.IdpV2MigrationInfo();

    migrationInfo.setMigrateDefaultToAccountNamespaceInBackstageCompleted(false);
    migrationInfo.setMigrateDefaultToAccountNamespaceInBackstageFrom(5000L);
    migrationInfo.setMigrateDefaultToAccountNamespaceInDependentsCompleted(false);
    migrationInfo.setMigrateDefaultToAccountNamespaceInDependentsFrom(6000L);
    migrationInfo.setMigrateWorkflowFormContextDataCompleted(false);
    migrationInfo.setMigrateWorkflowFormContextDataFrom(7000L);
    migrationInfo.setPopulateQueryableEntityRefInCatalogCompleted(false);
    migrationInfo.setPopulateQueryableEntityRefInCatalogFrom(8000L);

    assertFalse(migrationInfo.isMigrateDefaultToAccountNamespaceInBackstageCompleted());
    assertEquals(5000L, migrationInfo.getMigrateDefaultToAccountNamespaceInBackstageFrom());
    assertFalse(migrationInfo.isMigrateDefaultToAccountNamespaceInDependentsCompleted());
    assertEquals(6000L, migrationInfo.getMigrateDefaultToAccountNamespaceInDependentsFrom());
    assertFalse(migrationInfo.isMigrateWorkflowFormContextDataCompleted());
    assertEquals(7000L, migrationInfo.getMigrateWorkflowFormContextDataFrom());
    assertFalse(migrationInfo.isPopulateQueryableEntityRefInCatalogCompleted());
    assertEquals(8000L, migrationInfo.getPopulateQueryableEntityRefInCatalogFrom());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMigrateScopeInfoBuilder() {
    User user = new User();
    user.setName("testUser");
    user.setEmail("test@test.com");

    NamespaceEntity.Metadata.IdpV2MigrationInfo.MigrateScopeInfo scopeInfo =
        NamespaceEntity.Metadata.IdpV2MigrationInfo.MigrateScopeInfo.builder()
            .isActive(true)
            .request("testRequest")
            .updatedBy(user)
            .updatedAt(1000L)
            .build();

    assertNotNull(scopeInfo);
    assertTrue(scopeInfo.isActive());
    assertEquals("testRequest", scopeInfo.getRequest());
    assertEquals(user, scopeInfo.getUpdatedBy());
    assertEquals(1000L, scopeInfo.getUpdatedAt());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMigrateScopeInfoSettersAndGetters() {
    NamespaceEntity.Metadata.IdpV2MigrationInfo.MigrateScopeInfo scopeInfo =
        new NamespaceEntity.Metadata.IdpV2MigrationInfo.MigrateScopeInfo();

    User user = new User();
    user.setName("testUser2");

    scopeInfo.setActive(false);
    scopeInfo.setRequest("anotherRequest");
    scopeInfo.setUpdatedBy(user);
    scopeInfo.setUpdatedAt(2000L);

    assertFalse(scopeInfo.isActive());
    assertEquals("anotherRequest", scopeInfo.getRequest());
    assertEquals(user, scopeInfo.getUpdatedBy());
    assertEquals(2000L, scopeInfo.getUpdatedAt());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testNamespaceKeysConstants() {
    assertNotNull(NamespaceEntity.NamespaceKeys.accountIdentifier);
    assertNotNull(NamespaceEntity.NamespaceKeys.nextIteration);
    assertNotNull(NamespaceEntity.NamespaceKeys.isDeleted);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMetadataWithIdpV2MigrationInfo() {
    NamespaceEntity.Metadata.IdpV2MigrationInfo migrationInfo =
        NamespaceEntity.Metadata.IdpV2MigrationInfo.builder()
            .migrateDefaultToAccountNamespaceInBackstageCompleted(true)
            .build();

    NamespaceEntity.Metadata metadata =
        NamespaceEntity.Metadata.builder().idpV2MigrationInfo(migrationInfo).idpV2FFState(true).build();

    assertNotNull(metadata.getIdpV2MigrationInfo());
    assertTrue(metadata.getIdpV2MigrationInfo().isMigrateDefaultToAccountNamespaceInBackstageCompleted());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testNamespaceEntityWithNullMetadata() {
    NamespaceEntity entity =
        NamespaceEntity.builder().id(TEST_ID).accountIdentifier(TEST_ACCOUNT_IDENTIFIER).metadata(null).build();

    assertNotNull(entity);
    assertNull(entity.getMetadata());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIdpV2MigrationInfoWithMigrateScopeInfo() {
    User user = new User();
    user.setName("testUser");

    NamespaceEntity.Metadata.IdpV2MigrationInfo.MigrateScopeInfo scopeInfo =
        NamespaceEntity.Metadata.IdpV2MigrationInfo.MigrateScopeInfo.builder()
            .isActive(true)
            .request("testRequest")
            .updatedBy(user)
            .updatedAt(1000L)
            .build();

    NamespaceEntity.Metadata.IdpV2MigrationInfo migrationInfo =
        NamespaceEntity.Metadata.IdpV2MigrationInfo.builder().migrateScopeInfo(scopeInfo).build();

    assertNotNull(migrationInfo.getMigrateScopeInfo());
    assertTrue(migrationInfo.getMigrateScopeInfo().isActive());
    assertEquals("testRequest", migrationInfo.getMigrateScopeInfo().getRequest());
  }
}
