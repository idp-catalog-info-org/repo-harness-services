/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.aisre.resource;

import static io.harness.annotations.dev.HarnessTeam.CHAOS;
import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_VIEW;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.aisre.AiSrePipelineClient;
import io.harness.aisre.AiSrePipelineContextData;
import io.harness.aisre.IncidentTypeList;
import io.harness.aisre.IncidentTypeMetadata;
import io.harness.aisre.IncidentTypeSummary;
import io.harness.annotations.dev.OwnedBy;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Read-only helper endpoints that back the AISRE_CreateIncident step's dynamic form. Proxies to the
 * AI SRE (transposit) service to list incident types and fetch a type's field schema.
 */
@OwnedBy(CHAOS)
@Api("/aisre/incident-types")
@Path("/aisre/incident-types")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@PipelineServiceAuth
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AisreIncidentTypesResource {
  private static final String PIPELINE_RESOURCE_TYPE = "PIPELINE";

  private final AiSrePipelineClient aiSrePipelineClient;
  private final AccessControlClient accessControlClient;

  @GET
  @ApiOperation(value = "List AI SRE incident types", nickname = "getAisreIncidentTypes")
  @Operation(operationId = "getAisreIncidentTypes", summary = "Lists the AI SRE incident types for the given scope")
  @Hidden
  public ResponseDTO<List<IncidentTypeSummary>> listIncidentTypes(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId) throws Exception {
    checkPipelineViewAccess(accountId, orgId, projectId);
    // executeWithExceptions: SafeHttpCall.execute returns null on 404/5xx, which we would otherwise
    // flatten to an empty list and look like "no incident types" (SUCCESS + data:[]).
    AiSrePipelineContextData.setTargetScope(accountId, orgId, projectId);
    try {
      IncidentTypeList result =
          SafeHttpCall.executeWithExceptions(aiSrePipelineClient.listIncidentTypes(accountId, orgId, projectId));
      return ResponseDTO.newResponse(result == null ? Collections.emptyList() : result.getEntities());
    } finally {
      AiSrePipelineContextData.clear();
    }
  }

  @GET
  @Path("/{shortId}/metadata")
  @ApiOperation(value = "Get AI SRE incident type field schema", nickname = "getAisreIncidentTypeMetadata")
  @Operation(operationId = "getAisreIncidentTypeMetadata",
      summary = "Returns the field schema for a single AI SRE incident type")
  @Hidden
  public ResponseDTO<IncidentTypeMetadata>
  getIncidentTypeMetadata(@PathParam("shortId") String shortId,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId) throws Exception {
    checkPipelineViewAccess(accountId, orgId, projectId);
    AiSrePipelineContextData.setTargetScope(accountId, orgId, projectId);
    try {
      return ResponseDTO.newResponse(SafeHttpCall.executeWithExceptions(
          aiSrePipelineClient.getIncidentTypeMetadata(shortId, accountId, orgId, projectId)));
    } finally {
      AiSrePipelineContextData.clear();
    }
  }

  private void checkPipelineViewAccess(String accountId, String orgId, String projectId) {
    // Project-scoped check: the AI SRE client always authenticates as PipelineService, so authorize
    // the caller against the requested account/org/project before proxying.
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, orgId, projectId), Resource.of(PIPELINE_RESOURCE_TYPE, null), PIPELINE_VIEW);
  }
}
