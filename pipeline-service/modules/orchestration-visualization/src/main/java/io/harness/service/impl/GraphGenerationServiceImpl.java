/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.time.Duration.ofDays;
import static org.apache.commons.lang3.math.NumberUtils.max;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EphemeralOrchestrationGraph;
import io.harness.beans.FeatureName;
import io.harness.beans.GraphVertex;
import io.harness.beans.OrchestrationAdjacencyListInternal;
import io.harness.beans.OrchestrationGraph;
import io.harness.beans.WorkflowGraph;
import io.harness.beans.WorkflowGraphNode;
import io.harness.beans.WorkflowGraphRelation;
import io.harness.beans.converter.EphemeralOrchestrationGraphConverter;
import io.harness.beans.internal.EdgeListInternal;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.cache.EntityWithAccountId;
import io.harness.cache.SpringCacheEntity;
import io.harness.cache.SpringMongoStore;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.PipelineRetentionHelper;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.jobs.service.ExecutionRetentionIteratorEntityService;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.dto.SimplifiedOrchestrationGraphDTO;
import io.harness.dto.converter.OrchestrationGraphDTOConverter;
import io.harness.dto.converter.SimplifiedOrchestrationGraphDTOConverter;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.GraphUpdateEventInfo;
import io.harness.engine.observers.GraphUpdateEventObserver;
import io.harness.engine.observers.GraphUpdatesInfo;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.entity.eventlog.OrchestrationEventLog;
import io.harness.event.GraphStatusUpdateHelper;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.event.PlanExecutionModuleInfoUpdateEventHandler;
import io.harness.event.PlanExecutionStatusUpdateEventHandler;
import io.harness.event.StepDetailsUpdateEventHandler;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.generator.OrchestrationAdjacencyListGenerator;
import io.harness.graph.service.GraphCDCService;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.observer.Subject;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.data.NGWorkflowType;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo.GraphUpdateInfoKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.repositories.executions.GraphUpdateInfoRepositoryCustomImpl;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;
import io.harness.service.GraphGenerationService;
import io.harness.service.LogServiceUrlProvider;
import io.harness.service.PostgreSQLGraphStoreService;
import io.harness.skip.service.VertexSkipperService;
import io.harness.steps.StepUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class GraphGenerationServiceImpl implements GraphGenerationService {
  public static final int THRESHOLD_LOG = 1000;

  private static final String GRAPH_LOCK = "GRAPH_LOCK_";
  private static final int MAX_EXPECTED_GRAPH_UPDATE_TIME = 1000;
  private static final String GRAPH_CACHE_KEY_FORMAT = "%s/%s";
  private static final String GRAPH_CACHE_UUID_FORMAT = "%s_%s";

  @Inject private PlanExecutionService planExecutionService;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private NodeExecutionInfoService nodeExecutionInfoService;
  @Inject private GraphUpdateInfoRepositoryCustomImpl graphUpdateInfoRepositoryCustom;
  @Inject private SpringMongoStore mongoStore;
  @Inject private PostgreSQLGraphStoreService postgreSQLGraphStoreService;
  @Inject private GraphCDCService graphCDCService;
  @Inject private OrchestrationAdjacencyListGenerator orchestrationAdjacencyListGenerator;
  @Inject private VertexSkipperService vertexSkipperService;
  @Inject private OrchestrationEventLogRepository orchestrationEventLogRepository;
  @Inject private GraphStatusUpdateHelper graphStatusUpdateHelper;
  @Inject private PlanExecutionStatusUpdateEventHandler planExecutionStatusUpdateEventHandler;
  @Inject private StepDetailsUpdateEventHandler stepDetailsUpdateEventHandler;
  @Inject private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private OrchestrationLogPublisher orchestrationLogPublisher;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PlanExecutionModuleInfoUpdateEventHandler planExecutionModuleInfoUpdateEventHandler;
  @Inject PipelineRetentionService pipelineRetentionService;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Getter private final Subject<GraphUpdateEventObserver> graphUpdateObserverSubject = new Subject<>();
  @Inject ExecutionRetentionService executionRetentionService;
  @Inject private ExecutionRetentionIteratorEntityService retentionIteratorEntityService;
  @Inject(optional = true) private LogServiceUrlProvider logServiceUrlProvider;

  @Override
  public boolean updateGraph(String planExecutionId) {
    String lockName = GRAPH_LOCK + planExecutionId;
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(lockName, Duration.ofSeconds(10))) {
      if (lock == null) {
        log.debug(String.format(
            "[PMS_GRAPH_LOCK_TEST] Not able to take lock on graph generation for lockName - %s, returning early.",
            lockName));
        return false;
      }

      return updateGraphUnderLock(planExecutionId);
    } catch (Exception exception) {
      log.error(String.format(
                    "[GRAPH_ERROR] Exception Occurred while updating graph for planExecutionId: %s", planExecutionId),
          exception);
      return false;
    }
  }

  @Override
  public boolean updateGraphWithWaitLock(String planExecutionId) {
    String lockName = GRAPH_LOCK + planExecutionId;
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLockOptional(lockName, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
      if (lock == null) {
        log.debug(String.format(
            "[PMS_GRAPH_LOCK_TEST] Not able to take lock on graph generation for lockName - %s, returning early.",
            lockName));
        return false;
      }

      return updateGraphUnderLock(planExecutionId);
    } catch (Exception exception) {
      log.error(String.format(
                    "[GRAPH_ERROR] Exception Occurred while updating graph for planExecutionId: %s", planExecutionId),
          exception);
      return false;
    }
  }

  @Override
  public void validateAndUpdateFromNodeExecution(String planExecutionId, OrchestrationGraph orchestrationGraph) {
    Map<String, GraphVertex> graphVertexMap = orchestrationGraph.getAdjacencyList().getGraphVertexMap();

    List<String> nodeExecutionIdsNotInTerminalState =
        graphVertexMap.entrySet()
            .stream()
            .filter(entry -> entry.getValue().getStatus() != null)
            .filter(entry -> !StatusUtils.isFinalStatus(entry.getValue().getStatus()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

    // if list non-empty, then make a db call and update graph
    if (!nodeExecutionIdsNotInTerminalState.isEmpty()) {
      try (Stream<NodeExecution> nodeExecutionStream = nodeExecutionService.get(nodeExecutionIdsNotInTerminalState)) {
        Iterator<NodeExecution> nodeExecutions = nodeExecutionStream.iterator();
        while (nodeExecutions.hasNext()) {
          graphStatusUpdateHelper.handleEventV2(planExecutionId, nodeExecutions.next(), orchestrationGraph);
        }
      }
    }
  }

  // This must always be called after acquiring the lock
  @VisibleForTesting
  boolean updateGraphUnderLock(String planExecutionId) {
    // For CDC-started executions there is no OrchestrationGraph blob — skip the blob fetch entirely
    // and go straight to the lean summary-only update path.
    String accountIdForCdc = getAccountId(planExecutionId);
    PipelineExecutionSummaryEntity cdcSummaryEntity = getCdcStartedSummaryEntity(accountIdForCdc, planExecutionId);
    if (cdcSummaryEntity != null) {
      updateExecutionSummaryForCdcExecution(planExecutionId, accountIdForCdc, cdcSummaryEntity.getLastUpdatedAt());
      return true;
    }

    EntityWithAccountId orchestrationGraphWithAccountId =
        getCachedOrchestrationGraphWithAccountIdFromDB(planExecutionId);
    OrchestrationGraph orchestrationGraph = (OrchestrationGraph) orchestrationGraphWithAccountId.getEntity();
    if (orchestrationGraph == null) {
      log.warn("[PMS_GRAPH] Graph not yet generated. Passing on to next iteration");
      return true;
    }
    String accountId = orchestrationGraphWithAccountId.getAccountId();
    if (accountId == null) {
      log.warn("[PMS_GRAPH] AccountId should not be null");
      return updateGraphUnderLock(orchestrationGraph, null);
    }
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_REMOVE_ORCHESTRATION_LOG_EVENTS)) {
      return updateGraphUnderLockV2(orchestrationGraph, accountId);
    } else {
      return updateGraphUnderLock(orchestrationGraph, accountId);
    }
  }

  // This must always be called after acquiring the lock
  @VisibleForTesting
  boolean updateGraphUnderLock(OrchestrationGraph orchestrationGraph, String accountId) {
    if (orchestrationGraph == null) {
      return false;
    }
    boolean shouldAck = true;
    String planExecutionId = orchestrationGraph.getPlanExecutionId();
    long startTs = System.currentTimeMillis();
    long lastUpdatedAt = orchestrationGraph.getLastUpdatedAt();

    List<OrchestrationEventLog> unprocessedEventLogs =
        orchestrationEventLogRepository.findUnprocessedEvents(planExecutionId, lastUpdatedAt, THRESHOLD_LOG);
    if (unprocessedEventLogs.isEmpty()) {
      return true;
    }

    if (unprocessedEventLogs.size() == THRESHOLD_LOG) {
      log.warn("[PMS_GRAPH] Found [{}] unprocessed event logs", unprocessedEventLogs.size());
      // Re-emit if there are too many logs
      shouldAck = false;
    }
    boolean updateRequired = false;
    Update executionSummaryUpdate = new Update();
    Set<String> nodeExecutionIds = new HashSet<>();
    PlanExecution planExecution = null;
    LinkedList<GraphUpdateEventInfo> graphUpdateEventInfoList = new LinkedList<>();
    boolean isPipelineWorkflow =
        orchestrationGraph.getWorkflowType() == NGWorkflowType.PIPELINE; // By default, it is a pipeline workflow
    for (OrchestrationEventLog orchestrationEventLog : unprocessedEventLogs) {
      String nodeExecutionId = orchestrationEventLog.getNodeExecutionId();
      OrchestrationEventType orchestrationEventType = orchestrationEventLog.getOrchestrationEventType();
      // lastUpdatedAt is updated initially as this was not getting updating in case we have multiple event log of same
      // nodeExecution Id. If you check the default case in the below switch case, we continue if a particular node
      // execution is already processed without updating the lastUpdatedAt. For more details, you can refer CDS-75792
      lastUpdatedAt = orchestrationEventLog.getCreatedAt();
      switch (orchestrationEventType) {
        case PLAN_EXECUTION_STATUS_UPDATE:
          if (planExecution != null) {
            // If planExecution is notNull then the PLAN_EXECUTION_STATUS_UPDATE has already been applied.
            break;
          }
          planExecution = planExecutionService.get(planExecutionId);
          Status previousPlanStatus = orchestrationGraph.getStatus();
          executionSummaryUpdate = pmsExecutionSummaryService.updateStatusOps(planExecution, executionSummaryUpdate);
          orchestrationGraph = planExecutionStatusUpdateEventHandler.handleEvent(planExecution, orchestrationGraph);
          graphUpdateEventInfoList.add(GraphUpdateEventInfo.builder()
                                           .nodeType(NodeType.PLAN)
                                           .lastUpdatedAt(planExecution.getLastUpdatedAt())
                                           .status(planExecution.getStatus())
                                           .previousStatus(previousPlanStatus)
                                           .build());
          updateRequired = true;
          break;
        case STEP_DETAILS_UPDATE:
          orchestrationGraph = stepDetailsUpdateEventHandler.handleEvent(
              planExecutionId, nodeExecutionId, orchestrationGraph, executionSummaryUpdate, accountId);
          updateRequired = true;
          break;
        case STEP_INPUTS_UPDATE:
          orchestrationGraph =
              stepDetailsUpdateEventHandler.handleStepInputEvent(planExecutionId, nodeExecutionId, orchestrationGraph);
          updateRequired = true;
          break;
        case PIPELINE_INFO_UPDATE:
          planExecutionModuleInfoUpdateEventHandler.handlePipelineInfoUpdate(planExecutionId, executionSummaryUpdate);
          updateRequired = true;
          break;
        case STAGE_INFO_UPDATE:
          planExecutionModuleInfoUpdateEventHandler.handleStageInfoUpdate(
              planExecutionId, nodeExecutionId, executionSummaryUpdate);
          updateRequired = true;
          break;
        default:
          if (nodeExecutionIds.contains(nodeExecutionId)) {
            continue;
          }
          nodeExecutionIds.add(nodeExecutionId);
          NodeExecution nodeExecution = nodeExecutionService.get(nodeExecutionId);

          if (accountId != null
              && graphStatusUpdateHelper.isOldRetriedStepGroupNode(nodeExecution, orchestrationGraph)) {
            constructAndCacheOldRetryGraph(planExecutionId, nodeExecution, lastUpdatedAt);
          }

          updateRequired = pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(
                               planExecutionId, nodeExecution, executionSummaryUpdate)
              || updateRequired;
          orchestrationGraph =
              graphStatusUpdateHelper.handleEventV2(planExecutionId, nodeExecution, orchestrationGraph);
          if (!Objects.equals(nodeExecution.getSkipGraphType(), SkipType.SKIP_NODE)) {
            graphUpdateEventInfoList.add(GraphUpdateEventInfo.builder()
                                             .nodeExecutionId(nodeExecution.getUuid())
                                             .nodeType(NodeType.PLAN_NODE)
                                             .status(nodeExecution.getStatus())
                                             .lastUpdatedAt(nodeExecution.getLastUpdatedAt())
                                             .build());
          }
      }
    }

    if (updateRequired && isPipelineWorkflow) {
      executionSummaryUpdate.set(PlanExecutionSummaryKeys.lastUpdatedAt, lastUpdatedAt);
      pmsExecutionSummaryService.update(planExecutionId, executionSummaryUpdate);
    }
    // Updating the OrchestrationGraph after the PlanExecutionSummary. Because if there are any exceptions in updating
    // the PlanExecutionSummary, then Do not update the orchestrationGraph as well. And these orchestrationEventLogs
    // would be retries again.
    cachePartialOrchestrationGraph(orchestrationGraph.withLastUpdatedAt(lastUpdatedAt), lastUpdatedAt, accountId);
    long diff = System.currentTimeMillis() - startTs;
    if (diff > MAX_EXPECTED_GRAPH_UPDATE_TIME) {
      log.warn("[PMS_GRAPH] Processing of [{}] orchestration event logs completed in [{}ms]",
          unprocessedEventLogs.size(), diff);
    }

    notifyGraphObserver(accountId, planExecutionId, graphUpdateEventInfoList);
    return shouldAck;
  }

  private void notifyGraphObserver(
      String accountId, String planExecutionId, LinkedList<GraphUpdateEventInfo> graphUpdateEventInfoList) {
    if (shouldNotifyAfterGraphGen(accountId)) {
      GraphUpdatesInfo graphUpdatesInfo = GraphUpdatesInfo.builder()
                                              .planExecutionId(planExecutionId)
                                              .graphUpdateEventInfoList(graphUpdateEventInfoList)
                                              .build();
      graphUpdateObserverSubject.fireInform(GraphUpdateEventObserver::onGraphUpdate, graphUpdatesInfo);
    }
  }

  private void constructAndCacheOldRetryGraph(String planExecutionId, NodeExecution nodeExecution, long lastUpdatedAt) {
    OrchestrationGraph orchestrationGraph =
        constructOldRetryGraph(planExecutionId, nodeExecution, nodeExecution.getAccountId());
    if (orchestrationGraph == null) {
      log.warn("Graph not formed when constructing old retry graph for planExecutionId {} nodeExecutionId {}",
          planExecutionId, nodeExecution.getUuid());
      return;
    }
    cacheOrchestrationGraphInDB(orchestrationGraph.withLastUpdatedAt(lastUpdatedAt), nodeExecution.getAccountId());
  }

  @Override
  public OrchestrationGraph constructOldRetryGraph(
      String planExecutionId, NodeExecution nodeExecution, String accountId) {
    String nodeExecutionId = nodeExecution.getUuid();
    List<String> parentIds = new ArrayList<>();
    parentIds.add(nodeExecutionId);
    /*
      Extracting recursive child NodeExecutions for given nodeExecutionId
      And Incase of Multiple Retries for any child entity, we will only consider the lastRetried NodeExecution
      And will exclude the children of OldRetries NodeExecutions
    */
    List<NodeExecution> nodeExecutions =
        nodeExecutionService.fetchChildrenNodeExecutionsRecursivelyFromGivenParentIdWithoutOldRetries(
            planExecutionId, parentIds);
    nodeExecutions.add(nodeExecution);
    return buildOrchestrationGraphForNodeExecutionWithNodeExecutionId(planExecutionId, nodeExecutionId, nodeExecutions);
  }

  public OrchestrationGraph buildOrchestrationGraphForNodeExecutionWithNodeExecutionId(
      String planExecutionId, String nodeExecutionId, List<NodeExecution> nodeExecutions) {
    return OrchestrationGraph.builder()
        .cacheKey(String.format(GRAPH_CACHE_KEY_FORMAT, planExecutionId, nodeExecutionId))
        .cacheContextOrder(System.currentTimeMillis())
        .cacheParams(null)
        .planExecutionId(planExecutionId)
        .rootNodeIds(Lists.newArrayList(nodeExecutionId))
        .adjacencyList(orchestrationAdjacencyListGenerator.generateAdjacencyList(nodeExecutionId, nodeExecutions, true))
        .build();
  }

  @VisibleForTesting
  boolean updateGraphUnderLockV2(OrchestrationGraph orchestrationGraph, String accountId) {
    if (orchestrationGraph == null) {
      return false;
    }
    String planExecutionId = orchestrationGraph.getPlanExecutionId();

    boolean shouldAck = true;
    long lastUpdatedAt = orchestrationGraph.getLastUpdatedAt();

    boolean updateRequired;
    Long lastUpdatedAtResult;
    Update executionSummaryUpdate = new Update();
    boolean isPipelineWorkflow = orchestrationGraph.getWorkflowType() == NGWorkflowType.PIPELINE;

    LinkedList<GraphUpdateEventInfo> graphUpdateEventInfoList = new LinkedList<>();
    Pair<Long, Boolean> updatedResultNodeExecutions = updateGraphFromNodeExecutions(planExecutionId, lastUpdatedAt,
        executionSummaryUpdate, orchestrationGraph, graphUpdateEventInfoList, accountId);
    lastUpdatedAtResult = updatedResultNodeExecutions.getLeft();
    updateRequired = updatedResultNodeExecutions.getRight();

    Triple<Long, Boolean, OrchestrationGraph> updatedResultPlanExecution = updateGraphFromPlanExecution(
        planExecutionId, lastUpdatedAt, executionSummaryUpdate, orchestrationGraph, graphUpdateEventInfoList);
    orchestrationGraph = updatedResultPlanExecution.getRight();
    lastUpdatedAtResult = Math.min(updatedResultPlanExecution.getLeft(), lastUpdatedAtResult);
    updateRequired = updateRequired || updatedResultPlanExecution.getMiddle();

    Pair<Long, Boolean> updatedResultNodeExecutionInfo = updateGraphFromNodeExecutionInfo(
        planExecutionId, lastUpdatedAt, executionSummaryUpdate, orchestrationGraph, accountId);
    lastUpdatedAtResult = Math.min(updatedResultNodeExecutionInfo.getLeft(), lastUpdatedAtResult);
    updateRequired = updateRequired || updatedResultNodeExecutionInfo.getRight();

    Pair<Long, Boolean> updatedResultGraphInfo =
        updateGraphFromGraphUpdateInfo(planExecutionId, lastUpdatedAt, executionSummaryUpdate);
    lastUpdatedAtResult = Math.min(updatedResultGraphInfo.getLeft(), lastUpdatedAtResult);
    updateRequired = updateRequired || updatedResultGraphInfo.getRight();

    if (lastUpdatedAtResult != Long.MAX_VALUE) {
      lastUpdatedAt = lastUpdatedAtResult;
    }

    if (isPipelineWorkflow && updateRequired) {
      executionSummaryUpdate.set(PlanExecutionSummaryKeys.lastUpdatedAt, lastUpdatedAt);
      pmsExecutionSummaryService.update(planExecutionId, executionSummaryUpdate);
    }
    // Updating the OrchestrationGraph after the PlanExecutionSummary. Because if there are any exceptions in updating
    // the PlanExecutionSummary, then Do not update the orchestrationGraph as well. And these orchestrationEventLogs
    // would be retries again.
    cachePartialOrchestrationGraph(orchestrationGraph.withLastUpdatedAt(lastUpdatedAt), lastUpdatedAt, accountId);

    notifyGraphObserver(accountId, planExecutionId, graphUpdateEventInfoList);
    return shouldAck;
  }

  private void updateExecutionSummaryForCdcExecution(
      String planExecutionId, String accountId, Long summaryLastUpdatedAt) {
    try {
      long lastUpdatedAt = summaryLastUpdatedAt != null ? summaryLastUpdatedAt : 0L;
      Update executionSummaryUpdate = new Update();
      boolean updateRequired = false;
      Long lastUpdatedAtResult = Long.MAX_VALUE;

      // NodeExecutions: layoutNodeMap, moduleInfo, stepDetails
      Long lastUpdatedAtNodeExecutions = null;
      try (Stream<NodeExecution> nodeStream =
               nodeExecutionService.fetchAllNodeExecutionsByPlanExecutionIdLastUpdatedAtGTFromSecondary(
                   planExecutionId, lastUpdatedAt)) {
        Iterator<NodeExecution> nodeExecutions = nodeStream.iterator();
        while (nodeExecutions.hasNext()) {
          NodeExecution nodeExecution = nodeExecutions.next();
          updateRequired = pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(
                               planExecutionId, nodeExecution, executionSummaryUpdate)
              || updateRequired;
          if (lastUpdatedAtNodeExecutions != null) {
            lastUpdatedAtNodeExecutions = max(lastUpdatedAtNodeExecutions, nodeExecution.getLastUpdatedAt());
          } else {
            lastUpdatedAtNodeExecutions = nodeExecution.getLastUpdatedAt();
          }
        }
      }
      if (lastUpdatedAtNodeExecutions != null) {
        lastUpdatedAtResult = Math.min(lastUpdatedAtNodeExecutions, lastUpdatedAtResult);
      }

      // PlanExecution: status
      PlanExecution planExecution =
          planExecutionService.getByIdAndLastUpdatedAtGTFromSecondary(planExecutionId, lastUpdatedAt);
      if (planExecution != null) {
        pmsExecutionSummaryService.updateStatusOps(planExecution, executionSummaryUpdate);
        lastUpdatedAtResult = Math.min(planExecution.getLastUpdatedAt(), lastUpdatedAtResult);
        updateRequired = true;
      }

      // NodeExecutionInfo: stepDetails written directly to summaryEntityUpdate
      Long lastUpdatedAtInfo = null;
      try (Stream<NodeExecutionsInfo> infoStream =
               nodeExecutionInfoService.getStepDetailsNotUpdatedInGraphFromSecondary(planExecutionId, lastUpdatedAt)) {
        Iterator<NodeExecutionsInfo> infoList = infoStream.iterator();
        while (infoList.hasNext()) {
          NodeExecutionsInfo nodeExecutionsInfo = infoList.next();
          Map<String, PmsStepDetails> stepDetails =
              nodeExecutionInfoService.getStepDetailsFormNodeExecutionInfo(nodeExecutionsInfo);
          if (!stepDetails.isEmpty()) {
            Level currentLevel = null;
            try {
              // See if we can optimise this later
              NodeExecution ne =
                  nodeExecutionService.getWithFieldsIncludedFromSecondary(nodeExecutionsInfo.getNodeExecutionId(),
                      Set.of(NodeExecutionKeys.ambiance, NodeExecutionKeys.executionContext));
              if (ne.getAmbiance() != null) {
                currentLevel = NodeExecutionContextUtils.obtainCurrentLevel(ne);
              }
            } catch (Exception e) {
              log.warn("[CDC-SUMMARY] Could not fetch NodeExecution for step details update: {}",
                  nodeExecutionsInfo.getNodeExecutionId(), e);
            }
            if (currentLevel != null
                && (Objects.equals(currentLevel.getStepType().getStepCategory(), StepCategory.STAGE)
                    || Objects.equals(currentLevel.getStepType().getStepCategory(), StepCategory.STRATEGY))) {
              String stageUuid = currentLevel.getSetupId();
              if (accountId != null
                  && pmsFeatureFlagService.isEnabled(
                      accountId, FeatureName.PIPE_POPULATE_STEP_DETAILS_IN_RUNTIME_ID_FOR_STRATEGY_CHILD_NODES)) {
                if ((currentLevel.hasStrategyMetadata() || currentLevel.hasStrategyInfo())
                    && isNotEmpty(currentLevel.getRuntimeId())) {
                  stageUuid = currentLevel.getRuntimeId();
                }
              }
              executionSummaryUpdate.set(
                  PlanExecutionSummaryKeys.layoutNodeMap + "." + stageUuid + ".stepDetails", stepDetails);
              updateRequired = true;
            }
          }
          if (lastUpdatedAtInfo != null) {
            lastUpdatedAtInfo = max(lastUpdatedAtInfo, nodeExecutionsInfo.getLastUpdatedAt());
          } else {
            lastUpdatedAtInfo = nodeExecutionsInfo.getLastUpdatedAt();
          }
        }
      }
      if (lastUpdatedAtInfo != null) {
        lastUpdatedAtResult = Math.min(lastUpdatedAtInfo, lastUpdatedAtResult);
      }

      // GraphUpdateInfo: module info
      Pair<Long, Boolean> graphInfoResult =
          updateGraphFromGraphUpdateInfo(planExecutionId, lastUpdatedAt, executionSummaryUpdate, true);
      if (graphInfoResult.getLeft() != Long.MAX_VALUE) {
        lastUpdatedAtResult = Math.min(graphInfoResult.getLeft(), lastUpdatedAtResult);
      }
      updateRequired = updateRequired || graphInfoResult.getRight();

      if (updateRequired) {
        long newLastUpdatedAt = lastUpdatedAtResult != Long.MAX_VALUE ? lastUpdatedAtResult : lastUpdatedAt;
        executionSummaryUpdate.set(PlanExecutionSummaryKeys.lastUpdatedAt, newLastUpdatedAt);
        pmsExecutionSummaryService.update(planExecutionId, executionSummaryUpdate);
        log.debug("[CDC-SUMMARY] Updated execution summary for planExecutionId: {}", planExecutionId);
      }
    } catch (Exception e) {
      log.error("[CDC-SUMMARY] Failed to update execution summary for planExecutionId: {}", planExecutionId, e);
    }
  }

  private Pair<Long, Boolean> updateGraphFromNodeExecutions(String planExecutionId, Long lastUpdatedAt,
      Update executionSummaryUpdate, OrchestrationGraph orchestrationGraph,
      List<GraphUpdateEventInfo> graphUpdateEventInfoList, String accountId) {
    boolean updateRequired = false;
    Long lastUpdatedAtNodeExecutions = null;
    boolean shouldNotifyAfterGraphGen = shouldNotifyAfterGraphGen(accountId);
    try (Stream<NodeExecution> stream = nodeExecutionService.fetchAllNodeExecutionsByPlanExecutionIdLastUpdatedAtGT(
             planExecutionId, lastUpdatedAt)) {
      Iterator<NodeExecution> nodeExecutions = stream.iterator();
      while (nodeExecutions.hasNext()) {
        NodeExecution nodeExecution = nodeExecutions.next();

        if (graphStatusUpdateHelper.isOldRetriedStepGroupNode(nodeExecution, orchestrationGraph)) {
          constructAndCacheOldRetryGraph(planExecutionId, nodeExecution, lastUpdatedAt);
        }

        updateRequired = pmsExecutionSummaryService.handleNodeExecutionUpdateFromGraphUpdate(
                             planExecutionId, nodeExecution, executionSummaryUpdate)
            || updateRequired;
        orchestrationGraph = graphStatusUpdateHelper.handleEventV2(planExecutionId, nodeExecution, orchestrationGraph);
        if (lastUpdatedAtNodeExecutions != null) {
          lastUpdatedAtNodeExecutions = max(lastUpdatedAtNodeExecutions, nodeExecution.getLastUpdatedAt());
        } else {
          lastUpdatedAtNodeExecutions = nodeExecution.getLastUpdatedAt();
        }
        // Note: remove this FF check and the below status check for step nodes if more observers are added for graph
        // update events
        if (shouldNotifyAfterGraphGen) {
          // only send notifications for failed step or steps requiring a user action
          if (OrchestrationUtils.isStageNode(nodeExecution) || isBrokenStepNode(nodeExecution)
              || StatusUtils.userActionWaitingStatuses().contains(nodeExecution.getStatus())) {
            graphUpdateEventInfoList.add(GraphUpdateEventInfo.builder()
                                             .nodeExecutionId(nodeExecution.getUuid())
                                             .nodeType(NodeType.PLAN_NODE)
                                             .status(nodeExecution.getStatus())
                                             .lastUpdatedAt(nodeExecution.getLastUpdatedAt())
                                             .build());
          }
        }
      }
    }
    if (lastUpdatedAtNodeExecutions == null) {
      lastUpdatedAtNodeExecutions = Long.MAX_VALUE;
    }
    return Pair.of(lastUpdatedAtNodeExecutions, updateRequired);
  }

  private boolean isBrokenStepNode(NodeExecution nodeExecution) {
    return OrchestrationUtils.isStepNode(nodeExecution)
        && StatusUtils.brokeStatuses().contains(nodeExecution.getStatus());
  }

  private Triple<Long, Boolean, OrchestrationGraph> updateGraphFromPlanExecution(String planExecutionId,
      Long lastUpdatedAt, Update executionSummaryUpdate, OrchestrationGraph orchestrationGraph,
      List<GraphUpdateEventInfo> graphUpdateEventInfoList) {
    boolean updateRequired = false;
    Long lastUpdatedAtExecutions = null;
    PlanExecution planExecution = planExecutionService.getByIdAndLastUpdatedAtGT(planExecutionId, lastUpdatedAt);
    if (planExecution != null) {
      Status previousPlanStatus = orchestrationGraph.getStatus();
      lastUpdatedAtExecutions = planExecution.getLastUpdatedAt();
      pmsExecutionSummaryService.updateStatusOps(planExecution, executionSummaryUpdate);
      orchestrationGraph = planExecutionStatusUpdateEventHandler.handleEvent(planExecution, orchestrationGraph);
      updateRequired = true;
      graphUpdateEventInfoList.add(GraphUpdateEventInfo.builder()
                                       .nodeType(NodeType.PLAN)
                                       .lastUpdatedAt(planExecution.getLastUpdatedAt())
                                       .status(planExecution.getStatus())
                                       .previousStatus(previousPlanStatus)
                                       .build());
    }
    if (lastUpdatedAtExecutions == null) {
      lastUpdatedAtExecutions = Long.MAX_VALUE;
    }
    return Triple.of(lastUpdatedAtExecutions, updateRequired, orchestrationGraph);
  }

  private Pair<Long, Boolean> updateGraphFromNodeExecutionInfo(String planExecutionId, Long lastUpdatedAt,
      Update executionSummaryUpdate, OrchestrationGraph orchestrationGraph, String accountIdentifier) {
    boolean updateRequired = false;
    Long lastUpdatedAtNodeExecutionInfo = null;
    try (Stream<NodeExecutionsInfo> stream =
             nodeExecutionInfoService.getStepDetailsNotUpdatedInGraph(planExecutionId, lastUpdatedAt)) {
      Iterator<NodeExecutionsInfo> nodeExecutionsInfoList = stream.iterator();
      while (nodeExecutionsInfoList.hasNext()) {
        NodeExecutionsInfo nodeExecutionsInfo = nodeExecutionsInfoList.next();
        Map<String, PmsStepDetails> stepDetails =
            nodeExecutionInfoService.getStepDetailsFormNodeExecutionInfo(nodeExecutionsInfo);
        orchestrationGraph = stepDetailsUpdateEventHandler.handleEventV2(nodeExecutionsInfo.getNodeExecutionId(),
            orchestrationGraph, executionSummaryUpdate, stepDetails, accountIdentifier);
        orchestrationGraph =
            stepDetailsUpdateEventHandler.handleStepInputEventV2(nodeExecutionsInfo, orchestrationGraph);
        updateRequired = true;
        if (lastUpdatedAtNodeExecutionInfo != null) {
          lastUpdatedAtNodeExecutionInfo = max(lastUpdatedAtNodeExecutionInfo, nodeExecutionsInfo.getLastUpdatedAt());
        } else {
          lastUpdatedAtNodeExecutionInfo = nodeExecutionsInfo.getLastUpdatedAt();
        }
      }
    }
    if (lastUpdatedAtNodeExecutionInfo == null) {
      lastUpdatedAtNodeExecutionInfo = Long.MAX_VALUE;
    }
    return Pair.of(lastUpdatedAtNodeExecutionInfo, updateRequired);
  }

  private Pair<Long, Boolean> updateGraphFromGraphUpdateInfo(
      String planExecutionId, Long lastUpdatedAt, Update executionSummaryUpdate) {
    return updateGraphFromGraphUpdateInfo(planExecutionId, lastUpdatedAt, executionSummaryUpdate, false);
  }

  private Pair<Long, Boolean> updateGraphFromGraphUpdateInfo(
      String planExecutionId, Long lastUpdatedAt, Update executionSummaryUpdate, boolean useSecondary) {
    boolean updateRequired = false;
    Long lastUpdatedAtGraphUpdateInfo = null;
    try (Stream<GraphUpdateInfo> stream = useSecondary
            ? graphUpdateInfoRepositoryCustom.findGraphUpdateInfoNotProcessedInGraphFromSecondary(
                  getGraphUpdateInfoQuery(planExecutionId, lastUpdatedAt))
            : graphUpdateInfoRepositoryCustom.findGraphUpdateInfoNotProcessedInGraph(
                  getGraphUpdateInfoQuery(planExecutionId, lastUpdatedAt))) {
      Iterator<GraphUpdateInfo> graphUpdateInfoList = stream.iterator();
      while (graphUpdateInfoList.hasNext()) {
        GraphUpdateInfo graphUpdateInfo = graphUpdateInfoList.next();
        if (graphUpdateInfo.getExecutionSummaryUpdateInfo().getStepCategory() == StepCategory.PIPELINE) {
          planExecutionModuleInfoUpdateEventHandler.handlePipelineInfoUpdate(
              graphUpdateInfo.getPlanExecutionId(), executionSummaryUpdate);
        } else if (graphUpdateInfo.getExecutionSummaryUpdateInfo().getStepCategory() == StepCategory.STAGE) {
          planExecutionModuleInfoUpdateEventHandler.handleStageInfoUpdate(
              graphUpdateInfo.getPlanExecutionId(), graphUpdateInfo.getNodeExecutionId(), executionSummaryUpdate);
        }
        updateRequired = true;
        if (lastUpdatedAtGraphUpdateInfo != null) {
          lastUpdatedAtGraphUpdateInfo = max(lastUpdatedAtGraphUpdateInfo, graphUpdateInfo.getLastUpdatedAt());
        } else {
          lastUpdatedAtGraphUpdateInfo = graphUpdateInfo.getLastUpdatedAt();
        }
      }
    }
    if (lastUpdatedAtGraphUpdateInfo == null) {
      lastUpdatedAtGraphUpdateInfo = Long.MAX_VALUE;
    }
    return Pair.of(lastUpdatedAtGraphUpdateInfo, updateRequired);
  }

  /**
   * Retrieves cached OrchestrationGraph from the most appropriate data source.
   *
   * <p>Data source priority (when feature flags are enabled):
   * <ol>
   *   <li>CDC-based graph projections (PMS_USE_CDC_GRAPH_PROJECTIONS) - Real-time event-sourced graphs
   *   <li>PostgreSQL store (PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH) - Query-based graph storage
   *   <li>MongoDB store - Default fallback storage
   * </ol>
   *
   * <p>CDC projection fallback behavior:
   * <ul>
   *   <li>If projection not found: Falls back to PostgreSQL/MongoDB (projection may not be built yet)
   *   <li>If error occurs: Falls back to PostgreSQL/MongoDB (ensures system resilience)
   *   <li>Logs warnings/errors for monitoring and debugging
   * </ul>
   *
   * <p>Edge cases handled:
   * <ul>
   *   <li>Null accountId: Skips CDC check, proceeds to PostgreSQL/MongoDB
   *   <li>GraphProjectionService not initialized: Exception caught, falls back gracefully
   *   <li>Stale projections: Current implementation uses whatever is available; consider adding
   *       watermark/lag checks in future iterations
   *   <li>New executions: CDC projection may not exist yet, fallback handles this transparently
   * </ul>
   *
   * @param planExecutionId the plan execution ID to retrieve graph for
   * @param accountId the account ID for feature flag evaluation (can be null)
   * @return the orchestration graph from the most appropriate source, never null
   */

  /**
   * Checks whether CDC graph was enabled when the given execution started.
   * Uses a lightweight projection query to fetch only the cdcGraphEnabled field.
   * Returns false if the entity is not found or the field is null/false.
   */
  private boolean isCdcGraphEnabledForExecution(String accountId, String planExecutionId) {
    if (accountId == null || planExecutionId == null) {
      return false;
    }
    // If force rebuild is enabled, skip the CDC path entirely — all callers will
    // fall through to old stores or legacy path where forceRebuildOrchestrationGraph handles it
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD)) {
      return false;
    }
    return wasCdcStartedExecution(accountId, planExecutionId);
  }

  /**
   * Checks if this execution was started with CDC graph enabled (cdcGraphEnabled=true on entity).
   * Unlike isCdcGraphEnabledForExecution, this does NOT check any feature flags —
   * it only answers: "was this execution created during the CDC era?"
   * Used by read methods to decide whether force rebuild is needed.
   */
  private boolean wasCdcStartedExecution(String accountId, String planExecutionId) {
    if (accountId == null || planExecutionId == null) {
      return false;
    }
    try {
      PipelineExecutionSummaryEntity entity = pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(
          accountId, planExecutionId, Set.of(PlanExecutionSummaryKeys.cdcGraphEnabled));
      return entity != null && Boolean.TRUE.equals(entity.getCdcGraphEnabled());
    } catch (Exception e) {
      log.warn("[CDC-GRAPH] Failed to check cdcGraphEnabled for planExecutionId: {}", planExecutionId, e);
      return false;
    }
  }

  /**
   * Like wasCdcStartedExecution, but also projects lastUpdatedAt in the same query so callers
   * that need both (e.g. updateExecutionSummaryForCdcExecution) don't fetch the entity twice.
   * Returns null if the execution was not CDC-started.
   */
  private PipelineExecutionSummaryEntity getCdcStartedSummaryEntity(String accountId, String planExecutionId) {
    if (accountId == null || planExecutionId == null) {
      return null;
    }
    try {
      PipelineExecutionSummaryEntity entity = pmsExecutionSummaryService.fetchFromSecondaryWithProjections(accountId,
          planExecutionId, Set.of(PlanExecutionSummaryKeys.cdcGraphEnabled, PlanExecutionSummaryKeys.lastUpdatedAt));
      return entity != null && Boolean.TRUE.equals(entity.getCdcGraphEnabled()) ? entity : null;
    } catch (Exception e) {
      log.warn("[CDC-GRAPH] Failed to check cdcGraphEnabled for planExecutionId: {}", planExecutionId, e);
      return null;
    }
  }

  /**
   * Force-rebuilds the orchestration graph from ALL MongoDB source collections:
   * nodeExecutions, planExecutions, nodeExecutionsInfo, and graphUpdateInfo.
   * Initializes an empty graph (like OrchestrationStartEventHandler) with lastUpdatedAt=0
   * so updateGraphUnderLockV2 processes ALL records from all 4 collections.
   */
  private OrchestrationGraph forceRebuildOrchestrationGraph(String planExecutionId, String accountId) {
    log.warn("[CDC-GRAPH-FALLBACK] Force rebuilding graph from all source collections for planExecutionId: {}",
        planExecutionId);
    try {
      PlanExecution planExecution = planExecutionService.getPlanExecutionMetadata(planExecutionId);
      if (planExecution == null) {
        log.error("[CDC-GRAPH-FALLBACK] PlanExecution not found for planExecutionId: {}", planExecutionId);
        return null;
      }

      // Initialize empty graph (same pattern as OrchestrationStartEventHandler)
      // lastUpdatedAt=0 ensures updateGraphUnderLockV2 processes ALL records
      OrchestrationGraph graph = OrchestrationGraph.builder()
                                     .cacheKey(planExecutionId)
                                     .cacheParams(null)
                                     .cacheContextOrder(System.currentTimeMillis())
                                     .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                                                        .graphVertexMap(new HashMap<>())
                                                        .adjacencyMap(new HashMap<>())
                                                        .build())
                                     .planExecutionId(planExecutionId)
                                     .rootNodeIds(new ArrayList<>())
                                     .startTs(planExecution.getStartTs())
                                     .endTs(planExecution.getEndTs())
                                     .status(planExecution.getStatus())
                                     .lastUpdatedAt(0L)
                                     .build();

      // Process ALL records from all 4 collections and cache the enriched graph
      updateGraphUnderLockV2(graph, accountId);

      return getCachedOrchestrationGraphFromOldStores(planExecutionId, accountId);
    } catch (Exception e) {
      log.error("[CDC-GRAPH-FALLBACK] Failed to rebuild graph for planExecutionId: {}", planExecutionId, e);
      return null;
    }
  }

  private OrchestrationGraph getCachedOrchestrationGraphFromOldStores(String planExecutionId, String accountId) {
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH)) {
      OrchestrationGraph pgGraph = postgreSQLGraphStoreService.get(planExecutionId);
      if (pgGraph != null) {
        return pgGraph;
      }
    }
    return mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId, null);
  }

  @Override
  public OrchestrationGraph getCachedOrchestrationGraphFromDB(String planExecutionId, String accountId) {
    boolean cdcStarted = wasCdcStartedExecution(accountId, planExecutionId);

    // Force rebuild path: execution was CDC-started but we want to serve from old stores / rebuild.
    // wasCdcStartedExecution is independent of force rebuild FF — it only checks cdcGraphEnabled on entity.
    if (cdcStarted && pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD)) {
      OrchestrationGraph cachedGraph = getCachedOrchestrationGraphFromOldStores(planExecutionId, accountId);
      if (cachedGraph != null) {
        return cachedGraph;
      }
      return forceRebuildOrchestrationGraph(planExecutionId, accountId);
    }

    // Normal CDC path: serve from graph_vertex table (force rebuild FF check already handled above,
    // so cdcStarted && !FORCE_REBUILD is equivalent to isCdcGraphEnabledForExecution here)
    if (cdcStarted && !pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD)) {
      Optional<OrchestrationGraph> normalizedGraph = graphCDCService.getOrchestrationGraph(planExecutionId);
      if (normalizedGraph.isPresent()) {
        return normalizedGraph.get();
      }
      log.debug("[NORMALIZED-PG] Graph not found in normalized storage, falling back for planExecutionId: {}",
          planExecutionId);
    }

    // Try existing PostgreSQL (blob-based storage)
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH)) {
      OrchestrationGraph pgGraph = postgreSQLGraphStoreService.get(planExecutionId);
      if (pgGraph != null) {
        return pgGraph;
      } else {
        log.error("Failed to retrieve graph from PostgreSQL for planExecutionId: {}", planExecutionId);
      }
    }

    OrchestrationGraph mongoGraph =
        mongoStore.get(OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId, null);
    if (mongoGraph != null) {
      return mongoGraph;
    }

    // Last resort: CDC-started execution with nothing anywhere — force rebuild
    if (cdcStarted) {
      return forceRebuildOrchestrationGraph(planExecutionId, accountId);
    }
    return null;
  }

  @Override
  public OrchestrationGraph getCachedOrchestrationGraphFromSecondary(String accountIdentifier, String planExecutionId) {
    OrchestrationGraph orchestrationGraph =
        (OrchestrationGraph) executionRetentionService.readExpiredRecordFromObjectStore(accountIdentifier,
            planExecutionId, ExecutionRetentionObjectStoreCollection.EXECUTION_GRAPH, OrchestrationGraph.class);
    if (orchestrationGraph == null) {
      return getCachedGraphFromSecondary(planExecutionId, accountIdentifier);
    }
    return orchestrationGraph;
  }

  @Override
  public OrchestrationGraph getCachedOrchestrationGraphFromSecondary(
      String accountIdentifier, String planExecutionId, String nodeExecutionId) {
    OrchestrationGraph orchestrationGraph =
        (OrchestrationGraph) executionRetentionService.readExpiredRecordFromObjectStore(accountIdentifier,
            String.format(GRAPH_CACHE_UUID_FORMAT, planExecutionId, nodeExecutionId),
            ExecutionRetentionObjectStoreCollection.EXECUTION_SUB_GRAPH, OrchestrationGraph.class);
    if (orchestrationGraph == null) {
      return getCachedGraphFromSecondary(
          String.format(GRAPH_CACHE_KEY_FORMAT, planExecutionId, nodeExecutionId), accountIdentifier);
    }
    return orchestrationGraph;
  }

  private OrchestrationGraph getCachedGraphFromSecondary(String key, String accountIdentifier) {
    EntityWithAccountId entity = getCachedOrchestrationGraphFromSecondaryWithAccountId(key, accountIdentifier);
    return entity == null ? null : (OrchestrationGraph) entity.getEntity();
  }

  @Override
  public OrchestrationGraph getCdcSubGraph(String accountId, String planExecutionId, String nodeExecutionId) {
    if (!isCdcGraphEnabledForExecution(accountId, planExecutionId)) {
      return null;
    }
    try {
      // Use getOldRetrySubGraph because the retry step group subgraph API targets old_retry=true nodes.
      // getPartialOrchestrationGraph filters out old_retry and would return 0 records.
      Optional<OrchestrationGraph> partialGraphOpt =
          graphCDCService.getOldRetrySubGraph(planExecutionId, nodeExecutionId);
      if (partialGraphOpt.isPresent()) {
        return partialGraphOpt.get();
      }
    } catch (Exception e) {
      log.warn("[CDC-GRAPH] Failed to get CDC sub graph for planExecutionId: {}, nodeExecutionId: {}", planExecutionId,
          nodeExecutionId, e);
    }
    return null;
  }

  @Override
  public EntityWithAccountId getCachedOrchestrationGraphWithAccountIdFromDB(String planExecutionId) {
    String accountIdentifier = getAccountId(planExecutionId);
    boolean cdcStarted = wasCdcStartedExecution(accountIdentifier, planExecutionId);

    // Force rebuild path: execution was CDC-started but we want to serve from old stores / rebuild
    if (cdcStarted && pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD)) {
      OrchestrationGraph cachedGraph = getCachedOrchestrationGraphFromOldStores(planExecutionId, accountIdentifier);
      if (cachedGraph != null) {
        return EntityWithAccountId.builder().entity(cachedGraph).accountId(accountIdentifier).build();
      }
      OrchestrationGraph rebuilt = forceRebuildOrchestrationGraph(planExecutionId, accountIdentifier);
      if (rebuilt != null) {
        return EntityWithAccountId.builder().entity(rebuilt).accountId(accountIdentifier).build();
      }
      return null;
    }

    // Try existing PostgreSQL (blob-based storage)
    if (pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH)) {
      EntityWithAccountId pgResult = postgreSQLGraphStoreService.getWithAccountId(planExecutionId);
      if (pgResult != null) {
        return pgResult;
      } else {
        log.error("Failed to retrieve graph from PostgreSQL for planExecutionId: {}", planExecutionId);
      }
    }

    // Fallback to MongoDB
    EntityWithAccountId mongoResult = mongoStore.getWithAccountId(
        OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId, null);
    if (mongoResult != null) {
      return mongoResult;
    }

    // Last resort: CDC-started execution with nothing anywhere — force rebuild
    if (cdcStarted) {
      OrchestrationGraph rebuilt = forceRebuildOrchestrationGraph(planExecutionId, accountIdentifier);
      if (rebuilt != null) {
        return EntityWithAccountId.builder().entity(rebuilt).accountId(accountIdentifier).build();
      }
    }
    return null;
  }

  private String getAccountId(String planExecutionId) {
    PlanExecution planExecutionOnlyWithAccountId = planExecutionService.getWithFieldsIncludedFromSecondary(
        planExecutionId, Sets.newHashSet(PlanExecutionKeys.setupAbstractions));
    String accountId = null;
    if (planExecutionOnlyWithAccountId != null && planExecutionOnlyWithAccountId.getSetupAbstractions() != null) {
      accountId = planExecutionOnlyWithAccountId.getSetupAbstractions().get(SetupAbstractionKeys.accountId);
    }
    return accountId;
  }

  @Override
  public EntityWithAccountId getCachedOrchestrationGraphFromSecondaryWithAccountId(
      String planExecutionId, String accountIdentifier) {
    boolean cdcStarted = wasCdcStartedExecution(accountIdentifier, planExecutionId);

    // Force rebuild path: execution was CDC-started but we want to serve from old stores / rebuild
    if (cdcStarted && pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD)) {
      OrchestrationGraph cachedGraph = getCachedOrchestrationGraphFromOldStores(planExecutionId, accountIdentifier);
      if (cachedGraph != null) {
        return EntityWithAccountId.builder().entity(cachedGraph).accountId(accountIdentifier).build();
      }
      OrchestrationGraph rebuilt = forceRebuildOrchestrationGraph(planExecutionId, accountIdentifier);
      if (rebuilt != null) {
        return EntityWithAccountId.builder().entity(rebuilt).accountId(accountIdentifier).build();
      }
      return null;
    }

    // Try normalized PostgreSQL first (new implementation with per-vertex storage)
    // cdcStarted && !FORCE_REBUILD is equivalent to isCdcGraphEnabledForExecution, reusing the already-fetched result
    if (cdcStarted && !pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_CDC_GRAPH_FORCE_REBUILD)) {
      Optional<OrchestrationGraph> normalizedGraph = graphCDCService.getOrchestrationGraph(planExecutionId);
      if (normalizedGraph.isPresent()) {
        return EntityWithAccountId.builder().entity(normalizedGraph.get()).accountId(accountIdentifier).build();
      }
      log.debug("[NORMALIZED-PG] Graph not found in normalized storage, falling back for planExecutionId: {}",
          planExecutionId);
    }

    // Try existing PostgreSQL (blob-based storage)
    if (null != accountIdentifier
        && pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH)) {
      EntityWithAccountId pgResult = postgreSQLGraphStoreService.getWithAccountId(planExecutionId);
      if (pgResult != null) {
        return pgResult;
      } else {
        log.error("Failed to retrieve graph from PostgreSQL for planExecutionId: {}", planExecutionId);
      }
    }
    // Fallback to MongoDB
    EntityWithAccountId mongoResult = mongoStore.getFromSecondary(
        OrchestrationGraph.ALGORITHM_ID, OrchestrationGraph.STRUCTURE_HASH, planExecutionId, null);
    if (mongoResult != null) {
      return mongoResult;
    }

    // Last resort: CDC-started execution with nothing anywhere — force rebuild
    if (cdcStarted) {
      OrchestrationGraph rebuilt = forceRebuildOrchestrationGraph(planExecutionId, accountIdentifier);
      if (rebuilt != null) {
        return EntityWithAccountId.builder().entity(rebuilt).accountId(accountIdentifier).build();
      }
    }
    return null;
  }

  @Override
  public void cacheOrchestrationGraphInDB(OrchestrationGraph orchestrationGraph, String accountIdentifier) {
    Duration ttl = SpringCacheEntity.TTL;
    if (executionRetentionService.isEnabled()) {
      int retentionPeriodInDays =
          executionRetentionService.getMongoValidUntilTTL(ExecutionRetentionObjectStoreCollection.EXECUTION_GRAPH);
      ttl = ofDays(retentionPeriodInDays);
    } else if (pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.CDS_CUSTOMIZE_PIPELINE_TTL)) {
      ttl = PipelineRetentionHelper.getValidUntilAsDuration(
          pipelineRetentionService.getRetentionPeriodInMonths(accountIdentifier));
    }
    if (null == accountIdentifier) {
      log.warn("Skipping orchestration graph update: accountIdentifier is null. This is required for storing execution "
          + "graph data in both MongoDB and PostgresSQL.");
      return;
    }

    try {
      if (pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH)) {
        postgreSQLGraphStoreService.upsert(orchestrationGraph, ttl, accountIdentifier);
      }
    } catch (Exception exception) {
      log.error("Failed to update orchestration graph in postgres DB", exception);
    } finally {
      if (!pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_STOP_USING_MONGO_FOR_EXECUTION_GRAPH)) {
        mongoStore.upsert(orchestrationGraph, ttl, accountIdentifier);
      }
    }
  }

  private void cachePartialOrchestrationGraph(
      OrchestrationGraph orchestrationGraph, long entityUpdatedAt, String accountIdentifier) {
    Duration ttl = SpringCacheEntity.TTL;
    if (executionRetentionService.isEnabled()) {
      int retentionPeriodInDays =
          executionRetentionService.getMongoValidUntilTTL(ExecutionRetentionObjectStoreCollection.EXECUTION_GRAPH);
      ttl = ofDays(retentionPeriodInDays);
    } else if (pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.CDS_CUSTOMIZE_PIPELINE_TTL)) {
      ttl = PipelineRetentionHelper.getValidUntilAsDuration(
          pipelineRetentionService.getRetentionPeriodInMonths(accountIdentifier));
    }

    if (null == accountIdentifier) {
      log.warn("Skipping Partial orchestration graph update: accountIdentifier is null.");
      return;
    }

    try {
      if (pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH)) {
        postgreSQLGraphStoreService.upsert(orchestrationGraph, ttl, entityUpdatedAt, accountIdentifier);
      }
    } catch (Exception exception) {
      log.error("Failed to update partial orchestration graph in postgres DB", exception);
    } finally {
      if (!pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.PIPE_STOP_USING_MONGO_FOR_EXECUTION_GRAPH)) {
        mongoStore.upsert(orchestrationGraph, ttl, entityUpdatedAt);
      }
    }
  }

  @Override
  public OrchestrationGraphDTO generateOrchestrationGraphV2(String accountIdentifier, String planExecutionId) {
    EphemeralOrchestrationGraph ephemeralOrchestrationGraph =
        getEphemeralOrchestrationGraph(accountIdentifier, planExecutionId);
    return OrchestrationGraphDTOConverter.convertFrom(ephemeralOrchestrationGraph);
  }

  @Override
  public SimplifiedOrchestrationGraphDTO generateSimplifiedOrchestrationGraphV2(
      String accountIdentifier, String planExecutionId) {
    EphemeralOrchestrationGraph ephemeralOrchestrationGraph =
        getEphemeralOrchestrationGraph(accountIdentifier, planExecutionId);
    return SimplifiedOrchestrationGraphDTOConverter.convertFrom(ephemeralOrchestrationGraph);
  }

  @Override
  public OrchestrationGraphDTO generatePartialOrchestrationGraphFromSetupNodeIdAndExecutionId(
      String accountIdentifier, String startingSetupNodeId, String planExecutionId, String startingExecutionId) {
    // Use PostgreSQL path if CDC graph was enabled for this execution - fetches only subtree instead of entire graph
    if (isCdcGraphEnabledForExecution(accountIdentifier, planExecutionId)) {
      Optional<String> rootNodeIdOpt =
          graphCDCService.findNodeExecutionId(planExecutionId, startingSetupNodeId, startingExecutionId);

      if (rootNodeIdOpt.isPresent()) {
        Optional<OrchestrationGraph> partialGraphOpt =
            graphCDCService.getPartialOrchestrationGraph(planExecutionId, rootNodeIdOpt.get());

        if (partialGraphOpt.isPresent()) {
          EphemeralOrchestrationGraph ephemeralGraph =
              EphemeralOrchestrationGraphConverter.convertFrom(partialGraphOpt.get());
          vertexSkipperService.removeSkippedVertices(ephemeralGraph);
          return OrchestrationGraphDTOConverter.convertFrom(ephemeralGraph);
        }
      }
      // Fall through to legacy path if PostgreSQL lookup fails
      log.debug(
          "[NORMALIZED-PG] Falling back to legacy path for partial graph: planExecutionId={}, startingSetupNodeId={}",
          planExecutionId, startingSetupNodeId);
    }

    // Legacy path: fetch entire graph and filter in memory
    OrchestrationGraph orchestrationGraph =
        getCachedOrchestrationGraphFromSecondary(accountIdentifier, planExecutionId);
    if (orchestrationGraph == null) {
      orchestrationGraph = buildOrchestrationGraph(planExecutionId);
    } else {
      sendUpdateEventIfAny(orchestrationGraph, accountIdentifier);
    }

    String startingNodeId = obtainStartingIdFromSetupNodeIdAndExecutionId(
        orchestrationGraph.getAdjacencyList().getGraphVertexMap(), startingSetupNodeId, startingExecutionId);
    try {
      return generatePartialGraph(startingNodeId, orchestrationGraph);
    } catch (Exception ex) {
      log.error("Error while generating partial graph", ex);
      orchestrationGraph = buildOrchestrationGraph(planExecutionId);
      return generatePartialGraph(startingNodeId, orchestrationGraph);
    }
  }

  @Override
  public void sendUpdateEventIfAny(PipelineExecutionSummaryEntity executionSummaryEntity) {
    sendUpdateEventIfAny(executionSummaryEntity.getStatus().getEngineStatus(),
        executionSummaryEntity.getPlanExecutionId(), executionSummaryEntity.getLastUpdatedAt(),
        executionSummaryEntity.getAccountId());
  }

  @Override
  public void deleteAllGraphMetadataForGivenExecutionIds(
      Set<String> planExecutionIds, boolean retainPipelineExecutionDetailsAfterDelete, String accountId) {
    // Delete all related orchestration logs
    orchestrationEventLogRepository.deleteAllOrchestrationLogEvents(planExecutionIds);

    // Delete related cache entities
    if (!retainPipelineExecutionDetailsAfterDelete) {
      List<OrchestrationGraph> cacheEntities = new LinkedList<>();
      for (String planExecutionId : planExecutionIds) {
        OrchestrationGraph graph = OrchestrationGraph.builder().cacheKey(planExecutionId).cacheParams(null).build();
        cacheEntities.add(graph);
      }

      // Check if PostgresSQL is enabled for this account
      boolean postgresFFEnabled =
          pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_USE_POSTGRES_FOR_EXECUTION_GRAPH);
      if (postgresFFEnabled) {
        postgreSQLGraphStoreService.deleteUsingPattern(cacheEntities);
      }
      // Delete from MongoDB
      mongoStore.deleteUsingPattern(cacheEntities);
      mongoStore.delete(cacheEntities);
    }
  }

  private void sendUpdateEventIfAny(OrchestrationGraph orchestrationGraph, String accountId) {
    sendUpdateEventIfAny(orchestrationGraph.getStatus(), orchestrationGraph.getPlanExecutionId(),
        orchestrationGraph.getLastUpdatedAt(), accountId);
  }

  private void sendUpdateEventIfAny(
      Status planExecutionStatus, String planExecutionId, long lastUpdatedAt, String accountId) {
    if (accountId == null) {
      log.warn("[PMS_GRAPH] AccountId should not be null");
    }
    boolean checkIfUpdateNeeded;
    if (accountId != null
        && pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_REMOVE_ORCHESTRATION_LOG_EVENTS)) {
      checkIfUpdateNeeded = checkIfGraphUpdateNeeded(planExecutionId, lastUpdatedAt);
    } else {
      checkIfUpdateNeeded = orchestrationEventLogRepository.checkIfAnyUnprocessedEvents(planExecutionId, lastUpdatedAt);
    }
    if (!StatusUtils.isFinalStatus(planExecutionStatus) || checkIfUpdateNeeded) {
      orchestrationLogPublisher.sendLogEvent(planExecutionId, accountId);
    }
  }

  private boolean checkIfGraphUpdateNeeded(String planExecutionId, long lastUpdatedAt) {
    return nodeExecutionService.checkIfUnprocessedNodeExecutionsForPlanExecutionId(planExecutionId, lastUpdatedAt)
        || planExecutionService.checkIfPlanExecutionNotProcessedInGraph(planExecutionId, lastUpdatedAt)
        || nodeExecutionInfoService.checkIfUnprocessedNodeExecutionInfo(planExecutionId, lastUpdatedAt)
        || graphUpdateInfoRepositoryCustom.checkIfGraphUpdateInfoNotProcessedInGraph(
            getGraphUpdateInfoExistsQuery(planExecutionId, lastUpdatedAt));
  }

  private Query getGraphUpdateInfoQuery(String planExecutionId, long lastUpdatedAt) {
    Criteria criteria = Criteria.where(GraphUpdateInfoKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(GraphUpdateInfoKeys.lastUpdatedAt)
                            .gt(lastUpdatedAt);
    return new Query(criteria).with(Sort.by(Sort.Direction.ASC, GraphUpdateInfoKeys.createdAt));
  }

  private Query getGraphUpdateInfoExistsQuery(String planExecutionId, long lastUpdatedAt) {
    Criteria criteria = Criteria.where(GraphUpdateInfoKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(GraphUpdateInfoKeys.lastUpdatedAt)
                            .gt(lastUpdatedAt);
    return new Query(criteria);
  }

  public OrchestrationGraph buildOrchestrationGraph(String planExecutionId) {
    try {
      log.warn(String.format(
          "[GRAPH_ERROR]: Trying to build orchestration graph from scratch for planExecutionId [%s]", planExecutionId));
      PlanExecution planExecution = planExecutionService.getWithFieldsIncluded(planExecutionId,
          Set.of(PlanExecutionKeys.ambiance, PlanExecutionKeys.startTs, PlanExecutionKeys.endTs,
              PlanExecutionKeys.status, PlanExecutionKeys.postExecutionRollbackInfos));
      if (planExecution == null) {
        throw NestedExceptionUtils.hintWithExplanationException("Pipeline Execution with given plan execution id: ["
                + planExecutionId + "] not found or unable to generate a graph for it",
            "Try to open an execution which is not 6 months old. If issue persists, please contact harness support",
            new InvalidRequestException("Graph could not be generated for planExecutionId [" + planExecutionId + "]."));
      }
      List<NodeExecution> nodeExecutions = new LinkedList<>();

      try (Stream<NodeExecution> stream =
               nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(planExecutionId)) {
        Iterator<NodeExecution> iterator = stream.iterator();
        while (iterator.hasNext()) {
          nodeExecutions.add(iterator.next());
        }
      }
      log.warn(String.format("[GRAPH_ERROR]: Trying to build orchestration graph from scratch for planExecutionId [%s] "
              + "with nodeExecutionsCount [%d]",
          planExecutionId, nodeExecutions.size()));
      if (isEmpty(nodeExecutions)) {
        return OrchestrationGraph.builder()
            .adjacencyList(OrchestrationAdjacencyListInternal.builder()
                               .adjacencyMap(new HashMap<>())
                               .graphVertexMap(new HashMap<>())
                               .build())
            .rootNodeIds(new ArrayList<>())
            .build();
      }

      String rootNodeId = obtainStartingNodeExId(nodeExecutions);

      OrchestrationGraph graph = OrchestrationGraph.builder()
                                     .cacheKey(planExecutionId)
                                     .cacheContextOrder(System.currentTimeMillis())
                                     .cacheParams(null)
                                     .planExecutionId(planExecution.getUuid())
                                     .startTs(planExecution.getStartTs())
                                     .endTs(planExecution.getEndTs())
                                     .status(planExecution.getStatus())
                                     .rootNodeIds(Lists.newArrayList(rootNodeId))
                                     .adjacencyList(orchestrationAdjacencyListGenerator.generateAdjacencyList(
                                         rootNodeId, nodeExecutions, true))
                                     .build();

      List<NodeExecution> stageNodeExecutions =
          nodeExecutions.stream().filter(OrchestrationUtils::isStageOrParallelStageNode).collect(Collectors.toList());
      cacheOrchestrationGraphInDB(graph, AmbianceUtils.getAccountId(planExecution.getAmbiance()));
      pmsExecutionSummaryService.regenerateStageLayoutGraph(planExecutionId, stageNodeExecutions, planExecution);
      return graph;
    } catch (Exception ex) {
      log.error("Exception occurred while generating graph from nodeExecutions", ex);
      throw new InvalidRequestException(
          "Could not fetch graph for the given execution. It might have been deleted or does not exist");
    }
  }

  public OrchestrationGraph buildOrchestrationGraphForNodeExecution(
      String planExecutionId, String nodeExecutionId, List<NodeExecution> nodeExecutions) {
    return OrchestrationGraph.builder()
        .cacheKey(planExecutionId)
        .cacheContextOrder(System.currentTimeMillis())
        .cacheParams(null)
        .planExecutionId(planExecutionId)
        .rootNodeIds(Lists.newArrayList(nodeExecutionId))
        .adjacencyList(orchestrationAdjacencyListGenerator.generateAdjacencyList(nodeExecutionId, nodeExecutions, true))
        .build();
  }

  @Override
  public void deleteOutputsForStepInGraph(
      String accountIdentifier, String planExecutionId, String stepType, Long endTs, Status status) {
    OrchestrationGraph orchestrationGraph =
        getCachedOrchestrationGraphFromSecondary(accountIdentifier, planExecutionId);
    orchestrationGraph.getAdjacencyList().getGraphVertexMap().forEach((id, vertex) -> {
      if (Objects.equals(vertex.getStepType(), stepType)) {
        vertex.setOutcomeDocuments(new HashMap<>());
      }
    });
    cacheOrchestrationGraphInDB(orchestrationGraph, accountIdentifier);
    if (endTs != null) {
      retentionIteratorEntityService.syncToObjectStore(accountIdentifier, planExecutionId, endTs, status);
    }
  }

  /**
   * Maps an OrchestrationGraph to a WorkflowGraph for visualization purposes.
   *
   * @param accountIdentifier The account identifier
   * @param planExecutionId The plan execution ID
   * @param nodeExecutionId The starting node execution ID for traversal (optional)
   * @param depth The maximum depth to traverse from the starting node
   * @return A WorkflowGraph containing nodes and relations up to the specified depth
   */
  @Override
  public WorkflowGraph generateWorkflowGraph(
      String accountIdentifier, String planExecutionId, String nodeExecutionId, int depth) {
    // Get the orchestration graph
    OrchestrationGraph orchestrationGraph = getCachedOrchestrationGraphFromDB(planExecutionId, accountIdentifier);
    if (orchestrationGraph == null) {
      return WorkflowGraph.builder().data(new HashMap<>()).relation(new HashMap<>()).build();
    }

    // Initialize result containers
    Map<String, WorkflowGraphNode> data = new HashMap<>();
    Map<String, WorkflowGraphRelation> relation = new HashMap<>();

    // Get the source data structures
    Map<String, GraphVertex> vertexMap = orchestrationGraph.getAdjacencyList().getGraphVertexMap();
    Map<String, EdgeListInternal> adjacencyMap = orchestrationGraph.getAdjacencyList().getAdjacencyMap();

    // If nodeExecutionId is not specified, use the root node
    if (nodeExecutionId == null || nodeExecutionId.isEmpty()) {
      if (orchestrationGraph.getRootNodeIds() != null && !orchestrationGraph.getRootNodeIds().isEmpty()) {
        nodeExecutionId = orchestrationGraph.getRootNodeIds().get(0);
      } else {
        // No valid starting point
        return WorkflowGraph.builder().data(data).relation(relation).build();
      }
    }

    // Check if starting node exists
    if (!vertexMap.containsKey(nodeExecutionId)) {
      // Starting node not found
      return WorkflowGraph.builder().data(data).relation(relation).build();
    }

    // Track visited nodes to avoid cycles
    Set<String> visited = new HashSet<>();

    // Queue for BFS traversal with node ID and its depth
    Queue<Pair<String, Integer>> queue = new LinkedList<>();
    queue.add(Pair.of(nodeExecutionId, 0)); // Start with depth 0

    // Breadth-first traversal
    while (!queue.isEmpty()) {
      Pair<String, Integer> current = queue.poll();
      String currentNodeId = current.getLeft();
      int currentDepth = current.getRight();

      // Skip if already visited or exceeds max depth
      if (visited.contains(currentNodeId) || currentDepth > depth) {
        continue;
      }

      // Mark as visited
      visited.add(currentNodeId);

      // Get the vertex and map it to WorkflowGraphNode
      GraphVertex vertex = vertexMap.get(currentNodeId);
      if (vertex != null) {
        WorkflowGraphNode node = mapToWorkflowGraphNode(vertex, accountIdentifier);
        data.put(currentNodeId, node);
      }

      // Get adjacency information and map to WorkflowGraphRelation
      EdgeListInternal edgeList = adjacencyMap.get(currentNodeId);
      if (edgeList != null) {
        WorkflowGraphRelation graphRelation = mapToWorkflowGraphRelation(edgeList);
        relation.put(currentNodeId, graphRelation);

        // Only enqueue next level nodes if we haven't reached max depth
        if (currentDepth < depth) {
          // Process nextIds (siblings) of current node at the same depth level
          if (edgeList.getNextIds() != null) {
            for (String nextId : edgeList.getNextIds()) {
              if (!visited.contains(nextId)) {
                queue.add(Pair.of(nextId, currentDepth)); // Same depth for siblings
              }
            }
          }

          // Add children to the queue at next depth level
          if (edgeList.getEdges() != null) {
            for (String childId : edgeList.getEdges()) {
              if (!visited.contains(childId)) {
                queue.add(Pair.of(childId, currentDepth + 1));
              }
            }
          }
        }
      }
    }

    return WorkflowGraph.builder().data(data).relation(relation).build();
  }

  /**
   * Maps a GraphVertex to a WorkflowGraphNode with simplified fields.
   */
  private WorkflowGraphNode mapToWorkflowGraphNode(GraphVertex vertex, String accountIdentifier) {
    // Extract inputs and outputs from vertex
    Map<String, Object> inputs = extractInputs(vertex);
    Map<String, Object> outputs = extractOutputs(vertex, accountIdentifier);

    return WorkflowGraphNode.builder()
        .uuid(vertex.getUuid())
        .name(vertex.getName())
        .identifier(vertex.getIdentifier())
        .status(vertex.getStatus())
        .inputs(inputs)
        .outputs(outputs)
        .build();
  }

  /**
   * Maps an EdgeListInternal to a WorkflowGraphRelation with renamed fields.
   */
  private WorkflowGraphRelation mapToWorkflowGraphRelation(EdgeListInternal edgeList) {
    return WorkflowGraphRelation.builder()
        .parentId(edgeList.getParentId())
        .prevIds(edgeList.getPrevIds())
        .nextIds(edgeList.getNextIds())
        .children(edgeList.getEdges()) // Note: renamed from edges to children
        .build();
  }

  /**
   * Extracts input parameters from a GraphVertex.
   */
  private Map<String, Object> extractInputs(GraphVertex vertex) {
    Map<String, Object> inputs = new HashMap<>();
    if (vertex.getStepParameters() != null) {
      // Get the PmsStepParameters and convert to a map
      try {
        inputs = vertex.getPmsStepParameters();
      } catch (Exception e) {
        log.warn("Failed to extract inputs from vertex {}: {}", vertex.getUuid(), e.getMessage());
      }
    }
    return inputs;
  }

  /**
   * Extracts output results from a GraphVertex.
   * If outcomeDocuments doesn't contain a log entry but logBaseKey is present,
   * generates the log URL from logBaseKey.
   */
  private Map<String, Object> extractOutputs(GraphVertex vertex, String accountIdentifier) {
    Map<String, Object> outputs = new HashMap<>();
    if (vertex.getOutcomeDocuments() != null) {
      // Get the outcomes map
      try {
        outputs = vertex.getPmsOutcomes()
                      .entrySet()
                      .stream()
                      .filter(entry -> entry.getValue() != null)
                      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue()));
      } catch (Exception e) {
        log.warn("Failed to extract outputs from vertex {}: {}", vertex.getUuid(), e.getMessage());
      }
    }

    // If no log outcome exists but logBaseKey is present, generate the log URL
    if (!outputs.containsKey("log") && !isEmpty(vertex.getLogBaseKey())) {
      try {
        String logUrl = generateLogUrl(vertex.getLogBaseKey(), accountIdentifier);
        if (!isEmpty(logUrl)) {
          Map<String, Object> logOutcome = new HashMap<>();
          logOutcome.put("url", logUrl);
          outputs.put("log", logOutcome);
        }
      } catch (Exception e) {
        log.warn("Failed to generate log URL for vertex {}", vertex.getUuid(), e);
      }
    }
    return outputs;
  }

  /**
   * Generates a log download URL from the logBaseKey.
   */
  private String generateLogUrl(String logBaseKey, String accountIdentifier) {
    if (logServiceUrlProvider == null || isEmpty(accountIdentifier)) {
      log.debug("LogServiceUrlProvider not available or accountIdentifier is null/empty, cannot generate log URL");
      return null;
    }
    String logServiceBaseUrl = logServiceUrlProvider.getLogServiceBaseUrl(accountIdentifier);
    if (isEmpty(logServiceBaseUrl)) {
      return null;
    }
    return String.format(StepUtils.LOG_SERVICE_DOWNLOAD_LOG_URL, logServiceBaseUrl, accountIdentifier, logBaseKey);
  }

  private OrchestrationGraphDTO generatePartialGraph(String startId, OrchestrationGraph orchestrationGraph) {
    EphemeralOrchestrationGraph ephemeralOrchestrationGraph =
        EphemeralOrchestrationGraph.builder()
            .planExecutionId(orchestrationGraph.getPlanExecutionId())
            .rootNodeIds(Lists.newArrayList(startId))
            .startTs(orchestrationGraph.getStartTs())
            .endTs(orchestrationGraph.getEndTs())
            .status(orchestrationGraph.getStatus())
            .adjacencyList(orchestrationAdjacencyListGenerator.generatePartialAdjacencyList(
                startId, orchestrationGraph.getAdjacencyList()))
            .build();
    // removing the vertices which needs to hidden in graph rendering
    vertexSkipperService.removeSkippedVertices(ephemeralOrchestrationGraph);

    return OrchestrationGraphDTOConverter.convertFrom(ephemeralOrchestrationGraph);
  }

  private String obtainStartingIdFromSetupNodeIdAndExecutionId(
      Map<String, GraphVertex> graphVertexMap, String startingSetupNodeId, String startingExecutionId) {
    List<GraphVertex> vertexList = graphVertexMap.values()
                                       .stream()
                                       .filter(vertex -> {
                                         if (startingExecutionId != null) {
                                           return vertex.getPlanNodeId().equals(startingSetupNodeId)
                                               && vertex.getUuid().equals(startingExecutionId);
                                         }
                                         return vertex.getPlanNodeId().equals(startingSetupNodeId);
                                       })
                                       .collect(Collectors.toList());
    if (vertexList.size() == 1) {
      return vertexList.get(0).getUuid();
    }
    if (vertexList.size() > 1) {
      log.error(String.format("Multiple node Ids found for a given combination of setupId: %s and ExecutionId: %s",
          startingSetupNodeId, startingExecutionId));
    }
    return null;
  }

  private String obtainStartingNodeExId(List<NodeExecution> nodeExecutions) {
    return nodeExecutions.stream()
        .filter(node -> EmptyPredicate.isEmpty(node.getParentId()) && EmptyPredicate.isEmpty(node.getPreviousId()))
        .findFirst()
        .orElseThrow(() -> new InvalidRequestException("Starting node is not found"))
        .getUuid();
  }

  private EphemeralOrchestrationGraph getEphemeralOrchestrationGraph(String accountIdentifier, String planExecutionId) {
    OrchestrationGraph cachedOrchestrationGraph =
        getCachedOrchestrationGraphFromSecondary(accountIdentifier, planExecutionId);
    if (cachedOrchestrationGraph == null) {
      cachedOrchestrationGraph = buildOrchestrationGraph(planExecutionId);
    } else {
      sendUpdateEventIfAny(cachedOrchestrationGraph, accountIdentifier);
    }
    EphemeralOrchestrationGraph ephemeralOrchestrationGraph =
        EphemeralOrchestrationGraphConverter.convertFrom(cachedOrchestrationGraph);
    vertexSkipperService.removeSkippedVertices(ephemeralOrchestrationGraph);
    return ephemeralOrchestrationGraph;
  }

  private boolean shouldNotifyAfterGraphGen(String accountId) {
    return accountId != null && pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_NOTIFY_AFTER_GRAPH_UPDATE);
  }

  @Override
  public Map<String, GraphLayoutNodeDTO> getStageLayoutNodesFromPostgres(
      String accountIdentifier, String planExecutionId) {
    if (planExecutionId == null) {
      return null;
    }

    if (!isCdcGraphEnabledForExecution(accountIdentifier, planExecutionId)) {
      return null;
    }

    Map<String, GraphLayoutNodeDTO> layoutNodes = graphCDCService.getStageLayoutNodes(planExecutionId);

    if (layoutNodes == null || layoutNodes.isEmpty()) {
      log.debug("[NORMALIZED-PG] No stage layout nodes found in PostgreSQL for planExecutionId: {}", planExecutionId);
      return null;
    }

    log.debug("[NORMALIZED-PG] Retrieved {} stage layout nodes from PostgreSQL for planExecutionId: {}",
        layoutNodes.size(), planExecutionId);
    return layoutNodes;
  }

  @Override
  public Map<String, Object> getPipelineModuleInfoFromPostgres(String planExecutionId) {
    if (planExecutionId == null) {
      return null;
    }

    Map<String, Object> moduleInfo = graphCDCService.getPipelineModuleInfo(planExecutionId);

    if (moduleInfo == null || moduleInfo.isEmpty()) {
      log.debug("[NORMALIZED-PG] No pipeline module info found in PostgreSQL for planExecutionId: {}", planExecutionId);
      return null;
    }

    log.debug(
        "[NORMALIZED-PG] Retrieved pipeline module info from PostgreSQL for planExecutionId: {}", planExecutionId);
    return moduleInfo;
  }
}
