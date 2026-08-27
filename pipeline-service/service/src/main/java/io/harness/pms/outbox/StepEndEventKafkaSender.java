/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2025/03/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants.STEP_END_FOR_KAFKA;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.OutcomeInstance;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.engine.pms.audits.events.StepEndEvent;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.FailureInfoAvro;
import io.harness.pms.contracts.execution.StepEndEventAvro;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.KafkaEventTimeUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class StepEndEventKafkaSender extends AbstractEventKafkaSender {
  protected boolean sendEvent(StepEndEvent stepEndEvent) {
    // Use the common outbox processing pattern from AbstractEventKafkaSender
    var stepEndEventAvro = mapStepEndEventToAvro(stepEndEvent);
    return processOutboxEvent(stepEndEvent.getAccountIdentifier(), FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA,
        configuration.getStepDataIngestionTopicName(), stepEndEventAvro, stepEndEvent.getStepExecutionId(),
        STEP_END_FOR_KAFKA);
  }

  public void sendEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance, List<OutcomeInstance> outcomeInstances,
      Callback callback, String logUrl, String eventType) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (!isKafkaProducerInitialized(accountId)) {
      return;
    }
    PipelineExecutionSummaryEntity executionSummary = getPipelineExecutionSummary(ambiance);

    // For start and status update events, endTs or startTs might be null
    Long endTs = nodeOutboxInfo.getNodeExecution().getEndTs();
    Long startTs = nodeOutboxInfo.getNodeExecution().getStartTs();
    String endTsString = endTs != null ? KafkaEventTimeUtils.getISOFormatTime(endTs) : "";
    String startTsString = startTs != null ? KafkaEventTimeUtils.getISOFormatTime(startTs) : "";
    String duration = (endTs != null && startTs != null) ? KafkaEventTimeUtils.getDurationInMillis(endTs, startTs) : "";

    FailureInfoAvro failureInfoAvro = mapFailureInfoProtoToAvro(nodeOutboxInfo.getNodeExecution().getFailureInfo());

    StepEndEventAvro stepEndEventAvro =
        StepEndEventAvro.newBuilder()
            .setLevel(YAMLFieldNameConstants.STEP)
            .setAccountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .setOrgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .setProjectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .setParentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
            .setPipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
            .setPipelineName(executionSummary.getName())
            .setPlanExecutionId(ambiance.getPlanExecutionId())
            .setExecutionUrl(pipelineExpressionHelper.generateUrl(ambiance, null))
            .setStepName(nodeOutboxInfo.getNodeExecution().getName())
            .setStepIdentifier(nodeOutboxInfo.getNodeExecution().getIdentifier())
            .setStepExecutionId(nodeOutboxInfo.getNodeExecutionId())
            .setStepType(nodeOutboxInfo.getNodeExecution().getStepType().getType())
            .setStageExecutionId(AmbianceUtils.getStageRuntimeIdAmbiance(ambiance))
            .setStageIdentifier(AmbianceUtils.getStageLevelFromAmbiance(ambiance).get().getIdentifier())
            .setEndTs(endTsString)
            .setStartTs(startTsString)
            .setCreatedAt(KafkaEventTimeUtils.getISOFormatTime(nodeOutboxInfo.getNodeExecution().getCreatedAt()))
            .setDuration(duration)
            .setLastModifiedAt(
                KafkaEventTimeUtils.getISOFormatTime(nodeOutboxInfo.getNodeExecution().getLastUpdatedAt()))
            .setStatus(nodeOutboxInfo.getStatus().name())
            .setEventType(eventType)
            .setIsRetried(EmptyPredicate.isNotEmpty(nodeOutboxInfo.getNodeExecution().getRetryIds()))
            .setRetryIds(new ArrayList<>(nodeOutboxInfo.getNodeExecution().getRetryIds()))
            // Populate this in next PR
            .setStepInputs("")
            .setLogUrl(logUrl)
            .setFailureInfo(failureInfoAvro)
            .setFailureInfoJson(
                JsonPipelineUtils.convertToJson(NodeExecutionCDCWrapper.toFailureDataDocuments(failureInfoAvro))
                    .orElse(null))
            .setStepOutputs(outcomeInstances.stream()
                                .map(o -> RecastOrchestrationUtils.pruneRecasterAdditions(o.getOutcomeValue()))
                                .collect(Collectors.toList()))
            .build();
    getActiveAvroProducer(accountId).get().send(configuration.getStepDataIngestionTopicName(), stepEndEventAvro,
        Collections.emptyMap(), stepEndEventAvro.getStepExecutionId().toString(), callback);

    // Send NodeExecution CDC event
    sendNodeExecutionCDCEvent(stepEndEventAvro,
        nodeOutboxInfo.getNodeExecutionId(), // Use nodeExecutionId as document ID
        YAMLFieldNameConstants.STEP, // level
        eventType); // eventType (nodeStart, nodeStatusUpdate, nodeEnd)
  }

  @Override
  public void sendEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance, Callback callback, String eventType) {
    throw new UnsupportedOperationException(
        "Use sendEvent(NodeOutboxInfo, Ambiance, List<OutcomeInstance>, Callback) instead");
  }

  private StepEndEventAvro mapStepEndEventToAvro(StepEndEvent stepEndEvent) {
    PipelineExecutionSummaryEntity executionSummary =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(stepEndEvent.getAccountIdentifier(),
            stepEndEvent.getPlanExecutionId(), Set.of(PlanExecutionSummaryKeys.name));
    FailureInfoAvro failureInfoAvro = mapFailureInfoProtoToAvro(stepEndEvent.getFailureInfo());
    return StepEndEventAvro.newBuilder()
        .setLevel(YAMLFieldNameConstants.STEP)
        .setAccountIdentifier(stepEndEvent.getAccountIdentifier())
        .setOrgIdentifier(stepEndEvent.getOrgIdentifier())
        .setProjectIdentifier(stepEndEvent.getProjectIdentifier())
        .setParentUniqueId(stepEndEvent.getParentUniqueId())
        .setPipelineIdentifier(stepEndEvent.getPipelineIdentifier())
        .setPlanExecutionId(stepEndEvent.getPlanExecutionId())
        .setPipelineName(executionSummary.getName())
        .setExecutionUrl(pipelineExpressionHelper.generateUrl(stepEndEvent.getAccountIdentifier(),
            stepEndEvent.getOrgIdentifier(), stepEndEvent.getProjectIdentifier(), stepEndEvent.getPipelineIdentifier(),
            stepEndEvent.getPlanExecutionId(), Collections.emptyList()))
        .setStageExecutionId(stepEndEvent.getStageExecutionId())
        .setStepExecutionId(stepEndEvent.getStepExecutionId())
        .setStepIdentifier(stepEndEvent.getStepIdentifier())
        .setStepName(stepEndEvent.getStepName())
        .setStepType(stepEndEvent.getStepType())
        .setStatus(stepEndEvent.getStatus().name())
        .setEndTs(KafkaEventTimeUtils.getISOFormatTime(stepEndEvent.getEndTs()))
        .setStartTs(KafkaEventTimeUtils.getISOFormatTime(stepEndEvent.getStartTs()))
        .setCreatedAt(KafkaEventTimeUtils.getISOFormatTime(stepEndEvent.getCreatedAt()))
        .setDuration(KafkaEventTimeUtils.getDurationInMillis(stepEndEvent.getEndTs(), stepEndEvent.getStartTs()))
        .setLastModifiedAt(KafkaEventTimeUtils.getISOFormatTime(stepEndEvent.getLastModifiedAt()))
        .setLogUrl(stepEndEvent.getLogUrl())
        .setFailureInfo(failureInfoAvro)
        .setFailureInfoJson(
            JsonPipelineUtils.convertToJson(NodeExecutionCDCWrapper.toFailureDataDocuments(failureInfoAvro))
                .orElse(null))
        .setStageIdentifier(stepEndEvent.getStageIdentifier())
        .setIsRetried(EmptyPredicate.isNotEmpty(stepEndEvent.getRetryIds()))
        .setRetryIds(new ArrayList<>(stepEndEvent.getRetryIds()))
        .setStepInputs(stepEndEvent.getStepInputs())
        .setStepOutputs(stepEndEvent.getStepOutputs() == null ? Collections.emptyList()
                                                              : new ArrayList<>(stepEndEvent.getStepOutputs()))
        .setEventType(stepEndEvent.getNodeEventType())
        .build();
  }
}
