/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.service.git;

import static io.harness.idp.common.Constants.ACCOUNT_SCOPED;
import static io.harness.idp.common.Constants.BITBUCKET_CLOUD;
import static io.harness.idp.common.Constants.IDP_PREFIX;
import static io.harness.idp.integrations.entities.IntegrationEntity.SubType.GITHUB_ENTERPRISE;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_AZURE_INTEGRATION_ORGANIZATION1;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_CONNECTOR_IDENTIFIER;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GIT_INTEGRATION_IDENTIFIER;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_READ_VALIDATION_FILE_URL;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_WRITE_VALIDATION_BRANCH;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_WRITE_VALIDATION_PATH;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_WRITE_VALIDATION_REPO;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.azureRepoConnectorDTORepoToken;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.bitbucketCloudRepoConnectorDTORepoPassword;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.bitbucketServerRepoConnectorDTORepoPassword;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.githubConnectorDTORepoToken;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.gitlabConnectorDTORepoToken;
import static io.harness.idp.integrations.utils.Constants.HCR_CONNECTOR_IDENTIFIER;
import static io.harness.idp.integrations.utils.Constants.IDP_GIT_INTEGRATION_MANAGED_HCR;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.Constants;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.integrations.beans.git.GitIntegrationManagedTokenAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationTokenAuth;
import io.harness.idp.integrations.beans.git.GitIntegrationUsernamePasswordAuth;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.AzureIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketCloudIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketServerIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitlabIntegrationEntity;
import io.harness.idp.integrations.entities.git.HarnessCodeRepoIntegrationEntity;
import io.harness.idp.integrations.helpers.IntegrationsTestHelper;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.idp.v1.model.AppConfig;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationResponse;
import io.harness.spec.server.idp.v1.model.ReadValidationDetails;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;
import io.harness.springdata.TransactionHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationServiceImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_VANITY_URL = "https://vanity.harness.io";
  final HarnessCodeRepoConfig harnessCodeRepoConfig = HarnessCodeRepoConfig.builder()
                                                          .apiUrl("https://app.harness.io/code/git")
                                                          .gitBaseUrl("https://git.harness.io")
                                                          .baseUrl("https://app.harness.io")
                                                          .serviceClientSharedSecret("serviceClientSharedSecret")
                                                          .build();
  AutoCloseable openMocks;
  ConnectorDTO githubConnectorDtoRepoToken;
  ConnectorDTO gitlabConnectorDtoRepoToken;
  ConnectorDTO azureRepoConnectorDtoRepoToken;
  ConnectorDTO bitbucketCloudConnectorDtoRepoToken;
  ConnectorDTO bitbucketServerConnectorDtoRepoToken;

  @InjectMocks GitIntegrationServiceImpl gitIntegrationService;

  @Mock ConnectorResourceClient connectorResourceClient;
  @Mock GithubIntegrationOpsImpl githubIntegrationService;
  @Mock GitlabIntegrationOpsImpl gitlabIntegrationService;
  @Mock AzureIntegrationOpsImpl azureIntegrationService;
  @Mock BitbucketCloudIntegrationOpsImpl bitbucketCloudIntegrationService;
  @Mock BitbucketServerIntegrationOpsImpl bitbucketServerIntegrationService;
  @Mock HarnessCodeRepoIntegrationOpsImpl harnessCodeRepoIntegrationOps;
  @Mock IntegrationEntityRepository integrationEntityRepository;
  @Mock TransactionHelper transactionHelper;
  @Mock HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  @Mock SecretManagerClientService ngSecretService;
  @Mock IdpCommonService idpCommonService;

  @Before
  public void setUp() throws IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);

    githubConnectorDtoRepoToken = githubConnectorDTORepoToken();
    gitlabConnectorDtoRepoToken = gitlabConnectorDTORepoToken();
    azureRepoConnectorDtoRepoToken = azureRepoConnectorDTORepoToken();
    bitbucketCloudConnectorDtoRepoToken = bitbucketCloudRepoConnectorDTORepoPassword();
    bitbucketServerConnectorDtoRepoToken = bitbucketServerRepoConnectorDTORepoPassword();

    FieldUtils.writeField(gitIntegrationService, "harnessCodeRepoConfig", harnessCodeRepoConfig, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveRequestValidation1() {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, false);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveRequestValidation2() {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .readValidationDetails(new ReadValidationDetails().fileUrl(null))
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, false);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveRequestValidation3() {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .readValidationDetails(new ReadValidationDetails().fileUrl(null))
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveRequestValidation4() {
    GitIntegrationRequest gitIntegrationRequest =
        (GitIntegrationRequest) new GitIntegrationRequest()
            .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
            .writeValidationDetails(new WriteValidationDetails().repository(null))
            .type(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveRequestValidation5() {
    GitIntegrationRequest gitIntegrationRequest =
        (GitIntegrationRequest) new GitIntegrationRequest()
            .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
            .writeValidationDetails(new WriteValidationDetails().repository(TEST_WRITE_VALIDATION_REPO).branch(null))
            .type(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveRequestValidation6() {
    GitIntegrationRequest gitIntegrationRequest =
        (GitIntegrationRequest) new GitIntegrationRequest()
            .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
            .writeValidationDetails(new WriteValidationDetails()
                                        .repository(TEST_WRITE_VALIDATION_REPO)
                                        .branch(TEST_WRITE_VALIDATION_BRANCH)
                                        .path(null))
            .type(BaseIntegrationRequest.TypeEnum.GIT);
    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, true);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveConnectorFetchError() {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    willAnswer(invocation -> { throw new Exception("Exception Throw"); })
        .given(connectorResourceClient)
        .get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any());

    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, false, false);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveConnectorNotFoundError() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.empty());
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);

    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, false, false);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveDryRunWriteValidationSuccess() throws IOException {
    WriteValidationDetails writeValidationDetails = writeValidationDetails();
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .writeValidationDetails(writeValidationDetails)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(githubIntegrationService.prepare(githubConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(githubIntegrationEntity());
    doNothing()
        .when(githubIntegrationService)
        .validateWritePermission(
            TEST_CONNECTOR_IDENTIFIER, writeValidationDetails, githubConnectorDtoRepoToken.getConnectorInfo());

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, true);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Github", gitIntegrationResponse.getConnectorType());
    assertEquals("GITHUB_DIRECT", gitIntegrationResponse.getDisplayType());
    assertEquals("github.com", gitIntegrationResponse.getHost());
    assertEquals("UsernameToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveDryRunWriteValidationFailed() throws IOException {
    WriteValidationDetails writeValidationDetails = writeValidationDetails();
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .writeValidationDetails(writeValidationDetails)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(githubIntegrationService.prepare(githubConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(githubIntegrationEntity());
    doThrow(new UnexpectedException("Exception Throw"))
        .when(githubIntegrationService)
        .validateWritePermission(
            TEST_ACCOUNT_IDENTIFIER, writeValidationDetails, githubConnectorDtoRepoToken.getConnectorInfo());

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, true);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Github", gitIntegrationResponse.getConnectorType());
    assertEquals("GITHUB_DIRECT", gitIntegrationResponse.getDisplayType());
    assertEquals("github.com", gitIntegrationResponse.getHost());
    assertEquals("UsernameToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals("failed", gitIntegrationResponse.getValidation().getStatus());
    assertEquals("Exception Throw", gitIntegrationResponse.getValidation().getError());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveDryRunRead() throws IOException {
    ReadValidationDetails readValidationDetails = new ReadValidationDetails().fileUrl(TEST_READ_VALIDATION_FILE_URL);
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .readValidationDetails(readValidationDetails)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(githubIntegrationService.prepare(githubConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(githubIntegrationEntity());

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, false);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Github", gitIntegrationResponse.getConnectorType());
    assertEquals("GITHUB_DIRECT", gitIntegrationResponse.getDisplayType());
    assertEquals("github.com", gitIntegrationResponse.getHost());
    assertEquals("UsernameToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveAlreadyExists() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(githubIntegrationService.prepare(githubConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(githubIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.GITHUB, IntegrationEntity.SubType.GITHUB_DIRECT,
             "github.com"))
        .thenReturn(Optional.of(new GithubIntegrationEntity()));

    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, false, false);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveGithub() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(githubIntegrationService.prepare(githubConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(githubIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.GITHUB, IntegrationEntity.SubType.GITHUB_DIRECT,
             "github.com"))
        .thenReturn(Optional.empty());
    when(githubIntegrationService.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(githubIntegrationService.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(githubIntegrationService.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(githubIntegrationService)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, false, false);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Github", gitIntegrationResponse.getConnectorType());
    assertEquals("GITHUB_DIRECT", gitIntegrationResponse.getDisplayType());
    assertEquals("github.com", gitIntegrationResponse.getHost());
    assertEquals("UsernameToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveGitlab() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(gitlabConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(gitlabIntegrationService.prepare(gitlabConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(gitlabIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.GITLAB, null, "gitlab.com"))
        .thenReturn(Optional.empty());
    when(gitlabIntegrationService.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(gitlabIntegrationService.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(gitlabIntegrationService.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(gitlabIntegrationService)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, false, false);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Gitlab", gitIntegrationResponse.getConnectorType());
    assertEquals("GITLAB", gitIntegrationResponse.getDisplayType());
    assertEquals("gitlab.com", gitIntegrationResponse.getHost());
    assertEquals("UsernameToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveAzureRepo() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO =
        ResponseDTO.newResponse(Optional.of(azureRepoConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(azureIntegrationService.prepare(azureRepoConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(azureIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.AZURE, null, "organization1"))
        .thenReturn(Optional.empty());
    when(azureIntegrationService.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(azureIntegrationService.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(azureIntegrationService.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(azureIntegrationService)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, false, false);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("AzureRepo", gitIntegrationResponse.getConnectorType());
    assertEquals("AZURE", gitIntegrationResponse.getDisplayType());
    assertEquals("dev.azure.com/organization1", gitIntegrationResponse.getHost());
    assertEquals("UsernameToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveBitbucketCloud() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO =
        ResponseDTO.newResponse(Optional.of(bitbucketCloudConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(bitbucketCloudIntegrationService.prepare(bitbucketCloudConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(bitbucketCloudIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.BITBUCKET_CLOUD, null, null))
        .thenReturn(Optional.empty());
    when(bitbucketCloudIntegrationService.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(bitbucketCloudIntegrationService.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(bitbucketCloudIntegrationService.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(bitbucketCloudIntegrationService)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, false, false);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Bitbucket", gitIntegrationResponse.getConnectorType());
    assertEquals("BITBUCKET_CLOUD", gitIntegrationResponse.getDisplayType());
    assertEquals("bitbucket.org", gitIntegrationResponse.getHost());
    assertEquals("UsernamePassword", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveBitbucketServer() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO =
        ResponseDTO.newResponse(Optional.of(bitbucketServerConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(bitbucketServerIntegrationService.prepare(bitbucketServerConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(bitbucketServerIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.BITBUCKET_SERVER, null, null))
        .thenReturn(Optional.empty());
    when(bitbucketServerIntegrationService.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(bitbucketServerIntegrationService.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(bitbucketServerIntegrationService.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(bitbucketServerIntegrationService)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, false, false);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Bitbucket", gitIntegrationResponse.getConnectorType());
    assertEquals("BITBUCKET_SERVER", gitIntegrationResponse.getDisplayType());
    assertEquals("bitbucket.dev.harness.io", gitIntegrationResponse.getHost());
    assertEquals("UsernamePassword", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateNotFound() {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.empty());

    gitIntegrationService.update(
        TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationRequest, false);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateInvalidIncomingType() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.of(gitlabIntegrationEntity()));

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);

    gitIntegrationService.update(
        TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationRequest, false);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.of(githubIntegrationEntity()));

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(githubIntegrationService.prepare(githubConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(githubIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.GITHUB, IntegrationEntity.SubType.GITHUB_DIRECT,
             "github.com"))
        .thenReturn(Optional.empty());
    when(githubIntegrationService.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(githubIntegrationService.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(githubIntegrationService.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(githubIntegrationService)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));

    GitIntegrationResponse gitIntegrationResponse = gitIntegrationService.update(
        TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationRequest, false);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Github", gitIntegrationResponse.getConnectorType());
    assertEquals("GITHUB_DIRECT", gitIntegrationResponse.getDisplayType());
    assertEquals("github.com", gitIntegrationResponse.getHost());
    assertEquals("UsernameToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveOrUpdateSave() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.of(githubIntegrationEntity()));

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(githubIntegrationService.prepare(githubConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(githubIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.GITHUB, IntegrationEntity.SubType.GITHUB_DIRECT,
             "github.com"))
        .thenReturn(Optional.empty());
    when(githubIntegrationService.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(githubIntegrationService.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(githubIntegrationService.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(githubIntegrationService)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.saveOrUpdate(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Github", gitIntegrationResponse.getConnectorType());
    assertEquals("GITHUB_DIRECT", gitIntegrationResponse.getDisplayType());
    assertEquals("github.com", gitIntegrationResponse.getHost());
    assertEquals("UsernameToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveOrUpdateUpdate() throws IOException {
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.of(githubIntegrationEntity()));

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(githubIntegrationService.prepare(githubConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(githubIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.GITHUB, IntegrationEntity.SubType.GITHUB_DIRECT,
             "github.com"))
        .thenReturn(Optional.of(githubIntegrationEntity()));
    when(githubIntegrationService.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(githubIntegrationService.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(githubIntegrationService.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(githubIntegrationService)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.saveOrUpdate(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest);
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Github", gitIntegrationResponse.getConnectorType());
    assertEquals("GITHUB_DIRECT", gitIntegrationResponse.getDisplayType());
    assertEquals("github.com", gitIntegrationResponse.getHost());
    assertEquals("UsernameToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAll() {
    GithubIntegrationEntity githubIntegrationEntityEnterpriseToken = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_ENTERPRISE, TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, GitIntegrationEntity.AuthMode.TOKEN);
    GitlabIntegrationEntity gitlabIntegrationEntity = IntegrationsTestHelper.gitlabIntegrationEntity();
    AzureIntegrationEntity azureIntegrationEntityOrganization1 =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION1);

    List<IntegrationEntity> entities = new ArrayList<>();
    entities.add(githubIntegrationEntityEnterpriseToken);
    entities.add(gitlabIntegrationEntity);
    entities.add(azureIntegrationEntityOrganization1);

    when(integrationEntityRepository.findAll(any(), any())).thenReturn(new PageImpl<>(entities));
    List<GitIntegrationResponse> gitIntegrationResponses =
        gitIntegrationService.get(TEST_ACCOUNT_IDENTIFIER, PageRequest.of(0, 10), null);

    assertNotNull(gitIntegrationResponses);
    assertFalse(gitIntegrationResponses.isEmpty());
    assertEquals(3, gitIntegrationResponses.size());

    gitIntegrationResponses = gitIntegrationService.get(TEST_ACCOUNT_IDENTIFIER, PageRequest.of(0, 10), "test");

    assertNotNull(gitIntegrationResponses);
    assertFalse(gitIntegrationResponses.isEmpty());
    assertEquals(3, gitIntegrationResponses.size());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetNotFound() {
    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.empty());
    gitIntegrationService.get(TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGet() {
    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.of(githubIntegrationEntity()));

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.get(TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER);

    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Github", gitIntegrationResponse.getConnectorType());
    assertEquals("GITHUB_DIRECT", gitIntegrationResponse.getDisplayType());
    assertEquals("github.com", gitIntegrationResponse.getHost());
    assertEquals("UsernameToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(0L, gitIntegrationResponse.getUpdatedAt().longValue());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testDelete() {
    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.of(githubIntegrationEntity()));
    gitIntegrationService.delete(TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, false);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testProcessConnectorUpdate() throws IOException {
    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.of(githubIntegrationEntity()));

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);
    when(githubIntegrationService.prepare(githubConnectorDtoRepoToken.getConnectorInfo()))
        .thenReturn(githubIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.GITHUB, IntegrationEntity.SubType.GITHUB_DIRECT,
             "github.com"))
        .thenReturn(Optional.empty());
    when(githubIntegrationService.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(githubIntegrationService.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(githubIntegrationService.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(githubIntegrationService)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));

    gitIntegrationService.processConnectorUpdate(TEST_ACCOUNT_IDENTIFIER, TEST_CONNECTOR_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testProcessConnectorDelete() {
    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, TEST_GIT_INTEGRATION_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.of(githubIntegrationEntity()));
    gitIntegrationService.processConnectorDelete(TEST_ACCOUNT_IDENTIFIER, TEST_CONNECTOR_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteThroughAPI() throws IOException {
    WriteValidationDetails writeValidationDetails = writeValidationDetails();
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
                                                      .writeValidationDetails(writeValidationDetails)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    Call<ResponseDTO<Optional<ConnectorDTO>>> getConnectorResourceCall = mock(Call.class);
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(Optional.of(githubConnectorDtoRepoToken));
    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(connectorResourceClient.get(eq(TEST_CONNECTOR_IDENTIFIER), eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(getConnectorResourceCall);

    doNothing()
        .when(githubIntegrationService)
        .writeThroughAPI(eq(TEST_ACCOUNT_IDENTIFIER), eq(gitIntegrationRequest.getWriteValidationDetails()),
            eq(githubConnectorDtoRepoToken.getConnectorInfo()), any());

    gitIntegrationService.writeThroughAPI(
        TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, List.of(Pair.of("test", "test")));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSetupDefaultConnectorLessManagedHarnessCodeRepoIntegrationIfNotAlready() {
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.HARNESS_CODE_REPO, null, null))
        .thenReturn(Optional.empty());
    MockedStatic<SourcePrincipalContextBuilder> mockedStaticSourcePrincipalContextBuilder =
        mockStatic(SourcePrincipalContextBuilder.class);
    ServicePrincipal servicePrincipal = new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId());
    mockedStaticSourcePrincipalContextBuilder.when(SourcePrincipalContextBuilder::getSourcePrincipal)
        .thenReturn(servicePrincipal);
    MockedStatic<SecurityContextBuilder> mockedStaticSecurityContextBuilder = mockStatic(SecurityContextBuilder.class);
    mockedStaticSecurityContextBuilder.when(SecurityContextBuilder::getPrincipal).thenReturn(servicePrincipal);
    when(ngSecretService.create(eq(TEST_ACCOUNT_IDENTIFIER), eq(null), eq(null), eq(true), any()))
        .thenReturn(SecretResponseWrapper.builder().build());
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(AccountDTO.builder().identifier(TEST_ACCOUNT_IDENTIFIER).subdomainURL(TEST_VANITY_URL).build());
    when(harnessCodeRepoIntegrationOps.prepare(any())).thenReturn(harnessCodeRepoIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndParentTypeAndSubTypeAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.ParentType.HARNESS_CODE_REPO, null, "vanity.harness.io"))
        .thenReturn(Optional.empty());
    when(harnessCodeRepoIntegrationOps.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(harnessCodeRepoIntegrationOps.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(harnessCodeRepoIntegrationOps.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(harnessCodeRepoIntegrationOps)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));
    gitIntegrationService.setupDefaultConnectorLessManagedHarnessCodeRepoIntegrationIfNotAlready(
        TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFetchNonManagedGitIntegrations() {
    List<IntegrationEntity> nonManagedGitIntegrations = List.of(githubIntegrationEntity(), gitlabIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndIntegrationAndManagedFalse(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(nonManagedGitIntegrations);
    assertEquals(
        nonManagedGitIntegrations, gitIntegrationService.fetchNonManagedGitIntegrations(TEST_ACCOUNT_IDENTIFIER));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFetchManagedGitIntegrations() {
    List<IntegrationEntity> managedGitIntegrations = List.of(IntegrationsTestHelper.harnessCodeRepoIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifierAndIntegrationAndManagedTrue(
             TEST_ACCOUNT_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(managedGitIntegrations);
    assertEquals(managedGitIntegrations, gitIntegrationService.fetchManagedGitIntegrations(TEST_ACCOUNT_IDENTIFIER));
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateDefaultConnectorLessManagedHarnessCodeRepoIntegration() {
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(AccountDTO.builder().identifier(TEST_ACCOUNT_IDENTIFIER).subdomainURL(TEST_VANITY_URL).build());
    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, IDP_GIT_INTEGRATION_MANAGED_HCR, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.of(IntegrationsTestHelper.harnessCodeRepoIntegrationEntity()));
    when(harnessCodeRepoIntegrationOps.prepare(any())).thenReturn(harnessCodeRepoIntegrationEntity());
    when(harnessCodeRepoIntegrationOps.getAppConfig(any(), any())).thenReturn(new AppConfig());
    when(harnessCodeRepoIntegrationOps.getIntegrationConfigs(any())).thenReturn(Map.of("key1", "value1"));
    when(harnessCodeRepoIntegrationOps.getIntegrationSecrets(any())).thenReturn(Map.of("key1", "value1"));
    doNothing()
        .when(harnessCodeRepoIntegrationOps)
        .validateReadPermission(
            eq(TEST_ACCOUNT_IDENTIFIER), any(), any(), eq(Map.of("key1", "value1")), eq(Map.of("key1", "value1")));
    gitIntegrationService.updateDefaultConnectorLessManagedHarnessCodeRepoIntegration(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testSaveDryRunWriteValidationSuccessHarnessCodeRepo() {
    WriteValidationDetails writeValidationDetails = writeValidationDetails();
    writeValidationDetails.setRepository(
        "https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/HarnessHCRInternalUAT/Harness_Code/harness-core.git");
    GitIntegrationRequest gitIntegrationRequest = (GitIntegrationRequest) new GitIntegrationRequest()
                                                      .connectorIdentifier(ACCOUNT_SCOPED + HCR_CONNECTOR_IDENTIFIER)
                                                      .writeValidationDetails(writeValidationDetails)
                                                      .type(BaseIntegrationRequest.TypeEnum.GIT);

    when(harnessCodeConnectorUtils.getToken(any())).thenReturn("token");
    when(harnessCodeRepoIntegrationOps.prepare(any()))
        .thenReturn(IntegrationsTestHelper.harnessCodeRepoIntegrationEntity());
    doNothing()
        .when(harnessCodeRepoIntegrationOps)
        .validateWritePermission(eq(TEST_ACCOUNT_IDENTIFIER), eq(writeValidationDetails), any());

    GitIntegrationResponse gitIntegrationResponse =
        gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, true);
    assertEquals(IDP_GIT_INTEGRATION_MANAGED_HCR, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_GIT_INTEGRATION_MANAGED_HCR, gitIntegrationResponse.getName());
    assertEquals(IDP_GIT_INTEGRATION_MANAGED_HCR, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals("Harness", gitIntegrationResponse.getConnectorType());
    assertEquals("HARNESS_CODE_REPO", gitIntegrationResponse.getDisplayType());
    assertEquals("app.harness.io", gitIntegrationResponse.getHost());
    assertEquals("ManagedToken", gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertTrue(gitIntegrationResponse.isManaged());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());

    writeValidationDetails.setRepository(
        "https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/HarnessHCRInternalUAT/harness-core.git");
    gitIntegrationRequest.setWriteValidationDetails(writeValidationDetails);
    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, true);

    writeValidationDetails.setRepository("https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/harness-core.git");
    gitIntegrationRequest.setWriteValidationDetails(writeValidationDetails);
    gitIntegrationService.save(TEST_ACCOUNT_IDENTIFIER, gitIntegrationRequest, true, true);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testDeleteForAccount() {
    List<IntegrationEntity> integrationEntities = new ArrayList<>();
    integrationEntities.add(githubIntegrationEntity());
    integrationEntities.add(gitlabIntegrationEntity());
    when(integrationEntityRepository.findByAccountIdentifier(TEST_ACCOUNT_IDENTIFIER)).thenReturn(integrationEntities);
    when(integrationEntityRepository.findByAccountIdentifierAndIdentifierAndIntegration(
             TEST_ACCOUNT_IDENTIFIER, IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER, IntegrationEntity.Integration.GIT))
        .thenReturn(Optional.of(new GithubIntegrationEntity()));
    gitIntegrationService.delete(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetConnectorType() {
    IntegrationEntity integrationEntity = new GithubIntegrationEntity();
    integrationEntity.setParentType(IntegrationEntity.ParentType.GITHUB);
    String connectorType = gitIntegrationService.getConnectorType(integrationEntity);
    assertEquals(Constants.GITHUB, connectorType);

    integrationEntity = new GitlabIntegrationEntity();
    integrationEntity.setParentType(IntegrationEntity.ParentType.GITLAB);
    connectorType = gitIntegrationService.getConnectorType(integrationEntity);
    assertEquals(Constants.GITLAB, connectorType);

    integrationEntity = new GitlabIntegrationEntity();
    integrationEntity.setParentType(IntegrationEntity.ParentType.AZURE);
    connectorType = gitIntegrationService.getConnectorType(integrationEntity);
    assertEquals(Constants.AZURE_REPO, connectorType);

    integrationEntity = new GitlabIntegrationEntity();
    integrationEntity.setParentType(IntegrationEntity.ParentType.BITBUCKET_CLOUD);
    connectorType = gitIntegrationService.getConnectorType(integrationEntity);
    assertEquals(BITBUCKET_CLOUD, connectorType);

    integrationEntity = new GitlabIntegrationEntity();
    integrationEntity.setParentType(IntegrationEntity.ParentType.BITBUCKET_SERVER);
    connectorType = gitIntegrationService.getConnectorType(integrationEntity);
    assertEquals(Constants.BITBUCKET_SERVER, connectorType);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetAuthenticationDetailsForDelegateTask() {
    AzureIntegrationEntity azureIntegrationEntity = azureIntegrationEntity();
    when(integrationEntityRepository.findByAccountIdentifierAndAdditionalIndexer(
             TEST_ACCOUNT_IDENTIFIER, "organization1"))
        .thenReturn(List.of(azureIntegrationEntity));
    when(azureIntegrationService.getAuthenticationDetailsForDelegateTask(azureIntegrationEntity, List.of()))
        .thenReturn(AzureRepoTokenSpecDTO.builder().build());
    DecryptableEntity decryptableEntity = gitIntegrationService.getAuthenticationDetailsForDelegateTask(
        TEST_ACCOUNT_IDENTIFIER, "https://dev.azure.com/organization1/test", List.of(), null);
    assertEquals(AzureRepoTokenSpecDTO.class, decryptableEntity.getClass());
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private WriteValidationDetails writeValidationDetails() {
    return new WriteValidationDetails()
        .repository(TEST_WRITE_VALIDATION_REPO)
        .branch(TEST_WRITE_VALIDATION_BRANCH)
        .path(TEST_WRITE_VALIDATION_PATH);
  }

  private GithubIntegrationEntity githubIntegrationEntity() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("tokenSecretIdentifier");

    return GithubIntegrationEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .identifier(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER)
        .integration(IntegrationEntity.Integration.GIT)
        .parentType(IntegrationEntity.ParentType.GITHUB)
        .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
        .host("github.com")
        .subType(IntegrationEntity.SubType.GITHUB_DIRECT)
        .authMode(GitIntegrationEntity.AuthMode.TOKEN)
        .executeOnDelegate(false)
        .delegateSelectors(Set.of())
        .auth(gitIntegrationTokenAuth)
        .additionalIndexer("github.com")
        .build();
  }

  private GitlabIntegrationEntity gitlabIntegrationEntity() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("tokenSecretIdentifier");

    return GitlabIntegrationEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .identifier(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER)
        .integration(IntegrationEntity.Integration.GIT)
        .parentType(IntegrationEntity.ParentType.GITLAB)
        .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
        .host("gitlab.com")
        .authMode(GitIntegrationEntity.AuthMode.TOKEN)
        .executeOnDelegate(false)
        .delegateSelectors(Set.of())
        .auth(gitIntegrationTokenAuth)
        .additionalIndexer("gitlab.com")
        .build();
  }

  private AzureIntegrationEntity azureIntegrationEntity() {
    GitIntegrationTokenAuth gitIntegrationTokenAuth = new GitIntegrationTokenAuth();
    gitIntegrationTokenAuth.setTokenSecretIdentifier("tokenSecretIdentifier");

    return AzureIntegrationEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .identifier(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER)
        .integration(IntegrationEntity.Integration.GIT)
        .parentType(IntegrationEntity.ParentType.AZURE)
        .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
        .host("dev.azure.com")
        .authMode(GitIntegrationEntity.AuthMode.TOKEN)
        .executeOnDelegate(false)
        .delegateSelectors(Set.of())
        .auth(gitIntegrationTokenAuth)
        .additionalIndexer("organization1")
        .organization("organization1")
        .build();
  }

  private BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity() {
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsername("username");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("passwordSecretIdentifier");

    return BitbucketCloudIntegrationEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .identifier(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER)
        .integration(IntegrationEntity.Integration.GIT)
        .parentType(IntegrationEntity.ParentType.BITBUCKET_CLOUD)
        .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
        .host("bitbucket.org")
        .authMode(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD)
        .executeOnDelegate(false)
        .delegateSelectors(Set.of())
        .auth(gitIntegrationUsernamePasswordAuth)
        .build();
  }

  private BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity() {
    GitIntegrationUsernamePasswordAuth gitIntegrationUsernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    gitIntegrationUsernamePasswordAuth.setUsername("username");
    gitIntegrationUsernamePasswordAuth.setPasswordSecretIdentifier("passwordSecretIdentifier");

    return BitbucketServerIntegrationEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .identifier(IDP_PREFIX + TEST_CONNECTOR_IDENTIFIER)
        .integration(IntegrationEntity.Integration.GIT)
        .parentType(IntegrationEntity.ParentType.BITBUCKET_SERVER)
        .connectorIdentifier(TEST_CONNECTOR_IDENTIFIER)
        .host("bitbucket.dev.harness.io")
        .authMode(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD)
        .executeOnDelegate(false)
        .delegateSelectors(Set.of())
        .auth(gitIntegrationUsernamePasswordAuth)
        .build();
  }

  private HarnessCodeRepoIntegrationEntity harnessCodeRepoIntegrationEntity() {
    GitIntegrationManagedTokenAuth gitIntegrationManagedTokenAuth = new GitIntegrationManagedTokenAuth();
    gitIntegrationManagedTokenAuth.setManagedTokenSecretIdentifier("managedTokenSecretIdentifier");

    return HarnessCodeRepoIntegrationEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .identifier(IDP_GIT_INTEGRATION_MANAGED_HCR)
        .integration(IntegrationEntity.Integration.GIT)
        .parentType(IntegrationEntity.ParentType.HARNESS_CODE_REPO)
        .connectorIdentifier(IDP_GIT_INTEGRATION_MANAGED_HCR)
        .host("vanity.harness.io")
        .authMode(GitIntegrationEntity.AuthMode.MANAGED_TOKEN)
        .executeOnDelegate(false)
        .managed(true)
        .delegateSelectors(Set.of())
        .auth(gitIntegrationManagedTokenAuth)
        .additionalIndexer("vanity.harness.io")
        .build();
  }
}
