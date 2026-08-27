/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.registrars;

import static io.harness.rule.OwnerRule.FJUNIOR;
import static io.harness.rule.OwnerRule.SARTHAK_DALMIA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.AiEvalStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.aitestautomation.AiTestAutomationCIStep;
import io.harness.ci.execution.states.PluginStep;
import io.harness.ci.registrars.ExecutionRegistrar;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.AI)
public class ExecutionRegistrarTest extends CategoryTest {
  @Test
  @Owner(developers = SARTHAK_DALMIA)
  @Category(UnitTests.class)
  public void testGetEngineStepsContainsAiTestAutomationCIStep() {
    assertThat(ExecutionRegistrar.getEngineSteps().get(AiTestAutomationCIStep.STEP_TYPE))
        .isEqualTo(AiTestAutomationCIStep.class);
  }

  @Test
  @Owner(developers = FJUNIOR)
  @Category(UnitTests.class)
  public void testGetEngineStepsContainsAiEvalStepInfo() {
    assertThat(ExecutionRegistrar.getEngineSteps().get(AiEvalStepInfo.STEP_TYPE)).isEqualTo(PluginStep.class);
  }
}
