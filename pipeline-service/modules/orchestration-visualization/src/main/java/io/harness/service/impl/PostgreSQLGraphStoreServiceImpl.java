/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationGraph;
import io.harness.cache.EntityWithAccountId;
import io.harness.postgres.PostgresDBService;
import io.harness.serializer.KryoSerializer;
import io.harness.service.PostgreSQLGraphStoreService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL-based graph store service for orchestration graphs.
 * Simple implementation using cache_key as the primary key.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PostgreSQLGraphStoreServiceImpl implements PostgreSQLGraphStoreService {
  private static final String COL_ACCOUNT_IDENTIFIER = "account_identifier";
  private static final String COL_GRAPH_DATA = "graph_data";
  private static final String UPSERT_GRAPH_SQL = """
      INSERT INTO orchestration_graph_cache (
          cache_key, context_value, account_identifier, graph_data,
          created_at, last_updated_at, entity_updated_at, valid_until, version
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (cache_key) DO UPDATE SET
          graph_data = EXCLUDED.graph_data,
          last_updated_at = EXCLUDED.last_updated_at,
          entity_updated_at = EXCLUDED.entity_updated_at,
          valid_until = EXCLUDED.valid_until,
          version = orchestration_graph_cache.version + 1
      """;

  private static final String GET_GRAPH_SQL = """
      SELECT graph_data
      FROM orchestration_graph_cache 
      WHERE cache_key = ? AND valid_until > NOW()
      """;

  private static final String GET_GRAPH_WITH_ACCOUNT_SQL = """
      SELECT account_identifier, graph_data
      FROM orchestration_graph_cache 
      WHERE cache_key = ? AND valid_until > NOW()
      """;

  private static final String DELETE_GRAPH_SQL = """
      DELETE FROM orchestration_graph_cache WHERE cache_key = ?
      """;

  @Inject private PostgresDBService postgresDBService;

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer kryoSerializer;

  /**
   * Store or update an orchestration graph in PostgreSQL
   */
  public void upsert(OrchestrationGraph orchestrationGraph, Duration ttl, String accountIdentifier) {
    if (orchestrationGraph == null || orchestrationGraph.getCacheKey() == null) {
      log.warn("Cannot store null orchestration graph or graph without cacheKey");
      return;
    }

    try (Connection connection = postgresDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(UPSERT_GRAPH_SQL)) {
      byte[] graphDataBytes = kryoSerializer.asDeflatedBytes(orchestrationGraph);
      Timestamp now = Timestamp.from(Instant.now());
      Timestamp validUntil = Timestamp.from(Instant.now().plus(ttl));
      long currentTimeMillis = System.currentTimeMillis();

      statement.setString(1, orchestrationGraph.getCacheKey());
      statement.setLong(2, orchestrationGraph.contextHash());
      statement.setString(3, accountIdentifier);
      statement.setBytes(4, graphDataBytes);
      statement.setTimestamp(5, now); // created_at
      statement.setTimestamp(6, now); // last_updated_at
      statement.setLong(7, currentTimeMillis); // entity_updated_at
      statement.setTimestamp(8, validUntil);
      statement.setLong(9, 1);

      statement.executeUpdate();
      log.debug("Stored graph in PostgreSQL for cacheKey: {}", orchestrationGraph.getCacheKey());

    } catch (SQLException e) {
      log.error("Failed to store graph in PostgreSQL for cacheKey: {}", orchestrationGraph.getCacheKey(), e);
    }
  }

  /**
   * Upsert with entity updated timestamp (for partial graph updates)
   */
  public void upsert(OrchestrationGraph orchestrationGraph, Duration ttl, long entityUpdatedAt, String accountId) {
    if (orchestrationGraph == null || orchestrationGraph.getCacheKey() == null) {
      log.warn("Cannot store null orchestration graph or graph without cacheKey");
      return;
    }

    try (Connection connection = postgresDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(UPSERT_GRAPH_SQL)) {
      byte[] graphDataBytes = kryoSerializer.asDeflatedBytes(orchestrationGraph);
      Timestamp now = Timestamp.from(Instant.now());
      Timestamp validUntil = Timestamp.from(Instant.now().plus(ttl));

      statement.setString(1, orchestrationGraph.getCacheKey());
      statement.setLong(2, orchestrationGraph.contextHash());
      statement.setString(3, accountId);
      statement.setBytes(4, graphDataBytes);
      statement.setTimestamp(5, now); // created_at
      statement.setTimestamp(6, now); // last_updated_at
      statement.setLong(7, entityUpdatedAt); // entity_updated_at
      statement.setTimestamp(8, validUntil);
      statement.setLong(9, 1);

      statement.executeUpdate();
      log.debug("Stored partial graph in PostgreSQL for cacheKey: {}", orchestrationGraph.getCacheKey());

    } catch (SQLException e) {
      log.error("Failed to store partial graph in PostgreSQL for cacheKey: {}", orchestrationGraph.getCacheKey(), e);
    }
  }

  /**
   * Retrieve an orchestration graph from PostgreSQL
   */
  public OrchestrationGraph get(String planExecutionId) {
    if (planExecutionId == null) {
      return null;
    }

    try (Connection connection = postgresDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(GET_GRAPH_SQL)) {
      statement.setString(1, planExecutionId);

      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          byte[] kryoData = resultSet.getBytes(COL_GRAPH_DATA);
          if (kryoData != null) {
            return (OrchestrationGraph) kryoSerializer.asInflatedObject(kryoData);
          }
        }
      }

    } catch (SQLException e) {
      log.warn("Failed to retrieve graph from PostgreSQL for planExecutionId: {}", planExecutionId, e);
    }

    return null;
  }

  /**
   * Retrieve with account information
   */
  public EntityWithAccountId getWithAccountId(String planExecutionId) {
    if (planExecutionId == null) {
      return null;
    }

    try (Connection connection = postgresDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(GET_GRAPH_WITH_ACCOUNT_SQL)) {
      statement.setString(1, planExecutionId);

      try (ResultSet resultSet = statement.executeQuery()) {
        if (resultSet.next()) {
          String accountIdentifier = resultSet.getString(COL_ACCOUNT_IDENTIFIER);
          byte[] kryoData = resultSet.getBytes(COL_GRAPH_DATA);

          if (kryoData != null) {
            OrchestrationGraph graph = (OrchestrationGraph) kryoSerializer.asInflatedObject(kryoData);
            if (graph != null) {
              return EntityWithAccountId.builder().entity(graph).accountId(accountIdentifier).build();
            }
          }
        }
      }

    } catch (SQLException e) {
      log.error("Failed to retrieve graph with account from PostgreSQL for planExecutionId: {}", planExecutionId, e);
    }

    return null;
  }

  /**
   * Retrieve from secondary (same as primary for now)
   */
  public EntityWithAccountId getFromSecondary(
      long algorithmId, long structureHash, String planExecutionId, String accountIdentifier) {
    return getWithAccountId(planExecutionId);
  }

  /**
   * Delete a graph
   */
  public void delete(String planExecutionId) {
    if (planExecutionId == null) {
      return;
    }

    try (Connection connection = postgresDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(DELETE_GRAPH_SQL)) {
      statement.setString(1, planExecutionId);
      statement.executeUpdate();

    } catch (SQLException e) {
      log.error("Failed to delete graph from PostgreSQL for planExecutionId: {}", planExecutionId, e);
    }
  }

  /**
   * Delete multiple graphs
   * Delete multiple graphs in a single batch operation
   */
  public void delete(List<OrchestrationGraph> graphs) {
    if (graphs == null || graphs.isEmpty()) {
      return;
    }
    // Collect all cache keys
    List<String> cacheKeys = getCacheKeys(graphs);

    if (cacheKeys.isEmpty()) {
      return;
    }

    // Delete all in a single batch
    deleteBatch(cacheKeys);
  }

  /**
   * Delete using pattern (for old retry graphs)
   * Delete using pattern (for old retry graphs with planExecutionId/nodeExecutionId format)
   * Deletes all graphs and their subgraphs in a single batch operation
   */
  public void deleteUsingPattern(List<OrchestrationGraph> graphs) {
    if (graphs == null || graphs.isEmpty()) {
      return;
    }

    // Collect all cache keys that are not null
    List<String> cacheKeys = getCacheKeys(graphs);

    if (cacheKeys.isEmpty()) {
      return;
    }

    // Delete all graphs and their patterns in a single batch
    deleteByPatternBatch(cacheKeys);
  }

  private List<String> getCacheKeys(List<OrchestrationGraph> graphs) {
    return graphs.stream()
        .filter(graph -> graph != null && graph.getCacheKey() != null)
        .map(OrchestrationGraph::getCacheKey)
        .collect(Collectors.toList());
  }

  /**
   * Find cache keys matching a pattern (for subgraph queries)
   * @param pattern SQL LIKE pattern (e.g., "planExecutionId/%")
   * @return List of matching cache keys
   */
  public List<String> findCacheKeysByPattern(String pattern) {
    List<String> cacheKeys = new ArrayList<>();
    String sql = "SELECT cache_key FROM orchestration_graph_cache WHERE cache_key LIKE ?";

    try (Connection connection = postgresDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, pattern);

      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          cacheKeys.add(resultSet.getString("cache_key"));
        }
      }
    } catch (SQLException e) {
      log.error("Failed to find cache keys by pattern: {}", pattern, e);
    }

    return cacheKeys;
  }

  /**
   * Helper method to delete a batch of graphs by their cache keys
   */
  private void deleteBatch(List<String> cacheKeys) {
    if (cacheKeys.isEmpty()) {
      return;
    }

    // Build SQL with IN clause
    StringBuilder sql = new StringBuilder("DELETE FROM orchestration_graph_cache WHERE cache_key IN (");
    for (int i = 0; i < cacheKeys.size(); i++) {
      if (i > 0) {
        sql.append(",");
      }
      sql.append("?");
    }
    sql.append(")");

    try (Connection connection = postgresDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(sql.toString())) {
      // Set parameters
      for (int i = 0; i < cacheKeys.size(); i++) {
        statement.setString(i + 1, cacheKeys.get(i));
      }

      int deletedCount = statement.executeUpdate();
      log.debug("Deleted {} graphs from PostgreSQL in batch", deletedCount);

    } catch (SQLException e) {
      log.error("Failed to delete batch of {} graphs from PostgreSQL", cacheKeys.size(), e);
    }
  }

  /**
   * Helper method to delete graphs by pattern in batch (for retried step groups)
   * Deletes both exact matches and all entries with the key as prefix (planExecutionId/nodeExecutionId)
   * Uses LIKE with pattern ops index for efficient prefix matching
   */
  private void deleteByPatternBatch(List<String> cacheKeys) {
    if (cacheKeys.isEmpty()) {
      return;
    }

    // Build SQL with OR conditions for each cache key using LIKE
    // The text_pattern_ops index makes LIKE 'prefix%' queries efficient
    StringBuilder sql = new StringBuilder("DELETE FROM orchestration_graph_cache WHERE ");

    for (int i = 0; i < cacheKeys.size(); i++) {
      if (i > 0) {
        sql.append(" OR ");
      }
      // Use LIKE for prefix matching - will use idx_orch_cache_cache_key_pattern index
      sql.append("cache_key LIKE ?");
    }

    try (Connection connection = postgresDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(sql.toString())) {
      // Set parameters - append % wildcard for prefix matching
      int paramIndex = 1;
      for (String cacheKey : cacheKeys) {
        statement.setString(paramIndex++, cacheKey + "%"); // Prefix pattern
      }

      int deletedCount = statement.executeUpdate();
      log.debug("Deleted {} graphs from PostgreSQL using LIKE pattern for {} keys", deletedCount, cacheKeys.size());

    } catch (SQLException e) {
      log.error("Failed to delete batch of {} graphs by pattern from PostgreSQL", cacheKeys.size(), e);
    }
  }

  private static final String DELETE_EXPIRED_GRAPHS_SQL = """
      DELETE FROM orchestration_graph_cache
      WHERE ctid IN (
          SELECT ctid FROM orchestration_graph_cache
          WHERE valid_until < NOW()
          LIMIT ?
      )
      """;

  @Override
  public int deleteExpiredGraphs(int batchSize) {
    try (Connection connection = postgresDBService.getDBConnection();
         PreparedStatement statement = connection.prepareStatement(DELETE_EXPIRED_GRAPHS_SQL)) {
      statement.setInt(1, batchSize);
      int deletedCount = statement.executeUpdate();

      if (deletedCount > 0) {
        log.info("Deleted {} expired graphs from orchestration_graph_cache", deletedCount);
      }
      return deletedCount;
    } catch (SQLException e) {
      log.error("Failed to delete expired graphs from PostgreSQL", e);
      return 0;
    }
  }
}
