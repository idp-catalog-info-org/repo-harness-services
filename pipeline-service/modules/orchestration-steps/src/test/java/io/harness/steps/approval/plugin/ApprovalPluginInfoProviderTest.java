/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.plugin;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ApprovalPluginInfoProviderTest extends CategoryTest {
  private ApprovalPluginInfoProvider approvalPluginInfoProvider;

  @Before
  public void setUp() {
    approvalPluginInfoProvider = new ApprovalPluginInfoProvider();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testIsSupportedReturnsTrueForHarnessApproval() {
    assertThat(approvalPluginInfoProvider.isSupported(StepSpecTypeConstants.HARNESS_APPROVAL)).isTrue();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testIsSupportedReturnsFalseForOtherStepTypes() {
    assertThat(approvalPluginInfoProvider.isSupported("Run")).isFalse();
    assertThat(approvalPluginInfoProvider.isSupported("GoogleCloudRunDeploy")).isFalse();
    assertThat(approvalPluginInfoProvider.isSupported("Plugin")).isFalse();
    assertThat(approvalPluginInfoProvider.isSupported("OPAEvaluation")).isFalse();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginInfoReturnsShouldSkipTrue() {
    PluginCreationRequest request =
        PluginCreationRequest.newBuilder().setType(StepSpecTypeConstants.HARNESS_APPROVAL).build();
    Set<Integer> usedPorts = new HashSet<>();
    Ambiance ambiance = Ambiance.newBuilder().build();

    PluginCreationResponseWrapper response = approvalPluginInfoProvider.getPluginInfo(request, usedPorts, ambiance);

    assertThat(response.getShouldSkip()).isTrue();
  }
}
