/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.utils.execution.ExecutionModeUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Value;

@OwnedBy(CDC)
@Value
public class NodeExecutionsCache {
  private static final String NULL_PARENT_ID = "__NULL_PARENT_ID__";

  NodeExecutionService nodeExecutionService;
  PlanService planService;
  Ambiance ambiance;
  Map<String, NodeExecution> map;
  Map<String, List<String>> childrenMap;
  Map<String, Node> nodeMap;
  Map<String, Ambiance> ambianceMap;

  @Builder
  public NodeExecutionsCache(NodeExecutionService nodeExecutionService, PlanService planService, Ambiance ambiance) {
    this.nodeExecutionService = nodeExecutionService;
    this.planService = planService;
    this.ambiance = ambiance;
    this.nodeMap = new HashMap<>();
    this.map = new HashMap<>();
    this.childrenMap = new HashMap<>();
    this.ambianceMap = new HashMap<>();
  }

  public synchronized NodeExecution fetch(String nodeExecutionId) {
    if (nodeExecutionId == null) {
      return null;
    }
    if (map.containsKey(nodeExecutionId)) {
      return map.get(nodeExecutionId);
    }

    NodeExecution nodeExecution =
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.fieldsForExpressionEngine);
    map.put(nodeExecutionId, nodeExecution);
    return nodeExecution;
  }

  public NodeExecution getWithFieldsIncluded(String nodeExecutionId, Set<String> fieldsToInclude) {
    return nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, fieldsToInclude);
  }

  /**
   * Fetches a list of children for a particular parent Id.
   *
   * If parentId found in cache {@link NodeExecutionsCache#childrenMap} return list of nodes by
   * querying the {@link NodeExecutionsCache#map}
   *
   * Adds all the children to the {@link NodeExecutionsCache#map} and populates
   * {@link NodeExecutionsCache#childrenMap} with parentId => List#childIds
   *
   */
  public synchronized List<NodeExecution> fetchChildren(String parentId) {
    String childrenMapKey = parentId == null ? NULL_PARENT_ID : parentId;
    if (childrenMap.containsKey(childrenMapKey)) {
      List<String> ids = childrenMap.get(childrenMapKey);
      if (EmptyPredicate.isEmpty(ids)) {
        return Collections.emptyList();
      }

      return ids.stream().map(map::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    List<NodeExecution> childExecutions = new LinkedList<>();
    Set<String> fieldsForExpressionEngine = NodeProjectionUtils.fieldsForExpressionEngine;
    fetchChildrenExecutions(parentId, childExecutions, fieldsForExpressionEngine, ambiance.getPlanExecutionId());

    if (parentId != null
        && AmbianceUtils.checkIfFeatureFlagEnabled(
            ambiance, FeatureName.CDS_ALLOW_EXPRESSION_RESOLUTION_PIPELINE_ROLLBACK.name())
        && ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())) {
      NodeExecution parentNodeExecution = map.get(parentId);
      addChildrenFromOriginalExecution(parentNodeExecution, childExecutions);
    }

    if (EmptyPredicate.isEmpty(childExecutions)) {
      childrenMap.put(parentId, Collections.emptyList());
      return Collections.emptyList();
    }

    childExecutions.forEach(childExecution -> map.put(childExecution.getUuid(), childExecution));
    childrenMap.put(parentId, childExecutions.stream().map(NodeExecution::getUuid).collect(Collectors.toList()));
    return childExecutions;
  }

  private void fetchChildrenExecutions(String parentId, List<NodeExecution> childExecutions,
      Set<String> fieldsForExpressionEngine, String planExecutionId) {
    try (Stream<NodeExecution> stream = nodeExecutionService.fetchChildrenNodeExecutionsIterator(
             planExecutionId, parentId, fieldsForExpressionEngine)) {
      Iterator<NodeExecution> iterator = stream.iterator();
      while (iterator.hasNext()) {
        childExecutions.add(iterator.next());
      }
    }
  }

  // Should not change the fields to be included as its only used by NodeExecutionMap, if you change it may not use
  // index of NodeExecution collection
  public List<Status> findAllTerminalChildrenStatusOnly(
      String parentId, boolean includeChildrenOfStrategy, boolean filterNodeWithAdvisorProcessed) {
    List<NodeExecution> nodeExecutions = nodeExecutionService.findAllChildrenWithStatusInAndWithoutOldRetriesV2(
        ambiance.getPlanExecutionId(), parentId, StatusUtils.finalStatuses(), includeChildrenOfStrategy);

    if (filterNodeWithAdvisorProcessed) {
      return nodeExecutions.stream()
          .filter(NodeExecution::getAdvisorsProcessed)
          .map(NodeExecution::getStatus)
          .filter(status -> StatusUtils.finalStatuses().contains(status))
          .collect(Collectors.toList());
    }
    return nodeExecutions.stream()
        .map(NodeExecution::getStatus)
        .filter(status -> StatusUtils.finalStatuses().contains(status))
        .collect(Collectors.toList());
  }

  public synchronized Node fetchNode(String nodeId) {
    if (nodeId == null) {
      return null;
    }
    if (nodeMap.containsKey(nodeId)) {
      return nodeMap.get(nodeId);
    }

    Node node = planService.fetchNode(ambiance.getPlanId(), nodeId);
    nodeMap.put(nodeId, node);
    return node;
  }

  public synchronized Ambiance getAmbiance(String nodeExecutionId) {
    if (nodeExecutionId == null) {
      return null;
    }
    if (ambianceMap.containsKey(nodeExecutionId)) {
      return ambianceMap.get(nodeExecutionId);
    }
    Ambiance ambiance = nodeExecutionService.getAmbiance(
        nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbiance));
    ambianceMap.put(nodeExecutionId, ambiance);
    return ambiance;
  }

  private void addChildrenFromOriginalExecution(
      NodeExecution parentNodeExecution, List<NodeExecution> childExecutions) {
    // If parent node is of type identity and the mode of execution is rollback then we should add
    // all the children from original execution as well.
    if (ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())) {
      String rollbackExecutionId = ambiance.getMetadata().getOriginalPlanExecutionIdForRollbackMode();
      String originalNodeExecutionId;

      if (parentNodeExecution.getNodeType() == NodeType.IDENTITY_PLAN_NODE) {
        // If it is identity node then we will need to search for original nodeExecutionId in the parent execution.
        originalNodeExecutionId = parentNodeExecution.getOriginalNodeExecutionId();
      } else if (Objects.equals(parentNodeExecution.getGroup(), StepOutcomeGroup.STAGES.name())) {
        // If it is stages node then we need to figure out the original node Execution by plan node uuid. It should
        // give only once
        NodeExecution originalNodeExecution =
            nodeExecutionService.getByPlanNodeUuid(parentNodeExecution.getNodeId(), rollbackExecutionId);
        originalNodeExecutionId = originalNodeExecution.getUuid();
      } else {
        originalNodeExecutionId = parentNodeExecution.getUuid();
      }

      Set<String> childIdentifiers =
          childExecutions.stream().map(NodeExecution::getIdentifier).collect(Collectors.toSet());

      try (Stream<NodeExecution> stream = nodeExecutionService.fetchChildrenNodeExecutionsIterator(
               rollbackExecutionId, originalNodeExecutionId, NodeProjectionUtils.fieldsForExpressionEngine)) {
        Iterator<NodeExecution> iterator = stream.iterator();
        while (iterator.hasNext()) {
          NodeExecution nodeExecution = iterator.next();
          // Since there can be same identifiers in older execution as well. We should have the new identifier
          // having high priority than the older one.
          if (!childIdentifiers.contains(nodeExecution.getIdentifier())) {
            childExecutions.add(nodeExecution);
          }
        }
      }
    }
  }
}
