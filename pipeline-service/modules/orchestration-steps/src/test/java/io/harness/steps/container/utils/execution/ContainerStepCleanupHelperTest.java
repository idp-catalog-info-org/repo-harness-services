/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils.execution;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.steps.StepUtils.PIE_SIMPLIFY_LOG_BASE_KEY;
import static io.harness.steps.plugin.infrastructure.ContainerStepInfra.Type.KUBERNETES_DIRECT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.category.element.UnitTests;
import io.harness.delegate.RunnerRequest;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.task.ScheduleTaskRequest;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.service.ScheduleResponse;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.container.execution.ContainerStepCleanupHelper;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.plugin.infrastructure.ContainerCleanupDetails;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.utils.PmsFeatureFlagService;

import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class ContainerStepCleanupHelperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final List<String> CONTAINERS = List.of("container1", "container2");
  private static final String POD_NAME = "podName";
  private static final String NAMESPACE = "namespace";
  private static final String IP_ADDRESS = "128.0.0.0";

  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private ConnectorUtils connectorUtils;

  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private OutcomeService outcomeService;
  @Mock private ContainerExecutionConfig containerExecutionConfig;
  @Mock private RunnerRequestBuilder runnerRequestBuilder;

  @InjectMocks private ContainerStepCleanupHelper containerStepCleanupHelper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    ILogStreamingStepClient logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSendCleanupRequestWithContainerCleanupDetails() {
    ContainerStepInfra ContainerStepInfra = ContainerK8sInfra.builder()
                                                .type(KUBERNETES_DIRECT)
                                                .spec(ContainerInfraYamlSpec.builder()
                                                          .namespace(ParameterField.createValueField(NAMESPACE))
                                                          .connectorRef(ParameterField.createValueField("conRef"))
                                                          .build())
                                                .build();
    ContainerCleanupDetails containerCleanupDetails = ContainerCleanupDetails.builder()
                                                          .podName(POD_NAME)
                                                          .cleanUpContainerNames(CONTAINERS)
                                                          .infrastructure(ContainerStepInfra)
                                                          .build();
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        LiteEnginePodDetailsOutcome.builder().ipAddress(IP_ADDRESS).namespace(NAMESPACE).build();

    // First resolveOptional = CLEANUP_DETAILS, second = STAGE_INFRA_DETAILS (not found -> delegate path)
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(containerCleanupDetails).build())
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(liteEnginePodDetailsOutcome).build());
    when(pmsFeatureFlagService.isEnabled(anyString(), anyString())).thenReturn(true);
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(ConnectorDetails.builder().build());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("taskId");
    when(containerExecutionConfig.isLocal()).thenReturn(false);

    containerStepCleanupHelper.sendCleanupRequest(getAmbiance());

    final ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));

    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    CIK8CleanupTaskParams taskParameters = (CIK8CleanupTaskParams) delegateTaskRequest.getTaskParameters();

    assertThat(taskParameters.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(taskParameters.getCleanupContainerNames()).isEqualTo(CONTAINERS);
    assertThat(taskParameters.getPodNameList()).isEqualTo(List.of(POD_NAME));
    assertThat(taskParameters.getLiteEngineIP()).isEqualTo(IP_ADDRESS);
    assertThat(taskParameters.getLiteEnginePort()).isEqualTo(LITE_ENGINE_PORT);
    assertThat(taskParameters.getIsLocal()).isFalse();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testSendCleanupRequestWithContainerCleanupDetailsWithDefaultGracePeriod() {
    ContainerStepInfra ContainerStepInfra = ContainerK8sInfra.builder()
                                                .type(KUBERNETES_DIRECT)
                                                .spec(ContainerInfraYamlSpec.builder()
                                                          .namespace(ParameterField.createValueField(NAMESPACE))
                                                          .connectorRef(ParameterField.createValueField("conRef"))
                                                          .build())
                                                .build();
    ContainerCleanupDetails containerCleanupDetails = ContainerCleanupDetails.builder()
                                                          .podName(POD_NAME)
                                                          .cleanUpContainerNames(CONTAINERS)
                                                          .infrastructure(ContainerStepInfra)
                                                          .build();
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        LiteEnginePodDetailsOutcome.builder().ipAddress(IP_ADDRESS).namespace(NAMESPACE).build();

    // First resolveOptional = CLEANUP_DETAILS, second = STAGE_INFRA_DETAILS (not found -> delegate path)
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(containerCleanupDetails).build())
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(liteEnginePodDetailsOutcome).build());
    when(pmsFeatureFlagService.isEnabled(anyString(), anyString())).thenReturn(true);
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.CI_K8CLEANUP_DEFAULT_GRACE_PERIOD)))
        .thenReturn(true);
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(ConnectorDetails.builder().build());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn("taskId");
    when(containerExecutionConfig.isLocal()).thenReturn(false);

    containerStepCleanupHelper.sendCleanupRequest(getAmbiance());

    final ArgumentCaptor<DelegateTaskRequest> delegateTaskRequestArgumentCaptor =
        ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper, times(1))
        .submitAsyncTaskV2(delegateTaskRequestArgumentCaptor.capture(), eq(Duration.ZERO));

    DelegateTaskRequest delegateTaskRequest = delegateTaskRequestArgumentCaptor.getValue();
    CIK8CleanupTaskParams taskParameters = (CIK8CleanupTaskParams) delegateTaskRequest.getTaskParameters();

    assertThat(taskParameters.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(taskParameters.getCleanupContainerNames()).isEqualTo(CONTAINERS);
    assertThat(taskParameters.getPodNameList()).isEqualTo(List.of(POD_NAME));
    assertThat(taskParameters.getLiteEngineIP()).isEqualTo(IP_ADDRESS);
    assertThat(taskParameters.getLiteEnginePort()).isEqualTo(LITE_ENGINE_PORT);
    assertThat(taskParameters.isUseDefaultGracePeriod()).isTrue();
    assertThat(taskParameters.getIsLocal()).isFalse();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSendCleanupRequestWithPodCleanupDetails() {
    PodCleanupDetails podCleanupDetails =
        PodCleanupDetails.builder().podName(POD_NAME).cleanUpContainerNames(CONTAINERS).build();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(podCleanupDetails).build());
    containerStepCleanupHelper.sendCleanupRequest(getAmbiance());

    verify(delegateGrpcClientWrapper, times(0)).submitAsyncTaskV2(any(), any());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testSendCleanupRequest_whenStageInfraRouteToRunner_submitsToRunnerNotDelegate() {
    ContainerStepInfra containerStepInfra = ContainerK8sInfra.builder()
                                                .type(KUBERNETES_DIRECT)
                                                .spec(ContainerInfraYamlSpec.builder()
                                                          .namespace(ParameterField.createValueField(NAMESPACE))
                                                          .connectorRef(ParameterField.createValueField("conRef"))
                                                          .build())
                                                .build();
    ContainerCleanupDetails containerCleanupDetails = ContainerCleanupDetails.builder()
                                                          .podName(POD_NAME)
                                                          .cleanUpContainerNames(CONTAINERS)
                                                          .infrastructure(containerStepInfra)
                                                          .build();
    K8StageInfraDetails k8StageInfraDetails =
        K8StageInfraDetails.builder().containerNames(CONTAINERS).podName(POD_NAME).routeToRunner(true).build();
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        LiteEnginePodDetailsOutcome.builder().ipAddress(IP_ADDRESS).namespace(NAMESPACE).build();

    // First resolveOptional = CLEANUP_DETAILS, second = STAGE_INFRA_DETAILS
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(containerCleanupDetails).build())
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(k8StageInfraDetails).build());
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(liteEnginePodDetailsOutcome).build());
    when(pmsFeatureFlagService.isEnabled(anyString(), anyString())).thenReturn(false);
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(ConnectorDetails.builder().build());
    when(containerExecutionConfig.isLocal()).thenReturn(false);

    RunnerRequest runnerRequest = RunnerRequest.newBuilder().build();
    when(runnerRequestBuilder.buildCleanupRequest(any(), eq(k8StageInfraDetails), anyString(), any()))
        .thenReturn(runnerRequest);
    when(delegateGrpcClientWrapper.submit(eq(runnerRequest))).thenReturn("runner-task-id");

    containerStepCleanupHelper.sendCleanupRequest(getAmbiance());

    verify(delegateGrpcClientWrapper, times(1)).submit(eq(runnerRequest));
    verify(delegateGrpcClientWrapper, times(0)).submitAsyncTaskV2(any(), any());
    verify(runnerRequestBuilder).buildCleanupRequest(any(), eq(k8StageInfraDetails), anyString(), any());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testSendCleanupRequest_whenK8TransactionIdPresent_usesScheduleTaskApi() {
    ContainerStepInfra containerStepInfra = ContainerK8sInfra.builder()
                                                .type(KUBERNETES_DIRECT)
                                                .spec(ContainerInfraYamlSpec.builder()
                                                          .namespace(ParameterField.createValueField(NAMESPACE))
                                                          .connectorRef(ParameterField.createValueField("conRef"))
                                                          .build())
                                                .build();
    ContainerCleanupDetails containerCleanupDetails = ContainerCleanupDetails.builder()
                                                          .podName(POD_NAME)
                                                          .cleanUpContainerNames(CONTAINERS)
                                                          .infrastructure(containerStepInfra)
                                                          .build();
    K8StageInfraDetails k8StageInfraDetails = K8StageInfraDetails.builder()
                                                  .containerNames(CONTAINERS)
                                                  .podName(POD_NAME)
                                                  .routeToRunner(true)
                                                  .transactionId("txn-1")
                                                  .build();
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        LiteEnginePodDetailsOutcome.builder().ipAddress(IP_ADDRESS).namespace(NAMESPACE).build();

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(containerCleanupDetails).build())
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(k8StageInfraDetails).build());
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(liteEnginePodDetailsOutcome).build());
    when(pmsFeatureFlagService.isEnabled(anyString(), anyString())).thenReturn(false);
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(ConnectorDetails.builder().build());
    when(containerExecutionConfig.isLocal()).thenReturn(false);

    ScheduleTaskRequest scheduleTaskRequest = ScheduleTaskRequest.newBuilder().build();
    when(runnerRequestBuilder.buildCleanupRequestV1(any(), eq(k8StageInfraDetails), any()))
        .thenReturn(scheduleTaskRequest);
    when(delegateGrpcClientWrapper.submitScheduleTask(scheduleTaskRequest))
        .thenReturn(new ScheduleResponse("cleanup-sched-id", "", "txn-1"));

    containerStepCleanupHelper.sendCleanupRequest(getAmbiance());

    verify(delegateGrpcClientWrapper, times(1)).submitScheduleTask(scheduleTaskRequest);
    verify(delegateGrpcClientWrapper, times(0)).submit(any(RunnerRequest.class));
    verify(runnerRequestBuilder).buildCleanupRequestV1(any(), eq(k8StageInfraDetails), any());
    verify(runnerRequestBuilder, times(0)).buildCleanupRequest(any(), any(), anyString(), any());
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_ID)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
        .addLevels(Level.newBuilder().setIdentifier("Identifier").build())
        .setPlanExecutionId("planExecutionId")
        .setMetadata(ExecutionMetadata.newBuilder()
                         .setPipelineIdentifier("pipelineIdentifier")
                         .putFeatureFlagToValueMap(PIE_SIMPLIFY_LOG_BASE_KEY, false)
                         .build())
        .build();
  }
}
