/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.migration.beans.NGMigration;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingUpdateResponseDTO;
import io.harness.ngsettings.entities.AccountSetting;
import io.harness.ngsettings.entities.AccountSetting.AccountSettingKeys;
import io.harness.ngsettings.entities.Setting.SettingKeys;
import io.harness.ngsettings.services.SettingsService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class CEIncludeDiscountSettingMigration implements NGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private SettingsService settingsService;

  // The “source” setting you use to decide accounts that opted-in for discounts
  private static final String SOURCE_INCLUDE_DISCOUNTS_IDENTIFIER = SettingIdentifiers.INCLUDE_GCP_DISCOUNTS_IDENTIFIER;

  // The target identifiers we must set to true
  private static final List<String> TARGET_IDENTIFIERS =
      Arrays.asList(SettingIdentifiers.INCLUDE_GCP_RESOURCE_BASED_CUD_CREDITS_IDENTIFIER,
          SettingIdentifiers.INCLUDE_GCP_SUSTAINED_USE_DISCOUNTS_IDENTIFIER,
          SettingIdentifiers.INCLUDE_GCP_LEGACY_BASED_CUD_CREDITS_IDENTIFIER,
          SettingIdentifiers.INCLUDE_GCP_SUBSCRIPTION_CREDITS_IDENTIFIER);

  @Override
  public void migrate() {
    try {
      log.info("Starting CEIncludeDiscountSettingMigration");

      // 1) Find all accounts where the source identifier is true
      final Query<AccountSetting> q = hPersistence.createQuery(AccountSetting.class, excludeValidate)
                                          .filter(SettingKeys.identifier, SOURCE_INCLUDE_DISCOUNTS_IDENTIFIER)
                                          .filter(AccountSettingKeys.value, "true");

      // Use distinct account list
      final Set<String> accountIds = new LinkedHashSet<>();
      q.project(SettingKeys.accountIdentifier, true)
          .project(SettingKeys.identifier, true)
          .asList()
          .forEach(doc -> accountIds.add(doc.getAccountIdentifier()));

      log.info("Found {} accounts with '{}' = true", accountIds.size(), SOURCE_INCLUDE_DISCOUNTS_IDENTIFIER);

      // 2) For each account, update all target identifiers to "true" via SettingsService
      int updatedAccounts = 0;
      for (String accountId : accountIds) {
        try {
          updateTargetsTrueForAccount(accountId);
          updatedAccounts++;
        } catch (Exception ex) {
          log.error("Failed to update settings for account {}", accountId, ex);
        }
      }

      log.info("Finished CEIncludeDiscountSettingMigration. Accounts updated: {}", updatedAccounts);
    } catch (Exception e) {
      log.error("Failure in CEIncludeDiscountSettingMigration", e);
    }
  }

  private void updateTargetsTrueForAccount(final String accountId) {
    // Build Account scope. For account scope, uniqueId should be accountId
    ScopeInfo accountScope =
        ScopeInfo.builder().scopeType(ScopeLevel.ACCOUNT).accountIdentifier(accountId).uniqueId(accountId).build();

    // Build requests to set each target identifier to "true"
    List<SettingRequestDTO> requests = TARGET_IDENTIFIERS.stream()
                                           .map(id
                                               -> SettingRequestDTO.builder()
                                                      .identifier(id)
                                                      .value("true")
                                                      .allowOverrides(true)
                                                      .updateType(SettingUpdateType.UPDATE)
                                                      .build())
                                           .collect(Collectors.toCollection(ArrayList::new));

    List<SettingUpdateResponseDTO> responses = settingsService.update(accountScope, requests);
    log.info("Updated {} CE discount-related settings for account {}", responses.size(), accountId);
  }
}
