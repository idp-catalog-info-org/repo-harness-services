/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.resource;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
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
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.apiexamples.PipelineAPIConstants;
import io.harness.beans.ExecutionGraph;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dto.SimplifiedOrchestrationGraphDTO;
import io.harness.engine.executions.gitmetadata.service.PipelineExecutionGitMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.exception.InvalidRequestException;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateInputsErrorResponseDTO;
import io.harness.pipeline.service.PipelineServiceConfiguration;
import io.harness.pms.annotations.AnnotationContentResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PipelineExpressionHelper;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlWithTemplateDTO;
import io.harness.pms.pipeline.PMSPipelineListBranchesResponse;
import io.harness.pms.pipeline.PMSPipelineListRepoResponse;
import io.harness.pms.pipeline.PipelineExecutionNotesDTO;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.pipeline.annotations.PipelineAnnotationsResponseDTO;
import io.harness.pms.pipeline.annotations.PipelineAnnotationsService;
import io.harness.pms.pipeline.mappers.ExecutionGraphMapper;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.plan.execution.PlanExecutionResourceConstants;
import io.harness.pms.plan.execution.PmsExecutionSummaryDtoUpdateHelper;
import io.harness.pms.plan.execution.RetryExecutionHelper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.beans.dto.CustomPage;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionMetaDataResponseDetailsDTO;
import io.harness.pms.plan.execution.beans.dto.ExpressionEvaluationDetailDTO;
import io.harness.pms.plan.execution.beans.dto.NodeExecutionSubGraphResponse;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionIdentifierSummaryDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineFilterDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO;
import io.harness.pms.plan.execution.helper.ExecutionHelper;
import io.harness.pms.plan.execution.service.ExecutionGraphService;
import io.harness.pms.plan.execution.service.ExpressionEvaluatorService;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.search.service.PipelineSearchService;
import io.harness.utils.PageUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.utils.execution.ExecutionModeUtils;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Api("pipelines/execution")
@Path("pipelines/execution")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error"),
          @ApiResponse(code = 403, response = TemplateInputsErrorResponseDTO.class,
              message = "TemplateRefs Resolved failed in pipeline yaml.")
    })
@Tag(name = "Pipeline Execution Details", description = "This contains APIs for fetching Pipeline Execution Details")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@PipelineServiceAuth
@Slf4j
public class ExecutionDetailsResource {
  @Inject private final PMSExecutionService pmsExecutionService;
  @Inject private final AccessControlClient accessControlClient;
  @Inject private final PmsGitSyncHelper pmsGitSyncHelper;
  @Inject private final ExecutionHelper executionHelper;
  @Inject private final ExecutionGraphService executionGraphService;
  @Inject private final ExpressionEvaluatorService expressionEvaluatorService;
  @Inject private final PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private final PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject private final PipelineExpressionHelper pipelineExpressionHelper;
  @Inject private final RetryExecutionHelper retryExecutionHelper;
  @Inject private final PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private final PipelineExecutionGitMetadataService executionGitMetadataService;
  @Inject private final PipelineAnnotationsService pipelineAnnotationsService;
  @Inject PipelineSearchService pipelineSearchService;
  @Inject PipelineServiceConfiguration pipelineServiceConfiguration;
  @Inject PmsExecutionSummaryDtoUpdateHelper pmsExecutionSummaryDtoUpdateHelper;
  @Inject ScopeResolutionHelper scopeResolutionHelper;

  private final String INVALID_PAGE_REQUEST_EXCEPTION_MESSAGE =
      "Please Verify Executions list parameters for page and size, page should be >= 0 and size should be > 0 and "
      + "<=1000";
  private final String PIPELINE_RESOURCE_TYPE = "PIPELINE";
  private final String PERMISSION_MISSING_MESSAGE = "Missing permission %s on %s";
  private static final int MAX_ALLOWED_EXECUTION_IDS_TOTAL = 1000;
  private static final int MAX_ALLOWED_EXECUTIONS_PER_PAGE = 1000;
  private static final List<String> SORT_FIELDS_FOR_SUMMARY = Arrays.asList(PlanExecutionSummaryKeys.name,
      PlanExecutionSummaryKeys.status, PlanExecutionSummaryKeys.startTs, PlanExecutionSummaryKeys.pipelineTimeoutTs);

  private final String INVALID_SORT_REQUEST_EXCEPTION_MESSAGE =
      "Please Verify Executions list parameters for sort criteria, it should be in format <Field>%2C<SortOrder>, where "
      + "<SortOrder> can be one of 'ASC' or 'DESC' and <Field> can be any one of " + SORT_FIELDS_FOR_SUMMARY;

  private final String INVALID_PAGE_REQUEST_POLICY_EVALUATION_MESSAGE =
      "Please Verify Policy Evaluation list parameters for page and size, page should be >= 0 and size should be > 0 "
      + "and "
      + "<=%d";

  @POST
  @Path("/summary")
  @ApiOperation(value = "Gets Executions list", nickname = "getListOfExecutions")
  @Operation(operationId = "getListOfExecutions",
      description = "Returns a List of Pipeline Executions with Specific Filter", summary = "List Executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all the Executions of pipelines for given filter")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<Page<PipelineExecutionSummaryDTO>>
  getListOfExecutions(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_SEARCH_TERM_PARAM_MESSAGE) @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("10") int size,
      @Parameter(description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) @QueryParam("sort") List<String> sort,
      @Parameter(description = "Identifier of a saved filter to apply. Cannot be used together with the request body"
              + " filter properties. If both are provided, the saved filter takes precedence.")
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @Parameter(description = "Whether to show all executions accessible to the user, not just those in the"
              + " specified project. Defaults to false.") @DefaultValue("false")
      @QueryParam(NGResourceFilterConstants.SHOW_ALL_EXECUTONS) boolean showAllExecutions,
      @Parameter(description = "Harness module which triggered the execution. Examples: CD, CI, CE, etc.") @QueryParam(
          "module") String moduleName,
      @RequestBody(
          description = "Filter properties for listing pipeline executions. The `filterType` field is required and must"
              + " be set to `PipelineExecution`. The `branchName` field in this body filters executions by the"
              + " codebase/repository branch used during execution. This is different from the `branch` query"
              + " parameter (from Git Experience) which filters by the Git branch where the pipeline YAML"
              + " definition is stored. If both are provided, they filter independently.",
          content =
          {
            @Content(mediaType = "application/json",
                schema = @Schema(implementation = PipelineExecutionFilterPropertiesDTO.class),
                examples = @ExampleObject(name = "List", summary = "Sample List Pipeline Executions",
                    value = PipelineAPIConstants.LIST_EXECUTIONS,
                    description = "Sample List Pipeline Executions JSON Payload"))
          }) @Valid FilterPropertiesDTO filterProperties,
      @Parameter(
          description = "Filter by execution status. Accepts multiple values. Valid values: Running, AsyncWaiting,"
              + " TaskWaiting, TimedWaiting, Failed, Errored, IgnoreFailed, NotStarted, Expired, Aborted,"
              + " Discontinuing, Queued, Paused, ResourceWaiting, InterventionWaiting, ApprovalWaiting,"
              + " WaitStepRunning, QueuedLicenseLimitReached, QueuedExecutionConcurrencyReached, Success,"
              + " Suspended, Skipped, Pausing, ApprovalRejected, InputWaiting, AbortedByFreeze, UploadWaiting,"
              + " QueuedGlobalInfraCapacityReached, QUEUED_PLAN_CREATION") @QueryParam("status")
      List<ExecutionStatus> statusesList,
      @Parameter(description = "Whether to filter executions triggered by the current user only") @QueryParam(
          "myDeployments") boolean myDeployments,
      @Parameter(description = "Git Experience parameters. The `branch` query parameter here refers to the Git branch"
              + " where the pipeline YAML definition is stored (for remote/Git-backed pipelines). This is"
              + " different from the `branchName` field in the request body, which filters by the"
              + " codebase/repository branch used during execution.")
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    Pageable pageRequest;
    if (page < 0 || !(size > 0 && size <= 1000)) {
      throw new InvalidRequestException(INVALID_PAGE_REQUEST_EXCEPTION_MESSAGE);
    }
    String sortProperty = EmptyPredicate.isEmpty(sort) ? PlanExecutionSummaryKeys.startTs : sort.get(0).split(",")[0];
    if (EmptyPredicate.isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    if (!isSortPropertyValid(pageRequest)) {
      throw new InvalidRequestException(INVALID_SORT_REQUEST_EXCEPTION_MESSAGE);
    }

    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId);

    boolean shouldPopulateNotes =
        !pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_DISABLE_NOTES_IN_EXECUTION_LISTING);

    Page<PipelineExecutionSummaryDTO> planExecutionSummaryDTOS;
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_ELASTIC_SEARCH)) {
      Query query = pmsExecutionService.formQueryForSearch(accountId, orgId, projectId, pipelineIdentifier,
          filterIdentifier, (PipelineExecutionFilterPropertiesDTO) filterProperties, moduleName, searchTerm,
          statusesList, myDeployments, scopeInfo);
      Page<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
          pmsExecutionService.listExecutionsFromElastic(accountId, pageRequest, query, null);
      planExecutionSummaryDTOS = pipelineExecutionSummaryEntities.map(e
          -> PipelineExecutionSummaryDtoMapper.toDto(e,
              e.getEntityGitDetails() != null
                  ? e.getEntityGitDetails()
                  : pmsGitSyncHelper.getEntityGitDetailsFromBytes(e.getGitSyncBranchContext()),
              false, null, pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(e), scopeInfo, shouldPopulateNotes));
    } else {
      Criteria criteria = pmsExecutionService.formCriteria(accountId, orgId, projectId, pipelineIdentifier,
          filterIdentifier, (PipelineExecutionFilterPropertiesDTO) filterProperties, moduleName, searchTerm,
          statusesList, myDeployments, false, showAllExecutions, scopeInfo);
      // NOTE: We are getting entity git details from git context and not pipeline entity as we'll have to make DB calls
      // to fetch them and each might have a different branch context, so we cannot even batch them. The only data
      // missing because of this approach is objectId which UI doesn't use.
      planExecutionSummaryDTOS =
          pmsExecutionService.getPipelineExecutionSummaryEntity(criteria, pageRequest, accountId, sortProperty)
              .map(e
                  -> PipelineExecutionSummaryDtoMapper.toDto(e,
                      e.getEntityGitDetails() != null
                          ? e.getEntityGitDetails()
                          : pmsGitSyncHelper.getEntityGitDetailsFromBytes(e.getGitSyncBranchContext()),
                      false, null, pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(e), scopeInfo,
                      shouldPopulateNotes));
    }
    // we are not using the showRetryHistory on list page, so we are setting it false by default

    return ResponseDTO.newResponse(planExecutionSummaryDTOS);
  }

  private boolean isSortPropertyValid(Pageable pageRequest) {
    if (pageRequest.getSort().isUnsorted()) {
      return true;
    }
    for (Sort.Order order : pageRequest.getSort()) {
      if (!SORT_FIELDS_FOR_SUMMARY.contains(order.getProperty())) {
        return false;
      }
    }
    return true;
  }

  @GET
  @Path("/canRetry/{planExecutionId}")
  @ApiOperation(value = "Validates if an execution can be retried", nickname = "canRetryExecution")
  @Operation(operationId = "canRetryExecution",
      description = "Validates if an execution can be retried for a Given PlanExecution ID",
      summary = "Validate if Execution can be retried",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return true if the provided execution can be retried")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean>
  canRetryExecution(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                    @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Plan Execution Id for which we want to check if it can be retried",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.fetchExecutionSummary(accountId, planExecutionId, false);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(PIPELINE_RESOURCE_TYPE, executionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);

    Boolean isLatestExecution = retryExecutionHelper.isLatestExecution(executionSummaryEntity);
    Boolean canRetry =
        !ExecutionModeUtils.isRollbackMode(executionSummaryEntity.getExecutionMode()) && isLatestExecution;
    return ResponseDTO.newResponse(canRetry);
  }

  @POST
  @Path("/executionSummary")
  @ApiOperation(value = "Gets Executions Id list", nickname = "getListOfExecutionIdentifier")
  @Operation(operationId = "getListOfExecutionIdentifier",
      description = "Returns a List of Pipeline Executions Identifier with Specific Filter",
      summary = "List Execution Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all the Executions Identifier of pipelines for given filter")
      })
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = PIPELINE_RESOURCE_TYPE, permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<Page<PipelineExecutionIdentifierSummaryDTO>>
  getListOfExecutionIdentifier(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,

      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE)
      @DefaultValue("10") int size, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @RequestBody(description = "Returns a List of Pipeline Executions with Specific Filters", content = {
        @Content(mediaType = "application/json",
            examples = @ExampleObject(name = "List", summary = "Sample List Pipeline Executions",
                value = PipelineAPIConstants.LIST_EXECUTIONS,
                description = "Sample List Pipeline Executions JSON Payload"))
      }) @Valid FilterPropertiesDTO filterProperties) {
    Pageable pageRequest;
    if (page < 0 || !(size > 0 && size <= 1000)) {
      throw new InvalidRequestException(INVALID_PAGE_REQUEST_EXCEPTION_MESSAGE);
    }

    pageRequest = PageRequest.of(page, size, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));

    List<String> projections =
        Arrays.asList(PlanExecutionSummaryKeys.planExecutionId, PlanExecutionSummaryKeys.runSequence,
            PlanExecutionSummaryKeys.orgIdentifier, PlanExecutionSummaryKeys.pipelineIdentifier,
            PlanExecutionSummaryKeys.projectIdentifier, PlanExecutionSummaryKeys.status);

    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId);

    Page<PipelineExecutionIdentifierSummaryDTO> planExecutionSummaryDTOS;
    if (pmsFeatureFlagService.isEnabled(accountId, FeatureName.PIPE_ENABLE_ELASTIC_SEARCH)) {
      Query query = pmsExecutionService.formQueryForSearch(accountId, orgId, projectId, pipelineIdentifier,
          filterIdentifier, (PipelineExecutionFilterPropertiesDTO) filterProperties, null, null,
          ExecutionStatus.getListExecutionStatus(StatusUtils.finalStatuses()), false, scopeInfo);
      Page<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntities =
          pmsExecutionService.listExecutionsFromElastic(accountId, pageRequest, query, projections);
      planExecutionSummaryDTOS = pipelineExecutionSummaryEntities.map(
          entity -> PipelineExecutionSummaryDtoMapper.toExecutionIdentifierDto(entity, scopeInfo));

    } else {
      Criteria criteria = pmsExecutionService.formCriteria(accountId, orgId, projectId, pipelineIdentifier,
          filterIdentifier, (PipelineExecutionFilterPropertiesDTO) filterProperties, null, null,
          ExecutionStatus.getListExecutionStatus(StatusUtils.finalStatuses()), false, false, true, scopeInfo);
      planExecutionSummaryDTOS =
          pmsExecutionService.getPipelineExecutionSummaryEntityWithProjection(criteria, pageRequest, projections)
              .map(entity -> PipelineExecutionSummaryDtoMapper.toExecutionIdentifierDto(entity, scopeInfo));
    }

    return ResponseDTO.newResponse(planExecutionSummaryDTOS);
  }

  // This API is used only for internal purpose currently to support IDP plugin to fetch the executions based on
  // Parametrised Operator on modules in filterProperties. This API only supports multiple accountId,orgId,
  // projectId,pipelineIdentifier (As list to support multiple pipeline identifiers) and filterProperties as filter
  // criteria to obtain the executions.
  @POST
  @Path("/v2/summary")
  @ApiModelProperty(hidden = true)
  @Hidden
  @ApiOperation(value = "Gets Executions list for multiple pipeline filters with OR operator",
      nickname = "getListOfExecutionsForMultiplePipelinesIdentifiersWithOrOperators")
  @Operation(operationId = "getListOfExecutionsForMultiplePipelinesIdentifiersWithOrOperators",
      description = "Returns a List of Pipeline Executions with Specific Filters", summary = "List Executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns all the Executions of pipelines for given filters")
      })
  @NGAccessControlCheck(resourceType = PIPELINE_RESOURCE_TYPE, permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<Page<PipelineExecutionSummaryDTO>>
  getListOfExecutionsWithOrOperator(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @Size(max = 20) List<String> pipelineIdentifier,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE)
      @DefaultValue("10") int size, @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @RequestBody(description = "Returns a List of Pipeline Executions with Specific Filters", content = {
        @Content(mediaType = "application/json",
            examples = @ExampleObject(name = "List", summary = "Sample List Pipeline Executions",
                value = PipelineAPIConstants.LIST_EXECUTIONS,
                description = "Sample List Pipeline Executions JSON Payload"))
      }) @Valid FilterPropertiesDTO filterProperties) {
    Pageable pageRequest;
    if (page < 0 || !(size > 0 && size <= 1000)) {
      throw new InvalidRequestException(INVALID_PAGE_REQUEST_EXCEPTION_MESSAGE);
    }
    pageRequest = PageRequest.of(page, size, Sort.by(Direction.DESC, PlanExecutionSummaryKeys.startTs));

    // NOTE: We are getting entity git details from git context and not pipeline entity as we'll have to make DB calls
    // to fetch them and each might have a different branch context, so we cannot even batch them. The only data
    // missing because of this approach is objectId which UI doesn't use.
    // we are not using the showRetryHistory on list page, so we are setting it false by default

    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId);

    return ResponseDTO.newResponse(
        pmsExecutionService
            .getPipelineExecutionSummaryEntity(accountId, orgId, projectId, pipelineIdentifier, filterIdentifier,
                (PipelineExecutionFilterPropertiesDTO) filterProperties, pageRequest, scopeInfo)
            .map(e
                -> PipelineExecutionSummaryDtoMapper.toDto(e,
                    e.getEntityGitDetails() != null
                        ? e.getEntityGitDetails()
                        : pmsGitSyncHelper.getEntityGitDetailsFromBytes(e.getGitSyncBranchContext()),
                    false, null, pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(e), scopeInfo)));
  }

  @GET
  @Path("/v2/{planExecutionId}")
  @ApiOperation(value = "Gets Execution Detail V2", nickname = "getExecutionDetailV2")
  @Operation(operationId = "getExecutionDetailV2",
      description = "Returns the Pipeline Execution Details for a Given PlanExecution ID",
      summary = "Fetch Execution Details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Return the Pipeline Execution details for given PlanExecution Id without full graph if "
                + "stageNodeId is null")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PipelineExecutionDetailDTO>
  getExecutionDetailV2(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_ID_PARAM_MESSAGE) @QueryParam(
          "stageNodeId") String stageNodeId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_EXECUTION_PARAM_MESSAGE) @QueryParam(
          "stageNodeExecutionId") String stageNodeExecutionId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_EXECUTION_PARAM_MESSAGE) @QueryParam(
          "childStageNodeId") String childStageNodeId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_EXECUTION_PARAM_MESSAGE) @QueryParam(
          "childStageNodeExecutionId") String childStageNodeExecutionId,
      @Parameter(description = PipelineResourceConstants.GENERATE_FULL_GRAPH_PARAM_MESSAGE) @QueryParam(
          "renderFullBottomGraph") Boolean renderFullBottomGraph,
      @Parameter(description = "Plan Execution Id for which we want to get the Execution details",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.fetchExecutionSummary(accountId, planExecutionId, false);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(PIPELINE_RESOURCE_TYPE, executionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);

    EntityGitDetails entityGitDetails;
    if (executionSummaryEntity.getEntityGitDetails() == null) {
      entityGitDetails =
          pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntity.getGitSyncBranchContext());
    } else {
      entityGitDetails = executionSummaryEntity.getEntityGitDetails();
    }

    PipelineExecutionDetailDTO executionDetailDTO = executionHelper.getResponseDTO(stageNodeId, stageNodeExecutionId,
        childStageNodeId, renderFullBottomGraph, executionSummaryEntity, entityGitDetails, childStageNodeExecutionId);

    return ResponseDTO.newResponse(executionDetailDTO);
  }
  @GET
  @Path("/getExecutionGraph/{planExecutionId}")
  @ApiOperation(value = "Gets Execution Graph", nickname = "getExecutionGraph")
  @Operation(operationId = "getExecutionGraph",
      description = "Returns the Pipeline Execution Graph for a Given PlanExecution ID",
      summary = "Fetch Execution Graph",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Return the Pipeline Execution graph for given PlanExecution Id")
      })
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<ExecutionGraph>
  getExecutionGraph(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                    @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Plan Execution Id for which we want to get the Execution details",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.fetchExecutionSummary(accountId, planExecutionId, false);
    ExecutionGraph executionGraph = executionHelper.getExecutionGraph(executionSummaryEntity);
    return ResponseDTO.newResponse(executionGraph);
  }

  /* This API is for internal use case of IDP team for getting all the output variables along with value.
  Response for this API is limited as compare to already existing V2 API for better performance.
  * */

  @GET
  @Path("/internal/{planExecutionId}")
  @ApiOperation(value = "Gets Execution Detail Internal", nickname = "getExecutionDetailInternal")
  @Hidden
  @Operation(operationId = "getExecutionDetailInternal",
      description = "Returns the Simplified Pipeline Execution Details for a Given PlanExecution ID",
      summary = "Fetch Execution Details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Return the Simplified Pipeline Execution details for given PlanExecution Id without full "
                + "graph if stageNodeId is null")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<SimplifiedOrchestrationGraphDTO>
  getExecutionDetailInternal(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Plan Execution Id for which we want to get the Execution details",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    String pipelineIdentifier = pmsExecutionService.getPipelineIdentifier(accountId, planExecutionId);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(PIPELINE_RESOURCE_TYPE, pipelineIdentifier), PipelineRbacPermissions.PIPELINE_VIEW);

    return ResponseDTO.newResponse(pmsExecutionService.getSimplifiedOrchestrationGraph(accountId, planExecutionId));
  }

  @POST
  @Path("/url")
  @ApiOperation(value = "Gets Execution URL", nickname = "getExecutionURL")
  @Operation(operationId = "getExecutionURL",
      description = "Returns the Pipeline Execution Url for a Given PlanExecution ID", summary = "Fetch Execution Url",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Return the Pipeline Execution url for given Pipeline and PlanExecution Id")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<String>
  getExecutionURL(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                  @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Pipeline Id for which we want to get the Execution url", required = true) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineId,
      @Parameter(description = "Plan Execution Id for which we want to get the Execution url",
          required = true) @QueryParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId,
      @Parameter(description = "Modules") @QueryParam(NGCommonEntityConstants.MODULES) List<String> modules) {
    return ResponseDTO.newResponse(
        pipelineExpressionHelper.generateUrl(accountId, orgId, projectId, pipelineId, planExecutionId, modules));
  }

  @GET
  @Path("/subGraph/{planExecutionId}/{nodeExecutionId}")
  @ApiOperation(value = "Gets Execution SubGraph for retried stepGroup nodeExecutionId",
      nickname = "getExecutionSubGraphForNodeExecution")
  @Operation(operationId = "getExecutionSubGraphForNodeExecution",
      description = "Returns the Pipeline Execution SubGraph for a Given Retried StepGroup NodeExecution ID",
      summary = "Fetch Execution SubGraph for a Given Retried StepGroup NodeExecution ID",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Return Execution subGraph for a Given Retried StepGroup NodeExecution ID")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<NodeExecutionSubGraphResponse>
  getExecutionSubGraphForNodeExecution(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Node Execution Id for which we want to get the Execution SubGraph",
          required = true) @PathParam(NGCommonEntityConstants.NODE_KEY) String nodeExecutionId,
      @Parameter(description = "Plan Execution Id for which we want to get the Execution details",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, planExecutionId, false);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(PIPELINE_RESOURCE_TYPE, executionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);
    NodeExecutionSubGraphResponse nodeExecutionSubGraph = executionGraphService.getNodeExecutionSubGraph(
        nodeExecutionId, planExecutionId, accountId, executionSummaryEntity.getStartTs());
    return ResponseDTO.newResponse(nodeExecutionSubGraph);
  }

  @POST
  @Path("/{planExecutionId}/evaluateExpression")
  @ApiOperation(value = "Gets Execution Expression evaluated", nickname = "getExpressionEvaluated")
  @Operation(operationId = "getExpressionEvaluated", description = "Returns the Map of evaluated Expression",
      summary = "Gets Execution Expression evaluated",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Map of evaluated Expression")
      })
  @Hidden
  @NGAccessControlCheck(resourceType = PIPELINE_RESOURCE_TYPE, permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Timed
  @ResponseMetered
  public ResponseDTO<ExpressionEvaluationDetailDTO>
  getExpressionEvaluated(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @NotNull @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true)
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Parameter(description = "Plan Execution Id for which Expression have to be evaluated",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull String yaml) {
    return ResponseDTO.newResponse(expressionEvaluatorService.evaluateExpression(planExecutionId, yaml));
  }

  @GET
  @Path("/{planExecutionId}")
  @ApiOperation(value = "Gets Execution Detail", nickname = "getExecutionDetail")
  @Operation(operationId = "getExecutionDetail",
      description = "Returns the Pipeline Execution Details for a Given PlanExecution ID",
      summary = "Fetch Execution Details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Return the Pipeline Execution details for given PlanExecution Id")
      },
      deprecated = true)
  @Timed
  @ResponseMetered
  @Deprecated
  public ResponseDTO<PipelineExecutionDetailDTO>
  getExecutionDetail(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                     @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_ID_PARAM_MESSAGE) @QueryParam(
          "stageNodeId") String stageNodeId,
      @Parameter(description = PipelineResourceConstants.STAGE_NODE_EXECUTION_PARAM_MESSAGE) @QueryParam(
          "stageNodeExecutionId") String stageNodeExecutionId,
      @Parameter(description = "Plan Execution Id for which we want to get the Execution details",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgId, projectId);
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.fetchExecutionSummary(accountId, planExecutionId, false);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(PIPELINE_RESOURCE_TYPE, executionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW);

    EntityGitDetails entityGitDetails;
    if (executionSummaryEntity.getEntityGitDetails() == null) {
      entityGitDetails =
          pmsGitSyncHelper.getEntityGitDetailsFromBytes(executionSummaryEntity.getGitSyncBranchContext());
    } else {
      entityGitDetails = executionSummaryEntity.getEntityGitDetails();
    }

    return ResponseDTO.newResponse(
        PipelineExecutionDetailDTO.builder()
            .pipelineExecutionSummary(PipelineExecutionSummaryDtoMapper.toDto(executionSummaryEntity, entityGitDetails,
                retryExecutionHelper.shouldShowRetryHistory(executionSummaryEntity),
                retryExecutionHelper.isLatestExecution(executionSummaryEntity),
                pmsExecutionSummaryDtoUpdateHelper.getQueuedReason(executionSummaryEntity), scopeInfo))
            .executionGraph(
                ExecutionGraphMapper.toExecutionGraph(pmsExecutionService.getOrchestrationGraph(accountId, stageNodeId,
                                                          planExecutionId, stageNodeExecutionId),
                    executionSummaryEntity, scopeInfo))
            .build());
  }

  @GET
  @Path("/{planExecutionId}/metadata")
  @ApiOperation(value = "Get metadata of an execution", nickname = "getExecutionData")
  @Operation(operationId = "getExecutionData", summary = "Get execution metadata of a pipeline execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns metadata of a execution")
      })
  public ResponseDTO<ExecutionDataResponseDTO>
  getExecutions(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "ExecutionId of the execution for which we want to get Metadata") String planExecutionId) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(accountId, planExecutionId,
            Set.of(PlanExecutionSummaryKeys.accountId, PlanExecutionSummaryKeys.orgIdentifier,
                PlanExecutionSummaryKeys.projectIdentifier, PlanExecutionSummaryKeys.pipelineIdentifier,
                PlanExecutionSummaryKeys.parentUniqueId));
    ScopeInfo scopeInfo =
        scopeResolutionHelper.getScopeInfo(accountId, pipelineExecutionSummaryEntity.getParentUniqueId());
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier()),
        Resource.of(PIPELINE_RESOURCE_TYPE, pipelineExecutionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_VIEW,
        String.format(PERMISSION_MISSING_MESSAGE, PipelineRbacPermissions.PIPELINE_VIEW, "pipeline"));

    ExecutionDataResponseDTO executionDetailsResponseDTO =
        pmsExecutionService.getExecutionData(accountId, planExecutionId);
    return ResponseDTO.newResponse(executionDetailsResponseDTO);
  }

  @GET
  @Path("/{planExecutionId}/metadata/details")
  @ApiOperation(value = "Get plan metadata details of an execution", nickname = "getExecutionDataDetails")
  @NGAccessControlCheck(resourceType = PIPELINE_RESOURCE_TYPE, permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getExecutionDataDetails",
      summary = "Get execution metadata details of a pipeline execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns plan metadata details of a execution")
      })
  @Hidden
  public ResponseDTO<ExecutionMetaDataResponseDetailsDTO>
  getExecutionsDetails(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "ExecutionId of the execution for which we want to get Metadata") String planExecutionId) {
    // TODO - Issue w.r.t rbac as we dont have org/project and resource identifier - Jira - CDS-89264
    ExecutionMetaDataResponseDetailsDTO executionDetailsResponseDTO =
        pmsExecutionService.getExecutionDataDetails(planExecutionId, accountId);
    return ResponseDTO.newResponse(executionDetailsResponseDTO);
  }

  @GET
  @Produces({"application/yaml"})
  @Path("/{planExecutionId}/inputset")
  @ApiOperation(value = "Gets  inputsetYaml", nickname = "getInputsetYaml")
  @Operation(deprecated = true, operationId = "getInputsetYaml",
      summary = "Get the Input Set YAML used for given Plan Execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the Input Set YAML used for given Plan Execution")
      })
  @Hidden
  public String
  getInputsetYaml(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                  @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @QueryParam("resolveExpressions") @DefaultValue("false") boolean resolveExpressions,
      @QueryParam("resolveExpressionsType") @DefaultValue("UNKNOWN") ResolveInputYamlType resolveExpressionsType,
      @Parameter(description = "Plan Execution Id for which we want to get the Input Set YAML",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    return pmsExecutionService
        .getInputSetYamlWithTemplate(
            accountId, orgId, projectId, planExecutionId, false, resolveExpressions, resolveExpressionsType)
        .getInputSetYaml();
  }

  @GET
  @Path("/{planExecutionId}/inputsetV2")
  @ApiOperation(value = "Gets  inputsetYaml", nickname = "getInputsetYamlV2")
  @Operation(operationId = "getInputsetYamlV2", summary = "Get the Input Set YAML used for given Plan Execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the Input Set YAML used for given Plan Execution")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<InputSetYamlWithTemplateDTO>
  getInputsetYamlV2(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                    @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(
          description = "A boolean that indicates whether or not expressions should be resolved in input set yaml ")
      @QueryParam("resolveExpressions") @DefaultValue("false") boolean resolveExpressions,
      @Parameter(
          description =
              "Resolve Expressions Type indicates what kind of expressions should be resolved in input set yaml. "
              + "The default value is UNKNOWN in which case no expressions will be resolved"
              + "Choose a value from the enum list: [RESOLVE_ALL_EXPRESSIONS, RESOLVE_TRIGGER_EXPRESSIONS, UNKNOWN]")
      @QueryParam("resolveExpressionsType") @DefaultValue("UNKNOWN") ResolveInputYamlType resolveExpressionsType,
      @Parameter(description = "Plan Execution Id for which we want to get the Input Set YAML",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    return ResponseDTO.newResponse(pmsExecutionService.getInputSetYamlWithTemplate(
        accountId, orgId, projectId, planExecutionId, false, resolveExpressions, resolveExpressionsType));
  }

  @GET
  @Path("/list-repositories")
  @ApiOperation(value = "Gets execution repositories list", nickname = "getExecutionRepositoriesList")
  @Operation(operationId = "getExecutionRepositoriesList", description = "Returns a list of repositories branches",
      summary = "List repositories",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns a list of all the repositories for Pipeline created in this scope")
      })
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<PMSPipelineListRepoResponse>
  getListOfRepos(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                 @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        executionGitMetadataService.findUniqueListOfRepositories(scopeInfo, pipelineIdentifier));
  }

  @GET
  @Path("/list-branches")
  @ApiOperation(value = "Gets execution branches list", nickname = "getExecutionBranchesList")
  @Operation(operationId = "getExecutionBranchesList",
      description = "Returns a list of branches the pipeline was executed from", summary = "List Branches",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of branches the pipeline was executed from")
      })
  @Hidden
  @Timed
  @ResponseMetered
  public ResponseDTO<PMSPipelineListBranchesResponse>
  getListOfBranches(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                    @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_LIST_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName) {
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        executionGitMetadataService.findUniqueListOfBranches(scopeInfo, pipelineIdentifier, repoName));
  }

  @GET
  @Path("/{planExecutionId}/notes")
  @ApiOperation(value = "Get Notes of an execution from planExecutionMetadata", nickname = "getNotesForExecution")
  @Operation(operationId = "getNotesForExecution", summary = "Get Notes for a pipelineExecution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Notes of a pipelineExecution")
      })
  public ResponseDTO<PipelineExecutionNotesDTO>
  getNotesForPlanExecution(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "ExecutionId of the execution for which we want to get notes",
          required = true) String planExecutionId) {
    executionHelper.checkForAccessOrThrowForGivenPlanExecutionId(
        planExecutionId, accountId, PIPELINE_RESOURCE_TYPE, Arrays.asList(PipelineRbacPermissions.PIPELINE_VIEW));
    String pipelineExecutionNotes = pmsExecutionSummaryService.getNotesForExecution(accountId, planExecutionId);
    return ResponseDTO.newResponse(PipelineExecutionNotesDTO.builder().notes(pipelineExecutionNotes).build());
  }

  @PUT
  @Path("/{planExecutionId}/notes")
  @ApiOperation(value = "Updates Notes of a pipelineExecution", nickname = "updateNotesForExecution")
  @Operation(operationId = "updateNotesForExecution", summary = "Updates Notes for a pipelineExecution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Notes of a pipelineExecution")
      })
  public ResponseDTO<PipelineExecutionNotesDTO>
  updateNotesForPlanExecution(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @NotNull @Parameter(description = PlanExecutionResourceConstants.NOTES_OF_A_PIPELINE_EXECUTION,
          required = true) @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) String notes,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "ExecutionId of the execution for which we want to update notes",
          required = true) String planExecutionId) {
    executionHelper.checkForAccessOrThrowForGivenPlanExecutionId(planExecutionId, accountId, PIPELINE_RESOURCE_TYPE,
        Arrays.asList(PipelineRbacPermissions.PIPELINE_EXECUTE, PipelineRbacPermissions.PIPELINE_ABORT));
    String pipelineExecutionNotes =
        pmsExecutionSummaryService.updateNotesForExecution(accountId, planExecutionId, notes);
    return ResponseDTO.newResponse(PipelineExecutionNotesDTO.builder().notes(pipelineExecutionNotes).build());
  }

  // For more details on the API refer https://harness.atlassian.net/wiki/x/kIBRFAU
  @POST
  @Path("/summary/outline")
  @ApiOperation(value = "Gets Executions list outline", nickname = "getListOfExecutionsOutline")
  @Operation(operationId = "getListOfExecutionsOutline",
      description = "Returns a List of Pipeline Executions Outline given pipelineId or a list of executionIds",
      summary = "List Executions Outline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all the Executions outline given pipelineId or a list of executionIds")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<CustomPage<PipelineExecutionOutlineDTO>>
  getListOfExecutionsOutline(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = NGCommonEntityConstants.LAST_SEEN_EXECUTION_ID_MESSAGE) @QueryParam(
          NGCommonEntityConstants.LAST_SEEN_EXECUTION_ID_KEY) String lastSeenExecutionId,
      @Parameter(description = NGCommonEntityConstants.LAST_SEEN_START_TIME_MESSAGE) @QueryParam(
          NGCommonEntityConstants.LAST_SEEN_START_TIME_KEY) Long lastSeenStartTime,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE)
      @DefaultValue("10") int size, @RequestBody(description = "Filters for fetching executions outline", content = {
        @Content(mediaType = "application/json",
            examples =
                @ExampleObject(name = "List executions outline", summary = "Sample List Pipeline Executions outline",
                    value = PipelineAPIConstants.LIST_EXECUTIONS_OUTLINE,
                    description = "Sample List Pipeline Executions outline JSON Payload"))
      }) PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO) {
    if (!(size > 0 && size <= MAX_ALLOWED_EXECUTIONS_PER_PAGE)) {
      throw new InvalidRequestException(
          String.format("Please verify query parameters for field named size. Size should be > 0 and <=%s",
              MAX_ALLOWED_EXECUTIONS_PER_PAGE));
    }
    checkAndThrowIfExecutionIdsExceedLimit(pipelineExecutionOutlineFilterDTO);

    return ResponseDTO.newResponse(pmsExecutionService.getListOfExecutionsOutline(
        accountId, orgId, projectId, pipelineExecutionOutlineFilterDTO, lastSeenExecutionId, lastSeenStartTime, size));
  }

  @GET
  @Path("/{planExecutionId}/policy-evaluation")
  @ApiOperation(value = "Gets runtime policy evaluation details for pipeline", nickname = "getPolicyEvaluation")
  @Operation(operationId = "getPpolicyEvaluation", summary = "Gets the policy evaluated used for given Plan Execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return policy evaluation details for given Plan Execution")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<Page<GovernanceMetadata>>
  getListOfEvaluatedPolicy(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) Integer size,
      @Parameter(description = "Plan Execution Id for which we want to get the Input Set YAML",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    if (size == null) {
      size = pipelineServiceConfiguration.getPolicyEvaluationDetailsMaxPageSize();
    }

    if (page < 0 || !(size > 0 && size <= pipelineServiceConfiguration.getPolicyEvaluationDetailsMaxPageSize())) {
      throw new InvalidRequestException(String.format(INVALID_PAGE_REQUEST_POLICY_EVALUATION_MESSAGE,
          pipelineServiceConfiguration.getPolicyEvaluationDetailsMaxPageSize()));
    }
    return ResponseDTO.newResponse(pmsExecutionService.getListOfEvaluatedPolicy(
        accountIdentifier, orgIdentifier, projectIdentifier, planExecutionId, size, page));
  }

  @GET
  @Path("/{planExecutionId}/annotations")
  @ApiOperation(value = "Gets annotations for a pipeline execution", nickname = "getPipelineExecutionAnnotations")
  @Operation(operationId = "getPipelineExecutionAnnotations",
      description = "Returns annotations for a specific pipeline execution",
      summary = "Fetch Pipeline Execution Annotations",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns annotations for the specified pipeline execution")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<PipelineAnnotationsResponseDTO>
  getPipelineExecutionAnnotations(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE)
                                  @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @NotNull @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @NotNull @Parameter(description = "Plan Execution ID for the pipeline run") @PathParam(
          NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    log.info("Fetching annotations for pipeline {} in project {}, org {}, account {}, planExecutionId {}", pipelineId,
        projectId, orgId, accountId, planExecutionId);

    Optional<PipelineAnnotationsResponseDTO> annotations =
        pipelineAnnotationsService.get(accountId, orgId, projectId, pipelineId, planExecutionId);

    return ResponseDTO.newResponse(annotations.orElse(null));
  }

  @GET
  @Path("/{planExecutionId}/annotations/{contextId:.+}/content")
  @ApiOperation(value = "Gets full annotation content from GCS", nickname = "getAnnotationFullContent")
  @Operation(operationId = "getAnnotationFullContent",
      description = "Returns full annotation content for a specific context from GCS",
      summary = "Fetch Full Annotation Content",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns full content for the specified annotation")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<AnnotationContentResponseDTO>
  getAnnotationFullContent(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE)
                           @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @NotNull @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @NotNull @Parameter(description = "Plan Execution ID for the pipeline run") @PathParam(
          NGCommonEntityConstants.PLAN_KEY) String planExecutionId,
      @NotNull @Parameter(description = "Context ID for the annotation") @PathParam("contextId") String contextId) {
    // TODO - Change to debug
    log.error("Fetching full annotation content for context {} in plan {} for pipeline {}", contextId, planExecutionId,
        pipelineId);

    AnnotationContentResponseDTO response = pipelineAnnotationsService.getAnnotationFullContent(
        accountId, orgId, projectId, pipelineId, planExecutionId, contextId);

    return ResponseDTO.newResponse(response);
  }

  private void checkAndThrowIfExecutionIdsExceedLimit(
      PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO) {
    Optional.ofNullable(pipelineExecutionOutlineFilterDTO)
        .map(PipelineExecutionOutlineFilterDTO::getPlanExecutionIds)
        .filter(ids -> ids.size() > MAX_ALLOWED_EXECUTION_IDS_TOTAL)
        .ifPresent(
            ids -> { throw new InvalidRequestException("Only 1000 executionIds can be passed to the API at once"); });
  }

  @GET
  @Path("/{planExecutionId}/workflow-graph")
  @ApiOperation(value = "Gets workflow graph", nickname = "getWorkflowGraph")
  @Operation(operationId = "getWorkflowGraph", description = "Returns the workflow graph for visualization",
      summary = "Get workflow graph for visualization",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the workflow graph for visualization")
      })
  @Timed
  @ResponseMetered
  public ResponseDTO<io.harness.beans.WorkflowGraph>
  getWorkflowGraph(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                   @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = "Node Execution Id from which to start graph traversal") @QueryParam(
          "nodeExecutionId") String nodeExecutionId,
      @Parameter(description = "Maximum depth to traverse from the starting node") @QueryParam("depth") @DefaultValue(
          "10") int depth,
      @Parameter(description = "Plan Execution Id for which we want to get the workflow graph",
          required = true) @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId) {
    executionHelper.checkForAccessOrThrowForGivenPlanExecutionIdForWorkflowGraph(
        planExecutionId, accountId, PIPELINE_RESOURCE_TYPE, Arrays.asList(PipelineRbacPermissions.PIPELINE_VIEW));
    return ResponseDTO.newResponse(
        executionGraphService.getWorkflowGraph(planExecutionId, nodeExecutionId, depth, accountId));
  }
}
