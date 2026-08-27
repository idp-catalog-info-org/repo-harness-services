/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.SHRESTH_DEWAN;
import static io.harness.utils.PageUtils.getPageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleReferenceDTO;
import io.harness.accesscontrol.roles.api.RoleDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupAggregateDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.entities.NotificationSettingConfig;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;
import io.harness.ng.core.utils.UserGroupMapper;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
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
import org.springframework.data.domain.PageImpl;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class AggregateUserGroupServiceImplTest extends CategoryTest {
  @Mock private UserGroupService userGroupService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private AccessControlAdminClient accessControlAdminClient;
  @Mock private NgUserService ngUserService;
  @Inject @InjectMocks private AggregateUserGroupServiceImpl aggregateUserGroupService;

  private static final String ACCOUNT_IDENTIFIER = "ACCOUNT_IDENTIFIER";
  private static final String ORG_IDENTIFIER = "ORG_IDENTIFIER";
  private static final String PROJECT_IDENTIFIER = "PROJECT_IDENTIFIER";
  private static final ScopeInfo scopeInfo1 =
      new ScopeInfo(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, ScopeLevel.PROJECT, PROJECT_IDENTIFIER);
  private static final ScopeInfo scopeInfoAcc =
      new ScopeInfo(ACCOUNT_IDENTIFIER, null, null, ScopeLevel.ACCOUNT, ACCOUNT_IDENTIFIER);

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testListAggregateUserGroups() throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(2).build();
    List<NotificationSettingConfig> notificationConfigs = new ArrayList<>();
    List<String> users1 = Lists.newArrayList("u1", "u2", "u3", "u4", "u5", "u6", "u7");
    List<String> users2 = Lists.newArrayList("u3", "u4", "u5", "u6", "u7", "u8");
    List<String> users3 = Lists.newArrayList("u2");
    List<String> users4 = Lists.newArrayList();
    List<UserMetadataDTO> users =
        Lists.newArrayList(getUserMetadata("u1"), getUserMetadata("u2"), getUserMetadata("u3"), getUserMetadata("u4"),
            getUserMetadata("u5"), getUserMetadata("u6"), getUserMetadata("u7"), getUserMetadata("u8"));
    // normal usergroups
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .parentUniqueId("projectUniqueId")
                        .users(users1)
                        .notificationConfigs(notificationConfigs)
                        .build();
    UserGroup ug2 = UserGroup.builder()
                        .identifier("UG2")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .parentUniqueId("projectUniqueId")
                        .users(users2)
                        .notificationConfigs(notificationConfigs)
                        .build();
    // inherited usergroups
    UserGroup ug3 = UserGroup.builder()
                        .identifier("UG3")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .parentUniqueId("orgUniqueId")
                        .users(users3)
                        .notificationConfigs(notificationConfigs)
                        .build();
    UserGroup ug4 = UserGroup.builder()
                        .identifier("UG4")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .parentUniqueId(ACCOUNT_IDENTIFIER)
                        .users(users4)
                        .notificationConfigs(notificationConfigs)
                        .build();
    List<UserGroup> userGroups = Lists.newArrayList(ug1, ug2, ug3, ug4);

    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .uniqueId("projectUniqueId")
                                     .scopeType(ScopeLevel.PROJECT)
                                     .build();
    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_IDENTIFIER)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .uniqueId("orgUniqueId")
                                 .build();

    ScopeInfo accScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_IDENTIFIER)
                                 .uniqueId(ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(accScopeInfo.getUniqueId(), Optional.of(accScopeInfo));
    scopeInfoMap.put(orgScopeInfo.getUniqueId(), Optional.of(orgScopeInfo));
    scopeInfoMap.put(projectScopeInfo.getUniqueId(), Optional.of(projectScopeInfo));

    doReturn(new PageImpl<>(userGroups))
        .when(userGroupService)
        .list(scopeInfo1, null, UserGroupFilterType.EXCLUDE_INHERITED_GROUPS, null, getPageRequest(pageRequest));
    Set<PrincipalDTO> principalDTOSet =
        userGroups.stream()
            .map(userGroup -> {
              ScopeInfo currScopeInfo = scopeInfoMap.get(userGroup.getParentUniqueId()).get();
              return PrincipalDTO.builder()
                  .identifier(userGroup.getIdentifier())
                  .type(USER_GROUP)
                  .scopeLevel(ScopeLevel
                                  .of(currScopeInfo.getAccountIdentifier(), currScopeInfo.getOrgIdentifier(),
                                      currScopeInfo.getProjectIdentifier())
                                  .toString()
                                  .toLowerCase())
                  .build();
            })
            .collect(Collectors.toSet());
    doReturn(users).when(ngUserService).getUserMetadata(anyList());

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder().principalFilter(principalDTOSet).build());
    doReturn(Response.success(ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                                          .roles(new ArrayList<>())
                                                          .resourceGroups(new ArrayList<>())
                                                          .roleAssignments(new ArrayList<>())
                                                          .build())))
        .when(request)
        .execute();

    doReturn(scopeInfoMap).when(scopeInfoService).getScopeInfo(any(), any());
    PageResponse<UserGroupAggregateDTO> response = aggregateUserGroupService.listAggregateUserGroups(
        scopeInfo1, pageRequest, null, 2, UserGroupFilterType.EXCLUDE_INHERITED_GROUPS);
    assertThat(response.getContent()).hasSize(4);

    assertThat(response.getContent().get(0).getUsers().size()).isEqualTo(2);
    assertThat(
        response.getContent().get(0).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .containsExactly("u7", "u6");
    assertThat(response.getContent().get(1).getUsers().size()).isEqualTo(2);
    assertThat(
        response.getContent().get(1).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .containsExactly("u8", "u7");
    assertThat(response.getContent().get(2).getUsers().size()).isEqualTo(1);
    assertThat(
        response.getContent().get(2).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .containsExactly("u2");
    assertThat(response.getContent().get(3).getUsers().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testListAggregateUserGroupsByFilter() throws IOException {
    UserGroupFilterDTO userGroupFilterDTO = UserGroupFilterDTO.builder()
                                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                .orgIdentifier(ORG_IDENTIFIER)
                                                .projectIdentifier(PROJECT_IDENTIFIER)
                                                .filterType(UserGroupFilterType.EXCLUDE_INHERITED_GROUPS)
                                                .build();

    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(2).build();
    List<NotificationSettingConfig> notificationConfigs = new ArrayList<>();
    List<String> users1 = Lists.newArrayList("u1", "u2", "u3", "u4", "u5", "u6", "u7");
    List<String> users2 = Lists.newArrayList("u3", "u4", "u5", "u6", "u7", "u8");
    List<String> users3 = Lists.newArrayList("u2");
    List<String> users4 = Lists.newArrayList();
    List<UserMetadataDTO> users =
        Lists.newArrayList(getUserMetadata("u1"), getUserMetadata("u2"), getUserMetadata("u3"), getUserMetadata("u4"),
            getUserMetadata("u5"), getUserMetadata("u6"), getUserMetadata("u7"), getUserMetadata("u8"));
    // normal usergroups
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .parentUniqueId("projectUniqueId")
                        .users(users1)
                        .notificationConfigs(notificationConfigs)
                        .build();
    UserGroup ug2 = UserGroup.builder()
                        .identifier("UG2")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .parentUniqueId("projectUniqueId")
                        .users(users2)
                        .notificationConfigs(notificationConfigs)
                        .build();
    // inherited usergroups
    UserGroup ug3 = UserGroup.builder()
                        .identifier("UG3")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .parentUniqueId("orgUniqueId")
                        .users(users3)
                        .notificationConfigs(notificationConfigs)
                        .build();
    UserGroup ug4 = UserGroup.builder()
                        .identifier("UG4")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .parentUniqueId(ACCOUNT_IDENTIFIER)
                        .users(users4)
                        .notificationConfigs(notificationConfigs)
                        .build();
    List<UserGroup> userGroups = Lists.newArrayList(ug1, ug2, ug3, ug4);

    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .uniqueId("projectUniqueId")
                                     .scopeType(ScopeLevel.PROJECT)
                                     .build();
    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_IDENTIFIER)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .uniqueId("orgUniqueId")
                                 .build();

    ScopeInfo accScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_IDENTIFIER)
                                 .uniqueId(ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(accScopeInfo.getUniqueId(), Optional.of(accScopeInfo));
    scopeInfoMap.put(orgScopeInfo.getUniqueId(), Optional.of(orgScopeInfo));
    scopeInfoMap.put(projectScopeInfo.getUniqueId(), Optional.of(projectScopeInfo));

    doReturn(new PageImpl<>(userGroups))
        .when(userGroupService)
        .listByViewPermission(userGroupFilterDTO, getPageRequest(pageRequest));
    Set<PrincipalDTO> principalDTOSet =
        userGroups.stream()
            .map(userGroup -> {
              ScopeInfo currScopeInfo = scopeInfoMap.get(userGroup.getParentUniqueId()).get();

              return PrincipalDTO.builder()
                  .identifier(userGroup.getIdentifier())
                  .type(USER_GROUP)
                  .scopeLevel(ScopeLevel
                                  .of(currScopeInfo.getAccountIdentifier(), currScopeInfo.getOrgIdentifier(),
                                      currScopeInfo.getProjectIdentifier())
                                  .toString()
                                  .toLowerCase())
                  .build();
            })
            .collect(Collectors.toSet());
    doReturn(users).when(ngUserService).getUserMetadata(anyList());

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder().principalFilter(principalDTOSet).build());
    doReturn(Response.success(ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                                          .roles(new ArrayList<>())
                                                          .resourceGroups(new ArrayList<>())
                                                          .roleAssignments(new ArrayList<>())
                                                          .build())))
        .when(request)
        .execute();
    doReturn(scopeInfoMap).when(scopeInfoService).getScopeInfo(any(), any());
    PageResponse<UserGroupAggregateDTO> response =
        aggregateUserGroupService.listAggregateUserGroupsByFilter(projectScopeInfo, pageRequest, 2, userGroupFilterDTO);
    assertThat(response.getContent()).hasSize(4);

    assertThat(response.getContent().get(0).getUsers().size()).isEqualTo(2);
    assertThat(
        response.getContent().get(0).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .containsExactly("u7", "u6");
    assertThat(response.getContent().get(1).getUsers().size()).isEqualTo(2);
    assertThat(
        response.getContent().get(1).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .containsExactly("u8", "u7");
    assertThat(response.getContent().get(2).getUsers().size()).isEqualTo(1);
    assertThat(
        response.getContent().get(2).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .containsExactly("u2");
    assertThat(response.getContent().get(3).getUsers().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListAggregateUserGroupsGetAllUsers() throws IOException {
    PageRequest pageRequest = PageRequest.builder().pageIndex(0).pageSize(2).build();
    List<NotificationSettingConfig> notificationConfigs = new ArrayList<>();
    List<String> users1 = Lists.newArrayList("u1", "u2", "u3", "u4", "u5", "u6", "u7");
    List<UserMetadataDTO> users =
        Lists.newArrayList(getUserMetadata("u1"), getUserMetadata("u2"), getUserMetadata("u3"), getUserMetadata("u4"),
            getUserMetadata("u5"), getUserMetadata("u6"), getUserMetadata("u7"));
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .parentUniqueId("projectUniqueId")
                        .users(users1)
                        .notificationConfigs(notificationConfigs)
                        .build();
    List<UserGroup> userGroups = Lists.newArrayList(ug1);

    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .uniqueId("projectUniqueId")
                                     .scopeType(ScopeLevel.PROJECT)
                                     .build();
    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_IDENTIFIER)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .uniqueId("orgUniqueId")
                                 .build();

    ScopeInfo accScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_IDENTIFIER)
                                 .uniqueId(ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(accScopeInfo.getUniqueId(), Optional.of(accScopeInfo));
    scopeInfoMap.put(orgScopeInfo.getUniqueId(), Optional.of(orgScopeInfo));
    scopeInfoMap.put(projectScopeInfo.getUniqueId(), Optional.of(projectScopeInfo));

    doReturn(scopeInfo1).when(scopeInfoService).getScopeInfo(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    doReturn(new PageImpl<>(userGroups))
        .when(userGroupService)
        .list(scopeInfo1, null, UserGroupFilterType.INCLUDE_INHERITED_GROUPS, null, getPageRequest(pageRequest));
    Set<PrincipalDTO> principalDTOSet =
        userGroups.stream()
            .map(userGroup -> {
              ScopeInfo currScopeInfo = scopeInfoMap.get(userGroup.getParentUniqueId()).get();

              return PrincipalDTO.builder()
                  .identifier(userGroup.getIdentifier())
                  .type(USER_GROUP)
                  .scopeLevel(ScopeLevel
                                  .of(currScopeInfo.getAccountIdentifier(), currScopeInfo.getOrgIdentifier(),
                                      currScopeInfo.getProjectIdentifier())
                                  .toString()
                                  .toLowerCase())
                  .build();
            })
            .collect(Collectors.toSet());
    doReturn(users).when(ngUserService).getUserMetadata(anyList());

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder().principalFilter(principalDTOSet).build());
    doReturn(Response.success(ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                                          .roles(new ArrayList<>())
                                                          .resourceGroups(new ArrayList<>())
                                                          .roleAssignments(new ArrayList<>())
                                                          .build())))
        .when(request)
        .execute();

    doReturn(scopeInfoMap).when(scopeInfoService).getScopeInfo(any(), any());
    PageResponse<UserGroupAggregateDTO> response = aggregateUserGroupService.listAggregateUserGroups(
        scopeInfo1, pageRequest, null, -1, UserGroupFilterType.INCLUDE_INHERITED_GROUPS);
    assertThat(response.getContent()).hasSize(1);

    assertThat(response.getContent().get(0).getUsers().size()).isEqualTo(7);
    assertThat(
        response.getContent().get(0).getUsers().stream().map(UserMetadataDTO::getUuid).collect(Collectors.toList()))
        .contains("u1", "u2", "u3", "u4", "u5", "u6", "u7");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAggregateUserGroupsWithSameRoleAssignmentScope() throws IOException {
    List<NotificationSettingConfig> notificationConfigs = new ArrayList<>();
    List<String> users1 = Lists.newArrayList("u1", "u2", "u3", "u4", "u5", "u6", "u7");
    List<UserMetadataDTO> users =
        Lists.newArrayList(getUserMetadata("u1"), getUserMetadata("u2"), getUserMetadata("u3"), getUserMetadata("u4"),
            getUserMetadata("u5"), getUserMetadata("u6"), getUserMetadata("u7"));
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId("projectId")
                              .build();
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .uniqueId("uniqueUserGroupId")
                        .parentUniqueId("projectUniqueId")
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .users(users1)
                        .notificationConfigs(notificationConfigs)
                        .build();

    doReturn(Optional.of(ug1)).when(userGroupService).get(scopeInfo1, "UG1");
    PrincipalDTO principalDTO =
        PrincipalDTO.builder()
            .identifier("UG1")
            .type(USER_GROUP)
            .scopeLevel(ScopeLevel.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER).toString().toLowerCase())
            .build();
    doReturn(users).when(ngUserService).getUserMetadata(anyList());

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder().principalFilter(Collections.singleton(principalDTO)).build());
    doReturn(Response.success(ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                                          .roles(new ArrayList<>())
                                                          .resourceGroups(new ArrayList<>())
                                                          .roleAssignments(new ArrayList<>())
                                                          .build())))
        .when(request)
        .execute();

    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .uniqueId("projectUniqueId")
                                     .scopeType(ScopeLevel.PROJECT)
                                     .build();
    ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_IDENTIFIER)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .scopeType(ScopeLevel.ORGANIZATION)
                                 .uniqueId("orgUniqueId")
                                 .build();

    ScopeInfo accScopeInfo = ScopeInfo.builder()
                                 .accountIdentifier(ACCOUNT_IDENTIFIER)
                                 .uniqueId(ACCOUNT_IDENTIFIER)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(accScopeInfo.getUniqueId(), Optional.of(accScopeInfo));
    scopeInfoMap.put(orgScopeInfo.getUniqueId(), Optional.of(orgScopeInfo));
    scopeInfoMap.put(projectScopeInfo.getUniqueId(), Optional.of(projectScopeInfo));
    doReturn(scopeInfoMap).when(scopeInfoService).getScopeInfo(any(), any());

    UserGroupAggregateDTO response = aggregateUserGroupService.getAggregatedUserGroup(scopeInfo1, "UG1",
        ScopeDTO.builder()
            .accountIdentifier(ACCOUNT_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .build());

    assertThat(response.getUserGroupDTO()).isEqualTo(UserGroupMapper.toDTO(projectScopeInfo, ug1));
    assertThat(response.getRoleAssignmentsMetadataDTO()).isEqualTo(null);
    assertThat(response.getUsers()).isEqualTo(users);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAggregateUserGroupsWithDifferentRoleAssignmentScope() throws IOException {
    // get account level usergroup with project level role assignments
    // roleassignmentDTO is child of usergroup scope is checked in resource layer
    List<NotificationSettingConfig> notificationConfigs = new ArrayList<>();
    List<String> users1 = Lists.newArrayList("u1", "u2", "u3", "u4", "u5", "u6", "u7");
    List<UserMetadataDTO> users =
        Lists.newArrayList(getUserMetadata("u1"), getUserMetadata("u2"), getUserMetadata("u3"), getUserMetadata("u4"),
            getUserMetadata("u5"), getUserMetadata("u6"), getUserMetadata("u7"));
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .uniqueId(ACCOUNT_IDENTIFIER)
                              .build();
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .uniqueId("userGroupUniqueId")
                        .parentUniqueId(ACCOUNT_IDENTIFIER)
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .users(users1)
                        .notificationConfigs(notificationConfigs)
                        .build();

    doReturn(Optional.of(ug1)).when(userGroupService).get(scopeInfoAcc, "UG1");
    PrincipalDTO principalDTO = PrincipalDTO.builder()
                                    .identifier("UG1")
                                    .type(USER_GROUP)
                                    .scopeLevel(ScopeLevel.of(ACCOUNT_IDENTIFIER, null, null).toString().toLowerCase())
                                    .build();
    doReturn(users).when(ngUserService).getUserMetadata(anyList());

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder().principalFilter(Collections.singleton(principalDTO)).build());
    ScopeDTO roleAssignmentScopeDTO = ScopeDTO.builder()
                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                          .build();
    doReturn(
        Response.success(ResponseDTO.newResponse(
            RoleAssignmentAggregateResponseDTO.builder()
                .roles(new ArrayList<>(Collections.singleton(
                    RoleResponseDTO.builder()
                        .harnessManaged(false)
                        .role(RoleDTO.builder()
                                  .identifier("ROLE1")
                                  .name("ROLE1")
                                  .allowedScopeLevels(Collections.singleton(RoleDTO.ScopeLevel.ACCOUNT))
                                  .build())
                        .scope(ScopeDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).build())
                        .build())))
                .resourceGroups(new ArrayList<>(
                    Collections.singleton(ResourceGroupDTO.builder().identifier("RG1").name("RG1").build())))
                .roleAssignments(new ArrayList<>(Collections.singleton(
                    RoleAssignmentDTO.builder()
                        .identifier("RA1")
                        .roleIdentifier("ROLE1")
                        .roleReference(RoleReferenceDTO.builder().identifier("ROLE1").scopeLevel("account").build())
                        .resourceGroupIdentifier("RG1")
                        .disabled(false)
                        .managed(false)
                        .principal(principalDTO)
                        .build())))
                .scope(roleAssignmentScopeDTO)
                .build())))
        .when(request)
        .execute();

    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(scopeInfoAcc.getUniqueId(), Optional.of(scopeInfoAcc));
    doReturn(scopeInfoMap).when(scopeInfoService).getScopeInfo(any(), any());
    UserGroupAggregateDTO response =
        aggregateUserGroupService.getAggregatedUserGroup(scopeInfoAcc, "UG1", roleAssignmentScopeDTO);

    assertThat(response.getUserGroupDTO()).isEqualTo(UserGroupMapper.toDTO(scopeInfo, ug1));
    assertThat(response.getRoleAssignmentsMetadataDTO().size()).isEqualTo(1);
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getIdentifier()).isEqualTo("RA1");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getRoleIdentifier()).isEqualTo("ROLE1");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getResourceGroupIdentifier()).isEqualTo("RG1");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).isManagedRole()).isEqualTo(false);
    assertThat(response.getUsers()).isEqualTo(users);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetAggregateUserGroupsWhenUsergroupDNE() {
    // get account level usergroup with project level role assignments
    // roleassignmentDTO is child of usergroup scope is checked in resource layer
    doReturn(Optional.empty()).when(userGroupService).get(scopeInfo1, "UG1");

    ScopeDTO roleAssignmentScopeDTO = ScopeDTO.builder()
                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                          .build();

    try {
      aggregateUserGroupService.getAggregatedUserGroup(scopeInfo1, "UG1", roleAssignmentScopeDTO);
      fail("Expected failure as usergroup does not exist");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo(String.format("User Group is not available %s:%s:%s:%s", ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
              PROJECT_IDENTIFIER, "UG1"));
    }
  }

  @Test
  @Owner(developers = SHRESTH_DEWAN)
  @Category(UnitTests.class)
  public void getAggregateUserGroupsWithSameRoleAtDifferentScopeLevels() throws IOException {
    // Tests the fix where roles with the same identifier but different scope levels
    // no longer collide in the roleMap (key is now roleIdentifier|scopeLevel)
    List<NotificationSettingConfig> notificationConfigs = new ArrayList<>();
    List<String> users1 = Lists.newArrayList("u1", "u2");
    List<UserMetadataDTO> users = Lists.newArrayList(getUserMetadata("u1"), getUserMetadata("u2"));
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .uniqueId("uniqueUserGroupId")
                        .parentUniqueId("projectUniqueId")
                        .users(users1)
                        .notificationConfigs(notificationConfigs)
                        .build();

    doReturn(Optional.of(ug1)).when(userGroupService).get(scopeInfo1, "UG1");
    PrincipalDTO principalDTO =
        PrincipalDTO.builder()
            .identifier("UG1")
            .type(USER_GROUP)
            .scopeLevel(ScopeLevel.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER).toString().toLowerCase())
            .build();
    doReturn(users).when(ngUserService).getUserMetadata(anyList());

    Call<ResponseDTO<RoleAssignmentAggregateResponseDTO>> request = mock(Call.class);
    doReturn(request)
        .when(accessControlAdminClient)
        .getAggregatedFilteredRoleAssignments(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER,
            RoleAssignmentFilterDTO.builder().principalFilter(Collections.singleton(principalDTO)).build());

    // Two roles with the SAME identifier but different scope levels
    List<RoleResponseDTO> roles = new ArrayList<>();
    roles.add(RoleResponseDTO.builder()
                  .harnessManaged(true)
                  .role(RoleDTO.builder()
                            .identifier("SHARED_ROLE")
                            .name("SharedRole_Account")
                            .allowedScopeLevels(Collections.singleton(RoleDTO.ScopeLevel.ACCOUNT))
                            .build())
                  .scope(ScopeDTO.builder().accountIdentifier(ACCOUNT_IDENTIFIER).build())
                  .build());
    roles.add(RoleResponseDTO.builder()
                  .harnessManaged(false)
                  .role(RoleDTO.builder()
                            .identifier("SHARED_ROLE")
                            .name("SharedRole_Project")
                            .allowedScopeLevels(Collections.singleton(RoleDTO.ScopeLevel.PROJECT))
                            .build())
                  .scope(ScopeDTO.builder()
                             .accountIdentifier(ACCOUNT_IDENTIFIER)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJECT_IDENTIFIER)
                             .build())
                  .build());

    List<ResourceGroupDTO> resourceGroups = new ArrayList<>();
    resourceGroups.add(ResourceGroupDTO.builder().identifier("RG1").name("RG1").build());
    resourceGroups.add(ResourceGroupDTO.builder().identifier("RG2").name("RG2").build());

    // Two role assignments referencing the same role identifier but at different scope levels
    List<RoleAssignmentDTO> roleAssignments = new ArrayList<>();
    roleAssignments.add(
        RoleAssignmentDTO.builder()
            .identifier("RA1")
            .roleIdentifier("SHARED_ROLE")
            .roleReference(RoleReferenceDTO.builder().identifier("SHARED_ROLE").scopeLevel("account").build())
            .resourceGroupIdentifier("RG1")
            .disabled(false)
            .managed(true)
            .principal(principalDTO)
            .build());
    roleAssignments.add(
        RoleAssignmentDTO.builder()
            .identifier("RA2")
            .roleIdentifier("SHARED_ROLE")
            .roleReference(RoleReferenceDTO.builder().identifier("SHARED_ROLE").scopeLevel("project").build())
            .resourceGroupIdentifier("RG2")
            .disabled(false)
            .managed(false)
            .principal(principalDTO)
            .build());

    ScopeDTO roleAssignmentScopeDTO = ScopeDTO.builder()
                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                          .build();

    doReturn(Response.success(ResponseDTO.newResponse(RoleAssignmentAggregateResponseDTO.builder()
                                                          .roles(roles)
                                                          .resourceGroups(resourceGroups)
                                                          .roleAssignments(roleAssignments)
                                                          .scope(roleAssignmentScopeDTO)
                                                          .build())))
        .when(request)
        .execute();

    ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                     .accountIdentifier(ACCOUNT_IDENTIFIER)
                                     .orgIdentifier(ORG_IDENTIFIER)
                                     .projectIdentifier(PROJECT_IDENTIFIER)
                                     .uniqueId("projectUniqueId")
                                     .scopeType(ScopeLevel.PROJECT)
                                     .build();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(projectScopeInfo.getUniqueId(), Optional.of(projectScopeInfo));
    doReturn(scopeInfoMap).when(scopeInfoService).getScopeInfo(any(), any());

    UserGroupAggregateDTO response =
        aggregateUserGroupService.getAggregatedUserGroup(scopeInfo1, "UG1", roleAssignmentScopeDTO);

    assertThat(response.getRoleAssignmentsMetadataDTO()).hasSize(2);
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getIdentifier()).isEqualTo("RA1");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getRoleIdentifier()).isEqualTo("SHARED_ROLE");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getRoleName()).isEqualTo("SharedRole_Account");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getResourceGroupIdentifier()).isEqualTo("RG1");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).isManagedRole()).isTrue();
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).isManagedRoleAssignment()).isTrue();
    assertThat(response.getRoleAssignmentsMetadataDTO().get(0).getRoleScopeLevel()).isEqualTo("account");

    assertThat(response.getRoleAssignmentsMetadataDTO().get(1).getIdentifier()).isEqualTo("RA2");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(1).getRoleIdentifier()).isEqualTo("SHARED_ROLE");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(1).getRoleName()).isEqualTo("SharedRole_Project");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(1).getResourceGroupIdentifier()).isEqualTo("RG2");
    assertThat(response.getRoleAssignmentsMetadataDTO().get(1).isManagedRole()).isFalse();
    assertThat(response.getRoleAssignmentsMetadataDTO().get(1).isManagedRoleAssignment()).isFalse();
    assertThat(response.getRoleAssignmentsMetadataDTO().get(1).getRoleScopeLevel()).isEqualTo("project");

    assertThat(response.getUsers()).isEqualTo(users);
  }

  private static UserMetadataDTO getUserMetadata(String user) {
    return UserMetadataDTO.builder().name(user).email(user).uuid(user).build();
  }
}
