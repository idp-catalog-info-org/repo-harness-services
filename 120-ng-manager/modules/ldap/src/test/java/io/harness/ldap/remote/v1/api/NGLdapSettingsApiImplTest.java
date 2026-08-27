/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.remote.v1.api;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_PERMISSION;
import static io.harness.rule.OwnerRule.ABHISHEK_SINGH;
import static io.harness.rule.OwnerRule.JENNY;

import static software.wings.helpers.ext.ldap.LdapConstants.DEFAULT_CONNECT_TIMEOUT;
import static software.wings.helpers.ext.ldap.LdapConstants.DEFAULT_RESPONSE_TIMEOUT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NoResultFoundException;
import io.harness.exception.WingsException;
import io.harness.ldap.entity.NGLdapSettings;
import io.harness.ldap.mapper.NgLdapSettingsMapper;
import io.harness.ldap.service.NGLdapSettingsService;
import io.harness.ng.accesscontrol.usergroup.UserGroupPermissionUtils;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.LdapConnectionSettingsDTO;
import io.harness.spec.server.ng.v1.model.LdapGroupSettingsDTO;
import io.harness.spec.server.ng.v1.model.LdapSettingsDTO;
import io.harness.spec.server.ng.v1.model.LdapSettingsRequest;
import io.harness.spec.server.ng.v1.model.LdapSettingsResponse;
import io.harness.spec.server.ng.v1.model.LdapUserSettingsDTO;
import io.harness.spec.server.ng.v1.model.LinkSSOGroupRequestDTO;
import io.harness.spec.server.ng.v1.model.UnlinkSSOGroupRequestDTO;

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.beans.sso.SSOType;

import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class NGLdapSettingsApiImplTest extends CategoryTest {
  @Mock private AccessControlClient accessControlClient;
  @Mock private NGLdapSettingsService ngLdapSettingsService;
  @Mock private UserGroupService userGroupService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private UserGroupPermissionUtils userGroupPermissionUtils;

  private NGLdapSettingsApiImpl ldapSettingsApi;
  private NgLdapSettingsMapper ngLdapSettingsMapper;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String GROUP_ID = "GROUP_ID";
  private static final String IDENTIFIER = "LDAP1";
  private static final String SSO_ID = "SSO_ID";
  private static final String SSO_GROUP_ID = "SSO_ID";
  private static final String SSO_GROUP_NAME = "SSO_GROUP_NAME";
  private static final ScopeInfo ACC_SCOPE_INFO =
      ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).uniqueId(ACCOUNT_ID).build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ngLdapSettingsMapper = new NgLdapSettingsMapper();
    ldapSettingsApi = new NGLdapSettingsApiImpl(accessControlClient, ngLdapSettingsService, ngLdapSettingsMapper,
        userGroupService, scopeInfoService, userGroupPermissionUtils);
    when(userGroupPermissionUtils.getUserGroupManagePermissionForSSO(anyString()))
        .thenReturn(MANAGE_USERGROUP_PERMISSION);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testCreateNgLdapSettings() throws Exception {
    LdapSettingsRequest ldapSettingsRequest = new LdapSettingsRequest();
    ldapSettingsRequest.ldapSettings(getNgLdapSettingsDto());
    NGLdapSettings ngLdapSettings = ngLdapSettingsMapper.ngLdapSettings(getNgLdapSettingsDto());
    when(ngLdapSettingsService.create(ngLdapSettings)).thenReturn(ngLdapSettings);
    Response response = ldapSettingsApi.createNgLdapSettings(ldapSettingsRequest, ACCOUNT_ID);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isEqualTo(getLdapSettingsResponse());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testUpdateNgLdapSettings() throws Exception {
    LdapSettingsRequest ldapSettingsRequest = new LdapSettingsRequest();
    ldapSettingsRequest.ldapSettings(getNgLdapSettingsDto());
    NGLdapSettings ngLdapSettings = ngLdapSettingsMapper.ngLdapSettings(getNgLdapSettingsDto());
    when(ngLdapSettingsService.update(ngLdapSettings, ACCOUNT_ID)).thenReturn(ngLdapSettings);
    Response response = ldapSettingsApi.updateLdapSettings(IDENTIFIER, ldapSettingsRequest, ACCOUNT_ID);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(getLdapSettingsResponse());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testGetNgLdapSettings() {
    NGLdapSettings ngLdapSettings = getLdapSettingsEntity();
    when(ngLdapSettingsService.get(ACCOUNT_ID)).thenReturn(ngLdapSettings);
    Response response = ldapSettingsApi.getLdapSettings(IDENTIFIER, ACCOUNT_ID);
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo(getLdapSettingsResponse());
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testValidateTestConnectionForLdapSettings() {
    NGLdapSettings ngLdapSettings = getLdapSettingsEntity();
    when(ngLdapSettingsService.validateLdapConnectionSettings(ACCOUNT_ID, ngLdapSettings))
        .thenReturn(LdapTestResponse.builder().build());
    LdapSettingsRequest ldapSettingsRequest = new LdapSettingsRequest();
    ldapSettingsRequest.ldapSettings(ngLdapSettingsMapper.toLdapSettingsDTO(ngLdapSettings));
    Response result = ldapSettingsApi.validateConnectionSettings(ldapSettingsRequest, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testDeleteLdapSettings() {
    when(ngLdapSettingsService.delete(ACCOUNT_ID)).thenReturn(true);
    Response result = ldapSettingsApi.deleteLdapSettings(IDENTIFIER, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenDeleteLdapSettings_AndSettingsNotFound_ThenReturn404() {
    String errorMessage = String.format("LDAP Settings not found for account [%s].", ACCOUNT_ID);
    when(ngLdapSettingsService.delete(ACCOUNT_ID))
        .thenThrow(NoResultFoundException.newBuilder()
                       .code(ErrorCode.RESOURCE_NOT_FOUND)
                       .message(errorMessage)
                       .level(Level.ERROR)
                       .reportTargets(WingsException.USER)
                       .build());
    Response result = ldapSettingsApi.deleteLdapSettings(IDENTIFIER, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(404);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenDeleteLdapSettings_AndLinkedUserGroupsExist_ThenReturn400WithErrorMessage() {
    String errorMessage =
        "Deleting SSO provider with linked user groups is not allowed. Unlink the user groups in NG also first.";
    when(ngLdapSettingsService.delete(ACCOUNT_ID)).thenThrow(new InvalidRequestException(errorMessage));
    Response result = ldapSettingsApi.deleteLdapSettings(IDENTIFIER, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(400);
    assertThat(result.getEntity()).isInstanceOf(ResponseMessage.class);
    ResponseMessage responseMessage = (ResponseMessage) result.getEntity();
    assertThat(responseMessage.getMessage()).isEqualTo(errorMessage);
  }

  @Test
  @Owner(developers = ABHISHEK_SINGH)
  @Category(UnitTests.class)
  public void whenDeleteLdapSettings_AndIllegalArgumentException_ThenReturn400WithErrorMessage() {
    String errorMessage = "Invalid argument provided";
    when(ngLdapSettingsService.delete(ACCOUNT_ID)).thenThrow(new IllegalArgumentException(errorMessage));
    Response result = ldapSettingsApi.deleteLdapSettings(IDENTIFIER, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(400);
    assertThat(result.getEntity()).isInstanceOf(ResponseMessage.class);
    ResponseMessage responseMessage = (ResponseMessage) result.getEntity();
    assertThat(responseMessage.getMessage()).isEqualTo(errorMessage);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testValidateGroupSettingsForLdapSettings() {
    NGLdapSettings ngLdapSettings = getLdapSettingsEntity();
    when(ngLdapSettingsService.validateLdapConnectionSettings(ACCOUNT_ID, ngLdapSettings))
        .thenReturn(LdapTestResponse.builder().build());
    LdapSettingsRequest ldapSettingsRequest = new LdapSettingsRequest();
    ldapSettingsRequest.ldapSettings(ngLdapSettingsMapper.toLdapSettingsDTO(ngLdapSettings));
    Response result = ldapSettingsApi.validateGroupSettings(ldapSettingsRequest, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testValidateUserSettingsForLdapSettings() {
    NGLdapSettings ngLdapSettings = getLdapSettingsEntity();
    when(ngLdapSettingsService.validateLdapConnectionSettings(ACCOUNT_ID, ngLdapSettings))
        .thenReturn(LdapTestResponse.builder().build());
    LdapSettingsRequest ldapSettingsRequest = new LdapSettingsRequest();
    ldapSettingsRequest.ldapSettings(ngLdapSettingsMapper.toLdapSettingsDTO(ngLdapSettings));
    Response result = ldapSettingsApi.validateUserSettings(ldapSettingsRequest, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testLinkSSOSettings() {
    LinkSSOGroupRequestDTO linkSSOGroupRequestDTO = new LinkSSOGroupRequestDTO();
    linkSSOGroupRequestDTO.setSsoId(SSO_ID);
    linkSSOGroupRequestDTO.setSsoGroupId(SSO_GROUP_ID);
    linkSSOGroupRequestDTO.setSsoGroupName(SSO_GROUP_NAME);
    UserGroup userGroup = UserGroup.builder()
                              .identifier("UG1")
                              .accountIdentifier(ACCOUNT_ID)
                              .isSsoLinked(true)
                              .ssoGroupId(SSO_GROUP_ID)
                              .users(Collections.singletonList("test@tets.com"))
                              .build();
    NGLdapSettings ngLdapSettings = getLdapSettingsEntity();
    when(ngLdapSettingsService.get(ACCOUNT_ID)).thenReturn(ngLdapSettings);
    when(ngLdapSettingsService.linkToSsoGroup(ACC_SCOPE_INFO, GROUP_ID, SSOType.LDAP, linkSSOGroupRequestDTO.getSsoId(),
             linkSSOGroupRequestDTO.getSsoGroupId(), linkSSOGroupRequestDTO.getSsoGroupName()))
        .thenReturn(userGroup);
    Response result = ldapSettingsApi.linkLdapSettings(GROUP_ID, linkSSOGroupRequestDTO, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
    assertThat(result.getEntity()).isEqualTo(userGroup);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testLinkSSOSettingsWithExternallyManaged() {
    LinkSSOGroupRequestDTO linkSSOGroupRequestDTO = new LinkSSOGroupRequestDTO();
    linkSSOGroupRequestDTO.setSsoId(SSO_ID);
    linkSSOGroupRequestDTO.setSsoGroupId(SSO_GROUP_ID);
    linkSSOGroupRequestDTO.setSsoGroupName(SSO_GROUP_NAME);
    when(userGroupService.isExternallyManaged(ACC_SCOPE_INFO, GROUP_ID)).thenReturn(true);
    Response result = ldapSettingsApi.linkLdapSettings(GROUP_ID, linkSSOGroupRequestDTO, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(400);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testUnLinkSSOSettings() {
    UnlinkSSOGroupRequestDTO unlinkSSOGroupRequestDTO = new UnlinkSSOGroupRequestDTO();
    unlinkSSOGroupRequestDTO.setRetainMembers(true);
    UserGroup userGroup = UserGroup.builder()
                              .identifier("UG1")
                              .accountIdentifier(ACCOUNT_ID)
                              .isSsoLinked(true)
                              .ssoGroupId(SSO_GROUP_ID)
                              .users(Collections.singletonList("test@tets.com"))
                              .build();
    NGLdapSettings ngLdapSettings = getLdapSettingsEntity();
    when(ngLdapSettingsService.get(ACCOUNT_ID)).thenReturn(ngLdapSettings);
    when(userGroupService.unlinkSsoGroup(ACC_SCOPE_INFO, GROUP_ID, unlinkSSOGroupRequestDTO.isRetainMembers()))
        .thenReturn(userGroup);
    Response result = ldapSettingsApi.unlinkLdapSettings(GROUP_ID, unlinkSSOGroupRequestDTO, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(200);
    assertThat(result.getEntity()).isEqualTo(userGroup);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testUnLinkSSOSettingsWithExternallyManaged() {
    UnlinkSSOGroupRequestDTO unlinkSSOGroupRequestDTO = new UnlinkSSOGroupRequestDTO();
    unlinkSSOGroupRequestDTO.setRetainMembers(true);
    when(userGroupService.isExternallyManaged(ACC_SCOPE_INFO, GROUP_ID)).thenReturn(true);
    Response result = ldapSettingsApi.unlinkLdapSettings(GROUP_ID, unlinkSSOGroupRequestDTO, ACCOUNT_ID);
    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(400);
  }

  private LdapSettingsResponse getLdapSettingsResponse() {
    LdapSettingsResponse ldapSettingsResponse = new LdapSettingsResponse();
    ldapSettingsResponse.setLdapSettings(getNgLdapSettingsDto());
    return ldapSettingsResponse;
  }

  private LdapSettingsDTO getNgLdapSettingsDto() {
    LdapSettingsDTO ldapSettingsDTO = new LdapSettingsDTO();
    ldapSettingsDTO.setAccountIdentifier(ACCOUNT_ID);
    ldapSettingsDTO.setIdentifier(IDENTIFIER);
    ldapSettingsDTO.setName(IDENTIFIER);
    ldapSettingsDTO.setUrl("url");
    ldapSettingsDTO.setDisabled(false);
    ldapSettingsDTO.setCronExpression("");
    ldapSettingsDTO.setLdapConnectionSettings(getLdapConnectionSettingsDTO());
    ldapSettingsDTO.setLdapGroupSettings(List.of(getLdapGroupSettingsDTO()));
    ldapSettingsDTO.setLdapUserSettings(List.of(getLdapUserSettingsDTO()));
    ldapSettingsDTO.setSsoType(SSOType.LDAP.name());
    return ldapSettingsDTO;
  }

  private LdapConnectionSettingsDTO getLdapConnectionSettingsDTO() {
    LdapConnectionSettingsDTO connectionSettings = new LdapConnectionSettingsDTO();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setSslEnabled(true);
    connectionSettings.setResponseTimeout(DEFAULT_RESPONSE_TIMEOUT);
    connectionSettings.setConnectionTimeout(DEFAULT_CONNECT_TIMEOUT);
    connectionSettings.setSecretRefPath("account.sec1");
    connectionSettings.setMaxReferralHops(1);
    connectionSettings.setPort(400);
    connectionSettings.setHost("host");
    connectionSettings.setReferralsEnabled(false);
    return connectionSettings;
  }

  private LdapUserSettingsDTO getLdapUserSettingsDTO() {
    LdapUserSettingsDTO userSettings = new LdapUserSettingsDTO();
    userSettings.setBaseDN("testBaseDN");
    userSettings.setEmailAttr("emailatt1");
    userSettings.setGroupMembershipAttr("grp-mem");
    userSettings.setDisplayNameAttr("name-attr");
    userSettings.setSearchFilter("filter");
    userSettings.setSamAccountNameAttr("account");
    userSettings.setUidAttr("uid");
    return userSettings;
  }

  private LdapGroupSettingsDTO getLdapGroupSettingsDTO() {
    LdapGroupSettingsDTO groupSettings = new LdapGroupSettingsDTO();
    groupSettings.setBaseDN("testBaseDN");
    groupSettings.setNameAttr("nameattr");
    groupSettings.setSearchFilter("filter1");
    groupSettings.setDescriptionAttr("descr");
    groupSettings.setReferencedUserAttr("ref-att");
    groupSettings.setUserMembershipAttr("mem");
    return groupSettings;
  }

  private NGLdapSettings getLdapSettingsEntity() {
    NGLdapSettings ngLdapSettings = new NGLdapSettings();
    ngLdapSettings.setAccountIdentifier(ACCOUNT_ID);
    ngLdapSettings.setIdentifier(IDENTIFIER);
    ngLdapSettings.setName(IDENTIFIER);
    ngLdapSettings.setUrl("url");
    ngLdapSettings.setDisabled(false);
    ngLdapSettings.setCronExpression("");
    ngLdapSettings.setConnectionSettings(getLdapConnectionSettingsEntity());
    ngLdapSettings.setGroupSettingsList(List.of(getLdapGroupSettingsEntity()));
    ngLdapSettings.setUserSettingsList(List.of(getLdapUserSettingsEntity()));
    return ngLdapSettings;
  }

  private LdapConnectionSettings getLdapConnectionSettingsEntity() {
    LdapConnectionSettings connectionSettings = new LdapConnectionSettings();
    connectionSettings.setBindDN("testBindDN");
    connectionSettings.setSslEnabled(true);
    connectionSettings.setResponseTimeout(DEFAULT_RESPONSE_TIMEOUT);
    connectionSettings.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
    connectionSettings.setPasswordRef(SecretRefData.builder().identifier("sec1").scope(Scope.ACCOUNT).build());
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
