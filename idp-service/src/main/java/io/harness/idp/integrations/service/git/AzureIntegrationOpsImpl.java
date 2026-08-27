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
import static io.harness.idp.common.Constants.INTEGRATIONS_AZURE_PERSONAL_ACCESS_TOKEN;
import static io.harness.idp.common.Constants.SLASH_DELIMITER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.cistatus.service.azurerepo.AzureRepoConfig;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.task.idp.gitintegration.AzureIntegrationDto;
import io.harness.delegate.task.idp.gitintegration.GitIntegrationDto;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.git.GitClientHelper;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.entities.IntegrationEntity.Integration;
import io.harness.idp.integrations.entities.IntegrationEntity.ParentType;
import io.harness.idp.integrations.entities.git.AzureIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity.AuthMode;

import com.google.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public final class AzureIntegrationOpsImpl extends GitIntegrationOps<AzureIntegrationEntity, AzureRepoConnectorDTO> {
  @Inject AzureRepoService azureRepoService;

  @Override
  public AzureIntegrationEntity prepare(ConnectorInfoDTO connectorDTO) {
    AzureRepoConnectorDTO azureRepoConnectorDTO = getConnectorConfigDTO(connectorDTO);
    return AzureIntegrationEntity.builder()
        .accountIdentifier(connectorDTO.getAccountIdentifier())
        .identifier(Constants.IDP_PREFIX + connectorDTO.getIdentifier())
        .integration(Integration.GIT)
        .parentType(ParentType.AZURE)
        .additionalIndexer(getOrganization(azureRepoConnectorDTO))
        .connectorIdentifier(connectorDTO.getIdentifier())
        .host(getHost(azureRepoConnectorDTO))
        .authMode(validateAndGetAuthMode(azureRepoConnectorDTO))
        .executeOnDelegate(azureRepoConnectorDTO.getExecuteOnDelegate())
        .delegateSelectors(getDelegateSelectors(azureRepoConnectorDTO))
        .organization(getOrganization(azureRepoConnectorDTO))
        .auth((GitIntegrationTokenAuth) getAuth(azureRepoConnectorDTO, connectorDTO.getAccountIdentifier()))
        .build();
  }

  @Override
  public AzureRepoConnectorDTO getConnectorConfigDTO(ConnectorInfoDTO connectorInfoDTO) {
    return (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();
  }

  @Override
  public String getHost(AzureRepoConnectorDTO azureRepoConnectorDTO) {
    return "dev.azure.com";
  }

  @Override
  public AuthMode validateAndGetAuthMode(AzureRepoConnectorDTO azureRepoConnectorDTO) {
    AzureRepoHttpCredentialsDTO credentials = azureRepoHttpCredentialsDTO(azureRepoConnectorDTO);
    AzureRepoHttpAuthenticationType type = credentials.getType();
    if (type != AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN) {
      throw new InvalidRequestException("Azure integration is supported only with token authentication");
    }
    return AuthMode.TOKEN;
  }

  @Override
  public GitIntegrationAuth getAuth(AzureRepoConnectorDTO azureRepoConnectorDTO, String accountIdentifier) {
    AzureRepoHttpCredentialsDTO credentials = azureRepoHttpCredentialsDTO(azureRepoConnectorDTO);
    AzureRepoUsernameTokenDTO httpCredentialsSpec = (AzureRepoUsernameTokenDTO) credentials.getHttpCredentialsSpec();
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier(httpCredentialsSpec.getTokenRef().getIdentifier());
    return gitIntegrationTokenAuth;
  }

  @Override
  Map<String, String> getIntegrationConfigs(AzureIntegrationEntity azureIntegrationEntity) {
    return Map.of();
  }

  @Override
  public Map<String, String> getIntegrationSecrets(AzureIntegrationEntity azureIntegrationEntity) {
    Map<String, String> integrationSecrets = new HashMap<>();
    GitIntegrationTokenAuth gitIntegrationTokenAuth = azureIntegrationEntity.getAuth();
    integrationSecrets.put(INTEGRATIONS_AZURE_PERSONAL_ACCESS_TOKEN + "_" + azureIntegrationEntity.getOrganization(),
        String.valueOf(gitIntegrationTokenAuth.getTokenSecretIdentifier()));
    return integrationSecrets;
  }

  @Override
  public Set<String> getDelegateSelectors(AzureRepoConnectorDTO azureRepoConnectorDTO) {
    return azureRepoConnectorDTO.getDelegateSelectors();
  }

  @Override
  public String getIntegrationAppConfig(
      AzureIntegrationEntity azureIntegrationEntity, AzureRepoConnectorDTO azureRepoConnectorDTO) {
    String integrationConfig = readFileFromClassPath("integrations/git/azure.yaml");
    integrationConfig = integrationConfig.replace("${HOST}", getHost(azureRepoConnectorDTO));
    String organization = getOrganization(azureRepoConnectorDTO);
    integrationConfig = integrationConfig.replace("${ORGANIZATION}", organization);
    integrationConfig = integrationConfig.replace(
        "${AZURE_PERSONAL_ACCESS_TOKEN}", "${AZURE_PERSONAL_ACCESS_TOKEN_" + organization + "}");
    return integrationConfig;
  }

  @Override
  String getRepoUrl(AzureRepoConnectorDTO azureRepoConnectorDTO) {
    return azureRepoConnectorDTO.getUrl();
  }

  @Override
  String getGitConnectionType(AzureRepoConnectorDTO azureRepoConnectorDTO) {
    return azureRepoConnectorDTO.getConnectionTypeForGit().toString();
  }

  @Override
  void validateReadPermission(String accountIdentifier, AzureRepoConnectorDTO azureRepoConnectorDTO,
      AzureIntegrationEntity azureIntegrationEntity, Map<String, String> configsForGitIntegration,
      Map<String, String> secretsForGitIntegration) {
    if (azureIntegrationEntity.getReadPermissionValidation() != null) {
      validateReadPermissionForUrl(accountIdentifier, azureRepoConnectorDTO, azureIntegrationEntity,
          azureIntegrationEntity.getReadPermissionValidation().getFileUrl());
    }
  }

  @Override
  void validateReadPermissionForUrl(String accountIdentifier, AzureRepoConnectorDTO azureRepoConnectorDTO,
      AzureIntegrationEntity azureIntegrationEntity, String urlForValidation) {
    String fileUrlForValidation = urlForValidation;
    String[] pathParts = getPathParts(fileUrlForValidation);
    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation;
    if (azureIntegrationEntity.isExecuteOnDelegate()) {
      GitIntegrationDto gitIntegrationDto = AzureIntegrationDto.builder()
                                                .url("https://" + azureIntegrationEntity.getHost())
                                                .organization(pathParts[1])
                                                .project(pathParts[2])
                                                .repo(pathParts[4])
                                                .build();
      readPermissionValidation = validateViaDelegateAndFrameReadPermissionValidation(
          azureIntegrationEntity, gitIntegrationDto, fileUrlForValidation);
    } else {
      String token = getDecryptedValue(accountIdentifier, azureIntegrationEntity.getAuth().getTokenSecretIdentifier());
      JSONObject response = azureRepoService.getRepository(
          AzureRepoConfig.builder().azureRepoUrl("https://" + azureIntegrationEntity.getHost()).build(), token,
          pathParts[1], pathParts[2], pathParts[4]);
      readPermissionValidation = frameReadPermissionValidation(fileUrlForValidation, response);
    }
    azureIntegrationEntity.setReadPermissionValidation(readPermissionValidation);
  }

  @Override
  protected String getRepository(AzureRepoConnectorDTO azureRepoConnectorDTO, String repository) {
    String repo = GitClientHelper.getGitRepo(repository);
    if (azureRepoConnectorDTO.getConnectionType().equals(AzureRepoConnectionTypeDTO.REPO)
        && repository.contains("/_git/")) {
      String[] repoSplit = repo.split(SLASH_DELIMITER);
      repo = repoSplit.length > 1 ? repoSplit[repoSplit.length - 1] : repo;
    }
    return repo;
  }

  @Override
  public String getAlreadyExistErrorMessage(AzureIntegrationEntity gitIntegrationEntity) {
    return "Azure Repo integration with host " + gitIntegrationEntity.getHost() + " and organization "
        + gitIntegrationEntity.getAdditionalIndexer() + " already exists. ";
  }

  @Override
  List<String> getAdditionalHosts(ConnectorInfoDTO connectorDTO) {
    return List.of();
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(
      AzureIntegrationEntity azureIntegrationEntity, List<HttpHeaderConfig> headers) {
    removeHeadersForDelegateTask(headers);
    GitIntegrationTokenAuth auth = azureIntegrationEntity.getAuth();
    return AzureRepoTokenSpecDTO.builder()
        .tokenRef(isEmpty(auth.getTokenSecretIdentifier())
                ? null
                : new SecretRefData(addAccountScopeInIdentifier(auth.getTokenSecretIdentifier())))
        .build();
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(ConnectorInfoDTO connectorInfoDTO) {
    return null;
  }

  @Override
  protected DecryptableEntity getAuthForDelegateRequest(GitIntegrationEntity gitIntegrationEntity) {
    AzureIntegrationEntity azureIntegrationEntity = (AzureIntegrationEntity) gitIntegrationEntity;
    return AzureRepoTokenSpecDTO.builder()
        .tokenRef(
            new SecretRefData(addAccountScopeInIdentifier(azureIntegrationEntity.getAuth().getTokenSecretIdentifier())))
        .build();
  }

  private AzureRepoHttpCredentialsDTO azureRepoHttpCredentialsDTO(AzureRepoConnectorDTO azureRepoConnectorDTO) {
    AzureRepoAuthenticationDTO authentication = azureRepoConnectorDTO.getAuthentication();
    GitAuthType authType = authentication.getAuthType();
    if (authType != GitAuthType.HTTP) {
      throw new InvalidRequestException("Azure integration is supported only with HTTP authentication");
    }
    return (AzureRepoHttpCredentialsDTO) authentication.getCredentials();
  }

  private String getOrganization(AzureRepoConnectorDTO azureRepoConnectorDTO) {
    try {
      URI uri = new URI(azureRepoConnectorDTO.getUrl());
      String path = uri.getPath();
      return path.split("/")[1];
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Error in extracting organization from azure repo url");
    }
  }
}
