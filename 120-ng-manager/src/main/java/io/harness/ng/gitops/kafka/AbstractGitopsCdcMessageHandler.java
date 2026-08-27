/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.kafka;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.eventsframework.api.MessageHandler;
import io.harness.ff.FeatureFlagService;
import io.harness.kafka.consumers.HKafkaConsumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericEnumSymbol;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

/**
 * Abstract base class for GitOps CDC Kafka message handlers that bridges Avro change events
 * from Kafka Connect to existing idempotent Redis event handlers.
 *
 * <h3>Runtime behavior</h3>
 * <ul>
 *   <li><b>FF OFF (default):</b> returns immediately. {@link HKafkaConsumer} still commits
 *       the offset so the consumer group stays current and re-enabling the FF does not
 *       cause a massive replay of events already handled by the legacy Redis/Debezium path.</li>
 *   <li><b>FF ON:</b> extracts the MongoDB {@code _id} from the flattened Avro record,
 *       reads the op code from the {@code __op} header (added by Debezium's ExtractNewDocumentState
 *       SMT), converts the {@link GenericRecord} to plain JSON, builds a
 *       {@link DebeziumChangeEvent}, and delegates to the existing change-event handler.</li>
 * </ul>
 *
 * <h3>Delivery guarantees</h3>
 * <p>The downstream handlers are idempotent (INSERT … ON CONFLICT DO NOTHING/DO UPDATE), so
 * Kafka's at-least-once semantics are safe. This handler additionally runs a bounded in-process
 * retry loop for transient errors; after the final attempt fails the error is logged and
 * swallowed so {@link HKafkaConsumer} commits the offset and the consumer does not get stuck
 * on a poison pill.
 *
 * <h3>Synthesized DebeziumChangeEvent key</h3>
 * <p>The legacy {@link io.harness.redisHandler.RedisAbstractHandler#handleEvent} parses the event
 * key as JSON and extracts the {@code "id"} field. In the Redis transport this key is produced by
 * Debezium's JsonConverter; in the new Kafka path {@link HKafkaConsumer} does not forward the
 * Kafka record key to the handler. To preserve the downstream contract, this handler synthesizes
 * a key {@code {"id":"<_id>"}} from the top-level {@code _id} field in the flattened Avro value.
 */
@OwnedBy(GITOPS)
@Slf4j
public abstract class AbstractGitopsCdcMessageHandler implements MessageHandler<GenericRecord> {
  @VisibleForTesting static final String OP_HEADER = "__op";
  @VisibleForTesting static final String ID_FIELD = "_id";
  @VisibleForTesting static final int MAX_RETRIES = 3;
  @VisibleForTesting static final long RETRY_BACKOFF_MS = 500;

  private static final ObjectMapper OBJECT_MAPPER = NG_DEFAULT_OBJECT_MAPPER;

  private final FeatureFlagService featureFlagService;

  protected AbstractGitopsCdcMessageHandler(FeatureFlagService featureFlagService) {
    this.featureFlagService = featureFlagService;
  }

  /**
   * Returns the event handler to which this message handler delegates.
   */
  protected abstract DebeziumAbstractRedisEventHandler getEventHandler();

  /**
   * Returns a log-friendly name for the topic/entity type (e.g., "utilization_snapshot", "applications", "appsync").
   */
  protected abstract String getTopicName();

  @Override
  public void onMessage(GenericRecord message, Map<String, String> metadata, Map<String, Object> metricInfo) {
    if (!isFeatureFlagEnabled()) {
      log.debug("[CDC-Kafka][GitOps] FF CDS_GITOPS_ENABLE_KAFKA_CONNECT is OFF; committing offset without processing");
      return;
    }
    if (message == null) {
      log.warn("[CDC-Kafka][GitOps] Received null Avro record for {}; skipping", getTopicName());
      return;
    }

    String id = extractId(message);
    String optype = extractOptype(metadata);

    if (id.isEmpty()) {
      log.warn("[CDC-Kafka][GitOps] {} event has empty _id; optype={} — skipping to avoid bad DB write", getTopicName(),
          optype);
      return;
    }

    String jsonValue = convertValueToJson(message);
    long timestamp = extractTimestamp(metricInfo);

    log.debug(
        "[CDC-Kafka][GitOps] {} event received: id={}, optype={}, ts={}ms", getTopicName(), id, optype, timestamp);

    DebeziumChangeEvent event = DebeziumChangeEvent.newBuilder()
                                    .setKey(syntheticKey(id))
                                    .setValue(jsonValue)
                                    .setOptype(optype)
                                    .setTimestamp(timestamp)
                                    .build();

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        boolean ok = getEventHandler().handleEvent(event);
        if (ok) {
          log.debug("[CDC-Kafka][GitOps] {} event handled successfully: id={}, optype={}", getTopicName(), id, optype);
        } else {
          log.warn(
              "[CDC-Kafka][GitOps] {} event handler returned false for id={}, optype={}", getTopicName(), id, optype);
        }
        return;
      } catch (Exception e) {
        if (attempt < MAX_RETRIES) {
          log.warn("[CDC-Kafka][GitOps] {} event processing failed (attempt {}/{}), retrying: "
                  + "id={}, optype={}, error={}",
              getTopicName(), attempt, MAX_RETRIES, id, optype, e.getMessage());
          try {
            Thread.sleep(RETRY_BACKOFF_MS * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error(
                "[CDC-Kafka][GitOps] Retry interrupted for {} event id={}, optype={}", getTopicName(), id, optype);
            return;
          }
        } else {
          log.error("[CDC-Kafka][GitOps] {} event processing failed after {} attempts: "
                  + "id={}, optype={}, error={}",
              getTopicName(), MAX_RETRIES, id, optype, e.getMessage(), e);
        }
      }
    }
  }

  @VisibleForTesting
  boolean isFeatureFlagEnabled() {
    try {
      return featureFlagService.isGlobalEnabled(FeatureName.CDS_GITOPS_ENABLE_KAFKA_CONNECT);
    } catch (Exception e) {
      log.warn("[CDC-Kafka][GitOps] Failed to evaluate feature flag {}, defaulting to disabled",
          FeatureName.CDS_GITOPS_ENABLE_KAFKA_CONNECT, e);
      return false;
    }
  }

  @VisibleForTesting
  static String extractId(GenericRecord record) {
    Object id = record.get(ID_FIELD);
    return id == null ? "" : id.toString();
  }

  /**
   * Builds a JSON key {@code {"id":"<id>"}} that downstream {@code RedisAbstractHandler.getId()}
   * can parse via {@code objectMapper.readTree(key).get("id").asText()}. Uses Jackson to escape
   * the id safely instead of hand-rolling string concatenation.
   */
  @VisibleForTesting
  static String syntheticKey(String id) {
    try {
      return OBJECT_MAPPER.writeValueAsString(Collections.singletonMap("id", id == null ? "" : id));
    } catch (JsonProcessingException e) {
      log.warn("[CDC-Kafka][GitOps] Failed to serialize synthetic key for id={}", id, e);
      return "{\"id\":\"\"}";
    }
  }

  /**
   * Extracts the CDC operation type from Debezium's {@code __op} header (added by the
   * ExtractNewDocumentState SMT with {@code add.headers=op}).
   *
   * @return one of CREATE, UPDATE, DELETE, SNAPSHOT, or UNKNOWN when the header is absent
   */
  @VisibleForTesting
  static String extractOptype(Map<String, String> metadata) {
    if (metadata == null) {
      return "UNKNOWN";
    }
    String op = metadata.get(OP_HEADER);
    if (op == null) {
      return "UNKNOWN";
    }
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

  private static long extractTimestamp(Map<String, Object> metricInfo) {
    if (metricInfo == null) {
      return 0L;
    }
    Object ts = metricInfo.get(HKafkaConsumer.EVENT_SEND_TS);
    return ts instanceof Long ? (Long) ts : 0L;
  }

  /**
   * Converts an Avro-deserialized value to plain JSON compatible with
   * {@code ObjectMapper.readTree()} used by Redis event handlers.
   *
   * <p>{@code GenericData.toString()} would produce Avro-flavored JSON where union members
   * are wrapped (for example {@code {"int":30}} instead of {@code 30}). This recursively unwraps
   * Avro types to plain Java objects so Jackson emits standard JSON.
   */
  @VisibleForTesting
  static String convertValueToJson(Object value) {
    if (value == null) {
      return "";
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(toPlainObject(value));
    } catch (JsonProcessingException e) {
      log.warn("[CDC-Kafka][GitOps] Failed to convert Avro value to JSON, falling back to toString()", e);
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
          .map(AbstractGitopsCdcMessageHandler::toPlainObject)
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
}