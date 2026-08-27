/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.START_ACTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.idp.IdpIntegrationCatalogProcessorEvent;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.idp.integrations.service.catalog.CatalogIntegrationServiceImpl;
import io.harness.queue.QueueController;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequestIntegrationEntities;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IdpIntegrationCatalogProcessorEventConsumer extends AbstractIdpServiceRedisStreamConsumer {
  private static final String CONSUMER_NAME = "IdpIntegrationCatalogProcessorEventConsumer";

  @Inject private CatalogIntegrationServiceImpl catalogIntegrationService;

  @Inject
  public IdpIntegrationCatalogProcessorEventConsumer(
      @Named(IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT) Consumer redisConsumer, QueueController queueController,
      ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);
    if (message.hasMessage()) {
      try {
        boolean entityTypeAndActionValidation = entityTypeAndActionValidation(
            CONSUMER_NAME, message, IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT, START_ACTION);
        Map<String, String> metadata = message.getMessage().getMetadataMap();
        if (entityTypeAndActionValidation) {
          ByteString data = message.getMessage().getData();
          IdpIntegrationCatalogProcessorEvent idpIntegrationCatalogProcessorEvent =
              IdpIntegrationCatalogProcessorEvent.parseFrom(data);
          return lockAndProcessData(CONSUMER_NAME + "_EVENT_"
                  + idpIntegrationCatalogProcessorEvent.getAccountIdentifier() + "_"
                  + idpIntegrationCatalogProcessorEvent.getIntegrationId(),
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
    IdpIntegrationCatalogProcessorEvent idpIntegrationCatalogProcessorEvent =
        IdpIntegrationCatalogProcessorEvent.parseFrom(data);
    SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest = new SaveDiscoverEntitiesRequest();
    saveDiscoverEntitiesRequest.setSelectionFilter(SaveDiscoverEntitiesRequest.SelectionFilterEnum.valueOf(
        idpIntegrationCatalogProcessorEvent.getSelectionFilter()));
    List<SaveDiscoverEntitiesRequestIntegrationEntities> integrationEntities = new ArrayList<>();
    idpIntegrationCatalogProcessorEvent.getIntegrationEntitiesList().forEach(integrationEntity -> {
      SaveDiscoverEntitiesRequestIntegrationEntities saveDiscoverEntitiesRequestIntegrationEntities =
          new SaveDiscoverEntitiesRequestIntegrationEntities();
      saveDiscoverEntitiesRequestIntegrationEntities.setIntegrationEntityId(integrationEntity.getIntegrationEntityId());
      saveDiscoverEntitiesRequestIntegrationEntities.setAction(
          SaveDiscoverEntitiesRequestIntegrationEntities.ActionEnum.valueOf(integrationEntity.getAction()));
      saveDiscoverEntitiesRequestIntegrationEntities.setActionDestination(integrationEntity.getActionDestination());
      if (!integrationEntity.getType().isEmpty()) {
        saveDiscoverEntitiesRequestIntegrationEntities.setType(integrationEntity.getType());
      }
      if (!integrationEntity.getActionIdentifier().isEmpty()) {
        saveDiscoverEntitiesRequestIntegrationEntities.setActionIdentifier(integrationEntity.getActionIdentifier());
      }
      integrationEntities.add(saveDiscoverEntitiesRequestIntegrationEntities);
    });
    saveDiscoverEntitiesRequest.setIntegrationEntities(integrationEntities);
    saveDiscoverEntitiesRequest.setAutoDiscover(idpIntegrationCatalogProcessorEvent.getAutoDiscover());
    catalogIntegrationService.saveDiscoverEntitiesInternal(idpIntegrationCatalogProcessorEvent.getAccountIdentifier(),
        idpIntegrationCatalogProcessorEvent.getIntegrationOrgIdentifier(),
        idpIntegrationCatalogProcessorEvent.getIntegrationProjectIdentifier(),
        idpIntegrationCatalogProcessorEvent.getIntegrationId(), saveDiscoverEntitiesRequest,
        idpIntegrationCatalogProcessorEvent.getUserPrincipal());
  }
}
