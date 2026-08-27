/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_API_ENDPOINT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITY_V3;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.idp.IdpCatalogEntitiesSyncCaptureEventV3;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor;
import io.harness.idp.catalog.processor.api.ApiEndpointProcessor.ProcessingOutcome;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumes catalog CREATE/UPDATE events off the shared V3 stream and runs OpenAPI endpoint
 * extraction for API entities on FF-enabled accounts. Reloads the entity so re-delivered messages
 * process the latest state, then delegates to {@link ApiEndpointProcessor}.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IdpCatalogApiEndpointConsumerV3 extends AbstractIdpServiceRedisStreamConsumer {
  private static final String CONSUMER_NAME = "IdpCatalogApiEndpointConsumerV3";

  @Inject private CatalogEntityRepository catalogEntityRepository;
  @Inject private ApiEndpointProcessor apiEndpointProcessor;
  @Inject private IdpCommonService idpCommonService;

  @Inject
  public IdpCatalogApiEndpointConsumerV3(@Named(IDP_CATALOG_ENTITIES_V3_API_ENDPOINT) Consumer redisConsumer,
      QueueController queueController, ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);

    if (message.hasMessage()) {
      try {
        String entityType = message.getMessage().getMetadataMap().get(ENTITY_TYPE);
        boolean entityTypeAndActionValidation = entityTypeAndActionValidation(
            CONSUMER_NAME, message, IDP_CATALOG_ENTITY_V3, List.of(CREATE_ACTION, UPDATE_ACTION));
        if (entityTypeAndActionValidation) {
          ByteString data = message.getMessage().getData();
          IdpCatalogEntitiesSyncCaptureEventV3 event = IdpCatalogEntitiesSyncCaptureEventV3.parseFrom(data);
          return lockAndProcessData(
              CONSUMER_NAME + "_EVENT_" + event.getAccountIdentifier() + "_" + event.getEntityRef(), entityType, data);
        }
      } catch (Exception ex) {
        log.error("Error in processing message with id: {} in {} consumer. Error = {}", message.getId(), CONSUMER_NAME,
            ex.getMessage(), ex);
        return false;
      }
    }
    return true;
  }

  @Override
  protected void processInternal(String entityType, ByteString data) throws Exception {
    IdpCatalogEntitiesSyncCaptureEventV3 event = IdpCatalogEntitiesSyncCaptureEventV3.parseFrom(data);

    // Gate on FF (and kind, encoded in entityRef) before hitting the DB so ineligible events ack cheaply.
    String kindFromRef = extractKind(event.getEntityRef());
    if (kindFromRef == null) {
      log.warn("Malformed entityRef in event: {}", event.getEntityRef());
      return;
    }
    if (!Constants.API_KIND.equalsIgnoreCase(kindFromRef)) {
      log.debug("Skipping API endpoint extraction for non-API kind {} in {}", kindFromRef, CONSUMER_NAME);
      return;
    }
    boolean ffEnabled;
    try {
      ffEnabled = idpCommonService.idpApiEndpointExtractionEnabled(event.getAccountIdentifier());
    } catch (Exception ex) {
      // FF service hiccup: skip and ack rather than redeliver, else a prolonged FF outage would
      // hot-loop the consumer group. The backstop iterator picks the entity up later.
      log.warn("Failed to check IDP_API_ENDPOINT_EXTRACTION FF for account {} in {}; skipping. Error = {}",
          event.getAccountIdentifier(), CONSUMER_NAME, ex.getMessage());
      return;
    }
    if (!ffEnabled) {
      log.debug(
          "IDP_API_ENDPOINT_EXTRACTION not enabled for account {} in {}", event.getAccountIdentifier(), CONSUMER_NAME);
      return;
    }

    Optional<CatalogEntity> entityOpt = loadEntity(event);
    if (entityOpt.isEmpty()) {
      log.info("Skipping API endpoint extraction — entity not found (likely deleted) for entityRef {} in account {}",
          event.getEntityRef(), event.getAccountIdentifier());
      return;
    }
    CatalogEntity entity = entityOpt.get();

    ProcessingOutcome outcome = apiEndpointProcessor.processEntity(entity);
    log.info("Outcome for entityRef {} (account {}): status={} oldKeys={} newKeys={} warnings={} error={}",
        event.getEntityRef(), event.getAccountIdentifier(), outcome.getStatus(), outcome.getOldKeys().size(),
        outcome.getNewKeys().size(), outcome.getWarnings().size(), outcome.getErrorMessage());
  }

  /** {@code entityRef} format: {@code kind:scope/identifier} (per {@code CatalogUtils.entityRef}). */
  private static String extractKind(String entityRef) {
    if (entityRef == null) {
      return null;
    }
    int colonIndex = entityRef.indexOf(':');
    int slashIndex = entityRef.indexOf('/');
    if (colonIndex < 0 || slashIndex <= colonIndex) {
      return null;
    }
    return entityRef.substring(0, colonIndex);
  }

  private Optional<CatalogEntity> loadEntity(IdpCatalogEntitiesSyncCaptureEventV3 event) {
    String entityRef = event.getEntityRef();
    String kind = extractKind(entityRef);
    if (kind == null) {
      log.warn("Malformed entityRef in event: {}", entityRef);
      return Optional.empty();
    }
    String identifier = entityRef.substring(entityRef.indexOf('/') + 1);
    return catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
        event.getParentUniqueId(), kind, identifier);
  }
}
