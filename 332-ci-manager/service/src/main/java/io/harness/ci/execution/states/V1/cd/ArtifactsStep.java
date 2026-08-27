/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states.V1.cd;

import static io.harness.beans.steps.CIStepInfoType.UNIFIED_ARTIFACTS_STEP;
import static io.harness.beans.steps.constants.PlanCreatorNodesConstants.ARTIFACTS_NODE_ID;
import static io.harness.cd.beans.outcomes.ArtifactImagePullSecretTaskMeta.OUTCOME_KEY;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.unified.service.NGOutcomes.NG_OUTCOMES;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.cd.artifacts.mapper.ArtifactDelegateResponseMapper;
import io.harness.cd.beans.ArtifactsSweepingOutput;
import io.harness.cd.beans.outcomes.ArtifactImagePullSecretTaskMeta;
import io.harness.cd.beans.outcomes.ArtifactTaskIdSweepingOutput;
import io.harness.cd.beans.outcomes.ArtifactsOutcome;
import io.harness.cd.beans.outcomes.ArtifactsOutcomeSweepingOutput;
import io.harness.cd.beans.outcomes.ServiceStepUnitStatusSweepingOutput;
import io.harness.cd.beans.outcomes.TokenBasedImagePullSecretRegistry;
import io.harness.ci.execution.common.MapBasedReferenceExtractor;
import io.harness.ci.execution.common.MapBasedValidator;
import io.harness.ci.execution.states.helpers.AmiArtifactStepHelper;
import io.harness.ci.execution.states.helpers.ArtifactStepUtils;
import io.harness.ci.execution.states.helpers.ServiceStepSweepingOutputHelper;
import io.harness.ci.execution.states.helpers.ServiceStepUtility;
import io.harness.ci.execution.states.rollback.StepRollbackDataHelper;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.common.ExpressionMode;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitStatus;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.plancreator.stages.v1.EmptyStepParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.runner.request.utils.RunnerSubmitTaskUtils;
import io.harness.serializer.JsonUtils;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.unified.cd.service.artifacts.ArtifactConfig;
import io.harness.unified.cd.service.artifacts.ArtifactType;
import io.harness.utils.CDStepsExpressionResolver;
import io.harness.utils.StageStatus;
import io.harness.utils.TemplateYamlEntityType;
import io.harness.utils.TemplateYamlGenerator;
import io.harness.utils.TemplateYamlResult;
import io.harness.utils.TemplateYamlSourceType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class ArtifactsStep implements AsyncExecutableWithRbac<EmptyStepParameters> {
  public static final String ARTIFACTS = "artifacts";
  public static final String PRIMARY = "primary";
  public static final String ARTIFACT = "artifact";
  public static final String SIDECARS = "sidecars";

  @Inject private ServiceStepSweepingOutputHelper serviceStepSweepingOutputHelper;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject RunnerSubmitTaskUtils runnerSubmitTaskUtils;
  @Inject private CommonAbstractStepUtils commonAbstractStepUtils;
  @Inject SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private ResponseHandlerUtils responseHandlerUtils;
  @Inject ImagePullSecretUtils imagePullSecretUtils;
  @Inject private StepRollbackDataHelper stepRollbackDataHelper;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private MapBasedValidator mapBasedValidator;
  @Inject private MapBasedReferenceExtractor mapBasedReferenceExtractor;
  @Inject private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Inject private ConnectorInputsMapper connectorInputsMapper;
  @Inject private TemplateYamlGenerator templateYamlGenerator;

  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(UNIFIED_ARTIFACTS_STEP.getDisplayName()).setStepCategory(StepCategory.STEP).build();
  private static final String ARTIFACT_CONNECTOR_INPUT_FIELD = "artifactConnector";

  @Override
  public void validateResources(Ambiance ambiance, EmptyStepParameters stepParameters) {}

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage) {
    List<String> callbackIds = new ArrayList<>();
    List<String> logKeys = new ArrayList<>();
    List<String> commandUnits = new ArrayList<>();

    OptionalSweepingOutput optionalSweepingOutput =
        serviceStepSweepingOutputHelper.fetchServiceArtifactsSweepingOutput(ambiance);

    if (optionalSweepingOutput.isFound()) {
      ArtifactsSweepingOutput artifactsSweepingOutput = (ArtifactsSweepingOutput) optionalSweepingOutput.getOutput();
      if (isNotEmpty(artifactsSweepingOutput.getArtifactsMetadataMap())) {
        log.debug("Processing artifacts");
        Map<String, ArtifactConfig> artifactConfigMap =
            ArtifactStepUtils.toArtifactConfigMap(artifactsSweepingOutput.getArtifactsMetadataMap());
        return processArtifacts(ambiance, artifactConfigMap, artifactsSweepingOutput.getEnvVars());
      }
    }
    return AsyncExecutableResponse.newBuilder()
        .addAllLogKeys(logKeys)
        .addAllUnits(commandUnits)
        .addAllCallbackIds(callbackIds)
        .build();
  }

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, EmptyStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    if (isEmpty(responseDataMap)) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    // If any of the responses are in serialized format, deserialize them
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      entry.setValue(serializedResponseDataHelper.deserialize(entry.getValue()));
    }

    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();

    // Collect per-artifact unit statuses for the parent's unit-progress timeline. Walks every
    // response (success or failure) and persists the result for UnifiedServiceStep to read.
    // Kept separate from the chain-control failure handlers below so we don't have to alter
    // their early-return semantics or message construction.
    collectAndPersistArtifactUnitStatuses(ambiance, responseDataMap);

    List<Map<String, String>> outputVars = new ArrayList<>();

    if (stageInfraType == StageInfraDetails.Type.K8) {
      StepResponse response = handleK8AsyncFailureResponse(ambiance, responseDataMap);
      if (!Status.SUCCEEDED.equals(response.getStatus())) {
        stepRollbackDataHelper.updateStageStatusForRollback(ambiance, StageStatus.FAILED);
        return response;
      }
      outputVars = handleK8AsyncSuccessResponse(ambiance, responseDataMap);
    } else if (stageInfraType == StageInfraDetails.Type.VM || stageInfraType == StageInfraDetails.Type.DLITE_VM) {
      StepResponse response = handleVmAsyncFailureResponses(ambiance, responseDataMap);
      if (!Status.SUCCEEDED.equals(response.getStatus())) {
        stepRollbackDataHelper.updateStageStatusForRollback(ambiance, StageStatus.FAILED);
        return response;
      }
      outputVars = handleVmAsyncSuccessResponses(responseDataMap);
    }

    if (isNotEmpty(outputVars)) {
      // Update cdng ArtifactsOutcome present in ngOutcomes with delegate response data derived from plugin output vars.
      // This ensures downstream consumers of ngOutcomes get fully-resolved artifact outcomes (config + delegate
      // response).
      updateNgArtifactsOutcomeWithDelegateResponseIfPresent(ambiance, outputVars);

      Map<String, String> rollbackDataArtifactVars = getRollbackDataArtifactVars(outputVars);
      stepRollbackDataHelper.updateStageRollbackData(
          rollbackDataArtifactVars, Status.SUCCEEDED, ambiance, List.of(ARTIFACTS));
      ArtifactsOutcome artifactsOutcome = prepareArtifactsOutcome(outputVars, fetchArtifactConfigIdMap(ambiance));
      saveArtifactsOutcomeSweepingOutput(ambiance, artifactsOutcome);
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private Map<String, ArtifactConfig> fetchArtifactConfigIdMap(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput =
        serviceStepSweepingOutputHelper.fetchServiceArtifactsSweepingOutput(ambiance);
    if (!optionalSweepingOutput.isFound()) {
      return Map.of();
    }
    ArtifactsSweepingOutput artifactsSweepingOutput = (ArtifactsSweepingOutput) optionalSweepingOutput.getOutput();
    if (artifactsSweepingOutput == null || isEmpty(artifactsSweepingOutput.getArtifactsMetadataMap())) {
      return Map.of();
    }
    return ArtifactStepUtils.toArtifactConfigIdMap(artifactsSweepingOutput.getArtifactsMetadataMap());
  }

  private void updateNgArtifactsOutcomeWithDelegateResponseIfPresent(
      Ambiance ambiance, List<Map<String, String>> outputVarsList) {
    try {
      // Fetch ngOutcomes once and reuse for this update.
      OptionalSweepingOutput ngOutcomesSweepingOutput =
          sweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(NG_OUTCOMES));
      if (!ngOutcomesSweepingOutput.isFound()) {
        return;
      }

      VariablesSweepingOutput ngOutcomes = (VariablesSweepingOutput) ngOutcomesSweepingOutput.getOutput();
      if (ngOutcomes == null || !ngOutcomes.containsKey(ARTIFACTS)) {
        return;
      }

      String artifactsYamlString = (String) ngOutcomes.get(ARTIFACTS);
      if (isEmpty(artifactsYamlString)) {
        return;
      }

      io.harness.cdng.artifact.outcome.ArtifactsOutcome artifactsOutcomeFromNg =
          YamlUtils.read(artifactsYamlString, io.harness.cdng.artifact.outcome.ArtifactsOutcome.class);
      if (artifactsOutcomeFromNg == null) {
        return;
      }

      io.harness.cdng.artifact.outcome.ArtifactOutcome updatedPrimary = artifactsOutcomeFromNg.getPrimary();
      io.harness.cdng.artifact.outcome.SidecarsOutcome updatedSidecars =
          new io.harness.cdng.artifact.outcome.SidecarsOutcome();
      if (artifactsOutcomeFromNg.getSidecars() != null) {
        updatedSidecars.putAll(artifactsOutcomeFromNg.getSidecars());
      }

      boolean didUpdate = false;

      for (Map<String, String> outputVars : outputVarsList) {
        if (outputVars == null || isEmpty(outputVars.get(ARTIFACTS))) {
          continue;
        }

        Map<String, Object> currentArtifactsJsonOutputMap;
        try {
          currentArtifactsJsonOutputMap = JsonUtils.asMap(outputVars.get(ARTIFACTS));
        } catch (Exception e) {
          log.warn("Failed to parse plugin artifacts output JSON for ngOutcomes update", e);
          continue;
        }

        // Update primary artifact
        if (currentArtifactsJsonOutputMap.containsKey(PRIMARY) && updatedPrimary != null) {
          Object primaryObj = currentArtifactsJsonOutputMap.get(PRIMARY);
          if (primaryObj instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> primaryArtifactMap = (Map<String, Object>) primaryObj;
            ArtifactDelegateResponse delegateResponse = toArtifactDelegateResponse(primaryArtifactMap);
            if (delegateResponse != null) {
              try {
                updatedPrimary = ArtifactDelegateResponseMapper.updateArtifactOutcomeWithDelegateResponse(
                    updatedPrimary, delegateResponse);
                didUpdate = true;
              } catch (Exception ex) {
                log.warn("Failed to update primary artifact outcome in ngOutcomes using delegate response", ex);
              }
            }
          }
        }

        // Update sidecar artifacts
        if (currentArtifactsJsonOutputMap.containsKey(SIDECARS) && isNotEmpty(updatedSidecars)) {
          Object sidecarsObj = currentArtifactsJsonOutputMap.get(SIDECARS);
          if (sidecarsObj instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, Object> sidecarsMap = (Map<String, Object>) sidecarsObj;
            for (Map.Entry<String, Object> sidecarEntry : sidecarsMap.entrySet()) {
              String sidecarId = sidecarEntry.getKey();
              io.harness.cdng.artifact.outcome.ArtifactOutcome existingSidecarOutcome = updatedSidecars.get(sidecarId);
              if (existingSidecarOutcome == null) {
                continue;
              }

              if (!(sidecarEntry.getValue() instanceof Map)) {
                continue;
              }

              @SuppressWarnings("unchecked")
              Map<String, Object> sidecarArtifactMap = (Map<String, Object>) sidecarEntry.getValue();
              ArtifactDelegateResponse delegateResponse = toArtifactDelegateResponse(sidecarArtifactMap);
              if (delegateResponse == null) {
                continue;
              }

              try {
                io.harness.cdng.artifact.outcome.ArtifactOutcome updatedSidecarOutcome =
                    ArtifactDelegateResponseMapper.updateArtifactOutcomeWithDelegateResponse(
                        existingSidecarOutcome, delegateResponse);
                updatedSidecars.put(sidecarId, updatedSidecarOutcome);
                didUpdate = true;
              } catch (Exception ex) {
                log.warn(String.format(
                             "Failed to update sidecar artifact outcome [%s] in ngOutcomes using delegate response",
                             sidecarId),
                    ex);
              }
            }
          }
        }
      }

      if (!didUpdate) {
        return;
      }

      io.harness.cdng.artifact.outcome.ArtifactsOutcome updatedArtifactsOutcome =
          io.harness.cdng.artifact.outcome.ArtifactsOutcome.builder()
              .primary(updatedPrimary)
              .sidecars(updatedSidecars)
              .build();

      ngOutcomes.put(ARTIFACTS, YamlUtils.writeYamlString(updatedArtifactsOutcome));
      sweepingOutputService.consumeUpsert(ambiance, NG_OUTCOMES, ngOutcomes, StepCategory.STAGE.name());
    } catch (Exception e) {
      log.warn("Failed to update ngOutcomes artifacts outcome with delegate response data", e);
    }
  }

  private ArtifactDelegateResponse toArtifactDelegateResponse(Map<String, Object> artifactMap) {
    if (isEmpty(artifactMap)) {
      return null;
    }

    Map<String, Object> unknowns = new HashMap<>(artifactMap);
    Map<String, String> stringOutputVars = new HashMap<>();
    artifactMap.forEach((k, v) -> {
      if (v instanceof String) {
        stringOutputVars.put(k, (String) v);
      }
    });

    try {
      return ArtifactDelegateResponseMapper.toDelegateResponse(stringOutputVars, unknowns);
    } catch (Exception e) {
      log.warn("Failed to map plugin artifact output vars to delegate response", e);
      return null;
    }
  }

  private Map<String, String> getPrimaryArtifactVars(List<Map<String, String>> outputVars) {
    return outputVars.stream()
        .filter(map -> "true".equals(map.get("PLUGIN_ARTIFACT_PRIMARY_ARTIFACT")))
        .findFirst()
        .orElse(new HashMap<>());
  }

  private Map<String, String> getRollbackDataArtifactVars(List<Map<String, String>> outputVars) {
    Map<String, String> combinedVars = new HashMap<>();
    Map<String, Object> combinedArtifacts = new HashMap<>();

    // Extract primary artifact data
    Map<String, String> primaryVars = getPrimaryArtifactVars(outputVars);
    if (isNotEmpty(primaryVars) && primaryVars.containsKey(ARTIFACTS)) {
      Map<String, Object> primaryArtifactsMap = JsonUtils.asMap(primaryVars.get(ARTIFACTS));
      if (primaryArtifactsMap.containsKey(PRIMARY)) {
        combinedArtifacts.put(PRIMARY, primaryArtifactsMap.get(PRIMARY));
      }
    }

    // Extract sidecar artifacts data
    Map<String, Object> allSidecars = new HashMap<>();
    for (Map<String, String> outputVar : outputVars) {
      if (outputVar.containsKey(ARTIFACTS)) {
        Map<String, Object> artifactsMap = JsonUtils.asMap(outputVar.get(ARTIFACTS));
        if (artifactsMap.containsKey(SIDECARS)) {
          Map<String, Object> sidecarsMap = (Map<String, Object>) artifactsMap.get(SIDECARS);
          allSidecars.putAll(sidecarsMap);
        }
      }
    }

    // Add sidecars to combined artifacts if present
    if (isNotEmpty(allSidecars)) {
      combinedArtifacts.put(SIDECARS, allSidecars);
    }

    // Convert combined artifacts to JSON string and add to rollback key only
    if (isNotEmpty(combinedArtifacts)) {
      String combinedArtifactsJson = JsonUtils.asJson(combinedArtifacts);
      combinedVars.put(StepRollbackDataHelper.ROLLBACK_DATA_OUTPUT_KEY, combinedArtifactsJson);
    }

    return combinedVars;
  }

  private StepResponse handleVmAsyncFailureResponses(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      String failureErrorMsg = "";
      if (entry.getValue() instanceof VmTaskExecutionResponse vmTaskExecutionResponse) {
        if (CommandExecutionStatus.FAILURE.equals(vmTaskExecutionResponse.getCommandExecutionStatus())) {
          log.error("Failed to fetch artifact metadata for taskId: " + entry.getKey()
              + " reason: " + vmTaskExecutionResponse.getErrorMessage());

          Map<String, String> outputVariables = new HashMap<>();
          if (isNotEmpty(vmTaskExecutionResponse.getOutputs())) {
            outputVariables = responseHandlerUtils.getOutputVariables(vmTaskExecutionResponse.getOutputs());
            ArtifactsOutcome artifactsOutcome =
                prepareArtifactsOutcome(List.of(outputVariables), fetchArtifactConfigIdMap(ambiance));
            saveArtifactsOutcomeSweepingOutput(ambiance, artifactsOutcome);
          }
          failureErrorMsg = responseHandlerUtils.getFailureErrorMsg(
              outputVariables, "Failed to fetch artifact metadata: ", vmTaskExecutionResponse.getErrorMessage());
          return responseHandlerUtils.getGenericFailedStepResponse(ambiance,
              "Failed to fetch artifact metadata: " + vmTaskExecutionResponse.getErrorMessage(), failureErrorMsg);
        }
      }
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private void collectAndPersistArtifactUnitStatuses(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    Map<String, String> taskIdToArtifactId = resolveTaskIdToArtifactId(ambiance);
    Map<String, UnitStatus> statuses = new HashMap<>();
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      // Only record a status when we can resolve the callback id back to an artifact id. If the
      // mapping is missing, skip rather than surface a synthetic row keyed by the raw taskId.
      String artifactId = taskIdToArtifactId.get(entry.getKey());
      if (isNotEmpty(artifactId)) {
        statuses.put(artifactId, ResponseHandlerUtils.getUnitStatus(entry.getValue()));
      }
    }
    serviceStepSweepingOutputHelper.saveArtifactUnitStatusesSweepingOutput(
        ambiance, ServiceStepUnitStatusSweepingOutput.builder().statuses(statuses).build());
  }

  private Map<String, String> resolveTaskIdToArtifactId(Ambiance ambiance) {
    OptionalSweepingOutput out = serviceStepSweepingOutputHelper.fetchArtifactTaskIdSweepingOutput(ambiance);
    if (out.isFound() && out.getOutput() instanceof ArtifactTaskIdSweepingOutput existing
        && isNotEmpty(existing.getTaskIdToArtifactId())) {
      return existing.getTaskIdToArtifactId();
    }
    return new HashMap<>();
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

  private StepResponse handleK8AsyncFailureResponse(Ambiance ambiance, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = entry.getValue();
      if (responseData instanceof ErrorNotifyResponseData) {
        log.error("Received error response for step {}, error: {}", stepIdentifier,
            ((ErrorNotifyResponseData) responseData).getErrorMessage());
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to fetch artifact metadata", "Failed to fetch artifact metadata");
      }

      StepStatusTaskResponseData stepStatusTaskResponseData = (StepStatusTaskResponseData) entry.getValue();
      if (stepStatusTaskResponseData == null) {
        log.error("stepStatusTaskResponseData should not be null for step {}", stepIdentifier);
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to fetch artifact metadata", "Failed to fetch artifact metadata");
      }

      if (stepStatusTaskResponseData.getStepStatus() != null
          && !StepExecutionStatus.SUCCESS.equals(stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus())) {
        // Todo: check if output vars can be accessed here form stepStatusTaskResponseData
        return responseHandlerUtils.getGenericFailedStepResponse(
            ambiance, "Failed to fetch artifact metadata", "Failed to fetch artifact metadata");
      }
    }

    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private ArtifactsOutcome prepareArtifactsOutcome(
      List<Map<String, String>> outputVarsList, Map<String, ArtifactConfig> artifactConfigById) {
    ArtifactsOutcome artifactsOutcome = new ArtifactsOutcome();
    for (Map<String, String> outputVars : outputVarsList) {
      if (isNotEmpty(outputVars)) {
        if (outputVars.containsKey(ARTIFACTS)) {
          Map<String, Object> currentArtifactsJsonOutputMap = getArtifactJsonOutputMap(outputVars);
          handlePrimaryArtifactOutputResponse(artifactsOutcome, outputVars, currentArtifactsJsonOutputMap);
          handleSidecarsOutputResponse(artifactsOutcome, currentArtifactsJsonOutputMap);
          addImagePullSecretsToArtifacts(artifactsOutcome, currentArtifactsJsonOutputMap, artifactConfigById);
        }
        // this is post removal of artifact and artifacts keys
        artifactsOutcome.putAll(outputVars);
      }
    }
    return artifactsOutcome;
  }

  private void addImagePullSecretsToArtifacts(ArtifactsOutcome artifactsOutcome,
      Map<String, Object> currentArtifactsJsonOutputMap, Map<String, ArtifactConfig> artifactConfigById) {
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> imagePullSecretMeta =
        (Map<String, Map<String, Object>>) artifactsOutcome.getOrDefault(
            OUTCOME_KEY, new HashMap<String, Map<String, Object>>());

    if (currentArtifactsJsonOutputMap.containsKey(PRIMARY)) {
      Map<String, Object> primaryArtifact = (Map<String, Object>) currentArtifactsJsonOutputMap.get(PRIMARY);
      addImagePullSecretForArtifact(primaryArtifact, PRIMARY, imagePullSecretMeta, artifactConfigById);
    }

    if (currentArtifactsJsonOutputMap.containsKey(SIDECARS)) {
      Map<String, Object> sidecars = (Map<String, Object>) currentArtifactsJsonOutputMap.get(SIDECARS);
      for (Map.Entry<String, Object> sidecarEntry : sidecars.entrySet()) {
        String sidecarId = sidecarEntry.getKey();
        Map<String, Object> sidecarArtifact = (Map<String, Object>) sidecarEntry.getValue();
        addImagePullSecretForArtifact(sidecarArtifact, sidecarId, imagePullSecretMeta, artifactConfigById);
      }
    }

    if (isNotEmpty(imagePullSecretMeta)) {
      artifactsOutcome.put(OUTCOME_KEY, imagePullSecretMeta);
    }
  }

  private void addImagePullSecretForArtifact(Map<String, Object> artifact, String artifactIdentifier,
      Map<String, Map<String, Object>> imagePullSecretMeta, Map<String, ArtifactConfig> artifactConfigById) {
    String artifactType = (String) artifact.get("type");
    String imagePullSecretExpression =
        imagePullSecretUtils.getImagePullSecretExpression(artifactType, artifactIdentifier, artifact);
    if (imagePullSecretExpression != null) {
      artifact.put("imagePullSecretExp", imagePullSecretExpression);
    }
    TokenBasedImagePullSecretRegistry.forArtifactType(artifactType).ifPresent(registry -> {
      ArtifactConfig artifactConfig = artifactConfigById == null ? null : artifactConfigById.get(artifactIdentifier);
      Map<String, String> envVars = new HashMap<>();
      registry.getEnvVarFields().forEach(
          (envVar, fieldKey) -> envVars.put(envVar, resolveArtifactField(artifact, artifactConfig, fieldKey)));
      imagePullSecretMeta.put(artifactIdentifier,
          ArtifactImagePullSecretTaskMeta.builder()
              .connectorRef(resolveArtifactField(artifact, artifactConfig, ARTIFACT_CONNECTOR_INPUT_FIELD))
              .secretTaskId(TokenBasedImagePullSecretRegistry.imagePullSecretTaskId(artifactIdentifier))
              .binaryName(registry.getBinaryName())
              .envVars(envVars)
              .build()
              .toMap());
    });
  }

  private static String resolveArtifactField(Map<String, Object> artifact, ArtifactConfig artifactConfig, String key) {
    String fromPluginOutput = stringOrEmpty(artifact.get(key));
    if (isNotEmpty(fromPluginOutput)) {
      return fromPluginOutput;
    }
    if (artifactConfig == null || artifactConfig.getInputs() == null) {
      return "";
    }
    return stringOrEmpty(artifactConfig.getInputs().get(key));
  }

  private static String stringOrEmpty(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private Map<String, Object> getArtifactJsonOutputMap(Map<String, String> outputVars) {
    String artifactsJsonOutputString = outputVars.get(ARTIFACTS);
    outputVars.remove(ARTIFACTS);
    return JsonUtils.asMap(artifactsJsonOutputString);
  }

  private void handleSidecarsOutputResponse(
      ArtifactsOutcome artifactsOutcome, Map<String, Object> currentArtifactsJsonOutputMap) {
    Map<String, Object> existingSidecarsJsonOutputMap;
    if (currentArtifactsJsonOutputMap.containsKey(SIDECARS)) {
      if (artifactsOutcome.containsKey(SIDECARS)) {
        // below gets updated sidecars node, contains sidecars output with first level key as identifier
        existingSidecarsJsonOutputMap = (Map<String, Object>) artifactsOutcome.get(SIDECARS);

        Map<String, Object> currentSidecarsJsonOutputMap =
            (Map<String, Object>) currentArtifactsJsonOutputMap.get(SIDECARS);

        existingSidecarsJsonOutputMap.putAll(currentSidecarsJsonOutputMap);
      } else {
        // if this is first response that contains artifacts node
        artifactsOutcome.put(SIDECARS, currentArtifactsJsonOutputMap.get(SIDECARS));
      }
    }
  }

  private static void handlePrimaryArtifactOutputResponse(ArtifactsOutcome artifactsOutcome,
      Map<String, String> outputVars, Map<String, Object> currentArtifactsJsonOutputMap) {
    if (currentArtifactsJsonOutputMap.containsKey(PRIMARY)) {
      if (artifactsOutcome.containsKey(PRIMARY)) {
        throw new InvalidRequestException("There can be only one primary artifact, Found multiple");
      }
      artifactsOutcome.put(PRIMARY, currentArtifactsJsonOutputMap.get(PRIMARY));
    }

    if (outputVars.containsKey(ARTIFACT)) {
      outputVars.remove(ARTIFACT);
    }
  }

  private void saveArtifactsOutcomeSweepingOutput(Ambiance ambiance, ArtifactsOutcome artifactsOutcome) {
    ArtifactsOutcomeSweepingOutput artifactsOutcomeSweepingOutput =
        ArtifactsOutcomeSweepingOutput.builder().artifactsOutcome(artifactsOutcome).build();
    serviceStepSweepingOutputHelper.saveArtifactsOutcomeSweepingOutput(ambiance, artifactsOutcomeSweepingOutput);
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

  /**
   * Process artifacts.
   */
  private AsyncExecutableResponse processArtifacts(Ambiance ambiance, Map<String, ArtifactConfig> artifactConfigMap,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars) {
    List<String> callbackIds = new ArrayList<>();
    List<String> logKeys = new ArrayList<>();
    List<String> commandUnits = new ArrayList<>();

    // Resolve expressions in inputs values
    resolveInputsExpressions(ambiance, artifactConfigMap);

    // Validate artifact config map
    // TODO: (Tathagat) add validation framework later
    // validateArtifactConfigMap(artifactConfigMap);

    // RBAC check
    checkRuntimeAccessOrThrow(ambiance, artifactConfigMap);

    // Submit tasks for each artifact
    Map<String, String> taskIdToArtifactId = new HashMap<>();
    for (Map.Entry<String, ArtifactConfig> entry : artifactConfigMap.entrySet()) {
      String logKey = entry.getKey();
      ArtifactConfig artifactConfig = entry.getValue();
      if (ArtifactType.NO_OP_ACTION.equals(artifactConfig.getAction())) {
        continue;
      }
      String artifactId = artifactConfig.getId();

      TemplateYamlResult result = templateYamlGenerator.generateYamlWithMergedDefaults(ambiance,
          artifactConfig.getAction(), artifactConfig.getId(), artifactConfig.getInputs(),
          TemplateYamlEntityType.ARTIFACT, TemplateYamlSourceType.SERVICE);
      if (result == null) {
        throw new InvalidRequestException(String.format(
            "Could not fetch template to submit artifact task for artifact: [%s]", artifactConfig.getId()));
      }
      String templateYaml = result.getYaml();
      String callBackId = submitArtifactFetchTask(ambiance, artifactConfig, logKey, envVars, templateYaml);
      if (isNotEmpty(callBackId)) {
        callbackIds.add(callBackId);
        logKeys.add(logKey);
        commandUnits.add(artifactId);
        taskIdToArtifactId.put(callBackId, artifactId);
      }
    }

    // Persist the callback->artifact-id map so handleAsyncResponse can record per-artifact unit
    // statuses keyed by artifact id (not by callback id).
    serviceStepSweepingOutputHelper.saveArtifactTaskIdSweepingOutput(
        ambiance, ArtifactTaskIdSweepingOutput.builder().taskIdToArtifactId(taskIdToArtifactId).build());

    return AsyncExecutableResponse.newBuilder()
        .addAllLogKeys(logKeys)
        .addAllUnits(commandUnits)
        .addAllCallbackIds(callbackIds)
        .build();
  }

  /**
   * Resolve expressions in inputs values.
   */
  private void resolveInputsExpressions(Ambiance ambiance, Map<String, ArtifactConfig> artifactConfigMap) {
    for (ArtifactConfig artifactConfig : artifactConfigMap.values()) {
      Map<String, Object> inputsMap = artifactConfig.getInputs();
      if (isNotEmpty(inputsMap)) {
        cdStepsExpressionResolver.updateExpressions(ambiance, inputsMap, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
        if (ArtifactType.AMI.equals(artifactConfig.getUses())) {
          AmiArtifactStepHelper.normalizeInputs(inputsMap);
        }
      }
    }
  }

  /**
   * Build map structure from ArtifactConfig for RBAC reference extraction.
   */
  private Map<String, Object> artifactConfigToMapForRbac(ArtifactConfig artifactConfig) {
    Map<String, Object> map = new HashMap<>();
    map.put("inputs", artifactConfig.getInputs());
    if (artifactConfig.getWith() != null) {
      try {
        map.put("with", JsonUtils.asMap(JsonUtils.asJson(artifactConfig.getWith())));
      } catch (Exception e) {
        log.debug("Could not convert artifact with to map for RBAC extraction", e);
      }
    }
    return map;
  }

  /**
   * Extract references from artifact configs and check RBAC.
   */
  private void checkRuntimeAccessOrThrow(Ambiance ambiance, Map<String, ArtifactConfig> artifactConfigMap) {
    Set<EntityDetailProtoDTO> allEntityDetailsProto = new HashSet<>();

    for (ArtifactConfig artifactConfig : artifactConfigMap.values()) {
      Map<String, Object> artifactMap = artifactConfigToMapForRbac(artifactConfig);
      Set<EntityDetailProtoDTO> entityDetails =
          mapBasedReferenceExtractor.extractReferencesFromArtifactMap(artifactMap, ambiance);
      if (isNotEmpty(entityDetails)) {
        allEntityDetailsProto.addAll(entityDetails);
      }
    }

    List<EntityDetail> entityDetails =
        entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(emptyIfNull(allEntityDetailsProto)));
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }

  /**
   * Submit artifact fetch task from artifact config.
   */
  private String submitArtifactFetchTask(Ambiance ambiance, ArtifactConfig artifactConfig, String logKey,
      ParameterField<Map<String, ParameterField<JsonNode>>> envVars, String templateYaml) {
    String artifactId = artifactConfig.getId();
    templateYaml = (String) cdStepsExpressionResolver.updateExpressions(
        ambiance, templateYaml, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);

    StageInfraDetails stageInfraDetails = commonAbstractStepUtils.getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();

    if (stageInfraType == StageInfraDetails.Type.K8 || stageInfraType.name().equals("K8")) {
      // On K8 the step id keys the Initialize port/container-definition maps. Namespace it with the parent
      // node id (artifacts_<id>) so an artifact and a manifest sharing an id (e.g. both "primary") don't
      // collide in those maps. Applied only here (and to the build-time container def) - the log stream key
      // is passed separately as logKey (raw id), so command-unit/log rendering is unaffected.
      String stepId = ServiceStepUtility.getUniqueStepIdentifier(ARTIFACTS_NODE_ID, artifactId);
      return runnerSubmitTaskUtils.submitK8sTask(ambiance, stepId, envVars, templateYaml,
          (K8StageInfraDetails) stageInfraDetails, logKey, new HashMap<>(), new ArrayList<>());
    }

    // Non-K8 (VM) path derives the runner log stream key from this identifier (commandUnit), so it must stay
    // the raw id to match the command-unit/log keys reported to orchestration. VM has no port/container map.
    return runnerSubmitTaskUtils.submitTaskByTemplate(
        ambiance, artifactId, envVars, templateYaml, new ArrayList<>(), new HashMap<>());
  }
}
