/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.catalog.resources;

import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.entities.Action;
import io.harness.idp.catalog.entities.ActionStatus;
import io.harness.idp.catalog.mapper.ActionMapper;
import io.harness.idp.catalog.service.ActionService;
import io.harness.idp.common.IdpCommonService;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.ActionsApi;
import io.harness.spec.server.idp.v1.model.ActionCreateRequest;
import io.harness.spec.server.idp.v1.model.ActionResponse;
import io.harness.spec.server.idp.v1.model.ActionStatusChangeRequest;
import io.harness.spec.server.idp.v1.model.ActionUpdateRequest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@Timed
@ResponseMetered
public class ActionsApiImpl implements ActionsApi {
  private ActionService actionService;
  private IdpCommonService idpCommonService;
  private ScopeInfoClient scopeInfoClient;

  @Override
  public Response createAction(
      @Valid ActionCreateRequest body, String harnessAccount, String orgIdentifier, String projectIdentifier) {
    ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    Action action = ActionMapper.fromCreateRequest(body);
    Action created = actionService.createAction(scopeInfo, action);
    return Response.status(Response.Status.CREATED).entity(ActionMapper.toResponse(created, scopeInfo)).build();
  }

  @Override
  public Response listActions(String harnessAccount, String orgIdentifier, String projectIdentifier, Integer page,
      Integer limit, String status, String category, String searchTerm, String sort) {
    ActionStatus actionStatus = null;
    if (status != null) {
      try {
        actionStatus = ActionStatus.valueOf(status.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new InvalidRequestException(
            String.format("Invalid status filter '%s'. Must be one of: DRAFT, PUBLISHED, DEPRECATED", status));
      }
    }
    ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    Page<Action> result = actionService.listActions(scopeInfo, actionStatus, category, searchTerm, page, limit, sort);
    List<ActionResponse> items =
        result.getContent().stream().map(a -> ActionMapper.toResponse(a, scopeInfo)).collect(Collectors.toList());
    return idpCommonService.buildPageResponse(
        page != null ? page : 0, limit != null ? limit : 10, result.getTotalElements(), items);
  }

  @Override
  public Response getAction(
      String identifier, String version, String harnessAccount, String orgIdentifier, String projectIdentifier) {
    ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    Action action = actionService.getAction(scopeInfo, identifier, version);
    return Response.ok(ActionMapper.toResponse(action, scopeInfo)).build();
  }

  @Override
  public Response updateAction(@Valid ActionUpdateRequest body, String identifier, String version,
      String harnessAccount, String orgIdentifier, String projectIdentifier) {
    ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    Action updated = actionService.updateAction(scopeInfo, identifier, version, body);
    return Response.ok(ActionMapper.toResponse(updated, scopeInfo)).build();
  }

  @Override
  public Response deleteAction(
      String identifier, String version, String harnessAccount, String orgIdentifier, String projectIdentifier) {
    ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    actionService.deleteAction(scopeInfo, identifier, version);
    return Response.noContent().build();
  }

  @Override
  public Response changeActionStatus(@Valid ActionStatusChangeRequest body, String identifier, String version,
      String harnessAccount, String orgIdentifier, String projectIdentifier) {
    ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    ActionStatus targetStatus = ActionStatus.valueOf(body.getStatus().name());
    Action updated = actionService.changeStatus(scopeInfo, identifier, version, targetStatus);
    return Response.ok(ActionMapper.toResponse(updated, scopeInfo)).build();
  }

  @Override
  public Response listActionVersions(
      String identifier, String harnessAccount, String orgIdentifier, String projectIdentifier) {
    ScopeInfo scopeInfo = getResponse(scopeInfoClient.getScopeInfo(harnessAccount, orgIdentifier, projectIdentifier));
    List<ActionResponse> versions = actionService.listActionVersions(scopeInfo, identifier)
                                        .stream()
                                        .map(a -> ActionMapper.toResponse(a, scopeInfo))
                                        .collect(Collectors.toList());
    return Response.ok(versions).build();
  }
}
