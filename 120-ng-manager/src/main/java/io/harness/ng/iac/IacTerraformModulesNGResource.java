/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iac;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.IACM;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.sdk.GitSyncApiConstants;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import javax.validation.constraints.Max;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(IACM)
@Api("/iac-tf-module")
@Path("/iac-tf-module")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = io.harness.ng.core.dto.FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = io.harness.ng.core.dto.ErrorDTO.class, message = "Internal server error")
    })
@NextGenManagerAuth
@Slf4j
public class IacTerraformModulesNGResource {
  @Inject IacTerraformModulesHelper iacTerraformModulesHelper;

  @POST
  @Path("list-tags-async-task")
  @Timed
  @ApiOperation(value = "Send list tags", nickname = "sendListTags")
  public ResponseDTO listTagsAsyncTask(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
                                           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @DefaultValue("") String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @DefaultValue("") String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) String connectorRef,
      @Parameter(description = "Size of the list"
              + "(max 100)"
              + "Default Value: 50") @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue("50") @Max(100)
      int listSize,
      @Parameter(description = "Module name") @QueryParam("name") String name,
      @Parameter(description = "Module system") @QueryParam("system") String system,
      @Parameter(description = "Org where the module is scoped") @QueryParam("org") @DefaultValue("") String org,
      @Parameter(description = "Project where the module is scoped") @QueryParam("project") @DefaultValue(
          "") String project) {
    iacTerraformModulesHelper.sendListTags(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName,
        PageRequest.builder().pageSize(listSize).build(), name, system, org, project);

    return ResponseDTO.newResponse();
  }

  @POST
  @Path("terraform-module-async-task-array")
  @Timed
  @ApiOperation(
      value = "Create task that gets terraform module of given repo", nickname = "createTerraformModuleAsyncTaskArray")
  public ResponseDTO
  createTerraformModuleAsyncTaskArray(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
                                          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @DefaultValue("") String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @DefaultValue("") String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) String connectorRef,
      @Parameter(description = "Git tag") @QueryParam("gitTag") String[] gitTag,
      @Parameter(description = "Submodule of Terraform module") @QueryParam("path") @DefaultValue("") String[] path,
      @Parameter(description = "Module name") @QueryParam("name") String name,
      @Parameter(description = "Module system") @QueryParam("system") String system,
      @Parameter(description = "Org where the module is scoped") @QueryParam("org") @DefaultValue("") String org,
      @Parameter(description = "Project where the module is scoped") @QueryParam("project") @DefaultValue(
          "") String project) {
    iacTerraformModulesHelper.sendTerraformModule(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef,
        repoName, gitTag, path, name, system, org, project);

    return ResponseDTO.newResponse();
  }

  @POST
  @Path("terraform-module-async-task")
  @Timed
  @ApiOperation(
      value = "Create task that gets terraform module of given repo", nickname = "createTerraformModuleAsyncTask")
  public ResponseDTO
  createTerraformModuleAsyncTask(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotBlank @QueryParam(
                                     NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @DefaultValue("") String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier @DefaultValue("") String projectIdentifier,
      @Parameter(description = GitSyncApiConstants.REPO_NAME_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.REPO_NAME) String repoName,
      @Parameter(description = GitSyncApiConstants.GIT_CONNECTOR_REF_PARAM_MESSAGE) @QueryParam(
          GitSyncApiConstants.CONNECTOR_REF) String connectorRef,
      @Parameter(description = "Git tag") @QueryParam("gitTag") String[] gitTag,
      @Parameter(description = "Submodule of Terraform module") @QueryParam("path") @DefaultValue("") String path,
      @Parameter(description = "Module name") @QueryParam("name") String name,
      @Parameter(description = "Module system") @QueryParam("system") String system,
      @Parameter(description = "Org where the module is scoped") @QueryParam("org") @DefaultValue("") String org,
      @Parameter(description = "Project where the module is scoped") @QueryParam("project") @DefaultValue(
          "") String project) {
    iacTerraformModulesHelper.sendTerraformModule(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef,
        repoName, gitTag, path, name, system, org, project);

    return ResponseDTO.newResponse();
  }
}
