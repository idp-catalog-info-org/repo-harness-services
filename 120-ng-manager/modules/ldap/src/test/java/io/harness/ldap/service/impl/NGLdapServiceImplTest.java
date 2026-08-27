/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.service.impl;

import static io.harness.ldap.service.impl.NGLdapServiceImpl.LDAP_TASK_DEFAULT_MAXIMUM_TIMEOUT_MILLIS;
import static io.harness.ldap.service.impl.NGLdapServiceImpl.LDAP_TASK_DEFAULT_MINIMUM_TIMEOUT_MILLIS;
import static io.harness.rule.OwnerRule.ADITYA;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;
import static io.harness.rule.OwnerRule.SHASHANK;

import static software.wings.beans.TaskType.NG_LDAP_SEARCH_GROUPS;
import static software.wings.beans.sso.LdapTestResponse.Status.FAILURE;
import static software.wings.beans.sso.LdapTestResponse.Status.SUCCESS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataAndPasswordDetail;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSearchTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSyncTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapTestAuthenticationTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.ldap.scheduler.NGLdapGroupSyncHelper;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.dto.LdapSettings;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapUserResponse;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.helpers.ext.ldap.LdapResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PL)
@RunWith(MockitoJUnitRunner.class)
public class NGLdapServiceImplTest extends CategoryTest {
  public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
  public static final String DELEGATE_NOT_AVAILABLE =
      String.format(HintException.DELEGATE_NOT_AVAILABLE, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK);
  TaskSetupAbstractionHelper taskSetupAbstractionHelper = mock(TaskSetupAbstractionHelper.class);
  DelegateGrpcClientWrapper delegateGrpcClientWrapper = mock(DelegateGrpcClientWrapper.class);
  AuthSettingsManagerClient managerClient = mock(AuthSettingsManagerClient.class);
  NGLdapGroupSyncHelper groupSyncHelper = mock(NGLdapGroupSyncHelper.class);
  UserGroupService userGroupService = mock(UserGroupService.class);

  ScopeInfoService scopeInfoService = mock(ScopeInfoService.class);

  @Spy @InjectMocks private NGLdapServiceImpl ngLdapService;

  private LdapSettingsWithEncryptedDataDetail ldapSettingsWithEncryptedDataDetail;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String LDAP_SETTINGS_ID = "SSO_ID";
  private static final ScopeInfo PROJECT_SCOPE_INFO = ScopeInfo.builder()
                                                          .accountIdentifier(ACCOUNT_ID)
                                                          .orgIdentifier(ORG_ID)
                                                          .projectIdentifier(PROJECT_ID)
                                                          .uniqueId(PROJECT_ID)
                                                          .scopeType(ScopeLevel.PROJECT)
                                                          .build();

  @Before
  public void setup() {
    initMocks(this);
    LdapConnectionSettings settings = new LdapConnectionSettings();
    ldapSettingsWithEncryptedDataDetail =
        LdapSettingsWithEncryptedDataDetail.builder()
            .ldapSettings(LdapSettings.builder().uuid(LDAP_SETTINGS_ID).connectionSettings(settings).build())
            .encryptedDataDetail(EncryptedDataDetail.builder().build())
            .build();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapConnectionSuccessfulAndUnsuccessful() throws IOException {
    final String accountId = "testAccountId";
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    software.wings.beans.sso.LdapSettingsDTO ldapSettings = getLdapSettings(accountId);
    LdapTestResponse successfulTestResponse =
        LdapTestResponse.builder().status(SUCCESS).message("Connection Successful").build();

    mockCgClientCall();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(successfulTestResponse).build());

    LdapTestResponse ldapTestResponse = ngLdapService.validateLdapConnectionSettings(scopeInfo, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(successfulTestResponse.getStatus(), ldapTestResponse.getStatus());

    // Test unsuccessful
    ldapTestResponse = null;
    LdapTestResponse unsuccessfulTestResponse =
        LdapTestResponse.builder().status(FAILURE).message(INVALID_CREDENTIALS).build();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(unsuccessfulTestResponse).build());

    try {
      ldapTestResponse = ngLdapService.validateLdapConnectionSettings(scopeInfo, ldapSettings);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception ex) {
      assertNull(ldapTestResponse);
      assertThat(ex).isInstanceOf(HintException.class);
      assertEquals(ex.getMessage(), HintException.CHECK_LDAP_CONNECTION);
    }
  }

  @Test
  @Owner(developers = ADITYA)
  @Category(UnitTests.class)
  public void testWithInvalidHost() throws IOException {
    final String accountId = "testAccountId";
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();

    software.wings.beans.sso.LdapSettingsDTO ldapSettings = getLdapSettings(accountId);
    ldapSettings.getConnectionSettings().setHost("abc");
    LdapTestResponse unsuccessfulTestResponse =
        LdapTestResponse.builder().status(FAILURE).message(INVALID_CREDENTIALS).build();
    mockCgClientCall();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(unsuccessfulTestResponse).build());

    assertThatThrownBy(() -> ngLdapService.validateLdapConnectionSettings(scopeInfo, ldapSettings))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapConnectionException() throws IOException {
    final String accountId = "testAccountId";
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    software.wings.beans.sso.LdapSettingsDTO ldapSettings = getLdapSettings(accountId);
    mockCgClientCall();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(buildErrorNotifyResponseData());

    LdapTestResponse ldapTestResponse = null;
    try {
      ldapTestResponse = ngLdapService.validateLdapConnectionSettings(scopeInfo, ldapSettings);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception ex) {
      assertNull(ldapTestResponse);
      assertThat(ex).isInstanceOf(HintException.class);
      assertEquals(ex.getMessage(), DELEGATE_NOT_AVAILABLE);
    }
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchLdapGroups() throws IOException {
    int totalMembers = 4;
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(request).when(managerClient).getLdapSettingsUsingAccountId(ACCOUNT_ID);
    doReturn(Response.success(mockResponse)).when(request).execute();
    final String groupNameQuery = "grpName";
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name(groupNameQuery)
                                     .description("desc")
                                     .dn("uid=ldap_user1,ou=Users,dc=jumpcloud,dc=com")
                                     .totalMembers(totalMembers)
                                     .build();
    Collection<LdapGroupResponse> matchedGroups = Collections.singletonList(response);
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapGroupSearchTaskResponse.builder().ldapListGroupsResponses(matchedGroups).build());
    Collection<LdapGroupResponse> resultUserGroups =
        ngLdapService.searchLdapGroupsByName(PROJECT_SCOPE_INFO, "TestLdapID", groupNameQuery);
    assertNotNull(resultUserGroups);
    assertThat(resultUserGroups.size()).isEqualTo(1);
    assertThat(resultUserGroups.iterator().next().getTotalMembers()).isEqualTo(totalMembers);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchLdapGroupsEmptyListWhenLdapSettingsNotFound() {
    final String groupNameQuery = "grpName";
    doReturn(null).when(managerClient).getLdapSettingsUsingAccountId(ACCOUNT_ID);
    Collection<LdapGroupResponse> resultUserGroups = null;
    try {
      resultUserGroups = ngLdapService.searchLdapGroupsByName(PROJECT_SCOPE_INFO, "TestLdapID", groupNameQuery);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception exc) {
      assertNull(resultUserGroups);
      assertThat(exc).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSearchLdapGroupsDelegateResponseExceptionCase() throws IOException {
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(request).when(managerClient).getLdapSettingsUsingAccountId(ACCOUNT_ID);
    doReturn(Response.success(mockResponse)).when(request).execute();
    final String groupNameQuery = "grpName";
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(buildErrorNotifyResponseData());
    Collection<LdapGroupResponse> resultUserGroups = null;

    try {
      resultUserGroups = ngLdapService.searchLdapGroupsByName(PROJECT_SCOPE_INFO, "TestLdapID", groupNameQuery);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception exc) {
      assertNull(resultUserGroups);
      assertThat(exc).isInstanceOf(HintException.class);
      assertEquals(exc.getMessage(), DELEGATE_NOT_AVAILABLE);
    }
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapUserQuerySuccessfulAndUnsuccessful() throws IOException {
    final String accountId = "testAccountId";
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    software.wings.beans.sso.LdapSettingsDTO ldapSettings = getLdapSettings(accountId);
    LdapTestResponse successfulTestResponse =
        LdapTestResponse.builder()
            .status(SUCCESS)
            .message("Configuration looks good. Server returned non-zero number of records")
            .build();
    mockCgClientCall();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(successfulTestResponse).build());

    LdapTestResponse ldapTestResponse = ngLdapService.validateLdapUserSettings(scopeInfo, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(successfulTestResponse.getStatus(), ldapTestResponse.getStatus());

    LdapTestResponse unsuccessfulTestResponse = LdapTestResponse.builder().status(FAILURE).message(null).build();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(unsuccessfulTestResponse).build());

    ldapTestResponse = ngLdapService.validateLdapUserSettings(scopeInfo, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(unsuccessfulTestResponse.getStatus(), ldapTestResponse.getStatus());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapGroupQuerySuccessfulAndUnsuccessful() throws IOException {
    final String accountId = "testAccountId";
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    software.wings.beans.sso.LdapSettingsDTO ldapSettings = getLdapSettings(accountId);
    LdapTestResponse successfulTestResponse =
        LdapTestResponse.builder()
            .status(SUCCESS)
            .message("Configuration looks good. Server returned non-zero number of records")
            .build();
    mockCgClientCall();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(successfulTestResponse).build());

    LdapTestResponse ldapTestResponse = ngLdapService.validateLdapGroupSettings(scopeInfo, ldapSettings);

    assertNotNull(ldapTestResponse);
    assertEquals(successfulTestResponse.getStatus(), ldapTestResponse.getStatus());

    LdapTestResponse unsuccessfulTestResponse =
        LdapTestResponse.builder()
            .status(FAILURE)
            .message("Please check configuration. Server returned zero records for the configuration.")
            .build();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(unsuccessfulTestResponse).build());

    try {
      ldapTestResponse = ngLdapService.validateLdapGroupSettings(scopeInfo, ldapSettings);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(HintException.class);
      assertEquals(ex.getMessage(), HintException.LDAP_ATTRIBUTES_INCORRECT);
    }
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapUserQueryException() throws IOException {
    final String accountId = "testAccountId";
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    software.wings.beans.sso.LdapSettingsDTO ldapSettings = getLdapSettings(accountId);
    mockCgClientCall();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(buildErrorNotifyResponseData());

    LdapTestResponse ldapTestResponse = null;
    try {
      ldapTestResponse = ngLdapService.validateLdapUserSettings(scopeInfo, ldapSettings);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception ex) {
      assertNull(ldapTestResponse);
      assertThat(ex).isInstanceOf(HintException.class);
      assertEquals(ex.getMessage(), DELEGATE_NOT_AVAILABLE);
    }
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testLdapGroupQueryException() throws IOException {
    final String accountId = "testAccountId";
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    software.wings.beans.sso.LdapSettingsDTO ldapSettings = getLdapSettings(accountId);
    mockCgClientCall();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(buildErrorNotifyResponseData());

    LdapTestResponse ldapTestResponse = null;
    try {
      ldapTestResponse = ngLdapService.validateLdapGroupSettings(scopeInfo, ldapSettings);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception ex) {
      assertNull(ldapTestResponse);
      assertThat(ex).isInstanceOf(HintException.class);
      assertEquals(ex.getMessage(), DELEGATE_NOT_AVAILABLE);
    }
  }
  private ErrorNotifyResponseData buildErrorNotifyResponseData() {
    return ErrorNotifyResponseData.builder().errorMessage(INVALID_CREDENTIALS).build();
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSyncLdapGroups() throws IOException {
    int totalMembers = 1;
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(request).when(managerClient).getLdapSettingsUsingAccountId(ACCOUNT_ID);
    doReturn(Response.success(mockResponse)).when(request).execute();
    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    final String testUserName = "test 123";
    LdapUserResponse usrResponse = LdapUserResponse.builder().email(testUserEmail).name(testUserName).build();
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name("testLdapGroup")
                                     .description("desc")
                                     .dn(groupDn)
                                     .totalMembers(totalMembers)
                                     .users(Collections.singletonList(usrResponse))
                                     .build();
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .parentUniqueId("projUniqueId")
                        .isSsoLinked(true)
                        .ssoGroupId(groupDn)
                        .users(Collections.singletonList(testUserEmail))
                        .build();

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    usrGroupToLdapGroupMap.put(ug1, response);

    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(ug1.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(ug1.getParentUniqueId());

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, uniqueIds)).thenReturn(scopeInfoMap);

    doNothing().when(groupSyncHelper).reconcileAllUserGroups(any(), anyString(), anyString());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapGroupSyncTaskResponse.builder().ldapGroupsResponse(response).build());
    when(userGroupService.getUserGroupsBySsoId(anyString(), anyString())).thenReturn(Collections.singletonList(ug1));
    when(userGroupService.get(any(), anyString())).thenReturn(Optional.of(ug1));
    ngLdapService.syncUserGroupsJob(PROJECT_SCOPE_INFO);
    verify(managerClient, times(1)).getLdapSettingsUsingAccountId(ACCOUNT_ID);
    verify(groupSyncHelper, times(1)).reconcileAllUserGroups(usrGroupToLdapGroupMap, LDAP_SETTINGS_ID, ACCOUNT_ID);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testSyncLdapGroupsDisabledLDAP() throws IOException {
    // Arrange
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    ldapSettingsWithEncryptedDataDetail.getLdapSettings().setDisabled(true);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(request).when(managerClient).getLdapSettingsUsingAccountId(ACCOUNT_ID);
    doReturn(Response.success(mockResponse)).when(request).execute();

    // Act
    ngLdapService.syncUserGroupsJob(PROJECT_SCOPE_INFO);

    // Assert
    verify(managerClient, times(1)).getLdapSettingsUsingAccountId(ACCOUNT_ID);
    verify(groupSyncHelper, times(0)).reconcileAllUserGroups(any(), anyString(), anyString());
    verify(delegateGrpcClientWrapper, times(0)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testLDAPAuthentication() throws IOException {
    // Arrange
    final EncryptedRecordData encryptedRecord = EncryptedRecordData.builder()
                                                    .name("testLdapRecord")
                                                    .encryptedValue("encryptedTestPassword".toCharArray())
                                                    .kmsId(ACCOUNT_ID)
                                                    .build();
    EncryptedDataDetail encryptedPwdDetail =
        EncryptedDataDetail.builder().fieldName("password").encryptedData(encryptedRecord).build();

    Call<RestResponse<EncryptedDataDetail>> dataRequest = mock(Call.class);
    RestResponse<EncryptedDataDetail> mockDataResponse = new RestResponse<>(encryptedPwdDetail);
    String testPassword = "testPassword";
    final String userName = "testUserName@test.io";
    doReturn(Response.success(mockDataResponse)).when(dataRequest).execute();

    Call<RestResponse<LdapSettingsWithEncryptedDataAndPasswordDetail>> request = mock(Call.class);
    RestResponse<LdapSettingsWithEncryptedDataAndPasswordDetail> mockResponse =
        new RestResponse<>(LdapSettingsWithEncryptedDataAndPasswordDetail.builder()
                               .ldapSettings(ldapSettingsWithEncryptedDataDetail.getLdapSettings())
                               .encryptedDataDetail(ldapSettingsWithEncryptedDataDetail.getEncryptedDataDetail())
                               .encryptedPwdDataDetail(encryptedPwdDetail)
                               .build());
    doReturn(request).when(managerClient).getLdapSettingsAndEncryptedPassword(anyString(), any());
    doReturn(Response.success(mockResponse)).when(request).execute();
    String authSuccessMsg = "Authentication Successful";
    LdapResponse ldapResponse =
        LdapResponse.builder().status(LdapResponse.Status.SUCCESS).message(authSuccessMsg).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapTestAuthenticationTaskResponse.builder().ldapAuthenticationResponse(ldapResponse).build());

    // Act
    LdapResponse resultResponse = ngLdapService.testLDAPLogin(PROJECT_SCOPE_INFO, userName, testPassword);

    // Assert
    assertNotNull(resultResponse);
    assertThat(resultResponse.getStatus()).isEqualTo(LdapResponse.Status.SUCCESS);
    assertThat(resultResponse.getMessage()).isEqualTo(authSuccessMsg);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testLDAPAuthenticationInErrorCase() throws IOException {
    // Arrange
    final EncryptedRecordData encryptedRecord = EncryptedRecordData.builder()
                                                    .name("testLdapRecord")
                                                    .encryptedValue("encryptedTestPassword".toCharArray())
                                                    .kmsId(ACCOUNT_ID)
                                                    .build();
    EncryptedDataDetail encryptedPwdDetail =
        EncryptedDataDetail.builder().fieldName("password").encryptedData(encryptedRecord).build();

    Call<RestResponse<EncryptedDataDetail>> dataRequest = mock(Call.class);
    RestResponse<EncryptedDataDetail> mockDataResponse = new RestResponse<>(encryptedPwdDetail);
    String testPassword = "testPassword";
    final String userName = "testUserName@test.io";
    doReturn(Response.success(mockDataResponse)).when(dataRequest).execute();

    Call<RestResponse<LdapSettingsWithEncryptedDataAndPasswordDetail>> request = mock(Call.class);
    RestResponse<LdapSettingsWithEncryptedDataAndPasswordDetail> mockResponse =
        new RestResponse<>(LdapSettingsWithEncryptedDataAndPasswordDetail.builder()
                               .ldapSettings(ldapSettingsWithEncryptedDataDetail.getLdapSettings())
                               .encryptedDataDetail(ldapSettingsWithEncryptedDataDetail.getEncryptedDataDetail())
                               .encryptedPwdDataDetail(encryptedPwdDetail)
                               .build());
    doReturn(request).when(managerClient).getLdapSettingsAndEncryptedPassword(anyString(), any());
    doReturn(Response.success(mockResponse)).when(request).execute();
    LdapResponse ldapResponse =
        LdapResponse.builder().status(LdapResponse.Status.FAILURE).message(LdapConstants.INVALID_CREDENTIALS).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapTestAuthenticationTaskResponse.builder().ldapAuthenticationResponse(ldapResponse).build());

    // Act & Assert
    assertThatThrownBy(() -> ngLdapService.testLDAPLogin(PROJECT_SCOPE_INFO, userName, testPassword))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testResponseTimeoutDurationFromLdapSettingsTimeout() {
    software.wings.beans.sso.LdapSettingsDTO ldapSettings = getLdapSettings(ACCOUNT_ID);
    ldapSettings.getConnectionSettings().setResponseTimeout(5000); // 5 seconds

    Duration timeoutDuration = ngLdapService.getLdapDelegateTaskResponseTimeout(
        ldapSettings.getConnectionSettings().getResponseTimeout(), NG_LDAP_SEARCH_GROUPS.name(), ACCOUNT_ID);

    // Assert
    assertNotNull(timeoutDuration);
    assertThat(timeoutDuration.getSeconds() * 1000).isEqualTo((long) LDAP_TASK_DEFAULT_MINIMUM_TIMEOUT_MILLIS);

    ldapSettings.getConnectionSettings().setResponseTimeout(125000); // 125 seconds
    timeoutDuration = ngLdapService.getLdapDelegateTaskResponseTimeout(
        ldapSettings.getConnectionSettings().getResponseTimeout(), NG_LDAP_SEARCH_GROUPS.name(), ACCOUNT_ID);

    // Assert
    assertNotNull(timeoutDuration);
    assertThat(timeoutDuration.getSeconds() * 1000).isEqualTo((long) 125000);

    ldapSettings.getConnectionSettings().setResponseTimeout(500000); // 500 seconds
    timeoutDuration = ngLdapService.getLdapDelegateTaskResponseTimeout(
        ldapSettings.getConnectionSettings().getResponseTimeout(), NG_LDAP_SEARCH_GROUPS.name(), ACCOUNT_ID);

    // Assert
    assertNotNull(timeoutDuration);
    assertThat(timeoutDuration.getSeconds() * 1000).isEqualTo((long) LDAP_TASK_DEFAULT_MAXIMUM_TIMEOUT_MILLIS);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testIsUserGroupSsoStateValidTrue() {
    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";

    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .isSsoLinked(true)
                        .ssoGroupId(groupDn)
                        .users(Collections.singletonList(testUserEmail))
                        .build();

    when(userGroupService.get(any(), anyString())).thenReturn(Optional.of(ug1));
    boolean ssoStateValid = ngLdapService.isUserGroupSsoStateValid(PROJECT_SCOPE_INFO, ug1);
    assertThat(ssoStateValid).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testIsUserGroupSsoStateValidFalseSsoNotLinked() {
    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";

    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .isSsoLinked(false)
                        .ssoGroupId(groupDn)
                        .users(Collections.singletonList(testUserEmail))
                        .build();

    when(userGroupService.get(any(), anyString())).thenReturn(Optional.of(ug1));
    boolean ssoStateValid = ngLdapService.isUserGroupSsoStateValid(PROJECT_SCOPE_INFO, ug1);
    assertThat(ssoStateValid).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testIsUserGroupSsoStateValidFalseSsoGroupIdInvalid() {
    final String groupDn1 = "testGrpDn";
    final String groupDn2 = "testGrpDn1";
    final String testUserEmail = "test123@hn.io";

    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .isSsoLinked(true)
                        .ssoGroupId(groupDn1)
                        .users(Collections.singletonList(testUserEmail))
                        .build();

    UserGroup ug2 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .isSsoLinked(true)
                        .ssoGroupId(groupDn2)
                        .users(Collections.singletonList(testUserEmail))
                        .build();

    when(userGroupService.get(any(), anyString())).thenReturn(Optional.of(ug2));
    boolean ssoStateValid = ngLdapService.isUserGroupSsoStateValid(PROJECT_SCOPE_INFO, ug1);
    assertThat(ssoStateValid).isFalse();
  }

  private software.wings.beans.sso.LdapSettingsDTO getLdapSettings(String accountId) {
    LdapConnectionSettings settings = new LdapConnectionSettings();
    settings.setBindPassword("somePassword");
    software.wings.beans.sso.LdapSettingsDTO ldapSettings = software.wings.beans.sso.LdapSettingsDTO.builder()
                                                                .connectionSettings(settings)
                                                                .displayName("someDisplayName")
                                                                .accountId(accountId)
                                                                .build();
    ldapSettings.setUuid("someUuid");
    return ldapSettings;
  }

  private void mockCgClientCall() throws IOException {
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(request).when(managerClient).getLdapSettingsUsingAccountIdAndLdapSettings(any(), any());
    doReturn(Response.success(mockResponse)).when(request).execute();
  }
}
