/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources;

import javax.ws.rs.core.Response;

public class ProjectArtifactsApiImplTest extends AbstractArtifactsApiImplTest {
  private ProjectArtifactsApiImpl projectArtifactsApi;

  @Override
  protected void initializeApiInstance() {
    projectArtifactsApi = new ProjectArtifactsApiImpl(
        dockerArtifactService, acrArtifactService, githubPackagesArtifactService, nexusArtifactService);
  }

  @Override
  protected String getExpectedOrgIdentifier() {
    return ORG_ID;
  }

  @Override
  protected String getExpectedProjectIdentifier() {
    return PROJECT_ID;
  }

  @Override
  protected Response callGetDockerBuildDetails(String imagePath, String connectorRef) {
    return projectArtifactsApi.getDockerBuildDetails(ORG_ID, PROJECT_ID, imagePath, connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAcrBuildDetails(
      String subscriptionId, String registry, String repository, String connectorRef) {
    return projectArtifactsApi.getAcrBuildDetails(
        ORG_ID, PROJECT_ID, subscriptionId, registry, repository, connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAcrRegistries(String subscriptionId, String connectorRef) {
    return projectArtifactsApi.getAcrRegistries(ORG_ID, PROJECT_ID, connectorRef, subscriptionId, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAcrRepositories(String subscriptionId, String registry, String connectorRef) {
    return projectArtifactsApi.getAcrRepositories(
        ORG_ID, PROJECT_ID, registry, connectorRef, subscriptionId, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAzureSubscriptions(String connectorRef) {
    return projectArtifactsApi.getAzureSubscriptionsForAcr(ORG_ID, PROJECT_ID, null, connectorRef, null, ACCOUNT_ID);
  }

  @Override
  protected Response callGetGithubPackages(String packageType, String org, String connectorRef) {
    return projectArtifactsApi.getGithubPackages(ORG_ID, PROJECT_ID, packageType, org, connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetGithubPackageVersions(
      String packageName, String packageType, String org, String versionRegex, String connectorRef) {
    return projectArtifactsApi.getGithubPackagesVersions(
        ORG_ID, PROJECT_ID, connectorRef, packageName, packageType, org, versionRegex, ACCOUNT_ID);
  }

  @Override
  protected Response callGetGithubPackageLastSuccessfulVersion(
      String packageName, String packageType, String version, String versionRegex, String org, String connectorRef) {
    return projectArtifactsApi.getGithubPackagesLastSuccessfulVersion(
        ORG_ID, PROJECT_ID, connectorRef, packageName, packageType, org, version, versionRegex, ACCOUNT_ID);
  }

  @Override
  protected Response callGetNexusBuildDetails(String repository, String repositoryPort, String repositoryFormat,
      String repositoryUrl, String artifactPath, String connectorRef, String groupId, String artifactId,
      String extension, String classifier, String packageName, String group) {
    return projectArtifactsApi.getNexusBuildDetails(ORG_ID, PROJECT_ID, repository, repositoryPort, repositoryFormat,
        repositoryUrl, artifactPath, connectorRef, groupId, artifactId, extension, classifier, packageName, group,
        ACCOUNT_ID);
  }
}
