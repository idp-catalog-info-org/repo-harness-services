/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.EcsStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.ci.execution.utils.ci.CIStepInfoUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.RunnerRequest;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.CITaskExecutionResponse;
import io.harness.delegate.beans.ci.ecs.CIECSInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponseFromRunner;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.HDelegateTask;
import io.harness.delegate.task.ScheduleTaskRequest;
import io.harness.delegate.task.ScheduleTaskResponse;
import io.harness.delegate.task.taskrunner.TaskRunnerTaskResponse;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.plancreator.inject.InjectUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.runner.request.builder.RunnerRequestBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.container.ContainerStepInitHelper;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.container.execution.ContainerStepRbacHelper;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.container.utils.ContainerSpecUtils;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.steps.matrix.ExpandedExecutionWrapperInfo;
import io.harness.steps.matrix.StrategyExpansionData;
import io.harness.steps.matrix.StrategyHelper;
import io.harness.steps.plugin.ContainerStepConstants;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerEcsInfra;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.steps.plugin.infrastructure.ContainerStepInfra;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.FailureResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.InitialiseTaskUtils;
import io.harness.utils.PluginUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@Slf4j
public class InitContainerV2Step implements TaskExecutableWithRbac<InitContainerV2StepInfo, CITaskExecutionResponse>,
                                            AsyncExecutableWithRbac<InitContainerV2StepInfo> {
  private static final long TASK_BUFFER_TIMEOUT_MILLIS = 300_000L; // 5 minutes

  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Inject CIDelegateTaskExecutor cidelegateTaskExecutor;
  @Inject ContainerStepInitHelper containerStepInitHelper;
  @Inject io.harness.plancreator.steps.pluginstep.ContainerStepV2PluginProvider containerStepV2PluginProvider;
  @Inject ContainerStepRbacHelper containerStepRbacHelper;
  @Inject ContainerExecutionConfig containerExecutionConfig;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject InitialiseTaskUtils initialiseTaskUtils;
  @Inject PluginUtils pluginUtils;
  @Inject StrategyHelper strategyHelper;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private RunnerRequestBuilder runnerRequestBuilder;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private PmsFeatureFlagService featureFlagService;

  @Override
  public Class<InitContainerV2StepInfo> getStepParametersClass() {
    return InitContainerV2StepInfo.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, InitContainerV2StepInfo stepParameters) {
    containerStepRbacHelper.validateResources(stepParameters, ambiance);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, InitContainerV2StepInfo stepParameters,
      ThrowingSupplier<CITaskExecutionResponse> responseDataSupplier) throws Exception {
    if (responseDataSupplier.get().getType() == CITaskExecutionResponse.Type.K8) {
      return initialiseTaskUtils.handleK8sTaskExecutionResponse((K8sTaskExecutionResponse) responseDataSupplier.get());
    } else if (responseDataSupplier.get().getType() == CITaskExecutionResponse.Type.VM) {
      return containerStepInitHelper.handleVMTaskExecutionResponse(
          (VmTaskExecutionResponse) responseDataSupplier.get());
    }
    return null;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, InitContainerV2StepInfo stepParameters, StepInputPackage inputPackage) {
    if (ContainerStepInfra.Type.ECS_DIRECT.equals(stepParameters.getInfrastructure().getType())) {
      throw new CIStageExecutionException("ECS container step groups only support runner-based execution");
    }
    InitTaskData initTaskData = prepareInitTask(ambiance, stepParameters, false);
    String stageId = ambiance.getStageExecutionId();
    return TaskRequestsUtils.prepareTaskRequest(ambiance, initTaskData.taskData, referenceFalseKryoSerializer,
        TaskCategory.DELEGATE_TASK_V2, null, true,
        TaskType.valueOf(initTaskData.taskData.getTaskType()).getDisplayName(), initTaskData.taskSelectors,
        Scope.PROJECT, EnvironmentType.ALL, false, new ArrayList<>(), false, stageId);
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, InitContainerV2StepInfo stepParameters, StepInputPackage inputPackage) {
    boolean shouldRouteToRunner = shouldRouteToRunner(ambiance);
    if (ContainerStepInfra.Type.ECS_DIRECT.equals(stepParameters.getInfrastructure().getType())) {
      // Since we support only delegate 2.0 for ECS
      shouldRouteToRunner = true;
    }
    InitTaskData initTaskData = prepareInitTask(ambiance, stepParameters, shouldRouteToRunner);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Map<String, String> abstractions = StepUtils.buildAbstractions(ambiance, Scope.PROJECT);
    HDelegateTask task = (HDelegateTask) StepUtils.prepareDelegateTaskInput(
        accountId, initTaskData.taskData, abstractions, StepUtils.generateLogAbstractions(ambiance));
    List<String> taskSelectorStrings =
        initTaskData.taskSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList());
    String stageExecutionId = ambiance.getStageExecutionId();

    String taskId;
    if (ContainerStepInfra.Type.KUBERNETES_DIRECT.equals(stepParameters.getInfrastructure().getType())
        && shouldRouteToRunner) {
      long timeout = initTaskData.taskData.getTimeout() + TASK_BUFFER_TIMEOUT_MILLIS;
      DelegateTaskRequest delegateTaskRequest =
          cidelegateTaskExecutor.buildInitDelegateTaskRequest(abstractions, task, taskSelectorStrings,
              new ArrayList<>(), false, false, stageExecutionId, StepUtils.generateLogAbstractions(ambiance),
              ambiance.getExpressionFunctorToken(), true, initTaskData.taskSelectors);
      CIK8InitializeTaskParams cik8Params = (CIK8InitializeTaskParams) initTaskData.taskData.getParameters()[0];

      if (featureFlagService.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.PL_RUNNER_K8S_INFRA_USE_SCHEDULED_TASK_API)) {
        try {
          ScheduleTaskRequest scheduleTaskRequest = runnerRequestBuilder.buildInitRequestK8V1(
              ambiance, initTaskData.taskSelectors, cik8Params, timeout, delegateTaskRequest);
          ScheduleTaskResponse scheduleTaskResponse = cidelegateTaskExecutor.submitTask(scheduleTaskRequest);
          updateTransactionId(ambiance, scheduleTaskResponse);
          taskId = scheduleTaskResponse.getTaskId();
        } catch (JsonProcessingException ex) {
          log.error("Failed to build init request for k8s infrastructure", ex);
          throw new CIStageExecutionException("Failed to build init request for k8s infrastructure", ex);
        }
      } else {
        RunnerRequest runnerRequest = runnerRequestBuilder.buildInitRequestK8(
            ambiance, initTaskData.taskSelectors, cik8Params, timeout, delegateTaskRequest);
        taskId = cidelegateTaskExecutor.submitTask(runnerRequest);
      }
    } else if (ContainerStepInfra.Type.ECS_DIRECT.equals(stepParameters.getInfrastructure().getType())) {
      long timeout = initTaskData.taskData.getTimeout() + TASK_BUFFER_TIMEOUT_MILLIS;
      DelegateTaskRequest delegateTaskRequest =
          cidelegateTaskExecutor.buildInitDelegateTaskRequest(abstractions, task, taskSelectorStrings,
              new ArrayList<>(), false, false, stageExecutionId, StepUtils.generateLogAbstractions(ambiance),
              ambiance.getExpressionFunctorToken(), true, initTaskData.taskSelectors);
      CIECSInitializeTaskParams ecsParams = (CIECSInitializeTaskParams) initTaskData.taskData.getParameters()[0];
      try {
        ScheduleTaskRequest scheduleTaskRequest = runnerRequestBuilder.buildInitRequestEcsV1(
            ambiance, initTaskData.taskSelectors, ecsParams, timeout, delegateTaskRequest);
        ScheduleTaskResponse scheduleTaskResponse = cidelegateTaskExecutor.submitTask(scheduleTaskRequest);
        updateTransactionIdForEcs(ambiance, scheduleTaskResponse);
        taskId = scheduleTaskResponse.getTaskId();
      } catch (JsonProcessingException ex) {
        log.error("Failed to build init request for ECS infrastructure", ex);
        throw new CIStageExecutionException("Failed to build init request for ECS infrastructure", ex);
      }
    } else {
      taskId = cidelegateTaskExecutor.queueTask(abstractions, task, taskSelectorStrings, new ArrayList<>(), false,
          false, stageExecutionId, StepUtils.generateLogAbstractions(ambiance), ambiance.getExpressionFunctorToken(),
          true, initTaskData.taskSelectors);
    }

    String logPrefix = initialiseTaskUtils.getLogPrefix(ambiance, "STEP");
    return AsyncExecutableResponse.newBuilder().addCallbackIds(taskId).addAllLogKeys(List.of(logPrefix)).build();
  }

  void updateTransactionId(Ambiance ambiance, ScheduleTaskResponse scheduleTaskResponse) {
    OptionalSweepingOutput stageExecutionDetails = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_INFRA_DETAILS));
    if (stageExecutionDetails.isFound()
        && stageExecutionDetails.getOutput() instanceof K8StageInfraDetails k8StageInfraDetails) {
      executionSweepingOutputService.consumeUpsert(ambiance, STAGE_INFRA_DETAILS,
          k8StageInfraDetails.toBuilder().transactionId(scheduleTaskResponse.getTransactionId()).build(),
          StepCategory.STEP_GROUP.name());
    }
  }

  void updateTransactionIdForEcs(Ambiance ambiance, ScheduleTaskResponse scheduleTaskResponse) {
    OptionalSweepingOutput stageExecutionDetails = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_INFRA_DETAILS));
    if (stageExecutionDetails.isFound()
        && stageExecutionDetails.getOutput() instanceof EcsStageInfraDetails ecsStageInfraDetails) {
      executionSweepingOutputService.consumeUpsert(ambiance, STAGE_INFRA_DETAILS,
          ecsStageInfraDetails.toBuilder().transactionId(scheduleTaskResponse.getTransactionId()).build(),
          StepCategory.STEP_GROUP.name());
    }
  }

  private boolean shouldRouteToRunner(Ambiance ambiance) {
    return featureFlagService.isEnabled(
        AmbianceUtils.getAccountId(ambiance), FeatureName.CDS_CONTAINER_STEP_GROUP_STEPS_USE_RUNNER);
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, InitContainerV2StepInfo stepParameters, Map<String, ResponseData> responseDataMap) {
    if (responseDataMap == null || responseDataMap.isEmpty()) {
      return null;
    }
    ResponseData responseData = responseDataMap.values().iterator().next();
    responseData = serializedResponseDataHelper.deserialize(responseData);

    if (responseData instanceof ErrorNotifyResponseData || responseData instanceof FailureResponseData) {
      String message;
      if (responseData instanceof ErrorNotifyResponseData) {
        message = emptyIfNull(((ErrorNotifyResponseData) responseData).getErrorMessage());
      } else {
        message = emptyIfNull(((FailureResponseData) responseData).getErrorMessage());
      }
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .addFailureData(CIStepInfoUtils.getDefaultCIFailureDataInfo(message, ambiance))
                           .build())
          .build();
    }

    CITaskExecutionResponse ciTaskExecutionResponse;
    if (responseData instanceof TaskRunnerTaskResponse) {
      // task-runner returns a single, infra-agnostic TaskRunnerTaskResponse. Normalize it into a
      // K8s-shaped response so it flows through the existing infra-type switch below.
      // TODO: Generalize for VM / Docker once those land on task-runner (route to VmTaskExecutionResponse).
      ciTaskExecutionResponse = toK8sTaskExecutionResponseFromRunner((TaskRunnerTaskResponse) responseData);
    } else {
      ciTaskExecutionResponse = (CITaskExecutionResponse) responseData;
    }
    if (ciTaskExecutionResponse.getType() == CITaskExecutionResponse.Type.K8) {
      if (ciTaskExecutionResponse instanceof K8sTaskExecutionResponseFromRunner) {
        ciTaskExecutionResponse =
            ((K8sTaskExecutionResponseFromRunner) ciTaskExecutionResponse).toK8sTaskExecutionResponse();
      }
      return initialiseTaskUtils.handleK8sTaskExecutionResponse((K8sTaskExecutionResponse) ciTaskExecutionResponse);
    } else if (ciTaskExecutionResponse.getType() == CITaskExecutionResponse.Type.ECS) {
      return initialiseTaskUtils.handleEcsTaskExecutionResponse(ciTaskExecutionResponse);
    } else if (ciTaskExecutionResponse.getType() == CITaskExecutionResponse.Type.VM) {
      return containerStepInitHelper.handleVMTaskExecutionResponse((VmTaskExecutionResponse) ciTaskExecutionResponse);
    }
    throw new InvalidRequestException(format("Invalid infra type: %s", ciTaskExecutionResponse.getType()));
  }

  private static K8sTaskExecutionResponseFromRunner toK8sTaskExecutionResponseFromRunner(
      TaskRunnerTaskResponse taskResponse) {
    return K8sTaskExecutionResponseFromRunner.builder()
        .delegateMetaInfo(taskResponse.getDelegateMetaInfo())
        .commandExecutionStatus(taskResponse.getCommandExecutionStatus())
        .errorMessage(taskResponse.getErrorMessage())
        .build();
  }

  private InitTaskData prepareInitTask(
      Ambiance ambiance, InitContainerV2StepInfo stepParameters, boolean routeToRunner) {
    String logPrefix = initialiseTaskUtils.getLogPrefix(ambiance, "STEP");
    Map<String, StrategyExpansionData> strategyExpansionMap = new HashMap<>();
    List<ExecutionWrapperConfig> expandedExecutionElement = new ArrayList<>();
    boolean flexibleTemplatesEnabled = InjectUtils.IsFlexibleTemplatesEnabled(ambiance);
    for (ExecutionWrapperConfig config : stepParameters.getStepsExecutionConfig().getSteps()) {
      ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo =
          strategyHelper.expandExecutionWrapperConfig(config, Optional.empty(), flexibleTemplatesEnabled, ambiance);
      expandedExecutionElement.addAll(expandedExecutionWrapperInfo.getExpandedExecutionConfigs());
      strategyExpansionMap.putAll(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData());
    }

    stepParameters.setStepsExecutionConfig(StepsExecutionConfig.builder().steps(expandedExecutionElement).build());
    stepParameters.setStrategyExpansionMap(strategyExpansionMap);
    if (stepParameters.getInfrastructure().getType() == ContainerStepInfra.Type.KUBERNETES_DIRECT
        || stepParameters.getInfrastructure().getType() == ContainerStepInfra.Type.ECS_DIRECT) {
      Map<StepInfo, PluginCreationResponseList> pluginsData =
          containerStepV2PluginProvider.getPluginsDataV2(stepParameters, ambiance);
      stepParameters.setPluginsData(pluginsData);
    }
    List<TaskSelector> taskSelectors = getTaskSelectors(ambiance, stepParameters);

    String accountId = AmbianceUtils.getAccountId(ambiance);
    boolean longTimeoutsEnabled =
        featureFlagService.isEnabled(accountId, FeatureName.CDS_CONTAINER_STEP_GROUP_STAGE_TIMEOUT);

    if (longTimeoutsEnabled) {
      consumeExecutionConfig(ambiance);
      ParameterField<Timeout> timeout = resolveStageTimeout(stepParameters);
      initialiseTaskUtils.constructStageDetails(ambiance, stepParameters.getIdentifier(), stepParameters.getName(),
          StepOutcomeGroup.STEP_GROUP.name(), stepParameters.getInfrastructure(), timeout);
    }

    CIInitializeTaskParams buildSetupTaskParams = containerStepInitHelper.getBuildSetupTaskParams(
        stepParameters, ambiance, logPrefix, stepParameters.getStepGroupIdentifier(), taskSelectors, routeToRunner);

    if (!longTimeoutsEnabled) {
      consumeExecutionConfig(ambiance);
      initialiseTaskUtils.constructStageDetails(ambiance, stepParameters.getIdentifier(), stepParameters.getName(),
          StepOutcomeGroup.STEP_GROUP.name(), stepParameters.getInfrastructure());
    }

    TaskData taskData = initialiseTaskUtils.getTaskData(buildSetupTaskParams);
    return new InitTaskData(taskData, taskSelectors);
  }

  private ParameterField<Timeout> resolveStageTimeout(InitContainerV2StepInfo stepParameters) {
    if (stepParameters.getStageTimeout() == null || stepParameters.getStageTimeout().getValue() == null) {
      return null;
    }
    String timeoutValue = stepParameters.getStageTimeout().getValue();
    if (EngineExpressionEvaluator.hasExpressions(timeoutValue)) {
      log.warn("Stage timeout expression not resolved: {}, using default pod TTL", timeoutValue);
      return null;
    }
    try {
      Timeout parsed = Timeout.fromString(timeoutValue);
      return parsed != null ? ParameterField.createValueField(parsed) : null;
    } catch (Exception e) {
      log.warn("Invalid stage timeout value: {}, using default pod TTL", timeoutValue, e);
      return null;
    }
  }

  // DTO for common method to return InitTaskData used by Task and Async flow
  private static class InitTaskData {
    final TaskData taskData;
    final List<TaskSelector> taskSelectors;

    InitTaskData(TaskData taskData, List<TaskSelector> taskSelectors) {
      this.taskData = taskData;
      this.taskSelectors = taskSelectors;
    }
  }

  @NotNull
  private ArrayList<TaskSelector> getTaskSelectors(Ambiance ambiance, InitContainerV2StepInfo stepParameters) {
    ArrayList<TaskSelector> taskSelectors = AmbianceUtils.checkIfFeatureFlagEnabled(ambiance,
                                                FeatureName.CDS_CONTAINER_STEP_DELEGATE_SELECTOR_PRECEDENCE.name())
        ? getDelegateSelector(ambiance, stepParameters)
        : getConnectorDelegateSelector(ambiance, stepParameters);

    pluginUtils.logDelegateSelectorsList(taskSelectors, "InitContainer");
    return taskSelectors;
  }

  @NotNull
  private ArrayList<TaskSelector> getConnectorDelegateSelector(
      Ambiance ambiance, InitContainerV2StepInfo stepParameters) {
    if (stepParameters.getInfrastructure().getType() == ContainerStepInfra.Type.VM) {
      return new ArrayList<>();
    }
    if (stepParameters.getInfrastructure().getType() == ContainerStepInfra.Type.ECS_DIRECT) {
      ContainerEcsInfra containerEcsInfra = (ContainerEcsInfra) stepParameters.getInfrastructure();
      String connectorName = containerEcsInfra.getSpec().getConnectorRef().getValue();
      ConnectorDetails awsConnector =
          connectorUtils.getConnectorDetails(AmbianceUtils.getNgAccess(ambiance), connectorName);
      List<TaskSelector> connectorDelegateSelectors = ContainerSpecUtils.getConnectorDelegateSelectors(awsConnector);
      return new ArrayList<>(connectorDelegateSelectors);
    }
    ContainerK8sInfra containerK8sInfra = (ContainerK8sInfra) stepParameters.getInfrastructure();
    String connectorName = containerK8sInfra.getSpec().getConnectorRef().getValue();
    ConnectorDetails k8sConnector =
        connectorUtils.getConnectorDetails(AmbianceUtils.getNgAccess(ambiance), connectorName);
    List<TaskSelector> connectorDelegateSelectors = ContainerSpecUtils.getConnectorDelegateSelectors(k8sConnector);
    return new ArrayList<>(connectorDelegateSelectors);
  }

  @NotNull
  private ArrayList<TaskSelector> getDelegateSelector(Ambiance ambiance, InitContainerV2StepInfo stepParameters) {
    List<TaskSelector> highestPriorityDelegateSelectors = ContainerSpecUtils.getStepDelegateSelectors(stepParameters);
    if (EmptyPredicate.isEmpty(highestPriorityDelegateSelectors)) {
      return getConnectorDelegateSelector(ambiance, stepParameters);
    }
    return EmptyPredicate.isNotEmpty(highestPriorityDelegateSelectors)
        ? (ArrayList<TaskSelector>) highestPriorityDelegateSelectors
        : new ArrayList<>();
  }

  private void consumeExecutionConfig(Ambiance ambiance) {
    executionSweepingOutputService.consume(ambiance, ContainerStepConstants.CONTAINER_EXECUTION_CONFIG,
        containerExecutionConfig, StepCategory.STEP_GROUP.name());
  }
}
