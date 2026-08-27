/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The {@code input} envelope of an onboarding request. Wraps the declarative {@link OnboardingContextDTO}
 * and is intended to grow: additional sibling sections (e.g. options, metadata) can be added here without
 * changing the context schema. Unknown fields are ignored so the payload can evolve without breaking callers.
 */
@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "OnboardingInput", description = "Envelope carrying the onboarding context (extensible)")
public class OnboardingInputDTO {
  @Schema(description = "Declarative context describing the resources to onboard.")
  @JsonProperty("context")
  OnboardingContextDTO context;
}
