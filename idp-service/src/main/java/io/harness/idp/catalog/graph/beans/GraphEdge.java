/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a directed edge in the catalog entity graph.
 * References nodes by their entity ref (id).
 */
@Data
@Builder
@OwnedBy(HarnessTeam.IDP)
public class GraphEdge {
  /** Source entity reference (matches GraphNode.entityRef) */
  @JsonProperty("source") private String sourceEntityRef;

  /** Target entity reference (matches GraphNode.entityRef) */
  @JsonProperty("target") private String targetEntityRef;

  /** Relationship type (dependsOn, providesApi, ownedBy, etc.) */
  @JsonProperty("relation") private String relationType;

  /** Reverse of the relation type (e.g., dependsOn → dependencyOf) */
  @JsonProperty("reverseRelation") private String reverseRelationType;

  /** BFS traversal depth at which this edge was discovered */
  @JsonProperty("depth") private int depth;
}
