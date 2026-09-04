/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.lang.reflect.Method;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ContainerStepInitHelperResourceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";

  private final FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
  private final ContainerStepInitHelper containerStepInitHelper = new ContainerStepInitHelper();

  @Before
  public void setUp() {
    Reflect.on(containerStepInitHelper).set("featureFlagService", featureFlagService);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void shouldRemoveStageWorkloadForCdsConservativeFlag() throws Exception {
    when(featureFlagService.isEnabled(FeatureName.CDS_CONSERVATIVE_K8_RESOURCE_LIMITS, ACCOUNT_ID)).thenReturn(true);

    assertThat(stageWorkloadResource(500)).isEqualTo(0);
  }

  @Test
  @Owner(developers = OwnerRule.ABHISHEK)
  @Category(UnitTests.class)
  public void shouldKeepStageWorkloadWhenOnlyCiConservativeFlagIsOn() throws Exception {
    when(featureFlagService.isEnabled(FeatureName.CI_CONSERVATIVE_K8_RESOURCE_LIMITS, ACCOUNT_ID)).thenReturn(true);

    assertThat(stageWorkloadResource(500)).isEqualTo(500);
  }

  private int stageWorkloadResource(int computedResource) throws Exception {
    Method method = ContainerStepInitHelper.class.getDeclaredMethod("stageWorkloadResource", String.class, int.class);
    method.setAccessible(true);
    return (int) method.invoke(containerStepInitHelper, ACCOUNT_ID, computedResource);
  }
}
