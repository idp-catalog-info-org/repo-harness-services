/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.utils;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.request.GitFileBatchRequest;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.beans.response.GitFileResponse;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.utils.ScmGitFileOperationsHelper.GitConnectorInfo;
import io.harness.ci.execution.utils.configfile.GitFileStoreSpec;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.ci.states.codebase.ScmGitRefManager;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.task.scm.ScmBatchGetFileTaskParams;
import io.harness.exception.InvalidRequestException;
import io.harness.impl.scm.ScmGitProviderHelper;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;
import io.harness.service.ScmClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ScmGitFileOperationsHelperTest extends CIExecutionTestBase {
  private static final String ACCOUNT_ID = "testAccountId";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String REPO_NAME = "testRepo";
  private static final String BRANCH = "main";
  private static final String COMMIT_ID = "abc123";
  private static final String SLUG = "org/repo";
  private static final String FILE_PATH_1 = "path/to/file1.yaml";
  private static final String FILE_PATH_2 = "path/to/file2.yaml";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";

  @Mock private ConnectorUtils connectorUtils;
  @Mock private ScmGitRefManager scmGitRefManager;
  @Mock private ScmClient scmClient;
  @Mock private ScmGitProviderHelper scmGitProviderHelper;
  @Mock private ScmConnector scmConnector;
  @Mock private ConnectorDetails connectorDetails;

  @InjectMocks private ScmGitFileOperationsHelper scmGitFileOperationsHelper;

  private GitConnectorInfo gitConnectorInfo;

  @Before
  public void setUp() {
    when(connectorDetails.getOrgIdentifier()).thenReturn(ORG_ID);
    when(connectorDetails.getProjectIdentifier()).thenReturn(PROJECT_ID);

    gitConnectorInfo = GitConnectorInfo.builder()
                           .connectorRef(CONNECTOR_REF)
                           .connectorDetails(connectorDetails)
                           .accountId(ACCOUNT_ID)
                           .repoName(REPO_NAME)
                           .slug(SLUG)
                           .connector(scmConnector)
                           .build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBatchFile_whenBranchIsSet_shouldCallScmClientWithCorrectRequest() {
    GitFileStoreSpec spec =
        GitFileStoreSpec.builder().branch(BRANCH).commitId(null).paths(Arrays.asList(FILE_PATH_1, FILE_PATH_2)).build();
    GitFileBatchResponse expectedResponse =
        GitFileBatchResponse.builder().getBatchFileRequestIdentifierGitFileResponseMap(new HashMap<>()).build();
    when(scmClient.getBatchFile(any(GitFileBatchRequest.class))).thenReturn(expectedResponse);

    GitFileBatchResponse result = scmGitFileOperationsHelper.getBatchFile(spec, gitConnectorInfo);

    assertThat(result).as("Should return the response from scmClient").isEqualTo(expectedResponse);

    ArgumentCaptor<GitFileBatchRequest> captor = ArgumentCaptor.forClass(GitFileBatchRequest.class);
    verify(scmClient).getBatchFile(captor.capture());
    GitFileBatchRequest captured = captor.getValue();
    assertThat(captured.getAccountIdentifier()).as("Account identifier should match").isEqualTo(ACCOUNT_ID);
    assertThat(captured.getGetBatchFileRequestIdentifierGitFileRequestV2Map())
        .as("Should contain request entries for each file path")
        .hasSize(2);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBatchFile_whenCommitIdIsSet_shouldCallScmClient() {
    GitFileStoreSpec spec = GitFileStoreSpec.builder()
                                .branch(null)
                                .commitId(COMMIT_ID)
                                .paths(Collections.singletonList(FILE_PATH_1))
                                .build();
    GitFileBatchResponse expectedResponse =
        GitFileBatchResponse.builder().getBatchFileRequestIdentifierGitFileResponseMap(new HashMap<>()).build();
    when(scmClient.getBatchFile(any(GitFileBatchRequest.class))).thenReturn(expectedResponse);

    GitFileBatchResponse result = scmGitFileOperationsHelper.getBatchFile(spec, gitConnectorInfo);

    assertThat(result).as("Should return scmClient response for commit-based fetch").isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBatchFile_whenNoBranchOrCommit_shouldThrowException() {
    GitFileStoreSpec spec =
        GitFileStoreSpec.builder().branch(null).commitId(null).paths(Collections.singletonList(FILE_PATH_1)).build();

    assertThatThrownBy(() -> scmGitFileOperationsHelper.getBatchFile(spec, gitConnectorInfo))
        .as("Should throw when neither branch nor commitId is set")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("One of commit or branch must be set");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetBatchFile_whenBranchAndCommitEmpty_shouldThrowException() {
    GitFileStoreSpec spec =
        GitFileStoreSpec.builder().branch("").commitId("").paths(Collections.singletonList(FILE_PATH_1)).build();

    assertThatThrownBy(() -> scmGitFileOperationsHelper.getBatchFile(spec, gitConnectorInfo))
        .as("Should throw when both branch and commitId are empty strings")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("One of commit or branch must be set");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetScmGetBatchFileTaskParams_whenBranchIsSet_shouldBuildParams() {
    GitFileStoreSpec spec =
        GitFileStoreSpec.builder().branch(BRANCH).commitId(null).paths(Arrays.asList(FILE_PATH_1, FILE_PATH_2)).build();

    ScmBatchGetFileTaskParams result = scmGitFileOperationsHelper.getScmGetBatchFileTaskParams(spec, gitConnectorInfo);

    assertThat(result).as("Should return non-null task params").isNotNull();
    assertThat(result.getGetFileTaskParamsPerConnectorList())
        .as("Should contain one connector params entry")
        .hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetScmGetBatchFileTaskParams_whenNoBranchOrCommit_shouldThrowException() {
    GitFileStoreSpec spec =
        GitFileStoreSpec.builder().branch(null).commitId(null).paths(Collections.singletonList(FILE_PATH_1)).build();

    assertThatThrownBy(() -> scmGitFileOperationsHelper.getScmGetBatchFileTaskParams(spec, gitConnectorInfo))
        .as("Should throw when neither branch nor commitId is set")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("One of commit or branch must be set");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToFileContentsDataMap_shouldMapIdentifierToContent() {
    String content1 = "file1 content";
    String content2 = "file2 content";
    Map<GetBatchFileRequestIdentifier, GitFileResponse> responseMap = new HashMap<>();
    responseMap.put(GetBatchFileRequestIdentifier.builder().identifier(FILE_PATH_1).build(),
        GitFileResponse.builder().content(content1).build());
    responseMap.put(GetBatchFileRequestIdentifier.builder().identifier(FILE_PATH_2).build(),
        GitFileResponse.builder().content(content2).build());
    GitFileBatchResponse response =
        GitFileBatchResponse.builder().getBatchFileRequestIdentifierGitFileResponseMap(responseMap).build();

    Map<String, String> result = scmGitFileOperationsHelper.toFileContentsDataMap(response);

    assertThat(result).as("Should contain entries for both file paths").hasSize(2);
    assertThat(result.get(FILE_PATH_1)).as("Should map file1 path to file1 content").isEqualTo(content1);
    assertThat(result.get(FILE_PATH_2)).as("Should map file2 path to file2 content").isEqualTo(content2);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testToFileContentsDataMap_whenEmptyResponse_shouldReturnEmptyMap() {
    GitFileBatchResponse response =
        GitFileBatchResponse.builder().getBatchFileRequestIdentifierGitFileResponseMap(new HashMap<>()).build();

    Map<String, String> result = scmGitFileOperationsHelper.toFileContentsDataMap(response);

    assertThat(result).as("Should return empty map for empty response").isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetScmCgiFetchFilesTaskParams_whenBranchIsSet_shouldBuildTaskParams() {
    GitFileStoreSpec spec =
        GitFileStoreSpec.builder().branch(BRANCH).commitId(null).paths(Arrays.asList(FILE_PATH_1, FILE_PATH_2)).build();

    GitApiTaskParams result = scmGitFileOperationsHelper.getScmCgiFetchFilesTaskParams(spec, gitConnectorInfo);

    assertThat(result).as("Should return non-null task params").isNotNull();
    assertThat(result.getRef()).as("Should set branch as ref").isEqualTo(BRANCH);
    assertThat(result.getPaths())
        .as("Should contain both file paths")
        .containsExactlyInAnyOrder(FILE_PATH_1, FILE_PATH_2);
    assertThat(result.getSha()).as("Should set empty sha when commitId is null").isEmpty();
    assertThat(result.getConnectorDetails()).as("Should set connector details").isEqualTo(connectorDetails);
    assertThat(result.getSlug()).as("Should set slug").isEqualTo(SLUG);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetScmCgiFetchFilesTaskParams_whenCommitIdIsSet_shouldBuildTaskParams() {
    GitFileStoreSpec spec = GitFileStoreSpec.builder()
                                .branch(null)
                                .commitId(COMMIT_ID)
                                .paths(Collections.singletonList(FILE_PATH_1))
                                .build();

    GitApiTaskParams result = scmGitFileOperationsHelper.getScmCgiFetchFilesTaskParams(spec, gitConnectorInfo);

    assertThat(result.getRef()).as("Should set empty ref when branch is null").isEmpty();
    assertThat(result.getSha()).as("Should set commitId as sha").isEqualTo(COMMIT_ID);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetScmCgiFetchFilesTaskParams_whenNoBranchOrCommit_shouldThrowException() {
    GitFileStoreSpec spec =
        GitFileStoreSpec.builder().branch(null).commitId(null).paths(Collections.singletonList(FILE_PATH_1)).build();

    assertThatThrownBy(() -> scmGitFileOperationsHelper.getScmCgiFetchFilesTaskParams(spec, gitConnectorInfo))
        .as("Should throw when neither branch nor commitId is set")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("One of commit or branch must be set");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateBranchOrCommitPresent_whenBranchSet_shouldNotThrow() {
    GitFileStoreSpec spec = GitFileStoreSpec.builder().branch(BRANCH).commitId(null).build();

    scmGitFileOperationsHelper.validateBranchOrCommitPresent(spec);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateBranchOrCommitPresent_whenCommitIdSet_shouldNotThrow() {
    GitFileStoreSpec spec = GitFileStoreSpec.builder().branch(null).commitId(COMMIT_ID).build();

    scmGitFileOperationsHelper.validateBranchOrCommitPresent(spec);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateBranchOrCommitPresent_whenBothNull_shouldThrow() {
    GitFileStoreSpec spec = GitFileStoreSpec.builder().branch(null).commitId(null).build();

    assertThatThrownBy(() -> scmGitFileOperationsHelper.validateBranchOrCommitPresent(spec))
        .as("Should throw when neither branch nor commitId is present")
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("One of commit or branch must be set");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetGitConnectorInfo_shouldBuildInfoFromAmbiance() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(Map.of(
                                "accountId", ACCOUNT_ID, "projectIdentifier", PROJECT_ID, "orgIdentifier", ORG_ID))
                            .build();
    GitFileStoreSpec spec =
        GitFileStoreSpec.builder().connectorRef(CONNECTOR_REF).repoName("specRepo").branch(BRANCH).build();
    when(connectorUtils.getConnectorDetails(any(NGAccess.class), eq(CONNECTOR_REF), eq(true)))
        .thenReturn(connectorDetails);
    when(scmGitRefManager.getScmConnector(connectorDetails, ACCOUNT_ID, "specRepo")).thenReturn(scmConnector);
    when(scmGitProviderHelper.getRepoName(scmConnector)).thenReturn(REPO_NAME);
    when(scmGitProviderHelper.getSlug(scmConnector)).thenReturn(SLUG);

    GitConnectorInfo result = scmGitFileOperationsHelper.getGitConnectorInfo(ambiance, spec);

    assertThat(result.getConnectorRef()).as("Should set connectorRef from spec").isEqualTo(CONNECTOR_REF);
    assertThat(result.getConnectorDetails())
        .as("Should set connectorDetails from connectorUtils")
        .isEqualTo(connectorDetails);
    assertThat(result.getAccountId()).as("Should extract accountId from ambiance").isEqualTo(ACCOUNT_ID);
    assertThat(result.getRepoName()).as("Should set repoName from scmGitProviderHelper").isEqualTo(REPO_NAME);
    assertThat(result.getSlug()).as("Should set slug from scmGitProviderHelper").isEqualTo(SLUG);
    assertThat(result.getConnector()).as("Should set connector from scmGitRefManager").isEqualTo(scmConnector);
  }
}
