/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.entities.migration.NGManagerCDCEntitiesMigrationStatus;
import io.harness.ng.core.entities.migration.NGManagerCDCEntitiesMigrationStatus.NGManagerUniqueIdParentIdMigrationStatusKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateResults;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class RerunParentUniqueIdMigrationForStageExecutionInTSDBViaCDC implements NGMigration {
  @Inject private HPersistence persistence;

  private static final String DEBUG_LOG = "[RerunParentUniqueIdMigrationForStageExecutionInTSDBViaCDC]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG
          + ("Starting resetting of parentIdMigrationCompleted field for StageExecutionInfo in "
              + "cdcEntitiesMigrationStatus to false"));
      Query<NGManagerCDCEntitiesMigrationStatus> stageExecutionInfoRerunMigrationQuery =
          persistence.createQuery(NGManagerCDCEntitiesMigrationStatus.class)
              .filter(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName,
                  StageExecutionInfo.class.getSimpleName());

      UpdateResults updateResults = persistence.update(stageExecutionInfoRerunMigrationQuery,
          persistence.createUpdateOperations(NGManagerCDCEntitiesMigrationStatus.class)
              .set(NGManagerUniqueIdParentIdMigrationStatusKeys.cdcMigrationCompleted, false));
      log.info(DEBUG_LOG + "Updated Documents count {}", updateResults.getUpdatedCount());
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Failed to reset cdcMigrationCompleted field for StageExecutionInfo", e);
    }
    log.info(DEBUG_LOG + ("Successfully reset cdcMigrationCompleted field for StageExecutionInfo to false"));
  }
}
