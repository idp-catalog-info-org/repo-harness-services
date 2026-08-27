/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.queue;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * Managed lifecycle poller that continuously drains the Postgres {@code plan_creation_queue}.
 * Registered only when {@code useDbQueueForPlanCreation} is true (see PipelineServiceApplication).
 * During the cutover overlap window both this and the hsqs {@link PlanCreationQueuePoller} may run
 * — the idempotent status flip inside processing keeps that safe.
 */
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanCreationDbQueuePoller implements Managed {
  private static final long IDLE_SLEEP_MILLIS = 500L;
  @Inject private PlanCreationDbQueueDrainer planCreationDbQueueDrainer;
  private final AtomicBoolean shouldStop = new AtomicBoolean(false);

  @Override
  public void start() {
    ExecutorService executorService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat("plan-creation-db-queue-poller").build());
    executorService.execute(this::run);
  }

  public void run() {
    log.info("Started the Postgres plan-creation queue drainer");
    try {
      do {
        while (getMaintenanceFlag()) {
          sleep(ofSeconds(1));
        }
        int processed;
        try {
          processed = planCreationDbQueueDrainer.drainOnce();
        } catch (Exception ex) {
          log.error("plan-creation db queue drain iteration failed; retrying", ex);
          processed = 0;
        }
        // Back off only when the queue is empty; otherwise loop immediately to drain fast.
        // NOTE: we intentionally do NOT gate this loop on Redis health — the gate reads fail-open
        // (return 0) so a Redis outage means "process every candidate", which is exactly what we
        // want: never block executions because of a monitoring/counter dep. Redis outage is
        // surfaced via the rate-limited WARN in PlanConcurrencyCounterServiceImpl.awaitRead and
        // the isHealthy() metric on that service.
        if (processed == 0) {
          sleep(ofMillis(IDLE_SLEEP_MILLIS));
        }
      } while (!Thread.currentThread().isInterrupted() && !shouldStop.get());
    } catch (Exception ex) {
      log.error("plan-creation db queue poller unexpectedly stopped", ex);
    } finally {
      log.info("finished draining plan-creation db queue");
    }
  }

  @Override
  public void stop() {
    shouldStop.set(true);
  }
}
