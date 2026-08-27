/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.constants.Constants.SCM_CONFLICT_ERROR_MESSAGE;
import static io.harness.delegate.task.utils.PhysicalDataCenterConstants.EXECUTION_TIMEOUT_IN_SECONDS;
import static io.harness.gitsync.common.beans.GitOperation.CREATE_FILE;
import static io.harness.gitsync.common.beans.GitOperation.GET_FILE;
import static io.harness.idp.common.CommonUtils.getUserPrincipalFromPrincipal;
import static io.harness.idp.common.CommonUtils.removeTrailingAndLeadingSlash;
import static io.harness.idp.common.Constants.PRIVATE_KEY_END;
import static io.harness.idp.common.Constants.PRIVATE_KEY_START;
import static io.harness.idp.common.Constants.SLASH_DELIMITER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.Scope;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.task.idp.gitintegration.GitIntegrationDto;
import io.harness.delegate.task.idp.gitintegration.request.GitIntegrationReadValidationRequest;
import io.harness.delegate.task.idp.gitintegration.response.GitIntegrationReadValidationResponse;
import io.harness.exception.UnexpectedException;
import io.harness.gitsync.CreateFileRequest;
import io.harness.gitsync.CreateFileResponse;
import io.harness.gitsync.GetFileRequest;
import io.harness.gitsync.GetFileResponse;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc;
import io.harness.gitsync.UpdateFileRequest;
import io.harness.gitsync.UpdateFileResponse;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.gitsync.common.helper.GitSyncLogContextHelper;
import io.harness.gitsync.common.helper.ScopeIdentifierMapper;
import io.harness.gitsync.common.helper.UserPrincipalMapper;
import io.harness.http.HttpHeaderConfig;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity.AuthMode;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.Principal;
import io.harness.security.PrincipalProtoMapper;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;

import software.wings.beans.TaskType;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public abstract sealed class GitIntegrationOps<S extends IntegrationEntity, T extends ConnectorConfigDTO> permits
    AzureIntegrationOpsImpl,
    BitbucketIntegrationOpsImpl, GithubIntegrationOpsImpl, GitlabIntegrationOpsImpl, HarnessCodeRepoIntegrationOpsImpl {
  @Inject @Named("PRIVILEGED") SecretManagerClientService secretManagerClientService;
  @Inject HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoService;
  @Inject DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ConnectorResourceClient connectorResourceClient;

  static final String ERROR = "error";
  static final String FAILED = "failed";
  static final String STATUS = "status";
  static final String FILE_NAME = ".harness-idp-tmp-connection-validation";
  static final String FILE_CONTENT =
      "This file is generated by harness to test IDP write validation. Feel free to remove it.";

  abstract S prepare(ConnectorInfoDTO connectorDTO);

  abstract T getConnectorConfigDTO(ConnectorInfoDTO connectorInfoDTO);

  abstract String getHost(T connectorConfigDTO);

  abstract AuthMode validateAndGetAuthMode(T connectorConfigDTO);

  abstract GitIntegrationAuth getAuth(T connectorConfigDTO, String accountIdentifier);

  abstract Map<String, String> getIntegrationConfigs(S gitIntegrationEntity);

  abstract Map<String, String> getIntegrationSecrets(S gitIntegrationEntity);

  abstract Set<String> getDelegateSelectors(T connectorConfigDTO);

  abstract String getIntegrationAppConfig(S entity, T connectorConfigDTO);

  abstract String getRepoUrl(T connectorConfigDTO);

  abstract String getGitConnectionType(T connectorConfigDTO);
  abstract void validateReadPermission(String accountIdentifier, T connectorConfigDTO, S gitIntegrationEntity,
      Map<String, String> configsForGitIntegration, Map<String, String> secretsForGitIntegration);

  abstract void validateReadPermissionForUrl(
      String accountIdentifier, T connectorConfigDTO, S gitIntegrationEntity, String urlForValidation);

  abstract String getRepository(T connectorConfigDTO, String repository);

  abstract String getAlreadyExistErrorMessage(S gitIntegrationEntity);

  abstract List<String> getAdditionalHosts(ConnectorInfoDTO connectorDTO);

  abstract DecryptableEntity getAuthenticationDetailsForDelegateTask(
      S gitIntegrationEntity, List<HttpHeaderConfig> headers);

  abstract DecryptableEntity getAuthenticationDetailsForDelegateTask(ConnectorInfoDTO connectorInfoDTO);

  GitIntegrationEntity.ReadPermissionValidation validateViaDelegateAndFrameReadPermissionValidation(
      GitIntegrationEntity gitlabIntegrationEntity, GitIntegrationDto gitIntegrationDto, String fileUrlForValidation) {
    DecryptableEntity authentication = getAuthForDelegateRequest(gitlabIntegrationEntity);
    DelegateTaskRequest delegateTaskRequest =
        prepareDelegateTaskRequest(gitlabIntegrationEntity, gitIntegrationDto, authentication);
    GitIntegrationReadValidationResponse delegateTaskResponse = validateReadPermissionViaDelegate(delegateTaskRequest);
    return frameReadPermissionValidation(fileUrlForValidation, delegateTaskResponse);
  }

  void validateWritePermission(
      String accountIdentifier, WriteValidationDetails writeValidationDetails, ConnectorInfoDTO connectorInfoDTO) {
    List<Pair<String, String>> files = Collections.singletonList(Pair.of(FILE_NAME, FILE_CONTENT));
    writeThroughAPI(accountIdentifier, writeValidationDetails, connectorInfoDTO, files);
  }

  protected String getDomainFromUrl(String url) {
    try {
      URI uri = new URI(url);
      String host = uri.getHost();
      int port = uri.getPort();
      host = host.startsWith("www.") ? host.substring(4) : host;
      return port != -1 ? (host + ":" + port) : host;
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Error in extracting domain from url");
    }
  }

  protected NGAccess ngAccess(String accountIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(null)
        .projectIdentifier(null)
        .build();
  }

  protected String sanitizeHost(String host) {
    return host.toUpperCase().replace(".", "_");
  }

  protected AppConfig getAppConfig(S entity, T connectorConfigDTO) {
    AppConfig appConfig = new AppConfig();

    appConfig.setConfigId(entity.getConfigId());
    appConfig.setConfigs(getIntegrationAppConfig(entity, connectorConfigDTO));
    appConfig.setEnabled(true);

    return appConfig;
  }

  protected String[] getPathParts(String url) {
    try {
      URI uri = new URI(url);
      return uri.getPath().split("/");
    } catch (URISyntaxException e) {
      throw new UnexpectedException("Error in extracting path parts from url");
    }
  }

  protected String getDecryptedValue(String accountIdentifier, String identifier) {
    DecryptedSecretValue decryptedSecretValue =
        secretManagerClientService.getDecryptedSecretValue(accountIdentifier, null, null, identifier);
    return decryptedSecretValue.getDecryptedValue();
  }

  protected String getDecryptedValueGithubAppPrivateKey(String accountIdentifier, String identifier) {
    DecryptedSecretValue decryptedSecretValue =
        secretManagerClientService.getDecryptedSecretValue(accountIdentifier, null, null, identifier);
    SecretResponseWrapper secretResponseWrapper =
        secretManagerClientService.getSecret(accountIdentifier, null, null, identifier);
    if (secretResponseWrapper.getSecret().getType().equals(SecretType.SecretFile)) {
      decryptedSecretValue.setDecryptedValue(
          new String(Base64.getDecoder().decode(decryptedSecretValue.getDecryptedValue()), StandardCharsets.UTF_8));
    }
    if (secretResponseWrapper.getSecret().getType().equals(SecretType.SecretText)) {
      String privateKeyFormatted = formatPrivateKey(decryptedSecretValue.getDecryptedValue());
      decryptedSecretValue.setDecryptedValue(privateKeyFormatted);
    }
    return decryptedSecretValue.getDecryptedValue();
  }

  protected GitIntegrationReadValidationResponse validateReadPermissionViaDelegate(
      DelegateTaskRequest delegateTaskRequest) {
    try {
      DelegateResponseData responseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
      if (responseData instanceof ErrorNotifyResponseData errorNotifyResponseData) {
        log.error("Delegate error: Could not validate read permissions with error {}",
            errorNotifyResponseData.getErrorMessage(), errorNotifyResponseData.getException());
        return GitIntegrationReadValidationResponse.builder()
            .code(500)
            .status("failed")
            .error(errorNotifyResponseData.getErrorMessage())
            .build();
      }
      if (responseData instanceof GitIntegrationReadValidationResponse delegateTaskResponse) {
        log.info("delegateTaskResponse: {}", delegateTaskResponse);
        return (GitIntegrationReadValidationResponse) responseData;
      }
    } catch (Exception ex) {
      log.error("Delegate error: Could not validate read permissions", ex);
      return GitIntegrationReadValidationResponse.builder().code(500).status("failed").error(ex.getMessage()).build();
    }
    return null;
  }

  DelegateTaskRequest prepareDelegateTaskRequest(GitIntegrationEntity githubIntegrationEntity,
      GitIntegrationDto gitIntegrationDto, DecryptableEntity authentication) {
    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier(githubIntegrationEntity.getAccountIdentifier()).build();
    gitIntegrationDto.setAuthentication(authentication);
    gitIntegrationDto.setEncryptedDataDetails(
        secretManagerClientService.getEncryptionDetails(ngAccess, authentication));
    int integerHash = HashGenerator.generateIntegerHash();
    return DelegateTaskRequest.builder()
        .accountId(githubIntegrationEntity.getAccountIdentifier())
        .executionTimeout(Duration.ofSeconds(EXECUTION_TIMEOUT_IN_SECONDS))
        .taskType(TaskType.GIT_INTEGRATION_READ_VALIDATION_TASK.name())
        .taskParameters(GitIntegrationReadValidationRequest.builder().gitIntegration(gitIntegrationDto).build())
        .expressionFunctorToken(integerHash)
        .taskSelectors(githubIntegrationEntity.getDelegateSelectors())
        .taskDescription("IDP git read validation task")
        .taskSetupAbstraction("ng", "true")
        .build();
  }

  protected GitIntegrationEntity.ReadPermissionValidation frameReadPermissionValidation(
      String fileUrlForValidation, JSONObject response) {
    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation =
        new GitIntegrationEntity.ReadPermissionValidation();
    readPermissionValidation.setFileUrl(fileUrlForValidation);
    readPermissionValidation.setLastValidatedAt(System.currentTimeMillis());
    readPermissionValidation.setStatus(response.getString(STATUS));
    readPermissionValidation.setError(response.getString(ERROR));
    return readPermissionValidation;
  }

  protected GitIntegrationEntity.ReadPermissionValidation frameReadPermissionValidation(
      String fileUrlForValidation, GitIntegrationReadValidationResponse response) {
    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation =
        new GitIntegrationEntity.ReadPermissionValidation();
    readPermissionValidation.setFileUrl(fileUrlForValidation);
    readPermissionValidation.setLastValidatedAt(System.currentTimeMillis());
    readPermissionValidation.setStatus(response.getStatus());
    readPermissionValidation.setError(response.getError());
    return readPermissionValidation;
  }

  private String formatPrivateKey(String privateKey) {
    privateKey = privateKey.replace(PRIVATE_KEY_START + " ", "");
    privateKey = privateKey.replace(PRIVATE_KEY_END, "");
    privateKey = privateKey.replace(" ", "\n");
    String privateKeyFormatted = PRIVATE_KEY_START + "\n";
    privateKeyFormatted = privateKeyFormatted + privateKey;
    privateKeyFormatted = privateKeyFormatted + PRIVATE_KEY_END;
    return privateKeyFormatted;
  }

  protected void writeThroughAPI(String accountIdentifier, WriteValidationDetails writeValidationDetails,
      ConnectorInfoDTO connectorInfoDTO, List<Pair<String, String>> files) {
    String repo = getRepository((T) connectorInfoDTO.getConnectorConfig(), writeValidationDetails.getRepository());
    Scope scope = Scope.of(accountIdentifier, null, null);
    UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
    Principal principal = Principal.newBuilder().setUserPrincipal(UserPrincipalMapper.toProto(userPrincipal)).build();
    String branch = writeValidationDetails.getBranch();
    for (Pair<String, String> file : files) {
      Map<String, String> contextMap = new HashMap<>();
      String filePath =
          removeTrailingAndLeadingSlash(writeValidationDetails.getPath()) + SLASH_DELIMITER + file.getKey();
      contextMap = GitSyncLogContextHelper.setContextMap(scope, repo, branch, "", filePath, CREATE_FILE, contextMap);
      CreateFileRequest createFileRequest = createFileRequest(connectorInfoDTO.getIdentifier(), repo, branch,
          Pair.of(filePath, file.getValue()), scope, contextMap, principal);
      executeCreateFileRequest(createFileRequest);
    }
  }

  protected abstract DecryptableEntity getAuthForDelegateRequest(GitIntegrationEntity gitIntegrationEntity);

  protected void removeHeadersForDelegateTask(List<HttpHeaderConfig> headers) {
    List<HttpHeaderConfig> toBeRemovedHeaders;
    toBeRemovedHeaders = headers.stream()
                             .filter(httpHeaderConfig -> httpHeaderConfig.getKey().equalsIgnoreCase("Authorization"))
                             .collect(Collectors.toList());
    headers.removeAll(toBeRemovedHeaders);
  }

  public String getFileContent(
      Scope scope, String connectorIdentifier, String repoName, String branchName, String filePath) {
    try {
      String scopedConnectorIdentifier = CommonUtils.getScopedIdentifier(
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), connectorIdentifier);
      Map<String, String> contextMap = new HashMap<>();
      contextMap =
          GitSyncLogContextHelper.setContextMap(scope, repoName, branchName, "", filePath, GET_FILE, contextMap);
      // Connector credentials authenticate the read; principal is attribution only. Map whatever is in
      // context (UserPrincipal or ServicePrincipal) — matches PrincipalProtoMapper / git-sync getFile.
      Principal principal = buildPrincipalProtoFromContext();
      GetFileRequest getFileRequest =
          getFileRequest(scopedConnectorIdentifier, repoName, branchName, filePath, scope, contextMap, principal);
      GetFileResponse getFileResponse =
          GitSyncGrpcClientUtils.retryAndProcessException(harnessToGitPushInfoService::getFile, getFileRequest);
      if (getFileResponse.getStatusCode() >= 300) {
        throw new UnexpectedException(getFileResponse.getError().getErrorMessage());
      }
      return getFileResponse.getFileContent();
    } catch (Exception ex) {
      throw new UnexpectedException(ex.getMessage());
    }
  }

  /**
   * Maps the current source principal (user or service) into the git-sync protobuf Principal.
   * Do not assume UserPrincipal — background paths set ServicePrincipal.
   */
  private Principal buildPrincipalProtoFromContext() {
    io.harness.security.dto.Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (sourcePrincipal == null) {
      throw new UnexpectedException("Principal cannot be null for git getFileContent");
    }
    return PrincipalProtoMapper.toPrincipalProto(sourcePrincipal);
  }

  private CreateFileRequest createFileRequest(String connectorIdentifier, String repo, String branch,
      Pair<String, String> file, Scope scope, Map<String, String> contextMap, Principal principal) {
    return CreateFileRequest.newBuilder()
        .setRepoName(repo)
        .setBranchName(branch)
        .setFilePath(file.getKey())
        .setConnectorRef(Constants.ACCOUNT_SCOPED + connectorIdentifier)
        .setFileContent(file.getValue())
        .setBaseBranchName(branch)
        .setCommitMessage("Harness IDP")
        .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
        .putAllContextMap(contextMap)
        .setPrincipal(principal)
        .build();
  }

  private void executeCreateFileRequest(CreateFileRequest createFileRequest) {
    try {
      CreateFileResponse createFileResponse =
          GitSyncGrpcClientUtils.retryAndProcessException(harnessToGitPushInfoService::createFile, createFileRequest);
      if (createFileResponse.getStatusCode() >= 300) {
        throw new UnexpectedException(createFileResponse.getError().getErrorMessage());
      }
    } catch (Exception ex) {
      if (ex.getMessage().equals("A file with this name already exists")
          || ex.getMessage().contains("specified in the add operation already exists. Please specify a new path")
          || ex.getMessage().contains("could not be created because it already exists. A previous commit ID must be "
              + "provided when editing an existing file to prevent concurrent modifications.")
          || ex.getMessage().contains(" does not match ")) {
        UpdateFileRequest updateFileRequest = fromCreateFileRequest(createFileRequest);
        executeUpdateFileRequest(updateFileRequest);
      } else {
        String exceptionMessage = ex.getMessage();
        if (exceptionMessage.equals("Not Found")) {
          exceptionMessage = "Please make sure the provided repository exists and matches with the repository config "
              + "provided in the connector";
        }
        throw new UnexpectedException(exceptionMessage);
      }
    }
  }

  private UpdateFileRequest fromCreateFileRequest(CreateFileRequest createFileRequest) {
    return UpdateFileRequest.newBuilder()
        .setRepoName(createFileRequest.getRepoName())
        .setBranchName(createFileRequest.getBranchName())
        .setFilePath(createFileRequest.getFilePath())
        .setConnectorRef(createFileRequest.getConnectorRef())
        .setFileContent(createFileRequest.getFileContent())
        .setBaseBranchName(createFileRequest.getBaseBranchName())
        .setCommitMessage(createFileRequest.getCommitMessage())
        .setScopeIdentifiers(createFileRequest.getScopeIdentifiers())
        .putAllContextMap(createFileRequest.getContextMapMap())
        .setPrincipal(createFileRequest.getPrincipal())
        .build();
  }

  private void executeUpdateFileRequest(UpdateFileRequest updateFileRequest) {
    try {
      UpdateFileResponse updateFileResponse =
          GitSyncGrpcClientUtils.retryAndProcessException(harnessToGitPushInfoService::updateFile, updateFileRequest);
      if (updateFileResponse.getStatusCode() >= 300) {
        throw new UnexpectedException(updateFileResponse.getError().getErrorMessage());
      }
    } catch (Exception ex) {
      if (ex.getMessage().equals(SCM_CONFLICT_ERROR_MESSAGE) || ex.getMessage().contains(" does not match ")) {
        GetFileRequest getFileRequest = getFileRequest(updateFileRequest);
        GetFileResponse getFileResponse =
            GitSyncGrpcClientUtils.retryAndProcessException(harnessToGitPushInfoService::getFile, getFileRequest);
        if (getFileResponse.getStatusCode() >= 300) {
          throw new UnexpectedException(getFileResponse.getError().getErrorMessage());
        }
        updateFileRequest = cloneAndSetOldCommitIdBlobId(updateFileRequest,
            getFileResponse.getGitMetaData().getCommitId(), getFileResponse.getGitMetaData().getBlobId());
        try {
          UpdateFileResponse updateFileResponse = GitSyncGrpcClientUtils.retryAndProcessException(
              harnessToGitPushInfoService::updateFile, updateFileRequest);
          if (updateFileResponse.getStatusCode() >= 300
              && !updateFileResponse.getError().getErrorMessage().equals(
                  "The content provided is the same as what already exists. No change was committed.")) {
            throw new UnexpectedException(updateFileResponse.getError().getErrorMessage());
          }
        } catch (Exception e) {
          throw new UnexpectedException(e.getMessage());
        }
      } else {
        throw new UnexpectedException(ex.getMessage());
      }
    }
  }

  private GetFileRequest getFileRequest(UpdateFileRequest updateFileRequest) {
    return GetFileRequest.newBuilder()
        .setRepoName(updateFileRequest.getRepoName())
        .setConnectorRef(updateFileRequest.getConnectorRef())
        .setBranchName(Strings.nullToEmpty(updateFileRequest.getBranchName()))
        .setFilePath(updateFileRequest.getFilePath())
        .putAllContextMap(updateFileRequest.getContextMapMap())
        .setScopeIdentifiers(updateFileRequest.getScopeIdentifiers())
        .setPrincipal(updateFileRequest.getPrincipal())
        .build();
  }

  private UpdateFileRequest cloneAndSetOldCommitIdBlobId(
      UpdateFileRequest updateFileRequest, String oldCommitId, String blobId) {
    return UpdateFileRequest.newBuilder()
        .setRepoName(updateFileRequest.getRepoName())
        .setBranchName(updateFileRequest.getBranchName())
        .setFilePath(updateFileRequest.getFilePath())
        .setConnectorRef(updateFileRequest.getConnectorRef())
        .setFileContent(updateFileRequest.getFileContent())
        .setBaseBranchName(updateFileRequest.getBaseBranchName())
        .setCommitMessage(updateFileRequest.getCommitMessage())
        .setScopeIdentifiers(updateFileRequest.getScopeIdentifiers())
        .putAllContextMap(updateFileRequest.getContextMapMap())
        .setPrincipal(updateFileRequest.getPrincipal())
        .setOldCommitId(oldCommitId)
        .setOldFileSha(blobId)
        .build();
  }

  private GetFileRequest getFileRequest(String scopedConnectorIdentifier, String repoName, String branchName,
      String filePath, Scope scope, Map<String, String> contextMap, Principal principal) {
    return GetFileRequest.newBuilder()
        .setConnectorRef(scopedConnectorIdentifier)
        .setRepoName(repoName)
        .setBranchName(branchName)
        .setFilePath(filePath)
        .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
        .putAllContextMap(contextMap)
        .setPrincipal(principal)
        .build();
  }
}
