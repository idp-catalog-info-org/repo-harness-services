/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.rule.OwnerRule.MOHD_FAIZ;

import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanService;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.eventsframework.EventsFrameworkKafkaTopicResolver;
import io.harness.execution.NodeExecution;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.SendGitStatusConfig;
import io.harness.pms.contracts.stage.StageStatusEvent;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class StageStatusEventProducerTest extends CategoryTest {
  @Mock private PmsFeatureFlagService featureFlagService;
  @Mock private PlanService planService;
  @Mock private HKafkaProtoProducer hKafkaProtoProducer;
  @InjectMocks private StageStatusEventProducer stageStatusEventProducer;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String PLAN_ID = "planId";
  private static final String NODE_SETUP_ID = "nodeSetupId";
  private static final String NODE_EXECUTION_ID = "nodeExecutionId";
  private static final String TOPIC = "stageStatusTopic";
  private AutoCloseable closeable;

  @Before
  public void setUp() {
    closeable = MockitoAnnotations.openMocks(this);
  }

  @After
  public void releaseMocks() throws Exception {
    closeable.close();
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .setPlanId(PLAN_ID)
        .setPlanExecutionId("planExecutionId")
        .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(PIPELINE_ID).build())
        .build();
  }

  private NodeOutboxInfo buildNodeOutboxInfo() {
    NodeExecution nodeExecution = mock(NodeExecution.class);
    when(nodeExecution.getUuid()).thenReturn(NODE_EXECUTION_ID);
    when(nodeExecution.getStatus()).thenReturn(Status.SUCCEEDED);
    return NodeOutboxInfo.builder().nodeExecution(nodeExecution).type("STAGE").build();
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testSendEvent_WhenBothFlagsDisabled_DoesNotSend() {
    on(stageStatusEventProducer).set("hKafkaProtoProducer", Optional.of(hKafkaProtoProducer));
    Ambiance ambiance = buildAmbiance();

    try (MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class)) {
      ambianceUtils.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn(ACCOUNT_ID);
      when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);
      when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
          .thenReturn(true);

      stageStatusEventProducer.sendEvent(buildNodeOutboxInfo(), ambiance);

      verify(hKafkaProtoProducer, never()).send(anyString(), any(), any(), anyString());
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testSendEvent_WhenKafkaProducerEmpty_DoesNotSend() {
    on(stageStatusEventProducer).set("hKafkaProtoProducer", Optional.empty());
    Ambiance ambiance = buildAmbiance();

    try (MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class)) {
      ambianceUtils.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn(ACCOUNT_ID);
      when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(true);
      when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
          .thenReturn(false);

      stageStatusEventProducer.sendEvent(buildNodeOutboxInfo(), ambiance);

      verify(hKafkaProtoProducer, never()).send(anyString(), any(), any(), anyString());
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testSendEvent_WhenOnlyGitOpsFlagEnabledAndSendGitStatusEnabled_Sends() {
    on(stageStatusEventProducer).set("hKafkaProtoProducer", Optional.of(hKafkaProtoProducer));
    Ambiance ambiance = buildAmbiance();
    PlanNode planNode =
        PlanNode.builder().sendGitStatus(SendGitStatusConfig.newBuilder().setEnabled(true).build()).build();

    try (MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class);
         MockedStatic<EventsFrameworkKafkaTopicResolver> topicResolver =
             mockStatic(EventsFrameworkKafkaTopicResolver.class)) {
      ambianceUtils.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn(ACCOUNT_ID);
      ambianceUtils.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn(ORG_ID);
      ambianceUtils.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn(PROJECT_ID);
      ambianceUtils.when(() -> AmbianceUtils.obtainCurrentSetupId(ambiance)).thenReturn(NODE_SETUP_ID);
      when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);
      when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
          .thenReturn(false);
      when(planService.fetchNode(PLAN_ID, NODE_SETUP_ID)).thenReturn(planNode);
      topicResolver.when(EventsFrameworkKafkaTopicResolver::getPipelineStageStatusTopic).thenReturn(TOPIC);

      stageStatusEventProducer.sendEvent(buildNodeOutboxInfo(), ambiance);

      verify(hKafkaProtoProducer, times(1)).send(eq(TOPIC), any(StageStatusEvent.class), any(), anyString());
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testSendEvent_WhenSendStatusToGitFlagEnabledAndSendGitStatusEnabled_Sends() {
    on(stageStatusEventProducer).set("hKafkaProtoProducer", Optional.of(hKafkaProtoProducer));
    Ambiance ambiance = buildAmbiance();
    PlanNode planNode =
        PlanNode.builder().sendGitStatus(SendGitStatusConfig.newBuilder().setEnabled(true).build()).build();

    try (MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class);
         MockedStatic<EventsFrameworkKafkaTopicResolver> topicResolver =
             mockStatic(EventsFrameworkKafkaTopicResolver.class)) {
      ambianceUtils.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn(ACCOUNT_ID);
      ambianceUtils.when(() -> AmbianceUtils.getOrgIdentifier(ambiance)).thenReturn(ORG_ID);
      ambianceUtils.when(() -> AmbianceUtils.getProjectIdentifier(ambiance)).thenReturn(PROJECT_ID);
      ambianceUtils.when(() -> AmbianceUtils.obtainCurrentSetupId(ambiance)).thenReturn(NODE_SETUP_ID);
      when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(true);
      when(planService.fetchNode(PLAN_ID, NODE_SETUP_ID)).thenReturn(planNode);
      topicResolver.when(EventsFrameworkKafkaTopicResolver::getPipelineStageStatusTopic).thenReturn(TOPIC);

      stageStatusEventProducer.sendEvent(buildNodeOutboxInfo(), ambiance);

      verify(hKafkaProtoProducer, times(1)).send(eq(TOPIC), any(StageStatusEvent.class), any(), anyString());
    }
  }

  @Test
  @Owner(developers = MOHD_FAIZ)
  @Category(UnitTests.class)
  public void testSendEvent_WhenSendGitStatusNull_DoesNotSend() {
    on(stageStatusEventProducer).set("hKafkaProtoProducer", Optional.of(hKafkaProtoProducer));
    Ambiance ambiance = buildAmbiance();
    PlanNode planNode = PlanNode.builder().build();

    try (MockedStatic<AmbianceUtils> ambianceUtils = mockStatic(AmbianceUtils.class)) {
      ambianceUtils.when(() -> AmbianceUtils.getAccountId(ambiance)).thenReturn(ACCOUNT_ID);
      ambianceUtils.when(() -> AmbianceUtils.obtainCurrentSetupId(ambiance)).thenReturn(NODE_SETUP_ID);
      when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)).thenReturn(false);
      when(featureFlagService.isEnabled(ACCOUNT_ID, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED))
          .thenReturn(false);
      when(planService.fetchNode(PLAN_ID, NODE_SETUP_ID)).thenReturn(planNode);

      stageStatusEventProducer.sendEvent(buildNodeOutboxInfo(), ambiance);

      verify(hKafkaProtoProducer, never()).send(anyString(), any(), any(), anyString());
    }
  }
}
