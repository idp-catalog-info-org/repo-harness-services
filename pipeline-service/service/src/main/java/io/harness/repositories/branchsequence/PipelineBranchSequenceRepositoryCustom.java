/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.branchsequence;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Custom repository interface for PipelineBranchSequence with atomic operations.
 */
@OwnedBy(CI)
public interface PipelineBranchSequenceRepositoryCustom {
  /**
   * Atomically increments the sequence counter for a specific branch.
   *
   * <p>If no record exists for this pipeline + repo + branch combination,
   * a new record is created with sequenceId = 1.
   *
   * <p>Uses MongoDB findAndModify with upsert for atomic operation.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param normalizedRepoUrl the normalized repository URL (host/owner/repo, lowercase)
   * @param branch the branch name (without refs/heads/ prefix)
   * @param parentUniqueId the parent scope's uniqueId (project's uniqueId), may be null
   * @return the updated PipelineBranchSequence with the new sequenceId
   */
  PipelineBranchSequence incrementAndGet(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String normalizedRepoUrl, String branch, @Nullable String parentUniqueId);

  /**
   * Gets the current sequence for a specific branch without incrementing.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param normalizedRepoUrl the normalized repository URL
   * @param branch the branch name
   * @return the current sequence, or empty if no record exists
   */
  Optional<PipelineBranchSequence> getBranchSequence(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String normalizedRepoUrl, String branch);

  /**
   * Deletes all branch sequence records for a specific pipeline.
   *
   * <p>Called when a pipeline is deleted to clean up associated metadata.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @return the number of records deleted
   */
  long deleteAllForPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  /**
   * Gets all branch sequences for a specific pipeline.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @return list of all branch sequences for the pipeline
   */
  List<PipelineBranchSequence> getAllForPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  /**
   * Deletes a specific branch sequence record.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param normalizedRepoUrl the normalized repository URL
   * @param branch the branch name
   * @return true if a record was deleted, false if no record existed
   */
  boolean deleteBranchSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String normalizedRepoUrl, String branch);

  /**
   * Sets the sequence counter to a specific value for a branch.
   *
   * <p>If no record exists for this pipeline + repo + branch combination,
   * a new record is created with the specified sequenceId.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param normalizedRepoUrl the normalized repository URL
   * @param branch the branch name
   * @param sequenceId the sequence value to set
   * @return the updated PipelineBranchSequence with the new sequenceId
   */
  PipelineBranchSequence setSequenceId(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String normalizedRepoUrl, String branch, long sequenceId);
}
