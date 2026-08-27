/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import io.harness.migration.postgres.NGAbstractPostgresMigration;

public class AddIndexOnValidUntilToOrchestrationGraphCacheTable extends NGAbstractPostgresMigration {
  private static final String ADD_INDEX_ON_VALID_UNTIL_TO_ORCHESTRATION_GRAPH_CACHE_FILE_NAME =
      "timescale/add_index_on_valid_until_to_orchestration_graph_cache.sql";

  @Override
  public String getFileName() {
    return ADD_INDEX_ON_VALID_UNTIL_TO_ORCHESTRATION_GRAPH_CACHE_FILE_NAME;
  }

  @Override
  public boolean runInTransaction() {
    return true;
  }
}
