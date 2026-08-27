/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.EVENT_LISTENER_STEP_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.NG_GITX_WEBHOOK_PUSH_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_BRANCH_HOOK_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_PR_HOOK_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_PUSH_EVENT;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.CREATE_BRANCH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.DELETE_BRANCH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PR;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PUSH;
import static io.harness.gitsync.gitxwebhooks.metrics.GitXWebhookQueueOperationMetrics.WEBHOOK_BRANCH_EVENT_ENQUEUED;
import static io.harness.gitsync.gitxwebhooks.metrics.GitXWebhookQueueOperationMetrics.WEBHOOK_PUSH_EVENT_ENQUEUED;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InternalServerErrorException;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;
import io.harness.gitsync.gitxwebhooks.metrics.GitXWebhookQueueOperationMetrics;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventService;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.WebhookHmacHelper;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.services.api.WebhookEventService;
import io.harness.ng.webhook.services.api.WebhookService;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.MultivaluedMap;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class WebhookServiceImpl implements WebhookService, WebhookEventService {
  private static final String TRIGGERS = "triggers";
  private static final String EVENT_LISTENER_STEP = "event listener step";
  private final HarnessSCMWebhookServiceImpl harnessSCMWebhookService;
  private final DefaultWebhookServiceImpl defaultWebhookService;
  private final WebhookEventRepository webhookEventRepository;
  private final PmsFeatureFlagHelper ngFeatureFlagHelperService;
  private HsqsClientService hsqsClientService;
  private WebhookHelper webhookHelper;
  private WebhookHmacHelper webhookHmacHelper;
  private GitSyncSdkService gitSyncSdkService;
  private GitXWebhookEventService gitXWebhookEventService;
  private MetricService metricService;
  NextGenConfiguration nextGenConfiguration;

  @Override
  public WebhookEvent createWebhookEvent(
      Scope scope, GitXWebhook webhook, MultivaluedMap<String, String> headers, String payload) {
    // verify HMAC before processing the events
    if ((NGCommonEntityConstants.GENERIC_WEBHOOK_TYPE).equals(webhook.getWebhookType())) {
      webhookHmacHelper.verifyHMACSignature(webhook, payload, getHeaderConfig(headers));
    } else if ((NGCommonEntityConstants.SLACK_WEBHOOK_TYPE).equals(webhook.getWebhookType())) {
      webhookHmacHelper.verifyHMACSignatureForSlack(webhook, payload, getHeaderConfig(headers));
    }

    // Add event to process in queue
    WebhookEvent eventEntity = WebhookHelper.toNGTriggerWebhookEvent(payload, headers, scope, webhook.getIdentifier());

    WebhookEvent newEvent;
    try {
      newEvent = addEventToQueue(eventEntity);
    } catch (Exception e) {
      log.error("Failed to add event to queue", e);
      throw new InternalServerErrorException(e.getMessage(), e);
    }

    // Add event to database to store as event history
    try {
      GitXWebhookEvent createdGitXWebhookEvent = gitXWebhookEventService.createWebhookEvent(
          scope, webhook.getIdentifier(), newEvent.getUuid(), payload, newEvent.getCreatedAt());
      log.info("Successfully created the webhook event {}", createdGitXWebhookEvent.getEventIdentifier());
    } catch (Exception e) {
      log.error(String.format("Error occurred while storing the webhook event %s.", newEvent.getUuid()), e);
      throw new InternalServerErrorException(e.getMessage(), e);
    }
    return newEvent;
  }

  private static List<HeaderConfig> getHeaderConfig(MultivaluedMap<String, String> headers) {
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(headers)) {
      headers.keySet().forEach(
          header -> headerConfigs.add(HeaderConfig.builder().key(header).values(headers.get(header)).build()));
    }
    return headerConfigs;
  }

  @Override
  public WebhookEvent addEventToQueue(WebhookEvent webhookEvent) {
    try {
      // TODO: add a check based on env to use iterators in community edition and on prem
      if (!nextGenConfiguration.isUseQueueServiceForWebhookTriggers()
          || !ngFeatureFlagHelperService.isEnabled(
              webhookEvent.getAccountId(), FeatureName.CDS_QUEUE_SERVICE_FOR_TRIGGERS)) {
        return webhookEventRepository.save(webhookEvent);
      } else {
        generateWebhookDTOAndEnqueue(webhookEvent);
        // uuid is the main field to track event processing in upstream flows
        log.info("Processed webhook event with id {} in the accountId {}", webhookEvent.getUuid(),
            webhookEvent.getAccountId());
      }
      return webhookEvent;
    } catch (Exception e) {
      log.error("Webhook event could not be saved for processing", e);
      throw e;
    }
  }

  @VisibleForTesting
  void generateWebhookDTOAndEnqueue(WebhookEvent webhookEvent) {
    if (isEmpty(webhookEvent.getUuid())) {
      webhookEvent.setUuid(generateUuid());
    }
    if (webhookEvent.getCreatedAt() == null) {
      webhookEvent.setCreatedAt(System.currentTimeMillis());
    }
    try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext("eventId", webhookEvent.getUuid(),
             webhookEvent.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      String topic = nextGenConfiguration.getQueueServiceClientConfig().getTopic();
      String moduleName = topic;
      ParseWebhookResponse parseWebhookResponse = null;
      SourceRepoType sourceRepoType = WebhookHelper.getSourceRepoType(webhookEvent);
      if (sourceRepoType != SourceRepoType.UNRECOGNIZED && sourceRepoType != SourceRepoType.HARNESS_ARTIFACT_REGISTRY) {
        parseWebhookResponse = webhookHelper.invokeScmService(webhookEvent);
      }
      WebhookDTO webhookDTO = webhookHelper.generateWebhookDTO(webhookEvent, parseWebhookResponse, sourceRepoType);
      enqueueWebhookEvents(webhookDTO, topic, moduleName, webhookEvent.getUuid());
    }
  }

  private void enqueueWebhookEvents(WebhookDTO webhookDTO, String topic, String moduleName, String uuid) {
    // Consumer for webhook events stream: WebhookEventQueueProcessor (in Pipeline service)

    boolean triggerQueueingFailed = enqueueWebhookEvents(webhookDTO, topic, moduleName, uuid, TRIGGERS, WEBHOOK_EVENT);
    boolean stepQueuingFailed = false;
    // We want only consider non git events for event listener step
    if (!webhookDTO.hasGitDetails()) {
      stepQueuingFailed =
          enqueueWebhookEvents(webhookDTO, topic, moduleName, uuid, EVENT_LISTENER_STEP, EVENT_LISTENER_STEP_EVENT);
    }
    if (triggerQueueingFailed || stepQueuingFailed) {
      String failureReason = stepQueuingFailed
          ? (triggerQueueingFailed ? EVENT_LISTENER_STEP + "and" + TRIGGERS : EVENT_LISTENER_STEP)
          : TRIGGERS;
      throw new InternalServerErrorException(String.format("Failed to add event for %s to the Queue.", failureReason));
    }
  }

  private boolean enqueueWebhookEvents(
      WebhookDTO webhookDTO, String topic, String moduleName, String uuid, String queueType, String eventName) {
    try {
      boolean shouldExecuteTriggerSequentially = shouldExecuteTriggerSequentially(webhookDTO);
      EnqueueRequest enqueueRequest =
          getFirstEnqueueRequest(shouldExecuteTriggerSequentially, moduleName, topic, webhookDTO, eventName);
      EnqueueResponse execute = hsqsClientService.enqueue(enqueueRequest);
      recordEnqueueMetrics(enqueueRequest, topic, webhookDTO.getAccountId(), webhookDTO);
      log.info("Webhook event queued for {}. message id: {}, uuid: {}", queueType, execute.getItemId(), uuid);
      if (!shouldExecuteTriggerSequentially && webhookDTO.hasParsedResponse() && webhookDTO.hasGitDetails()) {
        enqueueRequest = getEnqueueRequestBasedOnGitEvent(moduleName, topic, webhookDTO);
        if (enqueueRequest != null) {
          execute = hsqsClientService.enqueue(enqueueRequest);
          recordEnqueueMetrics(enqueueRequest, topic, webhookDTO.getAccountId(), webhookDTO);
          log.info("Webhook {} event queued for {}. message id: {}", queueType, webhookDTO.getGitDetails().getEvent(),
              execute.getItemId());
        }
      }
    } catch (Exception e) {
      log.error("Failed to queue event for {}, uuid: {}", queueType, uuid, e);
      return true;
    }
    return false;
  }

  private void recordEnqueueMetrics(
      EnqueueRequest enqueueRequest, String topic, String accountId, WebhookDTO webhookDTO) {
    if (enqueueRequest.getTopic().equals(NG_GITX_WEBHOOK_PUSH_EVENT)) {
      GitXWebhookQueueOperationMetrics.recordMessageMetric(
          WEBHOOK_PUSH_EVENT_ENQUEUED, accountId, webhookDTO, metricService);
    } else if (enqueueRequest.getTopic().equals(topic + WEBHOOK_BRANCH_HOOK_EVENT)) {
      GitXWebhookQueueOperationMetrics.recordMessageMetric(
          WEBHOOK_BRANCH_EVENT_ENQUEUED, accountId, webhookDTO, metricService);
    }
  }

  private EnqueueRequest getEnqueueRequestBasedOnGitEvent(String moduleName, String topic, WebhookDTO webhookDTO) {
    // Consumer for push events stream: WebhookPushEventQueueProcessor (in NG manager)
    if (PUSH == webhookDTO.getGitDetails().getEvent()) {
      String producerName;
      if (gitSyncSdkService.isGitSimplificationEnabled(webhookDTO.getAccountId(), "", "")) {
        producerName = NG_GITX_WEBHOOK_PUSH_EVENT;
      } else {
        producerName = moduleName + WEBHOOK_PUSH_EVENT;
      }
      return EnqueueRequest.builder()
          .topic(producerName)
          .subTopic(webhookDTO.getAccountId())
          .producerName(producerName)
          .payload(RecastOrchestrationUtils.toJson(webhookDTO))
          .build();
    }
    // Consumer for branch hook events stream: WebhookBranchHookEventQueueProcessor (in NG manager)
    else if (CREATE_BRANCH == webhookDTO.getGitDetails().getEvent()
        || DELETE_BRANCH == webhookDTO.getGitDetails().getEvent()) {
      return EnqueueRequest.builder()
          .topic(topic + WEBHOOK_BRANCH_HOOK_EVENT)
          .subTopic(webhookDTO.getAccountId())
          .producerName(moduleName + WEBHOOK_BRANCH_HOOK_EVENT)
          .payload(RecastOrchestrationUtils.toJson(webhookDTO))
          .build();
    } else if (PR == webhookDTO.getGitDetails().getEvent()) {
      return EnqueueRequest.builder()
          .topic(topic + WEBHOOK_PR_HOOK_EVENT)
          .subTopic(webhookDTO.getAccountId())
          .producerName(moduleName + WEBHOOK_PR_HOOK_EVENT)
          .payload(RecastOrchestrationUtils.toJson(webhookDTO))
          .build();
    }
    // Here we can add more logic if needed to add more event topics.
    return null;
  }

  @Override
  public UpsertWebhookResponseDTO upsertWebhook(UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    return Boolean.TRUE.equals(upsertWebhookRequestDTO.getIsHarnessScm())
        ? harnessSCMWebhookService.upsertWebhook(upsertWebhookRequestDTO)
        : defaultWebhookService.upsertWebhook(upsertWebhookRequestDTO);
  }

  private boolean shouldExecuteTriggerSequentially(WebhookDTO webhookDTO) {
    return ngFeatureFlagHelperService.isEnabled(webhookDTO.getAccountId(), FeatureName.PIE_PROCESS_TRIGGER_SEQUENTIALLY)
        && isGitPushEvent(webhookDTO)
        && gitSyncSdkService.isGitSimplificationEnabled(webhookDTO.getAccountId(), "", "");
  }

  private boolean isGitPushEvent(WebhookDTO webhookDTO) {
    return webhookDTO.hasParsedResponse() && webhookDTO.hasGitDetails()
        && PUSH == webhookDTO.getGitDetails().getEvent();
  }

  private EnqueueRequest getFirstEnqueueRequest(boolean shouldExecuteTriggerSequentially, String moduleName,
      String topic, WebhookDTO webhookDTO, String eventName) {
    EnqueueRequest enqueueRequest;
    if (shouldExecuteTriggerSequentially) {
      enqueueRequest = getEnqueueRequestBasedOnGitEvent(moduleName, topic, webhookDTO);
    } else {
      enqueueRequest = EnqueueRequest.builder()
                           .topic(topic + eventName)
                           .subTopic(webhookDTO.getAccountId())
                           .producerName(moduleName + eventName)
                           .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                           .build();
    }
    return enqueueRequest;
  }
}
