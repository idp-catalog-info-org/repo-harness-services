/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.idp.common.Constants.INTEGRATIONS_BITBUCKET_CLOUD_PASSWORD;
import static io.harness.idp.common.Constants.INTEGRATIONS_BITBUCKET_CLOUD_USERNAME;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.bitbucketCloudRepoConnectorDTORepoPassword;
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
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.task.idp.gitintegration.response.GitIntegrationReadValidationResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationUsernamePasswordAuth;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketCloudIntegrationEntity;
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
public class BitbucketCloudIntegrationOpsImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  AutoCloseable openMocks;

  @InjectMocks BitbucketCloudIntegrationOpsImpl bitbucketCloudIntegrationOps;

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
    ConnectorDTO bitbucketCloudRepoConnectorDTORepoPassword = bitbucketCloudRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
    bitbucketConnectorDTO.setExecuteOnDelegate(false);
    connectorInfoDTO.setConnectorConfig(bitbucketConnectorDTO);
    bitbucketCloudRepoConnectorDTORepoPassword.setConnectorInfo(connectorInfoDTO);

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO =
        BitbucketUsernamePasswordDTO.builder()
            .usernameRef(SecretRefData.builder().identifier("secret").build())
            .passwordRef(SecretRefData.builder().identifier("secret").build())
            .build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(bitbucketUsernamePasswordDTO);

    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        bitbucketCloudIntegrationOps.prepare(bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo());
    assertEquals(Constants.IDP_PREFIX + bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo().getIdentifier(),
        bitbucketCloudIntegrationEntity.getIdentifier());
    assertEquals(IntegrationEntity.Integration.GIT, bitbucketCloudIntegrationEntity.getIntegration());
    assertEquals(IntegrationEntity.ParentType.BITBUCKET_CLOUD, bitbucketCloudIntegrationEntity.getParentType());
    assertEquals(bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo().getIdentifier(),
        bitbucketCloudIntegrationEntity.getConnectorIdentifier());
    assertEquals("bitbucket.org", bitbucketCloudIntegrationEntity.getHost());
    assertEquals(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD, bitbucketCloudIntegrationEntity.getAuthMode());
    assertFalse(bitbucketCloudIntegrationEntity.isExecuteOnDelegate());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareInvalidAuth() {
    ConnectorDTO bitbucketCloudRepoConnectorDTORepoPassword = bitbucketCloudRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
    BitbucketAuthenticationDTO bitbucketAuthenticationDTO = bitbucketConnectorDTO.getAuthentication();
    bitbucketAuthenticationDTO.setAuthType(GitAuthType.SSH);
    bitbucketConnectorDTO.setAuthentication(bitbucketAuthenticationDTO);
    connectorInfoDTO.setConnectorConfig(bitbucketConnectorDTO);
    bitbucketCloudRepoConnectorDTORepoPassword.setConnectorInfo(connectorInfoDTO);
    bitbucketCloudIntegrationOps.prepare(bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetConnectorConfigDTO() {
    ConnectorDTO bitbucketCloudRepoConnectorDTORepoPassword = bitbucketCloudRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();

    BitbucketConnectorDTO bitbucketConnectorDTOFunc = bitbucketCloudIntegrationOps.getConnectorConfigDTO(
        bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo());
    assertEquals(bitbucketConnectorDTO, bitbucketConnectorDTOFunc);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndGetAuthMode() {
    ConnectorDTO bitbucketCloudRepoConnectorDTORepoPassword = bitbucketCloudRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationEntity.AuthMode authMode = bitbucketCloudIntegrationOps.validateAndGetAuthMode(bitbucketConnectorDTO);
    assertEquals(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD, authMode);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthToken() {
    ConnectorDTO bitbucketCloudRepoConnectorDTORepoPassword = bitbucketCloudRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    BitbucketUsernamePasswordDTO bitbucketUsernamePasswordDTO =
        BitbucketUsernamePasswordDTO.builder()
            .usernameRef(SecretRefData.builder().identifier("secret").build())
            .passwordRef(SecretRefData.builder().identifier("secret").build())
            .build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(bitbucketUsernamePasswordDTO);

    GitIntegrationAuth gitIntegrationAuth =
        bitbucketCloudIntegrationOps.getAuth(bitbucketConnectorDTO, TEST_ACCOUNT_IDENTIFIER);
    assert gitIntegrationAuth instanceof GitIntegrationUsernamePasswordAuth;
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationConfigs() {
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsername("username");
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity = BitbucketCloudIntegrationEntity.builder()
                                                                          .host("bitbucket.org")
                                                                          .auth(gitIntegrationUsernamePasswordAuth)
                                                                          .build();

    Map<String, String> integrationConfigs =
        bitbucketCloudIntegrationOps.getIntegrationConfigs(bitbucketCloudIntegrationEntity);

    assertEquals(1, integrationConfigs.size());
    assertEquals("username", integrationConfigs.get(INTEGRATIONS_BITBUCKET_CLOUD_USERNAME));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationSecretsToken() {
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsernameSecretIdentifier("secret");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("secret");
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity = BitbucketCloudIntegrationEntity.builder()
                                                                          .host("bitbucket.org")
                                                                          .auth(gitIntegrationUsernamePasswordAuth)
                                                                          .build();

    Map<String, String> integrationSecrets =
        bitbucketCloudIntegrationOps.getIntegrationSecrets(bitbucketCloudIntegrationEntity);

    assertEquals(2, integrationSecrets.size());
    assertEquals("secret", integrationSecrets.get(INTEGRATIONS_BITBUCKET_CLOUD_USERNAME));
    assertEquals("secret", integrationSecrets.get(INTEGRATIONS_BITBUCKET_CLOUD_PASSWORD));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationAppConfig() {
    ConnectorDTO bitbucketCloudRepoConnectorDTORepoPassword = bitbucketCloudRepoConnectorDTORepoPassword();
    ConnectorInfoDTO connectorInfoDTO = bitbucketCloudRepoConnectorDTORepoPassword.getConnectorInfo();
    BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsername("username");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("secret");
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity = BitbucketCloudIntegrationEntity.builder()
                                                                          .host("bitbucket.org")
                                                                          .auth(gitIntegrationUsernamePasswordAuth)
                                                                          .build();

    String integrationConfig =
        bitbucketCloudIntegrationOps.getIntegrationAppConfig(bitbucketCloudIntegrationEntity, bitbucketConnectorDTO);
    assertEquals("integrations:\n"
            + "  bitbucketCloud:\n"
            + "    - username: ${BITBUCKET_CLOUD_USERNAME}\n"
            + "      appPassword: ${BITBUCKET_CLOUD_PASSWORD}",
        integrationConfig);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermission() {
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsernameSecretIdentifier("secret");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("secret");
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        BitbucketCloudIntegrationEntity.builder()
            .host("bitbucket.org")
            .authMode(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD)
            .auth(gitIntegrationUsernamePasswordAuth)
            .readPermissionValidation(GitIntegrationEntity.ReadPermissionValidation.builder()
                                          .fileUrl("https://s_sathish@bitbucket.org/s_sathish/sathish-test.git")
                                          .build())
            .build();

    Map<String, String> integrationConfigs =
        bitbucketCloudIntegrationOps.getIntegrationConfigs(bitbucketCloudIntegrationEntity);
    Map<String, String> integrationSecrets =
        bitbucketCloudIntegrationOps.getIntegrationSecrets(bitbucketCloudIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(
             TEST_ACCOUNT_IDENTIFIER, null, null, integrationSecrets.get(INTEGRATIONS_BITBUCKET_CLOUD_USERNAME)))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(secretManagerClientService.getDecryptedSecretValue(
             TEST_ACCOUNT_IDENTIFIER, null, null, integrationSecrets.get(INTEGRATIONS_BITBUCKET_CLOUD_PASSWORD)))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(bitbucketService.getRepository(BitbucketConfig.builder().bitbucketUrl("https://api.bitbucket.org").build(),
             "secret", "secret", "s_sathish", "sathish-test.git"))
        .thenReturn(new JSONObject().put("status", "success").put("error", ""));

    bitbucketCloudIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (BitbucketConnectorDTO) bitbucketCloudRepoConnectorDTORepoPassword().getConnectorInfo().getConnectorConfig(),
        bitbucketCloudIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("success", bitbucketCloudIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("", bitbucketCloudIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermissionTokenDelegate() {
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsernameSecretIdentifier("secret");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("secret");
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        BitbucketCloudIntegrationEntity.builder()
            .host("bitbucket.org")
            .authMode(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD)
            .auth(gitIntegrationUsernamePasswordAuth)
            .executeOnDelegate(true)
            .delegateSelectors(Set.of("delegate1"))
            .readPermissionValidation(GitIntegrationEntity.ReadPermissionValidation.builder()
                                          .fileUrl("https://s_sathish@bitbucket.org/s_sathish/sathish-test.git")
                                          .build())
            .build();

    Map<String, String> integrationConfigs =
        bitbucketCloudIntegrationOps.getIntegrationConfigs(bitbucketCloudIntegrationEntity);
    Map<String, String> integrationSecrets =
        bitbucketCloudIntegrationOps.getIntegrationSecrets(bitbucketCloudIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(
             TEST_ACCOUNT_IDENTIFIER, null, null, integrationSecrets.get(INTEGRATIONS_BITBUCKET_CLOUD_USERNAME)))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(secretManagerClientService.getDecryptedSecretValue(
             TEST_ACCOUNT_IDENTIFIER, null, null, integrationSecrets.get(INTEGRATIONS_BITBUCKET_CLOUD_PASSWORD)))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GitIntegrationReadValidationResponse.builder().code(200).status("success").error("").build());

    bitbucketCloudIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (BitbucketConnectorDTO) bitbucketCloudRepoConnectorDTORepoPassword().getConnectorInfo().getConnectorConfig(),
        bitbucketCloudIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("success", bitbucketCloudIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("", bitbucketCloudIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAlreadyExistErrorMessage() {
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        BitbucketCloudIntegrationEntity.builder().host("bitbucket.org").build();
    String error = bitbucketCloudIntegrationOps.getAlreadyExistErrorMessage(bitbucketCloudIntegrationEntity);
    assertEquals("Bitbucket integration with host bitbucket.org already exists. ", error);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthenticationDetailsForDelegateTask() {
    GitIntegrationUsernamePasswordAuth auth = new GitIntegrationUsernamePasswordAuth();
    auth.setUsername("username");
    auth.setPasswordSecretIdentifier("password");
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        BitbucketCloudIntegrationEntity.builder().host("bitbucket.org").auth(auth).build();
    DecryptableEntity decryptableEntity = bitbucketCloudIntegrationOps.getAuthenticationDetailsForDelegateTask(
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
