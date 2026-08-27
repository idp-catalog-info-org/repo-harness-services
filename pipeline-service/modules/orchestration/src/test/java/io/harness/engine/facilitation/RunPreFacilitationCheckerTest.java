/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.yaml.YAMLFieldNameConstants.HARNESS_HIDE_WHEN_SKIPPED;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executioncheck.PreFacilitationExecutionCheck;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.VariableResolverTracker;
import io.harness.plan.PlanNode;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.run.ExpressionBlock;
import io.harness.pms.contracts.execution.run.NodeRunInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PIPELINE)
public class RunPreFacilitationCheckerTest extends OrchestrationTestBase {
  @Mock PmsEngineExpressionService pmsEngineExpressionService;
  @Mock EngineExpressionEvaluator engineExpressionEvaluator;
  @Mock VariableResolverTracker variableResolverTracker;
  @Mock OrchestrationEngine engine;
  @Mock NodeExecutionService nodeExecutionService;
  @Inject MongoTemplate mongoTemplate;
  @Inject @InjectMocks RunPreFacilitationChecker checker;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void performCheckWhenConditionTrue() {
    String whenCondition = "<+pipeline.name>==\"name\"";
    String nodeExecutionId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .whenCondition(whenCondition)
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder().build())
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .build();
    mongoTemplate.save(nodeExecution);

    when(engineExpressionEvaluator.getVariableResolverTracker()).thenReturn(variableResolverTracker);
    when(variableResolverTracker.getUsage()).thenReturn(new HashMap<>());
    when(pmsEngineExpressionService.prepareExpressionEvaluator(nodeExecution.getAmbiance()))
        .thenReturn(engineExpressionEvaluator);
    when(engineExpressionEvaluator.evaluateExpression(whenCondition)).thenReturn(true);
    PreFacilitationExecutionCheck check = checker.performCheck(ambiance, planNode);
    assertThat(check).isNotNull();
    assertThat(check.isProceed()).isTrue();
    assertThat(check.getUpdates()).hasSize(1);
    assertThat(check.getUpdates().get(NodeExecutionKeys.nodeRunInfo))
        .isEqualTo(NodeRunInfo.newBuilder().setWhenCondition(whenCondition).setEvaluatedCondition(true).build());
    verify(engine, times(0)).processStepResponse(any(), any());
    verify(nodeExecutionService, times(0)).updateV2(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void performCheckWhenConditionTrueMergeUpdatesFFEnabled() {
    String whenCondition = "<+pipeline.name>==\"name\"";
    String nodeExecutionId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .whenCondition(whenCondition)
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .build();
    mongoTemplate.save(nodeExecution);

    when(engineExpressionEvaluator.getVariableResolverTracker()).thenReturn(variableResolverTracker);
    when(variableResolverTracker.getUsage()).thenReturn(new HashMap<>());
    when(pmsEngineExpressionService.prepareExpressionEvaluator(nodeExecution.getAmbiance()))
        .thenReturn(engineExpressionEvaluator);
    when(engineExpressionEvaluator.evaluateExpression(whenCondition)).thenReturn(true);
    PreFacilitationExecutionCheck check = checker.performCheck(ambiance, planNode);
    assertThat(check).isNotNull();
    assertThat(check.isProceed()).isTrue();
    assertThat(check.getUpdates()).hasSize(1);
    assertThat(check.getUpdates().get(NodeExecutionKeys.nodeRunInfo))
        .isEqualTo(NodeRunInfo.newBuilder().setWhenCondition(whenCondition).setEvaluatedCondition(true).build());
    verify(engine, times(0)).processStepResponse(any(), any());
    verify(nodeExecutionService, times(0)).updateV2(any(), any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void performCheckWhenConditionFalse() {
    String whenCondition = "<+pipeline.name>==\"name\"";
    String nodeExecutionId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .whenCondition(whenCondition)
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder().build())
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .build();
    mongoTemplate.save(nodeExecution);

    when(engineExpressionEvaluator.getVariableResolverTracker()).thenReturn(variableResolverTracker);
    when(variableResolverTracker.getUsage()).thenReturn(new HashMap<>());
    when(pmsEngineExpressionService.prepareExpressionEvaluator(nodeExecution.getAmbiance()))
        .thenReturn(engineExpressionEvaluator);
    when(engineExpressionEvaluator.evaluateExpression(whenCondition)).thenReturn(false);
    PreFacilitationExecutionCheck check = checker.performCheck(ambiance, planNode);
    assertThat(check).isNotNull();
    assertThat(check.isProceed()).isFalse();
    assertThat(check.getUpdates()).hasSize(1);
    assertThat(check.getUpdates().get(NodeExecutionKeys.nodeRunInfo))
        .isEqualTo(NodeRunInfo.newBuilder().setWhenCondition(whenCondition).setEvaluatedCondition(false).build());
    verify(nodeExecutionService, times(0)).updateV2(any(), any());
    verify(engine, times(1))
        .processStepResponse(ambiance,
            StepResponseProto.newBuilder()
                .setStatus(Status.SKIPPED)
                .setNodeRunInfo(
                    NodeRunInfo.newBuilder().setWhenCondition(whenCondition).setEvaluatedCondition(false).build())
                .build());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void performCheckWhenConditionException() {
    String whenCondition = "<+pipeline.name>==\"name\"";
    String nodeExecutionId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .whenCondition(whenCondition)
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder().build())
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .build();
    mongoTemplate.save(nodeExecution);

    when(engineExpressionEvaluator.getVariableResolverTracker()).thenReturn(variableResolverTracker);
    when(variableResolverTracker.getUsage()).thenReturn(new HashMap<>());
    when(pmsEngineExpressionService.prepareExpressionEvaluator(nodeExecution.getAmbiance()))
        .thenReturn(engineExpressionEvaluator);
    InvalidRequestException testException = new InvalidRequestException("TestException");
    when(engineExpressionEvaluator.evaluateExpression(whenCondition)).thenThrow(testException);
    PreFacilitationExecutionCheck check = checker.performCheck(ambiance, planNode);
    assertThat(check).isNotNull();
    assertThat(check.isProceed()).isFalse();
    assertThat(check.getUpdates()).isNull();
    verify(engine, times(1)).handleError(nodeExecution.getAmbiance(), testException);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testGetAllExpressions() {
    Map<String, Map<Object, Integer>> usages = new HashMap<>();
    usages.put("stage.name", ImmutableMap.of("stage1", 1));
    usages.put("pipeline.name", ImmutableMap.of("pipeline", 1));
    usages.put("onPipelineStatus", ImmutableMap.of(Boolean.TRUE, 1));
    usages.put("pipeline.currentStatus", ImmutableMap.of("SUCCESS", 1));

    when(engineExpressionEvaluator.getVariableResolverTracker()).thenReturn(variableResolverTracker);
    when(variableResolverTracker.getUsage()).thenReturn(usages);
    List<ExpressionBlock> allExpressions = checker.getAllExpressions(engineExpressionEvaluator);
    assertThat(allExpressions).isNotNull();
    assertThat(allExpressions.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetAllExpressions_1() {
    Map<String, Map<Object, Integer>> usages = new HashMap<>();
    usages.put("stage.name", ImmutableMap.of("stage1", 1));
    usages.put("pipeline.currentStatus", ImmutableMap.of("SUCCESS", 1));
    usages.put("OnPipelineSuccess", ImmutableMap.of("true", 1));

    when(engineExpressionEvaluator.getVariableResolverTracker()).thenReturn(variableResolverTracker);
    when(variableResolverTracker.getUsage()).thenReturn(usages);
    List<ExpressionBlock> allExpressions = checker.getAllExpressions(engineExpressionEvaluator);
    assertThat(allExpressions).isNotNull();
    assertThat(allExpressions.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testPerformPreFacilitationChecksWithMaximumLevelsDepth() {
    List<Level> levelList = new ArrayList<>();
    String setupId = generateUuid();
    String runtimeId = generateUuid();
    for (int i = 0; i < 25; i++) {
      levelList.add(Level.newBuilder().setSetupId(setupId).setRuntimeId(runtimeId).build());
    }
    String planId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanId(planId)
                            .setMetadata(io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder().build())
                            .addAllLevels(levelList)
                            .build();
    PlanNode planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    PreFacilitationExecutionCheck executionCheck = checker.performCheck(ambiance, planNode);
    assertTrue(executionCheck.isProceed());

    levelList.add(Level.newBuilder().setSetupId(setupId).setRuntimeId(runtimeId).build());
    ambiance = Ambiance.newBuilder()
                   .setMetadata(io.harness.pms.contracts.plan.ExecutionMetadata.newBuilder().build())
                   .setPlanId(planId)
                   .addAllLevels(levelList)
                   .build();
    planNode = PlanNode.builder().adviserObtainment(AdviserObtainment.newBuilder().build()).build();
    executionCheck = checker.performCheck(ambiance, planNode);
    assertFalse(executionCheck.isProceed());
    assertEquals(executionCheck.getReason(),
        "The pipeline has reached the maximum nesting allowed for an execution. Please simplify the pipeline "
            + "configuration so that it does not breach the allowed limit of nesting");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testShouldHideFromGraph_WithHarnessHideEnvVarTrue_ReturnsTrue() {
    // Create V1 Run step with HARNESS_HIDE_WHEN_SKIPPED=true
    Map<String, JsonNode> envMap = new HashMap<>();
    envMap.put(HARNESS_HIDE_WHEN_SKIPPED, TextNode.valueOf("true"));

    RunStepInfoV1 runStepInfoV1 = RunStepInfoV1.builder()
                                      .env(ParameterField.createValueField(envMap))
                                      .script(ParameterField.createValueField("echo hello"))
                                      .build();

    StepElementParametersV1 stepElementParametersV1 =
        StepElementParametersV1.builder().id("run_1").name("run_1").type("run").spec(runStepInfoV1).build();

    StepType runV1StepType =
        StepType.newBuilder().setType(CIStepInfoType.RUN.getDisplayName()).setStepCategory(StepCategory.STEP).build();

    PlanNode planNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("run_1")
            .stepType(runV1StepType)
            .identifier("run_1")
            .serviceName("CI")
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toMap(stepElementParametersV1)))
            .build();

    assertTrue(checker.shouldHideFromGraph(planNode));
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testShouldHideFromGraph_WithoutHarnessHideEnvVar_ReturnsFalse() {
    // Create V1 Run step WITHOUT HARNESS_HIDE_WHEN_SKIPPED env var
    Map<String, JsonNode> envMap = new HashMap<>();
    envMap.put("OTHER_VAR", TextNode.valueOf("value"));
    RunStepInfoV1 runStepInfoV1 = RunStepInfoV1.builder()
                                      .env(ParameterField.createValueField(envMap))
                                      .script(ParameterField.createValueField("echo hello"))
                                      .build();

    StepElementParametersV1 stepElementParametersV1 =
        StepElementParametersV1.builder().id("run_1").name("run_1").type("run").spec(runStepInfoV1).build();

    StepType runV1StepType =
        StepType.newBuilder().setType(CIStepInfoType.RUN.getDisplayName()).setStepCategory(StepCategory.STEP).build();

    PlanNode planNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .name("run_1")
            .stepType(runV1StepType)
            .identifier("run_1")
            .serviceName("CI")
            .stepParameters(PmsStepParameters.parse(RecastOrchestrationUtils.toMap(stepElementParametersV1)))
            .build();

    assertFalse(checker.shouldHideFromGraph(planNode));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testConvertToBoolean_WithBooleanTrue() {
    assertTrue(checker.convertToBoolean(Boolean.TRUE, "test condition"));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testConvertToBoolean_WithBooleanFalse() {
    assertFalse(checker.convertToBoolean(Boolean.FALSE, "test condition"));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testConvertToBoolean_WithStringTrue() {
    assertTrue(checker.convertToBoolean("true", "test condition"));
    assertTrue(checker.convertToBoolean("TRUE", "test condition"));
    assertTrue(checker.convertToBoolean(" true ", "test condition"));
    assertTrue(checker.convertToBoolean(" false ", "test condition"));
    assertTrue(checker.convertToBoolean("nonboolean", "test condition"));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testConvertToBoolean_WithStringFalse() {
    assertFalse(checker.convertToBoolean("false", "test condition"));
    assertFalse(checker.convertToBoolean("FALSE", "test condition"));
    assertFalse(checker.convertToBoolean("", "test condition"));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testConvertToBoolean_WithNull() {
    // Null should throw NullPointerException when unboxing (preserving old behavior)
    try {
      checker.convertToBoolean(null, "test condition");
      fail("Expected NullPointerException for null value");
    } catch (NullPointerException e) {
      // Expected behavior - null cannot be unboxed to primitive boolean
    }
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testConvertToBoolean_WithUnexpectedType() {
    // Unexpected types should throw ClassCastException (preserving old behavior)
    try {
      checker.convertToBoolean(123, "test condition");
      fail("Expected ClassCastException for Integer type");
    } catch (ClassCastException e) {
      // Expected behavior - Integer cannot be cast to Boolean
      assertThat(e.getMessage()).contains("Integer");
    }

    try {
      checker.convertToBoolean(new Object(), "test condition");
      fail("Expected ClassCastException for Object type");
    } catch (ClassCastException e) {
      // Expected behavior - Object cannot be cast to Boolean
      assertThat(e.getMessage()).contains("Object");
    }
  }
}
