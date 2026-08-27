/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.NoResultFoundException;
import io.harness.ldap.entity.NGLdapSettings;
import io.harness.ldap.mapper.NgLdapSettingsMapper;
import io.harness.ldap.service.NGLdapSettingsService;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.security.NgManagerOpaContextGuard;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.sso.LdapSettingsDTO;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class NGLdapMigrationScheduler implements Runnable {
  @Inject private NGFeatureFlagHelperService featureFlagService;
  @Inject private AuthSettingsManagerClient managerClient;
  @Inject private NGLdapSettingsService ngLdapSettingsService;
  @Inject private NgLdapSettingsMapper ldapSettingsMapper;
  @Inject private NGEncryptedDataService encryptedDataService;
  @Inject private SecretCrudService ngSecretService;
  @Inject private UserGroupService userGroupService;
  @Inject private SecretManagerClient secretManagerClient;
  private static final String LDAP_SECRET = "ldap-secret";

  @Override
  public void run() {
    log.info("[NGLdapMigrationScheduler]: Starting NG ldap settings migration");
    Set<String> accountIds =
        featureFlagService.getFeatureFlagEnabledAccountIds(FeatureName.PL_ENABLE_NG_LDAP_SETTINGS.name());
    for (String accountIdentifier : accountIds) {
      log.info("Starting NG ldap settings migration for account id {}", accountIdentifier);
      try {
        NGLdapSettings ngLdapSettings = ngLdapSettingsService.get(accountIdentifier);
        if (ngLdapSettings != null) {
          continue;
        }
      } catch (NoResultFoundException noResultFoundException) {
        log.info("Ldap settings not found in NG, so going ahead with migration");
      }
      LdapSettingsDTO ldapSettings = getResponse(managerClient.getLdapSettingsV2(accountIdentifier));
      if (ldapSettings == null) {
        log.info("No Ldap settings not found in CG for account id {}", accountIdentifier);
        continue;
      }

      if (ldapSettings.getConnectionSettings() == null) {
        log.warn("LDAP connection settings are null for account {}, skipping migration", accountIdentifier);
        continue;
      }

      String secret = ldapSettings.getConnectionSettings().getEncryptedBindPassword();
      if (!isNotEmpty(secret)) {
        log.warn("Bind secret is empty for account {}, skipping migration", accountIdentifier);
        continue;
      }

      boolean secretExists = false;
      try {
        java.util.Optional<SecretResponseWrapper> existingSecret = ngSecretService.get(
            ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build(), LDAP_SECRET);
        if (existingSecret.isPresent()) {
          log.info("Secret with identifier '{}' already exists for account {}, reusing existing secret", LDAP_SECRET,
              accountIdentifier);
          secretExists = true;
        }
      } catch (Exception e) {
        log.info("Secret '{}' not found for account {}, will create new secret", LDAP_SECRET, accountIdentifier);
      }

      if (!secretExists) {
        try {
          String secretValue = getResponse(secretManagerClient.getHarnessSecretValue(accountIdentifier, secret));
          if (!isNotEmpty(secretValue)) {
            log.error("Decrypted secret value is empty for account {}, skipping migration", accountIdentifier);
            continue;
          }
          SecretTextSpecDTO secretSpec = SecretTextSpecDTO.builder()
                                             .secretManagerIdentifier("account.harnessSecretManager")
                                             .valueType(ValueType.Inline)
                                             .value(secretValue)
                                             .build();

          SecretDTOV2 secretDTO = SecretDTOV2.builder()
                                      .type(SecretType.SecretText)
                                      .name(LDAP_SECRET)
                                      .identifier(LDAP_SECRET)
                                      .description("LDAP bind password migrated from CG")
                                      .spec(secretSpec)
                                      .build();
          try (NgManagerOpaContextGuard ignore = new NgManagerOpaContextGuard()) {
            ngSecretService.create(
                ScopeInfo.builder().accountIdentifier(accountIdentifier).uniqueId(accountIdentifier).build(),
                secretDTO);
          }
          log.info("Successfully created ldap secret for account {}", accountIdentifier);
        } catch (Exception ex) {
          log.error("Error while fetching or creating secret for account {}", accountIdentifier, ex);
        }
      }

      NGLdapSettings ngLdapSettings = ldapSettingsMapper.toNgLdapSettingsFromCG(ldapSettings);
      SecretRefData secretRef = SecretRefData.builder().identifier(LDAP_SECRET).scope(Scope.ACCOUNT).build();
      ngLdapSettings.getConnectionSettings().setPasswordRef(secretRef);

      try {
        ngLdapSettingsService.create(ngLdapSettings);
        log.info("NG ldap settings created for account id {}", accountIdentifier);
      } catch (Exception e) {
        log.error("Exception while creating NG ldap settings for account id {}", accountIdentifier);
        throw new RuntimeException(e);
      }
      List<UserGroup> userGroupsToSync = userGroupService.getUserGroupsBySsoId(ldapSettings.getUuid());
      if (isNotEmpty(userGroupsToSync)) {
        String ngSsoId = ngLdapSettings.getIdentifier();
        userGroupsToSync.forEach(userGroup -> {
          userGroupService.updateLinkedSsoId(userGroup, ngSsoId);
          log.info("Updated ssoId for user group {} with new NG ldap settings ssoId {}.", userGroup.getIdentifier(),
              ngSsoId);
        });
      }
      log.info("Migration completed for NG ldap settings for account id {}.", accountIdentifier);
    }
  }
}
