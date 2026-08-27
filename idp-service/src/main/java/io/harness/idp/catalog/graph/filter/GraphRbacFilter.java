/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.filter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;

import java.util.List;

/**
 * Contract for RBAC-based filtering of catalog entities during graph traversal.
 * Accepts and returns CatalogEntity lists so callers never need to build
 * permission-check payloads themselves.
 * Swap the Guice binding in IdpModule to change filtering behaviour.
 */
@OwnedBy(HarnessTeam.IDP)
public interface GraphRbacFilter {
  /**
   * Returns the subset of the given entities that the current principal
   * is permitted to view under idp_catalog_view.
   *
   * @param accountIdentifier Harness account
   * @param entities          candidate entities discovered during BFS
   * @return permitted entities (preserves order where possible)
   */
  List<CatalogEntity> filterPermitted(String accountIdentifier, List<CatalogEntity> entities);
}
