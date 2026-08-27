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
 * Manifest store providers accepted by onboarding. Typing the request field with this enum rejects any value
 * other than {@code github}, {@code bitbucket}, {@code gitlab} or {@code harnessCode} at deserialization time, and
 * advertises the supported values in the generated Swagger schema so the UI can discover them. Parsing is
 * case-insensitive and tolerant of separators (so {@code "GitHub"}, {@code "HARNESS_CODE"} and {@code "harness-code"}
 * all resolve); a blank value maps to {@code null} so the caller can omit the manifest section entirely.
 *
 * <p>This is the request-facing type; {@code OnboardingContextNormalizer#resolveManifestType} maps it to the
 * internal {@code ManifestProviderType} used by the provisioning steps.
 */
@OwnedBy(HarnessTeam.CDC)
public enum OnboardingManifestType {
  @JsonProperty("github") GITHUB("github"),
  @JsonProperty("bitbucket") BITBUCKET("bitbucket"),
  @JsonProperty("gitlab") GITLAB("gitlab"),
  @JsonProperty("harnessCode") HARNESS_CODE("harnessCode");

  private final String value;

  OnboardingManifestType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  /** Collapses case and separators so comparisons are forgiving of caller formatting. */
  private static String canonical(String value) {
    return value.trim().toLowerCase().replaceAll("[\\s_&-]", "");
  }

  @JsonCreator
  public static OnboardingManifestType fromValue(String input) {
    if (input == null || input.isBlank()) {
      return null;
    }
    String canonical = canonical(input);
    for (OnboardingManifestType type : values()) {
      if (canonical(type.value).equals(canonical)) {
        return type;
      }
    }
    throw new IllegalArgumentException(String.format(
        "Unsupported manifest type '%s'. Supported values: 'github', 'bitbucket', 'gitlab', 'harnessCode'.", input));
  }
}
