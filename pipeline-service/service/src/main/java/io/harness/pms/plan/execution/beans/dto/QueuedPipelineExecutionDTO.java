/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PriorityType;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.yaml.core.NGLabel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "QueuedPipelineExecution",
    description = "Represents a single queued pipeline execution with its global queue position")
public class QueuedPipelineExecutionDTO {
  @Schema(description = "Global position in the account queue (1-based), stable across filters. "
          + "Null for running executions, which do not hold a queue position.")
  Integer queuePosition;
  @Schema(description = "Execution ID") String planExecutionId;
  @Schema(description = "Pipeline identifier") String pipelineIdentifier;
  @Schema(description = "Pipeline display name") String pipelineName;
  @Schema(description = "Organization identifier") String orgIdentifier;
  @Schema(description = "Project identifier") String projectIdentifier;
  @Schema(description = "Queued execution status") ExecutionStatus status;
  @Schema(description = "Execution priority") PriorityType priorityType;
  @Schema(description = "When the execution was queued") Long startTs;
  @Schema(description = "Creation timestamp") Long createdAt;
  @Schema(description = "Information about who/what triggered the execution") ExecutionTriggerInfo executionTriggerInfo;
  @Schema(description = "Pipeline run number") int runSequence;
  @Schema(description = "Pipeline tags") List<NGTag> tags;
  @Schema(description = "Pipeline labels") List<NGLabel> labels;
  @JsonIgnore String parentUniqueId;
}
