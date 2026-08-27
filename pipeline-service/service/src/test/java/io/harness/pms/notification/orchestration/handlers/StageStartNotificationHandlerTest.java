/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.notification.PipelineEventType;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.notification.helper.NotificationHelper;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class StageStartNotificationHandlerTest extends CategoryTest {
  @Mock ExecutorService executorService;
  @Mock NotificationHelper notificationHelper;
  @Mock NodeExecutionService nodeExecutionService;
  @InjectMocks StageStartNotificationHandler stageStartNotificationHandler;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testOnNodeStart() {
    long ts = System.currentTimeMillis();
    PlanNode stagesNode = PlanNode.builder()
                              .uuid(generateUuid())
                              .name("STAGES")
                              .identifier("stages")
                              .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).build())
                              .build();
    Ambiance stagesAmbiance =
        Ambiance.newBuilder().addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stagesNode)).build();
    NodeExecution nodeExecution = NodeExecution.builder().ambiance(stagesAmbiance).build();
    NodeStartInfo nodeStartInfo = NodeStartInfo.builder().nodeExecution(nodeExecution).updatedTs(ts).build();
    doReturn(stagesAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    stageStartNotificationHandler.onNodeStart(nodeStartInfo);
    verify(notificationHelper, times(0)).sendNotification(any(), any(), any(), any());

    PlanNode stageNode = PlanNode.builder()
                             .uuid(generateUuid())
                             .name("STAGE")
                             .identifier("stage")
                             .stepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                             .build();
    Ambiance stageAmbiance =
        Ambiance.newBuilder().addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stageNode)).build();
    nodeExecution = NodeExecution.builder().ambiance(stageAmbiance).build();
    nodeStartInfo = NodeStartInfo.builder().nodeExecution(nodeExecution).updatedTs(ts).build();
    doReturn(stageAmbiance).when(nodeExecutionService).getAmbiance(nodeExecution);
    stageStartNotificationHandler.onNodeStart(nodeStartInfo);
    verify(notificationHelper, times(1))
        .sendNotification(stageAmbiance, PipelineEventType.STAGE_START, nodeExecution, ts);

    PlanNode stageNode1 = PlanNode.builder()
                              .uuid(generateUuid())
                              .name("PIPELINE")
                              .identifier("pipeline")
                              .stepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                              .build();

    Ambiance stageAmbiance1 =
        Ambiance.newBuilder().addLevels(PmsLevelUtils.buildLevelFromNode(generateUuid(), stageNode1)).build();
    nodeExecution = NodeExecution.builder().ambiance(stageAmbiance1).build();
    nodeStartInfo = NodeStartInfo.builder().nodeExecution(nodeExecution).updatedTs(ts).build();
    doReturn(stageAmbiance1).when(nodeExecutionService).getAmbiance(nodeExecution);
    stageStartNotificationHandler.onNodeStart(nodeStartInfo);
    verify(notificationHelper, times(1))
        .sendNotification(stageAmbiance1, PipelineEventType.PIPELINE_START, nodeExecution, ts);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetInformExecutorService() {
    assertThat(stageStartNotificationHandler.getInformExecutorService()).isEqualTo(executorService);
  }
}
