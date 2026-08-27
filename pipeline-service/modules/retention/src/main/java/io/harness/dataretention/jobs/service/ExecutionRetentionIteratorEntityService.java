/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention.jobs.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import java.time.Duration;
import java.util.Set;

/*
 * This service is used to do MongoDB operations for the ExecutionRetentionIteratorEntity
 * Like save/update records in DB
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
public interface ExecutionRetentionIteratorEntityService {
  /**
   * Syncs the 4 collections to object store
   * @param accountIdentifier accountIdentifier
   * @param planExecutionId planExecutionId for which to sync records
   * @param endTs endTs of the execution
   * @param status status of the execution
   */
  void syncToObjectStore(String accountIdentifier, String planExecutionId, Long endTs, Status status);

  /**
   * Syncs the 4 collections to object store, this fetches the 3 collections from db and expects the summary entity
   * to be passed as full, so we have to ensure it's the full record
   * Also returns the duration for which it ran
   */
  Duration syncRecordsToObjectStore(Set<String> planExecutionIDs);

  void syncSummaryEntityToObjectStore(
      PipelineExecutionSummaryEntity summaryEntity, ExecutionRetentionMetadata metadata);
}
