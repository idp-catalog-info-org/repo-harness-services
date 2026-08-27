/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** Owns the lifecycle of the bounded Private Connectivity operation executor. */
@OwnedBy(CI)
@Singleton
@Slf4j
public class PrivateConnectivityExecutorLifecycle implements Managed {
  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

  private final ExecutorService operationExecutor;

  @Inject
  public PrivateConnectivityExecutorLifecycle(
      @Named("privateConnectivityOperationExecutor") ExecutorService operationExecutor) {
    this.operationExecutor = operationExecutor;
  }

  @Override
  public void start() {
    // Executors are created eagerly by the Guice module and need no additional startup work.
  }

  @Override
  public void stop() {
    operationExecutor.shutdown();

    boolean interrupted = false;
    long deadlineNanos = System.nanoTime() + SHUTDOWN_TIMEOUT.toNanos();
    try {
      awaitUntilDeadline(operationExecutor, deadlineNanos);
    } catch (InterruptedException exception) {
      interrupted = true;
    } finally {
      forceShutdownIfNeeded("operation", operationExecutor);
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void awaitUntilDeadline(ExecutorService executor, long deadlineNanos) throws InterruptedException {
    long remainingNanos = deadlineNanos - System.nanoTime();
    if (remainingNanos > 0L) {
      executor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS);
    }
  }

  private static void forceShutdownIfNeeded(String name, ExecutorService executor) {
    if (executor.isTerminated()) {
      return;
    }
    List<Runnable> abandoned = executor.shutdownNow();
    log.warn(
        "Private Connectivity forced executor shutdown executor={} abandonedOperationCount={}", name, abandoned.size());
  }
}
