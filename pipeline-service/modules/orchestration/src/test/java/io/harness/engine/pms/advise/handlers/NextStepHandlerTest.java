/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.plan.ExecutionMode.NORMAL;
import static io.harness.pms.contracts.plan.ExecutionMode.PIPELINE_ROLLBACK;
import static io.harness.pms.contracts.plan.ExecutionMode.POST_EXECUTION_ROLLBACK;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SOUMYO_PURKAYASTHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.DagExecutionService;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.DependencyEntry;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.StringArray;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NextStepHandlerTest extends CategoryTest {
  @Mock private OrchestrationEngine engine;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanService planService;
  @Mock private FailureStrategyAdviserHandlerUtils failureStrategyAdviserHandlerUtils;
  @Mock private DagExecutionService dagExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject @InjectMocks private NextStepHandler nextStepHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void handleAdviseWhenNextNodeIsIsEmpty() {
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().build()).build();
    Ambiance ambiance =
        Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(NORMAL).build()).build();
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).build();
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doNothing().when(engine).endNodeExecution(any());
    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);
    verify(engine).endNodeExecution(any());
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void handleAdvise_NormalMode_CallsInterruptPipelineIfFailAll() {
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().setFailAll(true).build()).build();
    Ambiance ambiance =
        Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(NORMAL).build()).build();
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).build();
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doNothing().when(engine).endNodeExecution(any());

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);

    verify(failureStrategyAdviserHandlerUtils).interruptPipelineIfFailAll(nodeExecution, ambiance, true);
    verify(engine).endNodeExecution(any());
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void handleAdvise_PipelineRollbackMode_SkipsInterruptPipelineIfFailAll() {
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().setFailAll(true).build()).build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(PIPELINE_ROLLBACK).build())
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).build();
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doNothing().when(engine).endNodeExecution(any());

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);

    verify(failureStrategyAdviserHandlerUtils, never()).interruptPipelineIfFailAll(any(), any(), anyBoolean());
    verify(engine).endNodeExecution(any());
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void handleAdvise_PostExecutionRollbackMode_SkipsInterruptPipelineIfFailAll() {
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().setFailAll(true).build()).build();
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(POST_EXECUTION_ROLLBACK).build())
            .build();
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(ambiance).build();
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doNothing().when(engine).endNodeExecution(any());

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);

    verify(failureStrategyAdviserHandlerUtils, never()).interruptPipelineIfFailAll(any(), any(), anyBoolean());
    verify(engine).endNodeExecution(any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void handleAdviseWithNextNodeId() {
    String nodeExecutionId = generateUuid();
    String nextNodeId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder()
            .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(nextNodeId).build())
            .build();

    PlanNode planNode = PlanNode.builder()
                            .uuid(nextNodeId)
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).setPlanId(planId).build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .parentId(generateUuid())
                                      .notifyId(generateUuid())
                                      .build();

    doReturn(NodeExecution.builder()
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build())
                               .build())
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(any(), eq(NodeProjectionUtils.withAmbiance));
    when(planService.fetchNode(planId, nextNodeId)).thenReturn(planNode);
    doNothing().when(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    ArgumentCaptor<Ambiance> ambianceArgumentCaptor = ArgumentCaptor.forClass(Ambiance.class);
    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);
    verify(engine).runNextNode(ambianceArgumentCaptor.capture(), eq(planNode), eq(nodeExecution), eq(null));

    assertThat(ambianceArgumentCaptor.getValue().getLevelsCount()).isEqualTo(1);
    assertThat(ambianceArgumentCaptor.getValue().getLevels(0).getSetupId()).isEqualTo(nextNodeId);
    verify(planService, times(0)).saveIdentityNodesForMatrix(any(), any());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreateIdentityNodeIfRequired() {
    IdentityPlanNode identityPlanNode = IdentityPlanNode.builder().build();
    PlanNode planNode = PlanNode.builder()
                            .uuid("uuid")
                            .identifier("nodeId")
                            .name("nodeName")
                            .stepType(StepType.newBuilder().build())
                            .build();

    // NodeExecution.parentId is empty. Same node will be returned.
    assertThat(nextStepHandler.createIdentityNodeIfRequired(planNode,
                   NodeExecution.builder()
                       .ambiance(Ambiance.newBuilder().setPlanExecutionId("planExecutinoId").build())
                       .build(),
                   NORMAL))
        .isEqualTo(planNode);

    doReturn(NodeExecution.builder()
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build())
                               .build())
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq("parentId"), any());
    assertThat(nextStepHandler.createIdentityNodeIfRequired(
                   planNode, NodeExecution.builder().parentId("parentId").build(), NORMAL))
        .isEqualTo(planNode);

    // Till now, same node has been returned all time. So, no interaction with planService.
    verify(planService, times(0)).saveIdentityNodesForMatrix(any(), any());

    doReturn(NodeExecution.builder()
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setNodeType(NodeType.IDENTITY_PLAN_NODE.name()).build())
                               .build())
                 .uuid("parentId")
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq("parentId"), any());
    doReturn(NodeExecution.builder().uuid("originalNodeExecutionId").nextId("nextId").build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq("originalNodeExecutionId"), any());

    doReturn(NodeExecution.builder().uuid(PlanCreatorConstants.NEXT_ID).oldRetry(false).build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(eq(PlanCreatorConstants.NEXT_ID), any());

    // Since currentNode is of type planNode and parentNodeExecution.nodeType is identityPlanNode. So identityNode will
    // be created for current node.
    Node savedIdentityNode = nextStepHandler.createIdentityNodeIfRequired(planNode,
        NodeExecution.builder()
            .ambiance(Ambiance.newBuilder().setPlanId("planId").build())
            .originalNodeExecutionId("originalNodeExecutionId")
            .parentId("parentId")
            .build(),
        NORMAL);
    assertThat(savedIdentityNode.getName()).isEqualTo(planNode.getName());
    assertThat(savedIdentityNode.getIdentifier()).isEqualTo(planNode.getIdentifier());
    assertThat(savedIdentityNode.getStepType()).isEqualTo(planNode.getStepType());
    assertThat(((IdentityPlanNode) savedIdentityNode).getOriginalNodeExecutionId())
        .isEqualTo(PlanCreatorConstants.NEXT_ID);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateIdentityNodeInRBMode() {
    PlanNode planNode = PlanNode.builder()
                            .uuid("uuid")
                            .identifier("nodeId")
                            .name("nodeName")
                            .stepType(StepType.newBuilder().build())
                            .preserveInRollbackMode(true)
                            .build();
    assertThat(nextStepHandler.createIdentityNodeIfRequired(planNode, null, PIPELINE_ROLLBACK)).isEqualTo(planNode);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testHandleAdviseWithDagExecution_ShouldProcessDependentStages() {
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String currentNodeId = generateUuid();
    String dependentStageUuid1 = generateUuid();
    String dependentStageUuid2 = generateUuid();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(currentNodeId)
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(dependentStageUuid1)
                            .setDependencies(StringArray.newBuilder().addValues(currentNodeId).build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(dependentStageUuid2)
                            .setDependencies(StringArray.newBuilder().addValues(currentNodeId).build())
                            .build())
            .build();

    PlanNode parentStagesNode = PlanNode.builder().uuid(generateUuid()).dependencyGraph(dependencyGraph).build();

    PlanNode currentPlanNode = PlanNode.builder().uuid(currentNodeId).identifier("current-stage").build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setRuntimeId("pipeline-level").build())
            .addLevels(Level.newBuilder().setSetupId("stages-node-id").setRuntimeId("stages-runtime-id").build())
            .addLevels(Level.newBuilder().setSetupId(currentNodeId).setRuntimeId("current-stage-runtime-id").build())
            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .nodeId(currentNodeId)
                                      .ambiance(ambiance)
                                      .status(Status.SUCCEEDED)
                                      .nodeType(NodeType.PLAN_NODE.toString())
                                      .build();

    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().build()).build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doReturn(parentStagesNode).when(planService).fetchNode(planId, "stages-node-id");
    doReturn(currentPlanNode).when(planService).fetchNode(planId, currentNodeId);

    doReturn(nodeExecution).when(nodeExecutionService).update(eq(nodeExecutionId), any());

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);
    verify(nodeExecutionService).update(eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleDagNodeCompletion_NormalStageWithDependents_FiresDependencyCallbacks() {
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String currentNodeId = generateUuid();
    String stagesNodeId = generateUuid();
    String parentRuntimeId = generateUuid();

    String dependentStageUuid = generateUuid();
    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(currentNodeId)
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(dependentStageUuid)
                            .setDependencies(StringArray.newBuilder().addValues(currentNodeId).build())
                            .build())
            .build();

    PlanNode parentStagesNode = PlanNode.builder().uuid(stagesNodeId).dependencyGraph(dependencyGraph).build();
    PlanNode currentPlanNode = PlanNode.builder().uuid(currentNodeId).identifier("current-stage").build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setRuntimeId("pipeline-level").build())
            .addLevels(Level.newBuilder().setSetupId(stagesNodeId).setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder().setSetupId(currentNodeId).setRuntimeId("current-runtime").build())
            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .nodeId(currentNodeId)
                                      .ambiance(ambiance)
                                      .status(Status.SUCCEEDED)
                                      .build();

    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().build()).build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doReturn(parentStagesNode).when(planService).fetchNode(planId, stagesNodeId);
    doReturn(currentPlanNode).when(planService).fetchNode(planId, currentNodeId);
    doReturn(nodeExecution).when(nodeExecutionService).update(eq(nodeExecutionId), any());

    // No rollback triggered
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    String dependencyCallbackId = "dep-callback-1";
    doReturn(dependencyCallbackId).when(dagExecutionService).generateCallbackId(parentRuntimeId, currentNodeId, false);

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);

    // Verify dependency callback is fired
    verify(waitNotifyEngine).doneWith(eq(dependencyCallbackId), any());
    verify(nodeExecutionService).update(eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleDagNodeCompletion_LeafNodeNoDependents_FiresLeafCallback() {
    // Product scenario: The last stage in a DAG (no downstream dependents) completes.
    // It should fire a leaf callback to signal DAG completion to the parent.
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String currentNodeId = generateUuid();
    String stagesNodeId = generateUuid();
    String parentRuntimeId = generateUuid();
    String rootNodeId = generateUuid();

    // currentNode depends on rootNode, nothing depends on currentNode (it's a leaf)
    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(rootNodeId)
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(currentNodeId)
                            .setDependencies(StringArray.newBuilder().addValues(rootNodeId).build())
                            .build())
            .build();

    PlanNode parentStagesNode = PlanNode.builder().uuid(stagesNodeId).dependencyGraph(dependencyGraph).build();
    PlanNode currentPlanNode = PlanNode.builder().uuid(currentNodeId).identifier("leaf-stage").build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setRuntimeId("pipeline-level").build())
            .addLevels(Level.newBuilder().setSetupId(stagesNodeId).setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder().setSetupId(currentNodeId).setRuntimeId("current-runtime").build())
            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .nodeId(currentNodeId)
                                      .ambiance(ambiance)
                                      .status(Status.SUCCEEDED)
                                      .build();

    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().build()).build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doReturn(parentStagesNode).when(planService).fetchNode(planId, stagesNodeId);
    doReturn(currentPlanNode).when(planService).fetchNode(planId, currentNodeId);

    // No rollback triggered
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    String leafCallbackId = "leaf-callback";
    doReturn(leafCallbackId).when(dagExecutionService).generateCallbackId(parentRuntimeId, currentNodeId, true);

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);

    // Leaf node: no dependents → fires leaf callback (isLeafNode=true)
    verify(waitNotifyEngine).doneWith(eq(leafCallbackId), any());
    // No update to nextIds since there are no dependents
    verify(nodeExecutionService, never()).update(eq(nodeExecutionId), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleDagNodeCompletion_RollbackTriggered_ActiveStagesRunning_Deferred() {
    // Product scenario: Stage S2 fails in a DAG with S1, S2, S3 running in parallel.
    // S1 and S3 are still active → rollback must be deferred until they finish.
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String currentNodeId = generateUuid();
    String stagesNodeId = generateUuid();
    String parentRuntimeId = generateUuid();
    String rollbackStageId = generateUuid();

    DependencyGraphProto dependencyGraph = DependencyGraphProto.newBuilder()
                                               .addEntries(DependencyEntry.newBuilder()
                                                               .setNodeId(currentNodeId)
                                                               .setDependencies(StringArray.newBuilder().build())
                                                               .build())
                                               .build();

    PlanNode parentStagesNode = PlanNode.builder().uuid(stagesNodeId).dependencyGraph(dependencyGraph).build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(NORMAL).build())
            .addLevels(Level.newBuilder().setRuntimeId("pipeline-level").build())
            .addLevels(Level.newBuilder().setSetupId(stagesNodeId).setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder().setSetupId(currentNodeId).setRuntimeId("current-runtime").build())
            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .nodeId(currentNodeId)
                                      .ambiance(ambiance)
                                      .status(Status.FAILED)
                                      .build();

    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder()
            .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(rollbackStageId).build())
            .build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doReturn(parentStagesNode).when(planService).fetchNode(planId, stagesNodeId);

    // Sweeping output found → rollback triggered
    doReturn(OptionalSweepingOutput.builder().found(true).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    // 2 stages still active → rollback must be deferred
    doReturn(2L).when(nodeExecutionService).findCountByParentIdAndStatusIn(eq(parentRuntimeId), any(Set.class));

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);

    // Rollback deferred: runNextNode should NOT be called
    verify(engine, never()).runNextNode(any(), any(), any(), any());
    // Dependency callbacks should also NOT be fired when rollback is deferred (early return)
    verify(waitNotifyEngine, never()).doneWith(any(), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleDagNodeCompletion_RollbackTriggered_ClaimFails_AnotherStageAlreadyClaimed() {
    // Product scenario: S2 and S3 both fail almost simultaneously in a DAG.
    // Both try to initiate rollback. Only one should win the race via tryClaimDagRollbackInitiation.
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String currentNodeId = generateUuid();
    String stagesNodeId = generateUuid();
    String parentRuntimeId = generateUuid();
    String rollbackStageId = generateUuid();

    DependencyGraphProto dependencyGraph = DependencyGraphProto.newBuilder()
                                               .addEntries(DependencyEntry.newBuilder()
                                                               .setNodeId(currentNodeId)
                                                               .setDependencies(StringArray.newBuilder().build())
                                                               .build())
                                               .build();

    PlanNode parentStagesNode = PlanNode.builder().uuid(stagesNodeId).dependencyGraph(dependencyGraph).build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(NORMAL).build())
            .addLevels(Level.newBuilder().setRuntimeId("pipeline-level").build())
            .addLevels(Level.newBuilder().setSetupId(stagesNodeId).setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder().setSetupId(currentNodeId).setRuntimeId("current-runtime").build())
            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .nodeId(currentNodeId)
                                      .ambiance(ambiance)
                                      .status(Status.FAILED)
                                      .build();

    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder()
            .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(rollbackStageId).build())
            .build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doReturn(parentStagesNode).when(planService).fetchNode(planId, stagesNodeId);

    // Sweeping output found → rollback triggered
    doReturn(OptionalSweepingOutput.builder().found(true).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    // No active stages
    doReturn(0L).when(nodeExecutionService).findCountByParentIdAndStatusIn(eq(parentRuntimeId), any(Set.class));

    // Claim fails → another stage already initiated rollback (consume throws)
    doThrow(new RuntimeException("Output already consumed"))
        .when(executionSweepingOutputService)
        .consume(any(), any(), any(), any());

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);

    // Claim failed: runNextNode should NOT be called (another node will start rollback)
    verify(engine, never()).runNextNode(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleDagNodeCompletion_RollbackTriggered_NoActiveStages_ClaimSucceeds_InitiatesRollback() {
    // Product scenario: A stage fails in a DAG, all other parallel stages have finished,
    // this node successfully claims rollback → starts the rollback stage.
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String currentNodeId = generateUuid();
    String stagesNodeId = generateUuid();
    String parentRuntimeId = generateUuid();
    String rollbackStageId = generateUuid();

    DependencyGraphProto dependencyGraph = DependencyGraphProto.newBuilder()
                                               .addEntries(DependencyEntry.newBuilder()
                                                               .setNodeId(currentNodeId)
                                                               .setDependencies(StringArray.newBuilder().build())
                                                               .build())
                                               .build();

    PlanNode parentStagesNode = PlanNode.builder().uuid(stagesNodeId).dependencyGraph(dependencyGraph).build();

    PlanNode rollbackPlanNode =
        PlanNode.builder()
            .uuid(rollbackStageId)
            .identifier("pipeline-rollback")
            .stepType(
                StepType.newBuilder().setType("PIPELINE_ROLLBACK_STAGE").setStepCategory(StepCategory.STAGE).build())
            .serviceName("pms")
            .build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(NORMAL).build())
            .addLevels(Level.newBuilder().setRuntimeId("pipeline-level").build())
            .addLevels(Level.newBuilder().setSetupId(stagesNodeId).setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder().setSetupId(currentNodeId).setRuntimeId("current-runtime").build())
            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .nodeId(currentNodeId)
                                      .ambiance(ambiance)
                                      .status(Status.FAILED)
                                      .parentId(parentRuntimeId)
                                      .build();

    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder()
            .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(rollbackStageId).build())
            .build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doReturn(parentStagesNode).when(planService).fetchNode(planId, stagesNodeId);
    doReturn(rollbackPlanNode).when(planService).fetchNode(planId, rollbackStageId);

    // Sweeping output found → rollback triggered
    doReturn(OptionalSweepingOutput.builder().found(true).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    // No active stages
    doReturn(0L).when(nodeExecutionService).findCountByParentIdAndStatusIn(eq(parentRuntimeId), any(Set.class));

    // Parent is a PlanNode (not identity), so createIdentityNodeIfRequired returns as-is
    doReturn(NodeExecution.builder()
                 .ambiance(Ambiance.newBuilder()
                               .addLevels(Level.newBuilder().setNodeType(NodeType.PLAN_NODE.name()).build())
                               .build())
                 .build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(any(), eq(NodeProjectionUtils.withAmbiance));

    doNothing().when(nodeExecutionService).updateV2(eq(nodeExecutionId), any());

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);

    // Rollback initiated: runNextNode should be called with the rollback stage
    verify(engine).runNextNode(any(), eq(rollbackPlanNode), eq(nodeExecution), eq(null));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleDagNodeCompletion_AlreadyInRollbackMode_DoesNotTriggerRollbackAgain() {
    // Product scenario: During a rollback execution (ExecutionMode.PIPELINE_ROLLBACK), a stage completes.
    // shouldTriggerPipelineRollback should return false (already rolling back) → normal DAG completion flow.
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String currentNodeId = generateUuid();
    String stagesNodeId = generateUuid();
    String parentRuntimeId = generateUuid();

    String dependentStageUuid = generateUuid();
    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(currentNodeId)
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(dependentStageUuid)
                            .setDependencies(StringArray.newBuilder().addValues(currentNodeId).build())
                            .build())
            .build();

    PlanNode parentStagesNode = PlanNode.builder().uuid(stagesNodeId).dependencyGraph(dependencyGraph).build();
    PlanNode currentPlanNode = PlanNode.builder().uuid(currentNodeId).identifier("rollback-stage").build();

    // Ambiance already in PIPELINE_ROLLBACK mode
    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(PIPELINE_ROLLBACK).build())
            .addLevels(Level.newBuilder().setRuntimeId("pipeline-level").build())
            .addLevels(Level.newBuilder().setSetupId(stagesNodeId).setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder().setSetupId(currentNodeId).setRuntimeId("current-runtime").build())
            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .nodeId(currentNodeId)
                                      .ambiance(ambiance)
                                      .status(Status.SUCCEEDED)
                                      .build();

    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().build()).build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doReturn(parentStagesNode).when(planService).fetchNode(planId, stagesNodeId);
    doReturn(currentPlanNode).when(planService).fetchNode(planId, currentNodeId);
    doReturn(nodeExecution).when(nodeExecutionService).update(eq(nodeExecutionId), any());

    String dependencyCallbackId = "dep-callback";
    doReturn(dependencyCallbackId).when(dagExecutionService).generateCallbackId(parentRuntimeId, currentNodeId, false);

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);

    // Should NOT check sweeping output since already in rollback mode
    verify(executionSweepingOutputService, never()).resolveOptional(any(), any());
    // Should proceed with normal DAG completion — fire dependency callback
    verify(waitNotifyEngine).doneWith(eq(dependencyCallbackId), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testHandleDagNodeCompletion_SweepingOutputCheckThrows_ProceedsWithNormalDag() {
    // Product scenario: Sweeping output service throws an exception when checking for rollback.
    // shouldTriggerPipelineRollback catches exception and returns false → normal DAG flow continues.
    String nodeExecutionId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    String currentNodeId = generateUuid();
    String stagesNodeId = generateUuid();
    String parentRuntimeId = generateUuid();

    DependencyGraphProto dependencyGraph = DependencyGraphProto.newBuilder()
                                               .addEntries(DependencyEntry.newBuilder()
                                                               .setNodeId(currentNodeId)
                                                               .setDependencies(StringArray.newBuilder().build())
                                                               .build())
                                               .build();

    PlanNode parentStagesNode = PlanNode.builder().uuid(stagesNodeId).dependencyGraph(dependencyGraph).build();
    PlanNode currentPlanNode = PlanNode.builder().uuid(currentNodeId).identifier("stage-x").build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .setPlanId(planId)
            .setMetadata(ExecutionMetadata.newBuilder().setExecutionMode(NORMAL).build())
            .addLevels(Level.newBuilder().setRuntimeId("pipeline-level").build())
            .addLevels(Level.newBuilder().setSetupId(stagesNodeId).setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder().setSetupId(currentNodeId).setRuntimeId("current-runtime").build())
            .build();

    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .nodeId(currentNodeId)
                                      .ambiance(ambiance)
                                      .status(Status.SUCCEEDED)
                                      .build();

    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().build()).build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    doReturn(parentStagesNode).when(planService).fetchNode(planId, stagesNodeId);
    doReturn(currentPlanNode).when(planService).fetchNode(planId, currentNodeId);

    // Sweeping output check throws
    doThrow(new RuntimeException("service unavailable"))
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    String leafCallbackId = "leaf-callback";
    doReturn(leafCallbackId).when(dagExecutionService).generateCallbackId(parentRuntimeId, currentNodeId, true);

    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);

    // Exception caught → shouldTriggerPipelineRollback returns false → normal DAG path
    // currentNode is a leaf (no dependents) → fires leaf callback
    verify(waitNotifyEngine).doneWith(eq(leafCallbackId), any());
    // No rollback initiation
    verify(engine, never()).runNextNode(any(), any(), any(), any());
  }
}
