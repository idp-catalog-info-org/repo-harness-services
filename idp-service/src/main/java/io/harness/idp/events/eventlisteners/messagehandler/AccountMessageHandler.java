/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.messagehandler;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.events.eventlisteners.utility.EventListenerLogger;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.namespace.service.NamespaceService;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class AccountMessageHandler implements EventMessageHandler {
  private NamespaceService namespaceService;
  private GitIntegrationServiceImpl gitIntegrationService;

  @Override
  public void handleMessage(Message message, Object dto, String action) {
    EventListenerLogger.logForEventReceived(message);
    EntityChangeDTO entityChangeDTO = (EntityChangeDTO) dto;
    if (namespaceService.getAccountIdpStatus(entityChangeDTO.getAccountIdentifier().getValue())) {
      switch (action) {
        case CREATE_ACTION, UPDATE_ACTION:
          gitIntegrationService.updateDefaultConnectorLessManagedHarnessCodeRepoIntegration(
              entityChangeDTO.getAccountIdentifier().getValue());
          break;
        default:
          log.warn("ACTION - {} is not to be handled by IDP account event handler", action);
      }
    }
  }
}
