/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.resources;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.beans.TeamHierarchyResult;
import io.harness.idp.catalog.service.TeamHierarchyService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.TeamsApi;
import io.harness.utils.ApiUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Timed
@ResponseMetered
public class TeamsApiImpl implements TeamsApi {
  private static final String TEAM_HIERARCHY_FLOW_LOG = "[teamHierarchy flow]";
  private TeamHierarchyService teamHierarchyService;

  @Override
  public Response getTeamHierarchy(@AccountIdentifier String harnessAccount, String scopes, Boolean includeChildScopes,
      Boolean custom, Integer page, Integer limit, String sort, String searchTerm) {
    log.info("{} Received team hierarchy request account={} scopes={} includeChildScopes={}", TEAM_HIERARCHY_FLOW_LOG,
        harnessAccount, scopes, includeChildScopes);
    try {
      TeamHierarchyResult result = teamHierarchyService.getTeamHierarchy(
          harnessAccount, scopes, includeChildScopes, page, limit, sort, searchTerm, custom);
      Response.ResponseBuilder responseBuilder = Response.ok();
      Response.ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(
          responseBuilder, result.getTotalElements(), result.getPageNumber(), result.getPageSize());
      return responseBuilderWithLinks.entity(result.getNodes()).build();
    } catch (Exception e) {
      log.error("{} Team hierarchy request failed account={} scopes={}. Error={}", TEAM_HIERARCHY_FLOW_LOG,
          harnessAccount, scopes, e.getMessage(), e);
      throw e;
    }
  }

  @Override
  public Response getTeamOwnedEntities(String scope, String identifier, @AccountIdentifier String harnessAccount,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, Integer page,
      @Max(100L) Integer limit, String sort, String searchTerm, Boolean includeChildTeams) {
    int pageLimit = (limit == null || limit == -1) ? 10 : limit;
    GetEntitiesDTO getEntitiesDTO =
        teamHierarchyService.getTeamOwnedEntities(harnessAccount, orgIdentifier, projectIdentifier,
            "group"
                + ":" + scope + "/" + identifier,
            Boolean.TRUE.equals(includeChildTeams), page, pageLimit, sort, searchTerm);
    Response.ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(
        Response.ok(), getEntitiesDTO.getTotalElements(), getEntitiesDTO.getPageNumber(), pageLimit);
    return responseBuilderWithLinks.entity(getEntitiesDTO.getEntityResponses()).build();
  }
}
