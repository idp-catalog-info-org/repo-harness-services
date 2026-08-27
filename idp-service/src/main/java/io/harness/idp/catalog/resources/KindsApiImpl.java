/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.COMMA_SEPARATOR;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG_DELETE;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG_EDIT;
import static io.harness.idp.common.RbacConstants.IDP_CATALOG_VIEW;

import static java.lang.String.format;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.catalog.beans.KindRequestDTO;
import io.harness.idp.catalog.beans.KindResponseDTO;
import io.harness.idp.catalog.service.KindService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.KindsApi;
import io.harness.spec.server.idp.v1.model.KindCreateRequest;
import io.harness.spec.server.idp.v1.model.KindResponseBody;
import io.harness.spec.server.idp.v1.model.KindSchemaResponseBody;
import io.harness.spec.server.idp.v1.model.KindSchemaValidateRequest;
import io.harness.spec.server.idp.v1.model.KindUpdateRequest;
import io.harness.utils.ApiUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.Set;
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
public class KindsApiImpl implements KindsApi {
  private static final Set<String> ALLOWED_PROPERTIES = Set.of("name", "identifier");

  private KindService kindService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_CATALOG, permission = IDP_CATALOG_EDIT)
  public Response createKind(@Valid KindCreateRequest kindCreateRequest, @AccountIdentifier String harnessAccount) {
    KindRequestDTO kindRequestDTO = KindRequestDTO.builder()
                                        .identifier(kindCreateRequest.getIdentifier())
                                        .name(kindCreateRequest.getName())
                                        .description(kindCreateRequest.getDescription())
                                        .icon(kindCreateRequest.getIcon())
                                        .schema(kindCreateRequest.getSchema())
                                        .groupingKind(kindCreateRequest.isGroupingKind())
                                        .build();
    KindResponseBody kindResponseBody = kindService.save(harnessAccount, kindRequestDTO);
    return Response.status(Response.Status.CREATED).entity(kindResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_CATALOG, permission = IDP_CATALOG_DELETE)
  public Response deleteKind(String identifier, @AccountIdentifier String harnessAccount) {
    kindService.delete(harnessAccount, identifier);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_CATALOG, permission = IDP_CATALOG_VIEW)
  public Response getKind(String identifier, @AccountIdentifier String harnessAccount, Boolean custom) {
    custom = custom != null && custom;
    KindResponseBody kindResponseBody = kindService.get(harnessAccount, identifier, custom);
    return Response.status(Response.Status.OK).entity(kindResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_CATALOG, permission = IDP_CATALOG_VIEW)
  public Response getKindSchema(@AccountIdentifier String harnessAccount) {
    KindSchemaResponseBody kindSchemaResponseBody = kindService.getSchema();
    return Response.status(Response.Status.OK).entity(kindSchemaResponseBody).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_CATALOG, permission = IDP_CATALOG_VIEW)
  public Response getKinds(@AccountIdentifier String harnessAccount, Integer page, @Max(100L) Integer limit,
      String sort, String searchTerm, Boolean custom) {
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 100 : limit;
    sort = validateSort(sort);
    KindResponseDTO kindResponseDTO = kindService.get(harnessAccount, pageIndex, pageLimit, sort, searchTerm, custom);
    Response.ResponseBuilder responseBuilder = Response.ok();
    Response.ResponseBuilder responseBuilderWithLinks = ApiUtils.addLinksHeader(
        responseBuilder, kindResponseDTO.getTotalElements(), kindResponseDTO.getPageNumber(), pageLimit);
    return responseBuilderWithLinks.entity(kindResponseDTO.getKindResponseBodyList()).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_CATALOG, permission = IDP_CATALOG_VIEW)
  public Response kindSchemaValidate(@Valid KindSchemaValidateRequest body, @AccountIdentifier String harnessAccount) {
    kindService.validateSchema(body.getSchema());
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_CATALOG, permission = IDP_CATALOG_EDIT)
  public Response updateKind(
      @Valid KindUpdateRequest kindUpdateRequest, String identifier, @AccountIdentifier String harnessAccount) {
    KindRequestDTO kindRequestDTO = KindRequestDTO.builder()
                                        .identifier(identifier)
                                        .name(kindUpdateRequest.getName())
                                        .description(kindUpdateRequest.getDescription())
                                        .icon(kindUpdateRequest.getIcon())
                                        .schema(kindUpdateRequest.getSchema())
                                        .build();
    KindResponseBody kindResponseBody = kindService.update(harnessAccount, identifier, kindRequestDTO);
    return Response.status(Response.Status.OK).entity(kindResponseBody).build();
  }

  private String validateSort(String sort) {
    if (isEmpty(sort)) {
      return sort;
    }
    String[] sortSplit = sort.split(COMMA_SEPARATOR);
    if (!ALLOWED_PROPERTIES.contains(sortSplit[0])) {
      throw new InvalidArgumentsException(format("Invalid sort property: %s", sortSplit[0]));
    }
    return sort;
  }
}
