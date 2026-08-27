/*
 * Copyright 2026 Harness Inc. All rights reserved.
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
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.idp.events.eventlisteners.utility.EventListenerLogger;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class ProjectMessageHandler implements EventMessageHandler {
  IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;

  @Override
  public void handleMessage(Message message, Object dto, String action) {
    EventListenerLogger.logForEventReceived(message);
    ProjectEntityChangeDTO projectEntityChangeDTO = null;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("Exception in unpacking ProjectEntityChangeDTO for key {}", message.getId(), e);
    }
    if (Objects.isNull(projectEntityChangeDTO)) {
      return;
    }
    idpServiceMiscRedisProducer.publishProjectEventToRedis(projectEntityChangeDTO, action);
  }
}
