/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.entities;

import static io.harness.idp.integrations.entities.IntegrationEntity.SubType.GITHUB_DIRECT;
import static io.harness.idp.integrations.entities.IntegrationEntity.SubType.GITHUB_ENTERPRISE;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_AZURE_INTEGRATION_ORGANIZATION1;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_AZURE_INTEGRATION_ORGANIZATION2;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GITHUB_INTEGRATION_DIRECT_HOST;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.integrations.entities.git.AzureIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketCloudIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketServerIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitlabIntegrationEntity;
import io.harness.idp.integrations.helpers.IntegrationsTestHelper;
import io.harness.mongo.index.MongoIndex;
import io.harness.rule.Owner;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class IntegrationEntityTest extends CategoryTest {
  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIntegrationEntityMongoIndexes() {
    List<MongoIndex> integrationEntityMongoIndexes = IntegrationEntity.mongoIndexes();

    assertThat(integrationEntityMongoIndexes.size()).isEqualTo(3);
    assertThat(integrationEntityMongoIndexes.stream().map(MongoIndex::getName).collect(Collectors.toSet()).size())
        .isEqualTo(3);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetConfigId() {
    GithubIntegrationEntity githubIntegrationEntityToken = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_DIRECT, TEST_GITHUB_INTEGRATION_DIRECT_HOST, GitIntegrationEntity.AuthMode.TOKEN);
    GithubIntegrationEntity githubIntegrationEntityGithubApp = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_DIRECT, TEST_GITHUB_INTEGRATION_DIRECT_HOST, GitIntegrationEntity.AuthMode.GITHUB_APP);
    GithubIntegrationEntity githubIntegrationEntityEnterpriseGithubApp = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_ENTERPRISE, TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, GitIntegrationEntity.AuthMode.GITHUB_APP);
    GithubIntegrationEntity githubIntegrationEntityEnterpriseToken = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_ENTERPRISE, TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, GitIntegrationEntity.AuthMode.TOKEN);
    GitlabIntegrationEntity gitlabIntegrationEntity = IntegrationsTestHelper.gitlabIntegrationEntity();
    AzureIntegrationEntity azureIntegrationEntityOrganization1 =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION1);
    AzureIntegrationEntity azureIntegrationEntityOrganization2 =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION2);
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        IntegrationsTestHelper.bitbucketCloudIntegrationEntity();
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity =
        IntegrationsTestHelper.bitbucketServerIntegrationEntity();

    assertEquals("GITHUB_GITHUB_DIRECT", githubIntegrationEntityToken.getConfigId());
    assertEquals("GITHUB_GITHUB_DIRECT", githubIntegrationEntityGithubApp.getConfigId());
    assertEquals("GITHUB_GITHUB_ENTERPRISE", githubIntegrationEntityEnterpriseGithubApp.getConfigId());
    assertEquals("GITHUB_GITHUB_ENTERPRISE", githubIntegrationEntityEnterpriseToken.getConfigId());
    assertEquals("GITLAB", gitlabIntegrationEntity.getConfigId());
    assertEquals("AZURE_ORGANIZATION1", azureIntegrationEntityOrganization1.getConfigId());
    assertEquals("AZURE_ORGANIZATION2", azureIntegrationEntityOrganization2.getConfigId());
    assertEquals("BITBUCKET_CLOUD", bitbucketCloudIntegrationEntity.getConfigId());
    assertEquals("BITBUCKET_SERVER", bitbucketServerIntegrationEntity.getConfigId());
  }
}
