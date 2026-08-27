/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.rule.OwnerRule.NIYASHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.base.NgManagerTestBase;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.ldap.entity.NGLdapSettings;
import io.harness.ldap.mapper.NgLdapSettingsMapper;
import io.harness.ldap.service.NGLdapSettingsService;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.sso.LdapConnectionSettings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@Category(UnitTests.class)
public class NGLdapMigrationSchedulerTest extends NgManagerTestBase {
  private static final String ACCOUNT_ID = "testAccountId";
  private static final String SECRET_ID = "secretId123";
  private static final String SECRET_VALUE = "myLdapPassword";
  private static final String LDAP_SECRET_IDENTIFIER = "ldap-secret";

  @Mock private AuthSettingsManagerClient managerClient;
  @Mock private NGLdapSettingsService ngLdapSettingsService;
  @Mock private NgLdapSettingsMapper ldapSettingsMapper;
  @Mock private SecretCrudService ngSecretService;
  @Mock private UserGroupService userGroupService;
  @Mock private SecretManagerClient secretManagerClient;
  @Mock private NGFeatureFlagHelperService featureFlagService;
  @Mock private Call<RestResponse<software.wings.beans.sso.LdapSettingsDTO>> ldapSettingsCall;
  @Mock private Call<RestResponse<String>> secretValueCall;

  @InjectMocks private NGLdapMigrationScheduler scheduler;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_SkipWhenNGLdapSettingsAlreadyExist() {
    // Arrange
    NGLdapSettings existingSettings = new NGLdapSettings();
    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(ACCOUNT_ID));
    when(ngLdapSettingsService.get(anyString())).thenReturn(existingSettings);

    // Act
    scheduler.run();

    // Assert
    verify(ngLdapSettingsService, times(1)).get(anyString());
    verify(managerClient, never()).getLdapSettingsV2(anyString());
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_SkipWhenCGLdapSettingsNull() throws Exception {
    // Arrange
    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(ACCOUNT_ID));
    when(ngLdapSettingsService.get(anyString())).thenReturn(null);
    when(managerClient.getLdapSettingsV2(anyString())).thenReturn(ldapSettingsCall);
    when(ldapSettingsCall.execute()).thenReturn(Response.success(new RestResponse<>(null)));

    // Act
    scheduler.run();

    // Assert
    verify(managerClient, times(1)).getLdapSettingsV2(anyString());
    verify(ngSecretService, never()).create(any(), any());
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_SkipWhenConnectionSettingsNull() throws Exception {
    // Arrange
    software.wings.beans.sso.LdapSettingsDTO ldapSettings = new software.wings.beans.sso.LdapSettingsDTO();
    ldapSettings.setConnectionSettings(null);

    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(ACCOUNT_ID));
    when(ngLdapSettingsService.get(anyString())).thenReturn(null);
    when(managerClient.getLdapSettingsV2(anyString())).thenReturn(ldapSettingsCall);
    when(ldapSettingsCall.execute()).thenReturn(Response.success(new RestResponse<>(ldapSettings)));

    // Act
    scheduler.run();

    // Assert
    verify(managerClient, times(1)).getLdapSettingsV2(anyString());
    verify(ngSecretService, never()).create(any(), any());
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_SkipWhenEncryptedBindPasswordEmpty() throws Exception {
    // Arrange
    software.wings.beans.sso.LdapConnectionSettings connectionSettings =
        new software.wings.beans.sso.LdapConnectionSettings();
    connectionSettings.setEncryptedBindPassword("");

    software.wings.beans.sso.LdapSettingsDTO ldapSettings = new software.wings.beans.sso.LdapSettingsDTO();
    ldapSettings.setConnectionSettings(connectionSettings);

    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(ACCOUNT_ID));
    when(ngLdapSettingsService.get(anyString())).thenReturn(null);
    when(managerClient.getLdapSettingsV2(anyString())).thenReturn(ldapSettingsCall);
    when(ldapSettingsCall.execute()).thenReturn(Response.success(new RestResponse<>(ldapSettings)));

    // Act
    scheduler.run();

    // Assert
    verify(managerClient, times(1)).getLdapSettingsV2(anyString());
    verify(secretManagerClient, never()).getHarnessSecretValue(anyString(), anyString());
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_ReuseExistingSecret() throws Exception {
    // Arrange
    software.wings.beans.sso.LdapConnectionSettings connectionSettings =
        new software.wings.beans.sso.LdapConnectionSettings();
    connectionSettings.setEncryptedBindPassword(SECRET_ID);

    software.wings.beans.sso.LdapSettingsDTO ldapSettings = new software.wings.beans.sso.LdapSettingsDTO();
    ldapSettings.setConnectionSettings(connectionSettings);

    SecretResponseWrapper existingSecret = SecretResponseWrapper.builder().build();
    NGLdapSettings ngLdapSettings = new NGLdapSettings();
    ngLdapSettings.setConnectionSettings(new LdapConnectionSettings());

    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(ACCOUNT_ID));
    when(ngLdapSettingsService.get(anyString())).thenReturn(null);
    when(managerClient.getLdapSettingsV2(anyString())).thenReturn(ldapSettingsCall);
    when(ldapSettingsCall.execute()).thenReturn(Response.success(new RestResponse<>(ldapSettings)));
    when(ngSecretService.get(any(ScopeInfo.class), eq(LDAP_SECRET_IDENTIFIER))).thenReturn(Optional.of(existingSecret));
    when(ldapSettingsMapper.toNgLdapSettingsFromCG(any())).thenReturn(ngLdapSettings);
    when(userGroupService.getUserGroupsBySsoId(anyString())).thenReturn(Collections.emptyList());

    // Act
    scheduler.run();

    // Assert
    verify(ngSecretService, times(1)).get(any(ScopeInfo.class), eq(LDAP_SECRET_IDENTIFIER));
    verify(secretManagerClient, never()).getHarnessSecretValue(anyString(), anyString());
    verify(ngSecretService, never()).create(any(), any());
    verify(ngLdapSettingsService, times(1)).create(any(NGLdapSettings.class));
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_CreateNewSecret_Success() throws Exception {
    // Arrange
    software.wings.beans.sso.LdapConnectionSettings connectionSettings =
        new software.wings.beans.sso.LdapConnectionSettings();
    connectionSettings.setEncryptedBindPassword(SECRET_ID);
    connectionSettings.setHost("ldap.example.com");

    software.wings.beans.sso.LdapSettingsDTO ldapSettings = new software.wings.beans.sso.LdapSettingsDTO();
    ldapSettings.setConnectionSettings(connectionSettings);

    NGLdapSettings ngLdapSettings = new NGLdapSettings();
    ngLdapSettings.setConnectionSettings(new LdapConnectionSettings());

    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(ACCOUNT_ID));
    when(ngLdapSettingsService.get(anyString())).thenReturn(null);
    when(managerClient.getLdapSettingsV2(anyString())).thenReturn(ldapSettingsCall);
    when(ldapSettingsCall.execute()).thenReturn(Response.success(new RestResponse<>(ldapSettings)));
    when(ngSecretService.get(any(ScopeInfo.class), eq(LDAP_SECRET_IDENTIFIER))).thenReturn(Optional.empty());
    when(secretManagerClient.getHarnessSecretValue(anyString(), anyString())).thenReturn(secretValueCall);
    when(secretValueCall.execute()).thenReturn(Response.success(new RestResponse<>(SECRET_VALUE)));
    when(ldapSettingsMapper.toNgLdapSettingsFromCG(any())).thenReturn(ngLdapSettings);
    when(userGroupService.getUserGroupsBySsoId(anyString())).thenReturn(Collections.emptyList());
    doAnswer(invocation -> {
      assertThat(SecurityContextBuilder.getPrincipal().getName()).isEqualTo(NG_MANAGER.getServiceId());
      assertThat(SourcePrincipalContextBuilder.getSourcePrincipal().getName()).isEqualTo(NG_MANAGER.getServiceId());
      return null;
    })
        .when(ngSecretService)
        .create(any(ScopeInfo.class), any(SecretDTOV2.class));

    // Act
    scheduler.run();

    // Assert
    verify(secretManagerClient, times(1)).getHarnessSecretValue(anyString(), eq(SECRET_ID));
    verify(ngSecretService, times(1)).create(any(ScopeInfo.class), any(SecretDTOV2.class));
    verify(ngLdapSettingsService, times(1)).create(any(NGLdapSettings.class));
    assertThat(SecurityContextBuilder.getPrincipal()).isNull();
    assertThat(SourcePrincipalContextBuilder.getSourcePrincipal()).isNull();
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_SkipWhenDecryptedSecretValueEmpty() throws Exception {
    // Arrange
    software.wings.beans.sso.LdapConnectionSettings connectionSettings =
        new software.wings.beans.sso.LdapConnectionSettings();
    connectionSettings.setEncryptedBindPassword(SECRET_ID);

    software.wings.beans.sso.LdapSettingsDTO ldapSettings = new software.wings.beans.sso.LdapSettingsDTO();
    ldapSettings.setConnectionSettings(connectionSettings);

    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(ACCOUNT_ID));
    when(ngLdapSettingsService.get(anyString())).thenReturn(null);
    when(managerClient.getLdapSettingsV2(anyString())).thenReturn(ldapSettingsCall);
    when(ldapSettingsCall.execute()).thenReturn(Response.success(new RestResponse<>(ldapSettings)));
    when(ngSecretService.get(any(ScopeInfo.class), eq(LDAP_SECRET_IDENTIFIER))).thenReturn(Optional.empty());
    when(secretManagerClient.getHarnessSecretValue(anyString(), anyString())).thenReturn(secretValueCall);
    when(secretValueCall.execute()).thenReturn(Response.success(new RestResponse<>("")));

    // Act
    scheduler.run();

    // Assert
    verify(secretManagerClient, times(1)).getHarnessSecretValue(anyString(), eq(SECRET_ID));
    verify(ngSecretService, never()).create(any(), any());
    verify(ngLdapSettingsService, never()).create(any());
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_ContinueOnSecretCreationException() throws Exception {
    // Arrange
    software.wings.beans.sso.LdapConnectionSettings connectionSettings =
        new software.wings.beans.sso.LdapConnectionSettings();
    connectionSettings.setEncryptedBindPassword(SECRET_ID);

    software.wings.beans.sso.LdapSettingsDTO ldapSettings = new software.wings.beans.sso.LdapSettingsDTO();
    ldapSettings.setConnectionSettings(connectionSettings);

    NGLdapSettings ngLdapSettings = new NGLdapSettings();
    ngLdapSettings.setConnectionSettings(new LdapConnectionSettings());

    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(ACCOUNT_ID));
    when(ngLdapSettingsService.get(anyString())).thenReturn(null);
    when(managerClient.getLdapSettingsV2(anyString())).thenReturn(ldapSettingsCall);
    when(ldapSettingsCall.execute()).thenReturn(Response.success(new RestResponse<>(ldapSettings)));
    when(ngSecretService.get(any(ScopeInfo.class), eq(LDAP_SECRET_IDENTIFIER))).thenReturn(Optional.empty());
    when(secretManagerClient.getHarnessSecretValue(anyString(), anyString()))
        .thenThrow(new RuntimeException("Decryption failed"));
    when(ldapSettingsMapper.toNgLdapSettingsFromCG(any())).thenReturn(ngLdapSettings);
    when(userGroupService.getUserGroupsBySsoId(anyString())).thenReturn(Collections.emptyList());

    // Act
    scheduler.run();

    // Assert - verify that LDAP settings are still created even when secret creation fails
    verify(secretManagerClient, times(1)).getHarnessSecretValue(anyString(), eq(SECRET_ID));
    verify(ngSecretService, never()).create(any(), any());
    verify(ngLdapSettingsService, times(1)).create(any(NGLdapSettings.class));
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_SuccessfulMigrationWithUserGroupSync() throws Exception {
    // Arrange
    String ldapUuid = "ldapUuid123";
    String ngSsoId = "ngLdapId456";

    software.wings.beans.sso.LdapConnectionSettings connectionSettings =
        new software.wings.beans.sso.LdapConnectionSettings();
    connectionSettings.setEncryptedBindPassword(SECRET_ID);

    software.wings.beans.sso.LdapSettingsDTO ldapSettings = new software.wings.beans.sso.LdapSettingsDTO();
    ldapSettings.setConnectionSettings(connectionSettings);
    ldapSettings.setUuid(ldapUuid);

    NGLdapSettings ngLdapSettings = new NGLdapSettings();
    ngLdapSettings.setIdentifier(ngSsoId);
    ngLdapSettings.setConnectionSettings(new LdapConnectionSettings());

    UserGroup userGroup1 = UserGroup.builder().identifier("ug1").build();
    UserGroup userGroup2 = UserGroup.builder().identifier("ug2").build();
    List<UserGroup> userGroups = Arrays.asList(userGroup1, userGroup2);

    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(ACCOUNT_ID));
    when(ngLdapSettingsService.get(ACCOUNT_ID)).thenReturn(null);
    when(managerClient.getLdapSettingsV2(ACCOUNT_ID)).thenReturn(ldapSettingsCall);
    when(ldapSettingsCall.execute()).thenReturn(Response.success(new RestResponse<>(ldapSettings)));
    when(ngSecretService.get(any(ScopeInfo.class), eq(LDAP_SECRET_IDENTIFIER))).thenReturn(Optional.empty());
    when(secretManagerClient.getHarnessSecretValue(ACCOUNT_ID, SECRET_ID)).thenReturn(secretValueCall);
    when(secretValueCall.execute()).thenReturn(Response.success(new RestResponse<>(SECRET_VALUE)));
    when(ldapSettingsMapper.toNgLdapSettingsFromCG(any())).thenReturn(ngLdapSettings);
    when(userGroupService.getUserGroupsBySsoId(ldapUuid)).thenReturn(userGroups);

    // Act
    scheduler.run();

    // Assert
    verify(ngSecretService, times(1)).create(any(ScopeInfo.class), any(SecretDTOV2.class));
    verify(ngLdapSettingsService, times(1)).create(ngLdapSettings);
    verify(userGroupService, times(1)).getUserGroupsBySsoId(ldapUuid);
    verify(userGroupService, times(1)).updateLinkedSsoId(userGroup1, ngSsoId);
    verify(userGroupService, times(1)).updateLinkedSsoId(userGroup2, ngSsoId);
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_ThrowRuntimeExceptionOnNGLdapSettingsCreationFailure() throws Exception {
    // Arrange
    software.wings.beans.sso.LdapConnectionSettings connectionSettings =
        new software.wings.beans.sso.LdapConnectionSettings();
    connectionSettings.setEncryptedBindPassword(SECRET_ID);

    software.wings.beans.sso.LdapSettingsDTO ldapSettings = new software.wings.beans.sso.LdapSettingsDTO();
    ldapSettings.setConnectionSettings(connectionSettings);

    NGLdapSettings ngLdapSettings = new NGLdapSettings();
    ngLdapSettings.setConnectionSettings(new LdapConnectionSettings());

    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(ACCOUNT_ID));
    when(ngLdapSettingsService.get(ACCOUNT_ID)).thenReturn(null);
    when(managerClient.getLdapSettingsV2(ACCOUNT_ID)).thenReturn(ldapSettingsCall);
    when(ldapSettingsCall.execute()).thenReturn(Response.success(new RestResponse<>(ldapSettings)));
    when(ngSecretService.get(any(ScopeInfo.class), eq(LDAP_SECRET_IDENTIFIER))).thenReturn(Optional.empty());
    when(secretManagerClient.getHarnessSecretValue(ACCOUNT_ID, SECRET_ID)).thenReturn(secretValueCall);
    when(secretValueCall.execute()).thenReturn(Response.success(new RestResponse<>(SECRET_VALUE)));
    when(ldapSettingsMapper.toNgLdapSettingsFromCG(any())).thenReturn(ngLdapSettings);
    doThrow(new RuntimeException("Database error")).when(ngLdapSettingsService).create(any(NGLdapSettings.class));

    // Act & Assert
    assertThatThrownBy(() -> scheduler.run())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Database error");

    verify(ngLdapSettingsService, times(1)).create(ngLdapSettings);
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_ProcessMultipleAccountsWithFeatureFlag() throws Exception {
    // Arrange
    String account1 = "account1";
    String account2 = "account2";

    software.wings.beans.sso.LdapConnectionSettings connectionSettings =
        new software.wings.beans.sso.LdapConnectionSettings();
    connectionSettings.setEncryptedBindPassword(SECRET_ID);

    software.wings.beans.sso.LdapSettingsDTO ldapSettings = new software.wings.beans.sso.LdapSettingsDTO();
    ldapSettings.setConnectionSettings(connectionSettings);

    NGLdapSettings ngLdapSettings = new NGLdapSettings();
    ngLdapSettings.setConnectionSettings(new LdapConnectionSettings());

    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Set.of(account1, account2));
    when(ngLdapSettingsService.get(anyString())).thenReturn(null);
    when(managerClient.getLdapSettingsV2(anyString())).thenReturn(ldapSettingsCall);
    when(ldapSettingsCall.execute()).thenReturn(Response.success(new RestResponse<>(ldapSettings)));
    when(ngSecretService.get(any(ScopeInfo.class), eq(LDAP_SECRET_IDENTIFIER))).thenReturn(Optional.empty());
    when(secretManagerClient.getHarnessSecretValue(anyString(), anyString())).thenReturn(secretValueCall);
    when(secretValueCall.execute()).thenReturn(Response.success(new RestResponse<>(SECRET_VALUE)));
    when(ldapSettingsMapper.toNgLdapSettingsFromCG(any())).thenReturn(ngLdapSettings);
    when(userGroupService.getUserGroupsBySsoId(anyString())).thenReturn(Collections.emptyList());

    // Act
    scheduler.run();

    // Assert
    verify(ngLdapSettingsService, times(2)).get(anyString());
    verify(managerClient, times(2)).getLdapSettingsV2(anyString());
    verify(ngSecretService, times(2)).create(any(ScopeInfo.class), any(SecretDTOV2.class));
    verify(ngLdapSettingsService, times(2)).create(any(NGLdapSettings.class));
  }

  @Test
  @Owner(developers = NIYASHA)
  @Category(UnitTests.class)
  public void testRun_NoAccountsWithFeatureFlagEnabled() {
    // Arrange
    when(featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name()))
        .thenReturn(Collections.emptySet());

    // Act
    scheduler.run();

    // Assert
    verify(ngLdapSettingsService, never()).get(anyString());
    verify(managerClient, never()).getLdapSettingsV2(anyString());
  }
}