/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.executions.node.detection;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.NodeExecution;

import java.util.List;
import java.util.Map;

/**
 * Service for detecting stuck pipeline executions based on the algorithm:
 * 1. Check if node is in non-final state
 * 2. For leaf nodes (ASYNC, ASYNC_CHAIN):
 *    - No executable responses → STUCK
 *    - Has responses + advisorsProcessed=false → POSSIBLY_NOT_STUCK
 *    - Has responses + advisorsProcessed=true → STUCK
 * 3. For container nodes (CHILD, CHILDREN, CHILD_CHAIN):
 *    - No running children AND no children at all → STUCK
 *    - No running children BUT has children (in final status) → NOT_STUCK (completing normally)
 *    - Has running children → Check recursively
 */
@OwnedBy(PIPELINE)
public interface StuckExecutionDetectionService {
  /**
   * Analyze a node execution to determine if it is stuck.
   * Uses pre-loaded children to avoid N+1 database queries.
   * The childrenByParentId map should contain ALL descendants (any status) so the service
   * can distinguish "no children" (stuck) from "all children completed" (not stuck).
   *
   * @param nodeExecution The node execution to analyze
   * @param childrenByParentId Map of ALL pre-loaded children grouped by parent ID (any status)
   * @return Detection result with category and details
   */
  StuckExecutionResult analyzeNodeExecution(
      NodeExecution nodeExecution, Map<String, List<NodeExecution>> childrenByParentId);
}
