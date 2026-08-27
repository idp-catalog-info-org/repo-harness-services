/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers;

import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY;
import static io.harness.eventsframework.EventsFrameworkConstants.IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.context.GlobalContextData;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.idp.IdpCatalogCustomPropertiesCaptureEvent;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.manage.GlobalContextManager;
import io.harness.queue.QueueController;
import io.harness.remote.client.NGRestUtils;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.PrincipalContextData;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.User;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public class IdpCatalogCustomPropertyCaptureEventConsumer extends AbstractIdpServiceRedisStreamConsumer {
  private static final String CONSUMER_NAME = "IdpCatalogEntitiesSyncCaptureEventConsumerV2";
  @Inject CatalogService catalogService;
  @Inject CatalogServiceHelper catalogServiceHelper;
  @Inject ScopeInfoClient scopeInfoClient;

  @Inject
  public IdpCatalogCustomPropertyCaptureEventConsumer(
      @Named(IDP_CATALOG_CUSTOM_PROPERTY_ENTITY_CAPTURE_EVENT) Consumer redisConsumer, QueueController queueController,
      ResourceLocker resourceLocker) {
    super(redisConsumer, queueController, resourceLocker);
  }

  @Override
  protected boolean processMessage(Message message) {
    log.info("Processing message with id: {} in {} consumer", message.getId(), CONSUMER_NAME);
    if (message != null && message.hasMessage()) {
      boolean entityTypeAndActionValidation;
      try {
        Map<String, String> metadata = message.getMessage().getMetadataMap();
        String entityType = metadata.get(ENTITY_TYPE);
        entityTypeAndActionValidation = entityTypeAndActionValidation(
            CONSUMER_NAME, message, IDP_CATALOG_CUSTOM_PROPERTY_ENTITY, List.of(UPDATE_ACTION));
        if (entityTypeAndActionValidation) {
          ByteString data = message.getMessage().getData();
          IdpCatalogCustomPropertiesCaptureEvent idpCatalogCustomPropertiesCaptureEvent =
              IdpCatalogCustomPropertiesCaptureEvent.parseFrom(data);
          return lockAndProcessData(CONSUMER_NAME + "_EVENT_"
                  + idpCatalogCustomPropertiesCaptureEvent.getAccountIdentifier() + "_"
                  + idpCatalogCustomPropertiesCaptureEvent.getEntityRef(),
              entityType, data);
        }
      } catch (Exception ex) {
        log.error("Error in processing message with id: {} in {} consumer. Error = {}", message.getId(), CONSUMER_NAME,
            ex.getMessage(), ex);
        return false;
      }
      log.info("Processed messageId = {} in {} consumer", message.getId(), CONSUMER_NAME);
    }
    return true;
  }

  @Override
  protected void processInternal(String entityType, ByteString data) throws Exception {
    IdpCatalogCustomPropertiesCaptureEvent idpCatalogCustomPropertiesCaptureEvent =
        IdpCatalogCustomPropertiesCaptureEvent.parseFrom(data);
    handleEvents(idpCatalogCustomPropertiesCaptureEvent);
  }

  private void handleEvents(IdpCatalogCustomPropertiesCaptureEvent idpCatalogCustomPropertiesCaptureEvent) {
    String accountIdentifier = idpCatalogCustomPropertiesCaptureEvent.getAccountIdentifier();
    String orgIdentifier = idpCatalogCustomPropertiesCaptureEvent.getOrgIdentifier();
    String projectIdentifier = idpCatalogCustomPropertiesCaptureEvent.getProjectIdentifier();
    String entityRef = idpCatalogCustomPropertiesCaptureEvent.getEntityRef();
    User user = new User();
    user.setName(idpCatalogCustomPropertiesCaptureEvent.getUser().getName());
    user.setEmail(idpCatalogCustomPropertiesCaptureEvent.getUser().getEmail());
    user.setUuid(idpCatalogCustomPropertiesCaptureEvent.getUser().getUuid());
    GlobalContextData currentPrincipalContext = GlobalContextManager.get(PrincipalContextData.PRINCIPAL_CONTEXT);
    setUserContext(accountIdentifier, user);
    try {
      EntityUpdateRequest entityRequest = new EntityUpdateRequest();
      entityRequest.setYaml(idpCatalogCustomPropertiesCaptureEvent.getYaml());
      Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(entityRef);
      ScopeInfo scopeInfo =
          NGRestUtils.getResponse(scopeInfoClient.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier));
      CatalogEntity catalogEntity = catalogServiceHelper.catalogEntity(
          scopeInfo.getUniqueId(), kindScopeIdentifier.getLeft(), kindScopeIdentifier.getRight());
      if (catalogEntity instanceof InlineCatalogEntity) {
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().build());
      } else if (catalogEntity instanceof GitReferencedCatalogEntity gitReferencedCatalogEntity) {
        GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                     .storeType(StoreType.REMOTE)
                                                     .connectorRef(gitReferencedCatalogEntity.getConnectorRef())
                                                     .repoName(gitReferencedCatalogEntity.getRepo())
                                                     .filePath(gitReferencedCatalogEntity.getFilePath())
                                                     .build());
      }
      catalogService.updateEntity(
          accountIdentifier, orgIdentifier, projectIdentifier, entityRef, entityRequest, false, false, false, true);
    } finally {
      GlobalContextManager.upsertGlobalContextRecord(currentPrincipalContext);
    }
  }

  private void setUserContext(String accountIdentifier, User user) {
    if (user != null && StringUtils.isNotBlank(user.getUuid())) {
      GlobalContextManager.upsertGlobalContextRecord(
          PrincipalContextData.builder()
              .principal(new UserPrincipal(user.getUuid(), user.getEmail(), user.getName(), accountIdentifier))
              .build());
    }
  }
}
