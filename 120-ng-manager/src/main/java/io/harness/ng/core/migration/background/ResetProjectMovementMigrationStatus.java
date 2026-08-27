/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus.NGManagerUniqueIdParentIdMigrationStatusKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class ResetProjectMovementMigrationStatus implements NGMigration {
  @Inject private HPersistence persistence;

  private static final String DEBUG_LOG = "[ResetProjectMovementMigrationStatus]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting reset of all migration status fields to false");

      Query<NGManagerUniqueIdParentIdMigrationStatus> migrationStatusQuery =
          persistence.createQuery(NGManagerUniqueIdParentIdMigrationStatus.class);

      // Reset all three fields to false
      persistence.update(migrationStatusQuery,
          persistence.createUpdateOperations(NGManagerUniqueIdParentIdMigrationStatus.class)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.uniqueIdMigrationCompleted, false)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.parentIdMigrationCompleted, false)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.orphanEntityParentIdMigrationCompleted, false));

      log.info(DEBUG_LOG + "Successfully reset all migration status fields to false");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to reset migration status fields", e);
    }
  }
}
