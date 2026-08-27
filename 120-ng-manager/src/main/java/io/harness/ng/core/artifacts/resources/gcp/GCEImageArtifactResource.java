/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.gcp;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GCEImageArtifactConfig;
import io.harness.cdng.artifact.resources.gcp.service.GCEImageResourceService;
import io.harness.common.NGExpressionUtils;
import io.harness.delegate.task.artifacts.gcp.GCEImageFilter;
import io.harness.delegate.task.artifacts.gcp.GCEImageLabel;
import io.harness.evaluators.CDYamlExpressionEvaluator;
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
import java.util.ArrayList;
import java.util.List;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/gce-image")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GCEImageArtifactResource {
  private final GCEImageResourceService gceImageResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeInfoService scopeInfoService;

  @POST
  @Path("versions")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "List Versions for GCE Image Artifacts", nickname = "listVersionsForGCEImageArtifact")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<List<BuildDetails>> listVersions(@QueryParam("connectorRef") String gcpConnectorRef,
      @QueryParam("project") String project, @QueryParam("versionRegex") String versionRegex,
      GCEImageRequestBody gceImageRequestBody,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext =
          artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier, projectIdentifier,
              pipelineIdentifier, gceImageRequestBody.getRuntimeInputYaml(), fqnPath, gitEntityBasicInfo, serviceRef);
    }

    List<GCEImageLabel> gceImageLabels = new ArrayList<>();
    List<GCEImageFilter> gceImageFilters = new ArrayList<>();

    if (gceImageRequestBody != null) {
      gceImageLabels = gceImageRequestBody.getLabels();
      gceImageFilters = gceImageRequestBody.getFilters();
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null
              ? null
              : baseEvaluatorWithContext.getContextMap().get(artifactResourceUtils.SERVICE_GIT_BRANCH));

      GCEImageArtifactConfig gceImageArtifactConfig = (GCEImageArtifactConfig) artifactSpecFromService;

      if (gceImageArtifactConfig != null) {
        if (StringUtils.isBlank(gcpConnectorRef)) {
          gcpConnectorRef = (String) gceImageArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (StringUtils.isBlank(project)) {
          project = (String) gceImageArtifactConfig.getProject().fetchFinalValue();
        }

        if (gceImageArtifactConfig.getLabels() != null) {
          if (NGExpressionUtils.isRuntimeField(gceImageArtifactConfig.getLabels().getExpressionValue())) {
            gceImageLabels = gceImageRequestBody.getLabels();
          } else {
            gceImageLabels = gceImageArtifactConfig.getLabels().getValue();
          }
        }

        if (gceImageArtifactConfig.getFilters() != null) {
          if (NGExpressionUtils.isRuntimeField(gceImageArtifactConfig.getFilters().getExpressionValue())) {
            gceImageFilters = gceImageRequestBody.getFilters();
          } else {
            gceImageFilters = gceImageArtifactConfig.getFilters().getValue();
          }
        }
      }

      CDYamlExpressionEvaluator yamlExpressionEvaluator =
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

      // Getting the resolved connectorRef in case of expressions
      gcpConnectorRef = artifactResourceUtils
                            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                projectIdentifier, pipelineIdentifier, gceImageRequestBody.getRuntimeInputYaml(),
                                gcpConnectorRef, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                            .getValue();

      // Getting the resolved project in case of expressions
      project = artifactResourceUtils
                    .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                        pipelineIdentifier, gceImageRequestBody.getRuntimeInputYaml(), project, fqnPath,
                        gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                    .getValue();

      // Getting the resolved value field in labels in case of expressions
      if (gceImageLabels != null) {
        for (GCEImageLabel label : gceImageLabels) {
          label.setValue(artifactResourceUtils
                             .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                 projectIdentifier, pipelineIdentifier, gceImageRequestBody.getRuntimeInputYaml(),
                                 label.getValue(), fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                             .getValue());
        }
      }

      // Getting the resolved value field in filters in case of expressions
      if (gceImageFilters != null) {
        for (GCEImageFilter filter : gceImageFilters) {
          filter.setValue(artifactResourceUtils
                              .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                  projectIdentifier, pipelineIdentifier, gceImageRequestBody.getRuntimeInputYaml(),
                                  filter.getValue(), fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                              .getValue());
        }
      }
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(gcpConnectorRef, accountId, orgIdentifier, projectIdentifier);

    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    List<BuildDetails> builds = gceImageResourceService.listVersions(connectorRef, accountId, orgIdentifier,
        projectIdentifier, project, gceImageLabels, gceImageFilters, versionRegex, scopeInfo);

    return ResponseDTO.newResponse(builds);
  }
}
