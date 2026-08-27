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

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class OPAEvaluationStepParametersTest extends CategoryTest {
  private OPAEvaluationStepParameters stepParameters;

  @Before
  public void setUp() {
    stepParameters = new OPAEvaluationStepParameters();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuilder() {
    ParameterField<String> policySetId = ParameterField.createValueField("policy-set-123");
    ParameterField<String> evaluationId = ParameterField.createValueField("evaluation-123");
    ParameterField<String> orgId = ParameterField.createValueField("org-123");
    ParameterField<String> projectId = ParameterField.createValueField("project-123");

    OPAEvaluationStepParameters params = OPAEvaluationStepParameters.infoBuilder()
                                             .policySetId(policySetId)
                                             .evaluationId(evaluationId)
                                             .policySetOrgId(orgId)
                                             .policySetProjectId(projectId)
                                             .build();

    assertThat(params.getPolicySetId()).isEqualTo(policySetId);
    assertThat(params.getEvaluationId()).isEqualTo(evaluationId);
    assertThat(params.getPolicySetOrgId()).isEqualTo(orgId);
    assertThat(params.getPolicySetProjectId()).isEqualTo(projectId);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuilderWithNullValues() {
    OPAEvaluationStepParameters params = OPAEvaluationStepParameters.infoBuilder()
                                             .policySetId(null)
                                             .evaluationId(null)
                                             .policySetOrgId(null)
                                             .policySetProjectId(null)
                                             .build();

    assertThat(params.getPolicySetId()).isNull();
    assertThat(params.getEvaluationId()).isNull();
    assertThat(params.getPolicySetOrgId()).isNull();
    assertThat(params.getPolicySetProjectId()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testInheritanceFromBaseStepInfo() {
    ParameterField<String> image = ParameterField.createValueField("test-image");
    ParameterField<String> connectorRef = ParameterField.createValueField("test-connector");

    OPAEvaluationStepParameters params = OPAEvaluationStepParameters.infoBuilder()
                                             .image(image)
                                             .connectorRef(connectorRef)
                                             .policySetId(ParameterField.createValueField("policy-set-123"))
                                             .build();

    assertThat(params.getImage()).isEqualTo(image);
    assertThat(params.getConnectorRef()).isEqualTo(connectorRef);
  }
}
