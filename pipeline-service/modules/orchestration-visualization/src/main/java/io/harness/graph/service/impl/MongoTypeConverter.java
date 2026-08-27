/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Utility class for converting MongoDB Extended JSON types to Java types.
 * Handles various formats from Debezium CDC including:
 * - Extended JSON: {"$numberLong": "123456"}
 * - Plain values: 123456
 * - String representations: "123456"
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class MongoTypeConverter {
  /**
   * Convert a value to Long, handling MongoDB Extended JSON format.
   *
   * @param value the value to convert (can be Number, String, or Map with $numberLong)
   * @return the Long value, or null if conversion fails
   */
  public static Long toLong(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    if (value instanceof String) {
      try {
        return Long.parseLong((String) value);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Convert a value to Integer, handling various input formats.
   *
   * @param value the value to convert (can be Number or String)
   * @return the Integer value, or null if conversion fails
   */
  public static Integer toInteger(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value instanceof String) {
      try {
        return Integer.parseInt((String) value);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Convert a value to Boolean, handling various input formats.
   *
   * @param value the value to convert (can be Boolean or String)
   * @return the Boolean value, or null if conversion fails
   */
  @SuppressWarnings("ReturnNullInsteadOfBoolean")
  public static Boolean toBoolean(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof String) {
      return Boolean.parseBoolean((String) value);
    }
    return null;
  }

  /**
   * Extract a Long value from MongoDB Extended JSON format.
   * Handles both Extended JSON: {"$numberLong": "123456"} and plain values.
   *
   * @param value the value to extract (can be Map with $numberLong, Number, or String)
   * @return the Long value, or null if extraction fails
   */
  @SuppressWarnings("unchecked")
  public static Long extractLongFromExtendedJson(Object value) {
    if (value == null) {
      return null;
    }
    // Handle MongoDB Extended JSON: {"$numberLong": "1234567890123"}
    if (value instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) value;
      if (map.containsKey("$numberLong")) {
        Object numberLong = map.get("$numberLong");
        if (numberLong instanceof String) {
          try {
            return Long.parseLong((String) numberLong);
          } catch (NumberFormatException e) {
            return null;
          }
        } else if (numberLong instanceof Number) {
          return ((Number) numberLong).longValue();
        }
      }
    }
    // Fall back to standard conversion
    return toLong(value);
  }

  /**
   * Safely convert a value to String.
   *
   * @param value the value to convert
   * @return the String representation, or null if value is null
   */
  public static String toString(Object value) {
    return value != null ? value.toString() : null;
  }
}
