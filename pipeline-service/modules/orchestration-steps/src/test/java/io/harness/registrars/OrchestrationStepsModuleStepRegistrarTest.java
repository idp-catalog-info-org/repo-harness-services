/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.registrars;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.opa.step.OPAEvaluationAggregatorStep;
import io.harness.steps.opa.step.OPAEvaluationStep;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class OrchestrationStepsModuleStepRegistrarTest extends CategoryTest {
  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = OrchestrationStepsModuleStepRegistrar.getEngineSteps();

    assertThat(engineSteps).isNotNull();
    assertThat(engineSteps).isNotEmpty();

    // Verify OPA Evaluation Step is registered
    StepType opaEvaluationStepType = StepType.newBuilder()
                                         .setType(StepSpecTypeConstants.OPA_EVALUATION)
                                         .setStepCategory(io.harness.pms.contracts.steps.StepCategory.STEP)
                                         .build();
    assertThat(engineSteps).containsKey(opaEvaluationStepType);
    assertThat(engineSteps.get(opaEvaluationStepType)).isEqualTo(OPAEvaluationStep.class);

    // Verify OPA Evaluation Aggregator Step is registered
    StepType opaEvaluationAggregatorStepType = StepType.newBuilder()
                                                   .setType(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR)
                                                   .setStepCategory(io.harness.pms.contracts.steps.StepCategory.STEP)
                                                   .build();
    assertThat(engineSteps).containsKey(opaEvaluationAggregatorStepType);
    assertThat(engineSteps.get(opaEvaluationAggregatorStepType)).isEqualTo(OPAEvaluationAggregatorStep.class);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testOPAEvaluationStepRegistration() {
    Map<StepType, Class<? extends Step>> engineSteps = OrchestrationStepsModuleStepRegistrar.getEngineSteps();

    StepType opaEvaluationStepType = OPAEvaluationStep.STEP_TYPE;
    assertThat(engineSteps).containsKey(opaEvaluationStepType);
    assertThat(engineSteps.get(opaEvaluationStepType)).isEqualTo(OPAEvaluationStep.class);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testOPAEvaluationAggregatorStepRegistration() {
    Map<StepType, Class<? extends Step>> engineSteps = OrchestrationStepsModuleStepRegistrar.getEngineSteps();

    StepType opaEvaluationAggregatorStepType = OPAEvaluationAggregatorStep.STEP_TYPE;
    assertThat(engineSteps).containsKey(opaEvaluationAggregatorStepType);
    assertThat(engineSteps.get(opaEvaluationAggregatorStepType)).isEqualTo(OPAEvaluationAggregatorStep.class);
  }
}
