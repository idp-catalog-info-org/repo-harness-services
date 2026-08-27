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
import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "StageQueueRow", description = "One queued or running stage in the customer-facing queue view")
public class StageQueueRow {
  @Schema(description = "Pipeline identifier (YAML id)") String pipelineIdentifier;
  @Schema(description = "Pipeline name (snapshot at execution time)") String pipelineName;
  @Schema(description = "Customer-facing pipeline execution id; equals planExecutionId") String pipelineExecutionId;
  @Schema(description = "Plan execution id (PMS internal handle)") String planExecutionId;
  @Schema(description = "Stage identifier (YAML id)") String stageIdentifier;
  @Schema(description = "Stage display name") String stageName;
  @Schema(description = "QUEUED or RUNNING") StageQueueStatus status;
  @Schema(description = "Priority bucket; null for RUNNING rows") StageQueuePriority priority;
  @Schema(description = "1-based pickup position within the QUEUED set after sort; null for RUNNING")
  Integer queuePosition;
  @Schema(description = "Organization identifier") String orgIdentifier;
  @Schema(description = "Project identifier") String projectIdentifier;
  @Schema(description = "Who triggered the execution") TriggeredByDTO triggeredBy;
  @Schema(description = "Trigger type (MANUAL, WEBHOOK, SCHEDULER, etc.)") String triggerType;
  @Schema(description = "Delegates eligible to pick up the stage; QUEUED rows only")
  List<DelegateRefDTO> eligibleDelegates;
  @Schema(description = "Delegate currently executing the stage; RUNNING rows only") DelegateRefDTO executingDelegate;
  @Schema(description = "Epoch millis when the runner transaction was created") long createdAt;
  @Schema(description = "Human-readable queued duration; null for RUNNING") String queuedDuration;
  @Schema(description = "Queued duration in milliseconds; null for RUNNING") Long queuedDurationMs;
}
