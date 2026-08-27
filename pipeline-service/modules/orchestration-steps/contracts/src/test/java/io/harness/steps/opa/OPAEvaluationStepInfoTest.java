/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class OPAEvaluationStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetStepType() {
    OPAEvaluationStepInfo stepInfo =
        OPAEvaluationStepInfo.infoBuilder().policySetId(ParameterField.createValueField("policy-set-123")).build();
    assertEquals(stepInfo.getStepType(), StepSpecTypeConstants.OPA_EVALUATION_STEP_TYPE);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetFacilitatorType() {
    OPAEvaluationStepInfo stepInfo =
        OPAEvaluationStepInfo.infoBuilder().policySetId(ParameterField.createValueField("policy-set-123")).build();
    assertEquals(stepInfo.getFacilitatorType(), OrchestrationFacilitatorType.ASYNC);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetExpressionMode() {
    OPAEvaluationStepInfo stepInfo =
        OPAEvaluationStepInfo.infoBuilder().policySetId(ParameterField.createValueField("policy-set-123")).build();
    assertEquals(stepInfo.getExpressionMode(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetSpecParameters() {
    ParameterField<String> policySetId = ParameterField.createValueField("policy-set-123");
    ParameterField<String> evaluationId = ParameterField.createValueField("evaluation-123");
    OPAEvaluationStepInfo stepInfo =
        OPAEvaluationStepInfo.infoBuilder().policySetId(policySetId).evaluationId(evaluationId).build();
    SpecParameters specParameters = stepInfo.getSpecParameters();

    assertThat(specParameters).isNotNull();
    assertThat(specParameters).isInstanceOf(OPAEvaluationStepParameters.class);
    OPAEvaluationStepParameters stepParameters = (OPAEvaluationStepParameters) specParameters;
    assertThat(stepParameters.getPolicySetId()).isEqualTo(policySetId);
    assertThat(stepParameters.getEvaluationId()).isEqualTo(evaluationId);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetSpecParametersWithNullValues() {
    OPAEvaluationStepInfo stepInfo = OPAEvaluationStepInfo.infoBuilder().policySetId(null).evaluationId(null).build();
    SpecParameters specParameters = stepInfo.getSpecParameters();

    assertThat(specParameters).isNotNull();
    assertThat(specParameters).isInstanceOf(OPAEvaluationStepParameters.class);
    OPAEvaluationStepParameters stepParameters = (OPAEvaluationStepParameters) specParameters;
    assertThat(stepParameters.getPolicySetId()).isNull();
    assertThat(stepParameters.getEvaluationId()).isNull();
  }
}
