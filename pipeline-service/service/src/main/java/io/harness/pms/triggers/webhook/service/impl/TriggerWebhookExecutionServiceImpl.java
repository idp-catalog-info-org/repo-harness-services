/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_PAYLOAD;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SCM_SERVICE_CONNECTION_FAILED;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.pojos.IteratorConfig;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData.TriggerNotificationDataBuilder;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventsKeys;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.notification.helper.TriggerFailureNotificationHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerWebhookConfirmationHelper;
import io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionService;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerWebhookExecutionServiceImpl
    extends IteratorLoopModeHandler implements io.harness.pms.triggers.webhook.service.TriggerWebhookExecutionService,
                                               MongoPersistenceIterator.Handler<TriggerWebhookEvent> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private TriggerEventExecutionHelper ngTriggerWebhookExecutionHelper;
  @Inject private TriggerWebhookConfirmationHelper ngTriggerWebhookConfirmationHelper;

  @Inject private NGTriggerService ngTriggerService;
  @Inject private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private TriggerFailureNotificationHelper triggerFailureNotificationHelper;

  public void registerIterators(IteratorConfig iteratorConfig) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("Custom-WebhookEventProcessor")
            .poolSize(iteratorConfig.getThreadPoolCount())
            .interval(ofSeconds(iteratorConfig.getTargetIntervalInSeconds()))
            .build(),
        TriggerWebhookExecutionService.class,
        MongoPersistenceIterator.<TriggerWebhookEvent, SpringFilterExpander>builder()
            .clazz(TriggerWebhookEvent.class)
            .fieldName(TriggerWebhookEventsKeys.nextIteration)
            .targetInterval(ofMinutes(5))
            .acceptableExecutionTime(ofMinutes(2))
            .acceptableNoAlertDelay(ofSeconds(30))
            .handler(this)
            .filterExpander(query
                -> query.addCriteria(new Criteria()
                                         .and(TriggerWebhookEventsKeys.attemptCount)
                                         .lte(2)
                                         .and(TriggerWebhookEventsKeys.processing)
                                         .is(false)))
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "Custom-WebhookEventProcessor";
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
    iterator = (MongoPersistenceIterator<TriggerWebhookEvent, SpringFilterExpander>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       TriggerWebhookEvent.class,
                       MongoPersistenceIterator.<TriggerWebhookEvent, SpringFilterExpander>builder()
                           .clazz(TriggerWebhookEvent.class)
                           .fieldName(TriggerWebhookEventsKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableExecutionTime(ofMinutes(2))
                           .acceptableNoAlertDelay(ofSeconds(30))
                           .handler(this)
                           .schedulingType(REGULAR)
                           .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
                           .filterExpander(query
                               -> query.addCriteria(new Criteria()
                                                        .and(TriggerWebhookEventsKeys.attemptCount)
                                                        .lte(2)
                                                        .and(TriggerWebhookEventsKeys.processing)
                                                        .is(false)))
                           .redistribute(true));
  }

  @Override
  public void handle(TriggerWebhookEvent event) {
    try {
      TriggerNotificationDataBuilder triggerNotificationDataBuilder = TriggerNotificationData.builder();
      log.info("Received webhook event to fire triggers via V1 flow");
      updateTriggerEventProcessingStatus(event, true); // start processing
      WebhookEventProcessingResult result;

      if (event.isSubscriptionConfirmation()) {
        result = ngTriggerWebhookConfirmationHelper.handleTriggerWebhookConfirmationEvent(event);
      } else {
        result = ngTriggerWebhookExecutionHelper.handleTriggerWebhookEvent(
            TriggerMappingRequestData.builder().triggerWebhookEvent(event).webhookDTO(null).build(),
            triggerNotificationDataBuilder);
      }

      List<TriggerEventResponse> responseList = result.getResponses();

      // Remove any null values if present in list
      if (isNotEmpty(responseList)) {
        responseList = responseList.stream().filter(Objects::nonNull).collect(toList());
      }

      if (discardEmptyOrInvalidPayloadEvents(responseList)) {
        ngTriggerService.deleteTriggerWebhookEvent(event);
      } else if (!result.isMappedToTriggers()) {
        handleTriggerNotFoundCase(event, result);
      } else {
        responseList.forEach(response -> {
          Query query = new Query(Criteria.where(TriggerEventHistoryKeys.accountId)
                                      .is(response.getAccountId())
                                      .and(TriggerEventHistoryKeys.eventCorrelationId)
                                      .is(event.getUuid()));
          TriggerEventHistory triggerEventHistory = TriggerEventResponseHelper.toEntity(response);
          triggerFailureNotificationHelper.sendTriggerNotification(
              triggerEventHistory, response.getFinalStatus(), triggerNotificationDataBuilder);
          triggerEventHistoryRepository.upsert(triggerEventHistory, query);
        });
        ngTriggerService.deleteTriggerWebhookEvent(event);
      }
    } catch (Exception e) {
      event.setAttemptCount(event.getAttemptCount() + 1);
      ngTriggerService.updateTriggerWebhookEvent(event);
      log.error("Exception while handling webhook event. Please check", e);
    }
  }

  private boolean discardEmptyOrInvalidPayloadEvents(List<TriggerEventResponse> responseList) {
    if (isEmpty(responseList)) {
      return true;
    }
    if (responseList.size() == 1 && responseList.get(0).getFinalStatus() == INVALID_PAYLOAD) {
      log.info("Unknown/Unsupported Webhook Event encountered for accountId: {}. Exception received: {}",
          responseList.get(0).getAccountId(), responseList.get(0).getMessage());
      return true;
    }
    return false;
  }

  private void handleTriggerNotFoundCase(TriggerWebhookEvent event, WebhookEventProcessingResult result) {
    if (isScmConnectivityFailed(result) && event.getAttemptCount() < 2) {
      event.setAttemptCount(event.getAttemptCount() + 1);
      updateTriggerEventProcessingStatus(event, false);
      log.error("SCM service is unreachable. Please verify the service is running.");
    } else {
      TriggerEventHistory triggerEventHistory = TriggerEventResponseHelper.toEntity(result.getResponses().get(0));
      ngTriggerWebhookExecutionHelper.validateUniqueIdAndParentUniqueId(triggerEventHistory);
      triggerEventHistoryRepository.save(triggerEventHistory);
      ngTriggerService.deleteTriggerWebhookEvent(event);
    }
  }

  private boolean isScmConnectivityFailed(WebhookEventProcessingResult result) {
    return isNotEmpty(result.getResponses())
        && result.getResponses().get(0).getFinalStatus() == SCM_SERVICE_CONNECTION_FAILED;
  }

  protected void updateTriggerEventProcessingStatus(TriggerWebhookEvent event, boolean status) {
    event.setProcessing(status);
    ngTriggerService.updateTriggerWebhookEvent(event);
  }
}
