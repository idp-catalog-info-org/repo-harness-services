
/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIE_DISABLE_NOTES_UPDATE_AFTER_EXECUTION_COMPLETED;
import static io.harness.beans.FeatureName.PIE_PROCESS_ADDITIONAL_BASE_KEYS;
import static io.harness.beans.FeatureName.PIPE_DISABLE_PLACEHOLDER_FILTERING_FOR_SCM_GIT_METADATA;
import static io.harness.beans.FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION;
import static io.harness.beans.FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS;
import static io.harness.beans.FeatureName.PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.ngsettings.SettingIdentifiers.RUN_RBAC_VALIDATION_BEFORE_EXECUTING_INLINE_PIPELINES;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.pms.contracts.plan.TriggerType.RELEASE_ORCHESTRATION;

import static java.util.Objects.nonNull;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.data.algorithm.HashGenerator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.blockExecutionMetadata.BlockExecutionMetadataService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.engine.utils.OpaPolicyEvaluationHelper;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.WingsException;
import io.harness.execution.ExecutionPlan;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanCreationRequest;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.Builder;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.execution.RetryStagesMetadata;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.DynamicTestSplittingYamlMerger;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.InputSetMergeHelperV1;
import io.harness.expression.RuntimeInputValuesValidatorV1;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngtriggers.expressions.TriggerExpressionEvaluator;
import io.harness.opaclient.model.OpaConstants;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.RerunInfo;
import io.harness.pms.contracts.plan.RetryExecutionInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.merger.fqn.helpers.FQNHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.ngpipeline.inputset.helpers.InputSetSanitizer;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.enforcement.PipelineEnforcementService;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.service.yamlConversion.PipelineYamlConversionEntityService;
import io.harness.pms.pipeline.validation.async.beans.BarrierCycleValidator;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.pipeline.yaml.UnifiedPipelineYaml;
import io.harness.pms.pipelinestage.v1.helper.PipelineStageHelperV1;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.plan.creation.lookup.intfc.NodeTypeLookupService;
import io.harness.pms.plan.execution.PmsExecutionSummaryDtoUpdateHelper;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.RollbackGraphGenerator;
import io.harness.pms.plan.execution.SetupAbstractionUtils;
import io.harness.pms.plan.execution.StagesExecutionHelper;
import io.harness.pms.plan.execution.StoreTypeMapper;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineMetadataInternalDTO;
import io.harness.pms.plan.execution.beans.ProcessStageExecutionInfoResult;
import io.harness.pms.plan.execution.beans.StagesExecutionInfo;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.plan.utils.ExecutionHelperUtils;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.validator.PipelineRbacService;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.preprocess.RuntimeInputIdValidatorV1;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.yaml.ref.TemplateRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import retrofit2.Call;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class ExecutionHelper {
  NGSettingsClient settingsClient;
  PMSPipelineService pmsPipelineService;
  PMSPipelineServiceHelper pmsPipelineServiceHelper;
  PipelineGovernanceService pipelineGovernanceService;
  TriggeredByHelper triggeredByHelper;
  PlanExecutionService planExecutionService;
  PrincipalInfoHelper principalInfoHelper;
  PmsGitSyncHelper pmsGitSyncHelper;
  PMSYamlSchemaService pmsYamlSchemaService;
  PipelineRbacService pipelineRbacServiceImpl;
  RetryExecutionHelper retryExecutionHelper;
  PlanExecutionMetadataService planExecutionMetadataService;
  PMSPipelineTemplateHelper pipelineTemplateHelper;
  PipelineEnforcementService pipelineEnforcementService;
  PmsFeatureFlagHelper featureFlagService;
  PMSExecutionService pmsExecutionService;
  AccessControlClient accessControlClient;
  PipelineStageHelper pipelineStageHelper;
  PipelineStageHelperV1 pipelineStageHelperV1;
  NodeExecutionService nodeExecutionService;
  RollbackGraphGenerator rollbackGraphGenerator;
  PlanCreationQueueRequestHelper planCreationQueueRequestHelper;
  NodeTypeLookupService nodeTypeLookupService;
  PmsExecutionSummaryService pmsExecutionSummaryService;
  BlockExecutionMetadataService blockExecutionMetadataService;
  RuntimeInputValuesValidatorV1 runtimeInputValuesValidatorV1;
  ConnectorInputsMapper connectorInputsMapper;
  OpaPolicyEvaluationHelper opaPolicyEvaluationHelper;
  PmsExecutionSummaryDtoUpdateHelper pmsExecutionSummaryDtoUpdateHelper;
  ScopeResolutionHelper scopeResolutionHelper;
  PipelineYamlConversionEntityService pipelineYamlConversionEntityService;
  BarrierCycleValidator barrierCycleValidator;

  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String CHILD_PIPELINE_EXECUTION_DETAILS = "childPipelineExecutionDetails";
  public static final String PMS_EXECUTION_SETTINGS_GROUP_IDENTIFIER = "pms_execution_settings";
  public static final String INPUTS = "inputs";

  public PipelineEntity fetchPipelineEntity(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String pipelineIdentifier, ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    Optional<PipelineEntity> pipelineEntityOptional = pmsPipelineService.getPipeline(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false, false, false, scopeInfo, true);
    if (!pipelineEntityOptional.isPresent()) {
      throw new InvalidRequestException(
          String.format("Pipeline with the given ID: %s does not exist or has been deleted", pipelineIdentifier));
    }
    return pipelineEntityOptional.get();
  }

  public ExecutionTriggerInfo buildTriggerInfo(String originalExecutionId) {
    TriggeredBy triggeredBy = triggeredByHelper.getFromSecurityContext();
    ExecutionTriggerInfo.Builder triggerInfoBuilder;
    if (TriggeredByHelper.RMG_SERVICE_IDENTIFIER.equals(triggeredBy.getIdentifier())
        || TriggeredByHelper.RMG_SERVICE.equals(
            triggeredBy.getExtraInfoOrDefault(TriggeredByHelper.SOURCE_SERVICE, ""))) {
      triggerInfoBuilder =
          ExecutionTriggerInfo.newBuilder().setTriggerType(RELEASE_ORCHESTRATION).setTriggeredBy(triggeredBy);
    } else {
      triggerInfoBuilder = ExecutionTriggerInfo.newBuilder().setTriggerType(MANUAL).setTriggeredBy(triggeredBy);
    }

    if (originalExecutionId == null) {
      return triggerInfoBuilder.setIsRerun(false).build();
    }

    ExecutionMetadata metadata = planExecutionService.getExecutionMetadataFromPlanExecution(originalExecutionId);
    ExecutionTriggerInfo originalTriggerInfo = metadata.getTriggerInfo();
    RerunInfo.Builder rerunInfoBuilder = RerunInfo.newBuilder()
                                             .setPrevExecutionId(originalExecutionId)
                                             .setPrevTriggerType(originalTriggerInfo.getTriggerType());
    if (originalTriggerInfo.getIsRerun()) {
      return triggerInfoBuilder.setIsRerun(true)
          .setRerunInfo(rerunInfoBuilder.setRootExecutionId(originalTriggerInfo.getRerunInfo().getRootExecutionId())
                            .setRootTriggerType(originalTriggerInfo.getRerunInfo().getRootTriggerType())
                            .build())
          .build();
    }

    return triggerInfoBuilder.setIsRerun(true)
        .setRerunInfo(rerunInfoBuilder.setRootExecutionId(originalExecutionId)
                          .setRootTriggerType(originalTriggerInfo.getTriggerType())
                          .build())
        .build();
  }

  public ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String moduleType, String mergedRuntimeInputYaml,
      List<String> stagesToRun, Map<String, String> expressionValues, ExecutionTriggerInfo triggerInfo,
      String originalExecutionId, RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser,
      boolean isDebug, PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    return buildExecutionArgs(pipelineEntity, moduleType, mergedRuntimeInputYaml, stagesToRun, expressionValues,
        triggerInfo, originalExecutionId, retryExecutionParameters, notifyOnlyUser, isDebug,
        planExecutionMetadataWithContext, false, null);
  }

  public ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String moduleType, String mergedRuntimeInputYaml,
      List<String> stagesToRun, Map<String, String> expressionValues, ExecutionTriggerInfo triggerInfo,
      String originalExecutionId, RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser,
      boolean isDebug, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      boolean isParentIdQueryingEnabled, ScopeInfo scopeInfo) {
    JsonNode mergedRuntimeInputJsonNode = null;
    if (isNotEmpty(mergedRuntimeInputYaml)) {
      mergedRuntimeInputJsonNode = YamlUtils.readAsJsonNode(mergedRuntimeInputYaml);
    }
    return buildExecutionArgs(pipelineEntity, moduleType, stagesToRun, expressionValues, triggerInfo,
        originalExecutionId, retryExecutionParameters, notifyOnlyUser, isDebug, null, mergedRuntimeInputJsonNode,
        planExecutionMetadataWithContext, isParentIdQueryingEnabled, scopeInfo);
  }

  @SneakyThrows
  public ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String moduleType, List<String> stagesToRun,
      Map<String, String> expressionValues, ExecutionTriggerInfo triggerInfo, String originalExecutionId,
      RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser, boolean isDebug, String notes,
      JsonNode mergedRuntimeInputJsonNode, PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    return buildExecutionArgs(pipelineEntity, moduleType, stagesToRun, expressionValues, triggerInfo,
        originalExecutionId, retryExecutionParameters, notifyOnlyUser, isDebug, notes, mergedRuntimeInputJsonNode, null,
        false, planExecutionMetadataWithContext, false);
  }

  @SneakyThrows
  public ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String moduleType, List<String> stagesToRun,
      Map<String, String> expressionValues, ExecutionTriggerInfo triggerInfo, String originalExecutionId,
      RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser, boolean isDebug, String notes,
      JsonNode mergedRuntimeInputJsonNode, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      boolean isParentIdQueryingEnabled, ScopeInfo scopeInfo) {
    return buildExecutionArgs(pipelineEntity, moduleType, stagesToRun, expressionValues, triggerInfo,
        originalExecutionId, retryExecutionParameters, notifyOnlyUser, isDebug, notes, mergedRuntimeInputJsonNode,
        scopeInfo, isParentIdQueryingEnabled, planExecutionMetadataWithContext, false);
  }

  @SneakyThrows
  public ExecArgs buildExecutionArgs(PipelineEntity pipelineEntity, String moduleType, List<String> stagesToRun,
      Map<String, String> expressionValues, ExecutionTriggerInfo triggerInfo, String originalExecutionId,
      RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser, boolean isDebug, String notes,
      JsonNode mergedRuntimeInputJsonNode, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, boolean shouldRunAsV1) {
    long start = System.currentTimeMillis();
    final String executionId = generateUuid();

    String orgId = isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : pipelineEntity.getOrgIdentifier();
    String projectId =
        isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : pipelineEntity.getProjectIdentifier();

    boolean processAdditionalBaseKeys =
        featureFlagService.isEnabled(pipelineEntity.getAccountId(), PIE_PROCESS_ADDITIONAL_BASE_KEYS);

    try (AutoLogContext ignore = PlanCreatorUtils.autoLogContext(
             pipelineEntity.getAccountId(), orgId, projectId, pipelineEntity.getIdentifier(), null, executionId)) {
      PipelineMetadataInternalDTO pipelineMetadataInternalDTO = getPipelineMetadataInternalDTO(pipelineEntity,
          mergedRuntimeInputJsonNode, processAdditionalBaseKeys, scopeInfo, isParentIdQueryingEnabled, shouldRunAsV1);

      // This will only be non-null in case of V0 for now.
      BasicPipeline basicPipeline = pipelineMetadataInternalDTO.getBasicPipeline();
      String pipelineYamlWithTemplateRef = pipelineMetadataInternalDTO.getPipelineYamlWithTemplateRef();
      boolean isNotificationConfigured = false;
      boolean allowedStageExecution = false;
      if (HarnessYamlVersion.V0.equals(pipelineMetadataInternalDTO.getProcessedYamlVersion())) {
        if (basicPipeline != null) {
          isNotificationConfigured = isNotEmpty(basicPipeline.getNotificationRules());
          allowedStageExecution = pipelineMetadataInternalDTO.getBasicPipeline().isAllowStageExecutions();
        }
      } else {
        UnifiedPipelineYaml unifiedPipeline =
            UnifiedPipelineExecutionUtils.getUnifiedPipeline(pipelineMetadataInternalDTO.getPipelineYaml());
        allowedStageExecution = unifiedPipeline.isAllowStageExecutions();
        isNotificationConfigured = isNotEmpty(unifiedPipeline.getNotificationRules());
      }

      // TODO(Shalini): Change these methods to use jsonNode instead of yaml in processing.
      // This method throws error if stagesToRun is empty when allowedStageExecution is true. So, this needs to be
      // done before validating yaml schema, else error propagation would be different.
      ProcessStageExecutionInfoResult processStageExecutionInfoResult = processStageExecutionInfo(stagesToRun,
          allowedStageExecution, pipelineEntity, pipelineMetadataInternalDTO.getPipelineYaml(),
          pipelineYamlWithTemplateRef, expressionValues, shouldRunAsV1);
      StagesExecutionInfo stagesExecutionInfo = processStageExecutionInfoResult.getStagesExecutionInfo();
      pipelineYamlWithTemplateRef = processStageExecutionInfoResult.getFilteredPipelineYamlWithTemplateRef();

      if (!HarnessYamlVersion.isV1(pipelineMetadataInternalDTO.getProcessedYamlVersion())) {
        validateYamlSchema(pipelineEntity, mergedRuntimeInputJsonNode, processAdditionalBaseKeys, scopeInfo,
            isParentIdQueryingEnabled, pipelineMetadataInternalDTO.getPipelineYaml());
      }
      PlanExecutionMetadata planExecutionMetadata;
      // RetryExecutionInfo
      RetryExecutionInfo retryExecutionInfo = buildRetryInfo(
          retryExecutionParameters.isRetry(), pipelineEntity.getAccountIdentifier(), originalExecutionId);

      if (!EmptyPredicate.isEmpty(mergedRuntimeInputJsonNode)) {
        planExecutionMetadata =
            buildPlanExecutionMetadata(pipelineEntity, YamlUtils.writeYamlString(mergedRuntimeInputJsonNode),
                originalExecutionId, retryExecutionParameters, notifyOnlyUser, notes, executionId, stagesExecutionInfo,
                retryExecutionInfo, pipelineMetadataInternalDTO.getReferredTemplateIds(), expressionValues,
                planExecutionMetadataWithContext, shouldRunAsV1);
      } else {
        planExecutionMetadata = buildPlanExecutionMetadata(pipelineEntity, null, originalExecutionId,
            retryExecutionParameters, notifyOnlyUser, notes, executionId, stagesExecutionInfo, retryExecutionInfo,
            pipelineMetadataInternalDTO.getReferredTemplateIds(), expressionValues, planExecutionMetadataWithContext,
            shouldRunAsV1);
      }

      String branch;
      if (!featureFlagService.isEnabled(
              pipelineEntity.getAccountId(), PIPE_DISABLE_PLACEHOLDER_FILTERING_FOR_SCM_GIT_METADATA)) {
        // If feature flag is OFF (default), use V2 method with improved placeholder filtering
        branch = GitAwareContextHelper.getBranchInRequestOrFromSCMGitMetadataV2();
      } else {
        branch = GitAwareContextHelper.getBranchInRequestOrFromSCMGitMetadata();
      }
      String expandedJson;
      if (!checkIfAsyncPlanCreation(
              pipelineEntity.getAccountId(), triggerInfo, planExecutionMetadataWithContext.getIsAsyncPlanCreation())) {
        // Moving this on the consumer side of queue since we make grpc calls here to other services which should be
        // async
        if (isParentIdQueryingEnabled) {
          expandedJson = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(pipelineEntity, scopeInfo,
              pipelineYamlWithTemplateRef, branch, OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);
        } else {
          expandedJson = pipelineGovernanceService.fetchExpandedPipelineJSONFromYaml(
              pipelineEntity, pipelineYamlWithTemplateRef, branch, OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN);
        }
        planExecutionMetadataWithContext.setExpandedPipelineJson(expandedJson);
        planExecutionMetadataWithContext.setPipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef);
      } else {
        planExecutionMetadataWithContext.setPipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef);
      }

      planExecutionMetadataWithContext.setPlanExecutionMetadata(planExecutionMetadata);
      planExecutionMetadataWithContext.setStagesExecutionMetadata(stagesExecutionInfo.toStagesExecutionMetadata());

      // TODO (Yagyansh): populate planExecutionMetadataWithContext in case executionid isn't empty after reading
      //  from corresponding previous record, similar to populateDataFromOriginalMetadata.

      ExecutionMetadata executionMetadata =
          buildExecutionMetadata(pipelineEntity.getIdentifier(), moduleType, triggerInfo, pipelineEntity, executionId,
              isNotificationConfigured, isDebug, pipelineMetadataInternalDTO.getProcessedYamlVersion(),
              EmptyPredicate.isNotEmpty(expressionValues), scopeInfo, isParentIdQueryingEnabled, shouldRunAsV1);
      return ExecArgs.builder()
          .metadata(executionMetadata)
          .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
          .build();
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      log.error(String.format("Failed to start execution for Pipeline with identifier [%s] in Project [%s] of Org "
                        + "[%s]. Error Message: %s",
                    pipelineEntity.getIdentifier(), projectId, orgId, e.getMessage()),
          e);
      String errorMessage = extractMeaningfulErrorMessage(e);
      throw new InvalidRequestException(String.format("Failed to start execution for Pipeline: %s", errorMessage), e);
    } finally {
      log.info("[PMS_EXECUTE] Pipeline build execution args took time {}ms", System.currentTimeMillis() - start);
    }
  }

  // Determine effective version based on shouldRunAsV1 flag
  private String getEffectiveVersion(String pipelineVersion, boolean shouldRunAsV1) {
    if (shouldRunAsV1 && HarnessYamlVersion.V0.equals(pipelineVersion)) {
      return HarnessYamlVersion.V1;
    }
    return pipelineVersion;
  }

  /**
   * Extracts and formats meaningful error message from exception chain for UI display.
   * Traverses the exception cause chain to find specific error messages and converts them to user-friendly format.
   */
  private String extractMeaningfulErrorMessage(Exception e) {
    if (e == null) {
      return "Unknown error occurred";
    }

    // Check the exception and its causes for meaningful messages
    Throwable current = e;
    while (current != null) {
      String message = current.getMessage();
      String exceptionClassName = current.getClass().getName();

      if (YamlUtils.isYamlSizeLimitExceeded(current)) {
        return PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE;
      }

      if (message != null && !message.isEmpty()) {
        // Check for Jackson YAML parse errors (non-size)
        if (exceptionClassName.contains("JacksonYAMLParseException") || exceptionClassName.contains("YAMLException")) {
          return String.format("Invalid YAML format: %s", message);
        }

        // Check for InvalidYamlException
        if (current instanceof InvalidYamlException) {
          return message;
        }
      }
      current = current.getCause();
    }

    // If no specific error found, return the original exception message
    String originalMessage = e.getMessage();
    return originalMessage != null && !originalMessage.isEmpty() ? originalMessage : "Unknown error occurred";
  }

  public PipelineMetadataInternalDTO getPipelineMetadataInternalDTO(PipelineEntity pipelineEntity,
      JsonNode mergedRuntimeInputJsonNode, boolean processAdditionalBaseKeys, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, boolean shouldRunAsV1) throws Exception {
    String pipelineYaml;
    String pipelineYamlWithTemplateRef;
    BasicPipeline basicPipeline = null;
    String orgIdentifier = scopeInfo != null ? scopeInfo.getOrgIdentifier() : pipelineEntity.getOrgIdentifier();
    String projectIdentifier =
        scopeInfo != null ? scopeInfo.getProjectIdentifier() : pipelineEntity.getProjectIdentifier();
    TemplateMergeResponseDTO templateMergeResponseDTO;
    switch (pipelineEntity.getHarnessVersion()) {
      case HarnessYamlVersion.V1:
        runtimeInputValuesValidatorV1.validate(mergedRuntimeInputJsonNode, pipelineEntity.getYaml(),
            pipelineEntity.getAccountId(), orgIdentifier, projectIdentifier, true);

        pipelineYaml = InputSetMergeHelperV1.mergeInputSetIntoEntityYaml(mergedRuntimeInputJsonNode,
            pipelineEntity.getYaml(), connectorInputsMapper, pipelineEntity.getAccountIdentifier(), orgIdentifier,
            projectIdentifier, YAMLFieldNameConstants.PIPELINE);

        // Validate that entities with runtime inputs (including template refs) have user-provided ids.
        // This runs on the YAML before auto-ID generation to distinguish user-provided vs generated ids.
        RuntimeInputIdValidatorV1.validateIdsForEntitiesWithRuntimeInputs(pipelineYaml);

        // Adds ids in all the stages and steps where it doesn't already exists
        // For templates, the ids will be added by template service during template resolution
        pipelineYaml = pmsPipelineServiceHelper.preProcessPipelineYaml(pipelineYaml, false);

        JsonNode pipelineEntityJsonNode = YamlUtils.readAsJsonNode(pipelineYaml);
        if (!EmptyPredicate.isEmpty(mergedRuntimeInputJsonNode)) {
          pipelineEntityJsonNode = PipelineV1InputMergeHelper.mergeV1UserProvidedInputs(
              pipelineEntityJsonNode, mergedRuntimeInputJsonNode, processAdditionalBaseKeys, pipelineYaml);
        }

        templateMergeResponseDTO = getPipelineYamlAndValidateStaticallyReferredEntities(
            pipelineEntityJsonNode, pipelineEntity, System.currentTimeMillis(), scopeInfo, isParentIdQueryingEnabled);

        // After template resolution, validate that ancestors of template references containing
        // runtime inputs also have user-provided ids in the original pipeline YAML.
        pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
        pipelineYamlWithTemplateRef = templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();
        pipelineEntityJsonNode = YamlUtils.readAsJsonNode(
            isNotEmpty(pipelineYamlWithTemplateRef) ? pipelineYamlWithTemplateRef : pipelineYaml);

        pipelineEntityJsonNode = InputSetMergeHelperV1.mergeCacheIntelligenceYamlToPipelineYaml(
            pipelineEntityJsonNode, YAMLFieldNameConstants.SAVE_CACHE_YAML, YAMLFieldNameConstants.RESTORE_CACHE_YAML);
        pipelineEntityJsonNode = InputSetMergeHelperV1.mergeBuildIntelligenceYamlToPipelineYaml(
            pipelineEntityJsonNode, YAMLFieldNameConstants.BUILD_INTELLIGENCE_YAML);

        // Inject collection stages for dynamic test splitting (CI feature)
        try {
          pipelineEntityJsonNode = DynamicTestSplittingYamlMerger.injectCollectionStages(pipelineEntityJsonNode);
        } catch (Exception ex) {
          log.warn("[DYNAMIC_TEST_SPLITTING] Failed to inject collection stages for dynamic test splitting. Continuing "
                  + "without injection.",
              ex);
          // Continue execution with original YAML if injection fails
        }

        templateMergeResponseDTO = getPipelineYamlAndValidateStaticallyReferredEntities(
            pipelineEntityJsonNode, pipelineEntity, System.currentTimeMillis(), scopeInfo, isParentIdQueryingEnabled);
        pipelineYamlWithTemplateRef = templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();
        pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();

        /*
        Todo (Tathagat): this is a temporary fix. We have to optimise pre process.
        This is done to handle step and stage defined as first class inputs in template, in case where user doesn't
        provide the inputs value on pipeline . In this case, similar to v0 inject feature, we have to ignore such steps
        in plan creation
         */
        pipelineYaml = pmsPipelineServiceHelper.preProcessPipelineYaml(pipelineYaml, true);
        pipelineYamlWithTemplateRef =
            pmsPipelineServiceHelper.preProcessPipelineYaml(pipelineYamlWithTemplateRef, true);
        break;
      case HarnessYamlVersion.V0:
        templateMergeResponseDTO = getPipelineYamlAndValidateStaticallyReferredEntities(mergedRuntimeInputJsonNode,
            pipelineEntity, processAdditionalBaseKeys, scopeInfo, isParentIdQueryingEnabled);
        pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
        pipelineYamlWithTemplateRef = templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();

        // Inject collection stages for dynamic test splitting (CI feature) - V0 support
        try {
          JsonNode pipelineEntityJsonNodeV0 = YamlUtils.readAsJsonNode(pipelineYaml);
          pipelineEntityJsonNodeV0 = DynamicTestSplittingYamlMerger.injectCollectionStages(pipelineEntityJsonNodeV0);

          // Serialize to YAML once after injection
          pipelineYaml = YamlUtils.writeYamlString(pipelineEntityJsonNodeV0);

          // CRITICAL FIX: Also update pipelineYamlWithTemplateRef to include injected stages
          // This ensures that if PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION FF is enabled,
          // the injected stages are preserved when pipelineYamlWithTemplateRef is used later
          if (isNotEmpty(pipelineYamlWithTemplateRef)) {
            JsonNode pipelineWithTemplateRefNode = YamlUtils.readAsJsonNode(pipelineYamlWithTemplateRef);
            pipelineWithTemplateRefNode =
                DynamicTestSplittingYamlMerger.injectCollectionStages(pipelineWithTemplateRefNode);
            pipelineYamlWithTemplateRef = YamlUtils.writeYamlString(pipelineWithTemplateRefNode);
            log.info("[DYNAMIC_TEST_SPLITTING] V0: Also injected collection stages into pipelineYamlWithTemplateRef");
          }
        } catch (Exception ex) {
          log.warn("[DYNAMIC_TEST_SPLITTING] Failed to inject collection stages for dynamic test splitting in V0 "
                  + "pipeline. Continuing without injection.",
              ex);
          // Continue execution with original YAML if injection fails
        }

        // Parse to BasicPipeline using the already serialized YAML
        basicPipeline = YamlUtils.read(pipelineYaml, BasicPipeline.class);
        break;
      default:
        throw new InvalidRequestException("version not supported");
    }

    // the template id's used in the pipeline.
    Set<String> referredTemplateIds = new HashSet<>();
    if (!CollectionUtils.isEmpty(templateMergeResponseDTO.getTemplateReferenceSummaries())) {
      referredTemplateIds = templateMergeResponseDTO.getTemplateReferenceSummaries()
                                .stream()
                                .map(TemplateReferenceSummary::getTemplateIdentifier)
                                .collect(Collectors.toSet());
    }

    pipelineYaml = pipelineYamlConversionEntityService.convertV0PipelineYamlToV1(pipelineEntity.getAccountIdentifier(),
        pipelineEntity.getOrgIdentifier(), pipelineEntity.getProjectIdentifier(), pipelineEntity.getIdentifier(),
        pipelineEntity.getHarnessVersion(), pipelineYaml, shouldRunAsV1);
    return PipelineMetadataInternalDTO.builder()
        .pipelineYaml(pipelineYaml)
        .basicPipeline(basicPipeline)
        .pipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef)
        .processedYamlVersion(getEffectiveVersion(pipelineEntity.getHarnessVersion(), shouldRunAsV1))
        .referredTemplateIds(referredTemplateIds)
        .build();
  }

  private PlanExecutionMetadata buildPlanExecutionMetadata(PipelineEntity pipelineEntity, String mergedRuntimeInputYaml,
      String originalExecutionId, RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser,
      String notes, String executionId, StagesExecutionInfo stagesExecutionInfo, RetryExecutionInfo retryExecutionInfo,
      Set<String> referredTemplateIds, Map<String, String> stagesExpressionValues,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, boolean shouldRunAsV1) throws Exception {
    Builder planExecutionMetadataBuilder = obtainPlanExecutionMetadata(mergedRuntimeInputYaml, executionId,
        stagesExecutionInfo, originalExecutionId, retryExecutionParameters, notifyOnlyUser, notes, pipelineEntity,
        planExecutionMetadataWithContext, shouldRunAsV1);
    // TODO - @utkarsh @brijesh - CDS-85458 - Add Enforcement Check for V1 Pipelines
    if (HarnessYamlVersion.V0.equals(getEffectiveVersion(pipelineEntity.getHarnessVersion(), shouldRunAsV1))) {
      if (stagesExecutionInfo.isStagesExecution()) {
        pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(pipelineEntity.getAccountId(),
            YamlUtils.extractPipelineField(planExecutionMetadataWithContext.getProcessedYaml()));
      } else {
        pipelineEnforcementService.validateExecutionEnforcementsBasedOnStage(
            pipelineEntity, planExecutionMetadataWithContext.getProcessedYaml());
      }
    }

    if (retryExecutionParameters.isRetry()) {
      planExecutionMetadataBuilder.retryStagesMetadata(
          RetryStagesMetadata.builder()
              .retryStagesIdentifier(retryExecutionParameters.getRetryStagesIdentifier())
              .skipStagesIdentifier(retryExecutionParameters.getIdentifierOfSkipStages())
              .build());
    }
    if (retryExecutionInfo != null) {
      planExecutionMetadataBuilder.retryExecutionInfo(retryExecutionInfo);
    }

    if (referredTemplateIds != null) {
      planExecutionMetadataBuilder.referredTemplateIds(new ArrayList<>(referredTemplateIds));
    }

    // Convert fqnExpressionToValue map into the nested key-value map and
    // save.{"stage.stage1.variables.key1":"val1"}->
    // {"stage":{"stage1":{"variables":{"key1":"val1"}}}}
    Map<String, Object> stagesExpressionObjectValues = new HashMap<>();
    if (stagesExpressionValues != null) {
      stagesExpressionObjectValues.putAll(stagesExpressionValues);
    }
    Map<String, Object> stageExpressionValueMap =
        FQNHelper.convertFQNExpressionToMap(stagesExpressionObjectValues, true);
    planExecutionMetadataBuilder.stageExpressionValuesMap(stageExpressionValueMap);
    planExecutionMetadataWithContext.setStageExpressionValuesMap(stageExpressionValueMap);

    return planExecutionMetadataBuilder.build();
  }

  public void validateYamlSchema(PipelineEntity pipelineEntity, JsonNode mergedRuntimeInputJsonNode,
      boolean processAdditionalBaseKeys, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled,
      String mergedPipelineYaml) {
    /*
    For schema validations, we don't want input set validators to be appended. For example, if some timeout field
    in the pipeline is <+input>.allowedValues(12h, 1d), and the runtime input gives a value 12h, the value for
    this field in pipelineYamlJsonNode will be 12h.allowedValues(12h, 1d) for validation during execution. However,
    this value will give an error in schema validation. That's why we need a value that doesn't have this
    validator appended.
     */
    String orgId = isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : pipelineEntity.getOrgIdentifier();
    String projectId =
        isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : pipelineEntity.getProjectIdentifier();

    JsonNode jsonNodeForValidatingSchema =
        getPipelineYamlWithUnResolvedTemplates(mergedRuntimeInputJsonNode, pipelineEntity, processAdditionalBaseKeys);
    pmsYamlSchemaService.validateYamlSchema(pipelineEntity.getAccountId(), orgId, projectId,
        jsonNodeForValidatingSchema, pipelineEntity.getHarnessVersion());

    String yamlToValidate = isNotEmpty(mergedPipelineYaml) ? mergedPipelineYaml : pipelineEntity.getYaml();
    barrierCycleValidator.validate(pipelineEntity.getAccountId(), yamlToValidate);
  }

  private ExecutionMetadata buildExecutionMetadata(@NotNull String pipelineIdentifier, String moduleType,
      ExecutionTriggerInfo triggerInfo, PipelineEntity pipelineEntity, String executionId,
      boolean isNotificationConfigured, boolean isDebug, String processedYamlVersion, boolean isStageExecution,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled, boolean shouldRunAsV1) {
    ExecutionMetadata.Builder builder =
        ExecutionMetadata.newBuilder()
            .setExecutionUuid(executionId)
            .setTriggerInfo(triggerInfo)
            .setModuleType(EmptyPredicate.isEmpty(moduleType) ? "" : moduleType)
            .setPipelineIdentifier(pipelineIdentifier)
            .setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext())
            .setIsNotificationConfigured(isNotificationConfigured)
            .setHarnessVersion(getEffectiveVersion(pipelineEntity.getHarnessVersion(), shouldRunAsV1))
            .setIsDebug(isDebug)
            .setIsStagesExpressionsProvided(isStageExecution)
            .setProcessedYamlVersion(processedYamlVersion)
            .setExecutionMode(ExecutionMode.NORMAL)
            .setEnableDAG(Boolean.TRUE.equals(pipelineEntity.getEnableDAG()))
            .setIsPipelineConverted((shouldRunAsV1 && HarnessYamlVersion.V0.equals(pipelineEntity.getHarnessVersion()))
                || isNotEmpty(pipelineEntity.getConvertedFromPipelineId()));
    ByteString gitSyncBranchContext = pmsGitSyncHelper.getGitSyncBranchContextBytesThreadLocal(
        pipelineEntity, pipelineEntity.getStoreType(), pipelineEntity.getRepo(), pipelineEntity.getConnectorRef());
    if (gitSyncBranchContext != null) {
      builder.setGitSyncBranchContext(gitSyncBranchContext);
    }
    PipelineStoreType pipelineStoreType = StoreTypeMapper.fromStoreType(pipelineEntity.getStoreType());
    if (pipelineStoreType != null) {
      builder.setPipelineStoreType(pipelineStoreType);
    }
    if (pipelineEntity.getConnectorRef() != null) {
      builder.setPipelineConnectorRef(pipelineEntity.getConnectorRef());
    }
    // adding metadata populated by Pipeline NG Settings
    updateSettingsInExecutionMetadataBuilder(pipelineEntity, builder, scopeInfo, isParentIdQueryingEnabled);
    updateFeatureFlagsInExecutionMetadataBuilder(
        pipelineEntity.getAccountIdentifier(), ExecutionHelperUtils.featureNames, builder);
    updateEvaluatePolicyBeforeStepRunFeatureFlagInExecutionMetadataBuilder(pipelineEntity.getAccountIdentifier(),
        scopeInfo != null ? scopeInfo.getOrgIdentifier() : pipelineEntity.getOrgIdentifier(),
        scopeInfo != null ? scopeInfo.getProjectIdentifier() : pipelineEntity.getProjectIdentifier(), builder,
        getEffectiveVersion(pipelineEntity.getHarnessVersion(), shouldRunAsV1));
    return builder.build();
  }

  public JsonNode getPipelineYamlWithUnResolvedTemplates(
      JsonNode mergedRuntimeInputJsonNode, PipelineEntity pipelineEntity, boolean processAdditionalBaseKeys) {
    JsonNode pipelineJsonNodeForSchemaValidations;
    String version = pipelineEntity.getHarnessVersion();
    if (EmptyPredicate.isEmpty(mergedRuntimeInputJsonNode)) {
      pipelineJsonNodeForSchemaValidations = YamlUtils.readAsJsonNode(pipelineEntity.getYaml());
    } else {
      /*
      For schema validations, we don't want input set validators to be appended. For example, if some timeout field
      in the pipeline is <+input>.allowedValues(12h, 1d), and the runtime input gives a value 12h, the value for
      this field in pipelineJsonNode will be 12h.allowedValues(12h, 1d) for validation during execution. However,
      this value will give an error in schema validation. That's why we need a value that doesn't have this
      validator appended.
       */
      pipelineJsonNodeForSchemaValidations =
          MergeHelper.mergeRuntimeInputValuesIntoOriginalJsonNode(YamlUtils.readAsJsonNode(pipelineEntity.getYaml()),
              Collections.singletonList(mergedRuntimeInputJsonNode), false, processAdditionalBaseKeys);
    }
    pipelineJsonNodeForSchemaValidations = InputSetSanitizer.trimValues(pipelineJsonNodeForSchemaValidations, version);
    return pipelineJsonNodeForSchemaValidations;
  }

  @VisibleForTesting
  TemplateMergeResponseDTO getPipelineYamlAndValidateStaticallyReferredEntities(JsonNode mergedRuntimeInputJsonNode,
      PipelineEntity pipelineEntity, boolean processAdditionalBaseKeys, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    JsonNode pipelineJsonNode;

    long start = System.currentTimeMillis();
    if (EmptyPredicate.isEmpty(mergedRuntimeInputJsonNode)) {
      pipelineJsonNode = YamlUtils.readAsJsonNode(pipelineEntity.getYaml());
    } else {
      JsonNode pipelineEntityJsonNode = YamlUtils.readAsJsonNode(pipelineEntity.getYaml());
      pipelineJsonNode = MergeHelper.mergeRuntimeInputValuesIntoOriginalJsonNode(
          pipelineEntityJsonNode, mergedRuntimeInputJsonNode, true, true, processAdditionalBaseKeys);
    }
    return getPipelineYamlAndValidateStaticallyReferredEntities(
        pipelineJsonNode, pipelineEntity, start, scopeInfo, isParentIdQueryingEnabled);
  }

  public boolean shouldRunRbacValidationBeforeExecutingInlinePipelines(PipelineEntity pipelineEntity) {
    String shouldRunRbacValidationBeforeExecutingInlinePipelines = "true";
    try {
      shouldRunRbacValidationBeforeExecutingInlinePipelines =
          NGRestUtils
              .getResponse(settingsClient.getSetting(
                  RUN_RBAC_VALIDATION_BEFORE_EXECUTING_INLINE_PIPELINES, pipelineEntity.getAccountId(), null, null))
              .getValue();
    } catch (Exception ex) {
      log.error("Failed to fetch setting value for {}", RUN_RBAC_VALIDATION_BEFORE_EXECUTING_INLINE_PIPELINES, ex);
    }
    return Boolean.TRUE.equals(Boolean.valueOf(shouldRunRbacValidationBeforeExecutingInlinePipelines));
  }

  TemplateMergeResponseDTO getPipelineYamlAndValidateStaticallyReferredEntities(
      JsonNode pipelineJsonNode, PipelineEntity pipelineEntity, long start) {
    return getPipelineYamlAndValidateStaticallyReferredEntities(pipelineJsonNode, pipelineEntity, start, null, false);
  }

  TemplateMergeResponseDTO getPipelineYamlAndValidateStaticallyReferredEntities(JsonNode pipelineJsonNode,
      PipelineEntity pipelineEntity, long start, ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String processedYamlVersion = pipelineEntity.getHarnessVersion();
    pipelineJsonNode = InputSetSanitizer.trimValues(pipelineJsonNode, processedYamlVersion);
    String pipelineYaml = YamlUtils.writeYamlString(pipelineJsonNode);
    log.info("[PMS_EXECUTE] Pipeline input set merge total time took {}ms", System.currentTimeMillis() - start);

    String pipelineYamlWithTemplateRef = pipelineYaml;
    List<TemplateReferenceSummary> templateReferenceSummaries = null;

    String orgId = isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : pipelineEntity.getOrgIdentifier();
    String projectId =
        isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : pipelineEntity.getProjectIdentifier();

    if (Boolean.TRUE.equals(TemplateRefHelper.hasTemplateRef(pipelineEntity.getHarnessVersion(), pipelineJsonNode))) {
      TemplateMergeResponseDTO templateMergeResponseDTO =
          pipelineTemplateHelper.resolveTemplateRefsInPipelineAndAppendInputSetValidatorsForExecution(
              pipelineEntity.getAccountId(), orgId, projectId, pipelineYaml, true,
              featureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.OPA_PIPELINE_GOVERNANCE)
                  || featureFlagService.isEnabled(
                      pipelineEntity.getAccountId(), PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION)
                  || HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion()),
              BOOLEAN_FALSE_VALUE, pipelineEntity.getHarnessVersion(), pipelineEntity.getRepo());
      pipelineYaml = templateMergeResponseDTO.getMergedPipelineYaml();
      pipelineYamlWithTemplateRef =
          EmptyPredicate.isEmpty(templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef())
          ? pipelineYaml
          : templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef();
      processedYamlVersion = templateMergeResponseDTO.getProcessedYamlVersion();
      templateReferenceSummaries = templateMergeResponseDTO.getTemplateReferenceSummaries();
    }
    if ((pipelineEntity.getStoreType() == null || pipelineEntity.getStoreType() == StoreType.INLINE)
        && shouldRunRbacValidationBeforeExecutingInlinePipelines(pipelineEntity)) {
      // For REMOTE Pipelines, entity setup usage framework cannot be relied upon. That is because the setup usages
      // can be outdated wrt the YAML we find on Git during execution. This means the fail fast approach that we
      // have for RBAC checks can't be provided for remote pipelines
      pipelineRbacServiceImpl.extractAndValidateStaticallyReferredEntities(pipelineEntity.getAccountId(), orgId,
          projectId, pipelineEntity.getIdentifier(), pipelineJsonNode, scopeInfo, true, processedYamlVersion);
    }
    return TemplateMergeResponseDTO.builder()
        .mergedPipelineYaml(pipelineYaml)
        .mergedPipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef)
        .processedYamlVersion(processedYamlVersion)
        .templateReferenceSummaries(templateReferenceSummaries)
        .build();
  }

  private PlanExecutionMetadata.Builder obtainPlanExecutionMetadata(String mergedRuntimeInputYaml, String executionId,
      StagesExecutionInfo stagesExecutionInfo, String originalExecutionId,
      RetryExecutionParameters retryExecutionParameters, boolean notifyOnlyUser, String notes,
      PipelineEntity pipelineEntity, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      boolean shouldRunAsV1) {
    long start = System.currentTimeMillis();
    String version = getEffectiveVersion(pipelineEntity.getHarnessVersion(), shouldRunAsV1);
    boolean isRetry = retryExecutionParameters.isRetry();
    String pipelineYaml = stagesExecutionInfo.getPipelineYamlToRun();
    long expressionFunctorToken = HashGenerator.generateIntegerHash();
    planExecutionMetadataWithContext.setExpressionFunctorToken(expressionFunctorToken);

    String updatedYaml = updateYamlForUnifiedTemplates(version, pipelineYaml);

    PlanExecutionMetadata.Builder planExecutionMetadataBuilder =
        PlanExecutionMetadata.builder()
            .accountIdentifier(pipelineEntity.getAccountIdentifier())
            .planExecutionId(executionId)
            .inputSetYaml(mergedRuntimeInputYaml)
            .yaml(updatedYaml)
            .unifiedYaml(pipelineYaml)
            .stagesExecutionMetadata(stagesExecutionInfo.toStagesExecutionMetadata())
            .allowStagesExecution(stagesExecutionInfo.isAllowStagesExecution())
            .notifyOnlyUser(notifyOnlyUser)
            .notes(notes)
            .harnessVersion(version)
            .expressionFunctorToken(expressionFunctorToken);
    if (HarnessYamlVersion.isV1(version)) {
      pipelineYaml = pmsPipelineServiceHelper.injectTypeField(pipelineYaml);
    }
    String currentProcessedYaml;
    try {
      currentProcessedYaml = YamlUtils.injectUuid(pipelineYaml);
    } catch (IOException e) {
      log.error("Unable to inject Uuids into pipeline Yaml. Yaml:\n" + pipelineYaml, e);
      throw new InvalidYamlException("Unable to inject Uuids into pipeline Yaml", e);
    }
    if (isRetry) {
      try {
        boolean isDagEnabled = Boolean.TRUE.equals(pipelineEntity.getEnableDAG());
        currentProcessedYaml = retryExecutionHelper.retryProcessedYaml(originalExecutionId,
            retryExecutionParameters.getPreviousProcessedYaml(), currentProcessedYaml,
            retryExecutionParameters.getRetryStagesIdentifier(), retryExecutionParameters.getIdentifierOfSkipStages(),
            version, pipelineEntity.getAccountIdentifier(), isDagEnabled);
      } catch (IOException e) {
        log.error("Unable to get processed yaml. Previous Processed yaml:\n"
                + retryExecutionParameters.getPreviousProcessedYaml(),
            e);
        throw new InvalidYamlException("Unable to get processed yaml for retry.", e);
      }
    }
    planExecutionMetadataBuilder.processedYaml(currentProcessedYaml);
    planExecutionMetadataWithContext.setProcessedYaml(currentProcessedYaml);

    if (isNotEmpty(originalExecutionId)) {
      planExecutionMetadataBuilder = populateDataFromOriginalMetadata(pipelineEntity.getAccountIdentifier(),
          originalExecutionId, planExecutionMetadataBuilder, isRetry, planExecutionMetadataWithContext);
    }
    log.info("[PMS_EXECUTE] PlanExecution Metadata creation took total time {}ms", System.currentTimeMillis() - start);
    return planExecutionMetadataBuilder;
  }

  private String updateYamlForUnifiedTemplates(String version, String pipelineYaml) {
    String updatedYaml = pipelineYaml;
    if (HarnessYamlVersion.isV1(version)) {
      updatedYaml = removeTemplateMetadataFromResolvedYaml(pipelineYaml);
    }
    return updatedYaml;
  }

  /**
   * Removes template metadata fields from the resolved V1 pipeline YAML.
   * After template resolution, the 'template' and 'parent-template-type' fields are no longer needed
   * as the template content has been merged into the pipeline.
   *
   * @param pipelineYaml The resolved pipeline YAML string
   * @return The YAML string with template metadata removed
   */
  private static String removeTemplateMetadataFromResolvedYaml(String pipelineYaml) {
    JsonNode pipelineYamlNode = YamlUtils.readAsJsonNode(pipelineYaml);
    YamlNode yamlNode = new YamlNode(pipelineYamlNode);

    // Remove all 'template' fields (contains uses, storeType, icon-name, description, etc.)
    removeAllFieldOccurrences(yamlNode, YAMLFieldNameConstants.TEMPLATE_METADATA);

    // Remove all 'parent-template-type' fields
    removeAllFieldOccurrences(yamlNode, YAMLFieldNameConstants.PARENT_TEMPLATE_TYPE);

    return YamlUtils.writeYamlString(pipelineYamlNode);
  }

  /**
   * Removes all occurrences of a field from the YAML tree.
   *
   * @param yamlNode  The root YAML node
   * @param fieldName The field name to remove
   */
  private static void removeAllFieldOccurrences(YamlNode yamlNode, String fieldName) {
    YamlNode fieldNode = YamlNodeUtils.findFirstNodeMatchingFieldNameV1(yamlNode, fieldName);
    while (fieldNode != null && fieldNode.getParentNode() != null) {
      JsonNode parentNode = fieldNode.getParentNode().getCurrJsonNode();
      if (parentNode instanceof ObjectNode) {
        ((ObjectNode) parentNode).remove(fieldName);
      }
      // Find next occurrence
      fieldNode = YamlNodeUtils.findFirstNodeMatchingFieldNameV1(yamlNode, fieldName);
    }
  }

  private PlanExecutionMetadata.Builder populateDataFromOriginalMetadata(String accountIdentifier,
      String originalExecutionId, Builder planExecutionMetadataBuilder, boolean isRetry,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    Optional<PlanExecutionMetadata> prevMetadataOptional =
        planExecutionMetadataService.findByPlanExecutionId(accountIdentifier, originalExecutionId);

    if (prevMetadataOptional.isPresent()) {
      PlanExecutionMetadata prevMetadata = prevMetadataOptional.get();
      boolean readSwitchEnabled = featureFlagService.isEnabled(
          prevMetadata.getAccountIdentifier(), FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
      PlanExecution planExecution = null;
      if (readSwitchEnabled) {
        Optional<PlanExecution> planExecutionOptional =
            planExecutionService.getWithFieldsIncludedOptional(originalExecutionId,
                Set.of(PlanExecutionKeys.expressionFunctorToken, PlanExecutionKeys.triggerJsonPayload,
                    PlanExecutionKeys.triggerPayload));
        if (planExecutionOptional.isPresent()) {
          planExecution = planExecutionOptional.get();
        }
      }
      String triggerJsonPayload =
          PlanExecutionMigrationHelper.readTriggerJsonPayloadWithFallBackOnMetadata(prevMetadata, planExecution);
      TriggerPayload triggerPayload =
          PlanExecutionMigrationHelper.readTriggerPayloadWithFallBackOnMetadata(prevMetadata, planExecution);
      planExecutionMetadataBuilder = populateTriggerDataForRerun(
          planExecutionMetadataBuilder, planExecutionMetadataWithContext, triggerJsonPayload, triggerPayload);
      if (isRetry) {
        Long expressionFunctorToken =
            PlanExecutionMigrationHelper.readExpressionFunctorTokenWithFallBackOnMetadata(prevMetadata, planExecution);
        planExecutionMetadataBuilder = populateExpressionTokenForRetry(
            planExecutionMetadataBuilder, planExecutionMetadataWithContext, expressionFunctorToken);
      }
      return planExecutionMetadataBuilder;
    } else {
      log.warn("No prev plan execution metadata found for plan execution id [" + originalExecutionId + "]");
    }
    return planExecutionMetadataBuilder;
  }

  private PlanExecutionMetadata.Builder populateTriggerDataForRerun(Builder planExecutionMetadataBuilder,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, String triggerJsonPayload,
      TriggerPayload triggerPayload) {
    // Update the context with the trigger data
    planExecutionMetadataWithContext.setTriggerJsonPayload(triggerJsonPayload);
    planExecutionMetadataWithContext.setTriggerPayload(triggerPayload);

    // Set the trigger data in the builder directly
    return planExecutionMetadataBuilder.triggerPayload(triggerPayload).triggerJsonPayload(triggerJsonPayload);
  }

  private PlanExecutionMetadata.Builder populateExpressionTokenForRetry(Builder planExecutionMetadataBuilder,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, Long expressionFunctorToken) {
    if (nonNull(expressionFunctorToken)) {
      planExecutionMetadataWithContext.setExpressionFunctorToken(expressionFunctorToken);
      planExecutionMetadataBuilder.expressionFunctorToken(expressionFunctorToken);
    }
    // else we have generated a new token for this field in current metadata
    return planExecutionMetadataBuilder;
  }

  public PlanExecution startExecution(String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMetadata executionMetadata, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      boolean isDebug, ScopeInfo scopeInfo) {
    return startExecution(accountId, orgIdentifier, projectIdentifier, executionMetadata,
        planExecutionMetadataWithContext, scopeInfo, true, isDebug);
  }

  // Overloaded method with isDebug defaulted to false
  public PlanExecution startExecution(String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMetadata executionMetadata, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      ScopeInfo scopeInfo) {
    return startExecution(accountId, orgIdentifier, projectIdentifier, executionMetadata,
        planExecutionMetadataWithContext, false, scopeInfo);
  }

  private boolean checkPipelineIfCondition(PlanExecutionMetadataWithContext planExecutionMetadataWithContext) {
    try {
      JsonNode pipelineJsonNode = YamlUtils.readAsJsonNode(planExecutionMetadataWithContext.getProcessedYaml())
                                      .get(YAMLFieldNameConstants.PIPELINE);
      if (pipelineJsonNode == null) {
        throw new InvalidRequestException("Pipeline node is missing from pipeline yaml.");
      }
      JsonNode pipelineIfJsonNode = pipelineJsonNode.get(YAMLFieldNameConstants.IF);
      if (pipelineIfJsonNode == null) {
        return true;
      }
      String pipelineIfCondition = pipelineIfJsonNode.asText();
      EngineExpressionEvaluator expressionEvaluator = new TriggerExpressionEvaluator(
          planExecutionMetadataWithContext.getTriggerPayload(), planExecutionMetadataWithContext.getTriggerHeader(),
          planExecutionMetadataWithContext.getTriggerJsonPayload(), null);
      Object evaluatedExpression = expressionEvaluator.evaluateExpression(pipelineIfCondition);
      if (evaluatedExpression == null) {
        return false;
      }
      return (Boolean) evaluatedExpression;
    } catch (Exception ex) {
      throw new InvalidRequestException("Error in parsing pipeline if condition", ex);
    }
  }

  public PlanExecution startExecution(String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMetadata executionMetadata, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled, boolean isDebug) {
    blockExecutionMetadataService.shouldAllowRun(
        accountId, orgIdentifier, projectIdentifier, executionMetadata.getPipelineIdentifier(), scopeInfo);
    String moduleType = executionMetadata.getModuleType();
    String operationId =
        EmptyPredicate.isEmpty(moduleType) ? "PIPELINE_EXECUTE" : moduleType.toUpperCase() + "_PIPELINE_EXECUTE";
    pmsPipelineServiceHelper.validateAndThrowFlexEnforcementRules(operationId, scopeInfo);
    String version = executionMetadata.getProcessedYamlVersion();
    if (HarnessYamlVersion.isV1(version) && !checkPipelineIfCondition(planExecutionMetadataWithContext)) {
      throw new InvalidRequestException(
          "Pipeline execution cannot proceed because pipeline if condition is evaluated to false");
    }
    if (checkIfAsyncPlanCreation(
            accountId, executionMetadata.getTriggerInfo(), planExecutionMetadataWithContext.getIsAsyncPlanCreation())) {
      return planCreationQueueRequestHelper.savePlanExecutionAndQueuePlanExecutionRequest(
          PlanCreationRequest.builder()
              .accountId(accountId)
              .orgIdentifier(orgIdentifier)
              .projectIdentifier(projectIdentifier)
              .executionMetadata(executionMetadata)
              .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
              .scopeInfo(scopeInfo)
              .isParentIdQueryingEnabled(isParentIdQueryingEnabled)
              .isDebug(isDebug)
              .build());
    }
    return planCreationQueueRequestHelper.executePlanCreationRequest(
        PlanCreationRequest.builder()
            .accountId(accountId)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .executionMetadata(executionMetadata)
            .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
            .scopeInfo(scopeInfo)
            .isParentIdQueryingEnabled(isParentIdQueryingEnabled)
            .isDebug(isDebug)
            .runSequenceIncrementNeeded(true)
            .branchSequenceIncrementNeeded(true)
            .build());
  }

  public ExecutionPlan startDryRun(String accountId, String orgIdentifier, String projectIdentifier,
      ExecutionMetadata executionMetadata, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled, boolean isDebug) {
    blockExecutionMetadataService.shouldAllowRun(
        accountId, orgIdentifier, projectIdentifier, executionMetadata.getPipelineIdentifier(), scopeInfo);
    String version = executionMetadata.getProcessedYamlVersion();
    if (HarnessYamlVersion.isV1(version) && !checkPipelineIfCondition(planExecutionMetadataWithContext)) {
      throw new InvalidRequestException(
          "Pipeline execution cannot proceed because pipeline if condition is evaluated to false");
    }
    return planCreationQueueRequestHelper.createPlanForDryRun(
        PlanCreationRequest.builder()
            .accountId(accountId)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .executionMetadata(executionMetadata)
            .planExecutionMetadataWithContext(planExecutionMetadataWithContext)
            .scopeInfo(scopeInfo)
            .isParentIdQueryingEnabled(isParentIdQueryingEnabled)
            .isDebug(isDebug)
            .runSequenceIncrementNeeded(true)
            .branchSequenceIncrementNeeded(true)
            .build());
  }

  protected boolean checkIfAsyncPlanCreation(
      String accountId, ExecutionTriggerInfo triggerInfo, boolean isPlanCreationAsync) {
    return OrchestrationUtils.checkAsyncPlanCreation(
        featureFlagService.isEnabled(accountId, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION),
        featureFlagService.isEnabled(accountId, PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS),
        triggerInfo, isPlanCreationAsync);
  }

  public RetryExecutionInfo buildRetryInfo(boolean isRetry, String accountIdentifier, String originalExecutionId) {
    if (!isRetry || isEmpty(originalExecutionId)) {
      return RetryExecutionInfo.newBuilder().setIsRetry(false).build();
    }
    String rootRetryExecutionId =
        pmsExecutionSummaryService.fetchRootRetryExecutionId(accountIdentifier, originalExecutionId);
    return RetryExecutionInfo.newBuilder()
        .setIsRetry(true)
        .setParentRetryId(originalExecutionId)
        .setRootExecutionId(rootRetryExecutionId)
        .build();
  }

  public PipelineExecutionDetailDTO getResponseDTO(String stageNodeId, String stageNodeExecutionId,
      String childStageNodeId, Boolean renderFullBottomGraph, PipelineExecutionSummaryEntity executionSummaryEntity,
      EntityGitDetails entityGitDetails, String childStageNodeExecutionId) {
    String accountId = executionSummaryEntity.getAccountId();
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, executionSummaryEntity.getParentUniqueId());
    boolean useScopeInfo = scopeInfo != null;

    String orgId = useScopeInfo ? scopeInfo.getOrgIdentifier() : executionSummaryEntity.getOrgIdentifier();
    String projectId = useScopeInfo ? scopeInfo.getProjectIdentifier() : executionSummaryEntity.getProjectIdentifier();
    String planExecutionId = executionSummaryEntity.getPlanExecutionId();

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()), PipelineRbacPermissions.PIPELINE_VIEW);

    ChildExecutionDetailDTO rollbackGraph = rollbackGraphGenerator.checkAndBuildRollbackGraph(accountId, orgId,
        projectId, executionSummaryEntity, entityGitDetails, childStageNodeId, stageNodeExecutionId, stageNodeId);
    boolean showRetryHistory = retryExecutionHelper.shouldShowRetryHistory(executionSummaryEntity);
    Boolean isLatestExecution = retryExecutionHelper.isLatestExecution(executionSummaryEntity);
    // If the stage is of type Pipeline Stage, then return the child graph along with top graph of parent pipeline
    if (pipelineStageHelper.validateChildGraphToGenerate(
            executionSummaryEntity.getLayoutNodeMap(), stageNodeId, stageNodeExecutionId)
        || pipelineStageHelperV1.validateChildGraphToGenerate(executionSummaryEntity.getLayoutNodeMap(), stageNodeId)) {
      // TODO: check with @sahilHindwani whether this update is required or not.
      pmsExecutionService.sendGraphUpdateEvent(executionSummaryEntity);
      ChildExecutionDetailDTO childGraph = getChildGraph(stageNodeId, entityGitDetails, stageNodeExecutionId,
          childStageNodeId, executionSummaryEntity, childStageNodeExecutionId);
      return PipelineExecutionDetailDTO.builder()
          .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails,
              showRetryHistory, isLatestExecution,
              pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(executionSummaryEntity), scopeInfo))
          .childGraph(childGraph)
          .rollbackGraph(rollbackGraph)
          .build();
    }

    // if the rollback graph has its executionGraph field filled, then we don't need to add execution graph to
    // parent response dto, because UI will only use the execution graph in the rollback graph
    boolean rollbackGraphWithExecutionGraph = rollbackGraph != null && rollbackGraph.getExecutionGraph() != null;
    if (rollbackGraphWithExecutionGraph
        || EmptyPredicate.isEmpty(stageNodeId) && (renderFullBottomGraph == null || !renderFullBottomGraph)) {
      pmsExecutionService.sendGraphUpdateEvent(executionSummaryEntity);
      return PipelineExecutionDetailDTO.builder()
          .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails,
              showRetryHistory, isLatestExecution,
              pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(executionSummaryEntity), scopeInfo))
          .rollbackGraph(rollbackGraph)
          .build();
    }

    return PipelineExecutionDetailDTO.builder()
        .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails,
            showRetryHistory, isLatestExecution,
            pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(executionSummaryEntity), scopeInfo))
        .executionGraph(ExecutionGraphMapper.toExecutionGraph(
            pmsExecutionService.getOrchestrationGraph(accountId, stageNodeId, planExecutionId, stageNodeExecutionId),
            executionSummaryEntity, scopeInfo))
        .rollbackGraph(rollbackGraph)
        .build();
  }

  public ExecutionGraph getExecutionGraph(PipelineExecutionSummaryEntity executionSummaryEntity) {
    String accountId = executionSummaryEntity.getAccountId();
    String planExecutionId = executionSummaryEntity.getPlanExecutionId();
    return ExecutionGraphMapper.toExecutionGraph(
        pmsExecutionService.getOrchestrationGraphForAllStages(accountId, planExecutionId));
  }

  private ChildExecutionDetailDTO getChildGraph(String stageNodeId, EntityGitDetails entityGitDetails,
      String stageNodeExecutionId, String childStageNodeId, PipelineExecutionSummaryEntity executionSummaryEntity,
      String childStageNodeExecutionId) {
    String accountId = executionSummaryEntity.getAccountId();
    ChildExecutionDetailDTO childGraph = null;
    if (executionSummaryEntity.getLayoutNodeMap().get(stageNodeId) != null
        && executionSummaryEntity.getLayoutNodeMap().get(stageNodeId).getStepDetails() != null) {
      PmsStepDetails childPipelineExecutionDetails = executionSummaryEntity.getLayoutNodeMap()
                                                         .get(stageNodeId)
                                                         .getStepDetails()
                                                         .get(CHILD_PIPELINE_EXECUTION_DETAILS);
      if (childPipelineExecutionDetails != null) {
        String childOrgID = (String) childPipelineExecutionDetails.get(ORG_ID);
        String childProjectID = (String) childPipelineExecutionDetails.get(PROJECT_ID);
        String childExecutionId = (String) childPipelineExecutionDetails.get(PLAN_EXECUTION_ID);
        childGraph = pipelineStageHelper.getChildGraph(accountId, childStageNodeId, entityGitDetails, childExecutionId,
            childOrgID, childProjectID, stageNodeExecutionId);
      }
    }
    if (childGraph == null && stageNodeExecutionId != null
        && executionSummaryEntity.getLayoutNodeMap().get(stageNodeExecutionId) != null
        && executionSummaryEntity.getLayoutNodeMap().get(stageNodeExecutionId).getStepDetails() != null
        && executionSummaryEntity.getLayoutNodeMap().get(stageNodeExecutionId).getStrategyMetadata() != null) {
      PmsStepDetails childPipelineExecutionDetails = executionSummaryEntity.getLayoutNodeMap()
                                                         .get(stageNodeExecutionId)
                                                         .getStepDetails()
                                                         .get(CHILD_PIPELINE_EXECUTION_DETAILS);
      if (childPipelineExecutionDetails != null) {
        String childOrgID = (String) childPipelineExecutionDetails.get(ORG_ID);
        String childProjectID = (String) childPipelineExecutionDetails.get(PROJECT_ID);
        String childExecutionId = (String) childPipelineExecutionDetails.get(PLAN_EXECUTION_ID);
        childGraph = pipelineStageHelper.getChildGraph(accountId, childStageNodeId, entityGitDetails, childExecutionId,
            childOrgID, childProjectID, childStageNodeExecutionId);
      }
    }
    if (childGraph == null) {
      NodeExecution nodeExecution = getNodeExecution(stageNodeId, executionSummaryEntity.getPlanExecutionId());
      if (nodeExecution != null && isNotEmpty(nodeExecution.getExecutableResponses())) {
        childGraph = pipelineStageHelper.getChildGraph(
            accountId, childStageNodeId, entityGitDetails, nodeExecution, stageNodeExecutionId);
      }
    }
    return childGraph;
  }

  private NodeExecution getNodeExecution(String stageNodeId, String planExecutionId) {
    try {
      return nodeExecutionService.getByPlanNodeUuid(stageNodeId, planExecutionId);
    } catch (InvalidRequestException ex) {
      log.info("NodeExecution is null for plan node: {} ", stageNodeId);
    }
    return null;
  }

  public ProcessStageExecutionInfoResult processStageExecutionInfo(List<String> stagesToRun,
      boolean allowedStageExecution, PipelineEntity pipelineEntity, String pipelineYaml,
      String pipelineYamlWithTemplateRef, Map<String, String> expressionValues, boolean shouldRunAsV1) {
    StagesExecutionInfo stagesExecutionInfo;
    if (pipelineYamlWithTemplateRef != null
        && (featureFlagService.isEnabled(
                pipelineEntity.getAccountId(), PIPE_STORE_TEMPLATE_REFERENCE_SUMMARY_PER_EXECUTION)
            || HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion()))) {
      pipelineYaml = pipelineYamlWithTemplateRef;
    }
    if (isNotEmpty(stagesToRun)) {
      if (!allowedStageExecution) {
        throw new InvalidRequestException(
            String.format("Stage executions are not allowed for pipeline [%s]", pipelineEntity.getIdentifier()));
      }

      String effectiveVersion = getEffectiveVersion(pipelineEntity.getHarnessVersion(), shouldRunAsV1);
      StagesExecutionHelper.throwErrorIfAllStagesAreDeleted(pipelineYaml, stagesToRun, effectiveVersion);
      stagesExecutionInfo =
          StagesExecutionHelper.getStagesExecutionInfo(pipelineYaml, stagesToRun, expressionValues, effectiveVersion);
      pipelineYamlWithTemplateRef =
          InputSetMergeHelper.removeNonRequiredStages(pipelineYamlWithTemplateRef, stagesToRun, effectiveVersion);
    } else {
      stagesExecutionInfo = StagesExecutionInfo.builder()
                                .isStagesExecution(false)
                                .pipelineYamlToRun(pipelineYaml)
                                .allowStagesExecution(allowedStageExecution)
                                .build();
    }
    return ProcessStageExecutionInfoResult.builder()
        .stagesExecutionInfo(stagesExecutionInfo)
        .filteredPipelineYamlWithTemplateRef(pipelineYamlWithTemplateRef)
        .build();
  }

  public void updateSettingsInExecutionMetadataBuilder(PipelineEntity pipelineEntity, ExecutionMetadata.Builder builder,
      ScopeInfo scopeInfo, boolean isParentIdQueryingEnabled) {
    String orgId = isParentIdQueryingEnabled ? scopeInfo.getOrgIdentifier() : pipelineEntity.getOrgIdentifier();
    String projectId =
        isParentIdQueryingEnabled ? scopeInfo.getProjectIdentifier() : pipelineEntity.getProjectIdentifier();

    try {
      Call<ResponseDTO<List<SettingResponseDTO>>> responseDTOCall =
          settingsClient.listSettings(pipelineEntity.getAccountIdentifier(), orgId, projectId, SettingCategory.PMS,
              PMS_EXECUTION_SETTINGS_GROUP_IDENTIFIER);

      List<SettingResponseDTO> response = NGRestUtils.getResponse(responseDTOCall);

      for (SettingResponseDTO settingDto : response) {
        SettingDTO setting = settingDto.getSetting();
        builder.putSettingToValueMap(setting.getIdentifier(), setting.getValue());
      }

      // TODO(Remove this specific setting call once the settings-list API returns all scope settings and not only
      // project)
      if (!builder.getSettingToValueMapMap().containsKey(
              NGPipelineSettingsConstant.ENABLE_NODE_EXECUTION_AUDIT_EVENTS.getName())) {
        Call<ResponseDTO<SettingValueResponseDTO>> auditSettingResponseDTO =
            settingsClient.getSetting(NGPipelineSettingsConstant.ENABLE_NODE_EXECUTION_AUDIT_EVENTS.getName(),
                pipelineEntity.getAccountIdentifier(), null, null);
        SettingValueResponseDTO auditSettingResponse = NGRestUtils.getResponse(auditSettingResponseDTO);
        builder.putSettingToValueMap(
            NGPipelineSettingsConstant.ENABLE_NODE_EXECUTION_AUDIT_EVENTS.getName(), auditSettingResponse.getValue());
      }
    } catch (Exception e) {
      log.error("Error in fetching pipeline Settings due to {}", e.getMessage());
    }
  }

  public void updateFeatureFlagsInExecutionMetadataBuilder(
      String accountIdentifier, List<FeatureName> featureNames, ExecutionMetadata.Builder builder) {
    for (FeatureName featureName : featureNames) {
      boolean isEnabled = featureFlagService.isEnabled(accountIdentifier, featureName);
      if (isEnabled) {
        builder.putFeatureFlagToValueMap(featureName.name(), isEnabled);
      }
    }
  }

  private void updateEvaluatePolicyBeforeStepRunFeatureFlagInExecutionMetadataBuilder(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, ExecutionMetadata.Builder builder, String yamlVersion) {
    if (featureFlagService.isEnabled(
            accountIdentifier, FeatureName.PIPE_IS_PRE_STEP_OPA_POLICY_EVALUATION_ENABLED.name())
        && opaPolicyEvaluationHelper.shouldEvaluatePolicy(accountIdentifier, orgIdentifier, projectIdentifier,
            OpaConstants.OPA_EVALUATION_TYPE_PIPELINE, OpaConstants.OPA_EVALUATION_ACTION_STEP_START, yamlVersion)) {
      builder.putFeatureFlagToValueMap(OpaConstants.PIPE_IS_ON_STEP_START_POLICY_PRESENT, true);
    }
  }

  public void checkForAccessOrThrowForGivenPlanExecutionId(
      String planExecutionId, String accountId, String resourceType, List<String> pipelineRbacPermissions) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(accountId, planExecutionId,
            Set.of(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.accountId,
                PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.orgIdentifier,
                PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.projectIdentifier,
                PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.pipelineIdentifier));
    List<PermissionCheckDTO> permissionChecks = new ArrayList<>();
    for (String rbacPermission : pipelineRbacPermissions) {
      if (rbacPermission.equals(PipelineRbacPermissions.PIPELINE_ABORT)) {
        if (featureFlagService.isEnabled(accountId, FeatureName.CDS_PIPELINE_ABORT_RBAC_PERMISSION)) {
          permissionChecks.add(
              PermissionCheckDTO.builder()
                  .permission(PipelineRbacPermissions.PIPELINE_ABORT)
                  .resourceScope(ResourceScope.of(accountId, pipelineExecutionSummaryEntity.getOrgIdentifier(),
                      pipelineExecutionSummaryEntity.getProjectIdentifier()))
                  .resourceIdentifier(pipelineExecutionSummaryEntity.getPipelineIdentifier())
                  .resourceType(resourceType)
                  .build());
        }
      } else {
        permissionChecks.add(
            PermissionCheckDTO.builder()
                .permission(rbacPermission)
                .resourceScope(ResourceScope.of(accountId, pipelineExecutionSummaryEntity.getOrgIdentifier(),
                    pipelineExecutionSummaryEntity.getProjectIdentifier()))
                .resourceIdentifier(pipelineExecutionSummaryEntity.getPipelineIdentifier())
                .resourceType(resourceType)
                .build());
      }
    }
    accessControlClient.checkForAccessOrThrow(permissionChecks, null);
  }

  public void checkForAccessOrThrowForGivenPlanExecutionIdForWorkflowGraph(
      String planExecutionId, String accountId, String resourceType, List<String> pipelineRbacPermissions) {
    PlanExecution planExecution = planExecutionService.getWithFieldsIncluded(
        planExecutionId, Set.of(PlanExecutionKeys.setupAbstractions, PlanExecutionKeys.metadata));

    List<PermissionCheckDTO> permissionChecks = new ArrayList<>();
    for (String rbacPermission : pipelineRbacPermissions) {
      if (rbacPermission.equals(PipelineRbacPermissions.PIPELINE_ABORT)) {
        if (featureFlagService.isEnabled(accountId, FeatureName.CDS_PIPELINE_ABORT_RBAC_PERMISSION)) {
          permissionChecks.add(
              PermissionCheckDTO.builder()
                  .resourceType(resourceType)
                  .permission(PipelineRbacPermissions.PIPELINE_ABORT)
                  .resourceScope(ResourceScope.of(accountId,
                      SetupAbstractionUtils.getOrgIdentifier(planExecution.getSetupAbstractions()),
                      SetupAbstractionUtils.getProjectIdentifier(planExecution.getSetupAbstractions())))
                  .resourceIdentifier(planExecution.getMetadata().getPipelineIdentifier())
                  .resourceType(resourceType)
                  .build());
        }
      } else {
        permissionChecks.add(PermissionCheckDTO.builder()
                                 .permission(rbacPermission)
                                 .resourceScope(ResourceScope.of(accountId,
                                     SetupAbstractionUtils.getOrgIdentifier(planExecution.getSetupAbstractions()),
                                     SetupAbstractionUtils.getProjectIdentifier(planExecution.getSetupAbstractions())))
                                 .resourceIdentifier(planExecution.getMetadata().getPipelineIdentifier())
                                 .resourceType(resourceType)
                                 .build());
      }
    }
    accessControlClient.checkForAccessOrThrow(permissionChecks, null);
  }

  public boolean shouldDisableNotesUpdate(String planExecutionId, String accountId) {
    if (!featureFlagService.isEnabled(accountId, PIE_DISABLE_NOTES_UPDATE_AFTER_EXECUTION_COMPLETED)) {
      return false;
    }
    return StatusUtils.isFinalStatus(planExecutionService.getStatus(planExecutionId));
  }
}
