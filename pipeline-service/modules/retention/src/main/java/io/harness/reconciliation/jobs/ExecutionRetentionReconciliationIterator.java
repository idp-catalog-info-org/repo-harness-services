/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.reconciliation.jobs;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;
import static io.harness.utils.RetentionConstants.RETENTION_ITERATOR_DELAY_METRIC_NAME;
import static io.harness.utils.RetentionConstants.RETENTION_SYNC_ACCOUNT_ID_METRIC_LABEL_KEY;
import static io.harness.utils.RetentionConstants.RETENTION_SYNC_ENTITY_METRIC_LABEL_KEY;
import static io.harness.utils.RetentionConstants.RETENTION_SYNC_METHOD_METRIC_LABEL_KEY;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.isNull;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.dataretention.config.DataRetentionConfig;
import io.harness.dataretention.entity.ExecutionRetentionMetadata;
import io.harness.dataretention.entity.beans.ExecutionRetentionObjectStoreCollection;
import io.harness.dataretention.jobs.service.ExecutionRetentionIteratorEntityService;
import io.harness.dataretention.service.ExecutionRetentionMetadataService;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.elasticsearch.ElasticSearchClient;
import io.harness.exception.InternalServerErrorException;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.objectstore.ObjectStoreClient;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity;
import io.harness.reconciliation.entity.ExecutionRetentionReconciliationEntity.ExecutionRetentionReconciliationEntityKeys;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationDB;
import io.harness.reconciliation.entity.beans.ExecutionRetentionReconciliationStatus;
import io.harness.reconciliation.service.ExecutionRetentionReconciliationEntityService;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.search.service.PipelineSearchService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_DATA_RETENTION, HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@Singleton
public class ExecutionRetentionReconciliationIterator
    extends IteratorLoopModeHandler implements Handler<ExecutionRetentionReconciliationEntity> {
  private Duration syncJobMaxRunTime;
  private static final int RECONCILIATION_BATCH_SIZE = 1000;
  private int RECONCILIATION_BATCH_PROCESSING_SIZE = 20;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject ExecutionRetentionIteratorEntityService retentionIteratorEntityService;
  @Inject ExecutionRetentionReconciliationEntityService reconciliationEntityService;
  @Inject private Injector injector;
  @Nullable @Inject @Named("DataRetentionObjectStoreClient") private ObjectStoreClient objectStoreClient;
  @Inject DataRetentionConfig dataRetentionConfig;
  @Nullable @Inject private ElasticSearchClient elasticSearchClient;
  @Inject PipelineSearchService pipelineSearchService;
  @Inject private MetricService metricService;
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject private ExecutionRetentionMetadataService executionRetentionMetadataService;
  @Inject ExecutionRetentionService executionRetentionService;
  private static final String ITERATOR_SYNC_METHOD_RECONCILE = "RECONCILIATION";
  private static final String ITERATOR_SYNC_METHOD_REGULAR = "REGULAR";

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "ExecutionRetentionReconciliationIterator";
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
    if (dataRetentionConfig.getReconciliationBatchProcessingSize() > 0) {
      this.RECONCILIATION_BATCH_PROCESSING_SIZE = dataRetentionConfig.getReconciliationBatchProcessingSize();
    }
    iterator = (MongoPersistenceIterator<ExecutionRetentionReconciliationEntity, SpringFilterExpander>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       ExecutionRetentionReconciliationEntity.class,
                       MongoPersistenceIterator.<ExecutionRetentionReconciliationEntity, SpringFilterExpander>builder()
                           .clazz(ExecutionRetentionReconciliationEntity.class)
                           .fieldName(ExecutionRetentionReconciliationEntityKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofSeconds(10))
                           .acceptableExecutionTime(ofSeconds(10))
                           .handler(this)
                           .schedulingType(REGULAR)
                           .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
                           .filterExpander(q
                               -> q.addCriteria(where(ExecutionRetentionReconciliationEntityKeys.status)
                                                    .ne(ExecutionRetentionReconciliationStatus.COMPLETE))));
  }

  @Override
  public void handle(ExecutionRetentionReconciliationEntity entity) {
    if (ExecutionRetentionReconciliationDB.ELASTIC.equals(entity.getReconciliationDB())
        && entity.getShouldSyncFromGCS()) {
      syncFromRetentionMetadata(entity);
      return;
    }
    Long lastExecutionEndTs = null;
    Long reconcileBatchLastExecutionEndTs = null;
    Instant jobStartTs = Instant.now();
    Long totalRecordsProcessed = 0L;
    Duration totalElasticSyncRunTime = Duration.ZERO;
    Duration totalObjectStoreSyncRunTime = Duration.ZERO;
    Set<String> toBeReconciledPlanExecutionIDs = new HashSet<>();
    try {
      shouldRunReconciliation(entity);
      int batchSizeCounter = 0;
      boolean earlyJobEnd = false;
      try (Stream<PipelineExecutionSummaryEntity> stream = getSummaryEntityStream(entity)) {
        Iterator<PipelineExecutionSummaryEntity> iterator = stream.iterator();
        while (iterator.hasNext()) {
          PipelineExecutionSummaryEntity summaryEntity = iterator.next();
          reconcileBatchLastExecutionEndTs = summaryEntity.getEndTs();
          toBeReconciledPlanExecutionIDs.add(summaryEntity.getPlanExecutionId());
          if (toBeReconciledPlanExecutionIDs.size() >= RECONCILIATION_BATCH_PROCESSING_SIZE) {
            switch (entity.getReconciliationDB()) {
              case ELASTIC -> {
                totalElasticSyncRunTime =
                    totalElasticSyncRunTime.plus(syncRecordsToElastic(entity, toBeReconciledPlanExecutionIDs));
              }
              case OBJECT_STORE -> {
                totalObjectStoreSyncRunTime = totalObjectStoreSyncRunTime.plus(
                    retentionIteratorEntityService.syncRecordsToObjectStore(toBeReconciledPlanExecutionIDs));
              }
              default ->
                throw new InternalServerErrorException(
                    String.format("[RETENTION_RECONCILIATION]: DB type: %s is not supported for reconciliation",
                                  entity.getReconciliationDB()));
            }
            lastExecutionEndTs = reconcileBatchLastExecutionEndTs;
            totalRecordsProcessed += toBeReconciledPlanExecutionIDs.size();
            toBeReconciledPlanExecutionIDs.clear();
          }
          batchSizeCounter++;
          if (batchSizeCounter >= RECONCILIATION_BATCH_SIZE) {
            reconciliationEntityService.updateSyncCompletedUntil(entity.getUuid(), lastExecutionEndTs);
            recordDelayMetric(entity, lastExecutionEndTs);
            batchSizeCounter = 0;
          }
          if (hasJobRunTimeExceededMaxRunTime(jobStartTs)) {
            earlyJobEnd = true;
            break;
          }
          if (getMaintenanceFlag()) {
            earlyJobEnd = true;
            log.warn("[RETENTION_RECONCILIATION]: Service is going in maintenance mode so shutting down the iterator");
            break;
          }
        }
      }
      if (!toBeReconciledPlanExecutionIDs.isEmpty()) {
        switch (entity.getReconciliationDB()) {
          case ELASTIC -> {
            totalElasticSyncRunTime =
                totalElasticSyncRunTime.plus(syncRecordsToElastic(entity, toBeReconciledPlanExecutionIDs));
          }
          case OBJECT_STORE -> {
            totalObjectStoreSyncRunTime = totalObjectStoreSyncRunTime.plus(
                retentionIteratorEntityService.syncRecordsToObjectStore(toBeReconciledPlanExecutionIDs));
          }
          default ->
            throw new InternalServerErrorException(
                String.format("[RETENTION_RECONCILIATION]: DB type: %s is not supported for reconciliation",
                              entity.getReconciliationDB()));
        }
        lastExecutionEndTs = reconcileBatchLastExecutionEndTs;
        totalRecordsProcessed += toBeReconciledPlanExecutionIDs.size();
      }
      if (!earlyJobEnd && entity.getSyncUntil() != null) {
        reconciliationEntityService.updateStatus(entity.getUuid(), ExecutionRetentionReconciliationStatus.COMPLETE);
      }
    } catch (Exception ex) {
      // We are updating the next iteration to be 30 mins after current time
      // This is because if the record sync fails we want to retry it again quickly so that the lag doesn't pile up
      reconciliationEntityService.updateNextIteration(entity.getUuid(),
                                                      Instant.now().plus(Duration.ofMinutes(30)).toEpochMilli());
      log.error(
          String.format(
              "[RETENTION_RECONCILIATION]: Failed while reconciling data to object store/elastic for execution IDs: %s",
              toBeReconciledPlanExecutionIDs),
          ex);
      throw new InternalServerErrorException(
          String.format(
              "[RETENTION_RECONCILIATION]: Failed while reconciling data to object store/elastic for execution IDs: %s",
              toBeReconciledPlanExecutionIDs),
          ex);
    } finally {
      log.info(String.format("[RETENTION_RECONCILIATION]: Processed %d no. of records in %d seconds",
                             totalRecordsProcessed, Duration.between(jobStartTs, Instant.now()).getSeconds()));
      printRunTimeLogMessage(entity, totalRecordsProcessed, totalElasticSyncRunTime, totalObjectStoreSyncRunTime);
      if (lastExecutionEndTs != null) {
        reconciliationEntityService.updateSyncCompletedUntil(entity.getUuid(), lastExecutionEndTs);
        recordDelayMetric(entity, lastExecutionEndTs);
      } else if (entity.getSyncCompletedUntil() != null) {
        // No progress in this run (e.g. error or empty stream). Still emit the lag metric using the
        // previously persisted checkpoint so the time series doesn't disappear from dashboards on errors.
        recordDelayMetric(entity, entity.getSyncCompletedUntil());
      }
    }
  }

  private void printRunTimeLogMessage(ExecutionRetentionReconciliationEntity entity, Long totalRecordsProcessed,
                                      Duration totalElasticSyncRunTime, Duration totalObjectStoreSyncRunTime) {
    if (totalRecordsProcessed > 0) {
      Duration entitySyncRunTime = totalElasticSyncRunTime;
      if (ExecutionRetentionReconciliationDB.OBJECT_STORE.equals(entity.getReconciliationDB())) {
        entitySyncRunTime = totalObjectStoreSyncRunTime;
      }
      log.info(String.format("[RETENTION_RECONCILIATION]: %s Sync total run time: %d seconds, average run time: %d ms",
                             entity.getReconciliationDB(), entitySyncRunTime.getSeconds(),
                             entitySyncRunTime.toMillis() / totalRecordsProcessed));
    }
  }

  private Stream<PipelineExecutionSummaryEntity> getSummaryEntityStream(ExecutionRetentionReconciliationEntity entity) {
    String orgIdentifier = entity.getOrgIdentifier();
    if (entity.getSyncUntil() == null) {
      return pmsExecutionSummaryRepository.fetchPlanExecutionIdsWithGTEEndTsFromSecondary(
          entity.getAccountIdentifier(), entity.getSyncCompletedUntil(), orgIdentifier);
    }
    return pmsExecutionSummaryRepository.fetchPlanExecutionIdsBetweenEndTsFromSecondary(
        entity.getAccountIdentifier(), entity.getSyncCompletedUntil(), entity.getSyncUntil(), null, orgIdentifier);
  }

  private Duration syncRecordsToElastic(ExecutionRetentionReconciliationEntity entity,
                                        Set<String> toBeReconciledPlanExecutionIDs) {
    Instant syncStartTs = Instant.now();
    for (String currentPlanExecutionId : toBeReconciledPlanExecutionIDs) {
                  PipelineExecutionSummaryEntity fullSummaryEntity =
                      pmsExecutionSummaryRepository.fetchByPlanExecutionIdFromSecondary(currentPlanExecutionId);
                  if (Boolean.TRUE.equals(entity.getShouldOnlyUpdate())) {
                    // Below will find the actual index names in which this record is present in and call the update
                    // query on that index itself
                    pipelineSearchService.updateCompletedExecutionsToElastic(fullSummaryEntity);
                  } else {
                    boolean syncToElastic = true;
                    if (entity.getSyncCompletedUntil().equals(fullSummaryEntity.getEndTs())
                        || entity.getSyncUntil().equals(fullSummaryEntity.getEndTs())
                        || Boolean.TRUE.equals(entity.getVerifyRecordExistsBeforeInsert())) {
                      syncToElastic = !pipelineSearchService.checkIfPlanExecutionIDExists(fullSummaryEntity);
                    }
                    if (syncToElastic) {
                      /*
                       * We only want to sync the record to elastic in-case it's not present already. This is because we
                       * are using index alias in elastic search, and index rolls-over every 30 days, so let's say the
                       * record is already present and index rolls over after it, on saving the document again to
                       * elastic, it will be saved again to the latest index causing duplicate entries. So as a
                       * workaround, we are checking if the record doesn't already exist
                       */
                      pipelineSearchService.syncCompletedExecutionsToElastic(fullSummaryEntity);
                    }
                  }
                }
                return Duration.between(syncStartTs, Instant.now());
            }

            private void shouldRunReconciliation(ExecutionRetentionReconciliationEntity entity) {
              switch (entity.getReconciliationDB()) {
      case ELASTIC -> {
        if (elasticSearchClient == null) {
          log.error("[RETENTION_RECONCILIATION]: ElasticSearchClient is not initialized so skipping execution sync to "
                    + "elastic");
          throw new InternalServerErrorException("[RETENTION_RECONCILIATION]: ElasticSearchClient is not initialized "
                                                 + "so skipping execution sync to elastic");
        }
      }
      case OBJECT_STORE -> {
        if (objectStoreClient == null) {
          log.error("[RETENTION_RECONCILIATION]: ObjectStoreClient is not initialized so skipping reconciliation to "
                    + "object store");
          throw new InternalServerErrorException("[RETENTION_RECONCILIATION]: ObjectStoreClient is not initialized so "
                                                 + "skipping reconciliation to object store");
        }
      }
      default ->
        throw new InternalServerErrorException(
            String.format("[RETENTION_RECONCILIATION]: DB type: %s is not supported for reconciliation",
                          entity.getReconciliationDB()));
    }
    if (entity.getAccountIdentifier() != null
        && (entity.getSyncUntil() == null || entity.getSyncCompletedUntil() == null)) {
      throw new InternalServerErrorException(
          String.format("[RETENTION_RECONCILIATION]: SyncUntil/SyncCompletedUntil fields should be present to "
                        + "reconcile data for account id: %s",
                        entity.getAccountIdentifier()));
    }
    if (entity.getOrgIdentifier() != null && entity.getAccountIdentifier() == null) {
      throw new InternalServerErrorException(
          "[RETENTION_RECONCILIATION]: Account identifier is required when org identifier is provided");
    }
  }

  private boolean hasJobRunTimeExceededMaxRunTime(Instant jobStartTs) {
    Duration elapsedTime = Duration.between(jobStartTs, Instant.now());
    return elapsedTime.compareTo(this.syncJobMaxRunTime) > 0;
  }

  private void recordDelayMetric(ExecutionRetentionReconciliationEntity entity, Long lastExecutionEndTs) {
    String iteratorSyncMethod = ITERATOR_SYNC_METHOD_RECONCILE;
    Long syncUntilTime = entity.getSyncUntil();
    String accountId = entity.getAccountIdentifier();
    if (isNull(accountId)) {
      accountId = "ALL";
    }
    if (entity.getSyncUntil() == null) {
      iteratorSyncMethod = ITERATOR_SYNC_METHOD_REGULAR;
      syncUntilTime = Instant.now().toEpochMilli();
    }
    ImmutableMap<String, String> metricContextMap =
        ImmutableMap.<String, String>builder()
            .put(RETENTION_SYNC_ACCOUNT_ID_METRIC_LABEL_KEY, accountId)
            .put(RETENTION_SYNC_METHOD_METRIC_LABEL_KEY, iteratorSyncMethod)
            .put(RETENTION_SYNC_ENTITY_METRIC_LABEL_KEY, entity.getReconciliationDB().toString())
            .build();
    try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
      metricService.recordMetric(RETENTION_ITERATOR_DELAY_METRIC_NAME, syncUntilTime - lastExecutionEndTs);
    }
  }

  public void syncFromRetentionMetadata(ExecutionRetentionReconciliationEntity entity) {
    Long lastExecutionEndTs = null;
    Long reconcileBatchLastExecutionEndTs = null;
    Instant jobStartTs = Instant.now();
    Long totalRecordsProcessed = 0L;
    Duration totalElasticSyncRunTime = Duration.ZERO;
    List<ExecutionRetentionMetadata> toBeReconciledExecutionMetadata = new ArrayList<>();
    boolean earlyJobEnd = false;

    try {
      if (elasticSearchClient == null) {
        log.error(
            "[RETENTION_RECONCILIATION]: ElasticSearchClient is not initialized so skipping execution sync to elastic");
        throw new InternalServerErrorException(
            "[RETENTION_RECONCILIATION]: ElasticSearchClient is not initialized so skipping execution sync to elastic");
      }
      int batchSizeCounter = 0;
      // Stream from the ExecutionRetentionMetadata collection
      try (Stream<ExecutionRetentionMetadata> stream =
               executionRetentionMetadataService.fetchExecutionMetadataBetweenEndTsFromSecondary(
                   entity.getAccountIdentifier(), entity.getSyncCompletedUntil(), entity.getSyncUntil(),
                   entity.getOrgIdentifier())) {
        Iterator<ExecutionRetentionMetadata> iterator = stream.iterator();
        while (iterator.hasNext()) {
          ExecutionRetentionMetadata metadataEntity = iterator.next();
          reconcileBatchLastExecutionEndTs = metadataEntity.getEndTs();
          toBeReconciledExecutionMetadata.add(metadataEntity);
          if (toBeReconciledExecutionMetadata.size() >= RECONCILIATION_BATCH_PROCESSING_SIZE) {
            totalElasticSyncRunTime =
                totalElasticSyncRunTime.plus(syncRecordsToElastic(entity, toBeReconciledExecutionMetadata));
            lastExecutionEndTs = reconcileBatchLastExecutionEndTs;
            totalRecordsProcessed += toBeReconciledExecutionMetadata.size();
            toBeReconciledExecutionMetadata.clear();
          }

          batchSizeCounter++;
          if (batchSizeCounter >= RECONCILIATION_BATCH_SIZE) {
            reconciliationEntityService.updateSyncCompletedUntil(entity.getUuid(), lastExecutionEndTs);
            recordDelayMetric(entity, lastExecutionEndTs);
            batchSizeCounter = 0;
          }

          if (hasJobRunTimeExceededMaxRunTime(jobStartTs)) {
            earlyJobEnd = true;
            break;
          }

          if (getMaintenanceFlag()) {
            earlyJobEnd = true;
            log.warn("[RETENTION_RECONCILIATION]: Service is going in maintenance mode so shutting down the iterator");
            break;
          }
        }
      }

      // Process any remaining records
      if (!toBeReconciledExecutionMetadata.isEmpty()) {
        totalElasticSyncRunTime =
            totalElasticSyncRunTime.plus(syncRecordsToElastic(entity, toBeReconciledExecutionMetadata));
        lastExecutionEndTs = reconcileBatchLastExecutionEndTs;
        totalRecordsProcessed += toBeReconciledExecutionMetadata.size();
      }

      // Update status to COMPLETE if we've reached the end and syncUntil is specified
      if (!earlyJobEnd && entity.getSyncUntil() != null) {
        reconciliationEntityService.updateStatus(entity.getUuid(), ExecutionRetentionReconciliationStatus.COMPLETE);
      }
    } catch (Exception ex) {
      // We are updating the next iteration to be 30 mins after current time
      // This is because if the record sync fails we want to retry it again quickly so that the lag doesn't pile up
      reconciliationEntityService.updateNextIteration(entity.getUuid(),
                                                      Instant.now().plus(Duration.ofMinutes(30)).toEpochMilli());
      List<String> toBeReconciledPlanExecutionIDs = toBeReconciledExecutionMetadata.stream()
                                                        .map(ExecutionRetentionMetadata::getPlanExecutionId)
                                                        .collect(Collectors.toList());
      log.error(String.format("[RETENTION_RECONCILIATION]: Failed while syncing from ExecutionRetentionMetadata to "
                              + "elastic for execution IDs: %s",
                              toBeReconciledPlanExecutionIDs),
                ex);
      throw new InternalServerErrorException(
          String.format("[RETENTION_RECONCILIATION]: Failed while syncing from ExecutionRetentionMetadata to elastic "
                        + "for execution IDs: %s",
                        toBeReconciledPlanExecutionIDs),
          ex);
    } finally {
      log.info(String.format(
          "[RETENTION_RECONCILIATION]: Processed %d no. of records from ExecutionRetentionMetadata in %d seconds",
          totalRecordsProcessed, Duration.between(jobStartTs, Instant.now()).getSeconds()));
      printRunTimeLogMessage(entity, totalRecordsProcessed, totalElasticSyncRunTime, null);
      if (lastExecutionEndTs != null) {
        reconciliationEntityService.updateSyncCompletedUntil(entity.getUuid(), lastExecutionEndTs);
        recordDelayMetric(entity, lastExecutionEndTs);
      } else if (entity.getSyncCompletedUntil() != null) {
        // No progress in this run (e.g. error or empty stream). Still emit the lag metric using the
        // previously persisted checkpoint so the time series doesn't disappear from dashboards on errors.
        recordDelayMetric(entity, entity.getSyncCompletedUntil());
      }
    }
  }

  private Duration syncRecordsToElastic(ExecutionRetentionReconciliationEntity entity,
                                        List<ExecutionRetentionMetadata> toBeReconciledExecutionMetadata) {
    Instant syncStartTs = Instant.now();
    Map<String, Object> toBeReconciledExecutions = executionRetentionService.readRecordsFromObjectStore(
        toBeReconciledExecutionMetadata, ExecutionRetentionObjectStoreCollection.EXECUTION_SUMMARY,
        PipelineExecutionSummaryEntity.class);
    for (Map.Entry<String, Object> currentExecution : toBeReconciledExecutions.entrySet()) {
          PipelineExecutionSummaryEntity executionSummaryEntity =
              (PipelineExecutionSummaryEntity) currentExecution.getValue();
          if (Boolean.TRUE.equals(entity.getShouldOnlyUpdate())) {
            // Below will find the actual index names in which this record is present in and call the update
            // query on that index itself
            pipelineSearchService.updateCompletedExecutionsToElastic(executionSummaryEntity);
          } else {
            boolean syncToElastic = true;
            if (entity.getSyncCompletedUntil().equals(executionSummaryEntity.getEndTs())
                || entity.getSyncUntil().equals(executionSummaryEntity.getEndTs())
                || Boolean.TRUE.equals(entity.getVerifyRecordExistsBeforeInsert())) {
              syncToElastic = !pipelineSearchService.checkIfPlanExecutionIDExists(executionSummaryEntity);
            }
            if (syncToElastic) {
              /*
               * We only want to sync the record to elastic in-case it's not present already. This is because we
               * are using index alias in elastic search, and index rolls-over every 30 days, so let's say the
               * record is already present and index rolls over after it, on saving the document again to
               * elastic, it will be saved again to the latest index causing duplicate entries. So as a
               * workaround, we are checking if the record doesn't already exist
               */
              pipelineSearchService.syncCompletedExecutionsToElastic(executionSummaryEntity);
            }
          }
        }
        return Duration.between(syncStartTs, Instant.now());
              }
            }
