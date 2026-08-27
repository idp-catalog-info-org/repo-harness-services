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
 * Migration to create normalized graph tables for CDC-based graph generation.
 *
 * Creates:
 * - graph_vertex: Individual vertices with normalized columns and adjacency info
 * - graph_layout_node: Layout node data for pipeline execution summary
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public class CreateGraphVertexTable extends NGAbstractPostgresMigration {
  @Override
  public String getFileName() {
    return "timescale/create_graph_vertex_table.sql";
  }

  @Override
  public boolean runInTransaction() {
    return true;
  }
}
