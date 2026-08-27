/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.config;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
@OwnedBy(HarnessTeam.IDP)
public class DefaultTierGroupConfig {
  @JsonProperty String identifier;
  @JsonProperty String name;
  @JsonProperty String description;
  @JsonProperty List<TierConfig> tiers;

  @Value
  @Builder
  @Jacksonized
  public static class TierConfig {
    @JsonProperty String name;
    @JsonProperty String description;
    @JsonProperty DefaultTierIcon icon;
    @JsonProperty String colour;
    @JsonProperty Integer minScore;
    @JsonProperty Integer maxScore;
  }
}
