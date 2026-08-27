/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.service;

import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.LICENSE_USAGE_DAILY;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.timeStampZone;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.licenseusage.utils.LicenseUsageMetricHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class LicenseUsageMonthlyYearlyHandler implements Runnable {
  @Inject private PersistentLocker persistentLocker;
  @Inject private LicenseUsageSQLHelper licenseUsageSQLHelper;
  @Inject private MonthlyToYearlyRollupLicenseUsageDataProcessor monthlyYearlyUsageDataProcessor;
  @Inject private LicenseUsageMetricHelper metricHelper;
  private static final String LICENSE_USAGE_MONTHLY_YEARLY_JOB_FAILURE = "license_usage_monthly_yearly_job_failure";
  @Inject
  @Named("MonthlyYearlyLicenseUsageScheduler-Worker")
  private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

  private static final String LOCK_NAME = "MonthlyToYearlyRollupLicenseUsageSchedulerJobLock";

  @Override
  public void run() {
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME, Duration.ofMinutes(45))) {
      if (lock == null) {
        log.warn("Couldn't acquire lock");
        return;
      }
      processBatchOfEvents();
    } catch (Exception e) {
      log.error("Exception occurred while processing batch of events", e);
    }
  }

  public void processBatchOfEvents() {
    // Get the current date and time
    LocalDate today = LocalDate.now(timeStampZone);
    LocalDate firstDayOfMonth = today.withDayOfMonth(1);

    // Calculate the starting timestamp based on the current day
    long fromTimestamp;
    if (today.equals(firstDayOfMonth)) {
      // If today is the first day of the month, we fetch data starting from two months ago
      LocalDate twoMonthsAgo = today.minusMonths(1).withDayOfMonth(1);
      fromTimestamp = twoMonthsAgo.atStartOfDay(timeStampZone).toInstant().toEpochMilli();
    } else {
      // Otherwise, we only fetch data starting from the current month
      fromTimestamp = firstDayOfMonth.atStartOfDay(timeStampZone).toInstant().toEpochMilli();
    }

    long toTimestamp = Instant.now().toEpochMilli();
    List<String> dailyDistinctAccountIds =
        licenseUsageSQLHelper.fetchAllAccountIds(LICENSE_USAGE_DAILY, fromTimestamp, toTimestamp);

    for (String accountId : dailyDistinctAccountIds) {
      try {
        scheduledThreadPoolExecutor.submit(() -> processAccount(accountId, fromTimestamp, toTimestamp));
      } catch (Exception e) {
        log.error("Failed to submit account processing task: " + accountId, e);
        metricHelper.recordMetricForAccount(LICENSE_USAGE_MONTHLY_YEARLY_JOB_FAILURE, 1, accountId);
      }
    }
  }

  private void processAccount(String accountId, long fromTimestamp, long toTimestamp) {
    try {
      monthlyYearlyUsageDataProcessor.processDailyDataToMonthlyRollup(accountId, fromTimestamp, toTimestamp);
    } catch (Exception e) {
      log.error("Failed to process account ID: " + accountId, e);
      metricHelper.recordMetricForAccount(LICENSE_USAGE_MONTHLY_YEARLY_JOB_FAILURE, 1, accountId);
    }
  }
}
