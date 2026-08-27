/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.service;

import io.harness.ng.core.licenseusage.entities.LicenseUsageActivityData;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class LicenseUsageSQLHelper {
  private TimeScaleDBService timeScaleDBService;

  public static final String ACCOUNT_IDENTIFIER = "account_identifier";
  public static final String ORGANIZATION_IDENTIFIER = "organization_identifier";
  public static final String PROJECT_IDENTIFIER = "project_identifier";
  public static final String PIPELINE_IDENTIFIER = "pipeline_identifier";
  public static final String STAGE_IDENTIFIER = "stage_identifier";
  public static final String CI_OS_TYPE = "ci_os_type";
  public static final String CI_RESOURCE_CLASS = "ci_resource_class";
  public static final String UTC_TIMESTAMP = "utc_timestamp";
  public static final String USED_CREDITS = "used_credits";
  public static final String MODULE_TYPE = "module_type";
  public static final String CREATED_AT = "created_at";
  public static final String LICENSE_USAGE_HOURLY = "license_usage_hourly";
  public static final String LICENSE_USAGE_DAILY = "license_usage_daily";
  public static final String LICENSE_USAGE_MONTHLY = "license_usage_monthly";
  public static final String LICENSE_USAGE_YEARLY = "license_usage_yearly";

  public static final LinkedHashMap<String, String> attributeMap = new LinkedHashMap<>() {
    {
      put(UTC_TIMESTAMP, "");
      put(ACCOUNT_IDENTIFIER, "");
      put(ORGANIZATION_IDENTIFIER, "");
      put(PROJECT_IDENTIFIER, "");
      put(PIPELINE_IDENTIFIER, "");
      put(STAGE_IDENTIFIER, "");
      put(CI_OS_TYPE, "");
      put(CI_RESOURCE_CLASS, "");
      put(CREATED_AT, "");
      put(USED_CREDITS, "");
      put(MODULE_TYPE, "");
    }
  };

  public static final ZoneId timeStampZone = ZoneId.of("UTC");

  public void saveLicenseUsageActivityData(String tableName, LicenseUsageActivityData licenseUsageActivity) {
    StringJoiner columns = new StringJoiner(", ");
    StringJoiner placeholders = new StringJoiner(", ");
    StringJoiner conflictColumns = new StringJoiner(", ");

    // Generate column names and placeholders for the query
    for (Map.Entry<String, String> entry : attributeMap.entrySet()) {
      columns.add(entry.getKey());
      placeholders.add("?");
      if (!USED_CREDITS.equals(entry.getKey()) && !CREATED_AT.equals(entry.getKey())) {
        conflictColumns.add(entry.getKey());
      }
    }

    // Build the full SQL insert query
    String licenseTableUsedCredits = tableName + "." + USED_CREDITS;
    String usedCreditsValueToUpdate = " + EXCLUDED." + USED_CREDITS;
    String query = "";
    if (tableName.equals(LICENSE_USAGE_DAILY) || tableName.equals(LICENSE_USAGE_MONTHLY)
        || tableName.equals(LICENSE_USAGE_YEARLY)) {
      query = "INSERT INTO " + tableName + " (" + columns + ") "
          + "VALUES (" + placeholders + ") "
          + "ON CONFLICT (" + conflictColumns + ") DO UPDATE "
          + "SET " + USED_CREDITS + "=" + usedCreditsValueToUpdate + ";";
    } else {
      query = "INSERT INTO " + tableName + " (" + columns + ") "
          + "VALUES (" + placeholders + ") "
          + "ON CONFLICT (" + conflictColumns + ") DO UPDATE "
          + "SET " + USED_CREDITS + "=" + licenseTableUsedCredits + usedCreditsValueToUpdate + ";";
    }

    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(query)) {
      statement.setLong(1, licenseUsageActivity.getUtcTimestamp());
      statement.setString(2, licenseUsageActivity.getAccountIdentifier());
      statement.setString(3, licenseUsageActivity.getOrganizationIdentifier());
      statement.setString(4, licenseUsageActivity.getProjectIdentifier());
      statement.setString(5, licenseUsageActivity.getPipelineIdentifier());
      statement.setString(6, licenseUsageActivity.getStageIdentifier());
      statement.setString(7, licenseUsageActivity.getCiOsType());
      statement.setString(8, licenseUsageActivity.getCiResourceClass());
      statement.setLong(9, Instant.now().toEpochMilli());
      statement.setInt(10, licenseUsageActivity.getUsedCredits());
      statement.setString(11, licenseUsageActivity.getModuleType());

      int affectedRows = statement.executeUpdate();
      if (affectedRows == 0) {
        throw new SQLException("Inserting entity failed, no rows affected.");
      } else {
        log.debug("Inserted the record in the table: " + tableName);
      }
    } catch (SQLException e) {
      // Handle exceptions as appropriate for your application
      String errMessage =
          "Error while inserting license usage activity for accountId=" + licenseUsageActivity.getAccountIdentifier();
      log.error(errMessage, e);
      throw new RuntimeException(errMessage);
    }
  }

  public List<LicenseUsageActivityData> fetchRecordsFromPostgres(
      String tableName, String accountIdentifier, long fromTimestamp, long toTimestamp) {
    List<LicenseUsageActivityData> records = new ArrayList<>();
    String fetchQuery = "SELECT * FROM " + tableName + " WHERE " + ACCOUNT_IDENTIFIER + " = ? AND " + UTC_TIMESTAMP
        + " >= ? AND " + UTC_TIMESTAMP + " <= ?";

    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(fetchQuery)) {
      statement.setString(1, accountIdentifier);
      statement.setLong(2, fromTimestamp);
      statement.setLong(3, toTimestamp);

      ResultSet resultSet = statement.executeQuery();

      while (resultSet.next()) {
        LicenseUsageActivityData licenseUsageActivityData =
            LicenseUsageActivityData.builder()
                .accountIdentifier(resultSet.getString(ACCOUNT_IDENTIFIER))
                .projectIdentifier(resultSet.getString(PROJECT_IDENTIFIER))
                .pipelineIdentifier(resultSet.getString(PIPELINE_IDENTIFIER))
                .stageIdentifier(resultSet.getString(STAGE_IDENTIFIER))
                .organizationIdentifier(resultSet.getString(ORGANIZATION_IDENTIFIER))
                .ciOsType(resultSet.getString(CI_OS_TYPE))
                .ciResourceClass(resultSet.getString(CI_RESOURCE_CLASS))
                .utcTimestamp(resultSet.getLong(UTC_TIMESTAMP))
                .usedCredits(resultSet.getInt(USED_CREDITS))
                .moduleType(resultSet.getString(MODULE_TYPE))
                .build();

        records.add(licenseUsageActivityData);
      }
    } catch (SQLException e) {
      log.error("Error while fetching license usage activity from table: " + tableName, e);
    }

    return records;
  }

  public List<String> fetchAllAccountIds(String tableName, long fromTimestamp, long toTimestamp) {
    List<String> accountIds = new ArrayList<>();
    String query = "SELECT DISTINCT " + ACCOUNT_IDENTIFIER + " FROM " + tableName + " WHERE " + UTC_TIMESTAMP
        + " >= ? AND " + UTC_TIMESTAMP + " <= ?";
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement stmt = connection.prepareStatement(query)) {
      // Set the parameters for the prepared statement
      stmt.setLong(1, fromTimestamp);
      stmt.setLong(2, toTimestamp);
      ResultSet rs = stmt.executeQuery();
      while (rs.next()) {
        accountIds.add(rs.getString(ACCOUNT_IDENTIFIER));
      }
    } catch (SQLException e) {
      log.error("SQL Exception when fetching account IDs", e);
    }
    return accountIds;
  }

  public static long getStartOfDayUtc(long timestamp) { // 1715677200000
    LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), timeStampZone);
    LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
    return startOfDay.atZone(timeStampZone).toInstant().toEpochMilli();
  }

  public static long getStartOfMonthUtc(long timestamp) {
    LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), timeStampZone);
    LocalDateTime startOfMonth = date.withDayOfMonth(1).toLocalDate().atStartOfDay();
    return startOfMonth.atZone(timeStampZone).toInstant().toEpochMilli();
  }

  public static long getStartOfYearUtc(long timestamp) {
    LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), timeStampZone);
    LocalDateTime startOfYear = date.withDayOfYear(1).toLocalDate().atStartOfDay();
    return startOfYear.atZone(timeStampZone).toInstant().toEpochMilli();
  }
}
