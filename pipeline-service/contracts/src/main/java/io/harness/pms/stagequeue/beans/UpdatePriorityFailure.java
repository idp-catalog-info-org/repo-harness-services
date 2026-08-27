/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.stagequeue.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "UpdatePriorityFailure", description = "One failed stage selector from PUT /v2/stages/queue/priority")
public class UpdatePriorityFailure {
  @Schema(description = "Pipeline execution id from the request") String pipelineExecutionId;
  @Schema(description = "Stage identifier from the request") String stageIdentifier;
  @Schema(description = "Why the update did not apply") UpdatePriorityFailureReason reason;
  @Schema(description = "Optional human-readable detail; opaque to clients") String message;
}
