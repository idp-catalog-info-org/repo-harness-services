/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_REVERT_GITX_CHILD_PIPELINE_CONTEXT_ISSUE_FIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.ORG_IDENTIFIER;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PARENT_UNIQUE_IDENTIFIER;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PIPELINE_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PLAN_EXECUTION_ID;
import static io.harness.pms.instrumentaion.constants.PipelineInstrumentationConstants.PROJECT_IDENTIFIER;
import static io.harness.pms.utils.NGPipelineSettingsConstant.ALLOW_DYNAMIC_EXECUTIONS_FOR_PIPELINES;
import static io.harness.pms.utils.NGPipelineSettingsConstant.ALLOW_ORIGINAL_YAML_ON_RERUN;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.constants.OrchestrationStepTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMigrationHelper;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.engine.executions.retry.RetryGroup;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.execution.ExecutionPlan;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.execution.PlanExecutionMetadataWithContext;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.InputSetMergeHelperV1;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.gitx.EntityGitDetailsGuard;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.opa.gitx.OpaEnforcementResult;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.data.NGWorkflowType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntityType;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.helpers.validate.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetFilterHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.opa.gitx.pipeline.PipelineOpaStatusHandler;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.mappers.dto.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.RollbackModeExecutionHelper;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.NGYamlHelper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.PipelineYamlUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineExecutor {
  private final String START_PIPELINE_EXECUTION_EVENT = "ng_start_pipeline_execution";
  private final String POST_PROD_ROLLBACK_PIPELINE_EXECUTION_EVENT = "ng_post_prod_rollback_pipeline_execution";
  private final String DIRECT_EXECUTION_SUFFIX = "_direct_execution_ca2d7c80_e559_404a_bb05_d300f26e27c1";
  ExecutionHelper executionHelper;
  ValidateAndMergeHelper validateAndMergeHelper;
  PlanExecutionMetadataService planExecutionMetadataService;
  RetryExecutionHelper retryExecutionHelper;
  PMSPipelineTemplateHelper pipelineTemplateHelper;
  PipelineTelemetryHelper pipelineTelemetryHelper;
  PlanExecutionService planExecutionService;
  RollbackModeExecutionHelper rollbackModeExecutionHelper;
  PMSExecutionService pmsExecutionService;
  NodeExecutionService nodeExecutionService;
  PmsFeatureFlagHelper pmsFeatureFlagHelper;
  PmsGitSyncHelper pmsGitSyncHelper;
  PMSInputSetService pmsInputSetService;
  PMSPipelineServiceHelper pmsPipelineServiceHelper;
  GitAwareEntityHelper gitAwareEntityHelper;
  ConnectorInputsMapper connectorInputsMapper;
  PrincipalInfoHelper principalInfoHelper;
  NGSettingsClient ngSettingsClient;
  PipelineOpaStatusHandler pipelineOpaStatusHandler;

  private static final String EXPLANATION_PIPELINE_EXECUTION_NOT_FOUND = "Unable to rerun execution.";
  private static final String HINT_PIPELINE_EXECUTION_NOT_FOUND =
      "Pipeline executions older than 30 days cannot be re-run.";

  public String getCompiledYamlForPipeline(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String inputSetPipelineYaml) {
    try {
      JsonNode runtimeInputJsonNode = null;
      if (isNotEmpty(inputSetPipelineYaml)) {
        runtimeInputJsonNode = YamlUtils.readAsJsonNode(inputSetPipelineYaml);
      }
      PipelineEntity pipelineEntity =
          executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null);
      return executionHelper
          .getPipelineMetadataInternalDTO(pipelineEntity, runtimeInputJsonNode, false, null, false, true)
          .getPipelineYaml();
    } catch (Exception ex) {
      return "Something went wrong, could not generate compiled yaml";
    }
  }

  public PlanExecutionResponseDto runPipelineWithInputSetPipelineYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, String runtimeInputYaml, boolean useV2, boolean notifyOnlyUser, String notes,
      ScopeInfo scopeInfo, List<String> inputSetIdentifiers, boolean asyncPlanCreation, boolean shouldRunAsV1) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null, moduleType,
        runtimeInputYaml, Collections.emptyList(), Collections.emptyMap(), useV2, notifyOnlyUser, notes, scopeInfo,
        false, inputSetIdentifiers, asyncPlanCreation, shouldRunAsV1);
  }

  public ExecutionPlan dryRunPipeline(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, String moduleType, String runtimeInputYaml, boolean notifyOnlyUser,
      String notes, ScopeInfo scopeInfo, List<String> inputSetIdentifiers, boolean asyncPlanCreation,
      boolean shouldRunAsV1, boolean isDebug, PipelineEntity pipelineEntity, boolean isParentIdQueryingEnabled) {
    JsonNode runtimeInputJsonNode = null;
    String originalExecutionId = null;
    List<String> stagesToRun = Collections.emptyList();
    Map<String, String> expressionValues = Collections.emptyMap();
    if (isNotEmpty(runtimeInputYaml)) {
      runtimeInputJsonNode = YamlUtils.readAsJsonNode(runtimeInputYaml);
    }
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().runAllStages(true).isAsyncPlanCreation(asyncPlanCreation).build();

    resolveAndAssignInputSetsToExecution(accountId, orgIdentifier, projectIdentifier, pipelineEntity.getIdentifier(),
        inputSetIdentifiers, planExecutionMetadataWithContext, scopeInfo, isParentIdQueryingEnabled);

    ExecArgs execArgs = getExecArgsWithJsonNode(originalExecutionId, moduleType, runtimeInputJsonNode, stagesToRun,
        expressionValues, notifyOnlyUser, pipelineEntity, isDebug, notes, scopeInfo, isParentIdQueryingEnabled,
        planExecutionMetadataWithContext, shouldRunAsV1);

    return executionHelper.startDryRun(accountId, orgIdentifier, projectIdentifier, execArgs.getMetadata(),
        execArgs.getPlanExecutionMetadataWithContext(), scopeInfo, isParentIdQueryingEnabled, isDebug);
  }

  public PlanExecutionResponseDto runPipelineWithInputSetReferencesList(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType,
      MergeInputSetRequestDTOPMS mergeInputSetRequestDTOPMS, String pipelineBranch, String pipelineRepoID, String notes,
      boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    JsonNode lastJsonNodeToMerge = null;
    if (isNotEmpty(mergeInputSetRequestDTOPMS.getLastYamlToMerge())) {
      lastJsonNodeToMerge = YamlUtils.readAsJsonNode(mergeInputSetRequestDTOPMS.getLastYamlToMerge());
    }
    boolean processAdditionalBaseKeys =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_PROCESS_ADDITIONAL_BASE_KEYS);
    JsonNode mergedRuntimeInputJsonNode =
        validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(scopeInfo,
            pipelineIdentifier, mergeInputSetRequestDTOPMS.getInputSetReferences(), pipelineBranch, pipelineRepoID,
            null, lastJsonNodeToMerge, false, false, processAdditionalBaseKeys, true, null);
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null, moduleType,
        mergedRuntimeInputJsonNode, Collections.emptyList(), Collections.emptyMap(), false, false, false, notes, false,
        mergeInputSetRequestDTOPMS.getInputSetReferences(), asyncPlanCreation, scopeInfo);
  }

  public PlanExecutionResponseDto runStagesWithRuntimeInputYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, RunStageRequestDTO runStageRequestDTO, boolean useV2, String notes,
      List<String> inputSetIdentifiers, boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null, moduleType,
        runStageRequestDTO.getRuntimeInputYaml(), runStageRequestDTO.getStageIdentifiers(),
        runStageRequestDTO.getExpressionValues(), useV2, false, notes, scopeInfo, false, inputSetIdentifiers,
        asyncPlanCreation, false);
  }

  public PlanExecutionResponseDto rerunStagesWithRuntimeInputYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, String originalExecutionId, RunStageRequestDTO runStageRequestDTO, boolean useV2,
      boolean isDebug, String notes, boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, runStageRequestDTO.getRuntimeInputYaml(), runStageRequestDTO.getStageIdentifiers(),
        runStageRequestDTO.getExpressionValues(), useV2, false, notes, scopeInfo, true,
        retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, originalExecutionId), asyncPlanCreation, false);
  }

  private JsonNode getRuntimeInputJsonNodeForRerun(String accountIdentifier, String originalExecutionId) {
    JsonNode runtimeInputJsonNodeForRerun = null;
    if (EmptyPredicate.isNotEmpty(originalExecutionId)) {
      PlanExecutionMetadata planExecutionMetadata =
          planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
              accountIdentifier, originalExecutionId, Set.of(PlanExecutionMetadataKeys.inputSetYaml));
      if (EmptyPredicate.isNotEmpty(planExecutionMetadata.getInputSetYaml())) {
        String originalRuntimeInputYaml = planExecutionMetadata.getInputSetYaml();
        runtimeInputJsonNodeForRerun = YamlUtils.readAsJsonNode(originalRuntimeInputYaml);
      }
    }
    return runtimeInputJsonNodeForRerun;
  }

  public PlanExecutionResponseDto rerunPipelineWithInputSetPipelineYaml(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, String originalExecutionId,
      String runtimeInputYaml, boolean useV2, boolean isDebug, String notes, boolean useOriginalPipelineYaml,
      boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    return rerunPipelineWithInputSetPipelineYaml(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        moduleType, originalExecutionId, runtimeInputYaml, useV2, isDebug, notes, useOriginalPipelineYaml,
        retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, originalExecutionId), asyncPlanCreation,
        scopeInfo);
  }

  public PlanExecutionResponseDto rerunPipelineWithInputSetPipelineYaml(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, String originalExecutionId,
      String runtimeInputYaml, boolean useV2, boolean isDebug, String notes, boolean useOriginalPipelineYaml,
      List<String> inputSetIdentifiers, boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    JsonNode runtimeInputJsonNode = null;
    if (isNotEmpty(runtimeInputYaml)) {
      runtimeInputJsonNode = YamlUtils.readAsJsonNode(runtimeInputYaml);
    }
    try {
      if (useOriginalPipelineYaml) {
        // Validate settings
        if (scopeInfo != null) {
          validateOriginalYamlRerunSettings(
              scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier());
        } else {
          validateOriginalYamlRerunSettings(accountId, orgIdentifier, projectIdentifier);
        }
        // Get pipeline entity with original YAML and prepare for rerun
        OriginalYamlRerunResult result = prepareForOriginalYamlRerun(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, originalExecutionId, false, null, asyncPlanCreation, scopeInfo);

        PipelineEntity pipelineEntity = result.getPipelineEntity();
        runtimeInputJsonNode = result.getOriginalInputs();
        PlanExecutionMetadataWithContext metadataWithContext = result.getMetadataWithContext();
        return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineEntity, metadataWithContext,
            originalExecutionId, moduleType, runtimeInputJsonNode, Collections.emptyList(), Collections.emptyMap(),
            useV2, false, isDebug, notes, scopeInfo, true, inputSetIdentifiers, true, false);
      }
      // Standard rerun if not using useOriginalPipelineYaml
      return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
          moduleType, runtimeInputJsonNode, Collections.emptyList(), Collections.emptyMap(), useV2, false, isDebug,
          notes, scopeInfo, true, inputSetIdentifiers, asyncPlanCreation, false);
    } catch (EntityNotFoundException ex) {
      throw NestedExceptionUtils.hintWithExplanationException(HINT_PIPELINE_EXECUTION_NOT_FOUND,
          EXPLANATION_PIPELINE_EXECUTION_NOT_FOUND,
          new EntityNotFoundException("Execution details not found for id: " + originalExecutionId));
    }
  }

  public PlanExecutionResponseDto rerunPipelineWithInputSetReferencesList(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, String originalExecutionId,
      MergeInputSetRequestDTOPMS mergeInputSetRequestDTOPMS, String pipelineBranch, String pipelineRepoID,
      boolean isDebug, String notes, ScopeInfo scopeInfo) {
    JsonNode lastJsonNodeToMerge = null;
    boolean processAdditionalBaseKeys =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_PROCESS_ADDITIONAL_BASE_KEYS);
    if (isNotEmpty(mergeInputSetRequestDTOPMS.getLastYamlToMerge())) {
      lastJsonNodeToMerge = YamlUtils.readAsJsonNode(mergeInputSetRequestDTOPMS.getLastYamlToMerge());
    }
    JsonNode mergedRuntimeInputJsonNode =
        validateAndMergeHelper.getMergedJsonNodeFromInputSetReferencesAndRuntimeInputJsonNode(scopeInfo,
            pipelineIdentifier, mergeInputSetRequestDTOPMS.getInputSetReferences(), pipelineBranch, pipelineRepoID,
            null, lastJsonNodeToMerge, false, false, processAdditionalBaseKeys, true, null);
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, mergedRuntimeInputJsonNode, Collections.emptyList(), Collections.emptyMap(), false, false, false,
        notes, true, mergeInputSetRequestDTOPMS.getInputSetReferences(), false, scopeInfo);
  }

  private PlanExecutionResponseDto startPlanExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String originalExecutionId, String moduleType, String runtimeInputYaml,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean useV2, boolean notifyOnlyUser,
      String notes, ScopeInfo scopeInfo, boolean isRerun, List<String> inputSetIdentifiers, boolean asyncPlanCreation,
      boolean shouldRunAsV1) {
    JsonNode runtimeInputJsonNode = null;
    if (isNotEmpty(runtimeInputYaml)) {
      runtimeInputJsonNode = YamlUtils.readAsJsonNode(runtimeInputYaml);
    }
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, runtimeInputJsonNode, stagesToRun, expressionValues, useV2, notifyOnlyUser, false, notes, scopeInfo,
        isRerun, inputSetIdentifiers, asyncPlanCreation, shouldRunAsV1);
  }

  private PlanExecutionResponseDto startPlanExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String originalExecutionId, String moduleType, JsonNode runtimeInputJsonNode,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean useV2, boolean notifyOnlyUser,
      boolean isDebug, String notes, ScopeInfo scopeInfo, boolean isRerun, List<String> inputSetIdentifiers,
      boolean asyncPlanCreation, boolean shouldRunAsV1) {
    PipelineEntity pipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo);
    if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
      throw new InvalidRequestException(String.format(
          "Cannot execute a Draft Pipeline with PipelineID: %s, ProjectID %s", pipelineIdentifier, projectIdentifier));
    }
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().runAllStages(true).isAsyncPlanCreation(asyncPlanCreation).build();
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineEntity,
        planExecutionMetadataWithContext, originalExecutionId, moduleType, runtimeInputJsonNode, stagesToRun,
        expressionValues, useV2, notifyOnlyUser, isDebug, notes, scopeInfo, isRerun, inputSetIdentifiers, true,
        shouldRunAsV1);
  }

  private PlanExecutionResponseDto startPlanExecution(String accountId, String orgIdentifier, String projectIdentifier,
      PipelineEntity pipelineEntity, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      String originalExecutionId, String moduleType, JsonNode runtimeInputJsonNode, List<String> stagesToRun,
      Map<String, String> expressionValues, boolean useV2, boolean notifyOnlyUser, boolean isDebug, String notes,
      ScopeInfo scopeInfo, boolean isRerun, List<String> inputSetIdentifiers, boolean isParentIdQueryingEnabled,
      boolean shouldRunAsV1) {
    sendExecutionStartTelemetryEvent(accountId, orgIdentifier, projectIdentifier, pipelineEntity.getIdentifier());
    resolveAndAssignInputSetsToExecution(accountId, orgIdentifier, projectIdentifier, pipelineEntity.getIdentifier(),
        inputSetIdentifiers, planExecutionMetadataWithContext, scopeInfo, true);

    if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
      throw new InvalidRequestException(
          String.format("Cannot execute a Draft Pipeline with PipelineID: %s, ProjectID %s",
              pipelineEntity.getIdentifier(), projectIdentifier));
    }
    if (isRerun) {
      boolean isV1 = HarnessYamlVersion.isV1(pipelineEntity.getHarnessVersion());
      String resolvedPipelineYaml =
          pipelineTemplateHelper.resolveOnlyPipelineTemplateRefAndMerge(accountId, orgIdentifier, projectIdentifier,
              pipelineEntity.getYaml(), pipelineEntity.getStoreType(), "false", pipelineEntity.getHarnessVersion());
      boolean isFixedInputsOnRerun = isV1 ? UnifiedPipelineExecutionUtils.isFixedInputsOnRerun(resolvedPipelineYaml)
                                          : PipelineYamlUtils.isFixedInputsOnRerun(resolvedPipelineYaml);
      if (isFixedInputsOnRerun) {
        runtimeInputJsonNode = getRuntimeInputJsonNodeForRerun(accountId, originalExecutionId);
      }
    }

    ExecArgs execArgs = getExecArgsWithJsonNode(originalExecutionId, moduleType, runtimeInputJsonNode, stagesToRun,
        expressionValues, notifyOnlyUser, pipelineEntity, isDebug, notes, scopeInfo, isParentIdQueryingEnabled,
        planExecutionMetadataWithContext, shouldRunAsV1);
    return getPlanExecutionResponseDto(accountId, orgIdentifier, projectIdentifier, useV2, pipelineEntity, execArgs,
        scopeInfo, isParentIdQueryingEnabled, isDebug);
  }

  public PlanExecutionResponseDto startPlanExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String originalExecutionId, String moduleType, JsonNode runtimeInputJsonNode,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean useV2, boolean notifyOnlyUser,
      boolean isDebug, String notes, boolean isRerun, List<String> inputSetIdentifiers, boolean asyncPlanCreation,
      ScopeInfo scopeInfo) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, runtimeInputJsonNode, stagesToRun, expressionValues, useV2, notifyOnlyUser, isDebug, notes,
        scopeInfo, isRerun, inputSetIdentifiers, asyncPlanCreation, false);
  }

  public PlanExecutionResponseDto startDynamicExecution(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String pipelineYaml, String moduleType, boolean useV2,
      boolean notifyOnlyUser, String notes, ScopeInfo scopeInfo, boolean isInternalApi) {
    PipelineEntity pipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo);
    if (!isInternalApi) {
      boolean isAccountLevelSettingDisabled = "false".equals(
          NGRestUtils
              .getResponse(
                  ngSettingsClient.getSetting(ALLOW_DYNAMIC_EXECUTIONS_FOR_PIPELINES.getName(), accountId, null, null))
              .getValue());
      boolean isPipelineLevelConfigDisabled =
          pipelineEntity.getAllowDynamicExecutions() == null || !pipelineEntity.getAllowDynamicExecutions();
      if (isAccountLevelSettingDisabled && isPipelineLevelConfigDisabled) {
        throw new InvalidRequestException(
            "Dynamic execution is disabled. Enable it at the account and pipeline level settings to proceed.");
      }
      if (isAccountLevelSettingDisabled) {
        throw new InvalidRequestException(
            "Dynamic execution is disabled. Enable it in the account settings to proceed.");
      }
      if (isPipelineLevelConfigDisabled) {
        throw new InvalidRequestException(
            "Dynamic execution is disabled for this pipeline. Enable it in the pipeline settings to proceed.");
      }
    }

    List<NGTag> tagsFromDynamicYaml = extractTagsFromYaml(pipelineYaml);
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder().isDynamicExecution(true).tags(tagsFromDynamicYaml).build();
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineEntity.withYaml(pipelineYaml),
        planExecutionMetadataWithContext, null, moduleType, null, null, null, useV2, notifyOnlyUser, false, notes,
        scopeInfo, false, null, true, false);
  }

  public PlanExecutionResponseDto startDirectExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineYaml, String inputsYaml, String moduleType, boolean useV2,
      boolean notifyOnlyUser, String notes, ScopeInfo scopeInfo) {
    // Detect YAML version from the pipeline YAML content
    String yamlVersion = NGYamlHelper.getVersion(pipelineYaml);

    // Merge inputs based on YAML version
    if (isNotEmpty(inputsYaml)) {
      if (HarnessYamlVersion.isV1(yamlVersion)) {
        JsonNode inputsJsonNode = YamlUtils.readAsJsonNode(inputsYaml);
        pipelineYaml = InputSetMergeHelperV1.mergeInputSetIntoEntityYaml(inputsJsonNode, pipelineYaml,
            connectorInputsMapper, accountId, orgIdentifier, projectIdentifier, "pipeline");
      } else {
        pipelineYaml = MergeHelper.mergeInputSetFormatYamlToOriginYaml(pipelineYaml, inputsYaml);
      }
    }

    // Pre-process V1 YAML to add IDs to stages and steps
    if (HarnessYamlVersion.isV1(yamlVersion)) {
      pipelineYaml = pmsPipelineServiceHelper.preProcessPipelineYaml(pipelineYaml, false);
      if (isNotEmpty(inputsYaml)) {
        JsonNode inputsJsonNode = YamlUtils.readAsJsonNode(inputsYaml);
        JsonNode pipelineEntityJsonNode = YamlUtils.readAsJsonNode(pipelineYaml);
        pipelineEntityJsonNode = PipelineV1InputMergeHelper.mergeV1UserProvidedInputs(
            pipelineEntityJsonNode, inputsJsonNode, false, pipelineYaml);
        pipelineYaml = YamlUtils.writeYamlString(pipelineEntityJsonNode);
      }
      // Inject type field for V1 pipelines
      pipelineYaml = pmsPipelineServiceHelper.injectTypeField(pipelineYaml);
    }

    String processedYaml;
    try {
      processedYaml = YamlUtils.injectUuid(pipelineYaml);
    } catch (IOException e) {
      throw new InvalidRequestException("Failed to process pipeline YAML: " + e.getMessage(), e);
    }
    // Generate unique execution UUID
    String executionUuid = generateUuid();

    // Append a suffix to the pipeline identifier, so that the pipeline identifier does not conflict with existing
    // pipelines
    pipelineIdentifier = pipelineIdentifier + DIRECT_EXECUTION_SUFFIX;

    ExecutionMetadata executionMetadata =
        ExecutionMetadata.newBuilder()
            .setProcessedYamlVersion(yamlVersion)
            .setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext())
            .setHarnessVersion(yamlVersion)
            .setExecutionUuid(executionUuid) // Set the generated UUID
            .setPipelineIdentifier(pipelineIdentifier)
            .setPipelineStoreType(PipelineStoreType.INLINE)
            .setTriggerInfo(ExecutionTriggerInfo.newBuilder()
                                .setTriggerType(TriggerType.MANUAL)
                                .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier(generateUuid()).build()))
            .build();

    PlanExecutionMetadataWithContext planExecutionMetadataWithContext = PlanExecutionMetadataWithContext.builder()
                                                                            .pipelineYaml(pipelineYaml)
                                                                            .processedYaml(processedYaml)
                                                                            .isDynamicExecution(true)
                                                                            .workflowMode(NGWorkflowType.ORCHESTRATION)
                                                                            .build();
    planExecutionMetadataWithContext.setPlanExecutionMetadata(PlanExecutionMetadata.builder()
                                                                  .accountIdentifier(accountId)
                                                                  .harnessVersion(yamlVersion)
                                                                  .planExecutionId(executionUuid) // Use the same UUID
                                                                  .build());

    PlanExecution planExecution = executionHelper.startExecution(
        accountId, orgIdentifier, projectIdentifier, executionMetadata, planExecutionMetadataWithContext, scopeInfo);
    return PlanExecutionResponseDto.builder().planExecution(planExecution).build();
  }

  // todo: check if we need to take notifyOnlyUser and isDebug
  public PlanExecution startPostExecutionRollback(String accountId, String orgIdentifier, String projectIdentifier,
      String originalExecutionId, List<String> stageNodeExecutionIds, String notes, boolean asyncPlanCreation,
      ScopeInfo scopeInfo) {
    rollbackModeExecutionHelper.checkIfPostExecutionRollbackAllowed(stageNodeExecutionIds);
    // because post execution rollback will not be linked within any other execution via some stage, it does not have
    // any parent stage info
    PlanExecution execution =
        startRollbackModeExecution(accountId, orgIdentifier, projectIdentifier, originalExecutionId,
            stageNodeExecutionIds, ExecutionMode.POST_EXECUTION_ROLLBACK, null, notes, asyncPlanCreation, scopeInfo);
    sendPostProdRollbackTelemetryEvent(accountId, orgIdentifier, projectIdentifier, execution, scopeInfo);
    return execution;
  }

  public PlanExecution startPipelineRollback(String accountId, String orgIdentifier, String projectIdentifier,
      String originalExecutionId, PipelineStageInfo parentStageInfo, ScopeInfo scopeInfo) {
    List<String> stageExecutionIds =
        nodeExecutionService.fetchStageExecutions(originalExecutionId)
            .stream()
            .filter(n -> !n.getGroup().equals("STRATEGY"))
            .filter(n -> !n.getStepType().getType().equals(OrchestrationStepTypes.PIPELINE_ROLLBACK_STAGE))
            .map(NodeExecution::getUuid)
            .collect(Collectors.toList());
    return startRollbackModeExecution(accountId, orgIdentifier, projectIdentifier, originalExecutionId,
        stageExecutionIds, ExecutionMode.PIPELINE_ROLLBACK, parentStageInfo, null, true, scopeInfo);
  }

  PlanExecution startRollbackModeExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String originalExecutionId, List<String> stageNodeExecutionIds, ExecutionMode executionMode,
      PipelineStageInfo parentStageInfo, String notes, boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    String executionId = generateUuid();
    ExecutionTriggerInfo triggerInfo = executionHelper.buildTriggerInfo(null);
    PlanExecution originalPlanExecution = planExecutionService.getWithFieldsIncluded(
        originalExecutionId, Set.of(PlanExecutionKeys.createdAt, PlanExecutionKeys.metadata));
    ExecutionMetadata originalExecutionMetadata = originalPlanExecution.getMetadata();
    rollbackModeExecutionHelper.checkAndThrowExceptionIfExecutionOlderThanOneMonthForPostProdRollback(
        originalPlanExecution.getCreatedAt(), executionMode);
    ExecutionMetadata executionMetadata = rollbackModeExecutionHelper.transformExecutionMetadata(
        originalExecutionMetadata, executionId, triggerInfo, executionMode, parentStageInfo, stageNodeExecutionIds);

    Optional<PlanExecutionMetadata> optPlanExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(accountId, originalExecutionId);
    if (optPlanExecutionMetadata.isEmpty()) {
      return null;
    }
    PlanExecutionMetadata originalPlanExecutionMetadata = optPlanExecutionMetadata.get();
    PlanExecutionMetadataWithContext planExecutionMetadataWithContext = PlanExecutionMetadataWithContext.builder()
                                                                            .previousExecutionId(originalExecutionId)
                                                                            .runAllStages(true)
                                                                            .isAsyncPlanCreation(asyncPlanCreation)
                                                                            .build();
    PlanExecutionMetadata planExecutionMetadata =
        rollbackModeExecutionHelper.transformPlanExecutionMetadata(originalPlanExecutionMetadata, executionId,
            executionMode, stageNodeExecutionIds, notes, planExecutionMetadataWithContext);
    planExecutionMetadataWithContext.setPlanExecutionMetadata(planExecutionMetadata);
    try (GlobalContextManager.GlobalContextGuard ignore = GlobalContextManager.ensureGlobalContextGuard()) {
      if (executionMode.equals(ExecutionMode.POST_EXECUTION_ROLLBACK)) {
        // Populating GitDetails for executions where the pipeline is stored in Git
        GitSyncBranchContext branchContext =
            pmsGitSyncHelper.deserializeGitSyncBranchContext(originalExecutionMetadata.getGitSyncBranchContext());
        GitAwareContextHelper.populateGitDetails(branchContext.getGitBranchInfo());
      }
      return executionHelper.startExecution(
          accountId, orgIdentifier, projectIdentifier, executionMetadata, planExecutionMetadataWithContext, scopeInfo);
    }
  }

  private PlanExecutionResponseDto getPlanExecutionResponseDto(String accountId, String orgIdentifier,
      String projectIdentifier, boolean useV2, PipelineEntity pipelineEntity, ExecArgs execArgs, boolean isDebug,
      ScopeInfo scopeInfo) {
    return getPlanExecutionResponseDto(
        accountId, orgIdentifier, projectIdentifier, useV2, pipelineEntity, execArgs, scopeInfo, true, isDebug);
  }

  private PlanExecutionResponseDto getPlanExecutionResponseDto(String accountId, String orgIdentifier,
      String projectIdentifier, boolean useV2, PipelineEntity pipelineEntity, ExecArgs execArgs, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, boolean isDebug) {
    // OPA onSave gate runs on every execution (including rerun with original YAML) to enforce current governance
    // policies.
    applyOpaOnSaveGate(pipelineEntity, execArgs, scopeInfo);
    PlanExecution planExecution =
        executionHelper.startExecution(accountId, orgIdentifier, projectIdentifier, execArgs.getMetadata(),
            execArgs.getPlanExecutionMetadataWithContext(), scopeInfo, isParentIdQueryingEnabled, isDebug);
    return PlanExecutionResponseDto.builder()
        .planExecution(planExecution)
        .gitDetails(PMSPipelineDtoMapper.getEntityGitDetails(pipelineEntity))
        .build();
  }

  public void applyOpaOnSaveGate(PipelineEntity pipelineEntity, ExecArgs execArgs, ScopeInfo scopeInfo) {
    if (!pmsFeatureFlagHelper.isEnabled(pipelineEntity.getAccountId(), FeatureName.PIPE_OPA_GITX_ENFORCEMENT)) {
      return;
    }
    // Enforcement is fail-closed: with no resolved scope there is no way to evaluate policies, so refuse the execution.
    if (scopeInfo == null) {
      throw new InvalidRequestException(
          String.format("Unable to evaluate governance policies: scope could not be resolved for pipeline [%s].",
              pipelineEntity.getIdentifier()));
    }
    OpaEnforcementResult result = pipelineOpaStatusHandler.doOpaOnSaveEvaluation(
        pipelineEntity, scopeInfo, execArgs.getPlanExecutionMetadataWithContext().getPipelineYamlWithTemplateRef());
    if (result.isBlocked()) {
      String msg = result.isOpaUnavailable()
          ? "Execution blocked: OPA policy service is unavailable (fail-closed). " + result.getMessage()
          : result.getMessage();
      OpaOnSaveStatusDTO opaStatus = result.getOpaOnSaveStatusDTO();
      throw new PolicyEvaluationFailureException(msg, opaStatus);
    }
  }

  private ExecArgs getExecArgsWithJsonNode(String originalExecutionId, String moduleType, JsonNode runtimeInputJsonNode,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean notifyOnlyUser,
      PipelineEntity pipelineEntity, boolean isDebug, String notes, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled, PlanExecutionMetadataWithContext planExecutionMetadataWithContext,
      boolean shouldRunAsV1) {
    ExecutionTriggerInfo triggerInfo = executionHelper.buildTriggerInfo(originalExecutionId);

    // RetryExecutionParameters
    RetryExecutionParameters retryExecutionParameters = buildRetryExecutionParameters(false, null, null, null);
    return executionHelper.buildExecutionArgs(pipelineEntity, moduleType, stagesToRun, expressionValues, triggerInfo,
        originalExecutionId, retryExecutionParameters, notifyOnlyUser, isDebug, notes, runtimeInputJsonNode, scopeInfo,
        isParentIdQueryingEnabled, planExecutionMetadataWithContext, shouldRunAsV1);
  }

  public PlanExecutionResponseDto retryPipelineWithInputSetPipelineYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, String inputSetPipelineYaml, String previousExecutionId, List<String> retryStagesIdentifier,
      boolean runAllStages, boolean useV2, boolean isDebug, String notes, boolean asyncPlanCreation,
      ScopeInfo scopeInfo, Map<String, String> expressionValues) {
    PipelineEntity pipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo);

    boolean isDagEnabled = Boolean.TRUE.equals(pipelineEntity.getEnableDAG());
    RetryGroup retryGroup = retryExecutionHelper.validateRetryStagesIdentifiersAndGetRetryGroup(
        previousExecutionId, retryStagesIdentifier, pipelineEntity.getHarnessVersion(), isDagEnabled);

    if (!runAllStages && retryStagesIdentifier.size() > 1) {
      // run only failed stage
      retryStagesIdentifier = retryExecutionHelper.fetchOnlyFailedStages(retryGroup.getInfo(), retryStagesIdentifier);
    }

    ExecutionTriggerInfo triggerInfo = executionHelper.buildTriggerInfo(null);
    Optional<PlanExecutionMetadata> optionalPlanExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(accountId, previousExecutionId);

    if (!optionalPlanExecutionMetadata.isPresent()) {
      throw new InvalidRequestException(String.format("No plan exist for %s planExecutionId", previousExecutionId));
    }
    PlanExecutionMetadata planExecutionMetadata = optionalPlanExecutionMetadata.get();
    boolean readSwitchEnabled =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
    PlanExecution previousPlanExecution = null;
    if (readSwitchEnabled) {
      Optional<PlanExecution> planExecutionOptional = planExecutionService.getWithFieldsIncludedOptional(
          previousExecutionId, Set.of(PlanExecutionKeys.processedYaml, PlanExecutionKeys.stagesExecutionMetadata));
      if (planExecutionOptional.isPresent()) {
        previousPlanExecution = planExecutionOptional.get();
      }
    }
    String previousProcessedYaml = PlanExecutionMigrationHelper.readProcessedYamlWithFallBackOnMetadata(
        planExecutionMetadata, previousPlanExecution);
    List<String> identifierOfSkipStages = new ArrayList<>();

    // RetryExecutionParameters
    // TODO(BRIJESH): Stage identifiers should be same as YAML and not with the matrix prefix. Its temp. Do something
    // here.
    RetryExecutionParameters retryExecutionParameters =
        buildRetryExecutionParameters(true, previousProcessedYaml, retryStagesIdentifier, identifierOfSkipStages);

    StagesExecutionMetadata stagesExecutionMetadata =
        PlanExecutionMigrationHelper.readStagesExecutionMetadataWithFallBackOnMetadata(
            planExecutionMetadata, previousPlanExecution);

    Map<String, String> expressionValuesToUse = null;
    if (EmptyPredicate.isNotEmpty(expressionValues)) {
      expressionValuesToUse = expressionValues;
    } else if (stagesExecutionMetadata != null) {
      expressionValuesToUse = stagesExecutionMetadata.getExpressionValues();
    }

    JsonNode inputSetPipelineJsonNode = null;
    String resolvedPipelineYaml;
    resolvedPipelineYaml =
        pipelineTemplateHelper.resolveOnlyPipelineTemplateRefAndMerge(accountId, orgIdentifier, projectIdentifier,
            pipelineEntity.getYaml(), pipelineEntity.getStoreType(), "false", pipelineEntity.getHarnessVersion());
    if (PipelineYamlUtils.isFixedInputsOnRerun(resolvedPipelineYaml)) {
      if (EmptyPredicate.isNotEmpty(planExecutionMetadata.getInputSetYaml())) {
        inputSetPipelineJsonNode = YamlUtils.readAsJsonNode(planExecutionMetadata.getInputSetYaml());
      }
    } else {
      if (!isEmpty(inputSetPipelineYaml)) {
        inputSetPipelineJsonNode = YamlUtils.readAsJsonNode(inputSetPipelineYaml);
      }
    }

    PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
        PlanExecutionMetadataWithContext.builder()
            .isRetry(true)
            .identifierOfSkipStages(identifierOfSkipStages)
            .previousExecutionId(previousExecutionId)
            .retryStagesIdentifier(retryStagesIdentifier)
            .runAllStages(runAllStages)
            .isAsyncPlanCreation(asyncPlanCreation)
            .build();
    resolveAndAssignInputSetsToExecution(accountId, orgIdentifier, projectIdentifier, pipelineEntity.getIdentifier(),
        retryExecutionHelper.getInputSetIdForRerunPipeline(accountId, previousExecutionId),
        planExecutionMetadataWithContext, scopeInfo, true);

    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType,
        stagesExecutionMetadata == null ? null : stagesExecutionMetadata.getStageIdentifiers(), expressionValuesToUse,
        triggerInfo, previousExecutionId, retryExecutionParameters, false, isDebug, notes, inputSetPipelineJsonNode,
        planExecutionMetadataWithContext, true, scopeInfo);
    applyOpaOnSaveGate(pipelineEntity, execArgs, scopeInfo);
    PlanExecution planExecution = executionHelper.startExecution(accountId, orgIdentifier, projectIdentifier,
        execArgs.getMetadata(), execArgs.getPlanExecutionMetadataWithContext(), isDebug, scopeInfo);
    return PlanExecutionResponseDto.builder()
        .planExecution(planExecution)
        .gitDetails(EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity))
        .build();
  }

  public RetryExecutionParameters buildRetryExecutionParameters(
      boolean isRetry, String processedYaml, List<String> stagesIdentifier, List<String> identifierOfSkipStages) {
    if (!isRetry) {
      return RetryExecutionParameters.builder().isRetry(false).build();
    }

    return RetryExecutionParameters.builder()
        .isRetry(true)
        .previousProcessedYaml(processedYaml)
        .retryStagesIdentifier(stagesIdentifier)
        .identifierOfSkipStages(identifierOfSkipStages)
        .build();
  }

  private void sendExecutionStartTelemetryEvent(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    HashMap<String, Object> propertiesMap = new HashMap<>();
    propertiesMap.put(PROJECT_IDENTIFIER, projectId);
    propertiesMap.put(ORG_IDENTIFIER, orgId);
    propertiesMap.put(PIPELINE_ID, pipelineIdentifier);
    pipelineTelemetryHelper.sendTelemetryEventWithAccountName(START_PIPELINE_EXECUTION_EVENT, accountId, propertiesMap);
  }

  public PlanExecutionResponseDto runPipelineAsChildPipelineWithJsonNode(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, JsonNode runtimeJsonNode, boolean useV2,
      boolean notifyOnlyUser, List<String> inputSetReferences, PipelineStageInfo info, boolean isDebug,
      String originalExecutionId, boolean useOriginalPipelineYaml, ScopeInfo scopeInfo) {
    JsonNode inputSetJsonNode = runtimeJsonNode;
    boolean processAdditionalBaseKeys =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIE_PROCESS_ADDITIONAL_BASE_KEYS);
    if (!isEmpty(inputSetReferences)) {
      GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
      // Prefer transientBranch (set by upstream withBranch/step logic) to avoid default-branch fallback when repos
      // differ
      String branchForMerge = GitAwareContextHelper.isTransientBranchSet() && gitEntityInfo.getTransientBranch() != null
          ? gitEntityInfo.getTransientBranch()
          : gitEntityInfo.getBranch();
      inputSetJsonNode = validateAndMergeHelper.getMergeInputSetFromPipelineTemplateWithJsonNode(pipelineIdentifier,
          inputSetReferences, branchForMerge, gitEntityInfo.getRepoName(), null, processAdditionalBaseKeys, scopeInfo);
    }
    return startPlanExecutionForChildPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        originalExecutionId, moduleType, Collections.emptyList(), Collections.emptyMap(), useV2, notifyOnlyUser, info,
        isDebug, inputSetJsonNode, useOriginalPipelineYaml, scopeInfo);
  }

  private PlanExecutionResponseDto startPlanExecutionForChildPipeline(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String originalExecutionId, String moduleType,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean useV2, boolean notifyOnlyUser,
      PipelineStageInfo info, boolean isDebug, JsonNode mergedRuntimeInputJsonNode, boolean useOriginalPipelineYaml,
      ScopeInfo scopeInfo) {
    PipelineEntity pipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo);
    if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
      throw new InvalidRequestException(String.format(
          "Cannot execute a Draft Pipeline with PipelineID: %s, ProjectID %s", pipelineIdentifier, projectIdentifier));
    }

    GitEntityInfo gitEntityInfo = getGitContextForChildPipelineExecutions(pipelineEntity);
    try (EntityGitDetailsGuard ignored = new EntityGitDetailsGuard(gitEntityInfo)) {
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext =
          PlanExecutionMetadataWithContext.builder().runAllStages(true).isAsyncPlanCreation(true).build();

      if (useOriginalPipelineYaml) {
        // Get pipeline entity with original YAML and prepare for rerun
        OriginalYamlRerunResult result = prepareForOriginalYamlRerun(accountId, orgIdentifier, projectIdentifier,
            pipelineEntity.getIdentifier(), originalExecutionId, true, pipelineEntity, true, scopeInfo);

        pipelineEntity = result.getPipelineEntity();
        mergedRuntimeInputJsonNode = result.getOriginalInputs();
        planExecutionMetadataWithContext = result.getMetadataWithContext();
      }
      ExecArgs execArgs = getExecArgsWithJsonNode(originalExecutionId, moduleType, mergedRuntimeInputJsonNode,
          stagesToRun, expressionValues, notifyOnlyUser, pipelineEntity, isDebug, null, scopeInfo, true,
          planExecutionMetadataWithContext, false);
      if (info != null) {
        execArgs.setMetadata(execArgs.getMetadata().toBuilder().setPipelineStageInfo(info).build());

        PlanExecutionMetadata planExecutionMetadata =
            planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(accountId, info.getExecutionId(),
                Set.of(PlanExecutionMetadataKeys.triggerPayload, PlanExecutionMetadataKeys.triggerJsonPayload,
                    PlanExecutionMetadataKeys.expressionFunctorToken));
        boolean readSwitchEnabled =
            pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PIPE_EXECUTION_SWITCH_FIELD_SOURCE.name());
        PlanExecution planExecution = null;
        if (readSwitchEnabled) {
          Optional<PlanExecution> planExecutionOptional =
              planExecutionService.getWithFieldsIncludedOptional(info.getExecutionId(),
                  Set.of(PlanExecutionKeys.expressionFunctorToken, PlanExecutionKeys.triggerJsonPayload,
                      PlanExecutionKeys.triggerPayload));
          if (planExecutionOptional.isPresent()) {
            planExecution = planExecutionOptional.get();
          }
        }

        String triggerJsonPayload = PlanExecutionMigrationHelper.readTriggerJsonPayloadWithFallBackOnMetadata(
            planExecutionMetadata, planExecution);
        if (isNull(triggerJsonPayload)) {
          triggerJsonPayload = "";
        }
        TriggerPayload triggerPayload =
            PlanExecutionMigrationHelper.readTriggerPayloadWithFallBackOnMetadata(planExecutionMetadata, planExecution);
        if (isNull(triggerPayload)) {
          triggerPayload = TriggerPayload.newBuilder().build();
        }
        // Setting payload, trigger info to support trigger expression in child pipeline
        setTriggerInfo(info, execArgs, accountId, triggerJsonPayload, triggerPayload);

        // Setting expressionFunctor token for child pipeline
        Long expressionFunctorToken = PlanExecutionMigrationHelper.readExpressionFunctorTokenWithFallBackOnMetadata(
            planExecutionMetadata, planExecution);
        setExpressionFunctorToken(execArgs, expressionFunctorToken);
      }
      return getPlanExecutionResponseDto(
          accountId, orgIdentifier, projectIdentifier, useV2, pipelineEntity, execArgs, isDebug, scopeInfo);
    }
  }

  public void setExpressionFunctorToken(ExecArgs execArgs, Long expressionFunctorToken) {
    if (nonNull(expressionFunctorToken)) {
      PlanExecutionMetadata origPlanExecutionMetadata =
          execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
      execArgs.setPlanExecutionMetadataWithContext(
          execArgs.getPlanExecutionMetadataWithContext()
              .withExpressionFunctorToken(expressionFunctorToken)
              .withPlanExecutionMetadata(origPlanExecutionMetadata.withExpressionFunctorToken(expressionFunctorToken)));
    }
  }

  public void setTriggerInfo(PipelineStageInfo info, ExecArgs execArgs, String accountId, String triggerJsonPayload,
      TriggerPayload triggerPayload) {
    // Need to set triggerJsonPayload from parent to child to resolve trigger expression in child

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, info.getExecutionId());

    PlanExecutionMetadata origPlanExecutionMetadata =
        execArgs.getPlanExecutionMetadataWithContext().getPlanExecutionMetadata();
    execArgs.setPlanExecutionMetadataWithContext(execArgs.getPlanExecutionMetadataWithContext()
                                                     .toBuilder()
                                                     .planExecutionMetadata(origPlanExecutionMetadata.toBuilder()
                                                                                .triggerJsonPayload(triggerJsonPayload)
                                                                                .triggerPayload(triggerPayload)
                                                                                .build())
                                                     .triggerPayload(triggerPayload)
                                                     .triggerJsonPayload(triggerJsonPayload)
                                                     .build());

    // To support expression like - <+pipeline.triggeredBy.name>
    execArgs.setMetadata(execArgs.getMetadata()
                             .toBuilder()
                             .setTriggerInfo(pipelineExecutionSummaryEntity.getExecutionTriggerInfo())
                             .build());
  }

  /**
   * Sends a telemetry event for the post prod rollback.
   *
   * @param accountId     the account Identifier
   * @param orgId         the organization Identifier
   * @param projectId     the project Identifier
   * @param planExecution the plan execution of the pipeline
   * @param scopeInfo     the scopeInfo object with uniqueId
   */
  private void sendPostProdRollbackTelemetryEvent(
      String accountId, String orgId, String projectId, PlanExecution planExecution, ScopeInfo scopeInfo) {
    String pipelineId = null;
    try {
      pipelineId = AmbianceUtils.getPipelineIdentifier(planExecution.getAmbiance());
      HashMap<String, Object> propertiesMap = new HashMap<>();
      propertiesMap.put(PROJECT_IDENTIFIER, projectId);
      propertiesMap.put(ORG_IDENTIFIER, orgId);
      propertiesMap.put(PIPELINE_ID, pipelineId);
      if (scopeInfo != null) {
        propertiesMap.put(PARENT_UNIQUE_IDENTIFIER, scopeInfo.getUniqueId());
      }
      propertiesMap.put(PLAN_EXECUTION_ID, planExecution.getUuid());
      pipelineTelemetryHelper.sendTelemetryEventWithAccountName(
          POST_PROD_ROLLBACK_PIPELINE_EXECUTION_EVENT, accountId, propertiesMap);
    } catch (Exception e) {
      log.error("Error sending the telemetry event for Post Prod Rollback for pipeline : {} and error : {}", pipelineId,
          e.getMessage());
    }
  }

  /**
   * Prepares a pipeline entity with original YAML for rerun.
   * This method handles the common logic for updating pipeline entity and creating metadata
   * for rerunning with original YAML.
   */
  private OriginalYamlRerunResult prepareForOriginalYamlRerun(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String originalExecutionId, boolean isChildPipeline,
      PipelineEntity existingPipelineEntity, boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    // Get the original YAML
    String originalYaml = getOriginalYamlFromExecution(accountId, originalExecutionId, isChildPipeline);

    // Get the original execution summary to access tags and other metadata
    PipelineExecutionSummaryEntity summaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, originalExecutionId, false);

    if (summaryEntity == null) {
      throw new InvalidRequestException(String.format(
          "Cannot rerun with original YAML: Execution summary not found for execution ID: %s", originalExecutionId));
    }

    // Update or create pipeline entity with original YAML
    PipelineEntity pipelineEntity;
    if (existingPipelineEntity != null) {
      pipelineEntity = existingPipelineEntity.withYaml(originalYaml);
    } else {
      GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
      if (summaryEntity.getEntityGitDetails() != null) {
        gitEntityInfo = GitEntityInfo.builder().branch(summaryEntity.getEntityGitDetails().getBranch()).build();
      }

      try (EntityGitDetailsGuard gitGuard = new EntityGitDetailsGuard(gitEntityInfo)) {
        pipelineEntity = executionHelper.fetchPipelineEntity(
            accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo);
      } catch (Exception e) {
        throw new InvalidRequestException(
            String.format("Failed to prepare pipeline for original YAML rerun: %s", e.getMessage()), e);
      }

      pipelineEntity = pipelineEntity.withYaml(originalYaml);
    }

    List<NGTag> originalTags = summaryEntity.getTags();
    if (isNotEmpty(originalTags)) {
      pipelineEntity = pipelineEntity.withTags(originalTags);
    }

    JsonNode originalInputs = getRuntimeInputJsonNodeForRerun(accountId, originalExecutionId);

    PlanExecutionMetadataWithContext metadataWithContext = PlanExecutionMetadataWithContext.builder()
                                                               .runAllStages(true)
                                                               .isOriginalYamlUsedOnRerun(true)
                                                               .tags(originalTags)
                                                               .isAsyncPlanCreation(asyncPlanCreation)
                                                               .build();

    return OriginalYamlRerunResult.builder()
        .pipelineEntity(pipelineEntity)
        .originalInputs(originalInputs)
        .metadataWithContext(metadataWithContext)
        .build();
  }

  /**
   * Gets the original YAML from a previous execution.
   * This method handles the common logic for retrieving original YAML for pipeline reruns.
   */
  private String getOriginalYamlFromExecution(String accountId, String originalExecutionId, boolean isChildPipeline) {
    // Get and validate original execution
    PlanExecutionMetadata originalMetadata = planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
        accountId, originalExecutionId, Collections.emptySet());

    if (originalMetadata == null) {
      throw new EntityNotFoundException("Execution details not found for id: " + originalExecutionId);
    }

    String originalYaml = originalMetadata.getYaml();
    if (isEmpty(originalYaml)) {
      String errorMsg = isChildPipeline
          ? "Original Child pipeline YAML is empty for execution id: " + originalExecutionId
          : "Original pipeline YAML is empty for execution id: " + originalExecutionId;
      throw new InvalidRequestException(errorMsg);
    }

    return originalYaml;
  }

  private void validateOriginalYamlRerunSettings(String accountId, String orgIdentifier, String projectIdentifier) {
    String settingValue = NGRestUtils
                              .getResponse(ngSettingsClient.getSetting(
                                  ALLOW_ORIGINAL_YAML_ON_RERUN.getName(), accountId, orgIdentifier, projectIdentifier))
                              .getValue();

    if (BOOLEAN_FALSE_VALUE.equals(settingValue)) {
      throw new InvalidRequestException(
          "Using original pipeline YAML for rerun is not enabled. Enable this setting to proceed.");
    }
  }
  public void resolveAndAssignInputSetsToExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, List<String> inputSetIdentifiers,
      PlanExecutionMetadataWithContext planExecutionMetadataWithContext, ScopeInfo scopeInfo,
      boolean isParentIdQueryingEnabled) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    if (isEmpty(inputSetIdentifiers)) {
      return;
    }
    Map<String, InputSetEntity> availableInputSets =
        pmsInputSetService
            .list(PMSInputSetFilterHelper.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, InputSetListTypePMS.ALL, null, false,
                scopeInfo != null ? scopeInfo.getUniqueId() : null, isParentIdQueryingEnabled))
            .stream()
            .collect(Collectors.toMap(InputSetEntity::getIdentifier, Function.identity(), (e1, e2) -> e1));

    List<String> invalidInputSetIds = inputSetIdentifiers.stream()
                                          .filter(inputSetId -> !availableInputSets.containsKey(inputSetId))
                                          .collect(Collectors.toList());

    if (!invalidInputSetIds.isEmpty()) {
      throw new InvalidRequestException(
          String.format("The following input set identifiers do not exist for pipeline '%s': %s", pipelineIdentifier,
              String.join(", ", invalidInputSetIds)));
    }
    LinkedHashSet<String> uniqueNormalInputSetIds = new LinkedHashSet<>();

    ScopeInfo finalScopeInfo = scopeInfo;
    inputSetIdentifiers.forEach(inputSetId -> {
      InputSetEntity inputSet = availableInputSets.get(inputSetId);
      if (InputSetEntityType.OVERLAY_INPUT_SET.equals(inputSet.getInputSetEntityType())) {
        try {
          // For REMOTE overlays the Mongo-cached inputSetReferences field is not refreshed when the overlay is edited
          // in git, so we derive the references from the freshly-fetched yaml. Mirrors the pattern already used by the
          // merge/consume path in ValidateAndMergeHelper#getInputSetMetadataDTO.
          List<String> baseInputSetIds =
              pmsInputSetService
                  .getWithoutValidations(
                      finalScopeInfo, pipelineIdentifier, inputSetId, false, false, false, isParentIdQueryingEnabled)
                  .map(fetched
                      -> StoreType.REMOTE.equals(fetched.getStoreType()) && isNotEmpty(fetched.getYaml())
                          ? InputSetYamlHelper.getReferencesFromOverlayInputSetYaml(fetched.getYaml())
                          : fetched.getInputSetReferences())
                  .orElse(Collections.emptyList());

          uniqueNormalInputSetIds.addAll(baseInputSetIds);
        } catch (Exception e) {
          log.warn("Error processing overlay inputSet {}", inputSetId, e);
        }
      } else {
        uniqueNormalInputSetIds.add(inputSetId);
      }
    });
    if (isNotEmpty(uniqueNormalInputSetIds)) {
      planExecutionMetadataWithContext.setInputSetIdentifiers(new ArrayList<>(uniqueNormalInputSetIds));
    }
  }

  private List<NGTag> extractTagsFromYaml(String pipelineYaml) {
    try {
      var basicPipeline = PipelineYamlUtils.getBasicPipelineObject(pipelineYaml);
      if (basicPipeline != null && basicPipeline.getTags() != null) {
        return TagMapper.convertToList(basicPipeline.getTags());
      }
    } catch (Exception e) {
      log.warn("Failed to extract tags from dynamic pipeline YAML, falling back to pipeline entity tags", e);
    }
    return new ArrayList<>();
  }

  private GitEntityInfo getGitContextForChildPipelineExecutions(PipelineEntity childPipelineEntity) {
    if (pmsFeatureFlagHelper.isEnabled(
            childPipelineEntity.getAccountId(), PIPE_REVERT_GITX_CHILD_PIPELINE_CONTEXT_ISSUE_FIX)) {
      return GitAwareContextHelper.getGitRequestParamsInfo();
    } else {
      // default case
      return GitEntityInfo.builder()
          .branch(gitAwareEntityHelper.getWorkingBranch(
              GitAwareContextHelper.getRepoFromGitContext(), childPipelineEntity.getRepo()))
          .storeType(childPipelineEntity.getStoreType())
          .repoName(childPipelineEntity.getRepo())
          .build();
    }
  }
}
