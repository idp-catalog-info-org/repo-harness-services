/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executiongitmetadata;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.execution.gitmetadata.ExecutionGitMetadataReconciliationEntity;

import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface ExecutionGitMetadataReconciliationEntityRepositoryCustom {
  /**
   * Updates a GitMetadataReconciliationEntity by its UUID
   *
   * @param uuid     the UUID of the entity to update
   * @param updateOp the update operations to apply
   * @return the updated entity
   */
  ExecutionGitMetadataReconciliationEntity update(String uuid, Update updateOp);
}
