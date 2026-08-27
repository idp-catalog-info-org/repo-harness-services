/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.repositories.CatalogTableRepository;
import io.harness.idp.catalog.repositories.KindEntityRepository;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
public class AIDependencyKindCleanupMigration implements NGMigration {
  private static final String AI_DEPENDENCY_KIND_IDENTIFIER = "aidependency";
  private static final String AI_DEPENDENCY_TABLE_IDENTIFIER = "__Harness_AIDependency_Table__";

  @Inject KindEntityRepository kindEntityRepository;
  @Inject CatalogTableRepository catalogTableRepository;

  @Override
  public void migrate() {
    log.info("Starting migration to remove AIDependency kind and its catalog table.");

    kindEntityRepository.findByAccountIdentifierAndIdentifier(GLOBAL_ACCOUNT_ID, AI_DEPENDENCY_KIND_IDENTIFIER)
        .ifPresent(kindEntity -> {
          kindEntityRepository.delete(kindEntity);
          log.info("Deleted AIDependency kind entity.");
        });

    catalogTableRepository.findByAccountIdentifierAndIdentifier(GLOBAL_ACCOUNT_ID, AI_DEPENDENCY_TABLE_IDENTIFIER)
        .ifPresent(tableEntity -> {
          catalogTableRepository.delete(tableEntity);
          log.info("Deleted AIDependency catalog table entity.");
        });

    log.info("Migration to remove AIDependency kind completed.");
  }
}
