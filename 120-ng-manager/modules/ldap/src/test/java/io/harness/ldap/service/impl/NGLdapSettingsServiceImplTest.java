/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.service.impl;

import static io.harness.ldap.service.impl.NGLdapSettingsServiceImpl.LDAP_TASK_DEFAULT_MAXIMUM_TIMEOUT_MILLIS;
import static io.harness.ldap.service.impl.NGLdapSettingsServiceImpl.LDAP_TASK_DEFAULT_MINIMUM_TIMEOUT_MILLIS;
import static io.harness.rule.OwnerRule.ADITYA;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;
import static io.harness.rule.OwnerRule.SHASHANK;

import static software.wings.beans.TaskType.NG_LDAP_GROUPS_SYNC;
import static software.wings.beans.TaskType.NG_LDAP_TEST_CONN_SETTINGS;
import static software.wings.beans.sso.LdapTestResponse.Status.FAILURE;
import static software.wings.beans.sso.LdapTestResponse.Status.SUCCESS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.EncryptedData;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskParameters;
import io.harness.delegate.beans.ldap.NGLdapDelegateTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSearchTaskResponse;
import io.harness.delegate.beans.ldap.NGLdapGroupSyncTaskResponse;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NoResultFoundException;
import io.harness.exception.WingsException;
import io.harness.ldap.entity.NGLdapSettings;
import io.harness.ldap.scheduler.NGLdapGroupSyncHelper;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.LdapSettingsRepository;
import io.harness.repositories.SSOSettingsRepository;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionType;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapUserResponse;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.beans.sso.SSOType;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.PL)
@RunWith(MockitoJUnitRunner.class)
public class NGLdapSettingsServiceImplTest extends CategoryTest {
  @Spy @Inject @InjectMocks private NGLdapSettingsServiceImpl ngldapSettingsService;
  @Mock TransactionTemplate transactionTemplate;
  @Mock LdapSettingsRepository ldapSettingsRepository;
  @Mock SSOSettingsRepository ssoSettingsRepository;
  @Mock OutboxService outboxService;
  @Mock UserGroupService userGroupService;
  @Mock ScopeInfoService scopeInfoService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock NGLdapGroupSyncHelper ngLdapGroupSyncHelper;
  @Mock TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  private static final String CRON_EXPRESSION = "0 0/15 * 1/1 * ? *";
  private static final String ACCOUNT_ID = "accountId";
  public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final String IDENTIFIER = "IDENTIFIER";
  private static final String USER_GROUP_ID = "USER_GROUP_ID";
  private static final ScopeInfo ACCOUNT_SCOPE_INFO =
      ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).uniqueId(ACCOUNT_ID).build();
  private static final ScopeInfo PROJECT_SCOPE_INFO = ScopeInfo.builder()
                                                          .accountIdentifier(ACCOUNT_ID)
                                                          .orgIdentifier(ORG_ID)
                                                          .projectIdentifier(PROJECT_ID)
                                                          .uniqueId("projectUniqueId")
                                                          .build();

  @Before
  public void setup() {
    initMocks(this);
    ngldapSettingsService = new NGLdapSettingsServiceImpl(ldapSettingsRepository, ssoSettingsRepository, outboxService,
        transactionTemplate, userGroupService, scopeInfoService, ngLdapGroupSyncHelper, delegateGrpcClientWrapper,
        secretManagerClientService, taskSetupAbstractionHelper);
  }

  //--------------------------------------NG Ldap CRUD Test ----------------------//

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testLdapSettingsCreate() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    ngLdapSettings.setCronExpression("0 0 */1 ? * * *");
    when(ldapSettingsRepository.save(ngLdapSettings)).thenReturn(ngLdapSettings);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    NGLdapSettings after = ngldapSettingsService.create(ngLdapSettings);
    verify(outboxService, times(1)).save(any());
    assertThat(after).isEqualToComparingFieldByField(ngLdapSettings);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testLdapSettingsCreateInvalidRequestException() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    ngLdapSettings.setAccountIdentifier(ACCOUNT_ID);
    ngLdapSettings.setType(SSOType.LDAP);
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);
    Assertions.assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ngldapSettingsService.create(ngLdapSettings))
        .withMessageContaining("Ldap settings already exist for this account.");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetLdapSettings() throws Exception {
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(getLDAPSettings());
    NGLdapSettings NGLDAPSettings = ngldapSettingsService.get(ACCOUNT_ID);
    assertNotNull(NGLDAPSettings);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetLdapSettingsNotFound() {
    when(ldapSettingsRepository.findByIdentifierAndType("accountId2", SSOType.LDAP)).thenReturn(getLDAPSettings());
    assertThatThrownBy(() -> ngldapSettingsService.get(ACCOUNT_ID)).isInstanceOf(NoResultFoundException.class);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testUpdateLdapSettings() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);
    ngLdapSettings.setDisabled(true);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(ldapSettingsRepository.save(ngLdapSettings)).thenReturn(ngLdapSettings);
    NGLdapSettings after = ngldapSettingsService.update(ngLdapSettings, ACCOUNT_ID);
    verify(outboxService, times(1)).save(any());
    assertThat(after.isDisabled()).isEqualTo(true);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDeleteLdapSettings() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    boolean result = ngldapSettingsService.delete(ACCOUNT_ID);
    verify(outboxService, times(1)).save(any());
    assertTrue(result);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDeleteLdapSettingsWithLinkedSSOGroups() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    Map<String, Optional<ScopeInfo>> scopeInfoList = new HashMap<>();
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    UserGroup userGroup = UserGroup.builder()
                              .identifier("grp1")
                              .accountIdentifier(ACCOUNT_ID)
                              .isSsoLinked(true)
                              .users(Collections.singletonList("test@harness.io"))
                              .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(userGroup.getAccountIdentifier())
                              .uniqueId(userGroup.getAccountIdentifier())
                              .scopeType(ScopeLevel.ACCOUNT)
                              .build();
    scopeInfoList.put(userGroup.getAccountIdentifier(), Optional.of(scopeInfo));
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(scopeInfoList);
    when(userGroupService.getUserGroupsBySsoId(ACCOUNT_ID, ngLdapSettings.getIdentifier()))
        .thenReturn(Collections.singletonList(userGroup));
    Assertions.assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ngldapSettingsService.delete(ACCOUNT_ID))
        .withMessageContaining(
            "Deleting SSO provider with linked user groups is not allowed. Unlink the user groups in NG also first.");
  }

  //--------------------------------------NG Ldap Test Connection ----------------------//

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testLdapConnection() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    LdapTestResponse response = LdapTestResponse.builder().status(SUCCESS).message("Connection Successful").build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(response).build());
    BaseNGAccess baseNGAccess = BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).build();
    List<EncryptedDataDetail> encryptedDataDetailList =
        Collections.singletonList(EncryptedDataDetail.builder().build());
    LdapTestResponse ldapTestResponse =
        ngldapSettingsService.validateLdapConnectionSettings(ACCOUNT_ID, ngLdapSettings);
    assertNotNull(ldapTestResponse);
    assertEquals(response.getStatus(), ldapTestResponse.getStatus());
  }
  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testLdapConnectionNotSuccessfulDueToIncorrectData() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    LdapTestResponse response = LdapTestResponse.builder().status(FAILURE).message("Connection not successful").build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(response).build());
    BaseNGAccess baseNGAccess = BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).build();
    List<EncryptedDataDetail> encryptedDataDetailList =
        Collections.singletonList(EncryptedDataDetail.builder().build());
    Assertions.assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> ngldapSettingsService.validateLdapConnectionSettings(ACCOUNT_ID, ngLdapSettings))
        .withMessageContaining("Verify configuration provided in Base DN or Search Filter are correct");
  }

  @Test
  @Owner(developers = {ADITYA, JENNY})
  @Category(UnitTests.class)
  public void testWithInvalidHost() throws IOException {
    NGLdapSettings ldapSettings = getLDAPSettings();
    ldapSettings.getConnectionSettings().setHost("abc");
    LdapTestResponse unsuccessfulTestResponse =
        LdapTestResponse.builder().status(FAILURE).message(INVALID_CREDENTIALS).build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(unsuccessfulTestResponse).build());
    assertThatThrownBy(() -> ngldapSettingsService.validateLdapConnectionSettings(ACCOUNT_ID, ldapSettings))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetEncryptionDetailsWithSecretSpec() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    BaseNGAccess baseNGAccess = BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).build();
    EncryptedData encryptedData = EncryptedData.builder()
                                      .encryptionType(EncryptionType.LOCAL)
                                      .accountId(ACCOUNT_ID)
                                      .scopedToAccount(false)
                                      .build();
    String uuid = UUIDGenerator.generateUuid();
    encryptedData.setUuid(uuid);
    List<EncryptedDataDetail> encryptedDataDetailList =
        Arrays.asList(EncryptedDataDetail.builder()
                          .fieldName("FieldName")
                          .encryptedData(SecretManager.buildRecordData(encryptedData))
                          .build());
    when(secretManagerClientService.getEncryptionDetails(baseNGAccess, ngLdapSettings.getConnectionSettings()))
        .thenReturn(encryptedDataDetailList);
    EncryptedDataDetail encryptedDataDetail = ngldapSettingsService.getEncryptionDetails(ngLdapSettings);
    assertEquals(encryptedDataDetail.getEncryptedData().getUuid(), uuid);
  }

  //--------------------------------------NG Ldap Sync Group ----------------------//

  @Test
  @Owner(developers = {PRATEEK, JENNY})
  @Category(UnitTests.class)
  public void testSyncLdapGroups() throws IOException {
    int totalMembers = 1;
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

    NGLdapSettings ngLdapSettings = getLDAPSettings();
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);

    Map<UserGroup, LdapGroupResponse> usrGroupToLdapGroupMap = new HashMap<>();
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(ug1.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));
    usrGroupToLdapGroupMap.put(ug1, response);

    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(ug1.getParentUniqueId());
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, uniqueIds)).thenReturn(scopeInfoMap);
    doNothing().when(ngLdapGroupSyncHelper).reconcileAllUserGroups(any(), anyString(), anyString());

    OngoingStubbing<DelegateResponseData> delegateResponseDataOngoingStubbing =
        when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
            .thenReturn(NGLdapGroupSyncTaskResponse.builder().ldapGroupsResponse(response).build());
    when(userGroupService.getUserGroupsBySsoId(ACCOUNT_ID, ngLdapSettings.getIdentifier()))
        .thenReturn(Collections.singletonList(ug1));
    when(userGroupService.get(any(), anyString())).thenReturn(Optional.of(ug1));
    ngldapSettingsService.syncUserGroupsJob(ACCOUNT_ID);
    verify(ngLdapGroupSyncHelper, times(1))
        .reconcileAllUserGroups(usrGroupToLdapGroupMap, ngLdapSettings.getUuid(), ACCOUNT_ID);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTaskV2(any());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testLdapGroupSync() throws Exception {
    final String groupDn = "testGrpDn";
    final String testUserEmail = "test123@hn.io";
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);
    NGLdapGroupSyncTaskResponse response =
        NGLdapGroupSyncTaskResponse.builder()
            .ldapGroupsResponse(LdapGroupResponse.builder().message("success").build())
            .build();

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(response);
    BaseNGAccess baseNGAccess = BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).build();
    List<EncryptedDataDetail> encryptedDataDetailList =
        Collections.singletonList(EncryptedDataDetail.builder().build());
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
    usrGroupToLdapGroupMap.put(ug1, response.getLdapGroupsResponse());
    Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
    scopeInfoMap.put(ug1.getParentUniqueId(), Optional.of(PROJECT_SCOPE_INFO));
    Set<String> uniqueIds = new HashSet<>();
    uniqueIds.add(ug1.getParentUniqueId());
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, uniqueIds)).thenReturn(scopeInfoMap);
    when(userGroupService.getUserGroupsBySsoId(ACCOUNT_ID, ngLdapSettings.getIdentifier()))
        .thenReturn(Collections.singletonList(ug1));
    when(ngldapSettingsService.createDelegateTask(ngLdapSettings,
             NGLdapDelegateTaskParameters.builder().encryptedDataDetail(EncryptedDataDetail.builder().build()).build(),
             NG_LDAP_GROUPS_SYNC.name()))
        .thenReturn(response);
    when(userGroupService.get(any(), anyString())).thenReturn(Optional.of(ug1));
    ngldapSettingsService.syncUserGroupsJob(ACCOUNT_ID);
    verify(userGroupService, times(1)).getUserGroupsBySsoId(ACCOUNT_ID, ngLdapSettings.getIdentifier());
    verify(ngLdapGroupSyncHelper, times(1))
        .reconcileAllUserGroups(usrGroupToLdapGroupMap, ngLdapSettings.getUuid(), ACCOUNT_ID);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testLdapGroupSyncWhenDisabled() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    ngLdapSettings.setDisabled(true);
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);
    BaseNGAccess baseNGAccess = BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).build();
    List<EncryptedDataDetail> encryptedDataDetailList =
        Collections.singletonList(EncryptedDataDetail.builder().build());
    ngldapSettingsService.syncUserGroupsJob(ACCOUNT_ID);
    verify(userGroupService, times(0)).getUserGroupsBySsoId(ACCOUNT_ID, ngLdapSettings.getUuid());
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
    boolean ssoStateValid = ngldapSettingsService.isUserGroupSsoStateValid(PROJECT_SCOPE_INFO, ug1);
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
    boolean ssoStateValid = ngldapSettingsService.isUserGroupSsoStateValid(PROJECT_SCOPE_INFO, ug1);
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
    boolean ssoStateValid = ngldapSettingsService.isUserGroupSsoStateValid(PROJECT_SCOPE_INFO, ug1);
    assertThat(ssoStateValid).isFalse();
  }

  @Test
  @Owner(developers = {PRATEEK, JENNY})
  @Category(UnitTests.class)
  public void testSearchLdapGroupsByName() throws IOException {
    int totalMembers = 4;
    final String groupNameQuery = "grpName";
    LdapGroupResponse response = LdapGroupResponse.builder()
                                     .name(groupNameQuery)
                                     .description("desc")
                                     .dn("uid=ldap_user1,ou=Users,dc=jumpcloud,dc=com")
                                     .totalMembers(totalMembers)
                                     .build();
    Collection<LdapGroupResponse> matchedGroups = Collections.singletonList(response);

    NGLdapSettings ngLdapSettings = getLDAPSettings();
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapGroupSearchTaskResponse.builder().ldapListGroupsResponses(matchedGroups).build());
    Collection<LdapGroupResponse> resultUserGroups =
        ngldapSettingsService.searchLdapGroupsByName(ACCOUNT_ID, "TestLdapID");
    assertNotNull(resultUserGroups);
    assertThat(resultUserGroups.size()).isEqualTo(1);
    assertThat(resultUserGroups.iterator().next().getTotalMembers()).isEqualTo(totalMembers);
  }

  @Test
  @Owner(developers = {SHASHANK, JENNY})
  @Category(UnitTests.class)
  public void testLdapGroupQuerySuccessfulAndUnsuccessful() throws IOException {
    NGLdapSettings ldapSettings = getLDAPSettings();
    LdapTestResponse successfulTestResponse =
        LdapTestResponse.builder()
            .status(SUCCESS)
            .message("Configuration looks good. Server returned non-zero number of records")
            .build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(successfulTestResponse).build());

    LdapTestResponse ldapTestResponse = ngldapSettingsService.validateLdapGroupSettings(ACCOUNT_ID, ldapSettings);

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
      ldapTestResponse = ngldapSettingsService.validateLdapGroupSettings(ACCOUNT_ID, ldapSettings);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (Exception ex) {
      assertThat(ex).isInstanceOf(HintException.class);
      assertEquals(ex.getMessage(), HintException.LDAP_ATTRIBUTES_INCORRECT);
    }
  }

  //--------------------------------------Delegate Task  ----------------------//

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testLdapDelegateTaskTimeOutCalculation() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    DelegateTaskRequest delegateTaskRequest = ngldapSettingsService.getDelegateTask(
        ngLdapSettings, NGLdapDelegateTaskParameters.builder().build(), NG_LDAP_TEST_CONN_SETTINGS.name());
    assertEquals(delegateTaskRequest.getExecutionTimeout().toMillis(), LDAP_TASK_DEFAULT_MINIMUM_TIMEOUT_MILLIS);
    // time out value above minimum and below max time out
    ngLdapSettings.getConnectionSettings().setResponseTimeout(90000);
    DelegateTaskRequest delegateTaskRequest2 = ngldapSettingsService.getDelegateTask(
        ngLdapSettings, NGLdapDelegateTaskParameters.builder().build(), NG_LDAP_TEST_CONN_SETTINGS.name());
    assertEquals(delegateTaskRequest2.getExecutionTimeout().toMillis(), 90000);
    // time out value above max timeout value
    ngLdapSettings.getConnectionSettings().setResponseTimeout(200000);
    DelegateTaskRequest delegateTaskRequest3 = ngldapSettingsService.getDelegateTask(
        ngLdapSettings, NGLdapDelegateTaskParameters.builder().build(), NG_LDAP_TEST_CONN_SETTINGS.name());
    assertEquals(delegateTaskRequest3.getExecutionTimeout().toMillis(), LDAP_TASK_DEFAULT_MAXIMUM_TIMEOUT_MILLIS);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateTaskWithoutSecretRef() throws Exception {
    //@TODO: test
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDelegateTaskWithSelectors() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    DelegateTaskRequest delegateTaskRequest = ngldapSettingsService.getDelegateTask(
        ngLdapSettings, NGLdapDelegateTaskParameters.builder().build(), NG_LDAP_TEST_CONN_SETTINGS.name());
    assertEquals(delegateTaskRequest.getSelectors(), new ArrayList<>());
    // with selectors
    ngLdapSettings.getConnectionSettings().setDelegateSelectors(Set.of("sel1"));
    DelegateTaskRequest delegateTaskRequest1 = ngldapSettingsService.getDelegateTask(
        ngLdapSettings, NGLdapDelegateTaskParameters.builder().build(), NG_LDAP_TEST_CONN_SETTINGS.name());
    assertEquals(delegateTaskRequest1.getSelectors(),
        List.of(TaskSelector.newBuilder().setSelector("sel1").setOrigin("default").build()));
  }

  //--------------------------------------Validate APi's----------------------//

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testValidateUserSettings() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);
    LdapTestResponse response = LdapTestResponse.builder().status(SUCCESS).message("Validate successful").build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(response).build());
    BaseNGAccess baseNGAccess = BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).build();
    List<EncryptedDataDetail> encryptedDataDetailList =
        Collections.singletonList(EncryptedDataDetail.builder().build());
    LdapTestResponse ldapTestResponse = ngldapSettingsService.validateLdapUserSettings(ACCOUNT_ID, ngLdapSettings);
    assertNotNull(ldapTestResponse);
    assertEquals(response.getStatus(), ldapTestResponse.getStatus());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testValidateUserSettingsFailed() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);
    LdapTestResponse response = LdapTestResponse.builder().status(FAILURE).message("Validate not successful").build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(response).build());
    BaseNGAccess baseNGAccess = BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).build();
    List<EncryptedDataDetail> encryptedDataDetailList =
        Collections.singletonList(EncryptedDataDetail.builder().build());
    Assertions.assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> ngldapSettingsService.validateLdapUserSettings(ACCOUNT_ID, ngLdapSettings))
        .withMessageContaining("Verify configuration provided in Base DN or Search Filter are correct");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testValidateGroupSettings() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);
    LdapTestResponse response = LdapTestResponse.builder().status(SUCCESS).message("Validate successful").build();
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(NGLdapDelegateTaskResponse.builder().ldapTestResponse(response).build());
    BaseNGAccess baseNGAccess = BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).build();
    List<EncryptedDataDetail> encryptedDataDetailList =
        Collections.singletonList(EncryptedDataDetail.builder().build());
    LdapTestResponse ldapTestResponse = ngldapSettingsService.validateLdapGroupSettings(ACCOUNT_ID, ngLdapSettings);
    assertNotNull(ldapTestResponse);
    assertEquals(response.getStatus(), ldapTestResponse.getStatus());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testLinkSSOGroup() throws Exception {
    NGLdapSettings ngLdapSettings = getLDAPSettings();
    when(ldapSettingsRepository.findByAccountIdentifierAndType(ACCOUNT_ID, SSOType.LDAP)).thenReturn(ngLdapSettings);
    UserGroup userGroup = UserGroup.builder()
                              .identifier("UG1")
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .isSsoLinked(true)
                              .ssoGroupId(ngLdapSettings.getUuid())
                              .build();
    when(userGroupService.linkToSsoGroupNG(ACCOUNT_SCOPE_INFO, USER_GROUP_ID, SSOType.LDAP, ngLdapSettings.getUuid(),
             "grpid", "grpname", ngLdapSettings.getDisplayName()))
        .thenReturn(userGroup);
    assertEquals(ngldapSettingsService.linkToSsoGroup(
                     ACCOUNT_SCOPE_INFO, USER_GROUP_ID, SSOType.LDAP, ngLdapSettings.getUuid(), "grpid", "grpname"),
        userGroup);
  }

  private NGLdapSettings getLDAPSettings() {
    NGLdapSettings ngLdapSettings = new NGLdapSettings();
    ngLdapSettings.setAccountIdentifier(ACCOUNT_ID);
    ngLdapSettings.setIdentifier(IDENTIFIER);
    ngLdapSettings.setName(IDENTIFIER);
    ngLdapSettings.setUrl("url");
    ngLdapSettings.setDisabled(false);
    ngLdapSettings.setCronExpression("0 0/15 * 1/1 * ? *");
    ngLdapSettings.setConnectionSettings(getLdapConnectionSettingsEntity());
    ngLdapSettings.setGroupSettingsList(List.of(getLdapGroupSettingsEntity()));
    ngLdapSettings.setUserSettingsList(List.of(getLdapUserSettingsEntity()));
    return ngLdapSettings;
  }

  private LdapConnectionSettings getLdapConnectionSettingsEntity() {
    LdapConnectionSettings connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setSslEnabled(true);
    connectionSettings.setResponseTimeout(100);
    connectionSettings.setConnectTimeout(100);
    connectionSettings.setPasswordRef(SecretRefData.builder().identifier("sec1").build());
    connectionSettings.setMaxReferralHops(1);
    connectionSettings.setPort(400);
    connectionSettings.setHost("host");
    connectionSettings.setReferralsEnabled(false);
    return connectionSettings;
  }

  private LdapUserSettings getLdapUserSettingsEntity() {
    LdapUserSettings userSettings = new LdapUserSettings();
    userSettings.setBaseDN("testBaseDN");
    userSettings.setEmailAttr("emailatt1");
    userSettings.setGroupMembershipAttr("grp-mem");
    userSettings.setDisplayNameAttr("name-attr");
    userSettings.setSearchFilter("filter");
    userSettings.setSamAccountNameAttr("account");
    return userSettings;
  }

  private LdapGroupSettings getLdapGroupSettingsEntity() {
    LdapGroupSettings groupSettings = new LdapGroupSettings();
    groupSettings.setBaseDN("testBaseDN");
    groupSettings.setNameAttr("nameattr");
    groupSettings.setSearchFilter("filter1");
    groupSettings.setDescriptionAttr("descr");
    groupSettings.setReferencedUserAttr("ref-att");
    groupSettings.setUserMembershipAttr("mem");
    return groupSettings;
  }
}
