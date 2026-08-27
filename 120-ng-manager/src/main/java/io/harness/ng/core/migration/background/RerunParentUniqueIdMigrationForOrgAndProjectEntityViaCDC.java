/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.migration.NGManagerCDCEntitiesMigrationStatus;
import io.harness.ng.core.entities.migration.NGManagerCDCEntitiesMigrationStatus.NGManagerUniqueIdParentIdMigrationStatusKeys;
import io.harness.ng.core.entities.migration.NgManagerTsdbUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.NgManagerTsdbUniqueIdParentIdMigrationStatus.NgManagerTsdbUniqueIdParentIdMigrationStatusKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateResults;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class RerunParentUniqueIdMigrationForOrgAndProjectEntityViaCDC implements NGMigration {
  @Inject private HPersistence persistence;

  private static final String DEBUG_LOG = "[RerunParentUniqueIdMigrationForOrgAndProjectEntityViaCDC]: ";

  @Override
  public void migrate() {
    boolean skipThisMigration = Boolean.parseBoolean(System.getenv("SKIP_TSDB_RERUN_MIGRATION_FOR_PROJECT_MOVEMENT"));
    if (skipThisMigration) {
      log.info(DEBUG_LOG + "Skipping this migration to");
      return;
    }
    log.info(DEBUG_LOG
        + "Starting resetting of cdcMigrationCompleted field for Org and Project in cdcEntitiesMigrationStatus");

    // ----- 1. Reset for Organization -----
    try {
      log.info(DEBUG_LOG + "Starting reset for Organization");

      Query<NGManagerCDCEntitiesMigrationStatus> orgQuery =
          persistence.createQuery(NGManagerCDCEntitiesMigrationStatus.class)
              .filter(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName, Organization.class.getSimpleName());

      UpdateResults orgUpdateResults = persistence.update(orgQuery,
          persistence.createUpdateOperations(NGManagerCDCEntitiesMigrationStatus.class)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.cdcMigrationCompleted, false));

      log.info(
          DEBUG_LOG + "Organization reset complete. Updated documents count: {}", orgUpdateResults.getUpdatedCount());
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to reset cdcMigrationCompleted field for Organization", e);
    }

    // ----- 2. Reset for Project -----
    try {
      log.info(DEBUG_LOG + "Starting reset for Project");

      Query<NGManagerCDCEntitiesMigrationStatus> projectQuery =
          persistence.createQuery(NGManagerCDCEntitiesMigrationStatus.class)
              .filter(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName, Project.class.getSimpleName());

      UpdateResults projectUpdateResults = persistence.update(projectQuery,
          persistence.createUpdateOperations(NGManagerCDCEntitiesMigrationStatus.class)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.cdcMigrationCompleted, false));

      log.info(
          DEBUG_LOG + "Project reset complete. Updated documents count: {}", projectUpdateResults.getUpdatedCount());
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to reset cdcMigrationCompleted field for Project", e);
    }

    // ----- 3. Reset tsdbMigrationsStatus for Project -----
    try {
      log.info(DEBUG_LOG + "Starting reset for tsdb migration status for Project");

      Query<NgManagerTsdbUniqueIdParentIdMigrationStatus> projectQuery =
          persistence.createQuery(NgManagerTsdbUniqueIdParentIdMigrationStatus.class)
              .filter(NgManagerTsdbUniqueIdParentIdMigrationStatusKeys.entityClassName, "projects");

      UpdateResults projectUpdateResults = persistence.update(projectQuery,
          persistence.createUpdateOperations(NgManagerTsdbUniqueIdParentIdMigrationStatus.class)
              .set(NgManagerTsdbUniqueIdParentIdMigrationStatusKeys.migrationCompleted, false));

      log.info(DEBUG_LOG + "Projects tsdb status reset complete. Updated documents count: {}",
          projectUpdateResults.getUpdatedCount());
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to reset tsdbMigrationStatus field for Project", e);
    }

    log.info(DEBUG_LOG
        + ("Successfully completed resetting of cdcMigrationCompleted field for Organization and Project and setting "
            + "tsdbMigrationStatus field for Project"));
  }
}