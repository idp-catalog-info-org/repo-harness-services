/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.addAccountScopeInIdentifier;
import static io.harness.idp.common.CommonUtils.readFileFromClassPath;
import static io.harness.idp.common.Constants.INTEGRATIONS_GITLAB_TOKEN;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.cistatus.service.gitlab.GitlabConfig;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.delegate.task.idp.gitintegration.GitIntegrationDto;
import io.harness.delegate.task.idp.gitintegration.GitlabIntegrationDto;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.entities.IntegrationEntity.Integration;
import io.harness.idp.integrations.entities.IntegrationEntity.ParentType;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity.AuthMode;
import io.harness.idp.integrations.entities.git.GitlabIntegrationEntity;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public final class GitlabIntegrationOpsImpl extends GitIntegrationOps<GitlabIntegrationEntity, GitlabConnectorDTO> {
  @Inject GitlabService gitlabService;

  @Override
  public GitlabIntegrationEntity prepare(ConnectorInfoDTO connectorDTO) {
    GitlabConnectorDTO gitlabConnectorDTO = getConnectorConfigDTO(connectorDTO);
    return GitlabIntegrationEntity.builder()
        .accountIdentifier(connectorDTO.getAccountIdentifier())
        .identifier(Constants.IDP_PREFIX + connectorDTO.getIdentifier())
        .integration(Integration.GIT)
        .parentType(ParentType.GITLAB)
        .connectorIdentifier(connectorDTO.getIdentifier())
        .host(getHost(gitlabConnectorDTO))
        .authMode(validateAndGetAuthMode(gitlabConnectorDTO))
        .executeOnDelegate(gitlabConnectorDTO.getExecuteOnDelegate())
        .delegateSelectors(getDelegateSelectors(gitlabConnectorDTO))
        .auth((GitIntegrationTokenAuth) getAuth(gitlabConnectorDTO, connectorDTO.getAccountIdentifier()))
        .additionalIndexer(getHost(gitlabConnectorDTO))
        .build();
  }

  @Override
  public GitlabConnectorDTO getConnectorConfigDTO(ConnectorInfoDTO connectorInfoDTO) {
    return (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();
  }

  @Override
  public String getHost(GitlabConnectorDTO gitlabConnectorDTO) {
    return getDomainFromUrl(gitlabConnectorDTO.getUrl());
  }

  @Override
  public AuthMode validateAndGetAuthMode(GitlabConnectorDTO gitlabConnectorDTO) {
    GitlabHttpCredentialsDTO credentials = gitlabHttpCredentialsDTO(gitlabConnectorDTO);
    GitlabHttpAuthenticationType type = credentials.getType();
    if (type != GitlabHttpAuthenticationType.USERNAME_AND_TOKEN) {
      throw new InvalidRequestException("Gitlab integration is supported only with token authentication");
    }
    return AuthMode.TOKEN;
  }

  @Override
  public GitIntegrationAuth getAuth(GitlabConnectorDTO gitlabConnectorDTO, String accountIdentifier) {
    GitlabHttpCredentialsDTO credentials = gitlabHttpCredentialsDTO(gitlabConnectorDTO);
    GitlabUsernameTokenDTO httpCredentialsSpec = (GitlabUsernameTokenDTO) credentials.getHttpCredentialsSpec();
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier(httpCredentialsSpec.getTokenRef().getIdentifier());
    return gitIntegrationTokenAuth;
  }

  @Override
  Map<String, String> getIntegrationConfigs(GitlabIntegrationEntity gitlabIntegrationEntity) {
    return Map.of();
  }

  @Override
  public Map<String, String> getIntegrationSecrets(GitlabIntegrationEntity gitlabIntegrationEntity) {
    Map<String, String> integrationSecrets = new HashMap<>();
    GitIntegrationTokenAuth gitIntegrationTokenAuth = gitlabIntegrationEntity.getAuth();
    integrationSecrets.put(INTEGRATIONS_GITLAB_TOKEN + "_" + sanitizeHost(gitlabIntegrationEntity.getHost()),
        String.valueOf(gitIntegrationTokenAuth.getTokenSecretIdentifier()));
    return integrationSecrets;
  }

  @Override
  public Set<String> getDelegateSelectors(GitlabConnectorDTO gitlabConnectorDTO) {
    return gitlabConnectorDTO.getDelegateSelectors();
  }

  @Override
  public String getIntegrationAppConfig(
      GitlabIntegrationEntity gitlabIntegrationEntity, GitlabConnectorDTO gitlabConnectorDTO) {
    String integrationConfig = readFileFromClassPath("integrations/git/gitlab.yaml");
    integrationConfig = integrationConfig.replace("${HOST}", getHost(gitlabConnectorDTO));
    integrationConfig = integrationConfig.replace(
        "${GITLAB_TOKEN}", "${GITLAB_TOKEN_" + sanitizeHost(getHost(gitlabConnectorDTO)) + "}");
    return integrationConfig;
  }

  @Override
  String getRepoUrl(GitlabConnectorDTO gitlabConnectorDTO) {
    return gitlabConnectorDTO.getUrl();
  }

  @Override
  String getGitConnectionType(GitlabConnectorDTO gitlabConnectorDTO) {
    return gitlabConnectorDTO.getConnectionTypeForGit().toString();
  }

  @Override
  void validateReadPermission(String accountIdentifier, GitlabConnectorDTO gitlabConnectorDTO,
      GitlabIntegrationEntity gitlabIntegrationEntity, Map<String, String> configsForGitIntegration,
      Map<String, String> secretsForGitIntegration) {
    if (gitlabIntegrationEntity.getReadPermissionValidation() != null) {
      validateReadPermissionForUrl(accountIdentifier, gitlabConnectorDTO, gitlabIntegrationEntity,
          gitlabIntegrationEntity.getReadPermissionValidation().getFileUrl());
    }
  }

  @Override
  void validateReadPermissionForUrl(String accountIdentifier, GitlabConnectorDTO gitlabConnectorDTO,
      GitlabIntegrationEntity gitlabIntegrationEntity, String urlForValidation) {
    String[] pathParts = getPathParts(urlForValidation);
    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation;
    if (gitlabIntegrationEntity.isExecuteOnDelegate()) {
      GitIntegrationDto gitIntegrationDto = GitlabIntegrationDto.builder()
                                                .url("https://" + gitlabIntegrationEntity.getHost())
                                                .userProject(pathParts[1] + "/" + pathParts[2])
                                                .build();
      readPermissionValidation = validateViaDelegateAndFrameReadPermissionValidation(
          gitlabIntegrationEntity, gitIntegrationDto, urlForValidation);
    } else {
      String token = getDecryptedValue(accountIdentifier, gitlabIntegrationEntity.getAuth().getTokenSecretIdentifier());
      JSONObject response =
          gitlabService.getSingleProjectForUser(GitlabConfig.builder()
                                                    .gitlabUrl("https://" + gitlabIntegrationEntity.getHost())
                                                    .personalAccessToken(token)
                                                    .build(),
              pathParts[1] + "/" + pathParts[2]);
      readPermissionValidation = frameReadPermissionValidation(urlForValidation, response);
    }
    gitlabIntegrationEntity.setReadPermissionValidation(readPermissionValidation);
  }

  @Override
  protected String getRepository(GitlabConnectorDTO gitlabConnectorDTO, String repository) {
    return GitClientHelper.getGitRepo(repository);
  }

  @Override
  public String getAlreadyExistErrorMessage(GitlabIntegrationEntity gitIntegrationEntity) {
    return "GitLab integration with host " + gitIntegrationEntity.getHost() + " already exists. ";
  }

  @Override
  List<String> getAdditionalHosts(ConnectorInfoDTO connectorDTO) {
    return List.of();
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(
      GitlabIntegrationEntity gitIntegrationEntity, List<HttpHeaderConfig> headers) {
    return null;
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(ConnectorInfoDTO connectorInfoDTO) {
    GitlabUsernameTokenDTO authentication = null;
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

      authentication = GitlabUsernameTokenDTO.builder()
                           .tokenRef(new SecretRefData(isEmpty(secretOrgIdentifier) && isEmpty(secretProjectIdentifier)
                                   ? addAccountScopeInIdentifier(gitIntegrationTokenAuth.getTokenSecretIdentifier())
                                   : gitIntegrationTokenAuth.getTokenSecretIdentifier()))
                           .build();
    }
    return authentication;
  }

  @Override
  protected DecryptableEntity getAuthForDelegateRequest(GitIntegrationEntity gitIntegrationEntity) {
    GitlabIntegrationEntity gitlabIntegrationEntity = (GitlabIntegrationEntity) gitIntegrationEntity;
    return GitlabUsernameTokenDTO.builder()
        .tokenRef(new SecretRefData(
            addAccountScopeInIdentifier(gitlabIntegrationEntity.getAuth().getTokenSecretIdentifier())))
        .build();
  }

  public GitIntegrationAuth getApiAuthForScorecards(GitlabConnectorDTO gitlabConnectorDTO) {
    GitlabTokenSpecDTO gitlabTokenSpecDTO = gitlabTokenApiAccessDTO(gitlabConnectorDTO);
    if (gitlabTokenSpecDTO == null) {
      return null;
    }

    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier(gitlabTokenSpecDTO.getTokenRef().getIdentifier());

    return gitIntegrationTokenAuth;
  }

  private GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO(GitlabConnectorDTO gitlabConnectorDTO) {
    GitlabAuthenticationDTO authentication = gitlabConnectorDTO.getAuthentication();
    GitAuthType authType = authentication.getAuthType();
    if (authType != GitAuthType.HTTP) {
      throw new InvalidRequestException("Gitlab integration is supported only with HTTP authentication");
    }
    return (GitlabHttpCredentialsDTO) authentication.getCredentials();
  }

  private GitlabTokenSpecDTO gitlabTokenApiAccessDTO(GitlabConnectorDTO gitlabConnectorDTO) {
    GitlabApiAccessDTO apiAccess = gitlabConnectorDTO.getApiAccess();
    if (apiAccess == null) {
      return null;
    }

    GitlabApiAccessType type = apiAccess.getType();
    if (type != GitlabApiAccessType.TOKEN) {
      log.warn("Gitlab integration is supported only with Token authentication for api access");
      return null;
    }

    return (GitlabTokenSpecDTO) apiAccess.getSpec();
  }
}
