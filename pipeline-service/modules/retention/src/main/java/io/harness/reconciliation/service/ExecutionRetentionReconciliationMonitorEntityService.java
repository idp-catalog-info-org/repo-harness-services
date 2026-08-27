/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.reconciliation.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationMonitorEntity;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationMonitorStatus;

/*
 * This service is used to do MongoDB operations for the ExecutionRetentionReconciliationMonitorEntity
 * Like save/update records in DB
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(HarnessTeam.PIPELINE)
public interface ExecutionRetentionReconciliationMonitorEntityService {
  /**
   * Saves the execution retention reconciliation monitor entity in harness pms db
   * @param monitorEntity monitor entity to save
   * @return ExecutionRetentionReconciliationMonitorEntity the saved entity
   */
  ExecutionRetentionReconciliationMonitorEntity save(ExecutionRetentionReconciliationMonitorEntity monitorEntity);

  /**
   * Updates the sync completed until in the execution retention reconciliation monitor entity
   * @param uuid uuid of the reconciliation entity to update
   * @param syncCompletedUntil value to be updated
   * @return ExecutionRetentionReconciliationMonitorEntity the updated entity
   */
  ExecutionRetentionReconciliationMonitorEntity updateSyncCompletedUntil(String uuid, Long syncCompletedUntil);

  /**
   * Updates the next iteration time in the execution retention reconciliation monitor entity
   * @param uuid uuid of the iterator entity to update
   * @param nextIteration value to be updated
   * @return ExecutionRetentionReconciliationMonitorEntity the updated entity
   */
  ExecutionRetentionReconciliationMonitorEntity updateNextIteration(String uuid, Long nextIteration);

  ExecutionRetentionReconciliationMonitorEntity updateStatus(
      String uuid, ExecutionRetentionReconciliationMonitorStatus status);
}
