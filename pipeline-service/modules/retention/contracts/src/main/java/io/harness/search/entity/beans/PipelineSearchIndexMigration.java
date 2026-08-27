/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.search.entity.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(PIPELINE)
@Value
@Hidden
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PipelineSearchIndexMigration", description = "This contains information on the search index migration")
public class PipelineSearchIndexMigration {
  String uuid;
  String accountIdentifier;
  Long createdAt;
  Long lastUpdatedAt;
  // This field stores the elastic task id to reindex the records from old to new index
  String elasticTaskID;
  // This field stores the elastic task id to sync the records which were inserted b/w migration start time +- 5 minutes
  String elasticBufferSyncTaskID;
  PipelineSearchMigrationStatus status;
  PipelineSearchIndexRetentionPeriods oldIndexRetentionPeriod;
  PipelineSearchIndexRetentionPeriods newIndexRetentionPeriod;
  Long migrationStartTime;
  Long migrationEndTime;
}
