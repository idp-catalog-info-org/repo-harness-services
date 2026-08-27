/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.NGCommonEntityConstants.GENERIC_WEBHOOK_TYPE;
import static io.harness.NGCommonEntityConstants.NO_AUTH_TYPE_WEBHOOK;
import static io.harness.NGCommonEntityConstants.SLACK_WEBHOOK_TYPE;
import static io.harness.constants.Constants.X_GIT_HUB_EVENT;
import static io.harness.constants.Constants.X_HARNESS_ARTIFACT_REGISTRY_TRIGGER;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENT;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_PUSH_EVENT;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.HARI;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorScopeHelper;
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.task.scm.GitWebhookTaskType;
import io.harness.eraro.ErrorCode;
import io.harness.eventsframework.webhookpayloads.webhookdata.GitDetails;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.runtime.SCMRuntimeException;
import io.harness.gitsync.common.service.ScmClientFacilitatorService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.gitxwebhooks.entity.GenericWebhookSpec;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhookEvent;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventService;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.EnqueueRequest;
import io.harness.hsqs.client.model.EnqueueResponse;
import io.harness.hsqs.client.model.QueueServiceClientConfig;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.config.NextGenConfiguration;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ng.webhook.UpsertWebhookResponseDTO;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.WebhookHmacHelper;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.product.ci.scm.proto.EventBridge;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.product.ci.scm.proto.PushHook;
import io.harness.product.ci.scm.proto.WebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PmsFeatureFlagHelper;

import com.amazonaws.services.sns.model.InvalidStateException;
import java.net.MalformedURLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.ws.rs.core.MultivaluedHashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class WebhookServiceImplTest extends CategoryTest {
  @InjectMocks @Spy DefaultWebhookServiceImpl webhookService;
  @Mock AccountOrgProjectHelper accountOrgProjectHelper;
  @Mock WebhookEventRepository webhookEventRepository;
  @Mock ConnectorService connectorService;
  @Mock ConnectorScopeHelper connectorScopeHelper;
  @Mock ScmClientFacilitatorService scmClientFacilitatorService;
  @Mock ScmOrchestratorService scmOrchestratorService;
  @Mock NextGenConfiguration nextGenConfiguration;
  @Mock WebhookHelper webhookHelper;
  @Mock WebhookHmacHelper webhookHmacHelper;
  @Mock HsqsClientService hsqsClientService;

  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock PmsFeatureFlagHelper ngFeatureFlagHelperService;
  @Mock GitXWebhookEventService gitXWebhookEventService;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperServiceNg;
  @Mock PersistentLocker persistentLocker;

  @InjectMocks WebhookServiceImpl webhookServiceImpl;
  private String accountId = "accountId";
  private String orgId = "orgId";
  private String projectId = "projectId";
  private String parentUniqueId = "parentUnique";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARI)
  @Category(UnitTests.class)
  public void getTargetUrlTest() throws MalformedURLException, IllegalAccessException {
    doReturn("https://app.harness.io/gateway/ng/api/").when(webhookService).getWebhookBaseUrl();
    doReturn(null).when(accountOrgProjectHelper).getVanityUrl("abcde");
    final String targetUrl = webhookService.getTargetUrl("abcde");
    assertThat(targetUrl).isEqualTo("https://app.harness.io/gateway/ng/api/webhook?accountIdentifier=abcde");
    doReturn("https://app.harness.io/gateway/ng/api").when(webhookService).getWebhookBaseUrl();
    doReturn("https://vanity.harness.io/").when(accountOrgProjectHelper).getVanityUrl("abcde");
    final String targetUrl2 = webhookService.getTargetUrl("abcde");
    assertThat(targetUrl2).isEqualTo("https://vanity.harness.io/ng/api/webhook?accountIdentifier=abcde");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testAddEventToQueue() {
    WebhookEvent webhookEvent = WebhookEvent.builder().accountId("acc").build();
    when(webhookEventRepository.save(webhookEvent)).thenReturn(webhookEvent);
    assertThat(webhookServiceImpl.addEventToQueue(webhookEvent)).isEqualTo(webhookEvent);

    when(webhookEventRepository.save(webhookEvent)).thenThrow(new InvalidRequestException("message"));
    assertThatThrownBy(() -> webhookServiceImpl.addEventToQueue(webhookEvent))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("message");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUpsertWebhook() {
    doReturn("https://app.harness.io/gateway/ng/api/").when(webhookService).getWebhookBaseUrl();
    doReturn(null).when(accountOrgProjectHelper).getVanityUrl("abcde");
    when(ngFeatureFlagHelperServiceNg.isEnabled(any(), any())).thenReturn(false);

    UpsertWebhookRequestDTO upsertWebhookRequestDTO = UpsertWebhookRequestDTO.builder()
                                                          .accountIdentifier(accountId)
                                                          .projectIdentifier(projectId)
                                                          .orgIdentifier(orgId)
                                                          .connectorIdentifierRef("identifier")
                                                          .build();
    CreateWebhookResponse createWebhookResponse =
        CreateWebhookResponse.newBuilder().setWebhook(WebhookResponse.newBuilder().build()).setStatus(200).build();
    ConnectorResponseDTO connectorResponseDTO =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder().connectorConfig(GithubConnectorDTO.builder().build()).build())
            .build();
    when(connectorService.getByRef(accountId, orgId, projectId, "identifier"))
        .thenReturn(Optional.of(connectorResponseDTO));
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
             any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class)))
        .thenReturn(createWebhookResponse);
    when(scmClientFacilitatorService.upsertWebhook(upsertWebhookRequestDTO,
             "https://app.harness.io/gateway/ng/api/webhook?accountIdentifier=abcde", GitWebhookTaskType.UPSERT, null))
        .thenReturn(createWebhookResponse);
    assertThat(webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .isEqualTo(UpsertWebhookResponseDTO.builder()
                       .status(200)
                       .error("")
                       .webhookResponse(WebhookResponse.newBuilder().build())
                       .build());

    doThrow(new ExplanationException("message", new ScmException(ErrorCode.SCM_UNAUTHORIZED)))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
            any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class));
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("The credentials provided in the Github connector identifier are invalid or have expired. message")
        .isInstanceOf(ScmUnauthorizedException.class);

    doThrow(new ExplanationException("message", new InvalidRequestException("message")))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
            any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class));
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("message")
        .isInstanceOf(ExplanationException.class);

    doThrow(new HintException("message", new HintException("message", new ScmException(ErrorCode.SCM_UNAUTHORIZED))))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
            any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class));
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("The credentials provided in the Github connector identifier are invalid or have expired. message")
        .isInstanceOf(ScmUnauthorizedException.class);

    doThrow(new HintException("message", new InvalidRequestException("message")))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
            any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class));
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("message")
        .isInstanceOf(HintException.class);

    doThrow(new ScmException("message", ErrorCode.SCM_UNAUTHORIZED))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
            any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class));
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("The credentials provided in the Github connector identifier are invalid or have expired. "
            + "message")
        .isInstanceOf(ScmUnauthorizedException.class);

    doThrow(new SCMRuntimeException("message"))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
            any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class));
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("Unable to connect to Git Provider. Please check if credentials provided are correct and the repo "
            + "url is correct.")
        .isInstanceOf(ScmBadRequestException.class);

    doThrow(new InvalidRequestException("message"))
        .when(scmOrchestratorService)
        .processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
            any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class));
    assertThatThrownBy(() -> webhookService.upsertWebhook(upsertWebhookRequestDTO))
        .hasMessage("message")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetScmConnectorException() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountId)
                              .orgIdentifier(orgId)
                              .projectIdentifier(projectId)
                              .uniqueId(parentUniqueId)
                              .build();
    when(connectorScopeHelper.getConnectorScopeInfo(any(Scope.class), eq("identifier"))).thenReturn(scopeInfo);
    when(connectorService.get(eq(scopeInfo), eq("identifier"))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> webhookService.getScmConnector(accountId, orgId, projectId, parentUniqueId, "identifier"))
        .isInstanceOf(ConnectorNotFoundException.class)
        .hasMessage("No connector found for accountIdentifier: [" + accountId + "], orgIdentifier : [" + orgId
            + "], projectIdentifier : [" + projectId + "], connectorRef : [identifier]");

    ConnectorResponseDTO connectorResponseDTO =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder().connectorConfig(DockerConnectorDTO.builder().build()).build())
            .build();
    when(connectorService.get(eq(scopeInfo), eq("identifier"))).thenReturn(Optional.of(connectorResponseDTO));
    assertThatThrownBy(() -> webhookService.getScmConnector(accountId, orgId, projectId, parentUniqueId, "identifier"))
        .isInstanceOf(UnexpectedException.class)
        .hasMessage("The connector with the  identifier [null], accountIdentifier [accountId], orgIdentifier [orgId], "
            + "projectIdentifier [projectId] is not an scm connector");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGenerateWebhookDTOAndEnqueue() {
    when(gitSyncSdkService.isGitSimplificationEnabled(anyString(), anyString(), anyString())).thenReturn(false);
    doReturn(QueueServiceClientConfig.builder().topic("topic1").build())
        .when(nextGenConfiguration)
        .getQueueServiceClientConfig();
    WebhookEvent event =
        WebhookEvent.builder()
            .accountId("accountId")
            .uuid(generateUuid())
            .createdAt(0L)
            .headers(
                List.of(HeaderConfig.builder().key(X_GIT_HUB_EVENT).values(Collections.singletonList("value")).build()))
            .build();
    doReturn(null).when(webhookHelper).invokeScmService(event);
    WebhookDTO webhookDTO =
        WebhookDTO.newBuilder()
            .setAccountId("accountId")
            .setGitDetails(GitDetails.newBuilder()
                               .setSourceRepoType(SourceRepoType.GITHUB)
                               .setEvent(WebhookEventType.PUSH)
                               .build())
            .setParsedResponse(ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().build()).build())
            .build();
    doReturn(webhookDTO).when(webhookHelper).generateWebhookDTO(event, null, SourceRepoType.GITHUB);
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic("topic1" + WEBHOOK_EVENT)
                                        .subTopic("accountId")
                                        .producerName("topic1" + WEBHOOK_EVENT)
                                        .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                        .build();
    EnqueueRequest pushEnqueueRequest = EnqueueRequest.builder()
                                            .topic("topic1" + WEBHOOK_PUSH_EVENT)
                                            .subTopic("accountId")
                                            .producerName("topic1" + WEBHOOK_PUSH_EVENT)
                                            .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                            .build();
    doReturn(EnqueueResponse.builder().itemId("itemId").build()).when(hsqsClientService).enqueue(enqueueRequest);
    doReturn(EnqueueResponse.builder().itemId("itemId2").build()).when(hsqsClientService).enqueue(pushEnqueueRequest);
    assertThatCode(() -> webhookServiceImpl.generateWebhookDTOAndEnqueue(event)).doesNotThrowAnyException();
    verify(hsqsClientService, times(1)).enqueue(enqueueRequest);
    verify(hsqsClientService, times(1)).enqueue(pushEnqueueRequest);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGenerateWebhookDTOAndEnqueueForHARWebhookEvent() {
    when(gitSyncSdkService.isGitSimplificationEnabled(anyString(), anyString(), anyString())).thenReturn(false);
    doReturn(QueueServiceClientConfig.builder().topic("topic1").build())
        .when(nextGenConfiguration)
        .getQueueServiceClientConfig();
    WebhookEvent event = WebhookEvent.builder()
                             .accountId("accountId")
                             .uuid(generateUuid())
                             .createdAt(0L)
                             .headers(List.of(HeaderConfig.builder()
                                                  .key(X_HARNESS_ARTIFACT_REGISTRY_TRIGGER)
                                                  .values(Collections.singletonList("value"))
                                                  .build()))
                             .build();
    WebhookDTO webhookDTO = WebhookDTO.newBuilder()
                                .setAccountId("accountId")
                                .setWebhookTriggerType(WebhookTriggerType.HARNESS_REGISTRY)
                                .build();
    doReturn(webhookDTO).when(webhookHelper).generateWebhookDTO(event, null, SourceRepoType.HARNESS_ARTIFACT_REGISTRY);
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic("topic1" + WEBHOOK_EVENT)
                                        .subTopic("accountId")
                                        .producerName("topic1" + WEBHOOK_EVENT)
                                        .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                        .build();
    EnqueueRequest enqueueStepRequest = EnqueueRequest.builder()
                                            .topic("topic1EventListenerStepEvent")
                                            .subTopic("accountId")
                                            .producerName("topic1EventListenerStepEvent")
                                            .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                            .build();
    doReturn(EnqueueResponse.builder().itemId("itemId").build()).when(hsqsClientService).enqueue(enqueueRequest);
    doReturn(EnqueueResponse.builder().itemId("itemId2").build()).when(hsqsClientService).enqueue(enqueueStepRequest);
    assertThatCode(() -> webhookServiceImpl.generateWebhookDTOAndEnqueue(event)).doesNotThrowAnyException();
    verify(hsqsClientService, times(1)).enqueue(enqueueRequest);
    verify(hsqsClientService, times(1)).enqueue(enqueueStepRequest);
    verify(webhookHelper, times(0)).invokeScmService(any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGenerateWebhookDTOAndEnqueueForSequentialProcessingOfTriggers() {
    when(gitSyncSdkService.isGitSimplificationEnabled(anyString(), anyString(), anyString())).thenReturn(true);
    when(ngFeatureFlagHelperService.isEnabled(any(), (FeatureName) any())).thenReturn(true);
    doReturn(QueueServiceClientConfig.builder().topic("topic1").build())
        .when(nextGenConfiguration)
        .getQueueServiceClientConfig();
    WebhookEvent event =
        WebhookEvent.builder()
            .accountId("accountId")
            .uuid(generateUuid())
            .createdAt(0L)
            .headers(
                List.of(HeaderConfig.builder().key(X_GIT_HUB_EVENT).values(Collections.singletonList("value")).build()))
            .build();
    doReturn(null).when(webhookHelper).invokeScmService(event);
    WebhookDTO webhookDTO =
        WebhookDTO.newBuilder()
            .setAccountId("accountId")
            .setGitDetails(GitDetails.newBuilder()
                               .setSourceRepoType(SourceRepoType.GITHUB)
                               .setEvent(WebhookEventType.PUSH)
                               .build())
            .setParsedResponse(ParseWebhookResponse.newBuilder().setPush(PushHook.newBuilder().build()).build())
            .build();
    doReturn(webhookDTO).when(webhookHelper).generateWebhookDTO(event, null, SourceRepoType.GITHUB);
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic("topic1" + WEBHOOK_EVENT)
                                        .subTopic("accountId")
                                        .producerName("topic1" + WEBHOOK_EVENT)
                                        .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                        .build();
    EnqueueRequest pushEnqueueRequest = EnqueueRequest.builder()
                                            .topic("NGGitXWebhookPushEvent")
                                            .subTopic("accountId")
                                            .producerName("NGGitXWebhookPushEvent")
                                            .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                            .build();
    doReturn(EnqueueResponse.builder().itemId("itemId2").build()).when(hsqsClientService).enqueue(pushEnqueueRequest);
    assertThatCode(() -> webhookServiceImpl.generateWebhookDTOAndEnqueue(event)).doesNotThrowAnyException();
    verify(hsqsClientService, times(0)).enqueue(enqueueRequest);
    verify(hsqsClientService, times(1)).enqueue(pushEnqueueRequest);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testGenerateWebhookDTOAndEnqueueForSequentialProcessingOfEventListenerStep() {
    when(gitSyncSdkService.isGitSimplificationEnabled(anyString(), anyString(), anyString())).thenReturn(true);
    doReturn(QueueServiceClientConfig.builder().topic("topic1").build())
        .when(nextGenConfiguration)
        .getQueueServiceClientConfig();
    WebhookEvent event = WebhookEvent.builder()
                             .accountId("accountId")
                             .uuid(generateUuid())
                             .createdAt(0L)
                             .headers(new ArrayList<>())
                             .build();
    doReturn(null).when(webhookHelper).invokeScmService(event);
    WebhookDTO webhookDTO =
        WebhookDTO.newBuilder()
            .setAccountId("accountId")
            .setParsedResponse(
                ParseWebhookResponse.newBuilder().setEventBridge(EventBridge.newBuilder().build()).build())
            .build();
    doReturn(webhookDTO).when(webhookHelper).generateWebhookDTO(event, null, SourceRepoType.UNRECOGNIZED);
    EnqueueRequest enqueueRequest = EnqueueRequest.builder()
                                        .topic("topic1WebhookEvent")
                                        .subTopic("accountId")
                                        .producerName("topic1WebhookEvent")
                                        .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                        .build();
    EnqueueRequest enqueueStepRequest = EnqueueRequest.builder()
                                            .topic("topic1EventListenerStepEvent")
                                            .subTopic("accountId")
                                            .producerName("topic1EventListenerStepEvent")
                                            .payload(RecastOrchestrationUtils.toJson(webhookDTO))
                                            .build();
    doReturn(EnqueueResponse.builder().itemId("itemId1").build()).when(hsqsClientService).enqueue(enqueueRequest);
    doReturn(EnqueueResponse.builder().itemId("itemId2").build()).when(hsqsClientService).enqueue(enqueueStepRequest);
    assertThatCode(() -> webhookServiceImpl.generateWebhookDTOAndEnqueue(event)).doesNotThrowAnyException();
    verify(hsqsClientService, times(1)).enqueue(enqueueRequest);
    verify(hsqsClientService, times(1)).enqueue(enqueueStepRequest);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testVerifyHmacFailureVerificationCreationOfGenericWebhooks() {
    doThrow(new InvalidRequestException("Failed to verify HMAC signature for webhook"))
        .when(webhookHmacHelper)
        .verifyHMACSignature(any(), any(), any());
    assertThatThrownBy(
        ()
            -> webhookServiceImpl.createWebhookEvent(Scope.builder().accountIdentifier("accountId").build(),
                GitXWebhook.builder().webhookType(GENERIC_WEBHOOK_TYPE).build(), new MultivaluedHashMap<>(), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to verify HMAC signature for webhook");

    doThrow(new InvalidRequestException("Failed to verify HMAC signature for slack"))
        .when(webhookHmacHelper)
        .verifyHMACSignatureForSlack(any(), any(), any());
    assertThatThrownBy(
        ()
            -> webhookServiceImpl.createWebhookEvent(
                null, GitXWebhook.builder().webhookType(SLACK_WEBHOOK_TYPE).build(), new MultivaluedHashMap<>(), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to verify HMAC signature for slack");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testSuccessfulCreationOfGenericWebhooks() {
    var id = UUID.randomUUID().toString();
    when(ngFeatureFlagHelperService.isEnabled(eq(accountId), eq(FeatureName.CDS_QUEUE_SERVICE_FOR_TRIGGERS)))
        .thenReturn(false);
    doCallRealMethod().when(webhookHmacHelper).verifyHMACSignature(any(GitXWebhook.class), anyString(), anyList());
    when(webhookEventRepository.save(any(WebhookEvent.class)))
        .thenReturn(WebhookEvent.builder()
                        .webhookIdentifier("identifier")
                        .uuid(id)
                        .createdAt(LocalDateTime.now().plusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli())
                        .build());
    when(gitXWebhookEventService.createWebhookEvent(any(), anyString(), anyString(), anyString(), anyLong()))
        .thenReturn(GitXWebhookEvent.builder().eventIdentifier(UUID.randomUUID().toString()).build());
    var value = webhookServiceImpl.createWebhookEvent(Scope.builder().accountIdentifier(accountId).build(),
        GitXWebhook.builder()
            .identifier("identifier")
            .webhookType(GENERIC_WEBHOOK_TYPE)
            .spec(GenericWebhookSpec.builder().authType(NO_AUTH_TYPE_WEBHOOK).build())
            .build(),
        new MultivaluedHashMap<>(), "{\"randomKey\":\"randomValue\"}");
    verify(webhookEventRepository).save(any(WebhookEvent.class));
    verify(gitXWebhookEventService).createWebhookEvent(any(), anyString(), anyString(), anyString(), anyLong());
    assertThat(value).isNotNull();
    assertThat(value.getUuid()).isEqualTo(id);
    assertThat(value.getWebhookIdentifier()).isEqualTo("identifier");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testInternalServerErrorWhenFailToPushToQueue() {
    when(ngFeatureFlagHelperService.isEnabled(eq(accountId), eq(FeatureName.CDS_QUEUE_SERVICE_FOR_TRIGGERS)))
        .thenReturn(false);
    doCallRealMethod().when(webhookHmacHelper).verifyHMACSignature(any(GitXWebhook.class), anyString(), anyList());
    when(webhookEventRepository.save(any(WebhookEvent.class)))
        .thenThrow(new InvalidStateException("Failed to queue event"));
    assertThatThrownBy(
        ()
            -> webhookServiceImpl.createWebhookEvent(Scope.builder().accountIdentifier("accountId").build(),
                GitXWebhook.builder()
                    .identifier("identifier")
                    .webhookType(GENERIC_WEBHOOK_TYPE)
                    .spec(GenericWebhookSpec.builder().authType(NO_AUTH_TYPE_WEBHOOK).build())
                    .build(),
                new MultivaluedHashMap<>(), "{\"randomKey\":\"randomValue\"}"))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessageContaining("Failed to queue event");
    verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    verify(gitXWebhookEventService, never())
        .createWebhookEvent(any(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testInternalServerErrorWhenSavingEventToDB() {
    when(ngFeatureFlagHelperService.isEnabled(eq(accountId), eq(FeatureName.CDS_QUEUE_SERVICE_FOR_TRIGGERS)))
        .thenReturn(false);
    doCallRealMethod().when(webhookHmacHelper).verifyHMACSignature(any(GitXWebhook.class), anyString(), anyList());
    when(webhookEventRepository.save(any(WebhookEvent.class)))
        .thenReturn(WebhookEvent.builder()
                        .webhookIdentifier("identifier")
                        .uuid(UUID.randomUUID().toString())
                        .createdAt(LocalDateTime.now().plusDays(2).toInstant(ZoneOffset.UTC).toEpochMilli())
                        .build());
    when(gitXWebhookEventService.createWebhookEvent(any(), anyString(), anyString(), anyString(), anyLong()))
        .thenThrow(new InvalidStateException("Failed to save event history"));
    assertThatThrownBy(
        ()
            -> webhookServiceImpl.createWebhookEvent(Scope.builder().accountIdentifier(accountId).build(),
                GitXWebhook.builder()
                    .identifier("identifier")
                    .webhookType(GENERIC_WEBHOOK_TYPE)
                    .spec(GenericWebhookSpec.builder().authType(NO_AUTH_TYPE_WEBHOOK).build())
                    .build(),
                new MultivaluedHashMap<>(), "{\"randomKey\":\"randomValue\"}"))
        .isInstanceOf(InternalServerErrorException.class)
        .hasMessageContaining("Failed to save event history");
    verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
    verify(gitXWebhookEventService, times(1))
        .createWebhookEvent(any(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testBuildLockKey() {
    UpsertWebhookRequestDTO request = UpsertWebhookRequestDTO.builder()
                                          .accountIdentifier("acc123")
                                          .connectorIdentifierRef("conn456")
                                          .repoURL("https://github.com/user/repo")
                                          .build();
    String lockKey = webhookService.buildLockKey(request);
    assertThat(lockKey).isEqualTo("UPSERT_WEBHOOK_LOCK/acc123/conn456/https%3A%2F%2Fgithub.com%2Fuser%2Frepo");

    // Test with null repoURL
    UpsertWebhookRequestDTO requestWithNullRepo =
        UpsertWebhookRequestDTO.builder().accountIdentifier("acc123").connectorIdentifierRef("conn456").build();
    String lockKeyWithNullRepo = webhookService.buildLockKey(requestWithNullRepo);
    assertThat(lockKeyWithNullRepo).isEqualTo("UPSERT_WEBHOOK_LOCK/acc123/conn456/default");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpsertWebhookWithLockAcquired() {
    doReturn("https://app.harness.io/gateway/ng/api/").when(webhookService).getWebhookBaseUrl();
    doReturn(null).when(accountOrgProjectHelper).getVanityUrl("abcde");
    when(ngFeatureFlagHelperServiceNg.isEnabled(any(), any())).thenReturn(false);

    UpsertWebhookRequestDTO upsertWebhookRequestDTO = UpsertWebhookRequestDTO.builder()
                                                          .accountIdentifier(accountId)
                                                          .projectIdentifier(projectId)
                                                          .orgIdentifier(orgId)
                                                          .connectorIdentifierRef("identifier")
                                                          .repoURL("https://github.com/user/repo")
                                                          .build();
    CreateWebhookResponse createWebhookResponse =
        CreateWebhookResponse.newBuilder().setWebhook(WebhookResponse.newBuilder().build()).setStatus(200).build();
    ConnectorResponseDTO connectorResponseDTO =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder().connectorConfig(GithubConnectorDTO.builder().build()).build())
            .build();

    AcquiredLock mockLock = mock(AcquiredLock.class);
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(), any())).thenReturn(mockLock);
    when(connectorService.getByRef(accountId, orgId, projectId, "identifier"))
        .thenReturn(Optional.of(connectorResponseDTO));
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
             any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class)))
        .thenReturn(createWebhookResponse);

    UpsertWebhookResponseDTO response = webhookService.upsertWebhook(upsertWebhookRequestDTO);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    verify(persistentLocker, times(1)).waitToAcquireLockOptional(anyString(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpsertWebhookWithLockNotAcquired() {
    doReturn("https://app.harness.io/gateway/ng/api/").when(webhookService).getWebhookBaseUrl();
    doReturn(null).when(accountOrgProjectHelper).getVanityUrl("abcde");
    when(ngFeatureFlagHelperServiceNg.isEnabled(any(), any())).thenReturn(false);

    UpsertWebhookRequestDTO upsertWebhookRequestDTO = UpsertWebhookRequestDTO.builder()
                                                          .accountIdentifier(accountId)
                                                          .projectIdentifier(projectId)
                                                          .orgIdentifier(orgId)
                                                          .connectorIdentifierRef("identifier")
                                                          .repoURL("https://github.com/user/repo")
                                                          .build();
    CreateWebhookResponse createWebhookResponse =
        CreateWebhookResponse.newBuilder().setWebhook(WebhookResponse.newBuilder().build()).setStatus(200).build();
    ConnectorResponseDTO connectorResponseDTO =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder().connectorConfig(GithubConnectorDTO.builder().build()).build())
            .build();

    // Lock acquisition fails (returns null)
    when(persistentLocker.waitToAcquireLockOptional(anyString(), any(), any())).thenReturn(null);
    when(connectorService.getByRef(accountId, orgId, projectId, "identifier"))
        .thenReturn(Optional.of(connectorResponseDTO));
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
             any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class)))
        .thenReturn(createWebhookResponse);

    // Should proceed without lock (graceful degradation)
    UpsertWebhookResponseDTO response = webhookService.upsertWebhook(upsertWebhookRequestDTO);

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    verify(persistentLocker, times(1)).waitToAcquireLockOptional(anyString(), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testUpsertWebhookLockKeyFormat() {
    doReturn("https://app.harness.io/gateway/ng/api/").when(webhookService).getWebhookBaseUrl();
    doReturn(null).when(accountOrgProjectHelper).getVanityUrl("abcde");
    when(ngFeatureFlagHelperServiceNg.isEnabled(any(), any())).thenReturn(false);

    UpsertWebhookRequestDTO upsertWebhookRequestDTO = UpsertWebhookRequestDTO.builder()
                                                          .accountIdentifier(accountId)
                                                          .projectIdentifier(projectId)
                                                          .orgIdentifier(orgId)
                                                          .connectorIdentifierRef("github_connector")
                                                          .repoURL("https://github.com/org/repo")
                                                          .build();
    CreateWebhookResponse createWebhookResponse =
        CreateWebhookResponse.newBuilder().setWebhook(WebhookResponse.newBuilder().build()).setStatus(200).build();
    ConnectorResponseDTO connectorResponseDTO =
        ConnectorResponseDTO.builder()
            .connector(ConnectorInfoDTO.builder().connectorConfig(GithubConnectorDTO.builder().build()).build())
            .build();

    AcquiredLock mockLock = mock(AcquiredLock.class);
    when(persistentLocker.waitToAcquireLockOptional(
             eq("UPSERT_WEBHOOK_LOCK/accountId/github_connector/https%3A%2F%2Fgithub.com%2Forg%2Frepo"), any(), any()))
        .thenReturn(mockLock);
    when(connectorService.getByRef(accountId, orgId, projectId, "github_connector"))
        .thenReturn(Optional.of(connectorResponseDTO));
    when(scmOrchestratorService.processScmRequestUsingConnectorSettings(any(java.util.function.Function.class),
             any(io.harness.delegate.beans.connector.scm.intfc.ScmConnector.class)))
        .thenReturn(createWebhookResponse);

    UpsertWebhookResponseDTO response = webhookService.upsertWebhook(upsertWebhookRequestDTO);

    assertThat(response).isNotNull();
    verify(persistentLocker, times(1))
        .waitToAcquireLockOptional(
            eq("UPSERT_WEBHOOK_LOCK/accountId/github_connector/https%3A%2F%2Fgithub.com%2Forg%2Frepo"), any(), any());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testBuildLockKey_withRepoURL() {
    UpsertWebhookRequestDTO request = UpsertWebhookRequestDTO.builder()
                                          .accountIdentifier("acc1")
                                          .connectorIdentifierRef("conn1")
                                          .repoURL("https://github.com/org/repo")
                                          .build();

    String lockKey = webhookService.buildLockKey(request);

    assertThat(lockKey).isEqualTo("UPSERT_WEBHOOK_LOCK/acc1/conn1/https%3A%2F%2Fgithub.com%2Forg%2Frepo");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testBuildLockKey_withoutRepoURL() {
    UpsertWebhookRequestDTO request =
        UpsertWebhookRequestDTO.builder().accountIdentifier("acc1").connectorIdentifierRef("conn1").build();

    String lockKey = webhookService.buildLockKey(request);

    assertThat(lockKey).isEqualTo("UPSERT_WEBHOOK_LOCK/acc1/conn1/default");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testBuildLockKey_avoidsCollisions() {
    // These two URLs would collide with simple replace("/", "_") approach:
    // "https://org/my_repo" -> "https:__org_my_repo"
    // "https://org_my/repo" -> "https:__org_my_repo"

    UpsertWebhookRequestDTO request1 = UpsertWebhookRequestDTO.builder()
                                           .accountIdentifier("acc1")
                                           .connectorIdentifierRef("conn1")
                                           .repoURL("https://org/my_repo")
                                           .build();

    UpsertWebhookRequestDTO request2 = UpsertWebhookRequestDTO.builder()
                                           .accountIdentifier("acc1")
                                           .connectorIdentifierRef("conn1")
                                           .repoURL("https://org_my/repo")
                                           .build();

    String lockKey1 = webhookService.buildLockKey(request1);
    String lockKey2 = webhookService.buildLockKey(request2);

    // With URL encoding, these should produce different lock keys
    assertThat(lockKey1).isNotEqualTo(lockKey2);
    assertThat(lockKey1).isEqualTo("UPSERT_WEBHOOK_LOCK/acc1/conn1/https%3A%2F%2Forg%2Fmy_repo");
    assertThat(lockKey2).isEqualTo("UPSERT_WEBHOOK_LOCK/acc1/conn1/https%3A%2F%2Forg_my%2Frepo");
  }
}
