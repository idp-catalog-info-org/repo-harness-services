/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class CreateIndexOnAccIdOrgIdProjIdIdDeletedDeletedAtForPipelineTable extends NGAbstractTimeScaleMigration {
  private static final String ADD_INDEX_TO_PIPELINE_FILE_NAME =
      "timescale/add_index_on_accid_orgid_projid_id_deleted_deletedat_to_pipeline.sql";

  @Override
  public String getFileName() {
    return ADD_INDEX_TO_PIPELINE_FILE_NAME;
  }

  @Override
  public boolean runInTransaction() {
    return false;
  }
}
