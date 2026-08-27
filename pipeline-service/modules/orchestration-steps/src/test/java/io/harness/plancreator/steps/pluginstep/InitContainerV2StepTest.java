/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.EcsDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraSpec;
import io.harness.category.element.UnitTests;
import io.harness.delegate.RunnerRequest;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.CITaskExecutionResponse;
import io.harness.delegate.beans.ci.ecs.CIECSInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponseFromRunner;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.taskparams.CIVmInitializeTaskParams;
import io.harness.delegate.task.ScheduleTaskRequest;
import io.harness.delegate.task.ScheduleTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.steps.container.ContainerStepInitHelper;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.matrix.ExpandedExecutionWrapperInfo;
import io.harness.steps.matrix.StrategyHelper;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerEcsInfra;
import io.harness.steps.plugin.infrastructure.ContainerInfraYamlSpec;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.steps.plugin.infrastructure.ContainerVMInfra;
import io.harness.tasks.FailureResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.InitialiseTaskUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Category(UnitTests.class)
public class InitContainerV2StepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock
  @Named("referenceFalseKryoSerializer")
  private io.harness.serializer.KryoSerializer referenceFalseKryoSerializer;
  @Mock private CIDelegateTaskExecutor cidelegateTaskExecutor;
  @Mock private ContainerStepInitHelper containerStepInitHelper;
  @Mock private io.harness.plancreator.steps.pluginstep.ContainerStepV2PluginProvider containerStepV2PluginProvider;
  @Mock private io.harness.steps.container.execution.ContainerStepRbacHelper containerStepRbacHelper;
  @Mock private ContainerExecutionConfig containerExecutionConfig;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private InitialiseTaskUtils initialiseTaskUtils;
  @Mock private io.harness.utils.PluginUtils pluginUtils;
  @Mock private StrategyHelper strategyHelper;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private RunnerRequestBuilder runnerRequestBuilder;
  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private PmsFeatureFlagService featureFlagService;

  @InjectMocks private InitContainerV2Step initContainerV2Step;

  private Ambiance ambiance;
  private InitContainerV2StepInfo stepParameters;

  @Before
  public void setUp() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");

    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId("planId")
                   .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("pipeline").build())
                   .putAllSetupAbstractions(setupAbstractions)
                   .setStageExecutionId("stageId")
                   .setExpressionFunctorToken(12345L)
                   .build();

    stepParameters = InitContainerV2StepInfo.builder()
                         .stepGroupIdentifier("sg1")
                         .stepGroupName("StepGroup1")
                         .stepGroupIdentifier("init")
                         .stepGroupName("Init")
                         .infrastructure(ContainerVMInfra.builder().spec(mock(VmInfraSpec.class)).build())
                         .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
                         .build();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_queuesTaskWithLogStreamingAndReturnsAsyncResponse() {
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());
    CIVmInitializeTaskParams taskParams = CIVmInitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(
             any(), eq(ambiance), anyString(), eq("sg1"), any(), anyBoolean()))
        .thenReturn(taskParams);
    TaskData taskData = TaskData.builder().async(true).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");
    when(cidelegateTaskExecutor.queueTask(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyString(), any(),
             any(Long.class), eq(true), any()))
        .thenReturn("task-id-1");

    AsyncExecutableResponse response =
        initContainerV2Step.executeAsyncAfterRbac(ambiance, stepParameters, StepInputPackage.builder().build());

    assertThat(response.getCallbackIdsCount()).isEqualTo(1);
    assertThat(response.getCallbackIds(0)).isEqualTo("task-id-1");
    assertThat(response.getLogKeysCount()).isEqualTo(1);
    assertThat(response.getLogKeys(0)).isEqualTo("STEP_logPrefix");

    ArgumentCaptor<String> stageIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(cidelegateTaskExecutor)
        .queueTask(any(), any(), any(), any(), eq(false), eq(false), stageIdCaptor.capture(), any(), eq(12345L),
            eq(true), any());
    assertThat(stageIdCaptor.getValue()).isEqualTo("stageId");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void handleAsyncResponse_nullMap_returnsNull() {
    StepResponse response = initContainerV2Step.handleAsyncResponse(ambiance, stepParameters, null);
    assertThat(response).isNull();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void handleAsyncResponse_emptyMap_returnsNull() {
    StepResponse response = initContainerV2Step.handleAsyncResponse(ambiance, stepParameters, Collections.emptyMap());
    assertThat(response).isNull();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void handleAsyncResponse_errorNotifyResponseData_returnsFailedStepResponse() {
    ErrorNotifyResponseData errorData = ErrorNotifyResponseData.builder().errorMessage("connection refused").build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task1", errorData);
    when(serializedResponseDataHelper.deserialize(any())).thenReturn(errorData);

    StepResponse response = initContainerV2Step.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo().getFailureDataCount()).isGreaterThan(0);
    assertThat(response.getFailureInfo().getFailureData(0).getMessage()).isEqualTo("connection refused");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void handleAsyncResponse_failureResponseData_returnsFailedStepResponse() {
    FailureResponseData failureData = FailureResponseData.builder().errorMessage("task failed").build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task1", failureData);
    when(serializedResponseDataHelper.deserialize(any())).thenReturn(failureData);

    StepResponse response = initContainerV2Step.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
    assertThat(response.getFailureInfo().getFailureDataCount()).isGreaterThan(0);
    assertThat(response.getFailureInfo().getFailureData(0).getMessage()).isEqualTo("task failed");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void handleAsyncResponse_vmSuccess_delegatesToContainerStepInitHelper() {
    VmTaskExecutionResponse vmResponse = VmTaskExecutionResponse.builder()
                                             .commandExecutionStatus(io.harness.logging.CommandExecutionStatus.SUCCESS)
                                             .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task1", vmResponse);
    when(serializedResponseDataHelper.deserialize(any())).thenReturn(vmResponse);

    StepResponse expectedResponse = StepResponse.builder().status(Status.SUCCEEDED).build();
    when(containerStepInitHelper.handleVMTaskExecutionResponse(vmResponse)).thenReturn(expectedResponse);

    StepResponse response = initContainerV2Step.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response).isEqualTo(expectedResponse);
    verify(containerStepInitHelper).handleVMTaskExecutionResponse(vmResponse);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void handleAsyncResponse_unsupportedInfraType_returnsNull() {
    CITaskExecutionResponse unsupportedResponse = mock(CITaskExecutionResponse.class);
    when(unsupportedResponse.getType()).thenReturn(CITaskExecutionResponse.Type.DOCKER);

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task1", unsupportedResponse);
    when(serializedResponseDataHelper.deserialize(any())).thenReturn(unsupportedResponse);

    assertThatThrownBy(() -> initContainerV2Step.handleAsyncResponse(ambiance, stepParameters, responseDataMap))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid infra type");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_k8InfraAndFFEnabled_submitsToRunner() {
    InitContainerV2StepInfo k8StepParams =
        InitContainerV2StepInfo.builder()
            .stepGroupIdentifier("sg1")
            .stepGroupName("StepGroup1")
            .infrastructure(ContainerK8sInfra.builder()
                                .type(ContainerStepInfra.Type.KUBERNETES_DIRECT)
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("conn"))
                                          .namespace(ParameterField.createValueField("ns"))
                                          .build())
                                .build())
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .build();

    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER)))
        .thenReturn(true);
    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.PL_RUNNER_K8S_INFRA_USE_SCHEDULED_TASK_API)))
        .thenReturn(false);
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());
    CIK8InitializeTaskParams k8Params = CIK8InitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(true)))
        .thenReturn(k8Params);
    TaskData taskData = TaskData.builder().async(true).timeout(600000L).parameters(new Object[] {k8Params}).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");
    RunnerRequest runnerRequest = RunnerRequest.newBuilder().build();
    when(runnerRequestBuilder.buildInitRequestK8(any(), any(), eq(k8Params), anyLong(), any()))
        .thenReturn(runnerRequest);
    when(cidelegateTaskExecutor.submitTask(eq(runnerRequest))).thenReturn("runner-task-id");

    AsyncExecutableResponse response =
        initContainerV2Step.executeAsyncAfterRbac(ambiance, k8StepParams, StepInputPackage.builder().build());

    assertThat(response.getCallbackIdsCount()).isEqualTo(1);
    assertThat(response.getCallbackIds(0)).isEqualTo("runner-task-id");
    verify(cidelegateTaskExecutor).submitTask(eq(runnerRequest));
    verify(runnerRequestBuilder).buildInitRequestK8(any(), any(), eq(k8Params), anyLong(), any());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_k8InfraAndFFDisabled_queuesDelegateTask() {
    InitContainerV2StepInfo k8StepParams =
        InitContainerV2StepInfo.builder()
            .stepGroupIdentifier("sg1")
            .stepGroupName("StepGroup1")
            .infrastructure(ContainerK8sInfra.builder()
                                .type(ContainerStepInfra.Type.KUBERNETES_DIRECT)
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("conn"))
                                          .namespace(ParameterField.createValueField("ns"))
                                          .build())
                                .build())
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .build();

    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER)))
        .thenReturn(false);
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());
    CIK8InitializeTaskParams k8Params = CIK8InitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(false)))
        .thenReturn(k8Params);
    TaskData taskData = TaskData.builder().async(true).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");
    when(cidelegateTaskExecutor.queueTask(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyString(), any(),
             any(Long.class), eq(true), any()))
        .thenReturn("delegate-task-id");

    AsyncExecutableResponse response =
        initContainerV2Step.executeAsyncAfterRbac(ambiance, k8StepParams, StepInputPackage.builder().build());

    assertThat(response.getCallbackIdsCount()).isEqualTo(1);
    assertThat(response.getCallbackIds(0)).isEqualTo("delegate-task-id");
    verify(cidelegateTaskExecutor)
        .queueTask(any(), any(), any(), any(), eq(false), eq(false), anyString(), any(), eq(12345L), eq(true), any());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void handleAsyncResponse_k8Success_delegatesToInitialiseTaskUtils() {
    K8sTaskExecutionResponse k8Response = K8sTaskExecutionResponse.builder()
                                              .commandExecutionStatus(io.harness.logging.CommandExecutionStatus.SUCCESS)
                                              .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task1", k8Response);

    when(serializedResponseDataHelper.deserialize(any())).thenReturn(k8Response);
    StepResponse expectedResponse = StepResponse.builder().status(Status.SUCCEEDED).build();
    when(initialiseTaskUtils.handleK8sTaskExecutionResponse(any(K8sTaskExecutionResponse.class)))
        .thenReturn(expectedResponse);

    StepResponse response = initContainerV2Step.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response).isEqualTo(expectedResponse);
    verify(serializedResponseDataHelper).deserialize(any());
    verify(initialiseTaskUtils).handleK8sTaskExecutionResponse(any(K8sTaskExecutionResponse.class));
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void
  handleAsyncResponse_k8SuccessWithK8sTaskExecutionResponseFromRunner_convertsAndDelegatesToInitialiseTaskUtils() {
    K8sTaskExecutionResponseFromRunner fromRunnerResponse = mock(K8sTaskExecutionResponseFromRunner.class);
    K8sTaskExecutionResponse k8Response = K8sTaskExecutionResponse.builder()
                                              .commandExecutionStatus(io.harness.logging.CommandExecutionStatus.SUCCESS)
                                              .build();
    when(fromRunnerResponse.getType()).thenReturn(CITaskExecutionResponse.Type.K8);
    when(fromRunnerResponse.toK8sTaskExecutionResponse()).thenReturn(k8Response);

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("task1", fromRunnerResponse);

    when(serializedResponseDataHelper.deserialize(any())).thenReturn(fromRunnerResponse);
    StepResponse expectedResponse = StepResponse.builder().status(Status.SUCCEEDED).build();
    when(initialiseTaskUtils.handleK8sTaskExecutionResponse(eq(k8Response))).thenReturn(expectedResponse);

    StepResponse response = initContainerV2Step.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    assertThat(response).isEqualTo(expectedResponse);
    verify(serializedResponseDataHelper).deserialize(any());
    verify(fromRunnerResponse).toK8sTaskExecutionResponse();
    verify(initialiseTaskUtils).handleK8sTaskExecutionResponse(eq(k8Response));
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_k8InfraRunnerAndScheduledTaskApi_usesInitRequestK8V1()
      throws JsonProcessingException {
    InitContainerV2StepInfo k8StepParams =
        InitContainerV2StepInfo.builder()
            .stepGroupIdentifier("sg1")
            .stepGroupName("StepGroup1")
            .infrastructure(ContainerK8sInfra.builder()
                                .type(ContainerStepInfra.Type.KUBERNETES_DIRECT)
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("conn"))
                                          .namespace(ParameterField.createValueField("ns"))
                                          .build())
                                .build())
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .build();

    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER)))
        .thenReturn(true);
    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.PL_RUNNER_K8S_INFRA_USE_SCHEDULED_TASK_API)))
        .thenReturn(true);
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());
    CIK8InitializeTaskParams k8Params = CIK8InitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(true)))
        .thenReturn(k8Params);
    TaskData taskData = TaskData.builder().async(true).timeout(600000L).parameters(new Object[] {k8Params}).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");

    ScheduleTaskRequest scheduleTaskRequest = ScheduleTaskRequest.newBuilder().build();
    ScheduleTaskResponse scheduleTaskResponse =
        ScheduleTaskResponse.newBuilder().setTaskId("sched-init-id").setTransactionId("txn-abc").build();
    when(runnerRequestBuilder.buildInitRequestK8V1(any(), any(), eq(k8Params), anyLong(), any()))
        .thenReturn(scheduleTaskRequest);
    when(cidelegateTaskExecutor.submitTask(eq(scheduleTaskRequest))).thenReturn(scheduleTaskResponse);

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    AsyncExecutableResponse response =
        initContainerV2Step.executeAsyncAfterRbac(ambiance, k8StepParams, StepInputPackage.builder().build());

    assertThat(response.getCallbackIdsCount()).isEqualTo(1);
    assertThat(response.getCallbackIds(0)).isEqualTo("sched-init-id");
    verify(cidelegateTaskExecutor).submitTask(eq(scheduleTaskRequest));
    verify(runnerRequestBuilder).buildInitRequestK8V1(any(), any(), eq(k8Params), anyLong(), any());
    verify(runnerRequestBuilder, never()).buildInitRequestK8(any(), any(), any(), anyLong(), any());
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void updateTransactionId_whenK8StageInfraFound_upsertsTransactionId() {
    K8StageInfraDetails existing =
        K8StageInfraDetails.builder().delegateSelectors(Collections.emptyList()).podName("pod").build();
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(existing).build());

    ScheduleTaskResponse response =
        ScheduleTaskResponse.newBuilder().setTaskId("t1").setTransactionId("new-txn").build();
    initContainerV2Step.updateTransactionId(ambiance, response);

    ArgumentCaptor<ExecutionSweepingOutput> outputCaptor = ArgumentCaptor.forClass(ExecutionSweepingOutput.class);
    verify(executionSweepingOutputService)
        .consumeUpsert(
            eq(ambiance), eq(STAGE_INFRA_DETAILS), outputCaptor.capture(), eq(StepCategory.STEP_GROUP.name()));
    assertThat(outputCaptor.getValue()).isInstanceOf(K8StageInfraDetails.class);
    assertThat(((K8StageInfraDetails) outputCaptor.getValue()).getTransactionId()).isEqualTo("new-txn");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_scheduledTaskApiWhenBuildThrows_wrapsCiStageExecutionException()
      throws JsonProcessingException {
    InitContainerV2StepInfo k8StepParams =
        InitContainerV2StepInfo.builder()
            .stepGroupIdentifier("sg1")
            .stepGroupName("StepGroup1")
            .infrastructure(ContainerK8sInfra.builder()
                                .type(ContainerStepInfra.Type.KUBERNETES_DIRECT)
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("conn"))
                                          .namespace(ParameterField.createValueField("ns"))
                                          .build())
                                .build())
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .build();

    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER)))
        .thenReturn(true);
    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.PL_RUNNER_K8S_INFRA_USE_SCHEDULED_TASK_API)))
        .thenReturn(true);
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());
    CIK8InitializeTaskParams k8Params = CIK8InitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(true)))
        .thenReturn(k8Params);
    TaskData taskData = TaskData.builder().async(true).timeout(600000L).parameters(new Object[] {k8Params}).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");
    doThrow(new JsonParseException(null, "parse"))
        .when(runnerRequestBuilder)
        .buildInitRequestK8V1(any(), any(), eq(k8Params), anyLong(), any());
    assertThatThrownBy(
        () -> initContainerV2Step.executeAsyncAfterRbac(ambiance, k8StepParams, StepInputPackage.builder().build()))
        .isInstanceOf(CIStageExecutionException.class)
        .hasMessageContaining("Failed to build init request for k8s infrastructure")
        .hasCauseInstanceOf(JsonProcessingException.class);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_ecsInfra_submitsToRunnerEvenWhenRunnerFeatureFlagDisabled()
      throws JsonProcessingException {
    InitContainerV2StepInfo ecsStepParams =
        InitContainerV2StepInfo.builder()
            .stepGroupIdentifier("sg1")
            .stepGroupName("StepGroup1")
            .infrastructure(ContainerEcsInfra.builder()
                                .type(ContainerStepInfra.Type.ECS_DIRECT)
                                .spec(EcsDirectInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("awsConn"))
                                          .build())
                                .build())
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .build();

    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER)))
        .thenReturn(false);
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());

    CIECSInitializeTaskParams ecsParams = CIECSInitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(true)))
        .thenReturn(ecsParams);
    TaskData taskData = TaskData.builder().async(true).timeout(600000L).parameters(new Object[] {ecsParams}).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");

    when(cidelegateTaskExecutor.buildInitDelegateTaskRequest(any(), any(), any(), any(), anyBoolean(), anyBoolean(),
             anyString(), any(), anyLong(), anyBoolean(), any()))
        .thenReturn(DelegateTaskRequest.builder().accountId("account").build());

    ScheduleTaskRequest scheduleTaskRequest = ScheduleTaskRequest.newBuilder().build();
    ScheduleTaskResponse scheduleTaskResponse =
        ScheduleTaskResponse.newBuilder().setTaskId("ecs-init-id").setTransactionId("txn-ecs").build();
    when(runnerRequestBuilder.buildInitRequestEcsV1(any(), any(), eq(ecsParams), anyLong(), any()))
        .thenReturn(scheduleTaskRequest);
    when(cidelegateTaskExecutor.submitTask(eq(scheduleTaskRequest))).thenReturn(scheduleTaskResponse);

    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    AsyncExecutableResponse response =
        initContainerV2Step.executeAsyncAfterRbac(ambiance, ecsStepParams, StepInputPackage.builder().build());

    assertThat(response.getCallbackIdsCount()).isEqualTo(1);
    assertThat(response.getCallbackIds(0)).isEqualTo("ecs-init-id");
    verify(containerStepInitHelper)
        .getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(true));
    verify(runnerRequestBuilder).buildInitRequestEcsV1(any(), any(), eq(ecsParams), anyLong(), any());
    verify(cidelegateTaskExecutor).submitTask(eq(scheduleTaskRequest));
    verify(cidelegateTaskExecutor, never()).submitTask(any(RunnerRequest.class));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_longTimeoutsEnabled_constructsStageDetailsWithTimeout() {
    InitContainerV2StepInfo k8StepParams =
        InitContainerV2StepInfo.builder()
            .stepGroupIdentifier("sg1")
            .stepGroupName("StepGroup1")
            .infrastructure(ContainerK8sInfra.builder()
                                .type(ContainerStepInfra.Type.KUBERNETES_DIRECT)
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("conn"))
                                          .namespace(ParameterField.createValueField("ns"))
                                          .build())
                                .build())
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .stageTimeout(ParameterField.createValueField("30h"))
            .build();

    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT)))
        .thenReturn(true);
    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER)))
        .thenReturn(false);
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());
    CIK8InitializeTaskParams k8Params = CIK8InitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(false)))
        .thenReturn(k8Params);
    TaskData taskData = TaskData.builder().async(true).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");
    when(cidelegateTaskExecutor.queueTask(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyString(), any(),
             any(Long.class), eq(true), any()))
        .thenReturn("task-id");

    initContainerV2Step.executeAsyncAfterRbac(ambiance, k8StepParams, StepInputPackage.builder().build());

    ArgumentCaptor<io.harness.pms.yaml.ParameterField> timeoutCaptor =
        ArgumentCaptor.forClass(io.harness.pms.yaml.ParameterField.class);
    verify(initialiseTaskUtils)
        .constructStageDetails(
            eq(ambiance), eq("sg1"), eq("StepGroup1"), eq("STEP_GROUP"), any(), timeoutCaptor.capture());
    assertThat(timeoutCaptor.getValue()).isNotNull();
    assertThat(timeoutCaptor.getValue().getValue()).isNotNull();
    assertThat(((io.harness.yaml.core.timeout.Timeout) timeoutCaptor.getValue().getValue()).getTimeoutInMillis())
        .isEqualTo(30 * 60 * 60 * 1000L);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_longTimeoutsEnabled_invalidTimeout_constructsStageDetailsWithNullTimeout() {
    InitContainerV2StepInfo k8StepParams =
        InitContainerV2StepInfo.builder()
            .stepGroupIdentifier("sg1")
            .stepGroupName("StepGroup1")
            .infrastructure(ContainerK8sInfra.builder()
                                .type(ContainerStepInfra.Type.KUBERNETES_DIRECT)
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("conn"))
                                          .namespace(ParameterField.createValueField("ns"))
                                          .build())
                                .build())
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .stageTimeout(ParameterField.createValueField("invalidValue"))
            .build();

    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT)))
        .thenReturn(true);
    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER)))
        .thenReturn(false);
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());
    CIK8InitializeTaskParams k8Params = CIK8InitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(false)))
        .thenReturn(k8Params);
    TaskData taskData = TaskData.builder().async(true).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");
    when(cidelegateTaskExecutor.queueTask(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyString(), any(),
             any(Long.class), eq(true), any()))
        .thenReturn("task-id");

    initContainerV2Step.executeAsyncAfterRbac(ambiance, k8StepParams, StepInputPackage.builder().build());

    ArgumentCaptor<io.harness.pms.yaml.ParameterField> timeoutCaptor =
        ArgumentCaptor.forClass(io.harness.pms.yaml.ParameterField.class);
    verify(initialiseTaskUtils)
        .constructStageDetails(
            eq(ambiance), eq("sg1"), eq("StepGroup1"), eq("STEP_GROUP"), any(), timeoutCaptor.capture());
    assertThat(timeoutCaptor.getValue()).isNull();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_longTimeoutsDisabled_constructsStageDetailsWithoutTimeout() {
    InitContainerV2StepInfo k8StepParams =
        InitContainerV2StepInfo.builder()
            .stepGroupIdentifier("sg1")
            .stepGroupName("StepGroup1")
            .infrastructure(ContainerK8sInfra.builder()
                                .type(ContainerStepInfra.Type.KUBERNETES_DIRECT)
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("conn"))
                                          .namespace(ParameterField.createValueField("ns"))
                                          .build())
                                .build())
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .stageTimeout(ParameterField.createValueField("30h"))
            .build();

    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT)))
        .thenReturn(false);
    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER)))
        .thenReturn(false);
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());
    CIK8InitializeTaskParams k8Params = CIK8InitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(false)))
        .thenReturn(k8Params);
    TaskData taskData = TaskData.builder().async(true).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");
    when(cidelegateTaskExecutor.queueTask(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyString(), any(),
             any(Long.class), eq(true), any()))
        .thenReturn("task-id");

    initContainerV2Step.executeAsyncAfterRbac(ambiance, k8StepParams, StepInputPackage.builder().build());

    verify(initialiseTaskUtils)
        .constructStageDetails(eq(ambiance), eq("sg1"), eq("StepGroup1"), eq("STEP_GROUP"), any());
    verify(initialiseTaskUtils, never())
        .constructStageDetails(
            eq(ambiance), eq("sg1"), eq("StepGroup1"), eq("STEP_GROUP"), any(), any(ParameterField.class));
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_longTimeoutsEnabled_emptyStringTimeout_constructsStageDetailsWithNullTimeout() {
    InitContainerV2StepInfo k8StepParams =
        InitContainerV2StepInfo.builder()
            .stepGroupIdentifier("sg1")
            .stepGroupName("StepGroup1")
            .infrastructure(ContainerK8sInfra.builder()
                                .type(ContainerStepInfra.Type.KUBERNETES_DIRECT)
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("conn"))
                                          .namespace(ParameterField.createValueField("ns"))
                                          .build())
                                .build())
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .stageTimeout(ParameterField.createValueField(""))
            .build();

    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT)))
        .thenReturn(true);
    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER)))
        .thenReturn(false);
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());
    CIK8InitializeTaskParams k8Params = CIK8InitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(false)))
        .thenReturn(k8Params);
    TaskData taskData = TaskData.builder().async(true).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");
    when(cidelegateTaskExecutor.queueTask(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyString(), any(),
             any(Long.class), eq(true), any()))
        .thenReturn("task-id");

    initContainerV2Step.executeAsyncAfterRbac(ambiance, k8StepParams, StepInputPackage.builder().build());

    ArgumentCaptor<ParameterField> timeoutCaptor = ArgumentCaptor.forClass(ParameterField.class);
    verify(initialiseTaskUtils)
        .constructStageDetails(
            eq(ambiance), eq("sg1"), eq("StepGroup1"), eq("STEP_GROUP"), any(), timeoutCaptor.capture());
    assertThat(timeoutCaptor.getValue()).isNull();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbac_longTimeoutsEnabled_unresolvedExpression_constructsStageDetailsWithNullTimeout() {
    InitContainerV2StepInfo k8StepParams =
        InitContainerV2StepInfo.builder()
            .stepGroupIdentifier("sg1")
            .stepGroupName("StepGroup1")
            .infrastructure(ContainerK8sInfra.builder()
                                .type(ContainerStepInfra.Type.KUBERNETES_DIRECT)
                                .spec(ContainerInfraYamlSpec.builder()
                                          .connectorRef(ParameterField.createValueField("conn"))
                                          .namespace(ParameterField.createValueField("ns"))
                                          .build())
                                .build())
            .stepsExecutionConfig(StepsExecutionConfig.builder().steps(Collections.emptyList()).build())
            .stageTimeout(ParameterField.createValueField("<+pipeline.variables.stageTimeout>"))
            .build();

    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT)))
        .thenReturn(true);
    when(featureFlagService.isEnabled(eq("account"), eq(FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER)))
        .thenReturn(false);
    when(strategyHelper.expandExecutionWrapperConfig(any(), eq(Optional.empty()), anyBoolean(), eq(ambiance)))
        .thenReturn(ExpandedExecutionWrapperInfo.builder()
                        .expandedExecutionConfigs(Collections.emptyList())
                        .uuidToStrategyExpansionData(Collections.emptyMap())
                        .build());
    CIK8InitializeTaskParams k8Params = CIK8InitializeTaskParams.builder().build();
    when(containerStepInitHelper.getBuildSetupTaskParams(any(), eq(ambiance), anyString(), eq("sg1"), any(), eq(false)))
        .thenReturn(k8Params);
    TaskData taskData = TaskData.builder().async(true).build();
    when(initialiseTaskUtils.getTaskData(any(CIInitializeTaskParams.class))).thenReturn(taskData);
    when(initialiseTaskUtils.getLogPrefix(ambiance, "STEP")).thenReturn("STEP_logPrefix");
    when(cidelegateTaskExecutor.queueTask(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyString(), any(),
             any(Long.class), eq(true), any()))
        .thenReturn("task-id");

    initContainerV2Step.executeAsyncAfterRbac(ambiance, k8StepParams, StepInputPackage.builder().build());

    ArgumentCaptor<ParameterField> timeoutCaptor = ArgumentCaptor.forClass(ParameterField.class);
    verify(initialiseTaskUtils)
        .constructStageDetails(
            eq(ambiance), eq("sg1"), eq("StepGroup1"), eq("STEP_GROUP"), any(), timeoutCaptor.capture());
    assertThat(timeoutCaptor.getValue()).isNull();
  }
}
