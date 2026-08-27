/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.resources.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.azure.resources.dtos.AzureTagsDTO;
import io.harness.cdng.infra.definition.config.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.AzureContainerAppsInfrastructure;
import io.harness.cdng.infra.yaml.AzureInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.k8s.resources.azure.dtos.AzureClustersDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureContainerAppsManagedEnvironmentsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureDeploymentSlotsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureFunctionAppNamesDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureImageGalleriesDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureLocationsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureManagementGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureResourceGroupsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureWebAppNamesDTO;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.EnvironmentValidationHelper;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.azure.response.AzureHostResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.AzureListInstancesFilterDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.utils.IdentifierRefHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDP)
@Api("azure")
@Path("/azure")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AzureResource {
  private final AzureResourceService azureResourceService;
  private final InfrastructureEntityService infrastructureEntityService;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final EnvironmentValidationHelper environmentValidationHelper;
  private final AccessControlClient accessControlClient;
  private final ScopeInfoService scopeInfoService;

  @GET
  @Path("subscriptions")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure subscriptions ", nickname = "getAzureSubscriptions")
  public ResponseDTO<AzureSubscriptionsDTO> getAzureSubscriptions(
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    if (isEmpty(azureConnectorIdentifier) && isNotEmpty(envId) && isNotEmpty(infraDefinitionId)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      azureConnectorIdentifier = infrastructureDefinitionConfig.getSpec().getConnectorReference().getValue();
    }
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(
        azureResourceService.getSubscriptions(connectorRef, orgIdentifier, projectIdentifier, scopeInfo));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/app-services-names")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets azure app services names by subscriptionId and resourceGroup", nickname = "getAzureWebAppNames")
  public ResponseDTO<AzureWebAppNamesDTO>
  getAppServiceNames(@NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @NotEmpty @PathParam("subscriptionId") String subscriptionId,
      @NotNull @NotEmpty @PathParam("resourceGroup") String resourceGroup) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getWebAppNames(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, scopeInfo));
  }

  @GET
  @Path("v2/app-services-names")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure app services names V2", nickname = "getAzureWebAppNamesV2")
  public ResponseDTO<AzureWebAppNamesDTO> getAppServiceNamesV2(
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroup") String resourceGroup,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envId);
    checkForAccessOrThrow(accountId, orgIdentifier, projectIdentifier, envId, ENVIRONMENT_VIEW_PERMISSION, "view");
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId) || isEmpty(resourceGroup)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }
    if (isEmpty(azureConnectorIdentifier) && spec != null) {
      azureConnectorIdentifier = spec.getConnectorReference().getValue();
    }
    if (isEmpty(subscriptionId) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
    }
    if (isEmpty(resourceGroup) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      resourceGroup = azureInfrastructure.getResourceGroup().getValue();
    }
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getWebAppNames(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, scopeInfo));
  }

  @GET
  @Path("function-app-names")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure function app names ", nickname = "getAzureFunctionAppNames")
  public ResponseDTO<AzureFunctionAppNamesDTO> getFunctionAppNames(
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroup") String resourceGroup,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envId);
    checkForAccessOrThrow(accountId, orgIdentifier, projectIdentifier, envId, ENVIRONMENT_VIEW_PERMISSION, "view");
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId) || isEmpty(resourceGroup)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }
    if (spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = spec.getConnectorReference().getValue();
      }
      if (isEmpty(subscriptionId)) {
        subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
      }
      if (isEmpty(resourceGroup)) {
        resourceGroup = azureInfrastructure.getResourceGroup().getValue();
      }
    }
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getFunctionAppNames(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, scopeInfo));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/app-services/{webAppName}/slots")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure webApp deployment slots", nickname = "getAzureWebAppDeploymentSlots")
  public ResponseDTO<AzureDeploymentSlotsDTO> getAppServiceDeploymentSlotNames(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @NotEmpty @PathParam("subscriptionId") String subscriptionId,
      @NotNull @NotEmpty @PathParam("resourceGroup") String resourceGroup,
      @NotNull @NotEmpty @PathParam("webAppName") String webAppName) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getAppServiceDeploymentSlots(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, webAppName, scopeInfo));
  }

  @GET
  @Path("v2/app-services/{webAppName}/slots")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure webApp deployment slots V2", nickname = "getAzureWebAppDeploymentSlotsV2")
  public ResponseDTO<AzureDeploymentSlotsDTO> getAppServiceDeploymentSlotNamesV2(
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroup") String resourceGroup,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId,
      @NotNull @PathParam("webAppName") String webAppName) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envId);
    checkForAccessOrThrow(accountId, orgIdentifier, projectIdentifier, envId, ENVIRONMENT_VIEW_PERMISSION, "view");
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId) || isEmpty(resourceGroup)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }
    if (isEmpty(azureConnectorIdentifier) && spec != null) {
      azureConnectorIdentifier = spec.getConnectorReference().getValue();
    }
    if (isEmpty(subscriptionId) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
    }
    if (isEmpty(resourceGroup) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      resourceGroup = azureInfrastructure.getResourceGroup().getValue();
    }
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getAppServiceDeploymentSlots(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, webAppName, scopeInfo));
  }

  @GET
  @Path("function-app-services/{functionAppName}/slots")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure functionApp deployment slots V2", nickname = "getAzureFunctionAppDeploymentSlotsV2")
  public ResponseDTO<AzureDeploymentSlotsDTO> getFunctionAppServiceDeploymentSlotNames(
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroup") String resourceGroup,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId,
      @NotNull @PathParam("functionAppName") String functionAppName) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, envId);
    checkForAccessOrThrow(accountId, orgIdentifier, projectIdentifier, envId, ENVIRONMENT_VIEW_PERMISSION, "view");
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId) || isEmpty(resourceGroup)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }
    if (spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = spec.getConnectorReference().getValue();
      }
      if (isEmpty(subscriptionId)) {
        subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
      }
      if (isEmpty(resourceGroup)) {
        resourceGroup = azureInfrastructure.getResourceGroup().getValue();
      }
    }
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getFunctionServiceDeploymentSlots(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, functionAppName, scopeInfo));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets azure resource groups by subscription ", nickname = "getAzureResourceGroupsBySubscription")
  public ResponseDTO<AzureResourceGroupsDTO>
  getResourceGroupsBySubscription(@NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getResourceGroups(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, scopeInfo));
  }
  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/imageGalleries")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets azure image Galleries by resource group", nickname = "GetsazureimageGalleriesbyresourcegroup")
  public ResponseDTO<AzureImageGalleriesDTO>
  getImageGalleries(@QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId, @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @PathParam("resourceGroup") String resourceGroup) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getImageGallery(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, scopeInfo));
  }
  @GET
  @Path("v2/resourceGroups")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure resource groups V2", nickname = "getAzureResourceGroupsV2")
  public ResponseDTO<AzureResourceGroupsDTO> getResourceGroupsV2(
      @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(azureConnectorIdentifier) && spec != null) {
      azureConnectorIdentifier = spec.getConnectorReference().getValue();
    }

    if (isEmpty(subscriptionId) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
    }

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getResourceGroups(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, scopeInfo));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/resourceGroups/{resourceGroup}/clusters")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure k8s clusters by subscription ", nickname = "getAzureClusters")
  public ResponseDTO<AzureClustersDTO> getClusters(@NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId, @PathParam("resourceGroup") String resourceGroup) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getClusters(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, scopeInfo));
  }

  @GET
  @Path("v2/clusters")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure k8s clusters ", nickname = "getAzureClustersV2")
  public ResponseDTO<AzureClustersDTO> getAzureClustersV2(@QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroup") String resourceGroup,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId) || isEmpty(resourceGroup)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(azureConnectorIdentifier) && spec != null) {
      azureConnectorIdentifier = spec.getConnectorReference().getValue();
    }

    if (isEmpty(subscriptionId) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
    }

    if (isEmpty(resourceGroup) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      resourceGroup = azureInfrastructure.getResourceGroup().getValue();
    }

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getClusters(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, scopeInfo));
  }

  @GET
  @Path("subscriptions/{subscriptionId}/tags")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure tags by subscription ", nickname = "getSubscriptionTags")
  public ResponseDTO<AzureTagsDTO> getSubscriptionTags(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("subscriptionId") String subscriptionId) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(
        azureResourceService.getTags(connectorRef, orgIdentifier, projectIdentifier, subscriptionId, scopeInfo));
  }

  @GET
  @Path("v2/tags")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure tags by subscription ", nickname = "getSubscriptionTagsV2")
  public ResponseDTO<AzureTagsDTO> getSubscriptionTagsV2(@QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(azureConnectorIdentifier) && spec != null) {
      azureConnectorIdentifier = spec.getConnectorReference().getValue();
    }

    if (isEmpty(subscriptionId) && spec != null) {
      AzureInfrastructure azureInfrastructure = (AzureInfrastructure) spec;
      subscriptionId = azureInfrastructure.getSubscriptionId().getValue();
    }

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(
        azureResourceService.getTags(connectorRef, orgIdentifier, projectIdentifier, subscriptionId, scopeInfo));
  }

  private InfrastructureDefinitionConfig getInfrastructureDefinitionConfig(
      String accountId, String orgIdentifier, String projectIdentifier, String envId, String infraDefinitionId) {
    if (isEmpty(envId)) {
      throw new InvalidRequestException(
          String.valueOf(format("%s must be provided", NGCommonEntityConstants.ENVIRONMENT_KEY)));
    }

    if (isEmpty(infraDefinitionId)) {
      throw new InvalidRequestException(
          String.valueOf(format("%s must be provided", NGCommonEntityConstants.INFRA_DEFINITION_KEY)));
    }
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountId, orgIdentifier, projectIdentifier);

    InfrastructureEntity infrastructureEntity =
        infrastructureEntityService
            .get(accountId, orgIdentifier, projectIdentifier, scopeInfo, envId, infraDefinitionId)
            .orElseThrow(() -> {
              throw new NotFoundException(String.format(
                  "Infrastructure with identifier [%s] in project [%s], org [%s], environment [%s] not found",
                  infraDefinitionId, projectIdentifier, orgIdentifier, envId));
            });

    return InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureEntity)
        .getInfrastructureDefinitionConfig();
  }

  @GET
  @Path("management-groups")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure management groups", nickname = "getManagementGroups")
  public ResponseDTO<AzureManagementGroupsDTO> getManagementGroups(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(
        azureResourceService.getAzureManagementGroups(connectorRef, orgIdentifier, projectIdentifier, scopeInfo));
  }

  @GET
  @Path("locations")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets azure locations defined for a subscription", nickname = "getLocationsBySubscription")
  public ResponseDTO<AzureLocationsDTO> getLocations(
      @NotNull @QueryParam("connectorRef") String azureConnectorIdentifier,
      @QueryParam("subscriptionId") String subscriptionId,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(
        azureResourceService.getLocations(connectorRef, orgIdentifier, projectIdentifier, subscriptionId, scopeInfo));
  }

  @POST
  @Path("hosts")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Filter azure hosts by subscription, resourceGroup and tags", nickname = "filterAzureHosts")
  public ResponseDTO<List<String>> filterHosts(@QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId,
      @RequestBody(required = true, description = "Filter body") @Valid AzureListInstancesFilterDTO filter) {
    String subscriptionId = null;
    String resourceGroup = null;
    Map<String, String> tags = null;
    Boolean winRm = null;
    String hostConnectionType = null;

    if (filter != null) {
      subscriptionId = filter.getSubscriptionId();
      resourceGroup = filter.getResourceGroup();
      tags = filter.getTags();
      winRm = filter.getWinRm();
      hostConnectionType = filter.getHostConnectionType();
    }

    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId) || isEmpty(resourceGroup) || isEmpty(tags)
        || winRm == null || isEmpty(hostConnectionType)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure =
          (SshWinRmAzureInfrastructure) infrastructureDefinitionConfig.getSpec();

      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = sshWinRmAzureInfrastructure.getConnectorRef().getValue();

        if (isEmpty(azureConnectorIdentifier)) {
          throw new InvalidRequestException(
              format("azureConnectorRef defined in infrastructure with value='%s' evaluates to empty",
                  sshWinRmAzureInfrastructure.getConnectorReference().getExpressionValue()));
        }
      }

      if (isEmpty(subscriptionId)) {
        subscriptionId = ParameterFieldHelper.getParameterFieldValue(sshWinRmAzureInfrastructure.getSubscriptionId());

        if (isEmpty(subscriptionId)) {
          throw new InvalidRequestException(
              format("subscriptionId defined in infrastructure with value='%s' evaluates to empty",
                  sshWinRmAzureInfrastructure.getConnectorReference().getExpressionValue()));
        }
      }

      if (isEmpty(resourceGroup)) {
        resourceGroup = ParameterFieldHelper.getParameterFieldValue(sshWinRmAzureInfrastructure.getResourceGroup());

        if (isEmpty(resourceGroup)) {
          throw new InvalidRequestException(
              format("resourceGroup defined in infrastructure with value='%s' evaluates to empty",
                  sshWinRmAzureInfrastructure.getConnectorReference().getExpressionValue()));
        }
      }

      if (winRm == null) {
        winRm = infrastructureDefinitionConfig.getDeploymentType() == ServiceDefinitionType.WINRM;
      }

      if (isEmpty(tags)) {
        tags = ParameterFieldHelper.getParameterFieldValue(sshWinRmAzureInfrastructure.getTags());
      }

      if (isEmpty(hostConnectionType)) {
        hostConnectionType =
            ParameterFieldHelper.getParameterFieldValue(sshWinRmAzureInfrastructure.getHostConnectionType());
      }
    }

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    List<AzureHostResponse> hosts = azureResourceService.filterHosts(connectorRef, orgIdentifier, projectIdentifier,
        subscriptionId, resourceGroup, tags, winRm, hostConnectionType, scopeInfo);

    List<String> result =
        CollectionUtils.emptyIfNull(hosts).stream().map(AzureHostResponse::getAddress).collect(Collectors.toList());

    return ResponseDTO.newResponse(result);
  }

  @GET
  @Path("containerAppsManagedEnvironments")
  @Timed
  @ResponseMetered
  @ApiOperation(
      value = "Gets Azure Container Apps managed environments", nickname = "getAzureContainerAppsManagedEnvironments")
  public ResponseDTO<AzureContainerAppsManagedEnvironmentsDTO>
  getContainerAppsManagedEnvironments(@QueryParam("connectorRef") String azureConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("subscriptionId") String subscriptionId, @QueryParam("resourceGroup") String resourceGroup,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(azureConnectorIdentifier) || isEmpty(subscriptionId) || isEmpty(resourceGroup)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig =
          getInfrastructureDefinitionConfig(accountId, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (spec instanceof AzureContainerAppsInfrastructure) {
      AzureContainerAppsInfrastructure acaInfra = (AzureContainerAppsInfrastructure) spec;
      if (isEmpty(azureConnectorIdentifier)) {
        azureConnectorIdentifier = acaInfra.getConnectorReference().getValue();
      }
      if (isEmpty(subscriptionId)) {
        subscriptionId = ParameterFieldHelper.getParameterFieldValue(acaInfra.getSubscriptionId());
      }
      if (isEmpty(resourceGroup)) {
        resourceGroup = ParameterFieldHelper.getParameterFieldValue(acaInfra.getResourceGroup());
      }
    } else if (spec != null) {
      throw new InvalidRequestException("Infrastructure definition is not of type AzureContainerAppsInfrastructure");
    }

    IdentifierRef connectorRef = IdentifierRefHelper.getConnectorIdentifierRef(
        azureConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(azureResourceService.getContainerAppsManagedEnvironments(
        connectorRef, orgIdentifier, projectIdentifier, subscriptionId, resourceGroup, scopeInfo));
  }

  private void checkForAccessOrThrow(String accountId, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String permission, String action) {
    String exceptionMessage = format("unable to %s infrastructure(s)", action);
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(NGResourceType.ENVIRONMENT, envIdentifier), permission, exceptionMessage);
  }
}
