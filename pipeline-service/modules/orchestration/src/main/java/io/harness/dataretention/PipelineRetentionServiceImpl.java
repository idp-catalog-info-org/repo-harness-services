/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.dataretention;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.entity.accountoverrides.beans.AccountOverridesConstants.DEFAULT_RETENTION_PERIOD_IN_MONTHS;
import static io.harness.search.entity.beans.PipelineSearchMigrationStatus.COMPLETE;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.entity.accountoverrides.AccountOverridesCacheInfo;
import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.entity.accountoverrides.DataRetentionEntity.DataRetentionEntityKeys;
import io.harness.entity.accountoverrides.DataRetentionSettings;
import io.harness.entity.accountoverrides.SearchSettings;
import io.harness.entity.accountoverrides.beans.AccountOverridesConfigDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.pms.accountoverrides.ExpressionCallType;
import io.harness.pms.accountoverrides.LogStreamingLimitsDTO;
import io.harness.repositories.dataretention.DataRetentionRepository;
import io.harness.retention.PipelineRetentionPeriod;
import io.harness.search.entity.beans.PipelineSearchIndexMigration;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PipelineRetentionServiceImpl implements PipelineRetentionService {
  public static final int MAXIMUM_RETENTION_PERIOD_IN_MONTHS = 24;

  @Inject private DataRetentionRepository dataRetentionRepository;

  public LoadingCache<String, Optional<AccountOverridesCacheInfo>> accountIdCache =
      CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(5, TimeUnit.MINUTES).build(new CacheLoader<>() {
        @Override
        public Optional<AccountOverridesCacheInfo> load(@NotNull String accountIdentifier) throws IOException {
          Optional<DataRetentionEntity> dataRetentionEntity =
              dataRetentionRepository.findByAccountIdentifier(accountIdentifier);
          return dataRetentionEntity.map(retentionEntity
              -> AccountOverridesCacheInfo.builder()
                     .maxConcurrentExecutions(retentionEntity.getMaxConcurrentExecutions())
                     .retentionPeriodInMonths(retentionEntity.getRetentionPeriodInMonths())
                     .maxInputParameterSize(retentionEntity.getMaxInputParameterSize())
                     .maxOutcomeResponseSize(retentionEntity.getMaxOutcomeResponseSize())
                     .maxQueuedExecutionLimit(retentionEntity.getMaxQueuedExecutionLimit())
                     .maxTriggerCreationLimit(retentionEntity.getMaxTriggerCreationLimit())
                     .maxFileSize(retentionEntity.getMaxFileSize())
                     .stepOrStageMaxConcurrency(retentionEntity.getStepOrStageMaxConcurrency())
                     .maxLeafStepConcurrency(retentionEntity.getMaxLeafStepConcurrency())
                     .maxPipelineCreationLimit(retentionEntity.getMaxPipelineCreationLimit())
                     .dataRetentionSettings(retentionEntity.getDataRetentionSettings())
                     .searchSettings(retentionEntity.getSearchSettings())
                     .maxCustomWebhookPayloadSize(retentionEntity.getMaxCustomWebhookPayloadSize())
                     .maxExpressionCalls(retentionEntity.getMaxExpressionCalls())
                     .build());
        }
      });

  @Override
  public int getRetentionPeriodInMonths(String accountId) {
    if (isEmpty(accountId)) {
      log.warn(format(
          "Cannot find retention period since account ID is %s. Returning the default retention period of %d months",
          accountId, DEFAULT_RETENTION_PERIOD_IN_MONTHS));
      return DEFAULT_RETENTION_PERIOD_IN_MONTHS;
    }
    Optional<DataRetentionEntity> dataRetentionEntity = dataRetentionRepository.findByAccountIdentifier(accountId);
    if (dataRetentionEntity.isEmpty()) {
      return DEFAULT_RETENTION_PERIOD_IN_MONTHS;
    }
    int retentionPeriodInMonths = dataRetentionEntity.get().getRetentionPeriodInMonths();
    if (retentionPeriodInMonths < DEFAULT_RETENTION_PERIOD_IN_MONTHS
        || retentionPeriodInMonths > MAXIMUM_RETENTION_PERIOD_IN_MONTHS) {
      log.warn(format("Minimum retention period for pipeline execution is %d months and maximum is %d months, it "
              + "cannot be %d month(s). Returning the default retention period of %d months for account ID %s",
          DEFAULT_RETENTION_PERIOD_IN_MONTHS, MAXIMUM_RETENTION_PERIOD_IN_MONTHS, retentionPeriodInMonths,
          DEFAULT_RETENTION_PERIOD_IN_MONTHS, accountId));
      return DEFAULT_RETENTION_PERIOD_IN_MONTHS;
    }
    return retentionPeriodInMonths;
  }

  @Override
  public Optional<Long> getMaxConcurrentPipelineExecution(String accountId) {
    try {
      if (isEmpty(accountId)) {
        return Optional.empty();
      }
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountId);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getMaxConcurrentExecutions);
    } catch (Exception ex) {
      log.warn(
          String.format(
              "Exception occurred while fetching max concurrent executions from cache for account id: {%s}", accountId),
          ex);
      return Optional.empty();
    }
  }

  @Override
  public Optional<Long> getMaxInputParameterSize(String accountIdentifier) {
    try {
      if (isEmpty(accountIdentifier)) {
        return Optional.empty();
      }
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getMaxInputParameterSize);
    } catch (Exception ex) {
      log.warn(String.format(
                   "Exception occurred while fetching max input size for account id {%s} from db", accountIdentifier),
          ex);
      return Optional.empty();
    }
  }

  @Override
  public Optional<Integer> getMaxExpressionCalls(String accountIdentifier, ExpressionCallType callType) {
    try {
      if (isEmpty(accountIdentifier)) {
        return Optional.empty();
      }
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getMaxExpressionCalls)
          .map(callBudgets -> callBudgets.get(callType));
    } catch (Exception ex) {
      log.warn(String.format("Exception occurred while fetching max expression mongo calls for account id {%s} from db",
                   accountIdentifier),
          ex);
      return Optional.empty();
    }
  }

  @Override
  public Optional<Integer> getStepOrStageMaxConcurrency(String accountIdentifier) {
    try {
      if (isEmpty(accountIdentifier)) {
        return Optional.empty();
      }
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getStepOrStageMaxConcurrency);
    } catch (Exception ex) {
      log.warn(
          String.format("Exception occurred while fetching max concurrent step execution for account id {%s} from db",
              accountIdentifier),
          ex);
      return Optional.empty();
    }
  }

  @Override
  public Optional<Integer> getMaxLeafStepConcurrency(String accountIdentifier) {
    try {
      if (isEmpty(accountIdentifier)) {
        return Optional.empty();
      }
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getMaxLeafStepConcurrency);
    } catch (Exception ex) {
      log.warn(String.format("Exception occurred while fetching max leaf step concurrency for account id {%s} from db",
                   accountIdentifier),
          ex);
      return Optional.empty();
    }
  }

  @Override
  public Optional<Long> getMaxOutcomeResponseSize(String accountIdentifier) {
    if (isEmpty(accountIdentifier)) {
      return Optional.empty();
    }
    try {
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getMaxOutcomeResponseSize);
    } catch (Exception ex) {
      log.warn(String.format("Exception occurred while fetching max response outcome size for account id {%s} from db",
                   accountIdentifier),
          ex);
    }
    return Optional.empty();
  }

  @Override
  public DataRetentionEntity updateMaxStepInputSize(String accountIdentifier, Long maxInputSize) {
    Update updateOps = new Update();
    updateOps.set(DataRetentionEntityKeys.maxInputParameterSize, maxInputSize);
    DataRetentionEntity entity = dataRetentionRepository.findAndModify(accountIdentifier, updateOps);
    invalidateCacheOverrides(accountIdentifier);
    return entity;
  }

  @Override
  public DataRetentionEntity updateMaxOutcomeResponseSize(String accountIdentifier, Long maxOutcomeSize) {
    Update updateOps = new Update();
    updateOps.set(DataRetentionEntityKeys.maxOutcomeResponseSize, maxOutcomeSize);
    DataRetentionEntity entity = dataRetentionRepository.findAndModify(accountIdentifier, updateOps);
    invalidateCacheOverrides(accountIdentifier);
    return entity;
  }

  @Override
  public Optional<DataRetentionEntity> getRetentionConfigByAccountId(String accountIdentifier) {
    try {
      if (isEmpty(accountIdentifier)) {
        throw new InvalidRequestException("Account id cannot be empty");
      }
      return dataRetentionRepository.findByAccountIdentifier(accountIdentifier);
    } catch (Exception ex) {
      log.error("Exception occurred while fetching data retention config for account id: {}", accountIdentifier, ex);
      throw ex;
    }
  }

  private void invalidateCacheOverrides(String accountIdentifier) {
    try {
      accountIdCache.invalidate(accountIdentifier);
    } catch (Exception ex) {
      log.warn(
          String.format("Error in invalidating the account override for account id {%s} in cache: ", accountIdentifier),
          ex);
    }
  }

  @Override
  public AccountOverridesConfigDTO createAccountOverrides(AccountOverridesConfigDTO configDTO) {
    DataRetentionEntity entity =
        dataRetentionRepository.save(AccountOverridesMapper.toEntity(configDTO, DEFAULT_RETENTION_PERIOD_IN_MONTHS));
    // The above retentionPeriodInMonths is set to default 6 months value to reduce the warning log message
    // present in line 80
    return AccountOverridesMapper.toDTO(entity);
  }

  @Override
  public AccountOverridesConfigDTO updateAccountOverrides(
      String accountIdentifier, AccountOverridesConfigDTO configDTO) {
    Update updateOps = getUpdateMapFromRequestDTO(configDTO);
    DataRetentionEntity entity = dataRetentionRepository.update(accountIdentifier, updateOps);
    invalidateCacheOverrides(accountIdentifier);
    return AccountOverridesMapper.toDTO(entity);
  }

  private Update getUpdateMapFromRequestDTO(AccountOverridesConfigDTO configDTO) {
    Update updateOps = new Update();
    if (configDTO.getRetentionPeriodInMonths() != null) {
      updateOps.set(DataRetentionEntityKeys.retentionPeriodInMonths, configDTO.getRetentionPeriodInMonths());
    }
    if (configDTO.getMaxConcurrentExecutions() != null) {
      updateOps.set(DataRetentionEntityKeys.maxConcurrentExecutions, configDTO.getMaxConcurrentExecutions());
    }
    if (configDTO.getMaxInputParameterSize() != null) {
      updateOps.set(DataRetentionEntityKeys.maxInputParameterSize, configDTO.getMaxInputParameterSize());
    }
    if (configDTO.getMaxTriggerCreationLimit() != null) {
      updateOps.set(DataRetentionEntityKeys.maxTriggerCreationLimit, configDTO.getMaxTriggerCreationLimit());
    }
    if (configDTO.getMaxQueuedExecutionLimit() != null) {
      updateOps.set(DataRetentionEntityKeys.maxQueuedExecutionLimit, configDTO.getMaxQueuedExecutionLimit());
    }
    if (configDTO.getMaxLeafStepConcurrency() != null) {
      updateOps.set(DataRetentionEntityKeys.maxLeafStepConcurrency, configDTO.getMaxLeafStepConcurrency());
    }
    if (configDTO.getDataRetentionSettings() != null
        && configDTO.getDataRetentionSettings().getDataRetentionPeriod() != null) {
      updateOps.set(
          DataRetentionEntityKeys.dataRetentionPeriod, configDTO.getDataRetentionSettings().getDataRetentionPeriod());
    }
    if (configDTO.getExportSettings() != null && configDTO.getExportSettings().getMaxExportRequestsPerDay() != null) {
      updateOps.set(
          DataRetentionEntityKeys.maxExportRequestsPerDay, configDTO.getExportSettings().getMaxExportRequestsPerDay());
    }
    LogStreamingLimitsDTO logStreamingLimits = configDTO.getLogStreamingLimits();
    if (logStreamingLimits != null) {
      if (logStreamingLimits.getMaxLogLines() != null) {
        updateOps.set(DataRetentionEntityKeys.maxLogLines, logStreamingLimits.getMaxLogLines());
      }
      if (logStreamingLimits.getMaxLogLineLength() != null) {
        updateOps.set(DataRetentionEntityKeys.maxLogLineLength, logStreamingLimits.getMaxLogLineLength());
      }
      if (logStreamingLimits.getStreamExpirationSeconds() != null) {
        updateOps.set(DataRetentionEntityKeys.streamExpirationSeconds, logStreamingLimits.getStreamExpirationSeconds());
      }
      if (logStreamingLimits.getMaxLogSizeBytes() != null) {
        updateOps.set(DataRetentionEntityKeys.maxLogSizeBytes, logStreamingLimits.getMaxLogSizeBytes());
      }
      if (logStreamingLimits.getMaxWriteLogLinesPerMinute() != null) {
        updateOps.set(
            DataRetentionEntityKeys.maxWriteLogLinesPerMinute, logStreamingLimits.getMaxWriteLogLinesPerMinute());
      }
    }
    return updateOps;
  }

  @Override
  public Optional<Integer> getMaxQueuedExecutionLimit(String accountIdentifier) {
    if (isEmpty(accountIdentifier)) {
      return Optional.empty();
    }
    try {
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getMaxQueuedExecutionLimit);
    } catch (Exception ex) {
      log.warn(String.format("Exception occurred while fetching max queued executions for account id {%s} from db",
                   accountIdentifier),
          ex);
    }
    return Optional.empty();
  }

  @Override
  public Optional<Integer> getMaxTriggerCreationLimit(String accountIdentifier) {
    if (isEmpty(accountIdentifier)) {
      return Optional.empty();
    }
    try {
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getMaxTriggerCreationLimit);
    } catch (Exception ex) {
      log.warn(String.format("Exception occurred while fetching max trigger creation limit for account id {%s} from db",
                   accountIdentifier),
          ex);
    }
    return Optional.empty();
  }

  @Override
  public DataRetentionEntity updateMaxTriggerCreationLimit(String accountIdentifier, Long maxTriggerCount) {
    Update updateOps = new Update();
    updateOps.set(DataRetentionEntityKeys.maxTriggerCreationLimit, maxTriggerCount);
    DataRetentionEntity entity = dataRetentionRepository.findAndModify(accountIdentifier, updateOps);
    invalidateCacheOverrides(accountIdentifier);
    return entity;
  }

  @Override
  public DataRetentionEntity updateSearchIndexMigrationDetails(String accountIdentifier,
      PipelineSearchMigrationStatus indexMigrationStatus, String oldIndexName, String newIndexName) {
    Update updateOps = new Update();
    updateOps.set(DataRetentionEntityKeys.searchIndexMigrationStatus, indexMigrationStatus);
    if (!isEmpty(oldIndexName)) {
      updateOps.set(DataRetentionEntityKeys.searchIndexMigrationOldIndexName, oldIndexName);
    }
    if (!isEmpty(newIndexName)) {
      updateOps.set(DataRetentionEntityKeys.searchIndexMigrationNewIndexName, newIndexName);
    }
    DataRetentionEntity entity = dataRetentionRepository.update(accountIdentifier, updateOps);
    invalidateCacheOverrides(accountIdentifier);
    return entity;
  }

  @Override
  public PipelineRetentionPeriod getRetentionPeriod(
      String accountIdentifier, PipelineSearchIndexMigration indexMigrationDTO) {
    Optional<DataRetentionSettings> dataRetentionSettings = getDataRetentionSettings(accountIdentifier);
    return PipelineRetentionPeriod.builder()
        .oldIndexRetentionPeriod(indexMigrationDTO.getOldIndexRetentionPeriod())
        .newIndexRetentionPeriod(indexMigrationDTO.getNewIndexRetentionPeriod())
        .indexMigrationStatus(indexMigrationDTO.getStatus())
        .dataRetentionPeriod(dataRetentionSettings.map(DataRetentionSettings::getDataRetentionPeriod).orElse(null))
        .build();
  }

  @Override
  public PipelineRetentionPeriod updateRetentionPeriod(String accountIdentifier,
      DataRetentionPeriod dataRetentionPeriod, PipelineSearchIndexMigration indexMigrationDTO) {
    Update updateOps = new Update();
    updateOps.set(DataRetentionEntityKeys.dataRetentionPeriod, dataRetentionPeriod);
    updateOps.set(
        DataRetentionEntityKeys.retentionPeriodInMonths, dataRetentionPeriod.getDataRetentionPeriodInMonths());
    DataRetentionEntity entity = dataRetentionRepository.findAndModify(accountIdentifier, updateOps);
    invalidateCacheOverrides(accountIdentifier);
    return PipelineRetentionPeriod.builder()
        .oldIndexRetentionPeriod(indexMigrationDTO.getOldIndexRetentionPeriod())
        .newIndexRetentionPeriod(indexMigrationDTO.getNewIndexRetentionPeriod())
        .indexMigrationStatus(indexMigrationDTO.getStatus())
        .dataRetentionPeriod(entity.getDataRetentionSettings().getDataRetentionPeriod())
        .build();
  }

  @Override
  public Optional<DataRetentionSettings> getDataRetentionSettings(String accountIdentifier) {
    if (isEmpty(accountIdentifier)) {
      return Optional.empty();
    }
    try {
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getDataRetentionSettings);
    } catch (Exception ex) {
      log.warn(String.format("Exception occurred while fetching data retention settings for account id {%s} from db",
                   accountIdentifier),
          ex);
    }
    return Optional.empty();
  }

  @Override
  public Optional<SearchSettings> getSearchSettings(String accountIdentifier) {
    if (isEmpty(accountIdentifier)) {
      return Optional.empty();
    }
    try {
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getSearchSettings);
    } catch (Exception ex) {
      log.warn(String.format(
                   "Exception occurred while fetching search settings for account id {%s} from db", accountIdentifier),
          ex);
    }
    return Optional.empty();
  }

  @Override
  public Optional<Integer> getMaxPipelineCreationLimit(String accountIdentifier) {
    if (isEmpty(accountIdentifier)) {
      return Optional.empty();
    }
    try {
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getMaxPipelineCreationLimit);
    } catch (Exception ex) {
      log.warn(
          String.format("Exception occurred while fetching max pipeline creation limit for account id {%s} from db",
              accountIdentifier),
          ex);
    }
    return Optional.empty();
  }

  @Override
  public DataRetentionEntity updateMaxPipelineCreationLimit(String accountIdentifier, int maxPipelineCount) {
    Update updateOps = new Update();
    updateOps.set(DataRetentionEntityKeys.maxPipelineCreationLimit, maxPipelineCount);
    DataRetentionEntity entity = dataRetentionRepository.findAndModify(accountIdentifier, updateOps);
    invalidateCacheOverrides(accountIdentifier);
    return entity;
  }

  @Override
  public Optional<Long> getMaxFileSizeLimit(String accountIdentifier) {
    if (isEmpty(accountIdentifier)) {
      return Optional.empty();
    }
    try {
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getMaxFileSize);
    } catch (Exception ex) {
      log.warn(String.format("Exception occurred while fetching max file size limit for account id {%s} from db",
                   accountIdentifier),
          ex);
    }
    return Optional.empty();
  }

  @Override
  public DataRetentionEntity updateMaxFileSizeLimit(String accountIdentifier, Long maxFileSize) {
    Update updateOps = new Update();
    updateOps.set(DataRetentionEntityKeys.maxFileSize, maxFileSize);
    updateOps.setOnInsert(DataRetentionEntityKeys.retentionPeriodInMonths, DEFAULT_RETENTION_PERIOD_IN_MONTHS);
    DataRetentionEntity entity = dataRetentionRepository.findAndModify(accountIdentifier, updateOps);
    invalidateCacheOverrides(accountIdentifier);
    return entity;
  }

  @Override
  public Optional<Long> getPayloadSizeLimit(String accountIdentifier) {
    if (isEmpty(accountIdentifier)) {
      return Optional.empty();
    }
    try {
      Optional<AccountOverridesCacheInfo> accountOverrideCacheInfo = accountIdCache.get(accountIdentifier);
      return accountOverrideCacheInfo.map(AccountOverridesCacheInfo::getMaxCustomWebhookPayloadSize);
    } catch (Exception ex) {
      log.warn(String.format("Exception occurred while fetching max file size limit for account id {%s} from db",
                   accountIdentifier),
          ex);
    }
    return Optional.empty();
  }

  @Override
  public void updateMaxPayloadSizeLimit(String accountIdentifier, Long maxPayloadSize) {
    Update updateOps = new Update();
    updateOps.set(DataRetentionEntityKeys.maxCustomWebhookPayloadSize, maxPayloadSize);
    updateOps.setOnInsert(DataRetentionEntityKeys.retentionPeriodInMonths, DEFAULT_RETENTION_PERIOD_IN_MONTHS);
    DataRetentionEntity entity = dataRetentionRepository.findAndModify(accountIdentifier, updateOps);
    invalidateCacheOverrides(accountIdentifier);
  }

  @Override
  public Stream<DataRetentionEntity> getAllWithRetentionSettingsEnabledFromSecondary() {
    Criteria criteria = new Criteria(DataRetentionEntityKeys.dataRetentionSettings).ne(null);
    return dataRetentionRepository.fetchFromSecondaryWithProjections(
        criteria, Set.of(DataRetentionEntityKeys.accountIdentifier, DataRetentionEntityKeys.dataRetentionSettings));
  }

  @Override
  public Stream<DataRetentionEntity> getAllWithSearchSettingsFromSecondary() {
    Criteria criteria = new Criteria(DataRetentionEntityKeys.searchSettings)
                            .ne(null)
                            .and(DataRetentionEntityKeys.dataRetentionSettings)
                            .ne(null)
                            .and(DataRetentionEntityKeys.searchIndexMigrationStatus)
                            .is(COMPLETE);
    return dataRetentionRepository.fetchFromSecondaryWithProjections(
        criteria, Set.of(DataRetentionEntityKeys.accountIdentifier, DataRetentionEntityKeys.dataRetentionSettings));
  }
}
