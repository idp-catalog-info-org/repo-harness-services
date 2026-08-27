/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.getUserPrincipalFromPrincipal;
import static io.harness.idp.common.CommonUtils.readFileFromClassPath;
import static io.harness.idp.common.CommonUtils.removeTrailingAndLeadingSlash;
import static io.harness.idp.common.Constants.INTEGRATIONS_HARNESS_CODE_REPO_TOKEN;
import static io.harness.idp.common.Constants.SLASH_DELIMITER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.gitsync.GitFileDetails;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.response.GitFileResponse;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.HarnessAuthenticationDTO;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.common.JacksonUtils;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationManagedTokenAuth;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity.AuthMode;
import io.harness.idp.integrations.entities.git.HarnessCodeRepoIntegrationEntity;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.dto.UserPrincipal;
import io.harness.service.ScmServiceClient;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;
import io.harness.utils.ConnectorUtils;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
public final class HarnessCodeRepoIntegrationOpsImpl
    extends GitIntegrationOps<HarnessCodeRepoIntegrationEntity, HarnessConnectorDTO> {
  @Inject ConnectorUtils connectorUtils;
  @Inject SecretDecryptor secretDecryptor;
  @Inject SCMGrpc.SCMBlockingStub scmBlockingStub;
  @Inject ScmServiceClient scmServiceClient;
  @Inject IdpCommonService idpCommonService;

  @Override
  HarnessCodeRepoIntegrationEntity prepare(ConnectorInfoDTO connectorDTO) {
    HarnessConnectorDTO harnessConnectorDTO = getConnectorConfigDTO(connectorDTO);
    return HarnessCodeRepoIntegrationEntity.builder()
        .accountIdentifier(connectorDTO.getAccountIdentifier())
        .identifier(connectorDTO.getIdentifier())
        .integration(IntegrationEntity.Integration.GIT)
        .parentType(IntegrationEntity.ParentType.HARNESS_CODE_REPO)
        .connectorIdentifier(connectorDTO.getIdentifier())
        .host(getHost(harnessConnectorDTO))
        .additionalHosts(getAdditionalHosts(connectorDTO))
        .authMode(validateAndGetAuthMode(harnessConnectorDTO))
        .executeOnDelegate(harnessConnectorDTO.getExecuteOnDelegate())
        .delegateSelectors(getDelegateSelectors(harnessConnectorDTO))
        .auth((GitIntegrationManagedTokenAuth) getAuth(harnessConnectorDTO, connectorDTO.getAccountIdentifier()))
        .additionalIndexer(getHost(harnessConnectorDTO))
        .readPermissionValidation(GitIntegrationEntity.ReadPermissionValidation.builder()
                                      .status("success")
                                      .error("")
                                      .lastValidatedAt(System.currentTimeMillis())
                                      .build())
        .managed(true)
        .build();
  }

  @Override
  HarnessConnectorDTO getConnectorConfigDTO(ConnectorInfoDTO connectorInfoDTO) {
    return (HarnessConnectorDTO) connectorInfoDTO.getConnectorConfig();
  }

  @Override
  String getHost(HarnessConnectorDTO harnessConnectorDTO) {
    return getDomainFromUrl(harnessConnectorDTO.getUrl());
  }

  @Override
  AuthMode validateAndGetAuthMode(HarnessConnectorDTO harnessConnectorDTO) {
    HarnessHttpCredentialsDTO credentials = harnessCodeRepoHttpCredentialsDTO(harnessConnectorDTO);
    HarnessHttpAuthenticationType type = credentials.getType();
    if (type != HarnessHttpAuthenticationType.USERNAME_AND_TOKEN) {
      throw new InvalidRequestException("HarnessCodeRepo integration is supported only with token authentication");
    }
    return AuthMode.MANAGED_TOKEN;
  }

  @Override
  GitIntegrationAuth getAuth(HarnessConnectorDTO harnessConnectorDTO, String accountIdentifier) {
    return new GitIntegrationManagedTokenAuth();
  }

  @Override
  Map<String, String> getIntegrationConfigs(HarnessCodeRepoIntegrationEntity gitIntegrationEntity) {
    return Map.of();
  }

  @Override
  Map<String, String> getIntegrationSecrets(HarnessCodeRepoIntegrationEntity harnessCodeRepoIntegrationEntity) {
    Map<String, String> integrationSecrets = new HashMap<>();
    GitIntegrationManagedTokenAuth gitIntegrationManagedTokenAuth = harnessCodeRepoIntegrationEntity.getAuth();
    integrationSecrets.put(
        INTEGRATIONS_HARNESS_CODE_REPO_TOKEN + "_" + sanitizeHost(harnessCodeRepoIntegrationEntity.getHost()),
        String.valueOf(gitIntegrationManagedTokenAuth.getManagedTokenSecretIdentifier()));
    List<String> additionalHosts = harnessCodeRepoIntegrationEntity.getAdditionalHosts();
    if (!isEmpty(additionalHosts)) {
      for (String additionalHost : additionalHosts) {
        integrationSecrets.put(INTEGRATIONS_HARNESS_CODE_REPO_TOKEN + "_" + sanitizeHost(additionalHost),
            String.valueOf(gitIntegrationManagedTokenAuth.getManagedTokenSecretIdentifier()));
      }
    }
    return integrationSecrets;
  }

  @Override
  Set<String> getDelegateSelectors(HarnessConnectorDTO connectorConfigDTO) {
    return Collections.emptySet();
  }

  @Override
  String getIntegrationAppConfig(HarnessCodeRepoIntegrationEntity entity, HarnessConnectorDTO harnessConnectorDTO) {
    String host = getHost(harnessConnectorDTO);
    String integrationConfig = readFileFromClassPath("integrations/git/harness-code-repo.yaml");
    integrationConfig = integrationConfig.replace("${HOST}", host);
    integrationConfig = integrationConfig.replace(
        "${HARNESS_CODE_REPO_TOKEN}", "${HARNESS_CODE_REPO_TOKEN_" + sanitizeHost(host) + "}");

    try {
      Map<String, Object> yamlMap = JacksonUtils.YAML_MAPPER.readValue(integrationConfig, Map.class);
      List<Map<String, String>> harnessList =
          (List<Map<String, String>>) ((Map<String, Object>) yamlMap.get("integrations")).get("harness");
      List<String> additionalHosts = entity.getAdditionalHosts();
      if (!isEmpty(additionalHosts)) {
        for (String additionalHost : additionalHosts) {
          Map<String, String> entry = new HashMap<>();
          entry.put("host", additionalHost);
          entry.put("token", "${HARNESS_CODE_REPO_TOKEN_" + sanitizeHost(additionalHost) + "}");
          harnessList.add(entry);
        }
      }
      return JacksonUtils.YAML_MAPPER.writeValueAsString(yamlMap);
    } catch (Exception ex) {
      log.error("Error parsing YAML: ", ex);
      throw new UnexpectedException("Error while preparing integration app config for HarnessCodeRepo integration", ex);
    }
  }

  @Override
  String getRepoUrl(HarnessConnectorDTO harnessConnectorDTO) {
    return harnessConnectorDTO.getUrl();
  }

  @Override
  String getGitConnectionType(HarnessConnectorDTO harnessConnectorDTO) {
    return harnessConnectorDTO.getConnectionTypeForGit().toString();
  }

  @Override
  void validateReadPermission(String accountIdentifier, HarnessConnectorDTO harnessConnectorDTO,
      HarnessCodeRepoIntegrationEntity gitIntegrationEntity, Map<String, String> configsForGitIntegration,
      Map<String, String> secretsForGitIntegration) {}

  @Override
  void validateReadPermissionForUrl(String accountIdentifier, HarnessConnectorDTO harnessConnectorDTO,
      HarnessCodeRepoIntegrationEntity gitIntegrationEntity, String urlForValidation) {}

  @Override
  String getRepository(HarnessConnectorDTO connectorConfigDTO, String repository) {
    throw new UnsupportedOperationException("Method getRepository not yet supported for HarnessCodeRepo integration");
  }

  @Override
  String getAlreadyExistErrorMessage(HarnessCodeRepoIntegrationEntity gitIntegrationEntity) {
    return "HarnessCodeRepo integration with host " + gitIntegrationEntity.getHost() + " already exists. ";
  }

  @Override
  List<String> getAdditionalHosts(ConnectorInfoDTO connectorDTO) {
    AccountDTO accountDTO = idpCommonService.getAccountDTO(connectorDTO.getAccountIdentifier());
    String subdomainUrl = accountDTO.getSubdomainURL();
    if (!isEmpty(subdomainUrl)) {
      return List.of(getDomainFromUrl(subdomainUrl));
    }
    return List.of();
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(
      HarnessCodeRepoIntegrationEntity gitIntegrationEntity, List<HttpHeaderConfig> headers) {
    return null;
  }

  @Override
  DecryptableEntity getAuthenticationDetailsForDelegateTask(ConnectorInfoDTO connectorInfoDTO) {
    return null;
  }

  @Override
  protected void writeThroughAPI(String accountIdentifier, WriteValidationDetails writeValidationDetails,
      ConnectorInfoDTO connectorInfoDTO, List<Pair<String, String>> files) {
    ConnectorDTO connectorDTO = ConnectorDTO.builder().build();
    connectorDTO.setConnectorInfo(connectorInfoDTO);

    BaseNGAccess ngAccess = BaseNGAccess.builder().accountIdentifier(accountIdentifier).build();
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorDTO);

    ScmConnector scmConnector = (HarnessConnectorDTO) connectorDetails.getConnectorConfig();
    final DecryptableEntity decryptableEntity =
        secretDecryptor.decrypt(GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector),
            connectorDetails.getEncryptedDataDetails());
    GitApiAccessDecryptionHelper.setAPIAccessDecryptableEntity(scmConnector, decryptableEntity);

    UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();

    for (Pair<String, String> file : files) {
      String filePath =
          removeTrailingAndLeadingSlash(writeValidationDetails.getPath()) + SLASH_DELIMITER + file.getKey();

      GitFileDetails gitFileDetails = gitFileDetails(writeValidationDetails.getBranch(), filePath, file.getValue(),
          null, null, userPrincipal.getEmail(), userPrincipal.getUsername());
      try {
        CreateFileResponse createFileResponse =
            scmServiceClient.createFile(scmConnector, gitFileDetails, scmBlockingStub, false);
        if (createFileResponse.getStatus() >= 300) {
          throw new UnexpectedException(createFileResponse.getError());
        }
      } catch (Exception createEx) {
        if (createEx.getMessage().equals("Conflict")
            || (createEx.getMessage().contains("file path ") && createEx.getMessage().contains(" already exists"))) {
          tryUpdate(scmConnector, userPrincipal, writeValidationDetails, filePath, file.getValue());
        } else {
          throw new UnexpectedException(createEx.getMessage());
        }
      }
    }
  }

  @Override
  protected DecryptableEntity getAuthForDelegateRequest(GitIntegrationEntity gitIntegrationEntity) {
    throw new InvalidRequestException("HarnessCodeRepo integration is not supported in delegate connectivity mode");
  }

  private HarnessHttpCredentialsDTO harnessCodeRepoHttpCredentialsDTO(HarnessConnectorDTO harnessConnectorDTO) {
    HarnessAuthenticationDTO authentication = harnessConnectorDTO.getAuthentication();
    GitAuthType authType = authentication.getAuthType();
    if (authType != GitAuthType.HTTP) {
      throw new InvalidRequestException("HarnessCodeRepo integration is supported only with HTTP authentication");
    }
    return authentication.getCredentials();
  }

  private GitFileDetails gitFileDetails(String branch, String filePath, String fileContent, String oldFileSha,
      String commitId, String userEmail, String userName) {
    return GitFileDetails.builder()
        .filePath(filePath)
        .branch(branch)
        .fileContent(fileContent)
        .oldFileSha(oldFileSha)
        .commitId(commitId)
        .commitMessage("Harness IDP")
        .userEmail(userEmail)
        .userName(userName)
        .build();
  }

  private void tryUpdate(ScmConnector scmConnector, UserPrincipal userPrincipal,
      WriteValidationDetails writeValidationDetails, String filePath, String fileContent) {
    try {
      GitFileRequest gitFileRequest =
          GitFileRequest.builder().branch(writeValidationDetails.getBranch()).filepath(filePath).build();
      GitFileResponse gitFileResponse = scmServiceClient.getFile(scmConnector, gitFileRequest, scmBlockingStub);
      if (gitFileResponse.getStatusCode() >= 300) {
        throw new UnexpectedException(gitFileResponse.getError());
      }
      GitFileDetails gitFileDetailsForUpdate =
          gitFileDetails(writeValidationDetails.getBranch(), filePath, fileContent, gitFileResponse.getObjectId(),
              gitFileResponse.getCommitId(), userPrincipal.getEmail(), userPrincipal.getUsername());
      UpdateFileResponse updateFileResponse =
          scmServiceClient.updateFile(scmConnector, gitFileDetailsForUpdate, scmBlockingStub, false);
      if (updateFileResponse.getStatus() >= 300) {
        throw new UnexpectedException(updateFileResponse.getError());
      }
    } catch (Exception updateEx) {
      if (updateEx.getMessage().equals("No effective changes.")) {
        return;
      }
      throw new UnexpectedException(updateEx.getMessage());
    }
  }
}
