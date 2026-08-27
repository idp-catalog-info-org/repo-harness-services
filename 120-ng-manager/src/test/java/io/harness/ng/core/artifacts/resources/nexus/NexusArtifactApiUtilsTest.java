/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.nexus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class NexusArtifactApiUtilsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String CONNECTOR_REF = "connectorRef";
  private static final String REPOSITORY = "maven-releases";
  private static final String REPOSITORY_PORT = "8081";
  private static final String REPOSITORY_FORMAT = "maven2";
  private static final String REPOSITORY_URL = "http://nexus.example.com";
  private static final String ARTIFACT_PATH = "com/example";
  private static final String GROUP_ID = "com.example";
  private static final String ARTIFACT_ID = "my-artifact";
  private static final String EXTENSION = "jar";
  private static final String CLASSIFIER = "sources";
  private static final String PACKAGE_NAME = "docker-package";
  private static final String GROUP = "docker-group";

  @Mock private NexusResourceService nexusResourceService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private ArtifactResourceUtils artifactResourceUtils;

  @InjectMocks private NexusArtifactApiUtils nexusArtifactApiUtils;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetBuildDetails_ProjectScope() {
    // Given
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    NexusBuildDetailsDTO buildDetail = NexusBuildDetailsDTO.builder().tag("1.0.0").build();
    NexusResponseDTO expectedResponse = NexusResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(nexusResourceService.getBuildDetails(any(IdentifierRef.class), eq(REPOSITORY), eq(REPOSITORY_PORT),
             eq(ARTIFACT_PATH), eq(REPOSITORY_FORMAT), eq(REPOSITORY_URL), eq(ORG_ID), eq(PROJECT_ID), eq(GROUP_ID),
             eq(ARTIFACT_ID), eq(EXTENSION), eq(CLASSIFIER), eq(PACKAGE_NAME), eq(GROUP), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    NexusResponseDTO result = nexusArtifactApiUtils.getBuildDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, REPOSITORY,
        REPOSITORY_PORT, REPOSITORY_FORMAT, REPOSITORY_URL, ARTIFACT_PATH, CONNECTOR_REF, GROUP_ID, ARTIFACT_ID,
        EXTENSION, CLASSIFIER, PACKAGE_NAME, GROUP);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedResponse);
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("1.0.0");

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(nexusResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(REPOSITORY), eq(REPOSITORY_PORT), eq(ARTIFACT_PATH),
            eq(REPOSITORY_FORMAT), eq(REPOSITORY_URL), eq(ORG_ID), eq(PROJECT_ID), eq(GROUP_ID), eq(ARTIFACT_ID),
            eq(EXTENSION), eq(CLASSIFIER), eq(PACKAGE_NAME), eq(GROUP), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetBuildDetails_OrgScope() {
    // Given
    String orgScopedConnectorRef = "org." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(null)
                              .uniqueId(ORG_ID)
                              .build();

    NexusBuildDetailsDTO buildDetail = NexusBuildDetailsDTO.builder().tag("2.0.0").build();
    NexusResponseDTO expectedResponse = NexusResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, null)).thenReturn(scopeInfo);
    when(nexusResourceService.getBuildDetails(any(IdentifierRef.class), eq(REPOSITORY), eq(REPOSITORY_PORT),
             eq(ARTIFACT_PATH), eq(REPOSITORY_FORMAT), eq(REPOSITORY_URL), eq(ORG_ID), eq(null), eq(GROUP_ID),
             eq(ARTIFACT_ID), eq(EXTENSION), eq(null), eq(null), eq(null), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    NexusResponseDTO result =
        nexusArtifactApiUtils.getBuildDetails(ACCOUNT_ID, ORG_ID, null, REPOSITORY, REPOSITORY_PORT, REPOSITORY_FORMAT,
            REPOSITORY_URL, ARTIFACT_PATH, orgScopedConnectorRef, GROUP_ID, ARTIFACT_ID, EXTENSION, null, null, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedResponse);
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("2.0.0");

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, null);
    verify(nexusResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(REPOSITORY), eq(REPOSITORY_PORT), eq(ARTIFACT_PATH),
            eq(REPOSITORY_FORMAT), eq(REPOSITORY_URL), eq(ORG_ID), eq(null), eq(GROUP_ID), eq(ARTIFACT_ID),
            eq(EXTENSION), eq(null), eq(null), eq(null), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetBuildDetails_AccountScope() {
    // Given
    String accountScopedConnectorRef = "account." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(null)
                              .projectIdentifier(null)
                              .uniqueId(ACCOUNT_ID)
                              .build();

    NexusBuildDetailsDTO buildDetail = NexusBuildDetailsDTO.builder().tag("3.0.0").build();
    NexusResponseDTO expectedResponse = NexusResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(scopeInfo);
    when(nexusResourceService.getBuildDetails(any(IdentifierRef.class), eq(REPOSITORY), eq(null), eq(null), eq(null),
             eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(PACKAGE_NAME), eq(GROUP),
             eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    NexusResponseDTO result = nexusArtifactApiUtils.getBuildDetails(ACCOUNT_ID, null, null, REPOSITORY, null, null,
        null, null, accountScopedConnectorRef, null, null, null, null, PACKAGE_NAME, GROUP);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedResponse);
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("3.0.0");

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, null, null);
    verify(nexusResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(REPOSITORY), eq(null), eq(null), eq(null), eq(null), eq(null),
            eq(null), eq(null), eq(null), eq(null), eq(null), eq(PACKAGE_NAME), eq(GROUP), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetBuildDetails_WithMultipleBuilds() {
    // Given
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    NexusBuildDetailsDTO build1 = NexusBuildDetailsDTO.builder().tag("1.0.0").build();
    NexusBuildDetailsDTO build2 = NexusBuildDetailsDTO.builder().tag("1.1.0").build();
    NexusBuildDetailsDTO build3 = NexusBuildDetailsDTO.builder().tag("1.2.0").build();
    NexusResponseDTO expectedResponse =
        NexusResponseDTO.builder().buildDetailsList(Arrays.asList(build1, build2, build3)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(nexusResourceService.getBuildDetails(any(IdentifierRef.class), eq(REPOSITORY), eq(REPOSITORY_PORT),
             eq(ARTIFACT_PATH), eq(REPOSITORY_FORMAT), eq(REPOSITORY_URL), eq(ORG_ID), eq(PROJECT_ID), eq(GROUP_ID),
             eq(ARTIFACT_ID), eq(EXTENSION), eq(null), eq(null), eq(null), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    NexusResponseDTO result = nexusArtifactApiUtils.getBuildDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, REPOSITORY,
        REPOSITORY_PORT, REPOSITORY_FORMAT, REPOSITORY_URL, ARTIFACT_PATH, CONNECTOR_REF, GROUP_ID, ARTIFACT_ID,
        EXTENSION, null, null, null);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(3);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("1.0.0");
    assertThat(result.getBuildDetailsList().get(1).getTag()).isEqualTo("1.1.0");
    assertThat(result.getBuildDetailsList().get(2).getTag()).isEqualTo("1.2.0");
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetBuildDetails_WithEmptyResponse() {
    // Given
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    NexusResponseDTO expectedResponse = NexusResponseDTO.builder().buildDetailsList(Arrays.asList()).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(nexusResourceService.getBuildDetails(any(IdentifierRef.class), eq(REPOSITORY), eq(REPOSITORY_PORT),
             eq(ARTIFACT_PATH), eq(REPOSITORY_FORMAT), eq(REPOSITORY_URL), eq(ORG_ID), eq(PROJECT_ID), eq(GROUP_ID),
             eq(ARTIFACT_ID), eq(EXTENSION), eq(CLASSIFIER), eq(PACKAGE_NAME), eq(GROUP), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    NexusResponseDTO result = nexusArtifactApiUtils.getBuildDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, REPOSITORY,
        REPOSITORY_PORT, REPOSITORY_FORMAT, REPOSITORY_URL, ARTIFACT_PATH, CONNECTOR_REF, GROUP_ID, ARTIFACT_ID,
        EXTENSION, CLASSIFIER, PACKAGE_NAME, GROUP);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).isEmpty();

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(nexusResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(REPOSITORY), eq(REPOSITORY_PORT), eq(ARTIFACT_PATH),
            eq(REPOSITORY_FORMAT), eq(REPOSITORY_URL), eq(ORG_ID), eq(PROJECT_ID), eq(GROUP_ID), eq(ARTIFACT_ID),
            eq(EXTENSION), eq(CLASSIFIER), eq(PACKAGE_NAME), eq(GROUP), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetBuildDetails_DockerFormat() {
    // Given
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    NexusBuildDetailsDTO buildDetail = NexusBuildDetailsDTO.builder().tag("latest").build();
    NexusResponseDTO expectedResponse = NexusResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(nexusResourceService.getBuildDetails(any(IdentifierRef.class), eq("docker-repo"), eq("5000"), eq("nginx"),
             eq("docker"), eq("http://nexus.docker.com"), eq(ORG_ID), eq(PROJECT_ID), eq(null), eq(null), eq(null),
             eq(null), eq("nginx"), eq("library"), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    NexusResponseDTO result =
        nexusArtifactApiUtils.getBuildDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, "docker-repo", "5000", "docker",
            "http://nexus.docker.com", "nginx", CONNECTOR_REF, null, null, null, null, "nginx", "library");

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("latest");

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(nexusResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq("docker-repo"), eq("5000"), eq("nginx"), eq("docker"),
            eq("http://nexus.docker.com"), eq(ORG_ID), eq(PROJECT_ID), eq(null), eq(null), eq(null), eq(null),
            eq("nginx"), eq("library"), eq(scopeInfo));
  }
}
