/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class OPAEvaluationAggregatorStepParametersTest extends CategoryTest {
  private OPAEvaluationAggregatorStepParameters stepParameters;

  @Before
  public void setUp() {
    stepParameters = new OPAEvaluationAggregatorStepParameters();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuilder() {
    ParameterField<String> evaluationId = ParameterField.createValueField("evaluation-123");

    OPAEvaluationAggregatorStepParameters params =
        OPAEvaluationAggregatorStepParameters.infoBuilder().evaluationId(evaluationId).build();

    assertThat(params.getEvaluationId()).isEqualTo(evaluationId);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuilderWithNullEvaluationId() {
    OPAEvaluationAggregatorStepParameters params =
        OPAEvaluationAggregatorStepParameters.infoBuilder().evaluationId(null).build();

    assertThat(params.getEvaluationId()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetStepType() {
    assertThat(stepParameters.getStepType()).isEqualTo(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR);
  }
}
