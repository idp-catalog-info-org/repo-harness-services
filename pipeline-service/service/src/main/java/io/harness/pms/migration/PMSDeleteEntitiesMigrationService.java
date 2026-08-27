/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.migration;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class PMSDeleteEntitiesMigrationService implements Managed {
  private Future<?> jobFuture;
  private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("pms-delete-entities-migration-service-thread").build());
  private static final String DEBUG_LOG = "[PMSDeleteEntitiesMigration]: ";
  @Inject private PMSDeleteEntitiesMigration pmsDeleteEntitiesMigration;

  @Override
  public void start() throws Exception {
    log.info(DEBUG_LOG + "starting migration job...");
    jobFuture = executorService.scheduleWithFixedDelay(pmsDeleteEntitiesMigration, 0, 1440, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.info(DEBUG_LOG + "stopping migration job...");
    jobFuture.cancel(false);
    executorService.shutdownNow();
  }
}
