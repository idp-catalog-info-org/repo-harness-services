/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.gar;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceServiceImpl.GAR_REGIONS;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARPackageDTOList;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARRepositoryDTOList;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRequestDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceService;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.RegionGar;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/gar")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GARArtifactResource {
  private final GARResourceService gARResourceService;
  private ArtifactResourceUtils artifactResourceUtils;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeInfoService scopeInfoService;

  @GET
  @Path("getBuildDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets google artifact registry build details", nickname = "getBuildDetailsForGoogleArtifactRegistry")
  public ResponseDTO<GARResponseDTO>
  getBuildDetails(@QueryParam("connectorRef") String GCPConnectorIdentifier, @QueryParam("region") String region,
      @QueryParam("repositoryName") String repositoryName, @QueryParam("project") String project,
      @QueryParam("package") String pkg, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("version") String version,
      @QueryParam("versionRegex") String versionRegex, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(GCPConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    GARResponseDTO buildDetails = gARResourceService.getBuildDetails(connectorRef, region, repositoryName, project, pkg,
        version, versionRegex, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("v2/getBuildDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets google artifact registry build details v2", nickname = "getBuildDetailsForGoogleArtifactRegistryV2")
  public ResponseDTO<GARResponseDTO>
  getBuildDetailsV2(@QueryParam("connectorRef") String gcpConnectorIdentifier, @QueryParam("region") String region,
      @QueryParam("repositoryName") String repositoryName, @QueryParam("project") String project,
      @QueryParam("package") String pkg, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("version") String version, @QueryParam("versionRegex") String versionRegex,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    // In case of ServiceV2 Calls

    GARResponseDTO buildDetails = artifactResourceUtils.getBuildDetailsV2GAR(gcpConnectorIdentifier, region,
        repositoryName, project, pkg, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, version,
        versionRegex, fqnPath, runtimeInputYaml, serviceRef, gitEntityBasicInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets google artifact registry last successful build",
      nickname = "getLastSuccessfulBuildForGoogleArtifactRegistry")
  public ResponseDTO<GARBuildDetailsDTO>
  getLastSuccessfulBuild(@QueryParam(NGArtifactConstants.CONNECTOR_REF) String GCPConnectorIdentifier,
      @QueryParam(NGArtifactConstants.REGION) String region,
      @QueryParam(NGArtifactConstants.REPOSITORY_NAME) String repositoryName,
      @QueryParam(NGArtifactConstants.PROJECT) String project, @QueryParam(NGArtifactConstants.PACKAGE) String pkg,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @NotNull GarRequestDTO garRequestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(GCPConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    GARBuildDetailsDTO buildDetails = gARResourceService.getLastSuccessfulBuild(
        connectorRef, region, repositoryName, project, pkg, garRequestDTO, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("v2/getLastSuccessfulBuild")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets google artifact registry last successful build v2",
      nickname = "getLastSuccessfulBuildForGoogleArtifactRegistryV2")
  public ResponseDTO<GARBuildDetailsDTO>
  getLastSuccessfulBuildV2(@QueryParam(NGArtifactConstants.CONNECTOR_REF) String gcpConnectorIdentifier,
      @QueryParam(NGArtifactConstants.REGION) String region,
      @QueryParam(NGArtifactConstants.REPOSITORY_NAME) String repositoryName,
      @QueryParam(NGArtifactConstants.PROJECT) String project, @QueryParam(NGArtifactConstants.PACKAGE) String pkg,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull GarRequestDTO garRequestDTO) {
    GARBuildDetailsDTO buildDetails = artifactResourceUtils.getLastSuccessfulBuildV2GAR(gcpConnectorIdentifier, region,
        repositoryName, project, pkg, accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, garRequestDTO,
        fqnPath, serviceRef, gitEntityBasicInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("getRepositories")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets Repositories google artifact registry", nickname = "getRepositoriesForGoogleArtifactRegistry")
  public ResponseDTO<GARRepositoryDTOList>
  getRepositories(@QueryParam(NGArtifactConstants.CONNECTOR_REF) String GCPConnectorIdentifier,
      @QueryParam(NGArtifactConstants.REGION) String region, @QueryParam(NGArtifactConstants.PROJECT) String project,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(GCPConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    GARRepositoryDTOList modifiedBuildDetails = artifactResourceUtils.getRepositoriesGAR(
        connectorRef, region, project, accountId, orgIdentifier, projectIdentifier, false, scopeInfo);

    return ResponseDTO.newResponse(modifiedBuildDetails);
  }

  @POST
  @Path("v2/getRepositories")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets Repositories google artifact registry V2", nickname = "getRepositoriesForGoogleArtifactRegistryV2")
  public ResponseDTO<GARRepositoryDTOList>
  getRepositoriesV2(@QueryParam(NGArtifactConstants.CONNECTOR_REF) String GCPConnectorIdentifier,
      @QueryParam(NGArtifactConstants.REGION) String region, @QueryParam(NGArtifactConstants.PROJECT) String project,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(GCPConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    GARRepositoryDTOList buildDetails = artifactResourceUtils.getRepositoriesV2GAR(GCPConnectorIdentifier, region,
        project, accountId, orgIdentifier, pipelineIdentifier, fqnPath, serviceRef, runtimeInputYaml, projectIdentifier,
        gitEntityBasicInfo, true, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("getPackages")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets google artifact registry packages", nickname = "getPackagesForGoogleArtifactRegistry")
  public ResponseDTO<GARPackageDTOList> getPackages(@QueryParam("connectorRef") String GCPConnectorIdentifier,
      @QueryParam("region") String region, @QueryParam("repositoryName") String repositoryName,
      @QueryParam("project") String project, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(GCPConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    GARPackageDTOList buildDetails = artifactResourceUtils.getPackagesGAR(
        connectorRef, region, repositoryName, project, accountId, orgIdentifier, projectIdentifier, false, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("v2/getPackages")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets Repositories google artifact packages V2", nickname = "getPackagesForGoogleArtifactRegistryV2")
  public ResponseDTO<GARPackageDTOList>
  getPackagesV2(@QueryParam(NGArtifactConstants.CONNECTOR_REF) String GCPConnectorIdentifier,
      @QueryParam(NGArtifactConstants.REGION) String region,
      @QueryParam(NGArtifactConstants.REPOSITORY_NAME) String repositoryName,
      @QueryParam(NGArtifactConstants.PROJECT) String project,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    GARPackageDTOList buildDetails = artifactResourceUtils.getPackagesV2GAR(GCPConnectorIdentifier, region,
        repositoryName, project, accountId, orgIdentifier, pipelineIdentifier, fqnPath, serviceRef, runtimeInputYaml,
        projectIdentifier, gitEntityBasicInfo, true);
    return ResponseDTO.newResponse(buildDetails);
  }
  @GET
  @Path("getRegions")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets google artifact registry regions", nickname = "getRegionsForGoogleArtifactRegistry")
  public ResponseDTO<List<RegionGar>> getRegions() {
    return ResponseDTO.newResponse(GAR_REGIONS);
  }
}
