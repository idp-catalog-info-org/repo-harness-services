/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.registrars;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SHUBHENDU;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.pipelinerollback.PipelineRollbackStageStep;
import io.harness.pms.plan.execution.helper.DynamicStageStep;
import io.harness.pms.plan.execution.helper.PipelineStageStep;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineServiceStepRegistrarTest {
  @Test
  @Owner(developers = SHUBHENDU)
  @Category(UnitTests.class)
  public void testGetEngineFacilitators() {
    Map<StepType, Class<? extends Step>> engineSteps = PipelineServiceStepRegistrar.getEngineSteps();
    assertEquals(93L, engineSteps.size());
    assertEquals(true, engineSteps.containsValue(PipelineStageStep.class));
    assertEquals(true, engineSteps.containsValue(DynamicStageStep.class));
    assertEquals(true, engineSteps.containsValue(PipelineRollbackStageStep.class));

    Map<StepType, Class<? extends Step>> orchestrationEngineSteps =
        OrchestrationStepsModuleStepRegistrar.getEngineSteps();
    for (Map.Entry<StepType, Class<? extends Step>> entry : orchestrationEngineSteps.entrySet()) {
      assertTrue(engineSteps.containsValue(entry.getValue()));
    }
  }
}
