/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.events;

import static io.harness.audit.ResourceTypeConstants.IDP_GIT_INTEGRATIONS;
import static io.harness.idp.integrations.events.GitIntegrationDeleteEvent.GIT_INTEGRATION_DELETED;
import static io.harness.ng.core.ResourceConstants.LABEL_KEY_RESOURCE_NAME;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.integrations.entities.git.AzureIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketCloudIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketServerIntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitlabIntegrationEntity;
import io.harness.ng.core.AccountScope;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationDeleteEventTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_GIT_INTEGRATION_IDENTIFIER = "idp_testConnector123";
  static final String TEST_ACCOUNT_SCOPE = "account";

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWithGithubIntegrationEntity() {
    GithubIntegrationEntity githubIntegrationEntity = GithubIntegrationEntity.builder()
                                                          .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                          .identifier(TEST_GIT_INTEGRATION_IDENTIFIER)
                                                          .build();

    GitIntegrationDeleteEvent gitIntegrationDeleteEvent =
        new GitIntegrationDeleteEvent(TEST_ACCOUNT_IDENTIFIER, githubIntegrationEntity);
    validateResult(gitIntegrationDeleteEvent);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWithGitlabIntegrationEntity() {
    GitlabIntegrationEntity gitlabIntegrationEntity = GitlabIntegrationEntity.builder()
                                                          .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                          .identifier(TEST_GIT_INTEGRATION_IDENTIFIER)
                                                          .build();

    GitIntegrationDeleteEvent gitIntegrationDeleteEvent =
        new GitIntegrationDeleteEvent(TEST_ACCOUNT_IDENTIFIER, gitlabIntegrationEntity);
    validateResult(gitIntegrationDeleteEvent);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWithAzureIntegrationEntity() {
    AzureIntegrationEntity azureIntegrationEntity = AzureIntegrationEntity.builder()
                                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                        .identifier(TEST_GIT_INTEGRATION_IDENTIFIER)
                                                        .build();

    GitIntegrationDeleteEvent gitIntegrationDeleteEvent =
        new GitIntegrationDeleteEvent(TEST_ACCOUNT_IDENTIFIER, azureIntegrationEntity);
    validateResult(gitIntegrationDeleteEvent);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWithBitbucketCloudIntegrationEntity() {
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity = BitbucketCloudIntegrationEntity.builder()
                                                                          .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                                          .identifier(TEST_GIT_INTEGRATION_IDENTIFIER)
                                                                          .build();

    GitIntegrationDeleteEvent gitIntegrationDeleteEvent =
        new GitIntegrationDeleteEvent(TEST_ACCOUNT_IDENTIFIER, bitbucketCloudIntegrationEntity);
    validateResult(gitIntegrationDeleteEvent);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWithBitbucketServerIntegrationEntity() {
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity = BitbucketServerIntegrationEntity.builder()
                                                                            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                                            .identifier(TEST_GIT_INTEGRATION_IDENTIFIER)
                                                                            .build();

    GitIntegrationDeleteEvent gitIntegrationDeleteEvent =
        new GitIntegrationDeleteEvent(TEST_ACCOUNT_IDENTIFIER, bitbucketServerIntegrationEntity);
    validateResult(gitIntegrationDeleteEvent);
  }

  private void validateResult(GitIntegrationDeleteEvent gitIntegrationDeleteEvent) {
    assertEquals(AccountScope.class, gitIntegrationDeleteEvent.getResourceScope().getClass());
    assertEquals(TEST_ACCOUNT_SCOPE, gitIntegrationDeleteEvent.getResourceScope().getScope());
    assertEquals(TEST_ACCOUNT_IDENTIFIER + "_" + TEST_GIT_INTEGRATION_IDENTIFIER,
        gitIntegrationDeleteEvent.getResource().getIdentifier());
    assertEquals(IDP_GIT_INTEGRATIONS, gitIntegrationDeleteEvent.getResource().getType());
    assertEquals(TEST_GIT_INTEGRATION_IDENTIFIER,
        gitIntegrationDeleteEvent.getResource().getLabels().get(LABEL_KEY_RESOURCE_NAME));
    assertEquals(GIT_INTEGRATION_DELETED, gitIntegrationDeleteEvent.getEventType());
  }
}
