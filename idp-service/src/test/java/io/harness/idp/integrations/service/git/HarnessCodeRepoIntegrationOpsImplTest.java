/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.idp.common.Constants.INTEGRATIONS_HARNESS_CODE_REPO_TOKEN;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.harnessCodeRepoConnectorDto;
import static io.harness.idp.integrations.service.git.GitIntegrationServiceImplTest.TEST_VANITY_URL;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.response.GitFileResponse;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.HarnessAuthenticationDTO;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.harness.HarnessUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.integrations.beans.git.GitIntegrationAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationManagedTokenAuth;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.HarnessCodeRepoIntegrationEntity;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.service.ScmServiceClient;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;
import io.harness.utils.ConnectorUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class HarnessCodeRepoIntegrationOpsImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  AutoCloseable openMocks;

  @InjectMocks HarnessCodeRepoIntegrationOpsImpl harnessCodeRepoIntegrationOps;

  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DecryptionHelper decryptionHelper;
  @Mock ConnectorUtils connectorUtils;
  @Mock SecretDecryptor secretDecryptor;
  @Mock SCMGrpc.SCMBlockingStub scmBlockingStub;
  @Mock ScmServiceClient scmServiceClient;
  @Mock IdpCommonService idpCommonService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareTokenAuth() {
    ConnectorDTO harnessCodeRepoConnectorDto = harnessCodeRepoConnectorDto();
    ConnectorInfoDTO connectorInfoDTO = harnessCodeRepoConnectorDto.getConnectorInfo();
    HarnessConnectorDTO harnessConnectorDTO = (HarnessConnectorDTO) connectorInfoDTO.getConnectorConfig();
    harnessConnectorDTO.setExecuteOnDelegate(false);
    connectorInfoDTO.setConnectorConfig(harnessConnectorDTO);
    harnessCodeRepoConnectorDto.setConnectorInfo(connectorInfoDTO);
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(AccountDTO.builder().identifier(TEST_ACCOUNT_IDENTIFIER).subdomainURL(TEST_VANITY_URL).build());
    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    HarnessUsernameTokenDTO harnessUsernameTokenDTO =
        HarnessUsernameTokenDTO.builder().tokenRef(SecretRefData.builder().identifier("secret").build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(harnessUsernameTokenDTO);

    HarnessCodeRepoIntegrationEntity harnessCodeRepoIntegrationEntity =
        harnessCodeRepoIntegrationOps.prepare(harnessCodeRepoConnectorDto.getConnectorInfo());
    assertEquals(harnessCodeRepoConnectorDto.getConnectorInfo().getIdentifier(),
        harnessCodeRepoIntegrationEntity.getIdentifier());
    assertEquals(IntegrationEntity.Integration.GIT, harnessCodeRepoIntegrationEntity.getIntegration());
    assertEquals(IntegrationEntity.ParentType.HARNESS_CODE_REPO, harnessCodeRepoIntegrationEntity.getParentType());
    assertEquals(harnessCodeRepoConnectorDto.getConnectorInfo().getIdentifier(),
        harnessCodeRepoIntegrationEntity.getConnectorIdentifier());
    assertEquals("vanity.harness.io", harnessCodeRepoIntegrationEntity.getHost());
    assertEquals(GitIntegrationEntity.AuthMode.MANAGED_TOKEN, harnessCodeRepoIntegrationEntity.getAuthMode());
    assertFalse(harnessCodeRepoIntegrationEntity.isExecuteOnDelegate());
    assertTrue(harnessCodeRepoIntegrationEntity.isManaged());
    assertEquals("vanity.harness.io", harnessCodeRepoIntegrationEntity.getAdditionalIndexer());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testPrepareInvalidAuth() {
    ConnectorDTO harnessCodeRepoConnectorDto = harnessCodeRepoConnectorDto();
    ConnectorInfoDTO connectorInfoDTO = harnessCodeRepoConnectorDto.getConnectorInfo();
    HarnessConnectorDTO harnessConnectorDTO = (HarnessConnectorDTO) connectorInfoDTO.getConnectorConfig();
    HarnessAuthenticationDTO harnessAuthenticationDTO = harnessConnectorDTO.getAuthentication();
    harnessAuthenticationDTO.setAuthType(GitAuthType.SSH);
    harnessConnectorDTO.setAuthentication(harnessAuthenticationDTO);
    connectorInfoDTO.setConnectorConfig(harnessConnectorDTO);
    harnessCodeRepoConnectorDto.setConnectorInfo(connectorInfoDTO);
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(AccountDTO.builder().identifier(TEST_ACCOUNT_IDENTIFIER).subdomainURL(TEST_VANITY_URL).build());
    harnessCodeRepoIntegrationOps.prepare(harnessCodeRepoConnectorDto.getConnectorInfo());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetConnectorConfigDTO() {
    ConnectorDTO harnessCodeRepoConnectorDto = harnessCodeRepoConnectorDto();
    ConnectorInfoDTO connectorInfoDTO = harnessCodeRepoConnectorDto.getConnectorInfo();
    HarnessConnectorDTO harnessConnectorDTO = (HarnessConnectorDTO) connectorInfoDTO.getConnectorConfig();

    HarnessConnectorDTO harnessConnectorDTOFunc =
        harnessCodeRepoIntegrationOps.getConnectorConfigDTO(harnessCodeRepoConnectorDto.getConnectorInfo());
    assertEquals(harnessConnectorDTO, harnessConnectorDTOFunc);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testValidateAndGetAuthMode() {
    ConnectorDTO harnessCodeRepoConnectorDto = harnessCodeRepoConnectorDto();
    ConnectorInfoDTO connectorInfoDTO = harnessCodeRepoConnectorDto.getConnectorInfo();
    HarnessConnectorDTO harnessConnectorDTO = (HarnessConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationEntity.AuthMode authMode = harnessCodeRepoIntegrationOps.validateAndGetAuthMode(harnessConnectorDTO);
    assertEquals(GitIntegrationEntity.AuthMode.MANAGED_TOKEN, authMode);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthToken() {
    ConnectorDTO harnessCodeRepoConnectorDto = harnessCodeRepoConnectorDto();
    ConnectorInfoDTO connectorInfoDTO = harnessCodeRepoConnectorDto.getConnectorInfo();
    HarnessConnectorDTO harnessConnectorDTO = (HarnessConnectorDTO) connectorInfoDTO.getConnectorConfig();

    when(secretManagerClientService.getEncryptionDetails(any(), any())).thenReturn(new ArrayList<>());
    HarnessUsernameTokenDTO harnessUsernameTokenDTO =
        HarnessUsernameTokenDTO.builder().tokenRef(SecretRefData.builder().identifier("secret").build()).build();
    when(decryptionHelper.decrypt(any(), any())).thenReturn(harnessUsernameTokenDTO);

    GitIntegrationAuth gitIntegrationAuth =
        harnessCodeRepoIntegrationOps.getAuth(harnessConnectorDTO, TEST_ACCOUNT_IDENTIFIER);
    assert gitIntegrationAuth instanceof GitIntegrationManagedTokenAuth;
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationConfigs() {
    HarnessCodeRepoIntegrationEntity harnessCodeRepoIntegrationEntity =
        HarnessCodeRepoIntegrationEntity.builder().host("app.harness.io").build();

    Map<String, String> integrationConfigs =
        harnessCodeRepoIntegrationOps.getIntegrationConfigs(harnessCodeRepoIntegrationEntity);

    assertEquals(0, integrationConfigs.size());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationSecretsToken() {
    GitIntegrationManagedTokenAuth gitIntegrationManagedTokenAuth = new GitIntegrationManagedTokenAuth();
    gitIntegrationManagedTokenAuth.setManagedTokenSecretIdentifier("secret");
    HarnessCodeRepoIntegrationEntity harnessCodeRepoIntegrationEntity =
        HarnessCodeRepoIntegrationEntity.builder()
            .host("app.harness.io")
            .authMode(GitIntegrationEntity.AuthMode.MANAGED_TOKEN)
            .auth(gitIntegrationManagedTokenAuth)
            .build();

    Map<String, String> integrationSecrets =
        harnessCodeRepoIntegrationOps.getIntegrationSecrets(harnessCodeRepoIntegrationEntity);

    assertEquals(1, integrationSecrets.size());
    assertEquals("secret",
        integrationSecrets.get(INTEGRATIONS_HARNESS_CODE_REPO_TOKEN + "_"
            + "APP_HARNESS_IO"));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetIntegrationAppConfig() {
    ConnectorDTO harnessCodeRepoConnectorDto = harnessCodeRepoConnectorDto();
    ConnectorInfoDTO connectorInfoDTO = harnessCodeRepoConnectorDto.getConnectorInfo();
    HarnessConnectorDTO harnessConnectorDTO = (HarnessConnectorDTO) connectorInfoDTO.getConnectorConfig();

    GitIntegrationManagedTokenAuth gitIntegrationManagedTokenAuth = new GitIntegrationManagedTokenAuth();
    gitIntegrationManagedTokenAuth.setManagedTokenSecretIdentifier("secret");
    HarnessCodeRepoIntegrationEntity harnessCodeRepoIntegrationEntity =
        HarnessCodeRepoIntegrationEntity.builder()
            .host("app.harness.io")
            .authMode(GitIntegrationEntity.AuthMode.MANAGED_TOKEN)
            .auth(gitIntegrationManagedTokenAuth)
            .build();

    String integrationConfig =
        harnessCodeRepoIntegrationOps.getIntegrationAppConfig(harnessCodeRepoIntegrationEntity, harnessConnectorDTO);
    assertEquals("---\nintegrations:\n"
            + "  harness:\n"
            + "  - host: \"vanity.harness.io\"\n"
            + "    token: \"${HARNESS_CODE_REPO_TOKEN_VANITY_HARNESS_IO}\"\n",
        integrationConfig);
  }

  @Test(expected = UnsupportedOperationException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetRepositoryException() {
    harnessCodeRepoIntegrationOps.getRepository(HarnessConnectorDTO.builder().build(), "repository");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAlreadyExistErrorMessage() {
    HarnessCodeRepoIntegrationEntity harnessCodeRepoIntegrationEntity =
        HarnessCodeRepoIntegrationEntity.builder().host("app.harness.io").build();
    String error = harnessCodeRepoIntegrationOps.getAlreadyExistErrorMessage(harnessCodeRepoIntegrationEntity);
    assertEquals("HarnessCodeRepo integration with host app.harness.io already exists. ", error);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteThroughAPI() {
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository(
        "https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/HarnessHCRInternalUAT/Harness_Code/harness-core.git");
    writeValidationDetails.setBranch("main");
    writeValidationDetails.setPath("harness");

    ConnectorDTO connectorDTO = harnessCodeRepoConnectorDto();

    MockedStatic<SourcePrincipalContextBuilder> mockedStaticSourcePrincipalContextBuilder =
        mockStatic(SourcePrincipalContextBuilder.class);
    UserPrincipal userPrincipal = new UserPrincipal("name", "email", "username", "accountId");
    mockedStaticSourcePrincipalContextBuilder.when(SourcePrincipalContextBuilder::getSourcePrincipal)
        .thenReturn(userPrincipal);

    when(connectorUtils.getConnectorDetails(any(), eq(connectorDTO)))
        .thenReturn(
            ConnectorDetails.builder().connectorConfig(connectorDTO.getConnectorInfo().getConnectorConfig()).build());
    when(secretDecryptor.decrypt(any(), any())).thenReturn(null);
    when(scmServiceClient.createFile(any(), any(), eq(scmBlockingStub), eq(false)))
        .thenReturn(CreateFileResponse.newBuilder().setStatus(200).build());

    harnessCodeRepoIntegrationOps.writeThroughAPI(TEST_ACCOUNT_IDENTIFIER, writeValidationDetails,
        connectorDTO.getConnectorInfo(), List.of(Pair.of("test", "test")));
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteThroughAPIError() {
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository(
        "https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/HarnessHCRInternalUAT/Harness_Code/harness-core.git");
    writeValidationDetails.setBranch("main");
    writeValidationDetails.setPath("harness");

    ConnectorDTO connectorDTO = harnessCodeRepoConnectorDto();

    MockedStatic<SourcePrincipalContextBuilder> mockedStaticSourcePrincipalContextBuilder =
        mockStatic(SourcePrincipalContextBuilder.class);
    UserPrincipal userPrincipal = new UserPrincipal("name", "email", "username", "accountId");
    mockedStaticSourcePrincipalContextBuilder.when(SourcePrincipalContextBuilder::getSourcePrincipal)
        .thenReturn(userPrincipal);

    when(connectorUtils.getConnectorDetails(any(), eq(connectorDTO)))
        .thenReturn(
            ConnectorDetails.builder().connectorConfig(connectorDTO.getConnectorInfo().getConnectorConfig()).build());
    when(secretDecryptor.decrypt(any(), any())).thenReturn(null);
    when(scmServiceClient.createFile(any(), any(), eq(scmBlockingStub), eq(false)))
        .thenReturn(CreateFileResponse.newBuilder().setStatus(300).setError("NoConflict").build());

    harnessCodeRepoIntegrationOps.writeThroughAPI(TEST_ACCOUNT_IDENTIFIER, writeValidationDetails,
        connectorDTO.getConnectorInfo(), List.of(Pair.of("test", "test")));
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteThroughAPIErrorUpdateFlowError() {
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository(
        "https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/HarnessHCRInternalUAT/Harness_Code/harness-core.git");
    writeValidationDetails.setBranch("main");
    writeValidationDetails.setPath("harness");

    ConnectorDTO connectorDTO = harnessCodeRepoConnectorDto();

    MockedStatic<SourcePrincipalContextBuilder> mockedStaticSourcePrincipalContextBuilder =
        mockStatic(SourcePrincipalContextBuilder.class);
    UserPrincipal userPrincipal = new UserPrincipal("name", "email", "username", "accountId");
    mockedStaticSourcePrincipalContextBuilder.when(SourcePrincipalContextBuilder::getSourcePrincipal)
        .thenReturn(userPrincipal);

    when(connectorUtils.getConnectorDetails(any(), eq(connectorDTO)))
        .thenReturn(
            ConnectorDetails.builder().connectorConfig(connectorDTO.getConnectorInfo().getConnectorConfig()).build());
    when(secretDecryptor.decrypt(any(), any())).thenReturn(null);
    when(scmServiceClient.createFile(any(), any(), eq(scmBlockingStub), eq(false)))
        .thenReturn(CreateFileResponse.newBuilder().setStatus(300).setError("Conflict").build());
    when(scmServiceClient.getFile(any(), any(), eq(scmBlockingStub)))
        .thenReturn(GitFileResponse.builder().statusCode(300).build());

    harnessCodeRepoIntegrationOps.writeThroughAPI(TEST_ACCOUNT_IDENTIFIER, writeValidationDetails,
        connectorDTO.getConnectorInfo(), List.of(Pair.of("test", "test")));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteThroughAPIErrorUpdateFlow() {
    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository(
        "https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/HarnessHCRInternalUAT/Harness_Code/harness-core.git");
    writeValidationDetails.setBranch("main");
    writeValidationDetails.setPath("harness");

    ConnectorDTO connectorDTO = harnessCodeRepoConnectorDto();

    MockedStatic<SourcePrincipalContextBuilder> mockedStaticSourcePrincipalContextBuilder =
        mockStatic(SourcePrincipalContextBuilder.class);
    UserPrincipal userPrincipal = new UserPrincipal("name", "email", "username", "accountId");
    mockedStaticSourcePrincipalContextBuilder.when(SourcePrincipalContextBuilder::getSourcePrincipal)
        .thenReturn(userPrincipal);

    when(connectorUtils.getConnectorDetails(any(), eq(connectorDTO)))
        .thenReturn(
            ConnectorDetails.builder().connectorConfig(connectorDTO.getConnectorInfo().getConnectorConfig()).build());
    when(secretDecryptor.decrypt(any(), any())).thenReturn(null);
    when(scmServiceClient.createFile(any(), any(), eq(scmBlockingStub), eq(false)))
        .thenReturn(CreateFileResponse.newBuilder().setStatus(300).setError("Conflict").build());
    when(scmServiceClient.getFile(any(), any(), eq(scmBlockingStub)))
        .thenReturn(GitFileResponse.builder().statusCode(200).objectId("objectId").commitId("commitId").build());
    when(scmServiceClient.updateFile(any(), any(), eq(scmBlockingStub), eq(false)))
        .thenReturn(UpdateFileResponse.newBuilder().setStatus(200).build());

    harnessCodeRepoIntegrationOps.writeThroughAPI(TEST_ACCOUNT_IDENTIFIER, writeValidationDetails,
        connectorDTO.getConnectorInfo(), List.of(Pair.of("test", "test")));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
