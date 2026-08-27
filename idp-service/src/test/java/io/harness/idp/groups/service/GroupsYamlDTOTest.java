/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.groups.service;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Group;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GroupsYamlDTOTest extends CategoryTest {
  static final String TEST_GROUP_NAME = "testGroupName";
  static final String TEST_GROUP_IDENTIFIER = "testGroupIdentifier";
  static final String TEST_GROUP_DESCRIPTION = "testGroupDescription";
  static final String TEST_GROUP_ICON = "testGroupIcon";

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGroupsYamlDTOBuilder() {
    Group group1 = createGroup("group1", "Group 1");
    Group group2 = createGroup("group2", "Group 2");
    List<Group> groups = Arrays.asList(group1, group2);

    GroupsYamlDTO groupsYamlDTO = GroupsYamlDTO.builder().groups(groups).build();

    assertNotNull(groupsYamlDTO);
    assertNotNull(groupsYamlDTO.getGroups());
    assertEquals(2, groupsYamlDTO.getGroups().size());
    assertEquals("group1", groupsYamlDTO.getGroups().get(0).getIdentifier());
    assertEquals("group2", groupsYamlDTO.getGroups().get(1).getIdentifier());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGroupsYamlDTOSettersAndGetters() {
    GroupsYamlDTO groupsYamlDTO = GroupsYamlDTO.builder().build();

    Group group = createGroup(TEST_GROUP_IDENTIFIER, TEST_GROUP_NAME);
    List<Group> groups = new ArrayList<>();
    groups.add(group);

    groupsYamlDTO.setGroups(groups);

    assertNotNull(groupsYamlDTO.getGroups());
    assertEquals(1, groupsYamlDTO.getGroups().size());
    assertEquals(TEST_GROUP_IDENTIFIER, groupsYamlDTO.getGroups().get(0).getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupsYamlDTO.getGroups().get(0).getName());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGroupsYamlDTOWithNullGroups() {
    GroupsYamlDTO groupsYamlDTO = GroupsYamlDTO.builder().groups(null).build();

    assertNotNull(groupsYamlDTO);
    assertNull(groupsYamlDTO.getGroups());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGroupsYamlDTOWithEmptyGroups() {
    GroupsYamlDTO groupsYamlDTO = GroupsYamlDTO.builder().groups(new ArrayList<>()).build();

    assertNotNull(groupsYamlDTO);
    assertNotNull(groupsYamlDTO.getGroups());
    assertTrue(groupsYamlDTO.getGroups().isEmpty());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGroupsYamlDTOImplementsYamlDTO() {
    GroupsYamlDTO groupsYamlDTO = GroupsYamlDTO.builder().build();
    assertTrue(groupsYamlDTO instanceof io.harness.gitsync.beans.YamlDTO);
  }

  private Group createGroup(String identifier, String name) {
    Group group = new Group();
    group.setIdentifier(identifier);
    group.setName(name);
    group.setDescription(TEST_GROUP_DESCRIPTION);
    group.setIcon(TEST_GROUP_ICON);
    group.setOrder(1);
    return group;
  }
}
