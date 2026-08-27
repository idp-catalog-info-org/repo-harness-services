/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorMapper;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.embedded.zoomconnector.ZoomConnector;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.connector.ZoomConnectorDTO;
import io.harness.delegate.beans.connector.zoomconnector.ZoomConstant;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.NGFeatureFlagHelperService;

import clients.iromanager.remote.connectors.zoom.ZoomRefreshOAuthToken;
import clients.iromanager.remote.connectors.zoom.ZoomRetrofitClient;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class ZoomOAuthTokenRefresher {
  @Inject NextGenConfiguration configuration;
  @Inject private OAuthTokenRefresherHelper oAuthTokenRefresherHelper;

  @Inject PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject MongoTemplate mongoTemplate;
  @Inject ScopeInfoService scopeInfoService;
  @Inject private ConnectorMapper connectorMapper;
  @Inject DecryptionHelper decryptionHelper;

  @Inject NgUserService ngUserService;

  @Inject private ZoomRetrofitClient zoomRetrofitClient;
  @Inject NGFeatureFlagHelperService ngFeatureFlagHelperService;

  OAuthRef getOAuthDecrypted(ZoomConnector zoomConnector) {
    return OAuthRef.builder()
        .tokenRef(SecretRefHelper.createSecretRef(zoomConnector.getAccessTokenRef()))
        .refreshTokenRef(SecretRefHelper.createSecretRef(zoomConnector.getRefreshTokenRef()))
        .build();
  }

  public void handle(Object entity) {
    log.debug("Handling Zoom OAuth token refresh for entity:");
    if (!(entity instanceof ZoomConnector zoomConnector)) {
      log.error("Invalid entity type for Zoom OAuth token refresh: {}", entity.getClass().getName());
      return;
    }

    try {
      oAuthTokenRefresherHelper.updateContext();
      OAuthRef oAuthRef = getOAuthDecrypted(zoomConnector);
      if (oAuthRef == null || oAuthRef.getTokenRef() == null || oAuthRef.getRefreshTokenRef() == null) {
        log.error("Missing OAuth token references for account: {}", zoomConnector.getAccountIdentifier());
        return;
      }

      SecretDTOV2 tokenDTO = getSecretSecretValue(zoomConnector, oAuthRef.getTokenRef());
      SecretDTOV2 refreshTokenDTO = getSecretSecretValue(zoomConnector, oAuthRef.getRefreshTokenRef());

      if (tokenDTO == null || refreshTokenDTO == null) {
        log.error("Failed to retrieve token or refresh token for account: {}", zoomConnector.getAccountIdentifier());
        return;
      }

      String clientId = configuration.getZoomConfig().getClientId();
      String clientSecret = configuration.getZoomConfig().getClientSecret();

      try {
        ZoomConnectorDTO zoomConnectorDTO = getZoomOauthDecrypted(zoomConnector);
        if (zoomConnectorDTO == null || zoomConnectorDTO.getRefreshTokenRef() == null
            || zoomConnectorDTO.getRefreshTokenRef().getDecryptedValue() == null) {
          throw new IllegalArgumentException("ZoomConnectorDTO or its refresh token reference is null.");
        }

        String basicAuth = zoomRetrofitClient.generateBasicAuthHeader(clientId, clientSecret.toCharArray());
        if (basicAuth == null) {
          throw new IllegalArgumentException("Generated basicAuth header is null.");
        }

        char[] decryptedValue = zoomConnectorDTO.getRefreshTokenRef().getDecryptedValue();
        if (decryptedValue == null) {
          throw new IllegalArgumentException("Failed to decrypt zoom refresh token.");
        }

        String decryptedAccessToken = new String(decryptedValue);

        ZoomRefreshOAuthToken zoomRefreshOAuthToken =
            zoomRetrofitClient.performRefreshOAuthToken(basicAuth, decryptedAccessToken);
        if (zoomRefreshOAuthToken == null || zoomRefreshOAuthToken.getAccessToken() == null
            || zoomRefreshOAuthToken.getRefreshToken() == null) {
          throw new NullPointerException("ZoomRefreshOAuthToken or its tokens are null.");
        }

        updateSecretSecretValue(zoomConnector, tokenDTO, zoomRefreshOAuthToken.getAccessToken());
        updateSecretSecretValue(zoomConnector, refreshTokenDTO, zoomRefreshOAuthToken.getRefreshToken());

      } catch (Exception e) {
        log.error("Error handling Zoom OAuth token refresh for account: {}, connector: {}",
            zoomConnector.getAccountIdentifier(), zoomConnector.getIdentifier(), e);
      }
      log.info("Updated Zoom OAuth tokens successfully for account: {}, connector: {}",
          zoomConnector.getAccountIdentifier(), zoomConnector.getIdentifier());

    } catch (Exception e) {
      log.error("Error handling Zoom OAuth token refresh for account: {}, connector: {}",
          zoomConnector.getAccountIdentifier(), zoomConnector.getIdentifier(), e);
    }
  }

  public void registerIterators(int threadPoolSize) {
    SpringFilterExpander springFilterExpander = getFilterQuery();

    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(this.getClass().getName())
            .poolSize(threadPoolSize)
            .interval(ofSeconds(10))
            .build(),
        ZoomConnector.class,
        MongoPersistenceIterator.<ZoomConnector, SpringFilterExpander>builder()
            .clazz(ZoomConnector.class)
            .fieldName("nextTokenRenewIteration")
            .targetInterval(ofMinutes(configuration.getOauthRefreshFrequency()))
            .acceptableExecutionTime(ofMinutes(1))
            .acceptableNoAlertDelay(ofMinutes(1))
            .filterExpander(springFilterExpander)
            .handler(this::handle)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  private SpringFilterExpander getFilterQuery() {
    return query -> {
      Criteria criteria =
          Criteria.where(ZoomConstant.TYPE).is(TaskGroup.ZOOM).and(ZoomConstant.API_ACCESS_TYPE).is(ZoomConstant.OAUTH);
      query.addCriteria(criteria);
    };
  }

  SecretDTOV2 getSecretSecretValue(ZoomConnector zoomConnector, SecretRefData token) {
    String orgIdentifier = null;
    String projectIdentifier = null;

    if (token.getScope() == Scope.ORG) {
      orgIdentifier = zoomConnector.getOrgIdentifier();
    }

    if (token.getScope() == Scope.PROJECT) {
      orgIdentifier = zoomConnector.getOrgIdentifier();
      projectIdentifier = zoomConnector.getProjectIdentifier();
    }
    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(zoomConnector.getAccountIdentifier(), Set.of(zoomConnector.getParentUniqueId()))
            .get(zoomConnector.getParentUniqueId())
            .get();

    return oAuthTokenRefresherHelper.getSecretSecretValue(
        io.harness.beans.Scope.of(
            zoomConnector.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        token, scopeInfo);
  }

  private ZoomConnectorDTO getZoomOauthDecrypted(ZoomConnector entity) {
    if (entity == null) {
      throw new IllegalArgumentException("ZoomConnector entity cannot be null");
    }

    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(entity.getAccountIdentifier(), Set.of(entity.getParentUniqueId()))
            .get(entity.getParentUniqueId())
            .get();

    ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(entity, scopeInfo);
    ZoomConnectorDTO zoomConnectorDTO = (ZoomConnectorDTO) connectorDTO.getConnector().getConnectorConfig();

    if (zoomConnectorDTO == null) {
      throw new IllegalArgumentException("Invalid Zoom Connector Configuration");
    }

    List<EncryptedDataDetail> encryptionDetails =
        oAuthTokenRefresherHelper.getEncryptionDetails(zoomConnectorDTO, scopeInfo);

    return (ZoomConnectorDTO) decryptionHelper.decrypt(zoomConnectorDTO, encryptionDetails);
  }

  protected void setPrincipal(String userId, String accountIdentifier) {
    Principal principal = SecurityContextBuilder.getPrincipal();
    boolean isUserPrincipal = principal instanceof UserPrincipal;
    if (principal == null || !isUserPrincipal) {
      Optional<UserInfo> userInfo = ngUserService.getUserById(userId);
      if (userInfo.isEmpty()) {
        log.error("Failed to get user details for user id: {}", userId);
        throw new InvalidRequestException(String.format("Failed to get user details for user id: %s", userId));
      }
      principal = new UserPrincipal(userId, userInfo.get().getEmail(), userInfo.get().getName(), accountIdentifier);
    }
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
  }

  private void updateSecretSecretValue(ZoomConnector entity, SecretDTOV2 secretDTOV2, String newSecret) {
    oAuthTokenRefresherHelper.updateSecretSecretValue(
        io.harness.beans.Scope.of(
            entity.getAccountIdentifier(), secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier()),
        secretDTOV2, newSecret);
  }
}