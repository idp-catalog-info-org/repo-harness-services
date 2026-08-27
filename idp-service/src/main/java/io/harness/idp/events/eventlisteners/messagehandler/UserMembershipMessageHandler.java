/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.messagehandler;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.usermembership.UserMembershipDTO;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.eventlisteners.utility.EventListenerLogger;
import io.harness.idp.namespace.service.NamespaceService;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class UserMembershipMessageHandler implements EventMessageHandler {
  IdpCommonService idpCommonService;
  CatalogService catalogService;
  NamespaceService namespaceService;

  @Override
  public void handleMessage(Message message, Object dto, String action) {
    EventListenerLogger.logForEventReceived(message);
    UserMembershipDTO userMembershipDTO = (UserMembershipDTO) dto;

    log.debug("Received user membership message: {}", userMembershipDTO);

    String accountIdentifier = userMembershipDTO.getScope().getAccountIdentifier();

    if (CommonUtils.checkIfAccountLevelEvent(accountIdentifier, userMembershipDTO.getScope().getOrgIdentifier(),
            userMembershipDTO.getScope().getProjectIdentifier())
        && namespaceService.getAccountIdpStatus(accountIdentifier)) {
      log.debug("Handling user membership message: {}", userMembershipDTO);
      catalogService.handleUserBasedOnAction(accountIdentifier, userMembershipDTO, action);
    }
  }
}
