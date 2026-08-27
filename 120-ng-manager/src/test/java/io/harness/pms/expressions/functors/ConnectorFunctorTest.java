/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.YAGYANSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
public class ConnectorFunctorTest extends CategoryTest {
  private static final Long EXPRESSION_FUNCTOR_TOKEN = 1L;
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String CONNECTOR_ID = "connectorId";

  @Mock ConnectorService connectorService;
  @InjectMocks ConnectorFunctor connectorFunctor;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetNotValidArgs() {
    doReturn(
        Optional.of(
            ConnectorResponseDTO.builder()
                .connector(
                    ConnectorInfoDTO.builder()
                        .accountIdentifier(ACCOUNT_IDENTIFIER)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJECT_IDENTIFIER)
                        .connectorType(ConnectorType.GITHUB)
                        .connectorConfig(
                            GithubConnectorDTO.builder()
                                .url("https://www.github.com/dummy")
                                .connectionType(GitConnectionType.REPO)
                                .authentication(
                                    GithubAuthenticationDTO.builder()
                                        .authType(GitAuthType.HTTP)
                                        .credentials(GithubHttpCredentialsDTO.builder()
                                                         .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                         .httpCredentialsSpec(GithubUsernamePasswordDTO.builder()
                                                                                  .username("harness")
                                                                                  .passwordRef(SecretRefData.builder()
                                                                                                   .identifier("pass")
                                                                                                   .scope(Scope.ORG)
                                                                                                   .build())
                                                                                  .build())
                                                         .build()

                                                )
                                        .build())
                                .build())

                        .build())
                .build()))
        .when(connectorService)
        .getByRef(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_ID);

    Map<String, Object> responseMap = (Map<String, Object>) connectorFunctor.get(getAmbiance(), CONNECTOR_ID);
    assertThat(responseMap).isNotEmpty();

    assertThat(responseMap.get("type")).isEqualTo("Github");

    assertThat(((Map<String, Object>) responseMap.get("spec")).get("url")).isEqualTo("https://www.github.com/dummy");
    assertThat(((Map<String, Object>) responseMap.get("spec")).get("type")).isEqualTo("Repo");

    assertThat(((Map<String, Map>) responseMap.get("spec")).get("authentication").get("type")).isEqualTo("Http");

    assertThat(
        ((Map) ((Map) ((Map<String, Map>) responseMap.get("spec")).get("authentication").get("spec")).get("spec"))
            .get("username"))
        .isEqualTo("harness");
    assertThat(
        ((Map) ((Map) ((Map<String, Map>) responseMap.get("spec")).get("authentication").get("spec")).get("spec"))
            .get("passwordRef"))
        .isEqualTo("org.pass");
  }

  @Test
  @Owner(developers = YAGYANSH)
  @Category(UnitTests.class)
  public void testGetWithEmptyResponse() {
    // Set up mock to return empty response
    doReturn(Optional.empty())
        .when(connectorService)
        .getByRef(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, CONNECTOR_ID);

    // Execute the test
    Map<String, Object> responseMap = (Map<String, Object>) connectorFunctor.get(getAmbiance(), CONNECTOR_ID);

    // Verify empty map is returned
    assertThat(responseMap).isEmpty();
  }

  @Test
  @Owner(developers = YAGYANSH)
  @Category(UnitTests.class)
  public void testGetWithScopedConnectorIds() {
    // Create test connector response
    ConnectorResponseDTO connectorResponse =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .accountIdentifier(ACCOUNT_IDENTIFIER)
                           .connectorType(ConnectorType.GITHUB)
                           .identifier("scoped-connector")
                           .connectorConfig(GithubConnectorDTO.builder()
                                                .url("https://github.com/scoped")
                                                .connectionType(GitConnectionType.REPO)
                                                .build())
                           .build())
            .build();

    // Set up mock for scoped connector ID (e.g., "account.scoped-connector")
    String scopedConnectorId = "account.scoped-connector";
    doReturn(Optional.of(connectorResponse))
        .when(connectorService)
        .getByRef(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, scopedConnectorId);

    // Test with a scoped connector ID
    Map<String, Object> responseMap = (Map<String, Object>) connectorFunctor.get(getAmbiance(), scopedConnectorId);

    // Verify results
    assertThat(responseMap).isNotEmpty();
    assertThat(responseMap.get("type")).isEqualTo("Github");
    assertThat(((Map<String, Object>) responseMap.get("spec")).get("url")).isEqualTo("https://github.com/scoped");
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
        .setExpressionFunctorToken(EXPRESSION_FUNCTOR_TOKEN)
        .build();
  }
}
