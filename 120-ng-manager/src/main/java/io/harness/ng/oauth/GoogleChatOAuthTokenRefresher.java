/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 *
 * Requires: GoogleChatConnector entity (with nextTokenRenewIteration, PersistentRegularIterable),
 * GoogleChatConnectorDTO, and ConnectorType.GOOGLE_CHAT from the Google Chat connector feature.
 */

package io.harness.ng.oauth;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.beans.DecryptableEntity;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.connector.ConnectorMapper;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.embedded.googlechatconnector.GoogleChatConnector;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.connector.GoogleChatConnectorDTO;
import io.harness.delegate.beans.connector.googlechatconnector.GoogleChatConnectorConstants;
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
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;

import clients.iromanager.remote.connectors.googlechat.GoogleChatOAuthResponse;
import clients.iromanager.remote.connectors.googlechat.GoogleChatRetroFitClient;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class GoogleChatOAuthTokenRefresher {
  private static final String LOG_PREFIX = "[GOOGLE CHAT]";

  @Inject private OAuthTokenRefresherHelper oAuthTokenRefresherHelper;
  @Inject NextGenConfiguration configuration;
  @Inject PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject ScopeInfoClient scopeInfoClient;
  @Inject ScopeInfoService scopeInfoService;
  @Inject private ConnectorMapper connectorMapper;
  @Inject DecryptionHelper decryptionHelper;
  @Inject private SecretNGManagerClient secretNGManagerClient;
  @Inject MongoTemplate mongoTemplate;

  @Inject private GoogleChatRetroFitClient googleChatRetroFitClient;

  OAuthRef getOAuthDecrypted(GoogleChatConnector connector) {
    return OAuthRef.builder()
        .tokenRef(SecretRefHelper.createSecretRef(connector.getAccessTokenRef()))
        .refreshTokenRef(SecretRefHelper.createSecretRef(connector.getRefreshTokenRef()))
        .build();
  }

  SecretDTOV2 getSecretValue(GoogleChatConnector connector, SecretRefData token) {
    ScopeInfo secretScopeInfo = resolveSecretScope(connector, token);

    return oAuthTokenRefresherHelper.getSecretSecretValue(
        io.harness.beans.Scope.of(secretScopeInfo.getAccountIdentifier(), secretScopeInfo.getOrgIdentifier(),
            secretScopeInfo.getProjectIdentifier()),
        token, secretScopeInfo);
  }

  private ScopeInfo resolveSecretScope(GoogleChatConnector connector, SecretRefData token) {
    io.harness.encryption.Scope tokenScope = token.getScope();
    String accountId = connector.getAccountIdentifier();

    if (tokenScope == io.harness.encryption.Scope.ACCOUNT) {
      return ScopeInfo.builder().accountIdentifier(accountId).uniqueId(accountId).scopeType(ScopeLevel.ACCOUNT).build();
    }

    ScopeInfo connectorScopeInfo =
        NGRestUtils.getResponse(scopeInfoClient.getScopeInfos(accountId, Set.of(connector.getParentUniqueId())))
            .get(connector.getParentUniqueId())
            .get();

    if (tokenScope == io.harness.encryption.Scope.ORG) {
      String orgIdentifier = connectorScopeInfo.getOrgIdentifier();
      if (orgIdentifier == null) {
        log.error("{} Cannot resolve ORG-scoped secret for account-scoped connector. accountId={}, connectorId={}",
            LOG_PREFIX, accountId, connector.getIdentifier());
        return connectorScopeInfo;
      }
      return scopeInfoService.getScopeInfo(accountId, orgIdentifier, null);
    }

    return connectorScopeInfo;
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

  private GoogleChatConnectorDTO getGoogleChatOauthDecrypted(GoogleChatConnector entity) {
    if (entity == null) {
      throw new IllegalArgumentException("Google Chat Connector entity cannot be null");
    }

    ScopeInfo scopeInfo = NGRestUtils
                              .getResponse(scopeInfoClient.getScopeInfos(
                                  entity.getAccountIdentifier(), Set.of(entity.getParentUniqueId())))
                              .get(entity.getParentUniqueId())
                              .get();

    ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(entity, scopeInfo);
    GoogleChatConnectorDTO googleChatConnectorDTO =
        (GoogleChatConnectorDTO) connectorDTO.getConnector().getConnectorConfig();

    if (googleChatConnectorDTO == null) {
      throw new IllegalArgumentException("Invalid Google Chat Connector Configuration");
    }

    List<EncryptedDataDetail> encryptionDetails =
        oAuthTokenRefresherHelper.getEncryptionDetails(googleChatConnectorDTO, scopeInfo);

    return (GoogleChatConnectorDTO) decryptionHelper.decrypt(googleChatConnectorDTO, encryptionDetails);
  }

  private void updateSecretSecretValue(GoogleChatConnector entity, SecretDTOV2 secretDTOV2, String newSecret) {
    oAuthTokenRefresherHelper.updateSecretSecretValue(
        io.harness.beans.Scope.of(
            entity.getAccountIdentifier(), secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier()),
        secretDTOV2, newSecret);
  }

  public void handle(Object entity) {
    if (!(entity instanceof GoogleChatConnector googleChatConnector)) {
      log.error(
          "{} Invalid entity type for Google Chat OAuth token refresh: {}", LOG_PREFIX, entity.getClass().getName());
      return;
    }

    String accountId = googleChatConnector.getAccountIdentifier();
    String connectorId = googleChatConnector.getIdentifier();
    String connectorName = googleChatConnector.getName();

    try {
      oAuthTokenRefresherHelper.updateContext();
      log.info("{} Starting Google Chat OAuth token refresh for connector: accountId={}, connectorId={}, name={}",
          LOG_PREFIX, accountId, connectorId, connectorName);

      if (configuration.getGoogleChatConfig() == null) {
        log.error("{} Google Chat OAuth config (googleChatConfig) is not configured; skipping refresh. connectorId={}, "
                + "accountId={}",
            LOG_PREFIX, connectorId, accountId);
        return;
      }

      OAuthRef oAuthRef = getOAuthDecrypted(googleChatConnector);
      if (oAuthRef == null || oAuthRef.getTokenRef() == null || oAuthRef.getRefreshTokenRef() == null) {
        log.error("{} Missing OAuth token references (accessTokenRef or refreshTokenRef). accountId={}, connectorId={}",
            LOG_PREFIX, accountId, connectorId);
        return;
      }

      SecretDTOV2 tokenDTO = getSecretValue(googleChatConnector, oAuthRef.getTokenRef());
      SecretDTOV2 refreshTokenDTO = getSecretValue(googleChatConnector, oAuthRef.getRefreshTokenRef());

      if (tokenDTO == null || refreshTokenDTO == null) {
        log.error("{} Failed to resolve secret references for access or refresh token. accountId={}, connectorId={}",
            LOG_PREFIX, accountId, connectorId);
        return;
      }

      GoogleChatConnectorDTO googleChatConnectorDTO = getGoogleChatOauthDecrypted(googleChatConnector);
      char[] decryptedValue = googleChatConnectorDTO.getRefreshTokenRef().getDecryptedValue();
      if (decryptedValue == null) {
        log.error("{} Failed to decrypt refresh token secret. accountId={}, connectorId={}", LOG_PREFIX, accountId,
            connectorId);
        throw new IllegalArgumentException("Failed to decrypt Google Chat refresh token for account: " + accountId
            + " and identifier: " + connectorId);
      }

      String decryptedRefreshToken = new String(decryptedValue);
      log.debug(
          "{} Refresh token decrypted successfully, calling Google OAuth token endpoint. accountId={}, connectorId={}",
          LOG_PREFIX, accountId, connectorId);

      try {
        GoogleChatOAuthResponse response =
            googleChatRetroFitClient.performRefreshOAuthToken(configuration.getGoogleChatConfig().getClientId(),
                configuration.getGoogleChatConfig().getClientSecret(), decryptedRefreshToken);

        if (response == null || response.getAccessToken() == null || response.getAccessToken().isEmpty()) {
          log.error("{} Google OAuth token response is null or missing access_token. accountId={}, connectorId={}",
              LOG_PREFIX, accountId, connectorId);
          return;
        }

        updateSecretSecretValue(googleChatConnector, tokenDTO, response.getAccessToken());
        boolean refreshTokenRotated = response.getRefreshToken() != null && !response.getRefreshToken().isEmpty();
        if (refreshTokenRotated) {
          updateSecretSecretValue(googleChatConnector, refreshTokenDTO, response.getRefreshToken());
        }

        log.info("{} Google Chat OAuth token refresh completed successfully. accountId={}, connectorId={}, name={}, "
                + "refreshTokenRotated={}",
            LOG_PREFIX, accountId, connectorId, connectorName, refreshTokenRotated);
      } catch (Exception e) {
        log.error("{} Failed to refresh Google Chat OAuth token (Google API or secret update error). accountId={}, "
                + "connectorId={}, error={}",
            LOG_PREFIX, accountId, connectorId, e.getMessage(), e);
      }

    } catch (Exception e) {
      log.error("{} Error during Google Chat OAuth token refresh. accountId={}, connectorId={}, error={}", LOG_PREFIX,
          accountId, connectorId, e.getMessage(), e);
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
        GoogleChatConnector.class,
        MongoPersistenceIterator.<GoogleChatConnector, SpringFilterExpander>builder()
            .clazz(GoogleChatConnector.class)
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
      Criteria criteria = Criteria.where(GoogleChatConnectorConstants.TYPE)
                              .is(TaskGroup.GOOGLE_CHAT)
                              .and(GoogleChatConnectorConstants.API_ACCESS_TYPE)
                              .is(GoogleChatConnectorConstants.OAUTH);

      query.addCriteria(criteria);
    };
  }
}
