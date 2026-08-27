/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.groups.entities;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.mongo.index.MongoIndex;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GroupEntityTest extends CategoryTest {
  static final String TEST_ID = "testId";
  static final String TEST_UNIQUE_ID = "testUniqueId";
  static final String TEST_PARENT_UNIQUE_ID = "testParentUniqueId";
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccountId";
  static final String TEST_ORG_IDENTIFIER = "testOrgId";
  static final String TEST_PROJECT_IDENTIFIER = "testProjectId";
  static final String TEST_NAME = "testName";
  static final String TEST_IDENTIFIER = "testIdentifier";
  static final String TEST_DESCRIPTION = "testDescription";
  static final String TEST_ICON = "testIcon";
  static final Integer TEST_ORDER = 1;
  static final Long TEST_CREATED_AT = 1000L;
  static final Long TEST_LAST_UPDATED_AT = 2000L;

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMongoIndexes() {
    List<MongoIndex> indexes = GroupEntity.mongoIndexes();

    assertNotNull(indexes);
    assertEquals(1, indexes.size());

    MongoIndex index = indexes.get(0);
    assertEquals("unique_parentUniqueId_identifier", index.getName());
    assertTrue(index.isUnique());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGroupEntityBuilder() {
    List<String> workflows = Arrays.asList("workflow1", "workflow2");
    EmbeddedUser createdBy = EmbeddedUser.builder().name("creator").email("creator@test.com").build();
    EmbeddedUser updatedBy = EmbeddedUser.builder().name("updater").email("updater@test.com").build();

    GroupEntity entity = GroupEntity.builder()
                             .id(TEST_ID)
                             .uniqueId(TEST_UNIQUE_ID)
                             .parentUniqueId(TEST_PARENT_UNIQUE_ID)
                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                             .orgIdentifier(TEST_ORG_IDENTIFIER)
                             .projectIdentifier(TEST_PROJECT_IDENTIFIER)
                             .name(TEST_NAME)
                             .identifier(TEST_IDENTIFIER)
                             .description(TEST_DESCRIPTION)
                             .icon(TEST_ICON)
                             .workflows(workflows)
                             .order(TEST_ORDER)
                             .createdAt(TEST_CREATED_AT)
                             .createdBy(createdBy)
                             .lastUpdatedAt(TEST_LAST_UPDATED_AT)
                             .lastUpdatedBy(updatedBy)
                             .build();

    assertNotNull(entity);
    assertEquals(TEST_ID, entity.getId());
    assertEquals(TEST_UNIQUE_ID, entity.getUniqueId());
    assertEquals(TEST_PARENT_UNIQUE_ID, entity.getParentUniqueId());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertEquals(TEST_ORG_IDENTIFIER, entity.getOrgIdentifier());
    assertEquals(TEST_PROJECT_IDENTIFIER, entity.getProjectIdentifier());
    assertEquals(TEST_NAME, entity.getName());
    assertEquals(TEST_IDENTIFIER, entity.getIdentifier());
    assertEquals(TEST_DESCRIPTION, entity.getDescription());
    assertEquals(TEST_ICON, entity.getIcon());
    assertEquals(workflows, entity.getWorkflows());
    assertEquals(TEST_ORDER, entity.getOrder());
    assertEquals(TEST_CREATED_AT, Long.valueOf(entity.getCreatedAt()));
    assertEquals(createdBy, entity.getCreatedBy());
    assertEquals(TEST_LAST_UPDATED_AT, Long.valueOf(entity.getLastUpdatedAt()));
    assertEquals(updatedBy, entity.getLastUpdatedBy());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGroupEntitySettersAndGetters() {
    GroupEntity entity = GroupEntity.builder().build();

    entity.setId(TEST_ID);
    entity.setUniqueId(TEST_UNIQUE_ID);
    entity.setParentUniqueId(TEST_PARENT_UNIQUE_ID);
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setOrgIdentifier(TEST_ORG_IDENTIFIER);
    entity.setProjectIdentifier(TEST_PROJECT_IDENTIFIER);
    entity.setName(TEST_NAME);
    entity.setIdentifier(TEST_IDENTIFIER);
    entity.setDescription(TEST_DESCRIPTION);
    entity.setIcon(TEST_ICON);
    entity.setOrder(TEST_ORDER);
    entity.setCreatedAt(TEST_CREATED_AT);
    entity.setLastUpdatedAt(TEST_LAST_UPDATED_AT);

    List<String> workflows = Arrays.asList("wf1", "wf2");
    entity.setWorkflows(workflows);

    EmbeddedUser createdBy = EmbeddedUser.builder().name("test").build();
    entity.setCreatedBy(createdBy);

    EmbeddedUser updatedBy = EmbeddedUser.builder().name("test2").build();
    entity.setLastUpdatedBy(updatedBy);

    assertEquals(TEST_ID, entity.getId());
    assertEquals(TEST_UNIQUE_ID, entity.getUniqueId());
    assertEquals(TEST_PARENT_UNIQUE_ID, entity.getParentUniqueId());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, entity.getAccountIdentifier());
    assertEquals(TEST_ORG_IDENTIFIER, entity.getOrgIdentifier());
    assertEquals(TEST_PROJECT_IDENTIFIER, entity.getProjectIdentifier());
    assertEquals(TEST_NAME, entity.getName());
    assertEquals(TEST_IDENTIFIER, entity.getIdentifier());
    assertEquals(TEST_DESCRIPTION, entity.getDescription());
    assertEquals(TEST_ICON, entity.getIcon());
    assertEquals(TEST_ORDER, entity.getOrder());
    assertEquals(TEST_CREATED_AT, Long.valueOf(entity.getCreatedAt()));
    assertEquals(TEST_LAST_UPDATED_AT, Long.valueOf(entity.getLastUpdatedAt()));
    assertEquals(workflows, entity.getWorkflows());
    assertEquals(createdBy, entity.getCreatedBy());
    assertEquals(updatedBy, entity.getLastUpdatedBy());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGroupEntityWithNullWorkflows() {
    GroupEntity entity = GroupEntity.builder()
                             .id(TEST_ID)
                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                             .name(TEST_NAME)
                             .identifier(TEST_IDENTIFIER)
                             .description(TEST_DESCRIPTION)
                             .icon(TEST_ICON)
                             .order(TEST_ORDER)
                             .workflows(null)
                             .build();

    assertNotNull(entity);
    assertEquals(null, entity.getWorkflows());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGroupEntityWithEmptyWorkflows() {
    GroupEntity entity = GroupEntity.builder()
                             .id(TEST_ID)
                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                             .name(TEST_NAME)
                             .identifier(TEST_IDENTIFIER)
                             .description(TEST_DESCRIPTION)
                             .icon(TEST_ICON)
                             .order(TEST_ORDER)
                             .workflows(Arrays.asList())
                             .build();

    assertNotNull(entity);
    assertTrue(entity.getWorkflows().isEmpty());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGroupsEntityKeysConstants() {
    assertNotNull(GroupEntity.GroupsEntityKeys.accountIdentifier);
    assertNotNull(GroupEntity.GroupsEntityKeys.parentUniqueId);
    assertNotNull(GroupEntity.GroupsEntityKeys.identifier);
  }
}
