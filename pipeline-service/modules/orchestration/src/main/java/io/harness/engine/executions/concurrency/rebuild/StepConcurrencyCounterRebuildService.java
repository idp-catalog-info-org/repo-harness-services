/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.rebuild;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** Schedules {@link StepConcurrencyCounterRebuildJob} to run once every 24 hours. */
@OwnedBy(PIPELINE)
@Slf4j
public class StepConcurrencyCounterRebuildService implements Managed {
  private static final String LOG_CONTEXT = "[STEP_CONCURRENCY_REBUILD]: ";
  private static final long INTERVAL_HOURS = 24;

  private Future<?> jobFuture;
  private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("step-concurrency-counter-rebuild-thread").build());

  @Inject private StepConcurrencyCounterRebuildJob rebuildJob;

  @Override
  public void start() throws Exception {
    log.info(LOG_CONTEXT + "starting step-concurrency counter rebuild job...");
    jobFuture = executorService.scheduleWithFixedDelay(rebuildJob, 0, INTERVAL_HOURS, TimeUnit.HOURS);
  }

  @Override
  public void stop() throws Exception {
    log.info(LOG_CONTEXT + "stopping step-concurrency counter rebuild job...");
    if (jobFuture != null) {
      jobFuture.cancel(false);
    }
    executorService.shutdownNow();
  }
}
