/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cleanup;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cleanup.config.OrchestrationGraphCacheCleanupConfig;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_DATA_RETENTION})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class OrchestrationGraphCacheCleanupService implements Managed {
  private Future<?> jobFuture;
  private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("orchestration-graph-cache-cleanup-thread").build());
  private static final String LOG_PREFIX = "[ORCHESTRATION_GRAPH_CACHE_CLEANUP]";

  @Inject private OrchestrationGraphCacheCleanupJob orchestrationGraphCacheCleanupJob;
  @Inject private OrchestrationGraphCacheCleanupConfig config;

  @Override
  public void start() throws Exception {
    log.info("{} Starting orchestration graph cache cleanup service...", LOG_PREFIX);
    jobFuture = executorService.scheduleWithFixedDelay(
        orchestrationGraphCacheCleanupJob, 0, config.getCleanUpIntervalMinutes(), TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.info("{} Stopping orchestration graph cache cleanup service...", LOG_PREFIX);
    if (jobFuture != null) {
      jobFuture.cancel(false);
    }
    executorService.shutdownNow();
  }
}
