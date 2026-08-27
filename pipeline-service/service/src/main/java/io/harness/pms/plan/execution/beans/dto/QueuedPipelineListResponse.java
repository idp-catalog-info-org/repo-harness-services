/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.domain.Page;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "QueuedPipelineListResponse",
    description = "Paginated list of queued pipeline executions with queue metadata")
public class QueuedPipelineListResponse {
  @Schema(
      description = "Paginated list of executions (queued, waiting, and/or running, depending on the requested mode)")
  Page<QueuedPipelineExecutionDTO> queuedExecutions;
  @Schema(description = "Total queued executions in the account regardless of filters") int totalQueuedInAccount;
  @Schema(description = "Total waiting executions in the account regardless of filters") int totalWaitingInAccount;
  @Schema(description = "Total running executions returned for the account regardless of filters")
  int totalRunningInAccount;
  @Schema(description = "Account's maximum concurrent execution limit") long maxConcurrency;
  @Schema(description = "Currently running execution count") long currentRunning;
}
