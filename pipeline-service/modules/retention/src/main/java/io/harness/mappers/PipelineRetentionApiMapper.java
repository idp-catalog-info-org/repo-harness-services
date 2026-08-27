/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.retention.PipelineRetentionPeriod;
import io.harness.retention.PipelineRetentionPeriodResponseDTO;
import io.harness.retention.PipelineUpdateRetentionPeriodResponseDTO;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(PIPELINE)
@UtilityClass
public class PipelineRetentionApiMapper {
  public PipelineRetentionPeriodResponseDTO toResponseDTO(PipelineRetentionPeriod retentionPeriodDTO) {
    if (retentionPeriodDTO == null) {
      return null;
    }
    return PipelineRetentionPeriodResponseDTO.builder()
        .dataRetentionPeriod(retentionPeriodDTO.getDataRetentionPeriod())
        .newIndexRetentionPeriod(retentionPeriodDTO.getNewIndexRetentionPeriod())
        .indexMigrationStatus(retentionPeriodDTO.getIndexMigrationStatus())
        .oldIndexRetentionPeriod(retentionPeriodDTO.getOldIndexRetentionPeriod())
        .build();
  }

  public PipelineUpdateRetentionPeriodResponseDTO toUpdateResponseDTO(PipelineRetentionPeriod retentionPeriodDTO) {
    if (retentionPeriodDTO == null) {
      return null;
    }
    return PipelineUpdateRetentionPeriodResponseDTO.builder()
        .dataRetentionPeriod(retentionPeriodDTO.getDataRetentionPeriod())
        .newIndexRetentionPeriod(retentionPeriodDTO.getNewIndexRetentionPeriod())
        .indexMigrationStatus(retentionPeriodDTO.getIndexMigrationStatus())
        .oldIndexRetentionPeriod(retentionPeriodDTO.getOldIndexRetentionPeriod())
        .build();
  }
}
