/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.pms.notification.orchestration.handlers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.GraphUpdateEventInfo;
import io.harness.engine.observers.GraphUpdateEventObserver;
import io.harness.engine.observers.GraphUpdatesInfo;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.entity.eventlog.NotificationEventLog;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.notification.PipelineEventType;
import io.harness.notification.bean.NotificationRules;
import io.harness.observer.AsyncInformObserver;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.PipelineNotificationEventMeta;
import io.harness.pms.notification.helper.NotificationEventsHelper;
import io.harness.pms.notification.helper.NotificationHelper;
import io.harness.pms.sdk.SdkStepHelper;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PipelineEventNotificationHandler implements AsyncInformObserver, GraphUpdateEventObserver {
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject NotificationHelper notificationHelper;
  @Inject NotificationEventsHelper notificationEventsHelper;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject PlanExecutionService planExecutionService;
  @Inject SdkStepHelper sdkStepHelper;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;

  private static final Set<String> FIELDS_IN_NODE_EXECUTION = Set.of(NodeExecutionKeys.ambiance,
      NodeExecutionKeys.executionContext, NodeExecutionKeys.stepType, NodeExecutionKeys.startTs, NodeExecutionKeys.name,
      NodeExecutionKeys.status, NodeExecutionKeys.failureInfo, NodeExecutionKeys.uuid, NodeExecutionKeys.lastUpdatedAt,
      NodeExecutionKeys.group, NodeExecutionKeys.executionInputConfigured);

  private static final Set<String> FIELDS_IN_PLAN_EXECUTION_METADATA = Set.of(PlanExecutionMetadataKeys.yaml);
  private static final int GRAPH_UPDATES_PROCESSING_BATCH_SIZE = 50;
  /** Suffix for intervention notification dedupe keys; value is graph update time (epoch ms). */
  private static final char INTERVENTION_NOTIFICATION_DEDUPE_SEPARATOR = ':';

  @Override
  public void onGraphUpdate(GraphUpdatesInfo graphUpdatesInfo) {
    if (graphUpdatesInfo == null) {
      return;
    }

    LinkedList<GraphUpdateEventInfo> graphUpdateEventInfoList = graphUpdatesInfo.getGraphUpdateEventInfoList();
    String planExecutionId = graphUpdatesInfo.getPlanExecutionId();

    Optional<NodeExecution> pipelineNode =
        nodeExecutionService.getPipelineNodeExecutionWithProjections(planExecutionId, FIELDS_IN_NODE_EXECUTION);

    if (pipelineNode.isEmpty()) {
      log.error("This should not happen - Could not send notification for Plan execution entity. NodeExecution not "
          + "found for PlanExecution");
      return;
    }
    NodeExecution pipelineNodeExecution = pipelineNode.get();
    if (isEmpty(graphUpdateEventInfoList)) {
      return;
    }

    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
        NodeExecutionContextUtils.getAccountId(pipelineNodeExecution), planExecutionId,
        FIELDS_IN_PLAN_EXECUTION_METADATA);
    String yaml = planExecutionMetadata.getYaml();
    if (isEmpty(yaml)) {
      log.error("Empty yaml found in executionMetaData for execution id: {}, cannot proceed with sending notifications",
          planExecutionId);
      return;
    }

    graphUpdateEventInfoList = sortAndPrepareGraphUpdateEvents(graphUpdateEventInfoList, pipelineNodeExecution);

    // Partition the graphUpdateEventInfoList into batches
    List<List<GraphUpdateEventInfo>> partitions =
        ListUtils.partition(graphUpdateEventInfoList, GRAPH_UPDATES_PROCESSING_BATCH_SIZE);

    for (List<GraphUpdateEventInfo> currentBatchGraphUpdates : partitions) {
      Map<String, NodeExecution> nodeExecutionMap = getNodeExecutionsUpdated(currentBatchGraphUpdates);

      for (GraphUpdateEventInfo graphUpdateEventInfo : currentBatchGraphUpdates) {
        if (isUpdateInNodeExecution(graphUpdateEventInfo.getNodeType())) {
          processNodeExecution(planExecutionId, graphUpdateEventInfo,
              nodeExecutionMap.get(graphUpdateEventInfo.getNodeExecutionId()), yaml, nodeExecutionMap);
        } else if (isUpdateInPlanExecution(graphUpdateEventInfo.getNodeType())) {
          // get the nodeExecution corresponding to PIPELINE node for this planExecutionId
          processPlanExecution(planExecutionId, graphUpdateEventInfo, pipelineNodeExecution, yaml);
        }
      }
    }
  }

  private boolean isUpdateInNodeExecution(NodeType nodeType) {
    return NodeType.PLAN_NODE.equals(nodeType);
  }

  private boolean isUpdateInPlanExecution(NodeType nodeType) {
    return NodeType.PLAN.equals(nodeType);
  }

  private Map<String, NodeExecution> getNodeExecutionsUpdated(List<GraphUpdateEventInfo> graphUpdateEventInfoList) {
    Set<String> nodeExecutionIds = graphUpdateEventInfoList.stream()
                                       .filter(event -> NodeType.PLAN_NODE.equals(event.getNodeType()))
                                       .map(GraphUpdateEventInfo::getNodeExecutionId)
                                       .collect(Collectors.toSet());
    return nodeExecutionService.getAllWithFieldIncluded(nodeExecutionIds, FIELDS_IN_NODE_EXECUTION)
        .stream()
        .collect(Collectors.toMap(NodeExecution::getUuid, Function.identity()));
  }

  private LinkedList<GraphUpdateEventInfo> sortAndPrepareGraphUpdateEvents(
      LinkedList<GraphUpdateEventInfo> graphUpdateEventInfoList, NodeExecution pipelineNodeExecution) {
    // sort based on the order of the graph update that happened
    graphUpdateEventInfoList.sort(Comparator.comparingLong(GraphUpdateEventInfo::getLastUpdatedAt));

    // add a plan update event at first - when the pipeline transitions to a running state directly, the graph gen flow
    // doesn't receive the change in planExecution entity. This happens because, the planExecution entity is created
    // before the orchestration graph, and the base lastUpdatedAt is consumed from the cacheEntities collection.
    // As a workaround, we fetch this record and add it to the beginning of the graph update events.
    if (StatusUtils.flowingStatuses().contains(pipelineNodeExecution.getStatus())) {
      graphUpdateEventInfoList.addFirst(GraphUpdateEventInfo.builder()
                                            .nodeType(NodeType.PLAN)
                                            .status(pipelineNodeExecution.getStatus())
                                            .lastUpdatedAt(pipelineNodeExecution.getLastUpdatedAt())
                                            .build());
    }
    return graphUpdateEventInfoList;
  }

  private boolean isNotificationConfiguredForPipeline(NodeExecution pipelineNodeExecution) {
    return NodeExecutionContextUtils.isNotificationConfigured(pipelineNodeExecution);
  }

  // processes pipeline events; PRIORITY OF EVENTS: PIPELINE_START -> PIPELINE_SUCCESS/PIPELINE_FAILED -> PIPELINE_END
  @VisibleForTesting
  void processPlanExecution(
      String planExecutionId, GraphUpdateEventInfo graphUpdateEventInfo, NodeExecution planNodeExecution, String yaml) {
    Set<NotificationEventLog> notificationsSent =
        getNotificationsSent(planExecutionId, List.of(planNodeExecution.getUuid()));

    List<NotificationRules> notificationRules = notificationHelper.getNotificationRules(planNodeExecution, yaml);
    if (isNodeRunningUpdate(graphUpdateEventInfo)) {
      processRunningNode(PipelineEventType.PIPELINE_LEVEL, planExecutionId, graphUpdateEventInfo, planNodeExecution,
          notificationsSent, notificationRules);
    }

    if (isNodeResumedUpdate(graphUpdateEventInfo)) {
      checkAndSendResumeEvent(
          planExecutionId, graphUpdateEventInfo, planNodeExecution, notificationsSent, notificationRules);
    }

    if (isNodeCompletedUpdate(graphUpdateEventInfo)) {
      // check if PIPELINE_START already sent; if not send it
      // then, check if PIPELINE_SUCCESS/PIPELINE_FAILED already sent; if not send it
      // finally, send PIPELINE_END if not already sent
      processCompletedNode(PipelineEventType.PIPELINE_LEVEL, planExecutionId, graphUpdateEventInfo, planNodeExecution,
          notificationsSent, notificationRules);
      checkAndSendEvent(planExecutionId, graphUpdateEventInfo, planNodeExecution, PipelineEventType.PIPELINE_END,
          notificationsSent, notificationRules);
    }
  }

  private boolean isNodeCompletedUpdate(GraphUpdateEventInfo graphUpdateEventInfo) {
    return StatusUtils.isCompletedStatus(graphUpdateEventInfo.getStatus());
  }

  private boolean isNodeRunningUpdate(GraphUpdateEventInfo graphUpdateEventInfo) {
    return StatusUtils.flowingStatuses().contains(graphUpdateEventInfo.getStatus());
  }

  private boolean isNodeResumedUpdate(GraphUpdateEventInfo graphUpdateEventInfo) {
    return graphUpdateEventInfo.getPreviousStatus() != null
        && StatusUtils.userActionWaitingStatuses().contains(graphUpdateEventInfo.getPreviousStatus())
        && graphUpdateEventInfo.getStatus() == Status.RUNNING;
  }

  private boolean isNodeWaitingForUserAction(GraphUpdateEventInfo graphUpdateEventInfo) {
    return StatusUtils.userActionWaitingStatuses().contains(graphUpdateEventInfo.getStatus());
  }

  /**
   * Identifies whether a WAITING status on a stage comes directly from a stage-level
   * runtime input, instead of coming from a child step.
   *
   * We need this because WAITING_FOR_USER_ACTION notifications should only be sent at
   * the step level (for this particular flow using graph observer)
   * When a stage has runtime inputs, it enters WAITING before any
   * step runs, and no step-level event is generated. Without this check, the
   * notification would never be sent for stage-level inputs.
   *
   * The executionInputConfigured field is the definitive indicator:
   * - true: stage has execution input configured at stage level
   * - false: any INPUT_WAITING comes from a step inside the stage
   */
  private boolean isStageWaitingExecutionInput(
      GraphUpdateEventInfo graphUpdateEventInfo, NodeExecution stageNodeExecution) {
    if (!isNodeWaitingForUserAction(graphUpdateEventInfo)) {
      return false;
    }

    // If executionInput is configured for this stage → it's a stage-level execution input
    return stageNodeExecution.getExecutionInputConfigured() != null && stageNodeExecution.getExecutionInputConfigured();
  }

  private void processCompletedNode(String level, String planExecutionId, GraphUpdateEventInfo graphUpdateEventInfo,
      NodeExecution planNodeExecution, Set<NotificationEventLog> notificationsSent,
      List<NotificationRules> notificationRules) {
    processRunningNode(
        level, planExecutionId, graphUpdateEventInfo, planNodeExecution, notificationsSent, notificationRules);

    // same conditions as io.harness.pms.plan.execution.handlers.PlanStatusEventEmitterHandler.onPlanStatusUpdate
    if (graphUpdateEventInfo.getStatus().equals(Status.SUCCEEDED)
        || graphUpdateEventInfo.getStatus().equals(Status.IGNORE_FAILED)) {
      checkAndSendEvent(planExecutionId, graphUpdateEventInfo, planNodeExecution, getSuccessEventType(level),
          notificationsSent, notificationRules);
    } else if (StatusUtils.brokeAndAbortedStatuses().contains(graphUpdateEventInfo.getStatus())) {
      checkAndSendEvent(planExecutionId, graphUpdateEventInfo, planNodeExecution, getFailureEventType(level),
          notificationsSent, notificationRules);
    }
  }

  private void processRunningNode(String level, String planExecutionId, GraphUpdateEventInfo graphUpdateEventInfo,
      NodeExecution nodeExecution, Set<NotificationEventLog> notificationsSent,
      List<NotificationRules> notificationRules) {
    // check if <NODE>_START already sent; if not send it
    checkAndSendEvent(planExecutionId, graphUpdateEventInfo, nodeExecution, getStartEventType(level), notificationsSent,
        notificationRules);
  }

  // processes stage events; PRIORITY OF EVENTS: STAGE_START -> STAGE_SUCCESS/STAGE_FAILED
  @VisibleForTesting
  void processStageEvents(
      String planExecutionId, GraphUpdateEventInfo graphUpdateEventInfo, NodeExecution nodeExecution, String yaml) {
    Set<NotificationEventLog> notificationsSent =
        getNotificationsSent(planExecutionId, List.of(nodeExecution.getUuid()));

    List<NotificationRules> notificationRules = notificationHelper.getNotificationRules(nodeExecution, yaml);

    if (isNodeRunningUpdate(graphUpdateEventInfo)) {
      // check if STAGE_START already sent; if not send it
      processRunningNode(PipelineEventType.STAGE_LEVEL, planExecutionId, graphUpdateEventInfo, nodeExecution,
          notificationsSent, notificationRules);
    }

    if (isStageWaitingExecutionInput(graphUpdateEventInfo, nodeExecution)) {
      checkAndSendEvent(planExecutionId, graphUpdateEventInfo, nodeExecution, PipelineEventType.WAITING_FOR_USER_ACTION,
          notificationsSent, notificationRules);
    }

    if (isNodeCompletedUpdate(graphUpdateEventInfo)) {
      // check if STAGE_START (prior event) already sent; if not send it
      // then, check if STAGE_SUCCESS/STAGE_FAILED already sent; if not send it
      processCompletedNode(PipelineEventType.STAGE_LEVEL, planExecutionId, graphUpdateEventInfo, nodeExecution,
          notificationsSent, notificationRules);
    }
  }

  void processNodeExecution(String planExecutionId, GraphUpdateEventInfo graphUpdateEventInfo,
      NodeExecution nodeExecution, String yaml, Map<String, NodeExecution> nodeExecutionMap) {
    if (nodeExecution.getStepType() == null) {
      return;
    }
    if (OrchestrationUtils.isStageNode(nodeExecution)) {
      processStageEvents(planExecutionId, graphUpdateEventInfo, nodeExecution, yaml);
    } else if (OrchestrationUtils.isStepNode(nodeExecution)) {
      processStepEvents(planExecutionId, graphUpdateEventInfo, nodeExecution, yaml, nodeExecutionMap);
    }
  }

  @VisibleForTesting
  void processStepEvents(String planExecutionId, GraphUpdateEventInfo graphUpdateEventInfo, NodeExecution nodeExecution,
      String yaml, Map<String, NodeExecution> nodeExecutionMap) {
    String stageExecutionId = NodeExecutionContextUtils.getStageExecutionId(nodeExecution);
    Set<NotificationEventLog> notificationsSent =
        getNotificationsSent(planExecutionId, List.of(nodeExecution.getUuid(), stageExecutionId));

    List<NotificationRules> notificationRules = notificationHelper.getNotificationRules(nodeExecution, yaml);

    NodeExecution stageNodeExecution =
        Optional.ofNullable(nodeExecutionMap.get(stageExecutionId))
            .orElseGet(() -> nodeExecutionService.getWithFieldsIncluded(stageExecutionId, FIELDS_IN_NODE_EXECUTION));
    checkAndSendEvent(planExecutionId, graphUpdateEventInfo, stageNodeExecution,
        getStartEventType(PipelineEventType.STAGE_LEVEL), notificationsSent, notificationRules);

    // Only verify pending user actions at the step level.
    // On the stage level, if multiple steps can trigger a notification,
    // subsequent ones won't be triggered as they will be identified as
    // duplicates since they share the same node execution ID.
    if (isNodeWaitingForUserAction(graphUpdateEventInfo)) {
      checkAndSendEvent(planExecutionId, graphUpdateEventInfo, nodeExecution, PipelineEventType.WAITING_FOR_USER_ACTION,
          notificationsSent, notificationRules);
    }

    // condition same as io.harness.pms.execution.utils.StatusUtils.brokeStatuses
    if (StatusUtils.brokeStatuses().contains(graphUpdateEventInfo.getStatus())) {
      checkAndSendEvent(planExecutionId, graphUpdateEventInfo, nodeExecution, PipelineEventType.STEP_FAILED,
          notificationsSent, notificationRules);
    }
  }

  private void checkAndSendEvent(String planExecutionId, GraphUpdateEventInfo graphUpdateEventInfo,
      NodeExecution nodeExecution, PipelineEventType pipelineEventType, Set<NotificationEventLog> notificationsSent,
      List<NotificationRules> notificationRules) {
    String accountId = nodeExecution.getAccountId();
    String nodeUuid = nodeExecution.getUuid();

    boolean isRollback = NodeExecutionContextUtils.getExecutionMode(nodeExecution) == ExecutionMode.PIPELINE_ROLLBACK;
    boolean disableOnRollback =
        pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_DISABLE_PIPELINE_NOTIFICATIONS_ON_ROLLBACK);

    String interventionDedupeKey = null;
    long interventionNotificationTimestampMs = 0L;
    if (pipelineEventType == PipelineEventType.WAITING_FOR_USER_ACTION) {
      interventionNotificationTimestampMs = resolveInterventionDedupeTimestampMs(
          graphUpdateEventInfo.getLastUpdatedAt(), nodeExecution.getLastUpdatedAt());
      interventionDedupeKey = buildInterventionNotificationDedupeKey(nodeUuid, interventionNotificationTimestampMs);
    }

    boolean isDuplicateEvent = interventionDedupeKey != null
        ? isInterventionWaitingAlreadySent(interventionDedupeKey, notificationsSent)
        : isEventAlreadySent(nodeUuid, pipelineEventType, notificationsSent);
    boolean isEventConfigured =
        notificationHelper.isEventConfiguredForNode(nodeExecution, pipelineEventType, notificationRules);

    // Skip if rollback notifications are disabled
    if (disableOnRollback && isRollback) {
      return;
    }

    if (!isDuplicateEvent) {
      if (isEventConfigured) {
        if (interventionDedupeKey != null) {
          notificationHelper.sendNotificationEventWithLock(nodeExecution, planExecutionId,
              buildNotificationEvent(nodeExecution, graphUpdateEventInfo), pipelineEventType, interventionDedupeKey);
        } else {
          notificationHelper.sendNotificationEventWithLock(nodeExecution, planExecutionId,
              buildNotificationEvent(nodeExecution, graphUpdateEventInfo), pipelineEventType);
        }
      } else {
        if (interventionDedupeKey != null) {
          notificationHelper.sendCNSNotification(nodeExecutionService.getAmbiance(nodeExecution), pipelineEventType,
              nodeExecution, interventionNotificationTimestampMs, interventionDedupeKey);
        } else {
          notificationHelper.sendCNSNotification(nodeExecutionService.getAmbiance(nodeExecution), pipelineEventType,
              nodeExecution, nodeExecution.getLastUpdatedAt());
        }
      }
    }
  }

  @VisibleForTesting
  void checkAndSendResumeEvent(String planExecutionId, GraphUpdateEventInfo graphUpdateEventInfo,
      NodeExecution planNodeExecution, Set<NotificationEventLog> notificationsSent,
      List<NotificationRules> notificationRules) {
    String accountId = planNodeExecution.getAccountId();
    PipelineEventType pipelineEventType = PipelineEventType.PIPELINE_RESUMED;

    boolean isRollback =
        NodeExecutionContextUtils.getExecutionMode(planNodeExecution) == ExecutionMode.PIPELINE_ROLLBACK;
    boolean disableOnRollback =
        pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_DISABLE_PIPELINE_NOTIFICATIONS_ON_ROLLBACK);
    if (disableOnRollback && isRollback) {
      return;
    }

    Optional<NotificationEventLog> lastWaiting =
        notificationEventsHelper.findMostRecentByEventType(planExecutionId, PipelineEventType.WAITING_FOR_USER_ACTION);
    String dedupeNodeExecutionId =
        lastWaiting.map(NotificationEventLog::getNodeExecutionId).orElse(planNodeExecution.getUuid());

    boolean isDuplicateEvent = isEventAlreadySent(dedupeNodeExecutionId, pipelineEventType, notificationsSent);
    if (isDuplicateEvent) {
      return;
    }

    boolean isEventConfigured =
        notificationHelper.isEventConfiguredForNode(planNodeExecution, pipelineEventType, notificationRules);
    if (isEventConfigured) {
      notificationHelper.sendNotificationEventWithLock(planNodeExecution, planExecutionId,
          buildNotificationEvent(planNodeExecution, graphUpdateEventInfo), pipelineEventType, dedupeNodeExecutionId);
    } else {
      notificationHelper.sendCNSNotification(nodeExecutionService.getAmbiance(planNodeExecution), pipelineEventType,
          planNodeExecution, planNodeExecution.getLastUpdatedAt(), dedupeNodeExecutionId);
    }
  }

  private PipelineNotificationEventMeta buildNotificationEvent(
      NodeExecution nodeExecution, GraphUpdateEventInfo graphUpdateEventInfo) {
    return PipelineNotificationEventMeta.builder()
        .nodeExecutionId(nodeExecution.getUuid())
        .name(nodeExecution.getName())
        .ambiance(nodeExecutionService.getAmbiance(nodeExecution))
        .status(graphUpdateEventInfo.getStatus())
        .startedAt(nodeExecution.getStartTs())
        .lastUpdatedAt(graphUpdateEventInfo.getLastUpdatedAt())
        .failureInfo(nodeExecution.getFailureInfo())
        .stepType(nodeExecution.getStepType())
        .group(nodeExecution.getGroup())
        .build();
  }

  private Set<NotificationEventLog> getNotificationsSent(String planExecutionId, List<String> nodeExecutionIds) {
    return new HashSet<>(notificationEventsHelper.getNotificationsSent(planExecutionId, nodeExecutionIds));
  }

  private boolean isEventAlreadySent(
      String nodeExecutionId, PipelineEventType pipelineEventType, Set<NotificationEventLog> notificationEventLogs) {
    return notificationEventLogs.stream().anyMatch(log
        -> log.getPipelineEventType().equals(pipelineEventType) && log.getNodeExecutionId().equals(nodeExecutionId));
  }

  /**
   * Dedupe key for a single user-action-waiting "session". Distinct from plain {@code nodeExecutionId} so repeated
   * manual intervention on the same node can notify again; {@link #checkAndSendResumeEvent} pairs resume to the last
   * {@link PipelineEventType#WAITING_FOR_USER_ACTION} row (including this composite id).
   */
  @VisibleForTesting
  static String buildInterventionNotificationDedupeKey(String nodeExecutionUuid, long graphUpdateLastUpdatedAt) {
    return nodeExecutionUuid + INTERVENTION_NOTIFICATION_DEDUPE_SEPARATOR + graphUpdateLastUpdatedAt;
  }

  /**
   * Prefer graph update time for the intervention session; if unset (0), use the node row; if still absent, wall
   * clock so distinct manual-intervention cycles never collapse on {@code nodeUuid:0}.
   */
  @VisibleForTesting
  static long resolveInterventionDedupeTimestampMs(long graphLastUpdatedAt, Long nodeLastUpdatedAt) {
    if (graphLastUpdatedAt > 0) {
      return graphLastUpdatedAt;
    }
    if (nodeLastUpdatedAt != null && nodeLastUpdatedAt > 0) {
      return nodeLastUpdatedAt;
    }
    return System.currentTimeMillis();
  }

  private static boolean isInterventionWaitingAlreadySent(
      String interventionDedupeKey, Set<NotificationEventLog> notificationEventLogs) {
    return notificationEventLogs.stream().anyMatch(log
        -> Objects.equals(log.getPipelineEventType(), PipelineEventType.WAITING_FOR_USER_ACTION)
            && Objects.equals(log.getNodeExecutionId(), interventionDedupeKey));
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }

  private PipelineEventType getStartEventType(String level) {
    if (PipelineEventType.PIPELINE_LEVEL.equals(level)) {
      return PipelineEventType.PIPELINE_START;
    } else if (PipelineEventType.STAGE_LEVEL.equals(level)) {
      return PipelineEventType.STAGE_START;
    }
    throw new IllegalArgumentException("Unknown level: " + level);
  }

  private PipelineEventType getSuccessEventType(String level) {
    if (PipelineEventType.PIPELINE_LEVEL.equals(level)) {
      return PipelineEventType.PIPELINE_SUCCESS;
    } else if (PipelineEventType.STAGE_LEVEL.equals(level)) {
      return PipelineEventType.STAGE_SUCCESS;
    }
    throw new IllegalArgumentException("Unknown level: " + level);
  }

  private PipelineEventType getFailureEventType(String level) {
    if (PipelineEventType.PIPELINE_LEVEL.equals(level)) {
      return PipelineEventType.PIPELINE_FAILED;
    } else if (PipelineEventType.STAGE_LEVEL.equals(level)) {
      return PipelineEventType.STAGE_FAILED;
    }
    throw new IllegalArgumentException("Unknown level: " + level);
  }
}
