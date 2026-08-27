/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.opa.step;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveBooleanParameter;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_LOG_PREFIX_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.ng.core.infrastructure.InfrastructureKind.VM;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep.VmPluginStepBuilder;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepOutputV2;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.metrics.service.api.MetricService;
import io.harness.opaclient.model.PolicySetData;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.AbstractContainerStepV2;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.container.utils.ContainerStepImageUtils;
import io.harness.steps.container.utils.ContainerStepResolverUtils;
import io.harness.steps.opa.OPAEvaluationStepParameters;
import io.harness.steps.plugin.ContainerStepOutcome;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;
import io.harness.telemetry.helpers.StepExecutionTelemetryEventDTO;
import io.harness.yaml.core.timeout.Timeout;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class OPAEvaluationStep extends AbstractContainerStepV2<StepElementParameters> {
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;
  @Inject ConnectorUtils connectorUtils;
  @Inject private ContainerStepImageUtils containerStepImageUtils;
  @Inject private OPAEvaluationStepHelper opaEvaluationStepHelper;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private MetricService metricService;

  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.OPA_EVALUATION).setStepCategory(StepCategory.STEP).build();
  private static final String OPA_CUSTOMER_INFRA_EVALUATION_DURATION_SECONDS =
      "opa_customer_infra_evaluation_duration_seconds";

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public long getTimeout(Ambiance ambiance, StepElementParameters stepElementParameters) {
    return Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis();
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    return super.executeAsyncAfterRbac(ambiance, stepElementParameters, inputPackage);
  }

  @Override
  public VmPluginStep getVmPluginStep(Ambiance ambiance, StepElementParameters stepElementParameters, Map envVarMap) {
    OPAEvaluationStepParameters opaEvaluationStepParameters =
        (OPAEvaluationStepParameters) stepElementParameters.getSpec();
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(
        AmbianceUtils.getNgAccess(ambiance), opaEvaluationStepParameters.getConnectorRef().getValue());

    String policySetId = getParameterFieldValue(opaEvaluationStepParameters.getPolicySetId());
    if (isEmpty(policySetId)) {
      throw new IllegalArgumentException("Policy Set ID is required");
    }

    String policySetOrgId = getParameterFieldValue(opaEvaluationStepParameters.getPolicySetOrgId());
    String policySetProjectId = getParameterFieldValue(opaEvaluationStepParameters.getPolicySetProjectId());

    String planExecutionId = ambiance.getPlanExecutionId();
    String evaluationId = getParameterFieldValue(opaEvaluationStepParameters.getEvaluationId());
    if (isEmpty(evaluationId)) {
      log.info("OPAEvaluationStep.getVmPluginStep: evaluationId not provided in parameters, fetching from "
              + "planExecutionId={}",
          planExecutionId);
      evaluationId = opaEvaluationStepHelper.fetchEvaluationIdFromPlanExecutionId(ambiance, planExecutionId);
      if (isEmpty(evaluationId)) {
        throw new IllegalArgumentException("Failed to fetch Evaluation ID from planExecutionId: " + planExecutionId);
      }
      log.debug("OPAEvaluationStep.getVmPluginStep: Fetched evaluationId={} from planExecutionId", evaluationId);
    } else {
      log.debug("OPAEvaluationStep.getVmPluginStep: Using evaluationId={} from step parameters", evaluationId);
    }

    PolicySetData policySetData =
        opaEvaluationStepHelper.fetchPolicySet(ambiance, policySetId, policySetOrgId, policySetProjectId);
    String gcsSignedUrl = opaEvaluationStepHelper.getPayloadGcsSignedUrl(ambiance, evaluationId);

    // VM: single-quote rewrite for JSON-safe JEXL functor substitution. See helper javadoc.
    Pair<String, Set<String>> policyDetailsResult =
        opaEvaluationStepHelper.convertPolicySetDataToJsonString(ambiance, policySetData, true);
    String policyDetailsJson = policyDetailsResult.getLeft();
    Set<String> secretRefExpressions = policyDetailsResult.getRight();

    Map<String, String> pluginEnvVars = opaEvaluationStepHelper.buildEnvironmentVariables(
        ambiance, policySetId, evaluationId, gcsSignedUrl, policyDetailsJson, policySetOrgId, policySetProjectId);

    // Merge with user-provided environment variables (user vars take precedence)
    Map<String, String> envVars = new HashMap<>(pluginEnvVars);
    Map<String, String> userEnvVars = getEnvironmentVariables(ambiance, opaEvaluationStepParameters);
    if (isNotEmpty(userEnvVars)) {
      envVars.putAll(userEnvVars);
    }

    addSecretExprsAsIndividualEnvVars(envVars, secretRefExpressions);

    // HARNESS_WORKSPACE is required by the plugin. VM workspace pattern from
    // RunnerRequestBuilderHelper.addCommonEnvironmentVariables() / RunnerRequestBuilderConstants.
    String harnessWorkspace = String.format("/tmp/harness/%s", ambiance.getStageExecutionId());
    envVars.put(HARNESS_WORKSPACE, harnessWorkspace);

    VmPluginStepBuilder vmPluginStepBuilder =
        VmPluginStep.builder()
            .image(containerStepImageUtils.getFullyQualifiedImageName(
                opaEvaluationStepParameters.getImage().getValue(), connectorDetails))
            .imageConnector(connectorDetails)
            .privileged(resolveBooleanParameter(opaEvaluationStepParameters.getPrivileged(), Boolean.FALSE))
            .pullPolicy(
                ContainerStepResolverUtils.resolveImagePullPolicy(opaEvaluationStepParameters.getImagePullPolicy()))
            .envVariables(envVars)
            .timeoutSecs(getTimeout(ambiance, stepElementParameters) / 1000);

    if (opaEvaluationStepParameters.getRunAsUser() != null
        && opaEvaluationStepParameters.getRunAsUser().getValue() != null) {
      vmPluginStepBuilder.runAsUser(opaEvaluationStepParameters.getRunAsUser().getValue().toString());
    }
    return vmPluginStepBuilder.build();
  }

  @Override
  public UnitStep getSerialisedStep(Ambiance ambiance, StepElementParameters stepElementParameters, String accountId,
      String logKey, long timeout, String parkedTaskId) {
    String stepIdentifier = stepElementParameters.getIdentifier();
    String planExecutionId = ambiance.getPlanExecutionId();
    log.info("OPAEvaluationStep.getSerialisedStep: Starting serialization. stepIdentifier={}, planExecutionId={}, "
            + "accountId={}, logKey={}, timeout={}ms, parkedTaskId={}",
        stepIdentifier, planExecutionId, accountId, logKey, timeout, parkedTaskId);

    OPAEvaluationStepParameters opaEvaluationStepParameters =
        (OPAEvaluationStepParameters) stepElementParameters.getSpec();

    String policySetId = getParameterFieldValue(opaEvaluationStepParameters.getPolicySetId());
    if (isEmpty(policySetId)) {
      throw new IllegalArgumentException("Policy Set ID is required");
    }

    String policySetOrgId = getParameterFieldValue(opaEvaluationStepParameters.getPolicySetOrgId());
    String policySetProjectId = getParameterFieldValue(opaEvaluationStepParameters.getPolicySetProjectId());

    String evaluationId = getParameterFieldValue(opaEvaluationStepParameters.getEvaluationId());
    if (isEmpty(evaluationId)) {
      log.info("OPAEvaluationStep.getSerialisedStep: evaluationId not provided in parameters, fetching from "
              + "planExecutionId={}",
          planExecutionId);
      evaluationId = opaEvaluationStepHelper.fetchEvaluationIdFromPlanExecutionId(ambiance, planExecutionId);
      if (isEmpty(evaluationId)) {
        throw new IllegalArgumentException("Failed to fetch Evaluation ID from planExecutionId: " + planExecutionId);
      }
      log.debug("OPAEvaluationStep.getSerialisedStep: Fetched evaluationId={} from planExecutionId", evaluationId);
    } else {
      log.debug("OPAEvaluationStep.getSerialisedStep: Using evaluationId={} from step parameters", evaluationId);
    }

    PolicySetData policySetData =
        opaEvaluationStepHelper.fetchPolicySet(ambiance, policySetId, policySetOrgId, policySetProjectId);
    String gcsSignedUrl = opaEvaluationStepHelper.getPayloadGcsSignedUrl(ambiance, evaluationId);

    // K8s: keep double quotes (lite-engine addon resolver regex needs them). See helper javadoc.
    String policyDetailsJson =
        opaEvaluationStepHelper.convertPolicySetDataToJsonString(ambiance, policySetData, false).getLeft();

    Map<String, String> pluginEnvVars = opaEvaluationStepHelper.buildEnvironmentVariables(
        ambiance, policySetId, evaluationId, gcsSignedUrl, policyDetailsJson, policySetOrgId, policySetProjectId);

    // Merge with user-provided environment variables (user vars take precedence)
    Map<String, String> envVars = new HashMap<>(pluginEnvVars);
    Map<String, String> userEnvVars = getEnvironmentVariables(ambiance, opaEvaluationStepParameters);
    if (isNotEmpty(userEnvVars)) {
      envVars.putAll(userEnvVars);
    }

    // Add HARNESS_LOG_PREFIX_VARIABLE for container log streaming
    envVars.put(HARNESS_LOG_PREFIX_VARIABLE, logKey);

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(
        AmbianceUtils.getNgAccess(ambiance), opaEvaluationStepParameters.getConnectorRef().getValue());

    String image = containerStepImageUtils.getFullyQualifiedImageName(
        opaEvaluationStepParameters.getImage().getValue(), connectorDetails);

    // Use stepElementParameters.getIdentifier() to get the YAML identifier (e.g., "opa_eval_policy-set-123")
    // This matches what was stored in StepInfoProto.getIdentifier() during port assignment
    String stepIdentifierForPort = stepElementParameters.getIdentifier();
    Integer port = getPort(ambiance, stepIdentifierForPort);

    log.info("OPAEvaluationStep.getSerialisedStep: Serializing step with image={}, envVars count={}, port={}", image,
        envVars.size(), port);

    UnitStep unitStep = ContainerUnitStepUtils.serializeStepWithStepParameters(port, parkedTaskId, logKey,
        stepElementParameters.getIdentifier(), getTimeout(ambiance, stepElementParameters), accountId,
        stepElementParameters.getName(), delegateCallbackTokenSupplier, ambiance, envVars, image,
        Collections.emptyList());

    String unitStepImage = unitStep.hasPlugin() ? unitStep.getPlugin().getImage() : "N/A";
    log.info("OPAEvaluationStep.getSerialisedStep: Successfully serialized step. stepIdentifier={}, unitStepId={}, "
            + "unitStepDisplayName={}, unitStepImage={}, unitStepTaskId={}, unitStepCallbackToken={}, "
            + "planExecutionId={}. "
            + "This serialized step will be sent to delegate, which will forward it to Lite Engine for execution.",
        stepIdentifier, unitStep.getId(), unitStep.getDisplayName(), unitStepImage, unitStep.getTaskId(),
        unitStep.getCallbackToken(), planExecutionId);

    return unitStep;
  }

  @Override
  public void handleForCallbackId(Ambiance ambiance, StepElementParameters containerStepInfo,
      List<String> allCallbackIds, String callbackId, ResponseData responseData) {
    String stepIdentifier = containerStepInfo.getIdentifier();
    String planExecutionId = ambiance.getPlanExecutionId();
    log.info("OPAEvaluationStep.handleForCallbackId: Received callback. stepIdentifier={}, callbackId={}, "
            + "allCallbackIds={}, planExecutionId={}, responseDataType={}",
        stepIdentifier, callbackId, allCallbackIds, planExecutionId,
        responseData != null ? responseData.getClass().getSimpleName() : "null");

    // Call parent FIRST - parent handles deserialization and abort logic
    // The framework automatically handles wait-notify for success responses
    super.handleForCallbackId(ambiance, containerStepInfo, allCallbackIds, callbackId, responseData);

    try {
      ResponseData deserializedResponse = serializedResponseDataHelper.deserialize(responseData);
      Object response = deserializedResponse;
      if (deserializedResponse instanceof BinaryResponseData) {
        response = referenceFalseKryoSerializer.asInflatedObject(((BinaryResponseData) deserializedResponse).getData());
      }

      if (response instanceof K8sTaskExecutionResponse) {
        K8sTaskExecutionResponse k8sResponse = (K8sTaskExecutionResponse) response;
        log.info("OPAEvaluationStep.handleForCallbackId: K8sTaskExecutionResponse status={}, errorMessage={}",
            k8sResponse.getCommandExecutionStatus(), k8sResponse.getErrorMessage());
      } else if (response instanceof ErrorNotifyResponseData) {
        ErrorNotifyResponseData errorResponse = (ErrorNotifyResponseData) response;
        log.error("OPAEvaluationStep.handleForCallbackId: Received ErrorNotifyResponseData. "
                + "Error={}, CallbackId={}, AllCallbackIds={}",
            errorResponse.getErrorMessage(), callbackId, allCallbackIds);
      }
    } catch (Exception e) {
      log.warn("OPAEvaluationStep.handleForCallbackId: Error during logging (non-critical). stepIdentifier={}, "
              + "callbackId={}, error={}",
          stepIdentifier, callbackId, e.getMessage(), e);
    }
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = stepParameters.getIdentifier();
    String planExecutionId = ambiance.getPlanExecutionId();
    log.info("OPAEvaluationStep.handleAsyncResponse: Processing async response. stepIdentifier={}, "
            + "responseDataMapSize={}, planExecutionId={}",
        stepIdentifier, responseDataMap.size(), planExecutionId);

    // Log response details before calling parent
    // Wrap in try-catch to ensure exceptions don't interfere with parent's processing
    try {
      for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
        ResponseData responseData = entry.getValue();
        log.info("OPAEvaluationStep.handleAsyncResponse: Response key={}, type={}, class={}", entry.getKey(),
            responseData.getClass().getSimpleName(), responseData.getClass().getName());

        if (responseData instanceof ErrorNotifyResponseData) {
          ErrorNotifyResponseData errorResponse = (ErrorNotifyResponseData) responseData;
          log.error("OPAEvaluationStep.handleAsyncResponse: Found ErrorNotifyResponseData. Key={}, Error={}",
              entry.getKey(), errorResponse.getErrorMessage());
        } else if (responseData instanceof K8sTaskExecutionResponse) {
          K8sTaskExecutionResponse k8sResponse = (K8sTaskExecutionResponse) responseData;
          log.info("OPAEvaluationStep.handleAsyncResponse: K8sTaskExecutionResponse. Key={}, Status={}, Error={}",
              entry.getKey(), k8sResponse.getCommandExecutionStatus(), k8sResponse.getErrorMessage());
        }
      }
    } catch (Exception e) {
      log.warn("OPAEvaluationStep.handleAsyncResponse: Error during logging (non-critical). stepIdentifier={}, "
              + "error={}",
          stepIdentifier, e.getMessage(), e);
    }

    // Call parent to process the async response
    StepResponse stepResponse = super.handleAsyncResponse(ambiance, stepParameters, responseDataMap);

    // Record duration metric for customer infrastructure evaluation
    recordCustomerInfraEvaluationDuration(ambiance, stepParameters, responseDataMap, stepResponse);

    // Log final response status
    try {
      log.info("OPAEvaluationStep.handleAsyncResponse: Step response status={} for step {}", stepResponse.getStatus(),
          stepIdentifier);
      if (stepResponse.getFailureInfo() != null) {
        log.error("OPAEvaluationStep.handleAsyncResponse: Step failed. Error={}, FailureTypes={}",
            stepResponse.getFailureInfo().getErrorMessage(), stepResponse.getFailureInfo().getFailureTypesList());
      }
    } catch (Exception e) {
      log.warn("OPAEvaluationStep.handleAsyncResponse: Error during final logging (non-critical). stepIdentifier={}, "
              + "error={}",
          stepIdentifier, e.getMessage(), e);
    }

    return stepResponse;
  }

  @Override
  public StepOutcome getAnyOutComeForStep(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    StageInfraDetails stageInfraDetails = containerStepExecutionResponseHelper.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();

    if (stageInfraType == StageInfraDetails.Type.K8) {
      StepStatusTaskResponseData stepStatusTaskResponseData =
          containerStepExecutionResponseHelper.filterK8StepResponse(responseDataMap);

      if (stepStatusTaskResponseData != null
          && stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
        StepMapOutput stepOutput = (StepMapOutput) stepStatusTaskResponseData.getStepStatus().getOutput();
        Map<String, String> outputVariables = processOutput(stepOutput);
        return StepOutcome.builder()
            .name("output")
            .outcome(ContainerStepOutcome.builder().outputVariables(outputVariables).build())
            .build();
      }
    }
    // For VM infrastructure, return null to let ContainerStepExecutionResponseHelper handle outcome creation
    // This avoids duplicate outcome errors since buildAndReturnStepResponseForVM() always creates an outcome
    // with name "output" automatically
    return null;
  }

  private Map<String, String> processOutput(StepMapOutput stepOutput) {
    Map<String, String> outputVariables = new HashMap<>();
    if (stepOutput != null && isNotEmpty(stepOutput.getMap())) {
      stepOutput.getMap().forEach((key, value) -> {
        if (value != null) {
          outputVariables.put(key, value.toString());
        }
      });
    }
    return outputVariables;
  }

  private Map<String, String> processVmTaskResponse(VmTaskExecutionResponse taskResponse) {
    Map<String, String> outputVariables = (taskResponse.getOutputs() == null || taskResponse.getOutputs().isEmpty())
        ? taskResponse.getOutputVars()
        : taskResponse.getOutputs().stream().collect(Collectors.toMap(StepOutputV2::getKey, StepOutputV2::getValue));
    return outputVariables != null ? outputVariables : new HashMap<>();
  }

  // VM-only: K8s sets HARNESS_SECRETS_LIST pod-wide via lite-engine, but on VM nothing exposes
  // step secrets to the plugin process, so we add per-secret env vars + HARNESS_SECRETS_LIST
  // ourselves for the OPA plugin's masker.
  private void addSecretExprsAsIndividualEnvVars(Map<String, String> envVars, Set<String> secretRefExpressions) {
    if (secretRefExpressions == null || secretRefExpressions.isEmpty()) {
      return;
    }
    List<String> secretEnvVarNames = new ArrayList<>();
    int idx = 0;
    for (String expr : secretRefExpressions) {
      if (isEmpty(expr)) {
        continue;
      }
      String envVarName = OPAEvaluationStepHelper.PLUGIN_OPA_SECRET_PREFIX + idx;
      envVars.put(envVarName, expr);
      secretEnvVarNames.add(envVarName);
      idx++;
    }
    if (!secretEnvVarNames.isEmpty()) {
      envVars.put(OPAEvaluationStepHelper.HARNESS_SECRETS_LIST, String.join(",", secretEnvVarNames));
    }
    log.debug("Added {} PLUGIN_OPA_SECRET_<i> env vars and HARNESS_SECRETS_LIST for OPA plugin masking", idx);
  }

  private Map<String, String> getEnvironmentVariables(Ambiance ambiance, OPAEvaluationStepParameters stepParameters) {
    Map<String, String> envVars = new HashMap<>();
    if (stepParameters.getEnvVariables() != null && stepParameters.getEnvVariables().getValue() != null) {
      envVars.putAll(stepParameters.getEnvVariables().getValue());
    }
    return envVars;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    OPAEvaluationStepParameters opaEvaluationStepParameters = (OPAEvaluationStepParameters) stepParameters.getSpec();
    connectorUtils.getConnectorDetails(
        AmbianceUtils.getNgAccess(ambiance), opaEvaluationStepParameters.getConnectorRef().getValue());
  }

  @Override
  public StepExecutionTelemetryEventDTO getStepExecutionTelemetryEventDTO(
      Ambiance ambiance, StepElementParameters stepElementParameters) {
    HashMap<String, Object> telemetryProperties = new HashMap<>();
    OPAEvaluationStepParameters opaEvaluationStepParameters =
        (OPAEvaluationStepParameters) stepElementParameters.getSpec();
    if (ParameterField.isNotNull(opaEvaluationStepParameters.getPrivileged())) {
      telemetryProperties.put(AbstractContainerStepV2.PRIVILEGED_FOR_CONTAINERIZED_STEPS_FEATURE,
          opaEvaluationStepParameters.getPrivileged().fetchFinalValue());
    }
    telemetryProperties.put(AbstractContainerStepV2.RUN_AS_USER_FOR_CONTAINERIZED_STEPS_FEATURE,
        ParameterField.isNotNull(opaEvaluationStepParameters.getRunAsUser()));
    return StepExecutionTelemetryEventDTO.builder()
        .stepType(STEP_TYPE.getType())
        .properties(telemetryProperties)
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> getStepDelegateSelectors(SpecParameters specParameters) {
    OPAEvaluationStepParameters opaEvaluationStepParameters = (OPAEvaluationStepParameters) specParameters;
    return opaEvaluationStepParameters.getDelegateSelectors();
  }

  @Override
  protected String getStepType() {
    return StepSpecTypeConstants.OPA_EVALUATION;
  }

  /**
   * Records duration metric for policy set evaluation on customer infrastructure.
   * This metric tracks how long the policy evaluation plugin executed on customer infrastructure.
   * Duration is extracted from StepStatusTaskResponseData.stepStatus.totalTimeTakenInMillis which
   * represents the actual execution time from when lite-engine starts executing the step to completion.
   */
  private void recordCustomerInfraEvaluationDuration(Ambiance ambiance, StepElementParameters stepParameters,
      Map<String, ResponseData> responseDataMap, StepResponse stepResponse) {
    if (isEmpty(responseDataMap)) {
      return;
    }

    try {
      String accountId = AmbianceUtils.getAccountId(ambiance);

      // Extract policy set ID directly from step parameters (more reliable than parsing identifier)
      OPAEvaluationStepParameters opaEvaluationStepParameters = (OPAEvaluationStepParameters) stepParameters.getSpec();
      String policySetId = getParameterFieldValue(opaEvaluationStepParameters.getPolicySetId());

      if (isEmpty(accountId) || isEmpty(policySetId)) {
        return;
      }

      // Extract execution time from response (works for both K8s and VM if StepStatusTaskResponseData is present)
      // For K8s: Always uses StepStatusTaskResponseData
      // For VM: May use StepStatusTaskResponseData in some flows, otherwise VmTaskExecutionResponse doesn't expose
      // execution time
      StepStatusTaskResponseData stepStatusTaskResponseData =
          containerStepExecutionResponseHelper.filterK8StepResponse(responseDataMap);

      if (stepStatusTaskResponseData == null || stepStatusTaskResponseData.getStepStatus() == null
          || stepStatusTaskResponseData.getStepStatus().getTotalTimeTakenInMillis() <= 0) {
        return;
      }

      long durationMs = stepStatusTaskResponseData.getStepStatus().getTotalTimeTakenInMillis();

      // Extract infra type
      String infraType = extractInfraType(ambiance);

      // Skip metric if infra type cannot be resolved (consistent with how critical fields are handled)
      if (isEmpty(infraType)) {
        log.debug("OPAEvaluationStep: Skipping metric recording - infra type could not be resolved for policySetId: {}",
            policySetId);
        return;
      }

      String status = stepResponse.getStatus() == Status.SUCCEEDED ? "pass" : "error";

      // Build metric context map with only account_id, infra_type, and status to avoid cardinality explosion
      ImmutableMap<String, String> metricContextMap = ImmutableMap.<String, String>builder()
                                                          .put("account_id", accountId)
                                                          .put("infra_type", infraType)
                                                          .put("status", status)
                                                          .build();

      // Record metric in milliseconds to match distribution bucket boundaries (which are converted to ms internally)
      // The 'unit: "s"' field is just documentation - buckets are always in milliseconds internally
      try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
        metricService.recordMetric(OPA_CUSTOMER_INFRA_EVALUATION_DURATION_SECONDS, durationMs);
        log.info("OPAEvaluationStep: Recorded duration metric - policySetId: {}, duration: {}ms ({}s), "
                + "accountId: {}, infraType: {}, status: {}",
            policySetId, durationMs, durationMs / 1000.0, accountId, infraType, status);
      }
    } catch (Exception ex) {
      log.error("OPAEvaluationStep: Failed to record duration metric: {}", ex.getMessage(), ex);
    }
  }

  /**
   * Extracts infrastructure type from the pipeline execution ambiance.
   * This is determined by checking the stage infrastructure details.
   *
   * @param ambiance Pipeline execution ambiance
   * @return Infrastructure type string ("KubernetesDirect" or "VM"), or empty string if unable to determine
   */
  private String extractInfraType(Ambiance ambiance) {
    try {
      StageInfraDetails stageInfraDetails = containerStepExecutionResponseHelper.getStageInfra(ambiance);
      if (stageInfraDetails != null && stageInfraDetails.getType() != null) {
        StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
        if (stageInfraType == StageInfraDetails.Type.K8) {
          return KUBERNETES_DIRECT;
        } else if (stageInfraType == StageInfraDetails.Type.VM) {
          return VM;
        }
      }
      // Return empty string if unable to determine (caller will skip metric)
      log.debug("OPAEvaluationStep: Unable to determine infra type from stage infra details");
      return "";
    } catch (Exception ex) {
      log.warn("OPAEvaluationStep: Failed to extract infra type from ambiance: {}", ex.getMessage());
      return "";
    }
  }
}
