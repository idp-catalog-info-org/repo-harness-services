/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class AIAssetDiscoveryRecencyParser extends AbstractAIAssetParser {
  private static final String DISCOVERED_AT = "discovered_at";

  @Override
  protected Object evaluate(Map<String, Object> providerProperties, DataFetchDTO dataFetchDTO) {
    Object discoveredAtRaw = providerProperties.get(DISCOVERED_AT);
    if (discoveredAtRaw == null) {
      return buildResponse(dataFetchDTO, false, "discovered_at is missing in provider properties");
    }

    String discoveredAtStr = String.valueOf(discoveredAtRaw);
    Instant discoveredAt;
    try {
      discoveredAt = discoveredAtStr.contains("T")
              && (discoveredAtStr.endsWith("Z") || discoveredAtStr.matches(".*[+-]\\d{2}:\\d{2}$"))
          ? OffsetDateTime.parse(discoveredAtStr).toInstant()
          : Instant.parse(discoveredAtStr);
    } catch (Exception ex) {
      log.warn("Unable to parse discovered_at value: {}", discoveredAtStr);
      return buildResponse(
          dataFetchDTO, false, String.format("Unable to parse discovered_at value: %s", discoveredAtStr));
    }

    long daysSinceDiscovery = ChronoUnit.DAYS.between(discoveredAt, Instant.now());
    return buildResponse(dataFetchDTO, daysSinceDiscovery, null);
  }

  @Override
  protected String getErrorMessage() {
    return "Failed to evaluate discovery recency";
  }
}
