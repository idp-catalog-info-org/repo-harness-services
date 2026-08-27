/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.LICENSE_USAGE_DAILY;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.LICENSE_USAGE_MONTHLY;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.LICENSE_USAGE_YEARLY;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.getStartOfMonthUtc;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.getStartOfYearUtc;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.timeStampZone;
import static io.harness.ng.core.event.service.LicenseUsageScheduler.NOS_OF_SEMAPHORES_FOR_MONTHLY_YEARLY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.licenseusage.entities.LicenseUsageActivityData;
import io.harness.ng.core.licenseusage.utils.LicenseUsageMetricHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Singleton
@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class MonthlyToYearlyRollupLicenseUsageDataProcessor {
  private final LicenseUsageSQLHelper licenseUsageSQLHelper;
  private final LicenseUsageMetricHelper metricHelper;
  private static final String LICENSE_USAGE_MONTHLY_YEARLY_PROCESSED_COUNT =
      "license_usage_monthly_yearly_processed_count";

  private static final Semaphore semaphore = new Semaphore(NOS_OF_SEMAPHORES_FOR_MONTHLY_YEARLY, true);
  public void processDailyDataToMonthlyRollup(String accountIdentifier, long fromTimestamp, long toTimestamp) {
    try {
      // Acquire a permit before proceeding
      semaphore.acquire();

      LocalDate today = LocalDate.now(timeStampZone);
      LocalDate firstDayOfYear = today.withDayOfYear(1);

      long fromFirstDayOfTheYear = firstDayOfYear.atStartOfDay(timeStampZone).toInstant().toEpochMilli();

      List<LicenseUsageActivityData> dailyLicenseUsageActivityData = licenseUsageSQLHelper.fetchRecordsFromPostgres(
          LICENSE_USAGE_DAILY, accountIdentifier, fromTimestamp, toTimestamp);

      computeAndUpdateMonthlyLicenseUsageData(dailyLicenseUsageActivityData);
      metricHelper.recordMetricForAccount(
          LICENSE_USAGE_MONTHLY_YEARLY_PROCESSED_COUNT, dailyLicenseUsageActivityData.size(), accountIdentifier);

      List<LicenseUsageActivityData> monthlyLicenseUsageActivityData = licenseUsageSQLHelper.fetchRecordsFromPostgres(
          LICENSE_USAGE_MONTHLY, accountIdentifier, fromFirstDayOfTheYear, toTimestamp);

      computeAndUpdateYearlyLicenseUsageData(monthlyLicenseUsageActivityData);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Set the interrupt flag again
      log.error("Thread was interrupted, failed to complete operation");
    } finally {
      // Always release the semaphore
      semaphore.release();
    }
  }

  /**
   * Computes and updates the monthly license usage data by aggregating daily license usage records.
   *
   * @param dailyLicenseUsageActivityData A list of daily license usage activity data records.
   *
   * This method groups the daily records by the start of the month and other identifiers such as account, organization,
   * project, pipeline, stage, OS type, resource class, and module type. It then sums the used credits for each group to
   * compute the total monthly credits. The resulting monthly record is then saved to the LICENSE_USAGE_MONTHLY table.
   */
  public void computeAndUpdateMonthlyLicenseUsageData(List<LicenseUsageActivityData> dailyLicenseUsageActivityData) {
    // Aggregate monthly credits and also preserve daily records for reference
    Map<AggregationKey, List<LicenseUsageActivityData>> monthlyDataMap =
        dailyLicenseUsageActivityData.stream().collect(Collectors.groupingBy(daily
            -> new AggregationKey(getStartOfMonthUtc(daily.getUtcTimestamp()), daily.getAccountIdentifier(),
                daily.getOrganizationIdentifier(), daily.getProjectIdentifier(), daily.getPipelineIdentifier(),
                daily.getStageIdentifier(), daily.getCiOsType(), daily.getCiResourceClass(), daily.getModuleType())));

    // Process each month's data
    for (Map.Entry<AggregationKey, List<LicenseUsageActivityData>> entry : monthlyDataMap.entrySet()) {
      AggregationKey aggregationKey = entry.getKey();
      int totalMonthlyCredits = entry.getValue().stream().mapToInt(LicenseUsageActivityData::getUsedCredits).sum();

      LicenseUsageActivityData monthlyRecord = LicenseUsageActivityData.builder()
                                                   .accountIdentifier(aggregationKey.getAccountIdentifier())
                                                   .organizationIdentifier(aggregationKey.getOrganizationIdentifier())
                                                   .projectIdentifier(aggregationKey.getProjectIdentifier())
                                                   .pipelineIdentifier(aggregationKey.getPipelineIdentifier())
                                                   .stageIdentifier(aggregationKey.getStageIdentifier())
                                                   .ciOsType(aggregationKey.getCiOsType())
                                                   .ciResourceClass(aggregationKey.getCiResourceClass())
                                                   .moduleType(aggregationKey.getModuleType())
                                                   .usedCredits(totalMonthlyCredits)
                                                   .utcTimestamp(aggregationKey.getUtcTimestamp())
                                                   .build();

      licenseUsageSQLHelper.saveLicenseUsageActivityData(LICENSE_USAGE_MONTHLY, monthlyRecord);
    }
  }

  /**
   * Computes and updates the yearly license usage data by aggregating monthly license usage records.
   *
   * @param monthlyLicenseUsageActivityData A list of monthly license usage activity data records.
   *
   * This method groups the monthly records by the start of the year and other identifiers such as account,
   * organization, project, pipeline, stage, OS type, resource class, and module type. It then sums the used credits for
   * each group to compute the total yearly credits. The resulting yearly record is then saved to the
   * LICENSE_USAGE_YEARLY table.
   */
  public void computeAndUpdateYearlyLicenseUsageData(List<LicenseUsageActivityData> monthlyLicenseUsageActivityData) {
    Map<AggregationKey, List<LicenseUsageActivityData>> yearlyDataMap =
        monthlyLicenseUsageActivityData.stream().collect(Collectors.groupingBy(monthly
            -> new AggregationKey(getStartOfYearUtc(monthly.getUtcTimestamp()), monthly.getAccountIdentifier(),
                monthly.getOrganizationIdentifier(), monthly.getProjectIdentifier(), monthly.getPipelineIdentifier(),
                monthly.getStageIdentifier(), monthly.getCiOsType(), monthly.getCiResourceClass(),
                monthly.getModuleType())));

    // Process each year's data
    for (Map.Entry<AggregationKey, List<LicenseUsageActivityData>> entry : yearlyDataMap.entrySet()) {
      AggregationKey aggregationKey = entry.getKey();
      int totalYearlyCredits = entry.getValue().stream().mapToInt(LicenseUsageActivityData::getUsedCredits).sum();

      LicenseUsageActivityData yearlyRecord = LicenseUsageActivityData.builder()
                                                  .accountIdentifier(aggregationKey.getAccountIdentifier())
                                                  .organizationIdentifier(aggregationKey.getOrganizationIdentifier())
                                                  .projectIdentifier(aggregationKey.getProjectIdentifier())
                                                  .pipelineIdentifier(aggregationKey.getPipelineIdentifier())
                                                  .stageIdentifier(aggregationKey.getStageIdentifier())
                                                  .ciOsType(aggregationKey.getCiOsType())
                                                  .ciResourceClass(aggregationKey.getCiResourceClass())
                                                  .moduleType(aggregationKey.getModuleType())
                                                  .usedCredits(totalYearlyCredits)
                                                  .utcTimestamp(aggregationKey.getUtcTimestamp())
                                                  .build();

      licenseUsageSQLHelper.saveLicenseUsageActivityData(LICENSE_USAGE_YEARLY, yearlyRecord);
    }
  }
}
