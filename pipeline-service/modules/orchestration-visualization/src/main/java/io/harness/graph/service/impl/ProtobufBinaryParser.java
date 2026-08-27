/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.JsonUtils;

import com.google.api.client.util.Base64;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;

/**
 * Generic utility class for parsing protobuf messages from MongoDB CDC formats.
 * Handles:
 * - Spring Data MongoDB serialization: {"_serialised": {"$binary": {"base64": "...", "subType": "00"}}}
 * - SimplifiedJson: {"_serialised": "base64string"} or plain "base64string"
 * - Direct binary format: {"$binary": "base64string"}
 * - Plain JSON maps (fallback)
 *
 * All other CDC parsers (AmbianceParser, ExecutionContextParser, InterruptHistoriesParser)
 * delegate their binary extraction to this class.
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ProtobufBinaryParser {
  private static final String LOG_PREFIX = "[PROTOBUF-BINARY-PARSER]";

  /**
   * Functional interface for parsing protobuf bytes.
   */
  @FunctionalInterface
  public interface ProtobufParser<T extends Message> {
    T parseFrom(byte[] bytes) throws Exception;
  }

  /**
   * Parse a single protobuf message from CDC event and convert to JSONB.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Message> JSONB parseToJsonb(Object obj, ProtobufParser<T> parser) {
    if (obj == null) {
      return null;
    }

    try {
      if (obj instanceof Map) {
        Map<String, Object> map = (Map<String, Object>) obj;
        Optional<String> jsonOpt = parseMapToJson(map, parser);
        return jsonOpt.map(JSONB::valueOf).orElse(null);
      }
      if (obj instanceof String) {
        return decodeBase64ToJson((String) obj, parser).map(JSONB::valueOf).orElse(null);
      }

      log.debug("{} Unexpected type for protobuf: {}", LOG_PREFIX, obj.getClass().getSimpleName());
      return null;

    } catch (Exception e) {
      log.warn("{} Failed to parse protobuf: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }

  /**
   * Parse a list of protobuf messages from CDC event and convert to JSONB array.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Message> JSONB parseListToJsonb(Object listObj, ProtobufParser<T> parser) {
    if (listObj == null) {
      return null;
    }

    try {
      if (listObj instanceof List) {
        List<?> list = (List<?>) listObj;
        if (list.isEmpty()) {
          return JSONB.valueOf("[]");
        }

        // Each element is parsed to a JSON string via JsonFormat, then decoded back to an Object so the
        // list is serialized as an array of JSON objects rather than an array of escaped JSON strings.
        List<Object> parsedResponses = new ArrayList<>();
        for (Object item : list) {
          Optional<String> jsonOpt = Optional.empty();
          if (item instanceof Map) {
            jsonOpt = parseMapToJson((Map<String, Object>) item, parser);
          } else if (item instanceof String) {
            jsonOpt = decodeBase64ToJson((String) item, parser);
          }
          jsonOpt.ifPresent(json -> parsedResponses.add(JsonUtils.asObject(json, Object.class)));
        }

        if (parsedResponses.isEmpty()) {
          return null;
        }

        return JSONB.valueOf(JsonUtils.asJson(parsedResponses));
      }

      log.debug("{} Unexpected type for protobuf list: {}", LOG_PREFIX, listObj.getClass().getSimpleName());
      return null;

    } catch (Exception e) {
      log.warn("{} Failed to parse protobuf list: {}", LOG_PREFIX, e.getMessage());
      return null;
    }
  }

  /**
   * Parse a single protobuf message from CDC event and return the protobuf object.
   * Used by AmbianceParser and ExecutionContextParser which need the parsed object, not JSONB.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Message> Optional<T> parseToObject(Object obj, ProtobufParser<T> parser) {
    if (obj == null) {
      return Optional.empty();
    }

    try {
      if (obj instanceof Map) {
        Map<String, Object> map = (Map<String, Object>) obj;
        return extractBinaryFromMap(map).flatMap(base64 -> decodeBase64(base64, parser));
      }
      if (obj instanceof String) {
        return decodeBase64((String) obj, parser);
      }

      log.debug("{} Unexpected type for protobuf: {}", LOG_PREFIX, obj.getClass().getSimpleName());
      return Optional.empty();

    } catch (Exception e) {
      log.warn("{} Failed to parse protobuf object: {}", LOG_PREFIX, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Extract base64 binary string from a CDC Map (handles _serialised and $binary formats).
   * Returns empty if the map is a plain JSON map (no binary encoding).
   */
  @SuppressWarnings("unchecked")
  public static Optional<String> extractBinaryFromMap(Map<String, Object> map) {
    if (map.containsKey("_serialised")) {
      Object serialisedObj = map.get("_serialised");
      if (serialisedObj instanceof Map) {
        String base64Data = extractBase64FromBinaryFormat((Map<String, Object>) serialisedObj);
        if (base64Data != null) {
          return Optional.of(base64Data);
        }
      } else if (serialisedObj instanceof String) {
        return Optional.of((String) serialisedObj);
      }
    } else if (map.containsKey("$binary")) {
      String base64Data = extractBase64FromBinaryFormat(map);
      if (base64Data != null) {
        return Optional.of(base64Data);
      }
    }
    return Optional.empty();
  }

  /**
   * Decode base64 to protobuf object.
   */
  public static <T extends Message> Optional<T> decodeBase64(String base64Data, ProtobufParser<T> parser) {
    try {
      byte[] bytes = Base64.decodeBase64(base64Data);
      return Optional.of(parser.parseFrom(bytes));
    } catch (Exception e) {
      log.debug("{} Failed to parse protobuf binary: {}", LOG_PREFIX, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Parse a single protobuf message from a Map and return JSON string.
   */
  @SuppressWarnings("unchecked")
  private static <T extends Message> Optional<String> parseMapToJson(
      Map<String, Object> map, ProtobufParser<T> parser) {
    try {
      Optional<String> base64Opt = extractBinaryFromMap(map);
      if (base64Opt.isPresent()) {
        return decodeBase64ToJson(base64Opt.get(), parser);
      }
      // Fallback: plain JSON map (no binary encoding)
      if (!map.containsKey("_serialised") && !map.containsKey("$binary")) {
        return Optional.of(JsonUtils.asJson(map));
      }
    } catch (Exception e) {
      log.debug("{} Failed to parse protobuf from map: {}", LOG_PREFIX, e.getMessage());
    }
    return Optional.empty();
  }

  /**
   * Decode base64 to protobuf and convert to JSON string.
   */
  private static <T extends Message> Optional<String> decodeBase64ToJson(String base64Data, ProtobufParser<T> parser) {
    try {
      byte[] bytes = Base64.decodeBase64(base64Data);
      T message = parser.parseFrom(bytes);
      String json = JsonFormat.printer().omittingInsignificantWhitespace().print(message);
      return Optional.of(json);
    } catch (Exception e) {
      log.debug("{} Failed to parse protobuf binary: {}", LOG_PREFIX, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Extract base64 data from MongoDB Extended JSON Binary format.
   * Handles both: {"$binary": {"base64": "...", "subType": "00"}} and {"$binary": "base64string"}
   */
  @SuppressWarnings("unchecked")
  private static String extractBase64FromBinaryFormat(Map<String, Object> map) {
    Object binaryObj = map.get("$binary");
    if (binaryObj instanceof Map) {
      Map<String, Object> binary = (Map<String, Object>) binaryObj;
      return (String) binary.get("base64");
    } else if (binaryObj instanceof String) {
      return (String) binaryObj;
    }
    return null;
  }
}
