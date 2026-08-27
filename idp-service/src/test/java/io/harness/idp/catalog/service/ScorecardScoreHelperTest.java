/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.repositories.ScoreEntityByScorecardIdentifierEntityIdentifier;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@OwnedBy(HarnessTeam.IDP)
public class ScorecardScoreHelperTest extends CategoryTest {
  private static final String TEST_ACCOUNT = "account1";

  @Mock private ScoreRepository scoreRepository;
  @Mock private AggregationResults<ScoreEntityByScorecardIdentifierEntityIdentifier> aggregationResults;

  @InjectMocks private ScorecardScoreHelper scorecardScoreHelper;

  private ScopeTopology topology;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    topology = ScopeTopology.builder()
                   .accountUniqueId(TEST_ACCOUNT)
                   .orgs(Map.of("org1",
                       ScopeTopology.OrgNode.builder()
                           .uniqueId("account1/org1")
                           .projects(Map.of("project1", "account1/org1/project1"))
                           .build(),
                       "org2",
                       ScopeTopology.OrgNode.builder()
                           .uniqueId("account1/org2")
                           .projects(Map.of("project2", "account1/org2/project2"))
                           .build()))
                   .build();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_EmptyEntities_ReturnsEmptyMap() {
    List<ScorecardAndChecks> scorecards = List.of(buildScorecardAndChecks("sc1", "component", null, null));
    Map<String, List<ScoreEntity>> result =
        scorecardScoreHelper.fetchScoresForEntities(TEST_ACCOUNT, Collections.emptyList(), scorecards, topology);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_EmptyScorecards_ReturnsEmptyMap() {
    List<InlineCatalogEntity> entities = List.of(buildEntity("comp1", "component", "service", TEST_ACCOUNT));
    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(entities), new ArrayList<>(), topology);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_NullEntities_ReturnsEmptyMap() {
    List<ScorecardAndChecks> scorecards = List.of(buildScorecardAndChecks("sc1", "component", null, null));
    Map<String, List<ScoreEntity>> result =
        scorecardScoreHelper.fetchScoresForEntities(TEST_ACCOUNT, null, scorecards, topology);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_NullScorecards_ReturnsEmptyMap() {
    List<InlineCatalogEntity> entities = List.of(buildEntity("comp1", "component", "service", TEST_ACCOUNT));
    Map<String, List<ScoreEntity>> result =
        scorecardScoreHelper.fetchScoresForEntities(TEST_ACCOUNT, new ArrayList<>(entities), null, topology);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_NoMatchingKind_ReturnsEmptyMap() {
    InlineCatalogEntity entity = buildEntity("comp1", "component", "service", TEST_ACCOUNT);
    ScorecardAndChecks scorecard = buildScorecardAndChecks("sc1", "api", null, null);

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(entity)), List.of(scorecard), topology);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_SingleScorecardMatchesSingleEntity() {
    InlineCatalogEntity entity = buildEntity("comp1", "component", "service", TEST_ACCOUNT);
    ScorecardAndChecks scorecard = buildScorecardAndChecks("sc1", "component", null, List.of("account.*"));

    String entityDbKey = "account/component/comp1";
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT)
                                  .entityIdentifier(entityDbKey)
                                  .scorecardIdentifier("sc1")
                                  .score(85)
                                  .build();

    ScoreEntityByScorecardIdentifierEntityIdentifier dbResult =
        ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
            .entityIdentifier(entityDbKey)
            .scorecardIdentifier("sc1")
            .scoreEntity(scoreEntity)
            .build();

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(List.of(dbResult));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(entity)), List.of(scorecard), topology);

    assertThat(result).hasSize(1);
    String entityRef = "component:account/comp1";
    assertThat(result).containsKey(entityRef);
    assertThat(result.get(entityRef)).hasSize(1);
    assertThat(result.get(entityRef).get(0).getScorecardIdentifier()).isEqualTo("sc1");
    assertThat(result.get(entityRef).get(0).getScore()).isEqualTo(85);
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_ScorecardScopeFiltering() {
    InlineCatalogEntity entity = buildEntityWithParent("comp1", "component", "service", "account1/org2", "org2", null);
    ScorecardAndChecks scorecard = buildScorecardAndChecks("sc1", "component", null, List.of("account.org1.*"));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(entity)), List.of(scorecard), topology);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_ScorecardTypeFilter() {
    InlineCatalogEntity libraryEntity = buildEntity("comp1", "component", "library", TEST_ACCOUNT);
    InlineCatalogEntity serviceEntity = buildEntity("comp2", "component", "service", TEST_ACCOUNT);

    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setType("service");
    filter.setScopes(List.of("account.*"));

    ScorecardEntity scorecardEntity =
        ScorecardEntity.builder().identifier("sc1").accountIdentifier(TEST_ACCOUNT).filter(filter).build();
    ScorecardAndChecks scorecard = ScorecardAndChecks.builder().scorecard(scorecardEntity).build();

    String serviceEntityDbKey = "account/component/comp2";
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT)
                                  .entityIdentifier(serviceEntityDbKey)
                                  .scorecardIdentifier("sc1")
                                  .score(90)
                                  .build();

    ScoreEntityByScorecardIdentifierEntityIdentifier dbResult =
        ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
            .entityIdentifier(serviceEntityDbKey)
            .scorecardIdentifier("sc1")
            .scoreEntity(scoreEntity)
            .build();

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(List.of(dbResult));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(libraryEntity, serviceEntity)), List.of(scorecard), topology);

    assertThat(result).hasSize(1);
    String serviceEntityRef = "component:account/comp2";
    assertThat(result).containsKey(serviceEntityRef);
    assertThat(result).doesNotContainKey("component:account/comp1");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_ScorecardOwnerFilter() {
    InlineCatalogEntity entityTeamB = buildEntity("comp1", "component", "service", TEST_ACCOUNT);
    entityTeamB.setOwner("team-b");

    InlineCatalogEntity entityTeamA = buildEntity("comp2", "component", "service", TEST_ACCOUNT);
    entityTeamA.setOwner("team-a");

    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setOwners(List.of("team-a"));
    filter.setScopes(List.of("account.*"));

    ScorecardEntity scorecardEntity =
        ScorecardEntity.builder().identifier("sc1").accountIdentifier(TEST_ACCOUNT).filter(filter).build();
    ScorecardAndChecks scorecard = ScorecardAndChecks.builder().scorecard(scorecardEntity).build();

    String entityDbKey = "account/component/comp2";
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT)
                                  .entityIdentifier(entityDbKey)
                                  .scorecardIdentifier("sc1")
                                  .score(75)
                                  .build();

    ScoreEntityByScorecardIdentifierEntityIdentifier dbResult =
        ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
            .entityIdentifier(entityDbKey)
            .scorecardIdentifier("sc1")
            .scoreEntity(scoreEntity)
            .build();

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(List.of(dbResult));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(entityTeamB, entityTeamA)), List.of(scorecard), topology);

    assertThat(result).hasSize(1);
    assertThat(result).containsKey("component:account/comp2");
    assertThat(result).doesNotContainKey("component:account/comp1");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_ScorecardLifecycleFilter() {
    InlineCatalogEntity experimentalEntity = buildEntity("comp1", "component", "service", TEST_ACCOUNT);
    experimentalEntity.setSpec(Map.of("lifecycle", "experimental"));

    InlineCatalogEntity productionEntity = buildEntity("comp2", "component", "service", TEST_ACCOUNT);
    productionEntity.setSpec(Map.of("lifecycle", "production"));

    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setLifecycle(List.of("production"));
    filter.setScopes(List.of("account.*"));

    ScorecardEntity scorecardEntity =
        ScorecardEntity.builder().identifier("sc1").accountIdentifier(TEST_ACCOUNT).filter(filter).build();
    ScorecardAndChecks scorecard = ScorecardAndChecks.builder().scorecard(scorecardEntity).build();

    String entityDbKey = "account/component/comp2";
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT)
                                  .entityIdentifier(entityDbKey)
                                  .scorecardIdentifier("sc1")
                                  .score(80)
                                  .build();

    ScoreEntityByScorecardIdentifierEntityIdentifier dbResult =
        ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
            .entityIdentifier(entityDbKey)
            .scorecardIdentifier("sc1")
            .scoreEntity(scoreEntity)
            .build();

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(List.of(dbResult));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(experimentalEntity, productionEntity)), List.of(scorecard), topology);

    assertThat(result).hasSize(1);
    assertThat(result).containsKey("component:account/comp2");
    assertThat(result).doesNotContainKey("component:account/comp1");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_ScorecardTagsFilter() {
    InlineCatalogEntity partialTagEntity = buildEntity("comp1", "component", "service", TEST_ACCOUNT);
    partialTagEntity.setTags(List.of("java"));

    InlineCatalogEntity fullTagEntity = buildEntity("comp2", "component", "service", TEST_ACCOUNT);
    fullTagEntity.setTags(List.of("java", "backend", "api"));

    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setTags(List.of("java", "backend"));
    filter.setScopes(List.of("account.*"));

    ScorecardEntity scorecardEntity =
        ScorecardEntity.builder().identifier("sc1").accountIdentifier(TEST_ACCOUNT).filter(filter).build();
    ScorecardAndChecks scorecard = ScorecardAndChecks.builder().scorecard(scorecardEntity).build();

    String entityDbKey = "account/component/comp2";
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT)
                                  .entityIdentifier(entityDbKey)
                                  .scorecardIdentifier("sc1")
                                  .score(70)
                                  .build();

    ScoreEntityByScorecardIdentifierEntityIdentifier dbResult =
        ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
            .entityIdentifier(entityDbKey)
            .scorecardIdentifier("sc1")
            .scoreEntity(scoreEntity)
            .build();

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(List.of(dbResult));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(partialTagEntity, fullTagEntity)), List.of(scorecard), topology);

    assertThat(result).hasSize(1);
    assertThat(result).containsKey("component:account/comp2");
    assertThat(result).doesNotContainKey("component:account/comp1");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_TemplateToWorkflowKindMapping() {
    InlineCatalogEntity workflowEntity = buildEntity("wf1", "workflow", null, TEST_ACCOUNT);

    ScorecardAndChecks scorecard = buildScorecardAndChecks("sc1", "template", null, List.of("account.*"));

    String entityDbKey = "account/template/wf1";
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT)
                                  .entityIdentifier(entityDbKey)
                                  .scorecardIdentifier("sc1")
                                  .score(60)
                                  .build();

    ScoreEntityByScorecardIdentifierEntityIdentifier dbResult =
        ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
            .entityIdentifier(entityDbKey)
            .scorecardIdentifier("sc1")
            .scoreEntity(scoreEntity)
            .build();

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(List.of(dbResult));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(workflowEntity)), List.of(scorecard), topology);

    assertThat(result).hasSize(1);
    assertThat(result).containsKey("workflow:account/wf1");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_ScorecardNullFilter_Skipped() {
    InlineCatalogEntity entity = buildEntity("comp1", "component", "service", TEST_ACCOUNT);

    ScorecardEntity scorecardEntity =
        ScorecardEntity.builder().identifier("sc1").accountIdentifier(TEST_ACCOUNT).filter(null).build();
    ScorecardAndChecks scorecard = ScorecardAndChecks.builder().scorecard(scorecardEntity).build();

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(entity)), List.of(scorecard), topology);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_TypeFilterAll_MatchesAnyType() {
    InlineCatalogEntity entity = buildEntity("comp1", "component", "library", TEST_ACCOUNT);

    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");
    filter.setType("all");
    filter.setScopes(List.of("account.*"));

    ScorecardEntity scorecardEntity =
        ScorecardEntity.builder().identifier("sc1").accountIdentifier(TEST_ACCOUNT).filter(filter).build();
    ScorecardAndChecks scorecard = ScorecardAndChecks.builder().scorecard(scorecardEntity).build();

    String entityDbKey = "account/component/comp1";
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT)
                                  .entityIdentifier(entityDbKey)
                                  .scorecardIdentifier("sc1")
                                  .score(95)
                                  .build();

    ScoreEntityByScorecardIdentifierEntityIdentifier dbResult =
        ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
            .entityIdentifier(entityDbKey)
            .scorecardIdentifier("sc1")
            .scoreEntity(scoreEntity)
            .build();

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(List.of(dbResult));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(entity)), List.of(scorecard), topology);

    assertThat(result).hasSize(1);
    assertThat(result).containsKey("component:account/comp1");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_TypeFilterNull_MatchesAnyType() {
    InlineCatalogEntity entity = buildEntity("comp1", "component", "website", TEST_ACCOUNT);

    ScorecardAndChecks scorecard = buildScorecardAndChecks("sc1", "component", null, List.of("account.*"));

    String entityDbKey = "account/component/comp1";
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT)
                                  .entityIdentifier(entityDbKey)
                                  .scorecardIdentifier("sc1")
                                  .score(88)
                                  .build();

    ScoreEntityByScorecardIdentifierEntityIdentifier dbResult =
        ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
            .entityIdentifier(entityDbKey)
            .scorecardIdentifier("sc1")
            .scoreEntity(scoreEntity)
            .build();

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(List.of(dbResult));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(entity)), List.of(scorecard), topology);

    assertThat(result).hasSize(1);
    assertThat(result).containsKey("component:account/comp1");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_MultipleScorecardsMultipleEntities() {
    InlineCatalogEntity entityComp1 = buildEntity("comp1", "component", "service", TEST_ACCOUNT);
    InlineCatalogEntity entityComp2 = buildEntity("comp2", "component", "library", TEST_ACCOUNT);
    InlineCatalogEntity entityApi1 = buildEntity("api1", "api", null, TEST_ACCOUNT);

    ScorecardFilter filterComponent = new ScorecardFilter();
    filterComponent.setKind("component");
    filterComponent.setType("service");
    filterComponent.setScopes(List.of("account.*"));

    ScorecardEntity sc1Entity =
        ScorecardEntity.builder().identifier("sc1").accountIdentifier(TEST_ACCOUNT).filter(filterComponent).build();
    ScorecardAndChecks scorecard1 = ScorecardAndChecks.builder().scorecard(sc1Entity).build();

    ScorecardFilter filterApi = new ScorecardFilter();
    filterApi.setKind("api");
    filterApi.setScopes(List.of("account.*"));

    ScorecardEntity sc2Entity =
        ScorecardEntity.builder().identifier("sc2").accountIdentifier(TEST_ACCOUNT).filter(filterApi).build();
    ScorecardAndChecks scorecard2 = ScorecardAndChecks.builder().scorecard(sc2Entity).build();

    String comp1DbKey = "account/component/comp1";
    String api1DbKey = "account/api/api1";

    ScoreEntity scoreComp1 = ScoreEntity.builder()
                                 .accountIdentifier(TEST_ACCOUNT)
                                 .entityIdentifier(comp1DbKey)
                                 .scorecardIdentifier("sc1")
                                 .score(85)
                                 .build();

    ScoreEntity scoreApi1 = ScoreEntity.builder()
                                .accountIdentifier(TEST_ACCOUNT)
                                .entityIdentifier(api1DbKey)
                                .scorecardIdentifier("sc2")
                                .score(90)
                                .build();

    List<ScoreEntityByScorecardIdentifierEntityIdentifier> dbResults =
        List.of(ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
                    .entityIdentifier(comp1DbKey)
                    .scorecardIdentifier("sc1")
                    .scoreEntity(scoreComp1)
                    .build(),
            ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
                .entityIdentifier(api1DbKey)
                .scorecardIdentifier("sc2")
                .scoreEntity(scoreApi1)
                .build());

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(dbResults);

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(TEST_ACCOUNT,
        new ArrayList<>(List.of(entityComp1, entityComp2, entityApi1)), List.of(scorecard1, scorecard2), topology);

    assertThat(result).hasSize(2);
    assertThat(result).containsKey("component:account/comp1");
    assertThat(result).containsKey("api:account/api1");
    assertThat(result).doesNotContainKey("component:account/comp2");
    assertThat(result.get("component:account/comp1")).hasSize(1);
    assertThat(result.get("component:account/comp1").get(0).getScorecardIdentifier()).isEqualTo("sc1");
    assertThat(result.get("api:account/api1")).hasSize(1);
    assertThat(result.get("api:account/api1").get(0).getScorecardIdentifier()).isEqualTo("sc2");
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_ExceptionDuringDbFetch_ReturnsEmptyMap() {
    InlineCatalogEntity entity = buildEntity("comp1", "component", "service", TEST_ACCOUNT);
    ScorecardAndChecks scorecard = buildScorecardAndChecks("sc1", "component", null, List.of("account.*"));

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenThrow(new RuntimeException("DB connection failed"));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(entity)), List.of(scorecard), topology);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_EmptyDbResults_ReturnsEmptyMap() {
    InlineCatalogEntity entity = buildEntity("comp1", "component", "service", TEST_ACCOUNT);
    ScorecardAndChecks scorecard = buildScorecardAndChecks("sc1", "component", null, List.of("account.*"));

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(Collections.emptyList());

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(entity)), List.of(scorecard), topology);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ANKUR)
  @Category(UnitTests.class)
  public void testFetchScores_DefaultScopeFallback() {
    InlineCatalogEntity entity = buildEntity("comp1", "component", "service", TEST_ACCOUNT);

    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind("component");

    ScorecardEntity scorecardEntity =
        ScorecardEntity.builder().identifier("sc1").accountIdentifier(TEST_ACCOUNT).filter(filter).build();
    ScorecardAndChecks scorecard = ScorecardAndChecks.builder().scorecard(scorecardEntity).build();

    String entityDbKey = "account/component/comp1";
    ScoreEntity scoreEntity = ScoreEntity.builder()
                                  .accountIdentifier(TEST_ACCOUNT)
                                  .entityIdentifier(entityDbKey)
                                  .scorecardIdentifier("sc1")
                                  .score(77)
                                  .build();

    ScoreEntityByScorecardIdentifierEntityIdentifier dbResult =
        ScoreEntityByScorecardIdentifierEntityIdentifier.builder()
            .entityIdentifier(entityDbKey)
            .scorecardIdentifier("sc1")
            .scoreEntity(scoreEntity)
            .build();

    when(scoreRepository.getAllLatestScoresByScorecardsForEntities(anyString(), anyList(), anyBoolean()))
        .thenReturn(aggregationResults);
    when(aggregationResults.getMappedResults()).thenReturn(List.of(dbResult));

    Map<String, List<ScoreEntity>> result = scorecardScoreHelper.fetchScoresForEntities(
        TEST_ACCOUNT, new ArrayList<>(List.of(entity)), List.of(scorecard), topology);

    assertThat(result).hasSize(1);
    assertThat(result).containsKey("component:account/comp1");
    assertThat(result.get("component:account/comp1").get(0).getScore()).isEqualTo(77);
  }

  private InlineCatalogEntity buildEntity(String identifier, String kind, String type, String parentUniqueId) {
    return InlineCatalogEntity.builder()
        .accountIdentifier(TEST_ACCOUNT)
        .identifier(identifier)
        .kind(kind)
        .type(type)
        .parentUniqueId(parentUniqueId)
        .build();
  }

  private InlineCatalogEntity buildEntityWithParent(
      String identifier, String kind, String type, String parentUniqueId, String orgId, String projectId) {
    return InlineCatalogEntity.builder()
        .accountIdentifier(TEST_ACCOUNT)
        .identifier(identifier)
        .kind(kind)
        .type(type)
        .parentUniqueId(parentUniqueId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .build();
  }

  private ScorecardAndChecks buildScorecardAndChecks(String identifier, String kind, String type, List<String> scopes) {
    ScorecardFilter filter = new ScorecardFilter();
    filter.setKind(kind);
    if (type != null) {
      filter.setType(type);
    }
    if (scopes != null) {
      filter.setScopes(scopes);
    }

    ScorecardEntity scorecardEntity =
        ScorecardEntity.builder().identifier(identifier).accountIdentifier(TEST_ACCOUNT).filter(filter).build();
    return ScorecardAndChecks.builder().scorecard(scorecardEntity).build();
  }
}
