/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.executions.node.detection;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

/**
 * Category of stuck execution detection result.
 * Based on the detection algorithm specified in the Confluence documentation.
 */
@OwnedBy(PIPELINE)
public enum StuckExecutionCategory {
  /**
   * Execution is definitively stuck and not making progress.
   * Examples:
   * - Leaf node with no executable responses
   * - Leaf node with executable responses but advisors already processed
   * - Container node with no children
   * - Container node with stuck children
   */
  STUCK,

  /**
   * Execution may not be stuck, requires manual review.
   * Example: Leaf node with executable responses but advisors not yet processed.
   */
  POSSIBLY_NOT_STUCK,

  /**
   * Execution is not stuck and is in a valid waiting state.
   * Example: Container node with active children.
   */
  NOT_STUCK
}
