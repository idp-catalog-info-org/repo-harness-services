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

/** CDS-125860 migration v125: partial unique indexes on gitops_app_info include serviceid for linkage history. */
@OwnedBy(HarnessTeam.GITOPS)
public class ChangeGitopsAppInfoUniqueIndexIncludeServiceId extends NGAbstractTimeScaleMigration {
  @Override
  public String getFileName() {
    return "timescale/change_gitops_app_info_unique_index_include_serviceid.sql";
  }

  @Override
  public boolean executeFullScript() {
    return true;
  }
}
