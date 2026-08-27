/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.personaview.resource;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.Constants.COMMA_SEPARATOR;
import static io.harness.idp.common.RbacConstants.IDP_LAYOUT;
import static io.harness.idp.common.RbacConstants.IDP_LAYOUT_EDIT;
import static io.harness.idp.personaview.PersonaViewConstants.LOG_PREFIX;
import static io.harness.idp.personaview.entities.PersonaViewEntity.PersonaViewEntityKeys;

import static java.lang.String.format;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.personaview.cards.ComparisonByHierarchyCardService;
import io.harness.idp.personaview.service.PersonaViewService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.PersonaViewsApi;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyCardDataRequest;
import io.harness.spec.server.idp.v1.model.ComparisonByHierarchyCardDataResponse;
import io.harness.spec.server.idp.v1.model.PersonaView;
import io.harness.spec.server.idp.v1.model.PersonaViewListForUser;
import io.harness.spec.server.idp.v1.model.PersonaViewListResponse;
import io.harness.spec.server.idp.v1.model.PersonaViewResponse;
import io.harness.spec.server.idp.v1.model.SavePersonaViewRequest;
import io.harness.utils.PageUtils;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@NextGenManagerAuth
@OwnedBy(HarnessTeam.IDP)
@Slf4j
@Timed
@ResponseMetered
public class PersonaViewsApiImpl implements PersonaViewsApi {
  private static final Map<String, String> ALLOWED_PROPERTIES_API_TO_ENTITY_FIELD_MAP =
      Map.of("name", PersonaViewEntityKeys.name, "last_updated_at", PersonaViewEntityKeys.lastUpdatedAt);

  private final PersonaViewService personaViewService;
  private final IdpCommonService idpCommonService;
  private final ComparisonByHierarchyCardService comparisonByHierarchyCardService;

  @Inject
  public PersonaViewsApiImpl(PersonaViewService personaViewService, IdpCommonService idpCommonService,
      ComparisonByHierarchyCardService comparisonByHierarchyCardService) {
    this.personaViewService = personaViewService;
    this.idpCommonService = idpCommonService;
    this.comparisonByHierarchyCardService = comparisonByHierarchyCardService;
  }

  @Override
  public Response getPersonaViewsForUser(@AccountIdentifier String harnessAccount) {
    log.info("{} API getPersonaViewsForUser for account {}", LOG_PREFIX, harnessAccount);
    List<PersonaView> views = personaViewService.getPersonaViewsForUser(harnessAccount);
    return Response.status(Response.Status.OK).entity(new PersonaViewListForUser().views(views)).build();
  }

  @Override
  public Response listPersonaViews(@AccountIdentifier String harnessAccount, Integer page, Integer limit, String sort,
      String order, String searchTerm) {
    log.info("{} API listPersonaViews for account {} page={} limit={} searchTerm={}", LOG_PREFIX, harnessAccount, page,
        limit, searchTerm);
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 100 : limit;
    String resolvedSort = validateSort(sort);
    Pageable pageRequest = isEmpty(resolvedSort)
        ? PageRequest.of(pageIndex, pageLimit, Sort.by(Sort.Direction.DESC, PersonaViewEntityKeys.lastUpdatedAt))
        : PageUtils.getPageRequest(pageIndex, pageLimit, List.of(resolvedSort));
    Page<PersonaView> personaViewPage = personaViewService.listPersonaViews(harnessAccount, pageRequest, searchTerm);
    return idpCommonService.buildPageResponse(pageIndex, pageLimit, personaViewPage.getTotalElements(),
        new PersonaViewListResponse().views(personaViewPage.getContent()));
  }

  @Override
  public Response getPersonaView(String personaViewIdentifier, @AccountIdentifier String harnessAccount) {
    log.info("{} API getPersonaView {} for account {}", LOG_PREFIX, personaViewIdentifier, harnessAccount);
    PersonaViewResponse response = personaViewService.getPersonaView(harnessAccount, personaViewIdentifier);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response savePersonaView(
      String personaViewIdentifier, @Valid SavePersonaViewRequest body, @AccountIdentifier String harnessAccount) {
    log.info("{} API savePersonaView {} for account {}", LOG_PREFIX, personaViewIdentifier, harnessAccount);
    PersonaViewResponse response = personaViewService.savePersonaView(harnessAccount, personaViewIdentifier, body);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  public Response getComparisonByHierarchyCardData(@Valid ComparisonByHierarchyCardDataRequest body,
      String personaViewIdentifier, @AccountIdentifier String harnessAccount) {
    log.info("{} API getComparisonByHierarchyCardData personaView={} account={} scope={}", LOG_PREFIX,
        personaViewIdentifier, harnessAccount, body == null ? null : body.getScope());
    ComparisonByHierarchyCardDataResponse response =
        comparisonByHierarchyCardService.getData(harnessAccount, personaViewIdentifier, body);
    return Response.status(Response.Status.OK).entity(response).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response deletePersonaView(String personaViewIdentifier, @AccountIdentifier String harnessAccount) {
    log.info("{} API deletePersonaView {} for account {}", LOG_PREFIX, personaViewIdentifier, harnessAccount);
    personaViewService.deletePersonaView(harnessAccount, personaViewIdentifier);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  private String validateSort(String sort) {
    if (!isEmpty(sort)) {
      String[] sortSplit = sort.split(COMMA_SEPARATOR);
      if (sortSplit.length == 0 || isEmpty(sortSplit[0])) {
        throw new InvalidArgumentsException("Invalid sort parameter: sort property cannot be empty");
      }

      if (!ALLOWED_PROPERTIES_API_TO_ENTITY_FIELD_MAP.containsKey(sortSplit[0])) {
        throw new InvalidArgumentsException(format("Invalid sort property: %s", sortSplit[0]));
      }
      return sort.replace(sortSplit[0], ALLOWED_PROPERTIES_API_TO_ENTITY_FIELD_MAP.get(sortSplit[0]));
    }
    return sort;
  }
}
