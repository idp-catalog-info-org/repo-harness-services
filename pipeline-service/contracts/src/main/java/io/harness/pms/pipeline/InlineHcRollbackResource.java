/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;

import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@PipelineServiceAuth
@Api("/inline-hc-migration")
@Path("/inline-hc-migration")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(responseCode = "400", description = "Bad Request",
          content = @Content(schema = @Schema(implementation = FailureDTO.class)))
      ,
          @ApiResponse(responseCode = "500", description = "Internal server error",
              content = @Content(schema = @Schema(implementation = ErrorDTO.class)))
    })
@Tag(name = "InlineHC Migration", description = "APIs for InlineHC Migration operations")
@Hidden
public interface InlineHcRollbackResource {
  @POST
  @Path("/rollback")
  @Hidden
  ResponseDTO<ConsolidatedRollbackResponseDTO> rollbackInlineHCToInline(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull @Parameter(
          description = "Account identifier", required = true) @AccountIdentifier String accountIdentifier,
      @QueryParam("targetAccountIdentifier") @NotNull @Parameter(
          description = "Target account identifier", required = true) @AccountIdentifier String targetAccountIdentifier,
      @QueryParam("entityType") @NotNull @Parameter(
          description = "Entity type to migratecd ", required = true) InlineHcMigrationEntityType entityType);
}
