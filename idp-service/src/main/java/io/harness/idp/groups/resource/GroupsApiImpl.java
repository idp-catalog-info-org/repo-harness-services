/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.idp.groups.resource;

import static io.harness.idp.common.RbacConstants.IDP_LAYOUT;
import static io.harness.idp.common.RbacConstants.IDP_LAYOUT_EDIT;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.groups.mappers.WorkflowsMapper;
import io.harness.idp.groups.service.GroupsService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.GroupsApi;
import io.harness.spec.server.idp.v1.model.GroupRequest;
import io.harness.spec.server.idp.v1.model.GroupResponse;
import io.harness.spec.server.idp.v1.model.WorkflowsInfoResponse;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import java.util.List;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Timed
@ResponseMetered
public class GroupsApiImpl implements GroupsApi {
  @Inject GroupsService groupService;
  @Inject IdpCommonService idpCommonService;

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response deleteGroup(String groupIdentifier, @AccountIdentifier String harnessAccount,
      @ProjectIdentifier String projectIdentifier, @OrgIdentifier String orgIdentifier) {
    groupService.deleteGroup(harnessAccount, orgIdentifier, projectIdentifier, groupIdentifier);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @Override
  public Response getAllGroupsForAccount(@AccountIdentifier String harnessAccount,
      @ProjectIdentifier String projectIdentifier, @OrgIdentifier String orgIdentifier) {
    List<GroupResponse> groupResponseList =
        groupService.getAllGroupsForAccount(harnessAccount, orgIdentifier, projectIdentifier);
    return Response.status(Response.Status.OK).entity(groupResponseList).build();
  }

  @Override
  public Response getGroupDetails(String groupIdentifier, @AccountIdentifier String harnessAccount,
      @ProjectIdentifier String projectIdentifier, @OrgIdentifier String orgIdentifier) {
    GroupResponse groupResponse =
        groupService.getGroup(harnessAccount, orgIdentifier, projectIdentifier, groupIdentifier);
    return Response.status(Response.Status.OK).entity(groupResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response saveGroup(@Valid GroupRequest body, @AccountIdentifier String harnessAccount,
      @ProjectIdentifier String projectIdentifier, @OrgIdentifier String orgIdentifier) {
    GroupResponse groupResponse = groupService.saveGroup(harnessAccount, orgIdentifier, projectIdentifier, body);
    return Response.status(Response.Status.OK).entity(groupResponse).build();
  }

  @Override
  @NGAccessControlCheck(resourceType = IDP_LAYOUT, permission = IDP_LAYOUT_EDIT)
  public Response updateGroups(@Valid List<GroupRequest> body, @AccountIdentifier String harnessAccount,
      @ProjectIdentifier String projectIdentifier, @OrgIdentifier String orgIdentifier) {
    List<GroupResponse> groupResponses =
        groupService.updateGroup(harnessAccount, orgIdentifier, projectIdentifier, body);
    return Response.status(Response.Status.OK).entity(groupResponses).build();
  }

  @Override
  public Response getWorkflowsForAccount(@AccountIdentifier String harnessAccount, Integer page, Integer limit,
      @ProjectIdentifier String projectIdentifier, @OrgIdentifier String orgIdentifier, String searchTerm) {
    int pageIndex = page == null ? 0 : page;
    int pageLimit = limit == null ? 10 : limit;
    WorkflowsInfoResponse workflowsInfoResponse = null;
    if (true) {
      workflowsInfoResponse = WorkflowsMapper.toResponseFromCatalogEntities(
          groupService
              .getCatalogEntitiesForWorkflowsInfo(
                  harnessAccount, pageIndex, pageLimit, orgIdentifier, projectIdentifier, searchTerm)
              .getContent());
    } else {
      workflowsInfoResponse = WorkflowsMapper.toResponseFromBackstageCatalogEntities(
          groupService.getWorkflowsInfo(harnessAccount, orgIdentifier, projectIdentifier, pageIndex, pageLimit)
              .getContent());
    }
    return idpCommonService.buildPageResponse(
        pageIndex, pageLimit, workflowsInfoResponse.getWorkflows().size(), workflowsInfoResponse);
  }
}
