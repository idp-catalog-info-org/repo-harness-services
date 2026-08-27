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
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "UpdateStagePriorityRequest", description = "Request body for PUT /v2/stages/queue/priority (max 10 stages)")
public class UpdatePriorityRequest {
  @Schema(description = "Stages to reprioritize; max 10 entries", required = true)
  @NotEmpty
  @Size(max = 10, message = "stages exceeds the per-request limit of 10 entries")
  @Valid
  List<StageSelectorDTO> stages;
  @Schema(description = "Priority bucket to apply", required = true) @NotNull StageQueuePriority priority;
}
