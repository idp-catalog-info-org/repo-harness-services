/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.catalog.entities.CatalogEntity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;

@HarnessRepo
@OwnedBy(HarnessTeam.IDP)
public interface CatalogEntityRepository extends CrudRepository<CatalogEntity, String>, CatalogEntityRepositoryCustom {
  Optional<CatalogEntity> findByParentUniqueIdAndKindAndIdentifier(
      String parentUniqueId, String kind, String identifier);
  void deleteByParentUniqueIdAndKindAndIdentifier(String parentUniqueId, String kind, String identifier);

  List<CatalogEntity> findAllByParentUniqueIdAndKindIn(String parentUniqueId, List<String> kind);

  List<CatalogEntity> findAllByParentUniqueIdInAndKindInAndOwnerIn(
      List<String> parentUniqueIds, List<String> kinds, List<String> owners);

  List<CatalogEntity> findAllByParentUniqueIdAndKind(String parentUniqueId, String kind);

  @Query(value = "{ 'parentUniqueId': ?0, 'kind': ?1, 'identifier': { $in: ?2 } }",
      fields = "{'identifier': 1, 'name': 1, 'description': 1, 'parentUniqueId': 1,'accountIdentifier': '1', "
          + "'projectIdentifier': '1', 'orgIdentifier': '1', 'kind': 1, 'type': 1, 'owner': 1, '_class': 1 , 'id':1}")
  List<CatalogEntity>
  findAllByParentUniqueIdAndKindAndIdentifierIn(String parentUniqueId, String kind, List<String> identifier);

  List<CatalogEntity> findByParentUniqueIdInAndUniqueIdIn(List<String> parentUniqueIds, List<String> uniqueIds);

  @Query(value = "{ 'parentUniqueId': { $in: ?0 } }",
      fields = "{ 'kind': 1, 'identifier': 1, 'orgIdentifier': 1, 'projectIdentifier': 1, '_class': 1 }")
  List<CatalogEntity>
  findKindIdentifierScopeByParentUniqueIdIn(List<String> parentUniqueIds);

  @Query(value = "{ 'parentUniqueId': ?0, 'kind': { $in: ?1 }, 'identifier': { $in: ?2 } }",
      fields = "{ 'kind': 1, 'identifier': 1, '_class': 1 }")
  List<CatalogEntity>
  findKindAndIdentifierByParentUniqueIdAndKindInAndIdentifierIn(
      String parentUniqueId, List<String> kinds, List<String> identifiers);

  @Query("{ 'accountIdentifier' : ?0, 'kind' : ?1, 'identifier': { '$regex': '^_', '$options': '' } }")
  List<CatalogEntity> findAllByAccountIdentifierAndKindAndIdentifierStartingWithUnderscore(
      String accountIdentifier, String kind);

  List<CatalogEntity> findAllByParentUniqueId(String parentUniqueId);

  @Query(value = "{ 'accountIdentifier': ?0 }", fields = "{ 'kind': 1, '_class': 1 }")
  List<CatalogEntity> findAllByAccountIdentifierAndReturnProjectedFields(String accountIdentifier);

  @Query(value = "{ 'accountIdentifier': ?0 }", fields = "{ 'orgIdentifier': 1, 'parentUniqueId': 1, '_class': 1 }")
  List<CatalogEntity> findAllByAccountIdentifierAndOrgIdentifierIsNotNullAndProjectIdentifierIsNull(
      String accountIdentifier);

  @Query(value = "{ 'accountIdentifier': ?0 }",
      fields = "{ 'orgIdentifier': 1, 'projectIdentifier': 1, 'parentUniqueId': 1, '_class': 1 }")
  List<CatalogEntity>
  findAllByAccountIdentifierAndOrgIdentifierIsNotNullAndProjectIdentifierIsNotNull(String accountIdentifier);

  @Query(value = "{ 'accountIdentifier': ?0, 'orgIdentifier': ?1 }",
      fields = "{ 'orgIdentifier': 1, 'projectIdentifier': 1, 'parentUniqueId': 1, '_class': 1 }")
  List<CatalogEntity>
  findAllByAccountIdentifierAndOrgIdentifierIs(String accountIdentifier, String orgIdentifier);

  @Query(value = "{ 'accountIdentifier': ?0, 'orgIdentifier': ?1 }",
      fields = "{ 'orgIdentifier': 1, 'parentUniqueId': 1, '_class': 1 }")
  List<CatalogEntity>
  findAllByAccountIdentifierAndOrgIdentifierIsAndProjectIdentifierIsNull(
      String accountIdentifier, String orgIdentifier);

  List<CatalogEntity> findAllByAccountIdentifierAndOrgIdentifierIsAndProjectIdentifierIs(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);

  long countByParentUniqueIdAndKindIn(String parentUniqueId, List<String> kinds);

  Page<CatalogEntity> findByParentUniqueIdAndKind(String parentUniqueId, String kind, Pageable pageable);

  List<CatalogEntity> findByUniqueIdIn(List<String> uniqueIds);

  List<CatalogEntity> findByParentUniqueIdInAndKind(List<String> uniqueIds, String kind);

  @Query(value = "{ 'parentUniqueId': { $in: ?0 }, 'kind': { $in: ?1 } }",
      fields = "{ 'kind': 1, 'identifier': 1, 'orgIdentifier': 1, 'projectIdentifier': 1, '_class': 1 }")
  List<CatalogEntity>
  findKindIdentifierScopeByParentUniqueIdInAndKindIn(List<String> parentUniqueIds, List<String> kinds);

  List<CatalogEntity> findAllByAccountIdentifierAndKind(String accountIdentifier, String kind);

  Optional<CatalogEntity> findByAccountIdentifierAndQueryableEntityRef(
      String accountIdentifier, String queryableEntityRef);

  /**
   * Project-level entities of a given kind under an account. Hierarchy nodes representing projects live
   * at {@code (accountIdentifier=X, orgIdentifier=*, projectIdentifier=*, kind=hierarchy)} — this method
   * fetches them in one query for the Comparison-by-Hierarchy card without going via the parent org's
   * uniqueId.
   */
  List<CatalogEntity> findAllByAccountIdentifierAndKindAndProjectIdentifierIsNotNull(
      String accountIdentifier, String kind);

  @Query(value = "{ 'accountIdentifier': ?0, 'orgIdentifier': ?1 }",
      fields = "{ 'kind': 1, 'identifier': 1, 'accountIdentifier': 1, 'orgIdentifier': 1, "
          + "'projectIdentifier': 1, '_class': 1 }")
  List<CatalogEntity>
  findEntitiesForOrgNode(String accountIdentifier, String orgIdentifier);

  @Query(value = "{ 'accountIdentifier': ?0, 'orgIdentifier': ?1, 'projectIdentifier': ?2 }",
      fields = "{ 'kind': 1, 'identifier': 1, 'accountIdentifier': 1, 'orgIdentifier': 1, "
          + "'projectIdentifier': 1, '_class': 1 }")
  List<CatalogEntity>
  findEntitiesForProjectNode(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  /**
   * Org hierarchy nodes for a specific set of orgs. Org hierarchy nodes live in the org scope, so their
   * {@code parentUniqueId} is the org's own uniqueId (not the account); they are identified by
   * {@code orgIdentifier} with {@code projectIdentifier == null} (which also matches absent). Mirrors
   * {@link #findHierarchyProjectNodesForKeys} by keying off scope fields rather than parentUniqueId.
   * Used by the Comparison-by-Hierarchy card to fetch only the top-N nodes (full docs, since the
   * decorator metadata feeds the aggregation-rule values).
   */
  @Query("{ 'accountIdentifier': ?0, 'kind': ?1, 'projectIdentifier': null, 'orgIdentifier': { $in: ?2 } }")
  List<CatalogEntity> findHierarchyOrgNodes(String accountIdentifier, String kind, List<String> orgIdentifiers);

  List<CatalogEntity> findAllByAccountIdentifier(String accountIdentifier);

  boolean existsByAccountIdentifier(String accountIdentifier);

  @Query("{ 'accountIdentifier': ?0, 'orgIdentifier': ?1, 'projectIdentifier': ?2, 'kind': 'aiasset', "
      + "'decorator._processed_data.metadata.integration_properties.GitHub.asset_id': ?3 }")
  Optional<CatalogEntity>
  findByAccountIdentifierAndScopeAndGitHubAssetId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String assetId);
}
