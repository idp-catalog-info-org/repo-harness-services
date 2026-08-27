/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_INTEGRATION_CRUD_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.clients.integrationmanager.TypesIntegrationConfig;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.idp.IntegrationDetails;
import io.harness.idp.catalog.service.EntityLinkService;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IDPIntegrationCrudEventRedisConsumer extends AbstractIdpServiceRedisStreamConsumer {
  private static final String CONSUMER_NAME = "IDPIntegrationCrudEventRedisConsumer";
  private static final String INTEGRATION_CONFIG_DELETE_ACTION = "integration_config_delete";

  @Inject private CatalogIntegrationServiceImpl catalogIntegrationService;
  @Inject private EntityLinkService entityLinkService;

  @Inject
  public IDPIntegrationCrudEventRedisConsumer(@Named(IDP_INTEGRATION_CRUD_EVENT) Consumer redisConsumer,
      QueueController queueController, ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);
    if (message.hasMessage()) {
      try {
        ByteString data = message.getMessage().getData();
        IntegrationDetails integrationDetails = IntegrationDetails.parseFrom(data);

        String integrationSpacePath = integrationDetails.getIntegrationSpacePath();
        String[] scopeParts = integrationSpacePath.split("/");
        String accountIdentifier = scopeParts.length > 0 ? emptyToNull(scopeParts[0]) : null;
        String orgIdentifier = scopeParts.length > 1 ? emptyToNull(scopeParts[1]) : null;
        String projectIdentifier = scopeParts.length > 2 ? emptyToNull(scopeParts[2]) : null;

        if (INTEGRATION_CONFIG_DELETE_ACTION.equalsIgnoreCase(integrationDetails.getAction())) {
          entityLinkService.deleteLinksForIntegration(
              accountIdentifier, integrationDetails.getIntegrationID(), integrationSpacePath);
        } else if (integrationDetails.getAction().equalsIgnoreCase(DELETE_ACTION)) {
          String idpKind = integrationDetails.getIDPDataMap().get("kind") != null
              ? integrationDetails.getIDPDataMap().get("kind").toLowerCase()
              : null;
          catalogIntegrationService.unlinkIntegrationEntity(accountIdentifier, orgIdentifier, projectIdentifier,
              integrationDetails.getIntegrationID(), integrationDetails.getUUID(), idpKind,
              integrationDetails.getKind());
        } else {
          TypesIntegrationConfig integrationConfig = catalogIntegrationService.getIntegrationConfig(
              accountIdentifier, orgIdentifier, projectIdentifier, integrationDetails.getIntegrationID());
          SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest = new SaveDiscoverEntitiesRequest();
          saveDiscoverEntitiesRequest.setAutoDiscover(
              (Boolean) integrationConfig.getConfiguration().get("auto_import"));
          List<SaveDiscoverEntitiesRequestIntegrationEntities> integrationEntities = new ArrayList<>();
          SaveDiscoverEntitiesRequestIntegrationEntities saveDiscoverEntitiesRequestIntegrationEntities =
              new SaveDiscoverEntitiesRequestIntegrationEntities();
          saveDiscoverEntitiesRequestIntegrationEntities.setIntegrationEntityId(integrationDetails.getUUID());
          integrationEntities.add(saveDiscoverEntitiesRequestIntegrationEntities);
          saveDiscoverEntitiesRequest.setIntegrationEntities(integrationEntities);
          catalogIntegrationService.saveDiscoverEntitiesInternal(accountIdentifier, orgIdentifier, projectIdentifier,
              integrationDetails.getIntegrationID(), saveDiscoverEntitiesRequest, null);
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
  protected void processInternal(String entityType, ByteString data) {}

  private static String emptyToNull(String s) {
    return StringUtils.isBlank(s) ? null : s;
  }
}
