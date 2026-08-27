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
public class OPAEvaluationAggregatorStepNodeTest extends CategoryTest {
  private OPAEvaluationAggregatorStepNode stepNode;

  @Before
  public void setUp() {
    stepNode = new OPAEvaluationAggregatorStepNode();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetType() {
    String type = stepNode.getType();
    assertThat(type).isEqualTo(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetStepSpecType() {
    OPAEvaluationAggregatorStepInfo stepInfo = OPAEvaluationAggregatorStepInfo.infoBuilder()
                                                   .evaluationId(ParameterField.createValueField("evaluation-123"))
                                                   .build();
    stepNode.setOpaEvaluationAggregatorStepInfo(stepInfo);

    assertThat(stepNode.getStepSpecType()).isEqualTo(stepInfo);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDefaultType() {
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR);
    assertThat(stepNode.getType()).isEqualTo(OPAEvaluationAggregatorStepNode.StepType.OPAEvaluationAggregator.name);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testStepTypeEnum() {
    assertThat(OPAEvaluationAggregatorStepNode.StepType.OPAEvaluationAggregator.name)
        .isEqualTo(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR);
  }
}
