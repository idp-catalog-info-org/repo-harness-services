/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.salesforce;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.NGArtifactConstants;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.salesforceartifact.SalesforcePackageArtifactConfig;
import io.harness.cdng.artifact.resources.salesforce.dtos.SalesforcePackageDetailsDTO;
import io.harness.cdng.artifact.resources.salesforce.dtos.SalesforcePackageVersionDetailsDTO;
import io.harness.cdng.artifact.resources.salesforce.service.SalesforceArtifactResourceService;
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
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/salesforce")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class SalesforceArtifactResource {
  private final ArtifactResourceUtils artifactResourceUtils;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeInfoService scopeInfoService;

  @Inject SalesforceArtifactResourceService salesforceArtifactResourceService;

  @POST
  @Path("getPackageDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets package details for salesforce org", nickname = "getPackageDetailsForSalesforcePackage")
  public ResponseDTO<SalesforcePackageDetailsDTO> getPackageDetails(
      @QueryParam(NGArtifactConstants.CONNECTOR_REF) String salesforceConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      SalesforcePackageArtifactConfig salesforceArtifactsConfig =
          (SalesforcePackageArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(salesforceConnectorIdentifier)) {
        salesforceConnectorIdentifier = (String) salesforceArtifactsConfig.getConnectorRef().fetchFinalValue();
      }
    }

    String resolvedSalesforceConnector =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, salesforceConnectorIdentifier, fqnPath, gitEntityBasicInfo,
                serviceRef, null)
            .getValue();

    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(resolvedSalesforceConnector, accountId, orgIdentifier, projectIdentifier);

    artifactResourceUtils.checkConnectorAccess(connectorIdentifierRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(connectorIdentifierRef.getAccountIdentifier(),
              connectorIdentifierRef.getOrgIdentifier(), connectorIdentifierRef.getProjectIdentifier())
        : null;

    return ResponseDTO.newResponse(salesforceArtifactResourceService.getPackageDetails(
        connectorIdentifierRef, orgIdentifier, projectIdentifier, scopeInfo));
  }

  @POST
  @Path("getPackageVersionDetails")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Gets package version details for salesforce org",
      nickname = "getPackageVersionDetailsForSalesforcePackage")
  public ResponseDTO<SalesforcePackageVersionDetailsDTO>
  getPackageVersionDetails(@QueryParam(NGArtifactConstants.CONNECTOR_REF) String salesforceConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("packageId") String packageId,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      SalesforcePackageArtifactConfig salesforceArtifactsConfig =
          (SalesforcePackageArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(salesforceConnectorIdentifier)) {
        salesforceConnectorIdentifier = (String) salesforceArtifactsConfig.getConnectorRef().fetchFinalValue();
      }
      if (StringUtils.isBlank(packageId)) {
        packageId = (String) salesforceArtifactsConfig.getPackageId().fetchFinalValue();
      }
    }

    String resolvedSalesforceConnector =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, salesforceConnectorIdentifier, fqnPath, gitEntityBasicInfo,
                serviceRef, null)
            .getValue();

    String resolvedPackageId =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, packageId, fqnPath, gitEntityBasicInfo, serviceRef, null)
            .getValue();

    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(resolvedSalesforceConnector, accountId, orgIdentifier, projectIdentifier);

    artifactResourceUtils.checkConnectorAccess(connectorIdentifierRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(connectorIdentifierRef.getAccountIdentifier(),
              connectorIdentifierRef.getOrgIdentifier(), connectorIdentifierRef.getProjectIdentifier())
        : null;

    return ResponseDTO.newResponse(salesforceArtifactResourceService.getPackageVersionDetails(
        connectorIdentifierRef, resolvedPackageId, orgIdentifier, projectIdentifier, scopeInfo));
  }
}
