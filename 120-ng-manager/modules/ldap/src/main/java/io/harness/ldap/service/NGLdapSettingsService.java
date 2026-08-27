/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ldap.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.ldap.dto.NGLdapSettingsWithEncryptedDataDetailsDTO;
import io.harness.ldap.entity.NGLdapSettings;
import io.harness.ng.core.user.entities.UserGroup;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.SSOType;
import software.wings.helpers.ext.ldap.LdapResponse;

import java.util.Collection;
import java.util.List;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(HarnessTeam.PL)
public interface NGLdapSettingsService {
  NGLdapSettings create(@NotNull NGLdapSettings settings) throws Exception;

  NGLdapSettings get(@NotBlank String accountId);

  NGLdapSettings update(@NotNull NGLdapSettings settings, @NotBlank String accountIdentifier) throws Exception;

  boolean delete(@NotBlank String accountId);

  LdapTestResponse validateLdapConnectionSettings(@NotNull String accountIdentifier, NGLdapSettings ldapSettings);

  LdapTestResponse validateLdapUserSettings(@NotNull String accountIdentifier, NGLdapSettings ldapSettings);

  LdapTestResponse validateLdapGroupSettings(@NotNull String accountIdentifier, NGLdapSettings ldapSettings);

  Collection<LdapGroupResponse> searchLdapGroupsByName(@NotNull String accountIdentifier, @NotNull String name);

  void syncUserGroupsJob(@NotNull String accountIdentifier);

  void syncUserGroupWithGroupId(@NotNull ScopeInfo scopeInfo, @NotNull String userGroupId);

  UserGroup linkToSsoGroup(ScopeInfo scopeInfo, @NotBlank String userGroupId, @NotNull SSOType ssoType,
      @NotBlank String ssoId, @NotBlank String ssoGroupId, @NotBlank String ssoGroupName);

  NGLdapSettingsWithEncryptedDataDetailsDTO getLdapSettingsWithEncryptedDataDetails(@NotBlank String accountId);

  List<Long> getIterationsFromCron(String accountId, String cron);

  LdapResponse testLDAPLogin(@NotNull ScopeInfo scopeInfo, @NotNull String email, @NotNull String password);
}
