/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.aiagent.resources;

import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.ACCESS_CONNECTOR_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.CONNECTOR;
import static io.harness.pms.rbac.NGResourceType.SERVICE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.ng.core.aiagent.dto.AgentDiscoverRequestDTO;
import io.harness.ng.core.aiagent.dto.AgentDiscoverResponseDTO;
import io.harness.ng.core.aiagent.dto.AgentImportRequestDTO;
import io.harness.ng.core.aiagent.dto.AgentImportResponseDTO;
import io.harness.ng.core.aiagent.imports.AiAgentImportService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("/v1/cd/aiagent")
@Path("/v1/cd/aiagent")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@NextGenManagerAuth
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AiAgentImportResource {
  private final AiAgentImportService aiAgentImportService;
  private final AccessControlClient accessControlClient;

  @POST
  @Path("/discover")
  @ApiOperation(value = "Discover cloud AI agents", nickname = "discoverAiAgents")
  public ResponseDTO<AgentDiscoverResponseDTO> discover(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Valid @NotNull AgentDiscoverRequestDTO request) {
    log.info("Discovering AI agents for account={}, org={}, project={}, platform={}", accountId, orgId, projectId,
        request.getPlatform());
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, orgId, projectId), Resource.of(SERVICE, null), SERVICE_VIEW_PERMISSION);
    // Discovery uses the connector's credentials to call the cloud provider, so the caller must
    // also have access to the referenced connector — not just service permissions.
    checkConnectorAccess(accountId, orgId, projectId, request.getConnectorRef());
    return ResponseDTO.newResponse(aiAgentImportService.discover(accountId, orgId, projectId, request));
  }

  @POST
  @Path("/import")
  @ApiOperation(value = "Import a cloud AI agent as a service", nickname = "importAiAgent")
  public ResponseDTO<AgentImportResponseDTO> importAgent(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectId,
      @Valid @NotNull AgentImportRequestDTO request) {
    log.info("Importing AI agent for account={}, org={}, project={}, platform={}, cloudId={}", accountId, orgId,
        projectId, request.getPlatform(), request.getCloudId());
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, orgId, projectId), Resource.of(SERVICE, null), SERVICE_CREATE_PERMISSION);
    // Import uses the connector's credentials to call the cloud provider, so the caller must
    // also have access to the referenced connector — not just service permissions.
    checkConnectorAccess(accountId, orgId, projectId, request.getConnectorRef());
    return ResponseDTO.newResponse(aiAgentImportService.importAgent(accountId, orgId, projectId, request));
  }

  // Enforces connector access at the connector's own scope (a connectorRef may be scoped at
  // account/org level via account./org. prefixes), so callers cannot wield a connector's cloud
  // credentials through this API without the connector access permission.
  private void checkConnectorAccess(String accountId, String orgId, String projectId, String connectorRef) {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgId, projectId);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(connectorIdentifierRef.getAccountIdentifier(), connectorIdentifierRef.getOrgIdentifier(),
            connectorIdentifierRef.getProjectIdentifier()),
        Resource.of(CONNECTOR, connectorIdentifierRef.getIdentifier()), ACCESS_CONNECTOR_PERMISSION);
  }
}
