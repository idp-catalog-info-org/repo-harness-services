/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.gitsync.common.beans.GitlabOnPremSCM;
import io.harness.gitsync.common.beans.GitlabOnPremSCM.GitlabOnPremSCMKeys;
import io.harness.gitsync.common.beans.UserSourceCodeManager.UserSourceCodeManagerKeys;
import io.harness.gitsync.common.mappers.GitlabOnPremSCMMapper;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AutoLogContext;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.provider.ProviderInfoParams;
import io.harness.provider.ProviderRefreshTokenResponse;
import io.harness.provider.ProviderRefreshTokenTaskParameters;
import io.harness.provider.ProviderService;
import io.harness.provider.dto.GetProviderResponseDTO;
import io.harness.provider.entity.ProviderType;
import io.harness.provider.mapper.ProviderMapper;
import io.harness.secretmanagerclient.dto.config.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
public class GitlabOnPremSCMOAuthTokenRefresher extends AbstractSCMOAuthTokenRefresher<GitlabOnPremSCM> {
  @Inject ProviderService providerService;
  @Inject NGSecretManagerService ngSecretManagerService;
  @Inject NGEncryptedDataService ngEncryptedDataService;
  @Inject ProviderMapper providerMapper;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @Inject SecretCrudService ngSecretService;

  private static final int DELEGATE_TASK_TIMEOUT_IN_SECONDS = 30;

  @Inject
  public GitlabOnPremSCMOAuthTokenRefresher(
      PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
  }

  public void registerIterators(int threadPoolSize) {
    log.info("OAuth Refresh token iterator registered for Gitlab On Prem with Frequency:{}",
        configuration.getOauthRefreshFrequency());

    if (configuration.isOauthRefreshEnabled()) {
      SpringFilterExpander springFilterExpander = getFilterQuery();

      persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
          PersistenceIteratorFactory.PumpExecutorOptions.builder()
              .name(this.getClass().getName())
              .poolSize(threadPoolSize)
              .interval(ofSeconds(10))
              .build(),
          GitlabOnPremSCM.class,
          MongoPersistenceIterator.<GitlabOnPremSCM, SpringFilterExpander>builder()
              .clazz(GitlabOnPremSCM.class)
              .fieldName(GitlabOnPremSCMKeys.nextTokenRenewIteration)
              .targetInterval(ofMinutes(configuration.getOauthRefreshFrequency()))
              .acceptableExecutionTime(ofMinutes(1))
              .acceptableNoAlertDelay(ofMinutes(1))
              .filterExpander(springFilterExpander)
              .handler(this)
              .schedulingType(REGULAR)
              .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
              .redistribute(true));
    }
  }

  @Override
  public void handle(GitlabOnPremSCM entity) {
    try (AutoLogContext autoLogContext = new TokenRefresherLogContext(entity.getAccountIdentifier(),
             entity.getUserIdentifier(), entity.getType(), AutoLogContext.OverrideBehavior.OVERRIDE_NESTS)) {
      try {
        log.info("Starting Token Refresh for provider {}", entity.getProviderIdentifier());
        setPrincipal(entity.getUserIdentifier(), entity.getAccountIdentifier());
        OAuthRef oAuthRef = getOAuthDecrypted(entity);

        ScopeInfo scopeInfo = ScopeInfo.builder()
                                  .accountIdentifier(entity.getAccountIdentifier())
                                  .scopeType(ScopeLevel.ACCOUNT)
                                  .uniqueId(entity.getAccountIdentifier())
                                  .build();

        SecretDTOV2 tokenDTO = getSecretSecretValue(entity, oAuthRef.getTokenRef(), scopeInfo);
        SecretDTOV2 refreshTokenDTO = getSecretSecretValue(entity, oAuthRef.getRefreshTokenRef(), scopeInfo);

        if (tokenDTO == null) {
          log.error("Error getting access token secret from Harness");
          return;
        }
        if (refreshTokenDTO == null) {
          log.error("Error getting refresh token secret from Harness");
          return;
        }
        NGEncryptedData tokenDTOEncryptedData = ngEncryptedDataService.get(scopeInfo, tokenDTO.getIdentifier());
        NGEncryptedData refreshTokenDTOEncryptedData =
            ngEncryptedDataService.get(scopeInfo, refreshTokenDTO.getIdentifier());

        SecretManagerConfigDTO secretManager = ngSecretManagerService.getSecretManager(
            scopeInfo, tokenDTOEncryptedData.getSecretManagerIdentifier(), false);
        SecretManagerConfig secretManagerConfig = SecretManagerConfigMapper.fromDTO(secretManager);

        ProviderRefreshTokenResponse providerTaskResponse;

        try {
          providerTaskResponse = executeDelegateSyncTask(entity.getAccountIdentifier(), entity.getProviderIdentifier(),
              tokenDTOEncryptedData, refreshTokenDTOEncryptedData, refreshTokenDTO, secretManagerConfig);
        } catch (Exception e) {
          log.error("Error from delegate while refreshing token", e);
          return;
        }

        log.info("New access token and refresh token saved in secret manager {}", secretManagerConfig.getIdentifier());

        try {
          ngSecretService.update(scopeInfo, tokenDTO.getIdentifier(), tokenDTO,
              providerTaskResponse.getAccessToken().getEncryptionKey(),
              String.valueOf(providerTaskResponse.getAccessToken().getEncryptedValue()));
        } catch (Exception e) {
          log.error("Failed to update to access token", e);
          return;
        }
        try {
          ngSecretService.update(scopeInfo, refreshTokenDTO.getIdentifier(), refreshTokenDTO,
              providerTaskResponse.getRefreshToken().getEncryptionKey(),
              String.valueOf(providerTaskResponse.getRefreshToken().getEncryptedValue()));
        } catch (Exception e) {
          log.error("Access token got updated but failed to update refresh token, thus it is an inconsistent state and "
                  + "cannot be recovered automatically.",
              e);
          return;
        }
        log.info("Successfully updated access and refresh tokens in Harness!");
      } catch (Exception e) {
        log.error("Error in refreshing token ", e);
      }
    }
  }

  @Override
  public OAuthRef getOAuthDecrypted(GitlabOnPremSCM entity) {
    GitlabOauthDTO gitlabOAuthDTO =
        (GitlabOauthDTO) GitlabOnPremSCMMapper.toApiAccessDTO(entity.getApiAccessType(), entity.getGitlabApiAccess())
            .getSpec();
    return OAuthRef.builder()
        .tokenRef(gitlabOAuthDTO.getTokenRef())
        .refreshTokenRef(gitlabOAuthDTO.getRefreshTokenRef())
        .build();
  }

  @Override
  public OAuthConfig getOAuthConfig() {
    return null;
  }

  private SpringFilterExpander getFilterQuery() {
    return query -> {
      Criteria criteria = Criteria.where(UserSourceCodeManagerKeys.type)
                              .is(SCMType.GITLAB_ON_PREM)
                              .and(GitlabOnPremSCMKeys.apiAccessType)
                              .is(GitlabApiAccessType.OAUTH);
      query.addCriteria(criteria);
    };
  }

  private ProviderRefreshTokenResponse executeDelegateSyncTask(String accountIdentifier, String providerIdentifier,
      NGEncryptedData tokenDTOEncryptedData, NGEncryptedData refreshTokenDTOEncryptedData, SecretDTOV2 refreshTokenDTO,
      SecretManagerConfig secretManagerConfig) {
    GetProviderResponseDTO providerResponseDTO = providerService.get(accountIdentifier, providerIdentifier);
    ProviderInfoParams providerInfoParams =
        providerMapper.toProviderInfoParams(providerResponseDTO, refreshTokenDTO.getIdentifier());

    NGAccess ngAccess = BaseNGAccess.builder().accountIdentifier(accountIdentifier).build();
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManagerClientService.getEncryptionDetails(ngAccess, providerInfoParams);

    ProviderRefreshTokenTaskParameters providerTaskParameters =
        ProviderRefreshTokenTaskParameters.builder()
            .providerInfoParams(providerInfoParams)
            .encryptedDataDetails(encryptedDataDetails)
            .callBackUrl(getCallbackUrl(providerResponseDTO.getType()))
            .encryptionConfig(secretManagerConfig)
            .accessToken(tokenDTOEncryptedData)
            .refreshToken(refreshTokenDTOEncryptedData)
            .build();
    return (ProviderRefreshTokenResponse) getResponseData(accountIdentifier, providerTaskParameters);
  }

  private DelegateResponseData getResponseData(
      String accountIdentifier, ProviderRefreshTokenTaskParameters providerRequest) {
    try {
      Map<String, String> owner = getNGTaskSetupAbstractionsWithOwner(accountIdentifier, null, null);
      Map<String, String> abstractions = new HashMap<>(owner);
      final DelegateTaskRequest delegateTaskRequest =
          DelegateTaskRequest.builder()
              .accountId(accountIdentifier)
              .taskType(TaskType.PROVIDER_REFRESH_TOKEN_TASK_NG.name())
              .taskParameters(providerRequest)
              .executionTimeout(Duration.ofSeconds(DELEGATE_TASK_TIMEOUT_IN_SECONDS))
              .taskSetupAbstractions(abstractions)
              .taskSelectors(providerRequest.getProviderInfoParams().getDelegateSelectors())
              .build();
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      log.error("Error occurred while executing delegate task.", ex);
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    } catch (Exception e) {
      log.error("Unexpected error while creating delegate task to save refresh token.", e);
      throw new InternalServerErrorException("Unexpected error while creating delegate task to save refresh token.");
    }
  }

  private String getCallbackUrl(ProviderType type) {
    software.wings.security.authentication.oauth.OAuthConfig oAuthConfig = configuration.getProviderConfig().getOauth();
    if (ProviderType.GITLAB_ON_PREM.equals(type)) {
      return oAuthConfig.getGitlabOnPremConfig().getCallbackUrl();
    } else {
      throw new UnsupportedOperationException(String.format("Unknown Provider type %s", type));
    }
  }
}
