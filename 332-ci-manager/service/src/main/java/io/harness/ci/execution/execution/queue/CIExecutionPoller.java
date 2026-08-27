/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.queue;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIExecutionPoller implements Managed {
  @Inject(optional = true) CIInitPollerUtils executionPollerUtils;
  private AtomicBoolean shouldStop = new AtomicBoolean(false);

  @Override
  public void start() {
    ExecutorService executorService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat(executionPollerUtils.getModuleName() + "-queue-poller").build());
    executorService.execute(this::run);
  }
  public void run() {
    log.info("Started the Consumer {} for {}", this.getClass().getSimpleName(), executionPollerUtils.getModuleName());

    try {
      do {
        while (getMaintenanceFlag()) {
          sleep(ofSeconds(1));
        }
        executionPollerUtils.readEventsFrameworkMessages();
      } while (!Thread.currentThread().isInterrupted() && !shouldStop.get());
    } catch (Exception ex) {
      log.error("hsqs Consumer unexpectedly stopped", ex);
    } finally {
      log.info("finished consuming messages for {} init task", executionPollerUtils.getModuleName());
    }
  }

  @Override
  public void stop() throws Exception {}
}
