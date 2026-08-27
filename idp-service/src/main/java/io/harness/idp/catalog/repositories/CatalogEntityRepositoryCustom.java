/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.idp.catalog.beans.HierarchyEntityCount;
import io.harness.idp.catalog.beans.ScopeData;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.IDP)
public interface CatalogEntityRepositoryCustom {
  Page<CatalogEntity> getEntities(String harnessAccount, List<ScopeInfo> scopeInfos, Integer page, Integer limit,
      String sort, String searchTerm, String requestedEntityRefs, String entityRefs, String kind, String type,
      String owner, String lifecycle, String tags, String arbitraryFields, String filter);
  Page<CatalogEntity> getEntities(String harnessAccount, List<ScopeInfo> scopeInfos, Integer page, Integer limit,
      String sort, String searchTerm, String requestedEntityRefs, String entityRefs, String permittedEntityRefs,
      String kind, String type, String owner, String lifecycle, String tags, String arbitraryFields, String filter,
      boolean entityRefAndCriteria);
  Optional<CatalogEntity> findUserBasedOnAccountIdAndUUID(String accountIdentifier, String uuid);
  long getOwnedEntitiesCount(List<String> parentUniqueIds, String kind, String owner, String harnessAccount,
      List<ScopeInfo> scopeInfos, String requestedEntityRefs, boolean entityRefAndCriteria, String filter);
  long getOwnedEntitiesCountWithKindScopes(String harnessAccount,
      Map<String, List<ScopeInfo>> resourceTypeToPermittedScopes, List<String> permittedEntityRefs,
      List<ScopeInfo> allRequestedScopeInfos, String kind, String owner, String requestedEntityRefs,
      boolean entityRefAndCriteria, String filter);
  long getFavoritesEntitiesCount(String harnessAccount, List<ScopeInfo> scopeInfos, String favoriteEntityRefs,
      String requestedEntityRefs, boolean entityRefAndCriteria, String filter);
  long getFavoritesEntitiesCountWithGroupFallback(String harnessAccount, List<ScopeInfo> scopeInfos,
      String favoriteEntityRefs, String requestedEntityRefs, String deniedFavoriteEntityRefs,
      String permittedGroupEntityRefs, boolean entityRefAndCriteria, String filter);
  Page<CatalogEntity> getFavoritesEntitiesPageWithGroupFallback(String harnessAccount, List<ScopeInfo> scopeInfos,
      String favoriteEntityRefs, String requestedEntityRefs, String deniedFavoriteEntityRefs,
      String permittedGroupEntityRefs, boolean entityRefAndCriteria, String filter, Integer page, Integer limit,
      String sort, String searchTerm, String kind, String type, String owner, String lifecycle, String tags);
  List<CatalogEntity> getEntitiesForArbitraryFields(
      String accountIdentifier, Map<String, Object> arbitraryFields, String kind);
  Page<CatalogEntity> findAll(Criteria criteria, Pageable pageable);
  GitReferencedCatalogEntity getRemoteServiceWithYaml(
      GitReferencedCatalogEntity gitReferencedCatalogEntity, boolean loadFromCache, boolean loadFromFallbackBranch);
  Long countFileInstances(
      String accountIdentifier, String repoURL, String filePath, String connectorRef, String repoName);
  CatalogEntity findByFilePathAndRepo(String accountIdentifier, String filePath, String repo);
  List<String> findDistinctOrgIdentifiersByAccountIdentifierAndProjectIdentifierIsNull(String accountIdentifier);
  Map<String, Set<String>> findDistinctProjectIdentifiersByOrgIdentifierForAccount(String accountIdentifier);
  List<ScopeData> findDistinctScopeData(String accountIdentifier);

  /**
   * Top {@code limit} Orgs by number of catalog entities under them (across all of their projects),
   * ordered by count descending then orgIdentifier ascending. Entities whose {@code kind} equals
   * {@code excludedKind} (the structural hierarchy nodes) are not counted. The sort + limit run inside the
   * Mongo aggregation so the Comparison-by-Hierarchy card never materializes counts for nodes it discards.
   */
  List<HierarchyEntityCount> topEntityCountsByOrg(String accountIdentifier, String excludedKind, int limit);

  /**
   * Top {@code limit} Projects by number of catalog entities in them, ordered by count descending then
   * (orgIdentifier, projectIdentifier) ascending. See {@link #topEntityCountsByOrg}.
   */
  List<HierarchyEntityCount> topEntityCountsByOrgAndProject(String accountIdentifier, String excludedKind, int limit);

  /**
   * Project hierarchy nodes matching the given (org, project) keys. Returns full docs (the decorator
   * metadata feeds the card's aggregation-rule values).
   */
  List<CatalogEntity> findHierarchyProjectNodesForKeys(
      String accountIdentifier, String kind, List<HierarchyEntityCount> keys);
  List<String> findDistinctAccountIdentifiers();
  void convertInlineToGit(ScopeInfo scopeInfo, String kind, String identifier);
  List<CatalogEntity> getEntitiesFilters(List<String> parentUniqueId, List<String> kinds, String filter);
  long countByParentUniqueIdAndKindInAndFilter(String parentUniqueId, List<String> kinds, String filter);

  List<CatalogEntity> getEntitiesForEntityRefsAndKinds(
      String accountIdentifier, String entityRefs, List<ScopeInfo> scopeInfos, List<String> kinds);

  Page<CatalogEntity> findEnvironmentsByBlueprintIdentifier(String accountIdentifier, String blueprintIdentifier,
      List<String> environmentParentUniqueIds, String searchTerm, List<String> permittedEnvironmentRefs,
      List<ScopeInfo> scopeInfos, Pageable pageable);

  Page<CatalogEntity> findEntitiesByRelationRefs(String accountIdentifier, List<String> entityRefs, String searchTerm,
      List<ScopeInfo> scopeInfos, Pageable pageable, String kind, String type, String owner, String lifecycle,
      String tags, String filter);

  void addContentFile(String accountIdentifier, String uniqueId, String filePath, String label);

  Page<CatalogEntity> getEntitiesWithKindScopes(String harnessAccount,
      Map<String, List<ScopeInfo>> resourceTypeToPermittedScopes, List<String> permittedEntityRefs,
      List<ScopeInfo> allRequestedScopeInfos, Integer page, Integer limit, String sort, String searchTerm,
      String requestedEntityRefs, String entityRefs, String kind, String type, String owner, String lifecycle,
      String tags, String filter, boolean entityRefAndCriteria, List<String> permittedGroupEntityRefs);

  long countEntitiesByKindTypeAndScopes(
      String accountIdentifier, List<Pair<String, String>> kindTypePairs, Set<String> parentUniqueIds);

  Page<CatalogEntity> findRootTeamsByPermittedRefs(List<String> parentUniqueIds, Set<String> permittedEntityRefs,
      String searchTerm, Pageable pageable, String harnessAccount, List<ScopeInfo> scopeInfos);

  List<CatalogEntity> findAllTeamsInScopes(List<String> parentUniqueIds, Boolean custom);
}
