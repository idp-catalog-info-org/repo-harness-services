/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.events;

import static io.harness.audit.ResourceTypeConstants.IDP_GIT_INTEGRATIONS;
import static io.harness.idp.integrations.events.GitIntegrationUpdateEvent.GIT_INTEGRATION_UPDATED;
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
public class GitIntegrationUpdateEventTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_GIT_INTEGRATION_OLD_IDENTIFIER = "idp_testConnector123_old";
  static final String TEST_GIT_INTEGRATION_NEW_IDENTIFIER = "idp_testConnector123_new";
  static final String TEST_ACCOUNT_SCOPE = "account";

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWithGithubIntegrationEntity() {
    GithubIntegrationEntity oldGithubIntegrationEntity = GithubIntegrationEntity.builder()
                                                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                             .identifier(TEST_GIT_INTEGRATION_OLD_IDENTIFIER)
                                                             .build();
    GithubIntegrationEntity newGithubIntegrationEntity = GithubIntegrationEntity.builder()
                                                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                             .identifier(TEST_GIT_INTEGRATION_NEW_IDENTIFIER)
                                                             .build();

    GitIntegrationUpdateEvent gitIntegrationUpdateEvent =
        new GitIntegrationUpdateEvent(TEST_ACCOUNT_IDENTIFIER, oldGithubIntegrationEntity, newGithubIntegrationEntity);
    validateResult(gitIntegrationUpdateEvent);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWithGitlabIntegrationEntity() {
    GitlabIntegrationEntity oldGitlabIntegrationEntity = GitlabIntegrationEntity.builder()
                                                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                             .identifier(TEST_GIT_INTEGRATION_OLD_IDENTIFIER)
                                                             .build();
    GitlabIntegrationEntity newGitlabIntegrationEntity = GitlabIntegrationEntity.builder()
                                                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                             .identifier(TEST_GIT_INTEGRATION_NEW_IDENTIFIER)
                                                             .build();

    GitIntegrationUpdateEvent gitIntegrationUpdateEvent =
        new GitIntegrationUpdateEvent(TEST_ACCOUNT_IDENTIFIER, oldGitlabIntegrationEntity, newGitlabIntegrationEntity);
    validateResult(gitIntegrationUpdateEvent);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWithAzureIntegrationEntity() {
    AzureIntegrationEntity oldAzureIntegrationEntity = AzureIntegrationEntity.builder()
                                                           .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                           .identifier(TEST_GIT_INTEGRATION_OLD_IDENTIFIER)
                                                           .build();
    AzureIntegrationEntity newAzureIntegrationEntity = AzureIntegrationEntity.builder()
                                                           .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                           .identifier(TEST_GIT_INTEGRATION_NEW_IDENTIFIER)
                                                           .build();

    GitIntegrationUpdateEvent gitIntegrationUpdateEvent =
        new GitIntegrationUpdateEvent(TEST_ACCOUNT_IDENTIFIER, oldAzureIntegrationEntity, newAzureIntegrationEntity);
    validateResult(gitIntegrationUpdateEvent);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWithBitbucketCloudIntegrationEntity() {
    BitbucketCloudIntegrationEntity oldBitbucketCloudIntegrationEntity =
        BitbucketCloudIntegrationEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .identifier(TEST_GIT_INTEGRATION_OLD_IDENTIFIER)
            .build();
    BitbucketCloudIntegrationEntity newBitbucketCloudIntegrationEntity =
        BitbucketCloudIntegrationEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .identifier(TEST_GIT_INTEGRATION_NEW_IDENTIFIER)
            .build();

    GitIntegrationUpdateEvent gitIntegrationUpdateEvent = new GitIntegrationUpdateEvent(
        TEST_ACCOUNT_IDENTIFIER, oldBitbucketCloudIntegrationEntity, newBitbucketCloudIntegrationEntity);
    validateResult(gitIntegrationUpdateEvent);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWithBitbucketServerIntegrationEntity() {
    BitbucketServerIntegrationEntity oldBitbucketServerIntegrationEntity =
        BitbucketServerIntegrationEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .identifier(TEST_GIT_INTEGRATION_OLD_IDENTIFIER)
            .build();
    BitbucketServerIntegrationEntity newBitbucketServerIntegrationEntity =
        BitbucketServerIntegrationEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .identifier(TEST_GIT_INTEGRATION_NEW_IDENTIFIER)
            .build();

    GitIntegrationUpdateEvent gitIntegrationUpdateEvent = new GitIntegrationUpdateEvent(
        TEST_ACCOUNT_IDENTIFIER, oldBitbucketServerIntegrationEntity, newBitbucketServerIntegrationEntity);
    validateResult(gitIntegrationUpdateEvent);
  }

  private void validateResult(GitIntegrationUpdateEvent gitIntegrationUpdateEvent) {
    assertEquals(AccountScope.class, gitIntegrationUpdateEvent.getResourceScope().getClass());
    assertEquals(TEST_ACCOUNT_SCOPE, gitIntegrationUpdateEvent.getResourceScope().getScope());
    assertEquals(TEST_ACCOUNT_IDENTIFIER + "_" + TEST_GIT_INTEGRATION_NEW_IDENTIFIER,
        gitIntegrationUpdateEvent.getResource().getIdentifier());
    assertEquals(IDP_GIT_INTEGRATIONS, gitIntegrationUpdateEvent.getResource().getType());
    assertEquals(TEST_GIT_INTEGRATION_NEW_IDENTIFIER,
        gitIntegrationUpdateEvent.getResource().getLabels().get(LABEL_KEY_RESOURCE_NAME));
    assertEquals(GIT_INTEGRATION_UPDATED, gitIntegrationUpdateEvent.getEventType());
  }
}
