/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.gitmetadata.service.ExecutionGitMetadataReconciliationEntityService;
import io.harness.execution.gitmetadata.ExecutionGitMetadataReconciliationEntity;
import io.harness.execution.gitmetadata.beans.ExecutionGitMetadataReconciliationStatus;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class CreateExecutionGitMetadataReconciliationEntityRecord implements NGMigration {
  @Inject ExecutionGitMetadataReconciliationEntityService reconciliationEntityService;

  @Override
  public void migrate() {
    if (reconciliationEntityService.countTotalRecords() > 0) {
      log.info(
          "[GIT_METADATA_RECONCILIATION]: Execution Git Metadata Reconciliation Iterator Entity record already exists so not creating it again");
      return;
    }
    reconciliationEntityService.create(ExecutionGitMetadataReconciliationEntity.builder()
                                           .nextIteration(System.currentTimeMillis())
                                           .syncCompletedUntil(0L)
                                           .syncUntil(System.currentTimeMillis())
                                           .status(ExecutionGitMetadataReconciliationStatus.IN_PROGRESS)
                                           .createdAt(System.currentTimeMillis())
                                           .lastUpdatedAt(System.currentTimeMillis())
                                           .build());
    log.info(
        "[GIT_METADATA_RECONCILIATION]: Created a new Execution Git Metadata Reconciliation Iterator Entity record");
  }
}
