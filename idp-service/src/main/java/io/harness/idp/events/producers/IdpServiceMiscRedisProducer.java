/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.producers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_V3;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITY;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_ENTITY_V3;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_KIND_PROCESSOR_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.PROJECT_EVENTS_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.PROJECT_EVENT_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CREATE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.START_ACTION;
import static io.harness.security.SecurityContextBuilder.EMAIL;
import static io.harness.security.SecurityContextBuilder.UNIQUE_ID;
import static io.harness.security.SecurityContextBuilder.USERNAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.idp.IdpCatalogCustomPropertiesCaptureEvent;
import io.harness.eventsframework.schemas.idp.IdpCatalogEntitiesSyncCaptureEvent;
import io.harness.eventsframework.schemas.idp.IdpCatalogEntitiesSyncCaptureEventV3;
import io.harness.eventsframework.schemas.idp.IdpIntegrationCatalogProcessorEvent;
import io.harness.eventsframework.schemas.idp.IdpKindProcessorEvent;
import io.harness.eventsframework.schemas.idp.IdpLicenseUsageCaptureEvent;
import io.harness.eventsframework.schemas.idp.IntegrationEntities;
import io.harness.eventsframework.schemas.idp.UserDetails;
import io.harness.eventsframework.schemas.idp.UserPrincipal;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.spec.server.idp.v1.model.BackstageHarnessSyncRequest;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;
import io.harness.spec.server.idp.v1.model.User;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IdpServiceMiscRedisProducer {
  private final Producer idpModuleLicenseUsageCaptureEventProducer;
  private final Producer idpCatalogEntitiesSyncCaptureEventProducer;
  private final Producer idpCatalogEntitiesSyncEventProducer;
  private final Producer idpCatalogCustomPropertyCaptureEventProducer;
  private final Producer idpIntegrationCatalogProcessorEventProducer;
  private final Producer idpKindProcessorEventProducer;
  private final Producer projectEventsStreamProducer;

  @Inject
  public IdpServiceMiscRedisProducer(
      @Named(IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT) Producer idpModuleLicenseUsageCaptureEventProducer,
      @Named(IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT) Producer idpCatalogEntitiesSyncCaptureEventProducer,
      @Named(IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_V3) Producer idpCatalogEntitiesSyncEventProducer,
      @Named(IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT) Producer idpCatalogCustomPropertyCaptureEventProducer,
      @Named(IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT) Producer idpIntegrationCatalogProcessorEventProducer,
      @Named(IDP_KIND_PROCESSOR_EVENT) Producer idpKindProcessorEventProducer,
      @Named(PROJECT_EVENTS_STREAM) Producer projectEventsStreamProducer) {
    this.idpModuleLicenseUsageCaptureEventProducer = idpModuleLicenseUsageCaptureEventProducer;
    this.idpCatalogEntitiesSyncCaptureEventProducer = idpCatalogEntitiesSyncCaptureEventProducer;
    this.idpCatalogEntitiesSyncEventProducer = idpCatalogEntitiesSyncEventProducer;
    this.idpCatalogCustomPropertyCaptureEventProducer = idpCatalogCustomPropertyCaptureEventProducer;
    this.idpIntegrationCatalogProcessorEventProducer = idpIntegrationCatalogProcessorEventProducer;
    this.idpKindProcessorEventProducer = idpKindProcessorEventProducer;
    this.projectEventsStreamProducer = projectEventsStreamProducer;
  }

  public void publishIDPLicenseUsageUserCaptureDTOToRedis(
      String accountIdentifier, String userIdentifier, String email, String userName, long accessedAt) {
    try {
      String eventId = idpModuleLicenseUsageCaptureEventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  Map.of("accountIdentifier", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                      IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT, EventsFrameworkMetadataConstants.ACTION, CREATE_ACTION))
              .setData(
                  getIdpLicenseUsageCaptureEventData(accountIdentifier, userIdentifier, email, userName, accessedAt))
              .build());
      log.info("Produced event {} to redis for IDPLicenseUsageUserCapture accountIdentifier {} userIdentifier {}, "
              + "email {}, userName {}",
          eventId, accountIdentifier, userIdentifier, email, userName);
    } catch (Exception ex) {
      log.error("Failed to produce event to redis for IDPLicenseUsageUserCapture accountIdentifier {} userIdentifier "
              + "{}, email {}, userName {}. Error = {}",
          accountIdentifier, userIdentifier, email, userName, ex.getMessage(), ex);
      throw ex;
    }
  }

  public void publishIDPCatalogEntitiesSyncCaptureToRedis(
      String accountIdentifier, String entityUid, String action, User user, BackstageHarnessSyncRequest.TypeEnum type) {
    try {
      String eventId = idpCatalogEntitiesSyncCaptureEventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  Map.of("accountIdentifier", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                      IDP_CATALOG_ENTITY, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(getIdpCatalogEntitiesSyncCaptureEventData(accountIdentifier, entityUid, action, user, type))
              .build());
      log.info("Produced event {} to redis for backstage entity sync accountIdentifier {} identifier {}, action {}, "
              + "type  {}",
          eventId, accountIdentifier, entityUid, action, type);
    } catch (Exception ex) {
      log.error("Failed to produce event to redis for backstage entity sync accountIdentifier {} identifier {}, "
              + "action {}, type {}. Error = {}",
          accountIdentifier, entityUid, action, type, ex.getMessage(), ex);
      throw ex;
    }
  }

  public void publishIDPCatalogEntitiesToRedisV2(
      String accountIdentifier, String parentUniqueId, String entityRef, String action) {
    try {
      String eventId = idpCatalogEntitiesSyncEventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  Map.of("accountIdentifier", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                      IDP_CATALOG_ENTITY_V3, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(getIdpCatalogEntitiesCaptureEventDataV2(accountIdentifier, parentUniqueId, entityRef, action))
              .build());
      log.info("Produced event {} to redis for backstage entity sync accountIdentifier {} entityUid {}, action {}",
          eventId, accountIdentifier, entityRef, action);
    } catch (Exception ex) {
      log.error("Failed to produce event to redis for backstage entity sync accountIdentifier {} identifier {}, "
              + "action {}. Error = {}",
          accountIdentifier, entityRef, action, ex.getMessage(), ex);
    }
  }

  public void publishIDPCatalogCustomPropertyEventsToRedis(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String entityRef, String yaml, String action, User user) {
    try {
      String eventId = idpCatalogCustomPropertyCaptureEventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  Map.of("accountIdentifier", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                      IDP_CATALOG_CUSTOM_PROPERTY_ENTITY, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(getIdpCatalogCustomPropertyEntityCaptureEventData(
                  accountIdentifier, orgIdentifier, projectIdentifier, entityRef, yaml, action, user))
              .build());
      log.info(
          "Produced event {} to redis for catalog custom property entity accountIdentifier {} entityRef {}, action {}",
          eventId, accountIdentifier, entityRef, action);
    } catch (Exception ex) {
      log.error(
          "Failed to produce event to redis for catalog custom property entity accountIdentifier {} entityRef {}, "
              + "action {}. Error = {}",
          accountIdentifier, entityRef, action, ex.getMessage(), ex);
    }
  }

  public void publishIDPIntegrationCatalogProcessorEventToRedis(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest) {
    try {
      String eventId = idpIntegrationCatalogProcessorEventProducer.send(
          Message.newBuilder()
              .putAllMetadata(Map.of("accountIdentifier", accountIdentifier, "integrationId", integrationId,
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT,
                  EventsFrameworkMetadataConstants.ACTION, START_ACTION))
              .setData(getIDPIntegrationCatalogProcessorEventData(
                  accountIdentifier, orgIdentifier, projectIdentifier, integrationId, saveDiscoverEntitiesRequest))
              .build());
      log.info("Produced event {} to redis for IDPIntegrationCatalogProcessor accountIdentifier {} integrationId {}",
          eventId, accountIdentifier, integrationId);
    } catch (Exception ex) {
      log.error(
          "Failed to produce event to redis for IDPIntegrationCatalogProcessor accountIdentifier {} integrationId "
              + "{}. Error = {}",
          accountIdentifier, integrationId, ex.getMessage(), ex);
      throw ex;
    }
  }

  public void publishIDPKindProcessorEventToRedis(String accountIdentifier, String kindIdentifier, String action) {
    try {
      String eventId = idpKindProcessorEventProducer.send(
          Message.newBuilder()
              .putAllMetadata(Map.of("accountIdentifier", accountIdentifier, "kindIdentifier", kindIdentifier,
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, IDP_KIND_PROCESSOR_EVENT,
                  EventsFrameworkMetadataConstants.ACTION, action))
              .setData(getIdpKindProcessorEventData(accountIdentifier, kindIdentifier, action))
              .build());
      log.info("Produced event {} to redis for IDPKindProcessor accountIdentifier {} kindIdentifier {}", eventId,
          accountIdentifier, kindIdentifier);
    } catch (Exception ex) {
      log.error("Failed to produce event to redis for IDPKindProcessor accountIdentifier {} kindIdentifier "
              + "{}. Error = {}",
          accountIdentifier, kindIdentifier, ex.getMessage(), ex);
      throw ex;
    }
  }

  private ByteString getIdpLicenseUsageCaptureEventData(
      String accountIdentifier, String userIdentifier, String email, String userName, long accessedAt) {
    return IdpLicenseUsageCaptureEvent.newBuilder()
        .setAccountIdentifier(accountIdentifier)
        .setUserIdentifier(userIdentifier)
        .setEmail(email)
        .setUserName(userName)
        .setAccessedAt(accessedAt)
        .build()
        .toByteString();
  }

  private ByteString getIdpCatalogEntitiesSyncCaptureEventData(String accountIdentifier, String identifier,
      String action, User user, BackstageHarnessSyncRequest.TypeEnum type) {
    IdpCatalogEntitiesSyncCaptureEvent.Builder payloadBuilder = IdpCatalogEntitiesSyncCaptureEvent.newBuilder()
                                                                    .setAccountIdentifier(accountIdentifier)
                                                                    .setIdentifier(identifier)
                                                                    .setAction(action)
                                                                    .setSyncMode("sync")
                                                                    .setType(type.value());

    if (user != null) {
      payloadBuilder.setUserName(user.getName());
      payloadBuilder.setUserUuid(user.getUuid());
      payloadBuilder.setUserEmail(user.getEmail());
    }

    return payloadBuilder.build().toByteString();
  }

  private ByteString getIdpCatalogEntitiesCaptureEventDataV2(
      String accountIdentifier, String parentUniqueId, String entityRef, String action) {
    IdpCatalogEntitiesSyncCaptureEventV3.Builder payloadBuilder = IdpCatalogEntitiesSyncCaptureEventV3.newBuilder()
                                                                      .setAccountIdentifier(accountIdentifier)
                                                                      .setParentUniqueId(parentUniqueId)
                                                                      .setEntityRef(entityRef)
                                                                      .setAction(action);
    return payloadBuilder.build().toByteString();
  }

  private ByteString getIdpCatalogCustomPropertyEntityCaptureEventData(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String entityRef, String yaml, String action, User user) {
    UserDetails.Builder userDetailsBuilder =
        UserDetails.newBuilder().setUuid(user.getUuid()).setEmail(user.getEmail()).setName(user.getName());
    IdpCatalogCustomPropertiesCaptureEvent.Builder payloadBuilder = IdpCatalogCustomPropertiesCaptureEvent.newBuilder()
                                                                        .setAccountIdentifier(accountIdentifier)
                                                                        .setYaml(yaml)
                                                                        .setAction(action)
                                                                        .setUser(userDetailsBuilder.build());
    if (!isEmpty(entityRef)) {
      payloadBuilder.setEntityRef(entityRef);
    }
    if (!isEmpty(orgIdentifier)) {
      payloadBuilder.setOrgIdentifier(orgIdentifier);
    }
    if (!isEmpty(projectIdentifier)) {
      payloadBuilder.setProjectIdentifier(projectIdentifier);
    }
    return payloadBuilder.build().toByteString();
  }

  private ByteString getIDPIntegrationCatalogProcessorEventData(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String integrationId, SaveDiscoverEntitiesRequest saveDiscoverEntitiesRequest) {
    List<IntegrationEntities> integrationEntitiesList = new ArrayList<>();
    saveDiscoverEntitiesRequest.getIntegrationEntities().forEach(saveDiscoverEntitiesRequestIntegrationEntities -> {
      IntegrationEntities.Builder entityBuilder =
          IntegrationEntities.newBuilder()
              .setIntegrationEntityId(saveDiscoverEntitiesRequestIntegrationEntities.getIntegrationEntityId())
              .setAction(saveDiscoverEntitiesRequestIntegrationEntities.getAction().value());
      if (saveDiscoverEntitiesRequestIntegrationEntities.getActionDestination() != null) {
        entityBuilder.setActionDestination(saveDiscoverEntitiesRequestIntegrationEntities.getActionDestination());
      }
      if (saveDiscoverEntitiesRequestIntegrationEntities.getType() != null) {
        entityBuilder.setType(saveDiscoverEntitiesRequestIntegrationEntities.getType());
      }
      if (saveDiscoverEntitiesRequestIntegrationEntities.getActionIdentifier() != null) {
        entityBuilder.setActionIdentifier(saveDiscoverEntitiesRequestIntegrationEntities.getActionIdentifier());
      }
      IntegrationEntities integrationEntity = entityBuilder.build();
      integrationEntitiesList.add(integrationEntity);
    });
    IdpIntegrationCatalogProcessorEvent.Builder eventBuilder =
        IdpIntegrationCatalogProcessorEvent.newBuilder()
            .setAccountIdentifier(accountIdentifier)
            .setIntegrationId(integrationId)
            .addAllIntegrationEntities(integrationEntitiesList)
            .setAutoDiscover(Boolean.TRUE.equals(saveDiscoverEntitiesRequest.isAutoDiscover()))
            .setSelectionFilter(saveDiscoverEntitiesRequest.getSelectionFilter() != null
                    ? saveDiscoverEntitiesRequest.getSelectionFilter().name()
                    : SaveDiscoverEntitiesRequest.SelectionFilterEnum.MANUAL.name());
    if (!isEmpty(orgIdentifier)) {
      eventBuilder.setIntegrationOrgIdentifier(orgIdentifier);
    }
    if (!isEmpty(projectIdentifier)) {
      eventBuilder.setIntegrationProjectIdentifier(projectIdentifier);
    }
    UserPrincipal userPrincipal =
        UserPrincipal.newBuilder()
            .setUuid(SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(UNIQUE_ID))
            .setName(SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(USERNAME))
            .setEmail(SourcePrincipalContextBuilder.getSourcePrincipal().getJWTClaims().get(EMAIL))
            .build();
    eventBuilder.setUserPrincipal(userPrincipal);
    return eventBuilder.build().toByteString();
  }

  private ByteString getIdpKindProcessorEventData(String accountIdentifier, String kindIdentifier, String action) {
    IdpKindProcessorEvent.Builder payloadBuilder = IdpKindProcessorEvent.newBuilder()
                                                       .setAccountIdentifier(accountIdentifier)
                                                       .setKindIdentifier(kindIdentifier)
                                                       .setAction(action);
    return payloadBuilder.build().toByteString();
  }

  public void publishProjectEventToRedis(ProjectEntityChangeDTO projectEntityChangeDTO, String action) {
    try {
      String eventId = projectEventsStreamProducer.send(
          Message.newBuilder()
              .putAllMetadata(Map.of("accountIdentifier", projectEntityChangeDTO.getAccountIdentifier(),
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, PROJECT_EVENT_ENTITY,
                  EventsFrameworkMetadataConstants.ACTION, action))
              .setData(projectEntityChangeDTO.toByteString())
              .build());
      log.info("Produced project event {} to redis for account={}, project={}, action={}", eventId,
          projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getIdentifier(), action);
    } catch (Exception ex) {
      log.error("Failed to produce project event to redis for account={}, project={}, action={}. Error: {}",
          projectEntityChangeDTO.getAccountIdentifier(), projectEntityChangeDTO.getIdentifier(), action,
          ex.getMessage(), ex);
      throw ex;
    }
  }
}
