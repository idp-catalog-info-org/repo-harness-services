/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.retention;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
@Hidden
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PipelineRetentionPeriod", description = "This contains information on the pipeline retention period")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
public class PipelineRetentionPeriod {
  @NotNull DataRetentionPeriod dataRetentionPeriod;
  @NotNull PipelineSearchMigrationStatus indexMigrationStatus;
  @NotNull PipelineSearchIndexRetentionPeriods oldIndexRetentionPeriod;
  @NotNull PipelineSearchIndexRetentionPeriods newIndexRetentionPeriod;
}
