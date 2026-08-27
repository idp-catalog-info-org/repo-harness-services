/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsubscriptions.service.jobs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class DailyAccountUsersEntityUpdateJob implements Managed {
  private Future<?> jobFuture;
  private final ScheduledExecutorService executorService;

  private final DailyAccountUsersEntityUpdater dailyAccountUsersEntityUpdater;
  private final long initialDelayInMinutes;

  public static final String JOB_NAME = "dailyAccountUsersEntityUpdateJob";

  @Inject
  public DailyAccountUsersEntityUpdateJob(DailyAccountUsersEntityUpdater dailyAccountUsersEntityUpdater,
      @Named("dailyAccountUsersJobInitialDelayInMinutes") long initialDelayInMinutes) {
    String threadName = "daily-account-users-entity-updater";
    this.dailyAccountUsersEntityUpdater = dailyAccountUsersEntityUpdater;
    this.initialDelayInMinutes = initialDelayInMinutes;
    this.executorService =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat(threadName).build());
  }

  @Override
  public void start() throws Exception {
    log.info(JOB_NAME + " started with initialDelayInMinutes: " + initialDelayInMinutes);
    jobFuture = executorService.scheduleWithFixedDelay(
        dailyAccountUsersEntityUpdater, initialDelayInMinutes, 1440, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    log.info(JOB_NAME + " stopping...");
    jobFuture.cancel(false);
    executorService.shutdownNow();
  }
}
