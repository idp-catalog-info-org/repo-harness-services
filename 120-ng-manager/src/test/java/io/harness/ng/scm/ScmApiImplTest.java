/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scm;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.BranchFilterParameters;
import io.harness.beans.RepoFilterParameters;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.gitsync.common.dtos.GitBranchDetailsDTO;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitListBranchesResponse;
import io.harness.gitsync.common.dtos.GitListRepositoryResponse;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.PaginationDetails;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.CreatePullRequest;
import io.harness.spec.server.ng.v1.model.CreatePullRequestApiResponse;
import io.harness.spec.server.ng.v1.model.GitListBranchesApiResponse;
import io.harness.spec.server.ng.v1.model.GitListRepositoriesApiResponse;
import io.harness.utils.ScopeResolutionHelper;

import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ScmApiImplTest extends CategoryTest {
  private static final String ACCOUNT_IDENTIFIER = "accountId";
  private static final String ORG_IDENTIFIER = "orgId";
  private static final String PROJECT_IDENTIFIER = "projectId";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String REPO_NAME = "testRepo";

  @Mock private ScmFacilitatorService scmFacilitatorService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;

  private ScmApiImpl scmApiImpl;
  private ScopeInfo scopeInfo;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_IDENTIFIER)
                    .orgIdentifier(ORG_IDENTIFIER)
                    .projectIdentifier(PROJECT_IDENTIFIER)
                    .scopeType(ScopeLevel.PROJECT)
                    .uniqueId("uniqueId")
                    .build();
    scmApiImpl = new ScmApiImpl(scmFacilitatorService, scopeInfoService, scopeResolutionHelper, scopeInfo);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListAccountBranchesUsesPaginatedService() {
    GitListBranchesResponse serviceResponse =
        GitListBranchesResponse.builder()
            .gitBranchesResponse(GitBranchesResponseDTO.builder()
                                     .branches(java.util.List.of(GitBranchDetailsDTO.builder().name("main").build()))
                                     .build())
            .paginationDetails(PaginationDetails.builder().nextPage(2).build())
            .connectorType(ConnectorType.GITHUB)
            .build();
    when(scmFacilitatorService.listBranchesV3(eq(ACCOUNT_IDENTIFIER), eq(null), eq(null), eq(CONNECTOR_REF), eq(false),
             eq(null), eq(REPO_NAME), any(PageRequest.class), any(BranchFilterParameters.class), eq(scopeInfo),
             eq(true)))
        .thenReturn(serviceResponse);

    Response response = scmApiImpl.listAccountBranches(REPO_NAME, ACCOUNT_IDENTIFIER, CONNECTOR_REF, 1, 25, "feature");

    assertThat(response.getStatus()).isEqualTo(200);
    GitListBranchesApiResponse entity = (GitListBranchesApiResponse) response.getEntity();
    assertThat(entity.getData().getGitBranchesResponse().getBranches()).hasSize(1);
    assertThat(entity.getData().getPaginationDetails().getNextPage()).isEqualTo(2);

    ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
    ArgumentCaptor<BranchFilterParameters> branchFilterCaptor = ArgumentCaptor.forClass(BranchFilterParameters.class);
    verify(scmFacilitatorService)
        .listBranchesV3(eq(ACCOUNT_IDENTIFIER), eq(null), eq(null), eq(CONNECTOR_REF), eq(false), eq(null),
            eq(REPO_NAME), pageRequestCaptor.capture(), branchFilterCaptor.capture(), eq(scopeInfo), eq(true));
    assertThat(pageRequestCaptor.getValue().getPageIndex()).isEqualTo(2);
    assertThat(pageRequestCaptor.getValue().getPageSize()).isEqualTo(25);
    assertThat(branchFilterCaptor.getValue().getBranchName()).isEqualTo("feature");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testListAccountReposUsesPaginatedService() {
    GitListRepositoryResponse serviceResponse =
        GitListRepositoryResponse.builder()
            .gitRepositoryResponseList(java.util.List.of(GitRepositoryResponseDTO.builder().name("repo-one").build()))
            .paginationDetails(PaginationDetails.builder().nextPage(1).nextPageUrl("next-url").build())
            .build();
    when(scmFacilitatorService.listReposV2(eq(ACCOUNT_IDENTIFIER), eq(null), eq(null), eq(CONNECTOR_REF), eq(false),
             eq(null), any(PageRequest.class), any(RepoFilterParameters.class), eq(scopeInfo), eq(true)))
        .thenReturn(serviceResponse);

    Response response = scmApiImpl.listAccountRepos(ACCOUNT_IDENTIFIER, CONNECTOR_REF, 0, 25, "repo", "user", true);

    assertThat(response.getStatus()).isEqualTo(200);
    GitListRepositoriesApiResponse entity = (GitListRepositoriesApiResponse) response.getEntity();
    assertThat(entity.getData().getGitRepositoryResponseList()).hasSize(1);
    assertThat(entity.getData().getGitRepositoryResponseList().get(0).getName()).isEqualTo("repo-one");
    assertThat(entity.getData().getPaginationDetails().getNextPage()).isEqualTo(1);

    ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
    ArgumentCaptor<RepoFilterParameters> repoFilterCaptor = ArgumentCaptor.forClass(RepoFilterParameters.class);
    verify(scmFacilitatorService)
        .listReposV2(eq(ACCOUNT_IDENTIFIER), eq(null), eq(null), eq(CONNECTOR_REF), eq(false), eq(null),
            pageRequestCaptor.capture(), repoFilterCaptor.capture(), eq(scopeInfo), eq(true));
    assertThat(pageRequestCaptor.getValue().getPageIndex()).isEqualTo(0);
    assertThat(pageRequestCaptor.getValue().getPageSize()).isEqualTo(25);
    assertThat(repoFilterCaptor.getValue().getRepoName()).isEqualTo("repo");
    assertThat(repoFilterCaptor.getValue().getUserName()).isEqualTo("user");
    assertThat(repoFilterCaptor.getValue().isApplyGitXRepoAllowListFilter()).isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCreateAccountPullRequest() {
    when(scopeResolutionHelper.getScopeInfo(ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfo);
    when(scmFacilitatorService.createPR(any(ScmCreatePRRequestDTO.class)))
        .thenReturn(ScmCreatePRResponseDTO.builder().prNumber(42).build());

    CreatePullRequest createPullRequest = new CreatePullRequest();
    createPullRequest.setConnectorRef(CONNECTOR_REF);
    createPullRequest.setRepoName(REPO_NAME);
    createPullRequest.setTitle("title");
    createPullRequest.setSourceBranchName("source");
    createPullRequest.setTargetBranchName("target");

    Response response = scmApiImpl.createAccountPullRequest(createPullRequest, ACCOUNT_IDENTIFIER);

    assertThat(response.getStatus()).isEqualTo(200);
    CreatePullRequestApiResponse entity = (CreatePullRequestApiResponse) response.getEntity();
    assertThat(entity.getData().getPrNumber()).isEqualTo(42);

    ArgumentCaptor<ScmCreatePRRequestDTO> requestCaptor = ArgumentCaptor.forClass(ScmCreatePRRequestDTO.class);
    verify(scmFacilitatorService).createPR(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getScope()).isEqualTo(Scope.of(scopeInfo));
    assertThat(requestCaptor.getValue().getTitle()).isEqualTo("title");
  }
}
