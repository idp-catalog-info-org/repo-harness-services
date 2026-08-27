/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.START_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.idp.IdpCatalogEntitiesSyncCaptureEvent;
import io.harness.exception.UnexpectedException;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.queue.QueueController;
import io.harness.spec.server.idp.v1.model.BackstageHarnessSyncRequest;
import io.harness.spec.server.idp.v1.model.User;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IdpCatalogEntitiesSyncCaptureEventConsumer extends AbstractIdpServiceRedisStreamConsumer {
  private static final String CONSUMER_NAME = "IdpCatalogEntitiesSyncCaptureEventConsumer";

  @Inject private BackstageService backstageService;

  @Inject
  public IdpCatalogEntitiesSyncCaptureEventConsumer(
      @Named(IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT) Consumer redisConsumer, QueueController queueController,
      ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);
    if (message != null && message.hasMessage()) {
      boolean entityTypeAndActionValidation;
      try {
        Map<String, String> metadata = message.getMessage().getMetadataMap();
        String entityType = metadata.get(ENTITY_TYPE);
        entityTypeAndActionValidation = entityTypeAndActionValidation(CONSUMER_NAME, message, IDP_CATALOG_ENTITY,
            List.of(CREATE_ACTION, UPDATE_ACTION, DELETE_ACTION, START_ACTION));
        if (entityTypeAndActionValidation) {
          ByteString data = message.getMessage().getData();
          IdpCatalogEntitiesSyncCaptureEvent idpCatalogEntitiesSyncCaptureEvent =
              IdpCatalogEntitiesSyncCaptureEvent.parseFrom(data);
          return lockAndProcessData(CONSUMER_NAME + "_EVENT_"
                  + idpCatalogEntitiesSyncCaptureEvent.getAccountIdentifier() + "_"
                  + idpCatalogEntitiesSyncCaptureEvent.getIdentifier(),
              entityType, data);
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
    User user = new User();
    IdpCatalogEntitiesSyncCaptureEvent idpCatalogEntitiesSyncCaptureEvent =
        IdpCatalogEntitiesSyncCaptureEvent.parseFrom(data);
    user.setUuid(idpCatalogEntitiesSyncCaptureEvent.getUserUuid());
    user.setEmail(idpCatalogEntitiesSyncCaptureEvent.getUserEmail());
    user.setName(idpCatalogEntitiesSyncCaptureEvent.getUserName());

    boolean result = backstageService.syncByType(idpCatalogEntitiesSyncCaptureEvent.getAccountIdentifier(),
        BackstageHarnessSyncRequest.TypeEnum.fromValue(idpCatalogEntitiesSyncCaptureEvent.getType()),
        idpCatalogEntitiesSyncCaptureEvent.getIdentifier(), idpCatalogEntitiesSyncCaptureEvent.getAction(),
        idpCatalogEntitiesSyncCaptureEvent.getSyncMode(), user);

    if (!result) {
      throw new UnexpectedException("Error in syncing catalog entity as harness entity for given action, sync mode");
    }
  }
}
