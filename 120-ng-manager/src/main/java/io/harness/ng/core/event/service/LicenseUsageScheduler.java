/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Singleton;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class LicenseUsageScheduler {
  private static long HOURLY_DAILY_INITIAL_DELAY_SCHEDULE = 1;
  private static long MONTHLY_YEARLY_INITIAL_DELAY_SCHEDULE = 5;
  private static long MONTHLY_YEARLY_JOB_SCHEDULE = 1440;
  private static long NOS_OF_WORKER_THREADS_MONTHLY_YEARLY = 2;
  protected static int NOS_OF_SEMAPHORES_FOR_MONTHLY_YEARLY = calculateSemaphores(NOS_OF_WORKER_THREADS_MONTHLY_YEARLY);
  private long HOURLY_DAILY_JOB_SCHEDULE = 5;

  public LicenseUsageScheduler(long cloudCreditsHourlyJobSchedule) {
    HOURLY_DAILY_JOB_SCHEDULE = cloudCreditsHourlyJobSchedule;
  }

  private static int calculateSemaphores(long workerThreads) {
    // Ensure the calculated number of semaphores does not exceed Integer.MAX_VALUE
    long semaphores = workerThreads > 3 ? (long) Math.ceil(workerThreads / 2.0) : workerThreads;
    if (semaphores > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "The calculated number of semaphores exceeds the maximum allowed value for an int.");
    }
    return (int) semaphores; // Safely cast to int after ensuring it's within range
  }

  public void initialize(int poolSize1, int poolSize2, LicenseUsageHourlyDailyHandler licenseUsageHourlyDailyHandler,
      LicenseUsageMonthlyYearlyHandler licenseUsageMonthlyYearlyHandler) {
    log.info("Running the license usage scheduler");

    ScheduledThreadPoolExecutor mainHourlyDailyExecutor = new ScheduledThreadPoolExecutor(
        1, new ThreadFactoryBuilder().setNameFormat("HourlyDailyLicenseUsageScheduler-Main").build());

    ScheduledThreadPoolExecutor mainMonthlyYearlyExecutor = new ScheduledThreadPoolExecutor(
        1, new ThreadFactoryBuilder().setNameFormat("MonthlyYearlyLicenseUsageScheduler-Main").build());

    mainHourlyDailyExecutor.scheduleAtFixedRate(licenseUsageHourlyDailyHandler, HOURLY_DAILY_INITIAL_DELAY_SCHEDULE,
        HOURLY_DAILY_JOB_SCHEDULE, TimeUnit.MINUTES);

    mainMonthlyYearlyExecutor.scheduleAtFixedRate(licenseUsageMonthlyYearlyHandler,
        MONTHLY_YEARLY_INITIAL_DELAY_SCHEDULE, MONTHLY_YEARLY_JOB_SCHEDULE, TimeUnit.MINUTES);
  }
}
