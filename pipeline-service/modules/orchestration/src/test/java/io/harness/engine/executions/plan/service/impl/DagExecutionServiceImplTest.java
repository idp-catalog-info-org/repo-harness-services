/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.DependencyEntry;
import io.harness.pms.contracts.plan.DependencyGraphProto;
import io.harness.pms.contracts.plan.StringArray;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class DagExecutionServiceImplTest extends OrchestrationTestBase {
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private PlanService planService;

  @InjectMocks private DagExecutionServiceImpl dagExecutionService;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testFireDagCallbacksForNoAdviserStage_NoParentWithDependencyGraph_DoesNothing() {
    String stageNodeId = generateUuid();
    String parentNodeId = generateUuid();
    String planId = generateUuid();

    PlanNode stageNode =
        PlanNode.builder()
            .uuid(stageNodeId)
            .name("Test Stage")
            .identifier("test-stage")
            .stepType(StepType.newBuilder().setType("DEPLOYMENT_STAGE").setStepCategory(StepCategory.STAGE).build())
            .build();

    PlanNode parentNode = PlanNode.builder().uuid(parentNodeId).build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setSetupId(parentNodeId).setRuntimeId("parentRuntimeId").build())
            .addLevels(Level.newBuilder()
                           .setSetupId(stageNodeId)
                           .setRuntimeId("stageRuntimeId")
                           .setIdentifier("test-stage")
                           .build())
            .build();

    when(planService.fetchNode(planId, parentNodeId)).thenReturn(parentNode);

    dagExecutionService.fireDagCallbacksForNoAdviserStage(ambiance, stageNode, Status.SUCCEEDED);

    verify(waitNotifyEngine, never()).doneWith(any(), any());
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testFireDagCallbacksForNoAdviserStage_DagStage_FiresCallback() {
    String stageNodeId = generateUuid();
    String parentNodeId = generateUuid();
    String parentRuntimeId = generateUuid();
    String planId = generateUuid();
    String dependentStageId = generateUuid();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(stageNodeId)
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(dependentStageId)
                            .setDependencies(StringArray.newBuilder().addValues(stageNodeId).build())
                            .build())
            .build();

    PlanNode stageNode =
        PlanNode.builder()
            .uuid(stageNodeId)
            .name("Test Stage")
            .identifier("test-stage")
            .stepType(StepType.newBuilder().setType("DEPLOYMENT_STAGE").setStepCategory(StepCategory.STAGE).build())
            .build();

    PlanNode parentNode = PlanNode.builder().uuid(parentNodeId).dependencyGraph(dependencyGraph).build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setSetupId(parentNodeId).setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder()
                           .setSetupId(stageNodeId)
                           .setRuntimeId("stageRuntimeId")
                           .setIdentifier("test-stage")
                           .build())
            .build();

    when(planService.fetchNode(planId, parentNodeId)).thenReturn(parentNode);

    dagExecutionService.fireDagCallbacksForNoAdviserStage(ambiance, stageNode, Status.SUCCEEDED);

    String expectedCallbackId = "dag_" + parentRuntimeId + "_" + stageNodeId;
    verify(waitNotifyEngine, times(1)).doneWith(eq(expectedCallbackId), any(StepResponseNotifyData.class));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testFireDagCallbacksForNoAdviserStage_LeafDagStage_FiresLeafCallback() {
    String stageNodeId = generateUuid();
    String parentNodeId = generateUuid();
    String parentRuntimeId = generateUuid();
    String planId = generateUuid();
    String rootNodeId = generateUuid();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(rootNodeId)
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(stageNodeId)
                            .setDependencies(StringArray.newBuilder().addValues(rootNodeId).build())
                            .build())
            .build();

    PlanNode stageNode =
        PlanNode.builder()
            .uuid(stageNodeId)
            .name("Leaf Stage")
            .identifier("leaf-stage")
            .stepType(StepType.newBuilder().setType("DEPLOYMENT_STAGE").setStepCategory(StepCategory.STAGE).build())
            .build();

    PlanNode parentNode = PlanNode.builder().uuid(parentNodeId).dependencyGraph(dependencyGraph).build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setSetupId(parentNodeId).setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder()
                           .setSetupId(stageNodeId)
                           .setRuntimeId("stageRuntimeId")
                           .setIdentifier("leaf-stage")
                           .build())
            .build();

    when(planService.fetchNode(planId, parentNodeId)).thenReturn(parentNode);

    dagExecutionService.fireDagCallbacksForNoAdviserStage(ambiance, stageNode, Status.SUCCEEDED);

    String expectedLeafCallbackId = "dag_leaf_" + parentRuntimeId + "_" + stageNodeId;
    verify(waitNotifyEngine, times(1)).doneWith(eq(expectedLeafCallbackId), any(StepResponseNotifyData.class));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testFireDagCallbacksForNoAdviserStage_PipelineRollbackStage_FiresAllLeafCallbacks() {
    String rollbackStageNodeId = generateUuid();
    String parentNodeId = generateUuid();
    String parentRuntimeId = generateUuid();
    String planId = generateUuid();

    String rootNodeId = generateUuid();
    String leaf1NodeId = generateUuid();
    String leaf2NodeId = generateUuid();

    DependencyGraphProto dependencyGraph =
        DependencyGraphProto.newBuilder()
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(rootNodeId)
                            .setDependencies(StringArray.newBuilder().build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(leaf1NodeId)
                            .setDependencies(StringArray.newBuilder().addValues(rootNodeId).build())
                            .build())
            .addEntries(DependencyEntry.newBuilder()
                            .setNodeId(leaf2NodeId)
                            .setDependencies(StringArray.newBuilder().addValues(rootNodeId).build())
                            .build())
            .build();

    PlanNode rollbackNode = PlanNode.builder()
                                .uuid(rollbackStageNodeId)
                                .name("Pipeline Rollback")
                                .identifier("pipeline-rollback")
                                .stepType(StepType.newBuilder()
                                              .setType(OrchestrationStepTypes.PIPELINE_ROLLBACK_STAGE)
                                              .setStepCategory(StepCategory.STAGE)
                                              .build())
                                .build();

    PlanNode parentNode = PlanNode.builder().uuid(parentNodeId).dependencyGraph(dependencyGraph).build();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanId(planId)
            .addLevels(Level.newBuilder().setSetupId(parentNodeId).setRuntimeId(parentRuntimeId).build())
            .addLevels(Level.newBuilder()
                           .setSetupId(rollbackStageNodeId)
                           .setRuntimeId("rollbackRuntimeId")
                           .setIdentifier("pipeline-rollback")
                           .build())
            .build();

    when(planService.fetchNode(planId, parentNodeId)).thenReturn(parentNode);

    dagExecutionService.fireDagCallbacksForNoAdviserStage(ambiance, rollbackNode, Status.SUCCEEDED);

    String leaf1CallbackId = "dag_leaf_" + parentRuntimeId + "_" + leaf1NodeId;
    String leaf2CallbackId = "dag_leaf_" + parentRuntimeId + "_" + leaf2NodeId;
    verify(waitNotifyEngine, times(1)).doneWith(eq(leaf1CallbackId), any(StepResponseNotifyData.class));
    verify(waitNotifyEngine, times(1)).doneWith(eq(leaf2CallbackId), any(StepResponseNotifyData.class));
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGenerateCallbackId_NonLeafNode() {
    String parentNodeExecutionId = "parent-123";
    String currentNodeId = "node-456";

    String callbackId = dagExecutionService.generateCallbackId(parentNodeExecutionId, currentNodeId, false);

    String expected = "dag_parent-123_node-456";
    org.assertj.core.api.Assertions.assertThat(callbackId).isEqualTo(expected);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGenerateCallbackId_LeafNode() {
    String parentNodeExecutionId = "parent-123";
    String currentNodeId = "node-456";

    String callbackId = dagExecutionService.generateCallbackId(parentNodeExecutionId, currentNodeId, true);

    String expected = "dag_leaf_parent-123_node-456";
    org.assertj.core.api.Assertions.assertThat(callbackId).isEqualTo(expected);
  }
}
