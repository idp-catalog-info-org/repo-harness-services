/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.resource;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;

import static java.util.Objects.isNull;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidEntityException;
import io.harness.exception.InvalidRequestException;
import io.harness.ldap.dto.NGLdapSettingsWithEncryptedDataDetailsDTO;
import io.harness.ldap.mapper.NgLdapSettingsMapper;
import io.harness.ldap.service.NGLdapService;
import io.harness.ldap.service.NGLdapSettingsService;
import io.harness.ng.accesscontrol.usergroup.UserGroupPermissionUtils;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rest.RestResponse;
import io.harness.spec.server.ng.v1.model.LdapSettingsDTO;
import io.harness.sso.NGLdapSettingsWithEncryptedDataDetails;
import io.harness.utils.PmsFeatureFlagHelper;

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapGroupSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapUserSettings;
import software.wings.helpers.ext.ldap.LdapResponse;

import com.google.inject.Inject;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.Collection;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class NGLdapResourceImpl implements NGLdapResource {
  NGLdapService ngLdapService;
  AccessControlClient accessControlClient;
  UserGroupService userGroupService;
  private final Validator validator;
  NGLdapSettingsService ngLdapSettingsService;
  NgLdapSettingsMapper ngLdapSettingsMapper;
  PmsFeatureFlagHelper ngFeatureFlagHelperService;
  UserGroupPermissionUtils userGroupPermissionUtils;

  @Override
  public RestResponse<LdapTestResponse> validateLdapConnectionSettings(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, software.wings.beans.sso.LdapSettingsDTO settings, ScopeInfo scopeInfo) {
    validateLdapSettings(settings);
    LdapTestResponse ldapTestResponse = ngLdapService.validateLdapConnectionSettings(scopeInfo, settings);
    return new RestResponse<>(ldapTestResponse);
  }

  @Override
  public RestResponse<LdapTestResponse> validateLdapUserSettings(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, software.wings.beans.sso.LdapSettingsDTO settings, ScopeInfo scopeInfo) {
    validateLdapSettings(settings);
    LdapTestResponse ldapTestResponse = ngLdapService.validateLdapUserSettings(scopeInfo, settings);
    return new RestResponse<>(ldapTestResponse);
  }

  @Override
  public RestResponse<LdapTestResponse> validateLdapGroupSettings(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, software.wings.beans.sso.LdapSettingsDTO settings, ScopeInfo scopeInfo) {
    validateLdapSettings(settings);
    LdapTestResponse ldapTestResponse = ngLdapService.validateLdapGroupSettings(scopeInfo, settings);
    return new RestResponse<>(ldapTestResponse);
  }

  @Override
  public ResponseDTO<NGLdapSettingsWithEncryptedDataDetails> getNGLdapSettingsWithEncryptedDataDetail(
      String accountIdentifier) {
    NGLdapSettingsWithEncryptedDataDetailsDTO ngLdapSettingsWithEncryptedDataDetailsDTO =
        ngLdapSettingsService.getLdapSettingsWithEncryptedDataDetails(accountIdentifier);
    LdapSettingsDTO ldapSettingsDTO =
        ngLdapSettingsMapper.toLdapSettingsDTO(ngLdapSettingsWithEncryptedDataDetailsDTO.getNgLdapSettings());
    return ResponseDTO.newResponse(
        NGLdapSettingsWithEncryptedDataDetails.builder()
            .ldapSettings(ldapSettingsDTO)
            .encryptedDataDetail(ngLdapSettingsWithEncryptedDataDetailsDTO.getEncryptedDataDetail())
            .build());
  }

  @Override
  public RestResponse<Collection<LdapGroupResponse>> searchLdapGroups(String ldapId, String accountId,
      String orgIdentifier, String projectIdentifier, String name, ScopeInfo scopeInfo) {
    Collection<LdapGroupResponse> groups = null;
    if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_ENABLE_NG_LDAP_SETTINGS)) {
      try {
        groups = ngLdapSettingsService.searchLdapGroupsByName(scopeInfo.getAccountIdentifier(), name);
      } catch (InvalidEntityException ex) {
        log.warn("NG LDAP settings not populated completely, falling back to CG LDAP settings");
        groups = ngLdapService.searchLdapGroupsByName(scopeInfo, ldapId, name);
      }
    } else {
      groups = ngLdapService.searchLdapGroupsByName(scopeInfo, ldapId, name);
    }
    return new RestResponse<>(groups);
  }

  @Override
  public RestResponse<Boolean> syncLdapGroups(
      String accountId, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_ENABLE_NG_LDAP_SETTINGS)) {
      ngLdapSettingsService.syncUserGroupsJob(accountId);
    } else {
      ngLdapService.syncUserGroupsJob(scopeInfo);
    }
    return new RestResponse<>(true);
  }

  @Override
  public RestResponse<Boolean> syncLdapGroupsV2(
      String accountId, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_ENABLE_NG_LDAP_SETTINGS)) {
      ngLdapSettingsService.syncUserGroupsJob(accountId);
    } else {
      ngLdapService.syncUserGroupsJob(scopeInfo);
    }
    return new RestResponse<>(true);
  }

  @Override
  public RestResponse<LdapResponse> postLdapAuthenticationTest(String accountId, String orgIdentifier,
      String projectIdentifier, String email, String password, ScopeInfo scopeInfo) {
    return new RestResponse<>(ngLdapService.testLDAPLogin(scopeInfo, email, password));
  }

  @Override
  public RestResponse<Void> syncUserGroupLinkedToLDAP(
      String userGroupId, String accountId, String orgIdentifier, String projectIdentifier, ScopeInfo scopeInfo) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, userGroupId), userGroupPermissionUtils.getUserGroupManagePermissionForSSO(accountId));
    if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_ENABLE_NG_LDAP_SETTINGS)) {
      ngLdapSettingsService.syncUserGroupWithGroupId(scopeInfo, userGroupId);
    } else {
      ngLdapService.syncAUserGroupJob(scopeInfo, userGroupId);
    }
    return new RestResponse<>(null);
  }

  public void validateLdapSettings(software.wings.beans.sso.LdapSettingsDTO ldapSettings) {
    if (isEmpty(ldapSettings.getAccountId())) {
      throw new InvalidRequestException("accountId cannot be empty for ldap settings");
    }
    if (isNull(ldapSettings.getConnectionSettings())) {
      throw new InvalidRequestException("Connection settings are not defined for ldap settings");
    }

    Set<ConstraintViolation<LdapConnectionSettings>> connectionSettingsViolations =
        validator.validate(ldapSettings.getConnectionSettings());
    if (!connectionSettingsViolations.isEmpty()) {
      throw new JerseyViolationException(connectionSettingsViolations, null);
    }

    if (isNotEmpty(ldapSettings.getUserSettingsList())) {
      ldapSettings.getUserSettingsList().forEach(userSetting -> {
        Set<ConstraintViolation<LdapUserSettings>> userSettingsViolations = validator.validate(userSetting);
        if (!userSettingsViolations.isEmpty()) {
          throw new JerseyViolationException(userSettingsViolations, null);
        }
      });
    }

    if (isNotEmpty(ldapSettings.getGroupSettingsList())) {
      ldapSettings.getGroupSettingsList().forEach(groupSetting -> {
        Set<ConstraintViolation<LdapGroupSettings>> groupSettingsViolations = validator.validate(groupSetting);
        if (!groupSettingsViolations.isEmpty()) {
          throw new JerseyViolationException(groupSettingsViolations, null);
        }
      });
    }
  }
}
