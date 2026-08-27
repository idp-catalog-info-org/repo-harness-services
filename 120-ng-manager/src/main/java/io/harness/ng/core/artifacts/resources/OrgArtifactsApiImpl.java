/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.artifacts.resources.acr.AcrArtifactApiUtils;
import io.harness.ng.core.artifacts.resources.docker.DockerArtifactApiUtils;
import io.harness.ng.core.artifacts.resources.githubpackages.GithubPackagesArtifactApiUtils;
import io.harness.ng.core.artifacts.resources.nexus.NexusArtifactApiUtils;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.ng.v1.OrgArtifactsApi;

import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@NextGenManagerAuth
@OwnedBy(HarnessTeam.CDC)
public class OrgArtifactsApiImpl extends AbstractArtifactsApiImpl implements OrgArtifactsApi {
  @Inject
  public OrgArtifactsApiImpl(DockerArtifactApiUtils dockerArtifactService, AcrArtifactApiUtils acrArtifactService,
      GithubPackagesArtifactApiUtils githubPackagesArtifactService, NexusArtifactApiUtils nexusArtifactService) {
    super(dockerArtifactService, acrArtifactService, githubPackagesArtifactService, nexusArtifactService);
  }

  @Override
  public Response getOrgScopedDockerBuildDetails(
      String org, String imagePath, String connectorRef, String harnessAccount) {
    return super.getDockerBuildDetailsInternal(harnessAccount, org, null, imagePath, connectorRef);
  }

  @Override
  public Response getOrgScopedAcrBuildDetails(String org, String subscriptionId, String registry, String repository,
      String connectorRef, String harnessAccount) {
    return super.getAcrBuildDetailsInternal(
        harnessAccount, org, null, subscriptionId, registry, repository, connectorRef);
  }

  @Override
  public Response getOrgScopedAcrRegistries(
      String org, @NotNull String connectorRef, @NotNull String subscriptionId, String harnessAccount) {
    return super.getAcrRegistriesInternal(harnessAccount, org, null, subscriptionId, connectorRef);
  }

  @Override
  public Response getOrgScopedAcrRepositories(String org, String registry, @NotNull String connectorRef,
      @NotNull String subscriptionId, String harnessAccount) {
    return super.getAcrRepositoriesInternal(harnessAccount, org, null, subscriptionId, registry, connectorRef);
  }

  @Override
  public Response getOrgScopedAzureSubscriptionsForAcr(String org, String connectorRef, String harnessAccount) {
    return super.getAzureSubscriptionsInternal(harnessAccount, org, null, connectorRef);
  }

  @Override
  public Response getOrgScopedGithubPackages(
      String org, @NotNull String packageType, String githubOrg, String connectorRef, String harnessAccount) {
    return super.getGithubPackagesInternal(harnessAccount, org, null, packageType, githubOrg, connectorRef);
  }

  @Override
  public Response getOrgScopedGithubPackagesLastSuccessfulVersion(String org, @NotNull String connectorRef,
      @NotNull String packageName, @NotNull String packageType, String githubOrg, String version, String versionRegex,
      String harnessAccount) {
    return super.getGithubPackageLastSuccessfulVersionInternal(
        harnessAccount, org, null, packageName, packageType, version, versionRegex, githubOrg, connectorRef);
  }

  @Override
  public Response getOrgScopedGithubPackagesVersions(String org, @NotNull String connectorRef,
      @NotNull String packageName, @NotNull String packageType, String githubOrg, String versionRegex,
      String harnessAccount) {
    return super.getGithubPackageVersionsInternal(
        harnessAccount, org, null, packageName, packageType, githubOrg, versionRegex, connectorRef);
  }

  @Override
  public Response getOrgScopedNexusBuildDetails(String org, String repository, String repositoryPort,
      String repositoryFormat, String repositoryUrl, String artifactPath, String connectorRef, String groupId,
      String artifactId, String extension, String classifier, String packageName, String group, String harnessAccount) {
    return super.getNexusBuildDetailsInternal(harnessAccount, org, null, repository, repositoryPort, repositoryFormat,
        repositoryUrl, artifactPath, connectorRef, groupId, artifactId, extension, classifier, packageName, group);
  }
}
