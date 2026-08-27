/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.licenseusage.services;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.licenseusage.utils.LicenseUsageUtils.MAX_RETRIES;

import io.harness.ng.core.licenseusage.utils.LicenseUsageUtils;
import io.harness.spec.server.ng.v1.model.CreditUsage;
import io.harness.spec.server.ng.v1.model.LicenseUsageActivity;
import io.harness.timescaledb.TimeScaleDBService;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
public class LicenseUsageActivityServiceImpl implements LicenseUsageActivityService {
  private TimeScaleDBService timeScaleDBService;
  private static final String ALL_CI_OS_TYPES = "ALL_CI_OS_TYPES";

  /**
   * Given an accountIdentifier, provides the LicenseUsageActivity for the given time range
   * and additional filters if any
   * @param accountIdentifier   AccountIdentifier of the harness user
   * @param moduleType          Type of the module
   * @param startTime           Start Interval for fetching the data
   * @param endTime             End Interval for fetching the data
   * @param organizationIdentifiersFilter      List of organizationIds to filter if provided
   * @param projectIdentifiersFilter  List of projectIds to filter if provided
   * @param pipelineIdentifiersFilter List of pipelineIds to filter if provided
   * @param resourceClasses     List of resourceClasses to filter if provided
   * @return                    a Page of LicenseUsageActivityResponse
   */
  @Override
  public List<LicenseUsageActivity> getLicenseUsageActivity(String accountIdentifier, String moduleType, long startTime,
      long endTime, List<String> organizationIdentifiersFilter, List<String> projectIdentifiersFilter,
      List<String> pipelineIdentifiersFilter, List<String> resourceClasses, boolean rollup) {
    int retry = 0;
    boolean successfulOperation = false;
    String tableName = LicenseUsageUtils.getTableName(startTime, endTime);
    String query = LicenseUsageUtils.fetchQueryWithFilters(tableName, moduleType, organizationIdentifiersFilter,
        projectIdentifiersFilter, pipelineIdentifiersFilter, resourceClasses, startTime, endTime, false);

    // Map to hold results aggregated by both timestamp and ciOsType
    Map<Long, Map<String, Integer>> aggregatedCreditsMap = new HashMap<>();

    while (!successfulOperation && retry <= MAX_RETRIES) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement stmt = connection.prepareStatement(query)) {
        int parameterIndex = 1;
        stmt.setString(parameterIndex++, accountIdentifier);
        if (isNotEmpty(moduleType)) {
          stmt.setString(parameterIndex++, moduleType);
        }
        // Add additional parameters like startTime, endTime, and filters here
        parameterIndex = setListParameters(stmt, parameterIndex, organizationIdentifiersFilter);
        parameterIndex = setListParameters(stmt, parameterIndex, projectIdentifiersFilter);
        parameterIndex = setListParameters(stmt, parameterIndex, pipelineIdentifiersFilter);
        setListParameters(stmt, parameterIndex, resourceClasses);
        ResultSet resultSet = stmt.executeQuery();
        while (resultSet.next()) {
          long timestamp = resultSet.getLong("utc_timestamp");
          String ciOsType = resultSet.getString("ci_os_type");
          int totalCredits = resultSet.getInt("used_credits");

          // Aggregating total credits by timestamp and ciOsType
          aggregatedCreditsMap.computeIfAbsent(timestamp, k -> new HashMap<>())
              .merge(ciOsType, totalCredits, Integer::sum);
        }
        successfulOperation = true;
      } catch (SQLException exception) {
        retry++;
        if (retry >= MAX_RETRIES) {
          throw new RuntimeException("Could not fetch the license usage activity data: " + exception.getMessage());
        }
      }
    }

    // Populate the LicenseUsageActivity
    List<LicenseUsageActivity> licenseUsageActivities = new ArrayList<>();
    int rolledUpCredits = 0;
    long lastTimestampValue = 0;

    for (Map.Entry<Long, Map<String, Integer>> entry : aggregatedCreditsMap.entrySet()) {
      long timestamp = entry.getKey();
      Map<String, Integer> creditsByOsType = entry.getValue();

      List<CreditUsage> creditUsages = new ArrayList<>();
      for (Map.Entry<String, Integer> osTypeEntry : creditsByOsType.entrySet()) {
        CreditUsage creditUsage = new CreditUsage();
        creditUsage.setCiOsType(osTypeEntry.getKey());
        creditUsage.setTotalCredits(osTypeEntry.getValue());
        creditUsages.add(creditUsage);

        rolledUpCredits += osTypeEntry.getValue();
      }
      LicenseUsageActivity licenseUsageActivity = new LicenseUsageActivity();
      licenseUsageActivity.setTimestamp(timestamp);
      licenseUsageActivity.setCredits(creditUsages);
      licenseUsageActivities.add(licenseUsageActivity);
      // Saving the last timestamp for rollup
      lastTimestampValue = timestamp;
    }

    if (rollup) {
      LicenseUsageActivity licenseUsageActivity = new LicenseUsageActivity();
      CreditUsage creditUsage = new CreditUsage();
      creditUsage.setCiOsType(ALL_CI_OS_TYPES);
      creditUsage.setTotalCredits(rolledUpCredits);
      licenseUsageActivity.setTimestamp(lastTimestampValue);
      licenseUsageActivity.setCredits(Arrays.asList(creditUsage));
      return new ArrayList<>(Arrays.asList(licenseUsageActivity));
    }

    return licenseUsageActivities;
  }

  public File exportLicenseUsageActivityData(String accountIdentifier, String moduleType, long startTime, long endTime,
      List<String> organizationIdentifiersFilter, List<String> projectIdentifiersFilter,
      List<String> pipelineIdentifiersFilter, List<String> resourceClasses) {
    String tableName = LicenseUsageUtils.getTableName(startTime, endTime);
    String query = LicenseUsageUtils.fetchQueryWithFilters(tableName, moduleType, organizationIdentifiersFilter,
        projectIdentifiersFilter, pipelineIdentifiersFilter, resourceClasses, startTime, endTime, true);

    int retry = 0;
    final int MAX_RETRIES = 3;
    boolean successfulOperation = false;

    File csvFile = null;
    try {
      csvFile = File.createTempFile("license_usage", ".csv");
    } catch (IOException e) {
      log.error("Error creating CSV file", e);
      return null;
    }
    while (!successfulOperation && retry < MAX_RETRIES) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement stmt = connection.prepareStatement(query);
           CSVWriter writer = new CSVWriter(new FileWriter(csvFile))) {
        int parameterIndex = 1;
        stmt.setString(parameterIndex++, accountIdentifier);
        if (isNotEmpty(moduleType)) {
          stmt.setString(parameterIndex++, moduleType);
        }
        parameterIndex = setListParameters(stmt, parameterIndex, organizationIdentifiersFilter);
        parameterIndex = setListParameters(stmt, parameterIndex, projectIdentifiersFilter);
        parameterIndex = setListParameters(stmt, parameterIndex, pipelineIdentifiersFilter);
        setListParameters(stmt, parameterIndex, resourceClasses);

        // Execute query
        ResultSet resultSet = stmt.executeQuery();

        // Write the header
        String[] header = {"accountId", "organizationId", "projectId", "pipelineId", "stageId", "timestamp", "osType",
            "resourceClass", "totalCredits", "moduleType"};
        writer.writeNext(header);

        // Write data rows
        while (resultSet.next()) {
          String accountId = resultSet.getString("account_identifier");
          String orgId = resultSet.getString("organization_identifier");
          String projectId = resultSet.getString("project_identifier");
          String pipelineId = resultSet.getString("pipeline_identifier");
          long utcTimestamp = resultSet.getLong("utc_timestamp");
          String stageId = resultSet.getString("stage_identifier");
          String osType = resultSet.getString("ci_os_type");
          String resourceClass = resultSet.getString("ci_resource_class");
          int usedCredits = resultSet.getInt("used_credits");
          String moduleType2 = resultSet.getString("module_type");
          String[] dataRow = {accountId, orgId, projectId, pipelineId, stageId, Long.toString(utcTimestamp), osType,
              resourceClass, Integer.toString(usedCredits), moduleType2};
          writer.writeNext(dataRow);
        }

        successfulOperation = true;

      } catch (SQLException | IOException e) {
        retry++;
        if (retry >= MAX_RETRIES) {
          throw new RuntimeException(
              "Failed to export license usage activity data after " + MAX_RETRIES + " attempts", e);
        }
      }
    }

    return csvFile;
  }

  private static int setListParameters(PreparedStatement stmt, int parameterIndex, List<String> values)
      throws SQLException {
    for (String value : values) {
      stmt.setString(parameterIndex++, value);
    }
    return parameterIndex;
  }
}