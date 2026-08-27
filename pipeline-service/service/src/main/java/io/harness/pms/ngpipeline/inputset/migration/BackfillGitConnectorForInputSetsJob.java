/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.migration;

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

/**
 * Managed job that schedules the BackfillGitConnectorForInputSetsTask.
 * Runs periodically until all remote input sets have their Git connector references
 * published to entitySetupUsage.
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class BackfillGitConnectorForInputSetsJob implements Managed {
  private static final String LOG_PREFIX = "[BackfillGitConnectorForInputSetsJob]:";

  private static final long INITIAL_DELAY_MINUTES = 70L;
  private static final long FREQUENCY_MINUTES = 15L;

  private volatile Future<?> jobFuture;
  private final ScheduledExecutorService executorService;
  private final BackfillGitConnectorForInputSetsTask task;
  private final FeatureFlagService featureFlagService;

  @Inject
  public BackfillGitConnectorForInputSetsJob(
      BackfillGitConnectorForInputSetsTask task, FeatureFlagService featureFlagService) {
    this.task = task;
    this.featureFlagService = featureFlagService;
    this.executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat("pipeline-service-backfill-inputset-git-connector-setup-usage")
            .build());
  }

  @Override
  public void start() {
    if (featureFlagService.isGlobalEnabled(FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION)) {
      log.info(format("%s Job disabled by feature flag: %s", LOG_PREFIX,
          FeatureName.PIPE_DISABLE_INPUTSET_CONNECTOR_BACKFILL_MIGRATION));
      return;
    }
    log.info(LOG_PREFIX + "started...");
    jobFuture =
        executorService.scheduleWithFixedDelay(task, INITIAL_DELAY_MINUTES, FREQUENCY_MINUTES, TimeUnit.MINUTES);
  }

  @Override
  public void stop() {
    log.info(format("%s Job stopping - cancelling scheduled tasks", LOG_PREFIX));
    if (jobFuture != null) {
      jobFuture.cancel(false);
    }
    executorService.shutdownNow();
    log.info(format("%s Job stopped - executor service shutdown", LOG_PREFIX));
  }
}
