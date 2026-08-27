/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

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
import io.harness.beans.ScopeInfo;
import io.harness.engine.executions.retry.ExecutionInfo;
import io.harness.engine.executions.retry.RetryGroup;
import io.harness.engine.executions.retry.RetryHistoryResponseDto;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.executions.retry.RetryLatestExecutionResponseDto;
import io.harness.engine.executions.retry.RetryStageInfo;
import io.harness.engine.executions.retry.RetryStagesMetadataDTO;
import io.harness.eraro.ErrorCode;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.exception.ngexception.PipelineException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.notification.helper.NotificationHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.pipeline.mappers.GitXCacheMapper;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.plan.execution.PipelineExecutionDetailsApiUtils;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.rbac.PipelineSplitPermissionsHelper;
import io.harness.pms.stages.StageExecutionResponse;
import io.harness.pms.stages.StageExecutionSelectorHelper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.annotations.InternalApi;
import io.harness.security.dto.ApiKeyPrincipal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.pipeline.v1.PipelineExecutionApi;
import io.harness.spec.server.pipeline.v1.model.DirectPipelineExecuteRequestBody;
import io.harness.spec.server.pipeline.v1.model.DynamicPipelineExecuteInternalRequestBody;
import io.harness.spec.server.pipeline.v1.model.DynamicPipelineExecuteRequestBody;
import io.harness.spec.server.pipeline.v1.model.ExecutionDetails;
import io.harness.spec.server.pipeline.v1.model.NotificationRulesData;
import io.harness.spec.server.pipeline.v1.model.NotificationTemplateReconcileRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineExecuteRequestBody;
import io.harness.spec.server.pipeline.v1.model.PipelineExecuteResponseBody;
import io.harness.spec.server.pipeline.v1.model.PipelineNotificationTemplateValidateResponseBody;
import io.harness.spec.server.pipeline.v1.model.RerunPipelineRequest;
import io.harness.spec.server.pipeline.v1.model.RetryPipelineRequest;
import io.harness.spec.server.pipeline.v1.model.RunStageRequestBody;
import io.harness.spec.server.pipeline.v1.model.StageExecutionResponseBody;
import io.harness.spec.server.pipeline.v1.model.StageExecutionResponseList;
import io.harness.spec.server.pipeline.v1.model.UnresolvedNotificationRulesResponseBody;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PipelineExecutionApiImpl implements PipelineExecutionApi {
  private final PipelineExecutor pipelineExecutor;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final RetryExecutionHelper retryExecutionHelper;
  private final PMSPipelineService pmsPipelineService;
  private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  private final ScopeResolutionHelper scopeResolutionHelper;
  @Inject private final AccessControlClient accessControlClient;
  NotificationHelper notificationHelper;
  private final PipelineSplitPermissionsHelper pipelineSplitPermissionsHelper;
  @Inject private final PMSExecutionService pmsExecutionService;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response executePipeline(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @Valid PipelineExecuteRequestBody body,
      @AccountIdentifier String harnessAccount, String module, Boolean useFqnIfErrorResponse, Boolean notifyOnlyUser,
      String notes, String branchName, String connectorRef, String repoName, List<String> inputSetIdentifiers) {
    try {
      String inputSetPipelineYaml = null;
      if (body != null) {
        inputSetPipelineYaml = PipelineInputsUtils.getInputsForPipeline(body.getInputsYaml(), body.getInputs());
      }
      GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().branch(branchName).build());
      ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
      PlanExecutionResponseDto planExecutionResponseDto =
          pipelineExecutor.runPipelineWithInputSetPipelineYaml(harnessAccount, org, project, pipeline, module,
              inputSetPipelineYaml, false, notifyOnlyUser, notes, scopeInfo, inputSetIdentifiers, false, false);
      PipelineExecuteResponseBody pipelineExecuteResponseBody =
          getPipelineExecutionResponseFromPlanExecutionResponse(planExecutionResponseDto);
      return Response.ok().entity(pipelineExecuteResponseBody).build();
    } catch (NGTemplateException ex) {
      throw new PipelineException(
          PipelineException.PIPELINE_Execution_MESSAGE, ex, ErrorCode.NG_PIPELINE_EXECUTION_EXCEPTION);
    }
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response executeStagesWithInputYaml(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @Valid RunStageRequestBody body, @AccountIdentifier String harnessAccount,
      String module, String useFqnIfError, String notes, String branchName, String connectorRef, String repoName,
      List<String> inputSetIdentifiers) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().branch(branchName).build());
    RunStageRequestDTO runStageRequestDTO = getRunStageRequestDTO(body);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.runStagesWithRuntimeInputYaml(harnessAccount,
        org, project, pipeline, module, runStageRequestDTO, false, notes, inputSetIdentifiers, false, scopeInfo);
    PipelineExecuteResponseBody pipelineExecuteResponseBody =
        getPipelineExecutionResponseFromPlanExecutionResponse(planExecutionResponseDto);
    return Response.ok().entity(pipelineExecuteResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getLatestExecutionId(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, String executionId, @AccountIdentifier String harnessAccount) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(harnessAccount, executionId, false);
    String rootParentId = pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId();
    return Response.ok()
        .entity(toRetryLatestExecutionResponseDto(
            retryExecutionHelper.getRetryLatestExecutionId(harnessAccount, rootParentId)))
        .build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getRetryHistory(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, String executionId, @AccountIdentifier String harnessAccount) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(harnessAccount, executionId, false);
    String rootParentId = pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId();
    return Response.ok()
        .entity(
            toRetryHistoryResponseDto(retryExecutionHelper.getRetryHistory(harnessAccount, rootParentId, executionId)))
        .build();
  }

  private RunStageRequestDTO getRunStageRequestDTO(RunStageRequestBody body) {
    return RunStageRequestDTO.builder()
        .stageIdentifiers(body.getStageIdentifiers())
        .runtimeInputYaml(PipelineInputsUtils.getInputsForPipeline(body.getInputsYaml(), body.getInputs()))
        .expressionValues(body.getExpressionValues())
        .build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getStagesExecutionList(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, @AccountIdentifier String harnessAccount, String loadFromCache,
      String branchName, String connectorRef, String repoName) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().branch(branchName).build());
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    Optional<PipelineEntity> optionalPipelineEntity = pmsPipelineService.getPipeline(harnessAccount, org, project,
        pipeline, false, false, false, GitXCacheMapper.parseLoadFromCacheHeaderParam(loadFromCache), scopeInfo, true);
    if (!optionalPipelineEntity.isPresent()) {
      throw new InvalidRequestException(
          format("Pipeline [%s] under Project[%s], Organization [%s] doesn't exist.", pipeline, project, org));
    }
    PipelineEntity pipelineEntity = optionalPipelineEntity.get();
    String yaml = pipelineEntity.getYaml();
    if (Boolean.TRUE.equals(pipelineEntity.getTemplateReference())) {
      org = scopeInfo.getOrgIdentifier();
      project = scopeInfo.getProjectIdentifier();
      yaml = pipelineTemplateHelper
                 .resolveTemplateRefsInPipeline(harnessAccount, org, project, pipelineEntity.getYaml(), loadFromCache,
                     pipelineEntity.getHarnessVersion())
                 .getMergedPipelineYaml();
    }
    boolean shouldAllowStageExecutions;
    if (HarnessYamlVersion.V0.equals(pipelineEntity.getHarnessVersion())) {
      try {
        BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);
        shouldAllowStageExecutions = basicPipeline.isAllowStageExecutions();
      } catch (IOException e) {
        if (YamlUtils.isYamlSizeLimitExceeded(e)) {
          throw new InvalidRequestException(PipelineEntityUtils.PIPELINE_YAML_SIZE_LIMIT_EXCEEDED_MESSAGE, e);
        }
        throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage(), e);
      }
    } else {
      shouldAllowStageExecutions = UnifiedPipelineExecutionUtils.shouldAllowStageExecutions(yaml);
    }

    if (!shouldAllowStageExecutions) {
      return Response.ok().entity(Collections.emptyList()).build();
    }
    List<StageExecutionResponse> stageExecutionResponse =
        StageExecutionSelectorHelper.getStageExecutionResponse(yaml, pipelineEntity.getHarnessVersion());
    List<StageExecutionResponseBody> stagesExecutions = getStageExecutionResponse(stageExecutionResponse);
    StageExecutionResponseList stageExecutionResponseList = new StageExecutionResponseList();
    stageExecutionResponseList.addAll(stagesExecutions);
    return Response.ok().entity(stageExecutionResponseList).build();
  }

  @Override
  public Response listNotificationRulesWithUnresolvedInputs(@Valid NotificationTemplateReconcileRequestBody body,
      String org, String project, String pipeline, String harnessAccount) {
    if (body == null || isEmpty(body.getYaml())) {
      throw new InvalidRequestException(
          "The pipeline YAML file cannot be null. Please provide a valid YAML configuration.");
    }
    ArrayList<Map<String, String>> rulesNeedingConfiguration =
        notificationHelper.listNotificationRulesWithUnresolvedInputs(body.getYaml());
    UnresolvedNotificationRulesResponseBody unresolvedNotificationRulesResponse =
        new UnresolvedNotificationRulesResponseBody();
    unresolvedNotificationRulesResponse.setNotificationRules(toNotificationData(rulesNeedingConfiguration));
    return Response.ok().entity(unresolvedNotificationRulesResponse).build();
  }

  private List<StageExecutionResponseBody> getStageExecutionResponse(
      List<StageExecutionResponse> stageExecutionResponse) {
    List<StageExecutionResponseBody> stageExecResponse = new ArrayList<>();
    for (StageExecutionResponse stageRes : stageExecutionResponse) {
      StageExecutionResponseBody response = new StageExecutionResponseBody();
      response.setMessage(stageRes.getMessage());
      response.setStageIdentifier(stageRes.getStageIdentifier());
      response.setStageName(stageRes.getStageName());
      response.setStagesRequired(stageRes.getStagesRequired());
      response.setIsToBeBlocked(stageRes.isToBeBlocked());
      stageExecResponse.add(response);
    }
    return stageExecResponse;
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response rerunPipeline(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, String executionId, @Valid RerunPipelineRequest body,
      @AccountIdentifier String harnessAccount, String module, Boolean useFqnIfError, String notes, String branchName,
      String connectorRef, String repoName, Boolean useOriginalPipelineYaml) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().branch(branchName).build());
    String inputSetPipelineYaml = null;
    if (body != null) {
      inputSetPipelineYaml = PipelineInputsUtils.getInputsForPipeline(body.getInputsYaml(), body.getInputs());
    }
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(harnessAccount, org, project, pipeline, module,
            executionId, inputSetPipelineYaml, false, false, notes, useOriginalPipelineYaml, false, scopeInfo);
    PipelineExecuteResponseBody pipelineExecuteResponseBody =
        getPipelineExecutionResponseFromPlanExecutionResponse(planExecutionResponseDto);
    return Response.ok().entity(pipelineExecuteResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response rerunStagesExecutionOfPipeline(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, String executionId, @Valid RunStageRequestBody body,
      @AccountIdentifier String harnessAccount, String branchName, String connectorRef, String repoName,
      Boolean useFqnIfError, String module, String notes) {
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder().branch(branchName).build());
    RunStageRequestDTO runStageRequestDTO = getRunStageRequestDTO(body);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.rerunStagesWithRuntimeInputYaml(harnessAccount,
        org, project, pipeline, module, executionId, runStageRequestDTO, false, false, notes, false, scopeInfo);
    PipelineExecuteResponseBody pipelineExecuteResponseBody =
        getPipelineExecutionResponseFromPlanExecutionResponse(planExecutionResponseDto);
    return Response.ok().entity(pipelineExecuteResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response getRetryStages(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String pipeline, String executionId, String loadFromCache,
      @AccountIdentifier String accountId) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, org, project);
    return Response.ok()
        .entity(toRetryInfo(retryExecutionHelper.validateRetry(
            accountId, org, project, pipeline, executionId, loadFromCache, scopeInfo)))
        .build();
  }

  @Override
  @Timed
  @ResponseMetered
  public Response runDynamicExecutionWithInputYaml(String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, @Valid DynamicPipelineExecuteRequestBody body, String accountId, String moduleType,
      String notes, Boolean notifyOnlyUser) {
    String yaml = body == null ? null : body.getYaml();
    return dynamicExecutionHelper(orgIdentifier, projectIdentifier, pipelineIdentifier, yaml, accountId, moduleType,
        notes, notifyOnlyUser, false);
  }

  @Override
  @Timed
  @ApiOperation(
      hidden = true, value = "Execute Dynamic Execution Internal Api", nickname = "executeDynamicExecutionInternalApi")
  @ResponseMetered
  @Operation(operationId = "executeDynamicExecutionInternalApi", summary = "Execute Dynamic Execution Internal Api",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Execute Dynamic Execution Internal Api")
      })
  @Hidden()
  @InternalApi
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response
  runDynamicExecutionWithInputYamlInternal(@OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, @ResourceIdentifier String pipelineIdentifier,
      @Valid DynamicPipelineExecuteInternalRequestBody body, @AccountIdentifier String accountId, String moduleType,
      String notes, Boolean notifyOnlyUser) {
    String yaml = body == null ? null : body.getYaml();
    if (body == null || body.getPrincipal() == null) {
      return dynamicExecutionHelper(orgIdentifier, projectIdentifier, pipelineIdentifier, yaml, accountId, moduleType,
          notes, notifyOnlyUser, true);
    }
    io.harness.security.dto.Principal principal = toSecurityPrincipal(body.getPrincipal(), accountId);
    io.harness.security.dto.Principal previousPrincipal = SecurityContextBuilder.getPrincipal();
    io.harness.security.dto.Principal previousSourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    try {
      return dynamicExecutionHelper(orgIdentifier, projectIdentifier, pipelineIdentifier, yaml, accountId, moduleType,
          notes, notifyOnlyUser, true);
    } finally {
      SecurityContextBuilder.setContext(previousPrincipal);
      SourcePrincipalContextBuilder.setSourcePrincipal(previousSourcePrincipal);
    }
  }

  private io.harness.security.dto.Principal toSecurityPrincipal(
      io.harness.spec.server.pipeline.v1.model.Principal requestPrincipal, String accountId) {
    if (requestPrincipal.getPrincipalType() == null) {
      throw new InvalidRequestException("principal_type is required when principal is provided.");
    }
    String identifier = requestPrincipal.getPrincipalIdentifier();
    String uniqueId = requestPrincipal.getPrincipalUniqueId();
    switch (requestPrincipal.getPrincipalType()) {
      case USER:
        return new UserPrincipal(identifier, null, null, accountId, null, isEmpty(uniqueId) ? identifier : uniqueId);
      case SERVICE_ACCOUNT:
        return new ServiceAccountPrincipal(identifier, null, null, accountId, uniqueId);
      case SERVICE:
        return new ServicePrincipal(identifier);
      case API_KEY:
        return new ApiKeyPrincipal(identifier);
      default:
        throw new InvalidRequestException(
            String.format("Unsupported principal_type '%s'.", requestPrincipal.getPrincipalType()));
    }
  }

  private Response dynamicExecutionHelper(String orgIdentifier, String projectIdentifier, String pipelineIdentifier,
      String yaml, String accountId, String moduleType, String notes, Boolean notifyOnlyUser, boolean isInternalApi) {
    validateDynamicExecutionPathParams(orgIdentifier, projectIdentifier, pipelineIdentifier);
    if (isEmpty(yaml)) {
      throw new InvalidRequestException(
          "The input YAML to be executed cannot be null. Please provide a valid YAML to execute.");
    }
    try {
      ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);
      validateDynamicExecutionPermissions(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, scopeInfo);
      PlanExecutionResponseDto executionResponseDto =
          pipelineExecutor.startDynamicExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml,
              moduleType, false, notifyOnlyUser, notes, scopeInfo, isInternalApi);
      return Response.status(Response.Status.OK)
          .entity(getPipelineExecutionResponseFromPlanExecutionResponse(executionResponseDto))
          .build();
    } catch (InvalidRequestException ex) {
      throw ex;
    } catch (EntityNotFoundException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new InternalServerErrorException(
          format("Failed to execute dynamic pipeline [%s] in Organization [%s], Project [%s]. %s", pipelineIdentifier,
              orgIdentifier, projectIdentifier, ex.getMessage()),
          ex);
    }
  }

  @Override
  @Timed
  @ResponseMetered
  @Hidden
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response runDirectExecutionWithInputYaml(@OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier, @ResourceIdentifier String pipelineIdentifier,
      @Valid DirectPipelineExecuteRequestBody body, @AccountIdentifier String accountId, String moduleType,
      String notes, Boolean notifyOnlyUser) {
    if (body == null || isEmpty(body.getYaml())) {
      throw new InvalidRequestException(
          "The input YAML to be executed cannot be null. Please provide a valid YAML to execute.");
    }
    try {
      ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);
      validateDirectExecutionPermissions(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
      PlanExecutionResponseDto executionResponseDto =
          pipelineExecutor.startDirectExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
              body.getYaml(), body.getInputsYaml(), moduleType, false, notifyOnlyUser, notes, scopeInfo);
      return Response.status(Response.Status.OK)
          .entity(getPipelineExecutionResponseFromPlanExecutionResponse(executionResponseDto))
          .build();
    } catch (InvalidRequestException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new InternalServerErrorException(
          String.format("%s. %s", PipelineException.PIPELINE_Execution_MESSAGE, ex.getMessage()), ex);
    }
  }

  @Override
  public Response validateNotificationRulesWithUnresolvedInputs(@Valid NotificationTemplateReconcileRequestBody body,
      String org, String project, String pipeline, String harnessAccount) {
    if (body == null || isEmpty(body.getYaml())) {
      throw new InvalidRequestException(
          "The pipeline YAML file cannot be null. Please provide a valid YAML configuration.");
    }
    PipelineNotificationTemplateValidateResponseBody pipelineNotificationTemplateValidateResponse =
        new PipelineNotificationTemplateValidateResponseBody();
    pipelineNotificationTemplateValidateResponse.setIsInvalid(
        notificationHelper.validateNotificationRulesWithUnresolvedInputs(body.getYaml()));
    return Response.ok().entity(pipelineNotificationTemplateValidateResponse).build();
  }

  private void validateDynamicExecutionPermissions(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, ScopeInfo scopeInfo) {
    // access control checks: Users with both EDIT and EXECUTE Permissions are allowed to run this api.
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("PIPELINE", pipelineIdentifier), PipelineRbacPermissions.PIPELINE_EXECUTE);
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineIdentifier,
        pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
  }

  private void validateDirectExecutionPermissions(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    if (!pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_DIRECT_PIPELINES_EXECUTION)) {
      throw new InvalidRequestException("Direct execution feature is not enabled for the account.");
    }
    pipelineSplitPermissionsHelper.checkForPipelineRBACSplitAccessPermissions(accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineIdentifier,
        pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.PIPE_CREATE_EDIT_PERMISSION_SPLIT),
        PipelineRbacPermissions.PIPELINE_EDIT,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EDIT, PipelineRbacPermissions.PIPELINE_CREATE));
  }

  private void validateDynamicExecutionPathParams(
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    if (isEmpty(orgIdentifier)) {
      throw new InvalidRequestException(
          "Organization identifier is missing in the request. Please provide a valid org identifier in the URL.");
    }
    if (isEmpty(projectIdentifier)) {
      throw new InvalidRequestException(
          "Project identifier is missing in the request. Please provide a valid project identifier in the URL.");
    }
    if (isEmpty(pipelineIdentifier)) {
      throw new InvalidRequestException(
          "Pipeline identifier is missing in the request. Please provide a valid pipeline identifier in the URL.");
    }
  }

  private List<NotificationRulesData> toNotificationData(ArrayList<Map<String, String>> notificationList) {
    List<NotificationRulesData> notificationRules = new ArrayList<>();
    if (isNotEmpty(notificationList)) {
      for (Map<String, String> notificationRule : notificationList) {
        NotificationRulesData notificationData = new NotificationRulesData();
        notificationData.setNotificationRuleId(notificationRule.values().stream().findFirst().orElse(null));
        notificationRules.add(notificationData);
      }
    }
    return notificationRules;
  }

  private io.harness.spec.server.pipeline.v1.model.RetryHistoryResponseDto toRetryHistoryResponseDto(
      RetryHistoryResponseDto responseDto) {
    io.harness.spec.server.pipeline.v1.model.RetryHistoryResponseDto retryHistoryResponseDto =
        new io.harness.spec.server.pipeline.v1.model.RetryHistoryResponseDto();
    retryHistoryResponseDto.setLatestExecutionId(responseDto.getLatestExecutionId());
    retryHistoryResponseDto.setErrorMessage(responseDto.getErrorMessage());
    retryHistoryResponseDto.setExecutionInfos(
        responseDto.getExecutionInfos().stream().map(this::toExecutionInfo).toList());
    retryHistoryResponseDto.setRetryStagesMetadata(toRetryStagesMetadata(responseDto.getRetryStagesMetadata()));
    return retryHistoryResponseDto;
  }

  private io.harness.spec.server.pipeline.v1.model.ExecutionInfo toExecutionInfo(ExecutionInfo info) {
    io.harness.spec.server.pipeline.v1.model.ExecutionInfo executionInfo =
        new io.harness.spec.server.pipeline.v1.model.ExecutionInfo();
    executionInfo.setUuid(info.getUuid());
    executionInfo.setStarted(info.getStartTs());
    executionInfo.setEnded(info.getEndTs());
    executionInfo.setStatus(PipelineExecutionDetailsApiUtils.toExecutionStatusV1(info.getStatus()));
    executionInfo.setRunSequence(info.getRunSequence());
    return executionInfo;
  }

  private io.harness.spec.server.pipeline.v1.model.RetryStagesMetadataDTO toRetryStagesMetadata(
      RetryStagesMetadataDTO metadata) {
    io.harness.spec.server.pipeline.v1.model.RetryStagesMetadataDTO retryStagesMetadataDTO =
        new io.harness.spec.server.pipeline.v1.model.RetryStagesMetadataDTO();
    retryStagesMetadataDTO.setRetryStagesIdentifier(metadata.getRetryStagesIdentifier());
    retryStagesMetadataDTO.setSkipStagesIdentifier(metadata.getSkipStagesIdentifier());
    return retryStagesMetadataDTO;
  }

  private io.harness.spec.server.pipeline.v1.model.RetryLatestExecutionResponseDto toRetryLatestExecutionResponseDto(
      RetryLatestExecutionResponseDto responseDto) {
    io.harness.spec.server.pipeline.v1.model.RetryLatestExecutionResponseDto retryLatestExecutionResponseDto =
        new io.harness.spec.server.pipeline.v1.model.RetryLatestExecutionResponseDto();
    retryLatestExecutionResponseDto.setLatestExecutionId(responseDto.getLatestExecutionId());
    retryLatestExecutionResponseDto.setErrorMessage(responseDto.getErrorMessage());
    return retryLatestExecutionResponseDto;
  }

  private io.harness.spec.server.pipeline.v1.model.RetryInfo toRetryInfo(RetryInfo retryInfo) {
    io.harness.spec.server.pipeline.v1.model.RetryInfo retryInfo1 =
        new io.harness.spec.server.pipeline.v1.model.RetryInfo();
    retryInfo1.setIsResumable(retryInfo.isResumable());
    retryInfo1.setErrorMessage(retryInfo.getErrorMessage());
    retryInfo1.setGroups(retryInfo.getGroups().stream().map(this::toRetryGroup).collect(Collectors.toList()));
    return retryInfo1;
  }

  private io.harness.spec.server.pipeline.v1.model.RetryGroup toRetryGroup(RetryGroup retryGroup) {
    io.harness.spec.server.pipeline.v1.model.RetryGroup retryGroup1 =
        new io.harness.spec.server.pipeline.v1.model.RetryGroup();
    retryGroup1.setInfo(retryGroup.getInfo().stream().map(this::toRetryStageInfo).collect(Collectors.toList()));
    return retryGroup1;
  }

  private io.harness.spec.server.pipeline.v1.model.RetryStageInfo toRetryStageInfo(RetryStageInfo retryStageInfo) {
    io.harness.spec.server.pipeline.v1.model.RetryStageInfo retryStageInfo1 =
        new io.harness.spec.server.pipeline.v1.model.RetryStageInfo();
    retryStageInfo1.setName(retryStageInfo.getName());
    retryStageInfo1.setId(retryStageInfo.getIdentifier());
    retryStageInfo1.setStatus(retryStageInfo.getStatus().getDisplayName());
    retryStageInfo1.setCreatedAt(retryStageInfo.getCreatedAt());
    retryStageInfo1.setParentId(retryStageInfo.getParentId());
    retryStageInfo1.setNextId(retryStageInfo.getNextId());
    return retryStageInfo1;
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public Response retryPipelineWithInputsetPipelineYaml(String org, String project, String pipeline, String executionId,
      @Valid RetryPipelineRequest body, @AccountIdentifier String harnessAccount, String module,
      List<String> retryStages, Boolean runAllStages, String notes) {
    if (retryStages.size() == 0) {
      throw new InvalidRequestException("You need to select the stage to retry!!");
    }
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(harnessAccount, org, project);

    RetryInfo retryInfo =
        retryExecutionHelper.validateRetry(harnessAccount, org, project, pipeline, executionId, null, scopeInfo);

    if (!retryInfo.isResumable()) {
      throw new InvalidRequestException(retryInfo.getErrorMessage());
    }
    String inputSetPipelineYaml = null;
    Map<String, String> expressionValues = null;
    if (body != null) {
      inputSetPipelineYaml = PipelineInputsUtils.getInputsForPipeline(body.getInputsYaml(), body.getInputs());
      expressionValues = isNotEmpty(body.getExpressionValues()) ? body.getExpressionValues() : null;
    }
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.retryPipelineWithInputSetPipelineYaml(
        harnessAccount, org, project, pipeline, module, inputSetPipelineYaml, executionId, retryStages, runAllStages,
        false, false, notes, false, scopeInfo, expressionValues);
    PipelineExecuteResponseBody pipelineExecuteResponseBody =
        getPipelineExecutionResponseFromPlanExecutionResponse(planExecutionResponseDto);
    return Response.ok().entity(pipelineExecuteResponseBody).build();
  }

  PipelineExecuteResponseBody getPipelineExecutionResponseFromPlanExecutionResponse(
      PlanExecutionResponseDto planExecutionResponseDto) {
    PipelineExecuteResponseBody pipelineExecuteResponseBody = new PipelineExecuteResponseBody();
    ExecutionDetails executionDetails = new ExecutionDetails();
    executionDetails.setExecutionId(planExecutionResponseDto.getPlanExecution().getUuid());
    executionDetails.setStatus(planExecutionResponseDto.getPlanExecution().getStatus().toString());
    pipelineExecuteResponseBody.setExecutionDetails(executionDetails);
    return pipelineExecuteResponseBody;
  }
}
