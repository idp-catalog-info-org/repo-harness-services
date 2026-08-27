/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerResponseDTO;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.delegate.beans.azure.AcrBuildDetailsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.ng.core.artifacts.resources.acr.AcrArtifactApiUtils;
import io.harness.ng.core.artifacts.resources.docker.DockerArtifactApiUtils;
import io.harness.ng.core.artifacts.resources.githubpackages.GithubPackagesArtifactApiUtils;
import io.harness.ng.core.artifacts.resources.nexus.NexusArtifactApiUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Owner(developers = OwnerRule.ABOSII)
@Ignore("Ignore abstract class tests")
public abstract class AbstractArtifactsApiImplTest extends CategoryTest {
  protected static final String ACCOUNT_ID = "accountId";
  protected static final String ORG_ID = "orgId";
  protected static final String PROJECT_ID = "projectId";
  protected static final String IMAGE_PATH = "library/nginx";
  protected static final String CONNECTOR_REF = "connector";
  protected static final String SUBSCRIPTION_ID = "sub123";
  protected static final String REGISTRY = "myregistry";
  protected static final String REPOSITORY = "myrepo";

  @Mock protected DockerArtifactApiUtils dockerArtifactService;
  @Mock protected AcrArtifactApiUtils acrArtifactService;
  @Mock protected GithubPackagesArtifactApiUtils githubPackagesArtifactService;
  @Mock protected NexusArtifactApiUtils nexusArtifactService;

  protected AutoCloseable mocks;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    initializeApiInstance();
  }

  protected abstract void initializeApiInstance();

  protected abstract String getExpectedOrgIdentifier();

  protected abstract String getExpectedProjectIdentifier();

  protected abstract Response callGetDockerBuildDetails(String imagePath, String connectorRef);

  protected abstract Response callGetAcrBuildDetails(
      String subscriptionId, String registry, String repository, String connectorRef);

  protected abstract Response callGetAcrRegistries(String subscriptionId, String connectorRef);

  protected abstract Response callGetAcrRepositories(String subscriptionId, String registry, String connectorRef);

  protected abstract Response callGetAzureSubscriptions(String connectorRef);

  protected abstract Response callGetGithubPackages(String packageType, String org, String connectorRef);

  protected abstract Response callGetGithubPackageVersions(
      String packageName, String packageType, String org, String versionRegex, String connectorRef);

  protected abstract Response callGetGithubPackageLastSuccessfulVersion(
      String packageName, String packageType, String version, String versionRegex, String org, String connectorRef);

  protected abstract Response callGetNexusBuildDetails(String repository, String repositoryPort,
      String repositoryFormat, String repositoryUrl, String artifactPath, String connectorRef, String groupId,
      String artifactId, String extension, String classifier, String packageName, String group);

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetDockerBuildDetails() {
    // Given
    DockerBuildDetailsDTO buildDetail = DockerBuildDetailsDTO.builder().tag("1.0.0").imagePath(IMAGE_PATH).build();
    DockerResponseDTO dockerResponse = DockerResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(dockerArtifactService.getBuildDetails(
             ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), IMAGE_PATH, CONNECTOR_REF))
        .thenReturn(dockerResponse);

    // When
    Response response = callGetDockerBuildDetails(IMAGE_PATH, CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(DockerResponseDTO.class);

    DockerResponseDTO result = (DockerResponseDTO) response.getEntity();
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("1.0.0");
    assertThat(result.getBuildDetailsList().get(0).getImagePath()).isEqualTo(IMAGE_PATH);

    verify(dockerArtifactService)
        .getBuildDetails(
            ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), IMAGE_PATH, CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetDockerBuildDetails_WithNullImagePath() {
    // Given
    DockerBuildDetailsDTO buildDetail = DockerBuildDetailsDTO.builder().tag("latest").build();
    DockerResponseDTO dockerResponse = DockerResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(dockerArtifactService.getBuildDetails(
             ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), null, CONNECTOR_REF))
        .thenReturn(dockerResponse);

    // When
    Response response = callGetDockerBuildDetails(null, CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(DockerResponseDTO.class);

    DockerResponseDTO result = (DockerResponseDTO) response.getEntity();
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("latest");

    verify(dockerArtifactService)
        .getBuildDetails(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), null, CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetDockerBuildDetails_WithEmptyResponse() {
    // Given
    DockerResponseDTO dockerResponse = DockerResponseDTO.builder().buildDetailsList(Arrays.asList()).build();

    when(dockerArtifactService.getBuildDetails(
             ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), IMAGE_PATH, CONNECTOR_REF))
        .thenReturn(dockerResponse);

    // When
    Response response = callGetDockerBuildDetails(IMAGE_PATH, CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(DockerResponseDTO.class);

    DockerResponseDTO result = (DockerResponseDTO) response.getEntity();
    assertThat(result.getBuildDetailsList()).isEmpty();

    verify(dockerArtifactService)
        .getBuildDetails(
            ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), IMAGE_PATH, CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetAcrBuildDetails() {
    // Given
    AcrBuildDetailsDTO buildDetail = AcrBuildDetailsDTO.builder().tag("2.0.0").build();
    AcrResponseDTO acrResponse = AcrResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(acrArtifactService.getBuildDetails(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(),
             SUBSCRIPTION_ID, REGISTRY, REPOSITORY, CONNECTOR_REF))
        .thenReturn(acrResponse);

    // When
    Response response = callGetAcrBuildDetails(SUBSCRIPTION_ID, REGISTRY, REPOSITORY, CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(AcrResponseDTO.class);

    AcrResponseDTO result = (AcrResponseDTO) response.getEntity();
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("2.0.0");

    verify(acrArtifactService)
        .getBuildDetails(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), SUBSCRIPTION_ID,
            REGISTRY, REPOSITORY, CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetAcrBuildDetails_WithMultipleBuilds() {
    // Given
    AcrBuildDetailsDTO buildDetail1 = AcrBuildDetailsDTO.builder().tag("1.0.0").build();
    AcrBuildDetailsDTO buildDetail2 = AcrBuildDetailsDTO.builder().tag("2.0.0").build();
    AcrBuildDetailsDTO buildDetail3 = AcrBuildDetailsDTO.builder().tag("3.0.0").build();
    AcrResponseDTO acrResponse =
        AcrResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail1, buildDetail2, buildDetail3)).build();

    when(acrArtifactService.getBuildDetails(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(),
             SUBSCRIPTION_ID, REGISTRY, REPOSITORY, CONNECTOR_REF))
        .thenReturn(acrResponse);

    // When
    Response response = callGetAcrBuildDetails(SUBSCRIPTION_ID, REGISTRY, REPOSITORY, CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(AcrResponseDTO.class);

    AcrResponseDTO result = (AcrResponseDTO) response.getEntity();
    assertThat(result.getBuildDetailsList()).hasSize(3);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("1.0.0");
    assertThat(result.getBuildDetailsList().get(1).getTag()).isEqualTo("2.0.0");
    assertThat(result.getBuildDetailsList().get(2).getTag()).isEqualTo("3.0.0");

    verify(acrArtifactService)
        .getBuildDetails(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), SUBSCRIPTION_ID,
            REGISTRY, REPOSITORY, CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetAcrBuildDetails_WithEmptyResponse() {
    // Given
    AcrResponseDTO acrResponse = AcrResponseDTO.builder().buildDetailsList(Arrays.asList()).build();

    when(acrArtifactService.getBuildDetails(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(),
             SUBSCRIPTION_ID, REGISTRY, REPOSITORY, CONNECTOR_REF))
        .thenReturn(acrResponse);

    // When
    Response response = callGetAcrBuildDetails(SUBSCRIPTION_ID, REGISTRY, REPOSITORY, CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(AcrResponseDTO.class);

    AcrResponseDTO result = (AcrResponseDTO) response.getEntity();
    assertThat(result.getBuildDetailsList()).isEmpty();

    verify(acrArtifactService)
        .getBuildDetails(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), SUBSCRIPTION_ID,
            REGISTRY, REPOSITORY, CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetAcrRegistries() {
    // Given
    AcrRegistriesDTO expectedRegistries = AcrRegistriesDTO.builder().build();

    when(acrArtifactService.getRegistries(
             ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), SUBSCRIPTION_ID, CONNECTOR_REF))
        .thenReturn(expectedRegistries);

    // When
    Response response = callGetAcrRegistries(SUBSCRIPTION_ID, CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(AcrRegistriesDTO.class);

    AcrRegistriesDTO result = (AcrRegistriesDTO) response.getEntity();
    assertThat(result).isSameAs(expectedRegistries);

    verify(acrArtifactService)
        .getRegistries(
            ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), SUBSCRIPTION_ID, CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetAcrRepositories() {
    // Given
    AcrRepositoriesDTO expectedRepositories = AcrRepositoriesDTO.builder().build();

    when(acrArtifactService.getRepositories(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(),
             SUBSCRIPTION_ID, REGISTRY, CONNECTOR_REF))
        .thenReturn(expectedRepositories);

    // When
    Response response = callGetAcrRepositories(SUBSCRIPTION_ID, REGISTRY, CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(AcrRepositoriesDTO.class);

    AcrRepositoriesDTO result = (AcrRepositoriesDTO) response.getEntity();
    assertThat(result).isSameAs(expectedRepositories);

    verify(acrArtifactService)
        .getRepositories(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), SUBSCRIPTION_ID,
            REGISTRY, CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetAzureSubscriptions() {
    // Given
    AzureSubscriptionsDTO expectedSubscriptions = AzureSubscriptionsDTO.builder().build();

    when(acrArtifactService.getSubscriptions(
             ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), CONNECTOR_REF))
        .thenReturn(expectedSubscriptions);

    // When
    Response response = callGetAzureSubscriptions(CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(AzureSubscriptionsDTO.class);

    AzureSubscriptionsDTO result = (AzureSubscriptionsDTO) response.getEntity();
    assertThat(result).isSameAs(expectedSubscriptions);

    verify(acrArtifactService)
        .getSubscriptions(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetGithubPackages() {
    // Given
    GithubPackagesResponseDTO expectedPackages = GithubPackagesResponseDTO.builder().build();

    when(githubPackagesArtifactService.getPackages(
             ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), "container", "org", CONNECTOR_REF))
        .thenReturn(expectedPackages);

    // When
    Response response = callGetGithubPackages("container", "org", CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(GithubPackagesResponseDTO.class);

    GithubPackagesResponseDTO result = (GithubPackagesResponseDTO) response.getEntity();
    assertThat(result).isSameAs(expectedPackages);

    verify(githubPackagesArtifactService)
        .getPackages(
            ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), "container", "org", CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetGithubPackageVersions() {
    // Given
    List<BuildDetails> buildDetails = List.of(new BuildDetails());

    when(githubPackagesArtifactService.getPackageVersions(ACCOUNT_ID, getExpectedOrgIdentifier(),
             getExpectedProjectIdentifier(), "package1", "container", "org", ".*", CONNECTOR_REF))
        .thenReturn(buildDetails);

    // When
    Response response = callGetGithubPackageVersions("package1", "container", "org", ".*", CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);

    List<BuildDetails> result = (List<BuildDetails>) response.getEntity();
    assertThat(result).isSameAs(buildDetails);

    verify(githubPackagesArtifactService)
        .getPackageVersions(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), "package1",
            "container", "org", ".*", CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetGithubPackageLastSuccessfulVersion() {
    // Given
    BuildDetails expectedBuildDetails = new BuildDetails();

    when(githubPackagesArtifactService.getLastSuccessfulVersion(ACCOUNT_ID, getExpectedOrgIdentifier(),
             getExpectedProjectIdentifier(), "package1", "container", "1.0", ".*", "org", CONNECTOR_REF))
        .thenReturn(expectedBuildDetails);

    // When
    Response response =
        callGetGithubPackageLastSuccessfulVersion("package1", "container", "1.0", ".*", "org", CONNECTOR_REF);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(BuildDetails.class);

    BuildDetails result = (BuildDetails) response.getEntity();
    assertThat(result).isSameAs(expectedBuildDetails);

    verify(githubPackagesArtifactService)
        .getLastSuccessfulVersion(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), "package1",
            "container", "1.0", ".*", "org", CONNECTOR_REF);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetNexusBuildDetails() {
    // Given
    NexusBuildDetailsDTO buildDetail = NexusBuildDetailsDTO.builder().tag("1.0.0").build();
    NexusResponseDTO expectedResponse = NexusResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(nexusArtifactService.getBuildDetails(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(),
             "maven-releases", "8081", "maven2", "http://nexus.example.com", "com/example", CONNECTOR_REF,
             "com.example", "artifact", "jar", null, null, null))
        .thenReturn(expectedResponse);

    // When
    Response response = callGetNexusBuildDetails("maven-releases", "8081", "maven2", "http://nexus.example.com",
        "com/example", CONNECTOR_REF, "com.example", "artifact", "jar", null, null, null);

    // Then
    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isInstanceOf(NexusResponseDTO.class);

    NexusResponseDTO result = (NexusResponseDTO) response.getEntity();
    assertThat(result).isSameAs(expectedResponse);
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("1.0.0");

    verify(nexusArtifactService)
        .getBuildDetails(ACCOUNT_ID, getExpectedOrgIdentifier(), getExpectedProjectIdentifier(), "maven-releases",
            "8081", "maven2", "http://nexus.example.com", "com/example", CONNECTOR_REF, "com.example", "artifact",
            "jar", null, null, null);
  }
}
