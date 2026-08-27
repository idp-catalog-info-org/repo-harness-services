/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.gitmetadata.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.execution.gitmetadata.PipelineExecutionGitMetadata;
import io.harness.pms.pipeline.PMSPipelineListBranchesResponse;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.repositories.executiongitmetadata.PipelineExecutionGitMetadataRepository;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineExecutionGitMetadataServiceImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account1";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "project1";
  private static final String PARENT_UNIQUE_ID = "parentId1";
  private static final String PIPELINE_ID = "pipeline1";
  private static final String REPO_NAME = "repo1";
  private static final String BRANCH = "main";

  @Mock private PipelineExecutionGitMetadataRepository gitMetadataRepository;
  private PipelineExecutionGitMetadataServiceImpl gitMetadataService;
  private ScopeInfo scopeInfo;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    gitMetadataService = new PipelineExecutionGitMetadataServiceImpl(gitMetadataRepository);
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_ID)
                    .projectIdentifier(PROJECT_ID)
                    .uniqueId(PARENT_UNIQUE_ID)
                    .build();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testUpsert() {
    PipelineExecutionGitMetadata metadata = PipelineExecutionGitMetadata.builder()
                                                .accountIdentifier(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .parentUniqueId(PARENT_UNIQUE_ID)
                                                .pipelineIdentifier(PIPELINE_ID)
                                                .repoName(REPO_NAME)
                                                .branch(Lists.newArrayList(BRANCH))
                                                .build();

    when(gitMetadataRepository.upsert(scopeInfo, PIPELINE_ID, REPO_NAME, BRANCH)).thenReturn(metadata);

    PipelineExecutionGitMetadata result = gitMetadataService.upsert(scopeInfo, PIPELINE_ID, REPO_NAME, BRANCH);

    assertThat(result).isEqualTo(metadata);
    verify(gitMetadataRepository).upsert(scopeInfo, PIPELINE_ID, REPO_NAME, BRANCH);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFindUniqueListOfBranches() {
    List<String> expectedBranches = Lists.newArrayList(BRANCH);

    when(gitMetadataRepository.findUniqueListOfBranches(scopeInfo, PIPELINE_ID, REPO_NAME))
        .thenReturn(expectedBranches);

    PMSPipelineListBranchesResponse response =
        gitMetadataService.findUniqueListOfBranches(scopeInfo, PIPELINE_ID, REPO_NAME);

    assertThat(response).isNotNull();
    assertThat(response.getBranches()).containsExactly(BRANCH);
    verify(gitMetadataRepository).findUniqueListOfBranches(scopeInfo, PIPELINE_ID, REPO_NAME);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFindUniqueListOfBranchesWhenNoneFound() {
    when(gitMetadataRepository.findUniqueListOfBranches(scopeInfo, PIPELINE_ID, REPO_NAME))
        .thenReturn(Lists.newArrayList());

    PMSPipelineListBranchesResponse response =
        gitMetadataService.findUniqueListOfBranches(scopeInfo, PIPELINE_ID, REPO_NAME);

    assertThat(response).isNotNull();
    assertThat(response.getBranches()).isEmpty();
    verify(gitMetadataRepository).findUniqueListOfBranches(scopeInfo, PIPELINE_ID, REPO_NAME);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFindUniqueListOfRepositories() {
    List<String> repositories = Lists.newArrayList(REPO_NAME);
    when(gitMetadataRepository.findUniqueListOfRepositories(scopeInfo, PIPELINE_ID)).thenReturn(repositories);

    PMSPipelineListRepoResponse response = gitMetadataService.findUniqueListOfRepositories(scopeInfo, PIPELINE_ID);

    assertThat(response).isNotNull();
    assertThat(response.getRepositories()).containsExactly(REPO_NAME);
    verify(gitMetadataRepository).findUniqueListOfRepositories(scopeInfo, PIPELINE_ID);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFindUniqueListOfRepositoriesWhenNoneFound() {
    when(gitMetadataRepository.findUniqueListOfRepositories(scopeInfo, PIPELINE_ID)).thenReturn(Lists.newArrayList());

    PMSPipelineListRepoResponse response = gitMetadataService.findUniqueListOfRepositories(scopeInfo, PIPELINE_ID);

    assertThat(response).isNotNull();
    assertThat(response.getRepositories()).isEmpty();
    verify(gitMetadataRepository).findUniqueListOfRepositories(scopeInfo, PIPELINE_ID);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testDeletePipelineGitMetadata() {
    gitMetadataService.deletePipelineGitMetadata(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, true, null);
    verify(gitMetadataRepository, never())
        .deleteGitMetadataForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null);

    gitMetadataService.deletePipelineGitMetadata(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, false, null);
    verify(gitMetadataRepository).deleteGitMetadataForPipeline(ACCOUNT_ID, ORG_ID, PROJECT_ID, PIPELINE_ID, null);
  }
}
