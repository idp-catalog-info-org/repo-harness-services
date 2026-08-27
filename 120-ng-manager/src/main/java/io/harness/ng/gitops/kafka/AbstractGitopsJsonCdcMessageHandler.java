/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.gitops.kafka;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.debezium.DebeziumChangeEvent;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.eventsframework.api.MessageHandler;
import io.harness.ff.FeatureFlagService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for GitOps CDC Kafka message handlers that use JSON serialization
 * instead of Avro. Used when Avro field name restrictions are problematic (e.g., MongoDB's
 * dot replacement character ~ in label keys).
 *
 * <p>Similar to {@link AbstractGitopsCdcMessageHandler} but consumes JSON strings directly.
 */
@OwnedBy(GITOPS)
@Slf4j
public abstract class AbstractGitopsJsonCdcMessageHandler implements MessageHandler<String> {
  @VisibleForTesting static final String OP_HEADER = "__op";
  @VisibleForTesting static final String ID_FIELD = "_id";
  @VisibleForTesting static final int MAX_RETRIES = 3;
  @VisibleForTesting static final long RETRY_BACKOFF_MS = 500;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final FeatureFlagService featureFlagService;

  protected AbstractGitopsJsonCdcMessageHandler(FeatureFlagService featureFlagService) {
    this.featureFlagService = featureFlagService;
  }

  protected abstract DebeziumAbstractRedisEventHandler getEventHandler();
  protected abstract String getTopicName();

  @Override
  public void onMessage(String message, Map<String, String> metadata, Map<String, Object> metricInfo) {
    if (!isFeatureFlagEnabled()) {
      log.debug("[CDC-Kafka][GitOps] FF CDS_GITOPS_ENABLE_KAFKA_CONNECT is OFF; committing offset without processing");
      return;
    }

    if (message == null) {
      log.warn("[CDC-Kafka][GitOps] Received null message, skipping");
      return;
    }

    String id = extractId(message);
    if (id == null || id.isEmpty()) {
      log.warn("[CDC-Kafka][GitOps] {} message has empty _id, skipping", getTopicName());
      return;
    }

    String optype = extractOptype(metadata);
    Long timestamp = extractTimestamp(message);

    log.debug("[CDC-Kafka][GitOps] {} event received: id={}, optype={}, ts={}ms [messageId={}]", getTopicName(), id,
        optype, timestamp, metadata != null ? metadata.getOrDefault("messageId", "N/A") : "N/A");

    DebeziumChangeEvent event = buildChangeEvent(id, message, optype, timestamp);

    boolean handled = handleWithRetry(id, optype, event);
    if (handled) {
      log.debug("[CDC-Kafka][GitOps] {} event handled successfully: id={}, optype={} [messageId={}]", getTopicName(),
          id, optype, metadata != null ? metadata.getOrDefault("messageId", "N/A") : "N/A");
    } else {
      log.warn("[CDC-Kafka][GitOps] {} event failed after {} retries, dropping: id={}, optype={} [messageId={}]",
          getTopicName(), MAX_RETRIES, id, optype,
          metadata != null ? metadata.getOrDefault("messageId", "N/A") : "N/A");
    }
  }

  @VisibleForTesting
  static String extractId(String jsonMessage) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(jsonMessage);
      JsonNode idNode = root.get(ID_FIELD);
      return idNode == null ? "" : idNode.asText();
    } catch (Exception e) {
      log.warn("[CDC-Kafka][GitOps] Failed to extract _id from JSON message", e);
      return "";
    }
  }

  @VisibleForTesting
  static String extractOptype(Map<String, String> metadata) {
    if (metadata == null) {
      return "UNKNOWN";
    }
    String op = metadata.get(OP_HEADER);
    if (op == null) {
      return "UNKNOWN";
    }
    switch (op.toLowerCase(Locale.ROOT)) {
      case "c":
      case "r":
        return "CREATE";
      case "u":
        return "UPDATE";
      case "d":
        return "DELETE";
      default:
        return "UNKNOWN";
    }
  }

  @VisibleForTesting
  static Long extractTimestamp(String jsonMessage) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(jsonMessage);
      JsonNode tsNode = root.get("createdAt");
      if (tsNode == null) {
        tsNode = root.get("updatedAt");
      }
      if (tsNode == null) {
        tsNode = root.get("lastModifiedAt");
      }
      return tsNode == null ? System.currentTimeMillis() : tsNode.asLong();
    } catch (Exception e) {
      return System.currentTimeMillis();
    }
  }

  private DebeziumChangeEvent buildChangeEvent(String id, String jsonValue, String optype, Long timestamp) {
    String syntheticKey = String.format("{\"id\":\"%s\"}", id);
    // Replace MongoDB's dot replacement character (~) with dots in label keys.
    // MongoDB stores map keys like "harness.io/serviceRef" as "harness~io/serviceRef" because
    // dots are not allowed in field names. When Kafka Connect reads raw BSON and serializes to JSON,
    // the tilde is preserved. But the downstream handler expects dots (like Spring MongoDB's
    // MappingMongoConverter provides). So we normalize label keys here.
    String normalizedJson = normalizeMongoLabelKeys(jsonValue);
    return DebeziumChangeEvent.newBuilder()
        .setKey(syntheticKey)
        .setValue(normalizedJson)
        .setOptype(optype)
        .setTimestamp(timestamp)
        .build();
  }

  /**
   * Replaces MongoDB's dot replacement character (~) with dots in app.objectmeta.labels keys.
   * This makes raw JSON from Kafka Connect compatible with Spring MongoDB's MappingMongoConverter
   * behavior that the downstream handler expects.
   */
  @VisibleForTesting
  static String normalizeMongoLabelKeys(String jsonValue) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(jsonValue);
      JsonNode labelsNode = root.at("/app/objectmeta/labels");

      if (labelsNode.isMissingNode() || !labelsNode.isObject()) {
        return jsonValue; // No labels to normalize
      }

      // Replace ~ with . in all label keys
      ObjectNode labelsObject = (ObjectNode) labelsNode;
      Map<String, JsonNode> normalizedLabels = new LinkedHashMap<>();
      labelsObject.fields().forEachRemaining(entry -> {
        String normalizedKey = entry.getKey().replace('~', '.');
        normalizedLabels.put(normalizedKey, entry.getValue());
      });

      // Rebuild the labels object with normalized keys
      ObjectNode newLabelsObject = OBJECT_MAPPER.createObjectNode();
      normalizedLabels.forEach(newLabelsObject::set);

      // Replace the old labels node with normalized one
      ObjectNode appNode = (ObjectNode) root.at("/app");
      ObjectNode objectmetaNode = (ObjectNode) appNode.get("objectmeta");
      objectmetaNode.set("labels", newLabelsObject);

      return OBJECT_MAPPER.writeValueAsString(root);
    } catch (Exception e) {
      log.warn("[CDC-Kafka][GitOps] Failed to normalize label keys, using original JSON", e);
      return jsonValue; // Return original on error
    }
  }

  private boolean handleWithRetry(String id, String optype, DebeziumChangeEvent event) {
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        log.debug("[CDC-Kafka][GitOps] {} about to handle event: id={}, optype={}", getTopicName(), id, optype);

        boolean result;
        switch (optype) {
          case "CREATE":
            result = getEventHandler().handleCreateEvent(event.getKey(), event.getValue());
            break;
          case "UPDATE":
            result = getEventHandler().handleUpdateEvent(event.getKey(), event.getValue());
            break;
          case "DELETE":
            result = getEventHandler().handleDeleteEvent(id);
            break;
          default:
            log.warn("[CDC-Kafka][GitOps] {} unknown optype={}, skipping: id={}", getTopicName(), optype, id);
            return true;
        }

        if (result) {
          return true;
        }

        log.warn("[CDC-Kafka][GitOps] {} event handler returned false (attempt {}/{}): id={}, optype={}",
            getTopicName(), attempt, MAX_RETRIES, id, optype);

      } catch (Exception e) {
        log.warn("[CDC-Kafka][GitOps] {} event processing failed (attempt {}/{}), retrying: id={}, optype={}, error={}",
            getTopicName(), attempt, MAX_RETRIES, id, optype, e.getMessage(), e);
      }

      if (attempt < MAX_RETRIES) {
        try {
          Thread.sleep(RETRY_BACKOFF_MS * attempt);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
    }
    return false;
  }

  private boolean isFeatureFlagEnabled() {
    return featureFlagService.isGlobalEnabled(FeatureName.CDS_GITOPS_ENABLE_KAFKA_CONNECT);
  }
}