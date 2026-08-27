/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CONNECTOR_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.SECRET_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;
import io.harness.perpetualtask.entityreference.PerpetualTaskEntityReferenceService;
import io.harness.perpetualtask.entityreference.PerpetualTaskReferenceEntityType;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;

/**
 * Dedicated entity_crud listener that refreshes or stops perpetual tasks whose referenced connector, secret, or secret
 * manager (secret managers are connectors) was updated or deleted. Owns its handling instead of piggybacking on other
 * entity handlers.
 */
@OwnedBy(CDC)
@Singleton
public class PerpetualTaskEntityReferenceCRUDStreamListener implements MessageListener {
  @Inject private PerpetualTaskEntityReferenceService perpetualTaskEntityReferenceService;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Override
  public boolean handleMessage(Message message) {
    if (message == null || !message.hasMessage()) {
      return true;
    }
    Map<String, String> metadataMap = message.getMessage().getMetadataMap();
    if (metadataMap == null) {
      return true;
    }
    String action = metadataMap.get(ACTION);
    if (!UPDATE_ACTION.equals(action) && !DELETE_ACTION.equals(action)) {
      return true;
    }
    PerpetualTaskReferenceEntityType referenceEntityType = toReferenceEntityType(metadataMap.get(ENTITY_TYPE));
    if (referenceEntityType == null) {
      return true;
    }
    EntityChangeDTO entityChangeDTO;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      // Do not acknowledge a message we could not parse; surface it so it is retried/dead-lettered rather than dropped.
      throw new InvalidRequestException(
          String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
    }
    String accountId = entityChangeDTO.getAccountIdentifier().getValue();
    if (!ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_REFRESH_PERPETUAL_TASK_ON_ENTITY_UPDATE)) {
      return true;
    }
    String entityRef =
        IdentifierRefHelper
            .getIdentifierRef(entityChangeDTO.getIdentifier().getValue(), accountId,
                entityChangeDTO.getOrgIdentifier().getValue(), entityChangeDTO.getProjectIdentifier().getValue())
            .buildScopedIdentifier();
    perpetualTaskEntityReferenceService.onEntityUpdated(accountId, referenceEntityType, entityRef, action);
    return true;
  }

  private PerpetualTaskReferenceEntityType toReferenceEntityType(String entityType) {
    if (CONNECTOR_ENTITY.equals(entityType)) {
      return PerpetualTaskReferenceEntityType.CONNECTOR;
    } else if (SECRET_ENTITY.equals(entityType)) {
      return PerpetualTaskReferenceEntityType.SECRET;
    }
    return null;
  }
}
