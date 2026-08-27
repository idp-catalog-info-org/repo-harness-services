/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources;

import javax.ws.rs.core.Response;

public class OrgArtifactsApiImplTest extends AbstractArtifactsApiImplTest {
  private OrgArtifactsApiImpl orgArtifactsApi;

  @Override
  protected void initializeApiInstance() {
    orgArtifactsApi = new OrgArtifactsApiImpl(
        dockerArtifactService, acrArtifactService, githubPackagesArtifactService, nexusArtifactService);
  }

  @Override
  protected String getExpectedOrgIdentifier() {
    return ORG_ID;
  }

  @Override
  protected String getExpectedProjectIdentifier() {
    return null; // Org scope does not have a project
  }

  @Override
  protected Response callGetDockerBuildDetails(String imagePath, String connectorRef) {
    return orgArtifactsApi.getOrgScopedDockerBuildDetails(ORG_ID, imagePath, connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAcrBuildDetails(
      String subscriptionId, String registry, String repository, String connectorRef) {
    return orgArtifactsApi.getOrgScopedAcrBuildDetails(
        ORG_ID, subscriptionId, registry, repository, connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAcrRegistries(String subscriptionId, String connectorRef) {
    return orgArtifactsApi.getOrgScopedAcrRegistries(ORG_ID, connectorRef, subscriptionId, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAcrRepositories(String subscriptionId, String registry, String connectorRef) {
    return orgArtifactsApi.getOrgScopedAcrRepositories(ORG_ID, registry, connectorRef, subscriptionId, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAzureSubscriptions(String connectorRef) {
    return orgArtifactsApi.getOrgScopedAzureSubscriptionsForAcr(ORG_ID, connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetGithubPackages(String packageType, String org, String connectorRef) {
    return orgArtifactsApi.getOrgScopedGithubPackages(ORG_ID, packageType, org, connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetGithubPackageVersions(
      String packageName, String packageType, String org, String versionRegex, String connectorRef) {
    return orgArtifactsApi.getOrgScopedGithubPackagesVersions(
        ORG_ID, connectorRef, packageName, packageType, org, versionRegex, ACCOUNT_ID);
  }

  @Override
  protected Response callGetGithubPackageLastSuccessfulVersion(
      String packageName, String packageType, String version, String versionRegex, String org, String connectorRef) {
    return orgArtifactsApi.getOrgScopedGithubPackagesLastSuccessfulVersion(
        ORG_ID, connectorRef, packageName, packageType, org, version, versionRegex, ACCOUNT_ID);
  }

  @Override
  protected Response callGetNexusBuildDetails(String repository, String repositoryPort, String repositoryFormat,
      String repositoryUrl, String artifactPath, String connectorRef, String groupId, String artifactId,
      String extension, String classifier, String packageName, String group) {
    return orgArtifactsApi.getOrgScopedNexusBuildDetails(ORG_ID, repository, repositoryPort, repositoryFormat,
        repositoryUrl, artifactPath, connectorRef, groupId, artifactId, extension, classifier, packageName, group,
        ACCOUNT_ID);
  }
}
