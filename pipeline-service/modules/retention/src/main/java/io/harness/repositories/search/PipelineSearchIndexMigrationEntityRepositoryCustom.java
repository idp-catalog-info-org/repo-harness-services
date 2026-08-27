/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.search;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity;

import org.springframework.data.mongodb.core.query.Update;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(PIPELINE)
public interface PipelineSearchIndexMigrationEntityRepositoryCustom {
  /**
   * Updates the PipelineSearchIndexMigrationEntity by uuid
   * @param uuid of the document
   * @param updateOps updates to apply
   * @return PipelineSearchIndexMigrationEntity updated entity
   */
  PipelineSearchIndexMigrationEntity update(String uuid, Update updateOps);

  PipelineSearchIndexMigrationEntity findByAccountIdentifier(String accountId);
}
