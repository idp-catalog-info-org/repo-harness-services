/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceVariableOverridesFunctorTest extends CategoryTest {
  @Mock private PmsEngineExpressionService engineExpressionService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetServiceVariableValueWhenNoStepGroupsExist() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getNormalStepLevels("stepRuntimeId", "stageRuntimeId", "stageSetupId"))
                            .build();

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    functor.get("var1");
    verify(engineExpressionService, atLeastOnce()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetServiceVariableValueWhenStepGroupDoesNotDefineVar() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getStepWithinTheStepGroup("stepRuntimeId", "stageRuntimeId", "stageSetupId"))
                            .build();

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    functor.get("var1");
    verify(engineExpressionService, atLeastOnce()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetStepGroupVariableValueWhenStepGroupDefinesVar() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getStepWithinTheStepGroup("stepRuntimeId", "stageRuntimeId", "stageSetupId"))
                            .build();
    when(engineExpressionService.renderExpression(
             any(), eq("<+pipeline.stages.stage1.execution.stepGroup1.variables.var1>"), any()))
        .thenReturn("fromStepGroup");

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    Object val = functor.get("var1");

    // get value from step group
    verify(engineExpressionService, atLeastOnce())
        .renderExpression(any(), eq("<+pipeline.stages.stage1.execution.stepGroup1.variables.var1>"), any());

    // not from service variable
    verify(engineExpressionService, never()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetServiceVariableValueWhenNotInExecutionContext() {
    // rendering inside stage context but outside execution context
    // functor will return null to return original expression with mode RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED
    Ambiance ambiance = Ambiance.newBuilder()
                            .addAllLevels(getNormalStageLevelsWithoutExecutionContext("stageRuntimeId", "stageSetupId"))
                            .build();

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    Object value = functor.get("var1");

    assertThat(value).isNull();
    verify(engineExpressionService, never()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testV1GetStepGroupVariableValueWhenStepGroupDefinesVar() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addAllLevels(getV1StepWithinStepGroup("stageRuntimeId", "stageSetupId"))
            .build();
    when(engineExpressionService.renderExpression(any(), eq("<+group.variables.var1>"), any()))
        .thenReturn("fromStepGroup");

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    Object val = functor.get("var1");

    assertThat(val).isEqualTo("fromStepGroup");
    // value comes from the step group, service variable is not consulted
    verify(engineExpressionService, atLeastOnce()).renderExpression(any(), eq("<+group.variables.var1>"), any());
    verify(engineExpressionService, never()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testV1FallbackToServiceVariableWhenStepGroupDoesNotDefineVar() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addAllLevels(getV1StepWithinStepGroup("stageRuntimeId", "stageSetupId"))
            .build();
    // step group does not resolve, service variable does
    when(engineExpressionService.renderExpression(any(), eq("<+serviceVariables.var1>"), any()))
        .thenReturn("fromService");

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    Object val = functor.get("var1");

    assertThat(val).isEqualTo("fromService");
    verify(engineExpressionService, atLeastOnce()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testV1NestedStepGroupInnermostWins() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addAllLevels(getV1StepWithinNestedStepGroups("stageRuntimeId", "stageSetupId"))
            .build();
    when(engineExpressionService.renderExpression(any(), eq("<+group.variables.var1>"), any())).thenReturn("fromInner");

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    Object val = functor.get("var1");

    assertThat(val).isEqualTo("fromInner");
    verify(engineExpressionService, atLeastOnce()).renderExpression(any(), eq("<+group.variables.var1>"), any());
    // outer step group and service variable are not consulted once the inner one resolves
    verify(engineExpressionService, never())
        .renderExpression(any(), eq("<+group.getParentStepGroup.variables.var1>"), any());
    verify(engineExpressionService, never()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testV1NestedStepGroupOuterWinsWhenInnerDoesNotDefineVar() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addAllLevels(getV1StepWithinNestedStepGroups("stageRuntimeId", "stageSetupId"))
            .build();
    // inner step group does not define the var, outer one does (reached via getParentStepGroup)
    when(engineExpressionService.renderExpression(any(), eq("<+group.getParentStepGroup.variables.var1>"), any()))
        .thenReturn("fromOuter");

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    Object val = functor.get("var1");

    assertThat(val).isEqualTo("fromOuter");
    verify(engineExpressionService, atLeastOnce())
        .renderExpression(any(), eq("<+group.getParentStepGroup.variables.var1>"), any());
    verify(engineExpressionService, never()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testV1ReturnsNullWhenNotInsideStage() {
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
            .addAllLevels(getV1LevelsWithoutStage())
            .build();

    ServiceVariableOverridesFunctor functor = new ServiceVariableOverridesFunctor(ambiance, engineExpressionService);
    Object val = functor.get("var1");

    assertThat(val).isNull();
    verify(engineExpressionService, never()).renderExpression(any(), eq("<+serviceVariables.var1>"), any());
  }

  private List<Level> getNormalStageLevels(String stageRuntimeId, String stageSetupId) {
    List<Level> levelList = new LinkedList<>();
    levelList.add(
        Level.newBuilder()
            .setIdentifier("pipeline")
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).setType("pipeline").build())
            .build());
    levelList.add(Level.newBuilder()
                      .setIdentifier("stages")
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).setType("stages").build())
                      .build());
    levelList.add(Level.newBuilder()
                      .setIdentifier("stage1")
                      .setRuntimeId(stageRuntimeId)
                      .setSetupId(stageSetupId)
                      .setGroup("stage")
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
                      .build());
    levelList.add(Level.newBuilder()
                      .setIdentifier("execution")
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setGroup("EXECUTION")
                      .setStepType(StepType.newBuilder()
                                       .setStepCategory(StepCategory.STEP)
                                       .setType("NG_SECTION_WITH_ROLLBACK_INFO")
                                       .build())
                      .build());
    return levelList;
  }

  private List<Level> getNormalStageLevelsWithoutExecutionContext(String stageRuntimeId, String stageSetupId) {
    List<Level> levelList = new LinkedList<>();
    levelList.add(
        Level.newBuilder()
            .setIdentifier("pipeline")
            .setRuntimeId(UUIDGenerator.generateUuid())
            .setSetupId(UUIDGenerator.generateUuid())
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).setType("pipeline").build())
            .build());
    levelList.add(Level.newBuilder()
                      .setIdentifier("stages")
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).setType("stages").build())
                      .build());
    levelList.add(Level.newBuilder()
                      .setIdentifier("stage1")
                      .setRuntimeId(stageRuntimeId)
                      .setSetupId(stageSetupId)
                      .setGroup("stage")
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
                      .build());

    return levelList;
  }

  private List<Level> getNormalStepLevels(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> levels = getNormalStageLevels(stageRuntimeId, stageSetupId);
    levels.add(Level.newBuilder()
                   .setRuntimeId(stepRuntimeId)
                   .setIdentifier("shell1")
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setGroup("step")
                   .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
                   .build());
    return levels;
  }

  private List<Level> getStepWithinTheStepGroup(String stepRuntimeId, String stageRuntimeId, String stageSetupId) {
    List<Level> levels = getNormalStageLevels(stageRuntimeId, stageSetupId);

    levels.add(
        Level.newBuilder()
            .setRuntimeId("stepGroupRuntimeId")
            .setIdentifier("stepGroup1")
            .setSetupId(UUIDGenerator.generateUuid())
            .setGroup("step_group")
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP_GROUP).setType("STEP_GROUP").build())
            .build());

    levels.add(Level.newBuilder()
                   .setRuntimeId(stepRuntimeId)
                   .setIdentifier("shell1")
                   .setSetupId(UUIDGenerator.generateUuid())
                   .setGroup("step")
                   .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
                   .build());
    return levels;
  }

  private List<Level> getV1StageLevels(String stageRuntimeId, String stageSetupId) {
    List<Level> levelList = new LinkedList<>();
    levelList.add(Level.newBuilder()
                      .setIdentifier("stages")
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).setType("stages").build())
                      .build());
    levelList.add(Level.newBuilder()
                      .setIdentifier("stage1")
                      .setRuntimeId(stageRuntimeId)
                      .setSetupId(stageSetupId)
                      .setGroup("STAGE")
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("stage1").build())
                      .build());
    return levelList;
  }

  private Level getV1StepsContainerLevel() {
    return Level.newBuilder()
        .setIdentifier("steps")
        .setRuntimeId(UUIDGenerator.generateUuid())
        .setSetupId(UUIDGenerator.generateUuid())
        .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("NG_SECTION").build())
        .build();
  }

  private Level getV1GroupLevel(String identifier) {
    return Level.newBuilder()
        .setIdentifier(identifier)
        .setRuntimeId(UUIDGenerator.generateUuid())
        .setSetupId(UUIDGenerator.generateUuid())
        .setGroup("GROUP")
        .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("GROUP").build())
        .build();
  }

  private Level getV1StepLeaf() {
    return Level.newBuilder()
        .setIdentifier("shell1")
        .setRuntimeId(UUIDGenerator.generateUuid())
        .setSetupId(UUIDGenerator.generateUuid())
        .setGroup("step")
        .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("shellscript").build())
        .build();
  }

  private List<Level> getV1StepWithinStepGroup(String stageRuntimeId, String stageSetupId) {
    List<Level> levels = getV1StageLevels(stageRuntimeId, stageSetupId);
    levels.add(getV1StepsContainerLevel());
    levels.add(getV1GroupLevel("outerGroup"));
    levels.add(getV1StepsContainerLevel());
    levels.add(getV1StepLeaf());
    return levels;
  }

  private List<Level> getV1StepWithinNestedStepGroups(String stageRuntimeId, String stageSetupId) {
    List<Level> levels = getV1StageLevels(stageRuntimeId, stageSetupId);
    levels.add(getV1StepsContainerLevel());
    levels.add(getV1GroupLevel("outerGroup"));
    levels.add(getV1StepsContainerLevel());
    levels.add(getV1GroupLevel("innerGroup"));
    levels.add(getV1StepsContainerLevel());
    levels.add(getV1StepLeaf());
    return levels;
  }

  private List<Level> getV1LevelsWithoutStage() {
    List<Level> levelList = new LinkedList<>();
    levelList.add(Level.newBuilder()
                      .setIdentifier("stages")
                      .setRuntimeId(UUIDGenerator.generateUuid())
                      .setSetupId(UUIDGenerator.generateUuid())
                      .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).setType("stages").build())
                      .build());
    return levelList;
  }
}
