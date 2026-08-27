/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import static io.harness.beans.FeatureName.PIE_SIMPLIFY_LOG_BASE_KEY;
import static io.harness.graph.service.impl.GraphVertexFields.ACCOUNT_IDENTIFIER;
import static io.harness.graph.service.impl.GraphVertexFields.ADVISER_RESPONSE;
import static io.harness.graph.service.impl.GraphVertexFields.BASE_FQN;
import static io.harness.graph.service.impl.GraphVertexFields.CHILDREN_COUNT;
import static io.harness.graph.service.impl.GraphVertexFields.CREATED_AT;
import static io.harness.graph.service.impl.GraphVertexFields.CURRENT_LEVEL;
import static io.harness.graph.service.impl.GraphVertexFields.END_TS;
import static io.harness.graph.service.impl.GraphVertexFields.EXECUTABLE_RESPONSES;
import static io.harness.graph.service.impl.GraphVertexFields.EXECUTION_INPUT_CONFIGURED;
import static io.harness.graph.service.impl.GraphVertexFields.EXECUTION_MODE;
import static io.harness.graph.service.impl.GraphVertexFields.FAILURE_INFO;
import static io.harness.graph.service.impl.GraphVertexFields.HAS_BARRIER_CHILD;
import static io.harness.graph.service.impl.GraphVertexFields.IDENTIFIER;
import static io.harness.graph.service.impl.GraphVertexFields.INITIAL_WAIT_DURATION_MS;
import static io.harness.graph.service.impl.GraphVertexFields.INTERRUPT_HISTORIES;
import static io.harness.graph.service.impl.GraphVertexFields.LAST_UPDATED_AT;
import static io.harness.graph.service.impl.GraphVertexFields.LOG_BASE_KEY;
import static io.harness.graph.service.impl.GraphVertexFields.MODULE;
import static io.harness.graph.service.impl.GraphVertexFields.NAME;
import static io.harness.graph.service.impl.GraphVertexFields.NEXT_ID;
import static io.harness.graph.service.impl.GraphVertexFields.NODE_EXECUTIONS_INFO_ID;
import static io.harness.graph.service.impl.GraphVertexFields.NODE_EXECUTION_ID;
import static io.harness.graph.service.impl.GraphVertexFields.NODE_GROUP;
import static io.harness.graph.service.impl.GraphVertexFields.NODE_RUN_INFO;
import static io.harness.graph.service.impl.GraphVertexFields.OLD_RETRY;
import static io.harness.graph.service.impl.GraphVertexFields.PARENT_ID;
import static io.harness.graph.service.impl.GraphVertexFields.PLAN_EXECUTION_ID;
import static io.harness.graph.service.impl.GraphVertexFields.PLAN_NODE_ID;
import static io.harness.graph.service.impl.GraphVertexFields.PREVIOUS_ID;
import static io.harness.graph.service.impl.GraphVertexFields.PROGRESS_DATA;
import static io.harness.graph.service.impl.GraphVertexFields.RETRY_IDS;
import static io.harness.graph.service.impl.GraphVertexFields.RETRY_NODE_METADATA;
import static io.harness.graph.service.impl.GraphVertexFields.SKIP_TYPE;
import static io.harness.graph.service.impl.GraphVertexFields.START_TS;
import static io.harness.graph.service.impl.GraphVertexFields.STATUS;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_DETAILS;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_PARAMETERS;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_PARAMETERS_VERSION;
import static io.harness.graph.service.impl.GraphVertexFields.STEP_TYPE;
import static io.harness.graph.service.impl.GraphVertexFields.STRATEGY_METADATA;
import static io.harness.graph.service.impl.GraphVertexFields.STRATEGY_TYPE;
import static io.harness.graph.service.impl.GraphVertexFields.TABLE;
import static io.harness.graph.service.impl.GraphVertexFields.UNIT_PROGRESSES;
import static io.harness.graph.service.impl.GraphVertexFields.VALID_UNTIL;
import static io.harness.graph.service.impl.MongoTypeConverter.extractLongFromExtendedJson;
import static io.harness.graph.service.impl.MongoTypeConverter.toBoolean;
import static io.harness.graph.service.impl.MongoTypeConverter.toInteger;
import static io.harness.graph.service.impl.StrategyTypeExtractor.extract;

import static org.jooq.JSONB.jsonb;

import io.harness.DelegateInfoHelper;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationAdjacencyListInternal;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.dto.GraphDelegateSelectionLogParams;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.executions.steps.node.ExecutionNodeType;
import io.harness.graph.service.GraphBatchUpdateDTOs.ModuleInfoUpdate;
import io.harness.graph.service.GraphBatchUpdateDTOs.OutcomeUpdate;
import io.harness.graph.service.GraphBatchUpdateDTOs.StepDetailsUpdate;
import io.harness.graph.service.GraphBatchUpdateDTOs.VertexUpdate;
import io.harness.graph.service.GraphCDCService;
import io.harness.logging.UnitProgress;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.creation.lookup.intfc.NodeTypeLookupService;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.serializer.JsonUtils;
import io.harness.steps.StepUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.sql.SQLTransientException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

/**
 * PostgreSQL service for normalized graph storage.
 *
 * Uses individual columns for efficient queries and pagination.
 * Supports:
 * - Cursor-based pagination via child_index
 * - Efficient single-vertex updates
 * - Subtree queries via recursive CTE
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class GraphCDCServiceImpl implements GraphCDCService {
  private static final Duration DEFAULT_TTL = Duration.ofDays(30);
  private static final JSONB EMPTY_JSON_ARRAY = JSONB.valueOf("[]");
  private static final JSONB EMPTY_JSON_OBJECT = JSONB.valueOf("{}");

  /**
   * Matches positional array update keys, e.g. "executableResponses.0", which carry a single element
   * at a known index rather than the whole array.
   */
  private static final Pattern POSITIONAL_ARRAY_KEY_PATTERN = Pattern.compile("^([a-zA-Z][a-zA-Z0-9_]*)\\.(\\d+)$");

  /**
   * Array-valued fields that can be updated one element at a time, keyed by the field name as it
   * appears in the update. Elements are written at their index rather than replacing the column, so
   * previously stored elements survive.
   */
  private static final Map<String, PositionalArrayField> POSITIONAL_ARRAY_FIELDS =
      Map.of(NodeExecutionKeys.executableResponses,
          new PositionalArrayField(
              EXECUTABLE_RESPONSES, value -> ProtobufBinaryParser.parseToJsonb(value, ExecutableResponse::parseFrom)),
          NodeExecutionKeys.interruptHistories,
          new PositionalArrayField(INTERRUPT_HISTORIES, InterruptHistoriesParser::parseElementToJsonb));

  /**
   * How a single element of an array-valued column is parsed. Each element is stored as a JSON object,
   * consistent with how the corresponding full-array writers build the column (see
   * ProtobufBinaryParser#parseListToJsonb and InterruptHistoriesParser#parseToJsonb). Rows written before
   * this was the case may still hold elements as JSON strings; the readers in JsonbParserUtils handle both.
   */
  private record PositionalArrayField(Field<JSONB> column, Function<Object, JSONB> elementParser) {}

  /**
   * Retry policy for transient PostgreSQL/jOOQ errors.
   * Retries on: connection failures (08xxx), serialization failures (40001),
   * deadlocks (40P01), admin shutdown (57P01), and SQLTransientExceptions.
   * Does NOT retry: constraint violations (23xxx), syntax errors (42xxx).
   */
  private static final RetryPolicy<Object> PG_RETRY_POLICY =
      new RetryPolicy<>()
          .handleIf(GraphCDCServiceImpl::isTransientPostgresError)
          .withBackoff(1, 10, ChronoUnit.SECONDS)
          .withJitter(0.25)
          .withMaxAttempts(3)
          .onFailedAttempt(event
              -> log.warn("[NORMALIZED-PG] Retrying DB operation. Attempt No. {}", event.getAttemptCount(),
                  event.getLastFailure()))
          .onFailure(event
              -> log.error("[NORMALIZED-PG] DB operation failed after {} attempts", event.getAttemptCount(),
                  event.getFailure()));

  /**
   * Determines if an exception is a transient PostgreSQL error worth retrying.
   */
  private static boolean isTransientPostgresError(Throwable ex) {
    if (ex instanceof SQLTransientException) {
      return true;
    }
    if (ex instanceof DataAccessException dae) {
      String sqlState = dae.sqlState();
      if (sqlState != null) {
        // 40001 = serialization_failure, 40P01 = deadlock_detected
        // 08xxx = connection exceptions, 57P01 = admin_shutdown
        return sqlState.startsWith("40") || sqlState.startsWith("08") || "57P01".equals(sqlState);
      }
      // Also retry if the cause is a transient SQL exception
      return dae.getCause() instanceof SQLTransientException;
    }
    return false;
  }

  @Inject @Named("PipelineServiceDSLContext") private DSLContext dsl;
  @Inject private NodeTypeLookupService nodeTypeLookupService;
  @Inject private DelegateInfoHelper delegateInfoHelper;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  // ============================================
  // Write Operations (called by GraphCDCConsumer)
  // ============================================

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> getPipelineModuleInfo(String planExecutionId) {
    if (planExecutionId == null) {
      return null;
    }

    try {
      // Get the module_info from the PIPELINE_SECTION vertex
      Record1<JSONB> result = dsl.select(DSL.field("module_info", JSONB.class))
                                  .from(TABLE)
                                  .where(PLAN_EXECUTION_ID.eq(planExecutionId))
                                  .and(STEP_TYPE.eq("PIPELINE_SECTION"))
                                  .fetchOne();

      if (result == null) {
        log.debug("[NORMALIZED-PG] No PIPELINE_SECTION vertex found for plan: {}", planExecutionId);
        return null;
      }

      JSONB moduleInfoJsonb = result.value1();
      if (moduleInfoJsonb == null || moduleInfoJsonb.data() == null || moduleInfoJsonb.data().isEmpty()) {
        return null;
      }

      Map<String, Object> moduleInfo = JsonUtils.asObject(moduleInfoJsonb.data(), Map.class);
      log.debug("[NORMALIZED-PG] Retrieved pipeline module_info for plan: {}", planExecutionId);
      return moduleInfo;

    } catch (Exception e) {
      log.error("[NORMALIZED-PG] Failed to get pipeline module_info for plan: {}", planExecutionId, e);
      return null;
    }
  }

  /**
   * Deep merge moduleInfo following the same semantics as PlanExecutionModuleInfoUpdateEventHandler:
   * - Arrays: combine elements with set semantics (no duplicates)
   * - Maps: recursively merge
   * - Scalars: new value replaces existing
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> deepMergeModuleInfo(Map<String, Object> existing, Map<String, Object> updates) {
    Map<String, Object> result = new HashMap<>(existing);

    for (Map.Entry<String, Object> entry : updates.entrySet()) {
      String key = entry.getKey();
      Object newValue = entry.getValue();
      Object existingValue = result.get(key);

      if (newValue == null) {
        continue;
      }

      if (existingValue == null) {
        // No existing value, just set the new one
        result.put(key, newValue);
      } else if (newValue instanceof Map && existingValue instanceof Map) {
        // Both are maps - recursively merge
        result.put(key, deepMergeModuleInfo((Map<String, Object>) existingValue, (Map<String, Object>) newValue));
      } else if (newValue instanceof Collection && existingValue instanceof Collection) {
        // Both are collections - combine with set semantics (no duplicates)
        Set<Object> combined = new LinkedHashSet<>((Collection<?>) existingValue);
        combined.addAll((Collection<?>) newValue);
        result.put(key, new ArrayList<>(combined));
      } else {
        // Scalar or type mismatch - new value replaces existing
        result.put(key, newValue);
      }
    }

    return result;
  }

  @Override
  public void batchUpdateVertexFields(List<VertexUpdate> vertexUpdates) {
    if (vertexUpdates == null || vertexUpdates.isEmpty()) {
      return;
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime validUntil = now.plus(DEFAULT_TTL);
    long currentTime = System.currentTimeMillis();

    // Sort by nodeExecutionId to acquire row locks in consistent order, preventing deadlocks
    // when concurrent transactions upsert overlapping sets of rows
    var queries =
        vertexUpdates.stream()
            .filter(update -> update.getNodeExecutionId() != null && update.getUpdatedFields() != null)
            .sorted(Comparator.comparing(VertexUpdate::getNodeExecutionId))
            .map(update -> {
              Map<Field<?>, Object> insertValues = new HashMap<>();
              Map<Field<?>, Object> updateValues = new HashMap<>();

              // Required fields for INSERT
              insertValues.put(NODE_EXECUTION_ID, update.getNodeExecutionId());
              if (update.getPlanExecutionId() != null) {
                insertValues.put(PLAN_EXECUTION_ID, update.getPlanExecutionId());
              }
              if (update.getAccountIdentifier() != null) {
                insertValues.put(ACCOUNT_IDENTIFIER, update.getAccountIdentifier());
              }

              // Handle created_at - only set on INSERT
              Object createdAtValue = update.getUpdatedFields().get("createdAt");
              if (createdAtValue != null) {
                Long createdAt = extractLongFromExtendedJson(createdAtValue);
                if (createdAt != null) {
                  insertValues.put(CREATED_AT, createdAt);
                }
              }

              // Common fields — LAST_UPDATED_AT default only for INSERT (new rows).
              // For UPDATE (existing rows), only set via mapFieldToColumn when the CDC event
              // actually includes lastUpdatedAt, to avoid overwriting a correct value with
              // System.currentTimeMillis().
              insertValues.put(LAST_UPDATED_AT, currentTime);
              insertValues.put(VALID_UNTIL, validUntil);
              updateValues.put(VALID_UNTIL, validUntil);

              // Map all fields
              for (Map.Entry<String, Object> entry : update.getUpdatedFields().entrySet()) {
                mapFieldToColumn(entry.getKey(), entry.getValue(), insertValues);
                mapFieldToColumn(entry.getKey(), entry.getValue(), updateValues);
              }

              applyPositionalArrayUpdates(update.getUpdatedFields(), insertValues, updateValues);

              return dsl.insertInto(TABLE).set(insertValues).onConflict(NODE_EXECUTION_ID).doUpdate().set(updateValues);
            })
            .toList();

    // Retry only the DB execution — propagates to consumer after exhausting retries
    Failsafe.with(PG_RETRY_POLICY).run(() -> dsl.batch(queries).execute());
    log.debug("[NORMALIZED-PG] Batch upserted {} vertices", vertexUpdates.size());
  }

  @Override
  public void batchAppendOutcomes(List<OutcomeUpdate> outcomeUpdates) {
    if (outcomeUpdates == null || outcomeUpdates.isEmpty()) {
      return;
    }

    // Sort by lookup key to enforce a consistent lock order across concurrent batches and avoid deadlocks.
    List<OutcomeUpdate> sortedUpdates =
        outcomeUpdates.stream()
            .filter(u -> u.getOutcomeJson() != null)
            .sorted(Comparator.comparing(
                u -> u.isCreate() ? String.valueOf(u.getNodeExecutionId()) : String.valueOf(u.getDocumentId())))
            .collect(Collectors.toList());

    List<org.jooq.Query> queries = new ArrayList<>();

    for (OutcomeUpdate update : sortedUpdates) {
      if (update.isCreate()) {
        // CREATE: use nodeExecutionId to find vertex, store outcome keyed by outcomeName
        // outcome_documents format: {outcomeName: outcomeData} matching parseOutcomeDocuments expectations
        if (update.getNodeExecutionId() == null || update.getOutcomeName() == null) {
          continue;
        }

        queries.add(dsl.query("UPDATE graph_vertex SET "
                + "outcome_documents = jsonb_set(COALESCE(outcome_documents, '{}'::jsonb), ARRAY[?], ?::jsonb), "
                + "outcome_instance_ids = array_append(COALESCE(outcome_instance_ids, '{}'), ?), "
                + "last_updated_at = ? "
                + "WHERE node_execution_id = ?",
            update.getOutcomeName(), update.getOutcomeJson(), update.getDocumentId(), System.currentTimeMillis(),
            update.getNodeExecutionId()));
      } else {
        // UPDATE: use documentId to find vertex via outcome_instance_ids GIN index
        // If outcomeName is available, update that key directly; otherwise merge into existing
        if (update.getDocumentId() == null) {
          continue;
        }
        if (update.getOutcomeName() != null) {
          queries.add(dsl.query("UPDATE graph_vertex SET "
                  + "outcome_documents = jsonb_set(COALESCE(outcome_documents, '{}'::jsonb), ARRAY[?], ?::jsonb), "
                  + "last_updated_at = ? "
                  + "WHERE outcome_instance_ids @> ARRAY[?]::text[]",
              update.getOutcomeName(), update.getOutcomeJson(), System.currentTimeMillis(), update.getDocumentId()));
        } else {
          // outcomeName not in delta — merge outcome data into all existing outcome keys
          // This is a rare edge case; log and skip to avoid corrupting the structure
          log.warn(
              "[NORMALIZED-PG] Outcome UPDATE without outcomeName for documentId={}, skipping", update.getDocumentId());
        }
      }
    }

    if (!queries.isEmpty()) {
      // Retry only the DB execution — propagates to consumer after exhausting retries
      Failsafe.with(PG_RETRY_POLICY).run(() -> dsl.batch(queries).execute());
      log.debug("[NORMALIZED-PG] Batch processed {} outcome operations", queries.size());
    }
  }

  @Override
  public void batchUpdateStepDetails(List<StepDetailsUpdate> stepDetailsUpdates) {
    if (stepDetailsUpdates == null || stepDetailsUpdates.isEmpty()) {
      return;
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime validUntil = now.plus(DEFAULT_TTL);

    // Sort by nodeExecutionId (CREATE) or documentId (UPDATE) to enforce consistent lock order
    List<StepDetailsUpdate> sortedUpdates =
        stepDetailsUpdates.stream()
            .filter(u
                -> u.getStepDetailsJson() != null
                    || (u.getStepDetailsElementsByName() != null && !u.getStepDetailsElementsByName().isEmpty())
                    || u.getStrategyMetadataJson() != null || u.getRetryNodeMetadataJson() != null)
            .filter(u -> u.getNodeExecutionId() != null || u.getDocumentId() != null)
            .sorted(
                Comparator.comparing(u -> u.getNodeExecutionId() != null ? u.getNodeExecutionId() : u.getDocumentId()))
            .collect(Collectors.toList());

    // Run CREATE upserts before UPDATEs so a same-batch CREATE+UPDATE for the same doc
    // finds the row already present when the UPDATE's WHERE clause runs.
    List<org.jooq.Query> createQueries = new ArrayList<>();
    List<org.jooq.Query> updateQueries = new ArrayList<>();

    for (StepDetailsUpdate update : sortedUpdates) {
      long lastUpdatedAt = System.currentTimeMillis();

      if (update.isCreate()) {
        if (update.getNodeExecutionId() == null) {
          continue;
        }

        Map<Field<?>, Object> insertValues = new HashMap<>();
        Map<Field<?>, Object> updateValues = new HashMap<>();

        insertValues.put(NODE_EXECUTION_ID, update.getNodeExecutionId());
        insertValues.put(LAST_UPDATED_AT, lastUpdatedAt);
        insertValues.put(VALID_UNTIL, validUntil);

        updateValues.put(LAST_UPDATED_AT, lastUpdatedAt);
        updateValues.put(VALID_UNTIL, validUntil);

        if (isNonEmptyStepDetailsJson(update.getStepDetailsJson())) {
          JSONB stepDetailsJsonb = jsonb(update.getStepDetailsJson());
          insertValues.put(STEP_DETAILS, stepDetailsJsonb);
          updateValues.put(STEP_DETAILS, stepDetailsJsonb);
        }
        if (update.getStrategyMetadataJson() != null) {
          JSONB strategyMetadataJsonb = jsonb(update.getStrategyMetadataJson());
          insertValues.put(STRATEGY_METADATA, strategyMetadataJsonb);
          updateValues.put(STRATEGY_METADATA, strategyMetadataJsonb);
        }
        if (update.getRetryNodeMetadataJson() != null) {
          JSONB retryNodeMetadataJsonb = jsonb(update.getRetryNodeMetadataJson());
          insertValues.put(RETRY_NODE_METADATA, retryNodeMetadataJsonb);
          updateValues.put(RETRY_NODE_METADATA, retryNodeMetadataJsonb);
        }
        if (update.getDocumentId() != null) {
          insertValues.put(NODE_EXECUTIONS_INFO_ID, update.getDocumentId());
          updateValues.put(NODE_EXECUTIONS_INFO_ID, update.getDocumentId());
        }

        createQueries.add(
            dsl.insertInto(TABLE).set(insertValues).onConflict(NODE_EXECUTION_ID).doUpdate().set(updateValues));

      } else {
        if (update.getDocumentId() == null) {
          continue;
        }

        Map<Field<?>, Object> updateValues = new HashMap<>();
        updateValues.put(LAST_UPDATED_AT, lastUpdatedAt);
        updateValues.put(VALID_UNTIL, validUntil);

        if (isNonEmptyStepDetailsJson(update.getStepDetailsJson())) {
          updateValues.put(STEP_DETAILS, jsonb(update.getStepDetailsJson()));
        }
        applyStepDetailsPositionalUpdate(update.getStepDetailsElementsByName(), updateValues);
        if (update.getStrategyMetadataJson() != null) {
          updateValues.put(STRATEGY_METADATA, jsonb(update.getStrategyMetadataJson()));
        }
        if (update.getRetryNodeMetadataJson() != null) {
          updateValues.put(RETRY_NODE_METADATA, jsonb(update.getRetryNodeMetadataJson()));
        }

        updateQueries.add(
            dsl.update(TABLE).set(updateValues).where(NODE_EXECUTIONS_INFO_ID.eq(update.getDocumentId())));
      }
    }

    int totalQueries = createQueries.size() + updateQueries.size();
    if (totalQueries > 0) {
      Failsafe.with(PG_RETRY_POLICY).run(() -> {
        if (!createQueries.isEmpty()) {
          dsl.batch(createQueries).execute();
        }
        if (!updateQueries.isEmpty()) {
          dsl.batch(updateQueries).execute();
        }
      });
      log.debug("[NORMALIZED-PG] Batch upserted step details for {} entries ({} creates, {} updates)", totalQueries,
          createQueries.size(), updateQueries.size());
    }
  }

  @Override
  public void batchUpdateModuleInfo(List<ModuleInfoUpdate> moduleInfoUpdates) {
    if (moduleInfoUpdates == null || moduleInfoUpdates.isEmpty()) {
      return;
    }

    // Retry the entire transaction block (SELECT FOR UPDATE + UPDATE must be atomic)
    // Propagates to consumer after exhausting retries
    Failsafe.with(PG_RETRY_POLICY).run(() -> {
      // Process each update in a transaction with SELECT FOR UPDATE to ensure
      // deep merge correctness. Without this, concurrent updates to the same module key
      // (e.g., two events both updating cd.serviceInfo with different nested fields)
      // would lose data because JSONB || only does shallow merge.
      for (ModuleInfoUpdate update : moduleInfoUpdates) {
        if (update.getModuleInfo() == null || update.getModuleInfo().isEmpty()) {
          continue;
        }

        dsl.transaction(configuration -> {
          DSLContext txDsl = DSL.using(configuration);

          // Find the target row and lock it
          Record1<JSONB> result = selectModuleInfoForUpdate(txDsl, update);
          if (result == null) {
            log.debug("[NORMALIZED-PG] No vertex found for module info update, documentId: {}, stageUuid: {}",
                update.getDocumentId(), update.getStageUuid());
            return;
          }

          // Deep merge new moduleInfo into existing
          Map<String, Object> existingModuleInfo = parseModuleInfoJsonb(result.value1());
          Map<String, Object> mergedModuleInfo = deepMergeModuleInfo(existingModuleInfo, update.getModuleInfo());
          String mergedJson = JsonUtils.asJson(mergedModuleInfo);
          long now = System.currentTimeMillis();

          // Build and execute the update
          updateModuleInfoRow(txDsl, update, mergedJson, now);
        });
      }

      log.debug("[NORMALIZED-PG] Batch updated module info for {} entries", moduleInfoUpdates.size());
    });
  }

  @Override
  public void markBarrierParents(List<String> stageNodeExecutionIds) {
    if (stageNodeExecutionIds == null || stageNodeExecutionIds.isEmpty()) {
      return;
    }

    // Deduplicate stage IDs (multiple barriers can be in same stage)
    List<String> uniqueStageIds = stageNodeExecutionIds.stream().distinct().toList();

    Failsafe.with(PG_RETRY_POLICY).run(() -> {
      // Mark the stages - stage IDs are now extracted directly from ambiance.levels in CDC consumer
      // This eliminates the need to query the DB to walk up the parent chain
      int updated =
          dsl.update(TABLE).set(HAS_BARRIER_CHILD, true).where(NODE_EXECUTION_ID.in(uniqueStageIds)).execute();

      log.debug("[NORMALIZED-PG] Marked {} parent stages as having barrier children", updated);
    });
  }

  /**
   * SELECT module_info FOR UPDATE based on the update type (create vs update, pipeline vs stage).
   */
  private Record1<JSONB> selectModuleInfoForUpdate(DSLContext txDsl, ModuleInfoUpdate update) {
    if (update.isCreate()) {
      if (update.isPipelineLevel()) {
        return txDsl.select(DSL.field("module_info", JSONB.class))
            .from(TABLE)
            .where(PLAN_EXECUTION_ID.eq(update.getPlanExecutionId()))
            .and(STEP_TYPE.eq("PIPELINE_SECTION"))
            .forUpdate()
            .fetchOne();
      } else {
        // stageUuid can be planNodeId or nodeExecutionId
        Record1<JSONB> result = txDsl.select(DSL.field("module_info", JSONB.class))
                                    .from(TABLE)
                                    .where(PLAN_EXECUTION_ID.eq(update.getPlanExecutionId()))
                                    .and(PLAN_NODE_ID.eq(update.getStageUuid()))
                                    .forUpdate()
                                    .fetchOne();
        if (result == null) {
          result = txDsl.select(DSL.field("module_info", JSONB.class))
                       .from(TABLE)
                       .where(PLAN_EXECUTION_ID.eq(update.getPlanExecutionId()))
                       .and(NODE_EXECUTION_ID.eq(update.getStageUuid()))
                       .forUpdate()
                       .fetchOne();
        }
        return result;
      }
    } else {
      // UPDATE event — find by documentId in graph_update_info_ids array
      return txDsl.select(DSL.field("module_info", JSONB.class))
          .from(TABLE)
          .where(DSL.condition("graph_update_info_ids @> ARRAY[?]::text[]", update.getDocumentId()))
          .forUpdate()
          .fetchOne();
    }
  }

  /**
   * Write the merged module_info back, and track the documentId on CREATE events.
   */
  private void updateModuleInfoRow(DSLContext txDsl, ModuleInfoUpdate update, String mergedJson, long now) {
    if (update.isCreate()) {
      if (update.isPipelineLevel()) {
        if (update.getDocumentId() != null) {
          txDsl.execute("UPDATE graph_vertex SET module_info = ?::jsonb, "
                  + "graph_update_info_ids = array_append(COALESCE(graph_update_info_ids, '{}'), ?), "
                  + "last_updated_at = ? WHERE plan_execution_id = ? AND step_type = 'PIPELINE_SECTION'",
              mergedJson, update.getDocumentId(), now, update.getPlanExecutionId());
        } else {
          txDsl.execute("UPDATE graph_vertex SET module_info = ?::jsonb, last_updated_at = ? "
                  + "WHERE plan_execution_id = ? AND step_type = 'PIPELINE_SECTION'",
              mergedJson, now, update.getPlanExecutionId());
        }
      } else {
        if (update.getDocumentId() != null) {
          txDsl.execute("UPDATE graph_vertex SET module_info = ?::jsonb, "
                  + "graph_update_info_ids = array_append(COALESCE(graph_update_info_ids, '{}'), ?), "
                  + "last_updated_at = ? WHERE plan_execution_id = ? AND (plan_node_id = ? OR node_execution_id = ?)",
              mergedJson, update.getDocumentId(), now, update.getPlanExecutionId(), update.getStageUuid(),
              update.getStageUuid());
        } else {
          txDsl.execute("UPDATE graph_vertex SET module_info = ?::jsonb, last_updated_at = ? "
                  + "WHERE plan_execution_id = ? AND (plan_node_id = ? OR node_execution_id = ?)",
              mergedJson, now, update.getPlanExecutionId(), update.getStageUuid(), update.getStageUuid());
        }
      }
    } else {
      txDsl.execute("UPDATE graph_vertex SET module_info = ?::jsonb, last_updated_at = ? "
              + "WHERE graph_update_info_ids @> ARRAY[?]::text[]",
          mergedJson, now, update.getDocumentId());
    }
  }

  private Map<String, Object> parseModuleInfoJsonb(JSONB jsonb) {
    if (jsonb == null || jsonb.data() == null || jsonb.data().isEmpty() || "{}".equals(jsonb.data())) {
      return new HashMap<>();
    }
    try {
      return JsonUtils.asMap(jsonb.data());
    } catch (Exception e) {
      log.warn("[NORMALIZED-PG] Failed to parse module_info JSONB: {}", e.getMessage());
      return new HashMap<>();
    }
  }

  /**
   * Handle updates that carry a single array element at a positional key (e.g. "executableResponses.0")
   * instead of the whole array. The plain column-replace path in mapFieldToColumn matches on exact field
   * names, so these keys reach neither map and the element would be lost.
   *
   * <p>For an existing row the element is written at its index with jsonb_set, which keeps the elements
   * already stored and stays correct when the same update is delivered more than once. For a new row
   * there is nothing to preserve, so the elements are written as a fresh array.
   */
  private void applyPositionalArrayUpdates(
      Map<String, Object> updatedFields, Map<Field<?>, Object> insertValues, Map<Field<?>, Object> updateValues) {
    Map<String, Map<Integer, Object>> elementsByFieldAndIndex = new HashMap<>();
    for (Map.Entry<String, Object> entry : updatedFields.entrySet()) {
      Matcher matcher = POSITIONAL_ARRAY_KEY_PATTERN.matcher(entry.getKey());
      if (matcher.matches() && POSITIONAL_ARRAY_FIELDS.containsKey(matcher.group(1))) {
        elementsByFieldAndIndex.computeIfAbsent(matcher.group(1), field -> new TreeMap<>())
            .put(Integer.parseInt(matcher.group(2)), entry.getValue());
      }
    }

    elementsByFieldAndIndex.forEach((fieldName, elementsByIndex) -> {
      PositionalArrayField field = POSITIONAL_ARRAY_FIELDS.get(fieldName);

      // Parsed elements in index order, skipping any that fail to parse so one bad element does not
      // discard the rest.
      Map<Integer, JSONB> parsedByIndex = new TreeMap<>();
      elementsByIndex.forEach((index, element) -> {
        JSONB parsed = field.elementParser().apply(element);
        if (parsed == null) {
          log.warn("[NORMALIZED-PG] Skipping unparseable element for field path: {}.{}", fieldName, index);
          return;
        }
        parsedByIndex.put(index, parsed);
      });
      if (parsedByIndex.isEmpty()) {
        return;
      }

      // The conflict clause has both the target row and the proposed "excluded" row in scope, so reading
      // the stored array has to name the table explicitly. The assignment target stays unqualified.
      Field<JSONB> storedArray = DSL.field(DSL.name(TABLE.getName(), field.column().getName()), JSONB.class);
      Field<JSONB> existingArray = DSL.coalesce(storedArray, DSL.inline(EMPTY_JSON_ARRAY, JSONB.class));
      for (Map.Entry<Integer, JSONB> element : parsedByIndex.entrySet()) {
        existingArray = DSL.field("jsonb_set({0}, ARRAY[{1}::text], {2}, true)", JSONB.class, existingArray,
            DSL.inline(element.getKey()), DSL.val(element.getValue()));
      }
      updateValues.put(field.column(), existingArray);
      insertValues.put(field.column(), buildJsonArray(parsedByIndex.values()));
    });
  }

  /**
   * Merge single-element positional appends to nodeExecutionDetailsInfoList (extracted upstream by
   * the consumer, keyed by each element's "name" field) into the existing step_details JSONB
   * object via jsonb_set. step_details is stored as a name-keyed JSON object (matching
   * JsonbParserUtils#parseStepDetails' "direct map format" branch, and the shape the CREATE/full-array
   * update path already writes) -- NOT a JSON array. Using an integer-indexed jsonb_set here would
   * silently corrupt the column into e.g. {"0": {...}} whenever the stored value is (or starts as)
   * a JSON object rather than an array, since jsonb_set interprets a path segment as an array index
   * only when the target at that level is itself an array; against an object it is a literal key.
   */
  private void applyStepDetailsPositionalUpdate(
      Map<String, String> elementsByName, Map<Field<?>, Object> updateValues) {
    if (elementsByName == null || elementsByName.isEmpty()) {
      return;
    }
    Field<JSONB> existingObject = DSL.coalesce(STEP_DETAILS, DSL.inline(EMPTY_JSON_OBJECT, JSONB.class));
    for (Map.Entry<String, String> element : new TreeMap<>(elementsByName).entrySet()) {
      existingObject = DSL.field("jsonb_set({0}, ARRAY[{1}], {2}, true)", JSONB.class, existingObject,
          DSL.inline(element.getKey()), DSL.val(JSONB.valueOf(element.getValue())));
    }
    updateValues.put(STEP_DETAILS, existingObject);
  }

  /**
   * Build a JSON array column value from already-parsed elements.
   */
  private JSONB buildJsonArray(Collection<JSONB> elements) {
    List<Object> arrayElements = new ArrayList<>();
    for (JSONB element : elements) {
      arrayElements.add(JsonUtils.asObject(element.data(), Object.class));
    }
    return JSONB.valueOf(JsonUtils.asJson(arrayElements));
  }

  /**
   * Map a MongoDB field path to the corresponding PostgreSQL column and value.
   * MongoDB uses dot notation for nested fields, e.g., "ambiance.planExecutionId".
   */
  private void mapFieldToColumn(String fieldPath, Object value, Map<Field<?>, Object> updates) {
    // Handle top-level fields using NodeExecutionKeys constants
    switch (fieldPath) {
      case NodeExecutionKeys.status:
        if (value instanceof String) {
          updates.put(STATUS, value);
        } else if (value != null) {
          updates.put(STATUS, value.toString());
        }
        break;
      case NodeExecutionKeys.startTs:
        updates.put(START_TS, extractLongFromExtendedJson(value));
        break;
      case NodeExecutionKeys.endTs:
        updates.put(END_TS, extractLongFromExtendedJson(value));
        break;
      case NodeExecutionKeys.lastUpdatedAt:
        Long mongoLastUpdatedAt = extractLongFromExtendedJson(value);
        if (mongoLastUpdatedAt != null) {
          updates.put(LAST_UPDATED_AT, mongoLastUpdatedAt);
        }
        break;
      case NodeExecutionKeys.name:
        updates.put(NAME, value != null ? value.toString() : null);
        break;
      case NodeExecutionKeys.identifier:
        updates.put(IDENTIFIER, value != null ? value.toString() : null);
        break;
      case NodeExecutionKeys.nodeId:
        updates.put(PLAN_NODE_ID, value != null ? value.toString() : null);
        break;
      case NodeExecutionKeys.parentId:
        updates.put(PARENT_ID, value != null ? value.toString() : null);
        break;
      case NodeExecutionKeys.previousId:
        updates.put(PREVIOUS_ID, value != null ? value.toString() : null);
        break;
      case NodeExecutionKeys.nextId:
        updates.put(NEXT_ID, value != null ? value.toString() : null);
        break;
      case NodeExecutionKeys.mode:
        if (value instanceof String) {
          updates.put(EXECUTION_MODE, value);
        } else if (value != null) {
          updates.put(EXECUTION_MODE, value.toString());
        }
        break;
      case NodeExecutionKeys.skipGraphType:
        if (value instanceof String) {
          updates.put(SKIP_TYPE, value);
        } else if (value != null) {
          updates.put(SKIP_TYPE, value.toString());
        }
        break;
      case NodeExecutionKeys.executionInputConfigured:
        if (value instanceof Boolean) {
          updates.put(EXECUTION_INPUT_CONFIGURED, value);
        }
        break;
      case NodeExecutionKeys.initialWaitDuration:
        Long duration = extractLongFromExtendedJson(value);
        if (duration != null) {
          updates.put(INITIAL_WAIT_DURATION_MS, duration);
        }
        break;
      case NodeExecutionKeys.resolvedParams:
        updates.put(STEP_PARAMETERS, toJsonb(value));
        // Also extract strategyType for STRATEGY nodes
        extractAndStoreStrategyType(value, updates);
        break;
      case NodeExecutionKeys.resolvedParamsVersion:
        updates.put(STEP_PARAMETERS_VERSION, toInteger(value));
        break;
      case NodeExecutionKeys.failureInfo:
        // Parse FailureInfo protobuf from Kryo-serialized binary format
        updates.put(FAILURE_INFO, ProtobufBinaryParser.parseToJsonb(value, FailureInfo::parseFrom));
        break;
      case NodeExecutionKeys.nodeRunInfo:
        // Parse NodeRunInfo protobuf from Kryo-serialized binary format
        updates.put(NODE_RUN_INFO, ProtobufBinaryParser.parseToJsonb(value, NodeRunInfo::parseFrom));
        break;
      case NodeExecutionKeys.progressData:
        // progressData is an OrchestrationMap (Java class, not protobuf) - use regular JSON
        updates.put(PROGRESS_DATA, toJsonb(value));
        break;
      case NodeExecutionKeys.unitProgresses:
        // Parse UnitProgress protobuf list from Kryo-serialized binary format
        updates.put(UNIT_PROGRESSES, ProtobufBinaryParser.parseListToJsonb(value, UnitProgress::parseFrom));
        break;
      case NodeExecutionKeys.executableResponses:
        // Parse ExecutableResponse protobuf list from Kryo-serialized binary format
        updates.put(EXECUTABLE_RESPONSES, ProtobufBinaryParser.parseListToJsonb(value, ExecutableResponse::parseFrom));
        break;
      case NodeExecutionKeys.interruptHistories:
        // Parse InterruptEffect list (Java class with protobuf InterruptConfig inside)
        updates.put(INTERRUPT_HISTORIES, InterruptHistoriesParser.parseToJsonb(value));
        break;
      case NodeExecutionKeys.adviserResponse:
        // Parse AdviserResponse protobuf from Kryo-serialized binary format
        updates.put(ADVISER_RESPONSE, ProtobufBinaryParser.parseToJsonb(value, AdviserResponse::parseFrom));
        break;
      case NodeExecutionKeys.retryIds:
        if (value instanceof List) {
          @SuppressWarnings("unchecked") List<String> retryIds = (List<String>) value;
          updates.put(RETRY_IDS, retryIds.toArray(new String[0]));
        }
        break;
      case NodeExecutionKeys.ambiance:
        // Handle full ambiance object - extract nested fields using AmbianceParser
        AmbianceParser.parse(value).ifPresent(result -> {
          if (result.hasAccountId()) {
            updates.put(ACCOUNT_IDENTIFIER, result.getAccountId());
          }
          if (result.hasPlanExecutionId() && !updates.containsKey(PLAN_EXECUTION_ID)) {
            updates.put(PLAN_EXECUTION_ID, result.getPlanExecutionId());
          }
          // Derive baseFqn and currentLevel from ambiance levels (matches legacy GraphStatusUpdateHelper behavior)
          if (result.getAmbiance() != null && !result.getAmbiance().getLevelsList().isEmpty()) {
            List<Level> levels = result.getAmbiance().getLevelsList();
            String fqn = AmbianceUtils.getFQNUsingLevels(levels);
            if (fqn != null && !fqn.isEmpty()) {
              updates.put(BASE_FQN, fqn);
            }
            putCurrentLevel(updates, levels.get(levels.size() - 1));
          }
          // Derive logBaseKey from ambiance (fallback when executionContext is not available)
          if (result.getAmbiance() != null && !updates.containsKey(LOG_BASE_KEY)) {
            List<String> logKeys = StepUtils.generateLogKeys(result.getAmbiance(), null);
            if (logKeys != null && !logKeys.isEmpty()) {
              updates.put(LOG_BASE_KEY, logKeys.get(0));
            }
          }
        });
        break;
      case NodeExecutionKeys.executionContext:
        // Handle ExecutionContext object. In newer executions, ambiance is empty and levels live
        // on executionContext, so derive baseFqn/currentLevel from here when ambiance didn't supply them.
        ExecutionContextParser.parse(value).ifPresent(result -> {
          if (!result.hasExecutionContext()) {
            return;
          }
          if (result.hasAccountId()) {
            // Generate log keys using StepUtils with ExecutionContext, similar to addVertex in GraphStatusUpdateHelper
            // Hard-coded simplifyLogBaseKey = true as per requirement
            List<String> logKeys = StepUtils.generateLogKeys(result.getExecutionContext(), null,
                pmsFeatureFlagHelper.isEnabled(result.getAccountId(), PIE_SIMPLIFY_LOG_BASE_KEY));
            if (logKeys != null && !logKeys.isEmpty()) {
              updates.put(LOG_BASE_KEY, logKeys.get(0));
            }
          }
          List<Level> levels = result.getExecutionContext().getLevelsList();
          if (!levels.isEmpty()) {
            String fqn = AmbianceUtils.getFQNUsingLevels(levels);
            if (fqn != null && !fqn.isEmpty()) {
              updates.put(BASE_FQN, fqn);
            }
            putCurrentLevel(updates, levels.get(levels.size() - 1));
          }
        });
        break;
      case NodeExecutionKeys.module:
        if (value instanceof String) {
          updates.put(MODULE, value);
        } else if (value != null) {
          updates.put(MODULE, value.toString());
        }
        break;
      case NodeExecutionKeys.stepType:
        // stepType can come as a Map with "type" field or as a nested structure
        if (value instanceof Map) {
          @SuppressWarnings("unchecked") Map<String, Object> stepTypeMap = (Map<String, Object>) value;
          Object type = stepTypeMap.get("type");
          if (type != null) {
            updates.put(STEP_TYPE, type.toString());
          }
        } else if (value instanceof String) {
          updates.put(STEP_TYPE, value);
        }
        break;
      case NodeExecutionKeys.oldRetry:
        Boolean oldRetry = toBoolean(value);
        if (oldRetry != null) {
          updates.put(OLD_RETRY, oldRetry);
        }
        break;
      case NodeExecutionKeys.group:
        // NodeExecution.group maps to node_group (StepCategory: STAGE, STEP, STRATEGY, etc.)
        if (value instanceof String) {
          updates.put(NODE_GROUP, value);
        } else if (value != null) {
          updates.put(NODE_GROUP, value.toString());
        }
        break;
      case NodeExecutionKeys.createdAt:
        updates.put(CREATED_AT, extractLongFromExtendedJson(value));
        break;
      case NodeExecutionKeys.childrenCount:
        Long childrenCount = extractLongFromExtendedJson(value);
        if (childrenCount != null) {
          updates.put(CHILDREN_COUNT, childrenCount);
        }
        break;
      default:
        // Log for debugging but don't fail
        log.trace("[NORMALIZED-PG] Unmapped field path: {}", fieldPath);
        break;
    }
  }

  /**
   * Extract strategyType from resolvedParams and store it in the strategy_type column.
   * This is needed for STRATEGY nodes to correctly populate nodeType in layoutNodeMap.
   */
  private void extractAndStoreStrategyType(Object resolvedParams, Map<Field<?>, Object> updates) {
    extract(resolvedParams).ifPresent(strategyType -> updates.put(STRATEGY_TYPE, strategyType));
  }

  /**
   * Serialize a Level proto to JSONB using JsonFormat (matches GraphVertexMapper#parseProto on read side).
   */
  private void putCurrentLevel(Map<Field<?>, Object> updates, Level level) {
    if (level == null) {
      return;
    }
    try {
      String json = com.google.protobuf.util.JsonFormat.printer().omittingInsignificantWhitespace().print(level);
      updates.put(CURRENT_LEVEL, JSONB.valueOf(json));
    } catch (Exception e) {
      log.warn("[NORMALIZED-PG] Failed to serialize currentLevel: {}", e.getMessage());
    }
  }

  // ============================================
  // Read Operations
  // ============================================

  @Override
  public Optional<OrchestrationGraph> getOrchestrationGraph(String planExecutionId) {
    if (planExecutionId == null) {
      return Optional.empty();
    }

    try {
      // Fetch all vertices for this plan execution, excluding old retry nodes
      Result<Record> vertexRecords = dsl.select()
                                         .from(TABLE)
                                         .where(PLAN_EXECUTION_ID.eq(planExecutionId))
                                         .and(OLD_RETRY.isNull().or(OLD_RETRY.eq(false)))
                                         .fetch();

      if (vertexRecords.isEmpty()) {
        return Optional.empty();
      }

      // Reconstruct the graph from vertices only
      return Optional.of(reconstructGraph(planExecutionId, vertexRecords, null));

    } catch (Exception e) {
      log.error("[NORMALIZED-PG] Failed to get orchestration graph: {}", planExecutionId, e);
      return Optional.empty();
    }
  }

  @Override
  public List<GraphVertex> getChildrenPaginated(
      String planExecutionId, String parentId, long cursorCreatedAt, int limit) {
    if (planExecutionId == null || parentId == null) {
      return Collections.emptyList();
    }

    try {
      Result<Record> records = dsl.select()
                                   .from(TABLE)
                                   .where(PLAN_EXECUTION_ID.eq(planExecutionId))
                                   .and(PARENT_ID.eq(parentId))
                                   .and(CREATED_AT.gt(cursorCreatedAt))
                                   .and(OLD_RETRY.isNull().or(OLD_RETRY.eq(false)))
                                   .orderBy(CREATED_AT.asc())
                                   .limit(limit)
                                   .fetch();

      return records.stream().map(this::recordToGraphVertex).collect(Collectors.toList());

    } catch (Exception e) {
      log.error("[NORMALIZED-PG] Failed to get paginated children for parent: {}", parentId, e);
      return Collections.emptyList();
    }
  }

  @Override
  public int countChildren(String planExecutionId, String parentId) {
    if (planExecutionId == null || parentId == null) {
      return 0;
    }

    try {
      return dsl.selectCount()
          .from(TABLE)
          .where(PLAN_EXECUTION_ID.eq(planExecutionId))
          .and(PARENT_ID.eq(parentId))
          .and(OLD_RETRY.isNull().or(OLD_RETRY.eq(false)))
          .fetchOne(0, Integer.class);

    } catch (Exception e) {
      log.error("[NORMALIZED-PG] Failed to count children for parent: {}", parentId, e);
      return 0;
    }
  }

  @Override
  public Optional<OrchestrationGraph> getPartialOrchestrationGraph(String planExecutionId, String rootNodeId) {
    if (planExecutionId == null || rootNodeId == null) {
      return Optional.empty();
    }

    try {
      // Use recursive CTE to fetch only the subtree vertices, no depth limit
      String sql = """
          WITH RECURSIVE subtree AS (
              SELECT *
              FROM graph_vertex
              WHERE plan_execution_id = ? AND node_execution_id = ?
                AND (old_retry IS NULL OR old_retry = false)

              UNION ALL

              SELECT v.*
              FROM graph_vertex v
              INNER JOIN subtree s ON v.parent_id = s.node_execution_id
              WHERE v.plan_execution_id = ?
                AND (v.old_retry IS NULL OR v.old_retry = false)
          )
          SELECT * FROM subtree
          """;

      Result<Record> records = dsl.fetch(sql, planExecutionId, rootNodeId, planExecutionId);

      if (records.isEmpty()) {
        return Optional.empty();
      }

      // Reuse reconstructGraph to build the OrchestrationGraph from subtree records
      OrchestrationGraph graph = reconstructGraph(planExecutionId, records, rootNodeId);

      return Optional.of(graph);

    } catch (Exception e) {
      log.error("[NORMALIZED-PG] Failed to get partial orchestration graph for root: {}", rootNodeId, e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<OrchestrationGraph> getOldRetrySubGraph(String planExecutionId, String rootNodeId) {
    if (planExecutionId == null || rootNodeId == null) {
      return Optional.empty();
    }

    try {
      // Same recursive CTE as getPartialOrchestrationGraph but WITHOUT old_retry filter,
      // since the root node itself is old_retry=true and its children are too
      String sql = """
          WITH RECURSIVE subtree AS (
              SELECT *
              FROM graph_vertex
              WHERE plan_execution_id = ? AND node_execution_id = ?

              UNION ALL

              SELECT v.*
              FROM graph_vertex v
              INNER JOIN subtree s ON v.parent_id = s.node_execution_id
              WHERE v.plan_execution_id = ?
          )
          SELECT * FROM subtree
          """;

      Result<Record> records = dsl.fetch(sql, planExecutionId, rootNodeId, planExecutionId);

      if (records.isEmpty()) {
        return Optional.empty();
      }

      OrchestrationGraph graph = reconstructGraph(planExecutionId, records, rootNodeId);
      return Optional.of(graph);

    } catch (Exception e) {
      log.error("[NORMALIZED-PG] Failed to get old retry sub graph for root: {}", rootNodeId, e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> findNodeExecutionId(String planExecutionId, String planNodeId, String nodeExecutionId) {
    if (planExecutionId == null || planNodeId == null) {
      return Optional.empty();
    }

    try {
      // If nodeExecutionId is provided, validate it exists and matches planNodeId
      if (nodeExecutionId != null && !nodeExecutionId.isEmpty()) {
        Record record = dsl.select(NODE_EXECUTION_ID)
                            .from(TABLE)
                            .where(PLAN_EXECUTION_ID.eq(planExecutionId))
                            .and(NODE_EXECUTION_ID.eq(nodeExecutionId))
                            .and(PLAN_NODE_ID.eq(planNodeId))
                            .and(OLD_RETRY.isNull().or(OLD_RETRY.eq(false)))
                            .fetchOne();

        if (record != null) {
          return Optional.of(nodeExecutionId);
        }
        // If provided nodeExecutionId doesn't match, fall through to search by planNodeId
      }

      // Find by planNodeId - get the most recent one (in case of retries)
      Record record = dsl.select(NODE_EXECUTION_ID)
                          .from(TABLE)
                          .where(PLAN_EXECUTION_ID.eq(planExecutionId))
                          .and(PLAN_NODE_ID.eq(planNodeId))
                          .and(OLD_RETRY.isNull().or(OLD_RETRY.eq(false)))
                          .orderBy(CREATED_AT.desc())
                          .limit(1)
                          .fetchOne();

      if (record != null) {
        return Optional.of(record.get(NODE_EXECUTION_ID));
      }

      return Optional.empty();

    } catch (Exception e) {
      log.error("[NORMALIZED-PG] Failed to find nodeExecutionId for planNodeId: {}", planNodeId, e);
      return Optional.empty();
    }
  }

  @Override
  public Map<String, GraphLayoutNodeDTO> getStageLayoutNodes(String planExecutionId) {
    if (planExecutionId == null) {
      return Collections.emptyMap();
    }

    try {
      // Fetch all stage, strategy, and fork vertices for this plan execution.
      // STRATEGY nodes wrap stages (e.g., loop, matrix) and need to be included in layoutNodeMap.
      // FORK nodes wrap parallel stages and also need to be included.
      Result<Record> stageRecords = dsl.select()
                                        .from(TABLE)
                                        .where(PLAN_EXECUTION_ID.eq(planExecutionId))
                                        .and(NODE_GROUP.in("STAGE", "STRATEGY", "FORK"))
                                        .and(OLD_RETRY.isNull().or(OLD_RETRY.eq(false)))
                                        .orderBy(CREATED_AT.asc())
                                        .fetch();

      if (stageRecords.isEmpty()) {
        return Collections.emptyMap();
      }

      // Build separate sets for STRATEGY and FORK node execution IDs
      // STRATEGY nodes (matrix/loop) have children that share planNodeId - need nodeExecutionId as key
      // FORK nodes (parallel stages) have children with unique planNodeIds - use planNodeId as key
      Set<String> strategyNodeExecutionIds = stageRecords.stream()
                                                 .filter(r -> "STRATEGY".equals(r.get(NODE_GROUP)))
                                                 .map(r -> r.get(NODE_EXECUTION_ID))
                                                 .collect(Collectors.toSet());

      Set<String> forkNodeExecutionIds = stageRecords.stream()
                                             .filter(r -> "FORK".equals(r.get(NODE_GROUP)))
                                             .map(r -> r.get(NODE_EXECUTION_ID))
                                             .collect(Collectors.toSet());

      // Combined set for container detection — STRATEGY and FORK suppress sibling-chaining for children
      Set<String> containerNodeExecutionIds = new java.util.HashSet<>();
      containerNodeExecutionIds.addAll(strategyNodeExecutionIds);
      containerNodeExecutionIds.addAll(forkNodeExecutionIds);

      // First pass: determine layout keys for all records
      Map<String, String> nodeExecutionIdToLayoutKey = new HashMap<>();
      for (Record record : stageRecords) {
        String nodeExecutionId = record.get(NODE_EXECUTION_ID);
        String layoutKey = determineLayoutKey(record, strategyNodeExecutionIds, forkNodeExecutionIds);
        nodeExecutionIdToLayoutKey.put(nodeExecutionId, layoutKey);
      }

      // Build containerChildren: map of container nodeExecutionId -> list of child layout keys
      // These are used for EdgeLayoutListDTO.currentNodeChildren for STRATEGY and FORK nodes
      Map<String, List<String>> containerChildren =
          buildContainerChildren(stageRecords, containerNodeExecutionIds, nodeExecutionIdToLayoutKey);

      // Build siblingNextIds: map of layout key -> list containing next sibling's layout key
      // These are used for EdgeLayoutListDTO.nextIds (sequential stage order)
      Map<String, List<String>> siblingNextIds =
          buildSiblingNextIds(stageRecords, containerNodeExecutionIds, nodeExecutionIdToLayoutKey);

      // Build result map
      Map<String, GraphLayoutNodeDTO> layoutNodeMap = new HashMap<>();

      for (Record record : stageRecords) {
        String nodeExecutionId = record.get(NODE_EXECUTION_ID);
        String layoutKey = nodeExecutionIdToLayoutKey.get(nodeExecutionId);

        GraphLayoutNodeDTO dto = recordToGraphLayoutNodeDTO(record, layoutKey, containerChildren, siblingNextIds);
        if (layoutKey != null) {
          layoutNodeMap.put(layoutKey, dto);
        }
      }

      log.debug("[NORMALIZED-PG] Retrieved {} stage layout nodes for plan: {}", layoutNodeMap.size(), planExecutionId);
      return layoutNodeMap;

    } catch (Exception e) {
      log.error("[NORMALIZED-PG] Failed to get stage layout nodes: {}", planExecutionId, e);
      return Collections.emptyMap();
    }
  }

  /**
   * Determine the layout key for a record.
   * - STRATEGY nodes: keyed by planNodeId
   * - FORK nodes (parallel stages): keyed by planNodeId
   * - STAGE nodes under STRATEGY: keyed by nodeExecutionId (iterations share planNodeId)
   * - STAGE nodes under FORK: keyed by planNodeId (parallel stages have unique planNodeIds)
   * - Regular STAGE nodes: keyed by planNodeId
   */
  private String determineLayoutKey(
      Record record, Set<String> strategyNodeExecutionIds, Set<String> forkNodeExecutionIds) {
    String nodeExecutionId = record.get(NODE_EXECUTION_ID);
    String planNodeId = record.get(PLAN_NODE_ID);
    String nodeGroup = record.get(NODE_GROUP);
    String parentId = record.get(PARENT_ID);

    if ("STRATEGY".equals(nodeGroup) || "FORK".equals(nodeGroup)) {
      // Container nodes (STRATEGY, FORK) use planNodeId as key
      return planNodeId;
    } else if (parentId != null && strategyNodeExecutionIds.contains(parentId)) {
      // Child of a STRATEGY node - use nodeExecutionId as key
      // Matrix/loop iterations share the same planNodeId, so need unique nodeExecutionId
      return nodeExecutionId;
    } else if (parentId != null && forkNodeExecutionIds.contains(parentId)) {
      // Child of a FORK node (parallel stages) - use planNodeId as key
      // Each parallel stage has a unique planNodeId from the YAML
      return planNodeId;
    } else {
      // Regular STAGE node - use planNodeId as key
      return planNodeId;
    }
  }

  /**
   * Build map of container nodeExecutionId -> list of child layout keys.
   * Used for EdgeLayoutListDTO.currentNodeChildren for STRATEGY and FORK nodes.
   */
  private Map<String, List<String>> buildContainerChildren(
      Result<Record> records, Set<String> containerNodeExecutionIds, Map<String, String> nodeExecutionIdToLayoutKey) {
    Map<String, List<String>> containerChildren = new HashMap<>();

    for (Record record : records) {
      String parentId = record.get(PARENT_ID);
      // Only include children of container nodes (STRATEGY and FORK)
      if (parentId != null && containerNodeExecutionIds.contains(parentId)) {
        String nodeExecutionId = record.get(NODE_EXECUTION_ID);
        String childLayoutKey = nodeExecutionIdToLayoutKey.get(nodeExecutionId);
        containerChildren.computeIfAbsent(parentId, k -> new ArrayList<>()).add(childLayoutKey);
      }
    }

    return containerChildren;
  }

  /**
   * Build map of layout key -> list containing next sibling's layout key.
   * Siblings are nodes with the same parent, ordered by created_at.
   * Used for EdgeLayoutListDTO.nextIds.
   */
  private Map<String, List<String>> buildSiblingNextIds(
      Result<Record> records, Set<String> containerNodeExecutionIds, Map<String, String> nodeExecutionIdToLayoutKey) {
    Map<String, List<String>> siblingNextIds = new HashMap<>();

    // Group records by parent_id to find siblings
    Map<String, List<Record>> siblingGroups =
        records.stream()
            .filter(r -> r.get(PARENT_ID) != null)
            // Exclude children of container nodes (STRATEGY/FORK) - their children are parallel, not sequential
            .filter(r -> !containerNodeExecutionIds.contains(r.get(PARENT_ID)))
            .collect(Collectors.groupingBy(r -> r.get(PARENT_ID)));

    for (List<Record> siblings : siblingGroups.values()) {
      // Sort by created_at to get sequential order
      siblings.sort(Comparator.comparing(r -> r.get(CREATED_AT)));

      for (int i = 0; i < siblings.size(); i++) {
        Record current = siblings.get(i);
        String currentNodeExecutionId = current.get(NODE_EXECUTION_ID);
        String currentLayoutKey = nodeExecutionIdToLayoutKey.get(currentNodeExecutionId);

        if (i + 1 < siblings.size()) {
          // Has a next sibling
          Record next = siblings.get(i + 1);
          String nextNodeExecutionId = next.get(NODE_EXECUTION_ID);
          String nextLayoutKey = nodeExecutionIdToLayoutKey.get(nextNodeExecutionId);
          siblingNextIds.put(currentLayoutKey, Collections.singletonList(nextLayoutKey));
        } else {
          // Last sibling - no next
          siblingNextIds.put(currentLayoutKey, Collections.emptyList());
        }
      }
    }

    return siblingNextIds;
  }

  // ============================================
  // Helper Methods
  // ============================================

  private static boolean isNonEmptyStepDetailsJson(String json) {
    if (json == null) {
      return false;
    }
    String trimmed = json.trim();
    return !trimmed.isEmpty() && !"[]".equals(trimmed) && !"{}".equals(trimmed) && !"null".equals(trimmed);
  }

  private JSONB toJsonb(Object obj) {
    if (obj == null) {
      return null;
    }
    // Sanitize to remove null keys (valid in Java Maps but not in JSON)
    Object sanitized = sanitizeForJson(obj);
    if (sanitized == null) {
      return null;
    }
    return JSONB.valueOf(JsonUtils.asJson(sanitized));
  }

  /**
   * Recursively sanitize an object for JSON serialization.
   * Removes null keys from maps (valid in Java but not in JSON).
   */
  @SuppressWarnings("unchecked")
  private Object sanitizeForJson(Object obj) {
    if (obj == null) {
      return null;
    }

    if (obj instanceof Map) {
      Map<Object, Object> original = (Map<Object, Object>) obj;
      Map<String, Object> sanitized = new HashMap<>();
      for (Map.Entry<Object, Object> entry : original.entrySet()) {
        Object key = entry.getKey();
        // Skip null keys - not allowed in JSON
        if (key == null) {
          continue;
        }
        String keyStr = key.toString();
        sanitized.put(keyStr, sanitizeForJson(entry.getValue()));
      }
      return sanitized;
    }

    if (obj instanceof List) {
      List<Object> original = (List<Object>) obj;
      List<Object> sanitized = new ArrayList<>(original.size());
      for (Object item : original) {
        sanitized.add(sanitizeForJson(item));
      }
      return sanitized;
    }

    // Primitives and other objects - return as-is
    return obj;
  }

  /**
   * Reconstructs the OrchestrationGraph from vertex records.
   *
   * This method mirrors the legacy OrchestrationAdjacencyListGenerator behavior:
   * 1. Children are nodes with parentId pointing to this node AND no previousId
   *    (nodes with previousId are part of a chain, connected via nextIds)
   * 2. nextIds/prevIds capture sibling relationships for sequential execution
   * 3. Chain mode execution only shows the first child in edges
   * 4. Old retried node references are resolved to latest retry node
   */
  private OrchestrationGraph reconstructGraph(String planExecutionId, Result<Record> vertexRecords, String rootNodeId) {
    // Build lookup maps for efficient processing
    Map<String, Record> recordMap = new HashMap<>();
    for (Record record : vertexRecords) {
      recordMap.put(record.get(NODE_EXECUTION_ID), record);
    }

    // Build retry ID mapping: old retried node ID -> latest node ID
    // This mirrors legacy OrchestrationAdjacencyListGenerator.createOldRetriedNodeToLatestRetriedNodeExecutionMap
    Map<String, String> oldRetriedToLatestMap = buildOldRetriedNodeMap(vertexRecords);

    // Group children by parent - only include nodes WITHOUT previousId
    // Nodes with previousId are chained (connected via nextIds), not direct children
    Map<String, List<Record>> directChildrenByParent =
        vertexRecords.stream()
            .filter(r -> r.get(PARENT_ID) != null)
            .filter(r -> r.get(PREVIOUS_ID) == null) // Only first-in-chain or parallel nodes
            .collect(Collectors.groupingBy(r -> r.get(PARENT_ID)));

    // Build vertex map and adjacency map
    Map<String, GraphVertex> graphVertexMap = new HashMap<>();
    Map<String, EdgeListInternal> adjacencyMap = new HashMap<>();
    List<String> rootNodeIds = new ArrayList<>();
    if (rootNodeId != null) {
      rootNodeIds.add(rootNodeId);
    }
    long maxLastUpdatedAt = 0L;

    for (Record record : vertexRecords) {
      String nodeExecutionId = record.get(NODE_EXECUTION_ID);
      String parentId = record.get(PARENT_ID);
      String previousId = record.get(PREVIOUS_ID);
      String nextId = record.get(NEXT_ID);

      // Convert record to GraphVertex
      GraphVertex vertex = recordToGraphVertex(record);
      graphVertexMap.put(nodeExecutionId, vertex);

      // Track max lastUpdatedAt for change detection
      Long vertexLastUpdatedAt = record.get(LAST_UPDATED_AT);
      if (vertexLastUpdatedAt != null && vertexLastUpdatedAt > maxLastUpdatedAt) {
        maxLastUpdatedAt = vertexLastUpdatedAt;
      }

      // Track root nodes (nodes with no parent)
      if (parentId == null) {
        rootNodeIds.add(nodeExecutionId);
      }

      // Build edges (direct children without previousId)
      List<String> edges = buildEdgesForNode(nodeExecutionId, vertex, directChildrenByParent);

      // Build sibling relationships - resolve old retried node references
      List<String> nextIds = buildNextIds(nextId, parentId, oldRetriedToLatestMap);
      List<String> prevIds = buildPrevIds(previousId, oldRetriedToLatestMap);

      EdgeListInternal edgeList =
          EdgeListInternal.builder().parentId(parentId).edges(edges).nextIds(nextIds).prevIds(prevIds).build();

      adjacencyMap.put(nodeExecutionId, edgeList);
    }

    OrchestrationAdjacencyListInternal adjacencyList =
        OrchestrationAdjacencyListInternal.builder().graphVertexMap(graphVertexMap).adjacencyMap(adjacencyMap).build();

    return OrchestrationGraph.builder()
        .planExecutionId(planExecutionId)
        .cacheKey(planExecutionId)
        .lastUpdatedAt(maxLastUpdatedAt)
        .rootNodeIds(rootNodeIds)
        .adjacencyList(adjacencyList)
        .build();
  }

  /**
   * Build a mapping from old retried node IDs to their latest retry node IDs.
   * Each node's retryIds list contains the IDs of previous retry attempts.
   * This mirrors legacy OrchestrationAdjacencyListGenerator.createOldRetriedNodeToLatestRetriedNodeExecutionMap
   */
  private Map<String, String> buildOldRetriedNodeMap(Result<Record> vertexRecords) {
    Map<String, String> oldToLatestMap = new HashMap<>();
    for (Record record : vertexRecords) {
      String nodeExecutionId = record.get(NODE_EXECUTION_ID);
      String[] retryIds = record.get(RETRY_IDS);
      if (retryIds != null) {
        for (String oldRetryId : retryIds) {
          if (oldRetryId != null && !oldRetryId.isEmpty()) {
            oldToLatestMap.put(oldRetryId, nodeExecutionId);
          }
        }
      }
    }
    return oldToLatestMap;
  }

  /**
   * Build edges (direct children) for a node.
   * For chain mode execution, only the first child is included.
   * For parallel/default mode, all direct children are included.
   */
  private List<String> buildEdgesForNode(
      String nodeExecutionId, GraphVertex vertex, Map<String, List<Record>> directChildrenByParent) {
    List<Record> childRecords = directChildrenByParent.getOrDefault(nodeExecutionId, Collections.emptyList());
    if (childRecords.isEmpty()) {
      return new ArrayList<>();
    }

    // Sort children by created_at for consistent ordering
    List<Record> sortedChildren = childRecords.stream()
                                      .sorted(Comparator.comparingLong(r -> {
                                        Long createdAt = r.get(CREATED_AT);
                                        return createdAt != null ? createdAt : 0L;
                                      }))
                                      .collect(Collectors.toList());

    // Check if parent is in chain mode
    if (isChainMode(vertex.getMode())) {
      // Chain mode: only include the first child, rest are connected via nextIds
      return new ArrayList<>(Collections.singletonList(sortedChildren.get(0).get(NODE_EXECUTION_ID)));
    } else {
      // Parallel/default mode: include all direct children
      return sortedChildren.stream()
          .map(r -> r.get(NODE_EXECUTION_ID))
          .collect(Collectors.toCollection(ArrayList::new));
    }
  }

  /**
   * Build nextIds list from the node's nextId field.
   * nextId points to the next sibling in a chain execution.
   * Resolves old retried node references to their latest versions.
   */
  private List<String> buildNextIds(String nextId, String parentId, Map<String, String> oldRetriedToLatestMap) {
    if (nextId == null || nextId.isEmpty()) {
      return new ArrayList<>();
    }
    // Resolve old retried node reference to latest version
    String resolvedNextId = oldRetriedToLatestMap.getOrDefault(nextId, nextId);
    // Don't add nextId if it points to the parent (edge case)
    if (resolvedNextId.equals(parentId)) {
      return new ArrayList<>();
    }
    return new ArrayList<>(Collections.singletonList(resolvedNextId));
  }

  /**
   * Build prevIds list from the node's previousId field.
   * previousId points to the previous sibling in a chain execution.
   * Resolves old retried node references to their latest versions.
   */
  private List<String> buildPrevIds(String previousId, Map<String, String> oldRetriedToLatestMap) {
    if (previousId == null || previousId.isEmpty()) {
      return new ArrayList<>();
    }
    // Resolve old retried node reference to latest version
    String resolvedPrevId = oldRetriedToLatestMap.getOrDefault(previousId, previousId);
    return new ArrayList<>(Collections.singletonList(resolvedPrevId));
  }

  /**
   * Check if the execution mode indicates chain (sequential) execution.
   * In chain mode, children execute one after another, connected via nextIds.
   */
  private boolean isChainMode(io.harness.pms.contracts.execution.ExecutionMode mode) {
    if (mode == null) {
      return false;
    }
    // Use the utility class for consistency with legacy implementation
    return ExecutionModeUtils.isChainMode(mode);
  }

  private GraphVertex recordToGraphVertex(Record record) {
    GraphVertex vertex = GraphVertexMapper.fromRecord(record);
    // Enrich with delegate selection info for task-mode nodes
    return enrichWithDelegateInfo(vertex, record);
  }

  /**
   * Enrich GraphVertex with delegate selection info for task-mode nodes.
   * This mirrors the legacy GraphVertexConverter behavior where DelegateInfoHelper
   * is called to fetch delegate info from the delegate service.
   *
   * Only nodes with ExecutionMode in {TASK, TASK_CHAIN} need delegate info.
   */
  private GraphVertex enrichWithDelegateInfo(GraphVertex vertex, Record record) {
    if (vertex == null || vertex.getMode() == null) {
      return vertex;
    }

    // Get account ID from record
    String accountId = record.get(ACCOUNT_IDENTIFIER);
    if (accountId == null || accountId.isEmpty()) {
      return vertex;
    }

    // Get executable responses from vertex (already parsed)
    if (vertex.getExecutableResponses() == null || vertex.getExecutableResponses().isEmpty()) {
      return vertex;
    }

    try {
      // Fetch delegate info using the same helper as legacy
      List<GraphDelegateSelectionLogParams> delegateInfo = delegateInfoHelper.getDelegateInformationForGivenTask(
          vertex.getExecutableResponses(), vertex.getMode(), accountId);

      if (delegateInfo != null && !delegateInfo.isEmpty()) {
        return vertex.toBuilder().graphDelegateSelectionLogParams(delegateInfo).build();
      }
    } catch (Exception e) {
      log.debug("[NORMALIZED-PG] Failed to enrich delegate info for node {}: {}", vertex.getUuid(), e.getMessage());
    }

    return vertex;
  }

  private GraphLayoutNodeDTO recordToGraphLayoutNodeDTO(Record record, String layoutKey,
      Map<String, List<String>> containerChildren, Map<String, List<String>> siblingNextIds) {
    return GraphLayoutNodeMapper.fromRecord(record, layoutKey, containerChildren, siblingNextIds, nodeTypeLookupService,
        GraphCDCServiceImpl::mapStepTypeToNodeType);
  }

  // ============================================
  // NodeType Mapping (Static Cache for O(1) Lookup)
  // ============================================

  /**
   * Static cache mapping stepType (e.g., "CUSTOM_STAGE") to YAML-style nodeType (e.g., "Custom").
   * Built once at class load for O(1) lookup instead of O(n) iteration.
   */
  private static final Map<String, String> STEP_TYPE_TO_NODE_TYPE = buildStepTypeMapping();

  private static Map<String, String> buildStepTypeMapping() {
    return Arrays.stream(ExecutionNodeType.values())
        .filter(e -> e.getYamlType() != null && !e.getYamlType().isEmpty())
        .collect(Collectors.toMap(ExecutionNodeType::getName,
            e -> capitalize(e.getYamlType()), (existing, replacement) -> existing // Keep first on collision
            ));
  }

  private static String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }

  /**
   * Convert stepType (e.g., "CUSTOM_STAGE") to the YAML-style nodeType (e.g., "Custom").
   * Uses static cache for O(1) lookup.
   *
   * @param stepType the stepType.type value from NodeExecution (e.g., "CUSTOM_STAGE")
   * @return the YAML-style nodeType with capitalized first letter, or the original stepType if no mapping found
   */
  private static String mapStepTypeToNodeType(String stepType) {
    if (stepType == null || stepType.isEmpty()) {
      return stepType;
    }
    return STEP_TYPE_TO_NODE_TYPE.getOrDefault(stepType, stepType);
  }
}
