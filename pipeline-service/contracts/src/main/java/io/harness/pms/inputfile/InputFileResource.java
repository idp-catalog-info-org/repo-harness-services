/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputfile;

import static io.harness.NGCommonEntityConstants.FILE_CONTENT_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.FileDeleteResponseDTO;
import io.harness.pms.pipeline.FileMetadataResponseDTO;
import io.harness.pms.pipeline.FileUploadResponseDTO;
import io.harness.pms.pipeline.FileUploadResumeExecutionResponseDTO;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.plan.execution.PlanExecutionResourceConstants;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.InputStream;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataParam;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Api("input-file")
@Path("/input-file")
@PipelineServiceAuth
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Pipeline",
    description = "This contains pipeline APIs for files as provided as runtime input during pipeline execution")
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
public interface InputFileResource {
  @DELETE
  @ApiOperation(value = "Deletes a file uploaded at runtime to GCS", nickname = "deleteFile")
  @Operation(operationId = "deleteFile", summary = "Deletes a file uploaded at runtime to GCS",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Deletes a file uploaded at runtime to GCS.")
      })
  @Hidden
  ResponseDTO<FileDeleteResponseDTO>
  deleteFile(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
                 required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId,
      @Parameter(description = NGCommonEntityConstants.NODE_KEY_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.NODE_KEY) String nodeExecutionId,
      @Parameter(required = true) @QueryParam(NGCommonEntityConstants.FILE_IDENTIFIER_KEY) String fileName);

  @GET
  @Path("/metadata/{nodeExecutionId}")
  @ApiOperation(value = "Gets a file metadata of a uploaded file", nickname = "getFileMetadata")
  @Operation(operationId = "getFileMetadata", description = "Returns a file metadata of a uploaded file",
      summary = "Fetch a file metadata of a uploaded file for given",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a file metadata of a uploaded file")
      })
  @Hidden
  ResponseDTO<FileMetadataResponseDTO>
  getFileMetadata(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                  @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId,
      @Parameter(description = NGCommonEntityConstants.NODE_KEY_PARAM_MESSAGE, required = true) @NotNull @PathParam(
          NGCommonEntityConstants.NODE_KEY) String nodeExecutionId);

  @GET
  @Path("/file/{planExecutionId}")
  @ApiOperation(value = "Get file from GCS", nickname = "getFile")
  @Operation(operationId = "getFile",
      description =
          "Returns a file uploaded or filtered based on the fileIdentifier provided for a given nodeExecutionId",
      summary = "Returns a file uploaded or filtered based on the fileIdentifier provided for a given nodeExecutionId",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Returns a file uploaded or filtered based on the fileIdentifier provided for a given planExecutionId")
      })
  Response
  getFile(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
              required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE,
          required = true) @NotNull @PathParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId,
      @Parameter(description = NGCommonEntityConstants.NODE_KEY_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.NODE_KEY) String nodeExecutionId,
      @QueryParam("fileName") @NotNull String fileName);

  @POST
  @Path("resume-execution/{nodeExecutionId}")
  @ApiOperation(value = "Submits the step after providing all the file evidence, and resumes execution",
      nickname = "resumeExecution")
  @Operation(operationId = "resumeExecution",
      summary = "Submits the step after providing all the file evidence, and resumes execution",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Submits the step after providing all the file evidence")
      })
  @Hidden
  ResponseDTO<FileUploadResumeExecutionResponseDTO>
  resumeExecution(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull
                  @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId,
      @Parameter(description = NGCommonEntityConstants.NODE_KEY, required = true) @NotNull @PathParam(
          NGCommonEntityConstants.NODE_KEY) String nodeExecutionId);

  @POST
  @Consumes(MULTIPART_FORM_DATA)
  @Path("/upload/{nodeExecutionId}")
  @ApiOperation(value = "Uploads a file uploaded at runtime to GCS", nickname = "uploadFile")
  @Operation(operationId = "uploadFile", summary = "Uploads a file uploaded at runtime to GCS",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Uploads a file uploaded at runtime to GCS.")
      })
  @Hidden
  ResponseDTO<FileUploadResponseDTO>
  uploadFile(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
                 required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE,
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.PLAN_KEY) String planExecutionId,
      @Parameter(description = PipelineResourceConstants.ORIGINAL_EXECUTION_ID_PARAM_MESSAGE,
          required = true) @NotNull @PathParam(NGCommonEntityConstants.NODE_KEY) String nodeExecutionId,
      @Parameter(required = true) @QueryParam(NGCommonEntityConstants.FILE_IDENTIFIER_KEY) String fileName,
      @Parameter(description = FILE_CONTENT_MESSAGE) @FormDataParam("content") InputStream content);

  @GET
  @Path("/download-file")
  @ApiOperation(value = "Download file from GCS using filePath", nickname = "downloadFileUsingFilePath")
  @Operation(operationId = "downloadFileUsingFilePath", description = "Download file from GCS using filePath",
      summary = "Download file from GCS using filePath",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Download file from GCS using filePath")
      })
  Response
  downloadFileUsingFilePath(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                            @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = "filePath", required = true) @NotNull @QueryParam("filePath") String filePath);
}
