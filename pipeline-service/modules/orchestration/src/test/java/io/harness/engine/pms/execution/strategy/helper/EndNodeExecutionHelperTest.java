/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.helper;

import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.pms.contracts.steps.StepCategory.STAGE;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.SHALINI;

import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.data.expression.PmsEngineExpressionService;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.engine.pms.execution.strategy.plannode.PlanNodeExecutionStrategy;
import io.harness.execution.NodeExecution;
import io.harness.expression.common.ExpressionMode;
import io.harness.plan.PlanNode;
import io.harness.plancreator.exports.ExportConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.steps.io.StepOutcomeProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class EndNodeExecutionHelperTest extends OrchestrationTestBase {
  @Mock private PmsOutcomeService pmsOutcomeService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanNodeExecutionStrategy executionStrategy;
  @Mock private PlanService planService;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;
  @InjectMocks @Spy EndNodeExecutionHelperImpl endNodeExecutionHelper;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testEndNodeExecutionWithNoAdvisers() {
    doReturn(emptyList()).when(endNodeExecutionHelper).handleOutcomes(any(Ambiance.class), any(), any(), isNull());
    doReturn(null).when(endNodeExecutionHelper).finalizeNodeWithStepResponse(any(Ambiance.class), any(), anyBoolean());
    endNodeExecutionHelper.endNodeExecutionWithNoAdvisers(
        Ambiance.newBuilder().build(), StepResponseProto.newBuilder().build(), null);
    verify(executionStrategy, times(0)).endNodeExecution(any(Ambiance.class), any(NodeExecution.class), any());
    doReturn(NodeExecution.builder().ambiance(Ambiance.newBuilder().build()).build())
        .when(endNodeExecutionHelper)
        .finalizeNodeWithStepResponse(any(Ambiance.class), any(), anyBoolean());
    doReturn(Ambiance.newBuilder().build()).when(nodeExecutionService).getAmbiance(any());
    endNodeExecutionHelper.endNodeExecutionWithNoAdvisers(
        Ambiance.newBuilder().build(), StepResponseProto.newBuilder().build(), null);
    verify(executionStrategy, times(1)).endNodeExecution(any(Ambiance.class), any(NodeExecution.class), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessStepResponseWithNoAdvisers() {
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .updateStatusWithOps(anyString(), any(Status.class), any(Consumer.class), any(EnumSet.class));
    assertThat(endNodeExecutionHelper.processStepResponseWithNoAdvisers(
                   Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("1").build()).build(),
                   StepResponseProto.newBuilder().setStatus(Status.valueOf("NO_OP")).build()))
        .isInstanceOf(NodeExecution.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleOutcomes() {
    // CurrentLevel is of pipeline(not stage or stepGroup) so exports will not be published.
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(UUIDGenerator.generateUuid())
            .addLevels(Level.newBuilder()
                           .setSetupId(UUIDGenerator.generateUuid())
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                           .build())
            .build();
    assertEquals(
        new ArrayList<>(), endNodeExecutionHelper.handleOutcomes(ambiance, new ArrayList<>(), new ArrayList<>(), null));
    StepOutcomeProto stepOutcomeProto =
        StepOutcomeProto.newBuilder().setOutcome("1").setName("proto").setGroup("group1").build();
    doReturn("id1").when(pmsOutcomeService).consume(any(Ambiance.class), eq("proto"), anyString(), anyString());
    List<StepOutcomeProto> stepOutcomeProtoList = new ArrayList<>();
    stepOutcomeProtoList.add(stepOutcomeProto);
    List<StepOutcomeRef> stepOutcomeRefs =
        endNodeExecutionHelper.handleOutcomes(ambiance, stepOutcomeProtoList, new ArrayList<>(), null);
    assertEquals(1, stepOutcomeRefs.size());
    assertEquals("id1", stepOutcomeRefs.get(0).getInstanceId());
    assertEquals("proto", stepOutcomeRefs.get(0).getName());

    // CurrentLevel is of stage but planNode does not have any exports so exports will not be published.
    ambiance = ambiance.toBuilder()
                   .addLevels(Level.newBuilder()
                                  .setSetupId(UUIDGenerator.generateUuid())
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                  .build())
                   .build();
    doReturn(PlanNode.builder().build())
        .when(planService)
        .fetchNode(ambiance.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambiance));
    stepOutcomeRefs = endNodeExecutionHelper.handleOutcomes(ambiance, stepOutcomeProtoList, new ArrayList<>(), null);
    assertEquals(1, stepOutcomeRefs.size());
    assertEquals("id1", stepOutcomeRefs.get(0).getInstanceId());
    assertEquals("proto", stepOutcomeRefs.get(0).getName());

    // Now the planNode has populated the exports so exports outcomes will be published.
    Map<String, ExportConfig> exportConfigMap =
        Map.of("export_1", ExportConfig.builder().value("<+some.expression>").desc("Description").build());
    doReturn(PlanNode.builder().exports(exportConfigMap).build())
        .when(planService)
        .fetchNode(ambiance.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambiance));
    doReturn(Map.of("export_1", ExportConfig.builder().value("resolved_expression_value").desc("Description").build()))
        .when(pmsEngineExpressionService)
        .resolve(ambiance, exportConfigMap, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

    doReturn("id2")
        .when(pmsOutcomeService)
        .consume(any(Ambiance.class), eq(YAMLFieldNameConstants.EXPORTS), anyString(), anyString());
    stepOutcomeRefs = endNodeExecutionHelper.handleOutcomes(ambiance, stepOutcomeProtoList, new ArrayList<>(), null);
    assertEquals(2, stepOutcomeRefs.size());
    assertEquals("id1", stepOutcomeRefs.get(0).getInstanceId());
    assertEquals("proto", stepOutcomeRefs.get(0).getName());

    assertEquals("id2", stepOutcomeRefs.get(1).getInstanceId());
    assertEquals(YAMLFieldNameConstants.EXPORTS, stepOutcomeRefs.get(1).getName());

    // if planNode is not null, the planNode fetch is skipped to optimize reads
    String planExecutionId = UUIDGenerator.generateUuid();
    Ambiance ambianceOptimized =
        Ambiance.newBuilder()
            .setPlanId(planExecutionId)
            .addLevels(Level.newBuilder()
                           .setSetupId(UUIDGenerator.generateUuid())
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build())
            .build();
    endNodeExecutionHelper.handleOutcomes(
        ambianceOptimized, stepOutcomeProtoList, new ArrayList<>(), PlanNode.builder().build());
    verify(planService, never())
        .fetchNode(ambianceOptimized.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambianceOptimized));

    // if planNode is not null, but PIPE_DISABLE_SKIP_FETCH_ON_END_NODE_EXECUTION is enabled, the planNode fetch is not
    // skipped
    ambianceOptimized =
        ambianceOptimized.toBuilder()
            .setPlanId(planExecutionId)
            .addLevels(Level.newBuilder()
                           .setSetupId(UUIDGenerator.generateUuid())
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build())
            .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                Map.of("PIPE_DISABLE_SKIP_FETCH_ON_END_NODE_EXECUTION", true)))
            .build();
    doReturn(PlanNode.builder().build())
        .when(planService)
        .fetchNode(ambianceOptimized.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambianceOptimized));
    endNodeExecutionHelper.handleOutcomes(
        ambianceOptimized, stepOutcomeProtoList, new ArrayList<>(), PlanNode.builder().build());
    verify(planService, times(1))
        .fetchNode(ambianceOptimized.getPlanId(), AmbianceUtils.obtainCurrentSetupId(ambianceOptimized));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleStepResponsePreAdviser() {
    doReturn(null)
        .when(endNodeExecutionHelper)
        .processStepResponsePreAdvisers(any(Ambiance.class), any(StepResponseProto.class), any());
    assertNull(endNodeExecutionHelper.handleStepResponsePreAdviser(
        Ambiance.newBuilder().build(), StepResponseProto.newBuilder().build(), null));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testProcessStepResponsePreAdvisers() {
    NodeExecution nodeExecution = NodeExecution.builder().build();
    doReturn(nodeExecution)
        .when(nodeExecutionService)
        .updateStatusWithOps(anyString(), any(Status.class), any(Consumer.class), any(EnumSet.class));
    assertThat(endNodeExecutionHelper.processStepResponseWithNoAdvisers(
                   Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId("1").build()).build(),
                   StepResponseProto.newBuilder().setStatus(Status.valueOf("NO_OP")).build()))
        .isInstanceOf(NodeExecution.class);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testDecorateFailureData() {
    Level stageLevel = Level.newBuilder()
                           .setGroup("STAGE")
                           .setStartTs(3)
                           .setIdentifier("stageIdentifier")
                           .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(STAGE).build())
                           .build();
    Level stepLevel =
        Level.newBuilder()
            .setGroup("SECTION")
            .setStartTs(4)
            .setIdentifier("stepIdentifier")
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STEP).build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(stageLevel).addLevels(stepLevel).build();
    String errorMessage = "Error: failed";
    assertThat(endNodeExecutionHelper.decorateFailureData(ambiance, errorMessage, null))
        .isEqualTo(FailureData.newBuilder()
                       .setMessage(errorMessage)
                       .setStepIdentifier("stepIdentifier")
                       .setStageIdentifier("stageIdentifier")
                       .build());
    FailureData failureData = FailureData.newBuilder()
                                  .setCode(GENERAL_ERROR.name())
                                  .setMessage(errorMessage)
                                  .setLevel(io.harness.eraro.Level.ERROR.name())
                                  .build();
    assertThat(endNodeExecutionHelper.decorateFailureData(ambiance, errorMessage, failureData))
        .isEqualTo(FailureData.newBuilder()
                       .setCode(GENERAL_ERROR.name())
                       .setMessage(errorMessage)
                       .setLevel(io.harness.eraro.Level.ERROR.name())
                       .setStepIdentifier("stepIdentifier")
                       .setStageIdentifier("stageIdentifier")
                       .build());
  }
}
