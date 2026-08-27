/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.config.ServiceUniqueIdBackfillConfig;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ServiceUniqueIdBackfillJob implements Managed {
  private Future<?> jobFuture;
  private final ScheduledExecutorService executorService;
  private static final String DEBUG_MESSAGE = "ServiceUniqueIdBackfillJob: ";
  private final ServiceUniqueIdBackfillTask task;

  @Inject @Named("serviceUniqueIdBackfillJobConfig") private ServiceUniqueIdBackfillConfig config;

  @Inject
  public ServiceUniqueIdBackfillJob(ServiceUniqueIdBackfillTask task) {
    this.task = task;
    String threadName = "service-unique-id-backfill-thread";
    this.executorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat(threadName).build());
  }

  @Override
  public void start() {
    if (config.isDisabled()) {
      log.info(DEBUG_MESSAGE + "Service unique ID backfill job is disabled via config. Skipping start.");
      return;
    }

    log.info(DEBUG_MESSAGE + "started...");
    jobFuture = executorService.scheduleWithFixedDelay(
        task, config.getInitialDelayInMinutes(), config.getIntervalInMinutes(), TimeUnit.MINUTES);
  }

  @Override
  public void stop() {
    log.info(DEBUG_MESSAGE + "stopping...");
    if (jobFuture != null) {
      jobFuture.cancel(false);
    }
    executorService.shutdownNow();
  }
}
