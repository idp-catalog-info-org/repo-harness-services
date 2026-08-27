/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iro;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.data.validator.EntityIdentifier;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.IdentifierRefHelper;

import clients.iromanager.remote.connectors.zoom.ZoomDeAuthPayload;
import clients.iromanager.remote.connectors.zoom.ZoomOAuthResponse;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.MDC;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.web.bind.annotation.RequestBody;

@OwnedBy(HarnessTeam.IRO)
@Api(value = "ir", hidden = true)
@Path("/")
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@Slf4j
@NextGenManagerAuth
public class IRResource {
  final ZoomService zoomService;
  @Inject final IRServiceImpl irService;
  @Inject NextGenConfiguration configuration;

  @POST
  @InternalApi
  @Path("zoom/oauth/token")
  @ApiOperation(value = "Generate Zoom OAuth Token", nickname = "generateZoomOAuthToken", hidden = true)
  public ResponseDTO<ZoomOAuthResponse> generateZoomOAuthToken(
      @Parameter(description = "Account Identifier", required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,

      @Parameter(description = "Organization Identifier") @OrgIdentifier @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @io.harness.accesscontrol.OrgIdentifier String orgIdentifier,

      @Parameter(description = "Project Identifier") @ProjectIdentifier @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @io.harness.accesscontrol.ProjectIdentifier String projectIdentifier,

      @Parameter(description = "Connector Identifier", required = true) @EntityIdentifier @QueryParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier String connectorIdentifier) {
    String path;
    if (projectIdentifier != null && !projectIdentifier.isEmpty()) {
      path = String.join("/", accountIdentifier, orgIdentifier, projectIdentifier);
    } else if (orgIdentifier != null && !orgIdentifier.isEmpty()) {
      path = String.join("/", accountIdentifier, orgIdentifier);
    } else {
      path = accountIdentifier;
    }

    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(String.join("/", path, connectorIdentifier));

    ZoomOAuthResponse zoomOAuthResponse = zoomService.generateZoomAccessToken(identifierRef, connectorIdentifier);

    return ResponseDTO.newResponse(zoomOAuthResponse);
  }

  @POST
  @InternalApi
  @Path("zoom/oauth/deauth")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "DeAuthorize Zoom OAuth Token", nickname = "deAuthZoomOAuthToken", hidden = true)
  public Response deAuthZoomOAuthToken(@RequestBody @Valid ZoomDeAuthPayload requestDTO) {
    MDC.put("zoomUserId", requestDTO.getZoomUserId());
    MDC.put("zoomAccountId", requestDTO.getZoomAccountId());

    try {
      String configuredClientId = configuration.getZoomConfig().getClientId();

      // Validate request authenticity
      if (!configuredClientId.equals(requestDTO.getZoomClientId())) {
        log.warn("Unauthorized de-auth attempt. Invalid clientId provided");
        return Response.status(Response.Status.UNAUTHORIZED).entity("Invalid client_id").build();
      }

      ZoomDeAuthorizationResult result = zoomService.deAuthorizeZoomUser(requestDTO);
      if (result.isSuccess()) {
        return Response.ok("DeAuthorization processed successfully").build();
      } else {
        log.warn("Zoom de-authorization failed: {}", result.getMessage());
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity("Processing Error: " + result.getMessage())
            .build();
      }
    } catch (Exception ex) {
      String errorId = UUID.randomUUID().toString();
      log.error("Exception during Zoom de-authorization [correlationId={}]", errorId, ex);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("An internal error occurred. correlationId: " + errorId)
          .build();
    } finally {
      MDC.remove("zoomUserId");
      MDC.remove("zoomAccountId");
    }
  }

  @POST
  @InternalApi
  @Path("/ir/delegate/execute-http-request")
  @ApiOperation(value = "Execute HTTP request via delegate", nickname = "executeHttpRequest", hidden = true)
  public ResponseDTO<HttpDelegateTaskResponse> executeHttpRequest(@RequestBody @Valid HttpDelegateTaskRequest request) {
    try {
      if (request == null) {
        throw new IllegalArgumentException("Request body cannot be null");
      }

      request.validate();
      try {
        HttpDelegateTaskResponse response = irService.httpDelegateTaskHandler(request);
        if (response == null) {
          throw new IllegalArgumentException("Response cannot be null");
        }

        if ((response.getStatusCode() >= 200) && (response.getStatusCode() < 300)) {
          return ResponseDTO.newResponse(response);
        }

        throw new InvalidRequestException(response.getErrorMessage());

      } catch (Exception e) {
        throw new UnexpectedException(e.getMessage(), e);
      }

    } catch (IllegalArgumentException ex) {
      log.warn("Validation failed for request: {}", ex.getMessage());
      throw new InvalidRequestException("Invalid request: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      log.error("Failed to execute HTTP request", ex);
      throw new UnexpectedException("Failed to execute HTTP request: " + ex.getMessage(), ex);
    }
  }

  @POST
  @InternalApi
  @Path("/ir/delegate/httpRequest")
  @ApiOperation(value = "Execute HTTP request via delegate using a Harness connector for authentication",
      nickname = "executeConnectorHttpRequest", hidden = true)
  public ResponseDTO<HttpDelegateTaskResponse>
  executeConnectorHttpRequest(@RequestBody @Valid IrConnectorHttpRequest request) {
    return executeConnectorAuthenticatedHttp(request, "ai-sre");
  }

  /**
   * SSCA-dedicated surface for the same connector-authenticated delegate HTTP handler used by
   * {@link #executeConnectorHttpRequest}. Kept as a separate route so SSCA traffic can be tracked and
   * monitored independently from IR/ai-sre callers.
   */
  @POST
  @InternalApi
  @Path("/ssca/delegate/httpRequest")
  @ApiOperation(value = "Execute HTTP request via delegate using a Harness connector for authentication (SSCA)",
      nickname = "executeSscaConnectorHttpRequest", hidden = true)
  public ResponseDTO<HttpDelegateTaskResponse>
  executeSscaConnectorHttpRequest(@RequestBody @Valid IrConnectorHttpRequest request) {
    return executeConnectorAuthenticatedHttp(request, "ssca");
  }

  private ResponseDTO<HttpDelegateTaskResponse> executeConnectorAuthenticatedHttp(
      IrConnectorHttpRequest request, String caller) {
    try {
      if (request == null) {
        throw new IllegalArgumentException("Request body cannot be null");
      }
      request.validate();
      // Thin pass-through: the target API's status code and body are returned to the caller as-is. ng-manager does not
      // interpret the response status — the caller decides how to handle non-2xx. Only request validation (400) and
      // genuine delegate/infra failures (500) are raised here.
      HttpDelegateTaskResponse response = irService.connectorHttpTaskHandler(request);
      if (response == null) {
        throw new UnexpectedException("Response cannot be null");
      }
      return ResponseDTO.newResponse(response);
    } catch (IllegalArgumentException ex) {
      log.warn("Validation failed for connector HTTP request (caller={}): {}", caller, ex.getMessage());
      throw new InvalidRequestException("Invalid request: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      log.error("Failed to execute connector HTTP request (caller={})", caller, ex);
      throw new UnexpectedException("Failed to execute HTTP request: " + ex.getMessage(), ex);
    }
  }
}