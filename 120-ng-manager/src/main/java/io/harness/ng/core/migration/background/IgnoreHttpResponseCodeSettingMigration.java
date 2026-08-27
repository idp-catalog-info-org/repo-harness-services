/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.migration.beans.NGMigration;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.entities.AccountSetting;
import io.harness.repositories.ngsettings.spring.SettingRepository;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IgnoreHttpResponseCodeSettingMigration implements NGMigration {
  @Inject private NGFeatureFlagHelperService featureFlagService;
  @Inject private SettingRepository settingRepository;
  private static final String DEBUG_LOG = "[IgnoreHttpResponseCodeSettingMigration]: ";
  @Override
  public void migrate() {
    log.info(DEBUG_LOG
        + "Start migrating CDS_USE_HTTP_CHECK_IGNORE_RESPONSE_INSTEAD_OF_SOCKET_NG FF enabled accounts to settings.");
    String settingIdentifier = SettingIdentifiers.IGNORE_STATUS_CODE_FOR_HTTP_CONNECTIONS;
    String settingValueToSet = "true";

    int successfullyMigratedAccounts = 0;
    try {
      // migration is already released, removing FF enum usage here
      Set<String> accountIds =
          featureFlagService.getFeatureFlagEnabledAccountIds("CDS_USE_HTTP_CHECK_IGNORE_RESPONSE_INSTEAD_OF_SOCKET_NG");
      log.info(DEBUG_LOG + "Total {} accounts to be migrated.", accountIds.size());
      for (String accountId : accountIds) {
        try {
          AccountSetting accountSetting = AccountSetting.builder()
                                              .accountIdentifier(accountId)
                                              .identifier(settingIdentifier)
                                              .allowOverrides(true)
                                              .category(SettingCategory.CD)
                                              .valueType(SettingValueType.BOOLEAN)
                                              .value(settingValueToSet)
                                              .build();
          settingRepository.upsert(accountSetting);
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
