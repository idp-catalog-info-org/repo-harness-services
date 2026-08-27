/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.resource;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_PERMISSION;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.TEJAS;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.exception.InvalidRequestException;
import io.harness.ldap.mapper.NgLdapSettingsMapper;
import io.harness.ldap.service.NGLdapService;
import io.harness.ldap.service.NGLdapSettingsService;
import io.harness.ng.accesscontrol.usergroup.UserGroupPermissionUtils;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.api.UserGroupService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.PmsFeatureFlagHelper;

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapSettingsDTO;
import software.wings.beans.sso.LdapUserSettings;

import io.dropwizard.jersey.validation.JerseyViolationException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import javax.validation.Validation;
import javax.validation.Validator;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class NGLdapResourceImplTest extends CategoryTest {
  @Mock private AuthSettingsManagerClient managerClient;
  @Mock private NGLdapService ngLdapService;

  @Mock private AccessControlClient accessControlClient;
  @Mock private UserGroupService userGroupService;
  @Mock NGLdapSettingsService ngLdapSettingsService;
  @Mock NgLdapSettingsMapper ngLdapSettingsMapper;
  @Mock PmsFeatureFlagHelper ngFeatureFlagHelperService;
  @Mock UserGroupPermissionUtils userGroupPermissionUtils;
  private Validator validator;
  NGLdapResourceImpl ngLdapResourceImpl;

  private LdapSettingsWithEncryptedDataDetail ldapSettingsWithEncryptedDataDetail;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";

  @Before
  public void setup() {
    initMocks(this);
    ldapSettingsWithEncryptedDataDetail = LdapSettingsWithEncryptedDataDetail.builder()
                                              .ldapSettings(software.wings.beans.dto.LdapSettings.builder().build())
                                              .encryptedDataDetail(EncryptedDataDetail.builder().build())
                                              .build();

    validator = Validation.buildDefaultValidatorFactory().getValidator();

    ngLdapResourceImpl = new NGLdapResourceImpl(ngLdapService, accessControlClient, userGroupService, validator,
        ngLdapSettingsService, ngLdapSettingsMapper, ngFeatureFlagHelperService, userGroupPermissionUtils);
    when(userGroupPermissionUtils.getUserGroupManagePermissionForSSO(anyString()))
        .thenReturn(MANAGE_USERGROUP_PERMISSION);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testKryoRegistrarForUnexpectedChanges() throws IOException {
    int totalMembers = 4;
    final String groupQueryStr = "testGroupName";
    Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> request = mock(Call.class);
    doReturn(request).when(managerClient).getLdapSettingsUsingAccountId(ACCOUNT_ID);
    RestResponse<LdapSettingsWithEncryptedDataDetail> mockResponse =
        new RestResponse<>(ldapSettingsWithEncryptedDataDetail);
    doReturn(Response.success(mockResponse)).when(request).execute();
    LdapGroupResponse groupResponse = LdapGroupResponse.builder()
                                          .name(groupQueryStr)
                                          .description("desc")
                                          .dn("uid=ldap_user1,ou=Users,dc=jumpcloud,dc=com")
                                          .totalMembers(totalMembers)
                                          .build();
    Collection<LdapGroupResponse> userGroups = Collections.singletonList(groupResponse);
    doReturn(userGroups).when(ngLdapService).searchLdapGroupsByName(any(), any(), any());

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    RestResponse<Collection<LdapGroupResponse>> collectionRestResponse =
        ngLdapResourceImpl.searchLdapGroups("TestLdapID", ACCOUNT_ID, ORG_ID, PROJECT_ID, groupQueryStr, scopeInfo);
    assertNotNull(collectionRestResponse);
    assertFalse(collectionRestResponse.getResource().isEmpty());
    assertThat(collectionRestResponse.getResource().size()).isEqualTo(1);
    assertThat(collectionRestResponse.getResource().iterator().next().getTotalMembers()).isEqualTo(totalMembers);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testValidateLdapSettings() {
    LdapConnectionSettings ldapConnectionSettings = new LdapConnectionSettings();

    // validate empty accountId
    LdapSettingsDTO ldapSettings_NoAccountId =
        LdapSettingsDTO.builder().connectionSettings(ldapConnectionSettings).build();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> ngLdapResourceImpl.validateLdapSettings(ldapSettings_NoAccountId))
        .withMessage("accountId cannot be empty for ldap settings");

    // validate invalid ldapConnectionSettings
    LdapSettingsDTO ldapSettings_InvalidConnectionSettings =
        LdapSettingsDTO.builder().accountId(randomAlphabetic(10)).connectionSettings(ldapConnectionSettings).build();
    assertThatExceptionOfType(JerseyViolationException.class)
        .isThrownBy(() -> ngLdapResourceImpl.validateLdapSettings(ldapSettings_InvalidConnectionSettings))
        .withMessage("host: must not be null");

    // validate invalid ldapUserSettings
    ldapConnectionSettings.setHost("host");
    LdapUserSettings ldapUserSettings = new LdapUserSettings();
    ldapUserSettings.setBaseDN(null);
    LdapSettingsDTO ldapSettings_InvalidUserSettings =
        LdapSettingsDTO.builder()
            .accountId(randomAlphabetic(10))
            .connectionSettings(ldapConnectionSettings)
            .userSettingsList(Collections.singletonList(ldapUserSettings))
            .build();
    assertThatExceptionOfType(JerseyViolationException.class)
        .isThrownBy(() -> ngLdapResourceImpl.validateLdapSettings(ldapSettings_InvalidUserSettings))
        .withMessage("baseDN: may not be empty");

    // validate invalid ldapGroupSettings
    ldapUserSettings.setBaseDN("baseDN");
    LdapGroupSettings ldapGroupSettings = new LdapGroupSettings();
    ldapGroupSettings.setBaseDN(null);
    LdapSettingsDTO ldapSettings_InvalidGroupSettings =
        LdapSettingsDTO.builder()
            .accountId(randomAlphabetic(10))
            .connectionSettings(ldapConnectionSettings)
            .userSettingsList(Collections.singletonList(ldapUserSettings))
            .groupSettingsList(Collections.singletonList(ldapGroupSettings))
            .build();
    assertThatExceptionOfType(JerseyViolationException.class)
        .isThrownBy(() -> ngLdapResourceImpl.validateLdapSettings(ldapSettings_InvalidGroupSettings))
        .withMessage("baseDN: may not be empty");
  }
}
