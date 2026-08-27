/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity;
import io.harness.search.entity.beans.PipelineSearchIndexMigration;

import org.springframework.data.mongodb.core.query.Update;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(HarnessTeam.PIPELINE)
public interface PipelineSearchIndexMigrationService {
  /**
   * Saves the index migration iterator entity in harness pms db
   * @param searchIndexMigrationEntity migration entity to save
   * @return PipelineSearchIndexMigrationEntity the saved entity
   */
  PipelineSearchIndexMigrationEntity save(PipelineSearchIndexMigrationEntity searchIndexMigrationEntity);

  /**
   * Updates the index migration iterator entity in harness pms db
   * @param uuid migration entity uuid to update
   * @param updateOps update operations
   * @return PipelineSearchIndexMigrationEntity the updated entity
   */
  PipelineSearchIndexMigrationEntity update(String uuid, Update updateOps);

  PipelineSearchIndexMigration findByAccountIdentifier(String accountIdentifier);
  PipelineSearchIndexMigration updateRetentionPeriod(String accountIdentifier, DataRetentionPeriod dataRetentionPeriod);
}
