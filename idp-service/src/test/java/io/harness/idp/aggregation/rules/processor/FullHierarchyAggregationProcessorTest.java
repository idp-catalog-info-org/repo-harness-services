/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.processor;

import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.idp.aggregation.rules.beans.AggregationRulesDTO;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.helper.AggregationRulesHelper;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collections;
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
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class FullHierarchyAggregationProcessorTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock private AggregationRulesHelper aggregationRulesHelper;
  @Mock private ScoreRepository scoreRepository;
  AggregationRuleEntity aggregationRuleEntity;
  FullHierarchyAggregationProcessor processor;

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
    processor = new FullHierarchyAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithFullHierarchy() {
    ScopeInfo accountScope = createScopeInfo(null, null, ScopeLevel.ACCOUNT, ACCOUNT_ID);
    ScopeInfo orgScope1 = createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, generateUniqueId(ORG_ID_1, null));
    ScopeInfo orgScope2 = createScopeInfo(ORG_ID_2, null, ScopeLevel.ORGANIZATION, generateUniqueId(ORG_ID_2, null));
    ScopeInfo projectScope1 =
        createScopeInfo(ORG_ID_1, PROJECT_ID_1, ScopeLevel.PROJECT, generateUniqueId(ORG_ID_1, PROJECT_ID_1));
    ScopeInfo projectScope2 =
        createScopeInfo(ORG_ID_1, PROJECT_ID_2, ScopeLevel.PROJECT, generateUniqueId(ORG_ID_1, PROJECT_ID_2));
    ScopeInfo projectScope3 =
        createScopeInfo(ORG_ID_2, PROJECT_ID_3, ScopeLevel.PROJECT, generateUniqueId(ORG_ID_2, PROJECT_ID_3));

    List<ScopeInfo> scopeInfos =
        Arrays.asList(accountScope, orgScope1, orgScope2, projectScope1, projectScope2, projectScope3);
    CatalogEntity accountEntity = createCatalogEntity("account-entity", ACCOUNT_ID, createDecoratedMap(100.0));
    CatalogEntity org1Entity =
        createCatalogEntity("org1-entity", generateUniqueId(ORG_ID_1, null), createDecoratedMap(50.0));
    CatalogEntity org2Entity =
        createCatalogEntity("org2-entity", generateUniqueId(ORG_ID_2, null), createDecoratedMap(75.0));
    CatalogEntity project1Entity =
        createCatalogEntity("project1-entity", generateUniqueId(ORG_ID_1, PROJECT_ID_1), createDecoratedMap(30.0));
    CatalogEntity project2Entity =
        createCatalogEntity("project2-entity", generateUniqueId(ORG_ID_1, PROJECT_ID_2), createDecoratedMap(40.0));
    CatalogEntity project3Entity =
        createCatalogEntity("project3-entity", generateUniqueId(ORG_ID_2, PROJECT_ID_3), createDecoratedMap(25.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(
        Arrays.asList(accountEntity, org1Entity, org2Entity, project1Entity, project2Entity, project3Entity));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);

    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertThat(accountResult.getAggregationValue()).isEqualTo(320.0);

    assertThat(accountResult.getChildren()).hasSize(2);
    Map<String, AggregationRulesDTO> orgResults =
        accountResult.getChildren().stream().collect(Collectors.toMap(AggregationRulesDTO::getUniqueId, dto -> dto));

    AggregationRulesDTO org1Result = orgResults.get(generateUniqueId(ORG_ID_1, null));
    assertThat(org1Result).isNotNull();
    assertThat(org1Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ORGANIZATION);
    assertThat(org1Result.getAggregationValue()).isEqualTo(120.0);
    assertThat(org1Result.getChildren()).hasSize(2);

    AggregationRulesDTO org2Result = orgResults.get(generateUniqueId(ORG_ID_2, null));
    assertThat(org2Result).isNotNull();
    assertThat(org2Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ORGANIZATION);
    assertThat(org2Result.getAggregationValue()).isEqualTo(100.0);
    assertThat(org2Result.getChildren()).hasSize(1);

    Map<String, AggregationRulesDTO> org1ProjectResults =
        org1Result.getChildren().stream().collect(Collectors.toMap(AggregationRulesDTO::getUniqueId, dto -> dto));

    AggregationRulesDTO project1Result = org1ProjectResults.get(generateUniqueId(ORG_ID_1, PROJECT_ID_1));
    assertThat(project1Result).isNotNull();
    assertThat(project1Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertThat(project1Result.getAggregationValue()).isEqualTo(30.0);

    AggregationRulesDTO project2Result = org1ProjectResults.get(generateUniqueId(ORG_ID_1, PROJECT_ID_2));
    assertThat(project2Result).isNotNull();
    assertThat(project2Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.PROJECT);
    assertThat(project2Result.getAggregationValue()).isEqualTo(40.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithAccountLevelEntitiesOnly() {
    ScopeInfo accountScope = createScopeInfo(null, null, ScopeLevel.ACCOUNT, ACCOUNT_ID);

    List<ScopeInfo> scopeInfos = List.of(accountScope);

    CatalogEntity accountEntity1 = createCatalogEntity("account-entity-1", ACCOUNT_ID, createDecoratedMap(85.5));
    CatalogEntity accountEntity2 = createCatalogEntity("account-entity-2", ACCOUNT_ID, createDecoratedMap(92.3));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(accountEntity1, accountEntity2));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertThat(accountResult.getAggregationValue()).isEqualTo(177.8);
    assertThat(accountResult.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithPartialHierarchy() {
    ScopeInfo accountScope = createScopeInfo(null, null, ScopeLevel.ACCOUNT, ACCOUNT_ID);
    ScopeInfo orgScope1 = createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, generateUniqueId(ORG_ID_1, null));

    List<ScopeInfo> scopeInfos = Arrays.asList(accountScope, orgScope1);

    CatalogEntity accountEntity = createCatalogEntity("account-entity", ACCOUNT_ID, createDecoratedMap(60.0));
    CatalogEntity org1Entity =
        createCatalogEntity("org1-entity", generateUniqueId(ORG_ID_1, null), createDecoratedMap(40.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(accountEntity, org1Entity));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());
    List<AggregationRulesDTO> result = processor.process();

    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertThat(accountResult.getAggregationValue()).isEqualTo(100.0);

    assertThat(accountResult.getChildren()).hasSize(1);
    AggregationRulesDTO org1Result = accountResult.getChildren().get(0);
    assertThat(org1Result.getUniqueId()).isEqualTo(generateUniqueId(ORG_ID_1, null));
    assertThat(org1Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ORGANIZATION);
    assertThat(org1Result.getAggregationValue()).isEqualTo(40.0);
    assertThat(org1Result.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithEmptyEntities() {
    ScopeInfo accountScope = createScopeInfo(null, null, ScopeLevel.ACCOUNT, ACCOUNT_ID);
    List<ScopeInfo> scopeInfos = List.of(accountScope);
    Set<CatalogEntity> catalogEntities = new HashSet<>();

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertNull(accountResult.getAggregationValue());
    assertThat(accountResult.getChildren()).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithInvalidMetrics() {
    ScopeInfo accountScope = createScopeInfo(null, null, ScopeLevel.ACCOUNT, ACCOUNT_ID);
    ScopeInfo orgScope1 = createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, generateUniqueId(ORG_ID_1, null));

    List<ScopeInfo> scopeInfos = Arrays.asList(accountScope, orgScope1);
    CatalogEntity accountEntity = createCatalogEntity("account-entity", ACCOUNT_ID, new HashMap<>());
    CatalogEntity org1Entity =
        createCatalogEntity("org1-entity", generateUniqueId(ORG_ID_1, null), createDecoratedMap("invalid"));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(accountEntity, org1Entity));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();

    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertNull(accountResult.getAggregationValue());

    assertThat(accountResult.getChildren()).hasSize(1);
    AggregationRulesDTO org1Result = accountResult.getChildren().get(0);
    assertNull(org1Result.getAggregationValue());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithMixedValidInvalidMetrics() {
    ScopeInfo accountScope = createScopeInfo(null, null, ScopeLevel.ACCOUNT, ACCOUNT_ID);
    ScopeInfo orgScope1 = createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, generateUniqueId(ORG_ID_1, null));

    List<ScopeInfo> scopeInfos = Arrays.asList(accountScope, orgScope1);

    CatalogEntity validAccountEntity =
        createCatalogEntity("valid-account-entity", ACCOUNT_ID, createDecoratedMap(50.0));
    CatalogEntity invalidAccountEntity = createCatalogEntity("invalid-account-entity", ACCOUNT_ID, new HashMap<>());
    CatalogEntity validOrgEntity =
        createCatalogEntity("valid-org-entity", generateUniqueId(ORG_ID_1, null), createDecoratedMap(30.0));

    Set<CatalogEntity> catalogEntities =
        new HashSet<>(Arrays.asList(validAccountEntity, invalidAccountEntity, validOrgEntity));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertThat(accountResult.getAggregationValue()).isEqualTo(80.0);

    assertThat(accountResult.getChildren()).hasSize(1);
    AggregationRulesDTO org1Result = accountResult.getChildren().get(0);
    assertThat(org1Result.getAggregationValue()).isEqualTo(30.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithAverageFormula() {
    aggregationRuleEntity = createAggregationRuleEntity(AggregationRuleEntity.AggregationFormula.AVG);
    processor = new FullHierarchyAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);

    ScopeInfo accountScope = createScopeInfo(null, null, ScopeLevel.ACCOUNT, ACCOUNT_ID);
    ScopeInfo orgScope1 = createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, generateUniqueId(ORG_ID_1, null));
    ScopeInfo projectScope1 =
        createScopeInfo(ORG_ID_1, PROJECT_ID_1, ScopeLevel.PROJECT, generateUniqueId(ORG_ID_1, PROJECT_ID_1));

    List<ScopeInfo> scopeInfos = Arrays.asList(accountScope, orgScope1, projectScope1);

    CatalogEntity accountEntity = createCatalogEntity("account-entity", ACCOUNT_ID, createDecoratedMap(90.0));
    CatalogEntity org1Entity =
        createCatalogEntity("org1-entity", generateUniqueId(ORG_ID_1, null), createDecoratedMap(60.0));
    CatalogEntity project1Entity =
        createCatalogEntity("project1-entity", generateUniqueId(ORG_ID_1, PROJECT_ID_1), createDecoratedMap(30.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(accountEntity, org1Entity, project1Entity));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();

    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertThat(accountResult.getAggregationValue()).isEqualTo(60.0);

    assertThat(accountResult.getChildren()).hasSize(1);
    AggregationRulesDTO org1Result = accountResult.getChildren().get(0);
    assertThat(org1Result.getAggregationValue()).isEqualTo(45.0);

    assertThat(org1Result.getChildren()).hasSize(1);
    AggregationRulesDTO project1Result = org1Result.getChildren().get(0);
    assertThat(project1Result.getAggregationValue()).isEqualTo(30.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithMultipleEntitiesPerLevel() {
    ScopeInfo accountScope = createScopeInfo(null, null, ScopeLevel.ACCOUNT, ACCOUNT_ID);
    ScopeInfo orgScope1 = createScopeInfo(ORG_ID_1, null, ScopeLevel.ORGANIZATION, generateUniqueId(ORG_ID_1, null));
    ScopeInfo projectScope1 =
        createScopeInfo(ORG_ID_1, PROJECT_ID_1, ScopeLevel.PROJECT, generateUniqueId(ORG_ID_1, PROJECT_ID_1));

    List<ScopeInfo> scopeInfos = Arrays.asList(accountScope, orgScope1, projectScope1);

    CatalogEntity accountEntity1 = createCatalogEntity("account-entity-1", ACCOUNT_ID, createDecoratedMap(20.0));
    CatalogEntity accountEntity2 = createCatalogEntity("account-entity-2", ACCOUNT_ID, createDecoratedMap(30.0));
    CatalogEntity org1Entity1 =
        createCatalogEntity("org1-entity-1", generateUniqueId(ORG_ID_1, null), createDecoratedMap(15.0));
    CatalogEntity org1Entity2 =
        createCatalogEntity("org1-entity-2", generateUniqueId(ORG_ID_1, null), createDecoratedMap(25.0));
    CatalogEntity project1Entity1 =
        createCatalogEntity("project1-entity-1", generateUniqueId(ORG_ID_1, PROJECT_ID_1), createDecoratedMap(10.0));
    CatalogEntity project1Entity2 =
        createCatalogEntity("project1-entity-2", generateUniqueId(ORG_ID_1, PROJECT_ID_1), createDecoratedMap(20.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(
        Arrays.asList(accountEntity1, accountEntity2, org1Entity1, org1Entity2, project1Entity1, project1Entity2));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    when(aggregationRulesHelper.findAllOrgScopeInfos(scopeInfosAndEntitiesPair.getLeft(), aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair.getLeft());

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertThat(accountResult.getAggregationValue()).isEqualTo(120.0);

    assertThat(accountResult.getChildren()).hasSize(1);
    AggregationRulesDTO org1Result = accountResult.getChildren().get(0);
    assertThat(org1Result.getAggregationValue()).isEqualTo(70.0);

    assertThat(org1Result.getChildren()).hasSize(1);
    AggregationRulesDTO project1Result = org1Result.getChildren().get(0);
    assertThat(project1Result.getAggregationValue()).isEqualTo(30.0);
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

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveWithFullHierarchy() {
    String accountUniqueId = ACCOUNT_ID;
    String org1UniqueId = generateUniqueId(ORG_ID_1, null);
    String org2UniqueId = generateUniqueId(ORG_ID_2, null);
    String project1UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_1);
    String project2UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_2);
    String project3UniqueId = generateUniqueId(ORG_ID_2, PROJECT_ID_3);

    List<AggregationRulesDTO> projectDTOs1 = Arrays.asList(
        createAggregationRulesDTO(project1UniqueId, 30.0), createAggregationRulesDTO(project2UniqueId, 40.0));

    List<AggregationRulesDTO> projectDTOs2 = List.of(createAggregationRulesDTO(project3UniqueId, 25.0));

    List<AggregationRulesDTO> orgDTOs = Arrays.asList(AggregationRulesDTO.builder()
                                                          .uniqueId(org1UniqueId)
                                                          .aggregationValue(120.0)
                                                          .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                                                          .processedScope(AggregationRuleEntity.Scope.ORGANIZATION)
                                                          .children(projectDTOs1)
                                                          .build(),
        AggregationRulesDTO.builder()
            .uniqueId(org2UniqueId)
            .aggregationValue(100.0)
            .operation(AggregationRulesDTO.UpdateOperation.INGEST)
            .processedScope(AggregationRuleEntity.Scope.ORGANIZATION)
            .children(projectDTOs2)
            .build());

    List<AggregationRulesDTO> aggregationRulesDTOs = List.of(AggregationRulesDTO.builder()
                                                                 .uniqueId(accountUniqueId)
                                                                 .aggregationValue(320.0)
                                                                 .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                                                                 .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
                                                                 .children(orgDTOs)
                                                                 .build());

    Set<AggregationRuleEntity.Scope> scopesToAggregateAt = Set.of(AggregationRuleEntity.Scope.ACCOUNT,
        AggregationRuleEntity.Scope.ORGANIZATION, AggregationRuleEntity.Scope.PROJECT);
    aggregationRuleEntity.setScopesToAggregateAt(scopesToAggregateAt);
    List<String> expectedUniqueIds = Arrays.asList(
        accountUniqueId, org1UniqueId, org2UniqueId, project1UniqueId, project2UniqueId, project3UniqueId);

    List<CatalogEntity> existingCatalogEntities =
        expectedUniqueIds.stream()
            .map(id
                -> InlineCatalogEntity.builder()
                       .kind(COMPONENT_KIND)
                       .identifier("test-entity")
                       .accountIdentifier(ACCOUNT_ID)
                       .uniqueId(id)
                       .parentUniqueId(id)
                       .apiVersion("backstage.io/v1alpha1")
                       .referenceType(ReferenceType.INLINE)
                       .yaml("apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: test-entity")
                       .build())
            .collect(Collectors.toList());

    when(aggregationRulesHelper.getCatalogEntitiesByParentUniqueIds(anyList())).thenReturn(existingCatalogEntities);
    processor.save(aggregationRulesDTOs);
    verify(aggregationRulesHelper, times(1)).getCatalogEntitiesByParentUniqueIds(anyList());
    verify(aggregationRulesHelper, times(1)).saveAndAuditChanges(anySet(), anySet());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveWithFilteredScopes() {
    String accountUniqueId = ACCOUNT_ID;
    String org1UniqueId = generateUniqueId(ORG_ID_1, null);
    String project1UniqueId = generateUniqueId(ORG_ID_1, PROJECT_ID_1);

    List<AggregationRulesDTO> projectDTOs = List.of(createAggregationRulesDTO(project1UniqueId, 30.0));

    List<AggregationRulesDTO> orgDTOs = List.of(AggregationRulesDTO.builder()
                                                    .uniqueId(org1UniqueId)
                                                    .aggregationValue(120.0)
                                                    .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                                                    .processedScope(AggregationRuleEntity.Scope.ORGANIZATION)
                                                    .children(projectDTOs)
                                                    .build());

    List<AggregationRulesDTO> aggregationRulesDTOs = List.of(AggregationRulesDTO.builder()
                                                                 .uniqueId(accountUniqueId)
                                                                 .aggregationValue(150.0)
                                                                 .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                                                                 .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
                                                                 .children(orgDTOs)
                                                                 .build());

    Set<AggregationRuleEntity.Scope> scopesToAggregateAt =
        Set.of(AggregationRuleEntity.Scope.ACCOUNT, AggregationRuleEntity.Scope.PROJECT);
    aggregationRuleEntity.setScopesToAggregateAt(scopesToAggregateAt);
    List<String> expectedUniqueIds = Arrays.asList(accountUniqueId, project1UniqueId);

    List<CatalogEntity> existingCatalogEntities =
        expectedUniqueIds.stream()
            .map(id
                -> InlineCatalogEntity.builder()
                       .kind(COMPONENT_KIND)
                       .identifier("test-entity")
                       .accountIdentifier(ACCOUNT_ID)
                       .uniqueId(id)
                       .parentUniqueId(id)
                       .apiVersion("backstage.io/v1alpha1")
                       .referenceType(ReferenceType.INLINE)
                       .yaml("apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: test-entity")
                       .build())
            .collect(Collectors.toList());

    when(aggregationRulesHelper.getCatalogEntitiesByParentUniqueIds(expectedUniqueIds))
        .thenReturn(existingCatalogEntities);
    processor.save(aggregationRulesDTOs);

    verify(aggregationRulesHelper, times(1)).getCatalogEntitiesByParentUniqueIds(expectedUniqueIds);
    verify(aggregationRulesHelper, times(1)).saveAndAuditChanges(anySet(), anySet());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveWithSystemScopeFiltering() {
    String accountUniqueId = ACCOUNT_ID;
    String systemUniqueId = "system-entity-id";

    List<AggregationRulesDTO> aggregationRulesDTOs =
        Arrays.asList(AggregationRulesDTO.builder()
                          .uniqueId(accountUniqueId)
                          .aggregationValue(150.0)
                          .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                          .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
                          .children(Collections.emptyList())
                          .build(),
            AggregationRulesDTO.builder()
                .uniqueId(systemUniqueId)
                .aggregationValue(75.0)
                .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                .processedScope(AggregationRuleEntity.Scope.SYSTEM)
                .children(Collections.emptyList())
                .build());

    Set<AggregationRuleEntity.Scope> scopesToAggregateAt = Set.of(AggregationRuleEntity.Scope.ACCOUNT);
    aggregationRuleEntity.setScopesToAggregateAt(scopesToAggregateAt);
    List<String> expectedUniqueIds = List.of(accountUniqueId);

    List<CatalogEntity> existingCatalogEntities =
        expectedUniqueIds.stream()
            .map(id
                -> InlineCatalogEntity.builder()
                       .kind(COMPONENT_KIND)
                       .identifier("test-entity")
                       .accountIdentifier(ACCOUNT_ID)
                       .uniqueId(id)
                       .apiVersion("backstage.io/v1alpha1")
                       .referenceType(ReferenceType.INLINE)
                       .yaml("apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: test-entity")
                       .build())
            .collect(Collectors.toList());

    when(aggregationRulesHelper.getCatalogEntitiesByParentUniqueIds(expectedUniqueIds))
        .thenReturn(existingCatalogEntities);

    processor.save(aggregationRulesDTOs);

    verify(aggregationRulesHelper, times(1)).getCatalogEntitiesByParentUniqueIds(expectedUniqueIds);
    verify(aggregationRulesHelper, times(1)).saveAndAuditChanges(anySet(), anySet());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveWithNullDTOsFiltering() {
    String accountUniqueId = ACCOUNT_ID;

    List<AggregationRulesDTO> aggregationRulesDTOs =
        Arrays.asList(AggregationRulesDTO.builder()
                          .uniqueId(accountUniqueId)
                          .aggregationValue(150.0)
                          .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                          .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
                          .children(Collections.emptyList())
                          .build(),
            null, null);

    Set<AggregationRuleEntity.Scope> scopesToAggregateAt = Set.of(AggregationRuleEntity.Scope.ACCOUNT);
    aggregationRuleEntity.setScopesToAggregateAt(scopesToAggregateAt);

    List<String> expectedUniqueIds = List.of(accountUniqueId);

    List<CatalogEntity> existingCatalogEntities =
        expectedUniqueIds.stream()
            .map(id
                -> InlineCatalogEntity.builder()
                       .kind(COMPONENT_KIND)
                       .identifier("test-entity")
                       .accountIdentifier(ACCOUNT_ID)
                       .uniqueId(id)
                       .apiVersion("backstage.io/v1alpha1")
                       .referenceType(ReferenceType.INLINE)
                       .yaml("apiVersion: backstage.io/v1alpha1\nkind: Component\nmetadata:\n  name: test-entity")
                       .build())
            .collect(Collectors.toList());

    when(aggregationRulesHelper.getCatalogEntitiesByParentUniqueIds(expectedUniqueIds))
        .thenReturn(existingCatalogEntities);

    processor.save(aggregationRulesDTOs);

    verify(aggregationRulesHelper, times(1)).getCatalogEntitiesByParentUniqueIds(expectedUniqueIds);
    verify(aggregationRulesHelper, times(1)).saveAndAuditChanges(anySet(), anySet());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveWithEmptyDTOsList() {
    List<AggregationRulesDTO> emptyAggregationRulesDTOs = Collections.emptyList();
    Set<AggregationRuleEntity.Scope> scopesToAggregateAt = Set.of(AggregationRuleEntity.Scope.ACCOUNT);
    aggregationRuleEntity.setScopesToAggregateAt(scopesToAggregateAt);
    when(aggregationRulesHelper.getCatalogEntitiesByParentUniqueIds(Collections.emptyList()))
        .thenReturn(Collections.emptyList());

    processor.save(emptyAggregationRulesDTOs);
    verify(aggregationRulesHelper, times(1)).getCatalogEntitiesByParentUniqueIds(Collections.emptyList());
    verify(aggregationRulesHelper, times(1)).saveAndAuditChanges(anySet(), any(Set.class));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveWithNoMatchingScopes() {
    String orgUniqueId = generateUniqueId(ORG_ID_1, null);

    List<AggregationRulesDTO> aggregationRulesDTOs =
        Arrays.asList(AggregationRulesDTO.builder()
                          .uniqueId(ACCOUNT_ID)
                          .aggregationValue(150.0)
                          .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                          .processedScope(AggregationRuleEntity.Scope.ACCOUNT)
                          .children(Collections.emptyList())
                          .build(),
            AggregationRulesDTO.builder()
                .uniqueId(orgUniqueId)
                .aggregationValue(75.0)
                .operation(AggregationRulesDTO.UpdateOperation.INGEST)
                .processedScope(AggregationRuleEntity.Scope.ORGANIZATION)
                .children(Collections.emptyList())
                .build());
    Set<AggregationRuleEntity.Scope> scopesToAggregateAt = Set.of(AggregationRuleEntity.Scope.PROJECT);
    aggregationRuleEntity.setScopesToAggregateAt(scopesToAggregateAt);
    when(aggregationRulesHelper.getCatalogEntitiesByParentUniqueIds(Collections.emptyList()))
        .thenReturn(Collections.emptyList());

    processor.save(aggregationRulesDTOs);

    verify(aggregationRulesHelper, times(1)).getCatalogEntitiesByParentUniqueIds(Collections.emptyList());
    verify(aggregationRulesHelper, times(1)).saveAndAuditChanges(anySet(), anySet());
  }

  private AggregationRulesDTO createAggregationRulesDTO(String uniqueId, Double aggregationValue) {
    return AggregationRulesDTO.builder()
        .uniqueId(uniqueId)
        .aggregationValue(aggregationValue)
        .operation(AggregationRulesDTO.UpdateOperation.INGEST)
        .processedScope(AggregationRuleEntity.Scope.PROJECT)
        .children(Collections.emptyList())
        .build();
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
