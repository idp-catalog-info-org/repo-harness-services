/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
import io.harness.cleanup.config.OrchestrationGraphCacheCleanupConfig;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.service.PostgreSQLGraphStoreService;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class OrchestrationGraphCacheCleanupJob implements Runnable {
  private static final String LOG_PREFIX = "[ORCHESTRATION_GRAPH_CACHE_CLEANUP]";
  private static final String LOCK_NAME = "OrchestrationGraphCacheCleanupJob";

  @Inject private PersistentLocker persistentLocker;
  @Inject private OrchestrationGraphCacheCleanupConfig config;
  @Nullable @Inject private PostgreSQLGraphStoreService postgreSQLGraphStoreService;

  @Override
  public void run() {
    if (!config.isCleanUpEnabled()) {
      log.debug("{} Cleanup is disabled via cleanUpEnabled flag, skipping", LOG_PREFIX);
      return;
    }

    if (postgreSQLGraphStoreService == null) {
      log.debug("{} PostgreSQLGraphStoreService not available, skipping cleanup", LOG_PREFIX);
      return;
    }

    if (getMaintenanceFlag()) {
      log.info("{} System is in maintenance mode, skipping cleanup", LOG_PREFIX);
      return;
    }

    try (AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(
             LOCK_NAME, Duration.ofMinutes(config.getMaxJobDurationMinutes()), Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info("{} Could not acquire lock, another instance is running cleanup", LOG_PREFIX);
        return;
      }
      performCleanup();
    } catch (Exception ex) {
      log.error("{} Failed during graph cache cleanup", LOG_PREFIX, ex);
    }
  }

  private void performCleanup() {
    Instant startTime = Instant.now();
    int totalDeleted = 0;
    int batchSize = config.getBatchSize();
    Duration maxDuration = Duration.ofMinutes(config.getMaxJobDurationMinutes());

    log.info("{} Starting cleanup with batchSize={}, maxDuration={} minutes", LOG_PREFIX, batchSize,
        config.getMaxJobDurationMinutes());

    while (!getMaintenanceFlag()) {
      if (Duration.between(startTime, Instant.now()).compareTo(maxDuration) > 0) {
        log.info("{} Max job duration exceeded, stopping. Total deleted: {}", LOG_PREFIX, totalDeleted);
        break;
      }

      int deletedInBatch = postgreSQLGraphStoreService.deleteExpiredGraphs(batchSize);
      totalDeleted += deletedInBatch;

      if (deletedInBatch < batchSize) {
        log.info("{} Cleanup complete. Total deleted: {}", LOG_PREFIX, totalDeleted);
        break;
      }

      log.debug("{} Deleted {} in this batch, total so far: {}", LOG_PREFIX, deletedInBatch, totalDeleted);
    }
  }
}
