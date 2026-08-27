/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.migration.postgres.NGAbstractPostgresMigration;

/**
 * Migration to add pattern ops index for efficient LIKE prefix queries on orchestration_graph_cache table.
 * This index optimizes DELETE operations that use LIKE 'prefix%' patterns for batch deletions,
 * particularly for deleteUsingPattern operations that delete graphs with composite keys.
 */
public class AddPatternOpsIndexOrchestrationGraphCache extends NGAbstractPostgresMigration {
  private static final String ADD_PATTERN_OPS_INDEX_FILE_NAME =
      "timescale/add_pattern_ops_index_orchestration_graph_cache.sql";

  @Override
  public String getFileName() {
    return ADD_PATTERN_OPS_INDEX_FILE_NAME;
  }

  @Override
  public boolean runInTransaction() {
    // Index creation should not run in a transaction to avoid blocking
    return false;
  }
}
