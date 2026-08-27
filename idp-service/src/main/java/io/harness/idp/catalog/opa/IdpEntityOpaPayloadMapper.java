/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.opa;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.from;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IdpEntityOpaPayloadMapper {
  // Matches duration format: e.g. "9d", "24h", "1.5h", "30m", "2h30m", "1h30m10s", "7d12h"
  private static final Pattern DURATION_PATTERN = Pattern.compile(
      "(?:(\\d+(?:\\.\\d+)?)d)?(?:(\\d+(?:\\.\\d+)?)h)?(?:(\\d+(?:\\.\\d+)?)m)?(?:(\\d+(?:\\.\\d+)?)s)?");

  public static Map<String, Object> buildPayload(CatalogEntity entity) {
    Map<String, Object> payload = new HashMap<>();

    payload.put("kind", entity.getKind());
    payload.put("identifier", entity.getIdentifier());
    payload.put("name", entity.getName());
    payload.put("type", entity.getType());
    payload.put("owner", entity.getOwner());
    payload.put("tags", entity.getTags());
    payload.put("spec", entity.getSpec());
    payload.put("metadata", entity.getMetadata());

    enrichBasedOnKind(payload, entity);

    return payload;
  }

  @SuppressWarnings("unchecked")
  private static void enrichBasedOnKind(Map<String, Object> payload, CatalogEntity entity) {
    String kind = entity.getKind();
    Map<String, Object> spec = entity.getSpec();
    if (spec == null) {
      return;
    }

    if ("environmentblueprint".equalsIgnoreCase(kind)) {
      enrichBlueprintPayload(payload, spec);
    } else if ("environment".equalsIgnoreCase(kind)) {
      enrichEnvironmentPayload(payload, spec);
    }
  }

  @SuppressWarnings("unchecked")
  private static void enrichBlueprintPayload(Map<String, Object> payload, Map<String, Object> spec) {
    Map<String, Object> ttl = from(spec, "ttl", Map.class);
    if (ttl != null) {
      String ttlKind = from(ttl, "kind", String.class);
      String ttlDefault = from(ttl, "default", String.class);
      String ttlMax = from(ttl, "max", String.class);

      payload.put("ttl_mode", ttlKind);
      payload.put("ttl_default_hours", parseDurationToHours(ttlDefault));
      payload.put("ttl_max_hours", parseDurationToHours(ttlMax));
    } else {
      payload.put("ttl_mode", null);
      payload.put("ttl_default_hours", null);
      payload.put("ttl_max_hours", null);
    }
  }

  @SuppressWarnings("unchecked")
  private static void enrichEnvironmentPayload(Map<String, Object> payload, Map<String, Object> spec) {
    Map<String, Object> inputs = from(spec, "inputs", Map.class);
    if (inputs != null) {
      String ttl = from(inputs, "ttl", String.class);
      payload.put("ttl", ttl);
      payload.put("ttl_hours", parseDurationToHours(ttl));
    }

    Map<String, Object> envBlueprint = from(spec, "environmentBlueprint", Map.class);
    if (envBlueprint != null) {
      payload.put("blueprint_identifier", from(envBlueprint, "identifier", String.class));
      payload.put("blueprint_version", from(envBlueprint, "version", String.class));
    }
  }

  /**
   * Parses a duration string (e.g. "9d", "24h", "1.5h", "30m", "7d12h", "2h30m10s") to hours.
   * Supports days (d), hours (h), minutes (m), seconds (s).
   */
  static Double parseDurationToHours(String duration) {
    if (isEmpty(duration)) {
      return null;
    }

    try {
      Matcher matcher = DURATION_PATTERN.matcher(duration.trim());
      if (matcher.matches()) {
        double hours = 0;
        if (matcher.group(1) != null) {
          hours += Double.parseDouble(matcher.group(1)) * 24.0;
        }
        if (matcher.group(2) != null) {
          hours += Double.parseDouble(matcher.group(2));
        }
        if (matcher.group(3) != null) {
          hours += Double.parseDouble(matcher.group(3)) / 60.0;
        }
        if (matcher.group(4) != null) {
          hours += Double.parseDouble(matcher.group(4)) / 3600.0;
        }
        return hours > 0 ? hours : null;
      }
    } catch (NumberFormatException e) {
      log.warn("Failed to parse duration string: {}", duration, e);
    }
    return null;
  }
}
