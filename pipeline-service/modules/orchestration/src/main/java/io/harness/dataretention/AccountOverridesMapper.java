/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.entity.accountoverrides.DataRetentionEntity.DataRetentionEntityBuilder;
import io.harness.entity.accountoverrides.DataRetentionSettings;
import io.harness.entity.accountoverrides.ExportSettings;
import io.harness.entity.accountoverrides.LogStreamingLimits;
import io.harness.entity.accountoverrides.beans.AccountOverridesConfigDTO;
import io.harness.entity.accountoverrides.beans.DataRetentionSettingsDTO;
import io.harness.entity.accountoverrides.beans.ExportSettingsDTO;
import io.harness.pms.accountoverrides.LogStreamingLimitsDTO;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class AccountOverridesMapper {
  public DataRetentionEntity toEntity(AccountOverridesConfigDTO configDTO, int defaultRetentionPeriod) {
    DataRetentionEntityBuilder dataRetentionEntity =
        DataRetentionEntity.builder().accountIdentifier(configDTO.getAccountIdentifier());
    if (configDTO.getRetentionPeriodInMonths() != null) {
      dataRetentionEntity.retentionPeriodInMonths(configDTO.getRetentionPeriodInMonths());
    } else {
      dataRetentionEntity.retentionPeriodInMonths(defaultRetentionPeriod);
    }
    if (configDTO.getMaxConcurrentExecutions() != null) {
      dataRetentionEntity.maxConcurrentExecutions(configDTO.getMaxConcurrentExecutions());
    }
    if (configDTO.getMaxInputParameterSize() != null) {
      dataRetentionEntity.maxInputParameterSize(configDTO.getMaxInputParameterSize());
    }
    if (configDTO.getMaxOutcomeResponseSize() != null) {
      dataRetentionEntity.maxOutcomeResponseSize(configDTO.getMaxOutcomeResponseSize());
    }
    if (configDTO.getMaxQueuedExecutionLimit() != null) {
      dataRetentionEntity.maxQueuedExecutionLimit(configDTO.getMaxQueuedExecutionLimit());
    }
    if (configDTO.getMaxTriggerCreationLimit() != null) {
      dataRetentionEntity.maxTriggerCreationLimit(configDTO.getMaxTriggerCreationLimit());
    }
    if (configDTO.getMaxLeafStepConcurrency() != null) {
      dataRetentionEntity.maxLeafStepConcurrency(configDTO.getMaxLeafStepConcurrency());
    }
    if (configDTO.getMaxExpressionCalls() != null) {
      dataRetentionEntity.maxExpressionCalls(configDTO.getMaxExpressionCalls());
    }
    if (configDTO.getDataRetentionSettings() != null
        && configDTO.getDataRetentionSettings().getDataRetentionPeriod() != null) {
      dataRetentionEntity.dataRetentionSettings(
          DataRetentionSettings.builder()
              .dataRetentionPeriod(configDTO.getDataRetentionSettings().getDataRetentionPeriod())
              .build());
    }
    if (configDTO.getExportSettings() != null && configDTO.getExportSettings().getMaxExportRequestsPerDay() != null) {
      dataRetentionEntity.exportSettings(
          ExportSettings.builder()
              .maxExportRequestsPerDay(configDTO.getExportSettings().getMaxExportRequestsPerDay())
              .build());
    }
    if (configDTO.getLogStreamingLimits() != null) {
      dataRetentionEntity.logStreamingLimits(
          LogStreamingLimits.builder()
              .maxLogLines(configDTO.getLogStreamingLimits().getMaxLogLines())
              .maxLogLineLength(configDTO.getLogStreamingLimits().getMaxLogLineLength())
              .streamExpirationSeconds(configDTO.getLogStreamingLimits().getStreamExpirationSeconds())
              .maxLogSizeBytes(configDTO.getLogStreamingLimits().getMaxLogSizeBytes())
              .maxWriteLogLinesPerMinute(configDTO.getLogStreamingLimits().getMaxWriteLogLinesPerMinute())
              .build());
    }
    return dataRetentionEntity.build();
  }

  public AccountOverridesConfigDTO toDTO(DataRetentionEntity entity) {
    AccountOverridesConfigDTO responseDTO = AccountOverridesConfigDTO.builder()
                                                .accountIdentifier(entity.getAccountIdentifier())
                                                .retentionPeriodInMonths(entity.getRetentionPeriodInMonths())
                                                .maxConcurrentExecutions(entity.getMaxConcurrentExecutions())
                                                .maxOutcomeResponseSize(entity.getMaxOutcomeResponseSize())
                                                .maxInputParameterSize(entity.getMaxInputParameterSize())
                                                .maxQueuedExecutionLimit(entity.getMaxQueuedExecutionLimit())
                                                .maxTriggerCreationLimit(entity.getMaxTriggerCreationLimit())
                                                .maxLeafStepConcurrency(entity.getMaxLeafStepConcurrency())
                                                .maxExpressionCalls(entity.getMaxExpressionCalls())
                                                .build();
    if (entity.getDataRetentionSettings() != null
        && entity.getDataRetentionSettings().getDataRetentionPeriod() != null) {
      responseDTO.setDataRetentionSettings(
          DataRetentionSettingsDTO.builder()
              .dataRetentionPeriod(entity.getDataRetentionSettings().getDataRetentionPeriod())
              .build());
    }
    if (entity.getExportSettings() != null && entity.getExportSettings().getMaxExportRequestsPerDay() != null) {
      responseDTO.setExportSettings(
          ExportSettingsDTO.builder()
              .maxExportRequestsPerDay(entity.getExportSettings().getMaxExportRequestsPerDay())
              .build());
    }
    if (entity.getLogStreamingLimits() != null) {
      responseDTO.setLogStreamingLimits(
          LogStreamingLimitsDTO.builder()
              .maxLogLines(entity.getLogStreamingLimits().getMaxLogLines())
              .maxLogLineLength(entity.getLogStreamingLimits().getMaxLogLineLength())
              .streamExpirationSeconds(entity.getLogStreamingLimits().getStreamExpirationSeconds())
              .maxLogSizeBytes(entity.getLogStreamingLimits().getMaxLogSizeBytes())
              .maxWriteLogLinesPerMinute(entity.getLogStreamingLimits().getMaxWriteLogLinesPerMinute())
              .build());
    }
    return responseDTO;
  }
}
