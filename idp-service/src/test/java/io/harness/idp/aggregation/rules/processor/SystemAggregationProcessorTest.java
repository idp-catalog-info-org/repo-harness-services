/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.processor;

import static io.harness.idp.catalog.utils.Constants.SYSTEM_KIND;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
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
public class SystemAggregationProcessorTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock private AggregationRulesHelper aggregationRulesHelper;
  @Mock private ScoreRepository scoreRepository;
  AggregationRuleEntity aggregationRuleEntity;
  SystemAggregationProcessor processor;

  private static final String ACCOUNT_ID = "test-account-id";
  private static final String ORG_ID = "test-org-id";
  private static final String PROJECT_ID = "test-project-id";
  private static final String SYSTEM_REF_1 = "payment-system";
  private static final String SYSTEM_REF_2 = "user-management-system";
  private static final String SYSTEM_REF_3 = "notification-system";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    aggregationRuleEntity = createAggregationRuleEntity(AggregationRuleEntity.AggregationFormula.SUM);
    processor = new SystemAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithSingleSystemRef() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo());

    CatalogEntity entity1 =
        createCatalogEntityWithSystemRef("entity-1", "parent-1", SYSTEM_REF_1, createDecoratedMap(50.0));
    CatalogEntity entity2 =
        createCatalogEntityWithSystemRef("entity-2", "parent-2", SYSTEM_REF_1, createDecoratedMap(75.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(entity1, entity2));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);
    List<AggregationRulesDTO> result = processor.process();

    assertThat(result).hasSize(1);
    AggregationRulesDTO systemResult = result.get(0);
    assertThat(systemResult.getUniqueId()).isEqualTo(SYSTEM_REF_1);
    assertThat(systemResult.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.SYSTEM);
    assertThat(systemResult.getAggregationValue()).isEqualTo(125.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithMultipleSystemRefs() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo());

    CatalogEntity entity1 =
        createCatalogEntityWithSystemRef("entity-1", "parent-1", SYSTEM_REF_1, createDecoratedMap(30.0));
    CatalogEntity entity2 =
        createCatalogEntityWithSystemRef("entity-2", "parent-2", SYSTEM_REF_2, createDecoratedMap(40.0));
    CatalogEntity entity3 =
        createCatalogEntityWithSystemRef("entity-3", "parent-3", SYSTEM_REF_1, createDecoratedMap(20.0));
    CatalogEntity entity4 =
        createCatalogEntityWithSystemRef("entity-4", "parent-4", SYSTEM_REF_3, createDecoratedMap(60.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(entity1, entity2, entity3, entity4));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(3);

    Map<String, AggregationRulesDTO> resultMap =
        result.stream().collect(Collectors.toMap(AggregationRulesDTO::getUniqueId, dto -> dto));
    AggregationRulesDTO system1Result = resultMap.get(SYSTEM_REF_1);
    assertThat(system1Result).isNotNull();
    assertThat(system1Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.SYSTEM);
    assertThat(system1Result.getAggregationValue()).isEqualTo(50.0);

    AggregationRulesDTO system2Result = resultMap.get(SYSTEM_REF_2);
    assertThat(system2Result).isNotNull();
    assertThat(system2Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.SYSTEM);
    assertThat(system2Result.getAggregationValue()).isEqualTo(40.0);

    AggregationRulesDTO system3Result = resultMap.get(SYSTEM_REF_3);
    assertThat(system3Result).isNotNull();
    assertThat(system3Result.getProcessedScope()).isEqualTo(AggregationRuleEntity.Scope.SYSTEM);
    assertThat(system3Result.getAggregationValue()).isEqualTo(60.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithSystemRefList() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo());

    List<String> systemList = Arrays.asList(SYSTEM_REF_1, SYSTEM_REF_2);
    CatalogEntity entity =
        createCatalogEntityWithSystemRefList("entity-1", "parent-1", systemList, createDecoratedMap(100.0));

    Set<CatalogEntity> catalogEntities = Set.of(entity);

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(2);

    Map<String, AggregationRulesDTO> resultMap =
        result.stream().collect(Collectors.toMap(AggregationRulesDTO::getUniqueId, dto -> dto));
    AggregationRulesDTO system1Result = resultMap.get(SYSTEM_REF_1);
    assertThat(system1Result).isNotNull();
    assertThat(system1Result.getAggregationValue()).isEqualTo(100.0);

    AggregationRulesDTO system2Result = resultMap.get(SYSTEM_REF_2);
    assertThat(system2Result).isNotNull();
    assertThat(system2Result.getAggregationValue()).isEqualTo(100.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithNoSystemRefs() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo());

    CatalogEntity entityWithoutSystem = createCatalogEntityWithoutSystemRef(createDecoratedMap(50.0));

    Set<CatalogEntity> catalogEntities = Set.of(entityWithoutSystem);

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithEmptySystemRef() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo());

    CatalogEntity entityWithEmptySystem =
        createCatalogEntityWithSystemRef("entity-1", "parent-1", "", createDecoratedMap(50.0));

    Set<CatalogEntity> catalogEntities = Set.of(entityWithEmptySystem);

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithInvalidMetrics() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo());

    CatalogEntity validEntity =
        createCatalogEntityWithSystemRef("entity-1", "parent-1", SYSTEM_REF_1, createDecoratedMap(50.0));
    CatalogEntity invalidEntity =
        createCatalogEntityWithSystemRef("entity-2", "parent-2", SYSTEM_REF_1, new HashMap<>());

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(validEntity, invalidEntity));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO systemResult = result.get(0);
    assertThat(systemResult.getUniqueId()).isEqualTo(SYSTEM_REF_1);
    assertThat(systemResult.getAggregationValue()).isEqualTo(50.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithAverageFormula() {
    aggregationRuleEntity = createAggregationRuleEntity(AggregationRuleEntity.AggregationFormula.AVG);
    processor = new SystemAggregationProcessor(aggregationRulesHelper, aggregationRuleEntity, scoreRepository);

    List<ScopeInfo> scopeInfos = List.of(createScopeInfo());

    CatalogEntity entity1 =
        createCatalogEntityWithSystemRef("entity-1", "parent-1", SYSTEM_REF_1, createDecoratedMap(80.0));
    CatalogEntity entity2 =
        createCatalogEntityWithSystemRef("entity-2", "parent-2", SYSTEM_REF_1, createDecoratedMap(90.0));
    CatalogEntity entity3 =
        createCatalogEntityWithSystemRef("entity-3", "parent-3", SYSTEM_REF_1, createDecoratedMap(70.0));

    Set<CatalogEntity> catalogEntities = new HashSet<>(Arrays.asList(entity1, entity2, entity3));

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).hasSize(1);
    AggregationRulesDTO systemResult = result.get(0);
    assertThat(systemResult.getUniqueId()).isEqualTo(SYSTEM_REF_1);
    assertThat(systemResult.getAggregationValue()).isEqualTo(80.0);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testSaveFiltersSystemScopeOnly() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo());
    processor.scopeInfos = scopeInfos;
    processor.entityRefs = Set.of(SYSTEM_REF_1, SYSTEM_REF_2);

    String systemEntityRef = "system:account/" + SYSTEM_REF_1;
    AggregationRulesDTO systemDTO =
        createAggregationRulesDTOForUpdate(systemEntityRef, 50.0, AggregationRuleEntity.Scope.SYSTEM);
    AggregationRulesDTO accountDTO =
        createAggregationRulesDTOForUpdate("account-id", 100.0, AggregationRuleEntity.Scope.ACCOUNT);
    AggregationRulesDTO orgDTO =
        createAggregationRulesDTOForUpdate("org-id", 75.0, AggregationRuleEntity.Scope.ORGANIZATION);

    List<AggregationRulesDTO> aggregationRulesDTOs = Arrays.asList(systemDTO, accountDTO, orgDTO);

    CatalogEntity existingEntity = InlineCatalogEntity.builder()
                                       .kind(SYSTEM_KIND)
                                       .identifier(SYSTEM_REF_1)
                                       .accountIdentifier(ACCOUNT_ID)
                                       .uniqueId(systemEntityRef)
                                       .build();
    Set<CatalogEntity> existingEntities = Set.of(existingEntity);
    when(aggregationRulesHelper.getCatalogEntitiesByRef(eq(ACCOUNT_ID), eq(scopeInfos), anySet()))
        .thenReturn(existingEntities);

    processor.save(aggregationRulesDTOs);

    verify(aggregationRulesHelper, times(1)).getCatalogEntitiesByRef(eq(ACCOUNT_ID), eq(scopeInfos), anySet());
    verify(aggregationRulesHelper, times(1)).saveAndAuditChanges(anySet(), eq(existingEntities));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testProcessWithNullSpec() {
    List<ScopeInfo> scopeInfos = List.of(createScopeInfo());

    CatalogEntity entityWithNullSpec = mock(CatalogEntity.class);
    when(entityWithNullSpec.getUniqueId()).thenReturn("entity-1");
    when(entityWithNullSpec.getParentUniqueId()).thenReturn("parent-1");
    when(entityWithNullSpec.getDecoratedEntityMap()).thenReturn(createDecoratedMap(50.0));
    when(entityWithNullSpec.getSpec()).thenReturn(null);
    when(entityWithNullSpec.getIdentifier()).thenReturn("entity-1");
    when(entityWithNullSpec.getScope()).thenReturn("ACCOUNT");
    when(entityWithNullSpec.getOrgIdentifier()).thenReturn(null);
    when(entityWithNullSpec.getProjectIdentifier()).thenReturn(null);
    when(entityWithNullSpec.getKind()).thenReturn("Component");

    Set<CatalogEntity> catalogEntities = Set.of(entityWithNullSpec);

    Pair<List<ScopeInfo>, Set<CatalogEntity>> scopeInfosAndEntitiesPair = Pair.of(scopeInfos, catalogEntities);

    when(aggregationRulesHelper.scopeInfosAndCatalogEntitiesPair(aggregationRuleEntity))
        .thenReturn(scopeInfosAndEntitiesPair);

    List<AggregationRulesDTO> result = processor.process();
    assertThat(result).isEmpty();
  }

  private ScopeInfo createScopeInfo() {
    ScopeInfo scopeInfo = mock(ScopeInfo.class);
    when(scopeInfo.getAccountIdentifier()).thenReturn(ACCOUNT_ID);
    when(scopeInfo.getOrgIdentifier()).thenReturn(null);
    when(scopeInfo.getProjectIdentifier()).thenReturn(null);
    when(scopeInfo.getScopeType()).thenReturn(ScopeLevel.ACCOUNT);
    when(scopeInfo.getUniqueId()).thenReturn(generateUniqueId(null, null));
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

  private CatalogEntity createCatalogEntityWithSystemRef(
      String uniqueId, String parentUniqueId, String systemRef, Map<String, Object> decoratedEntityMap) {
    CatalogEntity entity = mock(CatalogEntity.class);
    when(entity.getUniqueId()).thenReturn(uniqueId);
    when(entity.getParentUniqueId()).thenReturn(parentUniqueId);
    when(entity.getDecoratedEntityMap()).thenReturn(decoratedEntityMap);
    when(entity.getIdentifier()).thenReturn(uniqueId);
    when(entity.getScope()).thenReturn("ACCOUNT");
    when(entity.getOrgIdentifier()).thenReturn(null);
    when(entity.getProjectIdentifier()).thenReturn(null);
    when(entity.getKind()).thenReturn("Component");

    Map<String, Object> spec = new HashMap<>();
    spec.put("system", systemRef);
    when(entity.getSpec()).thenReturn(spec);

    return entity;
  }

  private CatalogEntity createCatalogEntityWithSystemRefList(
      String uniqueId, String parentUniqueId, List<String> systemRefs, Map<String, Object> decoratedEntityMap) {
    CatalogEntity entity = mock(CatalogEntity.class);
    when(entity.getUniqueId()).thenReturn(uniqueId);
    when(entity.getParentUniqueId()).thenReturn(parentUniqueId);
    when(entity.getDecoratedEntityMap()).thenReturn(decoratedEntityMap);
    when(entity.getIdentifier()).thenReturn(uniqueId);
    when(entity.getScope()).thenReturn("ACCOUNT");
    when(entity.getOrgIdentifier()).thenReturn(null);
    when(entity.getProjectIdentifier()).thenReturn(null);
    when(entity.getKind()).thenReturn("Component");

    Map<String, Object> spec = new HashMap<>();
    spec.put("system", systemRefs);
    when(entity.getSpec()).thenReturn(spec);

    return entity;
  }

  private CatalogEntity createCatalogEntityWithoutSystemRef(Map<String, Object> decoratedEntityMap) {
    CatalogEntity entity = mock(CatalogEntity.class);
    when(entity.getUniqueId()).thenReturn("entity-1");
    when(entity.getParentUniqueId()).thenReturn("parent-1");
    when(entity.getDecoratedEntityMap()).thenReturn(decoratedEntityMap);
    when(entity.getIdentifier()).thenReturn("entity-1");
    when(entity.getScope()).thenReturn("ACCOUNT");
    when(entity.getOrgIdentifier()).thenReturn(null);
    when(entity.getProjectIdentifier()).thenReturn(null);
    when(entity.getKind()).thenReturn("Component");

    Map<String, Object> spec = new HashMap<>();
    when(entity.getSpec()).thenReturn(spec);

    return entity;
  }

  private Map<String, Object> createDecoratedMap(Object scoreValue) {
    Map<String, Object> decoratedMap = new HashMap<>();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("score", scoreValue);
    decoratedMap.put("metadata", metadata);
    return decoratedMap;
  }

  private AggregationRulesDTO createAggregationRulesDTOForUpdate(
      String uniqueId, Double aggregationValue, AggregationRuleEntity.Scope scope) {
    return AggregationRulesDTO.builder()
        .uniqueId(uniqueId)
        .operation(AggregationRulesDTO.UpdateOperation.INGEST)
        .aggregationValue(aggregationValue)
        .processedScope(scope)
        .children(Collections.emptyList())
        .build();
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
