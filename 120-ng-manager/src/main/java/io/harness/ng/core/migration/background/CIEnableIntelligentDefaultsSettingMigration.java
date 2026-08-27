/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.account.utils.AccountUtils;
import io.harness.beans.FeatureName;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIEnableIntelligentDefaultsSettingMigration implements NGMigration {
  @Inject private NGFeatureFlagHelperService featureFlagService;
  @Inject private SettingsService settingsService;
  @Inject private AccountUtils accountUtils;
  private static final String DEBUG_LOG = "[CIEnableIntelligentDefaultsSettingMigration]: ";
  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Start migrating CI_ENABLE_INTELLIGENT_DEFAULTS FF disabled accounts to settings.");
    String settingIdentifier = SettingIdentifiers.CI_BUILD_INTEL_AUTOMATIC_ENABLE;
    String settingValueFalse = "false";

    int successfullyMigratedAccounts = 0;
    try {
      List<String> accountIds = accountUtils.getAllNGAccountIds();
      for (String accountId : accountIds) {
        try {
          if (featureFlagService.isDisabled(accountId, FeatureName.CI_ENABLE_INTELLIGENT_DEFAULTS)) {
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
          }
        } catch (Exception e) {
          log.error(DEBUG_LOG + "Failed to create setting {} for account {}", settingIdentifier, accountId, e);
        }
      }
    } catch (Exception e) {
      log.error(
          DEBUG_LOG + "Failed during migration. Successfully migrated {} accounts", successfullyMigratedAccounts, e);
    }

    log.info(DEBUG_LOG + "Successfully migrated {} accounts.", successfullyMigratedAccounts);
  }
}
