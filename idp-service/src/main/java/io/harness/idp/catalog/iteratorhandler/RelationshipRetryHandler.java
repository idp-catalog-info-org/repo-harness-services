/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.iteratorhandler;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofSeconds;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.RelationshipTask;
import io.harness.idp.catalog.entities.TaskStatus;
import io.harness.idp.catalog.events.RelationshipProcessingEvent;
import io.harness.idp.catalog.processor.RelationshipEventProcessor;
import io.harness.idp.catalog.repositories.RelationshipTaskRepository;
import io.harness.idp.iterators.entity.IteratorEntity;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@AllArgsConstructor(onConstructor = @__({ @com.google.inject.Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class RelationshipRetryHandler implements MongoPersistenceIterator.Handler<IteratorEntity> {
  private static final String HANDLER_NAME = "RelationshipRetryHandler";
  private static final int MAX_RETRY_COUNT = 5;

  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MongoTemplate mongoTemplate;
  private RelationshipTaskRepository relationshipTaskRepository;
  private RelationshipEventProcessor relationshipEventProcessor;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void handle(IteratorEntity entity) {
    log.info("Relationship retry handler started at {}", System.currentTimeMillis());
    try {
      long now = System.currentTimeMillis();
      List<RelationshipTask> tasksToRetry =
          relationshipTaskRepository.findTasksReadyForRetry(TaskStatus.FAILED, now, MAX_RETRY_COUNT);

      if (tasksToRetry.isEmpty()) {
        log.info("No relationship tasks ready for retry");
        return;
      }

      log.info("Found {} relationship tasks ready for retry", tasksToRetry.size());

      for (RelationshipTask task : tasksToRetry) {
        retryTask(task);
      }
    } catch (Exception e) {
      log.error("Error in relationship retry handler: {}", e.getMessage(), e);
    }
    log.info("Relationship retry handler completed at {}", System.currentTimeMillis());
  }

  private void retryTask(RelationshipTask task) {
    try {
      RelationshipProcessingEvent event =
          objectMapper.readValue(task.getEventPayload(), RelationshipProcessingEvent.class);

      relationshipEventProcessor.processEvent(event);

      relationshipTaskRepository.delete(task);
      log.info("Successfully retried relationship event for entityId={}, retryCount={}", task.getEntityId(),
          task.getRetryCount());
    } catch (Exception e) {
      log.error("Failed to retry relationship task for entityId={}: {}", task.getEntityId(), e.getMessage(), e);
      updateFailedTask(task, e.getMessage());
    }
  }

  private void updateFailedTask(RelationshipTask task, String errorMessage) {
    long now = System.currentTimeMillis();
    task.setRetryCount(task.getRetryCount() + 1);
    task.setLastAttemptAt(now);
    task.setErrorMessage(errorMessage);
    if (task.getRetryCount() >= MAX_RETRY_COUNT) {
      task.setStatus(TaskStatus.DEAD_LETTER);
      log.warn("Relationship task for entityId={} moved to DEAD_LETTER after {} retries", task.getEntityId(),
          task.getRetryCount());
    } else {
      task.setStatus(TaskStatus.FAILED);
      task.setNextRetryAt(relationshipEventProcessor.calculateNextRetryTime(task.getRetryCount()));
    }
    relationshipTaskRepository.save(task);
  }

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(HANDLER_NAME)
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds() / 4))
            .build(),
        RelationshipRetryHandler.class,
        MongoPersistenceIterator.<IteratorEntity, SpringFilterExpander>builder()
            .clazz(IteratorEntity.class)
            .filterExpander(query -> query.addCriteria(where(IteratorEntity.IteratorsKeys.name).is(HANDLER_NAME)))
            .fieldName(IteratorEntity.IteratorsKeys.nextIteration)
            .targetInterval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .acceptableExecutionTime(ofSeconds(120))
            .acceptableNoAlertDelay(ofSeconds(60))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }
}
