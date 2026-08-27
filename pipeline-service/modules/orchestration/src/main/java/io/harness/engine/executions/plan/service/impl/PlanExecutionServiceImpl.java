/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.engine.pms.execution.strategy.plan.PlanExecutionStrategy.ENFORCEMENT_CALLBACK_ID;
import static io.harness.pms.contracts.execution.Status.ERRORED;
import static io.harness.pms.contracts.execution.Status.QUEUED_EXECUTION_CONCURRENCY_REACHED;
import static io.harness.pms.contracts.execution.Status.QUEUED_PLAN_CREATION;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.execution.PipelineStageResponseData;
import io.harness.engine.executions.concurrency.counter.PlanConcurrencyCounterMutationHook;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.helpers.PipelineStageStatusHelper;
import io.harness.engine.interrupts.statusupdate.NodeStatusUpdateHandlerFactory;
import io.harness.engine.observers.NodeStatusUpdateHandler;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.PlanExecutionDeleteObserver;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.engine.pms.execution.manual.beans.ManualExecutionAction;
import io.harness.engine.pms.execution.manual.beans.ManualExecutionResponseData;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.ExecutionMetadataKeys;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PriorityType;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.mongo.helper.SecondaryMongoTemplateHolder;
import io.harness.monitoring.PlanExecutionCountWithAccountAndTriggerTypeResult;
import io.harness.monitoring.PlanExecutionCountWithAccountResult;
import io.harness.observer.Subject;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.FacilitatorExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.repositories.planexecution.PlanExecutionRepository;
import io.harness.springdata.PersistenceModule;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.StringNotifyResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Field;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PlanExecutionServiceImpl implements PlanExecutionService {
  @Inject private PlanExecutionRepository planExecutionRepository;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private SecondaryMongoTemplateHolder secondaryMongoTemplateHolder;
  @Inject private NodeStatusUpdateHandlerFactory nodeStatusUpdateHandlerFactory;
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private PersistentLocker persistentLocker;
  @Inject private PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private PipelineStageStatusHelper pipelineStageStatusHelper;
  @Inject private AccessControlClient accessControlClient;
  @Inject private PlanConcurrencyCounterMutationHook planConcurrencyCounterMutationHook;

  private static final String PLAN_EXECUTION_STATUS_UPDATE_LOCK = "PLAN_STATUS_UPDATE_LOCK_";

  @Getter private final Subject<PlanStatusUpdateObserver> planStatusUpdateSubject = new Subject<>();
  @Getter private final Subject<PlanExecutionDeleteObserver> planExecutionDeleteObserverSubject = new Subject<>();

  @Override
  public PlanExecution save(PlanExecution planExecution) {
    return planExecutionRepository.save(planExecution);
  }

  /**
   * Always use this method while updating statuses. This guarantees we a hopping from correct statuses.
   * As we don't have transactions it is possible that your execution state is manipulated by some other thread and
   * your transition is no longer valid.
   * <p>
   * Like your workflow is aborted but some other thread try to set it to running. Same logic applied to plan execution
   * status as well
   */
  @Override
  public PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops) {
    return updateStatusForceful(planExecutionId, status, ops, false);
  }

  @Override
  public PlanExecution updateStatusForceful(
      @NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops, boolean forced) {
    EnumSet<Status> allowedStartStatuses = StatusUtils.planAllowedStartSet(status);
    return updateStatusForceful(planExecutionId, status, ops, forced, allowedStartStatuses);
  }

  @Override
  public PlanExecution updateStatusForceful(@NonNull String planExecutionId, @NonNull Status status,
      Consumer<Update> ops, boolean forced, EnumSet<Status> allowedStartStatuses) {
    Query query = query(where(PlanExecutionKeys.uuid).is(planExecutionId));
    if (!forced) {
      query.addCriteria(where(PlanExecutionKeys.status).in(allowedStartStatuses));
    }
    Update updateOps = new Update()
                           .set(PlanExecutionKeys.status, status)
                           .set(PlanExecutionKeys.lastUpdatedAt, System.currentTimeMillis());

    boolean isFinalStatus = StatusUtils.isFinalStatus(status);
    if (isFinalStatus) {
      updateOps.set(PlanExecutionKeys.endTs, System.currentTimeMillis());
    }
    if (ops != null) {
      ops.accept(updateOps);
    }
    // Fetched here once so observers receive it via emitEvent, replacing the per-observer DB read
    // that PlanStatusEventEmitterHandler previously did inside onPlanStatusUpdate.
    Status previousStatus = null;
    try {
      previousStatus = getStatus(planExecutionId);
    } catch (EntityNotFoundException e) {
      log.debug("Plan execution not found when fetching previousStatus for id: {}", planExecutionId);
    }

    PlanExecution updated = planExecutionRepository.updatePlanExecution(query, updateOps, false);
    if (updated == null) {
      log.warn("Cannot update execution status for the PlanExecution {} with {}", planExecutionId, status);
    } else {
      emitEvent(updated, previousStatus);
      // Post-commit: keep the per-project/account concurrency counters in sync. Inert unless the
      // account is on the per-project FF and the mutation kill switch is on. Best-effort; never
      // throws back into the status-transition path.
      maybeMutatePlanConcurrencyCounter(updated, previousStatus, status);
    }
    if (isFinalStatus) {
      waitNotifyEngine.doneWith(
          String.format(ENFORCEMENT_CALLBACK_ID, planExecutionId), StringNotifyResponseData.builder().build());
      waitNotifyEngine.doneWith(planExecutionId, PipelineStageResponseData.builder().status(status).build());
    }
    return updated;
  }

  private void maybeMutatePlanConcurrencyCounter(PlanExecution updated, Status previousStatus, Status newStatus) {
    try {
      if (!planConcurrencyCounterMutationHook.isEnabled()) {
        return;
      }
      Map<String, String> setupAbstractions = updated.getSetupAbstractions();
      String accountId = setupAbstractions == null ? null : setupAbstractions.get(SetupAbstractionKeys.accountId);
      if (accountId == null
          || !pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_PER_PROJECT_CONCURRENCY_OVERRIDES.name())) {
        return;
      }
      planConcurrencyCounterMutationHook.onStatusChange(setupAbstractions, previousStatus, newStatus);
    } catch (Exception ex) {
      log.warn("[PLAN_CONCURRENCY] failed to mutate plan concurrency counter for {}", updated.getUuid(), ex);
    }
  }

  @Override
  public void handleManualExecution(String accountId, String orgId, String projectId, @NonNull String nodeExecutionId,
      @NonNull ManualExecutionAction manualExecutionAction, ScopeInfo scopeInfo) {
    NodeExecution nodeExecution = nodeExecutionService.getWithFieldsIncluded(nodeExecutionId,
        Sets.newHashSet(
            NodeExecutionKeys.executableResponses, NodeExecutionKeys.executionContext, NodeExecutionKeys.ambiance));

    String planExecutionId = nodeExecution.getPlanExecutionId();
    ExecutionMetadata executionMetadata = getExecutionMetadataFromPlanExecution(planExecutionId);
    if (executionMetadata == null) {
      throw new InvalidRequestException("Plan Execution metadata not found for id: " + planExecutionId);
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionMetadata.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_EXECUTE);

    if (isEmpty(nodeExecution.getExecutableResponses())
        || !nodeExecution.getExecutableResponses().get(0).hasFacilitator()) {
      throw new InvalidRequestException(
          "Node Execution is not a Manual execution for nodeExecutionId: " + nodeExecutionId);
    }
    FacilitatorExecutableResponse facilitatorResponse = nodeExecution.getExecutableResponses().get(0).getFacilitator();
    if (isEmpty(facilitatorResponse.getCallbackIdsList())) {
      throw new InvalidRequestException(
          "No valid callback exists for resuming Manual execution for nodeExecutionId: " + nodeExecutionId);
    }
    waitNotifyEngine.doneWith(facilitatorResponse.getCallbackIds(0),
        ManualExecutionResponseData.builder().action(manualExecutionAction).build());
  }

  @Override
  public PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status) {
    return updateStatus(planExecutionId, status, null);
  }

  @Override
  public PlanExecution markPlanExecutionErrored(String planExecutionId) {
    return updateStatus(planExecutionId, ERRORED, ops -> ops.set(PlanExecutionKeys.endTs, System.currentTimeMillis()));
  }

  @Override
  public PlanExecution get(String planExecutionId) {
    return planExecutionRepository.findById(planExecutionId)
        .orElseThrow(() -> new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId));
  }

  @Override
  public PlanExecution getWithFieldsIncludedFromAnalytics(String planExecutionId, Set<String> fieldsToInclude) {
    return planExecutionRepository.getPlanExecutionWithProjectionsFromAnalytics(planExecutionId, fieldsToInclude);
  }

  @Override
  public PlanExecution getWithFieldsIncludedFromSecondary(String planExecutionId, Set<String> fieldsToInclude) {
    return planExecutionRepository.getPlanExecutionWithProjectionsFromSecondary(planExecutionId, fieldsToInclude);
  }

  @Override
  public PlanExecution getWithFieldsIncluded(String planExecutionId, Set<String> fieldsToInclude) {
    Query query = new Query(Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId));
    for (String field : fieldsToInclude) {
      query.fields().include(field);
    }
    PlanExecution planExecution = mongoTemplate.findOne(query, PlanExecution.class);
    if (planExecution == null) {
      throw new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId);
    }
    return planExecution;
  }

  @Override
  public Optional<PlanExecution> getWithFieldsIncludedOptional(String planExecutionId, Set<String> fieldsToInclude) {
    try {
      PlanExecution planExecution = getWithFieldsIncluded(planExecutionId, fieldsToInclude);
      return Optional.ofNullable(planExecution);
    } catch (Exception ex) {
      log.warn("Exception while fetching PlanExecution for planExecution id {}, returning empty", planExecutionId);
      return Optional.empty();
    }
  }

  @Override
  public PlanExecution getByIdAndLastUpdatedAtGT(String planExecutionId, Long lastUpdatedAt) {
    Query query = new Query(Criteria.where(PlanExecutionKeys.uuid)
                                .is(planExecutionId)
                                .and(PlanExecutionKeys.lastUpdatedAt)
                                .gt(lastUpdatedAt));
    return mongoTemplate.findOne(query, PlanExecution.class);
  }

  @Override
  public PlanExecution getByIdAndLastUpdatedAtGTFromSecondary(String planExecutionId, Long lastUpdatedAt) {
    Query query = new Query(Criteria.where(PlanExecutionKeys.uuid)
                                .is(planExecutionId)
                                .and(PlanExecutionKeys.lastUpdatedAt)
                                .gt(lastUpdatedAt));
    return secondaryMongoTemplateHolder.getSecondaryMongoTemplate().findOne(query, PlanExecution.class);
  }

  @Override
  public boolean checkIfPlanExecutionNotProcessedInGraph(String planExecutionId, Long lastUpdatedAt) {
    Query query = new Query(Criteria.where(PlanExecutionKeys.uuid)
                                .is(planExecutionId)
                                .and(PlanExecutionKeys.lastUpdatedAt)
                                .gt(lastUpdatedAt));
    return mongoTemplate.exists(query, PlanExecution.class);
  }

  @Override
  public PlanExecution getPlanExecutionMetadata(String planExecutionId) {
    PlanExecution planExecution = planExecutionRepository.getPlanExecutionWithProjections(planExecutionId,
        Lists.newArrayList(PlanExecutionKeys.metadata, PlanExecutionKeys.governanceMetadata,
            PlanExecutionKeys.setupAbstractions, PlanExecutionKeys.ambiance));
    if (planExecution == null) {
      throw new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId);
    }
    return planExecution;
  }

  @Override
  public ExecutionMetadata getExecutionMetadataFromPlanExecution(String planExecutionId) {
    PlanExecution planExecution = planExecutionRepository.getPlanExecutionWithIncludedProjections(
        planExecutionId, Lists.newArrayList(PlanExecutionKeys.metadata));
    if (planExecution == null) {
      throw new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId);
    }
    return planExecution.getMetadata();
  }

  @Override
  public Status getStatus(String planExecutionId) {
    PlanExecution planExecution = planExecutionRepository.getWithProjectionsWithoutUuid(
        planExecutionId, Lists.newArrayList(PlanExecutionKeys.status));
    if (planExecution == null) {
      throw new EntityNotFoundException("Plan Execution not found for id: " + planExecutionId);
    }
    return planExecution.getStatus();
  }

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    NodeStatusUpdateHandler nodeStatusUpdateObserver =
        nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(nodeUpdateInfo);
    if (nodeStatusUpdateObserver != null) {
      nodeStatusUpdateObserver.handleNodeStatusUpdate(nodeUpdateInfo);
    }
  }

  public List<PlanExecution> findAllByPlanExecutionIdIn(List<String> planExecutionIds) {
    Query query = query(where(PlanExecutionKeys.uuid).in(planExecutionIds));
    return mongoTemplate.find(query, PlanExecution.class);
  }

  @Override
  public List<PlanExecution> findPrevUnTerminatedPlanExecutionsByExecutionTag(
      PlanExecution planExecution, String executionTag) {
    List<String> resumableStatuses =
        StatusUtils.resumableStatuses().stream().map(status -> status.name()).collect(Collectors.toList());

    Criteria criteria = executionTagAndStatusCriteria(executionTag, resumableStatuses)
                            .and(PlanExecutionKeys.createdAt)
                            .lt(planExecution.getCreatedAt());

    return mongoTemplate.find(new Query(criteria), PlanExecution.class);
  }

  @Override
  public List<PlanExecution> findUnterminatedPlanExecutionsByExecutionTag(String executionTag) {
    // StatusUtils.resumableStatuses() excludes QUEUED_PLAN_CREATION and STARTING_PLAN_CREATION, but
    // PlanCreationQueueRequestHelper persists exactly QUEUED_PLAN_CREATION while
    // PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS is on. Without those two, an execution that is still
    // awaiting plan creation is invisible here: a merge queue checks_canceled reports "aborted 0" and leaves a build
    // running against a dequeued speculative commit, and checks_requested de-dupe never fires. The filter is widened
    // locally rather than in the shared resumableStatuses() constant, whose other callers must not change.
    List<String> unterminatedStatuses = Stream
                                            .concat(StatusUtils.resumableStatuses().stream(),
                                                Stream.of(Status.QUEUED_PLAN_CREATION, Status.STARTING_PLAN_CREATION))
                                            .map(Status::name)
                                            .collect(Collectors.toList());

    // Callers only need the id to raise an abort interrupt, and a PlanExecution carries the full plan
    // metadata, so projecting keeps this off the wire.
    Query query = new Query(executionTagAndStatusCriteria(executionTag, unterminatedStatuses));
    query.fields().include(PlanExecutionKeys.uuid);

    return mongoTemplate.find(query, PlanExecution.class);
  }

  private Criteria executionTagAndStatusCriteria(String executionTag, List<String> statuses) {
    return new Criteria()
        .and(ExecutionMetadataKeys.tagExecutionKey)
        .is(executionTag)
        .and(PlanExecutionKeys.status)
        .in(statuses);
  }

  @Override
  public Status calculateStatus(String planExecutionId) {
    return calculateStatus(planExecutionId, false);
  }

  @Override
  public Status calculateStatus(String planExecutionId, boolean shouldSkipIdentityNodes) {
    List<Status> statuses =
        nodeExecutionService.fetchNodeExecutionsStatusesWithoutOldRetries(planExecutionId, shouldSkipIdentityNodes);
    return OrchestrationUtils.calculateStatusForPlanExecution(statuses, planExecutionId);
  }

  /*
    This functions calculates the status of the based on status of all node execution status excluding current node. If
    the status comes out to be a terminal status, we are setting it to Running as currently is running. eg -> we have
    matrix in which few stages have failed. But currently as the  pipeline is running (may be a CI stage), then it
    should be marked to Running

    Updating planExecution status can cause race condition, thus using lock on planExecutionId so that updates are
    sequential
     */

  @Override
  public void calculateAndUpdateRunningStatusUnderLock(String planExecutionId, Status excludeNodeExecutionStatus) {
    String lockName = PLAN_EXECUTION_STATUS_UPDATE_LOCK + planExecutionId;
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLockOptional(lockName, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
      if (lock == null) {
        log.warn(String.format("[PLAN_EXECUTION_STATUS_UPDATE] Not able to take lock on plan status update for "
                + "lockName - %s, returning early.",
            lockName));
      }

      Status updateStatusTo = RUNNING;
      Status planExecutionStatus = getStatus(planExecutionId);
      if (planExecutionStatus == RUNNING) {
        return;
      }
      List<Status> nonFinalStatusNodeExecutions =
          nodeExecutionService.fetchNonFlowingAndNonFinalStatuses(planExecutionId);
      log.info("Calculating the planExecution status as current status {}", planExecutionStatus);
      Status planStatus = calculateNonFlowingAndNonFinalStatusExcluding(
          planExecutionId, nonFinalStatusNodeExecutions, excludeNodeExecutionStatus);
      if (!StatusUtils.isFinalStatus(planStatus)) {
        updateStatusTo = planStatus;
      }
      log.info("Marking PlanExecution {} status to {}", planExecutionId, updateStatusTo);
      updateStatus(planExecutionId, updateStatusTo);

    } catch (Exception exception) {
      log.error(String.format(
                    "[PLAN_EXECUTION_STATUS_UPDATE] Exception Occurred while updating status for planExecutionId: %s",
                    planExecutionId),
          exception);
    }
  }

  @Override
  public void calculateAndUpdateRunningStatusForStageAndPlanUnderLock(Ambiance ambiance) {
    String planExecutionId = ambiance.getPlanExecutionId();

    Status planExecutionStatus = getStatus(planExecutionId);
    if (planExecutionStatus == RUNNING) {
      return;
    }

    if (AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.PIPE_ROLLBACK_LEGACY_RESUME_STATUS_RECALC.name())) {
      // Legacy path: needs lock for heavy status recalculation
      String lockName = PLAN_EXECUTION_STATUS_UPDATE_LOCK + planExecutionId;
      try (AcquiredLock<?> lock =
               persistentLocker.waitToAcquireLockOptional(lockName, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
        if (lock == null) {
          log.warn(String.format("[PLAN_EXECUTION_STATUS_UPDATE] Not able to take lock on plan status update for "
                  + "lockName - %s, returning early.",
              lockName));
        }

        calculateAndUpdateRunningStatusForStageAndPlanLegacy(ambiance, planExecutionId, planExecutionStatus);
      } catch (Exception exception) {
        log.error(String.format(
                      "[PLAN_EXECUTION_STATUS_UPDATE] Exception Occurred while updating status for planExecutionId: %s",
                      planExecutionId),
            exception);
      }
    } else {
      // Optimized path: uses incremental distinct-status push-up, no lock needed
      try {
        updateStageAndPlanStatusOnResume(ambiance, planExecutionId, planExecutionStatus);
      } catch (Exception exception) {
        log.error(String.format(
                      "[PLAN_EXECUTION_STATUS_UPDATE] Exception Occurred while updating status for planExecutionId: %s",
                      planExecutionId),
            exception);
      }
    }
  }

  /**
   * Legacy path: fetches full NodeExecution documents, filters in-memory, and recalculates status.
   * Activated when PIPE_ROLLBACK_LEGACY_RESUME_STATUS_RECALC FF is enabled.
   */
  private void calculateAndUpdateRunningStatusForStageAndPlanLegacy(
      Ambiance ambiance, String planExecutionId, Status planExecutionStatus) {
    Status updateStatusTo = RUNNING;
    List<NodeExecution> allWaitingNodeExecutions;
    allWaitingNodeExecutions = nodeExecutionService.fetchWaitingStatusNodeExecutions(
        planExecutionId, NodeProjectionUtils.withAmbianceAndStatusProjected);
    String currentNodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String stageNodeExecutionId = AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);
    // Filtering the currentNodeExecution and stageNodeExecution because those might still be in waiting status and
    // we want to update those nodeExecutions now.
    allWaitingNodeExecutions =
        allWaitingNodeExecutions.stream()
            .filter(ne -> !(stageNodeExecutionId.equals(ne.getUuid()) || currentNodeExecutionId.equals(ne.getUuid())))
            .collect(Collectors.toList());

    // Updating the stage NodeExecution status.
    if (!AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_ENABLE_MANUAL_STAGE_RUN.name())
        || !AmbianceUtils.isCurrentLevelStage(ambiance)) {
      nodeExecutionService.updateCalculatedStatusForParentStageNode(ambiance, allWaitingNodeExecutions);
    }
    log.info("Calculating the planExecution status as current status {}", planExecutionStatus);
    Status planStatus = calculateNonFlowingAndNonFinalStatusExcluding(planExecutionId,
        allWaitingNodeExecutions.stream().map(NodeExecution::getStatus).collect(Collectors.toList()), null);
    if (!StatusUtils.isFinalStatus(planStatus)) {
      updateStatusTo = planStatus;
    }
    log.info("Marking PlanExecution {} status to {}", planExecutionId, updateStatusTo);
    updateStatusForceful(planExecutionId, updateStatusTo, null, false, StatusUtils.waitingStatuses());

    pipelineStageStatusHelper.updatePipelineAndStageRunningStatus(ambiance, updateStatusTo);
  }

  /**
   * Optimized path: uses distinct-status queries instead of fetching full documents.
   * No deserialization overhead. Default when PIPE_ROLLBACK_LEGACY_RESUME_STATUS_RECALC FF is disabled.
   */
  private void updateStageAndPlanStatusOnResume(Ambiance ambiance, String planExecutionId, Status planExecutionStatus) {
    String currentNodeExecutionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String stageNodeExecutionId = AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);

    // Update stage status if applicable
    if (!AmbianceUtils.checkIfFeatureFlagEnabled(ambiance, FeatureName.PIPE_ENABLE_MANUAL_STAGE_RUN.name())
        || !AmbianceUtils.isCurrentLevelStage(ambiance)) {
      List<Status> stageWaiters = nodeExecutionService.fetchDistinctWaitingStatusesForStage(
          planExecutionId, stageNodeExecutionId, currentNodeExecutionId);

      if (stageWaiters.isEmpty()) {
        nodeExecutionService.updateStatusWithOps(stageNodeExecutionId, RUNNING, null, StatusUtils.waitingStatuses());
      } else {
        Status calculatedStageStatus = StatusUtils.calculateStatus(stageWaiters, planExecutionId);
        if (!StatusUtils.isFinalStatus(calculatedStageStatus)) {
          nodeExecutionService.updateStatusWithOps(
              stageNodeExecutionId, calculatedStageStatus, null, EnumSet.of(RUNNING));
        }
      }
    }

    // Update plan status
    List<Status> planWaiters = nodeExecutionService.fetchDistinctWaitingStatusesForPlan(
        planExecutionId, currentNodeExecutionId, stageNodeExecutionId);

    if (planWaiters.isEmpty()) {
      log.info("No waiters remain, marking PlanExecution {} status to RUNNING", planExecutionId);
      updateStatusForceful(planExecutionId, RUNNING, null, false, StatusUtils.waitingStatuses());
      pipelineStageStatusHelper.updatePipelineAndStageRunningStatus(ambiance, RUNNING);
    } else {
      Status correctWaitingStatus = StatusUtils.calculateStatus(planWaiters, planExecutionId);
      if (correctWaitingStatus != planExecutionStatus) {
        log.info("Correcting PlanExecution {} status from {} to {}", planExecutionId, planExecutionStatus,
            correctWaitingStatus);
        updateStatusForceful(planExecutionId, correctWaitingStatus, null, false, StatusUtils.waitingStatuses());
      }
    }
  }

  // excludeCurrentNodeExecutionStatus if some status of nodeExecution you want to exclude from calculateStatus
  private Status calculateNonFlowingAndNonFinalStatusExcluding(
      String planExecutionId, List<Status> nonFinalStatusList, Status excludeCurrentNodeExecutionStatus) {
    if (excludeCurrentNodeExecutionStatus != null) {
      nonFinalStatusList.remove(excludeCurrentNodeExecutionStatus);
    }
    return StatusUtils.calculateStatus(nonFinalStatusList, planExecutionId);
  }

  public PlanExecution updateCalculatedStatus(String planExecutionId) {
    return updateStatus(planExecutionId, calculateStatus(planExecutionId));
  }

  private void emitEvent(PlanExecution planExecution, Status previousStatus) {
    Status currentStatus = planExecution.getStatus();
    Ambiance ambiance = buildFromPlanExecution(planExecution);
    planStatusUpdateSubject.fireInform(PlanStatusUpdateObserver::onPlanStatusUpdate, ambiance);
    planStatusUpdateSubject.fireInform(
        PlanStatusUpdateObserver::onPlanStatusUpdate, ambiance, currentStatus, previousStatus);
  }

  private Ambiance buildFromPlanExecution(PlanExecution planExecution) {
    return Ambiance.newBuilder()
        .setPlanExecutionId(planExecution.getUuid())
        .putAllSetupAbstractions(
            isEmpty(planExecution.getSetupAbstractions()) ? new HashMap<>() : planExecution.getSetupAbstractions())
        .setMetadata(
            planExecution.getMetadata() == null ? ExecutionMetadata.newBuilder().build() : planExecution.getMetadata())
        .build();
  }

  @Override
  public List<PlanExecution> findByStatusWithProjections(Set<Status> statuses, Set<String> fieldNames) {
    Query query = query(where(PlanExecutionKeys.status).in(statuses));
    Field field = query.fields();
    for (String fieldName : fieldNames) {
      field = field.include(fieldName);
    }
    return mongoTemplate.find(query, PlanExecution.class);
  }

  @Override
  public Stream<PlanExecution> fetchPlanExecutionsByStatusFromAnalytics(Set<Status> statuses, Set<String> fieldNames) {
    // Uses status_idx index
    Query query = query(where(PlanExecutionKeys.status).in(statuses));
    for (String fieldName : fieldNames) {
      query.fields().include(fieldName);
    }
    return planExecutionRepository.fetchPlanExecutionsFromAnalytics(query);
  }

  @Override
  public List<PlanExecution> findAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps(
      String accountId, String orgId, String projectId, long fromTS, long toTS) {
    Map<String, String> setupAbstractionSubFields = new HashMap<>();
    setupAbstractionSubFields.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractionSubFields.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractionSubFields.put(SetupAbstractionKeys.projectIdentifier, projectId);
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions)
                            .is(setupAbstractionSubFields)
                            .and(PlanExecutionKeys.lastUpdatedAt)
                            .gte(fromTS)
                            .lte(toTS);

    return mongoTemplate.find(query(criteria), PlanExecution.class);
  }

  @Override
  public long countRunningExecutionsForGivenPipelineInAccount(String accountId) {
    EnumSet<Status> statuses = StatusUtils.activeStatuses();
    // the change is added for ignoring approval waiting and resource waiting status for customers with that have high
    // number of pipelines waiting on such statuses. This FF that won't be GA'd for time being
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.CDS_IGNORE_APPROVAL_WAITING_FROM_CONCURRENT)) {
      statuses = StatusUtils.activeStatusWithoutApprovalWaiting();
    }
    // Uses - accountId_status_idx
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionKeys.status)
                            .in(statuses);
    return mongoTemplate.count(new Query(criteria), PlanExecution.class);
  }

  @Override
  public long countQueuedExecutionsForGivenAccount(String accountIdentifier) {
    // Uses - accountId_status_idx
    // Status QUEUED is used only for queued steps and stages whereas, QUEUED_EXECUTION_CONCURRENCY_REACHED is used for
    // queued pipelines
    // For async plan creation via queued service we have added a new queued pipeline status QUEUED_PLAN_CREATION
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
                            .is(accountIdentifier)
                            .and(PlanExecutionKeys.status)
                            .in(EnumSet.of(QUEUED_EXECUTION_CONCURRENCY_REACHED, QUEUED_PLAN_CREATION));
    return mongoTemplate.count(new Query(criteria), PlanExecution.class);
  }

  @Override
  public long countRunningExecutionsForGivenPriorityInAccount(String accountId, PriorityType priorityType) {
    EnumSet<Status> statuses = StatusUtils.activeStatuses();
    // the change is added for ignoring approval waiting and resource waiting status for customers with that have high
    // number of pipelines waiting on such statuses. This FF that won't be GA'd for time being
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.CDS_IGNORE_APPROVAL_WAITING_FROM_CONCURRENT)) {
      statuses = StatusUtils.activeStatusWithoutApprovalWaiting();
    }

    // Uses - accountId_priorityType_status_idx
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionKeys.priorityType)
                            .is(priorityType)
                            .and(PlanExecutionKeys.status)
                            .in(statuses);
    return mongoTemplate.count(new Query(criteria), PlanExecution.class);
  }

  @Override
  public PlanExecution findNextExecutionToRunInAccount(String accountId) {
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionKeys.status)
                            .in(Status.QUEUED, Status.QUEUED_EXECUTION_CONCURRENCY_REACHED);
    return mongoTemplate.findOne(
        new Query(criteria).with(Sort.by(Sort.Direction.ASC, PlanExecutionKeys.createdAt)), PlanExecution.class);
  }

  @Override
  public List<PlanExecution> findNextExecutionsToRunInAccount(String accountId, int limit) {
    Criteria criteria = new Criteria()
                            .and(PlanExecutionKeys.setupAbstractions + "." + SetupAbstractionKeys.accountId)
                            .is(accountId)
                            .and(PlanExecutionKeys.status)
                            .in(Status.QUEUED, Status.QUEUED_EXECUTION_CONCURRENCY_REACHED);
    Query query =
        new Query(criteria).with(Sort.by(Sort.Direction.ASC, PlanExecutionKeys.createdAt)).limit(Math.max(1, limit));
    return mongoTemplate.find(query, PlanExecution.class);
  }

  @Override
  public void deleteAllPlanExecutionAndMetadata(
      Set<String> planExecutionIds, boolean retainPipelineExecutionDetailsAfterDelete, String accountId) {
    // Uses idx index
    Query query = query(where(PlanExecutionKeys.uuid).in(planExecutionIds));
    for (String fieldName : PlanExecutionProjectionConstants.fieldsForPlanExecutionDelete) {
      query.fields().include(fieldName);
    }
    List<PlanExecution> batchPlanExecutions = new LinkedList<>();
    try (Stream<PlanExecution> stream = planExecutionRepository.fetchPlanExecutionsFromAnalytics(query)) {
      Iterator<PlanExecution> iterator = stream.iterator();
      while (iterator.hasNext()) {
        PlanExecution next = iterator.next();
        batchPlanExecutions.add(next);
        if (batchPlanExecutions.size() >= PersistenceModule.MAX_BATCH_SIZE) {
          deletePlanExecutionMetadataInternal(
              batchPlanExecutions, retainPipelineExecutionDetailsAfterDelete, accountId);
          batchPlanExecutions.clear();
        }
      }
    }
    if (EmptyPredicate.isNotEmpty(batchPlanExecutions)) {
      // at end if any execution metadata is left, delete those as well
      deletePlanExecutionMetadataInternal(batchPlanExecutions, retainPipelineExecutionDetailsAfterDelete, accountId);
    }
    deletePlanExecutionsInternal(planExecutionIds);
  }

  private void deletePlanExecutionMetadataInternal(
      List<PlanExecution> batchPlanExecutions, boolean retainPipelineExecutionDetailsAfterDelete, String accountId) {
    // Delete planExecutionMetadata example - PlanExecutionMetadata, PipelineExecutionSummaryEntity
    planExecutionDeleteObserverSubject.fireInform(PlanExecutionDeleteObserver::onPlanExecutionsDelete,
        batchPlanExecutions, retainPipelineExecutionDetailsAfterDelete, accountId);
  }

  private void deletePlanExecutionsInternal(Set<String> planExecutionIds) {
    if (EmptyPredicate.isEmpty(planExecutionIds)) {
      return;
    }
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // Uses - id index
      planExecutionRepository.deleteAllByUuidIn(planExecutionIds);
      return true;
    });
  }

  @Override
  public void updateTTL(String planExecutionId, Date ttlDate) {
    // Uses idx index
    if (EmptyPredicate.isEmpty(planExecutionId)) {
      return;
    }
    Criteria planExecutionIdCriteria = Criteria.where(PlanExecutionKeys.uuid).is(planExecutionId);
    Query query = new Query(planExecutionIdCriteria);
    Update ops = new Update();
    ops.set(PlanExecutionKeys.validUntil, ttlDate);

    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // Uses - id index
      planExecutionRepository.multiUpdatePlanExecution(query, ops);
      return true;
    });
  }

  @Override
  public List<PlanExecutionCountWithAccountResult> aggregateActiveExecutionsCountPerAccount() {
    return planExecutionRepository.aggregateActiveExecutionsCountPerAccount();
  }

  @Override
  public List<PlanExecutionCountWithAccountAndTriggerTypeResult>
  aggregateActiveExecutionsCountPerAccountWithTriggerType() {
    return planExecutionRepository.aggregateActiveExecutionsCountPerAccountByTriggerType();
  }

  @Override
  public List<String> findAllAccountIdsWithExecutionsFromAnalytics() {
    // Uses index - accountId_status_createdAt_idx
    return planExecutionRepository.findAllAccountIdsWithExecutionsFromAnalytics();
  }
}
