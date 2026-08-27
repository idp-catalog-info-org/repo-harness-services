/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.githubpackages;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.cdng.service.steps.constants.ServiceStepV3Constants.SERVICE_GIT_BRANCH;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.cdng.artifact.resources.githubpackages.service.GithubPackagesResourceService;
import io.harness.evaluators.CDYamlExpressionEvaluator;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.artifacts.resources.util.YamlExpressionEvaluatorWithContext;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import software.wings.helpers.ext.jenkins.BuildDetails;

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
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/githubpackages")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GithubPackagesArtifactResource {
  private final GithubPackagesResourceService githubPackagesResourceService;

  private final ArtifactResourceUtils artifactResourceUtils;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  private final ScopeInfoService scopeInfoService;

  // GET Api to fetch Github Packages from an account or an org
  @GET
  @Path("packages")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Package details for GithubPackages", nickname = "getPackagesFromGithub")
  public ResponseDTO<GithubPackagesResponseDTO> getPackages(@QueryParam("connectorRef") String gitConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("packageType") String packageType, @QueryParam("org") String org,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (StringUtils.isBlank(gitConnectorIdentifier)) {
      throw new InvalidRequestException("Connector reference cannot be empty");
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gitConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    GithubPackagesResponseDTO response = githubPackagesResourceService.getPackageDetails(
        connectorRef, accountId, orgIdentifier, projectIdentifier, packageType, org, scopeInfo);

    return ResponseDTO.newResponse(response);
  }

  // POST Api to fetch Github Packages with ServiceV2
  @POST
  @Path("v2/packages")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Package details for GithubPackages", nickname = "getPackagesFromGithubWithServiceV2")
  public ResponseDTO<GithubPackagesResponseDTO> getPackagesWithServiceV2(
      @QueryParam("connectorRef") String gitConnectorIdentifier, @QueryParam("packageType") String packageType,
      @QueryParam("org") String org, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    // In case of ServiceV2 Calls
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      GithubPackagesArtifactConfig githubPackagesArtifactConfig =
          (GithubPackagesArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(gitConnectorIdentifier)) {
        gitConnectorIdentifier = (String) githubPackagesArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(packageType)) {
        packageType = (String) githubPackagesArtifactConfig.getPackageType().fetchFinalValue();
      }

      if (StringUtils.isBlank(org)) {
        org = (String) githubPackagesArtifactConfig.getOrg().fetchFinalValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    // Getting the resolved org in case of expressions
    String resolvedOrg = artifactResourceUtils
                             .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                 projectIdentifier, pipelineIdentifier, runtimeInputYaml, org, fqnPath,
                                 gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                             .getValue();

    // Getting the resolved packageType in case of expressions
    String resolvedPackageType = artifactResourceUtils
                                     .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                         projectIdentifier, pipelineIdentifier, runtimeInputYaml, packageType, fqnPath,
                                         gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                     .getValue();

    // Getting the resolved ConnectorRef in case of expressions
    String resolvedConnectorRef =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, gitConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef,
                yamlExpressionEvaluator)
            .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedConnectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    GithubPackagesResponseDTO response = githubPackagesResourceService.getPackageDetails(
        connectorRef, accountId, orgIdentifier, projectIdentifier, resolvedPackageType, resolvedOrg, scopeInfo);

    return ResponseDTO.newResponse(response);
  }

  // GET Api to fetch Versions for a Github Package
  @GET
  @Path("versions")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Versions from Packages", nickname = "getVersionsFromPackages")
  public ResponseDTO<List<BuildDetails>> getVersionsOfPackage(
      @NotNull @QueryParam("connectorRef") String gitConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("packageName") String packageName, @NotNull @QueryParam("packageType") String packageType,
      @QueryParam("versionRegex") String versionRegex, @QueryParam("org") String org,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (StringUtils.isBlank(gitConnectorIdentifier)) {
      throw new InvalidRequestException("Connector reference cannot be empty");
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gitConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    List<BuildDetails> response = githubPackagesResourceService.getVersionsOfPackage(connectorRef, packageName,
        packageType, versionRegex, org, accountId, orgIdentifier, projectIdentifier, scopeInfo);

    return ResponseDTO.newResponse(response);
  }

  // POST Api to fetch Versions for a Github Package with ServiceV2
  @POST
  @Path("v2/versions")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Versions from Packages", nickname = "getVersionsFromPackagesWithServiceV2")
  public ResponseDTO<List<BuildDetails>> getVersionsOfPackageWithServiceV2(
      @QueryParam("connectorRef") String gitConnectorIdentifier, @QueryParam("packageName") String packageName,
      @QueryParam("packageType") String packageType, @QueryParam("versionRegex") String versionRegex,
      @QueryParam("org") String org, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    // In case of ServiceV2 Calls
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      GithubPackagesArtifactConfig githubPackagesArtifactConfig =
          (GithubPackagesArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(gitConnectorIdentifier)) {
        gitConnectorIdentifier = (String) githubPackagesArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(packageType)) {
        packageType = (String) githubPackagesArtifactConfig.getPackageType().fetchFinalValue();
      }

      if (StringUtils.isBlank(org)) {
        org = (String) githubPackagesArtifactConfig.getOrg().fetchFinalValue();
      }

      if (StringUtils.isBlank(versionRegex)) {
        versionRegex = (String) githubPackagesArtifactConfig.getVersionRegex().fetchFinalValue();
      }

      if (StringUtils.isBlank(packageName)) {
        packageName = (String) githubPackagesArtifactConfig.getPackageName().fetchFinalValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    // Getting the resolved org in case of expressions
    String resolvedOrg = artifactResourceUtils
                             .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                 projectIdentifier, pipelineIdentifier, runtimeInputYaml, org, fqnPath,
                                 gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                             .getValue();

    // Getting the resolved packageType in case of expressions
    String resolvedPackageType = artifactResourceUtils
                                     .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                         projectIdentifier, pipelineIdentifier, runtimeInputYaml, packageType, fqnPath,
                                         gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                     .getValue();

    // Getting the resolved ConnectorRef in case of expressions
    String resolvedConnectorRef =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, gitConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef,
                yamlExpressionEvaluator)
            .getValue();

    // Getting the resolved versionRegex in case of expressions
    String resolvedVersionRegex = artifactResourceUtils
                                      .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                          projectIdentifier, pipelineIdentifier, runtimeInputYaml, versionRegex,
                                          fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                      .getValue();

    // Getting the resolved packageName in case of expressions
    String resolvedPackageName = artifactResourceUtils
                                     .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                         projectIdentifier, pipelineIdentifier, runtimeInputYaml, packageName, fqnPath,
                                         gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                     .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedConnectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    List<BuildDetails> response = githubPackagesResourceService.getVersionsOfPackage(connectorRef, resolvedPackageName,
        resolvedPackageType, resolvedVersionRegex, resolvedOrg, accountId, orgIdentifier, projectIdentifier, scopeInfo);

    return ResponseDTO.newResponse(response);
  }

  // GET Api to fetch Last Successful Version for a Github Package
  @GET
  @Path("lastSuccessfulVersion")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets Last Successful Version for the Package", nickname = "getLastSuccessfulVersion")
  public ResponseDTO<BuildDetails> getLastSuccessfulVersion(
      @NotNull @QueryParam(NGArtifactConstants.CONNECTOR_REF) String gitConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGArtifactConstants.PACKAGE_NAME) String packageName,
      @NotNull @QueryParam(NGArtifactConstants.PACKAGE_TYPE) String packageType,
      @QueryParam(NGArtifactConstants.VERSION) String version,
      @QueryParam(NGArtifactConstants.VERSION_REGEX) String versionRegex,
      @QueryParam(NGArtifactConstants.ORG) String org, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (StringUtils.isBlank(gitConnectorIdentifier)) {
      throw new InvalidRequestException("Connector reference cannot be empty");
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gitConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    BuildDetails build = githubPackagesResourceService.getLastSuccessfulVersion(connectorRef, packageName, packageType,
        version, versionRegex, org, accountId, orgIdentifier, projectIdentifier, scopeInfo);

    return ResponseDTO.newResponse(build);
  }

  // GET Api to fetch Last Successful Version for a Github Package with ServiceV2
  @POST
  @Path("v2/lastSuccessfulVersion")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets Last Successful Version for the Package", nickname = "getLastSuccessfulVersionWithServiceV2")
  public ResponseDTO<BuildDetails>
  getLastSuccessfulVersionWithServiceV2(@QueryParam(NGArtifactConstants.CONNECTOR_REF) String gitConnectorIdentifier,
      @QueryParam(NGArtifactConstants.PACKAGE_NAME) String packageName,
      @QueryParam(NGArtifactConstants.PACKAGE_TYPE) String packageType,
      @QueryParam(NGArtifactConstants.VERSION) String version,
      @QueryParam(NGArtifactConstants.VERSION_REGEX) String versionRegex,
      @QueryParam(NGArtifactConstants.ORG) String org,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    // In case of ServiceV2 Calls
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      GithubPackagesArtifactConfig githubPackagesArtifactConfig =
          (GithubPackagesArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(gitConnectorIdentifier)) {
        gitConnectorIdentifier = (String) githubPackagesArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(packageType)) {
        packageType = (String) githubPackagesArtifactConfig.getPackageType().fetchFinalValue();
      }

      if (StringUtils.isBlank(org)) {
        org = (String) githubPackagesArtifactConfig.getOrg().fetchFinalValue();
      }

      if (StringUtils.isBlank(versionRegex)) {
        versionRegex = (String) githubPackagesArtifactConfig.getVersionRegex().fetchFinalValue();
      }

      if (StringUtils.isBlank(packageName)) {
        packageName = (String) githubPackagesArtifactConfig.getPackageName().fetchFinalValue();
      }

      if (StringUtils.isBlank(version)) {
        version = (String) githubPackagesArtifactConfig.getVersion().fetchFinalValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    // Getting the resolved org in case of expressions
    String resolvedOrg = artifactResourceUtils
                             .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                 projectIdentifier, pipelineIdentifier, runtimeInputYaml, org, fqnPath,
                                 gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                             .getValue();

    // Getting the resolved packageType in case of expressions
    String resolvedPackageType = artifactResourceUtils
                                     .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                         projectIdentifier, pipelineIdentifier, runtimeInputYaml, packageType, fqnPath,
                                         gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                     .getValue();

    // Getting the resolved ConnectorRef in case of expressions
    String resolvedConnectorRef =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, gitConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef,
                yamlExpressionEvaluator)
            .getValue();

    // Getting the resolved versionRegex in case of expressions
    String resolvedVersionRegex = artifactResourceUtils
                                      .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                          projectIdentifier, pipelineIdentifier, runtimeInputYaml, versionRegex,
                                          fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                      .getValue();

    // Getting the resolved packageName in case of expressions
    String resolvedPackageName = artifactResourceUtils
                                     .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                         projectIdentifier, pipelineIdentifier, runtimeInputYaml, packageName, fqnPath,
                                         gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                     .getValue();

    // Getting the resolved version in case of expressions
    String resolvedVersion = artifactResourceUtils
                                 .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                     projectIdentifier, pipelineIdentifier, runtimeInputYaml, version, fqnPath,
                                     gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                 .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedConnectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    BuildDetails build =
        githubPackagesResourceService.getLastSuccessfulVersion(connectorRef, resolvedPackageName, resolvedPackageType,
            resolvedVersion, resolvedVersionRegex, resolvedOrg, accountId, orgIdentifier, projectIdentifier, scopeInfo);

    return ResponseDTO.newResponse(build);
  }
}
