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
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.spec.server.idp.v1.model.TeamHierarchyNode;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BottomUpTeamHierarchyAclStrategy implements TeamHierarchyAclStrategy {
  private static final String TEAM_HIERARCHY_FLOW_LOG = "[teamHierarchy flow][bottom-up]";

  @Override
  public Set<String> visibleRootRefs(
      List<CatalogEntity> roots, Map<String, List<CatalogEntity>> childrenByParentRef, Set<String> permittedRefs) {
    Set<String> visible = new HashSet<>();
    for (CatalogEntity root : roots) {
      if (subtreeHasPermittedNode(root, childrenByParentRef, permittedRefs, new HashSet<>(), 0)) {
        visible.add(CatalogUtils.entityRef(root));
      }
    }
    return visible;
  }

  private boolean subtreeHasPermittedNode(CatalogEntity entity, Map<String, List<CatalogEntity>> childrenByParentRef,
      Set<String> permittedRefs, Set<String> visitedRefs, int depth) {
    String entityRef = CatalogUtils.entityRef(entity);
    if (permittedRefs.contains(entityRef)) {
      return true;
    }
    if (depth >= MAX_TREE_DEPTH || visitedRefs.contains(entityRef)) {
      return false;
    }
    Set<String> pathRefs = new HashSet<>(visitedRefs);
    pathRefs.add(entityRef);
    for (CatalogEntity child : childrenByParentRef.getOrDefault(entityRef, Collections.emptyList())) {
      if (subtreeHasPermittedNode(child, childrenByParentRef, permittedRefs, pathRefs, depth + 1)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<TeamHierarchyNode> assembleTree(List<CatalogEntity> roots,
      Map<String, List<CatalogEntity>> childrenByParentRef, Set<String> permittedRefs, NodeFactory nodeFactory) {
    List<TeamHierarchyNode> nodes = new ArrayList<>();
    for (CatalogEntity root : roots) {
      TeamHierarchyNode node = buildNode(root, childrenByParentRef, permittedRefs, nodeFactory, new HashSet<>(), 0);
      if (node != null) {
        nodes.add(node);
      }
    }
    return nodes;
  }

  private TeamHierarchyNode buildNode(CatalogEntity entity, Map<String, List<CatalogEntity>> childrenByParentRef,
      Set<String> permittedRefs, NodeFactory nodeFactory, Set<String> visitedRefs, int depth) {
    String entityRef = CatalogUtils.entityRef(entity);
    boolean selfPermitted = permittedRefs.contains(entityRef);

    if (depth >= MAX_TREE_DEPTH) {
      log.warn("{} Exceeded max depth={} at entity={}. Truncating subtree.", TEAM_HIERARCHY_FLOW_LOG, MAX_TREE_DEPTH,
          entityRef);
      return selfPermitted ? nodeFactory.create(entity, Collections.emptyList()) : null;
    }

    if (visitedRefs.contains(entityRef)) {
      log.warn("{} Detected cycle at entity={}. Truncating subtree.", TEAM_HIERARCHY_FLOW_LOG, entityRef);
      return null;
    }

    Set<String> pathRefs = new HashSet<>(visitedRefs);
    pathRefs.add(entityRef);

    List<TeamHierarchyNode> childNodes = new ArrayList<>();
    for (CatalogEntity child : childrenByParentRef.getOrDefault(entityRef, Collections.emptyList())) {
      TeamHierarchyNode childNode =
          buildNode(child, childrenByParentRef, permittedRefs, nodeFactory, pathRefs, depth + 1);
      if (childNode != null) {
        childNodes.add(childNode);
      }
    }

    if (selfPermitted || !childNodes.isEmpty()) {
      return nodeFactory.create(entity, childNodes);
    }
    return null;
  }
}
