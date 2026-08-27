/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Artifact source types accepted by onboarding. Typing the request field with this enum rejects any value other than
 * {@code DockerRegistry}, {@code Ecr}, {@code Artifactory} or {@code HarnessArtifactSample} at deserialization time,
 * and advertises the supported values in the generated Swagger schema so the UI can discover them. Matching is exact
 * and case-sensitive: Jackson deserializes the request string against the {@link #getValue()} output, so the value
 * must be sent verbatim. Omitting the field leaves it {@code null} so the caller can skip the artifact section
 * entirely.
 *
 * <p>This is the request-facing type; {@code OnboardingContextNormalizer#resolveArtifactType} maps it to the internal
 * {@code ArtifactProviderType} used by the provisioning steps.
 */
@OwnedBy(HarnessTeam.CDC)
public enum OnboardingArtifactType {
  @JsonProperty("DockerRegistry") DOCKER_REGISTRY("DockerRegistry"),
  @JsonProperty("Ecr") ECR("Ecr"),
  @JsonProperty("Artifactory") ARTIFACTORY("Artifactory"),
  @JsonProperty("HarnessArtifactSample") HARNESS_ARTIFACT_SAMPLE("HarnessArtifactSample");

  private final String value;

  OnboardingArtifactType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
