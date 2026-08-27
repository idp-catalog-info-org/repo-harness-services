/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.acr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRegistriesDTO;
import io.harness.cdng.artifact.resources.acr.dtos.AcrRepositoriesDTO;
import io.harness.cdng.artifact.resources.acr.service.AcrResourceService;
import io.harness.cdng.k8s.resources.azure.dtos.AzureSubscriptionsDTO;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.delegate.beans.azure.AcrBuildDetailsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;
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

public class AcrArtifactApiUtilsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SUBSCRIPTION_ID = "sub123";
  private static final String REGISTRY = "myregistry";
  private static final String REPOSITORY = "myrepo";
  private static final String CONNECTOR_REF = "acrConnector";
  private static final String TAG = "1.0.0";

  @Mock private AcrResourceService acrResourceService;
  @Mock private AzureResourceService azureResourceService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private ArtifactResourceUtils artifactResourceUtils;

  @InjectMocks private AcrArtifactApiUtils acrArtifactApiUtils;

  private AutoCloseable mocks;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
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

    AcrBuildDetailsDTO buildDetail = AcrBuildDetailsDTO.builder().tag(TAG).build();
    AcrResponseDTO expectedResponse = AcrResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(acrResourceService.getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(REPOSITORY),
             eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    AcrResponseDTO result = acrArtifactApiUtils.getBuildDetails(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, SUBSCRIPTION_ID, REGISTRY, REPOSITORY, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo(TAG);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(acrResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(REPOSITORY), eq(ORG_ID),
            eq(PROJECT_ID), eq(scopeInfo));
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

    AcrBuildDetailsDTO buildDetail = AcrBuildDetailsDTO.builder().tag(TAG).build();
    AcrResponseDTO expectedResponse = AcrResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, null)).thenReturn(scopeInfo);
    when(acrResourceService.getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(REPOSITORY),
             eq(ORG_ID), eq(null), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    AcrResponseDTO result = acrArtifactApiUtils.getBuildDetails(
        ACCOUNT_ID, ORG_ID, null, SUBSCRIPTION_ID, REGISTRY, REPOSITORY, orgScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo(TAG);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, null);
    verify(acrResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(REPOSITORY), eq(ORG_ID),
            eq(null), eq(scopeInfo));
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

    AcrBuildDetailsDTO buildDetail = AcrBuildDetailsDTO.builder().tag(TAG).build();
    AcrResponseDTO expectedResponse = AcrResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(scopeInfo);
    when(acrResourceService.getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(REPOSITORY),
             eq(null), eq(null), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    AcrResponseDTO result = acrArtifactApiUtils.getBuildDetails(
        ACCOUNT_ID, null, null, SUBSCRIPTION_ID, REGISTRY, REPOSITORY, accountScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo(TAG);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, null, null);
    verify(acrResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(REPOSITORY), eq(null),
            eq(null), eq(scopeInfo));
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

    AcrBuildDetailsDTO buildDetail1 = AcrBuildDetailsDTO.builder().tag("1.0.0").build();
    AcrBuildDetailsDTO buildDetail2 = AcrBuildDetailsDTO.builder().tag("2.0.0").build();
    AcrBuildDetailsDTO buildDetail3 = AcrBuildDetailsDTO.builder().tag("3.0.0").build();
    AcrResponseDTO expectedResponse =
        AcrResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail1, buildDetail2, buildDetail3)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(acrResourceService.getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(REPOSITORY),
             eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    AcrResponseDTO result = acrArtifactApiUtils.getBuildDetails(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, SUBSCRIPTION_ID, REGISTRY, REPOSITORY, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(3);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("1.0.0");
    assertThat(result.getBuildDetailsList().get(1).getTag()).isEqualTo("2.0.0");
    assertThat(result.getBuildDetailsList().get(2).getTag()).isEqualTo("3.0.0");

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(acrResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(REPOSITORY), eq(ORG_ID),
            eq(PROJECT_ID), eq(scopeInfo));
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

    AcrResponseDTO expectedResponse = AcrResponseDTO.builder().buildDetailsList(Arrays.asList()).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(acrResourceService.getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(REPOSITORY),
             eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    AcrResponseDTO result = acrArtifactApiUtils.getBuildDetails(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, SUBSCRIPTION_ID, REGISTRY, REPOSITORY, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).isEmpty();

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(acrResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(REPOSITORY), eq(ORG_ID),
            eq(PROJECT_ID), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetBuildDetails_WithDifferentRegistry() {
    // Given
    String differentRegistry = "differentRegistry";
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    AcrBuildDetailsDTO buildDetail = AcrBuildDetailsDTO.builder().tag(TAG).build();
    AcrResponseDTO expectedResponse = AcrResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(acrResourceService.getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(differentRegistry),
             eq(REPOSITORY), eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    AcrResponseDTO result = acrArtifactApiUtils.getBuildDetails(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, SUBSCRIPTION_ID, differentRegistry, REPOSITORY, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(1);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(acrResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(SUBSCRIPTION_ID), eq(differentRegistry), eq(REPOSITORY),
            eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetBuildDetails_WithDifferentSubscription() {
    // Given
    String differentSubscription = "differentSub456";
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    AcrBuildDetailsDTO buildDetail = AcrBuildDetailsDTO.builder().tag(TAG).build();
    AcrResponseDTO expectedResponse = AcrResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(acrResourceService.getBuildDetails(any(IdentifierRef.class), eq(differentSubscription), eq(REGISTRY),
             eq(REPOSITORY), eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    AcrResponseDTO result = acrArtifactApiUtils.getBuildDetails(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, differentSubscription, REGISTRY, REPOSITORY, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(1);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(acrResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(differentSubscription), eq(REGISTRY), eq(REPOSITORY), eq(ORG_ID),
            eq(PROJECT_ID), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetRegistries_ProjectScope() {
    // Given
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    AcrRegistriesDTO expectedRegistries = AcrRegistriesDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(acrResourceService.getRegistries(
             any(IdentifierRef.class), eq(ORG_ID), eq(PROJECT_ID), eq(SUBSCRIPTION_ID), eq(scopeInfo)))
        .thenReturn(expectedRegistries);

    // When
    AcrRegistriesDTO result =
        acrArtifactApiUtils.getRegistries(ACCOUNT_ID, ORG_ID, PROJECT_ID, SUBSCRIPTION_ID, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedRegistries);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(acrResourceService)
        .getRegistries(any(IdentifierRef.class), eq(ORG_ID), eq(PROJECT_ID), eq(SUBSCRIPTION_ID), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetRegistries_OrgScope() {
    // Given
    String orgScopedConnectorRef = "org." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(null)
                              .uniqueId(ORG_ID)
                              .build();

    AcrRegistriesDTO expectedRegistries = AcrRegistriesDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, null)).thenReturn(scopeInfo);
    when(acrResourceService.getRegistries(
             any(IdentifierRef.class), eq(ORG_ID), eq(null), eq(SUBSCRIPTION_ID), eq(scopeInfo)))
        .thenReturn(expectedRegistries);

    // When
    AcrRegistriesDTO result =
        acrArtifactApiUtils.getRegistries(ACCOUNT_ID, ORG_ID, null, SUBSCRIPTION_ID, orgScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedRegistries);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, null);
    verify(acrResourceService)
        .getRegistries(any(IdentifierRef.class), eq(ORG_ID), eq(null), eq(SUBSCRIPTION_ID), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetRegistries_AccountScope() {
    // Given
    String accountScopedConnectorRef = "account." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(null)
                              .projectIdentifier(null)
                              .uniqueId(ACCOUNT_ID)
                              .build();

    AcrRegistriesDTO expectedRegistries = AcrRegistriesDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(scopeInfo);
    when(acrResourceService.getRegistries(
             any(IdentifierRef.class), eq(null), eq(null), eq(SUBSCRIPTION_ID), eq(scopeInfo)))
        .thenReturn(expectedRegistries);

    // When
    AcrRegistriesDTO result =
        acrArtifactApiUtils.getRegistries(ACCOUNT_ID, null, null, SUBSCRIPTION_ID, accountScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedRegistries);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, null, null);
    verify(acrResourceService)
        .getRegistries(any(IdentifierRef.class), eq(null), eq(null), eq(SUBSCRIPTION_ID), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetRepositories_ProjectScope() {
    // Given
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    AcrRepositoriesDTO expectedRepositories = AcrRepositoriesDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(acrResourceService.getRepositories(
             any(IdentifierRef.class), eq(ORG_ID), eq(PROJECT_ID), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(scopeInfo)))
        .thenReturn(expectedRepositories);

    // When
    AcrRepositoriesDTO result =
        acrArtifactApiUtils.getRepositories(ACCOUNT_ID, ORG_ID, PROJECT_ID, SUBSCRIPTION_ID, REGISTRY, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedRepositories);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(acrResourceService)
        .getRepositories(
            any(IdentifierRef.class), eq(ORG_ID), eq(PROJECT_ID), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetRepositories_OrgScope() {
    // Given
    String orgScopedConnectorRef = "org." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(null)
                              .uniqueId(ORG_ID)
                              .build();

    AcrRepositoriesDTO expectedRepositories = AcrRepositoriesDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, null)).thenReturn(scopeInfo);
    when(acrResourceService.getRepositories(
             any(IdentifierRef.class), eq(ORG_ID), eq(null), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(scopeInfo)))
        .thenReturn(expectedRepositories);

    // When
    AcrRepositoriesDTO result =
        acrArtifactApiUtils.getRepositories(ACCOUNT_ID, ORG_ID, null, SUBSCRIPTION_ID, REGISTRY, orgScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedRepositories);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, null);
    verify(acrResourceService)
        .getRepositories(
            any(IdentifierRef.class), eq(ORG_ID), eq(null), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetRepositories_AccountScope() {
    // Given
    String accountScopedConnectorRef = "account." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(null)
                              .projectIdentifier(null)
                              .uniqueId(ACCOUNT_ID)
                              .build();

    AcrRepositoriesDTO expectedRepositories = AcrRepositoriesDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(scopeInfo);
    when(acrResourceService.getRepositories(
             any(IdentifierRef.class), eq(null), eq(null), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(scopeInfo)))
        .thenReturn(expectedRepositories);

    // When
    AcrRepositoriesDTO result = acrArtifactApiUtils.getRepositories(
        ACCOUNT_ID, null, null, SUBSCRIPTION_ID, REGISTRY, accountScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedRepositories);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, null, null);
    verify(acrResourceService)
        .getRepositories(
            any(IdentifierRef.class), eq(null), eq(null), eq(SUBSCRIPTION_ID), eq(REGISTRY), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetSubscriptions_ProjectScope() {
    // Given
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_ID)
                              .build();

    AzureSubscriptionsDTO expectedSubscriptions = AzureSubscriptionsDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(azureResourceService.getSubscriptions(any(IdentifierRef.class), eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo)))
        .thenReturn(expectedSubscriptions);

    // When
    AzureSubscriptionsDTO result = acrArtifactApiUtils.getSubscriptions(ACCOUNT_ID, ORG_ID, PROJECT_ID, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedSubscriptions);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(azureResourceService).getSubscriptions(any(IdentifierRef.class), eq(ORG_ID), eq(PROJECT_ID), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetSubscriptions_OrgScope() {
    // Given
    String orgScopedConnectorRef = "org." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(null)
                              .uniqueId(ORG_ID)
                              .build();

    AzureSubscriptionsDTO expectedSubscriptions = AzureSubscriptionsDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, null)).thenReturn(scopeInfo);
    when(azureResourceService.getSubscriptions(any(IdentifierRef.class), eq(ORG_ID), eq(null), eq(scopeInfo)))
        .thenReturn(expectedSubscriptions);

    // When
    AzureSubscriptionsDTO result =
        acrArtifactApiUtils.getSubscriptions(ACCOUNT_ID, ORG_ID, null, orgScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedSubscriptions);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, null);
    verify(azureResourceService).getSubscriptions(any(IdentifierRef.class), eq(ORG_ID), eq(null), eq(scopeInfo));
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testGetSubscriptions_AccountScope() {
    // Given
    String accountScopedConnectorRef = "account." + CONNECTOR_REF;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(null)
                              .projectIdentifier(null)
                              .uniqueId(ACCOUNT_ID)
                              .build();

    AzureSubscriptionsDTO expectedSubscriptions = AzureSubscriptionsDTO.builder().build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(scopeInfo);
    when(azureResourceService.getSubscriptions(any(IdentifierRef.class), eq(null), eq(null), eq(scopeInfo)))
        .thenReturn(expectedSubscriptions);

    // When
    AzureSubscriptionsDTO result =
        acrArtifactApiUtils.getSubscriptions(ACCOUNT_ID, null, null, accountScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).isSameAs(expectedSubscriptions);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, null, null);
    verify(azureResourceService).getSubscriptions(any(IdentifierRef.class), eq(null), eq(null), eq(scopeInfo));
  }
}
