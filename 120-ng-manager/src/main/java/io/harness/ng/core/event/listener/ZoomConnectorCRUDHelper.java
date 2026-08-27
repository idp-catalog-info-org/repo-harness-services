/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.listener;

import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ZoomConnectorDTO;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.models.Secret;
import io.harness.repositories.ng.core.spring.SecretRepository;

import clients.iromanager.remote.connectors.zoom.ZoomRetrofitClient;
import clients.iromanager.remote.connectors.zoom.ZoomUserResponse;
import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class for Zoom connector operations.
 * Handles fetching Zoom user information from the Zoom API.
 */
@Slf4j
public class ZoomConnectorCRUDHelper {
  @Inject private NGEncryptedDataService encryptedDataService;
  @Inject private ZoomRetrofitClient zoomRetrofitClient;
  @Inject private SecretRepository secretRepository;

  /**
   * Fetches Zoom user info for the given connector.
   * All errors and edge-cases are caught and logged.
   *
   * @param connector The Zoom connector data
   * @param connectorInfo Connection context information
   * @return Optional containing Zoom user info, or empty if operation failed
   */
  public Optional<ZoomUserResponse> getZoomUser(ZoomConnectorDTO connector, ConnectorInfoDTO connectorInfo) {
    // Validate access token reference
    if (connector.getAccessTokenRef() == null) {
      log.error("[Zoom]: Missing access token reference for connector: {}", connectorInfo.getIdentifier());
      return Optional.empty();
    }

    // Get and validate secret ID
    String secretId = connector.getAccessTokenRef().getIdentifier();
    if (secretId == null || secretId.isEmpty()) {
      log.error("[Zoom]: Invalid secret reference for connector: {}", connectorInfo.getIdentifier());
      return Optional.empty();
    }

    String secretScope = connector.getAccessTokenRef().getScope().toString();
    if (secretScope == null || secretScope.isEmpty()) {
      log.error("[Zoom]: Invalid scope reference for connector: {}", connectorInfo.getIdentifier());
      return Optional.empty();
    }

    String secretAccountId = connectorInfo.getAccountIdentifier();
    if (secretAccountId == null || secretAccountId.isEmpty()) {
      log.error("[Zoom]: Invalid account reference for connector: {}", connectorInfo.getIdentifier());
      return Optional.empty();
    }

    String secretOrgId = connectorInfo.getOrgIdentifier();
    String secretProjectId = connectorInfo.getProjectIdentifier();

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(secretAccountId)
                              .orgIdentifier(secretOrgId)
                              .projectIdentifier(secretProjectId)
                              .build();

    try {
      Optional<Secret> secret = fetchSecret(scopeInfo, secretId);
      if (secret.isEmpty()) {
        log.error("[Zoom]: Could not fetch secret for connector: {}", connectorInfo.getIdentifier());
        return Optional.empty();
      }

      String token = decryptSecret(secret.get(), scopeInfo, secretId);
      if (token == null || token.isEmpty()) {
        log.error("[Zoom]: Failed to decrypt token for connector: {}", connectorInfo.getIdentifier());
        return Optional.empty();
      }

      return callZoomApi(token, connectorInfo.getIdentifier());
    } catch (Exception e) {
      log.error("[Zoom]: Error processing Zoom connector {}: {}", connectorInfo.getIdentifier(), e.getMessage());
      return Optional.empty();
    }
  }

  private Optional<Secret> fetchSecret(ScopeInfo scopeInfo, String secretId) {
    return secretRepository.findByAccountIdentifierAndParentUniqueIdAndIdentifier(
        scopeInfo.getAccountIdentifier(), scopeInfo.getUniqueId(), secretId);
  }

  private String decryptSecret(Secret secret, ScopeInfo scopeInfo, String secretId) {
    // Add the parent unique ID to the scope info
    ScopeInfo scopeWithParent = ScopeInfo.builder()
                                    .accountIdentifier(scopeInfo.getAccountIdentifier())
                                    .orgIdentifier(scopeInfo.getOrgIdentifier())
                                    .projectIdentifier(scopeInfo.getProjectIdentifier())
                                    .uniqueId(secret.getParentUniqueId())
                                    .build();

    DecryptedSecretValue decryptedValue = encryptedDataService.decryptSecret(scopeWithParent, secretId);
    if (decryptedValue == null || decryptedValue.getDecryptedValue() == null) {
      log.error("[Zoom]: Failed to decrypt secret: {}", secretId);
      return null;
    }

    return decryptedValue.getDecryptedValue();
  }

  private Optional<ZoomUserResponse> callZoomApi(String token, String connectorId) {
    try {
      ZoomUserResponse response = zoomRetrofitClient.getZoomUser(token);
      if (response == null) {
        log.error("[Zoom]: ZoomRetrofitClient returned null for connector {}", connectorId);
        return Optional.empty();
      }
      return Optional.of(response);
    } catch (Exception ex) {
      log.error("[Zoom]: Zoom API call failed (connector={}): {}", connectorId, ex.getMessage());
      return Optional.empty();
    }
  }
}