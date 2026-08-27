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

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;

/**
 * Utility class for extracting strategyType from various data formats.
 * Strategy parameters can contain the strategyType field in different locations:
 * - Direct field: params.strategyType
 * - Under Kryo recast marker: params.__recast.strategyType
 * - Under strategyConfig: params.strategyConfig.strategyType
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class StrategyTypeExtractor {
  private static final String LOG_PREFIX = "[STRATEGY-TYPE]";

  /**
   * Extract strategyType from a raw object (Map or JSON String).
   * Used during CDC write operations.
   *
   * @param resolvedParams the step parameters object (can be Map or JSON String)
   * @return Optional containing the strategyType if found
   */
  @SuppressWarnings("unchecked")
  public static Optional<String> extract(Object resolvedParams) {
    if (resolvedParams == null) {
      return Optional.empty();
    }

    try {
      Map<String, Object> params = toMap(resolvedParams);
      if (params == null) {
        log.debug("{} resolvedParams could not be parsed as Map", LOG_PREFIX);
        return Optional.empty();
      }

      return extractFromMap(params);
    } catch (Exception e) {
      log.warn("{} Failed to extract strategyType: {}", LOG_PREFIX, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Extract strategyType from a JSONB column.
   * Used during read operations from PostgreSQL.
   *
   * @param stepParametersJsonb the step_parameters JSONB column value
   * @return Optional containing the strategyType if found
   */
  @SuppressWarnings("unchecked")
  public static Optional<String> extractFromJsonb(JSONB stepParametersJsonb) {
    if (JsonbParserUtils.isEmpty(stepParametersJsonb)) {
      log.debug("{} step_parameters is null or empty", LOG_PREFIX);
      return Optional.empty();
    }

    try {
      Map<String, Object> params = JsonUtils.asObject(stepParametersJsonb.data(), Map.class);
      if (params == null) {
        log.debug("{} Failed to parse step_parameters as Map", LOG_PREFIX);
        return Optional.empty();
      }

      return extractFromMap(params);
    } catch (Exception e) {
      log.warn("{} Failed to extract strategyType from JSONB: {}", LOG_PREFIX, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * Core extraction logic - looks for strategyType in various locations within the map.
   *
   * @param params the parsed parameters map
   * @return Optional containing the strategyType if found
   */
  @SuppressWarnings("unchecked")
  private static Optional<String> extractFromMap(Map<String, Object> params) {
    log.debug("{} Searching for strategyType. Available keys: {}", LOG_PREFIX, params.keySet());

    // 1. Direct field access: params.strategyType
    String strategyType = getStringValue(params, "strategyType");
    if (strategyType != null) {
      log.debug("{} Found strategyType at root level: {}", LOG_PREFIX, strategyType);
      return Optional.of(strategyType);
    }

    // 2. Check under __recast (Kryo serialization marker): params.__recast.strategyType
    Object recastData = params.get("__recast");
    if (recastData instanceof Map) {
      Map<String, Object> recastMap = (Map<String, Object>) recastData;
      strategyType = getStringValue(recastMap, "strategyType");
      if (strategyType != null) {
        log.debug("{} Found strategyType under __recast: {}", LOG_PREFIX, strategyType);
        return Optional.of(strategyType);
      }
    }

    // 3. Check under strategyConfig: params.strategyConfig.strategyType
    Object strategyConfig = params.get("strategyConfig");
    if (strategyConfig instanceof Map) {
      Map<String, Object> configMap = (Map<String, Object>) strategyConfig;
      strategyType = getStringValue(configMap, "strategyType");
      if (strategyType != null) {
        log.debug("{} Found strategyType under strategyConfig: {}", LOG_PREFIX, strategyType);
        return Optional.of(strategyType);
      }
    }

    log.debug("{} strategyType not found in any known location", LOG_PREFIX);
    return Optional.empty();
  }

  /**
   * Convert input to Map if possible.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> toMap(Object obj) {
    if (obj instanceof Map) {
      return (Map<String, Object>) obj;
    } else if (obj instanceof String) {
      return JsonUtils.asObject((String) obj, Map.class);
    }
    return null;
  }

  /**
   * Safely get a String value from a map.
   */
  private static String getStringValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value != null) {
      return value.toString();
    }
    return null;
  }
}
