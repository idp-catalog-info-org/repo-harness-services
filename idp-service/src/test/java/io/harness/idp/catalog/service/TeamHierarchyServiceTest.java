/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.idp.catalog.utils.Constants.CHILD_OF;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.OWNER_OF;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.beans.KindType;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.beans.TeamHierarchyResult;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.BuiltInKindEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.strategy.BottomUpTeamHierarchyAclStrategy;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.TeamHierarchyNode;

import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@OwnedBy(HarnessTeam.IDP)
public class TeamHierarchyServiceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "testAccount";
  private static final String TEST_SCOPES = "account";
  private static final String ORG_ID = "org1";
  private static final String ORG_UNIQUE_ID = "testAccount/org1";
  private static final String ROOT_IDENTIFIER = "root1";
  private static final String CHILD_IDENTIFIER = "child1";
  private static final String ROOT_REF = "group:account/root1";
  private static final String CHILD_REF = "group:account.org1/child1";

  @Mock private CatalogScopeResolver catalogScopeResolver;
  @Mock private CatalogOrgProjectService orgProjectService;
  @Mock private CatalogServiceHelper catalogServiceHelper;
  @Mock private CatalogEntityRepository catalogEntityRepository;
  @Mock private KindServiceHelper kindServiceHelper;
  @Mock private ScorecardService scorecardService;
  @Mock private ScorecardScoreHelper scorecardScoreHelper;
  // Use a REAL bottom-up strategy so visibleRootRefs actually computes from the tree + permitted refs; the strategy
  // is pure logic (no external deps) and is covered separately in BottomUpTeamHierarchyAclStrategyTest.
  private final BottomUpTeamHierarchyAclStrategy bottomUpTeamHierarchyAclStrategy =
      new BottomUpTeamHierarchyAclStrategy();

  private AutoCloseable openMocks;
  private TeamHierarchyService teamHierarchyService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    teamHierarchyService =
        new TeamHierarchyService(catalogScopeResolver, orgProjectService, catalogServiceHelper, catalogEntityRepository,
            kindServiceHelper, bottomUpTeamHierarchyAclStrategy, scorecardService, scorecardScoreHelper);

    when(catalogScopeResolver.resolve(eq(ACCOUNT_ID), eq(TEST_SCOPES))).thenReturn(buildScopeResolveResult());
    when(catalogServiceHelper.includeChildScopesIfApplicable(eq(TEST_SCOPES), eq(false))).thenReturn(TEST_SCOPES);
    when(catalogServiceHelper.includeChildScopesIfApplicable(eq(TEST_SCOPES), eq(true)))
        .thenReturn(TEST_SCOPES + "," + ORG_UNIQUE_ID);
    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_ID), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(orgProjectService.getOrgNames(eq(ACCOUNT_ID), anySet())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(ACCOUNT_ID), anySet(), any())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(ACCOUNT_ID))).thenReturn(Collections.emptyList());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyReturnsRootWithChildren() {
    CatalogEntity root = buildTeamEntity(ROOT_IDENTIFIER, null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity(CHILD_IDENTIFIER, ORG_ID, Map.of(CHILD_OF, Set.of(ROOT_REF)));

    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(true)))
        .thenReturn(List.of(root, child));
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             eq(List.of(ACCOUNT_ID)), eq(Set.of(ROOT_REF)), eq(null), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(List.of(root), PageRequest.of(0, 10), 1));
    when(orgProjectService.getOrgNames(eq(ACCOUNT_ID), eq(Set.of(ORG_ID)))).thenReturn(Map.of(ORG_ID, "Org One"));
    when(kindServiceHelper.findByAccountIdentifierIn(eq(ACCOUNT_ID)))
        .thenReturn(List.of(
            BuiltInKindEntity.builder().identifier(GROUP_KIND).icon("group-icon").kindType(KindType.BUILT_IN).build()));

    TeamHierarchyResult result =
        teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, null, null, null, null, true);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getPageSize()).isEqualTo(10);
    assertThat(result.getNodes()).hasSize(1);

    TeamHierarchyNode rootNode = result.getNodes().get(0);
    assertThat(rootNode.getIdentifier()).isEqualTo(ROOT_IDENTIFIER);
    assertThat(rootNode.getKindIcon()).isEqualTo("group-icon");
    assertThat(rootNode.getChildren()).hasSize(1);

    TeamHierarchyNode childNode = rootNode.getChildren().get(0);
    assertThat(childNode.getIdentifier()).isEqualTo(CHILD_IDENTIFIER);
    assertThat(childNode.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(childNode.getOrgName()).isEqualTo("Org One");
    assertThat(childNode.getKindIcon()).isEqualTo("group-icon");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyRbacFiltersRootTeam() {
    CatalogEntity root = buildTeamEntity(ROOT_IDENTIFIER, null, Collections.emptyMap());

    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(true))).thenReturn(List.of(root));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_ID), eq(Set.of(ROOT_REF)), eq("view")))
        .thenReturn(Collections.emptySet());
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             eq(List.of(ACCOUNT_ID)), eq(Collections.emptySet()), eq(null), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));

    TeamHierarchyResult result =
        teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, null, null, null, null, true);

    assertThat(result.getNodes()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyRbacTruncatesChildLevel() {
    CatalogEntity root = buildTeamEntity(ROOT_IDENTIFIER, null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity(CHILD_IDENTIFIER, ORG_ID, Map.of(CHILD_OF, Set.of(ROOT_REF)));

    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(true)))
        .thenReturn(List.of(root, child));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_ID), eq(Set.of(ROOT_REF, CHILD_REF)), eq("view")))
        .thenReturn(Set.of(ROOT_REF));
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             eq(List.of(ACCOUNT_ID)), eq(Set.of(ROOT_REF)), eq(null), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(List.of(root), PageRequest.of(0, 10), 1));

    TeamHierarchyResult result =
        teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, null, null, null, null, true);

    assertThat(result.getNodes()).hasSize(1);
    assertThat(result.getNodes().get(0).getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyIncludeChildScopesExpandsToDescendants() {
    CatalogEntity root = buildTeamEntity(ROOT_IDENTIFIER, null, Collections.emptyMap());

    CatalogScopeResolver.ScopeResolveResult expandedResult = buildExpandedScopeResolveResult();
    when(catalogScopeResolver.resolve(eq(ACCOUNT_ID), eq(TEST_SCOPES + "," + ORG_UNIQUE_ID)))
        .thenReturn(expandedResult);
    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID, ORG_UNIQUE_ID)), eq(true)))
        .thenReturn(List.of(root));
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             any(), any(), eq(null), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(List.of(root), PageRequest.of(0, 10), 1));

    teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, true, null, null, null, null, true);

    verify(catalogEntityRepository).findAllTeamsInScopes(eq(List.of(ACCOUNT_ID, ORG_UNIQUE_ID)), eq(true));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyDefaultPageAndLimit() {
    when(catalogEntityRepository.findAllTeamsInScopes(any(), eq(true))).thenReturn(Collections.emptyList());
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             any(), any(), eq("search1"), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));

    teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, null, null, null, "search1", true);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(catalogEntityRepository)
        .findRootTeamsByPermittedRefs(any(), any(), eq("search1"), pageableCaptor.capture(), eq(ACCOUNT_ID), any());
    assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    assertThat(pageableCaptor.getValue().getSort().isUnsorted()).isTrue();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchySortMappingAppliesRequestedDirection() {
    when(catalogEntityRepository.findAllTeamsInScopes(any(), eq(true))).thenReturn(Collections.emptyList());
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             any(), any(), eq(null), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(2, 5), 0));

    teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, 2, 5, "name,desc", null, true);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(catalogEntityRepository)
        .findRootTeamsByPermittedRefs(any(), any(), eq(null), pageableCaptor.capture(), eq(ACCOUNT_ID), any());
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isEqualTo(2);
    assertThat(pageable.getPageSize()).isEqualTo(5);
    assertThat(Objects.requireNonNull(pageable.getSort().getOrderFor("name")).isDescending()).isTrue();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyMaxTreeDepthBoundsLongChain() {
    int chainLength = 30;
    List<CatalogEntity> chain = buildTeamChain(chainLength);
    CatalogEntity root = chain.get(0);

    when(catalogEntityRepository.findAllTeamsInScopes(any(), eq(true))).thenReturn(chain);
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             any(), eq(Set.of("group:account/team0")), eq(null), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(List.of(root), PageRequest.of(0, 10), 1));

    TeamHierarchyResult result =
        teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, null, null, null, null, true);

    verify(catalogEntityRepository, times(1)).findAllTeamsInScopes(any(), eq(true));

    TeamHierarchyNode node = result.getNodes().get(0);
    int depthReached = 0;
    while (!node.getChildren().isEmpty()) {
      assertThat(node.getChildren()).hasSize(1);
      node = node.getChildren().get(0);
      depthReached++;
    }
    assertThat(depthReached).isEqualTo(25);
    assertThat(node.getIdentifier()).isEqualTo("team25");
  }

  private List<CatalogEntity> buildTeamChain(int chainLength) {
    List<CatalogEntity> chain = new ArrayList<>();
    for (int i = 0; i < chainLength; i++) {
      String identifier = "team" + i;
      Map<String, Set<String>> relations = new HashMap<>();
      if (i > 0) {
        relations.put(CHILD_OF, Set.of("group:account/team" + (i - 1)));
      }
      chain.add(buildTeamEntity(identifier, null, relations));
    }
    return chain;
  }

  private CatalogEntity buildTeamEntity(String identifier, String orgId, Map<String, Set<String>> relations) {
    String parentUniqueId = orgId != null ? ORG_UNIQUE_ID : ACCOUNT_ID;
    return InlineCatalogEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .parentUniqueId(parentUniqueId)
        .kind(GROUP_KIND)
        .identifier(identifier)
        .name(identifier)
        .orgIdentifier(orgId)
        .referenceType(ReferenceType.INLINE)
        .relations(relations)
        .spec(new HashMap<>())
        .metadata(new HashMap<>())
        .tags(List.of())
        .build();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyGrandchildReachableThroughPermittedChild() {
    CatalogEntity root = buildTeamEntity(ROOT_IDENTIFIER, null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity(CHILD_IDENTIFIER, ORG_ID, Map.of(CHILD_OF, Set.of(ROOT_REF)));
    String grandchildIdentifier = "grandchild1";
    String grandchildRef = "group:account.org1/grandchild1";
    CatalogEntity grandchild = buildTeamEntity(grandchildIdentifier, ORG_ID, Map.of(CHILD_OF, Set.of(CHILD_REF)));

    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(true)))
        .thenReturn(List.of(root, child, grandchild));
    when(catalogServiceHelper.checkEntityRefsPermission(
             eq(ACCOUNT_ID), eq(Set.of(ROOT_REF, CHILD_REF, grandchildRef)), eq("view")))
        .thenReturn(Set.of(ROOT_REF, CHILD_REF, grandchildRef));
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             eq(List.of(ACCOUNT_ID)), eq(Set.of(ROOT_REF)), eq(null), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(List.of(root), PageRequest.of(0, 10), 1));
    when(orgProjectService.getOrgNames(eq(ACCOUNT_ID), eq(Set.of(ORG_ID)))).thenReturn(Map.of(ORG_ID, "Org One"));

    TeamHierarchyResult result =
        teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, null, null, null, null, true);

    assertThat(result.getNodes()).hasSize(1);
    TeamHierarchyNode rootNode = result.getNodes().get(0);
    assertThat(rootNode.getChildren()).hasSize(1);
    TeamHierarchyNode childNode = rootNode.getChildren().get(0);
    assertThat(childNode.getIdentifier()).isEqualTo(CHILD_IDENTIFIER);
    assertThat(childNode.getChildren()).hasSize(1);
    TeamHierarchyNode grandchildNode = childNode.getChildren().get(0);
    assertThat(grandchildNode.getIdentifier()).isEqualTo(grandchildIdentifier);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyUnpermittedChildKeptAsPathToPermittedGrandchild() {
    // Bottom-up: child is NOT permitted but grandchild IS -> child is kept purely as a path to the permitted
    // grandchild (unlike top-down, which would drop the whole subtree at the unpermitted child).
    CatalogEntity root = buildTeamEntity(ROOT_IDENTIFIER, null, Collections.emptyMap());
    CatalogEntity child = buildTeamEntity(CHILD_IDENTIFIER, ORG_ID, Map.of(CHILD_OF, Set.of(ROOT_REF)));
    String grandchildIdentifier = "grandchild1";
    String grandchildRef = "group:account.org1/grandchild1";
    CatalogEntity grandchild = buildTeamEntity(grandchildIdentifier, ORG_ID, Map.of(CHILD_OF, Set.of(CHILD_REF)));

    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(true)))
        .thenReturn(List.of(root, child, grandchild));
    when(catalogServiceHelper.checkEntityRefsPermission(
             eq(ACCOUNT_ID), eq(Set.of(ROOT_REF, CHILD_REF, grandchildRef)), eq("view")))
        .thenReturn(Set.of(ROOT_REF, grandchildRef));
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             eq(List.of(ACCOUNT_ID)), eq(Set.of(ROOT_REF)), eq(null), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(List.of(root), PageRequest.of(0, 10), 1));

    TeamHierarchyResult result =
        teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, null, null, null, null, true);

    assertThat(result.getNodes()).hasSize(1);
    TeamHierarchyNode rootNode = result.getNodes().get(0);
    assertThat(rootNode.getChildren()).hasSize(1);
    TeamHierarchyNode childNode = rootNode.getChildren().get(0);
    assertThat(childNode.getIdentifier()).isEqualTo(CHILD_IDENTIFIER);
    assertThat(childNode.getChildren()).hasSize(1);
    assertThat(childNode.getChildren().get(0).getIdentifier()).isEqualTo(grandchildIdentifier);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyCycleGuardPreventsCyclicData() {
    CatalogEntity team1 = buildTeamEntity("team1", null, Map.of(CHILD_OF, Set.of("group:account/team2")));
    CatalogEntity team2 = buildTeamEntity("team2", null, Map.of(CHILD_OF, Set.of("group:account/team1")));

    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(true)))
        .thenReturn(List.of(team1, team2));
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             any(), any(), eq(null), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));

    TeamHierarchyResult result =
        teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, null, null, null, null, true);

    assertThat(result.getNodes()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchySearchTermPassedToPermittedRefs() {
    when(catalogEntityRepository.findAllTeamsInScopes(any(), eq(true))).thenReturn(Collections.emptyList());
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             any(), any(), eq("engineering"), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0));

    teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, null, null, null, "engineering", true);

    verify(catalogEntityRepository)
        .findRootTeamsByPermittedRefs(any(), any(), eq("engineering"), any(Pageable.class), eq(ACCOUNT_ID), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamHierarchyIncludeChildScopesFalseUsesRequestedScopesOnly() {
    CatalogEntity root = buildTeamEntity(ROOT_IDENTIFIER, null, Collections.emptyMap());

    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(true))).thenReturn(List.of(root));
    when(catalogEntityRepository.findRootTeamsByPermittedRefs(
             any(), any(), eq(null), any(Pageable.class), eq(ACCOUNT_ID), any()))
        .thenReturn(new PageImpl<>(List.of(root), PageRequest.of(0, 10), 1));

    teamHierarchyService.getTeamHierarchy(ACCOUNT_ID, TEST_SCOPES, false, null, null, null, null, true);

    verify(catalogEntityRepository).findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(true));
  }

  // ================= getTeamOwnedEntities =================

  private static final String TEAM_ENTITY_REF = "group:account/root1";

  private void stubTeamRefParsing() {
    when(catalogServiceHelper.getKindScopeIdentifier(TEAM_ENTITY_REF))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of(GROUP_KIND, "account", ROOT_IDENTIFIER));
    when(catalogServiceHelper.validateAndSanitizeKind(GROUP_KIND)).thenReturn(GROUP_KIND);
    when(catalogServiceHelper.validateAndSanitizeIdentifier(ROOT_IDENTIFIER)).thenReturn(ROOT_IDENTIFIER);
  }

  private static final String OWNED_ENTITY_REF = "component:account/svcA";

  // Stubs the paged accessible-entityRefs query (getEntities with entityRefs $in) and returns the given page content.
  private void stubAccessiblePageQuery(List<CatalogEntity> pageContent) {
    when(catalogEntityRepository.getEntities(eq(ACCOUNT_ID), any(), any(), any(), any(), any(), eq(null), any(),
             eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null)))
        .thenReturn(new PageImpl<>(pageContent, PageRequest.of(0, 10), pageContent.size()));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamOwnedEntitiesThrowsWhenNoTeamView() {
    stubTeamRefParsing();
    org.mockito.Mockito.doThrow(new io.harness.exception.InvalidRequestException("Missing Group view Permission"))
        .when(catalogServiceHelper)
        .checkCrudRbac(ACCOUNT_ID, null, null, GROUP_KIND, TEAM_ENTITY_REF, "view");

    org.assertj.core.api.Assertions
        .assertThatThrownBy(()
                                -> teamHierarchyService.getTeamOwnedEntities(
                                    ACCOUNT_ID, null, null, TEAM_ENTITY_REF, false, null, null, null, null))
        .isInstanceOf(io.harness.exception.InvalidRequestException.class)
        .hasMessageContaining("Missing Group view Permission");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamOwnedEntitiesTeamViewableReturnsAllItsEntities() {
    stubTeamRefParsing();
    // Team owns svcA via its persisted ownerOf relation (reverse of the entity's ownedBy).
    CatalogEntity team = buildTeamEntity(ROOT_IDENTIFIER, null, Map.of(OWNER_OF, Set.of(OWNED_ENTITY_REF)));
    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(null))).thenReturn(List.of(team));

    CatalogEntity owned = buildComponentEntity("svcA", TEAM_ENTITY_REF);

    // Team ref is viewable -> all its INHERITABLE-kind entities accessible (entity ref itself need NOT be permitted).
    when(catalogServiceHelper.checkEntityRefsPermission(
             eq(ACCOUNT_ID), eq(Set.of(TEAM_ENTITY_REF, OWNED_ENTITY_REF)), eq("view")))
        .thenReturn(Set.of(TEAM_ENTITY_REF));
    // svcA is a component (inheritable) -> team-based access applies.
    when(catalogServiceHelper.getKindScopeIdentifier(OWNED_ENTITY_REF))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("component", "account", "svcA"));
    when(catalogServiceHelper.isInheritableKind("component")).thenReturn(true);
    stubAccessiblePageQuery(List.of(owned));

    var result = teamHierarchyService.getTeamOwnedEntities(
        ACCOUNT_ID, null, null, TEAM_ENTITY_REF, false, null, null, null, null);

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getEntityResponses()).hasSize(1);
    // The accessible entityRefs passed to the paged query include the owned entity.
    verify(catalogEntityRepository)
        .getEntities(eq(ACCOUNT_ID), any(), any(), any(), any(), any(), eq(null), eq(OWNED_ENTITY_REF), eq(null),
            eq(null), eq(null), eq(null), eq(null), eq(null), eq(null));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamOwnedEntitiesTeamNotViewableButEntityDirectlyViewable() {
    stubTeamRefParsing();
    CatalogEntity team = buildTeamEntity(ROOT_IDENTIFIER, null, Map.of(OWNER_OF, Set.of(OWNED_ENTITY_REF)));
    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(null))).thenReturn(List.of(team));

    CatalogEntity owned = buildComponentEntity("svcA", TEAM_ENTITY_REF);

    // Team NOT viewable, but the entity itself IS directly viewable -> still accessible.
    when(catalogServiceHelper.checkEntityRefsPermission(
             eq(ACCOUNT_ID), eq(Set.of(TEAM_ENTITY_REF, OWNED_ENTITY_REF)), eq("view")))
        .thenReturn(Set.of(OWNED_ENTITY_REF));
    stubAccessiblePageQuery(List.of(owned));

    var result = teamHierarchyService.getTeamOwnedEntities(
        ACCOUNT_ID, null, null, TEAM_ENTITY_REF, false, null, null, null, null);

    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamOwnedEntitiesNeitherTeamNorEntityViewableExcluded() {
    stubTeamRefParsing();
    CatalogEntity team = buildTeamEntity(ROOT_IDENTIFIER, null, Map.of(OWNER_OF, Set.of(OWNED_ENTITY_REF)));
    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(null))).thenReturn(List.of(team));

    // Neither the team nor the entity is viewable -> nothing accessible, paged query never runs.
    when(catalogServiceHelper.checkEntityRefsPermission(
             eq(ACCOUNT_ID), eq(Set.of(TEAM_ENTITY_REF, OWNED_ENTITY_REF)), eq("view")))
        .thenReturn(Collections.emptySet());

    var result = teamHierarchyService.getTeamOwnedEntities(
        ACCOUNT_ID, null, null, TEAM_ENTITY_REF, false, null, null, null, null);

    assertThat(result.getTotalElements()).isEqualTo(0);
    assertThat(result.getEntityResponses()).isEmpty();
    verify(catalogEntityRepository, times(0))
        .getEntities(eq(ACCOUNT_ID), any(), any(), any(), any(), any(), eq(null), any(), eq(null), eq(null), eq(null),
            eq(null), eq(null), eq(null), eq(null));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamOwnedEntitiesIncludeChildTeamsCollectsSubtree() {
    stubTeamRefParsing();
    // includeChildTeams=true -> scopes expand and must resolve; the map-build also re-resolves the base scope.
    when(catalogScopeResolver.resolve(eq(ACCOUNT_ID), eq(TEST_SCOPES + "," + ORG_UNIQUE_ID)))
        .thenReturn(buildExpandedScopeResolveResult());
    String ownedBySubRef = "component:account/svcB";
    CatalogEntity team = buildTeamEntity(ROOT_IDENTIFIER, null, Collections.emptyMap());
    // Subteam is a child of the target team (childOf) AND owns svcB (ownerOf).
    CatalogEntity subteam = buildTeamEntity(
        CHILD_IDENTIFIER, ORG_ID, Map.of(CHILD_OF, Set.of(TEAM_ENTITY_REF), OWNER_OF, Set.of(ownedBySubRef)));
    when(catalogEntityRepository.findAllTeamsInScopes(any(), eq(null))).thenReturn(List.of(team, subteam));

    CatalogEntity ownedBySub = buildComponentEntity("svcB", CHILD_REF);

    // Subteam viewable -> its INHERITABLE-kind entity accessible.
    when(catalogServiceHelper.checkEntityRefsPermission(eq(ACCOUNT_ID), anySet(), eq("view")))
        .thenReturn(Set.of(CHILD_REF));
    // svcB is a component (inheritable) -> team-based access applies.
    when(catalogServiceHelper.getKindScopeIdentifier(ownedBySubRef))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("component", "account", "svcB"));
    when(catalogServiceHelper.isInheritableKind("component")).thenReturn(true);
    stubAccessiblePageQuery(List.of(ownedBySub));

    var result = teamHierarchyService.getTeamOwnedEntities(
        ACCOUNT_ID, null, null, TEAM_ENTITY_REF, true, null, null, null, null);

    assertThat(result.getTotalElements()).isEqualTo(1);
    // The batched permission check saw BOTH team refs (subtree collected) and the owned entity ref.
    verify(catalogServiceHelper)
        .checkEntityRefsPermission(
            eq(ACCOUNT_ID), eq(Set.of(TEAM_ENTITY_REF, CHILD_REF, "component:account/svcB")), eq("view"));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamOwnedEntitiesEmptyWhenTeamNotFound() {
    stubTeamRefParsing();
    // No teams in scope -> target team not found -> empty map -> empty result.
    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(null)))
        .thenReturn(Collections.emptyList());

    var result = teamHierarchyService.getTeamOwnedEntities(
        ACCOUNT_ID, null, null, TEAM_ENTITY_REF, false, null, null, null, null);

    assertThat(result.getTotalElements()).isEqualTo(0);
    assertThat(result.getEntityResponses()).isEmpty();
  }

  private static final String WORKFLOW_ENTITY_REF = "workflow:account/wf1";

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamOwnedEntitiesTeamViewableExcludesNonInheritableKind() {
    stubTeamRefParsing();
    // Team owns a workflow (non-inheritable). Team-based access must NOT extend to it.
    CatalogEntity team = buildTeamEntity(ROOT_IDENTIFIER, null, Map.of(OWNER_OF, Set.of(WORKFLOW_ENTITY_REF)));
    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(null))).thenReturn(List.of(team));

    // Team ref is viewable, but the workflow itself is NOT directly viewable.
    when(catalogServiceHelper.checkEntityRefsPermission(
             eq(ACCOUNT_ID), eq(Set.of(TEAM_ENTITY_REF, WORKFLOW_ENTITY_REF)), eq("view")))
        .thenReturn(Set.of(TEAM_ENTITY_REF));
    when(catalogServiceHelper.getKindScopeIdentifier(WORKFLOW_ENTITY_REF))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("workflow", "account", "wf1"));
    when(catalogServiceHelper.isInheritableKind("workflow")).thenReturn(false);

    var result = teamHierarchyService.getTeamOwnedEntities(
        ACCOUNT_ID, null, null, TEAM_ENTITY_REF, false, null, null, null, null);

    // Non-inheritable kind requires direct access -> nothing accessible, paged query never runs.
    assertThat(result.getTotalElements()).isEqualTo(0);
    assertThat(result.getEntityResponses()).isEmpty();
    verify(catalogEntityRepository, times(0))
        .getEntities(eq(ACCOUNT_ID), any(), any(), any(), any(), any(), eq(null), any(), eq(null), eq(null), eq(null),
            eq(null), eq(null), eq(null), eq(null));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetTeamOwnedEntitiesNonInheritableKindWithDirectAccessIncluded() {
    stubTeamRefParsing();
    CatalogEntity team = buildTeamEntity(ROOT_IDENTIFIER, null, Map.of(OWNER_OF, Set.of(WORKFLOW_ENTITY_REF)));
    when(catalogEntityRepository.findAllTeamsInScopes(eq(List.of(ACCOUNT_ID)), eq(null))).thenReturn(List.of(team));

    CatalogEntity workflow = buildEntity("workflow", "wf1", TEAM_ENTITY_REF);

    // Team ref viewable AND the workflow itself directly viewable -> accessible via direct access.
    when(catalogServiceHelper.checkEntityRefsPermission(
             eq(ACCOUNT_ID), eq(Set.of(TEAM_ENTITY_REF, WORKFLOW_ENTITY_REF)), eq("view")))
        .thenReturn(Set.of(TEAM_ENTITY_REF, WORKFLOW_ENTITY_REF));
    when(catalogServiceHelper.getKindScopeIdentifier(WORKFLOW_ENTITY_REF))
        .thenReturn(org.apache.commons.lang3.tuple.Triple.of("workflow", "account", "wf1"));
    when(catalogServiceHelper.isInheritableKind("workflow")).thenReturn(false);
    stubAccessiblePageQuery(List.of(workflow));

    var result = teamHierarchyService.getTeamOwnedEntities(
        ACCOUNT_ID, null, null, TEAM_ENTITY_REF, false, null, null, null, null);

    assertThat(result.getTotalElements()).isEqualTo(1);
    verify(catalogEntityRepository)
        .getEntities(eq(ACCOUNT_ID), any(), any(), any(), any(), any(), eq(null), eq(WORKFLOW_ENTITY_REF), eq(null),
            eq(null), eq(null), eq(null), eq(null), eq(null), eq(null));
  }

  private CatalogEntity buildComponentEntity(String identifier, String owner) {
    return buildEntity("component", identifier, owner);
  }

  private CatalogEntity buildEntity(String kind, String identifier, String owner) {
    return InlineCatalogEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .parentUniqueId(ACCOUNT_ID)
        .kind(kind)
        .identifier(identifier)
        .name(identifier)
        .owner(owner)
        .referenceType(ReferenceType.INLINE)
        .relations(new HashMap<>())
        .spec(new HashMap<>())
        .metadata(new HashMap<>())
        .tags(List.of())
        .build();
  }

  private CatalogScopeResolver.ScopeResolveResult buildScopeResolveResult() {
    ScopeInfo accountScope =
        ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).uniqueId(ACCOUNT_ID).build();
    Map<String, ScopeTopology.OrgNode> orgs = new HashMap<>();
    orgs.put(ORG_ID, ScopeTopology.OrgNode.builder().uniqueId(ORG_UNIQUE_ID).projects(new HashMap<>()).build());
    ScopeTopology topology = ScopeTopology.builder().accountUniqueId(ACCOUNT_ID).orgs(orgs).build();
    return CatalogScopeResolver.ScopeResolveResult.builder()
        .scopeInfos(List.of(accountScope))
        .topology(topology)
        .build();
  }

  private CatalogScopeResolver.ScopeResolveResult buildExpandedScopeResolveResult() {
    ScopeInfo accountScope =
        ScopeInfo.builder().accountIdentifier(ACCOUNT_ID).scopeType(ScopeLevel.ACCOUNT).uniqueId(ACCOUNT_ID).build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(ACCOUNT_ID)
                             .orgIdentifier(ORG_ID)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .uniqueId(ORG_UNIQUE_ID)
                             .build();
    Map<String, ScopeTopology.OrgNode> orgs = new HashMap<>();
    orgs.put(ORG_ID, ScopeTopology.OrgNode.builder().uniqueId(ORG_UNIQUE_ID).projects(new HashMap<>()).build());
    ScopeTopology topology = ScopeTopology.builder().accountUniqueId(ACCOUNT_ID).orgs(orgs).build();
    return CatalogScopeResolver.ScopeResolveResult.builder()
        .scopeInfos(List.of(accountScope, orgScope))
        .topology(topology)
        .build();
  }
}
