/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;
import io.harness.persistence.HPersistence;
import io.harness.projectmovement.mongo.PipelineUniqueIdParentIdMigrationStatus;
import io.harness.projectmovement.mongo.PipelineUniqueIdParentIdMigrationStatus.PipelineUniqueIdParentIdMigrationStatusKeys;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class ResetProjectMovementMigrationStatus implements NGMigration {
  @Inject private HPersistence persistence;

  private static final String DEBUG_LOG = "[ResetProjectMovementMigrationStatus-Pipeline]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting reset of all migration status fields to false");

      Query<PipelineUniqueIdParentIdMigrationStatus> migrationStatusQuery =
          persistence.createQuery(PipelineUniqueIdParentIdMigrationStatus.class);

      // Reset all three fields to false
      persistence.update(migrationStatusQuery,
          persistence.createUpdateOperations(PipelineUniqueIdParentIdMigrationStatus.class)
              .set(PipelineUniqueIdParentIdMigrationStatusKeys.uniqueIdMigrationCompleted, false)
              .set(PipelineUniqueIdParentIdMigrationStatusKeys.parentIdMigrationCompleted, false)
              .set(PipelineUniqueIdParentIdMigrationStatusKeys.orphanEntityParentIdMigrationCompleted, false));

      log.info(DEBUG_LOG + "Successfully reset all migration status fields to false");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to reset migration status fields", e);
    }
  }
}
