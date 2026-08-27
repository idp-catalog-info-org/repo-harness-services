/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class TriggersMigrationService implements Managed {
  private Future<?> optimizedS3TriggersMigrationFuture;
  private final ScheduledExecutorService optimizedS3TriggersMigrationExecutorService =
      Executors.newSingleThreadScheduledExecutor(
          new ThreadFactoryBuilder().setNameFormat("trigger-optimized-s3-migration-service-thread").build());
  private static final String DEBUG_MESSAGE = "TriggersMigrationService: ";
  @Inject private OptimizedS3TriggersMigration optimizedS3TriggersMigration;

  @Override
  public void start() throws Exception {
    log.info(DEBUG_MESSAGE + "started...");
    optimizedS3TriggersMigrationFuture = optimizedS3TriggersMigrationExecutorService.scheduleWithFixedDelay(
        optimizedS3TriggersMigration, 0, 60, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.info(DEBUG_MESSAGE + "stopping...");
    optimizedS3TriggersMigrationFuture.cancel(false);
    optimizedS3TriggersMigrationExecutorService.shutdown();
  }
}
