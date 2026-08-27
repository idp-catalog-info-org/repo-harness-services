/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scm;

import io.harness.beans.BranchFilterParameters;
import io.harness.beans.RepoFilterParameters;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.gitsync.common.dtos.GitListBranchesResponse;
import io.harness.gitsync.common.dtos.GitListRepositoryResponse;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetPRRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetPRResponseDTO;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.spec.server.ng.v1.model.CreatePullRequest;
import io.harness.utils.ScopeResolutionHelper;

import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;

abstract class AbstractScmApiImpl {
  private static final int DEFAULT_LIST_SIZE = 50;
  private static final int DEFAULT_PAGE = 0;

  private final ScmFacilitatorService scmFacilitatorService;
  private final ScopeInfoService scopeInfoService;
  private final ScopeResolutionHelper scopeResolutionHelper;
  private final ScopeInfo scopeInfo;

  AbstractScmApiImpl(ScmFacilitatorService scmFacilitatorService, ScopeInfoService scopeInfoService,
      ScopeResolutionHelper scopeResolutionHelper, ScopeInfo scopeInfo) {
    this.scmFacilitatorService = scmFacilitatorService;
    this.scopeInfoService = scopeInfoService;
    this.scopeResolutionHelper = scopeResolutionHelper;
    this.scopeInfo = scopeInfo;
  }

  Response createPullRequest(
      CreatePullRequest body, String harnessAccount, String orgIdentifier, String projectIdentifier) {
    Scope scope = Scope.of(scopeResolutionHelper.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    ScmCreatePRResponseDTO scmCreatePRResponseDTO =
        scmFacilitatorService.createPR(ScmCreatePRRequestDTO.builder()
                                           .title(body.getTitle())
                                           .scope(scope)
                                           .connectorRef(body.getConnectorRef())
                                           .repoName(body.getRepoName())
                                           .targetBranch(body.getTargetBranchName())
                                           .sourceBranch(body.getSourceBranchName())
                                           .build());
    return Response
        .ok(GitxScmOpenApiMapper.toCreatePullRequestApiResponse(
            io.harness.gitsync.common.dtos.CreatePRResponse.builder()
                .prNumber(scmCreatePRResponseDTO.getPrNumber())
                .build()))
        .build();
  }

  Response getPullRequest(String harnessAccount, String orgIdentifier, String projectIdentifier, String connectorRef,
      String repoName, Integer prNumber) {
    Scope scope = Scope.of(scopeResolutionHelper.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    ScmGetPRResponseDTO scmGetPRResponseDTO = scmFacilitatorService.getPR(ScmGetPRRequestDTO.builder()
                                                                              .scope(scope)
                                                                              .connectorRef(connectorRef)
                                                                              .repoName(repoName)
                                                                              .prNumber(prNumber)
                                                                              .build());
    return Response.ok(GitxScmOpenApiMapper.toGetPullRequestApiResponse(scmGetPRResponseDTO.getPrDetails())).build();
  }

  Response listBranches(String harnessAccount, String orgIdentifier, String projectIdentifier, String repoName,
      String connectorRef, Integer page, @Max(100) Integer size, String branchNameSearchTerm) {
    int pageNum = page == null ? DEFAULT_PAGE : page;
    int pageSize = size == null ? DEFAULT_LIST_SIZE : size;
    BranchFilterParameters branchFilterParameters =
        BranchFilterParameters.builder().branchName(branchNameSearchTerm).build();
    ScopeInfo resolvedScopeInfo = resolveScopeInfo(harnessAccount, orgIdentifier, projectIdentifier);
    GitListBranchesResponse gitListBranchesResponse =
        scmFacilitatorService.listBranchesV3(harnessAccount, orgIdentifier, projectIdentifier, connectorRef, false,
            null, repoName, PageRequest.builder().pageIndex(pageNum + 1).pageSize(pageSize).build(),
            branchFilterParameters, resolvedScopeInfo, true);
    return Response.ok(GitxScmOpenApiMapper.toGitListBranchesApiResponse(gitListBranchesResponse)).build();
  }

  Response listRepos(String harnessAccount, String orgIdentifier, String projectIdentifier, String connectorRef,
      Integer page, @Max(100) Integer size, String repoNameSearchTerm, String userNameSearchTerm,
      Boolean applyGitXRepoAllowListFilter) {
    int pageNum = page == null ? DEFAULT_PAGE : page;
    int pageSize = size == null ? DEFAULT_LIST_SIZE : size;
    RepoFilterParameters repoFilterParameters =
        RepoFilterParameters.builder()
            .repoName(repoNameSearchTerm)
            .userName(userNameSearchTerm)
            .applyGitXRepoAllowListFilter(applyGitXRepoAllowListFilter != null && applyGitXRepoAllowListFilter)
            .build();
    ScopeInfo resolvedScopeInfo = resolveScopeInfo(harnessAccount, orgIdentifier, projectIdentifier);
    GitListRepositoryResponse gitListRepositoryResponse =
        scmFacilitatorService.listReposV2(harnessAccount, orgIdentifier, projectIdentifier, connectorRef, false, null,
            PageRequest.builder().pageIndex(pageNum).pageSize(pageSize).build(), repoFilterParameters,
            resolvedScopeInfo, true);
    return Response.ok(GitxScmOpenApiMapper.toGitListRepositoriesApiResponse(gitListRepositoryResponse)).build();
  }

  private ScopeInfo resolveScopeInfo(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (scopeInfo != null) {
      return scopeInfo;
    }
    return scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
  }
}
