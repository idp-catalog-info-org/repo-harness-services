/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.pms.plan.execution.PlanExecutionInterruptType.ABORT;
import static io.harness.pms.plan.execution.PlanExecutionInterruptType.ABORTALL;

import static java.lang.String.format;

import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.engine.executions.retry.RetryHistoryResponseDto;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.executions.retry.RetryLatestExecutionResponseDto;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.PipelineException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.resource.CheckPostExecutionRollbackDTO;
import io.harness.pms.execution.resource.CheckPostExecutionRollbackDTO.CheckPostExecutionRollbackDTOBuilder;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.pipeline.PlanExecutionMetaRequestDTO;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.PlanExecutionInterruptTypePipeline;
import io.harness.pms.plan.execution.PlanExecutionInterruptTypeStage;
import io.harness.pms.plan.execution.PlanExecutionResourceConstants;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.RollbackModeExecutionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.RetryPipelineRequestDTO;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.beans.request.ManualExecutionRequestDto;
import io.harness.pms.plan.execution.beans.response.ManualExecutionResponseDto;
import io.harness.pms.plan.execution.mapper.ManualExecutionActionMapper;
import io.harness.pms.plan.execution.resources.PlanExecutionResource;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.preflight.dto.PreFlightDTO;
import io.harness.pms.preflight.service.intfc.PreflightService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.stages.StageExecutionResponse;
import io.harness.pms.stages.StageExecutionSelectorHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.template.yaml.ref.PipelineTemplateRefInfo;
import io.harness.template.yaml.ref.TemplateRefHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
@ScopeInfoResolutionApi
public class PlanExecutionResourceImpl implements PlanExecutionResource {
  @Inject private final PipelineExecutor pipelineExecutor;
  @Inject private final PMSExecutionService pmsExecutionService;
  @Inject private final OrchestrationEventLogRepository orchestrationEventLogRepository;
  @Inject private final AccessControlClient accessControlClient;
  @Inject PlanExecutionService planExecutionService;
  @Inject private final NodeExecutionService nodeExecutionService;

  @Inject private final PreflightService preflightService;
  @Inject private final PMSPipelineService pmsPipelineService;
  @Inject private final RetryExecutionHelper retryExecutionHelper;
  @Inject private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private final PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private NGSettingsClient settingsClient;
  @Inject private final RollbackModeExecutionHelper rollbackModeExecutionHelper;
  @Inject private final PMSPipelineServiceHelper pmsPipelineServiceHelper;

  private final List<PlanExecutionInterruptType> abortInterruptTypesList = Arrays.asList(ABORTALL, ABORT);

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> runPipelineWithInputSetPipelineYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, boolean notifyOnlyUser, String notes, String inputSetPipelineYaml,
      ScopeInfo scopeInfo, List<String> inputSetIdentifiers, boolean asyncPlanCreation, boolean shouldRunAsV1) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    try {
      PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.runPipelineWithInputSetPipelineYaml(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, inputSetPipelineYaml, false,
          notifyOnlyUser, notes, scopeInfo, inputSetIdentifiers, asyncPlanCreation, shouldRunAsV1);
      return ResponseDTO.newResponse(planExecutionResponseDto);
    } catch (NGTemplateException ex) {
      throw new PipelineException(
          PipelineException.PIPELINE_Execution_MESSAGE, ex, ErrorCode.NG_PIPELINE_EXECUTION_EXCEPTION);
    }
  }

  @Override
  public ResponseDTO<String> getCompiledYamlForPipeline(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String inputSetPipelineYaml) {
    return ResponseDTO.newResponse(pipelineExecutor.getCompiledYamlForPipeline(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetPipelineYaml));
  }

  @Override
  public ResponseDTO<PlanExecutionResponseDto> runPostExecutionRollback(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String planExecutionId, String stageNodeExecutionIds,
      String notes, boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    // TODO:(BRIJESH) Take stageNodeExecutionIds as list.
    // pipelineIdentifier is needed for the access control check. If it is not passed in, fetch the plan execution
    // to derive it.
    String resolvedPipelineIdentifier = pipelineIdentifier;
    if (isEmpty(resolvedPipelineIdentifier)) {
      PlanExecution existingPlanExecution =
          planExecutionService.getWithFieldsIncluded(planExecutionId, Sets.newHashSet(PlanExecutionKeys.ambiance));
      resolvedPipelineIdentifier = existingPlanExecution.getAmbiance().getMetadata().getPipelineIdentifier();
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", resolvedPipelineIdentifier), PipelineRbacPermissions.PIPELINE_EXECUTE);
    PlanExecution planExecution =
        pipelineExecutor.startPostExecutionRollback(accountId, orgIdentifier, projectIdentifier, planExecutionId,
            Collections.singletonList(stageNodeExecutionIds), notes, asyncPlanCreation, scopeInfo);
    return ResponseDTO.newResponse(PlanExecutionResponseDto.builder().planExecution(planExecution).build());
  }

  @Override
  public ResponseDTO<CheckPostExecutionRollbackDTO> checkIfPostExecutionRollbackIsAllowed(
      List<String> stageNodeExecutionIds) {
    CheckPostExecutionRollbackDTOBuilder builder = CheckPostExecutionRollbackDTO.builder();
    Ambiance ambiance = getAmbianceForRollbackCheck(stageNodeExecutionIds);
    if (ambiance != null) {
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
              AmbianceUtils.getProjectIdentifier(ambiance)),
          Resource.of("PIPELINE", ambiance.getMetadata().getPipelineIdentifier()),
          PipelineRbacPermissions.PIPELINE_EXECUTE);
    }
    try {
      rollbackModeExecutionHelper.checkIfPostExecutionRollbackAllowed(stageNodeExecutionIds);
    } catch (InvalidRequestException ex) {
      return ResponseDTO.newResponse(builder.isAllowed(Boolean.FALSE).build());
    }
    return ResponseDTO.newResponse(builder.isAllowed(Boolean.TRUE).build());
  }

  private Ambiance getAmbianceForRollbackCheck(List<String> stageNodeExecutionIds) {
    if (isEmpty(stageNodeExecutionIds)) {
      return null;
    }
    List<NodeExecution> nodeExecutions = nodeExecutionService.getAllWithFieldIncluded(
        new HashSet<>(stageNodeExecutionIds), NodeProjectionUtils.withAmbiance);
    if (isEmpty(nodeExecutions)) {
      return null;
    }
    Ambiance ambiance = nodeExecutions.get(0).getAmbiance();
    return ambiance == null || isEmpty(ambiance.getMetadata().getPipelineIdentifier()) ? null : ambiance;
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> runPipelineWithInputSetPipelineYamlV2(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, String notes, String inputSetPipelineYaml, List<String> inputSetIdentifiers,
      ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.runPipelineWithInputSetPipelineYaml(accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, inputSetPipelineYaml, false, false, notes,
        scopeInfo, inputSetIdentifiers, false, false);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> runStagesWithRuntimeInputYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, RunStageRequestDTO runStageRequestDTO, String notes,
      List<String> inputSetIdentifiers, boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(
        pipelineExecutor.runStagesWithRuntimeInputYaml(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            moduleType, runStageRequestDTO, false, notes, inputSetIdentifiers, asyncPlanCreation, scopeInfo));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> rerunStagesWithRuntimeInputYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, @NotNull String originalExecutionId,
      GitEntityFindInfoDTO gitEntityBasicInfo, boolean useFQNIfErrorResponse, RunStageRequestDTO runStageRequestDTO,
      String notes, boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(pipelineExecutor.rerunStagesWithRuntimeInputYaml(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, moduleType, originalExecutionId, runStageRequestDTO, false, false, notes,
        asyncPlanCreation, scopeInfo));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> rerunPipelineWithInputSetPipelineYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType, @NotNull String originalExecutionId,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, String inputSetPipelineYaml, String notes, Boolean useOriginalPipelineYaml,
      boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, originalExecutionId,
        inputSetPipelineYaml, false, false, notes, useOriginalPipelineYaml, asyncPlanCreation, scopeInfo);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> rerunPipelineWithInputSetPipelineYamlV2(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType, @NotNull String originalExecutionId,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, String inputSetPipelineYaml, String notes, Boolean useOriginalPipelineYaml,
      ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, originalExecutionId,
        inputSetPipelineYaml, false, false, notes, useOriginalPipelineYaml, false, scopeInfo);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> debugStagesWithRuntimeInputYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, @NotNull String originalExecutionId,
      GitEntityFindInfoDTO gitEntityBasicInfo, boolean useFQNIfErrorResponse, RunStageRequestDTO runStageRequestDTO,
      ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(pipelineExecutor.rerunStagesWithRuntimeInputYaml(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, moduleType, originalExecutionId, runStageRequestDTO, false, true, null,
        false, scopeInfo));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> debugPipelineWithInputSetPipelineYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType, @NotNull String originalExecutionId,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, String inputSetPipelineYaml, boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, originalExecutionId,
        inputSetPipelineYaml, false, true, null, false, asyncPlanCreation, scopeInfo);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> debugPipelineWithInputSetPipelineYamlV2(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType, @NotNull String originalExecutionId,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, String inputSetPipelineYaml, ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, originalExecutionId,
        inputSetPipelineYaml, false, true, null, false, false, scopeInfo);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<RetryInfo> getRetryStages(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, @NotNull String planExecutionId,
      GitEntityFindInfoDTO gitEntityBasicInfo, String loadFromCache, ScopeInfo scopeInfo) {
    return ResponseDTO.newResponse(retryExecutionHelper.validateRetry(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, planExecutionId, loadFromCache, scopeInfo));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> runPipelineWithInputSetIdentifierList(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, @NotNull @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO, String notes,
      boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.runPipelineWithInputSetReferencesList(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, mergeInputSetRequestDTO,
        gitEntityBasicInfo.getBranch(), gitEntityBasicInfo.getYamlGitConfigId(), notes, asyncPlanCreation, scopeInfo);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> rerunPipelineWithInputSetIdentifierList(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @Parameter(description = PipelineResourceConstants.ORIGINAL_EXECUTION_ID_PARAM_MESSAGE,
          required = true) String originalExecutionId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = PipelineResourceConstants.USE_FQN_IF_ERROR_RESPONSE_ERROR_MESSAGE,
          required = true) boolean useFQNIfErrorResponse,
      @RequestBody(required = true, description = "InputSet reference details") @NotNull
      @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO, String notes, ScopeInfo scopeInfo) {
    // TODO: Remove this ScopeInfo is passed from the context accurately.
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.rerunPipelineWithInputSetReferencesList(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, originalExecutionId, mergeInputSetRequestDTO,
            gitEntityBasicInfo.getBranch(), gitEntityBasicInfo.getYamlGitConfigId(), false, notes, scopeInfo);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  public ResponseDTO<InterruptDTO> handleInterrupt(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectId,
      @Parameter(
          description = "The Interrupt type needed to be applied to the execution. Choose a value from the enum list.")
      @NotNull PlanExecutionInterruptTypePipeline executionInterruptTypePipeline,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.") @NotNull String planExecutionId,
      ScopeInfo scopeInfo) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, planExecutionId, false);
    return ResponseDTO.newResponse(handleInterruptInternal(accountId, orgId, projectId,
        executionSummaryEntity.getPipelineIdentifier(), executionInterruptTypePipeline, planExecutionId, scopeInfo));
  }

  @Override
  public ResponseDTO<InterruptDTO> handleInterruptV2(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectId,
      @Parameter(
          description = "The Interrupt type needed to be applied to the execution. Choose a value from the enum list.")
      @NotNull PlanExecutionInterruptTypePipeline executionInterruptTypePipeline,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.") @NotNull String planExecutionId,
      ScopeInfo scopeInfo) {
    PlanExecution planExecution =
        planExecutionService.getWithFieldsIncluded(planExecutionId, Sets.newHashSet(PlanExecutionKeys.ambiance));
    return ResponseDTO.newResponse(handleInterruptInternal(accountId, orgId, projectId,
        planExecution.getAmbiance().getMetadata().getPipelineIdentifier(), executionInterruptTypePipeline,
        planExecutionId, scopeInfo));
  }

  @Override
  public ResponseDTO<List<InterruptDTO>> bulkAbortPipelineExecutionsInterrupt(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectId,
      @NotNull @RequestBody(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.") PlanExecutionMetaRequestDTO planExecutionIds,
      ScopeInfo scopeInfo) {
    List<InterruptDTO> response = new ArrayList<>();
    final Principal principal = SecurityContextBuilder.getPrincipal();
    if (principal == null || principal.getType() != PrincipalType.SERVICE) {
      // This API will only be called by CG manager(using admin portal) and not directly by any customer
      // Due to which we are adding a SERVICE principal check
      throw new AccessDeniedException("[PIPELINE ADMIN]: The API is called using an external user!", USER);
    }
    for (String executionId : planExecutionIds.getPipelineExecutionIds()) {
      SecurityContextBuilder.setContext(principal);
      ResponseDTO<InterruptDTO> result = handleInterrupt(
          accountId, orgId, projectId, PlanExecutionInterruptTypePipeline.ABORTALL, executionId, scopeInfo);
      response.add(result.getData());
    }
    return ResponseDTO.newResponse(response);
  }

  @Override
  // TODO(prashant) : This is a temp route for now merge it with the above. Need be done in sync with UI changes
  public ResponseDTO<InterruptDTO> handleStageInterrupt(@NotNull String accountId, @NotNull String orgId,
      @NotNull String projectId, @NotNull PlanExecutionInterruptTypeStage executionInterruptTypeStage,
      @NotNull String planExecutionId, @NotNull String nodeExecutionId, ScopeInfo scopeInfo) {
    PlanExecutionInterruptType executionInterruptType =
        PlanExecutionInterruptType.getPipelineExecutionInterrupt(executionInterruptTypeStage.getDisplayName());
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, planExecutionId, false);
    checkForPermissionAccessOrThrow(
        accountId, orgId, projectId, executionSummaryEntity.getPipelineIdentifier(), executionInterruptType, scopeInfo);
    checkIfInterruptIsBehindSettingsAndIsEnabled(accountId, orgId, projectId, executionInterruptType);
    return ResponseDTO.newResponse(
        pmsExecutionService.registerInterrupt(executionInterruptType, planExecutionId, nodeExecutionId));
  }

  @Override
  public ResponseDTO<InterruptDTO> handleManualInterventionInterrupt(@NotNull String accountId, @NotNull String orgId,
      @NotNull String projectId, @NotNull PlanExecutionInterruptType executionInterruptType,
      @NotNull String planExecutionId, @NotNull String nodeExecutionId, ScopeInfo scopeInfo) {
    String pipelineIdentifier =
        planExecutionService.getExecutionMetadataFromPlanExecution(planExecutionId).getPipelineIdentifier();
    checkForPermissionAccessOrThrow(accountId, orgId, projectId, pipelineIdentifier, executionInterruptType, scopeInfo);
    return ResponseDTO.newResponse(
        pmsExecutionService.registerInterrupt(executionInterruptType, planExecutionId, nodeExecutionId));
  }

  @Override
  public ResponseDTO<ManualExecutionResponseDto> handleManualExecution(String accountId, String orgId, String projectId,
      String nodeExecutionId, ManualExecutionRequestDto manualExecutionRequestDto, ScopeInfo scopeInfo) {
    planExecutionService.handleManualExecution(accountId, orgId, projectId, nodeExecutionId,
        ManualExecutionActionMapper.mapWaitStepAction(manualExecutionRequestDto.getAction()), scopeInfo);
    return ResponseDTO.newResponse(ManualExecutionResponseDto.builder().status(true).build());
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<String> startPreFlightCheck(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      String inputSetPipelineYaml, ScopeInfo scopeInfo) {
    try {
      return ResponseDTO.newResponse(preflightService.startPreflightCheck(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetPipelineYaml, scopeInfo));
    } catch (IOException ex) {
      log.error(format("Invalid YAML in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
    }
  }

  @Override
  public ResponseDTO<PreFlightDTO> getPreflightCheckResponse(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String preflightCheckId, String inputSetPipelineYaml) {
    return ResponseDTO.newResponse(preflightService.getPreflightCheckResponse(preflightCheckId));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<List<StageExecutionResponse>> getStagesExecutionList(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      String loadFromCache, ScopeInfo scopeInfo) {
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false,
            false, GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), scopeInfo, true);
    if (!optionalPipelineEntity.isPresent()) {
      throw new InvalidRequestException(format("Pipeline [%s] under Project[%s], Organization [%s] doesn't exist.",
          pipelineIdentifier, projectIdentifier, orgIdentifier));
    }
    orgIdentifier = isEmpty(scopeInfo.getOrgIdentifier()) ? orgIdentifier : scopeInfo.getOrgIdentifier();
    projectIdentifier =
        isEmpty(scopeInfo.getProjectIdentifier()) ? projectIdentifier : scopeInfo.getProjectIdentifier();
    PipelineEntity pipelineEntity = optionalPipelineEntity.get();
    String yaml = pipelineEntity.getYaml();
    // In case of pipeline template we will resolve only the pipeline template, as userFromStage can only be present
    // in either pipeline yaml or pipeline template yaml therefore we do not need to resolve the pipeline yaml
    // completely
    PipelineTemplateRefInfo hasPipelineTemplateWithRef =
        TemplateRefHelper.hasPipelineTemplateRef(yaml, optionalPipelineEntity.get().getHarnessVersion());
    boolean hasPipelineTemplatePresent = hasPipelineTemplateWithRef.isHasPipelineTemplate();
    if (hasPipelineTemplatePresent) {
      String templateIdentifier = hasPipelineTemplateWithRef.getPipelineTemplateIdentifier();
      String versionLabel = hasPipelineTemplateWithRef.getPipelineTemplateVersionLabel();
      String label = hasPipelineTemplateWithRef.getPipelineTemplateLabel();
      String transientBranch = hasPipelineTemplateWithRef.getTransientBranch();
      IdentifierRef templateIdentifierRef = IdentifierRefHelper.getIdentifierRefOrThrowException(
          templateIdentifier, accountId, orgIdentifier, projectIdentifier, YAMLFieldNameConstants.TEMPLATE);
      TemplateResponseDTO templateResponseDTO =
          pipelineTemplateHelper.getTemplate(templateIdentifierRef.getIdentifier(),
              templateIdentifierRef.getAccountIdentifier(), templateIdentifierRef.getOrgIdentifier(),
              templateIdentifierRef.getProjectIdentifier(), versionLabel, label, loadFromCache, transientBranch);
      String templateYaml = templateResponseDTO.getYaml();
      boolean templateOverridesEnabled =
          pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_TEMPLATE_OVERRIDES);
      yaml = TemplateRefHelper.mergePipelineTemplateYamlIntoPipelineYaml(
          hasPipelineTemplateWithRef.getPipelineYamlJsonNode(), templateYaml, yaml,
          hasPipelineTemplateWithRef.getTemplateInputsJsonNode(),
          hasPipelineTemplateWithRef.getTemplateOverridesJsonNode(), templateOverridesEnabled);
    }
    boolean shouldAllowStageExecutions;
    if (HarnessYamlVersion.V0.equals(pipelineEntity.getHarnessVersion())) {
      try {
        BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);
        shouldAllowStageExecutions = basicPipeline.isAllowStageExecutions();
      } catch (IOException ex) {
        if (YamlUtils.isYamlSizeLimitExceeded(ex)) {
          throw new InvalidRequestException(PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE, ex);
        }
        log.error("Cannot create pipeline entity due to " + ex.getMessage(), ex);
        throw new InvalidRequestException("Could not read pipeline yaml, please check if the yaml is valid", ex);
      }
    } else {
      shouldAllowStageExecutions = UnifiedPipelineExecutionUtils.shouldAllowStageExecutions(yaml);
    }

    if (!shouldAllowStageExecutions) {
      return ResponseDTO.newResponse(Collections.emptyList());
    }
    List<StageExecutionResponse> stageExecutionResponse =
        StageExecutionSelectorHelper.getStageExecutionResponse(yaml, pipelineEntity.getHarnessVersion());

    return ResponseDTO.newResponse(stageExecutionResponse);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> retryPipelineWithInputSetPipelineYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType, @NotNull String previousExecutionId,
      @NotNull List<String> retryStagesIdentifier, boolean runAllStages,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, String inputSetPipelineYaml, String notes,
      boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    validateRetryStagesAndExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        previousExecutionId, retryStagesIdentifier, scopeInfo);
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.retryPipelineWithInputSetPipelineYaml(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, inputSetPipelineYaml, previousExecutionId, retryStagesIdentifier,
            runAllStages, false, false, notes, asyncPlanCreation, scopeInfo, null);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> retryPipelineWithInputSetPipelineYamlV2(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType, @NotNull String previousExecutionId,
      @NotNull List<String> retryStagesIdentifier, boolean runAllStages,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, @Valid RetryPipelineRequestDTO retryPipelineRequestDTO,
      String notes, boolean asyncPlanCreation, ScopeInfo scopeInfo) {
    validateRetryStagesAndExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
        previousExecutionId, retryStagesIdentifier, scopeInfo);
    scopeInfo = pmsPipelineServiceHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier, scopeInfo);

    String runtimeInputYaml = retryPipelineRequestDTO != null ? retryPipelineRequestDTO.getRuntimeInputYaml() : null;
    Map<String, String> expressionValues =
        retryPipelineRequestDTO != null ? retryPipelineRequestDTO.getExpressionValues() : null;

    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.retryPipelineWithInputSetPipelineYaml(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, runtimeInputYaml, previousExecutionId, retryStagesIdentifier, runAllStages,
            false, false, notes, asyncPlanCreation, scopeInfo, expressionValues);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<RetryHistoryResponseDto> getRetryHistory(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String pipelineIdentifier, @NotNull String planExecutionId) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, planExecutionId, false);
    String rootParentId = pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId();
    return ResponseDTO.newResponse(retryExecutionHelper.getRetryHistory(accountId, rootParentId, planExecutionId));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<RetryLatestExecutionResponseDto> getRetryLatestExecutionId(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      @NotNull String planExecutionId) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, planExecutionId, false);
    String rootParentId = pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId();
    return ResponseDTO.newResponse(retryExecutionHelper.getRetryLatestExecutionId(accountId, rootParentId));
  }

  private void validateRetryStagesAndExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String previousExecutionId, List<String> retryStagesIdentifier, ScopeInfo scopeInfo) {
    if (retryStagesIdentifier.isEmpty()) {
      throw new InvalidRequestException("You need to select the stage to retry!!");
    }
    RetryInfo retryInfo = retryExecutionHelper.validateRetry(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, previousExecutionId, null, scopeInfo);
    if (!retryInfo.isResumable()) {
      throw new InvalidRequestException(retryInfo.getErrorMessage());
    }
  }

  private void checkIfInterruptIsBehindSettingsAndIsEnabled(@NotNull String accountId, @NotNull String orgId,
      @NotNull String projectId, PlanExecutionInterruptType executionInterruptType) {
    String allowUserToMarkStepAsFailedExplicitlySettingsStatus = null;
    String allowUserToMarkStepAsFailedExplicitly = "allow_user_to_mark_step_as_failed_explicitly";
    String allowUserToMarkStepAsFailedExplicitlyTrueValue = "true";
    if (PlanExecutionInterruptType.UserMarkedFailure.equals(executionInterruptType)) {
      try {
        allowUserToMarkStepAsFailedExplicitlySettingsStatus =
            NGRestUtils
                .getResponse(
                    settingsClient.getSetting(allowUserToMarkStepAsFailedExplicitly, accountId, orgId, projectId))
                .getValue();
      } catch (Exception ex) {
        log.error(format("Could not fetch setting [%s]", allowUserToMarkStepAsFailedExplicitly), ex);
        throw new InvalidRequestException("Could not fetch [Allow user to mark the step as failed explicitly] "
            + "Settings, Please contact Harness for further support.");
      }
      if (!allowUserToMarkStepAsFailedExplicitlyTrueValue.equals(allowUserToMarkStepAsFailedExplicitlySettingsStatus)) {
        throw new InvalidRequestException("[Allow user to mark the step as failed explicitly] Settings is not enabled, "
            + "Please enable this setting if you want to use this product.");
      }
    }
  }

  /***
   * It checks the abort pipeline permission for interrupt of ABORT types  with
   * PIE_PIPELINE_ABORT_RBAC_PERMISSION FF enabled
   * or checks execute pipeline permission for other interrupt type
   * @param accountId
   * @param orgId
   * @param projectId
   * @param pipelineIdentifier
   * @param executionInterruptType
   * @param scopeInfo
   */
  private void checkForPermissionAccessOrThrow(String accountId, String orgId, String projectId,
      String pipelineIdentifier, PlanExecutionInterruptType executionInterruptType, ScopeInfo scopeInfo) {
    orgId = scopeInfo == null || isEmpty(scopeInfo.getOrgIdentifier()) ? orgId : scopeInfo.getOrgIdentifier();
    projectId =
        scopeInfo == null || isEmpty(scopeInfo.getProjectIdentifier()) ? projectId : scopeInfo.getProjectIdentifier();
    if (abortInterruptTypesList.contains(executionInterruptType)
        && pmsFeatureFlagService.isEnabled(accountId, FeatureName.CDS_PIPELINE_ABORT_RBAC_PERMISSION)) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
          Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_ABORT);
    } else {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
          Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_EXECUTE);
    }
  }

  private InterruptDTO handleInterruptInternal(String accountId, String orgId, String projectId, String pipelineId,
      PlanExecutionInterruptTypePipeline executionInterruptTypePipeline, String planExecutionId, ScopeInfo scopeInfo) {
    PlanExecutionInterruptType executionInterruptType =
        PlanExecutionInterruptType.getPipelineExecutionInterrupt(executionInterruptTypePipeline.getDisplayName());
    checkForPermissionAccessOrThrow(accountId, orgId, projectId, pipelineId, executionInterruptType, scopeInfo);
    if (executionInterruptTypePipeline.equals(PlanExecutionInterruptTypePipeline.USERMARKEDFAILURE)) {
      checkIfInterruptIsBehindSettingsAndIsEnabled(accountId, orgId, projectId, executionInterruptType);
    }
    return pmsExecutionService.registerInterrupt(executionInterruptType, planExecutionId, null);
  }
}
