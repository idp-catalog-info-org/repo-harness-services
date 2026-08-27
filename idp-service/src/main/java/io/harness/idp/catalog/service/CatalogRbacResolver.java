/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.common.CommonUtils.getUserPrincipalFromPrincipal;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.idp.catalog.cache.CatalogRbacPermissionsCache;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.rbac.KindResourceTypeMapper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.common.RbacUtils;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class CatalogRbacResolver {
  private static final String GET_ENTITIES_FLOW_LOG = "[getEntities flow]";
  private final CatalogRbacPermissionsCache rbacPermissionsCache;
  private final CatalogServiceHelper catalogServiceHelper;
  private final CatalogEntityRepository catalogEntityRepository;

  @Inject
  public CatalogRbacResolver(CatalogRbacPermissionsCache rbacPermissionsCache,
      CatalogServiceHelper catalogServiceHelper, CatalogEntityRepository catalogEntityRepository) {
    this.rbacPermissionsCache = rbacPermissionsCache;
    this.catalogServiceHelper = catalogServiceHelper;
    this.catalogEntityRepository = catalogEntityRepository;
  }

  public RbacResolveResult resolve(
      String accountId, List<ScopeInfo> requestedScopeInfos, ScopeTopology topology, List<String> resolvedKinds) {
    if (RbacUtils.isPureServiceToServiceCall()) {
      Map<String, List<String>> resourceTypeToKinds = KindResourceTypeMapper.groupKindsByResourceType(resolvedKinds);
      Map<String, List<ScopeInfo>> allScopes = new HashMap<>();
      resourceTypeToKinds.keySet().forEach(rt -> allScopes.put(rt, requestedScopeInfos));
      log.info("{} Skipping RBAC resolution for service-to-service request account={} requestedScopeInfos={} "
              + "resourceTypes={}",
          GET_ENTITIES_FLOW_LOG, accountId, requestedScopeInfos.size(), resourceTypeToKinds.keySet());
      return RbacResolveResult.builder()
          .resourceTypeToPermittedScopes(allScopes)
          .permittedEntityRefs(new ArrayList<>())
          .build();
    }

    UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
    String userId = userPrincipal != null ? userPrincipal.getName() : "service";
    List<ScopeInfo> allScopeInfos = topology.buildScopeInfos(topology.getAllUniqueIds());

    Map<String, List<String>> resourceTypeToKinds = KindResourceTypeMapper.groupKindsByResourceType(resolvedKinds);
    Map<String, List<ScopeInfo>> resourceTypeToPermittedScopes = new HashMap<>();
    List<String> allPermittedEntityRefs = new ArrayList<>();

    for (Map.Entry<String, List<String>> entry : resourceTypeToKinds.entrySet()) {
      String resourceType = entry.getKey();
      List<String> kindsForType = entry.getValue();

      List<String> allowedUniqueIds =
          getOrComputeAllowedScopesForResourceType(accountId, userId, allScopeInfos, resourceType);

      Set<String> allowedSet = new HashSet<>(allowedUniqueIds);
      List<ScopeInfo> permittedForType =
          requestedScopeInfos.stream().filter(si -> allowedSet.contains(si.getUniqueId())).collect(Collectors.toList());
      resourceTypeToPermittedScopes.put(resourceType, permittedForType);

      List<String> deniedUniqueIds = requestedScopeInfos.stream()
                                         .map(ScopeInfo::getUniqueId)
                                         .filter(uid -> !allowedSet.contains(uid))
                                         .distinct()
                                         .collect(Collectors.toList());

      if (!deniedUniqueIds.isEmpty()) {
        String kindsStr = String.join(",", kindsForType);
        List<String> entityRefs = resolveEntityLevelRbac(accountId, deniedUniqueIds, kindsStr);
        allPermittedEntityRefs.addAll(entityRefs);
      }
    }

    log.info("{} RBAC resolver completed account={} user={} requestedScopeInfos={} resourceTypes={} "
            + "permittedEntityRefs={}",
        GET_ENTITIES_FLOW_LOG, accountId, userId, requestedScopeInfos.size(),
        resourceTypeToPermittedScopes.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue().size()).toList(),
        allPermittedEntityRefs.size());

    return RbacResolveResult.builder()
        .resourceTypeToPermittedScopes(resourceTypeToPermittedScopes)
        .permittedEntityRefs(allPermittedEntityRefs)
        .build();
  }

  public List<String> permittedGroupEntityRefs(
      String accountId, List<ScopeInfo> requestedScopeInfos, ScopeTopology topology, List<String> resolvedKinds) {
    if (resolvedKinds.stream().anyMatch(catalogServiceHelper::isInheritableKind)) {
      Set<String> uniqueScopesForGroups = catalogServiceHelper.uniqueParentScopesForGroups(requestedScopeInfos);
      List<String> uniqueIds = topology.resolveParentUniqueIds(String.join(",", uniqueScopesForGroups));
      List<String> permittedGroupEntityRefs = resolveEntityLevelRbac(accountId, uniqueIds, GROUP_KIND);
      log.info("{} Permitted-Group-EntityRefs evaluated account={} scopes={} "
              + "permittedEntityRefsByGroup={} kind={}",
          GET_ENTITIES_FLOW_LOG, accountId, String.join(",", uniqueScopesForGroups), permittedGroupEntityRefs.size(),
          String.join(",", resolvedKinds));
      return permittedGroupEntityRefs;
    }
    return new ArrayList<>();
  }

  private List<String> getOrComputeAllowedScopesForResourceType(
      String accountId, String userId, List<ScopeInfo> allScopeInfos, String resourceType) {
    String permission = KindResourceTypeMapper.permissionForResourceType(resourceType, "view");
    log.info("{} RBAC resolving scopes for resourceType={} permission={} account={} user={} allScopeInfos={}",
        GET_ENTITIES_FLOW_LOG, resourceType, permission, accountId, userId, allScopeInfos.size());

    List<ScopeInfo> allowedScopes =
        catalogServiceHelper.scopeInfosRbacByResourceType(accountId, allScopeInfos, resourceType, permission);
    return allowedScopes.stream().map(ScopeInfo::getUniqueId).distinct().collect(Collectors.toList());
  }

  private List<String> resolveEntityLevelRbac(String accountId, List<String> deniedUniqueIds, String kind) {
    List<CatalogEntity> deniedEntities;
    if (!isEmpty(kind)) {
      List<String> kinds = List.of(kind.split(","));
      deniedEntities =
          catalogEntityRepository.findKindIdentifierScopeByParentUniqueIdInAndKindIn(deniedUniqueIds, kinds);
    } else {
      deniedEntities = catalogEntityRepository.findKindIdentifierScopeByParentUniqueIdIn(deniedUniqueIds);
    }

    List<String> entityRefsToCheck = new ArrayList<>();
    deniedEntities.forEach(ce -> entityRefsToCheck.add(CatalogUtils.entityRef(ce)));
    List<String> permittedEntityRefs = catalogServiceHelper.filterPermittedEntityRefs(accountId, entityRefsToCheck);
    log.info("{} Entity-level RBAC evaluated account={} deniedScopeIds={} candidateEntityRefs={} "
            + "permittedEntityRefs={} kind={}",
        GET_ENTITIES_FLOW_LOG, accountId, deniedUniqueIds.size(), entityRefsToCheck.size(), permittedEntityRefs.size(),
        kind);
    return permittedEntityRefs;
  }

  @Data
  @Builder
  @AllArgsConstructor
  public static class RbacResolveResult {
    Map<String, List<ScopeInfo>> resourceTypeToPermittedScopes;
    List<String> permittedEntityRefs;
  }
}
