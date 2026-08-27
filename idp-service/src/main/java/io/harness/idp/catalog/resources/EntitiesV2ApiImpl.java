/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.resources;

import io.harness.annotations.dev.*;
import io.harness.idp.annotations.IdpServiceAuthIfHasApiKey;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.spec.server.idp.v1.EntitiesV2Api;
import io.harness.spec.server.idp.v1.model.EntitiesConvertRequestBody;
import io.harness.spec.server.idp.v1.model.EntityConvertV2Response;
import io.harness.utils.ApiUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
@Timed
@ResponseMetered
public class EntitiesV2ApiImpl implements EntitiesV2Api {
  private static final String GET_ENTITIES_FLOW_LOG = "[getEntities flow]";
  private CatalogService catalogService;

  @Override
  public Response entityListV2(String harnessAccount, Integer page, @Max(100L) Integer limit, String sort,
      String searchTerm, Boolean resolvePlaceholders, String scopes, String entityRefs, Boolean ownedByMe,
      Boolean favorites, String kind, String type, String owner, String lifecycle, String tags, String filter,
      Boolean skipFavorites) {
    int pageLimit = (limit == null || limit == -1) ? 10 : limit;
    int pageNumber = page == null ? 0 : page;
    log.info("{} Received V2 get entities request account={} page={} limit={} scopesPresent={} entityRefsPresent={} "
            + "ownedByMe={} favorites={} skipFavorites={} kind={} searchTermPresent={} filterPresent={}",
        GET_ENTITIES_FLOW_LOG, harnessAccount, pageNumber, pageLimit, scopes != null, entityRefs != null, ownedByMe,
        favorites, skipFavorites, kind, searchTerm != null, filter != null);
    try {
      GetEntitiesDTO getEntitiesDTO =
          catalogService.getEntitiesV2(harnessAccount, page, pageLimit, sort, searchTerm, resolvePlaceholders, scopes,
              entityRefs, ownedByMe, favorites, kind, type, owner, lifecycle, tags, filter, true, false, skipFavorites);
      Response.ResponseBuilder responseBuilder = Response.ok();
      Response.ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(
          responseBuilder, getEntitiesDTO.getTotalElements(), getEntitiesDTO.getPageNumber(), pageLimit);
      responseBuilderWithLinks.header("Total-Owned", getEntitiesDTO.getTotalOwned());
      responseBuilderWithLinks.header("Total-Starred", getEntitiesDTO.getTotalStarred());
      log.info("{} Completed V2 get entities request account={} page={} limit={} returnedEntities={} totalElements={} "
              + "totalOwned={} totalStarred={}",
          GET_ENTITIES_FLOW_LOG, harnessAccount, getEntitiesDTO.getPageNumber(), pageLimit,
          getEntitiesDTO.getEntityResponses() == null ? 0 : getEntitiesDTO.getEntityResponses().size(),
          getEntitiesDTO.getTotalElements(), getEntitiesDTO.getTotalOwned(), getEntitiesDTO.getTotalStarred());
      return responseBuilderWithLinks.entity(getEntitiesDTO.getEntityResponses()).build();
    } catch (Exception ex) {
      log.error("{} V2 get entities request failed account={} page={} limit={}. Error={}", GET_ENTITIES_FLOW_LOG,
          harnessAccount, pageNumber, pageLimit, ex.getMessage(), ex);
      throw ex;
    }
  }

  @Override
  @IdpServiceAuthIfHasApiKey
  public Response convertEntitiesV2(
      @Valid List<EntitiesConvertRequestBody> entitiesConvertRequestBodyList, String option, String harnessAccount) {
    List<EntityConvertV2Response> entitiesConvertResponse =
        catalogService.convertEntityV2(harnessAccount, option, entitiesConvertRequestBodyList);
    return Response.ok().entity(entitiesConvertResponse).build();
  }
}
