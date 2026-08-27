/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.filter;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.security.SecurityContextBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Bulk RBAC implementation that delegates directly to
 * {@link CatalogServiceHelper#checkEntityRefsPermissionWithOwnerFallback} — the same method
 * used by the entity list API — so permission check behaviour is consistent
 * across all catalog read surfaces.
 *
 * No caching. One ACL call per batch of up to 9999 entities (governed by
 * CatalogServiceHelper internally). Suitable as a simple baseline.
 */
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class BulkGraphRbacFilter implements GraphRbacFilter {
  private static final String GRAPH_TRAVERSE_FLOW_LOG = "[graphTraverse flow]";
  private final CatalogServiceHelper catalogServiceHelper;

  @Inject
  public BulkGraphRbacFilter(CatalogServiceHelper catalogServiceHelper) {
    this.catalogServiceHelper = catalogServiceHelper;
  }

  @Override
  public List<CatalogEntity> filterPermitted(String accountIdentifier, List<CatalogEntity> entities) {
    if (entities == null || entities.isEmpty()) {
      log.info(
          "{} Graph RBAC filter received empty candidate set account={}", GRAPH_TRAVERSE_FLOW_LOG, accountIdentifier);
      return List.of();
    }
    if (SecurityContextBuilder.getPrincipal() == null) {
      log.warn("{} No principal in security context. Skipping graph RBAC and returning all entities account={} "
              + "candidateEntities={}",
          GRAPH_TRAVERSE_FLOW_LOG, accountIdentifier, entities.size());
      return entities;
    }

    Map<String, CatalogEntity> entityKeyToEntity = new HashMap<>();
    Map<String, String> entityRefToOwner = new HashMap<>();
    Set<String> entityRefs = entities.stream()
                                 .map(entity -> {
                                   String ref = buildEntityRef(entity);
                                   entityKeyToEntity.put(entityKey(entity), entity);
                                   entityRefToOwner.put(ref, entity.getOwner());
                                   return ref;
                                 })
                                 .collect(Collectors.toSet());

    log.info("{} Invoking graph RBAC filter account={} candidateEntities={} uniqueEntityRefs={}",
        GRAPH_TRAVERSE_FLOW_LOG, accountIdentifier, entities.size(), entityRefs.size());
    Set<String> permittedRefs =
        catalogServiceHelper.checkEntityRefsPermissionWithOwnerFallback(accountIdentifier, entityRefToOwner, "view");

    List<CatalogEntity> permittedEntities = permittedRefs.stream()
                                                .map(ref -> entityKeyToEntity.get(parseEntityKey(ref)))
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.toList());
    log.info("{} Graph RBAC filter completed account={} candidateEntities={} permittedEntities={} permittedRefs={}",
        GRAPH_TRAVERSE_FLOW_LOG, accountIdentifier, entities.size(), permittedEntities.size(), permittedRefs.size());
    return permittedEntities;
  }

  /**
   * Builds a Harness-format entity ref: "kind:account[.org[.project]]/identifier"
   * compatible with CatalogServiceHelper.getKindScopeIdentifier().
   */
  private String buildEntityRef(CatalogEntity entity) {
    String kind = entity.getKind().toLowerCase();
    String scope = "account" + (!isEmpty(entity.getOrgIdentifier()) ? "." + entity.getOrgIdentifier() : "")
        + (!isEmpty(entity.getProjectIdentifier()) ? "." + entity.getProjectIdentifier() : "");
    return kind + ":" + scope + "/" + entity.getIdentifier();
  }

  /** "kind:identifier" deduplication key for reverse lookup. */
  private String entityKey(CatalogEntity entity) {
    return entity.getKind().toLowerCase() + ":" + entity.getIdentifier();
  }

  /**
   * Extracts "kind:identifier" from a ref of the form "kind:scope/identifier"
   * (the format returned by CatalogUtils.entityRef).
   */
  private String parseEntityKey(String entityRef) {
    if (entityRef == null) {
      return null;
    }
    int colonIdx = entityRef.indexOf(':');
    int slashIdx = entityRef.indexOf('/');
    if (colonIdx < 0 || slashIdx < 0 || slashIdx <= colonIdx) {
      return null;
    }
    String kind = entityRef.substring(0, colonIdx).toLowerCase();
    String identifier = entityRef.substring(slashIdx + 1);
    return kind + ":" + identifier;
  }
}
