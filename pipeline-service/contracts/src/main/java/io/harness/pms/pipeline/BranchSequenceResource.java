/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * REST API for managing branch-scoped build sequence IDs.
 *
 * <p>This API provides operations to view and manage the per-branch build counters
 * that power the {@code <+pipeline.branchSeqId>} expression.
 *
 * @see <a href="https://harness.atlassian.net/browse/CI-19987">CI-19987</a>
 */
@OwnedBy(CI)
@Api("pipelines")
@Path("pipelines")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Branch Sequences", description = "APIs for managing branch-scoped build sequence IDs")
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
public interface BranchSequenceResource {
  /**
   * Lists all branch sequences for a pipeline.
   */
  @GET
  @Path("/{pipelineIdentifier}/branch-sequences")
  @ApiOperation(value = "List branch sequences for a pipeline", nickname = "listBranchSequences")
  @Operation(operationId = "listBranchSequences",
      description = "Returns all branch sequence records for the specified pipeline, "
          + "showing the current build number for each branch/repo combination.",
      summary = "List Branch Sequences",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully retrieved branch sequences")
      })
  ResponseDTO<List<BranchSequenceDTO>>
  listBranchSequences(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          "orgIdentifier") @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          "projectIdentifier") @ProjectIdentifier String projectIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @PathParam(
          "pipelineIdentifier") @ResourceIdentifier String pipelineIdentifier);

  /**
   * Gets a specific branch sequence by branch and repo URL.
   */
  @GET
  @Path("/{pipelineIdentifier}/branch-sequences/current")
  @ApiOperation(value = "Get current branch sequence", nickname = "getBranchSequence")
  @Operation(operationId = "getBranchSequence",
      description = "Returns the current sequence ID for a specific branch and repository combination.",
      summary = "Get Branch Sequence",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully retrieved branch sequence")
        ,
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "Branch sequence not found")
      })
  ResponseDTO<BranchSequenceDTO>
  getBranchSequence(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                        "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          "orgIdentifier") @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          "projectIdentifier") @ProjectIdentifier String projectIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @PathParam(
          "pipelineIdentifier") @ResourceIdentifier String pipelineIdentifier,
      @NotNull @Parameter(description = "Repository URL (will be normalized)") @QueryParam("repoUrl") String repoUrl,
      @NotNull @Parameter(description = "Branch name") @QueryParam("branch") String branch);

  /**
   * Deletes all branch sequences for a pipeline.
   */
  @DELETE
  @Path("/{pipelineIdentifier}/branch-sequences")
  @ApiOperation(value = "Delete all branch sequences for a pipeline", nickname = "deleteBranchSequences")
  @Operation(operationId = "deleteBranchSequences",
      description = "Deletes all branch sequence records for the specified pipeline. "
          + "This resets the build counters for all branches.",
      summary = "Delete Branch Sequences",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully deleted branch sequences")
      })
  ResponseDTO<Long>
  deleteBranchSequences(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                            "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          "orgIdentifier") @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          "projectIdentifier") @ProjectIdentifier String projectIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @PathParam(
          "pipelineIdentifier") @ResourceIdentifier String pipelineIdentifier);

  /**
   * Deletes a specific branch sequence by branch and repo URL.
   */
  @DELETE
  @Path("/{pipelineIdentifier}/branch-sequences/branch")
  @ApiOperation(value = "Delete a specific branch sequence", nickname = "deleteBranchSequence")
  @Operation(operationId = "deleteBranchSequence",
      description = "Deletes the branch sequence record for a specific branch and repository combination. "
          + "The repository URL will be normalized internally.",
      summary = "Delete Branch Sequence",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully deleted branch sequence")
        ,
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "Branch sequence not found")
      })
  ResponseDTO<Boolean>
  deleteBranchSequence(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                           "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          "orgIdentifier") @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          "projectIdentifier") @ProjectIdentifier String projectIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @PathParam(
          "pipelineIdentifier") @ResourceIdentifier String pipelineIdentifier,
      @NotNull @Parameter(description = "Repository URL (will be normalized)") @QueryParam("repoUrl") String repoUrl,
      @NotNull @Parameter(description = "Branch name") @QueryParam("branch") String branch);

  /**
   * Sets the sequence counter to a specific value for a branch.
   */
  @PUT
  @Path("/{pipelineIdentifier}/branch-sequences/set")
  @ApiOperation(value = "Set branch sequence to a specific value", nickname = "setBranchSequence")
  @Operation(operationId = "setBranchSequence",
      description = "Sets the branch sequence counter to a specific value for the given branch and repository. "
          + "The repository URL will be normalized internally. If no record exists, a new one is created.",
      summary = "Set Branch Sequence",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "200", description = "Successfully set branch sequence")
        ,
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "Invalid parameters (e.g., invalid repo URL or branch)")
      })
  ResponseDTO<BranchSequenceDTO>
  setBranchSequence(@NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                        "accountIdentifier") @AccountIdentifier String accountIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          "orgIdentifier") @OrgIdentifier String orgIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          "projectIdentifier") @ProjectIdentifier String projectIdentifier,
      @NotNull @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @PathParam(
          "pipelineIdentifier") @ResourceIdentifier String pipelineIdentifier,
      @NotNull @Parameter(description = "Repository URL (will be normalized)") @QueryParam("repoUrl") String repoUrl,
      @NotNull @Parameter(description = "Branch name") @QueryParam("branch") String branch,
      @NotNull @Min(value = 0, message = "Sequence ID must be non-negative") @Parameter(
          description = "The sequence value to set") @QueryParam("sequenceId") Long sequenceId);
}
