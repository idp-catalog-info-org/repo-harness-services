/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.PROJECT_EVENT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkConstants.PROJECT_TOPOLOGY_REBUILD;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.MOVE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogScopeResolver;
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
public class ProjectTopologyRebuildConsumer extends AbstractIdpServiceRedisStreamConsumer {
  @Inject private CatalogScopeResolver catalogScopeResolver;
  @Inject private CatalogEntityRepository catalogEntityRepository;
  private static final String CONSUMER_NAME = "ProjectTopologyRebuildConsumer";

  @Inject
  public ProjectTopologyRebuildConsumer(@Named(PROJECT_TOPOLOGY_REBUILD) Consumer redisConsumer,
      QueueController queueController, ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);

    if (message.hasMessage()) {
      try {
        Map<String, String> metadata = message.getMessage().getMetadataMap();
        String entityType = metadata.get(ENTITY_TYPE);
        ByteString data = message.getMessage().getData();
        ProjectEntityChangeDTO projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(data);
        String accountIdentifier = projectEntityChangeDTO.getAccountIdentifier();

        if (!catalogEntityRepository.existsByAccountIdentifier(accountIdentifier)) {
          log.info("Skipping scope topology rebuild for account={} as it has no catalog entities (not an IDP account)",
              accountIdentifier);
          return true;
        }

        boolean entityTypeAndActionValidation = entityTypeAndActionValidation(CONSUMER_NAME, message,
            PROJECT_EVENT_ENTITY, List.of(CREATE_ACTION, UPDATE_ACTION, DELETE_ACTION, RESTORE_ACTION, MOVE_ACTION));

        if (entityTypeAndActionValidation) {
          return lockAndProcessData(CONSUMER_NAME + "_EVENT_" + accountIdentifier + "_"
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
    handleScopeTopologyRebuild(projectEntityChangeDTO);
  }

  void handleScopeTopologyRebuild(ProjectEntityChangeDTO projectEntityChangeDTO) {
    log.info("Rebuilding scope topology for account={}, project={}", projectEntityChangeDTO.getAccountIdentifier(),
        projectEntityChangeDTO.getIdentifier());

    try {
      catalogScopeResolver.buildScopeTopology(projectEntityChangeDTO.getAccountIdentifier());
      log.info("Successfully rebuilt scope topology cache for account={}, project={}",
          projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getIdentifier());
    } catch (Exception e) {
      log.error("Failed to rebuild scope topology for account={}, project={}. Error: {}",
          projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getIdentifier(), e.getMessage(), e);
    }
  }
}
