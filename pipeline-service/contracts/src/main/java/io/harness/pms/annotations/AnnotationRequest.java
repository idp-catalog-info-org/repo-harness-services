/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.annotations;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/**
 * Request DTO for creating/updating pipeline annotations.
 * Called directly by lite-engine after step execution.
 */
@OwnedBy(CI)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "AnnotationEntity", description = "AnnotationEntity")
public class AnnotationRequest {
  @JsonProperty("contextId") @Schema(description = "Unique context identifier for the annotation") String contextId;
  @JsonProperty("mode") @Schema(description = "Operation mode: replace, append, or delete") String mode;
  @JsonProperty("style") @Schema(description = "Visual style of the annotation") String style;
  @JsonProperty("priority") @Schema(description = "Priority level of the annotation") Integer priority;
  @JsonProperty("summary") @Schema(description = "Summary content of the annotation") String summary;
  @JsonProperty("timestamp") @Schema(description = "Timestamp when the annotation was created") Long timestamp;
  @JsonProperty("stepId") @Schema(description = "Step identifier associated with the annotation") String stepId;
}
