/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.elasticsearch.framework.OperatorEnum.CONSTANT_SCORE;
import static io.harness.elasticsearch.framework.OperatorEnum.EQUALS;
import static io.harness.elasticsearch.framework.OperatorEnum.EQUALS_ANY;
import static io.harness.elasticsearch.framework.OperatorEnum.LESS_THAN;
import static io.harness.elasticsearch.framework.OperatorEnum.MUST_MATCH_ALL;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.ELASTIC_SEARCH_FIELDS_LEAF_FIELDS;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.ELASTIC_SEARCH_PARENT_FIELDS;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.ELASTIC_SEARCH_RACE_CONDITION_FIELDS;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PIPELINE_SEARCH_READ_EXECUTION_SUMMARY_DTO_ALL_FIELDS;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ENTITY_MAPPINGS_JSON_FILE_PATH;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.elasticsearch.ElasticSearchDBConfig;
import io.harness.elasticsearch.ElasticSearchStream;
import io.harness.elasticsearch.utils.ElasticSearchQueryBuilder;
import io.harness.elasticsearch.utils.ElasticSearchUtils;
import io.harness.entity.accountoverrides.SearchSettings;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.search.helper.PipelineSearchHelper;
import io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;
import io.harness.search.entity.beans.PipelineSearchFieldType;
import io.harness.search.entity.beans.PipelineSearchIndexRetentionPeriods;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO;
import io.harness.search.mappers.PipelineSearchExecutionSummaryDTOMapper;
import io.harness.search.service.PipelineSearchService;
import io.harness.search.utils.PipelineSearchUtils;

import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.ReindexRequest;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TrackHits;
import co.elastic.clients.elasticsearch.indices.Alias;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexSettingsLifecycle;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateResponse;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingResponse;
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Slf4j
public class PipelineSearchServiceImpl implements PipelineSearchService {
  @Nullable @Inject private ElasticSearchClient elasticsearchClient;
  @Inject private ElasticSearchDBConfig elasticSearchDBConfig;
  @Inject private PipelineRetentionService pipelineRetentionService;

  private static final String DOCUMENT_MISSING_EXCEPTION = "document_missing_exception";
  private static final Long MAX_DOCS_TO_DELETE = 50000L;
  private static final Long MAX_RUNNING_DOCS_TO_DELETE = 200000L;
  private static final Long PAGINATION_MAX_DOCS_LIMIT = 10000L;
  private static final String UPDATE_EXECUTION_ERROR_MESSAGE =
      "[ELASTIC_SEARCH]: Could not update execution summary for execution uuid: %s";

  @Override
  public List<String> getAllWriteIndexNames(String accountIdentifier, Status status) {
    if (StatusUtils.isFinalStatus(status)) {
      return getCompletedExecutionIndexNames(accountIdentifier);
    } else {
      return List.of(getRunningExecutionIndexName());
    }
  }

  private List<String> getCompletedExecutionIndexNames(String accountIdentifier) {
    Optional<SearchSettings> searchSettingsOptional = pipelineRetentionService.getSearchSettings(accountIdentifier);
    if (searchSettingsOptional.isPresent()) {
      SearchSettings searchSettings = searchSettingsOptional.get();
      switch (searchSettings.getIndexMigrationStatus()) {
        case IN_PROGRESS -> {
          return List.of(searchSettings.getOldIndexName(), searchSettings.getNewIndexName());
        }
        case COMPLETE -> {
          return List.of(searchSettings.getNewIndexName());
        }
        default -> {
          return List.of(searchSettings.getOldIndexName());
        }
      }
    }
    return List.of(PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ALIAS_6_MONTH);
  }

  @Override
  public String getRunningExecutionIndexName() {
    return PipelineExecutionElasticSearchConstants.PMS_RUNNING_EXECUTIONS_INDEX;
  }

  @Override
  public Result index(PipelineSearchExecutionSummaryDTO pipelineSearchExecutionSummaryDTO, String indexName) {
    try {
      IndexRequest<PipelineSearchExecutionSummaryDTO> request = IndexRequest.of(i
          -> i.index(indexName)
                 .id(pipelineSearchExecutionSummaryDTO.getUuid())
                 .routing(pipelineSearchExecutionSummaryDTO.getAccountId())
                 .document(pipelineSearchExecutionSummaryDTO));

      IndexResponse response = elasticsearchClient.index(request);
      return response.result();
    } catch (IOException | ElasticsearchException ex) {
      throw new InternalServerErrorException(String.format("[ELASTIC_SEARCH]: Could not save execution for uuid: %s",
                                                 pipelineSearchExecutionSummaryDTO.getUuid()),
          ex);
    }
  }

  @Override
  public Result updateRecord(
          PipelineSearchExecutionSummaryDTO pipelineSearchExecutionSummaryDTO, String indexName) {
    try {
      UpdateResponse response = elasticsearchClient.updateRecord(u
                      -> u.index(indexName)
                      .id(pipelineSearchExecutionSummaryDTO.getUuid())
                      .routing(pipelineSearchExecutionSummaryDTO.getAccountId())
                      .doc(pipelineSearchExecutionSummaryDTO),
              PipelineSearchExecutionSummaryDTO.class, null);
      return response.result();
    } catch (IOException ex) {
      throw new InternalServerErrorException(
              String.format(UPDATE_EXECUTION_ERROR_MESSAGE,
                      pipelineSearchExecutionSummaryDTO.getUuid()),
              ex);
    } catch (ElasticsearchException ex) {
      if (ex.getMessage().contains(DOCUMENT_MISSING_EXCEPTION)) {
        // Elastic throws an exception in case the document doesn't exist, but in our case it can happen if
        // A completed execution summary gets updated again, so we are handling that case here
        // And we won't push the data to the completed execution index again
        return Result.NotFound;
      }
      throw new InternalServerErrorException(
              String.format(UPDATE_EXECUTION_ERROR_MESSAGE,
                      pipelineSearchExecutionSummaryDTO.getUuid()),
              ex);
    }
  }

  @Override
  public Result updateIsDeleted(
      PipelineSearchExecutionSummaryDTO pipelineSearchExecutionSummaryDTO, String indexName, boolean isDeleted) {
    try {
      UpdateResponse response = elasticsearchClient.updateRecord(u
          -> u.index(indexName)
                 .id(pipelineSearchExecutionSummaryDTO.getUuid())
                 .routing(pipelineSearchExecutionSummaryDTO.getAccountId())
                 .doc(PipelineSearchExecutionSummaryDTO.builder().isDeleted(isDeleted).build()),
          PipelineSearchExecutionSummaryDTO.class, DOCUMENT_MISSING_EXCEPTION);
      return response.result();
    } catch (IOException ex) {
      throw new InternalServerErrorException(
          String.format("[ELASTIC_SEARCH]: Could not update isDeleted field for execution uuid: %s",
              pipelineSearchExecutionSummaryDTO.getUuid()),
          ex);
    } catch (ElasticsearchException ex) {
      if (ex.getMessage().contains(DOCUMENT_MISSING_EXCEPTION)) {
        // Elastic throws an exception in case the document doesn't exist, but in our case it can happen if
        // A completed execution summary gets updated again, so we are handling that case here
        // And we won't push the data to the completed execution index again
        return Result.NotFound;
      }
      throw new InternalServerErrorException(
          String.format("[ELASTIC_SEARCH]: Could not update isDeleted field for execution uuid: %s",
              pipelineSearchExecutionSummaryDTO.getUuid()),
          ex);
    }
  }

  @Override
  public void save(PipelineExecutionSummaryEntity entity) {
    if (!elasticSearchDBConfig.isEnabled()) {
      return;
    }
    try {
      List<String> indexNames = getAllWriteIndexNames(entity.getAccountId(), entity.getStatus().getEngineStatus());
      PipelineSearchExecutionSummaryDTO executionSummaryDTO =
              PipelineSearchExecutionSummaryDTOMapper.toSearchEntity(entity, true);
      indexNames.forEach(index -> index(executionSummaryDTO, index));
    }
    catch (Exception e) {
      log.error(String.format("[ELASTIC_SEARCH]: Error while handling event for id: %s", entity.getUuid()), e);
    }
  }

  @Override
  public void update(PipelineExecutionSummaryEntity entity) {
    if (!elasticSearchDBConfig.isEnabled()) {
      return;
    }
    try {
      List<String> indexNames = getAllWriteIndexNames(entity.getAccountId(), entity.getStatus().getEngineStatus());
      PipelineSearchExecutionSummaryDTO executionSummaryDTO =
              PipelineSearchExecutionSummaryDTOMapper.toSearchEntity(entity, false);
      if (StatusUtils.isFinalStatus(entity.getStatus().getEngineStatus())) {

        /*
         * The below method updates the isDeleted as true in the running index to mark the execution as complete
         * Which will then be deleted async by another cron job, this also returns 2 different status apart from Updated i.e.:
         * No-Op: Document was already updated, can happen if graph update event is processed again
         * NotFound: Document was already updated and then deleted. Or this can also happen if there were some executions
         *           in running state before elastic was enabled in the cluster and ended after it
         */
        updateIsDeleted(executionSummaryDTO, getRunningExecutionIndexName(), true);
        List<String> indexesToInsert = new ArrayList<>();
        List<String> indexesToUpdate = new ArrayList<>();
        for(String indexName: indexNames) {
            Hit<PipelineSearchReadExecutionSummaryDTO> record = fetchByPlanExecutionId(
                executionSummaryDTO.getAccountId(), indexName, executionSummaryDTO.getPlanExecutionId());
            if (record == null) {
              indexesToInsert.add(indexName);
            } else {
              // Updating record in the index in which the record is already present instead of ignoring the update
              log.warn(String.format("[ELASTIC_SEARCH]: ExecutionID: %s updating in index: %s",
                  executionSummaryDTO.getPlanExecutionId(), record.index()));
              indexesToUpdate.add(record.index());
            }
          }
          // below is done as we are saving for first time in completed index
          executionSummaryDTO.setIsDeleted(false);
          indexesToInsert.forEach(index -> index(executionSummaryDTO, index));
          indexesToUpdate.forEach(index -> updateRecord(executionSummaryDTO, index));
      }
      else {
        // below is update operation which will only be done for running index
        indexNames.forEach(index -> updateRecord(executionSummaryDTO, index));
      }
    }
    catch (Exception e) {
      log.error(String.format("[ELASTIC_SEARCH]: Error while handling event for id: %s", entity.getUuid()), e);
    }
  }

  protected Hit<PipelineSearchReadExecutionSummaryDTO> fetchByPlanExecutionId(
      String accountIdentifier, String indexName, String planExecutionId) {
    try {
      Query query = ElasticSearchQueryBuilder.buildNestedQuery(CONSTANT_SCORE, null,
          ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
              EQUALS, PipelineSearchExecutionSummaryDTOKeys.planExecutionId, planExecutionId));
      SearchRequest searchRequest =
          new SearchRequest.Builder()
              .index(indexName)
              .query(query)
              .routing(accountIdentifier)
              .source(source
                  -> source.filter(filter -> filter.includes(PipelineSearchExecutionSummaryDTOKeys.planExecutionId)))
              .size(1)
              .build();

      SearchResponse<PipelineSearchReadExecutionSummaryDTO> searchResponse =
          elasticsearchClient.search(searchRequest, PipelineSearchReadExecutionSummaryDTO.class);
      if (isNotEmpty(searchResponse.hits().hits())) {
        return searchResponse.hits().hits().get(0);
      } else {
        return null;
      }
    } catch (Exception e) {
      log.error("[ELASTIC_SEARCH]: Failed to fetch execution id: {}, from index: {} for account: {}", planExecutionId,
          indexName, accountIdentifier, e);
      return null;
    }
  }

  private boolean checkIfPlanExecutionIdExists(String accountIdentifier, String indexName, String planExecutionId) {
    try {
      Query query = ElasticSearchQueryBuilder.buildNestedQuery(CONSTANT_SCORE, null,
          ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
              EQUALS, PipelineSearchExecutionSummaryDTOKeys.planExecutionId, planExecutionId));
      SearchRequest searchRequest =
          new SearchRequest.Builder()
              .index(indexName)
              .query(query)
              .routing(accountIdentifier)
              .source(source
                  -> source.filter(filter -> filter.includes(PipelineSearchExecutionSummaryDTOKeys.planExecutionId)))
              .size(1)
              .build();

      SearchResponse<PipelineSearchReadExecutionSummaryDTO> searchResponse =
          elasticsearchClient.search(searchRequest, PipelineSearchReadExecutionSummaryDTO.class);
      return searchResponse.hits().total().value() > 0;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public void syncCompletedExecutionsToElastic(PipelineExecutionSummaryEntity entity) {
    if (!elasticSearchDBConfig.isEnabled()) {
      return;
    }
    if (StatusUtils.isFinalStatus(entity.getStatus().getEngineStatus())) {
      List<String> indexNames = getAllWriteIndexNames(entity.getAccountId(), entity.getStatus().getEngineStatus());
      PipelineSearchExecutionSummaryDTO executionSummaryDTO =
          PipelineSearchExecutionSummaryDTOMapper.toSearchEntity(entity, true);
      indexNames.forEach(index -> index(executionSummaryDTO, index));
    }
  }

  @Override
  public void updateCompletedExecutionsToElastic(PipelineExecutionSummaryEntity entity) {
    if (!elasticSearchDBConfig.isEnabled()) {
      return;
    }
    if (StatusUtils.isFinalStatus(entity.getStatus().getEngineStatus())) {
      List<String> indexNames = findIndexesForPlanExecutionID(entity);
      PipelineSearchExecutionSummaryDTO executionSummaryDTO =
          PipelineSearchExecutionSummaryDTOMapper.toSearchEntity(entity, true);
      indexNames.forEach(index -> updateRecord(executionSummaryDTO, index));
    }
  }

  private List<String> findIndexesForPlanExecutionID(PipelineExecutionSummaryEntity entity) {
    List<String> indexes = getAllWriteIndexNames(entity.getAccountId(), entity.getStatus().getEngineStatus());
    Query query = getQueryForPlanExecutionIDAndAccountID(entity.getAccountId(), entity.getPlanExecutionId());
    List<Hit<PipelineSearchReadExecutionSummaryDTO>> hits =
        getHitsForQuery(query, indexes, PipelineExecutionElasticSearchConstants.PIPELINE_SEARCH_MAX_BATCH_SIZE);
    Set<String> actualIndexesToUpdate = new HashSet<>();
    for (Hit hit : hits) {
      if (!isNull(hit)) {
        actualIndexesToUpdate.add(hit.index());
      }
    }
    return new ArrayList<>(actualIndexesToUpdate);
  }

  private Query getQueryForPlanExecutionIDAndAccountID(String accountIdentifier, String planExecutionId) {
    List<Query> queries = new ArrayList<>();
    queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.accountId, accountIdentifier));
    queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.planExecutionId, planExecutionId));
    return ElasticSearchQueryBuilder.buildNestedQuery(
        CONSTANT_SCORE, null, ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, queries));
  }

  private List<Hit<PipelineSearchReadExecutionSummaryDTO>> getHitsForQuery(
      Query query, List<String> indexNames, int size) {
    SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(indexNames)
            .source(source
                -> source.filter(filter -> filter.includes(PipelineSearchExecutionSummaryDTOKeys.planExecutionId)))
            .query(query)
            .size(size)
            .build();
    try {
      SearchResponse<PipelineSearchReadExecutionSummaryDTO> searchResponse =
          elasticsearchClient.search(searchRequest, PipelineSearchReadExecutionSummaryDTO.class);
      return searchResponse.hits().hits();
    } catch (IOException ex) {
      throw new InvalidRequestException("[ELASTIC_SEARCH]: Could not fetch pipeline summary executions", ex);
    }
  }

  @Override
  public boolean shouldSyncToElastic(Update updateOps) {
    Set<String> fieldsUpdated = new HashSet<>();
    if (updateOps.getUpdateObject().containsKey("$set")) {
      fieldsUpdated.addAll(((Document) updateOps.getUpdateObject().get("$set")).keySet());
    }
    if (updateOps.getUpdateObject().containsKey("$addToSet")) {
      fieldsUpdated.addAll(((Document) updateOps.getUpdateObject().get("$addToSet")).keySet());
    }
    return fieldsUpdated.stream().anyMatch(ELASTIC_SEARCH_FIELDS_LEAF_FIELDS::contains)
        || fieldsUpdated.stream().anyMatch(field -> ELASTIC_SEARCH_PARENT_FIELDS.stream().anyMatch(field::startsWith));
  }

  @Override
  public boolean shouldFetchDocumentFromPrimary(Update updateOps) {
    Set<String> fieldsUpdated = new HashSet<>();
    if (updateOps.getUpdateObject().containsKey("$set")) {
      fieldsUpdated.addAll(((Document) updateOps.getUpdateObject().get("$set")).keySet());
    }
    if (updateOps.getUpdateObject().containsKey("$addToSet")) {
      fieldsUpdated.addAll(((Document) updateOps.getUpdateObject().get("$addToSet")).keySet());
    }
    return fieldsUpdated.stream().anyMatch(
        field -> ELASTIC_SEARCH_RACE_CONDITION_FIELDS.stream().anyMatch(field::startsWith));
  }

  @Override
  public List<String> getAllIndexNames(String accountIdentifier) {
    List<String> indexes = new ArrayList<>();
    indexes.add(getRunningExecutionIndexName());
    Optional<SearchSettings> searchSettingsOptional = pipelineRetentionService.getSearchSettings(accountIdentifier);
    if (searchSettingsOptional.isPresent()) {
      SearchSettings searchSettings = searchSettingsOptional.get();
      if (PipelineSearchMigrationStatus.COMPLETE.equals(searchSettings.getIndexMigrationStatus())) {
        indexes.add(searchSettings.getNewIndexName());
      } else {
        indexes.add(searchSettings.getOldIndexName());
      }
    } else {
      indexes.add(PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ALIAS_6_MONTH);
    }
    return indexes;
  }

  @Override
  public DeleteByQueryResponse deleteExecutions(Set<String> planExecutionIDs, String accountId) {
    if (!elasticSearchDBConfig.isEnabled()) {
      return null;
    }
    try {
      List<String> planExecutionIds = new ArrayList<>(planExecutionIDs);
      Query query = ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
          EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.planExecutionId, planExecutionIds);
      List<String> indexNames = new ArrayList<>(getCompletedExecutionIndexNames(accountId));
      indexNames.add(getRunningExecutionIndexName());
      return elasticsearchClient.deleteRecords(d -> d.index(indexNames).query(query));
    } catch (Exception e) {
      log.error(String.format("[ELASTIC_SEARCH]: Error while deleting documents for account: %s", accountId), e);
      throw new InternalServerErrorException(
          String.format("[ELASTIC_SEARCH]: Error while deleting documents for account: %s", accountId), e);
    }
  }

  @Override
  public String deleteCompletedExecutions() {
    if (!elasticSearchDBConfig.isEnabled()) {
      return null;
    }
    try {
      Query query = ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.isDeleted, true);
      // We are deleting maximum of 50k docs per deletion request, creating it as an async task
      // Also on conflict we are proceeding, this might happen in case 2 parallel delete tasks spin up parallel
      // Also 1 request per second mean that elastic will process max of 1k docs per sec(1 batch per sec)
      DeleteByQueryResponse response = elasticsearchClient.deleteRecords(d
          -> d.index(getRunningExecutionIndexName())
                 .query(query)
                 .maxDocs(MAX_RUNNING_DOCS_TO_DELETE)
                 .waitForCompletion(false)
                 .conflicts(Conflicts.Proceed));
      return response.task();
    } catch (Exception e) {
      log.error("[ELASTIC_SEARCH]: Error while deleting completed executions from running index", e);
      throw new InternalServerErrorException(
          "[ELASTIC_SEARCH]: Error while deleting completed executions from running index", e);
    }
  }

  @Override
  public String deleteExpiredExecutions(String accountIdentifier, int retentionPeriodInMonths) {
    if (!elasticSearchDBConfig.isEnabled()) {
      return null;
    }
    List<String> indexNames = getCompletedExecutionIndexNames(accountIdentifier);
    Query deleteQuery = getQueryForExpiredExecutions(List.of(accountIdentifier), retentionPeriodInMonths);
    return deleteExecutions(List.of(accountIdentifier), indexNames, deleteQuery);
  }

  @Override
  public String deleteExpiredExecutionsForDefaultRetentionPeriod(
      List<String> accountIdentifiers, int retentionPeriodInMonths) {
    if (!elasticSearchDBConfig.isEnabled()) {
      return null;
    }
    List<String> indexNames = getCompletedExecutionIndexNames(null);
    Query deleteQuery = getQueryForExpiredExecutions(accountIdentifiers, retentionPeriodInMonths);
    return deleteExecutions(accountIdentifiers, indexNames, deleteQuery);
  }

  private String deleteExecutions(List<String> accountIdentifiers, List<String> indexNames, Query deleteQuery) {
    try {
      // We are deleting maximum of 50k docs per deletion request, creating it as an async task
      // Also on conflict we are proceeding, this might happen in case 2 parallel delete tasks spin up parallel
      // Also 1 request per second mean that elastic will process max of 1k docs per sec(1 batch per sec)
      DeleteByQueryResponse response = elasticsearchClient.deleteRecords(
          d -> d.index(indexNames).query(deleteQuery).maxDocs(MAX_DOCS_TO_DELETE).waitForCompletion(false));
      return response.task();
    } catch (Exception e) {
      log.error(String.format(
                    "[ELASTIC_SEARCH]: Error while deleting expired executions for accounts: %s", accountIdentifiers),
          e);
      throw new InternalServerErrorException(
          String.format(
              "[ELASTIC_SEARCH]: Error while deleting expired executions for accounts: %s", accountIdentifiers),
          e);
    }
  }

  private Query getQueryForExpiredExecutions(List<String> accountIdentifiers, int retentionPeriodInMonths) {
    long ttl = DateTime.now().minusMonths(retentionPeriodInMonths).getMillis();
    List<Query> queries = new ArrayList<>();
    if (accountIdentifiers.size() == 1) {
      queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.accountId, accountIdentifiers.get(0)));
    } else {
      queries.add(ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
          EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.accountId, accountIdentifiers));
    }
    queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        LESS_THAN, PipelineSearchExecutionSummaryDTOKeys.endTs, ttl));
    return ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, queries);
  }

  private void validatePageRequest(Pageable pageable) {
    if (((int) pageable.getOffset() + pageable.getPageSize()) > PAGINATION_MAX_DOCS_LIMIT) {
      throw new InvalidRequestException(
          String.format("Please add more filters to page through more than %d records", PAGINATION_MAX_DOCS_LIMIT));
    }
  }

  @Override
  public Page<String> listExecutions(String accountId, Pageable pageable, Query query) {
    validatePageRequest(pageable);
    SearchRequest.Builder searchRequestBuilder =
        new SearchRequest.Builder()
            .index(getAllIndexNames(accountId))
            .source(source
                -> source.filter(filter -> filter.includes(PipelineSearchExecutionSummaryDTOKeys.planExecutionId)))
            .query(query)
            .trackTotalHits(TrackHits.of(th -> th.enabled(true)))
            .from((int) pageable.getOffset())
            .size(pageable.getPageSize());
    // Track total hits is to track the total count otherwise it limits to 10k max.(based on the index settings)

    if (pageable.getSort().isSorted()) {
      for (Sort.Order order : pageable.getSort()) {
        searchRequestBuilder.sort(s
            -> s.field(f
                -> f.field(PipelineSearchUtils.getSearchSortFieldMapping(order.getProperty()))
                       .order(order.getDirection() == Sort.Direction.ASC ? SortOrder.Asc : SortOrder.Desc)));
        /*
         * Using mongoDB it sorts based on the index being used by the query
         * For e.g. IF it uses
         * accountId_parentUniqueId_status_startTs_repo_branch_pipelineIds_modules_parent_info_range_idx then it
         * sorts by startTs automatically after status, but elastic doesn't have something similar So we add a
         * second sort field
         */
        if (!PlanExecutionSummaryKeys.startTs.equals(order.getProperty())) {
          searchRequestBuilder.sort(
              s -> s.field(f -> f.field(PipelineSearchExecutionSummaryDTOKeys.startTs).order(SortOrder.Desc)));
        }
      }
    }

    try {
      SearchResponse<PipelineSearchReadExecutionSummaryDTO> searchResponse =
          elasticsearchClient.search(searchRequestBuilder.build(), PipelineSearchReadExecutionSummaryDTO.class);
      List<String> results = searchResponse.hits().hits().stream().map(h -> h.source().getPlanExecutionId()).toList();
      long totalElements = searchResponse.hits().total().value();

      return new PageImpl<>(results, pageable, totalElements);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not perform this operation", e);
    }
  }

  @Override
  public List<String> listExecutionsFromIndex(Query query, String indexName, int size) {
    SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(indexName)
            .source(source
                -> source.filter(filter -> filter.includes(PipelineSearchExecutionSummaryDTOKeys.planExecutionId)))
            .query(query)
            .sort(s -> s.field(f -> f.field(PipelineSearchExecutionSummaryDTOKeys.endTs).order(SortOrder.Asc)))
            .size(size)
            .build();

    try {
      SearchResponse<PipelineSearchReadExecutionSummaryDTO> searchResponse =
          elasticsearchClient.search(searchRequest, PipelineSearchReadExecutionSummaryDTO.class);
      return searchResponse.hits().hits().stream().map(h -> h.source().getPlanExecutionId()).toList();
    } catch (IOException e) {
      throw new InvalidRequestException("Could not perform this operation", e);
    }
  }

  @Override
  public void createIndexAlias(String accountIdentifier, PipelineSearchIndexRetentionPeriods indexRetentionPeriod) {
    String indexName = indexRetentionPeriod.getIndexName(accountIdentifier);
    String indexTemplateName = indexRetentionPeriod.getIndexTemplateName(accountIdentifier);
    try {
      IndexSettingsLifecycle indexSettingsLifecycle = new IndexSettingsLifecycle.Builder()
                                                          .name(indexRetentionPeriod.getPolicyName())
                                                          .rolloverAlias(indexName)
                                                          .build();

      IndexTemplateMapping indexTemplateMapping =
          new IndexTemplateMapping.Builder()
              .mappings(ElasticSearchUtils.getTypeMappingFromFile(PMS_EXECUTION_ENTITY_MAPPINGS_JSON_FILE_PATH, true))
              .settings(new IndexSettings.Builder().lifecycle(indexSettingsLifecycle).build())
              .build();

      PutIndexTemplateRequest putIndexTemplateRequest =
          new PutIndexTemplateRequest.Builder()
              .name(indexTemplateName)
              .template(indexTemplateMapping)
              .indexPatterns(List.of(indexRetentionPeriod.getIndexPatterns(accountIdentifier)))
              .build();

      PutIndexTemplateResponse putIndexTemplateResponse = elasticsearchClient.putIndexTemplate(putIndexTemplateRequest);
      if (!putIndexTemplateResponse.acknowledged()) {
        throw new InternalServerErrorException(String.format(
            "[ELASTIC_SEARCH]: Could not create the index template %s for account id: %s in elasticsearch", indexName,
            accountIdentifier));
      }
      log.info(String.format(
          "[ELASTIC_SEARCH]: New index template: %s created for account: %s", indexTemplateName, accountIdentifier));
      CreateIndexRequest request = CreateIndexRequest.of(c
          -> c.index(indexRetentionPeriod.getFirstIndexName(accountIdentifier))
                 .aliases(indexName, Alias.of(a -> a.isWriteIndex(true))));
      CreateIndexResponse createIndexResponse = elasticsearchClient.createIndex(request);
      if (!createIndexResponse.acknowledged()) {
        throw new InternalServerErrorException(String.format(
            "[ELASTIC_SEARCH]: Could not start the index rollover for the alias %s for account id: %s in elasticsearch",
            indexName, accountIdentifier));
      }
      log.info(String.format("[ELASTIC_SEARCH]: New index: %s created for account: %s",
          indexRetentionPeriod.getFirstIndexName(accountIdentifier), accountIdentifier));
    } catch (Exception ex) {
      throw new InternalServerErrorException(
          String.format("[ELASTIC_SEARCH]: Could not create the index alias %s for account id: %s in elasticsearch",
              indexName, accountIdentifier),
          ex);
    }
  }

  @Override
  public void updateIndexAlias(
      String accountIdentifier, String fieldName, PipelineSearchIndexRetentionPeriods indexRetentionPeriod) {
    updateIndexAlias(accountIdentifier, fieldName, indexRetentionPeriod, PipelineSearchFieldType.KEYWORD);
  }

  @Override
  public void updateIndexAlias(String accountIdentifier, String fieldName,
      PipelineSearchIndexRetentionPeriods indexRetentionPeriod, PipelineSearchFieldType fieldType) {
    String indexName = indexRetentionPeriod.getIndexName(accountIdentifier);
    String indexTemplateName = indexRetentionPeriod.getIndexTemplateName(accountIdentifier);
    try {
      IndexSettingsLifecycle indexSettingsLifecycle = new IndexSettingsLifecycle.Builder()
                                                          .name(indexRetentionPeriod.getPolicyName())
                                                          .rolloverAlias(indexName)
                                                          .build();
      IndexTemplateMapping indexTemplateMapping =
          new IndexTemplateMapping.Builder()
              .mappings(ElasticSearchUtils.getTypeMappingFromFile(PMS_EXECUTION_ENTITY_MAPPINGS_JSON_FILE_PATH, true))
              .settings(new IndexSettings.Builder().lifecycle(indexSettingsLifecycle).build())
              .build();
      PutIndexTemplateRequest putIndexTemplateRequest =
          new PutIndexTemplateRequest.Builder()
              .name(indexTemplateName)
              .template(indexTemplateMapping)
              .indexPatterns(List.of(indexRetentionPeriod.getIndexPatterns(accountIdentifier)))
              .build();
      PutIndexTemplateResponse putIndexTemplateResponse = elasticsearchClient.putIndexTemplate(putIndexTemplateRequest);
      if (!putIndexTemplateResponse.acknowledged()) {
        throw new InternalServerErrorException(String.format(
            "[ELASTIC_SEARCH]: Could not update the index template %s for account id: %s in elasticsearch",
            indexTemplateName, accountIdentifier));
      }
      log.info(String.format(
          "[ELASTIC_SEARCH]: Index template: %s updated for account: %s", indexTemplateName, accountIdentifier));

      PutMappingRequest putMappingRequest = fieldType == PipelineSearchFieldType.DATE
          ? PipelineSearchUtils.buildPutMappingRequestForDateType(indexName, fieldName)
          : PipelineSearchUtils.buildPutMappingRequestForKeywordType(indexName, fieldName);
      PutMappingResponse response = elasticsearchClient.putMapping(putMappingRequest);
      if (!response.acknowledged()) {
        throw new InternalServerErrorException(
            String.format("[ELASTIC_SEARCH]: Could not update the index for the alias %s with the fieldName %s for "
                    + "account id: %s in elasticsearch",
                indexName, fieldName, accountIdentifier));
      }
      log.info(String.format("[ELASTIC_SEARCH]: Index alias: %s updated with fieldName: %s account: %s", indexName,
          fieldName, accountIdentifier));
    } catch (Exception ex) {
      throw new InternalServerErrorException(String.format("[ELASTIC_SEARCH]: Could not update the index alias %s for "
                                                     + "account id: %s for the fieldName: %s in elasticsearch",
                                                 indexName, accountIdentifier, fieldName),
          ex);
    }
  }

  @Override
  public ReindexResponse reIndexDocuments(
      String accountIdentifier, String oldIndexName, String newIndexName, Query query) {
    try {
      return elasticsearchClient.reindex(new ReindexRequest.Builder()
                                             .source(s -> s.index(oldIndexName).query(query))
                                             .dest(d -> d.index(newIndexName))
                                             .waitForCompletion(false)
                                             .build());
    } catch (IOException e) {
      throw new InternalServerErrorException(
          String.format(
              "[ELASTIC_SEARCH]: Error while reindexing documents for account: %s from index: %s to new index: %s",
              accountIdentifier, oldIndexName, newIndexName),
          e);
    }
  }

  @Override
  public Optional<Long> fetchFirstExecutionEndTs() {
    SearchRequest searchRequest =
        new SearchRequest.Builder()
            .index(getAllIndexNames(null))
            .source(source -> source.filter(filter -> filter.includes(PipelineSearchExecutionSummaryDTOKeys.endTs)))
            .size(1)
            .sort(s -> s.field(f -> f.field(PipelineSearchExecutionSummaryDTOKeys.endTs).order(SortOrder.Asc)))
            .build();

    try {
      SearchResponse<PipelineSearchReadExecutionSummaryDTO> searchResponse =
          elasticsearchClient.search(searchRequest, PipelineSearchReadExecutionSummaryDTO.class);
      return searchResponse.hits().hits().stream().map(h -> h.source().getEndTs()).filter(Objects::nonNull).findFirst();
    } catch (IOException e) {
      throw new InvalidRequestException("Could not perform this operation", e);
    }
  }

  @Override
  public boolean checkIfPlanExecutionIDExists(PipelineExecutionSummaryEntity entity) {
    List<String> indexes = getAllWriteIndexNames(entity.getAccountId(), entity.getStatus().getEngineStatus());
    for (String indexName : indexes) {
      boolean recordExists =
          checkIfPlanExecutionIdExists(entity.getAccountId(), indexName, entity.getPlanExecutionId());
      if (recordExists) {
        return true;
      }
    }
    return false;
  }

  private SearchRequest.Builder getSearchRequestBuilder(String accountIdentifier, Query query,
      Set<String> fieldsToInclude, Map<String, SortOrder> sortingFields, Integer size) {
    SearchRequest.Builder searchRequestBuilder =
        new SearchRequest.Builder()
            .query(query)
            .index(getAllIndexNames(accountIdentifier))
            .size(PipelineExecutionElasticSearchConstants.PIPELINE_SEARCH_MAX_BATCH_SIZE);
    if (isNotEmpty(fieldsToInclude)) {
      // validate if fieldsToInclude do exists in PipelineSearchExecutionSummaryDTO
      PipelineSearchUtils.validateFieldsToInclude(fieldsToInclude);
      searchRequestBuilder.source(
          source -> source.filter(filter -> filter.includes(fieldsToInclude.stream().toList())));
    }
    if (isNotEmpty(sortingFields)) {
      searchRequestBuilder.sort(PipelineSearchUtils.getSortOptions(sortingFields));
    } else {
      searchRequestBuilder.sort(
          PipelineSearchUtils.getSortOptions(Map.of(PipelineSearchExecutionSummaryDTOKeys.createdAt, SortOrder.Desc)));
    }
    // validating batch size
    if (size != null) {
      PipelineSearchUtils.validateSearchBatchSize(size);
      searchRequestBuilder.size(size);
    }
    return searchRequestBuilder;
  }

  private SearchRequest getSearchRequest(String accountIdentifier, Query query, Set<String> fieldsToInclude,
      Map<String, SortOrder> sortingFields, Integer size) {
    return getSearchRequestBuilder(accountIdentifier, query, fieldsToInclude, sortingFields, size).build();
  }

  private SearchRequest getSearchRequestWithSearchAfter(String accountIdentifier, Query query,
      Set<String> fieldsToInclude, Map<String, SortOrder> sortingFields, List<FieldValue> lastSortValues,
      Integer size) {
    SearchRequest.Builder searchRequestBuilder =
        getSearchRequestBuilder(accountIdentifier, query, fieldsToInclude, sortingFields, size);
    if (isNotEmpty(lastSortValues)) {
      searchRequestBuilder.searchAfter(lastSortValues);
    }
    return searchRequestBuilder.build();
  }

  @Override
  public Query formQueryForRootExecutionId(String accountIdentifier, String rootExecutionId) {
    // validating query params
    PipelineSearchUtils.validateQueryParamsForRootExecutionId(accountIdentifier, rootExecutionId);
    // Passing null to include all entries under the account scope
    List<Query> matchQueries = PipelineSearchHelper.getScopeQuery(accountIdentifier, null);
    matchQueries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.rootExecutionId, rootExecutionId));
    return ElasticSearchQueryBuilder.buildNestedQuery(
        CONSTANT_SCORE, null, ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, matchQueries));
  }

  @Override
  public PipelineSearchReadExecutionSummaryDTO fetchLatestExecutionUsingRootParentId(
      String accountIdentifier, String rootParentId) {
    Query query = formQueryForRootExecutionId(accountIdentifier, rootParentId);
    List<PipelineSearchReadExecutionSummaryDTO> pipelineSearchReadExecutionSummaryDTOList =
        listExecutions(accountIdentifier, query, null, 1);
    if (isNotEmpty(pipelineSearchReadExecutionSummaryDTOList)) {
      return pipelineSearchReadExecutionSummaryDTOList.get(0);
    }
    return null;
  }

  @Override
  public List<PipelineSearchReadExecutionSummaryDTO> listExecutions(
      String accountIdentifier, Query query, Map<String, SortOrder> sortingFields, Integer size) {
    SearchRequest searchRequest = getSearchRequest(
        accountIdentifier, query, PIPELINE_SEARCH_READ_EXECUTION_SUMMARY_DTO_ALL_FIELDS, sortingFields, size);
    try {
      SearchResponse<PipelineSearchReadExecutionSummaryDTO> searchResponse =
          elasticsearchClient.search(searchRequest, PipelineSearchReadExecutionSummaryDTO.class);
      return searchResponse.hits().hits().stream().map(h -> h.source()).toList();
    } catch (IOException ex) {
      throw new InvalidRequestException("[ELASTIC_SEARCH]: Could not fetch pipeline summary executions", ex);
    }
  }

  @Override
  public List<String> listExecutionIdsWithSearchAfter(String accountIdentifier, Query query,
      Map<String, SortOrder> sortingFields, List<Object> lastSortValues, Integer size) {
    SearchRequest searchRequest = getSearchRequestWithSearchAfter(accountIdentifier, query,
        Set.of(PipelineSearchExecutionSummaryDTOKeys.planExecutionId), sortingFields,
        PipelineSearchUtils.getFieldValueList(lastSortValues), size);
    try {
      SearchResponse<PipelineSearchReadExecutionSummaryDTO> searchResponse =
          elasticsearchClient.search(searchRequest, PipelineSearchReadExecutionSummaryDTO.class);
      return searchResponse.hits()
          .hits()
          .stream()
          .map(Hit::source)
          .map(PipelineSearchReadExecutionSummaryDTO::getPlanExecutionId)
          .toList();
    } catch (IOException ex) {
      throw new InvalidRequestException("[ELASTIC_SEARCH]: Could not fetch pipeline summary executions", ex);
    }
  }

  @Override
  public long getExecutionSummariesCount(String accountIdentifier, Query query) {
    if (!elasticSearchDBConfig.isEnabled()) {
      throw new InvalidRequestException(
          "[ELASTIC_SEARCH]: Could not fetch execution summaries count as elastic is not enabled");
    }
    CountRequest countRequest =
        new CountRequest.Builder().query(query).index(getAllIndexNames(accountIdentifier)).build();
    try {
      CountResponse countResponse = elasticsearchClient.count(countRequest);
      return countResponse.count();
    } catch (IOException ex) {
      throw new InvalidRequestException("[ELASTIC_SEARCH]: Could not fetch execution summaries count", ex);
    }
  }

  @Override
  public ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> fetchPipelineSearchReadExecutionSummaryDTO(
      String accountIdentifier, Query query, Set<String> fieldsToInclude) {
    Map<String, SortOrder> sortingFields =
        Map.of(PipelineSearchExecutionSummaryDTOKeys.planExecutionId, SortOrder.Desc);
    return fetchPipelineSearchReadExecutionSummaryDTO(accountIdentifier, query, fieldsToInclude, sortingFields);
  }

  @Override
  public ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> fetchPipelineSearchReadExecutionSummaryDTO(
      String accountIdentifier, Query query, Set<String> fieldsToInclude, Map<String, SortOrder> sortingFields) {
    SearchRequest searchRequest = getSearchRequest(accountIdentifier, query, fieldsToInclude, sortingFields, null);
    return new ElasticSearchStream<>(
        this.elasticsearchClient, searchRequest, PipelineSearchReadExecutionSummaryDTO.class);
  }
}
