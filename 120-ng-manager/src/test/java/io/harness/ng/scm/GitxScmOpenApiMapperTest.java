/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scm;

import static io.harness.ng.core.Status.SUCCESS;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.gitsync.common.dtos.GitBranchDetailsDTO;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitListBranchesResponse;
import io.harness.gitsync.common.dtos.GitListRepositoryResponse;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.PaginationDetails;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.GitListBranchesApiResponse;
import io.harness.spec.server.ng.v1.model.GitListRepositoriesApiResponse;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitxScmOpenApiMapperTest extends CategoryTest {
  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToGitListBranchesApiResponse() {
    GitListBranchesResponse gitListBranchesResponse =
        GitListBranchesResponse.builder()
            .gitBranchesResponse(GitBranchesResponseDTO.builder()
                                     .branches(java.util.List.of(GitBranchDetailsDTO.builder().name("main").build()))
                                     .defaultBranch(GitBranchDetailsDTO.builder().name("main").build())
                                     .build())
            .paginationDetails(PaginationDetails.builder().nextPage(1).nextPageUrl("next-url").build())
            .connectorType(ConnectorType.GITHUB)
            .build();

    GitListBranchesApiResponse response = GitxScmOpenApiMapper.toGitListBranchesApiResponse(gitListBranchesResponse);

    assertThat(response.getStatus()).isEqualTo(SUCCESS.name());
    assertThat(response.getData().getGitBranchesResponse().getBranches()).hasSize(1);
    assertThat(response.getData().getGitBranchesResponse().getBranches().get(0).getName()).isEqualTo("main");
    assertThat(response.getData().getPaginationDetails().getNextPage()).isEqualTo(1);
    assertThat(response.getData().getPaginationDetails().getNextPageUrl()).isEqualTo("next-url");
    assertThat(response.getData().getConnectorType()).isEqualTo("GITHUB");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testToGitListRepositoriesApiResponse() {
    GitListRepositoryResponse gitListRepositoryResponse =
        GitListRepositoryResponse.builder()
            .gitRepositoryResponseList(java.util.List.of(GitRepositoryResponseDTO.builder().name("repo-one").build()))
            .paginationDetails(PaginationDetails.builder().nextPage(2).nextPageUrl("next-url").build())
            .build();

    GitListRepositoriesApiResponse response =
        GitxScmOpenApiMapper.toGitListRepositoriesApiResponse(gitListRepositoryResponse);

    assertThat(response.getStatus()).isEqualTo(SUCCESS.name());
    assertThat(response.getData().getGitRepositoryResponseList()).hasSize(1);
    assertThat(response.getData().getGitRepositoryResponseList().get(0).getName()).isEqualTo("repo-one");
    assertThat(response.getData().getPaginationDetails().getNextPage()).isEqualTo(2);
    assertThat(response.getData().getPaginationDetails().getNextPageUrl()).isEqualTo("next-url");
  }
}
