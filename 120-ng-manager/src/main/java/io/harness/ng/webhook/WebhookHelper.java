/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.BITBUCKET_SERVER_HEADER_KEY;
import static io.harness.constants.Constants.X_AMZ_SNS_MESSAGE_TYPE;
import static io.harness.constants.Constants.X_BIT_BUCKET_EVENT;
import static io.harness.constants.Constants.X_GIT_HUB_EVENT;
import static io.harness.constants.Constants.X_GIT_LAB_EVENT;
import static io.harness.constants.Constants.X_HARNESS_ARTIFACT_REGISTRY_TRIGGER;
import static io.harness.constants.Constants.X_HARNESS_TRIGGER;
import static io.harness.constants.Constants.X_VSS_HEADER;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_BRANCH_HOOK_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PR_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PUSH_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.AWS_CODECOMMIT;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.AZURE;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.BITBUCKET;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.GITHUB;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.GITLAB;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.HARNESS;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.HARNESS_ARTIFACT_REGISTRY;
import static io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType.UNRECOGNIZED;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.CHECK;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.CREATE_BRANCH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.DELETE_BRANCH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.ISSUE_COMMENT;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.MERGE_QUEUE;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PR;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.PUSH;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookEventType.RELEASE;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType.CUSTOM;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType.GIT;
import static io.harness.eventsframework.webhookpayloads.webhookdata.WebhookTriggerType.HARNESS_REGISTRY;
import static io.harness.security.PrincipalProtoMapper.toPrincipalProto;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.webhookpayloads.webhookdata.EventHeader;
import io.harness.eventsframework.webhookpayloads.webhookdata.GitDetails;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoSubType;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookAllPayloadData;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.entities.WebhookEvent.WebhookEventBuilder;
import io.harness.product.ci.scm.proto.Action;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.service.WebhookParserSCMService;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.ws.rs.core.MultivaluedMap;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Slf4j
@Singleton
@OwnedBy(PIPELINE)
public class WebhookHelper {
  @Inject @Named(WEBHOOK_EVENTS_STREAM) private Producer webhookEventProducer;
  @Inject @Named(GIT_PUSH_EVENT_STREAM) private Producer gitPushEventProducer;
  @Inject @Named(GIT_PR_EVENT_STREAM) private Producer gitPrEventProducer;
  @Inject @Named(GIT_BRANCH_HOOK_EVENT_STREAM) private Producer gitBranchHookEventProducer;
  @Inject private WebhookParserSCMService webhookParserSCMService;
  @Inject private PmsFeatureFlagHelper ngFeatureFlagHelperService;
  @Inject private WebhookPayloadService webhookPayloadService;

  public static WebhookEvent toNGTriggerWebhookEvent(
      String payload, MultivaluedMap<String, String> httpHeaders, Scope webhookScope, String webhookIdentifier) {
    List<HeaderConfig> headerConfigs = new ArrayList<>();
    httpHeaders.forEach((k, v) -> headerConfigs.add(HeaderConfig.builder().key(k).values(v).build()));

    WebhookEventBuilder webhookEventBuilder =
        WebhookEvent.builder().accountId(webhookScope.getAccountIdentifier()).headers(headerConfigs).payload(payload);

    if (EmptyPredicate.isNotEmpty(webhookIdentifier)) {
      webhookEventBuilder.webhookScope(webhookScope).webhookIdentifier(webhookIdentifier);
    }

    return webhookEventBuilder.build();
  }

  public static boolean containsHeaderKey(Map<String, List<String>> headers, String key) {
    Set<String> headerKeys = headers.keySet();
    if (isEmpty(headerKeys) || isBlank(key)) {
      return false;
    }

    return headerKeys.contains(key) || headerKeys.contains(key.toLowerCase())
        || headerKeys.stream().anyMatch(key::equalsIgnoreCase);
  }

  public WebhookDTO generateWebhookDTO(
      WebhookEvent event, ParseWebhookResponse parseWebhookResponse, SourceRepoType sourceRepoType) {
    SourceRepoSubType sourceRepoSubType = getSourceRepoSubType(event);
    String accountId = event.getAccountId();
    String eventId = event.getUuid();
    String payload = event.getPayload();
    WebhookDTO.Builder builder = WebhookDTO.newBuilder()
                                     .setJsonPayload(payload)
                                     .addAllHeaders(generateEventHeaders(event))
                                     .setAccountId(accountId)
                                     .setEventId(eventId)
                                     .setTime(event.getCreatedAt());
    WebhookAllPayloadData.Builder webhookAllPayloadDataBuilder =
        WebhookAllPayloadData.newBuilder().setJsonPayload(payload);
    if (event.getPrincipal() != null) {
      builder.setPrincipal(toPrincipalProto(event.getPrincipal()));
    }

    if (sourceRepoType == HARNESS_ARTIFACT_REGISTRY) {
      builder.setWebhookTriggerType(HARNESS_REGISTRY);
    } else if (parseWebhookResponse == null) {
      builder.setWebhookTriggerType(CUSTOM);
    } else if (EmptyPredicate.isNotEmpty(event.getWebhookIdentifier())) {
      builder.setParsedResponse(parseWebhookResponse);
      webhookAllPayloadDataBuilder.setParsedResponse(parseWebhookResponse);
    } else {
      GitDetails gitDetails = generateGitDetails(parseWebhookResponse, sourceRepoType, sourceRepoSubType);
      builder.setParsedResponse(parseWebhookResponse)
          .setWebhookTriggerType(GIT)
          .setWebhookEventType(gitDetails.getEvent())
          .setGitDetails(gitDetails);
      webhookAllPayloadDataBuilder.setParsedResponse(parseWebhookResponse);
    }
    if (ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.CDS_STORE_WEBHOOK_PAYLOAD_IN_FILE_STORAGE)) {
      builder.setWebhookAllPayloadDataUuid(
          webhookPayloadService.saveWebhookAllPayloadData(accountId, eventId, webhookAllPayloadDataBuilder.build()));
    }
    return builder.build();
  }

  private static List<EventHeader> generateEventHeaders(WebhookEvent event) {
    return event.getHeaders()
        .stream()
        .map(headerConfig
            -> EventHeader.newBuilder().setKey(headerConfig.getKey()).addAllValues(headerConfig.getValues()).build())
        .collect(toList());
  }

  public static GitDetails generateGitDetails(
      ParseWebhookResponse parseWebhookResponse, SourceRepoType sourceRepoType, SourceRepoSubType sourceRepoSubType) {
    GitDetails.Builder builder = GitDetails.newBuilder().setSourceRepoType(sourceRepoType);
    if (parseWebhookResponse.hasPush()) {
      builder.setEvent(PUSH);
    } else if (parseWebhookResponse.hasPr()) {
      builder.setEvent(PR);
    } else if (parseWebhookResponse.hasComment()) {
      builder.setEvent(ISSUE_COMMENT);
    } else if (parseWebhookResponse.hasBranch()) {
      if (parseWebhookResponse.getBranch().getAction() == Action.CREATE) {
        builder.setEvent(CREATE_BRANCH);
      } else if (parseWebhookResponse.getBranch().getAction() == Action.DELETE) {
        builder.setEvent(DELETE_BRANCH);
      }
    } else if (parseWebhookResponse.hasRelease()) {
      builder.setEvent(RELEASE);
    } else if (parseWebhookResponse.hasCheckHook()) {
      builder.setEvent(CHECK);
    } else if (parseWebhookResponse.hasMergeQueue()) {
      builder.setEvent(MERGE_QUEUE);
    }

    if (!isNull(sourceRepoSubType)) {
      builder.setSourceRepoSubType(sourceRepoSubType);
    }
    return builder.build();
  }

  public static SourceRepoType getSourceRepoType(WebhookEvent event) {
    Map<String, List<String>> headers =
        event.getHeaders().stream().collect(Collectors.toMap(HeaderConfig::getKey, HeaderConfig::getValues));

    SourceRepoType sourceRepoType = UNRECOGNIZED;
    if (containsHeaderKey(headers, X_GIT_HUB_EVENT)) {
      sourceRepoType = GITHUB;
    } else if (containsHeaderKey(headers, X_GIT_LAB_EVENT)) {
      sourceRepoType = GITLAB;
    } else if (containsHeaderKey(headers, X_BIT_BUCKET_EVENT)) {
      sourceRepoType = BITBUCKET;
    } else if (containsHeaderKey(headers, X_AMZ_SNS_MESSAGE_TYPE)) {
      sourceRepoType = AWS_CODECOMMIT;
    } else if (containsHeaderKey(headers, X_VSS_HEADER)) {
      sourceRepoType = AZURE;
    } else if (containsHeaderKey(headers, X_HARNESS_TRIGGER)) {
      sourceRepoType = HARNESS;
    } else if (containsHeaderKey(headers, X_HARNESS_ARTIFACT_REGISTRY_TRIGGER)) {
      sourceRepoType = HARNESS_ARTIFACT_REGISTRY;
    } else {
      log.info("Got unrecognized source repo type for the webhook {}", event.getUuid());
    }

    return sourceRepoType;
  }

  public List<Producer> getProducerListForEvent(WebhookDTO webhookDTO) {
    List<Producer> producers = new ArrayList<>();
    if (ngFeatureFlagHelperService.isEnabled(webhookDTO.getAccountId(), FeatureName.PIE_PROCESS_TRIGGER_SEQUENTIALLY)
        && isGitPushEvent(webhookDTO)) {
      producers.add(gitPushEventProducer);
    } else if (ngFeatureFlagHelperService.isEnabled(
                   webhookDTO.getAccountId(), FeatureName.PIE_PROCESS_TRIGGER_SEQUENTIALLY)
        && isGitPREvent(webhookDTO)) {
      producers.add(gitPrEventProducer);
    } else {
      producers.add(webhookEventProducer);
      if (webhookDTO.hasParsedResponse() && webhookDTO.hasGitDetails()) {
        if (PUSH == webhookDTO.getGitDetails().getEvent()) {
          producers.add(gitPushEventProducer);
        } else if (PR == webhookDTO.getGitDetails().getEvent()) {
          producers.add(gitPrEventProducer);
        } else if (CREATE_BRANCH == webhookDTO.getGitDetails().getEvent()
            || DELETE_BRANCH == webhookDTO.getGitDetails().getEvent()) {
          producers.add(gitBranchHookEventProducer);
        }
        // Here we can add more logic if need to add more event topics.
      }
    }

    return producers;
  }

  public ParseWebhookResponse invokeScmService(WebhookEvent event) {
    try {
      Stopwatch stopwatch = Stopwatch.createStarted();
      ParseWebhookResponse parseWebhookResponse =
          webhookParserSCMService.parseWebhookUsingSCMAPI(event.getHeaders(), event.getPayload());
      log.info("Finished parsing webhook payload in {} ", stopwatch.elapsed(TimeUnit.SECONDS));
      return parseWebhookResponse;
    } catch (Exception exception) {
      logIfScmUnavailableException(event, exception);
    }

    // This failure could also mean, SCM could not parse payload. This may be some event SCM does not yet support.
    // We still need to continue, as someone might have configured Custom trigger on this.
    return null;
  }

  private void logIfScmUnavailableException(WebhookEvent event, Exception exception) {
    if (StatusRuntimeException.class.isAssignableFrom(exception.getClass())) {
      StatusRuntimeException e = (StatusRuntimeException) exception;

      if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
        // SCM service could not be accessed.
        log.error(new StringBuilder(128)
                      .append("SCM service unavailable for parsing webhook payload. EventId")
                      .append(event.getUuid())
                      .append(", Exception: ")
                      .append(e)
                      .toString());
      }
    }
  }

  private boolean isGitPushEvent(WebhookDTO webhookDTO) {
    return webhookDTO.hasParsedResponse() && webhookDTO.hasGitDetails()
        && PUSH == webhookDTO.getGitDetails().getEvent();
  }

  private boolean isGitPREvent(WebhookDTO webhookDTO) {
    return webhookDTO.hasParsedResponse() && webhookDTO.hasGitDetails() && PR == webhookDTO.getGitDetails().getEvent();
  }

  public static SourceRepoSubType getSourceRepoSubType(WebhookEvent webhookEvent) {
    Map<String, List<String>> headers =
        webhookEvent.getHeaders().stream().collect(Collectors.toMap(HeaderConfig::getKey, HeaderConfig::getValues));
    SourceRepoSubType sourceRepoSubType = SourceRepoSubType.UNKNOWN;
    if (containsHeaderKey(headers, X_BIT_BUCKET_EVENT) && containsHeaderKey(headers, BITBUCKET_SERVER_HEADER_KEY)) {
      sourceRepoSubType = SourceRepoSubType.STASH;
    }
    return sourceRepoSubType;
  }
}
