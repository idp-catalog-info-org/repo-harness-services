/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

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
public class AddParentUniqueIdForTimescaleTableJob implements Managed {
  private Future<?> uniqueIdParentIdForEntityJobFuture;
  private final ScheduledExecutorService executorService;
  private final AddUniqueIdParentUniqueIdForTimescaleCollections scopeEntitiesTask;
  private final NextGenConfiguration configuration;
  private static final String DEBUG_MESSAGE = "AddMissingUniqueIdParentIdForTsdbEntitiesJob ";

  @Inject
  public AddParentUniqueIdForTimescaleTableJob(
      AddUniqueIdParentUniqueIdForTimescaleCollections scopeEntitiesTask, NextGenConfiguration configuration) {
    this.scopeEntitiesTask = scopeEntitiesTask;
    this.executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("add-parentId-for-entities-tsdb").build());
    this.configuration = configuration;
  }
  @Override
  public void start() throws Exception {
    log.info(DEBUG_MESSAGE + "started...");
    if (configuration.isEnableTsdbMigrationForParentUniqueId()) {
      uniqueIdParentIdForEntityJobFuture =
          executorService.scheduleWithFixedDelay(scopeEntitiesTask, 10, 1440, TimeUnit.MINUTES);
    } else {
      log.info(DEBUG_MESSAGE + "Timescale migration for parent unique id is disabled. Not starting the job.");
    }
  }
  @Override
  public void stop() throws Exception {
    uniqueIdParentIdForEntityJobFuture.cancel(false);
    log.info(DEBUG_MESSAGE + "stopping...");
    executorService.shutdownNow();
  }
}