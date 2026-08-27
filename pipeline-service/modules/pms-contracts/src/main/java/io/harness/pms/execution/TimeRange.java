/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Data
@Builder
@Slf4j
public class TimeRange {
  private static final String RESOLVE_TIME_RANGE_ERROR_MSG = "unable to update the time range for TimeRange Filter";
  private static final String PARTIAL_TIME_RANGE_ERROR_MSG =
      "startTime or endTime is not provided in TimeRange filter. Either add the "
      + "missing field or remove the timeRange filter.";

  Long startTime;
  Long endTime;
  String relativeTime;
  TimeRangeFilterType timeRangeFilterType;

  private void updateRelativeTime() {
    // Parse the input
    if (!this.relativeTime.startsWith("-") || this.relativeTime.length() < 3) {
      throw new IllegalArgumentException("Relative time must start with '-' and have a valid unit (e.g., '-7d').");
    }

    String unit = this.relativeTime.substring(this.relativeTime.length() - 1); // Last character
    String valueStr = this.relativeTime.substring(1, this.relativeTime.length() - 1); // Exclude '-' and unit

    int value;
    try {
      value = Integer.parseInt(valueStr);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid numeric value in relative time input.");
    }

    Instant now = Instant.now();
    switch (unit.toLowerCase()) {
      case "d": // Days
        this.startTime = now.minus(value, ChronoUnit.DAYS).toEpochMilli();
        break;
      case "m": // Month
        this.startTime = now.minus(value, ChronoUnit.MONTHS).toEpochMilli();
        break;
      case "y": // Years
        this.startTime = now.minus(value, ChronoUnit.YEARS).toEpochMilli();
        break;
      default:
        throw new IllegalArgumentException("Unsupported time unit: " + unit);
    }

    this.endTime = now.toEpochMilli();
  }

  public void updateTimeRange() {
    if (this.startTime != null && this.endTime != null) {
      return;
    }

    if (EmptyPredicate.isEmpty(this.relativeTime) && this.timeRangeFilterType == null) {
      return;
    }

    if (EmptyPredicate.isNotEmpty(this.relativeTime)) {
      this.updateRelativeTime();
      return;
    }

    // Parse the input
    switch (this.timeRangeFilterType) {
      case LAST_7_DAYS
          -> {
        this.setRelativeTime("-7d"); this.updateRelativeTime();
      } case LAST_30_DAYS
          -> {
        this.setRelativeTime("-30d"); this.updateRelativeTime();
      } case THIS_YEAR
          -> {
        LocalDate firstDayOfCurrentYear = LocalDate.now().with(TemporalAdjusters.firstDayOfYear());
        this.startTime = getStartTimeAndDate(firstDayOfCurrentYear);
        this.endTime = Instant.now().toEpochMilli();
      } case LAST_MONTH
          -> {
        LocalDate firstDayOfLastMonth = getFirstDayOfMonthOffset(1);
        LocalDate lastDayOfLastMonth = getLastDayOfMonthOffset(1);
        this.startTime = getStartTimeAndDate(firstDayOfLastMonth);
        this.endTime = getEndTimeAndDate(lastDayOfLastMonth);
      } case LAST_YEAR
          -> {
        LocalDate firstDayOfLastYear = LocalDate.now().minusYears(1).with(TemporalAdjusters.firstDayOfYear());
        LocalDate lastDayOfLastyear = LocalDate.now().minusYears(1).with(TemporalAdjusters.lastDayOfYear());
        this.startTime = getStartTimeAndDate(firstDayOfLastYear);
        this.endTime = getEndTimeAndDate(lastDayOfLastyear);
      } case LAST_3_MONTHS
          -> {
        LocalDate firstDayOfLast3Month = getFirstDayOfMonthOffset(3);
        LocalDate lastDayOfLast3Month = getLastDayOfMonthOffset(1);
        this.startTime = getStartTimeAndDate(firstDayOfLast3Month);
        this.endTime = getEndTimeAndDate(lastDayOfLast3Month);
      } case LAST_6_MONTHS
          -> {
        LocalDate firstDayOfLast6Month = getFirstDayOfMonthOffset(6);
        LocalDate lastDayOfLast6Month = getLastDayOfMonthOffset(1);
        this.startTime = getStartTimeAndDate(firstDayOfLast6Month);
        this.endTime = getEndTimeAndDate(lastDayOfLast6Month);
      } case THIS_MONTH
          -> {
        LocalDate firstDayOfCurrentMonth = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        this.startTime = getStartTimeAndDate(firstDayOfCurrentMonth);
        this.endTime = Instant.now().toEpochMilli();
      } case LAST_12_MONTHS
          -> {
        LocalDate firstDayOfLast12Month = getFirstDayOfMonthOffset(12);
        LocalDate lastDayOfLast12Month = getLastDayOfMonthOffset(1);
        this.startTime = getStartTimeAndDate(firstDayOfLast12Month);
        this.endTime = getEndTimeAndDate(lastDayOfLast12Month);
      } case THIS_QUARTER
          -> {
        int currentMonth = LocalDate.now().getMonthValue();
        int startMonthThisQuarter = ((currentMonth - 1) / 3) * 3 + 1; // 1, 4, 7, 10
        LocalDate startOfThisQuarter = LocalDate.of(LocalDate.now().getYear(), startMonthThisQuarter, 1);
        // End time is the current date and time
        this.startTime = getStartTimeAndDate(startOfThisQuarter);
        this.endTime = Instant.now().toEpochMilli();
      } case LAST_QUARTER
          -> {
        int currentMonth = LocalDate.now().getMonthValue();
        int startMonthLastQuarter = ((currentMonth - 1) / 3) * 3 - 2; // Previous quarter's start month
        int yearOfLastQuarter = (startMonthLastQuarter <= 0) ? LocalDate.now().getYear() - 1:
        LocalDate.now().getYear();

        if (startMonthLastQuarter <= 0) {
          startMonthLastQuarter += 12; // Adjust for previous year's quarter
        }

        // Calculate start and end of last quarter
        LocalDate startOfLastQuarter = LocalDate.of(yearOfLastQuarter, startMonthLastQuarter, 1);
        LocalDate endOfLastQuarter = startOfLastQuarter.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());

        this.startTime = getStartTimeAndDate(startOfLastQuarter);
        this.endTime = getEndTimeAndDate(endOfLastQuarter);
    }
    default -> {}
  }
}

public boolean resolveTimeRangeFilter() {
  try {
    updateTimeRange();
  } catch (Exception e) {
    log.error(RESOLVE_TIME_RANGE_ERROR_MSG, e);
    throw new InvalidRequestException(RESOLVE_TIME_RANGE_ERROR_MSG, e);
  }
  if (startTime != null && endTime != null) {
    return true;
  }
  if ((startTime != null && endTime == null) || (startTime == null && endTime != null)) {
    throw new InvalidRequestException(PARTIAL_TIME_RANGE_ERROR_MSG);
  }
  return false;
}

private static LocalDate getFirstDayOfMonthOffset(int monthsAgo) {
  return LocalDate.now().minusMonths(monthsAgo).with(TemporalAdjusters.firstDayOfMonth());
}

private static LocalDate getLastDayOfMonthOffset(int monthsAgo) {
  return LocalDate.now().minusMonths(monthsAgo).with(TemporalAdjusters.lastDayOfMonth());
}

private static Long getStartTimeAndDate(LocalDate firstDate) {
  return firstDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
}

private static Long getEndTimeAndDate(LocalDate endDate) {
  return endDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
}
}
