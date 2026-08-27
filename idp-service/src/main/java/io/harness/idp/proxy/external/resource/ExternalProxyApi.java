/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.external.resource;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/v1/external-proxy/{endpoint:.+}")
@OwnedBy(HarnessTeam.IDP)
@Tag(name = "ExternalProxy", description = "Proxy API for forwarding requests to external services")
public interface ExternalProxyApi {
  @GET
  @Produces({"application/json", "text/plain", "*/*"})
  @Operation(operationId = "getExternalProxy", summary = "Forward GET request to external service",
      description = "Proxies GET requests to configured external endpoints with header filtering and authentication",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"ExternalProxy"})
  @ApiResponse(responseCode = "200", description = "Successful response from external service")
  @ApiResponse(responseCode = "400", description = "Bad request - endpoint not configured or method not allowed")
  @ApiResponse(responseCode = "403", description = "Forbidden - authentication failed")
  @ApiResponse(responseCode = "404", description = "Endpoint configuration not found")
  @ApiResponse(responseCode = "500", description = "Internal server error during proxy request")
  Response
  getProxy(@Context UriInfo uriInfo, @Context HttpHeaders headers,
      @PathParam("endpoint") @Parameter(description = "The proxy endpoint path (e.g., my-api/users)") String endpoint,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods "
              + "other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);

  @POST
  @Consumes({"application/json", "text/plain", "*/*"})
  @Produces({"application/json", "text/plain", "*/*"})
  @Operation(operationId = "postExternalProxy", summary = "Forward POST request to external service",
      description = "Proxies POST requests to configured external endpoints with header filtering and authentication",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"ExternalProxy"})
  @ApiResponse(responseCode = "200", description = "Successful response from external service")
  @ApiResponse(responseCode = "201", description = "Resource created on external service")
  @ApiResponse(responseCode = "400", description = "Bad request - endpoint not configured or method not allowed")
  @ApiResponse(responseCode = "403", description = "Forbidden - authentication failed")
  @ApiResponse(responseCode = "404", description = "Endpoint configuration not found")
  @ApiResponse(responseCode = "500", description = "Internal server error during proxy request")
  Response
  postProxy(@Context UriInfo uriInfo, @Context HttpHeaders headers,
      @PathParam("endpoint") @Parameter(description = "The proxy endpoint path (e.g., my-api/users)") String endpoint,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods "
              + "other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount,
      String body);

  @PUT
  @Consumes({"application/json", "text/plain", "*/*"})
  @Produces({"application/json", "text/plain", "*/*"})
  @Operation(operationId = "putExternalProxy", summary = "Forward PUT request to external service",
      description = "Proxies PUT requests to configured external endpoints with header filtering and authentication",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"ExternalProxy"})
  @ApiResponse(responseCode = "200", description = "Successful response from external service")
  @ApiResponse(responseCode = "400", description = "Bad request - endpoint not configured or method not allowed")
  @ApiResponse(responseCode = "403", description = "Forbidden - authentication failed")
  @ApiResponse(responseCode = "404", description = "Endpoint configuration not found")
  @ApiResponse(responseCode = "500", description = "Internal server error during proxy request")
  Response
  putProxy(@Context UriInfo uriInfo, @Context HttpHeaders headers,
      @PathParam("endpoint") @Parameter(description = "The proxy endpoint path (e.g., my-api/users)") String endpoint,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods "
              + "other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount,
      String body);

  @PATCH
  @Consumes({"application/json", "text/plain", "*/*"})
  @Produces({"application/json", "text/plain", "*/*"})
  @Operation(operationId = "patchExternalProxy", summary = "Forward PATCH request to external service",
      description = "Proxies PATCH requests to configured external endpoints with header filtering and authentication",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"ExternalProxy"})
  @ApiResponse(responseCode = "200", description = "Successful response from external service")
  @ApiResponse(responseCode = "400", description = "Bad request - endpoint not configured or method not allowed")
  @ApiResponse(responseCode = "403", description = "Forbidden - authentication failed")
  @ApiResponse(responseCode = "404", description = "Endpoint configuration not found")
  @ApiResponse(responseCode = "500", description = "Internal server error during proxy request")
  Response
  patchProxy(@Context UriInfo uriInfo, @Context HttpHeaders headers,
      @PathParam("endpoint") @Parameter(description = "The proxy endpoint path (e.g., my-api/users)") String endpoint,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods "
              + "other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount,
      String body);

  @DELETE
  @Produces({"application/json", "text/plain", "*/*"})
  @Operation(operationId = "deleteExternalProxy", summary = "Forward DELETE request to external service",
      description = "Proxies DELETE requests to configured external endpoints with header filtering and authentication",
      security = { @SecurityRequirement(name = "x-api-key") }, tags = {"ExternalProxy"})
  @ApiResponse(responseCode = "200", description = "Successful response from external service")
  @ApiResponse(responseCode = "204", description = "No content - resource deleted on external service")
  @ApiResponse(responseCode = "400", description = "Bad request - endpoint not configured or method not allowed")
  @ApiResponse(responseCode = "403", description = "Forbidden - authentication failed")
  @ApiResponse(responseCode = "404", description = "Endpoint configuration not found")
  @ApiResponse(responseCode = "500", description = "Internal server error during proxy request")
  Response
  deleteProxy(@Context UriInfo uriInfo, @Context HttpHeaders headers,
      @PathParam("endpoint") @Parameter(description = "The proxy endpoint path (e.g., my-api/users)") String endpoint,
      @HeaderParam("Harness-Account") @Parameter(
          description =
              "Identifier field of the account the resource is scoped to. This is required for Authorization methods "
              + "other than the x-api-key header. If you are using the x-api-key header, this can be skipped.")
      String harnessAccount);
}
