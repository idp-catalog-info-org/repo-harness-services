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
import io.harness.category.element.UnitTests;
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
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class AccountAggregationProcessorTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock private AggregationRulesHelper aggregationRulesHelper;
  @Mock private ScoreRepository scoreRepository;
  AggregationRuleEntity aggregationRuleEntity;
  AccountAggregationProcessor processor;

  private static final String ACCOUNT_ID = "test-account-id";
  private static final String ORG_ID = "test-org-id";
  private static final String PROJECT_ID = "test-project-id";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    aggregationRuleEntity = createAggregationRuleEntity(AggregationRuleEntity.AggregationFormula.SUM);
    processor = new AccountAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithValidEntities() {
    ScopeInfo accountScope = createScopeInfo(null, null, ScopeLevel.ACCOUNT);
    ScopeInfo orgScope = createScopeInfo(ORG_ID, null, ScopeLevel.ORGANIZATION);
    ScopeInfo projectScope = createScopeInfo(ORG_ID, PROJECT_ID, ScopeLevel.PROJECT);

    List<ScopeInfo> scopeInfos = Arrays.asList(accountScope, orgScope, projectScope);

    CatalogEntity entity1 = createCatalogEntity("entity1", "parent1", createDecoratedMap(85.5));
    CatalogEntity entity2 = createCatalogEntity("entity2", "parent2", createDecoratedMap(92.3));
    CatalogEntity entity3 = createCatalogEntity("entity3", "parent1", createDecoratedMap(78.1));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(entity1, entity2, entity3));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertThat(accountResult.getAggregationValue()).isEqualTo(255.89999999999998);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithEmptyEntities() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo(null, null, ScopeLevel.ACCOUNT));
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
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithNullMetrics() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo(null, null, ScopeLevel.ACCOUNT));
    CatalogEntity entity1 = createCatalogEntity("entity1", "parent1", new HashMap<>());
    CatalogEntity entity2 = createCatalogEntity("entity2", "parent2", createDecoratedMap("invalid"));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(entity1, entity2));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertNull(accountResult.getAggregationValue());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithMixedValidInvalidMetrics() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo(null, null, ScopeLevel.ACCOUNT));

    CatalogEntity validEntity1 = createCatalogEntity("entity1", "parent1", createDecoratedMap(50.0));
    CatalogEntity invalidEntity = createCatalogEntity("entity2", "parent2", new HashMap<>());
    CatalogEntity validEntity2 = createCatalogEntity("entity3", "parent1", createDecoratedMap(30.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(validEntity1, invalidEntity, validEntity2));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertThat(accountResult.getAggregationValue()).isEqualTo(80.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithAverageFormula() {
    aggregationRuleEntity = createAggregationRuleEntity(AggregationRuleEntity.AggregationFormula.AVG);
    processor = new AccountAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);

    List<ScopeInfo> scopeInfos = List.of(createScopeInfo(null, null, ScopeLevel.ACCOUNT));

    CatalogEntity entity1 = createCatalogEntity("entity1", "parent1", createDecoratedMap(80.0));
    CatalogEntity entity2 = createCatalogEntity("entity2", "parent2", createDecoratedMap(90.0));
    CatalogEntity entity3 = createCatalogEntity("entity3", "parent1", createDecoratedMap(70.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(entity1, entity2, entity3));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO accountResult = result.get(0);
    assertThat(accountResult.getUniqueId()).isEqualTo(ACCOUNT_ID);
    assertThat(accountResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.ACCOUNT);
    assertThat(accountResult.getAggregationValue()).isEqualTo(80.0);
  }

  private ScopeInfo createScopeInfo(String orgId, String projectId, ScopeLevel scopeLevel) {
    ScopeInfo scopeInfo = mock(ScopeInfo.class);
    when(scopeInfo.getAccountIdentifier()).thenReturn(ACCOUNT_ID);
    when(scopeInfo.getOrgIdentifier()).thenReturn(orgId);
    when(scopeInfo.getProjectIdentifier()).thenReturn(projectId);
    when(scopeInfo.getScopeType()).thenReturn(scopeLevel);
    when(scopeInfo.getUniqueId()).thenReturn(generateUniqueId(orgId, projectId));
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
