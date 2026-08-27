/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.smp.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.RETURN_TO_URL;
import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.account.accesscontrol.ResourceTypes;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.smp.dto.PublicKeyDTO;
import io.harness.ng.smp.service.SMPAuthServiceImpl;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.AdminPortalAuth;
import io.harness.security.annotations.PublicApi;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import javax.validation.Valid;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

@Api("/smp/auth")
@Path("/smp/auth")
@Produces(MediaType.APPLICATION_JSON)
public class SMPAuthResource {
  @Inject SMPAuthServiceImpl smpAuthService;

  @POST
  @Path("/key-pair")
  @Timed
  @ResponseMetered
  @Produces({"application/json", "application/yaml"})
  @ApiOperation(value = "create key-pair for given account", nickname = "createKeyPair")
  @Operation(operationId = "createKeyPair", summary = "create key-pair for given account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "create key-pair for given account")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<PublicKeyDTO>
  createKeyPair(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @AccountIdentifier @NotBlank @QueryParam(
      "accountIdentifier") String accountIdentifier) {
    return ResponseDTO.newResponse(smpAuthService.generateKeyPair(accountIdentifier));
  }

  @POST
  @Path("/key-pair/rotate")
  @Timed
  @ResponseMetered
  @Produces({"application/json", "application/yaml"})
  @ApiOperation(value = "rotate key-pair for given account", nickname = "rotateKeyPair")
  @Operation(operationId = "createKeyPair", summary = "rotate key-pair for given account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "rotate key-pair for given account")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<PublicKeyDTO>
  rotateKeyPair(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @AccountIdentifier @NotBlank @QueryParam(
      "accountIdentifier") String accountIdentifier) {
    return ResponseDTO.newResponse(smpAuthService.rotateKeyPair(accountIdentifier));
  }

  @GET
  @Path("/public-key")
  @Timed
  @ResponseMetered
  @Produces({"application/json", "application/yaml"})
  @ApiOperation(value = "get public key for given account", nickname = "getPublicKey")
  @Operation(operationId = "getPublicKey", summary = "get public key for given account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "get public key for given account")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<PublicKeyDTO>
  getPublicKey(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @AccountIdentifier @NotBlank @QueryParam(
      "accountIdentifier") String accountIdentifier) {
    String publicKey = smpAuthService.getPublicKey(accountIdentifier);
    if (publicKey == null) {
      throw new NotFoundException("Public key not found for account " + accountIdentifier);
    }
    PublicKeyDTO publicKeyDTO =
        PublicKeyDTO.builder().publicKey(publicKey).accountIdentifier(accountIdentifier).build();
    return ResponseDTO.newResponse(publicKeyDTO);
  }

  @GET
  @Path("/admin/public-key/")
  @Timed
  @ResponseMetered
  @Produces({"application/json", "application/yaml"})
  @ApiOperation(value = "get public key for given account", nickname = "getPublicKeyAdminPortal")
  @Operation(operationId = "getPublicKeyAdminPortal", summary = "get public key for given account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "get public key for given account")
      })
  @AdminPortalAuth
  public RestResponse<PublicKeyDTO>
  getPublicKeyAdminPortal(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @AccountIdentifier @NotBlank @QueryParam(
      "accountIdentifier") String accountIdentifier) {
    String publicKey = smpAuthService.getPublicKey(accountIdentifier);
    if (publicKey == null) {
      throw new NotFoundException("Public key not found for account " + accountIdentifier);
    }
    PublicKeyDTO publicKeyDTO =
        PublicKeyDTO.builder().publicKey(publicKey).accountIdentifier(accountIdentifier).build();
    return new RestResponse<>(publicKeyDTO);
  }

  @POST
  @Path("/admin/public-key")
  @Timed
  @ResponseMetered
  @Produces({"application/json", "application/yaml"})
  @ApiOperation(value = "create public key for given account", nickname = "createPublicKey")
  @Operation(operationId = "createPublicKey", summary = "create public key for given account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "create public key for given account")
      })
  @AdminPortalAuth
  public RestResponse<PublicKeyDTO>
  createPublicKey(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @AccountIdentifier @NotBlank @QueryParam(
                      "accountIdentifier") String accountIdentifier,
      @RequestBody(required = true, description = "public key of SMP account") @Valid PublicKeyDTO publicKeyDTO) {
    PublicKeyDTO createdKey = smpAuthService.createPublicKey(accountIdentifier, publicKeyDTO);
    return new RestResponse<>(createdKey);
  }

  @PUT
  @Path("/admin/public-key")
  @Timed
  @ResponseMetered
  @Produces({"application/json", "application/yaml"})
  @ApiOperation(value = "update public key for given account", nickname = "updatePublicKey")
  @Operation(operationId = "updatePublicKey", summary = "update public key for given account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "update public key for given account")
      })
  @AdminPortalAuth
  public RestResponse<PublicKeyDTO>
  updatePublicKey(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @AccountIdentifier @NotBlank @QueryParam(
                      "accountIdentifier") String accountIdentifier,
      @RequestBody(required = true, description = "public key of SMP account") @Valid PublicKeyDTO publicKeyDTO) {
    PublicKeyDTO updatedKey = smpAuthService.updatePublicKey(accountIdentifier, publicKeyDTO);
    return new RestResponse<>(updatedKey);
  }

  @GET
  @Path("/zendesk/redirect")
  @Timed
  @ResponseMetered
  @Produces({"application/json", "application/yaml"})
  @ApiOperation(value = "Get Zendesk redirect URL with embedded auth token", nickname = "getZendeskRedirect")
  @Operation(operationId = "getZendeskRedirect", summary = "Get Zendesk redirect URL with embedded auth token",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns a redirect URL with embedded authentication token")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<String>
  getZendeskRedirect(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @AccountIdentifier @NotBlank @QueryParam(
                         "accountIdentifier") String accountIdentifier,
      @Parameter(description = RETURN_TO_URL) @NotBlank @QueryParam("returnTo") String returnTo) {
    String redirectUrl = smpAuthService.generateSMPZendeskRedirectUrl(accountIdentifier, returnTo);
    return ResponseDTO.newResponse(redirectUrl);
  }

  @GET
  @Path("/zendesk/sso")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Process redirect from SMP and redirect to Zendesk SSO", nickname = "generateZendeskSsoUrl")
  @Operation(operationId = "generateZendeskSsoUrl", summary = "Process redirect from SMP and redirect to Zendesk SSO",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "302", description = "Redirects to Zendesk SSO URL for authentication")
        ,
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401", description = "Invalid authentication token")
      })
  @PublicApi
  public Response
  generateZendeskSsoUrl(@QueryParam("returnTo") @NotEmpty String returnTo,
      @QueryParam("token") @NotEmpty String jwtToken, @QueryParam("accountId") @NotEmpty String accountIdentifier) {
    // First verify the token is valid
    boolean isValid = smpAuthService.verifyAuthToken(jwtToken, accountIdentifier);
    if (!isValid) {
      throw new WebApplicationException("Invalid authentication token", Response.Status.UNAUTHORIZED);
    }

    String zendeskSsoUrl = smpAuthService.generateSMPZendeskSsoUrl(returnTo, jwtToken, accountIdentifier);

    // Return HTTP 302 redirect response
    return Response.status(Response.Status.FOUND).header("Location", zendeskSsoUrl).build();
  }
}
