/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.jira.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.ACCESS_CONNECTOR_PERMISSION;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.jira.resources.service.JiraResourceService;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.jira.JiraIssueCreateMetadataNG;
import io.harness.jira.JiraIssueNG;
import io.harness.jira.JiraIssueTransitionNG;
import io.harness.jira.JiraIssueUpdateMetadataNG;
import io.harness.jira.JiraProjectBasicNG;
import io.harness.jira.JiraStatusNG;
import io.harness.jira.JiraUserData;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.HashMap;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@OwnedBy(CDC)
@Api("jira")
@Path("/jira")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_APPROVALS})
public class JiraResource {
  private final JiraResourceService jiraResourceService;
  private final AccessControlClient accessControlClient;

  private void checkConnectorAccess(IdentifierRef connectorRef) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(connectorRef.getAccountIdentifier(),
                                                  connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier()),
        Resource.of(ResourceTypes.CONNECTOR, connectorRef.getIdentifier()), ACCESS_CONNECTOR_PERMISSION);
  }

  @GET
  @Path("validate")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Validate jira credentials", nickname = "validateJiraCredentials")
  public ResponseDTO<Boolean> validateCredentials(@NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    checkConnectorAccess(connectorRef);
    boolean isValid = jiraResourceService.validateCredentials(connectorRef, orgId, projectId);
    return ResponseDTO.newResponse(isValid);
  }

  @GET
  @Path("projects")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get jira projects", nickname = "getJiraProjects")
  public ResponseDTO<List<JiraProjectBasicNG>> getProjects(@NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    checkConnectorAccess(connectorRef);
    List<JiraProjectBasicNG> projects = jiraResourceService.getProjects(connectorRef, orgId, projectId);
    return ResponseDTO.newResponse(projects);
  }

  @GET
  @Path("statuses")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get jira statuses", nickname = "getJiraStatuses")
  public ResponseDTO<List<JiraStatusNG>> getStatuses(@NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId, @QueryParam("projectKey") String projectKey,
      @QueryParam("issueType") String issueType, @QueryParam("issueKey") String issueKey,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    checkConnectorAccess(connectorRef);
    List<JiraStatusNG> statuses =
        jiraResourceService.getStatuses(connectorRef, orgId, projectId, projectKey, issueType, issueKey);
    return ResponseDTO.newResponse(statuses);
  }

  @GET
  @Path("createMetadata")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get jira issue create metadata", nickname = "getJiraIssueCreateMetadata")
  public ResponseDTO<JiraIssueCreateMetadataNG> getIssueCreateMetadata(
      @NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId, @QueryParam("projectKey") String projectKey,
      @QueryParam("issueType") String issueType, @QueryParam("expand") String expand,
      @QueryParam("fetchStatus") boolean fetchStatus, @QueryParam("ignoreComment") boolean ignoreComment,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    checkConnectorAccess(connectorRef);
    JiraIssueCreateMetadataNG createMetadata = jiraResourceService.getIssueCreateMetadata(
        connectorRef, orgId, projectId, projectKey, issueType, expand, fetchStatus, ignoreComment);
    return ResponseDTO.newResponse(createMetadata);
  }

  @GET
  @Path("searchUser")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get jira usernames for the jira connector", nickname = "jiraUserSearch")
  public ResponseDTO<List<JiraUserData>> getUserSearch(
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountId,
      @QueryParam(NGCommonEntityConstants.CONNECTOR_IDENTIFIER_KEY) String connectorId,
      @QueryParam("userQuery") String userQuery, @QueryParam("offset") String offset) {
    checkConnectorAccess(
        IdentifierRefHelper.getIdentifierRef(connectorId, accountId, orgIdentifier, projectIdentifier));
    return ResponseDTO.newResponse(jiraResourceService
                                       .searchUser(accountId, orgIdentifier, projectIdentifier, connectorId,
                                           DEFAULT_SYNC_CALL_TIMEOUT, userQuery, offset)
                                       .getJiraUserDataList());
  }

  @GET
  @Path("updateMetadata")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get jira issue update metadata", nickname = "getJiraIssueUpdateMetadata")
  public ResponseDTO<JiraIssueUpdateMetadataNG> getIssueUpdateMetadata(
      @NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId, @QueryParam("issueKey") String issueKey,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    checkConnectorAccess(connectorRef);
    JiraIssueUpdateMetadataNG updateMetadata =
        jiraResourceService.getIssueUpdateMetadata(connectorRef, orgId, projectId, issueKey);
    return ResponseDTO.newResponse(updateMetadata);
  }

  @GET
  @Path("transitions")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get issue transitions", nickname = "getIssueTransitions")
  public ResponseDTO<List<JiraIssueTransitionNG>> getIssueTransitions(
      @NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @NotNull @QueryParam("issueKey") String issueKey, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    checkConnectorAccess(connectorRef);
    return ResponseDTO.newResponse(jiraResourceService.getTransitions(connectorRef, orgId, projectId, issueKey));
  }

  @POST
  @Path("issue/{issueKey}/transitions")
  @Consumes(MediaType.APPLICATION_JSON)
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Transition jira issue", nickname = "transitionJiraIssue")
  public ResponseDTO<JiraIssueNG> transitionIssue(@PathParam("issueKey") String issueKey,
      @NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @Body @NotNull @Valid JiraTransitionIssueBody transitionIssueBody) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    checkConnectorAccess(connectorRef);
    JiraIssueNG jiraIssueNG = jiraResourceService.transitionIssue(connectorRef, orgId, projectId, issueKey,
        transitionIssueBody.getTransitionId(), transitionIssueBody.getFields());
    return ResponseDTO.newResponse(jiraIssueNG);
  }

  @POST
  @Path("issue")
  @Consumes(MediaType.APPLICATION_JSON)
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Create jira issue", nickname = "createJiraIssue")
  public ResponseDTO<JiraIssueNG> createIssue(@NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @QueryParam("jiraProjectKey") String jiraProjectKey, @Body @NotNull JiraCreateIssueBody createIssueBody) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    checkConnectorAccess(connectorRef);
    HashMap<String, String> fields = new HashMap<>();
    if (createIssueBody.fields != null) {
      fields = createIssueBody.fields;
    }
    if (createIssueBody.summary != null) {
      fields.put("Summary", createIssueBody.summary);
    }
    JiraIssueNG jiraIssueNG = jiraResourceService.createIssue(
        orgId, projectId, connectorRef, jiraProjectKey, createIssueBody.issueType, fields);
    return ResponseDTO.newResponse(jiraIssueNG);
  }

  @POST
  @Path("issue/{issueKey}/comment")
  @Consumes(MediaType.APPLICATION_JSON)
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Add jira comment", nickname = "addJiraComment")
  public ResponseDTO<JiraIssueNG> addComment(@NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId, @PathParam("issueKey") String issueKey,
      @QueryParam("jiraProjectKey") String jiraProjectKey, @Body @NotNull JiraAddCommentBody addCommentBody) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    checkConnectorAccess(connectorRef);
    HashMap<String, String> fields = new HashMap<>();
    fields.put("Comment", addCommentBody.comment);
    JiraIssueNG jiraIssueNG =
        jiraResourceService.addComment(orgId, projectId, connectorRef, jiraProjectKey, issueKey, fields);
    return ResponseDTO.newResponse(jiraIssueNG);
  }

  @GET
  @Path("issue/{issueKey}")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get jira issue", nickname = "getJiraIssue")
  public ResponseDTO<JiraIssueNG> addComment(@PathParam("issueKey") String issueKey,
      @NotNull @QueryParam("connectorRef") String jiraConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgId,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @QueryParam("jiraProjectKey") String jiraProjectKey) {
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(jiraConnectorRef, accountId, orgId, projectId);
    checkConnectorAccess(connectorRef);
    JiraIssueNG jiraIssueNG = jiraResourceService.getIssue(orgId, projectId, connectorRef, jiraProjectKey, issueKey);
    return ResponseDTO.newResponse(jiraIssueNG);
  }
}