/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ANKIT_TIWARI;
import static io.harness.rule.OwnerRule.OM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class TimeRangeTest {
  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void updateRelativeTime() {
    TimeRange timeRange = new TimeRange(null, null, "-7d", null);
    timeRange.setRelativeTime("-7d");
    timeRange.updateTimeRange();

    long now = Instant.now().toEpochMilli();
    long sevenDaysAgo = Instant.now().minusMillis(7 * 24 * 60 * 60 * 1000).toEpochMilli();

    assertThat(timeRange.getStartTime()).isNotNull();
    assertThat(timeRange.getEndTime()).isNotNull();
    assertThat(timeRange.getStartTime()).isGreaterThan(sevenDaysAgo - 1000);
    assertThat(timeRange.getEndTime()).isLessThan(now + 1000);
  }

  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void updateTimeRangeThisQuarter() {
    TimeRange timeRange = new TimeRange(null, null, null, TimeRangeFilterType.THIS_QUARTER);

    // Test THIS_QUARTER time range
    timeRange.updateTimeRange();

    LocalDate now = LocalDate.now();
    int startMonthThisQuarter = ((now.getMonthValue() - 1) / 3) * 3 + 1;
    LocalDate startOfThisQuarter = LocalDate.of(now.getYear(), startMonthThisQuarter, 1);

    long expectedStart = startOfThisQuarter.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    long expectedEnd = Instant.now().toEpochMilli();

    assertThat(expectedStart).isEqualTo(timeRange.getStartTime());
    assertThat(timeRange.getEndTime()).isLessThanOrEqualTo(expectedEnd);
  }

  @Test
  @Owner(developers = ANKIT_TIWARI)
  @Category(UnitTests.class)
  public void updateTimeRangeLastQuarter() {
    // Test LAST_QUARTER time range
    TimeRange timeRange = new TimeRange(null, null, null, TimeRangeFilterType.LAST_QUARTER);

    timeRange.updateTimeRange();

    LocalDate now = LocalDate.now();
    int currentMonth = now.getMonthValue();
    int startMonthLastQuarter = ((currentMonth - 1) / 3) * 3 - 2;
    int yearOfLastQuarter = (startMonthLastQuarter <= 0) ? now.getYear() - 1 : now.getYear();

    if (startMonthLastQuarter <= 0) {
      startMonthLastQuarter += 12; // Adjust for the previous year
    }

    LocalDate startOfLastQuarter = LocalDate.of(yearOfLastQuarter, startMonthLastQuarter, 1);
    LocalDate endOfLastQuarter =
        startOfLastQuarter.plusMonths(2).withDayOfMonth(startOfLastQuarter.plusMonths(2).lengthOfMonth());

    long expectedStart = startOfLastQuarter.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    long expectedEnd = endOfLastQuarter.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

    assertThat(expectedStart).isEqualTo(timeRange.getStartTime());
    assertThat(expectedEnd).isEqualTo(timeRange.getEndTime());
  }

  @Test
  public void updateTimeRangeLastMonth() {
    // Set the time range to LAST_MONTH
    TimeRange timeRange = new TimeRange(null, null, null, TimeRangeFilterType.LAST_MONTH);

    timeRange.updateTimeRange();

    // Calculate expected start and end times for the last month
    LocalDate now = LocalDate.now();
    LocalDate firstDayOfLastMonth = now.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
    LocalDate lastDayOfLastMonth = now.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());

    long expectedStart = firstDayOfLastMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    long expectedEnd =
        lastDayOfLastMonth.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

    // Validate the start and end times
    assertThat(expectedStart).isEqualTo(timeRange.getStartTime());
    assertThat(expectedEnd).isEqualTo(timeRange.getEndTime());
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void resolveTimeRangeFilterReturnsTrueForExplicitStartAndEndTime() {
    TimeRange timeRange = TimeRange.builder().startTime(1000L).endTime(2000L).build();

    assertThat(timeRange.resolveTimeRangeFilter()).isTrue();
    assertThat(timeRange.getStartTime()).isEqualTo(1000L);
    assertThat(timeRange.getEndTime()).isEqualTo(2000L);
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void resolveTimeRangeFilterResolvesTimeRangeFilterType() {
    LocalDate firstDayOfCurrentMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
    long expectedStart = firstDayOfCurrentMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    TimeRange timeRange = TimeRange.builder().timeRangeFilterType(TimeRangeFilterType.THIS_MONTH).build();

    assertThat(timeRange.resolveTimeRangeFilter()).isTrue();
    assertThat(timeRange.getStartTime()).isEqualTo(expectedStart);
    assertThat(timeRange.getEndTime()).isNotNull();
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void resolveTimeRangeFilterReturnsFalseWhenBothTimesAreNull() {
    TimeRange timeRange = TimeRange.builder().build();

    assertThat(timeRange.resolveTimeRangeFilter()).isFalse();
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void resolveTimeRangeFilterThrowsWhenOnlyStartTimeIsProvided() {
    TimeRange timeRange = TimeRange.builder().startTime(1000L).build();

    assertThatThrownBy(timeRange::resolveTimeRangeFilter)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("startTime or endTime is not provided in TimeRange filter");
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void resolveTimeRangeFilterThrowsWhenOnlyEndTimeIsProvided() {
    TimeRange timeRange = TimeRange.builder().endTime(2000L).build();

    assertThatThrownBy(timeRange::resolveTimeRangeFilter)
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("startTime or endTime is not provided in TimeRange filter");
  }
}
