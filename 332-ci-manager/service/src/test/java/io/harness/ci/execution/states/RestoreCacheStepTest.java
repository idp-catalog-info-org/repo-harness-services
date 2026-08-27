/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.SATYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.RestoreCacheStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class RestoreCacheStepTest extends CategoryTest {
  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testStepType() {
    // Test that the step type constant is correctly initialized
    StepType stepType = RestoreCacheStep.STEP_TYPE;
    assertThat(stepType).isEqualTo(RestoreCacheStepInfo.STEP_TYPE);
    assertThat(stepType.getType()).isEqualTo("RestoreCache");
    assertThat(stepType.getStepCategory().name()).isEqualTo("STEP");
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testStepTypeConsistency() {
    // Ensure the STEP_TYPE is consistent with RestoreCacheStepInfo.STEP_TYPE
    assertThat(RestoreCacheStep.STEP_TYPE.getType()).isEqualTo(RestoreCacheStepInfo.STEP_TYPE.getType());
  }
}
