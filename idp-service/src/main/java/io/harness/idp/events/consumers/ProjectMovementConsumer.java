/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.PROJECT_EVENT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkConstants.PROJECT_MOVEMENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.MOVE_ACTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.queue.QueueController;

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
public class ProjectMovementConsumer extends AbstractIdpServiceRedisStreamConsumer {
  @Inject private CatalogService catalogService;
  private static final String CONSUMER_NAME = "ProjectMovementConsumer";

  @Inject
  public ProjectMovementConsumer(
      @Named(PROJECT_MOVEMENT) Consumer redisConsumer, QueueController queueController, ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);

    if (message.hasMessage()) {
      try {
        Map<String, String> metadata = message.getMessage().getMetadataMap();
        String entityType = metadata.get(ENTITY_TYPE);

        boolean entityTypeAndActionValidation =
            entityTypeAndActionValidation(CONSUMER_NAME, message, PROJECT_EVENT_ENTITY, List.of(MOVE_ACTION));

        if (entityTypeAndActionValidation) {
          ByteString data = message.getMessage().getData();
          ProjectEntityChangeDTO projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(data);
          return lockAndProcessData(CONSUMER_NAME + "_EVENT_" + projectEntityChangeDTO.getAccountIdentifier() + "_"
                  + projectEntityChangeDTO.getOrgIdentifier() + "_" + projectEntityChangeDTO.getIdentifier(),
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
    ProjectEntityChangeDTO projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(data);
    handleProjectMovement(projectEntityChangeDTO);
  }

  void handleProjectMovement(ProjectEntityChangeDTO projectEntityChangeDTO) {
    log.info("Handling project movement for account={}, project={}, oldOrg={}, newOrg={}",
        projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getIdentifier(),
        projectEntityChangeDTO.getOldOrgIdentifier(), projectEntityChangeDTO.getOrgIdentifier());

    try {
      catalogService.projectMovement(projectEntityChangeDTO);
      log.info("Successfully completed project movement for account={}, project={}",
          projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getIdentifier());
    } catch (Exception e) {
      log.error("Failed to handle project movement for account={}, project={}. Error: {}",
          projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getIdentifier(), e.getMessage(), e);
      throw e;
    }
  }
}
