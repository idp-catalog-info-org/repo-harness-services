/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.idp.common.Constants.INTEGRATIONS_GITLAB_TOKEN;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_READ_VALIDATION_FILE_URL;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.gitlabConnectorDTORepoToken;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptedSecretValue;
import io.harness.category.element.UnitTests;
import io.harness.cistatus.service.gitlab.GitlabConfig;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.delegate.task.idp.gitintegration.response.GitIntegrationReadValidationResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitlabIntegrationEntity;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GitlabIntegrationOpsImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  AutoCloseable openMocks;

  @InjectMocks GitlabIntegrationOpsImpl gitlabIntegrationOps;

  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DecryptionHelper decryptionHelper;
  @Mock GitlabService gitlabService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareTokenAuth() {
    ConnectorDTO gitlabConnectorDTORepoToken = gitlabConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = gitlabConnectorDTORepoToken.getConnectorInfo();
    GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();
    gitlabConnectorDTO.setExecuteOnDelegate(false);
    connectorInfoDTO.setConnectorConfig(gitlabConnectorDTO);
    gitlabConnectorDTORepoToken.setConnectorInfo(connectorInfoDTO);

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    GitlabUsernameTokenDTO gitlabUsernameTokenDTO =
        GitlabUsernameTokenDTO.builder().tokenRef(SecretRefData.builder().identifier("secret").build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(gitlabUsernameTokenDTO);

    GitlabIntegrationEntity gitlabIntegrationEntity =
        gitlabIntegrationOps.prepare(gitlabConnectorDTORepoToken.getConnectorInfo());
    assertEquals(Constants.IDP_PREFIX + gitlabConnectorDTORepoToken.getConnectorInfo().getIdentifier(),
        gitlabIntegrationEntity.getIdentifier());
    assertEquals(IntegrationEntity.Integration.GIT, gitlabIntegrationEntity.getIntegration());
    assertEquals(IntegrationEntity.ParentType.GITLAB, gitlabIntegrationEntity.getParentType());
    assertEquals(gitlabConnectorDTORepoToken.getConnectorInfo().getIdentifier(),
        gitlabIntegrationEntity.getConnectorIdentifier());
    assertEquals("gitlab.com", gitlabIntegrationEntity.getHost());
    assertEquals(GitIntegrationEntity.AuthMode.TOKEN, gitlabIntegrationEntity.getAuthMode());
    assertFalse(gitlabIntegrationEntity.isExecuteOnDelegate());
    assertEquals("gitlab.com", gitlabIntegrationEntity.getAdditionalIndexer());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareInvalidAuth() {
    ConnectorDTO gitlabConnectorDTORepoToken = gitlabConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = gitlabConnectorDTORepoToken.getConnectorInfo();
    GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GitlabAuthenticationDTO gitlabAuthenticationDTO = gitlabConnectorDTO.getAuthentication();
    gitlabAuthenticationDTO.setAuthType(GitAuthType.SSH);
    gitlabConnectorDTO.setAuthentication(gitlabAuthenticationDTO);
    connectorInfoDTO.setConnectorConfig(gitlabConnectorDTO);
    gitlabConnectorDTORepoToken.setConnectorInfo(connectorInfoDTO);
    gitlabIntegrationOps.prepare(gitlabConnectorDTORepoToken.getConnectorInfo());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetConnectorConfigDTO() {
    ConnectorDTO gitlabConnectorDTORepoToken = gitlabConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = gitlabConnectorDTORepoToken.getConnectorInfo();
    GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitlabConnectorDTO gitlabConnectorDTOFunc =
        gitlabIntegrationOps.getConnectorConfigDTO(gitlabConnectorDTORepoToken.getConnectorInfo());
    assertEquals(gitlabConnectorDTO, gitlabConnectorDTOFunc);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndGetAuthMode() {
    ConnectorDTO gitlabConnectorDTORepoToken = gitlabConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = gitlabConnectorDTORepoToken.getConnectorInfo();
    GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationEntity.AuthMode authMode = gitlabIntegrationOps.validateAndGetAuthMode(gitlabConnectorDTO);
    assertEquals(GitIntegrationEntity.AuthMode.TOKEN, authMode);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndGetAuthModeInvalid() {
    ConnectorDTO gitlabConnectorDTORepoToken = gitlabConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = gitlabConnectorDTORepoToken.getConnectorInfo();
    GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();
    GitlabAuthenticationDTO gitlabAuthenticationDTO = gitlabConnectorDTO.getAuthentication();
    GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
        (GitlabHttpCredentialsDTO) gitlabAuthenticationDTO.getCredentials();
    gitlabHttpCredentialsDTO.setType(GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD);
    gitlabAuthenticationDTO.setCredentials(gitlabHttpCredentialsDTO);
    gitlabConnectorDTO.setAuthentication(gitlabAuthenticationDTO);

    gitlabIntegrationOps.validateAndGetAuthMode(gitlabConnectorDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthToken() {
    ConnectorDTO gitlabConnectorDTORepoToken = gitlabConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = gitlabConnectorDTORepoToken.getConnectorInfo();
    GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    GitlabUsernameTokenDTO gitlabUsernameTokenDTO =
        GitlabUsernameTokenDTO.builder().tokenRef(SecretRefData.builder().identifier("secret").build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(gitlabUsernameTokenDTO);

    GitIntegrationAuth gitIntegrationAuth = gitlabIntegrationOps.getAuth(gitlabConnectorDTO, TEST_ACCOUNT_IDENTIFIER);
    assert gitIntegrationAuth instanceof GitIntegrationTokenAuth;
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationConfigs() {
    GitlabIntegrationEntity gitlabIntegrationEntity = GitlabIntegrationEntity.builder().host("gitlab.com").build();

    Map<String, String> integrationConfigs = gitlabIntegrationOps.getIntegrationConfigs(gitlabIntegrationEntity);

    assertEquals(0, integrationConfigs.size());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationSecretsToken() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    GitlabIntegrationEntity gitlabIntegrationEntity = GitlabIntegrationEntity.builder()
                                                          .host("gitlab.com")
                                                          .authMode(GitIntegrationEntity.AuthMode.TOKEN)
                                                          .auth(gitIntegrationTokenAuth)
                                                          .build();

    Map<String, String> integrationSecrets = gitlabIntegrationOps.getIntegrationSecrets(gitlabIntegrationEntity);

    assertEquals(1, integrationSecrets.size());
    assertEquals("secret",
        integrationSecrets.get(INTEGRATIONS_GITLAB_TOKEN + "_"
            + "GITLAB_COM"));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationAppConfig() {
    ConnectorDTO gitlabConnectorDTORepoToken = gitlabConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = gitlabConnectorDTORepoToken.getConnectorInfo();
    GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    GitlabIntegrationEntity gitlabIntegrationEntity = GitlabIntegrationEntity.builder()
                                                          .host("gitlab.com")
                                                          .authMode(GitIntegrationEntity.AuthMode.TOKEN)
                                                          .auth(gitIntegrationTokenAuth)
                                                          .build();

    String integrationConfig =
        gitlabIntegrationOps.getIntegrationAppConfig(gitlabIntegrationEntity, gitlabConnectorDTO);
    assertEquals("integrations:\n"
            + "  gitlab:\n"
            + "    - host: gitlab.com\n"
            + "      apiBaseUrl: https://gitlab.com/api/v4\n"
            + "      token: ${GITLAB_TOKEN_GITLAB_COM}",
        integrationConfig);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermission() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    GitlabIntegrationEntity gitlabIntegrationEntity =
        GitlabIntegrationEntity.builder()
            .host("gitlab.com")
            .authMode(GitIntegrationEntity.AuthMode.TOKEN)
            .auth(gitIntegrationTokenAuth)
            .readPermissionValidation(
                GitIntegrationEntity.ReadPermissionValidation.builder().fileUrl(TEST_READ_VALIDATION_FILE_URL).build())
            .build();

    Map<String, String> integrationConfigs = gitlabIntegrationOps.getIntegrationConfigs(gitlabIntegrationEntity);
    Map<String, String> integrationSecrets = gitlabIntegrationOps.getIntegrationSecrets(gitlabIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITLAB_TOKEN + "_"
                 + "GITLAB_COM")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(gitlabService.getSingleProjectForUser(GitlabConfig.builder()
                                                   .gitlabUrl("https://" + gitlabIntegrationEntity.getHost())
                                                   .personalAccessToken("secret")
                                                   .build(),
             "abc/def"))
        .thenReturn(new JSONObject().put("status", "success").put("error", ""));

    gitlabIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (GitlabConnectorDTO) gitlabConnectorDTORepoToken().getConnectorInfo().getConnectorConfig(),
        gitlabIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("success", gitlabIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("", gitlabIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermissionTokenDelegate() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    GitlabIntegrationEntity gitlabIntegrationEntity =
        GitlabIntegrationEntity.builder()
            .host("gitlab.com")
            .authMode(GitIntegrationEntity.AuthMode.TOKEN)
            .auth(gitIntegrationTokenAuth)
            .executeOnDelegate(true)
            .delegateSelectors(Set.of("delegate1"))
            .readPermissionValidation(
                GitIntegrationEntity.ReadPermissionValidation.builder().fileUrl(TEST_READ_VALIDATION_FILE_URL).build())
            .build();

    Map<String, String> integrationConfigs = gitlabIntegrationOps.getIntegrationConfigs(gitlabIntegrationEntity);
    Map<String, String> integrationSecrets = gitlabIntegrationOps.getIntegrationSecrets(gitlabIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_GITLAB_TOKEN + "_"
                 + "GITLAB_COM")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GitIntegrationReadValidationResponse.builder().code(200).status("success").error("").build());

    gitlabIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (GitlabConnectorDTO) gitlabConnectorDTORepoToken().getConnectorInfo().getConnectorConfig(),
        gitlabIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("success", gitlabIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("", gitlabIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetRepository() {
    ConnectorDTO gitlabConnectorDTORepoToken = gitlabConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = gitlabConnectorDTORepoToken.getConnectorInfo();
    GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorInfoDTO.getConnectorConfig();
    String repo = gitlabIntegrationOps.getRepository(gitlabConnectorDTO, "https://gitlab.com/harness/harness-core.git");
    assertEquals("harness-core", repo);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAlreadyExistErrorMessage() {
    GitlabIntegrationEntity gitlabIntegrationEntity = GitlabIntegrationEntity.builder().host("gitlab.com").build();
    String error = gitlabIntegrationOps.getAlreadyExistErrorMessage(gitlabIntegrationEntity);
    assertEquals("GitLab integration with host gitlab.com already exists. ", error);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
