/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.customdeployment;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import javax.validation.constraints.NotBlank;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("/custom-deployment-metadata-migration")
@Path("/custom-deployment-metadata-migration")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CustomDeploymentMetadataMigrationResource {
  private final CustomDeploymentMetadataMigrationService migrationService;

  @POST
  @Path("/trigger")
  @Timed
  @ResponseMetered
  @Hidden
  @ApiOperation(value = "Trigger custom deployment template metadata migration for an account",
      nickname = "triggerCustomDeploymentMetadataMigration")
  @Operation(operationId = "triggerCustomDeploymentMetadataMigration",
      summary = "Trigger backfill of templateMetadata on remote custom deployment services and infras",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns true if migration was successfully triggered")
      },
      hidden = true)
  public ResponseDTO<String>
  triggerMigration(@NotBlank @QueryParam("accountIdentifier") String accountIdentifier,
      @QueryParam("orgIdentifier") String orgIdentifier, @QueryParam("projectIdentifier") String projectIdentifier,
      CustomDeploymentMetadataMigrationRequestDTO requestDTO) {
    String runId = migrationService.triggerMigration(accountIdentifier, orgIdentifier, projectIdentifier, requestDTO);
    log.info("[CustomDeploymentMetadataMigration] [runId={}] [STATUS] TRIGGERED: account=[{}] org=[{}] project=[{}] "
            + "entityType=[{}] mode=[{}] dryRun=[{}]",
        runId, accountIdentifier, orgIdentifier, projectIdentifier,
        requestDTO != null ? requestDTO.getEntityType() : null,
        requestDTO != null ? requestDTO.getMigrationMode() : null, requestDTO != null && requestDTO.isDryRun());
    return ResponseDTO.newResponse(runId);
  }
}
