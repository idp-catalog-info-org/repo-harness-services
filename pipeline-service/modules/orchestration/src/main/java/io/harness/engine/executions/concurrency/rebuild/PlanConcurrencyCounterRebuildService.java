/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.rebuild;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Schedules the {@link PlanConcurrencyCounterRebuildJob} once every 24 hours. Registered as a
 * Dropwizard {@code Managed} only when {@code planConcurrencyRebuildJobEnabled} is true.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PlanConcurrencyCounterRebuildService implements Managed {
  private static final long INITIAL_DELAY_MINUTES = 5;
  private static final long INTERVAL_HOURS = 24;

  @Inject private PlanConcurrencyCounterRebuildJob rebuildJob;
  private ScheduledExecutorService executorService;

  @Override
  public void start() {
    executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("plan-concurrency-counter-rebuild").build());
    executorService.scheduleWithFixedDelay(
        rebuildJob, INITIAL_DELAY_MINUTES * 60, INTERVAL_HOURS * 3600, TimeUnit.SECONDS);
    log.info("Scheduled PlanConcurrencyCounterRebuildJob every {}h", INTERVAL_HOURS);
  }

  @Override
  public void stop() {
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }
}
