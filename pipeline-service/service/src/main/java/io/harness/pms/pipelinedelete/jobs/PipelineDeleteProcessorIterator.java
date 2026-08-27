/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinedelete.jobs;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InternalServerErrorException;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.pms.event.entitycrud.PipelineEntityCRUDStreamListener;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity;
import io.harness.pms.pipelinedelete.beans.entity.PipelineDeleteProcessorIteratorEntity.PipelineDeleteProcessorIteratorEntityKeys;
import io.harness.pms.pipelinedelete.service.PipelineDeleteProcessorIteratorEntityService;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
public class PipelineDeleteProcessorIterator
    extends IteratorLoopModeHandler implements Handler<PipelineDeleteProcessorIteratorEntity> {
  @Inject private PipelineEntityCRUDStreamListener pipelineEntityCRUDStreamListener;
  @Inject private PipelineDeleteProcessorIteratorEntityService deleteProcessorIteratorEntityService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private Duration syncJobMaxRunTime;

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "PipelineDeleteProcessorIterator";
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
    iterator = (MongoPersistenceIterator<PipelineDeleteProcessorIteratorEntity, SpringFilterExpander>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       PipelineDeleteProcessorIteratorEntity.class,
                       MongoPersistenceIterator.<PipelineDeleteProcessorIteratorEntity, SpringFilterExpander>builder()
                           .clazz(PipelineDeleteProcessorIteratorEntity.class)
                           .fieldName(PipelineDeleteProcessorIteratorEntityKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofSeconds(10))
                           .acceptableExecutionTime(ofSeconds(10))
                           .handler(this)
                           .schedulingType(REGULAR)
                           .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate)));
  }

  @Override
  public void handle(PipelineDeleteProcessorIteratorEntity entity) {
    Instant jobStartTs = Instant.now();
    try {
      boolean deleted = pipelineEntityCRUDStreamListener.processDeleteEvent(jobStartTs, this.syncJobMaxRunTime,
          entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier(),
          entity.getPipelineIdentifier(), entity.isRetainPipelineExecutionDetailsAfterDelete(),
          entity.getParentUniqueId());
      if (deleted) {
        deleteProcessorIteratorEntityService.deleteById(entity.getUuid());
      } else {
        deleteProcessorIteratorEntityService.updateNextIteration(
            entity.getUuid(), Instant.now().plus(Duration.ofMinutes(30)).toEpochMilli());
      }
    } catch (Exception ex) {
      // We are updating the next iteration to be 30 mins after current time
      // This is because if the record sync fails we want to retry it again quickly so that the lag doesn't pile up
      deleteProcessorIteratorEntityService.updateNextIteration(
          entity.getUuid(), Instant.now().plus(Duration.ofMinutes(30)).toEpochMilli());
      log.error(String.format(
                    "[PIPELINE_DELETE]: Failed while deleting data for account: %s, parentUniqueId: %s, pipeline: %s",
                    entity.getAccountIdentifier(), entity.getParentUniqueId(), entity.getPipelineIdentifier()),
          ex);
      throw new InternalServerErrorException(
          String.format(
              "[PIPELINE_DELETE]: Failed while deleting data for account: %s, parentUniqueId: %s, pipeline: %s",
              entity.getAccountIdentifier(), entity.getParentUniqueId(), entity.getPipelineIdentifier()),
          ex);
    }
  }
}
