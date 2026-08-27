/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.migration.postgres.NGAbstractPostgresMigration;

/**
 * Widen plan_creation_queue.{org_id,project_id,parent_unique_id} from VARCHAR(64) to VARCHAR(128).
 * org_id / project_id hold user-supplied NG identifiers (up to 128 chars), so 64 overflowed on
 * insert for long identifiers.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class WidenPlanCreationQueueIdentifierColumns extends NGAbstractPostgresMigration {
  @Override
  public String getFileName() {
    return "timescale/widen_plan_creation_queue_identifier_columns.sql";
  }

  @Override
  public boolean runInTransaction() {
    return true;
  }
}
