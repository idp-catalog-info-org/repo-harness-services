/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static java.lang.String.format;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGSecretManagerService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.security.NgManagerOpaContextGuard;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.provider.OAuthInfoParams;
import io.harness.provider.ProviderInfoParams;
import io.harness.provider.ProviderService;
import io.harness.provider.ProviderTaskParameters;
import io.harness.provider.ProviderTaskResponse;
import io.harness.provider.dto.GetProviderResponseDTO;
import io.harness.provider.dto.UserDetailsDTO;
import io.harness.provider.entity.ProviderType;
import io.harness.provider.logger.ProviderLogContext;
import io.harness.provider.mapper.ProviderMapper;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.dto.config.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.dto.UserPrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;
import software.wings.security.authentication.oauth.OAuthConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OauthSecretService {
  @Inject SecretCrudService ngSecretService;
  @Inject NgUserService ngUserService;
  @Inject ProviderService providerService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @Inject ProviderMapper providerMapper;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject NextGenConfiguration configuration;
  @Inject NGSecretManagerService ngSecretManagerService;

  private static final int timeoutInSecs = 30;

  String oauthAccessTokenSecretIdentifier = "harnessoauthaccesstoken_%s_%s";
  String oauthRefreshTokenSecretIdentifier = "harnessoauthsecrettoken_%s_%s";
  String oauthAccessTokenSecretName = "Harness-Oauth-access-token-%s";
  String oauthRefreshTokenSecretName = "Harness-Oauth-refresh-token-%s";

  public OauthAccessTokenResponseDTO createSecrets(ScopeInfo scopeInfo, String provider,
      OauthAccessTokenDTO accessToken, String secretManagerIdentifier, boolean isPrivateSecret,
      UserDetailsDTO userDetailsDTO) {
    try (NgManagerOpaContextGuard ignore = new NgManagerOpaContextGuard()) {
      SecretTextSpecDTO accessTokenSecretDTO = SecretTextSpecDTO.builder()
                                                   .secretManagerIdentifier(secretManagerIdentifier)
                                                   .value(accessToken.getAccessToken())
                                                   .valueType(ValueType.Inline)
                                                   .build();
      SecretTextSpecDTO refreshTokenSecretDTO = SecretTextSpecDTO.builder()
                                                    .secretManagerIdentifier(secretManagerIdentifier)
                                                    .value(accessToken.getRefreshToken())
                                                    .valueType(ValueType.Inline)
                                                    .build();
      String randomUUID = UUID.randomUUID().toString();
      SecretDTOV2 accessTokenSecretDTOV2 =
          SecretDTOV2.builder()
              .identifier(format(oauthAccessTokenSecretIdentifier, provider, (new Date()).getTime()))
              .name(format(oauthAccessTokenSecretName, randomUUID))
              .spec(accessTokenSecretDTO)
              .type(SecretType.SecretText)
              .orgIdentifier(scopeInfo.getOrgIdentifier())
              .projectIdentifier(scopeInfo.getProjectIdentifier())
              .build();
      Optional<UserMetadataDTO> userMetadataDTO = Optional.empty();
      if (isPrivateSecret) {
        userMetadataDTO = ngUserService.getUserByEmail(userDetailsDTO.getUserEmail(), false);
        if (!userMetadataDTO.isPresent()) {
          log.error("Failed to get user details for user email: {}", userDetailsDTO.getUserEmail());
          throw new InvalidRequestException(
              String.format("Failed to get user details for user email: %s", userDetailsDTO.getUserEmail()));
        }
        accessTokenSecretDTOV2.setOwner(new UserPrincipal(userMetadataDTO.get().getUuid(),
            userDetailsDTO.getUserEmail(), userMetadataDTO.get().getName(), scopeInfo.getAccountIdentifier()));
      }
      SecretResponseWrapper accessTokenResponse = ngSecretService.create(scopeInfo, accessTokenSecretDTOV2);

      // github doesn't provides refresh token
      if (provider.equals("github")) {
        return OauthAccessTokenResponseDTO.builder()
            .accessTokenRef(accessTokenResponse.getSecret().getIdentifier())
            .build();
      }

      SecretDTOV2 refreshTokenSecretDTOV2 =
          SecretDTOV2.builder()
              .identifier(format(oauthRefreshTokenSecretIdentifier, provider, (new Date()).getTime()))
              .name(format(oauthRefreshTokenSecretName, randomUUID))
              .spec(refreshTokenSecretDTO)
              .type(SecretType.SecretText)
              .orgIdentifier(scopeInfo.getOrgIdentifier())
              .projectIdentifier(scopeInfo.getProjectIdentifier())
              .build();
      if (isPrivateSecret) {
        refreshTokenSecretDTOV2.setOwner(new UserPrincipal(userMetadataDTO.get().getUuid(),
            userDetailsDTO.getUserEmail(), userMetadataDTO.get().getName(), scopeInfo.getAccountIdentifier()));
      }
      SecretResponseWrapper refreshTokenResponse = ngSecretService.create(scopeInfo, refreshTokenSecretDTOV2);
      return OauthAccessTokenResponseDTO.builder()
          .accessTokenRef(accessTokenResponse.getSecret().getIdentifier())
          .refreshTokenRef(refreshTokenResponse.getSecret().getIdentifier())
          .build();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException("Failed to restore security context after OAuth secret create", e);
    }
  }

  public OauthAccessTokenResponseDTO createAccessTokenRequest(
      String accountIdentifier, OAuthInfoParams stateInfo, UserDetailsDTO userDetailsDTO) {
    GetProviderResponseDTO providerInfo = providerService.get(accountIdentifier, stateInfo.getProviderIdentifier());

    try (ProviderLogContext context = new ProviderLogContext(userDetailsDTO, providerInfo)) {
      String secretManagerIdentifier =
          IdentifierRefHelper.getIdentifier(providerInfo.getProviderResponseInfoDTO().getSecretManagerRef());
      SecretManagerConfigDTO secretManager =
          ngSecretManagerService.getSecretManager(ScopeInfo.builder()
                                                      .accountIdentifier(accountIdentifier)
                                                      .uniqueId(accountIdentifier)
                                                      .scopeType(ScopeLevel.ACCOUNT)
                                                      .build(),
              secretManagerIdentifier, false);

      String randomUUID = UUID.randomUUID().toString();
      String accessTokenSecretName = format(oauthAccessTokenSecretName, randomUUID);
      String refreshTokenSecretName = format(oauthRefreshTokenSecretName, randomUUID);

      ProviderTaskParameters providerRequest = buildProviderTaskParameters(
          accountIdentifier, stateInfo, providerInfo, secretManager, accessTokenSecretName, refreshTokenSecretName);
      ProviderTaskResponse providerTaskResponse =
          (ProviderTaskResponse) executeDelegateSyncTask(accountIdentifier, providerRequest);

      verifyRefreshTokenPresence(providerTaskResponse, providerInfo.getType());
      return createHarnessSecrets(accountIdentifier, providerTaskResponse, stateInfo.isPrivateSecret(), userDetailsDTO,
          secretManager, providerInfo.getType(), accessTokenSecretName, refreshTokenSecretName);
    }
  }

  public OAuthInfoParams toStateInfoParams(OAuthCreateTokenAPIRequest stateInfo) {
    return OAuthInfoParams.builder()
        .isPrivateSecret(stateInfo.isPrivateSecret())
        .hostDomain(stateInfo.getHostDomain())
        .providerIdentifier(stateInfo.getProviderIdentifier())
        .code(stateInfo.getCode())
        .build();
  }

  private DelegateResponseData executeDelegateSyncTask(
      String accountIdentifier, ProviderTaskParameters providerRequest) {
    try {
      Map<String, String> owner = getNGTaskSetupAbstractionsWithOwner(accountIdentifier, null, null);
      Map<String, String> abstractions = new HashMap<>(owner);
      final DelegateTaskRequest delegateTaskRequest =
          DelegateTaskRequest.builder()
              .accountId(accountIdentifier)
              .taskType(TaskType.PROVIDER_ACCESS_TOKEN_CREATE_TASK_NG.name())
              .taskParameters(providerRequest)
              .executionTimeout(Duration.ofSeconds(timeoutInSecs))
              .taskSetupAbstractions(abstractions)
              .taskSelectors(providerRequest.getProviderInfoParams().getDelegateSelectors())
              .build();
      return delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      log.error("Error occurred while executing delegate task.", ex);
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    } catch (Exception e) {
      log.error("Unexpected error while creating delegate task to save access token.", e);
      throw new InternalServerErrorException("Unexpected error while creating delegate task to save access token.");
    }
  }

  private ProviderTaskParameters buildProviderTaskParameters(String accountIdentifier, OAuthInfoParams stateInfo,
      GetProviderResponseDTO providerInfo, SecretManagerConfigDTO secretManager, String accessTokenSecretName,
      String refreshTokenSecretName) {
    ProviderInfoParams providerInfoParams = providerMapper.toProviderInfoParams(providerInfo, null);
    NGAccess ngAccess = BaseNGAccess.builder().accountIdentifier(accountIdentifier).build();

    List<EncryptedDataDetail> encryptedDataDetails =
        secretManagerClientService.getEncryptionDetails(ngAccess, providerInfoParams);
    SecretManagerConfig secretManagerConfig = SecretManagerConfigMapper.fromDTO(secretManager);

    return ProviderTaskParameters.builder()
        .stateInfo(stateInfo)
        .providerInfoParams(providerInfoParams)
        .encryptedDataDetails(encryptedDataDetails)
        .callBackUrl(getCallbackUrl(providerInfo.getType()))
        .encryptionConfig(secretManagerConfig)
        .accessTokenSecretName(accessTokenSecretName)
        .refreshTokenSecretName(refreshTokenSecretName)
        .build();
  }

  private String getCallbackUrl(ProviderType type) {
    OAuthConfig oAuthConfig = configuration.getProviderConfig().getOauth();
    return switch (type) {
      case BITBUCKET_SERVER -> oAuthConfig.getBitbucketServerConfig().getCallbackUrl();
      case GITLAB_ON_PREM -> oAuthConfig.getGitlabOnPremConfig().getCallbackUrl();
      case GITHUB_ENTERPRISE -> oAuthConfig.getGithubEnterpriseConfig().getCallbackUrl();
      default -> throw new UnsupportedOperationException(String.format("Unknown Provider type %s", type));
    };
  }

  private OauthAccessTokenResponseDTO createHarnessSecrets(String accountIdentifier,
      ProviderTaskResponse providerTaskResponse, boolean isPrivateSecret, UserDetailsDTO userDetailsDTO,
      SecretManagerConfigDTO secretManagerConfigDTO, ProviderType providerType, String accessTokenSecretName,
      String refreshTokenSecretName) {
    try (NgManagerOpaContextGuard ignore = new NgManagerOpaContextGuard()) {
      ScopeInfo scopeInfo = ScopeInfo.builder()
                                .accountIdentifier(accountIdentifier)
                                .scopeType(ScopeLevel.ACCOUNT)
                                .uniqueId(accountIdentifier)
                                .build();
      String provider = providerType.toString().toLowerCase();

      SecretDTOV2 accessTokenSecretDTOV2 =
          getSecretDTOV2(secretManagerConfigDTO, provider, accessTokenSecretName, oauthAccessTokenSecretIdentifier);
      SecretResponseWrapper accessTokenData;
      SecretDTOV2 refreshTokenSecretDTOV2 =
          getSecretDTOV2(secretManagerConfigDTO, provider, refreshTokenSecretName, oauthRefreshTokenSecretIdentifier);
      SecretResponseWrapper refreshTokenData;
      if (isPrivateSecret) {
        setSecretOwnerForPrivateSecret(userDetailsDTO, scopeInfo, accessTokenSecretDTOV2, refreshTokenSecretDTOV2);
      }

      var responseBuilder = OauthAccessTokenResponseDTO.builder();
      try {
        accessTokenData = ngSecretService.create(scopeInfo, accessTokenSecretDTOV2,
            providerTaskResponse.getAccessToken().getEncryptionKey(),
            String.valueOf(providerTaskResponse.getAccessToken().getEncryptedValue()));
      } catch (Exception e) {
        log.error("Error occurred while saving access token.", e);
        throw new InternalServerErrorException("Error occurred while saving access token.");
      }
      responseBuilder.accessTokenRef(accessTokenData.getSecret().getIdentifier());

      if(providerType.isSupportsRefreshToken()){
        try {
          refreshTokenData = ngSecretService.create(scopeInfo, refreshTokenSecretDTOV2,
                  providerTaskResponse.getRefreshToken().getEncryptionKey(),
                  String.valueOf(providerTaskResponse.getRefreshToken().getEncryptedValue()));
        } catch (Exception e) {
          log.error("Error occurred while saving refresh token.", e);
          throw new InternalServerErrorException("Error occurred while saving refresh token.");
        }
        responseBuilder.refreshTokenRef(refreshTokenData.getSecret().getIdentifier());
      }

      return responseBuilder.build();
    } catch (Exception e) {
      log.error("Error occurred while saving encrypted secret", e);
      throw new InternalServerErrorException("Error occurred while saving encrypted secret");
    }
  }

  @VisibleForTesting
  void verifyRefreshTokenPresence(ProviderTaskResponse providerTaskResponse, ProviderType providerType) {
    boolean isRefreshTokenRecieved = null != providerTaskResponse.getRefreshToken();
    if(providerType.isSupportsRefreshToken() && !isRefreshTokenRecieved){
      throw new InternalServerErrorException(String.format("Error in getting refresh token for provider type %s", providerType));
    } else if(!providerType.isSupportsRefreshToken() && isRefreshTokenRecieved){
      log.error("Refresh token is not supported for provider type {} but recieved", providerType);
    }
  }

  private void setSecretOwnerForPrivateSecret(UserDetailsDTO userDetailsDTO, ScopeInfo scopeInfo,
      SecretDTOV2 accessTokenSecretDTOV2, SecretDTOV2 refreshTokenSecretDTOV2) {
    Optional<UserMetadataDTO> userMetadataDTO = ngUserService.getUserByEmail(userDetailsDTO.getUserEmail(), false);
    if (userMetadataDTO.isEmpty()) {
      log.error("User not found with email: {}", userDetailsDTO.getUserEmail());
      throw new InvalidRequestException(String.format("User not found with email: %s", userDetailsDTO.getUserEmail()));
    }
    setSecretOwner(userDetailsDTO, scopeInfo, accessTokenSecretDTOV2, userMetadataDTO.get());
    setSecretOwner(userDetailsDTO, scopeInfo, refreshTokenSecretDTOV2, userMetadataDTO.get());
  }

  private static void setSecretOwner(UserDetailsDTO userDetailsDTO, ScopeInfo scopeInfo,
      SecretDTOV2 accessTokenSecretDTOV2, UserMetadataDTO userMetadataDTO) {
    accessTokenSecretDTOV2.setOwner(new UserPrincipal(userMetadataDTO.getUuid(), userDetailsDTO.getUserEmail(),
        userMetadataDTO.getName(), scopeInfo.getAccountIdentifier()));
  }

  private SecretDTOV2 getSecretDTOV2(
      SecretManagerConfigDTO secretManagerConfigDTO, String provider, String secretName, String secretIdentifier) {
    SecretTextSpecDTO accessTokenSecretDTO = SecretTextSpecDTO.builder()
                                                 .secretManagerIdentifier(secretManagerConfigDTO.getIdentifier())
                                                 .valueType(ValueType.Inline)
                                                 .build();
    return SecretDTOV2.builder()
        .identifier(format(secretIdentifier, provider, (new Date()).getTime()))
        .name(secretName)
        .spec(accessTokenSecretDTO)
        .type(SecretType.SecretText)
        .build();
  }
}
