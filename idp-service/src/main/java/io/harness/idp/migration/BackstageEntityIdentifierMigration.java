/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.checks.service.CheckService;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class BackstageEntityIdentifierMigration implements NGMigration {
  @Inject NamespaceService namespaceService;
  @Inject ScoreService scoreService;
  @Inject ScorecardService scorecardService;
  @Inject CheckService checkService;
  @Inject BackstageService backstageService;

  @Override
  public void migrate() {
    log.info("Starting the migration for modifying entity identifiers.");
    List<String> accountIdentifiers = namespaceService.getAccountIds();
    log.info("Fetched {} IDP active accounts for entity identifier migration", accountIdentifiers.size());
    accountIdentifiers.forEach(accountIdentifier -> {
      Map<String, String> entityIdentifiersMap = new HashMap<>();
      if (!entityIdentifiersMap.isEmpty()) {
        log.info("Fetched {} backstage catalogs for account {}", entityIdentifiersMap.size(), accountIdentifier);
        scoreService.migrateEntityIdentifier(entityIdentifiersMap, accountIdentifier);
        scorecardService.migrateEntityIdentifier(entityIdentifiersMap, accountIdentifier);
        checkService.migrateEntityIdentifier(entityIdentifiersMap, accountIdentifier);
      }
    });
    log.info("Completed the migration for modifying entity identifiers.");
  }

  private Map<String, String> getEntityIdentifiersMap(String accountIdentifier) {
    List<BackstageCatalogEntity> backstageCatalogEntities =
        backstageService.findAllByAccountIdentifier(accountIdentifier);
    Map<String, String> entitiyIdentifiersMap = new HashMap<>();
    backstageCatalogEntities.forEach(backstageCatalogEntity
        -> entitiyIdentifiersMap.put(BackstageCatalogEntity.getValue(backstageCatalogEntity.getMetadata(),
                                         MetadataFieldConstants.UID, String.class),
            backstageCatalogEntity.getEntityUid()));
    return entitiyIdentifiersMap;
  }
}
