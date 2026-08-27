/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.ci.beans.entities.PipelineModuleInfoEntity;
import io.harness.ci.beans.entities.StageModuleInfoEntity;

/**
 * Custom repository interface for CommonPipelineModuleInfoEntity to provide atomic update operations.
 */
public interface PipelineModuleInfoRepositoryCustom {
  /**
   * Add a stage module info to a pipeline module info entity, using atomic MongoDB operations.
   * This method handles concurrency by using atomic updates and upsert capabilities.
   *
   * @param accountId The account identifier
   * @param orgId The organization identifier
   * @param projectId The project identifier
   * @param pipelineId The pipeline identifier
   * @param planExecutionId The plan execution identifier
   * @param stageInfo The stage module info to add
   * @return The updated CommonPipelineModuleInfoEntity
   */
  PipelineModuleInfoEntity addStageModuleInfo(String accountId, String orgId, String projectId, String pipelineId,
      String planExecutionId, String parentUniqueId, StageModuleInfoEntity stageInfo);
}
