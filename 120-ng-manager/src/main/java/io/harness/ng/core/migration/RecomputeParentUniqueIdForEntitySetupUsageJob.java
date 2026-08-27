/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class RecomputeParentUniqueIdForEntitySetupUsageJob implements Managed {
  private Future<?> uniqueIdParentIdForEntityJobFuture;
  private final ScheduledExecutorService executorService;
  private final RecomputeParentUniqueIdForEntitySetupUsageTask recomputeParentUniqueIdForEntitySetupUsageTask;
  private final FeatureFlagService featureFlagService;
  private static final String LOG_PREFIX = "[RecomputeParentUniqueIdForEntitySetupUsageJob]:";
  private static final long INITIAL_DELAY_MINUTES = 60; // 1 hour
  private static final long INTERVAL_MINUTES = 1440; // 24 hours

  @Inject
  public RecomputeParentUniqueIdForEntitySetupUsageJob(
      RecomputeParentUniqueIdForEntitySetupUsageTask recomputeParentUniqueIdForEntitySetupUsageTask,
      FeatureFlagService featureFlagService) {
    this.recomputeParentUniqueIdForEntitySetupUsageTask = recomputeParentUniqueIdForEntitySetupUsageTask;
    this.featureFlagService = featureFlagService;
    this.executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("recompute-parentId-for-entity-setup-usage").build());
  }

  @Override
  public void start() throws Exception {
    if (featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_RECOMPUTATION_FOR_ENTITY_SETUP_USAGE)) {
      log.info(format("%s Job disabled by feature flag: %s", LOG_PREFIX,
          FeatureName.PIPE_DISABLE_RECOMPUTATION_FOR_ENTITY_SETUP_USAGE));
      return;
    }
    log.info(format("%s Job starting - will run every %d minutes (initial delay: %d minute)", LOG_PREFIX,
        INTERVAL_MINUTES, INITIAL_DELAY_MINUTES));
    uniqueIdParentIdForEntityJobFuture = executorService.scheduleWithFixedDelay(
        recomputeParentUniqueIdForEntitySetupUsageTask, INITIAL_DELAY_MINUTES, INTERVAL_MINUTES, TimeUnit.MINUTES);
    log.info(format("%s Job scheduled successfully - next run in %d minute", LOG_PREFIX, INITIAL_DELAY_MINUTES));
  }

  @Override
  public void stop() throws Exception {
    log.info(format("%s Job stopping - cancelling scheduled tasks", LOG_PREFIX));
    if (uniqueIdParentIdForEntityJobFuture != null) {
      boolean cancelled = uniqueIdParentIdForEntityJobFuture.cancel(false);
      log.info(format("%s Scheduled task cancellation: %s", LOG_PREFIX, cancelled ? "successful" : "failed"));
    }
    executorService.shutdownNow();
    log.info(format("%s Job stopped - executor service shutdown", LOG_PREFIX));
  }
}
