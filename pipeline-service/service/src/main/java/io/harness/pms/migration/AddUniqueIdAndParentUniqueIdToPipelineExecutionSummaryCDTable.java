/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

@OwnedBy(HarnessTeam.PL)
public class AddUniqueIdAndParentUniqueIdToPipelineExecutionSummaryCDTable extends NGAbstractTimeScaleMigration {
  public static final String SQL_FILE =
      "timescale/add_parentuniqueid_uniqueid_columns_to_pipeline_execution_summary_cd_table.sql";

  @Override
  public String getFileName() {
    return SQL_FILE;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
