/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.strategy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.spec.server.idp.v1.model.TeamHierarchyNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public interface TeamHierarchyAclStrategy {
  int MAX_TREE_DEPTH = 25;

  Set<String> visibleRootRefs(
      List<CatalogEntity> roots, Map<String, List<CatalogEntity>> childrenByParentRef, Set<String> permittedRefs);

  List<TeamHierarchyNode> assembleTree(List<CatalogEntity> roots, Map<String, List<CatalogEntity>> childrenByParentRef,
      Set<String> permittedRefs, NodeFactory nodeFactory);

  @FunctionalInterface
  interface NodeFactory {
    TeamHierarchyNode create(CatalogEntity entity, List<TeamHierarchyNode> children);
  }
}
