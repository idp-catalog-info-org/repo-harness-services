/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.licenseusage.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class LicenseUsageUtils {
  public static final String ORGANIZATION_IDENTIFIERS_LIST =
      "This is the list of organization Identifiers on which the filter will be applied.";
  public static final String PROJECT_IDENTIFIERS_LIST =
      "This is the list of project Identifiers on which the filter will be applied.";
  public static final String PIPELINE_IDENTIFIERS_LIST =
      "This is the list of pipeline Identifiers on which the filter will be applied.";
  public static final String RESOURCE_CLASSES_LIST =
      "This is the list of resource classes on which the filter will be applied.";
  private static final String LICENSE_USAGE_HOURLY_TABLE = "license_usage_hourly";
  private static final String LICENSE_USAGE_DAILY_TABLE = "license_usage_daily";
  private static final String LICENSE_USAGE_MONTHLY_TABLE = "license_usage_monthly";
  private static final String LICENSE_USAGE_YEARLY_TABLE = "license_usage_yearly";
  public static final String MODULE_TYPE = "module_type";
  public static final int MAX_RETRIES = 3;

  public static String getTableName(long startTime, long endTime) {
    // Convert epoch times to LocalDate objects
    LocalDate startDate = Instant.ofEpochMilli(startTime).atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate endDate = Instant.ofEpochMilli(endTime).atZone(ZoneOffset.UTC).toLocalDate();

    // Calculate the number of days between the two dates
    long days = ChronoUnit.DAYS.between(startDate, endDate);
    String tableName = "";
    if (days >= 0 && days <= 1) { // HOURLY table to be looked
      tableName = LICENSE_USAGE_HOURLY_TABLE;
    } else if (days > 1 && days <= 31) { // DAILY table to be looked
      tableName = LICENSE_USAGE_DAILY_TABLE;
    } else if (days >= 32 && days <= 365) { // MONTHLY table to be looked
      tableName = LICENSE_USAGE_MONTHLY_TABLE;
    } else if (days > 366) { // YEARLY table to be looked
      tableName = LICENSE_USAGE_YEARLY_TABLE;
    }
    return tableName;
  }

  @SuppressWarnings("PMD")
  public static String fetchQueryWithFilters(String tableName, String moduleType,
      List<String> organizationIdentifiersFilter, List<String> projectIdentifiersFilter,
      List<String> pipelineIdentifiersFilter, List<String> resourceClasses, long startTime, long endTime,
      boolean sortedByTimestamp) {
    StringBuilder queryBuilder = new StringBuilder();
    queryBuilder.append(
        "SELECT utc_timestamp, organization_identifier, project_identifier, pipeline_identifier, stage_identifier,"
        + " ci_os_type, ci_resource_class, account_identifier AS account_identifier, "
        + "module_type, ");
    queryBuilder.append("SUM(used_credits) AS used_credits ")
        .append("FROM ")
        .append(tableName)
        .append(" ")
        .append("WHERE account_identifier = ? ");
    if (isNotEmpty(moduleType)) {
      queryBuilder.append("AND module_type = ? ");
    }

    // Add additional filters dynamically
    if (isNotEmpty(organizationIdentifiersFilter)) {
      queryBuilder.append("AND organization_identifier IN (");
      appendListPlaceholders(queryBuilder, organizationIdentifiersFilter.size());
      queryBuilder.append(") ");
    }
    if (isNotEmpty(projectIdentifiersFilter)) {
      queryBuilder.append("AND project_identifier IN (");
      appendListPlaceholders(queryBuilder, projectIdentifiersFilter.size());
      queryBuilder.append(") ");
    }
    if (isNotEmpty(pipelineIdentifiersFilter)) {
      queryBuilder.append("AND pipeline_identifier IN (");
      appendListPlaceholders(queryBuilder, pipelineIdentifiersFilter.size());
      queryBuilder.append(") ");
    }
    if (isNotEmpty(resourceClasses)) {
      queryBuilder.append("AND ci_resource_class IN (");
      appendListPlaceholders(queryBuilder, resourceClasses.size());
      queryBuilder.append(") ");
    }
    queryBuilder.append("AND utc_timestamp BETWEEN ").append(startTime).append(" AND ").append(endTime).append(" ");

    queryBuilder.append(
        "GROUP BY account_identifier, organization_identifier, project_identifier, pipeline_identifier, "
        + " utc_timestamp, ci_os_type, stage_identifier, ci_resource_class, module_type");

    if (sortedByTimestamp) {
      queryBuilder.append(" ORDER BY utc_timestamp");
    }

    return queryBuilder.toString();
  }
  @SuppressWarnings("PMD")
  private static void appendListPlaceholders(StringBuilder builder, int count) {
    for (int i = 0; i < count; i++) {
      builder.append("?");
      if (i < count - 1) {
        builder.append(", ");
      }
    }
    builder.append(" ");
  }
}