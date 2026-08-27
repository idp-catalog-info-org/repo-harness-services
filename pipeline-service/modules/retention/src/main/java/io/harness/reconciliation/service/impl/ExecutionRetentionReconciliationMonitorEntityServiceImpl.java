/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationMonitorEntity;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationMonitorEntity.ExecutionRetentionReconciliationMonitorEntityKeys;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationMonitorStatus;
import io.harness.reconciliation.service.ExecutionRetentionReconciliationMonitorEntityService;
import io.harness.repositories.reconciliation.ExecutionRetentionReconciliationMonitorEntityRepository;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ExecutionRetentionReconciliationMonitorEntityServiceImpl
    implements ExecutionRetentionReconciliationMonitorEntityService {
  @Inject private ExecutionRetentionReconciliationMonitorEntityRepository monitorEntityRepository;

  @Override
  public ExecutionRetentionReconciliationMonitorEntity save(
      ExecutionRetentionReconciliationMonitorEntity monitorEntity) {
    return monitorEntityRepository.save(monitorEntity);
  }

  @Override
  public ExecutionRetentionReconciliationMonitorEntity updateSyncCompletedUntil(String uuid, Long syncCompletedUntil) {
    Update updateOps = new Update();
    updateOps.set(ExecutionRetentionReconciliationMonitorEntityKeys.syncCompletedUntil, syncCompletedUntil);
    return monitorEntityRepository.update(uuid, updateOps);
  }

  @Override
  public ExecutionRetentionReconciliationMonitorEntity updateNextIteration(String uuid, Long nextIteration) {
    Update updateOps = new Update();
    updateOps.set(ExecutionRetentionReconciliationMonitorEntityKeys.nextIteration, nextIteration);
    return monitorEntityRepository.update(uuid, updateOps);
  }

  @Override
  public ExecutionRetentionReconciliationMonitorEntity updateStatus(
      String uuid, ExecutionRetentionReconciliationMonitorStatus status) {
    Update updateOps = new Update();
    updateOps.set(ExecutionRetentionReconciliationMonitorEntityKeys.status, status);
    return monitorEntityRepository.update(uuid, updateOps);
  }
}
