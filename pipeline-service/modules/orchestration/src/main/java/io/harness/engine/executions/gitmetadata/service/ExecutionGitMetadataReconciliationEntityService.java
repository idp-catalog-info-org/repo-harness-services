/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.gitmetadata.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.execution.gitmetadata.ExecutionGitMetadataReconciliationEntity;
import io.harness.execution.gitmetadata.beans.ExecutionGitMetadataReconciliationStatus;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface ExecutionGitMetadataReconciliationEntityService {
  /**
   * Creates a new ExecutionGitMetadataReconciliationEntity
   *
   * @param reconciliationEntity the entity to create
   * @return the created entity
   */
  ExecutionGitMetadataReconciliationEntity create(ExecutionGitMetadataReconciliationEntity reconciliationEntity);

  /**
   * Updates the sync completed until timestamp for a ExecutionGitMetadataReconciliationEntity
   *
   * @param uuid                the UUID of the entity to update
   * @param syncCompletedUntil  the new sync completed until timestamp
   * @return the updated entity
   */
  ExecutionGitMetadataReconciliationEntity updateSyncCompletedUntil(String uuid, Long syncCompletedUntil);

  /**
   * Updates the next iteration timestamp for a ExecutionGitMetadataReconciliationEntity
   *
   * @param uuid           the UUID of the entity to update
   * @param nextIteration  the new next iteration timestamp
   * @return the updated entity
   */
  ExecutionGitMetadataReconciliationEntity updateNextIteration(String uuid, Long nextIteration);

  /**
   * Updates the status of a ExecutionGitMetadataReconciliationEntity
   *
   * @param uuid    the UUID of the entity to update
   * @param status  the new status
   * @return the updated entity
   */
  ExecutionGitMetadataReconciliationEntity updateStatus(String uuid, ExecutionGitMetadataReconciliationStatus status);

  /**
   * Gets the total count of ExecutionGitMetadataReconciliationEntity records
   *
   * @return the count of records
   */
  long countTotalRecords();
}
