/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.service;

import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.LINUX_FLEX_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.LINUX_LARGE_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.LINUX_MEDIUM_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.LINUX_SMALL_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.LINUX_XLARGE_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.LINUX_XXLARGE_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.LINUX_XXXLARGE_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.MAC_FLEX_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.NEW_LINUX_LARGE_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.NEW_LINUX_MEDIUM_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.NEW_LINUX_SMALL_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.NEW_LINUX_XLARGE_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.NEW_LINUX_XSMALL_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.NEW_LINUX_XXLARGE_AMD_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.NEW_LINUX_XXLARGE_ARM_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.NEW_LINUX_XXXLARGE_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.NEW_WINDOWS_LARGE_MULTIPLIER;
import static io.harness.ng.core.event.service.HourlyToDailyRollUpLicenseUsageDataProcessor.WINDOWS_FLEX_MULTIPLIER;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.LICENSE_USAGE_DAILY;
import static io.harness.ng.core.event.service.LicenseUsageSQLHelper.LICENSE_USAGE_HOURLY;
import static io.harness.rule.OwnerRule.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ArchitectureType;
import io.harness.BuildInfraType;
import io.harness.CategoryTest;
import io.harness.CreditType;
import io.harness.Developer;
import io.harness.ModuleType;
import io.harness.OSType;
import io.harness.ResourceClass;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.credit.beans.credits.CICreditDTO;
import io.harness.credit.beans.credits.CreditDTO;
import io.harness.credit.entities.CreditOverUsageEntity;
import io.harness.credit.services.CreditService;
import io.harness.credit.utils.CreditStatus;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.licenseusage.entities.CILicenseUsage;
import io.harness.ng.core.licenseusage.entities.LicenseUsage;
import io.harness.ng.core.licenseusage.entities.LicenseUsage.LicenseUsageKeys;
import io.harness.ng.core.licenseusage.entities.LicenseUsageActivityData;
import io.harness.ng.core.licenseusage.utils.LicenseUsageMetricHelper;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

public class LicenseUsageDataProcessorTest extends CategoryTest {
  @Mock private CreditService creditService;
  @Mock private MongoTemplate mongoTemplate;
  @Mock private HourlyToDailyRollUpLicenseUsageDataProcessor hourlyToDailyRollUpLicenseUsageDataProcessor;
  @Mock private MonthlyToYearlyRollupLicenseUsageDataProcessor monthlyToYearlyRollupLicenseUsageDataProcessor;

  @Mock private LicenseUsageSQLHelper licenseUsageSQLHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private LicenseUsageMetricHelper metricHelper;
  @Mock private CILicenseUsage rawCILicenseUsageEvent; // Mock a LicenseUsage event
  private static final String ACCOUNT_IDENTIFIER = "testAccount123";
  private static final String LINUX_PIPELINE_IDENTIFIER = "test_linux";
  private static final String MACOS_PIPELINE_IDENTIFIER = "test_macos";
  private static final String ORG_IDENTIFIER = "default_orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "default_projectIdentifier";
  private static final String PIPELINE_IDENTIFIER = "default_pipelineIdentifier";
  private static final String STAGE_IDENTIFIER = "default_stageIdentifier";
  private static final long PURCHASE_TIME = 1684424719000L;
  private static final long EXPIRY_TIME = 2631195919000L;
  private static final long DOCS_BATCH_SIZE = 10;
  @Inject @InjectMocks private HourlyToDailyRollUpLicenseUsageDataProcessor licenseUsageDataProcessor;
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NITIKA)
  @Category(UnitTests.class)
  public void testLicenseUsageDailyData1() {
    List<LicenseUsageActivityData> licenseUsageActivityDataList =
        prepareMockedLicenseUsageActivityDataListWithDifferentTimestamp1();
    assertEquals(5, licenseUsageActivityDataList.size());
    List<LicenseUsageActivityData> expectedDailyDataList = prepareExpectedLicenseUsageActivityDailyData1();
    List<LicenseUsageActivityData> dailyDataList =
        licenseUsageDataProcessor.computeDailyLicenseUsageData(licenseUsageActivityDataList);
    assertEquals(expectedDailyDataList.size(), dailyDataList.size());
  }

  @Test
  @Owner(developers = NITIKA)
  @Category(UnitTests.class)
  public void testLicenseUsageDailyData2() {
    List<LicenseUsageActivityData> licenseUsageActivityDataList = mockLicenseUsageActivityData();
    assertEquals(5, licenseUsageActivityDataList.size());
    List<LicenseUsageActivityData> expectedDailyDataList = prepareExpectedLicenseUsageActivityDailyData2();
    List<LicenseUsageActivityData> dailyDataList =
        licenseUsageDataProcessor.computeDailyLicenseUsageData(licenseUsageActivityDataList);
    assertEquals(expectedDailyDataList.size(), dailyDataList.size());
  }

  @Test
  @Owner(developers = NITIKA)
  @Category(UnitTests.class)
  public void testLicenseUsageDailyDataForHourlyAndDailyRollUp_ShouldAddTwoRecordsInDailyTable() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfPreviousDay = zonedDateTime.toLocalDate().minusDays(1).atStartOfDay(timeStampZone);
    long fromTimestamp = startOfPreviousDay.toInstant().toEpochMilli();
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();
    List<CILicenseUsage> mongoEventslist = mockLicenseUsageActivityData2();
    Criteria mockCriteria = mock(Criteria.class);
    Query query = mock(Query.class);
    when(mockCriteria.is(mongoEventslist.get(0).getAccountIdentifier())).thenReturn(mockCriteria);
    when(mockCriteria.and(LicenseUsage.LicenseUsageKeys.isProcessed)).thenReturn(mockCriteria);
    when(mockCriteria.is(false)).thenReturn(mockCriteria);
    when(query.addCriteria(mockCriteria)).thenReturn(query);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class))).thenReturn(mongoEventslist);
    hourlyToDailyRollUpLicenseUsageDataProcessor.processLicenseUsageEvent(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class))).thenReturn(mockLicenseUsageActivityData2());
    List<LicenseUsageActivityData> dailyLicenseUsageActivityDataList = prepareExpectedLicenseUsageActivityDailyData3();
    List<LicenseUsageActivityData> hourlyLicenseUsageActivityDataList = prepareExpectedLicenseUsageActivityDailyData4();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(hourlyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualHourlyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(hourlyLicenseUsageActivityDataList.size(), actualHourlyLicenseUsageActivityDataList.size());
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDailyData3());
    List<LicenseUsageActivityData> actualDailyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    List<LicenseUsageActivityData> expectedDailyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDailyData3();
    assertEquals(expectedDailyLicenseUsageActivityDataList.size(), actualDailyLicenseUsageActivityDataList.size());
  }

  @Test
  @Owner(developers = NITIKA)
  @Category(UnitTests.class)
  public void
  testLicenseUsageDailyDataForHourlyAndDailyRollUp_WithDifferentHourAndSameDay_ShouldAddTwoRecordsInMonthlyTable() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfPreviousDay = zonedDateTime.toLocalDate().minusDays(1).atStartOfDay(timeStampZone);
    long fromTimestamp = startOfPreviousDay.toInstant().toEpochMilli();
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();
    List<CILicenseUsage> mongoEventslist = mockLicenseUsageActivityDataWithSameFields();
    Criteria mockCriteria = mock(Criteria.class);
    Query query = mock(Query.class);
    when(mockCriteria.is(mongoEventslist.get(0).getAccountIdentifier())).thenReturn(mockCriteria);
    when(mockCriteria.and(LicenseUsage.LicenseUsageKeys.isProcessed)).thenReturn(mockCriteria);
    when(mockCriteria.is(false)).thenReturn(mockCriteria);
    when(query.addCriteria(mockCriteria)).thenReturn(query);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class))).thenReturn(mongoEventslist);
    hourlyToDailyRollUpLicenseUsageDataProcessor.processLicenseUsageEvent(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class))).thenReturn(mockLicenseUsageActivityData2());
    List<LicenseUsageActivityData> dailyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDataWithSameDayAndDifferentHour();
    List<LicenseUsageActivityData> hourlyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDailyDataWithSameDayAndDifferentHour();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(hourlyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualHourlyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(hourlyLicenseUsageActivityDataList.size(), actualHourlyLicenseUsageActivityDataList.size());
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDailyData());
    List<LicenseUsageActivityData> actualDailyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualDailyLicenseUsageActivityDataList.size(), 1);
  }

  @Test
  @Owner(developers = NITIKA)
  @Category(UnitTests.class)
  public void
  testLicenseUsageDailyDataForHourlyAndDailyRollUp_When_RunningMultiplePipelinesIntheSameHour_Should_CountInTheSameHour() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfPreviousDay = zonedDateTime.toLocalDate().minusDays(1).atStartOfDay(timeStampZone);
    long fromTimestamp = startOfPreviousDay.toInstant().toEpochMilli();
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();
    List<CILicenseUsage> mongoEventslist = mockLicenseUsageActivityDataWithSameFields();
    Criteria mockCriteria = mock(Criteria.class);
    Query query = mock(Query.class);
    when(mockCriteria.is(mongoEventslist.get(0).getAccountIdentifier())).thenReturn(mockCriteria);
    when(mockCriteria.and(LicenseUsage.LicenseUsageKeys.isProcessed)).thenReturn(mockCriteria);
    when(mockCriteria.is(false)).thenReturn(mockCriteria);
    when(query.addCriteria(mockCriteria)).thenReturn(query);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class))).thenReturn(mongoEventslist);
    hourlyToDailyRollUpLicenseUsageDataProcessor.processLicenseUsageEvent(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class))).thenReturn(mockLicenseUsageActivityData2());
    List<LicenseUsageActivityData> dailyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDataWithSameDayAndDifferentHour();
    List<LicenseUsageActivityData> hourlyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDailyDataWithSameDayAndDifferentHour();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(hourlyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualHourlyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(hourlyLicenseUsageActivityDataList.size(), actualHourlyLicenseUsageActivityDataList.size());
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDailyData());
    List<LicenseUsageActivityData> actualDailyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualDailyLicenseUsageActivityDataList.size(), 1);

    // Mocking the behavior where we are running multiple pipelines multiple time while scheduler is still running
    List<CILicenseUsage> mongoEventslist2 = mockLicenseUsageActivityDataForTestingMultiplePipelines();
    Criteria mockCriteria2 = mock(Criteria.class);
    Query query2 = mock(Query.class);
    when(mockCriteria2.is(mongoEventslist.get(0).getAccountIdentifier())).thenReturn(mockCriteria2);
    when(mockCriteria2.and(LicenseUsage.LicenseUsageKeys.isProcessed)).thenReturn(mockCriteria);
    when(mockCriteria2.is(false)).thenReturn(mockCriteria2);
    when(query.addCriteria(mockCriteria)).thenReturn(query2);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class))).thenReturn(mongoEventslist2);
    hourlyToDailyRollUpLicenseUsageDataProcessor.processLicenseUsageEvent(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class)))
        .thenReturn(mockLicenseUsageActivityDataWithDifferentHours());
    List<LicenseUsageActivityData> dailyLicenseUsageActivityDataList2 =
        prepareExpectedLicenseUsageActivityDataWithSameDayAndDifferentHour();
    List<LicenseUsageActivityData> hourlyLicenseUsageActivityDataList2 =
        prepareExpectedLicenseUsageActivityDailyDataWithDifferentHour();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList2.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList2.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(hourlyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualHourlyLicenseUsageActivityDataList2 =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(hourlyLicenseUsageActivityDataList2.size(), actualHourlyLicenseUsageActivityDataList2.size());
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList2.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList2.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDailyData());
    List<LicenseUsageActivityData> actualDailyLicenseUsageActivityDataList2 =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualDailyLicenseUsageActivityDataList2.size(), 1);
    assertEquals(actualDailyLicenseUsageActivityDataList2.get(0).getUsedCredits(), 8);
  }

  @Test
  @Owner(developers = NEELAM)
  @Category(UnitTests.class)
  public void
  testProcessLicenseUsageEvent_When_RunningMultiplePipelinesInParallel_ShouldAggregateAndUpdateAllUsedCredits() {
    when(featureFlagService.isEnabled(FeatureName.CI_USE_OLD_CLOUD_MULTIPLIERS, ACCOUNT_IDENTIFIER)).thenReturn(true);
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfPreviousDay = zonedDateTime.toLocalDate().minusDays(1).atStartOfDay(timeStampZone);
    long fromTimestamp = startOfPreviousDay.toInstant().toEpochMilli();
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();
    List<LicenseUsage> rawLicenseUsageEvents = prepareParallelRunsOfPipelineLicenseUsageEvents();
    Criteria criteria = Criteria.where(LicenseUsageKeys.accountIdentifier)
                            .is(ACCOUNT_IDENTIFIER)
                            .and(LicenseUsageKeys.isProcessed)
                            .is(false);
    Query query = new Query();
    query.addCriteria(criteria);
    query.with(Sort.by(Sort.Direction.ASC, LicenseUsageKeys.createdAt));
    query.limit((int) DOCS_BATCH_SIZE);
    // Create a mock stream of LicenseUsage
    Stream<LicenseUsage> mockStream = rawLicenseUsageEvents.stream();
    // Mock the mongoTemplate.stream(...) call
    when(mongoTemplate.stream(eq(query), eq(LicenseUsage.class))).thenReturn(mockStream);
    List<LicenseUsageActivityData> hourlyLicenseUsageActivityDataList =
        prepareLicenseUsageActivityDataWithLinuxAndMacOsTypesForSameDayDifferentHours();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(1));

    // Mock active credits
    CreditDTO freeCreditsDTO1 = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 2000, 0);
    List<CreditDTO> freeActiveContracts = Collections.singletonList(freeCreditsDTO1);
    CreditDTO paidCreditsDTO1 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 3000, 0);
    List<CreditDTO> paidActiveContracts = Collections.singletonList(paidCreditsDTO1);

    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.FREE, CreditStatus.ACTIVE))
        .thenReturn(freeActiveContracts);
    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.PAID, CreditStatus.ACTIVE))
        .thenReturn(paidActiveContracts);
    CreditDTO updatedFreeCreditsDTO1 = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 2000, 70);
    when(creditService.updateCredit(ACCOUNT_IDENTIFIER, freeActiveContracts.get(0))).thenReturn(updatedFreeCreditsDTO1);

    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(hourlyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualHourlyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(hourlyLicenseUsageActivityDataList.size(), actualHourlyLicenseUsageActivityDataList.size());

    List<LicenseUsageActivityData> dailyLicenseUsageActivityDataList =
        prepareLicenseUsageActivityDataWithLinuxAndMacOsTypesForSameDayDifferentHours();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDailyData());
    List<LicenseUsageActivityData> actualDailyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualDailyLicenseUsageActivityDataList.size(), 1);

    // Call the method being tested and verify the behavior
    licenseUsageDataProcessor.processLicenseUsageEvent(ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.FREE), eq(CreditStatus.ACTIVE));
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.PAID), eq(CreditStatus.ACTIVE));
    ArgumentCaptor<CreditDTO> creditCaptor = ArgumentCaptor.forClass(CreditDTO.class);
    verify(creditService, times(1)).updateCredit(eq(ACCOUNT_IDENTIFIER), creditCaptor.capture());

    CreditDTO updatedCredit = creditCaptor.getValue();
    CILicenseUsage ciLinuxFlexPipelineUsageEvent = (CILicenseUsage) rawLicenseUsageEvents.get(0);
    CILicenseUsage ciMacOsFlexPipelineUsageEvent = (CILicenseUsage) rawLicenseUsageEvents.get(1);
    CILicenseUsage ciWindowsFlexPipelineUsageEvent = (CILicenseUsage) rawLicenseUsageEvents.get(2);
    int expectedUsedCredits = ciLinuxFlexPipelineUsageEvent.getBuildMinutes() * LINUX_FLEX_MULTIPLIER
        + ciMacOsFlexPipelineUsageEvent.getBuildMinutes() * MAC_FLEX_MULTIPLIER
        + ciWindowsFlexPipelineUsageEvent.getBuildMinutes() * WINDOWS_FLEX_MULTIPLIER;

    assertEquals(expectedUsedCredits, updatedCredit.getUsedCredits());
  }

  @Test
  @Owner(developers = TAPAN)
  @Category(UnitTests.class)
  public void testGetLinuxBuildCredits_OldCredit_PicksCheaperOfOldAndNewForEveryResourceClass() throws Exception {
    Method getLinuxBuildCreditsMethod = HourlyToDailyRollUpLicenseUsageDataProcessor.class.getDeclaredMethod(
        "getLinuxBuildCredits", CILicenseUsage.class, boolean.class);
    getLinuxBuildCreditsMethod.setAccessible(true);
    int buildMinutes = 5;

    int expectedXsmall = Math.min(LINUX_FLEX_MULTIPLIER, NEW_LINUX_XSMALL_MULTIPLIER) * buildMinutes;
    int actualXsmall = (int) getLinuxBuildCreditsMethod.invoke(licenseUsageDataProcessor,
        CILicenseUsage.builder()
            .resourceClass(ResourceClass.XSMALL)
            .architectureType(ArchitectureType.AMD64)
            .buildMinutes(buildMinutes)
            .build(),
        true);
    assertEquals(expectedXsmall, actualXsmall);

    int expectedFlex = Math.min(LINUX_FLEX_MULTIPLIER, NEW_LINUX_MEDIUM_MULTIPLIER) * buildMinutes;
    int actualFlex = (int) getLinuxBuildCreditsMethod.invoke(licenseUsageDataProcessor,
        CILicenseUsage.builder()
            .resourceClass(ResourceClass.FLEX)
            .architectureType(ArchitectureType.AMD64)
            .buildMinutes(buildMinutes)
            .build(),
        true);
    assertEquals(expectedFlex, actualFlex);

    int expectedSmall = Math.min(LINUX_SMALL_MULTIPLIER, NEW_LINUX_SMALL_MULTIPLIER) * buildMinutes;
    int actualSmall = (int) getLinuxBuildCreditsMethod.invoke(licenseUsageDataProcessor,
        CILicenseUsage.builder()
            .resourceClass(ResourceClass.SMALL)
            .architectureType(ArchitectureType.AMD64)
            .buildMinutes(buildMinutes)
            .build(),
        true);
    assertEquals(expectedSmall, actualSmall);

    int expectedMedium = Math.min(LINUX_MEDIUM_MULTIPLIER, NEW_LINUX_MEDIUM_MULTIPLIER) * buildMinutes;
    int actualMedium = (int) getLinuxBuildCreditsMethod.invoke(licenseUsageDataProcessor,
        CILicenseUsage.builder()
            .resourceClass(ResourceClass.MEDIUM)
            .architectureType(ArchitectureType.AMD64)
            .buildMinutes(buildMinutes)
            .build(),
        true);
    assertEquals(expectedMedium, actualMedium);

    int expectedLarge = Math.min(LINUX_LARGE_MULTIPLIER, NEW_LINUX_LARGE_MULTIPLIER) * buildMinutes;
    int actualLarge = (int) getLinuxBuildCreditsMethod.invoke(licenseUsageDataProcessor,
        CILicenseUsage.builder()
            .resourceClass(ResourceClass.LARGE)
            .architectureType(ArchitectureType.AMD64)
            .buildMinutes(buildMinutes)
            .build(),
        true);
    assertEquals(expectedLarge, actualLarge);

    int expectedXlarge = Math.min(LINUX_XLARGE_MULTIPLIER, NEW_LINUX_XLARGE_MULTIPLIER) * buildMinutes;
    int actualXlarge = (int) getLinuxBuildCreditsMethod.invoke(licenseUsageDataProcessor,
        CILicenseUsage.builder()
            .resourceClass(ResourceClass.XLARGE)
            .architectureType(ArchitectureType.AMD64)
            .buildMinutes(buildMinutes)
            .build(),
        true);
    assertEquals(expectedXlarge, actualXlarge);

    int expectedXxlargeAmd = Math.min(LINUX_XXLARGE_MULTIPLIER, NEW_LINUX_XXLARGE_AMD_MULTIPLIER) * buildMinutes;
    int actualXxlargeAmd = (int) getLinuxBuildCreditsMethod.invoke(licenseUsageDataProcessor,
        CILicenseUsage.builder()
            .resourceClass(ResourceClass.XXLARGE)
            .architectureType(ArchitectureType.AMD64)
            .buildMinutes(buildMinutes)
            .build(),
        true);
    assertEquals(expectedXxlargeAmd, actualXxlargeAmd);

    int expectedXxlargeArm = Math.min(LINUX_XXLARGE_MULTIPLIER, NEW_LINUX_XXLARGE_ARM_MULTIPLIER) * buildMinutes;
    int actualXxlargeArm = (int) getLinuxBuildCreditsMethod.invoke(licenseUsageDataProcessor,
        CILicenseUsage.builder()
            .resourceClass(ResourceClass.XXLARGE)
            .architectureType(ArchitectureType.ARM64)
            .buildMinutes(buildMinutes)
            .build(),
        true);
    assertEquals(expectedXxlargeArm, actualXxlargeArm);

    int expectedXxxlarge = Math.min(LINUX_XXXLARGE_MULTIPLIER, NEW_LINUX_XXXLARGE_MULTIPLIER) * buildMinutes;
    int actualXxxlarge = (int) getLinuxBuildCreditsMethod.invoke(licenseUsageDataProcessor,
        CILicenseUsage.builder()
            .resourceClass(ResourceClass.XXXLARGE)
            .architectureType(ArchitectureType.AMD64)
            .buildMinutes(buildMinutes)
            .build(),
        true);
    assertEquals(expectedXxxlarge, actualXxxlarge);
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testProcessLicenseUsageEvent_WithNewMultipliersEnabled_ShouldUseNewCreditValues() {
    when(featureFlagService.isEnabled(FeatureName.CI_USE_OLD_CLOUD_MULTIPLIERS, ACCOUNT_IDENTIFIER)).thenReturn(false);

    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfPreviousDay = zonedDateTime.toLocalDate().minusDays(1).atStartOfDay(timeStampZone);
    long fromTimestamp = startOfPreviousDay.toInstant().toEpochMilli();
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();
    List<LicenseUsage> rawLicenseUsageEvents = prepareNewMultiplierLicenseUsageEvents();
    Criteria criteria = Criteria.where(LicenseUsageKeys.accountIdentifier)
                            .is(ACCOUNT_IDENTIFIER)
                            .and(LicenseUsageKeys.isProcessed)
                            .is(false);
    Query query = new Query();
    query.addCriteria(criteria);
    query.with(Sort.by(Sort.Direction.ASC, LicenseUsageKeys.createdAt));
    query.limit((int) DOCS_BATCH_SIZE);
    Stream<LicenseUsage> mockStream = rawLicenseUsageEvents.stream();
    when(mongoTemplate.stream(eq(query), eq(LicenseUsage.class))).thenReturn(mockStream);
    List<LicenseUsageActivityData> hourlyLicenseUsageActivityDataList =
        prepareLicenseUsageActivityDataWithLinuxAndMacOsTypesForSameDayDifferentHours();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(eq(LICENSE_USAGE_HOURLY), any(LicenseUsageActivityData.class));

    CreditDTO freeCreditsDTO = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 5000, 0);
    List<CreditDTO> freeActiveContracts = Collections.singletonList(freeCreditsDTO);
    CreditDTO paidCreditsDTO = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 10000, 0);
    List<CreditDTO> paidActiveContracts = Collections.singletonList(paidCreditsDTO);

    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.FREE, CreditStatus.ACTIVE))
        .thenReturn(freeActiveContracts);
    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.PAID, CreditStatus.ACTIVE))
        .thenReturn(paidActiveContracts);
    when(creditService.updateCredit(eq(ACCOUNT_IDENTIFIER), any(CreditDTO.class))).thenReturn(freeCreditsDTO);

    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(hourlyLicenseUsageActivityDataList);
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(eq(LICENSE_USAGE_DAILY), any(LicenseUsageActivityData.class));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDailyData());

    licenseUsageDataProcessor.processLicenseUsageEvent(ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);

    ArgumentCaptor<CreditDTO> creditCaptor = ArgumentCaptor.forClass(CreditDTO.class);
    verify(creditService, times(1)).updateCredit(eq(ACCOUNT_IDENTIFIER), creditCaptor.capture());

    CreditDTO updatedCredit = creditCaptor.getValue();
    CILicenseUsage linuxMediumEvent = (CILicenseUsage) rawLicenseUsageEvents.get(0);
    CILicenseUsage macFlexEvent = (CILicenseUsage) rawLicenseUsageEvents.get(1);
    CILicenseUsage windowsLargeEvent = (CILicenseUsage) rawLicenseUsageEvents.get(2);
    CILicenseUsage linuxArmXxlargeEvent = (CILicenseUsage) rawLicenseUsageEvents.get(3);
    int expectedUsedCredits = linuxMediumEvent.getBuildMinutes() * NEW_LINUX_MEDIUM_MULTIPLIER
        + macFlexEvent.getBuildMinutes() * MAC_FLEX_MULTIPLIER
        + windowsLargeEvent.getBuildMinutes() * NEW_WINDOWS_LARGE_MULTIPLIER
        + linuxArmXxlargeEvent.getBuildMinutes() * NEW_LINUX_XXLARGE_ARM_MULTIPLIER;

    assertEquals(expectedUsedCredits, updatedCredit.getUsedCredits());
  }

  @Test
  @Owner(developers = NITIKA)
  @Category(UnitTests.class)
  public void
  testLicenseUsageDailyDataForHourlyAndDailyRollUpIntheSameHour_WithDifferentStages_should_add_records_with_different_stages() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfPreviousDay = zonedDateTime.toLocalDate().minusDays(1).atStartOfDay(timeStampZone);
    long fromTimestamp = startOfPreviousDay.toInstant().toEpochMilli();
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();
    List<CILicenseUsage> mongoEventslist = mockLicenseUsageActivityDataWithSameFieldsWithDifferentStageIdentifiers();
    Criteria mockCriteria = mock(Criteria.class);
    Query query = mock(Query.class);
    when(mockCriteria.is(mongoEventslist.get(0).getAccountIdentifier())).thenReturn(mockCriteria);
    when(mockCriteria.and(LicenseUsage.LicenseUsageKeys.isProcessed)).thenReturn(mockCriteria);
    when(mockCriteria.is(false)).thenReturn(mockCriteria);
    when(query.addCriteria(mockCriteria)).thenReturn(query);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class))).thenReturn(mongoEventslist);
    hourlyToDailyRollUpLicenseUsageDataProcessor.processLicenseUsageEvent(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class)))
        .thenReturn(mockLicenseUsageActivityDataWithDifferentStageIdentifiers());
    List<LicenseUsageActivityData> dailyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDataWithDifferentStageIdentifiers();
    List<LicenseUsageActivityData> hourlyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDailyDataWithDifferentStageIdentifiers();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(hourlyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualHourlyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(hourlyLicenseUsageActivityDataList.size(), actualHourlyLicenseUsageActivityDataList.size());
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_DAILY, dailyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(dailyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualDailyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_DAILY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualDailyLicenseUsageActivityDataList.size(), dailyLicenseUsageActivityDataList.size());
  }

  @Test
  @Owner(developers = NITIKA)
  @Category(UnitTests.class)
  public void
  testLicenseUsageDailyDataForHourlyAndDailyRollUp_With_Different_Organizations_should_add_records_with_different_organizations() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfPreviousDay = zonedDateTime.toLocalDate().minusDays(1).atStartOfDay(timeStampZone);
    long fromTimestamp = startOfPreviousDay.toInstant().toEpochMilli();
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();
    List<CILicenseUsage> mongoEventslist = mockLicenseUsageActivityDataWithSameFieldsWithDifferentOrgs();
    Criteria mockCriteria = mock(Criteria.class);
    Query query = mock(Query.class);
    when(mockCriteria.is(mongoEventslist.get(0).getAccountIdentifier())).thenReturn(mockCriteria);
    when(mockCriteria.and(LicenseUsage.LicenseUsageKeys.isProcessed)).thenReturn(mockCriteria);
    when(mockCriteria.is(false)).thenReturn(mockCriteria);
    when(query.addCriteria(mockCriteria)).thenReturn(query);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class))).thenReturn(mongoEventslist);
    hourlyToDailyRollUpLicenseUsageDataProcessor.processLicenseUsageEvent(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class)))
        .thenReturn(mockLicenseUsageActivityDataWithDifferentOrganizations());
    List<LicenseUsageActivityData> dailyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDataWithDifferentOrganizations();
    List<LicenseUsageActivityData> hourlyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDailyDataWithDifferentOrganizations();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(hourlyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualHourlyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(hourlyLicenseUsageActivityDataList.size(), actualHourlyLicenseUsageActivityDataList.size());
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_daily", dailyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_daily", dailyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             "license_usage_daily", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(dailyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualDailyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            "license_usage_daily", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualDailyLicenseUsageActivityDataList.size(), dailyLicenseUsageActivityDataList.size());
  }

  @Test
  @Owner(developers = NITIKA)
  @Category(UnitTests.class)
  public void
  testLicenseUsageDailyDataForHourlyAndDailyRollUp_With_Different_Projects_should_add_records_with_different_projects() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfPreviousDay = zonedDateTime.toLocalDate().minusDays(1).atStartOfDay(timeStampZone);
    long fromTimestamp = startOfPreviousDay.toInstant().toEpochMilli();
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();
    List<CILicenseUsage> mongoEventslist = mockLicenseUsageActivityDataWithSameFieldsWithDifferentProjects();
    Criteria mockCriteria = mock(Criteria.class);
    Query query = mock(Query.class);
    when(mockCriteria.is(mongoEventslist.get(0).getAccountIdentifier())).thenReturn(mockCriteria);
    when(mockCriteria.and(LicenseUsage.LicenseUsageKeys.isProcessed)).thenReturn(mockCriteria);
    when(mockCriteria.is(false)).thenReturn(mockCriteria);
    when(query.addCriteria(mockCriteria)).thenReturn(query);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class))).thenReturn(mongoEventslist);
    hourlyToDailyRollUpLicenseUsageDataProcessor.processLicenseUsageEvent(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);
    when(mongoTemplate.find(eq(query), eq(CILicenseUsage.class)))
        .thenReturn(mockLicenseUsageActivityDataWithDifferentProjects());
    List<LicenseUsageActivityData> dailyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDataWithDifferentProjects();
    List<LicenseUsageActivityData> hourlyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDailyDataWithDifferentProjects();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData(LICENSE_USAGE_HOURLY, hourlyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(hourlyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualHourlyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            LICENSE_USAGE_HOURLY, ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(hourlyLicenseUsageActivityDataList.size(), actualHourlyLicenseUsageActivityDataList.size());
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_daily", dailyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_daily", dailyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             "license_usage_daily", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(dailyLicenseUsageActivityDataList);
    List<LicenseUsageActivityData> actualDailyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            "license_usage_daily", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualDailyLicenseUsageActivityDataList.size(), dailyLicenseUsageActivityDataList.size());
  }

  @Test
  @Owner(developers = NITIKA)
  @Category(UnitTests.class)
  public void
  testLicenseUsageDailyDataForDailyAndMonthlyRollUp_WithDifferentMonths_ShouldAddTwoRecordsInMonthlyTable() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfCurrentMonth =
        zonedDateTime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    ZonedDateTime startOfApril2024 = startOfCurrentMonth.withYear(2024)
                                         .withMonth(4)
                                         .withDayOfMonth(1)
                                         .withHour(0)
                                         .withMinute(0)
                                         .withSecond(0)
                                         .withNano(0);
    long fromTimestamp = startOfApril2024.toInstant().toEpochMilli();
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> dailyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDataWithDifferentMonthsDays();
    List<LicenseUsageActivityData> monthlyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDataForMonthlyRollUp();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_daily", dailyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_daily", dailyLicenseUsageActivityDataList.get(1));
    monthlyToYearlyRollupLicenseUsageDataProcessor.processDailyDataToMonthlyRollup(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             "license_usage_daily", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDataWithDifferentMonthsDays());
    List<LicenseUsageActivityData> actualDailyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            "license_usage_daily", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualDailyLicenseUsageActivityDataList.size(),
        prepareExpectedLicenseUsageActivityDataWithDifferentMonthsDays().size());
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_monthly", monthlyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_monthly", monthlyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             "license_usage_monthly", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDataForMonthlyRollUp());
    List<LicenseUsageActivityData> actualMonthlyLicenseUsageDataList = licenseUsageSQLHelper.fetchRecordsFromPostgres(
        "license_usage_monthly", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualMonthlyLicenseUsageDataList.size(), monthlyLicenseUsageActivityDataList.size());
  }

  @Test
  @Owner(developers = NITIKA)
  @Category(UnitTests.class)
  public void
  testLicenseUsageDailyDataForMonthlyAndYearlyRollUp_WithSameMonthsAndDifferentFields_ShouldAddTwoRecordsInMonthlyTable() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfCurrentMonth =
        zonedDateTime.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    ZonedDateTime startOfMay2024 = startOfCurrentMonth.withYear(2024)
                                       .withMonth(5)
                                       .withDayOfMonth(1)
                                       .withHour(0)
                                       .withMinute(0)
                                       .withSecond(0)
                                       .withNano(0);
    long fromTimestamp = startOfMay2024.toInstant().toEpochMilli();
    long toTimestamp = zonedDateTime.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> dailyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDataWithMonthsDays();
    List<LicenseUsageActivityData> monthlyLicenseUsageActivityDataList =
        prepareExpectedLicenseUsageActivityDataForSameMonthlyRollUp();
    List<LicenseUsageActivityData> yearlyLicenseUsageDataList =
        prepareExpectedLicenseUsageActivityDataForYearlyRollUp();
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_daily", dailyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_daily", dailyLicenseUsageActivityDataList.get(1));
    monthlyToYearlyRollupLicenseUsageDataProcessor.processDailyDataToMonthlyRollup(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             "license_usage_daily", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDataWithMonthsDays());
    List<LicenseUsageActivityData> actualDailyLicenseUsageActivityDataList =
        licenseUsageSQLHelper.fetchRecordsFromPostgres(
            "license_usage_daily", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualDailyLicenseUsageActivityDataList.size(), dailyLicenseUsageActivityDataList.size());
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_monthly", monthlyLicenseUsageActivityDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_monthly", monthlyLicenseUsageActivityDataList.get(1));
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             "license_usage_monthly", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDataForSameMonthlyRollUp());
    List<LicenseUsageActivityData> actualMonthlyLicenseUsageDataList = licenseUsageSQLHelper.fetchRecordsFromPostgres(
        "license_usage_monthly", ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp);
    assertEquals(actualMonthlyLicenseUsageDataList.size(), monthlyLicenseUsageActivityDataList.size());
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_yearly", actualMonthlyLicenseUsageDataList.get(0));
    doNothing()
        .when(licenseUsageSQLHelper)
        .saveLicenseUsageActivityData("license_usage_yearly", actualMonthlyLicenseUsageDataList.get(1));
    ZonedDateTime zonedDateTime2 = ZonedDateTime.now(timeStampZone);
    ZonedDateTime startOfCurrentMonth2 =
        zonedDateTime2.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    ZonedDateTime startOfCurrentYear = startOfCurrentMonth2.withYear(2024)
                                           .withMonth(1)
                                           .withDayOfMonth(1)
                                           .withHour(0)
                                           .withMinute(0)
                                           .withSecond(0)
                                           .withNano(0);
    long fromTimestampForYearRollUp = startOfCurrentYear.toInstant().toEpochMilli();
    when(licenseUsageSQLHelper.fetchRecordsFromPostgres(
             "license_usage_yearly", ACCOUNT_IDENTIFIER, fromTimestampForYearRollUp, toTimestamp))
        .thenReturn(prepareExpectedLicenseUsageActivityDataForYearlyRollUp());
    List<LicenseUsageActivityData> actualYearlyLicenseUsageDataList = licenseUsageSQLHelper.fetchRecordsFromPostgres(
        "license_usage_yearly", ACCOUNT_IDENTIFIER, fromTimestampForYearRollUp, toTimestamp);
    assertEquals(actualYearlyLicenseUsageDataList.size(), yearlyLicenseUsageDataList.size());
  }

  @Test
  @Owner(developers = NEELAM)
  @Category(UnitTests.class)
  public void testUpdateCreditContracts_WhenFreeContractHasEnoughAvailable() {
    int currentUsedCredits = 5;
    // Availability in Free contract, so this should be used first
    CreditDTO freeCreditsDTO1 = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 10, 2);
    List<CreditDTO> freeActiveContracts = Collections.singletonList(freeCreditsDTO1);
    CreditDTO paidCreditsDTO1 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 3);
    List<CreditDTO> paidActiveContracts = Collections.singletonList(paidCreditsDTO1);

    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.FREE, CreditStatus.ACTIVE))
        .thenReturn(freeActiveContracts);
    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.PAID, CreditStatus.ACTIVE))
        .thenReturn(paidActiveContracts);
    CreditDTO updatedFreeCreditsDTO1 = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 10, 7);
    when(creditService.updateCredit(ACCOUNT_IDENTIFIER, freeActiveContracts.get(0))).thenReturn(updatedFreeCreditsDTO1);
    licenseUsageDataProcessor.updateCreditContracts(ACCOUNT_IDENTIFIER, currentUsedCredits, ModuleType.CI.name());
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.FREE), eq(CreditStatus.ACTIVE));
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.PAID), eq(CreditStatus.ACTIVE));
    verify(creditService, times(1)).updateCredit(any(), any());
  }

  @Test
  @Owner(developers = NEELAM)
  @Category(UnitTests.class)
  public void testUpdateCreditContracts_WhenFreeContractHasNothingAvailableAndPaidIsUpdated() {
    int currentUsedCredits = 5;

    // All used in Free Contract, no availability.
    CreditDTO freeCreditsDTO1 = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 10, 10);
    List<CreditDTO> freeActiveContracts = Collections.singletonList(freeCreditsDTO1);
    // Availability in Paid Contract so this should be updated
    CreditDTO paidCreditsDTO1 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 3);
    List<CreditDTO> paidActiveContracts = Collections.singletonList(paidCreditsDTO1);

    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.FREE, CreditStatus.ACTIVE))
        .thenReturn(freeActiveContracts);
    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.PAID, CreditStatus.ACTIVE))
        .thenReturn(paidActiveContracts);
    CreditDTO updatedPaidCreditsDTO1 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 8);
    when(creditService.updateCredit(ACCOUNT_IDENTIFIER, paidActiveContracts.get(0))).thenReturn(updatedPaidCreditsDTO1);

    licenseUsageDataProcessor.updateCreditContracts(ACCOUNT_IDENTIFIER, currentUsedCredits, ModuleType.CI.name());
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.FREE), eq(CreditStatus.ACTIVE));
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.PAID), eq(CreditStatus.ACTIVE));

    verify(creditService, times(1)).updateCredit(any(), any());
  }

  @Test
  @Owner(developers = NEELAM)
  @Category(UnitTests.class)
  public void testUpdateCreditContracts_WhenMultipleContractsShouldBeUpdatedToDistributeUsedCredits() {
    int currentUsedCredits = 5;

    // Availability for 1, should be updated
    CreditDTO freeCreditsDTO1 = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 10, 9);
    List<CreditDTO> freeActiveContracts = Collections.singletonList(freeCreditsDTO1);
    List<CreditDTO> paidActiveContracts = new ArrayList<>();
    // No Availability in this Paid Contract, this shouldn't be updated
    CreditDTO paidCreditsDTO1 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 20);
    paidActiveContracts.add(paidCreditsDTO1);
    // Availability for 1 in this Paid Contract, this should be updated
    CreditDTO paidCreditsDTO2 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 19);
    paidActiveContracts.add(paidCreditsDTO2);
    // Availability for 2 in this Paid Contract, this should be updated
    CreditDTO paidCreditsDTO3 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 18);
    paidActiveContracts.add(paidCreditsDTO3);

    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.FREE, CreditStatus.ACTIVE))
        .thenReturn(freeActiveContracts);
    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.PAID, CreditStatus.ACTIVE))
        .thenReturn(paidActiveContracts);
    CreditDTO updatedFreeCreditsDTO1 = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 10, 10);
    when(creditService.updateCredit(ACCOUNT_IDENTIFIER, freeActiveContracts.get(0))).thenReturn(updatedFreeCreditsDTO1);

    CreditDTO updatedPaidCreditsDTO2 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 20);
    when(creditService.updateCredit(ACCOUNT_IDENTIFIER, paidActiveContracts.get(1))).thenReturn(updatedPaidCreditsDTO2);

    CreditDTO updatedPaidCreditsDTO3 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 20);
    when(creditService.updateCredit(ACCOUNT_IDENTIFIER, paidActiveContracts.get(1))).thenReturn(updatedPaidCreditsDTO3);

    licenseUsageDataProcessor.updateCreditContracts(ACCOUNT_IDENTIFIER, currentUsedCredits, ModuleType.CI.name());
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.FREE), eq(CreditStatus.ACTIVE));
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.PAID), eq(CreditStatus.ACTIVE));
    verify(creditService, times(3)).updateCredit(any(), any());
  }
  @Test
  @Owner(developers = NEELAM)
  @Category(UnitTests.class)
  public void testUpdateCreditContracts_WhenBothFreeAndPaidHasNothingAvailableAndNoneShouldBeUpdated() {
    int currentUsedCredits = 5;
    // No availability in FREE or PAID contracts, none should be updated
    CreditDTO freeCreditsDTO1 = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 10, 10);
    List<CreditDTO> freeActiveContracts = Collections.singletonList(freeCreditsDTO1);
    CreditDTO paidCreditsDTO1 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 20);
    List<CreditDTO> paidActiveContracts = Collections.singletonList(paidCreditsDTO1);

    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.FREE, CreditStatus.ACTIVE))
        .thenReturn(freeActiveContracts);
    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.PAID, CreditStatus.ACTIVE))
        .thenReturn(paidActiveContracts);

    licenseUsageDataProcessor.updateCreditContracts(ACCOUNT_IDENTIFIER, currentUsedCredits, ModuleType.CI.name());
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.FREE), eq(CreditStatus.ACTIVE));
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.PAID), eq(CreditStatus.ACTIVE));

    // None of the contracts should be updated since no availability in any
    verify(creditService, times(0)).updateCredit(any(), any());
  }

  @Test
  @Owner(developers = NEELAM)
  @Category(UnitTests.class)
  public void testUpdateCreditContracts_WhenOverUsageIsReportedShouldBeUpdatedInMongoDB() {
    int currentUsedCredits = 5;

    // Availability for 1, should be updated
    CreditDTO freeCreditsDTO1 = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 10, 9);
    List<CreditDTO> freeActiveContracts = Collections.singletonList(freeCreditsDTO1);
    List<CreditDTO> paidActiveContracts = new ArrayList<>();
    // No Availability in this Paid Contract, this shouldn't be updated
    CreditDTO paidCreditsDTO1 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 20);
    paidActiveContracts.add(paidCreditsDTO1);
    // Availability for 1 in this Paid Contract, this should be updated
    CreditDTO paidCreditsDTO2 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 19);
    paidActiveContracts.add(paidCreditsDTO2);
    // Availability for 2 in this Paid Contract, this should be updated
    CreditDTO paidCreditsDTO3 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 18);
    paidActiveContracts.add(paidCreditsDTO3);

    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.FREE, CreditStatus.ACTIVE))
        .thenReturn(freeActiveContracts);
    when(creditService.getCredits(ACCOUNT_IDENTIFIER, CreditType.PAID, CreditStatus.ACTIVE))
        .thenReturn(paidActiveContracts);
    CreditDTO updatedFreeCreditsDTO1 = prepareCreditsDTO(CreditType.FREE, CreditStatus.ACTIVE, 10, 10);
    when(creditService.updateCredit(ACCOUNT_IDENTIFIER, freeActiveContracts.get(0))).thenReturn(updatedFreeCreditsDTO1);

    CreditDTO updatedPaidCreditsDTO2 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 20);
    when(creditService.updateCredit(ACCOUNT_IDENTIFIER, paidActiveContracts.get(1))).thenReturn(updatedPaidCreditsDTO2);

    CreditDTO updatedPaidCreditsDTO3 = prepareCreditsDTO(CreditType.PAID, CreditStatus.ACTIVE, 20, 20);
    when(creditService.updateCredit(ACCOUNT_IDENTIFIER, paidActiveContracts.get(1))).thenReturn(updatedPaidCreditsDTO3);

    // Remaining 1 overused credit should be updated in creditOverUsage collection
    when(mongoTemplate.findOne(any(Query.class), eq(CreditOverUsageEntity.class))).thenReturn(null);
    when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), eq(CreditOverUsageEntity.class)))
        .thenReturn(null);

    licenseUsageDataProcessor.updateCreditContracts(ACCOUNT_IDENTIFIER, currentUsedCredits, ModuleType.CI.name());
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.FREE), eq(CreditStatus.ACTIVE));
    verify(creditService, times(1)).getCredits(eq(ACCOUNT_IDENTIFIER), eq(CreditType.PAID), eq(CreditStatus.ACTIVE));
    verify(creditService, times(3)).updateCredit(any(), any());
    verify(mongoTemplate, times(1)).findOne(any(Query.class), eq(CreditOverUsageEntity.class));
    verify(mongoTemplate, times(1))
        .findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class),
            eq(CreditOverUsageEntity.class));
  }

  @Test
  @Owner(developers = NEELAM)
  @Category(UnitTests.class)
  public void testToBeMarkedProcessedListContainsEventIds() {
    // Arrange
    long fromTimestamp = System.currentTimeMillis() - 86400000; // 1 day ago
    long toTimestamp = System.currentTimeMillis();
    List<LicenseUsage> unProcessedEvents = new ArrayList<>();
    List<String> expectedToBeMarkedProcessed = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      unProcessedEvents.add(CILicenseUsage.builder()
                                .id("id" + i)
                                .accountIdentifier(ACCOUNT_IDENTIFIER)
                                .moduleType(ModuleType.CI)
                                .orgIdentifier("org1")
                                .projectIdentifier("proj1")
                                .pipelineIdentifier("pipe" + i)
                                .stageIdentifier("stage" + i)
                                .resourceClass(ResourceClass.FLEX)
                                .osType(OSType.LINUX)
                                .lastBuildTimestamp(System.currentTimeMillis())
                                .build());
      expectedToBeMarkedProcessed.add("id" + i);
    }

    // Set the method to return our mock data
    when(licenseUsageDataProcessor.findAllUnprocessedEventsByAccountId(ACCOUNT_IDENTIFIER, DOCS_BATCH_SIZE))
        .thenReturn(unProcessedEvents.stream());

    List<String> toBeMarkedProcessed = new ArrayList<>();
    HourlyToDailyRollUpLicenseUsageDataProcessor hourlyToDailyRollUpLicenseUsageDataProcessor =
        spy(licenseUsageDataProcessor);

    doAnswer(invocation -> {
      List<String> documentIds = invocation.getArgument(0);
      toBeMarkedProcessed.addAll(documentIds);
      return null; // return type is void, so return null
    })
        .when(hourlyToDailyRollUpLicenseUsageDataProcessor)
        .markEventsAsProcessed(anyList());

    // Act
    hourlyToDailyRollUpLicenseUsageDataProcessor.processLicenseUsageEvent(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);

    // Verify that toBeMarkedProcessed contains the correct ID
    assertEquals(expectedToBeMarkedProcessed, toBeMarkedProcessed);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testProcessLicenseUsageEvent_WhenNonCIModuleType_ShouldSkipAndNotProcess() {
    // Arrange
    long fromTimestamp = System.currentTimeMillis() - 86400000; // 1 day ago
    long toTimestamp = System.currentTimeMillis();
    List<LicenseUsage> unProcessedEvents = new ArrayList<>();

    // Create a mock non-CI LicenseUsage (e.g., CD module type)
    // This simulates a LicenseUsage event that is NOT a CILicenseUsage
    LicenseUsage nonCILicenseUsage = mock(LicenseUsage.class);
    when(nonCILicenseUsage.getId()).thenReturn("non-ci-event-123");
    when(nonCILicenseUsage.getAccountIdentifier()).thenReturn(ACCOUNT_IDENTIFIER);
    when(nonCILicenseUsage.getModuleType()).thenReturn(ModuleType.IACM);

    unProcessedEvents.add(nonCILicenseUsage);

    // Set the method to return our mock data
    when(licenseUsageDataProcessor.findAllUnprocessedEventsByAccountId(ACCOUNT_IDENTIFIER, DOCS_BATCH_SIZE))
        .thenReturn(unProcessedEvents.stream());

    List<String> toBeMarkedProcessed = new ArrayList<>();
    HourlyToDailyRollUpLicenseUsageDataProcessor hourlyToDailyRollUpLicenseUsageDataProcessor =
        spy(licenseUsageDataProcessor);

    doAnswer(invocation -> {
      List<String> documentIds = invocation.getArgument(0);
      toBeMarkedProcessed.addAll(documentIds);
      return null; // return type is void, so return null
    })
        .when(hourlyToDailyRollUpLicenseUsageDataProcessor)
        .markEventsAsProcessed(anyList());

    // Act
    hourlyToDailyRollUpLicenseUsageDataProcessor.processLicenseUsageEvent(
        ACCOUNT_IDENTIFIER, fromTimestamp, toTimestamp, DOCS_BATCH_SIZE);

    // Assert - Non-CI event should be skipped and not marked as processed
    assertEquals(0, toBeMarkedProcessed.size());
  }

  private List<LicenseUsageActivityData> prepareMockedLicenseUsageActivityDataListWithDifferentTimestamp1() {
    List<LicenseUsageActivityData> list = new ArrayList<>();
    ZoneId zoneId = ZoneId.systemDefault();
    ZonedDateTime[] specificTimes = new ZonedDateTime[] {
        LocalDateTime.now().minusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0).atZone(zoneId),
        LocalDateTime.now().minusDays(1).withHour(23).withMinute(0).withSecond(0).withNano(0).atZone(zoneId),
        LocalDateTime.now().withHour(1).withMinute(0).withSecond(0).withNano(0).atZone(zoneId), // Today 1 AM
        LocalDateTime.now().withHour(8).withMinute(0).withSecond(0).withNano(0).atZone(zoneId), // Today 8 AM
        LocalDateTime.now().withHour(13).withMinute(0).withSecond(0).withNano(0).atZone(zoneId) // Today 1 PM
    };
    for (int i = 0; i < specificTimes.length; i++) {
      LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                          .organizationIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                          .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                          .moduleType(ModuleType.CI.name())
                                          .usedCredits(2 * (i + 1))
                                          .createdAt(System.currentTimeMillis())
                                          .utcTimestamp(specificTimes[i].toInstant().toEpochMilli())
                                          .ciOsType(OSType.WINDOWS.name())
                                          .ciResourceClass(ResourceClass.SMALL.name())
                                          .build();
      list.add(data);
    }
    return list;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDailyData1() {
    List<LicenseUsageActivityData> list = new ArrayList<>();
    ZoneId zoneId = ZoneId.systemDefault();
    LocalDateTime yesterdayStartOfDay =
        LocalDateTime.now().minusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    LocalDateTime todayStartOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(6)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(yesterdayStartOfDay.atZone(zoneId).toInstant().toEpochMilli())
                                        .ciOsType(OSType.WINDOWS.name())
                                        .ciResourceClass(ResourceClass.SMALL.name())
                                        .build();
    list.add(data);
    LicenseUsageActivityData data1 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(24)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(todayStartOfDay.atZone(zoneId).toInstant().toEpochMilli())
                                         .ciOsType(OSType.WINDOWS.name())
                                         .ciResourceClass(ResourceClass.SMALL.name())
                                         .build();
    list.add(data1);
    return list;
  }

  private List<LicenseUsageActivityData> mockLicenseUsageActivityData() {
    List<LicenseUsageActivityData> list = new ArrayList<>();
    ZoneId zoneId = ZoneId.systemDefault();
    LocalDateTime now = LocalDateTime.now();
    ZonedDateTime[] specificTimes = new ZonedDateTime[] {
        now.minusDays(1).withHour(23).withMinute(0).withSecond(0).withNano(0).atZone(zoneId), // Yesterday 11:58 PM
        now.minusDays(1).withHour(23).withMinute(0).withSecond(0).withNano(0).atZone(zoneId), // Yesterday 11:59 PM
        now.withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(zoneId), // Today 12:00 AM
        now.withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(zoneId), // Today 12:01 AM
        now.withHour(0).withMinute(0).withSecond(0).withNano(0).atZone(zoneId) // Today 12:02 AM
    };
    for (int i = 0; i < specificTimes.length; i++) {
      LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                          .accountIdentifier(ACCOUNT_IDENTIFIER)
                                          .organizationIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJECT_IDENTIFIER)
                                          .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                          .moduleType(ModuleType.CI.name())
                                          .usedCredits(2 * (i + 1))
                                          .createdAt(System.currentTimeMillis())
                                          .utcTimestamp(specificTimes[i].toInstant().toEpochMilli())
                                          .ciOsType(OSType.WINDOWS.name())
                                          .ciResourceClass(ResourceClass.SMALL.name())
                                          .build();
      list.add(data);
    }
    return list;
  }

  private List<CILicenseUsage> mockLicenseUsageActivityData2() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(23).withMinute(58).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(22).withMinute(59).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<CILicenseUsage> ciLicenseUsageList = new ArrayList<>();
    CILicenseUsage data = CILicenseUsage.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier("Project1")
                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                              .moduleType(ModuleType.CI)
                              .buildMinutes(2)
                              .createdAt(System.currentTimeMillis())
                              .lastBuildTimestamp(timestamp)
                              .osType(OSType.WINDOWS)
                              .resourceClass(ResourceClass.SMALL)
                              .architectureType(ArchitectureType.AMD64)
                              .buildInfraType(BuildInfraType.CLOUD)
                              .isProcessed(false)
                              .build();
    ciLicenseUsageList.add(data);
    CILicenseUsage data2 = CILicenseUsage.builder()
                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJECT_IDENTIFIER)
                               .pipelineIdentifier(PIPELINE_IDENTIFIER)
                               .moduleType(ModuleType.CI)
                               .buildMinutes(2)
                               .createdAt(System.currentTimeMillis())
                               .lastBuildTimestamp(timestamp2)
                               .osType(OSType.WINDOWS)
                               .resourceClass(ResourceClass.SMALL)
                               .architectureType(ArchitectureType.AMD64)
                               .buildInfraType(BuildInfraType.CLOUD)
                               .isProcessed(false)
                               .build();
    ciLicenseUsageList.add(data2);
    return ciLicenseUsageList;
  }

  private List<CILicenseUsage> mockLicenseUsageActivityDataWithDifferentHours() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(19).withMinute(58).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(18).withMinute(59).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<CILicenseUsage> ciLicenseUsageList = new ArrayList<>();
    CILicenseUsage data = CILicenseUsage.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                              .moduleType(ModuleType.CI)
                              .buildMinutes(2)
                              .createdAt(System.currentTimeMillis())
                              .lastBuildTimestamp(timestamp)
                              .osType(OSType.WINDOWS)
                              .resourceClass(ResourceClass.SMALL)
                              .architectureType(ArchitectureType.AMD64)
                              .buildInfraType(BuildInfraType.CLOUD)
                              .isProcessed(false)
                              .build();
    ciLicenseUsageList.add(data);
    CILicenseUsage data2 = CILicenseUsage.builder()
                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJECT_IDENTIFIER)
                               .pipelineIdentifier(PIPELINE_IDENTIFIER)
                               .moduleType(ModuleType.CI)
                               .buildMinutes(2)
                               .createdAt(System.currentTimeMillis())
                               .lastBuildTimestamp(timestamp2)
                               .osType(OSType.WINDOWS)
                               .resourceClass(ResourceClass.SMALL)
                               .architectureType(ArchitectureType.AMD64)
                               .buildInfraType(BuildInfraType.CLOUD)
                               .isProcessed(false)
                               .build();
    ciLicenseUsageList.add(data2);
    return ciLicenseUsageList;
  }

  private List<CILicenseUsage> mockLicenseUsageActivityDataWithDifferentOrganizations() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(23).withMinute(58).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(23).withMinute(59).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<CILicenseUsage> ciLicenseUsageList = new ArrayList<>();
    CILicenseUsage data = CILicenseUsage.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                              .moduleType(ModuleType.CI)
                              .buildMinutes(2)
                              .createdAt(System.currentTimeMillis())
                              .lastBuildTimestamp(timestamp)
                              .osType(OSType.WINDOWS)
                              .resourceClass(ResourceClass.SMALL)
                              .architectureType(ArchitectureType.AMD64)
                              .buildInfraType(BuildInfraType.CLOUD)
                              .isProcessed(false)
                              .build();
    ciLicenseUsageList.add(data);
    CILicenseUsage data2 = CILicenseUsage.builder()
                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                               .orgIdentifier("Org2")
                               .projectIdentifier(PROJECT_IDENTIFIER)
                               .pipelineIdentifier(PIPELINE_IDENTIFIER)
                               .moduleType(ModuleType.CI)
                               .buildMinutes(2)
                               .createdAt(System.currentTimeMillis())
                               .lastBuildTimestamp(timestamp2)
                               .osType(OSType.WINDOWS)
                               .resourceClass(ResourceClass.SMALL)
                               .architectureType(ArchitectureType.AMD64)
                               .buildInfraType(BuildInfraType.CLOUD)
                               .isProcessed(false)
                               .build();
    ciLicenseUsageList.add(data2);
    return ciLicenseUsageList;
  }

  private List<CILicenseUsage> mockLicenseUsageActivityDataWithDifferentStageIdentifiers() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(23).withMinute(58).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(23).withMinute(59).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<CILicenseUsage> ciLicenseUsageList = new ArrayList<>();
    CILicenseUsage data = CILicenseUsage.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                              .stageIdentifier(STAGE_IDENTIFIER)
                              .moduleType(ModuleType.CI)
                              .buildMinutes(2)
                              .createdAt(System.currentTimeMillis())
                              .lastBuildTimestamp(timestamp)
                              .osType(OSType.WINDOWS)
                              .resourceClass(ResourceClass.SMALL)
                              .architectureType(ArchitectureType.AMD64)
                              .buildInfraType(BuildInfraType.CLOUD)
                              .isProcessed(false)
                              .build();
    ciLicenseUsageList.add(data);
    CILicenseUsage data2 = CILicenseUsage.builder()
                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJECT_IDENTIFIER)
                               .pipelineIdentifier(PIPELINE_IDENTIFIER)
                               .stageIdentifier("stage2")
                               .moduleType(ModuleType.CI)
                               .buildMinutes(2)
                               .createdAt(System.currentTimeMillis())
                               .lastBuildTimestamp(timestamp2)
                               .osType(OSType.WINDOWS)
                               .resourceClass(ResourceClass.SMALL)
                               .architectureType(ArchitectureType.AMD64)
                               .buildInfraType(BuildInfraType.CLOUD)
                               .isProcessed(false)
                               .build();
    ciLicenseUsageList.add(data2);
    return ciLicenseUsageList;
  }

  private List<CILicenseUsage> mockLicenseUsageActivityDataWithDifferentProjects() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(15).withHour(23).withMinute(58).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(15).withHour(23).withMinute(59).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<CILicenseUsage> ciLicenseUsageList = new ArrayList<>();
    CILicenseUsage data = CILicenseUsage.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                              .moduleType(ModuleType.CI)
                              .buildMinutes(2)
                              .createdAt(System.currentTimeMillis())
                              .lastBuildTimestamp(timestamp)
                              .osType(OSType.WINDOWS)
                              .resourceClass(ResourceClass.SMALL)
                              .architectureType(ArchitectureType.AMD64)
                              .buildInfraType(BuildInfraType.CLOUD)
                              .isProcessed(false)
                              .build();
    ciLicenseUsageList.add(data);
    CILicenseUsage data2 = CILicenseUsage.builder()
                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier("Project2")
                               .pipelineIdentifier(PIPELINE_IDENTIFIER)
                               .moduleType(ModuleType.CI)
                               .buildMinutes(2)
                               .createdAt(System.currentTimeMillis())
                               .lastBuildTimestamp(timestamp2)
                               .osType(OSType.WINDOWS)
                               .resourceClass(ResourceClass.SMALL)
                               .architectureType(ArchitectureType.AMD64)
                               .buildInfraType(BuildInfraType.CLOUD)
                               .isProcessed(false)
                               .build();
    ciLicenseUsageList.add(data2);
    return ciLicenseUsageList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDailyData2() {
    List<LicenseUsageActivityData> list = new ArrayList<>();
    ZoneId zoneId = ZoneId.systemDefault();
    LocalDateTime yesterdayStartOfDay =
        LocalDateTime.now().minusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    LocalDateTime todayStartOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(6)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(yesterdayStartOfDay.atZone(zoneId).toInstant().toEpochMilli())
                                        .ciOsType(OSType.WINDOWS.name())
                                        .ciResourceClass(ResourceClass.SMALL.name())
                                        .build();
    list.add(data);
    LicenseUsageActivityData data1 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(24)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(todayStartOfDay.atZone(zoneId).toInstant().toEpochMilli())
                                         .ciOsType(OSType.WINDOWS.name())
                                         .ciResourceClass(ResourceClass.SMALL.name())
                                         .build();
    list.add(data1);
    return list;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDailyData3() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(21).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(21).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(2)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType(OSType.WINDOWS.name())
                                        .ciResourceClass(ResourceClass.SMALL.name())
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(2)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType(OSType.WINDOWS.name())
                                         .ciResourceClass(ResourceClass.SMALL.name())
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDailyData4() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(21).withHour(11).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(21).withHour(11).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType("CI")
                                        .usedCredits(2)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType(OSType.WINDOWS.name())
                                        .ciResourceClass(ResourceClass.SMALL.name())
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(2)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType(OSType.WINDOWS.name())
                                         .ciResourceClass(ResourceClass.SMALL.name())
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<CILicenseUsage> mockLicenseUsageActivityDataWithSameFields() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(23).withMinute(58).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(22).withMinute(59).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<CILicenseUsage> ciLicenseUsageList = new ArrayList<>();
    CILicenseUsage data = CILicenseUsage.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                              .moduleType(ModuleType.CI)
                              .buildMinutes(2)
                              .createdAt(System.currentTimeMillis())
                              .lastBuildTimestamp(timestamp)
                              .osType(OSType.WINDOWS)
                              .resourceClass(ResourceClass.SMALL)
                              .architectureType(ArchitectureType.AMD64)
                              .buildInfraType(BuildInfraType.CLOUD)
                              .isProcessed(false)
                              .build();
    ciLicenseUsageList.add(data);
    CILicenseUsage data2 = CILicenseUsage.builder()
                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJECT_IDENTIFIER)
                               .pipelineIdentifier(PIPELINE_IDENTIFIER)
                               .moduleType(ModuleType.CI)
                               .buildMinutes(2)
                               .createdAt(System.currentTimeMillis())
                               .lastBuildTimestamp(timestamp2)
                               .osType(OSType.WINDOWS)
                               .resourceClass(ResourceClass.SMALL)
                               .architectureType(ArchitectureType.AMD64)
                               .buildInfraType(BuildInfraType.CLOUD)
                               .isProcessed(false)
                               .build();
    ciLicenseUsageList.add(data2);
    return ciLicenseUsageList;
  }

  private List<CILicenseUsage> mockLicenseUsageActivityDataWithSameFieldsWithDifferentOrgs() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(23).withMinute(58).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(22).withMinute(59).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<CILicenseUsage> ciLicenseUsageList = new ArrayList<>();
    CILicenseUsage data = CILicenseUsage.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                              .moduleType(ModuleType.CI)
                              .buildMinutes(2)
                              .createdAt(System.currentTimeMillis())
                              .lastBuildTimestamp(timestamp)
                              .osType(OSType.WINDOWS)
                              .resourceClass(ResourceClass.SMALL)
                              .architectureType(ArchitectureType.AMD64)
                              .buildInfraType(BuildInfraType.CLOUD)
                              .isProcessed(false)
                              .build();
    ciLicenseUsageList.add(data);
    CILicenseUsage data2 = CILicenseUsage.builder()
                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                               .orgIdentifier("Org2")
                               .projectIdentifier(PROJECT_IDENTIFIER)
                               .pipelineIdentifier(PIPELINE_IDENTIFIER)
                               .moduleType(ModuleType.CI)
                               .buildMinutes(2)
                               .createdAt(System.currentTimeMillis())
                               .lastBuildTimestamp(timestamp2)
                               .osType(OSType.WINDOWS)
                               .resourceClass(ResourceClass.SMALL)
                               .architectureType(ArchitectureType.AMD64)
                               .buildInfraType(BuildInfraType.CLOUD)
                               .isProcessed(false)
                               .build();
    ciLicenseUsageList.add(data2);
    return ciLicenseUsageList;
  }

  private List<CILicenseUsage> mockLicenseUsageActivityDataWithSameFieldsWithDifferentStageIdentifiers() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(23).withMinute(58).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(22).withMinute(59).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<CILicenseUsage> ciLicenseUsageList = new ArrayList<>();
    CILicenseUsage data = CILicenseUsage.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                              .stageIdentifier(STAGE_IDENTIFIER)
                              .moduleType(ModuleType.CI)
                              .buildMinutes(2)
                              .createdAt(System.currentTimeMillis())
                              .lastBuildTimestamp(timestamp)
                              .osType(OSType.WINDOWS)
                              .resourceClass(ResourceClass.SMALL)
                              .architectureType(ArchitectureType.AMD64)
                              .buildInfraType(BuildInfraType.CLOUD)
                              .isProcessed(false)
                              .build();
    ciLicenseUsageList.add(data);
    CILicenseUsage data2 = CILicenseUsage.builder()
                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJECT_IDENTIFIER)
                               .pipelineIdentifier(PIPELINE_IDENTIFIER)
                               .stageIdentifier("stage2")
                               .moduleType(ModuleType.CI)
                               .buildMinutes(2)
                               .createdAt(System.currentTimeMillis())
                               .lastBuildTimestamp(timestamp2)
                               .osType(OSType.WINDOWS)
                               .resourceClass(ResourceClass.SMALL)
                               .architectureType(ArchitectureType.AMD64)
                               .buildInfraType(BuildInfraType.CLOUD)
                               .isProcessed(false)
                               .build();
    ciLicenseUsageList.add(data2);
    return ciLicenseUsageList;
  }
  private List<LicenseUsage> prepareParallelRunsOfPipelineLicenseUsageEvents() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(23).withMinute(58).withSecond(0).withNano(
            0);
    long buildTimestamp1 = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(22).withMinute(59).withSecond(0).withNano(
            0);

    long buildTimestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsage> ciLicenseUsageEvents = new ArrayList<>();
    CILicenseUsage linuxFlexUsageEvent = CILicenseUsage.builder()
                                             .accountIdentifier(ACCOUNT_IDENTIFIER)
                                             .orgIdentifier(ORG_IDENTIFIER)
                                             .projectIdentifier(PROJECT_IDENTIFIER)
                                             .pipelineIdentifier(LINUX_PIPELINE_IDENTIFIER)
                                             .stageIdentifier(STAGE_IDENTIFIER)
                                             .moduleType(ModuleType.CI)
                                             .buildMinutes(1)
                                             .createdAt(System.currentTimeMillis())
                                             .lastBuildTimestamp(buildTimestamp1)
                                             .osType(OSType.LINUX)
                                             .developer(new Developer("Test Name", "test@harness.io"))
                                             .resourceClass(ResourceClass.FLEX)
                                             .architectureType(ArchitectureType.AMD64)
                                             .buildInfraType(BuildInfraType.CLOUD)
                                             .isProcessed(false)
                                             .build();
    CILicenseUsage macOsFlexUsageEvent = CILicenseUsage.builder()
                                             .accountIdentifier(ACCOUNT_IDENTIFIER)
                                             .orgIdentifier(ORG_IDENTIFIER)
                                             .projectIdentifier(PROJECT_IDENTIFIER)
                                             .pipelineIdentifier(MACOS_PIPELINE_IDENTIFIER)
                                             .stageIdentifier(STAGE_IDENTIFIER)
                                             .moduleType(ModuleType.CI)
                                             .buildMinutes(1)
                                             .createdAt(System.currentTimeMillis())
                                             .lastBuildTimestamp(buildTimestamp2)
                                             .osType(OSType.MACOS)
                                             .developer(new Developer("Test Name", "test@harness.io"))
                                             .resourceClass(ResourceClass.FLEX)
                                             .architectureType(ArchitectureType.ARM64)
                                             .buildInfraType(BuildInfraType.CLOUD)
                                             .isProcessed(false)
                                             .build();

    CILicenseUsage windowsFlexLicenseUsageEvent = CILicenseUsage.builder()
                                                      .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                      .orgIdentifier(ORG_IDENTIFIER)
                                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                                      .pipelineIdentifier(MACOS_PIPELINE_IDENTIFIER)
                                                      .stageIdentifier(STAGE_IDENTIFIER)
                                                      .moduleType(ModuleType.CI)
                                                      .buildMinutes(1)
                                                      .createdAt(System.currentTimeMillis())
                                                      .lastBuildTimestamp(buildTimestamp2)
                                                      .osType(OSType.WINDOWS)
                                                      .developer(new Developer("Test Name", "test@harness.io"))
                                                      .resourceClass(ResourceClass.FLEX)
                                                      .architectureType(ArchitectureType.ARM64)
                                                      .buildInfraType(BuildInfraType.CLOUD)
                                                      .isProcessed(false)
                                                      .build();

    ciLicenseUsageEvents.add(linuxFlexUsageEvent);
    ciLicenseUsageEvents.add(macOsFlexUsageEvent);
    ciLicenseUsageEvents.add(windowsFlexLicenseUsageEvent);
    return ciLicenseUsageEvents;
  }

  private List<CILicenseUsage> mockLicenseUsageActivityDataWithSameFieldsWithDifferentProjects() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(23).withMinute(58).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(22).withMinute(59).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<CILicenseUsage> ciLicenseUsageList = new ArrayList<>();
    CILicenseUsage data = CILicenseUsage.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                              .moduleType(ModuleType.CI)
                              .buildMinutes(2)
                              .createdAt(System.currentTimeMillis())
                              .lastBuildTimestamp(timestamp)
                              .osType(OSType.WINDOWS)
                              .resourceClass(ResourceClass.SMALL)
                              .architectureType(ArchitectureType.AMD64)
                              .buildInfraType(BuildInfraType.CLOUD)
                              .isProcessed(false)
                              .build();
    ciLicenseUsageList.add(data);
    CILicenseUsage data2 = CILicenseUsage.builder()
                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier("Project2")
                               .pipelineIdentifier(PIPELINE_IDENTIFIER)
                               .moduleType(ModuleType.CI)
                               .buildMinutes(2)
                               .createdAt(System.currentTimeMillis())
                               .lastBuildTimestamp(timestamp2)
                               .osType(OSType.WINDOWS)
                               .resourceClass(ResourceClass.SMALL)
                               .architectureType(ArchitectureType.AMD64)
                               .buildInfraType(BuildInfraType.CLOUD)
                               .isProcessed(false)
                               .build();
    ciLicenseUsageList.add(data2);
    return ciLicenseUsageList;
  }

  private List<CILicenseUsage> mockLicenseUsageActivityDataForTestingMultiplePipelines() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(19).withMinute(58).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(18).withMinute(59).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<CILicenseUsage> ciLicenseUsageList = new ArrayList<>();
    CILicenseUsage data = CILicenseUsage.builder()
                              .accountIdentifier(ACCOUNT_IDENTIFIER)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJECT_IDENTIFIER)
                              .pipelineIdentifier(PIPELINE_IDENTIFIER)
                              .moduleType(ModuleType.CI)
                              .buildMinutes(2)
                              .createdAt(System.currentTimeMillis())
                              .lastBuildTimestamp(timestamp)
                              .osType(OSType.WINDOWS)
                              .resourceClass(ResourceClass.SMALL)
                              .architectureType(ArchitectureType.AMD64)
                              .buildInfraType(BuildInfraType.CLOUD)
                              .isProcessed(false)
                              .build();
    ciLicenseUsageList.add(data);
    CILicenseUsage data2 = CILicenseUsage.builder()
                               .accountIdentifier(ACCOUNT_IDENTIFIER)
                               .orgIdentifier(ORG_IDENTIFIER)
                               .projectIdentifier(PROJECT_IDENTIFIER)
                               .pipelineIdentifier(PIPELINE_IDENTIFIER)
                               .moduleType(ModuleType.CI)
                               .buildMinutes(2)
                               .createdAt(System.currentTimeMillis())
                               .lastBuildTimestamp(timestamp2)
                               .osType(OSType.WINDOWS)
                               .resourceClass(ResourceClass.SMALL)
                               .architectureType(ArchitectureType.AMD64)
                               .buildInfraType(BuildInfraType.CLOUD)
                               .isProcessed(false)
                               .build();
    ciLicenseUsageList.add(data2);
    return ciLicenseUsageList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDataWithSameDayAndDifferentHour() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType("CI")
                                        .usedCredits(4)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType("WINDOWS")
                                        .ciResourceClass("SMALL")
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType("CI")
                                         .usedCredits(4)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType("WINDOWS")
                                         .ciResourceClass("SMALL")
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }
  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDataWithDifferentOrganizations() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(4)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType(OSType.WINDOWS.name())
                                        .ciResourceClass(ResourceClass.SMALL.name())
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier("Org2")
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(4)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType(OSType.WINDOWS.name())
                                         .ciResourceClass(ResourceClass.SMALL.name())
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDataWithDifferentProjects() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(4)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType(OSType.WINDOWS.name())
                                        .ciResourceClass(ResourceClass.SMALL.name())
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier("Project2")
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(4)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType(OSType.WINDOWS.name())
                                         .ciResourceClass(ResourceClass.SMALL.name())
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDataWithDifferentStageIdentifiers() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(21).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(21).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .stageIdentifier(STAGE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(4)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType(OSType.WINDOWS.name())
                                        .ciResourceClass(ResourceClass.SMALL.name())
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .stageIdentifier("stage2")
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(4)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType(OSType.WINDOWS.name())
                                         .ciResourceClass(ResourceClass.SMALL.name())
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDailyDataWithSameDayAndDifferentHour() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(11).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(10).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(2)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType(OSType.WINDOWS.name())
                                        .ciResourceClass(ResourceClass.SMALL.name())
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(2)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType(OSType.WINDOWS.name())
                                         .ciResourceClass(ResourceClass.SMALL.name())
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData>
  prepareLicenseUsageActivityDataWithLinuxAndMacOsTypesForSameDayDifferentHours() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(11).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(10).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(LINUX_PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(2)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType(OSType.LINUX.name())
                                        .ciResourceClass(ResourceClass.FLEX.name())
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(MACOS_PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(60)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType(OSType.MACOS.name())
                                         .ciResourceClass(ResourceClass.FLEX.name())
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDailyDataWithDifferentHour() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(7).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(6).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(2)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType(OSType.WINDOWS.name())
                                        .ciResourceClass(ResourceClass.SMALL.name())
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(2)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType(OSType.WINDOWS.name())
                                         .ciResourceClass(ResourceClass.SMALL.name())
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDailyDataWithDifferentOrganizations() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(11).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(10).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType("CI")
                                        .usedCredits(2)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType(OSType.WINDOWS.name())
                                        .ciResourceClass(ResourceClass.SMALL.name())
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier("Org2")
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(2)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType(OSType.WINDOWS.name())
                                         .ciResourceClass(ResourceClass.SMALL.name())
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDailyDataWithDifferentProjects() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(11).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(10).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(2)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType("WINDOWS")
                                        .ciResourceClass("SMALL")
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier("Project2")
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(2)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType("WINDOWS")
                                         .ciResourceClass("SMALL")
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDailyDataWithDifferentStageIdentifiers() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(11).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(31).withHour(10).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .stageIdentifier(STAGE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(2)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType("WINDOWS")
                                        .ciResourceClass("SMALL")
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier("Org2")
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .stageIdentifier("stage2")
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(2)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType("WINDOWS")
                                         .ciResourceClass("SMALL")
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDailyData() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(21).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(8)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType("WINDOWS")
                                        .ciResourceClass("SMALL")
                                        .build();
    licenseUsageActivityDataList.add(data);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDataWithDifferentMonthsDays() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(4).withDayOfMonth(30).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType("CI")
                                        .usedCredits(4)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType("WINDOWS")
                                        .ciResourceClass("SMALL")
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(4)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType("WINDOWS")
                                         .ciResourceClass("SMALL")
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDataWithMonthsDays() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(21).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(21).withHour(0).withMinute(0).withSecond(0).withNano(
            0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier(PROJECT_IDENTIFIER)
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType(ModuleType.CI.name())
                                        .usedCredits(4)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType("WINDOWS")
                                        .ciResourceClass("SMALL")
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(4)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType("WINDOWS")
                                         .ciResourceClass("SMALL")
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDataForMonthlyRollUp() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(4).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier("Project1")
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType("CI")
                                        .usedCredits(4)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType("WINDOWS")
                                        .ciResourceClass("SMALL")
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType(ModuleType.CI.name())
                                         .usedCredits(4)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType("WINDOWS")
                                         .ciResourceClass("SMALL")
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDataForSameMonthlyRollUp() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(5).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier("Project1")
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType("CI")
                                        .usedCredits(4)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType("WINDOWS")
                                        .ciResourceClass("SMALL")
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType("CI")
                                         .usedCredits(4)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType("WINDOWS")
                                         .ciResourceClass("SMALL")
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsageActivityData> prepareExpectedLicenseUsageActivityDataForYearlyRollUp() {
    ZoneId timeStampZone = ZoneId.of("UTC");
    ZonedDateTime zonedDateTime = ZonedDateTime.now(timeStampZone);
    ZonedDateTime specificDateTime =
        zonedDateTime.withYear(2024).withMonth(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    long timestamp = specificDateTime.toInstant().toEpochMilli();
    ZonedDateTime specificDateTime2 =
        zonedDateTime.withYear(2024).withMonth(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    long timestamp2 = specificDateTime2.toInstant().toEpochMilli();
    List<LicenseUsageActivityData> licenseUsageActivityDataList = new ArrayList<>();
    LicenseUsageActivityData data = LicenseUsageActivityData.builder()
                                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                                        .organizationIdentifier(ORG_IDENTIFIER)
                                        .projectIdentifier("Project1")
                                        .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                        .moduleType("CI")
                                        .usedCredits(4)
                                        .createdAt(System.currentTimeMillis())
                                        .utcTimestamp(timestamp)
                                        .ciOsType("WINDOWS")
                                        .ciResourceClass("SMALL")
                                        .build();
    licenseUsageActivityDataList.add(data);
    LicenseUsageActivityData data2 = LicenseUsageActivityData.builder()
                                         .accountIdentifier(ACCOUNT_IDENTIFIER)
                                         .organizationIdentifier(ORG_IDENTIFIER)
                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                         .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                         .moduleType("CI")
                                         .usedCredits(4)
                                         .createdAt(System.currentTimeMillis())
                                         .utcTimestamp(timestamp2)
                                         .ciOsType("WINDOWS")
                                         .ciResourceClass("SMALL")
                                         .build();
    licenseUsageActivityDataList.add(data2);
    return licenseUsageActivityDataList;
  }

  private List<LicenseUsage> prepareNewMultiplierLicenseUsageEvents() {
    ZonedDateTime zonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"));
    long buildTimestamp = zonedDateTime.withYear(2024)
                              .withMonth(5)
                              .withDayOfMonth(31)
                              .withHour(23)
                              .withMinute(58)
                              .withSecond(0)
                              .withNano(0)
                              .toInstant()
                              .toEpochMilli();
    List<LicenseUsage> events = new ArrayList<>();
    events.add(CILicenseUsage.builder()
                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                   .orgIdentifier(ORG_IDENTIFIER)
                   .projectIdentifier(PROJECT_IDENTIFIER)
                   .pipelineIdentifier(LINUX_PIPELINE_IDENTIFIER)
                   .stageIdentifier(STAGE_IDENTIFIER)
                   .moduleType(ModuleType.CI)
                   .buildMinutes(10)
                   .createdAt(System.currentTimeMillis())
                   .lastBuildTimestamp(buildTimestamp)
                   .osType(OSType.LINUX)
                   .developer(new Developer("Test Name", "test@harness.io"))
                   .resourceClass(ResourceClass.MEDIUM)
                   .architectureType(ArchitectureType.AMD64)
                   .buildInfraType(BuildInfraType.CLOUD)
                   .isProcessed(false)
                   .build());
    events.add(CILicenseUsage.builder()
                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                   .orgIdentifier(ORG_IDENTIFIER)
                   .projectIdentifier(PROJECT_IDENTIFIER)
                   .pipelineIdentifier(MACOS_PIPELINE_IDENTIFIER)
                   .stageIdentifier(STAGE_IDENTIFIER)
                   .moduleType(ModuleType.CI)
                   .buildMinutes(5)
                   .createdAt(System.currentTimeMillis())
                   .lastBuildTimestamp(buildTimestamp)
                   .osType(OSType.MACOS)
                   .developer(new Developer("Test Name", "test@harness.io"))
                   .resourceClass(ResourceClass.FLEX)
                   .architectureType(ArchitectureType.ARM64)
                   .buildInfraType(BuildInfraType.CLOUD)
                   .isProcessed(false)
                   .build());
    events.add(CILicenseUsage.builder()
                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                   .orgIdentifier(ORG_IDENTIFIER)
                   .projectIdentifier(PROJECT_IDENTIFIER)
                   .pipelineIdentifier(PIPELINE_IDENTIFIER)
                   .stageIdentifier(STAGE_IDENTIFIER)
                   .moduleType(ModuleType.CI)
                   .buildMinutes(3)
                   .createdAt(System.currentTimeMillis())
                   .lastBuildTimestamp(buildTimestamp)
                   .osType(OSType.WINDOWS)
                   .developer(new Developer("Test Name", "test@harness.io"))
                   .resourceClass(ResourceClass.LARGE)
                   .architectureType(ArchitectureType.AMD64)
                   .buildInfraType(BuildInfraType.CLOUD)
                   .isProcessed(false)
                   .build());
    events.add(CILicenseUsage.builder()
                   .accountIdentifier(ACCOUNT_IDENTIFIER)
                   .orgIdentifier(ORG_IDENTIFIER)
                   .projectIdentifier(PROJECT_IDENTIFIER)
                   .pipelineIdentifier(LINUX_PIPELINE_IDENTIFIER)
                   .stageIdentifier(STAGE_IDENTIFIER)
                   .moduleType(ModuleType.CI)
                   .buildMinutes(2)
                   .createdAt(System.currentTimeMillis())
                   .lastBuildTimestamp(buildTimestamp)
                   .osType(OSType.LINUX)
                   .developer(new Developer("Test Name", "test@harness.io"))
                   .resourceClass(ResourceClass.XXLARGE)
                   .architectureType(ArchitectureType.ARM64)
                   .buildInfraType(BuildInfraType.CLOUD)
                   .isProcessed(false)
                   .build());
    return events;
  }

  private CreditDTO prepareCreditsDTO(CreditType creditType, CreditStatus status, int quantity, int used) {
    return CICreditDTO.builder()
        .quantity(quantity)
        .accountIdentifier(ACCOUNT_IDENTIFIER)
        .id(ACCOUNT_IDENTIFIER)
        .purchaseTime(PURCHASE_TIME)
        .expiryTime(EXPIRY_TIME)
        .moduleType(ModuleType.CI)
        .creditStatus(status)
        .creditType(creditType)
        .usedCredits(used)
        .build();
  }
}