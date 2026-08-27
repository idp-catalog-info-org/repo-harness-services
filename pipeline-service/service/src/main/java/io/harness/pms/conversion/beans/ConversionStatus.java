/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.conversion.beans;

/**
 * Enum representing the status of a conversion job.
 */
public enum ConversionStatus {
  /**
   * Job has been created and queued for processing by the iterator.
   */
  QUEUED,

  /**
   * Job is currently being processed.
   * When combined with nextIteration=null, represents a "sleeping" state
   * (waiting for children to complete).
   */
  IN_PROGRESS,

  /**
   * Job completed successfully (all entities converted or skipped).
   */
  SUCCESS,

  /**
   * Job failed completely.
   */
  FAILED,

  /**
   * Job completed with some entities succeeding and some failing.
   * Only applicable to BATCH and PROJECT action types.
   */
  PARTIAL_SUCCESS,

  /**
   * Job was skipped (e.g., checksum match — entity already converted).
   */
  SKIPPED;

  /**
   * Check if the status is a final status (job has completed processing).
   *
   * @param status Status to check
   * @return true if status is final (SUCCESS, FAILED, PARTIAL_SUCCESS, or SKIPPED)
   */
  public static boolean isFinalStatus(ConversionStatus status) {
    return status == SUCCESS || status == FAILED || status == PARTIAL_SUCCESS || status == SKIPPED;
  }
}
