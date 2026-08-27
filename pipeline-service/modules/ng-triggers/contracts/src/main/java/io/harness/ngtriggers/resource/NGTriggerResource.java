/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.resource;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static javax.ws.rs.core.HttpHeaders.IF_MATCH;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.PollingTriggerStatusUpdateDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.BulkTriggersRequestDTO;
import io.harness.ngtriggers.beans.dto.BulkTriggersResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerCatalogDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerEventHistoryDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerYamlRequestDTO;
import io.harness.ngtriggers.beans.dto.NGTriggersFilterPropertiesDTO;
import io.harness.ngtriggers.beans.dto.TriggerYamlDiffDTO;
import io.harness.ngtriggers.beans.source.GitMoveOperationType;
import io.harness.ngtriggers.beans.source.TriggerUpdateCount;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import org.springframework.data.domain.Page;
import retrofit2.http.Body;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Api("triggers")
@Path("triggers")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@Tag(name = "Triggers", description = "This contains APIs related to Triggers.")
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
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@PipelineServiceAuth
@OwnedBy(PIPELINE)
public interface NGTriggerResource {
  @POST
  @Operation(operationId = "createTrigger", summary = "Creates Trigger for triggering target pipeline identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns details of the created Trigger.")
      })
  @ApiOperation(value = "Create Trigger", nickname = "createTrigger")
  @Timed
  @ResponseMetered
  ResponseDTO<NGTriggerResponseDTO>
  create(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline") @NotNull @QueryParam(
          "targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @NotNull @RequestBody(required = true, description = "Triggers YAML",
          content =
          {
            @Content(examples = @ExampleObject(name = "Create", summary = "Sample Create Trigger YAML",
                         value = "trigger:\n  name: mytrigger\n  identifier: mytrigger\n  enabled: true\n  ...",
                         description = "Sample Triggers YAML"))
          }) String yaml,
      @QueryParam("ignoreError") @DefaultValue("false") boolean ignoreError,
      @QueryParam("withServiceV2") @DefaultValue("false") boolean withServiceV2, @Context ScopeInfo scopeInfo);

  @POST
  @Path("/v2")
  @Operation(operationId = "createTriggerV2",
      summary = "Creates Trigger with executor identity in request body (v2 API).",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns details of the created Trigger.")
      })
  @ApiOperation(value = "Create Trigger V2", nickname = "createTriggerV2")
  @Timed
  @ResponseMetered
  ResponseDTO<NGTriggerResponseDTO>
  createV2(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline") @NotNull @QueryParam(
          "targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @NotNull @RequestBody(required = true,
          description = "Trigger YAML with executor identity. "
              + "When provided, executorInfo requires 'uuid' and 'type' (USER or SERVICE_ACCOUNT). "
              + "For SERVICE_ACCOUNT, also include accountIdentifier, orgIdentifier, and projectIdentifier. "
              + "Mandatory on create when enforce_executor_identity_for_triggers setting is on.",
          content =
          {
            @Content(mediaType = "application/json", schema = @Schema(implementation = NGTriggerYamlRequestDTO.class),
                examples = @ExampleObject(name = "Create", summary = "YAML plus optional executor",
                    value = "{ \"yaml\": \"...\", \"executorInfo\": { \"uuid\": \"user-id\", \"type\": \"USER\" } }"))
          }) NGTriggerYamlRequestDTO request,
      @QueryParam("ignoreError") @DefaultValue("false") boolean ignoreError,
      @QueryParam("withServiceV2") @DefaultValue("false") boolean withServiceV2, @Context ScopeInfo scopeInfo);

  @GET
  @Path("/{triggerIdentifier}")
  @Operation(operationId = "getTrigger",
      summary = "Gets the trigger by accountIdentifier, orgIdentifier, projectIdentifier, targetIdentifier and "
          + "triggerIdentifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the trigger with the accountIdentifier, orgIdentifier, projectIdentifier, "
                + "targetIdentifier and triggerIdentifier.")
      })
  @ApiOperation(value = "Gets a trigger by identifier", nickname = "getTrigger")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Timed
  @ResponseMetered
  ResponseDTO<NGTriggerResponseDTO>
  get(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline under which trigger resides") @NotNull @QueryParam(
          "targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @PathParam("triggerIdentifier") String triggerIdentifier, @Context ScopeInfo scopeInfo);

  @PUT
  @Operation(operationId = "updateTrigger", summary = "Updates trigger for pipeline with target pipeline identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated trigger")
      })
  @Path("/{triggerIdentifier}")
  @ApiOperation(value = "Update a trigger by identifier", nickname = "updateTrigger")
  @Timed
  @ResponseMetered
  ResponseDTO<NGTriggerResponseDTO>
  update(@HeaderParam(IF_MATCH) String ifMatch,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline under which trigger resides") @NotNull @QueryParam(
          "targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @PathParam("triggerIdentifier") String triggerIdentifier,
      @NotNull @RequestBody(required = true, description = "Triggers YAML",
          content =
          {
            @Content(examples = @ExampleObject(name = "Update", summary = "Sample Update Trigger YAML",
                         value = "trigger:\n  name: mytrigger\n  identifier: mytrigger\n  enabled: true\n  ...",
                         description = "Sample Triggers YAML"))
          }) String yaml,
      @QueryParam("ignoreError") @DefaultValue("false") boolean ignoreError, @Context ScopeInfo scopeInfo);

  @PUT
  @Path("/{triggerIdentifier}/v2")
  @Operation(operationId = "updateTriggerV2",
      summary = "Updates trigger with executor identity in request body (v2 API).",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated trigger")
      })
  @ApiOperation(value = "Update a trigger by identifier V2", nickname = "updateTriggerV2")
  @Timed
  @ResponseMetered
  ResponseDTO<NGTriggerResponseDTO>
  updateV2(@HeaderParam(IF_MATCH) String ifMatch,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline under which trigger resides") @NotNull @QueryParam(
          "targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @PathParam("triggerIdentifier") String triggerIdentifier,
      @NotNull @RequestBody(required = true,
          description = "Trigger YAML with executor identity. "
              + "When provided, executorInfo requires 'uuid' and 'type' (USER or SERVICE_ACCOUNT). "
              + "For SERVICE_ACCOUNT, also include accountIdentifier, orgIdentifier, and projectIdentifier. "
              + "Mandatory on create when enforce_executor_identity_for_triggers setting is on.",
          content =
          {
            @Content(mediaType = "application/json", schema = @Schema(implementation = NGTriggerYamlRequestDTO.class),
                examples = @ExampleObject(name = "Update", summary = "YAML plus optional executor",
                    value = "{ \"yaml\": \"...\", \"executorInfo\": { \"uuid\": \"user-id\", \"type\": \"USER\" } }"))
          }) NGTriggerYamlRequestDTO request,
      @QueryParam("ignoreError") @DefaultValue("false") boolean ignoreError, @Context ScopeInfo scopeInfo);

  @PUT
  @Hidden
  @Path("{triggerIdentifier}/status")
  @Operation(operationId = "updateTriggerStatus",
      summary = "Activates or deactivate trigger for pipeline with target pipeline identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = " Returns the response status.")
      })
  @ApiOperation(value = "Update a trigger's status by identifier", nickname = "updateTriggerStatus")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  ResponseDTO<Boolean>
  updateTriggerStatus(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline under which trigger resides") @NotNull @QueryParam(
          "targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @PathParam("triggerIdentifier") String triggerIdentifier, @NotNull @QueryParam("status") boolean status,
      @Context ScopeInfo scopeInfo);

  @PUT
  @Hidden
  @Path("pollingTriggerStatus")
  @Operation(operationId = "updatePollingTriggerStatus",
      summary = "Updates polling status data of a polling trigger using its signature(s).",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = " Returns the response status.")
      })
  @ApiOperation(value = "Update a trigger's polling status by signature", nickname = "updateTriggerPollingStatus")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  ResponseDTO<Boolean>
  updateTriggerPollingStatus(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @RequestBody(required = true) PollingTriggerStatusUpdateDTO statusUpdate);

  @DELETE
  @Operation(operationId = "deleteTrigger", summary = "Deletes Trigger by identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the boolean status.")
      })
  @Path("{triggerIdentifier}")
  @ApiOperation(value = "Delete a trigger by identifier", nickname = "deleteTrigger")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  @Timed
  @ResponseMetered
  ResponseDTO<Boolean> delete(@HeaderParam(IF_MATCH) String ifMatch,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline under which trigger resides.") @NotNull @QueryParam(
          "targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @PathParam("triggerIdentifier") String triggerIdentifier, @Context ScopeInfo scopeInfo);

  @GET
  @Operation(operationId = "getListForTarget",
      summary = "Gets the paginated list of triggers for accountIdentifier, orgIdentifier, projectIdentifier, "
          + "targetIdentifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the paginated list of triggers for accountIdentifier, orgIdentifier, "
                + "projectIdentifier, targetIdentifier.")
      })
  @ApiOperation(value = "Gets paginated Triggers list for target", nickname = "getTriggerListForTarget")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  ResponseDTO<PageResponse<NGTriggerDetailsResponseDTO>>
  getListForTarget(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline") @NotNull @QueryParam("targetIdentifier")
      @ResourceIdentifier String targetIdentifier, @QueryParam("filter") String filterQuery,
      @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("25") int size,
      @QueryParam("sort") List<String> sort, @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "This contains details of Trigger filters based on Trigger Types and Trigger Names ")
      @Body NGTriggersFilterPropertiesDTO filterProperties, @Context ScopeInfo scopeInfo);

  @GET
  @Operation(operationId = "getTriggerDetails",
      summary = "Fetches Trigger details for a specific accountIdentifier, orgIdentifier, projectIdentifier, "
          + "targetIdentifier, triggerIdentifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Fetches Trigger details for a specific accountIdentifier, orgIdentifier, projectIdentifier, "
                + "targetIdentifier, triggerIdentifier.")
      })
  @Path("{triggerIdentifier}/details")
  @ApiOperation(value = "Fetches Trigger details for a specific pipeline and trigger identifier, ",
      nickname = "getTriggerDetails")
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  @Timed
  @ResponseMetered
  ResponseDTO<NGTriggerDetailsResponseDTO>
  getTriggerDetails(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline") @PathParam("triggerIdentifier")
      String triggerIdentifier, @NotNull @QueryParam("targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @Context ScopeInfo scopeInfo);

  @GET
  @Operation(hidden = true, operationId = "generateWebhookToken",
      summary = "Generates random webhook token for new triggers.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns random webhook token.")
      })
  @Path("regenerateToken")
  @ApiOperation(value = "Regenerate webhook token", nickname = "generateWebhookToken")
  @Timed
  @ExceptionMetered
  RestResponse<String>
  generateWebhookToken();

  @GET
  @Path("catalog")
  @ApiOperation(value = "Get Trigger catalog", nickname = "getTriggerCatalog")
  @Operation(operationId = "getTriggerCatalog", summary = "Lists all Triggers",
      description = "Lists all the Triggers for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Trigger catalogue response")
      })
  @ScopeInfoResolutionExemptedApi
  ResponseDTO<NGTriggerCatalogDTO>
  getTriggerCatalog(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  /**
   * @deprecated
   * Use getTriggerEventHistory from NGTriggerEventHistoryResource
   */
  @GET
  @Path("{triggerIdentifier}/eventHistory")
  @ApiOperation(value = "Get Trigger event history", nickname = "triggerEventHistory")
  @Operation(operationId = "triggerEventHistory", summary = "Get event history for a trigger",
      description = "Get event history for a trigger",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Trigger catalogue response")
      })
  @Deprecated
  ResponseDTO<Page<NGTriggerEventHistoryDTO>>
  getTriggerEventHistory(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline under which trigger resides") @NotNull @QueryParam(
          "targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @PathParam("triggerIdentifier") String triggerIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_SEARCH_TERM_PARAM_MESSAGE) @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("10") int size,
      @Parameter(description = NGCommonEntityConstants.SORT_PARAM_MESSAGE) @QueryParam("sort") List<String> sort,
      @Context ScopeInfo scopeInfo);

  @GET
  @Path("{triggerIdentifier}/triggerReconciliationYamlDiff")
  @ApiOperation(hidden = true, value = "This contains data for trigger YAML reconciliation",
      nickname = "triggerReconciliationYamlDiff")
  @Hidden
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  ResponseDTO<TriggerYamlDiffDTO>
  getTriggerReconciliationYamlDiff(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline under which trigger resides") @NotNull @QueryParam(
          "targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @PathParam("triggerIdentifier") String triggerIdentifier, @Context ScopeInfo scopeInfo);

  @GET
  @Path("/dummy-NGTriggerConfigV2-api")
  @ApiOperation(value = "This is dummy api to expose NGTriggerConfigV2", nickname = "NGTriggerConfigV2")
  @Hidden
  ResponseDTO<NGTriggerConfigV2> getNGTriggerConfigV2();

  @PUT
  @Hidden
  @Path("/update-branch-name")
  @InternalApi
  ResponseDTO<TriggerUpdateCount> updateBranchName(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Identifier of the target pipeline under which trigger resides") @NotNull @QueryParam(
          "targetIdentifier") @ResourceIdentifier String targetIdentifier,
      @QueryParam("operationType") GitMoveOperationType operationType,
      @QueryParam("pipelineBranchName") String pipelineBranchName, @Context ScopeInfo scopeInfo);

  @PATCH
  @Hidden
  @Path("/bulk-toggle")
  @InternalApi
  @ScopeInfoResolutionExemptedApi
  ResponseDTO<BulkTriggersResponseDTO> bulkToggleTriggers(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @NotNull @Body BulkTriggersRequestDTO bulkTriggersRequestDTO);
}
