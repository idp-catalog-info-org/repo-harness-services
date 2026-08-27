/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.graph.beans.CatalogGraphEntity;
import io.harness.idp.catalog.graph.strategy.GraphTraversalStrategy;
import io.harness.spec.server.idp.v1.model.GraphEdge;
import io.harness.spec.server.idp.v1.model.GraphMetadata;
import io.harness.spec.server.idp.v1.model.GraphNode;
import io.harness.spec.server.idp.v1.model.GraphTraversalResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class GraphTraversalService {
  private static final String GRAPH_TRAVERSE_FLOW_LOG = "[graphTraverse flow]";
  private final GraphTraversalStrategy strategy;
  private final RelationsMaster relationsMaster;

  @Inject
  public GraphTraversalService(GraphTraversalStrategy strategy, RelationsMaster relationsMaster) {
    this.strategy = strategy;
    this.relationsMaster = relationsMaster;
  }

  public GraphTraversalResponse traverse(
      String harnessAccount, String baseEntityRef, List<String> relationshipTypes, List<String> kinds, int depth) {
    log.info("{} Entering traversal service account={} entityRef={} relationshipTypes={} kinds={} depth={}",
        GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, baseEntityRef, relationshipTypes, kinds, depth);
    GraphTraversalResponse graphTraversalResponse =
        convertToSpecModel(strategy.traverse(harnessAccount, baseEntityRef, relationshipTypes, kinds, depth));
    log.info("{} Traversal service completed account={} entityRef={} nodes={} edges={} baseEntityRef={} "
            + "maxDepthReached={}",
        GRAPH_TRAVERSE_FLOW_LOG, harnessAccount, baseEntityRef,
        graphTraversalResponse.getNodes() == null ? 0 : graphTraversalResponse.getNodes().size(),
        graphTraversalResponse.getEdges() == null ? 0 : graphTraversalResponse.getEdges().size(),
        graphTraversalResponse.getMetadata() == null ? null : graphTraversalResponse.getMetadata().getBaseEntityRef(),
        graphTraversalResponse.getMetadata() == null ? null
                                                     : graphTraversalResponse.getMetadata().getMaxDepthReached());
    return graphTraversalResponse;
  }

  /**
   * Converts internal GraphTraversalResponse to OpenAPI spec model.
   */
  private GraphTraversalResponse convertToSpecModel(CatalogGraphEntity response) {
    GraphTraversalResponse specResponse = new GraphTraversalResponse();

    // Convert nodes
    if (response.getNodes() != null) {
      List<GraphNode> specNodes = response.getNodes()
                                      .stream()
                                      .map(node -> {
                                        GraphNode specNode = new GraphNode();
                                        specNode.setEntityRef(node.getEntityRef());
                                        specNode.setKind(node.getKind());
                                        specNode.setName(node.getName());
                                        specNode.setType(node.getType());
                                        return specNode;
                                      })
                                      .collect(Collectors.toList());
      specResponse.setNodes(specNodes);
    }

    // Convert edges
    if (response.getEdges() != null) {
      List<GraphEdge> specEdges =
          response.getEdges()
              .stream()
              .map(edge -> {
                GraphEdge specEdge = new GraphEdge();
                specEdge.setSource(edge.getSourceEntityRef());
                specEdge.setTarget(edge.getTargetEntityRef());
                specEdge.setRelation(edge.getRelationType());
                specEdge.setReverseRelation(relationsMaster.getReverseRelation(edge.getRelationType()));
                specEdge.setDepth(edge.getDepth());
                return specEdge;
              })
              .collect(Collectors.toList());
      specResponse.setEdges(specEdges);
    }

    // Convert metadata
    if (response.getMetadata() != null) {
      GraphMetadata specMetadata = new GraphMetadata();
      specMetadata.setBaseEntityRef(response.getMetadata().getBaseEntityRef());
      specMetadata.setMaxDepthReached(response.getMetadata().getMaxDepthReached());
      specMetadata.setTotalEdges(response.getMetadata().getTotalEdges());
      specResponse.setMetadata(specMetadata);
    }

    return specResponse;
  }
}
