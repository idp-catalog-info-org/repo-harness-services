/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class AddExecutionStrategyTypeToCDStageExecutionTable extends NGAbstractTimeScaleMigration {
  public static final String ADD_EXECUTION_STRATEGY_TYPE_CD_STAGE_EXECUTION_SQL_FILE =
      "timescale/add_execution_strategy_type_to_cd_stage_execution.sql";

  @Override
  public String getFileName() {
    return ADD_EXECUTION_STRATEGY_TYPE_CD_STAGE_EXECUTION_SQL_FILE;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
