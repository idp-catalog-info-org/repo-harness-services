/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_CD_INFRA_STEP;
import static io.harness.cd.beans.outcomes.CdOutcomeConstants.INFRA_STEP_OUTCOME;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.service.v1.InfrastructureConstants.INFRASTRUCTURE_GROUP;
import static io.harness.runner.request.builder.RunnerRequestBuilder.generateLogKey;
import static io.harness.unified.service.NGOutcomes.INFRA_V0_OUTCOME;
import static io.harness.unified.service.NGOutcomes.INFRA_V0_OUTCOME_YAML;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.beans.stages.parameters.ExecutionInfoKey;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.cd.beans.outcomes.InfraStepOutcome;
import io.harness.cd.beans.outcomes.InfraStepOutcome.InfraStepOutcomeKeys;
import io.harness.ci.execution.common.InfraEntityProcessor;
import io.harness.ci.execution.common.ProcessedInfraResult;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.execution.states.rollback.RollbackSweepingOutput;
import io.harness.ci.execution.states.rollback.StepRollbackDataHelper;
import io.harness.ci.states.V1.cd.helpers.UnifiedInfraStepOpaHelper;
import io.harness.common.utils.YamlParsingUtils;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CIStageOutputHelper;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.serializer.JsonUtils;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.unified.cd.infrastructure.InfraConfig;
import io.harness.unified.cd.infrastructure.InfraInfoConfig;
import io.harness.unified.cd.infrastructure.InfraType;
import io.harness.unified.cd.infrastructure.InfrastructureSpec;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.StageStatus;
import io.harness.utils.TemplateYamlEntityType;
import io.harness.utils.TemplateYamlGenerator;
import io.harness.utils.TemplateYamlResult;
import io.harness.utils.TemplateYamlSourceType;
import io.harness.utils.execution.ExecutionModeUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class UnifiedCDInfraStep implements AsyncExecutableWithRbac<UnifiedCDInfraStepParameters> {
  public static final String PLUGIN_HARNESS_KUBE_CONFIG_PATH = "PLUGIN_HARNESS_KUBE_CONFIG_PATH";
  public static final String HARNESS_KUBE_CONFIG_PATH = "HARNESS_KUBE_CONFIG_PATH";
  public static final String INFRA = "infra";
  public static final String ROLLBACK_DATA_OUTPUT_KEY = "ROLLBACK_DATA";
  public static final String ADD_RC_STEP = "addRcStep";

  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject private AccessControlClient accessControlClient;
  @Inject private InfraStepOutcomeHelper infraStepOutcomeHelper;
  @Inject RunnerSubmitTaskUtils runnerSubmitTaskUtils;
  @Inject ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject InfraEntityProcessor infraEntityProcessor;
  @Inject private CommonAbstractStepUtils commonAbstractStepUtils;
  @Inject SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private ResponseHandlerUtils responseHandlerUtils;
  @Inject private TemplateYamlGenerator templateYamlGenerator;
  @Inject private StepRollbackDataHelper stepRollbackDataHelper;
  @Inject private UnifiedInfraStepOpaHelper unifiedInfraStepOpaHelper;
  @Inject private CIStageOutputHelper ciStageOutputHelper;

  public static final String LOG_SUFFIX = "Execute";

  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(UNIFIED_CD_INFRA_STEP.getDisplayName()).setStepCategory(StepCategory.STEP).build();

  @Override
  public void validateResources(Ambiance ambiance, UnifiedCDInfraStepParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public Class<UnifiedCDInfraStepParameters> getStepParametersClass() {
    return UnifiedCDInfraStepParameters.class;
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, UnifiedCDInfraStepParameters stepParameters, StepInputPackage inputPackage) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    final String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    ExecutionMode executionMode = ambiance.getMetadata().getExecutionMode();
    boolean isInRollbackMode = ExecutionModeUtils.isRollbackMode(executionMode);

    ProcessedInfraResult result =
        infraEntityProcessor.getGetInfraTaskExecutionMetadata(ambiance, accountId, orgIdentifier, projectIdentifier,
            stepParameters.getServiceRef(), stepParameters.getEnvironmentRef(), stepParameters.getInfraId(),
            stepParameters.getInfraInputs(), stepParameters.getEnvBranchRef(), stepParameters.getEnvGroupRef());

    InfraInfoConfig infraInfoConfig = result.getInfraConfig().getInfraInfoConfig();
    InfrastructureSpec infrastructureSpec = infraInfoConfig.getWith();

    validateRuntimeAccessOrThrow(ambiance, infrastructureSpec);

    String[] infraKeyValues = infraInfoConfig.getInfraKey();
    infraKeyValues = (String[]) cdStepsExpressionResolver.updateExpressions(
        ambiance, infraKeyValues, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    if (isNotEmpty(result.getServiceRef())) {
      // Get infra key values - either from spec (POJO) or from inputs (template)

      EnvironmentOutcome environmentOutcome = result.getEnvironmentOutcome();
      String envType = (environmentOutcome != null && environmentOutcome.getType() != null)
          ? environmentOutcome.getType().name()
          : null;
      ExecutionInfoKey executionKey = createExecutionInfoKey(Scope.of(accountId, orgIdentifier, projectIdentifier),
          result.getServiceRef(), result.getEnvRef(), result.getInfraId(), infraKeyValues);
      if (!isInRollbackMode) {
        stepRollbackDataHelper.saveRollbackDataSweepingOutput(ambiance, executionKey);
        stepRollbackDataHelper.updateRollbackDataEntityMetadata(ambiance, executionKey, envType);
      }
    }

    // To be used in handle async for step response outcome
    InfraStepOutcome infraStepOutcome = infraStepOutcomeHelper.getInfraStepOutcome(ambiance, result.getInfraMetadata(),
        result.getEnvironmentOutcome(), result.getInfraConfig(), result.getServiceRef(), infraKeyValues);
    saveInfraOutcomeSweepingOutput(ambiance, infraStepOutcome);

    String taskId = submitInfraTask(ambiance, stepParameters, result.getInfraConfig());
    return getAsyncExecutableResponse(ambiance, taskId, result.getInfraId());
  }

  private String submitInfraTask(
      Ambiance ambiance, UnifiedCDInfraStepParameters stepParameters, InfraConfig infraConfig) {
    InfraInfoConfig infraInfoConfig = infraConfig.getInfraInfoConfig();
    return submitTask(ambiance, stepParameters, infraInfoConfig);
  }

  private String submitTask(
      Ambiance ambiance, UnifiedCDInfraStepParameters stepParameters, InfraInfoConfig infraInfoConfig) {
    if (InfraType.NO_OP_ACTION.equals(infraInfoConfig.getAction())) {
      return null;
    }

    String infraId = stepParameters.getInfraId().obtainValue();

    String templateYaml = fetchInfraTemplateAndPopulateInputs(ambiance, infraInfoConfig, infraId);

    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    if (stageInfraType == StageInfraDetails.Type.K8 || stageInfraType.name().equals("K8")) {
      return runnerSubmitTaskUtils.submitK8sTask(ambiance, infraId, stepParameters.getEnvVars(), templateYaml,
          (K8StageInfraDetails) stageInfraDetails, generateLogKey(ambiance, infraId), new HashMap<>(),
          new ArrayList<>());
    }

    return runnerSubmitTaskUtils.submitTaskByTemplate(
        ambiance, infraId, stepParameters.getEnvVars(), templateYaml, new ArrayList<>(), new HashMap<>());
  }

  /**
   * Fetch infrastructure template and populate with resolved inputs.
   * Similar pattern to fetchConfigFileTemplateAndAddInputs.
   *
   * Flow:
   * 1. Check if infraInfoConfig.uses exists (template type)
   * 2. Build TemplateYamlConfig with templateType, entityId, inputs
   * 3. Call templateYamlGenerator.getInfraTemplateYamlFromConfig
   * 4. Resolve expressions via cdStepsExpressionResolver
   * 5. Return populated template YAML
   *
   * @param ambiance execution context
   * @param infraInfoConfig infrastructure configuration
   * @param infraId infrastructure identifier
   * @return template YAML with inputs resolved
   */
  private String fetchInfraTemplateAndPopulateInputs(
      Ambiance ambiance, InfraInfoConfig infraInfoConfig, String infraId) {
    if (InfraType.NO_OP_ACTION.equals(infraInfoConfig.getAction())) {
      log.warn("No infra type (uses) found for infra {}, cannot fetch template", infraId);
      return null;
    }

    try {
      // 1. Get template YAML with defaults from ngInfra sweeping output
      TemplateYamlResult result =
          templateYamlGenerator.generateYamlWithMergedDefaults(ambiance, infraInfoConfig.getAction(), infraId,
              infraInfoConfig.getInputs(), TemplateYamlEntityType.INFRA, TemplateYamlSourceType.INFRA);
      if (result == null) {
        throw new InvalidRequestException(
            String.format("Could not fetch template to submit infra task for infra: [%s]", infraId));
      }
      String templateYaml = result.getYaml();
      if (isEmpty(templateYaml)) {
        log.warn("No template YAML generated for infra {}", infraId);
        return null;
      }

      // 2. Resolve any remaining expressions in the template
      cdStepsExpressionResolver.updateExpressions(
          ambiance, templateYaml, io.harness.expression.common.ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);

      return templateYaml;

    } catch (Exception e) {
      log.error("Failed to fetch and populate infra template for {}", infraId, e);
      throw new InvalidRequestException("Failed to generate infrastructure template: " + e.getMessage(), e);
    }
  }

  private AsyncExecutableResponse getAsyncExecutableResponse(Ambiance ambiance, String taskId, String commandUnit) {
    if (isEmpty(taskId)) {
      return AsyncExecutableResponse.newBuilder().addAllLogKeys(List.of(generateLogKey(ambiance, commandUnit))).build();
    }

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(taskId)
        .addAllLogKeys(List.of(generateLogKey(ambiance, commandUnit)))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, UnifiedCDInfraStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    ExecutionMode executionMode = ambiance.getMetadata().getExecutionMode();
    boolean isInRollbackMode = ExecutionModeUtils.isRollbackMode(executionMode);

    if (isEmpty(responseDataMap)) {
      StepResponseBuilder responseBuilder = StepResponse.builder().status(Status.SUCCEEDED);
      OptionalSweepingOutput infraStepOutput =
          sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME));
      if (infraStepOutput.isFound()) {
        InfraStepOutcome infraStepOutcome = (InfraStepOutcome) infraStepOutput.getOutput();

        callOpaForInfraRuntimeContext(ambiance, infraStepOutcome, responseBuilder);

        addStepOutcome(ambiance, responseBuilder, new ArrayList<>(), infraStepOutcome);
        Map<String, String> infraRollbackData = getInfraStepOutcomeForRollback(infraStepOutcome);
        if (isNotEmpty(infraRollbackData) && !isInRollbackMode) {
          stepRollbackDataHelper.updateStageRollbackData(infraRollbackData, Status.SUCCEEDED, ambiance, List.of(INFRA));
        }
      }

      return responseBuilder.build();
    }

    // If any of the responses are in serialized format, deserialize them
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      entry.setValue(serializedResponseDataHelper.deserialize(entry.getValue()));
    }

    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    List<Map<String, String>> outputVars = new ArrayList<>();

    if (stageInfraType == StageInfraDetails.Type.K8) {
      StepResponse response = handleK8AsyncFailureResponse(ambiance, responseDataMap);
      if (!Status.SUCCEEDED.equals(response.getStatus())) {
        if (!isInRollbackMode) {
          stepRollbackDataHelper.updateStageStatusForRollback(ambiance, StageStatus.FAILED);
        }
        return response;
      }
      outputVars = handleK8AsyncSuccessResponse(ambiance, responseDataMap);
    } else if (stageInfraType == StageInfraDetails.Type.VM || stageInfraType == StageInfraDetails.Type.DLITE_VM) {
      StepResponse response = handleVmAsyncFailureResponses(ambiance, responseDataMap);
      if (!Status.SUCCEEDED.equals(response.getStatus())) {
        if (!isInRollbackMode) {
          stepRollbackDataHelper.updateStageStatusForRollback(ambiance, StageStatus.FAILED);
        }
        return response;
      }
      outputVars = handleVmAsyncSuccessResponses(responseDataMap);
    }

    StepResponseBuilder responseBuilder = StepResponse.builder().status(Status.SUCCEEDED);
    if (isInRollbackMode) {
      updateRollbackDataWithKubeConfigPath(ambiance, outputVars);
    }

    publishKubeConfigPathAsStageOutput(ambiance, outputVars);

    OptionalSweepingOutput infraStepOutput =
        sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(INFRA_STEP_OUTCOME));
    if (infraStepOutput.isFound()) {
      InfraStepOutcome infraStepOutcome = (InfraStepOutcome) infraStepOutput.getOutput();

      callOpaForInfraRuntimeContext(ambiance, infraStepOutcome, responseBuilder);

      addStepOutcome(ambiance, responseBuilder, outputVars, infraStepOutcome);
      Map<String, String> infraRollbackData = getInfraStepOutcomeForRollback(infraStepOutcome);
      if (isNotEmpty(infraRollbackData) && !isInRollbackMode) {
        stepRollbackDataHelper.updateStageRollbackData(infraRollbackData, Status.SUCCEEDED, ambiance, List.of(INFRA));
      }
    }

    return responseBuilder.build();
  }

  private void updateRollbackDataWithKubeConfigPath(Ambiance ambiance, List<Map<String, String>> outputVars) {
    if (!containsKubeConfig(outputVars)) {
      return;
    }

    OptionalSweepingOutput rollback =
        sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(ROLLBACK_DATA_OUTPUT_KEY));
    if (rollback.isFound()) {
      RollbackSweepingOutput rollbackSweepingOutput = (RollbackSweepingOutput) rollback.getOutput();
      if (rollbackSweepingOutput.containsKey(INFRA)) {
        Map<String, String> infraOutput = (Map<String, String>) rollbackSweepingOutput.get(INFRA);
        if (infraOutput.containsKey(PLUGIN_HARNESS_KUBE_CONFIG_PATH)) {
          infraOutput.put(PLUGIN_HARNESS_KUBE_CONFIG_PATH, outputVars.get(0).get(PLUGIN_HARNESS_KUBE_CONFIG_PATH));
          sweepingOutputService.consumeUpsert(
              ambiance, ROLLBACK_DATA_OUTPUT_KEY, rollbackSweepingOutput, StepCategory.PIPELINE.name());
        }
      }
    }
  }

  private static boolean containsKubeConfig(List<Map<String, String>> outputVars) {
    return isNotEmpty(outputVars) && outputVars.get(0).containsKey(PLUGIN_HARNESS_KUBE_CONFIG_PATH);
  }

  /**
   * Publishes the kubeconfig path emitted by the infra plugin into the stage output accumulator, under both the
   * PLUGIN_ prefixed key and the NG parity key. Steps executing after this one pick these up as environment
   * variables through the existing output-variables-as-env injection, for every runtime (kubernetes, vm, cloud and
   * shell).
   */
  private void publishKubeConfigPathAsStageOutput(Ambiance ambiance, List<Map<String, String>> outputVars) {
    if (!containsKubeConfig(outputVars)) {
      return;
    }

    String kubeConfigPath = outputVars.get(0).get(PLUGIN_HARNESS_KUBE_CONFIG_PATH);
    if (isEmpty(kubeConfigPath)) {
      return;
    }

    Map<String, String> kubeConfigOutputs = new HashMap<>();
    kubeConfigOutputs.put(PLUGIN_HARNESS_KUBE_CONFIG_PATH, kubeConfigPath);
    kubeConfigOutputs.put(HARNESS_KUBE_CONFIG_PATH, kubeConfigPath);
    ciStageOutputHelper.populateCIStageOutputs(
        kubeConfigOutputs, AmbianceUtils.getAccountId(ambiance), ambiance.getStageExecutionId());
  }

  private void validateRuntimeAccessOrThrow(Ambiance ambiance, InfrastructureSpec infrastructureSpec) {
    // TODO: Handle template-based InfraInfoConfig where InfrastructureSpec.with is null
    // In template flow, infraInfoConfig.with will be null and infraInfoConfig.inputs (Map<String, Object>) is used
    // instead. Need to:
    // 1. Check if infrastructureSpec is null (template-based)
    // 2. If null, extract connector refs from infraInfoConfig.inputs
    // 3. Build EntityDetailProtoDTO from inputs map instead of InfrastructureSpec
    // For now, skip validation when infrastructureSpec is null (template-based flow)

    if (infrastructureSpec == null) {
      log.info("Skipping runtime access validation for template-based infrastructure (infrastructureSpec is null)");
      return;
    }

    final ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    final String principal = executionPrincipalInfo.getPrincipal();

    if (isEmpty(principal)) {
      log.warn("no principal found while executing the infrastructure step. skipping resource validation");
      return;
    }
    Set<EntityDetailProtoDTO> entityDetailsProto =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, infrastructureSpec);
    List<EntityDetail> entityDetails =
        entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(entityDetailsProto));

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }

  private void saveInfraOutcomeSweepingOutput(Ambiance ambiance, InfraStepOutcome infraOutcome) {
    if (infraOutcome != null) {
      sweepingOutputService.consume(ambiance, INFRA_STEP_OUTCOME, infraOutcome, "");
    }
  }

  private void addStepOutcome(Ambiance ambiance, StepResponseBuilder responseBuilder,
      List<Map<String, String>> outputVars, InfraStepOutcome infraStepOutcome) {
    // Fetch ngOutcomes sweeping output
    VariablesSweepingOutput ngInfraOutcome = null;
    OptionalSweepingOutput ngOutcomesSweepingOutput =
        sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(INFRA_V0_OUTCOME));
    if (ngOutcomesSweepingOutput.isFound()) {
      ngInfraOutcome = (VariablesSweepingOutput) ngOutcomesSweepingOutput.getOutput();
    }

    // Use infra V0 outcome from ngOutcomes if available
    if (ngInfraOutcome != null && ngInfraOutcome.containsKey(INFRA_V0_OUTCOME_YAML)) {
      String infraV0OutcomeYaml = (String) ngInfraOutcome.get(INFRA_V0_OUTCOME_YAML);
      if (isNotEmpty(infraV0OutcomeYaml)) {
        Map<String, Object> infraV0OutcomeMap = YamlParsingUtils.parseYamlStringToMap(infraV0OutcomeYaml);
        if (isNotEmpty(outputVars) && outputVars.size() == 1 && isNotEmpty(outputVars.get(0))) {
          infraV0OutcomeMap.putAll(outputVars.get(0));
        }
        VariablesSweepingOutput infraV0StepOutcome = new VariablesSweepingOutput();
        infraV0StepOutcome.putAll(infraV0OutcomeMap);

        if (infraStepOutcome != null) {
          infraV0StepOutcome.put(ADD_RC_STEP, infraStepOutcome.get(ADD_RC_STEP));
          infraV0StepOutcome.put(
              InfraStepNGOutcomeKeys.RELEASE_ID, infraStepOutcome.get(InfraStepOutcomeKeys.releaseId));
          Object infraName = infraStepOutcome.get(InfraStepOutcomeKeys.name);
          if (infraName != null) {
            infraV0StepOutcome.put(InfraStepNGOutcomeKeys.NAME, infraName);
          }
          Object infraIdentifier = infraStepOutcome.get(InfraStepOutcomeKeys.identifier);
          if (infraIdentifier != null) {
            infraV0StepOutcome.put(InfraStepNGOutcomeKeys.INFRA_IDENTIFIER, infraIdentifier);
          }
        }

        if (isNotEmpty(outputVars) && outputVars.size() == 1 && isNotEmpty(outputVars.get(0))) {
          infraV0StepOutcome.putAll(outputVars.get(0));
        }
        responseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                        .outcome(infraV0StepOutcome)
                                        .name("output")
                                        .group(INFRASTRUCTURE_GROUP)
                                        .build());
        return;
      }
    }

    // Fallback to existing infraStepOutcome
    if (isNotEmpty(outputVars) && outputVars.size() == 1 && isNotEmpty(outputVars.get(0))) {
      infraStepOutcome.putAll(outputVars.get(0));
    }
    responseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                    .outcome(infraStepOutcome)
                                    .name("output")
                                    .group(INFRASTRUCTURE_GROUP)
                                    .build());
  }

  private Map<String, String> getInfraStepOutcomeForRollback(InfraStepOutcome infraStepOutcome) {
    Map<String, String> rollbackData = new HashMap<>();
    String outcomeJson = JsonUtils.asJson(infraStepOutcome);
    rollbackData.put(StepRollbackDataHelper.ROLLBACK_DATA_OUTPUT_KEY, outcomeJson);
    return rollbackData;
  }

  private StepResponse handleK8AsyncFailureResponse(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = entry.getValue();
      if (responseData instanceof ErrorNotifyResponseData) {
        log.error("Received error response for step {}, error: {}", stepIdentifier,
            ((ErrorNotifyResponseData) responseData).getErrorMessage());

        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to execute infra step task", "Failed to execute infra step task");
      }

      StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) entry.getValue();
      if (stepStatusTaskResponseData == null) {
        log.error("stepStatusTaskResponseData should not be null for step {}", stepIdentifier);
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to execute infra step task", "Failed to execute infra step task");
      }

      if (stepStatusTaskResponseData.getStepStatus() != null
          && !StepExecutionStatus.SUCCESS.equals(stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus())) {
        log.error("stepStatusTaskResponseData should not be null for step {}", stepIdentifier);
        // Todo: check if output vars can be accessed here form stepStatusTaskResponseData
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to execute infra step task", "Failed to execute infra step task");
      }
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private StepResponse handleVmAsyncFailureResponses(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      String failureErrorMsg = "";
      if (entry.getValue() instanceof VmTaskExecutionResponse vmTaskExecutionResponse) {
        if (CommandExecutionStatus.FAILURE.equals(vmTaskExecutionResponse.getCommandExecutionStatus())) {
          log.error("Failed to execute infra step task: " + entry.getKey()
              + " reason: " + vmTaskExecutionResponse.getErrorMessage());

          Map<String, String> outputVariables = new HashMap<>();
          if (isNotEmpty(vmTaskExecutionResponse.getOutputs())) {
            outputVariables = responseHandlerUtils.getOutputVariables(vmTaskExecutionResponse.getOutputs());
          }
          failureErrorMsg = responseHandlerUtils.getFailureErrorMsg(
              outputVariables, "Failed to execute infra step task: ", vmTaskExecutionResponse.getErrorMessage());
          return responseHandlerUtils.getGenericFailedStepResponse(ambiance,
              "Failed to execute infra step task: " + vmTaskExecutionResponse.getErrorMessage(), failureErrorMsg);
        }
      }
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private List<Map<String, String>> handleK8AsyncSuccessResponse(
      Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);
    Map<String, VmTaskExecutionResponse> vmTaskResponse = new HashMap<>();
    List<Map<String, String>> outputVars = new ArrayList<>();

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) entry.getValue();
      Map<String, String> resolvedOutputVariables = new HashMap<>();

      if (isNotEmpty(stepStatusTaskResponseData.getStepStatus().getOutputV2())) {
        stepStatusTaskResponseData.getStepStatus().getOutputV2().forEach(
            outputVariable -> { resolvedOutputVariables.put(outputVariable.getKey(), outputVariable.getValue()); });
      }

      VmTaskExecutionResponse vmTaskExecutionResponse =
          VmTaskExecutionResponse.builder()
              .outputVars(resolvedOutputVariables)
              .outputs(stepStatusTaskResponseData.getStepStatus().getOutputV2())
              .build();
      vmTaskResponse.put(entry.getKey(), vmTaskExecutionResponse);
      if (isNotEmpty(vmTaskExecutionResponse.getOutputVars())) {
        outputVars.add(vmTaskExecutionResponse.getOutputVars());
      }
    }
    return outputVars;
  }

  private List<Map<String, String>> handleVmAsyncSuccessResponses(Map<String, ResponseData> responseDataMap) {
    Map<String, VmTaskExecutionResponse> vmTaskResponse = new HashMap<>();
    List<Map<String, String>> outputVars = new ArrayList<>();

    if (isNotEmpty(responseDataMap)) {
      for (Map.Entry<String, ResponseData> responseDataEntry : responseDataMap.entrySet()) {
        ResponseData responseData = responseDataEntry.getValue();
        String taskId = responseDataEntry.getKey();
        if (responseData instanceof VmTaskExecutionResponse vmTaskExecutionResponse) {
          vmTaskResponse.put(taskId, vmTaskExecutionResponse);
          if (isNotEmpty(vmTaskExecutionResponse.getOutputVars())) {
            outputVars.add(vmTaskExecutionResponse.getOutputVars());
          }
        }
      }
    }
    return outputVars;
  }

  private ExecutionInfoKey createExecutionInfoKey(
      Scope scope, String serviceRef, String envRef, String infraId, String[] infraKeyValues) {
    return ExecutionInfoKey.builder()
        .scope(scope)
        .serviceIdentifier(serviceRef)
        .envIdentifier(envRef)
        .infraIdentifier(infraId)
        .deploymentIdentifier(String.join("_", infraKeyValues))
        .build();
  }

  void callOpaForInfraRuntimeContext(
      Ambiance ambiance, InfraStepOutcome infraStepOutcome, StepResponseBuilder responseBuilder) {
    try {
      unifiedInfraStepOpaHelper.checkAndCallOpaForInfrastructureRuntimeContext(
          ambiance, infraStepOutcome, responseBuilder.build());
    } catch (PolicyEvaluationFailureException ex) {
      log.error("OPA policy evaluation failed for infrastructure step", ex);
      throw ex;
    }
  }
}
