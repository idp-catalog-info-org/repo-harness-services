/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;
import static io.harness.opaclient.model.OpaConstants.OPA_STATUS_ERROR;
import static io.harness.opaclient.model.OpaConstants.OPA_STATUS_WARNING;
import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;
import static io.harness.rule.OwnerRule.AKSHAT_GOYAL;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.engine.governance.GovernanceMetadataErrorDTO;
import io.harness.exception.OPAPolicyEvaluationException;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.accesscontrol.usergroup.UserGroupPermissionUtils;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.Status;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@OwnedBy(PL)
public class UserGroupResourceTest extends CategoryTest {
  @Mock private UserGroupService userGroupService;
  @Mock private DefaultUserGroupService defaultUserGroupService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private UserGroupPermissionUtils userGroupPermissionUtils;
  @InjectMocks UserGroupResource userGroupResource;

  String accountIdentifier = randomAlphabetic(10);
  ScopeInfo scopeInfo = ScopeInfo.builder()
                            .accountIdentifier(accountIdentifier)
                            .uniqueId(accountIdentifier)
                            .scopeType(ScopeLevel.ACCOUNT)
                            .build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void whenListUserGroupsAndCallerHasAccessOnResourceTypeThenReturnAllUserGroups() {
    UserGroupFilterDTO userGroupFilterDTO = UserGroupFilterDTO.builder().accountIdentifier(accountIdentifier).build();
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    when(accessControlClient.hasAccess(
             ResourceScope.of(accountIdentifier, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(true);
    when(scopeInfoService.getScopeInfo(any(), any(), any())).thenReturn(scopeInfo);
    when(userGroupService.list(any(), eq(userGroupFilterDTO), any()))
        .thenReturn(PageUtils.getPage(Collections.emptyList(), 0, 10));
    userGroupResource.list(accountIdentifier, userGroupFilterDTO, pageRequest);
    verify(userGroupService, times(1)).list(scopeInfo, userGroupFilterDTO, getPageRequest(pageRequest));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void whenListUserGroupsAndCallerHasAccessOnSelectedResourceThenReturnPermittedUserGroupsOnly() {
    List<UserGroup> userGroupList = getUserGroupList();
    UserGroupFilterDTO userGroupFilterDTO = UserGroupFilterDTO.builder().accountIdentifier(accountIdentifier).build();
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(10).build();
    Pageable pageable = Pageable.ofSize(50000);
    Page<UserGroup> page = PageUtils.getPage(userGroupList, 0, 10);

    Set<String> uniqueIds = userGroupList.stream().map(UserGroup::getParentUniqueId).collect(Collectors.toSet());
    Map<String, Optional<ScopeInfo>> scopeInfoLists = new HashMap<>();
    scopeInfoLists.put(accountIdentifier, Optional.of(scopeInfo));
    when(scopeInfoService.getScopeInfo(userGroupFilterDTO.getAccountIdentifier(), userGroupFilterDTO.getOrgIdentifier(),
             userGroupFilterDTO.getProjectIdentifier()))
        .thenReturn(scopeInfo);
    when(scopeInfoService.getScopeInfo(userGroupFilterDTO.getAccountIdentifier(), uniqueIds))
        .thenReturn(scopeInfoLists);

    when(accessControlClient.hasAccess(
             ResourceScope.of(accountIdentifier, null, null), Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION))
        .thenReturn(false);
    when(userGroupService.list(scopeInfo, userGroupFilterDTO, pageable)).thenReturn(page);
    when(userGroupService.getPermittedUserGroups(page.getContent())).thenReturn(List.of(userGroupList.get(0)));
    ResponseDTO<PageResponse<UserGroupDTO>> result =
        userGroupResource.list(accountIdentifier, userGroupFilterDTO, pageRequest);
    verify(userGroupService, times(1)).list(scopeInfo, userGroupFilterDTO, pageable);
    verify(userGroupService, times(1)).getPermittedUserGroups(page.getContent());
    assertThat(result.getData().getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = AKSHAT_GOYAL)
  @Category(UnitTests.class)
  public void whenCreateDefaultUserGroupInternalAndServiceReturnsGroupThenReturnUserGroupDTO() {
    UserGroup userGroup = UserGroup.builder()
                              .accountIdentifier(accountIdentifier)
                              .parentUniqueId(accountIdentifier)
                              .uniqueId("defaultUg")
                              .identifier("default_user_group")
                              .name("Default User Group")
                              .build();
    when(scopeInfoService.getScopeInfo(accountIdentifier, null, null)).thenReturn(scopeInfo);
    when(defaultUserGroupService.create(scopeInfo, List.of())).thenReturn(userGroup);

    ResponseDTO<UserGroupDTO> result = userGroupResource.createDefaultUserGroupInternal(accountIdentifier, null, null);

    assertThat(result.getData()).isNotNull();
    assertThat(result.getData().getIdentifier()).isEqualTo("default_user_group");
    assertThat(result.getData().getName()).isEqualTo("Default User Group");
    verify(defaultUserGroupService, times(1)).create(scopeInfo, List.of());
  }

  @Test
  @Owner(developers = AKSHAT_GOYAL)
  @Category(UnitTests.class)
  public void whenCreateDefaultUserGroupInternalAndServiceReturnsNullThenReturnNullData() {
    when(scopeInfoService.getScopeInfo(accountIdentifier, null, null)).thenReturn(scopeInfo);
    when(defaultUserGroupService.create(scopeInfo, List.of())).thenReturn(null);

    ResponseDTO<UserGroupDTO> result = userGroupResource.createDefaultUserGroupInternal(accountIdentifier, null, null);

    assertThat(result.getData()).isNull();
    verify(defaultUserGroupService, times(1)).create(scopeInfo, List.of());
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenCreateAndOpaPolicyReturnsErrorThenReturnGovernanceMetadataWithoutCreatingUserGroup() {
    UserGroupDTO request = UserGroupDTO.builder().accountIdentifier(accountIdentifier).identifier("errorGroup").build();
    GovernanceMetadata error = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_ERROR).setDeny(true).build();
    when(userGroupService.createWithEvaluation(scopeInfo, request))
        .thenThrow(new OPAPolicyEvaluationException("Error: Failed to save the User Group due to Policy enforcement",
            GovernanceMetadataErrorDTO.builder().governanceMetadata(error).build()));

    ResponseDTO<UserGroupDTO> response = userGroupResource.create(accountIdentifier, null, null, request, scopeInfo);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData().getGovernanceMetadata()).isEqualTo(error);
    verify(userGroupService).createWithEvaluation(scopeInfo, request);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenCreateAndOpaPolicyReturnsWarningThenCreateUserGroupAndReturnGovernanceMetadata() {
    UserGroupDTO request =
        UserGroupDTO.builder().accountIdentifier(accountIdentifier).identifier("warningGroup").build();
    GovernanceMetadata warning = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    when(userGroupService.createWithEvaluation(scopeInfo, request))
        .thenReturn(UserGroupDTO.builder()
                        .accountIdentifier(accountIdentifier)
                        .identifier("warningGroup")
                        .governanceMetadata(warning)
                        .version(1L)
                        .build());

    ResponseDTO<UserGroupDTO> response = userGroupResource.create(accountIdentifier, null, null, request, scopeInfo);

    assertThat(response.getData().getIdentifier()).isEqualTo("warningGroup");
    assertThat(response.getData().getGovernanceMetadata()).isEqualTo(warning);
    verify(userGroupService).createWithEvaluation(scopeInfo, request);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenAddMemberAndOpaPolicyReturnsErrorThenEvaluatePostAdditionStateWithoutAddingMember() {
    String userGroupIdentifier = "errorGroup";
    String addedUser = "addedUser";
    GovernanceMetadata error = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_ERROR).setDeny(true).build();
    when(userGroupService.addMemberWithEvaluation(scopeInfo, userGroupIdentifier, addedUser))
        .thenThrow(new OPAPolicyEvaluationException("Error: Failed to save the User Group due to Policy enforcement",
            GovernanceMetadataErrorDTO.builder().governanceMetadata(error).build()));

    ResponseDTO<UserGroupDTO> response =
        userGroupResource.addMember(accountIdentifier, null, null, userGroupIdentifier, addedUser, scopeInfo);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
    assertThat(response.getData().getGovernanceMetadata()).isEqualTo(error);
    verify(userGroupService).addMemberWithEvaluation(scopeInfo, userGroupIdentifier, addedUser);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenRemoveMemberAndOpaPolicyReturnsWarningThenEvaluatePostRemovalStateAndRemoveMember() {
    String userGroupIdentifier = "warningGroup";
    String retainedUser = "retainedUser";
    String removedUser = "removedUser";
    GovernanceMetadata warning = GovernanceMetadata.newBuilder().setStatus(OPA_STATUS_WARNING).setDeny(false).build();
    when(userGroupService.removeMemberWithEvaluation(scopeInfo, userGroupIdentifier, removedUser))
        .thenReturn(UserGroupDTO.builder()
                        .identifier(userGroupIdentifier)
                        .users(List.of(retainedUser))
                        .governanceMetadata(warning)
                        .version(1L)
                        .build());

    ResponseDTO<UserGroupDTO> response =
        userGroupResource.removeMember(accountIdentifier, null, null, userGroupIdentifier, removedUser, scopeInfo);

    assertThat(response.getData().getGovernanceMetadata()).isEqualTo(warning);
    verify(userGroupService).removeMemberWithEvaluation(scopeInfo, userGroupIdentifier, removedUser);
  }

  private List<UserGroup> getUserGroupList() {
    return List.of(UserGroup.builder()
                       .accountIdentifier(accountIdentifier)
                       .parentUniqueId(accountIdentifier)
                       .uniqueId("ug1")
                       .identifier("ug1")
                       .name("ug1")
                       .build(),
        UserGroup.builder()
            .accountIdentifier(accountIdentifier)
            .parentUniqueId(accountIdentifier)
            .uniqueId("ug2")
            .identifier("ug2")
            .name("ug2")
            .build());
  }
}
