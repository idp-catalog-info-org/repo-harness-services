/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

@OwnedBy(HarnessTeam.DBDEVOPS)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.DB_DEVOPS})
public class AddCreatedUpdatedColumnsToDBOpsStepExecutionTable extends NGAbstractTimeScaleMigration {
  public static final String SQL_FILE = "timescale/add_columns_createdat_updatedat_to_dbops_step_execution.sql";

  @Override
  public String getFileName() {
    return SQL_FILE;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
