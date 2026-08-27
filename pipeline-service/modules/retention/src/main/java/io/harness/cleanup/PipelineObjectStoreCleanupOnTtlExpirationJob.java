/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cleanup;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.dataretention.entity.beans.ExecutionRetentionCleanupResult;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.logger.ExecutionRetentionLogContext;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.search.service.PipelineSearchService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineObjectStoreCleanupOnTtlExpirationJob implements Runnable {
  @Inject PersistentLocker persistentLocker;
  @Inject PipelineRetentionService pipelineRetentionService;
  @Inject ExecutionRetentionMetadataService executionRetentionMetadataService;
  @Inject ExecutionRetentionService executionRetentionService;
  @Nullable @Inject @Named("DataRetentionObjectStoreClient") private ObjectStoreClient objectStoreClient;
  @Inject DataRetentionConfig dataRetentionConfig;
  @Inject private PipelineSearchService pipelineSearchService;
  @Inject private ExecutionRetentionCleanupEventPublisher cleanupEventPublisher;
  private static final String LOG_CONTEXT = "[PIPELINE_OBJECT_STORE_CLEAN_UP]";
  private static final String LOCK_NAME = "PipelineObjectStoreCleanupOnTtlExpirationJob";
  public static final int ELASTIC_DELETE_ACCOUNT_BATCH_SIZE = 100;
  private String INTERRUPT_FLOW_MESSAGE =
      "Interrupting the flow as system is in maintenance or clean up job has been running for more than {} hours";
  private int totalCleanedUpMetadataCount;

  @Override
  public void run() {
    try (ExecutionRetentionLogContext context = new ExecutionRetentionLogContext(LOG_CONTEXT)) {
      // checking if object store client is available
      if (objectStoreClient == null) {
        log.warn("Not proceeding with clean up as objectStoreClient is not available");
        return;
      }

      // Acquiring a lock for maximum of 6 hours with a maximum wait time of 5 seconds to ensure that the cleanup logic
      // executes safely within the locked context
      try (AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(
               LOCK_NAME, Duration.ofMinutes(dataRetentionConfig.getCleanUpIntervalMinutes()), Duration.ofSeconds(5))) {
        if (lock != null) {
          startCleanup();
        }
      } catch (Exception ex) {
        log.error("Failed to acquire lock for doing object store clean up", ex);
      }
    }
  }

  private void startCleanup() {
    log.info("cleaning up started...");
    Instant cleanupJobStartTs = Instant.now();
    totalCleanedUpMetadataCount = 0;
    // delete 50k executions from running index of elastic search
    String taskId = pipelineSearchService.deleteCompletedExecutions();
    log.info(
        String.format("[ELASTIC_SEARCH]: TaskID: %s for deleting 50k completed executions from running index", taskId));
    Set<String> accountIds = cleanUpForAccountsWithDataRetentionSettings(cleanupJobStartTs);
    cleanUpForAccountsWithoutDataRetentionSettings(cleanupJobStartTs, accountIds);
    log.info("Total {} execution metadata got cleaned up and It took {} minutes", totalCleanedUpMetadataCount,
        Duration.between(cleanupJobStartTs, Instant.now()).toMinutes());
  }

  private Set<String> cleanUpForAccountsWithDataRetentionSettings(Instant cleanupJobStartTs) {
    Set<String> accountIds = new HashSet<>();
    try (Stream<DataRetentionEntity> stream =
             pipelineRetentionService.getAllWithRetentionSettingsEnabledFromSecondary()) {
      Iterator<DataRetentionEntity> iterator = stream.iterator();
      while (iterator.hasNext()) {
        DataRetentionEntity dataRetentionEntity = iterator.next();
        String accountIdentifier = dataRetentionEntity.getAccountIdentifier();
        if (dataRetentionEntity.getDataRetentionSettings().getDataRetentionPeriod() != null) {
          accountIds.add(accountIdentifier);
          int retentionPeriodInMonths =
              dataRetentionEntity.getDataRetentionSettings().getDataRetentionPeriod().getDataRetentionPeriodInMonths();
          String taskId = pipelineSearchService.deleteExpiredExecutions(accountIdentifier, retentionPeriodInMonths + 1);
          log.info(String.format("[ELASTIC_SEARCH]: TaskID: %s for deleting expired executions for account: %s, "
                  + "retention period: %d months",
              taskId, accountIdentifier, retentionPeriodInMonths));
          cleanUpObjectStoreAndMetadataByAccountId(accountIdentifier, retentionPeriodInMonths, cleanupJobStartTs);
        }
        if (isCleanupJobTimeMaxLimitExceeded(cleanupJobStartTs) || getMaintenanceFlag()) {
          log.warn(INTERRUPT_FLOW_MESSAGE,
              Duration.ofMinutes(dataRetentionConfig.getCleanUpIntervalMinutes()).toHours() - 1);
          break;
        }
      }
    } catch (Exception ex) {
      log.error("Failed while cleaning up object store for accounts with data retention setting enabled", ex);
    }
    return accountIds;
  }

  private void cleanUpForAccountsWithoutDataRetentionSettings(
      Instant cleanupJobStartTs, Set<String> accountIdsWithSettingEnabled) {
    if (isCleanupJobTimeMaxLimitExceeded(cleanupJobStartTs) || getMaintenanceFlag()) {
      log.warn(
          INTERRUPT_FLOW_MESSAGE, Duration.ofMinutes(dataRetentionConfig.getCleanUpIntervalMinutes()).toHours() - 1);
      return;
    }
    int retentionPeriodInMonths = DataRetentionPeriod.DATA_RETENTION_PERIOD_6_MONTHS.getDataRetentionPeriodInMonths();
    List<String> accountIdsWithoutRetentionSetting =
        executionRetentionMetadataService.getAllUniqueAccountIdsWithoutRetentionSetting(accountIdsWithSettingEnabled);
    /*
     Filter out null account IDs before processing, this is an intermediate fix - reason being this cleanup job is
     running parallelly to parent id migration job. So there is a race condition due to which this job deletes data from
     blob and parallelly migration picks same record, so the metadata record gets deleted and migration job fails(blob
     not found) and upserts record again with only plan execution id and parent unique id
     */
    List<String> validAccountIds =
        accountIdsWithoutRetentionSetting.stream().filter(accountId -> accountId != null).collect(Collectors.toList());

    List<List<String>> accountIDBatches = createBatchesForAccount(validAccountIds);
    for (List<String> accountIDs : accountIDBatches) {
      String taskId = pipelineSearchService.deleteExpiredExecutionsForDefaultRetentionPeriod(
          accountIDs, retentionPeriodInMonths + 1);
      log.info(String.format(
          "[ELASTIC_SEARCH]: TaskID: %s for deleting expired executions for accounts: %s, retention period: %d months",
          taskId, accountIDs, retentionPeriodInMonths));
    }
    for (String accountIdentifier : validAccountIds) {
      cleanUpObjectStoreAndMetadataByAccountId(accountIdentifier, retentionPeriodInMonths, cleanupJobStartTs);
    }
  }

  private void cleanUpObjectStoreAndMetadataByAccountId(
      String accountIdentifier, int retentionPeriodInMonths, Instant cleanupJobStartTs) {
    try (ExecutionRetentionLogContext context = new ExecutionRetentionLogContext(LOG_CONTEXT, accountIdentifier)) {
      // Adding 1 month of buffer time in retention period
      ExecutionRetentionCleanupResult result =
          executionRetentionService.deleteExpiredTTLExecutionsWithResult(accountIdentifier, retentionPeriodInMonths + 1,
              cleanupJobStartTs, Duration.ofMinutes(dataRetentionConfig.getCleanUpIntervalMinutes()).minusHours(1));

      totalCleanedUpMetadataCount += result.getCleanedCount();

      // Publish cleanup event for downstream services (e.g., ng-manager for TimescaleDB cleanup)
      if (result.getCleanedPlanExecutionIds() != null && !result.getCleanedPlanExecutionIds().isEmpty()) {
        cleanupEventPublisher.publishCleanupEvent(
            accountIdentifier, result.getCleanedPlanExecutionIds(), retentionPeriodInMonths, cleanupJobStartTs);
      }
    }
  }

  private List<List<String>> createBatchesForAccount(List<String> accountIDs) {
    List<List<String>> batches = new ArrayList<>();
    for (int i = 0; i < accountIDs.size(); i += ELASTIC_DELETE_ACCOUNT_BATCH_SIZE) {
      batches.add(accountIDs.subList(i, Math.min(i + ELASTIC_DELETE_ACCOUNT_BATCH_SIZE, accountIDs.size())));
    }
    return batches;
  }

  private boolean isCleanupJobTimeMaxLimitExceeded(Instant jobStartTs) {
    Duration elapsedTime = Duration.between(jobStartTs, Instant.now());
    return elapsedTime.compareTo(Duration.ofMinutes(dataRetentionConfig.getCleanUpIntervalMinutes()).minusHours(1)) > 0;
  }
}
