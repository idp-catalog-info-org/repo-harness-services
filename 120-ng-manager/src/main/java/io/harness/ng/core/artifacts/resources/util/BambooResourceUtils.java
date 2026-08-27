/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.util;

import static io.harness.cdng.service.steps.constants.ServiceStepV3Constants.SERVICE_GIT_BRANCH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.BambooArtifactConfig;
import io.harness.cdng.artifact.resources.bamboo.BambooResourceService;
import io.harness.cdng.artifact.resources.bamboo.dtos.BambooPlanKeysDTO;
import io.harness.evaluators.CDYamlExpressionEvaluator;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class BambooResourceUtils {
  @Inject ArtifactResourceUtils artifactResourceUtils;
  @Inject BambooResourceService bambooResourceService;
  @Inject ScopeInfoService scopeInfoService;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;
  public BambooPlanKeysDTO getBambooPlanKeys(String bambooConnectorIdentifier, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo, String fqnPath,
      String serviceRef, String runtimeInputYaml) {
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
      BambooArtifactConfig bambooArtifactConfig = (BambooArtifactConfig) artifactSpecFromService;
      if (isEmpty(bambooConnectorIdentifier)) {
        bambooConnectorIdentifier = bambooArtifactConfig.getConnectorRef().getValue();
      }
    }
    bambooConnectorIdentifier =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, bambooConnectorIdentifier, fqnPath, gitEntityBasicInfo,
                serviceRef,
                baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator())
            .getValue();
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(bambooConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return bambooResourceService.getPlanName(connectorRef, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public List<String> getBambooArtifactPaths(String bambooConnectorIdentifier, String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String planName, GitEntityFindInfoDTO gitEntityBasicInfo,
      String fqnPath, String serviceRef, String runtimeInputYaml) {
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
      BambooArtifactConfig bambooArtifactConfig = (BambooArtifactConfig) artifactSpecFromService;
      if (isEmpty(bambooConnectorIdentifier)) {
        bambooConnectorIdentifier = bambooArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(planName)) {
        planName = bambooArtifactConfig.getPlanKey().fetchFinalValue().toString();
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();
    planName = artifactResourceUtils
                   .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                       pipelineIdentifier, runtimeInputYaml, planName, fqnPath, gitEntityBasicInfo, serviceRef,
                       yamlExpressionEvaluator)
                   .getValue();
    bambooConnectorIdentifier =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, bambooConnectorIdentifier, fqnPath, gitEntityBasicInfo,
                serviceRef, yamlExpressionEvaluator)
            .getValue();
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(bambooConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return bambooResourceService.getArtifactPath(connectorRef, orgIdentifier, projectIdentifier, planName, scopeInfo);
  }

  public List<BuildDetails> getBambooArtifactBuildDetails(String bambooConnectorIdentifier, String accountId,
      String orgIdentifier, String projectIdentifier, String pipelineIdentifier, String planName,
      List<String> artifactPath, GitEntityFindInfoDTO gitEntityBasicInfo, String fqnPath, String serviceRef,
      String runtimeInputYaml) {
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
      BambooArtifactConfig bambooArtifactConfig = (BambooArtifactConfig) artifactSpecFromService;
      if (isEmpty(bambooConnectorIdentifier)) {
        bambooConnectorIdentifier = bambooArtifactConfig.getConnectorRef().getValue();
      }
      if (isEmpty(planName)) {
        planName = bambooArtifactConfig.getPlanKey().fetchFinalValue().toString();
      }
      if (isEmpty(artifactPath)) {
        artifactPath = bambooArtifactConfig.getArtifactPaths().getValue();
      }
    }
    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();
    planName = artifactResourceUtils
                   .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                       pipelineIdentifier, runtimeInputYaml, planName, fqnPath, gitEntityBasicInfo, serviceRef,
                       yamlExpressionEvaluator)
                   .getValue();
    bambooConnectorIdentifier =
        artifactResourceUtils
            .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier, projectIdentifier,
                pipelineIdentifier, runtimeInputYaml, bambooConnectorIdentifier, fqnPath, gitEntityBasicInfo,
                serviceRef, yamlExpressionEvaluator)
            .getValue();
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(bambooConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorRef);
    ScopeInfo scopeInfo =
        pmsFeatureFlagHelper.isEnabled(accountId, FeatureName.PL_USE_SCOPE_INFO_FOR_CONNECTOR_ENTITY_V2)
        ? scopeInfoService.getScopeInfo(
              connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier())
        : null;
    return bambooResourceService.getBuilds(
        connectorRef, orgIdentifier, projectIdentifier, planName, artifactPath, scopeInfo);
  }
}
