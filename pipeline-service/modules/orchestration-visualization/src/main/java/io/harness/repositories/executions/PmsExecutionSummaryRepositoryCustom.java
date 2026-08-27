/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface PmsExecutionSummaryRepositoryCustom {
  PipelineExecutionSummaryEntity update(Query query, Update update);

  // updates multiple records and doesnt return any record
  void multiUpdate(Query query, Update update);
  UpdateResult deleteAllExecutionsWhenPipelineDeleted(Query query, Update update);
  Page<PipelineExecutionSummaryEntity> findAll(Criteria criteria, Pageable pageable);

  Page<PipelineExecutionSummaryEntity> findAll(
      Criteria criteria, Pageable pageable, String accountId, String sortProperty);

  Page<PipelineExecutionSummaryEntity> findAllWithProjection(
      Criteria criteria, Pageable pageable, List<String> projections);

  Stream<PipelineExecutionSummaryEntity> findAllWithRequiredProjectionUsingAnalyticsNode(
      Criteria criteria, List<String> projections);

  long getCountOfExecutionSummary(Criteria criteria);
  String fetchRootRetryExecutionId(String planExecutionId);

  /**
   * This method is used to query pipelineExecutionSummaryEntity by filtering on endTs >= startTime and endTs <= endTime
   * Uses - endTs_idx index
   * @param startTime startTime
   * @param endTime endTime
   * @return Stream of PipelineExecutionSummaryEntity
   */
  Stream<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsBetweenEndTsFromSecondary(
      String accountId, Long startTime, Long endTime, Set<String> fieldsToInclude);

  /**
   * Same as {@link #fetchPlanExecutionIdsBetweenEndTsFromSecondary(String, Long, Long, Set)} with optional org scope.
   * When orgIdentifier is null, no org filter is applied.
   */
  Stream<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsBetweenEndTsFromSecondary(
      String accountId, Long startTime, Long endTime, Set<String> fieldsToInclude, String orgIdentifier);

  /**
   * This method is used to query pipelineExecutionSummaryEntity by filtering on endTs >= currentTime
   * Uses - endTs_idx index
   * @param currentTime currentTime
   * @return Stream of PipelineExecutionSummaryEntity
   */
  Stream<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsWithGTEEndTsFromSecondary(
      String accountId, Long currentTime);

  /**
   * Same as {@link #fetchPlanExecutionIdsWithGTEEndTsFromSecondary(String, Long)} with optional org scope.
   * When orgIdentifier is null, no org filter is applied.
   */
  Stream<PipelineExecutionSummaryEntity> fetchPlanExecutionIdsWithGTEEndTsFromSecondary(
      String accountId, Long currentTime, String orgIdentifier);

  Stream<PipelineExecutionSummaryEntity> fetchRunningStuckPlanExecutions();

  /**
   * Returns iterator on PipelineExecutionSummaryEntity for given query having projection fields else throws exception
   * The results are fetched from analytics peferred db node
   * @param query
   * @return
   */
  Stream<PipelineExecutionSummaryEntity> fetchExecutionSummaryEntityFromAnalytics(Query query);

  /**
   * Fetches PipelineExecutionSummaryEntity from DB using projections.
   * Only fields specified in fieldsToInclude are added.
   * @param criteria
   * @param fieldsToInclude
   * @return
   */
  PipelineExecutionSummaryEntity getPipelineExecutionSummaryWithProjections(
      Criteria criteria, Set<String> fieldsToInclude);

  /**
   * Fetches pipeline execution summary entity from rootParentId. Used to calculate the last retried pipeline
   *
   * Uses: rootExecution_createdAt_id idx
   *
   * @param rootParentId
   * @return
   */
  Stream<PipelineExecutionSummaryEntity> fetchPipelineSummaryEntityFromRootParentIdUsingSecondaryMongo(
      String rootParentId);

  /**
   * This method is used to query pipelineExecutionSummaryEntity using planExecutionId on secondary mongo
   * Uses - planExecutionId_idx index
   * @param planExecutionId planExecutionId
   * @return PipelineExecutionSummaryEntity
   */
  PipelineExecutionSummaryEntity fetchByPlanExecutionIdFromSecondary(String planExecutionId);

  PipelineExecutionSummaryEntity getPipelineExecutionSummaryEntityFromSecondaryMongoWithProjections(String accountId,
      String orgId, String projectId, String planExecutionId, boolean pipelineDeleted, List<String> projections,
      ScopeInfo scopeInfo);

  List<PipelineExecutionSummaryEntity> findAllWithProjectionWithoutPagination(
      Criteria criteria, Pageable pageable, List<String> projections, String hintIndex);

  List<PipelineExecutionSummaryEntity> findAllWithProjectionWithoutPagination(
      Criteria criteria, List<String> projections);

  /**
   * Fetches PipelineExecutionSummaryEntity from secondary DB using projections and planExecutionId.
   * Uses - planExecutionId_1 idx
   * @param planExecutionId planExecutionId
   * @param projections fields to include
   * @return PipelineExecutionSummaryEntity
   */
  PipelineExecutionSummaryEntity fetchFromSecondaryWithProjections(String planExecutionId, Set<String> projections);

  /**
   * Fetches the latest PipelineExecutionSummaryEntity having the specified
   * root parent id from secondary DB
   * Uses - rootExecution_createdAt_id idx
   * @param rootParentId rootParentId
   * @return PipelineExecutionSummaryEntity
   */
  PipelineExecutionSummaryEntity fetchLatestExecutionUsingRootParentIdFromSecondary(String rootParentId);
}
