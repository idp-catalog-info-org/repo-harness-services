/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers;

import static io.harness.idp.settings.events.PermissionsCreateEvent.PERMISSIONS_CREATED;
import static io.harness.idp.settings.events.PermissionsUpdateEvent.PERMISSIONS_UPDATED;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.ModuleType;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.beans.ResourceDTO;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.audit.client.api.AuditClientService;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrails.eventhandlers.dtos.PermissionsDTO;
import io.harness.idp.settings.events.PermissionsCreateEvent;
import io.harness.idp.settings.events.PermissionsUpdateEvent;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxEventHandler;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PermissionsEventHandler implements OutboxEventHandler {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;
  private final AuditClientService auditClientService;

  @Inject
  public PermissionsEventHandler(AuditClientService auditClientService) {
    this.auditClientService = auditClientService;
  }

  @Override
  public boolean handle(OutboxEvent outboxEvent) {
    try {
      switch (outboxEvent.getEventType()) {
        case PERMISSIONS_CREATED:
          return handlePermissionsCreateEvent(outboxEvent);
        case PERMISSIONS_UPDATED:
          return handlePermissionsUpdateEvent(outboxEvent);
        default:
          throw new InvalidArgumentsException(String.format("Not supported event type %s", outboxEvent.getEventType()));
      }
    } catch (IOException exception) {
      log.error("Failed to handle " + outboxEvent.getEventType() + " event", exception);
      return false;
    }
  }

  private boolean handlePermissionsCreateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    PermissionsCreateEvent permissionsCreateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PermissionsCreateEvent.class);
    BackstagePermissions newBackstagePermissions = permissionsCreateEvent.getNewBackstagePermissions();
    List<String> newBackstagePermissionList = newBackstagePermissions.getPermissions();
    Collections.sort(newBackstagePermissionList);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.CREATE)
            .module(ModuleType.IDP)
            .newYaml(NGYamlUtils.getYamlString(PermissionsDTO.builder()
                                                   .permissions(newBackstagePermissionList)
                                                   .userGroupIdentifiers(newBackstagePermissions.getUserGroups())
                                                   .build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }

  private boolean handlePermissionsUpdateEvent(OutboxEvent outboxEvent) throws IOException {
    GlobalContext globalContext = outboxEvent.getGlobalContext();

    PermissionsUpdateEvent permissionsUpdateEvent =
        objectMapper.readValue(outboxEvent.getEventData(), PermissionsUpdateEvent.class);

    BackstagePermissions newBackstagePermissions = permissionsUpdateEvent.getNewBackstagePermissions();
    List<String> newBackstagePermissionList = newBackstagePermissions.getPermissions();
    Collections.sort(newBackstagePermissionList);
    BackstagePermissions oldBackstagePermissions = permissionsUpdateEvent.getOldBackstagePermissions();
    List<String> oldBackstagePermissionsList = oldBackstagePermissions.getPermissions();
    Collections.sort(oldBackstagePermissionsList);

    AuditEntry auditEntry =
        AuditEntry.builder()
            .action(Action.UPDATE)
            .module(ModuleType.IDP)
            .newYaml(NGYamlUtils.getYamlString(PermissionsDTO.builder()
                                                   .permissions(newBackstagePermissionList)
                                                   .userGroupIdentifiers(newBackstagePermissions.getUserGroups())
                                                   .build(),
                objectMapper))
            .oldYaml(NGYamlUtils.getYamlString(PermissionsDTO.builder()
                                                   .permissions(oldBackstagePermissionsList)
                                                   .userGroupIdentifiers(oldBackstagePermissions.getUserGroups())
                                                   .build(),
                objectMapper))
            .timestamp(outboxEvent.getCreatedAt())
            .resource(ResourceDTO.fromResource(outboxEvent.getResource()))
            .resourceScope(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()))
            .insertId(outboxEvent.getId())
            .build();
    return auditClientService.publishAudit(auditEntry, globalContext);
  }
}