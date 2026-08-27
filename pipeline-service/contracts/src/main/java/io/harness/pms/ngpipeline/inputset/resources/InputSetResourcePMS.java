/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.resources;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static javax.ws.rs.core.HttpHeaders.IF_MATCH;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.apiexamples.PipelineAPIConstants;
import io.harness.beans.ScopeInfo;
import io.harness.gitaware.helper.GitImportInfoDTO;
import io.harness.gitsync.GitMetadataUpdateRequestInfoDTO;
import io.harness.gitsync.interceptor.GitEntityCreateInfoDTO;
import io.harness.gitsync.interceptor.GitEntityDeleteInfoDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.gitsync.interceptor.GitEntityUpdateInfoDTO;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.inputset.ForceImportInputSetRequestDTO;
import io.harness.pms.inputset.ForceImportInputSetResponse;
import io.harness.pms.inputset.InputSetFilterPropertiesDto;
import io.harness.pms.inputset.InputSetSchemaConstants;
import io.harness.pms.inputset.MergeInputSetForRerunRequestDTO;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.inputset.MergeInputSetTemplateRequestDTO;
import io.harness.pms.inputset.RemoteInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BatchInputSetsAPIRequest;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsAPIRequest;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsAPIResponse;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetGitUpdateResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetImportResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetListTypePMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetMoveConfigRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetMoveConfigResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSanitiseResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetTemplateResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlDiffDTO;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.pms.pipeline.PMSInputSetListRepoResponse;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.security.annotations.InternalApi;
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

@OwnedBy(PIPELINE)
@Api("/inputSets")
@Path("/inputSets")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })

@Tag(name = "Pipeline Input Set", description = "This contains APIs related to Input Sets")
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
public interface InputSetResourcePMS {
  @GET
  @Path("{inputSetIdentifier}")
  @ApiOperation(value = "Gets an InputSet by identifier", nickname = "getInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getInputSet",
      description = "Returns Input Set for a Given Identifier (Throws an Error if no Input Set Exists)",
      summary = "Fetch an Input Set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Input Set if exists for the given Identifier.")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<InputSetResponseDTOPMS>
  getInputSet(@PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) @Parameter(
                  description = PipelineResourceConstants.INPUT_SET_ID_PARAM_MESSAGE) String inputSetIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be fetched") String pipelineBranch,
      @QueryParam("pipelineRepoID") @Parameter(
          description = "Github Repo identifier of the Pipeline for which the Input Set is to be fetched")
      String pipelineRepoID,
      @QueryParam("loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo);

  @GET
  @Path("refresh-and-get/{inputSetIdentifier}")
  @ApiOperation(
      value = "Refresh git file cache and fetch an Input Set by identifier", nickname = "refreshAndGetInputSet")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "refreshAndGetInputSet",
      description = "Force-refreshes the git file cache for a remote Input Set and returns the freshly fetched entity. "
          + "Requires the git branch query parameter — repo default branch is not inferred when branch is omitted. "
          + "Behind the PIPE_GITX_FORCE_REFRESH feature flag.",
      summary = "Refresh cache and fetch an Input Set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns Input Set YAML fetched fresh from git")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<InputSetResponseDTOPMS>
  refreshAndGetInputSet(
      @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) @Parameter(
          description = PipelineResourceConstants.INPUT_SET_ID_PARAM_MESSAGE) String inputSetIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Parameter(description = GitSyncApiConstants.BRANCH_PARAM_MESSAGE, required = true) @NotBlank
      @QueryParam(GitSyncApiConstants.BRANCH_KEY) String branch, @Context ScopeInfo scopeInfo);

  @GET
  @Path("overlay/{inputSetIdentifier}")
  @ApiOperation(value = "Gets an Overlay InputSet by identifier", nickname = "getOverlayInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getOverlayInputSet", summary = "Gets an Overlay Input Set by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "The Overlay Input Set that corresponds to the given Overlay Input Set Identifier")
      })
  ResponseDTO<OverlayInputSetResponseDTOPMS>
  getOverlayInputSet(
      @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) @Parameter(
          description = PipelineResourceConstants.OVERLAY_INPUT_SET_ID_PARAM_MESSAGE) String inputSetIdentifier,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be fetched") String pipelineBranch,
      @QueryParam("pipelineRepoID") @Parameter(
          description = "Github Repo identifier of the Pipeline for which the Input Set is to be fetched")
      String pipelineRepoID,
      @QueryParam("loadFromFallbackBranch") @DefaultValue("false") boolean loadFromFallbackBranch,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo);

  @POST
  @ApiOperation(value = "Create an InputSet For Pipeline", nickname = "createInputSetForPipeline")
  @Operation(operationId = "postInputSet", description = "Creates an Input Set for a Pipeline",
      summary = "Create an Input Set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "If the YAML is valid, returns created Input Set. If not, it sends what is wrong with the YAML")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<InputSetResponseDTOPMS>
  createInputSet(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                     description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be created") String pipelineBranch,
      @QueryParam("pipelineRepoID")
      @Parameter(description = "Github Repo identifier of the Pipeline for which the Input Set is to be created")
      String pipelineRepoID, @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @QueryParam("InputSetVersion") @DefaultValue(HarnessYamlVersion.V0) @Parameter(
          description = "Input set yaml version, should be one of '0' or '1'") String inputSetVersion,
      @RequestBody(required = true,
          description = "Input set YAML to be created. The Account, Org, Project, and Pipeline identifiers inside the "
              + "YAML should match the query parameters.",
          content =
          {
            @Content(mediaType = "application/yaml",
                examples = @ExampleObject(name = "Create", summary = "Sample Input Set YAML",
                    value = PipelineAPIConstants.CREATE_INPUTSET_API, description = "Sample Input Set YAML"))
          }) @NotNull String yaml,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("overlay")
  @ApiOperation(value = "Create an Overlay InputSet For Pipeline", nickname = "createOverlayInputSetForPipeline")
  @Operation(operationId = "postOverlayInputSet", summary = "Create an Overlay Input Set for a pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "If the YAML is valid, returns created Overlay Input Set. If not, it sends what is wrong with the YAML")
      })
  ResponseDTO<OverlayInputSetResponseDTOPMS>
  createOverlayInputSet(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @BeanParam GitEntityCreateInfoDTO gitEntityCreateInfo,
      @QueryParam("InputSetVersion") @DefaultValue(HarnessYamlVersion.V0) @Parameter(
          description = "Input set yaml version, should be one of '0' or '1'") String inputSetVersion,
      @RequestBody(required = true,
          description = "Overlay Input Set YAML to be created. The Account, Org, Project, and Pipeline identifiers "
              + "inside the YAML should match the query parameters") @NotNull String yaml,
      @Context ScopeInfo scopeInfo);

  @PUT
  @Path("{inputSetIdentifier}")
  @ApiOperation(value = "Update an InputSet by identifier", nickname = "updateInputSetForPipeline")
  @Operation(operationId = "putInputSet", description = "Updates the Input Set for a Pipeline",
      summary = "Update an Input Set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "If the YAML is valid, returns the updated Input Set. If not, it sends what is wrong with the YAML")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<InputSetResponseDTOPMS>
  updateInputSet(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = "Identifier for the Input Set that needs to be updated. An Input Set corresponding to "
              + "this identifier should already exist.") @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY)
      String inputSetIdentifier,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be updated") String pipelineBranch,
      @QueryParam("pipelineRepoID")
      @Parameter(description = "Github Repo Id of the Pipeline for which the Input Set is to be updated")
      String pipelineRepoID, @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @QueryParam("InputSetVersion") @DefaultValue(HarnessYamlVersion.V0) @Parameter(
          description = "Input set yaml version, should be one of '0' or '1'") String inputSetVersion,
      @RequestBody(required = true,
          description = "Input set YAML to be updated. The query parameters should match the Account, Org, Project, "
              + "and Pipeline Ids in the YAML.",
          content =
          {
            @Content(mediaType = "application/yaml",
                examples = @ExampleObject(name = "Update", summary = "Sample Input Set YAML",
                    value = PipelineAPIConstants.CREATE_INPUTSET_API, description = "Sample Input Set YAML"))
          }) @NotNull String yaml,
      @Context ScopeInfo scopeInfo);

  @PUT
  @Path("overlay/{inputSetIdentifier}")
  @ApiOperation(value = "Update an Overlay InputSet by identifier", nickname = "updateOverlayInputSetForPipeline")
  @Operation(operationId = "putOverlayInputSet", summary = "Update an Overlay Input Set for a pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "If the YAML is valid, returns the updated Overlay Input Set. If not, it sends what is wrong "
                + "with the YAML")
      })
  ResponseDTO<OverlayInputSetResponseDTOPMS>
  updateOverlayInputSet(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = "Identifier for the Overlay Input Set that needs to be updated.") @PathParam(
          NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) String inputSetIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @QueryParam("InputSetVersion") @DefaultValue(HarnessYamlVersion.V0) @Parameter(
          description = "Input set yaml version, should be one of '0' or '1'") String inputSetVersion,
      @RequestBody(required = true,
          description =
              "Overlay Input Set YAML to be updated. The Account, Org, Project, and Pipeline identifiers inside the "
              + "YAML should match the query parameters, and the Overlay Input Set identifier cannot be changed.")
      @NotNull @ApiParam(hidden = true) String yaml);

  @DELETE
  @Path("{inputSetIdentifier}")
  @ApiOperation(value = "Delete an InputSet by identifier", nickname = "deleteInputSetForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_DELETE)
  @Operation(operationId = "deleteInputSet", description = "Deletes the Input Set by Identifier",
      summary = "Delete an Input Set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the Deleted Input Set")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<Boolean> delete(
      @Parameter(description = PipelineResourceConstants.IF_MATCH_PARAM_MESSAGE) @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = "Identifier of the Input Set that should be deleted.") @PathParam(
          NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) String inputSetIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @BeanParam GitEntityDeleteInfoDTO entityDeleteInfo, @Context ScopeInfo scopeInfo);

  @GET
  @ApiOperation(value = "Gets InputSets list for a pipeline", nickname = "getInputSetsListForPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "listInputSet", description = "Lists all Input Sets for a Pipeline",
      summary = "List Input Sets",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetch all the Input Sets for a Pipeline, including Overlay Input Sets.")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<PageResponse<InputSetSummaryResponseDTOPMS>>
  listInputSetsForPipeline(@QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") @Parameter(
                               description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("100") @Parameter(
          description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) int size,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Pipeline identifier for which we need the Input Sets list.") @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Parameter(description = InputSetSchemaConstants.INPUT_SET_TYPE_MESSAGE) @QueryParam(
          "inputSetType") @DefaultValue("ALL") InputSetListTypePMS inputSetListType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) @Parameter(
          description = PipelineResourceConstants.INPUT_SET_SEARCH_TERM_PARAM_MESSAGE) String searchTerm,
      @QueryParam(NGResourceFilterConstants.SORT_KEY) @Parameter(
          description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) List<String> sort,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @Context ScopeInfo scopeInfo);

  @POST
  @Path("template")
  @ApiOperation(value = "Get template from a pipeline YAML", nickname = "getTemplateFromPipeline")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "runtimeInputTemplate", description = "Returns Runtime Input Template for a Pipeline",
      summary = "Fetch Runtime Input Template",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetch Runtime Input Template for a Pipeline, along with any expressions whose value is "
                + "needed for running specific Stages")
      })
  @Timed
  @ResponseMetered
  ResponseDTO<InputSetTemplateResponseDTOPMS>
  getTemplateFromPipeline(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                              description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = "Pipeline identifier for which we need the Runtime Input Template.") String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, InputSetTemplateRequestDTO inputSetTemplateRequestDTO,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo);

  @POST
  @Path("merge")
  @ApiOperation(
      value = "Merges given Input Sets list on pipeline and return Input Set template format of applied pipeline",
      nickname = "getMergeInputSetFromPipelineTemplateWithListInput")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "mergeInputSets", summary = "Merge given Input Sets into a single Runtime Input YAML",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Merge given Input Sets into A single Runtime Input YAML")
      })
  ResponseDTO<MergeInputSetResponseDTOPMS>
  getMergeInputSetFromPipelineTemplate(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) String pipelineIdentifier,
      @Parameter(description = "Github branch of the Pipeline to which the Input Sets belong") @QueryParam(
          "pipelineBranch") String pipelineBranch,
      @Parameter(description = "Github Repo identifier of the Pipeline to which the Input Sets belong")
      @QueryParam("pipelineRepoID") String pipelineRepoID, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO,
      @HeaderParam("Load-From-Cache") @DefaultValue("false") String loadFromCache, @Context ScopeInfo scopeInfo);

  @POST
  @Path("merge-for-rerun")
  @ApiOperation(value = "Merges runtime input YAML from the given planExecutionId and return Input Set template format "
          + "of applied pipeline",
      nickname = "getMergeInputSetForRun")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "mergeInputSetsForRerun",
      summary = "Merge runtime input YAML from the given planExecutionId into the pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Merge runtime input YAML from the given planExecutionId into the pipeline")
      })
  @Hidden
  ResponseDTO<MergeInputSetResponseDTOPMS>
  getMergeInputSetForRerun(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                               description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) String pipelineIdentifier,
      @Parameter(description = "Github branch of the Pipeline to which the Input Sets belong") @QueryParam(
          "pipelineBranch") String pipelineBranch,
      @Parameter(description = "Github Repo identifier of the Pipeline to which the Input Sets belong")
      @QueryParam("pipelineRepoID") String pipelineRepoID, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull @Valid MergeInputSetForRerunRequestDTO mergeInputSetForRerunRequestDTO, @Context ScopeInfo scopeInfo);

  @POST
  @Path("merge-input-for-execution")
  @ApiOperation(value = "Merges pipeline template and input set yaml of pipeline execution for given planExecutionId",
      nickname = "getMergeInputForExecution")
  @Operation(operationId = "mergeInputsForExecution",
      summary = "Merges pipeline template and input set yaml of pipeline execution for given planExecutionId",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Merges pipeline template and input set yaml of pipeline execution for given planExecutionId")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  ResponseDTO<MergeInputSetResponseDTOPMS>
  getMergeInputForExecution(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                                description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @Parameter(
          description = "A boolean that indicates whether or not expressions should be resolved in input set yaml")
      @QueryParam("resolveExpressions") @DefaultValue("false") boolean resolveExpressions,
      @Parameter(description =
                     "Resolve Expressions Type indicates what kind of expressions should be resolved in input set yaml."
              + "The default value is UNKNOWN in which case no expressions will be resolved"
              + "Choose a value from the enum list: [RESOLVE_ALL_EXPRESSIONS, RESOLVE_TRIGGER_EXPRESSIONS, UNKNOWN]")
      @QueryParam("resolveExpressionsType") @DefaultValue("UNKNOWN") ResolveInputYamlType resolveExpressionsType,
      @NotNull @QueryParam("planExecutionId") String planExecutionId);

  @POST
  @Path("mergeWithTemplateYaml")
  @ApiOperation(
      value = "Merges given runtime input YAML on pipeline and return Input Set template format of applied pipeline",
      nickname = "getMergeInputSetFromPipelineTemplate")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "mergeRuntimeInputIntoPipeline",
      summary = "Merge given Runtime Input YAML into the Pipeline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Merge given Runtime Input YAML into the Pipeline")
      })
  @Hidden
  // TODO(Naman): Correct PipelineServiceClient when modifying this api
  ResponseDTO<MergeInputSetResponseDTOPMS>
  getMergeInputSetFromPipelineTemplate(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier @Parameter(
          description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) String pipelineIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline to which the Input Sets belong") String pipelineBranch,
      @QueryParam("pipelineRepoID") @Parameter(
          description = "Github Repo identifier of the Pipeline to which the Input Sets belong") String pipelineRepoID,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull @Valid MergeInputSetTemplateRequestDTO mergeInputSetTemplateRequestDTO, @Context ScopeInfo scopeInfo);

  @POST
  @Path("{inputSetIdentifier}/sanitise")
  @ApiOperation(value = "Sanitise an InputSet", nickname = "sanitiseInputSet")
  @Operation(operationId = "sanitiseInputSet", summary = "Sanitise an Input Set by removing invalid fields",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Sanitise an Input Set by removing invalid fields from the Input Set YAML and save it")
      })
  @Hidden
  ResponseDTO<InputSetSanitiseResponseDTO>
  sanitiseInputSet(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                       description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Parameter(description = "Identifier for the Input Set that needs to be updated. An Input Set corresponding to "
              + "this identifier should already exist.") @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY)
      String inputSetIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be updated") String pipelineBranch,
      @QueryParam("pipelineRepoID")
      @Parameter(description = "Github Repo Id of the Pipeline for which the Input Set is to be updated")
      String pipelineRepoID, @BeanParam GitEntityUpdateInfoDTO gitEntityInfo,
      @RequestBody(required = true, description = "The invalid Input Set Yaml to be sanitized")
      @NotNull String invalidInputSetYaml, @Context ScopeInfo scopeInfo);

  @GET
  @Path("{inputSetIdentifier}/yaml-diff")
  @ApiOperation(value = "Get sanitised YAML for an InputSet", nickname = "yamlDiffForInputSet")
  @Operation(operationId = "yamlDiffForInputSet",
      summary = "Get sanitised YAML for an InputSet by removing invalid fields",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Sanitise an Input Set by removing invalid fields from the Input Set YAML")
      })
  @Hidden
  ResponseDTO<InputSetYamlDiffDTO>
  getInputSetYAMLDiff(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Parameter(description = "Identifier for the Input Set that needs to be updated. An Input Set corresponding to "
              + "this identifier should already exist.") @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY)
      String inputSetIdentifier,
      @QueryParam("pipelineBranch") @Parameter(
          description = "Github branch of the Pipeline for which the Input Set is to be updated") String pipelineBranch,
      @QueryParam("pipelineRepoID")
      @Parameter(description = "Github Repo Id of the Pipeline for which the Input Set is to be updated")
      String pipelineRepoID, @BeanParam GitEntityUpdateInfoDTO gitEntityInfo, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/import/{inputSetIdentifier}")
  @Hidden
  @ApiOperation(value = "Get Input Set YAML from Git Repository", nickname = "importInputSet")
  @Operation(operationId = "importInputSet", summary = "Get Input Set YAML from Git Repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetches Input Set YAML from Git Repository and saves a record for it in Harness")
      })
  ResponseDTO<InputSetImportResponseDTO>
  importInputSetFromGit(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @Parameter(
                            description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) @Parameter(
          description = PipelineResourceConstants.INPUT_SET_ID_PARAM_MESSAGE) String inputSetIdentifier,
      @BeanParam @Valid GitImportInfoDTO gitImportInfoDTO, InputSetImportRequestDTO inputSetImportRequestDTO,
      @Context ScopeInfo scopeInfo);

  @POST
  @Path("/move-config/{inputSetIdentifier}")
  @Hidden
  @ApiOperation(
      value = "Move Input Set YAML from inline to remote or remote to inline", nickname = "inputSetMoveConfig")
  @Operation(operationId = "inputSetMoveConfig",
      summary = "Move Input Set YAML from inline to remote or remote to inline",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetches Input Set YAML from Harness DB and creates a remote entity or Fetches Pipeline YAML "
                + "from remote repository and creates a inline entity")
      })
  ResponseDTO<InputSetMoveConfigResponseDTO>
  moveConfig(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                 NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @PathParam(NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) @Parameter(
          description = PipelineResourceConstants.INPUT_SET_ID_PARAM_MESSAGE) String inputSetIdentifier,
      @BeanParam InputSetMoveConfigRequestDTO inputSetMoveConfigRequestDTO, @Context ScopeInfo scopeInfo);

  @GET
  @Path("/list-repos")
  @ApiOperation(value = "Gets InputSet Repository list", nickname = "getInputSetRepositoryList")
  @Operation(operationId = "getInputSetRepositoryList", description = "Gets the list of all repositories",
      summary = "List InputSet Repositories",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a list of all the repositories of all InputSets")
      })
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  ResponseDTO<PMSInputSetListRepoResponse>
  getListRepos(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Context ScopeInfo scopeInfo);

  @GET
  @Path("/remote-inputset-metadata")
  @ApiOperation(value = "List remote input sets grouped by repository for a given accountId",
      nickname = "getRemoteInputSetMetadata")
  @Operation(operationId = "getRemoteInputSetMetadata",
      description = "Returns all unique repoName/repoURL pairs for remote input sets in an account along with input "
          + "set metadata. Optionally filter by repoName.",
      summary = "List remote input sets grouped by repository",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "List of remote repositories with the input set file paths in each repo")
      })
  @InternalApi
  @Hidden
  ResponseDTO<RemoteInputSetsResponseDTO>
  getRemoteInputSetMetadata(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Optional filter to return remote input sets only for the given repoName.") @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = "Page number (zero-indexed).") @QueryParam(
          NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") int page,
      @Parameter(description = "Page size.") @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("20")
      int size, @Context ScopeInfo scopeInfo);

  @PUT
  @Path("/{inputSetIdentifier}/update-git-metadata")
  @ApiOperation(value = "Update git-metadata in remote inputSet", nickname = "updateInputSetGitDetails")
  @Operation(operationId = "updateInputSetGitDetails",
      description = "Update git-metadata in remote input-set and return the updated input-set",
      summary = "Update git-metadata in remote input-set",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns identifier of updated input-set")
      })
  ResponseDTO<InputSetGitUpdateResponseDTO>
  updateGitMetadataForInputSet(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.PIPELINE_ID_FOR_INPUT_SET_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) @ResourceIdentifier String pipelineIdentifier,
      @Parameter(description = PipelineResourceConstants.INPUT_SET_ID_PARAM_MESSAGE, required = true) @PathParam(
          NGCommonEntityConstants.INPUT_SET_IDENTIFIER_KEY) @ResourceIdentifier String inputSetIdentifier,
      @BeanParam GitMetadataUpdateRequestInfoDTO gitMetadataUpdateRequestInfo, @Context ScopeInfo scopeInfo);

  @POST
  @Hidden
  @Path("/list")
  @ApiOperation(value = "Gets InputSets list for a project", nickname = "getInputSetsListForProject")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "listInputSetForProject", description = "Lists all Input Sets for a Project",
      summary = "List Input Sets for a project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetch all the Input Sets for a Project, including Overlay Input Sets.")
      })
  ResponseDTO<PageResponse<InputSetListResponseDTO>>
  listInputSetsForProject(@QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") @Parameter(
                              description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("25") @Parameter(
          description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) int size,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = InputSetSchemaConstants.INPUT_SET_TYPE_MESSAGE) @QueryParam(
          "inputSetType") @DefaultValue("ALL") InputSetListTypePMS inputSetListType,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) @Parameter(
          description = PipelineResourceConstants.INPUT_SET_SEARCH_TERM_PARAM_MESSAGE) String searchTerm,
      @QueryParam(NGResourceFilterConstants.SORT_KEY) @Parameter(
          description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) List<String> sort,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @RequestBody(description = "This is the body for the filter properties for listing InputSets.")
      InputSetFilterPropertiesDto filterProperties, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/validate-yaml")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Hidden
  ResponseDTO<YamlValidationListAPIResponse> validateInputSetYaml(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @RequestBody(required = true) @Valid YamlValidationRequestBody yamlValidationRequestBody);

  @POST
  @Path("/force-import")
  @Hidden
  ResponseDTO<ForceImportInputSetResponse> forceImportInputSet(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      ForceImportInputSetRequestDTO requestDTO);

  @POST
  @Path("/get/batch-input-sets-metadata")
  @ApiOperation(value = "Gets regular InputSets metadata for multiple pipelines (excludes overlay input sets)",
      nickname = "getBatchInputSetsMetadata")
  @Operation(operationId = "getBatchInputSetsMetadata",
      description = "Lists regular Input Sets for multiple pipelines (excludes overlay input sets). "
          + "If pipeline identifiers are not provided, fetches all accessible input sets based on RBAC permissions.",
      summary = "List regular Input Sets for multiple pipelines (excludes overlay input sets)",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetch regular Input Sets for the specified pipelines (excludes overlay input sets). "
                + "If no pipeline identifiers provided, returns all accessible input sets based on RBAC.")
      })
  ResponseDTO<PageResponse<InputSetListResponseDTO>>
  getBatchInputSetsMetadata(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGResourceFilterConstants.PAGE_KEY) @DefaultValue("0") @Parameter(
          description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) int page,
      @QueryParam(NGResourceFilterConstants.SIZE_KEY) @DefaultValue("20") @Parameter(
          description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) int size,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) @Parameter(
          description = PipelineResourceConstants.INPUT_SET_SEARCH_TERM_PARAM_MESSAGE) String searchTerm,
      @Context ScopeInfo scopeInfo,
      @RequestBody(description = "Optional request containing pipeline identifiers to fetch regular input sets for. "
              + "If not provided or empty, fetches all accessible input sets based on RBAC permissions.")
      @Valid BatchInputSetsAPIRequest request);

  @POST
  @Path("/get/bulk")
  @ApiOperation(value = "Get multiple input sets by identifiers", nickname = "getBulkInputSets")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Operation(operationId = "getBulkInputSets",
      description =
          "Gets multiple input sets by their identifiers for a specific pipeline. Only returns non-deleted input sets.",
      summary = "Get multiple input sets by identifiers (non-deleted only)",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Fetch multiple non-deleted input sets by their identifiers.")
      })
  ResponseDTO<BulkInputSetsAPIResponse>
  getBulkInputSets(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull @Parameter(
          description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @AccountIdentifier String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @NotNull @Parameter(
          description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @OrgIdentifier String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @NotNull @Parameter(
          description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @ProjectIdentifier String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) @NotNull
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE)
      @ResourceIdentifier String pipelineIdentifier, @Context ScopeInfo scopeInfo,
      @RequestBody(required = true, description = "Request containing input set identifiers to fetch") @NotNull
      @Valid BulkInputSetsAPIRequest bulkInputSetsAPIRequest);
}
