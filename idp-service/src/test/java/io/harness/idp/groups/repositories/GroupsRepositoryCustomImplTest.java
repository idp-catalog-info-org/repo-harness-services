/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.groups.repositories;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

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
import io.harness.idp.groups.entities.GroupEntity;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GroupsRepositoryCustomImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccountId";
  static final String TEST_GROUP_NAME = "testGroupName";
  static final String TEST_GROUP_IDENTIFIER = "testGroupIdentifier";

  @Mock MongoTemplate mongoTemplate;

  @InjectMocks GroupsRepositoryCustomImpl groupsRepositoryCustomImpl;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndSearchOnNameWithSearchTerm() {
    GroupEntity groupEntity = GroupEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                  .name(TEST_GROUP_NAME)
                                  .identifier(TEST_GROUP_IDENTIFIER)
                                  .build();

    when(mongoTemplate.find(any(Query.class), eq(GroupEntity.class)))
        .thenReturn(Collections.singletonList(groupEntity));

    List<GroupEntity> result =
        groupsRepositoryCustomImpl.findByAccountIdentifierAndSearchOnName(TEST_ACCOUNT_IDENTIFIER, "test");

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(TEST_GROUP_NAME, result.get(0).getName());
    assertEquals(TEST_ACCOUNT_IDENTIFIER, result.get(0).getAccountIdentifier());

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(GroupEntity.class));

    Query capturedQuery = queryCaptor.getValue();
    assertNotNull(capturedQuery);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndSearchOnNameWithoutSearchTerm() {
    GroupEntity groupEntity1 =
        GroupEntity.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).name("Group1").identifier("group1").build();

    GroupEntity groupEntity2 =
        GroupEntity.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).name("Group2").identifier("group2").build();

    when(mongoTemplate.find(any(Query.class), eq(GroupEntity.class)))
        .thenReturn(Arrays.asList(groupEntity1, groupEntity2));

    List<GroupEntity> result =
        groupsRepositoryCustomImpl.findByAccountIdentifierAndSearchOnName(TEST_ACCOUNT_IDENTIFIER, null);

    assertNotNull(result);
    assertEquals(2, result.size());

    verify(mongoTemplate).find(any(Query.class), eq(GroupEntity.class));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndSearchOnNameWithEmptySearchTerm() {
    GroupEntity groupEntity = GroupEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                  .name(TEST_GROUP_NAME)
                                  .identifier(TEST_GROUP_IDENTIFIER)
                                  .build();

    when(mongoTemplate.find(any(Query.class), eq(GroupEntity.class)))
        .thenReturn(Collections.singletonList(groupEntity));

    List<GroupEntity> result =
        groupsRepositoryCustomImpl.findByAccountIdentifierAndSearchOnName(TEST_ACCOUNT_IDENTIFIER, "");

    assertNotNull(result);
    assertEquals(1, result.size());

    verify(mongoTemplate).find(any(Query.class), eq(GroupEntity.class));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndSearchOnNameReturnsEmptyList() {
    when(mongoTemplate.find(any(Query.class), eq(GroupEntity.class))).thenReturn(Collections.emptyList());

    List<GroupEntity> result =
        groupsRepositoryCustomImpl.findByAccountIdentifierAndSearchOnName(TEST_ACCOUNT_IDENTIFIER, "nonexistent");

    assertNotNull(result);
    assertTrue(result.isEmpty());

    verify(mongoTemplate).find(any(Query.class), eq(GroupEntity.class));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndSearchOnNameWithSpecialCharacters() {
    GroupEntity groupEntity = GroupEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                  .name("Test-Group_Name.123")
                                  .identifier(TEST_GROUP_IDENTIFIER)
                                  .build();

    when(mongoTemplate.find(any(Query.class), eq(GroupEntity.class)))
        .thenReturn(Collections.singletonList(groupEntity));

    List<GroupEntity> result =
        groupsRepositoryCustomImpl.findByAccountIdentifierAndSearchOnName(TEST_ACCOUNT_IDENTIFIER, "Test-Group");

    assertNotNull(result);
    assertEquals(1, result.size());

    verify(mongoTemplate).find(any(Query.class), eq(GroupEntity.class));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testFindByAccountIdentifierAndSearchOnNameCaseInsensitive() {
    GroupEntity groupEntity = GroupEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                  .name("TestGroupName")
                                  .identifier(TEST_GROUP_IDENTIFIER)
                                  .build();

    when(mongoTemplate.find(any(Query.class), eq(GroupEntity.class)))
        .thenReturn(Collections.singletonList(groupEntity));

    List<GroupEntity> result =
        groupsRepositoryCustomImpl.findByAccountIdentifierAndSearchOnName(TEST_ACCOUNT_IDENTIFIER, "TESTGROUP");

    assertNotNull(result);
    assertEquals(1, result.size());

    verify(mongoTemplate).find(any(Query.class), eq(GroupEntity.class));
  }
}
