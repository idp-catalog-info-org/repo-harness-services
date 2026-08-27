/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.unified;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.steps.barrier.unified.UnifiedBarrierStepNode;
import io.harness.plancreator.steps.barrier.unified.UnifiedBarrierStepPlanCreator;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanExecutionContext;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.utils.PlanCreatorUtilsCommon;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class UnifiedBarrierStepPlanCreatorTest extends CategoryTest {
  // Test-specific subclass that exposes the protected method
  private static class TestableUnifiedBarrierStepPlanCreator extends UnifiedBarrierStepPlanCreator {
    @Override
    public StepType getStepType() {
      return super.getStepType();
    }
  }

  private TestableUnifiedBarrierStepPlanCreator unifiedBarrierStepPlanCreator;

  @Mock private BarrierService barrierService;

  private String barrierStepYaml;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    barrierStepYaml = getBarrierStepYaml();
    unifiedBarrierStepPlanCreator = new TestableUnifiedBarrierStepPlanCreator();
    setField(unifiedBarrierStepPlanCreator, "barrierService", barrierService);
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getSuperclass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    assertThat(unifiedBarrierStepPlanCreator.getSupportedStepTypes()).hasSize(1);
    assertThat(unifiedBarrierStepPlanCreator.getSupportedStepTypes()).contains(YAMLFieldNameConstants.BARRIER_V1);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(unifiedBarrierStepPlanCreator.getFieldClass()).isEqualTo(UnifiedBarrierStepNode.class);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetStepType() {
    StepType stepType = unifiedBarrierStepPlanCreator.getStepType();
    assertThat(stepType.getType()).isEqualTo(StepSpecTypeConstants.BARRIER);
    assertThat(stepType.getStepCategory()).isEqualTo(StepCategory.STEP);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetFieldObject() throws IOException {
    YamlField yamlField = YamlUtils.readTree(barrierStepYaml);
    UnifiedBarrierStepNode stepNode = unifiedBarrierStepPlanCreator.getFieldObject(yamlField);

    // Verify node structure
    assertThat(stepNode).isNotNull();
    assertThat(stepNode.getType()).isEqualTo(StepSpecTypeConstants.BARRIER);
    assertThat(stepNode.getFacilitatorType()).isEqualTo(OrchestrationFacilitatorType.ASYNC);

    // Verify barrier step info
    assertThat(stepNode.getUnifiedBarrierStepInfo()).isNotNull();
    assertThat(stepNode.getUnifiedBarrierStepInfo().getName()).isEqualTo("my-barrier");
    assertThat(stepNode.getUnifiedBarrierStepInfo().getSpecParameters()).isNotNull();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testInvalidYaml() {
    String invalidYaml = "invalid: yaml: format";
    YamlField mockField = mock(YamlField.class);
    YamlNode mockNode = mock(YamlNode.class);
    when(mockField.getNode()).thenReturn(mockNode);
    when(mockNode.toString()).thenReturn(invalidYaml);

    assertThatThrownBy(() -> unifiedBarrierStepPlanCreator.getFieldObject(mockField))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessageContaining("Unable to parse barrier step yaml.");
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void createPlanForFieldShouldThrowWhenBarrierNameContainsExpression() throws IOException {
    String yamlField = "id: barrier\n"
        + "name: barrier\n"
        + "barrier:\n"
        + "  name: <+step.inputs.barrierName>\n"
        + "timeout: 10m";

    YamlField barrierStepYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    UnifiedBarrierStepNode barrierStepNode =
        YamlUtils.read(barrierStepYamlField.getNode().toString(), UnifiedBarrierStepNode.class);

    Map<String, PlanCreationContextValue> contextMap = new HashMap<>();
    PlanExecutionContext planExecutionContext =
        PlanExecutionContext.newBuilder().setExecutionUuid("executionId").build();
    contextMap.put("metadata", PlanCreationContextValue.newBuilder().setExecutionContext(planExecutionContext).build());
    PlanCreationContext ctx =
        PlanCreationContext.builder().globalContext(contextMap).currentField(barrierStepYamlField).build();

    MockedStatic<PlanCreatorUtilsCommon> mockSettings = Mockito.mockStatic(PlanCreatorUtilsCommon.class, RETURNS_MOCKS);

    assertThatThrownBy(() -> unifiedBarrierStepPlanCreator.createPlanForField(ctx, barrierStepNode))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Variable expressions are not allowed in Barrier Reference");

    mockSettings.close();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void createPlanForFieldShouldThrowWhenBarrierNameContainsPipelineExpression() throws IOException {
    String yamlField = "id: barrier\n"
        + "name: barrier\n"
        + "barrier:\n"
        + "  name: <+pipeline.variables.barrierName>\n"
        + "timeout: 10m";

    YamlField barrierStepYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    UnifiedBarrierStepNode barrierStepNode =
        YamlUtils.read(barrierStepYamlField.getNode().toString(), UnifiedBarrierStepNode.class);

    Map<String, PlanCreationContextValue> contextMap = new HashMap<>();
    PlanExecutionContext planExecutionContext =
        PlanExecutionContext.newBuilder().setExecutionUuid("executionId").build();
    contextMap.put("metadata", PlanCreationContextValue.newBuilder().setExecutionContext(planExecutionContext).build());
    PlanCreationContext ctx =
        PlanCreationContext.builder().globalContext(contextMap).currentField(barrierStepYamlField).build();

    MockedStatic<PlanCreatorUtilsCommon> mockSettings = Mockito.mockStatic(PlanCreatorUtilsCommon.class, RETURNS_MOCKS);

    assertThatThrownBy(() -> unifiedBarrierStepPlanCreator.createPlanForField(ctx, barrierStepNode))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Variable expressions are not allowed in Barrier Reference");

    mockSettings.close();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void createPlanForFieldShouldThrowWhenBarrierNameContainsCelExpression() throws IOException {
    String yamlField = "id: barrier\n"
        + "name: barrier\n"
        + "barrier:\n"
        + "  name: ${{ step.inputs.barrierName }}\n"
        + "timeout: 10m";

    YamlField barrierStepYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    UnifiedBarrierStepNode barrierStepNode =
        YamlUtils.read(barrierStepYamlField.getNode().toString(), UnifiedBarrierStepNode.class);

    Map<String, PlanCreationContextValue> contextMap = new HashMap<>();
    PlanExecutionContext planExecutionContext =
        PlanExecutionContext.newBuilder().setExecutionUuid("executionId").build();
    contextMap.put("metadata", PlanCreationContextValue.newBuilder().setExecutionContext(planExecutionContext).build());
    PlanCreationContext ctx =
        PlanCreationContext.builder().globalContext(contextMap).currentField(barrierStepYamlField).build();

    MockedStatic<PlanCreatorUtilsCommon> mockSettings = Mockito.mockStatic(PlanCreatorUtilsCommon.class, RETURNS_MOCKS);

    assertThatThrownBy(() -> unifiedBarrierStepPlanCreator.createPlanForField(ctx, barrierStepNode))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Variable expressions are not allowed in Barrier Reference");

    mockSettings.close();
  }

  private String getBarrierStepYaml() {
    String barrierStepYaml = "barrier:\n"
        + "  name: my-barrier\n"
        + "timeout: 10m\n"
        + "on-failure:\n"
        + "  errors: all\n"
        + "  action: abort";
    return barrierStepYaml;
  }
}
