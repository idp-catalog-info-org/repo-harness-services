/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.groups.mappers;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.idp.groups.entities.GroupEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Group;
import io.harness.spec.server.idp.v1.model.GroupResponse;
import io.harness.spec.server.idp.v1.model.WorkflowsInfo;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(IDP)
public class GroupsMapperTest extends CategoryTest {
  private static final String TEST_GROUP_MONGO_ID = "test-mongo-id";
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-identifier";
  private static final String TEST_GROUP_IDENTIFIER = "test-group-identifier";
  private static final String TEST_GROUP_NAME = "test-group-name";
  private static final String TEST_GROUP_DESCRIPTION = "test-group-description";
  private static final String TEST_GROUP_ICON = "test-group-icon";
  private static final int TEST_GROUP_ORDER = 1;

  private static final String TEST_WORKFLOWS_UUID = "test-workflows-uuid";
  private static final String TEST_WORKFLOW_NAME = "test-workflow-name";
  private static final String TEST_WORKFLOW_DESCRIPTION = "test-workflow-description";
  private static final String TEST_WORKFLOW_TITLE = "test-workflow-title";
  private static final String TEST_WORKFLOW_KIND = "test-workflow-kind";
  private static final String TEST_WORKFLOW_OWNER = "test-workflow-owner";
  private static final String TEST_WORKFLOW_TYPE = "test-workflow-type";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void toDtoTest() {
    Group group = GroupsMapper.toDTO(getGroupEntity(), new ArrayList<>(List.of(getWorkflowsInfo())), null, null);
    assertEquals(TEST_WORKFLOW_NAME, group.getWorkflows().get(0).getName());
    assertEquals(TEST_WORKFLOW_DESCRIPTION, group.getWorkflows().get(0).getDescription());
    assertEquals(TEST_WORKFLOW_TITLE, group.getWorkflows().get(0).getTitle());
    assertEquals(TEST_WORKFLOW_KIND, group.getWorkflows().get(0).getKind());
    assertEquals(TEST_GROUP_ICON, group.getIcon());
    assertEquals(TEST_GROUP_NAME, group.getName());
    assertEquals(TEST_GROUP_DESCRIPTION, group.getDescription());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void fromDtoTest() {
    GroupEntity groupEntity = GroupsMapper.fromDTO(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build(),
        getGroup());
    assertEquals(TEST_GROUP_NAME, groupEntity.getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupEntity.getDescription());
    assertEquals(TEST_GROUP_ICON, groupEntity.getIcon());
    assertEquals(TEST_GROUP_IDENTIFIER, getGroupEntity().getIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void toYamlGroupDTOTest() {
    Group group = GroupsMapper.toYamlGroupDTO(getGroupEntity(), new ArrayList<>(List.of(getWorkflowsInfo())));
    assertEquals(TEST_WORKFLOW_NAME, group.getWorkflows().get(0).getName());
    assertEquals(TEST_WORKFLOW_DESCRIPTION, group.getWorkflows().get(0).getDescription());
    assertEquals(TEST_WORKFLOW_TITLE, group.getWorkflows().get(0).getTitle());
    assertEquals(TEST_WORKFLOW_KIND, group.getWorkflows().get(0).getKind());

    assertEquals(TEST_GROUP_ICON, group.getIcon());
    assertEquals(TEST_GROUP_NAME, group.getName());
    assertEquals(TEST_GROUP_DESCRIPTION, group.getDescription());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void toResponseDTOTest() {
    GroupResponse groupResponse =
        GroupsMapper.toResponse(getGroupEntity(), new ArrayList<>(List.of(getWorkflowsInfo())), null, null);
    assertEquals(TEST_WORKFLOW_NAME, groupResponse.getGroup().getWorkflows().get(0).getName());
    assertEquals(TEST_WORKFLOW_DESCRIPTION, groupResponse.getGroup().getWorkflows().get(0).getDescription());
    assertEquals(TEST_WORKFLOW_TITLE, groupResponse.getGroup().getWorkflows().get(0).getTitle());
    assertEquals(TEST_WORKFLOW_KIND, groupResponse.getGroup().getWorkflows().get(0).getKind());

    assertEquals(TEST_GROUP_ICON, groupResponse.getGroup().getIcon());
    assertEquals(TEST_GROUP_NAME, groupResponse.getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponse.getGroup().getDescription());
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.getGroup().getIdentifier());
  }

  private Group getGroup() {
    Group group = new Group();
    group.setName(TEST_GROUP_NAME);
    group.setDescription(TEST_GROUP_DESCRIPTION);
    group.setIcon(TEST_GROUP_ICON);
    group.setIdentifier(TEST_GROUP_IDENTIFIER);
    group.setOrder(TEST_GROUP_ORDER);
    group.setWorkflows(new ArrayList<>(List.of(getWorkflowsInfo())));
    return group;
  }

  private GroupEntity getGroupEntity() {
    return GroupEntity.builder()
        .id(TEST_GROUP_MONGO_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .identifier(TEST_GROUP_IDENTIFIER)
        .name(TEST_GROUP_NAME)
        .description(TEST_GROUP_DESCRIPTION)
        .icon(TEST_GROUP_ICON)
        .order(TEST_GROUP_ORDER)
        .workflows(new ArrayList<>(List.of(TEST_WORKFLOWS_UUID)))
        .build();
  }

  private WorkflowsInfo getWorkflowsInfo() {
    WorkflowsInfo workflowsInfo = new WorkflowsInfo();
    workflowsInfo.setType(TEST_WORKFLOW_TYPE);
    workflowsInfo.setOwner(TEST_WORKFLOW_OWNER);
    workflowsInfo.setKind(TEST_WORKFLOW_KIND);
    workflowsInfo.setUid(TEST_WORKFLOWS_UUID);
    workflowsInfo.setDescription(TEST_WORKFLOW_DESCRIPTION);
    workflowsInfo.setTitle(TEST_WORKFLOW_TITLE);
    workflowsInfo.setName(TEST_WORKFLOW_NAME);
    return workflowsInfo;
  }
}
