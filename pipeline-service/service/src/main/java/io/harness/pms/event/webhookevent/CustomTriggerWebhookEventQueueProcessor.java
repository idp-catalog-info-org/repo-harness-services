/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.webhookevent;

import static io.harness.eventsframework.EventsFrameworkConstants.TRIGGER_CUSTOM_WEBHOOK_EVENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse.HsqsProcessMessageResponseBuilder;
import io.harness.hsqs.client.model.DequeueRequest;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.logging.AutoLogContext;
import io.harness.logging.NgTriggerAutoLogContext;
import io.harness.ngtriggers.beans.entity.TriggerCustomWebhookEventPayload;
import io.harness.pms.triggers.webhook.service.TriggerCustomWebhookExecutionService;
import io.harness.queuePoller.AbstractHsqsQueueProcessor;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class CustomTriggerWebhookEventQueueProcessor extends AbstractHsqsQueueProcessor {
  @Inject TriggerCustomWebhookExecutionService triggerCustomWebhookExecutionService;
  @Inject HsqsClientService hsqsClientService;
  @Inject @Named("webhookEventHsqsDequeueConfig") HsqsDequeueConfig webhookEventHsqsDequeueConfig;
  @Inject @Named("CustomWebhookTriggerExecutorService") private Executor customWebhookTriggerExecutorService;

  @Override
  protected void pollAndProcessMessages() {
    try {
      List<DequeueResponse> messages = hsqsClientService.dequeue(DequeueRequest.builder()
                                                                     .batchSize(getHsqsDequeueConfig().getBatchSize())
                                                                     .consumerName(getTopicName())
                                                                     .topic(getTopicName())
                                                                     .maxWaitDuration(5)
                                                                     .build());
      for (DequeueResponse message : messages) {
        customWebhookTriggerExecutorService.execute(() -> processMessage(message));
      }
      sleepForMessageSize(messages);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void sleepForMessageSize(List<DequeueResponse> messages) throws Exception {
    if (messages.size() < webhookEventHsqsDequeueConfig.getBatchSize()) {
      TimeUnit.MILLISECONDS.sleep(webhookEventHsqsDequeueConfig.getThreadSleepTimeInMillis());
    }
  }

  @Override
  public HsqsProcessMessageResponse processResponse(DequeueResponse message) {
    HsqsProcessMessageResponseBuilder hsqsProcessMessageResponseBuilder = HsqsProcessMessageResponse.builder();
    try {
      log.debug("Started processing webhook event for item id {}", message.getItemId());
      TriggerCustomWebhookEventPayload triggerCustomWebhookEventPayload =
          RecastOrchestrationUtils.fromJson(message.getPayload(), TriggerCustomWebhookEventPayload.class);
      String eventCorrelationId = triggerCustomWebhookEventPayload.getEventCorrelationId();
      String accountId = triggerCustomWebhookEventPayload.getAccountId();
      hsqsProcessMessageResponseBuilder.accountId(accountId);
      try (NgTriggerAutoLogContext ignore0 = new NgTriggerAutoLogContext(
               "customEventId", eventCorrelationId, accountId, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        boolean success = triggerCustomWebhookExecutionService.processMessage(accountId, eventCorrelationId);
        return hsqsProcessMessageResponseBuilder.success(success).build();
      }
    } catch (Exception e) {
      // The only case where we might catch an exception here is if there is failure in deserializing the payload. hence
      // returning true here as if we cant deserialize the payload then we cant process the event so no point in unack.
      log.error("Exception while processing custom webhook event in message with item id {}", message.getItemId(), e);
      return hsqsProcessMessageResponseBuilder.success(true).build();
    }
  }

  @Override
  public String getTopicName() {
    return "pms" + TRIGGER_CUSTOM_WEBHOOK_EVENT;
  }

  @Override
  public HsqsDequeueConfig getHsqsDequeueConfig() {
    return webhookEventHsqsDequeueConfig;
  }
}
