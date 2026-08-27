/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class AddUsageDetailColumnsToServiceInstancesLicenseDailyReport extends NGAbstractTimeScaleMigration {
  private static final String ADD_USAGE_DETAIL_COLUMNS_TO_SERVICE_INSTANCES_LICENSE_DAILY_REPORT_FILE =
      "timescale/add_usage_detail_columns_to_service_instances_license_daily_report.sql";
  @Override
  public String getFileName() {
    return ADD_USAGE_DETAIL_COLUMNS_TO_SERVICE_INSTANCES_LICENSE_DAILY_REPORT_FILE;
  }
}
