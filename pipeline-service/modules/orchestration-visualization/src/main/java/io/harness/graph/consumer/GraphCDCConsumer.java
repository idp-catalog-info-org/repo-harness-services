/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.consumer;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.graph.consumer.GraphCDCConsumerMetrics.BATCH_PROCESSING_DURATION_MS;
import static io.harness.graph.consumer.GraphCDCConsumerMetrics.BATCH_SIZE;
import static io.harness.graph.consumer.GraphCDCConsumerMetrics.RECORDS_PROCESSED_TOTAL;
import static io.harness.graph.consumer.GraphCDCConsumerMetrics.VERTICES_PER_BATCH;

import static org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG;
import static org.apache.kafka.common.config.SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS;
import static org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM;
import static org.apache.kafka.common.config.SslConfigs.SSL_ENABLED_PROTOCOLS_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_KEYSTORE_TYPE_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_KEY_PASSWORD_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_PROTOCOL_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_PROVIDER_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG;
import static org.apache.kafka.common.config.SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG;
import static org.apache.kafka.common.security.auth.SecurityProtocol.SASL_SSL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.event.streams.model.ChangeDataEvent;
import io.harness.event.streams.serde.GraphStreamSerdes;
import io.harness.graph.service.GraphBatchUpdateDTOs.ModuleInfoUpdate;
import io.harness.graph.service.GraphBatchUpdateDTOs.OutcomeUpdate;
import io.harness.graph.service.GraphBatchUpdateDTOs.StepDetailsUpdate;
import io.harness.graph.service.GraphBatchUpdateDTOs.VertexUpdate;
import io.harness.graph.service.GraphCDCService;
import io.harness.graph.service.impl.AmbianceParser;
import io.harness.graph.service.impl.AmbianceParser.AmbianceResult;
import io.harness.graph.service.impl.ExecutionContextParser;
import io.harness.graph.service.impl.ExecutionContextParser.ExecutionContextResult;
import io.harness.graph.service.impl.MongoTypeConverter;
import io.harness.graph.service.impl.ProtobufBinaryParser;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.serializer.JsonUtils;
import io.harness.serializer.KryoSerializer;

import com.google.api.client.util.Base64;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Kafka consumer that reads CDC events from MongoDB Kafka Connector and writes to normalized PostgreSQL.
 *
 * This consumer:
 * 1. Consumes from a single CDC topic (all collections routed via RegexRouter)
 * 2. Parses MongoDB change stream events in JSON (SimplifiedJson format)
 * 3. For inserts: deserializes fullDocument with Kryo support for compressed fields
 * 4. For updates: uses updateDescription.updatedFields for delta updates, finds rows by document _id
 * 5. Batch upserts to PostgreSQL using normalized columns
 *
 * Key design decisions:
 * - Uses MongoDB Kafka Connector with SimplifiedJson (no Avro, no Schema Registry)
 * - change_streams mode: fullDocument only on insert/replace, null on update
 * - UPDATE events use document _id to find target rows via indexed columns/arrays
 * - At-least-once semantics with idempotent upserts (effectively exactly-once)
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class GraphCDCConsumer implements Managed {
  private static final Duration POLL_TIMEOUT = Duration.ofMillis(3000);

  // On Mongo 8, a single-element $addToSet/$push append surfaces in updatedFields as a positional
  // dotted key (e.g. "nodeExecutionDetailsInfoList.0") carrying just that element, instead of the
  // whole array under the plain field name.
  private static final Pattern STEP_DETAILS_POSITIONAL_KEY_PATTERN =
      Pattern.compile("^nodeExecutionDetailsInfoList\\.(\\d+)$");
  private static final Pattern MODULE_INFO_TRAILING_INDEX_PATTERN = Pattern.compile("^(.+)\\.(\\d+)$");

  private final GraphCDCConsumerConfig config;
  private final GraphCDCService graphService;
  private final KryoSerializer kryoSerializer;
  private final PmsExecutionSummaryService pmsExecutionSummaryService;
  private final MetricService metricService;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  private KafkaConsumer<String, ChangeDataEvent> consumer;

  @Inject
  public GraphCDCConsumer(GraphCDCConsumerConfig config, GraphCDCService graphService, KryoSerializer kryoSerializer,
      PmsExecutionSummaryService pmsExecutionSummaryService, MetricService metricService) {
    this.config = config;
    this.graphService = graphService;
    this.kryoSerializer = kryoSerializer;
    this.pmsExecutionSummaryService = pmsExecutionSummaryService;
    this.metricService = metricService;
  }

  @Override
  public void start() throws Exception {
    if (!config.isEnabled()) {
      log.info("[CDC-PG-CONSUMER] Consumer is disabled, not starting");
      return;
    }

    log.info("[CDC-PG-CONSUMER] Starting GraphCDCConsumer");
    log.info("[CDC-PG-CONSUMER] Topics: {}", config.getTopics());
    log.info("[CDC-PG-CONSUMER] Bootstrap servers: {}", config.getKafkaBaseConfig().getBootstrapServers());
    log.info("[CDC-PG-CONSUMER] Consumer group: {}", config.getConsumerGroup());

    consumer = createConsumer();
    consumer.subscribe(config.getTopics());

    running.set(true);
    executorService.submit(this::pollLoop);

    log.info("[CDC-PG-CONSUMER] Consumer started successfully");
  }

  @Override
  public void stop() throws Exception {
    log.info("[CDC-PG-CONSUMER] Stopping GraphCDCConsumer");
    running.set(false);

    if (consumer != null) {
      consumer.wakeup();
    }

    executorService.shutdown();
    if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
      log.warn("[CDC-PG-CONSUMER] Executor did not terminate in time, forcing shutdown");
      executorService.shutdownNow();
    }

    if (consumer != null) {
      consumer.close();
    }

    log.info("[CDC-PG-CONSUMER] Consumer stopped");
  }

  private KafkaConsumer<String, ChangeDataEvent> createConsumer() {
    KafkaBaseConfig kafkaBaseConfig = config.getKafkaBaseConfig();
    Properties props = new Properties();

    // Security protocol and SASL settings
    props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, kafkaBaseConfig.getSecurityProtocol());
    if (kafkaBaseConfig.getSecurityProtocol().startsWith("SASL")) {
      props.put(SASL_MECHANISM, kafkaBaseConfig.getSaslMechanism());
      props.put(SASL_JAAS_CONFIG, kafkaBaseConfig.getSaslJaasConfig());
      if (SASL_SSL.name().equals(kafkaBaseConfig.getSecurityProtocol())) {
        props.put(
            SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, kafkaBaseConfig.getSslEndpointIdentificationAlgorithm());
      }
    }
    if (isNotEmpty(kafkaBaseConfig.getSaslLoginCallbackHandlerClass())) {
      props.put(SASL_LOGIN_CALLBACK_HANDLER_CLASS, kafkaBaseConfig.getSaslLoginCallbackHandlerClass());
    }

    // Configure SSL/TLS properties
    if (kafkaBaseConfig.getSecurityProtocol().endsWith("SSL")) {
      if (isNotEmpty(kafkaBaseConfig.getSslTruststoreLocation())) {
        props.put(SSL_TRUSTSTORE_LOCATION_CONFIG, kafkaBaseConfig.getSslTruststoreLocation());
      }
      if (isNotEmpty(kafkaBaseConfig.getSslTruststorePassword())) {
        props.put(SSL_TRUSTSTORE_PASSWORD_CONFIG, kafkaBaseConfig.getSslTruststorePassword());
      }
      if (isNotEmpty(kafkaBaseConfig.getSslTruststoreType())) {
        props.put(SSL_TRUSTSTORE_TYPE_CONFIG, kafkaBaseConfig.getSslTruststoreType());
      }
      if (isNotEmpty(kafkaBaseConfig.getSslKeystoreLocation())) {
        props.put(SSL_KEYSTORE_LOCATION_CONFIG, kafkaBaseConfig.getSslKeystoreLocation());
      }
      if (isNotEmpty(kafkaBaseConfig.getSslKeystorePassword())) {
        props.put(SSL_KEYSTORE_PASSWORD_CONFIG, kafkaBaseConfig.getSslKeystorePassword());
      }
      if (isNotEmpty(kafkaBaseConfig.getSslKeystoreType())) {
        props.put(SSL_KEYSTORE_TYPE_CONFIG, kafkaBaseConfig.getSslKeystoreType());
      }
      if (isNotEmpty(kafkaBaseConfig.getSslKeyPassword())) {
        props.put(SSL_KEY_PASSWORD_CONFIG, kafkaBaseConfig.getSslKeyPassword());
      }
      if (isNotEmpty(kafkaBaseConfig.getSslProtocol())) {
        props.put(SSL_PROTOCOL_CONFIG, kafkaBaseConfig.getSslProtocol());
      }
      if (isNotEmpty(kafkaBaseConfig.getSslEnabledProtocols())) {
        props.put(SSL_ENABLED_PROTOCOLS_CONFIG, kafkaBaseConfig.getSslEnabledProtocols());
      }
      if (isNotEmpty(kafkaBaseConfig.getSslProvider())) {
        props.put(SSL_PROVIDER_CONFIG, kafkaBaseConfig.getSslProvider());
      }
    }

    // Bootstrap servers and consumer settings
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBaseConfig.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, config.getConsumerGroup());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, GraphStreamSerdes.ChangeDataEventDeserializer.class.getName());

    // Manual offset commit for at-least-once semantics
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    // Start from earliest if no committed offset
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    // Batch settings
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, config.getMaxPollRecordCount());
    props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, config.getFetchMinBytes());
    props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, config.getFetchMaxWaitMs());
    props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, config.getMaxPollIntervalMs());
    props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, config.getMaxPartitionFetchBytes());

    return new KafkaConsumer<>(props);
  }

  private void pollLoop() {
    log.info("[CDC-PG-CONSUMER] Poll loop started");

    try {
      while (running.get()) {
        ConsumerRecords<String, ChangeDataEvent> records = consumer.poll(POLL_TIMEOUT);

        if (records.isEmpty()) {
          continue;
        }

        long startTime = System.currentTimeMillis();
        int totalEvents = records.count();
        metricService.recordMetric(BATCH_SIZE, totalEvents);
        log.debug("[CDC-PG-CONSUMER] Received {} records from Kafka", totalEvents);

        // Accumulators for batch operations
        Map<String, VertexUpdateAccumulator> vertexUpdates = new HashMap<>();
        List<OutcomeUpdate> outcomeUpdates = new ArrayList<>();
        List<StepDetailsUpdate> stepDetailsUpdates = new ArrayList<>();
        List<ModuleInfoUpdate> moduleInfoUpdates = new ArrayList<>();
        List<String> barrierStepParentIds = new ArrayList<>();

        // Counters for logging
        int nodeExecCreateCount = 0;
        int nodeExecUpdateCount = 0;
        int stepDetailsCount = 0;
        int outcomeCount = 0;
        int graphUpdateInfoCount = 0;

        for (ConsumerRecord<String, ChangeDataEvent> record : records) {
          ChangeDataEvent event = record.value();
          if (event == null || event.isDelete()) {
            continue;
          }

          try {
            String collection = event.getCollection();
            if (collection == null) {
              continue;
            }

            switch (collection) {
              case "nodeExecutions":
                if (event.isCreate()) {
                  accumulateNodeExecutionCreate(event, vertexUpdates, barrierStepParentIds);
                  nodeExecCreateCount++;
                  recordProcessed(collection, "create");
                } else if (event.isUpdate() && event.hasUpdatedFields()) {
                  accumulateNodeExecutionUpdate(event, vertexUpdates);
                  nodeExecUpdateCount++;
                  recordProcessed(collection, "update");
                }
                break;

              case "nodeExecutionsInfo":
                if (event.isCreate()) {
                  accumulateNodeExecutionsInfoCreate(event, stepDetailsUpdates);
                  recordProcessed(collection, "create");
                } else if (event.isUpdate()) {
                  accumulateNodeExecutionsInfoUpdate(event, stepDetailsUpdates);
                  recordProcessed(collection, "update");
                }
                stepDetailsCount++;
                break;

              case "outcomeInstances":
                if (event.isCreate()) {
                  accumulateOutcomeInstanceCreate(event, outcomeUpdates);
                  recordProcessed(collection, "create");
                } else if (event.isUpdate()) {
                  accumulateOutcomeInstanceUpdate(event, outcomeUpdates);
                  recordProcessed(collection, "update");
                }
                outcomeCount++;
                break;

              case "graphUpdateInfo":
                if (event.isCreate()) {
                  accumulateGraphUpdateInfoCreate(event, moduleInfoUpdates);
                  recordProcessed(collection, "create");
                } else if (event.isUpdate()) {
                  accumulateGraphUpdateInfoUpdate(event, moduleInfoUpdates);
                  recordProcessed(collection, "update");
                }
                graphUpdateInfoCount++;
                break;

              default:
                log.debug("[CDC-PG-CONSUMER] Unknown collection: {}", collection);
            }
          } catch (Exception e) {
            log.error("[CDC-PG-CONSUMER] Failed to process record: {}", e.getMessage(), e);
          }
        }

        // Execute batch operations and commit only on success.
        // If batch write fails after retries, skip commit so Kafka replays these records.
        try {
          executeBatchUpdates(
              vertexUpdates, outcomeUpdates, stepDetailsUpdates, moduleInfoUpdates, barrierStepParentIds);
          consumer.commitSync();
        } catch (Exception e) {
          log.error("[CDC-PG-CONSUMER] Batch write failed after retries, skipping commit. "
                  + "Records will be replayed on next poll. Error: {}",
              e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;
        metricService.recordMetric(VERTICES_PER_BATCH, vertexUpdates.size());
        metricService.recordMetric(BATCH_PROCESSING_DURATION_MS, duration);
        log.info("[CDC-PG-CONSUMER] Processed {} records in {}ms: {} creates, {} updates, {} stepDetails, {} outcomes, "
                + "{} graphUpdateInfo (batched to {} vertices)",
            totalEvents, duration, nodeExecCreateCount, nodeExecUpdateCount, stepDetailsCount, outcomeCount,
            graphUpdateInfoCount, vertexUpdates.size());
      }
    } catch (WakeupException e) {
      if (running.get()) {
        log.error("[CDC-PG-CONSUMER] Unexpected wakeup", e);
      }
    } catch (Exception e) {
      log.error("[CDC-PG-CONSUMER] Error in poll loop", e);
    }

    log.info("[CDC-PG-CONSUMER] Poll loop ended");
  }

  /**
   * Helper class to accumulate updates for a single vertex.
   * Later updates override earlier ones for the same field.
   */
  private static class VertexUpdateAccumulator {
    String planExecutionId;
    String nodeExecutionId;
    String accountIdentifier;
    Map<String, Object> fields = new HashMap<>();

    VertexUpdate toVertexUpdate() {
      return VertexUpdate.builder()
          .planExecutionId(planExecutionId)
          .nodeExecutionId(nodeExecutionId)
          .accountIdentifier(accountIdentifier)
          .updatedFields(fields)
          .build();
    }
  }

  /**
   * Execute all accumulated batch updates in a single database round-trip per type.
   */
  private void executeBatchUpdates(Map<String, VertexUpdateAccumulator> vertexUpdates,
      List<OutcomeUpdate> outcomeUpdates, List<StepDetailsUpdate> stepDetailsUpdates,
      List<ModuleInfoUpdate> moduleInfoUpdates, List<String> barrierStepParentIds) {
    if (!vertexUpdates.isEmpty()) {
      List<VertexUpdate> updates =
          vertexUpdates.values().stream().map(VertexUpdateAccumulator::toVertexUpdate).toList();
      graphService.batchUpdateVertexFields(updates);
    }

    if (!outcomeUpdates.isEmpty()) {
      graphService.batchAppendOutcomes(outcomeUpdates);
    }

    if (!stepDetailsUpdates.isEmpty()) {
      graphService.batchUpdateStepDetails(stepDetailsUpdates);
    }

    if (!moduleInfoUpdates.isEmpty()) {
      graphService.batchUpdateModuleInfo(moduleInfoUpdates);
    }

    if (!barrierStepParentIds.isEmpty()) {
      graphService.markBarrierParents(barrierStepParentIds);
    }
  }

  // ============================================
  // nodeExecutions handlers
  // ============================================

  /**
   * Accumulate nodeExecution create event. fullDocument has the complete document.
   */
  private void accumulateNodeExecutionCreate(
      ChangeDataEvent event, Map<String, VertexUpdateAccumulator> vertexUpdates, List<String> barrierStepParentIds) {
    String planExecutionId = event.extractPlanExecutionId();
    String nodeExecutionId = event.getDocumentId();

    if (planExecutionId == null || nodeExecutionId == null) {
      log.warn("[CDC-PG-CONSUMER] Missing planExecutionId or nodeExecutionId in create event");
      return;
    }

    Map<String, Object> doc = event.getFullDocument();
    if (doc == null) {
      return;
    }

    String accountIdentifier = extractAccountIdentifier(doc);
    Map<String, Object> inflatedDoc = inflateCompressedFields(doc);

    VertexUpdateAccumulator accumulator = vertexUpdates.computeIfAbsent(nodeExecutionId, k -> {
      VertexUpdateAccumulator acc = new VertexUpdateAccumulator();
      acc.nodeExecutionId = nodeExecutionId;
      return acc;
    });

    accumulator.planExecutionId = planExecutionId;
    if (accountIdentifier != null) {
      accumulator.accountIdentifier = accountIdentifier;
    }
    accumulator.fields.putAll(inflatedDoc);

    // Detect barrier steps and extract stage ID from executionContext.levels (or ambiance.levels for legacy)
    Object stepTypeObj = inflatedDoc.get("stepType");
    if (stepTypeObj != null) {
      // stepType is an object: {type: "Barrier", stepCategory: "STEP"}
      String stepType = null;
      if (stepTypeObj instanceof Map) {
        @SuppressWarnings("unchecked") Map<String, Object> stepTypeMap = (Map<String, Object>) stepTypeObj;
        Object typeObj = stepTypeMap.get("type");
        stepType = typeObj != null ? extractStringValue(typeObj) : null;
      }

      if ("Barrier".equals(stepType)) {
        // Extract stage ID directly from executionContext/ambiance levels instead of querying later
        String stageNodeExecutionId = extractStageNodeExecutionIdFromContext(doc);
        if (stageNodeExecutionId != null && !stageNodeExecutionId.isEmpty()) {
          barrierStepParentIds.add(stageNodeExecutionId);
          log.debug("[CDC-PG-CONSUMER] Detected barrier step {} in stage {}", nodeExecutionId, stageNodeExecutionId);
        }
      }
    }
  }

  /**
   * Accumulate nodeExecution update event. Only delta fields from updateDescription.
   * Uses documentKey._id as nodeExecutionId. planExecutionId/accountIdentifier not needed
   * since the row should already exist from the CREATE event.
   */
  private void accumulateNodeExecutionUpdate(
      ChangeDataEvent event, Map<String, VertexUpdateAccumulator> vertexUpdates) {
    String nodeExecutionId = event.getDocumentId();

    if (nodeExecutionId == null) {
      log.warn("[CDC-PG-CONSUMER] Missing nodeExecutionId in update event");
      return;
    }

    Map<String, Object> updatedFields = event.getUpdatedFields();
    if (updatedFields == null || updatedFields.isEmpty()) {
      return;
    }

    Map<String, Object> inflatedFields = inflateCompressedFields(updatedFields);

    VertexUpdateAccumulator accumulator = vertexUpdates.computeIfAbsent(nodeExecutionId, k -> {
      VertexUpdateAccumulator acc = new VertexUpdateAccumulator();
      acc.nodeExecutionId = nodeExecutionId;
      return acc;
    });

    accumulator.fields.putAll(inflatedFields);
  }

  // ============================================
  // nodeExecutionsInfo handlers
  // ============================================

  /**
   * Accumulate nodeExecutionsInfo CREATE event.
   * fullDocument has nodeExecutionId, nodeExecutionDetailsInfoList, strategyMetadata, retryNodeMetadata.
   * Stores documentKey._id as node_executions_info_id on the vertex.
   */
  @SuppressWarnings("unchecked")
  private void accumulateNodeExecutionsInfoCreate(ChangeDataEvent event, List<StepDetailsUpdate> stepDetailsUpdates) {
    Map<String, Object> doc = event.getFullDocument();
    if (doc == null) {
      return;
    }

    String nodeExecutionId = (String) doc.get("nodeExecutionId");
    if (nodeExecutionId == null) {
      return;
    }
    String documentId = event.getDocumentId();
    Object detailsList = doc.get("nodeExecutionDetailsInfoList");
    String stepDetailsJson = (detailsList instanceof java.util.List)
        ? inflateAndSerializeStepDetails((java.util.List<Object>) detailsList)
        : null;
    String strategyMetadataJson = serializeStrategyMetadata(doc.get("strategyMetadata"));
    String retryNodeMetadataJson = serializeRetryNodeMetadata(doc.get("retryNodeMetadata"));

    if (stepDetailsJson == null && strategyMetadataJson == null && retryNodeMetadataJson == null) {
      return;
    }

    stepDetailsUpdates.add(StepDetailsUpdate.builder()
                               .nodeExecutionId(nodeExecutionId)
                               .documentId(documentId)
                               .stepDetailsJson(stepDetailsJson)
                               .strategyMetadataJson(strategyMetadataJson)
                               .retryNodeMetadataJson(retryNodeMetadataJson)
                               .isCreate(true)
                               .build());
  }

  /**
   * Accumulate nodeExecutionsInfo UPDATE event.
   * Only updateDescription.updatedFields is available. Uses documentKey._id
   * to find the vertex via node_executions_info_id column.
   */
  @SuppressWarnings("unchecked")
  private void accumulateNodeExecutionsInfoUpdate(ChangeDataEvent event, List<StepDetailsUpdate> stepDetailsUpdates) {
    Map<String, Object> updatedFields = event.getUpdatedFields();
    if (updatedFields == null) {
      return;
    }

    String documentId = event.getDocumentId();
    if (documentId == null) {
      return;
    }
    Object detailsList = updatedFields.get("nodeExecutionDetailsInfoList");
    String stepDetailsJson = (detailsList instanceof java.util.List)
        ? inflateAndSerializeStepDetails((java.util.List<Object>) detailsList)
        : null;
    Map<String, String> stepDetailsElementsByName = extractPositionalStepDetailsElements(updatedFields);
    String strategyMetadataJson = updatedFields.containsKey("strategyMetadata")
        ? serializeStrategyMetadata(updatedFields.get("strategyMetadata"))
        : null;
    String retryNodeMetadataJson = updatedFields.containsKey("retryNodeMetadata")
        ? serializeRetryNodeMetadata(updatedFields.get("retryNodeMetadata"))
        : null;

    if (stepDetailsJson == null && stepDetailsElementsByName.isEmpty() && strategyMetadataJson == null
        && retryNodeMetadataJson == null) {
      return;
    }

    stepDetailsUpdates.add(StepDetailsUpdate.builder()
                               .documentId(documentId)
                               .stepDetailsJson(stepDetailsJson)
                               .stepDetailsElementsByName(stepDetailsElementsByName)
                               .strategyMetadataJson(strategyMetadataJson)
                               .retryNodeMetadataJson(retryNodeMetadataJson)
                               .isCreate(false)
                               .build());
  }

  /**
   * Extract single-element positional appends to nodeExecutionDetailsInfoList
   * (e.g. "nodeExecutionDetailsInfoList.0") that Mongo 8 surfaces instead of the whole-array key.
   * Keyed by the element's "name" field (not its array index) so the merge into the
   * name-keyed step_details JSONB object (see GraphCDCServiceImpl#applyStepDetailsPositionalUpdate)
   * lands under the correct key instead of a bogus numeric one.
   */
  private Map<String, String> extractPositionalStepDetailsElements(Map<String, Object> updatedFields) {
    Map<String, String> elementsByName = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : updatedFields.entrySet()) {
      Matcher matcher = STEP_DETAILS_POSITIONAL_KEY_PATTERN.matcher(entry.getKey());
      if (!matcher.matches()) {
        continue;
      }
      Map.Entry<String, Object> nameAndValue = extractStepDetailNameAndValue(entry.getValue());
      if (nameAndValue == null) {
        log.warn(
            "[CDC-PG-CONSUMER] Skipping unparseable nodeExecutionDetailsInfoList element at path: {}", entry.getKey());
        continue;
      }
      elementsByName.put(nameAndValue.getKey(), JsonUtils.asJson(nameAndValue.getValue()));
    }
    return elementsByName;
  }

  /**
   * Serialize strategyMetadata (Kryo-encoded protobuf) to a JSON string.
   * Returns null if unparseable / absent.
   */
  private String serializeStrategyMetadata(Object value) {
    if (value == null) {
      return null;
    }
    org.jooq.JSONB jsonb = ProtobufBinaryParser.parseToJsonb(value, StrategyMetadata::parseFrom);
    return jsonb == null ? null : jsonb.data();
  }

  /**
   * Serialize retryNodeMetadata (a plain BSON map with nested ExecutionTriggerInfo) to JSON.
   * Returns null if absent.
   */
  private String serializeRetryNodeMetadata(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return JsonUtils.asJson(value);
    } catch (Exception e) {
      log.warn("[CDC-PG-CONSUMER] Failed to serialize retryNodeMetadata: {}", e.getMessage());
      return null;
    }
  }

  // ============================================
  // outcomeInstances handlers
  // ============================================

  /**
   * Accumulate outcomeInstance CREATE event.
   * fullDocument has all fields. Stores data keyed by documentKey._id in outcome_documents JSONB.
   */
  @SuppressWarnings("unchecked")
  private void accumulateOutcomeInstanceCreate(ChangeDataEvent event, List<OutcomeUpdate> outcomeUpdates) {
    Map<String, Object> doc = event.getFullDocument();
    if (doc == null) {
      return;
    }

    String planExecutionId = (String) doc.get("planExecutionId");
    String documentId = event.getDocumentId();

    Map<String, Object> producedBy = (Map<String, Object>) doc.get("producedBy");
    if (producedBy == null) {
      return;
    }

    String nodeExecutionId = (String) producedBy.get("runtimeId");
    if (nodeExecutionId == null) {
      return;
    }

    String outcomeName = (String) doc.get("name");
    if (outcomeName == null) {
      outcomeName = "default";
    }

    // outcomeValue (PmsOutcome/OrchestrationMap) is stored as Kryo-compressed Binary in MongoDB.
    // With SimplifiedJson, it comes as a plain base64 string. Fall back to deprecated 'outcome' field.
    Object outcome = inflateIfCompressed(doc.get("outcomeValue"));
    if (outcome == null) {
      outcome = doc.get("outcome");
    }
    String outcomeJson = outcome != null ? JsonUtils.asJson(outcome) : null;

    if (outcomeJson != null) {
      outcomeUpdates.add(OutcomeUpdate.builder()
                             .planExecutionId(planExecutionId)
                             .nodeExecutionId(nodeExecutionId)
                             .documentId(documentId)
                             .outcomeName(outcomeName)
                             .outcomeJson(outcomeJson)
                             .isCreate(true)
                             .build());
    }
  }

  /**
   * Accumulate outcomeInstance UPDATE event.
   * Only delta fields available. Uses documentKey._id to find vertex via outcome_instance_ids array.
   */
  private void accumulateOutcomeInstanceUpdate(ChangeDataEvent event, List<OutcomeUpdate> outcomeUpdates) {
    Map<String, Object> updatedFields = event.getUpdatedFields();
    if (updatedFields == null) {
      return;
    }

    String documentId = event.getDocumentId();
    if (documentId == null) {
      return;
    }

    // Build partial update: only include fields that changed
    // outcome_documents JSONB stores {outcomeName: outcomeData} matching parseOutcomeDocuments format
    Object outcome = inflateIfCompressed(updatedFields.get("outcomeValue"));
    if (outcome == null) {
      outcome = updatedFields.get("outcome");
    }
    String outcomeName = (String) updatedFields.get("name");

    if (outcome != null) {
      String outcomeJson = JsonUtils.asJson(outcome);
      outcomeUpdates.add(OutcomeUpdate.builder()
                             .documentId(documentId)
                             .outcomeName(outcomeName)
                             .outcomeJson(outcomeJson)
                             .isCreate(false)
                             .build());
    }
  }

  // ============================================
  // graphUpdateInfo handlers
  // ============================================

  /**
   * Accumulate graphUpdateInfo CREATE event.
   * fullDocument has planExecutionId and executionSummaryUpdateInfo.
   * Stores data keyed by documentKey._id in module_info JSONB.
   */
  @SuppressWarnings("unchecked")
  private void accumulateGraphUpdateInfoCreate(ChangeDataEvent event, List<ModuleInfoUpdate> moduleInfoUpdates) {
    Map<String, Object> doc = event.getFullDocument();
    if (doc == null) {
      return;
    }

    String planExecutionId = (String) doc.get("planExecutionId");
    String documentId = event.getDocumentId();

    Map<String, Object> updateInfo = (Map<String, Object>) doc.get("executionSummaryUpdateInfo");
    if (updateInfo == null) {
      return;
    }

    String stepCategoryStr = (String) updateInfo.get("stepCategory");
    String stageUuid = (String) updateInfo.get("stageUuid");
    Map<String, Object> moduleInfo = (Map<String, Object>) updateInfo.get("moduleInfo");

    if (moduleInfo == null || moduleInfo.isEmpty()) {
      return;
    }

    // Store just the moduleInfo map directly (e.g., {cd: {...}}) to match the read path format
    // The read path (parseModuleInfo) expects Map<String, LinkedHashMap<String, Object>> keyed by module name
    if ("STAGE".equals(stepCategoryStr) && stageUuid != null) {
      moduleInfoUpdates.add(ModuleInfoUpdate.builder()
                                .planExecutionId(planExecutionId)
                                .documentId(documentId)
                                .stageUuid(stageUuid)
                                .moduleInfo(moduleInfo)
                                .isPipelineLevel(false)
                                .isCreate(true)
                                .build());
    } else if ("PIPELINE".equals(stepCategoryStr) && planExecutionId != null) {
      moduleInfoUpdates.add(ModuleInfoUpdate.builder()
                                .planExecutionId(planExecutionId)
                                .documentId(documentId)
                                .moduleInfo(moduleInfo)
                                .isPipelineLevel(true)
                                .isCreate(true)
                                .build());
    }
  }

  /**
   * Accumulate graphUpdateInfo UPDATE event.
   * Only delta fields available. Uses documentKey._id to find vertex via graph_update_info_ids array.
   */
  @SuppressWarnings("unchecked")
  private void accumulateGraphUpdateInfoUpdate(ChangeDataEvent event, List<ModuleInfoUpdate> moduleInfoUpdates) {
    Map<String, Object> updatedFields = event.getUpdatedFields();
    if (updatedFields == null) {
      return;
    }

    String documentId = event.getDocumentId();
    if (documentId == null) {
      return;
    }

    // For UPDATE, extract just the moduleInfo map (e.g., {cd: {...}}) to match read path format.
    // Updates may come as full object or dot-notation paths.
    Map<String, Object> moduleInfoMap = new HashMap<>();

    Map<String, Object> updateInfo = (Map<String, Object>) updatedFields.get("executionSummaryUpdateInfo");
    if (updateInfo != null) {
      Object moduleInfo = updateInfo.get("moduleInfo");
      if (moduleInfo instanceof Map) {
        moduleInfoMap.putAll((Map<String, Object>) moduleInfo);
      }
    }

    // Handle dot-notation updates like "executionSummaryUpdateInfo.moduleInfo.cd.envIdentifiers"
    // We need to reconstruct the nested moduleInfo structure from these flat keys.
    String moduleInfoPrefix = "executionSummaryUpdateInfo.moduleInfo.";
    for (Map.Entry<String, Object> entry : updatedFields.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(moduleInfoPrefix)) {
        // e.g., key = "executionSummaryUpdateInfo.moduleInfo.cd.envIdentifiers"
        // subPath = "cd.envIdentifiers" → moduleName = "cd", field = "envIdentifiers"
        String subPath = key.substring(moduleInfoPrefix.length());
        int dotIdx = subPath.indexOf('.');
        if (dotIdx > 0) {
          String moduleName = subPath.substring(0, dotIdx);
          String fieldName = subPath.substring(dotIdx + 1);
          Map<String, Object> moduleData =
              (Map<String, Object>) moduleInfoMap.computeIfAbsent(moduleName, k -> new HashMap<>());

          // On Mongo 8, a single-element $addToSet(...).each(...) append to a module-level
          // Collection field (e.g. envIdentifiers) surfaces as a positional key
          // (e.g. "cd.envIdentifiers.0") instead of the whole-array key ("cd.envIdentifiers").
          // Reconstruct it as a single-element list so it union-merges correctly downstream.
          Matcher indexMatcher = MODULE_INFO_TRAILING_INDEX_PATTERN.matcher(fieldName);
          if (indexMatcher.matches()) {
            String arrayFieldName = indexMatcher.group(1);
            List<Object> elements = (List<Object>) moduleData.computeIfAbsent(arrayFieldName, k -> new ArrayList<>());
            elements.add(entry.getValue());
          } else {
            moduleData.put(fieldName, entry.getValue());
          }
        } else {
          // Direct module-level update like "executionSummaryUpdateInfo.moduleInfo.cd"
          moduleInfoMap.put(subPath, entry.getValue());
        }
      } else if (key.equals("executionSummaryUpdateInfo.moduleInfo") && entry.getValue() instanceof Map) {
        moduleInfoMap.putAll((Map<String, Object>) entry.getValue());
      }
    }

    if (!moduleInfoMap.isEmpty()) {
      moduleInfoUpdates.add(
          ModuleInfoUpdate.builder().documentId(documentId).moduleInfo(moduleInfoMap).isCreate(false).build());
    }
  }

  // ============================================
  // planExecutions handlers
  // ============================================

  /**
   * Process a planExecution CDC event.
   * Extracts status, endTs, and failureInfo from updated fields and updates
   * PipelineExecutionSummaryEntity via PmsExecutionSummaryService.
   *
   * With change.stream.full.document=default:
   * - CREATE: fullDocument available
   * - UPDATE: only updatedFields available (delta)
   *
   * @return true if the event was processed, false if skipped
   */
  @SuppressWarnings("unchecked")
  private boolean processPlanExecution(ChangeDataEvent event) {
    String planExecutionId = event.getDocumentId();
    if (planExecutionId == null) {
      return false;
    }

    String statusStr = null;
    Long endTs = null;
    Object failureInfoObj = null;

    if (event.isCreate()) {
      Map<String, Object> doc = event.getFullDocument();
      if (doc == null) {
        return false;
      }
      Object statusObj = doc.get("status");
      if (statusObj != null) {
        statusStr = statusObj.toString();
      }
      endTs = MongoTypeConverter.extractLongFromExtendedJson(doc.get("endTs"));
      failureInfoObj = doc.get("failureInfo");
    } else if (event.isUpdate()) {
      Map<String, Object> updatedFields = event.getUpdatedFields();
      if (updatedFields == null || !updatedFields.containsKey("status")) {
        // Only process if status actually changed
        return false;
      }
      Object statusObj = updatedFields.get("status");
      if (statusObj != null) {
        statusStr = statusObj.toString();
      }
      if (updatedFields.containsKey("endTs")) {
        endTs = MongoTypeConverter.extractLongFromExtendedJson(updatedFields.get("endTs"));
      }
      if (updatedFields.containsKey("failureInfo")) {
        failureInfoObj = updatedFields.get("failureInfo");
      }
    } else {
      return false;
    }

    if (statusStr == null) {
      return false;
    }

    FailureInfo failureInfo = extractFailureInfoFromCDC(failureInfoObj);

    try {
      Status status = Status.valueOf(statusStr);
      pmsExecutionSummaryService.updateStatusFromCDC(planExecutionId, status, endTs, failureInfo);
      return true;
    } catch (IllegalArgumentException e) {
      log.warn("[CDC-PG-CONSUMER] Unknown plan execution status: {}", statusStr);
    } catch (Exception e) {
      log.error(
          "[CDC-PG-CONSUMER] Failed to update plan execution status for {}: {}", planExecutionId, e.getMessage(), e);
    }
    return false;
  }

  /**
   * Extract and deserialize FailureInfo protobuf from a CDC field value.
   * Delegates binary extraction to {@link ProtobufBinaryParser}.
   */
  private FailureInfo extractFailureInfoFromCDC(Object failureInfoObj) {
    return ProtobufBinaryParser.parseToObject(failureInfoObj, FailureInfo::parseFrom).orElse(null);
  }

  // ============================================
  // Helper methods
  // ============================================

  /**
   * Inflate step details list and serialize to a name-keyed JSON object, matching the
   * shape graph_vertex.step_details is read back in (JsonbParserUtils#parseStepDetails'
   * "direct map format" branch) -- NOT a JSON array. Each nodeExecutionDetailsInfoList
   * element is {name, stepDetails, uuid}; only name/stepDetails are kept.
   */
  private String inflateAndSerializeStepDetails(java.util.List<Object> list) {
    Map<String, Object> byName = new LinkedHashMap<>();

    for (Object item : list) {
      Map.Entry<String, Object> nameAndValue = extractStepDetailNameAndValue(item);
      if (nameAndValue != null) {
        byName.put(nameAndValue.getKey(), nameAndValue.getValue());
      }
    }

    return JsonUtils.asJson(byName);
  }

  /**
   * Extract the (name, stepDetails) pair from a single nodeExecutionDetailsInfoList element,
   * decompressing its Kryo-encoded stepDetails sub-field if present. Returns null if the item
   * isn't a Map, or is missing a name/stepDetails value.
   */
  @SuppressWarnings("unchecked")
  private Map.Entry<String, Object> extractStepDetailNameAndValue(Object item) {
    if (!(item instanceof Map)) {
      return null;
    }
    Map<String, Object> itemMap = (Map<String, Object>) item;
    Object nameObj = itemMap.get("name");
    if (!(nameObj instanceof String)) {
      return null;
    }
    Object stepDetails = itemMap.get("stepDetails");
    if (stepDetails == null) {
      return null;
    }
    Object inflatedStepDetails = inflateIfCompressed(stepDetails);
    if (inflatedStepDetails == null) {
      return null;
    }
    return Map.entry((String) nameObj, inflatedStepDetails);
  }

  /**
   * Extract account identifier from the document's executionContext.setupAbstractions.accountId
   * (or ambiance.setupAbstractions.accountId for legacy records).
   * Uses proper protobuf deserialization via ExecutionContextParser/AmbianceParser.
   */
  private String extractAccountIdentifier(Map<String, Object> doc) {
    if (doc == null) {
      return null;
    }

    // Try executionContext first (current field)
    Object executionContextObj = doc.get("executionContext");
    if (executionContextObj != null) {
      try {
        Optional<ExecutionContextResult> result = ExecutionContextParser.parse(executionContextObj);
        if (result.isPresent() && result.get().hasAccountId()) {
          return result.get().getAccountId();
        }
      } catch (Exception e) {
        log.debug("[CDC-PG-CONSUMER] Failed to parse executionContext for accountId: {}", e.getMessage());
      }
    }

    // Fall back to ambiance (legacy field)
    Object ambianceObj = doc.get("ambiance");
    if (ambianceObj != null) {
      try {
        Optional<io.harness.graph.service.impl.AmbianceParser.AmbianceResult> result =
            io.harness.graph.service.impl.AmbianceParser.parse(ambianceObj);
        if (result.isPresent() && result.get().hasAccountId()) {
          return result.get().getAccountId();
        }
      } catch (Exception e) {
        log.debug("[CDC-PG-CONSUMER] Failed to parse ambiance for accountId: {}", e.getMessage());
      }
    }

    return null;
  }

  /**
   * Extract the stage node execution ID from executionContext.levels (or ambiance.levels for legacy)
   * by deserializing the protobuf and traversing levels to find the STAGE level.
   * This avoids the need to query the database to walk up the parent chain.
   *
   * @param doc The full nodeExecution document from CDC CREATE event
   * @return The runtimeId of the STAGE level, or null if not found
   */
  private String extractStageNodeExecutionIdFromContext(Map<String, Object> doc) {
    if (doc == null) {
      return null;
    }

    // Try executionContext first (current field)
    Object executionContextObj = doc.get("executionContext");
    if (executionContextObj != null) {
      String stageId = extractStageFromExecutionContext(executionContextObj);
      if (stageId != null) {
        return stageId;
      }
    }

    // Fall back to ambiance (legacy field)
    Object ambianceObj = doc.get("ambiance");
    if (ambianceObj != null) {
      return extractStageFromAmbiance(ambianceObj);
    }

    return null;
  }

  /**
   * Extract stage ID from ExecutionContext protobuf.
   */
  private String extractStageFromExecutionContext(Object executionContextObj) {
    try {
      Optional<ExecutionContextResult> result = ExecutionContextParser.parse(executionContextObj);

      if (result.isPresent() && result.get().hasExecutionContext()) {
        ExecutionContext executionContext = result.get().getExecutionContext();
        return findStageRuntimeIdInLevels(executionContext.getLevelsList());
      }
    } catch (Exception e) {
      log.debug("[CDC-PG-CONSUMER] Failed to parse executionContext for barrier stage extraction: {}", e.getMessage());
    }
    return null;
  }

  /**
   * Extract stage ID from Ambiance protobuf (legacy field).
   */
  private String extractStageFromAmbiance(Object ambianceObj) {
    try {
      Optional<AmbianceResult> result = AmbianceParser.parse(ambianceObj);

      if (result.isPresent() && result.get().getAmbiance() != null) {
        Ambiance ambiance = result.get().getAmbiance();
        return findStageRuntimeIdInLevels(ambiance.getLevelsList());
      }
    } catch (Exception e) {
      log.debug("[CDC-PG-CONSUMER] Failed to parse ambiance for barrier stage extraction: {}", e.getMessage());
    }
    return null;
  }

  /**
   * Find the STAGE level's runtimeId from a list of levels.
   * Levels are ordered from outermost (pipeline) to innermost (current step).
   */
  private String findStageRuntimeIdInLevels(java.util.List<io.harness.pms.contracts.ambiance.Level> levels) {
    if (levels == null || levels.isEmpty()) {
      return null;
    }

    for (Level level : levels) {
      if (level.hasStepType()
          && level.getStepType().getStepCategory() == io.harness.pms.contracts.steps.StepCategory.STAGE) {
        return level.getRuntimeId();
      }
    }

    return null;
  }

  /**
   * Extract string value from CDC document field (handles both String and extended JSON).
   */
  private String extractStringValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String) {
      return (String) value;
    }
    return value.toString();
  }

  /**
   * Inflate Kryo-compressed fields in the document.
   *
   * Fields that are stored as compressed Kryo binary (Base64 encoded):
   * - resolvedParams (PmsStepParameters extends OrchestrationMap)
   * - resolvedInputs (PmsStepParameters)
   * - progressData (OrchestrationMap)
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> inflateCompressedFields(Map<String, Object> doc) {
    if (doc == null) {
      return null;
    }

    Map<String, Object> result = new HashMap<>(doc);

    String[] compressedFields = {"resolvedParams", "resolvedInputs", "progressData"};

    for (String fieldName : compressedFields) {
      if (result.containsKey(fieldName)) {
        Object value = result.get(fieldName);
        Object inflated = inflateIfCompressed(value);
        if (inflated != null) {
          result.put(fieldName, inflated);
        } else {
          result.remove(fieldName);
        }
      }
    }

    return result;
  }

  /**
   * Inflate a potentially compressed value.
   *
   * With SimplifiedJson, binary data may come as Base64 string directly
   * or still wrapped in MongoDB binary format.
   *
   * Spring Data MongoDB serialized format:
   * - {"_serialised": {"$binary": {"base64": "...", "subType": "00"}}}
   */
  @SuppressWarnings("unchecked")
  private Object inflateIfCompressed(Object value) {
    if (value == null) {
      return null;
    }

    try {
      String base64Data = null;

      if (value instanceof Map) {
        Map<String, Object> map = (Map<String, Object>) value;

        // Handle Spring Data MongoDB _serialised wrapper format
        if (map.containsKey("_serialised")) {
          Object serialisedObj = map.get("_serialised");
          if (serialisedObj instanceof Map) {
            map = (Map<String, Object>) serialisedObj;
          } else if (serialisedObj instanceof String) {
            base64Data = (String) serialisedObj;
          }
        }

        if (base64Data == null && map.containsKey("$binary")) {
          Object binaryObj = map.get("$binary");
          if (binaryObj instanceof Map) {
            Map<String, Object> binary = (Map<String, Object>) binaryObj;
            base64Data = (String) binary.get("base64");
          } else if (binaryObj instanceof String) {
            base64Data = (String) binaryObj;
          }
        }
      } else if (value instanceof String) {
        String str = (String) value;
        if (str.startsWith("eJ") || str.length() > 100) {
          base64Data = str;
        } else {
          return value;
        }
      }

      if (base64Data == null) {
        return value;
      }

      byte[] bytes = Base64.decodeBase64(base64Data);
      return kryoSerializer.asInflatedObject(bytes);
    } catch (Exception e) {
      log.error("[CDC-PG-CONSUMER] Failed to inflate compressed field: {}", e.getMessage());
      return null;
    }
  }

  private void recordProcessed(String collection, String operation) {
    try (PmsMetricContextGuard ignore =
             new PmsMetricContextGuard(ImmutableMap.of("collection", collection, "operation", operation))) {
      metricService.recordMetric(RECORDS_PROCESSED_TOTAL, 1);
    }
  }
}
