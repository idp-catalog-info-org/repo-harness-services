/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.InfrastructureMapping;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus.NGManagerUniqueIdParentIdMigrationStatusKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class ResetInfrastructureMappingNgMigrationStatus implements NGMigration {
  @Inject private HPersistence persistence;

  private static final String DEBUG_LOG = "[ResetInfrastructureMappingNgMigrationStatus]: ";

  @Override
  public void migrate() {
    try {
      log.info(
          DEBUG_LOG + "Starting resetting of parentIdMigrationCompleted field for InfrastructureMappingNg to false");
      Query<NGManagerUniqueIdParentIdMigrationStatus> infrastructureMappingNgMigrationStatusQuery =
          persistence.createQuery(NGManagerUniqueIdParentIdMigrationStatus.class)
              .filter(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName, InfrastructureMapping.class);

      persistence.update(infrastructureMappingNgMigrationStatusQuery,
          persistence.createUpdateOperations(NGManagerUniqueIdParentIdMigrationStatus.class)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.parentIdMigrationCompleted, false)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.forceMigrateParentUniqueId, true));
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to reset parentIdMigrationCompleted field for InfrastructureMappingNg", e);
    }
    log.info(DEBUG_LOG
        + ("Successfully reset parentIdMigrationCompleted field for InfrastructureMappingNg to false and "
            + "forceMigrateParentUniqueId to true"));
  }
}
