/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.DefaultConnectorServiceImpl;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.zoomconnector.ZoomConnector;
import io.harness.connector.entities.embedded.zoomconnector.ZoomConnector.ZoomConnectorKeys;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ZoomConnectorDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.ng.core.api.NGSecretServiceV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.models.Secret;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.oauth.OAuthTokenRefresherHelper;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.ConnectorRepository;
import io.harness.secret.services.impl.NGSecretServiceV2Impl;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;

import clients.iromanager.remote.connectors.zoom.ZoomDeAuthPayload;
import clients.iromanager.remote.connectors.zoom.ZoomOAuthResponse;
import clients.iromanager.remote.connectors.zoom.ZoomRefreshOAuthToken;
import clients.iromanager.remote.connectors.zoom.ZoomRetrofitClient;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
public class ZoomServiceImpl implements ZoomService {
  private final ConnectorService connectorService;
  private final DecryptionHelper decryptionHelper;
  private final SecretNGManagerClient secretNGManagerClient;

  private final ZoomRetrofitClient zoomRetrofitClient;

  @Inject ConnectorRepository connectorRepository;
  @Inject DefaultConnectorServiceImpl defaultConnectorService;
  @Inject NGSecretServiceV2 secretService;

  @Inject NGSecretServiceV2Impl ngSecretServiceV2;

  @Inject OAuthTokenRefresherHelper oAuthTokenRefresherHelper;

  @Inject NextGenConfiguration configuration;
  @Inject ScopeInfoService scopeInfoService;

  private static final String ZOOM_USER_ID = "zoomUserId";
  private static final String ZOOM_ACCOUNT_ID = "zoomAccountId";
  private static final String API_ACCESS_TYPE = "apiAccessType";
  private static final String OAUTH = "OAUTH";

  @Inject
  public ZoomServiceImpl(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      DecryptionHelper decryptionHelper, SecretNGManagerClient secretNGManagerClient,
      ZoomRetrofitClient zoomRetrofitClient) {
    this.connectorService = connectorService;
    this.decryptionHelper = decryptionHelper;
    this.secretNGManagerClient = secretNGManagerClient;
    this.zoomRetrofitClient = zoomRetrofitClient;
  }

  @Override
  public ZoomOAuthResponse generateZoomAccessToken(IdentifierRef identifierRef, String connectorIdentifier) {
    String connectorNotFountErrorMsg = String.format("Connector with identifier [%s] not found.", connectorIdentifier,
        identifierRef.getProjectIdentifier(), identifierRef.getOrgIdentifier());

    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier());
    ConnectorResponseDTO connectorResponseDTO =
        connectorService.get(scopeInfo, connectorIdentifier)
            .orElseThrow(() -> new NotFoundException(connectorNotFountErrorMsg));

    if (connectorResponseDTO.getConnector().getConnectorType() != ConnectorType.ZOOM) {
      throw new InvalidRequestException(String.format("Invalid connector type [%s]. Expected connector type: [ZOOM].",
          connectorResponseDTO.getConnector().getConnectorType()));
    }

    ZoomConnectorDTO zoomConnectorDTO = (ZoomConnectorDTO) connectorResponseDTO.getConnector().getConnectorConfig();
    if (zoomConnectorDTO == null) {
      throw new InvalidRequestException("Invalid Zoom Connector Configuration");
    }

    if (zoomConnectorDTO.getAccessTokenRef() == null || zoomConnectorDTO.getRefreshTokenRef() == null) {
      log.error("OAuth tokens not found for connector: {} in account: {}. accessTokenRef: {}, refreshTokenRef: {}",
          connectorIdentifier, identifierRef.getAccountIdentifier(), zoomConnectorDTO.getAccessTokenRef() != null,
          zoomConnectorDTO.getRefreshTokenRef() != null);
      throw new InvalidRequestException(String.format(
          "OAuth tokens not configured for connector: %s (account: %s). Please configure OAuth tokens first.",
          connectorIdentifier, identifierRef.getAccountIdentifier()));
    }

    if (isTokenExpiredOrExpiringSoon(zoomConnectorDTO.getTokenExpirationTime())) {
      log.info("Performing lazy refresh for Zoom OAuth tokens for connector: {} (token expired or expiring soon)",
          connectorIdentifier);
      return performLazyTokenRefresh(identifierRef, connectorResponseDTO);
    } else {
      log.info("Token is still valid for connector: {}, returning existing tokens. Expiration time: {}",
          connectorIdentifier, zoomConnectorDTO.getTokenExpirationTime());

      try {
        return getExistingTokens(identifierRef, connectorResponseDTO);
      } catch (Exception e) {
        log.warn("Failed to retrieve existing tokens for connector: {}, falling back to refresh. Error: {}",
            connectorIdentifier, e.getMessage());
        return performLazyTokenRefresh(identifierRef, connectorResponseDTO);
      }
    }
  }

  private List<EncryptedDataDetail> getEncryptedDataDetails(
      NGAccess basicNgAccessObject, DecryptableEntity decryptableEntity) {
    return NGRestUtils.getResponse(
        secretNGManagerClient.getEncryptionDetails(basicNgAccessObject.getAccountIdentifier(),
            NGAccessWithEncryptionConsumer.builder()
                .ngAccess(basicNgAccessObject)
                .decryptableEntity(decryptableEntity)
                .build()));
  }

  private ZoomOAuthResponse performLazyTokenRefresh(
      IdentifierRef identifierRef, ConnectorResponseDTO connectorResponseDTO) {
    try {
      if (connectorResponseDTO.getConnector() == null) {
        log.error("Connector data is null for connector: {} in account: {}, org: {}, project: {}",
            identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
            identifierRef.getProjectIdentifier());
        throw new InvalidRequestException(String.format("Connector data is null for connector: %s, account: %s",
            identifierRef.getIdentifier(), identifierRef.getAccountIdentifier()));
      }

      ZoomConnectorDTO zoomConnectorDTO = (ZoomConnectorDTO) connectorResponseDTO.getConnector().getConnectorConfig();

      SecretRefData accessTokenRef = zoomConnectorDTO.getAccessTokenRef();
      SecretRefData refreshTokenRef = zoomConnectorDTO.getRefreshTokenRef();

      if (accessTokenRef == null || refreshTokenRef == null) {
        log.error(
            "Missing OAuth token references for connector: {} in account: {}. accessTokenRef: {}, refreshTokenRef: {}",
            identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(), accessTokenRef != null,
            refreshTokenRef != null);
        throw new InvalidRequestException(String.format("Missing OAuth token references for connector: %s, account: %s",
            identifierRef.getIdentifier(), identifierRef.getAccountIdentifier()));
      }

      SecretDTOV2 accessTokenDTO = getSecretDTOV2(identifierRef.getAccountIdentifier(),
          identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), accessTokenRef);
      SecretDTOV2 refreshTokenDTO = getSecretDTOV2(identifierRef.getAccountIdentifier(),
          identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), refreshTokenRef);

      if (accessTokenDTO == null || refreshTokenDTO == null) {
        log.error("Failed to retrieve token secrets for connector: {} in account: {}. accessTokenDTO: {}, "
                + "refreshTokenDTO: {}",
            identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(), accessTokenDTO != null,
            refreshTokenDTO != null);
        throw new InvalidRequestException(
            String.format("Failed to retrieve access or refresh token secrets for connector: %s, account: %s",
                identifierRef.getIdentifier(), identifierRef.getAccountIdentifier()));
      }

      if (configuration.getZoomConfig() == null) {
        log.error("Zoom configuration is null for connector: {} in account: {}", identifierRef.getIdentifier(),
            identifierRef.getAccountIdentifier());
        throw new InvalidRequestException(
            String.format("Zoom configuration is not available for connector: %s, account: %s",
                identifierRef.getIdentifier(), identifierRef.getAccountIdentifier()));
      }

      String clientId = configuration.getZoomConfig().getClientId();
      String clientSecret = configuration.getZoomConfig().getClientSecret();

      if (clientId == null || clientSecret == null) {
        log.error("Zoom app credentials (clientId or clientSecret) are null for connector: {} in account: {}",
            identifierRef.getIdentifier(), identifierRef.getAccountIdentifier());
        throw new InvalidRequestException(
            String.format("Zoom app credentials not configured in the system for connector: %s, account: %s",
                identifierRef.getIdentifier(), identifierRef.getAccountIdentifier()));
      }

      ZoomConnectorDTO decryptedZoomConnector = decryptZoomConnector(identifierRef, connectorResponseDTO);

      if (decryptedZoomConnector.getRefreshTokenRef() == null) {
        log.error("Refresh token reference is null after decryption for connector: {} in account: {}",
            identifierRef.getIdentifier(), identifierRef.getAccountIdentifier());
        throw new InvalidRequestException(
            String.format("Refresh token reference is null after decryption for connector: %s, account: %s",
                identifierRef.getIdentifier(), identifierRef.getAccountIdentifier()));
      }

      char[] decryptedRefreshToken = decryptedZoomConnector.getRefreshTokenRef().getDecryptedValue();
      if (decryptedRefreshToken == null) {
        log.error("Failed to decrypt Zoom refresh token for connector: {} in account: {}",
            identifierRef.getIdentifier(), identifierRef.getAccountIdentifier());
        throw new IllegalArgumentException(
            String.format("Failed to decrypt Zoom refresh token for connector: %s, account: %s",
                identifierRef.getIdentifier(), identifierRef.getAccountIdentifier()));
      }

      String basicAuth = zoomRetrofitClient.generateBasicAuthHeader(clientId, clientSecret.toCharArray());

      try {
        ZoomRefreshOAuthToken refreshedToken =
            zoomRetrofitClient.performRefreshOAuthToken(basicAuth, new String(decryptedRefreshToken));

        if (refreshedToken == null || refreshedToken.getAccessToken() == null
            || refreshedToken.getRefreshToken() == null) {
          log.error("Zoom refresh token response is invalid or incomplete for connector: {} in account: {}. Response: "
                  + "{}, accessToken: {}, refreshToken: {}",
              identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(), refreshedToken != null,
              refreshedToken != null && refreshedToken.getAccessToken() != null,
              refreshedToken != null && refreshedToken.getRefreshToken() != null);
          throw new InvalidRequestException(
              String.format("Zoom refresh token response is invalid or incomplete for connector: %s, account: %s",
                  identifierRef.getIdentifier(), identifierRef.getAccountIdentifier()));
        }

        io.harness.beans.Scope scope = io.harness.beans.Scope.of(identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier());

        oAuthTokenRefresherHelper.updateSecretSecretValue(scope, accessTokenDTO, refreshedToken.getAccessToken());
        oAuthTokenRefresherHelper.updateSecretSecretValue(scope, refreshTokenDTO, refreshedToken.getRefreshToken());

        long expirationTime = System.currentTimeMillis() + (refreshedToken.getExpiresIn() * 1000L);
        updateConnectorTokenExpirationTime(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
            identifierRef.getProjectIdentifier(), identifierRef.getIdentifier(), expirationTime);

        ZoomOAuthResponse response = new ZoomOAuthResponse();
        response.setAccess_token(refreshedToken.getAccessToken());
        response.setToken_type(refreshedToken.getTokenType());
        response.setExpires_in(refreshedToken.getExpiresIn());
        response.setScope(refreshedToken.getScope());
        response.setRefresh_token(refreshedToken.getRefreshToken());

        log.info("Successfully refreshed Zoom OAuth tokens for connector: {}. Token expires at: {}",
            identifierRef.getIdentifier(), expirationTime);
        return response;
      } finally {
        zoomRetrofitClient.clearSensitiveData(decryptedRefreshToken);
      }
    } catch (Exception e) {
      log.error("Failed to refresh Zoom OAuth token for connector: {}", identifierRef.getIdentifier(), e);
      throw new InvalidRequestException("Failed to refresh Zoom OAuth token: " + e.getMessage(), e);
    }
  }

  private SecretDTOV2 getSecretDTOV2(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SecretRefData tokenRef) {
    String resolvedOrgIdentifier = null;
    String resolvedProjectIdentifier = null;

    if (tokenRef.getScope() == Scope.ORG) {
      resolvedOrgIdentifier = orgIdentifier;
    }

    if (tokenRef.getScope() == Scope.PROJECT) {
      resolvedOrgIdentifier = orgIdentifier;
      resolvedProjectIdentifier = projectIdentifier;
    }

    io.harness.beans.Scope scope =
        io.harness.beans.Scope.of(accountIdentifier, resolvedOrgIdentifier, resolvedProjectIdentifier);

    return oAuthTokenRefresherHelper.getSecretSecretValue(scope, tokenRef, null);
  }

  private ZoomConnectorDTO decryptZoomConnector(
      IdentifierRef identifierRef, ConnectorResponseDTO connectorResponseDTO) {
    ZoomConnectorDTO zoomConnectorDTO = (ZoomConnectorDTO) connectorResponseDTO.getConnector().getConnectorConfig();

    List<DecryptableEntity> decryptableEntities =
        connectorResponseDTO.getConnector().getConnectorConfig().getDecryptableEntities();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                       .accountIdentifier(identifierRef.getAccountIdentifier())
                                       .orgIdentifier(identifierRef.getOrgIdentifier())
                                       .projectIdentifier(identifierRef.getProjectIdentifier())
                                       .build();

    if (decryptableEntities != null && !decryptableEntities.isEmpty()) {
      for (DecryptableEntity decryptableEntity : decryptableEntities) {
        encryptedDataDetails.addAll(getEncryptedDataDetails(basicNGAccessObject, decryptableEntity));
      }
    }

    ZoomConnectorDTO decryptedZoomConnector =
        (ZoomConnectorDTO) decryptionHelper.decrypt(zoomConnectorDTO, encryptedDataDetails);

    if (decryptedZoomConnector == null) {
      log.error("Failed to decrypt Zoom connector configuration for connector: {} in account: {}",
          identifierRef.getIdentifier(), identifierRef.getAccountIdentifier());
      throw new InvalidRequestException(
          String.format("Failed to decrypt Zoom connector configuration for connector: %s, account: %s",
              identifierRef.getIdentifier(), identifierRef.getAccountIdentifier()));
    }

    return decryptedZoomConnector;
  }

  private ZoomOAuthResponse getExistingTokens(IdentifierRef identifierRef, ConnectorResponseDTO connectorResponseDTO) {
    char[] accessTokenValue = null;
    char[] refreshTokenValue = null;

    try {
      log.debug("Retrieving existing tokens for connector: {} in account: {}", identifierRef.getIdentifier(),
          identifierRef.getAccountIdentifier());

      ZoomConnectorDTO zoomConnectorDTO = (ZoomConnectorDTO) connectorResponseDTO.getConnector().getConnectorConfig();

      ZoomConnectorDTO decryptedZoomConnector = decryptZoomConnector(identifierRef, connectorResponseDTO);

      if (decryptedZoomConnector.getAccessTokenRef() != null
          && decryptedZoomConnector.getAccessTokenRef().getDecryptedValue() != null) {
        accessTokenValue = decryptedZoomConnector.getAccessTokenRef().getDecryptedValue();
      }

      if (decryptedZoomConnector.getRefreshTokenRef() != null
          && decryptedZoomConnector.getRefreshTokenRef().getDecryptedValue() != null) {
        refreshTokenValue = decryptedZoomConnector.getRefreshTokenRef().getDecryptedValue();
      }

      if (accessTokenValue == null || refreshTokenValue == null) {
        log.error("Missing decrypted token values for connector: {} in account: {}. accessToken: {}, refreshToken: {}",
            identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(), accessTokenValue != null,
            refreshTokenValue != null);
        throw new InvalidRequestException(String.format("Missing decrypted token values for connector: %s, account: %s",
            identifierRef.getIdentifier(), identifierRef.getAccountIdentifier()));
      }

      ZoomOAuthResponse response = new ZoomOAuthResponse();
      response.setAccess_token(new String(accessTokenValue));
      response.setRefresh_token(new String(refreshTokenValue));

      if (zoomConnectorDTO.getTokenExpirationTime() != null) {
        long remainingSeconds = (zoomConnectorDTO.getTokenExpirationTime() - System.currentTimeMillis()) / 1000;
        response.setExpires_in((int) remainingSeconds);
      }

      log.info("Successfully retrieved existing tokens for connector: {}", identifierRef.getIdentifier());
      return response;
    } finally {
      if (accessTokenValue != null) {
        Arrays.fill(accessTokenValue, '\0');
      }
      if (refreshTokenValue != null) {
        Arrays.fill(refreshTokenValue, '\0');
      }
    }
  }

  private boolean isTokenExpiredOrExpiringSoon(Long tokenExpirationTime) {
    if (tokenExpirationTime == null) {
      return true;
    }

    long currentTime = System.currentTimeMillis();
    long bufferTime = 5 * 60 * 1000;

    return (tokenExpirationTime - bufferTime) <= currentTime;
  }

  private void updateConnectorTokenExpirationTime(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifier, long expirationTime) {
    try {
      ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
      Criteria criteria = Criteria.where(ConnectorKeys.accountIdentifier)
                              .is(accountIdentifier)
                              .and(ConnectorKeys.parentUniqueId)
                              .is(scopeInfo.getUniqueId())
                              .and(ConnectorKeys.identifier)
                              .is(connectorIdentifier)
                              .and(ConnectorKeys.type)
                              .is(ConnectorType.ZOOM)
                              .and(ConnectorKeys.deleted)
                              .ne(true);

      Update update = new Update().set(ZoomConnectorKeys.tokenExpirationTime, expirationTime);

      connectorRepository.update(criteria, update);
      log.info("Updated token expiration time for connector: {} to {}", connectorIdentifier, expirationTime);
    } catch (Exception e) {
      log.error("Failed to update token expiration time for connector: {}", connectorIdentifier, e);
    }
  }

  @Override
  public ZoomDeAuthorizationResult deAuthorizeZoomUser(ZoomDeAuthPayload requestDTO) {
    log.info("Processing Zoom deAuthorization request, payload: {}", requestDTO);

    try {
      Criteria criteria = new Criteria()
                              .and(ZOOM_USER_ID)
                              .is(requestDTO.getZoomUserId())
                              .and(ZOOM_ACCOUNT_ID)
                              .is(requestDTO.getZoomAccountId())
                              .and(ConnectorKeys.type)
                              .is(ConnectorType.ZOOM)
                              .and(API_ACCESS_TYPE)
                              .is(OAUTH)
                              .and(ConnectorKeys.deleted)
                              .is(false);

      // Find all connectors associated with this Zoom user
      Page<Connector> connectors = connectorRepository.findAll(criteria, Pageable.unpaged());

      if (connectors == null || connectors.isEmpty()) {
        log.warn("No Zoom connectors found for de-authorization");
        return ZoomDeAuthorizationResult.failed("No connectors found for the specified Zoom user");
      }

      // Process each connector
      List<String> processedConnectors = new ArrayList<>();
      List<String> failedConnectors = new ArrayList<>();

      for (Connector conn : connectors.getContent()) {
        if (!(conn instanceof ZoomConnector)) {
          log.warn("Expected ZoomConnector but found: {}", conn.getClass().getSimpleName());
          continue;
        }

        if (conn.getType() != ConnectorType.ZOOM) {
          log.error(
              "Type mismatch for ZoomConnector {}: expected ZOOM but found {}", conn.getIdentifier(), conn.getType());
          continue;
        }

        ZoomConnector zoomConnector = (ZoomConnector) conn;
        String connectorId = zoomConnector.getIdentifier();

        try {
          log.debug("Processing connector: {}", connectorId);
          ScopeInfo connectorScopeInfo =
              scopeInfoService.getScopeInfo(conn.getAccountIdentifier(), Set.of(conn.getParentUniqueId()))
                  .get(conn.getParentUniqueId())
                  .get();

          // Delete zoom connector and its secrets with the zoom connector
          if (deleteZoomConnectorAndSecret(zoomConnector, connectorScopeInfo)) {
            processedConnectors.add(connectorId);
          } else {
            failedConnectors.add(connectorId);
          }
        } catch (Exception ex) {
          log.error("Error processing connector: {}", connectorId, ex);
          failedConnectors.add(connectorId);
        }
      }

      // Prepare result based on processing outcome
      if (failedConnectors.isEmpty()) {
        return ZoomDeAuthorizationResult.success(
            String.format("Successfully de-authorized %d connectors", processedConnectors.size()));
      } else {
        return ZoomDeAuthorizationResult.partialSuccess(
            String.format("Successfully de-authorized %d connectors, %d failed", processedConnectors.size(),
                failedConnectors.size()),
            processedConnectors, failedConnectors);
      }
    } catch (Exception ex) {
      log.error("Unhandled exception during de-authorization process", ex);
      return ZoomDeAuthorizationResult.failed("System error during de-authorization: " + ex.getMessage());
    }
  }

  /**
   * Helper method to delete both connector and its associated secret
   */
  private boolean deleteZoomConnectorAndSecret(ZoomConnector connector, ScopeInfo connectorScopeInfo) {
    boolean connectorDeleted = false;
    boolean secretDeleted = false;

    // Step 1: Delete the connector
    try {
      log.info(
          "Deleting Zoom connector: {} in scope: {}", connector.getIdentifier(), connectorScopeInfo.getScopeType());

      connectorDeleted = defaultConnectorService.delete(connectorScopeInfo, connector.getIdentifier(), false);

      if (!connectorDeleted) {
        log.warn("Failed to delete connector: {}, scope: {}", connector.getIdentifier(), connectorScopeInfo);
      } else {
        log.info("Successfully deleted connector: {}, scope: {}", connector.getIdentifier(), connectorScopeInfo);
      }
    } catch (Exception ex) {
      log.error("Exception while deleting connector: {}", connector.getIdentifier(), ex);
      connectorDeleted = false;
    }

    // Step 2: Delete the associated secret
    try {
      secretDeleted = deleteZoomConnectorSecrets(connector, connectorScopeInfo);
    } catch (EntityNotFoundException ex) {
      // This is an expected case if the secret was already deleted
      log.info("Secret not found for connector {}: {}", connector.getIdentifier(), ex.getMessage());
      secretDeleted = true; // Consider it "deleted" if not found
    } catch (Exception ex) {
      log.error("Exception while deleting secret for connector: {}", connector.getIdentifier(), ex);
    }

    return connectorDeleted && secretDeleted;
  }

  /**
   * Helper method to delete the secrets associated with a Zoom connector
   */
  private boolean deleteZoomConnectorSecrets(ZoomConnector connector, ScopeInfo connectorScopeInfo) {
    boolean hasAccessToken = false;
    boolean hasRefreshToken = false;

    // Validate access token reference
    if (connector.getAccessTokenRef() == null || connector.getAccessTokenRef().isEmpty()) {
      log.warn("No Zoom accessToken secret reference found for connector: {}", connector.getIdentifier());
    } else if (validateSecretReference(connector.getAccessTokenRef())) {
      hasAccessToken = true;
    } else {
      log.error("Invalid accessToken reference format for connector: {}", connector.getIdentifier());
      return false; // Fail deletion due to invalid reference format
    }

    // Validate refresh token reference
    if (connector.getRefreshTokenRef() == null || connector.getRefreshTokenRef().isEmpty()) {
      log.warn("No Zoom refreshToken secret reference found for connector: {}", connector.getIdentifier());
    } else if (validateSecretReference(connector.getRefreshTokenRef())) {
      hasRefreshToken = true;
    } else {
      log.error("Invalid refreshToken reference format for connector: {}", connector.getIdentifier());
      return false; // Fail deletion due to invalid reference format
    }

    // If no tokens to delete, return success
    if (!hasAccessToken && !hasRefreshToken) {
      log.info("No token secrets to delete for connector: {}", connector.getIdentifier());
      return true;
    }

    boolean accessTokenDeleted = true; // Default to true if no token to delete
    boolean refreshTokenDeleted = true; // Default to true if no token to delete

    // Delete access token if it exists
    if (hasAccessToken) {
      String[] accessTokenRefs = connector.getAccessTokenRef().split("\\.");
      accessTokenDeleted = deleteZoomSecret(connector, accessTokenRefs, connectorScopeInfo);
      if (!accessTokenDeleted) {
        log.error("Failed to delete access token for connector: {}", connector.getIdentifier());
      }
    }

    // Delete refresh token if it exists
    if (hasRefreshToken) {
      String[] refreshTokenRefs = connector.getRefreshTokenRef().split("\\.");
      refreshTokenDeleted = deleteZoomSecret(connector, refreshTokenRefs, connectorScopeInfo);
      if (!refreshTokenDeleted) {
        log.error("Failed to delete refresh token for connector: {}", connector.getIdentifier());
      }
    }

    // Transaction will commit only if all operations succeed
    return accessTokenDeleted && refreshTokenDeleted;
  }

  /**
   * Validates that a secret reference has the correct format and scope
   *
   * @param reference The secret reference in format [scope].[identifier]
   * @return true if the reference is valid, false otherwise
   */
  private boolean validateSecretReference(String reference) {
    if (reference == null || reference.isEmpty()) {
      return false;
    }

    String[] parts = reference.split("\\.");
    if (parts.length > 2) {
      log.warn("Malformed secret reference: {}", reference);
      return false;
    }

    // Ensure identifier part is not empty
    if (parts.length == 0 || parts[0].isEmpty() || parts.length == 2 && parts[1].isEmpty()) {
      log.warn("Empty component in secret reference: {}", reference);
      return false;
    }

    // Validate scope if present
    if (parts.length > 1) {
      String scope = parts[0];
      if (!Arrays.asList("project", "org", "account").contains(scope)) {
        log.warn("Invalid scope '{}' in secret reference: {}", scope, reference);
        return false;
      }
    }

    return true;
  }
  /**
   * Helper method to delete a specific Zoom secret
   */
  private boolean deleteZoomSecret(ZoomConnector connector, String[] tokenRefs, ScopeInfo connectorScopeInfo) {
    // Extract secret identifier and scope from reference
    String secretScope = tokenRefs.length > 1 ? tokenRefs[0] : "project";
    String secretIdentifier = tokenRefs.length > 1 ? tokenRefs[1] : tokenRefs[0];

    String accId = connector.getAccountIdentifier();

    String orgId = null;
    String projId = null;

    switch (secretScope) {
      case "project":
        orgId = connectorScopeInfo.getOrgIdentifier();
        projId = connectorScopeInfo.getProjectIdentifier();
        break;
      case "org":
        orgId = connectorScopeInfo.getOrgIdentifier();
        break;
      case "account":
        // Both orgId and projId remain null
        break;
      default:
        break;
    }

    // Determine the scope parameters based on the secret scope type
    log.debug("Deleting secret '{}' in scope type: {}", secretIdentifier, secretScope);

    try {
      // Find the secret first to ensure it exists

      ScopeInfo secretScopeInfo = scopeInfoService.getScopeInfo(accId, orgId, projId);
      Optional<Secret> secretOpt = ngSecretServiceV2.get(secretScopeInfo, secretIdentifier);

      if (secretOpt.isEmpty()) {
        throw new EntityNotFoundException(String.format(
            "Secret with id: %s not found in scope: %s (accountId: %s, orgId: %s, projectId: %s)", secretIdentifier,
            secretScope, accId, orgId != null ? orgId : "null", projId != null ? projId : "null"));
      }

      // Delete the secret
      boolean deleted = secretService.delete(secretScopeInfo, secretIdentifier, false);

      if (deleted) {
        log.info("Successfully deleted secret: {} with scope: {}", secretIdentifier, secretOpt);
        return true;
      } else {
        log.warn("Failed to delete secret: {} with scope: {}", secretIdentifier, secretOpt);
        return false;
      }
    } catch (EntityNotFoundException e) {
      log.warn("Secret not found: {} - {}", secretIdentifier, e.getMessage());
      // Consider it a failure since we expected the secret to exist
      return false;
    } catch (Exception e) {
      log.error("Error deleting secret: {}", secretIdentifier, e);
      return false;
    }
  }
}
