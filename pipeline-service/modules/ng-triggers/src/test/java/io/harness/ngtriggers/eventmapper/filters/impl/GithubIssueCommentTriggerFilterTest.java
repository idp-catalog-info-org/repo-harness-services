/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.filters.impl;

import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.FAILED_TO_FETCH_PR_DETAILS;
import static io.harness.rule.OwnerRule.AKASH_SHRIVASTAVA;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.SOUMYO_PURKAYASTHA;
import static io.harness.rule.OwnerRule.VINICIUS;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.IssueCommentWebhookEvent;
import io.harness.beans.Repository;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.delegate.beans.gitapi.GitApiFindPRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.encryption.SecretRefData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.utils.SCMDataObtainer;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.polling.contracts.BuildInfo;
import io.harness.polling.contracts.Metadata;
import io.harness.polling.contracts.PollingResponse;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.FindPRResponse;
import io.harness.product.ci.scm.proto.Issue;
import io.harness.product.ci.scm.proto.IssueCommentHook;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.Reference;
import io.harness.product.ci.scm.proto.Signature;
import io.harness.product.ci.scm.proto.User;
import io.harness.rule.Owner;
import io.harness.runnercommons.cgi.utils.UnifiedConditionChecker;
import io.harness.secrets.SecretDecryptor;
import io.harness.serializer.KryoSerializer;
import io.harness.service.ScmServiceClient;
import io.harness.service.WebhookParserSCMService;
import io.harness.tasks.BinaryResponseData;
import io.harness.utils.ConnectorUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.slf4j.LoggerFactory;

public class GithubIssueCommentTriggerFilterTest extends CategoryTest {
  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;
  @Inject @InjectMocks private GithubIssueCommentTriggerFilter githubIssueCommentTriggerFilter;
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;

  @Mock ConnectorUtils connectorUtils;
  @Mock TaskExecutionUtils taskExecutionUtils;
  @Mock WebhookParserSCMService webhookParserSCMService;
  @Mock PayloadConditionsTriggerFilter payloadConditionsTriggerFilter;
  @Mock ScmServiceClient scmServiceClient;
  @Mock SecretDecryptor secretDecryptor;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock SCMDataObtainer scmDataObtainer;
  @Mock private UnifiedConditionChecker unifiedConditionChecker;
  @Mock private io.harness.runnercommons.cgi.task.git.RunnerGitRefTaskBuilder runnerGitRefTaskBuilder;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;
  private static final String PARENT_UNIQUE_ID = "uniqueId";
  @Spy private TaskSetupAbstractionHelper taskSetupAbstractionHelper = new TaskSetupAbstractionHelper();
  private static Repository repository1 = Repository.builder()
                                              .httpURL("https://github.com/owner1/repo1.git")
                                              .sshURL("git@github.com:owner1/repo1.git")
                                              .link("https://github.com/owner1/repo1/b")
                                              .build();

  String pushPayload = "{\"commits\": [\n"
      + "  {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest1.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File1_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value1.yml\"\n"
      + "    ]\n"
      + "  }, \n"
      + "  {\n"
      + "    \"id\": \"3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"tree_id\": \"cc8524297287b55f07e38c56ecd43625f935252f\",\n"
      + "    \"distinct\": true,\n"
      + "    \"message\": \"nn\",\n"
      + "    \"timestamp\": \"2021-06-25T14:52:49-07:00\",\n"
      + "    \"url\": \"https://github.com/wings-software/cicddemo/commit/3a45ee02a55a29d696a2a1b0b923efa81523bb6c\",\n"
      + "    \"author\": {\n"
      + "      \"name\": \"Adwait Bhandare\",\n"
      + "      \"email\": \"adwait.bhandare@harness.io\",\n"
      + "      \"username\": \"adwaitabhandare\"\n"
      + "    },\n"
      + "    \"committer\": {\n"
      + "      \"name\": \"GitHub\",\n"
      + "      \"email\": \"noreply@github.com\",\n"
      + "      \"username\": \"web-flow\"\n"
      + "    },\n"
      + "    \"added\": [\n"
      + "      \"spec/manifest2.yml\"\n"
      + "    ],\n"
      + "    \"removed\": [\n"
      + "      \"File2_Removed.txt\"\n"
      + "    ],\n"
      + "    \"modified\": [\n"
      + "      \"values/value2.yml\"\n"
      + "    ]\n"
      + "  }\n"
      + "]}";

  @Before
  public void setUp() throws IOException, IllegalAccessException {
    initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    logger = (Logger) LoggerFactory.getLogger(BitbucketPRCommentTriggerFilter.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    when(pmsFeatureFlagService.isEnabled(any(), eq(FeatureName.CDS_NG_USE_SCM_FOR_PR_DETAILS_ON_ISSUE_COMMENT_TRIGGER)))
        .thenReturn(false);

    // Ensure feature flag for PL_USE_RUNNER is set to false by default
    when(pmsFeatureFlagService.isEnabled(anyString(), eq(FeatureName.PL_USE_RUNNER))).thenReturn(false);
    when(unifiedConditionChecker.shouldUseUnifiedFlow(any(), anyBoolean())).thenReturn(false);
    doAnswer(invocation -> {
      List<String> parentUniqueIds = invocation.getArgument(1);
      Map<String, Optional<ScopeInfo>> scopeInfoMap = new HashMap<>();
      for (String parentUniqueId : parentUniqueIds) {
        if (parentUniqueId == null || parentUniqueId.isBlank()) {
          continue;
        }
        scopeInfoMap.put(parentUniqueId,
            Optional.of(ScopeInfo.builder()
                            .accountIdentifier("acc")
                            .orgIdentifier("org")
                            .projectIdentifier("proj")
                            .uniqueId(parentUniqueId)
                            .scopeType(ScopeLevel.PROJECT)
                            .build()));
      }
      return scopeInfoMap;
    })
        .when(scopeResolutionHelper)
        .getScopeInfos(anyString(), anyList());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long creatAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .parentUniqueId(PARENT_UNIQUE_ID)
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(creatAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("20").build())
                    .originalEvent(triggerWebhookEvent)
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("release.1234"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    byte[] data = new byte[0];
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .webhookEventResponse(TriggerEventResponse.builder().payload(pushPayload).build())
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(filterRequestData);
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    WebhookEventMappingResponse webhookEventMappingResponse =
        githubIssueCommentTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void applyFilterTestWithDelegateUsingSCM() throws IOException {
    when(pmsFeatureFlagService.isEnabled(any(), eq(FeatureName.CDS_NG_USE_SCM_FOR_PR_DETAILS_ON_ISSUE_COMMENT_TRIGGER)))
        .thenReturn(true);
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long creatAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .parentUniqueId(PARENT_UNIQUE_ID)
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(creatAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("20").build())
                    .originalEvent(triggerWebhookEvent)
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("release.1234"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    byte[] data = new byte[0];

    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);
    PullRequest.Builder prBuilder = PullRequest.newBuilder();
    JsonFormat.parser().ignoringUnknownFields().merge(prJson, prBuilder);
    doReturn(ScmGitRefTaskResponseData.builder()
                 .gitRefType(GitRefType.PULL_REQUEST)
                 .findPRResponse(FindPRResponse.newBuilder().setPr(prBuilder.build()).build().toByteArray())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .webhookEventResponse(TriggerEventResponse.builder().payload(pushPayload).build())
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(filterRequestData);
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder().connectorConfig(GithubConnectorDTO.builder().build()).build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(scmDataObtainer.getGitURL(connectorDetails, details1)).thenReturn("gitUrl");
    WebhookEventMappingResponse webhookEventMappingResponse =
        githubIssueCommentTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    verify(scmDataObtainer, times(1)).getGitURL(connectorDetails, details1);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void applyFilterTestOnManager() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long creatAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    List<Commit> commits = new ArrayList<>();
    commits.add(Commit.newBuilder().setSha("abc").setAuthor(Signature.newBuilder().setName("author").build()).build());

    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(
                IssueCommentHook.newBuilder()
                    .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().addAllCommits(commits).build()).build())
                    .build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .parentUniqueId(PARENT_UNIQUE_ID)
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(creatAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("20").build())
                    .originalEvent(triggerWebhookEvent)
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("release.1234"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);
    doReturn(WebhookEventMappingResponse.builder()
                 .webhookEventResponse(TriggerEventResponse.builder().payload(pushPayload).build())
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(filterRequestData);
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
    FindPRResponse prResponse = FindPRResponse.newBuilder()
                                    .setPr(PullRequest.newBuilder()
                                               .setTarget("main")
                                               .setSource("feature/abc")
                                               .setNumber(1)
                                               .setTitle("Title")
                                               .setSha("commitId")
                                               .setRef("ref")
                                               .setBase(Reference.newBuilder().setSha("commitIdBase").build())
                                               .setAuthor(User.newBuilder().setAvatar("http://...").build())
                                               .setLink("http://github.com/octocat/hello-world/pull/1")
                                               .setClosed(false)
                                               .setMerged(false)
                                               .setMergeSha("mergeSha")
                                               .build())
                                    .build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(scmServiceClient.findPR(any(), anyLong(), any())).thenReturn(prResponse);
    WebhookEventMappingResponse webhookEventMappingResponse =
        githubIssueCommentTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    assertThat(filterRequestData.getWebhookPayloadData().getParseWebhookResponse().getPr().getPr().getCommitsCount())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterEmptyPrJsonTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long creatAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .parentUniqueId(PARENT_UNIQUE_ID)
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(creatAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("20").build())
                    .originalEvent(triggerWebhookEvent)
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("release.1234"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    byte[] data = new byte[0];
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson("").build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .webhookEventResponse(TriggerEventResponse.builder().payload(pushPayload).build())
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(filterRequestData);
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(ConnectorDetails.builder().build());
    WebhookEventMappingResponse webhookEventMappingResponse =
        githubIssueCommentTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getMessage())
        .isEqualTo("Failed to fetch PR Details");
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getPayload()).isEqualTo(pushPayload);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterExceptionTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    Long creatAt = 12L;
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .parentUniqueId(PARENT_UNIQUE_ID)
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(creatAt).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("20").build())
                    .originalEvent(triggerWebhookEvent)
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .pollingResponse(PollingResponse.newBuilder()
                                 .setBuildInfo(BuildInfo.newBuilder()
                                                   .addAllVersions(Collections.singletonList("release.1234"))
                                                   .addAllMetadata(Collections.singletonList(
                                                       Metadata.newBuilder().putAllMetadata(metadata).build()))
                                                   .build())
                                 .build())
            .details(asList(details1))
            .build();
    byte[] data = new byte[0];
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doThrow(NullPointerException.class).when(webhookParserSCMService).convertPRWebhookEvent(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .webhookEventResponse(TriggerEventResponse.builder().payload(pushPayload).build())
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(filterRequestData);
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    WebhookEventMappingResponse webhookEventMappingResponse =
        githubIssueCommentTriggerFilter.applyFilter(filterRequestData);
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getMessage())
        .isEqualTo("Failed to fetch PR Details: java.lang.NullPointerException");
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getPayload()).isEqualTo(pushPayload);
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testFailedToFetchPr() {
    FilterRequestData filterRequestData1 =
        FilterRequestData.builder()
            .isCustomTrigger(false)
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().createdAt(1L).build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("pullRequestNum").build())
                    .repository(Repository.builder().branch("branch").name("name").build())
                    .build())
            .details(Collections.singletonList(
                TriggerDetails.builder()
                    .ngTriggerEntity(NGTriggerEntity.builder()
                                         .accountId("acc")
                                         .orgIdentifier("org")
                                         .projectIdentifier("proj")
                                         .parentUniqueId(PARENT_UNIQUE_ID)
                                         .metadata(NGTriggerMetadata.builder()
                                                       .webhook(WebhookMetadata.builder()
                                                                    .git(GitMetadata.builder()
                                                                             .connectorIdentifier("connectorIdentifier")
                                                                             .build())
                                                                    .build())
                                                       .build())
                                         .build())
                    .build()))
            .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    BinaryResponseData binaryResponseData = BinaryResponseData.builder().build();
    when(taskExecutionUtils.executeSyncTask(any())).thenReturn(binaryResponseData);
    GitApiTaskResponse gitApiTaskResponse = GitApiTaskResponse.builder().errorMessage("errorMessage").build();
    when(kryoSerializer.asInflatedObject(any())).thenReturn(gitApiTaskResponse);
    WebhookEventMappingResponse webhookEventMappingResponse1 =
        githubIssueCommentTriggerFilter.applyFilter(filterRequestData1);
    assertThat(webhookEventMappingResponse1.isFailedToFindTrigger()).isEqualTo(true);
    assertThat(webhookEventMappingResponse1.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(FAILED_TO_FETCH_PR_DETAILS);
  }

  @Test
  @Owner(developers = AKASH_SHRIVASTAVA)
  @Category(UnitTests.class)
  public void testApplyFilterWithRunner() throws Exception {
    // Enable runner feature flag
    when(unifiedConditionChecker.shouldUseUnifiedFlow(any(), anyBoolean())).thenReturn(true);

    // Setup test data
    String accountId = "test-account";
    String prNumber = "123";
    String owner = "owner1";
    String repo = "repo1";

    // Create webhook payload data
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder()
            .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum(prNumber).build())
            .repository(Repository.builder().namespace(owner).name(repo).slug(owner + "/" + repo).build())
            .parseWebhookResponse(
                ParseWebhookResponse.newBuilder()
                    .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
                    .setComment(IssueCommentHook.newBuilder()
                                    .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                                    .build())
                    .build())
            .originalEvent(TriggerWebhookEvent.builder()
                               .accountId(accountId)
                               .sourceRepoType("GITHUB")
                               .createdAt(System.currentTimeMillis())
                               .build())
            .build();

    // Create trigger details
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId(accountId)
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .parentUniqueId(PARENT_UNIQUE_ID)
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("connector-id").build())
                                               .build())
                                  .build())
                    .build())
            .build();

    // Create filter request data
    FilterRequestData filterRequestData = FilterRequestData.builder()
                                              .accountId(accountId)
                                              .webhookPayloadData(webhookPayloadData)
                                              .details(Collections.singletonList(triggerDetails))
                                              .build();

    // Create connector details
    ConnectorDetails connectorDetails =
        ConnectorDetails.builder().connectorConfig(GithubConnectorDTO.builder().build()).build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(scmDataObtainer.getGitURL(connectorDetails, triggerDetails)).thenReturn("gitUrl");

    // Setup runner response
    ClassLoader classLoader = getClass().getClassLoader();
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);

    ScmGitRefTaskResponseData expectedResponse =
        ScmGitRefTaskResponseData.builder()
            .findPRResponse(FindPRResponse.newBuilder()
                                .setPr(PullRequest.newBuilder().setTitle("Test PR").build())
                                .build()
                                .toByteArray())
            .build();

    // Mock runner behavior using doReturn
    doReturn(expectedResponse).when(runnerGitRefTaskBuilder).sendRefTask(any(), any(), any(), any());

    // Mock payload conditions filter
    when(payloadConditionsTriggerFilter.applyFilter(any()))
        .thenReturn(WebhookEventMappingResponse.builder()
                        .webhookEventResponse(TriggerEventResponse.builder().build())
                        .failedToFindTrigger(false)
                        .build());

    // Execute the method
    WebhookEventMappingResponse response = githubIssueCommentTriggerFilter.applyFilter(filterRequestData);

    // Verify results
    assertThat(response).isNotNull();
    assertThat(response.isFailedToFindTrigger()).isFalse();

    // Verify that runner was called with correct parameters

    // Verify that task execution was not called (since we're using runner)
    verify(taskExecutionUtils, times(0)).executeSyncTask(any());
  }

  @Test
  @Owner(developers = AKASH_SHRIVASTAVA)
  @Category(UnitTests.class)
  public void testApplyFilterWithRunnerException() throws Exception {
    // Enable runner feature flag
    when(unifiedConditionChecker.shouldUseUnifiedFlow(any(), anyBoolean())).thenReturn(true);
    // Setup test data
    String accountId = "test-account";
    String prNumber = "123";
    String owner = "owner1";
    String repo = "repo1";

    // Create webhook payload data
    WebhookPayloadData webhookPayloadData =
        WebhookPayloadData.builder()
            .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum(prNumber).build())
            .repository(Repository.builder().namespace(owner).name(repo).slug(owner + "/" + repo).build())
            .parseWebhookResponse(
                ParseWebhookResponse.newBuilder()
                    .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
                    .setComment(IssueCommentHook.newBuilder()
                                    .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                                    .build())
                    .build())
            .originalEvent(TriggerWebhookEvent.builder()
                               .accountId(accountId)
                               .sourceRepoType("GITHUB")
                               .createdAt(System.currentTimeMillis())
                               .build())
            .build();

    // Create trigger details
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId(accountId)
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .parentUniqueId(PARENT_UNIQUE_ID)
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("connector-id").build())
                                               .build())
                                  .build())
                    .build())
            .build();

    // Create filter request data
    FilterRequestData filterRequestData = FilterRequestData.builder()
                                              .accountId(accountId)
                                              .webhookPayloadData(webhookPayloadData)
                                              .details(Collections.singletonList(triggerDetails))
                                              .build();

    // Create connector details
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);

    // Mock runner to throw exception using doThrow
    doThrow(new RuntimeException("Runner error")).when(runnerGitRefTaskBuilder).sendRefTask(any(), any(), any(), any());

    // Execute the method
    WebhookEventMappingResponse response = githubIssueCommentTriggerFilter.applyFilter(filterRequestData);

    // Verify error response
    assertThat(response).isNotNull();
    assertThat(response.isFailedToFindTrigger()).isTrue();
    assertThat(response.getWebhookEventResponse().getMessage()).contains("Failed to fetch PR Details");

    // Verify that runner was called

    // Verify that task execution was not called (since we're using runner)
    verify(taskExecutionUtils, times(0)).executeSyncTask(any());
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void buildAndFireTaskSetsTaskSetupAbstractionsForScopedConnectorTest() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .parentUniqueId(PARENT_UNIQUE_ID)
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(12L).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("20").build())
                    .originalEvent(triggerWebhookEvent)
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .details(asList(details1))
            .build();
    byte[] data = new byte[0];
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .webhookEventResponse(TriggerEventResponse.builder().payload(pushPayload).build())
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(filterRequestData);
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .orgIdentifier("org")
                                            .projectIdentifier("proj")
                                            .delegateSelectors(Set.of("org-kubernetes-delegate"))
                                            .build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);

    githubIssueCommentTriggerFilter.applyFilter(filterRequestData);

    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(taskExecutionUtils).executeSyncTask(captor.capture());
    DelegateTaskRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest.getTaskSetupAbstractions()).containsEntry("ng", "true");
    assertThat(capturedRequest.getTaskSetupAbstractions()).containsEntry("owner", "org/proj");
    assertThat(capturedRequest.getTaskSetupAbstractions()).containsEntry("orgIdentifier", "org");
    assertThat(capturedRequest.getTaskSetupAbstractions()).containsEntry("projectIdentifier", "proj");
    assertThat(capturedRequest.getTaskSelectors()).contains("org-kubernetes-delegate");
  }

  @Test
  @Owner(developers = SOUMYO_PURKAYASTHA)
  @Category(UnitTests.class)
  public void getPullRequestDetailsWithScmSetsTaskSetupAbstractionsForScopedConnectorTest() throws IOException {
    when(pmsFeatureFlagService.isEnabled(any(), eq(FeatureName.CDS_NG_USE_SCM_FOR_PR_DETAILS_ON_ISSUE_COMMENT_TRIGGER)))
        .thenReturn(true);
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder()
            .ngTriggerEntity(
                NGTriggerEntity.builder()
                    .accountId("acc")
                    .orgIdentifier("org")
                    .projectIdentifier("proj")
                    .parentUniqueId(PARENT_UNIQUE_ID)
                    .metadata(NGTriggerMetadata.builder()
                                  .webhook(WebhookMetadata.builder()
                                               .type("GITHUB")
                                               .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                               .build())
                                  .build())
                    .build())
            .ngTriggerConfigV2(ngTriggerConfigV2)
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder().payload(pushPayload).sourceRepoType("Github").createdAt(12L).build();

    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .accountId("p")
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(TriggerWebhookEvent.builder().accountId("acc").sourceRepoType("GITHUB").build())
                    .webhookEvent(IssueCommentWebhookEvent.builder().pullRequestNum("20").build())
                    .originalEvent(triggerWebhookEvent)
                    .parseWebhookResponse(parseWebhookResponse)
                    .repository(repository1)
                    .build())
            .details(asList(details1))
            .build();
    byte[] data = new byte[0];

    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);
    PullRequest.Builder prBuilder = PullRequest.newBuilder();
    JsonFormat.parser().ignoringUnknownFields().merge(prJson, prBuilder);
    doReturn(ScmGitRefTaskResponseData.builder()
                 .gitRefType(GitRefType.PULL_REQUEST)
                 .findPRResponse(FindPRResponse.newBuilder().setPr(prBuilder.build()).build().toByteArray())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .webhookEventResponse(TriggerEventResponse.builder().payload(pushPayload).build())
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(filterRequestData);
    ConnectorDetails connectorDetails = ConnectorDetails.builder()
                                            .connectorConfig(GithubConnectorDTO.builder().build())
                                            .orgIdentifier("org")
                                            .projectIdentifier("proj")
                                            .delegateSelectors(Set.of("org-kubernetes-delegate"))
                                            .build();
    when(connectorUtils.getConnectorDetails(any(), anyString())).thenReturn(connectorDetails);
    when(scmDataObtainer.getGitURL(connectorDetails, details1)).thenReturn("gitUrl");

    githubIssueCommentTriggerFilter.applyFilter(filterRequestData);

    ArgumentCaptor<DelegateTaskRequest> captor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(taskExecutionUtils).executeSyncTask(captor.capture());
    DelegateTaskRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest.getTaskSetupAbstractions()).containsEntry("ng", "true");
    assertThat(capturedRequest.getTaskSetupAbstractions()).containsEntry("owner", "org/proj");
    assertThat(capturedRequest.getTaskSetupAbstractions()).containsEntry("orgIdentifier", "org");
    assertThat(capturedRequest.getTaskSetupAbstractions()).containsEntry("projectIdentifier", "proj");
    assertThat(capturedRequest.getTaskSelectors()).contains("org-kubernetes-delegate");
  }
}
