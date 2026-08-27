/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Response containing the graph traversal results.
 * Uses a normalized structure with separate nodes and edges lists.
 */
@Data
@Builder
@OwnedBy(HarnessTeam.IDP)
public class CatalogGraphEntity {
  /** List of unique nodes discovered during traversal */
  private List<GraphNode> nodes;

  /** List of edges connecting the nodes */
  private List<GraphEdge> edges;

  /** Traversal metadata (base entity, depth, counts) */
  private GraphMetadata metadata;
}
