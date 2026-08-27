/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.tasks;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.steps.CIRegistry;
import io.harness.beans.steps.stepinfo.CIStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfoV1;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.serializer.vm.VmRunStepSerializerV1;
import io.harness.ci.execution.states.helpers.CDStepsEnvironmentVarsHelper;
import io.harness.delegate.HarnessSecret;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.v1.StepElementParametersV1;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.CIK8ExecuteStepTaskParamsHelper;
import io.harness.pms.sdk.core.plugin.CIVMExecuteStepTaskParamsHelper;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.ExecuteStepRequest;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.runner.request.CIExecuteTaskData;
import io.harness.runner.request.VmStepExecuteHelperData;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.runner.request.helpers.RunnerRequestBuilderHelper;
import io.harness.runner.request.helpers.RunnerV0YamlHelper;
import io.harness.runner.request.helpers.infra.TaskHelper;
import io.harness.runner.request.helpers.infra.TaskHelperFactory;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.vm.VmExecuteStepUtils;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
@OwnedBy(HarnessTeam.CI)
@Slf4j
public class RunnerTaskExecutorUtils {
  public static final String TIMEOUT = "2h";
  public static final long TIMEOUT_IN_MILLIS = 2 * 60 * 60 * 1000;
  @Inject private VmRunStepSerializerV1 vmRunStepSerializerV1;
  @Inject private CIDelegateTaskExecutor executor;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private RunnerV0YamlHelper runnerV0YamlHelper;
  @Inject private TaskHelperFactory taskHelperFactory;
  @Inject private RunnerRequestBuilder runnerRequestBuilder;
  @Inject private VmExecuteStepUtils vmExecuteStepUtils;
  @Inject private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CIK8ExecuteStepTaskParamsHelper cik8ExecuteStepTaskParamsHelper;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CommonAbstractStepUtils commonAbstractStepUtils;
  @Inject private CIVMExecuteStepTaskParamsHelper civmExecuteStepTaskParamsHelper;
  @Inject private CDStepsEnvironmentVarsHelper cdStepsEnvironmentVarsHelper;
  @Inject private PmsFeatureFlagService featureFlagService;

  public String submitRunnerExecuteTask(StepBaseParameters stepParameters, RunStepInfoV1 runStepInfo, Ambiance ambiance,
      String stepId, StageDetails stageDetails, StageInfraDetails stageInfraDetails, Long timeOutInMillis) {
    CIStepInfo ciStepInfo = (CIStepInfo) stepParameters.getSpec();
    List<HarnessSecret> secrets =
        RunnerRequestBuilderHelper.updateSecretExprAndGetSecrets(ambiance, runStepInfo, new HashSet<>());
    VmStepExecuteHelperData vmStepExecuteHelperData =
        getVmStepExecuteHelperData(runStepInfo, ambiance, true, stageDetails, stepId, stageInfraDetails, secrets);
    long timeout = timeOutInMillis != null ? timeOutInMillis : TIMEOUT_IN_MILLIS;
    return submitRunnerExecuteTask(vmStepExecuteHelperData, executor, ambiance, stepParameters,
        stageDetails.getInfrastructure(), stageInfraDetails, timeout, Optional.empty());
  }

  public String submitK8ExecuteTask(StepBaseParameters stepParameters, Ambiance ambiance, String stepId,
      StageInfraDetails stageInfraDetails, Long timeOutInMillis) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    boolean isLocal = ciExecutionServiceConfig.isLocal();
    String delegateSvcEndpoint = ciExecutionServiceConfig.getDelegateServiceEndpointVariableValue();
    String taskType = TaskType.CI_EXECUTE_STEP_V2.toString();
    boolean isDetach = false;
    CIStepInfo ciStepInfo = (CIStepInfo) stepParameters.getSpec();
    long timeout = timeOutInMillis != null ? timeOutInMillis : ciStepInfo.getDefaultTimeout();
    String ip = cik8ExecuteStepTaskParamsHelper.getLitEnginePodIp(ambiance);
    String executionId = AmbianceUtils.obtainCurrentRuntimeId(ambiance) + generateUuid();
    String logKey = RunnerRequestBuilder.generateLogKey(ambiance, "Execute");
    UnitStep unitStep = cik8ExecuteStepTaskParamsHelper.serialiseStep(ciStepInfo, logKey, stepId,
        cik8ExecuteStepTaskParamsHelper.getPort(ambiance, stepId), ambiance, isDetach, true, true);
    Pair<CIK8ExecuteStepTaskParams, ExecuteStepRequest> paramsWithExecuteStepRequest =
        cik8ExecuteStepTaskParamsHelper.prepareCik8ExecuteStepTaskParams(ambiance, executionId, accountId,
            (K8StageInfraDetails) stageInfraDetails, new ArrayList<>(), ip, unitStep, isLocal, delegateSvcEndpoint,
            new ArrayList<>(), ciStepInfo);
    List<TaskSelector> taskSelectors = connectorUtils.fetchDelegateSelector(ambiance, executionSweepingOutputResolver);
    DelegateTaskRequest delegateTaskRequest = cik8ExecuteStepTaskParamsHelper.getDelegateTaskRequest(
        ambiance, timeout, accountId, taskType, paramsWithExecuteStepRequest.getLeft(), taskSelectors);
    return submitK8ExecuteTask(ciDelegateTaskExecutor, ambiance, timeout, delegateTaskRequest,
        paramsWithExecuteStepRequest.getRight(), paramsWithExecuteStepRequest.getLeft().getStepConnector(),
        paramsWithExecuteStepRequest.getLeft().getSecretVariableDetails(),
        commonAbstractStepUtils.getContainerName(ambiance, stepId), (K8StageInfraDetails) stageInfraDetails);
  }

  private VmStepInfo getVmStepInfo(RunStepInfoV1 runStepInfo, Ambiance ambiance, String stepId,
      ParameterField<Timeout> parameterFieldTimeout, List<CIRegistry> registries, String delegateId,
      StageInfraDetails stageInfraDetails) {
    VmStepInfo vmStepInfo = vmRunStepSerializerV1.serialize(
        runStepInfo, ambiance, stepId, parameterFieldTimeout, null, registries, delegateId, stageInfraDetails);
    civmExecuteStepTaskParamsHelper.injectOutputVarsAsEnvVars(
        vmStepInfo, AmbianceUtils.getAccountId(ambiance), ambiance.getStageExecutionId(), stepId, runStepInfo);
    return vmStepInfo;
  }

  private VmStepExecuteHelperData getVmStepExecuteHelperData(RunStepInfoV1 runStepInfo, Ambiance ambiance,
      boolean buildRunnerParams, StageDetails stageDetails, String stepId, StageInfraDetails stageInfraDetails,
      List<HarnessSecret> additionalSecrets) {
    VmDetailsOutcome vmDetailsOutcome = civmExecuteStepTaskParamsHelper.getVmDetailsOutcome(ambiance);
    Map<String, String> initEnvVars = new HashMap<>(civmExecuteStepTaskParamsHelper.getInitEnvVars(ambiance));

    List<HarnessSecret> secretFQNs = new ArrayList<>();
    VmStepInfo stepInfo =
        getVmStepInfo(runStepInfo, ambiance, stepId, ParameterField.createValueField(Timeout.fromString(TIMEOUT)),
            stageDetails.getRegistries(), vmDetailsOutcome.getDelegateId(), stageInfraDetails);
    if (isNotEmpty(additionalSecrets)) {
      secretFQNs.addAll(additionalSecrets);
    }
    return VmStepExecuteHelperData.builder()
        .vmStepInfo(stepInfo)
        .vmDetailsOutcome(vmDetailsOutcome)
        .initEnvVars(initEnvVars)
        .shouldRouteV0StageToRunner(buildRunnerParams)
        .secretFQNs(secretFQNs)
        .secretIds(new HashSet<>())
        .build();
  }

  private String submitRunnerExecuteTask(VmStepExecuteHelperData vmStepExecuteHelperData,
      CIDelegateTaskExecutor executor, Ambiance ambiance, StepBaseParameters stepBaseParameters,
      Infrastructure infrastructure, StageInfraDetails stageInfraDetails, long timeoutInMillis,
      Optional<DelegateTaskRequest> delegateTaskRequest) {
    TaskHelper helper = taskHelperFactory.getHelper(stepBaseParameters.getVersion(), infrastructure);
    Map<String, String> envVars = vmStepExecuteHelperData.getInitEnvVars();
    envVars.putAll(vmStepExecuteHelperData.getVmStepInfo().getExistingEnvVariables());
    Map<String, String> cdRelatedEnvVars =
        cdStepsEnvironmentVarsHelper.retrieveAndSetEnvVarsForCDSteps(ambiance, stepBaseParameters.getSpec());
    if (isNotEmpty(cdRelatedEnvVars)) {
      envVars.putAll(cdRelatedEnvVars);
    }
    vmStepExecuteHelperData.getSecretFQNs().addAll(RunnerRequestBuilderHelper.updateSecretExprAndGetSecrets(
        ambiance, envVars, vmStepExecuteHelperData.getSecretIds()));
    CIExecuteTaskData executeTaskData = civmExecuteStepTaskParamsHelper.getExecuteTaskData(stepBaseParameters,
        infrastructure, ambiance, vmStepExecuteHelperData, envVars, new HashSet<>(), stageInfraDetails, "Execute",
        timeoutInMillis, true, true);
    List<TaskSelector> taskSelectors = new ArrayList<>();
    if (stepBaseParameters instanceof StepElementParametersV1 stepElementParameters) {
      taskSelectors = TaskSelectorYaml.toTaskSelector(stepElementParameters.getDelegate());
    }
    return executor.submitTask(runnerRequestBuilder.buildExecuteRequest(
        ambiance, executeTaskData, helper, delegateTaskRequest, taskSelectors));
  }

  private String submitK8ExecuteTask(CIDelegateTaskExecutor executor, Ambiance ambiance, long timeoutInMillis,
      DelegateTaskRequest delegateTaskRequest, io.harness.product.ci.engine.proto.ExecuteStepRequest executeStepRequest,
      List<ConnectorDetails> connectorDetailsList, List<SecretVariableDetails> secretVariableDetails,
      String containerName, K8StageInfraDetails stageInfraDetails) {
    if (featureFlagService.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.PL_RUNNER_K8S_INFRA_USE_SCHEDULED_TASK_API)
        && stageInfraDetails.getTransactionId() != null) {
      try {
        return executor
            .submitTask(runnerRequestBuilder.buildExecuteRequestK8V1(ambiance, delegateTaskRequest, executeStepRequest,
                timeoutInMillis, connectorDetailsList, secretVariableDetails, containerName, stageInfraDetails))
            .getTaskId();
      } catch (JsonProcessingException ex) {
        log.error("Failed to submit execute task to Scheduled Task API", ex);
        throw new RuntimeException("Failed to submit execute task to Scheduled Task API", ex);
      }
    } else {
      return executor.submitTask(runnerRequestBuilder.buildExecuteRequestK8(ambiance, delegateTaskRequest,
          executeStepRequest, timeoutInMillis, connectorDetailsList, secretVariableDetails, containerName));
    }
  }
}
