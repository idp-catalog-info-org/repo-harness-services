/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delay.DelayEventHelper;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.execution.ExecutionInputService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.pms.resume.callback.waitretry.v2.EngineWaitRetryCallbackV2;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.NodeExecution;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.PlanNode;
import io.harness.plancreator.constants.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.ForMetadata;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.pms.contracts.interrupts.RetryInterruptConfig;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class RetryHelperTest extends OrchestrationTestBase {
  @Mock OrchestrationEngine engine;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanService planService;
  @Mock ExecutorService executorService;
  @Mock ExecutionInputService executionInputService;
  @Mock private DelayEventHelper delayEventHelper;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private NodeExecutionInfoService pmsGraphStepDetailsService;
  @Spy @Inject @InjectMocks RetryHelper retryHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void verifyMocks() {
    Mockito.verifyNoMoreInteractions(engine);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRetryNodeExecution() {
    String nodeExecutionId = generateUuid();
    String nodeId = generateUuid();
    String planId = generateUuid();
    String interruptId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeId)
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .setPlanId(planId)
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                            .setMetadata(ExecutionMetadata.newBuilder().putAllFeatureFlagToValueMap(
                                Map.of("PIPE_DISABLE_NODE_EXECUTION_INFO_UPSERT_OPTIMIZATION", true)))
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .nodeId(nodeId)
            .identifier("DUMMY")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .build();

    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.save(any())).thenReturn(nodeExecution);
    when(nodeExecutionService.updateRelationShipsForRetryNode(any(), any())).thenReturn(true);
    when(nodeExecutionService.markRetried(any())).thenReturn(true);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);

    when(planService.fetchNode(planId, nodeId)).thenReturn(planNode);

    doReturn(ExecutionInputInstance.builder().build())
        .when(executionInputService)
        .getExecutionInputInstance(nodeExecutionId);
    retryHelper.retryNodeExecution(nodeExecution.getUuid(), interruptId, interruptConfig);
    verify(pmsGraphStepDetailsService)
        .saveNodeExecutionInfo(any(String.class), eq(ambiance.getPlanExecutionId()), eq(null), eq("accountId"));
    verify(executorService).submit(any(Runnable.class));
  }
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestCloneForRetry() {
    String nodeId = generateUuid();
    String nodeExecutionId = generateUuid();

    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeId)
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").build())
                            .serviceName("DUMMY")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .nodeId(nodeId)
            .identifier("DUMMY")
            .name("name")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .stepType(StepType.newBuilder().setType("DUMMY").build())
            .module("DUMMY")
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .startTs(System.currentTimeMillis())
            .build();
    String newNodeUuid = generateUuid();
    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .build();
    doReturn(ExecutionInputInstance.builder().build())
        .when(executionInputService)
        .getExecutionInputInstance(nodeExecutionId);
    NodeExecution clonedNodeExecution = retryHelper.cloneForRetry(
        nodeExecution, newNodeUuid, nodeExecution.getAmbiance(), interruptConfig, generateUuid());

    assertThat(clonedNodeExecution).isNotNull();
    assertThat(clonedNodeExecution.getUuid()).isEqualTo(newNodeUuid);
    assertThat(clonedNodeExecution.getRetryIds()).containsExactly(nodeExecution.getUuid());
    assertThat(clonedNodeExecution.getInterruptHistories()).hasSize(1);
    assertThat(clonedNodeExecution.getInterruptHistories().get(0).getInterruptType()).isEqualTo(InterruptType.RETRY);
    assertThat(clonedNodeExecution.getStartTs()).isNull();
    assertThat(clonedNodeExecution.getEndTs()).isNull();
    assertThat(clonedNodeExecution.getStatus()).isEqualTo(Status.QUEUED);
    assertThat(clonedNodeExecution.getName()).isEqualTo("name");
    assertThat(clonedNodeExecution.getIdentifier()).isEqualTo("DUMMY");
    assertThat(clonedNodeExecution.getExecutionContext()).isNotNull();
    assertThat(clonedNodeExecution.getExecutionContext().getPlanExecutionId()).isEqualTo(ambiance.getPlanExecutionId());
    assertThat(clonedNodeExecution.getExecutionContext().getLevelsList()).isEqualTo(ambiance.getLevelsList());
    assertThat(clonedNodeExecution.getAmbiance()).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCloneInputInstanceForRetry() {
    String originalNodeExecutionId = "originalNodeExecutionId";
    String newNodeExecutionId = "newNodeExecutionId";
    Map<String, Object> mergedTemplateMap = ImmutableMap.of("key", "val");
    ExecutionInputInstance executionInputInstance = ExecutionInputInstance.builder()
                                                        .nodeExecutionId(originalNodeExecutionId)
                                                        .template("template")
                                                        .userInput("userinputyaml")
                                                        .mergedInputTemplate(mergedTemplateMap)
                                                        .build();
    doReturn(executionInputInstance).when(executionInputService).getExecutionInputInstance(originalNodeExecutionId);

    doReturn(ExecutionInputInstance.builder()
                 .userInput(executionInputInstance.getUserInput())
                 .template(executionInputInstance.getTemplate())
                 .mergedInputTemplate(executionInputInstance.getMergedInputTemplate())
                 .nodeExecutionId(newNodeExecutionId)
                 .inputInstanceId("RandomUUID")
                 .build())
        .when(executionInputService)
        .save(any());
    ExecutionInputInstance clonedInstance =
        retryHelper.cloneAndSaveInputInstanceForRetry(originalNodeExecutionId, newNodeExecutionId);

    assertThat(executionInputInstance.getInputInstanceId()).isNotEqualTo(clonedInstance.getInputInstanceId());
    assertThat(clonedInstance.getNodeExecutionId()).isEqualTo(newNodeExecutionId);
    assertThat(clonedInstance.getMergedInputTemplate()).isEqualTo(executionInputInstance.getMergedInputTemplate());
    assertThat(clonedInstance.getTemplate()).isEqualTo(executionInputInstance.getTemplate());
    assertThat(clonedInstance.getUserInput()).isEqualTo(executionInputInstance.getUserInput());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testFinalAmbianceShouldHaveStrategyMetadata() {
    String nodeExecutionId = generateUuid();
    String nodeId = generateUuid();
    String planId = generateUuid();
    String interruptId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeId)
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .serviceName("CD")
                            .build();
    StrategyMetadata strategyMetadata = StrategyMetadata.newBuilder()
                                            .setForMetadata(ForMetadata.newBuilder().setValue("hostName").build())
                                            .setCurrentIteration(1)
                                            .setTotalIterations(1)
                                            .build();
    Level level = PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode, strategyMetadata, true);
    Ambiance ambiance =
        Ambiance.newBuilder().setPlanExecutionId(generateUuid()).setPlanId(planId).addLevels(level).build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .nodeId(nodeId)
            .identifier("DUMMY")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .build();

    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.save(any())).thenReturn(nodeExecution);
    when(nodeExecutionService.updateRelationShipsForRetryNode(any(), any())).thenReturn(true);
    when(nodeExecutionService.markRetried(any())).thenReturn(true);
    when(planService.fetchNode(planId, nodeId)).thenReturn(planNode);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);
    when(pmsGraphStepDetailsService.getStrategyMetadata(level)).thenReturn(strategyMetadata);

    doReturn(ExecutionInputInstance.builder().build())
        .when(executionInputService)
        .getExecutionInputInstance(nodeExecutionId);
    retryHelper.retryNodeExecution(nodeExecution.getUuid(), interruptId, interruptConfig);
    ambiance.getLevels(0).toBuilder().setRetryIndex(1);
    ArgumentCaptor<Ambiance> captor = ArgumentCaptor.forClass(Ambiance.class);
    verify(retryHelper).cloneForRetry(any(), anyString(), captor.capture(), any(), anyString());
    assertEquals(captor.getValue().getLevels(0).getStrategyMetadata(), strategyMetadata);
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testShouldWaitBeforeRetry() {
    String nodeExecutionId = generateUuid();
    String nodeId = generateUuid();
    String planId = generateUuid();
    String interruptId = generateUuid();
    String resumeId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeId)
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .serviceName("CD")
                            .build();
    StrategyMetadata strategyMetadata = StrategyMetadata.newBuilder()
                                            .setForMetadata(ForMetadata.newBuilder().setValue("hostName").build())
                                            .setCurrentIteration(1)
                                            .setTotalIterations(1)
                                            .build();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(generateUuid())
            .setPlanId(planId)
            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode, strategyMetadata, true))
            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .nodeId(nodeId)
            .identifier("DUMMY")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .build();
    Level level = PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode, strategyMetadata, true)
                      .toBuilder()
                      .setRetryIndex(1)
                      .build();
    Ambiance newAmbiance =
        Ambiance.newBuilder().setPlanExecutionId(generateUuid()).setPlanId(planId).addLevels(level).build();
    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .setRetryInterruptConfig(RetryInterruptConfig.newBuilder().setWaitInterval(100L).build())
            .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.save(any())).thenReturn(nodeExecution);
    when(nodeExecutionService.updateRelationShipsForRetryNode(any(), any())).thenReturn(true);
    when(nodeExecutionService.markRetried(any())).thenReturn(true);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(newAmbiance);
    when(pmsGraphStepDetailsService.getStrategyMetadata(level)).thenReturn(strategyMetadata);

    when(planService.fetchNode(planId, nodeId)).thenReturn(planNode);
    doReturn(resumeId).when(delayEventHelper).delay(eq(100L), any());

    doReturn(ExecutionInputInstance.builder().build())
        .when(executionInputService)
        .getExecutionInputInstance(nodeExecutionId);
    retryHelper.retryNodeExecution(nodeExecution.getUuid(), interruptId, interruptConfig);
    ambiance.getLevels(0).toBuilder().setRetryIndex(1);
    ArgumentCaptor<Ambiance> captor = ArgumentCaptor.forClass(Ambiance.class);
    verify(retryHelper).cloneForRetry(any(), anyString(), captor.capture(), any(), anyString());
    assertEquals(captor.getValue().getLevels(0).getStrategyMetadata(), strategyMetadata);

    verify(delayEventHelper).delay(eq(100L), any());

    ArgumentCaptor<EngineWaitRetryCallbackV2> argumentCaptor = ArgumentCaptor.forClass(EngineWaitRetryCallbackV2.class);
    verify(waitNotifyEngine).waitForAllOn(any(), argumentCaptor.capture(), eq(resumeId));

    EngineWaitRetryCallbackV2 cb = argumentCaptor.getValue();
    assertThat(cb.getAmbiance().getLevels(0).getRetryIndex()).isEqualTo(1);
    assertThat(ambiance.getLevels(0).getRetryIndex()).isEqualTo(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRetryNodeExecution_WithStepGroupAndFFEnabled_ShouldMarkChildrenRetried() {
    String nodeExecutionId = generateUuid();
    String nodeId = generateUuid();
    String planId = generateUuid();
    String interruptId = generateUuid();
    PlanNode planNode =
        PlanNode.builder()
            .uuid(nodeId)
            .identifier("STEP_GROUP_NODE")
            .stepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP).build())
            .serviceName("CD")
            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .setPlanId(planId)
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .nodeId(nodeId)
            .identifier("STEP_GROUP_NODE")
            .stepType(StepType.newBuilder().setType("STEP_GROUP").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group(AmbianceUtils.STEP_GROUP)
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .build();

    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.save(any())).thenReturn(nodeExecution);
    when(nodeExecutionService.updateRelationShipsForRetryNode(any(), any())).thenReturn(true);
    when(nodeExecutionService.markCurrentNodeExecutionAndChildrenRetried(any(), any())).thenReturn(true);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);
    when(planService.fetchNode(planId, nodeId)).thenReturn(planNode);

    doReturn(ExecutionInputInstance.builder().build())
        .when(executionInputService)
        .getExecutionInputInstance(nodeExecutionId);
    retryHelper.retryNodeExecution(nodeExecution.getUuid(), interruptId, interruptConfig);

    verify(nodeExecutionService, times(1))
        .markCurrentNodeExecutionAndChildrenRetried(nodeExecutionId, ambiance.getPlanExecutionId());
    verify(nodeExecutionService, never()).markRetried(any());
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRetryNodeExecution_WithGroupConstant_ShouldMarkChildrenRetried() {
    String nodeExecutionId = generateUuid();
    String nodeId = generateUuid();
    String planId = generateUuid();
    String interruptId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeId)
                            .identifier("GROUP_NODE")
                            .stepType(StepType.newBuilder().setType("GROUP").setStepCategory(StepCategory.STEP).build())
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .setPlanId(planId)
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .nodeId(nodeId)
            .identifier("GROUP_NODE")
            .stepType(StepType.newBuilder().setType("GROUP").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group(NGCommonUtilPlanCreationConstants.GROUP)
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .build();

    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.save(any())).thenReturn(nodeExecution);
    when(nodeExecutionService.updateRelationShipsForRetryNode(any(), any())).thenReturn(true);
    when(nodeExecutionService.markCurrentNodeExecutionAndChildrenRetried(any(), any())).thenReturn(true);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);
    when(planService.fetchNode(planId, nodeId)).thenReturn(planNode);

    doReturn(ExecutionInputInstance.builder().build())
        .when(executionInputService)
        .getExecutionInputInstance(nodeExecutionId);
    retryHelper.retryNodeExecution(nodeExecution.getUuid(), interruptId, interruptConfig);

    verify(nodeExecutionService, times(1))
        .markCurrentNodeExecutionAndChildrenRetried(nodeExecutionId, ambiance.getPlanExecutionId());
    verify(nodeExecutionService, never()).markRetried(any());
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testRetryNodeExecution_WithRegularStepAndFFDisabled_ShouldCallMarkRetried() {
    String nodeExecutionId = generateUuid();
    String nodeId = generateUuid();
    String planId = generateUuid();
    String interruptId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeId)
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .setPlanId(planId)
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .nodeId(nodeId)
            .identifier("DUMMY")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .build();

    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.save(any())).thenReturn(nodeExecution);
    when(nodeExecutionService.updateRelationShipsForRetryNode(any(), any())).thenReturn(true);
    when(nodeExecutionService.markRetried(any())).thenReturn(true);
    when(nodeExecutionService.getAmbiance(any())).thenReturn(ambiance);
    when(planService.fetchNode(planId, nodeId)).thenReturn(planNode);

    doReturn(ExecutionInputInstance.builder().build())
        .when(executionInputService)
        .getExecutionInputInstance(nodeExecutionId);
    retryHelper.retryNodeExecution(nodeExecution.getUuid(), interruptId, interruptConfig);

    verify(nodeExecutionService, times(1)).markRetried(nodeExecutionId);
    verify(nodeExecutionService, never()).markCurrentNodeExecutionAndChildrenRetried(any(), any());
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testRetryNodeExecution_WithCacheStatusFF_ShouldClearFirstUnsuccessfulRuntimeIdChain() {
    String nodeExecutionId = generateUuid();
    String nodeId = generateUuid();
    String planId = generateUuid();
    String interruptId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeId)
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .serviceName("CD")
                            .build();

    // Build levels with stage
    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setSetupId(generateUuid())
                           .setGroup("STAGE")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build();

    Level stepLevel = Level.newBuilder()
                          .setRuntimeId(nodeExecutionId)
                          .setSetupId(generateUuid())
                          .setGroup("STEP")
                          .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                          .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .addLevels(stageLevel)
                            .addLevels(stepLevel)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .putFeatureFlagToValueMap(
                                                 io.harness.beans.FeatureName.PIPE_CACHE_CURRENT_STATUS.name(), true)
                                             .build())
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .nodeId(nodeId)
            .identifier("DUMMY")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .build();

    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.save(any())).thenReturn(nodeExecution);
    when(nodeExecutionService.updateRelationShipsForRetryNode(any(), any())).thenReturn(true);
    when(nodeExecutionService.markRetried(any())).thenReturn(true);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(nodeExecutionService.getAmbiance(any(NodeExecution.class))).thenReturn(ambiance);
    when(planService.fetchNode(planId, nodeId)).thenReturn(planNode);

    // Mock NodeExecutionsInfo with firstUnsuccessfulRuntimeIdChain matching current level
    io.harness.beans.stepDetail.NodeExecutionsInfo stageExecutionsInfo =
        io.harness.beans.stepDetail.NodeExecutionsInfo.builder()
            .nodeExecutionId(stageNodeExecutionId)
            .failedChildIdChain(nodeExecutionId) // matches current level
            .build();
    when(pmsGraphStepDetailsService.getNodeExecutionsInfoWithProjections(eq(stageNodeExecutionId), any()))
        .thenReturn(stageExecutionsInfo);

    doReturn(ExecutionInputInstance.builder().build())
        .when(executionInputService)
        .getExecutionInputInstance(nodeExecutionId);

    retryHelper.retryNodeExecution(nodeExecution.getUuid(), interruptId, interruptConfig);

    // Verify clearFirstUnsuccessfulRuntimeIdChain was called
    verify(pmsGraphStepDetailsService, times(1)).clearFirstUnsuccessfulRuntimeIdChain(stageNodeExecutionId);
    verify(executorService).submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testRetryNodeExecution_WithCacheStatusFF_ShouldNotClearWhenDifferentRuntimeId() {
    String nodeExecutionId = generateUuid();
    String nodeId = generateUuid();
    String planId = generateUuid();
    String interruptId = generateUuid();
    String stageNodeExecutionId = generateUuid();
    String differentNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();

    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeId)
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .serviceName("CD")
                            .build();

    Level stageLevel = Level.newBuilder()
                           .setRuntimeId(stageNodeExecutionId)
                           .setSetupId(generateUuid())
                           .setGroup("STAGE")
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .build();

    Level stepLevel = Level.newBuilder()
                          .setRuntimeId(nodeExecutionId)
                          .setSetupId(generateUuid())
                          .setGroup("STEP")
                          .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                          .build();

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .setPlanId(planId)
                            .addLevels(stageLevel)
                            .addLevels(stepLevel)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                            .setMetadata(ExecutionMetadata.newBuilder()
                                             .putFeatureFlagToValueMap(
                                                 io.harness.beans.FeatureName.PIPE_CACHE_CURRENT_STATUS.name(), true)
                                             .build())
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .nodeId(nodeId)
            .identifier("DUMMY")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .build();

    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.save(any())).thenReturn(nodeExecution);
    when(nodeExecutionService.updateRelationShipsForRetryNode(any(), any())).thenReturn(true);
    when(nodeExecutionService.markRetried(any())).thenReturn(true);
    when(nodeExecutionService.getAmbiance(nodeExecution)).thenReturn(ambiance);
    when(nodeExecutionService.getAmbiance(any(NodeExecution.class))).thenReturn(ambiance);
    when(planService.fetchNode(planId, nodeId)).thenReturn(planNode);

    // Mock NodeExecutionsInfo with firstUnsuccessfulRuntimeIdChain NOT matching current level
    io.harness.beans.stepDetail.NodeExecutionsInfo stageExecutionsInfo =
        io.harness.beans.stepDetail.NodeExecutionsInfo.builder()
            .nodeExecutionId(stageNodeExecutionId)
            .failedChildIdChain(differentNodeExecutionId) // different from current level
            .build();
    when(pmsGraphStepDetailsService.getNodeExecutionsInfoWithProjections(eq(stageNodeExecutionId), any()))
        .thenReturn(stageExecutionsInfo);

    doReturn(ExecutionInputInstance.builder().build())
        .when(executionInputService)
        .getExecutionInputInstance(nodeExecutionId);

    retryHelper.retryNodeExecution(nodeExecution.getUuid(), interruptId, interruptConfig);

    // Verify clearFirstUnsuccessfulRuntimeIdChain was NOT called
    verify(pmsGraphStepDetailsService, never()).clearFirstUnsuccessfulRuntimeIdChain(any());
    verify(executorService).submit(any(Runnable.class));
  }
}
