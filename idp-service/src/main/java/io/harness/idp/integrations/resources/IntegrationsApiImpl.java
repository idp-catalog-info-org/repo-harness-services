/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.COMMA_SEPARATOR;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.RbacConstants.IDP_INTEGRATION;
import static io.harness.idp.common.RbacConstants.IDP_INTEGRATION_CREATE;
import static io.harness.idp.common.RbacConstants.IDP_INTEGRATION_DELETE;
import static io.harness.idp.common.RbacConstants.IDP_INTEGRATION_EDIT;
import static io.harness.idp.common.RbacConstants.IDP_INTEGRATION_VIEW;

import static java.lang.String.format;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.integrations.beans.common.DiscoverEntitiesDTO;
import io.harness.idp.integrations.beans.common.ImportedEntitiesDTO;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.service.IntegrationService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.IntegrationsApi;
import io.harness.spec.server.idp.v1.model.AbstractIntegrationRequest;
import io.harness.spec.server.idp.v1.model.AbstractIntegrationResponse;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.SaveDiscoverEntitiesRequest;
import io.harness.spec.server.idp.v1.model.UnlinkIntegrationEntitiesRequest;
import io.harness.spec.server.idp.v1.model.UnlinkIntegrationEntitiesResponse;
import io.harness.utils.PageUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Timed
@ResponseMetered
public class IntegrationsApiImpl implements IntegrationsApi {
  private IntegrationService integrationService;
  private IdpCommonService idpCommonService;
  private static final Set<String> ALLOWED_PROPERTIES = Set.of("lastUpdatedAt", "status");

  @Override
  @NGAccessControlCheck(resourceType = IDP_INTEGRATION, permission = IDP_INTEGRATION_CREATE)
  public Response createIntegration(@Valid AbstractIntegrationRequest body, String integration,
      @AccountIdentifier String harnessAccount, Boolean dryRun, Boolean writeValidation) {
    BaseIntegrationResponse response =
        integrationService.save(harnessAccount, integration, body, dryRun, writeValidation);
    return Response.status(Response.Status.OK).entity(from(response)).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_INTEGRATION, permission = IDP_INTEGRATION_DELETE)
  public Response deleteIntegrations(String integration, @AccountIdentifier String harnessAccount) {
    integrationService.delete(harnessAccount, integration);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_INTEGRATION, permission = IDP_INTEGRATION_VIEW)
  public Response discoverEntities(String integrationId, @AccountIdentifier String harnessAccount,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, Integer page, Integer limit,
      String sort, String searchTerm, String kinds, Integer prevOffset, Integer nextOffset) {
    idpCommonService.newFlowCheck(harnessAccount);
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 10 : limit;
    DiscoverEntitiesDTO discoveredEntities = integrationService.discoverEntities(harnessAccount, orgIdentifier,
        projectIdentifier, BaseIntegrationRequest.TypeEnum.CATALOG.value(), integrationId, pageIndex, pageLimit, sort,
        searchTerm, kinds, prevOffset, nextOffset);
    Map<String, Object> responseBody = new LinkedHashMap<>();
    responseBody.put("entities", discoveredEntities.getDiscoverEntitiesResponses());
    responseBody.put("merge_suggestions", discoveredEntities.getMergeSuggestions());
    if (discoveredEntities.isOffsetPagination()) {
      responseBody.put("prev_offset", discoveredEntities.getPrevOffset());
      responseBody.put("next_offset", discoveredEntities.getNextOffset());
      return Response.ok(responseBody).build();
    }
    return idpCommonService.buildPageResponse(
        pageIndex, pageLimit, discoveredEntities.getTotalElements(), responseBody);
  }

  @Override
  public Response getIntegration(String integration, String integrationId, @AccountIdentifier String harnessAccount) {
    BaseIntegrationResponse response = integrationService.get(harnessAccount, integration, integrationId);
    return Response.status(Response.Status.OK).entity(from(response)).build();
  }

  @Override
  public Response getIntegrations(String integration, @AccountIdentifier String harnessAccount, Integer page,
      Integer limit, String sort, String searchTerm) {
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 100 : limit;
    sort = validateSort(sort, integration);
    Pageable pageRequest = isEmpty(sort)
        ? PageRequest.of(
              pageIndex, pageLimit, Sort.by(Sort.Direction.DESC, IntegrationEntity.IntegrationsKeys.lastUpdatedAt))
        : PageUtils.getPageRequest(pageIndex, pageLimit, List.of(sort));
    List<BaseIntegrationResponse> responses =
        integrationService.get(harnessAccount, integration, pageRequest, searchTerm);
    List<AbstractIntegrationResponse> abstractIntegrationResponses = from(responses);
    return idpCommonService.buildPageResponse(
        pageIndex, pageLimit, abstractIntegrationResponses.size(), abstractIntegrationResponses);
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_INTEGRATION, permission = IDP_INTEGRATION_CREATE)
  public Response saveDiscoverEntities(@Valid SaveDiscoverEntitiesRequest body, String integrationId,
      @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    idpCommonService.newFlowCheck(harnessAccount);
    integrationService.saveDiscoverEntities(harnessAccount, orgIdentifier, projectIdentifier,
        BaseIntegrationRequest.TypeEnum.CATALOG.value(), integrationId, body);
    return Response.status(Response.Status.ACCEPTED).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_INTEGRATION, permission = IDP_INTEGRATION_VIEW)
  public Response getImportedEntities(String integrationId, @AccountIdentifier String harnessAccount,
      @OrgIdentifier String orgIdentifier, @ProjectIdentifier String projectIdentifier, Integer page, Integer limit,
      String sort, String searchTerm, String kinds) {
    idpCommonService.newFlowCheck(harnessAccount);
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 10 : limit;
    ImportedEntitiesDTO importedEntities = integrationService.getImportedEntities(harnessAccount, orgIdentifier,
        projectIdentifier, BaseIntegrationRequest.TypeEnum.CATALOG.value(), integrationId, pageIndex, pageLimit, sort,
        searchTerm, kinds);
    return idpCommonService.buildPageResponse(
        pageIndex, pageLimit, importedEntities.getTotalElements(), importedEntities.getImportedEntityResponses());
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_INTEGRATION, permission = IDP_INTEGRATION_DELETE)
  public Response unlinkIntegrationEntities(@Valid UnlinkIntegrationEntitiesRequest unlinkIntegrationEntitiesRequest,
      String integrationId, @AccountIdentifier String harnessAccount, @OrgIdentifier String orgIdentifier,
      @ProjectIdentifier String projectIdentifier) {
    idpCommonService.newFlowCheck(harnessAccount);
    UnlinkIntegrationEntitiesResponse unlinkIntegrationEntitiesResponse = integrationService.unlinkIntegrationEntities(
        harnessAccount, orgIdentifier, projectIdentifier, BaseIntegrationRequest.TypeEnum.CATALOG.value(),
        integrationId, unlinkIntegrationEntitiesRequest.getEntityRefs());
    return Response.status(Response.Status.OK).entity(unlinkIntegrationEntitiesResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_INTEGRATION, permission = IDP_INTEGRATION_EDIT)
  public Response updateIntegration(@Valid AbstractIntegrationRequest body, String integration, String integrationId,
      @AccountIdentifier String harnessAccount, Boolean dryRun) {
    BaseIntegrationResponse response =
        integrationService.update(harnessAccount, integration, integrationId, body, dryRun);
    return Response.status(Response.Status.OK).entity(from(response)).build();
  }

  private AbstractIntegrationResponse from(BaseIntegrationResponse response) {
    AbstractIntegrationResponse integrationResponse = new AbstractIntegrationResponse();
    integrationResponse.setResponse(response);
    return integrationResponse;
  }

  private List<AbstractIntegrationResponse> from(List<BaseIntegrationResponse> responses) {
    List<AbstractIntegrationResponse> abstractIntegrationResponses = new ArrayList<>();
    responses.forEach(response -> {
      AbstractIntegrationResponse abstractIntegrationResponse = new AbstractIntegrationResponse();
      abstractIntegrationResponse.setResponse(response);
      abstractIntegrationResponses.add(abstractIntegrationResponse);
    });
    return abstractIntegrationResponses;
  }

  private String validateSort(String sort, String integration) {
    if (!isEmpty(sort)) {
      String[] sortSplit = sort.split(COMMA_SEPARATOR);
      if (integration.equals(BaseIntegrationRequest.TypeEnum.GIT.name())) {
        if (!ALLOWED_PROPERTIES.contains(sortSplit[0])) {
          throw new InvalidArgumentsException(format("Invalid sort property: %s", sortSplit[0]));
        }

        if (sortSplit[0].equals("status")) {
          sort = sort.replace(sortSplit[0], "readPermissionValidation" + DOT_SEPARATOR + "status");
        }
      }
    }
    return sort;
  }
}
