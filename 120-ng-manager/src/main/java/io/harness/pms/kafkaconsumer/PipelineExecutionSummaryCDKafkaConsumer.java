/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.kafkaconsumer;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
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

import io.harness.annotations.dev.OwnedBy;
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaBaseConfig;
import io.harness.ng.gitops.config.CdcKafkaConfig;
import io.harness.ng.gitops.config.CdcKafkaConsumerConfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.dropwizard.lifecycle.Managed;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericEnumSymbol;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.RecordDeserializationException;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * PIPELINE-team Kafka CDC consumer for {@code pmsMongo.pms-harness.planExecutionsSummary}.
 *
 * <p>Sequential poll loop (GTM/IDP pattern) — no HKafkaConsumer inheritance.
 * Single thread per partition preserves ordering and avoids livelock; this mirrors
 * the pipeline-service planExecutionSummary Kafka consumer design.
 *
 * <p>Three-step config-driven cutover (no FF caching issues):
 * <ol>
 *   <li>{@code kafkaConsumerEnabled=true}  — register thread at boot (starts draining)
 *   <li>{@code processingEnabled=true}     — after connector verified → writes to TimescaleDB
 *   <li>{@code redisShortCircuit=true}     — Redis consumer stops processing (Kafka is source of truth)
 * </ol>
 */
@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class PipelineExecutionSummaryCDKafkaConsumer implements Runnable, Managed {
  // Package-visible so handler and Redis consumer can reference without a separate constants file
  public static final String CONSUMER_CONFIG_KEY = "planExecutionsSummaryCD";
  public static final String CONSUMER_GROUP = "ng-manager-pipeline-execution-summary-cd-cdc";

  private static final ObjectMapper OBJECT_MAPPER = NG_DEFAULT_OBJECT_MAPPER;
  private static final String OP_HEADER = "__op";
  private static final long DRAIN_RECHECK_INTERVAL_MS = 5_000;
  private static final long STATUS_LOG_INTERVAL_MS = 60_000;
  private static final Duration POISON_PILL_COMMIT_TIMEOUT = Duration.ofSeconds(30);

  private final String topic;
  private final PipelineExecutionSummaryCDKafkaCdcMessageHandler messageHandler;
  private final ConsumerMaintenanceListener consumerMaintenanceListener;
  private final Properties properties;

  private volatile Consumer<String, Object> consumer;
  private ExecutorService consumerThread;

  private long lastStatusLogTime;
  private long processedCount;
  private long drainedCount;
  private long errorCount;

  @Inject
  public PipelineExecutionSummaryCDKafkaConsumer(PipelineExecutionSummaryCDKafkaCdcMessageHandler messageHandler,
      @KafkaModule.General KafkaBaseConfig kafkaBaseConfig, ConsumerMaintenanceListener consumerMaintenanceListener,
      CdcKafkaConfig cdcKafkaConfig) {
    this.messageHandler = messageHandler;
    this.consumerMaintenanceListener = consumerMaintenanceListener;

    CdcKafkaConsumerConfig consumerCfg =
        cdcKafkaConfig.getConsumer(CONSUMER_CONFIG_KEY)
            .orElseThrow(
                () -> new IllegalStateException("Missing CDC Kafka consumer config for '" + CONSUMER_CONFIG_KEY + "'"));
    this.topic = consumerCfg.getTopic();
    this.properties = buildProperties(kafkaBaseConfig, CONSUMER_GROUP, consumerCfg.getMaxPollRecords());
  }

  @Override
  public void start() {
    consumerThread = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat("pipe-exec-summary-cd-cdc-%d").setDaemon(true).build());
    consumerThread.submit(this);
    log.info("[CDC-Kafka][PIPE] PipelineExecutionSummaryCDKafkaConsumer started (topic={}, group={})", topic,
        CONSUMER_GROUP);
  }

  @Override
  public void stop() throws Exception {
    if (consumer != null) {
      consumer.wakeup();
    }
    if (consumerThread != null) {
      consumerThread.shutdown();
      if (!consumerThread.awaitTermination(30, TimeUnit.SECONDS)) {
        consumerThread.shutdownNow();
      }
    }
    log.info("[CDC-Kafka][PIPE] PipelineExecutionSummaryCDKafkaConsumer stopped");
  }

  @Override
  public void run() {
    consumer = new KafkaConsumer<>(properties);
    consumer.subscribe(Collections.singletonList(topic));
    lastStatusLogTime = System.currentTimeMillis();
    log.info("[CDC-Kafka][PIPE] Consumer subscribed to topic={}, group={}", topic, CONSUMER_GROUP);

    try {
      while (!consumerMaintenanceListener.inShutdown()) {
        try {
          if (consumerMaintenanceListener.inMaintenance()) {
            log.warn("[CDC-Kafka][PIPE] Consumer awaiting maintenance for topic={}", topic);
            consumerMaintenanceListener.awaitMaintenance();
          }
          processOneBatch();
          logPeriodicStatus();
        } catch (WakeupException e) {
          log.info("[CDC-Kafka][PIPE] Consumer woken up for shutdown, topic={}", topic);
          break;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        } catch (Exception e) {
          log.error("[CDC-Kafka][PIPE] Unexpected error in poll loop topic={}: {}", topic, e.getMessage(), e);
          errorCount++;
        }
      }
    } finally {
      closeConsumer();
    }
  }

  private void processOneBatch() throws InterruptedException {
    ConsumerRecords<String, Object> records;
    try {
      records = consumer.poll(Duration.ofMillis(1000));
    } catch (RecordDeserializationException e) {
      seekPastPoisonPill(e);
      errorCount++;
      return;
    }

    if (records.isEmpty()) {
      Thread.sleep(DRAIN_RECHECK_INTERVAL_MS);
      return;
    }

    for (TopicPartition partition : records.partitions()) {
      for (ConsumerRecord<String, Object> record : records.records(partition)) {
        processSingleRecord(record);
      }
    }
  }

  private void processSingleRecord(ConsumerRecord<String, Object> record) {
    try {
      DebeziumChangeEvent event = buildChangeEvent(record);
      boolean processed = messageHandler.handleEvent(event);
      if (processed) {
        processedCount++;
      } else {
        drainedCount++;
      }
      commitOffset(record);
    } catch (Exception e) {
      log.error("[CDC-Kafka][PIPE] Error processing record topic={}, partition={}, offset={}: {}", record.topic(),
          record.partition(), record.offset(), e.getMessage(), e);
      errorCount++;
      commitOffset(record);
    }
  }

  @VisibleForTesting
  static DebeziumChangeEvent buildChangeEvent(ConsumerRecord<String, Object> record) {
    String key = record.key() != null ? record.key() : "";
    String optype = extractOptype(record);
    String jsonValue = convertValueToJson(record.value());

    return DebeziumChangeEvent.newBuilder()
        .setKey(key)
        .setValue(jsonValue)
        .setOptype(optype)
        .setTimestamp(record.timestamp())
        .build();
  }

  private static String extractOptype(ConsumerRecord<String, ?> record) {
    for (Header header : record.headers()) {
      if (OP_HEADER.equals(header.key())) {
        String op = new String(header.value(), StandardCharsets.UTF_8);
        switch (op) {
          case "c":
            return "CREATE";
          case "u":
            return "UPDATE";
          case "d":
            return "DELETE";
          case "r":
            return "SNAPSHOT";
          default:
            return op.toUpperCase(Locale.ROOT);
        }
      }
    }
    return "UNKNOWN";
  }

  @VisibleForTesting
  static String convertValueToJson(Object value) {
    if (value == null) {
      return "";
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(toPlainObject(value));
    } catch (JsonProcessingException e) {
      log.warn("[CDC-Kafka][PIPE] Failed to convert Avro value to JSON, falling back to toString()", e);
      return value.toString();
    }
  }

  @SuppressWarnings("unchecked")
  private static Object toPlainObject(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Utf8) {
      return value.toString();
    }
    if (value instanceof GenericRecord) {
      GenericRecord record = (GenericRecord) value;
      Map<String, Object> map = new LinkedHashMap<>();
      for (Schema.Field field : record.getSchema().getFields()) {
        map.put(field.name(), toPlainObject(record.get(field.name())));
      }
      return map;
    }
    if (value instanceof Collection) {
      return ((Collection<?>) value)
          .stream()
          .map(PipelineExecutionSummaryCDKafkaConsumer::toPlainObject)
          .collect(Collectors.toList());
    }
    if (value instanceof Map) {
      Map<String, Object> result = new LinkedHashMap<>();
      ((Map<Object, Object>) value).forEach((k, v) -> result.put(String.valueOf(k), toPlainObject(v)));
      return result;
    }
    if (value instanceof ByteBuffer) {
      ByteBuffer buf = ((ByteBuffer) value).duplicate();
      byte[] bytes = new byte[buf.remaining()];
      buf.get(bytes);
      return Base64.getEncoder().encodeToString(bytes);
    }
    if (value instanceof GenericEnumSymbol) {
      return value.toString();
    }
    if (value instanceof GenericFixed) {
      return Base64.getEncoder().encodeToString(((GenericFixed) value).bytes());
    }
    return value;
  }

  private void commitOffset(ConsumerRecord<String, Object> record) {
    Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
    offsets.put(new TopicPartition(record.topic(), record.partition()), new OffsetAndMetadata(record.offset() + 1));
    consumer.commitAsync(offsets, (map, e) -> {
      if (e != null) {
        log.warn("[CDC-Kafka][PIPE] Failed to commit offset topic={}, partition={}, offset={}: {}", record.topic(),
            record.partition(), record.offset(), e.getMessage());
      }
    });
  }

  private void seekPastPoisonPill(RecordDeserializationException e) {
    TopicPartition partition = e.topicPartition();
    long offset = e.offset();
    log.error("[CDC-Kafka][PIPE] Poison pill at topic={}, partition={}, offset={}. Seeking past it.", topic,
        partition.partition(), offset, e);
    try {
      consumer.seek(partition, offset + 1);
      Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
      offsets.put(partition, new OffsetAndMetadata(offset + 1));
      consumer.commitSync(offsets, POISON_PILL_COMMIT_TIMEOUT);
    } catch (KafkaException ex) {
      log.error("[CDC-Kafka][PIPE] Failed to seek past poison pill topic={}, partition={}, offset={}", topic,
          partition.partition(), offset, ex);
    }
  }

  private void logPeriodicStatus() {
    long now = System.currentTimeMillis();
    if (now - lastStatusLogTime >= STATUS_LOG_INTERVAL_MS) {
      log.info("[CDC-Kafka][PIPE] Status topic={}: processed={}, drained={}, errors={}", topic, processedCount,
          drainedCount, errorCount);
      processedCount = 0;
      drainedCount = 0;
      errorCount = 0;
      lastStatusLogTime = now;
    }
  }

  private void closeConsumer() {
    if (consumer != null) {
      try {
        consumer.close();
      } catch (Exception e) {
        log.error("[CDC-Kafka][PIPE] Error closing consumer for topic={}", topic, e);
      }
    }
  }

  private static Properties buildProperties(
      KafkaBaseConfig kafkaBaseConfig, String consumerGroupId, int maxPollRecords) {
    Properties props = new Properties();

    props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, kafkaBaseConfig.getSecurityProtocol());
    if (kafkaBaseConfig.getSecurityProtocol().startsWith("SASL")) {
      props.put(SASL_MECHANISM, kafkaBaseConfig.getSaslMechanism());
      props.put(SASL_JAAS_CONFIG, kafkaBaseConfig.getSaslJaasConfig());
      if ("SASL_SSL".equals(kafkaBaseConfig.getSecurityProtocol())) {
        props.put(
            SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, kafkaBaseConfig.getSslEndpointIdentificationAlgorithm());
      }
    }
    if (kafkaBaseConfig.getSaslLoginCallbackHandlerClass() != null
        && !kafkaBaseConfig.getSaslLoginCallbackHandlerClass().isEmpty()) {
      props.put(SASL_LOGIN_CALLBACK_HANDLER_CLASS, kafkaBaseConfig.getSaslLoginCallbackHandlerClass());
    }
    if (kafkaBaseConfig.getSecurityProtocol().endsWith("SSL")) {
      putIfNotEmpty(props, SSL_TRUSTSTORE_LOCATION_CONFIG, kafkaBaseConfig.getSslTruststoreLocation());
      putIfNotEmpty(props, SSL_TRUSTSTORE_PASSWORD_CONFIG, kafkaBaseConfig.getSslTruststorePassword());
      putIfNotEmpty(props, SSL_TRUSTSTORE_TYPE_CONFIG, kafkaBaseConfig.getSslTruststoreType());
      putIfNotEmpty(props, SSL_KEYSTORE_LOCATION_CONFIG, kafkaBaseConfig.getSslKeystoreLocation());
      putIfNotEmpty(props, SSL_KEYSTORE_PASSWORD_CONFIG, kafkaBaseConfig.getSslKeystorePassword());
      putIfNotEmpty(props, SSL_KEYSTORE_TYPE_CONFIG, kafkaBaseConfig.getSslKeystoreType());
      putIfNotEmpty(props, SSL_KEY_PASSWORD_CONFIG, kafkaBaseConfig.getSslKeyPassword());
      putIfNotEmpty(props, SSL_PROTOCOL_CONFIG, kafkaBaseConfig.getSslProtocol());
      putIfNotEmpty(props, SSL_ENABLED_PROTOCOLS_CONFIG, kafkaBaseConfig.getSslEnabledProtocols());
      putIfNotEmpty(props, SSL_PROVIDER_CONFIG, kafkaBaseConfig.getSslProvider());
    }

    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBaseConfig.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());

    if (kafkaBaseConfig.getSchemaRegistryUrl() != null) {
      props.put("schema.registry.url", kafkaBaseConfig.getSchemaRegistryUrl());
      putIfNotEmpty(props, "basic.auth.user.info", kafkaBaseConfig.getSchemaRegistryBasicAuthUserInfo());
      putIfNotEmpty(
          props, "basic.auth.credentials.source", kafkaBaseConfig.getSchemaRegistryBasicAuthCredentialsSource());
    }

    props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, false);
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
    props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
        Arrays.asList(CooperativeStickyAssignor.class.getName(), RangeAssignor.class.getName()));

    return props;
  }

  private static void putIfNotEmpty(Properties props, String key, String value) {
    if (value != null && !value.isEmpty()) {
      props.put(key, value);
    }
  }
}
