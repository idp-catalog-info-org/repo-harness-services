/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.messagehandler;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPSERT_ACTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.idp.integrations.beans.catalog.HarnessCDIntegrationSyncRequest;
import io.harness.idp.integrations.service.catalog.HarnessCDIntegrationOpsImpl;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class ServiceMessageHandler implements EventMessageHandler {
  private HarnessCDIntegrationOpsImpl harnessCDIntegrationOps;

  @Override
  public void handleMessage(Message message, Object dto, String action) {
    EntityChangeDTO entityChangeDTO = (EntityChangeDTO) dto;
    log.info("Received CD service change event - Account {}, Org {}, Project {}, Scope {}, ScopeUniqueId {}, "
            + "Identifier {}, Action {}",
        entityChangeDTO.getAccountIdentifier().getValue(), entityChangeDTO.getOrgIdentifier().getValue(),
        entityChangeDTO.getProjectIdentifier().getValue(), entityChangeDTO.getScopeInfo().getScope().name(),
        entityChangeDTO.getScopeInfo().getUniqueId().getValue(), entityChangeDTO.getIdentifier().getValue(), action);

    HarnessCDIntegrationSyncRequest harnessCDIntegrationSyncRequest =
        HarnessCDIntegrationSyncRequest.builder()
            .accountIdentifier(entityChangeDTO.getAccountIdentifier().getValue())
            .orgIdentifier(entityChangeDTO.getOrgIdentifier().getValue())
            .projectIdentifier(entityChangeDTO.getProjectIdentifier().getValue())
            .scope(entityChangeDTO.getScopeInfo().getScope().name())
            .scopeUniqueId(entityChangeDTO.getScopeInfo().getUniqueId().getValue())
            .identifier(entityChangeDTO.getIdentifier().getValue())
            .action(action)
            .build();

    switch (action) {
      case CREATE_ACTION, UPDATE_ACTION, UPSERT_ACTION, DELETE_ACTION:
        harnessCDIntegrationOps.performIncrementalSync(harnessCDIntegrationSyncRequest);
        break;
      default:
        log.warn("Unsupported action {} for service message handler", action);
    }
  }
}
