/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import static io.harness.beans.FeatureName.PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.ORCHESTRATION_LOG;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.observers.NodeStartInfo;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.StepDetailsUpdateInfo;
import io.harness.entity.eventlog.OrchestrationEventLog;
import io.harness.eventsframework.EventsFrameworkConfiguration;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.kafka.producers.HKafkaProtoProducer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.ExecutionContext;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.contracts.visualisation.log.OrchestrationLogEvent;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.ImmutableMap;
import java.sql.Date;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import javax.cache.Cache;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.redisson.client.RedisException;

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationLogPublisherTest extends OrchestrationTestBase {
  private static final String planExecutionId = generateUuid();
  private static final String nodeExecutionId = generateUuid();
  private static final String accountId = generateUuid();
  private static final OrchestrationLogEvent orchestrationLogEvent =
      OrchestrationLogEvent.newBuilder().setPlanExecutionId(planExecutionId).build();

  @Mock private OrchestrationEventLogRepository repository;
  @Mock private Producer producer;
  @InjectMocks private OrchestrationLogPublisher publisher;
  @Mock Cache<String, Long> orchestrationLogCache;
  @Mock OrchestrationLogConfiguration orchestrationLogConfiguration;
  @Mock PlanExecutionService planExecutionService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock EventsFrameworkConfiguration eventsFrameworkConfiguration;
  @Mock HKafkaProtoProducer hKafkaProtoProducer;

  @Before
  public void setUp() throws IllegalAccessException {
    FieldUtils.writeField(publisher, "producer", producer, true);
    FieldUtils.writeField(publisher, "orchestrationEventLogRepository", repository, true);
    when(producer.send(any())).thenReturn(null);
    when(orchestrationLogCache.get(any())).thenReturn(5L);
    when(orchestrationLogConfiguration.getOrchestrationLogBatchSize()).thenReturn(1);

    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                            .build();
    when(planExecutionService.getWithFieldsIncluded(any(), any()))
        .thenReturn(PlanExecution.builder().ambiance(ambiance).build());
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_REMOVE_SAVE_ORCHESTRATION_LOG_EVENTS))
        .thenReturn(false);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdate() {
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(getNodeExecution()).build();
    OrchestrationEventLog orchestrationEventLog =
        getOrchestrationEventLog(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    publisher.onNodeStatusUpdate(nodeUpdateInfo);
    shouldTestOnNodeInternally(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testOrchestrationEventSave() {
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(nodeExecutionId)
                               .executionContext(ExecutionContext.newBuilder()
                                                     .setPlanExecutionId(planExecutionId)
                                                     .putSetupAbstractions("accountId", accountId)
                                                     .build())
                               .build())
            .build();
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_REMOVE_SAVE_ORCHESTRATION_LOG_EVENTS))
        .thenReturn(true);

    publisher.onNodeStatusUpdate(nodeUpdateInfo);
    verify(repository, times(0)).save(any());
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void onPlanStatusUpdate() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(planExecutionId)
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    OrchestrationEventLog orchestrationEventLog =
        getOrchestrationEventLog(OrchestrationEventType.PLAN_EXECUTION_STATUS_UPDATE);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    publisher.onPlanStatusUpdate(ambiance);
    shouldTestOnNodeInternally(OrchestrationEventType.PLAN_EXECUTION_STATUS_UPDATE);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnNodeUpdate() {
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(getNodeExecution()).build();
    OrchestrationEventLog orchestrationEventLog =
        getOrchestrationEventLog(OrchestrationEventType.NODE_EXECUTION_UPDATE);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    publisher.onNodeUpdate(nodeUpdateInfo);
    shouldTestOnNodeInternally(OrchestrationEventType.NODE_EXECUTION_UPDATE);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void onStepDetailsUpdate() {
    StepDetailsUpdateInfo stepDetailsUpdateInfo =
        StepDetailsUpdateInfo.builder().planExecutionId(planExecutionId).nodeExecutionId(nodeExecutionId).build();

    OrchestrationEventLog orchestrationEventLog = getOrchestrationEventLog(OrchestrationEventType.STEP_DETAILS_UPDATE);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    publisher.onStepDetailsUpdate(stepDetailsUpdateInfo);
    shouldTestOnNodeInternally(OrchestrationEventType.STEP_DETAILS_UPDATE);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyOnStepInputsAdd() {
    StepDetailsUpdateInfo stepDetailsUpdateInfo =
        StepDetailsUpdateInfo.builder().planExecutionId(planExecutionId).nodeExecutionId(nodeExecutionId).build();

    OrchestrationEventLog orchestrationEventLog = getOrchestrationEventLog(OrchestrationEventType.STEP_INPUTS_UPDATE);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    publisher.onStepInputsAdd(stepDetailsUpdateInfo);
    shouldTestOnNodeInternally(OrchestrationEventType.STEP_INPUTS_UPDATE);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void verifyOnNodeStart() {
    NodeStartInfo nodeStartInfo =
        NodeStartInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .uuid(nodeExecutionId)
                               .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                               .build())
            .build();

    OrchestrationEventLog orchestrationEventLog = getOrchestrationEventLog(OrchestrationEventType.NODE_EXECUTION_START);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    publisher.onNodeStart(nodeStartInfo);
    shouldTestOnNodeInternally(OrchestrationEventType.NODE_EXECUTION_START);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSendLogEvent() {
    publisher.sendLogEvent(planExecutionId, accountId);

    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(producer).send(messageArgumentCaptor.capture());

    Message message = messageArgumentCaptor.getValue();
    assertThat(message.getData()).isEqualTo(orchestrationLogEvent.toByteString());
    assertThat(message.getMetadataMap()).containsOnly(Map.entry("planExecutionId", planExecutionId));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldSendLogEventViaKafkaWhenKafkaEnabled() throws IllegalAccessException {
    // Setup: Kafka is enabled and producer is present
    FieldUtils.writeField(publisher, "eventsFrameworkConfiguration", eventsFrameworkConfiguration, true);
    FieldUtils.writeField(publisher, "hKafkaProtoProducer", Optional.of(hKafkaProtoProducer), true);
    when(eventsFrameworkConfiguration.isShouldUseKafka()).thenReturn(true);
    when(pmsFeatureFlagHelper.isEnabled(accountId, PIPE_SHOULD_ENABLE_PMS_SDK_KAFKA_STREAMING)).thenReturn(true);

    publisher.sendLogEvent(planExecutionId, accountId);

    // Verify Kafka producer is called with correct parameters
    ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<OrchestrationLogEvent> eventCaptor = ArgumentCaptor.forClass(OrchestrationLogEvent.class);
    ArgumentCaptor<Map> metadataCaptor = ArgumentCaptor.forClass(Map.class);
    verify(hKafkaProtoProducer).send(topicCaptor.capture(), eventCaptor.capture(), metadataCaptor.capture());

    assertThat(topicCaptor.getValue()).isEqualTo(ORCHESTRATION_LOG);
    assertThat(eventCaptor.getValue().getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(metadataCaptor.getValue()).containsEntry("planExecutionId", planExecutionId);

    // Verify Redis producer is NOT called
    verify(producer, times(0)).send(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldFallbackToRedisWhenKafkaProducerNotPresent() throws IllegalAccessException {
    // Setup: Kafka is enabled but producer is NOT present
    FieldUtils.writeField(publisher, "eventsFrameworkConfiguration", eventsFrameworkConfiguration, true);
    FieldUtils.writeField(publisher, "hKafkaProtoProducer", Optional.empty(), true);
    when(eventsFrameworkConfiguration.isShouldUseKafka()).thenReturn(true);

    publisher.sendLogEvent(planExecutionId, accountId);

    // Verify Kafka producer is NOT called
    verify(hKafkaProtoProducer, times(0)).send(any(), any(), any());

    // Verify fallback to Redis producer
    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(producer).send(messageArgumentCaptor.capture());

    Message message = messageArgumentCaptor.getValue();
    assertThat(message.getData()).isEqualTo(orchestrationLogEvent.toByteString());
    assertThat(message.getMetadataMap()).containsEntry("planExecutionId", planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldUseRedisWhenKafkaDisabled() throws IllegalAccessException {
    // Setup: Kafka is disabled
    FieldUtils.writeField(publisher, "eventsFrameworkConfiguration", eventsFrameworkConfiguration, true);
    FieldUtils.writeField(publisher, "hKafkaProtoProducer", Optional.of(hKafkaProtoProducer), true);
    when(eventsFrameworkConfiguration.isShouldUseKafka()).thenReturn(false);

    publisher.sendLogEvent(planExecutionId, accountId);

    // Verify Kafka producer is NOT called
    verify(hKafkaProtoProducer, times(0)).send(any(), any(), any());

    // Verify Redis producer is called
    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(producer).send(messageArgumentCaptor.capture());

    Message message = messageArgumentCaptor.getValue();
    assertThat(message.getData()).isEqualTo(orchestrationLogEvent.toByteString());
    assertThat(message.getMetadataMap()).containsEntry("planExecutionId", planExecutionId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldSendLogEventWithCorrectOrchestrationLogEvent() throws IllegalAccessException {
    String testPlanExecutionId = "test-plan-execution-id";
    String testAccountId = "test-account-id";

    publisher.sendLogEvent(testPlanExecutionId, testAccountId);

    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(producer).send(messageArgumentCaptor.capture());

    Message message = messageArgumentCaptor.getValue();
    OrchestrationLogEvent expectedEvent =
        OrchestrationLogEvent.newBuilder().setPlanExecutionId(testPlanExecutionId).build();
    assertThat(message.getData()).isEqualTo(expectedEvent.toByteString());
    assertThat(message.getMetadataMap()).containsEntry("planExecutionId", testPlanExecutionId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shouldTestExceptionInOrchestrationCache() {
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(getNodeExecution()).build();
    OrchestrationEventLog orchestrationEventLog =
        getOrchestrationEventLog(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);

    when(repository.save(any())).thenReturn(orchestrationEventLog);

    doThrow(new RedisException()).when(orchestrationLogCache).get(any());
    publisher.onNodeStatusUpdate(nodeUpdateInfo);
    ArgumentCaptor<OrchestrationEventLog> argumentCaptor = ArgumentCaptor.forClass(OrchestrationEventLog.class);
    verify(repository, times(1)).save(argumentCaptor.capture());
    OrchestrationEventLog eventLog = argumentCaptor.getValue();
    assertThat(eventLog.getNodeExecutionId()).isEqualTo(nodeExecutionId);
    assertThat(eventLog.getPlanExecutionId()).isEqualTo(planExecutionId);
    assertThat(eventLog.getOrchestrationEventType()).isEqualTo(OrchestrationEventType.NODE_EXECUTION_STATUS_UPDATE);
    verify(producer, times(0)).send(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void onPipelineInfoUpdate() {
    OrchestrationEventLog orchestrationEventLog = getOrchestrationEventLog(OrchestrationEventType.PIPELINE_INFO_UPDATE);
    when(repository.save(any())).thenReturn(orchestrationEventLog);
    publisher.onPipelineInfoUpdate(planExecutionId);
    shouldTestOnNodeInternally(OrchestrationEventType.PIPELINE_INFO_UPDATE);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void onStageInfoUpdate() {
    OrchestrationEventLog orchestrationEventLog = getOrchestrationEventLog(OrchestrationEventType.STAGE_INFO_UPDATE);
    when(repository.save(any())).thenReturn(orchestrationEventLog);
    publisher.onStageInfoUpdate(planExecutionId, nodeExecutionId);
    shouldTestOnNodeInternally(OrchestrationEventType.STAGE_INFO_UPDATE);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldSkipNonStageNodesAndStepInputsWhenCdcGraphEnabled() {
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder()
                                     .putFeatureFlagToValueMap(FeatureName.PIPE_USE_CDC_BASED_GRAPH.name(), true)
                                     .build();
    when(planExecutionService.getWithFieldsIncluded(any(), any()))
        .thenReturn(
            PlanExecution.builder().metadata(metadata).setupAbstractions(Map.of("accountId", accountId)).build());
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_USE_CDC_BASED_GRAPH)).thenReturn(true);

    // non-stage node (STEP category) — should not publish
    NodeUpdateInfo nonStageNodeUpdate =
        NodeUpdateInfo.builder().nodeExecution(getNodeExecution()).build(); // no stepType → not stage-level
    publisher.onNodeStatusUpdate(nonStageNodeUpdate);
    publisher.onNodeUpdate(nonStageNodeUpdate);

    NodeStartInfo nonStageNodeStart = NodeStartInfo.builder().nodeExecution(getNodeExecution()).build();
    publisher.onNodeStart(nonStageNodeStart);

    // step inputs — always skipped when CDC is enabled
    StepDetailsUpdateInfo stepDetailsUpdateInfo =
        StepDetailsUpdateInfo.builder().planExecutionId(planExecutionId).nodeExecutionId(nodeExecutionId).build();
    publisher.onStepInputsAdd(stepDetailsUpdateInfo);

    verify(repository, times(0)).save(any());
    verify(producer, times(0)).send(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldPublishForStageLevelNodeWhenCdcGraphEnabled() {
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder()
                                     .putFeatureFlagToValueMap(FeatureName.PIPE_USE_CDC_BASED_GRAPH.name(), true)
                                     .build();
    when(planExecutionService.getWithFieldsIncluded(any(), any()))
        .thenReturn(
            PlanExecution.builder().metadata(metadata).setupAbstractions(Map.of("accountId", accountId)).build());
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_USE_CDC_BASED_GRAPH)).thenReturn(true);

    StepType stageStepType = StepType.newBuilder().setStepCategory(StepCategory.STAGE).build();
    NodeExecution stageNode = NodeExecution.builder()
                                  .uuid(nodeExecutionId)
                                  .stepType(stageStepType)
                                  .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                                  .status(Status.SUCCEEDED)
                                  .build();
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(stageNode).build();

    publisher.onNodeStatusUpdate(nodeUpdateInfo);

    // CDC is active but it's a stage node — should publish a log event
    verify(producer, times(1)).send(any());
    verify(repository, times(0)).save(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldResumePublishingWhenCdcFfRolledBack() {
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder()
                                     .putFeatureFlagToValueMap(FeatureName.PIPE_USE_CDC_BASED_GRAPH.name(), true)
                                     .build();
    when(planExecutionService.getWithFieldsIncluded(any(), any()))
        .thenReturn(
            PlanExecution.builder().metadata(metadata).setupAbstractions(Map.of("accountId", accountId)).build());
    // CDC FF is now OFF globally — execution was started with it ON, but FF rolled back
    when(pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_USE_CDC_BASED_GRAPH)).thenReturn(false);

    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(getNodeExecution()).build();
    publisher.onNodeStatusUpdate(nodeUpdateInfo);

    // CDC check returns false (FF rolled back), falls through to regular path → saves event
    verify(repository, times(1)).save(any());
  }

  private void shouldTestOnNodeInternally(OrchestrationEventType eventType) {
    ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
    verify(producer).send(messageArgumentCaptor.capture());

    Message message = messageArgumentCaptor.getValue();
    assertThat(message.getData()).isEqualTo(orchestrationLogEvent.toByteString());
    assertThat(message.getMetadataMap()).isEqualTo(ImmutableMap.of("planExecutionId", planExecutionId));
  }

  private OrchestrationEventLog getOrchestrationEventLog(OrchestrationEventType eventType) {
    return OrchestrationEventLog.builder()
        .createdAt(System.currentTimeMillis())
        .nodeExecutionId(nodeExecutionId)
        .orchestrationEventType(eventType)
        .planExecutionId(planExecutionId)
        .validUntil(Date.from(OffsetDateTime.now().plus(Duration.ofDays(14)).toInstant()))
        .build();
  }

  private NodeExecution getNodeExecution() {
    return NodeExecution.builder()
        .uuid(nodeExecutionId)
        .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
        .status(Status.SUCCEEDED)
        .build();
  }
}
