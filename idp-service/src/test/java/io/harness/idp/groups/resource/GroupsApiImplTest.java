/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.groups.resource;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogTemplateEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.groups.service.GroupsService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.Group;
import io.harness.spec.server.idp.v1.model.GroupRequest;
import io.harness.spec.server.idp.v1.model.GroupResponse;
import io.harness.spec.server.idp.v1.model.WorkflowsInfo;
import io.harness.spec.server.idp.v1.model.WorkflowsInfoResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import retrofit2.Call;

@OwnedBy(IDP)
public class GroupsApiImplTest extends CategoryTest {
  @Mock GroupsService groupService;
  @Mock IdpCommonService idpCommonService;
  @Mock AccountClient accountClient;

  @InjectMocks GroupsApiImpl groupsApi;

  private static final String TEST_GROUP_MONGO_ID = "test-mongo-id";
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-identifier";
  private static final String TEST_GROUP_IDENTIFIER = "test-group-identifier";
  private static final String TEST_GROUP_NAME = "test-group-name";
  private static final String TEST_GROUP_DESCRIPTION = "test-group-description";
  private static final String TEST_GROUP_ICON = "test-group-icon";
  private static final int TEST_GROUP_ORDER = 1;

  private static final String TEST_YAML_STRING = "test-yaml-string";

  private static final String TEST_TEMPLATE_UUID = "test-template-uuid";
  private static final String TEST_TEMPLATE_API_VERSION = "test-template-api-version";
  private static final String TEST_TEMPLATE_YAML = "test-template-yaml";
  private static final String TEST_CATALOG_TYPE = "Template";
  private static final String TEST_CATALOG_NAME = "test-catalog-name";
  private static final String TEST_CATALOG_DESCRIPTION = "test-catalog-description";
  private static final String TEST_CATALOG_TITLE = "test-catalog-title";
  private static final String TEST_CATALOG_OWNER = "test-catalog-owner";
  private static final String TEST_CATALOG_ICON = "test-catalog-icon";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void deleteGroupTest() {
    Response response = groupsApi.deleteGroup(TEST_GROUP_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void getAllGroupsForAccountTest() {
    when(groupService.getAllGroupsForAccount(TEST_ACCOUNT_IDENTIFIER, null, null))
        .thenReturn(new ArrayList<>(List.of(getGroupResponse())));
    Response response = groupsApi.getAllGroupsForAccount(TEST_ACCOUNT_IDENTIFIER, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    List<GroupResponse> groupResponses = (List<GroupResponse>) response.getEntity();
    assertEquals(TEST_GROUP_ICON, groupResponses.get(0).getGroup().getIcon());
    assertEquals(TEST_GROUP_NAME, groupResponses.get(0).getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponses.get(0).getGroup().getDescription());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void getGroupDetailsTest() {
    when(groupService.getGroup(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_GROUP_IDENTIFIER))
        .thenReturn(getGroupResponse());
    Response response = groupsApi.getGroupDetails(TEST_GROUP_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    GroupResponse groupResponse = (GroupResponse) response.getEntity();
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.getGroup().getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupResponse.getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponse.getGroup().getDescription());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void saveGroupTest() {
    when(groupService.saveGroup(TEST_ACCOUNT_IDENTIFIER, null, null, getRequestBody())).thenReturn(getGroupResponse());
    Response response = groupsApi.saveGroup(getRequestBody(), TEST_ACCOUNT_IDENTIFIER, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    GroupResponse groupResponse = (GroupResponse) response.getEntity();
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.getGroup().getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupResponse.getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponse.getGroup().getDescription());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void updateGroupTest() {
    when(groupService.updateGroup(TEST_ACCOUNT_IDENTIFIER, null, null, new ArrayList<>(List.of(getRequestBody()))))
        .thenReturn(new ArrayList<>(List.of(getGroupResponse())));
    Response response = groupsApi.updateGroups(List.of(getRequestBody()), TEST_ACCOUNT_IDENTIFIER, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    List<GroupResponse> groupResponse = (List<GroupResponse>) response.getEntity();
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.get(0).getGroup().getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupResponse.get(0).getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponse.get(0).getGroup().getDescription());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void getWorkflowsForAccountTest() throws Exception {
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(retrofit2.Response.success(new RestResponse<>(false)));

    Page<BackstageCatalogEntity> mockPage = new PageImpl<>(Collections.singletonList(getBackstageCatalogEntity()));
    when(groupService.getWorkflowsInfo(TEST_ACCOUNT_IDENTIFIER, null, null, 0, 10)).thenReturn(mockPage);

    Page<CatalogEntity> mockPageCatalogEntity = new PageImpl<>(Collections.singletonList(getCatalogEntity()));
    when(groupService.getCatalogEntitiesForWorkflowsInfo(TEST_ACCOUNT_IDENTIFIER, 0, 10, null, null, null))
        .thenReturn(mockPageCatalogEntity);

    WorkflowsInfoResponse mockResponse = getWorkflowsInfoResponse();
    when(idpCommonService.buildPageResponse(anyInt(), anyInt(), anyLong(), any(WorkflowsInfoResponse.class)))
        .thenReturn(Response.ok().entity(mockResponse).build());

    Response response = groupsApi.getWorkflowsForAccount(TEST_ACCOUNT_IDENTIFIER, null, null, null, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    WorkflowsInfoResponse workflowsInfoResponse = (WorkflowsInfoResponse) response.getEntity();
    assertEquals(TEST_CATALOG_NAME, workflowsInfoResponse.getWorkflows().get(0).getName());
    assertEquals(TEST_CATALOG_DESCRIPTION, workflowsInfoResponse.getWorkflows().get(0).getDescription());
    assertEquals(TEST_CATALOG_TITLE, workflowsInfoResponse.getWorkflows().get(0).getTitle());

    when(ffCall.execute()).thenReturn(retrofit2.Response.success(new RestResponse<>(true)));
    response = groupsApi.getWorkflowsForAccount(TEST_ACCOUNT_IDENTIFIER, null, null, null, null, null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    workflowsInfoResponse = (WorkflowsInfoResponse) response.getEntity();
    assertEquals(TEST_CATALOG_NAME, workflowsInfoResponse.getWorkflows().get(0).getName());
    assertEquals(TEST_CATALOG_DESCRIPTION, workflowsInfoResponse.getWorkflows().get(0).getDescription());
    assertEquals(TEST_CATALOG_TITLE, workflowsInfoResponse.getWorkflows().get(0).getTitle());
  }

  private CatalogEntity getCatalogEntity() {
    InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
    inlineCatalogEntity.setName(TEST_CATALOG_NAME);
    inlineCatalogEntity.setDescription(TEST_CATALOG_DESCRIPTION);
    inlineCatalogEntity.setKind(WORKFLOW_KIND);
    inlineCatalogEntity.setOwner(TEST_ACCOUNT_IDENTIFIER);

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("icon", TEST_CATALOG_ICON);

    inlineCatalogEntity.setMetadata(metadata);
    inlineCatalogEntity.setIdentifier(TEST_TEMPLATE_UUID);
    inlineCatalogEntity.setType(TEST_CATALOG_TYPE);
    return inlineCatalogEntity;
  }

  private BackstageCatalogEntity getBackstageCatalogEntity() {
    BackstageCatalogTemplateEntity backstageCatalogTemplateEntity = new BackstageCatalogTemplateEntity();
    backstageCatalogTemplateEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    backstageCatalogTemplateEntity.setEntityUid(TEST_TEMPLATE_UUID);
    backstageCatalogTemplateEntity.setKind(TEST_CATALOG_TYPE);
    backstageCatalogTemplateEntity.setYaml(TEST_TEMPLATE_YAML);
    backstageCatalogTemplateEntity.setApiVersion(TEST_TEMPLATE_API_VERSION);
    backstageCatalogTemplateEntity.setSpec(
        BackstageCatalogTemplateEntity.Spec.builder().type(TEST_CATALOG_TYPE).build());

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("name", TEST_CATALOG_NAME);
    metadata.put("description", TEST_CATALOG_DESCRIPTION);
    metadata.put("title", TEST_CATALOG_TITLE);
    metadata.put("owner", TEST_CATALOG_OWNER);

    backstageCatalogTemplateEntity.setMetadata(metadata);
    return backstageCatalogTemplateEntity;
  }

  private WorkflowsInfoResponse getWorkflowsInfoResponse() {
    WorkflowsInfoResponse workflowsInfoResponse = new WorkflowsInfoResponse();
    workflowsInfoResponse.setWorkflows(new ArrayList<>(List.of(getWorkflowsInfo())));
    return workflowsInfoResponse;
  }

  private WorkflowsInfo getWorkflowsInfo() {
    WorkflowsInfo workflowsInfo = new WorkflowsInfo();
    workflowsInfo.setType(TEST_CATALOG_TYPE);
    workflowsInfo.setOwner(TEST_CATALOG_OWNER);
    workflowsInfo.setKind(TEST_CATALOG_TYPE);
    workflowsInfo.setUid(TEST_TEMPLATE_UUID);
    workflowsInfo.setDescription(TEST_CATALOG_DESCRIPTION);
    workflowsInfo.setTitle(TEST_CATALOG_TITLE);
    workflowsInfo.setName(TEST_CATALOG_NAME);
    return workflowsInfo;
  }

  private GroupResponse getGroupResponse() {
    GroupResponse groupResponse = new GroupResponse();
    groupResponse.setGroup(getGroup());
    return groupResponse;
  }

  private GroupRequest getRequestBody() {
    GroupRequest groupRequest = new GroupRequest();
    groupRequest.setGroup(getGroup());
    return groupRequest;
  }

  private Group getGroup() {
    Group group = new Group();
    group.setName(TEST_GROUP_NAME);
    group.setDescription(TEST_GROUP_DESCRIPTION);
    group.setIcon(TEST_GROUP_ICON);
    group.setIdentifier(TEST_GROUP_IDENTIFIER);
    group.setOrder(TEST_GROUP_ORDER);
    return group;
  }
}
