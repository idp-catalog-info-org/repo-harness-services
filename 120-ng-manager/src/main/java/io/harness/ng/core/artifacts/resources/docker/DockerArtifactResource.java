/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.docker;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.cdng.service.steps.constants.ServiceStepV3Constants.SERVICE_GIT_BRANCH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
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
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.resources.docker.dtos.DockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerRequestDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerResponseDTO;
import io.harness.cdng.artifact.resources.docker.service.DockerResourceService;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.evaluators.CDYamlExpressionEvaluator;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.artifacts.resources.util.YamlExpressionEvaluatorWithContext;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/docker")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class DockerArtifactResource {
  private final DockerResourceService dockerResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeInfoService scopeInfoService;
  private final DockerArtifactApiUtils dockerArtifactService;

  @GET
  @Path("getBuildDetails")
  @ApiOperation(value = "Gets docker build details", nickname = "getBuildDetailsForDocker")
  @Timed
  @ResponseMetered
  public ResponseDTO<DockerResponseDTO> getBuildDetails(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    DockerResponseDTO buildDetails = dockerArtifactService.getBuildDetails(
        accountId, orgIdentifier, projectIdentifier, imagePath, dockerConnectorIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getBuildDetailsV2")
  @ApiOperation(value = "Gets docker build details with yaml input for expression resolution",
      nickname = "getBuildDetailsForDockerWithYaml")
  @Timed
  @ResponseMetered
  public ResponseDTO<DockerResponseDTO>
  getBuildDetailsV2(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam(NGArtifactConstants.TAG_INPUT) String tagInput, @NotNull @QueryParam("fqnPath") String fqnPath,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @QueryParam(NGCommonEntityConstants.SERVICE_GIT_BRANCH) String serviceGitBranch) {
    String tagRegex = null;
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isEmpty(serviceGitBranch)) {
      serviceGitBranch =
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath, serviceGitBranch);

      DockerHubArtifactConfig dockerHubArtifactConfig = (DockerHubArtifactConfig) artifactSpecFromService;
      if (isEmpty(imagePath)) {
        imagePath = (String) dockerHubArtifactConfig.getImagePath().fetchFinalValue();
      }
      if (isEmpty(dockerConnectorIdentifier)) {
        dockerConnectorIdentifier = (String) dockerHubArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (EmptyPredicate.isNotEmpty(tagInput)) {
        final ParameterField<String> tagRegexParameterField =
            RuntimeInputValuesValidator.getInputSetParameterField(tagInput);
        if (tagRegexParameterField != null && artifactResourceUtils.checkValidRegexType(tagRegexParameterField)) {
          tagRegex = tagRegexParameterField.getInputSetValidator().getParameters();
        }
      }

      if (EmptyPredicate.isEmpty(tagRegex)
          && artifactResourceUtils.checkValidRegexType(dockerHubArtifactConfig.getTag())) {
        tagRegex = dockerHubArtifactConfig.getTag().getInputSetValidator().getParameters();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    dockerConnectorIdentifier =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, dockerConnectorIdentifier, fqnPath, gitEntityBasicInfo,
                serviceRef, yamlExpressionEvaluator)
            .getValue();

    imagePath = artifactResourceUtils
                    .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                        pipelineIdentifier, runtimeInputYaml, imagePath, fqnPath, gitEntityBasicInfo, serviceRef,
                        yamlExpressionEvaluator)
                    .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    DockerResponseDTO buildDetails = dockerResourceService.getBuildDetails(
        connectorRef, imagePath, orgIdentifier, projectIdentifier, tagRegex, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLabels")
  @ApiOperation(value = "Gets docker labels", nickname = "getLabelsForDocker")
  @Timed
  @ResponseMetered
  public ResponseDTO<DockerResponseDTO> getLabels(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, DockerRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    DockerResponseDTO buildDetails = dockerResourceService.getLabels(
        connectorRef, imagePath, requestDTO, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @ApiOperation(value = "Gets docker last successful build", nickname = "getLastSuccessfulBuildForDocker")
  @Timed
  @ResponseMetered
  public ResponseDTO<DockerBuildDetailsDTO> getLastSuccessfulBuild(
      @QueryParam(NGArtifactConstants.IMAGE_PATH) String imagePath,
      @QueryParam(NGArtifactConstants.CONNECTOR_REF) String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, DockerRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    DockerBuildDetailsDTO buildDetails = dockerResourceService.getSuccessfulBuild(
        connectorRef, imagePath, requestDTO, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuildV2")
  @ApiOperation(value = "Gets docker last successful build with yaml input for expression resolution",
      nickname = "getLastSuccessfulBuildForDockerWithYaml")
  @Timed
  @ResponseMetered
  public ResponseDTO<DockerBuildDetailsDTO>
  getLastSuccessfulBuildV2(@QueryParam(NGArtifactConstants.IMAGE_PATH) String imagePath,
      @QueryParam(NGArtifactConstants.CONNECTOR_REF) String dockerConnectorIdentifier,
      @QueryParam(NGArtifactConstants.TAG) String tag,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @NotNull DockerRequestDTO requestDTO, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    DockerBuildDetailsDTO dockerBuildDetailsDTO =
        artifactResourceUtils.getLastSuccessfulBuildV2Docker(imagePath, dockerConnectorIdentifier, tag, accountId,
            orgIdentifier, projectIdentifier, pipelineIdentifier, fqnPath, gitEntityBasicInfo, requestDTO, serviceRef);
    return ResponseDTO.newResponse(dockerBuildDetailsDTO);
  }

  @GET
  @Path("validateArtifactServer")
  @ApiOperation(value = "Validate docker artifact server", nickname = "validateArtifactServerForDocker")
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean> validateArtifactServer(@QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    boolean isValidArtifactServer =
        dockerResourceService.validateArtifactServer(connectorRef, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }

  @GET
  @Path("validateArtifactSource")
  @ApiOperation(value = "Validate docker image", nickname = "validateArtifactImageForDocker")
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean> validateArtifactImage(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    boolean isValidArtifactImage = dockerResourceService.validateArtifactSource(
        imagePath, connectorRef, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(isValidArtifactImage);
  }

  @POST
  @Path("validateArtifact")
  @ApiOperation(value = "Validate docker artifact with tag/tagregx if given", nickname = "validateArtifactForDocker")
  @Timed
  @ResponseMetered
  public ResponseDTO<Boolean> validateArtifact(@QueryParam("imagePath") String imagePath,
      @QueryParam("connectorRef") String dockerConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, DockerRequestDTO requestDTO) {
    if (NGExpressionUtils.isRuntimeOrExpressionField(dockerConnectorIdentifier)) {
      throw new InvalidRequestException("ConnectorRef is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(imagePath)) {
      throw new InvalidRequestException("ImagePath is an expression/runtime input, please send fixed value.");
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    boolean isValidArtifact = false;
    if (!ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTag())
        && !ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTagRegex())) {
      ScopeInfo scopeInfo =
          pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
          ? scopeInfoService.getScopeInfo(connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(),
                connectorRef.getProjectIdentifier())
          : null;

      isValidArtifact = dockerResourceService.validateArtifactSource(
          imagePath, connectorRef, orgIdentifier, projectIdentifier, scopeInfo);
    } else {
      try {
        ResponseDTO<DockerBuildDetailsDTO> lastSuccessfulBuild = getLastSuccessfulBuild(
            imagePath, dockerConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, requestDTO);
        if (lastSuccessfulBuild.getData() != null && isNotEmpty(lastSuccessfulBuild.getData().getTag())) {
          isValidArtifact = true;
        }
      } catch (Exception e) {
        log.info("Not able to find any artifact with given parameters - " + requestDTO.toString() + " and imagePath - "
            + imagePath);
      }
    }
    return ResponseDTO.newResponse(isValidArtifact);
  }
}
