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
 * Deployment type for onboarding pipeline generation. Together with {@link OnboardingStrategy} it selects the
 * pipeline template ({@code <deploymentType>_<strategy>.yaml}, e.g. {@code kubernetes_rolling.yaml}), so a new
 * deployment type is a matter of adding a constant here and dropping the matching template files. Typing the request
 * field with this enum rejects any unsupported value at deserialization time. Parsing is case-insensitive and
 * tolerant of separators (so {@code "Kubernetes"}, {@code "KUBERNETES"} all resolve); a blank value maps to
 * {@code null}.
 */
@OwnedBy(HarnessTeam.CDC)
public enum OnboardingDeploymentType {
  @JsonProperty("kubernetes") KUBERNETES("kubernetes");

  private final String value;

  OnboardingDeploymentType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static OnboardingDeploymentType fromValue(String input) {
    if (input == null || input.isBlank()) {
      return null;
    }
    String canonical = input.trim().toLowerCase().replaceAll("[\\s_&-]", "");
    for (OnboardingDeploymentType type : values()) {
      if (type.value.equals(canonical)) {
        return type;
      }
    }
    throw new IllegalArgumentException(
        String.format("Unsupported deployment type '%s'. Supported values: 'kubernetes'.", input));
  }
}
