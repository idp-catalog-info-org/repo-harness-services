/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

@OwnedBy(PL)
public class AddReplicaIdentityToLicenseUsageHourly extends NGAbstractTimeScaleMigration {
  @Override
  public String getFileName() {
    return "timescale/add_replica_identity_to_license_usage_hourly.sql";
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
