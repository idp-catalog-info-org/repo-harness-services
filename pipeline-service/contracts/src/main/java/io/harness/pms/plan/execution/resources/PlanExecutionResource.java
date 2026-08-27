/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.resources;

import static io.harness.pms.pipeline.PipelineResourceConstants.INPUT_SET_IDENTIFIER_LIST_PARAM_MESSAGE;
import static io.harness.pms.pipeline.PipelineResourceConstants.NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE;
import static io.harness.pms.pipeline.PipelineResourceConstants.USE_ORIGINAL_PIPELINE_YAML;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.apiexamples.PipelineAPIConstants;
import io.harness.beans.ScopeInfo;
import io.harness.engine.executions.retry.RetryHistoryResponseDto;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.executions.retry.RetryLatestExecutionResponseDto;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.execution.resource.CheckPostExecutionRollbackDTO;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.pipeline.PlanExecutionMetaRequestDTO;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.PlanExecutionInterruptTypePipeline;
import io.harness.pms.plan.execution.PlanExecutionInterruptTypeStage;
import io.harness.pms.plan.execution.PlanExecutionResourceConstants;
import io.harness.pms.plan.execution.PlanExecutionResponseDto;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.RetryPipelineRequestDTO;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.beans.request.ManualExecutionRequestDto;
import io.harness.pms.plan.execution.beans.response.ManualExecutionResponseDto;
import io.harness.pms.preflight.dto.PreFlightDTO;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.stages.StageExecutionResponse;
import io.harness.security.annotations.InternalApi;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
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
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import org.hibernate.validator.constraints.NotEmpty;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Tag(name = "Pipeline Execute", description = "This contains APIs for Executing a Pipeline")
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
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error"),
          @ApiResponse(code = 403, response = AccessDeniedErrorDTO.class, message = "Unauthorized")
    })
@OwnedBy(HarnessTeam.PIPELINE)
@Api("/pipeline/execute")
@Path("/pipeline/execute")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
public interface PlanExecutionResource {
  @POST
  @Path("/{identifier}")
  @ApiOperation(
      value = "Execute a pipeline with inputSet pipeline YAML", nickname = "postPipelineExecuteWithInputSetYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postPipelineExecuteWithInputSetYaml",
      description = "Execute a Pipeline with Runtime Input YAML",
      summary = "Execute a Pipeline with Runtime Input YAML",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = PlanExecutionResponseDto.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = PlanExecutionResponseDto.class))
        })
      })
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  runPipelineWithInputSetPipelineYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @QueryParam("notifyOnlyUser") @DefaultValue("false") boolean notifyOnlyUser,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @ApiParam(hidden = true) @RequestBody(
          description = "Enter Runtime Input YAML if the Pipeline contains Runtime Inputs. Template for this can be "
              + "Fetched from /inputSets/template API.",
          content =
          {
            @Content(mediaType = "application/yaml",
                examples = @ExampleObject(name = "Execute Runtime Input YAML",
                    summary = "Execute Pipeline with Runtime Input YAML",
                    value = PipelineAPIConstants.EXECUTE_INPUT_YAML, description = ""))
          }) String inputSetPipelineYaml,
      @Context ScopeInfo scopeInfo,
      @QueryParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_LIST) @Parameter(
          description = INPUT_SET_IDENTIFIER_LIST_PARAM_MESSAGE) List<String> inputSetIdentifiers,
      @QueryParam(NGCommonEntityConstants.ASYNC_PLAN_CREATION) @DefaultValue("false") boolean asyncPlanCreation,
      @QueryParam("shouldRunAsV1") @DefaultValue("false") @Parameter(
          description = "Execute V0 pipeline using unified mode for enhanced features") boolean shouldRunAsV1);

  @POST
  @Path("/compiled-yaml")
  @ApiOperation(value = "Get compiled YAML for pipeline execution", nickname = "getCompiledYamlForPipelineExecution")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getCompiledYamlForPipelineExecution",
      description = "Get compiled YAML for pipeline execution", summary = "Get compiled YAML for pipeline execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns compiled YAML", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseDTO.class))
        })
      })
  @Timed
  @ResponseMetered
  @Hidden
  ResponseDTO<String>
  getCompiledYamlForPipeline(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @Parameter(
          description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE)
      @ResourceIdentifier String pipelineIdentifier,
      @ApiParam(hidden = true) @RequestBody(
          description = "Enter Runtime Input YAML if the Pipeline contains Runtime Inputs.", content = {
            @Content(mediaType = "application/yaml",
                examples = @ExampleObject(name = "Runtime Input YAML", summary = "Runtime Input YAML for compilation",
                    value = PipelineAPIConstants.EXECUTE_INPUT_YAML, description = ""))
          }) String inputSetPipelineYaml);

  @POST
  @Path("postExecutionRollbackIsAllowed")
  @ApiOperation(hidden = true, value = "Check if Rollback of previous Execution is possible",
      nickname = "postExecutionRollbackIsAllowed")
  @Hidden
  ResponseDTO<CheckPostExecutionRollbackDTO>
  checkIfPostExecutionRollbackIsAllowed(@RequestBody(required = true) @NotNull List<String> stageNodeExecutionIds);

  @POST
  @Path("postExecutionRollback/{planExecutionId}")
  @ApiOperation(value = "Rollback a previous Execution", nickname = "postExecutionRollback")
  @Operation(operationId = "postExecutionRollback", description = "Rollback a previous Execution",
      summary = "Rollback a previous Execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details")
      })
  @Hidden
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  runPostExecutionRollback(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @Parameter(
          description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE)
      @ResourceIdentifier String pipelineIdentifier,
      @PathParam(NGCommonEntityConstants.PLAN_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE) @ResourceIdentifier
      @NotNull String planExecutionId, @QueryParam("stageNodeExecutionIds") @NotNull String stageNodeExecutionIds,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @QueryParam(NGCommonEntityConstants.ASYNC_PLAN_CREATION) @DefaultValue("false") boolean asyncPlanCreation,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/{identifier}/v2")
  @ApiOperation(
      value = "Execute a pipeline with inputSet pipeline YAML V2", nickname = "postPipelineExecuteWithInputSetYamlv2")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postPipelineExecuteWithInputSetYamlv2",
      summary = "Execute a pipeline with inputSet pipeline yaml V2",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details V2")
      })
  @Hidden
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  runPipelineWithInputSetPipelineYamlV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @ApiParam(hidden = true) @Parameter(
          description = "InputSet YAML if the pipeline contains runtime inputs. This will be empty by default if "
              + "pipeline does not contains runtime inputs") String inputSetPipelineYaml,
      @QueryParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_LIST) @Parameter(
          description = INPUT_SET_IDENTIFIER_LIST_PARAM_MESSAGE) List<String> inputSetIdentifiers,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/{identifier}/stages")
  @ApiOperation(value = "Execute given Stages of a Pipeline with inputSet pipeline yaml",
      nickname = "runStagesWithRuntimeInputYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postExecuteStages", summary = "Execute given Stages of a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Execute given Stages of a Pipeline with Runtime Input Yaml")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  runStagesWithRuntimeInputYaml(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                                    description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier @NotEmpty @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      RunStageRequestDTO runStageRequestDTO,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @QueryParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_LIST) @Parameter(
          description = INPUT_SET_IDENTIFIER_LIST_PARAM_MESSAGE) List<String> inputSetIdentifiers,
      @QueryParam(NGCommonEntityConstants.ASYNC_PLAN_CREATION) @DefaultValue("false") boolean asyncPlanCreation,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/rerun/{originalExecutionId}/{identifier}/stages")
  @ApiOperation(value = "Rerun a pipeline with inputSet pipeline yaml", nickname = "rerunStagesWithRuntimeInputYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postReExecuteStages", summary = "Re-run given Stages of a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Re-run a given Stages Execution of a Pipeline")
      })
  @Hidden
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  rerunStagesWithRuntimeInputYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier @NotEmpty @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineIdentifier,
      @NotNull @PathParam("originalExecutionId")
      @Parameter(description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String originalExecutionId, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      RunStageRequestDTO runStageRequestDTO,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @QueryParam(NGCommonEntityConstants.ASYNC_PLAN_CREATION) @DefaultValue("false") boolean asyncPlanCreation,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/rerun/{originalExecutionId}/{identifier}")
  @ApiOperation(
      value = "Re Execute a pipeline with inputSet pipeline yaml", nickname = "rePostPipelineExecuteWithInputSetYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "rePostPipelineExecuteWithInputSetYaml",
      summary = "Re Execute a pipeline with inputSet pipeline yaml",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details")
      })
  @Hidden
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  rerunPipelineWithInputSetPipelineYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @PathParam("originalExecutionId") @Parameter(
          description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String originalExecutionId,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @ApiParam(hidden = true) @Parameter(
          description = "InputSet YAML if the pipeline contains runtime inputs. This will be empty by default if "
              + "pipeline does not contains runtime inputs") String inputSetPipelineYaml,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @QueryParam(NGCommonEntityConstants.USE_ORIGINAL_PIPELINE_YAML) @Parameter(
          description = USE_ORIGINAL_PIPELINE_YAML) @DefaultValue("false") Boolean useOriginalPipelineYaml,
      @QueryParam(NGCommonEntityConstants.ASYNC_PLAN_CREATION) @DefaultValue("false") boolean asyncPlanCreation,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/rerun/v2/{originalExecutionId}/{identifier}")
  @ApiOperation(value = "Re Execute a pipeline with inputSet pipeline yaml Version 2",
      nickname = "rePostPipelineExecuteWithInputSetYamlV2")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "rePostPipelineExecuteWithInputSetYamlV2",
      summary = "Re Execute a pipeline with InputSet Pipeline YAML Version 2",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details")
      })
  @Hidden
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  rerunPipelineWithInputSetPipelineYamlV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @PathParam("originalExecutionId") @Parameter(
          description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String originalExecutionId,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @ApiParam(hidden = true) @Parameter(
          description = "InputSet YAML if the pipeline contains runtime inputs. This will be empty by default if "
              + "pipeline does not contains runtime inputs") String inputSetPipelineYaml,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @QueryParam(NGCommonEntityConstants.USE_ORIGINAL_PIPELINE_YAML) @Parameter(
          description = USE_ORIGINAL_PIPELINE_YAML) @DefaultValue("false") Boolean useOriginalPipelineYaml,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/debug/{originalExecutionId}/{identifier}/stages")
  @ApiOperation(value = "debug a pipeline with inputSet pipeline yaml", nickname = "debugStagesWithRuntimeInputYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postDebugStages", summary = "debug given Stages of a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Re-run a given Stages Execution of a Pipeline")
      })
  @Hidden
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  debugStagesWithRuntimeInputYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier @NotEmpty @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineIdentifier,
      @NotNull @PathParam("originalExecutionId")
      @Parameter(description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String originalExecutionId, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      RunStageRequestDTO runStageRequestDTO, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/debug/{originalExecutionId}/{identifier}")
  @ApiOperation(
      value = "debug a pipeline with inputSet pipeline yaml", nickname = "debugPipelineExecuteWithInputSetYaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "debugPipelineExecuteWithInputSetYaml",
      summary = "debug a pipeline with inputSet pipeline yaml",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details")
      })
  @Hidden
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  debugPipelineWithInputSetPipelineYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @PathParam("originalExecutionId") @Parameter(
          description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String originalExecutionId,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @ApiParam(hidden = true) @Parameter(
          description = "InputSet YAML if the pipeline contains runtime inputs. This will be empty by default if "
              + "pipeline does not contains runtime inputs") String inputSetPipelineYaml,
      @QueryParam(NGCommonEntityConstants.ASYNC_PLAN_CREATION) @DefaultValue("false") boolean asyncPlanCreation,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/debug/v2/{originalExecutionId}/{identifier}")
  @ApiOperation(value = "Re Execute a pipeline with inputSet pipeline yaml Version 2",
      nickname = "debugPipelineExecuteWithInputSetYamlV2")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "debugPipelineExecuteWithInputSetYamlV2",
      summary = "Re Execute a pipeline with InputSet Pipeline YAML Version 2",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details")
      })
  @Hidden
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  debugPipelineWithInputSetPipelineYamlV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @PathParam("originalExecutionId") @Parameter(
          description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String originalExecutionId,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @ApiParam(hidden = true) @Parameter(
          description = "InputSet YAML if the pipeline contains runtime inputs. This will be empty by default if "
              + "pipeline does not contains runtime inputs") String inputSetPipelineYaml,
      @Context ScopeInfo scopeInfo);

  @GET
  @Path("/{planExecutionId}/retryStages")
  @ApiOperation(value = "Get retry stages for failed pipeline", nickname = "getRetryStages")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "getRetryStages", summary = "Get retry stages for failed pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all retry stages from where we can retry the failed pipeline")
      })
  @Hidden
  ResponseDTO<RetryInfo>
  getRetryStages(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
                     description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "planExecutionId of the execution we want to retry") String planExecutionId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/{identifier}/inputSetList")
  @ApiOperation(
      value = "Execute a pipeline with input set references list", nickname = "postPipelineExecuteWithInputSetList")

  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "postPipelineExecuteWithInputSetList",
      description = "Execute a Pipeline with Input Set References",
      summary = "Execute a Pipeline with Input Set References",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details V2")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  runPipelineWithInputSetIdentifierList(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY)
      @Parameter(description = PlanExecutionResourceConstants.PIPELINE_IDENTIFIER_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @NotNull @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @QueryParam(NGCommonEntityConstants.ASYNC_PLAN_CREATION) @DefaultValue("false") boolean asyncPlanCreation,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("rerun/{originalExecutionId}/{identifier}/inputSetList")
  @Operation(operationId = "rerunPipelineWithInputSetIdentifierList",
      summary = "Rerun a pipeline with given inputSet identifiers",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns details of new plan execution and git details if any")
      })
  @ApiOperation(
      value = "Execute a pipeline with input set references list", nickname = "rePostPipelineExecuteWithInputSetList")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Hidden
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  rerunPipelineWithInputSetIdentifierList(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.MODULE_TYPE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.MODULE_TYPE) String moduleType,
      @NotNull @Parameter(description = PipelineResourceConstants.ORIGINAL_EXECUTION_ID_PARAM_MESSAGE,
          required = true) @PathParam("originalExecutionId") String originalExecutionId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier @NotEmpty String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = PipelineResourceConstants.USE_FQN_IF_ERROR_RESPONSE_ERROR_MESSAGE,
          required = true) @QueryParam("useFQNIfError") @DefaultValue("false") boolean useFQNIfErrorResponse,
      @RequestBody(required = true, description = "InputSet reference details") @NotNull
      @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @Context ScopeInfo scopeInfo);

  @PUT
  @ApiOperation(value = "stop the pipeline executions", nickname = "handleInterrupt")
  @Path("/interrupt/{planExecutionId}")
  @Operation(operationId = "putHandleInterrupt", description = "Executes an Interrupt on a Given Execution",
      summary = "Execute an Interrupt",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Takes a possible Interrupt value and applies it onto the execution referred by the planExecutionId")
      })
  ResponseDTO<InterruptDTO>
  handleInterrupt(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
                      description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgId,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectId,
      @Parameter(
          description = "The Interrupt type needed to be applied to the execution. Choose a value from the enum list.")
      @NotNull @QueryParam("interruptType") PlanExecutionInterruptTypePipeline executionInterruptTypePipeline,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.") @NotNull @PathParam("planExecutionId")
      String planExecutionId,
      @Context ScopeInfo scopeInfo);

  @PUT
  @ApiOperation(value = "stop any workflow executions using pipelione", nickname = "handleInterruptV2")
  @Path("/interrupt/internal/{planExecutionId}")
  @Operation(operationId = "putHandleInterruptV2", description = "Executes an Interrupt on a Given Execution",
      summary = "Execute an Interrupt",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Takes a possible Interrupt value and applies it onto the execution referred by the planExecutionId")
      })
  @Hidden
  @InternalApi
  ResponseDTO<InterruptDTO>
  handleInterruptV2(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
                        description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectId,
      @Parameter(
          description = "The Interrupt type needed to be applied to the execution. Choose a value from the enum list.")
      @NotNull @QueryParam("interruptType") PlanExecutionInterruptTypePipeline executionInterruptTypePipeline,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.") @NotNull @PathParam("planExecutionId")
      String planExecutionId,
      @Context ScopeInfo scopeInfo);

  // TODO(prashant) : This is a temp route for now merge it with the above. Need be done in sync with UI changes
  @PUT
  @ApiOperation(value = "mark as failure or stop the stage executions", nickname = "handleStageInterrupt")
  @Operation(operationId = "handleStageInterrupt", summary = "Handles the interrupt for a given stage in a pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Takes a possible Interrupt value and applies it onto the given stage in the execution")
      })
  @Path("/interrupt/{planExecutionId}/{nodeExecutionId}")
  ResponseDTO<InterruptDTO>
  handleStageInterrupt(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
                           required = true) @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @NotNull @Parameter(
          description = "The Interrupt type needed to be applied to the execution. Choose a value from the enum list.")
      @QueryParam("interruptType") PlanExecutionInterruptTypeStage executionInterruptTypeStage,
      @NotNull @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.",
          required = true) @PathParam("planExecutionId") String planExecutionId,
      @NotNull @Parameter(description = PlanExecutionResourceConstants.NODE_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.",
          required = true) @PathParam("nodeExecutionId") String nodeExecutionId,
      @Context ScopeInfo scopeInfo);

  @PUT
  @ApiOperation(value = "Ignore,Abort,MarkAsSuccess,Retry on post manual intervention",
      nickname = "handleManualInterventionInterrupt")
  @Path("/manualIntervention/interrupt/{planExecutionId}/{nodeExecutionId}")
  @Operation(operationId = "handleManualInterventionInterrupt",
      summary = "Handles Ignore,Abort,MarkAsSuccess,Retry on post manual intervention for a given execution with the "
          + "given planExecutionId",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Takes a possible Interrupt value and applies it onto the given stage in the execution")
      })
  @Hidden
  ResponseDTO<InterruptDTO>
  handleManualInterventionInterrupt(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @NotNull @Parameter(
          description = "The Interrupt type needed to be applied to the execution. Choose a value from the enum list.")
      @QueryParam("interruptType") PlanExecutionInterruptType executionInterruptType,
      @NotNull @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.",
          required = true) @PathParam("planExecutionId") String planExecutionId,
      @NotNull @Parameter(description = PlanExecutionResourceConstants.NODE_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.",
          required = true) @PathParam("nodeExecutionId") String nodeExecutionId,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/manual-execution/{nodeExecutionId}")
  @ApiOperation(value = "Marks the Manual Execution as fail or resume", nickname = "markManualExecution")
  @Operation(operationId = "markManualExecution", summary = "Marks the Manual Execution as fail or resume",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns status of api request")
      })
  ResponseDTO<ManualExecutionResponseDto>
  handleManualExecution(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @NotNull @Parameter(description = "Node execution id on which the manual execution action is required",
          required = true) @PathParam("nodeExecutionId") String nodeExecutionId,
      @RequestBody(required = true, description = "Details of the manual execution action that needs to be taken")
      @Valid @NotNull ManualExecutionRequestDto manualExecutionRequestDto, @Context ScopeInfo scopeInfo);

  @POST
  @ApiOperation(value = "initiate pre flight check", nickname = "startPreflightCheck")
  @Path("/preflightCheck")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "startPreFlightCheck", summary = "Start Preflight Checks for a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Start Preflight Checks for a Pipeline, given a Runtime Input YAML. Returns Preflight Id")
      })
  @Hidden
  ResponseDTO<String>
  startPreFlightCheck(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @NotEmpty @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @ApiParam(hidden = true) @Parameter(description = "Runtime Input YAML to be sent for Pipeline execution")
      String inputSetPipelineYaml, @Context ScopeInfo scopeInfo);

  @GET
  @ApiOperation(value = "get preflight check response", nickname = "getPreflightCheckResponse")
  @Path("/getPreflightCheckResponse")
  @Operation(operationId = "getPreFlightCheckResponse", summary = "Get Preflight Checks Response for a Preflight Id",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Get Preflight Checks Response for a Preflight Id. May require Multiple Queries if Preflight "
                + "is In Progress")
      })
  @Hidden
  ResponseDTO<PreFlightDTO>
  getPreflightCheckResponse(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
                                description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam("preflightCheckId") @Parameter(
          description = "Preflight Id from the start Preflight Checks API") String preflightCheckId,
      @ApiParam(hidden = true) String inputSetPipelineYaml);

  @GET
  @ApiOperation(value = "get list of stages for stage execution", nickname = "getStagesExecutionList")
  @Path("/stagesExecutionList")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getStagesExecutionList", summary = "Get list of Stages to select for Stage executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns list of Stage identifiers with their names and stage dependencies")
      })
  @Hidden
  ResponseDTO<List<StageExecutionResponse>>
  getStagesExecutionList(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                             description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @NotEmpty @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo);

  @POST
  @Path("retry/{identifier}")
  @ApiOperation(value = "Retry a executed pipeline with inputSet pipeline yaml", nickname = "retryPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "retryPipeline", summary = "Retry a executed pipeline with inputSet pipeline yaml",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns execution details", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = PlanExecutionResponseDto.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = PlanExecutionResponseDto.class))
        })
      })
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  retryPipelineWithInputSetPipelineYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @QueryParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String previousExecutionId,
      @NotNull @QueryParam(NGCommonEntityConstants.RETRY_STAGES) @Parameter(
          description = PlanExecutionResourceConstants.RETRY_STAGES_PARAM_MESSAGE) List<String> retryStagesIdentifier,
      @QueryParam(NGCommonEntityConstants.RUN_ALL_STAGES) @Parameter(
          description = PlanExecutionResourceConstants.RUN_ALL_STAGES) @DefaultValue("true") boolean runAllStages,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier,
      @ApiParam(hidden = true) @RequestBody(description = "Retry a executed pipeline with inputSet pipeline yaml",
          content =
          {
            @Content(mediaType = "application/yaml",
                examples = @ExampleObject(name = "Retry a executed pipeline with Runtime Input YAML",
                    summary = "Retry a executed pipeline with Runtime Input YAML",
                    value = PipelineAPIConstants.EXECUTE_INPUT_YAML,
                    description = "Retry a executed pipeline with Runtime Input YAML"))
          }) String inputSetPipelineYaml,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @QueryParam(NGCommonEntityConstants.ASYNC_PLAN_CREATION) @DefaultValue("false") boolean asyncPlanCreation,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("retry/v2/{identifier}")
  @ApiOperation(value = "Retry a executed pipeline with Runtime Input YAML V2", nickname = "retryPipelineV2")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Operation(operationId = "retryPipelineV2",
      description = "Retry a executed pipeline with Runtime Input YAML and Expression Values V2",
      summary = "Retry a executed pipeline with Runtime Input YAML V2",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline execution details", content = {
          @Content(mediaType = "application/json", schema = @Schema(implementation = PlanExecutionResponseDto.class))
          , @Content(mediaType = "application/yaml", schema = @Schema(implementation = PlanExecutionResponseDto.class))
        })
      })
  @Timed
  @ResponseMetered
  ResponseDTO<PlanExecutionResponseDto>
  retryPipelineWithInputSetPipelineYamlV2(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.MODULE_TYPE) @Parameter(
          description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @QueryParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = PlanExecutionResourceConstants.ORIGINAL_EXECUTION_IDENTIFIER_PARAM_MESSAGE)
      String previousExecutionId,
      @NotNull @QueryParam(NGCommonEntityConstants.RETRY_STAGES) @Parameter(
          description = PlanExecutionResourceConstants.RETRY_STAGES_PARAM_MESSAGE) List<String> retryStagesIdentifier,
      @QueryParam(NGCommonEntityConstants.RUN_ALL_STAGES) @Parameter(
          description = PlanExecutionResourceConstants.RUN_ALL_STAGES) @DefaultValue("true") boolean runAllStages,
      @PathParam(NGCommonEntityConstants.IDENTIFIER_KEY) @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier,
      @RequestBody(required = false,
          description = "Request body containing Runtime Input YAML and Expression Values for retry execution",
          content =
          {
            @Content(mediaType = "application/json", schema = @Schema(implementation = RetryPipelineRequestDTO.class))
            , @Content(mediaType = "application/yaml", schema = @Schema(implementation = RetryPipelineRequestDTO.class))
          }) @Valid RetryPipelineRequestDTO retryPipelineRequestDTO,
      @QueryParam(NGCommonEntityConstants.NOTES_FOR_PIPELINE_EXECUTION) @Parameter(
          description = NOTES_FOR_PLAN_EXECUTION_PARAM_MESSAGE) @DefaultValue("") String notes,
      @QueryParam(NGCommonEntityConstants.ASYNC_PLAN_CREATION) @DefaultValue("false") boolean asyncPlanCreation,
      @Context ScopeInfo scopeInfo);

  @GET
  @Path("retryHistory/{planExecutionId}")
  @ApiOperation(value = "Retry History for a given execution", nickname = "retryHistory")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "retryHistory", summary = "Retry History for a given execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns retry history execution details")
      })
  ResponseDTO<RetryHistoryResponseDto>
  getRetryHistory(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE)
      @ResourceIdentifier String pipelineIdentifier,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description = "planExecutionId of the execution of whose we need to find the retry history")
      String planExecutionId);

  @GET
  @Path("latestExecutionId/{planExecutionId}")
  @ApiOperation(value = "Latest ExecutionId from Retry Executions", nickname = "latestExecutionId")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "latestExecutionId", summary = "Latest ExecutionId from Retry Executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns execution id of the latest execution from all retries")
      })
  @Hidden
  ResponseDTO<RetryLatestExecutionResponseDto>
  getRetryLatestExecutionId(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE)
      @ResourceIdentifier String pipelineIdentifier,
      @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) @Parameter(
          description =
              "planExecutionId of the execution of whose we need to find the latest execution planExecutionId")
      String planExecutionId);

  @PUT
  @Hidden
  @ApiOperation(value = "Abort the pipeline executions for the given execution ids, synchronously",
      nickname = "bulkAbortPipelineExecutionsInterrupt")
  @Path("/sync/bulkAbort")
  @Operation(operationId = "bulkAbortPipelineExecutionsInterrupt",
      description = "Abort the pipeline executions for the given execution ids",
      summary = "Abort the pipeline executions for the given execution ids",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Takes a list of executionIds and synchronously aborts the execution,")
      })
  ResponseDTO<List<InterruptDTO>>
  bulkAbortPipelineExecutionsInterrupt(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgId,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectId,
      @NotNull @RequestBody(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Abort needs to be applied.") PlanExecutionMetaRequestDTO planExecutionIds,
      @Context ScopeInfo scopeInfo);
}
