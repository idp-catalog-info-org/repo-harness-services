/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.nexus;
import static io.harness.cdng.service.steps.constants.ServiceStepV3Constants.SERVICE_GIT_BRANCH;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusRequestDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
import io.harness.delegate.task.artifacts.source.ArtifactSourceType;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.artifacts.resources.util.YamlExpressionEvaluatorWithContext;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import software.wings.helpers.ext.nexus.NexusRepositories;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_COMMON_STEPS,
        HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDP)
@Api("artifacts")
@Path("/artifacts/nexus")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class NexusArtifactResource {
  private final NexusResourceService nexusResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeInfoService scopeInfoService;
  private final NexusArtifactApiUtils nexusArtifactApiUtils;

  @GET
  @Path("getBuildDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets nexus artifact build details", nickname = "getBuildDetailsForNexusArtifact")
  public ResponseDTO<NexusResponseDTO> getBuildDetails(@QueryParam("repository") String repository,
      @QueryParam("repositoryPort") String repositoryPort, @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("repositoryUrl") String artifactRepositoryUrl, @QueryParam("artifactPath") String artifactPath,
      @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @QueryParam(NGArtifactConstants.GROUP_ID) String groupId,
      @QueryParam(NGArtifactConstants.ARTIFACT_ID) String artifactId, @QueryParam("extension") String extension,
      @QueryParam("classifier") String classifier, @QueryParam("packageName") String packageName,
      @QueryParam(NGArtifactConstants.GROUP) String group,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    return ResponseDTO.newResponse(nexusArtifactApiUtils.getBuildDetails(accountId, orgIdentifier, projectIdentifier,
        repository, repositoryPort, repositoryFormat, artifactRepositoryUrl, artifactPath, nexusConnectorIdentifier,
        groupId, artifactId, extension, classifier, packageName, group));
  }

  @POST
  @Path("getBuildDetailsV2")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets nexus artifact build details with yaml input for expression resolution",
      nickname = "getBuildDetailsForNexusArtifactWithYaml")
  public ResponseDTO<NexusResponseDTO>
  getBuildDetailsV2(@QueryParam("repository") String repository, @QueryParam("repositoryPort") String repositoryPort,
      @QueryParam("artifactPath") String artifactPath, @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("repositoryUrl") String artifactRepositoryUrl,
      @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @QueryParam(NGArtifactConstants.GROUP_ID) String groupId,
      @QueryParam(NGArtifactConstants.ARTIFACT_ID) String artifactId, @QueryParam("extension") String extension,
      @QueryParam("classifier") String classifier, @QueryParam("packageName") String packageName,
      @QueryParam(NGArtifactConstants.GROUP) String group,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    NexusResponseDTO buildDetails;
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath.trim(),
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS2_REGISTRY)) {
        buildDetails = artifactResourceUtils.getBuildDetailsNexus2(nexusConnectorIdentifier, repository, repositoryPort,
            artifactPath, repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier, groupId,
            artifactId, extension, classifier, packageName, pipelineIdentifier, fqnPath, gitEntityBasicInfo,
            runtimeInputYaml, serviceRef, accountId, group, baseEvaluatorWithContext);
        return ResponseDTO.newResponse(buildDetails);
      }
    }
    buildDetails = artifactResourceUtils.getBuildDetails(nexusConnectorIdentifier, repository, repositoryPort,
        artifactPath, repositoryFormat, artifactRepositoryUrl, orgIdentifier, projectIdentifier, groupId, artifactId,
        extension, classifier, packageName, pipelineIdentifier, fqnPath.trim(), gitEntityBasicInfo, runtimeInputYaml,
        serviceRef, accountId, group, baseEvaluatorWithContext);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets nexus artifact last successful build", nickname = "getLastSuccessfulBuildForNexusArtifact")
  public ResponseDTO<NexusBuildDetailsDTO>
  getLastSuccessfulBuild(@QueryParam(NGArtifactConstants.REPOSITORY) String repository,
      @QueryParam(NGArtifactConstants.REPOSITORY_PORT) String repositoryPort,
      @QueryParam(NGCommonEntityConstants.ARTIFACT_PATH) String artifactPath,
      @QueryParam(NGArtifactConstants.REPOSITORY_FORMAT) String repositoryFormat,
      @QueryParam(NGArtifactConstants.REPOSITORY_URL) String artifactRepositoryUrl,
      @QueryParam(NGArtifactConstants.CONNECTOR_REF) String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, NexusRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    NexusBuildDetailsDTO buildDetails =
        nexusResourceService.getSuccessfulBuild(connectorRef, repository, repositoryPort, artifactPath,
            repositoryFormat, artifactRepositoryUrl, requestDTO, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuildV2")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets nexus artifact last successful build with yaml input for expression resolution",
      nickname = "getLastSuccessfulBuildForNexusArtifactWithYaml")
  public ResponseDTO<NexusBuildDetailsDTO>
  getLastSuccessfulBuildV2(@QueryParam(NGArtifactConstants.REPOSITORY) String repository,
      @QueryParam(NGArtifactConstants.REPOSITORY_PORT) String repositoryPort,
      @QueryParam(NGCommonEntityConstants.ARTIFACT_PATH) String artifactPath,
      @QueryParam(NGArtifactConstants.REPOSITORY_FORMAT) String repositoryFormat,
      @QueryParam(NGArtifactConstants.REPOSITORY_URL) String artifactRepositoryUrl,
      @QueryParam(NGArtifactConstants.CONNECTOR_REF) String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef, @NotNull NexusRequestDTO nexusRequestDTO) {
    NexusBuildDetailsDTO buildDetails = artifactResourceUtils.getLastSuccessfulBuildV2Nexus3(repository, repositoryPort,
        artifactPath, repositoryFormat, artifactRepositoryUrl, nexusConnectorIdentifier, accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, fqnPath, gitEntityBasicInfo, serviceRef, nexusRequestDTO);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("validateArtifactServer")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Validate nexus artifact server", nickname = "validateArtifactServerForNexus")
  public ResponseDTO<Boolean> validateArtifactServer(@QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(nexusConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    boolean isValidArtifactServer =
        nexusResourceService.validateArtifactServer(connectorRef, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }

  @POST
  @Hidden
  @Path("getRepositories")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get Repositories for nexus artifact server", nickname = "getRepositories")
  public ResponseDTO<List<NexusRepositories>> getRepositories(
      @QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("repositoryFormat") String repositoryFormat, @QueryParam("fqnPath") String fqnPath,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));
      if (artifactSpecFromService.getSourceType().equals(ArtifactSourceType.NEXUS2_REGISTRY)) {
        return ResponseDTO.newResponse(artifactResourceUtils.getRepositoriesNexus2(orgIdentifier, projectIdentifier,
            repositoryFormat, accountId, pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath,
            gitEntityBasicInfo, serviceRef, baseEvaluatorWithContext));
      }
    }
    return ResponseDTO.newResponse(artifactResourceUtils.getRepositoriesNexus3(orgIdentifier, projectIdentifier,
        repositoryFormat, accountId, pipelineIdentifier, runtimeInputYaml, nexusConnectorIdentifier, fqnPath,
        gitEntityBasicInfo, serviceRef, baseEvaluatorWithContext));
  }

  @POST
  @Hidden
  @Path("groupIds")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get GroupIds for nexus", nickname = "getGroupIds")
  public ResponseDTO<List<String>> getGroupIds(@QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("repositoryFormat") String repositoryFormat, @QueryParam("repository") String repository,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    List<String> groupIds = artifactResourceUtils.getNexusGroupIds(nexusConnectorIdentifier, accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, repositoryFormat, repository, fqnPath, gitEntityBasicInfo,
        runtimeInputYaml, serviceRef);

    return ResponseDTO.newResponse(groupIds);
  }

  @POST
  @Hidden
  @Path("artifactIds")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get ArtifactIds for nexus", nickname = "artifactIds")
  public ResponseDTO<List<String>> getArtifactIds(@QueryParam("connectorRef") String nexusConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("repositoryFormat") String repositoryFormat, @QueryParam("repository") String repository,
      @QueryParam("groupId") String groupId, @QueryParam("nexusSourceType") String sourceType,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    List<String> artifactIds = artifactResourceUtils.getNexusArtifactIds(nexusConnectorIdentifier, accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, repositoryFormat, repository, groupId, sourceType,
        fqnPath, gitEntityBasicInfo, runtimeInputYaml, serviceRef);

    return ResponseDTO.newResponse(artifactIds);
  }
}
