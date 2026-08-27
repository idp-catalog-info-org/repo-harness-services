/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.PipelineModuleInfoEntity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Repository for CIPipelineModuleInfoEntity operations.
 */
@HarnessRepo
@OwnedBy(HarnessTeam.CI)
public interface PipelineModuleInfoRepository
    extends CrudRepository<PipelineModuleInfoEntity, String>, PipelineModuleInfoRepositoryCustom {
  /**
   * Find a CIPipelineModuleInfoEntity by its plan execution ID.
   */
  Optional<PipelineModuleInfoEntity> findByPlanExecutionId(String planExecutionId);

  /**
   * Find all entities by accountIdentifier.
   */
  List<PipelineModuleInfoEntity> findByAccountIdentifier(String accountIdentifier);
}
