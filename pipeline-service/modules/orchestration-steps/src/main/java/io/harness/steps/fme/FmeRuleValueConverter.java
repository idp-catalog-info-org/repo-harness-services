/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Shared type-conversion helpers for FME rule values.
 * Used by both {@link SegmentRuleExternalConverter} and {@link FmeTargetingRulesMapper}
 * to convert polymorphic {@code ParameterField<Object>} values into their expected Java types.
 */
@OwnedBy(HarnessTeam.FME)
@UtilityClass
public class FmeRuleValueConverter {
  @SuppressWarnings("unchecked")
  public static List<String> asStringList(Object value) {
    if (value instanceof List) {
      return ((List<?>) value).stream().map(String::valueOf).collect(Collectors.toList());
    }
    throw new IllegalArgumentException(
        format("Expected a list but got: %s", value != null ? value.getClass().getSimpleName() : "null"));
  }

  public static String asSingleString(Object value) {
    if (value instanceof String) {
      return (String) value;
    }
    if (value instanceof List) {
      List<?> list = (List<?>) value;
      if (!list.isEmpty()) {
        return String.valueOf(list.get(0));
      }
      throw new IllegalArgumentException("Expected a non-empty list for single string value");
    }
    if (value != null) {
      return String.valueOf(value);
    }
    throw new IllegalArgumentException("Expected a non-null value for single string");
  }

  public static Boolean asBoolean(Object value) {
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value instanceof String) {
      return Boolean.valueOf((String) value);
    }
    if (value instanceof List) {
      List<?> list = (List<?>) value;
      if (!list.isEmpty()) {
        return asBoolean(list.get(0));
      }
      throw new IllegalArgumentException("Expected a non-empty list for boolean value");
    }
    throw new IllegalArgumentException(
        format("Expected a boolean but got: %s", value != null ? value.getClass().getSimpleName() : "null"));
  }

  public static Long asLong(Object value) {
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    if (value instanceof String) {
      try {
        return Long.parseLong((String) value);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(format("Expected a valid number string but got: '%s'", value));
      }
    }
    if (value instanceof List) {
      List<?> list = (List<?>) value;
      if (!list.isEmpty()) {
        return asLong(list.get(0));
      }
      throw new IllegalArgumentException("Expected a non-empty list for number value");
    }
    throw new IllegalArgumentException(
        format("Expected a number but got: %s", value != null ? value.getClass().getSimpleName() : "null"));
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> asBetweenMap(Object value) {
    if (value instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) value;
      if (!map.containsKey("from") || !map.containsKey("to")) {
        throw new IllegalArgumentException("Between value must have 'from' and 'to' fields");
      }
      if (map.get("from") == null || map.get("to") == null) {
        throw new IllegalArgumentException("Between value 'from' and 'to' fields must not be null");
      }
      return map;
    }
    if (value instanceof List) {
      List<?> list = (List<?>) value;
      if (list.size() == 2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("from", list.get(0));
        map.put("to", list.get(1));
        return map;
      }
      throw new IllegalArgumentException(
          format("Expected a list with exactly 2 elements [from, to] but got %d elements", list.size()));
    }
    throw new IllegalArgumentException(format(
        "Expected a map with 'from' and 'to' but got: %s", value != null ? value.getClass().getSimpleName() : "null"));
  }
}
