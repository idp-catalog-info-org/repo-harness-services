/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dataretention.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.entity.accountoverrides.beans.AccountOverridesConfigDTO;
import io.harness.entity.accountoverrides.beans.ExportSettingsDTO;
import io.harness.pms.accountoverrides.AccountOverridesCreateRequestDTO;
import io.harness.pms.accountoverrides.AccountOverridesCreateResponseDTO;
import io.harness.pms.accountoverrides.AccountOverridesCreateResponseDTO.AccountOverridesCreateResponseDTOBuilder;
import io.harness.pms.accountoverrides.AccountOverridesUpdateRequestDTO;
import io.harness.pms.accountoverrides.AccountOverridesUpdateResponseDTO;
import io.harness.pms.accountoverrides.AccountOverridesUpdateResponseDTO.AccountOverridesUpdateResponseDTOBuilder;
import io.harness.pms.accountoverrides.DataRetentionSettingsCreateResponseDTO;
import io.harness.pms.accountoverrides.DataRetentionSettingsUpdateResponseDTO;
import io.harness.pms.accountoverrides.ExportSettingsCreateResponseDTO;
import io.harness.pms.accountoverrides.ExportSettingsUpdateResponseDTO;
import io.harness.pms.accountoverrides.LogStreamingLimitsDTO;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class AccountOverridesApiMapper {
  public AccountOverridesConfigDTO toDTO(String accountIdentifier, AccountOverridesCreateRequestDTO createRequestDTO) {
    AccountOverridesConfigDTO configDTO =
        AccountOverridesConfigDTO.builder().accountIdentifier(accountIdentifier).build();
    if (createRequestDTO.getMaxConcurrentExecutions() != null) {
      configDTO.setMaxConcurrentExecutions(createRequestDTO.getMaxConcurrentExecutions());
    }
    if (createRequestDTO.getMaxInputParameterSize() != null) {
      configDTO.setMaxInputParameterSize(createRequestDTO.getMaxInputParameterSize());
    }
    if (createRequestDTO.getMaxOutcomeResponseSize() != null) {
      configDTO.setMaxOutcomeResponseSize(createRequestDTO.getMaxOutcomeResponseSize());
    }
    if (createRequestDTO.getMaxTriggerCreationLimit() != null) {
      configDTO.setMaxTriggerCreationLimit(createRequestDTO.getMaxTriggerCreationLimit());
    }
    if (createRequestDTO.getMaxQueuedExecutionLimit() != null) {
      configDTO.setMaxQueuedExecutionLimit(createRequestDTO.getMaxQueuedExecutionLimit());
    }
    if (createRequestDTO.getMaxLeafStepConcurrency() != null) {
      configDTO.setMaxLeafStepConcurrency(createRequestDTO.getMaxLeafStepConcurrency());
    }
    if (createRequestDTO.getMaxExpressionCalls() != null) {
      configDTO.setMaxExpressionCalls(createRequestDTO.getMaxExpressionCalls());
    }
    if (createRequestDTO.getExportSettings() != null
        && createRequestDTO.getExportSettings().getMaxExportRequestsPerDay() != null) {
      configDTO.setExportSettings(
          ExportSettingsDTO.builder()
              .maxExportRequestsPerDay(createRequestDTO.getExportSettings().getMaxExportRequestsPerDay())
              .build());
    }
    if (createRequestDTO.getLogStreamingLimits() != null) {
      configDTO.setLogStreamingLimits(
          LogStreamingLimitsDTO.builder()
              .maxLogLines(createRequestDTO.getLogStreamingLimits().getMaxLogLines())
              .maxLogLineLength(createRequestDTO.getLogStreamingLimits().getMaxLogLineLength())
              .streamExpirationSeconds(createRequestDTO.getLogStreamingLimits().getStreamExpirationSeconds())
              .maxLogSizeBytes(createRequestDTO.getLogStreamingLimits().getMaxLogSizeBytes())
              .maxWriteLogLinesPerMinute(createRequestDTO.getLogStreamingLimits().getMaxWriteLogLinesPerMinute())
              .build());
    }
    return configDTO;
  }

  public AccountOverridesConfigDTO toDTO(String accountIdentifier, AccountOverridesUpdateRequestDTO updateRequestDTO) {
    AccountOverridesConfigDTO configDTO =
        AccountOverridesConfigDTO.builder().accountIdentifier(accountIdentifier).build();
    if (updateRequestDTO.getMaxConcurrentExecutions() != null) {
      configDTO.setMaxConcurrentExecutions(updateRequestDTO.getMaxConcurrentExecutions());
    }
    if (updateRequestDTO.getMaxInputParameterSize() != null) {
      configDTO.setMaxInputParameterSize(updateRequestDTO.getMaxInputParameterSize());
    }
    if (updateRequestDTO.getMaxOutcomeResponseSize() != null) {
      configDTO.setMaxOutcomeResponseSize(updateRequestDTO.getMaxOutcomeResponseSize());
    }
    if (updateRequestDTO.getMaxLeafStepConcurrency() != null) {
      configDTO.setMaxLeafStepConcurrency(updateRequestDTO.getMaxLeafStepConcurrency());
    }
    if (updateRequestDTO.getMaxExpressionCalls() != null) {
      configDTO.setMaxExpressionCalls(updateRequestDTO.getMaxExpressionCalls());
    }
    if (updateRequestDTO.getExportSettings() != null
        && updateRequestDTO.getExportSettings().getMaxExportRequestsPerDay() != null) {
      configDTO.setExportSettings(
          ExportSettingsDTO.builder()
              .maxExportRequestsPerDay(updateRequestDTO.getExportSettings().getMaxExportRequestsPerDay())
              .build());
    }
    if (updateRequestDTO.getLogStreamingLimits() != null) {
      configDTO.setLogStreamingLimits(
          LogStreamingLimitsDTO.builder()
              .maxLogLines(updateRequestDTO.getLogStreamingLimits().getMaxLogLines())
              .maxLogLineLength(updateRequestDTO.getLogStreamingLimits().getMaxLogLineLength())
              .streamExpirationSeconds(updateRequestDTO.getLogStreamingLimits().getStreamExpirationSeconds())
              .maxLogSizeBytes(updateRequestDTO.getLogStreamingLimits().getMaxLogSizeBytes())
              .maxWriteLogLinesPerMinute(updateRequestDTO.getLogStreamingLimits().getMaxWriteLogLinesPerMinute())
              .build());
    }
    return configDTO;
  }

  public AccountOverridesCreateResponseDTO toCreateResponseDTO(AccountOverridesConfigDTO configDTO) {
    AccountOverridesCreateResponseDTOBuilder responseDTO =
        AccountOverridesCreateResponseDTO.builder()
            .accountIdentifier(configDTO.getAccountIdentifier())
            .retentionPeriodInMonths(configDTO.getRetentionPeriodInMonths())
            .maxConcurrentExecutions(configDTO.getMaxConcurrentExecutions())
            .maxOutcomeResponseSize(configDTO.getMaxOutcomeResponseSize())
            .maxTriggerCreationLimit(configDTO.getMaxTriggerCreationLimit())
            .maxQueuedExecutionLimit(configDTO.getMaxQueuedExecutionLimit())
            .maxLeafStepConcurrency(configDTO.getMaxLeafStepConcurrency())
            .maxInputParameterSize(configDTO.getMaxInputParameterSize());
    if (configDTO.getDataRetentionSettings() != null
        && configDTO.getDataRetentionSettings().getDataRetentionPeriod() != null) {
      responseDTO.dataRetentionSettings(
          DataRetentionSettingsCreateResponseDTO.builder()
              .dataRetentionPeriod(configDTO.getDataRetentionSettings().getDataRetentionPeriod())
              .build());
    }
    if (configDTO.getExportSettings() != null && configDTO.getExportSettings().getMaxExportRequestsPerDay() != null) {
      responseDTO.exportSettings(
          ExportSettingsCreateResponseDTO.builder()
              .maxExportRequestsPerDay(configDTO.getExportSettings().getMaxExportRequestsPerDay())
              .build());
    }
    if (configDTO.getLogStreamingLimits() != null) {
      responseDTO.logStreamingLimits(
          LogStreamingLimitsDTO.builder()
              .maxLogLines(configDTO.getLogStreamingLimits().getMaxLogLines())
              .maxLogLineLength(configDTO.getLogStreamingLimits().getMaxLogLineLength())
              .streamExpirationSeconds(configDTO.getLogStreamingLimits().getStreamExpirationSeconds())
              .maxLogSizeBytes(configDTO.getLogStreamingLimits().getMaxLogSizeBytes())
              .maxWriteLogLinesPerMinute(configDTO.getLogStreamingLimits().getMaxWriteLogLinesPerMinute())
              .build());
    }
    return responseDTO.build();
  }

  public AccountOverridesUpdateResponseDTO toUpdateResponseDTO(AccountOverridesConfigDTO updateConfigDTO) {
    AccountOverridesUpdateResponseDTOBuilder responseDTO =
        AccountOverridesUpdateResponseDTO.builder()
            .accountIdentifier(updateConfigDTO.getAccountIdentifier())
            .retentionPeriodInMonths(updateConfigDTO.getRetentionPeriodInMonths())
            .maxConcurrentExecutions(updateConfigDTO.getMaxConcurrentExecutions())
            .maxOutcomeResponseSize(updateConfigDTO.getMaxOutcomeResponseSize())
            .maxTriggerCreationLimit(updateConfigDTO.getMaxTriggerCreationLimit())
            .maxQueuedExecutionLimit(updateConfigDTO.getMaxQueuedExecutionLimit())
            .maxLeafStepConcurrency(updateConfigDTO.getMaxLeafStepConcurrency())
            .maxInputParameterSize(updateConfigDTO.getMaxInputParameterSize())
            .stepOrStageMaxConcurrency(updateConfigDTO.getStepOrStageMaxConcurrency());
    if (updateConfigDTO.getDataRetentionSettings() != null
        && updateConfigDTO.getDataRetentionSettings().getDataRetentionPeriod() != null) {
      responseDTO.dataRetentionSettings(
          DataRetentionSettingsUpdateResponseDTO.builder()
              .dataRetentionPeriod(updateConfigDTO.getDataRetentionSettings().getDataRetentionPeriod())
              .build());
    }
    if (updateConfigDTO.getExportSettings() != null
        && updateConfigDTO.getExportSettings().getMaxExportRequestsPerDay() != null) {
      responseDTO.exportSettings(
          ExportSettingsUpdateResponseDTO.builder()
              .maxExportRequestsPerDay(updateConfigDTO.getExportSettings().getMaxExportRequestsPerDay())
              .build());
    }
    if (updateConfigDTO.getLogStreamingLimits() != null) {
      responseDTO.logStreamingLimits(
          LogStreamingLimitsDTO.builder()
              .maxLogLines(updateConfigDTO.getLogStreamingLimits().getMaxLogLines())
              .maxLogLineLength(updateConfigDTO.getLogStreamingLimits().getMaxLogLineLength())
              .streamExpirationSeconds(updateConfigDTO.getLogStreamingLimits().getStreamExpirationSeconds())
              .maxLogSizeBytes(updateConfigDTO.getLogStreamingLimits().getMaxLogSizeBytes())
              .maxWriteLogLinesPerMinute(updateConfigDTO.getLogStreamingLimits().getMaxWriteLogLinesPerMinute())
              .build());
    }
    return responseDTO.build();
  }
}
