/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.opa;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.PmsStepPlanCreatorUtils;
import io.harness.plancreator.inject.InjectUtils;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.opa.OPAEvaluationStepInfo;
import io.harness.steps.opa.OPAEvaluationStepNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class OPAEvaluationStepPlanCreatorTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;

  @InjectMocks private OPAEvaluationStepPlanCreator opaEvaluationStepPlanCreator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(opaEvaluationStepPlanCreator.getFieldClass()).isEqualTo(OPAEvaluationStepNode.class);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = opaEvaluationStepPlanCreator.getSupportedTypes();
    assertThat(supportedTypes).hasSize(1);
    assertThat(supportedTypes).containsKey(STEP);
    assertThat(supportedTypes.get(STEP)).containsExactly(StepSpecTypeConstants.OPA_EVALUATION);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreatePlanForStep() {
    String stepNodeId = "step-node-id";
    io.harness.plancreator.steps.common.StepElementParameters stepParameters =
        io.harness.plancreator.steps.common.StepElementParameters.builder()
            .spec(io.harness.steps.opa.OPAEvaluationStepParameters.infoBuilder()
                      .policySetId(io.harness.pms.yaml.ParameterField.createValueField("policy-set-id"))
                      .build())
            .build();
    List<AdviserObtainment> adviserObtainments = Collections.emptyList();

    PlanNode planNode = opaEvaluationStepPlanCreator.createPlanForStep(stepNodeId, stepParameters, adviserObtainments);

    assertThat(planNode).isNotNull();
    assertThat(planNode.getUuid()).isEqualTo(stepNodeId);
    assertThat(planNode.getName()).isEqualTo("OPA Evaluation");
    assertThat(planNode.getIdentifier()).isEqualTo("OPAEvaluation");
    assertThat(planNode.getStepType().getType()).isEqualTo(StepSpecTypeConstants.OPA_EVALUATION);
    assertThat(planNode.getStepType().getStepCategory()).isEqualTo(StepCategory.STEP);
    assertThat(planNode.getGroup()).isEqualTo("STEP");
    assertThat(planNode.getFacilitatorObtainments()).hasSize(1);
    assertThat(planNode.getFacilitatorObtainments().get(0).getType().getType())
        .isEqualTo(OrchestrationFacilitatorType.ASYNC);
    assertThat(planNode.getSkipGraphType().toString()).isEqualTo("NOOP");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodesInsideStepGroupWithInfra() throws Exception {
    // Create a step node
    OPAEvaluationStepNode stepNode = new OPAEvaluationStepNode();
    stepNode.setName("OPA Evaluation Step");
    stepNode.setIdentifier("opa_eval_step");
    stepNode.setUuid("step-uuid");
    stepNode.setOpaEvaluationStepInfo(
        OPAEvaluationStepInfo.infoBuilder()
            .policySetId(io.harness.pms.yaml.ParameterField.createValueField("policy-set-id"))
            .build());

    // Create YAML structure with step group containing infrastructure
    String yamlContent = "stepGroup:\n"
        + "  name: StepGroup_1\n"
        + "  identifier: StepGroup_1\n"
        + "  stepGroupInfra:\n"
        + "    type: KubernetesDirect\n"
        + "    spec:\n"
        + "      connectorRef: connector-ref\n"
        + "      namespace: default\n"
        + "  steps:\n"
        + "    - step:\n"
        + "        type: OPAEvaluation\n"
        + "        name: OPA Evaluation Step\n"
        + "        identifier: opa_eval_step\n"
        + "        spec:\n"
        + "          policySetId: policy-set-id";

    String yamlWithUuid = YamlUtils.injectUuid(yamlContent);
    YamlField stepGroupField = YamlUtils.readTree(yamlWithUuid).getNode().getField("stepGroup");
    YamlField stepsField = stepGroupField.getNode().getField("steps");
    YamlField stepField = stepsField.getNode().asArray().get(0).getField("step");

    PlanCreationContext ctx = PlanCreationContext.builder().currentField(stepField).yaml(yamlWithUuid).build();

    try (MockedStatic<InjectUtils> injectUtilsMock = mockStatic(InjectUtils.class);
         MockedStatic<PmsStepPlanCreatorUtils> pmsStepPlanCreatorUtilsMock = mockStatic(PmsStepPlanCreatorUtils.class);
         MockedStatic<StrategyUtils> strategyUtilsMock = mockStatic(StrategyUtils.class);
         MockedStatic<YamlUtils> yamlUtilsMock = mockStatic(YamlUtils.class)) {
      injectUtilsMock.when(() -> InjectUtils.IsFlexibleTemplatesEnabled(any(PlanCreationContext.class)))
          .thenReturn(false);
      pmsStepPlanCreatorUtilsMock
          .when(()
                    -> PmsStepPlanCreatorUtils.getAdviserObtainmentFromMetaData(
                        any(KryoSerializer.class), any(YamlField.class), anyBoolean(), anyBoolean()))
          .thenReturn(Collections.emptyList());
      pmsStepPlanCreatorUtilsMock.when(() -> PmsStepPlanCreatorUtils.getName(any(OPAEvaluationStepNode.class)))
          .thenReturn("OPA Evaluation Step");
      // Mock the StrategyUtils method - use the overload that matches the actual call signature
      // The method signature is: addStrategyFieldDependencyIfPresent(KryoSerializer, PlanCreationContext, String,
      // String, String, LinkedHashMap<String, PlanCreationResponse>, Map<String, ByteString>, List<AdviserObtainment>,
      // Boolean, boolean) We need to explicitly cast to LinkedHashMap to avoid ambiguity with the Map<String,
      // YamlField> overload
      @SuppressWarnings("unchecked") LinkedHashMap<String, PlanCreationResponse> responseMap = any(LinkedHashMap.class);
      strategyUtilsMock
          .when(()
                    -> StrategyUtils.addStrategyFieldDependencyIfPresent(any(KryoSerializer.class),
                        any(PlanCreationContext.class), anyString(), anyString(), anyString(), responseMap,
                        any(Map.class), any(List.class), any(Boolean.class), anyBoolean()))
          .thenAnswer(invocation -> null);
      strategyUtilsMock.when(() -> StrategyUtils.getSwappedPlanNodeId(any(PlanCreationContext.class), anyString()))
          .thenAnswer(invocation -> invocation.getArgument(1));
      strategyUtilsMock
          .when(() -> StrategyUtils.getIdentifierWithExpression(any(PlanCreationContext.class), anyString()))
          .thenAnswer(invocation -> invocation.getArgument(1));

      // Mock YamlUtils.findParentNode to return the step group node (for step group detection)
      yamlUtilsMock.when(() -> YamlUtils.findParentNode(any(io.harness.pms.yaml.YamlNode.class), eq(STEP_GROUP)))
          .thenReturn(stepGroupField.getNode());
      // Mock ROLLBACK_STEPS to return null (not in rollback)
      yamlUtilsMock.when(() -> YamlUtils.findParentNode(any(io.harness.pms.yaml.YamlNode.class), eq(ROLLBACK_STEPS)))
          .thenReturn(null);

      // Mock kryoSerializer.asBytes() which is called in createPlanForField
      when(kryoSerializer.asBytes(any())).thenReturn(new byte[0]);

      // When inside step group with infra, createPlanForField should be called (not createPlanForChildrenNodes)
      // This bypasses ChildrenPlanCreator pattern and creates only the step node
      PlanCreationResponse result = opaEvaluationStepPlanCreator.createPlanForField(ctx, stepNode);

      // Should only create step node, not init node (since inside step group with infra)
      assertThat(result).isNotNull();
      assertThat(result.getPlanNode()).isNotNull();
      assertThat(result.getPlanNode().getUuid()).startsWith("step-");
      assertThat(result.getPlanNode().getStepType().getType()).isEqualTo(StepSpecTypeConstants.OPA_EVALUATION);
    }
  }
}
