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
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_PAYLOAD;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SCM_SERVICE_CONNECTION_FAILED;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData;
import io.harness.ngtriggers.beans.dto.TriggerNotificationData.TriggerNotificationDataBuilder;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEventStatus;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent.TriggerWebhookEventBuilder;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.pms.notification.helper.TriggerFailureNotificationHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerEventExecutionHelper;
import io.harness.pms.triggers.webhook.helpers.TriggerWebhookConfirmationHelper;
import io.harness.pms.triggers.webhook.service.TriggerCustomWebhookExecutionService;
import io.harness.repositories.custom.TriggerCustomWebhookEventRepositoryCustom;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerCustomWebhookExecutionServiceImpl implements TriggerCustomWebhookExecutionService {
  @Inject private MongoTemplate mongoTemplate;
  @Inject private TriggerEventExecutionHelper ngTriggerWebhookExecutionHelper;
  @Inject private TriggerWebhookConfirmationHelper ngTriggerWebhookConfirmationHelper;

  @Inject private NGTriggerService ngTriggerService;
  @Inject private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private TriggerCustomWebhookEventRepositoryCustom triggerCustomWebhookEventRepositoryCustom;
  @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Inject private TriggerFailureNotificationHelper triggerFailureNotificationHelper;

  @Override
  public boolean processMessage(String accountId, String eventCorrelationId) {
    TriggerCustomWebhookEvent event = null;
    TriggerNotificationDataBuilder triggerNotificationDataBuilder = TriggerNotificationData.builder();
    try {
      // process event further since no. of processing events is within threshold
      event = updateTriggerEventProcessingStatus(eventCorrelationId, null,
          TriggerCustomWebhookEventStatus.PROCESSING.name(), List.of(TriggerCustomWebhookEventStatus.QUEUED.name()));
      if (event == null) {
        // This can happen if same event is received twice so the event will be processing state and update will fail
        // and return null. Added this check to make out flow idempotent.
        log.warn("Event not found in queued state for eventCorrelationId : {}", eventCorrelationId);
        return false;
      }

      // Start processing the event
      log.info("Received webhook event to fire custom webhook trigger via queue service");
      WebhookEventProcessingResult result = processTriggerCustomWebhookEvent(event, triggerNotificationDataBuilder);

      List<TriggerEventResponse> responseList = result.getResponses();

      // Remove any null values if present in list , generally one custom webhook trigger will trigger only one pipeline
      if (isNotEmpty(responseList)) {
        responseList = responseList.stream().filter(Objects::nonNull).collect(toList());
      }

      // check  if response was successfully received and do we need to process the request again due to some failure.
      return processResponses(responseList, event, result, triggerNotificationDataBuilder);
    } catch (Exception e) {
      if (event != null) {
        return handleFailureInProcessMessage(eventCorrelationId, e, event);
      }
    }
    return true;
  }

  private WebhookEventProcessingResult processTriggerCustomWebhookEvent(
      TriggerCustomWebhookEvent event, TriggerNotificationDataBuilder triggerNotificationDataBuilder) {
    WebhookEventProcessingResult result;
    TriggerWebhookEvent triggerWebhookEvent = getTriggerWebhookEvent(event);
    if (event.isSubscriptionConfirmation()) {
      result = ngTriggerWebhookConfirmationHelper.handleTriggerWebhookConfirmationEvent(triggerWebhookEvent);
    } else {
      result = ngTriggerWebhookExecutionHelper.handleTriggerWebhookEvent(
          TriggerMappingRequestData.builder().triggerWebhookEvent(triggerWebhookEvent).webhookDTO(null).build(),
          triggerNotificationDataBuilder);
    }
    return result;
  }

  private boolean handleFailureInProcessMessage(
      String eventCorrelationId, Exception e, TriggerCustomWebhookEvent event) {
    event.setAttemptCount(event.getAttemptCount() + 1);
    ngTriggerService.updateTriggerCustomWebhookEvent(event.getUuid(), event.getAttemptCount(),
        TriggerCustomWebhookEventStatus.QUEUED.name(), List.of(TriggerCustomWebhookEventStatus.PROCESSING.name()));
    log.error(
        "Exception while handling webhook event with eventCorrelationId : {}. Please check", eventCorrelationId, e);
    if (event.getAttemptCount() == 2) {
      return true;
    } else {
      return false;
    }
  }

  private boolean processResponses(List<TriggerEventResponse> responseList, TriggerCustomWebhookEvent event,
      WebhookEventProcessingResult result, TriggerNotificationDataBuilder triggerNotificationDataBuilder) {
    boolean success;
    if (discardEmptyOrInvalidPayloadEvents(responseList)) {
      ngTriggerService.deleteTriggerCustomWebhookEvent(event);
      success = true;
    } else if (!result.isMappedToTriggers()) {
      success = handleTriggerNotFoundFailureDueToFailedScmConnectivity(event, result);
    } else {
      TriggerCustomWebhookEvent finalEvent = event;
      responseList.forEach(response -> {
        Query query = new Query(Criteria.where(TriggerEventHistoryKeys.accountId)
                                    .is(response.getAccountId())
                                    .and(TriggerEventHistoryKeys.eventCorrelationId)
                                    .is(finalEvent.getUuid()));
        TriggerEventHistory triggerEventHistory = TriggerEventResponseHelper.toEntity(response);
        triggerFailureNotificationHelper.sendTriggerNotification(
            triggerEventHistory, response.getFinalStatus(), triggerNotificationDataBuilder);
        triggerEventHistoryRepository.upsert(triggerEventHistory, query);
      });
      ngTriggerService.deleteTriggerCustomWebhookEvent(event);
      success = true;
    }
    return success;
  }

  private TriggerWebhookEvent getTriggerWebhookEvent(TriggerCustomWebhookEvent event) {
    TriggerWebhookEventBuilder triggerWebhookEventBuilder = ngTriggerElementMapper.toNGTriggerWebhookEventForCustom(
        event.getAccountId(), event.getOrgIdentifier(), event.getProjectIdentifier(), event.getPipelineIdentifier(),
        event.getTriggerIdentifier(), event.getPayload(), event.getHeaders());
    triggerWebhookEventBuilder.uuid(event.getUuid());
    triggerWebhookEventBuilder.sourceRepoType(event.getSourceRepoType());
    triggerWebhookEventBuilder.principal(event.getPrincipal());
    triggerWebhookEventBuilder.uniqueId(event.getUniqueId());
    triggerWebhookEventBuilder.parentUniqueId(event.getParentUniqueId());
    triggerWebhookEventBuilder.createdAt(event.getCreatedAt());
    return triggerWebhookEventBuilder.build();
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

  private boolean handleTriggerNotFoundFailureDueToFailedScmConnectivity(
      TriggerCustomWebhookEvent event, WebhookEventProcessingResult result) {
    if (isScmConnectivityFailed(result) && event.getAttemptCount() < 2) {
      event.setAttemptCount(event.getAttemptCount() + 1);
      updateTriggerEventProcessingStatus(event.getAccountId(), event.getAttemptCount(),
          TriggerCustomWebhookEventStatus.QUEUED.name(), List.of(TriggerCustomWebhookEventStatus.PROCESSING.name()));
      log.error("SCM service is unreachable. Please verify the service is running.");
      return false;
    } else {
      TriggerEventHistory triggerEventHistory = TriggerEventResponseHelper.toEntity(result.getResponses().get(0));
      ngTriggerWebhookExecutionHelper.validateUniqueIdAndParentUniqueId(triggerEventHistory);
      triggerEventHistoryRepository.save(triggerEventHistory);
      ngTriggerService.deleteTriggerCustomWebhookEvent(event);
      return true;
    }
  }

  private boolean isScmConnectivityFailed(WebhookEventProcessingResult result) {
    return isNotEmpty(result.getResponses())
        && result.getResponses().get(0).getFinalStatus() == SCM_SERVICE_CONNECTION_FAILED;
  }

  protected TriggerCustomWebhookEvent updateTriggerEventProcessingStatus(
      String customWebhookId, Integer attemptCount, String status, List<String> allowedStatus) {
    return ngTriggerService.updateTriggerCustomWebhookEvent(customWebhookId, attemptCount, status, allowedStatus);
  }
}
