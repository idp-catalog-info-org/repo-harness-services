/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.FailureDataAvro;
import io.harness.pms.contracts.execution.FailureInfoAvro;
import io.harness.pms.contracts.execution.PipelineEventAvro;
import io.harness.pms.contracts.execution.StageEventAvro;
import io.harness.pms.contracts.execution.StepEndEventAvro;
import io.harness.pms.execution.cdc.BSONTimestamp;
import io.harness.pms.execution.cdc.CDCEventEnvelope;
import io.harness.pms.execution.cdc.ClusterTime;
import io.harness.pms.execution.cdc.DocumentKey;
import io.harness.pms.execution.cdc.FailureDataDocument;
import io.harness.pms.execution.cdc.MongoDBCDCEvent;
import io.harness.pms.execution.cdc.Namespace;
import io.harness.pms.execution.cdc.NodeExecutionFullDocument;
import io.harness.pms.execution.cdc.PipelineExecutionCDCEnrichment;
import io.harness.pms.execution.cdc.ResumeToken;
import io.harness.pms.execution.cdc.WallTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.connect.avro.ConnectDefault;
import io.serializer.HObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;

/**
 * Utility class for wrapping NodeExecution Kafka Avro events in MongoDB CDC format.
 * Wraps node execution events (step, stage, pipeline) for CDC-aware consumers using type-safe DTOs.
 * Events are mapped to their respective collections: nodeExecutionsStep, nodeExecutionsStage, nodeExecutionsPipeline.
 */
@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionCDCWrapper {
  private static final ObjectMapper OBJECT_MAPPER = HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

  /**
   * Creates a plain JSON CDC envelope matching the required schema for Kafka Connect MongoDB Source format.
   * This is the new method for plain JSON CDC events (not Avro).
   *
   * @param avroEvent The original Avro event (StepEndEventAvro, StageEventAvro, or PipelineEventAvro)
   * @param database MongoDB database name (e.g., "harness-pms")
   * @param collection MongoDB collection name (e.g., "nodeExecutionsStep", "nodeExecutionsStage",
   *     "nodeExecutionsPipeline")
   * @param documentId The document ID (execution ID)
   * @param operationType CDC operation type ("insert", "update", "delete")
   * @return Plain JSON string with CDC envelope
   */
  public static String createPlainJsonCDCEnvelope(
      SpecificRecordBase avroEvent, String database, String collection, String documentId, String operationType) {
    return createPlainJsonCDCEnvelope(avroEvent, database, collection, documentId, operationType, null);
  }

  /**
   * Creates a plain JSON CDC envelope, injecting pipeline enrichment fields for pipeline-level events.
   * Pass {@code null} for step/stage events.
   *
   * <p>The Avro schema is intentionally <b>not</b> changed. All extra pipeline CDC fields
   * (triggerType, triggeredById, triggeredByIdentifier, executedModules, deleted, runSequence)
   * are sourced from planExecutionsSummary and bundled in {@code enrichment}.
   *
   * @param avroEvent     The original Avro event
   * @param database      MongoDB database name (e.g., "harness-pms")
   * @param collection    MongoDB collection name
   * @param documentId    The document ID (execution ID)
   * @param operationType CDC operation type ("insert", "update", "delete")
   * @param enrichment    Pipeline enrichment from planExecutionsSummary; null for step/stage events
   * @return Plain JSON string with CDC envelope
   */
  public static String createPlainJsonCDCEnvelope(SpecificRecordBase avroEvent, String database, String collection,
      String documentId, String operationType, PipelineExecutionCDCEnrichment enrichment) {
    try {
      // Build CDC event using type-safe DTOs
      MongoDBCDCEvent cdcEvent = buildCDCEvent(avroEvent, database, collection, documentId, operationType, enrichment);

      // Create the top-level envelope using type-safe DTO
      CDCEventEnvelope envelope =
          CDCEventEnvelope.builder().version("1.0").db(database).sourceType("MONGODB").payload(cdcEvent).build();

      // Serialize the entire envelope to JSON string using Jackson
      return OBJECT_MAPPER.writeValueAsString(envelope);
    } catch (Exception ex) {
      log.error("Failed to create plain JSON CDC envelope. Database: {}, Collection: {}, DocumentId: {}", database,
          collection, documentId, ex);
      throw new RuntimeException("Failed to create plain JSON CDC envelope", ex);
    }
  }

  /**
   * Wraps an Avro event in MongoDB CDC envelope format matching Kafka Connect structure.
   *
   * @param avroEvent The original Avro event (StepEndEventAvro, StageEventAvro, or PipelineEventAvro)
   * @param database MongoDB database name (e.g., "harness-pms")
   * @param collection MongoDB collection name (e.g., "nodeExecutionsStep", "nodeExecutionsStage",
   *     "nodeExecutionsPipeline")
   * @param documentId The document ID (execution ID)
   * @param operationType CDC operation type ("insert", "update", "delete")
   * @return ConnectDefault with payload as JSON string
   * @deprecated Use {@link #createPlainJsonCDCEnvelope} for plain JSON CDC events instead.
   */
  @Deprecated
  public static ConnectDefault wrapInNodeExecutionCDCFormat(
      SpecificRecordBase avroEvent, String database, String collection, String documentId, String operationType) {
    try {
      // Build CDC event using type-safe DTOs (no enrichment for the deprecated Avro path)
      MongoDBCDCEvent cdcEvent = buildCDCEvent(
          avroEvent, database, collection, documentId, operationType, (PipelineExecutionCDCEnrichment) null);

      // Serialize to JSON string using Jackson
      String payloadJson = OBJECT_MAPPER.writeValueAsString(cdcEvent);

      // Create simple Avro record with payload, db, and source_type
      return ConnectDefault.newBuilder().setPayload(payloadJson).setDb(database).setSourceType("MONGODB").build();
    } catch (Exception ex) {
      log.error("Failed to wrap NodeExecution event in CDC format. Database: {}, Collection: {}, DocumentId: {}",
          database, collection, documentId, ex);
      throw new RuntimeException("Failed to wrap NodeExecution event in CDC format", ex);
    }
  }

  /**
   * Builds the CDC event using type-safe DTOs matching MongoDB change stream format.
   * {@code enrichment} is injected only for pipeline-level events; pass {@code null} for step/stage.
   */
  private static MongoDBCDCEvent buildCDCEvent(SpecificRecordBase avroEvent, String database, String collection,
      String documentId, String operationType, PipelineExecutionCDCEnrichment enrichment) {
    Instant now = Instant.now();
    long timestampSeconds = now.getEpochSecond();
    long timestampMillis = now.toEpochMilli();
    int incrementValue = (int) (timestampMillis % 1000);

    // Build resume token
    ResumeToken resumeToken = ResumeToken.builder().data(generateResumeToken(documentId)).build();

    // Build cluster time
    BSONTimestamp bsonTimestamp = BSONTimestamp.builder().t(timestampSeconds).i(incrementValue).build();
    ClusterTime clusterTime = ClusterTime.builder().timestamp(bsonTimestamp).build();

    // Build wall time with ISO-8601 format for Spark timestamp parsing
    String iso8601Timestamp = now.atZone(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
    WallTime wallTime = WallTime.builder().date(iso8601Timestamp).build();

    // Build namespace
    Namespace namespace = Namespace.builder().db(database).coll(collection).build();

    // Build document key
    DocumentKey documentKey = DocumentKey.builder().id(documentId).build();

    // Build full document from Avro event; inject pipeline enrichment only for pipeline events
    NodeExecutionFullDocument fullDocument = (avroEvent instanceof PipelineEventAvro)
        ? mapPipelineEventToFullDocument((PipelineEventAvro) avroEvent, enrichment)
        : convertAvroToFullDocument(avroEvent);

    // Build complete CDC event
    return MongoDBCDCEvent.builder()
        .id(resumeToken)
        .operationType(operationType)
        .clusterTime(clusterTime)
        .wallTime(wallTime)
        .ns(namespace)
        .documentKey(documentKey)
        .fullDocument(fullDocument)
        .build();
  }

  /**
   * Generates a simple resume token for the change stream.
   * In a real CDC system, this would be a MongoDB resume token.
   * For application events, we generate a synthetic token based on timestamp and documentId.
   *
   * @param documentId The document ID
   * @return Resume token string
   */
  private static String generateResumeToken(String documentId) {
    // Format: timestamp_documentId (simplified resume token)
    return Instant.now().toEpochMilli() + "_" + documentId;
  }

  /**
   * Converts an Avro event to unified NodeExecutionFullDocument DTO.
   * Maps all fields from Step/Stage/Pipeline events into a single structure.
   *
   * @param avroEvent The Avro event (StepEndEventAvro, StageEventAvro, or PipelineEventAvro)
   * @return NodeExecutionFullDocument with all applicable fields populated
   */
  private static NodeExecutionFullDocument convertAvroToFullDocument(SpecificRecordBase avroEvent) {
    if (avroEvent instanceof StepEndEventAvro) {
      return mapStepEventToFullDocument((StepEndEventAvro) avroEvent);
    } else if (avroEvent instanceof StageEventAvro) {
      return mapStageEventToFullDocument((StageEventAvro) avroEvent);
    } else if (avroEvent instanceof PipelineEventAvro) {
      return mapPipelineEventToFullDocument((PipelineEventAvro) avroEvent, (PipelineExecutionCDCEnrichment) null);
    } else {
      throw new IllegalArgumentException("Unsupported Avro event type: " + avroEvent.getClass().getName());
    }
  }

  /**
   * Maps StepEndEventAvro to NodeExecutionFullDocument.
   */
  private static NodeExecutionFullDocument mapStepEventToFullDocument(StepEndEventAvro event) {
    return NodeExecutionFullDocument
        .builder()
        // Common fields
        .level(toStringOrNull(event.getLevel()))
        .accountIdentifier(toStringOrNull(event.getAccountIdentifier()))
        .orgIdentifier(toStringOrNull(event.getOrgIdentifier()))
        .projectIdentifier(toStringOrNull(event.getProjectIdentifier()))
        .parentUniqueId(toStringOrNull(event.getParentUniqueId()))
        .pipelineIdentifier(toStringOrNull(event.getPipelineIdentifier()))
        .pipelineName(toStringOrNull(event.getPipelineName()))
        .planExecutionId(toStringOrNull(event.getPlanExecutionId()))
        .executionUrl(toStringOrNull(event.getExecutionUrl()))
        .status(toStringOrNull(event.getStatus()))
        .eventType(toStringOrNull(event.getEventType()))
        .createdAt(toStringOrNull(event.getCreatedAt()))
        .startTs(toStringOrNull(event.getStartTs()))
        .lastModifiedAt(toStringOrNull(event.getLastModifiedAt()))
        .endTs(toStringOrNull(event.getEndTs()))
        .duration(toStringOrNull(event.getDuration()))
        .failureInfo(toFailureDataDocuments(event.getFailureInfo()))
        .moduleInfo(event.getModuleInfo())
        // Stage fields (from step context)
        .stageExecutionId(toStringOrNull(event.getStageExecutionId()))
        .stageIdentifier(toStringOrNull(event.getStageIdentifier()))
        // Step-specific fields
        .stepExecutionId(toStringOrNull(event.getStepExecutionId()))
        .stepIdentifier(toStringOrNull(event.getStepIdentifier()))
        .stepName(toStringOrNull(event.getStepName()))
        .stepType(toStringOrNull(event.getStepType()))
        .stepInputs(toStringOrNull(event.getStepInputs()))
        .isRetried(event.getIsRetried())
        .retryIds(toStringListOrNull(event.getRetryIds()))
        .logUrl(toStringOrNull(event.getLogUrl()))
        .stepOutputs(toStringListOrNull(event.getStepOutputs()))
        .build();
  }

  /**
   * Maps StageEventAvro to NodeExecutionFullDocument.
   */
  private static NodeExecutionFullDocument mapStageEventToFullDocument(StageEventAvro event) {
    return NodeExecutionFullDocument
        .builder()
        // Common fields
        .level(toStringOrNull(event.getLevel()))
        .accountIdentifier(toStringOrNull(event.getAccountIdentifier()))
        .orgIdentifier(toStringOrNull(event.getOrgIdentifier()))
        .projectIdentifier(toStringOrNull(event.getProjectIdentifier()))
        .parentUniqueId(toStringOrNull(event.getParentUniqueId()))
        .pipelineIdentifier(toStringOrNull(event.getPipelineIdentifier()))
        .pipelineName(toStringOrNull(event.getPipelineName()))
        .planExecutionId(toStringOrNull(event.getPlanExecutionId()))
        .executionUrl(toStringOrNull(event.getExecutionUrl()))
        .status(toStringOrNull(event.getStatus()))
        .eventType(toStringOrNull(event.getEventType()))
        .createdAt(toStringOrNull(event.getCreatedAt()))
        .startTs(toStringOrNull(event.getStartTs()))
        .lastModifiedAt(toStringOrNull(event.getLastModifiedAt()))
        .endTs(toStringOrNull(event.getEndTs()))
        .duration(toStringOrNull(event.getDuration()))
        .failureInfo(toFailureDataDocuments(event.getFailureInfo()))
        .moduleInfo(event.getModuleInfo())
        // Stage-specific fields
        .stageExecutionId(toStringOrNull(event.getStageExecutionId()))
        .stageIdentifier(toStringOrNull(event.getStageIdentifier()))
        .stageName(toStringOrNull(event.getStageName()))
        .stageType(toStringOrNull(event.getStageType()))
        .build();
  }

  /**
   * Maps PipelineEventAvro to NodeExecutionFullDocument with optional pipeline enrichment.
   *
   * <p>Fields that come ONLY from {@code planExecutionsSummary} (and are therefore absent from the
   * Avro event) are populated via {@link PipelineExecutionCDCEnrichment}:
   * <ul>
   *   <li>{@code runSequence} – pipeline run counter</li>
   *   <li>{@code triggerType} – e.g. MANUAL, WEBHOOK, SCHEDULER_CRON</li>
   *   <li>{@code triggeredById} – UUID of the initiating user/trigger</li>
   *   <li>{@code triggeredByIdentifier} – username / trigger identifier</li>
   *   <li>{@code executedModules} – list of modules that actually ran (CD, CI, …)</li>
   *   <li>{@code deleted} – soft-delete flag from planExecutionsSummary</li>
   * </ul>
   *
   * <p>Pass {@code null} for {@code enrichment} when called from the deprecated Avro wrapper path
   * or from step/stage event paths (where these fields do not apply).
   */
  private static NodeExecutionFullDocument mapPipelineEventToFullDocument(
      PipelineEventAvro event, PipelineExecutionCDCEnrichment enrichment) {
    return NodeExecutionFullDocument
        .builder()
        // Common fields sourced from the Avro event
        .level(toStringOrNull(event.getLevel()))
        .accountIdentifier(toStringOrNull(event.getAccountIdentifier()))
        .orgIdentifier(toStringOrNull(event.getOrgIdentifier()))
        .projectIdentifier(toStringOrNull(event.getProjectIdentifier()))
        .parentUniqueId(toStringOrNull(event.getParentUniqueId()))
        .pipelineIdentifier(toStringOrNull(event.getPipelineIdentifier()))
        .pipelineName(toStringOrNull(event.getPipelineName()))
        .planExecutionId(toStringOrNull(event.getPlanExecutionId()))
        .executionUrl(toStringOrNull(event.getExecutionUrl()))
        .status(toStringOrNull(event.getStatus()))
        .eventType(toStringOrNull(event.getEventType()))
        .createdAt(toStringOrNull(event.getCreatedAt()))
        .startTs(toStringOrNull(event.getStartTs()))
        .lastModifiedAt(toStringOrNull(event.getLastModifiedAt()))
        .endTs(toStringOrNull(event.getEndTs()))
        .duration(toStringOrNull(event.getDuration()))
        .failureInfo(toFailureDataDocuments(event.getFailureInfo()))
        // Pipeline-specific fields sourced from Avro event
        .tags(toStringMapOrNull(event.getTags()))
        // Pipeline-specific fields sourced from planExecutionsSummary via enrichment DTO.
        // All null-safe: if enrichment or any sub-field is null the builder just leaves it null.
        .runSequence(enrichment != null ? enrichment.getRunSequence() : null)
        .triggerType(enrichment != null ? enrichment.getTriggerType() : null)
        .triggeredById(enrichment != null ? enrichment.getTriggeredById() : null)
        .triggeredByIdentifier(enrichment != null ? enrichment.getTriggeredByIdentifier() : null)
        .executedModules(enrichment != null ? enrichment.getExecutedModules() : null)
        .deleted(enrichment != null ? enrichment.getDeleted() : null)
        .build();
  }

  /**
   * Converts CharSequence to String, returns null if input is null.
   */
  private static String toStringOrNull(CharSequence value) {
    return value != null ? value.toString() : null;
  }

  /**
   * Converts {@code List<CharSequence>} to {@code List<String>}, returns null if input is null.
   */
  private static java.util.List<String> toStringListOrNull(java.util.List<CharSequence> list) {
    if (list == null) {
      return null;
    }
    return list.stream().map(cs -> cs != null ? cs.toString() : null).collect(java.util.stream.Collectors.toList());
  }

  /**
   * Converts {@code Map<CharSequence, CharSequence>} to {@code Map<String, String>}, returns null if input is null.
   */
  private static java.util.Map<String, String> toStringMapOrNull(java.util.Map<CharSequence, CharSequence> map) {
    if (map == null) {
      return null;
    }
    java.util.Map<String, String> result = new java.util.HashMap<>();
    map.forEach((k, v) -> result.put(k.toString(), v != null ? v.toString() : null));
    return result;
  }

  protected static List<FailureDataDocument> toFailureDataDocuments(FailureInfoAvro failureInfoAvro) {
    if (failureInfoAvro == null || failureInfoAvro.getFailureData() == null) {
      return null;
    }
    return failureInfoAvro.getFailureData()
        .stream()
        .map(NodeExecutionCDCWrapper::toFailureDataDocument)
        .collect(java.util.stream.Collectors.toList());
  }

  private static FailureDataDocument toFailureDataDocument(FailureDataAvro failureDataAvro) {
    if (failureDataAvro == null) {
      return null;
    }
    return FailureDataDocument.builder()
        .code(toStringOrNull(failureDataAvro.getCode()))
        .level(toStringOrNull(failureDataAvro.getLevel()))
        .message(toStringOrNull(failureDataAvro.getMessage()))
        .failureType(toStringOrNull(failureDataAvro.getFailureType()))
        .failureSubType(toStringOrNull(failureDataAvro.getFailureSubType()))
        .build();
  }

  /**
   * Determines the CDC operation type based on node execution event type.
   * Maps event types (nodeStart, nodeStatusUpdate, nodeEnd) to CDC operations.
   *
   * @param eventType The event type (e.g., "nodeStart", "nodeEnd")
   * @return CDC operation type ("insert", "update", etc.)
   */
  public static String determineNodeExecutionCDCOperationType(String eventType) {
    if (eventType == null) {
      return "update"; // Default to update for safety
    }

    switch (eventType) {
      case "nodeStart":
        return "insert"; // First time we see this execution
      case "nodeStatusUpdate":
      case "nodeEnd":
        return "update"; // Subsequent updates to the execution
      default:
        return "update";
    }
  }

  /**
   * Determines the MongoDB collection name based on node execution level.
   * Each level (step, stage, pipeline) has its own collection.
   *
   * @param level The event level ("step", "stage", "pipeline")
   * @return Collection name for node execution CDC format
   */
  public static String determineNodeExecutionCollectionName(String level) {
    if (level == null) {
      return "nodeExecutionsPipeline"; // Default
    }

    switch (level.toLowerCase()) {
      case "step":
        return "nodeExecutionsStep";
      case "stage":
        return "nodeExecutionsStage";
      case "pipeline":
        return "nodeExecutionsPipeline";
      default:
        return "nodeExecutionsPipeline";
    }
  }
}
