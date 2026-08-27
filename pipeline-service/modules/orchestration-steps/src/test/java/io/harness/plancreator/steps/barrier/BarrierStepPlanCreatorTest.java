/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.barrier;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_MOCKS;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.distribution.barrier.Barrier;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanExecutionContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.utils.PlanCreatorUtilsCommon;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(PIPELINE)
public class BarrierStepPlanCreatorTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock BarrierService barrierService;
  @Mock PlanCreatorUtilsCommon planCreatorUtilsCommon;
  BarrierStepPlanCreator barrierStepPlanCreator = new BarrierStepPlanCreator();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    setField(barrierStepPlanCreator, "barrierService", barrierService);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(barrierStepPlanCreator.getFieldClass()).isEqualTo(BarrierStepNode.class);
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void getCreatePlanForField() throws IOException {
    String yamlField = "step:\n"
        + "  type: Barrier\n"
        + "  name: Barrier_1\n"
        + "  identifier: Barrier_1\n"
        + "  spec:\n"
        + "    barrierRef: parent.bar1\n"
        + "  timeout: 10m";

    String pipelineYaml = "pipeline:\n"
        + "  name: parentBarrier\n"
        + "  identifier: parentBarrier\n"
        + "  projectIdentifier: test\n"
        + "  orgIdentifier: default\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - parallel:\n"
        + "        - stage:\n"
        + "            name: cus2\n"
        + "            identifier: cus2\n"
        + "            description: \"\"\n"
        + "            type: Custom\n"
        + "            spec:\n"
        + "              execution:\n"
        + "                steps:\n"
        + "                  - step:\n"
        + "                      type: Wait\n"
        + "                      name: Wait_1\n"
        + "                      identifier: Wait_1\n"
        + "                      spec:\n"
        + "                        duration: 10m\n"
        + "                  - step:\n"
        + "                      type: Barrier\n"
        + "                      name: Barrier_1\n"
        + "                      identifier: Barrier_1\n"
        + "                      spec:\n"
        + "                        barrierRef: bar1\n"
        + "                      timeout: 10m\n"
        + "                  - step:\n"
        + "                      type: Wait\n"
        + "                      name: Wait_2\n"
        + "                      identifier: Wait_2\n"
        + "                      spec:\n"
        + "                        duration: 10m";

    YamlField barrierStepYamlField = YamlUtils.injectUuidInYamlField(yamlField);

    Map<String, PlanCreationContextValue> contextMap = new HashMap<>();
    PlanExecutionContext planExecutionContext = PlanExecutionContext.newBuilder()
                                                    .setExecutionUuid("executionId")
                                                    .setPipelineStageInfo(PipelineStageInfo.newBuilder()
                                                                              .setHasParentPipeline(true)
                                                                              .setExecutionId("parentExecutionId")
                                                                              .setStageNodeId("stageNodeId")
                                                                              .build())
                                                    .build();
    contextMap.put("metadata", PlanCreationContextValue.newBuilder().setExecutionContext(planExecutionContext).build());
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(contextMap)
                                  .currentField(barrierStepYamlField.getNode().getField("step"))
                                  .yaml(pipelineYaml)
                                  .build();

    BarrierStepNode barrierStepNode =
        YamlUtils.read(barrierStepYamlField.getNode().getField("step").getNode().toString(), BarrierStepNode.class);
    MockedStatic<PlanCreatorUtilsCommon> mockSettings = Mockito.mockStatic(PlanCreatorUtilsCommon.class, RETURNS_MOCKS);
    when(planCreatorUtilsCommon.getFromParentInfo(anyString(), any(PlanCreationContext.class)))
        .thenReturn(HarnessValue.newBuilder().setStringValue("test").build());

    // Create a mutable list for barrier positions
    List<BarrierPositionInfo.BarrierPosition> positions = new ArrayList<>();
    positions.add(BarrierPositionInfo.BarrierPosition.builder()
                      .stageSetupId("s1")
                      .stepSetupId("s2")
                      .strategySetupId("s3")
                      .allStrategySetupIds(new ArrayList<>(List.of("s1"))) // Ensure this is mutable too
                      .isDummyPosition(false)
                      .build());
    positions.add(BarrierPositionInfo.BarrierPosition.builder()
                      .isDummyPositionForChildPipeline(true)
                      .parentPipelineStageNodeId("stageNodeId")
                      .build());

    // Create a mutable BarrierPositionInfo
    BarrierPositionInfo positionInfo = BarrierPositionInfo.builder()
                                           .planExecutionId("executionId")
                                           .barrierPositionList(new ArrayList<>(positions)) // Ensure mutable copy
                                           .build();

    BarrierExecutionInstance barrierExecutionInstance =
        BarrierExecutionInstance.builder()
            .setupInfo(BarrierSetupInfo.builder().name("Barrier_1").identifier("Barrier_1").build())
            .name("Barrier_1")
            .barrierState(Barrier.State.STANDING)
            .identifier("Barrier_1")
            .planExecutionId("executionId")
            .positionInfo(positionInfo)
            .build();
    when(barrierService.findByIdentifierAndPlanExecutionId(any(), any())).thenReturn(barrierExecutionInstance);
    when(barrierService.atomicallyRemoveDummyBarrierPosition(anyString(), anyString(), anyString())).thenReturn(true);
    PlanCreationResponse response = barrierStepPlanCreator.createPlanForField(ctx, barrierStepNode);
    assertThat(response).isNotNull();
    mockSettings.close();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void createPlanForFieldShouldThrowWhenBarrierRefContainsExpression() throws IOException {
    String yamlField = "step:\n"
        + "  type: Barrier\n"
        + "  name: Barrier_1\n"
        + "  identifier: Barrier_1\n"
        + "  spec:\n"
        + "    barrierRef: <+pipeline.variables.barrierName>\n"
        + "  timeout: 10m";

    YamlField barrierStepYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    BarrierStepNode barrierStepNode =
        YamlUtils.read(barrierStepYamlField.getNode().getField("step").getNode().toString(), BarrierStepNode.class);

    Map<String, PlanCreationContextValue> contextMap = new HashMap<>();
    PlanExecutionContext planExecutionContext =
        PlanExecutionContext.newBuilder().setExecutionUuid("executionId").build();
    contextMap.put("metadata", PlanCreationContextValue.newBuilder().setExecutionContext(planExecutionContext).build());
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(contextMap)
                                  .currentField(barrierStepYamlField.getNode().getField("step"))
                                  .build();

    MockedStatic<PlanCreatorUtilsCommon> mockSettings = Mockito.mockStatic(PlanCreatorUtilsCommon.class, RETURNS_MOCKS);

    assertThatThrownBy(() -> barrierStepPlanCreator.createPlanForField(ctx, barrierStepNode))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Variable expressions are not allowed in Barrier Reference");

    mockSettings.close();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void createPlanForFieldShouldThrowWhenBarrierRefContainsStageExpression() throws IOException {
    String yamlField = "step:\n"
        + "  type: Barrier\n"
        + "  name: Barrier_1\n"
        + "  identifier: Barrier_1\n"
        + "  spec:\n"
        + "    barrierRef: <+stage.variables.myBarrier>\n"
        + "  timeout: 10m";

    YamlField barrierStepYamlField = YamlUtils.injectUuidInYamlField(yamlField);
    BarrierStepNode barrierStepNode =
        YamlUtils.read(barrierStepYamlField.getNode().getField("step").getNode().toString(), BarrierStepNode.class);

    Map<String, PlanCreationContextValue> contextMap = new HashMap<>();
    PlanExecutionContext planExecutionContext =
        PlanExecutionContext.newBuilder().setExecutionUuid("executionId").build();
    contextMap.put("metadata", PlanCreationContextValue.newBuilder().setExecutionContext(planExecutionContext).build());
    PlanCreationContext ctx = PlanCreationContext.builder()
                                  .globalContext(contextMap)
                                  .currentField(barrierStepYamlField.getNode().getField("step"))
                                  .build();

    MockedStatic<PlanCreatorUtilsCommon> mockSettings = Mockito.mockStatic(PlanCreatorUtilsCommon.class, RETURNS_MOCKS);

    assertThatThrownBy(() -> barrierStepPlanCreator.createPlanForField(ctx, barrierStepNode))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Variable expressions are not allowed in Barrier Reference");

    mockSettings.close();
  }
}
