/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.branchsequence;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository interface for PipelineBranchSequence entity.
 *
 * <p>Provides CRUD operations and custom atomic operations for branch sequence counters.
 */
@HarnessRepo
@Transactional
@OwnedBy(CI)
public interface PipelineBranchSequenceRepository
    extends PagingAndSortingRepository<PipelineBranchSequence, String>, CrudRepository<PipelineBranchSequence, String>,
            PipelineBranchSequenceRepositoryCustom {
  /**
   * Find a branch sequence record by all identifying fields.
   */
  Optional<PipelineBranchSequence>
  findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndPipelineIdentifierAndNormalizedRepoUrlAndBranch(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String normalizedRepoUrl, String branch);
}
