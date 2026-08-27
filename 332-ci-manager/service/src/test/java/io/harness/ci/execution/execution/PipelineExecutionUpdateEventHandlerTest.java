/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.rule.OwnerRule.GARGI;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.SATYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.app.beans.entities.StepExecutionParameters;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.CILogKeyMetadata;
import io.harness.beans.sweepingoutputs.CISweepingOutputNames;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.execution.intfc.GitBuildStatusUtility;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.metrics.CIObservabilityConstants;
import io.harness.ci.metrics.ExecutionMetricsService;
import io.harness.ci.metrics.helper.CIMetricsHelper;
import io.harness.data.structure.ListUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.dto.CILicenseSummaryDTO;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.PmsCommonConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.repositories.CILogKeyRepository;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.repositories.CIStepStatusRepository;
import io.harness.repositories.StepExecutionParametersRepository;
import io.harness.rule.Owner;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineExecutionUpdateEventHandlerTest extends CategoryTest {
  @Mock private GitBuildStatusUtility gitBuildStatusUtility;
  @Mock private StepExecutionParametersRepository stepExecutionParametersRepository;
  @Mock private CIFeatureFlagService ciFeatureFlagService;
  @Mock private StageCleanupUtility stageCleanupUtility;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private CILogServiceUtils ciLogServiceUtils;
  @Mock private CILicenseService ciLicenseService;
  @Mock private CIAccountExecutionMetadataRepository ciAccountExecutionMetadataRepository;
  @Mock private QueueExecutionUtils queueExecutionUtils;
  @Mock private HsqsClientService hsqsClientService;
  @Mock private CILogKeyRepository ciLogKeyRepository;
  @Mock private CIStageOutputRepository ciStageOutputRepository;
  @Mock private CIStepStatusRepository ciStepStatusRepository;
  @Mock private ExecutorService executorService;
  @Mock private ExecutorService ciRatelimitHandlerExecutor;
  @Mock private ExecutionMetricsService executionMetricsService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @InjectMocks private PipelineExecutionUpdateEventHandler pipelineExecutionUpdateEventHandler;

  private static final String ACCOUNT_ID = "accountId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    })
        .when(executorService)
        .submit(any(Runnable.class));
    doAnswer(invocation -> {
      ((Runnable) invocation.getArgument(0)).run();
      return null;
    })
        .when(ciRatelimitHandlerExecutor)
        .submit(any(Runnable.class));
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testHandleEvent() {
    OrchestrationEvent orchestrationEvent =
        OrchestrationEvent.builder()
            .ambiance(Ambiance.newBuilder()
                          .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                              "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                          .addAllLevels(ListUtils.newArrayList(Level.newBuilder().setRuntimeId("node1").build()))
                          .build())
            .build();
    when(stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(any(), any()))
        .thenReturn(Optional.of(
            StepExecutionParameters.builder().accountId("accountId").stepParameters("stepParameters").build()));
    when(gitBuildStatusUtility.shouldSendStatus(any())).thenReturn(true);
    pipelineExecutionUpdateEventHandler.handleEvent(orchestrationEvent);

    verify(gitBuildStatusUtility).sendStatusToGit(any(), any(), any(), any(), null);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testShouldSkip_WhenAllConditionsMet() throws Exception {
    // Given - FF enabled, gitStatus present, PR event
    StepParameters stepParams = createIntegrationStageParams(true);
    Ambiance ambiance = createStageLevelAmbiance(); // Fixed: proper stage-level ambiance
    OrchestrationEvent event = createOrchestrationEventWithPR();

    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, "accountId")).thenReturn(true);

    // When
    boolean shouldSkip = invokeShouldSkipCIGitStatusUpdate(stepParams, ambiance, event, "accountId");

    // Then
    assertThat(shouldSkip).isTrue();
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testShouldNotSkip_WhenFFDisabled() throws Exception {
    // Given - FF disabled
    StepParameters stepParams = createIntegrationStageParams(true);
    Ambiance ambiance = createStageLevelAmbiance();
    OrchestrationEvent event = createOrchestrationEventWithPR();

    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, "accountId")).thenReturn(false);

    // When
    boolean shouldSkip = invokeShouldSkipCIGitStatusUpdate(stepParams, ambiance, event, "accountId");

    // Then
    assertThat(shouldSkip).isFalse();
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testShouldNotSkip_WhenGitStatusNotConfigured() throws Exception {
    // Given - gitStatus field not present in YAML
    StepParameters stepParams = createIntegrationStageParams(false);
    Ambiance ambiance = createStageLevelAmbiance();
    OrchestrationEvent event = createOrchestrationEventWithPR();

    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, "accountId")).thenReturn(true);

    // When
    boolean shouldSkip = invokeShouldSkipCIGitStatusUpdate(stepParams, ambiance, event, "accountId");

    // Then
    assertThat(shouldSkip).isFalse();
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testShouldNotSkip_ForNonPREvents() throws Exception {
    // Given - Non-PR events (Push, Manual execution, etc.)
    StepParameters stepParams = createIntegrationStageParams(true);
    Ambiance ambiance = createStageLevelAmbiance();
    OrchestrationEvent event = createOrchestrationEventWithoutPR();

    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, "accountId")).thenReturn(true);

    // When
    boolean shouldSkip = invokeShouldSkipCIGitStatusUpdate(stepParams, ambiance, event, "accountId");

    // Then
    assertThat(shouldSkip).isFalse();
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testShouldNotSkip_WhenGitStatusConfigPresentIsNull() throws Exception {
    // Given - Old execution (gitStatusConfigPresent = null)
    StepParameters stepParams = createIntegrationStageParams(null);
    Ambiance ambiance = createStageLevelAmbiance();
    OrchestrationEvent event = createOrchestrationEventWithPR();

    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, "accountId")).thenReturn(true);

    // When
    boolean shouldSkip = invokeShouldSkipCIGitStatusUpdate(stepParams, ambiance, event, "accountId");

    // Then
    assertThat(shouldSkip).isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendGitStatus_whenStageLevel_shouldSendStatus() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime1")
                           .setIdentifier("stage1")
                           .setStartTs(1000L)
                           .build();
    Ambiance ambiance = buildAmbiance(stageLevel);
    StepParameters stepParams = createIntegrationStageParams(false);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(true);
    when(gitBuildStatusUtility.getStepParameters(eq(ambiance), eq(event), eq(ACCOUNT_ID))).thenReturn(stepParams);
    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, ACCOUNT_ID)).thenReturn(false);
    stubLicenseForNonFreeEdition();
    stubCleanupDefaults(ambiance);

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should not throw for stage-level git status")
        .doesNotThrowAnyException();

    verify(gitBuildStatusUtility)
        .sendStatusToGit(eq(Status.SUCCEEDED), eq(stepParams), eq(ambiance), eq(ACCOUNT_ID), eq(event));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendGitStatus_whenCodeBaseStepSucceeded_shouldSendRunning() {
    Level stepLevel = Level.newBuilder()
                          .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                           .setStepCategory(StepCategory.STEP)
                                           .setType("CI_CODEBASE_TASK")
                                           .build())
                          .setRuntimeId("runtime2")
                          .setIdentifier("codebase1")
                          .build();
    Ambiance ambiance = buildAmbiance(stepLevel);
    StepParameters stepParams = createIntegrationStageParams(false);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STEP))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(eq(stepLevel), eq(Status.SUCCEEDED))).thenReturn(true);
    when(gitBuildStatusUtility.getStepParameters(eq(ambiance), eq(event), eq(ACCOUNT_ID))).thenReturn(stepParams);
    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, ACCOUNT_ID)).thenReturn(false);
    stubLicenseForNonFreeEdition();

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should not throw for codebase step succeeded")
        .doesNotThrowAnyException();

    verify(gitBuildStatusUtility)
        .sendStatusToGit(eq(Status.RUNNING), eq(stepParams), eq(ambiance), eq(ACCOUNT_ID), eq(event));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendGitStatus_whenLiteEngineTask_shouldSendRunning() {
    Level stepLevel = Level.newBuilder()
                          .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                           .setStepCategory(StepCategory.STEP)
                                           .setType("liteEngineTask")
                                           .build())
                          .setRuntimeId("runtime3")
                          .setIdentifier("lite1")
                          .build();
    Ambiance ambiance = buildAmbiance(stepLevel);
    StepParameters stepParams = createIntegrationStageParams(false);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STEP))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(eq(stepLevel), eq(Status.SUCCEEDED))).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(eq(stepLevel), eq(event), eq(ambiance), eq(ACCOUNT_ID)))
        .thenReturn(true);
    when(gitBuildStatusUtility.getStepParameters(eq(ambiance), eq(event), eq(ACCOUNT_ID))).thenReturn(stepParams);
    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, ACCOUNT_ID)).thenReturn(false);
    stubLicenseForNonFreeEdition();

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should not throw for liteEngineTask")
        .doesNotThrowAnyException();

    verify(gitBuildStatusUtility)
        .sendStatusToGit(eq(Status.RUNNING), eq(stepParams), eq(ambiance), eq(ACCOUNT_ID), eq(event));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendGitStatus_whenAutoAbortThroughTrigger_shouldSkipStatus() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime4")
                           .setIdentifier("stage2")
                           .setStartTs(2000L)
                           .build();
    Ambiance ambiance = buildAmbiance(stageLevel);

    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .ambiance(ambiance)
            .status(Status.ABORTED)
            .serviceName("ci")
            .tags(Collections.singletonList(PmsCommonConstants.AUTO_ABORT_PIPELINE_THROUGH_TRIGGER))
            .build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(true);
    stubLicenseForNonFreeEdition();
    stubCleanupDefaults(ambiance);

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should not throw when auto-abort skips git status")
        .doesNotThrowAnyException();

    verify(gitBuildStatusUtility, never()).sendStatusToGit(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendGitStatus_whenShouldNotSendStatus_shouldNotSendGitStatus() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime5")
                           .setIdentifier("stage3")
                           .setStartTs(3000L)
                           .build();
    Ambiance ambiance = buildAmbiance(stageLevel);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(eq(stageLevel), eq(Status.SUCCEEDED))).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(eq(stageLevel), eq(event), eq(ambiance), eq(ACCOUNT_ID)))
        .thenReturn(false);
    stubLicenseForNonFreeEdition();
    stubCleanupDefaults(ambiance);

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should not throw when git status should not be sent")
        .doesNotThrowAnyException();

    verify(gitBuildStatusUtility, never()).sendStatusToGit(any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendGitStatus_exceptionHandled() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime6")
                           .setIdentifier("stage4")
                           .setStartTs(4000L)
                           .build();
    Ambiance ambiance = buildAmbiance(stageLevel);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE)))
        .thenThrow(new RuntimeException("git status error"));
    stubLicenseForNonFreeEdition();
    stubCleanupDefaults(ambiance);

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should swallow exceptions from sendGitStatus")
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendCleanupRequest_whenStageFinalStatus_shouldCleanup() throws Exception {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime7")
                           .setIdentifier("stage5")
                           .setStartTs(5000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithStageExecution(stageLevel, "stageExec1");
    QueueServiceClientConfig queueConfig = QueueServiceClientConfig.builder().topic("ci-topic").build();
    when(ciExecutionServiceConfig.getQueueServiceClientConfig()).thenReturn(queueConfig);
    when(queueExecutionUtils.deleteActiveExecutionRecord(eq("stageExec1"))).thenReturn(null);
    when(ciLogKeyRepository.findByStageExecutionId(eq("stageExec1"))).thenReturn(null);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(eq(stageLevel), eq(Status.SUCCEEDED))).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(eq(stageLevel), eq(event), eq(ambiance), eq(ACCOUNT_ID)))
        .thenReturn(false);
    stubLicenseForNonFreeEdition();

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should execute cleanup for stage with final status")
        .doesNotThrowAnyException();

    verify(stageCleanupUtility).submitCleanupRequest(eq(ambiance), eq("stage5"));
    verify(ciStageOutputRepository).deleteFirstByStageExecutionId(eq("stageExec1"));
    verify(ciStepStatusRepository).deleteByStageExecutionId(eq("stageExec1"));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendCleanupRequest_withQueueMetadata_shouldAck() throws Exception {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime8")
                           .setIdentifier("stage6")
                           .setStartTs(6000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithStageExecution(stageLevel, "stageExec2");
    QueueServiceClientConfig queueConfig = QueueServiceClientConfig.builder().topic("ci-topic").build();
    when(ciExecutionServiceConfig.getQueueServiceClientConfig()).thenReturn(queueConfig);

    CIExecutionMetadata metadata =
        CIExecutionMetadata.builder().queueId("queue1").queueTopic("qTopic").queueSubtopic("qSub").build();
    when(queueExecutionUtils.deleteActiveExecutionRecord(eq("stageExec2"))).thenReturn(metadata);
    when(ciLogKeyRepository.findByStageExecutionId(eq("stageExec2"))).thenReturn(null);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.FAILED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(eq(stageLevel), eq(Status.FAILED))).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(eq(stageLevel), eq(event), eq(ambiance), eq(ACCOUNT_ID)))
        .thenReturn(false);
    stubLicenseForNonFreeEdition();

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should ack queue when metadata has queueId")
        .doesNotThrowAnyException();

    verify(hsqsClientService).ack(any());
    verify(stageCleanupUtility).submitCleanupRequest(eq(ambiance), eq("stage6"));
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testHandleEvent_cleanupDispatched_shouldRecordSingleSuccessSample() throws Exception {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("IntegrationStageStepPMS")
                                            .build())
                           .setRuntimeId("runtimeCleanupMetric")
                           .setIdentifier("stageCleanupMetric")
                           .setStartTs(7000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithModuleType(stageLevel);
    stubCleanupDefaults(ambiance);
    stubLicenseForNonFreeEdition();
    when(stageCleanupUtility.submitCleanupRequest(any(), anyString()))
        .thenReturn(new StageCleanupUtility.CleanupSubmitResult(true, "KubernetesDirect"));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    pipelineExecutionUpdateEventHandler.handleEvent(event);

    verify(executionMetricsService)
        .recordSystemApiCall(eq(ACCOUNT_ID), anyString(), eq(CIObservabilityConstants.OP_CLEANUP_INFRA),
            eq(CIObservabilityConstants.OUTCOME_SUCCESS), eq(CIObservabilityConstants.PHASE_SUBMIT), isNull());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testHandleEvent_cleanupSkipped_shouldNotRecordAnySample() throws Exception {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("IntegrationStageStepPMS")
                                            .build())
                           .setRuntimeId("runtimeCleanupSkipped")
                           .setIdentifier("stageCleanupSkipped")
                           .setStartTs(8000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithModuleType(stageLevel);
    stubCleanupDefaults(ambiance);
    stubLicenseForNonFreeEdition();
    // An intentionally skipped cleanup (e.g. CI_SKIP_CLOUD_VM_CLEANUP) reports false and must not be counted.
    when(stageCleanupUtility.submitCleanupRequest(any(), anyString()))
        .thenReturn(new StageCleanupUtility.CleanupSubmitResult(false, "HostedVm"));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    pipelineExecutionUpdateEventHandler.handleEvent(event);

    verify(executionMetricsService, never())
        .recordSystemApiCall(
            anyString(), anyString(), eq(CIObservabilityConstants.OP_CLEANUP_INFRA), anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testHandleEvent_unprovisionedCleanup_shouldNotRecordFailure() throws Exception {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("IntegrationStageStepPMS")
                                            .build())
                           .setRuntimeId("runtimeUnprovisionedCleanup")
                           .setIdentifier("stageUnprovisionedCleanup")
                           .setStartTs(8500L)
                           .build();
    Ambiance ambiance = buildAmbianceWithModuleType(stageLevel);
    stubCleanupDefaults(ambiance);
    stubLicenseForNonFreeEdition();
    when(stageCleanupUtility.submitCleanupRequest(any(), anyString()))
        .thenThrow(new CIStageExecutionException(CIMetricsHelper.UNPROVISIONED_CLEANUP_MESSAGE));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.FAILED).serviceName("ci").build();

    pipelineExecutionUpdateEventHandler.handleEvent(event);

    verify(stageCleanupUtility, atLeastOnce()).submitCleanupRequest(eq(ambiance), eq("stageUnprovisionedCleanup"));
    verify(executionMetricsService, never())
        .recordSystemApiCall(
            anyString(), anyString(), eq(CIObservabilityConstants.OP_CLEANUP_INFRA), anyString(), anyString(), any());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testHandleEvent_prefersEventInfraForCleanupAndSkipsStageDetails() throws Exception {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("IntegrationStageStepPMS")
                                            .build())
                           .setRuntimeId("runtimeCleanupReuse")
                           .setIdentifier("stageCleanupReuse")
                           .setStartTs(9000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithModuleType(stageLevel);
    stubCleanupDefaults(ambiance);
    stubLicenseForNonFreeEdition();

    Infrastructure kubernetes = mock(Infrastructure.class);
    when(kubernetes.getType()).thenReturn(Infrastructure.Type.KUBERNETES_DIRECT);
    when(stageCleanupUtility.submitCleanupRequest(any(), anyString()))
        .thenReturn(new StageCleanupUtility.CleanupSubmitResult(true, "KubernetesDirect"));

    OrchestrationEvent event = OrchestrationEvent.builder()
                                   .ambiance(ambiance)
                                   .status(Status.SUCCEEDED)
                                   .serviceName("ci")
                                   .resolvedStepParameters(stageParamsWithInfra(kubernetes))
                                   .build();

    pipelineExecutionUpdateEventHandler.handleEvent(event);

    verify(executionSweepingOutputResolver, never())
        .resolveOptional(any(), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)));
    verify(executionSweepingOutputResolver, never())
        .resolveOptional(any(), eq(RefObjectUtils.getOutcomeRefObject(CISweepingOutputNames.INITIALIZE_EXECUTION)));
    verify(executionSweepingOutputResolver, never())
        .resolveOptional(any(), eq(RefObjectUtils.getSweepingOutputRefObject(StageInfraDetails.STAGE_INFRA_DETAILS)));
    verify(executionMetricsService)
        .recordStageExecution(eq(ACCOUNT_ID), eq("KubernetesDirect"), eq(Status.SUCCEEDED.name()),
            eq(CIObservabilityConstants.STAGE_PHASE_EXECUTION));
    verify(executionMetricsService)
        .recordSystemApiCall(eq(ACCOUNT_ID), eq("KubernetesDirect"), eq(CIObservabilityConstants.OP_CLEANUP_INFRA),
            eq(CIObservabilityConstants.OUTCOME_SUCCESS), eq(CIObservabilityConstants.PHASE_SUBMIT), isNull());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testHandleEvent_identityNodeFallsBackToStageDetailsForInfra() throws Exception {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("IntegrationStageStepPMS")
                                            .build())
                           .setRuntimeId("runtimeIdentityInfra")
                           .setIdentifier("stageIdentityInfra")
                           .setStartTs(9050L)
                           .build();
    Ambiance ambiance = buildAmbianceWithModuleType(stageLevel);
    stubCleanupDefaults(ambiance);
    stubLicenseForNonFreeEdition();

    Infrastructure kubernetes = mock(Infrastructure.class);
    when(kubernetes.getType()).thenReturn(Infrastructure.Type.KUBERNETES_DIRECT);
    when(executionSweepingOutputResolver.resolveOptional(
             any(), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().infrastructure(kubernetes).build())
                        .build());
    when(stageCleanupUtility.submitCleanupRequest(any(), anyString()))
        .thenReturn(new StageCleanupUtility.CleanupSubmitResult(true, "KubernetesDirect"));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    pipelineExecutionUpdateEventHandler.handleEvent(event);

    verify(executionSweepingOutputResolver, times(1))
        .resolveOptional(any(), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)));
    verify(executionMetricsService)
        .recordStageExecution(eq(ACCOUNT_ID), eq("KubernetesDirect"), eq(Status.SUCCEEDED.name()),
            eq(CIObservabilityConstants.STAGE_PHASE_EXECUTION));
    verify(executionSweepingOutputResolver, never())
        .resolveOptional(any(), eq(RefObjectUtils.getOutcomeRefObject(CISweepingOutputNames.INITIALIZE_EXECUTION)));
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testHandleEvent_cleanupFallbackReadsStageInfraDetailsNotStageDetails() throws Exception {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("IntegrationStageStepPMS")
                                            .build())
                           .setRuntimeId("runtimeCleanupStageInfra")
                           .setIdentifier("stageCleanupStageInfra")
                           .setStartTs(9100L)
                           .build();
    Ambiance ambiance = buildAmbianceWithModuleType(stageLevel);
    stubCleanupDefaults(ambiance);
    stubLicenseForNonFreeEdition();
    when(executionSweepingOutputResolver.resolveOptional(
             any(), eq(RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails))))
        .thenThrow(new RuntimeException("stageDetails unavailable"));
    when(stageCleanupUtility.submitCleanupRequest(any(), anyString())).thenThrow(new RuntimeException("submit failed"));
    when(executionSweepingOutputResolver.resolveOptional(
             any(), eq(RefObjectUtils.getSweepingOutputRefObject(StageInfraDetails.STAGE_INFRA_DETAILS))))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(K8StageInfraDetails.builder().build()).build());

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    pipelineExecutionUpdateEventHandler.handleEvent(event);

    verify(executionSweepingOutputResolver, times(1))
        .resolveOptional(any(), eq(RefObjectUtils.getSweepingOutputRefObject(StageInfraDetails.STAGE_INFRA_DETAILS)));
    verify(executionMetricsService)
        .recordSystemApiCall(eq(ACCOUNT_ID), eq("KubernetesDirect"), eq(CIObservabilityConstants.OP_CLEANUP_INFRA),
            eq(CIObservabilityConstants.OUTCOME_SYSTEM_FAILURE), eq(CIObservabilityConstants.PHASE_SUBMIT), isNull());
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testHandleEvent_preInitPrefersEventStepParameters() throws Exception {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("IntegrationStageStepPMS")
                                            .build())
                           .setRuntimeId("runtimePreInit")
                           .setIdentifier("stagePreInit")
                           .setStartTs(10000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithModuleType(stageLevel);
    stubCleanupDefaults(ambiance);
    stubLicenseForNonFreeEdition();
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(stageCleanupUtility.submitCleanupRequest(any(), anyString()))
        .thenReturn(new StageCleanupUtility.CleanupSubmitResult(false, CIObservabilityConstants.INFRA_TYPE_UNKNOWN));

    Infrastructure kubernetes = mock(Infrastructure.class);
    when(kubernetes.getType()).thenReturn(Infrastructure.Type.KUBERNETES_DIRECT);
    StepParameters stageParams = stageParamsWithInfra(kubernetes);
    OrchestrationEvent event = OrchestrationEvent.builder()
                                   .ambiance(ambiance)
                                   .status(Status.FAILED)
                                   .serviceName("ci")
                                   .resolvedStepParameters(stageParams)
                                   .build();

    pipelineExecutionUpdateEventHandler.handleEvent(event);

    verify(executionMetricsService)
        .recordStageExecution(eq(ACCOUNT_ID), eq("KubernetesDirect"), eq(Status.FAILED.name()),
            eq(CIObservabilityConstants.STAGE_PHASE_PRE_INIT));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendCleanupRequest_withLogKeys_shouldCloseStreams() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime9")
                           .setIdentifier("stage7")
                           .setStartTs(7000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithStageExecution(stageLevel, "stageExec3");
    QueueServiceClientConfig queueConfig = QueueServiceClientConfig.builder().topic("ci-topic").build();
    when(ciExecutionServiceConfig.getQueueServiceClientConfig()).thenReturn(queueConfig);
    when(queueExecutionUtils.deleteActiveExecutionRecord(eq("stageExec3"))).thenReturn(null);

    CILogKeyMetadata logKeyMetadata =
        CILogKeyMetadata.builder().stageExecutionId("stageExec3").logKeys(Arrays.asList("key1", "key2")).build();
    when(ciLogKeyRepository.findByStageExecutionId(eq("stageExec3"))).thenReturn(logKeyMetadata);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(eq(stageLevel), eq(Status.SUCCEEDED))).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(eq(stageLevel), eq(event), eq(ambiance), eq(ACCOUNT_ID)))
        .thenReturn(false);
    stubLicenseForNonFreeEdition();

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should close log streams when log keys exist")
        .doesNotThrowAnyException();

    verify(ciLogServiceUtils).closeLogStream(eq(ACCOUNT_ID), eq("key1"), eq(true), eq(false));
    verify(ciLogServiceUtils).closeLogStream(eq(ACCOUNT_ID), eq("key2"), eq(true), eq(false));
    verify(ciLogKeyRepository).deleteByStageExecutionId(eq("stageExec3"));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendCleanupRequest_withoutLogKeys_shouldCloseWithPrefix() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime10")
                           .setIdentifier("stage8")
                           .setStartTs(8000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithStageExecution(stageLevel, "stageExec4");
    QueueServiceClientConfig queueConfig = QueueServiceClientConfig.builder().topic("ci-topic").build();
    when(ciExecutionServiceConfig.getQueueServiceClientConfig()).thenReturn(queueConfig);
    when(queueExecutionUtils.deleteActiveExecutionRecord(eq("stageExec4"))).thenReturn(null);
    when(ciLogKeyRepository.findByStageExecutionId(eq("stageExec4"))).thenReturn(null);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.ERRORED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(eq(stageLevel), eq(Status.ERRORED))).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(eq(stageLevel), eq(event), eq(ambiance), eq(ACCOUNT_ID)))
        .thenReturn(false);
    stubLicenseForNonFreeEdition();

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should close log streams with prefix when no log keys in DB")
        .doesNotThrowAnyException();

    verify(ciLogServiceUtils).closeLogStream(eq(ACCOUNT_ID), anyString(), eq(true), eq(true));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendCleanupRequest_whenStepLevel_shouldNotCleanup() throws Exception {
    Level stepLevel = Level.newBuilder()
                          .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                           .setStepCategory(StepCategory.STEP)
                                           .setType("Run")
                                           .build())
                          .setRuntimeId("runtime11")
                          .setIdentifier("step1")
                          .build();
    Ambiance ambiance = buildAmbiance(stepLevel);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STEP))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(eq(stepLevel), eq(Status.SUCCEEDED))).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(eq(stepLevel), eq(event), eq(ambiance), eq(ACCOUNT_ID)))
        .thenReturn(false);
    stubLicenseForNonFreeEdition();

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should not cleanup for step-level events")
        .doesNotThrowAnyException();

    verify(stageCleanupUtility, never()).submitCleanupRequest(any(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_updateDailyBuildCount_whenFreeEditionAndRunning_shouldUpdate() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime12")
                           .setIdentifier("stage9")
                           .setStartTs(9000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithModuleType(stageLevel);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.RUNNING).serviceName("ci").build();

    LicensesWithSummaryDTO licenseDTO = CILicenseSummaryDTO.builder().edition(Edition.FREE).build();
    when(ciLicenseService.getLicenseSummary(eq(ACCOUNT_ID), anyString(), any())).thenReturn(licenseDTO);

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(any(), any())).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(any(), any(), any(), any())).thenReturn(false);

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should update daily build count for free edition on RUNNING")
        .doesNotThrowAnyException();

    verify(ciAccountExecutionMetadataRepository).updateCIDailyBuilds(eq(ACCOUNT_ID), eq(9000L));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_updateDailyBuildCount_whenLicenseNull_shouldThrow() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime13")
                           .setIdentifier("stage10")
                           .setStartTs(10000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithModuleType(stageLevel);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.RUNNING).serviceName("ci").build();

    when(ciLicenseService.getLicenseSummary(eq(ACCOUNT_ID), anyString())).thenReturn(null);

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(any(), any())).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(any(), any(), any(), any())).thenReturn(false);

    doAnswer(invocation -> {
      Runnable runnable = invocation.getArgument(0);
      try {
        runnable.run();
      } catch (CIStageExecutionException e) {
        // expected
      }
      return null;
    })
        .when(ciRatelimitHandlerExecutor)
        .submit(any(Runnable.class));

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should handle CIStageExecutionException when license is null")
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_updateDailyBuildCount_whenNonFreeEdition_shouldNotUpdate() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime14")
                           .setIdentifier("stage11")
                           .setStartTs(11000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithModuleType(stageLevel);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.RUNNING).serviceName("ci").build();

    stubLicenseForNonFreeEdition();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(any(), any())).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(any(), any(), any(), any())).thenReturn(false);

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should not update daily build count for non-free edition")
        .doesNotThrowAnyException();

    verify(ciAccountExecutionMetadataRepository, never()).updateCIDailyBuilds(anyString(), any());
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_isAutoAbortThroughTrigger_whenEmptyTags_shouldNotSkip() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime15")
                           .setIdentifier("stage12")
                           .setStartTs(12000L)
                           .build();
    Ambiance ambiance = buildAmbiance(stageLevel);
    StepParameters stepParams = createIntegrationStageParams(false);

    OrchestrationEvent event = OrchestrationEvent.builder()
                                   .ambiance(ambiance)
                                   .status(Status.ABORTED)
                                   .serviceName("ci")
                                   .tags(Collections.emptyList())
                                   .build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(true);
    when(gitBuildStatusUtility.getStepParameters(eq(ambiance), eq(event), eq(ACCOUNT_ID))).thenReturn(stepParams);
    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, ACCOUNT_ID)).thenReturn(false);
    stubLicenseForNonFreeEdition();
    stubCleanupDefaults(ambiance);

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should not skip git status when tags are empty")
        .doesNotThrowAnyException();

    verify(gitBuildStatusUtility)
        .sendStatusToGit(eq(Status.ABORTED), eq(stepParams), eq(ambiance), eq(ACCOUNT_ID), eq(event));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_deleteCIStageOutputs_exceptionSwallowed() throws Exception {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime16")
                           .setIdentifier("stage13")
                           .setStartTs(13000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithStageExecution(stageLevel, "stageExec5");
    QueueServiceClientConfig queueConfig = QueueServiceClientConfig.builder().topic("ci-topic").build();
    when(ciExecutionServiceConfig.getQueueServiceClientConfig()).thenReturn(queueConfig);
    when(queueExecutionUtils.deleteActiveExecutionRecord(eq("stageExec5"))).thenReturn(null);
    doThrow(new RuntimeException("DB error"))
        .when(ciStageOutputRepository)
        .deleteFirstByStageExecutionId(eq("stageExec5"));
    when(ciLogKeyRepository.findByStageExecutionId(eq("stageExec5"))).thenReturn(null);

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.SUCCEEDED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(any(), any())).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(any(), any(), any(), any())).thenReturn(false);
    stubLicenseForNonFreeEdition();

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should swallow exceptions from deleteCIStageOutputs")
        .doesNotThrowAnyException();

    verify(stageCleanupUtility).submitCleanupRequest(eq(ambiance), eq("stage13"));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleEvent_sendCleanupRequest_exceptionInCleanup_shouldBeSwallowed() {
    Level stageLevel = Level.newBuilder()
                           .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                            .setStepCategory(StepCategory.STAGE)
                                            .setType("CI")
                                            .build())
                           .setRuntimeId("runtime17")
                           .setIdentifier("stage14")
                           .setStartTs(14000L)
                           .build();
    Ambiance ambiance = buildAmbianceWithStageExecution(stageLevel, "stageExec6");

    when(ciExecutionServiceConfig.getQueueServiceClientConfig()).thenThrow(new RuntimeException("config error"));

    OrchestrationEvent event =
        OrchestrationEvent.builder().ambiance(ambiance).status(Status.FAILED).serviceName("ci").build();

    when(gitBuildStatusUtility.shouldSendStatus(eq(StepCategory.STAGE))).thenReturn(false);
    when(gitBuildStatusUtility.isCodeBaseStepSucceeded(any(), any())).thenReturn(false);
    when(gitBuildStatusUtility.shouldSentStatusOnInitialize(any(), any(), any(), any())).thenReturn(false);
    stubLicenseForNonFreeEdition();

    assertThatCode(() -> pipelineExecutionUpdateEventHandler.handleEvent(event))
        .as("handleEvent should swallow exceptions from sendCleanupRequest")
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsPREvent_whenNullTriggerPayload_shouldReturnFalse() throws Exception {
    OrchestrationEvent event = OrchestrationEvent.builder().build();

    boolean result = invokeIsPREvent(event);

    assertThat(result).as("isPREvent should return false when triggerPayload is null").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsPREvent_whenHasPR_shouldReturnTrue() throws Exception {
    OrchestrationEvent event = createOrchestrationEventWithPR();

    boolean result = invokeIsPREvent(event);

    assertThat(result).as("isPREvent should return true when parsed payload has PR").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsAutoAbortThroughTrigger_whenTagPresent_shouldReturnTrue() throws Exception {
    OrchestrationEvent event =
        OrchestrationEvent.builder()
            .tags(Collections.singletonList(PmsCommonConstants.AUTO_ABORT_PIPELINE_THROUGH_TRIGGER))
            .build();

    boolean result = invokeIsAutoAbortThroughTrigger(event);

    assertThat(result).as("isAutoAbortThroughTrigger should return true when tag is present").isTrue();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsAutoAbortThroughTrigger_whenTagNotPresent_shouldReturnFalse() throws Exception {
    OrchestrationEvent event = OrchestrationEvent.builder().tags(Collections.singletonList("SOME_OTHER_TAG")).build();

    boolean result = invokeIsAutoAbortThroughTrigger(event);

    assertThat(result).as("isAutoAbortThroughTrigger should return false when tag is not present").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testIsAutoAbortThroughTrigger_whenNullTags_shouldReturnFalse() throws Exception {
    OrchestrationEvent event = OrchestrationEvent.builder().build();

    boolean result = invokeIsAutoAbortThroughTrigger(event);

    assertThat(result).as("isAutoAbortThroughTrigger should return false when tags is null").isFalse();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractGitStatusConfigFromStageParams_whenNotStageElementParams_shouldReturnFalse() throws Exception {
    StepParameters nonStageParams = new StepParameters() {};

    boolean result = invokeExtractGitStatusConfigFromStageParams(nonStageParams);

    assertThat(result).as("extractGitStatusConfigFromStageParams should return false for non-stage params").isFalse();
  }

  private boolean invokeIsPREvent(OrchestrationEvent event) throws Exception {
    Method method = PipelineExecutionUpdateEventHandler.class.getDeclaredMethod("isPREvent", OrchestrationEvent.class);
    method.setAccessible(true);
    return (boolean) method.invoke(pipelineExecutionUpdateEventHandler, event);
  }

  private boolean invokeIsAutoAbortThroughTrigger(OrchestrationEvent event) throws Exception {
    Method method = PipelineExecutionUpdateEventHandler.class.getDeclaredMethod(
        "isAutoAbortThroughTrigger", OrchestrationEvent.class);
    method.setAccessible(true);
    return (boolean) method.invoke(pipelineExecutionUpdateEventHandler, event);
  }

  private boolean invokeExtractGitStatusConfigFromStageParams(StepParameters stageParams) throws Exception {
    Method method = PipelineExecutionUpdateEventHandler.class.getDeclaredMethod(
        "extractGitStatusConfigFromStageParams", StepParameters.class);
    method.setAccessible(true);
    return (boolean) method.invoke(pipelineExecutionUpdateEventHandler, stageParams);
  }

  private Ambiance buildAmbiance(Level level) {
    return Ambiance.newBuilder().putSetupAbstractions("accountId", ACCOUNT_ID).addLevels(level).build();
  }

  private Ambiance buildAmbianceWithStageExecution(Level level, String stageExecutionId) {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", ACCOUNT_ID)
        .setStageExecutionId(stageExecutionId)
        .addLevels(level)
        .build();
  }

  private Ambiance buildAmbianceWithModuleType(Level stageLevel) {
    Level ciStageLevel = Level.newBuilder()
                             .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                              .setStepCategory(StepCategory.STAGE)
                                              .setType("IntegrationStageStepPMS")
                                              .build())
                             .setRuntimeId(stageLevel.getRuntimeId())
                             .setIdentifier(stageLevel.getIdentifier())
                             .setStartTs(stageLevel.getStartTs())
                             .build();
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", ACCOUNT_ID)
        .addLevels(ciStageLevel)
        .setStageExecutionId("stageExecModuleType")
        .build();
  }

  private void stubLicenseForNonFreeEdition() {
    LicensesWithSummaryDTO licenseDTO = CILicenseSummaryDTO.builder().edition(Edition.ENTERPRISE).build();
    when(ciLicenseService.getLicenseSummary(eq(ACCOUNT_ID), anyString(), any())).thenReturn(licenseDTO);
  }

  private void stubCleanupDefaults(Ambiance ambiance) {
    QueueServiceClientConfig queueConfig = QueueServiceClientConfig.builder().topic("ci-topic").build();
    when(ciExecutionServiceConfig.getQueueServiceClientConfig()).thenReturn(queueConfig);
    when(queueExecutionUtils.deleteActiveExecutionRecord(anyString())).thenReturn(null);
    when(ciLogKeyRepository.findByStageExecutionId(anyString())).thenReturn(null);
  }

  private StepParameters stageParamsWithInfra(Infrastructure infrastructure) {
    return StageElementParameters.builder()
        .specConfig(IntegrationStageStepParametersPMS.builder().infrastructure(infrastructure).build())
        .build();
  }

  // Helper methods
  private StepParameters createIntegrationStageParams(Boolean gitStatusPresent) {
    IntegrationStageStepParametersPMS integrationParams =
        IntegrationStageStepParametersPMS.builder().gitStatusConfigPresent(gitStatusPresent).build();

    StageElementParameters stageParams = StageElementParameters.builder().specConfig(integrationParams).build();

    return stageParams;
  }

  private OrchestrationEvent createOrchestrationEventWithPR() {
    ParsedPayload parsedPayload =
        ParsedPayload.newBuilder().setPr(io.harness.product.ci.scm.proto.PullRequestHook.newBuilder().build()).build();
    TriggerPayload triggerPayload = TriggerPayload.newBuilder().setParsedPayload(parsedPayload).build();
    return OrchestrationEvent.builder().triggerPayload(triggerPayload).build();
  }

  private OrchestrationEvent createOrchestrationEventWithoutPR() {
    // No PR in payload - could be Push, Manual, etc.
    return OrchestrationEvent.builder().build();
  }

  private boolean invokeShouldSkipCIGitStatusUpdate(
      StepParameters stepParams, Ambiance ambiance, OrchestrationEvent event, String accountId) throws Exception {
    Method method = PipelineExecutionUpdateEventHandler.class.getDeclaredMethod(
        "shouldSkipCIGitStatusUpdate", StepParameters.class, Ambiance.class, OrchestrationEvent.class, String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(pipelineExecutionUpdateEventHandler, stepParams, ambiance, event, accountId);
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testShouldSkip_ForStepLevelEvent_WhenGitStatusConfigured() throws Exception {
    // Given - Step-level event (not stage), gitStatus configured in parent stage
    StepParameters stepParams = new StepParameters() {}; // Not StageElementParameters
    Ambiance ambiance = createStepLevelAmbiance();
    OrchestrationEvent event = createOrchestrationEventWithPR();

    StepParameters stageParams = createIntegrationStageParams(true);

    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, "accountId")).thenReturn(true);
    when(gitBuildStatusUtility.getStageParameters(any(), any(), any())).thenReturn(stageParams);

    // When
    boolean shouldSkip = invokeShouldSkipCIGitStatusUpdate(stepParams, ambiance, event, "accountId");

    // Then
    assertThat(shouldSkip).isTrue();
    verify(gitBuildStatusUtility).getStageParameters(any(), any(), any());
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testShouldNotSkip_ForStepLevelEvent_WhenStageParamsNotFound() throws Exception {
    // Given - Step-level event, but can't fetch parent stage params
    StepParameters stepParams = new StepParameters() {};
    Ambiance ambiance = createStepLevelAmbiance();
    OrchestrationEvent event = createOrchestrationEventWithPR();

    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, "accountId")).thenReturn(true);
    when(gitBuildStatusUtility.getStageParameters(any(), any(), any())).thenReturn(null);

    // When
    boolean shouldSkip = invokeShouldSkipCIGitStatusUpdate(stepParams, ambiance, event, "accountId");

    // Then
    assertThat(shouldSkip).isFalse();
    verify(gitBuildStatusUtility).getStageParameters(any(), any(), any());
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testShouldSkip_ForStageLevelEvent_NoDBCall() throws Exception {
    // Given - Stage-level event, stepParameters already contains stage params
    StepParameters stepParams = createIntegrationStageParams(true);
    Ambiance ambiance = createStageLevelAmbiance();
    OrchestrationEvent event = createOrchestrationEventWithPR();

    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, "accountId")).thenReturn(true);

    // When
    boolean shouldSkip = invokeShouldSkipCIGitStatusUpdate(stepParams, ambiance, event, "accountId");

    // Then
    assertThat(shouldSkip).isTrue();
    // IMPORTANT: Verify getStageParameters() was NOT called (optimization check)
    verify(gitBuildStatusUtility, never()).getStageParameters(any(), any(), any());
  }

  @Test
  @Owner(developers = GARGI)
  @Category(UnitTests.class)
  public void testShouldNotSkip_ForStepLevelEvent_WhenGitStatusNotConfigured() throws Exception {
    // Given - Step-level event, parent stage has gitStatus=false
    StepParameters stepParams = new StepParameters() {};
    Ambiance ambiance = createStepLevelAmbiance();
    OrchestrationEvent event = createOrchestrationEventWithPR();

    StepParameters stageParams = createIntegrationStageParams(false);

    when(ciFeatureFlagService.isEnabled(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT, "accountId")).thenReturn(true);
    when(gitBuildStatusUtility.getStageParameters(any(), any(), any())).thenReturn(stageParams);

    // When
    boolean shouldSkip = invokeShouldSkipCIGitStatusUpdate(stepParams, ambiance, event, "accountId");

    // Then
    assertThat(shouldSkip).isFalse();
    verify(gitBuildStatusUtility).getStageParameters(any(), any(), any());
  }

  private Ambiance createStepLevelAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "accountId")
        .addLevels(Level.newBuilder()
                       .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                        .setStepCategory(StepCategory.STEP)
                                        .setType("Run")
                                        .build())
                       .build())
        .build();
  }

  private Ambiance createStageLevelAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "accountId")
        .addLevels(Level.newBuilder()
                       .setStepType(io.harness.pms.contracts.steps.StepType.newBuilder()
                                        .setStepCategory(StepCategory.STAGE)
                                        .setType("CI")
                                        .build())
                       .build())
        .build();
  }
}
