/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.idp.common.Constants.INTEGRATIONS_GITHUB_APP_APPLICATION_ID;
import static io.harness.idp.common.Constants.INTEGRATIONS_GITHUB_APP_INSTALLATION_ID;
import static io.harness.idp.common.Constants.INTEGRATIONS_GITHUB_APP_PRIVATE_KEY;
import static io.harness.idp.common.Constants.INTEGRATIONS_GITHUB_TOKEN;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_READ_VALIDATION_FILE_URL;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.githubConnectorDTORepoToken;
import static io.harness.rule.OwnerRule.ROUNAK;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cistatus.service.GithubAppConfig;
import io.harness.cistatus.service.GithubService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.idp.gitintegration.response.GitIntegrationReadValidationResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.gitsync.CreateFileResponse;
import io.harness.gitsync.ErrorDetails;
import io.harness.gitsync.GetFileRequest;
import io.harness.gitsync.GetFileResponse;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc;
import io.harness.gitsync.UpdateFileResponse;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationGithubAppAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GithubIntegrationOpsImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  AutoCloseable openMocks;

  @InjectMocks GithubIntegrationOpsImpl githubIntegrationOps;

  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DecryptionHelper decryptionHelper;
  @Mock GithubService githubService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareDirectTokenAuth() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().tokenRef(SecretRefData.builder().identifier("secret").build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(githubUsernameTokenDTO);

    GithubIntegrationEntity githubIntegrationEntity =
        githubIntegrationOps.prepare(githubConnectorDtoRepoToken.getConnectorInfo());
    assertEquals(Constants.IDP_PREFIX + githubConnectorDtoRepoToken.getConnectorInfo().getIdentifier(),
        githubIntegrationEntity.getIdentifier());
    assertEquals(IntegrationEntity.Integration.GIT, githubIntegrationEntity.getIntegration());
    assertEquals(IntegrationEntity.ParentType.GITHUB, githubIntegrationEntity.getParentType());
    assertEquals(githubConnectorDtoRepoToken.getConnectorInfo().getIdentifier(),
        githubIntegrationEntity.getConnectorIdentifier());
    assertEquals("github.com", githubIntegrationEntity.getHost());
    assertEquals(IntegrationEntity.SubType.GITHUB_DIRECT, githubIntegrationEntity.getSubType());
    assertEquals(GitIntegrationEntity.AuthMode.TOKEN, githubIntegrationEntity.getAuthMode());
    assertFalse(githubIntegrationEntity.isExecuteOnDelegate());
    assertEquals("github.com", githubIntegrationEntity.getAdditionalIndexer());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareEnterpriseTokenAuth() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    githubConnectorDTO.setUrl("https://www.ghe.com");
    connectorInfoDTO.setConnectorConfig(githubConnectorDTO);
    githubConnectorDtoRepoToken.setConnectorInfo(connectorInfoDTO);

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().tokenRef(SecretRefData.builder().identifier("secret").build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(githubUsernameTokenDTO);

    GithubIntegrationEntity githubIntegrationEntity =
        githubIntegrationOps.prepare(githubConnectorDtoRepoToken.getConnectorInfo());
    assertEquals(Constants.IDP_PREFIX + githubConnectorDtoRepoToken.getConnectorInfo().getIdentifier(),
        githubIntegrationEntity.getIdentifier());
    assertEquals(IntegrationEntity.Integration.GIT, githubIntegrationEntity.getIntegration());
    assertEquals(IntegrationEntity.ParentType.GITHUB, githubIntegrationEntity.getParentType());
    assertEquals(githubConnectorDtoRepoToken.getConnectorInfo().getIdentifier(),
        githubIntegrationEntity.getConnectorIdentifier());
    assertEquals("ghe.com", githubIntegrationEntity.getHost());
    assertEquals(IntegrationEntity.SubType.GITHUB_ENTERPRISE, githubIntegrationEntity.getSubType());
    assertEquals(GitIntegrationEntity.AuthMode.TOKEN, githubIntegrationEntity.getAuthMode());
    assertFalse(githubIntegrationEntity.isExecuteOnDelegate());
    assertEquals("ghe.com", githubIntegrationEntity.getAdditionalIndexer());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareInvalidAuth() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubAuthenticationDTO githubAuthenticationDTO = githubConnectorDTO.getAuthentication();
    githubAuthenticationDTO.setAuthType(GitAuthType.SSH);
    githubConnectorDTO.setAuthentication(githubAuthenticationDTO);
    connectorInfoDTO.setConnectorConfig(githubConnectorDTO);
    githubConnectorDtoRepoToken.setConnectorInfo(connectorInfoDTO);
    githubIntegrationOps.prepare(githubConnectorDtoRepoToken.getConnectorInfo());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetConnectorConfigDTO() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GithubConnectorDTO githubConnectorDTOFunc =
        githubIntegrationOps.getConnectorConfigDTO(githubConnectorDtoRepoToken.getConnectorInfo());
    assertEquals(githubConnectorDTO, githubConnectorDTOFunc);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndGetAuthMode() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationEntity.AuthMode authMode = githubIntegrationOps.validateAndGetAuthMode(githubConnectorDTO);
    assertEquals(GitIntegrationEntity.AuthMode.TOKEN, authMode);

    GithubAuthenticationDTO githubAuthenticationDTO = githubConnectorDTO.getAuthentication();
    GithubHttpCredentialsDTO githubCredentialsDTO = (GithubHttpCredentialsDTO) githubAuthenticationDTO.getCredentials();
    githubCredentialsDTO.setType(GithubHttpAuthenticationType.GITHUB_APP);
    githubAuthenticationDTO.setCredentials(githubCredentialsDTO);
    githubConnectorDTO.setAuthentication(githubAuthenticationDTO);

    authMode = githubIntegrationOps.validateAndGetAuthMode(githubConnectorDTO);
    assertEquals(GitIntegrationEntity.AuthMode.GITHUB_APP, authMode);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndGetAuthModeInvalid() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubAuthenticationDTO githubAuthenticationDTO = githubConnectorDTO.getAuthentication();
    GithubHttpCredentialsDTO githubCredentialsDTO = (GithubHttpCredentialsDTO) githubAuthenticationDTO.getCredentials();
    githubCredentialsDTO.setType(GithubHttpAuthenticationType.ANONYMOUS);
    githubAuthenticationDTO.setCredentials(githubCredentialsDTO);
    githubConnectorDTO.setAuthentication(githubAuthenticationDTO);

    githubIntegrationOps.validateAndGetAuthMode(githubConnectorDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthToken() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    GithubUsernameTokenDTO githubUsernameTokenDTO =
        GithubUsernameTokenDTO.builder().tokenRef(SecretRefData.builder().identifier("secret").build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(githubUsernameTokenDTO);

    GitIntegrationAuth gitIntegrationAuth = githubIntegrationOps.getAuth(githubConnectorDTO, TEST_ACCOUNT_IDENTIFIER);
    assert gitIntegrationAuth instanceof GitIntegrationTokenAuth;
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthGithubApp() {
    GithubAppDTO githubAppDTO = GithubAppDTO.builder()
                                    .installationId("installationId")
                                    .applicationId("applicationId")
                                    .installationIdRef(SecretRefData.builder().identifier("secret").build())
                                    .applicationIdRef(SecretRefData.builder().identifier("secret").build())
                                    .privateKeyRef(SecretRefData.builder().identifier("secret").build())
                                    .build();

    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubAuthenticationDTO githubAuthenticationDTO = githubConnectorDTO.getAuthentication();
    GithubHttpCredentialsDTO githubCredentialsDTO = (GithubHttpCredentialsDTO) githubAuthenticationDTO.getCredentials();
    githubCredentialsDTO.setType(GithubHttpAuthenticationType.GITHUB_APP);
    githubCredentialsDTO.setHttpCredentialsSpec(githubAppDTO);
    githubAuthenticationDTO.setCredentials(githubCredentialsDTO);
    githubConnectorDTO.setAuthentication(githubAuthenticationDTO);

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    when(decryptionHelper.decrypt(any(), any())).thenReturn(githubAppDTO);

    GitIntegrationAuth gitIntegrationAuth = githubIntegrationOps.getAuth(githubConnectorDTO, TEST_ACCOUNT_IDENTIFIER);
    assert gitIntegrationAuth instanceof GitIntegrationGithubAppAuth;
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthInvalid() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GithubAuthenticationDTO githubAuthenticationDTO = githubConnectorDTO.getAuthentication();
    GithubHttpCredentialsDTO githubCredentialsDTO = (GithubHttpCredentialsDTO) githubAuthenticationDTO.getCredentials();
    githubCredentialsDTO.setType(GithubHttpAuthenticationType.ANONYMOUS);
    githubAuthenticationDTO.setCredentials(githubCredentialsDTO);
    githubConnectorDTO.setAuthentication(githubAuthenticationDTO);

    githubIntegrationOps.getAuth(githubConnectorDTO, TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationConfigs() {
    GitIntegrationGithubAppAuth gitIntegrationGithubAppAuth = new GitIntegrationGithubAppAuth();
    gitIntegrationGithubAppAuth.setApplicationId("applicationId");
    gitIntegrationGithubAppAuth.setInstallationId("installationId");
    GithubIntegrationEntity githubIntegrationEntity = GithubIntegrationEntity.builder()
                                                          .host("github.com")
                                                          .authMode(GitIntegrationEntity.AuthMode.GITHUB_APP)
                                                          .auth(gitIntegrationGithubAppAuth)
                                                          .build();

    Map<String, String> integrationConfigs = githubIntegrationOps.getIntegrationConfigs(githubIntegrationEntity);

    assertEquals(2, integrationConfigs.size());
    assertEquals("applicationId",
        integrationConfigs.get(INTEGRATIONS_GITHUB_APP_APPLICATION_ID + "_"
            + "GITHUB_COM"));
    assertEquals("installationId",
        integrationConfigs.get(INTEGRATIONS_GITHUB_APP_INSTALLATION_ID + "_"
            + "GITHUB_COM"));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationSecretsGithubApp() {
    GitIntegrationGithubAppAuth gitIntegrationGithubAppAuth = new GitIntegrationGithubAppAuth();
    gitIntegrationGithubAppAuth.setApplicationIdSecretIdentifier("applicationId");
    gitIntegrationGithubAppAuth.setInstallationIdSecretIdentifier("installationId");
    gitIntegrationGithubAppAuth.setPrivateKeySecretIdentifier("privateKey");
    GithubIntegrationEntity githubIntegrationEntity = GithubIntegrationEntity.builder()
                                                          .host("github.com")
                                                          .authMode(GitIntegrationEntity.AuthMode.GITHUB_APP)
                                                          .auth(gitIntegrationGithubAppAuth)
                                                          .build();

    Map<String, String> integrationSecrets = githubIntegrationOps.getIntegrationSecrets(githubIntegrationEntity);

    assertEquals(3, integrationSecrets.size());
    assertEquals("applicationId",
        integrationSecrets.get(INTEGRATIONS_GITHUB_APP_APPLICATION_ID + "_"
            + "GITHUB_COM"));
    assertEquals("installationId",
        integrationSecrets.get(INTEGRATIONS_GITHUB_APP_INSTALLATION_ID + "_"
            + "GITHUB_COM"));
    assertEquals("privateKey",
        integrationSecrets.get(INTEGRATIONS_GITHUB_APP_PRIVATE_KEY + "_"
            + "GITHUB_COM"));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationSecretsToken() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    GithubIntegrationEntity githubIntegrationEntity = GithubIntegrationEntity.builder()
                                                          .host("github.com")
                                                          .authMode(GitIntegrationEntity.AuthMode.TOKEN)
                                                          .auth(gitIntegrationTokenAuth)
                                                          .build();

    Map<String, String> integrationSecrets = githubIntegrationOps.getIntegrationSecrets(githubIntegrationEntity);

    assertEquals(1, integrationSecrets.size());
    assertEquals("secret",
        integrationSecrets.get(INTEGRATIONS_GITHUB_TOKEN + "_"
            + "GITHUB_COM"));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationAppConfig() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    GithubIntegrationEntity githubIntegrationEntity = GithubIntegrationEntity.builder()
                                                          .host("github.com")
                                                          .authMode(GitIntegrationEntity.AuthMode.TOKEN)
                                                          .auth(gitIntegrationTokenAuth)
                                                          .build();

    String integrationConfig =
        githubIntegrationOps.getIntegrationAppConfig(githubIntegrationEntity, githubConnectorDTO);
    assertEquals("integrations:\n"
            + "  github:\n"
            + "    - host: github.com\n"
            + "      apiBaseUrl: https://api.github.com\n"
            + "      token: ${GITHUB_TOKEN_GITHUB_COM}",
        integrationConfig);

    githubConnectorDTO.setUrl("https://www.ghe.com");
    githubIntegrationEntity = GithubIntegrationEntity.builder()
                                  .host("ghe.com")
                                  .authMode(GitIntegrationEntity.AuthMode.TOKEN)
                                  .auth(gitIntegrationTokenAuth)
                                  .build();

    integrationConfig = githubIntegrationOps.getIntegrationAppConfig(githubIntegrationEntity, githubConnectorDTO);
    assertEquals("integrations:\n"
            + "  github:\n"
            + "    - host: ghe.com\n"
            + "      apiBaseUrl: https://ghe.com/api/v3\n"
            + "      token: ${GITHUB_TOKEN_GHE_COM}",
        integrationConfig);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationAppConfigGithubApp() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationGithubAppAuth gitIntegrationGithubAppAuth = new GitIntegrationGithubAppAuth();
    GithubIntegrationEntity githubIntegrationEntity = GithubIntegrationEntity.builder()
                                                          .host("github.com")
                                                          .authMode(GitIntegrationEntity.AuthMode.GITHUB_APP)
                                                          .auth(gitIntegrationGithubAppAuth)
                                                          .build();

    String integrationConfig =
        githubIntegrationOps.getIntegrationAppConfig(githubIntegrationEntity, githubConnectorDTO);

    assertEquals("integrations:\n"
            + "  github:\n"
            + "    - host: github.com\n"
            + "      apiBaseUrl: https://api.github.com\n"
            + "      apps:\n"
            + "        - appId: ${GITHUB_APP_APPLICATION_ID_GITHUB_COM}\n"
            + "          clientId: clientId\n"
            + "          clientSecret: clientSecret\n"
            + "          webhookSecret: webhookSecret\n"
            + "          privateKey: |\n"
            + "            ${GITHUB_APP_PRIVATE_KEY_GITHUB_COM}",
        integrationConfig);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermissionInvalidUrl() {
    GithubIntegrationEntity githubIntegrationEntity =
        GithubIntegrationEntity.builder()
            .readPermissionValidation(
                GitIntegrationEntity.ReadPermissionValidation.builder().fileUrl("Invalid^").build())
            .build();

    githubIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (GithubConnectorDTO) githubConnectorDTORepoToken().getConnectorInfo().getConnectorConfig(),
        githubIntegrationEntity, Map.of(), Map.of());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermissionGithubApp() {
    GitIntegrationGithubAppAuth gitIntegrationGithubAppAuth = new GitIntegrationGithubAppAuth();
    gitIntegrationGithubAppAuth.setApplicationId("applicationId");
    gitIntegrationGithubAppAuth.setInstallationId("installationId");
    gitIntegrationGithubAppAuth.setApplicationIdSecretIdentifier("applicationId");
    gitIntegrationGithubAppAuth.setInstallationIdSecretIdentifier("installationId");
    gitIntegrationGithubAppAuth.setPrivateKeySecretIdentifier("privateKey");
    GithubIntegrationEntity githubIntegrationEntity =
        GithubIntegrationEntity.builder()
            .host("github.com")
            .authMode(GitIntegrationEntity.AuthMode.GITHUB_APP)
            .auth(gitIntegrationGithubAppAuth)
            .readPermissionValidation(
                GitIntegrationEntity.ReadPermissionValidation.builder().fileUrl(TEST_READ_VALIDATION_FILE_URL).build())
            .build();

    Map<String, String> integrationConfigs = githubIntegrationOps.getIntegrationConfigs(githubIntegrationEntity);

    gitIntegrationGithubAppAuth.setApplicationId(null);
    gitIntegrationGithubAppAuth.setInstallationId(null);
    githubIntegrationEntity.setAuth(gitIntegrationGithubAppAuth);

    Map<String, String> integrationSecrets = githubIntegrationOps.getIntegrationSecrets(githubIntegrationEntity);

    when(githubService.getToken(any())).thenReturn("token");
    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITHUB_APP_PRIVATE_KEY + "_"
                 + "GITHUB_COM")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITHUB_APP_APPLICATION_ID + "_"
                 + "GITHUB_COM")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITHUB_APP_INSTALLATION_ID + "_"
                 + "GITHUB_COM")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITHUB_APP_PRIVATE_KEY + "_"
                 + "GITHUB_COM")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(secretManagerClientService.getSecret(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITHUB_APP_PRIVATE_KEY + "_"
                 + "GITHUB_COM")))
        .thenReturn(
            SecretResponseWrapper.builder().secret(SecretDTOV2.builder().type(SecretType.SecretFile).build()).build());
    when(githubService.getRepository(
             GithubAppConfig.builder().githubUrl("https://api.github.com").build(), "token", "abc", "def"))
        .thenReturn(new JSONObject().put("status", "success").put("error", ""));

    githubIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (GithubConnectorDTO) githubConnectorDTORepoToken().getConnectorInfo().getConnectorConfig(),
        githubIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("success", githubIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("", githubIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermissionGithubAppDelegateFailed() {
    GitIntegrationGithubAppAuth gitIntegrationGithubAppAuth = new GitIntegrationGithubAppAuth();
    gitIntegrationGithubAppAuth.setApplicationId("applicationId");
    gitIntegrationGithubAppAuth.setInstallationId("installationId");
    gitIntegrationGithubAppAuth.setApplicationIdSecretIdentifier("applicationId");
    gitIntegrationGithubAppAuth.setInstallationIdSecretIdentifier("installationId");
    gitIntegrationGithubAppAuth.setPrivateKeySecretIdentifier("privateKey");
    GithubIntegrationEntity githubIntegrationEntity =
        GithubIntegrationEntity.builder()
            .host("github.com")
            .authMode(GitIntegrationEntity.AuthMode.GITHUB_APP)
            .auth(gitIntegrationGithubAppAuth)
            .executeOnDelegate(true)
            .delegateSelectors(Set.of("delegate1"))
            .readPermissionValidation(
                GitIntegrationEntity.ReadPermissionValidation.builder().fileUrl(TEST_READ_VALIDATION_FILE_URL).build())
            .build();

    Map<String, String> integrationConfigs = githubIntegrationOps.getIntegrationConfigs(githubIntegrationEntity);

    gitIntegrationGithubAppAuth.setApplicationId(null);
    gitIntegrationGithubAppAuth.setInstallationId(null);
    githubIntegrationEntity.setAuth(gitIntegrationGithubAppAuth);

    Map<String, String> integrationSecrets = githubIntegrationOps.getIntegrationSecrets(githubIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITHUB_APP_PRIVATE_KEY + "_"
                 + "GITHUB_COM")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(secretManagerClientService.getSecret(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITHUB_APP_PRIVATE_KEY + "_"
                 + "GITHUB_COM")))
        .thenReturn(
            SecretResponseWrapper.builder().secret(SecretDTOV2.builder().type(SecretType.SecretText).build()).build());
    when(githubService.getToken(any())).thenReturn("token");
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(
            GitIntegrationReadValidationResponse.builder().code(400).status("failed").error("Invalid request").build());

    githubIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (GithubConnectorDTO) githubConnectorDTORepoToken().getConnectorInfo().getConnectorConfig(),
        githubIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("failed", githubIntegrationEntity.getReadPermissionValidation().getStatus());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermissionTokenDelegateSuccess() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    GithubIntegrationEntity githubIntegrationEntity =
        GithubIntegrationEntity.builder()
            .host("github.com")
            .authMode(GitIntegrationEntity.AuthMode.TOKEN)
            .auth(gitIntegrationTokenAuth)
            .executeOnDelegate(true)
            .delegateSelectors(Set.of("delegate1"))
            .readPermissionValidation(
                GitIntegrationEntity.ReadPermissionValidation.builder().fileUrl(TEST_READ_VALIDATION_FILE_URL).build())
            .build();

    Map<String, String> integrationConfigs = githubIntegrationOps.getIntegrationConfigs(githubIntegrationEntity);
    Map<String, String> integrationSecrets = githubIntegrationOps.getIntegrationSecrets(githubIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITHUB_TOKEN + "_"
                 + "GITHUB_COM")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(githubService.getToken(any())).thenReturn("token");
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GitIntegrationReadValidationResponse.builder().code(200).status("success").error("").build());

    githubIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (GithubConnectorDTO) githubConnectorDTORepoToken().getConnectorInfo().getConnectorConfig(),
        githubIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("success", githubIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("", githubIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermissionTokenDelegateError() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    GithubIntegrationEntity githubIntegrationEntity =
        GithubIntegrationEntity.builder()
            .host("github.com")
            .authMode(GitIntegrationEntity.AuthMode.TOKEN)
            .auth(gitIntegrationTokenAuth)
            .executeOnDelegate(true)
            .delegateSelectors(Set.of("delegate1"))
            .readPermissionValidation(
                GitIntegrationEntity.ReadPermissionValidation.builder().fileUrl(TEST_READ_VALIDATION_FILE_URL).build())
            .build();

    Map<String, String> integrationConfigs = githubIntegrationOps.getIntegrationConfigs(githubIntegrationEntity);
    Map<String, String> integrationSecrets = githubIntegrationOps.getIntegrationSecrets(githubIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITHUB_TOKEN + "_"
                 + "GITHUB_COM")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(githubService.getToken(any())).thenReturn("token");
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GitIntegrationReadValidationResponse.builder().code(500).status("failed").error("Error").build());

    githubIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (GithubConnectorDTO) githubConnectorDTORepoToken().getConnectorInfo().getConnectorConfig(),
        githubIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("failed", githubIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("Error", githubIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetRepository() {
    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorInfoDTO.getConnectorConfig();
    String repo = githubIntegrationOps.getRepository(githubConnectorDTO, "https://github.com/harness/harness-core.git");
    assertEquals("harness-core", repo);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAlreadyExistErrorMessage() {
    GithubIntegrationEntity githubIntegrationEntity = GithubIntegrationEntity.builder().host("github.com").build();
    String error = githubIntegrationOps.getAlreadyExistErrorMessage(githubIntegrationEntity);
    assertEquals("GitHub integration with host github.com already exists. ", error);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteValidationErrorScenario1() {
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository("https://github.com/harness/harness-core.git");
    writeValidationDetails.setBranch("develop");
    writeValidationDetails.setPath("/harness-services/");

    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();

    SourcePrincipalContextBuilder.setSourcePrincipal(new UserPrincipal("name", "email", "username", "accountId"));

    when(harnessToGitPushInfoService.createFile(any()))
        .thenReturn(
            CreateFileResponse.newBuilder()
                .setStatusCode(300)
                .setError(ErrorDetails.newBuilder().setErrorMessage("A file with this name already exists").build())
                .build());
    when(harnessToGitPushInfoService.updateFile(any()))
        .thenReturn(UpdateFileResponse.newBuilder()
                        .setStatusCode(300)
                        .setError(ErrorDetails.newBuilder()
                                      .setErrorMessage("Cannot update file as it has conflicts with remote")
                                      .build())
                        .build());
    when(harnessToGitPushInfoService.getFile(any()))
        .thenReturn(GetFileResponse.newBuilder()
                        .setStatusCode(300)
                        .setError(ErrorDetails.newBuilder().setErrorMessage("Error").build())
                        .build());

    githubIntegrationOps.validateWritePermission(TEST_ACCOUNT_IDENTIFIER, writeValidationDetails, connectorInfoDTO);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteValidationErrorScenario2() {
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository("https://github.com/harness/harness-core.git");
    writeValidationDetails.setBranch("develop");
    writeValidationDetails.setPath("/harness-services/");

    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();

    SourcePrincipalContextBuilder.setSourcePrincipal(new UserPrincipal("name", "email", "username", "accountId"));

    when(harnessToGitPushInfoService.createFile(any()))
        .thenReturn(CreateFileResponse.newBuilder()
                        .setStatusCode(300)
                        .setError(ErrorDetails.newBuilder().setErrorMessage("Not Found").build())
                        .build());

    githubIntegrationOps.validateWritePermission(TEST_ACCOUNT_IDENTIFIER, writeValidationDetails, connectorInfoDTO);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteValidationErrorScenario3() {
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository("https://github.com/harness/harness-core.git");
    writeValidationDetails.setBranch("develop");
    writeValidationDetails.setPath("/harness-services/");

    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();

    SourcePrincipalContextBuilder.setSourcePrincipal(new UserPrincipal("name", "email", "username", "accountId"));

    when(harnessToGitPushInfoService.createFile(any()))
        .thenReturn(
            CreateFileResponse.newBuilder()
                .setStatusCode(300)
                .setError(ErrorDetails.newBuilder().setErrorMessage("A file with this name already exists").build())
                .build());
    when(harnessToGitPushInfoService.updateFile(any()))
        .thenReturn(UpdateFileResponse.newBuilder()
                        .setStatusCode(300)
                        .setError(ErrorDetails.newBuilder().setErrorMessage("Not Found").build())
                        .build());

    githubIntegrationOps.validateWritePermission(TEST_ACCOUNT_IDENTIFIER, writeValidationDetails, connectorInfoDTO);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteValidationErrorScenario4() {
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository("https://github.com/harness/harness-core.git");
    writeValidationDetails.setBranch("develop");
    writeValidationDetails.setPath("/harness-services/");

    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();

    SourcePrincipalContextBuilder.setSourcePrincipal(new UserPrincipal("name", "email", "username", "accountId"));

    when(harnessToGitPushInfoService.createFile(any()))
        .thenReturn(CreateFileResponse.newBuilder()
                        .setStatusCode(300)
                        .setError(ErrorDetails.newBuilder().setErrorMessage("Not Found").build())
                        .build());

    githubIntegrationOps.validateWritePermission(TEST_ACCOUNT_IDENTIFIER, writeValidationDetails, connectorInfoDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteValidation() {
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository("https://github.com/harness/harness-core.git");
    writeValidationDetails.setBranch("develop");
    writeValidationDetails.setPath("/harness-services/");

    ConnectorDTO githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = githubConnectorDtoRepoToken.getConnectorInfo();

    SourcePrincipalContextBuilder.setSourcePrincipal(new UserPrincipal("name", "email", "username", "accountId"));

    when(harnessToGitPushInfoService.createFile(any()))
        .thenReturn(CreateFileResponse.newBuilder().setStatusCode(200).build());

    githubIntegrationOps.validateWritePermission(TEST_ACCOUNT_IDENTIFIER, writeValidationDetails, connectorInfoDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthenticationDetailsForDelegateTask() {
    GitIntegrationGithubAppAuth auth = new GitIntegrationGithubAppAuth();
    auth.setApplicationId("applicationId");
    auth.setInstallationId("installationId");
    auth.setPrivateKeySecretIdentifier("privateKeySecretIdentifier");
    GithubIntegrationEntity githubIntegrationEntity =
        GithubIntegrationEntity.builder().host("github.com").auth(auth).build();
    DecryptableEntity decryptableEntity =
        githubIntegrationOps.getAuthenticationDetailsForDelegateTask(githubIntegrationEntity, new ArrayList<>());
    assertNotNull(decryptableEntity);
    assertEquals(GithubAppDTO.class, decryptableEntity.getClass());
    GithubAppDTO githubAppDTO = (GithubAppDTO) decryptableEntity;
    assertEquals("applicationId", githubAppDTO.getApplicationId());
    assertEquals("installationId", githubAppDTO.getInstallationId());
    assertEquals("privateKeySecretIdentifier", githubAppDTO.getPrivateKeyRef().getIdentifier());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthenticationDetailsForDelegateTaskTokenAuth() {
    GitIntegrationTokenAuth auth = new GitIntegrationTokenAuth();
    GithubIntegrationEntity githubIntegrationEntity =
        GithubIntegrationEntity.builder().host("github.com").auth(auth).build();
    DecryptableEntity decryptableEntity =
        githubIntegrationOps.getAuthenticationDetailsForDelegateTask(githubIntegrationEntity, new ArrayList<>());
    assertNull(decryptableEntity);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthForDelegateRequestGithubAppInstallationIdAsSecret() {
    GitIntegrationGithubAppAuth auth = new GitIntegrationGithubAppAuth();
    auth.setApplicationIdSecretIdentifier("appIdSecret");
    auth.setInstallationIdSecretIdentifier("installationIdSecret");
    auth.setPrivateKeySecretIdentifier("privateKeySecret");
    GithubIntegrationEntity githubIntegrationEntity = GithubIntegrationEntity.builder()
                                                          .host("github.com")
                                                          .authMode(GitIntegrationEntity.AuthMode.GITHUB_APP)
                                                          .auth(auth)
                                                          .build();

    DecryptableEntity decryptableEntity = githubIntegrationOps.getAuthForDelegateRequest(githubIntegrationEntity);
    assertNotNull(decryptableEntity);
    assertEquals(GithubAppDTO.class, decryptableEntity.getClass());
    GithubAppDTO githubAppDTO = (GithubAppDTO) decryptableEntity;
    assertEquals("installationIdSecret", githubAppDTO.getInstallationIdRef().getIdentifier());
    assertEquals("appIdSecret", githubAppDTO.getApplicationIdRef().getIdentifier());
    assertEquals("privateKeySecret", githubAppDTO.getPrivateKeyRef().getIdentifier());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testGetFileContent_withServicePrincipal_sendsServicePrincipalOnRequest() {
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    when(harnessToGitPushInfoService.getFile(any()))
        .thenReturn(GetFileResponse.newBuilder().setStatusCode(200).setFileContent("openapi: 3.0.0").build());

    String content = githubIntegrationOps.getFileContent(
        Scope.of(TEST_ACCOUNT_IDENTIFIER, null, null), "github-connector", "org/repo", "main", "openapi.yaml");

    assertEquals("openapi: 3.0.0", content);
    ArgumentCaptor<GetFileRequest> requestCaptor = ArgumentCaptor.forClass(GetFileRequest.class);
    verify(harnessToGitPushInfoService).getFile(requestCaptor.capture());
    assertTrue(requestCaptor.getValue().getPrincipal().hasServicePrincipal());
    assertEquals(AuthorizationServiceHeader.IDP_SERVICE.getServiceId(),
        requestCaptor.getValue().getPrincipal().getServicePrincipal().getName());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
