/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.X_HARNESS_ARTIFACT_REGISTRY_TRIGGER;
import static io.harness.constants.Constants.X_HARNESS_TRIGGER;
import static io.harness.constants.Constants.X_HARNESS_WEBHOOK_SIGNATURE;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_BRANCH_HOOK_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PR_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.GIT_PUSH_EVENT_STREAM;
import static io.harness.eventsframework.EventsFrameworkConstants.WEBHOOK_EVENTS_STREAM;
import static io.harness.gitsync.gitxwebhooks.metrics.GitXWebhookQueueOperationMetrics.WEBHOOK_BRANCH_EVENT_ENQUEUED;
import static io.harness.gitsync.gitxwebhooks.metrics.GitXWebhookQueueOperationMetrics.WEBHOOK_PULL_REQUEST_EVENT_ENQUEUED;
import static io.harness.gitsync.gitxwebhooks.metrics.GitXWebhookQueueOperationMetrics.WEBHOOK_PUSH_EVENT_ENQUEUED;
import static io.harness.mongo.iterator.pojos.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.api.AbstractProducer;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.impl.redis.RedisProducer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.webhookpayloads.webhookdata.SourceRepoType;
import io.harness.eventsframework.webhookpayloads.webhookdata.WebhookDTO;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.gitsync.gitxwebhooks.metrics.GitXWebhookQueueOperationMetrics;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.logging.AutoLogContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.webhook.WebhookHelper;
import io.harness.ng.webhook.WebhookHmacHelper;
import io.harness.ng.webhook.WebhookSecretsConfig;
import io.harness.ng.webhook.entities.WebhookEvent;
import io.harness.ng.webhook.entities.WebhookEvent.WebhookEventsKeys;
import io.harness.ng.webhook.services.api.WebhookEventProcessingService;
import io.harness.product.ci.scm.proto.EventBridge;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.repositories.ng.webhook.spring.WebhookEventRepository;
import io.harness.scope.ScopeHelper;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class WebhookEventProcessingServiceImpl
    implements WebhookEventProcessingService, MongoPersistenceIterator.Handler<WebhookEvent> {
  private static final String HMAC_SHA_256 = "HmacSHA256";
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private WebhookHelper webhookHelper;
  @Inject private WebhookHmacHelper webhookHmacHelper;
  @Inject WebhookEventRepository webhookEventRepository;
  @Inject private PmsFeatureFlagHelper ngFeatureFlagHelperService;
  @Inject private GitXWebhookService webhookService;
  @Inject private WebhookSecretsConfig webhookSecretsConfig;
  @Inject private MetricService metricService;

  @Override
  public void registerIterators(int threadPoolSize) {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("WebhookEventProcessor")
            .poolSize(threadPoolSize)
            .interval(ofSeconds(5))
            .build(),
        WebhookEventProcessingService.class,
        MongoPersistenceIterator.<WebhookEvent, SpringFilterExpander>builder()
            .clazz(WebhookEvent.class)
            .fieldName(WebhookEventsKeys.nextIteration)
            .targetInterval(ofMinutes(5))
            .acceptableExecutionTime(ofMinutes(2))
            .acceptableNoAlertDelay(ofSeconds(30))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
            .redistribute(true));
  }

  @Override
  public void handle(WebhookEvent event) {
    try (AutoLogContext ignore2 = event.autoLogContext()) {
      log.info("Processing the webhook event with uuid = [{}]", event.getUuid());
      SourceRepoType sourceRepoType = WebhookHelper.getSourceRepoType(event);
      ParseWebhookResponse parseWebhookResponse = getParsedWebhookResponse(event, sourceRepoType);

      if (event.getWebhookScope() != null) {
        Optional<GitXWebhook> webhookOptional =
            webhookService.getWebhookByIdentifier(event.getAccountId(), event.getWebhookScope().getOrgIdentifier(),
                event.getWebhookScope().getProjectIdentifier(), event.getWebhookIdentifier());

        if (webhookOptional.isPresent()) {
          if ((NGCommonEntityConstants.GENERIC_WEBHOOK_TYPE).equals(webhookOptional.get().getWebhookType())) {
            webhookHmacHelper.verifyHMACSignature(webhookOptional.get(), event.getPayload(), event.getHeaders());
          } else if ((NGCommonEntityConstants.SLACK_WEBHOOK_TYPE).equals(webhookOptional.get().getWebhookType())) {
            webhookHmacHelper.verifyHMACSignatureForSlack(
                webhookOptional.get(), event.getPayload(), event.getHeaders());
          }
        }
      }

      if (event.getHeaders().stream().anyMatch(
              headerConfig -> X_HARNESS_ARTIFACT_REGISTRY_TRIGGER.equalsIgnoreCase(headerConfig.getKey()))
          && containsSignatureHeader(event)) {
        WebhookHmacHelper.verifySignature(event.getPayload(), event.getHeaders(), X_HARNESS_WEBHOOK_SIGNATURE,
            webhookSecretsConfig.getArtifactRegistry(), HMAC_SHA_256);
      }

      if (event.getHeaders().stream().anyMatch(
              headerConfig -> X_HARNESS_TRIGGER.equalsIgnoreCase(headerConfig.getKey()))
          && containsSignatureHeader(event)) {
        WebhookHmacHelper.verifySignature(event.getPayload(), event.getHeaders(), X_HARNESS_WEBHOOK_SIGNATURE,
            webhookSecretsConfig.getCode(), HMAC_SHA_256);
      }

      try {
        publishWebhookEvent(event, parseWebhookResponse, sourceRepoType);
      } catch (Exception e) {
        log.error("Error while publishing Webhook Event: ", e);
      } finally {
        webhookEventRepository.delete(event);
      }
    }
  }

  private static boolean containsSignatureHeader(WebhookEvent event) {
    return event.getHeaders().stream().anyMatch(h -> X_HARNESS_WEBHOOK_SIGNATURE.equalsIgnoreCase(h.getKey()));
  }

  private ParseWebhookResponse getParsedWebhookResponse(WebhookEvent event, SourceRepoType sourceRepoType) {
    if (EmptyPredicate.isNotEmpty(event.getWebhookIdentifier())) {
      return ParseWebhookResponse.newBuilder()
          .setEventBridge(EventBridge.newBuilder()
                              .setWebhookId(event.getWebhookIdentifier())
                              .setScope(ScopeHelper
                                            .getScope(event.getWebhookScope().getAccountIdentifier(),
                                                event.getWebhookScope().getOrgIdentifier(),
                                                event.getWebhookScope().getProjectIdentifier())
                                            .getYamlRepresentation())
                              .build())
          .build();
    } else if (sourceRepoType != SourceRepoType.UNRECOGNIZED
        && sourceRepoType != SourceRepoType.HARNESS_ARTIFACT_REGISTRY) {
      return webhookHelper.invokeScmService(event);
    }

    return null;
  }

  public void publishWebhookEvent(
      WebhookEvent event, ParseWebhookResponse parseWebhookResponse, SourceRepoType sourceRepoType) {
    WebhookDTO webhookDTO = webhookHelper.generateWebhookDTO(event, parseWebhookResponse, sourceRepoType);

    List<Producer> producersList = webhookHelper.getProducerListForEvent(webhookDTO);
    // if publish fails for one of the producers, still continue for rest of the producers.
    for (Producer producer : producersList) {
      try {
        producer.send(getMessage(producer, webhookDTO));
        recordEnqueueMetrics(producer, webhookDTO);
      } catch (EventsFrameworkDownException e) {
        String topicName =
            producer instanceof AbstractProducer ? ((AbstractProducer) producer).getTopicName() : StringUtils.EMPTY;
        log.error(
            String.format("Error while publishing Webhook Event: %s to Topic %s", webhookDTO.getEventId(), topicName),
            e);
      }
    }
  }

  private void recordEnqueueMetrics(Producer producer, WebhookDTO webhookDTO) {
    String topicName = producer instanceof RedisProducer ? ((RedisProducer) producer).getTopicName() : null;
    if (GIT_PUSH_EVENT_STREAM.equals(topicName)) {
      GitXWebhookQueueOperationMetrics.recordMessageMetric(
          WEBHOOK_PUSH_EVENT_ENQUEUED, webhookDTO.getAccountId(), webhookDTO, metricService);
    } else if (GIT_BRANCH_HOOK_EVENT_STREAM.equals(topicName)) {
      GitXWebhookQueueOperationMetrics.recordMessageMetric(
          WEBHOOK_BRANCH_EVENT_ENQUEUED, webhookDTO.getAccountId(), webhookDTO, metricService);
    } else if (GIT_PR_EVENT_STREAM.equals(topicName)) {
      GitXWebhookQueueOperationMetrics.recordMessageMetric(
          WEBHOOK_PULL_REQUEST_EVENT_ENQUEUED, webhookDTO.getAccountId(), webhookDTO, metricService);
    }
  }

  private Message getMessage(Producer producer, WebhookDTO webhookDTO) {
    String topicName = producer instanceof RedisProducer ? ((RedisProducer) producer).getTopicName() : null;
    if (WEBHOOK_EVENTS_STREAM.equals(topicName)
        && ngFeatureFlagHelperService.isEnabled(
            webhookDTO.getAccountId(), FeatureName.CDS_STORE_WEBHOOK_PAYLOAD_IN_FILE_STORAGE)) {
      /* If webhook payload data is being stored in file store, we don't need to send it over Redis.
         Thus, here we remove webhook payload data from the event's DTO.
         For the time being, we are only removing webhook payload data from WEBHOOK_EVENTS_STREAM. */
      // TODO: Make this method remove webhook payload data from ALL streams.
      webhookDTO = webhookDTO.toBuilder()
                       .setJsonPayload(StringUtils.EMPTY)
                       .setParsedResponse(ParseWebhookResponse.newBuilder().build())
                       .build();
    }
    if (GIT_PUSH_EVENT_STREAM.equals(topicName)
        && ngFeatureFlagHelperService.isEnabled(
            webhookDTO.getAccountId(), FeatureName.CDS_USE_WEBHOOK_PAYLOAD_FILE_IN_GITX)) {
      /* If webhook payload data is being stored in file store, we don't need to send it over Redis. Thus, here we
       * remove webhook payload data from the event's DTO.*/
      webhookDTO = webhookDTO.toBuilder()
                       .setJsonPayload(StringUtils.EMPTY)
                       .setParsedResponse(ParseWebhookResponse.newBuilder().build())
                       .build();
    }
    return Message.newBuilder().setData(webhookDTO.toByteString()).build();
  }
}
