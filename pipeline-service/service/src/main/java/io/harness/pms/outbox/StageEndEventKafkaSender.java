/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2025/03/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants.STAGE_END_FOR_KAFKA;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.engine.pms.audits.events.StageKafkaEvent;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.FailureInfoAvro;
import io.harness.pms.contracts.execution.StageEventAvro;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.utils.KafkaEventTimeUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.util.Collections;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class StageEndEventKafkaSender extends AbstractEventKafkaSender {
  protected boolean sendEvent(StageKafkaEvent stageEndEvent) {
    // Use the common outbox processing pattern from AbstractEventKafkaSender
    StageEventAvro stageEventAvro = mapStageEndEventToAvro(stageEndEvent);
    return processOutboxEvent(stageEndEvent.getAccountIdentifier(),
        FeatureName.PIPE_PUSH_PIPELINE_STAGE_END_EVENTS_TO_KAFKA, configuration.getStageDataIngestionTopicName(),
        stageEventAvro, stageEndEvent.getStageExecutionId(), STAGE_END_FOR_KAFKA);
  }

  @Override
  public void sendEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance, Callback callback, String eventType) {
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
    StageEventAvro stageEventAvro =
        StageEventAvro.newBuilder()
            .setLevel(YAMLFieldNameConstants.STAGE)
            .setAccountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .setOrgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .setProjectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .setParentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
            .setPipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
            .setPipelineName(executionSummary != null ? executionSummary.getName() : "")
            .setPlanExecutionId(ambiance.getPlanExecutionId())
            .setExecutionUrl(generateExecutionUrl(ambiance))
            .setStageExecutionId(nodeOutboxInfo.getNodeExecutionId())
            .setStageIdentifier(AmbianceUtils.getStageIdentifierFromAmbiance(ambiance))
            .setStageName(nodeOutboxInfo.getNodeExecution().getName())
            .setStageType(ambiance.getMetadata().getModuleType())
            .setStatus(nodeOutboxInfo.getStatus().name())
            .setEventType(eventType)
            .setCreatedAt(KafkaEventTimeUtils.getISOFormatTime(nodeOutboxInfo.getNodeExecution().getCreatedAt()))
            .setStartTs(startTsString)
            .setLastModifiedAt(
                KafkaEventTimeUtils.getISOFormatTime(nodeOutboxInfo.getNodeExecution().getLastUpdatedAt()))
            .setEndTs(endTsString)
            .setDuration(duration)
            .setFailureInfo(failureInfoAvro)
            .setFailureInfoJson(
                JsonPipelineUtils.convertToJson(NodeExecutionCDCWrapper.toFailureDataDocuments(failureInfoAvro))
                    .orElse(null))
            .setModuleInfo(null)
            .build();
    getActiveAvroProducer(accountId).get().send(configuration.getStageDataIngestionTopicName(), stageEventAvro,
        Collections.emptyMap(), stageEventAvro.getStageExecutionId().toString(), callback);

    // Send NodeExecution CDC event
    sendNodeExecutionCDCEvent(stageEventAvro,
        nodeOutboxInfo.getNodeExecutionId(), // Use nodeExecutionId as document ID
        YAMLFieldNameConstants.STAGE, // level
        eventType); // eventType (nodeStart, nodeStatusUpdate, nodeEnd)
  }

  private StageEventAvro mapStageEndEventToAvro(StageKafkaEvent stageEndEvent) {
    PipelineExecutionSummaryEntity executionSummary =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(stageEndEvent.getAccountIdentifier(),
            stageEndEvent.getPlanExecutionId(), Set.of(PlanExecutionSummaryKeys.name));

    FailureInfoAvro failureInfoAvro = mapFailureInfoProtoToAvro(stageEndEvent.getFailureInfo());
    return StageEventAvro.newBuilder()
        .setLevel(YAMLFieldNameConstants.STAGE)
        .setAccountIdentifier(stageEndEvent.getAccountIdentifier())
        .setOrgIdentifier(stageEndEvent.getOrgIdentifier())
        .setProjectIdentifier(stageEndEvent.getProjectIdentifier())
        .setParentUniqueId(stageEndEvent.getParentUniqueId())
        .setPipelineIdentifier(stageEndEvent.getPipelineIdentifier())
        .setPipelineName(executionSummary != null ? executionSummary.getName() : "")
        .setPlanExecutionId(stageEndEvent.getPlanExecutionId())
        .setExecutionUrl(pipelineExpressionHelper.generateUrl(stageEndEvent.getAccountIdentifier(),
            stageEndEvent.getOrgIdentifier(), stageEndEvent.getProjectIdentifier(),
            stageEndEvent.getPipelineIdentifier(), stageEndEvent.getPlanExecutionId(), Collections.emptyList()))
        .setStageExecutionId(stageEndEvent.getStageExecutionId())
        .setStageIdentifier(stageEndEvent.getStageIdentifier())
        .setStageName(stageEndEvent.getStageName())
        .setStageType(stageEndEvent.getStageType())
        .setCreatedAt(KafkaEventTimeUtils.getISOFormatTime(stageEndEvent.getCreatedAt()))
        .setStartTs(KafkaEventTimeUtils.getISOFormatTime(stageEndEvent.getStartTs()))
        .setEndTs(KafkaEventTimeUtils.getISOFormatTime(stageEndEvent.getEndTs()))
        .setLastModifiedAt(KafkaEventTimeUtils.getISOFormatTime(stageEndEvent.getLastModifiedAt()))
        .setDuration(KafkaEventTimeUtils.getDurationInMillis(stageEndEvent.getEndTs(), stageEndEvent.getStartTs()))
        .setStatus(stageEndEvent.getStatus())
        .setFailureInfo(failureInfoAvro)
        .setFailureInfoJson(
            JsonPipelineUtils.convertToJson(NodeExecutionCDCWrapper.toFailureDataDocuments(failureInfoAvro))
                .orElse(null))
        .setModuleInfo(null)
        .setEventType(stageEndEvent.getNodeEventType())
        .build();
  }
}