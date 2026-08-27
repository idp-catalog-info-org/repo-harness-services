/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.beans.OrchestrationAdjacencyListInternal;
import io.harness.beans.OrchestrationGraph;
import io.harness.cache.EntityWithAccountId;
import io.harness.category.element.UnitTests;
import io.harness.postgres.PostgresDBService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Test class for {@link PostgreSQLGraphStoreServiceImpl}
 */
@Slf4j
public class PostgreSQLGraphStoreServiceImplTest extends OrchestrationVisualizationTestBase {
  @Mock private PostgresDBService postgresDBService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private Connection connection;
  @Mock private PreparedStatement preparedStatement;
  @Mock private ResultSet resultSet;

  @InjectMocks private PostgreSQLGraphStoreServiceImpl postgreSQLGraphStoreService;

  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String ACCOUNT_ID = "accountId";
  private static final Duration TTL = Duration.ofHours(1);
  private static final byte[] GRAPH_DATA_BYTES = "graphData".getBytes();

  private OrchestrationGraph orchestrationGraph;

  @Before
  public void setup() throws SQLException {
    orchestrationGraph = OrchestrationGraph.builder()
                             .planExecutionId(PLAN_EXECUTION_ID)
                             .cacheKey(PLAN_EXECUTION_ID)
                             .rootNodeIds(Collections.singletonList("rootNode"))
                             .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                .graphVertexMap(new HashMap<>())
                                                .adjacencyMap(new HashMap<>())
                                                .build())
                             .build();

    when(postgresDBService.getDBConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpsertWithValidGraph() throws SQLException {
    // Given
    when(kryoSerializer.asDeflatedBytes(orchestrationGraph)).thenReturn(GRAPH_DATA_BYTES);

    // When
    postgreSQLGraphStoreService.upsert(orchestrationGraph, TTL, ACCOUNT_ID);

    // Then
    verify(postgresDBService).getDBConnection();
    verify(connection).prepareStatement(any());
    verify(preparedStatement).setString(1, PLAN_EXECUTION_ID);
    verify(preparedStatement).setLong(2, orchestrationGraph.contextHash());
    verify(preparedStatement).setString(3, ACCOUNT_ID);
    verify(preparedStatement).setBytes(4, GRAPH_DATA_BYTES);
    verify(preparedStatement).executeUpdate();
    verify(preparedStatement).close();
    verify(connection).close();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpsertWithNullGraph() throws SQLException {
    // When
    postgreSQLGraphStoreService.upsert(null, TTL, ACCOUNT_ID);

    // Then
    verify(postgresDBService, never()).getDBConnection();
    verify(preparedStatement, never()).executeUpdate();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpsertWithNullPlanExecutionId() throws SQLException {
    // Given
    OrchestrationGraph graphWithoutPlanId = OrchestrationGraph.builder().build();

    // When
    postgreSQLGraphStoreService.upsert(graphWithoutPlanId, TTL, ACCOUNT_ID);

    // Then
    verify(postgresDBService, never()).getDBConnection();
    verify(preparedStatement, never()).executeUpdate();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpsertWithSQLException() throws SQLException {
    // Given
    when(kryoSerializer.asDeflatedBytes(orchestrationGraph)).thenReturn(GRAPH_DATA_BYTES);
    doThrow(new SQLException("Database error")).when(preparedStatement).executeUpdate();

    // When
    postgreSQLGraphStoreService.upsert(orchestrationGraph, TTL, ACCOUNT_ID);

    // Then
    verify(postgresDBService).getDBConnection();
    verify(preparedStatement).executeUpdate();
    // Should handle exception gracefully and log error
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpsertWithEntityUpdatedAt() throws SQLException {
    // Given
    long entityUpdatedAt = System.currentTimeMillis();
    when(kryoSerializer.asDeflatedBytes(orchestrationGraph)).thenReturn(GRAPH_DATA_BYTES);

    // When
    postgreSQLGraphStoreService.upsert(orchestrationGraph, TTL, entityUpdatedAt, ACCOUNT_ID);

    // Then
    verify(postgresDBService).getDBConnection();
    verify(preparedStatement).setString(1, PLAN_EXECUTION_ID);
    verify(preparedStatement).setLong(2, orchestrationGraph.contextHash());
    verify(preparedStatement).setString(3, ACCOUNT_ID);
    verify(preparedStatement).setBytes(4, GRAPH_DATA_BYTES);
    verify(preparedStatement).setLong(7, entityUpdatedAt);
    verify(preparedStatement).executeUpdate();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetWithValidPlanExecutionId() throws SQLException {
    // Given
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getBytes("graph_data")).thenReturn(GRAPH_DATA_BYTES);
    when(kryoSerializer.asInflatedObject(GRAPH_DATA_BYTES)).thenReturn(orchestrationGraph);

    // When
    OrchestrationGraph result = postgreSQLGraphStoreService.get(PLAN_EXECUTION_ID);

    // Then
    assertThat(result).isEqualTo(orchestrationGraph);
    verify(preparedStatement).setString(1, PLAN_EXECUTION_ID);
    verify(preparedStatement).executeQuery();
    verify(resultSet).close();
    verify(preparedStatement).close();
    verify(connection).close();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetWithNullPlanExecutionId() throws SQLException {
    // When
    OrchestrationGraph result = postgreSQLGraphStoreService.get(null);

    // Then
    assertThat(result).isNull();
    verify(postgresDBService, never()).getDBConnection();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetWhenNoDataFound() throws SQLException {
    // Given
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    // When
    OrchestrationGraph result = postgreSQLGraphStoreService.get(PLAN_EXECUTION_ID);

    // Then
    assertThat(result).isNull();
    verify(preparedStatement).setString(1, PLAN_EXECUTION_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetWithSQLException() throws SQLException {
    // Given
    doThrow(new SQLException("Query error")).when(preparedStatement).executeQuery();

    // When
    OrchestrationGraph result = postgreSQLGraphStoreService.get(PLAN_EXECUTION_ID);

    // Then
    assertThat(result).isNull();
    verify(preparedStatement).setString(1, PLAN_EXECUTION_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetWithAccountId() throws SQLException {
    // Given
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getString("account_identifier")).thenReturn(ACCOUNT_ID);
    when(resultSet.getBytes("graph_data")).thenReturn(GRAPH_DATA_BYTES);
    when(kryoSerializer.asInflatedObject(GRAPH_DATA_BYTES)).thenReturn(orchestrationGraph);

    // When
    EntityWithAccountId result = postgreSQLGraphStoreService.getWithAccountId(PLAN_EXECUTION_ID);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.getEntity()).isEqualTo(orchestrationGraph);
    verify(preparedStatement).setString(1, PLAN_EXECUTION_ID);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetWithAccountIdWhenNullPlanExecutionId() throws SQLException {
    // When
    EntityWithAccountId result = postgreSQLGraphStoreService.getWithAccountId(null);

    // Then
    assertThat(result).isNull();
    verify(postgresDBService, never()).getDBConnection();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetWithAccountIdWhenNoData() throws SQLException {
    // Given
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    // When
    EntityWithAccountId result = postgreSQLGraphStoreService.getWithAccountId(PLAN_EXECUTION_ID);

    // Then
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetWithAccountIdWhenNullGraphData() throws SQLException {
    // Given
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getString("account_identifier")).thenReturn(ACCOUNT_ID);
    when(resultSet.getBytes("graph_data")).thenReturn(null);

    // When
    EntityWithAccountId result = postgreSQLGraphStoreService.getWithAccountId(PLAN_EXECUTION_ID);

    // Then
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testGetFromSecondary() throws SQLException {
    // Given
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getString("account_identifier")).thenReturn(ACCOUNT_ID);
    when(resultSet.getBytes("graph_data")).thenReturn(GRAPH_DATA_BYTES);
    when(kryoSerializer.asInflatedObject(GRAPH_DATA_BYTES)).thenReturn(orchestrationGraph);

    // When
    EntityWithAccountId result = postgreSQLGraphStoreService.getFromSecondary(1L, 2L, PLAN_EXECUTION_ID, ACCOUNT_ID);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(result.getEntity()).isEqualTo(orchestrationGraph);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteWithValidPlanExecutionId() throws SQLException {
    // When
    postgreSQLGraphStoreService.delete(PLAN_EXECUTION_ID);

    // Then
    verify(postgresDBService).getDBConnection();
    verify(preparedStatement).setString(1, PLAN_EXECUTION_ID);
    verify(preparedStatement).executeUpdate();
    verify(preparedStatement).close();
    verify(connection).close();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteWithNullPlanExecutionId() throws SQLException {
    // When
    postgreSQLGraphStoreService.delete((String) null);

    // Then
    verify(postgresDBService, never()).getDBConnection();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteWithSQLException() throws SQLException {
    // Given
    doThrow(new SQLException("Delete error")).when(preparedStatement).executeUpdate();

    // When
    postgreSQLGraphStoreService.delete(PLAN_EXECUTION_ID);

    // Then
    verify(preparedStatement).setString(1, PLAN_EXECUTION_ID);
    verify(preparedStatement).executeUpdate();
    // Should handle exception gracefully
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteMultipleGraphs() throws SQLException {
    // Given
    OrchestrationGraph graph1 = OrchestrationGraph.builder().cacheKey("key1").build();
    OrchestrationGraph graph2 = OrchestrationGraph.builder().cacheKey("key2").build();
    OrchestrationGraph graph3 = OrchestrationGraph.builder().cacheKey(null).build();
    List<OrchestrationGraph> graphs = Arrays.asList(graph1, graph2, null, graph3);
    when(preparedStatement.executeUpdate()).thenReturn(2);

    // When
    postgreSQLGraphStoreService.delete(graphs);

    // Then
    verify(postgresDBService).getDBConnection();

    // Verify SQL query construction
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sqlCaptor.capture());
    String sql = sqlCaptor.getValue();
    assertThat(sql).isEqualTo("DELETE FROM orchestration_graph_cache WHERE cache_key IN (?,?)");

    // Verify parameters set correctly - batch operation sets all parameters in one call
    verify(preparedStatement).setString(1, "key1");
    verify(preparedStatement).setString(2, "key2");
    verify(preparedStatement, times(1)).executeUpdate();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteMultipleGraphsWithNullList() throws SQLException {
    // When
    postgreSQLGraphStoreService.delete((List<OrchestrationGraph>) null);

    // Then
    verify(postgresDBService, never()).getDBConnection();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteMultipleGraphsWithEmptyList() throws SQLException {
    // When
    postgreSQLGraphStoreService.delete(new ArrayList<>());

    // Then
    verify(postgresDBService, never()).getDBConnection();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteUsingPattern() throws SQLException {
    // Given
    OrchestrationGraph graph1 = OrchestrationGraph.builder().cacheKey("pattern1").build();
    OrchestrationGraph graph2 = OrchestrationGraph.builder().cacheKey("pattern2").build();
    List<OrchestrationGraph> graphs = Arrays.asList(graph1, graph2);
    when(preparedStatement.executeUpdate()).thenReturn(4); // Assume deleted main graphs + subgraphs

    // When
    postgreSQLGraphStoreService.deleteUsingPattern(graphs);

    // Then
    verify(postgresDBService).getDBConnection();

    // Verify SQL query construction for LIKE pattern matching
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sqlCaptor.capture());
    String sql = sqlCaptor.getValue();
    assertThat(sql).isEqualTo("DELETE FROM orchestration_graph_cache WHERE "
        + "cache_key LIKE ? OR "
        + "cache_key LIKE ?");

    // Verify parameters set correctly - all in one batch operation
    verify(preparedStatement).setString(1, "pattern1%"); // Prefix pattern with wildcard
    verify(preparedStatement).setString(2, "pattern2%"); // Prefix pattern with wildcard
    verify(preparedStatement, times(1)).executeUpdate();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testTimestampHandling() throws SQLException {
    // Given
    when(kryoSerializer.asDeflatedBytes(orchestrationGraph)).thenReturn(GRAPH_DATA_BYTES);
    ArgumentCaptor<Timestamp> timestampCaptor = ArgumentCaptor.forClass(Timestamp.class);

    // When
    Instant before = Instant.now();
    postgreSQLGraphStoreService.upsert(orchestrationGraph, TTL, ACCOUNT_ID);
    Instant after = Instant.now();

    // Then
    verify(preparedStatement, times(3)).setTimestamp(any(Integer.class), timestampCaptor.capture());
    List<Timestamp> timestamps = timestampCaptor.getAllValues();

    // Verify created_at and last_updated_at are set to current time
    assertThat(timestamps.get(0).toInstant()).isBetween(before, after);
    assertThat(timestamps.get(1).toInstant()).isBetween(before, after);

    // Verify valid_until is set to current time + TTL
    assertThat(timestamps.get(2).toInstant()).isBetween(before.plus(TTL), after.plus(TTL));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testConnectionResourceManagement() throws SQLException {
    // Given
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getBytes("graph_data")).thenReturn(GRAPH_DATA_BYTES);
    when(kryoSerializer.asInflatedObject(GRAPH_DATA_BYTES)).thenReturn(orchestrationGraph);

    // When
    postgreSQLGraphStoreService.get(PLAN_EXECUTION_ID);

    // Then - verify resources are closed in correct order
    verify(resultSet).close();
    verify(preparedStatement).close();
    verify(connection).close();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testVersionHandling() throws SQLException {
    // Given
    when(kryoSerializer.asDeflatedBytes(orchestrationGraph)).thenReturn(GRAPH_DATA_BYTES);

    // When
    postgreSQLGraphStoreService.upsert(orchestrationGraph, TTL, ACCOUNT_ID);

    // Then
    verify(preparedStatement).setLong(9, 1); // Initial version should be 1
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testContextHashCalculation() throws SQLException {
    // Given
    when(kryoSerializer.asDeflatedBytes(orchestrationGraph)).thenReturn(GRAPH_DATA_BYTES);

    // When
    postgreSQLGraphStoreService.upsert(orchestrationGraph, TTL, ACCOUNT_ID);

    // Then
    verify(preparedStatement).setLong(2, orchestrationGraph.contextHash());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testNullKryoDataHandling() throws SQLException {
    // Given
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getBytes("graph_data")).thenReturn(null);

    // When
    OrchestrationGraph result = postgreSQLGraphStoreService.get(PLAN_EXECUTION_ID);

    // Then
    assertThat(result).isNull();
    verify(kryoSerializer, never()).asInflatedObject(any());
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeserializationWithInvalidData() throws SQLException {
    // Given
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true);
    when(resultSet.getString("account_identifier")).thenReturn(ACCOUNT_ID);
    when(resultSet.getBytes("graph_data")).thenReturn(GRAPH_DATA_BYTES);
    when(kryoSerializer.asInflatedObject(GRAPH_DATA_BYTES)).thenReturn(null);

    // When
    EntityWithAccountId result = postgreSQLGraphStoreService.getWithAccountId(PLAN_EXECUTION_ID);

    // Then
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCurrentTimeMillisUsage() throws SQLException {
    // Given
    when(kryoSerializer.asDeflatedBytes(orchestrationGraph)).thenReturn(GRAPH_DATA_BYTES);
    ArgumentCaptor<Long> longCaptor = ArgumentCaptor.forClass(Long.class);

    // When
    long before = System.currentTimeMillis();
    postgreSQLGraphStoreService.upsert(orchestrationGraph, TTL, ACCOUNT_ID);
    long after = System.currentTimeMillis();

    // Then
    verify(preparedStatement).setLong(eq(7), longCaptor.capture());
    assertThat(longCaptor.getValue()).isBetween(before, after);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFindCacheKeysByPattern() throws SQLException {
    // Given
    String pattern = "planExecutionId123/%";
    String cacheKey1 = "planExecutionId123/nodeId456";
    String cacheKey2 = "planExecutionId123/nodeId789";

    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(true, true, false);
    when(resultSet.getString("cache_key")).thenReturn(cacheKey1, cacheKey2);

    // When
    List<String> result = postgreSQLGraphStoreService.findCacheKeysByPattern(pattern);

    // Then
    verify(preparedStatement).setString(1, pattern);
    verify(preparedStatement).executeQuery();
    assertThat(result).hasSize(2);
    assertThat(result).containsExactly(cacheKey1, cacheKey2);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFindCacheKeysByPatternWithNoResults() throws SQLException {
    // Given
    String pattern = "nonexistent/%";
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.next()).thenReturn(false);

    // When
    List<String> result = postgreSQLGraphStoreService.findCacheKeysByPattern(pattern);

    // Then
    verify(preparedStatement).setString(1, pattern);
    verify(preparedStatement).executeQuery();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFindCacheKeysByPatternWithSQLException() throws SQLException {
    // Given
    String pattern = "planExecutionId123/%";
    when(preparedStatement.executeQuery()).thenThrow(new SQLException("Database error"));

    // When
    List<String> result = postgreSQLGraphStoreService.findCacheKeysByPattern(pattern);

    // Then
    verify(preparedStatement).setString(1, pattern);
    assertThat(result).isEmpty();
  }

  // ============= DELETE FUNCTION TESTS =============

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteSingleGraph() throws SQLException {
    // Given
    String planExecutionId = "testPlanId";
    when(preparedStatement.executeUpdate()).thenReturn(1);

    // When
    postgreSQLGraphStoreService.delete(planExecutionId);

    // Then
    verify(postgresDBService).getDBConnection();
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sqlCaptor.capture());
    assertThat(sqlCaptor.getValue().trim()).isEqualTo("DELETE FROM orchestration_graph_cache WHERE cache_key = ?");
    verify(preparedStatement).setString(1, planExecutionId);
    verify(preparedStatement).executeUpdate();
    // Note: close() is called automatically by try-with-resources
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteMultipleGraphsWithNullsAndEmptyKeys() throws SQLException {
    // Given
    List<OrchestrationGraph> graphs = Arrays.asList(null, OrchestrationGraph.builder().cacheKey("key1").build(),
        OrchestrationGraph.builder().cacheKey(null).build(), OrchestrationGraph.builder().cacheKey("key2").build());
    when(preparedStatement.executeUpdate()).thenReturn(2);

    // When
    postgreSQLGraphStoreService.delete(graphs);

    // Then
    verify(postgresDBService).getDBConnection();

    // Verify SQL query construction - should only have 2 placeholders
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sqlCaptor.capture());
    String sql = sqlCaptor.getValue();
    assertThat(sql).isEqualTo("DELETE FROM orchestration_graph_cache WHERE cache_key IN (?,?)");

    // Verify only valid keys are set
    verify(preparedStatement).setString(1, "key1");
    verify(preparedStatement).setString(2, "key2");
    verify(preparedStatement).executeUpdate();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteUsingPatternWithCompositeKeys() throws SQLException {
    // Given - Testing with composite keys (planExecutionId/nodeExecutionId format)
    List<OrchestrationGraph> graphs = Arrays.asList(OrchestrationGraph.builder().cacheKey("planId1/nodeId1").build(),
        OrchestrationGraph.builder().cacheKey("planId2/nodeId2").build());
    when(preparedStatement.executeUpdate()).thenReturn(2);

    // When
    postgreSQLGraphStoreService.deleteUsingPattern(graphs);

    // Then
    verify(postgresDBService).getDBConnection();

    // Verify parameters include pattern for prefix matching
    verify(preparedStatement).setString(1, "planId1/nodeId1%");
    verify(preparedStatement).setString(2, "planId2/nodeId2%");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteUsingPatternWithNullsAndEmptyKeys() throws SQLException {
    // Given
    List<OrchestrationGraph> graphs = Arrays.asList(null, OrchestrationGraph.builder().cacheKey("planId1").build(),
        OrchestrationGraph.builder().cacheKey(null).build(), OrchestrationGraph.builder().cacheKey("planId2").build());
    when(preparedStatement.executeUpdate()).thenReturn(4);

    // When
    postgreSQLGraphStoreService.deleteUsingPattern(graphs);

    // Then
    verify(postgresDBService).getDBConnection();

    // Verify SQL query construction - should only have LIKE conditions for valid keys
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sqlCaptor.capture());
    String sql = sqlCaptor.getValue();
    assertThat(sql).isEqualTo("DELETE FROM orchestration_graph_cache WHERE "
        + "cache_key LIKE ? OR "
        + "cache_key LIKE ?");

    // Verify only valid keys are set with % wildcard
    verify(preparedStatement).setString(1, "planId1%");
    verify(preparedStatement).setString(2, "planId2%");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteBatchWithLargeNumberOfKeys() throws SQLException {
    // Given - Test with a large number of keys to ensure SQL construction is correct
    int numKeys = 50;
    List<OrchestrationGraph> graphs = IntStream.range(0, numKeys)
                                          .mapToObj(i -> OrchestrationGraph.builder().cacheKey("key" + i).build())
                                          .collect(Collectors.toList());
    when(preparedStatement.executeUpdate()).thenReturn(numKeys);

    // When
    postgreSQLGraphStoreService.delete(graphs);

    // Then
    verify(postgresDBService).getDBConnection();

    // Verify SQL has correct number of placeholders
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sqlCaptor.capture());
    String sql = sqlCaptor.getValue();

    // Count question marks in SQL
    long placeholderCount = sql.chars().filter(ch -> ch == '?').count();
    assertThat(placeholderCount).isEqualTo(numKeys);

    // Verify all parameters are set
    for (int i = 0; i < numKeys; i++) {
      verify(preparedStatement).setString(i + 1, "key" + i);
    }
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteUsingPatternWithLargeNumberOfKeys() throws SQLException {
    // Given - Test pattern deletion with many keys
    int numKeys = 25;
    List<OrchestrationGraph> graphs = IntStream.range(0, numKeys)
                                          .mapToObj(i -> OrchestrationGraph.builder().cacheKey("planId" + i).build())
                                          .collect(Collectors.toList());
    when(preparedStatement.executeUpdate()).thenReturn(numKeys * 3); // Assume each has 2 subgraphs

    // When
    postgreSQLGraphStoreService.deleteUsingPattern(graphs);

    // Then
    verify(postgresDBService).getDBConnection();

    // Verify all parameters are set (1 per key with % wildcard)
    for (int i = 0; i < numKeys; i++) {
      verify(preparedStatement).setString(i + 1, "planId" + i + "%");
    }
  }

  // ============= DELETE EXPIRED GRAPHS TESTS =============

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteExpiredGraphsWithRecordsDeleted() throws SQLException {
    // Given
    int batchSize = 1000;
    int expectedDeletedCount = 50;
    when(preparedStatement.executeUpdate()).thenReturn(expectedDeletedCount);

    // When
    int result = postgreSQLGraphStoreService.deleteExpiredGraphs(batchSize);

    // Then
    assertThat(result).isEqualTo(expectedDeletedCount);
    verify(postgresDBService).getDBConnection();

    // Verify SQL query contains ctid-based subquery with LIMIT
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sqlCaptor.capture());
    String sql = sqlCaptor.getValue();
    assertThat(sql).contains("DELETE FROM orchestration_graph_cache");
    assertThat(sql).contains("ctid");
    assertThat(sql).contains("valid_until < NOW()");
    assertThat(sql).contains("LIMIT ?");

    // Verify batchSize parameter is set
    verify(preparedStatement).setInt(1, batchSize);
    verify(preparedStatement).executeUpdate();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteExpiredGraphsWithNoRecordsToDelete() throws SQLException {
    // Given
    int batchSize = 500;
    when(preparedStatement.executeUpdate()).thenReturn(0);

    // When
    int result = postgreSQLGraphStoreService.deleteExpiredGraphs(batchSize);

    // Then
    assertThat(result).isEqualTo(0);
    verify(postgresDBService).getDBConnection();
    verify(preparedStatement).setInt(1, batchSize);
    verify(preparedStatement).executeUpdate();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteExpiredGraphsWithSQLException() throws SQLException {
    // Given
    int batchSize = 1000;
    when(preparedStatement.executeUpdate()).thenThrow(new SQLException("Database error"));

    // When
    int result = postgreSQLGraphStoreService.deleteExpiredGraphs(batchSize);

    // Then
    assertThat(result).isEqualTo(0);
    verify(postgresDBService).getDBConnection();
    verify(preparedStatement).setInt(1, batchSize);
    verify(preparedStatement).executeUpdate();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteExpiredGraphsWithConnectionException() throws SQLException {
    // Given
    int batchSize = 1000;
    when(postgresDBService.getDBConnection()).thenThrow(new SQLException("Connection failed"));

    // When
    int result = postgreSQLGraphStoreService.deleteExpiredGraphs(batchSize);

    // Then
    assertThat(result).isEqualTo(0);
    verify(postgresDBService).getDBConnection();
    verify(preparedStatement, never()).executeUpdate();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteExpiredGraphsResourceManagement() throws SQLException {
    // Given
    int batchSize = 1000;
    when(preparedStatement.executeUpdate()).thenReturn(10);

    // When
    postgreSQLGraphStoreService.deleteExpiredGraphs(batchSize);

    // Then - verify resources are properly closed via try-with-resources
    verify(preparedStatement).close();
    verify(connection).close();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testDeleteExpiredGraphsFullBatchDeleted() throws SQLException {
    // Given - When deleted count equals batch size, caller should call again
    int batchSize = 1000;
    when(preparedStatement.executeUpdate()).thenReturn(batchSize);

    // When
    int result = postgreSQLGraphStoreService.deleteExpiredGraphs(batchSize);

    // Then
    assertThat(result).isEqualTo(batchSize);
    verify(preparedStatement).setInt(1, batchSize);
  }
}
