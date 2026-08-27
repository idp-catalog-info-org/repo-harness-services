/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.common;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class DateUtils {
  public long parseTimestamp(String timestamp, String format) {
    try {
      SimpleDateFormat dateFormat = new SimpleDateFormat(format);
      Date date = dateFormat.parse(timestamp);
      return date.getTime();
    } catch (ParseException e) {
      throw new InvalidRequestException(format("Cannot parse date time value %s", timestamp));
    }
  }

  public static Pair<Long, Long> getPreviousDay24HourTimeFrame() {
    LocalTime midnight = LocalTime.MIDNIGHT;
    LocalDate today = LocalDate.now();
    LocalDateTime todayMidnight = LocalDateTime.of(today, midnight);
    LocalDateTime previousDayMidnight = todayMidnight.minusDays(1);
    return Pair.of(previousDayMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        todayMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
  }

  public static Pair<String, Date> yesterdayDateInStringAndDateFormat() {
    final Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DATE, -1);
    Date yesterdayDate = calendar.getTime();
    return Pair.of(DateUtils.getDateStringByFormat(yesterdayDate, "yyyy-MM-dd"),
        DateUtils.getDateByFormat(yesterdayDate, "yyyy-MM-dd"));
  }

  public static long yesterdayInMilliseconds() {
    return timestampInMsWithAmountManipulation(-1);
  }

  public static long midnightInMilliseconds() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DATE, 1);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTimeInMillis();
  }

  public static long timestampInMsWithAmountManipulation(int amount) {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DATE, amount);
    calendar.set(Calendar.HOUR_OF_DAY, 12);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTimeInMillis();
  }

  public static String getDateStringByFormat(Date date, String format) {
    return new SimpleDateFormat(format).format(date);
  }

  public static Date getDateByFormat(Date date, String format) {
    try {
      DateFormat dateFormat = new SimpleDateFormat(format);
      return dateFormat.parse(dateFormat.format(date));
    } catch (ParseException e) {
      throw new UnexpectedException(e.getMessage());
    }
  }

  public static Date localDateToDate(LocalDate localDate) {
    return Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
  }

  public static LocalDate dateToLocateDate(Date date) {
    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  }

  public static String milliSecondsToDateWithFormat(long milliSeconds, String dateFormat) {
    DateFormat dateFormatter = new SimpleDateFormat(dateFormat);
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(milliSeconds);
    return dateFormatter.format(calendar.getTime());
  }

  public static long startOfTheDayInMilliseconds(long timestamp) {
    ZoneOffset zoneOffset = ZoneId.systemDefault().getRules().getOffset(Instant.now());
    LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, zoneOffset);
    LocalDate localDate = dateTime.toLocalDate();
    LocalDateTime startOfDay = LocalDateTime.of(localDate, LocalTime.MIDNIGHT);
    return startOfDay.toEpochSecond(zoneOffset) * 1000;
  }

  public static long timestampOneYearFromNow() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.YEAR, 1);
    return cal.getTime().getTime();
  }
}
