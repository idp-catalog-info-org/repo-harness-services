/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.constants.Constants.X_HARNESS_ARTIFACT_REGISTRY_TRIGGER;
import static io.harness.constants.Constants.X_HARNESS_TRIGGER;
import static io.harness.constants.Constants.X_HARNESS_WEBHOOK_SIGNATURE;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_BRANCH_HOOK_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PR_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PUSH_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM;
import static io.harness.gitsync.gitxwebhooks.metrics.GitXWebhookQueueOperationMetrics.WEBHOOK_BRANCH_EVENT_ENQUEUED;
import static io.harness.gitsync.gitxwebhooks.metrics.GitXWebhookQueueOperationMetrics.WEBHOOK_PULL_REQUEST_EVENT_ENQUEUED;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.WebhookHmacHelper;
import io.harness.ng.webhook.WebhookSecretsConfig;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.services.api.WebhookEventProcessingService;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;
import io.harness.rule.Owner;
import io.harness.service.WebhookParserSCMService;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.Lists;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.mongodb.core.MongoTemplate;

public class WebhookEventProcessingServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks WebhookEventProcessingServiceImpl webhookEventProcessingService;
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock MongoTemplate mongoTemplate;
  @Mock WebhookEventRepository webhookEventRepository;
  @Mock @Named(GIT_PUSH_EVENT_STREAM) private Producer gitPushEventProducer;
  @Mock @Named(WEBHOOK_EVENTS_STREAM) private Producer webhookEventProducer;
  @Mock WebhookParserSCMService webhookParserSCMService;
  @Mock WebhookHelper webhookHelper;
  @Mock WebhookHmacHelper webhookHmacHelper;
  @Mock NGEncryptedDataService encryptedDataService;
  @Mock WebhookSecretsConfig webhookSecretsConfig;
  @Mock PmsFeatureFlagHelper ngFeatureFlagHelperService;
  @Mock GitXWebhookService webhookService;
  @Mock MetricService metricService;

  @Before
  public void setup() {}

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testHandle_whenGenericWebhookWithScope_verifiesHmacAndDeletesEvent() {
    WebhookEvent webhookEvent = WebhookEvent.builder()
                                    .accountId("accountId")
                                    .webhookIdentifier("wh")
                                    .webhookScope(io.harness.beans.Scope.builder()
                                                      .accountIdentifier("accountId")
                                                      .orgIdentifier("org")
                                                      .projectIdentifier("proj")
                                                      .build())
                                    .payload("payload")
                                    .headers(Collections.emptyList())
                                    .build();

    GitXWebhook webhook =
        GitXWebhook.builder().webhookType(io.harness.NGCommonEntityConstants.GENERIC_WEBHOOK_TYPE).build();
    when(webhookService.getWebhookByIdentifier(eq("accountId"), eq("org"), eq("proj"), eq("wh")))
        .thenReturn(Optional.of(webhook));
    when(webhookHelper.invokeScmService(webhookEvent)).thenReturn(ParseWebhookResponse.newBuilder().build());
    when(webhookHelper.generateWebhookDTO(any(), any(), any())).thenReturn(WebhookDTO.newBuilder().build());
    when(webhookHelper.getProducerListForEvent(any())).thenReturn(Collections.emptyList());

    webhookEventProcessingService.handle(webhookEvent);

    verify(webhookHmacHelper, times(1)).verifyHMACSignature(eq(webhook), eq("payload"), eq(Collections.emptyList()));
    verify(webhookEventRepository, times(1)).delete(any());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testHandle_whenWebhookWithScopeAndNonMatchingType_doesNotVerifyHmacAndDeletesEvent() {
    WebhookEvent webhookEvent = WebhookEvent.builder()
                                    .accountId("accountId")
                                    .webhookIdentifier("wh")
                                    .webhookScope(io.harness.beans.Scope.builder()
                                                      .accountIdentifier("accountId")
                                                      .orgIdentifier("org")
                                                      .projectIdentifier("proj")
                                                      .build())
                                    .payload("payload")
                                    .headers(Collections.emptyList())
                                    .build();

    GitXWebhook webhook = GitXWebhook.builder().webhookType("custom").build();
    when(webhookService.getWebhookByIdentifier(eq("accountId"), eq("org"), eq("proj"), eq("wh")))
        .thenReturn(Optional.of(webhook));
    when(webhookHelper.invokeScmService(webhookEvent)).thenReturn(ParseWebhookResponse.newBuilder().build());
    when(webhookHelper.generateWebhookDTO(any(), any(), any())).thenReturn(WebhookDTO.newBuilder().build());
    when(webhookHelper.getProducerListForEvent(any())).thenReturn(Collections.emptyList());

    webhookEventProcessingService.handle(webhookEvent);

    verify(webhookHmacHelper, times(0)).verifyHMACSignature(any(), any(), any());
    verify(webhookHmacHelper, times(0)).verifyHMACSignatureForSlack(any(), any(), any());
    verify(webhookEventRepository, times(1)).delete(any());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testHandle_whenSlackWebhookWithScope_verifiesHmacAndDeletesEvent() {
    WebhookEvent webhookEvent = WebhookEvent.builder()
                                    .accountId("accountId")
                                    .webhookIdentifier("wh")
                                    .webhookScope(io.harness.beans.Scope.builder()
                                                      .accountIdentifier("accountId")
                                                      .orgIdentifier("org")
                                                      .projectIdentifier("proj")
                                                      .build())
                                    .payload("payload")
                                    .headers(Collections.emptyList())
                                    .build();

    GitXWebhook webhook =
        GitXWebhook.builder().webhookType(io.harness.NGCommonEntityConstants.SLACK_WEBHOOK_TYPE).build();
    when(webhookService.getWebhookByIdentifier(eq("accountId"), eq("org"), eq("proj"), eq("wh")))
        .thenReturn(Optional.of(webhook));
    when(webhookHelper.invokeScmService(webhookEvent)).thenReturn(ParseWebhookResponse.newBuilder().build());
    when(webhookHelper.generateWebhookDTO(any(), any(), any())).thenReturn(WebhookDTO.newBuilder().build());
    when(webhookHelper.getProducerListForEvent(any())).thenReturn(Collections.emptyList());

    webhookEventProcessingService.handle(webhookEvent);

    verify(webhookHmacHelper, times(1))
        .verifyHMACSignatureForSlack(eq(webhook), eq("payload"), eq(Collections.emptyList()));
    verify(webhookEventRepository, times(1)).delete(any());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testPublishWebhookEvent_whenWebhookStreamAndPayloadStored_removesPayloadFromMessage() throws Exception {
    WebhookEvent webhookEvent = WebhookEvent.builder().accountId("acc").build();
    ParseWebhookResponse parseWebhookResponse = ParseWebhookResponse.newBuilder().build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder().setAccountId("acc").setEventId("evt").setJsonPayload("abc").build();

    when(webhookHelper.generateWebhookDTO(eq(webhookEvent), eq(parseWebhookResponse), eq(SourceRepoType.GITHUB)))
        .thenReturn(webhookDTO);
    RedisProducer producer = Mockito.mock(RedisProducer.class);
    when(producer.getTopicName()).thenReturn(WEBHOOK_EVENTS_STREAM);
    when(webhookHelper.getProducerListForEvent(eq(webhookDTO))).thenReturn(Collections.singletonList(producer));
    when(ngFeatureFlagHelperService.isEnabled(eq("acc"), any(FeatureName.class))).thenReturn(true);

    webhookEventProcessingService.publishWebhookEvent(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB);

    org.mockito.ArgumentCaptor<Message> messageCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
    verify(producer, times(1)).send(messageCaptor.capture());
    WebhookDTO sent = WebhookDTO.parseFrom(messageCaptor.getValue().getData());
    assertThat(sent.getJsonPayload()).isEqualTo("");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testPublishWebhookEvent_whenGitPushStreamAndPayloadStored_removesPayloadFromMessage() throws Exception {
    WebhookEvent webhookEvent = WebhookEvent.builder().accountId("acc").build();
    ParseWebhookResponse parseWebhookResponse = ParseWebhookResponse.newBuilder().build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder().setAccountId("acc").setEventId("evt").setJsonPayload("abc").build();

    when(webhookHelper.generateWebhookDTO(eq(webhookEvent), eq(parseWebhookResponse), eq(SourceRepoType.GITHUB)))
        .thenReturn(webhookDTO);
    RedisProducer producer = Mockito.mock(RedisProducer.class);
    when(producer.getTopicName()).thenReturn(GIT_PUSH_EVENT_STREAM);
    when(webhookHelper.getProducerListForEvent(eq(webhookDTO))).thenReturn(Collections.singletonList(producer));
    when(ngFeatureFlagHelperService.isEnabled(eq("acc"), any(FeatureName.class))).thenReturn(true);

    webhookEventProcessingService.publishWebhookEvent(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB);

    org.mockito.ArgumentCaptor<Message> messageCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
    verify(producer, times(1)).send(messageCaptor.capture());
    WebhookDTO sent = WebhookDTO.parseFrom(messageCaptor.getValue().getData());
    assertThat(sent.getJsonPayload()).isEqualTo("");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    webhookEventProcessingService.registerIterators(5);
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(WebhookEventProcessingService.class), any());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testHandle() {
    List<Producer> producers = new ArrayList<>();
    WebhookEvent webhookEvent =
        WebhookEvent.builder()
            .accountId("accountId")
            .headers(Lists.newArrayList(
                HeaderConfig.builder().key("key").values(Collections.singletonList("value")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Collections.singletonList("1234567890")).build()))
            .build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder().build();
    ParseWebhookResponse parseWebhookResponse = ParseWebhookResponse.newBuilder().build();
    when(webhookHelper.invokeScmService(webhookEvent)).thenReturn(parseWebhookResponse);
    when(webhookHelper.generateWebhookDTO(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB))
        .thenReturn(WebhookDTO.newBuilder().build());
    producers.add(gitPushEventProducer);
    when(webhookHelper.getProducerListForEvent(webhookDTO)).thenReturn(producers);
    doReturn("")
        .when(gitPushEventProducer)
        .send(Message.newBuilder().setData(WebhookDTO.newBuilder().build().toByteString()).build());
    doNothing().when(webhookEventRepository).delete(any());
    webhookEventProcessingService.handle(webhookEvent);

    doThrow(new InvalidRequestException("message"))
        .when(webhookHelper)
        .generateWebhookDTO(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB);
    webhookEventProcessingService.handle(webhookEvent);
    verify(webhookHelper, times(1)).getProducerListForEvent(webhookDTO);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testHandle_whenWebhookIdentifierPresent_doesNotInvokeScmService_andDeletesEvent() {
    WebhookEvent webhookEvent = WebhookEvent.builder()
                                    .accountId("accountId")
                                    .webhookIdentifier("wh")
                                    .webhookScope(io.harness.beans.Scope.builder()
                                                      .accountIdentifier("accountId")
                                                      .orgIdentifier("org")
                                                      .projectIdentifier("proj")
                                                      .build())
                                    .payload("payload")
                                    .headers(Collections.emptyList())
                                    .build();

    when(webhookService.getWebhookByIdentifier(eq("accountId"), eq("org"), eq("proj"), eq("wh")))
        .thenReturn(Optional.empty());
    when(webhookHelper.generateWebhookDTO(any(), any(), any())).thenReturn(WebhookDTO.newBuilder().build());
    when(webhookHelper.getProducerListForEvent(any())).thenReturn(Collections.emptyList());

    webhookEventProcessingService.handle(webhookEvent);

    verify(webhookHelper, times(0)).invokeScmService(any());
    verify(webhookEventRepository, times(1)).delete(any());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testHandle_whenSourceRepoTypeUnrecognized_doesNotInvokeScmService_andDeletesEvent() {
    WebhookEvent webhookEvent =
        WebhookEvent.builder().accountId("accountId").payload("payload").headers(Collections.emptyList()).build();
    when(webhookHelper.generateWebhookDTO(any(), any(), any())).thenReturn(WebhookDTO.newBuilder().build());
    when(webhookHelper.getProducerListForEvent(any())).thenReturn(Collections.emptyList());

    webhookEventProcessingService.handle(webhookEvent);

    verify(webhookHelper, times(0)).invokeScmService(any());
    verify(webhookEventRepository, times(1)).delete(any());
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testPublishWebhookEvent_recordsMetrics_forPullRequestAndBranchStreams() {
    WebhookEvent webhookEvent = WebhookEvent.builder().accountId("acc").build();
    ParseWebhookResponse parseWebhookResponse = ParseWebhookResponse.newBuilder().build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder()
                                .setAccountId("acc")
                                .setEventId("evt")
                                .setParsedResponse(ParseWebhookResponse.newBuilder().build())
                                .build();

    when(webhookHelper.generateWebhookDTO(eq(webhookEvent), eq(parseWebhookResponse), eq(SourceRepoType.GITHUB)))
        .thenReturn(webhookDTO);

    RedisProducer prProducer = mock(RedisProducer.class);
    when(prProducer.getTopicName()).thenReturn(GIT_PR_EVENT_STREAM);
    when(prProducer.send(any())).thenReturn("");

    RedisProducer branchProducer = mock(RedisProducer.class);
    when(branchProducer.getTopicName()).thenReturn(GIT_BRANCH_HOOK_EVENT_STREAM);
    when(branchProducer.send(any())).thenReturn("");

    when(webhookHelper.getProducerListForEvent(eq(webhookDTO))).thenReturn(Arrays.asList(prProducer, branchProducer));

    webhookEventProcessingService.publishWebhookEvent(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB);

    verify(metricService, times(1)).incCounter(eq(WEBHOOK_PULL_REQUEST_EVENT_ENQUEUED));
    verify(metricService, times(1)).incCounter(eq(WEBHOOK_BRANCH_EVENT_ENQUEUED));
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testPublishWebhookEvent_whenOneProducerFails_continuesWithOtherProducer() {
    WebhookEvent webhookEvent = WebhookEvent.builder().accountId("acc").build();
    ParseWebhookResponse parseWebhookResponse = ParseWebhookResponse.newBuilder().build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder().setAccountId("acc").setEventId("evt").build();

    when(webhookHelper.generateWebhookDTO(eq(webhookEvent), eq(parseWebhookResponse), eq(SourceRepoType.GITHUB)))
        .thenReturn(webhookDTO);

    RedisProducer failingProducer = mock(RedisProducer.class);
    when(failingProducer.getTopicName()).thenReturn(GIT_PUSH_EVENT_STREAM);
    doThrow(new EventsFrameworkDownException("message")).when(failingProducer).send(any());

    RedisProducer successProducer = mock(RedisProducer.class);
    when(successProducer.getTopicName()).thenReturn(GIT_PUSH_EVENT_STREAM);
    when(successProducer.send(any())).thenReturn("");

    when(webhookHelper.getProducerListForEvent(eq(webhookDTO)))
        .thenReturn(Arrays.asList(failingProducer, successProducer));

    webhookEventProcessingService.publishWebhookEvent(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB);

    verify(successProducer, times(1)).send(any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleHAREvent() {
    List<Producer> producers = new ArrayList<>();
    Producer p = Mockito.mock(Producer.class);
    producers.add(p);
    WebhookEvent webhookEvent = WebhookEvent.builder()
                                    .accountId("accountId")
                                    .payload("test")
                                    .headers(Arrays.asList(HeaderConfig.builder()
                                                               .key(X_HARNESS_ARTIFACT_REGISTRY_TRIGGER)
                                                               .values(Collections.singletonList("value"))
                                                               .build(),
                                        HeaderConfig.builder()
                                            .key(X_HARNESS_WEBHOOK_SIGNATURE)
                                            .values(Collections.singletonList(
                                                "7c7e0aa72a145702bdd7ca49f811320767a2190edf6776f4f72a9a496363c16e"))
                                            .build()))
                                    .build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder().build();
    when(webhookSecretsConfig.getArtifactRegistry()).thenReturn("harKey");
    when(webhookHelper.generateWebhookDTO(webhookEvent, null, SourceRepoType.HARNESS_ARTIFACT_REGISTRY))
        .thenReturn(WebhookDTO.newBuilder().build());
    producers.add(webhookEventProducer);
    when(webhookHelper.getProducerListForEvent(webhookDTO)).thenReturn(producers);
    doReturn("")
        .when(webhookEventProducer)
        .send(Message.newBuilder().setData(WebhookDTO.newBuilder().build().toByteString()).build());
    doNothing().when(webhookEventRepository).delete(any());
    doReturn("").when(p).send(any());
    webhookEventProcessingService.handle(webhookEvent);
    verify(p, times(1)).send(any());

    doThrow(new InvalidRequestException("message"))
        .when(webhookHelper)
        .generateWebhookDTO(webhookEvent, null, SourceRepoType.HARNESS_ARTIFACT_REGISTRY);
    webhookEventProcessingService.handle(webhookEvent);
    verify(webhookHelper, times(1)).getProducerListForEvent(webhookDTO);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleCodeEvent() {
    List<Producer> producers = new ArrayList<>();
    Producer p = Mockito.mock(Producer.class);
    producers.add(p);
    WebhookEvent webhookEvent =
        WebhookEvent.builder()
            .accountId("accountId")
            .payload("test")
            .headers(Arrays.asList(
                HeaderConfig.builder().key(X_HARNESS_TRIGGER).values(Collections.singletonList("value")).build(),
                HeaderConfig.builder()
                    .key(X_HARNESS_WEBHOOK_SIGNATURE)
                    .values(
                        Collections.singletonList("453d9cac89b38a86b14e8bb3f0701c7c26778e0ce986859cb94ef6b6c6e839a3"))
                    .build()))
            .build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder().build();
    when(webhookSecretsConfig.getCode()).thenReturn("codeKey");
    when(webhookHelper.generateWebhookDTO(webhookEvent, null, SourceRepoType.HARNESS))
        .thenReturn(WebhookDTO.newBuilder().build());
    producers.add(webhookEventProducer);
    when(webhookHelper.getProducerListForEvent(webhookDTO)).thenReturn(producers);
    doReturn("")
        .when(webhookEventProducer)
        .send(Message.newBuilder().setData(WebhookDTO.newBuilder().build().toByteString()).build());
    doNothing().when(webhookEventRepository).delete(any());
    doReturn("").when(p).send(any());
    webhookEventProcessingService.handle(webhookEvent);
    verify(p, times(1)).send(any());

    doThrow(new InvalidRequestException("message"))
        .when(webhookHelper)
        .generateWebhookDTO(webhookEvent, null, SourceRepoType.HARNESS);
    webhookEventProcessingService.handle(webhookEvent);
    verify(webhookHelper, times(1)).getProducerListForEvent(webhookDTO);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testPublishWebhookEventException() {
    List<Producer> producers = new ArrayList<>();
    WebhookEvent webhookEvent =
        WebhookEvent.builder()
            .headers(Lists.newArrayList(
                HeaderConfig.builder().key("key").values(Collections.singletonList("value")).build(),
                HeaderConfig.builder().key("X-GitHub-Event").values(Collections.singletonList("1234567890")).build()))
            .build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder().setEventId("eventId").build();
    ParseWebhookResponse parseWebhookResponse = ParseWebhookResponse.newBuilder().build();
    when(webhookHelper.generateWebhookDTO(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB))
        .thenReturn(webhookDTO);
    producers.add(gitPushEventProducer);
    when(webhookHelper.getProducerListForEvent(webhookDTO)).thenReturn(producers);
    doThrow(new EventsFrameworkDownException("message"))
        .when(gitPushEventProducer)
        .send(Message.newBuilder().setData(webhookDTO.toByteString()).build());
    webhookEventProcessingService.publishWebhookEvent(webhookEvent, parseWebhookResponse, SourceRepoType.GITHUB);
    verify(webhookHelper, times(1)).getProducerListForEvent(webhookDTO);
  }
}
