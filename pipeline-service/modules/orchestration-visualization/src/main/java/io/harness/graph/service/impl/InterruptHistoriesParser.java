/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.serializer.JsonUtils;

import com.google.protobuf.util.JsonFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;

/**
 * Utility class for parsing InterruptEffect list from MongoDB CDC formats.
 * InterruptEffect is a Java class (not protobuf) containing a nested InterruptConfig protobuf.
 * Delegates binary extraction to {@link ProtobufBinaryParser}.
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class InterruptHistoriesParser {
  private static final String LOG_PREFIX = "[INTERRUPT-HISTORIES-PARSER]";

  /**
   * Parse interrupt histories from CDC event and convert to JSONB.
   */
  @SuppressWarnings("unchecked")
  public static JSONB parseToJsonb(Object interruptHistoriesObj) {
    if (interruptHistoriesObj == null) {
      return null;
    }

    try {
      if (interruptHistoriesObj instanceof List) {
        List<?> list = (List<?>) interruptHistoriesObj;
        if (list.isEmpty()) {
          return JSONB.valueOf("[]");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Object item : list) {
          if (item instanceof Map) {
            Map<String, Object> parsed = parseInterruptEffectToMap((Map<String, Object>) item);
            if (parsed != null) {
              results.add(parsed);
            }
          }
        }

        if (results.isEmpty()) {
          return null;
        }

        return JSONB.valueOf(JsonUtils.asJson(results));
      }

      log.debug("{} Unexpected type for interruptHistories: {}", LOG_PREFIX,
          interruptHistoriesObj.getClass().getSimpleName());
      return null;

    } catch (Exception e) {
      log.warn("{} Failed to parse interrupt histories: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }

  /**
   * Parse a single interrupt history element to JSONB, for updates that carry one element
   * rather than the whole list.
   */
  @SuppressWarnings("unchecked")
  public static JSONB parseElementToJsonb(Object interruptEffectObj) {
    if (!(interruptEffectObj instanceof Map)) {
      log.debug("{} Unexpected type for interrupt history element: {}", LOG_PREFIX,
          interruptEffectObj == null ? "null" : interruptEffectObj.getClass().getSimpleName());
      return null;
    }
    Map<String, Object> parsed = parseInterruptEffectToMap((Map<String, Object>) interruptEffectObj);
    return parsed == null ? null : JSONB.valueOf(JsonUtils.asJson(parsed));
  }

  /**
   * Parse a single InterruptEffect from a Map representation.
   * InterruptEffect is a Java class with scalar fields + nested InterruptConfig protobuf.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseInterruptEffectToMap(Map<String, Object> map) {
    try {
      Map<String, Object> result = new HashMap<>();

      Object interruptId = map.get("interruptId");
      if (interruptId != null) {
        result.put("interruptId", interruptId.toString());
      }

      Object tookEffectAt = map.get("tookEffectAt");
      if (tookEffectAt instanceof Number) {
        result.put("tookEffectAt", ((Number) tookEffectAt).longValue());
      }

      Object interruptTypeObj = map.get("interruptType");
      if (interruptTypeObj != null) {
        result.put("interruptType", interruptTypeObj.toString());
      }

      // interruptConfig is a nested protobuf — delegate binary parsing
      Object interruptConfigObj = map.get("interruptConfig");
      if (interruptConfigObj instanceof Map) {
        Map<String, Object> configMap = (Map<String, Object>) interruptConfigObj;
        Optional<String> base64Opt = ProtobufBinaryParser.extractBinaryFromMap(configMap);
        if (base64Opt.isPresent()) {
          Optional<InterruptConfig> config =
              ProtobufBinaryParser.decodeBase64(base64Opt.get(), InterruptConfig::parseFrom);
          if (config.isPresent()) {
            String configJson = JsonFormat.printer().omittingInsignificantWhitespace().print(config.get());
            result.put("interruptConfig", JsonUtils.asObject(configJson, Map.class));
          }
        } else {
          // Plain JSON map — no binary encoding
          result.put("interruptConfig", configMap);
        }
      }

      return result;
    } catch (Exception e) {
      log.debug("{} Failed to parse InterruptEffect from map: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }
}
