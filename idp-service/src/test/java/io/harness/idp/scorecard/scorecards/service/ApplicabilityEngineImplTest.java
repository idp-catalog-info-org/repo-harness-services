/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecards.service;

import static io.harness.rule.OwnerRule.HARJAS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ApplicabilityEngineImplTest extends CategoryTest {
  private ApplicabilityEngineImpl applicabilityEngine;

  private static final String ACCOUNT_ID = "testAccount";

  @Before
  public void setUp() {
    applicabilityEngine = new ApplicabilityEngineImpl();
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_nullFilter() {
    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    assertFalse(applicabilityEngine.isApplicable(null, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_nullEntity() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    assertFalse(applicabilityEngine.isApplicable(filter, null, new HashMap<>()));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_kindMismatch() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("api");
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    assertFalse(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_templateKindMapsToWorkflow() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("template");
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("workflow", "scaffolder", "org1", null, "team-a");
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_typeMismatch() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setType("library");
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    assertFalse(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_typeAll_matchesAnyType() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setType("all");
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_ownerMismatch() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setOwners(List.of("team-b", "team-c"));
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    assertFalse(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_ownerMatch() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setOwners(List.of("team-a", "team-b"));
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_ownerNullOnEntity() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setOwners(List.of("team-a"));
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", null);
    assertFalse(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_lifecycleMismatch() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setLifecycle(List.of("production"));
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    Map<String, Object> spec = new HashMap<>();
    spec.put("lifecycle", "experimental");
    entity.setSpec(spec);
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)) == false);
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_lifecycleMatch() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setLifecycle(List.of("production", "experimental"));
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    Map<String, Object> spec = new HashMap<>();
    spec.put("lifecycle", "production");
    entity.setSpec(spec);
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_tagsMismatch() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setTags(List.of("java", "backend"));
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    entity.setTags(List.of("java", "frontend"));
    assertFalse(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_tagsMatch() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setTags(List.of("java", "backend"));
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    entity.setTags(List.of("java", "backend", "microservice"));
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_scopeAccountWildcard() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_scopeAccountOnly_entityAtAccountLevel() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account"));

    InlineCatalogEntity entity = buildEntity("component", "service", null, null, "team-a");
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_scopeAccountOnly_entityAtProjectLevel() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    Map<String, Set<ScopeInfo>> scopeInfos = new HashMap<>();
    scopeInfos.put("account",
        Set.of(ScopeInfo.builder()
                   .accountIdentifier(ACCOUNT_ID)
                   .scopeType(ScopeLevel.ACCOUNT)
                   .uniqueId(ACCOUNT_ID)
                   .build()));
    assertFalse(applicabilityEngine.isApplicable(filter, entity, scopeInfos));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_scopeSpecificOrg_entityInThatOrg() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account.org1.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_scopeSpecificOrg_entityInDifferentOrg() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account.org2.*"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    Map<String, Set<ScopeInfo>> scopeInfos = new HashMap<>();
    scopeInfos.put("scopes",
        Set.of(ScopeInfo.builder()
                   .accountIdentifier(ACCOUNT_ID)
                   .orgIdentifier("org2")
                   .scopeType(ScopeLevel.ORGANIZATION)
                   .uniqueId("org2UniqueId")
                   .build(),
            ScopeInfo.builder()
                .accountIdentifier(ACCOUNT_ID)
                .orgIdentifier("org2")
                .projectIdentifier("proj3")
                .scopeType(ScopeLevel.PROJECT)
                .uniqueId("proj3UniqueId")
                .build()));
    assertFalse(applicabilityEngine.isApplicable(filter, entity, scopeInfos));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_scopeSpecificOrgAndProject() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account.org1.proj1"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_scopeSpecificOrgAndProject_mismatch() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account.org1.proj2"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    Map<String, Set<ScopeInfo>> scopeInfos = new HashMap<>();
    scopeInfos.put("scopes",
        Set.of(ScopeInfo.builder()
                   .accountIdentifier(ACCOUNT_ID)
                   .orgIdentifier("org1")
                   .projectIdentifier("proj2")
                   .scopeType(ScopeLevel.PROJECT)
                   .uniqueId("proj2UniqueId")
                   .build()));
    assertFalse(applicabilityEngine.isApplicable(filter, entity, scopeInfos));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_scopeOrgLevel_entityAtOrgLevel() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account.org"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", null, "team-a");
    Map<String, Set<ScopeInfo>> scopeInfos = new HashMap<>();
    scopeInfos.put("scopes",
        Set.of(ScopeInfo.builder()
                   .accountIdentifier(ACCOUNT_ID)
                   .orgIdentifier("org1")
                   .scopeType(ScopeLevel.ORGANIZATION)
                   .uniqueId("org1UniqueId")
                   .build()));
    assertTrue(applicabilityEngine.isApplicable(filter, entity, scopeInfos));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_scopeProjectLevel_entityAtProjectLevel() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setScopes(List.of("account.org.project"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testIsApplicable_allFiltersMatch() {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setType("service");
    filter.setOwners(List.of("team-a"));
    filter.setLifecycle(List.of("production"));
    filter.setTags(List.of("java"));
    filter.setScopes(List.of("account.org1.proj1"));

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    Map<String, Object> spec = new HashMap<>();
    spec.put("lifecycle", "production");
    entity.setSpec(spec);
    entity.setTags(List.of("java", "backend"));

    assertTrue(applicabilityEngine.isApplicable(filter, entity, buildScopeInfoMap(entity)));
  }

  @Test
  @Owner(developers = HARJAS)
  @Category(UnitTests.class)
  public void testGetApplicableScorecardIds() {
    ScorecardFilter filterMatch = new ScorecardFilter();
    filterMatch.setKind("component");
    filterMatch.setScopes(List.of("account.*"));

    ScorecardFilter filterNoMatch = new ScorecardFilter();
    filterNoMatch.setKind("api");
    filterNoMatch.setScopes(List.of("account.*"));

    ScorecardEntity sc1 = ScorecardEntity.builder().identifier("sc1").filter(filterMatch).build();
    ScorecardEntity sc2 = ScorecardEntity.builder().identifier("sc2").filter(filterNoMatch).build();
    ScorecardEntity sc3 = ScorecardEntity.builder().identifier("sc3").filter(filterMatch).build();

    InlineCatalogEntity entity = buildEntity("component", "service", "org1", "proj1", "team-a");
    Map<String, Set<ScopeInfo>> scopeInfos = buildScopeInfoMap(entity);

    Set<String> result =
        applicabilityEngine.getApplicableScorecardIds(ACCOUNT_ID, entity, List.of(sc1, sc2, sc3), scopeInfos);

    assertEquals(2, result.size());
    assertTrue(result.contains("sc1"));
    assertTrue(result.contains("sc3"));
    assertFalse(result.contains("sc2"));
  }

  private InlineCatalogEntity buildEntity(
      String kind, String type, String orgIdentifier, String projectIdentifier, String owner) {
    InlineCatalogEntity entity = InlineCatalogEntity.builder().build();
    entity.setAccountIdentifier(ACCOUNT_ID);
    entity.setKind(kind);
    entity.setType(type);
    entity.setOrgIdentifier(orgIdentifier);
    entity.setProjectIdentifier(projectIdentifier);
    entity.setOwner(owner);

    String parentUniqueId;
    if (projectIdentifier != null) {
      parentUniqueId = projectIdentifier + "UniqueId";
    } else if (orgIdentifier != null) {
      parentUniqueId = orgIdentifier + "UniqueId";
    } else {
      parentUniqueId = ACCOUNT_ID;
    }
    entity.setParentUniqueId(parentUniqueId);
    return entity;
  }

  private Map<String, Set<ScopeInfo>> buildScopeInfoMap(InlineCatalogEntity entity) {
    ScopeLevel level;
    if (entity.getProjectIdentifier() != null) {
      level = ScopeLevel.PROJECT;
    } else if (entity.getOrgIdentifier() != null) {
      level = ScopeLevel.ORGANIZATION;
    } else {
      level = ScopeLevel.ACCOUNT;
    }

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(entity.getAccountIdentifier())
                              .orgIdentifier(entity.getOrgIdentifier())
                              .projectIdentifier(entity.getProjectIdentifier())
                              .scopeType(level)
                              .uniqueId(entity.getParentUniqueId())
                              .build();

    Map<String, Set<ScopeInfo>> map = new HashMap<>();
    map.put("scopes", Set.of(scopeInfo));
    return map;
  }
}
