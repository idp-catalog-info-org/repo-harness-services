/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.migration.postgres.NGAbstractPostgresMigration;

/**
 * Migration to widen graph_vertex.status column from VARCHAR(32) to VARCHAR(64).
 * Some Status enum values exceed 32 characters (e.g., QUEUED_GLOBAL_INFRA_CAPACITY_REACHED = 36 chars).
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class WidenGraphVertexStatusColumn extends NGAbstractPostgresMigration {
  @Override
  public String getFileName() {
    return "timescale/widen_graph_vertex_status_column.sql";
  }

  @Override
  public boolean runInTransaction() {
    return true;
  }
}
