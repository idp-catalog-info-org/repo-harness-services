
/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dataretention;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.entity.accountoverrides.DataRetentionSettings;
import io.harness.entity.accountoverrides.SearchSettings;
import io.harness.entity.accountoverrides.beans.AccountOverridesConfigDTO;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.pms.accountoverrides.ExpressionCallType;
import io.harness.retention.PipelineRetentionPeriod;
import io.harness.search.entity.beans.PipelineSearchIndexMigration;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;

import java.util.Optional;
import java.util.stream.Stream;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public interface PipelineRetentionService {
  int getRetentionPeriodInMonths(String accountId);

  Optional<Long> getMaxConcurrentPipelineExecution(String accountId);

  Optional<Integer> getStepOrStageMaxConcurrency(String accountIdentifier);

  Optional<Integer> getMaxLeafStepConcurrency(String accountIdentifier);

  Optional<Long> getMaxInputParameterSize(String accountIdentifier);

  Optional<Integer> getMaxExpressionCalls(String accountIdentifier, ExpressionCallType callType);

  DataRetentionEntity updateMaxStepInputSize(String accountIdentifier, Long maxInputSize);

  Optional<DataRetentionEntity> getRetentionConfigByAccountId(String accountIdentifier);

  Optional<Long> getMaxOutcomeResponseSize(String accountIdentifier);

  DataRetentionEntity updateMaxOutcomeResponseSize(String accountIdentifier, Long maxOutcomeSize);

  AccountOverridesConfigDTO createAccountOverrides(AccountOverridesConfigDTO configDTO);

  AccountOverridesConfigDTO updateAccountOverrides(String accountIdentifier, AccountOverridesConfigDTO configDTO);

  Optional<Integer> getMaxQueuedExecutionLimit(String accountIdentifier);

  Optional<Integer> getMaxTriggerCreationLimit(String accountIdentifier);

  DataRetentionEntity updateMaxTriggerCreationLimit(String accountIdentifier, Long maxTriggerCount);

  DataRetentionEntity updateSearchIndexMigrationDetails(String accountIdentifier,
      PipelineSearchMigrationStatus indexMigrationStatus, String oldIndexName, String newIndexName);

  PipelineRetentionPeriod getRetentionPeriod(String accountIdentifier, PipelineSearchIndexMigration indexMigrationDTO);

  PipelineRetentionPeriod updateRetentionPeriod(String accountIdentifier, DataRetentionPeriod dataRetentionPeriod,
      PipelineSearchIndexMigration indexMigrationDTO);

  Optional<DataRetentionSettings> getDataRetentionSettings(String accountIdentifier);

  Optional<SearchSettings> getSearchSettings(String accountIdentifier);

  Optional<Integer> getMaxPipelineCreationLimit(String accountIdentifier);

  DataRetentionEntity updateMaxPipelineCreationLimit(String accountIdentifier, int maxPipelineCount);

  Optional<Long> getMaxFileSizeLimit(String accountIdentifier);

  DataRetentionEntity updateMaxFileSizeLimit(String accountIdentifier, Long maxFileSize);

  Optional<Long> getPayloadSizeLimit(String accountIdentifier);

  void updateMaxPayloadSizeLimit(String accountIdentifier, Long maxPayloadSize);

  Stream<DataRetentionEntity> getAllWithRetentionSettingsEnabledFromSecondary();

  Stream<DataRetentionEntity> getAllWithSearchSettingsFromSecondary();
}
