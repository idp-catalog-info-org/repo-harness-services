/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.idp.common.Constants.INTEGRATIONS_BITBUCKET_SERVER_PASSWORD;
import static io.harness.idp.common.Constants.INTEGRATIONS_BITBUCKET_SERVER_USERNAME;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.bitbucketServerRepoConnectorDTORepoPassword;
import static io.harness.rule.OwnerRule.MANAS_ASATI;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DecryptedSecretValue;
import io.harness.category.element.UnitTests;
import io.harness.cistatus.service.bitbucket.BitbucketConfig;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAccessTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.task.idp.gitintegration.response.GitIntegrationReadValidationResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationUsernamePasswordAuth;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketCloudIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketServerIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
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
public class BitbucketServerIntegrationOpsImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  AutoCloseable openMocks;

  @InjectMocks BitbucketServerIntegrationOpsImpl bitbucketServerIntegrationOps;

  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DecryptionHelper decryptionHelper;
  @Mock BitbucketService bitbucketService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareTokenAuth() {
    ConnectorDTO bitbucketServerRepoConnectorDTORepoPassword = bitbucketServerRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
    bitbucketConnectorDTO.setExecuteOnDelegate(false);
    connectorInfoDTO.setConnectorConfig(bitbucketConnectorDTO);
    bitbucketServerRepoConnectorDTORepoPassword.setConnectorInfo(connectorInfoDTO);

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO =
        BitbucketUsernamePasswordDTO.builder()
            .usernameRef(SecretRefData.builder().identifier("secret").build())
            .passwordRef(SecretRefData.builder().identifier("secret").build())
            .build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(bitbucketUsernamePasswordDTO);

    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity =
        bitbucketServerIntegrationOps.prepare(bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo());
    assertEquals(Constants.IDP_PREFIX + bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo().getIdentifier(),
        bitbucketServerIntegrationEntity.getIdentifier());
    assertEquals(IntegrationEntity.Integration.GIT, bitbucketServerIntegrationEntity.getIntegration());
    assertEquals(IntegrationEntity.ParentType.BITBUCKET_SERVER, bitbucketServerIntegrationEntity.getParentType());
    assertEquals(bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo().getIdentifier(),
        bitbucketServerIntegrationEntity.getConnectorIdentifier());
    assertEquals("bitbucket.dev.harness.io", bitbucketServerIntegrationEntity.getHost());
    assertEquals(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD, bitbucketServerIntegrationEntity.getAuthMode());
    assertFalse(bitbucketServerIntegrationEntity.isExecuteOnDelegate());
    assertEquals("bitbucket.dev.harness.io", bitbucketServerIntegrationEntity.getAdditionalIndexer());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareInvalidAuth() {
    ConnectorDTO bitbucketServerRepoConnectorDTORepoPassword = bitbucketServerRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
    BitbucketAuthenticationDTO bitbucketAuthenticationDTO = bitbucketConnectorDTO.getAuthentication();
    bitbucketAuthenticationDTO.setAuthType(GitAuthType.SSH);
    bitbucketConnectorDTO.setAuthentication(bitbucketAuthenticationDTO);
    connectorInfoDTO.setConnectorConfig(bitbucketConnectorDTO);
    bitbucketServerRepoConnectorDTORepoPassword.setConnectorInfo(connectorInfoDTO);
    bitbucketServerIntegrationOps.prepare(bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetConnectorConfigDTO() {
    ConnectorDTO bitbucketServerRepoConnectorDTORepoPassword = bitbucketServerRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();

    BitbucketConnectorDTO bitbucketConnectorDTOFunc = bitbucketServerIntegrationOps.getConnectorConfigDTO(
        bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo());
    assertEquals(bitbucketConnectorDTO, bitbucketConnectorDTOFunc);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndGetAuthMode() {
    ConnectorDTO bitbucketServerRepoConnectorDTORepoPassword = bitbucketServerRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationEntity.AuthMode authMode =
        bitbucketServerIntegrationOps.validateAndGetAuthMode(bitbucketConnectorDTO);
    assertEquals(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD, authMode);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthToken() {
    ConnectorDTO bitbucketServerRepoConnectorDTORepoPassword = bitbucketServerRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO =
        BitbucketUsernamePasswordDTO.builder()
            .usernameRef(SecretRefData.builder().identifier("secret").build())
            .passwordRef(SecretRefData.builder().identifier("secret").build())
            .build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(bitbucketUsernamePasswordDTO);

    GitIntegrationAuth gitIntegrationAuth =
        bitbucketServerIntegrationOps.getAuth(bitbucketConnectorDTO, TEST_ACCOUNT_IDENTIFIER);
    assert gitIntegrationAuth instanceof GitIntegrationUsernamePasswordAuth;
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationConfigs() {
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsername("username");
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity = BitbucketServerIntegrationEntity.builder()
                                                                            .host("bitbucket.dev.harness.io")
                                                                            .auth(gitIntegrationUsernamePasswordAuth)
                                                                            .build();

    Map<String, String> integrationConfigs =
        bitbucketServerIntegrationOps.getIntegrationConfigs(bitbucketServerIntegrationEntity);

    assertEquals(1, integrationConfigs.size());
    assertEquals("username",
        integrationConfigs.get(INTEGRATIONS_BITBUCKET_SERVER_USERNAME + "_"
            + "BITBUCKET_DEV_HARNESS_IO"));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationSecretsToken() {
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsernameSecretIdentifier("secret");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("secret");
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity = BitbucketServerIntegrationEntity.builder()
                                                                            .host("bitbucket.dev.harness.io")
                                                                            .auth(gitIntegrationUsernamePasswordAuth)
                                                                            .build();

    Map<String, String> integrationSecrets =
        bitbucketServerIntegrationOps.getIntegrationSecrets(bitbucketServerIntegrationEntity);

    assertEquals(2, integrationSecrets.size());
    assertEquals("secret",
        integrationSecrets.get(INTEGRATIONS_BITBUCKET_SERVER_USERNAME + "_"
            + "BITBUCKET_DEV_HARNESS_IO"));
    assertEquals("secret",
        integrationSecrets.get(INTEGRATIONS_BITBUCKET_SERVER_PASSWORD + "_"
            + "BITBUCKET_DEV_HARNESS_IO"));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationAppConfig() {
    ConnectorDTO bitbucketServerRepoConnectorDTORepoPassword = bitbucketServerRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsername("username");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("secret");
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity = BitbucketServerIntegrationEntity.builder()
                                                                            .host("bitbucket.dev.harness.io")
                                                                            .auth(gitIntegrationUsernamePasswordAuth)
                                                                            .build();

    String integrationConfig =
        bitbucketServerIntegrationOps.getIntegrationAppConfig(bitbucketServerIntegrationEntity, bitbucketConnectorDTO);
    assertEquals("integrations:\n"
            + "  bitbucketServer:\n"
            + "    - host: bitbucket.dev.harness.io\n"
            + "      apiBaseUrl: https://bitbucket.dev.harness.io/rest/api/1.0\n"
            + "      username: ${BITBUCKET_SERVER_USERNAME_BITBUCKET_DEV_HARNESS_IO}\n"
            + "      password: ${BITBUCKET_SERVER_PASSWORD_BITBUCKET_DEV_HARNESS_IO}",
        integrationConfig);
  }

  @Test
  @Owner(developers = MANAS_ASATI)
  @Category(UnitTests.class)
  public void testGetIntegrationAppConfigSshWithApiUrl() {
    // SSH Server connector behind a context path: apiUrl supplies the REST base subpath for ${API_BASE_URL}.
    BitbucketConnectorDTO bitbucketConnectorDTO =
        BitbucketConnectorDTO.builder()
            .url("ssh://git@bitbucket.dev.harness.io/har/idp.git")
            .connectionType(GitConnectionType.REPO)
            .authentication(BitbucketAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
            .apiAccess(BitbucketApiAccessDTO.builder()
                           .type(BitbucketApiAccessType.ACCESS_TOKEN)
                           .spec(BitbucketAccessTokenApiAccessDTO.builder()
                                     .tokenRef(SecretRefData.builder().identifier("tokenRef").build())
                                     .apiUrl("https://bitbucket.dev.harness.io/bitbucket")
                                     .build())
                           .build())
            .build();

    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsername("username");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("secret");
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity = BitbucketServerIntegrationEntity.builder()
                                                                            .host("bitbucket.dev.harness.io")
                                                                            .auth(gitIntegrationUsernamePasswordAuth)
                                                                            .build();

    String integrationConfig =
        bitbucketServerIntegrationOps.getIntegrationAppConfig(bitbucketServerIntegrationEntity, bitbucketConnectorDTO);
    assertEquals("integrations:\n"
            + "  bitbucketServer:\n"
            + "    - host: bitbucket.dev.harness.io\n"
            + "      apiBaseUrl: https://bitbucket.dev.harness.io/bitbucket/rest/api/1.0\n"
            + "      username: ${BITBUCKET_SERVER_USERNAME_BITBUCKET_DEV_HARNESS_IO}\n"
            + "      password: ${BITBUCKET_SERVER_PASSWORD_BITBUCKET_DEV_HARNESS_IO}",
        integrationConfig);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermission() {
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsername("username");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("secret");
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity =
        BitbucketServerIntegrationEntity.builder()
            .host("bitbucket.dev.harness.io")
            .auth(gitIntegrationUsernamePasswordAuth)
            .readPermissionValidation(GitIntegrationEntity.ReadPermissionValidation.builder()
                                          .fileUrl("https://bitbucket.dev.harness.io/scm/har/idp.git")
                                          .build())
            .build();

    Map<String, String> integrationConfigs =
        bitbucketServerIntegrationOps.getIntegrationConfigs(bitbucketServerIntegrationEntity);
    Map<String, String> integrationSecrets =
        bitbucketServerIntegrationOps.getIntegrationSecrets(bitbucketServerIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_BITBUCKET_SERVER_USERNAME + "_BITBUCKET_DEV_HARNESS_IO")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_BITBUCKET_SERVER_PASSWORD + "_BITBUCKET_DEV_HARNESS_IO")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(bitbucketService.getRepository(
             BitbucketConfig.builder().bitbucketUrl("https://bitbucket.dev.harness.io").build(), "username", "secret",
             "har", "idp.git"))
        .thenReturn(new JSONObject().put("status", "success").put("error", ""));

    bitbucketServerIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (BitbucketConnectorDTO) bitbucketServerRepoConnectorDTORepoPassword().getConnectorInfo().getConnectorConfig(),
        bitbucketServerIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("success", bitbucketServerIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("", bitbucketServerIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermissionTokenDelegate() {
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsernameSecretIdentifier("secret");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("secret");
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity =
        BitbucketServerIntegrationEntity.builder()
            .host("bitbucket.dev.harness.io")
            .authMode(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD)
            .auth(gitIntegrationUsernamePasswordAuth)
            .executeOnDelegate(true)
            .delegateSelectors(Set.of("delegate1"))
            .readPermissionValidation(GitIntegrationEntity.ReadPermissionValidation.builder()
                                          .fileUrl("https://bitbucket.dev.harness.io/scm/har/idp.git")
                                          .build())
            .build();

    Map<String, String> integrationConfigs =
        bitbucketServerIntegrationOps.getIntegrationConfigs(bitbucketServerIntegrationEntity);
    Map<String, String> integrationSecrets =
        bitbucketServerIntegrationOps.getIntegrationSecrets(bitbucketServerIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_BITBUCKET_SERVER_USERNAME + "_BITBUCKET_DEV_HARNESS_IO")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_BITBUCKET_SERVER_PASSWORD + "_BITBUCKET_DEV_HARNESS_IO")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GitIntegrationReadValidationResponse.builder().code(200).status("success").error("").build());

    bitbucketServerIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (BitbucketConnectorDTO) bitbucketServerRepoConnectorDTORepoPassword().getConnectorInfo().getConnectorConfig(),
        bitbucketServerIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("success", bitbucketServerIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("", bitbucketServerIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetRepository() {
    ConnectorDTO bitbucketServerRepoConnectorDTORepoPassword = bitbucketServerRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketServerRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
    String repo = bitbucketServerIntegrationOps.getRepository(
        bitbucketConnectorDTO, "https://bitbucket.dev.harness.io/scm/har/idp.git");
    assertEquals("idp", repo);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAlreadyExistErrorMessage() {
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity =
        BitbucketServerIntegrationEntity.builder().host("bitbucket.dev.harness.io").build();
    String error = bitbucketServerIntegrationOps.getAlreadyExistErrorMessage(bitbucketServerIntegrationEntity);
    assertEquals("Bitbucket integration with host bitbucket.dev.harness.io already exists. ", error);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthenticationDetailsForDelegateTask() {
    GitIntegrationUsernamePasswordAuth auth = new GitIntegrationUsernamePasswordAuth();
    auth.setUsername("username");
    auth.setPasswordSecretIdentifier("password");
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        BitbucketCloudIntegrationEntity.builder().host("bitbucket.dev.harness.io").auth(auth).build();
    DecryptableEntity decryptableEntity = bitbucketServerIntegrationOps.getAuthenticationDetailsForDelegateTask(
        bitbucketCloudIntegrationEntity, new ArrayList<>());
    assertEquals(BitbucketUsernamePasswordDTO.class, decryptableEntity.getClass());
    BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO = (BitbucketUsernamePasswordDTO) decryptableEntity;
    assertEquals("username", bitbucketUsernamePasswordDTO.getUsername());
    assertEquals("password", bitbucketUsernamePasswordDTO.getPasswordRef().getIdentifier());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
