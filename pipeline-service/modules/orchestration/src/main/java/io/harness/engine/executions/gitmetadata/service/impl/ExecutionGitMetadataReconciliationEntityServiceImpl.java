/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.gitmetadata.service.impl;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.gitmetadata.service.ExecutionGitMetadataReconciliationEntityService;
import io.harness.execution.gitmetadata.ExecutionGitMetadataReconciliationEntity;
import io.harness.execution.gitmetadata.ExecutionGitMetadataReconciliationEntity.ExecutionGitMetadataReconciliationEntityKeys;
import io.harness.execution.gitmetadata.beans.ExecutionGitMetadataReconciliationStatus;
import io.harness.repositories.executiongitmetadata.ExecutionGitMetadataReconciliationEntityRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class ExecutionGitMetadataReconciliationEntityServiceImpl
    implements ExecutionGitMetadataReconciliationEntityService {
  @Inject private ExecutionGitMetadataReconciliationEntityRepository reconciliationEntityRepository;

  @Override
  public ExecutionGitMetadataReconciliationEntity create(
      ExecutionGitMetadataReconciliationEntity reconciliationEntity) {
    return reconciliationEntityRepository.save(reconciliationEntity);
  }

  @Override
  public ExecutionGitMetadataReconciliationEntity updateSyncCompletedUntil(String uuid, Long syncCompletedUntil) {
    Update updateOps = new Update()
                           .set(ExecutionGitMetadataReconciliationEntityKeys.syncCompletedUntil, syncCompletedUntil)
                           .set(ExecutionGitMetadataReconciliationEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    return reconciliationEntityRepository.update(uuid, updateOps);
  }

  @Override
  public ExecutionGitMetadataReconciliationEntity updateNextIteration(String uuid, Long nextIteration) {
    Update updateOps = new Update()
                           .set(ExecutionGitMetadataReconciliationEntityKeys.nextIteration, nextIteration)
                           .set(ExecutionGitMetadataReconciliationEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    return reconciliationEntityRepository.update(uuid, updateOps);
  }

  @Override
  public ExecutionGitMetadataReconciliationEntity updateStatus(
      String uuid, ExecutionGitMetadataReconciliationStatus status) {
    Update updateOps = new Update()
                           .set(ExecutionGitMetadataReconciliationEntityKeys.status, status)
                           .set(ExecutionGitMetadataReconciliationEntityKeys.lastUpdatedAt, System.currentTimeMillis());
    return reconciliationEntityRepository.update(uuid, updateOps);
  }

  @Override
  public long countTotalRecords() {
    return reconciliationEntityRepository.count();
  }
}
