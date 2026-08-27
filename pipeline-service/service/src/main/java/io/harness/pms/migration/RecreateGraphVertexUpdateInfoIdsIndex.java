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
 * Migration to recreate GIN index on graph_vertex.graph_update_info_ids as non-partial.
 * The old partial index was not usable with FOR UPDATE queries, causing full table scans.
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class RecreateGraphVertexUpdateInfoIdsIndex extends NGAbstractPostgresMigration {
  @Override
  public String getFileName() {
    return "timescale/recreate_graph_vertex_update_info_ids_index.sql";
  }

  @Override
  public boolean runInTransaction() {
    return true;
  }
}
