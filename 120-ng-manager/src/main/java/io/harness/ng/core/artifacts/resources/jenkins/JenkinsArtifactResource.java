/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.jenkins;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.resources.jenkins.dtos.JenkinsJobDetailsDTO;
import io.harness.cdng.artifact.resources.jenkins.service.JenkinsResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.jenkins.JobDetails.JobParameter;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/jenkins")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class JenkinsArtifactResource {
  private final JenkinsResourceService jenkinsResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeInfoService scopeInfoService;
  @GET
  @Path("jobs")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Job details for Jenkins", nickname = "getJobDetailsForJenkins")
  public ResponseDTO<JenkinsJobDetailsDTO> getJobDetails(@QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("parentJobName") String parentJobName, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    JenkinsJobDetailsDTO buildDetails =
        jenkinsResourceService.getJobDetails(connectorRef, orgIdentifier, projectIdentifier, parentJobName, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("job/{jobName}/paths")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets jenkins Artifact Paths", nickname = "getArtifactPath For Jenkins")
  public ResponseDTO<List<String>> getArtifactPath(@QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @PathParam("jobName") String jobName,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return ResponseDTO.newResponse(
        jenkinsResourceService.getArtifactPath(connectorRef, orgIdentifier, projectIdentifier, jobName, scopeInfo));
  }

  @GET
  @Path("job/{jobName}/builds")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Jenkins builds", nickname = "getBuilds For Jenkins")
  public ResponseDTO<List<BuildDetails>> getBuildsForJob(@QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @PathParam("jobName") String jobName, @QueryParam("artifactPath") String artifactPath,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return ResponseDTO.newResponse(jenkinsResourceService.getBuildForJob(
        connectorRef, orgIdentifier, projectIdentifier, jobName, Arrays.asList(artifactPath), scopeInfo));
  }

  @GET
  @Path("job/{jobName}/details")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Jenkins Job paramter", nickname = "getJobParameters for Jenkins")
  public ResponseDTO<List<JobParameter>> getJobParameters(@QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @PathParam("jobName") String jobName, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    List<JobDetails> jobDetails =
        jenkinsResourceService.getJobParameters(connectorRef, orgIdentifier, projectIdentifier, jobName, scopeInfo);
    List<JobParameter> jobParameters;
    if (jobDetails.size() > 0) {
      jobParameters = jobDetails.get(0).getParameters();
    } else {
      jobParameters = new ArrayList<>();
    }
    return ResponseDTO.newResponse(jobParameters);
  }

  @POST
  @Hidden
  @Path("/v2/jobs")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Job details for Jenkins ServiceV2", nickname = "getJobDetailsForJenkinsServiceV2")
  public ResponseDTO<JenkinsJobDetailsDTO> getJobDetailsV2(
      @QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam(NGCommonEntityConstants.PARENT_JOB_NAME) String parentJobName,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @QueryParam(NGCommonEntityConstants.FQN_PATH) String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef, String runtimeInputYaml) {
    JenkinsJobDetailsDTO buildDetails = artifactResourceUtils.getJenkinsJobDetails(jenkinsConnectorIdentifier,
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, parentJobName, gitEntityBasicInfo, fqnPath,
        serviceRef, runtimeInputYaml);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Hidden
  @Path("/v2/jobArtifactPaths")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets jenkins Artifact Paths ServiceV2", nickname = "getArtifactPath For Jenkins ServiceV2")
  public ResponseDTO<List<String>> getArtifactPathV2(@QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam(NGCommonEntityConstants.JOB_NAME) String jobName, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam(NGCommonEntityConstants.FQN_PATH) String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef, String runtimeInputYaml) {
    List<String> jenkinsArtifactPaths =
        artifactResourceUtils.getJenkinsArtifactPaths(jenkinsConnectorIdentifier, accountId, orgIdentifier,
            projectIdentifier, pipelineIdentifier, jobName, gitEntityBasicInfo, fqnPath, serviceRef, runtimeInputYaml);
    return ResponseDTO.newResponse(jenkinsArtifactPaths);
  }

  @POST
  @Hidden
  @Path("/v2/jobBuilds")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Jenkins builds ServiceV2", nickname = "getBuilds For Jenkins ServiceV2")
  public ResponseDTO<List<BuildDetails>> getBuildsForJobV2(
      @QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam(NGCommonEntityConstants.JOB_NAME) String jobName,
      @QueryParam(NGCommonEntityConstants.ARTIFACT_PATH) String artifactPath,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @QueryParam(NGCommonEntityConstants.FQN_PATH) String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef, String runtimeInputYaml) {
    List<BuildDetails> buildDetails = artifactResourceUtils.getJenkinsJobBuildsV2(jenkinsConnectorIdentifier, accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, jobName, artifactPath, gitEntityBasicInfo, fqnPath,
        serviceRef, runtimeInputYaml);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Hidden
  @Path("/v2/jobDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Jenkins Job paramter ServiceV2", nickname = "getJobParameters for Jenkins ServiceV2")
  public ResponseDTO<List<JobParameter>> getJobParametersV2(
      @QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam(NGCommonEntityConstants.JOB_NAME) String jobName, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam(NGCommonEntityConstants.FQN_PATH) String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef, String runtimeInputYaml) {
    List<JobParameter> jobParameters =
        artifactResourceUtils.getJenkinsJobParameters(jenkinsConnectorIdentifier, accountId, orgIdentifier,
            projectIdentifier, pipelineIdentifier, jobName, gitEntityBasicInfo, fqnPath, serviceRef, runtimeInputYaml);

    return ResponseDTO.newResponse(jobParameters);
  }
}
