/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationGraph;
import io.harness.cache.EntityWithAccountId;

import java.time.Duration;
import java.util.List;

/**
 * PostgreSQL-based graph store service for orchestration graphs.
 * Simple implementation using plan_execution_id as the key.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public interface PostgreSQLGraphStoreService {
  /**
   * Store or update an orchestration graph in PostgreSQL
   */
  void upsert(OrchestrationGraph orchestrationGraph, Duration ttl, String accountIdentifier);

  /**
   * Upsert with entity updated timestamp (for partial graph updates)
   */
  void upsert(OrchestrationGraph orchestrationGraph, Duration ttl, long entityUpdatedAt, String accountId);

  /**
   * Retrieve an orchestration graph from PostgreSQL
   */
  OrchestrationGraph get(String planExecutionId);

  /**
   * Retrieve with account information
   */
  EntityWithAccountId getWithAccountId(String planExecutionId);

  /**
   * Retrieve from secondary (same as primary for now)
   */
  EntityWithAccountId getFromSecondary(
      long algorithmId, long structureHash, String planExecutionId, String accountIdentifier);

  /**
   * Delete a graph
   */
  void delete(String planExecutionId);

  /**
   * Delete multiple graphs
   */
  void delete(List<OrchestrationGraph> graphs);

  /**
   * Delete using pattern (for old retry graphs)
   */
  void deleteUsingPattern(List<OrchestrationGraph> graphs);

  /**
   * Find cache keys matching a pattern (for subgraph queries)
   * @param pattern SQL LIKE pattern (e.g., "planExecutionId/%")
   * @return List of matching cache keys
   */
  List<String> findCacheKeysByPattern(String pattern);

  /**
   * Delete expired graphs based on valid_until timestamp.
   * @param batchSize Maximum number of records to delete in one call
   * @return Number of records deleted
   */
  int deleteExpiredGraphs(int batchSize);
}
