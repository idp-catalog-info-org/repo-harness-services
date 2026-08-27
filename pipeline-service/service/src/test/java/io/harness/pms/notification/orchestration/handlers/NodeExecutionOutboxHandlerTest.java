/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification.orchestration.handlers;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.OutcomeInstance;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.beans.NodeOutboxInfo;
import io.harness.engine.pms.audits.events.NodeExecutionOutboxEventConstants;
import io.harness.engine.pms.data.outcome.impl.PmsOutcomeServiceImpl;
import io.harness.execution.NodeExecution;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.outbox.PipelineEndEventKafkaSender;
import io.harness.pms.outbox.StageEndEventKafkaSender;
import io.harness.pms.outbox.StageStatusEventProducer;
import io.harness.pms.outbox.StepEndEventKafkaSender;
import io.harness.rule.Owner;
import io.harness.steps.OutputExpressionConstants;
import io.harness.utils.PmsFeatureFlagService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.kafka.clients.producer.Callback;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionOutboxHandlerTest extends CategoryTest {
  @Spy @InjectMocks NodeExecutionOutboxHandler nodeExecutionOutboxHandler;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PipelineServiceConfiguration configuration;

  private NodeStartInfo nodeStartInfo;
  private NodeUpdateInfo nodeUpdateInfo;
  @Mock PmsOutcomeServiceImpl pmsOutcomeService;
  @Mock PmsFeatureFlagService featureFlagService;
  @Mock OutboxService executionOutboxService;
  @Mock StepEndEventKafkaSender stepEndEventKafkaSender;
  @Mock StageStatusEventProducer stageStatusEventProducer;
  @Mock StageEndEventKafkaSender stageEndEventKafkaSender;
  @Mock PipelineEndEventKafkaSender pipelineEndEventKafkaSender;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    nodeStartInfo =
        NodeStartInfo.builder()
            .nodeExecution(
                NodeExecution.builder()
                    .ambiance(Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build())
                    .group("PIPELINE")
                    .build())
            .build();
    nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(
                NodeExecution.builder()
                    .ambiance(Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build())
                    .group("PIPELINE")
                    .build())
            .build();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testOnNodeStartAndUpdateEvents() {
    doReturn(nodeStartInfo.getNodeExecution().getAmbiance())
        .when(nodeExecutionService)
        .getAmbiance(nodeStartInfo.getNodeExecution());
    nodeExecutionOutboxHandler.onNodeStart(nodeStartInfo);
    verify(nodeExecutionOutboxHandler, times(1)).sendOutboxEvents(any());
    doReturn(nodeUpdateInfo.getNodeExecution().getAmbiance())
        .when(nodeExecutionService)
        .getAmbiance(nodeUpdateInfo.getNodeExecution());
    nodeExecutionOutboxHandler.onNodeStatusUpdate(nodeUpdateInfo);
    verify(nodeExecutionOutboxHandler, times(2)).sendOutboxEvents(any());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSendStepExecutionEvents() {
    // Set up default behavior: Kafka feature flags disabled
    String accountId = "testAccountId";

    Ambiance ambiance =
        Ambiance.newBuilder()
            .addLevels(Level.newBuilder()
                           .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                           .setRuntimeId("stageId")
                           .build())
            .setMetadata(ExecutionMetadata.newBuilder().build())
            .putSetupAbstractions("accountId", accountId)
            .build();
    NodeOutboxInfo nodeOutboxInfo =
        NodeOutboxInfo.builder()
            .nodeExecution(
                NodeExecution.builder()
                    .uuid("stepId")
                    .ambiance(ambiance)
                    .stepType(StepType.newBuilder().setType("ShellScript").setStepCategory(StepCategory.STEP).build())
                    .mode(ExecutionMode.SYNC)
                    .status(Status.SUCCEEDED)
                    .group("STEP")
                    .build())
            .type("STEP")
            .build();
    doReturn(nodeOutboxInfo.getNodeExecution().getAmbiance())
        .when(nodeExecutionService)
        .getAmbiance(nodeOutboxInfo.getNodeExecution());

    // When feature flag is enabled, verify Kafka sender is called
    doReturn(true)
        .when(featureFlagService)
        .isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA));
    doCallRealMethod().when(nodeExecutionOutboxHandler).sendStepExecutionEvents(any());

    Callback mockCallback = mock(Callback.class);
    doReturn(mockCallback)
        .when(stepEndEventKafkaSender)
        .createFailureHandlingCallback(any(), any(), any(), any(), any(), any(), any());
    doReturn("step-topic").when(configuration).getStepDataIngestionTopicName();

    nodeExecutionOutboxHandler.publishEndEventData(nodeOutboxInfo, ambiance);

    // Verify outcome service is called
    verify(pmsOutcomeService, times(1)).fetchOutcomeInstanceByRuntimeId(any());

    // Update verification to match the actual method signature with 6 parameters
    verify(stepEndEventKafkaSender, times(1))
        .sendEvent(eq(nodeOutboxInfo), any(Ambiance.class), any(), any(Callback.class), any(), any());
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPublishEndEventData() {
    testPublishEndEventDataForPipelineGroup();
    testPublishEndEventDataForStageGroup();
    testPublishEndEventDataForStepGroup();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testForSendStageStatus() {
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(
                NodeExecution.builder()
                    .ambiance(Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build())
                    .group("STAGE")
                    .build())
            .build();
    doReturn(nodeUpdateInfo.getNodeExecution().getAmbiance())
        .when(nodeExecutionService)
        .getAmbiance(nodeUpdateInfo.getNodeExecution());
    try (MockedStatic<AmbianceUtils> mocked = Mockito.mockStatic(AmbianceUtils.class)) {
      mocked.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn("accountId");

      when(featureFlagService.isEnabled(eq("accountId"), eq(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)))
          .thenReturn(false);
      when(featureFlagService.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED)))
          .thenReturn(true);
      nodeExecutionOutboxHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(stageStatusEventProducer, times(0)).sendEvent(any(), any());

      when(featureFlagService.isEnabled(eq("accountId"), eq(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)))
          .thenReturn(true);
      nodeExecutionOutboxHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(stageStatusEventProducer, times(1)).sendEvent(any(), any());

      when(featureFlagService.isEnabled(eq("accountId"), eq(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)))
          .thenReturn(false);
      when(featureFlagService.isEnabled(eq("accountId"), eq(FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED)))
          .thenReturn(false);
      nodeExecutionOutboxHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(stageStatusEventProducer, times(2)).sendEvent(any(), any());
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testForSendStageStatusShouldNotSendForPipelineEvent() {
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(
                NodeExecution.builder()
                    .ambiance(Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build())
                    .group("PIPELINE")
                    .build())
            .build();
    doReturn(nodeStartInfo.getNodeExecution().getAmbiance())
        .when(nodeExecutionService)
        .getAmbiance(nodeStartInfo.getNodeExecution());
    nodeExecutionOutboxHandler.onNodeStart(nodeStartInfo);
    verify(nodeExecutionOutboxHandler, times(1)).sendOutboxEvents(any());
    doReturn(nodeUpdateInfo.getNodeExecution().getAmbiance())
        .when(nodeExecutionService)
        .getAmbiance(nodeUpdateInfo.getNodeExecution());
    nodeExecutionOutboxHandler.onNodeStatusUpdate(nodeUpdateInfo);
    verify(nodeExecutionOutboxHandler, times(2)).sendOutboxEvents(any());
    verify(stageStatusEventProducer, times(0)).sendEvent(any(), any());
    try (MockedStatic<AmbianceUtils> mocked = Mockito.mockStatic(AmbianceUtils.class)) {
      mocked.when(() -> AmbianceUtils.getAccountId(any(Ambiance.class))).thenReturn("accountId");
      when(featureFlagService.isEnabled(eq("accountId"), eq(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT)))
          .thenReturn(true);
      nodeExecutionOutboxHandler.onNodeStatusUpdate(nodeUpdateInfo);
      verify(stageStatusEventProducer, times(0)).sendEvent(any(), any());
    }
  }

  private void testPublishEndEventDataForPipelineGroup() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .build();

    // Create a complete NodeExecution with status for pipeline
    NodeExecution pipelineExecution =
        NodeExecution.builder()
            .uuid("pipelineId")
            .group("PIPELINE")
            .ambiance(ambiance)
            .status(Status.SUCCEEDED) // This is important - events are sent only for certain statuses
            .build();

    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder().nodeExecution(pipelineExecution).type("PIPELINE").build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeOutboxInfo.getNodeExecution());

    // First with feature flag disabled - no Kafka actions
    when(featureFlagService.isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_PIPELINE_STAGE_END_EVENTS_TO_KAFKA)))
        .thenReturn(false);
    when(featureFlagService.isEnabled(
             eq(accountId), eq(FeatureName.PIPE_PUSH_NODE_START_AND_STATUS_UPDATE_EVENTS_TO_KAFKA)))
        .thenReturn(false);
    nodeExecutionOutboxHandler.publishEndEventData(nodeOutboxInfo, ambiance);
    verify(pipelineEndEventKafkaSender, never()).sendEvent(any(), any(), any(), any());

    // Reset mocks
    Mockito.reset(pipelineEndEventKafkaSender);

    // Now with feature flag enabled
    when(featureFlagService.isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_PIPELINE_STAGE_END_EVENTS_TO_KAFKA)))
        .thenReturn(true);

    // Setup mock callback exactly as NodeExecutionOutboxHandler would use it
    Callback mockCallback = mock(Callback.class);
    doReturn(mockCallback)
        .when(pipelineEndEventKafkaSender)
        .createFailureHandlingCallback(eq(nodeOutboxInfo), eq(ambiance),
            eq(NodeExecutionOutboxEventConstants.PIPELINE_END_FOR_KAFKA), any(), anyString(), anyString(), any());
    doReturn("pipeline-topic").when(configuration).getPipelineDataIngestionTopicName();

    // Direct stub for the sender method
    doAnswer(invocation -> null).when(pipelineEndEventKafkaSender).sendEvent(any(), any(), any(), any());

    // Call the method directly to avoid stubbed behavior
    doCallRealMethod().when(nodeExecutionOutboxHandler).publishEndEventData(any(), any());

    // Call the actual method
    nodeExecutionOutboxHandler.publishEndEventData(nodeOutboxInfo, ambiance);

    // Verify expected interactions - match the actual method signature
    verify(pipelineEndEventKafkaSender, times(1)).sendEvent(eq(nodeOutboxInfo), eq(ambiance), eq(mockCallback), any());
  }

  private void testPublishEndEventDataForStageGroup() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .build();

    // Create a complete NodeExecution with status for stage
    NodeExecution stageExecution =
        NodeExecution.builder()
            .uuid("stageId")
            .group("STAGE")
            .ambiance(ambiance)
            .status(Status.SUCCEEDED) // This is important - events are sent only for certain statuses
            .build();

    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder().nodeExecution(stageExecution).type("STAGE").build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeOutboxInfo.getNodeExecution());

    // First with feature flag disabled - no Kafka actions
    when(featureFlagService.isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_PIPELINE_STAGE_END_EVENTS_TO_KAFKA)))
        .thenReturn(false);
    when(featureFlagService.isEnabled(
             eq(accountId), eq(FeatureName.PIPE_PUSH_NODE_START_AND_STATUS_UPDATE_EVENTS_TO_KAFKA)))
        .thenReturn(false);
    nodeExecutionOutboxHandler.publishEndEventData(nodeOutboxInfo, ambiance);
    verify(stageEndEventKafkaSender, never()).sendEvent(any(), any(), any(), any());

    // Reset mocks
    Mockito.reset(stageEndEventKafkaSender);

    // Now with feature flag enabled
    when(featureFlagService.isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_PIPELINE_STAGE_END_EVENTS_TO_KAFKA)))
        .thenReturn(true);

    // Setup mock callback exactly as NodeExecutionOutboxHandler would use it
    Callback mockCallback = mock(Callback.class);
    doReturn(mockCallback)
        .when(stageEndEventKafkaSender)
        .createFailureHandlingCallback(eq(nodeOutboxInfo), eq(ambiance),
            eq(NodeExecutionOutboxEventConstants.STAGE_END_FOR_KAFKA), any(), anyString(), anyString(), any());
    doReturn("stage-topic").when(configuration).getStageDataIngestionTopicName();

    // Direct stub for the sender method
    doAnswer(invocation -> null).when(stageEndEventKafkaSender).sendEvent(any(), any(), any(), any());

    // Call the method directly to avoid stubbed behavior
    doCallRealMethod().when(nodeExecutionOutboxHandler).publishEndEventData(any(), any());

    nodeExecutionOutboxHandler.publishEndEventData(nodeOutboxInfo, ambiance);

    // Verify expected interactions
    verify(stageEndEventKafkaSender, times(1)).sendEvent(eq(nodeOutboxInfo), eq(ambiance), eq(mockCallback), any());
  }

  private void testPublishEndEventDataForStepGroup() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .build();

    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .nodeExecution(createNodeExecution("stepId", ambiance, "ShellScript",
                                            StepCategory.STEP, Status.SUCCEEDED, "STEP"))
                                        .type("STEP")
                                        .build();

    doReturn(ambiance).when(nodeExecutionService).getAmbiance(nodeOutboxInfo.getNodeExecution());

    // First with feature flag disabled - no Kafka actions
    when(featureFlagService.isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA)))
        .thenReturn(false);
    when(featureFlagService.isEnabled(
             eq(accountId), eq(FeatureName.PIPE_PUSH_NODE_START_AND_STATUS_UPDATE_EVENTS_TO_KAFKA)))
        .thenReturn(false);
    nodeExecutionOutboxHandler.publishEndEventData(nodeOutboxInfo, ambiance);
    verify(stepEndEventKafkaSender, never()).sendEvent(any(), any(), any(), any(), any(), any());

    // Reset mocks
    Mockito.reset(stepEndEventKafkaSender);
    Mockito.reset(pmsOutcomeService);

    // Now with feature flag enabled
    when(featureFlagService.isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA)))
        .thenReturn(true);

    // Setup mock outcome instances
    List<OutcomeInstance> mockOutcomeInstances = new ArrayList<>();
    doReturn(mockOutcomeInstances).when(pmsOutcomeService).fetchOutcomeInstanceByRuntimeId(anyString());

    // Setup mock callback exactly as NodeExecutionOutboxHandler would use it
    Callback mockCallback = mock(Callback.class);
    doReturn(mockCallback)
        .when(stepEndEventKafkaSender)
        .createFailureHandlingCallback(eq(nodeOutboxInfo), eq(ambiance),
            eq(NodeExecutionOutboxEventConstants.STEP_END_FOR_KAFKA), any(), anyString(), anyString(), any());
    doReturn("step-topic").when(configuration).getStepDataIngestionTopicName();

    // Direct stub for the sender method
    doAnswer(invocation -> null).when(stepEndEventKafkaSender).sendEvent(any(), any(), any(), any(), any(), any());

    // Direct call of the method under test
    doCallRealMethod().when(nodeExecutionOutboxHandler).publishEndEventData(any(), any());

    // This will now call the real publishEndEventData method
    nodeExecutionOutboxHandler.publishEndEventData(nodeOutboxInfo, ambiance);

    // Verify that pmsOutcomeService was called
    verify(pmsOutcomeService, times(1)).fetchOutcomeInstanceByRuntimeId(any());
    verify(stepEndEventKafkaSender, times(1))
        .sendEvent(eq(nodeOutboxInfo), eq(ambiance), any(), eq(mockCallback), any(), any());
  }

  private NodeExecution createNodeExecution(
      String id, Ambiance ambiance, String type, StepCategory category, Status status, String group) {
    return NodeExecution.builder()
        .uuid(id)
        .ambiance(ambiance)
        .stepType(StepType.newBuilder().setType(type).setStepCategory(category).build())
        .mode(ExecutionMode.SYNC)
        .status(status)
        .group(group)
        .build();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExtractLogUrlFromOutcomes_nullOutcomes() throws Exception {
    Method extractLogUrlMethod =
        NodeExecutionOutboxHandler.class.getDeclaredMethod("extractLogUrlFromOutcomes", List.class);
    extractLogUrlMethod.setAccessible(true);

    String logUrl = (String) extractLogUrlMethod.invoke(nodeExecutionOutboxHandler, (List<OutcomeInstance>) null);

    assertThat(logUrl).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExtractLogUrlFromOutcomes_emptyOutcomes() throws Exception {
    Method extractLogUrlMethod =
        NodeExecutionOutboxHandler.class.getDeclaredMethod("extractLogUrlFromOutcomes", List.class);
    extractLogUrlMethod.setAccessible(true);

    List<OutcomeInstance> emptyList = Collections.emptyList();
    String logUrl = (String) extractLogUrlMethod.invoke(nodeExecutionOutboxHandler, emptyList);

    assertThat(logUrl).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExtractLogUrlFromOutcomes_noLogOutcome() throws Exception {
    Method extractLogUrlMethod =
        NodeExecutionOutboxHandler.class.getDeclaredMethod("extractLogUrlFromOutcomes", List.class);
    extractLogUrlMethod.setAccessible(true);

    List<OutcomeInstance> outcomes = new ArrayList<>();
    OutcomeInstance outcomeInstance = createMockOutcomeInstance("some-other-outcome", "value", "someValue");
    outcomes.add(outcomeInstance);

    String logUrl = (String) extractLogUrlMethod.invoke(nodeExecutionOutboxHandler, outcomes);

    assertThat(logUrl).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExtractLogUrlFromOutcomes_validLogOutcome() throws Exception {
    Method extractLogUrlMethod =
        NodeExecutionOutboxHandler.class.getDeclaredMethod("extractLogUrlFromOutcomes", List.class);
    extractLogUrlMethod.setAccessible(true);

    String expectedUrl = "http://localhost:8079/blob/download?accountID=testAccount&prefix=test/logs";
    List<OutcomeInstance> outcomes = new ArrayList<>();

    outcomes.add(createMockOutcomeInstance("some-other-outcome", "value", "someValue"));
    outcomes.add(createMockOutcomeInstance(OutputExpressionConstants.LOG, OutputExpressionConstants.URL, expectedUrl));

    String logUrl = (String) extractLogUrlMethod.invoke(nodeExecutionOutboxHandler, outcomes);

    assertThat(logUrl).isEqualTo(expectedUrl);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExtractLogUrlFromOutcomes_logOutcomeNoUrl() throws Exception {
    Method extractLogUrlMethod =
        NodeExecutionOutboxHandler.class.getDeclaredMethod("extractLogUrlFromOutcomes", List.class);
    extractLogUrlMethod.setAccessible(true);

    List<OutcomeInstance> outcomes = new ArrayList<>();
    outcomes.add(createMockOutcomeInstance(OutputExpressionConstants.LOG, "someOtherField", "value"));

    String logUrl = (String) extractLogUrlMethod.invoke(nodeExecutionOutboxHandler, outcomes);

    assertThat(logUrl).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testExtractLogUrlFromOutcomes_nullUrlValue() throws Exception {
    Method extractLogUrlMethod =
        NodeExecutionOutboxHandler.class.getDeclaredMethod("extractLogUrlFromOutcomes", List.class);
    extractLogUrlMethod.setAccessible(true);

    List<OutcomeInstance> outcomes = new ArrayList<>();
    outcomes.add(createMockOutcomeInstance(OutputExpressionConstants.LOG, OutputExpressionConstants.URL, null));

    String logUrl = (String) extractLogUrlMethod.invoke(nodeExecutionOutboxHandler, outcomes);

    assertThat(logUrl).isNull();
  }

  private OutcomeInstance createMockOutcomeInstance(String name, String key, Object value) {
    OutcomeInstance mockInstance = mock(OutcomeInstance.class);
    when(mockInstance.getName()).thenReturn(name);

    PmsOutcome outcomeValue = mock(PmsOutcome.class);
    when(outcomeValue.get(key)).thenReturn(value);
    when(mockInstance.getOutcomeValue()).thenReturn(outcomeValue);

    return mockInstance;
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testShouldSendPipelineOrStageEvent_whenFeatureFlagEnabledAndFinalStatus() throws Exception {
    String accountId = "testAccount";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .build();
    Status status = Status.SUCCEEDED;
    String eventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END;

    when(featureFlagService.isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_PIPELINE_STAGE_END_EVENTS_TO_KAFKA)))
        .thenReturn(true);

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod(
        "shouldSendPipelineOrStageEvent", Ambiance.class, Status.class, String.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(nodeExecutionOutboxHandler, ambiance, status, eventType);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testShouldSendPipelineOrStageEvent_whenFeatureFlagDisabledAndFinalStatus() throws Exception {
    String accountId = "testAccount";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .build();
    Status status = Status.SUCCEEDED;
    String eventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END;

    when(featureFlagService.isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_PIPELINE_STAGE_END_EVENTS_TO_KAFKA)))
        .thenReturn(false);

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod(
        "shouldSendPipelineOrStageEvent", Ambiance.class, Status.class, String.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(nodeExecutionOutboxHandler, ambiance, status, eventType);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testShouldSendPipelineOrStageEvent_whenNodeStartEventAndFeatureFlagEnabled() throws Exception {
    String accountId = "testAccount";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .build();
    Status status = Status.RUNNING;
    String eventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_START;

    when(featureFlagService.isEnabled(
             eq(accountId), eq(FeatureName.PIPE_PUSH_NODE_START_AND_STATUS_UPDATE_EVENTS_TO_KAFKA)))
        .thenReturn(true);

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod(
        "shouldSendPipelineOrStageEvent", Ambiance.class, Status.class, String.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(nodeExecutionOutboxHandler, ambiance, status, eventType);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testShouldSendPipelineOrStageEvent_whenNodeStatusUpdateEventAndFeatureFlagEnabled() throws Exception {
    String accountId = "testAccount";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .build();
    Status status = Status.INTERVENTION_WAITING;
    String eventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_STATUS_UPDATE;

    when(featureFlagService.isEnabled(
             eq(accountId), eq(FeatureName.PIPE_PUSH_NODE_START_AND_STATUS_UPDATE_EVENTS_TO_KAFKA)))
        .thenReturn(true);

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod(
        "shouldSendPipelineOrStageEvent", Ambiance.class, Status.class, String.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(nodeExecutionOutboxHandler, ambiance, status, eventType);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testShouldSendStepEvent_whenFeatureFlagEnabledAndLeafModeAndFinalStatus() throws Exception {
    String accountId = "testAccount";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid("stepId").mode(ExecutionMode.SYNC).status(Status.SUCCEEDED).build();
    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .nodeExecution(nodeExecution)
                                        .type(NodeExecutionOutboxEventConstants.NODE_UPDATE_INFO)
                                        .build();
    Status status = Status.SUCCEEDED;
    String eventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END;

    when(featureFlagService.isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA)))
        .thenReturn(true);

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod(
        "shouldSendStepEvent", Ambiance.class, NodeOutboxInfo.class, Status.class, String.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(nodeExecutionOutboxHandler, ambiance, nodeOutboxInfo, status, eventType);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testShouldSendStepEvent_whenFeatureFlagDisabled() throws Exception {
    String accountId = "testAccount";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid("stepId").mode(ExecutionMode.SYNC).status(Status.SUCCEEDED).build();
    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .nodeExecution(nodeExecution)
                                        .type(NodeExecutionOutboxEventConstants.NODE_UPDATE_INFO)
                                        .build();
    Status status = Status.SUCCEEDED;
    String eventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END;

    when(featureFlagService.isEnabled(eq(accountId), eq(FeatureName.PIPE_PUSH_STEP_END_EVENTS_TO_KAFKA)))
        .thenReturn(false);

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod(
        "shouldSendStepEvent", Ambiance.class, NodeOutboxInfo.class, Status.class, String.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(nodeExecutionOutboxHandler, ambiance, nodeOutboxInfo, status, eventType);

    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testShouldSendStepEvent_whenNodeStartEventAndFeatureFlagEnabled() throws Exception {
    String accountId = "testAccount";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid("stepId").mode(ExecutionMode.SYNC).status(Status.RUNNING).build();
    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .nodeExecution(nodeExecution)
                                        .type(NodeExecutionOutboxEventConstants.NODE_START_INFO)
                                        .build();
    Status status = Status.RUNNING;
    String eventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_START;

    when(featureFlagService.isEnabled(
             eq(accountId), eq(FeatureName.PIPE_PUSH_NODE_START_AND_STATUS_UPDATE_EVENTS_TO_KAFKA)))
        .thenReturn(true);

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod(
        "shouldSendStepEvent", Ambiance.class, NodeOutboxInfo.class, Status.class, String.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(nodeExecutionOutboxHandler, ambiance, nodeOutboxInfo, status, eventType);

    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineEventType_nodeStart() throws Exception {
    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .type(NodeExecutionOutboxEventConstants.NODE_START_INFO)
                                        .nodeExecution(NodeExecution.builder().status(Status.RUNNING).build())
                                        .build();

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod("determineEventType", NodeOutboxInfo.class);
    method.setAccessible(true);

    String eventType = (String) method.invoke(nodeExecutionOutboxHandler, nodeOutboxInfo);

    assertThat(eventType).isEqualTo(NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_START);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineEventType_nodeEnd() throws Exception {
    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .type(NodeExecutionOutboxEventConstants.NODE_UPDATE_INFO)
                                        .nodeExecution(NodeExecution.builder().status(Status.SUCCEEDED).build())
                                        .build();

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod("determineEventType", NodeOutboxInfo.class);
    method.setAccessible(true);

    String eventType = (String) method.invoke(nodeExecutionOutboxHandler, nodeOutboxInfo);

    assertThat(eventType).isEqualTo(NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineEventType_nodeStatusUpdate() throws Exception {
    NodeOutboxInfo nodeOutboxInfo =
        NodeOutboxInfo.builder()
            .type(NodeExecutionOutboxEventConstants.NODE_UPDATE_INFO)
            .nodeExecution(NodeExecution.builder().status(Status.INTERVENTION_WAITING).build())
            .build();

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod("determineEventType", NodeOutboxInfo.class);
    method.setAccessible(true);

    String eventType = (String) method.invoke(nodeExecutionOutboxHandler, nodeOutboxInfo);

    assertThat(eventType).isEqualTo(NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_STATUS_UPDATE);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testDetermineEventType_nullForSkippedStatus() throws Exception {
    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .type(NodeExecutionOutboxEventConstants.NODE_UPDATE_INFO)
                                        .nodeExecution(NodeExecution.builder().status(Status.SKIPPED).build())
                                        .build();

    Method method = NodeExecutionOutboxHandler.class.getDeclaredMethod("determineEventType", NodeOutboxInfo.class);
    method.setAccessible(true);

    String eventType = (String) method.invoke(nodeExecutionOutboxHandler, nodeOutboxInfo);

    assertThat(eventType).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testMapNodeOutboxInfoToPipelineKafkaEvent_includesEventType() {
    String accountId = "testAccount";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", "testOrg")
                            .putSetupAbstractions("projectIdentifier", "testProject")
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("pipelineId")
                                      .status(Status.SUCCEEDED)
                                      .startTs(1000L)
                                      .createdAt(900L)
                                      .lastUpdatedAt(2000L)
                                      .build();
    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .nodeExecution(nodeExecution)
                                        .type(NodeExecutionOutboxEventConstants.NODE_UPDATE_INFO)
                                        .updatedTs(2000L)
                                        .runSequence(1)
                                        .build();
    String eventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END;

    var pipelineKafkaEvent =
        io.harness.pms.notification.orchestration.NodeExecutionEventUtils.mapNodeOutboxInfoToPipelineKafkaEvent(
            nodeOutboxInfo, ambiance, eventType);

    assertThat(pipelineKafkaEvent).isNotNull();
    assertThat(pipelineKafkaEvent.getNodeEventType()).isEqualTo(eventType);
    assertThat(pipelineKafkaEvent.getStatus()).isEqualTo(Status.SUCCEEDED.name());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testMapNodeOutboxInfoToStageKafkaEvent_includesEventType() {
    String accountId = "testAccount";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", "testOrg")
                            .putSetupAbstractions("projectIdentifier", "testProject")
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("stageId")
                                      .identifier("stage1")
                                      .status(Status.SUCCEEDED)
                                      .startTs(1000L)
                                      .createdAt(900L)
                                      .lastUpdatedAt(2000L)
                                      .build();
    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .nodeExecution(nodeExecution)
                                        .type(NodeExecutionOutboxEventConstants.NODE_UPDATE_INFO)
                                        .updatedTs(2000L)
                                        .runSequence(1)
                                        .build();
    String eventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END;

    var stageKafkaEvent =
        io.harness.pms.notification.orchestration.NodeExecutionEventUtils.mapNodeOutboxInfoToStageKafkaEvent(
            nodeOutboxInfo, ambiance, eventType);

    assertThat(stageKafkaEvent).isNotNull();
    assertThat(stageKafkaEvent.getNodeEventType()).isEqualTo(eventType);
    assertThat(stageKafkaEvent.getStatus()).isEqualTo(Status.SUCCEEDED.name());
    assertThat(stageKafkaEvent.getStageIdentifier()).isEqualTo("stage1");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testMapNodeOutboxInfoToStepEndEvent_includesEventType() {
    String accountId = "testAccount";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setMetadata(ExecutionMetadata.newBuilder().build())
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", "testOrg")
                            .putSetupAbstractions("projectIdentifier", "testProject")
                            .addLevels(Level.newBuilder().setIdentifier("stage1").setGroup("STAGE").build())
                            .build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("stepId")
                                      .identifier("step1")
                                      .name("Test Step")
                                      .status(Status.SUCCEEDED)
                                      .stepType(StepType.newBuilder().setType("ShellScript").build())
                                      .startTs(1000L)
                                      .createdAt(900L)
                                      .lastUpdatedAt(2000L)
                                      .retryIds(Collections.emptyList())
                                      .build();
    NodeOutboxInfo nodeOutboxInfo = NodeOutboxInfo.builder()
                                        .nodeExecution(nodeExecution)
                                        .type(NodeExecutionOutboxEventConstants.NODE_UPDATE_INFO)
                                        .updatedTs(2000L)
                                        .runSequence(1)
                                        .build();
    String eventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_START;
    String logUrl = "http://logs.example.com/step1";

    var stepEndEvent =
        io.harness.pms.notification.orchestration.NodeExecutionEventUtils.mapNodeOutboxInfoToStepEndEvent(
            nodeOutboxInfo, ambiance, Collections.emptyList(), logUrl, eventType);

    assertThat(stepEndEvent).isNotNull();
    assertThat(stepEndEvent.getNodeEventType()).isEqualTo(eventType);
    assertThat(stepEndEvent.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepEndEvent.getStepIdentifier()).isEqualTo("step1");
    assertThat(stepEndEvent.getLogUrl()).isEqualTo(logUrl);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testKafkaEventTypes_preserveEventTypeThroughBuilder() {
    // Test PipelineKafkaEvent
    String pipelineEventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_END;
    var pipelineEvent = io.harness.engine.pms.audits.events.PipelineKafkaEvent.builder()
                            .accountIdentifier("acc1")
                            .orgIdentifier("org1")
                            .projectIdentifier("proj1")
                            .pipelineIdentifier("pipe1")
                            .planExecutionId("exec1")
                            .status("SUCCEEDED")
                            .nodeEventType(pipelineEventType)
                            .build();
    assertThat(pipelineEvent.getNodeEventType()).isEqualTo(pipelineEventType);

    // Test StageKafkaEvent
    String stageEventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_STATUS_UPDATE;
    var stageEvent = io.harness.engine.pms.audits.events.StageKafkaEvent.builder()
                         .accountIdentifier("acc1")
                         .orgIdentifier("org1")
                         .projectIdentifier("proj1")
                         .pipelineIdentifier("pipe1")
                         .planExecutionId("exec1")
                         .stageExecutionId("stage1")
                         .stageIdentifier("stageId")
                         .stageName("Test Stage")
                         .stageType("Deployment")
                         .status("RUNNING")
                         .nodeEventType(stageEventType)
                         .build();
    assertThat(stageEvent.getNodeEventType()).isEqualTo(stageEventType);

    // Test StepEndEvent
    String stepEventType = NodeExecutionOutboxEventConstants.EVENT_TYPE_NODE_START;
    var stepEvent = io.harness.engine.pms.audits.events.StepEndEvent.builder()
                        .accountIdentifier("acc1")
                        .orgIdentifier("org1")
                        .projectIdentifier("proj1")
                        .pipelineIdentifier("pipe1")
                        .planExecutionId("exec1")
                        .stageExecutionId("stage1")
                        .stageIdentifier("stageId")
                        .stepExecutionId("step1")
                        .stepIdentifier("stepId")
                        .stepName("Test Step")
                        .stepType("ShellScript")
                        .status(Status.RUNNING)
                        .nodeEventType(stepEventType)
                        .build();
    assertThat(stepEvent.getNodeEventType()).isEqualTo(stepEventType);
  }
}
