/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.aws.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.aws.service.AwsResourceServiceImpl;
import io.harness.cdng.infra.definition.config.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.AwsBaseInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.AwsEKSException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.AwsListInstancesFilterDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.yaml.infra.HostConnectionTypeKind;

import software.wings.beans.NameValuePair;
import software.wings.service.impl.aws.model.AwsCFTemplateParamsData;
import software.wings.service.impl.aws.model.AwsEC2Instance;
import software.wings.service.impl.aws.model.AwsVPC;

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
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Api("aws")
@Path("/aws/aws-helper")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AwsHelperResource {
  private final AwsResourceServiceImpl awsHelperService;
  private final InfrastructureEntityService infrastructureEntityService;
  private final ArtifactResourceUtils artifactResourceUtils;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final ScopeInfoService scopeInfoService;

  @GET
  @Path("regions")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get all the AWS regions defined in the application", nickname = "RegionsForAws")
  public ResponseDTO<Map<String, String>> listRegions() {
    return ResponseDTO.newResponse(awsHelperService.getRegions());
  }

  @GET
  @Path("/aws-regions")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get AWS regions with name-code pairs", nickname = "listAwsRegions")
  public ResponseDTO<List<NameValuePair>> listAwsRegions() {
    return ResponseDTO.newResponse(awsHelperService.getAwsRegions());
  }

  @POST
  @Path("cf-parameters")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get Cloudformation parameters from a template", nickname = "CFParametersForAws")
  public ResponseDTO<List<AwsCFTemplateParamsData>> listCFParameterKeys(@QueryParam("type") @NotNull String type,
      @QueryParam("region") @NotNull String region, @QueryParam("isBranch") boolean isBranch,
      @QueryParam("branch") String branch, @QueryParam("filePath") String templatePath,
      @QueryParam("commitId") String commitId, @QueryParam("awsConnectorRef") @NotNull String awsConnectorRef,
      @QueryParam("gitConnectorRef") String gitConnectorRefParam, @QueryParam("repoName") String repoName,
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, String data) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);

    ScopeInfo awsScopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());
    List<AwsCFTemplateParamsData> keys = awsHelperService.getCFparametersKeys(type, region, isBranch, branch, repoName,
        templatePath, commitId, connectorRef, data, gitConnectorRefParam, accountIdentifier, orgIdentifier,
        projectIdentifier, awsScopeInfo);

    return ResponseDTO.newResponse(keys);
  }

  @GET
  @Path("cf-capabilities")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get the Cloudformation capabilities", nickname = "CFCapabilitiesForAws")
  public ResponseDTO<List<String>> listCFCapabilities() {
    return ResponseDTO.newResponse(awsHelperService.getCapabilities());
  }

  @GET
  @Path("cf-states")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get all the Cloudformation states for a stack", nickname = "CFStatesForAws")
  public ResponseDTO<Set<String>> listCFStates() {
    return ResponseDTO.newResponse(awsHelperService.getCFStates());
  }

  @GET
  @Path("iam-roles")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get all the IAM roles", nickname = "getIamRolesForAws")
  public ResponseDTO<Map<String, String>> listIamRoles(@NotNull @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return ResponseDTO.newResponse(
        awsHelperService.getRolesARNs(connectorRef, orgIdentifier, projectIdentifier, region, scopeInfo));
  }

  @POST
  @Path("hosts")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get all the IAM hosts", nickname = "filterAwsHosts")
  public ResponseDTO<List<String>> filterHosts(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId,
      @RequestBody(required = true, description = "Filter body") @Valid AwsListInstancesFilterDTO filterDTO) {
    Boolean isWinRm = null;
    String region = null;
    String autoScalingGroupName = null;
    List<String> vpcIds = null;
    Map<String, String> tags = null;
    String hostConnectionType = null;

    if (filterDTO != null) {
      isWinRm = filterDTO.getWinRm();
      region = filterDTO.getRegion();
      autoScalingGroupName = filterDTO.getAutoScalingGroupName();
      hostConnectionType = filterDTO.getHostConnectionType();
      vpcIds = filterDTO.getVpcIds();
      tags = filterDTO.getTags();
    }

    if (isEmpty(awsConnectorRef) || isWinRm == null || isEmpty(region) || isEmpty(autoScalingGroupName)
        || isEmpty(vpcIds) || isEmpty(tags) || isEmpty(hostConnectionType)) {
      if (isEmpty(envId)) {
        throw new InvalidRequestException(
            String.valueOf(format("%s must be provided", NGCommonEntityConstants.ENVIRONMENT_KEY)));
      }

      if (isEmpty(infraDefinitionId)) {
        throw new InvalidRequestException(
            String.valueOf(format("%s must be provided", NGCommonEntityConstants.INFRA_DEFINITION_KEY)));
      }

      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      SshWinRmAwsInfrastructure sshWinRmAwsInfrastructure =
          (SshWinRmAwsInfrastructure) infrastructureDefinitionConfig.getSpec();

      if (isEmpty(awsConnectorRef)) {
        awsConnectorRef = sshWinRmAwsInfrastructure.getConnectorReference().getValue();

        if (isEmpty(awsConnectorRef)) {
          throw new InvalidRequestException(
              format("awsConnectorRef defined in infrastructure with value='%s' evaluates to empty",
                  sshWinRmAwsInfrastructure.getConnectorReference().getExpressionValue()));
        }
      }

      if (isEmpty(region)) {
        region = sshWinRmAwsInfrastructure.getRegion().getValue();

        if (isEmpty(region)) {
          throw new InvalidRequestException(
              format("region defined in infrastructure with value='%s' evaluates to empty",
                  sshWinRmAwsInfrastructure.getRegion().getExpressionValue()));
        }
      }

      if (isWinRm == null) {
        isWinRm = infrastructureDefinitionConfig.getDeploymentType() == ServiceDefinitionType.WINRM;
      }

      if (isEmpty(autoScalingGroupName)) {
        autoScalingGroupName = ParameterFieldHelper.getParameterFieldValue(sshWinRmAwsInfrastructure.getAsgName());
      }

      if (isEmpty(hostConnectionType)) {
        hostConnectionType =
            ParameterFieldHelper.getParameterFieldValue(sshWinRmAwsInfrastructure.getHostConnectionType());
      }

      if (sshWinRmAwsInfrastructure.getAwsInstanceFilter() != null) {
        if (isEmpty(vpcIds)) {
          vpcIds =
              ParameterFieldHelper.getParameterFieldValue(sshWinRmAwsInfrastructure.getAwsInstanceFilter().getVpcs());
        }

        if (isEmpty(tags)) {
          tags =
              ParameterFieldHelper.getParameterFieldValue(sshWinRmAwsInfrastructure.getAwsInstanceFilter().getTags());
        }
      }
    }

    String hostConnectionTypeFinal = hostConnectionType;

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    List<AwsEC2Instance> instances =
        awsHelperService.filterHosts(connectorRef, isWinRm, region, vpcIds, tags, autoScalingGroupName, scopeInfo);
    List<String> result =
        CollectionUtils.emptyIfNull(instances)
            .stream()
            .map(instance
                -> HostConnectionTypeKind.PRIVATE_IP.equalsIgnoreCase(hostConnectionTypeFinal) ? instance.getPrivateIp()
                                                                                               : instance.getPublicIp())
            .collect(Collectors.toList());

    return ResponseDTO.newResponse(result);
  }

  @GET
  @Path("vpcs")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get all the vpcs", nickname = "vpcs")
  public ResponseDTO<List<AwsVPC>> getVpcs(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((SshWinRmAwsInfrastructure) spec).getRegion().getValue();
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return ResponseDTO.newResponse(
        awsHelperService.getVPCs(connectorRef, orgIdentifier, projectIdentifier, region, scopeInfo));
  }

  @GET
  @Path("tags")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get all the tags", nickname = "tags")
  public ResponseDTO<Set<String>> getTags(@NotNull @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("region") String region) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    Map<String, String> tags =
        awsHelperService.getTags(connectorRef, orgIdentifier, projectIdentifier, region, scopeInfo);
    return ResponseDTO.newResponse(tags.keySet());
  }

  @GET
  @Path("v2/tags")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get all the tags V2", nickname = "tagsV2")
  public ResponseDTO<Set<String>> getTagsV2(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((SshWinRmAwsInfrastructure) spec).getRegion().getValue();
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    Map<String, String> tags =
        awsHelperService.getTags(connectorRef, orgIdentifier, projectIdentifier, region, scopeInfo);
    return ResponseDTO.newResponse(tags.keySet());
  }

  @GET
  @Path("load-balancers")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get load balancers", nickname = "loadBalancers")
  public ResponseDTO<List<String>> getLoadBalancers(@NotNull @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam("region") String region) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return ResponseDTO.newResponse(
        awsHelperService.getLoadBalancers(connectorRef, orgIdentifier, projectIdentifier, region, scopeInfo));
  }

  @GET
  @Path("auto-scaling-groups")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get auto scaling groups", nickname = "autoScalingGroups")
  public ResponseDTO<List<String>> getASGNames(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      if (spec instanceof AwsBaseInfrastructure) {
        region = ((AwsBaseInfrastructure) spec).getRegion().getValue();
      }

      if (spec instanceof SshWinRmAwsInfrastructure) {
        region = ((SshWinRmAwsInfrastructure) spec).getRegion().getValue();
      }
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return ResponseDTO.newResponse(
        awsHelperService.getASGNames(connectorRef, orgIdentifier, projectIdentifier, region, scopeInfo));
  }

  @GET
  @Path("clusters")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get clusters", nickname = "clusters")
  public ResponseDTO<List<String>> getClusterNames(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((EcsInfrastructure) spec).getRegion().getValue();
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(
        awsHelperService.getClusterNames(connectorRef, orgIdentifier, projectIdentifier, region, scopeInfo));
  }

  @GET
  @Path("ecs-services")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get ECS services in a cluster", nickname = "ecsServices")
  public ResponseDTO<List<String>> getECSServiceNames(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region,
      @NotNull @QueryParam("cluster") String cluster,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((EcsInfrastructure) spec).getRegion().getValue();
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(awsHelperService.getECSServiceNames(
        connectorRef, orgIdentifier, projectIdentifier, region, cluster, scopeInfo));
  }

  @GET
  @Path("elastic-load-balancers")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get elastic load balancers", nickname = "elastic load balancers")
  public ResponseDTO<List<String>> getElasticLoadBalancers(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((AwsBaseInfrastructure) spec).getRegion().getValue();
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(awsHelperService.getElasticLoadBalancerNames(
        connectorRef, orgIdentifier, projectIdentifier, region, scopeInfo));
  }

  @GET
  @Path("listeners")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get elastic load balancer listeners ", nickname = "listeners")
  public ResponseDTO<Map<String, String>> getElasticLoadBalancerListenersArn(
      @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region,
      @NotNull @QueryParam("elasticLoadBalancer") String elasticLoadBalancer,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((AwsBaseInfrastructure) spec).getRegion().getValue();
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(awsHelperService.getElasticLoadBalancerListenersArn(
        connectorRef, orgIdentifier, projectIdentifier, region, elasticLoadBalancer, scopeInfo));
  }

  @GET
  @Path("listener-rules-arns")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get elastic load balancer listener rules", nickname = "listener rules")
  public ResponseDTO<List<String>> getElasticLoadBalancerListenerRules(
      @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region,
      @NotNull @QueryParam("elasticLoadBalancer") String elasticLoadBalancer,
      @NotNull @QueryParam("listenerArn") String listenerArn,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((AwsBaseInfrastructure) spec).getRegion().getValue();
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(awsHelperService.getElasticLoadBalancerListenerRules(
        connectorRef, orgIdentifier, projectIdentifier, region, elasticLoadBalancer, listenerArn, scopeInfo));
  }

  @GET
  @Path("target-group-arns")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get target groups attached to listener rule", nickname = "target groups")
  public ResponseDTO<List<String>> getTargetGroupsAttachedToListenerRule(
      @QueryParam("awsConnectorRef") String awsConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("region") String region,
      @NotNull @QueryParam("elasticLoadBalancer") String elasticLoadBalancer,
      @NotNull @QueryParam("listenerArn") String listenerArn,
      @NotNull @QueryParam("listenerRuleArn") String listenerRuleArn,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;
    if (isEmpty(awsConnectorRef) || isEmpty(region)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }

    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    if (isEmpty(region) && spec != null) {
      region = ((AwsBaseInfrastructure) spec).getRegion().getValue();
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(awsHelperService.getTargetGroupsAttachedToListenerRule(connectorRef, orgIdentifier,
        projectIdentifier, region, elasticLoadBalancer, listenerArn, listenerRuleArn, scopeInfo));
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
  @Path("eks/clusters")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get EKS clusters list", nickname = "getEKSClusterNames")
  public ResponseDTO<List<String>> getEKSClusterNames(@QueryParam("awsConnectorRef") String awsConnectorRef,
      @QueryParam("region") String region,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_KEY) String envId,
      @Parameter(description = NGCommonEntityConstants.INFRADEF_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.INFRA_DEFINITION_KEY) String infraDefinitionId) {
    Infrastructure spec = null;

    if (isEmpty(awsConnectorRef)) {
      InfrastructureDefinitionConfig infrastructureDefinitionConfig = getInfrastructureDefinitionConfig(
          accountIdentifier, orgIdentifier, projectIdentifier, envId, infraDefinitionId);
      spec = infrastructureDefinitionConfig.getSpec();
    }
    if (isEmpty(awsConnectorRef) && spec != null) {
      awsConnectorRef = spec.getConnectorReference().getValue();
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    if (awsHelperService.isEKSClusterConfiguredManually((K8sAwsInfrastructure) spec)) {
      throw new AwsEKSException("Provide cluster name for manual configuration");
    }
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(
        connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier());

    return ResponseDTO.newResponse(
        awsHelperService.getEKSClusterNames(connectorRef, orgIdentifier, projectIdentifier, region, scopeInfo));
  }
}
