/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.idp.IdpKindProcessorEvent;
import io.harness.idp.catalog.service.KindService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IdpKindProcessorEventConsumer extends AbstractIdpServiceRedisStreamConsumer {
  private static final String CONSUMER_NAME = "IdpKindProcessorEventConsumer";

  @Inject private KindService kindService;

  @Inject
  public IdpKindProcessorEventConsumer(@Named(IDP_KIND_PROCESSOR_EVENT) Consumer redisConsumer,
      QueueController queueController, ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);
    if (message.hasMessage()) {
      try {
        boolean entityTypeAndActionValidation =
            entityTypeAndActionValidation(CONSUMER_NAME, message, IDP_KIND_PROCESSOR_EVENT, DELETE_ACTION);
        Map<String, String> metadata = message.getMessage().getMetadataMap();
        if (entityTypeAndActionValidation) {
          ByteString data = message.getMessage().getData();
          IdpKindProcessorEvent idpKindProcessorEvent = IdpKindProcessorEvent.parseFrom(data);
          return lockAndProcessData(CONSUMER_NAME + "_EVENT_" + idpKindProcessorEvent.getAccountIdentifier() + "_"
                  + idpKindProcessorEvent.getKindIdentifier(),
              metadata.get(ENTITY_TYPE), data);
        }
      } catch (Exception ex) {
        log.error("Error in processing message with id: {} in {} consumer. Error = {}", message.getId(), CONSUMER_NAME,
            ex.getMessage(), ex);
        return false;
      }
      log.info("Processed messageId = {} in {} consumer", message.getId(), CONSUMER_NAME);
    }
    return true;
  }

  @Override
  protected void processInternal(String entityType, ByteString data) throws Exception {
    IdpKindProcessorEvent idpKindProcessorEvent = IdpKindProcessorEvent.parseFrom(data);
    kindService.processKindDelete(
        idpKindProcessorEvent.getAccountIdentifier(), idpKindProcessorEvent.getKindIdentifier());
  }
}
