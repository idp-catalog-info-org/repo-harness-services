/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.pms.contracts.execution.Status.UPLOAD_WAITING;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.interrupts.helpers.PipelineStageStatusHelper;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
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
public class UploadWaitingStepStatusUpdateTest extends CategoryTest {
  @Mock private PlanExecutionService planExecutionService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PipelineStageStatusHelper pipelineStageStatusHelper;
  @InjectMocks UploadWaitingStepStatusUpdate uploadWaitingStepStatusUpdate;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testHandleNodeStatusUpdate() {
    String planExecutionId = "planExecutionId";
    String stageNodeExecutionId = "stageNodeExecutionId";
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(
                NodeExecution.builder()
                    .ambiance(Ambiance.newBuilder()
                                  .addLevels(Level.newBuilder()
                                                 .setRuntimeId(stageNodeExecutionId)
                                                 .setStepType(
                                                     StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                                 .build())
                                  .setPlanExecutionId(planExecutionId)
                                  .build())
                    .build())
            .build();

    uploadWaitingStepStatusUpdate.handleNodeStatusUpdate(nodeUpdateInfo);
    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(
            stageNodeExecutionId, UPLOAD_WAITING, null, StatusUtils.planAllowedStartSet(UPLOAD_WAITING));
    verify(planExecutionService, times(1)).updateStatus(planExecutionId, UPLOAD_WAITING);
    verify(pipelineStageStatusHelper, times(1)).updatePipelineAndStageWaitingStatus(nodeUpdateInfo, UPLOAD_WAITING);
  }
}
