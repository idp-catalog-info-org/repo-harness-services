/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.fetcher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.graph.utils.EntityRefResolver.ScopedEntityLookup;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstracts catalog_entities lookups needed by the BFS graph traversal.
 * Decouples the strategy from the underlying MongoDB queries.
 */
@OwnedBy(HarnessTeam.IDP)
public interface CatalogEntityGraphFetcher {
  /**
   * Look up the root (base) entity by its parentUniqueId (scope's unique ID), kind, and identifier.
   * Leverages the unique composite index (parentUniqueId, kind, identifier).
   * Called once per traversal request.
   */
  Optional<CatalogEntity> findRootEntity(String parentUniqueId, String kind, String identifier);

  /**
   * Batch-fetch entities using parentUniqueId-based lookups. Each ScopedEntityLookup contains
   * the target scope's parentUniqueId, kind, and identifier.
   *
   * @param lookups list of scoped entity lookups with parentUniqueId
   * @return map keyed by "kind:identifier" for O(1) lookup by the BFS strategy
   */
  Map<String, CatalogEntity> fetchByScopedLookups(List<ScopedEntityLookup> lookups);
}
