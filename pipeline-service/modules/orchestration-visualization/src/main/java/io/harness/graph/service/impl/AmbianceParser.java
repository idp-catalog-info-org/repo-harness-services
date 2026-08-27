/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.graph.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;

import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for parsing Ambiance protobuf from MongoDB CDC formats.
 * Delegates binary extraction to {@link ProtobufBinaryParser}.
 */
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class AmbianceParser {
  @Value
  @Builder
  public static class AmbianceResult {
    String accountId;
    String planExecutionId;
    Ambiance ambiance;

    public boolean hasAccountId() {
      return accountId != null && !accountId.isEmpty();
    }

    public boolean hasPlanExecutionId() {
      return planExecutionId != null && !planExecutionId.isEmpty();
    }
  }

  /**
   * Parse an ambiance object from CDC event and extract relevant fields.
   */
  @SuppressWarnings("unchecked")
  public static Optional<AmbianceResult> parse(Object ambianceObj) {
    if (ambianceObj == null) {
      return Optional.empty();
    }

    try {
      // Try parsing as protobuf binary (handles Map with _serialised/$binary and plain String)
      Optional<Ambiance> parsed = ProtobufBinaryParser.parseToObject(ambianceObj, Ambiance::parseFrom);
      if (parsed.isPresent()) {
        return buildResultFromAmbiance(parsed.get());
      }

      // Fallback: plain deserialized JSON map (no binary encoding)
      if (ambianceObj instanceof Map) {
        Map<String, Object> map = (Map<String, Object>) ambianceObj;
        if (!map.containsKey("_serialised") && !map.containsKey("$binary")) {
          return parseFromPlainMap(map);
        }
      }

      return Optional.empty();
    } catch (Exception e) {
      log.warn("[AMBIANCE-PARSER] Failed to parse ambiance: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private static Optional<AmbianceResult> buildResultFromAmbiance(Ambiance ambiance) {
    String accountId = null;
    if (ambiance.getSetupAbstractionsMap().containsKey("accountId")) {
      accountId = ambiance.getSetupAbstractionsMap().get("accountId");
    }

    String planExecutionId = null;
    if (!ambiance.getPlanExecutionId().isEmpty()) {
      planExecutionId = ambiance.getPlanExecutionId();
    }

    return Optional.of(
        AmbianceResult.builder().accountId(accountId).planExecutionId(planExecutionId).ambiance(ambiance).build());
  }

  @SuppressWarnings("unchecked")
  private static Optional<AmbianceResult> parseFromPlainMap(Map<String, Object> map) {
    String accountId = null;
    Object setupAbstractionsObj = map.get("setupAbstractions");
    if (setupAbstractionsObj instanceof Map) {
      Map<String, Object> setupAbstractions = (Map<String, Object>) setupAbstractionsObj;
      Object accountIdObj = setupAbstractions.get("accountId");
      if (accountIdObj != null) {
        accountId = accountIdObj.toString();
      }
    }

    String planExecutionId = null;
    Object planExecutionIdObj = map.get("planExecutionId");
    if (planExecutionIdObj != null) {
      planExecutionId = planExecutionIdObj.toString();
    }

    if (accountId != null || planExecutionId != null) {
      return Optional.of(AmbianceResult.builder().accountId(accountId).planExecutionId(planExecutionId).build());
    }
    return Optional.empty();
  }

  /**
   * Handle ambiance sub-fields from dot-notation paths in delta updates.
   */
  public static Optional<String> extractAccountIdFromSubField(String fieldPath, Object value) {
    if ((fieldPath.equals("ambiance.setupAbstractions.accountId")
            || fieldPath.equals("ambiance.setupAbstractions.accountIdentifier"))
        && value != null) {
      return Optional.of(value.toString());
    }
    return Optional.empty();
  }
}
