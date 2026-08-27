/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.idp.common.Constants.INTEGRATIONS_AZURE_PERSONAL_ACCESS_TOKEN;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.azureRepoConnectorDTORepoToken;
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
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.task.idp.gitintegration.response.GitIntegrationReadValidationResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.AzureIntegrationEntity;
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
public class AzureIntegrationOpsImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  AutoCloseable openMocks;

  @InjectMocks AzureIntegrationOpsImpl azureIntegrationOps;

  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DecryptionHelper decryptionHelper;
  @Mock AzureRepoService azureRepoService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareTokenAuthInvalidUrl() {
    ConnectorDTO azureRepoConnectorDTORepoToken = azureRepoConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = azureRepoConnectorDTORepoToken.getConnectorInfo();
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();
    azureRepoConnectorDTO.setExecuteOnDelegate(false);
    azureRepoConnectorDTO.setUrl("invalid^");
    connectorInfoDTO.setConnectorConfig(azureRepoConnectorDTO);
    azureRepoConnectorDTORepoToken.setConnectorInfo(connectorInfoDTO);

    azureIntegrationOps.prepare(azureRepoConnectorDTORepoToken.getConnectorInfo());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareTokenAuth() {
    ConnectorDTO azureRepoConnectorDTORepoToken = azureRepoConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = azureRepoConnectorDTORepoToken.getConnectorInfo();
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();
    azureRepoConnectorDTO.setExecuteOnDelegate(false);
    connectorInfoDTO.setConnectorConfig(azureRepoConnectorDTO);
    azureRepoConnectorDTORepoToken.setConnectorInfo(connectorInfoDTO);

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    AzureRepoUsernameTokenDTO azureRepoUsernameTokenDTO =
        AzureRepoUsernameTokenDTO.builder().tokenRef(SecretRefData.builder().identifier("secret").build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(azureRepoUsernameTokenDTO);

    AzureIntegrationEntity azureIntegrationEntity =
        azureIntegrationOps.prepare(azureRepoConnectorDTORepoToken.getConnectorInfo());
    assertEquals(Constants.IDP_PREFIX + azureRepoConnectorDTORepoToken.getConnectorInfo().getIdentifier(),
        azureIntegrationEntity.getIdentifier());
    assertEquals(IntegrationEntity.Integration.GIT, azureIntegrationEntity.getIntegration());
    assertEquals(IntegrationEntity.ParentType.AZURE, azureIntegrationEntity.getParentType());
    assertEquals(azureRepoConnectorDTORepoToken.getConnectorInfo().getIdentifier(),
        azureIntegrationEntity.getConnectorIdentifier());
    assertEquals("dev.azure.com", azureIntegrationEntity.getHost());
    assertEquals(GitIntegrationEntity.AuthMode.TOKEN, azureIntegrationEntity.getAuthMode());
    assertFalse(azureIntegrationEntity.isExecuteOnDelegate());
    assertEquals("automation-cdc", azureIntegrationEntity.getAdditionalIndexer());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareInvalidAuth() {
    ConnectorDTO azureRepoConnectorDTORepoToken = azureRepoConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = azureRepoConnectorDTORepoToken.getConnectorInfo();
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();
    AzureRepoAuthenticationDTO azureRepoAuthenticationDTO = azureRepoConnectorDTO.getAuthentication();
    azureRepoAuthenticationDTO.setAuthType(GitAuthType.SSH);
    azureRepoConnectorDTO.setAuthentication(azureRepoAuthenticationDTO);
    connectorInfoDTO.setConnectorConfig(azureRepoConnectorDTO);
    azureRepoConnectorDTORepoToken.setConnectorInfo(connectorInfoDTO);
    azureIntegrationOps.prepare(azureRepoConnectorDTORepoToken.getConnectorInfo());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetConnectorConfigDTO() {
    ConnectorDTO azureRepoConnectorDTORepoToken = azureRepoConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = azureRepoConnectorDTORepoToken.getConnectorInfo();
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();

    AzureRepoConnectorDTO azureRepoConnectorDTOFunc =
        azureIntegrationOps.getConnectorConfigDTO(azureRepoConnectorDTORepoToken.getConnectorInfo());
    assertEquals(azureRepoConnectorDTO, azureRepoConnectorDTOFunc);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndGetAuthMode() {
    ConnectorDTO azureRepoConnectorDTORepoToken = azureRepoConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = azureRepoConnectorDTORepoToken.getConnectorInfo();
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationEntity.AuthMode authMode = azureIntegrationOps.validateAndGetAuthMode(azureRepoConnectorDTO);
    assertEquals(GitIntegrationEntity.AuthMode.TOKEN, authMode);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthToken() {
    ConnectorDTO azureRepoConnectorDTORepoToken = azureRepoConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = azureRepoConnectorDTORepoToken.getConnectorInfo();
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    AzureRepoUsernameTokenDTO azureRepoUsernameTokenDTO =
        AzureRepoUsernameTokenDTO.builder().tokenRef(SecretRefData.builder().identifier("secret").build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(azureRepoUsernameTokenDTO);

    GitIntegrationAuth gitIntegrationAuth = azureIntegrationOps.getAuth(azureRepoConnectorDTO, TEST_ACCOUNT_IDENTIFIER);
    assert gitIntegrationAuth instanceof GitIntegrationTokenAuth;
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationConfigs() {
    AzureIntegrationEntity azureIntegrationEntity = AzureIntegrationEntity.builder().host("dev.azure.com").build();

    Map<String, String> integrationConfigs = azureIntegrationOps.getIntegrationConfigs(azureIntegrationEntity);

    assertEquals(0, integrationConfigs.size());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationSecretsToken() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    AzureIntegrationEntity azureIntegrationEntity = AzureIntegrationEntity.builder()
                                                        .host("dev.azure.com")
                                                        .authMode(GitIntegrationEntity.AuthMode.TOKEN)
                                                        .auth(gitIntegrationTokenAuth)
                                                        .organization("automation-cdc")
                                                        .build();

    Map<String, String> integrationSecrets = azureIntegrationOps.getIntegrationSecrets(azureIntegrationEntity);

    assertEquals(1, integrationSecrets.size());
    assertEquals("secret",
        integrationSecrets.get(INTEGRATIONS_AZURE_PERSONAL_ACCESS_TOKEN + "_"
            + "automation-cdc"));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationAppConfig() {
    ConnectorDTO azureRepoConnectorDTORepoToken = azureRepoConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = azureRepoConnectorDTORepoToken.getConnectorInfo();
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    AzureIntegrationEntity azureIntegrationEntity = AzureIntegrationEntity.builder()
                                                        .host("dev.azure.com")
                                                        .authMode(GitIntegrationEntity.AuthMode.TOKEN)
                                                        .auth(gitIntegrationTokenAuth)
                                                        .build();

    String integrationConfig =
        azureIntegrationOps.getIntegrationAppConfig(azureIntegrationEntity, azureRepoConnectorDTO);
    assertEquals("integrations:\n"
            + "  azure:\n"
            + "    - host: dev.azure.com\n"
            + "      credentials:\n"
            + "        - organizations:\n"
            + "            - automation-cdc\n"
            + "          personalAccessToken: ${AZURE_PERSONAL_ACCESS_TOKEN_automation-cdc}",
        integrationConfig);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermission() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    AzureIntegrationEntity azureIntegrationEntity =
        AzureIntegrationEntity.builder()
            .host("dev.azure.com")
            .authMode(GitIntegrationEntity.AuthMode.TOKEN)
            .auth(gitIntegrationTokenAuth)
            .organization("automation-cdc")
            .readPermissionValidation(
                GitIntegrationEntity.ReadPermissionValidation.builder()
                    .fileUrl("https://automation-cdc@dev.azure.com/automation-cdc/IDP/_git/IDPAUTO")
                    .build())
            .build();

    Map<String, String> integrationConfigs = azureIntegrationOps.getIntegrationConfigs(azureIntegrationEntity);
    Map<String, String> integrationSecrets = azureIntegrationOps.getIntegrationSecrets(azureIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_AZURE_PERSONAL_ACCESS_TOKEN + "_"
                 + "automation-cdc")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(azureRepoService.getRepository(any(), any(), any(), any(), any()))
        .thenReturn(new JSONObject().put("status", "success").put("error", ""));

    azureIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (AzureRepoConnectorDTO) azureRepoConnectorDTORepoToken().getConnectorInfo().getConnectorConfig(),
        azureIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("success", azureIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("", azureIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateReadPermissionTokenDelegate() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("secret");
    AzureIntegrationEntity azureIntegrationEntity =
        AzureIntegrationEntity.builder()
            .host("dev.azure.com")
            .authMode(GitIntegrationEntity.AuthMode.TOKEN)
            .auth(gitIntegrationTokenAuth)
            .organization("automation-cdc")
            .executeOnDelegate(true)
            .delegateSelectors(Set.of("delegate1"))
            .readPermissionValidation(
                GitIntegrationEntity.ReadPermissionValidation.builder()
                    .fileUrl("https://automation-cdc@dev.azure.com/automation-cdc/IDP/_git/IDPAUTO")
                    .build())
            .build();

    Map<String, String> integrationConfigs = azureIntegrationOps.getIntegrationConfigs(azureIntegrationEntity);
    Map<String, String> integrationSecrets = azureIntegrationOps.getIntegrationSecrets(azureIntegrationEntity);

    when(secretManagerClientService.getDecryptedSecretValue(TEST_ACCOUNT_IDENTIFIER, null, null,
             integrationSecrets.get(INTEGRATIONS_AZURE_PERSONAL_ACCESS_TOKEN + "_"
                 + "automation-cdc")))
        .thenReturn(DecryptedSecretValue.builder().decryptedValue("secret").build());
    when(delegateGrpcClientWrapper.executeSyncTaskV2(any()))
        .thenReturn(GitIntegrationReadValidationResponse.builder().code(200).status("success").error("").build());

    azureIntegrationOps.validateReadPermission(TEST_ACCOUNT_IDENTIFIER,
        (AzureRepoConnectorDTO) azureRepoConnectorDTORepoToken().getConnectorInfo().getConnectorConfig(),
        azureIntegrationEntity, integrationConfigs, integrationSecrets);
    assertEquals("success", azureIntegrationEntity.getReadPermissionValidation().getStatus());
    assertEquals("", azureIntegrationEntity.getReadPermissionValidation().getError());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetRepository() {
    ConnectorDTO azureRepoConnectorDTORepoToken = azureRepoConnectorDTORepoToken();
    ConnectorInfoDTO connectorInfoDTO = azureRepoConnectorDTORepoToken.getConnectorInfo();
    AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) connectorInfoDTO.getConnectorConfig();
    String repo = azureIntegrationOps.getRepository(
        azureRepoConnectorDTO, "https://automation-cdc@dev.azure.com/automation-cdc/IDP/_git/IDPAUTO");
    assertEquals("IDPAUTO", repo);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAlreadyExistErrorMessage() {
    AzureIntegrationEntity azureIntegrationEntity =
        AzureIntegrationEntity.builder().host("dev.azure.com").additionalIndexer("org1").build();
    String error = azureIntegrationOps.getAlreadyExistErrorMessage(azureIntegrationEntity);
    assertEquals("Azure Repo integration with host dev.azure.com and organization org1 already exists. ", error);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthenticationDetailsForDelegateTask() {
    GitIntegrationTokenAuth auth = new GitIntegrationTokenAuth();
    auth.setTokenSecretIdentifier("secret");
    AzureIntegrationEntity azureIntegrationEntity =
        AzureIntegrationEntity.builder().host("dev.azure.com").additionalIndexer("org1").auth(auth).build();
    DecryptableEntity decryptableEntity =
        azureIntegrationOps.getAuthenticationDetailsForDelegateTask(azureIntegrationEntity, new ArrayList<>());
    assertEquals(AzureRepoTokenSpecDTO.class, decryptableEntity.getClass());
    AzureRepoTokenSpecDTO azureRepoTokenSpecDTO = (AzureRepoTokenSpecDTO) decryptableEntity;
    assertEquals("secret", azureRepoTokenSpecDTO.getTokenRef().getIdentifier());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
