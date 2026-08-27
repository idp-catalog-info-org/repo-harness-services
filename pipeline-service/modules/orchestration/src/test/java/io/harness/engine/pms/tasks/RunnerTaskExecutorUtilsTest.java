/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.tasks;

import static io.harness.rule.OwnerRule.SANYA;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.serializer.vm.VmRunStepSerializerV1;
import io.harness.ci.execution.states.helpers.CDStepsEnvironmentVarsHelper;
import io.harness.ci.execution.states.helpers.K8RunStepSerializerV1;
import io.harness.delegate.RunnerRequest;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.task.ScheduleTaskRequest;
import io.harness.delegate.task.ScheduleTaskResponse;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.plugin.CIK8ExecuteStepTaskParamsHelper;
import io.harness.pms.sdk.core.plugin.CIVMExecuteStepTaskParamsHelper;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.ExecuteStepRequest;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.runner.request.CIExecuteTaskData;
import io.harness.runner.request.VmStepExecuteHelperData;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.runner.request.helpers.RunnerRequestBuilderHelper;
import io.harness.runner.request.helpers.infra.TaskHelper;
import io.harness.runner.request.helpers.infra.TaskHelperFactory;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.vm.VmExecuteStepUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class RunnerTaskExecutorUtilsTest extends CategoryTest {
  @Mock private CIVMExecuteStepTaskParamsHelper civmExecuteStepTaskParamsHelper;
  @Mock private CIK8ExecuteStepTaskParamsHelper cik8ExecuteStepTaskParamsHelper;
  @Mock private CIDelegateTaskExecutor executor;
  @Mock private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Mock private VmRunStepSerializerV1 vmRunStepSerializerV1;
  @Mock private VmExecuteStepUtils vmExecuteStepUtils;
  @Mock private TaskHelperFactory taskHelperFactory;
  @Mock private RunnerRequestBuilder runnerRequestBuilder;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock private K8RunStepSerializerV1 k8RunStepSerializerV1;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private CommonAbstractStepUtils commonAbstractStepUtils;
  @Mock private CDStepsEnvironmentVarsHelper cdStepsEnvironmentVarsHelper;
  @Mock private PmsFeatureFlagService featureFlagService;
  @InjectMocks RunnerTaskExecutorUtils runnerTaskExecutorUtils;
  private Ambiance ambiance;

  @Before
  public void setup() {
    on(runnerTaskExecutorUtils).set("civmExecuteStepTaskParamsHelper", civmExecuteStepTaskParamsHelper);
    on(runnerTaskExecutorUtils).set("cik8ExecuteStepTaskParamsHelper", cik8ExecuteStepTaskParamsHelper);
    on(runnerTaskExecutorUtils).set("vmRunStepSerializerV1", vmRunStepSerializerV1);
    on(runnerTaskExecutorUtils).set("vmExecuteStepUtils", vmExecuteStepUtils);
    on(runnerTaskExecutorUtils).set("taskHelperFactory", taskHelperFactory);
    on(runnerTaskExecutorUtils).set("runnerRequestBuilder", runnerRequestBuilder);
    on(runnerTaskExecutorUtils).set("ciExecutionServiceConfig", ciExecutionServiceConfig);
    on(runnerTaskExecutorUtils).set("connectorUtils", connectorUtils);
    on(runnerTaskExecutorUtils).set("commonAbstractStepUtils", commonAbstractStepUtils);
    on(runnerTaskExecutorUtils).set("cdStepsEnvironmentVarsHelper", cdStepsEnvironmentVarsHelper);
    on(runnerTaskExecutorUtils).set("featureFlagService", featureFlagService);
    on(runnerTaskExecutorUtils).set("executor", executor);
    on(runnerTaskExecutorUtils).set("ciDelegateTaskExecutor", ciDelegateTaskExecutor);
    on(cik8ExecuteStepTaskParamsHelper).set("k8RunStepSerializerV1", k8RunStepSerializerV1);

    ambiance = Ambiance.newBuilder()
                   .putSetupAbstractions("accountId", "testAccount")
                   .putSetupAbstractions("projectIdentifier", "testProject")
                   .putSetupAbstractions("orgIdentifier", "testOrg")
                   .mergeMetadata(ExecutionMetadata.newBuilder().setHarnessVersion(HarnessYamlVersion.V1).build())
                   .build();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testSubmitRunnerExecuteTask() {
    RunStepInfoV1 runStepInfo =
        RunStepInfoV1.builder().script(ParameterField.createValueField("echo hello world")).build();
    StepElementParametersV1 stepParameters = StepElementParametersV1.builder().spec(runStepInfo).build();
    VmStageInfraDetails stageInfraDetails = VmStageInfraDetails.builder().build();
    StageDetails stageDetails = StageDetails.builder().build();
    String stepId = "stepId";
    String taskId = "taskId";
    VmDetailsOutcome vmDetailsOutcome = mock(VmDetailsOutcome.class);
    TaskHelper taskHelper = mock(TaskHelper.class);
    VmRunStep vmRunStep = mock(VmRunStep.class);
    CIExecuteTaskData ciExecuteTaskData = mock(CIExecuteTaskData.class);
    io.harness.delegate.HarnessSecret secret1 = io.harness.delegate.HarnessSecret.newBuilder().setId("secret1").build();
    io.harness.delegate.HarnessSecret secret2 = io.harness.delegate.HarnessSecret.newBuilder().setId("secret2").build();
    List<io.harness.delegate.HarnessSecret> expectedSecrets = List.of(secret1, secret2);

    when(cdStepsEnvironmentVarsHelper.retrieveAndSetEnvVarsForCDSteps(any(), any()))
        .thenReturn(Map.of("PLUGIN_JIRA_API_KEY", "${ngSecretManager.obtain(\"jiraKey\", 0)}"));
    when(civmExecuteStepTaskParamsHelper.getVmDetailsOutcome(any())).thenReturn(vmDetailsOutcome);
    when(vmDetailsOutcome.getDelegateId()).thenReturn("delegate");
    when(vmDetailsOutcome.getPoolDriverUsed()).thenReturn("pool");
    when(vmRunStepSerializerV1.serialize(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(vmRunStep);
    when(taskHelperFactory.getHelper(any(), any())).thenReturn(taskHelper);
    when(runnerRequestBuilder.buildExecuteRequest(any(), any(), any(), any(), any()))
        .thenReturn(RunnerRequest.newBuilder().build());
    when(executor.submitTask(any(RunnerRequest.class))).thenReturn(taskId);
    when(civmExecuteStepTaskParamsHelper.getExecuteTaskData(
             any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyBoolean(), anyBoolean()))
        .thenReturn(ciExecuteTaskData);
    doNothing().when(taskHelper).enrichExecuteTaskData(any(), any());

    String result;
    try (MockedStatic<RunnerRequestBuilderHelper> mockedStatic =
             mockStatic(RunnerRequestBuilderHelper.class, CALLS_REAL_METHODS)) {
      mockedStatic.when(() -> RunnerRequestBuilderHelper.updateSecretExprAndGetSecrets(any(), any(), any()))
          .thenReturn(expectedSecrets);
      result = runnerTaskExecutorUtils.submitRunnerExecuteTask(
          stepParameters, runStepInfo, ambiance, stepId, stageDetails, stageInfraDetails, null);
      // Called twice: once for the script's own secrets, once over the merged env (incl. CD-connector-derived vars).
      mockedStatic.verify(
          () -> RunnerRequestBuilderHelper.updateSecretExprAndGetSecrets(any(), any(), any()), times(2));

      ArgumentCaptor<VmStepExecuteHelperData> helperDataCaptor = ArgumentCaptor.forClass(VmStepExecuteHelperData.class);
      verify(civmExecuteStepTaskParamsHelper)
          .getExecuteTaskData(any(), any(), any(), helperDataCaptor.capture(), any(), any(), any(), any(), anyLong(),
              anyBoolean(), anyBoolean());
      VmStepExecuteHelperData capturedData = helperDataCaptor.getValue();
      // 2 secrets from the script's own resolution + 2 from the CD-connector-derived env vars' resolution.
      assertThat(capturedData.getSecretFQNs()).hasSize(4);
    }
    assertThat(result).isEqualTo(taskId);
    verify(civmExecuteStepTaskParamsHelper)
        .getExecuteTaskData(
            any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), anyBoolean(), anyBoolean());
    verify(runnerRequestBuilder).buildExecuteRequest(any(), any(), any(), any(), any());
    verify(executor).submitTask(any(RunnerRequest.class));
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testSubmitK8ExecuteTask() {
    RunStepInfoV1 runStepInfo =
        RunStepInfoV1.builder().script(ParameterField.createValueField("echo hello world")).build();
    StepElementParametersV1 stepParameters = StepElementParametersV1.builder().spec(runStepInfo).build();
    K8StageInfraDetails stageInfraDetails = K8StageInfraDetails.builder().build();
    TaskSelector selector = TaskSelector.newBuilder().setSelector("selector").setOrigin("origin").build();
    List<TaskSelector> taskSelectors = Arrays.asList(selector);
    String stepId = "stepId";
    String taskId = "taskId";
    String ip = "127.0.0.1";
    String containerName = "container";
    Integer port = 8080;
    UnitStep unitStep = mock(UnitStep.class);
    CIK8ExecuteStepTaskParams k8TaskParams = mock(CIK8ExecuteStepTaskParams.class);
    ExecuteStepRequest executeStepRequest = mock(ExecuteStepRequest.class);

    when(ciExecutionServiceConfig.isLocal()).thenReturn(true);
    when(ciExecutionServiceConfig.getDelegateServiceEndpointVariableValue()).thenReturn("http://localhost");
    when(cik8ExecuteStepTaskParamsHelper.getLitEnginePodIp(any())).thenReturn(ip);
    when(cik8ExecuteStepTaskParamsHelper.getPort(ambiance, stepId)).thenReturn(port);
    when(k8RunStepSerializerV1.serializeV1(
             any(), any(), any(), any(), any(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(unitStep);
    when(cik8ExecuteStepTaskParamsHelper.prepareCik8ExecuteStepTaskParams(any(), anyString(), anyString(), any(),
             anyList(), anyString(), any(), anyBoolean(), anyString(), anyList(), any()))
        .thenReturn(Pair.of(k8TaskParams, executeStepRequest));
    when(commonAbstractStepUtils.getContainerName(any(), any())).thenReturn(containerName);
    when(connectorUtils.fetchDelegateSelector(any(), any())).thenReturn(taskSelectors);
    when(featureFlagService.isEnabled(any(), any(FeatureName.class))).thenReturn(false);
    when(runnerRequestBuilder.buildExecuteRequestK8(any(), any(), any(), anyLong(), any(), any(), any()))
        .thenReturn(RunnerRequest.newBuilder().build());
    when(ciDelegateTaskExecutor.submitTask(any(RunnerRequest.class))).thenReturn(taskId);
    String result =
        runnerTaskExecutorUtils.submitK8ExecuteTask(stepParameters, ambiance, stepId, stageInfraDetails, null);
    assertThat(result).isEqualTo(taskId);
    verify(cik8ExecuteStepTaskParamsHelper)
        .serialiseStep(any(), any(), any(), anyInt(), any(), anyBoolean(), anyBoolean(), anyBoolean());
    verify(runnerRequestBuilder).buildExecuteRequestK8(any(), any(), any(), anyLong(), any(), any(), any());
    verify(ciDelegateTaskExecutor).submitTask(any(RunnerRequest.class));
  }

  @Test
  @Owner(developers = SANYA)
  @Category(UnitTests.class)
  public void testSubmitK8ExecuteTaskWithScheduledTaskApiEnabled() throws Exception {
    RunStepInfoV1 runStepInfo =
        RunStepInfoV1.builder().script(ParameterField.createValueField("echo hello world")).build();
    StepElementParametersV1 stepParameters = StepElementParametersV1.builder().spec(runStepInfo).build();
    K8StageInfraDetails stageInfraDetails = K8StageInfraDetails.builder().transactionId("transactionId").build();
    TaskSelector selector = TaskSelector.newBuilder().setSelector("selector").setOrigin("origin").build();
    List<TaskSelector> taskSelectors = Arrays.asList(selector);
    String stepId = "stepId";
    String taskId = "taskId";
    String ip = "127.0.0.1";
    String containerName = "container";
    Integer port = 8080;
    UnitStep unitStep = mock(UnitStep.class);
    CIK8ExecuteStepTaskParams k8TaskParams = mock(CIK8ExecuteStepTaskParams.class);
    ExecuteStepRequest executeStepRequest = mock(ExecuteStepRequest.class);

    when(ciExecutionServiceConfig.isLocal()).thenReturn(true);
    when(ciExecutionServiceConfig.getDelegateServiceEndpointVariableValue()).thenReturn("http://localhost");
    when(cik8ExecuteStepTaskParamsHelper.getLitEnginePodIp(any())).thenReturn(ip);
    when(cik8ExecuteStepTaskParamsHelper.getPort(ambiance, stepId)).thenReturn(port);
    when(k8RunStepSerializerV1.serializeV1(
             any(), any(), any(), any(), any(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(unitStep);
    when(cik8ExecuteStepTaskParamsHelper.prepareCik8ExecuteStepTaskParams(any(), anyString(), anyString(), any(),
             anyList(), anyString(), any(), anyBoolean(), anyString(), anyList(), any()))
        .thenReturn(Pair.of(k8TaskParams, executeStepRequest));
    when(commonAbstractStepUtils.getContainerName(any(), any())).thenReturn(containerName);
    when(connectorUtils.fetchDelegateSelector(any(), any())).thenReturn(taskSelectors);
    when(featureFlagService.isEnabled(any(), eq(FeatureName.PL_RUNNER_K8S_INFRA_USE_SCHEDULED_TASK_API)))
        .thenReturn(true);
    when(runnerRequestBuilder.buildExecuteRequestK8V1(
             any(), any(), any(), anyLong(), any(), any(), any(), any(K8StageInfraDetails.class)))
        .thenReturn(ScheduleTaskRequest.newBuilder().build());
    when(ciDelegateTaskExecutor.submitTask(any(ScheduleTaskRequest.class)))
        .thenReturn(ScheduleTaskResponse.newBuilder().setTaskId(taskId).build());

    String result =
        runnerTaskExecutorUtils.submitK8ExecuteTask(stepParameters, ambiance, stepId, stageInfraDetails, null);
    assertThat(result).isEqualTo(taskId);
    verify(runnerRequestBuilder)
        .buildExecuteRequestK8V1(any(), any(), any(), anyLong(), any(), any(), any(), any(K8StageInfraDetails.class));
    verify(ciDelegateTaskExecutor).submitTask(any(ScheduleTaskRequest.class));
  }
}
