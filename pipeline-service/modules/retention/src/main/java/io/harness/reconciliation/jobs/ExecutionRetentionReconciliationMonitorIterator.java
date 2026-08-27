/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.reconciliation.jobs;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.elasticsearch.framework.OperatorEnum.CONSTANT_SCORE;
import static io.harness.elasticsearch.framework.OperatorEnum.EQUALS;
import static io.harness.elasticsearch.framework.OperatorEnum.GREATER_THAN;
import static io.harness.elasticsearch.framework.OperatorEnum.LESS_THAN_EQUALS;
import static io.harness.elasticsearch.framework.OperatorEnum.MUST_MATCH_ALL;
import static io.harness.elasticsearch.framework.OperatorEnum.RANGE_INCLUDING_ENDS;
import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.isNull;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.ExecutionRetentionMetadata.ExecutionRetentionMetadataKeys;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.elasticsearch.ElasticSearchStream;
import io.harness.elasticsearch.utils.ElasticSearchQueryBuilder;
import io.harness.exception.InternalServerErrorException;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationMonitorEntity;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationMonitorEntity.ExecutionRetentionReconciliationMonitorEntityKeys;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationMonitorStatus;
import io.harness.reconciliation.service.ExecutionRetentionReconciliationMonitorEntityService;
import io.harness.search.entity.beans.PipelineSearchExecutionSummaryDTO.PipelineSearchExecutionSummaryDTOKeys;
import io.harness.search.entity.beans.PipelineSearchReadExecutionSummaryDTO;
import io.harness.search.service.PipelineSearchService;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Singleton
public class ExecutionRetentionReconciliationMonitorIterator
    extends IteratorLoopModeHandler implements Handler<ExecutionRetentionReconciliationMonitorEntity> {
  private Duration syncJobMaxRunTime;
  private static final int RECONCILIATION_BATCH_SIZE = 1000;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private ExecutionRetentionReconciliationMonitorEntityService reconciliationMonitorEntityService;
  @Nullable @Inject private ElasticSearchClient elasticSearchClient;
  @Inject private PipelineSearchService pipelineSearchService;
  @Inject private ExecutionRetentionMetadataService executionRetentionMetadataService;

  private static final String ITERATOR_NAME = "ExecutionRetentionReconciliationMonitorIterator";

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = ITERATOR_NAME;
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
    if (targetInterval.compareTo(Duration.ofHours(1)) > 0) {
      this.syncJobMaxRunTime = targetInterval.minus(Duration.ofHours(1));
    } else {
      this.syncJobMaxRunTime = targetInterval.minus(Duration.ofMinutes(10));
    }
    iterator =
        (MongoPersistenceIterator<ExecutionRetentionReconciliationMonitorEntity, SpringFilterExpander>)
            persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                ExecutionRetentionReconciliationMonitorEntity.class,
                MongoPersistenceIterator.<ExecutionRetentionReconciliationMonitorEntity, SpringFilterExpander>builder()
                    .clazz(ExecutionRetentionReconciliationMonitorEntity.class)
                    .fieldName(ExecutionRetentionReconciliationMonitorEntityKeys.nextIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ofSeconds(10))
                    .acceptableExecutionTime(ofSeconds(10))
                    .handler(this)
                    .schedulingType(REGULAR)
                    .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
                    .filterExpander(q
                        -> q.addCriteria(where(ExecutionRetentionReconciliationMonitorEntityKeys.status)
                                             .ne(ExecutionRetentionReconciliationMonitorStatus.COMPLETE))));
  }

  @Override
  public void handle(ExecutionRetentionReconciliationMonitorEntity entity) {
    Long lastExecutionEndTs = null;
    Instant jobStartTs = Instant.now();
    Long totalRecordsProcessed = 0L;
    Long totalMissingExecutionIDs = 0L;
    Set<String> batchExecutionIds = new HashSet<>();

    try {
      shouldRunReconciliation(entity);
      boolean earlyJobEnd = false;

      try (ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> stream = getExecutionSummaryStream(entity)) {
        Iterator<PipelineSearchReadExecutionSummaryDTO> iterator = stream.iterator();
        while (iterator.hasNext()) {
          PipelineSearchReadExecutionSummaryDTO summaryDTO = iterator.next();
          lastExecutionEndTs = summaryDTO.getEndTs();
          batchExecutionIds.add(summaryDTO.getPlanExecutionId());

          if (batchExecutionIds.size() >= RECONCILIATION_BATCH_SIZE) {
            Set<String> batchMissingExecutionIds = findMissingExecutionsInBatch(batchExecutionIds);
            totalRecordsProcessed += batchExecutionIds.size();
            batchExecutionIds.clear();

            if (!batchMissingExecutionIds.isEmpty()) {
              logMissingExecutionsInBatches(batchMissingExecutionIds);
              totalMissingExecutionIDs += batchMissingExecutionIds.size();
            }
            reconciliationMonitorEntityService.updateSyncCompletedUntil(entity.getUuid(), lastExecutionEndTs);
          }

          if (hasJobRunTimeExceededMaxRunTime(jobStartTs) || getMaintenanceFlag()) {
            earlyJobEnd = true;
            if (getMaintenanceFlag()) {
              log.warn(
                  "[RETENTION_RECONCILIATION_MONITOR]: Service is going in maintenance mode so shutting down the iterator");
            }
            break;
          }
        }
      }
      if (!batchExecutionIds.isEmpty()) {
        Set<String> batchMissingExecutionIds = findMissingExecutionsInBatch(batchExecutionIds);
        totalRecordsProcessed += batchExecutionIds.size();
        batchExecutionIds.clear();

        if (!batchMissingExecutionIds.isEmpty()) {
          logMissingExecutionsInBatches(batchMissingExecutionIds);
          totalMissingExecutionIDs += batchMissingExecutionIds.size();
        }
      }
      if (!earlyJobEnd && entity.getSyncUntil() != null) {
        reconciliationMonitorEntityService.updateStatus(
            entity.getUuid(), ExecutionRetentionReconciliationMonitorStatus.COMPLETE);
      }
    } catch (Exception ex) {
      log.error("[RETENTION_RECONCILIATION_MONITOR]: Failed to reconcile records for account: {}",
          entity.getAccountIdentifier(), ex);
    } finally {
      log.info(
          "[RETENTION_RECONCILIATION_MONITOR]: Reconciliation Job completed for account: {}, records processed: {}, "
              + "syncCompletedUntil: {}, syncUntil: {}, total missing executions: {}",
          entity.getAccountIdentifier(), totalRecordsProcessed, lastExecutionEndTs, entity.getSyncUntil(),
          totalMissingExecutionIDs);
    }
  }

  /**
   * Process a batch of execution IDs:
   * Find which IDs exist in Elasticsearch but not in MongoDB
   *
   * @param executionIds Set of execution IDs to process
   * @return Set of missing execution IDs
   */
  private Set<String> findMissingExecutionsInBatch(Set<String> executionIds) {
    if (executionIds.isEmpty()) {
      return Collections.emptySet();
    }

    Criteria criteria = Criteria.where(ExecutionRetentionMetadataKeys.planExecutionId).in(executionIds);
    List<ExecutionRetentionMetadata> existingMetadata = executionRetentionMetadataService.fetchAllFromSecondary(
        criteria, Set.of(ExecutionRetentionMetadataKeys.planExecutionId));

    Set<String> existingExecutionIds =
        existingMetadata.stream().map(ExecutionRetentionMetadata::getPlanExecutionId).collect(Collectors.toSet());

    Set<String> missingExecutionIds = new HashSet<>(executionIds);
    missingExecutionIds.removeAll(existingExecutionIds);
    return missingExecutionIds;
  }

  /**
   * Log missing executions in batches of 100
   */
  private void logMissingExecutionsInBatches(Set<String> missingExecutionIds) {
    if (missingExecutionIds.isEmpty()) {
      return;
    }

    List<String> missingExecList = new ArrayList<>(missingExecutionIds);
    int batchSize = 100;

    for (int i = 0; i < missingExecList.size(); i += batchSize) {
      int endIndex = Math.min(i + batchSize, missingExecList.size());
      log.warn("[RETENTION_RECONCILIATION_MONITOR]: Missing execution IDs from GCS: {}",
          String.join(", ", missingExecList.subList(i, endIndex)));
    }
  }

  private ElasticSearchStream<PipelineSearchReadExecutionSummaryDTO> getExecutionSummaryStream(
      ExecutionRetentionReconciliationMonitorEntity entity) {
    List<Query> queries = new ArrayList<>();

    if (isNotEmpty(entity.getAccountIdentifier())) {
      queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          EQUALS, PipelineSearchExecutionSummaryDTOKeys.accountId, entity.getAccountIdentifier()));
    }

    if (entity.getSyncCompletedUntil() != null && entity.getSyncUntil() != null) {
      queries.add(ElasticSearchQueryBuilder.buildRangeQuery(RANGE_INCLUDING_ENDS,
          PipelineSearchExecutionSummaryDTOKeys.endTs, entity.getSyncCompletedUntil(), entity.getSyncUntil()));
    } else if (entity.getSyncCompletedUntil() != null) {
      queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          GREATER_THAN, PipelineSearchExecutionSummaryDTOKeys.endTs, entity.getSyncCompletedUntil()));
    } else if (entity.getSyncUntil() != null) {
      queries.add(ElasticSearchQueryBuilder.buildSingleValueComparisonQuery(
          LESS_THAN_EQUALS, PipelineSearchExecutionSummaryDTOKeys.endTs, entity.getSyncUntil()));
    }

    Query finalQuery = ElasticSearchQueryBuilder.buildNestedQuery(
        CONSTANT_SCORE, null, ElasticSearchQueryBuilder.buildCombinedQuery(MUST_MATCH_ALL, queries));

    Set<String> fieldsToInclude = new HashSet<>();
    fieldsToInclude.add(PipelineSearchExecutionSummaryDTOKeys.planExecutionId);
    fieldsToInclude.add(PipelineSearchExecutionSummaryDTOKeys.endTs);

    Map<String, SortOrder> sortingFields = Map.of(PipelineSearchExecutionSummaryDTOKeys.endTs, SortOrder.Asc);

    return pipelineSearchService.fetchPipelineSearchReadExecutionSummaryDTO(
        entity.getAccountIdentifier(), finalQuery, fieldsToInclude, sortingFields);
  }

  private boolean hasJobRunTimeExceededMaxRunTime(Instant jobStartTs) {
    Duration jobRunTime = Duration.between(jobStartTs, Instant.now());
    return jobRunTime.compareTo(syncJobMaxRunTime) > 0;
  }

  private void shouldRunReconciliation(ExecutionRetentionReconciliationMonitorEntity entity) {
    if (isNull(entity.getSyncCompletedUntil()) && isNull(entity.getSyncUntil())) {
      throw new InternalServerErrorException(String.format(
          "[ELASTIC_METADATA_RECONCILIATION]: Both syncCompletedUntil and syncUntil are null for entity: %s",
          entity.getUuid()));
    }

    if (elasticSearchClient == null) {
      log.error(
          "[RETENTION_RECONCILIATION_MONITOR]: ElasticSearchClient is not initialized so skipping reconciliation monitor for elastic");
      throw new InternalServerErrorException(
          "[RETENTION_RECONCILIATION_MONITOR]: ElasticSearchClient is not initialized so skipping reconciliation monitor for elastic");
    }
  }
}
