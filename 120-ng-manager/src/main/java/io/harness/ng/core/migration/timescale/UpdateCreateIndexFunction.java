/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.migration.timescale;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.timescale.NGAbstractTimeScaleMigration;

/**
 * CDS-127425: extend {@code create_index} for UNIQUE indexes and partial indexes via an optional WHERE
 * clause (PostgreSQL 14 compatible). Replaces the 4-arg function installed by v2 bootstrap; numbered
 * migrations do not replay, so this must be a forward-only migration. {@code where_clause} is for
 * hardcoded migration SQL literals only, not user input.
 */
@OwnedBy(HarnessTeam.CDC)
public class UpdateCreateIndexFunction extends NGAbstractTimeScaleMigration {
  @Override
  public String getFileName() {
    return "timescale/update_create_index_function.sql";
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
