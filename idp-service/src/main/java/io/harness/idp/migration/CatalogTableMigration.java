/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.idp.common.JacksonUtils.readValue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.entities.CatalogTableEntity;
import io.harness.idp.catalog.repositories.CatalogTableRepository;
import io.harness.migration.beans.NGMigration;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogTableMigration implements NGMigration {
  static final String CATALOG_TABLE_MIGRATIONS_FOLDER_PATH = "migrations/catalogTables.json";
  @Inject CatalogTableRepository catalogTableRepository;

  @Override
  public void migrate() {
    log.info("Starting the migration for adding data to catalogTable related collections.");
    String catalogTablesContent = loadResourceFileAsString();
    List<CatalogTableEntity> catalogTables = readValue(catalogTablesContent, CatalogTableEntity.class);
    catalogTableRepository.saveAll(catalogTables);
    log.info("Migration complete for adding data to catalogTable related collections.");
  }

  private String loadResourceFileAsString() {
    try {
      return Resources.toString(Resources.getResource(CATALOG_TABLE_MIGRATIONS_FOLDER_PATH), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("Error in loading resource {} as string. Error = {}", CATALOG_TABLE_MIGRATIONS_FOLDER_PATH,
          e.getMessage(), e);
      throw new UnexpectedException("Error in loading resource " + CATALOG_TABLE_MIGRATIONS_FOLDER_PATH
          + " as string. Error = " + e.getMessage());
    }
  }
}
