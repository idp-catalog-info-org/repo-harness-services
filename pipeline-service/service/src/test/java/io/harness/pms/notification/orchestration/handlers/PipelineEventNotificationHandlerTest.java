/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.notification.orchestration.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.notification.orchestration.handlers.PipelineEventNotificationHandler.resolveInterventionDedupeTimestampMs;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.OM;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.observers.GraphUpdateEventInfo;
import io.harness.entity.eventlog.NotificationEventLog;
import io.harness.execution.NodeExecution;
import io.harness.notification.PipelineEventType;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.notification.helper.NotificationEventsHelper;
import io.harness.pms.notification.helper.NotificationHelper;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)

public class PipelineEventNotificationHandlerTest {
  private static final String PLAN_EXECUTION_ID = "somePlanExecutionId";
  private static final String NODE_EXECUTION_ID = "abc";
  private static final Long LAST_UPDATED_AT = 170000000000L;

  private static final Ambiance ambiance = Ambiance.newBuilder()
                                               .putSetupAbstractions("accountId", "testAccountId")
                                               .putSetupAbstractions("orgIdentifier", "testOrg")
                                               .putSetupAbstractions("projectIdentifier", "testProject")
                                               .build();
  @Mock NotificationEventsHelper notificationEventsHelper;
  @Mock NotificationHelper notificationHelper;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @InjectMocks PipelineEventNotificationHandler pipelineEventNotificationHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSendingPipelineStartEvent() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.RUNNING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());
    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_START));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSendingPipelineStartSuccessEvents() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.SUCCEEDED)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());
    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_START));
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_SUCCESS));
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_END));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSendingPipelineSuccessEvent() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.SUCCEEDED)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any()))
        .thenReturn(Collections.singletonList(NotificationEventLog.builder()
                                                  .planExecutionId(PLAN_EXECUTION_ID)
                                                  .pipelineEventType(PipelineEventType.PIPELINE_START)
                                                  .nodeExecutionId(NODE_EXECUTION_ID)
                                                  .build()));
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_SUCCESS));
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_END));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSendingStageStartEvent() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.RUNNING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());
    pipelineEventNotificationHandler.processStageEvents(PLAN_EXECUTION_ID, graphUpdateEventInfo, nodeExecution, "");
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STAGE_START));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSendingStageStartSuccessEvents() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.SUCCEEDED)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());
    pipelineEventNotificationHandler.processStageEvents(PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STAGE_START));
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STAGE_SUCCESS));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSendingStageSuccessEvent() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.SUCCEEDED)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any()))
        .thenReturn(Collections.singletonList(NotificationEventLog.builder()
                                                  .planExecutionId(PLAN_EXECUTION_ID)
                                                  .pipelineEventType(PipelineEventType.PIPELINE_START)
                                                  .nodeExecutionId(NODE_EXECUTION_ID)
                                                  .build()));
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    pipelineEventNotificationHandler.processStageEvents(PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STAGE_SUCCESS));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSendingStageStartStepFailedEvents() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.FAILED)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    Map<String, String> setUpAbstractions = new HashMap<>();
    setUpAbstractions.put("accountId", "testAccountId");
    Ambiance ambiance =
        Ambiance.newBuilder().setStageExecutionId("stageOfAbc").putAllSetupAbstractions(setUpAbstractions).build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().uuid(NODE_EXECUTION_ID).ambiance(ambiance).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    when(nodeExecutionService.getWithFieldsIncluded(any(), any()))
        .thenReturn(NodeExecution.builder().uuid("stageOfAbc").ambiance(ambiance).build());
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());
    pipelineEventNotificationHandler.processStepEvents(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "", new HashMap<>());
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STAGE_START));
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STEP_FAILED));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testSendingStepFailedEvent() {
    String stageExecutionId = "stageOfAbc";
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.FAILED)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution = NodeExecution.builder()
                                          .uuid(NODE_EXECUTION_ID)
                                          .ambiance(Ambiance.newBuilder().setStageExecutionId("stageOfAbc").build())
                                          .startTs(LAST_UPDATED_AT)
                                          .build();
    when(notificationEventsHelper.getNotificationsSent(any(), any()))
        .thenReturn(Collections.singletonList(NotificationEventLog.builder()
                                                  .planExecutionId(PLAN_EXECUTION_ID)
                                                  .nodeExecutionId(stageExecutionId)
                                                  .pipelineEventType(PipelineEventType.STAGE_START)
                                                  .build()));
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    when(nodeExecutionService.getWithFieldsIncluded(any(), any()))
        .thenReturn(NodeExecution.builder().ambiance(ambiance).uuid("stageOfAbc").build());
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());
    pipelineEventNotificationHandler.processStepEvents(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "", new HashMap<>());
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STEP_FAILED));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testForDuplicateEvents() {
    String stageExecutionId = "stageOfAbc";
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.FAILED)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution = NodeExecution.builder()
                                          .uuid(NODE_EXECUTION_ID)
                                          .ambiance(Ambiance.newBuilder().setStageExecutionId(stageExecutionId).build())
                                          .startTs(LAST_UPDATED_AT)
                                          .build();
    when(notificationEventsHelper.getNotificationsSent(any(), any()))
        .thenReturn(List.of(NotificationEventLog.builder()
                                .planExecutionId(PLAN_EXECUTION_ID)
                                .nodeExecutionId(NODE_EXECUTION_ID)
                                .pipelineEventType(PipelineEventType.STEP_FAILED)
                                .build(),
            NotificationEventLog.builder()
                .planExecutionId(PLAN_EXECUTION_ID)
                .nodeExecutionId(stageExecutionId)
                .pipelineEventType(PipelineEventType.STAGE_START)
                .build()));
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    when(nodeExecutionService.getWithFieldsIncluded(any(), any()))
        .thenReturn(NodeExecution.builder().ambiance(ambiance).uuid(stageExecutionId).build());
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());
    pipelineEventNotificationHandler.processStepEvents(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "", new HashMap<>());
    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STAGE_START));
    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STEP_FAILED));
    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STAGE_START));
    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STEP_FAILED));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testSendingPipelineStartEvents_CNS() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.RUNNING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();

    // Create a plan node execution with proper setup
    NodeExecution planNodeExecution = NodeExecution.builder()
                                          .ambiance(ambiance)
                                          .uuid(NODE_EXECUTION_ID)
                                          .startTs(LAST_UPDATED_AT)
                                          .nodeType(NodeType.PLAN.name())
                                          .build();

    // Mock the required service responses
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(false);

    // Enable the CNS feature flag
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.PIPE_DISABLE_PIPELINE_NOTIFICATIONS_ON_ROLLBACK)))
        .thenReturn(false);

    // Mock the notification sending
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    // Execute the method under test
    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "pipelineYaml");
    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_START));

    // Verify the standard notification was sent
    verify(notificationHelper, times(1)).sendCNSNotification(any(), eq(PipelineEventType.PIPELINE_START), any(), any());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUserActionRequiredForStageWithApprovalWaitingStatus() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.APPROVAL_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution stageNodeExecution = NodeExecution.builder()
                                           .ambiance(ambiance)
                                           .uuid(NODE_EXECUTION_ID)
                                           .startTs(LAST_UPDATED_AT)
                                           .nodeType(NodeType.PLAN_NODE.name())
                                           .executionInputConfigured(true)
                                           .build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    pipelineEventNotificationHandler.processStageEvents(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, stageNodeExecution, "");

    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.WAITING_FOR_USER_ACTION), any());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUserActionRequiredForStageWithInterventionWaitingStatus() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.INTERVENTION_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution stageNodeExecution = NodeExecution.builder()
                                           .ambiance(ambiance)
                                           .uuid(NODE_EXECUTION_ID)
                                           .startTs(LAST_UPDATED_AT)
                                           .nodeType(NodeType.PLAN_NODE.name())
                                           .executionInputConfigured(true)
                                           .build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    pipelineEventNotificationHandler.processStageEvents(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, stageNodeExecution, "");

    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.WAITING_FOR_USER_ACTION), any());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUserActionRequiredForStageWithInputWaitingStatus() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.INPUT_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution stageNodeExecution = NodeExecution.builder()
                                           .ambiance(ambiance)
                                           .uuid(NODE_EXECUTION_ID)
                                           .startTs(LAST_UPDATED_AT)
                                           .nodeType(NodeType.PLAN_NODE.name())
                                           .executionInputConfigured(true)
                                           .build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    pipelineEventNotificationHandler.processStageEvents(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, stageNodeExecution, "");

    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.WAITING_FOR_USER_ACTION), any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testUserActionRequiredForStageWithUploadWaitingStatus() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.UPLOAD_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution stageNodeExecution = NodeExecution.builder()
                                           .ambiance(ambiance)
                                           .uuid(NODE_EXECUTION_ID)
                                           .startTs(LAST_UPDATED_AT)
                                           .nodeType(NodeType.PLAN_NODE.name())
                                           .executionInputConfigured(true)
                                           .build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    pipelineEventNotificationHandler.processStageEvents(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, stageNodeExecution, "");

    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.WAITING_FOR_USER_ACTION), any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testSendingPipelineResumedEventAfterUploadWaiting() {
    String waitingNodeId = "uploadStepNodeId";
    String waitingDedupeKey =
        PipelineEventNotificationHandler.buildInterventionNotificationDedupeKey(waitingNodeId, LAST_UPDATED_AT);
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.RUNNING)
                                                    .previousStatus(Status.UPLOAD_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationEventsHelper.findMostRecentByEventType(
             eq(PLAN_EXECUTION_ID), eq(PipelineEventType.WAITING_FOR_USER_ACTION)))
        .thenReturn(Optional.of(NotificationEventLog.builder()
                                    .planExecutionId(PLAN_EXECUTION_ID)
                                    .nodeExecutionId(waitingDedupeKey)
                                    .pipelineEventType(PipelineEventType.WAITING_FOR_USER_ACTION)
                                    .build()));
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any(), any());

    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");

    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(
            any(), any(), any(), eq(PipelineEventType.PIPELINE_RESUMED), eq(waitingDedupeKey));
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUserActionRequiredNotSentWhenAlreadySent() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.APPROVAL_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution stageNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();

    // Mock that WAITING_FOR_USER_ACTION was already sent
    NotificationEventLog existingLog =
        NotificationEventLog.builder()
            .nodeExecutionId(PipelineEventNotificationHandler.buildInterventionNotificationDedupeKey(
                NODE_EXECUTION_ID, LAST_UPDATED_AT))
            .pipelineEventType(PipelineEventType.WAITING_FOR_USER_ACTION)
            .build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(List.of(existingLog));
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);

    pipelineEventNotificationHandler.processStageEvents(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, stageNodeExecution, "");

    // Verify notification was NOT sent again
    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.WAITING_FOR_USER_ACTION), any());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUserActionRequiredNotSentForNonWaitingStatus() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.RUNNING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution stageNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    pipelineEventNotificationHandler.processStageEvents(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, stageNodeExecution, "");

    // Verify WAITING_FOR_USER_ACTION was NOT sent for RUNNING status
    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.WAITING_FOR_USER_ACTION), any());
    // But STAGE_START should be sent
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.STAGE_START));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testSendingPipelineResumedEventAfterApprovalWaiting() {
    String waitingNodeId = "approvalStepNodeId";
    String waitingDedupeKey =
        PipelineEventNotificationHandler.buildInterventionNotificationDedupeKey(waitingNodeId, LAST_UPDATED_AT);
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.RUNNING)
                                                    .previousStatus(Status.APPROVAL_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationEventsHelper.findMostRecentByEventType(
             eq(PLAN_EXECUTION_ID), eq(PipelineEventType.WAITING_FOR_USER_ACTION)))
        .thenReturn(Optional.of(NotificationEventLog.builder()
                                    .planExecutionId(PLAN_EXECUTION_ID)
                                    .nodeExecutionId(waitingDedupeKey)
                                    .pipelineEventType(PipelineEventType.WAITING_FOR_USER_ACTION)
                                    .build()));
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any(), any());

    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");

    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(
            any(), any(), any(), eq(PipelineEventType.PIPELINE_RESUMED), eq(waitingDedupeKey));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testSendingPipelineResumedEventAfterInterventionWaiting() {
    String waitingNodeId = "interventionStepNodeId";
    String waitingDedupeKey =
        PipelineEventNotificationHandler.buildInterventionNotificationDedupeKey(waitingNodeId, LAST_UPDATED_AT);
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.RUNNING)
                                                    .previousStatus(Status.INTERVENTION_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationEventsHelper.findMostRecentByEventType(
             eq(PLAN_EXECUTION_ID), eq(PipelineEventType.WAITING_FOR_USER_ACTION)))
        .thenReturn(Optional.of(NotificationEventLog.builder()
                                    .planExecutionId(PLAN_EXECUTION_ID)
                                    .nodeExecutionId(waitingDedupeKey)
                                    .pipelineEventType(PipelineEventType.WAITING_FOR_USER_ACTION)
                                    .build()));
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any(), any());

    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");

    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(
            any(), any(), any(), eq(PipelineEventType.PIPELINE_RESUMED), eq(waitingDedupeKey));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testSendingPipelineResumedEventAfterInputWaiting() {
    String waitingNodeId = "inputStepNodeId";
    String waitingDedupeKey =
        PipelineEventNotificationHandler.buildInterventionNotificationDedupeKey(waitingNodeId, LAST_UPDATED_AT);
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.RUNNING)
                                                    .previousStatus(Status.INPUT_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationEventsHelper.findMostRecentByEventType(
             eq(PLAN_EXECUTION_ID), eq(PipelineEventType.WAITING_FOR_USER_ACTION)))
        .thenReturn(Optional.of(NotificationEventLog.builder()
                                    .planExecutionId(PLAN_EXECUTION_ID)
                                    .nodeExecutionId(waitingDedupeKey)
                                    .pipelineEventType(PipelineEventType.WAITING_FOR_USER_ACTION)
                                    .build()));
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any(), any());

    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");

    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(
            any(), any(), any(), eq(PipelineEventType.PIPELINE_RESUMED), eq(waitingDedupeKey));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testPipelineResumedNotSentWhenNoPreviousStatus() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.RUNNING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");

    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_RESUMED));
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_START));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testPipelineResumedNotSentWhenPreviousStatusNotWaiting() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.RUNNING)
                                                    .previousStatus(Status.QUEUED)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");

    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_RESUMED));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testPipelineResumedNotSentWhenCurrentStatusNotRunning() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.SUCCEEDED)
                                                    .previousStatus(Status.APPROVAL_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    doNothing().when(notificationHelper).sendNotificationEventWithLock(any(), any(), any(), any());

    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");

    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_RESUMED));
  }

  @Test
  @Owner(developers = OM)
  @Category(UnitTests.class)
  public void testPipelineResumedNotSentWhenAlreadySent() {
    String waitingNodeId = "approvalStepNodeId";
    String waitingDedupeKey =
        PipelineEventNotificationHandler.buildInterventionNotificationDedupeKey(waitingNodeId, LAST_UPDATED_AT);
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN)
                                                    .status(Status.RUNNING)
                                                    .previousStatus(Status.APPROVAL_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution planNodeExecution =
        NodeExecution.builder().ambiance(ambiance).uuid(NODE_EXECUTION_ID).startTs(LAST_UPDATED_AT).build();
    when(notificationEventsHelper.getNotificationsSent(any(), any()))
        .thenReturn(List.of(NotificationEventLog.builder()
                                .planExecutionId(PLAN_EXECUTION_ID)
                                .nodeExecutionId(waitingDedupeKey)
                                .pipelineEventType(PipelineEventType.PIPELINE_RESUMED)
                                .build()));
    when(notificationEventsHelper.findMostRecentByEventType(
             eq(PLAN_EXECUTION_ID), eq(PipelineEventType.WAITING_FOR_USER_ACTION)))
        .thenReturn(Optional.of(NotificationEventLog.builder()
                                    .planExecutionId(PLAN_EXECUTION_ID)
                                    .nodeExecutionId(waitingDedupeKey)
                                    .pipelineEventType(PipelineEventType.WAITING_FOR_USER_ACTION)
                                    .build()));
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);

    pipelineEventNotificationHandler.processPlanExecution(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, planNodeExecution, "");

    verify(notificationHelper, times(0))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.PIPELINE_RESUMED), any());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testUserActionRequiredWithRollbackDisabled() {
    GraphUpdateEventInfo graphUpdateEventInfo = GraphUpdateEventInfo.builder()
                                                    .nodeExecutionId(NODE_EXECUTION_ID)
                                                    .nodeType(NodeType.PLAN_NODE)
                                                    .status(Status.APPROVAL_WAITING)
                                                    .lastUpdatedAt(LAST_UPDATED_AT)
                                                    .build();
    NodeExecution stageNodeExecution = NodeExecution.builder()
                                           .ambiance(ambiance)
                                           .uuid(NODE_EXECUTION_ID)
                                           .startTs(LAST_UPDATED_AT)
                                           .nodeType(NodeType.PLAN_NODE.name())
                                           .executionInputConfigured(true)
                                           .build();
    when(notificationEventsHelper.getNotificationsSent(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.getNotificationRules(any(), any())).thenReturn(Collections.emptyList());
    when(notificationHelper.isEventConfiguredForNode(any(), any(), any())).thenReturn(true);
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.PIPE_DISABLE_PIPELINE_NOTIFICATIONS_ON_ROLLBACK)))
        .thenReturn(true);

    pipelineEventNotificationHandler.processStageEvents(
        PLAN_EXECUTION_ID, graphUpdateEventInfo, stageNodeExecution, "");

    // Should still send notification for non-rollback execution
    verify(notificationHelper, times(1))
        .sendNotificationEventWithLock(any(), any(), any(), eq(PipelineEventType.WAITING_FOR_USER_ACTION), any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testResolveInterventionDedupeTimestampMs_prefersGraphTs() {
    assertThat(resolveInterventionDedupeTimestampMs(99L, 1L)).isEqualTo(99L);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testResolveInterventionDedupeTimestampMs_fallsBackToNodeTsWhenGraphZero() {
    assertThat(resolveInterventionDedupeTimestampMs(0L, 42L)).isEqualTo(42L);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testResolveInterventionDedupeTimestampMs_usesWallClockWhenBothMissing() {
    long before = System.currentTimeMillis();
    long resolved = resolveInterventionDedupeTimestampMs(0L, null);
    long after = System.currentTimeMillis();
    assertThat(resolved).isGreaterThanOrEqualTo(before).isLessThanOrEqualTo(after);
  }
}
