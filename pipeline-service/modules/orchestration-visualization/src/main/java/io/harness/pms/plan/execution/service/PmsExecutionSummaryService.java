/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.RetryExecutionInfoDTO;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public interface PmsExecutionSummaryService {
  void regenerateStageLayoutGraph(
      String planExecutionId, List<NodeExecution> nodeExecutions, PlanExecution planExecution);
  PipelineExecutionSummaryEntity update(String planExecutionId, Update update);
  void updateResolvedUserInputSetYaml(String planExecutionId, String resolvedInputSetYaml, String harnessVersion);
  // Saves PipelineExecutionSummaryEntity in planExecutionsSummary collection in harness-pms db
  PipelineExecutionSummaryEntity save(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity);

  /**
   * This method is used to query pipelineExecutionSummaryEntity using planExecutionId and the fields that should be set
   * in the response
   *
   * Uses- planExecutionId index
   *
   * @param accountIdentifier
   * @param planExecutionId
   * @param fields
   * @return
   */
  PipelineExecutionSummaryEntity getPipelineExecutionSummaryWithProjections(
      String accountIdentifier, String planExecutionId, Set<String> fields);

  /**
   * updates the top graph based on the type of nodeExecution
   * @param planExecutionId
   * @param nodeExecution
   * @param update
   * @return
   */
  boolean handleNodeExecutionUpdateFromGraphUpdate(String planExecutionId, NodeExecution nodeExecution, Update update);

  /**
   * Fetches pipeline execution ids and their status only as an iterator from analytics node
   * Uses - accountId_organizationId_projectId_pipelineId idx
   * @param accountId
   * @param orgIdentifier
   * @param projectIdentifier
   * @param pipelineIdentifier
   * @return
   */
  Stream<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsAndStatusFromAnalytics(String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String parentUniqueId);

  /**
   * Delete all PipelineExecutionSummaryEntity for given planExecutionIds
   * Uses - planExecutionId_idx index
   * @param planExecutionIds
   */
  void deleteAllSummaryForGivenPlanExecutionIds(
      Set<String> planExecutionIds, boolean retainPipelineExecutionDetailsAfterDelete, String accountId);

  /**
   * Updates TTL all PipelineExecutionSummaryEntity and its related metadata
   * @param planExecutionId Id of to be updated TTL planExecutions
   * Uses - planExecutionId_unique idx
   */
  void updateTTL(String planExecutionId, Date ttlDate);

  /**
   * Adds the status updates for the PipelineExecutionSummaryEntity for give planExecution. It also updates the endTs if
   * status is terminal.
   * @param planExecution planExecution for which we want to update the PipelineExecutionSummaryEntity.
   * @param summaryEntityUpdate Update object that will have the update operations inside it. Caller will apply all the
   *     updates in once.
   * Uses - planExecutionId_unique idx
   */
  Update updateStatusOps(PlanExecution planExecution, Update summaryEntityUpdate);

  /**
   * Fetches the latest retry execution info after filtering by root parent id
   * @param accountIdentifier
   * @param rootParentId root planExecutionId for which we want to fetch the latest execution
   * Uses - rootExecution_createdAt_id idx
   */
  RetryExecutionInfoDTO fetchLatestRetryExecutionInfoDTO(String accountIdentifier, String rootParentId);

  /**
   * Fetches PipelineExecutionSummaryEntity from secondary DB using projections and planExecutionId.
   * @param planExecutionId planExecutionId
   * @param projections fields to include
   * @return PipelineExecutionSummaryEntity
   */
  PipelineExecutionSummaryEntity fetchFromSecondaryWithProjections(
      String accountIdentifier, String planExecutionId, Set<String> projections);

  String fetchRootRetryExecutionId(String accountIdentifier, String planExecutionId);

  PipelineExecutionSummaryEntity getFromSecondaryWithProjections(String accountId, String orgId, String projectId,
      String planExecutionId, boolean pipelineDeleted, List<String> projections, ScopeInfo scopeInfo);

  String getNotesForExecution(String accountIdentifier, String planExecutionId);

  String updateNotesForExecution(String accountIdentifier, String planExecutionId, String notes);

  /**
   * Updates the PipelineExecutionSummaryEntity with status, endTs, failureInfo, and abortedBy from a CDC
   * planExecution event. Mirrors the logic of updateStatusOps but takes pre-extracted fields from the CDC document:
   * - Sets internalStatus and status on all calls
   * - Sets executionErrorInfo and failureInfo for ERRORED/EXPIRED statuses
   * - Fetches and sets abortedBy from interrupts collection for ABORTED status
   * - Sets endTs for final statuses
   *
   * @param planExecutionId the plan execution ID
   * @param status the protobuf Status enum value
   * @param endTs the end timestamp (may be null for non-final statuses)
   * @param failureInfo the protobuf FailureInfo deserialized from the CDC document (may be null)
   */
  void updateStatusFromCDC(String planExecutionId, Status status, Long endTs, FailureInfo failureInfo);
}
