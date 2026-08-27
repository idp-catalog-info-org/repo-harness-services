/*
 * Copyright 2024 Harness Inc. All rights reserved.
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
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.migration.beans.NGMigration;
import io.harness.migration.ng.ElasticSearchNotAvailableException;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationDB;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationStatus;
import io.harness.reconciliation.service.ExecutionRetentionReconciliationEntityService;
import io.harness.search.service.PipelineSearchService;

import com.google.inject.Inject;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineExecutionRetentionCreateReconciliationIteratorRecord implements NGMigration {
  @Inject ExecutionRetentionReconciliationEntityService reconciliationEntityService;
  @Inject PipelineSearchService pipelineSearchService;
  @Nullable @Inject private ElasticSearchClient elasticSearchClient;

  @Override
  public void migrate() {
    if (reconciliationEntityService.countTotalRecords() > 2) {
      log.info(
          "[RETENTION_RECONCILIATION]: Execution Retention Reconciliation Entity record already exists so not creating it again");
      return;
    }
    if (elasticSearchClient == null) {
      throw new ElasticSearchNotAvailableException(
          String.format("[Migration]: Migration %s failed - ELASTICSEARCHDB NOT AVAILABLE", getClass()));
    }
    Optional<Long> endTs = pipelineSearchService.fetchFirstExecutionEndTs();
    Long syncUntil = System.currentTimeMillis();
    if (endTs.isPresent()) {
      syncUntil = endTs.get();
    }
    reconciliationEntityService.save(ExecutionRetentionReconciliationEntity.builder()
                                         .nextIteration(0L)
                                         .syncCompletedUntil(0L)
                                         .syncUntil(syncUntil)
                                         .status(ExecutionRetentionReconciliationStatus.IN_PROGRESS)
                                         .reconciliationDB(ExecutionRetentionReconciliationDB.ELASTIC)
                                         .build());
    reconciliationEntityService.save(ExecutionRetentionReconciliationEntity.builder()
                                         .nextIteration(0L)
                                         .syncCompletedUntil(0L)
                                         .status(ExecutionRetentionReconciliationStatus.IN_PROGRESS)
                                         .reconciliationDB(ExecutionRetentionReconciliationDB.OBJECT_STORE)
                                         .build());
    log.info("[RETENTION_RECONCILIATION]: Created a new Execution Retention Reconciliation Entity record");
  }
}
