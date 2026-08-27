/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dataretention;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.mappers.PipelineRetentionApiMapper;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.rest.RestResponse;
import io.harness.retention.PipelineRetentionPeriod;
import io.harness.retention.PipelineRetentionPeriodResponseDTO;
import io.harness.retention.PipelineUpdateRetentionPeriodResponseDTO;
import io.harness.search.entity.beans.PipelineSearchIndexMigration;
import io.harness.search.service.PipelineSearchIndexMigrationService;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PipelineRetentionResourceImpl implements PipelineRetentionResource {
  @Inject private final PipelineRetentionService pipelineRetentionService;
  @Inject private final PipelineSearchIndexMigrationService searchIndexMigrationService;

  @Override
  public RestResponse<Integer> getRetentionPeriodInMonths(@NotNull String accountId) {
    return new RestResponse<>(pipelineRetentionService.getRetentionPeriodInMonths(accountId));
  }

  @Override
  public ResponseDTO<PipelineRetentionPeriodResponseDTO> getRetentionMigrationStatus(String accountIdentifier) {
    PipelineSearchIndexMigration indexMigrationDTO =
        searchIndexMigrationService.findByAccountIdentifier(accountIdentifier);
    if (indexMigrationDTO == null) {
      throw new InvalidRequestException(
          String.format("Currently this account: %s is on default retention period of 6 months", accountIdentifier));
    }
    PipelineRetentionPeriod retentionPeriodDTO =
        pipelineRetentionService.getRetentionPeriod(accountIdentifier, indexMigrationDTO);
    return ResponseDTO.newResponse(PipelineRetentionApiMapper.toResponseDTO(retentionPeriodDTO));
  }

  @Override
  public ResponseDTO<PipelineUpdateRetentionPeriodResponseDTO> updateRetentionPeriod(
      @NotNull String accountIdentifier, @NotNull DataRetentionPeriod dataRetentionPeriod) {
    int retentionPeriodInMonths = pipelineRetentionService.getRetentionPeriodInMonths(accountIdentifier);
    if (dataRetentionPeriod.getDataRetentionPeriodInMonths() == retentionPeriodInMonths) {
      throw new InternalServerErrorException(String.format(
          "Provided account: %s, is already on %s month retention plan", accountIdentifier, retentionPeriodInMonths));
    }
    PipelineSearchIndexMigration indexMigrationDTO =
        searchIndexMigrationService.updateRetentionPeriod(accountIdentifier, dataRetentionPeriod);
    if (indexMigrationDTO == null) {
      throw new InternalServerErrorException(
          String.format("Error while creating index migration entity for account: %s", accountIdentifier));
    }
    PipelineRetentionPeriod retentionPeriodDTO =
        pipelineRetentionService.updateRetentionPeriod(accountIdentifier, dataRetentionPeriod, indexMigrationDTO);
    return ResponseDTO.newResponse(PipelineRetentionApiMapper.toUpdateResponseDTO(retentionPeriodDTO));
  }
}
