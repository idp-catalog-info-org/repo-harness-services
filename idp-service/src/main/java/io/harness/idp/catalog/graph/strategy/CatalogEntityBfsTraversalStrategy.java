/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.strategy;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.EntityNotFoundException;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.graph.beans.CatalogGraphEntity;
import io.harness.idp.catalog.graph.beans.GraphEdge;
import io.harness.idp.catalog.graph.beans.GraphMetadata;
import io.harness.idp.catalog.graph.beans.GraphNode;
import io.harness.idp.catalog.graph.fetcher.CatalogEntityGraphFetcher;
import io.harness.idp.catalog.graph.filter.GraphRbacFilter;
import io.harness.idp.catalog.graph.utils.EntityRefResolver;
import io.harness.idp.catalog.graph.utils.RelationExtractor;
import io.harness.idp.catalog.graph.utils.RelationExtractor.RelationBatch;
import io.harness.idp.catalog.service.CatalogScopeResolver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * BFS traversal over the catalog_entities collection using the inline relations map.
 *
 * Algorithm:
 *   1. Convert input Harness format "kind:account[.org[.project]]/identifier"
 *      to queryableEntityRef format "namespace/kind/identifier"
 *   2. Fetch root entity using indexed queryableEntityRef field
 *   3. Per depth:
 *      a. Extract relations from frontier entities, filter by relationshipTypes and kinds
 *      b. Convert relation refs (Backstage format) to queryableEntityRef format
 *      c. Batch-fetch entities using WHERE queryableEntityRef IN [list]
 *      d. RBAC-filter the fetched entities
 *      e. Build response edges and advance the frontier
 *
 * All queries use the indexed queryableEntityRef field for optimal performance.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogEntityBfsTraversalStrategy implements GraphTraversalStrategy {
  private static final String GRAPH_TRAVERSE_FLOW_LOG = "[graphTraverse flow]";
  private final CatalogEntityGraphFetcher fetcher;
  private final GraphRbacFilter rbacFilter;
  private final RelationExtractor relationExtractor;
  private final CatalogScopeResolver scopeResolver;

  @Inject
  public CatalogEntityBfsTraversalStrategy(CatalogEntityGraphFetcher fetcher, GraphRbacFilter rbacFilter,
      RelationExtractor relationExtractor, CatalogScopeResolver scopeResolver) {
    this.fetcher = fetcher;
    this.rbacFilter = rbacFilter;
    this.relationExtractor = relationExtractor;
    this.scopeResolver = scopeResolver;
  }

  @Override
  public CatalogGraphEntity traverse(
      String harnessAccount, String baseEntityRef, List<String> relationshipTypes, List<String> kinds, int depth) {
    log.info("{} BFS traversal started account={} entityRef={} relationshipTypes={} kinds={} depth={}",
        GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, baseEntityRef, relationshipTypes, kinds, depth);

    Optional<CatalogEntity> rootOpt = fetchRootEntity(harnessAccount, baseEntityRef);
    if (rootOpt.isEmpty()) {
      log.warn("{} Root entity lookup failed account={} entityRef={}", GRAPH_TRAVERSE_FLOW_LOG, harnessAccount,
          baseEntityRef);
      throw new EntityNotFoundException("Root entity not found: " + baseEntityRef);
    }

    log.info("{} Root entity resolved account={} rootEntityRef={} rootKind={} rootName={}", GRAPH_TRAVERSE_FLOW_LOG,
        harnessAccount, EntityRefResolver.buildEntityRef(rootOpt.get()), rootOpt.get().getKind(),
        rootOpt.get().getName());
    return performBfsTraversal(harnessAccount, rootOpt.get(), relationshipTypes, kinds, depth);
  }

  /**
   * Fetches the root entity by parsing the Harness-format entityRef, resolving the scope
   * namespace to a parentUniqueId via CatalogScopeResolver, and querying on (parentUniqueId, kind, identifier).
   */
  private Optional<CatalogEntity> fetchRootEntity(String harnessAccount, String baseEntityRef) {
    Optional<EntityRefResolver.ParsedEntityRef> parsedOpt = EntityRefResolver.parseHarnessEntityRef(baseEntityRef);
    if (parsedOpt.isEmpty()) {
      log.warn("{} Invalid graph root entity ref format={}", GRAPH_TRAVERSE_FLOW_LOG, baseEntityRef);
      return Optional.empty();
    }
    EntityRefResolver.ParsedEntityRef parsed = parsedOpt.get();

    String namespace =
        "account" + (parsed.org != null ? "." + parsed.org : "") + (parsed.project != null ? "." + parsed.project : "");
    String parentUniqueId = scopeResolver.resolveNamespaceToUniqueId(harnessAccount, namespace);
    if (parentUniqueId == null) {
      log.warn("{} Could not resolve namespace={} to parentUniqueId account={} entityRef={}", GRAPH_TRAVERSE_FLOW_LOG,
          namespace, harnessAccount, baseEntityRef);
      return Optional.empty();
    }

    log.info("{} Parsed root entity ref account={} entityRef={} kind={} parentUniqueId={} identifier={}",
        GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, baseEntityRef, parsed.kind, parentUniqueId, parsed.identifier);
    return fetcher.findRootEntity(parentUniqueId, parsed.kind, parsed.identifier);
  }

  /**
   * Performs BFS traversal from the root entity up to the specified depth.
   */
  private CatalogGraphEntity performBfsTraversal(
      String harnessAccount, CatalogEntity rootEntity, List<String> relationshipTypes, List<String> kinds, int depth) {
    Set<String> relationshipTypeSet = isEmpty(relationshipTypes) ? Set.of() : new HashSet<>(relationshipTypes);
    Set<String> kindsSet = isEmpty(kinds) ? Set.of() : new HashSet<>(kinds);

    List<GraphEdge> resultEdges = new ArrayList<>();
    Map<String, CatalogEntity> uniqueEntities = new HashMap<>();
    Set<String> visited = new HashSet<>();

    // Add root entity to nodes
    String rootKey = EntityRefResolver.buildEntityKey(rootEntity);
    visited.add(rootKey);
    uniqueEntities.put(EntityRefResolver.buildEntityRef(rootEntity), rootEntity);

    List<CatalogEntity> frontier = List.of(rootEntity);
    int maxDepthReached = 0;

    for (int currentDepth = 1; currentDepth <= depth; currentDepth++) {
      log.info("{} Processing traversal depth account={} rootEntityRef={} currentDepth={} frontierSize={} visited={} "
              + "relationshipTypes={} kinds={}",
          GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, EntityRefResolver.buildEntityRef(rootEntity), currentDepth,
          frontier.size(), visited.size(), relationshipTypeSet.size(), kindsSet.size());
      DepthResult depthResult =
          processDepthLevel(harnessAccount, frontier, relationshipTypeSet, kindsSet, visited, currentDepth);

      if (depthResult.edges().isEmpty()) {
        log.info("{} Traversal stopped at depth account={} rootEntityRef={} currentDepth={} reason=no_edges",
            GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, EntityRefResolver.buildEntityRef(rootEntity), currentDepth);
        break;
      }

      resultEdges.addAll(depthResult.edges());
      maxDepthReached = currentDepth;
      frontier = depthResult.nextFrontier();
      log.info("{} Depth processed account={} rootEntityRef={} currentDepth={} edgesAdded={} nextFrontierSize={} "
              + "visited={}",
          GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, EntityRefResolver.buildEntityRef(rootEntity), currentDepth,
          depthResult.edges().size(), frontier.size(), visited.size());

      for (CatalogEntity entity : frontier) {
        uniqueEntities.put(EntityRefResolver.buildEntityRef(entity), entity);
      }

      if (frontier.isEmpty()) {
        log.info("{} Traversal frontier exhausted account={} rootEntityRef={} currentDepth={}", GRAPH_TRAVERSE_FLOW_LOG,
            harnessAccount, EntityRefResolver.buildEntityRef(rootEntity), currentDepth);
        break;
      }
    }

    log.info("{} BFS traversal completed account={} rootEntityRef={} nodes={} edges={} maxDepthReached={}",
        GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, EntityRefResolver.buildEntityRef(rootEntity), uniqueEntities.size(),
        resultEdges.size(), maxDepthReached);
    return buildResponse(rootEntity, resultEdges, uniqueEntities, maxDepthReached);
  }

  /**
   * Processes a single depth level: extract relations, fetch entities, apply RBAC, build edges.
   * Returns both the edges and the next frontier.
   */
  private DepthResult processDepthLevel(String harnessAccount, List<CatalogEntity> frontier,
      Set<String> relationshipTypeSet, Set<String> kindsSet, Set<String> visited, int currentDepth) {
    RelationBatch relationBatch = relationExtractor.extractRelations(frontier, relationshipTypeSet, kindsSet, visited,
        harnessAccount, namespace -> scopeResolver.resolveNamespaceToUniqueId(harnessAccount, namespace));
    log.info("{} Relation extraction completed account={} currentDepth={} frontierSize={} lookups={} "
            + "edgeDescriptors={} visited={}",
        GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, currentDepth, frontier.size(),
        relationBatch.getScopedEntityLookups().size(), relationBatch.getEdgeDescriptors().size(), visited.size());

    if (relationBatch.getScopedEntityLookups().isEmpty()) {
      log.info("{} No scoped entity lookups found account={} currentDepth={}. Traversal depth complete.",
          GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, currentDepth);
      return new DepthResult(List.of(), List.of());
    }

    Map<String, CatalogEntity> fetchedByKey = fetcher.fetchByScopedLookups(relationBatch.getScopedEntityLookups());
    log.info("{} Batch fetch completed account={} currentDepth={} requestedLookups={} fetchedEntities={}",
        GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, currentDepth, relationBatch.getScopedEntityLookups().size(),
        fetchedByKey.size());
    visited.addAll(fetchedByKey.keySet());

    if (fetchedByKey.isEmpty()) {
      log.info("{} No related entities fetched account={} currentDepth={}. Traversal depth complete.",
          GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, currentDepth);
      return new DepthResult(List.of(), List.of());
    }

    List<CatalogEntity> permittedEntities = applyRbacFilter(harnessAccount, new ArrayList<>(fetchedByKey.values()));
    log.info("{} RBAC filtering completed account={} currentDepth={} fetchedEntities={} permittedEntities={}",
        GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, currentDepth, fetchedByKey.size(), permittedEntities.size());

    List<GraphEdge> edges = buildEdgesForEntities(permittedEntities, relationBatch, currentDepth);
    log.info("{} Edge construction completed account={} currentDepth={} edgesBuilt={} nextFrontierSize={}",
        GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, currentDepth, edges.size(), permittedEntities.size());

    return new DepthResult(edges, permittedEntities);
  }

  /**
   * Applies RBAC filtering to the fetched entities.
   */
  private List<CatalogEntity> applyRbacFilter(String harnessAccount, List<CatalogEntity> entities) {
    return rbacFilter.filterPermitted(harnessAccount, entities);
  }

  /**
   * Builds GraphEdge objects for permitted entities.
   * Iterates over all edge descriptors and creates an edge for each one whose target was fetched
   * and permitted. This allows multiple edges to the same target with different relation types.
   */
  private List<GraphEdge> buildEdgesForEntities(
      List<CatalogEntity> permittedEntities, RelationBatch relationBatch, int depth) {
    // Build a set of permitted entity keys for fast lookup
    Set<String> permittedKeys = new HashSet<>();
    Map<String, CatalogEntity> keyToEntity = new HashMap<>();
    for (CatalogEntity entity : permittedEntities) {
      String key = EntityRefResolver.buildEntityKey(entity);
      permittedKeys.add(key);
      keyToEntity.put(key, entity);
    }

    List<GraphEdge> edges = new ArrayList<>();
    for (RelationExtractor.EdgeDescriptor descriptor : relationBatch.getEdgeDescriptors()) {
      if (permittedKeys.contains(descriptor.targetEntityKey())) {
        CatalogEntity targetEntity = keyToEntity.get(descriptor.targetEntityKey());
        edges.add(GraphEdge.builder()
                      .sourceEntityRef(descriptor.sourceEntityRef())
                      .targetEntityRef(EntityRefResolver.buildEntityRef(targetEntity))
                      .relationType(descriptor.relationType())
                      .depth(depth)
                      .build());
      }
    }

    return edges;
  }

  /**
   * Builds the final GraphTraversalResponse with nodes and edges.
   */
  private CatalogGraphEntity buildResponse(CatalogEntity rootEntity, List<GraphEdge> resultEdges,
      Map<String, CatalogEntity> uniqueEntities, int maxDepthReached) {
    // Build nodes list from unique entities
    List<GraphNode> nodes = uniqueEntities.entrySet()
                                .stream()
                                .map(entry
                                    -> GraphNode.builder()
                                           .entityRef(entry.getKey())
                                           .kind(entry.getValue().getKind())
                                           .name(entry.getValue().getName())
                                           .type(entry.getValue().getType())
                                           .build())
                                .collect(Collectors.toList());

    return CatalogGraphEntity.builder()
        .nodes(nodes)
        .edges(resultEdges)
        .metadata(GraphMetadata.builder()
                      .baseEntityRef(EntityRefResolver.buildEntityRef(rootEntity))
                      .maxDepthReached(maxDepthReached)
                      .totalEdges(resultEdges.size())
                      .build())
        .build();
  }

  /**
   * Result from processing a single depth level.
   * Contains both the edges and the next frontier entities.
   */
  private record DepthResult(List<GraphEdge> edges, List<CatalogEntity> nextFrontier) {}
}
