/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution.queue;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_QUEUE_TIME;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.CIInitTaskArgs;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.queue.CIInitTaskMessageProcessorImpl;
import io.harness.ci.execution.queue.ProcessMessageResponse;
import io.harness.ci.execution.states.IntegrationStageStepPMSFacilitator;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIExecutionRepository;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CIInitTaskMessageProcessorImplTest extends CIExecutionTestBase {
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private CIBuildEnforcer buildEnforcer;
  @Mock private CIExecutionRepository ciExecutionRepository;
  @Mock private QueueExecutionUtils queueExecutionUtils;
  @Mock private IntegrationStageStepPMSFacilitator stepPMSFacilitator;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private QueueServiceClientConfig queueServiceClientConfig;

  @InjectMocks private CIInitTaskMessageProcessorImpl processor;

  private String accountId;
  private String stageExecutionId;
  private Ambiance ambiance;
  private StepParameters stepParameters;
  private Infrastructure infrastructure;
  private DequeueResponse dequeueResponse;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    accountId = "testAccount123";
    stageExecutionId = "stageExec456";

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org1");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project1");

    String stageRuntimeId = "stageRuntime789";
    ambiance = Ambiance.newBuilder()
                   .putAllSetupAbstractions(setupAbstractions)
                   .setStageExecutionId(stageExecutionId)
                   .addLevels(io.harness.pms.contracts.ambiance.Level.newBuilder()
                                  .setRuntimeId(stageRuntimeId)
                                  .setSetupId("stage1")
                                  .setIdentifier("ci_stage")
                                  .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                                   .setType("IntegrationStageStepPMS")
                                                   .setStepCategory(io.harness.pms.contracts.steps.StepCategory.STAGE)
                                                   .build())
                                  .build())
                   .build();

    infrastructure = HostedVmInfraYaml.builder()
                         .type(Infrastructure.Type.HOSTED_VM)
                         .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                                   .platform(ParameterField.createValueField(
                                       Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                                   .build())
                         .build();

    IntegrationStageStepParametersPMS integrationStageConfig =
        IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build();
    stepParameters = StageElementParameters.builder().specConfig(integrationStageConfig).build();

    CIInitTaskArgs ciInitTaskArgs = CIInitTaskArgs.builder().ambiance(ambiance).stepParameters(stepParameters).build();
    String payload = RecastOrchestrationUtils.toJson(ciInitTaskArgs);
    dequeueResponse = DequeueResponse.builder().itemId("item123").payload(payload).build();

    // Mock common config
    when(ciExecutionServiceConfig.getQueueServiceClientConfig()).thenReturn(queueServiceClientConfig);
    when(queueServiceClientConfig.getTopic()).thenReturn("ci-queue-topic");
  }

  // ==================== Tests for processMessage - shouldRun check ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenShouldRunFalse_thenUnackMessage() {
    // Given - Build should not run due to concurrency limits
    when(buildEnforcer.shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(false);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isFalse(); // unack for retry
    verify(buildEnforcer).shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any());
    verify(queueExecutionUtils, never()).publishQueueCountMetrics(any(), any());
  }

  // ==================== Tests for processMessage - FF Disabled ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFDisabled_thenUpdateStatusAndPublishMetrics() {
    // Given
    when(buildEnforcer.shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(true);
    when(ciExecutionRepository.tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId))
        .thenReturn(true);
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(false);

    CIExecutionMetadata metadata = CIExecutionMetadata.builder().queueId("queue123").build();
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenReturn(metadata);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isTrue();
    verify(buildEnforcer).shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any());
    verify(ciExecutionRepository).tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId);
    verify(queueExecutionUtils).publishQueueCountMetrics(eq(ambiance), any(Infrastructure.class));
    verify(queueExecutionUtils).isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class));
    verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());
    verify(queueExecutionUtils).publishQueueTimeMetrics(eq(ambiance), any(Infrastructure.class), eq("queue123"));
    verify(stepPMSFacilitator).sendFacilitatorResponse(ambiance, Status.RUNNING);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFDisabled_thenPublishesQueueTimeSweepingOutput() {
    // Given
    when(buildEnforcer.shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(true);
    when(ciExecutionRepository.tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId))
        .thenReturn(true);
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(false);

    CIExecutionMetadata metadata = CIExecutionMetadata.builder().queueId("queue123").build();
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenReturn(metadata);
    when(queueExecutionUtils.computeQueueTimeInMillis("queue123")).thenReturn(4200.0);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then - the queue time is persisted on a stage-scoped sweeping output instead of the deletable metadata record
    assertThat(response.getSuccess()).isTrue();
    verify(executionSweepingOutputService)
        .consume(eq(ambiance), eq(STAGE_QUEUE_TIME), any(), eq(StepOutcomeGroup.STAGE.name()));
    verify(stepPMSFacilitator).sendFacilitatorResponse(ambiance, Status.RUNNING);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFDisabledAndMetadataNull_thenAckMessage() {
    // Given
    when(buildEnforcer.shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(true);
    when(ciExecutionRepository.tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId))
        .thenReturn(true);
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(false);
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenReturn(null);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isTrue(); // ack message (aborted execution)
    verify(ciExecutionRepository).tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId);
    verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());
    verify(queueExecutionUtils).publishQueueCountMetrics(eq(ambiance), any(Infrastructure.class));
    verify(queueExecutionUtils, never()).publishQueueTimeMetrics(any(), any(), anyString());
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
  }

  // ==================== Tests for processMessage - FF Enabled ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFEnabledAndLockAcquired_thenEnqueueBuildAndSendResponse() {
    // Given
    when(buildEnforcer.shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(true);
    when(ciExecutionRepository.tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId))
        .thenReturn(true);
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);

    CIExecutionMetadata metadata = CIExecutionMetadata.builder().queueId("queue123").build();
    when(ciExecutionRepository.getExecutionMetadata(accountId, stageExecutionId)).thenReturn(metadata);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isTrue();
    verify(buildEnforcer).shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any());
    verify(queueExecutionUtils).publishQueueCountMetrics(any(Ambiance.class), any(Infrastructure.class));
    verify(ciExecutionRepository).tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId);
    verify(queueExecutionUtils).isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class));
    verify(ciExecutionRepository).getExecutionMetadata(accountId, stageExecutionId);
    verify(queueExecutionUtils).publishQueueTimeMetrics(any(Ambiance.class), any(Infrastructure.class), eq("queue123"));
    verify(stepPMSFacilitator).enqueueBuild(any(Ambiance.class), any(StepParameters.class));
    verify(stepPMSFacilitator)
        .sendFacilitatorResponse(any(Ambiance.class), eq(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED));
    verify(ciExecutionRepository, never()).updateExecutionStatus(anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFEnabledAndLockNotAcquired_thenUnackMessage() {
    // Given
    when(buildEnforcer.shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(true);
    when(ciExecutionRepository.tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId))
        .thenReturn(false);
    // Mock existing metadata to be non-null (normal case)
    CIExecutionMetadata existingMetadata = CIExecutionMetadata.builder().queueId("queue123").build();
    when(ciExecutionRepository.getExecutionMetadata(accountId, stageExecutionId)).thenReturn(existingMetadata);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isFalse(); // unack message for retry (in case other thread crashes)
    verify(buildEnforcer).shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any());
    verify(ciExecutionRepository).tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId);
    verify(ciExecutionRepository).getExecutionMetadata(accountId, stageExecutionId);
    verify(stepPMSFacilitator, never()).enqueueBuild(any(), any());
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), any());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenLockNotAcquiredAndMetadataNull_thenAckMessage() {
    // Given
    when(buildEnforcer.shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(true);
    when(ciExecutionRepository.tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId))
        .thenReturn(false);
    // Mock existing metadata to be null (aborted execution case)
    when(ciExecutionRepository.getExecutionMetadata(accountId, stageExecutionId)).thenReturn(null);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isTrue(); // ack message (aborted execution)
    verify(buildEnforcer).shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any());
    verify(ciExecutionRepository).tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId);
    verify(ciExecutionRepository).getExecutionMetadata(accountId, stageExecutionId);
    verify(stepPMSFacilitator, never()).enqueueBuild(any(), any());
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), any());
    verify(queueExecutionUtils, never()).publishQueueCountMetrics(any(), any());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFEnabledAndEnqueueFails_thenSendFacilitatorResponseAndAck() {
    // Given
    when(buildEnforcer.shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(true);
    when(ciExecutionRepository.tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId))
        .thenReturn(true);
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);

    // Mock enqueueBuild to throw exception (void method, use doThrow)
    doThrow(new RuntimeException("Enqueue failed"))
        .when(stepPMSFacilitator)
        .enqueueBuild(any(Ambiance.class), any(StepParameters.class));

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isTrue(); // ack and let task proceed
    verify(ciExecutionRepository).tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId);
    verify(stepPMSFacilitator).enqueueBuild(any(Ambiance.class), any(StepParameters.class));
    // Verify sendFacilitatorResponse was called without status (fallback)
    verify(stepPMSFacilitator).sendFacilitatorResponse(any(Ambiance.class), eq(Status.RUNNING));
  }

  // ==================== Tests for processMessage - Exception Handling ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenInvalidPayload_thenUnackMessage() {
    // Given
    DequeueResponse invalidResponse = DequeueResponse.builder().itemId("item456").payload("invalid json").build();

    // When
    ProcessMessageResponse response = processor.processMessage(invalidResponse);

    // Then
    assertThat(response.getSuccess()).isFalse(); // unack for retry
    verify(buildEnforcer, never()).shouldRun(anyString(), any(), anyString(), any());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenExceptionDuringParsing_thenUnackMessage() {
    // Given
    when(buildEnforcer.shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any()))
        .thenThrow(new RuntimeException("Parsing error"));

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isFalse(); // unack for retry
  }

  // ==================== Tests for stale message check ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenMessageIsStale_thenAckMessage() {
    // Given - message is older than 35 days
    String staleItemId = (System.currentTimeMillis() - (36L * 24 * 60 * 60 * 1000)) + "-0";
    CIInitTaskArgs ciInitTaskArgs = CIInitTaskArgs.builder().ambiance(ambiance).stepParameters(stepParameters).build();
    String payload = RecastOrchestrationUtils.toJson(ciInitTaskArgs);
    DequeueResponse staleResponse = DequeueResponse.builder().itemId(staleItemId).payload(payload).build();

    when(queueExecutionUtils.isStaleQueueMessage(staleItemId)).thenReturn(true);

    // When
    ProcessMessageResponse response = processor.processMessage(staleResponse);

    // Then
    assertThat(response.getSuccess()).isTrue(); // ack stale message
    verify(queueExecutionUtils).isStaleQueueMessage(staleItemId);
    // Verify that no further processing occurred
    verify(buildEnforcer, never()).shouldRun(anyString(), any(), anyString(), any());
    verify(ciExecutionRepository, never()).tryAcquireConcurrencyQueueMessageProcessorLock(anyString(), anyString());
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenMessageIsNotStale_thenContinueProcessing() {
    // Given - message is recent (1 hour old)
    String recentItemId = (System.currentTimeMillis() - (60 * 60 * 1000)) + "-0";
    CIInitTaskArgs ciInitTaskArgs = CIInitTaskArgs.builder().ambiance(ambiance).stepParameters(stepParameters).build();
    String payload = RecastOrchestrationUtils.toJson(ciInitTaskArgs);
    DequeueResponse recentResponse = DequeueResponse.builder().itemId(recentItemId).payload(payload).build();

    when(queueExecutionUtils.isStaleQueueMessage(recentItemId)).thenReturn(false);
    when(buildEnforcer.shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any()))
        .thenReturn(true);
    when(ciExecutionRepository.tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId))
        .thenReturn(true);
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(false);

    CIExecutionMetadata metadata = CIExecutionMetadata.builder().queueId("queue123").build();
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenReturn(metadata);

    // When
    ProcessMessageResponse response = processor.processMessage(recentResponse);

    // Then
    assertThat(response.getSuccess()).isTrue();
    verify(queueExecutionUtils).isStaleQueueMessage(recentItemId);
    // Verify that normal processing occurred
    verify(buildEnforcer).shouldRun(eq(accountId), any(Infrastructure.class), eq(ModuleType.CI.name()), any());
    verify(ciExecutionRepository).tryAcquireConcurrencyQueueMessageProcessorLock(accountId, stageExecutionId);
    verify(stepPMSFacilitator).sendFacilitatorResponse(ambiance, Status.RUNNING);
  }

  // ==================== Tests for getTopic ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetTopic() {
    // When
    String topic = processor.getTopic();

    // Then
    assertThat(topic).isEqualTo("ci-queue-topic");
    verify(ciExecutionServiceConfig).getQueueServiceClientConfig();
  }
}
