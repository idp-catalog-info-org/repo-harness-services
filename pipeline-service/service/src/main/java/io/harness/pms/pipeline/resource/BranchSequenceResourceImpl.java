/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.resource;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.BranchSequenceDTO;
import io.harness.pms.pipeline.BranchSequenceResource;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence;
import io.harness.pms.pipeline.branchsequence.RepoUrlNormalizer;
import io.harness.pms.pipeline.service.BranchSequenceService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the Branch Sequence REST API.
 */
@OwnedBy(CI)
@PipelineServiceAuth
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class BranchSequenceResourceImpl implements BranchSequenceResource {
  private final BranchSequenceService branchSequenceService;
  private final AccessControlClient accessControlClient;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  /**
   * Checks if the branch sequence feature flag is enabled, throws exception if not.
   */
  private void checkFeatureFlagEnabled(String accountIdentifier) {
    if (!pmsFeatureFlagHelper.isEnabled(accountIdentifier, FeatureName.CI_ENABLE_BRANCH_SEQUENCE_ID)) {
      throw new InvalidRequestException(
          "Branch sequence feature is not enabled. Please enable the CI_ENABLE_BRANCH_SEQUENCE_ID feature flag.");
    }
  }

  @Override
  public ResponseDTO<List<BranchSequenceDTO>> listBranchSequences(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    // Check if feature flag is enabled
    checkFeatureFlagEnabled(accountIdentifier);

    // Check pipeline view permission
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_VIEW);

    log.info("Listing branch sequences for pipeline={}, account={}, org={}, project={}", pipelineIdentifier,
        accountIdentifier, orgIdentifier, projectIdentifier);

    List<PipelineBranchSequence> sequences = branchSequenceService.getAllForPipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);

    List<BranchSequenceDTO> dtos = sequences.stream().map(this::toDTO).collect(Collectors.toList());

    return ResponseDTO.newResponse(dtos);
  }

  @Override
  public ResponseDTO<BranchSequenceDTO> getBranchSequence(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String repoUrl, String branch) {
    // Check if feature flag is enabled
    checkFeatureFlagEnabled(accountIdentifier);

    // Check pipeline view permission
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_VIEW);

    log.info("Getting branch sequence for pipeline={}, branch={}, repo={}", pipelineIdentifier, branch, repoUrl);

    Optional<Long> sequenceOpt = branchSequenceService.getBranchSequence(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, repoUrl, branch);

    if (sequenceOpt.isEmpty()) {
      throw new EntityNotFoundException(String.format(
          "No branch sequence found for pipeline=%s, branch=%s, repo=%s", pipelineIdentifier, branch, repoUrl));
    }

    // Normalize the values before returning in DTO
    String normalizedRepoUrl = RepoUrlNormalizer.normalize(repoUrl);
    String normalizedBranch = RepoUrlNormalizer.normalizeBranch(branch);

    BranchSequenceDTO dto = BranchSequenceDTO.builder()
                                .branch(normalizedBranch)
                                .normalizedRepoUrl(normalizedRepoUrl)
                                .sequenceId(sequenceOpt.get())
                                .build();

    return ResponseDTO.newResponse(dto);
  }

  @Override
  public ResponseDTO<Long> deleteBranchSequences(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    // Check if feature flag is enabled
    checkFeatureFlagEnabled(accountIdentifier);

    // Check pipeline edit/delete permission
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_DELETE);

    log.info("Deleting all branch sequences for pipeline={}, account={}, org={}, project={}", pipelineIdentifier,
        accountIdentifier, orgIdentifier, projectIdentifier);

    long deletedCount = branchSequenceService.deleteAllForPipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);

    log.info("Deleted {} branch sequence records for pipeline={}", deletedCount, pipelineIdentifier);

    return ResponseDTO.newResponse(deletedCount);
  }

  @Override
  public ResponseDTO<Boolean> deleteBranchSequence(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String repoUrl, String branch) {
    // Check if feature flag is enabled
    checkFeatureFlagEnabled(accountIdentifier);

    // Check pipeline edit/delete permission
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_DELETE);

    log.info("Deleting branch sequence for pipeline={}, branch={}, repo={}", pipelineIdentifier, branch, repoUrl);

    boolean deleted = branchSequenceService.deleteBranchSequence(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, repoUrl, branch);

    if (!deleted) {
      throw new EntityNotFoundException(String.format(
          "No branch sequence found for pipeline=%s, branch=%s, repo=%s", pipelineIdentifier, branch, repoUrl));
    }

    return ResponseDTO.newResponse(deleted);
  }

  @Override
  public ResponseDTO<BranchSequenceDTO> setBranchSequence(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String repoUrl, String branch, Long sequenceId) {
    // Check if feature flag is enabled
    checkFeatureFlagEnabled(accountIdentifier);

    // Check pipeline edit permission
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_EDIT);

    log.info("Setting branch sequence for pipeline={}, branch={}, repo={}, sequenceId={}", pipelineIdentifier, branch,
        repoUrl, sequenceId);

    PipelineBranchSequence result = branchSequenceService.setBranchSequence(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, repoUrl, branch, sequenceId);

    if (result == null) {
      throw new InvalidRequestException(
          String.format("Failed to set branch sequence. Invalid repo URL '%s' or branch '%s'", repoUrl, branch));
    }

    return ResponseDTO.newResponse(toDTO(result));
  }

  /**
   * Converts entity to DTO.
   */
  private BranchSequenceDTO toDTO(PipelineBranchSequence entity) {
    return BranchSequenceDTO.builder()
        .normalizedRepoUrl(entity.getNormalizedRepoUrl())
        .branch(entity.getBranch())
        .sequenceId(entity.getSequenceId())
        .createdAt(entity.getCreatedAt())
        .lastUpdatedAt(entity.getLastUpdatedAt())
        .build();
  }
}
