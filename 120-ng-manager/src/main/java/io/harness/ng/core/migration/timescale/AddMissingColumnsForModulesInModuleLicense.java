/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

@OwnedBy(HarnessTeam.GTM)
public class AddMissingColumnsForModulesInModuleLicense extends NGAbstractTimeScaleMigration {
  private static final String ADD_DEV_LICENSE_COLUMN_FOR_ALL_MODULES =
      "timescale/add_module_specific_columns_to_all_module_licenses_timescale.sql";
  @Override
  public String getFileName() {
    return ADD_DEV_LICENSE_COLUMN_FOR_ALL_MODULES;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
