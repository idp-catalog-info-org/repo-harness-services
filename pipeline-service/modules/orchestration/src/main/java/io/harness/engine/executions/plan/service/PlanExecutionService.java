/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.pms.execution.manual.beans.ManualExecutionAction;
import io.harness.execution.PlanExecution;
import io.harness.execution.PriorityType;
import io.harness.monitoring.PlanExecutionCountWithAccountAndTriggerTypeResult;
import io.harness.monitoring.PlanExecutionCountWithAccountResult;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.NonNull;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface PlanExecutionService extends NodeStatusUpdateObserver {
  PlanExecution updateStatusForceful(@NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops,
      boolean forced, EnumSet<Status> allowedStartStatuses);
  void handleManualExecution(String accountId, String orgId, String projectId, @NonNull String nodeExecutionId,
      @NonNull ManualExecutionAction manualExecutionAction, ScopeInfo scopeInfo);
  PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status);
  PlanExecution markPlanExecutionErrored(String planExecutionId);
  PlanExecution updateStatus(@NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops);

  PlanExecution updateStatusForceful(
      @NonNull String planExecutionId, @NonNull Status status, Consumer<Update> ops, boolean forced);

  PlanExecution get(String planExecutionId);

  PlanExecution getWithFieldsIncludedFromAnalytics(String planExecutionId, Set<String> fieldsToInclude);

  PlanExecution getWithFieldsIncludedFromSecondary(String planExecutionId, Set<String> fieldsToInclude);

  PlanExecution getWithFieldsIncluded(String planExecutionId, Set<String> fieldsToInclude);
  Optional<PlanExecution> getWithFieldsIncludedOptional(String planExecutionId, Set<String> fieldsToInclude);

  PlanExecution getByIdAndLastUpdatedAtGT(String planExecutionId, Long lastUpdatedAt);

  PlanExecution getByIdAndLastUpdatedAtGTFromSecondary(String planExecutionId, Long lastUpdatedAt);

  boolean checkIfPlanExecutionNotProcessedInGraph(String planExecutionId, Long lastUpdatedAt);

  /**
   * @param planExecutionId planExecutionId
   * @return This method returns PlanExecution but excluding ExecutionMetadata, GovernanceMetadata,
   * setupAbstractions and ambiance
   */
  PlanExecution getPlanExecutionMetadata(String planExecutionId);

  ExecutionMetadata getExecutionMetadataFromPlanExecution(String planExecutionId);

  PlanExecution save(PlanExecution planExecution);

  Status getStatus(String planExecutionId);

  List<PlanExecution> findAllByPlanExecutionIdIn(List<String> planExecutionIds);

  List<PlanExecution> findPrevUnTerminatedPlanExecutionsByExecutionTag(
      PlanExecution planExecution, String executionTag);

  /**
   * Returns id-only projections of the unterminated executions carrying the given execution tag. Every other
   * field on the returned objects is null; widen the projection in the implementation before reading them.
   */
  List<PlanExecution> findUnterminatedPlanExecutionsByExecutionTag(String executionTag);

  Status calculateStatus(String planExecutionId);

  Status calculateStatus(String planExecutionId, boolean shouldSkipIdentityNodes);

  PlanExecution updateCalculatedStatus(String planExecutionId);

  /**
   * Updated planExecution status if calculated status are non-final and non-flowing statuses under Lock
   * @param planExecutionId
   * @param excludeNodeExecutionStatus
   */
  void calculateAndUpdateRunningStatusUnderLock(String planExecutionId, Status excludeNodeExecutionStatus);

  /**
   * Updated planExecution status and stage nodeExecution status if calculated status are non-final and non-flowing
   * statuses under Lock
   * @param ambiance
   */
  void calculateAndUpdateRunningStatusForStageAndPlanUnderLock(Ambiance ambiance);

  List<PlanExecution> findByStatusWithProjections(Set<Status> statuses, Set<String> fieldNames);

  /**
   * Fetches all planExecutions entries with given status with fieldsToBeIncluded as projections from analytics node
   * Uses - status_idx index
   * @param statuses
   * @param fieldNames
   * @return
   */
  Stream<PlanExecution> fetchPlanExecutionsByStatusFromAnalytics(Set<Status> statuses, Set<String> fieldNames);

  // Todo: Remove
  List<PlanExecution> findAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps(
      String accountId, String orgId, String projectId, long startTS, long endTS);

  long countRunningExecutionsForGivenPipelineInAccount(String accountId);

  long countQueuedExecutionsForGivenAccount(String accountIdentifier);

  long countRunningExecutionsForGivenPriorityInAccount(String accountId, PriorityType priorityType);

  PlanExecution findNextExecutionToRunInAccount(String accountId);

  List<PlanExecution> findNextExecutionsToRunInAccount(String accountId, int limit);

  /**
   * Deletes the planExecution and its related metadata
   * @param planExecutionIds Ids of to be deleted planExecutions
   */
  void deleteAllPlanExecutionAndMetadata(
      Set<String> planExecutionIds, boolean retainPipelineExecutionDetailsAfterDelete, String accountId);

  /**
   * Updates TTL all planExecution and its related metadata
   * @param planExecutionId Ids of to be updated TTL planExecutions
   */
  void updateTTL(String planExecutionId, Date ttlDate);

  /**
   * Fetches aggregated active executions count per account from analytics node
   * @return
   */
  List<PlanExecutionCountWithAccountResult> aggregateActiveExecutionsCountPerAccount();

  List<PlanExecutionCountWithAccountAndTriggerTypeResult> aggregateActiveExecutionsCountPerAccountWithTriggerType();

  List<String> findAllAccountIdsWithExecutionsFromAnalytics();
}
