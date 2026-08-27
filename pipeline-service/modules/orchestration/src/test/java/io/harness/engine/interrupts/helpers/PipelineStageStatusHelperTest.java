/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.pms.contracts.execution.Status.INTERVENTION_WAITING;
import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineStageStatusHelperTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionService planExecutionService;
  @InjectMocks private PipelineStageStatusHelper pipelineStageStatusHelper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testUpdatePipelineAndStageWaitingStatus() {
    String planExecutionId = "planExecutionId";
    String stageNodeExecutionId = "stageNodeExecutionId";
    String parentPipelineStageNodeId = "parentPipelineStageNodeId";
    String parentPipelineExecutionId = "parentPipelineExecutionId";
    Status interventionWaiting = INTERVENTION_WAITING;
    PipelineStageInfo pipelineStageInfo = PipelineStageInfo.newBuilder()
                                              .setStageNodeId(parentPipelineStageNodeId)
                                              .setExecutionId(parentPipelineExecutionId)
                                              .setHasParentPipeline(true)
                                              .build();
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(
                NodeExecution.builder()
                    .ambiance(
                        Ambiance.newBuilder()
                            .addLevels(
                                Level.newBuilder()
                                    .setRuntimeId(stageNodeExecutionId)
                                    .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                    .build())
                            .setPlanExecutionId(planExecutionId)
                            .setMetadata(ExecutionMetadata.newBuilder().setPipelineStageInfo(pipelineStageInfo).build())
                            .build())
                    .build())
            .build();
    String parentPipelineStageExecutionId = "parentPipelineStageExecutionId";
    NodeExecution pipelineStageNodeExecution =
        NodeExecution.builder()
            .ambiance(Ambiance.newBuilder().setStageExecutionId(parentPipelineStageExecutionId).build())
            .build();
    doReturn(pipelineStageNodeExecution)
        .when(nodeExecutionService)
        .getByPlanNodeUuid(pipelineStageInfo.getStageNodeId(), pipelineStageInfo.getExecutionId());
    doReturn(nodeUpdateInfo.getNodeExecution().getAmbiance())
        .when(nodeExecutionService)
        .getAmbiance(nodeUpdateInfo.getNodeExecution());
    pipelineStageStatusHelper.updatePipelineAndStageWaitingStatus(nodeUpdateInfo, interventionWaiting);
    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(parentPipelineStageExecutionId, interventionWaiting, null,
            StatusUtils.planAllowedStartSet(interventionWaiting));
    verify(planExecutionService, times(1)).updateStatus(parentPipelineExecutionId, interventionWaiting);
  }
}
