/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.beans;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ScopeTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testScopeEnumValues() {
    assertThat(Scope.values()).hasSize(3);
    assertThat(Scope.values()).containsExactly(Scope.ACCOUNT, Scope.ORGANIZATION, Scope.PROJECT);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testScopeValueOf() {
    assertThat(Scope.valueOf("ACCOUNT")).isEqualTo(Scope.ACCOUNT);
    assertThat(Scope.valueOf("ORGANIZATION")).isEqualTo(Scope.ORGANIZATION);
    assertThat(Scope.valueOf("PROJECT")).isEqualTo(Scope.PROJECT);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testScopeName() {
    assertThat(Scope.ACCOUNT.name()).isEqualTo("ACCOUNT");
    assertThat(Scope.ORGANIZATION.name()).isEqualTo("ORGANIZATION");
    assertThat(Scope.PROJECT.name()).isEqualTo("PROJECT");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testScopeOrdinal() {
    assertThat(Scope.ACCOUNT.ordinal()).isEqualTo(0);
    assertThat(Scope.ORGANIZATION.ordinal()).isEqualTo(1);
    assertThat(Scope.PROJECT.ordinal()).isEqualTo(2);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testScopeEquality() {
    Scope scope1 = Scope.ACCOUNT;
    Scope scope2 = Scope.ACCOUNT;
    Scope scope3 = Scope.PROJECT;

    assertThat(scope1).isEqualTo(scope2);
    assertThat(scope1).isNotEqualTo(scope3);
    assertThat(scope1 == scope2).isTrue();
    assertThat(scope1 == scope3).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testScopeComparison() {
    assertThat(Scope.ACCOUNT.compareTo(Scope.ORGANIZATION)).isLessThan(0);
    assertThat(Scope.ORGANIZATION.compareTo(Scope.ACCOUNT)).isGreaterThan(0);
    assertThat(Scope.ORGANIZATION.compareTo(Scope.ORGANIZATION)).isEqualTo(0);
    assertThat(Scope.PROJECT.compareTo(Scope.ORGANIZATION)).isGreaterThan(0);
    assertThat(Scope.ACCOUNT.compareTo(Scope.PROJECT)).isLessThan(0);
  }
}
