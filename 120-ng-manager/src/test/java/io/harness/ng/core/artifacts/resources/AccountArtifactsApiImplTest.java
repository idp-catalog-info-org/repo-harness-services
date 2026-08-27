/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources;

import javax.ws.rs.core.Response;

public class AccountArtifactsApiImplTest extends AbstractArtifactsApiImplTest {
  private AccountArtifactsApiImpl accountArtifactsApi;

  @Override
  protected void initializeApiInstance() {
    accountArtifactsApi = new AccountArtifactsApiImpl(
        dockerArtifactService, acrArtifactService, githubPackagesArtifactService, nexusArtifactService);
  }

  @Override
  protected String getExpectedOrgIdentifier() {
    return null; // Account scope does not have an organization
  }

  @Override
  protected String getExpectedProjectIdentifier() {
    return null; // Account scope does not have a project
  }

  @Override
  protected Response callGetDockerBuildDetails(String imagePath, String connectorRef) {
    return accountArtifactsApi.getAccountScopedDockerBuildDetails(imagePath, connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAcrBuildDetails(
      String subscriptionId, String registry, String repository, String connectorRef) {
    return accountArtifactsApi.getAccountScopedAcrBuildDetails(
        subscriptionId, registry, repository, connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAcrRegistries(String subscriptionId, String connectorRef) {
    return accountArtifactsApi.getAccountScopedAcrRegistries(connectorRef, subscriptionId, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAcrRepositories(String subscriptionId, String registry, String connectorRef) {
    return accountArtifactsApi.getAccountScopedAcrRepositories(registry, connectorRef, subscriptionId, ACCOUNT_ID);
  }

  @Override
  protected Response callGetAzureSubscriptions(String connectorRef) {
    return accountArtifactsApi.getAccountScopedAzureSubscriptionsForAcr(connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetGithubPackages(String packageType, String org, String connectorRef) {
    return accountArtifactsApi.getAccountScopedGithubPackages(packageType, org, connectorRef, ACCOUNT_ID);
  }

  @Override
  protected Response callGetGithubPackageVersions(
      String packageName, String packageType, String org, String versionRegex, String connectorRef) {
    return accountArtifactsApi.getAccountScopedGithubPackagesVersions(
        connectorRef, packageName, packageType, org, versionRegex, ACCOUNT_ID);
  }

  @Override
  protected Response callGetGithubPackageLastSuccessfulVersion(
      String packageName, String packageType, String version, String versionRegex, String org, String connectorRef) {
    return accountArtifactsApi.getAccountScopedGithubPackagesLastSuccessfulVersion(
        connectorRef, packageName, packageType, org, version, versionRegex, ACCOUNT_ID);
  }

  @Override
  protected Response callGetNexusBuildDetails(String repository, String repositoryPort, String repositoryFormat,
      String repositoryUrl, String artifactPath, String connectorRef, String groupId, String artifactId,
      String extension, String classifier, String packageName, String group) {
    return accountArtifactsApi.getAccountScopedNexusBuildDetails(repository, repositoryPort, repositoryFormat,
        repositoryUrl, artifactPath, connectorRef, groupId, artifactId, extension, classifier, packageName, group,
        ACCOUNT_ID);
  }
}
