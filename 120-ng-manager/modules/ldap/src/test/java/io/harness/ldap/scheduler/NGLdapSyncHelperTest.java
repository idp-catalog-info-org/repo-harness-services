/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.scheduler;

import static io.harness.rule.OwnerRule.NIYASHA;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.ds.remote.DSLdapUserResponse;
import io.harness.ds.remote.DirectoryServiceResourceClient;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapUserResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PL)
@RunWith(MockitoJUnitRunner.class)
public class NGLdapSyncHelperTest extends CategoryTest {
  NgUserService ngUserService = mock(NgUserService.class);
  InviteService inviteService = mock(InviteService.class);
  UserClient userClient = mock(UserClient.class);
  UserGroupService userGroupService = mock(UserGroupService.class);
  ScopeInfoService scopeInfoService = mock(ScopeInfoService.class);
  @Mock Call<ResponseDTO<DSLdapUserResponse>> mockCall;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private DirectoryServiceResourceClient directoryServiceResourceClient;
  @Spy @InjectMocks private NGLdapGroupSyncHelper ldapGroupSyncHelper;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String LDAP_SETTINGS_ID = "SSO_ID";
  private static final ScopeInfo PROJECT_SCOPE_INFO = ScopeInfo.builder()
                                                          .accountIdentifier(ACCOUNT_ID)
                                                          .orgIdentifier(ORG_ID)
                                                          .projectIdentifier(PROJECT_ID)
                                                          .uniqueId("uniqueProjectId")
                                                          .scopeType(ScopeLevel.PROJECT)
                                                          .build();

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testReconcileAllUserGroupsAddUser() throws IOException {
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).build();
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .parentUniqueId("projUniqueId")
                            .ssoGroupId(groupDn)
                            .users(Collections.emptyList())
                            .notificationConfigs(new ArrayList<>())
                            .build();
    UserInfo userInfo = UserInfo.builder().name(testUserName).email(testUserEmail).uuid("USER_ID1").build();
    UserMetadataDTO userMetadataDTO =
        UserMetadataDTO.builder().uuid(userInfo.getUuid()).name(userInfo.getName()).email(userInfo.getEmail()).build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(userGrp.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));

    when(scopeInfoService.getScopeInfo(userGrp.getAccountIdentifier(), Set.of(userGrp.getParentUniqueId())))
        .thenReturn(scopeInfoMap);
    when(userGroupService.update(any(ScopeInfo.class), any(UserGroupDTO.class))).thenReturn(userGrp);
    when(ngUserService.getUserInfoByEmailFromCG(anyString())).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserByEmail(anyString(), anyBoolean())).thenReturn(Optional.of(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), (ScopeInfo) any())).thenReturn(true);
    when(userGroupService.addMember(any(), anyString(), anyString())).thenReturn(userGrp);

    Call<RestResponse<Optional<UserInfo>>> request = mock(Call.class);
    RestResponse<Optional<UserInfo>> mockResponse = new RestResponse<>(Optional.of(userInfo));
    doReturn(Response.success(mockResponse)).when(request).execute();

    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(ngUserService, times(1)).getUserInfoByEmailFromCG(testUserEmail);
    verify(ngUserService, times(1)).getUserByEmail(testUserEmail, false);
    verify(userGroupService, times(1)).update(any(ScopeInfo.class), any(UserGroupDTO.class));
    verify(inviteService, times(1)).create(eq(scopeInfo), any(), anyBoolean(), anyBoolean());
    verify(userGroupService, times(1)).addMember(PROJECT_SCOPE_INFO, userGrpId, userInfo.getUuid());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testReconcileAllUserGroupsAddUserNotAddedToNG() throws IOException {
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).build();
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .parentUniqueId("projUniqueId")
                            .ssoGroupId(groupDn)
                            .users(Collections.singletonList(testUserEmail))
                            .notificationConfigs(new ArrayList<>())
                            .build();
    UserInfo userInfo =
        UserInfo.builder()
            .name(testUserName)
            .email(testUserEmail)
            .uuid("USER_ID1")
            .accounts(Collections.singletonList(GatewayAccountRequestDTO.builder().uuid(ACCOUNT_ID).build()))
            .build();

    UserMetadataDTO userMetadataDTO =
        UserMetadataDTO.builder().uuid(userInfo.getUuid()).name(userInfo.getName()).email(userInfo.getEmail()).build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(userGrp.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));

    when(scopeInfoService.getScopeInfo(userGrp.getAccountIdentifier(), Set.of(userGrp.getParentUniqueId())))
        .thenReturn(scopeInfoMap);
    when(userGroupService.update(any(ScopeInfo.class), any(UserGroupDTO.class))).thenReturn(userGrp);
    when(ngUserService.getUserInfoByEmailFromCG(anyString())).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserByEmail(anyString(), anyBoolean()))
        .thenReturn(Optional.empty(), Optional.of(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), (ScopeInfo) any())).thenReturn(true);

    Call<RestResponse<Optional<UserInfo>>> request = mock(Call.class);
    RestResponse<Optional<UserInfo>> mockResponse = new RestResponse<>(Optional.of(userInfo));
    doReturn(Response.success(mockResponse)).when(request).execute();

    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(ngUserService, times(1)).getUserInfoByEmailFromCG(testUserEmail);
    verify(ngUserService, times(2)).getUserByEmail(testUserEmail, false);
    verify(userGroupService, times(1)).update(any(ScopeInfo.class), any(UserGroupDTO.class));
    verify(inviteService, times(0)).create(eq(scopeInfo), any(), anyBoolean(), anyBoolean());
    verify(userGroupService, times(1)).addMember(PROJECT_SCOPE_INFO, userGrpId, userMetadataDTO.getUuid());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testReconcileAllUserGroupsAddUserNotAddedToNGAccount() throws IOException {
    // Arrange
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    final String userUUID = "USER_ID1";
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).build();
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .parentUniqueId("projUniqueId")
                            .ssoGroupId(groupDn)
                            .users(Collections.singletonList(testUserEmail))
                            .notificationConfigs(new ArrayList<>())
                            .build();
    UserInfo userInfo =
        UserInfo.builder()
            .name(testUserName)
            .email(testUserEmail)
            .uuid(userUUID)
            .accounts(Collections.singletonList(GatewayAccountRequestDTO.builder().uuid(ACCOUNT_ID).build()))
            .build();

    UserMetadataDTO userMetaData =
        UserMetadataDTO.builder().name(randomAlphabetic(10)).uuid(userUUID).email(testUserEmail).build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(userGrp.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));

    when(scopeInfoService.getScopeInfo(userGrp.getAccountIdentifier(), Set.of(userGrp.getParentUniqueId())))
        .thenReturn(scopeInfoMap);
    when(userGroupService.update(any(ScopeInfo.class), any(UserGroupDTO.class))).thenReturn(userGrp);
    when(ngUserService.getUserInfoByEmailFromCG(anyString())).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserByEmail(anyString(), anyBoolean())).thenReturn(Optional.of(userMetaData));
    when(ngUserService.isUserAtScope(anyString(), (ScopeInfo) any())).thenReturn(false, true);

    Call<RestResponse<Optional<UserInfo>>> request = mock(Call.class);
    RestResponse<Optional<UserInfo>> mockResponse = new RestResponse<>(Optional.of(userInfo));
    doReturn(Response.success(mockResponse)).when(request).execute();

    // Act
    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);

    // Assert
    verify(ngUserService, times(1)).getUserInfoByEmailFromCG(testUserEmail);
    verify(ngUserService, times(2)).getUserByEmail(testUserEmail, false);
    verify(userGroupService, times(1)).update(any(ScopeInfo.class), any(UserGroupDTO.class));
    verify(inviteService, times(0)).create(eq(scopeInfo), any(), anyBoolean(), anyBoolean());
    verify(ngUserService, times(1)).getUserInfoByEmailFromCG(testUserEmail);
    verify(ngUserService, times(2)).isUserAtScope(anyString(), (ScopeInfo) any());
    verify(ngUserService, times(1))
        .addUserToScope(userMetaData.getUuid(),
            Scope.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_ID).projectIdentifier(PROJECT_ID).build(),
            emptyList(), emptyList(), UserMembershipUpdateSource.SYSTEM, PROJECT_SCOPE_INFO);
    verify(userGroupService, times(1)).addMember(PROJECT_SCOPE_INFO, userGrpId, userMetaData.getUuid());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testReconcileAllUserGroupsRemoveUser() throws IOException {
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.emptyList())
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .uniqueId("userGroupUniqueId")
                            .parentUniqueId("orgUniqueId")
                            .ssoGroupId(groupDn)
                            .users(Collections.singletonList(testUserEmail))
                            .notificationConfigs(new ArrayList<>())
                            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .uniqueId("orgUniqueId")
                              .scopeType(ScopeLevel.ORGANIZATION)
                              .build();
    UserInfo userInfo = UserInfo.builder().name(testUserName).email(testUserEmail).uuid("User1").build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(userGrp.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, Set.of(userGrp.getParentUniqueId()))).thenReturn(scopeInfoMap);
    when(userGroupService.update(any(ScopeInfo.class), any(UserGroupDTO.class))).thenReturn(userGrp);
    when(ngUserService.listCurrentGenUsers(anyString(), any())).thenReturn(Collections.singletonList(userInfo));
    when(userGroupService.removeMember(any(ScopeInfo.class), anyString(), anyString())).thenReturn(userGrp);

    Call<RestResponse<Optional<UserInfo>>> request = mock(Call.class);
    RestResponse<Optional<UserInfo>> mockResponse = new RestResponse<>(Optional.of(userInfo));
    doReturn(Response.success(mockResponse)).when(request).execute();

    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(ngUserService, times(1))
        .listCurrentGenUsers(ACCOUNT_ID, UserFilterNG.builder().userIds(userGrp.getUsers()).build());
    verify(userGroupService, times(1)).update(any(ScopeInfo.class), any(UserGroupDTO.class));
    verify(userGroupService, times(1)).removeMember(PROJECT_SCOPE_INFO, userGrpId, userInfo.getUuid());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testReconcileAllUserGroupsUpdateUser() throws IOException {
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .parentUniqueId("projUniqueId")
                            .ssoGroupId(groupDn)
                            .users(Collections.singletonList(testUserEmail))
                            .notificationConfigs(new ArrayList<>())
                            .build();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId("projUniqueId")
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    UserInfo userInfo = UserInfo.builder().name(testUserName).email(testUserEmail).uuid("User1").build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(userGrp.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, Set.of(userGrp.getParentUniqueId()))).thenReturn(scopeInfoMap);
    when(userGroupService.update(any(ScopeInfo.class), any(UserGroupDTO.class))).thenReturn(userGrp);
    when(ngUserService.listCurrentGenUsers(anyString(), any())).thenReturn(Collections.singletonList(userInfo));

    Call<RestResponse<Optional<UserInfo>>> request = mock(Call.class);
    RestResponse<Optional<UserInfo>> mockResponse = new RestResponse<>(Optional.of(userInfo));
    doReturn(request).when(userClient).updateUser(any());
    doReturn(Response.success(mockResponse)).when(request).execute();

    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(ngUserService, times(1))
        .listCurrentGenUsers(ACCOUNT_ID, UserFilterNG.builder().userIds(userGrp.getUsers()).build());
    verify(userGroupService, times(1)).update(any(ScopeInfo.class), any(UserGroupDTO.class));
    verify(userClient, times(1)).updateUser(any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testReconcileAllUserGroups_withoutSsoGroupNameChange_shouldNotCreateAudit() throws IOException {
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    final String userGroupName = "testLdapGroup";
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name(userGroupName)
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .parentUniqueId("projUniqueId")
                            .ssoGroupId(groupDn)
                            .ssoGroupName(userGroupName)
                            .users(Collections.singletonList(testUserEmail))
                            .notificationConfigs(new ArrayList<>())
                            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId("projUniqueId")
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    UserInfo userInfo = UserInfo.builder().name(testUserName).email(testUserEmail).uuid(testUserName).build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(userGrp.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, Set.of(userGrp.getParentUniqueId()))).thenReturn(scopeInfoMap);
    when(ngUserService.listCurrentGenUsers(anyString(), any())).thenReturn(Collections.singletonList(userInfo));

    Call<RestResponse<Optional<UserInfo>>> request = mock(Call.class);
    RestResponse<Optional<UserInfo>> mockResponse = new RestResponse<>(Optional.of(userInfo));
    doReturn(request).when(userClient).updateUser(any());
    doReturn(Response.success(mockResponse)).when(request).execute();

    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(ngUserService, times(1))
        .listCurrentGenUsers(ACCOUNT_ID, UserFilterNG.builder().userIds(userGrp.getUsers()).build());
    verify(userGroupService, times(0)).update(any(ScopeInfo.class), any(UserGroupDTO.class));
    verify(userClient, times(1)).updateUser(any());
  }
  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testReconcileAllUserCreateUserInDS_Success() throws IOException {
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    DSLdapUserResponse ldapUserResponse = DSLdapUserResponse.builder().build();
    when(directoryServiceResourceClient.createUsersInDS(anyString(), anyString(), anyString(), any()))
        .thenReturn(mockCall);
    doReturn(mockCall).when(mockCall).clone();
    doReturn(Response.success(ResponseDTO.newResponse(ldapUserResponse))).when(mockCall).execute();
    when(ngUserService.createUserForDS(any())).thenReturn(true);
    when(mockFeatureFlagService.isEnabled(any(FeatureName.class), any())).thenReturn(true);
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .parentUniqueId("projUniqueId")
                            .ssoGroupId(groupDn)
                            .users(Collections.emptyList())
                            .notificationConfigs(new ArrayList<>())
                            .build();
    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(userGrp.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));

    when(scopeInfoService.getScopeInfo(userGrp.getAccountIdentifier(), Set.of(userGrp.getParentUniqueId())))
        .thenReturn(scopeInfoMap);
    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(ngUserService, times(1)).getUserInfoByEmailFromCG(testUserEmail);
    verify(userGroupService, times(1)).update(any(ScopeInfo.class), any(UserGroupDTO.class));
    verify(ngUserService, times(1)).createUserForDS(any());
    verify(directoryServiceResourceClient, times(1)).createUsersInDS(anyString(), anyString(), anyString(), any());
  }
  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testReconcileAllUserCreateUserInDSFailedToGetResponse() throws IOException {
    int totalMembers = 1;

    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    final String userGrpId = "UG1";
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup userGrp = UserGroup.builder()
                            .identifier(userGrpId)
                            .accountIdentifier(ACCOUNT_ID)
                            .orgIdentifier(ORG_ID)
                            .projectIdentifier(PROJECT_ID)
                            .parentUniqueId("projUniqueId")
                            .ssoGroupId(groupDn)
                            .users(Collections.emptyList())
                            .notificationConfigs(new ArrayList<>())
                            .build();
    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(userGrp, response);
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(userGrp.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));

    when(scopeInfoService.getScopeInfo(userGrp.getAccountIdentifier(), Set.of(userGrp.getParentUniqueId())))
        .thenReturn(scopeInfoMap);
    when(mockFeatureFlagService.isEnabled(any(FeatureName.class), any())).thenReturn(true);
    when(ngUserService.getUserInfoByEmailFromCG(anyString())).thenReturn(Optional.empty());
    doReturn(mockCall).when(mockCall).clone();
    doReturn(Response.error(404, mock(ResponseBody.class))).when(mockCall).execute();
    when(directoryServiceResourceClient.createUsersInDS(anyString(), anyString(), anyString(), any()))
        .thenReturn(mockCall);

    ldapGroupSyncHelper.reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(directoryServiceResourceClient, times(1)).createUsersInDS(anyString(), anyString(), anyString(), any());
    verify(ngUserService, times(0)).createUserForDS(any());
  }
}
