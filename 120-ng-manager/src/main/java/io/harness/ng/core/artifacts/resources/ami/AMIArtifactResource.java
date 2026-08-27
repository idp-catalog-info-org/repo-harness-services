/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.ami;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.ami.AMITagObject;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeInfoResolutionApi;
import io.harness.beans.ScopeInfoResolutionExemptedApi;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.resources.ami.AMIResourceService;
import io.harness.common.NGExpressionUtils;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
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
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/ami")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@ScopeInfoResolutionApi
public class AMIArtifactResource {
  private final AMIResourceService amiResourceService;

  private final ArtifactResourceUtils artifactResourceUtils;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private final ScopeInfoService scopeInfoService;

  @POST
  @Path("tags")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "List Tags for AMI Artifacts", nickname = "listTagsForAMIArtifact")
  public ResponseDTO<List<AMITagObject>> listTags(@QueryParam("connectorRef") String awsConnectorRef,
      @QueryParam("region") String region, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo, @Context ScopeInfo scopeInfo) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      if (scopeInfo != null) {
        baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(
            scopeInfo, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);

      } else {
        baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
            projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
      }
    }

    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null
              ? null
              : baseEvaluatorWithContext.getContextMap().get(artifactResourceUtils.SERVICE_GIT_BRANCH));

      AMIArtifactConfig amiArtifactConfig = (AMIArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(awsConnectorRef)) {
        awsConnectorRef = (String) amiArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(region)) {
        region = (String) amiArtifactConfig.getRegion().fetchFinalValue();
      }
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    // Getting the resolved connectorRef  in case of expressions
    String resolvedAwsConnectorRef = artifactResourceUtils
                                         .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                             projectIdentifier, pipelineIdentifier, runtimeInputYaml, awsConnectorRef,
                                             fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                         .getValue();

    // Getting the resolved project  in case of expressions
    String resolvedRegion = artifactResourceUtils
                                .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                    projectIdentifier, pipelineIdentifier, runtimeInputYaml, region, fqnPath,
                                    gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                .getValue();

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedAwsConnectorRef, accountId, orgIdentifier, projectIdentifier);

    ScopeInfo scopeInfoForRef =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    List<String> response = amiResourceService.listTags(
        connectorRef, accountId, orgIdentifier, projectIdentifier, resolvedRegion, scopeInfoForRef);

    List<AMITagObject> amiTags = new ArrayList<>();

    for (String s : response) {
      AMITagObject amiTagObject = AMITagObject.builder().tagName(s).build();

      amiTags.add(amiTagObject);
    }

    return ResponseDTO.newResponse(amiTags);
  }

  @POST
  @Path("versions")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "List Versions for AMI Artifacts", nickname = "listVersionsForAMIArtifact")
  @ScopeInfoResolutionExemptedApi
  public ResponseDTO<List<BuildDetails>> listVersions(@QueryParam("connectorRef") String awsConnectorRef,
      @QueryParam("region") String region, @QueryParam("versionRegex") String versionRegex,
      AMIRequestBody amiRequestBody, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
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
              pipelineIdentifier, amiRequestBody.getRuntimeInputYaml(), fqnPath, gitEntityBasicInfo, serviceRef);
    }

    List<AMITag> amiTags = new ArrayList<>();

    List<AMIFilter> amiFilters = new ArrayList<>();

    if (amiRequestBody != null) {
      amiTags = amiRequestBody.getTags();

      amiFilters = amiRequestBody.getFilters();
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null
              ? null
              : baseEvaluatorWithContext.getContextMap().get(artifactResourceUtils.SERVICE_GIT_BRANCH));

      AMIArtifactConfig amiArtifactConfig = (AMIArtifactConfig) artifactSpecFromService;

      if (amiArtifactConfig != null) {
        if (StringUtils.isBlank(awsConnectorRef)) {
          awsConnectorRef = (String) amiArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (StringUtils.isBlank(region)) {
          region = (String) amiArtifactConfig.getRegion().fetchFinalValue();
        }

        if (amiArtifactConfig.getTags() != null) {
          if (NGExpressionUtils.isRuntimeField(amiArtifactConfig.getTags().getExpressionValue())) {
            amiTags = amiRequestBody.getTags();
          } else {
            amiTags = amiArtifactConfig.getTags().getValue();
          }
        }

        if (amiArtifactConfig.getFilters() != null) {
          if (NGExpressionUtils.isRuntimeField(amiArtifactConfig.getFilters().getExpressionValue())) {
            amiFilters = amiRequestBody.getFilters();
          } else {
            amiFilters = amiArtifactConfig.getFilters().getValue();
          }
        }
      }

      CDYamlExpressionEvaluator yamlExpressionEvaluator =
          baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

      // Getting the resolved connectorRef  in case of expressions
      awsConnectorRef = artifactResourceUtils
                            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                projectIdentifier, pipelineIdentifier, amiRequestBody.getRuntimeInputYaml(),
                                awsConnectorRef, fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                            .getValue();

      // Getting the resolved project  in case of expressions
      region = artifactResourceUtils
                   .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                       pipelineIdentifier, amiRequestBody.getRuntimeInputYaml(), region, fqnPath, gitEntityBasicInfo,
                       serviceRef, yamlExpressionEvaluator)
                   .getValue();

      // Getting the resolved value field in tags in case of expressions
      if (amiTags != null) {
        for (AMITag tag : amiTags) {
          tag.setValue(artifactResourceUtils
                           .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                               projectIdentifier, pipelineIdentifier, amiRequestBody.getRuntimeInputYaml(),
                               tag.getValue(), fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                           .getValue());
        }
      }

      // Getting the resolved value field in tags in case of expressions
      if (amiFilters != null) {
        for (AMIFilter filter : amiFilters) {
          filter.setValue(artifactResourceUtils
                              .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                  projectIdentifier, pipelineIdentifier, amiRequestBody.getRuntimeInputYaml(),
                                  filter.getValue(), fqnPath, gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                              .getValue());
        }
      }
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountId, orgIdentifier, projectIdentifier);

    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;

    List<BuildDetails> builds = amiResourceService.listVersions(connectorRef, accountId, orgIdentifier,
        projectIdentifier, region, amiTags, amiFilters, versionRegex, scopeInfo);

    return ResponseDTO.newResponse(builds);
  }
}
