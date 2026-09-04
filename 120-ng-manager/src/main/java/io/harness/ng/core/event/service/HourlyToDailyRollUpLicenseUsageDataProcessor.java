/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.LICENSE_USAGE_DAILY;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.LICENSE_USAGE_HOURLY;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.getStartOfDayUtc;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.timeStampZone;

import io.harness.ArchitectureType;
import io.harness.CreditType;
import io.harness.ModuleType;
import io.harness.OSType;
import io.harness.ResourceClass;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.CreditOverUsageEntity;
import io.harness.credit.entities.CreditOverUsageEntity.CreditOverUsageKeys;
import io.harness.credit.services.CreditService;
import io.harness.credit.utils.CreditStatus;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.licenseusage.entities.CILicenseUsage;
import io.harness.ng.core.licenseusage.entities.LicenseUsage;
import io.harness.ng.core.licenseusage.entities.LicenseUsage.LicenseUsageKeys;
import io.harness.ng.core.licenseusage.entities.LicenseUsageActivityData;
import io.harness.ng.core.licenseusage.utils.LicenseUsageMetricHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@Singleton
@OwnedBy(PL)
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class HourlyToDailyRollUpLicenseUsageDataProcessor {
  private final CreditService creditService;
  private final LicenseUsageSQLHelper licenseUsageSQLHelper;
  private final LicenseUsageMetricHelper metricHelper;
  private final FeatureFlagService featureFlagService;
  private MongoTemplate mongoTemplate;
  private static final String LICENSE_USAGE_HOURLY_DAILY_PROCESSED_COUNT = "license_usage_hourly_daily_processed_count";
  // Old multipliers (for existing CI Cloud accounts with CI_USE_OLD_CLOUD_MULTIPLIERS enabled)
  public static final int LINUX_FLEX_MULTIPLIER = 2;
  public static final int LINUX_LARGE_MULTIPLIER = 10;
  public static final int LINUX_MEDIUM_MULTIPLIER = 2;
  public static final int LINUX_SMALL_MULTIPLIER = 2;
  public static final int LINUX_XLARGE_MULTIPLIER = 20;
  public static final int LINUX_XXLARGE_MULTIPLIER = 40;
  public static final int LINUX_XXXLARGE_MULTIPLIER = 60;
  public static final int MAC_FLEX_MULTIPLIER = 60;
  public static final int WINDOWS_FLEX_MULTIPLIER = 6;
  public static final int WINDOWS_MEDIUM_MULTIPLIER = 12;
  public static final int WINDOWS_LARGE_MULTIPLIER = 24;
  public static final int WINDOWS_XLARGE_MULTIPLIER = 48;
  public static final int WINDOWS_XXLARGE_MULTIPLIER = 96;
  public static final int WINDOWS_XXXLARGE_MULTIPLIER = 144;

  // New multipliers (default for all accounts, unless CI_USE_OLD_CLOUD_MULTIPLIERS is enabled)
  public static final int NEW_LINUX_XSMALL_MULTIPLIER = 1;
  public static final int NEW_LINUX_SMALL_MULTIPLIER = 2;
  public static final int NEW_LINUX_MEDIUM_MULTIPLIER = 4;
  public static final int NEW_LINUX_LARGE_MULTIPLIER = 8;
  public static final int NEW_LINUX_XLARGE_MULTIPLIER = 16;
  public static final int NEW_LINUX_XXLARGE_AMD_MULTIPLIER = 32;
  public static final int NEW_LINUX_XXLARGE_ARM_MULTIPLIER = 24;
  public static final int NEW_LINUX_XXXLARGE_MULTIPLIER = 48;
  public static final int NEW_WINDOWS_SMALL_MULTIPLIER = 4;
  public static final int NEW_WINDOWS_MEDIUM_MULTIPLIER = 8;
  public static final int NEW_WINDOWS_LARGE_MULTIPLIER = 16;
  public static final int NEW_WINDOWS_XLARGE_MULTIPLIER = 32;
  public static final int NEW_WINDOWS_XXLARGE_MULTIPLIER = 64;
  public static final int NEW_WINDOWS_XXXLARGE_MULTIPLIER = 96;
  private static final int MAX_DOCUMENTS_PER_CALL = 500;
  private static String resourceClassDefaultMsg =
      "ResourceClass=%s is not supported for calculating the credits usage for %s";

  public void processLicenseUsageEvent(
      String accountIdBeingProcessed, long fromTimestamp, long toTimestamp, long docsBatchSize) {
    List<String> toBeMarkedProcessed = new ArrayList<>();
    Map<AggregationKey, HourlyBucketValue> hourlyBucketMap = new HashMap<>();
    int totalUsedCreditsByAccountId = 0;
    String moduleTypeName = "";
    try (Stream<LicenseUsage> unProcessedEventsStream =
             findAllUnprocessedEventsByAccountId(accountIdBeingProcessed, docsBatchSize)) {
      Iterator<LicenseUsage> unProcessedEvents = unProcessedEventsStream.iterator();
      while (unProcessedEvents.hasNext()) {
        LicenseUsage rawLicenseUsageEvent = unProcessedEvents.next();

        // Check if LicenseUsage is of type CILicenseUsage before processing
        if (!(rawLicenseUsageEvent instanceof CILicenseUsage)) {
          log.error("Skipping processing of License Usage event - not a CILicenseUsage type: Event ID="
              + rawLicenseUsageEvent.getId());
          continue;
        }

        // Check for null fields in License Usage events before processing
        CILicenseUsage ciLicenseUsage = (CILicenseUsage) rawLicenseUsageEvent;
        if (ciLicenseUsage.getAccountIdentifier() == null || ciLicenseUsage.getOrgIdentifier() == null
            || ciLicenseUsage.getProjectIdentifier() == null || ciLicenseUsage.getPipelineIdentifier() == null
            || ciLicenseUsage.getStageIdentifier() == null || ciLicenseUsage.getResourceClass() == null
            || ciLicenseUsage.getOsType() == null) {
          log.error(
              "Skipping processing of License Usage event due to null fields: Event ID=" + ciLicenseUsage.getId());
          continue;
        }

        ModuleType moduleType = rawLicenseUsageEvent.getModuleType();
        moduleTypeName = moduleType.name();
        long lastBuildTimestamp = ciLicenseUsage.getLastBuildTimestamp();
        LocalDateTime dateTime = Instant.ofEpochMilli(lastBuildTimestamp).atZone(timeStampZone).toLocalDateTime();
        LocalDateTime beginningOfHour = dateTime.withMinute(0).withSecond(0).withNano(0);
        long lastBuildTimestampStartHour = beginningOfHour.atZone(timeStampZone).toInstant().toEpochMilli();
        AggregationKey bucketId = AggregationKey.builder()
                                      .accountIdentifier(ciLicenseUsage.getAccountIdentifier())
                                      .organizationIdentifier(ciLicenseUsage.getOrgIdentifier())
                                      .projectIdentifier(ciLicenseUsage.getProjectIdentifier())
                                      .pipelineIdentifier(ciLicenseUsage.getPipelineIdentifier())
                                      .stageIdentifier(ciLicenseUsage.getStageIdentifier())
                                      .ciResourceClass(ciLicenseUsage.getResourceClass().name())
                                      .ciOsType(ciLicenseUsage.getOsType().name())
                                      .utcTimestamp(lastBuildTimestampStartHour)
                                      .moduleType(ciLicenseUsage.getModuleType().name())
                                      .build();
        int usedCredits = getCreditsForBuild(ciLicenseUsage);
        totalUsedCreditsByAccountId += usedCredits;
        HourlyBucketValue existingValue =
            hourlyBucketMap.getOrDefault(bucketId, new HourlyBucketValue(rawLicenseUsageEvent.getId(), 0));
        existingValue.setUsedCredits(existingValue.getUsedCredits() + usedCredits);
        hourlyBucketMap.put(bucketId, existingValue);
        toBeMarkedProcessed.add(rawLicenseUsageEvent.getId());
      }
    }

    log.info("License Usage - processLicenseUsageEvent : For accountID {}, Nos of Entities toBeMarkedProcessed = {}",
        accountIdBeingProcessed, toBeMarkedProcessed.size());

    // Update the processed account
    try {
      for (Map.Entry<AggregationKey, HourlyBucketValue> entry : hourlyBucketMap.entrySet()) {
        LicenseUsageActivityData hourlyActivityData = buildHourlyLicenseUsageData(entry.getKey(), entry.getValue());
        licenseUsageSQLHelper.saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyActivityData);
      }
    } catch (Exception e) {
      log.error("Encountered an error while inserting data into Postgres, not marking the events as processed.", e);
      throw new RuntimeException(
          "Could not insert the data into Postgres, not marking the events as processed " + e.getMessage());
    }
    updateCreditContracts(accountIdBeingProcessed, totalUsedCreditsByAccountId, moduleTypeName);
    markEventsAsProcessed(toBeMarkedProcessed);
    metricHelper.recordMetricForAccount(
        LICENSE_USAGE_HOURLY_DAILY_PROCESSED_COUNT, toBeMarkedProcessed.size(), accountIdBeingProcessed);
    // Daily
    List<LicenseUsageActivityData> hourlyLicenseUsageActivityData = licenseUsageSQLHelper.fetchRecordsFromPostgres(
        LICENSE_USAGE_HOURLY, accountIdBeingProcessed, fromTimestamp, toTimestamp);
    List<LicenseUsageActivityData> dailyLicenseUsageActivityData =
        computeDailyLicenseUsageData(hourlyLicenseUsageActivityData);
    for (LicenseUsageActivityData licenseUsageActivityData : dailyLicenseUsageActivityData) {
      licenseUsageSQLHelper.saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, licenseUsageActivityData);
    }
  }

  void markEventsAsProcessed(List<String> documentIds) {
    if (documentIds.isEmpty()) {
      return;
    }
    Query updateQuery = new Query(Criteria.where(LicenseUsageKeys.id).in(documentIds));
    Update update = new Update().set(LicenseUsageKeys.isProcessed, true);
    mongoTemplate.updateMulti(updateQuery, update, LicenseUsage.class);
  }

  Stream<LicenseUsage> findAllUnprocessedEventsByAccountId(String accountIdentifier, long docsBatchSize) {
    Criteria criteria = Criteria.where(LicenseUsageKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(LicenseUsageKeys.isProcessed)
                            .is(false);
    Query query = new Query(criteria);

    // Add sorting for consistent results
    query.with(Sort.by(Sort.Direction.ASC, LicenseUsageKeys.createdAt));

    // Limit to docsBatchSize
    query.limit((int) docsBatchSize);

    // Use MongoDB iterator to process documents one at a time
    return mongoTemplate.stream(query, LicenseUsage.class);
  }

  public LicenseUsageActivityData buildHourlyLicenseUsageData(
      AggregationKey bucket, HourlyBucketValue hourlyBucketValue) {
    return LicenseUsageActivityData.builder()
        .accountIdentifier(bucket.getAccountIdentifier())
        .organizationIdentifier(bucket.getOrganizationIdentifier())
        .projectIdentifier(bucket.getProjectIdentifier())
        .pipelineIdentifier(bucket.getPipelineIdentifier())
        .stageIdentifier(bucket.getStageIdentifier())
        .ciOsType(bucket.getCiOsType())
        .ciResourceClass(bucket.getCiResourceClass())
        .moduleType(bucket.getModuleType())
        .usedCredits(hourlyBucketValue.getUsedCredits())
        .utcTimestamp(bucket.getUtcTimestamp())
        .createdAt(Instant.now().toEpochMilli())
        .build();
  }

  private int getCreditsForBuild(CILicenseUsage ciLicenseUsage) {
    boolean useOldCredit =
        featureFlagService.isEnabled(FeatureName.CI_USE_OLD_CLOUD_MULTIPLIERS, ciLicenseUsage.getAccountIdentifier());
    int credits = 0;
    switch (ciLicenseUsage.getOsType()) {
      case WINDOWS:
        credits = getWindowsBuildCredits(ciLicenseUsage, useOldCredit);
        break;
      case MACOS:
        credits = getMacosBuildCredits(ciLicenseUsage);
        break;
      case LINUX:
        credits = getLinuxBuildCredits(ciLicenseUsage, useOldCredit);
        break;
      default:
        String errorMessage = String.format("Unsupported OS type: %s", ciLicenseUsage.getOsType());
        throw new RuntimeException(errorMessage);
    }

    return credits;
  }

  private static int getWindowsBuildCredits(CILicenseUsage ciLicenseUsage, boolean useOldCredit) {
    int buildMinutes = ciLicenseUsage.getBuildMinutes();
    ResourceClass resourceClass = ciLicenseUsage.getResourceClass();
    if (useOldCredit) {
      switch (resourceClass) {
        case FLEX:
        case SMALL:
          return WINDOWS_FLEX_MULTIPLIER * buildMinutes;
        case MEDIUM:
          return WINDOWS_MEDIUM_MULTIPLIER * buildMinutes;
        case LARGE:
          return WINDOWS_LARGE_MULTIPLIER * buildMinutes;
        case XLARGE:
          return WINDOWS_XLARGE_MULTIPLIER * buildMinutes;
        case XXLARGE:
          return WINDOWS_XXLARGE_MULTIPLIER * buildMinutes;
        case XXXLARGE:
          return WINDOWS_XXXLARGE_MULTIPLIER * buildMinutes;
        default:
          throw new RuntimeException(String.format(resourceClassDefaultMsg, resourceClass, OSType.WINDOWS));
      }
    }
    switch (resourceClass) {
      case FLEX:
      case SMALL:
        return NEW_WINDOWS_SMALL_MULTIPLIER * buildMinutes;
      case MEDIUM:
        return NEW_WINDOWS_MEDIUM_MULTIPLIER * buildMinutes;
      case LARGE:
        return NEW_WINDOWS_LARGE_MULTIPLIER * buildMinutes;
      case XLARGE:
        return NEW_WINDOWS_XLARGE_MULTIPLIER * buildMinutes;
      case XXLARGE:
        return NEW_WINDOWS_XXLARGE_MULTIPLIER * buildMinutes;
      case XXXLARGE:
        return NEW_WINDOWS_XXXLARGE_MULTIPLIER * buildMinutes;
      default:
        throw new RuntimeException(String.format(resourceClassDefaultMsg, resourceClass, OSType.WINDOWS));
    }
  }

  private static int getMacosBuildCredits(CILicenseUsage ciLicenseUsage) {
    int credits = 0;
    ResourceClass resourceClass = ciLicenseUsage.getResourceClass();
    switch (resourceClass) {
      case FLEX:
        credits = MAC_FLEX_MULTIPLIER * ciLicenseUsage.getBuildMinutes();
        break;
      default:
        throw new RuntimeException(String.format(resourceClassDefaultMsg, resourceClass, OSType.MACOS));
    }

    return credits;
  }

  private int getLinuxBuildCredits(CILicenseUsage ciLicenseUsage, boolean useOldCredit) {
    int buildMinutes = ciLicenseUsage.getBuildMinutes();
    ResourceClass resourceClass = ciLicenseUsage.getResourceClass();
    ArchitectureType architectureType = ciLicenseUsage.getArchitectureType();
    int newMultiplier = getNewLinuxMultiplier(resourceClass, architectureType);
    if (!useOldCredit) {
      return newMultiplier * buildMinutes;
    }
    return Math.min(getOldLinuxMultiplier(resourceClass), newMultiplier) * buildMinutes;
  }

  private static int getOldLinuxMultiplier(ResourceClass resourceClass) {
    switch (resourceClass) {
      case XSMALL:
      case FLEX:
        return LINUX_FLEX_MULTIPLIER;
      case SMALL:
        return LINUX_SMALL_MULTIPLIER;
      case MEDIUM:
        return LINUX_MEDIUM_MULTIPLIER;
      case LARGE:
        return LINUX_LARGE_MULTIPLIER;
      case XLARGE:
        return LINUX_XLARGE_MULTIPLIER;
      case XXLARGE:
        return LINUX_XXLARGE_MULTIPLIER;
      case XXXLARGE:
        return LINUX_XXXLARGE_MULTIPLIER;
      default:
        throw new RuntimeException(String.format(resourceClassDefaultMsg, resourceClass, OSType.LINUX));
    }
  }

  private static int getNewLinuxMultiplier(ResourceClass resourceClass, ArchitectureType architectureType) {
    switch (resourceClass) {
      case XSMALL:
        return NEW_LINUX_XSMALL_MULTIPLIER;
      case FLEX:
        return NEW_LINUX_MEDIUM_MULTIPLIER;
      case SMALL:
        return NEW_LINUX_SMALL_MULTIPLIER;
      case MEDIUM:
        return NEW_LINUX_MEDIUM_MULTIPLIER;
      case LARGE:
        return NEW_LINUX_LARGE_MULTIPLIER;
      case XLARGE:
        return NEW_LINUX_XLARGE_MULTIPLIER;
      case XXLARGE:
        if (architectureType == ArchitectureType.ARM64) {
          return NEW_LINUX_XXLARGE_ARM_MULTIPLIER;
        }
        return NEW_LINUX_XXLARGE_AMD_MULTIPLIER;
      case XXXLARGE:
        return NEW_LINUX_XXXLARGE_MULTIPLIER;
      default:
        throw new RuntimeException(String.format(resourceClassDefaultMsg, resourceClass, OSType.LINUX));
    }
  }

  /**
   * This method takes in accountIdentifier and tries to consumer the currently used credits
   * from the provisioned quantity from the active credits contracts.
   * Credits are consumed from the FREE active contract first followed by the PAID active contracts
   * based on the closest expiry dates.
   *
   * @param accountIdentifier AccountIdentifier of the user
   * @param currentUsedCredits used credits based on the incoming LicenseUsageEvent
   */
  void updateCreditContracts(String accountIdentifier, int currentUsedCredits, String moduleTypeName) {
    // Get all Active Credits Contracts for this account
    List<CreditDTO> freeActiveContracts =
        creditService.getCredits(accountIdentifier, CreditType.FREE, CreditStatus.ACTIVE);
    List<CreditDTO> paidActiveContracts =
        creditService.getCredits(accountIdentifier, CreditType.PAID, CreditStatus.ACTIVE);

    int remainingUsage = updateUsedCreditsInContracts(accountIdentifier, freeActiveContracts, currentUsedCredits);
    if (remainingUsage != 0) {
      remainingUsage = updateUsedCreditsInContracts(accountIdentifier, paidActiveContracts, remainingUsage);
      if (remainingUsage != 0) { // Over usage after consuming Free and Paid contracts
        try {
          Query query = new Query(Criteria.where(CreditOverUsageKeys.accountIdentifier).is(accountIdentifier));
          Update update = new Update();
          CreditOverUsageEntity existingEntity = mongoTemplate.findOne(query, CreditOverUsageEntity.class);
          if (existingEntity == null) {
            update.set(CreditOverUsageKeys.moduleType, moduleTypeName);
            update.set(CreditOverUsageKeys.overUsageCount, remainingUsage);
            update.set(CreditOverUsageKeys.createdAt, System.currentTimeMillis());
          } else {
            update.set(CreditOverUsageKeys.overUsageCount, existingEntity.getOverUsageCount() + remainingUsage);
          }
          update.set(CreditOverUsageKeys.lastUpdatedAt, System.currentTimeMillis());
          FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);
          mongoTemplate.findAndModify(query, update, options, CreditOverUsageEntity.class);
        } catch (Exception ex) {
          log.error("Encountered an exception while trying to update the overusage for accountIdentifier={}."
                  + " Exception=",
              accountIdentifier, ex);
        }
      }
    }
  }
  int updateUsedCreditsInContracts(String accountIdentifier, List<CreditDTO> activeContracts, int computedUsage) {
    // Sort them based on the expiryTime
    Collections.sort(activeContracts, Comparator.comparingLong(CreditDTO::getExpiryTime));
    int remainingComputedUsage = computedUsage;
    for (CreditDTO creditDTO : activeContracts) {
      int prevUsedCredits = creditDTO.getUsedCredits();
      int availableCredits = creditDTO.getQuantity() - prevUsedCredits;
      if (remainingComputedUsage <= 0 || availableCredits == 0) {
        continue; // nothing to reduce, early return
      }
      if (availableCredits >= remainingComputedUsage) {
        creditDTO.setUsedCredits(prevUsedCredits + remainingComputedUsage);
        remainingComputedUsage = 0;
      } else if (availableCredits < remainingComputedUsage) {
        creditDTO.setUsedCredits(prevUsedCredits + availableCredits);
        remainingComputedUsage -= availableCredits;
      }
      creditService.updateCredit(accountIdentifier, creditDTO);
    }
    return remainingComputedUsage;
  }

  public List<LicenseUsageActivityData> computeDailyLicenseUsageData(
      List<LicenseUsageActivityData> hourlyLicenseUsageActivityData) {
    Map<AggregationKey, List<LicenseUsageActivityData>> groupedData =
        aggregateHourlyData(hourlyLicenseUsageActivityData);

    List<LicenseUsageActivityData> dailyData = new ArrayList<>();
    for (Map.Entry<AggregationKey, List<LicenseUsageActivityData>> entry : groupedData.entrySet()) {
      AggregationKey key = entry.getKey();
      int totalCredits = entry.getValue().stream().mapToInt(LicenseUsageActivityData::getUsedCredits).sum();

      dailyData.add(LicenseUsageActivityData.builder()
                        .accountIdentifier(key.getAccountIdentifier())
                        .organizationIdentifier(key.getOrganizationIdentifier())
                        .projectIdentifier(key.getProjectIdentifier())
                        .pipelineIdentifier(key.getPipelineIdentifier())
                        .stageIdentifier(key.getStageIdentifier())
                        .ciOsType(key.getCiOsType())
                        .ciResourceClass(key.getCiResourceClass())
                        .moduleType(key.getModuleType())
                        .usedCredits(totalCredits)
                        .utcTimestamp(key.getUtcTimestamp())
                        .build());
    }
    return dailyData;
  }

  public Map<AggregationKey, List<LicenseUsageActivityData>> aggregateHourlyData(
      List<LicenseUsageActivityData> hourlyData) {
    return hourlyData.stream().collect(Collectors.groupingBy(hourly
        -> new AggregationKey(getStartOfDayUtc(hourly.getUtcTimestamp()), hourly.getAccountIdentifier(),
            hourly.getOrganizationIdentifier(), hourly.getProjectIdentifier(), hourly.getPipelineIdentifier(),
            hourly.getStageIdentifier(), hourly.getCiOsType(), hourly.getCiResourceClass(), hourly.getModuleType())));
  }
}