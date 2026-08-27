/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.reconciliation.service.impl;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity.ExecutionRetentionReconciliationEntityKeys;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationStatus;
import io.harness.reconciliation.service.ExecutionRetentionReconciliationEntityService;
import io.harness.repositories.reconciliation.ExecutionRetentionReconciliationEntityRepository;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ExecutionRetentionReconciliationEntityServiceImpl
    implements ExecutionRetentionReconciliationEntityService {
  @Inject private ExecutionRetentionReconciliationEntityRepository reconciliationEntityRepository;

  @Override
  public ExecutionRetentionReconciliationEntity save(ExecutionRetentionReconciliationEntity dataMigrationEntity) {
    return reconciliationEntityRepository.save(dataMigrationEntity);
  }

  @Override
  public ExecutionRetentionReconciliationEntity updateSyncCompletedUntil(String uuid, Long syncCompletedUntil) {
    Update updateOps = new Update();
    updateOps.set(ExecutionRetentionReconciliationEntityKeys.syncCompletedUntil, syncCompletedUntil);
    return reconciliationEntityRepository.update(uuid, updateOps);
  }

  @Override
  public ExecutionRetentionReconciliationEntity updateNextIteration(String uuid, Long nextIteration) {
    Update updateOps = new Update();
    updateOps.set(ExecutionRetentionReconciliationEntityKeys.nextIteration, nextIteration);
    return reconciliationEntityRepository.update(uuid, updateOps);
  }

  @Override
  public ExecutionRetentionReconciliationEntity updateStatus(
      String uuid, ExecutionRetentionReconciliationStatus status) {
    Update updateOps = new Update();
    updateOps.set(ExecutionRetentionReconciliationEntityKeys.status, status);
    return reconciliationEntityRepository.update(uuid, updateOps);
  }

  @Override
  public long countTotalRecords() {
    return reconciliationEntityRepository.count();
  }
}
