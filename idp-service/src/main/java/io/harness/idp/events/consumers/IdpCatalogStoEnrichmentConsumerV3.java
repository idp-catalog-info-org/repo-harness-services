/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITIES_V3_STO_ENRICHMENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITY_V3;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.idp.catalog.utils.Constants.CORE_KINDS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.idp.IdpCatalogEntitiesSyncCaptureEventV3;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.STOHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IdpCatalogStoEnrichmentConsumerV3 extends AbstractIdpServiceRedisStreamConsumer {
  @Inject private CatalogEntityRepository catalogEntityRepository;
  @Inject private STOHelper stoHelper;
  @Inject private IdpCommonService idpCommonService;
  private static final String CONSUMER_NAME = "IdpCatalogStoEnrichmentConsumerV3";

  @Inject
  public IdpCatalogStoEnrichmentConsumerV3(@Named(IDP_CATALOG_ENTITIES_V3_STO_ENRICHMENT) Consumer redisConsumer,
      QueueController queueController, ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);

    if (message.hasMessage()) {
      boolean entityTypeAndActionValidation;
      try {
        Map<String, String> metadata = message.getMessage().getMetadataMap();
        String entityType = metadata.get(ENTITY_TYPE);
        entityTypeAndActionValidation = entityTypeAndActionValidation(
            CONSUMER_NAME, message, IDP_CATALOG_ENTITY_V3, List.of(CREATE_ACTION, UPDATE_ACTION));
        if (entityTypeAndActionValidation) {
          ByteString data = message.getMessage().getData();
          IdpCatalogEntitiesSyncCaptureEventV3 idpCatalogEntitiesSyncCaptureEventV3 =
              IdpCatalogEntitiesSyncCaptureEventV3.parseFrom(data);
          return lockAndProcessData(CONSUMER_NAME + "_EVENT_"
                  + idpCatalogEntitiesSyncCaptureEventV3.getAccountIdentifier() + "_"
                  + idpCatalogEntitiesSyncCaptureEventV3.getEntityRef(),
              entityType, data);
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
    IdpCatalogEntitiesSyncCaptureEventV3 idpCatalogEntitiesSyncCaptureEventV3 =
        IdpCatalogEntitiesSyncCaptureEventV3.parseFrom(data);
    handleStoEnrichment(idpCatalogEntitiesSyncCaptureEventV3);
  }

  void handleStoEnrichment(IdpCatalogEntitiesSyncCaptureEventV3 idpCatalogEntitiesSyncCaptureEventV3) {
    log.info("Handling STO enrichment for account={}, entityRef={}, action={}",
        idpCatalogEntitiesSyncCaptureEventV3.getAccountIdentifier(),
        idpCatalogEntitiesSyncCaptureEventV3.getEntityRef(), idpCatalogEntitiesSyncCaptureEventV3.getAction());

    String[] entityRefSplit = idpCatalogEntitiesSyncCaptureEventV3.getEntityRef().split(":");
    String entityKind = entityRefSplit[0];
    if (!CORE_KINDS.contains(entityKind)) {
      log.debug("Skipping STO enrichment for non-core kind {} in {}", entityKind, CONSUMER_NAME);
      return;
    }
    String entityScopeIdentifier = entityRefSplit[1];
    int slashIndex = entityScopeIdentifier.indexOf("/");
    String entityIdentifier =
        slashIndex != -1 ? entityScopeIdentifier.substring(slashIndex + 1) : entityScopeIdentifier;

    Optional<CatalogEntity> optionalCatalogEntity = catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(
        idpCatalogEntitiesSyncCaptureEventV3.getParentUniqueId(), entityKind, entityIdentifier);

    if (optionalCatalogEntity.isEmpty()) {
      log.error("No catalog entity found for accountIdentifier={}, entityRef={} in {}",
          idpCatalogEntitiesSyncCaptureEventV3.getAccountIdentifier(),
          idpCatalogEntitiesSyncCaptureEventV3.getEntityRef(), CONSUMER_NAME);
      return;
    }

    CatalogEntity catalogEntity = optionalCatalogEntity.get();
    if (!idpCommonService.idpStoEnabled(catalogEntity.getAccountIdentifier())) {
      log.debug("STO is not enabled for account {} in {}", catalogEntity.getAccountIdentifier(), CONSUMER_NAME);
      return;
    }

    try {
      log.info("Populating STO data for entity {} in {}", idpCatalogEntitiesSyncCaptureEventV3.getEntityRef(),
          CONSUMER_NAME);
      stoHelper.populateSTOData(catalogEntity);
      log.info("Successfully enriched entity {} with STO data in {}",
          idpCatalogEntitiesSyncCaptureEventV3.getEntityRef(), CONSUMER_NAME);
    } catch (Exception e) {
      log.error("Failed to populate STO data for catalog entity {} in {}. Error: {}", catalogEntity.getIdentifier(),
          CONSUMER_NAME, e.getMessage(), e);
    }
  }
}
