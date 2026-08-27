/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.processor;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.idp.aggregation.rules.beans.AggregationRulesDTO;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.helper.AggregationRulesHelper;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ProjectAggregationProcessorTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock private AggregationRulesHelper aggregationRulesHelper;
  @Mock private ScoreRepository scoreRepository;
  AggregationRuleEntity aggregationRuleEntity;

  private ProjectAggregationProcessor processor;

  private static final String ACCOUNT_ID = "test-account-id";
  private static final String ORG_ID_1 = "test-org-id-1";
  private static final String ORG_ID_2 = "test-org-id-2";
  private static final String PROJECT_ID_1 = "test-project-id-1";
  private static final String PROJECT_ID_2 = "test-project-id-2";
  private static final String PROJECT_ID_3 = "test-project-id-3";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    aggregationRuleEntity = createAggregationRuleEntity(AggregationRuleEntity.AggregationFormula.SUM);
    processor = new ProjectAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testProcess_WithMultipleOrgsAndProjects() {
    String org1UniqueId = generateUniqueId(ORG_ID_1, null);
    String org2UniqueId = generateUniqueId(ORG_ID_2, null);
    String project1UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_1);
    String project2UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_2);
    String project3UniqueId = generateUniqueId(ORG_ID_2, PROJECT_ID_3);

    List<ScopeInfo> scopeInfos = Arrays.asList(createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, org1UniqueId),
        createScopeInfo(ORG_ID_2, null, ScopeLevel.ORGANIZATION, org2UniqueId),
        createScopeInfo(ORG_ID_1, PROJECT_ID_1, ScopeLevel.PROJECT, project1UniqueId),
        createScopeInfo(ORG_ID_1, PROJECT_ID_2, ScopeLevel.PROJECT, project2UniqueId),
        createScopeInfo(ORG_ID_2, PROJECT_ID_3, ScopeLevel.PROJECT, project3UniqueId));

    CatalogEntity projectEntity1A =
        createCatalogEntity("project-entity-1A", project1UniqueId, createDecoratedMap(50.0));
    CatalogEntity projectEntity1B =
        createCatalogEntity("project-entity-1B", project1UniqueId, createDecoratedMap(30.0));
    CatalogEntity projectEntity2A =
        createCatalogEntity("project-entity-2A", project2UniqueId, createDecoratedMap(75.0));
    CatalogEntity projectEntity3A =
        createCatalogEntity("project-entity-3A", project3UniqueId, createDecoratedMap(25.0));
    CatalogEntity projectEntity3B =
        createCatalogEntity("project-entity-3B", project3UniqueId, createDecoratedMap(35.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(
        Arrays.asList(projectEntity1A, projectEntity1B, projectEntity2A, projectEntity3A, projectEntity3B));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());
    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(3);

    Map<String, AggregationRulesDTO> resultMap =
        result.stream().collect(Collectors.toMap(AggregationRulesDTO::getUniqueId, dto -> dto));

    AggregationRulesDTO project1Result = resultMap.get(project1UniqueId);
    assertThat(project1Result).isNotNull();
    assertThat(project1Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertThat(project1Result.getAggregationValue()).isEqualTo(80.0);

    AggregationRulesDTO project2Result = resultMap.get(project2UniqueId);
    assertThat(project2Result).isNotNull();
    assertThat(project2Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertThat(project2Result.getAggregationValue()).isEqualTo(75.0);

    AggregationRulesDTO project3Result = resultMap.get(project3UniqueId);
    assertThat(project3Result).isNotNull();
    assertThat(project3Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertThat(project3Result.getAggregationValue()).isEqualTo(60.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testProcess_WithSingleOrgMultipleProjects() {
    String org1UniqueId = generateUniqueId(ORG_ID_1, null);
    String project1UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_1);
    String project2UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_2);

    List<ScopeInfo> scopeInfos = Arrays.asList(createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, org1UniqueId),
        createScopeInfo(ORG_ID_1, PROJECT_ID_1, ScopeLevel.PROJECT, project1UniqueId),
        createScopeInfo(ORG_ID_1, PROJECT_ID_2, ScopeLevel.PROJECT, project2UniqueId));

    CatalogEntity projectEntity1 = createCatalogEntity("project-entity-1", project1UniqueId, createDecoratedMap(40.0));
    CatalogEntity projectEntity2 = createCatalogEntity("project-entity-2", project2UniqueId, createDecoratedMap(60.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(projectEntity1, projectEntity2));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(2);

    Map<String, AggregationRulesDTO> resultMap =
        result.stream().collect(Collectors.toMap(AggregationRulesDTO::getUniqueId, dto -> dto));

    AggregationRulesDTO project1Result = resultMap.get(project1UniqueId);
    assertThat(project1Result).isNotNull();
    assertThat(project1Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertThat(project1Result.getAggregationValue()).isEqualTo(40.0);

    AggregationRulesDTO project2Result = resultMap.get(project2UniqueId);
    assertThat(project2Result).isNotNull();
    assertThat(project2Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertThat(project2Result.getAggregationValue()).isEqualTo(60.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testProcess_WithOrgButNoProjects() {
    String org1UniqueId = generateUniqueId(ORG_ID_1, null);

    List<ScopeInfo> scopeInfos = List.of(createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, org1UniqueId));

    Set<CatalogEntity> catalogEntities = new HashSet<>();

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testProcess_WithProjectsHavingNoEntities() {
    String org1UniqueId = generateUniqueId(ORG_ID_1, null);
    String project1UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_1);

    List<ScopeInfo> scopeInfos = Arrays.asList(createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, org1UniqueId),
        createScopeInfo(ORG_ID_1, PROJECT_ID_1, ScopeLevel.PROJECT, project1UniqueId));

    Set<CatalogEntity> catalogEntities = new HashSet<>();

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO projectResult = result.get(0);
    assertThat(projectResult.getUniqueId()).isEqualTo(project1UniqueId);
    assertThat(projectResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertNull(projectResult.getAggregationValue());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testProcess_WithInvalidMetrics() {
    String org1UniqueId = generateUniqueId(ORG_ID_1, null);
    String project1UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_1);

    List<ScopeInfo> scopeInfos = Arrays.asList(createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, org1UniqueId),
        createScopeInfo(ORG_ID_1, PROJECT_ID_1, ScopeLevel.PROJECT, project1UniqueId));

    CatalogEntity invalidEntity1 = createCatalogEntity("invalid-entity-1", project1UniqueId, new HashMap<>());
    CatalogEntity invalidEntity2 =
        createCatalogEntity("invalid-entity-2", project1UniqueId, createDecoratedMap("invalid"));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(invalidEntity1, invalidEntity2));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());
    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO projectResult = result.get(0);
    assertThat(projectResult.getUniqueId()).isEqualTo(project1UniqueId);
    assertThat(projectResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertNull(projectResult.getAggregationValue());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testProcess_WithMixedValidInvalidMetrics() {
    String org1UniqueId = generateUniqueId(ORG_ID_1, null);
    String project1UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_1);

    List<ScopeInfo> scopeInfos = Arrays.asList(createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, org1UniqueId),
        createScopeInfo(ORG_ID_1, PROJECT_ID_1, ScopeLevel.PROJECT, project1UniqueId));

    CatalogEntity validEntity = createCatalogEntity("valid-entity", project1UniqueId, createDecoratedMap(45.0));
    CatalogEntity invalidEntity = createCatalogEntity("invalid-entity", project1UniqueId, new HashMap<>());
    CatalogEntity anotherValidEntity =
        createCatalogEntity("another-valid-entity", project1UniqueId, createDecoratedMap(55.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(validEntity, invalidEntity, anotherValidEntity));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();

    assertThat(result).hasSize(1);
    AggregationRulesDTO projectResult = result.get(0);
    assertThat(projectResult.getUniqueId()).isEqualTo(project1UniqueId);
    assertThat(projectResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertThat(projectResult.getAggregationValue()).isEqualTo(100.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testProcess_WithAverageFormula() {
    aggregationRuleEntity = createAggregationRuleEntity(AggregationRuleEntity.AggregationFormula.AVG);
    processor = new ProjectAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);

    String org1UniqueId = generateUniqueId(ORG_ID_1, null);
    String project1UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_1);

    List<ScopeInfo> scopeInfos = Arrays.asList(createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, org1UniqueId),
        createScopeInfo(ORG_ID_1, PROJECT_ID_1, ScopeLevel.PROJECT, project1UniqueId));

    CatalogEntity entity1 = createCatalogEntity("entity-1", project1UniqueId, createDecoratedMap(80.0));
    CatalogEntity entity2 = createCatalogEntity("entity-2", project1UniqueId, createDecoratedMap(90.0));
    CatalogEntity entity3 = createCatalogEntity("entity-3", project1UniqueId, createDecoratedMap(70.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(entity1, entity2, entity3));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();

    assertThat(result).hasSize(1);
    AggregationRulesDTO projectResult = result.get(0);
    assertThat(projectResult.getUniqueId()).isEqualTo(project1UniqueId);
    assertThat(projectResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);

    assertThat(projectResult.getAggregationValue()).isEqualTo(80.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  public void testProcess_IgnoresAccountScopeInfo() {
    String org1UniqueId = generateUniqueId(ORG_ID_1, null);
    String project1UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_1);

    List<ScopeInfo> scopeInfos = Arrays.asList(createScopeInfo(null, null, ScopeLevel.ACCOUNT, ACCOUNT_ID),
        createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, org1UniqueId),
        createScopeInfo(ORG_ID_1, PROJECT_ID_1, ScopeLevel.PROJECT, project1UniqueId));

    CatalogEntity projectEntity = createCatalogEntity("project-entity", project1UniqueId, createDecoratedMap(50.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(List.of(projectEntity));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();

    assertThat(result).hasSize(1);
    AggregationRulesDTO projectResult = result.get(0);
    assertThat(projectResult.getUniqueId()).isEqualTo(project1UniqueId);
    assertThat(projectResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertThat(projectResult.getAggregationValue()).isEqualTo(50.0);
  }

  private ScopeInfo createScopeInfo(String orgId, String projectId, ScopeLevel scopeLevel, String uniqueId) {
    ScopeInfo scopeInfo = mock(ScopeInfo.class);
    when(scopeInfo.getAccountIdentifier()).thenReturn(ACCOUNT_ID);
    when(scopeInfo.getOrgIdentifier()).thenReturn(orgId);
    when(scopeInfo.getProjectIdentifier()).thenReturn(projectId);
    when(scopeInfo.getScopeType()).thenReturn(scopeLevel);
    when(scopeInfo.getUniqueId()).thenReturn(uniqueId);
    return scopeInfo;
  }

  private String generateUniqueId(String orgId, String projectId) {
    if (projectId != null) {
      return ACCOUNT_ID + ":" + orgId + ":" + projectId;
    } else if (orgId != null) {
      return ACCOUNT_ID + ":" + orgId;
    } else {
      return ACCOUNT_ID;
    }
  }

  private CatalogEntity createCatalogEntity(
      String uniqueId, String parentUniqueId, Map<String, Object> decoratedEntityMap) {
    CatalogEntity entity = mock(CatalogEntity.class);
    when(entity.getUniqueId()).thenReturn(uniqueId);
    when(entity.getParentUniqueId()).thenReturn(parentUniqueId);
    when(entity.getDecoratedEntityMap()).thenReturn(decoratedEntityMap);
    when(entity.getIdentifier()).thenReturn(uniqueId);
    when(entity.getScope()).thenReturn("ACCOUNT");
    when(entity.getOrgIdentifier()).thenReturn(null);
    when(entity.getProjectIdentifier()).thenReturn(null);
    when(entity.getKind()).thenReturn("Component");
    return entity;
  }

  private Map<String, Object> createDecoratedMap(Object scoreValue) {
    Map<String, Object> decoratedMap = new HashMap<>();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("score", scoreValue);
    decoratedMap.put("metadata", metadata);
    return decoratedMap;
  }

  private AggregationRuleEntity createAggregationRuleEntity(AggregationRuleEntity.AggregationFormula formula) {
    return AggregationRuleEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .aggFormula(formula)
        .identifier("rule1")
        .name("rule1")
        .fieldForAgg("metadata.score")
        .build();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
