/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.gitmetadata.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.engine.executions.gitmetadata.service.PipelineExecutionGitMetadataService;
import io.harness.execution.gitmetadata.PipelineExecutionGitMetadata;
import io.harness.pms.pipeline.PMSPipelineListBranchesResponse;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.repositories.executiongitmetadata.PipelineExecutionGitMetadataRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Singleton
public class PipelineExecutionGitMetadataServiceImpl implements PipelineExecutionGitMetadataService {
  private final PipelineExecutionGitMetadataRepository gitMetadataRepository;

  @Inject
  public PipelineExecutionGitMetadataServiceImpl(PipelineExecutionGitMetadataRepository gitMetadataRepository) {
    this.gitMetadataRepository = gitMetadataRepository;
  }

  @Override
  public PipelineExecutionGitMetadata upsert(
      ScopeInfo scopeInfo, String pipelineIdentifier, String repoName, String branch) {
    return gitMetadataRepository.upsert(scopeInfo, pipelineIdentifier, repoName, branch);
  }

  @Override
  public PMSPipelineListBranchesResponse findUniqueListOfBranches(
      ScopeInfo scopeInfo, String pipelineIdentifier, String repoName) {
    List<String> branches = gitMetadataRepository.findUniqueListOfBranches(scopeInfo, pipelineIdentifier, repoName);
    return PMSPipelineListBranchesResponse.builder().branches(branches).build();
  }

  @Override
  public PMSPipelineListRepoResponse findUniqueListOfRepositories(ScopeInfo scopeInfo, String pipelineIdentifier) {
    List<String> repositories = gitMetadataRepository.findUniqueListOfRepositories(scopeInfo, pipelineIdentifier);
    return PMSPipelineListRepoResponse.builder().repositories(repositories).build();
  }

  @Override
  public void deletePipelineGitMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean retainPipelineExecutionDetailsAfterDelete, String parentUniqueId) {
    if (!retainPipelineExecutionDetailsAfterDelete) {
      gitMetadataRepository.deleteGitMetadataForPipeline(
          accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, parentUniqueId);
    }
  }
}
