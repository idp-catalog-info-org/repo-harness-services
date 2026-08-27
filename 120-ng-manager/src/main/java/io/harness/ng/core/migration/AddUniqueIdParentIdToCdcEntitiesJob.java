/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.config.NextGenConfiguration;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AddUniqueIdParentIdToCdcEntitiesJob implements Managed {
  private Future<?> cdcEntityuniqueIdParentIdForEntityJobFuture;
  private final ScheduledExecutorService executorService;
  private final AddUniqueIdParentIdToCdcEntitiesTask cdcEntitiesTask;
  private final NextGenConfiguration configuration;
  private static final String DEBUG_MESSAGE = "AddMissingUniqueIdParentIdForCDCEntitiesJob ";

  @Inject
  public AddUniqueIdParentIdToCdcEntitiesJob(
      AddUniqueIdParentIdToCdcEntitiesTask cdcEntitiesTask, NextGenConfiguration configuration) {
    this.cdcEntitiesTask = cdcEntitiesTask;
    this.executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("add-missing-uniqueId-parentId-for-cdc-entities").build());
    this.configuration = configuration;
  }

  @Override
  public void start() throws Exception {
    log.info(DEBUG_MESSAGE + "started...");
    if (configuration.isEnableCdcMigrationByCreatingChangeEventsInMongo()) {
      executorService.scheduleWithFixedDelay(cdcEntitiesTask, 1, 2880, TimeUnit.MINUTES);
    } else {
      log.info(DEBUG_MESSAGE + "CDC migration is disabled. Not starting the job.");
    }
  }

  @Override
  public void stop() throws Exception {
    cdcEntityuniqueIdParentIdForEntityJobFuture.cancel(false);
    log.info(DEBUG_MESSAGE + "stopping...");
    executorService.shutdownNow();
  }
}
