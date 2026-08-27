/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.salesforce;

import static io.harness.rule.OwnerRule.HARSHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.salesforce.dtos.SalesforcePackageDetailDTO;
import io.harness.cdng.artifact.resources.salesforce.dtos.SalesforcePackageDetailsDTO;
import io.harness.cdng.artifact.resources.salesforce.dtos.SalesforcePackageVersionDetailDTO;
import io.harness.cdng.artifact.resources.salesforce.dtos.SalesforcePackageVersionDetailsDTO;
import io.harness.cdng.artifact.resources.salesforce.service.SalesforceArtifactResourceService;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.artifacts.resources.util.ResolvedFieldValueWithYamlExpressionEvaluator;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class SalesforceArtifactResourceTest extends CategoryTest {
  @Mock private SalesforceArtifactResourceService salesforceArtifactResourceService;
  @Mock private ArtifactResourceUtils artifactResourceUtils;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @InjectMocks private SalesforceArtifactResource salesforceArtifactResource;

  private static final String ACCOUNT_ID = "account123";
  private static final String ORG_ID = "org123";
  private static final String PROJECT_ID = "project123";
  private static final String CONNECTOR_ID = "connector123";
  private static final String PACKAGE_ID = "package123";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetPackageDetails() {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(CONNECTOR_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID);

    SalesforcePackageDetailDTO packageDetail =
        SalesforcePackageDetailDTO.builder().id("pkg1").name("Package 1").namespacePrefix("ns1").build();

    List<SalesforcePackageDetailDTO> packageDetails = Arrays.asList(packageDetail);
    SalesforcePackageDetailsDTO packageDetailsDTO =
        SalesforcePackageDetailsDTO.builder().salesforcePackageDetails(packageDetails).build();

    doReturn(packageDetailsDTO)
        .when(salesforceArtifactResourceService)
        .getPackageDetails(eq(connectorIdentifierRef), eq(ORG_ID), eq(PROJECT_ID), eq(null));

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder()
                 .yamlExpressionEvaluator(null)
                 .value(CONNECTOR_ID)
                 .build())
        .when(artifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, CONNECTOR_ID, null, null, null, null);

    ResponseDTO<SalesforcePackageDetailsDTO> response = salesforceArtifactResource.getPackageDetails(
        CONNECTOR_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, null, null, null);

    // Verify
    assertThat(response).isNotNull();
    assertThat(response.getData()).isEqualTo(packageDetailsDTO);
    assertThat(response.getData().getSalesforcePackageDetails()).hasSize(1);
    assertThat(response.getData().getSalesforcePackageDetails().get(0).getId()).isEqualTo("pkg1");
    assertThat(response.getData().getSalesforcePackageDetails().get(0).getName()).isEqualTo("Package 1");

    verify(salesforceArtifactResourceService)
        .getPackageDetails(eq(connectorIdentifierRef), eq(ORG_ID), eq(PROJECT_ID), eq(null));
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetPackageVersionDetails() {
    IdentifierRef connectorIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(CONNECTOR_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID);

    SalesforcePackageVersionDetailDTO versionDetail =
        SalesforcePackageVersionDetailDTO.builder().id("ver1").name("Version 1").build();

    List<SalesforcePackageVersionDetailDTO> versionDetails = Arrays.asList(versionDetail);
    SalesforcePackageVersionDetailsDTO versionDetailsDTO =
        SalesforcePackageVersionDetailsDTO.builder().salesforcePackageVersionDetails(versionDetails).build();

    doReturn(versionDetailsDTO)
        .when(salesforceArtifactResourceService)
        .getPackageVersionDetails(eq(connectorIdentifierRef), eq(PACKAGE_ID), eq(ORG_ID), eq(PROJECT_ID), eq(null));

    doReturn(ResolvedFieldValueWithYamlExpressionEvaluator.builder()
                 .yamlExpressionEvaluator(null)
                 .value(CONNECTOR_ID)
                 .build())
        .when(artifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, CONNECTOR_ID, null, null, null, null);

    doReturn(
        ResolvedFieldValueWithYamlExpressionEvaluator.builder().yamlExpressionEvaluator(null).value(PACKAGE_ID).build())
        .when(artifactResourceUtils)
        .getResolvedFieldValueWithYamlExpressionEvaluator(
            ACCOUNT_ID, ORG_ID, PROJECT_ID, null, null, PACKAGE_ID, null, null, null, null);

    ResponseDTO<SalesforcePackageVersionDetailsDTO> response = salesforceArtifactResource.getPackageVersionDetails(
        CONNECTOR_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID, PACKAGE_ID, null, null, null, null, null);

    assertThat(response).isNotNull();
    assertThat(response.getData()).isEqualTo(versionDetailsDTO);
    assertThat(response.getData().getSalesforcePackageVersionDetails()).hasSize(1);
    assertThat(response.getData().getSalesforcePackageVersionDetails().get(0).getId()).isEqualTo("ver1");
    assertThat(response.getData().getSalesforcePackageVersionDetails().get(0).getName()).isEqualTo("Version 1");

    verify(salesforceArtifactResourceService)
        .getPackageVersionDetails(eq(connectorIdentifierRef), eq(PACKAGE_ID), eq(ORG_ID), eq(PROJECT_ID), eq(null));
  }
}
