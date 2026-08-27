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
import io.harness.pms.contracts.execution.failure.FailureInfo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Data
@NoArgsConstructor
public class StageKafkaEvent extends BaseKafkaExecutionEvent {
  private String stageExecutionId;
  private String stageIdentifier;
  private String stageName;
  private String stageType;
  @NotNull private String status;
  private TriggeredInfo triggeredInfo;
  private String nodeEventType; // nodeStart, nodeEnd, nodeStatusUpdate

  @Builder
  public StageKafkaEvent(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String parentUniqueId, String pipelineIdentifier, String planExecutionId, String stageExecutionId,
      String stageIdentifier, String stageName, String stageType, String status, Long startTs, Long endTs,
      Long createdAt, Long lastModifiedAt, FailureInfo failureInfo, Integer runSequence, TriggeredInfo triggeredInfo,
      String nodeEventType) {
    super(accountIdentifier, orgIdentifier, projectIdentifier, parentUniqueId, pipelineIdentifier, planExecutionId,
        runSequence, createdAt, lastModifiedAt, startTs, endTs, failureInfo);
    this.stageExecutionId = stageExecutionId;
    this.stageIdentifier = stageIdentifier;
    this.stageName = stageName;
    this.stageType = stageType;
    this.status = status;
    this.triggeredInfo = triggeredInfo;
    this.nodeEventType = nodeEventType;
  }

  @Override
  public String getStatusAsString() {
    return status;
  }

  @JsonIgnore
  @Override
  public String getEventType() {
    return NodeExecutionOutboxEventConstants.STAGE_END_FOR_KAFKA;
  }
}
