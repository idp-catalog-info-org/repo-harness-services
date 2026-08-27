/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution.queue;

import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;
import static io.harness.rule.OwnerRule.SHUBHAM_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.CIInitTaskArgs;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.GlobalQueueingConfig;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.vm.intfc.VmInitializeUtils;
import io.harness.ci.execution.queue.CICapacityTaskMessageProcessorImpl;
import io.harness.ci.execution.queue.ProcessMessageResponse;
import io.harness.ci.execution.states.IntegrationStageStepPMSFacilitator;
import io.harness.ci.executionplan.base.CIExecutionTestBase;
import io.harness.delegate.RunnerRequest;
import io.harness.delegate.beans.ci.vm.runner.CapacityReservationRequest;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIExecutionRepository;
import io.harness.rule.Owner;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.CILicenseUsageUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CI)
public class CICapacityTaskMessageProcessorImplTest extends CIExecutionTestBase {
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private CIExecutionRepository ciExecutionRepository;
  @Mock private QueueExecutionUtils queueExecutionUtils;
  @Mock private IntegrationStageStepPMSFacilitator stepPMSFacilitator;
  @Mock private AsyncWaitEngine asyncWaitEngine;
  @Mock private RunnerRequestBuilder runnerRequestBuilder;
  @Mock private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Mock private VmInitializeUtils vmInitializeUtils;
  @Mock private VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Mock private CILicenseUsageUtils ciLicenseUsageUtils;
  @Mock private GlobalQueueingConfig globalQueueingConfig;
  @Mock private QueueServiceClientConfig queueServiceClientConfig;
  @Mock private CILicenseService ciLicenseService;

  @InjectMocks private CICapacityTaskMessageProcessorImpl processor;

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
                   .setPlanExecutionId("planExec999")
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
    when(ciExecutionServiceConfig.getGlobalQueueingConfig()).thenReturn(globalQueueingConfig);
    when(globalQueueingConfig.getCapacityTaskRetryIntervalMs()).thenReturn(60_000L);
    when(globalQueueingConfig.getCapacityTaskTimeoutMs()).thenReturn(null);
    when(globalQueueingConfig.getCapacityTaskMaxWaitTimeMs()).thenReturn(null);
    when(ciExecutionServiceConfig.getQueueServiceClientConfig()).thenReturn(queueServiceClientConfig);
    when(queueServiceClientConfig.getTopic()).thenReturn("ci");
  }

  // ==================== Tests for processMessage - FF Disabled ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFDisabled_thenUpdateStatusAndPublishMetrics() {
    // Given
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(false);
    CIExecutionMetadata metadata = CIExecutionMetadata.builder().queueId("queue123").build();
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenReturn(metadata);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isTrue();
    verify(queueExecutionUtils).isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class));
    verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());
    verify(queueExecutionUtils).publishQueueCountMetrics(eq(ambiance), any(Infrastructure.class));
    verify(queueExecutionUtils).publishGlobalQueueTimeMetrics(eq(ambiance), any(Infrastructure.class), eq("queue123"));
    verify(stepPMSFacilitator).sendFacilitatorResponse(ambiance, Status.RUNNING);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFDisabledAndMetadataNull_thenAckMessage() {
    // Given
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(false);
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenReturn(null);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isTrue();
    verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());
    verify(queueExecutionUtils).publishQueueCountMetrics(eq(ambiance), any(Infrastructure.class));
    verify(queueExecutionUtils, never()).publishGlobalQueueTimeMetrics(any(), any(), anyString());
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
  }

  // ==================== Tests for processMessage - FF Enabled ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFEnabledAndLockAcquired_thenExecuteCapacityTask() {
    // Given
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);

    when(ciExecutionRepository.tryAcquireCapacityTaskLock(
             eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L)))
        .thenReturn(true);

    // Mock capacity task execution
    when(vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(any(), any(), any()))
        .thenReturn(Pair.of("poolId123", new ArrayList<>()));
    when(vmInitializeUtils.getBuildTags(any())).thenReturn(new HashMap<>());
    when(vmInitializeUtils.isCIFreeLicense(anyString(), anyString())).thenReturn(false);
    when(ciLicenseUsageUtils.getResourceClass(anyString(), any(HostedVmInfraYaml.class), eq(false)))
        .thenReturn(Optional.of("STANDARD"));
    when(vmInitializeTaskParamsBuilder.getVMConfig(any(), any(), any())).thenReturn(null);

    RunnerRequest runnerRequest = mock(RunnerRequest.class);
    when(runnerRequestBuilder.buildCapacityRequestWithPoolSpec(any(), any(), any(), any(), eq(300_000L)))
        .thenReturn(runnerRequest);
    when(ciDelegateTaskExecutor.submitTask(runnerRequest)).thenReturn("taskId789");

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isFalse(); // unack for notification
    verify(queueExecutionUtils).isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class));
    verify(ciExecutionRepository)
        .tryAcquireCapacityTaskLock(eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L));
    verify(ciDelegateTaskExecutor).submitTask(runnerRequest);
    verify(asyncWaitEngine).waitForAllOn(any(), eq(null), eq(List.of("taskId789")), eq(0L));
    verify(ciExecutionRepository, never()).updateExecutionStatus(anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenCapacityTaskTimeoutConfigured_thenUseConfiguredValue() {
    // Given - capacityTaskTimeoutMs is configured to a custom value
    long configuredTimeoutMs = 600_000L; // 10 minutes
    when(globalQueueingConfig.getCapacityTaskTimeoutMs()).thenReturn(configuredTimeoutMs);

    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);
    when(ciExecutionRepository.tryAcquireCapacityTaskLock(
             eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L)))
        .thenReturn(true);
    when(vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(any(), any(), any()))
        .thenReturn(Pair.of("poolId123", new ArrayList<>()));
    when(vmInitializeUtils.getBuildTags(any())).thenReturn(new HashMap<>());
    when(ciLicenseUsageUtils.getResourceClass(anyString(), any(HostedVmInfraYaml.class), eq(false)))
        .thenReturn(Optional.of("STANDARD"));
    when(vmInitializeTaskParamsBuilder.getVMConfig(any(), any(), any())).thenReturn(null);

    RunnerRequest runnerRequest = mock(RunnerRequest.class);
    when(runnerRequestBuilder.buildCapacityRequestWithPoolSpec(any(), any(), any(), any(), eq(configuredTimeoutMs)))
        .thenReturn(runnerRequest);
    when(ciDelegateTaskExecutor.submitTask(runnerRequest)).thenReturn("taskId789");

    // When
    processor.processMessage(dequeueResponse);

    // Then - configured timeout was passed through to the runner builder
    verify(runnerRequestBuilder).buildCapacityRequestWithPoolSpec(any(), any(), any(), any(), eq(configuredTimeoutMs));
    verify(ciDelegateTaskExecutor).submitTask(runnerRequest);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenMaxWaitTimeConfigured_thenHonorConfiguredThreshold() {
    // Given - capacityTaskMaxWaitTimeMs is configured to 10 minutes; task started 9 minutes ago
    // (would exceed default 8-min threshold but not the configured 10-min one).
    long configuredMaxWaitMs = 10 * 60 * 1000L;
    when(globalQueueingConfig.getCapacityTaskMaxWaitTimeMs()).thenReturn(configuredMaxWaitMs);

    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);
    when(ciExecutionRepository.tryAcquireCapacityTaskLock(
             eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L)))
        .thenReturn(false);

    long lastProcessedTime = System.currentTimeMillis() - (9 * 60 * 1000); // 9 minutes ago
    CIExecutionMetadata metadata =
        CIExecutionMetadata.builder().capacityTaskInProgress(true).capacityTaskProcessedTime(lastProcessedTime).build();
    when(ciExecutionRepository.getExecutionMetadata(accountId, stageExecutionId)).thenReturn(metadata);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then - within configured threshold, so message is unacked for retry (not forced to proceed)
    assertThat(response.getSuccess()).isFalse();
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
    verify(ciExecutionRepository, never()).updateExecutionStatus(anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFEnabledAndCapacityTaskInProgress_thenUnackMessage() {
    // Given
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);

    // Lock acquisition fails
    when(ciExecutionRepository.tryAcquireCapacityTaskLock(
             eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L)))
        .thenReturn(false);

    // Metadata shows capacity task in progress for 1 minute (within 2 min timeout)
    long lastProcessedTime = System.currentTimeMillis() - (60 * 1000); // 1 minute ago
    CIExecutionMetadata metadata =
        CIExecutionMetadata.builder().capacityTaskInProgress(true).capacityTaskProcessedTime(lastProcessedTime).build();
    when(ciExecutionRepository.getExecutionMetadata(accountId, stageExecutionId)).thenReturn(metadata);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isFalse(); // unack for retry
    verify(ciExecutionRepository)
        .tryAcquireCapacityTaskLock(eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L));
    verify(ciExecutionRepository).getExecutionMetadata(accountId, stageExecutionId);
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING)); // should not proceed
    verify(ciDelegateTaskExecutor, never()).submitTask(any(RunnerRequest.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFEnabledAndCapacityTaskInProgressWithNullTime_thenUnackMessage() {
    // Given - Capacity task in progress but lastProcessedTime is null (edge case)
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);

    when(ciExecutionRepository.tryAcquireCapacityTaskLock(
             eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L)))
        .thenReturn(false);

    CIExecutionMetadata metadata =
        CIExecutionMetadata.builder().capacityTaskInProgress(true).capacityTaskProcessedTime(null).build();
    when(ciExecutionRepository.getExecutionMetadata(accountId, stageExecutionId)).thenReturn(metadata);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isFalse(); // unack for retry (no timeout check if time is null)
    verify(ciExecutionRepository).getExecutionMetadata(accountId, stageExecutionId);
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
    verify(ciDelegateTaskExecutor, never()).submitTask(any(RunnerRequest.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenCapacityTaskExceedsMaxWaitTimeout_thenProceedWithExecution() {
    // Given - Capacity task in progress for more than max-wait timeout (default 8 minutes)
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);

    when(ciExecutionRepository.tryAcquireCapacityTaskLock(
             eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L)))
        .thenReturn(false);

    // Capacity task started more than 8 minutes ago (8 min = 480,000 ms)
    long lastProcessedTime = System.currentTimeMillis() - (9 * 60 * 1000); // 9 minutes ago
    CIExecutionMetadata metadata = CIExecutionMetadata.builder()
                                       .capacityTaskInProgress(true)
                                       .capacityTaskProcessedTime(lastProcessedTime)
                                       .queueId("queue123")
                                       .build();
    when(ciExecutionRepository.getExecutionMetadata(accountId, stageExecutionId)).thenReturn(metadata);
    when(ciExecutionRepository.updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString()))
        .thenReturn(metadata);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isTrue(); // ack message
    verify(ciExecutionRepository).getExecutionMetadata(accountId, stageExecutionId);
    verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());
    verify(stepPMSFacilitator).sendFacilitatorResponse(ambiance, Status.RUNNING);
    verify(ciDelegateTaskExecutor, never()).submitTask(any(RunnerRequest.class)); // should not execute capacity task
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFEnabledAndProcessedTooRecently_thenUnackMessage() {
    // Given
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);

    when(ciExecutionRepository.tryAcquireCapacityTaskLock(
             eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L)))
        .thenReturn(false);

    long lastProcessedTime = System.currentTimeMillis() - 30_000; // 30 seconds ago
    CIExecutionMetadata metadata = CIExecutionMetadata.builder()
                                       .capacityTaskInProgress(false)
                                       .capacityTaskProcessedTime(lastProcessedTime)
                                       .build();
    when(ciExecutionRepository.getExecutionMetadata(accountId, stageExecutionId)).thenReturn(metadata);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isFalse(); // unack for retry
    verify(ciExecutionRepository).getExecutionMetadata(accountId, stageExecutionId);
    verify(ciDelegateTaskExecutor, never()).submitTask(any(RunnerRequest.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFEnabledAndMetadataNull_thenAckMessage() {
    // Given
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);

    when(ciExecutionRepository.tryAcquireCapacityTaskLock(
             eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L)))
        .thenReturn(false);

    when(ciExecutionRepository.getExecutionMetadata(accountId, stageExecutionId)).thenReturn(null);

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isTrue(); // ack message (aborted execution)
    verify(ciExecutionRepository).getExecutionMetadata(accountId, stageExecutionId);
    verify(ciDelegateTaskExecutor, never()).submitTask(any(RunnerRequest.class));
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testProcessMessage_whenExceptionOccurs_thenUnackMessage() {
    // Given
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class)))
        .thenThrow(new RuntimeException("Service unavailable"));

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then
    assertThat(response.getSuccess()).isFalse(); // unack for retry
  }

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
    verify(queueExecutionUtils, never()).isGlobalQueueEnabled(any(), any());
    verify(ciExecutionRepository, never()).updateExecutionStatus(anyString(), anyString(), anyString());
    verify(stepPMSFacilitator, never()).sendFacilitatorResponse(any(), eq(Status.RUNNING));
    verify(ciDelegateTaskExecutor, never()).submitTask(any(RunnerRequest.class));
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
    verify(queueExecutionUtils).isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class));
    verify(ciExecutionRepository).updateExecutionStatus(accountId, stageExecutionId, Status.RUNNING.toString());
    verify(stepPMSFacilitator).sendFacilitatorResponse(ambiance, Status.RUNNING);
  }

  // ==================== Tests for AIT bypass in capacity task ====================

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testProcessMessage_whenFFEnabledAndAitBypass_thenCiFreeLicenseIsFalse() {
    // Given - AIT bypass returns ENTERPRISE, so ciFreeLicense should be false
    when(queueExecutionUtils.isGlobalQueueEnabled(eq(ambiance), any(Infrastructure.class))).thenReturn(true);
    when(ciExecutionRepository.tryAcquireCapacityTaskLock(
             eq(accountId), eq(stageExecutionId), any(Long.class), eq(60_000L)))
        .thenReturn(true);
    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());
    when(vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(any(), any(), any()))
        .thenReturn(Pair.of("poolId123", new ArrayList<>()));
    when(vmInitializeUtils.getBuildTags(any())).thenReturn(new HashMap<>());
    when(ciLicenseUsageUtils.getResourceClass(anyString(), any(HostedVmInfraYaml.class), eq(false)))
        .thenReturn(Optional.of("STANDARD"));
    when(vmInitializeTaskParamsBuilder.getVMConfig(any(), any(), any())).thenReturn(null);

    RunnerRequest runnerRequest = mock(RunnerRequest.class);
    when(runnerRequestBuilder.buildCapacityRequestWithPoolSpec(any(), any(), any(), any(), eq(300_000L)))
        .thenReturn(runnerRequest);
    when(ciDelegateTaskExecutor.submitTask(runnerRequest)).thenReturn("taskId789");

    // When
    ProcessMessageResponse response = processor.processMessage(dequeueResponse);

    // Then - ciFreeLicense is false because ENTERPRISE license
    verify(ciLicenseService).getLicenseSummary(any(), any(), any());
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testGetCapacityReservationRequest_whenAitBypass_thenSkipsFreeLicenseCheck() throws Exception {
    // AIT bypass is centralized — getLicenseSummary returns ENTERPRISE for AIT principals
    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build());
    when(vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(any(), any(), any()))
        .thenReturn(Pair.of("poolId123", new ArrayList<>()));
    when(vmInitializeUtils.getBuildTags(any())).thenReturn(new HashMap<>());
    when(ciLicenseUsageUtils.getResourceClass(anyString(), any(HostedVmInfraYaml.class), eq(false)))
        .thenReturn(Optional.of("STANDARD"));
    when(vmInitializeTaskParamsBuilder.getVMConfig(any(), any(), any())).thenReturn(null);

    Method method = CICapacityTaskMessageProcessorImpl.class.getDeclaredMethod(
        "getCapacityReservationRequest", Infrastructure.class, Map.class, Ambiance.class);
    method.setAccessible(true);

    Object capacityRequest = method.invoke(processor, infrastructure, new HashMap<>(), ambiance);

    assertThat(capacityRequest).isNotNull();
    assertThat(((CapacityReservationRequest) capacityRequest).getContext().getPipelineExecutionID())
        .isEqualTo("planExec999");
    verify(ciLicenseService).getLicenseSummary(any(), any(), any());
  }

  @Test
  @Owner(developers = SHUBHAM_AGARWAL)
  @Category(UnitTests.class)
  public void testGetCapacityReservationRequest_whenNotBypassed_thenChecksFreeLicense() throws Exception {
    when(ciLicenseService.getLicenseSummary(any(), any(), any()))
        .thenReturn(CILicenseSummaryDTO.builder().edition(Edition.FREE).build());
    when(vmInitializeTaskParamsBuilder.getPoolIdsAndFallbacks(any(), any(), any()))
        .thenReturn(Pair.of("poolId123", new ArrayList<>()));
    when(vmInitializeUtils.getBuildTags(any())).thenReturn(new HashMap<>());
    when(ciLicenseUsageUtils.getResourceClass(anyString(), any(HostedVmInfraYaml.class), eq(true)))
        .thenReturn(Optional.of("STANDARD"));
    when(vmInitializeTaskParamsBuilder.getVMConfig(any(), any(), any())).thenReturn(null);

    Method method = CICapacityTaskMessageProcessorImpl.class.getDeclaredMethod(
        "getCapacityReservationRequest", Infrastructure.class, Map.class, Ambiance.class);
    method.setAccessible(true);

    Object capacityRequest = method.invoke(processor, infrastructure, new HashMap<>(), ambiance);

    assertThat(capacityRequest).isNotNull();
    verify(ciLicenseService).getLicenseSummary(any(), any(), any());
  }

  // ==================== Tests for getTopic ====================

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetTopic() {
    // When
    String topic = processor.getTopic();

    // Then
    assertThat(topic).isEqualTo("global_capacity_queue_ci");
    verify(ciExecutionServiceConfig).getQueueServiceClientConfig();
  }
}
