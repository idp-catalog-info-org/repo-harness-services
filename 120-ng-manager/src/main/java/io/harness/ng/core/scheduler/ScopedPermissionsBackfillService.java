/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.config.ScopedPermissionsBackfillConfig;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class ScopedPermissionsBackfillService implements Managed {
  private Future<?> future;
  private final ScheduledExecutorService executorService;
  private static final String DEBUG_MESSAGE = "ScopedPermissionsBackfillService: ";
  private final ScopedPermissionsBackfillJob job;
  private final ScopedPermissionsBackfillConfig config;

  @Inject
  public ScopedPermissionsBackfillService(ScopedPermissionsBackfillJob job,
      @Named("scopedPermissionsBackfillConfig") ScopedPermissionsBackfillConfig config) {
    this.job = job;
    this.config = config;
    String threadName = "scoped-permissions-backfill-job";
    this.executorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat(threadName).build());
  }

  @Override
  public void start() throws Exception {
    if (Boolean.TRUE.equals(config.getDisableJob())) {
      log.info(DEBUG_MESSAGE + "scoped permissions backfill job is disabled via config. Skipping start.");
      return;
    }

    log.info(DEBUG_MESSAGE + "started...");
    Random random = new Random();
    // Add jitter (0-900s) on top of the configured initial delay so that multi-replica
    // ng-manager pods don't all fire at exactly the same moment after a deploy.
    long delay = random.nextInt(901) + config.getInitialDelayInSeconds();
    future = executorService.scheduleWithFixedDelay(job, delay, config.getDelayInSeconds(), TimeUnit.SECONDS);
  }

  @Override
  public void stop() throws Exception {
    log.info(DEBUG_MESSAGE + "stopping...");
    if (future != null) {
      future.cancel(false);
    }
    executorService.shutdown();
  }
}
