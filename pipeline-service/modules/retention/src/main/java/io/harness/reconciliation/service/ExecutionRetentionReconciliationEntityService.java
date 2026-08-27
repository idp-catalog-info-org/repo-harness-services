/*
 * Copyright 2024 Harness Inc. All rights reserved.
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
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationStatus;

/*
 * This service is used to do MongoDB operations for the ExecutionRetentionReconciliationEntity
 * Like save/update records in DB
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(HarnessTeam.PIPELINE)
public interface ExecutionRetentionReconciliationEntityService {
  /**
   * Saves the execution retention reconciliation entity in harness pms db
   * @param reconciliationEntity reconciliation entity to save
   * @return ExecutionRetentionReconciliationEntity the saved entity
   */
  ExecutionRetentionReconciliationEntity save(ExecutionRetentionReconciliationEntity reconciliationEntity);

  /**
   * Updates the sync completed until in the execution retention reconciliation entity
   * @param uuid uuid of the reconciliation entity to update
   * @param syncCompletedUntil value to be updated
   * @return ExecutionRetentionReconciliationEntity the updated entity
   */
  ExecutionRetentionReconciliationEntity updateSyncCompletedUntil(String uuid, Long syncCompletedUntil);

  /**
   * Updates the next iteration time in the execution retention reconciliation entity
   * @param uuid uuid of the iterator entity to update
   * @param nextIteration value to be updated
   * @return ExecutionRetentionReconciliationEntity the updated entity
   */
  ExecutionRetentionReconciliationEntity updateNextIteration(String uuid, Long nextIteration);

  ExecutionRetentionReconciliationEntity updateStatus(String uuid, ExecutionRetentionReconciliationStatus status);

  /**
   * Counts total no. of records present in the collection
   */
  long countTotalRecords();
}
