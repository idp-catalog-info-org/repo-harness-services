/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.elasticsearch.ElasticSearchStream;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO;
import io.harness.search.entity.beans.PipelineSearchFieldType;
import io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO;

import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
public interface PipelineSearchService {
  /**
   * Returns the elastic search index name based on the account id and the status of the execution
   *
   * @param accountIdentifier accountID - to customize the index name per account
   * @param status           status of the execution
   * @return index name
   */
  List<String> getAllWriteIndexNames(String accountIdentifier, Status status);

  /**
   * Returns the elastic search index name for executions in any running state
   * @return index name for executions in any running state
   */
  String getRunningExecutionIndexName();

  /**
   * Creates the doc if not present, otherwise replaces the old document with the new one.
   * This method is only useful if we are sending the full document, DON'T USE if sending partial document
   * And also better performant than update if the doc size is small
   * Reference - https://stackoverflow.com/questions/53111042/index-vs-partial-update-in-elasticsearch
   * Index Refresh(for search) will happen every 1s by default on elasticsearch
   * @return Result - enum of the status of the operation(NOOP/CREATED/DELETED etc.)
   */
  Result index(PipelineSearchExecutionSummaryDTO pipelineSearchExecutionSummaryDTO, String indexName);

  Result updateRecord(PipelineSearchExecutionSummaryDTO pipelineSearchExecutionSummaryDTO, String indexName);

  /**
   * Updates the isDeleted field for the provided execution
   * @param pipelineSearchExecutionSummaryDTO execution for which the field is to be updated
   * @param indexName name of the index
   * @return Result - enum of the status of the operation(NOOP/CREATED/DELETED etc.)
   */
  Result updateIsDeleted(
      PipelineSearchExecutionSummaryDTO pipelineSearchExecutionSummaryDTO, String indexName, boolean isDeleted);

  void save(PipelineExecutionSummaryEntity entity);

  /**
   * Syncs the provided execution summary to elasticsearch
   * Internally checks which index to sync the record to and saves the record
   */
  void update(PipelineExecutionSummaryEntity entity);

  void syncCompletedExecutionsToElastic(PipelineExecutionSummaryEntity entity);

  void updateCompletedExecutionsToElastic(PipelineExecutionSummaryEntity entity);

  /**
   * Verifies whether the update operations for PipelineExecutionSummaryEntity requires an update on elastic as well
   * This is required because we are not storing the full entity in elastic search
   * If the updated fields are not present in elastic, we will skip the update operation on elastic
   */
  boolean shouldSyncToElastic(Update updateOps);

  boolean shouldFetchDocumentFromPrimary(Update updateOps);

  /**
   * Returns all the index names in elastic search for an account
   */
  List<String> getAllIndexNames(String accountIdentifier);

  /**
   * Fetches first execution endTs from Elastic search, this is required for the reconciliation job
   */
  Optional<Long> fetchFirstExecutionEndTs();

  boolean checkIfPlanExecutionIDExists(PipelineExecutionSummaryEntity entity);

  DeleteByQueryResponse deleteExecutions(Set<String> planExecutionIDs, String accountId);

  /**
   * Deletes the completed executions from the running index and spins up an async task for the same
   */
  String deleteCompletedExecutions();

  String deleteExpiredExecutions(String accountIdentifier, int retentionPeriodInMonths);

  String deleteExpiredExecutionsForDefaultRetentionPeriod(List<String> accountIdentifier, int retentionPeriodInMonths);

  /**
   * List the executions from elasticsearch based on the query and the pageable request
   * Returns only the plan Execution ids in a sorted order, rest of the data is fetched via mongoDB
   */
  Page<String> listExecutions(String accountId, Pageable pageable, Query query);

  /**
   * List the executions from elasticsearch based on the query, index name and the no. of records to fetch
   * This is required for the index migration job to fetch the planExecutionIDs missing in old and new index
   */
  List<String> listExecutionsFromIndex(Query query, String indexName, int size);

  /**
   * Creates index alias for accounts with higher retention periods
   */
  void createIndexAlias(String accountIdentifier, PipelineSearchIndexRetentionPeriods indexRetentionPeriod);

  /**
   * Updates index alias for a given field. Defaults to KEYWORD field type.
   */
  void updateIndexAlias(
      String accountIdentifier, String fieldName, PipelineSearchIndexRetentionPeriods indexRetentionPeriod);

  /**
   * Updates index alias for a given field with an explicit field type (e.g. DATE for timestamp fields).
   */
  void updateIndexAlias(String accountIdentifier, String fieldName,
      PipelineSearchIndexRetentionPeriods indexRetentionPeriod, PipelineSearchFieldType fieldType);

  /**
   * Reindexing documents from one index to another
   * This is required for the migration job to migrate data for a customer from one elastic index to another
   */
  ReindexResponse reIndexDocuments(String accountIdentifier, String oldIndexName, String newIndexName, Query query);

  /**
   * It creates basic query for rootExecutionId with scope as accountIdentifier
   * @param accountIdentifier
   * @param rootExecutionId
   * @return
   */
  Query formQueryForRootExecutionId(String accountIdentifier, String rootExecutionId);

  /**
   * Fetches the latest retry execution info after filtering by root parent id
   * @param accountIdentifier
   * @param rootParentId
   * @return
   */
  PipelineSearchReadExecutionSummaryDTO fetchLatestExecutionUsingRootParentId(
      String accountIdentifier, String rootParentId);

  /**
   *  List the executions from elasticsearch based on the query and sorting fields order
   * @param accountIdentifier
   * @param query
   * @param sortingFields
   * @param size
   * @return
   */
  List<PipelineSearchReadExecutionSummaryDTO> listExecutions(
      String accountIdentifier, Query query, Map<String, SortOrder> sortingFields, Integer size);

  /**
   * It gives count execution summary entity count
   * @param accountIdentifier
   * @param query
   * @return
   */
  long getExecutionSummariesCount(String accountIdentifier, Query query);

  /**
   * It returns the stream of PipelineSearchReadExecutionSummaryDTO
   * @param accountIdentifier
   * @param query
   * @param fieldsToInclude
   * @return
   */
  ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> fetchPipelineSearchReadExecutionSummaryDTO(
      String accountIdentifier, Query query, Set<String> fieldsToInclude);

  /**
   * List the planExecution ids from elasticsearch based on the query,
   * @param accountIdentifier
   * @param query
   * @param sortingFields
   * @param lastSortValues
   * @param size
   * @return
   */
  List<String> listExecutionIdsWithSearchAfter(String accountIdentifier, Query query,
      Map<String, SortOrder> sortingFields, List<Object> lastSortValues, Integer size);

  ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> fetchPipelineSearchReadExecutionSummaryDTO(
      String accountIdentifier, Query query, Set<String> fieldsToInclude, Map<String, SortOrder> sortingFields);
}
