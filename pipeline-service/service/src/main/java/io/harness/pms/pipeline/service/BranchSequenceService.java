/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.pipeline.branchsequence.BranchSequenceResult;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Service for managing branch-scoped build sequence IDs.
 *
 * <p>Provides operations to increment and retrieve branch sequence counters
 * that enable the {@code <+pipeline.branchSeqId>} expression.
 *
 * @see <a href="https://harness.atlassian.net/browse/CI-19987">CI-19987</a>
 */
@OwnedBy(CI)
public interface BranchSequenceService {
  /**
   * Increments and returns the branch sequence counter.
   *
   * <p>This is the primary method called during plan creation for trigger-based builds.
   * It atomically increments the counter for the specific pipeline + repo + branch combination.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param repoUrl the repository URL (will be normalized internally)
   * @param branch the branch name (will be normalized internally)
   * @param parentUniqueId the parent scope's uniqueId (project's uniqueId), may be null
   * @return the new sequence number (1 for first build, incrementing thereafter)
   */
  long incrementBranchSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String repoUrl, String branch, @Nullable String parentUniqueId);

  /**
   * Gets the current branch sequence without incrementing.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param repoUrl the repository URL (will be normalized internally)
   * @param branch the branch name (will be normalized internally)
   * @return the current sequence, or empty if no builds have occurred for this branch
   */
  Optional<Long> getBranchSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String repoUrl, String branch);

  /**
   * Extracts branch and repo URL from TriggerPayload, increments the sequence, and returns full result.
   *
   * <p>This is the primary method for trigger-based builds. It handles extraction of branch and
   * repo URL from various webhook payload types (Push, PR, Branch, Release) and returns
   * the sequence ID along with normalized values for setting on ExecutionMetadata.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param triggerPayload the trigger payload containing branch and repo information
   * @param parentUniqueId the parent scope's uniqueId (project's uniqueId), may be null
   * @return BranchSequenceResult with sequence ID and normalized values, or null if not applicable
   */
  @Nullable
  BranchSequenceResult incrementBranchSequenceFromTriggerPayload(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, TriggerPayload triggerPayload,
      @Nullable String parentUniqueId);

  /**
   * Deletes all branch sequence records for a pipeline.
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
   * Gets all branch sequences for a pipeline.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @return list of all branch sequences
   */
  List<PipelineBranchSequence> getAllForPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier);

  /**
   * Deletes a specific branch sequence record.
   *
   * <p>The repoUrl will be normalized internally before deletion.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param repoUrl the repository URL (will be normalized internally)
   * @param branch the branch name (will be normalized internally)
   * @return true if a record was deleted, false if no record existed
   */
  boolean deleteBranchSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String repoUrl, String branch);

  /**
   * Sets the sequence counter to a specific value for a branch.
   *
   * <p>The repoUrl will be normalized internally. If no record exists for this
   * pipeline + repo + branch combination, a new record is created.
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param repoUrl the repository URL (will be normalized internally)
   * @param branch the branch name (will be normalized internally)
   * @param sequenceId the sequence value to set (must be positive)
   * @return the updated branch sequence, or null if normalization fails
   */
  @Nullable
  PipelineBranchSequence setBranchSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String repoUrl, String branch, long sequenceId);

  /**
   * Extracts branch and repo info from processed YAML and increments the sequence.
   *
   * <p>This method supports manual execution with branch selection by parsing the pipeline's
   * codebase configuration from the processed YAML. It looks for:
   * <ul>
   *   <li>Branch: from {@code pipeline.properties.ci.codebase.build.spec.branch}</li>
   *   <li>Repo URL: from trigger payload if available, otherwise uses connector ref as identifier</li>
   * </ul>
   *
   * <p><b>Limitations:</b> When repo URL is not available from trigger payload, the connector ref
   * is used as a repo identifier. This means:
   * <ul>
   *   <li>Same repo via different connectors may get different counters</li>
   *   <li>Account-level connectors used for multiple repos will share counters (incorrect)</li>
   * </ul>
   *
   * @param accountIdentifier the account identifier
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param pipelineIdentifier the pipeline identifier
   * @param processedYaml the processed/resolved pipeline YAML
   * @param triggerPayload optional trigger payload that may contain repo URL
   * @param parentUniqueId the parent scope's uniqueId (project's uniqueId), may be null
   * @return BranchSequenceResult with sequence ID and normalized values, or null if not applicable
   */
  @Nullable
  BranchSequenceResult incrementBranchSequenceFromProcessedYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String processedYaml,
      @Nullable TriggerPayload triggerPayload, @Nullable String parentUniqueId);
}
