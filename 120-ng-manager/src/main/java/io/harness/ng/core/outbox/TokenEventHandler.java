/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.ng.core.events.TokenCreateEvent.TOKEN_CREATED;
import static io.harness.ng.core.events.TokenDeleteEvent.TOKEN_DELETED;
import static io.harness.ng.core.events.TokenExpireEvent.TOKEN_EXPIRED;
import static io.harness.ng.core.events.TokenUpdateEvent.TOKEN_UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.ScopeInfo;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.TokenMode;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenRequest;
import io.harness.ng.core.events.TokenCreateEvent;
import io.harness.ng.core.events.TokenDeleteEvent;
import io.harness.ng.core.events.TokenExpireEvent;
import io.harness.ng.core.events.TokenUpdateEvent;
import io.harness.ng.core.utils.TokenNotificationUtils;
import io.harness.notification.entities.NotificationEvent;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
public class TokenEventHandler implements OutboxEventHandler {
  private final ObjectMapper objectMapper;
  private final Producer eventProducer;
  private final AuditClientService auditClientService;
  private final TokenNotificationUtils tokenNotificationUtils;

  @Inject
  public TokenEventHandler(@Named(EventsFrameworkConstants.ENTITY_CRUD) Producer eventProducer,
      AuditClientService auditClientService, TokenNotificationUtils tokenNotificationUtils) {
    this.objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    this.eventProducer = eventProducer;
    this.auditClientService = auditClientService;
    this.tokenNotificationUtils = tokenNotificationUtils;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case TOKEN_CREATED:
          return handleTokenCreateEvent(outboxEvent);
        case TOKEN_UPDATED:
          return handleTokenUpdateEvent(outboxEvent);
        case TOKEN_DELETED:
          return handleTokenDeleteEvent(outboxEvent);
        case TOKEN_EXPIRED:
          return handleTokenExpireEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handleTokenCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();
    TokenCreateEvent TokenCreateEvent = objectMapper.readValue(outboxEvent.getEventData(), TokenCreateEvent.class);
    TokenDTO createdToken = TokenCreateEvent.getToken();
    ScopeInfo scopeInfo = getScopeInfo(createdToken);
    boolean publishedToRedis = publishEvent(createdToken, CREATE_ACTION, scopeInfo);

    boolean auditPublished;
    if (shouldPublishAudit(createdToken)) {
      AuditEntry auditEntry = AuditEntry.builder()
                                  .action(Action.CREATE)
                                  .module(ModuleType.CORE)
                                  .newYaml(getYamlString(TokenRequest.builder().token(createdToken).build()))
                                  .timestamp(outboxEvent.getCreatedAt())
                                  .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                  .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                  .insertId(outboxEvent.getId())
                                  .build();
      auditPublished = auditClientService.publishAudit(auditEntry, globalContext);
    } else {
      auditPublished = true;
    }

    try {
      tokenNotificationUtils.sendTokenNotification(
          createdToken, NotificationEvent.TOKEN_CREATED, NotificationEvent.TOKEN_CREATED.name(), null);
    } catch (Exception e) {
      log.error("Failed to send notification for token create event, tokenId: {}", createdToken.getIdentifier(), e);
    }

    return publishedToRedis && auditPublished;
  }

  private boolean handleTokenUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    TokenUpdateEvent TokenUpdateEvent = objectMapper.readValue(outboxEvent.getEventData(), TokenUpdateEvent.class);
    TokenDTO updatedToken = TokenUpdateEvent.getNewToken();
    ScopeInfo scopeInfo = getScopeInfo(updatedToken);

    boolean publishedToRedis = publishEvent(updatedToken, EventsFrameworkMetadataConstants.UPDATE_ACTION, scopeInfo);

    boolean auditPublished;
    if (shouldPublishAudit(updatedToken)) {
      AuditEntry auditEntry =
          AuditEntry.builder()
              .action(Action.UPDATE)
              .module(ModuleType.CORE)
              .newYaml(getYamlString(TokenRequest.builder().token(updatedToken).build()))
              .oldYaml(getYamlString(TokenRequest.builder().token(TokenUpdateEvent.getOldToken()).build()))
              .timestamp(outboxEvent.getCreatedAt())
              .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
              .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
              .insertId(outboxEvent.getId())
              .build();
      auditPublished = auditClientService.publishAudit(auditEntry, globalContext);
    } else {
      auditPublished = true;
    }

    boolean isRotation = updatedToken.getIdentifier() != null && updatedToken.getIdentifier().startsWith("rotated_");
    NotificationEvent notificationEvent = isRotation ? NotificationEvent.TOKEN_ROTATED : NotificationEvent.TOKEN_EDITED;
    String idempotencyPrefix =
        isRotation ? NotificationEvent.TOKEN_ROTATED.name() : NotificationEvent.TOKEN_EDITED.name();

    // For rotation events, use the old token's identifier and name so users recognize which token was rotated
    if (isRotation) {
      TokenDTO oldToken = TokenUpdateEvent.getOldToken();
      updatedToken.setIdentifier(oldToken.getIdentifier());
      updatedToken.setName(oldToken.getName());
    }

    try {
      tokenNotificationUtils.sendTokenNotification(updatedToken, notificationEvent, idempotencyPrefix, null);
    } catch (Exception e) {
      log.error("Failed to send notification for token update event, tokenId: {}", updatedToken.getIdentifier(), e);
    }
    return publishedToRedis && auditPublished;
  }

  private boolean handleTokenDeleteEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    TokenDeleteEvent TokenDeleteEvent = objectMapper.readValue(outboxEvent.getEventData(), TokenDeleteEvent.class);
    TokenDTO deletedToken = TokenDeleteEvent.getToken();
    ScopeInfo scopeInfo = getScopeInfo(deletedToken);

    boolean publishedToRedis = publishEvent(deletedToken, EventsFrameworkMetadataConstants.DELETE_ACTION, scopeInfo);

    boolean auditPublished;
    if (shouldPublishAudit(deletedToken)) {
      AuditEntry auditEntry = AuditEntry.builder()
                                  .action(Action.DELETE)
                                  .module(ModuleType.CORE)
                                  .oldYaml(getYamlString(TokenRequest.builder().token(deletedToken).build()))
                                  .timestamp(outboxEvent.getCreatedAt())
                                  .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                  .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                  .insertId(outboxEvent.getId())
                                  .build();
      auditPublished = auditClientService.publishAudit(auditEntry, globalContext);
    } else {
      auditPublished = true;
    }

    try {
      tokenNotificationUtils.sendTokenNotification(
          deletedToken, NotificationEvent.TOKEN_DELETED, NotificationEvent.TOKEN_DELETED.name(), null);
    } catch (Exception e) {
      log.error("Failed to send notification for token delete event, tokenId: {}", deletedToken.getIdentifier(), e);
    }

    return publishedToRedis && auditPublished;
  }

  private boolean handleTokenExpireEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    TokenExpireEvent TokenExpireEvent = objectMapper.readValue(outboxEvent.getEventData(), TokenExpireEvent.class);
    TokenDTO expiredToken = TokenExpireEvent.getToken();

    boolean auditPublished;
    if (shouldPublishAudit(expiredToken)) {
      AuditEntry auditEntry = AuditEntry.builder()
                                  .action(Action.EXPIRED)
                                  .module(ModuleType.CORE)
                                  .oldYaml(getYamlString(TokenRequest.builder().token(expiredToken).build()))
                                  .timestamp(outboxEvent.getCreatedAt())
                                  .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
                                  .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
                                  .insertId(outboxEvent.getId())
                                  .build();
      auditPublished = auditClientService.publishAudit(auditEntry, globalContext);
    } else {
      auditPublished = true;
    }

    try {
      tokenNotificationUtils.sendTokenNotification(
          expiredToken, NotificationEvent.TOKEN_EXPIRED, NotificationEvent.TOKEN_EXPIRED.name(), null);
    } catch (Exception e) {
      log.error("Failed to send notification for token expire event, tokenId: {}", expiredToken.getIdentifier(), e);
    }

    return auditPublished;
  }

  private boolean shouldPublishAudit(TokenDTO token) {
    return !(ApiKeyType.SCOPED_TOKEN.equals(token.getApiKeyType()) && TokenMode.EPHEMERAL.equals(token.getTokenMode()));
  }

  private boolean publishEvent(TokenDTO tokenDTO, String action, ScopeInfo scopeInfo) {
    try {
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", tokenDTO.getAccountIdentifier(),
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.TOKEN_ENTITY,
                  EventsFrameworkMetadataConstants.ACTION, action))
              .setData(EntityChangeDTO.newBuilder()
                           .setIdentifier(StringValue.of(tokenDTO.getIdentifier()))
                           .setOrgIdentifier(tokenDTO.getOrgIdentifier() != null
                                   ? StringValue.of(tokenDTO.getOrgIdentifier())
                                   : StringValue.of(""))
                           .setProjectIdentifier(tokenDTO.getProjectIdentifier() != null
                                   ? StringValue.of(tokenDTO.getProjectIdentifier())
                                   : StringValue.of(""))
                           .setUniqueId(StringValue.of(tokenDTO.getUniqueId()))
                           .setScopeInfo(scopeInfo)
                           .build()
                           .toByteString())
              .build());
      return true;
    } catch (EventsFrameworkDownException e) {
      log.error(
          "Failed to send " + action + " event to events framework api key identifier: " + tokenDTO.getIdentifier(), e);
      return false;
    }
  }

  private ScopeInfo getScopeInfo(TokenDTO tokenDTO) {
    return ScopeInfo.newBuilder()
        .setUniqueId(StringValue.of(tokenDTO.getParentUniqueId()))
        .setScope(getScopeType(tokenDTO))
        .build();
  }

  private ScopeProtoEnum getScopeType(TokenDTO tokenDTO) {
    if (isNotEmpty(tokenDTO.getOrgIdentifier())) {
      if (isEmpty(tokenDTO.getProjectIdentifier())) {
        return ScopeProtoEnum.ORG;
      } else {
        return ScopeProtoEnum.PROJECT;
      }
    }
    return ScopeProtoEnum.ACCOUNT;
  }
}
