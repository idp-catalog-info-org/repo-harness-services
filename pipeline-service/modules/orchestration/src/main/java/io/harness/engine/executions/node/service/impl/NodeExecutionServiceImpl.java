/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.PmsCommonConstants.AUTO_ABORT_PIPELINE_THROUGH_TRIGGER;
import static io.harness.pms.contracts.execution.Status.ABORTED;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.EXPIRED;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.springdata.PersistenceUtils.getRetryPolicy;
import static io.harness.springdata.SpringDataMongoUtils.returnNewOptions;

import static org.springframework.data.domain.Sort.by;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.concurrency.counter.StepConcurrencyCounterMutationHook;
import io.harness.engine.executions.node.config.StuckNodeExecutionsMarkingConfig;
import io.harness.engine.executions.node.exception.NodeExecutionUpdateFailedException;
import io.harness.engine.executions.node.helper.NodeExecutionReadHelper;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.engine.observers.NodeExecutionDeleteObserver;
import io.harness.engine.observers.NodeExecutionStartObserver;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.event.OrchestrationLogPublisher;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.NodeExecutionContextUtils;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.interrupts.InterruptEffect;
import io.harness.logging.UnitProgress;
import io.harness.monitoring.ExecutionStatistics;
import io.harness.observer.Subject;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEvent.Builder;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.springdata.TransactionHelper;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(PIPELINE)
public class NodeExecutionServiceImpl implements NodeExecutionService {
  private static final int MAX_BATCH_SIZE = 500;

  private static final Set<String> GRAPH_FIELDS = Set.of(NodeExecutionKeys.mode, NodeExecutionKeys.progressData,
      NodeExecutionKeys.unitProgresses, NodeExecutionKeys.executableResponses, NodeExecutionKeys.interruptHistories,
      NodeExecutionKeys.retryIds, NodeExecutionKeys.oldRetry, NodeExecutionKeys.failureInfo, NodeExecutionKeys.endTs,
      NodeExecutionKeys.excludedKeysFromStepInputs);
  @Inject private MongoTemplate mongoTemplate;
  @Inject private OrchestrationEventEmitter eventEmitter;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private TransactionHelper transactionHelper;
  @Inject private OrchestrationLogPublisher orchestrationLogPublisher;
  @Inject private NodeExecutionReadHelper nodeExecutionReadHelper;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PlanService planService;
  @Inject private PlanExpansionService planExpansionService;
  @Inject private NodeExecutionInfoService nodeExecutionInfoService;
  @Inject private StepConcurrencyCounterMutationHook counterMutationHook;

  @Getter private final Subject<NodeStatusUpdateObserver> nodeStatusUpdateSubject = new Subject<>();
  @Getter private final Subject<NodeExecutionStartObserver> nodeExecutionStartSubject = new Subject<>();
  @Getter private final Subject<NodeExecutionDeleteObserver> nodeDeleteObserverSubject = new Subject<>();
  @Inject private StuckNodeExecutionsMarkingConfig stuckNodeExecutionsMarkingConfig;

  private final int MAX_DEPTH = 15;

  // Retry the processing-mark write once on transient Mongo failures (e.g. socket drops during a failover/stepdown).
  // Uses the same transient-error matching as DEFAULT_RETRY_POLICY but with a single retry. See PIPE-35791.
  private static final RetryPolicy<Object> MARK_PROCESSING_RETRY_POLICY =
      getRetryPolicy("Retrying markNodesProcessing. Attempt No. {}", "markNodesProcessing failed. Attempt No. {}")
          .withMaxAttempts(2);

  @Override
  public NodeExecution get(String nodeExecutionId) {
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    Optional<NodeExecution> nodeExecutionOptional = nodeExecutionReadHelper.getOneWithoutProjections(query);
    if (nodeExecutionOptional.isEmpty()) {
      throw new InvalidRequestException("Node Execution is null for id: " + nodeExecutionId);
    }
    return nodeExecutionOptional.get();
  }

  @Override
  public Stream<NodeExecution> get(List<String> nodeExecutionIds) {
    Query query = query(where(NodeExecutionKeys.uuid).in(nodeExecutionIds));
    return nodeExecutionReadHelper.fetchNodeExecutionsWithAllFields(query);
  }

  @Override
  public NodeExecution getWithFieldsIncluded(String nodeExecutionId, Set<String> fieldsToInclude) {
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    Optional<NodeExecution> nodeExecutionOptional = nodeExecutionReadHelper.getOne(query);
    if (nodeExecutionOptional.isEmpty()) {
      throw new InvalidRequestException("Node Execution is null for id: " + nodeExecutionId);
    }
    return nodeExecutionOptional.get();
  }

  @Override
  public NodeExecution getWithFieldsIncludedFromSecondary(String nodeExecutionId, Set<String> fieldsToInclude) {
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    Optional<NodeExecution> nodeExecutionOptional = nodeExecutionReadHelper.getOneFromSecondary(query);
    if (nodeExecutionOptional.isEmpty()) {
      throw new InvalidRequestException("Node Execution is null for id: " + nodeExecutionId);
    }
    return nodeExecutionOptional.get();
  }

  public Optional<NodeExecution> getOptional(String nodeExecutionId, Set<String> fieldsToInclude) {
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.getOne(query);
  }

  @Override
  public Optional<NodeExecution> getPipelineNodeExecutionWithProjections(
      @NonNull String planExecutionId, Set<String> fields) {
    // Uses - planExecutionId_stepCategory_identifier_idx
    // Sort is not part of index, as node selection is always one node thus it will not impact much
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).is(StepCategory.PIPELINE))
                      .with(Sort.by(Direction.ASC, NodeExecutionKeys.createdAt));
    for (String fieldName : fields) {
      query.fields().include(fieldName);
    }
    return Optional.ofNullable(mongoTemplate.findOne(query, NodeExecution.class));
  }

  // TODO (alexi) : Handle the case where multiple instances are returned
  @Override
  public NodeExecution getByPlanNodeUuid(String planNodeUuid, String planExecutionId) {
    // Uses - planExecutionId_nodeId_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.nodeId).is(planNodeUuid));
    NodeExecution nodeExecution = mongoTemplate.findOne(query, NodeExecution.class);
    if (nodeExecution == null) {
      throw new InvalidRequestException("Node Execution is null for planNodeUuid: " + planNodeUuid);
    }
    return nodeExecution;
  }

  @Override
  public List<NodeExecution> getAll(Set<String> nodeExecutionIds) {
    if (EmptyPredicate.isEmpty(nodeExecutionIds)) {
      return new ArrayList<>();
    }

    if (nodeExecutionIds.size() > MAX_BATCH_SIZE) {
      throw new InvalidRequestException(
          String.format("requested %d records more than threshold of %d. consider pagination", nodeExecutionIds.size(),
              MAX_BATCH_SIZE));
    }
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).in(nodeExecutionIds));
    return nodeExecutionReadHelper.fetchNodeExecutionsWithoutProjections(query);
  }

  @Override
  public List<NodeExecution> getAllWithFieldIncluded(Set<String> nodeExecutionIds, Set<String> fieldsToInclude) {
    if (EmptyPredicate.isEmpty(nodeExecutionIds)) {
      return new ArrayList<>();
    }

    if (nodeExecutionIds.size() > MAX_BATCH_SIZE) {
      throw new InvalidRequestException(
          String.format("requested %d records more than threshold of %d. consider pagination", nodeExecutionIds.size(),
              MAX_BATCH_SIZE));
    }
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).in(nodeExecutionIds));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public Stream<NodeExecution> fetchAllNodeExecutions(String planExecutionId, Set<String> fieldsToInclude) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutionsFromAnalytics(query);
  }

  @Override
  public Stream<NodeExecution> fetchAllStepNodeExecutions(String planExecutionId, Set<String> fieldsToInclude) {
    // Uses - planExecutionId_stepCategory_identifier_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).is(StepCategory.STEP));
    for (String fieldName : fieldsToInclude) {
      query.fields().include(fieldName);
    }
    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  @Override
  public List<Status> fetchNodeExecutionsStatusesWithoutOldRetries(
      String planExecutionId, boolean ignoreIdentityNodes) {
    // Both Query Uses - planExecutionId_mode_status_oldRetry_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    if (ignoreIdentityNodes) {
      query =
          query.addCriteria(where(NodeExecutionKeys.nodeType).in(Lists.newArrayList(null, NodeType.PLAN_NODE.name())));
    }
    // Exclude so that it can use Projection covered from index without scanning documents.
    query.fields().exclude(NodeExecutionKeys.id).include(NodeExecutionKeys.status);
    List<NodeExecution> nodeExecutions = new LinkedList<>();
    try (Stream<NodeExecution> stream = nodeExecutionReadHelper.fetchNodeExecutions(query)) {
      Iterator<NodeExecution> iterator = stream.iterator();
      while (iterator.hasNext()) {
        nodeExecutions.add(iterator.next());
      }
    }
    return nodeExecutions.stream().map(NodeExecution::getStatus).collect(Collectors.toList());
  }

  @Override
  public List<Status> fetchNonFlowingAndNonFinalStatuses(String planExecutionId) {
    // Uses - planExecutionId_mode_status_oldRetry_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).in(StatusUtils.nonFlowingAndNonFinalStatuses()))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    // Exclude so that it can use Projection simplified from index without scanning documents.
    query.fields().exclude(NodeExecutionKeys.id).include(NodeExecutionKeys.status);
    List<NodeExecution> nodeExecutions = new LinkedList<>();
    try (Stream<NodeExecution> stream = nodeExecutionReadHelper.fetchNodeExecutions(query)) {
      Iterator<NodeExecution> iterator = stream.iterator();
      while (iterator.hasNext()) {
        nodeExecutions.add(iterator.next());
      }
    }
    return nodeExecutions.stream().map(NodeExecution::getStatus).collect(Collectors.toList());
  }

  @Override
  public List<NodeExecution> fetchWaitingStatusNodeExecutions(String planExecutionId, Set<String> fieldsToInclude) {
    // Uses - planExecutionId_mode_status_oldRetry_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).in(StatusUtils.waitingStatuses()))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    if (EmptyPredicate.isNotEmpty(fieldsToInclude)) {
      fieldsToInclude.forEach(o -> query.fields().include(o));
    }
    List<NodeExecution> nodeExecutions = new LinkedList<>();
    try (Stream<NodeExecution> stream = nodeExecutionReadHelper.fetchNodeExecutions(query)) {
      Iterator<NodeExecution> iterator = stream.iterator();
      while (iterator.hasNext()) {
        nodeExecutions.add(iterator.next());
      }
    }
    return nodeExecutions;
  }

  @Override
  public List<Status> fetchDistinctWaitingStatusesForStage(
      String planExecutionId, String stageExecutionId, String excludeNodeExecutionId) {
    // Uses - planExecutionId_mode_status_oldRetry_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stageExecutionId).is(stageExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).in(StatusUtils.waitingStatuses()))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false))
                      .addCriteria(where(NodeExecutionKeys.uuid).nin(excludeNodeExecutionId, stageExecutionId));
    return mongoTemplate.findDistinct(query, NodeExecutionKeys.status, NodeExecution.class, Status.class);
  }

  @Override
  public List<Status> fetchDistinctWaitingStatusesForPlan(
      String planExecutionId, String excludeNodeExecutionId, String excludeStageNodeExecutionId) {
    // Uses - planExecutionId_mode_status_oldRetry_idx
    Query query =
        query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
            .addCriteria(where(NodeExecutionKeys.status).in(StatusUtils.waitingStatuses()))
            .addCriteria(where(NodeExecutionKeys.oldRetry).is(false))
            .addCriteria(where(NodeExecutionKeys.uuid).nin(excludeNodeExecutionId, excludeStageNodeExecutionId));
    return mongoTemplate.findDistinct(query, NodeExecutionKeys.status, NodeExecution.class, Status.class);
  }

  @Override
  public Stream<NodeExecution> fetchNodeExecutionsWithoutOldRetriesIterator(String planExecutionId) {
    // Uses - planExecutionId_oldRetry_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    // Can't use fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator as it uses projections
    return nodeExecutionReadHelper.fetchNodeExecutionsIteratorWithoutProjections(query);
  }

  @Override
  public Stream<NodeExecution> fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
      String planExecutionId, EnumSet<Status> statuses, @NotNull Set<String> fieldsToInclude) {
    // Uses - planExecutionId_status_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    if (EmptyPredicate.isNotEmpty(statuses)) {
      query.addCriteria(where(NodeExecutionKeys.status).in(statuses));
    }
    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  @Override
  public Stream<NodeExecution> fetchNodeExecutionsWithoutOldRetriesIterator(
      String planExecutionId, @NotNull Set<String> fieldsToInclude) {
    return fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
        planExecutionId, EnumSet.noneOf(Status.class), fieldsToInclude);
  }

  @Override
  public Stream<NodeExecution> fetchChildrenNodeExecutionsIterator(
      String planExecutionId, String parentId, Set<String> fieldsToBeIncluded) {
    // Uses planExecutionId_parentId_createdAt_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.parentId).is(parentId))
                      .with(Sort.by(Direction.DESC, NodeExecutionKeys.createdAt));
    for (String field : fieldsToBeIncluded) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  @Override
  public Stream<NodeExecution> fetchChildrenNodeExecutionsIterator(
      String planExecutionId, String parentId, Direction sortOrderOfCreatedAt, Set<String> fieldsToBeIncluded) {
    return fetchChildrenNodeExecutionsIteratorWithProjection(
        planExecutionId, List.of(parentId), sortOrderOfCreatedAt, fieldsToBeIncluded);
  }

  @VisibleForTesting
  Stream<NodeExecution> fetchChildrenNodeExecutionsIteratorWithoutProjection(
      String planExecutionId, List<String> parentIds) {
    // Uses planExecutionId_parentId_createdAt_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.parentId).in(parentIds))
                      .with(Sort.by(Direction.ASC, NodeExecutionKeys.createdAt));
    return nodeExecutionReadHelper.fetchNodeExecutionsIteratorWithoutProjectionsFromSecondary(query);
  }

  Stream<NodeExecution> fetchChildrenNodeExecutionsIteratorWithProjection(
      String planExecutionId, List<String> parentIds, Direction sortOrderOfCreatedAt, Set<String> fieldsToBeIncluded) {
    // Uses planExecutionId_parentId_createdAt_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.parentId).in(parentIds))
                      .with(Sort.by(sortOrderOfCreatedAt, NodeExecutionKeys.createdAt));
    for (String field : fieldsToBeIncluded) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  public List<NodeExecution> fetchChildrenNodeExecutionsRecursivelyFromGivenParentIdWithoutOldRetries(
      String planExecutionId, List<String> parentIds) {
    return fetchChildrenNodeExecutionsRecursivelyFromGivenParentId(planExecutionId, parentIds, MAX_DEPTH);
  }

  private List<NodeExecution> fetchChildrenNodeExecutionsRecursivelyFromGivenParentId(
      String planExecutionId, List<String> parentIds, int depth) {
    if (depth <= 0) {
      throw new InvalidRequestException(
          String.format("Exceeded Max Depth level [%s] for the Node SubGraph", MAX_DEPTH));
    }
    if (EmptyPredicate.isEmpty(parentIds)) {
      return new ArrayList<>();
    }
    List<NodeExecution> recursiveChildrenNodeExecutions = new LinkedList<>();
    try (Stream<NodeExecution> stream =
             fetchChildrenNodeExecutionsIteratorWithoutProjection(planExecutionId, parentIds)) {
      Iterator<NodeExecution> iterator = stream.iterator();
      while (iterator.hasNext()) {
        recursiveChildrenNodeExecutions.add(iterator.next());
      }
    }
    List<String> childParentIds = new LinkedList<>();
    if (EmptyPredicate.isEmpty(recursiveChildrenNodeExecutions)) {
      return new ArrayList<>();
    }
    for (NodeExecution nodeExecution : recursiveChildrenNodeExecutions) {
      childParentIds.add(nodeExecution.getUuid());
    }
    List<NodeExecution> childNodeExecutions =
        fetchChildrenNodeExecutionsRecursivelyFromGivenParentId(planExecutionId, childParentIds, depth - 1);
    recursiveChildrenNodeExecutions.addAll(childNodeExecutions);
    return recursiveChildrenNodeExecutions;
  }

  @Override
  public Stream<NodeExecution> fetchChildrenNodeExecutionsIterator(String parentId, Set<String> fieldsToBeIncluded) {
    // Uses planExecutionId_parentId_createdAt_idx
    Query query = query(where(NodeExecutionKeys.parentId).is(parentId));
    for (String field : fieldsToBeIncluded) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  @Override
  public Stream<NodeExecution> fetchAllNodeExecutionsByStatusIteratorFromAnalytics(
      EnumSet<Status> statuses, Set<String> fieldsToBeIncluded) {
    // Uses status_idx index
    Query query = query(where(NodeExecutionKeys.status).in(statuses));
    for (String fieldName : fieldsToBeIncluded) {
      query.fields().include(fieldName);
    }
    return nodeExecutionReadHelper.fetchNodeExecutionsFromAnalytics(query);
  }

  @Override
  public long findCountByParentIdAndStatusIn(String parentId, Set<Status> flowingStatuses) {
    // Uses - parentId_status_idx index
    Query query =
        query(where(NodeExecutionKeys.parentId).is(parentId)).addCriteria(where(NodeExecutionKeys.oldRetry).is(false));

    if (EmptyPredicate.isNotEmpty(flowingStatuses)) {
      query.addCriteria(where(NodeExecutionKeys.status).in(flowingStatuses));
    }
    return nodeExecutionReadHelper.findCount(query);
  }

  @Override
  public List<NodeExecution> extractChildExecutions(String parentId, boolean includeParent,
      List<NodeExecution> finalList, List<NodeExecution> allExecutions, boolean includeChildrenOfStrategy) {
    Map<String, List<NodeExecution>> parentChildrenMap = new HashMap<>();
    for (NodeExecution execution : allExecutions) {
      if (parentChildrenMap.containsKey(execution.getParentId())) {
        parentChildrenMap.get(execution.getParentId()).add(execution);
      } else {
        List<NodeExecution> cList = new ArrayList<>();
        cList.add(execution);
        parentChildrenMap.put(execution.getParentId(), cList);
      }
    }
    extractChildList(parentChildrenMap, parentId, finalList, includeChildrenOfStrategy);
    if (includeParent) {
      finalList.add(allExecutions.stream()
                        .filter(ne -> ne.getUuid().equals(parentId))
                        .findFirst()
                        .orElseThrow(() -> new UnexpectedException("Pipeline has already completed execution")));
    }
    return finalList;
  }

  // Extracts child list recursively from parentChildrenMap into finalList
  private void extractChildList(Map<String, List<NodeExecution>> parentChildrenMap, String parentId,
      List<NodeExecution> finalList, boolean includeChildrenOfStrategy) {
    List<NodeExecution> children = parentChildrenMap.get(parentId);
    if (isEmpty(children)) {
      return;
    }
    finalList.addAll(children);
    children.forEach(child -> {
      // NOTE: We are ignoring the status of steps inside strategy because of max concurrency defined.
      // We need to run all the steps inside strategy once
      if (includeChildrenOfStrategy || child.getStepType().getStepCategory() != StepCategory.STRATEGY) {
        extractChildList(parentChildrenMap, child.getUuid(), finalList, includeChildrenOfStrategy);
      }
    });
  }

  private ProjectionOperation createProjectionFromSet(Set<String> fields) {
    ProjectionOperation projection = Aggregation.project();

    for (String field : fields) {
      projection = projection.and(field).as(field);
    }

    return projection;
  }

  @Override
  public List<NodeExecution> findAllChildrenWithStatusInAndWithoutOldRetriesV2(
      String planExecutionId, String parentId, EnumSet<Status> statuses, boolean includeChildrenOfStrategy) {
    var graphLookupOperation = Aggregation.graphLookup("nodeExecutions")
                                   .startWith("$_id")
                                   .connectFrom("_id")
                                   .connectTo("parentId")
                                   .maxDepth(10)
                                   .as("descendants");

    Aggregation aggregation = Aggregation.newAggregation(
        Aggregation.match(
            Criteria.where(NodeExecutionKeys.planExecutionId).is(planExecutionId).and("_id").is(parentId)),

        graphLookupOperation,

        Aggregation.unwind("$descendants"), Aggregation.replaceRoot("descendants"),
        Aggregation.match(Criteria.where(NodeExecutionKeys.oldRetry).is(false)),
        // This limit caps the retrieval of nodeExecutions to 200k. This is a hard limit.
        Aggregation.limit(200_000), createProjectionFromSet(NodeProjectionUtils.fieldsForAllChildrenExtractor));

    List<NodeExecution> allDescendants =
        mongoTemplate.aggregate(aggregation, "nodeExecutions", NodeExecution.class).getMappedResults();

    // Filter out children of STRATEGY nodes in memory if needed
    if (!includeChildrenOfStrategy && isNotEmpty(allDescendants)) {
      return filterOutChildrenOfStrategyNodes(allDescendants);
    }

    return allDescendants;
  }

  /**
   * Filters out descendants of STRATEGY nodes while keeping STRATEGY nodes themselves.
   * Uses the same logic as extractChildList() method.
   */
  private List<NodeExecution> filterOutChildrenOfStrategyNodes(List<NodeExecution> allDescendants) {
    // Build a set of STRATEGY node UUIDs for quick lookup
    Set<String> strategyNodeIds = allDescendants.stream()
                                      .filter(ne -> ne.getStepType().getStepCategory() == StepCategory.STRATEGY)
                                      .map(NodeExecution::getUuid)
                                      .collect(Collectors.toSet());

    // Build a parent-child map for quick lookup
    Map<String, List<NodeExecution>> parentChildMap = new HashMap<>();
    for (NodeExecution ne : allDescendants) {
      String parentId = ne.getParentId();
      if (parentId != null) {
        parentChildMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(ne);
      }
    }

    // Recursively collect all descendants of strategy nodes
    Set<String> allStrategyDescendants = new HashSet<>();
    for (String strategyNodeId : strategyNodeIds) {
      collectAllDescendants(strategyNodeId, parentChildMap, allStrategyDescendants);
    }

    // Filter out all descendants of strategy nodes
    return allDescendants.stream()
        .filter(ne -> !allStrategyDescendants.contains(ne.getUuid()))
        .collect(Collectors.toList());
  }

  /**
   * Recursively collects all descendants of a given parent node.
   * This traverses the entire subtree and adds all descendant UUIDs to the provided set.
   */
  private void collectAllDescendants(
      String parentId, Map<String, List<NodeExecution>> parentChildMap, Set<String> descendants) {
    List<NodeExecution> children = parentChildMap.get(parentId);
    if (children == null || children.isEmpty()) {
      return;
    }

    for (NodeExecution child : children) {
      descendants.add(child.getUuid());
      // Recursively collect descendants of this child
      collectAllDescendants(child.getUuid(), parentChildMap, descendants);
    }
  }

  @Override
  public List<NodeExecution> findAllChildrenWithStatusInAndWithoutOldRetries(String planExecutionId, String parentId,
      EnumSet<Status> flowingStatuses, boolean includeParent, Set<String> fieldsToBeIncluded,
      boolean includeChildrenOfStrategy) {
    List<NodeExecution> finalList = new ArrayList<>();
    Set<String> finalFieldsToBeIncluded = new HashSet<>(NodeProjectionUtils.fieldsForAllChildrenExtractor);
    if (EmptyPredicate.isNotEmpty(fieldsToBeIncluded)) {
      finalFieldsToBeIncluded.addAll(fieldsToBeIncluded);
    }
    List<NodeExecution> allExecutions = new LinkedList<>();

    try (Stream<NodeExecution> stream = fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             planExecutionId, flowingStatuses, finalFieldsToBeIncluded)) {
      Iterator<NodeExecution> iterator = stream.iterator();
      while (iterator.hasNext()) {
        allExecutions.add(iterator.next());
      }
    }
    return extractChildExecutions(parentId, includeParent, finalList, allExecutions, includeChildrenOfStrategy);
  }

  @Override
  public NodeExecution save(NodeExecution nodeExecution) {
    if (nodeExecution.getVersion() == null) {
      NodeExecution savedNodeExecution = transactionHelper.performTransaction(() -> {
        NodeExecution nodeExecution1 = mongoTemplate.insert(nodeExecution);
        orchestrationLogPublisher.onNodeStart(NodeStartInfo.builder().nodeExecution(nodeExecution).build());
        return nodeExecution1;
      });
      if (savedNodeExecution != null) {
        // Havnt added triggerPayload in the event as no one is consuming triggerPayload on NodeExecutionStart
        Builder builder = OrchestrationEvent.newBuilder()
                              .setAmbiance(getAmbiance(nodeExecution))
                              .setStatus(nodeExecution.getStatus())
                              .setEventType(OrchestrationEventType.NODE_EXECUTION_START)
                              .setServiceName(nodeExecution.getModule());

        // @Todo(Archit): Send Original StepParameters incase of PipelineRollback
        if (checkPresenceOfResolvedParametersForNonIdentityNodes(nodeExecution)) {
          builder.setStepParameters(nodeExecution.getResolvedStepParametersBytes());
        }
        eventEmitter.emitEvent(builder.build());
      }
      nodeExecutionStartSubject.fireInform(
          NodeExecutionStartObserver::onNodeStart, NodeStartInfo.builder().nodeExecution(savedNodeExecution).build());
      return savedNodeExecution;
    } else {
      NodeExecution savedNodeExecution = transactionHelper.performTransaction(() -> {
        orchestrationLogPublisher.onNodeStart(NodeStartInfo.builder().nodeExecution(nodeExecution).build());
        return mongoTemplate.save(nodeExecution);
      });
      if (savedNodeExecution != null) {
        emitEvent(savedNodeExecution, OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);
      }
      return savedNodeExecution;
    }
  }

  @VisibleForTesting
  boolean checkPresenceOfResolvedParametersForNonIdentityNodes(NodeExecution nodeExecution) {
    return nodeExecution.getNodeType() != null && NodeType.IDENTITY_PLAN_NODE != nodeExecution.getNodeType()
        && nodeExecution.getResolvedStepParameters() != null;
  }

  // Save a collection nodeExecutions.
  // This does not send any orchestration event. So if you want to do graph update operations on NodeExecution save,
  // then use the below save() method.
  @Override
  public List<NodeExecution> saveAll(Collection<NodeExecution> nodeExecutions) {
    return new ArrayList<>(mongoTemplate.insertAll(nodeExecutions));
  }

  @Override
  public NodeExecution update(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops) {
    return updateNodeExecutionInternal(nodeExecutionId, ops, new HashSet<>(), false, false);
  }

  @Override
  public NodeExecution update(
      @NonNull String nodeExecutionId, @NonNull Consumer<Update> ops, @NonNull Set<String> fieldsToBeIncluded) {
    return updateNodeExecutionInternal(nodeExecutionId, ops, fieldsToBeIncluded, true, false);
  }

  private NodeExecution updateNodeExecutionInternal(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops,
      @NonNull Set<String> fieldsToBeIncluded, boolean validateProjection,
      boolean updateForNonFinalNodeExecutionStatus) {
    return updateNodeExecutionInternal(
        nodeExecutionId, ops, fieldsToBeIncluded, validateProjection, updateForNonFinalNodeExecutionStatus, null);
  }

  // additionalCriteria, when non-null, is ANDed with the base uuid (and optional status) criteria.
  private NodeExecution updateNodeExecutionInternal(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops,
      @NonNull Set<String> fieldsToBeIncluded, boolean validateProjection, boolean updateForNonFinalNodeExecutionStatus,
      Criteria additionalCriteria) {
    Criteria criteria = where(NodeExecutionKeys.uuid).is(nodeExecutionId);
    if (updateForNonFinalNodeExecutionStatus) {
      criteria = criteria.and(NodeExecutionKeys.status).nin(StatusUtils.finalStatuses());
    }
    if (additionalCriteria != null) {
      criteria = criteria.andOperator(additionalCriteria);
    }
    Query query = query(criteria);
    if (validateProjection) {
      validateNodeExecutionProjection(fieldsToBeIncluded);
      fieldsToBeIncluded.addAll(NodeProjectionUtils.fieldsForNodeUpdateObserver);
      for (String field : fieldsToBeIncluded) {
        query.fields().include(field);
      }
    }
    Update updateOps = new Update().set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    ops.accept(updateOps);
    boolean shouldLog = shouldLog(updateOps);
    boolean shouldUpdateCache = shouldUpdateCache(updateOps);
    return transactionHelper.performTransaction(() -> {
      NodeExecution updated = mongoTemplate.findAndModify(query, updateOps, returnNewOptions, NodeExecution.class);
      if (updated == null) {
        throw new NodeExecutionUpdateFailedException(
            "Node Execution Cannot be updated with provided operations" + nodeExecutionId);
      }
      if (shouldLog) {
        orchestrationLogPublisher.onNodeUpdate(NodeUpdateInfo.builder().nodeExecution(updated).build());
      }
      if (shouldUpdateCache) {
        nodeExecutionInfoService.updateCalculatedStatusForParentNodes(updated);
      }
      return updated;
    });
  }

  @VisibleForTesting
  boolean shouldUpdateCache(Update updateOps) {
    Document setDoc = updateOps.getUpdateObject().get("$set", Document.class);
    return setDoc != null && setDoc.getBoolean(NodeExecutionKeys.advisorsProcessed) == Boolean.TRUE;
  }

  @VisibleForTesting
  boolean shouldLog(Update updateOps) {
    Set<String> fieldsUpdated = new HashSet<>();
    if (updateOps.getUpdateObject().containsKey("$set")) {
      fieldsUpdated.addAll(((Document) updateOps.getUpdateObject().get("$set")).keySet());
    }
    if (updateOps.getUpdateObject().containsKey("$addToSet")) {
      fieldsUpdated.addAll(((Document) updateOps.getUpdateObject().get("$addToSet")).keySet());
    }
    return fieldsUpdated.stream().anyMatch(GRAPH_FIELDS::contains);
  }

  @Override
  public void updateV2(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops) {
    updateNodeExecutionInternal(nodeExecutionId, ops, Sets.newHashSet(NodeExecutionKeys.uuid), true, false);
  }

  @Override
  public void updateV2ForNonFinalStatusNodeExecution(@NonNull String nodeExecutionId, @NonNull Consumer<Update> ops) {
    try {
      updateNodeExecutionInternal(nodeExecutionId, ops, Sets.newHashSet(NodeExecutionKeys.uuid), true, true);
    } catch (NodeExecutionUpdateFailedException ex) {
      // ignore this exception, this is thrown when the no node is updated
      log.info("Skipped updating node execution: {} as it is in final state", nodeExecutionId);
    }
  }

  @Override
  public void updateUnitProgressesIfNewer(
      @NonNull String nodeExecutionId, @NonNull List<UnitProgress> unitProgresses, long timestamp) {
    updateWithUnitProgressTimestampFence(nodeExecutionId, timestamp,
        ops
        -> ops.set(NodeExecutionKeys.unitProgresses, unitProgresses)
               .set(NodeExecutionKeys.progressData + "." + NodeExecutionKeys.unitProgresses, unitProgresses));
  }

  @Override
  public void updateWithUnitProgressTimestampFence(
      @NonNull String nodeExecutionId, long timestamp, @NonNull Consumer<Update> payloadOps) {
    // Drop the update unless the timestamp is at least as new as what's persisted (or nothing is persisted yet).
    // Inclusive (<=) so the two paths sharing one snapshot's token both land; strictly-older snapshots are dropped.
    Criteria timestampCriteria =
        new Criteria().orOperator(where(NodeExecutionKeys.unitProgressesTimestamp).exists(false),
            where(NodeExecutionKeys.unitProgressesTimestamp).lte(timestamp));
    try {
      updateNodeExecutionInternal(nodeExecutionId, ops -> {
        payloadOps.accept(ops);
        ops.set(NodeExecutionKeys.unitProgressesTimestamp, timestamp);
      }, Sets.newHashSet(NodeExecutionKeys.uuid), true, true, timestampCriteria);
    } catch (NodeExecutionUpdateFailedException ex) {
      // Expected: a newer update is already persisted or the node is final, so this stale update is dropped.
      log.debug(
          "Skipped stale unit progress update for nodeExecutionId: {} with timestamp: {}", nodeExecutionId, timestamp);
    }
  }

  /**
   * Always use this method while updating statuses. This guarantees we are hopping from correct statuses.
   * As we don't have transactions it is possible that your node execution state is manipulated by some other thread and
   * your transition is no longer valid.
   * <p>
   * Like your workflow is aborted but some other thread try to set it to running. Same logic applied to plan execution
   * status as well
   */

  @Override
  public NodeExecution updateStatusWithOps(@NonNull String nodeExecutionId, @NonNull Status status,
      Consumer<Update> ops, EnumSet<Status> overrideStatusSet) {
    Update updateOps = new Update();
    if (ops != null) {
      ops.accept(updateOps);
    }
    return updateStatusWithUpdate(nodeExecutionId, status, updateOps, overrideStatusSet);
  }

  @Override
  public void updateCalculatedStatusForParentStageNode(
      Ambiance ambiance, List<NodeExecution> allNonFlowingNodeExecutions) {
    String stageNodeExecutionId = AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);
    Status updateStatusTo = RUNNING;
    // Internal nodes(like spec/execution/steps) would not come here because the parent method is only fetching
    // nonFlowingNonFinal status nodeExecutions from DB.
    List<Status> childrenStatuses = allNonFlowingNodeExecutions.stream()
                                        .filter(ne -> stageNodeExecutionId.equals(ne.getStageExecutionId()))
                                        .map(NodeExecution::getStatus)
                                        .collect(Collectors.toList());
    Status calculatesStatus = StatusUtils.calculateStatus(childrenStatuses, ambiance.getPlanExecutionId());
    if (!StatusUtils.isFinalStatus(calculatesStatus)) {
      updateStatusTo = calculatesStatus;
    }
    updateStatusWithOps(stageNodeExecutionId, updateStatusTo, null, StatusUtils.activeStatuses());
  }

  private NodeExecution updateStatusWithUpdate(
      @NotNull String nodeExecutionId, @NotNull Status status, Update ops, EnumSet<Status> overrideStatusSet) {
    EnumSet<Status> allowedStartStatuses =
        isEmpty(overrideStatusSet) ? StatusUtils.nodeAllowedStartSet(status) : overrideStatusSet;
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).in(allowedStartStatuses));

    Update updateOps =
        ops.set(NodeExecutionKeys.status, status).set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    addFinalStatusOps(updateOps, status);

    // Only pay for the pre-update projection when the counter hook will actually consume it.
    // The kill switch must gate this DB read too, not only the downstream Redis write.
    //
    // Known drift source: this read is outside the transaction, so a concurrent writer may change
    // the row between this findOne and the findAndModify below. If the new current status still
    // matches allowedStartStatuses, findAndModify succeeds and the hook fires with a stale
    // preUpdateStatus, producing a spurious ±1. Moving the read inside performTransaction only
    // shrinks the window (Mongo transactions are disabled in this deployment — see
    // mongoConfig.transactionsEnabled). The bounded drift is reconciled by the daily rebuild job
    // landing in the follow-up PR.
    boolean counterHookEnabled = counterMutationHook.isEnabled();
    Status preUpdateStatus = null;
    if (counterHookEnabled) {
      Query preReadQuery = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
      preReadQuery.fields().include(NodeExecutionKeys.status).include(NodeExecutionKeys.mode);
      NodeExecution preUpdate = mongoTemplate.findOne(preReadQuery, NodeExecution.class);
      preUpdateStatus = preUpdate == null ? null : preUpdate.getStatus();
    }

    List<String> timeoutInstanceIds = getTimeoutInstanceIds(status, nodeExecutionId);
    NodeExecution updatedNodeExecution = transactionHelper.performTransaction(() -> {
      NodeExecution updated = mongoTemplate.findAndModify(query, updateOps, returnNewOptions, NodeExecution.class);
      if (updated == null) {
        log.warn("Cannot update execution status for the node {} with {}", nodeExecutionId, status);
      } else {
        planExpansionService.updateStatus(getAmbiance(updated), status);
        if (updated.getStepType().getStepCategory() == StepCategory.STAGE || StatusUtils.isFinalStatus(status)) {
          emitEvent(updated, OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);
        }
        orchestrationLogPublisher.onNodeStatusUpdate(NodeUpdateInfo.builder().nodeExecution(updated).build());
      }
      return updated;
    });
    if (updatedNodeExecution != null) {
      nodeExecutionInfoService.updateCalculatedStatusForParentNodes(updatedNodeExecution);
      nodeStatusUpdateSubject.fireInform(NodeStatusUpdateObserver::onNodeStatusUpdate,
          NodeUpdateInfo.builder().nodeExecution(updatedNodeExecution).timeoutInstanceIds(timeoutInstanceIds).build());
      if (counterHookEnabled) {
        // Post-commit: fire the step-concurrency counter mutation. Runs synchronously but swallows
        // all exceptions internally — orchestration never blocks on a Redis write.
        counterMutationHook.onStatusChange(AmbianceUtils.getAccountId(updatedNodeExecution.getAmbiance()),
            updatedNodeExecution.getMode(), preUpdateStatus, status);
      }
    }
    return updatedNodeExecution;
  }

  // Add additional updateOps based on nodeStatus to be updated
  // This is done to reduce write conflicts on same record, and send multiple updates at one go.
  private void addFinalStatusOps(Update updateOps, Status toBeUpdatedNodeStatus) {
    if (StatusUtils.isFinalStatus(toBeUpdatedNodeStatus)) {
      updateOps.set(NodeExecutionKeys.endTs, System.currentTimeMillis());
      if (toBeUpdatedNodeStatus != EXPIRED) {
        updateOps.set(NodeExecutionKeys.timeoutInstanceIds, new ArrayList<>());
      }
    }
  }

  @VisibleForTesting
  List<String> getTimeoutInstanceIds(Status toBeUpdatedNodeStatus, String currentNodeExecutionId) {
    List<String> timeoutInstanceIds = new LinkedList<>();
    if (StatusUtils.isFinalStatus(toBeUpdatedNodeStatus)) {
      Query getCurrentNodeQuery = query(where(NodeExecutionKeys.uuid).is(currentNodeExecutionId));
      getCurrentNodeQuery.fields().include(NodeExecutionKeys.uuid).include(NodeExecutionKeys.timeoutInstanceIds);
      NodeExecution oldNodeExecution =
          nodeExecutionReadHelper.fetchNodeExecutionsFromSecondaryTemplate(getCurrentNodeQuery);
      // nodeExecution could be null due to skipped nodes
      timeoutInstanceIds = oldNodeExecution == null ? new LinkedList<>() : oldNodeExecution.getTimeoutInstanceIds();
    }
    return timeoutInstanceIds;
  }

  @Override
  public long markLeavesDiscontinuing(List<String> leafInstanceIds) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.status, DISCONTINUING);
    ops.set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());

    // Pre-query rows currently OUT of the slot-occupying set that will actually transition INTO
    // the slot set on this update (DISCONTINUING is in-set), grouped by account. Restricted to
    // finalizableStatuses() minus the slot set itself — this excludes leaf ids already terminal
    // (SUCCEEDED, FAILED, etc.), since their run already finished and counting them as +1 would
    // be wrong, but includes APPROVAL_WAITING (out-of-slot, since APPROVAL is a leaf mode).
    EnumSet<Status> outOfSlotFinalizable = EnumSet.copyOf(StatusUtils.finalizableStatuses());
    outOfSlotFinalizable.removeAll(StatusUtils.ACTIVE_STATUSES_OCCUPYING_STEP_CONCURRENCY_SLOT);
    Query preStatusQuery = query(where(NodeExecutionKeys.uuid).in(leafInstanceIds))
                               .addCriteria(where(NodeExecutionKeys.status).in(outOfSlotFinalizable));
    preStatusQuery.fields().include(NodeExecutionKeys.status).include(NodeExecutionKeys.ambiance);
    List<NodeExecution> preRows = mongoTemplate.find(preStatusQuery, NodeExecution.class);
    java.util.Map<String, Long> outOfSlotByAccount = new java.util.HashMap<>();
    for (NodeExecution row : preRows) {
      String rowAccountId = row.getAmbiance() == null ? null : AmbianceUtils.getAccountId(row.getAmbiance());
      if (rowAccountId == null || rowAccountId.isEmpty()) {
        continue;
      }
      outOfSlotByAccount.merge(rowAccountId, 1L, Long::sum);
    }

    // Use Id index
    Query query = query(where(NodeExecutionKeys.uuid).in(leafInstanceIds));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.warn("No NodeExecutions could be marked as DISCONTINUING for given nodeExecutionIds");
      return -1;
    }

    for (java.util.Map.Entry<String, Long> entry : outOfSlotByAccount.entrySet()) {
      try {
        counterMutationHook.onBulkEntry(entry.getKey(), entry.getValue());
      } catch (Exception ex) {
        log.warn("[STEP_CONCURRENCY] markLeavesDiscontinuing: onBulkEntry failed account={} count={}", entry.getKey(),
            entry.getValue(), ex);
      }
    }
    return updateResult.getModifiedCount();
  }

  @Override
  public long markAllLeavesAndQueuedNodesDiscontinuing(String planExecutionId, EnumSet<Status> statuses) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.status, DISCONTINUING);
    ops.set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    Criteria leafNodeCriteria = where(NodeExecutionKeys.mode)
                                    .in(ExecutionModeUtils.leafModes())
                                    .and(NodeExecutionKeys.status)
                                    .in(statuses)
                                    .and(NodeExecutionKeys.oldRetry)
                                    .is(false);
    Criteria queuedNodeCriteria = where(NodeExecutionKeys.status).in(StatusUtils.getQueuedNodesWithInputWaiting());
    // Pre-count leaf rows currently OUT of the slot set that will transition INTO the slot set
    // via DISCONTINUING. Derived from finalizableStatuses() minus the live slot set so it stays
    // correct as the slot set changes — e.g. APPROVAL_WAITING (APPROVAL is a leaf mode) is
    // out-of-slot and must be counted here, not just QUEUED / QUEUED_STEP_LIMIT_REACHED.
    EnumSet<Status> outOfSlotFinalizable = EnumSet.copyOf(StatusUtils.finalizableStatuses());
    outOfSlotFinalizable.removeAll(StatusUtils.ACTIVE_STATUSES_OCCUPYING_STEP_CONCURRENCY_SLOT);
    Query counterEntryQuery = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                                  .addCriteria(where(NodeExecutionKeys.mode).in(ExecutionModeUtils.leafModes()))
                                  .addCriteria(where(NodeExecutionKeys.status).in(outOfSlotFinalizable));
    long outOfSlotLeafRows = mongoTemplate.count(counterEntryQuery, NodeExecution.class);
    // Uses - planExecutionId_status_idx
    Query query = query(
        where(NodeExecutionKeys.planExecutionId).is(planExecutionId).orOperator(leafNodeCriteria, queuedNodeCriteria));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.warn("No NodeExecutions could be marked as DISCONTINUING -  planExecutionId: {}", planExecutionId);
      return -1;
    }
    if (outOfSlotLeafRows > 0) {
      fireBulkEntryForPlan(planExecutionId, outOfSlotLeafRows);
    }
    return updateResult.getModifiedCount();
  }

  /**
   * Resolves the plan's accountId once and fires a bulk counter +N for the leaf rows that
   * transitioned OUT-of-slot -> IN-slot. See {@link #fireBulkExitForPlan} for the symmetric
   * decrement path.
   */
  private void fireBulkEntryForPlan(String planExecutionId, long count) {
    try {
      Optional<PlanExecution> planExecutionOptional =
          planExecutionService.getWithFieldsIncludedOptional(planExecutionId, Set.of(PlanExecutionKeys.ambiance));
      if (planExecutionOptional.isEmpty() || planExecutionOptional.get().getAmbiance() == null) {
        log.warn("[STEP_CONCURRENCY] bulk-entry: could not resolve accountId for planExecutionId={}", planExecutionId);
        return;
      }
      counterMutationHook.onBulkEntry(AmbianceUtils.getAccountId(planExecutionOptional.get().getAmbiance()), count);
    } catch (Exception ex) {
      log.warn("[STEP_CONCURRENCY] bulk-entry failed for planExecutionId={}", planExecutionId, ex);
    }
  }

  @Override
  public long markAllFinalizableNodesDiscontinuing(String planExecutionId) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.status, DISCONTINUING);
    ops.set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    // Pre-count leaf rows currently OUT of the slot set that will transition INTO the slot set via
    // DISCONTINUING. Derived from finalizableStatuses() minus the live slot set — e.g.
    // APPROVAL_WAITING (APPROVAL is a leaf mode) is out-of-slot and must be counted here, not just
    // QUEUED / QUEUED_STEP_LIMIT_REACHED, otherwise the later DISCONTINUING -> ABORTED single-row
    // -1 fires against a counter that never received the matching +1. Uses -
    // planExecutionId_mode_status_oldRetry_idx.
    EnumSet<Status> outOfSlotFinalizable = EnumSet.copyOf(StatusUtils.finalizableStatuses());
    outOfSlotFinalizable.removeAll(StatusUtils.ACTIVE_STATUSES_OCCUPYING_STEP_CONCURRENCY_SLOT);
    Query counterEntryQuery = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                                  .addCriteria(where(NodeExecutionKeys.mode).in(ExecutionModeUtils.leafModes()))
                                  .addCriteria(where(NodeExecutionKeys.status).in(outOfSlotFinalizable));
    long outOfSlotLeafRows = mongoTemplate.count(counterEntryQuery, NodeExecution.class);
    // Uses - planExecutionId_status_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.status)
                            .in(StatusUtils.finalizableStatuses()));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.warn("No NodeExecutions could be marked as DISCONTINUING -  planExecutionId: {}", planExecutionId);
      return -1;
    }
    if (outOfSlotLeafRows > 0) {
      fireBulkEntryForPlan(planExecutionId, outOfSlotLeafRows);
    }
    return updateResult.getModifiedCount();
  }

  /**
   * Update the old execution -> set oldRetry flag set to true
   *
   * @param nodeExecutionId Id of Failed Node Execution
   */
  @Override
  public boolean markRetried(String nodeExecutionId) {
    Update ops = new Update().set(NodeExecutionKeys.oldRetry, Boolean.TRUE);
    ops.set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).is(nodeExecutionId));
    NodeExecution nodeExecution = mongoTemplate.findAndModify(query, ops, NodeExecution.class);
    if (nodeExecution == null) {
      log.error("Failed to mark node as retry");
      return false;
    }
    orchestrationLogPublisher.onNodeUpdate(NodeUpdateInfo.builder().nodeExecution(nodeExecution).build());
    return true;
  }

  @Override
  public boolean markCurrentNodeExecutionAndChildrenRetried(String nodeExecutionId, String planExecutionId) {
    Update ops = new Update().set(NodeExecutionKeys.oldRetry, Boolean.TRUE);
    ops.set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    List<String> allNestedChildrenNodeUUIds =
        fetchAllChildrenNodeIdsRecursively(planExecutionId, Collections.singletonList(nodeExecutionId));
    allNestedChildrenNodeUUIds.add(nodeExecutionId);
    if (allNestedChildrenNodeUUIds.size() > 100) {
      log.warn("More than 100 nodeExecution's oldRetry is updated.");
    }
    // Uses - id index
    Query query = query(where(NodeExecutionKeys.uuid).in(allNestedChildrenNodeUUIds));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.error("Failed to mark node as retry");
      return false;
    }
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build();
    orchestrationLogPublisher.onNodeUpdate(
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(nodeExecutionId)
                               .executionContext(AmbianceUtils.getExecutionContextFromAmbiance(ambiance))
                               .ambiance(ambiance)
                               .build())
            .build());
    return true;
  }

  List<String> fetchAllChildrenNodeIdsRecursively(String planExecutionId, List<String> parentIds) {
    return fetchAllChildrenNodeIdsRecursively(planExecutionId, parentIds, MAX_DEPTH);
  }

  /*
  Fetches all children NodeExecution UUID's recursively.
   */
  List<String> fetchAllChildrenNodeIdsRecursively(String planExecutionId, List<String> parentIds, int depth) {
    if (depth <= 0) {
      throw new InvalidRequestException(String.format("Exceeded Max Depth level [%s] for the StepGroup.", MAX_DEPTH));
    }
    if (EmptyPredicate.isEmpty(parentIds)) {
      return new ArrayList<>();
    }
    List<String> recursiveChildrenNodeExecutionUUIDs = new LinkedList<>();
    try (Stream<NodeExecution> stream = fetchChildrenNodeExecutionsIteratorWithProjection(
             planExecutionId, parentIds, Direction.ASC, NodeProjectionUtils.fieldsForFetchingChildren)) {
      Iterator<NodeExecution> iterator = stream.iterator();
      while (iterator.hasNext()) {
        recursiveChildrenNodeExecutionUUIDs.add(iterator.next().getUuid());
      }
    }
    if (EmptyPredicate.isEmpty(recursiveChildrenNodeExecutionUUIDs)) {
      return new ArrayList<>();
    }
    List<String> childNodeExecutions =
        fetchAllChildrenNodeIdsRecursively(planExecutionId, recursiveChildrenNodeExecutionUUIDs, depth - 1);
    recursiveChildrenNodeExecutionUUIDs.addAll(childNodeExecutions);
    return recursiveChildrenNodeExecutionUUIDs;
  }

  @Override
  public void deleteAllNodeExecutionAndMetadata(Set<String> planExecutionIds) {
    // Fetches all nodeExecutions from analytics for given planExecutionIds
    List<NodeExecution> batchNodeExecutionList = new LinkedList<>();
    Set<String> nodeExecutionsIdsToDelete = new HashSet<>();
    try (Stream<NodeExecution> stream =
             fetchNodeExecutionsFromAnalytics(planExecutionIds, NodeProjectionUtils.fieldsForNodeExecutionDelete)) {
      Iterator<NodeExecution> iterator = stream.iterator();
      while (iterator.hasNext()) {
        NodeExecution next = iterator.next();
        nodeExecutionsIdsToDelete.add(next.getUuid());
        batchNodeExecutionList.add(next);
        if (batchNodeExecutionList.size() >= MAX_BATCH_SIZE) {
          // delete node Executions in batches of 1000
          deleteNodeExecutionsMetadataInternal(batchNodeExecutionList);
          deleteNodeExecutionsInternal(nodeExecutionsIdsToDelete);
          batchNodeExecutionList.clear();
          nodeExecutionsIdsToDelete.clear();
        }
      }
    }
    // delete the remaining node Executions
    if (EmptyPredicate.isNotEmpty(batchNodeExecutionList)) {
      deleteNodeExecutionsMetadataInternal(batchNodeExecutionList);
    }
    if (isNotEmpty(nodeExecutionsIdsToDelete)) {
      deleteNodeExecutionsInternal(nodeExecutionsIdsToDelete);
    }
  }

  /**
   * Deletes all nodeExecutions metadata
   *
   * @param nodeExecutionsToDelete
   */
  private void deleteNodeExecutionsMetadataInternal(List<NodeExecution> nodeExecutionsToDelete) {
    // Delete nodeExecutionMetadata example - WaitInstances, resourceRestraintInstances, timeoutInstanceIds, etc
    nodeDeleteObserverSubject.fireInform(NodeExecutionDeleteObserver::onNodesDelete, nodeExecutionsToDelete);
  }

  /**
   * Deletes all nodeExecutions for given ids
   * This method assumes the nodeExecutions will be in batch thus caller needs to handle it
   *
   * @param batchNodeExecutionIds
   */
  private void deleteNodeExecutionsInternal(Set<String> batchNodeExecutionIds) {
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // Uses - id index
      Query query = query(where(NodeExecutionKeys.id).in(batchNodeExecutionIds));
      mongoTemplate.remove(query, NodeExecution.class);
      return true;
    });
  }

  @Override
  public void updateTTLForNodeExecution(String planExecutionId, Date ttlExpiryDate) {
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // Uses - planExecutionId_nodeId_idx index
      Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId));
      Update ops = new Update();
      ops.set(NodeExecutionKeys.validUntil, ttlExpiryDate);
      mongoTemplate.updateMulti(query, ops, NodeExecution.class);
      return true;
    });
  }

  /**
   * Update Nodes for which the previousId was failed node execution and replace it with the
   * note execution which is being retried
   *
   * @param nodeExecutionId    Old nodeExecutionId
   * @param newNodeExecutionId Id of new retry node execution
   */
  @Override
  public boolean updateRelationShipsForRetryNode(String nodeExecutionId, String newNodeExecutionId) {
    Update ops = new Update().set(NodeExecutionKeys.previousId, newNodeExecutionId);
    ops.set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    // Uses - previous_id_idx
    Query query = query(where(NodeExecutionKeys.previousId).is(nodeExecutionId));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (updateResult.wasAcknowledged()) {
      log.warn("No previous nodeExecutions could be updated for this nodeExecutionId: {}", nodeExecutionId);
      return false;
    }
    return true;
  }

  @Override
  public boolean errorOutActiveNodes(String planExecutionId) {
    Update ops = new Update();
    ops.set(NodeExecutionKeys.status, ERRORED);
    ops.set(NodeExecutionKeys.endTs, System.currentTimeMillis());
    ops.set(NodeExecutionKeys.lastUpdatedAt, System.currentTimeMillis());
    // Pre-count leaf rows currently in the slot-occupying set — these will need a bulk counter
    // decrement after the update. The updateMulti still moves ALL active rows to ERRORED (non-
    // leaf cleanup unchanged), we just need to know how many of them were leaf-and-in-slot so
    // the counter is kept accurate.
    Query leafSlotCountQuery =
        query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
            .addCriteria(where(NodeExecutionKeys.mode).in(ExecutionModeUtils.leafModes()))
            .addCriteria(
                where(NodeExecutionKeys.status).in(StatusUtils.ACTIVE_STATUSES_OCCUPYING_STEP_CONCURRENCY_SLOT));
    long leafSlotOccupants = mongoTemplate.count(leafSlotCountQuery, NodeExecution.class);
    // Uses - planExecutionId_status_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).in(StatusUtils.activeStatuses()));
    UpdateResult updateResult = mongoTemplate.updateMulti(query, ops, NodeExecution.class);
    if (!updateResult.wasAcknowledged()) {
      log.warn("No NodeExecutions could be marked as ERRORED -  planExecutionId: {}", planExecutionId);
      return false;
    }
    if (leafSlotOccupants > 0) {
      fireBulkExitForPlan(planExecutionId, leafSlotOccupants);
    }
    return true;
  }

  /**
   * Resolves the plan's accountId once and fires a bulk counter -N. All leaves in a plan share an
   * accountId, so a single hook call covers the whole update. Best-effort — the daily rebuild
   * reconciles if the accountId lookup fails.
   */
  private void fireBulkExitForPlan(String planExecutionId, long count) {
    try {
      Optional<PlanExecution> planExecutionOptional =
          planExecutionService.getWithFieldsIncludedOptional(planExecutionId, Set.of(PlanExecutionKeys.ambiance));
      if (planExecutionOptional.isEmpty() || planExecutionOptional.get().getAmbiance() == null) {
        log.warn("[STEP_CONCURRENCY] bulk-exit: could not resolve accountId for planExecutionId={}", planExecutionId);
        return;
      }
      counterMutationHook.onBulkExit(AmbianceUtils.getAccountId(planExecutionOptional.get().getAmbiance()), count);
    } catch (Exception ex) {
      log.warn("[STEP_CONCURRENCY] bulk-exit failed for planExecutionId={}", planExecutionId, ex);
    }
  }

  @VisibleForTesting
  Stream<NodeExecution> fetchNodeExecutionsFromAnalytics(String planExecutionId, @NotNull Set<String> fieldsToInclude) {
    // Uses - id_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutionsFromAnalytics(query);
  }

  @VisibleForTesting
  Stream<NodeExecution> fetchNodeExecutionsFromAnalytics(
      Set<String> planExecutionIds, @NotNull Set<String> fieldsToInclude) {
    // Uses - id_idx
    Query query = query(where(NodeExecutionKeys.planExecutionId).in(planExecutionIds));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutionsFromAnalytics(query);
  }

  @VisibleForTesting
  void emitEvent(NodeExecution nodeExecution, OrchestrationEventType orchestrationEventType) {
    emitEvent(nodeExecution, orchestrationEventType, false);
  }

  @Override
  public void emitEvent(NodeExecution nodeExecution, OrchestrationEventType orchestrationEventType, boolean bakfill) {
    if (nodeExecution == null) {
      return;
    }
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().build();
    if (nodeExecution.getPlanExecutionId() != null) {
      PlanExecutionMetadata metadata =
          planExecutionMetadataService
              .findByPlanExecutionId(
                  NodeExecutionContextUtils.getAccountId(nodeExecution), nodeExecution.getPlanExecutionId())
              .orElseThrow(()
                               -> new InvalidRequestException(
                                   "No Metadata present for planExecution :" + nodeExecution.getPlanExecutionId()));
      boolean readSwitchEnabled = pmsFeatureFlagService.isEnabled(
          metadata.getAccountIdentifier(), FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
      PlanExecution planExecution = null;
      if (readSwitchEnabled) {
        Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
            nodeExecution.getPlanExecutionId(), Set.of(PlanExecutionKeys.triggerPayload));
        if (planExecutionOptional.isPresent()) {
          planExecution = planExecutionOptional.get();
        }
      }
      TriggerPayload retrievedTriggerPayload =
          PlanExecutionMigrationHelper.readTriggerPayloadWithFallBackOnMetadata(metadata, planExecution);
      if (Objects.nonNull(retrievedTriggerPayload)) {
        triggerPayload = retrievedTriggerPayload;
      }
    }

    Builder eventBuilder = OrchestrationEvent.newBuilder()
                               .setAmbiance(getAmbiance(nodeExecution))
                               .setStatus(nodeExecution.getStatus())
                               .setEventType(orchestrationEventType)
                               .setServiceName(nodeExecution.getModule())
                               .setTriggerPayload(triggerPayload)
                               .setEndTs(nodeExecution.getEndTs() == null ? 0 : nodeExecution.getEndTs());

    if (checkPresenceOfResolvedParametersForNonIdentityNodes(nodeExecution)) {
      eventBuilder.setStepParameters(nodeExecution.getResolvedStepParametersBytes());
    }

    updateEventIfCausedByAutoAbortThroughTrigger(nodeExecution, orchestrationEventType, eventBuilder);
    if (bakfill) {
      eventEmitter.replayEvent(eventBuilder.build());
    } else {
      eventEmitter.emitEvent(eventBuilder.build());
    }
  }

  /**
   * This may seem very specialized logic for a particular case, but we want to keep events lighter as much as possible.
   * So putting this data only in case needed, as there will be large no of NODE_EXECUTION_STATUS_UPDATE events.
   * <p>
   * This is special handling added for CI usecase, to skip update git prs in case of pipeline auto abort from trigger.
   * NOTE: some refactoring is due, with which CI will start listenening to Stage level events only, then this wont be
   * needed here. But, that may take some time.
   */
  @VisibleForTesting
  void updateEventIfCausedByAutoAbortThroughTrigger(
      NodeExecution nodeExecution, OrchestrationEventType orchestrationEventType, Builder eventBuilder) {
    if (orchestrationEventType == OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE) {
      Level level = nodeExecution.getCurrentLevel();
      if (level != null && level.getStepType().getStepCategory() == StepCategory.STAGE
          && nodeExecution.getStatus() == ABORTED) {
        List<NodeExecution> allChildrenWithStatusInAborted =
            findAllChildrenWithStatusInAndWithoutOldRetries(nodeExecution.getPlanExecutionId(), nodeExecution.getUuid(),
                EnumSet.of(ABORTED), true, Sets.newHashSet(NodeExecutionKeys.interruptHistories), false);
        if (isEmpty(allChildrenWithStatusInAborted)) {
          return;
        }

        List<NodeExecution> nodeExecutionsAbortedThroughTrigger =
            allChildrenWithStatusInAborted.stream().filter(this::isAbortedThroughTrigger).collect(Collectors.toList());
        if (EmptyPredicate.isNotEmpty(nodeExecutionsAbortedThroughTrigger)) {
          eventBuilder.addTags(AUTO_ABORT_PIPELINE_THROUGH_TRIGGER);
        }
      }
    }
  }

  private boolean isAbortedThroughTrigger(NodeExecution nodeExecution) {
    return nodeExecution.getInterruptHistories().stream().anyMatch(this::isIssuedByTrigger);
  }

  private boolean isIssuedByTrigger(InterruptEffect interruptEffect) {
    InterruptConfig interruptConfig = interruptEffect.getInterruptConfig();
    return interruptConfig.hasIssuedBy() && interruptConfig.getIssuedBy().hasTriggerIssuer()
        && interruptConfig.getIssuedBy().getTriggerIssuer().getAbortPrevConcurrentExecution();
  }

  @Override
  public List<RetryStageInfo> getStageDetailFromPlanExecutionId(String planExecutionId, String pipelineVersion) {
    List<NodeExecution> nodeExecutions = fetchStageExecutions(planExecutionId);
    return fetchStageDetailFromNodeExecution(getFilteredStageNodeExecution(nodeExecutions, pipelineVersion));
  }

  private List<NodeExecution> getFilteredStageNodeExecution(
      List<NodeExecution> nodeExecutions, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      /* For V1 pipelines, exclude stages that are nested within stage groups from the stage execution list.
       This ensures we only show top-level stages and stage groups, not individual stages within groups.
       Retry operations are performed at the stage group level, not on individual nested stages.*/
      return nodeExecutions.stream()
          .filter(nodeExecution -> !NodeExecutionContextUtils.isCurrentLevelStageInGroup(nodeExecution))
          .toList();
    } else {
      return nodeExecutions;
    }
  }

  @Override
  public List<RetryStageInfo> getStageDetailFromPlanExecutionIdV2(String planExecutionId) {
    return fetchStageDetailFromNodeExecution(fetchStageExecutionsV2(planExecutionId));
  }

  @Override
  public List<NodeExecution> fetchStageExecutions(String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.status).ne(Status.SKIPPED))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).in(StepCategory.STAGE, StepCategory.STRATEGY));
    query.with(by(NodeExecutionKeys.createdAt));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public List<NodeExecution> fetchStageExecutionsV2(String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).in(StepCategory.STAGE, StepCategory.STRATEGY));
    query.with(by(NodeExecutionKeys.createdAt));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public List<NodeExecution> fetchStageExecutionsWithProjection(
      String planExecutionId, Set<String> fieldsToBeIncluded) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).in(StepCategory.STAGE, StepCategory.STRATEGY));
    for (String field : fieldsToBeIncluded) {
      query.fields().include(field);
    }
    query.with(by(NodeExecutionKeys.createdAt));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public List<NodeExecution> findCurrentStageAttempts(Set<String> planExecutionIds, Set<String> stageIdentifiers) {
    if (EmptyPredicate.isEmpty(planExecutionIds) || EmptyPredicate.isEmpty(stageIdentifiers)) {
      return new ArrayList<>();
    }
    // Uses - planExecutionId_stepCategory_identifier_idx. M6 tiebreak (largest createdAt wins
    // when retry insert + oldRetry flip race) is enforced at the call site.
    Query query = query(where(NodeExecutionKeys.planExecutionId).in(planExecutionIds))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).is(StepCategory.STAGE))
                      .addCriteria(where(NodeExecutionKeys.identifier).in(stageIdentifiers))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    query.fields()
        .include(NodeExecutionKeys.uuid)
        .include(NodeExecutionKeys.identifier)
        .include(NodeExecutionKeys.createdAt)
        .include(NodeExecutionKeys.executionContext)
        .include(NodeExecutionKeys.ambiance);
    return nodeExecutionReadHelper.findNodeExecutionsFromSecondary(query);
  }

  @Override
  public List<NodeExecution> fetchStageExecutionsWithEndTsAndStatusProjection(String planExecutionId) {
    Query query =
        query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
            .addCriteria(
                where(NodeExecutionKeys.stepCategory).in(Arrays.asList(StepCategory.STAGE, StepCategory.STRATEGY)));
    query.fields()
        .include(NodeExecutionKeys.uuid)
        .include(NodeExecutionKeys.status)
        .include(NodeExecutionKeys.startTs)
        .include(NodeExecutionKeys.endTs)
        .include(NodeExecutionKeys.createdAt)
        .include(NodeExecutionKeys.mode)
        .include(NodeExecutionKeys.stepType)
        .include(NodeExecutionKeys.ambiance)
        .include(NodeExecutionKeys.executionContext)
        .include(NodeExecutionKeys.nodeId)
        .include(NodeExecutionKeys.parentId)
        .include(NodeExecutionKeys.oldRetry)
        .include(NodeExecutionKeys.resolvedParams)
        .include(NodeExecutionKeys.failureInfo)
        .include(NodeExecutionKeys.originalNodeExecutionId)
        .include(NodeExecutionKeys.executableResponses);

    query.with(by(NodeExecutionKeys.createdAt));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  // TODO optimize this to remove n+1 queries
  private List<RetryStageInfo> fetchStageDetailFromNodeExecution(List<NodeExecution> nodeExecutionList) {
    List<RetryStageInfo> stageDetails = new ArrayList<>();

    if (nodeExecutionList.size() == 0) {
      throw new InvalidRequestException("No stage to retry");
    }

    List<NodeExecution> stageNodeExecutions = new ArrayList<>();
    Set<String> strategyNodeExecutionIds = new HashSet<>();
    for (NodeExecution nodeExecution : nodeExecutionList) {
      if (nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY) {
        if (AmbianceUtils.isCurrentStrategyLevelAtStage(nodeExecution.getLevels())) {
          strategyNodeExecutionIds.add(nodeExecution.getUuid());

          String nextId = nodeExecution.getNextId();
          String parentId = nodeExecution.getParentId();
          RetryStageInfo stageDetail =
              RetryStageInfo.builder()
                  .name(nodeExecution.getName())
                  .identifier(nodeExecution.getIdentifier())
                  .parentId(parentId)
                  .createdAt(nodeExecution.getCreatedAt())
                  .status(ExecutionStatus.getExecutionStatus(nodeExecution.getStatus()))
                  .nextId(nextId != null
                          ? nextId
                          : getWithFieldsIncluded(parentId, Sets.newHashSet(NodeExecutionKeys.nextId)).getNextId())
                  .isStageGroupId(nodeExecution.getStepType().getType().equals(NGCommonUtilPlanCreationConstants.GROUP))
                  .build();
          stageDetails.add(stageDetail);
        }
      } else {
        stageNodeExecutions.add(nodeExecution);
      }
    }

    for (NodeExecution nodeExecution : stageNodeExecutions) {
      String nextId = nodeExecution.getNextId();
      String parentId = nodeExecution.getParentId();
      if (strategyNodeExecutionIds.contains(parentId)) {
        continue;
      }
      RetryStageInfo stageDetail =
          RetryStageInfo.builder()
              .name(nodeExecution.getName())
              .identifier(nodeExecution.getIdentifier())
              .parentId(parentId)
              .createdAt(nodeExecution.getCreatedAt())
              .status(ExecutionStatus.getExecutionStatus(nodeExecution.getStatus()))
              .nextId(nextId != null
                      ? nextId
                      : getWithFieldsIncluded(parentId, Sets.newHashSet(NodeExecutionKeys.nextId)).getNextId())
              .isStageGroupId(nodeExecution.getStepType().getType().equals(NGCommonUtilPlanCreationConstants.GROUP))
              .build();
      stageDetails.add(stageDetail);
    }
    return stageDetails;
  }

  @Override
  public List<String> fetchStageFqnFromStageIdentifiers(String planExecutionId, List<String> stageIdentifiers) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .addCriteria(where(NodeExecutionKeys.stepCategory).in(StepCategory.STAGE, StepCategory.STRATEGY))
                      .addCriteria(where(NodeExecutionKeys.identifier).in(stageIdentifiers));

    List<NodeExecution> nodeExecutions = mongoTemplate.find(query, NodeExecution.class);
    // Scenario: Retry from failed last stage
    // If failed stage has a step with strategy and has same identifier as previous stages then above query is returning
    // failed stage step node execution AS result failed stage fqn was getting filtered and while plan creation IDENTITY
    // NODE was getting creating for failed stage instead of PLAN NODE

    // filtering the stage nodes both normal and with strategy then fetching stageFqn for filtered Nodes
    return nodeExecutions.stream()
        .filter(nodeExecution
            -> (nodeExecution.getStepType().getStepCategory().equals(StepCategory.STAGE)
                || AmbianceUtils.isCurrentStrategyLevelAtStage(nodeExecution.getLevels())))
        .map(NodeExecution::getStageFqn)
        .collect(Collectors.toList());
  }

  @Override
  public List<NodeExecution> fetchStrategyNodeExecutions(String planExecutionId, List<String> stageFQNs) {
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.stepCategory)
                            .is(StepCategory.STRATEGY)
                            .and(NodeExecutionKeys.stageFqn)
                            .in(stageFQNs);

    Query query = new Query().addCriteria(criteria);

    return mongoTemplate.find(query, NodeExecution.class);
  }

  @Override
  public Map<String, Node> mapNodeExecutionIdWithPlanNodeForGivenStageFQN(
      String planExecutionId, List<String> stageFQNs) {
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.stageFqn)
                            .in(stageFQNs);

    Query query = new Query().addCriteria(criteria);

    List<NodeExecution> nodeExecutions = mongoTemplate.find(query, NodeExecution.class);

    Map<String, NodeExecution> nodeExecutionMap = getUniqueNodeExecutionForNodes(nodeExecutions);
    // fetching stageFqn of stage Nodes
    Map<String, Node> nodeExecutionIdToPlanNode = new HashMap<>();

    Set<String> nodeIds = nodeExecutionMap.values().stream().map(NodeExecution::getNodeId).collect(Collectors.toSet());
    // Here we have assumed that plan id of all node executions will be same as this was the assumption till now as well
    String planId = !isEmpty(nodeExecutions) ? nodeExecutions.get(0).getPlanId() : null;
    // TODO Remove the list query to fetch list of nodes
    Set<Node> nodes = planService.fetchAllNodes(planId, nodeIds);
    Map<String, Node> nodeMap = nodes.stream().collect(Collectors.toMap(Node::getUuid, node -> node));

    nodeExecutionMap.forEach(
        (uuid, nodeExecution)
            -> nodeExecutionIdToPlanNode.put(nodeExecution.getUuid(), nodeMap.get(nodeExecution.getNodeId())));
    return nodeExecutionIdToPlanNode;
  }

  // We can have multiple nodeExecution corresponding to same node during the retry-failure-strategy. So this method
  // makes sure that only the latest nodeExecution is used for retry when there are multiple nodeExecutions due to
  // retry-failure-strategy. There will be only one such node due to retry-failure-strategy.

  // In case of strategy, returning any nodeExecution for steps is fine. Because during the execution, the children
  // nodeExecutions are decided by the original nodeExecution of strategy node. And there will be only one strategy
  // nodeExecution and that too with oldRetry false.
  private Map<String, NodeExecution> getUniqueNodeExecutionForNodes(List<NodeExecution> nodeExecutions) {
    Map<String, NodeExecution> nodeExecutionMap = new HashMap<>();
    for (NodeExecution nodeExecution : nodeExecutions) {
      if (!nodeExecutionMap.containsKey(nodeExecution.getNodeId()) && !nodeExecution.getOldRetry()) {
        nodeExecutionMap.put(nodeExecution.getNodeId(), nodeExecution);
      }
    }
    return nodeExecutionMap;
  }

  private void validateNodeExecutionProjection(Set<String> fieldsToInclude) {
    if (EmptyPredicate.isEmpty(fieldsToInclude)) {
      throw new InvalidRequestException("Projection fields cannot be empty in NodeExecution query.");
    }
  }

  @Override
  public Stream<NodeExecution> fetchNodeExecutionsForGivenStageFQNs(
      String planExecutionId, List<String> stageFQNs, Collection<String> requiredFields) {
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.stageFqn)
                            .in(stageFQNs)
                            .and(NodeExecutionKeys.oldRetry)
                            .is(false);

    Query query = query(criteria);
    if (EmptyPredicate.isNotEmpty(requiredFields)) {
      for (String requiredField : requiredFields) {
        query.fields().include(requiredField);
      }
    }

    return nodeExecutionReadHelper.fetchNodeExecutions(query);
  }

  @Override
  public NodeExecution fetchNodeExecutionForPlanNodeAndRetriedId(
      String planExecutionId, String planNodeId, boolean oldRetry, List<String> retriedId) {
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.nodeId)
                            .is(planNodeId)
                            .and(NodeExecutionKeys.oldRetry)
                            .is(oldRetry)
                            .and(NodeExecutionKeys.retryIds)
                            .in(retriedId);

    Query query = query(criteria);
    query.fields().include(NodeExecutionKeys.id);
    return nodeExecutionReadHelper.fetchNodeExecutionsFromSecondaryTemplate(query);
  }

  @Override
  public Stream<NodeExecution> fetchAllLeavesUsingPlanExecutionId(
      String planExecutionId, Set<String> fieldsToBeIncluded) {
    // Uses - planExecutionId_mode_status_oldRetry_idx
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.mode)
                            .in(ExecutionModeUtils.leafModes());
    Query query = query(criteria);
    for (String field : fieldsToBeIncluded) {
      query.fields().include(field);
    }
    return nodeExecutionReadHelper.fetchNodeExecutionsFromAnalytics(query);
  }

  @Override
  public Stream<NodeExecution> fetchAllNodeExecutionsByPlanExecutionIdLastUpdatedAtGT(
      String planExecutionId, Long lastUpdatedAt) {
    // Uses - planExecutionId_lastUpdatedAt_createdAt_idx
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.lastUpdatedAt)
                            .gt(lastUpdatedAt);

    Query query = query(criteria).with(Sort.by(Direction.ASC, NodeExecutionKeys.createdAt));
    return nodeExecutionReadHelper.fetchNodeExecutionsWithoutValidation(query);
  }

  @Override
  public Stream<NodeExecution> fetchAllNodeExecutionsByPlanExecutionIdLastUpdatedAtGTFromSecondary(
      String planExecutionId, Long lastUpdatedAt) {
    // Uses - planExecutionId_lastUpdatedAt_createdAt_idx
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.lastUpdatedAt)
                            .gt(lastUpdatedAt);
    Query query = query(criteria).with(Sort.by(Direction.ASC, NodeExecutionKeys.createdAt));
    return nodeExecutionReadHelper.fetchNodeExecutionsWithoutValidationFromSecondary(query);
  }

  @Override
  public boolean checkIfUnprocessedNodeExecutionsForPlanExecutionId(String planExecutionId, Long lastUpdatedAt) {
    // Uses - planExecutionId_lastUpdatedAt_createdAt_idx
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.lastUpdatedAt)
                            .gt(lastUpdatedAt);

    Query query = query(criteria);
    return mongoTemplate.exists(query, NodeExecution.class);
  }

  @Override
  public ExecutionStatistics aggregateRunningNodeExecutionsCount() {
    return nodeExecutionReadHelper.aggregateRunningExecutionCount();
  }

  @Override
  public Ambiance getAmbiance(NodeExecution nodeExecution) {
    if (nodeExecution == null) {
      return null;
    }
    if (nodeExecution.getExecutionContext() != null) {
      try {
        ExecutionContext executionContext = nodeExecution.getExecutionContext();
        ExecutionMetadata executionMetadata =
            planExecutionService.getExecutionMetadataFromPlanExecution(nodeExecution.getPlanExecutionId());
        return AmbianceUtils.getAmbianceFromExecutionContextAndMetadata(executionContext, executionMetadata);
      } catch (Exception ex) {
        log.warn("[AMBIANCE_REMOVAL]: Exception occurred while getting execution metadata for nodeExecution with uuid "
                + "{}. Falling back to use ambiance from nodeExecution",
            nodeExecution.getUuid(), ex);
        return nodeExecution.getAmbiance();
      }
    } else {
      log.warn("[AMBIANCE_REMOVAL]: Execution context is null for nodeExecution with uuid "
              + "{}. Falling back to use ambiance from nodeExecution",
          nodeExecution.getUuid());
      return nodeExecution.getAmbiance();
    }
  }

  @Override
  public List<Ambiance> getAmbiances(List<NodeExecution> nodeExecutions) {
    if (EmptyPredicate.isEmpty(nodeExecutions)) {
      return Collections.emptyList();
    }

    String planExecutionId = nodeExecutions.get(0).getPlanExecutionId();

    ExecutionMetadata executionMetadata = planExecutionService.getExecutionMetadataFromPlanExecution(planExecutionId);

    List<Ambiance> ambianceList = new ArrayList<>();

    for (NodeExecution nodeExecution : nodeExecutions) {
      if (nodeExecution.getExecutionContext() != null) {
        try {
          ambianceList.add(AmbianceUtils.getAmbianceFromExecutionContextAndMetadata(
              nodeExecution.getExecutionContext(), executionMetadata));
        } catch (Exception ex) {
          log.warn(
              "[AMBIANCE_REMOVAL]: Exception occurred while getting execution metadata for nodeExecution with uuid "
                  + "{}. Falling back to use ambiance from nodeExecution",
              nodeExecution.getUuid(), ex);
          ambianceList.add(nodeExecution.getAmbiance());
        }
      } else {
        log.warn("[AMBIANCE_REMOVAL]: Execution context is null for nodeExecution with uuid "
                + "{}. Falling back to use ambiance from nodeExecution",
            nodeExecution.getUuid());
        ambianceList.add(nodeExecution.getAmbiance());
      }
    }
    return ambianceList;
  }

  @Override
  public void markNodesProcessing(List<String> nodeExecutionIds, boolean processing) {
    if (stuckNodeExecutionsMarkingConfig.enabled()) {
      var criteria = Criteria.where(NodeExecutionKeys.id).in(nodeExecutionIds);
      var update = new Update().set(NodeExecutionKeys.processingEvent, processing);
      if (processing) {
        Duration maxProcessingDuration =
            Duration.ofMinutes(stuckNodeExecutionsMarkingConfig.maxProcessingDurationMinutes());
        update.set(NodeExecutionKeys.nextIteration, Instant.now().plus(maxProcessingDuration).toEpochMilli())
            .set(NodeExecutionKeys.processingEventStartedAt, Instant.now().toEpochMilli());
      } else {
        update.unset(NodeExecutionKeys.processingEventStartedAt);
      }
      // Retry once on transient Mongo failures (e.g. socket drops during a failover/stepdown) so that a momentary
      // connectivity blip does not permanently lose the processing-mark and, in turn, the resume event. See PIPE-35791.
      Failsafe.with(MARK_PROCESSING_RETRY_POLICY).get(() -> {
        mongoTemplate.updateMulti(new Query(criteria), update, NodeExecution.class);
        return true;
      });
    }
  }

  @Override
  public List<String> fetchListOfApprovalInstanceIdsForPlanExecutionId(String planExecutionId) {
    Criteria criteria = Criteria.where(NodeExecutionKeys.planExecutionId)
                            .is(planExecutionId)
                            .and(NodeExecutionKeys.type)
                            .is(StepSpecTypeConstants.HARNESS_APPROVAL);

    Query query = query(criteria);

    query.fields().include(NodeExecutionKeys.executableResponses);

    List<String> approvalInstanceIds = new ArrayList<>();

    try (Stream<NodeExecution> stream =
             nodeExecutionReadHelper.fetchNodeExecutionsIteratorWithoutProjectionsFromSecondary(query)) {
      Iterator<NodeExecution> iterator = stream.iterator();
      while (iterator.hasNext()) {
        NodeExecution nodeExecution = iterator.next();

        if (nodeExecution != null && EmptyPredicate.isNotEmpty(nodeExecution.getExecutableResponses())
            && nodeExecution.getExecutableResponses().get(0) != null) {
          AsyncExecutableResponse asyncExecutableResponse = nodeExecution.getExecutableResponses().get(0).getAsync();
          if (asyncExecutableResponse != null && asyncExecutableResponse.getCallbackIdsCount() > 0) {
            approvalInstanceIds.add(asyncExecutableResponse.getCallbackIds(0));
          }
        }
      }
    }
    return approvalInstanceIds;
  }

  @Override
  public PmsStepParameters getResolvedStepInputs(
      List<String> stepInputsKeyExclude, PmsStepParameters resolvedParameters) {
    if (EmptyPredicate.isEmpty(stepInputsKeyExclude) || EmptyPredicate.isEmpty(resolvedParameters)) {
      return resolvedParameters;
    }

    PmsStepParameters clonedParameters = PmsStepParameters.parse(resolvedParameters);
    // Iterate through the list of keys to remove
    for (String key : stepInputsKeyExclude) {
      // Split the key into individual parts
      String[] keyParts = key.split("\\.");

      // Traverse the cloned map to reach the innermost map
      Map<String, Object> currentMap = clonedParameters;
      boolean removeKey = true;
      for (int i = 0; i < keyParts.length - 1; i++) {
        String part = keyParts[i];
        if (currentMap == null || !currentMap.containsKey(part)) {
          removeKey = false;
          break;
        }
        Object nextMap = currentMap.get(part);
        if (nextMap instanceof Map) {
          // Shallow copy the inner map only when necessary
          currentMap.put(part, PmsStepParameters.parse((Map<String, Object>) nextMap));
          currentMap = (Map<String, Object>) currentMap.get(part);
        }
      }

      // Remove the final key from the required map
      if (currentMap != null && removeKey) {
        currentMap.remove(keyParts[keyParts.length - 1]);
      }
    }

    return clonedParameters;
  }

  @Override
  public long getCountOfLeafStepsWithGivenStatuses(String planExecutionId, Set<Status> statuses) {
    Criteria leafNodeCriteria = where(NodeExecutionKeys.planExecutionId)
                                    .is(planExecutionId)
                                    .and(NodeExecutionKeys.mode)
                                    .in(ExecutionModeUtils.leafModes())
                                    .and(NodeExecutionKeys.status)
                                    .in(statuses);
    return mongoTemplate.count(new Query(leafNodeCriteria), NodeExecution.class);
  }

  @Override
  public NodeExecution updateUsingQuery(Query query, Update update) {
    return mongoTemplate.findAndModify(query, update, NodeExecution.class);
  }
}
