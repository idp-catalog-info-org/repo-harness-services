/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scm;

import static io.harness.ng.core.Status.SUCCESS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.common.dtos.GitBranchDetailsDTO;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.PRDetailsDTO;
import io.harness.ng.core.CorrelationContext;
import io.harness.spec.server.ng.v1.model.CreatePullRequestApiResponse;
import io.harness.spec.server.ng.v1.model.CreatePullRequestResponse;
import io.harness.spec.server.ng.v1.model.GetPullRequestApiResponse;
import io.harness.spec.server.ng.v1.model.GitBranchDetails;
import io.harness.spec.server.ng.v1.model.GitBranchPaginationDetails;
import io.harness.spec.server.ng.v1.model.GitBranches;
import io.harness.spec.server.ng.v1.model.GitListBranches;
import io.harness.spec.server.ng.v1.model.GitListBranchesApiResponse;
import io.harness.spec.server.ng.v1.model.GitListRepositories;
import io.harness.spec.server.ng.v1.model.GitListRepositoriesApiResponse;
import io.harness.spec.server.ng.v1.model.GitRepositoryDetails;
import io.harness.spec.server.ng.v1.model.GitRepositoryPaginationDetails;
import io.harness.spec.server.ng.v1.model.PullRequestDetails;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@UtilityClass
public class GitxScmOpenApiMapper {
  public static GitListBranchesApiResponse toGitListBranchesApiResponse(
      io.harness.gitsync.common.dtos.GitListBranchesResponse gitListBranchesResponse) {
    GitListBranchesApiResponse response = new GitListBranchesApiResponse();
    response.setStatus(SUCCESS.name());
    response.setCorrelationId(CorrelationContext.getCorrelationId());
    response.setData(toGitListBranches(gitListBranchesResponse));
    return response;
  }

  public static CreatePullRequestApiResponse toCreatePullRequestApiResponse(
      io.harness.gitsync.common.dtos.CreatePRResponse createPRResponse) {
    CreatePullRequestApiResponse response = new CreatePullRequestApiResponse();
    response.setStatus(SUCCESS.name());
    response.setCorrelationId(CorrelationContext.getCorrelationId());
    CreatePullRequestResponse data = new CreatePullRequestResponse();
    data.setPrNumber(createPRResponse.getPrNumber());
    response.setData(data);
    return response;
  }

  public static GetPullRequestApiResponse toGetPullRequestApiResponse(PRDetailsDTO prDetails) {
    GetPullRequestApiResponse response = new GetPullRequestApiResponse();
    response.setStatus(SUCCESS.name());
    response.setCorrelationId(CorrelationContext.getCorrelationId());
    if (prDetails != null) {
      PullRequestDetails data = new PullRequestDetails();
      data.setNumber(prDetails.getNumber());
      data.setTitle(prDetails.getTitle());
      data.setSourceBranch(prDetails.getSourceBranch());
      data.setTargetBranch(prDetails.getTargetBranch());
      data.setHeadSha(prDetails.getHeadSha());
      data.setBaseSha(prDetails.getBaseSha());
      data.setLink(prDetails.getLink());
      data.setClosed(prDetails.isClosed());
      data.setMerged(prDetails.isMerged());
      response.setData(data);
    }
    return response;
  }

  public static GitListRepositoriesApiResponse toGitListRepositoriesApiResponse(
      io.harness.gitsync.common.dtos.GitListRepositoryResponse gitListRepositoryResponse) {
    GitListRepositoriesApiResponse response = new GitListRepositoriesApiResponse();
    response.setStatus(SUCCESS.name());
    response.setCorrelationId(CorrelationContext.getCorrelationId());
    response.setData(toGitListRepositories(gitListRepositoryResponse));
    return response;
  }

  private static GitListRepositories toGitListRepositories(
      io.harness.gitsync.common.dtos.GitListRepositoryResponse gitListRepositoryResponse) {
    if (gitListRepositoryResponse == null) {
      return new GitListRepositories();
    }
    GitListRepositories response = new GitListRepositories();
    if (gitListRepositoryResponse.getGitRepositoryResponseList() != null) {
      List<GitRepositoryDetails> repositories = gitListRepositoryResponse.getGitRepositoryResponseList()
                                                    .stream()
                                                    .map(GitxScmOpenApiMapper::toGitRepositoryDetails)
                                                    .collect(Collectors.toList());
      response.setGitRepositoryResponseList(repositories);
    }
    if (gitListRepositoryResponse.getPaginationDetails() != null) {
      response.setPaginationDetails(
          new GitRepositoryPaginationDetails()
              .nextPage(gitListRepositoryResponse.getPaginationDetails().getNextPage())
              .nextPageUrl(gitListRepositoryResponse.getPaginationDetails().getNextPageUrl()));
    }
    return response;
  }

  private static GitRepositoryDetails toGitRepositoryDetails(GitRepositoryResponseDTO repository) {
    if (repository == null) {
      return null;
    }
    return new GitRepositoryDetails().name(repository.getName());
  }

  private static GitListBranches toGitListBranches(
      io.harness.gitsync.common.dtos.GitListBranchesResponse gitListBranchesResponse) {
    if (gitListBranchesResponse == null) {
      return new GitListBranches();
    }
    GitListBranches response = new GitListBranches();
    if (gitListBranchesResponse.getGitBranchesResponse() != null) {
      response.setGitBranchesResponse(toGitBranches(gitListBranchesResponse.getGitBranchesResponse()));
    }
    if (gitListBranchesResponse.getPaginationDetails() != null) {
      response.setPaginationDetails(new GitBranchPaginationDetails()
                                        .nextPage(gitListBranchesResponse.getPaginationDetails().getNextPage())
                                        .nextPageUrl(gitListBranchesResponse.getPaginationDetails().getNextPageUrl()));
    }
    if (gitListBranchesResponse.getConnectorType() != null) {
      response.setConnectorType(gitListBranchesResponse.getConnectorType().name());
    }
    return response;
  }

  private static GitBranches toGitBranches(GitBranchesResponseDTO gitBranches) {
    if (gitBranches == null) {
      return new GitBranches().branches(Collections.emptyList());
    }
    List<GitBranchDetails> branches = gitBranches.getBranches() == null
        ? Collections.emptyList()
        : gitBranches.getBranches().stream().map(GitxScmOpenApiMapper::toGitBranchDetails).collect(Collectors.toList());
    return new GitBranches().branches(branches).defaultBranch(toGitBranchDetails(gitBranches.getDefaultBranch()));
  }

  private static GitBranchDetails toGitBranchDetails(GitBranchDetailsDTO branch) {
    if (branch == null) {
      return null;
    }
    return new GitBranchDetails().name(branch.getName());
  }
}
