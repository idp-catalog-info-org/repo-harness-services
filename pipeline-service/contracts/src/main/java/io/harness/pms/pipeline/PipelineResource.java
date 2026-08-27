/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static javax.ws.rs.core.HttpHeaders.IF_MATCH;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.allowedvalues.AllowedValuesUsagesDTO;
import io.harness.allowedvalues.AllowedValuesUsagesRequestDTO;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.apiexamples.PipelineAPIConstants;
import io.harness.beans.ExecutionNode;
import io.harness.beans.ScopeInfo;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitaware.helper.PipelineMoveConfigRequestDTO;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityDeleteInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.bean.NotificationRules;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.pms.governance.PipelineSaveResponse;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.spec.server.pipeline.v1.model.PipelineValidationUUIDResponseBody;
import io.harness.steps.template.TemplateStepNode;
import io.harness.steps.template.stage.TemplateStageNode;
import io.harness.yaml.validator.beans.YamlSchemaValidationRequestDTO;
import io.harness.yaml.validator.beans.YamlSchemaValidationResponseDTO;
import io.harness.yaml.validator.beans.YamlValidationListAPIResponse;
import io.harness.yaml.validator.beans.YamlValidationRequestBody;

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
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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
import org.springframework.data.domain.Page;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Api("pipelines")
@Path("pipelines")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Pipeline", description = "This contains APIs related to Setup of Pipelines")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
public interface PipelineResource {
  @POST
  @ApiOperation(value = "Create a Pipeline", nickname = "createPipeline")
  @Operation(operationId = "postPipeline", description = "Creates a Pipeline", summary = "Create a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created pipeline")
      },
      deprecated = true)
  @Timed
  @Deprecated
  ResponseDTO<String>
  createPipeline(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                 @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.IDENTIFIER_KEY) String pipelineIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_NAME_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.NAME_KEY) String pipelineName,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DESCRIPTION_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.DESCRIPTION_KEY) String pipelineDescription,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DRAFT_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.DRAFT_KEY) Boolean isDraft,
      @Parameter(description = "Enable DAG execution for pipeline") @QueryParam("enableDAG") @DefaultValue("false")
      Boolean enableDAG, @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull String yaml, @Context ScopeInfo scopeInfo);

  @GET
  @Path("/attributes")
  @ApiOperation(value = "Get Pipeline attributes for Tag based access control for given pipeline identifiers",
      nickname = "getPipelineAttributes")
  @Operation(operationId = "getPipelineAttributes",
      description = "Get Pipeline attributes for Tag based access control for given pipeline identifiers",
      summary = "Get Pipeline attributes for Tag based access control",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the list of attributes for given list of pipeline identifiers")
      },
      deprecated = true)
  @Timed
  @InternalApi
  @Hidden
  ResponseDTO<List<Map<String, String>>>
  getPipelineAttributes(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                        @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @QueryParam("pipelineIdentifiers") List<String> pipelineIdentifiers,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.SIZE)
      @DefaultValue("25") int size, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/check-allowed-values")
  @ApiOperation(value = "Gets list of entities using allowedValues", nickname = "getPipelinesWithAllowedValues")
  @Operation(operationId = "getPipelineAttributes", description = "Gets list of entities using allowedValues",
      summary = "Gets list of entities using allowedValues",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns list of entities using allowedValues")
      },
      deprecated = true)
  @Timed
  @InternalApi
  @Hidden
  ResponseDTO<AllowedValuesUsagesDTO>
  getEntitiesWithAllowedValues(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @RequestBody AllowedValuesUsagesRequestDTO request);

  @POST
  @Path("/v2")
  @ApiOperation(value = "Create a Pipeline", nickname = "createPipelineV2")
  @Operation(operationId = "postPipelineV2", description = "Creates a Pipeline", summary = "Create a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created pipeline with metadata")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<PipelineSaveResponse>
  createPipelineV2(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                   @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.IDENTIFIER_KEY) String pipelineIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_NAME_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.NAME_KEY) String pipelineName,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DESCRIPTION_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.DESCRIPTION_KEY) String pipelineDescription,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DRAFT_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.DRAFT_KEY) Boolean isDraft,
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @Parameter(description = PipelineResourceConstants.ALLOW_DYNAMIC_EXECUTIONS) @QueryParam(
          NGCommonEntityConstants.ALLOW_DYNAMIC_EXECUTIONS) Boolean allowDynamicExecutions,
      @Parameter(description = "Enable DAG execution for pipeline") @QueryParam("enableDAG") @DefaultValue(
          "false") Boolean enableDAG,
      @RequestBody(required = true, description = "Pipeline YAML",
          content =
          {
            @Content(mediaType = "application/yaml",
                examples = @ExampleObject(name = "Create", summary = "Sample Create Pipeline YAML",
                    value = PipelineAPIConstants.CREATE_PIPELINE_API,
                    description = "Sample Pipeline YAML with One Build Stage and One Deploy Stage"))
          }) @NotNull String yaml,
      @QueryParam("public") @DefaultValue("false") boolean isPublic, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/v2/clone")
  @ApiOperation(value = "Clone a Pipeline", nickname = "clonePipelineV2")
  @Operation(operationId = "clonePipelineV2", summary = "Clone a Pipeline API",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns cloned pipeline with metadata")
      })
  @Hidden
  ResponseDTO<PipelineSaveResponse>
  clonePipelineV2(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                  @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @Parameter(description = "Enable DAG execution for pipeline") @QueryParam("enableDAG") @DefaultValue(
          "false") Boolean enableDAG,
      @RequestBody(required = true, description = "Request Body for Cloning a pipeline")
      @NotNull ClonePipelineDTO clonePipelineDTO, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/clone")
  @ApiOperation(value = "Clone a Pipeline", nickname = "clonePipeline")
  @Operation(operationId = "clonePipeline", summary = "Clone a Pipeline API",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns cloned pipeline with metadata")
      })
  @Hidden
  @Deprecated
  ResponseDTO<PipelineSaveResponse>
  clonePipeline(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @Parameter(description = "Enable DAG execution for pipeline") @QueryParam("enableDAG") @DefaultValue(
          "false") Boolean enableDAG,
      @RequestBody(required = true,
          description = "Request Body for Cloning a pipeline") @NotNull ClonePipelineDTO clonePipelineDTO);

  @POST
  @Path("/variables")
  @Operation(operationId = "createVariables",
      summary = "Get all the Variables which can be used as expression in the Pipeline.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all Variables used that are valid to be used as expression in pipeline.")
      })
  @ApiOperation(value = "Create variables for Pipeline", nickname = "createVariables")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  ResponseDTO<VariableMergeServiceResponse>
  createVariables(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                  @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull @ApiParam(hidden = true) String yaml,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/v2/variables")
  @Operation(operationId = "createVariablesV2",
      summary = "Get all the Variables which can be used as expression in the Pipeline.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns all Variables used that are valid to be used as expression in pipeline.")
      })
  @ApiOperation(value = "Create variables for Pipeline", nickname = "createVariablesV2")
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  ResponseDTO<VariableMergeServiceResponse>
  createVariablesV2(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                    @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull @ApiParam(hidden = true) String yaml,
      @Context ScopeInfo scopeInfo);

  @GET
  @Path("/{pipelineIdentifier}")
  @ApiOperation(value = "Gets a pipeline by identifier", nickname = "getPipeline")
  @Operation(operationId = "getPipeline", description = "Returns a Pipeline by Identifier",
      summary = "Fetch a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline YAML")
      })
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  ResponseDTO<PMSPipelineResponseDTO>
  getPipelineByIdentifier(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                          @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = "This is a boolean value. If true, returns Templates resolved Pipeline YAML in the "
              + "response else returns null.") @QueryParam("getTemplatesResolvedPipeline") @DefaultValue("false")
      boolean getTemplatesResolvedPipeline,
      @QueryParam("loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @QueryParam("validateAsync") @DefaultValue("false") boolean validateAsync,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo);

  @GET
  @Path("/refresh-and-get/{pipelineIdentifier}")
  @ApiOperation(value = "Refresh git file cache and fetch a pipeline by identifier", nickname = "refreshAndGetPipeline")
  @Operation(operationId = "refreshAndGetPipeline",
      description =
          "Force-refreshes the git file cache for a remote pipeline and returns the freshly fetched Pipeline. "
          + "Requires the git branch query parameter — repo default branch is not inferred when branch is omitted. "
          + "Intended for customer-facing recovery when the git-to-Harness sync is stale (webhook lost, race "
          + "condition, etc.). "
          + "Behind the PIPE_GITX_FORCE_REFRESH feature flag.",
      summary = "Refresh cache and fetch a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns pipeline YAML fetched fresh from git")
      })
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  ResponseDTO<PMSPipelineResponseDTO>
  refreshAndGetPipeline(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                        @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE, required = true) @NotBlank
      @QueryParam(GitSyncApiConstants.BRANCH_KEY) String branch, @Context ScopeInfo scopeInfo);

  @PUT
  @Path("/{pipelineIdentifier}")
  @ApiOperation(value = "Update a Pipeline", nickname = "putPipeline")
  @Operation(operationId = "updatePipeline", description = "Updates a Pipeline by Identifier",
      summary = "Update a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns updated pipeline")
      },
      deprecated = true)
  @Timed
  @Deprecated
  ResponseDTO<String>
  updatePipeline(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_NAME_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.NAME_KEY) String pipelineName,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DESCRIPTION_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.DESCRIPTION_KEY) String pipelineDescription,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DRAFT_PARAM_MESSAGE, required = false, hidden = true)
      @QueryParam(NGCommonEntityConstants.DRAFT_KEY) Boolean isDraft, @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @RequestBody(required = true, description = "Pipeline YAML to be updated") @NotNull String yaml,
      @Context ScopeInfo scopeInfo);

  @PUT
  @Path("/v2/{pipelineIdentifier}")
  @ApiOperation(value = "Update a Pipeline", nickname = "putPipelineV2")
  @Operation(operationId = "updatePipelineV2", description = "Updates a Pipeline by Identifier",
      summary = "Update a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns updated pipeline with metadata")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<PipelineSaveResponse>
  updatePipelineV2(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_NAME_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.NAME_KEY) String pipelineName,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DESCRIPTION_PARAM_MESSAGE, required = false,
          hidden = true) @QueryParam(NGCommonEntityConstants.DESCRIPTION_KEY) String pipelineDescription,
      @Parameter(description = PipelineResourceConstants.PIPELINE_DRAFT_PARAM_MESSAGE, required = false, hidden = true)
      @QueryParam(NGCommonEntityConstants.DRAFT_KEY) Boolean isDraft, @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @Parameter(description = PipelineResourceConstants.ALLOW_DYNAMIC_EXECUTIONS) @QueryParam(
          NGCommonEntityConstants.ALLOW_DYNAMIC_EXECUTIONS) Boolean allowDynamicExecutions,
      @RequestBody(required = true, description = "Pipeline YAML to be updated",
          content =
          {
            @Content(mediaType = "application/yaml",
                examples = @ExampleObject(name = "Update", summary = "Sample Update Pipeline YAML",
                    value = PipelineAPIConstants.CREATE_PIPELINE_API,
                    description = "Sample Pipeline YAML with One Build Stage and One Deploy Stage"))
          }) @NotNull String yaml,
      @QueryParam("public") @DefaultValue("false") boolean isPublic, @Context ScopeInfo scopeInfo);

  @DELETE
  @Path("/{pipelineIdentifier}")
  @ApiOperation(value = "Delete a pipeline", nickname = "softDeletePipeline")
  @Operation(operationId = "deletePipeline", description = "Deletes a Pipeline by Identifier",
      summary = "Delete a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Boolean status whether request was successful or not")
      })
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  ResponseDTO<Boolean>
  deletePipeline(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @BeanParam GitEntityDeleteInfoDTO entityDeleteInfo, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/list")
  @ApiOperation(value = "Gets Pipeline list", nickname = "getPipelineList")
  @Operation(operationId = "getPipelineList", description = "Returns List of Pipelines in the Given Project",
      summary = "List Pipelines",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Paginated list of pipelines.")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<Page<PMSPipelineSummaryResponseDTO>>
  getListOfPipelines(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE + " (max 200)") @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("25") int size,
      @QueryParam("sort") @Parameter(description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) List<String> sort,
      @Parameter(description = PipelineResourceConstants.PIPELINE_SEARCH_TERM_PARAM_MESSAGE)
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm, @QueryParam("module") String module,
      @QueryParam("filterIdentifier") String filterIdentifier, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @RequestBody(description = "This is the body for the filter properties for listing pipelines.",
          content =
          {
            @Content(mediaType = "application/json",
                examples = @ExampleObject(name = "List", summary = "Sample List Pipeline JSON",
                    value = PipelineAPIConstants.LIST_PIPELINE_API, description = "Sample List Pipeline JSON Payload"))
          }) PipelineFilterPropertiesDto filterProperties,
      @Parameter(description = "Boolean flag to get distinct pipelines from all branches.")
      @QueryParam("getDistinctFromBranches") Boolean getDistinctFromBranches, @Context ScopeInfo scopeInfo);

  @GET
  @Path("/summary/{pipelineIdentifier}")
  @ApiOperation(value = "Gets Pipeline Summary of a pipeline", nickname = "getPipelineSummary")
  @Operation(operationId = "getPipelineSummary", description = "Returns Pipeline Summary by Identifier",
      summary = "Fetch Pipeline Summary",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns Pipeline Summary having pipelineIdentifier as specified in request")
      })
  @Timed
  @ResponseMetered
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  ResponseDTO<PMSPipelineSummaryResponseDTO>
  getPipelineSummary(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @PathParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = " ", hidden = true) @QueryParam(
          PipelineResourceConstants.GET_METADATA_ONLY_PARAM_KEY) Boolean getMetadataOnly,
      @QueryParam("loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/import/{pipelineIdentifier}")
  @ApiOperation(value = "Get Pipeline YAML from Git Repository", nickname = "importPipeline")
  @Operation(operationId = "importPipeline", summary = "Import and Create Pipeline from Git Repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Import and Create Pipeline from Git Repository and saves a record for it in Harness")
      })
  @Deprecated(forRemoval = true)
  ResponseDTO<PipelineSaveResponse>
  importPipelineFromGit(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @PathParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineId,
      @BeanParam GitImportInfoDTO gitImportInfoDTO, PipelineImportRequestDTO pipelineImportRequestDTO,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/import")
  @ApiOperation(
      value = "Get Pipeline YAML from Git Repository without pipelineIdentifier", nickname = "importPipelineWithoutId")
  @Operation(operationId = "importPipeline", summary = "Import and Create Pipeline from Git Repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Import and Create Pipeline from Git Repository and saves a record for it in Harness")
      })
  ResponseDTO<PipelineSaveResponse>
  importPipelineFromGit(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @BeanParam @Valid GitImportInfoDTO gitImportInfoDTO, PipelineImportRequestDTO pipelineImportRequestDTO,
      @Context ScopeInfo scopeInfo);

  @GET
  @Path("/expandedJSON/{pipelineIdentifier}")
  @ApiOperation(value = "Gets Pipeline JSON with extra info for some fields", nickname = "getExpandedPipelineJSON")
  @Operation(operationId = "getExpandedPipelineJSON", summary = "Gets Pipeline JSON with extra info for some fields",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Gets Pipeline JSON with extra info for some fields as required for Pipeline Governance")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  ResponseDTO<ExpandedPipelineJsonDTO>
  getExpandedPipelineJson(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE)
                          @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @PathParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @Context ScopeInfo scopeInfo);
  @POST
  @Path("/v2/steps")
  @Hidden
  @ApiOperation(value = "Get Steps for given modules Version 2", nickname = "getStepsV2")
  @Operation(operationId = "getStepsV2", summary = "Gets all the Steps for given Category (V2 Version)",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns steps for a given Category")
      })
  ResponseDTO<StepCategory>
  getStepsV2(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
                 required = true) @NotNull @QueryParam("accountId") String accountId,
      @RequestBody(required = true,
          description = "Step Pallete Filter request body") @NotNull StepPalleteFilterWrapper stepPalleteFilterWrapper);

  @GET
  @Path("/notification")
  @ApiOperation(value = "Get Notification Schema", nickname = "getNotificationSchema")
  @Hidden
  ResponseDTO<NotificationRules> getNotificationSchema();

  @GET
  @Path("/getExecutionNode")
  @ApiOperation(value = "get execution node", nickname = "getExecutionNode")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getExecutionNode", summary = "Get the Execution Node by Execution Id",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Execution Node if it exists, else returns Null.")
      })
  @Hidden
  ResponseDTO<ExecutionNode>
  getExecutionNode(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                   @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = "Id for the corresponding Node Execution", required = true) @NotNull @QueryParam(
          "nodeExecutionId") String nodeExecutionId);

  @GET
  @Path("/dummy-pmsSteps-api")
  @ApiOperation(value = "This is dummy api to expose pmsSteps", nickname = "dummyPmsStepsApi")
  @Hidden
  ResponseDTO<PmsAbstractStepNode> getPmsStepNodes();

  @GET
  @Path("/dummy-templateStep-api")
  @ApiOperation(value = "This is dummy api to expose templateStepNode", nickname = "dummyTemplateStepApi")
  @Hidden
  // do not delete this.
  ResponseDTO<TemplateStepNode> getTemplateStepNode();

  @GET
  @Path("/dummy-templateStage-api")
  @ApiOperation(value = "This is dummy api to expose templateStageNode", nickname = "dummyTemplateStageApi")
  @Hidden
  // do not delete this.
  ResponseDTO<TemplateStageNode> getTemplateStageNode();

  @POST
  @Path("/validate-yaml-with-schema")
  @ApiOperation(value = "Validate a Pipeline YAML", nickname = "validatePipelineByYAML")
  @Hidden
  @Operation(operationId = "postPipeline", summary = "Validate a Pipeline YAML with Schema",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return if Pipeline YAML is valid or not")
      })
  ResponseDTO<String>
  validatePipelineByYAML(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                         @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @RequestBody(required = true, description = "Pipeline YAML") @NotNull String yaml, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/validate-pipeline-with-schema")
  @ApiOperation(value = "Validate a Pipeline", nickname = "validatePipeline")
  @Hidden
  @Operation(operationId = "postPipeline", summary = "Validate a Pipeline with Schema",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return if Pipeline is valid or not")
      })
  ResponseDTO<String>
  validatePipelineByIdentifier(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @Context ScopeInfo scopeInfo);

  @GET
  @Path("resolved-templates-pipeline-yaml/{pipelineIdentifier}")
  @ApiOperation(value = "Gets template resolved pipeline yaml", nickname = "getTemplateResolvedPipeline")
  @Hidden
  @Operation(operationId = "getTemplateResolvedPipeline",
      summary = "Gets template resolved pipeline yaml by pipeline identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns templates resolved pipeline YAML")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  ResponseDTO<TemplatesResolvedPipelineResponseDTO>
  getTemplateResolvedPipelineYaml(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo);

  @GET
  @Path("/list-repos")
  @ApiOperation(value = "Gets Repository list", nickname = "getRepositoryList")
  @Operation(operationId = "getRepositoryList", description = "Gets the list of all repositories",
      summary = "List Repositories",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the repositories of all Pipelines")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  ResponseDTO<PMSPipelineListRepoResponse>
  getListRepos(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Context ScopeInfo scopeInfo);

  @GET
  @Path("/remote-pipeline-metadata")
  @ApiOperation(value = "List remote pipelines grouped by repository for a given accountId",
      nickname = "getRemotePipelineMetadata")
  @Operation(operationId = "getRemotePipelineMetadata",
      description = "Returns all unique repoName/repoURL pairs for remote pipelines in an account along with pipeline "
          + "metadata. Optionally filter by repoName.",
      summary = "List remote pipelines grouped by repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "List of remote repositories with the pipeline file paths in each repo")
      })
  @InternalApi
  @Hidden
  ResponseDTO<RemotePipelinesResponseDTO>
  getRemotePipelineMetadata(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Optional filter to return remote pipelines only for the given repoName.") @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = "Page number (zero-indexed).") @QueryParam(
          NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Page size.") @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("20")
      int size, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/move-config/{pipelineIdentifier}")
  @Hidden
  @ApiOperation(value = "Move Pipeline YAML from inline to remote or remote to inline", nickname = "moveConfigs")
  @Operation(operationId = "moveConfigs", summary = "Move Pipeline YAML from inline to remote or remote to inline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetches Pipeline YAML from Harness DB and creates a remote entity or Fetches Pipeline YAML "
                + "from remote repository and creates a inline entity")
      })
  ResponseDTO<MoveConfigResponse>
  moveConfig(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
             @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @PathParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) String pipelineIdentifier,
      @BeanParam PipelineMoveConfigRequestDTO pipelineMoveConfigRequestDTO, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/{pipelineIdentifier}/validate")
  @Hidden
  @ApiOperation(value = "Start a validation event for a Pipeline", nickname = "validatePipelineAsync")
  @Operation(operationId = "validatePipelineAsync",
      description = "Start a validation event for a Pipeline and return the uuid of the event",
      summary = "Start a validation event for a Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return uuid of the started event")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  ResponseDTO<PipelineValidationUUIDResponseBody>
  startPipelineValidationEvent(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo);

  @GET
  @Path("validate/{uuid}")
  @Hidden
  @ApiOperation(value = "Get Pipeline validation event data", nickname = "getPipelineValidateResult")
  @Operation(operationId = "getPipelineValidateResult",
      description = "Get Pipeline validation event data for the given uuid",
      summary = "Get Pipeline validation event data",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return uuid of the started event")
      })

  ResponseDTO<PipelineValidationResponseDTO>
  getPipelineValidateResult(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.UUID) String uuid);

  @PUT
  @Path("/{pipelineIdentifier}/update-git-metadata")
  @ApiOperation(value = "Update git-metadata in remote pipeline Entity", nickname = "updatePipelineGitDetails")
  @Operation(operationId = "updatePipelineGitDetails",
      description = "Update git-metadata in remote pipeline and returns the identifier of updated pipeline",
      summary = "Update git-metadata in remote pipeline Entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns identifier of updated pipeline")
      })
  ResponseDTO<PMSGitUpdateResponseDTO>
  updateGitMetadataForPipeline(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @BeanParam GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/validate-yaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  ResponseDTO<YamlValidationListAPIResponse> validatePipelineYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @RequestBody(required = true) @Valid YamlValidationRequestBody yamlValidationRequestBody);

  @POST
  @Path("/validate-yaml-schema")
  @Hidden
  @InternalApi
  @ApiOperation(value = "Validate pipeline YAML against schema", nickname = "validatePipelineYamlSchema")
  @Operation(operationId = "validatePipelineYamlSchema",
      summary = "Validate pipeline YAML against the JSON schema for the specified version",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns schema validation result")
      })
  ResponseDTO<YamlSchemaValidationResponseDTO>
  validatePipelineYamlSchema(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @NotNull @RequestBody(required = true) @Valid YamlSchemaValidationRequestDTO requestDTO);

  @POST
  @Hidden
  @Path("/force-import")
  @ApiOperation(value = "Import YAML from git and create pipeline bypassing errors", nickname = "forceImportPipeline")
  @Operation(operationId = "forceImportPipeline", summary = "Force import and create pipeline from git repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Force import and create pipeline from git repository")
      })
  ResponseDTO<ForceImportPipelineResponse>
  forceImportPipeline(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      ForceImportPipelineRequestDTO requestDTO);

  @POST
  @Path("/{pipelineIdentifier}/convert-to-dag")
  @ApiOperation(value = "Convert existing pipeline to DAG format", nickname = "convertPipelineToDAG")
  @Operation(operationId = "convertPipelineToDAG", summary = "Convert existing pipeline to DAG format",
      description = "Converts an existing sequential pipeline to DAG format by adding depends_on fields to stages",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the converted pipeline response")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EDIT)
  ResponseDTO<PMSPipelineResponseDTO>
  convertPipelineToDAG(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Context ScopeInfo scopeInfo);
}
