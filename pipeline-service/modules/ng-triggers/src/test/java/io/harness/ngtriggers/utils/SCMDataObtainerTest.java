/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.BHUMIJ;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.SAKSHI;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.encryption.SecretRefData;
import io.harness.exception.FailedToFetchCommitsException;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubIssueCommentSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.spec.GithubSpec;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.Issue;
import io.harness.product.ci.scm.proto.IssueCommentHook;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;
import io.harness.serializer.KryoSerializer;
import io.harness.service.ScmServiceClient;
import io.harness.tasks.BinaryResponseData;
import io.harness.utils.ConnectorUtils;
import io.harness.utils.PmsFeatureFlagHelper;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SCMDataObtainerTest extends CategoryTest {
  @Mock SecretDecryptor secretDecryptor;
  @Mock ScmServiceClient scmServiceClient;
  @Mock TaskExecutionUtils taskExecutionUtils;
  @Mock KryoSerializer kryoSerializer;
  @Mock KryoSerializer referenceFalseKryoSerializer;
  @Mock ConnectorUtils connectorUtils;
  @Mock PmsFeatureFlagHelper featureFlagHelper;
  @InjectMocks SCMDataObtainer scmDataObtainer;

  @Before
  public void setUp() throws IOException {
    initMocks(this);
    on(scmDataObtainer).set("kryoSerializer", kryoSerializer);
    on(scmDataObtainer).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testAcquireProviderData() {
    SCMDataObtainer spyScmDataObtainer = spy(new SCMDataObtainer(
        taskExecutionUtils, connectorUtils, kryoSerializer, referenceFalseKryoSerializer, featureFlagHelper));
    List<TriggerDetails> triggers = Collections.emptyList();
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build())
                    .build())

            .build();
    doNothing().when(spyScmDataObtainer).acquirePullRequestCommits(any(), any(), any(), anyBoolean());
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)))
        .thenReturn(false);
    spyScmDataObtainer.acquireProviderData(filterRequestData, triggers, null, false);
    verify(spyScmDataObtainer, times(1)).acquirePullRequestCommits(filterRequestData, triggers, null, false);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetGitURL() {
    String gitURL = scmDataObtainer.getGitURL(GitConnectionType.ACCOUNT, "url", "repo_name");
    assertThat(gitURL).isEqualTo("url/repo_name.git");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetGitURLWithConnectorAndTriggerDetails() {
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().connectionType(GitConnectionType.ACCOUNT).url("url").build())
            .executeOnDelegate(true)
            .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(
                        NGTriggerSourceV2.builder()
                            .spec(WebhookTriggerConfigV2.builder()
                                      .type(WebhookTriggerType.GITHUB)
                                      .spec(GithubSpec.builder()
                                                .spec(GithubIssueCommentSpec.builder().repoName("repo_name").build())
                                                .build())
                                      .build())
                            .build())
                    .build())
            .build();
    String gitURL = scmDataObtainer.getGitURL(connectorDetails, triggerDetails);
    assertThat(gitURL).isEqualTo("url/repo_name.git");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testRetrieveGenericGitConnectorURL() {
    String genericGitConnectorURL =
        scmDataObtainer.retrieveGenericGitConnectorURL("repo_name", GitConnectionType.ACCOUNT, "url");
    assertThat(genericGitConnectorURL).isEqualTo("url/repo_name");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testAcquirePullRequestCommits() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build())
                    .build())

            .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("account")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .git(GitMetadata.builder().connectorIdentifier("connector").build())
                                               .build())
                                  .build())
                    .build())
            .build();
    List<TriggerDetails> triggers = Collections.singletonList(triggerDetails);
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.TOKEN)
                                                .spec(GithubTokenSpecDTO.builder()
                                                          .tokenRef(SecretRefData.builder().identifier("token").build())
                                                          .build())
                                                .build())
                                 .build())
            .executeOnDelegate(true)
            .build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)))
        .thenReturn(false);
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().build());

    byte[] list = ListCommitsInPRResponse.newBuilder()
                      .addCommits(Commit.newBuilder()
                                      .setSha("commitId")
                                      .setMessage("message")
                                      .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                                      .build())
                      .build()
                      .toByteArray();
    when(kryoSerializer.asInflatedObject(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder().listCommitsInPRResponse(list).build());
    scmDataObtainer.acquirePullRequestCommits(filterRequestData, triggers, null, false);
    assertThat(
        filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getPr().getPr().getCommitsList().size())
        .isEqualTo(1);
    assertThat(
        filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getPr().getPr().getCommits(0).getSha())
        .isEqualTo("commitId");
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testAcquirePullRequestCommitsForIssueComment() {
    // Matches the actual issue comment webhook payload structure:
    // issue.number holds the PR number; issue.pr has no number set (it's 0)
    PullRequest pullRequest =
        PullRequest.newBuilder().setLink("https://github.com/sakshimalhan/harness_test/pull/1").build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("account")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setComment(IssueCommentHook.newBuilder()
                                            .setIssue(Issue.newBuilder()
                                                          .setNumber(1) // PR number is on issue, not on pr object
                                                          .setTitle("pr title here Create random2.txt")
                                                          .setBody("PR description here")
                                                          .setPr(pullRequest)
                                                          .build())
                                            .build())
                            .build())
                    .build())
            .build();

    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("account")
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .git(GitMetadata.builder().connectorIdentifier("connector").build())
                                               .build())
                                  .build())
                    .build())
            .build();
    List<TriggerDetails> triggers = Collections.singletonList(triggerDetails);

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.TOKEN)
                                                .spec(GithubTokenSpecDTO.builder()
                                                          .tokenRef(SecretRefData.builder().identifier("token").build())
                                                          .build())
                                                .build())
                                 .build())
            .executeOnDelegate(true)
            .build();

    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)))
        .thenReturn(false);
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_ENABLE_TRIGGER_ISSUE_COMMENT_COMMIT_FETCH)))
        .thenReturn(true);
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().build());

    byte[] list = ListCommitsInPRResponse.newBuilder()
                      .addCommits(Commit.newBuilder().setSha("commitSha").setMessage("latest commit message").build())
                      .build()
                      .toByteArray();
    when(kryoSerializer.asInflatedObject(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder().listCommitsInPRResponse(list).build());

    scmDataObtainer.acquirePullRequestCommits(filterRequestData, triggers, null, false);

    // Commits should be fetched and stored on comment.issue.pr
    Issue updatedIssue = filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getComment().getIssue();
    assertThat(updatedIssue.getPr().getCommitsList()).hasSize(1);
    assertThat(updatedIssue.getPr().getCommits(0).getSha()).isEqualTo("commitSha");
    assertThat(updatedIssue.getPr().getCommits(0).getMessage()).isEqualTo("latest commit message");
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testAcquirePullRequestCommitsForIssueCommentWhenFFDisabled() {
    PullRequest pullRequest =
        PullRequest.newBuilder().setLink("https://github.com/sakshimalhan/harness_test/pull/1").build();
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("account")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setComment(IssueCommentHook.newBuilder()
                                            .setIssue(Issue.newBuilder().setNumber(1).setPr(pullRequest).build())
                                            .build())
                            .build())
                    .build())
            .build();

    List<TriggerDetails> triggers = Collections.emptyList();

    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_ENABLE_TRIGGER_ISSUE_COMMENT_COMMIT_FETCH)))
        .thenReturn(false);

    scmDataObtainer.acquirePullRequestCommits(filterRequestData, triggers, null, false);

    Issue issue = filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getComment().getIssue();
    assertThat(issue.getPr().getCommitsList()).isEmpty();
    verify(connectorUtils, times(0)).getConnectorDetails(any(), anyString());
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetCommitsInPrViaDelegate() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(NGTriggerEntity.builder().accountId("account").build())
            .build();

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.TOKEN)
                                                .spec(GithubTokenSpecDTO.builder()
                                                          .tokenRef(SecretRefData.builder().identifier("token").build())
                                                          .build())
                                                .build())
                                 .build())
            .executeOnDelegate(true)
            .build();

    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)))
        .thenReturn(false);
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().build());

    byte[] list = ListCommitsInPRResponse.newBuilder()
                      .addCommits(Commit.newBuilder()
                                      .setSha("commitId")
                                      .setMessage("message")
                                      .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                                      .build())
                      .build()
                      .toByteArray();
    when(kryoSerializer.asInflatedObject(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder().listCommitsInPRResponse(list).build());

    List<Commit> commits = scmDataObtainer.getCommitsInPr(connectorDetails, triggerDetails, 3);
    assertThat(commits.size()).isEqualTo(1);
    assertThat(commits.get(0).getSha()).isEqualTo("commitId");
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testGetCommitsInPrViaDelegateUsingKryoWithoutReference() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(NGTriggerEntity.builder().accountId("account").build())
            .build();

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.TOKEN)
                                                .spec(GithubTokenSpecDTO.builder()
                                                          .tokenRef(SecretRefData.builder().identifier("token").build())
                                                          .build())
                                                .build())
                                 .build())
            .executeOnDelegate(true)
            .build();

    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)))
        .thenReturn(false);
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenReturn(BinaryResponseData.builder().usingKryoWithoutReference(true).build());

    byte[] list = ListCommitsInPRResponse.newBuilder()
                      .addCommits(Commit.newBuilder()
                                      .setSha("commitId")
                                      .setMessage("message")
                                      .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                                      .build())
                      .build()
                      .toByteArray();
    when(referenceFalseKryoSerializer.asInflatedObject(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder().listCommitsInPRResponse(list).build());

    List<Commit> commits = scmDataObtainer.getCommitsInPr(connectorDetails, triggerDetails, 3);
    assertThat(commits.size()).isEqualTo(1);
    assertThat(commits.get(0).getSha()).isEqualTo("commitId");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetCommitsInPrViaGithubApp() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(NGTriggerEntity.builder().accountId("account").build())
            .build();

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.GITHUB_APP)
                                                .spec(GithubAppSpecDTO.builder().build())
                                                .build())
                                 .build())
            .executeOnDelegate(false)
            .build();

    ListCommitsInPRResponse list =
        ListCommitsInPRResponse.newBuilder()
            .addCommits(Commit.newBuilder()
                            .setSha("commitId")
                            .setMessage("message")
                            .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                            .build())
            .build();
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)))
        .thenReturn(false);
    when(scmServiceClient.listCommitsInPR(any(), anyLong(), any())).thenReturn(list);

    List<Commit> commits = scmDataObtainer.getCommitsInPr(connectorDetails, triggerDetails, 3);
    assertThat(commits.size()).isEqualTo(1);
    assertThat(commits.get(0).getSha()).isEqualTo("commitId");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testGetCommitsInPrViaManager() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(NGTriggerEntity.builder().accountId("account").build())
            .build();

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.TOKEN)
                                                .spec(GithubTokenSpecDTO.builder()
                                                          .tokenRef(SecretRefData.builder().identifier("token").build())
                                                          .build())
                                                .build())
                                 .build())
            .executeOnDelegate(false)
            .build();

    ListCommitsInPRResponse list =
        ListCommitsInPRResponse.newBuilder()
            .addCommits(Commit.newBuilder()
                            .setSha("commitId")
                            .setMessage("message")
                            .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                            .build())
            .build();
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)))
        .thenReturn(false);
    when(scmServiceClient.listCommitsInPR(any(), anyLong(), any())).thenReturn(list);

    List<Commit> commits = scmDataObtainer.getCommitsInPr(connectorDetails, triggerDetails, 3);
    assertThat(commits.size()).isEqualTo(1);
    assertThat(commits.get(0).getSha()).isEqualTo("commitId");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testFetchCommitsWithMultipleTriggersSameConnector() {
    String connectorIdentifier = "test-connector";

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build())
                    .build())

            .build();

    TriggerDetails trigger1 =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("account")
                    .identifier("trigger1")
                    .metadata(
                        NGTriggerMetadata.builder()
                            .webhook(WebhookMetadata.builder()
                                         .git(GitMetadata.builder().connectorIdentifier(connectorIdentifier).build())
                                         .build())
                            .build())
                    .build())
            .build();

    TriggerDetails trigger2 =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("account")
                    .identifier("trigger2")
                    .metadata(
                        NGTriggerMetadata.builder()
                            .webhook(WebhookMetadata.builder()
                                         .git(GitMetadata.builder().connectorIdentifier(connectorIdentifier).build())
                                         .build())
                            .build())
                    .build())
            .build();

    TriggerDetails trigger3 =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("account")
                    .identifier("trigger3")
                    .metadata(
                        NGTriggerMetadata.builder()
                            .webhook(WebhookMetadata.builder()
                                         .git(GitMetadata.builder().connectorIdentifier("different-connector").build())
                                         .build())
                            .build())
                    .build())
            .build();

    List<TriggerDetails> triggers = Arrays.asList(trigger1, trigger2, trigger3);

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url("url").build())
            .executeOnDelegate(true)
            .build();

    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)))
        .thenReturn(true);
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_SCM_FETCH_THROW_EXCEPTION))).thenReturn(false);
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenThrow(new IllegalStateException("failed to fetch"));

    byte[] list = ListCommitsInPRResponse.newBuilder()
                      .addCommits(Commit.newBuilder()
                                      .setSha("commitId")
                                      .setMessage("message")
                                      .setLink("http://github.com/octocat/hello-world/pull/1/commits/commitId")
                                      .build())
                      .build()
                      .toByteArray();

    when(kryoSerializer.asInflatedObject(any()))
        .thenReturn(ScmGitRefTaskResponseData.builder().listCommitsInPRResponse(list).build());

    scmDataObtainer.acquirePullRequestCommits(filterRequestData, triggers, null, false);

    verify(connectorUtils, times(2)).getConnectorDetails(any(), anyString());
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testFetchCommitsThrowsFailedToFetchCommitsExceptionWhenScmCallFails() {
    String connectorIdentifier = "test-connector";
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("account")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build())
                    .build())
            .build();

    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("account")
                    .metadata(
                        NGTriggerMetadata.builder()
                            .webhook(WebhookMetadata.builder()
                                         .git(GitMetadata.builder().connectorIdentifier(connectorIdentifier).build())
                                         .build())
                            .build())
                    .build())
            .build();
    List<TriggerDetails> triggers = Collections.singletonList(triggerDetails);

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.TOKEN)
                                                .spec(GithubTokenSpecDTO.builder()
                                                          .tokenRef(SecretRefData.builder().identifier("token").build())
                                                          .build())
                                                .build())
                                 .build())
            .executeOnDelegate(true)
            .build();

    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)))
        .thenReturn(false);
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_SCM_FETCH_THROW_EXCEPTION))).thenReturn(true);
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenThrow(new IllegalStateException("failed to fetch"));

    assertThatThrownBy(() -> scmDataObtainer.acquirePullRequestCommits(filterRequestData, triggers, null, false))
        .isInstanceOf(FailedToFetchCommitsException.class)
        .hasMessageContaining(connectorIdentifier);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testFetchCommitsSwallowsExceptionWhenRevertKillSwitchEnabled() {
    String connectorIdentifier = "test-connector";
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("account")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder().setPr(PullRequestHook.newBuilder().build()).build())
                    .build())
            .build();

    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .spec(WebhookTriggerConfigV2.builder().type(WebhookTriggerType.GITHUB).build())
                                .build())
                    .build())
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("account")
                    .metadata(
                        NGTriggerMetadata.builder()
                            .webhook(WebhookMetadata.builder()
                                         .git(GitMetadata.builder().connectorIdentifier(connectorIdentifier).build())
                                         .build())
                            .build())
                    .build())
            .build();
    List<TriggerDetails> triggers = Collections.singletonList(triggerDetails);

    ConnectorDetails connectorDetails =
        ConnectorDetails.builder()
            .connectorType(ConnectorType.GITHUB)
            .connectorConfig(GithubConnectorDTO.builder()
                                 .connectionType(GitConnectionType.REPO)
                                 .url("url")
                                 .apiAccess(GithubApiAccessDTO.builder()
                                                .type(GithubApiAccessType.TOKEN)
                                                .spec(GithubTokenSpecDTO.builder()
                                                          .tokenRef(SecretRefData.builder().identifier("token").build())
                                                          .build())
                                                .build())
                                 .build())
            .executeOnDelegate(true)
            .build();

    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_FETCH_COMMITS_FROM_CONNECTORS)))
        .thenReturn(false);
    when(featureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_TRIGGER_SCM_FETCH_THROW_EXCEPTION))).thenReturn(false);
    when(taskExecutionUtils.executeSyncTask(any(DelegateTaskRequest.class)))
        .thenThrow(new IllegalStateException("failed to fetch"));

    scmDataObtainer.acquirePullRequestCommits(filterRequestData, triggers, null, false);

    assertThat(
        filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getPr().getPr().getCommitsList().size())
        .isEqualTo(0);
  }
}
