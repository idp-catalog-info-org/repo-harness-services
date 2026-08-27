/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.aggregation.rules.processor;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.aggregation.rules.entity.AggregationRuleEntity;
import io.harness.idp.aggregation.rules.helper.AggregationRulesHelper;
import io.harness.idp.scorecard.scores.repositories.ScoreRepository;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class AggregationRulesProcessorFactoryTest extends CategoryTest {
  AutoCloseable openMocks;

  @Mock private AggregationRulesHelper aggregationRulesHelper;
  @Mock private ScoreRepository scoreRepository;

  AggregationRulesProcessorFactory factory;

  private static final String ACCOUNT_ID = "test-account-id";

  @Before
  public void setUp() throws Exception {
    openMocks = MockitoAnnotations.openMocks(this);
    factory = new AggregationRulesProcessorFactory();

    Field helperField = AggregationRulesProcessorFactory.class.getDeclaredField("aggregationRulesHelper");
    helperField.setAccessible(true);
    helperField.set(factory, aggregationRulesHelper);

    Field scoreRepoField = AggregationRulesProcessorFactory.class.getDeclaredField("scoreRepository");
    scoreRepoField.setAccessible(true);
    scoreRepoField.set(factory, scoreRepository);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_SystemScopeOnly() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.SYSTEM));
    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(SystemAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_AccountScopeOnly() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.ACCOUNT));

    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(AccountAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_OrganizationScopeOnly() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.ORGANIZATION));

    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(OrgAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_ProjectScopeOnly() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.PROJECT));

    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(ProjectAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_FullHierarchy() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.ACCOUNT,
        AggregationRuleEntity.Scope.ORGANIZATION, AggregationRuleEntity.Scope.PROJECT));

    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(FullHierarchyAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_MultipleScopesButNotAll() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(
        Set.of(AggregationRuleEntity.Scope.ACCOUNT, AggregationRuleEntity.Scope.ORGANIZATION));

    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(FullHierarchyAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_SystemWithHierarchical() {
    AggregationRuleEntity ruleEntity =
        createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.SYSTEM, AggregationRuleEntity.Scope.ACCOUNT,
            AggregationRuleEntity.Scope.ORGANIZATION, AggregationRuleEntity.Scope.PROJECT));
    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(CompositeAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_SystemWithSingleHierarchicalScope() {
    AggregationRuleEntity ruleEntity =
        createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.SYSTEM, AggregationRuleEntity.Scope.ACCOUNT));

    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(CompositeAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_SystemWithPartialHierarchy() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.SYSTEM,
        AggregationRuleEntity.Scope.ORGANIZATION, AggregationRuleEntity.Scope.PROJECT));
    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(CompositeAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_EmptyScopes() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Collections.emptySet());
    assertThatThrownBy(() -> factory.createProcessor(ruleEntity))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No valid aggregation scopes provided");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_InvalidScopeOnly() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Collections.emptySet());
    assertThatThrownBy(() -> factory.createProcessor(ruleEntity))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No valid aggregation scopes provided");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_AccountAndOrganization() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(
        Set.of(AggregationRuleEntity.Scope.ACCOUNT, AggregationRuleEntity.Scope.ORGANIZATION));

    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(FullHierarchyAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_AccountAndProject() {
    AggregationRuleEntity ruleEntity =
        createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.ACCOUNT, AggregationRuleEntity.Scope.PROJECT));
    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(FullHierarchyAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_OrganizationAndProject() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(
        Set.of(AggregationRuleEntity.Scope.ORGANIZATION, AggregationRuleEntity.Scope.PROJECT));

    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(FullHierarchyAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_AllScopesIncludingSystem() {
    AggregationRuleEntity ruleEntity =
        createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.SYSTEM, AggregationRuleEntity.Scope.ACCOUNT,
            AggregationRuleEntity.Scope.ORGANIZATION, AggregationRuleEntity.Scope.PROJECT));

    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(CompositeAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_SystemWithOrgAndProjectOnly() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.SYSTEM,
        AggregationRuleEntity.Scope.ORGANIZATION, AggregationRuleEntity.Scope.PROJECT));

    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(CompositeAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_MultipleCalls_SameInput() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.ACCOUNT));

    AggregationProcessor processor1 = factory.createProcessor(ruleEntity);
    AggregationProcessor processor2 = factory.createProcessor(ruleEntity);
    assertThat(processor1).isInstanceOf(AccountAggregationProcessor.class);
    assertThat(processor2).isInstanceOf(AccountAggregationProcessor.class);
    assertThat(processor1).isNotSameAs(processor2);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_FactoryPreservesRuleEntity() {
    AggregationRuleEntity ruleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.ACCOUNT));
    AggregationProcessor processor = factory.createProcessor(ruleEntity);
    assertThat(processor).isInstanceOf(AccountAggregationProcessor.class);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProcessor_DifferentRuleEntities() {
    AggregationRuleEntity accountRuleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.ACCOUNT));
    AggregationRuleEntity projectRuleEntity = createAggregationRuleEntity(Set.of(AggregationRuleEntity.Scope.PROJECT));

    AggregationProcessor accountProcessor = factory.createProcessor(accountRuleEntity);
    AggregationProcessor projectProcessor = factory.createProcessor(projectRuleEntity);
    assertThat(accountProcessor).isInstanceOf(AccountAggregationProcessor.class);
    assertThat(projectProcessor).isInstanceOf(ProjectAggregationProcessor.class);
  }

  private AggregationRuleEntity createAggregationRuleEntity(Set<AggregationRuleEntity.Scope> scopes) {
    AggregationRuleEntity ruleEntity = mock(AggregationRuleEntity.class);
    when(ruleEntity.getScopesToAggregateAt()).thenReturn(scopes);
    when(ruleEntity.getAccountIdentifier()).thenReturn(ACCOUNT_ID);
    when(ruleEntity.getAggFormula()).thenReturn(AggregationRuleEntity.AggregationFormula.SUM);
    when(ruleEntity.getIdentifier()).thenReturn("test-rule");
    when(ruleEntity.getName()).thenReturn("test-rule");
    when(ruleEntity.getFieldForAgg()).thenReturn("metadata.score");
    return ruleEntity;
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
