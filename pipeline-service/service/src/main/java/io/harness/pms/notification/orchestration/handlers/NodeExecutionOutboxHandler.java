/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import static io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants.PIPELINE_END_FOR_KAFKA;
import static io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants.STAGE_END_FOR_KAFKA;
import static io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants.STEP_END_FOR_KAFKA;

import io.harness.AbortInfoHelper;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.OutcomeInstance;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.engine.pms.audits.events.NodeExecutionEvent;
import io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants;
import io.harness.engine.pms.data.outcome.impl.PmsOutcomeServiceImpl;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.logging.AutoLogContext;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.orchestration.NodeExecutionEventUtils;
import io.harness.pms.outbox.PipelineEndEventKafkaSender;
import io.harness.pms.outbox.StageEndEventKafkaSender;
import io.harness.pms.outbox.StageStatusEventProducer;
import io.harness.pms.outbox.StepEndEventKafkaSender;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.OutputExpressionConstants;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;

/***
 * This class constructs NodeExecutionEvents and
 * sends them to Outbox for audits.
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionOutboxHandler implements NodeExecutionStartObserver, NodeStatusUpdateObserver {
  @Inject @Named("executionOutboxService") private OutboxService executionOutboxService;
  @Inject private AbortInfoHelper abortInfoHelper;
  @Inject private NGSettingsClient settingsClient;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject protected PipelineServiceConfiguration configuration;
  @Inject PmsOutcomeServiceImpl pmsOutcomeService;
  @Inject PmsFeatureFlagService featureFlagService;
  @Inject StepEndEventKafkaSender stepEndEventKafkaSender;
  @Inject PipelineEndEventKafkaSender pipelineEndEventKafkaSender;
  @Inject StageStatusEventProducer stageStatusEventProducer;
  @Inject StageEndEventKafkaSender stageEndEventKafkaSender;

  @Override
  public void onNodeStart(NodeStartInfo nodeStartInfo) {
    if (!validatePresenceOfNodeGroupInNodeStartInfo(nodeStartInfo)) {
      return;
    }

    NodeOutboxInfo nodeOutboxInfo =
        NodeOutboxInfo.builder()
            .nodeExecution(nodeStartInfo.getNodeExecution())
            .updatedTs(nodeStartInfo.getUpdatedTs())
            .type(NodeExecutionOutboxEventConstants.NODE_START_INFO)
            .runSequence(NodeExecutionContextUtils.getRunSequence(nodeStartInfo.getNodeExecution()))
            .build();
    sendOutboxEvents(nodeOutboxInfo);
  }

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    if (!validatePresenceOfNodeGroupInNodeUpdateInfo(nodeUpdateInfo)) {
      return;
    }

    NodeOutboxInfo nodeOutboxInfo =
        NodeOutboxInfo.builder()
            .nodeExecution(nodeUpdateInfo.getNodeExecution())
            .updatedTs(nodeUpdateInfo.getUpdatedTs())
            .type(NodeExecutionOutboxEventConstants.NODE_UPDATE_INFO)
            .runSequence(NodeExecutionContextUtils.getRunSequence(nodeUpdateInfo.getNodeExecution()))
            .build();
    sendOutboxEvents(nodeOutboxInfo);
  }

  @VisibleForTesting
  void sendOutboxEvents(NodeOutboxInfo nodeOutboxInfo) {
    Ambiance ambiance = nodeExecutionService.getAmbiance(nodeOutboxInfo.getNodeExecution());
    boolean enableNodeAudit = AmbianceUtils.isNodeExecutionAuditsEnabled(ambiance);
    try {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      boolean sendStatusToGitEnabled =
          featureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT);
      boolean gitOpsStatusEnabled =
          !featureFlagService.isEnabled(accountId, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED);
      if ((sendStatusToGitEnabled || gitOpsStatusEnabled)
          && nodeOutboxInfo.getNodeExecution().getGroup().equals(NodeExecutionOutboxEventConstants.STAGE)) {
        stageStatusEventProducer.sendEvent(
            nodeOutboxInfo, nodeExecutionService.getAmbiance(nodeOutboxInfo.getNodeExecution()));
      }
    } catch (Exception ex) {
      log.error(String.format(
                    "Failed to send stage status event for nodeExecutionId: %s", nodeOutboxInfo.getNodeExecutionId()),
          ex);
    }

    if (enableNodeAudit) {
      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
        String nodeGroup = nodeOutboxInfo.getNodeExecution().getGroup();
        try {
          switch (nodeGroup) {
            case NodeExecutionOutboxEventConstants.PIPELINE:
              sendPipelineExecutionEvents(nodeOutboxInfo);
              break;
            case NodeExecutionOutboxEventConstants.STAGE:
              sendStageExecutionEvents(nodeOutboxInfo);
              break;
            default:
              log.debug(String.format(NodeExecutionOutboxEventConstants.AUDIT_NOT_SUPPORTED_MSG, nodeGroup));
          }
        } catch (Exception ex) {
          log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG, nodeGroup), ex);
        }
      }
    }
    publishEndEventData(nodeOutboxInfo, ambiance);
  }

  void publishEndEventData(NodeOutboxInfo nodeOutboxInfo, Ambiance ambiance) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String nodeGroup = nodeOutboxInfo.getNodeExecution().getGroup();
      String eventType = determineEventType(nodeOutboxInfo);
      Status status = nodeOutboxInfo.getStatus();
      try {
        switch (nodeGroup) {
          case NodeExecutionOutboxEventConstants.STEP:
            if (shouldSendStepEvent(ambiance, nodeOutboxInfo, status, eventType)) {
              try {
                sendStepExecutionEvents(nodeOutboxInfo);
              } catch (Exception ex) {
                log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG_FOR_WITH_NODE_ID,
                              nodeOutboxInfo.getNodeExecutionId()),
                    ex);
              }
            }
            break;
          case NodeExecutionOutboxEventConstants.PIPELINE:
            if (shouldSendPipelineOrStageEvent(ambiance, status, eventType)) {
              try {
                Callback callback = pipelineEndEventKafkaSender.createFailureHandlingCallback(nodeOutboxInfo, ambiance,
                    PIPELINE_END_FOR_KAFKA,
                    (nodeInfo)
                        -> NodeExecutionEventUtils.mapNodeOutboxInfoToPipelineKafkaEvent(nodeInfo, ambiance, eventType),
                    configuration.getPipelineDataIngestionTopicName(), YAMLFieldNameConstants.PIPELINE, eventType);
                pipelineEndEventKafkaSender.sendEvent(nodeOutboxInfo, ambiance, callback, eventType);
              } catch (Exception ex) {
                log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG_FOR_WITH_NODE_ID,
                              nodeOutboxInfo.getNodeExecutionId()),
                    ex);
              }
            }
            break;
          case NodeExecutionOutboxEventConstants.STAGE:
            if (shouldSendPipelineOrStageEvent(ambiance, status, eventType)) {
              try {
                Callback callback = stageEndEventKafkaSender.createFailureHandlingCallback(nodeOutboxInfo, ambiance,
                    STAGE_END_FOR_KAFKA,
                    (nodeInfo)
                        -> NodeExecutionEventUtils.mapNodeOutboxInfoToStageKafkaEvent(nodeInfo, ambiance, eventType),
                    configuration.getStageDataIngestionTopicName(), YAMLFieldNameConstants.STAGE, eventType);
                stageEndEventKafkaSender.sendEvent(nodeOutboxInfo, ambiance, callback, eventType);
              } catch (Exception ex) {
                log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG_FOR_WITH_NODE_ID,
                              nodeOutboxInfo.getNodeExecutionId()),
                    ex);
              }
            }
            break;
          default:
            log.debug(String.format(NodeExecutionOutboxEventConstants.END_EVENT_PUBLISH_SUPPORTED_MSG, nodeGroup));
        }
      } catch (Exception ex) {
        log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG, nodeGroup), ex);
      }
    }
  }

  private void sendPipelineExecutionEvents(NodeOutboxInfo nodeOutboxInfo) {
    Ambiance ambiance = nodeExecutionService.getAmbiance(nodeOutboxInfo.getNodeExecution());
    Status status = nodeOutboxInfo.getStatus();
    NodeExecutionEvent nodeExecutionEvent = null;

    try {
      // PipelineStartEvent for audit
      if (NodeExecutionOutboxEventConstants.NODE_START_INFO.equals(nodeOutboxInfo.getType())) {
        nodeExecutionEvent = NodeExecutionEventUtils.mapNodeOutboxInfoToPipelineStartEvent(nodeOutboxInfo, ambiance);
        if (nodeExecutionEvent != null) {
          executionOutboxService.save(nodeExecutionEvent);
        }
        return;
      }

      // PipelineInterruptEvents for audit
      switch (status) {
        case ABORTED:
          nodeExecutionEvent = NodeExecutionEventUtils.mapAmbianceToAbortEvent(
              ambiance, abortInfoHelper.fetchAbortedByInfoFromInterrupts(ambiance.getPlanExecutionId()));
          break;
        case EXPIRED:
          nodeExecutionEvent = NodeExecutionEventUtils.mapAmbianceToTimeoutEvent(ambiance);
          break;
        default:
          log.debug(String.format("Currently Audits are not supported for status: %s", status.name()));
      }
      // In case of Abort and Expire we need to send 2 events one being the PipelineEndEvent also!
      if (nodeExecutionEvent != null) {
        executionOutboxService.save(nodeExecutionEvent);
      }

      // PipelineEndEvent for audit
      if (StatusUtils.finalStatuses().contains(status)) {
        nodeExecutionEvent = NodeExecutionEventUtils.mapNodeOutboxInfoToPipelineEndEvent(nodeOutboxInfo, ambiance);
        if (nodeExecutionEvent != null) {
          executionOutboxService.save(nodeExecutionEvent);
        }
      }
    } catch (Exception ex) {
      log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG_FOR_WITH_NODE_ID,
                    nodeOutboxInfo.getNodeExecutionId()),
          ex);
    }
  }

  @VisibleForTesting
  protected void sendStepExecutionEvents(NodeOutboxInfo nodeOutboxInfo) {
    try {
      Ambiance ambiance = nodeExecutionService.getAmbiance(nodeOutboxInfo.getNodeExecution());
      List<OutcomeInstance> outcomeInstances =
          pmsOutcomeService.fetchOutcomeInstanceByRuntimeId(nodeOutboxInfo.getNodeExecutionId());
      // Extract logUrl from outcome instances (outcome with name "log" contains key "url")
      String logUrl = extractLogUrlFromOutcomes(outcomeInstances);
      String eventType = determineEventType(nodeOutboxInfo);
      Callback callback =
          stepEndEventKafkaSender.createFailureHandlingCallback(nodeOutboxInfo, ambiance, STEP_END_FOR_KAFKA,
              (nodeInfo)
                  -> NodeExecutionEventUtils.mapNodeOutboxInfoToStepEndEvent(
                      nodeInfo, ambiance, outcomeInstances, logUrl, eventType),
              configuration.getStepDataIngestionTopicName(), YAMLFieldNameConstants.STEP, eventType);
      stepEndEventKafkaSender.sendEvent(nodeOutboxInfo, ambiance, outcomeInstances, callback, logUrl, eventType);
    } catch (Exception ex) {
      log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG_FOR_WITH_NODE_ID,
                    nodeOutboxInfo.getNodeExecutionId()),
          ex);
    }
  }

  @VisibleForTesting
  protected void sendStageExecutionEvents(NodeOutboxInfo nodeOutboxInfo) {
    Status status = nodeOutboxInfo.getStatus();
    NodeExecutionEvent nodeExecutionEvent = null;

    try {
      // StageStartEvent for audit
      if (NodeExecutionOutboxEventConstants.NODE_START_INFO.equals(nodeOutboxInfo.getType())) {
        nodeExecutionEvent = NodeExecutionEventUtils.mapNodeOutboxInfoToStageStartEvent(
            nodeOutboxInfo, nodeExecutionService.getAmbiance(nodeOutboxInfo.getNodeExecution()));
      }

      // StageEndEvent for audit
      if (isFinalNonSkippedStatus(status)) {
        nodeExecutionEvent = NodeExecutionEventUtils.mapNodeOutboxInfoToStageEndEvent(
            nodeOutboxInfo, nodeExecutionService.getAmbiance(nodeOutboxInfo.getNodeExecution()));
      }

      if (nodeExecutionEvent != null) {
        executionOutboxService.save(nodeExecutionEvent);
      }
    } catch (Exception ex) {
      log.error(String.format(NodeExecutionOutboxEventConstants.UNEXPECTED_ERROR_MSG_FOR_WITH_NODE_ID,
                    nodeOutboxInfo.getNodeExecutionId()),
          ex);
    }
  }

  private boolean validatePresenceOfNodeGroupInNodeStartInfo(NodeStartInfo nodeStartInfo) {
    if (nodeStartInfo != null && nodeStartInfo.getNodeExecution() != null
        && nodeStartInfo.getNodeExecution().getGroup() != null) {
      return true;
    }

    log.error(String.format(NodeExecutionOutboxEventConstants.FIELDS_NOT_POPULATED_MSG));
    return false;
  }

  private boolean validatePresenceOfNodeGroupInNodeUpdateInfo(NodeUpdateInfo nodeUpdateInfo) {
    if (nodeUpdateInfo != null && nodeUpdateInfo.getNodeExecution().getGroup() != null) {
      return true;
    }

    log.error(String.format(NodeExecutionOutboxEventConstants.FIELDS_NOT_POPULATED_MSG));
    return false;
  }

  private boolean isFinalNonSkippedStatus(Status status) {
    return !Status.SKIPPED.equals(status) && StatusUtils.finalStatuses().contains(status);
  }

  private boolean shouldSendPipelineOrStageEvent(Ambiance ambiance, Status status, String eventType) {
    return (featureFlagService.isEnabled(
                AmbianceUtils.getAccountId(ambiance), FeatureName.PIPE_PUSH_PIPELINE_STAGE_END_EVENTS_TO_KAFKA)
               && isFinalNonSkippedStatus(status))
        || (eventType != null && !NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END.equals(eventType)
            && featureFlagService.isEnabled(AmbianceUtils.getAccountId(ambiance),
                FeatureName.PIPE_PUSH_NODE_START_AND_STATUS_UPDATE_EVENTS_TO_KAFKA));
  }

  private boolean shouldSendStepEvent(
      Ambiance ambiance, NodeOutboxInfo nodeOutboxInfo, Status status, String eventType) {
    return (featureFlagService.isEnabled(
                AmbianceUtils.getAccountId(ambiance), FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA)
               && ExecutionModeUtils.isLeafMode(nodeOutboxInfo.getNodeExecution().getMode())
               && isFinalNonSkippedStatus(status))
        || (eventType != null && !NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END.equals(eventType)
            && featureFlagService.isEnabled(AmbianceUtils.getAccountId(ambiance),
                FeatureName.PIPE_PUSH_NODE_START_AND_STATUS_UPDATE_EVENTS_TO_KAFKA));
  }

  private String extractLogUrlFromOutcomes(List<OutcomeInstance> outcomeInstances) {
    if (EmptyPredicate.isEmpty(outcomeInstances)) {
      return null;
    }

    try {
      return outcomeInstances.stream()
          .filter(outcome -> OutputExpressionConstants.LOG.equals(outcome.getName()))
          .findFirst()
          .map(OutcomeInstance::getOutcomeValue)
          .map(outcomeValue -> outcomeValue.get(OutputExpressionConstants.URL))
          .map(Object::toString)
          .orElse(null);
    } catch (Exception ex) {
      log.warn("Failed to extract log URL from outcome instances", ex);
      return null;
    }
  }

  private String determineEventType(NodeOutboxInfo nodeOutboxInfo) {
    String nodeOutboxInfoType = nodeOutboxInfo.getType();
    Status status = nodeOutboxInfo.getStatus();

    // Node start events
    if (NodeExecutionOutboxEventConstants.NODE_START_INFO.equals(nodeOutboxInfoType)) {
      return NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_START;
    }

    // Node end events - final statuses (excluding SKIPPED)
    if (isFinalNonSkippedStatus(status)) {
      return NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END;
    }

    // Node status update events - waiting statuses
    if (StatusUtils.waitingStatuses().contains(status)) {
      return NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_STATUS_UPDATE;
    }

    return null;
  }
}