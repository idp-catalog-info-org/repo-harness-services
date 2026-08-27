/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.projectmovement.elastic;

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

@CodePulse(module = ProductModule.PL, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class AddUniqueIdParentIdToElasticEntitiesJob implements Managed {
  //  TODO: @Adithya once the job is successfully run, remove all this job
  private Future<?> uniqueIdParentIdForEntityJobFuture;
  private final ScheduledExecutorService executorService;
  private final AddUniqueIdParentIdToEntitiesElasticsearchTask addUniqueIdParentIdToEntitiesElasticsearchTask;
  private static final String DEBUG_MESSAGE = "AddMissingUniqueIdParentIdForElasticEntitiesJob ";

  @Inject
  public AddUniqueIdParentIdToElasticEntitiesJob(
      AddUniqueIdParentIdToEntitiesElasticsearchTask addUniqueIdParentIdToEntitiesElasticsearchTask) {
    this.addUniqueIdParentIdToEntitiesElasticsearchTask = addUniqueIdParentIdToEntitiesElasticsearchTask;
    this.executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("pipeline-uniqueId-parentId-for-elastic-entities").build());
  }

  @Override
  public void start() throws Exception {
    log.info(DEBUG_MESSAGE + "started...");
    uniqueIdParentIdForEntityJobFuture = executorService.scheduleWithFixedDelay(
        addUniqueIdParentIdToEntitiesElasticsearchTask, 1, 120, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    uniqueIdParentIdForEntityJobFuture.cancel(false);
    log.info(DEBUG_MESSAGE + "stopping...");
    executorService.shutdownNow();
  }
}