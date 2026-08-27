/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import io.harness.pms.accountoverrides.AccountOverridesConfigResponseDTO;
import io.harness.pms.accountoverrides.AccountOverridesCreateRequestDTO;
import io.harness.pms.accountoverrides.AccountOverridesCreateResponseDTO;
import io.harness.pms.accountoverrides.AccountOverridesUpdateRequestDTO;
import io.harness.pms.accountoverrides.AccountOverridesUpdateResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@PipelineServiceAuth
@Api("admin")
@Path("/admin")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Pipeline", description = "This contains pipeline APIs that are exposed on the Admin")
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
public interface PipelineAdminResource {
  @PUT
  @Path("/block-execution")
  @ApiOperation(value = "Block pipeline execution for various scopes including accountId, projectId, orgId or "
          + "pipelineId for a specific account, accessible via admin panel",
      nickname = "blockExecutionPipeline")
  @Operation(operationId = "blockExecutionPipeline",
      summary =
          "Blocks the pipeline execution for various scopes including accountId, projectId, orgId or pipelineId for a "
          + "specific account. This api is intended for internal use only and can be accessed through the admin panel.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Blocks the pipeline execution for various scopes including accountId, projectId, orgId or "
                + "pipelineId for a specific account. This api is intended for internal use only and can be "
                + "accessed through the admin panel.")
      })
  @Hidden
  ResponseDTO<BlockExecutionResponseDTO>
  blockExecutionPipeline(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                         @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier);

  @DELETE
  @Path("/unblock-execution")
  @Operation(operationId = "unblockExecutionPipeline",
      summary = "Unblocks the pipeline execution for various scopes including accountId, projectId, orgId or "
          + "pipelineId for a specific account. This api is intended for internal use only and can be accessed "
          + "through the admin panel.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Unblocks the pipeline execution for various scopes including accountId, projectId, orgId or "
                + "pipelineId for a specific account. This api is intended for internal use only and can be "
                + "accessed through the admin panel.")
      })
  @ApiOperation(value = "Unblock pipeline execution for various scopes including accountId, projectId, orgId or "
          + "pipelineId for a specific account, accessible via admin panel",
      nickname = "unblockExecutionPipeline")
  @Hidden
  ResponseDTO<BlockExecutionResponseDTO>
  unblockExecutionPipeline(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true)
                           @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier);

  @GET
  @Path("/overrides")
  @ApiOperation(value = "Get override configs for an account", nickname = "getDataRetentionConfig")
  @Operation(operationId = "getDataRetentionConfig", description = "Get overridden configs and limits for an account",
      summary = "Get data retention overridden configs and limits for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the list of data retention overridden configs and limits for an account")
      })
  @Hidden
  ResponseDTO<AccountOverridesConfigResponseDTO>
  getPipelineDataRetentionConfig(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier);

  @POST
  @Path("/account-overrides")
  @ApiOperation(value = "Create account overrides", nickname = "createAccountOverrides")
  @Operation(operationId = "createAccountOverrides", description = "Create account overrides",
      summary = "Create account overrides",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the account overrides")
      })
  @Hidden
  ResponseDTO<AccountOverridesCreateResponseDTO>
  createAccountOverrides(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true,
          description = "Create Account Overrides body") @NotNull AccountOverridesCreateRequestDTO createRequest);

  @PUT
  @Path("/account-overrides")
  @ApiOperation(value = "Update account overrides", nickname = "updateAccountOverrides")
  @Operation(operationId = "updateAccountOverrides", description = "Update account overrides",
      summary = "Update account overrides",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the account overrides")
      })
  @Hidden
  ResponseDTO<AccountOverridesUpdateResponseDTO>
  updateAccountOverrides(
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true,
          description = "Update Account Overrides body") @NotNull AccountOverridesUpdateRequestDTO updateRequest);

  @POST
  @Path("/replay-node-executions")
  @ApiOperation(value = "Replay node executions", nickname = "replayNodeExecutionEvents")
  @Operation(operationId = "replayNodeExecutionEvents", description = "Replay node executions",
      summary = "Replay node executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return the triggered ammount")
      })
  @Hidden
  ResponseDTO<Void>
  replayNodeExecutionEvents(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.MODULE_TYPE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.MODULE_TYPE) String module,
      @Parameter(description = PipelineResourceConstants.START_TIME_EPOCH_PARAM_MESSAGE) @QueryParam(
          "startTs") long startTs,
      @Parameter(description = PipelineResourceConstants.END_TIME_EPOCH_PARAM_MESSAGE) @QueryParam("endTs") long endTs);

  @POST
  @Path("/force-abort-executions")
  @ApiOperation(value = "Force abort pipeline executions", nickname = "forceAbortPipelineExecutions")
  @Operation(operationId = "forceAbortPipelineExecutions", description = "Force abort pipeline executions",
      summary = "Force abort pipeline executions",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Return summary of force abort")
      })
  @Hidden
  ResponseDTO<ForceAbortExecutionsResponseDTO>
  forceAbortExecutions(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(required = true,
          description = "Force abort plan executions request body") @NotNull ForceAbortExecutionsRequestDTO request);

  @POST
  @Path("/step-concurrency/recompute")
  @ApiOperation(value = "Recompute step-concurrency counters", nickname = "recomputeStepConcurrencyCounters")
  @Operation(operationId = "recomputeStepConcurrencyCounters",
      description = "Recompute the step-concurrency Redis counters from Mongo, bypassing the leader lock held by "
          + "the daily rebuild job",
      summary = "Recompute step-concurrency counters",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns success response")
      })
  @Hidden
  ResponseDTO<Void>
  recomputeStepConcurrencyCounters();

  @GET
  @Path("/step-concurrency/counter")
  @ApiOperation(value = "Get a step-concurrency counter value", nickname = "getStepConcurrencyCounter")
  @Operation(operationId = "getStepConcurrencyCounter",
      description = "Read the current Redis step-concurrency counter value for the cluster, for a given account, "
          + "or for every account with a live counter (scope=account with no accountIdentifier)",
      summary = "Get a step-concurrency counter value",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the counter value")
      })
  @Hidden
  ResponseDTO<StepConcurrencyCounterResponseDTO>
  getStepConcurrencyCounter(@Parameter(description = "Counter scope: cluster or account",
                                required = true) @NotNull @QueryParam("scope") String scope,
      @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  @POST
  @Path("/plan-concurrency/recompute")
  @ApiOperation(value = "Recompute plan-concurrency counters", nickname = "recomputePlanConcurrencyCounters")
  @Operation(operationId = "recomputePlanConcurrencyCounters",
      description = "Recompute the per-project plan (pipeline execution) concurrency Redis counters from Mongo, "
          + "bypassing the leader lock held by the daily rebuild job",
      summary = "Recompute plan-concurrency counters",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns success response")
      })
  @Hidden
  ResponseDTO<Void>
  recomputePlanConcurrencyCounters();

  @GET
  @Path("/plan-concurrency/counters")
  @ApiOperation(
      value = "Get per-project plan-concurrency counters for an account", nickname = "getPlanConcurrencyCounters")
  @Operation(operationId = "getPlanConcurrencyCounters",
      description = "Read the current Redis plan (pipeline execution) concurrency counters for an account: the "
          + "authoritative per-account count plus the per-project counters for every project in the account with a "
          + "live counter",
      summary = "Get per-project plan-concurrency counters for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the counters")
      })
  @Hidden
  ResponseDTO<PlanConcurrencyCounterResponseDTO>
  getPlanConcurrencyCounters(@Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE,
      required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  @DELETE
  @Path("/feature-flag-cache")
  @ApiOperation(value = "Clear feature flag cache for account", nickname = "clearFeatureFlagCacheForAccount")
  @Operation(operationId = "clearFeatureFlagCacheForAccount",
      description = "Clear feature flag cache entries for a specific account",
      summary = "Clear feature flag cache for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns success response")
      })
  @Hidden
  ResponseDTO<String>
  clearFeatureFlagCacheForAccount(@QueryParam("accountIdentifier") String accountIdentifier,
      @RequestBody(required = true) @NotNull @Valid FeatureFlagCacheClearRequest request);
}
