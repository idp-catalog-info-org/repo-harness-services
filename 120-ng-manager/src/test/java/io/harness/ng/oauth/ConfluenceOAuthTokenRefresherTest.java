/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorMapper;
import io.harness.connector.entities.embedded.confluenceconnector.ConfluenceConnector;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.confluenceconnector.ConfluenceApiAccessType;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.security.authentication.oauth.ConfluenceConfig;

import clients.iromanager.remote.connectors.confluence.ConfluenceRetroFitClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class ConfluenceOAuthTokenRefresherTest extends NgManagerTestBase {
  private static final String TEST_ACCOUNT_ID = "testAccountId";
  private static final String TEST_CONNECTOR_ID = "testConnectorId";
  private static final String TEST_ACCESS_TOKEN = "testAccessToken";
  private static final String TEST_REFRESH_TOKEN = "testRefreshToken";

  @Mock private OAuthTokenRefresherHelper oAuthTokenRefresherHelper;
  @Mock private NextGenConfiguration configuration;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private ConnectorMapper connectorMapper;
  @Mock private DecryptionHelper decryptionHelper;
  @Mock private ConfluenceRetroFitClient confluenceRetroFitClient;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @InjectMocks private ConfluenceOAuthTokenRefresher confluenceOAuthTokenRefresher;

  private ConfluenceConnector testConnector;
  private ConfluenceConfig confluenceConfig;

  @Before
  public void setup() {
    // Setup test connector using mock since builder doesn't include parent class fields
    testConnector = mock(ConfluenceConnector.class);
    Mockito.when(testConnector.getAccountIdentifier()).thenReturn(TEST_ACCOUNT_ID);
    Mockito.when(testConnector.getIdentifier()).thenReturn(TEST_CONNECTOR_ID);
    Mockito.when(testConnector.getParentUniqueId()).thenReturn("parent_id");
    Mockito.when(testConnector.getAccessTokenRef()).thenReturn(TEST_ACCESS_TOKEN);
    Mockito.when(testConnector.getRefreshTokenRef()).thenReturn(TEST_REFRESH_TOKEN);
    Mockito.when(testConnector.getApiAccessType()).thenReturn(ConfluenceApiAccessType.OAUTH);

    // Setup configuration
    confluenceConfig = ConfluenceConfig.builder().clientId("client_id").clientSecret("client_secret").build();
    Mockito.when(configuration.getConfluenceConfig()).thenReturn(confluenceConfig);
  }

  @Test
  @Owner(developers = OwnerRule.RAJ_DAS)
  @Category(UnitTests.class)
  public void testHandle_InvalidEntityType() {
    // Arrange
    String invalidEntity = "Invalid Entity";

    // Act
    confluenceOAuthTokenRefresher.handle(invalidEntity);

    // Assert - Should not process invalid entity types
    verify(oAuthTokenRefresherHelper, never()).updateContext();
  }

  @Test
  @Owner(developers = OwnerRule.RAJ_DAS)
  @Category(UnitTests.class)
  public void testGetOAuthDecrypted() {
    // Arrange
    try (MockedStatic<SecretRefHelper> mockedSecretRefHelper = Mockito.mockStatic(SecretRefHelper.class)) {
      SecretRefData mockTokenRef = mock(SecretRefData.class);
      SecretRefData mockRefreshTokenRef = mock(SecretRefData.class);

      mockedSecretRefHelper.when(() -> SecretRefHelper.createSecretRef(TEST_ACCESS_TOKEN)).thenReturn(mockTokenRef);
      mockedSecretRefHelper.when(() -> SecretRefHelper.createSecretRef(TEST_REFRESH_TOKEN))
          .thenReturn(mockRefreshTokenRef);

      // Act
      OAuthRef result = confluenceOAuthTokenRefresher.getOAuthDecrypted(testConnector);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getTokenRef()).isEqualTo(mockTokenRef);
      assertThat(result.getRefreshTokenRef()).isEqualTo(mockRefreshTokenRef);
    }
  }

  @Test
  @Owner(developers = OwnerRule.RAJ_DAS)
  @Category(UnitTests.class)
  public void testGetOAuthDecrypted_WithNullTokenRefs() {
    // Arrange
    ConfluenceConnector connectorWithNullRefs = mock(ConfluenceConnector.class);
    Mockito.when(connectorWithNullRefs.getAccessTokenRef()).thenReturn(null);
    Mockito.when(connectorWithNullRefs.getRefreshTokenRef()).thenReturn(null);

    try (MockedStatic<SecretRefHelper> mockedSecretRefHelper = Mockito.mockStatic(SecretRefHelper.class)) {
      mockedSecretRefHelper.when(() -> SecretRefHelper.createSecretRef(null)).thenReturn(null);

      // Act
      OAuthRef result = confluenceOAuthTokenRefresher.getOAuthDecrypted(connectorWithNullRefs);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getTokenRef()).isNull();
      assertThat(result.getRefreshTokenRef()).isNull();
    }
  }
}
