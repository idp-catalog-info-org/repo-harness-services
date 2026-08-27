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
public class IDPDwMetricContextTest extends CategoryTest {
  private static final String TEST_METHOD = "GET";
  private static final String TEST_RESOURCE = "users";
  private static final String TEST_CONTAINER = "idp-service";
  private static final String TEST_STATUS_CODE = "200";

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testContextCreationWithAllParameters() {
    try (IDPDwMetricContext context =
             new IDPDwMetricContext(TEST_METHOD, TEST_RESOURCE, TEST_CONTAINER, TEST_STATUS_CODE)) {
      assertThat(context).isNotNull();
    }
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testContextCreationWithoutStatusCode() {
    try (IDPDwMetricContext context = new IDPDwMetricContext(TEST_METHOD, TEST_RESOURCE, TEST_CONTAINER)) {
      assertThat(context).isNotNull();
    }
  }

  @Test
  @Owner(developers = ANKUR)
  @Category(UnitTests.class)
  public void testContextAutoCloseability() {
    IDPDwMetricContext context = new IDPDwMetricContext(TEST_METHOD, TEST_RESOURCE, TEST_CONTAINER, TEST_STATUS_CODE);
    assertThat(context).isNotNull();
    context.close();
  }
}
