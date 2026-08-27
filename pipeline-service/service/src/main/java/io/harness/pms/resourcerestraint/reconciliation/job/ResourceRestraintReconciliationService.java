/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.resourcerestraint.reconciliation.job;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.resourcerestraint.reconciliation.config.ResourceRestraintReconciliationConfig;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ResourceRestraintReconciliationService implements Managed {
  private Future<?> jobFuture;
  private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("resource-restraint-reconciliation-thread").build());
  private static final String LOG_CONTEXT = "[RESOURCE_RESTRAINT_RECONCILIATION]: ";
  @Inject private ResourceRestraintReconciliationJob resourceRestraintReconciliationJob;
  @Inject private ResourceRestraintReconciliationConfig resourceRestraintReconciliationConfig;

  @Override
  public void start() throws Exception {
    log.info(LOG_CONTEXT + "starting resource restraint reconciliation job...");
    jobFuture = executorService.scheduleWithFixedDelay(resourceRestraintReconciliationJob, 0,
        resourceRestraintReconciliationConfig.getIntervalInMins(), TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.info(LOG_CONTEXT + "stopping resource restraint reconciliation job...");
    jobFuture.cancel(false);
    executorService.shutdownNow();
  }
}
