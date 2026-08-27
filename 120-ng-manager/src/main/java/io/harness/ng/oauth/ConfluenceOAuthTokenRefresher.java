/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.beans.DecryptableEntity;
import io.harness.beans.ScopeInfo;
import io.harness.connector.ConnectorMapper;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.embedded.confluenceconnector.ConfluenceConnector;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.connector.ConfluenceConnectorDTO;
import io.harness.delegate.beans.connector.confluenceconnector.ConfluenceConnectorConstants;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.NGFeatureFlagHelperService;

import clients.iromanager.remote.connectors.confluence.ConfluenceOAuthResponse;
import clients.iromanager.remote.connectors.confluence.ConfluenceRetroFitClient;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class ConfluenceOAuthTokenRefresher {
  @Inject private io.harness.ng.oauth.OAuthTokenRefresherHelper oAuthTokenRefresherHelper;
  @Inject NextGenConfiguration configuration;
  @Inject PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject ScopeInfoService scopeInfoService;
  @Inject private ConnectorMapper connectorMapper;
  @Inject DecryptionHelper decryptionHelper;
  @Inject private SecretNGManagerClient secretNGManagerClient;
  @Inject MongoTemplate mongoTemplate;
  @Inject NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Inject private ConfluenceRetroFitClient confluenceRetroFitClient;

  io.harness.ng.oauth.OAuthRef getOAuthDecrypted(ConfluenceConnector confluenceConnector) {
    return io.harness.ng.oauth.OAuthRef.builder()
        .tokenRef(SecretRefHelper.createSecretRef(confluenceConnector.getAccessTokenRef()))
        .refreshTokenRef(SecretRefHelper.createSecretRef(confluenceConnector.getRefreshTokenRef()))
        .build();
  }

  SecretDTOV2 getSecretValue(ConfluenceConnector confluenceConnector, SecretRefData token) {
    ScopeInfo scopeInfo =
        scopeInfoService
            .getScopeInfo(confluenceConnector.getAccountIdentifier(), Set.of(confluenceConnector.getParentUniqueId()))
            .get(confluenceConnector.getParentUniqueId())
            .get();

    return oAuthTokenRefresherHelper.getSecretSecretValue(
        io.harness.beans.Scope.of(
            scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        token, scopeInfo);
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
  private ConfluenceConnectorDTO getConfluenceOauthDecrypted(ConfluenceConnector entity) {
    if (entity == null) {
      throw new IllegalArgumentException("[CONFLUENCE OAUTH] Confluence Connector entity cannot be null");
    }

    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(entity.getAccountIdentifier(), Set.of(entity.getParentUniqueId()))
            .get(entity.getParentUniqueId())
            .get();

    ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(entity, scopeInfo);
    ConfluenceConnectorDTO confluenceConnectorDTO =
        (ConfluenceConnectorDTO) connectorDTO.getConnector().getConnectorConfig();

    if (confluenceConnectorDTO == null) {
      throw new IllegalArgumentException("[CONFLUENCE OAUTH] Invalid Confluence Connector Configuration");
    }

    List<EncryptedDataDetail> encryptionDetails =
        oAuthTokenRefresherHelper.getEncryptionDetails(confluenceConnectorDTO, scopeInfo);

    return (ConfluenceConnectorDTO) decryptionHelper.decrypt(confluenceConnectorDTO, encryptionDetails);
  }

  private void updateSecretSecretValue(ConfluenceConnector entity, SecretDTOV2 secretDTOV2, String newSecret) {
    oAuthTokenRefresherHelper.updateSecretSecretValue(
        io.harness.beans.Scope.of(
            entity.getAccountIdentifier(), secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier()),
        secretDTOV2, newSecret);
  }

  public void handle(Object entity) {
    if (!(entity instanceof ConfluenceConnector confluenceConnector)) {
      log.error("[CONFLUENCE OAUTH] Invalid entity type: {}", entity.getClass().getName());
      return;
    }

    try {
      oAuthTokenRefresherHelper.updateContext();
      log.info("[CONFLUENCE OAUTH] Starting token refresh for account: {}, connector: {}",
          confluenceConnector.getAccountIdentifier(), confluenceConnector.getIdentifier());
      io.harness.ng.oauth.OAuthRef oAuthRef = getOAuthDecrypted(confluenceConnector);
      if (oAuthRef == null || oAuthRef.getTokenRef() == null || oAuthRef.getRefreshTokenRef() == null) {
        log.error("[CONFLUENCE OAUTH] Missing OAuth token references for account: {}, connector: {}",
            confluenceConnector.getAccountIdentifier(), confluenceConnector.getIdentifier());
        return;
      }

      SecretDTOV2 tokenDTO = getSecretValue(confluenceConnector, oAuthRef.getTokenRef());
      SecretDTOV2 refreshTokenDTO = getSecretValue(confluenceConnector, oAuthRef.getRefreshTokenRef());

      if (tokenDTO == null || refreshTokenDTO == null) {
        log.error("[CONFLUENCE OAUTH] Failed to resolve secrets for account: {}, connector: {}, tokenDTO: {}, "
                + "refreshTokenDTO: {}",
            confluenceConnector.getAccountIdentifier(), confluenceConnector.getIdentifier(),
            (tokenDTO == null ? "null" : "present"), (refreshTokenDTO == null ? "null" : "present"));
        return;
      }

      ConfluenceConnectorDTO confluenceConnectorDTO = getConfluenceOauthDecrypted(confluenceConnector);
      char[] decryptedValue = confluenceConnectorDTO.getRefreshTokenRef().getDecryptedValue();
      if (decryptedValue == null) {
        throw new IllegalArgumentException("[CONFLUENCE OAUTH] Failed to decrypt refresh token for account: "
            + confluenceConnector.getAccountIdentifier() + ", connector: " + confluenceConnector.getIdentifier());
      }

      String decryptedRefreshToken = new String(decryptedValue);

      try {
        ConfluenceOAuthResponse confluenceOAuthResponse =
            confluenceRetroFitClient.performOAuthRefreshTokenRequest(configuration.getConfluenceConfig().getClientId(),
                configuration.getConfluenceConfig().getClientSecret(), decryptedRefreshToken);

        if (confluenceOAuthResponse == null) {
          log.error("[CONFLUENCE OAUTH] Received null response from Atlassian for account: {}, connector: {}",
              confluenceConnector.getAccountIdentifier(), confluenceConnector.getIdentifier());
          return;
        }

        if (confluenceOAuthResponse.getAccess_token() == null || confluenceOAuthResponse.getAccess_token().isEmpty()) {
          log.error("[CONFLUENCE OAUTH] Access token missing in response for account: {}, connector: {}",
              confluenceConnector.getAccountIdentifier(), confluenceConnector.getIdentifier());
          return;
        }

        if (confluenceOAuthResponse.getRefresh_token() == null
            || confluenceOAuthResponse.getRefresh_token().isEmpty()) {
          log.error("[CONFLUENCE OAUTH] Refresh token missing in response for account: {}, connector: {}",
              confluenceConnector.getAccountIdentifier(), confluenceConnector.getIdentifier());
          return;
        }

        updateSecretSecretValue(confluenceConnector, tokenDTO, confluenceOAuthResponse.getAccess_token());
        updateSecretSecretValue(confluenceConnector, refreshTokenDTO, confluenceOAuthResponse.getRefresh_token());

        log.debug("[CONFLUENCE OAUTH] Successfully refreshed tokens for account: {}, connector: {}",
            confluenceConnector.getAccountIdentifier(), confluenceConnector.getIdentifier());
      } catch (Exception e) {
        log.error("[CONFLUENCE OAUTH] Failed to refresh token for account: {}, connector: {}. Error: {}",
            confluenceConnector.getAccountIdentifier(), confluenceConnector.getIdentifier(), e.getMessage(), e);
      }

    } catch (Exception e) {
      log.error("[CONFLUENCE OAUTH] Error handling token refresh for account: {}, connector: {}",
          confluenceConnector.getAccountIdentifier(), confluenceConnector.getIdentifier(), e);
    }
  }

  public void registerIterators(int threadPoolSize) {
    log.info("[CONFLUENCE OAUTH] Registering iterator with frequency: {} minutes, enabled: {}",
        configuration.getOauthRefreshFrequency(), configuration.isOauthRefreshEnabled());

    SpringFilterExpander springFilterExpander = getFilterQuery();

    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name(this.getClass().getName())
            .poolSize(threadPoolSize)
            .interval(ofSeconds(10))
            .build(),
        ConfluenceConnector.class,
        MongoPersistenceIterator.<ConfluenceConnector, SpringFilterExpander>builder()
            .clazz(ConfluenceConnector.class)
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
      Criteria criteria = Criteria.where(ConfluenceConnectorConstants.TYPE)
                              .is(TaskGroup.CONFLUENCE)
                              .and(ConfluenceConnectorConstants.API_ACCESS_TYPE)
                              .is(ConfluenceConnectorConstants.OAUTH);

      query.addCriteria(criteria);
    };
  }
}
