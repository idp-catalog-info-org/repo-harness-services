/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.beans.FeatureName.HAR_ENABLED;
import static io.harness.delegate.beans.connector.docker.DockerAuthType.USER_PASSWORD;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.BaseNGAccess;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Owner;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.utils.PmsFeatureFlagService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ConnectorUtilsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String HARNESS_DEFAULT_LITE_ENGINE_IMAGE = "harness/default-lite-engine";
  private static final String HARNESS_DEFAULT_ADDON_TAG_IMAGE = "harness/default-addon-tag";
  @Mock private ContainerExecutionConfig containerExecutionConfig;
  @Mock private PmsFeatureFlagService ciFeatureFlagService;
  @Mock private ServiceHttpClientConfig harnessRegistryConfig;
  @Mock private io.harness.ci.utils.HarnessRegistryConnectorUtils harnessRegistryConnectorUtils;
  @InjectMocks private ConnectorUtils connectorUtils;
  private BaseNGAccess ngAccess =
      BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_ID).projectIdentifier(PROJECT_ID).build();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetConnectorDetailsWithConversionInfoForHAR() {
    DockerConnectorDTO dockerConnectorDTO =
        DockerConnectorDTO.builder()
            .auth(DockerAuthenticationDTO.builder()
                      .authType(USER_PASSWORD)
                      .credentials(DockerUserNamePasswordDTO.builder()
                                       .passwordRef(SecretRefData.builder().decryptedValue("dd".toCharArray()).build())
                                       .username("harness")
                                       .build())
                      .build())
            .build();
    ConnectorConversionInfo connectorConversionInfo = ConnectorConversionInfo.builder().registryRef("registry").build();
    when(ciFeatureFlagService.isEnabled(any(), eq(HAR_ENABLED))).thenReturn(true);
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorConfig(dockerConnectorDTO).build();
    when(harnessRegistryConnectorUtils.getConnectorDetailsForHarnessArtifactRegistry(any()))
        .thenReturn(connectorDetails);
    when(harnessRegistryConfig.getBaseUrl()).thenReturn("reg");
    ConnectorDetails connectorDetail =
        connectorUtils.getConnectorDetailsWithConversionInfo(ngAccess, connectorConversionInfo);
    assertThat(connectorDetail.getConnectorConfig()).isInstanceOf(DockerConnectorDTO.class);
    DockerConnectorDTO dockerConnectorDTOFromTest = (DockerConnectorDTO) connectorDetail.getConnectorConfig();
    assertThat(dockerConnectorDTOFromTest.getAuth().getCredentials()).isInstanceOf(DockerUserNamePasswordDTO.class);
  }
}
