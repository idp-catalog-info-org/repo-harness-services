/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2025/03/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.api.impl.KafkaOutboxServiceImpl;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.producers.HKafkaStringProducer;
import io.harness.kafka.producers.avro.HKafkaAvroProducer;
import io.harness.metrics.service.api.MetricService;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.FailureDataAvro;
import io.harness.pms.contracts.execution.FailureInfoAvro;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureTypeInfo;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.cdc.PipelineExecutionCDCEnrichment;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.Callback;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public abstract class AbstractEventKafkaSender {
  // Confluent Avro producer (legacy) — used when the PIPE-33718 FF is OFF (default behavior).
  @Inject @KafkaModule.Confluent protected Optional<HKafkaAvroProducer> hKafkaAvroProducerOptional;
  // Self-hosted (General) Avro producer — used when PIPE_PUBLISH_DATA_INGESTION_EVENTS_TO_GENERAL_KAFKA
  // is enabled for the account. Topic names continue to come from PipelineServiceConfiguration
  // (env-prefixed values like prod0_pipeline_step_executions_summary). PIPE-33718.
  @Inject @KafkaModule.General protected Optional<HKafkaAvroProducer> hKafkaAvroProducerGeneralOptional;
  @Inject @KafkaModule.General protected Optional<HKafkaStringProducer> hKafkaGeneralStringProducerOptional;
  @Inject protected PipelineServiceConfiguration configuration;
  @Inject protected PipelineExpressionHelper pipelineExpressionHelper;
  @Inject protected PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject protected PmsFeatureFlagService featureFlagService;
  @Inject @Named("executionOutboxService") private OutboxService executionOutboxService;
  @Inject @Named("kafkaOutboxService") private OutboxService kafkaOutboxService;
  @Inject(optional = true) protected MetricService metricService;

  protected static final String KAFKA_PRODUCER_EVENT_SENT_COUNT = "kafka_producer_event_sent_count";
  private static final String STATUS_SUCCESS = "success";
  private static final String STATUS_FAILURE = "failure";

  /**
   * Returns the Avro producer that should be used to publish events for the given account.
   *
   * <p>When {@link FeatureName#PIPE_PUBLISH_DATA_INGESTION_EVENTS_TO_GENERAL_KAFKA} is enabled
   * for the account, the self-hosted (General) Kafka producer is returned. Otherwise the
   * Confluent producer is returned (current/default behavior). This is the single entry-point
   * for the Confluent → self-hosted dual-cluster rollout (PIPE-33718) so that all three event
   * levels (step, stage, pipeline) flip atomically per-account when the flag is toggled.
   *
   * <p>The returned {@link Optional} may be empty if the active producer's binding is not
   * initialized in the current environment — callers must guard against this.
   */
  protected Optional<HKafkaAvroProducer> getActiveAvroProducer(String accountIdentifier) {
    if (featureFlagService != null
        && featureFlagService.isEnabled(
            accountIdentifier, FeatureName.PIPE_PUBLISH_DATA_INGESTION_EVENTS_TO_GENERAL_KAFKA)) {
      return hKafkaAvroProducerGeneralOptional;
    }
    return hKafkaAvroProducerOptional;
  }

  protected boolean isKafkaProducerInitialized(String accountIdentifier) {
    if (getActiveAvroProducer(accountIdentifier).isEmpty()) {
      log.warn("Kafka avro producer is not initialized");
      return false;
    }
    return true;
  }

  protected boolean isGeneralKafkaStringProducerInitialized() {
    if (hKafkaGeneralStringProducerOptional.isEmpty()) {
      log.warn("General Kafka string producer is not initialized");
      return false;
    }
    return true;
  }

  protected PipelineExecutionSummaryEntity getPipelineExecutionSummary(Ambiance ambiance) {
    return pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(AmbianceUtils.getAccountId(ambiance),
        ambiance.getPlanExecutionId(), Set.of(PlanExecutionSummaryKeys.name, PlanExecutionSummaryKeys.tags));
  }

  protected String generateExecutionUrl(Ambiance ambiance) {
    return pipelineExpressionHelper.generateUrl(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
        AmbianceUtils.getPipelineIdentifier(ambiance), ambiance.getPlanExecutionId(),
        java.util.Collections.emptyList());
  }

  protected FailureInfoAvro mapFailureInfoProtoToAvro(FailureInfo failureInfoProto) {
    if (failureInfoProto == null) {
      return null;
    }

    List<FailureDataAvro> failureDataAvroList = new ArrayList<>();

    if (EmptyPredicate.isNotEmpty(failureInfoProto.getFailureDataList())) {
      for (FailureData failureDataProto : failureInfoProto.getFailureDataList()) {
        String failureType = null;
        String failureSubType = null;
        if (EmptyPredicate.isNotEmpty(failureDataProto.getFailureTypeInfosList())) {
          FailureTypeInfo firstInfo = failureDataProto.getFailureTypeInfos(0);
          failureType = firstInfo.getFailureType().name();
          failureSubType = firstInfo.getFailureSubType().name();
        }

        FailureDataAvro failureDataAvro = FailureDataAvro.newBuilder()
                                              .setCode(failureDataProto.getCode())
                                              .setLevel(failureDataProto.getLevel())
                                              .setMessage(failureDataProto.getMessage())
                                              .setFailureType(failureType)
                                              .setFailureSubType(failureSubType)
                                              .build();
        failureDataAvroList.add(failureDataAvro);
      }
    } else if (EmptyPredicate.isNotEmpty(failureInfoProto.getErrorMessage())) {
      // Fallback: Create a single FailureDataAvro from errorMessage for backward compatibility
      FailureDataAvro failureDataAvro = FailureDataAvro.newBuilder()
                                            .setCode(null)
                                            .setLevel(null)
                                            .setMessage(failureInfoProto.getErrorMessage())
                                            .setFailureType(null)
                                            .setFailureSubType(null)
                                            .build();
      failureDataAvroList.add(failureDataAvro);
    }

    if (failureDataAvroList.isEmpty()) {
      return null;
    }

    return FailureInfoAvro.newBuilder().setFailureData(failureDataAvroList).build();
  }

  public <Event> Callback createFailureHandlingCallback(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance,
      String eventType, Function<NodeOutboxInfo, Event> eventMapper, String topicName, String level,
      String metricEventType) {
    return (recordMetadata, exception) -> {
      if (exception != null) {
        Event nodeExecutionEvent = eventMapper.apply(nodeOutboxInfo);
        log.warn(String.format("Failed to send %s directly to Kafka for nodeExecution %s. Saving into the KafkaOutbox.",
                     eventType, nodeOutboxInfo.getNodeExecutionId()),
            exception);
        ((KafkaOutboxServiceImpl) kafkaOutboxService).save((io.harness.event.Event) nodeExecutionEvent, topicName);
        emitKafkaEventMetric(level, topicName, metricEventType, STATUS_FAILURE);
      } else {
        emitKafkaEventMetric(level, topicName, metricEventType, STATUS_SUCCESS);
      }
    };
  }

  protected boolean processOutboxEvent(String accountIdentifier, FeatureName featureName, String topicName,
      Object avroObject, String recordKey, String eventType) {
    var isFeatureEnabled = featureFlagService.isEnabled(accountIdentifier, featureName);
    Optional<HKafkaAvroProducer> activeProducer = getActiveAvroProducer(accountIdentifier);
    if (isFeatureEnabled && activeProducer.isPresent()) {
      try {
        // Create a latch with count 1 to synchronize the async Kafka callback
        final CountDownLatch latch = new CountDownLatch(1);
        // Initialize result holder (needs to be final or effectively final for use in callback)
        final AtomicBoolean success =
            new AtomicBoolean(false); // Send to Kafka with a callback that signals the latch when complete
        Callback callback = (metadata, exception) -> {
          try {
            if (exception != null) {
              log.warn("Failed to process {} from KafkaOutbox to Kafka topic {}", eventType, topicName, exception);
              success.set(false);
            } else {
              log.debug("Successfully processed {} from KafkaOutbox to Kafka topic {}", eventType, topicName);
              success.set(true);
            }
          } finally {
            latch.countDown();
          }
        };
        activeProducer.get().send(
            topicName, (org.apache.avro.generic.GenericRecord) avroObject, Collections.emptyMap(), recordKey, callback);
        // Wait for the callback to complete (with timeout)
        if (!latch.await(5, TimeUnit.SECONDS)) {
          log.warn("Timed out waiting for Kafka send operation to complete for {} to topic {}", eventType, topicName);
          return false;
        }
        return success.get();
      } catch (Exception e) {
        log.warn("Failed to send {} to Kafka", eventType, e);
        return false;
      }
    }
    log.warn("Skipping sending {} to Kafka: feature flag disabled or Kafka producer not initialized", eventType);
    return false;
  }

  public abstract void sendEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance, Callback callback, String eventType);

  /**
   * Extracts accountId from the Avro event for feature flag checking.
   *
   * @param avroEvent The Avro event
   * @return Account ID or null if not found
   */
  private String extractAccountId(SpecificRecordBase avroEvent) {
    try {
      Object accountIdField = avroEvent.get("accountIdentifier");
      return accountIdField != null ? accountIdField.toString() : null;
    } catch (Exception ex) {
      log.warn("Failed to extract accountIdentifier from Avro event", ex);
      return null;
    }
  }

  protected void emitKafkaEventMetric(String level, String topic, String eventType, String status) {
    if (metricService == null) {
      return;
    }
    try (PmsMetricContextGuard guard = new PmsMetricContextGuard(
             ImmutableMap.<String, String>builder()
                 .put(PmsEventMonitoringConstants.LEVEL, level)
                 .put(PmsEventMonitoringConstants.TOPIC, topic)
                 .put(PmsEventMonitoringConstants.EVENT_TYPE, eventType != null ? eventType : "")
                 .put(PmsEventMonitoringConstants.STATUS, status)
                 .build())) {
      metricService.incCounter(KAFKA_PRODUCER_EVENT_SENT_COUNT);
    } catch (Exception ex) {
      log.debug("Failed to emit Kafka event metric", ex);
    }
  }

  /**
   * Sends NodeExecution CDC event to the CDC topic if CDC publishing is enabled.
   * This method wraps the original Avro event in MongoDB CDC format as plain JSON and sends it to a separate CDC topic.
   * Uses General Kafka String Producer for self-hosted Kafka.
   *
   * @param originalAvroEvent The original Avro event (StepEndEventAvro, StageEventAvro, or PipelineEventAvro)
   * @param documentId The document ID (execution ID) to use as CDC document key
   * @param level Event level ("step", "stage", "pipeline")
   * @param eventType Event type ("nodeStart", "nodeStatusUpdate", "nodeEnd")
   */
  protected void sendNodeExecutionCDCEvent(
      SpecificRecordBase originalAvroEvent, String documentId, String level, String eventType) {
    // Stage and step events carry no pipeline enrichment
    sendNodeExecutionCDCEvent(originalAvroEvent, documentId, level, eventType, (PipelineExecutionCDCEnrichment) null);
  }

  /**
   * Sends NodeExecution CDC event to the non-Avro (UDP) topic.
   *
   * <p>For pipeline-level events the caller should pass a fully-populated
   * {@link PipelineExecutionCDCEnrichment} (sourced from {@code planExecutionsSummary}).
   * For step / stage events pass {@code null} — the underlying wrapper ignores the enrichment
   * for non-pipeline Avro types, so the call is always safe.
   *
   * <p>This method is <b>fail-safe</b>: any exception during enrichment extraction or serialisation
   * is caught and logged; the event is silently dropped rather than disrupting the caller's flow
   * (the CDC topic is a best-effort analytics sink, not a transactional channel).
   *
   * @param originalAvroEvent The original Avro event (StepEndEventAvro, StageEventAvro, or PipelineEventAvro)
   * @param documentId        The document ID (execution ID) to use as CDC document key
   * @param level             Event level ("step", "stage", "pipeline")
   * @param eventType         Event type ("nodeStart", "nodeStatusUpdate", "nodeEnd")
   * @param enrichment        Pipeline enrichment from planExecutionsSummary; null for step/stage events
   */
  protected void sendNodeExecutionCDCEvent(SpecificRecordBase originalAvroEvent, String documentId, String level,
      String eventType, PipelineExecutionCDCEnrichment enrichment) {
    // Check if feature flag is enabled
    String accountId = extractAccountId(originalAvroEvent);
    if (accountId != null
        && !featureFlagService.isEnabled(accountId, FeatureName.PIPE_MANUAL_NODE_EXECUTION_CDC_EVENTS)) {
      log.debug("NodeExecution CDC events feature flag is disabled for account: {}, skipping CDC send", accountId);
      return;
    }

    // Check if General Kafka string producer is initialized
    if (!isGeneralKafkaStringProducerInitialized()) {
      log.warn("General Kafka string producer not initialized, cannot send NodeExecution CDC event for documentId: {}",
          documentId);
      return;
    }

    try {
      String database = "harness-pms"; // MongoDB database name
      String collection = NodeExecutionCDCWrapper.determineNodeExecutionCollectionName(level);
      String operationType = NodeExecutionCDCWrapper.determineNodeExecutionCDCOperationType(eventType);

      // Create plain JSON CDC envelope matching the schema; enrichment may be null for step/stage
      String cdcJsonPayload = NodeExecutionCDCWrapper.createPlainJsonCDCEnvelope(
          originalAvroEvent, database, collection, documentId, operationType, enrichment);

      String cdcTopicName = configuration.getCdcNodeExecutionTopicName();
      log.debug("Sending NodeExecution CDC event to topic: {}, database: {}, collection: {}, documentId: {}, level: {}",
          cdcTopicName, database, collection, documentId, level);

      // Use General Kafka String Producer for self-hosted Kafka
      hKafkaGeneralStringProducerOptional.get().send(
          cdcTopicName, cdcJsonPayload, Collections.emptyMap(), documentId, null);
      emitKafkaEventMetric(level, cdcTopicName, eventType, STATUS_SUCCESS);

      log.info("NodeExecution CDC {} event sent successfully to topic: {} for documentId: {}", level, cdcTopicName,
          documentId);
    } catch (Exception ex) {
      log.error("Error sending NodeExecution CDC event for documentId: {}, level: {}, eventType: {}", documentId, level,
          eventType, ex);
      emitKafkaEventMetric(level, configuration.getCdcNodeExecutionTopicName(), eventType, STATUS_FAILURE);
    }
  }
}
