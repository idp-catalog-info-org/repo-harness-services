/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.jobs;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.elasticsearch.framework.OperatorEnum.CONSTANT_SCORE;
import static io.harness.elasticsearch.framework.OperatorEnum.EQUALS;
import static io.harness.elasticsearch.framework.OperatorEnum.EQUALS_ANY;
import static io.harness.elasticsearch.framework.OperatorEnum.MUST_MATCH_ALL;
import static io.harness.elasticsearch.framework.OperatorEnum.RANGE_INCLUDING_ENDS;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.elasticsearch.utils.ElasticSearchQueryBuilder;
import io.harness.exception.InternalServerErrorException;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity;
import io.harness.search.entity.PipelineSearchIndexMigrationEntity.PipelineSearchIndexMigrationEntityKeys;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;
import io.harness.search.entity.beans.PipelineSearchMigrationStatus;
import io.harness.search.service.PipelineSearchIndexMigrationService;
import io.harness.search.service.PipelineSearchService;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import co.elastic.clients.elasticsearch.tasks.GetTasksResponse;
import co.elastic.clients.elasticsearch.tasks.TaskInfo;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Singleton
public class SearchIndexMigrationIterator
    extends IteratorLoopModeHandler implements Handler<PipelineSearchIndexMigrationEntity> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private Injector injector;
  @Nullable @Inject private ElasticSearchClient elasticsearchClient;
  @Inject private PipelineSearchService pipelineSearchService;
  @Inject PersistentLocker persistentLocker;
  @Inject private PipelineSearchIndexMigrationService indexMigrationService;
  @Inject private PipelineRetentionService pipelineRetentionService;
  private static final Long ELASTIC_INDEX_MIGRATION_DELTA_TIME = 10 * 60 * 1000L; // 10 minutes
  private Duration lockTimeout;
  private static final String LOCK_NAME = "SearchIndexMigrationIterator-%s";

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "SearchIndexMigrationIterator";
    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    // do nothing
    log.error("createAndStartIterator is not overridden");
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    if (targetInterval.compareTo(Duration.ofMinutes(10)) < 0) {
      log.error(String.format(
          "[ELASTIC_SEARCH]: SearchIndexMigrationIterator cannot have target interval less than 10 mins, configured targetInterval: %d mins. Taking it as default of 10 mins",
          targetInterval.toMinutes()));
      targetInterval = Duration.ofMinutes(10);
    }
    lockTimeout = targetInterval;
    iterator = (MongoPersistenceIterator<PipelineSearchIndexMigrationEntity, SpringFilterExpander>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       PipelineSearchIndexMigrationEntity.class,
                       MongoPersistenceIterator.<PipelineSearchIndexMigrationEntity, SpringFilterExpander>builder()
                           .clazz(PipelineSearchIndexMigrationEntity.class)
                           .fieldName(PipelineSearchIndexMigrationEntityKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofSeconds(10))
                           .acceptableExecutionTime(ofSeconds(10))
                           .handler(this)
                           .schedulingType(REGULAR)
                           .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
                           .filterExpander(q
                               -> q.addCriteria(where(PipelineSearchIndexMigrationEntityKeys.status)
                                                    .in(PipelineSearchMigrationStatus.NOT_STARTED,
                                                        PipelineSearchMigrationStatus.IN_PROGRESS))));
  }

  /*
   * Search Index Migration is required in-case an account changes their data retention period
   * As part of the search index migration we need to create a new index for the account and then create 2 re-index
   * tasks
   * 1. Re-index task by filtering on accountID - Copies data from old index to new index
   * 2. Buffer Sync Re-index task by filtering on missing planExecutionIDs -
   *        We will first fetch the planExecutionIDs from both old and new index which ended in the time range of
   *        of migrationStartTime+-5 mins, once we have the list we will compare and see if any of the IDs from old
   *        index is absent in the new one. If yes, we will spin up a new async re-index task to sync those IDs
   *        Otherwise we will mark the migration as complete
   *
   * More details here: https://harness.atlassian.net/wiki/spaces/CDNG/pages/21743763510/ElasticSearch+Indexing+Strategy
   */
  @Override
  public void handle(PipelineSearchIndexMigrationEntity entity) {
    if (elasticsearchClient == null) {
      log.error("[ELASTIC_SEARCH]: ElasticSearchClient is not initialized so skipping elastic index migration");
      throw new InternalServerErrorException(
          "[ELASTIC_SEARCH]: ElasticSearchClient is not initialized so skipping elastic index migration");
    }

    // Take a lock for below code with timeouts and print logs if lock is not acquired and also print some values from
    // entity
    try (AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(
             String.format(LOCK_NAME, entity.getUuid()), lockTimeout, Duration.ofSeconds(5))) {
      if (lock != null) {
        logEntityDetails(entity, "[INDEX_MIGRATION]: Lock acquired");
        if (entity.getStatus() == PipelineSearchMigrationStatus.NOT_STARTED) {
          // This status will be the first time the index migration record is created
          startIndexMigrationProcess(entity);
        } else if (entity.getStatus() == PipelineSearchMigrationStatus.IN_PROGRESS) {
          // This status will be after a re-index task has been started for elastic
          handleInProgressStatus(entity);
        } else {
          log.error(String.format("[ELASTIC_SEARCH]: Migration status: %s is not handled for migration id: %s",
              entity.getStatus(), entity.getUuid()));
          throw new InternalServerErrorException(
              String.format("[ELASTIC_SEARCH]: Migration status: %s is not handled for migration id: %s",
                  entity.getStatus(), entity.getUuid()));
        }
      } else {
        logEntityDetails(entity, "[INDEX_MIGRATION]: Failed to acquire lock");
      }
    }
  }

  private void logEntityDetails(PipelineSearchIndexMigrationEntity entity, String messagePrefix) {
    log.info(String.format(
        "[ELASTIC_SEARCH]: %s for account: %s, uuid: %s, nextIteration: %d, status: %s, elasticTaskID: %s, elasticBufferSyncTaskID: %s",
        messagePrefix, entity.getAccountIdentifier(), entity.getUuid(), entity.getNextIteration(), entity.getStatus(),
        entity.getElasticTaskID(), entity.getElasticBufferSyncTaskID()));
  }

  /*
   * This method will first create the new index alias for this account
   * It will then spin up a new re-index task to copy documents for this account from old index to new index by
   * filtering on the same accountID, and will save this taskID in the PipelineSearchIndexMigrationEntity to check for
   * the status in the nextIteration.
   */
  private void startIndexMigrationProcess(PipelineSearchIndexMigrationEntity entity) {
    String accountIdentifier = entity.getAccountIdentifier();
    String oldIndexName = entity.getOldIndexRetentionPeriod().getIndexName(accountIdentifier);
    String newIndexName = entity.getNewIndexRetentionPeriod().getIndexName(accountIdentifier);
    try {
      pipelineSearchService.createIndexAlias(accountIdentifier, entity.getNewIndexRetentionPeriod());
      Query query = ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL,
          List.of(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
              EQUALS, PipelineSearchExecutionSummaryDTOKeys.accountId, accountIdentifier)));
      ReindexResponse reindexResponse =
          pipelineSearchService.reIndexDocuments(accountIdentifier, oldIndexName, newIndexName, query);
      log.info(
          String.format("[ELASTIC_SEARCH]: Index migration reindexing task created with task id: %s for account: %s",
              reindexResponse.task(), accountIdentifier));
      Update update = new Update();
      update.set(PipelineSearchIndexMigrationEntityKeys.elasticTaskID, reindexResponse.task());
      updateMigrationStatus(accountIdentifier, entity.getUuid(), update, PipelineSearchMigrationStatus.IN_PROGRESS,
          oldIndexName, newIndexName);
    } catch (Exception ex) {
      handleFailedMigration(oldIndexName, newIndexName, entity);
      log.error(
          String.format("[ELASTIC_SEARCH]: Failed while migrating the account: %s from index: %s to new index: %s",
              accountIdentifier, oldIndexName, newIndexName),
          ex);
      throw new InternalServerErrorException(
          String.format("[ELASTIC_SEARCH]: Failed while migrating the account: %s from index: %s to new index: %s",
              accountIdentifier, oldIndexName, newIndexName),
          ex);
    }
  }

  private void updateMigrationStatus(String accountIdentifier, String uuid, Update update,
      PipelineSearchMigrationStatus migrationStatus, String oldIndexName, String newIndexName) {
    if (update == null) {
      update = new Update();
    }
    update.set(PipelineSearchIndexMigrationEntityKeys.status, migrationStatus);
    indexMigrationService.update(uuid, update);
    pipelineRetentionService.updateSearchIndexMigrationDetails(
        accountIdentifier, migrationStatus, oldIndexName, newIndexName);
  }

  /*
   * This method will handle the case in which an index migration failed and will update the
   * PipelineSearchIndexMigrationEntity accordingly
   */
  private void handleFailedMigration(
      String oldIndexName, String newIndexName, PipelineSearchIndexMigrationEntity entity) {
    try {
      updateMigrationStatus(entity.getAccountIdentifier(), entity.getUuid(), null, PipelineSearchMigrationStatus.FAILED,
          oldIndexName, newIndexName);
    } catch (Exception ex) {
      log.error(
          String.format(
              "[ELASTIC_SEARCH]: Failed while updating the migration status as failed for account: %s from index: %s to new index: %s",
              entity.getAccountIdentifier(), oldIndexName, newIndexName),
          ex);
    }
  }

  /*
   * This method will:
   * 1. First check if the buffer sync taskID is not null, this is because after this the migration is complete
   * 2. If buffer sync taskID is null but taskId is not null, then we need to start the buffer sync task
   */
  private void handleInProgressStatus(PipelineSearchIndexMigrationEntity entity) {
    String accountIdentifier = entity.getAccountIdentifier();
    String oldIndexName = entity.getOldIndexRetentionPeriod().getIndexName(accountIdentifier);
    String newIndexName = entity.getNewIndexRetentionPeriod().getIndexName(accountIdentifier);
    try {
      if (!isEmpty(entity.getElasticBufferSyncTaskID())) {
        handleElasticBufferSyncTask(entity, oldIndexName, newIndexName);
      } else if (!isEmpty(entity.getElasticTaskID())) {
        handleElasticSyncTask(entity, oldIndexName, newIndexName);
      }
    } catch (Exception e) {
      handleFailedMigration(oldIndexName, newIndexName, entity);
      throw new InternalServerErrorException(
          String.format("[ELASTIC_SEARCH]: Failed while reindexing buffer records for elastic for accountId: %s",
              accountIdentifier),
          e);
    }
  }

  /*
   * This will check the status of the buffer sync task and then if successful will mark the migration as complete
   * otherwise will fail the migration
   */
  private void handleElasticBufferSyncTask(
      PipelineSearchIndexMigrationEntity entity, String oldIndexName, String newIndexName) throws IOException {
    String accountIdentifier = entity.getAccountIdentifier();
    GetTasksResponse taskResponse = elasticsearchClient.getTask(t -> t.taskId(entity.getElasticBufferSyncTaskID()));
    if (taskResponse.completed()) {
      if (taskResponse.error() != null) {
        log.error(String.format(
            "[ELASTIC_SEARCH]: Elasticsearch Buffer Sync Reindexing task failed for account id: %s, task id: %s. Reason: %s",
            accountIdentifier, entity.getElasticBufferSyncTaskID(), taskResponse.error().reason()));
        handleFailedMigration(oldIndexName, newIndexName, entity);
        return;
      }
      markMigrationComplete(entity, accountIdentifier, taskResponse);
    }
  }

  private void markMigrationComplete(
      PipelineSearchIndexMigrationEntity entity, String accountIdentifier, GetTasksResponse taskResponse) {
    Update update = new Update();
    update.set(PipelineSearchIndexMigrationEntityKeys.migrationStartTime, taskResponse.task().startTimeInMillis());
    update.set(PipelineSearchIndexMigrationEntityKeys.migrationEndTime, getTaskEndTime(taskResponse));
    updateMigrationStatus(
        entity.getAccountIdentifier(), entity.getUuid(), update, PipelineSearchMigrationStatus.COMPLETE, null, null);
    log.info(String.format("[ELASTIC_SEARCH]: Index migration complete for account: %s", accountIdentifier));
  }

  /*
   * This task checks for the status of the original re-index task and if it's complete it will spin up a new buffer
   * sync task and store its task id
   */
  private void handleElasticSyncTask(
      PipelineSearchIndexMigrationEntity entity, String oldIndexName, String newIndexName) throws IOException {
    String accountIdentifier = entity.getAccountIdentifier();
    GetTasksResponse taskResponse = elasticsearchClient.getTask(t -> t.taskId(entity.getElasticTaskID()));
    if (taskResponse.completed()) {
      if (taskResponse.error() != null) {
        log.error(String.format(
            "[ELASTIC_SEARCH]: Elasticsearch Reindexing task failed for account id: %s, task id: %s. Reason: %s",
            accountIdentifier, entity.getElasticTaskID(), taskResponse.error().reason()));
        handleFailedMigration(oldIndexName, newIndexName, entity);
        return;
      }
      List<String> missingExecutionIDs = fetchMissingPlanExecutionIDsInNewIndex(accountIdentifier,
          taskResponse.task().startTimeInMillis(), ELASTIC_INDEX_MIGRATION_DELTA_TIME, oldIndexName, newIndexName);
      if (isEmpty(missingExecutionIDs)) {
        markMigrationComplete(entity, accountIdentifier, taskResponse);
        return;
      }
      ReindexResponse reindexResponse = pipelineSearchService.reIndexDocuments(accountIdentifier, oldIndexName,
          newIndexName, getQueryForMissingPlanExecutionIDs(accountIdentifier, missingExecutionIDs));
      log.info(String.format(
          "[ELASTIC_SEARCH]: Index migration buffer sync task to sync: %d executions created with task id: %s for account: %s",
          missingExecutionIDs.size(), reindexResponse.task(), accountIdentifier));
      Update update = new Update();
      update.set(PipelineSearchIndexMigrationEntityKeys.elasticBufferSyncTaskID, reindexResponse.task());
      update.set(PipelineSearchIndexMigrationEntityKeys.migrationStartTime, taskResponse.task().startTimeInMillis());
      update.set(PipelineSearchIndexMigrationEntityKeys.migrationEndTime, getTaskEndTime(taskResponse));
      indexMigrationService.update(entity.getUuid(), update);
    }
  }

  private Long getTaskEndTime(GetTasksResponse taskResponse) {
    TaskInfo taskInfo = taskResponse.task();

    // Get task start and running times (if available)
    Long startTimeInMillis = taskInfo.startTimeInMillis();
    Long runningTimeInNanos = taskInfo.runningTimeInNanos();
    // Elastic currently doesn't send task end time and only sends for how much time the task ran

    if (startTimeInMillis != null && runningTimeInNanos != null) {
      return startTimeInMillis + TimeUnit.NANOSECONDS.toMillis(runningTimeInNanos);
    }
    return null;
  }

  /*
   * Fetches the missing planExecutionIDs in the new index compared to the oldIndex, currently it's fetching 10k entries
   * but even after considering 10x growth in our current system we will reach a state in which this query
   * will return us 3.5k records, so we should be good here
   */
  private List<String> fetchMissingPlanExecutionIDsInNewIndex(String accountIdentifier, Long migrationStartTime,
      Long bufferTimeInMillis, String oldIndexName, String newIndexName) {
    if (migrationStartTime == null) {
      throw new InternalServerErrorException(String.format(
          "[ELASTIC_SEARCH]: Migration start time cannot be null for reindexing buffer docs for accountId: %s",
          accountIdentifier));
    }
    Query query = getQueryForIndexMigration(accountIdentifier, migrationStartTime, bufferTimeInMillis);
    List<String> oldIndexIDs = pipelineSearchService.listExecutionsFromIndex(query, oldIndexName, 10000);
    List<String> newIndexIDs = pipelineSearchService.listExecutionsFromIndex(query, newIndexName, 10000);
    return findMissingIds(oldIndexIDs, newIndexIDs);
  }

  // This compares the 2 planExecutionIds list from old and new index and returns a list of missing fields from oldIndex
  public static List<String> findMissingIds(List<String> oldIndexIds, List<String> newIndexIds) {
    if (oldIndexIds == null) {
      return Collections.emptyList();
    }
    Set<String> newIndexIdSet = new HashSet<>(newIndexIds);

    return oldIndexIds.stream().filter(id -> !newIndexIdSet.contains(id)).collect(Collectors.toList());
  }

  // This builds a query to only reindex the missing documents from the new index
  private Query getQueryForMissingPlanExecutionIDs(String accountIdentifier, List<String> planExecutionIds) {
    List<Query> queries = new ArrayList<>();
    queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.accountId, accountIdentifier));
    queries.add(ElasticSearchQueryBuilder.buildMultiValueComparisonQuery(
        EQUALS_ANY, PipelineSearchExecutionSummaryDTOKeys.planExecutionId, planExecutionIds));
    return ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, queries);
  }

  // This builds a time based range query comparing the endTs of the planExecution
  private Query getQueryForIndexMigration(String accountIdentifier, Long migrationStartTime, Long bufferTimeInMillis) {
    List<Query> queries = new ArrayList<>();
    queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
        EQUALS, PipelineSearchExecutionSummaryDTOKeys.accountId, accountIdentifier));
    queries.add(
        ElasticSearchQueryBuilder.buildRangeQuery(RANGE_INCLUDING_ENDS, PipelineSearchExecutionSummaryDTOKeys.endTs,
            migrationStartTime - bufferTimeInMillis, migrationStartTime + bufferTimeInMillis));
    return ElasticSearchQueryBuilder.buildNestedQuery(
        CONSTANT_SCORE, null, ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, queries));
  }
}
