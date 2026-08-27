/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import static lombok.AccessLevel.PRIVATE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.HarnessToIDPHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.namespace.service.NamespaceServiceImpl;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@AllArgsConstructor(access = PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class SyncUserGroupsMigration implements NGMigration {
  @Inject private HarnessToIDPHelper harnessToIDPHelper;
  @Inject private CatalogEntityRepository catalogEntityRepository;
  @Inject private NamespaceServiceImpl namespaceService;

  @Override
  public void migrate() {
    log.info("Starting SyncUserGroupsMigration migration");
    try {
      List<String> accountIds = namespaceService.getAccountIds();

      if (CollectionUtils.isEmpty(accountIds)) {
        log.info("No accounts found for synchronization");
        return;
      }

      log.info("Found {} accounts to process for user group synchronization", accountIds.size());

      processUserGroupsForAllAccounts(accountIds);

      log.info("Successfully completed SyncUserGroupsMigration migration");
    } catch (Exception e) {
      log.error("Error during SyncUserGroupsMigration migration", e);
      throw new RuntimeException("Failed to sync user groups", e);
    }
  }

  private List<CatalogEntity> fetchUserGroupsForAccount(String accountId) {
    try {
      log.debug("Fetching user groups for account: {}", accountId);
      return catalogEntityRepository.findAllByAccountIdentifierAndKind(accountId, "group");
    } catch (Exception e) {
      log.error("Failed to fetch user groups for account: {}", accountId, e);
      throw e;
    }
  }

  private void processUserGroupsForAllAccounts(List<String> accountIds) {
    log.info("Processing user groups for {} accounts", accountIds.size());
    int successCount = 0;
    int failureCount = 0;
    int totalGroupsProcessed = 0;

    for (String accountId : accountIds) {
      try {
        List<CatalogEntity> userGroups = fetchUserGroupsForAccount(accountId);

        if (CollectionUtils.isEmpty(userGroups)) {
          log.debug("No user groups found for account: {}", accountId);
          continue;
        }

        log.debug("Syncing {} user groups for account {}", userGroups.size(), accountId);
        harnessToIDPHelper.harnessToIdpSync(userGroups, accountId, UPDATE_ACTION);
        log.debug("Successfully synced {} user groups for account {}", userGroups.size(), accountId);

        successCount++;
        totalGroupsProcessed += userGroups.size();
      } catch (Exception e) {
        log.error("Failed to sync user groups for account {}: {}", accountId, e.getMessage(), e);
        failureCount++;
      }
    }

    log.info("User group sync summary: {} accounts successful, {} accounts failed, {} total groups processed",
        successCount, failureCount, totalGroupsProcessed);
    if (failureCount > 0) {
      log.warn("Some accounts failed during user group synchronization");
    }
  }
}
