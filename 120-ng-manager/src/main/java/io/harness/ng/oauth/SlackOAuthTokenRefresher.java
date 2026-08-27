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
import io.harness.connector.entities.embedded.slackconnector.SlackConnector;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.connector.SlackConnectorDTO;
import io.harness.delegate.beans.connector.slackconnector.SlackConnectorConstants;
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

import clients.iromanager.remote.connectors.slack.SlackOAuthResponse;
import clients.iromanager.remote.connectors.slack.SlackRetroFitClient;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class SlackOAuthTokenRefresher {
  @Inject private OAuthTokenRefresherHelper oAuthTokenRefresherHelper;
  @Inject NextGenConfiguration configuration;
  @Inject PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject ScopeInfoService scopeInfoService;
  @Inject private ConnectorMapper connectorMapper;
  @Inject DecryptionHelper decryptionHelper;
  @Inject private SecretNGManagerClient secretNGManagerClient;
  @Inject MongoTemplate mongoTemplate;
  @Inject NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Inject private SlackRetroFitClient slackRetroFitClient;

  OAuthRef getOAuthDecrypted(SlackConnector slackConnector) {
    return OAuthRef.builder()
        .tokenRef(SecretRefHelper.createSecretRef(slackConnector.getAccessTokenRef()))
        .refreshTokenRef(SecretRefHelper.createSecretRef(slackConnector.getRefreshTokenRef()))
        .build();
  }

  SecretDTOV2 getSecretValue(SlackConnector slackConnector, SecretRefData token) {
    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(slackConnector.getAccountIdentifier(), Set.of(slackConnector.getParentUniqueId()))
            .get(slackConnector.getParentUniqueId())
            .get();

    return oAuthTokenRefresherHelper.getSecretSecretValue(
        io.harness.beans.Scope.of(
            slackConnector.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
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
  private SlackConnectorDTO getSlackOauthDecrypted(SlackConnector entity) {
    if (entity == null) {
      throw new IllegalArgumentException("Slack Connector entity cannot be null");
    }

    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(entity.getAccountIdentifier(), Set.of(entity.getParentUniqueId()))
            .get(entity.getParentUniqueId())
            .get();

    ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(entity, scopeInfo);
    SlackConnectorDTO slackConnectorDTO = (SlackConnectorDTO) connectorDTO.getConnector().getConnectorConfig();

    if (slackConnectorDTO == null) {
      throw new IllegalArgumentException("Invalid Slack Connector Configuration");
    }

    List<EncryptedDataDetail> encryptionDetails =
        oAuthTokenRefresherHelper.getEncryptionDetails(slackConnectorDTO, scopeInfo);

    return (SlackConnectorDTO) decryptionHelper.decrypt(slackConnectorDTO, encryptionDetails);
  }

  private void updateSecretSecretValue(SlackConnector entity, SecretDTOV2 secretDTOV2, String newSecret) {
    oAuthTokenRefresherHelper.updateSecretSecretValue(
        io.harness.beans.Scope.of(
            entity.getAccountIdentifier(), secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier()),
        secretDTOV2, newSecret);
  }

  public void handle(Object entity) {
    if (!(entity instanceof SlackConnector slackConnector)) {
      log.error("Invalid entity type for Slack OAuth token refresh: {}", entity.getClass().getName());
      return;
    }

    try {
      oAuthTokenRefresherHelper.updateContext();
      log.info("Starting Slack OAuth token refresh for account: {}", slackConnector.getAccountIdentifier());
      OAuthRef oAuthRef = getOAuthDecrypted(slackConnector);
      if (oAuthRef == null || oAuthRef.getTokenRef() == null || oAuthRef.getRefreshTokenRef() == null) {
        log.error("Missing OAuth token references for account: {} and identifier: {}",
            slackConnector.getAccountIdentifier(), slackConnector.getIdentifier());
        return;
      }

      SecretDTOV2 tokenDTO = getSecretValue(slackConnector, oAuthRef.getTokenRef());
      SecretDTOV2 refreshTokenDTO = getSecretValue(slackConnector, oAuthRef.getRefreshTokenRef());

      if (tokenDTO == null || refreshTokenDTO == null) {
        log.error("tokenDTO and refreshTokenDTO are null for account: {} and identifier: {}",
            slackConnector.getAccountIdentifier(), slackConnector.getIdentifier());
        return;
      }

      SlackConnectorDTO slackConnectorDTO = getSlackOauthDecrypted(slackConnector);
      char[] decryptedValue = slackConnectorDTO.getRefreshTokenRef().getDecryptedValue();
      if (decryptedValue == null) {
        throw new IllegalArgumentException("Failed to decrypt slack refresh token for account: "
            + slackConnector.getAccountIdentifier() + " and identifier: " + slackConnector.getIdentifier());
      }

      String decryptedRefreshToken = new String(decryptedValue);

      try {
        SlackOAuthResponse slackOAuthResponse =
            slackRetroFitClient.performRefreshOAuthToken(configuration.getSlackConfig().getClientId(),
                configuration.getSlackConfig().getClientSecret(), decryptedRefreshToken);

        if (slackOAuthResponse == null) {
          log.error("Received null response while refreshing slack OAuth token for account: {} and identifier: {}",
              slackConnector.getAccountIdentifier(), slackConnector.getIdentifier());
        }

        if (slackOAuthResponse == null) {
          log.error("Slack OAuth response is null for account: {} and identifier: {}",
              slackConnector.getAccountIdentifier(), slackConnector.getIdentifier());
          throw new IllegalArgumentException("Slack OAuth response is null");
        }

        if (slackOAuthResponse.getAccess_token() == null || slackOAuthResponse.getAccess_token().isEmpty()) {
          log.error("Slack Access token is missing in the OAuth response for account: {} and identifier: {}",
              slackConnector.getAccountIdentifier(), slackConnector.getIdentifier());
        }

        if (slackOAuthResponse.getRefresh_token() == null || slackOAuthResponse.getRefresh_token().isEmpty()) {
          log.error("Slack Refresh token is missing in the OAuth response for account: {} and identifier: {}",
              slackConnector.getAccountIdentifier(), slackConnector.getIdentifier());
        }

        updateSecretSecretValue(slackConnector, tokenDTO, slackOAuthResponse.getAccess_token());
        updateSecretSecretValue(slackConnector, refreshTokenDTO, slackOAuthResponse.getRefresh_token());

        log.debug("Updated Slack OAuth tokens successfully for account: {} and identifier: {}",
            slackConnector.getAccountIdentifier(), slackConnector.getIdentifier());
      } catch (Exception e) {
        log.error("Failed to generate Slack OAuth token for account: {} and identifier: {}",
            slackConnector.getAccountIdentifier(), slackConnector.getIdentifier(), e);
      }

    } catch (Exception e) {
      log.error("Error handling Slack OAuth token refresh for account: {}", slackConnector.getAccountIdentifier(), e);
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
        SlackConnector.class,
        MongoPersistenceIterator.<SlackConnector, SpringFilterExpander>builder()
            .clazz(SlackConnector.class)
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
      Criteria criteria = Criteria.where(SlackConnectorConstants.TYPE)
                              .is(TaskGroup.SLACK)
                              .and(SlackConnectorConstants.API_ACCESS_TYPE)
                              .is(SlackConnectorConstants.OAUTH);

      query.addCriteria(criteria);
    };
  }
}