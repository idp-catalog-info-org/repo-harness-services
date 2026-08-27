/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class AddIndexOnOrgIdProjectIdToPipelineExecutionSummaryTable extends NGAbstractTimeScaleMigration {
  private static final String ADD_INDEX_ON_ORGID_PROJECTID_TO_PIPELINE_EXECUTION_SUMMARY_FILE_NAME =
      "timescale/add_index_on_orgid_projectid_to_pipeline_execution_summary.sql";

  @Override
  public String getFileName() {
    return ADD_INDEX_ON_ORGID_PROJECTID_TO_PIPELINE_EXECUTION_SUMMARY_FILE_NAME;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
