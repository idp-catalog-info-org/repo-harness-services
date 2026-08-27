/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.githubpackages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.cdng.artifact.resources.githubpackages.service.GithubPackagesResourceService;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GithubPackagesArtifactApiUtilsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PACKAGE_TYPE = "container";
  private static final String PACKAGE_NAME = "my-package";
  private static final String ORG = "myorg";
  private static final String VERSION = "1.0.0";
  private static final String VERSION_REGEX = ".*";
  private static final String CONNECTOR_REF = "githubConnector";

  @Mock private GithubPackagesResourceService githubPackagesResourceService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private ArtifactResourceUtils artifactResourceUtils;

  @InjectMocks private GithubPackagesArtifactApiUtils githubPackagesArtifactApiUtils;

  private AutoCloseable mocks;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetPackages_ProjectScope() {
    // Given
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    GithubPackagesResponseDTO expectedResponse = GithubPackagesResponseDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(githubPackagesResourceService.getPackageDetails(any(IdentifierRef.class), eq(ACCOUNT_ID), eq(ORG_ID),
             eq(PROJECT_ID), eq(PACKAGE_TYPE), eq(ORG), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    GithubPackagesResponseDTO result =
        githubPackagesArtifactApiUtils.getPackages(ACCOUNT_ID, ORG_ID, PROJECT_ID, PACKAGE_TYPE, ORG, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedResponse);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(githubPackagesResourceService)
        .getPackageDetails(any(IdentifierRef.class), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(PACKAGE_TYPE),
            eq(ORG), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetPackages_OrgScope() {
    // Given
    String orgScopedConnectorRef = "org." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(null)
                              .uniqueId(ORG_ID)
                              .build();

    GithubPackagesResponseDTO expectedResponse = GithubPackagesResponseDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, null)).thenReturn(scopeInfo);
    when(githubPackagesResourceService.getPackageDetails(
             any(IdentifierRef.class), eq(ACCOUNT_ID), eq(ORG_ID), eq(null), eq(PACKAGE_TYPE), eq(ORG), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    GithubPackagesResponseDTO result =
        githubPackagesArtifactApiUtils.getPackages(ACCOUNT_ID, ORG_ID, null, PACKAGE_TYPE, ORG, orgScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedResponse);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, null);
    verify(githubPackagesResourceService)
        .getPackageDetails(
            any(IdentifierRef.class), eq(ACCOUNT_ID), eq(ORG_ID), eq(null), eq(PACKAGE_TYPE), eq(ORG), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetPackages_AccountScope() {
    // Given
    String accountScopedConnectorRef = "account." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(null)
                              .projectIdentifier(null)
                              .uniqueId(ACCOUNT_ID)
                              .build();

    GithubPackagesResponseDTO expectedResponse = GithubPackagesResponseDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(scopeInfo);
    when(githubPackagesResourceService.getPackageDetails(
             any(IdentifierRef.class), eq(ACCOUNT_ID), eq(null), eq(null), eq(PACKAGE_TYPE), eq(ORG), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    GithubPackagesResponseDTO result = githubPackagesArtifactApiUtils.getPackages(
        ACCOUNT_ID, null, null, PACKAGE_TYPE, ORG, accountScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedResponse);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, null, null);
    verify(githubPackagesResourceService)
        .getPackageDetails(
            any(IdentifierRef.class), eq(ACCOUNT_ID), eq(null), eq(null), eq(PACKAGE_TYPE), eq(ORG), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetPackageVersions_ProjectScope() {
    // Given
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    BuildDetails buildDetail = BuildDetails.Builder.aBuildDetails().withNumber(VERSION).build();
    List<BuildDetails> expectedVersions = Arrays.asList(buildDetail);

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(githubPackagesResourceService.getVersionsOfPackage(any(IdentifierRef.class), eq(PACKAGE_NAME),
             eq(PACKAGE_TYPE), eq(VERSION_REGEX), eq(ORG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo)))
        .thenReturn(expectedVersions);

    // When
    List<BuildDetails> result = githubPackagesArtifactApiUtils.getPackageVersions(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PACKAGE_NAME, PACKAGE_TYPE, ORG, VERSION_REGEX, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getNumber()).isEqualTo(VERSION);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(githubPackagesResourceService)
        .getVersionsOfPackage(any(IdentifierRef.class), eq(PACKAGE_NAME), eq(PACKAGE_TYPE), eq(VERSION_REGEX), eq(ORG),
            eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetPackageVersions_OrgScope() {
    // Given
    String orgScopedConnectorRef = "org." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(null)
                              .uniqueId(ORG_ID)
                              .build();

    BuildDetails buildDetail = BuildDetails.Builder.aBuildDetails().withNumber(VERSION).build();
    List<BuildDetails> expectedVersions = Arrays.asList(buildDetail);

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, null)).thenReturn(scopeInfo);
    when(githubPackagesResourceService.getVersionsOfPackage(any(IdentifierRef.class), eq(PACKAGE_NAME),
             eq(PACKAGE_TYPE), eq(VERSION_REGEX), eq(ORG), eq(ACCOUNT_ID), eq(ORG_ID), eq(null), eq(scopeInfo)))
        .thenReturn(expectedVersions);

    // When
    List<BuildDetails> result = githubPackagesArtifactApiUtils.getPackageVersions(
        ACCOUNT_ID, ORG_ID, null, PACKAGE_NAME, PACKAGE_TYPE, ORG, VERSION_REGEX, orgScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getNumber()).isEqualTo(VERSION);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, null);
    verify(githubPackagesResourceService)
        .getVersionsOfPackage(any(IdentifierRef.class), eq(PACKAGE_NAME), eq(PACKAGE_TYPE), eq(VERSION_REGEX), eq(ORG),
            eq(ACCOUNT_ID), eq(ORG_ID), eq(null), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetPackageVersions_AccountScope() {
    // Given
    String accountScopedConnectorRef = "account." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(null)
                              .projectIdentifier(null)
                              .uniqueId(ACCOUNT_ID)
                              .build();

    BuildDetails buildDetail = BuildDetails.Builder.aBuildDetails().withNumber(VERSION).build();
    List<BuildDetails> expectedVersions = Arrays.asList(buildDetail);

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(scopeInfo);
    when(githubPackagesResourceService.getVersionsOfPackage(any(IdentifierRef.class), eq(PACKAGE_NAME),
             eq(PACKAGE_TYPE), eq(VERSION_REGEX), eq(ORG), eq(ACCOUNT_ID), eq(null), eq(null), eq(scopeInfo)))
        .thenReturn(expectedVersions);

    // When
    List<BuildDetails> result = githubPackagesArtifactApiUtils.getPackageVersions(
        ACCOUNT_ID, null, null, PACKAGE_NAME, PACKAGE_TYPE, ORG, VERSION_REGEX, accountScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getNumber()).isEqualTo(VERSION);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, null, null);
    verify(githubPackagesResourceService)
        .getVersionsOfPackage(any(IdentifierRef.class), eq(PACKAGE_NAME), eq(PACKAGE_TYPE), eq(VERSION_REGEX), eq(ORG),
            eq(ACCOUNT_ID), eq(null), eq(null), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulVersion_ProjectScope() {
    // Given
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    BuildDetails expectedBuild = BuildDetails.Builder.aBuildDetails().withNumber(VERSION).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(githubPackagesResourceService.getLastSuccessfulVersion(any(IdentifierRef.class), eq(PACKAGE_NAME),
             eq(PACKAGE_TYPE), eq(VERSION), eq(VERSION_REGEX), eq(ORG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(scopeInfo)))
        .thenReturn(expectedBuild);

    // When
    BuildDetails result = githubPackagesArtifactApiUtils.getLastSuccessfulVersion(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, PACKAGE_NAME, PACKAGE_TYPE, VERSION, VERSION_REGEX, ORG, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getNumber()).isEqualTo(VERSION);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(githubPackagesResourceService)
        .getLastSuccessfulVersion(any(IdentifierRef.class), eq(PACKAGE_NAME), eq(PACKAGE_TYPE), eq(VERSION),
            eq(VERSION_REGEX), eq(ORG), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulVersion_OrgScope() {
    // Given
    String orgScopedConnectorRef = "org." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(null)
                              .uniqueId(ORG_ID)
                              .build();

    BuildDetails expectedBuild = BuildDetails.Builder.aBuildDetails().withNumber(VERSION).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, null)).thenReturn(scopeInfo);
    when(githubPackagesResourceService.getLastSuccessfulVersion(any(IdentifierRef.class), eq(PACKAGE_NAME),
             eq(PACKAGE_TYPE), eq(VERSION), eq(VERSION_REGEX), eq(ORG), eq(ACCOUNT_ID), eq(ORG_ID), eq(null),
             eq(scopeInfo)))
        .thenReturn(expectedBuild);

    // When
    BuildDetails result = githubPackagesArtifactApiUtils.getLastSuccessfulVersion(
        ACCOUNT_ID, ORG_ID, null, PACKAGE_NAME, PACKAGE_TYPE, VERSION, VERSION_REGEX, ORG, orgScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getNumber()).isEqualTo(VERSION);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, null);
    verify(githubPackagesResourceService)
        .getLastSuccessfulVersion(any(IdentifierRef.class), eq(PACKAGE_NAME), eq(PACKAGE_TYPE), eq(VERSION),
            eq(VERSION_REGEX), eq(ORG), eq(ACCOUNT_ID), eq(ORG_ID), eq(null), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulVersion_AccountScope() {
    // Given
    String accountScopedConnectorRef = "account." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(null)
                              .projectIdentifier(null)
                              .uniqueId(ACCOUNT_ID)
                              .build();

    BuildDetails expectedBuild = BuildDetails.Builder.aBuildDetails().withNumber(VERSION).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(scopeInfo);
    when(githubPackagesResourceService.getLastSuccessfulVersion(any(IdentifierRef.class), eq(PACKAGE_NAME),
             eq(PACKAGE_TYPE), eq(VERSION), eq(VERSION_REGEX), eq(ORG), eq(ACCOUNT_ID), eq(null), eq(null),
             eq(scopeInfo)))
        .thenReturn(expectedBuild);

    // When
    BuildDetails result = githubPackagesArtifactApiUtils.getLastSuccessfulVersion(
        ACCOUNT_ID, null, null, PACKAGE_NAME, PACKAGE_TYPE, VERSION, VERSION_REGEX, ORG, accountScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getNumber()).isEqualTo(VERSION);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, null, null);
    verify(githubPackagesResourceService)
        .getLastSuccessfulVersion(any(IdentifierRef.class), eq(PACKAGE_NAME), eq(PACKAGE_TYPE), eq(VERSION),
            eq(VERSION_REGEX), eq(ORG), eq(ACCOUNT_ID), eq(null), eq(null), eq(scopeInfo));
  }
}
