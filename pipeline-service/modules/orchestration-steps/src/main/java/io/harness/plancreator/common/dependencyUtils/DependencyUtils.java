/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.common.dependencyUtils;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.DependencyEntry;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.StringArray;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Generic utility class for dependency graph operations.
 * Provides common functionality for cycle detection, graph building, and validation
 * that can be reused across different node types (stages, steps, step groups).
 */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class DependencyUtils {
  private static final String DEPENDENCY_NOT_FOUND_WARNING =
      "Dependency node '{}' not found in yaml definition. Available nodes: {}";
  private static final String DEPENDENCY_NOT_FOUND_ERROR =
      "Dependency node '%s' not found in yaml definition. Available nodes: %s";
  private static final String VALIDATION_SUCCESS_MESSAGE =
      "Dependency graph validation completed successfully - no cycles detected";

  /**
   * Creates a dependency graph from the YAML field.
   * This is the main entry point for dependency graph creation.
   *
   * @param yamlField The YAML field containing the pipeline nodes (stages for now)
   * @param identifierToNodeUuid Map of stage identifiers to their corresponding node UUIDs
   * @param nodeName Name of the node (stage for now)
   * @return A DependencyGraphProto object representing the dependencies
   */
  public static DependencyGraphProto createDependencyGraph(
      YamlField yamlField, Map<String, String> identifierToNodeUuid, String nodeName) {
    try {
      // Build dependency graph
      Map<String, List<String>> dependencyGraph = buildDependencyGraph(yamlField, identifierToNodeUuid, nodeName);
      return convertToProtobuf(dependencyGraph);
    } catch (Exception e) {
      log.error("Failed to create dependency graph from yaml field", e);
      throw new InvalidRequestException("Invalid dependency configuration: " + e.getMessage(), e);
    }
  }

  /**
   * Generic record to hold node information.
   * Contains the node identifier and its corresponding node UUID.
   */
  public record NodeInfo(String identifier, String nodeUuid) {}

  /**
   * Resolves node identifiers from depends_on field to their corresponding node UUIDs.
   *
   * @param dependsOnField The YAML field containing dependency node identifiers
   * @param identifierToNodeUuid Mapping from node identifiers to node UUIDs
   * @return List of resolved dependency node UUIDs
   */
  public static List<String> resolveDependencyNodeUuids(
      YamlField dependsOnField, Map<String, String> identifierToNodeUuid) {
    validateDependsOnField(dependsOnField);

    List<String> dependencies = new ArrayList<>();

    for (YamlNode depNode : dependsOnField.getNode().asArray()) {
      String dependentNodeId = depNode.getCurrJsonNode().asText();
      String dependentNodeUuid = identifierToNodeUuid.get(dependentNodeId);

      if (dependentNodeUuid != null) {
        dependencies.add(dependentNodeUuid);
      } else {
        log.warn(DEPENDENCY_NOT_FOUND_WARNING, dependentNodeId, identifierToNodeUuid.keySet());
        throw new InvalidRequestException(
            String.format(DEPENDENCY_NOT_FOUND_ERROR, dependentNodeId, identifierToNodeUuid.keySet()));
      }
    }

    return dependencies;
  }

  /**
   * Converts the internal dependency graph representation to protobuf format.
   *
   * @param dependencyGraph Map of node UUID to list of dependency node UUIDs
   * @return DependencyGraphProto object suitable for orchestration execution
   */
  /**
   * Builds a {@link DependencyGraphProto} from a node-to-dependencies map.
   */
  public static DependencyGraphProto buildDependencyGraphProto(Map<String, List<String>> dependencyGraph) {
    return convertToProtobuf(dependencyGraph);
  }

  /**
   * Collects the active rollback subgraph: rollback targets plus every transitive dependency.
   */
  public static Set<String> collectActiveSubgraphNodes(
      Map<String, List<String>> dependencyGraph, Collection<String> rollbackTargetNodeIds) {
    Set<String> activeNodes = new LinkedHashSet<>();
    Deque<String> queue = new ArrayDeque<>(rollbackTargetNodeIds);
    while (!queue.isEmpty()) {
      String nodeId = queue.poll();
      if (!activeNodes.add(nodeId)) {
        continue;
      }
      for (String dependency : dependencyGraph.getOrDefault(nodeId, Collections.emptyList())) {
        if (!activeNodes.contains(dependency)) {
          queue.add(dependency);
        }
      }
    }
    return activeNodes;
  }

  /**
   * Active subgraph for DAG post-prod rollback when {@code reversedDependencyGraph} is the rollback-time graph.
   *
   * <p>Rollback targets are forward leaves. In the reversed graph they may appear as roots with empty dependencies
   * (fan-out/merge), while in linear chains they list upstream nodes as dependencies ({@code deploy → [cs1]}). A walk
   * on the reversed graph alone misses upstream stages in the fan-out case; rehydrating the forward graph and unioning
   * both walks includes every stage that ran before the target in the original deploy without pulling in parallel
   * siblings (e.g. S1 when rolling back S2).
   */
  public static Set<String> collectActiveSubgraphNodesForDagPostExecutionRollback(
      Map<String, List<String>> reversedDependencyGraph, Collection<String> rollbackTargetNodeIds) {
    if (reversedDependencyGraph == null || reversedDependencyGraph.isEmpty() || rollbackTargetNodeIds == null
        || rollbackTargetNodeIds.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> activeNodes =
        new LinkedHashSet<>(collectActiveSubgraphNodes(reversedDependencyGraph, rollbackTargetNodeIds));
    Map<String, List<String>> forwardDependencyGraph = computeReverseDependencyGraph(reversedDependencyGraph);
    activeNodes.addAll(collectActiveSubgraphNodes(forwardDependencyGraph, rollbackTargetNodeIds));
    return activeNodes;
  }

  /**
   * Collects layout node IDs reachable from {@code rootNodeIds} via {@code edgeLayoutList.currentNodeChildren}.
   * Optionally follows {@code nextIds} (stage-level sibling links in DAG layout — must be excluded when pruning a
   * focused post-prod rollback so parallel deploy stages and upstream stages are not pulled in).
   */
  public static Set<String> collectLayoutSubgraphNodes(
      Map<String, GraphLayoutNode> layoutNodeMap, Collection<String> rootNodeIds, boolean includeNextIds) {
    if (layoutNodeMap == null || layoutNodeMap.isEmpty() || rootNodeIds == null || rootNodeIds.isEmpty()) {
      return Collections.emptySet();
    }
    Set<String> reachableNodes = new LinkedHashSet<>();
    Deque<String> queue = new ArrayDeque<>();
    for (String rootNodeId : rootNodeIds) {
      if (rootNodeId != null && layoutNodeMap.containsKey(rootNodeId)) {
        queue.add(rootNodeId);
      }
    }
    while (!queue.isEmpty()) {
      String nodeId = queue.poll();
      if (!reachableNodes.add(nodeId)) {
        continue;
      }
      GraphLayoutNode layoutNode = layoutNodeMap.get(nodeId);
      if (layoutNode == null || !layoutNode.hasEdgeLayoutList()) {
        continue;
      }
      layoutNode.getEdgeLayoutList()
          .getCurrentNodeChildrenList()
          .stream()
          .filter(layoutNodeMap::containsKey)
          .forEach(queue::add);
      if (includeNextIds) {
        layoutNode.getEdgeLayoutList().getNextIdsList().stream().filter(layoutNodeMap::containsKey).forEach(queue::add);
      }
    }
    return reachableNodes;
  }

  public static Set<String> collectLayoutSubgraphNodes(
      Map<String, GraphLayoutNode> layoutNodeMap, Collection<String> rootNodeIds) {
    return collectLayoutSubgraphNodes(layoutNodeMap, rootNodeIds, true);
  }

  /**
   * Retains only layout nodes reachable from {@code rootNodeIds} and drops edge links to pruned nodes.
   */
  public static Map<String, GraphLayoutNode> pruneLayoutNodeMapForSubgraph(
      Map<String, GraphLayoutNode> layoutNodeMap, Collection<String> rootNodeIds, boolean includeNextIds) {
    Set<String> activeNodeIds = collectLayoutSubgraphNodes(layoutNodeMap, rootNodeIds, includeNextIds);
    if (activeNodeIds.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, GraphLayoutNode> prunedLayoutNodeMap = new LinkedHashMap<>();
    for (String nodeId : activeNodeIds) {
      GraphLayoutNode layoutNode = layoutNodeMap.get(nodeId);
      if (layoutNode == null) {
        continue;
      }
      if (!layoutNode.hasEdgeLayoutList()) {
        prunedLayoutNodeMap.put(nodeId, layoutNode);
        continue;
      }
      EdgeLayoutList.Builder edgeBuilder = EdgeLayoutList.newBuilder();
      layoutNode.getEdgeLayoutList()
          .getCurrentNodeChildrenList()
          .stream()
          .filter(activeNodeIds::contains)
          .forEach(edgeBuilder::addCurrentNodeChildren);
      layoutNode.getEdgeLayoutList()
          .getNextIdsList()
          .stream()
          .filter(activeNodeIds::contains)
          .forEach(edgeBuilder::addNextIds);
      prunedLayoutNodeMap.put(nodeId, layoutNode.toBuilder().setEdgeLayoutList(edgeBuilder.build()).build());
    }
    return prunedLayoutNodeMap;
  }

  public static Map<String, GraphLayoutNode> pruneLayoutNodeMapForSubgraph(
      Map<String, GraphLayoutNode> layoutNodeMap, Collection<String> rootNodeIds) {
    return pruneLayoutNodeMapForSubgraph(layoutNodeMap, rootNodeIds, true);
  }

  /**
   * Restricts a dependency graph to {@code nodesToKeep}, dropping external dependencies.
   */
  public static Map<String, List<String>> pruneDependencyGraph(
      Map<String, List<String>> dependencyGraph, Set<String> nodesToKeep) {
    Map<String, List<String>> prunedGraph = new LinkedHashMap<>();
    for (String nodeId : nodesToKeep) {
      List<String> dependencies = dependencyGraph.getOrDefault(nodeId, Collections.emptyList());
      prunedGraph.put(
          nodeId, dependencies.stream().filter(nodesToKeep::contains).collect(Collectors.toCollection(ArrayList::new)));
    }
    return prunedGraph;
  }

  /**
   * Returns nodes with no dependencies in the given graph (DAG execution entry points).
   */
  public static List<String> findRootNodesInDependencyGraphMap(Map<String, List<String>> dependencyGraph) {
    List<String> rootNodeIds = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : dependencyGraph.entrySet()) {
      if (entry.getValue().isEmpty()) {
        rootNodeIds.add(entry.getKey());
      }
    }
    return rootNodeIds;
  }

  @VisibleForTesting
  static DependencyGraphProto convertToProtobuf(Map<String, List<String>> dependencyGraph) {
    // Validate for circular dependencies and perform general validation
    validateDependencyGraph(dependencyGraph);
    validateNoCycles(dependencyGraph);

    DependencyGraphProto.Builder builder = DependencyGraphProto.newBuilder();

    for (Map.Entry<String, List<String>> entry : dependencyGraph.entrySet()) {
      StringArray stringArray = StringArray.newBuilder().addAllValues(entry.getValue()).build();
      DependencyEntry depEntry =
          DependencyEntry.newBuilder().setNodeId(entry.getKey()).setDependencies(stringArray).build();
      builder.addEntries(depEntry);
    }

    return builder.build();
  }

  /**
   * Validates that the dependency graph contains no cycles using Topological Sort (Kahn's algorithm)
   * @param dependencyGraph Map of node UUID to list of its dependency node UUIDs
   * @throws InvalidArgumentsException if a cycle is detected
   */
  @VisibleForTesting
  static void validateNoCycles(Map<String, List<String>> dependencyGraph) {
    if (hasTopologicalSortCycle(dependencyGraph)) {
      throw new InvalidArgumentsException("Circular dependency detected in dependency graph");
    }
    log.info(VALIDATION_SUCCESS_MESSAGE);
  }

  /**
   * Detects cycles using Topological Sort (Kahn's algorithm).
   * If we can't process all nodes in topological order, there's a cycle.
   *
   * @param dependencyGraph The complete dependency graph (node UUID to dependency node UUIDs)
   * @return true if cycle is detected, false otherwise
   */
  @VisibleForTesting
  static boolean hasTopologicalSortCycle(Map<String, List<String>> dependencyGraph) {
    // Calculate in-degrees for all nodes
    Map<String, Integer> inDegree = calculateInDegrees(dependencyGraph);

    // Initialize queue with nodes having zero in-degree
    Queue<String> queue = new LinkedList<>();
    for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
      if (entry.getValue() == 0) {
        queue.offer(entry.getKey());
      }
    }

    int processedNodes = 0;

    // Process nodes in topological order
    while (!queue.isEmpty()) {
      String currentNode = queue.poll();
      processedNodes++;

      // Reduce in-degree of dependent nodes
      List<String> dependencies = dependencyGraph.getOrDefault(currentNode, Collections.emptyList());
      for (String dependentNode : dependencies) {
        int newInDegree = inDegree.get(dependentNode) - 1;
        inDegree.put(dependentNode, newInDegree);

        // If in-degree becomes 0, add to queue
        if (newInDegree == 0) {
          queue.offer(dependentNode);
        }
      }
    }

    // If we couldn't process all nodes, there's a cycle
    boolean hasCycle = processedNodes != dependencyGraph.size();

    if (hasCycle) {
      // Simple logging - identify nodes involved in cycle without expensive DFS
      Set<String> cycleNodes =
          dependencyGraph.keySet().stream().filter(node -> inDegree.get(node) > 0).collect(Collectors.toSet());
      log.error("Cycle detected involving nodes: {}", cycleNodes);
    }

    return hasCycle;
  }

  /**
   * Calculates in-degrees for all nodes in the dependency graph.
   * In-degree represents the number of nodes that depend on this node.
   *
   * @param dependencyGraph The complete dependency graph
   * @return Map of node UUID to its in-degree count
   */
  private static Map<String, Integer> calculateInDegrees(Map<String, List<String>> dependencyGraph) {
    Map<String, Integer> inDegree = new HashMap<>();

    // Initialize all nodes with in-degree 0
    for (String node : dependencyGraph.keySet()) {
      inDegree.put(node, 0);
    }

    // Calculate in-degrees by counting incoming edges
    for (List<String> dependencies : dependencyGraph.values()) {
      for (String dependentNode : dependencies) {
        inDegree.put(dependentNode, inDegree.getOrDefault(dependentNode, 0) + 1);
      }
    }

    return inDegree;
  }

  /**
   * Validates that the node mapping is not null or empty.
   *
   * @param nodeMapping The ID to node UUID mapping to validate
   * @throws InvalidArgumentsException if validation fails
   */
  static void validateNodeMapping(Map<String, String> nodeMapping) {
    Objects.requireNonNull(nodeMapping, "Node id to uuid mapping cannot be null");
  }

  /**
   * Validates that the node UUID is not null or empty.
   *
   * @param nodeUuid The node UUID to validate
   * @throws InvalidArgumentsException if validation fails
   */
  static void validateNodeUuid(String nodeUuid) {
    if (nodeUuid == null || nodeUuid.trim().isEmpty()) {
      throw new InvalidArgumentsException("Node UUID cannot be null or empty");
    }
  }

  /**
   * Validates that the depends_on field is valid.
   *
   * @param dependsOnField The depends_on YAML field to validate
   * @throws InvalidArgumentsException if validation fails
   */
  static void validateDependsOnField(YamlField dependsOnField) {
    Objects.requireNonNull(dependsOnField, "DependsOn field cannot be null");
    Objects.requireNonNull(dependsOnField.getNode(), "DependsOn field node cannot be null");
  }

  /**
   * Validates that the dependency graph is not null.
   *
   * @param dependencyGraph The dependency graph to validate
   * @throws InvalidArgumentsException if validation fails
   */
  @VisibleForTesting
  static void validateDependencyGraph(Map<String, List<String>> dependencyGraph) {
    Objects.requireNonNull(dependencyGraph, "Dependency graph cannot be null");
  }

  /**
   * Generic utility to build NodeInfo from a YAML field with validation.
   *
   * @param yamlField The YAML field to extract node info from
   * @param errorMessageTemplate Error message template for missing fields (should contain %s placeholder)
   * @return NodeInfo containing identifier and UUID
   * @throws InvalidArgumentsException if required fields are missing
   */
  public static NodeInfo buildNodeInfo(YamlField yamlField, String errorMessageTemplate) {
    Objects.requireNonNull(yamlField, "YAML field cannot be null");

    String nodeUuid = yamlField.getUuid();
    String nodeId = yamlField.getNode().getIdentifier();

    if (nodeId == null || nodeUuid == null) {
      throw new InvalidArgumentsException(String.format(errorMessageTemplate, yamlField));
    }

    return new NodeInfo(nodeId, nodeUuid);
  }

  /**
   * Builds the complete stage dependency graph by processing depends_on relationships.
   *
   * @param yamlField The YAML field containing all pipeline stages
   * @param identifierToNodeUuid Mapping from stage identifiers to node UUIDs
   * @return Complete dependency graph where keys are node UUIDs and values are lists of dependency node UUIDs
   */
  @VisibleForTesting
  static Map<String, List<String>> buildDependencyGraph(
      YamlField yamlField, Map<String, String> identifierToNodeUuid, String nodeName) {
    DependencyUtils.validateNodeMapping(identifierToNodeUuid);

    // Initialize empty dependency graph preserving YAML insertion order
    Map<String, List<String>> dependencyGraph = new LinkedHashMap<>();
    for (String nodeUuid : identifierToNodeUuid.values()) {
      dependencyGraph.put(nodeUuid, new ArrayList<>());
    }

    // Populate dependency relationships
    for (YamlNode yamlNode : yamlField.getNode().asArray()) {
      YamlField yamlNodeField = yamlNode.getField(nodeName);
      if (yamlNodeField != null) {
        String nodeUuid = yamlNodeField.getUuid();
        validateNodeUuid(nodeUuid);

        // Extract and resolve dependencies
        YamlField dependsOnField = yamlNodeField.getNode().getField(YAMLFieldNameConstants.DEPENDS_ON);
        if (dependsOnField != null) {
          List<String> dependencies = resolveDependencyNodeUuids(dependsOnField, identifierToNodeUuid);
          dependencyGraph.get(nodeUuid).addAll(dependencies);
        }
      }
    }

    return dependencyGraph;
  }

  @VisibleForTesting
  public Map<String, List<String>> convertDependencyGraphToMap(DependencyGraphProto dependencyGraph) {
    Map<String, List<String>> depMap = new LinkedHashMap<>();
    for (DependencyEntry entry : dependencyGraph.getEntriesList()) {
      depMap.put(entry.getNodeId(), new ArrayList<>(entry.getDependencies().getValuesList()));
    }
    return depMap;
  }

  /**
   * Calculate leaf nodes from dependency graph
   * Leaf nodes are the nodes that no other node depends on
   */
  @VisibleForTesting
  public List<String> calculateLeafNodes(Map<String, List<String>> dependencyGraph) {
    Set<String> allNodes = new LinkedHashSet<>(dependencyGraph.keySet());
    Set<String> nodesWithDependents = new HashSet<>();

    // Find all nodes that have other nodes depending on them
    // In the format node -> [dependencies], we need to find nodes that appear in dependency lists
    for (List<String> dependencies : dependencyGraph.values()) {
      nodesWithDependents.addAll(dependencies);
    }

    // Leaf nodes are those that no other node depends on
    // i.e., nodes that never appear in any dependency list
    allNodes.removeAll(nodesWithDependents);
    return new ArrayList<>(allNodes);
  }

  public List<String> findRootNodesInDependencyGraph(DependencyGraphProto dependencyGraphProto) {
    List<String> initialNodes = new ArrayList<>();
    for (DependencyEntry entry : dependencyGraphProto.getEntriesList()) {
      if (entry.getDependencies().getValuesList().isEmpty()) {
        initialNodes.add(entry.getNodeId());
      }
    }
    return initialNodes;
  }

  public List<String> findDependentNodes(String currentNodeId, DependencyGraphProto dependencyGraph) {
    List<String> dependentNodes = new ArrayList<>();
    for (DependencyEntry entry : dependencyGraph.getEntriesList()) {
      if (entry.getDependencies().getValuesList().contains(currentNodeId)) {
        dependentNodes.add(entry.getNodeId());
      }
    }
    return dependentNodes;
  }

  public Map<String, List<String>> computeReverseDependencyGraph(Map<String, List<String>> dependencyGraph) {
    Map<String, List<String>> reverseDependencyGraph = new LinkedHashMap<>();

    // Initialize all nodes with empty dependency lists
    for (String nodeId : dependencyGraph.keySet()) {
      reverseDependencyGraph.put(nodeId, new ArrayList<>());
    }

    // Reverse the dependencies
    // Original: A depends on [B, C] means B and C must complete before A
    // Reverse: B has dependent [A], C has dependent [A] means A must rollback before B and C
    for (Map.Entry<String, List<String>> entry : dependencyGraph.entrySet()) {
      String nodeId = entry.getKey();
      List<String> dependencies = entry.getValue();

      for (String dependency : dependencies) {
        // In reverse graph, the dependency now depends on the original node
        if (reverseDependencyGraph.containsKey(dependency)) {
          reverseDependencyGraph.get(dependency).add(nodeId);
        }
      }
    }

    return reverseDependencyGraph;
  }
}
