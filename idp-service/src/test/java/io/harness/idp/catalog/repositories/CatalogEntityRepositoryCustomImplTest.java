/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.repositories;

import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.NON_INHERITABLE_KINDS;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.rbac.KindResourceTypeMapper;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class CatalogEntityRepositoryCustomImplTest extends CategoryTest {
  static final String TEST_PARENT_UNIQUE_ID = "testParentUniqueId";
  AutoCloseable openMocks;

  @InjectMocks private CatalogEntityRepositoryCustomImpl catalogEntityRepositoryCustom;

  @Mock private MongoTemplate mongoTemplate;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetFavoritesEntitiesCountWithEmptyFavoriteEntityRefs() {
    long favoritesEntitiesCount = catalogEntityRepositoryCustom.getFavoritesEntitiesCount(TEST_PARENT_UNIQUE_ID,
        List.of(ScopeInfo.builder().uniqueId(TEST_PARENT_UNIQUE_ID).build()), null, null, false, null);
    assertThat(favoritesEntitiesCount).isEqualTo(0);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetFavoritesEntitiesCount() {
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntity.class))).thenReturn(3L);
    long favoritesEntitiesCount = catalogEntityRepositoryCustom.getFavoritesEntitiesCount(TEST_PARENT_UNIQUE_ID,
        List.of(ScopeInfo.builder().uniqueId(TEST_PARENT_UNIQUE_ID).build()),
        "test,component:test1,,component:default/test2", null, false, null);
    assertThat(favoritesEntitiesCount).isEqualTo(3);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOwnedEntitiesCountWithEmptyOwner() {
    long ownedEntitiesCount = catalogEntityRepositoryCustom.getOwnedEntitiesCount(
        List.of(TEST_PARENT_UNIQUE_ID), null, null, null, null, null, false, null);
    assertThat(ownedEntitiesCount).isEqualTo(0);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOwnedEntitiesCount() {
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntity.class))).thenReturn(4L);
    long ownedEntitiesCount = catalogEntityRepositoryCustom.getOwnedEntitiesCount(List.of(TEST_PARENT_UNIQUE_ID), null,
        "test,group:test1,user:test2,group:default/test3", null, null, null, false, null);
    assertThat(ownedEntitiesCount).isEqualTo(4);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOwnedEntitiesCountWithKindScopesWithEmptyOwner() {
    long ownedEntitiesCount = catalogEntityRepositoryCustom.getOwnedEntitiesCountWithKindScopes(
        TEST_PARENT_UNIQUE_ID, Map.of(), List.of(), List.of(), null, null, null, false, null);

    assertThat(ownedEntitiesCount).isEqualTo(0);
    verify(mongoTemplate, never()).count(any(Query.class), eq(CatalogEntity.class));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOwnedEntitiesCountWithKindScopesWithNoPermittedScopesOrRefs() {
    long ownedEntitiesCount = catalogEntityRepositoryCustom.getOwnedEntitiesCountWithKindScopes(
        TEST_PARENT_UNIQUE_ID, Map.of(), List.of(), List.of(), null, "user:account/user1", null, false, null);

    assertThat(ownedEntitiesCount).isEqualTo(0);
    verify(mongoTemplate, never()).count(any(Query.class), eq(CatalogEntity.class));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOwnedEntitiesCountWithKindScopesAppliesRbacKindOwnerAndFilterCriteria() {
    ScopeInfo catalogScope = ScopeInfo.builder().uniqueId("catalogScope").build();
    ScopeInfo workflowScope = ScopeInfo.builder().uniqueId("workflowScope").build();
    Map<String, List<ScopeInfo>> permittedScopes = Map.of(KindResourceTypeMapper.CATALOG_RESOURCE_TYPE,
        List.of(catalogScope), KindResourceTypeMapper.WORKFLOW_RESOURCE_TYPE, List.of(workflowScope));
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntity.class))).thenReturn(6L);

    long ownedEntitiesCount = catalogEntityRepositoryCustom.getOwnedEntitiesCountWithKindScopes(TEST_PARENT_UNIQUE_ID,
        permittedScopes, List.of(), List.of(catalogScope, workflowScope), "component,workflow",
        "group:account/platform,user:account/user1", null, false, "spec.lifecycle=production");

    assertThat(ownedEntitiesCount).isEqualTo(6);
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).count(queryCaptor.capture(), eq(CatalogEntity.class));
    Document query = queryCaptor.getValue().getQueryObject();
    assertThat(findValues(query, CatalogEntity.CatalogKeys.parentUniqueId))
        .contains(new Document("$in", List.of("catalogScope")), new Document("$in", List.of("workflowScope")));
    assertThat(findValues(query, CatalogEntity.CatalogKeys.kind))
        .contains(new Document("$nin", KindResourceTypeMapper.SPECIAL_KINDS), new Document("$in", List.of("workflow")),
            new Document("$in", List.of("component", "workflow")));
    assertThat(findValues(query, CatalogEntity.CatalogKeys.owner))
        .contains("group:account/platform", "user:account/user1");
    assertThat(findValues(query, "spec.lifecycle")).contains("production");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOwnedEntitiesCountWithKindScopesIncludesEntityLevelPermission() {
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_PARENT_UNIQUE_ID)
                             .orgIdentifier("org1")
                             .uniqueId("orgUniqueId")
                             .build();
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntity.class))).thenReturn(1L);

    long ownedEntitiesCount = catalogEntityRepositoryCustom.getOwnedEntitiesCountWithKindScopes(TEST_PARENT_UNIQUE_ID,
        Map.of(), List.of("component:account.org1/service1"), List.of(orgScope), "component", "group:account/platform",
        null, false, null);

    assertThat(ownedEntitiesCount).isEqualTo(1);
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).count(queryCaptor.capture(), eq(CatalogEntity.class));
    assertThat(findValues(queryCaptor.getValue().getQueryObject(), CatalogEntity.CatalogKeys.queryableEntityRef))
        .contains("orgUniqueId/component/service1");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOwnedEntitiesCountWithKindScopesAppliesRequestedRefs() {
    ScopeInfo accountScope =
        ScopeInfo.builder().accountIdentifier(TEST_PARENT_UNIQUE_ID).uniqueId(TEST_PARENT_UNIQUE_ID).build();
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntity.class))).thenReturn(1L);

    long ownedEntitiesCount = catalogEntityRepositoryCustom.getOwnedEntitiesCountWithKindScopes(TEST_PARENT_UNIQUE_ID,
        Map.of(KindResourceTypeMapper.CATALOG_RESOURCE_TYPE, List.of(accountScope)), List.of(), List.of(accountScope),
        "component", "group:account/platform", "component:account/service1", true, null);

    assertThat(ownedEntitiesCount).isEqualTo(1);
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).count(queryCaptor.capture(), eq(CatalogEntity.class));
    assertThat(findValues(queryCaptor.getValue().getQueryObject(), CatalogEntity.CatalogKeys.queryableEntityRef))
        .contains(TEST_PARENT_UNIQUE_ID + "/component/service1");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetOwnedEntitiesCountWithKindScopesFailsClosedForUnresolvableRequestedRefs() {
    ScopeInfo accountScope =
        ScopeInfo.builder().accountIdentifier(TEST_PARENT_UNIQUE_ID).uniqueId(TEST_PARENT_UNIQUE_ID).build();

    long ownedEntitiesCount = catalogEntityRepositoryCustom.getOwnedEntitiesCountWithKindScopes(TEST_PARENT_UNIQUE_ID,
        Map.of(KindResourceTypeMapper.CATALOG_RESOURCE_TYPE, List.of(accountScope)), List.of(), List.of(accountScope),
        "component", "group:account/platform", "component:account.missing/service1", true, null);

    assertThat(ownedEntitiesCount).isEqualTo(0);
    verify(mongoTemplate, never()).count(any(Query.class), eq(CatalogEntity.class));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFindUserBasedOnAccountIdAndUUID() {
    InlineCatalogEntity inlineCatalogEntity = InlineCatalogEntity.builder()
                                                  .kind(GROUP_KIND)
                                                  .identifier("identifier")
                                                  .metadata(Map.of("uuid", "uuid"))
                                                  .build();
    when(mongoTemplate.findOne(any(Query.class), eq(CatalogEntity.class))).thenReturn(inlineCatalogEntity);
    Optional<CatalogEntity> optionalCatalogEntity =
        catalogEntityRepositoryCustom.findUserBasedOnAccountIdAndUUID(TEST_PARENT_UNIQUE_ID, "uuid");
    assertThat(optionalCatalogEntity).isNotEmpty();
    assertThat(optionalCatalogEntity.get()).isEqualTo(inlineCatalogEntity);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntities() {
    List<CatalogEntity> inlineCatalogEntities = List.of(InlineCatalogEntity.builder().build());
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntity.class))).thenReturn(4L);
    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class))).thenReturn(inlineCatalogEntities);
    Page<CatalogEntity> catalogEntities = catalogEntityRepositoryCustom.getEntities(TEST_PARENT_UNIQUE_ID,
        List.of(ScopeInfo.builder().uniqueId(TEST_PARENT_UNIQUE_ID).build()), 0, 10, "name,asc", "s1", null,
        "test,component:test1,,component:default/test2", "component", "service", null, "production", "tag1,tag2",
        "test1=test2", null);
    assertThat(catalogEntities).isNotEmpty();
    assertThat(catalogEntities.getTotalElements()).isEqualTo(1);
    assertThat(catalogEntities.getContent()).isEqualTo(inlineCatalogEntities);
    assertThat(catalogEntities.getPageable().getPageNumber()).isEqualTo(0);
    assertThat(catalogEntities.getPageable().getPageSize()).isEqualTo(10);

    catalogEntities = catalogEntityRepositoryCustom.getEntities(TEST_PARENT_UNIQUE_ID,
        List.of(ScopeInfo.builder().uniqueId(TEST_PARENT_UNIQUE_ID).build()), 0, 10, "name,desc", "s1", null,
        "test,component:test1,,component:default/test2", "component", "service", null, "production", "tag1,tag2",
        "test1=test2", null);
    assertThat(catalogEntities).isNotEmpty();
    assertThat(catalogEntities.getTotalElements()).isEqualTo(1);
    assertThat(catalogEntities.getContent()).isEqualTo(inlineCatalogEntities);
    assertThat(catalogEntities.getPageable().getPageNumber()).isEqualTo(0);
    assertThat(catalogEntities.getPageable().getPageSize()).isEqualTo(10);

    catalogEntities = catalogEntityRepositoryCustom.getEntities(TEST_PARENT_UNIQUE_ID,
        List.of(ScopeInfo.builder().uniqueId(TEST_PARENT_UNIQUE_ID).build()), 0, 10, "identifier,asc", "s1", null,
        "test,component:test1,,component:default/test2", "component", "service", null, "production", "tag1,tag2",
        "test1=test2", null);
    assertThat(catalogEntities).isNotEmpty();
    assertThat(catalogEntities.getTotalElements()).isEqualTo(1);
    assertThat(catalogEntities.getContent()).isEqualTo(inlineCatalogEntities);
    assertThat(catalogEntities.getPageable().getPageNumber()).isEqualTo(0);
    assertThat(catalogEntities.getPageable().getPageSize()).isEqualTo(10);

    catalogEntities = catalogEntityRepositoryCustom.getEntities(TEST_PARENT_UNIQUE_ID,
        List.of(ScopeInfo.builder().uniqueId(TEST_PARENT_UNIQUE_ID).build()), 0, 10, "identifier,desc", "s1", null,
        "test,component:test1,,component:default/test2", "component", "service", null, "production", "tag1,tag2",
        "test1=test2", null);
    assertThat(catalogEntities).isNotEmpty();
    assertThat(catalogEntities.getTotalElements()).isEqualTo(1);
    assertThat(catalogEntities.getContent()).isEqualTo(inlineCatalogEntities);
    assertThat(catalogEntities.getPageable().getPageNumber()).isEqualTo(0);
    assertThat(catalogEntities.getPageable().getPageSize()).isEqualTo(10);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindRootTeamsByPermittedRefsReturnsPermittedRootsWithCorrectTotalElements() {
    List<CatalogEntity> permittedRoots = List.of(InlineCatalogEntity.builder()
                                                     .accountIdentifier("testAccount")
                                                     .kind(GROUP_KIND)
                                                     .identifier("team1")
                                                     .name("Team One")
                                                     .build());
    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class))).thenReturn(permittedRoots);
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntity.class))).thenReturn(2L);

    Set<String> permittedRefs = Set.of("group:account/team1", "group:account/team2");
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("testAccount")
                              .uniqueId("testAccount")
                              .scopeType(ScopeLevel.ACCOUNT)
                              .build();
    Page<CatalogEntity> result = catalogEntityRepositoryCustom.findRootTeamsByPermittedRefs(
        List.of(TEST_PARENT_UNIQUE_ID), permittedRefs, null, PageRequest.of(0, 10), "testAccount", List.of(scopeInfo));

    assertThat(result.getContent()).isEqualTo(permittedRoots);
    assertThat(result.getTotalElements()).isGreaterThan(0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindRootTeamsByPermittedRefsEmptyWhenNoPermittedRefs() {
    Page<CatalogEntity> result = catalogEntityRepositoryCustom.findRootTeamsByPermittedRefs(
        List.of(TEST_PARENT_UNIQUE_ID), Collections.emptySet(), null, PageRequest.of(0, 10), "testAccount",
        List.of(ScopeInfo.builder()
                    .accountIdentifier("testAccount")
                    .uniqueId("testAccount")
                    .scopeType(ScopeLevel.ACCOUNT)
                    .build()));

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindRootTeamsByPermittedRefsEmptyWhenParentUniqueIdsEmpty() {
    Set<String> permittedRefs = Set.of("group:account/team1");
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("testAccount")
                              .uniqueId("testAccount")
                              .scopeType(ScopeLevel.ACCOUNT)
                              .build();
    Page<CatalogEntity> result = catalogEntityRepositoryCustom.findRootTeamsByPermittedRefs(
        Collections.emptyList(), permittedRefs, null, PageRequest.of(0, 10), "testAccount", List.of(scopeInfo));

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindRootTeamsByPermittedRefsAppliesSearchTerm() {
    List<CatalogEntity> matchingRoots = List.of(InlineCatalogEntity.builder()
                                                    .accountIdentifier("testAccount")
                                                    .kind(GROUP_KIND)
                                                    .identifier("engineering")
                                                    .name("Engineering Team")
                                                    .build());
    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class))).thenReturn(matchingRoots);
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntity.class))).thenReturn(1L);

    Set<String> permittedRefs = Set.of("group:account/engineering");
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier("testAccount")
                              .uniqueId("testAccount")
                              .scopeType(ScopeLevel.ACCOUNT)
                              .build();
    Page<CatalogEntity> result =
        catalogEntityRepositoryCustom.findRootTeamsByPermittedRefs(List.of(TEST_PARENT_UNIQUE_ID), permittedRefs,
            "engineering", PageRequest.of(0, 10), "testAccount", List.of(scopeInfo));

    assertThat(result.getContent()).isEqualTo(matchingRoots);
    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindAllTeamsInScopesReturnsAllGroupEntities() {
    List<CatalogEntity> allTeams = List.of(InlineCatalogEntity.builder().kind(GROUP_KIND).identifier("team1").build(),
        InlineCatalogEntity.builder().kind(GROUP_KIND).identifier("team2").build());
    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class))).thenReturn(allTeams);

    List<CatalogEntity> result =
        catalogEntityRepositoryCustom.findAllTeamsInScopes(List.of(TEST_PARENT_UNIQUE_ID), true);

    assertThat(result).isEqualTo(allTeams);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFindAllTeamsInScopesEmptyWhenParentUniqueIdsEmpty() {
    List<CatalogEntity> result = catalogEntityRepositoryCustom.findAllTeamsInScopes(Collections.emptyList(), true);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetFavoritesEntitiesPageWithGroupFallbackEmptyWhenNoFavoritesOrFallback() {
    Page<CatalogEntity> result = catalogEntityRepositoryCustom.getFavoritesEntitiesPageWithGroupFallback(
        TEST_PARENT_UNIQUE_ID, List.of(ScopeInfo.builder().uniqueId(TEST_PARENT_UNIQUE_ID).build()), null, null, null,
        null, false, null, 0, 10, null, null, null, null, null, null, null);

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
    verify(mongoTemplate, never()).count(any(Query.class), eq(CatalogEntity.class));
    verify(mongoTemplate, never()).find(any(Query.class), eq(CatalogEntity.class));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetFavoritesEntitiesPageWithGroupFallbackAppliesGroupFallbackBranch() {
    ScopeInfo accountScope =
        ScopeInfo.builder().accountIdentifier(TEST_PARENT_UNIQUE_ID).uniqueId(TEST_PARENT_UNIQUE_ID).build();
    List<CatalogEntity> teamOwnedFavorites = List.of(
        InlineCatalogEntity.builder().kind("component").identifier("comp1").owner("group:account/team1").build());
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntity.class))).thenReturn(1L);
    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class))).thenReturn(teamOwnedFavorites);

    Page<CatalogEntity> result = catalogEntityRepositoryCustom.getFavoritesEntitiesPageWithGroupFallback(
        TEST_PARENT_UNIQUE_ID, List.of(accountScope), null, null, "component:account/comp1", "group:account/team1",
        false, null, 0, 10, null, null, null, null, null, null, null);

    assertThat(result.getContent()).isEqualTo(teamOwnedFavorites);
    assertThat(result.getTotalElements()).isEqualTo(1);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(CatalogEntity.class));
    Document query = queryCaptor.getValue().getQueryObject();
    assertThat(findValues(query, CatalogEntity.CatalogKeys.owner))
        .contains(new Document("$in", List.of("group:account/team1")));
    assertThat(findValues(query, CatalogEntity.CatalogKeys.queryableEntityRef))
        .contains(TEST_PARENT_UNIQUE_ID + "/component/comp1");

    // The group-ownership (indirect) favorites branch must be capped to inheritable kinds via $nin on the
    // non-inheritable kinds, so a favorited non-inheritable entity (e.g. group) owned by a viewable group is not
    // leaked without direct access.
    List<Object> kindValues = findValues(query, CatalogEntity.CatalogKeys.kind);
    Optional<Object> ninOnKind =
        kindValues.stream().filter(v -> v instanceof Document d && d.containsKey("$nin")).findFirst();
    assertThat(ninOnKind).isPresent();
    @SuppressWarnings("unchecked")
    Collection<String> ninKinds = (Collection<String>) ((Document) ninOnKind.get()).get("$nin");
    assertThat(ninKinds).containsExactlyInAnyOrderElementsOf(NON_INHERITABLE_KINDS);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetEntitiesWithKindScopesRestrictsGroupFallbackToInheritableKinds() {
    ScopeInfo accountScope =
        ScopeInfo.builder().accountIdentifier(TEST_PARENT_UNIQUE_ID).uniqueId(TEST_PARENT_UNIQUE_ID).build();
    when(mongoTemplate.count(any(Query.class), eq(CatalogEntity.class))).thenReturn(1L);
    when(mongoTemplate.find(any(Query.class), eq(CatalogEntity.class)))
        .thenReturn(List.of(
            InlineCatalogEntity.builder().kind("component").identifier("comp1").owner("group:account/team1").build()));

    // No direct-scope/direct-ref access; only indirect access via a viewable group. Requested kinds mix an
    // inheritable (component) and a non-inheritable (workflow) kind.
    Page<CatalogEntity> result = catalogEntityRepositoryCustom.getEntitiesWithKindScopes(TEST_PARENT_UNIQUE_ID,
        Collections.emptyMap(), List.of(), List.of(accountScope), 0, 10, null, null, null, null, "component,workflow",
        null, null, null, null, null, false, List.of("group:account/team1"));

    assertThat(result.getTotalElements()).isEqualTo(1);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(CatalogEntity.class));
    Document query = queryCaptor.getValue().getQueryObject();

    List<Object> kindValues = findValues(query, CatalogEntity.CatalogKeys.kind);
    // The owned-by-group (indirect) branch is capped to inheritable kinds via $nin on non-inheritable kinds,
    // so a team-owned workflow can never be resolved indirectly.
    Optional<Object> ninOnKind =
        kindValues.stream().filter(v -> v instanceof Document d && d.containsKey("$nin")).findFirst();
    assertThat(ninOnKind).isPresent();
    @SuppressWarnings("unchecked")
    Collection<String> ninKinds = (Collection<String>) ((Document) ninOnKind.get()).get("$nin");
    assertThat(ninKinds).containsExactlyInAnyOrderElementsOf(NON_INHERITABLE_KINDS);
    // The outer kind filter still permits both requested kinds (workflow may still be resolved via direct access).
    assertThat(kindValues).contains(new Document("$in", List.of("component", "workflow")));
    // The indirect branch is scoped to the viewable group's ownership.
    assertThat(findValues(query, CatalogEntity.CatalogKeys.owner)).contains("group:account/team1");
  }

  private List<Object> findValues(Object node, String key) {
    List<Object> values = new ArrayList<>();
    if (node instanceof Map<?, ?> map) {
      if (map.containsKey(key)) {
        values.add(map.get(key));
      }
      map.values().forEach(value -> values.addAll(findValues(value, key)));
    } else if (node instanceof Iterable<?> iterable) {
      iterable.forEach(value -> values.addAll(findValues(value, key)));
    }
    return values;
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
