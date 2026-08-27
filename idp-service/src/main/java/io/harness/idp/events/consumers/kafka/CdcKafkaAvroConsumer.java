/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.kafka;

import static io.harness.annotations.dev.HarnessTeam.IDP;

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

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.eventsframework.api.MessageHandler;
import io.harness.ff.FeatureFlagService;
import io.harness.kafka.common.ConsumerMaintenanceListener;
import io.harness.kafka.config.KafkaBaseConfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
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
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Abstract base class for IDP CDC Kafka consumers that read Avro-encoded messages
 * from Kafka Connect and map them to {@link DebeziumChangeEvent} protobuf messages.
 *
 * <p>This consumer supports dual-pipeline processing with Redis Streams as a fallback.
 * A feature flag controls which consumer actively processes:
 * <ul>
 *   <li>FF OFF → Redis consumer processes; Kafka consumer drains and commits offsets
 *       (to keep the consumer group current and avoid a stale backlog on FF enable).</li>
 *   <li>FF ON  → Kafka consumer processes; Redis consumer acknowledges without processing.</li>
 * </ul>
 * <p>During FF transitions there is a brief window where both consumers may process
 * the same event (at-least-once). Downstream handlers <b>must be idempotent</b>.
 */
@OwnedBy(IDP)
@Slf4j
public abstract class CdcKafkaAvroConsumer implements Runnable, Closeable {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @VisibleForTesting static final String OP_HEADER = "__op";

  private static final long FF_RECHECK_INTERVAL_MS = 5000;
  private static final long STATUS_LOG_INTERVAL_MS = 60_000;
  private static final Duration POISON_PILL_COMMIT_TIMEOUT = Duration.ofSeconds(30);
  private static final int COMMIT_FAILURE_ESCALATION_THRESHOLD = 5;

  private final String topic;
  private final MessageHandler<DebeziumChangeEvent> messageHandler;
  private final ConsumerMaintenanceListener consumerMaintenanceListener;
  private final Properties properties;
  private final FeatureFlagService featureFlagService;
  private final FeatureName featureFlagName;
  private final boolean useJsonValueFormat;
  private volatile Consumer<String, Object> consumer;
  private boolean lastKnownFfState;
  private long lastStatusLogTime;
  private long processedSinceLastLog;
  private long drainedSinceLastLog;
  private long errorSinceLastLog;
  private int consecutiveCommitFailures;

  @VisibleForTesting
  String getTopic() {
    return topic;
  }

  @VisibleForTesting
  boolean isUseJsonValueFormat() {
    return useJsonValueFormat;
  }

  @VisibleForTesting
  Properties getConsumerProperties() {
    return properties;
  }

  protected CdcKafkaAvroConsumer(String topic, String consumerGroupId, KafkaBaseConfig kafkaBaseConfig,
      MessageHandler<DebeziumChangeEvent> messageHandler, ConsumerMaintenanceListener consumerMaintenanceListener,
      int maxPollRecords, FeatureFlagService featureFlagService, FeatureName featureFlagName,
      boolean useJsonValueFormat) {
    this.topic = topic;
    this.messageHandler = messageHandler;
    this.consumerMaintenanceListener = consumerMaintenanceListener;
    this.useJsonValueFormat = useJsonValueFormat;
    this.properties = buildProperties(kafkaBaseConfig, consumerGroupId, maxPollRecords, useJsonValueFormat);
    this.featureFlagService = featureFlagService;
    this.featureFlagName = featureFlagName;
  }

  @VisibleForTesting
  protected CdcKafkaAvroConsumer(String topic, MessageHandler<DebeziumChangeEvent> messageHandler,
      ConsumerMaintenanceListener consumerMaintenanceListener, Consumer<String, Object> consumer,
      FeatureFlagService featureFlagService, FeatureName featureFlagName, boolean useJsonValueFormat) {
    this.topic = topic;
    this.messageHandler = messageHandler;
    this.consumerMaintenanceListener = consumerMaintenanceListener;
    this.useJsonValueFormat = useJsonValueFormat;
    this.properties = null;
    this.consumer = consumer;
    this.featureFlagService = featureFlagService;
    this.featureFlagName = featureFlagName;
  }

  @Override
  public void run() {
    if (consumer == null) {
      consumer = new KafkaConsumer<>(properties);
    }
    consumer.subscribe(Collections.singletonList(topic));
    lastKnownFfState = isFeatureFlagEnabled();
    lastStatusLogTime = System.currentTimeMillis();
    log.info("[CDC-Kafka] Consumer started for topic={}, FF {}={}, mode={}, valueFormat={}", topic,
        featureFlagName.name(), lastKnownFfState, lastKnownFfState ? "PROCESSING" : "DRAINING",
        useJsonValueFormat ? "JSON (schema registry disabled)" : "AVRO");

    while (!consumerMaintenanceListener.inShutdown()) {
      try {
        if (consumerMaintenanceListener.inMaintenance()) {
          log.warn("CDC Kafka Avro consumer awaiting maintenance for topic {}", topic);
          consumerMaintenanceListener.awaitMaintenance();
        }
        boolean ffEnabled = isFeatureFlagEnabled();
        if (ffEnabled != lastKnownFfState) {
          log.info("[CDC-Kafka] FF transition for topic={}: {} -> {} (mode={})", topic,
              lastKnownFfState ? "ENABLED" : "DISABLED", ffEnabled ? "ENABLED" : "DISABLED",
              ffEnabled ? "PROCESSING" : "DRAINING");
          lastKnownFfState = ffEnabled;
        }
        if (ffEnabled) {
          processOrdered();
        } else {
          drainAndCommit();
        }
        logPeriodicStatus();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.info("CDC Kafka Avro consumer interrupted for topic {}, shutting down", topic);
        break;
      } catch (Exception e) {
        log.error("Error in CDC Kafka Avro consumer for topic {}: {}", topic, e.getMessage(), e);
        errorSinceLastLog++;
      }
    }
    log.warn("CDC Kafka Avro consumer thread exiting for topic {}. "
            + "If the feature flag {} is enabled, no Kafka CDC events will be processed for this topic.",
        topic, featureFlagName.name());
    close();
  }

  @VisibleForTesting
  boolean isFeatureFlagEnabled() {
    try {
      return featureFlagService != null && featureFlagService.isGlobalEnabled(featureFlagName);
    } catch (Exception e) {
      log.warn("Failed to evaluate feature flag {} for topic {}, defaulting to disabled", featureFlagName, topic, e);
      return false;
    }
  }

  // Drains Kafka records without processing and commits their offsets. This keeps
  // the consumer group current while the Redis pipeline is the active processor
  // (FF disabled). Without this, re-enabling the FF would cause a massive replay
  // of events already handled by Redis.
  private void drainAndCommit() throws InterruptedException {
    ConsumerRecords<String, Object> records;
    try {
      synchronized (consumer) {
        records = consumer.poll(Duration.ofMillis(1000));
      }
    } catch (RecordDeserializationException e) {
      seekPastPoisonPill(e);
      return;
    }
    if (records.isEmpty()) {
      Thread.sleep(FF_RECHECK_INTERVAL_MS);
      return;
    }
    for (TopicPartition partition : records.partitions()) {
      List<ConsumerRecord<String, Object>> partitionRecords = records.records(partition);
      if (!partitionRecords.isEmpty()) {
        ConsumerRecord<String, Object> last = partitionRecords.get(partitionRecords.size() - 1);
        commitOffset(last);
      }
    }
    drainedSinceLastLog += records.count();
  }

  private void logPeriodicStatus() {
    long now = System.currentTimeMillis();
    if (now - lastStatusLogTime >= STATUS_LOG_INTERVAL_MS) {
      log.info("[CDC-Kafka] Status for topic={}: processed={}, drained={}, errors={}, mode={}", topic,
          processedSinceLastLog, drainedSinceLastLog, errorSinceLastLog, lastKnownFfState ? "PROCESSING" : "DRAINING");
      processedSinceLastLog = 0;
      drainedSinceLastLog = 0;
      errorSinceLastLog = 0;
      lastStatusLogTime = now;
    }
  }

  private void processOrdered() throws InterruptedException {
    ConsumerRecords<String, Object> records;
    try {
      synchronized (consumer) {
        records = consumer.poll(Duration.ofMillis(1000));
      }
    } catch (RecordDeserializationException e) {
      seekPastPoisonPill(e);
      errorSinceLastLog++;
      return;
    }

    if (records.isEmpty()) {
      Thread.sleep(300);
      return;
    }

    for (TopicPartition partition : records.partitions()) {
      for (ConsumerRecord<String, Object> record : records.records(partition)) {
        try {
          DebeziumChangeEvent event = mapToChangeEvent(record);
          Map<String, String> headers = extractHeaders(record);
          Map<String, Object> metricInfo = buildMetricInfo(record);
          Map<String, String> enrichedHeaders = new HashMap<>(headers);
          metricInfo.forEach((k, v) -> enrichedHeaders.put(k, String.valueOf(v)));

          messageHandler.onMessage(event, enrichedHeaders, metricInfo);
          commitOffset(record);
          processedSinceLastLog++;
        } catch (Exception e) {
          log.error("Error processing CDC Avro record from topic={}, partition={}, offset={}: {}", topic,
              record.partition(), record.offset(), e.getMessage(), e);
          errorSinceLastLog++;
        }
      }
    }
  }

  private void seekPastPoisonPill(RecordDeserializationException e) {
    TopicPartition partition = e.topicPartition();
    long offset = e.offset();
    log.error("[CDC-Kafka] Poison pill detected at topic={}, partition={}, offset={}. Seeking past it.", topic,
        partition.partition(), offset, e);
    try {
      synchronized (consumer) {
        consumer.seek(partition, offset + 1);
        Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
        offsets.put(partition, new OffsetAndMetadata(offset + 1));
        consumer.commitSync(offsets, POISON_PILL_COMMIT_TIMEOUT);
      }
      log.info("[CDC-Kafka] Successfully skipped poison pill at topic={}, partition={}, offset={}", topic,
          partition.partition(), offset);
    } catch (KafkaException ex) {
      log.error("[CDC-Kafka] Failed to seek past poison pill at topic={}, partition={}, offset={}", topic,
          partition.partition(), offset, ex);
    }
  }

  @VisibleForTesting
  DebeziumChangeEvent mapToChangeEvent(ConsumerRecord<String, Object> record) {
    return DebeziumChangeEvent.newBuilder()
        .setKey(record.key() != null ? record.key() : "")
        .setValue(convertValueToJson(record.value()))
        .setOptype(extractOptype(record))
        .setTimestamp(record.timestamp())
        .build();
  }

  @VisibleForTesting
  static String convertValueToJson(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof String) {
      String str = ((String) value).trim();
      if (str.isEmpty()) {
        return "";
      }
      if (!(str.startsWith("{") || str.startsWith("["))) {
        String raw = (String) value;
        String prefix = raw.substring(0, Math.min(50, raw.length()));
        throw new IllegalArgumentException(
            String.format("Non-JSON string value in JSON mode (length=%d, prefix='%s')", raw.length(), prefix));
      }
      return (String) value;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(toPlainObject(value));
    } catch (JsonProcessingException e) {
      log.warn("Failed to convert Avro value to JSON, falling back to toString()", e);
      return value.toString();
    }
  }

  @VisibleForTesting
  static String extractOptype(ConsumerRecord<String, ?> record) {
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
      return ((Collection<?>) value).stream().map(CdcKafkaAvroConsumer::toPlainObject).collect(Collectors.toList());
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

  private Map<String, String> extractHeaders(ConsumerRecord<String, Object> record) {
    Map<String, String> headers = new HashMap<>();
    record.headers().forEach(h -> headers.put(h.key(), new String(h.value(), StandardCharsets.UTF_8)));
    return headers;
  }

  private Map<String, Object> buildMetricInfo(ConsumerRecord<String, Object> record) {
    Map<String, Object> metricInfo = new HashMap<>();
    metricInfo.put("streamName", topic);
    metricInfo.put("eventSendTs", record.timestamp());
    metricInfo.put("eventReceiveTs", Instant.now().toEpochMilli());
    return metricInfo;
  }

  private void commitOffset(ConsumerRecord<String, Object> record) {
    Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
    offsets.put(new TopicPartition(topic, record.partition()), new OffsetAndMetadata(record.offset() + 1));
    synchronized (consumer) {
      consumer.commitAsync(offsets, (map, e) -> {
        if (e != null) {
          consecutiveCommitFailures++;
          if (consecutiveCommitFailures >= COMMIT_FAILURE_ESCALATION_THRESHOLD) {
            log.error("Failed to commit offset for topic={}, partition={}, offset={} "
                    + "(consecutive failures={}): {}",
                topic, record.partition(), record.offset(), consecutiveCommitFailures, e.getMessage());
          } else {
            log.warn("Failed to commit offset for topic={}, partition={}, offset={}: {}", topic, record.partition(),
                record.offset(), e.getMessage());
          }
        } else {
          consecutiveCommitFailures = 0;
        }
      });
    }
  }

  @Override
  public void close() {
    if (consumer != null) {
      try {
        consumer.close();
      } catch (Exception e) {
        log.error("Error closing CDC Kafka Avro consumer for topic {}", topic, e);
      }
    }
  }

  @VisibleForTesting
  static Properties buildProperties(
      KafkaBaseConfig kafkaBaseConfig, String consumerGroupId, int maxPollRecords, boolean useJsonValueFormat) {
    Properties props = new Properties();

    props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, kafkaBaseConfig.getSecurityProtocol());
    if (kafkaBaseConfig.getSecurityProtocol().startsWith("SASL")) {
      props.put(SASL_MECHANISM, kafkaBaseConfig.getSaslMechanism());
      props.put(SASL_JAAS_CONFIG, kafkaBaseConfig.getSaslJaasConfig());
      if (SASL_SSL.name().equals(kafkaBaseConfig.getSecurityProtocol())) {
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

    if (useJsonValueFormat) {
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    } else {
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
      if (kafkaBaseConfig.getSchemaRegistryUrl() != null) {
        props.put("schema.registry.url", kafkaBaseConfig.getSchemaRegistryUrl());
        putIfNotEmpty(props, "basic.auth.user.info", kafkaBaseConfig.getSchemaRegistryBasicAuthUserInfo());
        putIfNotEmpty(
            props, "basic.auth.credentials.source", kafkaBaseConfig.getSchemaRegistryBasicAuthCredentialsSource());
      }
      props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, false);
    }

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
