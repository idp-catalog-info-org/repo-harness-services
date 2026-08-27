/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.groups.service;

import static io.harness.annotations.dev.HarnessTeam.IDP;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.entities.BackstageCatalogTemplateEntity;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.groups.entities.GroupEntity;
import io.harness.idp.groups.repositories.GroupsRepository;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.spec.server.idp.v1.model.Group;
import io.harness.spec.server.idp.v1.model.GroupRequest;
import io.harness.spec.server.idp.v1.model.GroupResponse;
import io.harness.spec.server.idp.v1.model.WorkflowsInfo;

import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(IDP)
public class GroupServiceImplTest extends CategoryTest {
  @Mock GroupsRepository groupRepository;
  @Mock @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Mock BackstageService backstageService;
  @Mock OutboxService outboxService;
  @Mock CatalogEntityRepository catalogEntityRepository;
  @InjectMocks GroupsServiceImpl groupsServiceImpl;
  @Mock AccountClient accountClient;
  @Mock ScopeInfoClient scopeInfoClient;
  @Mock NamespaceService namespaceService;

  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-identifier";

  private static final String TEST_GROUP_MONGO_ID = "test-mongo-id";
  private static final String TEST_GROUP_IDENTIFIER = "test-group-identifier";
  private static final String TEST_GROUP_NAME = "test-group-name";
  private static final String TEST_GROUP_DESCRIPTION = "test-group-description";
  private static final String TEST_GROUP_ICON = "test-group-icon";
  private static final int TEST_GROUP_ORDER = 1;

  private static final String TEST_TEMPLATE_UUID = "test/template/uuid";
  private static final String TEST_TEMPLATE_API_VERSION = "test-template-api-version";
  private static final String TEST_TEMPLATE_YAML = "test-template-yaml";
  private static final String TEST_CATALOG_TYPE = "Template";
  private static final String TEST_CATALOG_NAME = "test-catalog-name";
  private static final String TEST_CATALOG_DESCRIPTION = "test-catalog-description";
  private static final String TEST_CATALOG_TITLE = "test-catalog-title";
  private static final String TEST_CATALOG_OWNER = "test-catalog-owner";

  private static final String GROUP_NOT_FOUND_WITH_IDENTIFIER_ERROR_MESSAGE =
      "Group with identifier - test-group-identifier not found for account - test-account-identifier";

  private static final String INVALID_UPDATE_CASE_ERROR_MESSAGE =
      "Groups cannot be updated as few groups with identifiers - [test-group-identifier] in project - null, org - null "
      + "are not saved yet";

  private static final String INVALID_UPDATE_CASE_NO_ORDER_PROVIDED_ERROR_MESSAGE =
      "Groups cannot be updated as few groups with identifiers - [test-group-identifier] are not having order value";

  private static final String NO_GROUPS_TO_UPDATE_ERROR_MESSAGE =
      "No group to update for account - test-account-identifier";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void getAllGroupsForAccountTest() throws Exception {
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    when(groupRepository.findAllByParentUniqueId(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(new ArrayList<>(List.of(getGroupEntity())));
    when(backstageService.findAllByAccountIdentifierAndKind(
             TEST_ACCOUNT_IDENTIFIER, BackstageCatalogEntityTypes.TEMPLATE.kind))
        .thenReturn(new ArrayList<>(List.of(getBackstageCatalogEntity())));
    when(catalogEntityRepository.findAllByParentUniqueIdAndKind(TEST_ACCOUNT_IDENTIFIER, WORKFLOW_KIND))
        .thenReturn(new ArrayList<>(new ArrayList<>(List.of(getCatalogEntity()))));

    List<GroupResponse> groupResponse = groupsServiceImpl.getAllGroupsForAccount(TEST_ACCOUNT_IDENTIFIER, null, null);
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.get(0).getGroup().getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupResponse.get(0).getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponse.get(0).getGroup().getDescription());
    assertEquals(TEST_GROUP_ICON, groupResponse.get(0).getGroup().getIcon());

    // FF enabled case
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    groupResponse = groupsServiceImpl.getAllGroupsForAccount(TEST_ACCOUNT_IDENTIFIER, null, null);
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.get(0).getGroup().getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupResponse.get(0).getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponse.get(0).getGroup().getDescription());
    assertEquals(TEST_GROUP_ICON, groupResponse.get(0).getGroup().getIcon());

    // no groups present case
    when(groupRepository.findAllByParentUniqueId(TEST_ACCOUNT_IDENTIFIER)).thenReturn(new ArrayList<>());
    groupResponse = groupsServiceImpl.getAllGroupsForAccount(TEST_ACCOUNT_IDENTIFIER, null, null);
    assertTrue(groupResponse.isEmpty());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void deleteGroupTest() throws Exception {
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    GroupEntity group = getGroupEntity();
    when(groupRepository.findAllByParentUniqueId(TEST_ACCOUNT_IDENTIFIER)).thenReturn(new ArrayList<>(List.of(group)));
    when(groupRepository.saveAll(new ArrayList<>(List.of(getGroupEntity()))))
        .thenReturn(new ArrayList<>(List.of(group)));
    when(backstageService.findAllByAccountIdentifierAndEntityRefs(any(), any()))
        .thenReturn(new ArrayList<>(List.of(getBackstageCatalogEntity())));
    when(catalogEntityRepository.findAllByParentUniqueIdAndKindAndIdentifierIn(any(), any(), any()))
        .thenReturn(new ArrayList<>(List.of(getCatalogEntity())));
    doNothing().when(groupRepository).delete(group);
    groupsServiceImpl.deleteGroup(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_GROUP_IDENTIFIER);
    verify(groupRepository, times(1)).delete(group);

    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    groupsServiceImpl.deleteGroup(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_GROUP_IDENTIFIER);
    verify(groupRepository, times(2)).delete(group);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void getGroupTest() throws Exception {
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    GroupEntity groupEntity = getGroupEntity();
    when(groupRepository.findByParentUniqueIdAndIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_GROUP_IDENTIFIER))
        .thenReturn(Optional.of(groupEntity));
    when(backstageService.findAllByAccountIdentifierAndKind(
             TEST_ACCOUNT_IDENTIFIER, BackstageCatalogEntityTypes.TEMPLATE.kind))
        .thenReturn(new ArrayList<>(List.of(getBackstageCatalogEntity())));
    when(catalogEntityRepository.findAllByParentUniqueIdAndKindAndIdentifierIn(any(), any(), any()))
        .thenReturn(new ArrayList<>(List.of(getCatalogEntity())));
    GroupResponse groupResponse =
        groupsServiceImpl.getGroup(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_GROUP_IDENTIFIER);
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.getGroup().getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupResponse.getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponse.getGroup().getDescription());
    assertEquals(TEST_GROUP_ICON, groupResponse.getGroup().getIcon());

    // FF enabled case
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    groupResponse = groupsServiceImpl.getGroup(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_GROUP_IDENTIFIER);
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.getGroup().getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupResponse.getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponse.getGroup().getDescription());
    assertEquals(TEST_GROUP_ICON, groupResponse.getGroup().getIcon());

    // Group not present case
    when(groupRepository.findByParentUniqueIdAndIdentifier(TEST_ACCOUNT_IDENTIFIER, TEST_GROUP_IDENTIFIER))
        .thenReturn(Optional.empty());
    Exception exception = null;
    try {
      groupsServiceImpl.getGroup(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_GROUP_IDENTIFIER);
    } catch (NotFoundException e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(GROUP_NOT_FOUND_WITH_IDENTIFIER_ERROR_MESSAGE, exception.getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void saveGroupTest() throws Exception {
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    GroupEntity groupEntity = getGroupEntity();
    when(backstageService.findAllByAccountIdentifierAndKind(
             TEST_ACCOUNT_IDENTIFIER, BackstageCatalogEntityTypes.TEMPLATE.kind))
        .thenReturn(new ArrayList<>(List.of(getBackstageCatalogEntity())));
    when(groupRepository.save(any(GroupEntity.class))).thenReturn(groupEntity);
    when(catalogEntityRepository.findAllByParentUniqueIdAndKindAndIdentifierIn(any(), any(), any()))
        .thenReturn(new ArrayList<>(List.of(getCatalogEntity())));
    GroupResponse groupResponse =
        groupsServiceImpl.saveGroup(TEST_ACCOUNT_IDENTIFIER, null, null, getGroupRequest(true, true));
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.getGroup().getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupResponse.getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponse.getGroup().getDescription());
    assertEquals(TEST_GROUP_ICON, groupResponse.getGroup().getIcon());

    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    groupResponse = groupsServiceImpl.saveGroup(TEST_ACCOUNT_IDENTIFIER, null, null, getGroupRequest(true, true));
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.getGroup().getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupResponse.getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponse.getGroup().getDescription());
    assertEquals(TEST_GROUP_ICON, groupResponse.getGroup().getIcon());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void updateGroupTest() throws Exception {
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    List<GroupEntity> groupEntities = new ArrayList<>(List.of(getGroupEntity()));

    when(groupRepository.findAllByParentUniqueId(TEST_ACCOUNT_IDENTIFIER)).thenReturn(groupEntities);
    when(backstageService.findAllByAccountIdentifierAndKind(
             TEST_ACCOUNT_IDENTIFIER, BackstageCatalogEntityTypes.TEMPLATE.kind))
        .thenReturn(new ArrayList<>(List.of(getBackstageCatalogEntity())));
    when(groupRepository.saveAll(any())).thenReturn(groupEntities);
    List<GroupResponse> groupResponses = groupsServiceImpl.updateGroup(
        TEST_ACCOUNT_IDENTIFIER, null, null, new ArrayList<>(List.of(getGroupRequest(true, true))));
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponses.get(0).getGroup().getIdentifier());
    assertEquals(TEST_GROUP_NAME, groupResponses.get(0).getGroup().getName());
    assertEquals(TEST_GROUP_DESCRIPTION, groupResponses.get(0).getGroup().getDescription());
    assertEquals(TEST_GROUP_ICON, groupResponses.get(0).getGroup().getIcon());
    // no order set case
    Exception exception = null;
    try {
      groupsServiceImpl.updateGroup(
          TEST_ACCOUNT_IDENTIFIER, null, null, new ArrayList<>(List.of(getGroupRequest(true, false))));
    } catch (InvalidRequestException e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(INVALID_UPDATE_CASE_NO_ORDER_PROVIDED_ERROR_MESSAGE, exception.getMessage());

    // save case
    GroupEntity savedGroupEntity = getGroupEntity();
    savedGroupEntity.setIdentifier("save" + TEST_GROUP_IDENTIFIER);
    groupEntities = new ArrayList<>(List.of(savedGroupEntity));
    when(groupRepository.findAllByParentUniqueId(TEST_ACCOUNT_IDENTIFIER)).thenReturn(groupEntities);
    exception = null;
    try {
      groupsServiceImpl.updateGroup(
          TEST_ACCOUNT_IDENTIFIER, null, null, new ArrayList<>(List.of(getGroupRequest(true, true))));
    } catch (InvalidRequestException e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(INVALID_UPDATE_CASE_ERROR_MESSAGE, exception.getMessage());

    // no group present case
    exception = null;
    try {
      groupsServiceImpl.updateGroup(TEST_ACCOUNT_IDENTIFIER, null, null, new ArrayList<>());
    } catch (InvalidRequestException e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(NO_GROUPS_TO_UPDATE_ERROR_MESSAGE, exception.getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void getWorkflowsInfoTest() {
    when(backstageService.findAllByAccountIdentifierAndKind(
             TEST_ACCOUNT_IDENTIFIER, BackstageCatalogEntityTypes.TEMPLATE.kind, 0, 10))
        .thenReturn(new PageImpl<>(Collections.singletonList(getBackstageCatalogEntity())));
    Page<BackstageCatalogEntity> backstageCatalogEntities =
        groupsServiceImpl.getWorkflowsInfo(TEST_ACCOUNT_IDENTIFIER, null, null, 0, 10);
    assertNotNull(backstageCatalogEntities);
    assertEquals(TEST_TEMPLATE_UUID, backstageCatalogEntities.getContent().get(0).getEntityUid());
    assertEquals(TEST_CATALOG_TYPE, backstageCatalogEntities.getContent().get(0).getKind());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void getCatalogEntitiesForWorkflowsInfoTest() {
    when(catalogEntityRepository.findAll(any(), any()))
        .thenReturn(new PageImpl<>(Collections.singletonList(getCatalogEntity())));
    Page<CatalogEntity> catalogEntities =
        groupsServiceImpl.getCatalogEntitiesForWorkflowsInfo(TEST_ACCOUNT_IDENTIFIER, 0, 10, null, null, null);
    assertNotNull(catalogEntities);
    assertEquals(TEST_TEMPLATE_UUID, catalogEntities.getContent().get(0).getIdentifier());
    assertEquals(WORKFLOW_KIND, catalogEntities.getContent().get(0).getKind());
  }

  private GroupEntity getGroupEntity() {
    return GroupEntity.builder()
        .id(TEST_GROUP_MONGO_ID)
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .identifier(TEST_GROUP_IDENTIFIER)
        .name(TEST_GROUP_NAME)
        .projectIdentifier(null)
        .orgIdentifier(null)
        .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
        .uniqueId("test")
        .description(TEST_GROUP_DESCRIPTION)
        .icon(TEST_GROUP_ICON)
        .order(TEST_GROUP_ORDER)
        .workflows(new ArrayList<>(List.of(TEST_TEMPLATE_UUID)))
        .build();
  }

  private WorkflowsInfo getWorkflowsInfo() {
    WorkflowsInfo workflowsInfo = new WorkflowsInfo();
    workflowsInfo.setType(TEST_CATALOG_TYPE);
    workflowsInfo.setOwner(TEST_CATALOG_OWNER);
    workflowsInfo.setKind(TEST_CATALOG_TYPE);
    workflowsInfo.setUid(TEST_TEMPLATE_UUID);
    workflowsInfo.setDescription(TEST_CATALOG_DESCRIPTION);
    workflowsInfo.setTitle(TEST_CATALOG_DESCRIPTION);
    workflowsInfo.setName(TEST_CATALOG_NAME);
    return workflowsInfo;
  }

  private CatalogEntity getCatalogEntity() {
    InlineCatalogEntity inlineCatalogEntity = new InlineCatalogEntity();
    inlineCatalogEntity.setName(TEST_CATALOG_NAME);
    inlineCatalogEntity.setDescription(TEST_CATALOG_DESCRIPTION);
    inlineCatalogEntity.setKind(WORKFLOW_KIND);
    inlineCatalogEntity.setOwner(TEST_ACCOUNT_IDENTIFIER);

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

  private Group getGroup(boolean setWorkflows, boolean setOrder) {
    Group group = new Group();
    group.setName(TEST_GROUP_NAME);
    group.setDescription(TEST_GROUP_DESCRIPTION);
    group.setIcon(TEST_GROUP_ICON);
    group.setIdentifier(TEST_GROUP_IDENTIFIER);
    if (setOrder) {
      group.setOrder(TEST_GROUP_ORDER);
    }
    if (setWorkflows) {
      group.setWorkflows(new ArrayList<>(List.of(getWorkflowsInfo())));
    }
    return group;
  }

  private GroupRequest getGroupRequest(boolean setWorkflows, boolean setOrder) {
    GroupRequest groupRequest = new GroupRequest();
    groupRequest.setGroup(getGroup(setWorkflows, setOrder));
    return groupRequest;
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyEntityIdentifierForIdpV2() {
    List<GroupEntity> groupEntities = new ArrayList<>();
    GroupEntity entity1 = getGroupEntity();
    entity1.setWorkflows(new ArrayList<>(List.of("default:template:workflow1", "namespace/template/workflow2")));
    groupEntities.add(entity1);

    when(groupRepository.findAllByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(groupEntities);

    groupsServiceImpl.modifyEntityIdentifierForIdpV2(TEST_ACCOUNT_IDENTIFIER, Collections.emptySet());

    verify(groupRepository).saveAll(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyEntityIdentifierForIdpV2WithEmptyGroups() {
    when(groupRepository.findAllByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(new ArrayList<>());

    groupsServiceImpl.modifyEntityIdentifierForIdpV2(TEST_ACCOUNT_IDENTIFIER, Collections.emptySet());

    verify(groupRepository, times(0)).saveAll(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyEntityIdentifierForIdpV2WithNullConflictedEntityUids() {
    List<GroupEntity> groupEntities = new ArrayList<>();
    GroupEntity entity1 = getGroupEntity();
    entity1.setWorkflows(new ArrayList<>(List.of("account/template/workflow1")));
    groupEntities.add(entity1);

    when(groupRepository.findAllByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(groupEntities);

    groupsServiceImpl.modifyEntityIdentifierForIdpV2(TEST_ACCOUNT_IDENTIFIER, null);

    verify(groupRepository).saveAll(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyEntityIdentifierForIdpV2WithEmptyWorkflows() {
    List<GroupEntity> groupEntities = new ArrayList<>();
    GroupEntity entity1 = getGroupEntity();
    entity1.setWorkflows(new ArrayList<>());
    groupEntities.add(entity1);

    when(groupRepository.findAllByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(groupEntities);

    groupsServiceImpl.modifyEntityIdentifierForIdpV2(TEST_ACCOUNT_IDENTIFIER, Collections.emptySet());

    verify(groupRepository, times(0)).saveAll(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyScopeForEntityIdentifier() {
    List<GroupEntity> groupEntities = new ArrayList<>();
    GroupEntity entity1 = getGroupEntity();
    entity1.setWorkflows(new ArrayList<>(List.of("existingIdentifier", "otherIdentifier")));
    groupEntities.add(entity1);

    groupsServiceImpl.modifyScopeForEntityIdentifier(
        groupEntities, TEST_ACCOUNT_IDENTIFIER, "existingIdentifier", "modifiedIdentifier");

    verify(groupRepository).saveAll(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyScopeForEntityIdentifierWithNoMatchingWorkflow() {
    List<GroupEntity> groupEntities = new ArrayList<>();
    GroupEntity entity1 = getGroupEntity();
    entity1.setWorkflows(new ArrayList<>(List.of("workflow1", "workflow2")));
    groupEntities.add(entity1);

    groupsServiceImpl.modifyScopeForEntityIdentifier(
        groupEntities, TEST_ACCOUNT_IDENTIFIER, "nonExistingIdentifier", "modifiedIdentifier");

    verify(groupRepository, times(0)).saveAll(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testModifyScopeForEntityIdentifierWithEmptyWorkflows() {
    List<GroupEntity> groupEntities = new ArrayList<>();
    GroupEntity entity1 = getGroupEntity();
    entity1.setWorkflows(new ArrayList<>());
    groupEntities.add(entity1);

    groupsServiceImpl.modifyScopeForEntityIdentifier(
        groupEntities, TEST_ACCOUNT_IDENTIFIER, "existingIdentifier", "modifiedIdentifier");

    verify(groupRepository, times(0)).saveAll(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testAddUniqueIdAndParentUniqueIdInfo() throws Exception {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId("parentUniqueId").build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    List<GroupEntity> groupEntities = new ArrayList<>();
    GroupEntity entity1 = getGroupEntity();
    groupEntities.add(entity1);

    when(groupRepository.findAllByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(groupEntities);

    groupsServiceImpl.addUniqueIdAndParentUniqueIdInfo(TEST_ACCOUNT_IDENTIFIER);

    verify(groupRepository).saveAll(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDeleteGroupWithNoGroupsPresent() throws Exception {
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    when(groupRepository.findAllByParentUniqueId(TEST_ACCOUNT_IDENTIFIER)).thenReturn(new ArrayList<>());

    groupsServiceImpl.deleteGroup(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_GROUP_IDENTIFIER);

    verify(groupRepository, times(0)).delete(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDeleteGroupWithGroupNotFound() throws Exception {
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    GroupEntity differentGroup = getGroupEntity();
    differentGroup.setIdentifier("different-identifier");
    when(groupRepository.findAllByParentUniqueId(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(new ArrayList<>(List.of(differentGroup)));

    groupsServiceImpl.deleteGroup(TEST_ACCOUNT_IDENTIFIER, null, null, TEST_GROUP_IDENTIFIER);

    verify(groupRepository, times(0)).delete(any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetCatalogEntitiesForWorkflowsInfoWithSearchTerm() {
    when(catalogEntityRepository.findAll(any(), any()))
        .thenReturn(new PageImpl<>(Collections.singletonList(getCatalogEntity())));
    Page<CatalogEntity> catalogEntities =
        groupsServiceImpl.getCatalogEntitiesForWorkflowsInfo(TEST_ACCOUNT_IDENTIFIER, 0, 10, null, null, "searchTerm");
    assertNotNull(catalogEntities);
    assertEquals(1, catalogEntities.getContent().size());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSaveGroupWithNoWorkflows() throws Exception {
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    GroupEntity groupEntity = getGroupEntity();
    groupEntity.setWorkflows(null);
    when(backstageService.findAllByAccountIdentifierAndKind(
             TEST_ACCOUNT_IDENTIFIER, BackstageCatalogEntityTypes.TEMPLATE.kind))
        .thenReturn(new ArrayList<>(List.of(getBackstageCatalogEntity())));
    when(groupRepository.save(any(GroupEntity.class))).thenReturn(groupEntity);

    GroupResponse groupResponse =
        groupsServiceImpl.saveGroup(TEST_ACCOUNT_IDENTIFIER, null, null, getGroupRequest(false, true));
    assertNotNull(groupResponse);
    assertEquals(TEST_GROUP_IDENTIFIER, groupResponse.getGroup().getIdentifier());
  }
}
