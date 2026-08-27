/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Deployment strategy selector for onboarding pipeline generation. When present in the request context, the
 * onboarding call renders a ready-to-run pipeline for this strategy instead of provisioning resources. Together
 * with {@link OnboardingDeploymentType} it selects the pipeline template
 * ({@code <deploymentType>_<strategy>.yaml}). Typing the request field with this enum rejects any unsupported
 * value at deserialization time. Parsing is case-insensitive and tolerant of separators (so {@code "Rolling"},
 * {@code "ROLLING"} all resolve); a blank value maps to {@code null} so the caller falls through to the standard
 * provisioning flow. Supported strategies: {@code rolling}, {@code canary}, {@code bluegreen} (parsing is
 * separator-tolerant, so {@code "blue-green"}, {@code "Blue_Green"} and {@code "bluegreen"} all resolve).
 */
@OwnedBy(HarnessTeam.CDC)
public enum OnboardingStrategy {
  @JsonProperty("rolling") ROLLING("rolling"),
  @JsonProperty("canary") CANARY("canary"),
  @JsonProperty("bluegreen") BLUE_GREEN("bluegreen");

  private final String value;

  OnboardingStrategy(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static OnboardingStrategy fromValue(String input) {
    if (input == null || input.isBlank()) {
      return null;
    }
    String canonical = input.trim().toLowerCase().replaceAll("[\\s_&-]", "");
    for (OnboardingStrategy strategy : values()) {
      if (strategy.value.equals(canonical)) {
        return strategy;
      }
    }
    throw new IllegalArgumentException(
        String.format("Unsupported strategy '%s'. Supported values: 'rolling', 'canary', 'bluegreen'.", input));
  }
}
