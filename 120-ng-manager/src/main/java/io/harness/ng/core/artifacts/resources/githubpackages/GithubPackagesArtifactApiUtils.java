/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.githubpackages;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.cdng.artifact.resources.githubpackages.service.GithubPackagesResourceService;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.utils.IdentifierRefHelper;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class GithubPackagesArtifactApiUtils {
  private final GithubPackagesResourceService githubPackagesResourceService;
  private final ScopeInfoService scopeInfoService;
  private final ArtifactResourceUtils artifactResourceUtils;

  public GithubPackagesResponseDTO getPackages(String accountId, String orgIdentifier, String projectIdentifier,
      String packageType, String org, String connectorRef) {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getConnectorIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorIdentifierRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(connectorIdentifierRef.getAccountIdentifier(),
        connectorIdentifierRef.getOrgIdentifier(), connectorIdentifierRef.getProjectIdentifier());

    return githubPackagesResourceService.getPackageDetails(
        connectorIdentifierRef, accountId, orgIdentifier, projectIdentifier, packageType, org, scopeInfo);
  }

  public List<BuildDetails> getPackageVersions(String accountId, String orgIdentifier, String projectIdentifier,
      String packageName, String packageType, String org, String versionRegex, String connectorRef) {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getConnectorIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorIdentifierRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(connectorIdentifierRef.getAccountIdentifier(),
        connectorIdentifierRef.getOrgIdentifier(), connectorIdentifierRef.getProjectIdentifier());

    return githubPackagesResourceService.getVersionsOfPackage(connectorIdentifierRef, packageName, packageType,
        versionRegex, org, accountId, orgIdentifier, projectIdentifier, scopeInfo);
  }

  public BuildDetails getLastSuccessfulVersion(String accountId, String orgIdentifier, String projectIdentifier,
      String packageName, String packageType, String version, String versionRegex, String org, String connectorRef) {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getConnectorIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    artifactResourceUtils.checkConnectorAccess(connectorIdentifierRef);
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(connectorIdentifierRef.getAccountIdentifier(),
        connectorIdentifierRef.getOrgIdentifier(), connectorIdentifierRef.getProjectIdentifier());

    return githubPackagesResourceService.getLastSuccessfulVersion(connectorIdentifierRef, packageName, packageType,
        version, versionRegex, org, accountId, orgIdentifier, projectIdentifier, scopeInfo);
  }
}
