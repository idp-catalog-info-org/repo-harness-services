/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.EBTASAM;

import static junit.framework.TestCase.assertEquals;

import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.plugin.CommonPipelinePluginStep;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.SubCategory;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.Mock;

public class CommonPipelinePluginStepTest extends CIExecutionTestBase {
  @Mock(answer = Answers.CALLS_REAL_METHODS) CommonPipelinePluginStep commonPipelinePluginStep;
  @Before
  public void setup() {}

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testNoWrapperSteps() {
    Level pipelineLevel = createLevel("pipeline", "PIPELINE", null);
    Level stageLevel = createLevel("stage", "STAGE", null);
    List<Level> levels = Arrays.asList(pipelineLevel, stageLevel);
    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).setPlanExecutionId("planExecutionId").build();
    String stepIdentifier = "run_unit_tests";

    String result = commonPipelinePluginStep.getUniqueStepIdentifier(ambiance, stepIdentifier);

    assertEquals("run_unit_tests", result);
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testStepGroupLevel() {
    Level stageLevel = createLevel("deploy_stage", "STAGE", null);
    Level stepGroupLevel = createLevel("integration_tests", "STEP_GROUP", null);
    List<Level> levels = Arrays.asList(stageLevel, stepGroupLevel);
    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).setPlanExecutionId("planExecutionId").build();
    String stepIdentifier = "run_selenium";

    String result = commonPipelinePluginStep.getUniqueStepIdentifier(ambiance, stepIdentifier);

    assertEquals("integration_tests_run_selenium", result);
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testInsertStepLevel() {
    Level templateLevel = createLevel("template", "TEMPLATE", null);
    Level insertLevel = createLevel("inject_vars", "INSERT", SubCategory.STEP_LEVEL);
    List<Level> levels = Arrays.asList(templateLevel, insertLevel);
    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).setPlanExecutionId("planExecutionId").build();
    String stepIdentifier = "validate_inputs";

    String result = commonPipelinePluginStep.getUniqueStepIdentifier(ambiance, stepIdentifier);

    assertEquals("inject_vars_validate_inputs", result);
  }
  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testInsertStageLevel() {
    Level templateLevel = createLevel("template", "TEMPLATE", null);
    Level insertLevel = createLevel("inject_pipeline", "INSERT", SubCategory.STAGE_LEVEL);
    List<Level> levels = Arrays.asList(templateLevel, insertLevel);
    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(levels).setPlanExecutionId("planExecutionId").build();
    String stepIdentifier = "deploy_app";

    String result = commonPipelinePluginStep.getUniqueStepIdentifier(ambiance, stepIdentifier);

    assertEquals("deploy_app", result);
  }
  private Level createLevel(String identifier, String stepType, SubCategory subCategory) {
    StepType.Builder stepTypeBuilder = StepType.newBuilder().setType(stepType);
    if (subCategory != null) {
      stepTypeBuilder.setSubCategory(subCategory);
    }
    return Level.newBuilder().setIdentifier(identifier).setStepType(stepTypeBuilder.build()).build();
  }
}
