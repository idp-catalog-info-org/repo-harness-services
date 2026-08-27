/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration;

import io.harness.abort.AbortedBy;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.audit.beans.custom.executions.TriggeredByInfoAuditDetails;
import io.harness.audit.beans.data.NodeExecutionEventData;
import io.harness.data.OutcomeInstance;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.engine.pms.audits.events.PipelineAbortEvent;
import io.harness.engine.pms.audits.events.PipelineEndEvent;
import io.harness.engine.pms.audits.events.PipelineKafkaEvent;
import io.harness.engine.pms.audits.events.PipelineStartEvent;
import io.harness.engine.pms.audits.events.PipelineTimeoutEvent;
import io.harness.engine.pms.audits.events.StageEndEvent;
import io.harness.engine.pms.audits.events.StageKafkaEvent;
import io.harness.engine.pms.audits.events.StageStartEvent;
import io.harness.engine.pms.audits.events.StepEndEvent;
import io.harness.engine.pms.audits.events.StepEndEvent.StepEndEventBuilder;
import io.harness.engine.pms.audits.events.TriggeredInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class NodeExecutionEventUtils {
  // Below events are for sending NodeExecutionEvents to Outbox.
  public static PipelineStartEvent mapNodeOutboxInfoToPipelineStartEvent(
      NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance) {
    return PipelineStartEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(nodeOutboxInfo.getRunSequence())
        .triggeredInfo(buildTriggeredByFromAmbiance(ambiance))
        .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
        .build();
  }

  public static StageStartEvent mapNodeOutboxInfoToStageStartEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance) {
    return StageStartEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(nodeOutboxInfo.getRunSequence())
        .stageIdentifier(nodeOutboxInfo.getNodeExecution().getIdentifier())
        .stageType(ambiance.getMetadata().getModuleType())
        .nodeExecutionId(nodeOutboxInfo.getNodeExecutionId())
        .triggeredInfo(buildTriggeredByFromAmbiance(ambiance))
        .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
        .build();
  }

  public static StageEndEvent mapNodeOutboxInfoToStageEndEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance) {
    return StageEndEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(nodeOutboxInfo.getRunSequence())
        .stageIdentifier(nodeOutboxInfo.getNodeExecution().getIdentifier())
        .stageType(ambiance.getMetadata().getModuleType())
        .nodeExecutionId(nodeOutboxInfo.getNodeExecutionId())
        .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
        .triggeredInfo(buildTriggeredByFromAmbiance(ambiance))
        .endTs(nodeOutboxInfo.getUpdatedTs())
        .status(nodeOutboxInfo.getStatus().name())
        .build();
  }

  public static StageKafkaEvent mapNodeOutboxInfoToStageKafkaEvent(
      NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance, String eventType) {
    return StageKafkaEvent.builder()
        .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
        .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
        .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(nodeOutboxInfo.getRunSequence())
        .stageExecutionId(nodeOutboxInfo.getNodeExecutionId())
        .stageIdentifier(nodeOutboxInfo.getNodeExecution().getIdentifier())
        .stageName(nodeOutboxInfo.getNodeExecution().getName())
        .stageType(ambiance.getMetadata().getModuleType())
        .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
        .triggeredInfo(buildTriggeredByFromAmbiance(ambiance))
        .endTs(nodeOutboxInfo.getUpdatedTs())
        .createdAt(nodeOutboxInfo.getNodeExecution().getCreatedAt())
        .lastModifiedAt(nodeOutboxInfo.getNodeExecution().getLastUpdatedAt())
        .status(nodeOutboxInfo.getStatus().name())
        .failureInfo(nodeOutboxInfo.getNodeExecution().getFailureInfo())
        .nodeEventType(eventType)
        .build();
  }

  public static StepEndEvent mapNodeOutboxInfoToStepEndEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance,
      List<OutcomeInstance> outcomeInstances, String logUrl, String eventType) {
    StepEndEventBuilder stepEndEventBuilder =
        StepEndEvent.builder()
            .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
            .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .planExecutionId(ambiance.getPlanExecutionId())
            .runSequence(nodeOutboxInfo.getRunSequence())
            .stepName(nodeOutboxInfo.getNodeExecution().getName())
            .stepIdentifier(nodeOutboxInfo.getNodeExecution().getIdentifier())
            .stepExecutionId(nodeOutboxInfo.getNodeExecutionId())
            .stepType(nodeOutboxInfo.getNodeExecution().getStepType().getType())
            .stageExecutionId(AmbianceUtils.getStageRuntimeIdAmbiance(ambiance))
            .stageIdentifier(AmbianceUtils.getStageLevelFromAmbiance(ambiance).get().getIdentifier())
            .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
            .createdAt(nodeOutboxInfo.getNodeExecution().getCreatedAt())
            .endTs(nodeOutboxInfo.getUpdatedTs())
            .lastModifiedAt(nodeOutboxInfo.getUpdatedTs())
            .status(nodeOutboxInfo.getStatus())
            .isRetried(EmptyPredicate.isNotEmpty(nodeOutboxInfo.getNodeExecution().getRetryIds()))
            .retryIds(nodeOutboxInfo.getNodeExecution().getRetryIds())
            // Populate this in next PR
            .stepInputs("")
            .logUrl(logUrl)
            .failureInfo(nodeOutboxInfo.getNodeExecution().getFailureInfo())
            .nodeEventType(eventType);
    if (EmptyPredicate.isNotEmpty(outcomeInstances)) {
      stepEndEventBuilder.stepOutputs(
          outcomeInstances.stream()
              .map(o -> RecastOrchestrationUtils.pruneRecasterAdditions(o.getOutcomeValue()))
              .collect(Collectors.toList()));
    }
    return stepEndEventBuilder.build();
  }
  public static PipelineTimeoutEvent mapAmbianceToTimeoutEvent(Ambiance ambiance) {
    return PipelineTimeoutEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(ambiance.getMetadata().getRunSequence())
        .build();
  }

  public static PipelineKafkaEvent mapNodeOutboxInfoToPipelineKafkaEvent(
      NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance, String eventType) {
    return PipelineKafkaEvent.builder()
        .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
        .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
        .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(nodeOutboxInfo.getRunSequence())
        .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
        .triggeredInfo(buildTriggeredByFromAmbiance(ambiance))
        .endTs(nodeOutboxInfo.getUpdatedTs())
        .createdAt(nodeOutboxInfo.getNodeExecution().getCreatedAt())
        .lastModifiedAt(nodeOutboxInfo.getNodeExecution().getLastUpdatedAt())
        .status(nodeOutboxInfo.getStatus().name())
        .failureInfo(nodeOutboxInfo.getNodeExecution().getFailureInfo())
        .tags(Collections.emptyMap()) // Empty tags for now
        .nodeEventType(eventType)
        .build();
  }

  public static PipelineAbortEvent mapAmbianceToAbortEvent(Ambiance ambiance, AbortedBy abortedBy) {
    return PipelineAbortEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(ambiance.getMetadata().getRunSequence())
        .triggeredInfo(getTriggeredInfoFromAbortInfo(ambiance, abortedBy))
        .build();
  }

  public static PipelineEndEvent mapNodeOutboxInfoToPipelineEndEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance) {
    return PipelineEndEvent.builder()
        .accountIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
        .orgIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier))
        .projectIdentifier(ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier))
        .parentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
        .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
        .planExecutionId(ambiance.getPlanExecutionId())
        .runSequence(nodeOutboxInfo.getRunSequence())
        .startTs(nodeOutboxInfo.getNodeExecution().getStartTs())
        .endTs(nodeOutboxInfo.getUpdatedTs())
        .triggeredInfo(buildTriggeredByFromAmbiance(ambiance))
        .status(nodeOutboxInfo.getStatus().name())
        .build();
  }

  private static TriggeredInfo buildTriggeredByFromAmbiance(Ambiance ambiance) {
    return TriggeredInfo.builder()
        .type(AmbianceUtils.getTriggerType(ambiance).name())
        .identifier(AmbianceUtils.getTriggerIdentifier(ambiance))
        .extraInfo(AmbianceUtils.getTriggerBy(ambiance).getExtraInfoMap())
        .build();
  }

  private static TriggeredInfo getTriggeredInfoFromAbortInfo(Ambiance ambiance, AbortedBy abortedBy) {
    return TriggeredInfo.builder()
        .type(AmbianceUtils.getTriggerType(ambiance).name())
        .identifier(abortedBy.getUserName())
        .extraInfo(Collections.singletonMap("email", abortedBy.getEmail()))
        .build();
  }

  // Below events are for Publishing NodeExecutionAudits.
  public static NodeExecutionEventData mapPipelineStartEventToNodeExecutionEventData(
      PipelineStartEvent pipelineStartEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(pipelineStartEvent.getAccountIdentifier())
        .orgIdentifier(pipelineStartEvent.getOrgIdentifier())
        .projectIdentifier(pipelineStartEvent.getProjectIdentifier())
        .pipelineIdentifier(pipelineStartEvent.getPipelineIdentifier())
        .planExecutionId(pipelineStartEvent.getPlanExecutionId())
        .runSequence(pipelineStartEvent.getRunSequence())
        .triggeredBy(getTriggeredByInfoAuditDetails(pipelineStartEvent.getTriggeredInfo()))
        .startTs(pipelineStartEvent.getStartTs())
        .build();
  }

  public static NodeExecutionEventData mapStageStartEventToNodeExecutionEventData(StageStartEvent stageStartEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(stageStartEvent.getAccountIdentifier())
        .orgIdentifier(stageStartEvent.getOrgIdentifier())
        .projectIdentifier(stageStartEvent.getProjectIdentifier())
        .pipelineIdentifier(stageStartEvent.getPipelineIdentifier())
        .runSequence(stageStartEvent.getRunSequence())
        .stageIdentifier(stageStartEvent.getStageIdentifier())
        .stageType(stageStartEvent.getStageType())
        .planExecutionId(stageStartEvent.getPlanExecutionId())
        .nodeExecutionId(stageStartEvent.getNodeExecutionId())
        .triggeredBy(getTriggeredByInfoAuditDetails(stageStartEvent.getTriggeredInfo()))
        .startTs(stageStartEvent.getStartTs())
        .build();
  }

  public static NodeExecutionEventData mapStageEndEventToNodeExecutionEventData(StageEndEvent stageEndEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(stageEndEvent.getAccountIdentifier())
        .orgIdentifier(stageEndEvent.getOrgIdentifier())
        .projectIdentifier(stageEndEvent.getProjectIdentifier())
        .pipelineIdentifier(stageEndEvent.getPipelineIdentifier())
        .runSequence(stageEndEvent.getRunSequence())
        .stageIdentifier(stageEndEvent.getStageIdentifier())
        .stageType(stageEndEvent.getStageType())
        .planExecutionId(stageEndEvent.getPlanExecutionId())
        .nodeExecutionId(stageEndEvent.getNodeExecutionId())
        .status(stageEndEvent.getStatus())
        .triggeredBy(getTriggeredByInfoAuditDetails(stageEndEvent.getTriggeredInfo()))
        .startTs(stageEndEvent.getStartTs())
        .endTs(stageEndEvent.getEndTs())
        .build();
  }

  public static NodeExecutionEventData mapPipelineTimeoutEventToNodeExecutionEventData(
      PipelineTimeoutEvent pipelineTimeoutEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(pipelineTimeoutEvent.getAccountIdentifier())
        .orgIdentifier(pipelineTimeoutEvent.getOrgIdentifier())
        .projectIdentifier(pipelineTimeoutEvent.getProjectIdentifier())
        .pipelineIdentifier(pipelineTimeoutEvent.getPipelineIdentifier())
        .planExecutionId(pipelineTimeoutEvent.getPlanExecutionId())
        .runSequence(pipelineTimeoutEvent.getRunSequence())
        .build();
  }

  public static NodeExecutionEventData mapPipelineEndEventToNodeExecutionEventData(PipelineEndEvent pipelineEndEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(pipelineEndEvent.getAccountIdentifier())
        .orgIdentifier(pipelineEndEvent.getOrgIdentifier())
        .projectIdentifier(pipelineEndEvent.getProjectIdentifier())
        .pipelineIdentifier(pipelineEndEvent.getPipelineIdentifier())
        .planExecutionId(pipelineEndEvent.getPlanExecutionId())
        .runSequence(pipelineEndEvent.getRunSequence())
        .status(pipelineEndEvent.getStatus())
        .triggeredBy(getTriggeredByInfoAuditDetails(pipelineEndEvent.getTriggeredInfo()))
        .startTs(pipelineEndEvent.getStartTs())
        .endTs(pipelineEndEvent.getEndTs())
        .build();
  }

  public static NodeExecutionEventData mapPipelineAbortEventToNodeExecutionEventData(
      PipelineAbortEvent pipelineAbortEvent) {
    return NodeExecutionEventData.builder()
        .accountIdentifier(pipelineAbortEvent.getAccountIdentifier())
        .orgIdentifier(pipelineAbortEvent.getOrgIdentifier())
        .projectIdentifier(pipelineAbortEvent.getProjectIdentifier())
        .pipelineIdentifier(pipelineAbortEvent.getPipelineIdentifier())
        .planExecutionId(pipelineAbortEvent.getPlanExecutionId())
        .runSequence(pipelineAbortEvent.getRunSequence())
        .triggeredBy(getTriggeredByInfoAuditDetails(pipelineAbortEvent.getTriggeredInfo()))
        .build();
  }

  private static TriggeredByInfoAuditDetails getTriggeredByInfoAuditDetails(TriggeredInfo triggeredInfo) {
    return TriggeredByInfoAuditDetails.builder()
        .type(triggeredInfo.getType())
        .identifier(triggeredInfo.getIdentifier())
        .extraInfo(triggeredInfo.getExtraInfo())
        .build();
  }
}
