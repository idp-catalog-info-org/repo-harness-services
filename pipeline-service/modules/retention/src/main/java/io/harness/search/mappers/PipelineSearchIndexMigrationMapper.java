/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity;
import io.harness.search.entity.beans.PipelineSearchIndexMigration;

import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(PIPELINE)
@UtilityClass
public class PipelineSearchIndexMigrationMapper {
  public PipelineSearchIndexMigration toDTO(PipelineSearchIndexMigrationEntity searchIndexMigrationEntity) {
    if (searchIndexMigrationEntity == null) {
      return null;
    }
    return PipelineSearchIndexMigration.builder()
        .accountIdentifier(searchIndexMigrationEntity.getAccountIdentifier())
        .createdAt(searchIndexMigrationEntity.getCreatedAt())
        .uuid(searchIndexMigrationEntity.getUuid())
        .lastUpdatedAt(searchIndexMigrationEntity.getLastUpdatedAt())
        .elasticTaskID(searchIndexMigrationEntity.getElasticTaskID())
        .elasticBufferSyncTaskID(searchIndexMigrationEntity.getElasticBufferSyncTaskID())
        .status(searchIndexMigrationEntity.getStatus())
        .oldIndexRetentionPeriod(searchIndexMigrationEntity.getOldIndexRetentionPeriod())
        .newIndexRetentionPeriod(searchIndexMigrationEntity.getNewIndexRetentionPeriod())
        .migrationStartTime(searchIndexMigrationEntity.getMigrationStartTime())
        .migrationEndTime(searchIndexMigrationEntity.getMigrationEndTime())
        .build();
  }
}
