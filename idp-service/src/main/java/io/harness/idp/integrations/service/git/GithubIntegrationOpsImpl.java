/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.common.CommonUtils.addAccountScopeInIdentifier;
import static io.harness.idp.common.CommonUtils.readFileFromClassPath;
import static io.harness.idp.common.Constants.INTEGRATIONS_GITHUB_APP_APPLICATION_ID;
import static io.harness.idp.common.Constants.INTEGRATIONS_GITHUB_APP_INSTALLATION_ID;
import static io.harness.idp.common.Constants.INTEGRATIONS_GITHUB_APP_PRIVATE_KEY;
import static io.harness.idp.common.Constants.INTEGRATIONS_GITHUB_TOKEN;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.idp.gitintegration.GitIntegrationDto;
import io.harness.delegate.task.idp.gitintegration.GithubIntegrationDto;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationGithubAppAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.entities.IntegrationEntity.Integration;
import io.harness.idp.integrations.entities.IntegrationEntity.ParentType;
import io.harness.idp.integrations.entities.IntegrationEntity.SubType;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity.AuthMode;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public final class GithubIntegrationOpsImpl extends GitIntegrationOps<GithubIntegrationEntity, GithubConnectorDTO> {
  private static final String SUPPORTED_AUTH_ERROR =
      "Github integration is supported only with token authentication or app authentication";

  @Inject GithubService githubService;

  @Override
  public GithubIntegrationEntity prepare(ConnectorInfoDTO connectorDTO) {
    GithubConnectorDTO githubConnectorDTO = getConnectorConfigDTO(connectorDTO);
    return GithubIntegrationEntity.builder()
        .accountIdentifier(connectorDTO.getAccountIdentifier())
        .identifier(Constants.IDP_PREFIX + connectorDTO.getIdentifier())
        .integration(Integration.GIT)
        .parentType(ParentType.GITHUB)
        .connectorIdentifier(connectorDTO.getIdentifier())
        .host(getHost(githubConnectorDTO))
        .subType(getHost(githubConnectorDTO).equals("github.com") ? SubType.GITHUB_DIRECT : SubType.GITHUB_ENTERPRISE)
        .authMode(validateAndGetAuthMode(githubConnectorDTO))
        .executeOnDelegate(githubConnectorDTO.getExecuteOnDelegate())
        .delegateSelectors(getDelegateSelectors(githubConnectorDTO))
        .auth(getAuth(githubConnectorDTO, connectorDTO.getAccountIdentifier()))
        .additionalIndexer(getHost(githubConnectorDTO))
        .build();
  }

  @Override
  public GithubConnectorDTO getConnectorConfigDTO(ConnectorInfoDTO connectorInfoDTO) {
    return (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
  }

  @Override
  public String getHost(GithubConnectorDTO githubConnectorDTO) {
    return getDomainFromUrl(githubConnectorDTO.getUrl());
  }

  @Override
  public AuthMode validateAndGetAuthMode(GithubConnectorDTO githubConnectorDTO) {
    GithubHttpCredentialsDTO credentials = githubHttpCredentialsDTO(githubConnectorDTO);
    GithubHttpAuthenticationType type = credentials.getType();
    if (type != GithubHttpAuthenticationType.USERNAME_AND_TOKEN && type != GithubHttpAuthenticationType.GITHUB_APP) {
      throw new InvalidRequestException(SUPPORTED_AUTH_ERROR);
    }
    return type.equals(GithubHttpAuthenticationType.USERNAME_AND_TOKEN) ? AuthMode.TOKEN : AuthMode.GITHUB_APP;
  }

  @Override
  public GitIntegrationAuth getAuth(GithubConnectorDTO githubConnectorDTO, String accountIdentifier) {
    GithubHttpCredentialsDTO credentials = githubHttpCredentialsDTO(githubConnectorDTO);
    GithubHttpAuthenticationType type = credentials.getType();
    if (type.equals(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)) {
      GithubUsernameTokenDTO httpCredentialsSpec = (GithubUsernameTokenDTO) credentials.getHttpCredentialsSpec();
      GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
      gitIntegrationTokenAuth.setTokenSecretIdentifier(httpCredentialsSpec.getTokenRef().getIdentifier());
      return gitIntegrationTokenAuth;
    }
    if (type.equals(GithubHttpAuthenticationType.GITHUB_APP)) {
      return getGitIntegrationGithubAppAuth(credentials);
    }
    throw new InvalidRequestException(SUPPORTED_AUTH_ERROR);
  }

  @Override
  Map<String, String> getIntegrationConfigs(GithubIntegrationEntity githubIntegrationEntity) {
    Map<String, String> integrationConfigs = new HashMap<>();
    AuthMode authMode = githubIntegrationEntity.getAuthMode();
    if (authMode.equals(AuthMode.GITHUB_APP)) {
      GitIntegrationGithubAppAuth gitIntegrationGithubAppAuth =
          (GitIntegrationGithubAppAuth) githubIntegrationEntity.getAuth();
      if (isNotEmpty(gitIntegrationGithubAppAuth.getApplicationId())) {
        integrationConfigs.put(
            INTEGRATIONS_GITHUB_APP_APPLICATION_ID + "_" + sanitizeHost(githubIntegrationEntity.getHost()),
            gitIntegrationGithubAppAuth.getApplicationId());
      }
      if (isNotEmpty(gitIntegrationGithubAppAuth.getInstallationId())) {
        integrationConfigs.put(
            INTEGRATIONS_GITHUB_APP_INSTALLATION_ID + "_" + sanitizeHost(githubIntegrationEntity.getHost()),
            gitIntegrationGithubAppAuth.getInstallationId());
      }
    }
    return integrationConfigs;
  }

  @Override
  public Map<String, String> getIntegrationSecrets(GithubIntegrationEntity githubIntegrationEntity) {
    Map<String, String> integrationSecrets = new HashMap<>();
    AuthMode authMode = githubIntegrationEntity.getAuthMode();
    if (authMode.equals(AuthMode.TOKEN)) {
      GitIntegrationTokenAuth gitIntegrationTokenAuth = (GitIntegrationTokenAuth) githubIntegrationEntity.getAuth();
      integrationSecrets.put(INTEGRATIONS_GITHUB_TOKEN + "_" + sanitizeHost(githubIntegrationEntity.getHost()),
          String.valueOf(gitIntegrationTokenAuth.getTokenSecretIdentifier()));
    }
    if (authMode.equals(AuthMode.GITHUB_APP)) {
      GitIntegrationGithubAppAuth gitIntegrationGithubAppAuth =
          (GitIntegrationGithubAppAuth) githubIntegrationEntity.getAuth();
      if (isEmpty(gitIntegrationGithubAppAuth.getApplicationId())) {
        integrationSecrets.put(
            INTEGRATIONS_GITHUB_APP_APPLICATION_ID + "_" + sanitizeHost(githubIntegrationEntity.getHost()),
            String.valueOf(gitIntegrationGithubAppAuth.getApplicationIdSecretIdentifier()));
      }
      if (isEmpty(gitIntegrationGithubAppAuth.getInstallationId())) {
        integrationSecrets.put(
            INTEGRATIONS_GITHUB_APP_INSTALLATION_ID + "_" + sanitizeHost(githubIntegrationEntity.getHost()),
            String.valueOf(gitIntegrationGithubAppAuth.getInstallationIdSecretIdentifier()));
      }
      integrationSecrets.put(
          INTEGRATIONS_GITHUB_APP_PRIVATE_KEY + "_" + sanitizeHost(githubIntegrationEntity.getHost()),
          String.valueOf(gitIntegrationGithubAppAuth.getPrivateKeySecretIdentifier()));
    }
    return integrationSecrets;
  }

  @Override
  public Set<String> getDelegateSelectors(GithubConnectorDTO githubConnectorDTO) {
    return githubConnectorDTO.getDelegateSelectors();
  }

  @Override
  public String getIntegrationAppConfig(
      GithubIntegrationEntity githubIntegrationEntity, GithubConnectorDTO githubConnectorDTO) {
    String integrationConfig = githubIntegrationEntity.getAuthMode().equals(AuthMode.GITHUB_APP)
        ? readFileFromClassPath("integrations/git/github-app.yaml")
        : readFileFromClassPath("integrations/git/github.yaml");
    String host = getHost(githubConnectorDTO);
    integrationConfig = integrationConfig.replace("${HOST}", host);
    integrationConfig = integrationConfig.replace("${API_BASE_URL}", getGithubApiBaseUrlFromHost(host));
    integrationConfig = integrationConfig.replace("${GITHUB_TOKEN}", "${GITHUB_TOKEN_" + sanitizeHost(host) + "}");
    integrationConfig = integrationConfig.replace(
        "${GITHUB_APP_APPLICATION_ID}", "${GITHUB_APP_APPLICATION_ID_" + sanitizeHost(host) + "}");
    integrationConfig =
        integrationConfig.replace("${GITHUB_APP_PRIVATE_KEY}", "${GITHUB_APP_PRIVATE_KEY_" + sanitizeHost(host) + "}");
    return integrationConfig;
  }

  @Override
  String getRepoUrl(GithubConnectorDTO githubConnectorDTO) {
    return githubConnectorDTO.getUrl();
  }

  @Override
  String getGitConnectionType(GithubConnectorDTO connectorConfigDTO) {
    return connectorConfigDTO.getConnectionTypeForGit().toString();
  }

  @Override
  void validateReadPermission(String accountIdentifier, GithubConnectorDTO githubConnectorDTO,
      GithubIntegrationEntity githubIntegrationEntity, Map<String, String> configsForGitIntegration,
      Map<String, String> secretsForGitIntegration) {
    if (githubIntegrationEntity.getReadPermissionValidation() != null) {
      validateReadPermissionForUrl(accountIdentifier, githubConnectorDTO, githubIntegrationEntity,
          githubIntegrationEntity.getReadPermissionValidation().getFileUrl());
    }
  }

  @Override
  void validateReadPermissionForUrl(String accountIdentifier, GithubConnectorDTO githubConnectorDTO,
      GithubIntegrationEntity githubIntegrationEntity, String urlForValidation) {
    String[] pathParts = getPathParts(urlForValidation);
    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation;
    if (githubIntegrationEntity.isExecuteOnDelegate()) {
      GitIntegrationDto gitIntegrationDto = GithubIntegrationDto.builder()
                                                .url(getGithubApiBaseUrlFromHost(githubIntegrationEntity.getHost()))
                                                .owner(pathParts[1])
                                                .repo(pathParts[2])
                                                .build();
      readPermissionValidation = validateViaDelegateAndFrameReadPermissionValidation(
          githubIntegrationEntity, gitIntegrationDto, urlForValidation);
    } else {
      String token = getToken(accountIdentifier, githubIntegrationEntity);
      JSONObject response = githubService.getRepository(
          GithubAppConfig.builder().githubUrl(getGithubApiBaseUrlFromHost(githubIntegrationEntity.getHost())).build(),
          token, pathParts[1], pathParts[2]);
      readPermissionValidation = frameReadPermissionValidation(urlForValidation, response);
    }
    githubIntegrationEntity.setReadPermissionValidation(readPermissionValidation);
  }

  @Override
  protected String getRepository(GithubConnectorDTO githubConnectorDTO, String repository) {
    return GitClientHelper.getGitRepo(repository);
  }

  @Override
  String getAlreadyExistErrorMessage(GithubIntegrationEntity gitIntegrationEntity) {
    return "GitHub integration with host " + gitIntegrationEntity.getHost() + " already exists. ";
  }

  @Override
  List<String> getAdditionalHosts(ConnectorInfoDTO connectorDTO) {
    return List.of();
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(
      GithubIntegrationEntity githubIntegrationEntity, List<HttpHeaderConfig> headers) {
    if (githubIntegrationEntity.getAuth() instanceof GitIntegrationGithubAppAuth) {
      removeHeadersForDelegateTask(headers);
      GitIntegrationGithubAppAuth auth = (GitIntegrationGithubAppAuth) githubIntegrationEntity.getAuth();
      return GithubAppDTO.builder()
          .applicationId(isEmpty(auth.getApplicationId()) ? null : auth.getApplicationId())
          .installationId(isEmpty(auth.getInstallationId()) ? null : auth.getInstallationId())
          .applicationIdRef(isEmpty(auth.getApplicationIdSecretIdentifier())
                  ? null
                  : new SecretRefData(addAccountScopeInIdentifier(auth.getApplicationIdSecretIdentifier())))
          .installationIdRef(isEmpty(auth.getInstallationIdSecretIdentifier())
                  ? null
                  : new SecretRefData(addAccountScopeInIdentifier(auth.getInstallationIdSecretIdentifier())))
          .privateKeyRef(isEmpty(auth.getPrivateKeySecretIdentifier())
                  ? null
                  : new SecretRefData(addAccountScopeInIdentifier(auth.getPrivateKeySecretIdentifier())))
          .build();
    }
    return null;
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(ConnectorInfoDTO connectorInfoDTO) {
    GithubHttpCredentialsSpecDTO authentication = null;
    GitIntegrationAuth gitIntegrationAuth = getApiAuthForScorecards(getConnectorConfigDTO(connectorInfoDTO));
    if (gitIntegrationAuth instanceof GitIntegrationTokenAuth gitIntegrationTokenAuth) {
      String[] secretRefSplit = gitIntegrationTokenAuth.getTokenSecretIdentifier().split("[.]");
      String secretOrgIdentifier = null;
      String secretProjectIdentifier = null;
      if (secretRefSplit.length == 2 && secretRefSplit[0].equals("org")) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
      }
      if (secretRefSplit.length == 1) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
        secretProjectIdentifier = connectorInfoDTO.getProjectIdentifier();
      }

      authentication = GithubUsernameTokenDTO.builder()
                           .tokenRef(new SecretRefData(isEmpty(secretOrgIdentifier) && isEmpty(secretProjectIdentifier)
                                   ? addAccountScopeInIdentifier(gitIntegrationTokenAuth.getTokenSecretIdentifier())
                                   : gitIntegrationTokenAuth.getTokenSecretIdentifier()))
                           .build();
    }
    if (gitIntegrationAuth instanceof GitIntegrationGithubAppAuth gitIntegrationGithubAppAuth) {
      String[] secretRefSplit = gitIntegrationGithubAppAuth.getPrivateKeySecretIdentifier().split("[.]");
      String secretOrgIdentifier = null;
      String secretProjectIdentifier = null;
      if (secretRefSplit.length == 2 && secretRefSplit[0].equals("org")) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
      }
      if (secretRefSplit.length == 1) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
        secretProjectIdentifier = connectorInfoDTO.getProjectIdentifier();
      }

      GithubAppDTO githubAppDTO =
          GithubAppDTO.builder()
              .applicationId(gitIntegrationGithubAppAuth.getApplicationId())
              .installationId(gitIntegrationGithubAppAuth.getInstallationId())
              .privateKeyRef(new SecretRefData(isEmpty(secretOrgIdentifier) && isEmpty(secretProjectIdentifier)
                      ? addAccountScopeInIdentifier(gitIntegrationGithubAppAuth.getPrivateKeySecretIdentifier())
                      : gitIntegrationGithubAppAuth.getPrivateKeySecretIdentifier()))
              .build();

      secretRefSplit = gitIntegrationGithubAppAuth.getInstallationIdSecretIdentifier().split("[.]");
      secretOrgIdentifier = null;
      secretProjectIdentifier = null;
      if (secretRefSplit.length == 2 && secretRefSplit[0].equals("org")) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
      }
      if (secretRefSplit.length == 1) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
        secretProjectIdentifier = connectorInfoDTO.getProjectIdentifier();
      }

      githubAppDTO.setInstallationIdRef(isEmpty(gitIntegrationGithubAppAuth.getInstallationId())
              ? new SecretRefData(isEmpty(secretOrgIdentifier) && isEmpty(secretProjectIdentifier)
                        ? addAccountScopeInIdentifier(gitIntegrationGithubAppAuth.getInstallationIdSecretIdentifier())
                        : gitIntegrationGithubAppAuth.getInstallationIdSecretIdentifier())
              : null);

      secretRefSplit = gitIntegrationGithubAppAuth.getApplicationIdSecretIdentifier().split("[.]");
      secretOrgIdentifier = null;
      secretProjectIdentifier = null;
      if (secretRefSplit.length == 2 && secretRefSplit[0].equals("org")) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
      }
      if (secretRefSplit.length == 1) {
        secretOrgIdentifier = connectorInfoDTO.getOrgIdentifier();
        secretProjectIdentifier = connectorInfoDTO.getProjectIdentifier();
      }

      githubAppDTO.setApplicationIdRef(isEmpty(gitIntegrationGithubAppAuth.getApplicationId())
              ? new SecretRefData(isEmpty(secretOrgIdentifier) && isEmpty(secretProjectIdentifier)
                        ? addAccountScopeInIdentifier(gitIntegrationGithubAppAuth.getApplicationIdSecretIdentifier())
                        : gitIntegrationGithubAppAuth.getApplicationIdSecretIdentifier())
              : null);
      authentication = githubAppDTO;
    }
    return authentication;
  }

  @Override
  protected DecryptableEntity getAuthForDelegateRequest(GitIntegrationEntity gitIntegrationEntity) {
    GithubIntegrationEntity githubIntegrationEntity = (GithubIntegrationEntity) gitIntegrationEntity;
    GithubHttpCredentialsSpecDTO authentication = null;
    if (githubIntegrationEntity.getAuthMode().equals(AuthMode.GITHUB_APP)) {
      GitIntegrationGithubAppAuth auth = (GitIntegrationGithubAppAuth) githubIntegrationEntity.getAuth();
      GithubAppDTO githubAppDTO =
          GithubAppDTO.builder()
              .applicationId(auth.getApplicationId())
              .installationId(auth.getInstallationId())
              .privateKeyRef(new SecretRefData(addAccountScopeInIdentifier(auth.getPrivateKeySecretIdentifier())))
              .build();
      githubAppDTO.setInstallationIdRef(isEmpty(auth.getInstallationId())
              ? new SecretRefData(addAccountScopeInIdentifier(auth.getInstallationIdSecretIdentifier()))
              : null);
      githubAppDTO.setApplicationIdRef(isEmpty(auth.getApplicationId())
              ? new SecretRefData(addAccountScopeInIdentifier(auth.getApplicationIdSecretIdentifier()))
              : null);
      authentication = githubAppDTO;
    } else if (githubIntegrationEntity.getAuthMode().equals(AuthMode.TOKEN)) {
      GitIntegrationTokenAuth auth = (GitIntegrationTokenAuth) githubIntegrationEntity.getAuth();
      authentication = GithubUsernameTokenDTO.builder()
                           .tokenRef(new SecretRefData(addAccountScopeInIdentifier(auth.getTokenSecretIdentifier())))
                           .build();
    }
    return authentication;
  }

  public GitIntegrationAuth getApiAuthForScorecards(GithubConnectorDTO githubConnectorDTO) {
    GithubApiAccessSpecDTO githubApiAccessSpecDTO = githubTokenApiAccessDTO(githubConnectorDTO);
    if (githubApiAccessSpecDTO == null) {
      return null;
    }

    if (githubApiAccessSpecDTO instanceof GithubTokenSpecDTO githubTokenSpecDTO) {
      GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
      gitIntegrationTokenAuth.setTokenSecretIdentifier(githubTokenSpecDTO.getTokenRef().getIdentifier());
      return gitIntegrationTokenAuth;
    }

    if (githubApiAccessSpecDTO instanceof GithubAppSpecDTO githubAppSpecDTO) {
      GitIntegrationGithubAppAuth gitIntegrationGithubAppAuth = new GitIntegrationGithubAppAuth();
      gitIntegrationGithubAppAuth.setApplicationId(githubAppSpecDTO.getApplicationId());
      gitIntegrationGithubAppAuth.setApplicationIdSecretIdentifier(
          githubAppSpecDTO.getApplicationIdRef().getIdentifier());
      gitIntegrationGithubAppAuth.setInstallationId(githubAppSpecDTO.getInstallationId());
      gitIntegrationGithubAppAuth.setInstallationIdSecretIdentifier(
          githubAppSpecDTO.getInstallationIdRef().getIdentifier());
      gitIntegrationGithubAppAuth.setPrivateKeySecretIdentifier(githubAppSpecDTO.getPrivateKeyRef().getIdentifier());
      return gitIntegrationGithubAppAuth;
    }

    return null;
  }

  private static GitIntegrationGithubAppAuth getGitIntegrationGithubAppAuth(GithubHttpCredentialsDTO credentials) {
    GithubAppDTO httpCredentialsSpec = (GithubAppDTO) credentials.getHttpCredentialsSpec();
    GitIntegrationGithubAppAuth gitIntegrationTokenAuth = new GitIntegrationGithubAppAuth();
    gitIntegrationTokenAuth.setApplicationId(httpCredentialsSpec.getApplicationId());
    if (httpCredentialsSpec.getApplicationIdRef() != null) {
      gitIntegrationTokenAuth.setApplicationIdSecretIdentifier(
          httpCredentialsSpec.getApplicationIdRef().getIdentifier());
    }
    gitIntegrationTokenAuth.setInstallationId(httpCredentialsSpec.getInstallationId());
    if (httpCredentialsSpec.getInstallationIdRef() != null) {
      gitIntegrationTokenAuth.setInstallationIdSecretIdentifier(
          httpCredentialsSpec.getInstallationIdRef().getIdentifier());
    }
    gitIntegrationTokenAuth.setPrivateKeySecretIdentifier(httpCredentialsSpec.getPrivateKeyRef().getIdentifier());
    return gitIntegrationTokenAuth;
  }

  private GithubHttpCredentialsDTO githubHttpCredentialsDTO(GithubConnectorDTO githubConnectorDTO) {
    GithubAuthenticationDTO authentication = githubConnectorDTO.getAuthentication();
    GitAuthType authType = authentication.getAuthType();
    if (authType != GitAuthType.HTTP) {
      throw new InvalidRequestException("Github integration is supported only with HTTP authentication");
    }
    return (GithubHttpCredentialsDTO) authentication.getCredentials();
  }

  public String getGithubApiBaseUrlFromHost(String host) {
    return (host.equals("github.com")) ? String.format("https://api.%s", host)
                                       : String.format("https://%s/api/v3", host);
  }

  private String getToken(String accountIdentifier, GithubIntegrationEntity githubIntegrationEntity) {
    String token;
    if (githubIntegrationEntity.getAuthMode().equals(AuthMode.TOKEN)) {
      GitIntegrationTokenAuth auth = (GitIntegrationTokenAuth) githubIntegrationEntity.getAuth();
      token = getDecryptedValue(accountIdentifier, auth.getTokenSecretIdentifier());
    } else {
      GitIntegrationGithubAppAuth auth = (GitIntegrationGithubAppAuth) githubIntegrationEntity.getAuth();
      String applicationId = isEmpty(auth.getApplicationId())
          ? getDecryptedValue(accountIdentifier, auth.getApplicationIdSecretIdentifier())
          : auth.getApplicationId();
      String installationId = isEmpty(auth.getInstallationId())
          ? getDecryptedValue(accountIdentifier, auth.getInstallationIdSecretIdentifier())
          : auth.getInstallationId();
      token = githubService.getToken(
          GithubAppConfig.builder()
              .githubUrl(getGithubApiBaseUrlFromHost(githubIntegrationEntity.getHost()))
              .appId(applicationId)
              .installationId(installationId)
              .privateKey(getDecryptedValueGithubAppPrivateKey(accountIdentifier, auth.getPrivateKeySecretIdentifier()))
              .build());
    }
    return token;
  }

  private GithubApiAccessSpecDTO githubTokenApiAccessDTO(GithubConnectorDTO githubConnectorDTO) {
    GithubApiAccessDTO apiAccess = githubConnectorDTO.getApiAccess();
    if (apiAccess == null) {
      return null;
    }

    GithubApiAccessType type = apiAccess.getType();
    if (type != GithubApiAccessType.TOKEN && type != GithubApiAccessType.GITHUB_APP) {
      log.warn("Github integration is supported only with Token or App authentication for api access");
      return null;
    }

    return apiAccess.getSpec();
  }
}
