/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.ecr;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.cdng.artifact.NGArtifactConstants.REGISTRY_ID;
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
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrListImagesDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrRequestDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrResponseDTO;
import io.harness.cdng.artifact.resources.ecr.service.EcrResourceService;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_COMMON_STEPS})
@Api("artifacts")
@Path("/artifacts/ecr")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class EcrArtifactResource {
  private final EcrResourceService ecrResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;

  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeInfoService scopeInfoService;

  @GET
  @Path("getBuildDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets ecr build details", nickname = "getBuildDetailsForEcr")
  public ResponseDTO<EcrResponseDTO> getBuildDetails(@QueryParam(REGISTRY_ID) String registryId,
      @NotNull @QueryParam("imagePath") String imagePath, @NotNull @QueryParam("region") String region,
      @NotNull @QueryParam("connectorRef") String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    EcrResponseDTO buildDetails = ecrResourceService.getBuildDetails(
        connectorRef, registryId, imagePath, region, orgIdentifier, projectIdentifier, null, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getBuildDetailsV2")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets ecr build details with yaml expression", nickname = "getBuildDetailsForEcrWithYaml")
  public ResponseDTO<EcrResponseDTO> getBuildDetailsV2(@QueryParam(REGISTRY_ID) String registryId,
      @QueryParam("imagePath") String imagePath, @QueryParam("region") String region,
      @QueryParam("connectorRef") String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @QueryParam(NGArtifactConstants.TAG_INPUT) String tagInput) {
    String tagRegex = null;
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(registryId) && ParameterField.isNotNull(ecrArtifactConfig.getRegistryId())) {
        registryId = (String) ecrArtifactConfig.getRegistryId().fetchFinalValue();
      }
      if (isEmpty(imagePath)) {
        imagePath = (String) ecrArtifactConfig.getImagePath().fetchFinalValue();
      }
      if (isEmpty(region)) {
        region = (String) ecrArtifactConfig.getRegion().fetchFinalValue();
      }

      if (isEmpty(ecrConnectorIdentifier)) {
        ecrConnectorIdentifier = (String) ecrArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (EmptyPredicate.isNotEmpty(tagInput)) {
        final ParameterField<String> tagRegexParameterField =
            RuntimeInputValuesValidator.getInputSetParameterField(tagInput);
        if (tagRegexParameterField != null && artifactResourceUtils.checkValidRegexType(tagRegexParameterField)) {
          tagRegex = tagRegexParameterField.getInputSetValidator().getParameters();
        }
      }

      if (EmptyPredicate.isEmpty(tagRegex) && artifactResourceUtils.checkValidRegexType(ecrArtifactConfig.getTag())) {
        tagRegex = ecrArtifactConfig.getTag().getInputSetValidator().getParameters();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    ecrConnectorIdentifier = artifactResourceUtils
                                 .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                     projectIdentifier, pipelineIdentifier, runtimeInputYaml, ecrConnectorIdentifier,
                                     fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                 .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    registryId = artifactResourceUtils
                     .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                         pipelineIdentifier, runtimeInputYaml, registryId, fqnPath, gitEntityBasicInfo, serviceRef,
                         yamlExpressionEvaluator)
                     .getValue();

    imagePath = artifactResourceUtils
                    .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                        pipelineIdentifier, runtimeInputYaml, imagePath, fqnPath, gitEntityBasicInfo, serviceRef,
                        yamlExpressionEvaluator)
                    .getValue();

    // resolving region
    region = artifactResourceUtils
                 .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                     pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef,
                     yamlExpressionEvaluator)
                 .getValue();
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    EcrResponseDTO buildDetails = ecrResourceService.getBuildDetails(
        connectorRef, registryId, imagePath, region, orgIdentifier, projectIdentifier, tagRegex, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuild")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets ecr last successful build", nickname = "getLastSuccessfulBuildForEcr")
  public ResponseDTO<EcrBuildDetailsDTO> getLastSuccessfulBuild(@QueryParam(REGISTRY_ID) String registryId,
      @NotNull @QueryParam(NGArtifactConstants.IMAGE_PATH) String imagePath,
      @NotNull @QueryParam(NGArtifactConstants.CONNECTOR_REF) String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, EcrRequestDTO requestDTO) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    EcrBuildDetailsDTO buildDetails = ecrResourceService.getSuccessfulBuild(
        connectorRef, registryId, imagePath, requestDTO, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(buildDetails);
  }

  @POST
  @Path("getLastSuccessfulBuildV2")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets ecr last successful build with yaml expression", nickname = "getLastSuccessfulBuildForEcrWithYaml")
  public ResponseDTO<EcrBuildDetailsDTO>
  getLastSuccessfulBuildV2(@QueryParam(REGISTRY_ID) String registryId,
      @QueryParam(NGArtifactConstants.IMAGE_PATH) String imagePath,
      @QueryParam(NGArtifactConstants.CONNECTOR_REF) String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @NotNull EcrRequestDTO ecrRequestDTO) {
    EcrBuildDetailsDTO ecrBuildDetailsDTO = artifactResourceUtils.getLastSuccessfulBuildV2ECR(registryId, imagePath,
        ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, fqnPath, serviceRef, pipelineIdentifier,
        gitEntityBasicInfo, ecrRequestDTO);
    return ResponseDTO.newResponse(ecrBuildDetailsDTO);
  }

  @GET
  @Path("validateArtifactServer")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Validate ecr artifact server", nickname = "validateArtifactServerForEcr")
  public ResponseDTO<Boolean> validateArtifactServer(@QueryParam(REGISTRY_ID) String registryId,
      @NotNull @QueryParam("imagePath") String imagePath,
      @NotNull @QueryParam("connectorRef") String ecrConnectorIdentifier, @NotNull @QueryParam("region") String region,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    boolean isValidArtifactServer = ecrResourceService.validateArtifactServer(
        connectorRef, registryId, imagePath, orgIdentifier, projectIdentifier, region, scopeInfo);
    return ResponseDTO.newResponse(isValidArtifactServer);
  }

  @GET
  @Path("validateArtifactSource")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Validate Ecr image", nickname = "validateArtifactImageForEcr")
  public ResponseDTO<Boolean> validateArtifactImage(@QueryParam(REGISTRY_ID) String registryId,
      @NotNull @QueryParam("imagePath") String imagePath, @NotNull @QueryParam("region") String region,
      @NotNull @QueryParam("connectorRef") String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    boolean isValidArtifactImage = ecrResourceService.validateArtifactSource(
        registryId, imagePath, connectorRef, region, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(isValidArtifactImage);
  }

  @POST
  @Path("validateArtifact")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Validate Ecr Artifact", nickname = "validateArtifactForEcr")
  public ResponseDTO<Boolean> validateArtifact(@QueryParam(REGISTRY_ID) String registryId,
      @NotNull @QueryParam("imagePath") String imagePath, @NotNull @QueryParam("region") String region,
      @NotNull @QueryParam("connectorRef") String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, EcrRequestDTO requestDTO) {
    if (NGExpressionUtils.isRuntimeOrExpressionField(ecrConnectorIdentifier)) {
      throw new InvalidRequestException("ConnectorRef is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(registryId)) {
      throw new InvalidRequestException("RegistryId is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(imagePath)) {
      throw new InvalidRequestException("ImagePath is an expression/runtime input, please send fixed value.");
    }
    if (NGExpressionUtils.isRuntimeOrExpressionField(region)) {
      throw new InvalidRequestException("region is an expression/runtime input, please send fixed value.");
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    boolean isValidArtifact = false;
    if (!ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTag())
        && !ArtifactResourceUtils.isFieldFixedValue(requestDTO.getTagRegex())) {
      isValidArtifact = ecrResourceService.validateArtifactSource(
          registryId, imagePath, connectorRef, region, orgIdentifier, projectIdentifier, scopeInfo);
    } else {
      try {
        ResponseDTO<EcrBuildDetailsDTO> lastSuccessfulBuild = getLastSuccessfulBuild(
            registryId, imagePath, ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, requestDTO);
        if (lastSuccessfulBuild.getData() != null
            && EmptyPredicate.isNotEmpty(lastSuccessfulBuild.getData().getTag())) {
          isValidArtifact = true;
        }
      } catch (Exception e) {
        log.info("Not able to find any artifact with given parameters - " + requestDTO.toString() + " and imagePath - "
            + imagePath);
      }
    }
    return ResponseDTO.newResponse(isValidArtifact);
  }

  @POST
  @Path("getImages")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets ecr images", nickname = "getImagesListForEcr")
  public ResponseDTO<EcrListImagesDTO> getImages(@QueryParam(REGISTRY_ID) String registryId,
      @QueryParam("region") String region, @QueryParam("connectorRef") String ecrConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier, @QueryParam("fqnPath") String fqnPath,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getContextMap().get(SERVICE_GIT_BRANCH));

      EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactSpecFromService;
      if (isEmpty(registryId) && ParameterField.isNotNull(ecrArtifactConfig.getRegistryId())) {
        registryId = (String) ecrArtifactConfig.getRegistryId().fetchFinalValue();
      }
      if (isEmpty(region)) {
        region = (String) ecrArtifactConfig.getRegion().fetchFinalValue();
        if (isEmpty(region)) {
          throw new InvalidRequestException("Please input a valid region.");
        }
      }
      if (isEmpty(ecrConnectorIdentifier)) {
        ecrConnectorIdentifier = (String) ecrArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    ecrConnectorIdentifier = artifactResourceUtils
                                 .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                     projectIdentifier, pipelineIdentifier, runtimeInputYaml, ecrConnectorIdentifier,
                                     fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                 .getValue();
    registryId = artifactResourceUtils
                     .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                         pipelineIdentifier, runtimeInputYaml, registryId, fqnPath, gitEntityBasicInfo, serviceRef,
                         yamlExpressionEvaluator)
                     .getValue();
    region = artifactResourceUtils
                 .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                     pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef,
                     yamlExpressionEvaluator)
                 .getValue();
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(ecrConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    EcrListImagesDTO ecrListImagesDTO =
        ecrResourceService.getImages(connectorRef, registryId, region, orgIdentifier, projectIdentifier, scopeInfo);
    return ResponseDTO.newResponse(ecrListImagesDTO);
  }
}
