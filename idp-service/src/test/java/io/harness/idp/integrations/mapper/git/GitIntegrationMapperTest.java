/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.mapper.git;

import static io.harness.idp.integrations.entities.IntegrationEntity.SubType.GITHUB_DIRECT;
import static io.harness.idp.integrations.entities.IntegrationEntity.SubType.GITHUB_ENTERPRISE;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_AZURE_INTEGRATION;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_AZURE_INTEGRATION_ORGANIZATION1;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_AZURE_INTEGRATION_ORGANIZATION2;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_BITBUCKET_CLOUD_INTEGRATION;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_BITBUCKET_SERVER_INTEGRATION;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_CONNECTOR_IDENTIFIER;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GITHUB_INTEGRATION_DIRECT_HOST;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GITLAB_INTEGRATION;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GIT_INTEGRATION_IDENTIFIER;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_HARNESS_CODE_REPO_INTEGRATION;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_READ_VALIDATION_ERROR;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_READ_VALIDATION_FILE_URL;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_READ_VALIDATION_STATUS;
import static io.harness.idp.integrations.utils.Constants.GITHUB_APP;
import static io.harness.idp.integrations.utils.Constants.IDP_GIT_INTEGRATION_MANAGED_HCR;
import static io.harness.idp.integrations.utils.Constants.MANAGED_TOKEN;
import static io.harness.idp.integrations.utils.Constants.USERNAME_AND_TOKEN;
import static io.harness.idp.integrations.utils.Constants.USERNAME_PASSWORD;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.Constants;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.AzureIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketCloudIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketServerIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitlabIntegrationEntity;
import io.harness.idp.integrations.entities.git.HarnessCodeRepoIntegrationEntity;
import io.harness.idp.integrations.helpers.IntegrationsTestHelper;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.GitIntegrationResponse;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationMapperTest extends CategoryTest {
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseGithubDirectToken() {
    GithubIntegrationEntity githubIntegrationEntity = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_DIRECT, TEST_GITHUB_INTEGRATION_DIRECT_HOST, GitIntegrationEntity.AuthMode.TOKEN);

    GitIntegrationResponse gitIntegrationResponse = GitIntegrationMapper.toResponse(githubIntegrationEntity);

    assertNotNull(gitIntegrationResponse);
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals(Constants.GITHUB, gitIntegrationResponse.getConnectorType());
    assertEquals(GITHUB_DIRECT.toString(), gitIntegrationResponse.getDisplayType());
    assertEquals(TEST_GITHUB_INTEGRATION_DIRECT_HOST, gitIntegrationResponse.getHost());
    assertEquals(USERNAME_AND_TOKEN, gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(TEST_READ_VALIDATION_FILE_URL, gitIntegrationResponse.getValidation().getUrl());
    assertEquals(TEST_READ_VALIDATION_STATUS, gitIntegrationResponse.getValidation().getStatus());
    assertEquals(TEST_READ_VALIDATION_ERROR, gitIntegrationResponse.getValidation().getError());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseGithubDirectGithubApp() {
    GithubIntegrationEntity githubIntegrationEntity = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_DIRECT, TEST_GITHUB_INTEGRATION_DIRECT_HOST, GitIntegrationEntity.AuthMode.GITHUB_APP);

    GitIntegrationResponse gitIntegrationResponse = GitIntegrationMapper.toResponse(githubIntegrationEntity);

    assertNotNull(gitIntegrationResponse);
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals(Constants.GITHUB, gitIntegrationResponse.getConnectorType());
    assertEquals(GITHUB_DIRECT.toString(), gitIntegrationResponse.getDisplayType());
    assertEquals(TEST_GITHUB_INTEGRATION_DIRECT_HOST, gitIntegrationResponse.getHost());
    assertEquals(GITHUB_APP, gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(TEST_READ_VALIDATION_FILE_URL, gitIntegrationResponse.getValidation().getUrl());
    assertEquals(TEST_READ_VALIDATION_STATUS, gitIntegrationResponse.getValidation().getStatus());
    assertEquals(TEST_READ_VALIDATION_ERROR, gitIntegrationResponse.getValidation().getError());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseGithubEnterpriseGithubApp() {
    GithubIntegrationEntity githubIntegrationEntity = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_ENTERPRISE, TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, GitIntegrationEntity.AuthMode.GITHUB_APP);

    GitIntegrationResponse gitIntegrationResponse = GitIntegrationMapper.toResponse(githubIntegrationEntity);

    assertNotNull(gitIntegrationResponse);
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals(Constants.GITHUB, gitIntegrationResponse.getConnectorType());
    assertEquals(GITHUB_ENTERPRISE.toString(), gitIntegrationResponse.getDisplayType());
    assertEquals(TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, gitIntegrationResponse.getHost());
    assertEquals(GITHUB_APP, gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(TEST_READ_VALIDATION_FILE_URL, gitIntegrationResponse.getValidation().getUrl());
    assertEquals(TEST_READ_VALIDATION_STATUS, gitIntegrationResponse.getValidation().getStatus());
    assertEquals(TEST_READ_VALIDATION_ERROR, gitIntegrationResponse.getValidation().getError());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseGithubEnterpriseToken() {
    GithubIntegrationEntity githubIntegrationEntity = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_ENTERPRISE, TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, GitIntegrationEntity.AuthMode.TOKEN);

    GitIntegrationResponse gitIntegrationResponse = GitIntegrationMapper.toResponse(githubIntegrationEntity);

    assertNotNull(gitIntegrationResponse);
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals(Constants.GITHUB, gitIntegrationResponse.getConnectorType());
    assertEquals(GITHUB_ENTERPRISE.toString(), gitIntegrationResponse.getDisplayType());
    assertEquals(TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, gitIntegrationResponse.getHost());
    assertEquals(USERNAME_AND_TOKEN, gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(TEST_READ_VALIDATION_FILE_URL, gitIntegrationResponse.getValidation().getUrl());
    assertEquals(TEST_READ_VALIDATION_STATUS, gitIntegrationResponse.getValidation().getStatus());
    assertEquals(TEST_READ_VALIDATION_ERROR, gitIntegrationResponse.getValidation().getError());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseGitlab() {
    GitlabIntegrationEntity gitlabIntegrationEntity = IntegrationsTestHelper.gitlabIntegrationEntity();

    GitIntegrationResponse gitIntegrationResponse = GitIntegrationMapper.toResponse(gitlabIntegrationEntity);

    assertNotNull(gitIntegrationResponse);
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals(Constants.GITLAB, gitIntegrationResponse.getConnectorType());
    assertEquals(IntegrationEntity.ParentType.GITLAB.toString(), gitIntegrationResponse.getDisplayType());
    assertEquals(TEST_GITLAB_INTEGRATION, gitIntegrationResponse.getHost());
    assertEquals(USERNAME_AND_TOKEN, gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(TEST_READ_VALIDATION_FILE_URL, gitIntegrationResponse.getValidation().getUrl());
    assertEquals(TEST_READ_VALIDATION_STATUS, gitIntegrationResponse.getValidation().getStatus());
    assertEquals(TEST_READ_VALIDATION_ERROR, gitIntegrationResponse.getValidation().getError());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseAzureOrganization1() {
    AzureIntegrationEntity azureIntegrationEntity =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION1);

    GitIntegrationResponse gitIntegrationResponse = GitIntegrationMapper.toResponse(azureIntegrationEntity);

    assertNotNull(gitIntegrationResponse);
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals(Constants.AZURE_REPO, gitIntegrationResponse.getConnectorType());
    assertEquals(IntegrationEntity.ParentType.AZURE.toString(), gitIntegrationResponse.getDisplayType());
    assertEquals(TEST_AZURE_INTEGRATION + "/" + TEST_AZURE_INTEGRATION_ORGANIZATION1, gitIntegrationResponse.getHost());
    assertEquals(USERNAME_AND_TOKEN, gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(TEST_READ_VALIDATION_FILE_URL, gitIntegrationResponse.getValidation().getUrl());
    assertEquals(TEST_READ_VALIDATION_STATUS, gitIntegrationResponse.getValidation().getStatus());
    assertEquals(TEST_READ_VALIDATION_ERROR, gitIntegrationResponse.getValidation().getError());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseAzureOrganization2() {
    AzureIntegrationEntity azureIntegrationEntity =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION2);

    GitIntegrationResponse gitIntegrationResponse = GitIntegrationMapper.toResponse(azureIntegrationEntity);

    assertNotNull(gitIntegrationResponse);
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals(Constants.AZURE_REPO, gitIntegrationResponse.getConnectorType());
    assertEquals(IntegrationEntity.ParentType.AZURE.toString(), gitIntegrationResponse.getDisplayType());
    assertEquals(TEST_AZURE_INTEGRATION + "/" + TEST_AZURE_INTEGRATION_ORGANIZATION2, gitIntegrationResponse.getHost());
    assertEquals(USERNAME_AND_TOKEN, gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(TEST_READ_VALIDATION_FILE_URL, gitIntegrationResponse.getValidation().getUrl());
    assertEquals(TEST_READ_VALIDATION_STATUS, gitIntegrationResponse.getValidation().getStatus());
    assertEquals(TEST_READ_VALIDATION_ERROR, gitIntegrationResponse.getValidation().getError());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseBitbucketCloud() {
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        IntegrationsTestHelper.bitbucketCloudIntegrationEntity();

    GitIntegrationResponse gitIntegrationResponse = GitIntegrationMapper.toResponse(bitbucketCloudIntegrationEntity);

    assertNotNull(gitIntegrationResponse);
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals(Constants.BITBUCKET, gitIntegrationResponse.getConnectorType());
    assertEquals(IntegrationEntity.ParentType.BITBUCKET_CLOUD.toString(), gitIntegrationResponse.getDisplayType());
    assertEquals(TEST_BITBUCKET_CLOUD_INTEGRATION, gitIntegrationResponse.getHost());
    assertEquals(USERNAME_PASSWORD, gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(TEST_READ_VALIDATION_FILE_URL, gitIntegrationResponse.getValidation().getUrl());
    assertEquals(TEST_READ_VALIDATION_STATUS, gitIntegrationResponse.getValidation().getStatus());
    assertEquals(TEST_READ_VALIDATION_ERROR, gitIntegrationResponse.getValidation().getError());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseBitbucketServer() {
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity =
        IntegrationsTestHelper.bitbucketServerIntegrationEntity();

    GitIntegrationResponse gitIntegrationResponse = GitIntegrationMapper.toResponse(bitbucketServerIntegrationEntity);

    assertNotNull(gitIntegrationResponse);
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getIdentifier());
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER, gitIntegrationResponse.getName());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals(Constants.BITBUCKET, gitIntegrationResponse.getConnectorType());
    assertEquals(IntegrationEntity.ParentType.BITBUCKET_SERVER.toString(), gitIntegrationResponse.getDisplayType());
    assertEquals(TEST_BITBUCKET_SERVER_INTEGRATION, gitIntegrationResponse.getHost());
    assertEquals(USERNAME_PASSWORD, gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(TEST_READ_VALIDATION_FILE_URL, gitIntegrationResponse.getValidation().getUrl());
    assertEquals(TEST_READ_VALIDATION_STATUS, gitIntegrationResponse.getValidation().getStatus());
    assertEquals(TEST_READ_VALIDATION_ERROR, gitIntegrationResponse.getValidation().getError());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseHarnessCodeRepoDirectManagedToken() {
    HarnessCodeRepoIntegrationEntity harnessCodeRepoIntegrationEntity =
        IntegrationsTestHelper.harnessCodeRepoIntegrationEntity();

    GitIntegrationResponse gitIntegrationResponse = GitIntegrationMapper.toResponse(harnessCodeRepoIntegrationEntity);

    assertNotNull(gitIntegrationResponse);
    assertEquals(IDP_GIT_INTEGRATION_MANAGED_HCR, gitIntegrationResponse.getIdentifier());
    assertEquals(IDP_GIT_INTEGRATION_MANAGED_HCR, gitIntegrationResponse.getName());
    assertEquals(IDP_GIT_INTEGRATION_MANAGED_HCR, gitIntegrationResponse.getConnectorIdentifier());
    assertEquals(Constants.HARNESS, gitIntegrationResponse.getConnectorType());
    assertEquals(IntegrationEntity.ParentType.HARNESS_CODE_REPO.toString(), gitIntegrationResponse.getDisplayType());
    assertEquals(TEST_HARNESS_CODE_REPO_INTEGRATION, gitIntegrationResponse.getHost());
    assertEquals(MANAGED_TOKEN, gitIntegrationResponse.getAuthType());
    assertFalse(gitIntegrationResponse.isViaDelegate());
    assertEquals(TEST_READ_VALIDATION_FILE_URL, gitIntegrationResponse.getValidation().getUrl());
    assertEquals(TEST_READ_VALIDATION_STATUS, gitIntegrationResponse.getValidation().getStatus());
    assertEquals(TEST_READ_VALIDATION_ERROR, gitIntegrationResponse.getValidation().getError());
    assertFalse(gitIntegrationResponse.isParentDeleted());
    assertTrue(gitIntegrationResponse.isManaged());
    assertEquals(BaseIntegrationResponse.TypeEnum.GIT, gitIntegrationResponse.getType());
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testInvalidAuthMode() {
    GitIntegrationEntity.AuthMode.valueOf("invalid");
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testInvalidParentType() {
    IntegrationEntity.ParentType.valueOf("invalid");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToResponseList() {
    GitlabIntegrationEntity gitlabIntegrationEntity = IntegrationsTestHelper.gitlabIntegrationEntity();
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        IntegrationsTestHelper.bitbucketCloudIntegrationEntity();

    List<IntegrationEntity> integrationEntities = new ArrayList<>();
    integrationEntities.add(gitlabIntegrationEntity);
    integrationEntities.add(bitbucketCloudIntegrationEntity);

    List<GitIntegrationResponse> gitIntegrationResponse = GitIntegrationMapper.toResponse(integrationEntities);

    assertNotNull(gitIntegrationResponse);
    assertFalse(gitIntegrationResponse.isEmpty());
    assertEquals(2, gitIntegrationResponse.size());
  }
}
