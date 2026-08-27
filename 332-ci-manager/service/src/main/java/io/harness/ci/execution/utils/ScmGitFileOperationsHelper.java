/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.Scope;
import io.harness.beans.request.GitFileBatchRequest;
import io.harness.beans.request.GitFileRequestV2;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.beans.response.GitFileResponse;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.utils.configfile.GitFileStoreSpec;
import io.harness.ci.states.codebase.ScmGitRefManager;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.task.scm.ConnectorDecryptionParams;
import io.harness.delegate.task.scm.GetFileTaskParamsPerConnector;
import io.harness.delegate.task.scm.GitFileLocationDetails;
import io.harness.delegate.task.scm.ScmBatchGetFileTaskParams;
import io.harness.exception.InvalidRequestException;
import io.harness.impl.scm.ScmGitProviderHelper;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.service.ScmClient;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** SCM batch file operations for unified config files using {@link GitFileStoreSpec}. */
public class ScmGitFileOperationsHelper {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ScmGitRefManager scmGitRefManager;
  @Inject private ScmClient scmClient;
  @Inject private ScmGitProviderHelper scmGitProviderHelper;

  public GitFileBatchResponse getBatchFile(GitFileStoreSpec spec, GitConnectorInfo gitConnectorInfo) {
    validateBranchOrCommitPresent(spec);
    return getFilesInBatch(spec, gitConnectorInfo.getConnector(), gitConnectorInfo.getConnectorRef(),
        gitConnectorInfo.getAccountId(), gitConnectorInfo.getRepoName());
  }

  private GitFileBatchResponse getFilesInBatch(
      GitFileStoreSpec spec, ScmConnector scmConnector, String connectorRef, String accountId, String repoName) {
    List<String> filePaths = spec.getPaths();
    String branch = emptyToBlank(spec.getBranch());
    String commitId = emptyToBlank(spec.getCommitId());
    Map<GetBatchFileRequestIdentifier, GitFileRequestV2> gitFilesRequestMap = new HashMap<>();
    for (String path : filePaths) {
      GitFileRequestV2 gitFileRequestV2 = GitFileRequestV2.builder()
                                              .repo(repoName)
                                              .branch(branch)
                                              .commitId(commitId)
                                              .filepath(path)
                                              .getOnlyFileContent(false)
                                              .scmConnector(scmConnector)
                                              .connectorRef(connectorRef)
                                              .build();
      GetBatchFileRequestIdentifier identifier = GetBatchFileRequestIdentifier.builder().identifier(path).build();
      gitFilesRequestMap.put(identifier, gitFileRequestV2);
    }
    GitFileBatchRequest request = GitFileBatchRequest.builder()
                                      .accountIdentifier(accountId)
                                      .getBatchFileRequestIdentifierGitFileRequestV2Map(gitFilesRequestMap)
                                      .build();
    return scmClient.getBatchFile(request);
  }

  public ScmBatchGetFileTaskParams getScmGetBatchFileTaskParams(
      GitFileStoreSpec spec, GitConnectorInfo gitConnectorInfo) {
    validateBranchOrCommitPresent(spec);
    List<String> filePaths = spec.getPaths();
    String branch = emptyToBlank(spec.getBranch());
    String commitId = emptyToBlank(spec.getCommitId());
    List<GetFileTaskParamsPerConnector> paramsPerConnector =
        getFileTaskParamsPerConnector(gitConnectorInfo.getConnectorDetails(), gitConnectorInfo.getConnector(),
            gitConnectorInfo.getAccountId(), filePaths, gitConnectorInfo.getRepoName(), branch, commitId);
    return ScmBatchGetFileTaskParams.builder().getFileTaskParamsPerConnectorList(paramsPerConnector).build();
  }

  private List<GetFileTaskParamsPerConnector> getFileTaskParamsPerConnector(ConnectorDetails connectorDetails,
      ScmConnector scmConnector, String accountId, List<String> filePaths, String repoName, String branch,
      String commitId) {
    Scope scope = Scope.of(accountId, connectorDetails.getOrgIdentifier(), connectorDetails.getProjectIdentifier());
    ConnectorDecryptionParams connectorDecryptionParams =
        ConnectorDecryptionParams.builder().scmConnector(scmConnector).connectorScope(scope).build();
    Map<GetBatchFileRequestIdentifier, GitFileLocationDetails> gitFileLocationDetailsMap = new HashMap<>();
    for (String path : filePaths) {
      GitFileLocationDetails gitFileLocationDetails = GitFileLocationDetails.builder()
                                                          .repo(repoName)
                                                          .branch(branch)
                                                          .commitId(commitId)
                                                          .filepath(path)
                                                          .getOnlyFileContent(false)
                                                          .build();
      GetBatchFileRequestIdentifier identifier = GetBatchFileRequestIdentifier.builder().identifier(path).build();
      gitFileLocationDetailsMap.put(identifier, gitFileLocationDetails);
    }
    GetFileTaskParamsPerConnector paramsPerConnector = GetFileTaskParamsPerConnector.builder()
                                                           .connectorDecryptionParams(connectorDecryptionParams)
                                                           .gitFileLocationDetailsMap(gitFileLocationDetailsMap)
                                                           .build();
    return new ArrayList<>(List.of(paramsPerConnector));
  }

  public Map<String, String> toFileContentsDataMap(GitFileBatchResponse response) {
    Map<String, String> fileContentsDataMap = new HashMap<>();
    Map<GetBatchFileRequestIdentifier, GitFileResponse> batchFileRequestIdentifierGitFileResponseMap =
        response.getGetBatchFileRequestIdentifierGitFileResponseMap();
    for (Map.Entry<GetBatchFileRequestIdentifier, GitFileResponse> entry :
        batchFileRequestIdentifierGitFileResponseMap.entrySet()) {
      String pathIdentifier = entry.getKey().getIdentifier();
      String fileContent = entry.getValue().getContent();
      fileContentsDataMap.put(pathIdentifier, fileContent);
    }
    return fileContentsDataMap;
  }

  public GitApiTaskParams getScmCgiFetchFilesTaskParams(GitFileStoreSpec spec, GitConnectorInfo gitConnectorInfo) {
    validateBranchOrCommitPresent(spec);
    return GitApiTaskParams.builder()
        .ref(emptyToBlank(spec.getBranch()))
        .paths(spec.getPaths())
        .sha(emptyToBlank(spec.getCommitId()))
        .connectorDetails(gitConnectorInfo.getConnectorDetails())
        .slug(gitConnectorInfo.getSlug())
        .build();
  }

  public void validateBranchOrCommitPresent(GitFileStoreSpec spec) {
    if (isEmpty(spec.getBranch()) && isEmpty(spec.getCommitId())) {
      throw new InvalidRequestException("One of commit or branch must be set");
    }
  }

  @Data
  @Builder
  public static class GitConnectorInfo {
    private String connectorRef;
    private ConnectorDetails connectorDetails;
    private String accountId;
    private String repoName;
    private String slug;
    private ScmConnector connector;
  }

  public GitConnectorInfo getGitConnectorInfo(Ambiance ambiance, GitFileStoreSpec spec) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, spec.getConnectorRef(), true);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String repo = spec.getRepoName();
    ScmConnector connector = scmGitRefManager.getScmConnector(connectorDetails, accountId, repo);
    String repoName = scmGitProviderHelper.getRepoName(connector);
    String slug = scmGitProviderHelper.getSlug(connector);
    return GitConnectorInfo.builder()
        .connectorRef(spec.getConnectorRef())
        .connectorDetails(connectorDetails)
        .accountId(accountId)
        .repoName(repoName)
        .slug(slug)
        .connector(connector)
        .build();
  }

  private static String emptyToBlank(String s) {
    return s == null ? "" : s;
  }
}
