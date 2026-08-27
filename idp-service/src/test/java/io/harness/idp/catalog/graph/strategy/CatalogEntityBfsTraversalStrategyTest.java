/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.graph.strategy;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.graph.beans.CatalogGraphEntity;
import io.harness.idp.catalog.graph.beans.GraphEdge;
import io.harness.idp.catalog.graph.fetcher.CatalogEntityGraphFetcher;
import io.harness.idp.catalog.graph.filter.GraphRbacFilter;
import io.harness.idp.catalog.graph.utils.RelationExtractor;
import io.harness.idp.catalog.service.CatalogScopeResolver;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests BFS traversal over catalog_entities with multiple relation types and depth=2.
 *
 * Graph used across tests:
 *
 *   system/payment-platform  (rootEntityRef = "system:account/payment-platform")
 *     ├── hasPart  → component/payment-service
 *     │               ├── providesApis → api/payment-api
 *     │               └── dependsOn    → component/auth-service  (visited — skipped)
 *     ├── hasPart  → component/auth-service
 *     │               └── providesApis → api/auth-api
 *     └── ownedBy  → group/platform-team  (RBAC denied)
 */
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class CatalogEntityBfsTraversalStrategyTest extends CategoryTest {
  static final String ACCOUNT_ID = "testAccount123";
  static final String PARENT_UID = "testAccount123";
  static final String ORG_UID = "orgUniqueId456";
  static final String PROJECT_UID = "projectUniqueId789";

  // rootEntityRef in Harness format (account-level = no org/project)
  static final String ROOT_ENTITY_REF = "system:account/payment-platform";

  // Expected entity refs in response (built by strategy's buildEntityRef)
  static final String REF_PAYMENT_PLATFORM = "system:account/payment-platform";
  static final String REF_PAYMENT_SERVICE = "component:account/payment-service";
  static final String REF_AUTH_SERVICE = "component:account/auth-service";
  static final String REF_PLATFORM_TEAM = "group:account/platform-team";
  static final String REF_PAYMENT_API = "api:account/payment-api";
  static final String REF_AUTH_API = "api:account/auth-api";

  // entityKeys used as fetchChildren map keys
  static final String KEY_PAYMENT_SERVICE = "component:payment-service";
  static final String KEY_AUTH_SERVICE = "component:auth-service";
  static final String KEY_PLATFORM_TEAM = "group:platform-team";
  static final String KEY_PAYMENT_API = "api:payment-api";
  static final String KEY_AUTH_API = "api:auth-api";

  AutoCloseable openMocks;

  @Mock CatalogEntityGraphFetcher fetcher;
  @Mock GraphRbacFilter rbacFilter;
  @Mock CatalogScopeResolver scopeResolver;
  CatalogEntityBfsTraversalStrategy strategy;

  ScopeTopology topology;

  InlineCatalogEntity root;
  InlineCatalogEntity paymentService;
  InlineCatalogEntity authService;
  InlineCatalogEntity platformTeam;
  InlineCatalogEntity paymentApi;
  InlineCatalogEntity authApi;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);

    topology =
        ScopeTopology.builder()
            .accountUniqueId(ACCOUNT_ID)
            .orgs(Map.of("default",
                ScopeTopology.OrgNode.builder().uniqueId(ORG_UID).projects(Map.of("Commons", PROJECT_UID)).build()))
            .build();
    when(scopeResolver.resolveNamespaceToUniqueId(eq(ACCOUNT_ID), any())).thenAnswer(invocation -> {
      String namespace = invocation.getArgument(1);
      return topology.resolveNamespaceToUniqueId(namespace);
    });

    RelationExtractor relationExtractor = new RelationExtractor();
    strategy = new CatalogEntityBfsTraversalStrategy(fetcher, rbacFilter, relationExtractor, scopeResolver);

    root = InlineCatalogEntity.builder()
               .accountIdentifier(ACCOUNT_ID)
               .parentUniqueId(PARENT_UID)
               .identifier("payment-platform")
               .kind("system")
               .relations(new HashMap<>(Map.of("hasPart",
                   new HashSet<>(Set.of("component:account/payment-service", "component:account/auth-service")),
                   "ownedBy", new HashSet<>(Set.of("group:account/platform-team")))))
               .build();

    paymentService =
        InlineCatalogEntity.builder()
            .accountIdentifier(ACCOUNT_ID)
            .parentUniqueId(PARENT_UID)
            .identifier("payment-service")
            .kind("component")
            .relations(new HashMap<>(Map.of("providesApis", new HashSet<>(Set.of("api:account/payment-api")),
                "dependsOn", new HashSet<>(Set.of("component:account/auth-service")))))
            .build();

    authService = InlineCatalogEntity.builder()
                      .accountIdentifier(ACCOUNT_ID)
                      .parentUniqueId(PARENT_UID)
                      .identifier("auth-service")
                      .kind("component")
                      .relations(new HashMap<>(Map.of("providesApis", new HashSet<>(Set.of("api:account/auth-api")))))
                      .build();

    platformTeam = InlineCatalogEntity.builder()
                       .accountIdentifier(ACCOUNT_ID)
                       .parentUniqueId(PARENT_UID)
                       .identifier("platform-team")
                       .kind("group")
                       .relations(new HashMap<>())
                       .build();

    paymentApi = InlineCatalogEntity.builder()
                     .accountIdentifier(ACCOUNT_ID)
                     .parentUniqueId(PARENT_UID)
                     .identifier("payment-api")
                     .kind("api")
                     .relations(new HashMap<>())
                     .build();

    authApi = InlineCatalogEntity.builder()
                  .accountIdentifier(ACCOUNT_ID)
                  .parentUniqueId(PARENT_UID)
                  .identifier("auth-api")
                  .kind("api")
                  .relations(new HashMap<>())
                  .build();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  /**
   * Core scenario: depth=2, multiple relation types, RBAC denies platform-team,
   * visited set prevents re-traversal of auth-service via payment-service's dependsOn.
   */
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBfsDepth2WithMultipleRelationsAndRbacFiltering() {
    when(fetcher.findRootEntity(ACCOUNT_ID, "system", "payment-platform")).thenReturn(Optional.of(root));

    Map<String, CatalogEntity> depth1Result =
        Map.of(KEY_PAYMENT_SERVICE, paymentService, KEY_AUTH_SERVICE, authService, KEY_PLATFORM_TEAM, platformTeam);
    Map<String, CatalogEntity> depth2Result = Map.of(KEY_PAYMENT_API, paymentApi, KEY_AUTH_API, authApi);

    doReturn(depth1Result).doReturn(depth2Result).when(fetcher).fetchByScopedLookups(any());

    when(rbacFilter.filterPermitted(eq(ACCOUNT_ID), any()))
        .thenReturn(List.of(paymentService, authService))
        .thenReturn(List.of(paymentApi, authApi));

    CatalogGraphEntity response = strategy.traverse(
        ACCOUNT_ID, ROOT_ENTITY_REF, List.of("hasPart", "ownedBy", "providesApis", "dependsOn"), List.of(), 2);

    assertThat(response.getMetadata().getBaseEntityRef()).isEqualTo(REF_PAYMENT_PLATFORM);
    assertThat(response.getMetadata().getMaxDepthReached()).isEqualTo(2);

    List<GraphEdge> depth1Edges = edgesAtDepth(response, 1);
    assertThat(depth1Edges).hasSize(2);
    assertThat(depth1Edges).extracting(GraphEdge::getSourceEntityRef).containsOnly(REF_PAYMENT_PLATFORM);
    assertThat(depth1Edges)
        .extracting(GraphEdge::getTargetEntityRef)
        .containsExactlyInAnyOrder(REF_PAYMENT_SERVICE, REF_AUTH_SERVICE);

    // platform-team absent — RBAC denied
    assertThat(response.getEdges()).extracting(GraphEdge::getTargetEntityRef).doesNotContain(REF_PLATFORM_TEAM);

    List<GraphEdge> depth2Edges = edgesAtDepth(response, 2);
    assertThat(depth2Edges).hasSize(2);
    assertThat(depth2Edges)
        .extracting(GraphEdge::getTargetEntityRef)
        .containsExactlyInAnyOrder(REF_PAYMENT_API, REF_AUTH_API);

    // auth-service must NOT appear at depth 2 — visited set skipped the dependsOn ref
    assertThat(depth2Edges).extracting(GraphEdge::getTargetEntityRef).doesNotContain(REF_AUTH_SERVICE);
  }

  /**
   * Kind filter: request only 'component' — APIs and groups excluded before fetch.
   */
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBfsDepth2WithKindFilter() {
    when(fetcher.findRootEntity(ACCOUNT_ID, "system", "payment-platform")).thenReturn(Optional.of(root));

    Map<String, CatalogEntity> depth1Result =
        Map.of(KEY_PAYMENT_SERVICE, paymentService, KEY_AUTH_SERVICE, authService);

    doReturn(depth1Result).when(fetcher).fetchByScopedLookups(any());

    when(rbacFilter.filterPermitted(eq(ACCOUNT_ID), any())).thenReturn(List.of(paymentService, authService));

    CatalogGraphEntity response = strategy.traverse(
        ACCOUNT_ID, ROOT_ENTITY_REF, List.of("hasPart", "providesApis", "dependsOn"), List.of("component"), 2);

    assertThat(response.getEdges()).hasSize(2);
    assertThat(response.getEdges())
        .extracting(GraphEdge::getTargetEntityRef)
        .containsExactlyInAnyOrder(REF_PAYMENT_SERVICE, REF_AUTH_SERVICE);
    assertThat(response.getMetadata().getMaxDepthReached()).isEqualTo(1);
  }

  /**
   * Invalid rootEntityRef format — empty response.
   */
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testInvalidRootEntityRefReturnsEmptyResponse() {
    assertThatThrownBy(() -> strategy.traverse(ACCOUNT_ID, "invalid-ref", List.of("hasPart"), List.of(), 3))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Root entity not found: invalid-ref");
  }

  /**
   * Root entity not found — empty response.
   */
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testRootEntityNotFoundReturnsEmptyResponse() {
    when(fetcher.findRootEntity(ACCOUNT_ID, "system", "unknown-system")).thenReturn(Optional.empty());

    assertThatThrownBy(
        () -> strategy.traverse(ACCOUNT_ID, "system:account/unknown-system", List.of("hasPart"), List.of(), 3))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Root entity not found: system:account/unknown-system");
  }

  /**
   * All depth-1 entities RBAC denied — frontier empty, BFS stops at depth 1.
   */
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testAllEntitiesRbacDeniedAtDepth1StopsBfs() {
    when(fetcher.findRootEntity(ACCOUNT_ID, "system", "payment-platform")).thenReturn(Optional.of(root));

    Map<String, CatalogEntity> depth1Result =
        Map.of(KEY_PAYMENT_SERVICE, paymentService, KEY_AUTH_SERVICE, authService, KEY_PLATFORM_TEAM, platformTeam);

    doReturn(depth1Result).when(fetcher).fetchByScopedLookups(any());
    when(rbacFilter.filterPermitted(eq(ACCOUNT_ID), any())).thenReturn(List.of());

    CatalogGraphEntity response =
        strategy.traverse(ACCOUNT_ID, ROOT_ENTITY_REF, List.of("hasPart", "ownedBy"), List.of(), 2);

    assertThat(response.getEdges()).isEmpty();
    assertThat(response.getMetadata().getMaxDepthReached()).isEqualTo(0);
  }

  /**
   * Depth=1 should only expand immediate neighbors.
   */
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBfsDepth1OnlyExpandsImmediateNeighbors() {
    when(fetcher.findRootEntity(ACCOUNT_ID, "system", "payment-platform")).thenReturn(Optional.of(root));

    Map<String, CatalogEntity> depth1Result =
        Map.of(KEY_PAYMENT_SERVICE, paymentService, KEY_AUTH_SERVICE, authService, KEY_PLATFORM_TEAM, platformTeam);

    doReturn(depth1Result).when(fetcher).fetchByScopedLookups(any());
    when(rbacFilter.filterPermitted(eq(ACCOUNT_ID), any()))
        .thenReturn(List.of(paymentService, authService, platformTeam));

    CatalogGraphEntity response =
        strategy.traverse(ACCOUNT_ID, ROOT_ENTITY_REF, List.of("hasPart", "ownedBy"), List.of(), 1);

    assertThat(response.getMetadata().getMaxDepthReached()).isEqualTo(1);
    assertThat(response.getEdges()).hasSize(3);
    assertThat(response.getEdges())
        .extracting(GraphEdge::getTargetEntityRef)
        .containsExactlyInAnyOrder(REF_PAYMENT_SERVICE, REF_AUTH_SERVICE, REF_PLATFORM_TEAM);
    assertThat(response.getNodes()).hasSize(4);
  }

  /**
   * Root entity with no relations at all — BFS should return only the root node.
   */
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBfsRootWithNoRelationsReturnsOnlyRoot() {
    InlineCatalogEntity loneRoot = InlineCatalogEntity.builder()
                                       .accountIdentifier(ACCOUNT_ID)
                                       .parentUniqueId(PARENT_UID)
                                       .identifier("lone-system")
                                       .kind("system")
                                       .relations(new HashMap<>())
                                       .build();

    when(fetcher.findRootEntity(ACCOUNT_ID, "system", "lone-system")).thenReturn(Optional.of(loneRoot));

    CatalogGraphEntity response = strategy.traverse(ACCOUNT_ID, "system:account/lone-system", List.of(), List.of(), 3);

    assertThat(response.getNodes()).hasSize(1);
    assertThat(response.getNodes().get(0).getEntityRef()).isEqualTo("system:account/lone-system");
    assertThat(response.getEdges()).isEmpty();
    assertThat(response.getMetadata().getMaxDepthReached()).isEqualTo(0);
    assertThat(response.getMetadata().getTotalEdges()).isEqualTo(0);
  }

  /**
   * Project-scoped traversal — relations use "account.org.project" namespace.
   * The ScopeTopology resolves "account.default.Commons" → PROJECT_UID.
   */
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBfsProjectScopedEntitiesTraversedCorrectly() {
    InlineCatalogEntity projectRoot = InlineCatalogEntity.builder()
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier("default")
                                          .projectIdentifier("Commons")
                                          .parentUniqueId(PROJECT_UID)
                                          .identifier("IDP_Backstage")
                                          .kind("system")
                                          .relations(new HashMap<>(Map.of("hasPart",
                                              new HashSet<>(Set.of("component:account.default.Commons/IDP_App")),
                                              "ownedBy", new HashSet<>(Set.of("group:account/_account_all_users")))))
                                          .build();

    InlineCatalogEntity idpApp = InlineCatalogEntity.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier("default")
                                     .projectIdentifier("Commons")
                                     .parentUniqueId(PROJECT_UID)
                                     .identifier("IDP_App")
                                     .kind("component")
                                     .relations(new HashMap<>())
                                     .build();

    InlineCatalogEntity allUsersGroup = InlineCatalogEntity.builder()
                                            .accountIdentifier(ACCOUNT_ID)
                                            .parentUniqueId(ACCOUNT_ID)
                                            .identifier("_account_all_users")
                                            .kind("group")
                                            .relations(new HashMap<>())
                                            .build();

    when(fetcher.findRootEntity(PROJECT_UID, "system", "IDP_Backstage")).thenReturn(Optional.of(projectRoot));

    Map<String, CatalogEntity> depth1Result =
        Map.of("component:IDP_App", idpApp, "group:_account_all_users", allUsersGroup);
    doReturn(depth1Result).when(fetcher).fetchByScopedLookups(any());

    when(rbacFilter.filterPermitted(eq(ACCOUNT_ID), any())).thenReturn(List.of(idpApp, allUsersGroup));

    CatalogGraphEntity response =
        strategy.traverse(ACCOUNT_ID, "system:account.default.Commons/IDP_Backstage", List.of(), List.of(), 2);

    assertThat(response.getMetadata().getBaseEntityRef()).isEqualTo("system:account.default.Commons/IDP_Backstage");
    assertThat(response.getMetadata().getMaxDepthReached()).isEqualTo(1);
    assertThat(response.getNodes()).hasSize(3);
    assertThat(response.getEdges()).hasSize(2);
    assertThat(response.getEdges())
        .extracting(GraphEdge::getTargetEntityRef)
        .containsExactlyInAnyOrder("component:account.default.Commons/IDP_App", "group:account/_account_all_users");
  }

  /**
   * Fetcher returns empty map at depth 1 — BFS should stop with no edges.
   */
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBfsFetcherReturnsEmptyStopsBfs() {
    when(fetcher.findRootEntity(ACCOUNT_ID, "system", "payment-platform")).thenReturn(Optional.of(root));

    doReturn(Map.of()).when(fetcher).fetchByScopedLookups(any());

    CatalogGraphEntity response = strategy.traverse(ACCOUNT_ID, ROOT_ENTITY_REF, List.of("hasPart"), List.of(), 2);

    assertThat(response.getEdges()).isEmpty();
    assertThat(response.getNodes()).hasSize(1);
    assertThat(response.getMetadata().getMaxDepthReached()).isEqualTo(0);
  }

  /**
   * Relationship type filter restricts which relations are followed.
   */
  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testBfsRelationshipTypeFilterExcludesNonMatchingTypes() {
    when(fetcher.findRootEntity(ACCOUNT_ID, "system", "payment-platform")).thenReturn(Optional.of(root));

    Map<String, CatalogEntity> depth1Result =
        Map.of(KEY_PAYMENT_SERVICE, paymentService, KEY_AUTH_SERVICE, authService);
    doReturn(depth1Result).when(fetcher).fetchByScopedLookups(any());

    when(rbacFilter.filterPermitted(eq(ACCOUNT_ID), any())).thenReturn(List.of(paymentService, authService));

    CatalogGraphEntity response = strategy.traverse(ACCOUNT_ID, ROOT_ENTITY_REF, List.of("hasPart"), List.of(), 1);

    assertThat(response.getEdges()).hasSize(2);
    assertThat(response.getEdges()).extracting(GraphEdge::getRelationType).containsOnly("hasPart");
    assertThat(response.getEdges()).extracting(GraphEdge::getTargetEntityRef).doesNotContain(REF_PLATFORM_TEAM);
  }

  // ---- helpers ----

  private List<GraphEdge> edgesAtDepth(CatalogGraphEntity response, int depth) {
    return response.getEdges().stream().filter(e -> e.getDepth() == depth).collect(Collectors.toList());
  }
}
