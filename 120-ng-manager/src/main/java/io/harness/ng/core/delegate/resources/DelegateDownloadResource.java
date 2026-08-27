/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.resources;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateDownloadRequest;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import org.hibernate.validator.constraints.NotEmpty;

@Api("download-delegates")
@Path("/download-delegates")
@Produces("application/json")
@Consumes({"application/json"})
@OwnedBy(HarnessTeam.DEL)
@Tag(name = "Delegate Download Resource", description = "Contains APIs related to Downloading Delegates")
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
public class DelegateDownloadResource {
  public DelegateDownloadResource() {}

  @POST
  @Path("/kubernetes")
  @ApiOperation(value = "Downloads a kubernetes delegate yaml file.", nickname = "downloadKubernetesDelegateYaml")
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @Operation(operationId = "downloadKubernetesDelegateYaml", summary = "Downloads a kubernetes delegate yaml file.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Successfully generated and downloaded kubernetes delegate yaml file")
      })
  public Response
  downloadKubernetesDelegate(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                 NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(required = true, description = "Parameters needed for downloading kubernetes delegate yaml")
      DelegateDownloadRequest delegateDownloadRequest) throws IOException {
    return Response.ok().build();
  }

  @POST
  @Path("/docker")
  @ApiOperation(value = "Downloads a docker delegate yaml file.", nickname = "downloadDockerDelegateYaml")
  @Timed
  @ExceptionMetered
  @ResponseMetered
  @Operation(operationId = "downloadDockerDelegateYaml", summary = "Downloads a docker delegate yaml file.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "Successfully generated and downloaded docker delegate yaml file")
      })
  public Response
  downloadDockerDelegate(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(required = true, description = "Parameters needed for downloading docker delegate yaml")
      DelegateDownloadRequest delegateDownloadRequest) throws IOException {
    return Response.ok().build();
  }
}
