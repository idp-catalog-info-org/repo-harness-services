/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.plan.execution.helper.ExecutionHelper.PMS_EXECUTION_SETTINGS_GROUP_IDENTIFIER;
import static io.harness.pms.utils.NGPipelineSettingsConstant.MAX_PIPELINE_TIMEOUT;
import static io.harness.pms.utils.NGPipelineSettingsConstant.MAX_STAGE_TIMEOUT;
import static io.harness.pms.utils.PmsConstants.DEFAULT_TIMEOUT;

import static java.util.Objects.nonNull;

import io.harness.agent.convert.V1ToV0StepGroupConverter;
import io.harness.agent.expansion.AgentTemplateExpansionService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.YamlException;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.plancreator.stages.OpaEvaluationStageHelper;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ErrorResponse;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.plan.PlanCreationBlobRequest;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanCreationResponse;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.exception.PmsExceptionUtils;
import io.harness.pms.plan.creation.PlanCreationBlobResponseUtils;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.plan.creation.info.PlanCreatorServiceInfo;
import io.harness.pms.plan.creation.validator.PlanCreationValidator;
import io.harness.pms.plan.preprocess.AIVerifyMatrixPreprocessor;
import io.harness.pms.plan.preprocess.AgentStepPreprocessor;
import io.harness.pms.plan.preprocess.AiEvalStepPreprocessor;
import io.harness.pms.plan.preprocess.PlanCreationYamlPreprocessor;
import io.harness.pms.plan.preprocess.PlanCreationYamlPreprocessorV0;
import io.harness.pms.plan.utils.PlanExecutionContextMapper;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponseWithModule;
import io.harness.pms.sdk.core.plan.creation.creators.PlanCreatorServiceHelper;
import io.harness.pms.sdk.helper.PmsSdkHelper;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.utils.PmsGrpcClientUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanCreatorMergeService {
  private static final int MAX_DEPTH = 10;

  private final Executor executor;

  private final PmsSdkHelper pmsSdkHelper;
  private final WaitNotifyEngine waitNotifyEngine;
  PmsEventSender pmsEventSender;
  PlanCreationValidator planCreationValidator;
  private final Integer planCreatorMergeServiceDependencyBatch;
  private final KryoSerializer kryoSerializer;
  private final NGSettingsClient ngSettingsClient;
  private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private ScopeResolutionHelper scopeResolutionHelper;
  @Inject private OpaEvaluationStageHelper opaEvaluationStageHelper;
  @Inject private Map<String, Executor> planCreatorExecutorServices;
  @Inject private AgentTemplateExpansionService agentTemplateExpansionService;
  @Inject private V1ToV0StepGroupConverter v1ToV0StepGroupConverter;

  @Inject
  public PlanCreatorMergeService(PmsSdkHelper pmsSdkHelper, PmsEventSender pmsEventSender,
      WaitNotifyEngine waitNotifyEngine, PlanCreationValidator planCreationValidator,
      @Named("PlanCreatorMergeExecutorService") Executor executor,
      @Named("planCreatorMergeServiceDependencyBatch") Integer planCreatorMergeServiceDependencyBatch,
      KryoSerializer kryoSerializer, NGSettingsClient ngSettingsClient, PmsFeatureFlagHelper pmsFeatureFlagHelper,
      ScopeResolutionHelper scopeResolutionHelper) {
    this.pmsSdkHelper = pmsSdkHelper;
    this.pmsEventSender = pmsEventSender;
    this.waitNotifyEngine = waitNotifyEngine;
    this.planCreationValidator = planCreationValidator;
    this.executor = executor;
    this.planCreatorMergeServiceDependencyBatch = planCreatorMergeServiceDependencyBatch;
    this.kryoSerializer = kryoSerializer;
    this.ngSettingsClient = ngSettingsClient;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
    this.scopeResolutionHelper = scopeResolutionHelper;
  }

  public PlanCreationBlobResponse createPipelinePlanVersion(String accountId, String orgIdentifier,
      String projectIdentifier, String version, ExecutionMetadata metadata,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) throws IOException {
    YamlField pipelineField = YamlUtils.extractPipelineField(planExecutionMetadataWithContext.getProcessedYaml());
    if (pipelineField.getNode().getUuid() == null) {
      throw new YamlException("Processed pipeline yaml does not have uuid for the pipeline field");
    }
    return createPlanVersioned(accountId, orgIdentifier, projectIdentifier, pipelineField, version, metadata,
        planExecutionMetadataWithContext, scopeInfo, isParentIdQueryingEnabled, Collections.emptyMap());
  }

  public PlanCreationBlobResponse createPlanVersioned(String accountId, String orgIdentifier, String projectIdentifier,
      YamlField rootField, String version, ExecutionMetadata metadata,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, Map<String, String> planCreationGlobalContextMap) throws IOException {
    try (AutoLogContext ignore =
             PlanCreatorUtils.autoLogContext(metadata, accountId, orgIdentifier, projectIdentifier, scopeInfo)) {
      log.info("[PMS_PlanCreatorMergeService] Starting plan creation");
      if (rootField.getNode().getUuid() == null) {
        throw new YamlException("Processed yaml does not have uuid for the root field");
      }

      // ========== OPA EVALUATION STAGE INJECTION ==========
      // Inject OPA stage using plan creation YAML preprocessor (V0 only)
      // Only inject if feature flag is enabled
      String processedYaml = planExecutionMetadataWithContext.getProcessedYaml();
      if (metadata != null && pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.OPA_RUN_ON_CUSTOMER_INFRA)
          && HarnessYamlVersion.V0.equals(version)) {
        PlanCreationYamlPreprocessor preprocessor = new PlanCreationYamlPreprocessorV0(opaEvaluationStageHelper);
        JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(processedYaml);
        JsonNode updatedJsonNode =
            preprocessor.preprocessPipelineYaml(pipelineJsonNode, accountId, orgIdentifier, projectIdentifier,
                metadata.getExecutionUuid(), metadata.getPipelineIdentifier(), metadata.getExecutionMode());

        if (updatedJsonNode != pipelineJsonNode) {
          String updatedProcessedYaml = JsonPipelineUtils.getJsonString(updatedJsonNode);
          log.info("OPA stage injected into processedYaml via plan creation preprocessor. Updating "
              + "planExecutionMetadataWithContext");
          planExecutionMetadataWithContext =
              planExecutionMetadataWithContext.toBuilder().processedYaml(updatedProcessedYaml).build();

          rootField = YamlUtils.extractPipelineField(updatedProcessedYaml);
          if (rootField.getNode().getUuid() == null) {
            throw new YamlException("Processed yaml does not have uuid for the root field after OPA injection");
          }
        }
      }
      // ========== END OPA EVALUATION STAGE INJECTION ==========

      // ========== AGENT STEP EXPANSION ==========
      if (metadata != null && HarnessYamlVersion.V0.equals(version)
          && pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.ML_ENABLE_AI_AGENTS)) {
        PlanCreationYamlPreprocessor agentPreprocessor =
            new AgentStepPreprocessor(agentTemplateExpansionService, v1ToV0StepGroupConverter);
        JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(planExecutionMetadataWithContext.getProcessedYaml());
        JsonNode updatedJsonNode =
            agentPreprocessor.preprocessPipelineYaml(pipelineJsonNode, accountId, orgIdentifier, projectIdentifier,
                metadata.getExecutionUuid(), metadata.getPipelineIdentifier(), metadata.getExecutionMode());

        if (updatedJsonNode != pipelineJsonNode) {
          String updatedProcessedYaml = JsonPipelineUtils.getJsonString(updatedJsonNode);
          log.info("Agent steps expanded into StepGroups in processedYaml");
          planExecutionMetadataWithContext =
              planExecutionMetadataWithContext.toBuilder().processedYaml(updatedProcessedYaml).build();

          rootField = YamlUtils.extractPipelineField(updatedProcessedYaml);
          if (rootField.getNode().getUuid() == null) {
            throw new YamlException("Processed yaml does not have uuid for the root field after Agent step expansion");
          }
        }
      }
      // ========== END AGENT STEP EXPANSION ==========

      // ========== AI EVAL STEP EXPANSION ==========
      if (metadata != null && HarnessYamlVersion.V0.equals(version)
          && pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.AI_ENABLE_EVAL_STEP)) {
        PlanCreationYamlPreprocessor aiEvalPreprocessor =
            new AiEvalStepPreprocessor(agentTemplateExpansionService, v1ToV0StepGroupConverter);
        JsonNode aiEvalPipelineJsonNode = YamlUtils.readAsJsonNode(planExecutionMetadataWithContext.getProcessedYaml());
        JsonNode aiEvalUpdatedJsonNode = aiEvalPreprocessor.preprocessPipelineYaml(aiEvalPipelineJsonNode, accountId,
            orgIdentifier, projectIdentifier, metadata.getExecutionUuid(), metadata.getPipelineIdentifier(),
            metadata.getExecutionMode());

        if (aiEvalUpdatedJsonNode != aiEvalPipelineJsonNode) {
          String updatedProcessedYaml = JsonPipelineUtils.getJsonString(aiEvalUpdatedJsonNode);
          log.info("AiEval steps expanded into StepGroups in processedYaml");
          planExecutionMetadataWithContext =
              planExecutionMetadataWithContext.toBuilder().processedYaml(updatedProcessedYaml).build();

          rootField = YamlUtils.extractPipelineField(updatedProcessedYaml);
          if (rootField.getNode().getUuid() == null) {
            throw new YamlException("Processed yaml does not have uuid for the root field after AiEval step expansion");
          }
        }
      }
      // ========== END AI EVAL STEP EXPANSION ==========

      // ========== AI VERIFY MATRIX INJECTION ==========
      // Cheap substring guard so pipelines without any AIVerifyNG step skip the YAML parse + tree walk entirely.
      String aiVerifyProcessedYaml = planExecutionMetadataWithContext.getProcessedYaml();
      if (metadata != null && HarnessYamlVersion.V0.equals(version) && aiVerifyProcessedYaml != null
          && aiVerifyProcessedYaml.contains(AIVerifyMatrixPreprocessor.AI_VERIFY_STEP_TYPE)
          && pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_CV_AI_VERIFY_NG)) {
        PlanCreationYamlPreprocessor aiVerifyMatrixPreprocessor = new AIVerifyMatrixPreprocessor();
        JsonNode aiVerifyPipelineJsonNode =
            YamlUtils.readAsJsonNode(planExecutionMetadataWithContext.getProcessedYaml());
        JsonNode aiVerifyUpdatedJsonNode = aiVerifyMatrixPreprocessor.preprocessPipelineYaml(aiVerifyPipelineJsonNode,
            accountId, orgIdentifier, projectIdentifier, metadata.getExecutionUuid(), metadata.getPipelineIdentifier(),
            metadata.getExecutionMode());

        if (aiVerifyUpdatedJsonNode != aiVerifyPipelineJsonNode) {
          String updatedProcessedYaml = JsonPipelineUtils.getJsonString(aiVerifyUpdatedJsonNode);
          log.info("AIVerifyNG strategy.matrix injected in processedYaml");
          planExecutionMetadataWithContext =
              planExecutionMetadataWithContext.toBuilder().processedYaml(updatedProcessedYaml).build();

          rootField = YamlUtils.extractPipelineField(updatedProcessedYaml);
          if (rootField.getNode().getUuid() == null) {
            throw new YamlException(
                "Processed yaml does not have uuid for the root field after AIVerifyNG matrix injection");
          }
        }
      }
      // ========== END AI VERIFY MATRIX INJECTION ==========

      HarnessStruct.Builder parentInfoBuilder = HarnessStruct.newBuilder().putData(
          PlanCreatorConstants.YAML_VERSION, HarnessValue.newBuilder().setStringValue(version).build());
      if (isNotEmpty(planCreationGlobalContextMap)) {
        planCreationGlobalContextMap.forEach(

            (k, v) -> {
              if (v != null) {
                parentInfoBuilder.putData(k, HarnessValue.newBuilder().setStringValue(v).build());
              }
            });
      }

      Map<String, PlanCreatorServiceInfo> services = pmsSdkHelper.getServicesV2();
      Dependencies dependencies = Dependencies.newBuilder()
                                      .setYaml(planExecutionMetadataWithContext.getProcessedYaml())
                                      .putDependencies(rootField.getNode().getUuid(), rootField.getNode().getYamlPath())
                                      .putDependencyMetadata(rootField.getNode().getUuid(),
                                          Dependency.newBuilder().setParentInfo(parentInfoBuilder.build()).build())
                                      .build();

      PlanCreationBlobResponse finalResponse;
      if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_CREATE_MERGE_PLAN_V2_OPTIMIZED_FLOW)) {
        finalResponse = createPlanForDependenciesRecursiveV2(accountId, orgIdentifier, projectIdentifier, services,
            dependencies, metadata, planExecutionMetadataWithContext, scopeInfo, isParentIdQueryingEnabled);
      } else {
        finalResponse = createPlanForDependenciesRecursive(accountId, orgIdentifier, projectIdentifier, services,
            dependencies, metadata, planExecutionMetadataWithContext, scopeInfo, isParentIdQueryingEnabled);
      }
      planCreationValidator.validate(accountId, finalResponse);
      PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataWithContext.getPlanExecutionMetadata();

      planExecutionMetadata.setExecutionInputConfigured(finalResponse.getNodesMap().values().stream().anyMatch(
          o -> !EmptyPredicate.isEmpty(o.getExecutionInputTemplate())));
      return finalResponse;
    }
  }

  @VisibleForTesting
  Map<String, PlanCreationContextValue> createInitialPlanCreationContext(String accountId, String orgIdentifier,
      String projectIdentifier, ExecutionMetadata metadata,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataWithContext.getPlanExecutionMetadata();
    Map<String, String> settingsMap = getTimeoutSettingsMap(accountId, orgIdentifier, projectIdentifier);
    String pipelineVersion = metadata != null && isNotEmpty(metadata.getHarnessVersion()) ? metadata.getHarnessVersion()
                                                                                          : HarnessYamlVersion.V0;
    Map<String, Boolean> featureFlagMap = getFeatureFlagMap(accountId, pipelineVersion);
    // TODO(BRIJESH): Remove the isExecutionInputEnabled field from PlanCreationContextValue. Once the change to remove
    // its usages is deployed in all services.
    Map<String, PlanCreationContextValue> planCreationContextMap = new HashMap<>();
    PlanCreationContextValue.Builder builder = PlanCreationContextValue.newBuilder()
                                                   .setAccountIdentifier(accountId)
                                                   .setOrgIdentifier(orgIdentifier)
                                                   .setProjectIdentifier(projectIdentifier)
                                                   .setIsExecutionInputEnabled(true);

    if (scopeInfo != null && isNotEmpty(scopeInfo.getUniqueId())) {
      builder.setParentUniqueId(scopeInfo.getUniqueId());
    } else {
      try {
        ScopeInfo resolvedScopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);
        builder.setParentUniqueId(resolvedScopeInfo.getUniqueId());
      } catch (Exception exception) {
        log.error("Failed to resolve scope info for account: {}, org: {}, project: {}", accountId, orgIdentifier,
            projectIdentifier, exception);
      }
    }
    if (metadata != null) {
      builder.setExecutionContext(PlanExecutionContextMapper.toExecutionContext(metadata, settingsMap, featureFlagMap));
    }
    if (planExecutionMetadata != null) {
      TriggerPayload triggerPayload =
          readTriggerPayloadWithFallbackOnMetadata(planExecutionMetadataWithContext, planExecutionMetadata, accountId);
      if (Objects.nonNull(triggerPayload)) {
        builder.setTriggerPayload(triggerPayload);
      }
    }
    planCreationContextMap.put("metadata", builder.build());
    return planCreationContextMap;
  }

  private TriggerPayload readTriggerPayloadWithFallbackOnMetadata(
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, PlanExecutionMetadata planExecutionMetadata,
      String accountId) {
    boolean readSwitchEnabled =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    if (readSwitchEnabled) {
      if (nonNull(planExecutionMetadataWithContext) && nonNull(planExecutionMetadataWithContext.getTriggerPayload())) {
        return planExecutionMetadataWithContext.getTriggerPayload();
      }
      if (nonNull(planExecutionMetadataWithContext.getTriggerPayload())) {
        log.warn("triggerPayload Disparity detected between previous planExecution and planExecutionMetadata : null vs "
                + "{} for planExecutionId : {}",
            PlanExecutionKeys.triggerPayload, planExecutionMetadata.getPlanExecutionId());
      }
    }
    return planExecutionMetadata.getTriggerPayload();
  }

  public Map<String, String> getTimeoutSettingsMap(String accountId, String orgIdentifier, String projectIdentifier) {
    Map<String, String> settingsMap = new HashMap<>();
    try {
      Call<ResponseDTO<List<SettingResponseDTO>>> responseDTOCall = ngSettingsClient.listSettings(
          accountId, orgIdentifier, projectIdentifier, SettingCategory.PMS, PMS_EXECUTION_SETTINGS_GROUP_IDENTIFIER);
      List<SettingResponseDTO> response = NGRestUtils.getResponse(responseDTOCall);

      for (SettingResponseDTO settingDto : response) {
        SettingDTO setting = settingDto.getSetting();
        settingsMap.put(setting.getIdentifier(), setting.getValue());
      }
    } catch (Exception exception) {
      settingsMap.put(MAX_STAGE_TIMEOUT.getName(), DEFAULT_TIMEOUT);
      settingsMap.put(MAX_PIPELINE_TIMEOUT.getName(), DEFAULT_TIMEOUT);
    }
    return settingsMap;
  }

  public Map<String, Boolean> getFeatureFlagMap(String accountId, String yamlVersion) {
    Map<String, Boolean> featureFlagMap = new HashMap<>();
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_DISABLE_MAX_TIMEOUT_CONFIG.toString())) {
      featureFlagMap.put(FeatureName.CDS_DISABLE_MAX_TIMEOUT_CONFIG.toString(), true);
    }
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_INIT_STEP_FAILURE_STRATEGY.toString())) {
      featureFlagMap.put(FeatureName.CDS_INIT_STEP_FAILURE_STRATEGY.toString(), true);
    }
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_ENABLE_FILE_UPLOAD_AS_RUNTIME_INPUT.toString())) {
      featureFlagMap.put(FeatureName.PIPE_ENABLE_FILE_UPLOAD_AS_RUNTIME_INPUT.toString(), true);
    }
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_NEW_YAML_NODE_COMPARE_METHOD.toString())) {
      featureFlagMap.put(FeatureName.PIPE_NEW_YAML_NODE_COMPARE_METHOD.toString(), true);
    }
    if (pmsFeatureFlagHelper.isEnabled(
            accountId, FeatureName.PIPE_THROW_ERROR_WHEN_NO_VALID_STAGE_IN_PIPELINE.toString())) {
      featureFlagMap.put(FeatureName.PIPE_THROW_ERROR_WHEN_NO_VALID_STAGE_IN_PIPELINE.toString(), true);
    }
    if (pmsFeatureFlagHelper.isEnabled(
            accountId, FeatureName.PIPE_IS_PRE_STEP_OPA_POLICY_EVALUATION_ENABLED.toString())) {
      featureFlagMap.put(FeatureName.PIPE_IS_PRE_STEP_OPA_POLICY_EVALUATION_ENABLED.toString(), true);
    }
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_ENABLE_STRATEGY_FOR_CHAINED_PIPELINES.toString())) {
      featureFlagMap.put(FeatureName.PIPE_ENABLE_STRATEGY_FOR_CHAINED_PIPELINES.toString(), true);
    }

    if (pmsFeatureFlagHelper.isEnabled(
            accountId, FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.toString())
        || HarnessYamlVersion.isV1(yamlVersion)) {
      featureFlagMap.put(FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION.toString(), true);
    }

    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT.toString())) {
      featureFlagMap.put(FeatureName.PIPE_ENABLE_SEND_STATUS_TO_GIT.toString(), true);
    }

    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED.toString())) {
      featureFlagMap.put(FeatureName.CDS_GITOPS_SEND_STATUS_TO_GIT_DISABLED.toString(), true);
    }

    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_ENABLE_MANUAL_STAGE_RUN.toString())) {
      featureFlagMap.put(FeatureName.PIPE_ENABLE_MANUAL_STAGE_RUN.toString(), true);
    }

    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_SKIP_MATRIX_LOOP_ON_ZERO_ITERATIONS.toString())) {
      featureFlagMap.put(FeatureName.PIPE_SKIP_MATRIX_LOOP_ON_ZERO_ITERATIONS.toString(), true);
    }
    return featureFlagMap;
  }

  PlanCreationBlobResponse createPlanForDependenciesRecursive(String accountId, String orgIdentifier,
      String projectIdentifier, Map<String, PlanCreatorServiceInfo> services, Dependencies initialDependencies,
      ExecutionMetadata metadata, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    PlanCreationBlobResponse.Builder finalResponseBuilder =
        PlanCreationBlobResponse.newBuilder().setDeps(initialDependencies);
    if (EmptyPredicate.isEmpty(services) || EmptyPredicate.isEmpty(initialDependencies.getDependenciesMap())) {
      return finalResponseBuilder.build();
    }

    finalResponseBuilder.putAllContext(createInitialPlanCreationContext(accountId, orgIdentifier, projectIdentifier,
        metadata, planExecutionMetadataWithContext, scopeInfo, isParentIdQueryingEnabled));

    try {
      for (int i = 0; i < MAX_DEPTH && isNotEmpty(finalResponseBuilder.getDeps().getDependenciesMap()); i++) {
        String version = metadata.getHarnessVersion();
        YamlField fullYamlField = YamlUtils.readTree(finalResponseBuilder.getDeps().getYaml());
        PlanCreationBlobResponse currIterationResponse =
            createPlanForDependencies(services, finalResponseBuilder, fullYamlField, version, accountId);
        PlanCreationBlobResponseUtils.addNodes(finalResponseBuilder, currIterationResponse.getNodesMap());
        PlanCreationBlobResponseUtils.mergeStartingNodeId(
            finalResponseBuilder, currIterationResponse.getStartingNodeId());
        PlanCreationBlobResponseUtils.mergeLayoutNodeInfo(finalResponseBuilder, currIterationResponse);
        PlanCreationBlobResponseUtils.mergePreservedNodesInRollbackMode(finalResponseBuilder, currIterationResponse);
        PlanCreationBlobResponseUtils.mergeServiceAffinityMap(finalResponseBuilder, currIterationResponse);
        if (isNotEmpty(finalResponseBuilder.getDeps().getDependenciesMap())) {
          throw new InvalidRequestException(
              PmsExceptionUtils.getUnresolvedDependencyPathsErrorMessage(finalResponseBuilder.getDeps()));
        }
        PlanCreationBlobResponseUtils.mergeContext(finalResponseBuilder, currIterationResponse.getContextMap());
        PlanCreationBlobResponseUtils.addDependenciesV2(finalResponseBuilder, currIterationResponse);
      }
    } catch (IOException e) {
      throw new UnexpectedException(e.getMessage(), e);
    }

    return finalResponseBuilder.build();
  }

  PlanCreationBlobResponse createPlanForDependenciesRecursiveV2(String accountId, String orgIdentifier,
      String projectIdentifier, Map<String, PlanCreatorServiceInfo> services, Dependencies initialDependencies,
      ExecutionMetadata metadata, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    PlanCreationBlobResponse.Builder finalResponseBuilder =
        PlanCreationBlobResponse.newBuilder().setDeps(initialDependencies);
    if (EmptyPredicate.isEmpty(services) || EmptyPredicate.isEmpty(initialDependencies.getDependenciesMap())) {
      return finalResponseBuilder.build();
    }

    finalResponseBuilder.putAllContext(createInitialPlanCreationContext(accountId, orgIdentifier, projectIdentifier,
        metadata, planExecutionMetadataWithContext, scopeInfo, isParentIdQueryingEnabled));

    try {
      YamlField fullYamlField = YamlUtils.readTree(finalResponseBuilder.getDeps().getYaml());
      Map<String, JsonNode> fqnJsonNodeMap = new HashMap<>();

      for (int i = 0; i < MAX_DEPTH && isNotEmpty(finalResponseBuilder.getDeps().getDependenciesMap()); i++) {
        String version = metadata.getHarnessVersion();
        PlanCreationBlobResponse currentIterationResponse = createPlanForDependenciesV2(
            services, finalResponseBuilder, fullYamlField, version, fqnJsonNodeMap, accountId);

        PlanCreationBlobResponseUtils.mergeV2(
            finalResponseBuilder, currentIterationResponse, fullYamlField, fqnJsonNodeMap);
        finalResponseBuilder.setDeps(
            finalResponseBuilder.getDeps().toBuilder().setYaml(YamlUtils.writeYamlString(fullYamlField)).build());
      }
    } catch (IOException e) {
      throw new UnexpectedException(e.getMessage(), e);
    }

    return finalResponseBuilder.build();
  }

  private PlanCreationBlobResponse createPlanForDependencies(Map<String, PlanCreatorServiceInfo> services,
      PlanCreationBlobResponse.Builder responseBuilder, YamlField fullYamlField, String harnessVersion,
      String accountId) {
    PlanCreationBlobResponse.Builder currIterationResponseBuilder = PlanCreationBlobResponse.newBuilder();
    CompletableFutures<PlanCreationResponseWithModule> completableFutures;
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_SEPARATE_PLAN_CREATION_EXECUTORS)) {
      completableFutures = new CompletableFutures<>(executor, planCreatorExecutorServices);
    } else {
      completableFutures = new CompletableFutures<>(executor);
    }

    PlanCreationContext ctx = PlanCreationContext.builder().globalContext(responseBuilder.getContextMap()).build();
    try (AutoLogContext ignore = PlanCreatorServiceHelper.autoLogContext(ctx)) {
      long start = System.currentTimeMillis();
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap =
          new HashMap<>();
      getServiceToDependenciesMap(
          services, responseBuilder, fullYamlField, serviceToDependencyMap, harnessVersion, accountId);

      // Sending batch dependency requests for a single service in a async fashion.
      executeCreatePlanInBatchDependency(responseBuilder, completableFutures, serviceToDependencyMap);

      // Collecting results for all completable futures at one go, thus it will wait till all dependencies are resolved.
      List<ErrorResponse> errorResponses;
      List<String> errorModules;
      try {
        List<PlanCreationResponseWithModule> planCreationResponses =
            completableFutures.allOf().get(PlanCreatorConstants.SDK_CREATOR_GRPC_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        errorResponses = planCreationResponses.stream()
                             .filter(resp
                                 -> resp.getPlanCreationResponse().getResponseCase()
                                     == PlanCreationResponse.ResponseCase.ERRORRESPONSE)
                             .map(resp -> resp.getPlanCreationResponse().getErrorResponse())
                             .collect(Collectors.toList());
        errorModules = planCreationResponses.stream()
                           .filter(resp
                               -> resp.getPlanCreationResponse().getResponseCase()
                                   == PlanCreationResponse.ResponseCase.ERRORRESPONSE)
                           .map(PlanCreationResponseWithModule::getModule)
                           .toList();
        if (EmptyPredicate.isEmpty(errorResponses)) {
          planCreationResponses.forEach(resp
              -> PlanCreationBlobResponseUtils.merge(
                  currIterationResponseBuilder, resp.getPlanCreationResponse().getBlobResponse()));
        }
      } catch (Exception ex) {
        throw new UnexpectedException("Error fetching plan creation response from service", ex);
      } finally {
        log.info("[PMS_PlanCreatorMergeService_Time] Sdk plan creators done took {}ms for initial dependencies size {}",
            System.currentTimeMillis() - start, responseBuilder.getDeps().getDependenciesMap().size());
      }
      PmsExceptionUtils.checkAndThrowPlanCreatorException(errorResponses, errorModules);
      return currIterationResponseBuilder.build();
    }
  }

  private PlanCreationBlobResponse createPlanForDependenciesV2(Map<String, PlanCreatorServiceInfo> services,
      PlanCreationBlobResponse.Builder responseBuilder, YamlField fullYamlField, String harnessVersion,
      Map<String, JsonNode> fqnJsonNodeMap, String accountId) {
    PlanCreationBlobResponse.Builder currIterationResponseBuilder = PlanCreationBlobResponse.newBuilder();
    CompletableFutures<PlanCreationResponseWithModule> completableFutures;
    if (pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_SEPARATE_PLAN_CREATION_EXECUTORS)) {
      completableFutures = new CompletableFutures<>(executor, planCreatorExecutorServices);
    } else {
      completableFutures = new CompletableFutures<>(executor);
    }

    PlanCreationContext ctx = PlanCreationContext.builder().globalContext(responseBuilder.getContextMap()).build();
    try (AutoLogContext ignore = PlanCreatorServiceHelper.autoLogContext(ctx)) {
      long start = System.currentTimeMillis();
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap =
          new HashMap<>();
      getServiceToDependenciesMap(
          services, responseBuilder, fullYamlField, serviceToDependencyMap, harnessVersion, accountId);

      // Sending batch dependency requests for a single service in a async fashion.
      executeCreatePlanInBatchDependency(responseBuilder, completableFutures, serviceToDependencyMap);

      // Collecting results for all completable futures at one go, thus it will wait till all dependencies are resolved.
      List<ErrorResponse> errorResponses;
      List<String> errorModules;
      try {
        List<PlanCreationResponseWithModule> planCreationResponses =
            completableFutures.allOf().get(PlanCreatorConstants.SDK_CREATOR_GRPC_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        errorResponses = planCreationResponses.stream()
                             .filter(resp
                                 -> resp.getPlanCreationResponse().getResponseCase()
                                     == PlanCreationResponse.ResponseCase.ERRORRESPONSE)
                             .map(resp -> resp.getPlanCreationResponse().getErrorResponse())
                             .collect(Collectors.toList());
        errorModules = planCreationResponses.stream()
                           .filter(resp
                               -> resp.getPlanCreationResponse().getResponseCase()
                                   == PlanCreationResponse.ResponseCase.ERRORRESPONSE)
                           .map(PlanCreationResponseWithModule::getModule)
                           .toList();
        if (EmptyPredicate.isEmpty(errorResponses)) {
          planCreationResponses.forEach(resp
              -> PlanCreationBlobResponseUtils.mergeV2(currIterationResponseBuilder,
                  resp.getPlanCreationResponse().getBlobResponse(), fullYamlField, fqnJsonNodeMap));
        }
      } catch (Exception ex) {
        throw new UnexpectedException("Error fetching plan creation response from service", ex);
      } finally {
        log.info(
            "[PMS_PlanCreatorMergeServiceV2_Time] Sdk plan creators done took {}ms for initial dependencies size {}",
            System.currentTimeMillis() - start, responseBuilder.getDeps().getDependenciesMap().size());
      }
      PmsExceptionUtils.checkAndThrowPlanCreatorException(errorResponses, errorModules);
      return currIterationResponseBuilder.build();
    }
  }

  // Sending all dependencies in batch manner in async fashion
  private void executeCreatePlanInBatchDependency(PlanCreationBlobResponse.Builder responseBuilder,
      CompletableFutures<PlanCreationResponseWithModule> completableFutures,
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap) {
    for (Map.Entry<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceDependencyEntry :
        serviceToDependencyMap.entrySet()) {
      Map.Entry<String, PlanCreatorServiceInfo> serviceInfo = serviceDependencyEntry.getKey();
      List<Map.Entry<String, String>> dependencyList = serviceDependencyEntry.getValue();
      Map<String, String> dependencyBatch = new HashMap<>();
      for (Map.Entry<String, String> dependency : dependencyList) {
        dependencyBatch.put(dependency.getKey(), dependency.getValue());
        if (dependencyBatch.size() >= planCreatorMergeServiceDependencyBatch) {
          Dependencies batchDependency = PmsSdkHelper.createBatchDependency(responseBuilder.getDeps(), dependencyBatch);
          Map<String, String> batchServiceAffinityMap = PmsSdkHelper.createBatchServiceAffinityMap(
              dependencyBatch.keySet(), responseBuilder.getServiceAffinityMap());
          executeDependenciesAsync(completableFutures, serviceInfo, batchDependency, batchServiceAffinityMap,
              responseBuilder.getContextMap());
          dependencyBatch = new HashMap<>();
        }
      }

      // call completable future for leftover batch
      if (dependencyBatch.size() > 0) {
        Dependencies batchDependency = PmsSdkHelper.createBatchDependency(responseBuilder.getDeps(), dependencyBatch);
        Map<String, String> batchServiceAffinityMap = PmsSdkHelper.createBatchServiceAffinityMap(
            dependencyBatch.keySet(), responseBuilder.getServiceAffinityMap());
        executeDependenciesAsync(
            completableFutures, serviceInfo, batchDependency, batchServiceAffinityMap, responseBuilder.getContextMap());
      }
    }
  }

  // Collecting which dependencies are supported with which service as a map.
  public void getServiceToDependenciesMap(Map<String, PlanCreatorServiceInfo> services,
      PlanCreationBlobResponse.Builder responseBuilder, YamlField fullYamlField,
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap,
      String harnessVersion, String accountId) {
    // Initializing the responseMap
    for (Map.Entry<String, PlanCreatorServiceInfo> serviceEntry : services.entrySet()) {
      serviceToDependencyMap.put(serviceEntry, new LinkedList<>());
    }

    addDependencyToServiceDependencyMapBasedOnPriority(
        services, responseBuilder, fullYamlField, serviceToDependencyMap, harnessVersion, accountId);
  }

  private void addDependencyToServiceDependencyMapBasedOnPriority(Map<String, PlanCreatorServiceInfo> services,
      PlanCreationBlobResponse.Builder responseBuilder, YamlField fullYamlField,
      Map<Map.Entry<String, PlanCreatorServiceInfo>, List<Map.Entry<String, String>>> serviceToDependencyMap,
      String harnessVersion, String accountId) {
    for (Map.Entry<String, String> dependencyEntry : responseBuilder.getDeps().getDependenciesMap().entrySet()) {
      harnessVersion = getYamlVersionForDependencyEntry(harnessVersion, dependencyEntry, responseBuilder);
      // Always first check  -
      // 1. Affinity service
      // 2. pipeline-service dependencies
      Map.Entry<String, PlanCreatorServiceInfo> pmsPlanCreatorService =
          services.entrySet()
              .stream()
              .filter(PmsSdkHelper::isPipelineService)
              .findFirst()
              .orElseThrow(
                  () -> new InvalidRequestException("Pipeline Service service provider information is missing."));

      String affinityService =
          PmsSdkHelper.getServiceAffinityForGivenDependency(responseBuilder.getServiceAffinityMap(), dependencyEntry);
      Map.Entry<String, PlanCreatorServiceInfo> affinityServicePlanCreatorService =
          services.entrySet()
              .stream()
              .filter(s -> PmsSdkHelper.getServiceForGivenAffinity(s, affinityService))
              .findFirst()
              .orElse(null);

      if (pmsSdkHelper.checkIfGivenServiceSupportsPath(
              affinityServicePlanCreatorService, dependencyEntry, fullYamlField, harnessVersion)) {
        serviceToDependencyMap.get(affinityServicePlanCreatorService).add(dependencyEntry);
      } else if (pmsSdkHelper.checkIfGivenServiceSupportsPath(
                     pmsPlanCreatorService, dependencyEntry, fullYamlField, harnessVersion, accountId)) {
        serviceToDependencyMap.get(pmsPlanCreatorService).add(dependencyEntry);
      } else {
        for (Map.Entry<String, PlanCreatorServiceInfo> serviceInfoEntry : services.entrySet()) {
          if (PmsSdkHelper.isPipelineService(serviceInfoEntry)) {
            continue;
          }
          if (pmsSdkHelper.checkIfGivenServiceSupportsPath(
                  serviceInfoEntry, dependencyEntry, fullYamlField, harnessVersion, accountId)) {
            serviceToDependencyMap.get(serviceInfoEntry).add(dependencyEntry);
            break;
          }
        }
      }
    }
  }

  // Sending batch dependency requests for a single service in a async fashion.
  private void executeDependenciesAsync(CompletableFutures<PlanCreationResponseWithModule> completableFutures,
      Map.Entry<String, PlanCreatorServiceInfo> serviceInfo, Dependencies batchDependency,
      Map<String, String> batchServiceAffinityMap, Map<String, PlanCreationContextValue> contextMap) {
    PlanCreationContext ctx = PlanCreationContext.builder().globalContext(contextMap).build();
    completableFutures.supplyAsyncExecutorsMap(serviceInfo.getKey(), () -> {
      try (AutoLogContext ignore = PlanCreatorServiceHelper.autoLogContext(ctx)) {
        try {
          return PlanCreationResponseWithModule.builder()
              .planCreationResponse(PmsGrpcClientUtils.retryAndProcessException(
                  serviceInfo.getValue().getPlanCreationClient()::createPlan,
                  PlanCreationBlobRequest.newBuilder()
                      .setDeps(batchDependency)
                      .putAllContext(contextMap)
                      .putAllServiceAffinity(batchServiceAffinityMap)
                      .build()))
              .module(serviceInfo.getKey())
              .build();
        } catch (StatusRuntimeException ex) {
          log.error(
              String.format("Error connecting with service: [%s]. Is this service Running?", serviceInfo.getKey()), ex);
          return PlanCreationResponseWithModule.builder()
              .planCreationResponse(
                  PlanCreationResponse.newBuilder()
                      .setErrorResponse(
                          ErrorResponse.newBuilder()
                              .addMessages(String.format("Error connecting with service: [%s]", serviceInfo.getKey()))
                              .build())
                      .build())
              .module(serviceInfo.getKey())
              .build();
        }
      }
    });
  }

  private String getYamlVersionForDependencyEntry(String harnessVersion, Map.Entry<String, String> dependencyEntry,
      PlanCreationBlobResponse.Builder responseBuilder) {
    if (responseBuilder.getDeps().getDependencyMetadataMap().get(dependencyEntry.getKey()) != null
        && responseBuilder.getDeps()
                .getDependencyMetadataMap()
                .get(dependencyEntry.getKey())
                .getParentInfo()
                .getDataMap()
                .get(PlanCreatorConstants.YAML_VERSION)
            != null) {
      harnessVersion = responseBuilder.getDeps()
                           .getDependencyMetadataMap()
                           .get(dependencyEntry.getKey())
                           .getParentInfo()
                           .getDataMap()
                           .get(PlanCreatorConstants.YAML_VERSION)
                           .getStringValue();
    }
    return harnessVersion;
  }
}
