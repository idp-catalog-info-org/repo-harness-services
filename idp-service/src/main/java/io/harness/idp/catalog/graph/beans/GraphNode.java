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
 * Represents a node in the catalog entity graph.
 * Contains entity metadata including id, kind, name, and type.
 */
@Data
@Builder
@OwnedBy(HarnessTeam.IDP)
public class GraphNode {
  /** Unique entity reference in format "kind:account[.org[.project]]/identifier" */
  @JsonProperty("entityRef") private String entityRef;

  /** Entity kind (component, api, resource, group, etc.) */
  @JsonProperty("kind") private String kind;

  /** Human-readable entity name */
  @JsonProperty("name") private String name;

  /** Entity type (service, library, website, openapi, etc.) */
  @JsonProperty("type") private String type;
}
