/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.iac;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.ci.pod.SSHKeyDetails;
import io.harness.delegate.beans.connector.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.GitConfigDTO;
import io.harness.delegate.beans.connector.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.encryption.SecretRefData;
import io.harness.impl.scm.ScmGitProviderHelper;
import io.harness.ng.BaseUrls;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Slf4j
public class IacTerraformModulesHelperTest extends CategoryTest {
  @Inject @Mock SecretUtils secretUtils;
  @Mock @Inject DecryptionHelper decryptionHelper;
  @Mock @Inject NextGenConfiguration nextGenConfiguration;
  @InjectMocks private IacTerraformModulesHelper helper;
  @Mock ScmGitProviderHelper scmGitProviderHelper;
  private ScmConnector scmConnector;

  @Before
  public void setUp() {
    helper = new IacTerraformModulesHelper();
    scmConnector = mock(ScmConnector.class);
    MockitoAnnotations.openMocks(this);
  }
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractSSHSecretGithub() {
    GithubConnectorDTO githubConnector = mock(GithubConnectorDTO.class);
    when(githubConnector.getConnectorType()).thenReturn(ConnectorType.GITHUB);
    CreateMocksForProvider("GITHUB", githubConnector, "SSH");
    char[] result = helper.extractSHHSecret("accountId", githubConnector);
    assertEquals("ssh-key", new String(result));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractSSHSecretGitLab() {
    GitlabConnectorDTO githubConnector = mock(GitlabConnectorDTO.class);
    when(githubConnector.getConnectorType()).thenReturn(ConnectorType.GITLAB);
    CreateMocksForProvider("GITLAB", githubConnector, "SSH");
    char[] result = helper.extractSHHSecret("accountId", githubConnector);
    assertEquals("ssh-key", new String(result));
  }
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractSSHSecretBitbucket() {
    BitbucketConnectorDTO githubConnector = mock(BitbucketConnectorDTO.class);
    when(githubConnector.getConnectorType()).thenReturn(ConnectorType.BITBUCKET);
    CreateMocksForProvider("BITBUCKET", githubConnector, "SSH");
    char[] result = helper.extractSHHSecret("accountId", githubConnector);
    assertEquals("ssh-key", new String(result));
  }
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractSSHSecretGit() {
    GitConfigDTO githubConnector = mock(GitConfigDTO.class);
    when(githubConnector.getConnectorType()).thenReturn(ConnectorType.GIT);
    CreateMocksForProvider("GIT", githubConnector, "SSH");
    char[] result = helper.extractSHHSecret("accountId", githubConnector);
    assertEquals("ssh-key", new String(result));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractSSHSecretAzureCode() {
    AzureRepoConnectorDTO githubConnector = mock(AzureRepoConnectorDTO.class);
    when(githubConnector.getConnectorType()).thenReturn(ConnectorType.AZURE_REPO);
    CreateMocksForProvider("AZURE_REPO", githubConnector, "SSH");
    char[] result = helper.extractSHHSecret("accountId", githubConnector);
    assertEquals("ssh-key", new String(result));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractSSHSecretEmptyWhenHTTP() {
    GithubConnectorDTO githubConnector = mock(GithubConnectorDTO.class);
    when(githubConnector.getConnectorType()).thenReturn(ConnectorType.GITHUB);
    CreateMocksForProvider("GITHUB", githubConnector, "HTTP");
    char[] result = helper.extractSHHSecret("accountId", githubConnector);
    assertEquals("", new String(result));
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractDomainGithub() {
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.REPO)
                                             .url("https://github.com/user/repo.git")
                                             .build();
    String domain = helper.extractDomain("", githubConnector);
    assertEquals("github.com", domain);
    GithubConnectorDTO githubConnector2 =
        GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url("git@github.com:user/repo.git").build();
    String domain2 = helper.extractDomain("", githubConnector2);
    assertEquals("github.com", domain2);
    GithubConnectorDTO githubConnector3 = GithubConnectorDTO.builder()
                                              .connectionType(GitConnectionType.REPO)
                                              .url("https://github.company.com/organization/repository.git")
                                              .build();
    String domain3 = helper.extractDomain("", githubConnector3);
    assertEquals("github.company.com", domain3);
    GithubConnectorDTO githubConnector4 = GithubConnectorDTO.builder()
                                              .connectionType(GitConnectionType.REPO)
                                              .url("git@github.company.com:organization/repository.git")
                                              .build();
    String domain4 = helper.extractDomain("", githubConnector4);
    assertEquals("github.company.com", domain4);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractDomainGitlab() {
    GitlabConnectorDTO gitConnector = GitlabConnectorDTO.builder()
                                          .connectionType(GitConnectionType.REPO)
                                          .url("https://gitlab.com/user/repo.git")
                                          .build();
    String domain = helper.extractDomain("", gitConnector);
    assertEquals("gitlab.com", domain);
    GitlabConnectorDTO gitConnector2 = GitlabConnectorDTO.builder()
                                           .connectionType(GitConnectionType.REPO)
                                           .url("git@gitlab.com:organization/repo.git")
                                           .build();
    String domain2 = helper.extractDomain("", gitConnector2);
    assertEquals("gitlab.com", domain2);
    GitlabConnectorDTO gitConnector3 = GitlabConnectorDTO.builder()
                                           .connectionType(GitConnectionType.REPO)
                                           .url("https://gitlab.company.com/organization/repo.git")
                                           .build();
    String domain3 = helper.extractDomain("", gitConnector3);
    assertEquals("gitlab.company.com", domain3);
    GitlabConnectorDTO gitConnector4 = GitlabConnectorDTO.builder()
                                           .connectionType(GitConnectionType.REPO)
                                           .url("git@gitlab.company.com:organization/repo.git")
                                           .build();
    String domain4 = helper.extractDomain("", gitConnector4);
    assertEquals("gitlab.company.com", domain4);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractDomainBitbucket() {
    BitbucketConnectorDTO gitConnector = BitbucketConnectorDTO.builder()
                                             .connectionType(GitConnectionType.REPO)
                                             .url("https://username@bitbucket.org/organization/terraform-aws-sqs.git")
                                             .build();
    String domain = helper.extractDomain("", gitConnector);
    assertEquals("bitbucket.org", domain);
    BitbucketConnectorDTO gitConnector2 = BitbucketConnectorDTO.builder()
                                              .connectionType(GitConnectionType.REPO)
                                              .url("git@bitbucket.org:organization/terraform-aws-sqs.git")
                                              .build();
    String domain2 = helper.extractDomain("", gitConnector2);
    assertEquals("bitbucket.org", domain2);
    BitbucketConnectorDTO gitConnector3 = BitbucketConnectorDTO.builder()
                                              .connectionType(GitConnectionType.REPO)
                                              .url("https://bitbucket.company.com/scm/project/repo.git")
                                              .build();
    String domain3 = helper.extractDomain("", gitConnector3);
    assertEquals("bitbucket.company.com", domain3);
    BitbucketConnectorDTO gitConnector4 = BitbucketConnectorDTO.builder()
                                              .connectionType(GitConnectionType.REPO)
                                              .url("git@bitbucket.company.com:7999/project/repo.git")
                                              .build();
    String domain4 = helper.extractDomain("", gitConnector4);
    assertEquals("bitbucket.company.com", domain4);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractDomainAzureRepo() {
    AzureRepoConnectorDTO gitConnector = AzureRepoConnectorDTO.builder()
                                             .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                             .url(" https://dev.azure.com/organization/project/_git/repository")
                                             .build();
    String domain = helper.extractDomain("", gitConnector);
    assertEquals("dev.azure.com", domain);
    AzureRepoConnectorDTO gitConnector2 = AzureRepoConnectorDTO.builder()
                                              .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                              .url("git@ssh.dev.azure.com:v3/organization/project/repository")
                                              .build();
    String domain2 = helper.extractDomain("", gitConnector2);
    assertEquals("ssh.dev.azure.com", domain2);
    AzureRepoConnectorDTO gitConnector3 = AzureRepoConnectorDTO.builder()
                                              .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                              .url("https://azure.company.com/organization/project/_git/repository")
                                              .build();
    String domain3 = helper.extractDomain("", gitConnector3);
    assertEquals("azure.company.com", domain3);
    AzureRepoConnectorDTO gitConnector4 = AzureRepoConnectorDTO.builder()
                                              .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                              .url("git@ssh.azure.company.com:v3/organization/project/repository")
                                              .build();
    String domain4 = helper.extractDomain("", gitConnector4);
    assertEquals("ssh.azure.company.com", domain4);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testExtractPort() {
    GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                .connectionType(GitConnectionType.REPO)
                                                .url("git@gitlab.com:organization/repo.git")
                                                .build();
    int port = helper.extractPort("", gitlabConnectorDTO);
    assertEquals(22, port);
    BitbucketConnectorDTO bitbucketConnectorDTO = BitbucketConnectorDTO.builder()
                                                      .connectionType(GitConnectionType.REPO)
                                                      .url("git@bitbucket.org:organization/terraform-aws-sqs.git")
                                                      .build();
    int port2 = helper.extractPort("", bitbucketConnectorDTO);
    assertEquals(22, port2);
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url("git@github.com:user/repo.git").build();
    int port3 = helper.extractPort("", githubConnectorDTO);
    assertEquals(22, port3);
    AzureRepoConnectorDTO azureRepoConnectorDTO = AzureRepoConnectorDTO.builder()
                                                      .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                      .url("git@ssh.dev.azure.com:v3/organization/project/repository")
                                                      .build();
    int port4 = helper.extractPort("", azureRepoConnectorDTO);
    assertEquals(22, port4);
    BitbucketConnectorDTO bitbucketEnterpriseConnectorDTO = BitbucketConnectorDTO.builder()
                                                                .connectionType(GitConnectionType.REPO)
                                                                .url("git@bitbucket.company.com:7999/project/repo.git")
                                                                .build();
    int port5 = helper.extractPort("", bitbucketEnterpriseConnectorDTO);
    assertEquals(7999, port5);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetTagDownloadURLAzureRepo() {
    AzureRepoConnectorDTO azureRepoConnector = AzureRepoConnectorDTO.builder()
                                                   .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                   .url("https://dev.azure.com/project/testiacm/_git/terraform-aws-sqs")
                                                   .build();
    when(scmGitProviderHelper.getRepoOwner(azureRepoConnector)).thenReturn("project");
    when(scmGitProviderHelper.getRepoName(azureRepoConnector)).thenReturn("testiacm/_git/terraform-aws-sqs");
    when(scmGitProviderHelper.getSlug(azureRepoConnector)).thenReturn("terraform-aws-sqs");

    String downloadUrl = helper.getTagDownloadURL(azureRepoConnector, "hash123", "");
    assertEquals("git::https://dev.azure.com/project/testiacm/_git/terraform-aws-sqs?ref=hash123", downloadUrl);
  }
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetTagDownloadURLAzureRepoSSH() {
    AzureRepoConnectorDTO azureRepoConnector = AzureRepoConnectorDTO.builder()
                                                   .connectionType(AzureRepoConnectionTypeDTO.REPO)
                                                   .url("git@ssh.dev.azure.com:v3/project/testiacm/terraform-aws-sqs")
                                                   .build();
    when(scmGitProviderHelper.getRepoOwner(azureRepoConnector)).thenReturn("project");
    when(scmGitProviderHelper.getRepoName(azureRepoConnector)).thenReturn("testiacm/_git/terraform-aws-sqs");
    when(scmGitProviderHelper.getSlug(azureRepoConnector)).thenReturn("terraform-aws-sqs");

    String downloadUrl = helper.getTagDownloadURL(azureRepoConnector, "hash123", "");
    assertEquals("git::ssh://git@ssh.dev.azure.com/v3/project/testiacm/terraform-aws-sqs?ref=hash123", downloadUrl);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testGetTagDownloadURLHarness0Repo() {
    HarnessConnectorDTO harnessConnectorDTO = HarnessConnectorDTO.builder()
                                                  .gitBaseUrl("https://git0.harness.io")
                                                  .slug("account/org/project/terraform-grafana-slo.git")
                                                  .build();

    BaseUrls baseUrls = mock(BaseUrls.class);
    when(baseUrls.getHarnessCodeGitUrl()).thenReturn("https://git0.harness.io");
    when(nextGenConfiguration.getBaseUrls()).thenReturn(baseUrls);

    when(scmGitProviderHelper.getSlug(harnessConnectorDTO)).thenReturn("account/org/project/terraform-grafana-slo.git");

    String downloadUrl = helper.getTagDownloadURL(harnessConnectorDTO, "hash123", "");
    assertEquals("git::https://git0.harness.io/account/org/project/terraform-grafana-slo.git?ref=hash123", downloadUrl);
  }

  private void CreateMocksForProvider(String provider, ConnectorConfigDTO connectorConfigDTO, String protocol) {
    SecretRefData secretRefData = mock(SecretRefData.class);
    SSHKeyDetails sshKeyDetails = mock(SSHKeyDetails.class);
    SSHKeyReferenceCredentialDTO sshKeyReferenceCredentialDTO = mock(SSHKeyReferenceCredentialDTO.class);

    switch (provider) {
      case "GITHUB" -> {
        GithubConnectorDTO githubConnector = (GithubConnectorDTO) connectorConfigDTO;
        GithubAuthenticationDTO githubAuthentication = mock(GithubAuthenticationDTO.class);
        GithubSshCredentialsDTO githubSshCredentials = mock(GithubSshCredentialsDTO.class);
        when(githubConnector.getAuthentication()).thenReturn(githubAuthentication);
        if (protocol.equals("HTTP")) {
          when(githubAuthentication.getAuthType()).thenReturn(GitAuthType.HTTP);
        } else if (protocol.equals("SSH")) {
          when(githubAuthentication.getAuthType()).thenReturn(GitAuthType.SSH);
          when(githubAuthentication.getCredentials()).thenReturn(githubSshCredentials);
          when(githubSshCredentials.getSshKeyRef()).thenReturn(secretRefData);
          when(secretUtils.getSshKey(any(), any())).thenReturn(sshKeyDetails);
          when(decryptionHelper.decrypt(any(), any())).thenReturn(sshKeyReferenceCredentialDTO);
          when(sshKeyReferenceCredentialDTO.getKey()).thenReturn(secretRefData);
          when(secretRefData.getDecryptedValue()).thenReturn("ssh-key".toCharArray());
        }
      }
      case "BITBUCKET" -> {
        BitbucketConnectorDTO bitbucketConnector = (BitbucketConnectorDTO) connectorConfigDTO;
        BitbucketAuthenticationDTO bitbucketAuthentication = mock(BitbucketAuthenticationDTO.class);
        BitbucketSshCredentialsDTO bitbucketSshCredentials = mock(BitbucketSshCredentialsDTO.class);
        when(bitbucketConnector.getAuthentication()).thenReturn(bitbucketAuthentication);
        if (protocol.equals("HTTP")) {
          when(bitbucketConnector.getAuthentication().getAuthType()).thenReturn(GitAuthType.HTTP);
        } else if (protocol.equals("SSH")) {
          when(bitbucketAuthentication.getAuthType()).thenReturn(GitAuthType.SSH);
          when(bitbucketAuthentication.getCredentials()).thenReturn(bitbucketSshCredentials);
          when(bitbucketSshCredentials.getSshKeyRef()).thenReturn(secretRefData);
          when(secretUtils.getSshKey(any(), any())).thenReturn(sshKeyDetails);
          when(decryptionHelper.decrypt(any(), any())).thenReturn(sshKeyReferenceCredentialDTO);
          when(sshKeyReferenceCredentialDTO.getKey()).thenReturn(secretRefData);
          when(secretRefData.getDecryptedValue()).thenReturn("ssh-key".toCharArray());
        }
      }
      case "GITLAB" -> {
        GitlabConnectorDTO gitlabConnector = (GitlabConnectorDTO) connectorConfigDTO;
        GitlabAuthenticationDTO gitlabAuthentication = mock(GitlabAuthenticationDTO.class);
        GitlabSshCredentialsDTO gitlabSshCredentials = mock(GitlabSshCredentialsDTO.class);
        when(gitlabConnector.getAuthentication()).thenReturn(gitlabAuthentication);
        if (protocol.equals("HTTP")) {
          when(gitlabConnector.getAuthentication().getAuthType()).thenReturn(GitAuthType.HTTP);
        } else if (protocol.equals("SSH")) {
          when(gitlabAuthentication.getAuthType()).thenReturn(GitAuthType.SSH);
          when(gitlabAuthentication.getCredentials()).thenReturn(gitlabSshCredentials);
          when(gitlabSshCredentials.getSshKeyRef()).thenReturn(secretRefData);
          when(secretUtils.getSshKey(any(), any())).thenReturn(sshKeyDetails);
          when(decryptionHelper.decrypt(any(), any())).thenReturn(sshKeyReferenceCredentialDTO);
          when(sshKeyReferenceCredentialDTO.getKey()).thenReturn(secretRefData);
          when(secretRefData.getDecryptedValue()).thenReturn("ssh-key".toCharArray());
        }
      }
      case "AZURE_REPO" -> {
        AzureRepoConnectorDTO azureRepoConnector = (AzureRepoConnectorDTO) connectorConfigDTO;
        AzureRepoAuthenticationDTO azureRepoAuthentication = mock(AzureRepoAuthenticationDTO.class);
        AzureRepoSshCredentialsDTO azureRepoSshCredentials = mock(AzureRepoSshCredentialsDTO.class);
        when(azureRepoConnector.getAuthentication()).thenReturn(azureRepoAuthentication);
        if (protocol.equals("HTTP")) {
          when(azureRepoConnector.getAuthentication().getAuthType()).thenReturn(GitAuthType.HTTP);
        } else if (protocol.equals("SSH")) {
          when(azureRepoAuthentication.getAuthType()).thenReturn(GitAuthType.SSH);
          when(azureRepoAuthentication.getCredentials()).thenReturn(azureRepoSshCredentials);
          when(secretUtils.getSshKey(any(), any())).thenReturn(sshKeyDetails);
          when(decryptionHelper.decrypt(any(), any())).thenReturn(sshKeyReferenceCredentialDTO);
          when(sshKeyReferenceCredentialDTO.getKey()).thenReturn(secretRefData);
          when(secretRefData.getDecryptedValue()).thenReturn("ssh-key".toCharArray());
        }
      }
      case "GIT" -> {
        GitConfigDTO gitConfig = (GitConfigDTO) connectorConfigDTO;
        GitAuthenticationDTO gitAuthentication = mock(GitAuthenticationDTO.class);
        GitSSHAuthenticationDTO gitSSHAuthenticationDTO = mock(GitSSHAuthenticationDTO.class);
        when(gitConfig.getGitAuth()).thenReturn(gitAuthentication);
        if (protocol.equals("HTTP")) {
          when(gitConfig.getGitAuthType()).thenReturn(GitAuthType.HTTP);
        } else if (protocol.equals("SSH")) {
          when(gitConfig.getGitAuthType()).thenReturn(GitAuthType.SSH);
          when(gitConfig.getGitAuth()).thenReturn(gitSSHAuthenticationDTO);
          when(gitSSHAuthenticationDTO.getEncryptedSshKey()).thenReturn(secretRefData);
          when(secretUtils.getSshKey(any(), any())).thenReturn(sshKeyDetails);
          when(decryptionHelper.decrypt(any(), any())).thenReturn(sshKeyReferenceCredentialDTO);
          when(sshKeyReferenceCredentialDTO.getKey()).thenReturn(secretRefData);
          when(secretRefData.getDecryptedValue()).thenReturn("ssh-key".toCharArray());
        }
      }
      default -> {
      }
    }
  }
}
