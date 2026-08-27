/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.RetryNodeMetadata;
import io.harness.interrupts.InterruptEffect;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;

/**
 * Utility class for parsing JSONB data from PostgreSQL.
 * Handles both Jackson-based parsing and Protobuf message parsing.
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class JsonbParserUtils {
  private static final String LOG_PREFIX = "[JSONB-PARSER]";

  /**
   * Check if JSONB is null or contains no data.
   *
   * @param jsonb the JSONB value to check
   * @return true if null or empty, false otherwise
   */
  public static boolean isEmpty(JSONB jsonb) {
    return jsonb == null || jsonb.data() == null || jsonb.data().isEmpty();
  }

  /**
   * Parse JSONB to a specified class using Jackson.
   *
   * @param jsonb the JSONB value to parse
   * @param clazz the target class
   * @return the parsed object, or null if parsing fails
   */
  public static <T> T parse(JSONB jsonb, Class<T> clazz) {
    if (isEmpty(jsonb)) {
      return null;
    }
    try {
      return JsonUtils.asObject(jsonb.data(), clazz);
    } catch (Exception e) {
      log.warn("{} Failed to parse JSONB to {}: {}", LOG_PREFIX, clazz.getSimpleName(), e.getMessage());
      return null;
    }
  }

  /**
   * Parse a protobuf message from JSONB using protobuf's JsonFormat parser.
   * This handles protobuf types that Jackson cannot deserialize.
   *
   * @param jsonb the JSONB value containing protobuf JSON
   * @param defaultInstance the default instance of the protobuf message (used for type inference)
   * @return the parsed protobuf message, or null if parsing fails
   */
  @SuppressWarnings("unchecked")
  public static <T extends Message> T parseProto(JSONB jsonb, T defaultInstance) {
    if (isEmpty(jsonb)) {
      return null;
    }
    try {
      Message.Builder builder = defaultInstance.newBuilderForType();
      JsonFormat.parser().ignoringUnknownFields().merge(jsonb.data(), builder);
      return (T) builder.build();
    } catch (Exception e) {
      log.warn(
          "{} Failed to parse protobuf {}: {}", LOG_PREFIX, defaultInstance.getClass().getSimpleName(), e.getMessage());
      return null;
    }
  }

  /**
   * Parse a list of protobuf messages from JSONB.
   *
   * @param jsonb the JSONB value containing a JSON array of protobuf messages
   * @param defaultInstance the default instance of the protobuf message (used for type inference)
   * @return the list of parsed protobuf messages, or null if parsing fails
   */
  @SuppressWarnings("unchecked")
  public static <T extends Message> List<T> parseProtoList(JSONB jsonb, T defaultInstance) {
    if (isEmpty(jsonb)) {
      return null;
    }
    try {
      List<Map<String, Object>> rawList = JsonUtils.asObject(jsonb.data(), List.class);
      if (rawList == null || rawList.isEmpty()) {
        return null;
      }
      List<T> result = new ArrayList<>();
      for (Map<String, Object> item : rawList) {
        String itemJson = JsonUtils.asJson(item);
        Message.Builder builder = defaultInstance.newBuilderForType();
        JsonFormat.parser().ignoringUnknownFields().merge(itemJson, builder);
        result.add((T) builder.build());
      }
      return result;
    } catch (Exception e) {
      log.warn("{} Failed to parse protobuf list of {}: {}", LOG_PREFIX, defaultInstance.getClass().getSimpleName(),
          e.getMessage());
      return null;
    }
  }

  /**
   * Parse outcome documents from JSONB.
   *
   * @param jsonb the JSONB value containing outcome documents map
   * @return map of outcome name to PmsOutcome, or null if parsing fails
   */
  public static Map<String, PmsOutcome> parseOutcomeDocuments(JSONB jsonb) {
    if (isEmpty(jsonb)) {
      return null;
    }
    try {
      return JsonUtils.asObject(jsonb.data(), new TypeReference<Map<String, PmsOutcome>>() {});
    } catch (Exception e) {
      log.warn("{} Failed to parse outcome_documents: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }

  /**
   * Parse step_details from JSONB.
   * The data may be stored in two formats:
   * 1. Array format: [{"name": "key1", "stepDetails": {...}}, ...] (from NodeExecutionsInfo)
   * 2. Map format: {"key1": {...}, "key2": {...}} (direct map)
   *
   * @param jsonb the JSONB value containing step details
   * @return map of step detail name to PmsStepDetails, or null if parsing fails
   */
  @SuppressWarnings("unchecked")
  public static Map<String, PmsStepDetails> parseStepDetails(JSONB jsonb) {
    if (isEmpty(jsonb)) {
      return null;
    }
    try {
      String data = jsonb.data().trim();
      // Check if it's an array (from NodeExecutionsInfo list format)
      if (data.startsWith("[")) {
        List<Map<String, Object>> list = JsonUtils.asObject(data, List.class);
        if (list == null || list.isEmpty()) {
          return null;
        }
        Map<String, PmsStepDetails> result = new HashMap<>();
        for (Map<String, Object> item : list) {
          String name = (String) item.get("name");
          Object stepDetailsObj = item.get("stepDetails");
          if (name != null && stepDetailsObj != null) {
            // Use PmsStepDetails.parse() since it extends OrchestrationMap (HashMap)
            // and cannot be deserialized directly with JsonUtils.asObject()
            PmsStepDetails stepDetails = stepDetailsObj instanceof Map
                ? PmsStepDetails.parse((Map<String, Object>) stepDetailsObj)
                : PmsStepDetails.parse(JsonUtils.asJson(stepDetailsObj));
            result.put(name, stepDetails);
          }
        }
        return result.isEmpty() ? null : result;
      } else {
        // Direct map format - parse as generic map first, then convert to PmsStepDetails
        Map<String, Map<String, Object>> rawMap =
            JsonUtils.asObject(data, new TypeReference<Map<String, Map<String, Object>>>() {});
        if (rawMap == null || rawMap.isEmpty()) {
          return null;
        }
        Map<String, PmsStepDetails> result = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : rawMap.entrySet()) {
          result.put(entry.getKey(), PmsStepDetails.parse(entry.getValue()));
        }
        return result;
      }
    } catch (Exception e) {
      log.warn("{} Failed to parse step_details: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }

  /**
   * Parse InterruptEffect list from JSONB.
   * InterruptEffect is a Java class containing protobuf fields (InterruptType, InterruptConfig).
   *
   * @param jsonb the JSONB value containing interrupt histories
   * @return list of InterruptEffect, or null if parsing fails
   */
  @SuppressWarnings("unchecked")
  public static List<InterruptEffect> parseInterruptHistories(JSONB jsonb) {
    if (isEmpty(jsonb)) {
      return null;
    }
    try {
      List<Map<String, Object>> rawList = JsonUtils.asObject(jsonb.data(), List.class);
      if (rawList == null || rawList.isEmpty()) {
        return null;
      }
      List<InterruptEffect> result = new ArrayList<>();
      for (Map<String, Object> item : rawList) {
        String interruptId = (String) item.get("interruptId");
        Number tookEffectAtNum = (Number) item.get("tookEffectAt");
        long tookEffectAt = tookEffectAtNum != null ? tookEffectAtNum.longValue() : 0L;

        // Parse InterruptType (protobuf enum)
        InterruptType interruptType = parseInterruptType(item.get("interruptType"));

        // Parse InterruptConfig (protobuf message)
        InterruptConfig interruptConfig = parseInterruptConfig(item.get("interruptConfig"));

        result.add(InterruptEffect.builder()
                       .interruptId(interruptId)
                       .tookEffectAt(tookEffectAt)
                       .interruptType(interruptType)
                       .interruptConfig(interruptConfig)
                       .build());
      }
      return result;
    } catch (Exception e) {
      log.warn("{} Failed to parse interruptHistories: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }

  /**
   * Parse InterruptType from raw object (can be String or Number).
   */
  private static InterruptType parseInterruptType(Object interruptTypeObj) {
    if (interruptTypeObj == null) {
      return null;
    }
    if (interruptTypeObj instanceof String) {
      try {
        return InterruptType.valueOf((String) interruptTypeObj);
      } catch (IllegalArgumentException e) {
        log.debug("{} InterruptType not found for value: {}", LOG_PREFIX, interruptTypeObj);
        return null;
      }
    } else if (interruptTypeObj instanceof Number) {
      return InterruptType.forNumber(((Number) interruptTypeObj).intValue());
    }
    return null;
  }

  /**
   * Parse InterruptConfig protobuf message from raw object.
   */
  private static InterruptConfig parseInterruptConfig(Object interruptConfigObj) {
    if (interruptConfigObj == null) {
      return null;
    }
    try {
      String configJson = JsonUtils.asJson(interruptConfigObj);
      InterruptConfig.Builder builder = InterruptConfig.newBuilder();
      JsonFormat.parser().ignoringUnknownFields().merge(configJson, builder);
      return builder.build();
    } catch (Exception e) {
      log.debug("{} Failed to parse InterruptConfig: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }

  /**
   * Parse RetryNodeMetadata from JSONB.
   * RetryNodeMetadata is a Java class containing a protobuf field (ExecutionTriggerInfo).
   *
   * @param jsonb the JSONB value containing retry node metadata
   * @return RetryNodeMetadata, or null if parsing fails or not present
   */
  @SuppressWarnings("unchecked")
  public static RetryNodeMetadata parseRetryNodeMetadata(JSONB jsonb) {
    if (isEmpty(jsonb)) {
      return null;
    }
    try {
      Map<String, Object> rawMap = JsonUtils.asObject(jsonb.data(), Map.class);
      if (rawMap == null || rawMap.isEmpty()) {
        return null;
      }

      // Extract scalar fields
      Number startTsNum = (Number) rawMap.get("startTs");
      Number endTsNum = (Number) rawMap.get("endTs");
      Number runSequenceNum = (Number) rawMap.get("runSequence");
      String originalPlanExecutionId = (String) rawMap.get("originalPlanExecutionId");

      // Parse ExecutionTriggerInfo (protobuf message)
      ExecutionTriggerInfo executedBy = parseExecutionTriggerInfo(rawMap.get("executedBy"));

      return RetryNodeMetadata.builder()
          .startTs(startTsNum != null ? startTsNum.longValue() : null)
          .endTs(endTsNum != null ? endTsNum.longValue() : null)
          .runSequence(runSequenceNum != null ? runSequenceNum.intValue() : null)
          .originalPlanExecutionId(originalPlanExecutionId)
          .executedBy(executedBy)
          .build();
    } catch (Exception e) {
      log.warn("{} Failed to parse retryNodeMetadata: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }

  /**
   * Parse ExecutionTriggerInfo protobuf message from raw object.
   */
  private static ExecutionTriggerInfo parseExecutionTriggerInfo(Object executedByObj) {
    if (executedByObj == null) {
      return null;
    }
    try {
      String json = JsonUtils.asJson(executedByObj);
      ExecutionTriggerInfo.Builder builder = ExecutionTriggerInfo.newBuilder();
      JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
      return builder.build();
    } catch (Exception e) {
      log.debug("{} Failed to parse ExecutionTriggerInfo: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }
}
