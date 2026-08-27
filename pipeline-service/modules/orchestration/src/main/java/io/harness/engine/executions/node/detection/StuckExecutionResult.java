/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.executions.node.detection;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;

/**
 * Result of stuck execution detection for a single node.
 */
@OwnedBy(PIPELINE)
@Value
@Builder
public class StuckExecutionResult {
  /** Unique identifier of the node execution */
  String nodeExecutionId;

  /** Plan execution ID this node belongs to */
  String planExecutionId;

  /** Account identifier */
  String accountId;

  /** Organization identifier */
  String orgIdentifier;

  /** Project identifier */
  String projectIdentifier;

  /** Pipeline identifier */
  String pipelineIdentifier;

  /** Detection category (STUCK, POSSIBLY_NOT_STUCK, NOT_STUCK) */
  StuckExecutionCategory category;

  /** Reason for the detection result */
  String reason;
}
