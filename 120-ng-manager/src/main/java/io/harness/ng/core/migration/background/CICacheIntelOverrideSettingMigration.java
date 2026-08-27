/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.migration.beans.NGMigration;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.services.SettingsService;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CICacheIntelOverrideSettingMigration implements NGMigration {
  @Inject private NGFeatureFlagHelperService featureFlagService;
  @Inject private SettingsService settingsService;
  private static final String DEBUG_LOG = "[CICacheOverrideSettingMigration]: ";
  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Start migrating CI_CACHE_OVERRIDE_FALSE FF enabled accounts to settings.");
    String settingIdentifier = SettingIdentifiers.CI_CACHE_INTEL_ALWAYS_OVERRIDE;
    String settingValueFalse = "false";

    int successfullyMigratedAccounts = 0;
    try {
      // using string for FF instead of enum since FF will be deprecated later
      Set<String> accountIds = featureFlagService.getFeatureFlagEnabledAccountIds("CI_CACHE_OVERRIDE_FALSE");
      log.info(DEBUG_LOG + "Total {} accounts to be migrated.", accountIds.size());
      for (String accountId : accountIds) {
        try {
          ScopeInfo scopeInfo = ScopeInfo.builder()
                                    .accountIdentifier(accountId)
                                    .scopeType(ScopeLevel.ACCOUNT)
                                    .uniqueId(accountId)
                                    .build();

          List<SettingRequestDTO> settingRequestDTOList = List.of(SettingRequestDTO.builder()
                                                                      .identifier(settingIdentifier)
                                                                      .value(settingValueFalse)
                                                                      .allowOverrides(true)
                                                                      .updateType(SettingUpdateType.UPDATE)
                                                                      .build());
          settingsService.update(scopeInfo, settingRequestDTOList);
          successfullyMigratedAccounts = successfullyMigratedAccounts + 1;
        } catch (Exception e) {
          log.error(DEBUG_LOG + "Failed to create setting {} for account {}", settingIdentifier, accountId, e);
        }
      }
    } catch (Exception e) {
      log.error(
          DEBUG_LOG + "Failed during migration. Successfully migrated {} accounts", successfullyMigratedAccounts, e);
    }
  }
}
