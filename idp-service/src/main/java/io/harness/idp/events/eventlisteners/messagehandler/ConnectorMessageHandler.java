/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.messagehandler;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.eventlisteners.utility.EventListenerLogger;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.namespace.service.NamespaceService;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class ConnectorMessageHandler implements EventMessageHandler {
  private GitIntegrationServiceImpl gitIntegrationService;
  private CatalogService catalogService;
  private NamespaceService namespaceService;
  private IdpCommonService idpCommonService;

  @Override
  public void handleMessage(Message message, Object dto, String action) throws Exception {
    EventListenerLogger.logForEventReceived(message);
    EntityChangeDTO entityChangeDTO = (EntityChangeDTO) dto;
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    String orgIdentifier = entityChangeDTO.getOrgIdentifier().getValue();
    String projectIdentifier = entityChangeDTO.getProjectIdentifier().getValue();
    String connectorIdentifier = entityChangeDTO.getIdentifier().getValue();
    boolean isIDPEnabled = namespaceService.getAccountIdpStatus(accountIdentifier);

    if (CommonUtils.checkIfAccountLevelEvent(accountIdentifier, orgIdentifier, projectIdentifier) && isIDPEnabled) {
      switch (action) {
        case UPDATE_ACTION ->
                gitIntegrationService.processConnectorUpdate(accountIdentifier, connectorIdentifier);
        case DELETE_ACTION ->
                gitIntegrationService.processConnectorDelete(accountIdentifier, connectorIdentifier);
        default -> log.warn("ACTION - {} is not to be handled by IDP connector event handler", action);
      }
    }

    if (isIDPEnabled && idpCommonService.idpV2Enabled(accountIdentifier)) {
      connectorIdentifier = CommonUtils.getScopedIdentifier(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier);
      switch (action) {
        case UPDATE_ACTION ->
                catalogService.updateSourceCodeInEntityOnConnectorUpdate(entityChangeDTO.getAccountIdentifier().getValue(), entityChangeDTO.getOrgIdentifier().getValue(),
                        entityChangeDTO.getProjectIdentifier().getValue(), connectorIdentifier);
        case DELETE_ACTION ->
                catalogService.removeSourceCodeReferencesOnConnectorDeletion(entityChangeDTO.getAccountIdentifier().getValue(), entityChangeDTO.getOrgIdentifier().getValue(),
                        entityChangeDTO.getProjectIdentifier().getValue(), connectorIdentifier);
        default -> log.warn("ACTION - {} is not to be handled by IDP connector event handler", action);
      }
    }
  }
}