/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.projectmovement.mongo;

import static io.harness.beans.FeatureName.PIPE_DISABLE_MONGO_CDC_MIGRATION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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
public class AddUniqueIdParentIdToCdcEntitiesJob implements Managed {
  private Future<?> uniqueIdParentIdForCdcEntitiesJobFuture;
  private final ScheduledExecutorService executorService;
  private final AddUniqueIdParentIdToCdcEntitiesTask cdcEntitiesTask;
  private final FeatureFlagService featureFlagService;
  private static final String DEBUG_MESSAGE = "AddUniqueIdParentIdToCdcEntitiesJob ";

  @Inject
  public AddUniqueIdParentIdToCdcEntitiesJob(
      AddUniqueIdParentIdToCdcEntitiesTask cdcEntitiesTask, FeatureFlagService featureFlagService) {
    this.cdcEntitiesTask = cdcEntitiesTask;
    this.featureFlagService = featureFlagService;
    this.executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("pipeline-uniqueId-parentId-for-cdc-entities").build());
  }

  @Override
  public void start() throws Exception {
    if (featureFlagService.isGlobalEnabled(PIPE_DISABLE_MONGO_CDC_MIGRATION)) {
      log.info(String.format(
          "The FF %s is enabled, skipping the mongo CDC migration job", PIPE_DISABLE_MONGO_CDC_MIGRATION.name()));
    } else {
      log.info(DEBUG_MESSAGE + "started...");
      uniqueIdParentIdForCdcEntitiesJobFuture =
          executorService.scheduleWithFixedDelay(cdcEntitiesTask, 1, 2880, TimeUnit.MINUTES);
    }
  }

  @Override
  public void stop() throws Exception {
    if (uniqueIdParentIdForCdcEntitiesJobFuture != null) {
      uniqueIdParentIdForCdcEntitiesJobFuture.cancel(false);
    }
    log.info(DEBUG_MESSAGE + "stopping...");
    executorService.shutdownNow();
  }
}
