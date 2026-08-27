/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.service.impl;

import static io.harness.graph.Tables.ORCHESTRATION_GRAPH_CACHE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationGraph;
import io.harness.cache.EntityWithAccountId;
import io.harness.serializer.KryoSerializer;
import io.harness.service.PostgreSQLGraphStoreService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

/**
 * PostgreSQL-based graph store service for orchestration graphs using JOOQ.
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j

public class PostgreSQLGraphStoreJOOQServiceImpl implements PostgreSQLGraphStoreService {
  @Inject @Named("PipelineServiceDSLContext") private DSLContext dsl;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer kryoSerializer;

  /**
   * Upsert orchestration graph.
   */
  @Override
  public void upsert(OrchestrationGraph graph, Duration ttl, String accountIdentifier) {
    if (graph == null || graph.getCacheKey() == null) {
      log.warn("Cannot store null orchestration graph or graph without cacheKey");
      return;
    }

    try {
      byte[] graphData = kryoSerializer.asDeflatedBytes(graph);
      OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
      OffsetDateTime validUntil = OffsetDateTime.ofInstant(Instant.now().plus(ttl), ZoneOffset.UTC);

      dsl.insertInto(ORCHESTRATION_GRAPH_CACHE)
          .set(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY, graph.getCacheKey())
          .set(ORCHESTRATION_GRAPH_CACHE.CONTEXT_VALUE, graph.contextHash())
          .set(ORCHESTRATION_GRAPH_CACHE.ACCOUNT_IDENTIFIER, accountIdentifier)
          .set(ORCHESTRATION_GRAPH_CACHE.GRAPH_DATA, graphData)
          .set(ORCHESTRATION_GRAPH_CACHE.CREATED_AT, now)
          .set(ORCHESTRATION_GRAPH_CACHE.LAST_UPDATED_AT, now)
          .set(ORCHESTRATION_GRAPH_CACHE.ENTITY_UPDATED_AT, System.currentTimeMillis())
          .set(ORCHESTRATION_GRAPH_CACHE.VALID_UNTIL, validUntil)
          .set(ORCHESTRATION_GRAPH_CACHE.VERSION, 1L)
          .onConflict(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY)
          .doUpdate()
          .set(ORCHESTRATION_GRAPH_CACHE.GRAPH_DATA, DSL.field("excluded.graph_data", byte[].class))
          .set(ORCHESTRATION_GRAPH_CACHE.LAST_UPDATED_AT, DSL.field("excluded.last_updated_at", OffsetDateTime.class))
          .set(ORCHESTRATION_GRAPH_CACHE.ENTITY_UPDATED_AT, DSL.field("excluded.entity_updated_at", Long.class))
          .set(ORCHESTRATION_GRAPH_CACHE.VALID_UNTIL, DSL.field("excluded.valid_until", OffsetDateTime.class))
          .set(ORCHESTRATION_GRAPH_CACHE.VERSION, ORCHESTRATION_GRAPH_CACHE.VERSION.plus(1))
          .execute();

      log.debug("Stored graph in PostgreSQL for cacheKey: {}", graph.getCacheKey());
    } catch (Exception e) {
      log.error("Failed to upsert orchestration graph for cacheKey {}", graph.getCacheKey(), e);
    }
  }

  @Override
  public void upsert(OrchestrationGraph graph, Duration ttl, long entityUpdatedAt, String accountId) {
    if (graph == null || graph.getCacheKey() == null) {
      log.warn("Cannot store null orchestration graph or graph without cacheKey");
      return;
    }

    try {
      byte[] graphData = kryoSerializer.asDeflatedBytes(graph);
      OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
      OffsetDateTime validUntil = OffsetDateTime.ofInstant(Instant.now().plus(ttl), ZoneOffset.UTC);

      dsl.insertInto(ORCHESTRATION_GRAPH_CACHE)
          .set(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY, graph.getCacheKey())
          .set(ORCHESTRATION_GRAPH_CACHE.CONTEXT_VALUE, graph.contextHash())
          .set(ORCHESTRATION_GRAPH_CACHE.ACCOUNT_IDENTIFIER, accountId)
          .set(ORCHESTRATION_GRAPH_CACHE.GRAPH_DATA, graphData)
          .set(ORCHESTRATION_GRAPH_CACHE.CREATED_AT, now)
          .set(ORCHESTRATION_GRAPH_CACHE.LAST_UPDATED_AT, now)
          .set(ORCHESTRATION_GRAPH_CACHE.ENTITY_UPDATED_AT, entityUpdatedAt)
          .set(ORCHESTRATION_GRAPH_CACHE.VALID_UNTIL, validUntil)
          .set(ORCHESTRATION_GRAPH_CACHE.VERSION, 1L)
          .onConflict(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY)
          .doUpdate()
          .set(ORCHESTRATION_GRAPH_CACHE.GRAPH_DATA, DSL.field("excluded.graph_data", byte[].class))
          .set(ORCHESTRATION_GRAPH_CACHE.LAST_UPDATED_AT, DSL.field("excluded.last_updated_at", OffsetDateTime.class))
          .set(ORCHESTRATION_GRAPH_CACHE.ENTITY_UPDATED_AT, DSL.field("excluded.entity_updated_at", Long.class))
          .set(ORCHESTRATION_GRAPH_CACHE.VALID_UNTIL, DSL.field("excluded.valid_until", OffsetDateTime.class))
          .set(ORCHESTRATION_GRAPH_CACHE.VERSION, ORCHESTRATION_GRAPH_CACHE.VERSION.plus(1))
          .execute();

      log.debug("Stored partial graph in PostgreSQL for cacheKey: {}", graph.getCacheKey());
    } catch (Exception e) {
      log.error("Failed to upsert partial orchestration graph for cacheKey {}", graph.getCacheKey(), e);
    }
  }

  @Override
  public OrchestrationGraph get(String planExecutionId) {
    if (planExecutionId == null) {
      return null;
    }

    try {
      byte[] data =
          dsl.select(ORCHESTRATION_GRAPH_CACHE.GRAPH_DATA)
              .from(ORCHESTRATION_GRAPH_CACHE)
              .where(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY.eq(planExecutionId))
              .and(ORCHESTRATION_GRAPH_CACHE.VALID_UNTIL.gt(OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)))
              .fetchOne(ORCHESTRATION_GRAPH_CACHE.GRAPH_DATA);

      return data != null ? (OrchestrationGraph) kryoSerializer.asInflatedObject(data) : null;
    } catch (Exception e) {
      log.error("Failed to fetch orchestration graph for planExecutionId {}", planExecutionId, e);
      return null;
    }
  }

  @Override
  public EntityWithAccountId getWithAccountId(String planExecutionId) {
    if (planExecutionId == null) {
      return null;
    }

    try {
      var record =
          dsl.select(ORCHESTRATION_GRAPH_CACHE.ACCOUNT_IDENTIFIER, ORCHESTRATION_GRAPH_CACHE.GRAPH_DATA)
              .from(ORCHESTRATION_GRAPH_CACHE)
              .where(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY.eq(planExecutionId))
              .and(ORCHESTRATION_GRAPH_CACHE.VALID_UNTIL.gt(OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)))
              .fetchOne();

      if (record == null) {
        return null;
      }

      String accountId = record.get(ORCHESTRATION_GRAPH_CACHE.ACCOUNT_IDENTIFIER);
      byte[] data = record.get(ORCHESTRATION_GRAPH_CACHE.GRAPH_DATA);
      OrchestrationGraph graph = data != null ? (OrchestrationGraph) kryoSerializer.asInflatedObject(data) : null;

      return graph != null ? EntityWithAccountId.builder().accountId(accountId).entity(graph).build() : null;
    } catch (Exception e) {
      log.error("Failed to fetch orchestration graph with account for {}", planExecutionId, e);
      return null;
    }
  }

  @Override
  public void delete(String planExecutionId) {
    if (planExecutionId == null) {
      return;
    }
    try {
      dsl.deleteFrom(ORCHESTRATION_GRAPH_CACHE)
          .where(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY.eq(planExecutionId))
          .execute();
    } catch (Exception e) {
      log.error("Failed to delete orchestration graph for planExecutionId {}", planExecutionId, e);
    }
  }

  @Override
  public void delete(List<OrchestrationGraph> graphs) {
    if (graphs == null || graphs.isEmpty()) {
      return;
    }

    List<String> keys = graphs.stream().map(OrchestrationGraph::getCacheKey).filter(k -> k != null).toList();

    if (!keys.isEmpty()) {
      dsl.deleteFrom(ORCHESTRATION_GRAPH_CACHE).where(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY.in(keys)).execute();
    }
  }

  @Override
  public void deleteUsingPattern(List<OrchestrationGraph> graphs) {
    delete(graphs);
  }

  @Override
  public List<String> findCacheKeysByPattern(String pattern) {
    try {
      return dsl.select(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY)
          .from(ORCHESTRATION_GRAPH_CACHE)
          .where(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY.like(pattern))
          .fetch(ORCHESTRATION_GRAPH_CACHE.CACHE_KEY);
    } catch (Exception e) {
      log.error("Failed to find cache keys by pattern {}", pattern, e);
      return List.of();
    }
  }

  @Override
  public EntityWithAccountId getFromSecondary(
      long algorithmId, long structureHash, String planExecutionId, String accountId) {
    return getWithAccountId(planExecutionId);
  }

  /**
   * Delete expired graphs based on valid_until timestamp.
   * Uses single-statement batch deletion with ctid subquery to minimize lock duration.
   * This is more efficient than fetching keys first as it:
   * - Uses single database round trip
   * - No application memory overhead for storing keys
   * - Uses ctid (physical row location) for fast row identification
   *
   * @param batchSize Maximum number of records to delete in one call
   * @return Number of records deleted
   */
  @Override
  public int deleteExpiredGraphs(int batchSize) {
    try {
      OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);

      // Use ctid (PostgreSQL internal row identifier) for efficient single-statement deletion
      // This avoids two round trips and doesn't load keys into application memory
      Field<Object> ctid = DSL.field("ctid");

      int deletedCount = dsl.deleteFrom(ORCHESTRATION_GRAPH_CACHE)
                             .where(ctid.in(dsl.select(ctid)
                                                .from(ORCHESTRATION_GRAPH_CACHE)
                                                .where(ORCHESTRATION_GRAPH_CACHE.VALID_UNTIL.lt(now))
                                                .limit(batchSize)))
                             .execute();

      if (deletedCount > 0) {
        log.info("Deleted {} expired graphs from orchestration_graph_cache", deletedCount);
      }
      return deletedCount;
    } catch (Exception e) {
      log.error("Failed to delete expired graphs from PostgreSQL", e);
      return 0;
    }
  }
}
