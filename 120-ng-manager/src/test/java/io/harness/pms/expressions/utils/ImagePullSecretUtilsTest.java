/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.AYUSHMAN;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PRAGYESH;
import static io.harness.rule.OwnerRule.TARUN_UBA;
import static io.harness.rule.OwnerRule.vivekveman;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessArtifactRegistryHelper;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GarArtifactOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GithubPackagesArtifactOutcome;
import io.harness.cdng.artifact.outcome.HarArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.cdng.artifactory.utils.ArtifactoryOidcHelperUtility;
import io.harness.cdng.aws.utils.AwsOidcConnectorUtility;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.azure.response.AzureAcrTokenTaskResponse;
import io.harness.delegate.beans.connector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthSADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureUserAssignedMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.constants.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.constants.AzureManagedIdentityType;
import io.harness.delegate.beans.connector.azureconnector.constants.AzureSecretType;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.source.ArtifactSourceConstants;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.DockerConfigJsonSecretFunctor;
import io.harness.expression.ImageSecretFunctor;
import io.harness.k8s.model.ImageDetails;
import io.harness.ng.BaseUrls;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.oidc.artifactory.credential.ArtifactoryOidcCredentialUtility;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.remote.NextGenConfig;
import io.harness.rule.Owner;
import io.harness.security.JWTTokenServiceUtils;
import io.harness.security.SimpleEncryption;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.PmsFeatureFlagHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(PIPELINE)
public class ImagePullSecretUtilsTest extends CategoryTest {
  @Mock private EcrImagePullSecretHelper ecrImagePullSecretHelper;
  @Mock private ConnectorService connectorService;

  @Mock private HarnessArtifactRegistryHelper harnessArtifactRegistryHelper;

  @Mock private NextGenConfiguration nextGenConfiguration;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Mock private BaseUrls baseUrls;
  @InjectMocks @Spy private ImagePullSecretUtils imagePullSecretUtils;
  @Mock private AzureHelperService azureHelperService;
  @Mock ArtifactoryOidcHelperUtility artifactoryOidcHelperUtility;
  @Mock AwsOidcConnectorUtility awsOidcConnectorUtility;
  @Mock ArtifactoryOidcCredentialUtility artifactoryOidcCredentialUtility;

  private final String ACCOUNT_ID_KEY = "accountId";
  private final String ACCOUNT_ID_VALUE = "accountId";
  private final String PROJECT_ID_KEY = "projectIdentifier";
  private final String PROJECT_ID_VALUE = "projectId";
  private final String ORG_ID_KEY = "orgIdentifier";
  private final String ORG_ID_VALUE = "orgIdentifier";

  private final String ACR_SUBSCRIPTION_ID = "123456-5432-5432-543213";
  private final String ACR_REGISTRY_NAME = "testreg";
  private final String ACR_REGISTRY = format("%s.azurecr.io", ACR_REGISTRY_NAME.toLowerCase());
  private final String ACR_DUMMY_USERNAME = "00000000-0000-0000-0000-000000000000";
  private final String ACR_REPOSITORY = "test/app";
  private final String ACR_TAG = "2.51";
  private final String ACR_CONNECTOR_REF = "aztestref";
  private final String ACR_SECRET_KEY = "secret";
  private final String ACR_SECRET_CERT = "certificate";
  private final String ACR_CLIENT_ID = "098766-5432-3456-765432";
  private final String ACR_TENANT_ID = "123456-5432-1234-765432";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testEcrImagePullSecret() throws IOException {
    final String ecrUrl = "https://aws_account_id.dkr.ecr.region.amazonaws.com/imageName";
    final String authToken = "QVdTOnJhbmRvbXRva2VuCg==";
    final String ecrImagePullSecret = "${imageSecret.create(\"https://https://"
        + "aws_account_id.dkr.ecr.region.amazonaws.com/\", \"AWS\", \"randomtoken\n"
        + "\")}";
    final String ecrDockerPullJson = "${dockerConfigJsonSecretFunc.create(\"https://https://"
        + "aws_account_id.dkr.ecr.region.amazonaws.com/\", \"AWS\", \"randomtoken\n"
        + "\")}";
    ArtifactOutcome artifactOutcome =
        EcrArtifactOutcome.builder().type(ArtifactSourceConstants.ECR_NAME).connectorRef("account").build();
    Ambiance ambiance = getAmbiance();
    ArtifactTaskExecutionResponse responseForImageUrl =
        ArtifactTaskExecutionResponse.builder()
            .artifactDelegateResponse(EcrArtifactDelegateResponse.builder().imageUrl(ecrUrl).build())
            .build();
    ArtifactTaskExecutionResponse responseForAuthToken =
        ArtifactTaskExecutionResponse.builder()
            .artifactDelegateResponse(EcrArtifactDelegateResponse.builder().authToken(authToken).build())
            .build();
    BaseNGAccess baseNGAccess = BaseNGAccess.builder().build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();
    encryptionDetails.add(EncryptedDataDetail.builder().build());
    ResponseDTO<Optional<ConnectorDTO>> responseDTO = ResponseDTO.newResponse(
        Optional.of(ConnectorDTO.builder().connectorInfo(ConnectorInfoDTO.builder().build()).build()));

    Optional<ConnectorResponseDTO> connectorResponseDTO = Optional.of(
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder().connectorConfig(AwsConnectorDTO.builder().build()).build())
            .build());

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    when(ecrImagePullSecretHelper.getBaseNGAccess(any(), any(), any())).thenReturn(baseNGAccess);
    when(ecrImagePullSecretHelper.getEncryptionDetails(any(), any())).thenReturn(encryptionDetails);
    when(ecrImagePullSecretHelper.executeSyncTask(any(), eq(ArtifactTaskType.GET_IMAGE_URL), any(), any()))
        .thenReturn(responseForImageUrl);
    when(ecrImagePullSecretHelper.executeSyncTask(any(), eq(ArtifactTaskType.GET_AUTH_TOKEN), any(), any()))
        .thenReturn(responseForAuthToken);
    assertThat(imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance).equals(ecrImagePullSecret));
    assertThat(imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance).equals(ecrDockerPullJson));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGcrImagePullSecret() throws IOException {
    ArtifactOutcome artifactOutcome = GcrArtifactOutcome.builder()
                                          .registryHostname("us.gcr.io")
                                          .imagePath("test-image")
                                          .type(ArtifactSourceConstants.GCR_NAME)
                                          .connectorRef("account")
                                          .build();
    Ambiance ambiance = getAmbiance();

    GcpConnectorCredentialDTO connectorCredentialDTO =
        GcpConnectorCredentialDTO.builder()
            .config(GcpManualDetailsDTO.builder()
                        .secretKeyRef(SecretRefData.builder().identifier("secret").build())
                        .build())
            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
            .build();
    Optional<ConnectorResponseDTO> connectorResponseDTO = Optional.of(
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .connectorConfig(GcpConnectorDTO.builder().credential(connectorCredentialDTO).build())
                           .build())
            .build());
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance))
        .isInstanceOf(InvalidRequestException.class);

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    assertEquals(imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance),
        "${imageSecret.create(\"us.gcr.io/test-image\", \"_json_key\", ${ngSecretManager.obtain(\"null\", 0)})}");
    assertEquals(imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance),
        "${dockerConfigJsonSecretFunc.create(\"us.gcr.io/test-image\", \"_json_key\", "
            + "${ngSecretManager.obtain(\"null\", 0)})}");
  }

  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testGarImagePullSecret() throws IOException {
    // Test GAR artifact with tag to ensure registry URL excludes the tag
    String registryHostname = "us-west1-docker.pkg.dev";
    String project = "gar-setup";
    String pkg = "docker/delegate";
    String version = "25.07.86300";
    String fullImageWithTag = registryHostname + "/" + project + "/" + pkg + ":" + version;
    String expectedRegistryUrlWithoutTag = registryHostname + "/" + project + "/" + pkg;

    GarArtifactOutcome garArtifactOutcome = GarArtifactOutcome.builder()
                                                .connectorRef("account.gcpConnector")
                                                .registryHostname(registryHostname)
                                                .project(project)
                                                .pkg(pkg)
                                                .version(version)
                                                .image(fullImageWithTag)
                                                .type(ArtifactSourceConstants.GOOGLE_ARTIFACT_REGISTRY_NAME)
                                                .build();

    Ambiance ambiance = getAmbiance();

    GcpConnectorCredentialDTO connectorCredentialDTO =
        GcpConnectorCredentialDTO.builder()
            .config(GcpManualDetailsDTO.builder()
                        .secretKeyRef(SecretRefData.builder().identifier("secret").build())
                        .build())
            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
            .build();

    Optional<ConnectorResponseDTO> connectorResponseDTO = Optional.of(
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .connectorConfig(GcpConnectorDTO.builder().credential(connectorCredentialDTO).build())
                           .build())
            .build());

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);

    String imagePullSecret = imagePullSecretUtils.getImagePullSecret(garArtifactOutcome, ambiance);
    assertThat(imagePullSecret).contains(expectedRegistryUrlWithoutTag);
    assertThat(imagePullSecret).doesNotContain(":" + version);

    String dockerConfigJson = imagePullSecretUtils.getDockerConfigJson(garArtifactOutcome, ambiance);
    assertThat(dockerConfigJson).contains(expectedRegistryUrlWithoutTag);
    assertThat(dockerConfigJson).doesNotContain(":" + version);
    assertThat(dockerConfigJson).contains("_json_key");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testDockerImagePullSecret() throws IOException {
    ArtifactOutcome artifactOutcome = DockerArtifactOutcome.builder()
                                          .imagePath("test-image")
                                          .type(ArtifactSourceConstants.DOCKER_REGISTRY_NAME)
                                          .connectorRef("account")
                                          .build();
    Ambiance ambiance = getAmbiance();

    DockerAuthenticationDTO authenticationDTO =
        DockerAuthenticationDTO.builder()
            .authType(DockerAuthType.USER_PASSWORD)
            .credentials(DockerUserNamePasswordDTO.builder()
                             .usernameRef(SecretRefData.builder().identifier("username").build())
                             .passwordRef(SecretRefData.builder().identifier("password").build())
                             .build())
            .build();

    Optional<ConnectorResponseDTO> connectorResponseDTO =
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder()
                                       .connectorConfig(DockerConnectorDTO.builder()
                                                            .dockerRegistryUrl("index.docker.io")
                                                            .auth(authenticationDTO)
                                                            .build())
                                       .build())
                        .build());

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    assertEquals(imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance),
        "${imageSecret.create(\"index.docker.io\", ${ngSecretManager.obtain(\"null\", 0)}, "
            + "${ngSecretManager.obtain(\"null\", 0)})}");
    assertEquals(imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance),
        "${dockerConfigJsonSecretFunc.create(\"index.docker.io\", ${ngSecretManager.obtain(\"null\", 0)}, "
            + "${ngSecretManager.obtain(\"null\", 0)})}");
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void testHarnessArtifactRegistryDockerImagePullSecret() throws IOException {
    ArtifactOutcome artifactOutcome = HarArtifactOutcome.builder()
                                          .imagePath("test-image")
                                          .type(ArtifactSourceConstants.HARNESS_ARTIFACT_REGISTRY_NAME)
                                          .registryRef("test-registry")
                                          .tag("test-tag")
                                          .build();
    Ambiance ambiance = getAmbiance();

    Principal principal = new UserPrincipal("test-name", "test@harness.io", "test", "test-accountId", "test-role");

    NextGenConfig nextGenConfig = NextGenConfig.builder().ngManagerServiceSecret("test-secret").build();

    AccountDTO accountDTO = AccountDTO.builder().build();

    String registry = "test.url.io";
    String password = "test-token";

    when(harnessArtifactRegistryHelper.getPrincipal(any())).thenReturn(principal);
    when(nextGenConfiguration.getNextGenConfig()).thenReturn(nextGenConfig);
    when(baseUrls.getHarnessArtifactRegistryUrl()).thenReturn(registry);
    when(harnessArtifactRegistryHelper.getRegistryHost(any())).thenReturn(registry);
    when(harnessArtifactRegistryHelper.getAccountDTO(any())).thenReturn(accountDTO);

    try (MockedStatic<JWTTokenServiceUtils> mockStatic = mockStatic(JWTTokenServiceUtils.class)) {
      when(JWTTokenServiceUtils.generateJWTToken(any(), any(), any())).thenReturn(password);

      assertEquals(imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance),
          "${imageSecret.create(\"test.url.io\", \"test-name\", \"test-token\")}");
      assertEquals(imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance),
          "${dockerConfigJsonSecretFunc.create(\"test.url.io\", \"test-name\", \"test-token\")}");
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testNexusDockerImagePullSecret() throws IOException {
    ArtifactOutcome artifactOutcome = NexusArtifactOutcome.builder()
                                          .artifactPath("test-image")
                                          .type(ArtifactSourceConstants.NEXUS3_REGISTRY_NAME)
                                          .connectorRef("account")
                                          .build();
    Ambiance ambiance = getAmbiance();

    NexusAuthenticationDTO authenticationDTO =
        NexusAuthenticationDTO.builder()
            .authType(NexusAuthType.USER_PASSWORD)
            .credentials(NexusUsernamePasswordAuthDTO.builder()
                             .usernameRef(SecretRefData.builder().identifier("username").build())
                             .passwordRef(SecretRefData.builder().identifier("password").build())
                             .build())
            .build();

    Optional<ConnectorResponseDTO> connectorResponseDTO = Optional.of(
        ConnectorResponseDTO.builder()
            .connector(
                ConnectorInfoDTO.builder()
                    .connectorConfig(
                        NexusConnectorDTO.builder().nexusServerUrl("nexus.harness.io").auth(authenticationDTO).build())
                    .build())
            .build());

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    assertEquals(imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance),
        "${imageSecret.create(\"nexus.harness.io\", ${ngSecretManager.obtain(\"null\", 0)}, "
            + "${ngSecretManager.obtain(\"null\", 0)})}");
    assertEquals(imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance),
        "${dockerConfigJsonSecretFunc.create(\"nexus.harness.io\", ${ngSecretManager.obtain(\"null\", 0)}, "
            + "${ngSecretManager.obtain(\"null\", 0)})}");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testArtifactoryDockerImagePullSecret() throws IOException {
    ArtifactOutcome artifactOutcome = ArtifactoryArtifactOutcome.builder()
                                          .artifactPath("test-image")
                                          .type(ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME)
                                          .connectorRef("account")
                                          .build();
    Ambiance ambiance = getAmbiance();

    ArtifactoryAuthenticationDTO authenticationDTO =
        ArtifactoryAuthenticationDTO.builder()
            .authType(ArtifactoryAuthType.USER_PASSWORD)
            .credentials(ArtifactoryUsernamePasswordAuthDTO.builder()
                             .usernameRef(SecretRefData.builder().identifier("username").build())
                             .passwordRef(SecretRefData.builder().identifier("password").build())
                             .build())
            .build();

    Optional<ConnectorResponseDTO> connectorResponseDTO =
        Optional.of(ConnectorResponseDTO.builder()
                        .connector(ConnectorInfoDTO.builder()
                                       .connectorConfig(ArtifactoryConnectorDTO.builder()
                                                            .artifactoryServerUrl("harness.jfrog.io")
                                                            .auth(authenticationDTO)
                                                            .build())
                                       .build())
                        .build());

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    assertEquals(imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance),
        "${imageSecret.create(\"harness.jfrog.io\", ${ngSecretManager.obtain(\"null\", 0)}, "
            + "${ngSecretManager.obtain(\"null\", 0)})}");
    assertEquals(imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance),
        "${dockerConfigJsonSecretFunc.create(\"harness.jfrog.io\", ${ngSecretManager.obtain(\"null\", 0)}, "
            + "${ngSecretManager.obtain(\"null\", 0)})}");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUnsupportedArtifactSourceType() {
    ArtifactOutcome artifactOutcome =
        DockerArtifactOutcome.builder().imagePath("test-image").type("randomString").connectorRef("account").build();
    Ambiance ambiance = getAmbiance();

    assertThatThrownBy(() -> imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testAcrImagePullSecretUsingSPWithSecret() {
    ArtifactOutcome artifactOutcome = getAcrArtifactOutcome();
    Ambiance ambiance = getAmbiance();
    AzureCredentialDTO azureCredentialDTO = getAzureCredentialsForSPWithSecret();
    Optional<ConnectorResponseDTO> connectorResponseDTO = getAzureConnector(azureCredentialDTO);

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    assertEquals(imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance),
        format("${imageSecret.create(\"%s\", \"%s\", ${ngSecretManager.obtain(\"%s\", 0)})}", ACR_REGISTRY,
            ACR_CLIENT_ID, "null"));
    assertEquals(imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance),
        format("${dockerConfigJsonSecretFunc.create(\"%s\", \"%s\", ${ngSecretManager.obtain(\"%s\", 0)})}",
            ACR_REGISTRY, ACR_CLIENT_ID, "null"));
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testAcrImagePullSecretUsingSPWithCert() {
    ArtifactOutcome artifactOutcome = getAcrArtifactOutcome();
    Ambiance ambiance = getAmbiance();
    BaseNGAccess baseNGAccess = getBaseNGAccess(ACCOUNT_ID_VALUE, ORG_ID_VALUE, PROJECT_ID_VALUE);
    AzureCredentialDTO azureCredentialDTO = getAzureCredentialsForSPWithCert();
    Optional<ConnectorResponseDTO> connectorResponseDTO = getAzureConnector(azureCredentialDTO);

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    when(azureHelperService.getBaseNGAccess(ACCOUNT_ID_VALUE, ORG_ID_VALUE, PROJECT_ID_VALUE)).thenReturn(baseNGAccess);
    when(azureHelperService.getEncryptionDetails(any(), any())).thenReturn(null);

    String jwtAcrToken = "ejyaoisncoanidoaiwjndoqiwjndocqijwdoqw9cjoq93jcq0owi9f0qc9i3jc93";
    AzureAcrTokenTaskResponse azureAcrTokenTaskResponse =
        AzureAcrTokenTaskResponse.builder().token(jwtAcrToken).build();

    when(azureHelperService.executeSyncTask(any(), any(), any(BaseNGAccess.class), anyString()))
        .thenReturn(azureAcrTokenTaskResponse);
    assertEquals(imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance),
        format("${imageSecret.create(\"%s\", \"%s\", \"%s\")}", ACR_REGISTRY, ACR_DUMMY_USERNAME, jwtAcrToken));
    assertEquals(imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance),
        format("${dockerConfigJsonSecretFunc.create(\"%s\", \"%s\", \"%s\")}", ACR_REGISTRY, ACR_DUMMY_USERNAME,
            jwtAcrToken));
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testFailedAcrImagePullSecretUsingSPWithCertWhenNoTokenFetched() {
    ArtifactOutcome artifactOutcome = getAcrArtifactOutcome();
    Ambiance ambiance = getAmbiance();
    BaseNGAccess baseNGAccess = getBaseNGAccess(ACCOUNT_ID_VALUE, ORG_ID_VALUE, PROJECT_ID_VALUE);
    AzureCredentialDTO azureCredentialDTO = getAzureCredentialsForSPWithCert();
    Optional<ConnectorResponseDTO> connectorResponseDTO = getAzureConnector(azureCredentialDTO);

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    when(azureHelperService.getBaseNGAccess(ACCOUNT_ID_VALUE, ORG_ID_VALUE, PROJECT_ID_VALUE)).thenReturn(baseNGAccess);
    when(azureHelperService.getEncryptionDetails(any(), any())).thenReturn(null);
    when(azureHelperService.executeSyncTask(any(), any(), any(BaseNGAccess.class), anyString()))
        .thenThrow(new RuntimeException("some unexpected exception"));

    assertThatThrownBy(() -> imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testAcrImagePullSecretUsingUserAssignedMSI() {
    ArtifactOutcome artifactOutcome = getAcrArtifactOutcome();
    Ambiance ambiance = getAmbiance();
    BaseNGAccess baseNGAccess = getBaseNGAccess(ACCOUNT_ID_VALUE, ORG_ID_VALUE, PROJECT_ID_VALUE);
    AzureCredentialDTO azureCredentialDTO = getAzureCredentialsForUserAssignedMSI();
    Optional<ConnectorResponseDTO> connectorResponseDTO = getAzureConnector(azureCredentialDTO);

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    when(azureHelperService.getBaseNGAccess(ACCOUNT_ID_VALUE, ORG_ID_VALUE, PROJECT_ID_VALUE)).thenReturn(baseNGAccess);
    when(azureHelperService.getEncryptionDetails(any(), any())).thenReturn(null);

    String jwtAcrToken = "ejyaoisncoanidoaiwjndoqiwjndocqijwdoqw9cjoq93jcq0owi9f0qc9i3jc93";
    AzureAcrTokenTaskResponse azureAcrTokenTaskResponse =
        AzureAcrTokenTaskResponse.builder().token(jwtAcrToken).build();

    when(azureHelperService.executeSyncTask(any(), any(), any(BaseNGAccess.class), anyString()))
        .thenReturn(azureAcrTokenTaskResponse);
    assertEquals(imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance),
        format("${imageSecret.create(\"%s\", \"%s\", \"%s\")}", ACR_REGISTRY, ACR_DUMMY_USERNAME, jwtAcrToken));
    assertEquals(imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance),
        format("${dockerConfigJsonSecretFunc.create(\"%s\", \"%s\", \"%s\")}", ACR_REGISTRY, ACR_DUMMY_USERNAME,
            jwtAcrToken));
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testAcrImagePullSecretUsingSystemAssignedMSI() {
    ArtifactOutcome artifactOutcome = getAcrArtifactOutcome();
    Ambiance ambiance = getAmbiance();
    BaseNGAccess baseNGAccess = getBaseNGAccess(ACCOUNT_ID_VALUE, ORG_ID_VALUE, PROJECT_ID_VALUE);
    AzureCredentialDTO azureCredentialDTO = getAzureCredentialsForSystemAssignedMSI();
    Optional<ConnectorResponseDTO> connectorResponseDTO = getAzureConnector(azureCredentialDTO);

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    when(azureHelperService.getBaseNGAccess(ACCOUNT_ID_VALUE, ORG_ID_VALUE, PROJECT_ID_VALUE)).thenReturn(baseNGAccess);
    when(azureHelperService.getEncryptionDetails(any(), any())).thenReturn(null);

    String jwtAcrToken = "ejyaoisncoanidoaiwjndoqiwjndocqijwdoqw9cjoq93jcq0owi9f0qc9i3jc93";
    AzureAcrTokenTaskResponse azureAcrTokenTaskResponse =
        AzureAcrTokenTaskResponse.builder().token(jwtAcrToken).build();

    when(azureHelperService.executeSyncTask(any(), any(), any(BaseNGAccess.class), anyString()))
        .thenReturn(azureAcrTokenTaskResponse);
    assertEquals(imagePullSecretUtils.getImagePullSecret(artifactOutcome, ambiance),
        format("${imageSecret.create(\"%s\", \"%s\", \"%s\")}", ACR_REGISTRY, ACR_DUMMY_USERNAME, jwtAcrToken));
    assertEquals(imagePullSecretUtils.getDockerConfigJson(artifactOutcome, ambiance),
        format("${dockerConfigJsonSecretFunc.create(\"%s\", \"%s\", \"%s\")}", ACR_REGISTRY, ACR_DUMMY_USERNAME,
            jwtAcrToken));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGithubImagePullSecret() throws IOException {
    GithubPackagesArtifactOutcome githubPackagesArtifactOutcome = GithubPackagesArtifactOutcome.builder()
                                                                      .type("GithubPackageRegistry")
                                                                      .packageType("container")
                                                                      .connectorRef("account.accountref")
                                                                      .image("image")
                                                                      .packageName("package")
                                                                      .build();
    Ambiance ambiance = getAmbiance();

    Optional<ConnectorResponseDTO> connectorResponseDTO = Optional.of(
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder()
                           .connectorConfig(
                               GithubConnectorDTO.builder()
                                   .authentication(
                                       GithubAuthenticationDTO.builder()
                                           .authType(GitAuthType.HTTP)
                                           .credentials(GithubHttpCredentialsDTO.builder()
                                                            .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                                            .httpCredentialsSpec(
                                                                GithubUsernameTokenDTO.builder()
                                                                    .username("username")
                                                                    .tokenRef(SecretRefData.builder()
                                                                                  .identifier("accountLevelConnector")
                                                                                  .scope(Scope.ACCOUNT)
                                                                                  .build())
                                                                    .build())
                                                            .build())
                                           .build())
                                   .apiAccess(GithubApiAccessDTO.builder()
                                                  .type(GithubApiAccessType.TOKEN)
                                                  .spec(GithubTokenSpecDTO.builder()
                                                            .tokenRef(SecretRefData.builder()
                                                                          .identifier("accountLevelConnector")
                                                                          .scope(Scope.ACCOUNT)
                                                                          .build())
                                                            .build())
                                                  .build())
                                   .build())
                           .build())
            .build());

    when(connectorService.get(any(), any(), any(), any())).thenReturn(connectorResponseDTO);
    assertEquals(imagePullSecretUtils.getImagePullSecret(githubPackagesArtifactOutcome, ambiance),
        format("${imageSecret.create(\"%s\", \"%s\", ${ngSecretManager.obtain(\"%s\", 0)})}", "https://ghcr.io",
            "username", "account.accountLevelConnector"));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetSweepingOutPutSecret() {
    SimpleEncryption encryption = mock(SimpleEncryption.class);
    when(encryption.encrypt(any(byte[].class))).thenReturn("encrypted-secret".getBytes(StandardCharsets.UTF_8));

    String result = ImagePullSecretUtils.getSweepingOutPutSecret("secret", false, encryption);
    assertEquals("${sweepingOutputSecrets.obtain(\"ecr_image_pull_secret_sweepingOutput_var_name\","
            + "\"ZW5jcnlwdGVkLXNlY3JldA==\")}",
        result);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetSweepingOutPutSecret_withSingleQuotes() {
    SimpleEncryption encryption = mock(SimpleEncryption.class);
    when(encryption.encrypt(any(byte[].class))).thenReturn("encrypted-secret".getBytes(StandardCharsets.UTF_8));

    String result = ImagePullSecretUtils.getSweepingOutPutSecret("test-secret", true, encryption);
    assertEquals(
        "${sweepingOutputSecrets.obtain('ecr_image_pull_secret_sweepingOutput_var_name', 'ZW5jcnlwdGVkLXNlY3JldA==')}",
        result);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetArtifactRegistryCredentials_withPasswordAndSweepingFunctor() throws Exception {
    ImageDetails imageDetails =
        ImageDetails.builder().password("password").username("username").registryUrl("https://registry.com").build();

    try (MockedStatic<ImageSecretFunctor> mockedStaticImageSecret = Mockito.mockStatic(ImageSecretFunctor.class)) {
      mockedStaticImageSecret
          .when(() -> ImageSecretFunctor.createAndEncodeImageSecret("https://registry.com", "username", "password"))
          .thenReturn("mockedEncodedSecret");

      imagePullSecretUtils.getArtifactRegistryCredentials(imageDetails, false, true);

      mockedStaticImageSecret.verify(
          ()
              -> ImageSecretFunctor.createAndEncodeImageSecret("https://registry.com", "username", "password"),
          times(1));
    }
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetArtifactRegistryCredentials_withoutPasswordAndPasswordRef() throws Exception {
    ImageDetails imageDetails = ImageDetails.builder().username("username").registryUrl("https://registry.com").build();

    assertThat(imagePullSecretUtils.getArtifactRegistryCredentials(imageDetails, false, true)).isEqualTo("");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetArtifactRegistryCredentialsFromUsernameRef_withoutPasswordAndPasswordRef() throws Exception {
    ImageDetails imageDetails =
        ImageDetails.builder().usernameRef("usernameRef").registryUrl("https://registry.com").build();

    assertThat(imagePullSecretUtils.getArtifactRegistryCredentials(imageDetails, false, true)).isEqualTo("");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetArtifactRegistryCredentials_withPasswordAndSweepingFunctorFalse() throws Exception {
    ImageDetails imageDetails =
        ImageDetails.builder().password("password").username("username").registryUrl("https://registry.com").build();

    try (MockedStatic<ImageSecretFunctor> mockedStaticImageSecret = Mockito.mockStatic(ImageSecretFunctor.class)) {
      mockedStaticImageSecret
          .when(() -> ImageSecretFunctor.createAndEncodeImageSecret("https://registry.com", "username", "password"))
          .thenReturn("mockedEncodedSecret");

      String result = imagePullSecretUtils.getArtifactRegistryCredentials(imageDetails, false, false);

      mockedStaticImageSecret.verify(
          ()
              -> ImageSecretFunctor.createAndEncodeImageSecret("https://registry.com", "username", "password"),
          times(0));

      assertEquals("${imageSecret.create(\"https://registry.com\", \"username\", \"password\")}", result);
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testgGetArtifactRegistryCredentialsFromUsernameRef_withPasswordAndSweepingFunctor() throws Exception {
    ImageDetails imageDetails =
        ImageDetails.builder().password("password").usernameRef("username").registryUrl("https://registry.com").build();

    try (MockedStatic<ImageSecretFunctor> mockedStaticImageSecret = Mockito.mockStatic(ImageSecretFunctor.class)) {
      mockedStaticImageSecret
          .when(() -> ImageSecretFunctor.createAndEncodeImageSecret("https://registry.com", "username", "password"))
          .thenReturn("mockedEncodedSecret");

      imagePullSecretUtils.getArtifactRegistryCredentialsFromUsernameRef(imageDetails, false, true);

      mockedStaticImageSecret.verify(
          ()
              -> ImageSecretFunctor.createAndEncodeImageSecret("https://registry.com", "username", "password"),
          times(1));
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetArtifactRegistryCredentialsFromUsernameRef_withPasswordAndSweepingFunctorFalse() throws Exception {
    ImageDetails imageDetails =
        ImageDetails.builder().password("password").usernameRef("username").registryUrl("https://registry.com").build();

    try (MockedStatic<ImageSecretFunctor> mockedStaticImageSecret = Mockito.mockStatic(ImageSecretFunctor.class)) {
      mockedStaticImageSecret
          .when(() -> ImageSecretFunctor.createAndEncodeImageSecret("https://registry.com", "username", "password"))
          .thenReturn("mockedEncodedSecret");

      String result = imagePullSecretUtils.getArtifactRegistryCredentialsFromUsernameRef(imageDetails, false, false);

      mockedStaticImageSecret.verify(
          ()
              -> ImageSecretFunctor.createAndEncodeImageSecret("https://registry.com", "username", "password"),
          times(0));

      assertEquals("${imageSecret.create(\"https://registry.com\", username, \"password\")}", result);
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetDockerConfigJson_withPasswordAndSweepingFunctor() throws Exception {
    ImageDetails imageDetails =
        ImageDetails.builder().password("password").username("username").registryUrl("https://registry.com").build();

    try (MockedStatic<DockerConfigJsonSecretFunctor> mockedDockerConfigJsonSecretFunctor =
             Mockito.mockStatic(DockerConfigJsonSecretFunctor.class)) {
      mockedDockerConfigJsonSecretFunctor
          .when(()
                    -> DockerConfigJsonSecretFunctor.createAndEncodeDockerConfigJsonSecret(
                        "https://registry.com", "username", "password"))
          .thenReturn("mockedEncodedSecret");

      imagePullSecretUtils.getDockerConfigJson(imageDetails, false, true);

      mockedDockerConfigJsonSecretFunctor.verify(
          ()
              -> DockerConfigJsonSecretFunctor.createAndEncodeDockerConfigJsonSecret(
                  "https://registry.com", "username", "password"),
          times(1));
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetDockerConfigJson_withPasswordAndSweepingFunctorFalse() throws Exception {
    ImageDetails imageDetails =
        ImageDetails.builder().password("password").username("username").registryUrl("https://registry.com").build();

    try (MockedStatic<DockerConfigJsonSecretFunctor> mockedDockerConfigJsonSecretFunctor =
             Mockito.mockStatic(DockerConfigJsonSecretFunctor.class)) {
      mockedDockerConfigJsonSecretFunctor
          .when(()
                    -> DockerConfigJsonSecretFunctor.createAndEncodeDockerConfigJsonSecret(
                        "https://registry.com", "username", "password"))
          .thenReturn("mockedEncodedSecret");

      String result = imagePullSecretUtils.getDockerConfigJson(imageDetails, false, false);

      mockedDockerConfigJsonSecretFunctor.verify(
          ()
              -> DockerConfigJsonSecretFunctor.createAndEncodeDockerConfigJsonSecret(
                  "https://registry.com", "username", "password"),
          times(0));

      assertEquals(
          "${dockerConfigJsonSecretFunc.create(\"https://registry.com\", \"username\", \"password\")}", result);
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testgGetDockerConfigJsonFromUsernameRef_withPasswordAndSweepingFunctor() throws Exception {
    ImageDetails imageDetails =
        ImageDetails.builder().password("password").usernameRef("username").registryUrl("https://registry.com").build();

    try (MockedStatic<DockerConfigJsonSecretFunctor> mockedDockerConfigJsonSecretFunctor =
             Mockito.mockStatic(DockerConfigJsonSecretFunctor.class)) {
      mockedDockerConfigJsonSecretFunctor
          .when(()
                    -> DockerConfigJsonSecretFunctor.createAndEncodeDockerConfigJsonSecret(
                        "https://registry.com", "username", "password"))
          .thenReturn("mockedEncodedSecret");

      imagePullSecretUtils.getDockerConfigJsonFromUsernameRef(imageDetails, false, true);

      mockedDockerConfigJsonSecretFunctor.verify(
          ()
              -> DockerConfigJsonSecretFunctor.createAndEncodeDockerConfigJsonSecret(
                  "https://registry.com", "username", "password"),
          times(1));
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testGetDockerConfigJsonFromUsernameRef_withPasswordAndSweepingFunctorFalse() throws Exception {
    ImageDetails imageDetails =
        ImageDetails.builder().password("password").usernameRef("username").registryUrl("https://registry.com").build();

    try (MockedStatic<DockerConfigJsonSecretFunctor> mockedDockerConfigJsonSecretFunctor =
             Mockito.mockStatic(DockerConfigJsonSecretFunctor.class)) {
      mockedDockerConfigJsonSecretFunctor
          .when(()
                    -> DockerConfigJsonSecretFunctor.createAndEncodeDockerConfigJsonSecret(
                        "https://registry.com", "username", "password"))
          .thenReturn("mockedEncodedSecret");

      String result = imagePullSecretUtils.getDockerConfigJsonFromUsernameRef(imageDetails, false, false);

      mockedDockerConfigJsonSecretFunctor.verify(
          ()
              -> DockerConfigJsonSecretFunctor.createAndEncodeDockerConfigJsonSecret(
                  "https://registry.com", "username", "password"),
          times(0));

      assertEquals(
          "${dockerConfigJsonSecretFunc.create(\"https://registry.com\", \"username\", \"password\")}", result);
    }
  }

  private ArtifactOutcome getAcrArtifactOutcome() {
    return AcrArtifactOutcome.builder()
        .subscription(ACR_SUBSCRIPTION_ID)
        .registry(ACR_REGISTRY)
        .repository(ACR_REPOSITORY)
        .tag(ACR_TAG)
        .type(ArtifactSourceConstants.ACR_NAME)
        .connectorRef(ACR_CONNECTOR_REF)
        .image(format("%s/%s:%s", ACR_REGISTRY, ACR_REPOSITORY, ACR_TAG))
        .build();
  }

  private AzureCredentialDTO getAzureCredentialsForSPWithSecret() {
    AzureAuthDTO azureAuthDTO =
        AzureAuthDTO.builder()
            .azureSecretType(AzureSecretType.SECRET_KEY)
            .credentials(AzureClientSecretKeyDTO.builder()
                             .secretKey(SecretRefData.builder().decryptedValue(ACR_SECRET_KEY.toCharArray()).build())
                             .build())
            .build();

    AzureManualDetailsDTO azureManualDetailsDTO =
        AzureManualDetailsDTO.builder().authDTO(azureAuthDTO).clientId(ACR_CLIENT_ID).tenantId(ACR_TENANT_ID).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
        .config(azureManualDetailsDTO)
        .build();
  }

  private AzureCredentialDTO getAzureCredentialsForSPWithCert() {
    AzureAuthDTO azureAuthDTO =
        AzureAuthDTO.builder()
            .azureSecretType(AzureSecretType.KEY_CERT)
            .credentials(AzureClientSecretKeyDTO.builder()
                             .secretKey(SecretRefData.builder().decryptedValue(ACR_SECRET_CERT.toCharArray()).build())
                             .build())
            .build();

    AzureManualDetailsDTO azureManualDetailsDTO =
        AzureManualDetailsDTO.builder().authDTO(azureAuthDTO).clientId(ACR_CLIENT_ID).tenantId(ACR_TENANT_ID).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
        .config(azureManualDetailsDTO)
        .build();
  }

  private AzureCredentialDTO getAzureCredentialsForUserAssignedMSI() {
    AzureUserAssignedMSIAuthDTO azureUserAssignedMSIAuthDTO =
        AzureUserAssignedMSIAuthDTO.builder().clientId(ACR_CLIENT_ID).build();

    AzureMSIAuthDTO azureMSIAuthDTO =
        AzureMSIAuthUADTO.builder()
            .azureManagedIdentityType(AzureManagedIdentityType.USER_ASSIGNED_MANAGED_IDENTITY)
            .credentials(azureUserAssignedMSIAuthDTO)
            .build();

    AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
        AzureInheritFromDelegateDetailsDTO.builder().authDTO(azureMSIAuthDTO).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
        .config(azureInheritFromDelegateDetailsDTO)
        .build();
  }

  private AzureCredentialDTO getAzureCredentialsForSystemAssignedMSI() {
    AzureMSIAuthDTO azureMSIAuthDTO =
        AzureMSIAuthSADTO.builder()
            .azureManagedIdentityType(AzureManagedIdentityType.SYSTEM_ASSIGNED_MANAGED_IDENTITY)
            .build();

    AzureInheritFromDelegateDetailsDTO azureInheritFromDelegateDetailsDTO =
        AzureInheritFromDelegateDetailsDTO.builder().authDTO(azureMSIAuthDTO).build();

    return AzureCredentialDTO.builder()
        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
        .config(azureInheritFromDelegateDetailsDTO)
        .build();
  }

  private Optional<ConnectorResponseDTO> getAzureConnector(AzureCredentialDTO azureCredentialDTO) {
    return Optional.of(ConnectorResponseDTO.builder()
                           .connector(ConnectorInfoDTO.builder()
                                          .connectorConfig(AzureConnectorDTO.builder()
                                                               .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                                               .credential(azureCredentialDTO)
                                                               .build())
                                          .build())
                           .build());
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(ACCOUNT_ID_KEY, ACCOUNT_ID_VALUE)
        .putSetupAbstractions(PROJECT_ID_KEY, PROJECT_ID_VALUE)
        .putSetupAbstractions(ORG_ID_KEY, ORG_ID_VALUE)
        .build();
  }

  private BaseNGAccess getBaseNGAccess(String accountId, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
