/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.rollback;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.rollback.BatchRollbackRequestDTO;
import io.harness.dtos.rollback.BatchRollbackResponseDTO;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO;
import io.harness.dtos.rollback.PostProdRollbackRequestDTO;
import io.harness.dtos.rollback.PostProdRollbackResponseDTO;
import io.harness.dtos.rollback.RollbackRequestDTO;
import io.harness.dtos.rollback.RollbackResponseDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@OwnedBy(HarnessTeam.CDP)
@Api("rollback")
@Path("rollback")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = io.harness.ng.core.dto.FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = io.harness.ng.core.dto.ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Rollback", description = "This contains APIs related to Post Prod Rollback of specific service")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class PostProdRollbackResource {
  @Inject PostProdRollbackService postProdRollbackService;
  @POST
  @Path("/trigger")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Trigger the post-prod-rollback for the given instanceUuid", nickname = "triggerRollback")
  @Operation(operationId = "triggerRollback", summary = "Trigger the rollback for specific service",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return status of triggered rollback")
      })
  public ResponseDTO<PostProdRollbackResponseDTO>
  triggerRollback(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @NotNull PostProdRollbackRequestDTO requestDTO) {
    return ResponseDTO.newResponse(postProdRollbackService.triggerRollback(accountIdentifier, orgIdentifier,
        projectIdentifier, requestDTO.getInstanceKey(), requestDTO.getInfrastructureMappingId()));
  }
  @POST
  @Path("/check")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Check if the post-prod-rollback is possible for the given instanceUuid",
      nickname = "checkIfInstanceCanBeRolledBack")
  @Operation(operationId = "checkIfInstanceCanBeRolledBack",
      summary = "Verification for rollback eligibility for service",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return details on whether rollback is allowed")
      })
  public ResponseDTO<PostProdRollbackCheckDTO>
  checkIfInstanceCanBeRolledBack(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @NotNull @Valid PostProdRollbackRequestDTO requestDTO) {
    return ResponseDTO.newResponse(postProdRollbackService.checkIfRollbackAllowed(accountIdentifier, orgIdentifier,
        projectIdentifier, requestDTO.getInstanceKey(), requestDTO.getInfrastructureMappingId()));
  }

  @POST
  @Path("/trigger/v2")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Trigger the post-prod-rollback", nickname = "triggerRollbackV2")
  @Operation(operationId = "triggerRollbackV2", summary = "Trigger the rollback for specific service to an environment",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return status of triggered rollback")
      })
  public ResponseDTO<RollbackResponseDTO>
  triggerRollbackV2(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @NotNull RollbackRequestDTO requestDTO) {
    return ResponseDTO.newResponse(
        postProdRollbackService.triggerRollbackV2(accountIdentifier, orgIdentifier, projectIdentifier, requestDTO));
  }

  @POST
  @Path("/trigger/v3")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Trigger post-prod-rollback for multiple service/env/infra targets in a single call",
      nickname = "triggerRollbackV3")
  @Operation(operationId = "triggerRollbackV3",
      summary = "Trigger rollback for multiple targets using the same semantics as v2 (latest pipeline execution "
          + "group per target). Failures are reported per target without failing the entire batch.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return per-target rollback status")
      })
  public ResponseDTO<BatchRollbackResponseDTO>
  triggerRollbackV3(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Body @NotNull @Valid BatchRollbackRequestDTO requestDTO) {
    return ResponseDTO.newResponse(
        postProdRollbackService.triggerRollbackV3(accountIdentifier, orgIdentifier, projectIdentifier, requestDTO));
  }
}
