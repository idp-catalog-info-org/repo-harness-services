/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.executions.node.detection;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of stuck execution detection service.
 * Implements the algorithm from the Confluence specification.
 */
@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class StuckExecutionDetectionServiceImpl implements StuckExecutionDetectionService {
  private static final int MAX_RECURSION_DEPTH = 10;

  // If a container's last child completed more than this time ago but parent is still RUNNING,
  // the callback was likely lost and the execution is stuck
  private static final Duration CALLBACK_TIMEOUT = Duration.ofMinutes(5);

  @Override
  public StuckExecutionResult analyzeNodeExecution(
      NodeExecution nodeExecution, Map<String, List<NodeExecution>> childrenByParentId) {
    return analyzeNodeExecutionWithDepth(nodeExecution, childrenByParentId, 0);
  }

  private StuckExecutionResult analyzeNodeExecutionWithDepth(
      NodeExecution nodeExecution, Map<String, List<NodeExecution>> childrenByParentId, int depth) {
    // Safety check: prevent infinite recursion
    if (depth > MAX_RECURSION_DEPTH) {
      log.warn("Max recursion depth {} reached for node {}", MAX_RECURSION_DEPTH, nodeExecution.getUuid());
      return buildResult(nodeExecution, StuckExecutionCategory.POSSIBLY_NOT_STUCK, "Max recursion depth reached");
    }

    ExecutionMode mode = nodeExecution.getMode();

    // Classify and check based on mode
    if (ExecutionModeUtils.isLeafMode(mode)) {
      return checkLeafNode(nodeExecution);
    } else if (ExecutionModeUtils.isParentMode(mode)) {
      return checkNonLeafNode(nodeExecution, childrenByParentId, depth);
    }

    return buildResult(nodeExecution, StuckExecutionCategory.POSSIBLY_NOT_STUCK, "Unknown mode classification");
  }

  /**
   * Check if a leaf node is stuck.
   * Algorithm:
   * - Empty executableResponses → STUCK
   * - Has executableResponses + advisorsProcessed=false → POSSIBLY_NOT_STUCK
   * - Has executableResponses + advisorsProcessed=true → STUCK
   */
  private StuckExecutionResult checkLeafNode(NodeExecution node) {
    boolean hasResponses = !isEmpty(node.getExecutableResponses());
    Boolean advisorsProcessed = node.getAdvisorsProcessed(); // Returns true if null

    if (!hasResponses) {
      return buildResult(node, StuckExecutionCategory.STUCK, "Leaf node with no executable responses");
    }

    if (Boolean.FALSE.equals(advisorsProcessed)) {
      return buildResult(
          node, StuckExecutionCategory.POSSIBLY_NOT_STUCK, "Has executable responses but advisors not yet processed");
    }

    if (Boolean.TRUE.equals(advisorsProcessed)) {
      return buildResult(node, StuckExecutionCategory.STUCK,
          "Has executable responses but advisors already processed with no progress");
    }

    return buildResult(node, StuckExecutionCategory.NOT_STUCK, "Leaf node in valid state");
  }

  /**
   * Check if a non-leaf (container) node is stuck.
   * Algorithm:
   * - No children at all → STUCK (truly stuck, container should have spawned children)
   * - No running children BUT has children (in final status) → NOT_STUCK (completing normally)
   * - Has running children → Recursively check each child
   */
  private StuckExecutionResult checkNonLeafNode(
      NodeExecution node, Map<String, List<NodeExecution>> childrenByParentId, int depth) {
    // Get ALL pre-loaded children (any status)
    List<NodeExecution> allChildren = childrenByParentId.getOrDefault(node.getUuid(), Collections.emptyList());

    // Filter to get only children in resumable (running) statuses
    List<NodeExecution> runningChildren =
        allChildren.stream()
            .filter(child -> StatusUtils.resumableStatuses().contains(child.getStatus()))
            .collect(Collectors.toList());

    if (runningChildren.isEmpty()) {
      // No running children - need to distinguish between:
      // 1. No children at all → STUCK (truly stuck, container should have spawned children)
      // 2. Has children but all completed (in final status) → check timeout
      if (allChildren.isEmpty()) {
        return buildResult(node, StuckExecutionCategory.STUCK, "Container node with no children");
      }

      // Has children but none are running - check if callback might be lost
      // Find when the most recently completed child finished
      long lastChildCompletionTime =
          allChildren.stream()
              .filter(child -> StatusUtils.finalStatuses().contains(child.getStatus()))
              .mapToLong(child -> child.getLastUpdatedAt() != null ? child.getLastUpdatedAt() : 0L)
              .max()
              .orElse(0L);

      long timeSinceCompletion = System.currentTimeMillis() - lastChildCompletionTime;

      if (lastChildCompletionTime > 0 && timeSinceCompletion > CALLBACK_TIMEOUT.toMillis()) {
        // Children completed more than 5 minutes ago but parent is still RUNNING - callback likely lost
        return buildResult(node, StuckExecutionCategory.STUCK,
            String.format("Children completed %d min ago, callback likely lost",
                Duration.ofMillis(timeSinceCompletion).toMinutes()));
      }

      // Within grace period - completing normally
      return buildResult(
          node, StuckExecutionCategory.NOT_STUCK, "Container node has children in final status - completing normally");
    }

    // Recursively check running children
    long stuckChildCount = runningChildren.stream()
                               .map(child -> analyzeNodeExecutionWithDepth(child, childrenByParentId, depth + 1))
                               .filter(result -> result.getCategory() == StuckExecutionCategory.STUCK)
                               .count();

    if (stuckChildCount > 0) {
      return buildResult(
          node, StuckExecutionCategory.STUCK, String.format("Container node has %d stuck child(ren)", stuckChildCount));
    }

    return buildResult(node, StuckExecutionCategory.NOT_STUCK, "Container node with active children");
  }

  /**
   * Build a detection result with all relevant information.
   */
  private StuckExecutionResult buildResult(NodeExecution node, StuckExecutionCategory category, String reason) {
    return StuckExecutionResult.builder()
        .nodeExecutionId(node.getUuid())
        .planExecutionId(node.getPlanExecutionId())
        .accountId(node.getAccountId())
        .orgIdentifier(node.getOrgIdentifier())
        .projectIdentifier(node.getProjectIdentifier())
        .pipelineIdentifier(node.getPipelineIdentifier())
        .category(category)
        .reason(reason)
        .build();
  }
}
