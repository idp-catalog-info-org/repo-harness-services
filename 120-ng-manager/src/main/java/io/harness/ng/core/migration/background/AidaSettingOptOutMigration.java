/*
 * Copyright 2026 Harness Inc. All rights reserved.
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
import io.harness.ngsettings.dto.SettingUpdateResponseDTO;
import io.harness.ngsettings.entities.AccountSetting;
import io.harness.ngsettings.services.SettingsService;
import io.harness.repositories.ngsettings.spring.SettingRepository;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AidaSettingOptOutMigration implements NGMigration {
  private static final String RESOURCE_PATH = "io/harness/ngsettings/aida-optout-accounts.txt";
  private static final String DEBUG_LOG = "[AidaSettingOptOutMigration]: ";
  private static final String SETTING_VALUE_FALSE = "false";

  @Inject private SettingsService settingsService;
  @Inject private SettingRepository settingRepository;

  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Start seeding aida=false for opt-out accounts.");

    Set<String> accountIds = loadAccountIds();
    if (accountIds.isEmpty()) {
      log.info(DEBUG_LOG + "No opt-out account IDs found in {}. Nothing to do.", RESOURCE_PATH);
      return;
    }

    int inserted = 0;
    int skipped = 0;
    int failed = 0;
    for (String accountId : accountIds) {
      try {
        Optional<AccountSetting> existing = settingRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
            accountId, accountId, SettingIdentifiers.AIDA);
        if (existing.isPresent()) {
          skipped++;
          continue;
        }

        ScopeInfo scopeInfo =
            ScopeInfo.builder().accountIdentifier(accountId).scopeType(ScopeLevel.ACCOUNT).uniqueId(accountId).build();

        List<SettingRequestDTO> settingRequestDTOList = List.of(SettingRequestDTO.builder()
                                                                    .identifier(SettingIdentifiers.AIDA)
                                                                    .value(SETTING_VALUE_FALSE)
                                                                    .allowOverrides(true)
                                                                    .updateType(SettingUpdateType.UPDATE)
                                                                    .build());
        List<SettingUpdateResponseDTO> responses = settingsService.update(scopeInfo, settingRequestDTOList);
        if (responses.stream().anyMatch(response -> !response.isUpdateStatus())) {
          failed++;
          log.error(DEBUG_LOG + "Failed to seed aida=false for account {}. Response: {}", accountId, responses);
          continue;
        }

        inserted++;
      } catch (Exception e) {
        failed++;
        log.error(DEBUG_LOG + "Failed to seed aida=false for account {}", accountId, e);
      }
    }

    log.info(
        DEBUG_LOG + "Done. inserted={}, skipped={}, failed={}, total={}", inserted, skipped, failed, accountIds.size());
  }

  private Set<String> loadAccountIds() {
    Set<String> accountIds = new LinkedHashSet<>();
    try {
      URL url = getClass().getClassLoader().getResource(RESOURCE_PATH);
      if (url == null) {
        log.error(DEBUG_LOG + "Resource {} not found on classpath.", RESOURCE_PATH);
        return accountIds;
      }
      String contents = Resources.toString(url, StandardCharsets.UTF_8);
      Arrays.stream(contents.split("\\R"))
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .forEach(accountIds::add);
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to load account IDs from {}", RESOURCE_PATH, e);
    }
    return accountIds;
  }
}
