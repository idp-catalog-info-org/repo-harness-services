/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.beans.FeatureName.PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.ORCHESTRATION_LOG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.engine.observers.StepDetailsUpdateInfo;
import io.harness.engine.observers.StepDetailsUpdateObserver;
import io.harness.entity.eventlog.OrchestrationEventLog;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.EventsFrameworkKafkaTopicResolver;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.kafka.KafkaModule;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.SubCategory;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.sql.Date;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class OrchestrationLogPublisher implements PlanStatusUpdateObserver, StepDetailsUpdateObserver {
  @Inject private OrchestrationEventLogRepository orchestrationEventLogRepository;
  @Inject @Named(ORCHESTRATION_LOG) private Producer producer;
  @Inject @Named("orchestrationLogCache") Cache<String, Long> orchestrationLogCache;
  @Inject @KafkaModule.General private Optional<HKafkaProtoProducer> hKafkaProtoProducer;
  @Inject private EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @Inject OrchestrationLogConfiguration orchestrationLogConfiguration;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject PlanExecutionService planExecutionService;

  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    if (isCdcGraphEnabled(nodeUpdateInfo.getPlanExecutionId())) {
      if (isStageLevelNode(nodeUpdateInfo.getNodeExecution())) {
        batchAndSendLogEventIfRequired(nodeUpdateInfo.getPlanExecutionId(), nodeUpdateInfo.getAccountId());
      }
      return;
    }
    // if ff is enabled, do not save orchestration event into db
    if (isFeatureEnabledForSaveOrchestrationLogEvents(nodeUpdateInfo.getAccountId())) {
      batchAndSendLogEventIfRequired(nodeUpdateInfo.getPlanExecutionId(), nodeUpdateInfo.getAccountId());
    } else {
      createAndHandleEventLog(nodeUpdateInfo.getPlanExecutionId(), nodeUpdateInfo.getNodeExecutionId(),
          OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE, nodeUpdateInfo.getAccountId());
    }
  }

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance) {
    // if ff is enabled, do not save orchestration event into db
    if (isFeatureEnabledForSaveOrchestrationLogEvents(AmbianceUtils.getAccountId(ambiance))) {
      sendLogEvent(ambiance.getPlanExecutionId(), AmbianceUtils.getAccountId(ambiance));
    } else {
      createAndHandleEventLogForPlan(ambiance.getPlanExecutionId(), AmbianceUtils.obtainCurrentRuntimeId(ambiance),
          OrchestrationEventType.PLAN_EXECUTION_STATUS_UPDATE, AmbianceUtils.getAccountId(ambiance));
    }
  }

  public void onNodeUpdate(NodeUpdateInfo nodeUpdateInfo) {
    if (isCdcGraphEnabled(nodeUpdateInfo.getPlanExecutionId())) {
      if (isStageLevelNode(nodeUpdateInfo.getNodeExecution())) {
        batchAndSendLogEventIfRequired(nodeUpdateInfo.getPlanExecutionId(), nodeUpdateInfo.getAccountId());
      }
      return;
    }
    // if ff is enabled, do not save orchestration event into db
    if (isFeatureEnabledForSaveOrchestrationLogEvents(nodeUpdateInfo.getAccountId())) {
      batchAndSendLogEventIfRequired(nodeUpdateInfo.getPlanExecutionId(), nodeUpdateInfo.getAccountId());
    } else {
      createAndHandleEventLog(nodeUpdateInfo.getPlanExecutionId(), nodeUpdateInfo.getNodeExecutionId(),
          OrchestrationEventType.NODE_EXECUTION_UPDATE, nodeUpdateInfo.getAccountId());
    }
  }

  private void createAndHandleEventLog(
      String planExecutionId, String nodeExecutionId, OrchestrationEventType eventType, String accountId) {
    orchestrationEventLogRepository.save(
        OrchestrationEventLog.builder()
            .createdAt(System.currentTimeMillis())
            .nodeExecutionId(nodeExecutionId)
            .orchestrationEventType(eventType)
            .planExecutionId(planExecutionId)
            .validUntil(Date.from(OffsetDateTime.now().plus(Duration.ofDays(14)).toInstant()))
            .build());
    batchAndSendLogEventIfRequired(planExecutionId, accountId);
  }

  private void createAndHandleEventLogForPlan(
      String planExecutionId, String nodeExecutionId, OrchestrationEventType eventType, String accountId) {
    orchestrationEventLogRepository.save(
        OrchestrationEventLog.builder()
            .createdAt(System.currentTimeMillis())
            .nodeExecutionId(nodeExecutionId)
            .orchestrationEventType(eventType)
            .planExecutionId(planExecutionId)
            .validUntil(Date.from(OffsetDateTime.now().plus(Duration.ofDays(14)).toInstant()))
            .build());
    sendLogEvent(planExecutionId, accountId);
  }

  private void batchAndSendLogEventIfRequired(String planExecutionId, String accountId) {
    try {
      Long currentValue = orchestrationLogCache.get(planExecutionId);
      if (currentValue != null) {
        if (currentValue >= orchestrationLogConfiguration.getOrchestrationLogBatchSize()) {
          sendLogEvent(planExecutionId, accountId);
          orchestrationLogCache.put(planExecutionId, 1L);
        } else {
          orchestrationLogCache.put(planExecutionId, currentValue + 1);
        }
      } else {
        orchestrationLogCache.put(planExecutionId, 1L);
      }
    } catch (Exception ex) {
      log.error(String.format("Exception occurred while publishing orchestrationLogEvent for planExecutionId: %s",
                    planExecutionId),
          ex);
    }
  }

  public void sendLogEvent(String planExecutionId, String accountId) {
    OrchestrationLogEvent orchestrationLogEvent =
        OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();
    if (eventsFrameworkConfiguration.isShouldUseKafka()
        && pmsFeatureFlagHelper.isEnabled(accountId, PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING)) {
      if (hKafkaProtoProducer.isPresent()) {
        hKafkaProtoProducer.get().send(EventsFrameworkKafkaTopicResolver.getOrchestrationLogTopic(),
            orchestrationLogEvent, ImmutableMap.of("planExecutionId", planExecutionId));
        return;
      }
      log.warn("Kafka producer is not present, check the configuration. Fallback to redis.");
    }
    producer.send(Message.newBuilder()
                      .putAllMetadata(ImmutableMap.of("planExecutionId", planExecutionId))
                      .setData(orchestrationLogEvent.toByteString())
                      .build());
  }

  public void onNodeStart(NodeStartInfo nodeStartInfo) {
    if (isCdcGraphEnabled(nodeStartInfo.getNodeExecution().getPlanExecutionId())) {
      if (isStageLevelNode(nodeStartInfo.getNodeExecution())) {
        batchAndSendLogEventIfRequired(
            nodeStartInfo.getNodeExecution().getPlanExecutionId(), nodeStartInfo.getAccountId());
      }
      return;
    }
    // if ff is enabled, do not save orchestration event into db
    if (isFeatureEnabledForSaveOrchestrationLogEvents(nodeStartInfo.getAccountId())) {
      batchAndSendLogEventIfRequired(
          nodeStartInfo.getNodeExecution().getPlanExecutionId(), nodeStartInfo.getAccountId());
    } else {
      createAndHandleEventLog(nodeStartInfo.getNodeExecution().getPlanExecutionId(),
          nodeStartInfo.getNodeExecution().getUuid(), OrchestrationEventType.NODE_EXECUTION_START,
          nodeStartInfo.getAccountId());
    }
  }

  public void onPipelineInfoUpdate(String planExecutionId) {
    String accountId = getAccountId(planExecutionId);
    // if ff is enabled, do not save orchestration event into db
    if (isFeatureEnabledForSaveOrchestrationLogEvents(accountId)) {
      batchAndSendLogEventIfRequired(planExecutionId, accountId);
    } else {
      createAndHandleEventLog(planExecutionId, null, OrchestrationEventType.PIPELINE_INFO_UPDATE, accountId);
    }
  }

  public void onStageInfoUpdate(String planExecutionId, String nodeExecutionId) {
    String accountId = getAccountId(planExecutionId);
    // if ff is enabled, do not save orchestration event into db
    if (isFeatureEnabledForSaveOrchestrationLogEvents(accountId)) {
      batchAndSendLogEventIfRequired(planExecutionId, accountId);
    } else {
      createAndHandleEventLog(planExecutionId, nodeExecutionId, OrchestrationEventType.STAGE_INFO_UPDATE, accountId);
    }
  }

  @Override
  public void onStepDetailsUpdate(StepDetailsUpdateInfo stepDetailsUpdateInfo) {
    if (isCdcGraphEnabled(stepDetailsUpdateInfo.getPlanExecutionId())) {
      if (isStageLevelStepType(stepDetailsUpdateInfo.getStepType())) {
        batchAndSendLogEventIfRequired(
            stepDetailsUpdateInfo.getPlanExecutionId(), stepDetailsUpdateInfo.getAccountId());
      }
      return;
    }
    // if ff is enabled, do not save orchestration event into db
    if (isFeatureEnabledForSaveOrchestrationLogEvents(stepDetailsUpdateInfo.getAccountId())) {
      batchAndSendLogEventIfRequired(stepDetailsUpdateInfo.getPlanExecutionId(), stepDetailsUpdateInfo.getAccountId());
    } else {
      createAndHandleEventLog(stepDetailsUpdateInfo.getPlanExecutionId(), stepDetailsUpdateInfo.getNodeExecutionId(),
          OrchestrationEventType.STEP_DETAILS_UPDATE, stepDetailsUpdateInfo.getAccountId());
    }
  }

  @Override
  public void onStepInputsAdd(StepDetailsUpdateInfo stepDetailsUpdateInfo) {
    if (isCdcGraphEnabled(stepDetailsUpdateInfo.getPlanExecutionId())) {
      return;
    }
    // if ff is enabled, do not save orchestration event into db
    if (isFeatureEnabledForSaveOrchestrationLogEvents(stepDetailsUpdateInfo.getAccountId())) {
      batchAndSendLogEventIfRequired(stepDetailsUpdateInfo.getPlanExecutionId(), stepDetailsUpdateInfo.getAccountId());
    } else {
      createAndHandleEventLog(stepDetailsUpdateInfo.getPlanExecutionId(), stepDetailsUpdateInfo.getNodeExecutionId(),
          OrchestrationEventType.STEP_INPUTS_UPDATE, stepDetailsUpdateInfo.getAccountId());
    }
  }

  private boolean isStageLevelNode(NodeExecution nodeExecution) {
    if (nodeExecution == null || nodeExecution.getStepType() == null) {
      return false;
    }
    return isStageLevelStepType(nodeExecution.getStepType());
  }

  private boolean isStageLevelStepType(io.harness.pms.contracts.steps.StepType stepType) {
    if (stepType == null) {
      return false;
    }
    StepCategory category = stepType.getStepCategory();
    if (category == StepCategory.STAGE || category == StepCategory.STRATEGY) {
      return true;
    }
    return category == StepCategory.INSERT && stepType.getSubCategory() == SubCategory.STAGE_LEVEL;
  }

  private boolean isCdcGraphEnabled(String planExecutionId) {
    try {
      PlanExecution planExecution = planExecutionService.getWithFieldsIncluded(
          planExecutionId, Set.of(PlanExecutionKeys.metadata, PlanExecutionKeys.accountId));
      if (planExecution == null || planExecution.getMetadata() == null) {
        return false;
      }
      boolean wasEnabledAtStart = planExecution.getMetadata().getFeatureFlagToValueMapOrDefault(
          FeatureName.PIPE_USE_CDC_BASED_GRAPH.name(), false);
      if (!wasEnabledAtStart) {
        return false;
      }
      // FF was ON at execution start — but if now OFF globally, resume publishing
      String accountId =
          planExecution.getSetupAbstractions() != null ? planExecution.getSetupAbstractions().get("accountId") : null;
      return isNotEmpty(accountId) && pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_USE_CDC_BASED_GRAPH);
    } catch (Exception e) {
      log.warn("[CDC-GRAPH] Failed to check CDC graph FF from execution metadata for planExecutionId: {}",
          planExecutionId, e);
      return false;
    }
  }

  private boolean isFeatureEnabledForSaveOrchestrationLogEvents(String accountId) {
    return isNotEmpty(accountId)
        && pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_REMOVE_SAVE_ORCHESTRATION_LOG_EVENTS);
  }

  private String getAccountId(String planExecutionId) {
    PlanExecution planExecution =
        planExecutionService.getWithFieldsIncluded(planExecutionId, Collections.singleton(PlanExecutionKeys.ambiance));
    return planExecution != null ? AmbianceUtils.getAccountId(planExecution.getAmbiance()) : "";
  }
}
