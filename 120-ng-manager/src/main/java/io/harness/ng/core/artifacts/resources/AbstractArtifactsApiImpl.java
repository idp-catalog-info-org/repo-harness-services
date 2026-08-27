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
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerResponseDTO;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.ng.core.artifacts.resources.acr.AcrArtifactApiUtils;
import io.harness.ng.core.artifacts.resources.docker.DockerArtifactApiUtils;
import io.harness.ng.core.artifacts.resources.githubpackages.GithubPackagesArtifactApiUtils;
import io.harness.ng.core.artifacts.resources.nexus.NexusArtifactApiUtils;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public abstract class AbstractArtifactsApiImpl {
  protected final DockerArtifactApiUtils dockerArtifactService;
  protected final AcrArtifactApiUtils acrArtifactService;
  protected final GithubPackagesArtifactApiUtils githubPackagesArtifactService;
  protected final NexusArtifactApiUtils nexusArtifactService;

  protected Response getDockerBuildDetailsInternal(
      String accountId, String orgIdentifier, String projectIdentifier, String imagePath, String connectorRef) {
    DockerResponseDTO buildDetails =
        dockerArtifactService.getBuildDetails(accountId, orgIdentifier, projectIdentifier, imagePath, connectorRef);
    return Response.ok().entity(buildDetails).build();
  }

  protected Response getAcrBuildDetailsInternal(String accountId, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String registry, String repository, String connectorRef) {
    AcrResponseDTO buildDetails = acrArtifactService.getBuildDetails(
        accountId, orgIdentifier, projectIdentifier, subscriptionId, registry, repository, connectorRef);
    return Response.ok().entity(buildDetails).build();
  }

  protected Response getAcrRegistriesInternal(
      String accountId, String orgIdentifier, String projectIdentifier, String subscriptionId, String connectorRef) {
    AcrRegistriesDTO registries =
        acrArtifactService.getRegistries(accountId, orgIdentifier, projectIdentifier, subscriptionId, connectorRef);
    return Response.ok().entity(registries).build();
  }

  protected Response getAcrRepositoriesInternal(String accountId, String orgIdentifier, String projectIdentifier,
      String subscriptionId, String registry, String connectorRef) {
    AcrRepositoriesDTO repositories = acrArtifactService.getRepositories(
        accountId, orgIdentifier, projectIdentifier, subscriptionId, registry, connectorRef);
    return Response.ok().entity(repositories).build();
  }

  protected Response getAzureSubscriptionsInternal(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorRef) {
    AzureSubscriptionsDTO subscriptions =
        acrArtifactService.getSubscriptions(accountId, orgIdentifier, projectIdentifier, connectorRef);
    return Response.ok().entity(subscriptions).build();
  }

  protected Response getGithubPackagesInternal(String accountId, String orgIdentifier, String projectIdentifier,
      String packageType, String org, String connectorRef) {
    GithubPackagesResponseDTO packages = githubPackagesArtifactService.getPackages(
        accountId, orgIdentifier, projectIdentifier, packageType, org, connectorRef);
    return Response.ok().entity(packages).build();
  }

  protected Response getGithubPackageVersionsInternal(String accountId, String orgIdentifier, String projectIdentifier,
      String packageName, String packageType, String org, String versionRegex, String connectorRef) {
    List<BuildDetails> versions = githubPackagesArtifactService.getPackageVersions(
        accountId, orgIdentifier, projectIdentifier, packageName, packageType, org, versionRegex, connectorRef);
    return Response.ok().entity(versions).build();
  }

  protected Response getGithubPackageLastSuccessfulVersionInternal(String accountId, String orgIdentifier,
      String projectIdentifier, String packageName, String packageType, String version, String versionRegex, String org,
      String connectorRef) {
    BuildDetails buildDetails = githubPackagesArtifactService.getLastSuccessfulVersion(accountId, orgIdentifier,
        projectIdentifier, packageName, packageType, version, versionRegex, org, connectorRef);
    return Response.ok().entity(buildDetails).build();
  }

  protected Response getNexusBuildDetailsInternal(String accountId, String orgIdentifier, String projectIdentifier,
      String repository, String repositoryPort, String repositoryFormat, String repositoryUrl, String artifactPath,
      String connectorRef, String groupId, String artifactId, String extension, String classifier, String packageName,
      String group) {
    NexusResponseDTO buildDetails = nexusArtifactService.getBuildDetails(accountId, orgIdentifier, projectIdentifier,
        repository, repositoryPort, repositoryFormat, repositoryUrl, artifactPath, connectorRef, groupId, artifactId,
        extension, classifier, packageName, group);
    return Response.ok().entity(buildDetails).build();
  }
}
