/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.eventmapper.impl;

import static io.harness.beans.FeatureName.PIPE_TRIGGER_MAPPING_V2;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SCM_SERVICE_CONNECTION_FAILED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.SKIPPED;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static io.grpc.Status.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.PushWebhookEvent;
import io.harness.beans.Repository;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.gitapi.GitApiFindPRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.logging.CommandExecutionStatus;
import io.harness.metrics.service.api.MetricService;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.GitMetadata;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.eventmapper.filters.TriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.ngtriggers.eventmapper.filters.impl.AccountTriggerFilter;
import io.harness.ngtriggers.eventmapper.filters.impl.PayloadConditionsTriggerFilter;
import io.harness.ngtriggers.helpers.WebhookEventPublisher;
import io.harness.ngtriggers.helpers.filter.TriggerFilterStore;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.utils.SCMUtils;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.ngtriggers.utils.WebhookEventPayloadParser;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.GitProvider;
import io.harness.product.ci.scm.proto.Issue;
import io.harness.product.ci.scm.proto.IssueCommentHook;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PullRequest;
import io.harness.product.ci.scm.proto.PullRequestHook;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.WebhookParserSCMService;
import io.harness.tasks.BinaryResponseData;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

public class GitWebhookEventToTriggerMapperTest extends CategoryTest {
  @Mock WebhookEventPayloadParser webhookEventPayloadParser;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock NGTriggerService ngTriggerService;
  @Mock TriggerFilterStore triggerFilterStore;
  @InjectMocks @Inject GitWebhookEventToTriggerMapper mapper;
  @Mock TaskExecutionUtils taskExecutionUtils;
  @Mock WebhookParserSCMService webhookParserSCMService;
  @InjectMocks @Inject AccountTriggerFilter accountTriggerFilter;
  @Mock PayloadConditionsTriggerFilter payloadConditionsTriggerFilter;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private WebhookEventPublisher webhookEventPublisher;
  @Mock private TriggerFilter triggerFilter;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private SCMUtils scmUtils;
  @Mock private MongoTemplate mongoTemplate;
  @Mock MetricService metricService;

  private static final String ACCOUNT_ID = "accountId";

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
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(mapper).set("triggerMapperHelper", new TriggerMapperHelper(metricService));
  }

  private NGTriggerEntity buildTriggerEntityForOptimization() {
    return NGTriggerEntity.builder()
        .accountId("acc")
        .orgIdentifier("org")
        .projectIdentifier("proj")
        .metadata(NGTriggerMetadata.builder()
                      .webhook(WebhookMetadata.builder()
                                   .type("GITHUB")
                                   .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                   .build())
                      .build())
        .build();
  }

  private void stubOptimizedTriggerRetrieval() {
    doReturn(Collections.singletonList(buildTriggerEntityForOptimization()))
        .when(ngTriggerService)
        .findTriggersByCriteria(any());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testParseEventData() {
    TriggerWebhookEvent event = TriggerWebhookEvent.builder().createdAt(1l).build();
    StatusRuntimeException statusRuntimeException = new StatusRuntimeException(UNAVAILABLE);
    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(any(), eq(PIPE_TRIGGER_MAPPING_V2));
    doThrow(statusRuntimeException).when(webhookEventPayloadParser).parseEvent(event);

    WebhookEventMappingResponse webhookEventMappingResponse =
        mapper.mapWebhookEventToTriggers(TriggerMappingRequestData.builder().triggerWebhookEvent(event).build());
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().isExceptionOccurred()).isTrue();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus())
        .isEqualTo(SCM_SERVICE_CONNECTION_FAILED);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void applyFilterTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");

    ClassLoader classLoader = getClass().getClassLoader();

    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .sourceRepoType("Github")
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload(pushPayload)
            .build();

    byte[] data = new byte[0];
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);

    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(any(), eq(PIPE_TRIGGER_MAPPING_V2));
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());

    doReturn(WebhookPayloadData.builder()
                 .webhookEvent(PushWebhookEvent.builder().repository(repository1).branchName("main").build())
                 .originalEvent(triggerWebhookEvent)
                 .parseWebhookResponse(parseWebhookResponse)
                 .build())
        .when(webhookEventPayloadParser)
        .parseEvent(any());
    doReturn(Arrays.asList(payloadConditionsTriggerFilter)).when(triggerFilterStore).getWebhookTriggerFilters(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .parseWebhookResponse(parseWebhookResponse)
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(any());
    doReturn(false)
        .when(pmsFeatureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.CDS_SKIP_WEBHOOK_TRIGGER_EXECUTION_ON_SPECIAL_KEYWORDS.toString());
    stubOptimizedTriggerRetrieval();
    WebhookEventMappingResponse webhookEventMappingResponse = mapper.mapWebhookEventToTriggers(
        TriggerMappingRequestData.builder().triggerWebhookEvent(triggerWebhookEvent).build());
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void skipPipelineExecutionIfSpecialKeywordIsPresentTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");

    ClassLoader classLoader = getClass().getClassLoader();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(
                PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setTitle("[ci skip] test").build()).build())
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .accountId(ACCOUNT_ID)
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload("{a: b}")
            .build();
    WebhookDTO webhookDTO =
        WebhookDTO.newBuilder()
            .setParsedResponse(ParseWebhookResponse.newBuilder()
                                   .setPr(PullRequestHook.newBuilder()
                                              .setPr(PullRequest.newBuilder().setTitle("[skip ci] test").build())
                                              .build())
                                   .build())
            .build();

    byte[] data = new byte[0];
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);

    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(any(), eq(PIPE_TRIGGER_MAPPING_V2));
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookPayloadData.builder()
                 .webhookEvent(PushWebhookEvent.builder().repository(repository1).branchName("main").build())
                 .originalEvent(triggerWebhookEvent)
                 .parseWebhookResponse(parseWebhookResponse)
                 .build())
        .when(webhookEventPayloadParser)
        .convertWebhookResponse(any(), any());
    doReturn(Collections.singletonList(payloadConditionsTriggerFilter))
        .when(triggerFilterStore)
        .getWebhookTriggerFilters(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .parseWebhookResponse(parseWebhookResponse)
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(any());
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.CDS_SKIP_WEBHOOK_TRIGGER_EXECUTION_ON_SPECIAL_KEYWORDS);
    stubOptimizedTriggerRetrieval();
    WebhookEventMappingResponse webhookEventMappingResponse = mapper.mapWebhookEventToTriggers(
        TriggerMappingRequestData.builder().triggerWebhookEvent(triggerWebhookEvent).webhookDTO(webhookDTO).build());
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus()).isEqualTo(SKIPPED);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testMapWebhookEventToTriggers() {
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(
                PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setTitle("[ci skip] test").build()).build())
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .accountId(ACCOUNT_ID)
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload("{a: b}")
            .build();
    WebhookDTO webhookDTO =
        WebhookDTO.newBuilder()
            .setParsedResponse(ParseWebhookResponse.newBuilder()
                                   .setPr(PullRequestHook.newBuilder()
                                              .setPr(PullRequest.newBuilder().setTitle("[skip ci] test").build())
                                              .build())
                                   .build())
            .build();

    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(any(), eq(PIPE_TRIGGER_MAPPING_V2));
    doReturn(WebhookPayloadData.builder()
                 .webhookEvent(PushWebhookEvent.builder().repository(repository1).branchName("main").build())
                 .originalEvent(triggerWebhookEvent)
                 .parseWebhookResponse(parseWebhookResponse)
                 .build())
        .when(webhookEventPayloadParser)
        .convertWebhookResponse(any(), any());
    doReturn(Collections.singletonList(payloadConditionsTriggerFilter))
        .when(triggerFilterStore)
        .getWebhookTriggerFilters(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .parseWebhookResponse(parseWebhookResponse)
                 .failedToFindTrigger(true)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(any());
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.CDS_SKIP_WEBHOOK_TRIGGER_EXECUTION_ON_SPECIAL_KEYWORDS);
    stubOptimizedTriggerRetrieval();
    WebhookEventMappingResponse webhookEventMappingResponse = mapper.mapWebhookEventToTriggers(
        TriggerMappingRequestData.builder().triggerWebhookEvent(triggerWebhookEvent).webhookDTO(webhookDTO).build());
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse()).isEqualTo(null);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void applyFilterTest_v2() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
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

    NGTriggerEntity triggerEntity =
        NGTriggerEntity.builder()
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .type("GITHUB")
                                       .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                       .build())
                          .build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder().ngTriggerEntity(triggerEntity).ngTriggerConfigV2(ngTriggerConfigV2).build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .sourceRepoType("Github")
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload(pushPayload)
            .build();

    byte[] data = new byte[0];
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);

    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(any(), eq(PIPE_TRIGGER_MAPPING_V2));
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(Collections.singletonList(payloadConditionsTriggerFilter))
        .when(triggerFilterStore)
        .getWebhookTriggerFilters(any());
    doReturn(Collections.singletonList(details1)).when(payloadConditionsTriggerFilter).applyFilterV2(any(), any());
    doReturn(WebhookPayloadData.builder()
                 .webhookEvent(PushWebhookEvent.builder().repository(repository1).branchName("main").build())
                 .originalEvent(triggerWebhookEvent)
                 .parseWebhookResponse(parseWebhookResponse)
                 .build())
        .when(webhookEventPayloadParser)
        .parseEvent(any());
    doReturn(false)
        .when(pmsFeatureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.CDS_SKIP_WEBHOOK_TRIGGER_EXECUTION_ON_SPECIAL_KEYWORDS.toString());
    var stream = Collections.singletonList(triggerEntity).stream();
    doReturn(stream).when(mongoTemplate).stream(any(), eq(NGTriggerEntity.class));
    WebhookEventMappingResponse webhookEventMappingResponse = mapper.mapWebhookEventToTriggers(
        TriggerMappingRequestData.builder().triggerWebhookEvent(triggerWebhookEvent).build());
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void skipPipelineExecutionIfSpecialKeywordIsPresentForPushEventTest_v2() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    ClassLoader classLoader = getClass().getClassLoader();
    String ngTriggerYaml_github_pr =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-filePath-pr-v2.yaml")),
            StandardCharsets.UTF_8);
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(
                PushHook.newBuilder().setCommit(Commit.newBuilder().setMessage("please [skip ci] blah blah")).build())
            .build();

    NGTriggerEntity triggerEntity =
        NGTriggerEntity.builder()
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .type("GITHUB")
                                       .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                       .build())
                          .build())
            .build();
    TriggerDetails details1 =
        TriggerDetails.builder().ngTriggerEntity(triggerEntity).ngTriggerConfigV2(ngTriggerConfigV2).build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .accountId(ACCOUNT_ID)
            .sourceRepoType("Github")
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload(pushPayload)
            .build();

    byte[] data = new byte[0];
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);

    doReturn(true).when(pmsFeatureFlagHelper).isEnabled(any(), eq(PIPE_TRIGGER_MAPPING_V2));
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(Collections.singletonList(payloadConditionsTriggerFilter))
        .when(triggerFilterStore)
        .getWebhookTriggerFilters(any());
    doReturn(Collections.singletonList(details1)).when(payloadConditionsTriggerFilter).applyFilterV2(any(), any());
    doReturn(WebhookPayloadData.builder()
                 .webhookEvent(PushWebhookEvent.builder().repository(repository1).branchName("main").build())
                 .originalEvent(triggerWebhookEvent)
                 .parseWebhookResponse(parseWebhookResponse)
                 .build())
        .when(webhookEventPayloadParser)
        .parseEvent(any());
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.CDS_SKIP_WEBHOOK_TRIGGER_EXECUTION_ON_SPECIAL_KEYWORDS);
    var stream = Collections.singletonList(triggerEntity).stream();
    doReturn(stream).when(mongoTemplate).stream(any(), eq(NGTriggerEntity.class));
    WebhookEventMappingResponse webhookEventMappingResponse = mapper.mapWebhookEventToTriggers(
        TriggerMappingRequestData.builder().triggerWebhookEvent(triggerWebhookEvent).build());
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus()).isEqualTo(SKIPPED);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void skipPipelineExecutionIfSpecialKeywordIsPresentForPushEventTest() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");

    ClassLoader classLoader = getClass().getClassLoader();
    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPush(
                PushHook.newBuilder().setCommit(Commit.newBuilder().setMessage("please [skip ci] blah blah")).build())
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .accountId(ACCOUNT_ID)
            .sourceRepoType("Github")
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload(pushPayload)
            .build();
    WebhookDTO webhookDTO =
        WebhookDTO.newBuilder()
            .setParsedResponse(
                ParseWebhookResponse.newBuilder()
                    .setPush(PushHook.newBuilder()
                                 .setCommit(Commit.newBuilder().setMessage("please skip ci blah blah").build())
                                 .build())
                    .build())
            .build();

    byte[] data = new byte[0];
    final URL testFile = classLoader.getResource("github_PR.json");
    String prJson = Resources.toString(testFile, Charsets.UTF_8);

    doReturn(false).when(pmsFeatureFlagHelper).isEnabled(any(), eq(PIPE_TRIGGER_MAPPING_V2));
    doReturn(BinaryResponseData.builder().data(data).build()).when(taskExecutionUtils).executeSyncTask(any());
    doReturn(GitApiTaskResponse.builder()
                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                 .gitApiResult(GitApiFindPRTaskResponse.builder().prJson(prJson).build())
                 .build())
        .when(kryoSerializer)
        .asInflatedObject(any());
    doReturn(WebhookPayloadData.builder()
                 .webhookEvent(PushWebhookEvent.builder().repository(repository1).branchName("main").build())
                 .originalEvent(triggerWebhookEvent)
                 .parseWebhookResponse(parseWebhookResponse)
                 .build())
        .when(webhookEventPayloadParser)
        .convertWebhookResponse(any(), any());
    doReturn(Collections.singletonList(payloadConditionsTriggerFilter))
        .when(triggerFilterStore)
        .getWebhookTriggerFilters(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .parseWebhookResponse(parseWebhookResponse)
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(any());
    doReturn(true)
        .when(pmsFeatureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.CDS_SKIP_WEBHOOK_TRIGGER_EXECUTION_ON_SPECIAL_KEYWORDS);
    stubOptimizedTriggerRetrieval();
    WebhookEventMappingResponse webhookEventMappingResponse = mapper.mapWebhookEventToTriggers(
        TriggerMappingRequestData.builder().triggerWebhookEvent(triggerWebhookEvent).webhookDTO(webhookDTO).build());
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    assertThat(webhookEventMappingResponse.getWebhookEventResponse().getFinalStatus()).isEqualTo(SKIPPED);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testCheckIfSkipCiExpressionIsPresent() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPush(
                                PushHook.newBuilder()
                                    .addCommits(
                                        Commit.newBuilder().setMessage("This is a [pipeline skip] message").build())
                                    .build())
                            .build())
                    .build())
            .build();
    boolean actual = mapper.checkIfSkipCiExpressionIsPresent(filterRequestData);
    assertThat(actual).isEqualTo(true);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCheckIfSkipCiExpressionIsPresent_headCommitHasSkipKeywordOnMergePush() {
    // Simulates PR merge push: commits[0] is an older feature commit without the keyword, while the
    // head/merge commit message includes the PR title that was updated to contain [skip ci].
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPush(PushHook.newBuilder()
                                         .addCommits(Commit.newBuilder()
                                                         .setMessage("feat: initial change without skip keyword")
                                                         .build())
                                         .addCommits(Commit.newBuilder()
                                                         .setMessage("feat: additional change without skip keyword")
                                                         .build())
                                         .setCommit(Commit.newBuilder()
                                                        .setMessage("Merge pull request #228 from branch\n\n"
                                                            + "[skip ci] platform nodejs hello")
                                                        .build())
                                         .build())
                            .build())
                    .build())
            .build();
    assertThat(mapper.checkIfSkipCiExpressionIsPresent(filterRequestData)).isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCheckIfSkipCiExpressionIsPresent_doesNotSkipWhenOnlyOlderCommitHasKeyword() {
    // Tip/head drives CI: an older commit in the push with [skip ci] must not suppress the run when
    // the head commit message does not contain the keyword.
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPush(
                                PushHook.newBuilder()
                                    .addCommits(
                                        Commit.newBuilder().setMessage("feat: older change with [skip ci]").build())
                                    .addCommits(Commit.newBuilder().setMessage("feat: tip without keyword").build())
                                    .setCommit(Commit.newBuilder().setMessage("feat: tip without keyword").build())
                                    .build())
                            .build())
                    .build())
            .build();
    assertThat(mapper.checkIfSkipCiExpressionIsPresent(filterRequestData)).isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCheckIfSkipCiExpressionIsPresent_noSkipWhenNeitherHeadNorCommitsHaveKeyword() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPush(PushHook.newBuilder()
                                         .addCommits(Commit.newBuilder().setMessage("feat: initial").build())
                                         .setCommit(Commit.newBuilder()
                                                        .setMessage("Merge pull request #228\n\nfeat: initial")
                                                        .build())
                                         .build())
                            .build())
                    .build())
            .build();
    assertThat(mapper.checkIfSkipCiExpressionIsPresent(filterRequestData)).isFalse();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCheckIfSkipCiExpressionIsPresent_prTitleHasSkipKeyword() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(PullRequestHook.newBuilder()
                                       .setPr(PullRequest.newBuilder().setTitle("[skip ci] regular pr title").build())
                                       .build())
                            .build())
                    .build())
            .build();
    assertThat(mapper.checkIfSkipCiExpressionIsPresent(filterRequestData)).isTrue();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCheckIfSkipCiExpressionIsPresent_prCommitMessageAloneDoesNotSkip() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPr(
                                PullRequestHook.newBuilder()
                                    .setPr(PullRequest.newBuilder()
                                               .setTitle("regular pr title")
                                               .addCommits(
                                                   Commit.newBuilder().setMessage("fix: something [ci skip]").build())
                                               .build())
                                    .build())
                            .build())
                    .build())
            .build();
    assertThat(mapper.checkIfSkipCiExpressionIsPresent(filterRequestData)).isFalse();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCheckIfSkipCiExpressionIsPresentForBitbucketOnPrem() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .parseWebhookResponse(
                        ParseWebhookResponse.newBuilder()
                            .setPush(
                                PushHook.newBuilder().addCommits(Commit.newBuilder().setSha("sha").build()).build())
                            .build())
                    .originalEvent(TriggerWebhookEvent.builder().headers(List.of()).build())
                    .build())
            .build();
    doReturn(GitProvider.STASH).when(webhookParserSCMService).obtainWebhookSource(any());
    doReturn("This is a [pipeline skip] message").when(scmUtils).fetchCommitMessage(eq("sha"), any());
    boolean actual = mapper.checkIfSkipCiExpressionIsPresent(filterRequestData);
    assertThat(actual).isEqualTo(true);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCheckIfSkipCiExpressionIsPresentForBitbucketOnPrem_resolvesFirstCommitOnly() {
    // Bitbucket on-prem resolves the first commit through SCM and does not fetch the head commit on top
    // of it, so a push costs a single SCM call.
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .parseWebhookResponse(ParseWebhookResponse.newBuilder()
                                              .setPush(PushHook.newBuilder()
                                                           .addCommits(Commit.newBuilder().setSha("oldest-sha").build())
                                                           .addCommits(Commit.newBuilder().setSha("head-sha").build())
                                                           .setCommit(Commit.newBuilder().setSha("head-sha").build())
                                                           .build())
                                              .build())
                    .originalEvent(TriggerWebhookEvent.builder().headers(List.of()).build())
                    .build())
            .build();
    doReturn(GitProvider.STASH).when(webhookParserSCMService).obtainWebhookSource(any());
    doReturn("feat: change with [skip ci]").when(scmUtils).fetchCommitMessage(eq("oldest-sha"), any());

    assertThat(mapper.checkIfSkipCiExpressionIsPresent(filterRequestData)).isTrue();
    verify(scmUtils).fetchCommitMessage(eq("oldest-sha"), any());
    verify(scmUtils, never()).fetchCommitMessage(eq("head-sha"), any());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testCheckIfSkipCiExpressionIsPresentForBitbucketOnPrem_fallsBackToHeadCommit() {
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .parseWebhookResponse(ParseWebhookResponse.newBuilder()
                                              .setPush(PushHook.newBuilder()
                                                           .addCommits(Commit.newBuilder().setSha("oldest-sha").build())
                                                           .setCommit(Commit.newBuilder().setSha("head-sha").build())
                                                           .build())
                                              .build())
                    .originalEvent(TriggerWebhookEvent.builder().headers(List.of()).build())
                    .build())
            .build();
    doReturn(GitProvider.STASH).when(webhookParserSCMService).obtainWebhookSource(any());
    doReturn("").when(scmUtils).fetchCommitMessage(eq("oldest-sha"), any());
    doReturn("merge with [skip ci]").when(scmUtils).fetchCommitMessage(eq("head-sha"), any());

    assertThat(mapper.checkIfSkipCiExpressionIsPresent(filterRequestData)).isTrue();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void applyFilterTest_withOptimizationRetrieve() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    ClassLoader classLoader = getClass().getClassLoader();

    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();

    NGTriggerEntity triggerEntity =
        NGTriggerEntity.builder()
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("proj")
            .metadata(NGTriggerMetadata.builder()
                          .webhook(WebhookMetadata.builder()
                                       .type("GITHUB")
                                       .git(GitMetadata.builder().connectorIdentifier("account.con1").build())
                                       .build())
                          .build())
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .sourceRepoType("Github")
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload(pushPayload)
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
    doReturn(List.of(accountTriggerFilter, payloadConditionsTriggerFilter))
        .when(triggerFilterStore)
        .getWebhookTriggerFilters(any());
    doReturn(Collections.singletonList(triggerEntity)).when(ngTriggerService).findTriggersByCriteria(any());
    doReturn(WebhookEventMappingResponse.builder()
                 .parseWebhookResponse(parseWebhookResponse)
                 .failedToFindTrigger(false)
                 .build())
        .when(payloadConditionsTriggerFilter)
        .applyFilter(any());
    doReturn(WebhookPayloadData.builder()
                 .webhookEvent(PushWebhookEvent.builder().repository(repository1).branchName("main").build())
                 .originalEvent(triggerWebhookEvent)
                 .parseWebhookResponse(parseWebhookResponse)
                 .build())
        .when(webhookEventPayloadParser)
        .parseEvent(any());
    doReturn(false)
        .when(pmsFeatureFlagHelper)
        .isEnabled(ACCOUNT_ID, FeatureName.CDS_SKIP_WEBHOOK_TRIGGER_EXECUTION_ON_SPECIAL_KEYWORDS.toString());
    WebhookEventMappingResponse webhookEventMappingResponse = mapper.mapWebhookEventToTriggers(
        TriggerMappingRequestData.builder().triggerWebhookEvent(triggerWebhookEvent).build());
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isFalse();
    verify(payloadConditionsTriggerFilter, times(1)).applyFilter(any());
    verify(ngTriggerService, times(1)).findTriggersByCriteria(any());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void applyFilterTest_withOptimizationRetrieve_empty() throws IOException {
    Map<String, String> metadata = new HashMap<>();
    metadata.put("key1", "value1");
    metadata.put("key2", "value1value2");
    ClassLoader classLoader = getClass().getClassLoader();

    ParseWebhookResponse parseWebhookResponse =
        ParseWebhookResponse.newBuilder()
            .setPr(PullRequestHook.newBuilder().setPr(PullRequest.newBuilder().setNumber(2).build()).build())
            .setComment(IssueCommentHook.newBuilder()
                            .setIssue(Issue.newBuilder().setPr(PullRequest.newBuilder().build()).build())
                            .build())
            .setPush(PushHook.newBuilder().addCommits(Commit.newBuilder().build()).build())
            .build();

    TriggerWebhookEvent triggerWebhookEvent =
        TriggerWebhookEvent.builder()
            .sourceRepoType("Github")
            .headers(Arrays.asList(
                HeaderConfig.builder().key("content-type").values(Arrays.asList("application/json")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Arrays.asList("someValue")).build()))
            .payload(pushPayload)
            .createdAt(System.currentTimeMillis())
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
    doReturn(List.of(accountTriggerFilter, payloadConditionsTriggerFilter))
        .when(triggerFilterStore)
        .getWebhookTriggerFilters(any());
    doReturn(Collections.emptyList()).when(ngTriggerService).findTriggersByCriteria(any());
    doReturn(WebhookPayloadData.builder()
                 .webhookEvent(PushWebhookEvent.builder().repository(repository1).branchName("main").build())
                 .originalEvent(triggerWebhookEvent)
                 .parseWebhookResponse(parseWebhookResponse)
                 .build())
        .when(webhookEventPayloadParser)
        .parseEvent(any());
    WebhookEventMappingResponse webhookEventMappingResponse = mapper.mapWebhookEventToTriggers(
        TriggerMappingRequestData.builder().triggerWebhookEvent(triggerWebhookEvent).build());
    assertThat(webhookEventMappingResponse).isNotNull();
    assertThat(webhookEventMappingResponse.isFailedToFindTrigger()).isTrue();
    verify(payloadConditionsTriggerFilter, times(0)).applyFilter(any());
    verify(ngTriggerService, times(1)).findTriggersByCriteria(any());
  }
}