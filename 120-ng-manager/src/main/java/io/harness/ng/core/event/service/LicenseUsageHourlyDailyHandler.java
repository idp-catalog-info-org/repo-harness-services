/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.service;

import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.timeStampZone;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.licenseusage.entities.LicenseUsage;
import io.harness.ng.core.licenseusage.entities.LicenseUsage.LicenseUsageKeys;
import io.harness.ng.core.licenseusage.utils.LicenseUsageMetricHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.ReadPreference;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Singleton
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class LicenseUsageHourlyDailyHandler implements Runnable {
  @Inject private HourlyToDailyRollUpLicenseUsageDataProcessor licenseUsageDataProcessor;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private PersistentLocker persistentLocker;
  @Inject private LicenseUsageMetricHelper metricHelper;
  private static final String LICENSE_USAGE_HOURLY_DAILY_JOB_FAILURE = "license_usage_hourly_daily_job_failure";
  private static final String LICENSE_USAGE_UNPROCESSED_ACCOUNT_COUNT = "license_usage_unprocessed_account_count";
  private static final String ALL_ACCOUNTS = "__all__";
  @Inject
  @Named("HourlyDailyLicenseUsageScheduler-Worker")
  private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
  private static final String LOCK_NAME = "HourlyToDailyRollupLicenseUsageScheduleJobLock";
  private long HOURLY_DAILY_JOB_BATCH_SIZE = 100;

  public LicenseUsageHourlyDailyHandler(long cloudCreditsHourlyJobBatchSize) {
    HOURLY_DAILY_JOB_BATCH_SIZE = cloudCreditsHourlyJobBatchSize;
  }

  @Override
  public void run() {
    log.info("LicenseUsageHourlyDailyHandler job started");
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME, Duration.ofMinutes(45))) {
      if (lock == null) {
        log.warn("Couldn't acquire lock");
        return;
      }
      processBatchOfEvents();
    } catch (Exception e) {
      log.error("Exception occurred while processing batch of events", e);
    }
    log.info("LicenseUsageHourlyDailyHandler job completed");
  }

  public void processBatchOfEvents() {
    Query query = new Query();
    query.addCriteria(Criteria.where(LicenseUsageKeys.isProcessed).is(false));
    query.withReadPreference(
        ReadPreference.secondaryPreferred()); // this will offload primary but also focus on availability
    List<String> unProcessedAccountIds =
        mongoTemplate.findDistinct(query, LicenseUsageKeys.accountIdentifier, LicenseUsage.class, String.class);
    metricHelper.recordMetricForAccount(
        LICENSE_USAGE_UNPROCESSED_ACCOUNT_COUNT, unProcessedAccountIds.size(), ALL_ACCOUNTS);

    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    // Calculate the start of the previous day
    ZonedDateTime startOfPreviousDay = zonedDateTime.toLocalDate().minusDays(1).atStartOfDay(timeStampZone);
    long fromTimestamp = startOfPreviousDay.toInstant().toEpochMilli();

    // Get the current time as epoch milliseconds
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();

    for (String accountIdBeingProcessed : unProcessedAccountIds) {
      try {
        scheduledThreadPoolExecutor.submit(() -> processAccount(accountIdBeingProcessed, fromTimestamp, toTimestamp));
      } catch (Exception e) {
        log.error("Failed to submit account processing task: " + accountIdBeingProcessed, e);
        metricHelper.recordMetricForAccount(LICENSE_USAGE_HOURLY_DAILY_JOB_FAILURE, 1, accountIdBeingProcessed);
      }
    }
  }

  public void processAccount(String accountIdBeingProcessed, long fromTimestamp, long toTimestamp) {
    try {
      licenseUsageDataProcessor.processLicenseUsageEvent(
          accountIdBeingProcessed, fromTimestamp, toTimestamp, HOURLY_DAILY_JOB_BATCH_SIZE);
    } catch (Exception e) {
      log.error("Failed to process account ID: " + accountIdBeingProcessed, e);
      metricHelper.recordMetricForAccount(LICENSE_USAGE_HOURLY_DAILY_JOB_FAILURE, 1, accountIdBeingProcessed);
    }
  }
}
