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
import io.harness.connector.entities.embedded.msteamsconnector.MsTeamsConnector;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.connector.MsTeamsConnectorDTO;
import io.harness.delegate.beans.connector.msteamsconnector.MsTeamsConnectorConstants;
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

import clients.iromanager.remote.connectors.msteams.MsTeamsOAuthResponse;
import clients.iromanager.remote.connectors.msteams.MsTeamsRetroFitClient;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
public class MsTeamsOAuthTokenRefresher {
  @Inject private OAuthTokenRefresherHelper oAuthTokenRefresherHelper;
  @Inject NextGenConfiguration configuration;
  @Inject PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject ScopeInfoService scopeInfoService;
  @Inject private ConnectorMapper connectorMapper;
  @Inject DecryptionHelper decryptionHelper;
  @Inject private SecretNGManagerClient secretNGManagerClient;
  @Inject MongoTemplate mongoTemplate;
  @Inject NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Inject private MsTeamsRetroFitClient msTeamsRetroFitClient;

  OAuthRef getOAuthDecrypted(MsTeamsConnector msTeamsConnector) {
    return OAuthRef.builder()
        .tokenRef(SecretRefHelper.createSecretRef(msTeamsConnector.getAccessTokenRef()))
        .refreshTokenRef(SecretRefHelper.createSecretRef(msTeamsConnector.getRefreshTokenRef()))
        .build();
  }

  SecretDTOV2 getSecretValue(MsTeamsConnector msTeamsConnector, SecretRefData token) {
    ScopeInfo scopeInfo =
        scopeInfoService
            .getScopeInfo(msTeamsConnector.getAccountIdentifier(), Set.of(msTeamsConnector.getParentUniqueId()))
            .get(msTeamsConnector.getParentUniqueId())
            .get();

    return oAuthTokenRefresherHelper.getSecretSecretValue(
        io.harness.beans.Scope.of(
            msTeamsConnector.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
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
  private MsTeamsConnectorDTO getMsTeamsOauthDecrypted(MsTeamsConnector entity) {
    if (entity == null) {
      throw new IllegalArgumentException("MsTeams Connector entity cannot be null");
    }

    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(entity.getAccountIdentifier(), Set.of(entity.getParentUniqueId()))
            .get(entity.getParentUniqueId())
            .get();

    ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(entity, scopeInfo);
    MsTeamsConnectorDTO msTeamsConnectorDTO = (MsTeamsConnectorDTO) connectorDTO.getConnector().getConnectorConfig();

    if (msTeamsConnectorDTO == null) {
      throw new IllegalArgumentException("Invalid MsTeams Connector Configuration");
    }

    List<EncryptedDataDetail> encryptionDetails =
        oAuthTokenRefresherHelper.getEncryptionDetails(msTeamsConnectorDTO, scopeInfo);

    return (MsTeamsConnectorDTO) decryptionHelper.decrypt(msTeamsConnectorDTO, encryptionDetails);
  }

  private void updateSecretSecretValue(MsTeamsConnector entity, SecretDTOV2 secretDTOV2, String newSecret) {
    oAuthTokenRefresherHelper.updateSecretSecretValue(
        io.harness.beans.Scope.of(
            entity.getAccountIdentifier(), secretDTOV2.getOrgIdentifier(), secretDTOV2.getProjectIdentifier()),
        secretDTOV2, newSecret);
  }

  public void handle(Object entity) {
    if (!(entity instanceof MsTeamsConnector msTeamsConnector)) {
      log.error("Invalid entity type for MsTeams OAuth token refresh: {}", entity.getClass().getName());
      return;
    }

    try {
      oAuthTokenRefresherHelper.updateContext();
      log.info("Starting MsTeams OAuth token refresh for account: {}", msTeamsConnector.getAccountIdentifier());
      OAuthRef oAuthRef = getOAuthDecrypted(msTeamsConnector);
      if (oAuthRef == null || oAuthRef.getTokenRef() == null || oAuthRef.getRefreshTokenRef() == null) {
        log.error("Missing OAuth token references for account: {} and identifier: {}",
            msTeamsConnector.getAccountIdentifier(), msTeamsConnector.getIdentifier());
        return;
      }

      SecretDTOV2 tokenDTO = getSecretValue(msTeamsConnector, oAuthRef.getTokenRef());
      SecretDTOV2 refreshTokenDTO = getSecretValue(msTeamsConnector, oAuthRef.getRefreshTokenRef());

      if (tokenDTO == null || refreshTokenDTO == null) {
        log.error("Failed to retrieve token or refresh token for account: {} and identifier: {}",
            msTeamsConnector.getAccountIdentifier(), msTeamsConnector.getIdentifier());
        return;
      }

      MsTeamsConnectorDTO msTeamsConnectorDTO = getMsTeamsOauthDecrypted(msTeamsConnector);
      char[] decryptedValue = msTeamsConnectorDTO.getRefreshTokenRef().getDecryptedValue();
      if (decryptedValue == null) {
        throw new IllegalArgumentException("Failed to decrypt ms teams refresh token for account: "
            + msTeamsConnector.getAccountIdentifier() + " and identifier: " + msTeamsConnector.getIdentifier());
      }

      String decryptedRefreshToken = new String(decryptedValue);

      try {
        MsTeamsOAuthResponse msTeamsOAuthResponse =
            msTeamsRetroFitClient.performOAuthRefreshTokenRequest(configuration.getMsTeamsConfig().getClientId(),
                configuration.getMsTeamsConfig().getClientSecret(), decryptedRefreshToken);

        if (msTeamsOAuthResponse == null) {
          log.error("Received null response while refreshing MsTeams OAuth token for account: {} and identifier: {}",
              msTeamsConnector.getAccountIdentifier(), msTeamsConnector.getIdentifier());
        }

        assert msTeamsOAuthResponse != null;
        if (msTeamsOAuthResponse.getAccessToken() == null || msTeamsOAuthResponse.getAccessToken().isEmpty()) {
          log.error("MsTeams Access token is missing in the OAuth response for account: {} and identifier: {}",
              msTeamsConnector.getAccountIdentifier(), msTeamsConnector.getIdentifier());
        }

        if (msTeamsOAuthResponse.getRefreshToken() == null || msTeamsOAuthResponse.getRefreshToken().isEmpty()) {
          log.error("MsTeams Refresh token is missing in the OAuth response for account: {} and identifier: {}",
              msTeamsConnector.getAccountIdentifier(), msTeamsConnector.getIdentifier());
        }

        updateSecretSecretValue(msTeamsConnector, tokenDTO, msTeamsOAuthResponse.getAccessToken());
        updateSecretSecretValue(msTeamsConnector, refreshTokenDTO, msTeamsOAuthResponse.getRefreshToken());

        log.debug("Updated MsTeams OAuth tokens successfully for account: {} and identifier: {}",
            msTeamsConnector.getAccountIdentifier(), msTeamsConnector.getIdentifier());
      } catch (Exception e) {
        log.error("Failed to generate MsTeams OAuth token for account: {} and identifier: {}",
            msTeamsConnector.getAccountIdentifier(), msTeamsConnector.getIdentifier(), e);
      }

    } catch (Exception e) {
      log.error(
          "Error handling MsTeams OAuth token refresh for account: {}", msTeamsConnector.getAccountIdentifier(), e);
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
        MsTeamsConnector.class,
        MongoPersistenceIterator.<MsTeamsConnector, SpringFilterExpander>builder()
            .clazz(MsTeamsConnector.class)
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
      Criteria criteria = Criteria.where(MsTeamsConnectorConstants.TYPE)
                              .is(TaskGroup.MS_TEAMS)
                              .and(MsTeamsConnectorConstants.API_ACCESS_TYPE)
                              .is(MsTeamsConnectorConstants.OAUTH);

      query.addCriteria(criteria);
    };
  }
}
