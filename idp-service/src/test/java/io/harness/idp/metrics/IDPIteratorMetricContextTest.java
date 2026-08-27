/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.metrics;

import static io.harness.rule.OwnerRule.ANKUR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IDPIteratorMetricContextTest extends CategoryTest {
  private static final String TEST_ACCOUNT = "test-account";
  private static final String TEST_ITERATOR = "TestIterator";

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testContextCreationWithValidParameters() {
    try (IDPIteratorMetricContext context = new IDPIteratorMetricContext(TEST_ACCOUNT, TEST_ITERATOR)) {
      assertThat(context).isNotNull();
    }
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testContextAutoCloseability() {
    IDPIteratorMetricContext context = new IDPIteratorMetricContext(TEST_ACCOUNT, TEST_ITERATOR);
    assertThat(context).isNotNull();
    context.close();
  }
}
