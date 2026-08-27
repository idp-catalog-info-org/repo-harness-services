/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class AddIndexesOnAccIdOrgIdProjIdPipeIdToPipelineExecutionSummaryCDTable extends NGAbstractTimeScaleMigration {
  private static final String ADD_INDEXES_ON_ACCID_ORGID_PROJID_PIPEID_TO_PIPELINE_EXECUTION_SUMMARY_CD_FILE_NAME =
      "timescale/add_index_on_accid_orgid_projid_pipeid_to_pipeline_execution_summary_cd.sql";

  @Override
  public String getFileName() {
    return ADD_INDEXES_ON_ACCID_ORGID_PROJID_PIPEID_TO_PIPELINE_EXECUTION_SUMMARY_CD_FILE_NAME;
  }
}
