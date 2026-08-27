/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.concurrency.counter;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PlanConcurrencyCounterKeyTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testForAccount() {
    String key = PlanConcurrencyCounterKey.forAccount("acc123");
    assertThat(key).isEqualTo("plan_concurrency:account:acc123");
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testForProject() {
    String key = PlanConcurrencyCounterKey.forProject("acc123", "proj-uuid-456");
    assertThat(key).isEqualTo("plan_concurrency:project:acc123/proj-uuid-456");
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProjectScope() {
    String scope = PlanConcurrencyCounterKey.projectScope("acc123", "proj-uuid-456");
    assertThat(scope).isEqualTo("acc123/proj-uuid-456");
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAccountKeyPattern() {
    String pattern = PlanConcurrencyCounterKey.accountKeyPattern();
    assertThat(pattern).isEqualTo("plan_concurrency:account:*");
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProjectKeyPattern() {
    String pattern = PlanConcurrencyCounterKey.projectKeyPattern();
    assertThat(pattern).isEqualTo("plan_concurrency:project:*");
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testAccountIdFromKey() {
    String accountId = PlanConcurrencyCounterKey.accountIdFromKey("plan_concurrency:account:acc123");
    assertThat(accountId).isEqualTo("acc123");
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProjectScopeFromKey() {
    String scope = PlanConcurrencyCounterKey.projectScopeFromKey("plan_concurrency:project:acc123/proj-uuid-456");
    assertThat(scope).isEqualTo("acc123/proj-uuid-456");
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testForProject_HandlesNullParentUniqueId() {
    String key = PlanConcurrencyCounterKey.forProject("acc123", null);
    assertThat(key).isEqualTo("plan_concurrency:project:acc123/");
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testProjectScope_HandlesNullParentUniqueId() {
    String scope = PlanConcurrencyCounterKey.projectScope("acc123", null);
    assertThat(scope).isEqualTo("acc123/");
  }
}
