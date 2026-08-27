/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener.gitops;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.GITOPS_AGENT_ENTITY;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.event.MessageListener;
import io.harness.scope.ScopeHelper;
import io.harness.service.instancesync.GitopsInstanceSyncService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(GITOPS)
@Slf4j
@Singleton
public class AgentCrudStreamListener implements MessageListener {
  private GitopsInstanceSyncService gitopsInstanceSyncService;

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap.get(ENTITY_TYPE) != null && GITOPS_AGENT_ENTITY.equals(metadataMap.get(ENTITY_TYPE))) {
        EntityChangeDTO entityChangeDTO;
        try {
          entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
        }
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processAgentEntityChangeEvent(entityChangeDTO, action);
        }
      }
    }
    return true;
  }

  private boolean processAgentEntityChangeEvent(EntityChangeDTO entityChangeDTO, String action) {
    if (DELETE_ACTION.equals(action)) {
      processDeleteEvent(entityChangeDTO);
    }
    return true;
  }

  private void processDeleteEvent(EntityChangeDTO entityChangeDTO) {
    try (AutoLogContext ignore1 =
             new AccountLogContext(entityChangeDTO.getAccountIdentifier().getValue(), OVERRIDE_NESTS)) {
      log.info("Deleting instances for agent deletion {}", entityChangeDTO.getIdentifier().getValue());
    }

    String scopedAgentIdentifier = ScopeHelper.getScopedIdentifier(entityChangeDTO.getAccountIdentifier().getValue(),
        entityChangeDTO.getOrgIdentifier().getValue(), entityChangeDTO.getProjectIdentifier().getValue(),
        entityChangeDTO.getIdentifier().getValue());

    gitopsInstanceSyncService.deleteInstancesForAgent(entityChangeDTO.getAccountIdentifier().getValue(),
        entityChangeDTO.getOrgIdentifier().getValue(), entityChangeDTO.getProjectIdentifier().getValue(),
        scopedAgentIdentifier);
  }
}
