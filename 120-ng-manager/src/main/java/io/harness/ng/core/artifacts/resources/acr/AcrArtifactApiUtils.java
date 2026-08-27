/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.acr;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.acr.service.AcrResourceService;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDP)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class AcrArtifactApiUtils {
  private final AcrResourceService acrResourceService;
  private final AzureResourceService azureResourceService;
  private final ScopeInfoService scopeInfoService;
  private final ArtifactResourceUtils artifactResourceUtils;

  public AcrResponseDTO getBuildDetails(String accountId, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String registry, String repository, String connectorRef) {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getConnectorIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorIdentifierRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(connectorIdentifierRef.getAccountIdentifier(),
        connectorIdentifierRef.getOrgIdentifier(), connectorIdentifierRef.getProjectIdentifier());

    return acrResourceService.getBuildDetails(
        connectorIdentifierRef, subscriptionId, registry, repository, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public AcrRegistriesDTO getRegistries(
      String accountId, String orgIdentifier, String projectIdentifier, String subscriptionId, String connectorRef) {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getConnectorIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorIdentifierRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(connectorIdentifierRef.getAccountIdentifier(),
        connectorIdentifierRef.getOrgIdentifier(), connectorIdentifierRef.getProjectIdentifier());

    return acrResourceService.getRegistries(
        connectorIdentifierRef, orgIdentifier, projectIdentifier, subscriptionId, scopeInfo);
  }

  public AcrRepositoriesDTO getRepositories(String accountId, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String registry, String connectorRef) {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getConnectorIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorIdentifierRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(connectorIdentifierRef.getAccountIdentifier(),
        connectorIdentifierRef.getOrgIdentifier(), connectorIdentifierRef.getProjectIdentifier());

    return acrResourceService.getRepositories(
        connectorIdentifierRef, orgIdentifier, projectIdentifier, subscriptionId, registry, scopeInfo);
  }

  public AzureSubscriptionsDTO getSubscriptions(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorRef) {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getConnectorIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorIdentifierRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(connectorIdentifierRef.getAccountIdentifier(),
        connectorIdentifierRef.getOrgIdentifier(), connectorIdentifierRef.getProjectIdentifier());

    return azureResourceService.getSubscriptions(connectorIdentifierRef, orgIdentifier, projectIdentifier, scopeInfo);
  }
}
