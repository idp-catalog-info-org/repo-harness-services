/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import static io.harness.annotations.dev.HarnessTeam.IRO;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.CONNECTOR_ENTITY;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.EntityReference;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorServiceImpl;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.zoomconnector.ZoomConnector.ZoomConnectorKeys;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ZoomConnectorDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.beans.connector.zoomconnector.ZoomApiAccessType;
import io.harness.encryption.Scope;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.activityhistory.entity.NGActivity;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.event.MessageListener;
import io.harness.repositories.ConnectorRepository;
import io.harness.repositories.activityhistory.NGActivityCustomRepository;
import io.harness.scope.ScopeHelper;

import clients.iromanager.remote.connectors.zoom.ZoomRetrofitClient;
import clients.iromanager.remote.connectors.zoom.ZoomRevokeTokenResponse;
import clients.iromanager.remote.connectors.zoom.ZoomUserResponse;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

/**
 * This Class responsible for processing Zoom connector change events in the system.
 * This class handles CRUD operations for Zoom connectors and manages revocation
 * of OAuth tokens when connectors are deleted.
 */
@OwnedBy(IRO)
@Slf4j
@Singleton
public class ZoomConnectorCRUDStreamListener implements MessageListener {
  private static final String ACTION = "action";
  private static final String CONNECTOR_TYPE = "connectorType";
  private static final String CONNECTORS = "Connectors";
  private static final String SECRET_TYPE = "Secrets";
  private static final String ZOOM = "Zoom";
  private static final String DELETE = "delete";
  private static final String CREATE = "create";
  private static final String UPDATE = "update";
  private static final String SUCCESS = "success";
  private static final String OAUTH_TOKEN_PREFIX_IDENTIFIER =
      "harnessoauthaccesstoken"; // Referred from: OauthSecretService.java for the prefix
  private static final String REFERRED_BY_ENTITY_TYPE = "referredByEntityType";
  private static final String REFERRED_BY_ENTITY_FQN = "referredByEntityFQN";
  private static final String LAST_MODIFIED_AT = "lastModifiedAt";

  @Inject NGActivityCustomRepository ngActivityCustomRepository;
  @Inject NGEncryptedDataService encryptedDataService;
  @Inject ZoomRetrofitClient zoomRetrofitClient;
  @Inject NextGenConfiguration configuration;
  @Inject ConnectorServiceImpl connectorService;
  @Inject ZoomConnectorCRUDHelper zoomConnectorCRUDHelper;
  @Inject ConnectorRepository connectorRepository;

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && CONNECTOR_ENTITY.equals(metadataMap.get(ENTITY_TYPE))) {
        return processConnectorChangeEvent(message);
      }
    }
    return true;
  }

  /**
   * Processes a connector change event message and performs appropriate actions
   * based on the event type.
   *
   * @param message The message containing connector change event data
   * @return true if the event was processed successfully, false otherwise
   */
  public boolean processConnectorChangeEvent(Message message) {
    if (message == null) {
      log.error("[Zoom]: Received null message or message content");
      return true;
    }

    String action = message.getMessage().getMetadataMap().get(ACTION);
    String connectorType = message.getMessage().getMetadataMap().get(CONNECTOR_TYPE);
    log.debug("[Zoom]: Processing zoom connector change event with action: {}", action);

    try {
      if (DELETE.equalsIgnoreCase(action) && ZOOM.equalsIgnoreCase(connectorType)) {
        try {
          processDeleteConnectorEvent(message);
        } catch (Exception e) {
          log.error("[Zoom]: Error in delete handler", e);
        }
      } else if ((CREATE.equalsIgnoreCase(action) || UPDATE.equalsIgnoreCase(action))
          && ZOOM.equalsIgnoreCase(connectorType)) {
        try {
          processCreateConnectorEvent(message);
        } catch (Exception e) {
          log.error("[Zoom]: Error in create/update handler", e);
        }
      }
    } catch (Exception e) {
      log.error("[Zoom]: Unexpected error processing connector change event", e);
    }

    // Always return true, regardless of handler success otherwise it loop again
    return true;
  }

  private void processDeleteConnectorEvent(Message message) {
    EntityChangeDTO entityChangeDTO;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("[Zoom]: Failed to parse EntityChangeDTO for message ID: {}", message.getId(), e);
      return;
    }

    String referredByEntityFQN = buildEntityFQN(entityChangeDTO);
    log.info("[Zoom]: Processing deletion for Zoom connector with FQN: {}", referredByEntityFQN);

    Optional<DecryptedSecretValue> oauthToken = findAssociatedOAuthToken(referredByEntityFQN);
    if (oauthToken.isEmpty()) {
      log.warn("[Zoom]: No OAuth token found for connector: {}", referredByEntityFQN);
      return;
    }

    revokeZoomOAuthToken(oauthToken.get(), referredByEntityFQN);
  }
  /**
   * Builds a fully qualified name (FQN) for the entity based on its identifiers.
   *
   * @param entityChangeDTO The entity change data
   * @return The fully qualified name of the entity
   */
  private String buildEntityFQN(EntityChangeDTO entityChangeDTO) {
    Scope scope = ScopeHelper.getScope(entityChangeDTO.getAccountIdentifier().toString(),
        entityChangeDTO.getOrgIdentifier().toString(), entityChangeDTO.getProjectIdentifier().toString());

    String baseFQN = entityChangeDTO.getAccountIdentifier().getValue();

    switch (scope) {
      case ORG:
        baseFQN = String.format("%s/%s", baseFQN, entityChangeDTO.getOrgIdentifier().getValue());
        break;
      case PROJECT:
        baseFQN = String.format("%s/%s/%s", baseFQN, entityChangeDTO.getOrgIdentifier().getValue(),
            entityChangeDTO.getProjectIdentifier().getValue());
        break;
      default:
        // No additional scope, retain account level
        break;
    }

    return String.format("%s/%s", baseFQN, entityChangeDTO.getIdentifier().getValue());
  }

  /**
   * Finds the OAuth token associated with the connector.
   *
   * @param connectorFQN The fully qualified name of the connector
   * @return An Optional containing the decrypted secret value if found
   */
  private Optional<DecryptedSecretValue> findAssociatedOAuthToken(String connectorFQN) {
    Criteria criteria =
        Criteria.where(REFERRED_BY_ENTITY_TYPE).is(CONNECTORS).and(REFERRED_BY_ENTITY_FQN).is(connectorFQN);

    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Order.desc(LAST_MODIFIED_AT)));
    Page<NGActivity> page = ngActivityCustomRepository.findAll(criteria, pageable);
    List<NGActivity> results = page.getContent();

    for (NGActivity activity : results) {
      if (activity.getReferredEntity() == null || activity.getReferredEntity().getEntityRef() == null) {
        continue;
      }

      String type = String.valueOf(activity.getReferredEntity().getType());
      String identifier = activity.getReferredEntity().getEntityRef().getIdentifier();

      if (SECRET_TYPE.equalsIgnoreCase(type) && StringUtils.isNotBlank(identifier)
          && identifier.contains(OAUTH_TOKEN_PREFIX_IDENTIFIER)) {
        try {
          ScopeInfo scopeInfo = buildScopeInfo(activity);
          if (scopeInfo == null) {
            return Optional.empty();
          }

          return Optional.of(encryptedDataService.decryptSecret(scopeInfo, identifier));
        } catch (Exception e) {
          log.error("[Zoom]: Failed to decrypt secret: {} for connector: {}", identifier, connectorFQN, e);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Builds ScopeInfo object from NGActivity.
   *
   * @param activity The NGActivity containing scope information
   * @return A ScopeInfo object built from the activity
   */
  private ScopeInfo buildScopeInfo(NGActivity activity) {
    EntityReference entityRef = activity.getReferredEntity().getEntityRef();
    String accountId = entityRef.getAccountIdentifier();
    String orgId = entityRef.getOrgIdentifier();
    String projectId = entityRef.getProjectIdentifier();
    ScopeLevel scopeLevel;

    if (isNotEmpty(accountId) && isNotEmpty(orgId) && isNotEmpty(projectId)) {
      scopeLevel = ScopeLevel.PROJECT;
    } else if (isNotEmpty(accountId) && isNotEmpty(orgId)) {
      scopeLevel = ScopeLevel.ORGANIZATION;
    } else if (isNotEmpty(accountId)) {
      scopeLevel = ScopeLevel.ACCOUNT;
    } else {
      return null;
    }

    return ScopeInfo.builder()
        .scopeType(scopeLevel)
        .accountIdentifier(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .uniqueId(entityRef.getParentUniqueId())
        .build();
  }

  private boolean isNotEmpty(String value) {
    return value != null && !value.isEmpty();
  }

  /**
   * Revokes a Zoom OAuth token.
   *
   * @param token        The decrypted token to revoke
   * @param connectorFQN The connector's fully qualified name for logging
   */
  private void revokeZoomOAuthToken(DecryptedSecretValue token, String connectorFQN) {
    if (token == null || StringUtils.isBlank(token.getDecryptedValue())) {
      log.warn("[Zoom]: Empty or null token for connector: {}", connectorFQN);
      return;
    }

    String clientId = configuration.getZoomConfig().getClientId();
    String clientSecret = configuration.getZoomConfig().getClientSecret();

    if (StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
      log.error("[Zoom]: Zoom client credentials are not configured properly");
      return;
    }

    String basicAuth;
    try {
      basicAuth = zoomRetrofitClient.generateBasicAuthHeader(clientId, clientSecret.toCharArray());
      if (StringUtils.isBlank(basicAuth)) {
        log.error("[Zoom]: Failed to generate Basic Auth header for Zoom API");
        return;
      }
    } catch (Exception e) {
      log.error("[Zoom]: Error generating Basic Auth header", e);
      return;
    }

    try {
      ZoomRevokeTokenResponse response = zoomRetrofitClient.performRevokeToken(basicAuth, token.getDecryptedValue());
      if (response != null && SUCCESS.equalsIgnoreCase(response.getStatus())) {
        log.info("[Zoom]: Successfully revoked token for connector: {}", connectorFQN);
      } else {
        log.error("[Zoom]: Token revocation failed for connector: {}. Response: {}", connectorFQN,
            response != null ? response.getStatus() : "null");
      }
    } catch (Exception e) {
      log.error("[Zoom]: Exception while revoking Zoom token for connector: {}", connectorFQN, e);
    }
  }

  private void processCreateConnectorEvent(Message message) {
    // Parse the entity change data from the message
    EntityChangeDTO entityChangeDTO;
    try {
      entityChangeDTO = EntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      log.error("[Zoom]: Failed to parse EntityChangeDTO for message ID: {}", message.getId(), e);
      return;
    }

    // Build scope information from the entity change data
    ScopeInfo scope = ScopeInfo.builder()
                          .accountIdentifier(entityChangeDTO.getAccountIdentifier().getValue())
                          .orgIdentifier(entityChangeDTO.getOrgIdentifier().getValue())
                          .projectIdentifier(entityChangeDTO.getProjectIdentifier().getValue())
                          .uniqueId(entityChangeDTO.getScopeInfo().getUniqueId().getValue())
                          .build();

    String connectorId = entityChangeDTO.getIdentifier().getValue();

    // Get connector details - return early if not found
    ConnectorResponseDTO connectorResponse = connectorService.get(scope, connectorId).orElse(null);

    if (connectorResponse == null) {
      log.warn("[Zoom]: Connector not found with ID: {} and scope: {}", connectorId, scope);
      return;
    }

    // Extract and validate connector configuration
    ConnectorInfoDTO connectorInfo = connectorResponse.getConnector();
    ConnectorConfigDTO rawConfig = connectorInfo.getConnectorConfig();

    if (!(rawConfig instanceof ZoomConnectorDTO zoomConnector)) {
      log.error("[Zoom]: Expected ZoomConnector but got {}", rawConfig.getClass().getSimpleName());
      return;
    }

    if (zoomConnector.getApiAccessType() != ZoomApiAccessType.OAUTH) {
      log.error("[Zoom]: Zoom API access is not OAUTH Access Type: {}", zoomConnector.getApiAccessType());
      return;
    }

    if (connectorInfo.getConnectorType() != ConnectorType.ZOOM) {
      log.error("[Zoom]: Expected connectorType ZOOM but got {}", connectorInfo.getConnectorType());
      return;
    }

    // Process Zoom specific data
    ZoomConnectorDTO updatedZoomConnectorDTO = enrichZoomConnectorWithUserData(zoomConnector, connectorInfo);

    if (updatedZoomConnectorDTO.getZoomAccountId() == null || updatedZoomConnectorDTO.getZoomUserId() == null) {
      log.error("[Zoom]: Zoom account ID and User ID are not set");
      return;
    }

    Criteria criteria = Criteria.where(ConnectorKeys.identifier)
                            .is(connectorInfo.getIdentifier())
                            .and(ConnectorKeys.accountIdentifier)
                            .is(scope.getAccountIdentifier())
                            .and(ConnectorKeys.parentUniqueId)
                            .is(scope.getUniqueId())
                            .and(ConnectorKeys.type)
                            .is(ConnectorType.ZOOM);
    Update update = new Update()
                        .set(ZoomConnectorKeys.zoomAccountId, updatedZoomConnectorDTO.getZoomAccountId())
                        .set(ZoomConnectorKeys.zoomUserId, updatedZoomConnectorDTO.getZoomUserId());

    Connector connector = connectorRepository.update(criteria, update);

    if (connector != null) {
      log.info("[Zoom]: Successfully updated Zoom connector with ID: {} and scope: {}", connectorId, scope);
      return;
    }

    log.warn("[Zoom]: Failed to update Zoom connector with ID: {} and scope: {}", connectorId, scope);
  }

  /**
   * Enriches the Zoom connector with user account data
   */
  private ZoomConnectorDTO enrichZoomConnectorWithUserData(
      ZoomConnectorDTO zoomConnector, ConnectorInfoDTO connectorInfo) {
    Optional<ZoomUserResponse> userResponseOpt = zoomConnectorCRUDHelper.getZoomUser(zoomConnector, connectorInfo);

    if (userResponseOpt.isPresent()) {
      ZoomUserResponse userData = userResponseOpt.get();
      zoomConnector.setZoomAccountId(userData.getAccount_id());
      zoomConnector.setZoomUserId(userData.getId());
    } else {
      zoomConnector.setZoomAccountId(null);
      zoomConnector.setZoomUserId(null);
    }

    return zoomConnector;
  }
}
