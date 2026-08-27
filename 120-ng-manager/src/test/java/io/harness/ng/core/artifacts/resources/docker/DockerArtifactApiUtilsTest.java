/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.IdentifierRef;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.docker.dtos.DockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.docker.dtos.DockerResponseDTO;
import io.harness.cdng.artifact.resources.docker.service.DockerResourceService;
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

public class DockerArtifactApiUtilsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String IMAGE_PATH = "library/nginx";
  private static final String CONNECTOR_REF = "dockerConnector";
  private static final String TAG = "1.0.0";

  @Mock private DockerResourceService dockerResourceService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private ArtifactResourceUtils artifactResourceUtils;

  @InjectMocks private DockerArtifactApiUtils dockerArtifactApiUtils;

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

    DockerBuildDetailsDTO buildDetail = DockerBuildDetailsDTO.builder().tag(TAG).imagePath(IMAGE_PATH).build();
    DockerResponseDTO expectedResponse =
        DockerResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(dockerResourceService.getBuildDetails(
             any(IdentifierRef.class), eq(IMAGE_PATH), eq(ORG_ID), eq(PROJECT_ID), isNull(), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    DockerResponseDTO result =
        dockerArtifactApiUtils.getBuildDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, IMAGE_PATH, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo(TAG);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(dockerResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(IMAGE_PATH), eq(ORG_ID), eq(PROJECT_ID), isNull(), eq(scopeInfo));
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

    DockerBuildDetailsDTO buildDetail1 = DockerBuildDetailsDTO.builder().tag("1.0.0").imagePath(IMAGE_PATH).build();
    DockerBuildDetailsDTO buildDetail2 = DockerBuildDetailsDTO.builder().tag("2.0.0").imagePath(IMAGE_PATH).build();
    DockerBuildDetailsDTO buildDetail3 = DockerBuildDetailsDTO.builder().tag("3.0.0").imagePath(IMAGE_PATH).build();
    DockerResponseDTO expectedResponse =
        DockerResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail1, buildDetail2, buildDetail3)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(dockerResourceService.getBuildDetails(
             any(IdentifierRef.class), eq(IMAGE_PATH), eq(ORG_ID), eq(PROJECT_ID), isNull(), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    DockerResponseDTO result =
        dockerArtifactApiUtils.getBuildDetails(ACCOUNT_ID, ORG_ID, PROJECT_ID, IMAGE_PATH, CONNECTOR_REF);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(3);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo("1.0.0");
    assertThat(result.getBuildDetailsList().get(1).getTag()).isEqualTo("2.0.0");
    assertThat(result.getBuildDetailsList().get(2).getTag()).isEqualTo("3.0.0");

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);
    verify(dockerResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(IMAGE_PATH), eq(ORG_ID), eq(PROJECT_ID), isNull(), eq(scopeInfo));
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

    DockerBuildDetailsDTO buildDetail = DockerBuildDetailsDTO.builder().tag(TAG).imagePath(IMAGE_PATH).build();
    DockerResponseDTO expectedResponse =
        DockerResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, null)).thenReturn(scopeInfo);
    when(dockerResourceService.getBuildDetails(
             any(IdentifierRef.class), eq(IMAGE_PATH), eq(ORG_ID), eq(null), isNull(), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    DockerResponseDTO result =
        dockerArtifactApiUtils.getBuildDetails(ACCOUNT_ID, ORG_ID, null, IMAGE_PATH, orgScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo(TAG);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, null);
    verify(dockerResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(IMAGE_PATH), eq(ORG_ID), eq(null), isNull(), eq(scopeInfo));
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

    DockerBuildDetailsDTO buildDetail = DockerBuildDetailsDTO.builder().tag(TAG).imagePath(IMAGE_PATH).build();
    DockerResponseDTO expectedResponse =
        DockerResponseDTO.builder().buildDetailsList(Arrays.asList(buildDetail)).build();

    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, null, null)).thenReturn(scopeInfo);
    when(dockerResourceService.getBuildDetails(
             any(IdentifierRef.class), eq(IMAGE_PATH), eq(null), eq(null), isNull(), eq(scopeInfo)))
        .thenReturn(expectedResponse);

    // When
    DockerResponseDTO result =
        dockerArtifactApiUtils.getBuildDetails(ACCOUNT_ID, null, null, IMAGE_PATH, accountScopedConnectorRef);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getBuildDetailsList()).hasSize(1);
    assertThat(result.getBuildDetailsList().get(0).getTag()).isEqualTo(TAG);

    verify(scopeInfoService).getScopeInfo(ACCOUNT_ID, null, null);
    verify(dockerResourceService)
        .getBuildDetails(any(IdentifierRef.class), eq(IMAGE_PATH), eq(null), eq(null), isNull(), eq(scopeInfo));
  }
}
