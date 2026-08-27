/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.iacm;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_IACM_PREPARE_EXECUTION_STEP;
import static io.harness.ci.commonconstants.IACMExecutionConstants.IACM_OUTPUT;
import static io.harness.ci.commonconstants.IACMExecutionConstants.TASK_COMMAND_UNIT_MAP;
import static io.harness.ci.execution.states.helpers.ServiceStepUtility.generateLogKey;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.sdk.core.plugin.CommonAbstractStepExecutable.generateLogKey;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.entities.IACMUnifiedExecutionRequest;
import io.harness.beans.entities.IACMUnifiedExecutionResponse;
import io.harness.beans.entities.IACMUnifiedStepWrapper;
import io.harness.beans.entities.TerraformEndpointsData;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.commonconstants.IACMExecutionConstants;
import io.harness.ci.states.V1.cd.ResponseHandlerUtils;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.expression.common.ExpressionMode;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.iacm.beans.outcomes.IACMOutcome;
import io.harness.iacm.execution.IACMUnifiedUtils;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.utils.CDStepsExpressionResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IACM)
@Slf4j
public class UnifiedIACMPrepareStep implements AsyncExecutableWithRbac<UnifiedIACMPrepareParameters> {
  private final ObjectMapper objectMapper = new ObjectMapper();
  @Inject private CDStepsExpressionResolver stepsExpressionResolver;
  @Inject RunnerSubmitTaskUtils runnerSubmitTaskUtils;
  @Inject private IACMServiceUtils iacmServiceUtils;
  @Inject private IACMUnifiedUtils iacmUnifiedUtils;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private CommonAbstractStepUtils commonAbstractStepUtils;
  @Inject SerializedResponseDataHelper serializedResponseDataHelper;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(UNIFIED_IACM_PREPARE_EXECUTION_STEP.getDisplayName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public Class<UnifiedIACMPrepareParameters> getStepParametersClass() {
    return UnifiedIACMPrepareParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, UnifiedIACMPrepareParameters stepParameters) {
    // rbac validation should go here
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, UnifiedIACMPrepareParameters stepParameters, StepInputPackage inputPackage) {
    String workspaceId = stepParameters.getWorkspaceId() != null ? stepParameters.getWorkspaceId().obtainValue() : null;
    String remoteExecutionId =
        stepParameters.getRemoteExecutionId() != null ? stepParameters.getRemoteExecutionId().obtainValue() : null;
    String moduleId = stepParameters.getModuleTestId() != null ? stepParameters.getModuleTestId().obtainValue() : null;
    List<String> playbooks = ParameterField.isNotNull(stepParameters.getPlaybooks())
        ? (List<String>) stepParameters.getPlaybooks().fetchFinalValue()
        : null;
    List<String> inventories = ParameterField.isNotNull(stepParameters.getInventories())
        ? (List<String>) stepParameters.getInventories().fetchFinalValue()
        : null;
    IACMUnifiedExecutionRequest.WebhookInfo webhookInfo = null;
    if (isNotEmpty(stepParameters.getWebhookEventType())) {
      webhookInfo = IACMUnifiedExecutionRequest.WebhookInfo.builder()
                        .type(stepParameters.getWebhookEventType())
                        .connector(stepParameters.getWebhookConnector())
                        .link(stepParameters.getWebhookLink())
                        .repo(stepParameters.getWebhookRepo())
                        .build();
    }
    IACMUnifiedExecutionResponse unifiedExecutionData = iacmServiceUtils.createIACMUnifiedExecution(
        ambiance, false, workspaceId, webhookInfo, remoteExecutionId, moduleId, playbooks, inventories);

    // handle outcomes
    Map<String, String> outputs = unifiedExecutionData.getOutputs();
    stepsExpressionResolver.updateExpressions(ambiance, outputs, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    outputs.entrySet().removeIf(e -> e.getValue() == null || "null".equals(e.getValue()));
    IACMOutcome iacmOutcome = new IACMOutcome();
    iacmOutcome.putAll(outputs);
    sweepingOutputService.consume(ambiance, IACM_OUTPUT, iacmOutcome, StepCategory.STAGE.name());

    // handle env vars
    Map<String, String> envVars = unifiedExecutionData.getEnvVariables();
    envVars.entrySet().forEach(e
        -> stepsExpressionResolver.updateExpressions(ambiance, e,
            e.getKey().startsWith("NETRC_") ? ExpressionMode.RETURN_NULL_IF_UNRESOLVED
                                            : ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED));
    envVars.entrySet().removeIf(e -> e.getValue() == null || "null".equals(e.getValue()));
    // Ansible-specific: iac-server doesn't provide PLUGIN_ENDPOINT_VARIABLES for ansible flows
    enrichAnsibleWithEndpointVars(envVars, ambiance);
    IACMRelatedEnvVars iacmRelatedEnvVars = new IACMRelatedEnvVars();
    iacmRelatedEnvVars.putAll(envVars);
    sweepingOutputService.consume(
        ambiance, IACMExecutionConstants.IACM_RELATED_ENV_VARS, iacmRelatedEnvVars, StepCategory.STAGE.name());

    List<String> logKeys = new ArrayList<>();
    List<String> commandUnits = new ArrayList<>();
    // handle pre execution steps
    List<String> callbackIds = executePrepareExecutionSteps(unifiedExecutionData, logKeys, commandUnits, ambiance);
    return AsyncExecutableResponse.newBuilder()
        .addAllLogKeys(logKeys)
        .addAllUnits(commandUnits)
        .addAllCallbackIds(callbackIds)
        .build();
  }
  List<String> executePrepareExecutionSteps(IACMUnifiedExecutionResponse unifiedExecution, List<String> logKeys,
      List<String> commandUnits, Ambiance ambiance) {
    List<String> callbackIds = new ArrayList<>();
    if (isEmpty(unifiedExecution.getSteps())) {
      return callbackIds;
    }
    final String logBaseKey = LogStreamingStepClientFactory.getLogBaseKey(ambiance);
    TaskCommandUnitMap taskCommandUnitMap = new TaskCommandUnitMap();

    for (IACMUnifiedStepWrapper step : unifiedExecution.getSteps()) {
      String templateYaml = iacmUnifiedUtils.getYamlFromIACMUnifiedStepWrapper(ambiance, step);
      String taskId = submitTaskByTemplate(ambiance, step, templateYaml);
      taskCommandUnitMap.put(taskId, step.getId());
      callbackIds.add(taskId);
      commandUnits.add(step.getId());
      logKeys.add(generateLogKey(logBaseKey, step.getId()));
    }

    sweepingOutputService.consume(ambiance, TASK_COMMAND_UNIT_MAP, taskCommandUnitMap, StepCategory.STAGE.name());
    return callbackIds;
  }

  private String submitTaskByTemplate(Ambiance ambiance, IACMUnifiedStepWrapper step, String templateYaml) {
    String taskId;
    if (isK8sExecution(ambiance)) {
      taskId = runnerSubmitTaskUtils.submitK8sTask(ambiance, step.getId(), null, templateYaml,
          (K8StageInfraDetails) commonAbstractStepUtils.getStageInfra(ambiance), generateLogKey(ambiance, step.getId()),
          new HashMap<>(), new ArrayList<>());
    } else {
      taskId = runnerSubmitTaskUtils.submitTaskByTemplate(
          ambiance, step.getId(), null, templateYaml, new ArrayList<>(), new HashMap<>());
    }
    return taskId;
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, UnifiedIACMPrepareParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    List<UnitProgress> unitProgresses = new ArrayList<>();
    long stepStartTs = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long stepEndTs = System.currentTimeMillis();

    Status status = handleAsyncResponses(responseDataMap, ambiance, unitProgresses, stepStartTs, stepEndTs);
    final List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
    OptionalSweepingOutput optionalSweepingOutput =
        sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(IACM_OUTPUT));
    if (optionalSweepingOutput.isFound()) {
      IACMOutcome iacmOutcome = (IACMOutcome) optionalSweepingOutput.getOutput();
      stepOutcomes.add(StepResponse.StepOutcome.builder().name(IACM_OUTPUT).outcome(iacmOutcome).build());
    }

    return StepResponse.builder().status(status).stepOutcomes(stepOutcomes).unitProgressList(unitProgresses).build();
  }
  private Status handleAsyncResponses(Map<String, ResponseData> responseDataMap, Ambiance ambiance,
      List<UnitProgress> unitProgresses, long stepStartTs, long stepEndTs) {
    Status status = Status.SUCCEEDED;
    if (isNotEmpty(responseDataMap)) {
      TaskCommandUnitMap taskCommandUnitMap = getTaskCommandUnitMap(ambiance);

      for (Map.Entry<String, ResponseData> responseDataEntry : responseDataMap.entrySet()) {
        ResponseData responseData = serializedResponseDataHelper.deserialize(responseDataEntry.getValue());
        String taskId = responseDataEntry.getKey();

        CommandExecutionStatus commandExecutionStatus = isK8sExecution(ambiance)
            ? handleK8sExecutionResponse(responseData, taskId)
            : handleVmExecutionResponse(responseData, taskId);

        if (isNotEmpty(taskCommandUnitMap)) {
          addUnitProgress(unitProgresses, taskCommandUnitMap.get(taskId), stepStartTs, stepEndTs,
              commandExecutionStatus.getUnitStatus());
        }

        if (!CommandExecutionStatus.SUCCESS.equals(commandExecutionStatus)) {
          status = Status.FAILED;
        }
      }
    }
    return status;
  }

  private TaskCommandUnitMap getTaskCommandUnitMap(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(TASK_COMMAND_UNIT_MAP));
    return optionalSweepingOutput.isFound() ? (TaskCommandUnitMap) optionalSweepingOutput.getOutput() : null;
  }

  private static void addUnitProgress(List<UnitProgress> unitProgresses, String commandUnit, long iacmStepStartTs,
      long stepEndTs, UnitStatus unitStatus) {
    unitProgresses.add(UnitProgress.newBuilder()
                           .setStatus(unitStatus)
                           .setUnitName(commandUnit)
                           .setStartTime(iacmStepStartTs)
                           .setEndTime(stepEndTs)
                           .build());
  }

  // Ansible unified flow: the iac-server does not return PLUGIN_ENDPOINT_VARIABLES or IACM connectivity
  // vars for ansible (unlike workspace flows which get IACM_WORKSPACE_ID and rely on the runtime helper
  // to reconstruct the endpoint data). We must inject them here so the ansible plugin container receives
  // them regardless of infrastructure type (K8 or VM/Runner).
  private void enrichAnsibleWithEndpointVars(Map<String, String> envVars, Ambiance ambiance) {
    if (envVars.containsKey(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES)) {
      return;
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);

    envVars.putIfAbsent(IACMExecutionConstants.PLUGIN_STAGE_EXECUTION_ID, ambiance.getStageExecutionId());
    envVars.putIfAbsent(
        IACMExecutionConstants.PLUGIN_ENDPOINT_PIPELINE_STAGE_EXECUTION_ID, ambiance.getStageExecutionId());
    envVars.putIfAbsent(
        IACMExecutionConstants.HARNESS_IACM_SERVICE_ENDPOINT, iacmServiceUtils.getIacmServiceUrl(accountId));
    envVars.putIfAbsent(IACMExecutionConstants.HARNESS_IACM_SERVICE_TOKEN,
        iacmServiceUtils.generateJWTTokenWithCache(accountId, orgId, projectId));

    try {
      String endpointVars = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
          TerraformEndpointsData.builder()
              .base_url(iacmServiceUtils.getIacmServiceUrl(accountId))
              .org_id(orgId)
              .project_id(projectId)
              .account_id(accountId)
              .workspace_id(null)
              .module_id(null)
              .token(iacmServiceUtils.generateJWTTokenWithCache(accountId, orgId, projectId))
              .pipeline_execution_id(AmbianceUtils.getPipelineExecutionIdentifier(ambiance))
              .pipeline_stage_execution_id(ambiance.getStageExecutionId())
              .build());
      if (isNotEmpty(endpointVars)) {
        envVars.put(IACMExecutionConstants.PLUGIN_ENDPOINT_VARIABLES, endpointVars);
      }
    } catch (Exception e) {
      log.error("Failed to construct PLUGIN_ENDPOINT_VARIABLES for ansible unified execution", e);
    }

    try {
      envVars.putIfAbsent(IACMExecutionConstants.PLUGIN_GLOBAL_GATEWAY_PROVIDER_DOMAIN,
          iacmServiceUtils.getGGProviderDomain(accountId));
    } catch (Exception e) {
      log.warn("Failed to get GG provider domain for ansible unified execution", e);
    }
  }

  private boolean isK8sExecution(Ambiance ambiance) {
    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    return stageInfraType == StageInfraDetails.Type.K8 || stageInfraType.name().equals("K8");
  }

  private CommandExecutionStatus handleVmExecutionResponse(ResponseData responseData, String taskId) {
    if (responseData instanceof VmTaskExecutionResponse vmTaskExecutionResponse) {
      return vmTaskExecutionResponse.getCommandExecutionStatus();
    }
    log.error(
        "Failed to get task response for taskId: {}. ResponseData is not instance of VmTaskExecutionResponse" + taskId);
    return CommandExecutionStatus.FAILURE;
  }

  private CommandExecutionStatus handleK8sExecutionResponse(ResponseData responseData, String taskId) {
    if (responseData instanceof ErrorNotifyResponseData) {
      log.error("Received error response for taskId {}, error: {}", taskId,
          ((ErrorNotifyResponseData) responseData).getErrorMessage());
      return CommandExecutionStatus.FAILURE;
    }

    StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) responseData;
    if (stepStatusTaskResponseData == null) {
      log.error("stepStatusTaskResponseData should not be null for taskId {}", taskId);
      return CommandExecutionStatus.FAILURE;
    }

    return ResponseHandlerUtils.getCommandExecutionStatusForK8s(
        stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus());
  }
}