/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.EntityResponseScorecards;
import io.harness.spec.server.idp.v1.model.EntityResponseScorecardsScores;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@OwnedBy(HarnessTeam.IDP)
public class CatalogServiceV2ImplTest extends CategoryTest {
  private static final String TEST_ACCOUNT = "testAccount";
  private static final String ORG_1 = "org1";
  private static final String PROJECT_1 = "proj1";
  private static final String UNIQUE_ID_ACCOUNT = "testAccount";
  private static final String UNIQUE_ID_ORG = "testAccount/org1";
  private static final String UNIQUE_ID_PROJECT = "testAccount/org1/proj1";

  @Mock private CatalogScopeResolver scopeResolver;
  @Mock private CatalogRbacResolver rbacResolver;
  @Mock private CatalogOrgProjectService orgProjectService;
  @Mock private CatalogServiceHelper catalogServiceHelper;
  @Mock private CatalogEntityRepository catalogEntityRepository;
  @Mock private KindServiceHelper kindServiceHelper;
  @Mock private ScorecardService scorecardService;
  @Mock private ScorecardScoreHelper scorecardScoreHelper;
  @Mock private IDPGitXHelper idpGitXHelper;

  private CatalogServiceV2Impl catalogServiceV2;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    catalogServiceV2 = new CatalogServiceV2Impl(scopeResolver, rbacResolver, orgProjectService, catalogServiceHelper,
        catalogEntityRepository, kindServiceHelper, scorecardService, scorecardScoreHelper, idpGitXHelper,
        new HashMap<>());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_DefaultParams_ReturnsEntities() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT),
        buildCatalogEntity("component", "comp2", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 2);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(2);
    assertThat(result.getPageNumber()).isEqualTo(0);
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_SkipFavorites_NoFavoritesComputation() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, true);

    assertThat(result).isNotNull();
    assertThat(result.getTotalStarred()).isEqualTo(0);
    verify(catalogServiceHelper, never()).getUserFavoriteEntityRefs(anyString(), anyString(), anyString(), anyString());
    verify(catalogServiceHelper, never()).getUserFavoriteEntityRefsForOrgs(anyString(), anyList(), anyString());
    verify(catalogServiceHelper, never()).getUserFavoriteEntityRefsForProjects(anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_FavoritesFilter_FiltersToFavorites() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());

    String favoriteRefs = "component:account/comp1,component:account/comp2";
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn(favoriteRefs);
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getFavoritesEntitiesPageWithGroupFallback(eq(TEST_ACCOUNT), eq(scopeInfos), any(),
             any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(),
             any()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(),
             eq(scopeInfos), isNull(), anyString(), isNull(), eq(false), isNull()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(1L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        true, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_SkipFavoritesAndFavoritesTrue_ThrowsException() {
    buildDefaultMocksForScopeAndRbac();

    assertThatThrownBy(()
                           -> catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
                               true, null, null, null, null, null, null, true, false, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Cannot use skip_favorites=true and favorites=true together");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_FavoritesFilter_NoFavorites_ReturnsEmpty() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        true, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_FavoritesFilter_NoDirectAccessButTeamOwned_RoutesToGroupFallback() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());
    when(rbacResolver.permittedGroupEntityRefs(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(List.of("group:account/team1"));

    String favoriteRefs = "component:account/comp1,component:account/comp2";
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn(favoriteRefs);
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenReturn(Collections.emptySet());

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getFavoritesEntitiesPageWithGroupFallback(eq(TEST_ACCOUNT), eq(scopeInfos), any(),
             any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(),
             any()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(),
             eq(scopeInfos), isNull(), anyString(), isNull(), eq(false), isNull()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(1L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        true, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    verify(catalogEntityRepository)
        .getFavoritesEntitiesPageWithGroupFallback(eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(),
            anyBoolean(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any());
    verify(catalogEntityRepository, never())
        .getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(), anyInt(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_MultipleKinds_IncludingWorkflow_NoException() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1, "workflow,component");

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, "workflow,component", null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_EmptyScopesAndEntityRefs_DefaultsToAllScopes() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1);

    catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null, null, null, null, null,
        null, null, null, true, false, null);

    verify(catalogServiceHelper).getAllScopes();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_EmptyPermittedScopes_FallsBackToAccount() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());

    // Return empty permitted scopes so the fallback kicks in
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>()))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());

    List<CatalogEntity> entities = Collections.emptyList();
    // After fallback, the list should contain the account-level ScopeInfo
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 0);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), isNull(), anyString(), isNull(), eq(false), isNull()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), anyList(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn(null);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_OwnedByMeFilter() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1);
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("user:ankur@harness.io");

    catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, true, null, null, null, null,
        null, null, null, true, false, null);

    // getOwnedByMe is called twice: once for the ownedByMe filter (line 139) and once for totalCount (line 170)
    verify(catalogServiceHelper, times(2)).getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_ScorecardsEnriched_ForCoreKinds() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1);

    List<ScorecardAndChecks> scorecardAndChecksList = Collections.emptyList();
    when(scorecardService.getAllScorecardAndChecks(eq(TEST_ACCOUNT), isNull())).thenReturn(scorecardAndChecksList);
    when(scorecardScoreHelper.fetchScoresForEntities(eq(TEST_ACCOUNT), eq(entities), eq(scorecardAndChecksList), any()))
        .thenReturn(new HashMap<>());

    catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null, null, null, null, null,
        null, null, null, true, false, null);

    verify(scorecardService).getAllScorecardAndChecks(eq(TEST_ACCOUNT), isNull());
    verify(scorecardScoreHelper)
        .fetchScoresForEntities(eq(TEST_ACCOUNT), eq(entities), eq(scorecardAndChecksList), any());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_ScorecardsSkipped_ForNonCoreKinds() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("workflow", "wf1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1);

    catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null, null, null, null, null,
        null, null, null, true, false, null);

    verify(scorecardService, never()).getAllScorecardAndChecks(anyString(), any());
    verify(scorecardScoreHelper, never()).fetchScoresForEntities(anyString(), anyList(), anyList(), any());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_ScorecardsSkipped_WhenIncludeScorecardsDataFalse() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1);

    catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null, null, null, null, null,
        null, null, null, false, false, null);

    verify(scorecardService, never()).getAllScorecardAndChecks(anyString(), any());
    verify(scorecardScoreHelper, never()).fetchScoresForEntities(anyString(), anyList(), anyList(), any());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_EntitySkipped_UnresolvedOrgName() {
    CatalogEntity entityWithOrg = buildCatalogEntity("component", "comp1", ORG_1, null, UNIQUE_ID_ORG);
    CatalogEntity entityWithoutOrg = buildCatalogEntity("component", "comp2", null, null, UNIQUE_ID_ACCOUNT);
    List<CatalogEntity> entities = List.of(entityWithOrg, entityWithoutOrg);

    buildDefaultMocks(entities, 2);
    // org1 is not resolved (empty map)
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    // entityWithOrg should be skipped, entityWithoutOrg should remain
    assertThat(result.getEntityResponses()).hasSize(1);
    assertThat(result.getTotalElements()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_EntitySkipped_UnresolvedProjectName() {
    CatalogEntity entityWithOrgProj = buildCatalogEntity("component", "comp1", ORG_1, PROJECT_1, UNIQUE_ID_PROJECT);
    List<CatalogEntity> entities = List.of(entityWithOrgProj);

    buildDefaultMocks(entities, 1);
    Map<String, String> orgNameMap = new HashMap<>();
    orgNameMap.put(ORG_1, "Org One");
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(orgNameMap);
    // project name not resolved
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result.getEntityResponses()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_MongoReplacementConfig_AppliedToFilter() {
    HashMap<String, String> mongoReplacementConfig = new HashMap<>();
    mongoReplacementConfig.put("metadata.field1", "spec.field1");
    catalogServiceV2 = new CatalogServiceV2Impl(scopeResolver, rbacResolver, orgProjectService, catalogServiceHelper,
        catalogEntityRepository, kindServiceHelper, scorecardService, scorecardScoreHelper, idpGitXHelper,
        mongoReplacementConfig);

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn(null);
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    // The filter should have "metadata.field1" replaced with "spec.field1"
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(),
             eq(scopeInfos), isNull(), eq("owner1"), isNull(), eq(false), eq("spec.field1=value")))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), eq("spec.field1=value")))
        .thenReturn(0L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, "metadata.field1=value", true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_ExceptionWrapped_AsInvalidRequestException() {
    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenThrow(new RuntimeException("Scope resolution failed"));

    assertThatThrownBy(()
                           -> catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
                               null, null, null, null, null, null, null, true, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Scope resolution failed");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_OrgProjectNamesResolved() {
    CatalogEntity entity1 = buildCatalogEntity("component", "comp1", ORG_1, PROJECT_1, UNIQUE_ID_PROJECT);
    List<CatalogEntity> entities = List.of(entity1);

    buildDefaultMocks(entities, 1);
    Map<String, String> orgNameMap = new HashMap<>();
    orgNameMap.put(ORG_1, "Org One");
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(orgNameMap);
    Map<String, String> projectNameMap = new HashMap<>();
    projectNameMap.put(ORG_1 + ":" + PROJECT_1, "Project One");
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(projectNameMap);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    verify(orgProjectService).getOrgNames(eq(TEST_ACCOUNT), any());
    verify(orgProjectService).getProjectNames(eq(TEST_ACCOUNT), any(), anyMap());
    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_PaginationHeaders_Correct() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    long totalCount = 100;

    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn("component:account/comp1");
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(2, 20), totalCount);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(),
             eq(scopeInfos), isNull(), eq("owner1"), isNull(), eq(false), isNull()))
        .thenReturn(5L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(3L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 2, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result.getPageNumber()).isEqualTo(2);
    assertThat(result.getTotalElements()).isEqualTo(totalCount);
    assertThat(result.getTotalOwned()).isEqualTo(5L);
    assertThat(result.getTotalStarred()).isEqualTo(3L);
    verify(catalogEntityRepository)
        .getOwnedEntitiesCountWithKindScopes(TEST_ACCOUNT, Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)),
            List.of(), scopeInfos, null, "owner1", null, false, null);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_ComputeFavorites_OrgLevel() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .orgIdentifier(ORG_1)
                             .uniqueId(UNIQUE_ID_ORG)
                             .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, orgScope);

    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.org1.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());

    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn("component:account/comp1");
    when(catalogServiceHelper.getUserFavoriteEntityRefsForOrgs(eq(TEST_ACCOUNT), anyList(), eq("IDPENTITY")))
        .thenReturn("component:account.org1/comp2");
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), eq(scopeInfos), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(2L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, "account.org1.*",
        null, null, null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getTotalStarred()).isEqualTo(2L);
    verify(catalogServiceHelper).getUserFavoriteEntityRefsForOrgs(eq(TEST_ACCOUNT), anyList(), eq("IDPENTITY"));
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_ComputeFavorites_ProjectLevel() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    ScopeInfo projectScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.PROJECT)
                                 .orgIdentifier(ORG_1)
                                 .projectIdentifier(PROJECT_1)
                                 .uniqueId(UNIQUE_ID_PROJECT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, projectScope);

    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.org1.proj1")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());

    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn("component:account/comp1");
    when(catalogServiceHelper.getUserFavoriteEntityRefsForProjects(eq(TEST_ACCOUNT), eq("org1.proj1"), eq("IDPENTITY")))
        .thenReturn("component:account.org1.proj1/comp3");
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), eq(scopeInfos), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(2L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, "account.org1.proj1",
        null, null, null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getTotalStarred()).isEqualTo(2L);
    verify(catalogServiceHelper)
        .getUserFavoriteEntityRefsForProjects(eq(TEST_ACCOUNT), eq("org1.proj1"), eq("IDPENTITY"));
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_ComputeFavorites_KindFiltering() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());

    // Account favorites include mixed kinds
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn("component:account/comp1,api:account/api1,component:account/comp2");
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(),
             eq(scopeInfos), eq("component"), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    // After kind filtering, only component refs remain: "component:account/comp1,component:account/comp2"
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(2L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, "component", null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    // api:account/api1 should be filtered out by kind filtering, leaving 2 component refs
    assertThat(result.getTotalStarred()).isEqualTo(2L);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_WithScoreEntities_ConstructsScorecards() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn(null);
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), eq(scopeInfos), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    // Setup scorecards
    ScorecardEntity scorecardEntity = ScorecardEntity.builder().identifier("sc1").name("Scorecard One").build();
    ScorecardAndChecks scorecardAndChecks =
        ScorecardAndChecks.builder().scorecard(scorecardEntity).checks(Collections.emptyList()).build();
    List<ScorecardAndChecks> scorecardAndChecksList = List.of(scorecardAndChecks);
    when(scorecardService.getAllScorecardAndChecks(eq(TEST_ACCOUNT), isNull())).thenReturn(scorecardAndChecksList);

    // Setup score entities with check statuses
    CheckStatus passCheck = new CheckStatus();
    passCheck.setStatus(CheckStatus.StatusEnum.PASS);
    CheckStatus failCheck = new CheckStatus();
    failCheck.setStatus(CheckStatus.StatusEnum.FAIL);

    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .scorecardIdentifier("sc1")
                                  .score(75)
                                  .tierName("Gold")
                                  .tierGroupIdentifier("default_tiers")
                                  .checkStatus(Arrays.asList(passCheck, passCheck, failCheck))
                                  .build();

    // entityRef for comp1 with no org/project = "component:account/comp1"
    String entityRef = "component:account/comp1";
    Map<String, List<ScoreEntity>> entityScoresMap = new HashMap<>();
    entityScoresMap.put(entityRef, List.of(scoreEntity));
    when(scorecardScoreHelper.fetchScoresForEntities(eq(TEST_ACCOUNT), eq(entities), eq(scorecardAndChecksList), any()))
        .thenReturn(entityScoresMap);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    EntityResponseScorecards scorecards = result.getEntityResponses().get(0).getScorecards();
    assertThat(scorecards).isNotNull();
    assertThat(scorecards.getScores()).hasSize(1);
    EntityResponseScorecardsScores score = scorecards.getScores().get(0);
    assertThat(score.getScorecard()).isEqualTo("sc1");
    assertThat(score.getScorecardName()).isEqualTo("Scorecard One");
    assertThat(score.getScore()).isEqualTo(BigDecimal.valueOf(75));
    assertThat(score.getTotalChecks()).isEqualTo(BigDecimal.valueOf(3));
    assertThat(score.getPassedChecks()).isEqualTo(BigDecimal.valueOf(2));
    assertThat(score.getTier()).isNotNull();
    assertThat(score.getTier().getTierName()).isEqualTo("Gold");
    assertThat(scorecards.getAverage()).isEqualTo(BigDecimal.valueOf(75));
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_AccountFavoritesFails_ReturnsEntitiesWithoutFavorites() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1);
    // Account-level favorites throws exception
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenThrow(new RuntimeException("Favorites service unavailable"));

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_OrgFavoritesFails_ReturnsEntitiesWithPartialFavorites() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .orgIdentifier(ORG_1)
                             .uniqueId(UNIQUE_ID_ORG)
                             .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, orgScope);

    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.org1.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());

    // Account favorites succeed
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn("component:account/comp1");
    // Org favorites fail
    when(catalogServiceHelper.getUserFavoriteEntityRefsForOrgs(eq(TEST_ACCOUNT), anyList(), eq("IDPENTITY")))
        .thenThrow(new RuntimeException("Org favorites service unavailable"));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), eq(scopeInfos), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(1L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, "account.org1.*",
        null, null, null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    // Account favorites still counted despite org failure
    assertThat(result.getTotalStarred()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_ProjectFavoritesFails_ReturnsEntitiesWithPartialFavorites() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    ScopeInfo projectScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.PROJECT)
                                 .orgIdentifier(ORG_1)
                                 .projectIdentifier(PROJECT_1)
                                 .uniqueId(UNIQUE_ID_PROJECT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, projectScope);

    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.org1.proj1")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());

    // Account favorites succeed
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn("component:account/comp1");
    // Project favorites fail
    when(catalogServiceHelper.getUserFavoriteEntityRefsForProjects(eq(TEST_ACCOUNT), anyString(), eq("IDPENTITY")))
        .thenThrow(new RuntimeException("Project favorites service unavailable"));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), eq(scopeInfos), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(1L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, "account.org1.proj1",
        null, null, null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    assertThat(result.getTotalStarred()).isEqualTo(1L);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_FavoritesCountFails_ReturnsZeroStarred() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1);
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn("component:account/comp1");
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), anyList(), any(), any(), any(), any(), anyBoolean(), any()))
        .thenThrow(new RuntimeException("Favorites count query failed"));

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    assertThat(result.getTotalStarred()).isEqualTo(0L);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_OwnedCountFails_ReturnsZeroOwnedAndEntities() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), any(), anyString(), any(), anyBoolean(), any()))
        .thenThrow(new RuntimeException("Owned count query failed"));

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    assertThat(result.getTotalOwned()).isEqualTo(0L);
    verify(catalogEntityRepository)
        .getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), eq(0), eq(20), isNull(), isNull(),
            isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), anyList());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_AllFavoritesFail_ReturnsEntitiesNormally() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .orgIdentifier(ORG_1)
                             .uniqueId(UNIQUE_ID_ORG)
                             .build();
    ScopeInfo projectScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.PROJECT)
                                 .orgIdentifier(ORG_1)
                                 .projectIdentifier(PROJECT_1)
                                 .uniqueId(UNIQUE_ID_PROJECT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, orgScope, projectScope);

    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.org1.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());

    // All favorites calls fail
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenThrow(new RuntimeException("Account favorites failed"));
    when(catalogServiceHelper.getUserFavoriteEntityRefsForOrgs(eq(TEST_ACCOUNT), anyList(), eq("IDPENTITY")))
        .thenThrow(new RuntimeException("Org favorites failed"));
    when(catalogServiceHelper.getUserFavoriteEntityRefsForProjects(eq(TEST_ACCOUNT), anyString(), eq("IDPENTITY")))
        .thenThrow(new RuntimeException("Project favorites failed"));
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT),
        buildCatalogEntity("component", "comp2", ORG_1, null, UNIQUE_ID_ORG));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 2);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), eq(scopeInfos), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    Map<String, String> orgNameMap = new HashMap<>();
    orgNameMap.put(ORG_1, "Org One");
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(orgNameMap);
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, "account.org1.*",
        null, null, null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(2);
    assertThat(result.getTotalStarred()).isEqualTo(0L);
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_EntityLevelFavorites_IncludedInResults() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT)
                             .orgIdentifier(ORG_1)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .uniqueId(UNIQUE_ID_ORG)
                             .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, orgScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());

    List<String> permittedEntityRefs =
        List.of("component:account.org1/svc1", "component:account.org1/svc2", "api:account.org1/api1");
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(permittedEntityRefs)
                        .build());

    // Account-level favorite plus org-level favorites; all pass the "view" permission check.
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn("component:account/comp1");
    when(catalogServiceHelper.getUserFavoriteEntityRefsForOrgs(eq(TEST_ACCOUNT), anyList(), eq("IDPENTITY")))
        .thenReturn("component:account.org1/svc1,component:account.org1/svc3");
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));

    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    // The group-fallback count aggregates the permitted (account + org) favorites and surfaces as totalStarred.
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(2L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getTotalStarred()).isEqualTo(2L);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_EntityLevelFavorites_EmptyPermittedEntityRefs_NoExtraComputation() {
    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));

    buildDefaultMocks(entities, 1);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    // With no favorites resolved, the favorites count is never computed.
    verify(catalogEntityRepository, never())
        .getFavoritesEntitiesCountWithGroupFallback(
            anyString(), anyList(), any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_EntityLevelFavorites_NoneAreFavorites_NoCountAdded() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());

    List<String> permittedEntityRefs = List.of("component:account.org1/svc1", "component:account.org1/svc2");
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(permittedEntityRefs)
                        .build());

    // No favorites at any scope.
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn(null);
    when(catalogServiceHelper.getUserFavoriteEntityRefsForOrgs(eq(TEST_ACCOUNT), anyList(), eq("IDPENTITY")))
        .thenReturn(null);

    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getTotalStarred()).isEqualTo(0L);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_EntityLevelFavorites_MixedScopes_ProjectAndOrg() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT)
                             .orgIdentifier(ORG_1)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .uniqueId(UNIQUE_ID_ORG)
                             .build();
    ScopeInfo projectScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .orgIdentifier(ORG_1)
                                 .projectIdentifier(PROJECT_1)
                                 .scopeType(ScopeLevel.PROJECT)
                                 .uniqueId(UNIQUE_ID_PROJECT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, orgScope, projectScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());

    List<String> permittedEntityRefs =
        List.of("component:account.org1/svc1", "component:account.org1.proj1/svc2", "api:account.org2/api1");
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(permittedEntityRefs)
                        .build());

    // Favorites come from org and project scopes; account scope has none.
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn(null);
    when(catalogServiceHelper.getUserFavoriteEntityRefsForOrgs(eq(TEST_ACCOUNT), anyList(), eq("IDPENTITY")))
        .thenReturn("component:account.org1/svc1,api:account.org2/api1");
    when(catalogServiceHelper.getUserFavoriteEntityRefsForProjects(eq(TEST_ACCOUNT), eq("org1.proj1"), eq("IDPENTITY")))
        .thenReturn("component:account.org1.proj1/svc2");
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));

    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(3L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getTotalStarred()).isEqualTo(3L);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_EntityLevelFavorites_FavoritesFilterIncludesEntityLevel() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT)
                             .orgIdentifier(ORG_1)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .uniqueId(UNIQUE_ID_ORG)
                             .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, orgScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());

    List<String> permittedEntityRefs = List.of("component:account.org1/svc1");
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(permittedEntityRefs)
                        .build());

    // Account-level and org-level favorites, both permitted.
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn("component:account/comp1");
    when(catalogServiceHelper.getUserFavoriteEntityRefsForOrgs(eq(TEST_ACCOUNT), anyList(), eq("IDPENTITY")))
        .thenReturn("component:account.org1/svc1");
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));

    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getFavoritesEntitiesPageWithGroupFallback(eq(TEST_ACCOUNT), eq(scopeInfos), any(),
             any(), any(), any(), anyBoolean(), any(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(),
             any()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(2L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        true, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    // DB counts both account-level (comp1) and org-level (svc1) favorites.
    assertThat(result.getTotalStarred()).isEqualTo(2L);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testGetEntitiesV2_EntityLevelFavorites_ComputeFails_ContinuesGracefully() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    ScopeInfo orgScope = ScopeInfo.builder()
                             .accountIdentifier(TEST_ACCOUNT)
                             .orgIdentifier(ORG_1)
                             .scopeType(ScopeLevel.ORGANIZATION)
                             .uniqueId(UNIQUE_ID_ORG)
                             .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope, orgScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());

    List<String> permittedEntityRefs = List.of("component:account.org1/svc1");
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(permittedEntityRefs)
                        .build());

    // Account-level favorites succeed; org-level favorites lookup blows up but is handled gracefully.
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn("component:account/comp1");
    when(catalogServiceHelper.getUserFavoriteEntityRefsForOrgs(eq(TEST_ACCOUNT), anyList(), eq("IDPENTITY")))
        .thenThrow(new RuntimeException("Invalid entity ref"));
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));

    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");

    List<CatalogEntity> entities = List.of(buildCatalogEntity("component", "comp1", null, null, UNIQUE_ID_ACCOUNT));
    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), 1);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), isNull(), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    // Only the account-level favorite survives; the count reflects it.
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(1L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);

    GetEntitiesDTO result = catalogServiceV2.getEntitiesV2(TEST_ACCOUNT, 0, 20, null, null, false, null, null, null,
        null, null, null, null, null, null, null, true, false, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    // Only scope-level favorites counted, entity-level gracefully failed
    assertThat(result.getTotalStarred()).isEqualTo(1L);
  }

  // --- Helper methods ---

  private CatalogEntity buildCatalogEntity(
      String kind, String identifier, String orgId, String projectId, String parentUniqueId) {
    return InlineCatalogEntity.builder()
        .kind(kind)
        .identifier(identifier)
        .name(identifier)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .parentUniqueId(parentUniqueId)
        .accountIdentifier(TEST_ACCOUNT)
        .queryableEntityRef(parentUniqueId + "/" + kind + "/" + identifier)
        .referenceType(ReferenceType.INLINE)
        .spec(new HashMap<>())
        .tags(new ArrayList<>())
        .yaml("kind: " + kind + "\nidentifier: " + identifier)
        .build();
  }

  private ScopeTopology buildScopeTopology(String accountId) {
    Map<String, String> projects = new HashMap<>();
    projects.put(PROJECT_1, UNIQUE_ID_PROJECT);
    ScopeTopology.OrgNode orgNode = ScopeTopology.OrgNode.builder().uniqueId(UNIQUE_ID_ORG).projects(projects).build();
    Map<String, ScopeTopology.OrgNode> orgs = new HashMap<>();
    orgs.put(ORG_1, orgNode);
    return ScopeTopology.builder().accountUniqueId(accountId).orgs(orgs).build();
  }

  private void buildDefaultMocksForScopeAndRbac() {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());
  }

  private void buildDefaultMocks(List<CatalogEntity> entities, long totalCount) {
    buildDefaultMocks(entities, totalCount, null);
  }

  private void buildDefaultMocks(List<CatalogEntity> entities, long totalCount, String kind) {
    ScopeTopology topology = buildScopeTopology(TEST_ACCOUNT);
    ScopeInfo accountScope = ScopeInfo.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .scopeType(ScopeLevel.ACCOUNT)
                                 .uniqueId(UNIQUE_ID_ACCOUNT)
                                 .build();
    List<ScopeInfo> scopeInfos = List.of(accountScope);

    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(scopeResolver.resolve(eq(TEST_ACCOUNT), eq("account.*")))
        .thenReturn(
            CatalogScopeResolver.ScopeResolveResult.builder().scopeInfos(scopeInfos).topology(topology).build());
    when(rbacResolver.resolve(eq(TEST_ACCOUNT), eq(scopeInfos), eq(topology), anyList()))
        .thenReturn(CatalogRbacResolver.RbacResolveResult.builder()
                        .resourceTypeToPermittedScopes(Map.of("IDP_CATALOG", new ArrayList<>(scopeInfos)))
                        .permittedEntityRefs(new ArrayList<>())
                        .build());
    when(catalogServiceHelper.getUserFavoriteEntityRefs(eq(TEST_ACCOUNT), isNull(), isNull(), eq("IDPENTITY")))
        .thenReturn(null);
    when(catalogServiceHelper.checkEntityRefsPermission(eq(TEST_ACCOUNT), anySet(), eq("view")))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), isNull())).thenReturn("owner1");
    when(catalogServiceHelper.getOwnedByMe(eq(UNIQUE_ID_ACCOUNT), eq(null))).thenReturn("owner1");

    Page<CatalogEntity> page = new PageImpl<>(entities, PageRequest.of(0, 20), totalCount);
    when(catalogEntityRepository.getEntitiesWithKindScopes(eq(TEST_ACCOUNT), anyMap(), anyList(), anyList(), anyInt(),
             anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyList()))
        .thenReturn(page);
    when(catalogEntityRepository.getOwnedEntitiesCountWithKindScopes(
             eq(TEST_ACCOUNT), anyMap(), anyList(), eq(scopeInfos), eq(kind), anyString(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(catalogEntityRepository.getFavoritesEntitiesCountWithGroupFallback(
             eq(TEST_ACCOUNT), eq(scopeInfos), any(), any(), any(), any(), anyBoolean(), any()))
        .thenReturn(0L);
    when(orgProjectService.getOrgNames(eq(TEST_ACCOUNT), any())).thenReturn(new HashMap<>());
    when(orgProjectService.getProjectNames(eq(TEST_ACCOUNT), any(), anyMap())).thenReturn(new HashMap<>());
    when(kindServiceHelper.findByAccountIdentifierIn(eq(TEST_ACCOUNT))).thenReturn(Collections.emptyList());
    when(catalogServiceHelper.resolveOwner(eq(UNIQUE_ID_ACCOUNT), eq(entities))).thenReturn(entities);
    when(idpGitXHelper.getEntityDetails(any())).thenReturn(null);
  }
}
