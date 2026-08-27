/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.gitmetadata.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.execution.gitmetadata.PipelineExecutionGitMetadata;
import io.harness.pms.pipeline.PMSPipelineListBranchesResponse;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface PipelineExecutionGitMetadataService {
  /**
   * Upsert git metadata with branch information
   * @param scopeInfo Contains account, org, project identifiers and uniqueId
   * @param pipelineIdentifier Pipeline identifier
   * @param repoName Repository name
   * @param branch Branch name
   * @return Updated PipelineExecutionGitMetadata
   */
  PipelineExecutionGitMetadata upsert(ScopeInfo scopeInfo, String pipelineIdentifier, String repoName, String branch);

  /**
   * Get list of all branches for a repository
   * @param scopeInfo Contains account, org, project identifiers and uniqueId
   * @param pipelineIdentifier Pipeline identifier
   * @param repoName Repository name
   * @return Response containing list of branch names
   */
  PMSPipelineListBranchesResponse findUniqueListOfBranches(
      ScopeInfo scopeInfo, String pipelineIdentifier, String repoName);

  /**
   * Get list of all repositories for a pipeline
   * @param scopeInfo Contains account, org, project identifiers and uniqueId
   * @param pipelineIdentifier Pipeline identifier
   * @return Response containing list of repository names
   */
  PMSPipelineListRepoResponse findUniqueListOfRepositories(ScopeInfo scopeInfo, String pipelineIdentifier);

  /**
   * Delete execution git metadata for a pipeline
   *
   * @param accountIdentifier                         Account identifier
   * @param orgIdentifier                             Organization identifier
   * @param projectIdentifier                         Project identifier
   * @param pipelineIdentifier                        Pipeline identifier
   * @param retainPipelineExecutionDetailsAfterDelete retain the execution details or not after delete
   */
  void deletePipelineGitMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean retainPipelineExecutionDetailsAfterDelete, String parentUniqueId);
}
