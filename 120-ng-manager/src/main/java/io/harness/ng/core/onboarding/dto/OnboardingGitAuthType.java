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
 * Git manifest auth type accepted by onboarding. Typing the request field with this enum rejects any value other
 * than {@code UsernameToken}, {@code UsernamePassword} or {@code OAuth} at deserialization time. Parsing is
 * case-insensitive and tolerant of separators (so {@code "usernametoken"}, {@code "username_token"},
 * {@code "username-password"}, {@code "oauth"} all resolve); a blank value maps to {@code null} so the caller can
 * rely on the downstream default. {@code UsernameToken}/{@code OAuth} apply to GitHub and GitLab;
 * {@code UsernamePassword} applies to Bitbucket.
 */
@OwnedBy(HarnessTeam.CDC)
public enum OnboardingGitAuthType {
  @JsonProperty("UsernameToken") USERNAME_TOKEN("UsernameToken", "usernametoken"),
  @JsonProperty("UsernamePassword") USERNAME_PASSWORD("UsernamePassword", "usernamepassword"),
  @JsonProperty("OAuth") OAUTH("OAuth", "oauth");

  private final String value;
  private final String canonical;

  OnboardingGitAuthType(String value, String canonical) {
    this.value = value;
    this.canonical = canonical;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static OnboardingGitAuthType fromValue(String input) {
    if (input == null || input.isBlank()) {
      return null;
    }
    String canonicalInput = input.trim().toLowerCase().replaceAll("[\\s_&-]", "");
    for (OnboardingGitAuthType type : values()) {
      if (type.canonical.equals(canonicalInput)) {
        return type;
      }
    }
    throw new IllegalArgumentException(String.format(
        "Unsupported git auth type '%s'. Supported values: 'UsernameToken', 'UsernamePassword', 'OAuth'.", input));
  }
}
