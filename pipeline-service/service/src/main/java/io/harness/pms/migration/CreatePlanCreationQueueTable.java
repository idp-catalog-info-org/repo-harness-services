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
 * Postgres migration for the FIFO queue store that replaces the hsqs-based queue-based plan
 * creation flow and backs the per-project concurrency skip-ahead drain.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class CreatePlanCreationQueueTable extends NGAbstractPostgresMigration {
  @Override
  public String getFileName() {
    return "timescale/create_plan_creation_queue.sql";
  }

  @Override
  public boolean runInTransaction() {
    return true;
  }
}
