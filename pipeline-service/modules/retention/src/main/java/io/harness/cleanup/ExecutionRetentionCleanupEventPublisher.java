/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cleanup;

import static io.harness.eventsframework.EventsFrameworkConstants.EXECUTION_RETENTION_CLEANUP_EVENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.executionretention.ExecutionRetentionCleanupEvent;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.List;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * Publisher for execution retention cleanup events.
 * Publishes events to notify downstream services (like ng-manager) about cleaned up executions
 * so they can clean up their associated data (e.g., TimescaleDB records).
 */
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@Singleton
public class ExecutionRetentionCleanupEventPublisher {
  private static final int DEFAULT_BATCH_SIZE = 500;
  private static final String ACCOUNT_ID_KEY = "accountId";
  private static final String LOG_PREFIX = "[EXECUTION_RETENTION_CLEANUP_EVENT]";

  @Nullable @Inject @Named(EXECUTION_RETENTION_CLEANUP_EVENT) private Producer eventProducer;
  @Inject private DataRetentionConfig dataRetentionConfig;

  /**
   * Publishes cleanup events for the given planExecutionIds.
   * If the list is large, it will be batched into multiple events based on cleanupEventBatchSize config.
   *
   * @param accountIdentifier the account for which cleanup happened
   * @param planExecutionIds list of plan execution IDs that were cleaned up
   * @param retentionPeriodInMonths the retention period used for cleanup
   * @param cleanupTimestamp timestamp when cleanup started
   */
  public void publishCleanupEvent(
      String accountIdentifier, List<String> planExecutionIds, int retentionPeriodInMonths, Instant cleanupTimestamp) {
    // Check if cleanup event publishing is enabled via config
    if (!dataRetentionConfig.isCleanupEventEnabled()) {
      log.debug("{} Cleanup event publishing is disabled via config, skipping", LOG_PREFIX);
      return;
    }

    if (eventProducer == null) {
      log.debug("{} Event producer not available, skipping event publishing", LOG_PREFIX);
      return;
    }

    if (planExecutionIds == null || planExecutionIds.isEmpty()) {
      return;
    }

    int batchSize = getBatchSize();

    // Batch the events if too many planExecutionIds
    for (int i = 0; i < planExecutionIds.size(); i += batchSize) {
      List<String> batch = planExecutionIds.subList(i, Math.min(i + batchSize, planExecutionIds.size()));

      ExecutionRetentionCleanupEvent event =
          buildEvent(accountIdentifier, batch, retentionPeriodInMonths, cleanupTimestamp);

      try {
        eventProducer.send(Message.newBuilder()
                               .putAllMetadata(ImmutableMap.of(ACCOUNT_ID_KEY, accountIdentifier))
                               .setData(event.toByteString())
                               .build());

        log.info("{} Published cleanup event for account: {}, planExecutionIds count: {}", LOG_PREFIX,
            accountIdentifier, batch.size());

      } catch (EventsFrameworkDownException ex) {
        log.error("{} Failed to publish cleanup event for account: {}", LOG_PREFIX, accountIdentifier, ex);
        // Don't throw - cleanup should continue even if event publishing fails
      }
    }
  }

  private int getBatchSize() {
    int configuredBatchSize = dataRetentionConfig.getCleanupEventBatchSize();
    // Ensure batch size is within reasonable bounds (min 100, max 1000).
    // We can modify this later if needed
    if (configuredBatchSize < 100) {
      return 100;
    } else if (configuredBatchSize > 1000) {
      return 1000;
    }
    return configuredBatchSize > 0 ? configuredBatchSize : DEFAULT_BATCH_SIZE;
  }

  private ExecutionRetentionCleanupEvent buildEvent(
      String accountIdentifier, List<String> planExecutionIds, int retentionPeriodInMonths, Instant cleanupTimestamp) {
    return ExecutionRetentionCleanupEvent.newBuilder()
        .setAccountIdentifier(accountIdentifier)
        .addAllPlanExecutionIds(planExecutionIds)
        .setRetentionPeriodInMonths(retentionPeriodInMonths)
        .setCleanupTimestampMillis(cleanupTimestamp.toEpochMilli())
        .build();
  }
}
