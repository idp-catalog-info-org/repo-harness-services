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
 * Top-level body of {@code POST /onboarding/execute}. The nested {@code input} envelope keeps the request
 * extensible: new top-level sections can be added alongside {@code input} without touching the context schema.
 * Unknown fields are ignored so the payload can evolve without breaking older callers.
 */
@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "OnboardingExecuteRequest", description = "Top-level onboarding execute request")
public class OnboardingExecuteRequestDTO {
  @Schema(description = "Envelope carrying the onboarding context.") @JsonProperty("input") OnboardingInputDTO input;
}
