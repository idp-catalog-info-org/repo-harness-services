/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.helpers;

import static io.harness.delegate.beans.connector.utils.ConnectorType.HARNESS;
import static io.harness.idp.integrations.utils.Constants.IDP_GIT_INTEGRATION_MANAGED_HCR;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.HarnessAuthenticationDTO;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessType;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.harness.HarnessHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessUsernameTokenDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.AzureIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketCloudIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketServerIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitlabIntegrationEntity;
import io.harness.idp.integrations.entities.git.HarnessCodeRepoIntegrationEntity;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IntegrationsTestHelper extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  public static final String TEST_GIT_INTEGRATION_IDENTIFIER = "idp_testConnector123";
  public static final String TEST_CONNECTOR_IDENTIFIER = "testConnector123";
  public static final String TEST_GITHUB_INTEGRATION_DIRECT_HOST = "github.com";
  public static final String TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST = "ghe.com";
  public static final String TEST_GITLAB_INTEGRATION = "gitlab.com";
  public static final String TEST_AZURE_INTEGRATION = "dev.azure.com";
  public static final String TEST_AZURE_INTEGRATION_ORGANIZATION1 = "organization1";
  public static final String TEST_AZURE_INTEGRATION_ORGANIZATION2 = "organization2";
  public static final String TEST_AZURE_INTEGRATION_ORGANIZATION3 = "organization3";
  public static final String TEST_AZURE_INTEGRATION_ORGANIZATION4 = "organization4";
  public static final String TEST_BITBUCKET_CLOUD_INTEGRATION = "bitbucket.org";
  public static final String TEST_BITBUCKET_SERVER_INTEGRATION = "bitbucket.dev.xyz.com";
  public static final String TEST_HARNESS_CODE_REPO_INTEGRATION = "app.harness.io";
  public static final String TEST_READ_VALIDATION_FILE_URL = "https://xyz.com/abc/def";
  public static final String TEST_READ_VALIDATION_STATUS = "testReadValidationStatus";
  public static final String TEST_READ_VALIDATION_ERROR = "testReadValidationError";
  public static final String TEST_WRITE_VALIDATION_REPO = "testWriteValidationRepo123";
  public static final String TEST_WRITE_VALIDATION_BRANCH = "testWriteValidationBranch123";
  public static final String TEST_WRITE_VALIDATION_PATH = "testWriteValidationPath123";

  public static GithubIntegrationEntity githubIntegrationEntity(
      IntegrationEntity.SubType subType, String host, GitIntegrationEntity.AuthMode authMode) {
    return githubIntegrationEntity(TEST_GIT_INTEGRATION_IDENTIFIER, subType, host, authMode);
  }

  public static GithubIntegrationEntity githubIntegrationEntity(
      String identifier, IntegrationEntity.SubType subType, String host, GitIntegrationEntity.AuthMode authMode) {
    GithubIntegrationEntity githubIntegrationEntity = new GithubIntegrationEntity();

    githubIntegrationEntity.setIdentifier(identifier);
    githubIntegrationEntity.setParentType(IntegrationEntity.ParentType.GITHUB);
    githubIntegrationEntity.setSubType(subType);
    githubIntegrationEntity.setConnectorIdentifier(TEST_CONNECTOR_IDENTIFIER);
    githubIntegrationEntity.setHost(host);
    githubIntegrationEntity.setAuthMode(authMode);

    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation =
        GitIntegrationEntity.ReadPermissionValidation.builder().build();
    readPermissionValidation.setFileUrl(TEST_READ_VALIDATION_FILE_URL);
    readPermissionValidation.setStatus(TEST_READ_VALIDATION_STATUS);
    readPermissionValidation.setError(TEST_READ_VALIDATION_ERROR);

    githubIntegrationEntity.setReadPermissionValidation(readPermissionValidation);

    githubIntegrationEntity.setLastUpdatedAt(1713283668000L);

    return githubIntegrationEntity;
  }

  public static GitlabIntegrationEntity gitlabIntegrationEntity() {
    return gitlabIntegrationEntity(TEST_GIT_INTEGRATION_IDENTIFIER);
  }

  public static GitlabIntegrationEntity gitlabIntegrationEntity(String identifier) {
    GitlabIntegrationEntity gitlabIntegrationEntity = new GitlabIntegrationEntity();

    gitlabIntegrationEntity.setIdentifier(identifier);
    gitlabIntegrationEntity.setParentType(IntegrationEntity.ParentType.GITLAB);
    gitlabIntegrationEntity.setConnectorIdentifier(TEST_CONNECTOR_IDENTIFIER);
    gitlabIntegrationEntity.setHost(TEST_GITLAB_INTEGRATION);
    gitlabIntegrationEntity.setAuthMode(GitIntegrationEntity.AuthMode.TOKEN);

    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation =
        GitIntegrationEntity.ReadPermissionValidation.builder().build();
    readPermissionValidation.setFileUrl(TEST_READ_VALIDATION_FILE_URL);
    readPermissionValidation.setStatus(TEST_READ_VALIDATION_STATUS);
    readPermissionValidation.setError(TEST_READ_VALIDATION_ERROR);

    gitlabIntegrationEntity.setReadPermissionValidation(readPermissionValidation);

    gitlabIntegrationEntity.setLastUpdatedAt(1713283668002L);

    return gitlabIntegrationEntity;
  }

  public static AzureIntegrationEntity azureIntegrationEntity(String organization) {
    return azureIntegrationEntity(TEST_GIT_INTEGRATION_IDENTIFIER, organization);
  }

  public static AzureIntegrationEntity azureIntegrationEntity(String identifier, String organization) {
    AzureIntegrationEntity azureIntegrationEntity = new AzureIntegrationEntity();

    azureIntegrationEntity.setIdentifier(identifier);
    azureIntegrationEntity.setParentType(IntegrationEntity.ParentType.AZURE);
    azureIntegrationEntity.setAdditionalIndexer(organization);
    azureIntegrationEntity.setConnectorIdentifier(TEST_CONNECTOR_IDENTIFIER);
    azureIntegrationEntity.setHost(TEST_AZURE_INTEGRATION);
    azureIntegrationEntity.setAuthMode(GitIntegrationEntity.AuthMode.TOKEN);

    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation =
        GitIntegrationEntity.ReadPermissionValidation.builder().build();
    readPermissionValidation.setFileUrl(TEST_READ_VALIDATION_FILE_URL);
    readPermissionValidation.setStatus(TEST_READ_VALIDATION_STATUS);
    readPermissionValidation.setError(TEST_READ_VALIDATION_ERROR);

    azureIntegrationEntity.setReadPermissionValidation(readPermissionValidation);

    azureIntegrationEntity.setLastUpdatedAt(1713283668004L);

    return azureIntegrationEntity;
  }

  public static BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity() {
    return bitbucketCloudIntegrationEntity(TEST_GIT_INTEGRATION_IDENTIFIER);
  }

  public static BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity(String identifier) {
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity = new BitbucketCloudIntegrationEntity();

    bitbucketCloudIntegrationEntity.setIdentifier(identifier);
    bitbucketCloudIntegrationEntity.setParentType(IntegrationEntity.ParentType.BITBUCKET_CLOUD);
    bitbucketCloudIntegrationEntity.setConnectorIdentifier(TEST_CONNECTOR_IDENTIFIER);
    bitbucketCloudIntegrationEntity.setHost(TEST_BITBUCKET_CLOUD_INTEGRATION);
    bitbucketCloudIntegrationEntity.setAuthMode(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD);

    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation =
        GitIntegrationEntity.ReadPermissionValidation.builder().build();
    readPermissionValidation.setFileUrl(TEST_READ_VALIDATION_FILE_URL);
    readPermissionValidation.setStatus(TEST_READ_VALIDATION_STATUS);
    readPermissionValidation.setError(TEST_READ_VALIDATION_ERROR);

    bitbucketCloudIntegrationEntity.setReadPermissionValidation(readPermissionValidation);

    bitbucketCloudIntegrationEntity.setLastUpdatedAt(1713283668006L);

    return bitbucketCloudIntegrationEntity;
  }

  public static BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity() {
    return bitbucketServerIntegrationEntity(TEST_GIT_INTEGRATION_IDENTIFIER);
  }

  public static BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity(String identifier) {
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity = new BitbucketServerIntegrationEntity();

    bitbucketServerIntegrationEntity.setIdentifier(identifier);
    bitbucketServerIntegrationEntity.setParentType(IntegrationEntity.ParentType.BITBUCKET_SERVER);
    bitbucketServerIntegrationEntity.setConnectorIdentifier(TEST_CONNECTOR_IDENTIFIER);
    bitbucketServerIntegrationEntity.setHost(TEST_BITBUCKET_SERVER_INTEGRATION);
    bitbucketServerIntegrationEntity.setAuthMode(GitIntegrationEntity.AuthMode.USERNAME_PASSWORD);

    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation =
        GitIntegrationEntity.ReadPermissionValidation.builder().build();
    readPermissionValidation.setFileUrl(TEST_READ_VALIDATION_FILE_URL);
    readPermissionValidation.setStatus(TEST_READ_VALIDATION_STATUS);
    readPermissionValidation.setError(TEST_READ_VALIDATION_ERROR);

    bitbucketServerIntegrationEntity.setReadPermissionValidation(readPermissionValidation);

    bitbucketServerIntegrationEntity.setLastUpdatedAt(1713283668008L);

    return bitbucketServerIntegrationEntity;
  }

  public static HarnessCodeRepoIntegrationEntity harnessCodeRepoIntegrationEntity() {
    HarnessCodeRepoIntegrationEntity harnessCodeRepoIntegrationEntity = new HarnessCodeRepoIntegrationEntity();

    harnessCodeRepoIntegrationEntity.setIdentifier(IDP_GIT_INTEGRATION_MANAGED_HCR);
    harnessCodeRepoIntegrationEntity.setParentType(IntegrationEntity.ParentType.HARNESS_CODE_REPO);
    harnessCodeRepoIntegrationEntity.setConnectorIdentifier(IDP_GIT_INTEGRATION_MANAGED_HCR);
    harnessCodeRepoIntegrationEntity.setHost(TEST_HARNESS_CODE_REPO_INTEGRATION);
    harnessCodeRepoIntegrationEntity.setAuthMode(GitIntegrationEntity.AuthMode.MANAGED_TOKEN);

    GitIntegrationEntity.ReadPermissionValidation readPermissionValidation =
        GitIntegrationEntity.ReadPermissionValidation.builder().build();
    readPermissionValidation.setFileUrl(TEST_READ_VALIDATION_FILE_URL);
    readPermissionValidation.setStatus(TEST_READ_VALIDATION_STATUS);
    readPermissionValidation.setError(TEST_READ_VALIDATION_ERROR);

    harnessCodeRepoIntegrationEntity.setReadPermissionValidation(readPermissionValidation);

    harnessCodeRepoIntegrationEntity.setLastUpdatedAt(1713283668008L);
    harnessCodeRepoIntegrationEntity.setManaged(true);

    return harnessCodeRepoIntegrationEntity;
  }

  public static ConnectorDTO githubConnectorDTORepoToken() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name(TEST_CONNECTOR_IDENTIFIER)
                .identifier(TEST_CONNECTOR_IDENTIFIER)
                .connectorType(ConnectorType.GITHUB)
                .connectorConfig(
                    GithubConnectorDTO.builder()
                        .url("https://github.com/harness/harness-core.git")
                        .connectionType(GitConnectionType.REPO)
                        .authentication(
                            GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(
                                    GithubHttpCredentialsDTO.builder()
                                        .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                        .httpCredentialsSpec(GithubUsernameTokenDTO.builder()
                                                                 .username("username")
                                                                 .tokenRef(SecretRefData.builder()
                                                                               .identifier("gitToken")
                                                                               .scope(Scope.ACCOUNT)
                                                                               .decryptedValue("token".toCharArray())
                                                                               .build())
                                                                 .build())
                                        .build())
                                .build())

                        .build())
                .build())
        .build();
  }

  public static ConnectorDTO gitlabConnectorDTORepoToken() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name(TEST_CONNECTOR_IDENTIFIER)
                .identifier(TEST_CONNECTOR_IDENTIFIER)
                .connectorType(ConnectorType.GITLAB)
                .connectorConfig(
                    GitlabConnectorDTO.builder()
                        .url("https://gitlab.com/harness/harness-core.git")
                        .connectionType(GitConnectionType.REPO)
                        .authentication(
                            GitlabAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(
                                    GitlabHttpCredentialsDTO.builder()
                                        .type(GitlabHttpAuthenticationType.USERNAME_AND_TOKEN)
                                        .httpCredentialsSpec(GitlabUsernameTokenDTO.builder()
                                                                 .username("username")
                                                                 .tokenRef(SecretRefData.builder()
                                                                               .identifier("gitToken")
                                                                               .scope(Scope.ACCOUNT)
                                                                               .decryptedValue("token".toCharArray())
                                                                               .build())
                                                                 .build())
                                        .build())
                                .build())

                        .build())
                .build())
        .build();
  }

  public static ConnectorDTO azureRepoConnectorDTORepoToken() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name(TEST_CONNECTOR_IDENTIFIER)
                .identifier(TEST_CONNECTOR_IDENTIFIER)
                .connectorType(ConnectorType.AZURE_REPO)
                .connectorConfig(
                    AzureRepoConnectorDTO.builder()
                        .url("https://automation-cdc@dev.azure.com/automation-cdc/IDP/_git/IDPAUTO")
                        .connectionType(AzureRepoConnectionTypeDTO.REPO)
                        .authentication(
                            AzureRepoAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(
                                    AzureRepoHttpCredentialsDTO.builder()
                                        .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                                        .httpCredentialsSpec(AzureRepoUsernameTokenDTO.builder()
                                                                 .username("username")
                                                                 .tokenRef(SecretRefData.builder()
                                                                               .identifier("gitToken")
                                                                               .scope(Scope.ACCOUNT)
                                                                               .decryptedValue("token".toCharArray())
                                                                               .build())
                                                                 .build())
                                        .build())
                                .build())

                        .build())
                .build())
        .build();
  }

  public static ConnectorDTO bitbucketCloudRepoConnectorDTORepoPassword() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name(TEST_CONNECTOR_IDENTIFIER)
                .identifier(TEST_CONNECTOR_IDENTIFIER)
                .connectorType(ConnectorType.BITBUCKET)
                .connectorConfig(
                    BitbucketConnectorDTO.builder()
                        .url("https://s_sathish@bitbucket.org/s_sathish/sathish-test.git")
                        .connectionType(GitConnectionType.REPO)
                        .authentication(
                            BitbucketAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(BitbucketHttpCredentialsDTO.builder()
                                                 .type(BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(
                                                     BitbucketUsernamePasswordDTO.builder()
                                                         .username("username")
                                                         .passwordRef(SecretRefData.builder()
                                                                          .identifier("gitPassword")
                                                                          .scope(Scope.ACCOUNT)
                                                                          .decryptedValue("password".toCharArray())
                                                                          .build())
                                                         .build())
                                                 .build())
                                .build())

                        .build())
                .build())
        .build();
  }

  public static ConnectorDTO bitbucketServerRepoConnectorDTORepoPassword() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name(TEST_CONNECTOR_IDENTIFIER)
                .identifier(TEST_CONNECTOR_IDENTIFIER)
                .connectorType(ConnectorType.BITBUCKET)
                .connectorConfig(
                    BitbucketConnectorDTO.builder()
                        .url("https://bitbucket.dev.harness.io/scm/har/idp.git")
                        .connectionType(GitConnectionType.REPO)
                        .authentication(
                            BitbucketAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(BitbucketHttpCredentialsDTO.builder()
                                                 .type(BitbucketHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(
                                                     BitbucketUsernamePasswordDTO.builder()
                                                         .username("username")
                                                         .passwordRef(SecretRefData.builder()
                                                                          .identifier("gitPassword")
                                                                          .scope(Scope.ACCOUNT)
                                                                          .decryptedValue("password".toCharArray())
                                                                          .build())
                                                         .build())
                                                 .build())
                                .build())

                        .build())
                .build())
        .build();
  }

  public static ConnectorDTO harnessCodeRepoConnectorDto() {
    ConnectorDTO connectorDTO = ConnectorDTO.builder().build();
    ConnectorInfoDTO connectorInfoDTO = new ConnectorInfoDTO();
    connectorInfoDTO.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    connectorInfoDTO.setIdentifier(IDP_GIT_INTEGRATION_MANAGED_HCR);
    connectorInfoDTO.setConnectorType(HARNESS);
    HarnessConnectorDTO harnessConnectorDTO =
        HarnessConnectorDTO.builder()
            .url("https://vanity.harness.io")
            .authentication(
                HarnessAuthenticationDTO.builder()
                    .authType(GitAuthType.HTTP)
                    .credentials(
                        HarnessHttpCredentialsDTO.builder()
                            .type(HarnessHttpAuthenticationType.USERNAME_AND_TOKEN)
                            .httpCredentialsSpec(
                                HarnessUsernameTokenDTO.builder()
                                    .username(null)
                                    .tokenRef(
                                        SecretRefData.builder().identifier(IDP_GIT_INTEGRATION_MANAGED_HCR).build())
                                    .build())
                            .build())
                    .build())
            .apiAccess(HarnessApiAccessDTO.builder()
                           .type(HarnessApiAccessType.TOKEN)
                           .spec(HarnessTokenSpecDTO.builder()
                                     .tokenRef(SecretRefData.builder().identifier("tokenIdentifier").build())
                                     .build())
                           .build())
            .build();
    connectorInfoDTO.setConnectorConfig(harnessConnectorDTO);
    connectorDTO.setConnectorInfo(connectorInfoDTO);
    return connectorDTO;
  }
}
