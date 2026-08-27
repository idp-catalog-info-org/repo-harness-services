/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.messagehandler;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.RESTORE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.service.CatalogScopeResolver;
import io.harness.idp.events.eventlisteners.utility.EventListenerLogger;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class OrganizationMessageHandler implements EventMessageHandler {
  private CatalogEntityRepository catalogEntityRepository;
  private CatalogScopeResolver catalogScopeResolver;

  @Override
  public void handleMessage(Message message, Object dto, String action) {
    EventListenerLogger.logForEventReceived(message);
    OrganizationEntityChangeDTO organizationEntityChangeDTO = null;
    try {
      organizationEntityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking OrganizationEntityChangeDTO for key {}", message.getId(), e);
    }
    if (Objects.isNull(organizationEntityChangeDTO)) {
      return;
    }
    String accountIdentifier = organizationEntityChangeDTO.getAccountIdentifier();
    if (!catalogEntityRepository.existsByAccountIdentifier(accountIdentifier)) {
      log.info("Skipping scope topology rebuild for account={} as it has no catalog entities (not an IDP account)",
          accountIdentifier);
      return;
    }
    switch (action) {
      case CREATE_ACTION, UPDATE_ACTION, DELETE_ACTION, RESTORE_ACTION:
        catalogScopeResolver.buildScopeTopology(accountIdentifier);
        log.info("Rebuilt scope topology cache for account={} due to organization {} action={}", accountIdentifier,
            organizationEntityChangeDTO.getIdentifier(), action);
        break;
      default:
        log.warn("ACTION - {} is not to be handled by IDP organization event handler", action);
    }
  }
}
