/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.webhookevent;

import static io.harness.eventsframework.EventsFrameworkConstants.EVENT_LISTENER_STEP_EVENT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InternalServerErrorException;
import io.harness.hsqs.client.beans.HsqsDequeueConfig;
import io.harness.hsqs.client.beans.HsqsProcessMessageResponse;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.logging.AutoLogContext;
import io.harness.logging.EventListenerStepEventAutoLogContext;
import io.harness.pms.eventlistener.EventListenerStepEventHandler;
import io.harness.queuePoller.AbstractHsqsQueueProcessor;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class EventListenerStepEventQueueProcessor extends AbstractHsqsQueueProcessor {
  @Inject EventListenerStepEventHandler eventListenerStepEventService;
  @Inject @Named("webhookEventHsqsDequeueConfig") HsqsDequeueConfig webhookEventHsqsDequeueConfig;

  @Override
  public HsqsProcessMessageResponse processResponse(DequeueResponse message) {
    try {
      log.info("Started processing EventListener step event for item id {}", message.getItemId());
      WebhookDTO webhookDTO = RecastOrchestrationUtils.fromJson(message.getPayload(), WebhookDTO.class);
      try (EventListenerStepEventAutoLogContext ignore0 =
               new EventListenerStepEventAutoLogContext(webhookDTO.getEventId(), message.getItemId(),
                   webhookDTO.getAccountId(), AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
        eventListenerStepEventService.processEvent(webhookDTO);
        return HsqsProcessMessageResponse.builder().success(true).accountId(webhookDTO.getAccountId()).build();
      }
    } catch (Exception e) {
      log.error("Exception while processing EventListener step event", e);
      throw new InternalServerErrorException("Exception while processing EventListener step event", e);
    }
  }

  @Override
  public String getTopicName() {
    return "ng" + EVENT_LISTENER_STEP_EVENT;
  }

  @Override
  public HsqsDequeueConfig getHsqsDequeueConfig() {
    return webhookEventHsqsDequeueConfig;
  }
}
