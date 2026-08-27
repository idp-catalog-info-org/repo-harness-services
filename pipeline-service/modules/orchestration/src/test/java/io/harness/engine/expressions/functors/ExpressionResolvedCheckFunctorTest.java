/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, components = HarnessModuleComponent.CDS_PIPELINE, unitCoverageRequired = false)
public class ExpressionResolvedCheckFunctorTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testIsResolved() {
    ExpressionResolvedCheckFunctor functor = ExpressionResolvedCheckFunctor.builder().build();
    assertThat(functor.isResolved(null)).isEqualTo(false);
    assertThat(functor.isResolved("<+someExpression>")).isEqualTo(false);
    assertThat(functor.isResolved("someValue")).isEqualTo(true);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testIsUnresolved() {
    ExpressionResolvedCheckFunctor functor = ExpressionResolvedCheckFunctor.builder().build();
    assertThat(functor.isUnresolved(null)).isEqualTo(true);
    assertThat(functor.isUnresolved("<+someExpression>")).isEqualTo(true);
    assertThat(functor.isUnresolved("someValue")).isEqualTo(false);
  }
}
