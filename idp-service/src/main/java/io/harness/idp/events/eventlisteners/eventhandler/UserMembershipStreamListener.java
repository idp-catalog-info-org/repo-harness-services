/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.eventlisteners.eventhandler;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.NgEventLogContext;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.usermembership.UserMembershipDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.events.eventlisteners.factory.EventMessageHandlerFactory;
import io.harness.idp.events.eventlisteners.messagehandler.EventMessageHandler;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class UserMembershipStreamListener implements MessageListener {
  private static final String LOCK_NAME_FORMAT = "EVENT_%s_%s_%s";
  EventMessageHandlerFactory eventMessageHandlerFactory;
  ResourceLocker resourceLocker;

  @Override
  public boolean handleMessage(Message message) {
    if (message == null || !message.hasMessage()) {
      log.error(
          "Unable to complete processing the user membership event with the id because Message for the event was null");
      return true;
    }

    final String messageId = message.getId();
    try (AutoLogContext ignore = new NgEventLogContext(messageId, OVERRIDE_ERROR)) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      String entityType = metadataMap.get(ENTITY_TYPE);
      if (entityType == null) {
        log.error("Unable to complete processing the user membership event with the id {} because Entity Type for the "
                + "event was null",
            messageId);
        return true;
      }
      String action = metadataMap.get(ACTION);
      if (action == null) {
        log.error("Unable to complete processing the user membership event with the id {} because ACTION for the event "
                + "was null",
            messageId);
        return true;
      }
      EventMessageHandler eventMessageHandler = eventMessageHandlerFactory.getEventMessageHandler(entityType);

      if (eventMessageHandler != null) {
        AcquiredLock lock;
        UserMembershipDTO userMembershipDTO = null;
        try {
          userMembershipDTO = userMembershipDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking userMembershipDTO for key %s", message.getId()), e);
        }

        if (userMembershipDTO != null) {
          String lockUser = String.format(LOCK_NAME_FORMAT, userMembershipDTO.getScope().getAccountIdentifier(),
              entityType, userMembershipDTO.getUserId());
          lock = resourceLocker.acquireLock(lockUser);
          if (lock == null) {
            return false;
          }
          try {
            eventMessageHandler.handleMessage(message, userMembershipDTO, action);
            log.info("Completed processing the usermembership  event with the id {}", messageId);
          } catch (Exception e) {
            log.error("Error in handling the usermembership event with the id {} for entity type {}", messageId,
                entityType, e);
          } finally {
            resourceLocker.releaseLock(lock);
          }
          return true;
        }
      }
    }
    return true;
  }
}