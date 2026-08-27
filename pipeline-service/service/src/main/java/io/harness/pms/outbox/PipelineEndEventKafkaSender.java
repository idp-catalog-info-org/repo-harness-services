/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2025/03/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants.PIPELINE_END_FOR_KAFKA;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.engine.pms.audits.events.PipelineKafkaEvent;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.FailureInfoAvro;
import io.harness.pms.contracts.execution.PipelineEventAvro;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.cdc.PipelineExecutionCDCEnrichment;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.utils.KafkaEventTimeUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PipelineEndEventKafkaSender extends AbstractEventKafkaSender {
  public boolean sendEvent(PipelineKafkaEvent pipelineEndEvent) {
    var pipelineEventAvro = mapPipelineEndEventToAvro(pipelineEndEvent);
    return processOutboxEvent(pipelineEndEvent.getAccountIdentifier(),
        FeatureName.PIPE_PUSH_PIPELINE_STAGE_END_EVENTS_TO_KAFKA, configuration.getPipelineDataIngestionTopicName(),
        pipelineEventAvro, pipelineEndEvent.getPlanExecutionId(), PIPELINE_END_FOR_KAFKA);
  }

  @Override
  public void sendEvent(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance, Callback callback, String eventType) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (!isKafkaProducerInitialized(accountId)) {
      return;
    }

    // Fetch planExecutionsSummary with all projections needed for both the Avro event and the
    // pipeline CDC enrichment in a single DB call to avoid multiple round-trips.
    PipelineExecutionSummaryEntity executionSummary =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(accountId, ambiance.getPlanExecutionId(),
            Set.of(PlanExecutionSummaryKeys.name, PlanExecutionSummaryKeys.tags, PlanExecutionSummaryKeys.runSequence,
                PlanExecutionSummaryKeys.executionTriggerInfo, PlanExecutionSummaryKeys.executedModules,
                PlanExecutionSummaryKeys.pipelineDeleted));

    Map<CharSequence, CharSequence> tags = new HashMap<>();
    if (executionSummary != null && executionSummary.getTags() != null) {
      executionSummary.getTags().forEach(tag -> tags.put(tag.getKey(), tag.getValue()));
    }

    // For start and status update events, endTs or startTs might be null
    Long endTs = nodeOutboxInfo.getNodeExecution().getEndTs();
    Long startTs = nodeOutboxInfo.getNodeExecution().getStartTs();
    String endTsString = endTs != null ? KafkaEventTimeUtils.getISOFormatTime(endTs) : "";
    String startTsString = startTs != null ? KafkaEventTimeUtils.getISOFormatTime(startTs) : "";
    String duration = (endTs != null && startTs != null) ? KafkaEventTimeUtils.getDurationInMillis(endTs, startTs) : "";

    FailureInfoAvro failureInfoAvro = mapFailureInfoProtoToAvro(nodeOutboxInfo.getNodeExecution().getFailureInfo());
    PipelineEventAvro pipelineEventAvro =
        PipelineEventAvro.newBuilder()
            .setLevel(YAMLFieldNameConstants.PIPELINE)
            .setAccountIdentifier(AmbianceUtils.getAccountId(ambiance))
            .setOrgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
            .setProjectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
            .setParentUniqueId(AmbianceUtils.getParentUniqueIdentifier(ambiance))
            .setPipelineIdentifier(AmbianceUtils.getPipelineIdentifier(ambiance))
            .setPipelineName(executionSummary != null ? executionSummary.getName() : "")
            .setPlanExecutionId(ambiance.getPlanExecutionId())
            .setExecutionUrl(generateExecutionUrl(ambiance))
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
            .setTags(tags.isEmpty() ? null : tags)
            .build();

    // Send Avro event to the Looker (data ingestion) topic — Avro schema is NOT changed
    getActiveAvroProducer(accountId).get().send(configuration.getPipelineDataIngestionTopicName(), pipelineEventAvro,
        Collections.emptyMap(), pipelineEventAvro.getPlanExecutionId().toString(), callback);

    // Build pipeline CDC enrichment (all fields sourced from planExecutionsSummary, never from Avro).
    // This is built fail-safely: any extraction error logs a warning and leaves the field null so
    // the CDC event is still sent with whatever data is available.
    PipelineExecutionCDCEnrichment enrichment = buildPipelineCDCEnrichment(executionSummary);

    // Send non-Avro CDC event to UDP topic with full pipeline enrichment
    sendNodeExecutionCDCEvent(
        pipelineEventAvro, ambiance.getPlanExecutionId(), YAMLFieldNameConstants.PIPELINE, eventType, enrichment);
  }

  /**
   * Builds a {@link PipelineExecutionCDCEnrichment} from {@code planExecutionsSummary}.
   *
   * <p>Each field is extracted independently in its own try/catch so that a failure on one
   * field (e.g. unexpected null deep inside a Protobuf object) never silently swallows the
   * others and never prevents the CDC event from being sent.
   *
   * <p>A {@code null} field in the resulting DTO means "value unavailable"; the derivation
   * config's fallback / default rule applies downstream.
   */
  private PipelineExecutionCDCEnrichment buildPipelineCDCEnrichment(PipelineExecutionSummaryEntity executionSummary) {
    var builder = PipelineExecutionCDCEnrichment.builder();

    if (executionSummary == null) {
      log.warn("PipelineExecutionSummaryEntity is null; CDC enrichment fields will be absent from pipeline CDC event");
      return builder.build();
    }

    // runSequence
    try {
      builder.runSequence(executionSummary.getRunSequence());
    } catch (Exception ex) {
      log.warn("Failed to extract runSequence for pipeline CDC enrichment", ex);
    }

    // triggerType, triggeredById, triggeredByIdentifier — all sourced from executionTriggerInfo
    try {
      ExecutionTriggerInfo triggerInfo = executionSummary.getExecutionTriggerInfo();
      if (triggerInfo != null) {
        try {
          // Protobuf enums always have a name(); UNRECOGNIZED is returned for unknown values
          if (triggerInfo.getTriggerType() != null) {
            builder.triggerType(triggerInfo.getTriggerType().name());
          }
        } catch (Exception ex) {
          log.warn("Failed to extract triggerType for pipeline CDC enrichment", ex);
        }
        try {
          if (triggerInfo.hasTriggeredBy()) {
            builder.triggeredById(triggerInfo.getTriggeredBy().getUuid());
            builder.triggeredByIdentifier(triggerInfo.getTriggeredBy().getIdentifier());
          }
        } catch (Exception ex) {
          log.warn("Failed to extract triggeredBy fields for pipeline CDC enrichment", ex);
        }
      }
    } catch (Exception ex) {
      log.warn("Failed to extract executionTriggerInfo for pipeline CDC enrichment", ex);
    }

    // executedModules — convert Set<String> to List<String>
    try {
      if (executionSummary.getExecutedModules() != null && !executionSummary.getExecutedModules().isEmpty()) {
        List<String> modules = new ArrayList<>(executionSummary.getExecutedModules());
        builder.executedModules(modules);
      }
    } catch (Exception ex) {
      log.warn("Failed to extract executedModules for pipeline CDC enrichment", ex);
    }

    // deleted (pipelineDeleted)
    try {
      builder.deleted(executionSummary.getPipelineDeleted());
    } catch (Exception ex) {
      log.warn("Failed to extract pipelineDeleted for pipeline CDC enrichment", ex);
    }

    return builder.build();
  }

  private PipelineEventAvro mapPipelineEndEventToAvro(PipelineKafkaEvent pipelineEndEvent) {
    PipelineExecutionSummaryEntity executionSummary =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(pipelineEndEvent.getAccountIdentifier(),
            pipelineEndEvent.getPlanExecutionId(),
            Set.of(PlanExecutionSummaryKeys.name, PlanExecutionSummaryKeys.tags));

    Map<CharSequence, CharSequence> tags = new HashMap<>();
    if (executionSummary != null && executionSummary.getTags() != null) {
      executionSummary.getTags().forEach(tag -> tags.put(tag.getKey(), tag.getValue()));
    }

    FailureInfoAvro failureInfoAvro = mapFailureInfoProtoToAvro(pipelineEndEvent.getFailureInfo());
    return PipelineEventAvro.newBuilder()
        .setLevel(YAMLFieldNameConstants.PIPELINE)
        .setAccountIdentifier(pipelineEndEvent.getAccountIdentifier())
        .setOrgIdentifier(pipelineEndEvent.getOrgIdentifier())
        .setProjectIdentifier(pipelineEndEvent.getProjectIdentifier())
        .setParentUniqueId(pipelineEndEvent.getParentUniqueId())
        .setPipelineIdentifier(pipelineEndEvent.getPipelineIdentifier())
        .setPlanExecutionId(pipelineEndEvent.getPlanExecutionId())
        .setPipelineName(executionSummary != null ? executionSummary.getName() : "")
        .setExecutionUrl(pipelineExpressionHelper.generateUrl(pipelineEndEvent.getAccountIdentifier(),
            pipelineEndEvent.getOrgIdentifier(), pipelineEndEvent.getProjectIdentifier(),
            pipelineEndEvent.getPipelineIdentifier(), pipelineEndEvent.getPlanExecutionId(), Collections.emptyList()))
        .setStatus(pipelineEndEvent.getStatus())
        .setStartTs(KafkaEventTimeUtils.getISOFormatTime(pipelineEndEvent.getStartTs()))
        .setEndTs(KafkaEventTimeUtils.getISOFormatTime(pipelineEndEvent.getEndTs()))
        .setDuration(
            KafkaEventTimeUtils.getDurationInMillis(pipelineEndEvent.getEndTs(), pipelineEndEvent.getStartTs()))
        .setCreatedAt(
            KafkaEventTimeUtils.getISOFormatTime(pipelineEndEvent.getCreatedAt())) // Use startTs as createdAt fallback
        .setLastModifiedAt(KafkaEventTimeUtils.getISOFormatTime(
            pipelineEndEvent.getLastModifiedAt())) // Use endTs as lastModifiedAt fallback
        .setFailureInfo(failureInfoAvro)
        .setFailureInfoJson(
            JsonPipelineUtils.convertToJson(NodeExecutionCDCWrapper.toFailureDataDocuments(failureInfoAvro))
                .orElse(null))
        .setTags(tags)
        .setEventType(pipelineEndEvent.getNodeEventType())
        .build();
  }
}
