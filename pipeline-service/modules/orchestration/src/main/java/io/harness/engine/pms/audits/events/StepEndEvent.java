/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.audits.events;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
public class StepEndEvent extends BaseKafkaExecutionEvent {
  private String stageExecutionId;
  private String stageIdentifier;
  private String stepExecutionId;
  private String stepName;
  private String stepIdentifier;
  private Status status;
  @NotNull private String stepType;
  private boolean isRetried;
  private List<String> retryIds;
  private String stepInputs;
  private List<String> stepOutputs;
  private String logUrl;
  private String nodeEventType; // nodeStart, nodeEnd, nodeStatusUpdate

  @Builder
  public StepEndEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier, String parentUniqueId,
      String pipelineIdentifier, String planExecutionId, String stageExecutionId, String stageIdentifier,
      String stepExecutionId, String stepName, String stepIdentifier, Status status, String stepType, Long startTs,
      Long endTs, Long createdAt, Long lastModifiedAt, FailureInfo failureInfo, Integer runSequence,
      List<String> stepOutputs, boolean isRetried, List<String> retryIds, String stepInputs, String logUrl,
      String nodeEventType) {
    super(accountIdentifier, orgIdentifier, projectIdentifier, parentUniqueId, pipelineIdentifier, planExecutionId,
        runSequence, createdAt, lastModifiedAt, startTs, endTs, failureInfo);
    this.stageExecutionId = stageExecutionId;
    this.stageIdentifier = stageIdentifier;
    this.stepExecutionId = stepExecutionId;
    this.stepName = stepName;
    this.stepIdentifier = stepIdentifier;
    this.status = status;
    this.stepType = stepType;
    this.stepOutputs = stepOutputs;
    this.isRetried = isRetried;
    this.retryIds = retryIds;
    this.stepInputs = stepInputs;
    this.logUrl = logUrl;
    this.nodeEventType = nodeEventType;
  }

  @Override
  public String getStatusAsString() {
    return status != null ? status.name() : null;
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return NodeExecutionOutboxEventConstants.STEP_END_FOR_KAFKA;
  }
}