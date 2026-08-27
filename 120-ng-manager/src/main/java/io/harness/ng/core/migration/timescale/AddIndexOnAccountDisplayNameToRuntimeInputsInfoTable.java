/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

public class AddIndexOnAccountDisplayNameToRuntimeInputsInfoTable extends NGAbstractTimeScaleMigration {
  private static final String FILE_NAME = "timescale/add_index_on_account_display_name_to_runtime_inputs_info.sql";

  @Override
  public String getFileName() {
    return FILE_NAME;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
