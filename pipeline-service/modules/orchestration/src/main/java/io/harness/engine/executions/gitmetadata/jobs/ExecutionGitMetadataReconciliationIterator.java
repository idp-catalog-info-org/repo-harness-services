/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.gitmetadata.jobs;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.engine.executions.gitmetadata.service.ExecutionGitMetadataReconciliationEntityService;
import io.harness.engine.executions.gitmetadata.service.PipelineExecutionGitMetadataService;
import io.harness.exception.InternalServerErrorException;
import io.harness.execution.gitmetadata.ExecutionGitMetadataReconciliationEntity;
import io.harness.execution.gitmetadata.ExecutionGitMetadataReconciliationEntity.ExecutionGitMetadataReconciliationEntityKeys;
import io.harness.execution.gitmetadata.beans.ExecutionGitMetadataReconciliationStatus;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.utils.ScopeResolutionHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
public class ExecutionGitMetadataReconciliationIterator
    extends IteratorLoopModeHandler implements Handler<ExecutionGitMetadataReconciliationEntity> {
  private Duration syncJobMaxRunTime;
  private static final int RECONCILIATION_BATCH_SIZE = 1000;

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private ExecutionGitMetadataReconciliationEntityService reconciliationEntityService;
  @Inject private PipelineExecutionGitMetadataService executionGitMetadataService;
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "ExecutionGitMetadataReconciliationIterator";
    // Register the iterator with the iterator config handler
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    // Not implemented
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
        (MongoPersistenceIterator<ExecutionGitMetadataReconciliationEntity, SpringFilterExpander>)
            persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                ExecutionGitMetadataReconciliationEntity.class,
                MongoPersistenceIterator.<ExecutionGitMetadataReconciliationEntity, SpringFilterExpander>builder()
                    .clazz(ExecutionGitMetadataReconciliationEntity.class)
                    .fieldName(ExecutionGitMetadataReconciliationEntityKeys.nextIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ofSeconds(10))
                    .acceptableExecutionTime(ofSeconds(10))
                    .handler(this)
                    .schedulingType(REGULAR)
                    .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
                    .filterExpander(q
                        -> q.addCriteria(where(ExecutionGitMetadataReconciliationEntityKeys.status)
                                             .ne(ExecutionGitMetadataReconciliationStatus.COMPLETE))));
  }

  @Override
  public void handle(ExecutionGitMetadataReconciliationEntity entity) {
    Long lastExecutionEndTs = null;
    Instant jobStartTs = Instant.now();
    Long totalRecordsProcessed = 0L;

    try {
      int batchSizeCounter = 0;
      boolean earlyJobEnd = false;

      try (Stream<PipelineExecutionSummaryEntity> stream =
               pmsExecutionSummaryRepository.fetchPlanExecutionIdsBetweenEndTsFromSecondary(null,
                   entity.getSyncCompletedUntil(), entity.getSyncUntil(),
                   Set.of(PlanExecutionSummaryKeys.accountId, PlanExecutionSummaryKeys.orgIdentifier,
                       PlanExecutionSummaryKeys.projectIdentifier, PlanExecutionSummaryKeys.pipelineIdentifier,
                       PlanExecutionSummaryKeys.parentUniqueId, PlanExecutionSummaryKeys.entityGitDetails))) {
        Iterator<PipelineExecutionSummaryEntity> iterator = stream.iterator();
        while (iterator.hasNext()) {
          PipelineExecutionSummaryEntity summaryEntity = iterator.next();
          lastExecutionEndTs = summaryEntity.getEndTs();

          // Process the summary entity to upsert git metadata
          processExecutionSummary(summaryEntity);

          batchSizeCounter++;
          totalRecordsProcessed++;

          if (batchSizeCounter >= RECONCILIATION_BATCH_SIZE) {
            reconciliationEntityService.updateSyncCompletedUntil(entity.getUuid(), lastExecutionEndTs);
            batchSizeCounter = 0;
          }

          if (hasJobRunTimeExceededMaxRunTime(jobStartTs)) {
            earlyJobEnd = true;
            log.info("[GIT_METADATA_RECONCILIATION]: Job max run time exceeded, stopping");
            break;
          }

          if (getMaintenanceFlag()) {
            earlyJobEnd = true;
            log.warn(
                "[GIT_METADATA_RECONCILIATION]: Service is going in maintenance mode so shutting down the iterator");
            break;
          }
        }
      }

      if (!earlyJobEnd) {
        reconciliationEntityService.updateStatus(entity.getUuid(), ExecutionGitMetadataReconciliationStatus.COMPLETE);
        log.info("[GIT_METADATA_RECONCILIATION]: Reconciliation completed successfully");
      }

    } catch (Exception ex) {
      // We are updating the next iteration to be 30 mins after current time
      // This is because if the record sync fails we want to retry it again quickly so that the lag doesn't pile up
      reconciliationEntityService.updateNextIteration(
          entity.getUuid(), Instant.now().plus(Duration.ofMinutes(30)).toEpochMilli());
      log.error("[GIT_METADATA_RECONCILIATION]: Failed while reconciling git metadata", ex);
      throw new InternalServerErrorException(
          "[GIT_METADATA_RECONCILIATION]: Failed while reconciling git metadata", ex);
    } finally {
      log.info(String.format("[GIT_METADATA_RECONCILIATION]: Processed %d records in %d seconds", totalRecordsProcessed,
          Duration.between(jobStartTs, Instant.now()).getSeconds()));

      if (lastExecutionEndTs != null) {
        reconciliationEntityService.updateSyncCompletedUntil(entity.getUuid(), lastExecutionEndTs);
      }
    }
  }

  /**
   * Processes a summary entity and upserts git metadata
   */
  private void processExecutionSummary(PipelineExecutionSummaryEntity summaryEntity) {
    if (summaryEntity == null || summaryEntity.getEntityGitDetails() == null) {
      return;
    }

    String repoName = summaryEntity.getEntityGitDetails().getRepoName();
    String branch = summaryEntity.getEntityGitDetails().getBranch();
    if (repoName != null && branch != null) {
      ScopeInfo scopeInfo =
          scopeResolutionHelper.getScopeInfo(summaryEntity.getAccountId(), summaryEntity.getParentUniqueId());

      executionGitMetadataService.upsert(scopeInfo, summaryEntity.getPipelineIdentifier(), repoName, branch);
    }
  }

  /**
   * Checks if the job run time has exceeded the maximum allowed run time
   */
  private boolean hasJobRunTimeExceededMaxRunTime(Instant jobStartTs) {
    return Duration.between(jobStartTs, Instant.now()).compareTo(syncJobMaxRunTime) > 0;
  }
}
