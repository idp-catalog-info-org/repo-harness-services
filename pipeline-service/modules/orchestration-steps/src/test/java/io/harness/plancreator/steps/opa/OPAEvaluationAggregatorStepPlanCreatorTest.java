/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.opa.OPAEvaluationAggregatorStepInfo;
import io.harness.steps.opa.OPAEvaluationAggregatorStepNode;

import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class OPAEvaluationAggregatorStepPlanCreatorTest extends CategoryTest {
  private OPAEvaluationAggregatorStepPlanCreator opaEvaluationAggregatorStepPlanCreator;

  @Before
  public void setUp() {
    opaEvaluationAggregatorStepPlanCreator = new OPAEvaluationAggregatorStepPlanCreator();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(opaEvaluationAggregatorStepPlanCreator.getFieldClass()).isEqualTo(OPAEvaluationAggregatorStepNode.class);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes = opaEvaluationAggregatorStepPlanCreator.getSupportedStepTypes();
    assertThat(supportedStepTypes).hasSize(1);
    assertThat(supportedStepTypes).containsExactly(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreatePlanForField() throws Exception {
    // Create a step node
    OPAEvaluationAggregatorStepNode stepNode = new OPAEvaluationAggregatorStepNode();
    stepNode.setName("OPA Evaluation Aggregator Step");
    stepNode.setIdentifier("opa_eval_aggregator_step");
    stepNode.setUuid("step-uuid");
    stepNode.setOpaEvaluationAggregatorStepInfo(
        OPAEvaluationAggregatorStepInfo.infoBuilder()
            .evaluationId(io.harness.pms.yaml.ParameterField.createValueField("evaluation-id"))
            .build());

    // Create YAML structure
    String yamlContent = "step:\n"
        + "  type: OPAEvaluationAggregator\n"
        + "  name: OPA Evaluation Aggregator Step\n"
        + "  identifier: opa_eval_aggregator_step\n"
        + "  spec:\n"
        + "    evaluationId: evaluation-id";

    String yamlWithUuid = YamlUtils.injectUuid(yamlContent);
    YamlField stepField = YamlUtils.readTree(yamlWithUuid).getNode().getField("step");

    PlanCreationContext ctx = PlanCreationContext.builder().currentField(stepField).yaml(yamlWithUuid).build();

    PlanCreationResponse response = opaEvaluationAggregatorStepPlanCreator.createPlanForField(ctx, stepNode);

    assertThat(response).isNotNull();
    assertThat(response.getPlanNode()).isNotNull();
    assertThat(response.getPlanNode().getStepType().getType())
        .isEqualTo(StepSpecTypeConstants.OPA_EVALUATION_AGGREGATOR);
  }
}
