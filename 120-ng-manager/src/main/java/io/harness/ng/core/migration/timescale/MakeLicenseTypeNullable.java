package io.harness.ng.core.migration.timescale;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

@OwnedBy(HarnessTeam.GTM)
public class MakeLicenseTypeNullable extends NGAbstractTimeScaleMigration {
  private static final String MIGRATE_LICENSE_TYPE_TO_NULLABLE = "timescale/alter_license_type_to_nullable.sql";
  @Override
  public String getFileName() {
    return MIGRATE_LICENSE_TYPE_TO_NULLABLE;
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
