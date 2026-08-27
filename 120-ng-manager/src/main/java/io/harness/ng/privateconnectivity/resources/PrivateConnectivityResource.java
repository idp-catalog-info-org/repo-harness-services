/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.privateconnectivity.resources;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.account.accesscontrol.ResourceTypes;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityAdminResponseDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityCredentialDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityHelperCredentialDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityInternalDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityPublicStatus;
import io.harness.ng.core.privateconnectivity.PrivateConnectivityResponseDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupRequestDTO;
import io.harness.ng.core.privateconnectivity.PrivateConnectivitySetupResponseDTO;
import io.harness.ng.privateconnectivity.services.PrivateConnectivityService;
import io.harness.security.annotations.AdminPortalAuth;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
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
import java.util.Map;
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
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * REST resource for the provider-neutral Cloud Private Connectivity configuration and state
 * contract. Phase 1 bootstrap credentials are intentionally Tailscale-specific.
 */
@NextGenManagerAuth
@Api("/private-connectivity")
@Path("/private-connectivity")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Private Connectivity",
    description = "APIs for managing Harness Cloud Private Connectivity (per-account L3 private network)")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(CI)
public class PrivateConnectivityResource {
  private final PrivateConnectivityService privateConnectivityService;

  @POST
  @Path("/setup")
  @ApiOperation(value = "Setup private connectivity", nickname = "setupPrivateConnectivity")
  @Operation(operationId = "setupPrivateConnectivity",
      summary = "Provision Harness Cloud Private Connectivity for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "Setup result with an initial reusable credential; serve with Cache-Control: no-store")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public Response
  setup(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier,
      @RequestBody @Valid @NotNull PrivateConnectivitySetupRequestDTO request) {
    log.info("Private Connectivity API setup requested account={} routeCount={} domainCount={} splitDnsCount={}",
        accountIdentifier, request.getAdvertiseRoutes() == null ? 0 : request.getAdvertiseRoutes().size(),
        request.getDomains() == null ? 0 : request.getDomains().size(),
        request.getDns() == null || request.getDns().getSplitDnsDomains() == null
            ? 0
            : request.getDns().getSplitDnsDomains().size());
    PrivateConnectivitySetupResponseDTO result = privateConnectivityService.setup(accountIdentifier, request);
    Response.Status status = result.getCredential() == null ? Response.Status.OK : Response.Status.CREATED;
    log.info("Private Connectivity API setup completed account={} status={} httpStatus={} credentialIssued={}",
        accountIdentifier, result.getStatus(), status.getStatusCode(), result.getCredential() != null);
    return Response.status(status).entity(ResponseDTO.newResponse(result)).header("Cache-Control", "no-store").build();
  }

  @GET
  @ApiOperation(value = "Get private connectivity", nickname = "getPrivateConnectivity")
  @Operation(operationId = "getPrivateConnectivity",
      summary = "Get current Harness Cloud Private Connectivity state for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Current private connectivity status and config; no credentials returned")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<PrivateConnectivityResponseDTO>
  get(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier) {
    PrivateConnectivityResponseDTO response = privateConnectivityService.get(accountIdentifier);
    log.info("Private Connectivity API state read account={} status={}", accountIdentifier, response.getStatus());
    return ResponseDTO.newResponse(response);
  }

  @PUT
  @Path("/config")
  @ApiOperation(value = "Update private connectivity config", nickname = "updatePrivateConnectivityConfig")
  @Operation(operationId = "updatePrivateConnectivityConfig",
      summary = "Update network config (routes/domains/dns) for an account's private connectivity",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Updated private connectivity state") })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public Response
  updateConfig(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier,
      @RequestBody @Valid @NotNull PrivateConnectivitySetupRequestDTO request) {
    return replaceConfig(accountIdentifier, request, "customer");
  }

  @PUT
  @Path("/admin/config")
  @AdminPortalAuth
  @Hidden
  public Response updateConfigAsAdmin(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @RequestBody @Valid @NotNull PrivateConnectivitySetupRequestDTO request) {
    return replaceConfig(accountIdentifier, request, "admin");
  }

  @POST
  @Path("/reconcile")
  @AdminPortalAuth
  @Hidden
  public Response reconcile(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier) {
    log.info("Private Connectivity operator reconcile requested account={}", accountIdentifier);
    boolean accepted = privateConnectivityService.resume(accountIdentifier);
    log.info("Private Connectivity operator reconcile completed account={} accepted={}", accountIdentifier, accepted);
    return Response.accepted(ResponseDTO.newResponse(Map.of("accepted", accepted))).build();
  }

  @POST
  @Path("/credential")
  @ApiOperation(value = "Mint customer-appliance credential", nickname = "getPrivateConnectivityCredential")
  @Operation(operationId = "getPrivateConnectivityCredential",
      summary = "Mint a new reusable, preauthorized 90-day customer-appliance credential",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Join credential; serve with Cache-Control: no-store")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public Response
  getCredential(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull String accountIdentifier) {
    log.info("Private Connectivity API customer credential requested account={}", accountIdentifier);
    PrivateConnectivityCredentialDTO credential = privateConnectivityService.getCredential(accountIdentifier);
    log.info("Private Connectivity API customer credential issued account={} expiresAt={}", accountIdentifier,
        credential.getExpiresAt());
    return Response.ok(ResponseDTO.newResponse(credential)).header("Cache-Control", "no-store").build();
  }

  @POST
  @Path("/helper/credential")
  @AdminPortalAuth
  @Hidden
  public Response getHelperCredential(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE)
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier) {
    log.info("Private Connectivity operator helper credential requested account={}", accountIdentifier);
    PrivateConnectivityHelperCredentialDTO credential =
        privateConnectivityService.getHelperCredential(accountIdentifier);
    log.info("Private Connectivity operator helper credential issued account={} expiresAt={}", accountIdentifier,
        credential.getExpiresAt());
    return Response.ok(ResponseDTO.newResponse(credential)).header("Cache-Control", "no-store").build();
  }

  @DELETE
  @AdminPortalAuth
  @ApiOperation(value = "Release private connectivity", nickname = "releasePrivateConnectivity")
  @Operation(operationId = "releasePrivateConnectivity",
      summary = "Admin: release Harness Cloud Private Connectivity for an account (async)",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Accepted; release runs asynchronously via ReleaseSanitizer")
      })
  @Hidden
  public Response
  release(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier) {
    log.info("Private Connectivity operator release requested account={}", accountIdentifier);
    boolean accepted = privateConnectivityService.release(accountIdentifier);
    log.info("Private Connectivity operator release completed account={} accepted={}", accountIdentifier, accepted);
    return Response.accepted(ResponseDTO.newResponse(Map.of("accepted", accepted))).build();
  }

  @GET
  @Path("/admin")
  @AdminPortalAuth
  @Hidden
  public ResponseDTO<PrivateConnectivityAdminResponseDTO> getAdmin(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier) {
    PrivateConnectivityAdminResponseDTO state = privateConnectivityService.getAdmin(accountIdentifier);
    log.info("Private Connectivity operator state read account={} status={} networkRef={} operation={} "
            + "releasePhase={} retryCount={}",
        accountIdentifier, state.getStatus(), state.getProviderNetworkRef(), state.getOperationType(),
        state.getReleasePhase(), state.getRetryCount());
    return ResponseDTO.newResponse(state);
  }

  @GET
  @Path("/internal")
  @InternalApi
  @ApiOperation(value = "Get private connectivity internal state", nickname = "getPrivateConnectivityInternal")
  @Operation(operationId = "getPrivateConnectivityInternal",
      summary = "Internal: get private connectivity hosted-workload enrollment state",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Sanitized state for internal hosted-build enrollment decisions")
      })
  @Hidden
  public ResponseDTO<PrivateConnectivityInternalDTO>
  getInternal(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier) {
    PrivateConnectivityInternalDTO state = privateConnectivityService.getInternal(accountIdentifier);
    log.info("Private Connectivity internal state read account={} controlPlaneReady={}", accountIdentifier,
        state.getControlPlaneReady());
    return ResponseDTO.newResponse(state);
  }

  private Response replaceConfig(
      String accountIdentifier, PrivateConnectivitySetupRequestDTO request, String callerType) {
    log.info("Private Connectivity {} configuration replacement requested account={} routeCount={} domainCount={}",
        callerType, accountIdentifier, request.getAdvertiseRoutes() == null ? 0 : request.getAdvertiseRoutes().size(),
        request.getDomains() == null ? 0 : request.getDomains().size());
    PrivateConnectivityResponseDTO response = privateConnectivityService.updateConfig(accountIdentifier, request);
    log.info("Private Connectivity {} configuration replacement completed account={} status={}", callerType,
        accountIdentifier, response.getStatus());
    Response.Status status = response.getStatus() == PrivateConnectivityPublicStatus.UPDATING ? Response.Status.ACCEPTED
                                                                                              : Response.Status.OK;
    return Response.status(status).entity(ResponseDTO.newResponse(response)).build();
  }
}
