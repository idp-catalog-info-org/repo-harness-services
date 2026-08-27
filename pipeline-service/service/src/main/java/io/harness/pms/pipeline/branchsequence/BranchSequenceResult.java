/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.branchsequence;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

/**
 * Result of incrementing a branch sequence counter.
 *
 * <p>Contains the new sequence ID along with the normalized branch and repository URL
 * that were used to compute the counter key.
 */
@OwnedBy(CI)
@Value
@Builder
public class BranchSequenceResult {
  /**
   * The new sequence number (1 for first build, incrementing thereafter).
   */
  long branchSeqId;

  /**
   * The normalized branch name (without refs/heads/ prefix).
   */
  String normalizedBranch;

  /**
   * The normalized repository URL (host/owner/repo, lowercase).
   */
  String normalizedRepoUrl;
}
